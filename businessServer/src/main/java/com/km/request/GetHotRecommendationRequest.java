package com.km.request;
//获取书籍类别的Top书籍
public class GetHotRecommendationRequest {

    private int num;

    public GetHotRecommendationRequest(int num) {
        this.num = num;
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }
}
