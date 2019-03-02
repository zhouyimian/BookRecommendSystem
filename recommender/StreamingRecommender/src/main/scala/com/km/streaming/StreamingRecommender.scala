package com.km.streaming

import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.{MongoClient, MongoClientURI}
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.kafka010._
import org.apache.spark.streaming.{Seconds, StreamingContext}
import redis.clients.jedis.Jedis
import com.km.java.model.Constant._
import com.km.scala.model.BookRecs
import com.km.scala.model.MongoConfig

import scala.collection.JavaConversions._

object ConnHelper extends Serializable {
  lazy val jedis = new Jedis("RecommendSystem")
  lazy val mongoClient = MongoClient(MongoClientURI("mongodb://RecommendSystem:27017/"+MONGODB_DATABASE))
}

object StreamingRecommender {
  val MAX_USER_RATINGS_NUM = 20
  val MAX_SIM_BOOK_NUM = 20

  def main(args: Array[String]): Unit = {
    val config = Map(
      "spark.cores" -> "local[*]",
      "mongo.uri" -> ("mongodb://RecommendSystem:27017/" + MONGODB_DATABASE),
      "mongo.db" -> MONGODB_DATABASE,
      "kafka.topic" -> "recommender"
    )


    //创建SparkConf配置
    val sparkConf = new SparkConf().setAppName("StreamingRecommender").setMaster(config("spark.cores"))
    //创建一个Sparksession
    val spark = SparkSession.builder().config(sparkConf).getOrCreate()

    val sc = spark.sparkContext

    val ssc = new StreamingContext(sc, Seconds(2))

    implicit val mongoConfig = MongoConfig(config("mongo.uri"), config("mongo.db"))
    import spark.implicits._

    //广播图书相似度矩阵

    //转换为Map[Int,Map[Int,Double]]
    val simBookMatrix = spark
      .read
      .option("uri", config("mongo.uri"))
      .option("collection", MONGODB_BOOK_RECS_COLLECTION)
      .format(MONGO_DRIVER_CLASS)
      .load()
      .as[BookRecs]
      .rdd
      .map { recs =>
        (recs.bid, recs.recs.map(x => (x.bid, x.r)).toMap)
      }.collectAsMap()

    val simBooksMatrixBroadCast = sc.broadcast(simBookMatrix)

    val test = sc.makeRDD(1 to 2)
    test.map(x => simBooksMatrixBroadCast.value.get(1)).count()


    //创建kafka的连接
    val kafkaParams = Map(
      "bootstrap.servers" -> "192.168.43.44:9092",
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> "test-consumer-group",
      "auto.offset.reset" -> "latest"
    )
    val kafkaStream = KafkaUtils.createDirectStream[String, String](
      ssc,
      LocationStrategies.PreferConsistent,
      ConsumerStrategies.Subscribe[String, String](Array(config("kafka.topic")), kafkaParams))

    //UID/MID/SCORE/TIMESTAMP
    //产生评分流
    val ratingStream = kafkaStream.map { case msg =>
      var attr = msg.value().split("\\|")
      (attr(0).toInt, attr(1).toInt, attr(2).toDouble, attr(3).toInt)
    }
    ratingStream.foreachRDD { rdd =>
      rdd.map { case (uid, bid, score, timestamp) =>
        //获取当前最近的M次图书评分
        val userRecentlyRatings = getUserRecentlyRating(MAX_USER_RATINGS_NUM, uid, ConnHelper.jedis)
        //获取图书P最相似的K部图书
        val simBooks = getTopSimBooks(MAX_SIM_BOOK_NUM, bid, uid, simBooksMatrixBroadCast.value)
        //计算待选图书的优先级
        val streamRecs = computeBookScores(simBooksMatrixBroadCast.value, userRecentlyRatings, simBooks)
        //将数据保存到Mongodb
        saveRecsToMongoDB(uid, streamRecs)
      }.count()
    }
    ssc.start()
    ssc.awaitTermination()
  }

  //获取当前最近的M次图书评分
  /**
    *
    * @param num 评分的个数
    * @param uid 哪个用户
    * @return
    */
  def getUserRecentlyRating(num: Int, uid: Int, jedis: Jedis): Array[(Int, Double)] = {
    jedis.lrange("uid:" + uid.toString, 0, num).map { item =>
      val attr = item.split("\\:")
      (attr(0).trim.toInt, (attr(1).trim.toDouble))
    }.toArray
  }

