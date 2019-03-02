package com.km.statistics

import java.text.SimpleDateFormat
import java.util.Date

import com.km.scala.model._
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import com.km.java.model.Constant._

object StatisticsRecommender {
  def main(args: Array[String]): Unit = {
    val config = Map(
      "spark.cores" -> "local[*]",
      "mongo.uri" -> ("mongodb://RecommendSystem:27017/"+MONGODB_DATABASE),
      "mongo.db" -> MONGODB_DATABASE
    )


    //创建SparkConf配置
    val sparkConf = new SparkConf().setAppName("StatisticsRecommender").setMaster(config("spark.cores"))

    //创建一个Sparksession
    val spark = SparkSession.builder().config(sparkConf).getOrCreate()

    val mongoConfig = MongoConfig(config("mongo.uri"), config("mongo.db"))

    import spark.implicits._
    //将数据集加载进来
    MONGODB_BOOK_RECS_COLLECTION
    val ratingDF = spark
      .read
      .option("uri", mongoConfig.uri)
      .option("collection", "RatingWithUrl")
      .format("com.mongodb.spark.sql")
      .load()
      .as[BookRatingWithUrl]
      .toDF()


    val bookDF = spark
      .read
      .option("uri", mongoConfig.uri)
      .option("collection", MONGODB_BOOK_WITHTAGS_COLLECTION)
      .format("com.mongodb.spark.sql")
      .load()
      .as[BookInfoWithTags]
      .toDF()


    ratingDF.createOrReplaceTempView("ratings")
    //统计所有历史数据中每个书籍的评分数
    //数据结构 -》mid count
    val rateMoreBooksDF = spark.sql("select url,count(url) as count from ratings group by url")

    rateMoreBooksDF
      .write
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_RATE_MORE_BOOK)
      .mode("overwrite")
      .format(MONGO_DRIVER_CLASS)
      .save()


    //统计每本书的平均评分
    val averageBooks = spark.sql("select url,avg(score) as avg from ratings group by url")

    averageBooks
      .write
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_AVERAGE_BOOKS)
      .mode("overwrite")
      .format(MONGO_DRIVER_CLASS)
      .save()
    //关闭spark
    spark.stop()

  }
}
