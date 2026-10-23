package com.mght.timestopwatch;

import android.Manifest;
import android.content.pm.PackageManager;
import android.view.View;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class VoiceStandbyDeviceTest {
    @Test public void standbyCanBeCancelledAndManualStartStillWorks() throws Exception {
        assertEquals(PackageManager.PERMISSION_GRANTED, InstrumentationRegistry.getInstrumentation()
                .getTargetContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            click(scenario, "语音待命");
            long deadline = SystemClock.elapsedRealtime() + 5000;
            AtomicReference<View> ready = new AtomicReference<>();
            while (SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity(activity -> ready.set(find(activity.findViewById(android.R.id.content),
                        "语音待命中，请开始讲话（嘈杂环境可能误触发）")));
                if (ready.get() != null) break;
                Thread.sleep(50);
            }
            assertNotNull("Real microphone reaches the listening state", ready.get());
            assertShown(scenario, "取消语音待命");
            click(scenario, "取消语音待命");
            Thread.sleep(400);
            assertShown(scenario, "开始计时");
            assertShown(scenario, "00:00");
            click(scenario, "语音待命");
            scenario.recreate();
            assertShown(scenario, "语音待命");
            assertShown(scenario, "00:00");
            click(scenario, "开始计时");
            assertShown(scenario, "暂停计时");
            Thread.sleep(1200);
            assertShown(scenario, "00:01");
            click(scenario, "暂停计时");
        }
    }

    private static void click(ActivityScenario<MainActivity> scenario, String text) {
        scenario.onActivity(activity -> {
            View view = find(activity.findViewById(android.R.id.content), text);
            assertNotNull(text, view);
            assertTrue(view.isEnabled());
            assertTrue(view.performClick());
        });
    }

    private static void assertShown(ActivityScenario<MainActivity> scenario, String text) {
        scenario.onActivity(activity -> assertNotNull(text, find(activity.findViewById(android.R.id.content), text)));
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
