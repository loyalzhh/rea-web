package com.example.novelreader;

import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 朗读引擎：基于 Android 系统 TextToSpeech。
 * 优先使用手机上的讯飞语音引擎（com.iflytek.speechcloud），
 * 未安装时回退系统默认引擎。预留 setVoice / 语速 / 逐句回调接口，
 * 便于以后替换为讯飞官方 SDK（需用户自有 APPID）。
 */
public class TtsEngine {

    public interface Callback {
        void onReady(boolean ok);
        void onSentenceStart(int index);
        void onSentenceDone(int index);
        void onError(String message);
    }

    public static final String IFLYTEK_ENGINE = "com.iflytek.speechcloud";

    private final Context ctx;
    private final Callback cb;
    private TextToSpeech tts;
    private boolean ready = false;
    private float speed = 1.0f;

    public TtsEngine(Context ctx, Callback cb) {
        this.ctx = ctx;
        this.cb = cb;
    }

    /** engine 传 null 表示使用系统默认引擎 */
    public void init(String engine) {
        shutdown();
        TextToSpeech.OnInitListener listener = status -> {
            ready = status == TextToSpeech.SUCCESS;
            if (ready) {
                tts.setLanguage(Locale.CHINESE);
                tts.setSpeechRate(speed);
                tts.setOnUtteranceProgressListener(progressListener);
            }
            cb.onReady(ready);
        };
        try {
            tts = (engine == null || engine.isEmpty())
                    ? new TextToSpeech(ctx, listener)
                    : new TextToSpeech(ctx, listener, engine);
        } catch (Exception e) {
            ready = false;
            cb.onReady(false);
        }
    }

    private final UtteranceProgressListener progressListener = new UtteranceProgressListener() {
        @Override public void onStart(String utteranceId) {
            int i = indexOf(utteranceId);
            if (i >= 0) cb.onSentenceStart(i);
        }
        @Override public void onDone(String utteranceId) {
            int i = indexOf(utteranceId);
            if (i >= 0) cb.onSentenceDone(i);
        }
        @Override @Deprecated public void onError(String utteranceId) {
            cb.onError("朗读引擎返回错误");
        }
        @Override public void onError(String utteranceId, int errorCode) {
            cb.onError("朗读出错 (code=" + errorCode + ")");
        }
    };

    private static int indexOf(String id) {
        if (id == null || !id.startsWith("s")) return -1;
        try { return Integer.parseInt(id.substring(1)); } catch (Exception e) { return -1; }
    }

    /** 已安装的 TTS 引擎列表；未就绪时返回空 */
    public List<TextToSpeech.EngineInfo> engines() {
        if (tts == null) return Collections.emptyList();
        try {
            List<TextToSpeech.EngineInfo> l = tts.getEngines();
            return l == null ? Collections.emptyList() : l;
        } catch (Exception e) { return Collections.emptyList(); }
    }

    /** 自动挑选讯飞引擎，否则返回 null（系统默认） */
    public String autoEngine() {
        for (TextToSpeech.EngineInfo e : engines()) {
            if (e.name != null && e.name.toLowerCase().contains("iflytek")) return e.name;
        }
        return null;
    }

    /** 可用的中文音色（过滤需要联网的） */
    public List<Voice> chineseVoices() {
        List<Voice> out = new ArrayList<>();
        if (!ready || tts == null) return out;
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null) return out;
            for (Voice v : voices) {
                Locale loc = v.getLocale();
                if (loc != null && ("zh".equals(loc.getLanguage()) || "chi".equals(loc.getISO3Language()))
                        && !v.isNetworkConnectionRequired()) {
                    out.add(v);
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    public void setVoice(Voice v) {
        if (ready && tts != null && v != null) tts.setVoice(v);
    }

    public void setSpeed(float rate) {
        this.speed = rate;
        if (ready && tts != null) tts.setSpeechRate(rate);
    }

    /** 从 from 句开始，把整章句子排入播放队列；onStart 回调用于高亮 */
    public void speak(List<Sentence> sentences, int from) {
        if (!ready || tts == null || sentences == null || sentences.isEmpty()) return;
        stop();
        Bundle params = new Bundle();
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
        for (int i = from; i < sentences.size(); i++) {
            String id = "s" + i;
            int mode = (i == from) ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            tts.speak(sentences.get(i).text, mode, params, id);
        }
    }

    public void stop() {
        if (tts != null) tts.stop();
    }

    public boolean isReady() { return ready; }

    public void shutdown() {
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {}
            tts = null;
        }
        ready = false;
    }
}
