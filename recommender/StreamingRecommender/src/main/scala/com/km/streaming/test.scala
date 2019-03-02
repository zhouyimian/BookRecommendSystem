package com.km.streaming

import com.km.java.model.Constant._
import com.km.scala.model.MongoConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.kafka010.{ConsumerStrategies, KafkaUtils, LocationStrategies}
import org.apache.spark.streaming.{Seconds, StreamingContext}

object test {
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
      rdd.map {
        case (uid, bid, score, timestamp) =>
          print(">>>>>>>>>>>")
      }.count()
    }

    ssc.start()
    ssc.awaitTermination()
  }
}
