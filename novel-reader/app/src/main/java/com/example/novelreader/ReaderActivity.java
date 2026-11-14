package com.example.novelreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.Voice;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 阅读页（仿主流中文阅读 App 的沉浸式布局）
 * - 点正文显隐顶部/底部工具栏
 * - 底部设置面板：字号 / 行距 / 背景主题 / 亮度
 * - 底部朗读条：逐句播放、语速、音色、定时
 * - 正文逐句高亮 + 自动滚动，进度按「章/句」记忆
 */
public class ReaderActivity extends Activity {

    // 五套背景主题：羊皮纸 / 纯白 / 护眼 / 浅灰 / 夜间
    private static final int[] THEME_BG = {
            0xFFF5EBD5, 0xFFFFFFFF, 0xFFCCE8CF, 0xFFDADADA, 0xFF2B2B2B};
    private static final int[] THEME_TEXT = {
            0xFF3A3226, 0xFF222222, 0xFF27372B, 0xFF333333, 0xFFCBCBCB};
    private static final int[] THEME_SUB = {
            0xFF8B8069, 0xFF888888, 0xFF5A6E5D, 0xFF666666, 0xFF8A8A8A};
    private static final int[] THEME_HL = {
            0xFFFFE0A3, 0xFFFFE9A8, 0xFFA8D5B5, 0xFFBDBDBD, 0xFF6B5B2E};
    private static final int[] THEME_BAR = {
            0xEEF7EFE0, 0xEEFFFFFF, 0xEEDCF0DF, 0xEEE6E6E6, 0xEE333333};
    private static final String[] THEME_NAMES = {"羊皮纸", "纯白", "护眼", "浅灰", "夜间"};
    private static final String[] LINE_NAMES = {"紧凑", "标准", "宽松"};

    private View readerRoot;
    private ScrollView scrollContent;
    private TextView tvChapterTitle, tvContent, tvFooter, tvBookTitle;
    private ViewGroup topBar, bottomBar, settingsPanel, ttsBar;
    private TextView tvFontSize, tvBrightness, tvSpeed, tvStatus, btnPlay;
    private SeekBar seekProgress, seekSpeed, seekBrightness;
    private TextView[] swatches;
    private TextView[] lineButtons;

    private Book book;
    private List<Chapter> chapters = new ArrayList<>();
    private int chapterIndex = 0, sentenceIndex = 0;
    private boolean playing = false, barsVisible = false;
    private TtsEngine tts;
    private List<Voice> voices = new ArrayList<>();
    private int voiceIndex = 0;
    private float speed = 1.0f;
    private int fontSize = 20, lineSpacingIdx = 1, themeIdx = 0;
    private float lineSpacing = 1.5f;
    private int timerMinutes = 0;
    private long timerEndAt = 0;
    private final Handler handler = new Handler();
    private final Runnable timerTick = new Runnable() {
        @Override public void run() {
            if (timerMinutes <= 0) return;
            long remain = timerEndAt - System.currentTimeMillis();
            if (remain <= 0) {
                stopPlay();
                timerMinutes = 0;
                tvStatus.setText("定时结束，已停止朗读");
                return;
            }
            tvStatus.setText("朗读中 · 定时剩余 " + (remain / 60000 + 1) + " 分钟");
            handler.postDelayed(this, 30000);
        }
    };

    private SharedPreferences prefs() { return BookStore.prefs(this); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);

        readerRoot = findViewById(R.id.readerRoot);
        scrollContent = findViewById(R.id.scrollContent);
        tvChapterTitle = findViewById(R.id.tvChapterTitle);
        tvContent = findViewById(R.id.tvContent);
        tvFooter = findViewById(R.id.tvFooter);
        tvBookTitle = findViewById(R.id.tvBookTitle);
        topBar = findViewById(R.id.topBar);
        bottomBar = findViewById(R.id.bottomBar);
        settingsPanel = findViewById(R.id.settingsPanel);
        ttsBar = findViewById(R.id.ttsBar);
        tvFontSize = findViewById(R.id.tvFontSize);
        tvBrightness = findViewById(R.id.tvBrightness);
        tvSpeed = findViewById(R.id.tvSpeed);
        tvStatus = findViewById(R.id.tvStatus);
        btnPlay = findViewById(R.id.btnPlay);
        seekProgress = findViewById(R.id.seekProgress);
        seekSpeed = findViewById(R.id.seekSpeed);
        seekBrightness = findViewById(R.id.seekBrightness);

