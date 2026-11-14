package com.example.novelreader;

import java.util.List;

/** 书架上的一本书。chapters 为运行时解析结果，不持久化 */
public class Book {
    public final String id;
    public final String title;
    public final String fileName;
    public final boolean builtIn;
    public transient List<Chapter> chapters;

    public Book(String id, String title, String fileName, boolean builtIn) {
        this.id = id;
        this.title = title;
        this.fileName = fileName;
        this.builtIn = builtIn;
    }
}
