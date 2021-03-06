package com.km.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Tag {

    @JsonIgnore
    private int _id;

    private int uid;

    private int bid;

    private String tag;

    private long timestamp;

    public Tag(int uid, int bid, String tag, long timestamp) {
        this.uid = uid;
        this.bid = bid;
        this.tag = tag;
        this.timestamp = timestamp;
    }

    public int get_id() {
        return _id;
    }

    public void set_id(int _id) {
        this._id = _id;
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

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