        swatches = new TextView[]{
                findViewById(R.id.theme0), findViewById(R.id.theme1),
                findViewById(R.id.theme2), findViewById(R.id.theme3),
                findViewById(R.id.theme4)};
        lineButtons = new TextView[]{
                findViewById(R.id.btnLine1), findViewById(R.id.btnLine2),
                findViewById(R.id.btnLine3)};

        String id = getIntent().getStringExtra("book_id");
        book = BookStore.find(this, id);
        if (book == null) { finish(); return; }

        loadSettings();
        applyTheme();
        bindControls();
        setupTapToggle();

        tvStatus.setText("正在解析章节…");
        new Thread(() -> {
            try {
                String text = BookStore.loadText(this, book);
                chapters = ChapterParser.parse(text);
                runOnUiThread(() -> {
                    int[] pos = BookStore.getProgress(this, book.id);
                    chapterIndex = Math.min(Math.max(pos[0], 0), Math.max(chapters.size() - 1, 0));
                    sentenceIndex = Math.max(pos[1], 0);
                    showChapter();
                    initTts();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "读取失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // ---------------- 外观设置 ----------------
    private void loadSettings() {
        fontSize = prefs().getInt("font_size", 20);
        lineSpacingIdx = prefs().getInt("line_spacing", 1);
        lineSpacing = new float[]{1.25f, 1.5f, 1.85f}[lineSpacingIdx];
        themeIdx = prefs().getInt("theme", 0);
        speed = prefs().getFloat("speed", 1.0f);
    }

    private void applyTheme() {
        readerRoot.setBackgroundColor(THEME_BG[themeIdx]);
        tvContent.setTextColor(THEME_TEXT[themeIdx]);
        tvChapterTitle.setTextColor(THEME_TEXT[themeIdx]);
        tvFooter.setTextColor(THEME_SUB[themeIdx]);
        tvContent.setTextSize(fontSize);
        tvContent.setLineSpacing(0, lineSpacing);
        tvFontSize.setText(String.valueOf(fontSize));

        topBar.setBackgroundColor(THEME_BAR[themeIdx]);
        bottomBar.setBackgroundColor(THEME_BAR[themeIdx]);
        settingsPanel.setBackgroundColor(THEME_BAR[themeIdx]);
        ttsBar.setBackgroundColor(THEME_BAR[themeIdx]);
        tintText(topBar, THEME_TEXT[themeIdx]);
        tintText(bottomBar, THEME_TEXT[themeIdx]);
        tintText(settingsPanel, THEME_TEXT[themeIdx]);
        tintText(ttsBar, THEME_TEXT[themeIdx]);
        btnPlay.setTextColor(0xFFFFFFFF);
        tvStatus.setTextColor(THEME_SUB[themeIdx]);
        tvBrightness.setTextColor(THEME_SUB[themeIdx]);
        tvSpeed.setTextColor(THEME_SUB[themeIdx]);

        for (int i = 0; i < swatches.length; i++) {
            swatches[i].setBackgroundColor(THEME_BG[i]);
            swatches[i].setTextColor(THEME_TEXT[i]);
            swatches[i].setText(i == themeIdx ? "✓ " + THEME_NAMES[i] : THEME_NAMES[i]);
        }
        for (int i = 0; i < lineButtons.length; i++) {
            lineButtons[i].setText(i == lineSpacingIdx ? "✓ " + LINE_NAMES[i] : LINE_NAMES[i]);
        }

        if (!chapters.isEmpty()) highlight(sentenceIndex);
    }

    private void tintText(ViewGroup root, int color) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View v = root.getChildAt(i);
            if (v instanceof TextView) ((TextView) v).setTextColor(color);
            else if (v instanceof ViewGroup) tintText((ViewGroup) v, color);
        }
    }

    private void setFontSize(int size) {
        fontSize = Math.max(14, Math.min(30, size));
        prefs().edit().putInt("font_size", fontSize).apply();
        applyTheme();
    }

    private void setLineSpacing(int idx) {
        lineSpacingIdx = idx;
        lineSpacing = new float[]{1.25f, 1.5f, 1.85f}[idx];
        prefs().edit().putInt("line_spacing", idx).apply();
        applyTheme();
    }

    private void selectTheme(int idx) {
        themeIdx = idx;
        prefs().edit().putInt("theme", idx).apply();
        applyTheme();
    }

    private void setBrightness(int progress) {
        float b = 0.1f + progress / 100f * 0.9f;
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = b;
        getWindow().setAttributes(lp);
        tvBrightness.setText(progress + "%");
        prefs().edit().putInt("brightness", progress).apply();
    }

    // ---------------- 工具栏交互 ----------------
    private void setupTapToggle() {
        GestureDetector detector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onDown(MotionEvent e) { return true; }
                    @Override public boolean onSingleTapUp(MotionEvent e) {
                        toggleBars();
                        return true;
                    }
                });
        scrollContent.setOnTouchListener((v, event) -> {
            detector.onTouchEvent(event);
            return false; // 交还给 ScrollView 处理滚动
        });
    }

    private void toggleBars() {
        barsVisible = !barsVisible;
        topBar.setVisibility(barsVisible ? View.VISIBLE : View.GONE);
        if (barsVisible) {
            settingsPanel.setVisibility(View.GONE);
            ttsBar.setVisibility(View.GONE);
            bottomBar.setVisibility(View.VISIBLE);
        } else {
            bottomBar.setVisibility(View.GONE);
            settingsPanel.setVisibility(View.GONE);
            ttsBar.setVisibility(View.GONE);
        }
    }

    private void openPanel(View panel) {
        barsVisible = true;
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.GONE);
        settingsPanel.setVisibility(panel == settingsPanel ? View.VISIBLE : View.GONE);
        ttsBar.setVisibility(panel == ttsBar ? View.VISIBLE : View.GONE);
    }

