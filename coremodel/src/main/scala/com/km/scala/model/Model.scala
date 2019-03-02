package com.km.scala.model
//bookinfo数据集通过, 分隔符
//1                                                              数据ID
// https://book.douban.com/subject/26963900/                     书籍URL
// 小说                                                          书籍类别
// 房思琪的初戀樂園                                              书名
// 林奕含                                                        作者
// 8.8                                                           书籍评分
// 5577                                                          评分人数
// NTD320                                                        价格
// 游擊文化                                                      出版商
// 2017-2-7                                                      出版时间
// 2835                                                          评论人数
case class BookInfo(val id:Int,val url:String,val tag:String,val name:String,val author:String,val score:Double,val score_count:Int,
                    val price:String,val issue: String,val issue_time:String , val comment_count:Int)

case class BookInfoWithTags(val url:String,val name:String,val author:String,val score:Double,val score_count:Int,
                    val price:String,val issue: String,val issue_time:String , val comment_count:Int,val tags:String)


case class BookInfoWithTagsAndId(val bid:Int,val url:String,val name:String,val author:String,val score:Double,val score_count:Int,
                            val price:String,val issue: String,val issue_time:String , val comment_count:Int,val tags:String)
//score数据集 用户对书籍的评分
//2452784                     用户ID
// 1858576,                   书籍ID
// 2.5,                       用户对书籍的评分
case class BookRating(val uid: String, val bid: Int, val score: Double)


//将uid转换成int类型
case class BookRatingWithUrl(val uid: Int, val bid: Int, val score: Double)


case class Tags(val url: String, val tags:String)
/**
  *MongoDB的连接配置
  * @param uri MongoDB的连接
  * @param db  MongoDB要操作的数据库
  */
case class MongoConfig(val uri:String,val db:String)

/**
  * elasticSearch的连接配置
  * @param HttpHosts        HTTP的主机列表，以逗号分割
  * @param transportHosts  Transport主机列表，用逗号分隔
  * @param index            需要操作的索引
  * @param clustername      ES集群的名称
  */
case class ESconfig(val HttpHosts:String,val transportHosts:String,val index:String,val clustername:String)


//推荐
case class Recommendation(bid: Int, r: Double)

//用户的推荐
case class UserRecs(uid: Int, recs: Seq[Recommendation])

//书籍的相似度
case class BookRecs(bid: Int, recs: Seq[Recommendation])


/**
  * 书籍类别推荐结果
  * @param genres    书籍类别
  * @param recs       Top10的书籍集合
  */
case class GenresRecommendation(genres:String,recs:Seq[Recommendation])

object Model {

}
