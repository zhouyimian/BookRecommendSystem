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

object addBookUrlToBid {
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
    val ratingRDD = spark.sparkContext.textFile(rating_data_path)


    var map:Map[String,Int] = Map()
    var id:Int = 0


    //将bookRDD转换为DataFrame
    val ratingDF = ratingRDD.map(item => {
      val attr = item.split("::")
      if(map.contains(attr(0).trim.toString)){
        BookRatingWithUrl(map.get(attr(0).trim.toString).get,attr(1).toInt,attr(2).toDouble)
      }
      else{
        map.+=(attr(0).trim.toString -> id)
        id+=1
        BookRatingWithUrl(map.get(attr(0).trim.toString).get,attr(1).toInt,attr(2).toDouble)
      }

    }).toDF()

    implicit val mongoConfig = MongoConfig(config.get("mongo.uri").get, config.get("mongo.db").get)

    //将数据保存到mongodb
    storeDataInMongoDB(ratingDF)


    //关闭spark
    spark.stop()

  }

  //将数据保存到mongodb的方法
  def storeDataInMongoDB(bookWithTagsDF: DataFrame)(implicit mongoConfig: MongoConfig): Unit = {
    //新建一个到mongodb的连接
    val mongoClient = MongoClient(MongoClientURI(mongoConfig.uri))

    //如果mongodb中有对应的数据库，应该删除
    mongoClient(mongoConfig.db)(MONGODB_RATING_WITHINT__COLLECTION).dropCollection()

    //将当前数据写入mongodb
    bookWithTagsDF
      .write
      .option("uri", mongoConfig.uri)
      .option("collection", MONGODB_RATING_WITHINT__COLLECTION)
      .mode("overwrite")
      .format(MONGO_DRIVER_CLASS)
      .save()

    mongoClient.close()
  }
}