    private void bindControls() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnMore).setOnClickListener(v -> showMoreMenu());

        findViewById(R.id.btnCatalog).setOnClickListener(v -> showChapterDialog());
        findViewById(R.id.btnTheme).setOnClickListener(v -> openPanel(settingsPanel));
        findViewById(R.id.btnSettings).setOnClickListener(v -> openPanel(settingsPanel));
        findViewById(R.id.btnRead).setOnClickListener(v -> openPanel(ttsBar));
        findViewById(R.id.btnTimerQuick).setOnClickListener(v -> openPanel(ttsBar));

        findViewById(R.id.btnPrevCh).setOnClickListener(v -> switchChapter(-1));
        findViewById(R.id.btnNextCh).setOnClickListener(v -> switchChapter(1));

        seekProgress.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!fromUser || chapters.isEmpty()) return;
                List<Sentence> ss = chapterSentences();
                int target = Math.round(p / 100f * Math.max(ss.size() - 1, 0));
                sentenceIndex = target;
                highlight(target);
                BookStore.saveProgress(ReaderActivity.this, book.id, chapterIndex, target);
                if (playing) startPlay();
            }
        });

        findViewById(R.id.btnFontMinus).setOnClickListener(v -> setFontSize(fontSize - 2));
        findViewById(R.id.btnFontPlus).setOnClickListener(v -> setFontSize(fontSize + 2));
        for (int i = 0; i < lineButtons.length; i++) {
            final int idx = i;
            lineButtons[i].setOnClickListener(v -> setLineSpacing(idx));
        }
        for (int i = 0; i < swatches.length; i++) {
            final int idx = i;
            swatches[i].setOnClickListener(v -> selectTheme(idx));
        }
        int savedBrightness = prefs().getInt("brightness", -1);
        if (savedBrightness >= 0) {
            seekBrightness.setProgress(savedBrightness);
            setBrightness(savedBrightness);
        } else {
            tvBrightness.setText("跟随");
        }
        seekBrightness.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) setBrightness(p);
            }
        });
        findViewById(R.id.btnCloseSettings).setOnClickListener(v -> toggleBars());

        btnPlay.setOnClickListener(v -> { if (playing) stopPlay(); else { openPanel(ttsBar); startPlay(); } });
        findViewById(R.id.btnPrevSentence).setOnClickListener(v -> jumpSentence(-1));
        findViewById(R.id.btnNextSentence).setOnClickListener(v -> jumpSentence(1));
        findViewById(R.id.btnCloseTts).setOnClickListener(v -> toggleBars());
        findViewById(R.id.btnVoice).setOnClickListener(v -> cycleVoice());
        findViewById(R.id.btnTimer).setOnClickListener(v -> cycleTimer());

        int sp = Math.round((speed - 0.5f) * 20);
        seekSpeed.setProgress(Math.max(0, Math.min(20, sp)));
        tvSpeed.setText(String.format("%.1fx", speed));
        seekSpeed.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                speed = 0.5f + p / 20f * 1.5f;
                tvSpeed.setText(String.format("%.1fx", speed));
                if (tts != null) tts.setSpeed(speed);
                prefs().edit().putFloat("speed", speed).apply();
            }
        });
    }

    private void showMoreMenu() {
        new AlertDialog.Builder(this)
                .setTitle(book.title)
                .setItems(new String[]{"重置本书阅读进度", "关于本应用"}, (d, which) -> {
                    if (which == 0) {
                        BookStore.saveProgress(this, book.id, 0, 0);
                        chapterIndex = 0; sentenceIndex = 0;
                        showChapter();
                        Toast.makeText(this, "进度已重置", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "听书 · 小说朗读 v1.0\n本地 TXT 朗读，文本不上传", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    // ---------------- 朗读 ----------------
    private void initTts() {
        String engine = prefs().getString("engine_pkg", "");
        tts = new TtsEngine(this, new TtsEngine.Callback() {
            @Override public void onReady(boolean ok) {
                runOnUiThread(() -> {
                    if (!ok) { tvStatus.setText("语音引擎初始化失败，请检查系统 TTS"); return; }
                    tts.setSpeed(speed);
                    voices = tts.chineseVoices();
                    tvStatus.setText("就绪 · 共 " + chapters.size() + " 章 · 本章 " + chapterSentences().size() + " 句");
                });
            }
            @Override public void onSentenceStart(int i) {
                runOnUiThread(() -> {
                    sentenceIndex = i;
                    highlight(i);
                    tvStatus.setText("朗读中 · 第 " + (i + 1) + "/" + chapterSentences().size() + " 句");
                    updateFooter();
                    BookStore.saveProgress(ReaderActivity.this, book.id, chapterIndex, i);
                });
            }
            @Override public void onSentenceDone(int i) {
                if (i >= chapterSentences().size() - 1 && chapterIndex < chapters.size() - 1) {
                    runOnUiThread(() -> {
                        if (!playing) return;
                        switchChapter(1);
                        startPlay();
                    });
                }
            }
            @Override public void onError(String m) {
                runOnUiThread(() -> {
                    playing = false;
                    btnPlay.setText("▶  开始朗读");
                    tvStatus.setText(m);
                });
            }
        });
        tts.init(engine.isEmpty() ? null : engine);
    }

    private List<Sentence> chapterSentences() {
        if (chapters.isEmpty()) return new ArrayList<>();
        return chapters.get(chapterIndex).sentences;
    }

    private void startPlay() {
        if (tts == null || !tts.isReady()) {
            Toast.makeText(this, "语音引擎尚未就绪", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Sentence> ss = chapterSentences();
        if (ss.isEmpty()) return;
        if (sentenceIndex >= ss.size()) sentenceIndex = 0;
        tts.speak(ss, sentenceIndex);
        playing = true;
        btnPlay.setText("❚❚  暂停朗读");
        tvStatus.setText("朗读中…");
    }

    private void stopPlay() {
        if (tts != null) tts.stop();
        playing = false;
        btnPlay.setText("▶  开始朗读");
        tvStatus.setText("已暂停");
    }

    private void jumpSentence(int delta) {
        if (chapters.isEmpty()) return;
        int size = chapterSentences().size();
        sentenceIndex = Math.max(0, Math.min(size - 1, sentenceIndex + delta));
        highlight(sentenceIndex);
        BookStore.saveProgress(this, book.id, chapterIndex, sentenceIndex);
        updateFooter();
        if (playing) startPlay();
    }

    private void cycleVoice() {
        if (voices.isEmpty() && tts != null) voices = tts.chineseVoices();
        if (voices.isEmpty()) {
            Toast.makeText(this, "当前引擎没有可切换的中文音色", Toast.LENGTH_SHORT).show();
            return;
        }
        voiceIndex = (voiceIndex + 1) % voices.size();
        Voice v = voices.get(voiceIndex);
        if (tts != null) tts.setVoice(v);
        tvStatus.setText("音色：" + v.getName());
    }

    private void cycleTimer() {
        int[] opts = {0, 15, 30, 60};
        int next = 0;
        for (int i = 0; i < opts.length; i++) if (opts[i] == timerMinutes) next = (i + 1) % opts.length;
        timerMinutes = opts[next];
        handler.removeCallbacks(timerTick);
        if (timerMinutes == 0) {
            tvStatus.setText("定时已取消");
        } else {
            timerEndAt = System.currentTimeMillis() + timerMinutes * 60000L;
            handler.postDelayed(timerTick, 30000);
            tvStatus.setText("定时停止：" + timerMinutes + " 分钟");
        }
        ((TextView) findViewById(R.id.btnTimer)).setText("定时：" + (timerMinutes == 0 ? "关" : timerMinutes + "分"));
    }

    // ---------------- 正文显示 ----------------
    private void showChapter() {
        if (chapters.isEmpty()) return;
        Chapter ch = chapters.get(chapterIndex);
        tvBookTitle.setText(book.title + " · " + ch.title);
        tvChapterTitle.setText(ch.title);
        sentenceIndex = Math.min(sentenceIndex, Math.max(ch.sentences.size() - 1, 0));
        highlight(sentenceIndex);
        updateFooter();
        scrollContent.post(() -> scrollContent.scrollTo(0, 0));
    }

    private void highlight(int index) {
        if (chapters.isEmpty()) return;
        Chapter ch = chapters.get(chapterIndex);
        SpannableString sp = new SpannableString(ch.content);
        if (index >= 0 && index < ch.sentences.size()) {
            Sentence s = ch.sentences.get(index);
            int end = Math.min(s.end, sp.length());
            int start = Math.min(s.start, end);
            sp.setSpan(new BackgroundColorSpan(THEME_HL[themeIdx]),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tvContent.setText(sp);
        tvContent.post(() -> {
            Layout layout = tvContent.getLayout();
            if (layout == null || index < 0 || index >= ch.sentences.size()) return;
            int offset = Math.min(ch.sentences.get(index).start, Math.max(ch.content.length() - 1, 0));
            int y = layout.getLineTop(layout.getLineForOffset(offset));
            scrollContent.smoothScrollTo(0, Math.max(0, tvContent.getTop() + y - 100));
        });
        if (!seekProgress.isPressed()) {
            int size = Math.max(ch.sentences.size() - 1, 1);
            seekProgress.setProgress(Math.round(index * 100f / size));
        }
    }

    private void updateFooter() {
        if (chapters.isEmpty()) return;
        int size = Math.max(chapterSentences().size(), 1);
        int percent = Math.round((sentenceIndex + 1) * 100f / size);
        tvFooter.setText("第 " + (chapterIndex + 1) + "/" + chapters.size() + " 章 · 第 "
                + (sentenceIndex + 1) + "/" + size + " 句 · 本章 " + percent + "%");
    }

    private void switchChapter(int delta) {
        if (chapters.isEmpty()) return;
        boolean wasPlaying = playing;
        stopPlay();
        chapterIndex += delta;
        if (chapterIndex < 0) { chapterIndex = 0; Toast.makeText(this, "已经是第一章", Toast.LENGTH_SHORT).show(); }
        if (chapterIndex >= chapters.size()) {
            chapterIndex = chapters.size() - 1;
            Toast.makeText(this, "已经是最后一章", Toast.LENGTH_SHORT).show();
        }
        sentenceIndex = 0;
        showChapter();
        BookStore.saveProgress(this, book.id, chapterIndex, 0);
        if (wasPlaying) startPlay();
    }

    private void showChapterDialog() {
        if (chapters.isEmpty()) return;
        String[] titles = new String[chapters.size()];
        for (int i = 0; i < chapters.size(); i++) titles[i] = (i + 1) + ". " + chapters.get(i).title;
        new AlertDialog.Builder(this)
                .setTitle("目录（共 " + chapters.size() + " 章）")
                .setItems(titles, (d, which) -> {
                    stopPlay();
                    chapterIndex = which;
                    sentenceIndex = 0;
                    showChapter();
                    BookStore.saveProgress(this, book.id, chapterIndex, 0);
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private static abstract class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar s) {}
        @Override public void onStopTrackingTouch(SeekBar s) {}
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(timerTick);
        if (book != null) BookStore.saveProgress(this, book.id, chapterIndex, sentenceIndex);
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
