package com.km.model;

public class Recommendation {

    private int bid;

    private double score;

    public Recommendation(int bid,double score){
        this.bid = bid;
        this.score = score;
    }

    public int getBid() {
        return bid;
    }

    public void setBid(int bid) {
        this.bid = bid;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    @Override
    public int hashCode() {
        return bid;
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj)
            return true;
        if(!(obj instanceof Recommendation))
            return false;
        if(((Recommendation) obj).bid==this.bid)
            return true;
        return false;
    }

    @Override
    public String toString() {
        return "Recommendation{" +
                "bid=" + bid +
                ", score=" + score +
                '}';
    }
}
