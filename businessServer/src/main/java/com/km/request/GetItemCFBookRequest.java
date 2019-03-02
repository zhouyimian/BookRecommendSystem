package com.km.request;

public class GetItemCFBookRequest {
    private int bid;

    private int num;

    public GetItemCFBookRequest(int bid, int num) {
        this.bid = bid;
        this.num = num;
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
