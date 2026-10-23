package com.mght.timestopwatch;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Offline, queued post-speech recognition. Audio and text stay in private app storage. */
public final class SpeechTranscriber {
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Set<String> pending = new HashSet<>();

    public static boolean isPending(String id) { return pending.contains(id); }

    public static void submit(Context context, String id, File audio) {
        if (!pending.add(id)) return;
        Context app = context.getApplicationContext();
        if (!update(app, id, "正在生成文字稿…", null)) {
            pending.remove(id);
            audio.delete();
            return;
        }
        executor.execute(() -> {
            String result = null;
            String state;
            try {
                result = transcribe(app, audio);
                File textFile = new File(audio.getParentFile(), audio.getName().replace(".wav", ".txt"));
                try (FileOutputStream output = new FileOutputStream(textFile)) {
                    output.write(result.getBytes(StandardCharsets.UTF_8));
                }
                state = result.trim().isEmpty() ? "未识别到清晰的中文讲话，可回放录音或重试" : "文字稿已生成";
            } catch (IOException | JSONException | RuntimeException | LinkageError exception) {
                state = "转写失败，录音已保留，可重试";
            }
            String finalResult = result;
            String finalState = state;
            main.post(() -> {
                pending.remove(id);
                if (!update(app, id, finalState, finalResult)) {
                    audio.delete();
                    new File(audio.getParentFile(), audio.getName().replace(".wav", ".txt")).delete();
                }
            });
        });
    }

    public static void recordingFailed(Context context, String id) {
        update(context, id, "没有可用录音，未生成文字稿", null);
    }

    private static boolean update(Context context, String id, String state, String text) {
        SharedPreferences prefs = context.getSharedPreferences("time_officer", Context.MODE_PRIVATE);
        try {
            JSONArray records = new JSONArray(prefs.getString("records", "[]"));
            for (int i = 0; i < records.length(); i++) {
                JSONObject record = records.optJSONObject(i);
                if (record != null && id.equals(record.optString("id"))) {
                    record.put("transcriptionState", state);
                    if (text != null) record.put("transcript", text);
                    prefs.edit().putString("records", records.toString()).apply();
                    return true;
                }
            }
        } catch (JSONException ignored) { }
        return false;
    }

    /** Runs off the UI thread. Supports recordings created by WavFile. */
    public static String transcribe(Context context, File wav) throws IOException, JSONException {
        if (!wav.isFile()) throw new IOException("Recording missing");
        File modelDir = prepareModel(context);
        StringBuilder text = new StringBuilder();
        try (Model model = new Model(modelDir.getAbsolutePath());
             Recognizer recognizer = new Recognizer(model, 16000);
             RandomAccessFile input = new RandomAccessFile(wav, "r")) {
            byte[] header = new byte[44];
            input.readFully(header);
            if (header[0] != 'R' || header[8] != 'W' || header[36] != 'd') throw new IOException("Unsupported WAV");
            byte[] buffer = new byte[8000];
            int count;
            while ((count = input.read(buffer)) > 0) {
                if (recognizer.acceptWaveForm(buffer, count)) append(text, recognizer.getResult());
            }
            append(text, recognizer.getFinalResult());
        }
        return text.toString();
    }

    private static void append(StringBuilder text, String json) throws JSONException {
        String sentence = new JSONObject(json).optString("text", "").trim();
        // Remove token separators between Chinese characters, preserving English word boundaries.
        sentence = sentence.replaceAll("(?<=[\\u3400-\\u9fff]) +(?=[\\u3400-\\u9fff])", "");
        if (!sentence.isEmpty()) {
            if (text.length() > 0) text.append('\n');
            text.append(sentence);
        }
    }

    private static synchronized File prepareModel(Context context) throws IOException {
        File root = new File(context.getNoBackupFilesDir(), "offline-models");
        File model = new File(root, "vosk-model-small-cn-0.22");
        File ready = new File(model, ".ready");
        if (ready.isFile()) return model;
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot create model directory");
        String prefix = root.getCanonicalPath() + File.separator;
        try (ZipInputStream input = new ZipInputStream(context.getAssets().open("model-cn.zip"))) {
            ZipEntry entry;
            byte[] buffer = new byte[32768];
            while ((entry = input.getNextEntry()) != null) {
                File target = new File(root, entry.getName());
                if (!target.getCanonicalPath().startsWith(prefix)) throw new IOException("Invalid model archive");
                if (entry.isDirectory()) {
                    if (!target.isDirectory() && !target.mkdirs()) throw new IOException("Cannot extract model");
                } else {
                    File parent = target.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot extract model");
                    try (FileOutputStream output = new FileOutputStream(target)) {
                        int count;
                        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    }
                }
                input.closeEntry();
            }
        }
        if (!new File(model, "am/final.mdl").isFile()) throw new IOException("Model incomplete");
        if (!ready.createNewFile()) throw new IOException("Cannot mark model ready");
        return model;
    }

    public static void deleteRecording(Context context, JSONObject record) {
        File directory = new File(context.getFilesDir(), "recordings");
        String name = record.optString("audio");
        if (name.matches("[a-f0-9-]+\\.wav")) {
            new File(directory, name).delete();
            new File(directory, name.replace(".wav", ".txt")).delete();
        }
    }
}
