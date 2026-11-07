package com.mght.timestopwatch;

import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ProgressBar;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicReference;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

/** Exercises the user flow on the connected Android device without changing system settings. */
@RunWith(AndroidJUnit4.class)
public class TimerDeviceTest {
    @Test public void timerAndSignalsWorkOnDevice() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withText("选择环节 / 设置举牌时间")).perform(click());
            onView(withText("2分钟 · 即兴演讲 · 01:00 / 01:30 / 02:00")).perform(click());
            onView(withText("选择环节 / 设置举牌时间")).perform(click());
            onView(withText("自定义举牌时间")).perform(click());
            onView(allOf(isAssignableFrom(EditText.class), withText("01:00")))
                    .perform(replaceText("00:03"));
            androidx.test.espresso.Espresso.closeSoftKeyboard();
            onView(allOf(isAssignableFrom(EditText.class), withText("01:30")))
                    .perform(replaceText("00:06"));
            androidx.test.espresso.Espresso.closeSoftKeyboard();
            onView(allOf(isAssignableFrom(EditText.class), withText("02:00")))
                    .perform(replaceText("00:09"));
            androidx.test.espresso.Espresso.closeSoftKeyboard();
            onView(withText("保存")).perform(click());

            onView(withText("选择演讲者（可选）")).perform(click());
            onView(withText("新增演讲者")).perform(click());
            onView(androidx.test.espresso.matcher.ViewMatchers.withHint("演讲者姓名"))
                    .perform(replaceText("真机验证"));
            androidx.test.espresso.Espresso.closeSoftKeyboard();
            onView(withText("保存")).perform(click());

            clickInActivity(scenario, "开始计时");
            waitForText(scenario, "绿牌 · 已达最低时间", 5000);
            clickInActivity(scenario, "暂停计时");
            String paused = clockText(scenario);
            Thread.sleep(1200);
            assertEquals("Paused clock must remain unchanged", paused, clockText(scenario));
            scenario.onActivity(activity -> {
                assertNotNull(findContaining(activity.findViewById(android.R.id.content), "总时间 00:09"));
                ProgressBar progress = findProgress(activity.findViewById(android.R.id.content));
                assertNotNull(progress);
                assertTrue(progress.getProgress() > 0 && progress.getProgress() < progress.getMax());
            });
            screenshot("02-green-paused.png");
            scenario.recreate();
            assertEquals("Rotation/recreation preserves paused time", paused, clockText(scenario));

            clickInActivity(scenario, "继续计时");
            waitForText(scenario, "黄牌 · 接近规定时间", 5000);
            clickInActivity(scenario, "暂停计时");
            screenshot("03-yellow.png");
            clickInActivity(scenario, "继续计时");
            waitForText(scenario, "红牌 · 已达规定时间", 5000);
            clickInActivity(scenario, "暂停计时");
            clickInActivity(scenario, "全屏举牌");
            screenshot("04-red-fullscreen.png");
            assertText(scenario, "红牌 · 已达规定时间");
            scenario.onActivity(activity -> assertNull(findText(activity.findViewById(android.R.id.content), "按铃 · 红牌后已满30秒")));
            clickInActivity(scenario, "继续计时");
            waitForText(scenario, "按铃 · 红牌后已满30秒", 32000);
            scenario.onActivity(activity -> {
                try {
                    java.lang.reflect.Field field = MainActivity.class.getDeclaredField("bellPlayer");
                    field.setAccessible(true);
                    android.media.MediaPlayer player = (android.media.MediaPlayer) field.get(activity);
                    assertNotNull("Bell audio player was created at the deadline", player);
                    assertTrue("Bell audio is playing on the device", player.isPlaying());
                } catch (ReflectiveOperationException exception) {
                    throw new AssertionError(exception);
                }
            });
            clickInActivity(scenario, "暂停计时");
            scenario.onActivity(activity -> {
                ProgressBar progress = findProgress(activity.findViewById(android.R.id.content));
                assertNotNull(progress);
                assertEquals("Overtime keeps the progress bar full", progress.getMax(), progress.getProgress());
            });
            screenshot("06-auto-bell.png");
            scenario.recreate();
            assertText(scenario, "按铃 · 红牌后已满30秒");
            clickInActivity(scenario, "返回控制台");

            clickInActivity(scenario, "继续计时");
            String beforeRecreate = clockText(scenario);
            scenario.recreate();
            assertText(scenario, "暂停计时");
            Thread.sleep(1200);
            assertTrue(seconds(clockText(scenario)) >= seconds(beforeRecreate) + 1);
            clickInActivity(scenario, "结束并记录");
            assertEquals("00:00", clockText(scenario));
            AtomicReference<String> records = new AtomicReference<>();
            scenario.onActivity(activity -> records.set(findContaining(activity.findViewById(android.R.id.content), "自定义 / 超过红牌时间")));
            assertNotNull("Finished session is saved and visible", records.get());
            AtomicReference<String> latestTag = new AtomicReference<>();
            scenario.onActivity(activity -> {
                try {
                    org.json.JSONArray saved = new org.json.JSONArray(activity.getSharedPreferences("time_officer", 0).getString("records", "[]"));
                    latestTag.set("speech-record-" + saved.getJSONObject(saved.length() - 1).getString("id"));
                } catch (org.json.JSONException exception) { throw new AssertionError(exception); }
            });
            onView(allOf(withText(containsString("自定义 / 超过红牌时间")),
                    androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA(
                            androidx.test.espresso.matcher.ViewMatchers.withTagValue(org.hamcrest.Matchers.is(latestTag.get())))))
                    .perform(scrollTo());
            screenshot("05-recorded.png");
            scenario.recreate();
            scenario.onActivity(activity -> assertNotNull(findContaining(activity.findViewById(android.R.id.content), "自定义 / 超过红牌时间")));
            onView(withText("选择环节 / 设置举牌时间")).perform(click());
            onView(withText("2分钟 · 即兴演讲 · 01:00 / 01:30 / 02:00")).perform(click());
            clickInActivity(scenario, "开始计时");
            Thread.sleep(200);
            clickInActivity(scenario, "暂停计时");
            clickInActivity(scenario, "重置");
            onView(withText("重置")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
            assertEquals("00:00", clockText(scenario));
            assertText(scenario, "开始计时");
            scenario.onActivity(activity -> assertTrue(findText(activity.findViewById(android.R.id.content),
                    "选择环节 / 设置举牌时间").isEnabled()));
        }
    }

    private static ProgressBar findProgress(View view) {
        if (view instanceof ProgressBar) return (ProgressBar) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ProgressBar found = findProgress(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void clickInActivity(ActivityScenario<MainActivity> scenario, String text) {
        scenario.onActivity(activity -> {
            View view = findText(activity.findViewById(android.R.id.content), text);
            assertNotNull("Missing control: " + text, view);
            assertTrue("Disabled control: " + text, view.isEnabled());
            assertTrue(view.performClick());
        });
    }

    private static void assertText(ActivityScenario<MainActivity> scenario, String text) {
        scenario.onActivity(activity -> assertNotNull("Missing text: " + text,
                findText(activity.findViewById(android.R.id.content), text)));
    }

    private static void waitForText(ActivityScenario<MainActivity> scenario, String text, long timeout) throws InterruptedException {
        long deadline = SystemClock.elapsedRealtime() + timeout;
        while (SystemClock.elapsedRealtime() < deadline) {
            AtomicReference<View> found = new AtomicReference<>();
            scenario.onActivity(activity -> found.set(findText(activity.findViewById(android.R.id.content), text)));
            if (found.get() != null) return;
            Thread.sleep(100);
        }
        fail("Timed out waiting for " + text);
    }

    private static View findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String findContaining(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(text))
            return ((TextView) view).getText().toString();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                String found = findContaining(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String clockText(ActivityScenario<MainActivity> scenario) {
        AtomicReference<String> result = new AtomicReference<>();
        scenario.onActivity(activity -> result.set(findClock(activity.findViewById(android.R.id.content))));
        assertNotNull(result.get());
        return result.get();
    }

    private static String findClock(View view) {
        if (view instanceof TextView && ((TextView) view).getText().toString().matches("\\d{2,}:\\d{2}"))
            return ((TextView) view).getText().toString();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                String found = findClock(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int seconds(String clock) {
        String[] parts = clock.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private static void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Thread.sleep(200);
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File directory = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null), "validation");
        assertTrue(directory.exists() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally { bitmap.recycle(); }
    }
}
