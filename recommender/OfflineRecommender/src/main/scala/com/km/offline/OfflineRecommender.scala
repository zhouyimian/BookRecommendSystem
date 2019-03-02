package com.km.offline

import com.km.java.model.Constant._
import com.km.scala.model._
import org.apache.spark.SparkConf
import org.apache.spark.mllib.recommendation.{ALS, Rating}
import org.apache.spark.sql.SparkSession
import org.jblas.DoubleMatrix
object OfflineRecommender {
  val USER_MAX_RECOMMENDATION = 10

  def main(args: Array[String]): Unit = {
    val config = Map(
      "spark.cores" -> "local[*]",
      "mongo.uri" -> ("mongodb://RecommendSystem:27017/"+MONGODB_DATABASE),
      "mongo.db" -> MONGODB_DATABASE
    )


    //创建SparkConf配置
    val sparkConf = new SparkConf().setAppName("OfflineRecommender").setMaster(config("spark.cores"))
      .set("spark.executor.memory", "6G").set("spark.driver.memory", "4G")
    //创建一个Sparksession
    val spark = SparkSession.builder().config(sparkConf).getOrCreate()

    val mongoConfig = MongoConfig(config("mongo.uri"), config("mongo.db"))

    import spark.implicits._

    //读取mongodb的业务数据
    val ratingRDD = spark
      .read
      .option("uri", mongoConfig.uri)
      .option("collection", MONGODB_RATING_WITHINT__COLLECTION)
      .format(MONGO_DRIVER_CLASS)
      .load()
      .as[BookRatingWithUrl]
      .rdd
      .map(rating => (rating.uid, rating.bid, rating.score)).cache()
    //用户数据集
    val userRDD = ratingRDD.map(_._1).distinct()
    //创建训练数据集


    //书籍数据集
//    val bookRDD = spark
//      .read
//      .option("uri", mongoConfig.uri)
//      .option("collection", MONGODB_BOOK_WITHBID_COLLECTION)
//      .format(MONGO_DRIVER_CLASS)
//      .load()
//      .as[BookInfoWithTagsAndId]
//      .rdd
//      .map(_.bid).cache()

    val bookRDD = ratingRDD.map(_._2).distinct()
    val trainData = ratingRDD.map(x => Rating(x._1, x._2, x._3))

    val (rank, iterators, lambda) = (5, 2, 0.01)
    //训练ALS模型
    val model = ALS.train(trainData,rank,iterators,lambda)
    //需要构造一个userProducts RDD[(Int,Int)]

    val userBooks = userRDD.cartesian(bookRDD)

    val preRatings = model.predict(userBooks)


    val userRecs = preRatings.map(rating => (rating.user, (rating.product, rating.rating)))
      .groupByKey()
      .map {
        case (uid, recs) => UserRecs(uid, recs.toList.sortWith(_._2 > _._2).take(USER_MAX_RECOMMENDATION).map(x => Recommendation(x._1, x._2)))
      }.toDF()

    userRecs.write
      .option("uri", mongoConfig.uri)
      .option("collection", MONGODB_USER_RECS_COLLECTION)
      .mode("overwrite")
      .format(MONGO_DRIVER_CLASS)
      .save()



    //计算图书相似度矩阵
    //获取图书的特征矩阵
    val bookFeatures = model.productFeatures.map {
      case (bid, features) =>
        (bid, new DoubleMatrix(features))
    }
    val bookRecs = bookFeatures.cartesian(bookFeatures)
      .filter { case (a, b) => a._1 != b._1 }
      .map {
        case (a, b) =>
          val simScore = this.consinSim(a._2, b._2)
          (a._1, (b._1, simScore))
      }.filter(_._2._2 > 0.8)
      .groupByKey()
      .map{
        case (bid,items) =>
          BookRecs(bid,items.toList.map(x => Recommendation(x._1,x._2)))
      }.toDF()

    bookRecs
      .write
      .option("uri", mongoConfig.uri)
      .option("collection", MONGODB_BOOK_RECS_COLLECTION)
      .mode("overwrite")
      .format(MONGO_DRIVER_CLASS)
      .save()
    spark.close()

  }

  //余弦相似度计算
  def consinSim(movie1: DoubleMatrix, movie2: DoubleMatrix): Double = {
    movie1.dot(movie2) / (movie1.norm2() * movie2.norm2())
  }

}

