package com.mght.timestopwatch;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import android.content.ClipboardManager;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RecordingTranscriptionDeviceTest {
    @Test public void chineseFixtureProducesActualOfflineText() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File fixture = new File(context.getCacheDir(), "mandarin-fixture.wav");
        try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("mandarin.wav");
             FileOutputStream output = new FileOutputStream(fixture)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) > 0) output.write(buffer, 0, count);
        }
        String text = SpeechTranscriber.transcribe(context, fixture);
        File directory = new File(context.getExternalFilesDir(null), "validation");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, "offline-chinese-result.txt"))) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
        assertTrue("Expected Chinese speech content, got: " + text, text.contains("大家") && text.contains("今天"));
        fixture.delete();
    }

    @Test public void recordingPausesSurvivesRotationAndTranscribesAfterFinish() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences("time_officer", Context.MODE_PRIVATE).edit().putBoolean("recordEnabled", true).commit();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickInActivity(scenario, "开始计时");
            AtomicReference<SpeechCapture> session = new AtomicReference<>();
            long deadline = SystemClock.elapsedRealtime() + 5000;
            while (SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity(activity -> session.set(RecordingService.current()));
                if (session.get() != null && session.get().file.length() > 32044) break;
                Thread.sleep(100);
            }
            assertNotNull(session.get());
            File audio = session.get().file;
            assertTrue("Actual microphone PCM was saved", audio.length() > 32044);
            clickInActivity(scenario, "暂停计时");
            Thread.sleep(200);
            long pausedSize = audio.length();
            Thread.sleep(500);
            assertEquals("Paused audio must not grow", pausedSize, audio.length());
            scenario.recreate();
            scenario.onActivity(activity -> assertSame(session.get(), RecordingService.current()));
            clickInActivity(scenario, "继续计时");
            Thread.sleep(1200);
            assertTrue(audio.length() > pausedSize);
            clickInActivity(scenario, "结束并记录");
            assertText(scenario, "00:00");
            deadline = SystemClock.elapsedRealtime() + 120000;
            JSONObject saved = null;
            while (SystemClock.elapsedRealtime() < deadline) {
                JSONArray records = new JSONArray(context.getSharedPreferences("time_officer", Context.MODE_PRIVATE).getString("records", "[]"));
                for (int i = 0; i < records.length(); i++) {
                    JSONObject record = records.getJSONObject(i);
                    if (audio.getName().equals(record.optString("audio"))) saved = record;
                }
                if (saved != null && !saved.optString("transcriptionState").startsWith("正在")) break;
                Thread.sleep(200);
            }
            assertNotNull(saved);
            String state = saved.optString("transcriptionState");
            assertTrue("Post-speech recognition completed: " + state,
                    state.equals("文字稿已生成") || state.startsWith("未识别到清晰"));
            assertTrue(audio.isFile() && audio.length() > 44);
        }
    }

    @Test public void postSpeechTranscriptCanBeOpenedAndCopied() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String id = UUID.randomUUID().toString();
        File directory = new File(context.getFilesDir(), "recordings");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        File audio = new File(directory, id + ".wav");
        try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("mandarin.wav");
             FileOutputStream output = new FileOutputStream(audio)) {
            byte[] bytes = new byte[8192]; int count;
            while ((count = input.read(bytes)) > 0) output.write(bytes, 0, count);
        }
        android.content.SharedPreferences preferences = context.getSharedPreferences("time_officer", Context.MODE_PRIVATE);
        JSONArray records = new JSONArray(preferences.getString("records", "[]"));
        records.put(new JSONObject().put("id", id).put("speaker", "转写功能验证")
                .put("preset", "中文合成语音测试").put("duration", "00:09").put("status", "测试录音")
                .put("time", "10-15").put("audio", audio.getName()));
        preferences.edit().putString("records", records.toString()).commit();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> SpeechTranscriber.submit(context, id, audio));
        String transcript = "";
        long deadline = SystemClock.elapsedRealtime() + 90000;
        while (SystemClock.elapsedRealtime() < deadline) {
            JSONArray latest = new JSONArray(preferences.getString("records", "[]"));
            for (int i = 0; i < latest.length(); i++) {
                JSONObject record = latest.getJSONObject(i);
                if (id.equals(record.optString("id"))) transcript = record.optString("transcript");
            }
            if (transcript.contains("今天")) break;
            Thread.sleep(200);
        }
        assertTrue(transcript, transcript.contains("大家") && transcript.contains("今天"));
        assertTrue(new File(directory, id + ".txt").isFile());
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(allOf(withText("查看文字稿"), isDescendantOfA(withTagValue(is("speech-record-" + id)))))
                    .perform(scrollTo(), click());
            onView(withText(containsString("大家好"))).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
            android.graphics.Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            File screenshots = new File(context.getExternalFilesDir(null), "validation");
            assertTrue(screenshots.isDirectory() || screenshots.mkdirs());
            try (FileOutputStream output = new FileOutputStream(new File(screenshots, "08-transcript.png"))) {
                assertNotNull(screenshot);
                assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output));
            } finally { if (screenshot != null) screenshot.recycle(); }
            onView(withText("复制全文")).perform(click());
            scenario.onActivity(activity -> {
                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                assertNotNull(clipboard.getPrimaryClip());
                assertTrue(clipboard.getPrimaryClip().getItemAt(0).coerceToText(activity).toString().contains("今天"));
            });
            onView(allOf(withText("回放录音"), isDescendantOfA(withTagValue(is("speech-record-" + id)))))
                    .perform(scrollTo(), click());
            onView(withText("录音回放")).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
            onView(withText("停止")).perform(click());
        }
    }

    private static void clickInActivity(ActivityScenario<MainActivity> scenario, String text) {
        scenario.onActivity(activity -> {
            View view = find(activity.findViewById(android.R.id.content), text);
            assertNotNull(text, view);
            assertTrue(view.isEnabled());
            assertTrue(view.performClick());
        });
    }
    private static void assertText(ActivityScenario<MainActivity> scenario, String text) {
        scenario.onActivity(activity -> assertNotNull(find(activity.findViewById(android.R.id.content), text)));
    }
    private static View find(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
