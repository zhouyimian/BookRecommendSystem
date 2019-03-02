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


object test {
  def main(args: Array[String]): Unit = {
    var s ="https://book.douban.com/subject/1001165/"
    print(s.split("/")(4))
  }
}