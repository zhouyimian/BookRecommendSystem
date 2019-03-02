package com.km.rest;

import com.km.java.model.Constant;
import com.km.model.*;
import com.km.request.*;
import com.km.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Controller
@RequestMapping("/rest/books")
public class BookRestApi {

    @Autowired
    private RecommenderService recommenderService;
    @Autowired
    private UserService userService;
    @Autowired
    private BookService bookService;
    @Autowired
    private TagService tagService;
    @Autowired
    private RatingService ratingService;

    private Logger logger = LoggerFactory.getLogger(BookRestApi.class);

    //====================================主页========================================
    //提供获取实时推荐信息接口
    @GetMapping("/stream")
    @ResponseBody
    public Model getRealtimeRecommendation(@RequestParam("uid") int uid,@RequestParam("bid") long bid, @RequestParam("num") int num, Model model) {
        List<Recommendation> recommendations = recommenderService
                .getHybridRecommendations(new GetHybridRecommendationRequest(0.4,0.3,0.3,uid,(int)bid,num));
        //冷启动
//        if (recommendations.size() == 0) {
//            Random random = new Random();
//            recommendations = recommenderService.getGenresTopBooks(new GetGenresTopBookRequest(user.getGenres().get(random.nextInt(user.getGenres().size())), num));
//        }
//        List<Recommendation> recommendations = recommenderService
//                .getStreamRecsBooks(new GetStreamRecsRequest(uid,bid,num));
//        List<Integer> ids = new ArrayList<>();
//        for (Recommendation recommendation : recommendations) {
//            ids.add(recommendation.getBid());
//        }
//        List<Book> result = bookService.getBooksByBids(ids);
        model.addAttribute("success", true);
        model.addAttribute("books", recommendations);
        for(Recommendation item : recommendations){
            System.out.println(item);
        }
        return model;
    }

    //提供获取离线推荐信息接口
    @GetMapping("/offline")
    @ResponseBody
    public Model getOfflineRecommendation(@RequestParam("username") String username, @RequestParam("num") int num, Model model) {
        User user = userService.findUserByUsername(username);
        List<Recommendation> recommendations = recommenderService.getUserCFBooks(new GetUserCFRequest(user.getUid(), num));
        //冷启动
        if (recommendations.size() == 0) {
            Random random = new Random();
            recommendations = recommenderService.getGenresTopBooks(new GetGenresTopBookRequest(user.getGenres().get(random.nextInt(user.getGenres().size())), num));
        }

        List<Integer> ids = new ArrayList<>();
        for (Recommendation recommendation : recommendations) {
            ids.add(recommendation.getBid());
        }
        List<Book> result = bookService.getBooksByBids(ids);
        model.addAttribute("success", true);
        model.addAttribute("books", result);
        return model;
    }


    //提供获取热门推荐信息接口
//    @GetMapping("/hot")
//    @ResponseBody
//    public Model getHotRecommendation(@RequestParam("num") int num, Model model) {
//        model.addAttribute("success",true);
//        model.addAttribute("books",recommenderService.getHotRecommendations(new GetHotRecommendationRequest(num)));
//        return model;
//    }

    //提供获取优质书籍信息接口
    @GetMapping("/rate")
    @ResponseBody
    public Model getRateMoreRecommendation(@RequestParam("num") int num, Model model) {
        model.addAttribute("success",true);
        model.addAttribute("books",recommenderService.getRateModeBooks(new GetRateMoreBooksRequest(num)));
        return model;
    }

    //=======================模糊检索=============================
    @GetMapping("/getFuzzySearchBooks")
    @ResponseBody
    public Model getFuzzySearchBooks(@RequestParam("query") String query,@RequestParam("num") int num, Model model) {
        model.addAttribute("success",true);
        model.addAttribute("books",recommenderService.getFuzzBooks(new GetFuzzySearchBooksRequest(query,num)));
        return model;
    }

    //=====================单本书籍详细页面=========================
    @GetMapping("/info/{bid}")
    @ResponseBody
    public Model getBookInfo(@PathVariable("bid") int bid, Model model) {
        model.addAttribute("success",true);
        model.addAttribute("books",bookService.findBookInfo(bid));
        return model;
    }

    //给书籍打标签
    @GetMapping("/addtag/{bid}")
    @ResponseBody
    public Model addTagToBook(@PathVariable("bid") int bid, @RequestParam("username") String username ,@RequestParam("tagname") String tagname, Model model) {
        User user = userService.findUserByUsername(username);
        Tag tag =new Tag(user.getUid(),bid,tagname,System.currentTimeMillis()/1000);
        tagService.addTagToBook(tag);
        return null;
    }

    //获取书籍的所有标签信息
    @GetMapping("/tags/{bid}")
    @ResponseBody
    public Model getBookTags(@PathVariable("bid") int bid, Model model) {
        model.addAttribute("success",true);
        model.addAttribute("books",tagService.getBookTags(bid));
        return model;
    }

    //获取书籍的混合推荐
    @GetMapping("/same/{uid}/{bid}")
    @ResponseBody
    public Model getSimBooksRecommendation(@PathVariable("uid") int uid,@PathVariable("bid") int bid,@RequestParam("num") int num, Model model) {
        model.addAttribute("success",true);
        model.addAttribute("books",recommenderService
                .getHybridRecommendations(new GetHybridRecommendationRequest(0.4,0.3,0.5,uid,bid,num)));
        return model;
    }

    //给书籍打分
    @PostMapping("/rate/{bid}")
    @ResponseBody
    public void rateBook(@RequestParam("username") String username,@PathVariable("bid") int bid,
                           @RequestParam("score") Double score, Model model) {
        User user = userService.findUserByUsername(username);
        Rating rating = new Rating(user.getUid(),bid,score,System.currentTimeMillis()/1000);
        ratingService.rateToBook(rating);
        //输出埋点日志
        logger.info(Constant.USER_RATING_LOG_PREFIX+rating.getUid()+"|"+rating.getBid()+"|"+rating.getScore()+"|"+rating.getTimestamp());
    }

//    //提供书籍类别查找
//    @GetMapping("/genres")
//    @ResponseBody
//    public Model getGenresBooks(@RequestParam("genres") String genres, @RequestParam("num")int num, Model model) {
//        model.addAttribute("success",true);
//        model.addAttribute("books",recommenderService
//                .getGenresBooks(new GetGenresBookRequest(genres,num)));
//        return model;
//    }

//    //提供用户所有的书籍评分记录
//    @GetMapping("/getUserRatings")
//    @ResponseBody
//    public Model getUserRatings(@RequestParam("username") String uername, Model model) {
//        return null;
//    }
//
//    //获取图表数据
//    @GetMapping("/getUserChart")
//    @ResponseBody
//    public Model getUserChart(@RequestParam("username") String uername, Model model) {
//        return null;
//    }
}
