package com.km.dataloader

import com.km.java.model.Constant._
import com.km.scala.model._
import com.mongodb.casbah.{MongoClient, MongoClientURI}
import org.apache.spark.SparkConf
import org.apache.spark.sql.{DataFrame, SparkSession}


//数据的主加载服务


object createTags {
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

    val tagDF = bookDF.select("url","tag").distinct().toDF()

    import org.apache.spark.sql.functions._

    val newTag = tagDF.groupBy($"url").agg(concat_ws("|",collect_set($"tag")).as("tags"))
    //需要将处理后的tag数据和bookinfo数据融合，产生新的book数据
    val bookWithTagsDF = bookDF.join(newTag, Seq("url", "url"), "left").select("url", "name", "author", "score", "score_count",
      "price", "issue", "issue_time", "comment_count","tags").dropDuplicates(Seq("url"))


    implicit val mongoConfig = MongoConfig(config.get("mongo.uri").get, config.get("mongo.db").get)

    val count:Int=0
    //将数据保存到mongodb
    storeDataInMongoDB(bookWithTagsDF)


    //关闭spark
    spark.stop()

  }

  //将数据保存到mongodb的方法
  def storeDataInMongoDB(bookWithTagsDF: DataFrame)(implicit mongoConfig: MongoConfig): Unit = {
    //新建一个到mongodb的连接
    val mongoClient = MongoClient(MongoClientURI(mongoConfig.uri))

    //如果mongodb中有对应的数据库，应该删除
    mongoClient(mongoConfig.db)(MONGODB_BOOK_WITHTAGS_COLLECTION).dropCollection()

    //将当前数据写入mongodb
    bookWithTagsDF
      .write
      .option("uri", mongoConfig.uri)
      .option("collection", MONGODB_BOOK_WITHTAGS_COLLECTION)
      .mode("overwrite")
      .format(MONGO_DRIVER_CLASS)
      .save()

    mongoClient.close()
  }
}
