package com.example.novelreader;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 章节解析 + 分句。
 * 思路参考了商业阅读 App 的朗读模块：先把正文切成章，再把章切成句，
 * 每句记录字符偏移，播放时按句子回调做高亮与进度定位。此处为独立实现。
 */
public final class ChapterParser {

    private static final Pattern CHAPTER = Pattern.compile(
            "^\\s*(第[零〇一二三四五六七八九十百千两0-9]{1,12}[章节回卷部篇集][^\\n]{0,40}" +
            "|序章|楔子|引子|序言|尾声|后记|番外[^\\n]{0,20})\\s*$");

    private ChapterParser() {}

    public static List<Chapter> parse(String raw) {
        String text = raw == null ? "" : raw.replace("\r\n", "\n").replace('\r', '\n');
        List<Chapter> result = new ArrayList<>();
        String[] lines = text.split("\n", -1);

        List<int[]> heads = new ArrayList<>(); // {行号}
        for (int i = 0; i < lines.length; i++) {
            Matcher m = CHAPTER.matcher(lines[i]);
            if (m.matches() && lines[i].length() <= 50) heads.add(new int[]{i});
        }

        if (heads.isEmpty()) {
            // 没有章节标记：按空行/长度切成若干"段落"，保证朗读体验一致
            StringBuilder buf = new StringBuilder();
            for (String line : lines) {
                buf.append(line).append("\n");
                if (buf.length() >= 1800) {
                    addChapter(result, "第 " + (result.size() + 1) + " 段", buf.toString());
                    buf.setLength(0);
                }
            }
            if (buf.toString().trim().length() > 0) {
                addChapter(result, result.isEmpty() ? "全文" : "第 " + (result.size() + 1) + " 段", buf.toString());
            }
            return result;
        }

        if (heads.get(0)[0] > 0) {
            String pre = join(lines, 0, heads.get(0)[0]);
            if (pre.trim().length() > 0) addChapter(result, "开篇", pre);
        }
        for (int h = 0; h < heads.size(); h++) {
            int from = heads.get(h)[0];
            int to = (h + 1 < heads.size()) ? heads.get(h + 1)[0] : lines.length;
            String title = lines[from].trim();
            String body = join(lines, from + 1, to);
            addChapter(result, title, body);
        }
        return result;
    }

    private static void addChapter(List<Chapter> list, String title, String body) {
        String content = body.trim();
        if (content.isEmpty()) content = "（本章暂无正文）";
        list.add(new Chapter(title, content, splitSentences(content)));
    }

    private static String join(String[] lines, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to && i < lines.length; i++) sb.append(lines[i]).append("\n");
        return sb.toString();
    }

    /** 按中文/英文句末标点切句，保留标点，并记录原文字偏移 */
    public static List<Sentence> splitSentences(String content) {
        List<Sentence> list = new ArrayList<>();
        int n = content.length();
        int start = -1;
        for (int i = 0; i < n; i++) {
            char c = content.charAt(i);
            if (start < 0) {
                if (!Character.isWhitespace(c)) start = i;
                continue;
            }
            if (isEnd(c) || c == '\n') {
                int end = i + 1;
                // 吃掉句末的右引号/右括号
                while (end < n && isClosing(content.charAt(end))) end++;
                String s = content.substring(start, end).trim();
                if (!s.isEmpty()) list.add(new Sentence(s, start, end));
                start = -1;
                i = end - 1;
            }
        }
        if (start >= 0) {
            String s = content.substring(start, n).trim();
            if (!s.isEmpty()) list.add(new Sentence(s, start, n));
        }
        if (list.isEmpty()) list.add(new Sentence(content, 0, n));
        return list;
    }

    private static boolean isEnd(char c) {
        return c == '。' || c == '！' || c == '？' || c == '!' || c == '?'
                || c == '；' || c == ';' || c == '…';
    }

    private static boolean isClosing(char c) {
        return c == '”' || c == '’' || c == '」' || c == '』' || c == '）'
                || c == ')' || c == '"' || c == '\'' || c == '》';
    }
}
