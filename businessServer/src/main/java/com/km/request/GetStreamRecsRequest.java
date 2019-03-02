package com.km.request;
//实时推荐
public class GetStreamRecsRequest {

    private int uid;

    private int bid;

    private int num;

    public GetStreamRecsRequest(int uid,int bid, int num) {
        this.uid = uid;
        this.num = num;
        this.bid = bid;
    }

    public int getUid() {
        return uid;
    }

    public void setUid(int uid) {
        this.uid = uid;
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }

    public int getBid() {
        return bid;
    }

    public void setBid(int bid) {
        this.bid = bid;
    }
}
