package com.km.request;
//获取书籍类别的Top书籍
public class GetGenresTopBookRequest {
    private String genres;
    private int num;

    public GetGenresTopBookRequest(String genres, int num) {
        this.genres = genres;
        this.num = num;
    }

    public String getGenres() {
        return genres;
    }

    public void setGenres(String genres) {
        this.genres = genres;
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }
}
