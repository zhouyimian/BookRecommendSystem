package com.km.utils;

import com.km.model.Recommendation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;

public class FixSizedPriorityQueue {
    private PriorityQueue<Recommendation> queue;
    private int maxSize; //堆的最大容量

    public FixSizedPriorityQueue(int maxSize) {
        if (maxSize <= 0) throw new IllegalArgumentException();
        this.maxSize = maxSize;
        this.queue = new PriorityQueue(maxSize, new Comparator<Recommendation>() {
            @Override
            public int compare(Recommendation o1, Recommendation o2) {
                return o2.getScore()-o1.getScore()>0?1:0;
            }
        });
    }

    public void add(Recommendation recommendation){
        if(queue.size() < maxSize){ //未达到最大容量，直接添加
            queue.add(recommendation);
        }else{ //队列已满
            Recommendation peek = queue.peek();
            if(recommendation.getScore()-peek.getScore() > 0){
                queue.poll();
                queue.add(recommendation);
            }
        }
    }

    public PriorityQueue<Recommendation> getQueue() {
        return queue;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public static void main(String[] args) {
        long num = 1000240;
        System.out.println((int)num);
    }
}
