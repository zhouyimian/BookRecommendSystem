package com.km.request;

public class GetFuzzySearchBooksRequest {

    private String query;

    private int num;

    public GetFuzzySearchBooksRequest(String query, int num) {
        this.query = query;
        this.num = num;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }
}