  /**
    *
    * @param num        相似图书的数量
    * @param bid        当前图书的ID
    * @param uid        当前用户的ID
    * @param simBooks  图书相似度矩阵的广播变量值
    * @param mongConfig Mongodb的配置
    * @return
    */
  //获取图书P最相似的K部图书
  def getTopSimBooks(num: Int, bid: Int, uid: Int, simBooks: scala.collection.Map[Int, scala.collection.immutable.Map[Int, Double]])(implicit mongConfig: MongoConfig): Array[Int] = {
    //从广播变量的书籍相似度矩阵中获取当前书籍所有的相似书籍
    val allSimBooks = simBooks.get(bid).get.toArray
    //获取用户已经看过的图书
    val ratingExist = ConnHelper.mongoClient(mongConfig.db)(MONGODB_RATING_COLLECTION).find(MongoDBObject("uid" -> uid)).toArray.map { item =>
      item.get("bid").toString.toInt
    }
    //过滤掉已经评分过的图书，并排序输出
    allSimBooks.filter(x => !ratingExist.contains(x._1)).sortWith(_._2 > _._2).take(num).map(x => x._1)
  }

  /**
    * 计算待选图书的优先级
    *
    * @param simBooks           图书相似度矩阵
    * @param userRecentlyRatings 用户最近的k次评分
    * @param topSimBooks        当前图书最相似的k个图书
    * @return
    */
  def computeBookScores(simBooks: scala.collection.Map[Int, scala.collection.immutable.Map[Int, Double]],
                        userRecentlyRatings: Array[(Int, Double)], topSimBooks: Array[Int]): Array[(Int, Double)] = {

    //用于保存每个待选图书和最近评分的每一本图书的权重得分
    val score = scala.collection.mutable.ArrayBuffer[(Int, Double)]()

    //用于保存每个图书的增强因子数
    val increMap = scala.collection.mutable.HashMap[Int, Int]()

    //用于保存每个图书的减弱因子数
    val decreMap = scala.collection.mutable.HashMap[Int, Int]()
    for (topSimMovie <- topSimBooks; userRecentlyRating <- userRecentlyRatings) {
      val simScore = getBookSimScore(simBooks, userRecentlyRating._1, topSimMovie)
      if (simScore > 0.6) {
        score += ((topSimMovie, simScore * userRecentlyRating._2))
        if (userRecentlyRating._2 > 3) {
          increMap(topSimMovie) = increMap.getOrDefault(topSimMovie, 0) + 1
        } else {
          decreMap(topSimMovie) = decreMap.getOrDefault(topSimMovie, 0) + 1
        }
      }
    }
    score.groupBy(_._1).map { case (bid, sims) =>
      (bid, sims.map(_._2).sum / sims.length + log(increMap(bid)) - log(decreMap(bid)))
    }.toArray
  }

  def log(m: Int): Double = {
    math.log(m) / math.log(2)
  }

  /**
    * 获取两个图书之间的相似度
    *
    * @param simBooks       图书相似度矩阵
    * @param userRatingBook 用户已经评分的图书
    * @param topSimBook     候选图书
    * @return
    */
  def getBookSimScore(simBooks: scala.collection.Map[Int, scala.collection.immutable.Map[Int, Double]], userRatingBook: Int, topSimBook: Int): Double = {
    simBooks.get(topSimBook) match {
      case Some(sim) => sim.get(userRatingBook) match {
        case Some(score) => score
        case None => 0.0
      }
      case None => 0.0
    }
  }

  /**
    * 将数据保存到Mongodb  uid -> 1,recs -> 22:4.5|45,3.3
    *
    * @param uid        流式的推荐结果
    * @param streamRecs 流式的推荐结果
    * @param mongConfig Mongodb的配置
    */
  def saveRecsToMongoDB(uid: Int, streamRecs: Array[(Int, Double)])(implicit mongConfig: MongoConfig): Unit = {
    //到StreamRecs的连接
    val streamRecsCollection = ConnHelper.mongoClient(mongConfig.db)(MONGODB_STREAM_RECS_COLLECTION)

    streamRecsCollection.findAndRemove(MongoDBObject("uid" -> uid))

    streamRecsCollection.insert(MongoDBObject("uid" -> uid, "recs" -> streamRecs.map(x => x._1 + ":" + x._2).mkString("|")))
  }
}

