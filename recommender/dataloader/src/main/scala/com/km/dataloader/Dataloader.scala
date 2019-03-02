package com.km.dataloader

import java.net.InetAddress

import com.km.java.model.Constant._
import com.km.scala.model._
import com.mongodb.casbah.{MongoClient, MongoClientURI}
import org.apache.spark.SparkConf
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.transport.client.PreBuiltTransportClient


//数据的主加载服务


object Dataloader {
  def main(args: Array[String]): Unit = {


    val mongo_server = "RecommendSystem:27017"
    val es_http_server = "RecommendSystem:9200"
    val es_trans_server = "RecommendSystem:9300"
    val es_cluster_name = "es-cluster"

    val book_data_path = "D:\\IDEA_project\\BookRecommendSystem\\recommender\\dataloader\\src\\main\\resources\\small\\bookinfo.dat"
    val rating_data_path = "D:\\IDEA_project\\BookRecommendSystem\\recommender\\dataloader\\src\\main\\resources\\small\\score.dat"


    val config = Map(
      "spark.cores" -> "local[*]",
      "mongo.uri" -> ("mongodb://RecommendSystem:27017/" + MONGODB_DATABASE),
      "mongo.db" -> "recommender",
      "es.httpHosts" -> "RecommendSystem:9200",
      "es.transportHosts" -> "RecommendSystem:9300",
      "es.index" -> ES_INDEX,
      "es.cluster.name" -> "es-cluster"
    )

    //创建SparkConf配置
    val sparkConf = new SparkConf().setAppName("DataLoader").setMaster(config.get("spark.cores").get)
    //创建一个Sparksession
    val spark = SparkSession.builder().config(sparkConf).getOrCreate()

    import spark.implicits._
    //将数据集加载进来
    val bookRDD = spark.sparkContext.textFile(book_data_path)

    //过滤数据
    val filteBook = bookRDD.filter(item =>
      (item.split(",").length == 11 && item.last != ',')
    )

    //将bookRDD转换为DataFrame
    val bookDF = filteBook.map(item => {
      val attr = item.split(",")
      if (attr(5).isEmpty) attr(5) = "0"
      if (attr(6).isEmpty) attr(6) = "0"
      if (attr(10).isEmpty) attr(10) = "0"
      BookInfo(attr(0).trim.toInt, attr(1).trim, attr(2).trim, attr(3).trim,
        attr(4).trim, attr(5).trim.toDouble, attr(6).trim.toInt, attr(7).trim, attr(8).trim, attr(9).trim, attr(10).trim.toInt)
    }).toDF()


    val ratingRDD = spark.sparkContext.textFile(rating_data_path)
    //将ratingRDD转换为DataFrame
    val ratingDF = ratingRDD.map(item => {
      val attr = item.split("::")
      BookRating(attr(0).trim.toString, attr(1).toInt, attr(2).toDouble)
    }).toDF()


    implicit val mongoConfig = MongoConfig(config.get("mongo.uri").get, config.get("mongo.db").get)

    //将数据保存到mongodb
    storeDataInMongoDB(bookDF, ratingDF)


    //这里对tag数据集进行处理，处理后的形式为  MID   tag1/tag2/...
    /**
      * MID,Tags
      * 1   tag1/tag2/...
      */
    //    val newTag = tagDF.groupBy($"mid").agg(concat_ws("|",collect_set($"tag")).as("tags"))
    //    //需要将处理后的tag数据和movie数据融合，产生新的movie数据
    //    val movieWithTagsDF = movieDF.join(newTag,Seq("mid","mid"),"left").select("mid","name","descri","timelong","issue","shoot","language","genres","actors","directors","tags")

    implicit val esConfig = ESconfig(config.get("es.httpHosts").get, config.get("es.transportHosts").get,
      config.get("es.index").get, config.get("es.cluster.name").get)
    //将数据保存到es
    //storeDataInES(movieWithTagsDF)

    //关闭spark
    spark.stop()

  }

  //将数据保存到mongodb的方法
  def storeDataInMongoDB(bookDF: DataFrame, ratingDF: DataFrame)(implicit mongoConfig: MongoConfig): Unit = {
    //新建一个到mongodb的连接
    val mongoClient = MongoClient(MongoClientURI(mongoConfig.uri))

    //如果mongodb中有对应的数据库，应该删除
    mongoClient(mongoConfig.db)(MONGODB_BOOK_COLLECTION).dropCollection()

    mongoClient(mongoConfig.db)(MONGODB_RATING_COLLECTION).dropCollection()

    //将当前数据写入mongodb
    bookDF
      .write
      .option("uri", mongoConfig.uri)
      .option("collection", MONGODB_BOOK_COLLECTION)
      .mode("overwrite")
      .format(MONGO_DRIVER_CLASS)
      .save()

    ratingDF
      .write
      .option("uri", mongoConfig.uri)
      .option("collection", MONGODB_RATING_COLLECTION)
      .mode("overwrite")
      .format(MONGO_DRIVER_CLASS)
      .save()


    //对数据库表建立索引
    //    mongoClient(mongoConfig.db)(MONGODB_Book_COLLECTION).createIndex(MongoDBObject("mid"->1))
    //    mongoClient(mongoConfig.db)(MONGODB_RATING_COLLECTION).createIndex(MongoDBObject("uid"->1))
    //    mongoClient(mongoConfig.db)(MONGODB_RATING_COLLECTION).createIndex(MongoDBObject("mid"->1))

    //关闭mongodb连接
    mongoClient.close()
  }

  //将数据保存到es的方法
  def storeDataInES(movieWithTagsDF: DataFrame)(implicit eSConfig: ESconfig): Unit = {
    //新建一个配置
    val settings: Settings = Settings.builder().put("cluster.name", eSConfig.clustername).build()

    //新建ES客户端
    val esClient = new PreBuiltTransportClient(settings)

    //需要将transportHosts添加到esClient中
    val REGEX_HOST_PORT = "(.+):(\\d+)".r
    eSConfig.transportHosts.split(",").foreach {
      case REGEX_HOST_PORT(host: String, port: String) => {
        esClient.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host), port.toInt))
      }
    }
    //需要清除ES遗留的数据
    if (esClient.admin().indices().exists(new IndicesExistsRequest(eSConfig.index)).actionGet().isExists) {
      esClient.admin().indices().delete(new DeleteIndexRequest(eSConfig.index))
    }
    esClient.admin().indices().create(new CreateIndexRequest(eSConfig.index))


    //将数据写入到ES中
    movieWithTagsDF
      .write
      .option("es.nodes", eSConfig.HttpHosts)
      .option("es.http.timeout", "100m")
      .option("es.mapping.id", "bid")
      .mode("overwrite")
      .format(ES_DRIVER_CLASS)
      .save(eSConfig.index + "/" + ES_TYPE)
  }
}
