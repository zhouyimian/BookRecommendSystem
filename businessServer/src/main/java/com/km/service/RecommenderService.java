package com.km.service;

import com.km.java.model.Constant;
import com.km.model.Recommendation;
import com.km.request.*;
import com.km.utils.FixSizedPriorityQueue;
import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.FuzzyQueryBuilder;
import org.elasticsearch.index.query.MoreLikeThisQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Service
public class RecommenderService {

    @Autowired
    private MongoClient mongoClient;

    @Autowired
    private TransportClient esClient;

    private MongoDatabase mongoDatabase;

    @Autowired
    private Jedis jedis;

    private MongoDatabase getMongoDatabase() {
        if (mongoDatabase == null)
            this.mongoDatabase = mongoClient.getDatabase(Constant.MONGODB_DATABASE);
        return this.mongoDatabase;
    }


    public List<Recommendation> getHybridRecommendations(GetHybridRecommendationRequest request) {
        //获取实时推荐结果
        List<Recommendation> streamRecs = getStreamRecsBooks(new GetStreamRecsRequest(request.getUid(), request.getBid(), request.getNum()));
        //获取ALS离线用户矩阵的推荐结果
        List<Recommendation> alsUserRecs = getUserCFBooks(new GetUserCFRequest(request.getUid(), request.getNum()));
        //获取ALS离线图书矩阵的推荐结果
        List<Recommendation> alsBookRecs = getItemCFBooks(new GetItemCFBookRequest(request.getBid(), request.getNum()));
        //基于内容的推荐结果 这个得依赖于ES 暂时无法实现

        List<Recommendation> result = new ArrayList<>();
        result.addAll(streamRecs.subList(0, (int) (streamRecs.size() * request.getStreamShare())));
        result.addAll(alsUserRecs.subList(0, (int) (alsUserRecs.size() * request.getAlsUserShare())));
        result.addAll(alsBookRecs.subList(0, (int) (alsBookRecs.size() * request.getAlsBookShare())));
        return result;
    }

    //获取当前用户的实时推荐
    //该方法得改进成自己去redis和mongodb取数据进行计算
    public List<Recommendation> getStreamRecsBooks(GetStreamRecsRequest request) {
        List<Recommendation> result = new ArrayList<>();
        MongoCollection<Document> streamRecsCollection = getMongoDatabase().getCollection(Constant.MONGODB_BOOK_RECS_COLLECTION);

        Document document = streamRecsCollection.find(new Document("bid", request.getBid())).first();
        ArrayList<Document> documents = document.get("recs", ArrayList.class);

        FixSizedPriorityQueue MongoSamqueue = new FixSizedPriorityQueue(Constant.USER_RATING_QUEUE_SIZE);
        for (Document item : documents) {
            MongoSamqueue.add(new Recommendation(item.getInteger("bid"), item.getDouble("r")));
        }
        List<Recommendation> mongoSam = new ArrayList<>();
        for (int i = 0; i < MongoSamqueue.getMaxSize(); i++) {
            mongoSam.add(MongoSamqueue.getQueue().poll());
        }

        ArrayList<Recommendation> redisqueue = new ArrayList<>();
        List<String> redisString = jedis.lrange("uid:" + request.getUid(), 0, Constant.USER_RATING_QUEUE_SIZE - 1);
        for (String s : redisString) {
            redisqueue.add(new Recommendation(Integer.parseInt(s.split(":")[0]), Double.parseDouble(s.split(":")[1])));
        }
        //计算与mongodb中查询电影相似度最高的K部候选电影得分
        for (int i = 0; i < mongoSam.size(); i++) {
            double score = 0;
            int incount = 0;
            int recount = 0;
            int count = 0;
            //获取mongoSam中每部电影和其他所有电影的相似度数据
            Document SameScores = mongoClient.getDatabase(Constant.MONGODB_DATABASE)
                    .getCollection(Constant.MONGODB_BOOK_RECS_COLLECTION)
                    .find(Filters.eq("bid", mongoSam.get(i).getBid())).first();
            ArrayList<Document> recss = document.get("recs", ArrayList.class);
            for(int j = 0;j<recss.size();j++){
                for(int k = 0;k<redisqueue.size();k++){
                    if(recss.get(j).getInteger("bid")==redisqueue.get(k).getBid()){
                        score+=recss.get(j).getInteger("r")*redisqueue.get(k).getScore();
                        double avg = mongoClient.getDatabase(Constant.MONGODB_DATABASE)
                                .getCollection(Constant.MONGODB_AVERAGE_BOOKS)
                                .find(Filters.eq("bid", redisqueue.get(k).getBid())).first().getDouble("avg");
                        if(avg>=3)
                            incount++;
                        else
                            recount++;
                        count++;
                        break;
                    }
                }
            }
            score = score/count + Math.log(Math.max(incount,1))-Math.log(Math.max(recount,1));
            result.add(new Recommendation(mongoSam.get(i).getBid(),score));
        }
        //获取上次实时推荐结果，和这次的实时推荐结果进行融合
        List<Recommendation> recentRecommends = parseDocument(
                mongoClient.getDatabase(Constant.MONGODB_DATABASE)
                        .getCollection(Constant.MONGODB_STREAM_RECS_COLLECTION)
                        .find(Filters.eq("uid", request.getUid())).first(),Constant.USER_RATING_QUEUE_SIZE);
        for(Recommendation item:result)
            MongoSamqueue.add(item);
        for(Recommendation item:recentRecommends)
            MongoSamqueue.add(item);
        result.clear();
        System.out.println(result.size());
        for (int i = 0; i < MongoSamqueue.getMaxSize(); i++) {
            result.add(MongoSamqueue.getQueue().poll());
        }
        System.out.println(result.size());
        return result;
    }

