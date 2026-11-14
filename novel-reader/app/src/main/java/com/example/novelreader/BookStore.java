package com.example.novelreader;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 书架数据 + 文本读取（自动识别 UTF-8 / GBK） */
public final class BookStore {
    private static final String PREF = "novel_reader";
    private static final String KEY_BOOKS = "books";
    private static final String SAMPLE_ID = "builtin_sample";
    private static final String SAMPLE_ASSET = "sample_novel.txt";

    private BookStore() {}

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static List<Book> list(Context c) {
        List<Book> books = new ArrayList<>();
        books.add(new Book(SAMPLE_ID, "示例小说 · 城南旧灯", SAMPLE_ASSET, true));
        try {
            String json = prefs(c).getString(KEY_BOOKS, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                books.add(new Book(o.getString("id"), o.getString("title"),
                        o.getString("file"), false));
            }
        } catch (Exception ignored) {}
        return books;
    }

    public static Book find(Context c, String id) {
        for (Book b : list(c)) if (b.id.equals(id)) return b;
        return null;
    }

    private static void save(Context c, List<Book> books) {
        JSONArray arr = new JSONArray();
        for (Book b : books) {
            if (b.builtIn) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("id", b.id);
                o.put("title", b.title);
                o.put("file", b.fileName);
                arr.put(o);
            } catch (Exception ignored) {}
        }
        prefs(c).edit().putString(KEY_BOOKS, arr.toString()).apply();
    }

    /** 从系统文件选择器导入 TXT，返回新书 id */
    public static String importTxt(Context c, Uri uri, String displayName) throws Exception {
        byte[] bytes = readAll(c, uri);
        String id = UUID.randomUUID().toString().substring(0, 8);
        String title = displayName == null ? "导入的小说" : displayName.replaceFirst("\\.[^.]+$", "");
        File dir = new File(c.getFilesDir(), "books");
        if (!dir.exists()) dir.mkdirs();
        File dest = new File(dir, id + ".txt");
        try (FileOutputStream out = new FileOutputStream(dest)) {
            out.write(bytes);
        }
        List<Book> books = list(c);
        books.add(new Book(id, title, dest.getName(), false));
        save(c, books);
        return id;
    }

    public static String loadText(Context c, Book b) throws Exception {
        byte[] bytes;
        if (b.builtIn) {
            try (InputStream in = c.getAssets().open(b.fileName)) {
                bytes = readAll(in);
            }
        } else {
            File f = new File(new File(c.getFilesDir(), "books"), b.fileName);
            try (InputStream in = new java.io.FileInputStream(f)) {
                bytes = readAll(in);
            }
        }
        return decode(bytes);
    }

    private static String decode(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        try {
            CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return dec.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, java.nio.charset.Charset.forName("GBK"));
        }
    }

    private static byte[] readAll(Context c, Uri uri) throws Exception {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("无法读取文件");
            return readAll(in);
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    // ---- 阅读进度 ----
    public static void saveProgress(Context c, String bookId, int chapter, int sentence) {
        prefs(c).edit()
                .putInt("pos_" + bookId + "_c", chapter)
                .putInt("pos_" + bookId + "_s", sentence)
                .apply();
    }

    public static int[] getProgress(Context c, String bookId) {
        SharedPreferences p = prefs(c);
        return new int[]{p.getInt("pos_" + bookId + "_c", 0), p.getInt("pos_" + bookId + "_s", 0)};
    }
}
