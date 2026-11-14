package com.example.novelreader;

/** 一句话，携带在章节正文中的字符偏移，用于逐句高亮 */
public class Sentence {
    public final String text;
    public final int start;
    public final int end;

    public Sentence(String text, int start, int end) {
        this.text = text;
        this.start = start;
        this.end = end;
    }
}
