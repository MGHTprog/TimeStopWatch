package com.mght.timestopwatch;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicReference;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SpeakerEditingDeviceTest {
    @Test public void editWhileRunningAndFullscreenSavesLatestName() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withText("开始计时")).perform(scrollTo(), click());
            onView(withText("＋ 添加演讲者（可选）")).perform(scrollTo(), click());
            onView(withHint("演讲者姓名")).perform(replaceText("计时中改名验证"));
            closeSoftKeyboard();
            onView(withText("保存")).perform(click());
            AtomicReference<String> before = new AtomicReference<>();
            scenario.onActivity(activity -> before.set(clock(activity.findViewById(android.R.id.content))));
            Thread.sleep(1200);
            scenario.onActivity(activity -> {
                View root = activity.findViewById(android.R.id.content);
                assertTrue(seconds(clock(root)) > seconds(before.get()));
            });
            onView(withText("全屏举牌")).perform(scrollTo(), click());
            onView(withText("演讲者：计时中改名验证")).perform(scrollTo(), click());
            onView(withHint("演讲者姓名")).perform(replaceText("全屏改名验证"));
            closeSoftKeyboard();
            onView(withText("保存")).perform(click());
            onView(withText("结束并记录")).perform(scrollTo(), click());
            onView(withText(containsString("全屏改名验证"))).perform(scrollTo());
        }
    }

    private static String clock(View view) {
        if (view instanceof TextView && ((TextView) view).getText().toString().matches("\\d{2,}:\\d{2}"))
            return ((TextView) view).getText().toString();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                String text = clock(group.getChildAt(i));
                if (text != null) return text;
            }
        }
        return null;
    }

    private static int seconds(String text) {
        assertNotNull(text);
        String[] parts = text.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }
}
