package com.example.novelreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** 书架：内置示例 + 导入本地 TXT + 语音引擎选择 */
public class MainActivity extends Activity {

    private static final int REQ_IMPORT = 1001;
    private static final String KEY_ENGINE = "engine_pkg";
    private static final String KEY_ENGINE_SET = "engine_user_set";

    private ListView listBooks;
    private TextView tvEngine;
    private ArrayAdapter<Book> adapter;
    private final List<Book> books = new ArrayList<>();
    private TtsEngine probe;
    private List<TextToSpeech.EngineInfo> engines = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listBooks = findViewById(R.id.listBooks);
        tvEngine = findViewById(R.id.tvEngine);
        Button btnImport = findViewById(R.id.btnImport);
        Button btnEngine = findViewById(R.id.btnEngine);

        adapter = new ArrayAdapter<Book>(this, 0, books) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView;
                if (v == null) v = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.item_book, parent, false);
                Book b = getItem(position);
                ((TextView) v.findViewById(R.id.tvTitle)).setText(b.title);
                String info = b.builtIn ? "内置示例 · 公版文本" : "本地导入 · TXT";
                int[] pos = BookStore.getProgress(MainActivity.this, b.id);
                if (pos[0] > 0 || pos[1] > 0) info += " · 上次读到第 " + (pos[0] + 1) + " 章";
                ((TextView) v.findViewById(R.id.tvInfo)).setText(info);
                return v;
            }
        };
        listBooks.setAdapter(adapter);

        listBooks.setOnItemClickListener((parent, view, position, id) -> {
            Intent it = new Intent(this, ReaderActivity.class);
            it.putExtra("book_id", books.get(position).id);
            startActivity(it);
        });

        btnImport.setOnClickListener(v -> {
            Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            it.addCategory(Intent.CATEGORY_OPENABLE);
            it.setType("*/*");
            it.putExtra(Intent.EXTRA_MIME_TYPES,
                    new String[]{"text/plain", "application/octet-stream"});
            startActivityForResult(it, REQ_IMPORT);
        });

        btnEngine.setOnClickListener(v -> showEngineDialog());

        probe = new TtsEngine(this, new TtsEngine.Callback() {
            @Override public void onReady(boolean ok) {
                if (ok) {
                    engines = probe.engines();
                    // 首次启动自动选定讯飞语音引擎（若手机已安装）
                    if (!BookStore.prefs(MainActivity.this).getBoolean(KEY_ENGINE_SET, false)) {
                        String auto = probe.autoEngine();
                        if (auto != null) {
                            BookStore.prefs(MainActivity.this).edit()
                                    .putString(KEY_ENGINE, auto).apply();
                            BookStore.prefs(MainActivity.this).edit()
                                    .putBoolean(KEY_ENGINE_SET, true).apply();
                        }
                    }
                }
                runOnUiThread(() -> updateEngineLabel(ok));
            }
            @Override public void onSentenceStart(int i) {}
            @Override public void onSentenceDone(int i) {}
            @Override public void onError(String m) {}
        });
        probe.init(null);
        updateEngineLabel(false);
    }

    private void updateEngineLabel(boolean ready) {
        if (!ready && engines.isEmpty()) {
            tvEngine.setText("语音引擎：检测中…（需系统已安装中文 TTS）");
            return;
        }
        String pkg = BookStore.prefs(this).getString(KEY_ENGINE, "");
        String label = "系统默认";
        for (TextToSpeech.EngineInfo e : engines) {
            if (e.name.equals(pkg)) {
                label = e.label + (e.name.toLowerCase().contains("iflytek") ? "（讯飞音色）" : "");
                break;
            }
        }
        tvEngine.setText("当前语音引擎：" + label + " · 已发现 " + engines.size() + " 个引擎");
    }

    private void showEngineDialog() {
        if (engines.isEmpty()) {
            Toast.makeText(this, "未检测到语音引擎，请先在手机上安装中文 TTS", Toast.LENGTH_LONG).show();
            return;
        }
        List<String> labels = new ArrayList<>();
        List<String> pkgs = new ArrayList<>();
        labels.add("系统默认");
        pkgs.add("");
        for (TextToSpeech.EngineInfo e : engines) {
            labels.add(e.label + (e.name.toLowerCase().contains("iflytek") ? "（讯飞）" : ""));
            pkgs.add(e.name);
        }
        new AlertDialog.Builder(this)
                .setTitle("选择语音引擎")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    BookStore.prefs(this).edit()
                            .putString(KEY_ENGINE, pkgs.get(which))
                            .putBoolean(KEY_ENGINE_SET, true)
                            .apply();
                    updateEngineLabel(true);
                    Toast.makeText(this, "已选择：" + labels.get(which) + "\n重新进入书籍后生效",
                            Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            String name = queryName(uri);
            String id = BookStore.importTxt(this, uri, name);
            Toast.makeText(this, "导入成功，点击开始阅读", Toast.LENGTH_SHORT).show();
            refreshBooks();
            Intent it = new Intent(this, ReaderActivity.class);
            it.putExtra("book_id", id);
            startActivity(it);
        } catch (Exception e) {
            Toast.makeText(this, "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String queryName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) {}
        return "导入的小说.txt";
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshBooks();
    }

    private void refreshBooks() {
        books.clear();
        books.addAll(BookStore.list(this));
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        if (probe != null) probe.shutdown();
        super.onDestroy();
    }
}