    //ALS算法中用户推荐矩阵
    public List<Recommendation> getUserCFBooks(GetUserCFRequest request) {
        MongoCollection<Document> userCFCollection = getMongoDatabase().getCollection(Constant.MONGODB_USER_RECS_COLLECTION);
        Document document = userCFCollection.find(new Document("uid", request.getUid())).first();
        return parseDocument(document, request.getSum());
    }

    //
    public List<Recommendation> getItemCFBooks(GetItemCFBookRequest request) {
        MongoCollection<Document> itemCFCollection = getMongoDatabase().getCollection(Constant.MONGODB_BOOK_RECS_COLLECTION);
        Document document = itemCFCollection.find(new Document("bid", request.getBid())).first();
        return parseDocument(document, request.getNum());
    }


    public List<Recommendation> parseDocument(Document document, int sum) {
        List<Recommendation> result = new ArrayList<>();
        if (null == document || document.isEmpty())
            return result;
        ArrayList<Document> documents = document.get("recs", ArrayList.class);
        int count = 0;
        for (Document item : documents) {
            result.add(new Recommendation(item.getInteger("bid"), item.getDouble("r")));
            count++;
            if (count >= sum)
                break;
        }
        return result;
    }

    //基于内容的推荐
    public List<Recommendation> getContentBasedRecommendations(GetContentBasedRecommendationRequest request) {
        MoreLikeThisQueryBuilder queryBuilder = QueryBuilders.moreLikeThisQuery
                (new MoreLikeThisQueryBuilder.Item[]{new MoreLikeThisQueryBuilder.Item
                        (Constant.ES_INDEX, Constant.ES_TYPE, String.valueOf(request.getBid()))
                });
        SearchResponse response = esClient.prepareSearch(Constant.ES_INDEX).setQuery(queryBuilder).setSize(request.getNum()).execute().actionGet();
        return parseESResponse(response);
    }

    public List<Recommendation> parseESResponse(SearchResponse response) {
        List<Recommendation> recommendations = new ArrayList<>();
        for (SearchHit hit : response.getHits()) {
            Map<String, Object> hitcontent = hit.getSourceAsMap();
            recommendations.add(new Recommendation((int) (hitcontent.get("bid")), 0D));
        }
        return recommendations;
    }

    /**
     * 获取书籍类别的Top书籍，解决冷启动问题
     *
     * @param request
     * @return
     */
    public List<Recommendation> getGenresTopBooks(GetGenresTopBookRequest request) {
        Document genresDocument = getMongoDatabase()
                .getCollection(Constant.MONGODB_GENRES_TOP_BOOKS)
                .find(new Document("genres", request.getGenres())).first();
        List<Recommendation> recommendations = new ArrayList<>();
        if (null == genresDocument || genresDocument.isEmpty())
            return recommendations;
        return parseDocument(genresDocument, request.getNum());
    }

    /**
     * 获取热门书籍
     * @param request
     * @return
     */
    public List<Recommendation> getHotRecommendations(GetHotRecommendationRequest request){
        FindIterable<Document> documents = getMongoDatabase().getCollection(Constant.MONGODB_RATE_MORE_RECENTLY_BOOKS).find().sort(Sorts.descending("yearmouth"));
        List<Recommendation> recommendations = new ArrayList<>();
        for(Document item:documents){
            recommendations.add(new Recommendation(item.getInteger("mid"),0D));
        }
        return recommendations.subList(0,recommendations.size()>request.getNum()?request.getNum():recommendations.size());
    }

    /**
     * 获取优质书籍
     *
     * @param request
     * @return
     */
    public List<Recommendation> getRateModeBooks(GetRateMoreBooksRequest request) {
        FindIterable<Document> documents = getMongoDatabase().getCollection(Constant.MONGODB_RATE_MORE_BOOK).find().sort(Sorts.descending("count"));
        List<Recommendation> recommendations = new ArrayList<>();
        int count = 0;
        int sum = request.getNum();
        for (Document item : documents) {
            recommendations.add(new Recommendation(item.getInteger("bid"), 0D));
            count++;
            if (count >= sum)
                break;
        }
        return recommendations;
    }
    /**
     * 获取最新书籍
     * @param request
     * @return
     */
//    public List<Recommendation> getNewBooks(GetNewBooksRequest request){
//        FindIterable<Document> documents = getMongoDatabase().getCollection(Constant.MONGODB_BOOK_COLLECTION).find().sort(Sorts.descending("issue"));
//        List<Recommendation> recommendations = new ArrayList<>();
//        for(Document item:documents){
//            recommendations.add(new Recommendation(item.getInteger("mid"),0D));
//        }
//        return recommendations.subList(0,recommendations.size()>request.getNum()?request.getNum():recommendations.size());
//    }

    /**
     * 模糊主题检索
     *
     * @param request
     * @return
     */
    public List<Recommendation> getFuzzBooks(GetFuzzySearchBooksRequest request) {
        FuzzyQueryBuilder queryBuilder = QueryBuilders.fuzzyQuery("name", request.getQuery());
        SearchResponse response = esClient.prepareSearch(Constant.ES_INDEX).setQuery(queryBuilder).setSize(request.getNum()).execute().actionGet();
        return parseESResponse(response);
    }

    //    public List<Recommendation> getGenresBooks(GetGenresBookRequest request){
//        FuzzyQueryBuilder queryBuilder = QueryBuilders.fuzzyQuery("genres",request.getGenres());
//        SearchResponse response = esClient.prepareSearch(Constant.ES_INDEX).setQuery(queryBuilder).setSize(request.getNum()).execute().actionGet();
//        return parseESResponse(response);
//    }
}
