package com.km.request;

//混合推荐
public class GetHybridRecommendationRequest {

    //实时推荐中的结果占比
    private double streamShare;

    //基于ALS的用户矩阵离线结果占比
    private double alsUserShare;

    //基于ALS的图书矩阵离线结果占比
    private double alsBookShare;

    private int uid;

    private int bid;

    private int num;

    public GetHybridRecommendationRequest(double streamShare, double alsUserShare, double alsBookShare, int uid, int bid, int num) {
        this.streamShare = streamShare;
        this.alsUserShare = alsUserShare;
        this.alsBookShare = alsBookShare;
        this.uid = uid;
        this.bid = bid;
        this.num = num;
    }

    public double getStreamShare() {
        return streamShare;
    }

    public void setStreamShare(double streamShare) {
        this.streamShare = streamShare;
    }

    public double getAlsUserShare() {
        return alsUserShare;
    }

    public void setAlsUserShare(double alsUserShare) {
        this.alsUserShare = alsUserShare;
    }

    public double getAlsBookShare() {
        return alsBookShare;
    }

    public void setAlsBookShare(double alsBookShare) {
        this.alsBookShare = alsBookShare;
    }

    public int getUid() {
        return uid;
    }

    public void setUid(int uid) {
        this.uid = uid;
    }

    public int getBid() {
        return bid;
    }

    public void setBid(int bid) {
        this.bid = bid;
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }
}
