package com.km.request;
//获取书籍类别的Top书籍
public class GetRateMoreBooksRequest {

    private int num;

    public GetRateMoreBooksRequest(int num) {
        this.num = num;
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }
}
