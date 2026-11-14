package com.example.novelreader;

import java.util.List;

/** 一个章节：标题 + 正文 + 按标点切好的句子列表 */
public class Chapter {
    public final String title;
    public final String content;
    public final List<Sentence> sentences;

    public Chapter(String title, String content, List<Sentence> sentences) {
        this.title = title;
        this.content = content;
        this.sentences = sentences;
    }
}
