package com.km.request;
//获取书籍类别的Top书籍
public class GetNewBooksRequest {

    private int num;

    public GetNewBooksRequest(int num) {
        this.num = num;
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }
}
