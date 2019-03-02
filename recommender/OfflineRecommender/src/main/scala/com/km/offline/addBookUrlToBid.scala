package com.km.offline

import com.km.java.model.Constant._
import com.km.scala.model._
import com.mongodb.casbah.{MongoClient, MongoClientURI}
import org.apache.spark.SparkConf
import org.apache.spark.sql.{DataFrame, SparkSession}

object addBookUrlToBid {
  def main(args: Array[String]): Unit = {

    val config = Map(
      "spark.cores" -> "local[*]",
      "mongo.uri" -> ("mongodb://RecommendSystem:27017/" + MONGODB_DATABASE),
      "mongo.db" -> MONGODB_DATABASE
    )


    //创建SparkConf配置
    val sparkConf = new SparkConf().setAppName("addBookUrlToBid").setMaster(config("spark.cores"))
      .set("spark.executor.memory", "4G").set("spark.driver.memory", "3G")
    //创建一个Sparksession
    val spark = SparkSession.builder().config(sparkConf).getOrCreate()

    val mongoConfig = MongoConfig(config("mongo.uri"), config("mongo.db"))

    import spark.implicits._

    //读取mongodb的业务数据
    val bookinfoRdd = spark
      .read
      .option("uri", mongoConfig.uri)
      .option("collection", MONGODB_BOOK_WITHTAGS_COLLECTION)
      .format(MONGO_DRIVER_CLASS)
      .load()
      .as[BookInfoWithTags]
      .rdd


    var bookInfoWithtagsandId = bookinfoRdd.map(info=>{
      BookInfoWithTagsAndId((info.url.split("/")(4)).toInt,info.url,info.name,info.author,info.score
        ,info.score_count,info.price,info.issue,info.issue_time,info.comment_count,info.tags)
    }).toDF()

    storeDataInMongoDB(bookInfoWithtagsandId)


    //关闭spark
    spark.stop()

  }

  //将数据保存到mongodb的方法
  def storeDataInMongoDB(bookInfoWithtagsandId: DataFrame): Unit = {
    //新建一个到mongodb的连接
    val mongoClient = MongoClient(MongoClientURI("mongodb://RecommendSystem:27017/" + MONGODB_DATABASE))

    //如果mongodb中有对应的数据库，应该删除
    mongoClient(MONGODB_DATABASE)(MONGODB_BOOK_WITHBID_COLLECTION).dropCollection()

    //将当前数据写入mongodb
    bookInfoWithtagsandId
      .write
      .option("uri", "mongodb://RecommendSystem:27017/" + MONGODB_DATABASE)
      .option("collection", MONGODB_BOOK_WITHBID_COLLECTION)
      .mode("overwrite")
      .format(MONGO_DRIVER_CLASS)
      .save()

    mongoClient.close()
  }
}


