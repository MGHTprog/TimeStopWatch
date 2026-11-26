package com.mght.timestopwatch;

import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class MeetingRecordStatusDeviceTest {
    @Test public void savingInYellowStageShowsYellowInMeetingRecords() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                Field preferences = field("preferences");
                SharedPreferences original = (SharedPreferences) get(preferences, activity);
                SharedPreferences isolated = activity.getSharedPreferences("meeting_record_status_device_test", 0);
                TimerSession timer = (TimerSession) get(field("timer"), activity);
                assertEquals("The device must not be timing a real speech", 0, timer.getAccumulated());
                assertFalse("The device must not be timing a real speech", timer.isRunning());
                assertNull(get(field("recording"), activity));
                assertNull(get(field("retainedAudio"), activity));
                try {
                    // Exercise the save button without modifying the user's records or starting the microphone.
                    preferences.set(activity, isolated);
                    field("records").set(activity, new JSONArray());
                    field("preset").set(activity, "黄牌回归验证");
                    field("speaker").set(activity, "测试演讲者");
                    field("cardsEnabled").setBoolean(activity, true);
                    field("green").setInt(activity, 60);
                    field("yellow").setInt(activity, 90);
                    field("red").setInt(activity, 120);
                    timer.restore(95000, 0, false);
                    Button finish = (Button) get(field("finishButton"), activity);
                    assertNotNull(finish);
                    assertTrue(finish.performClick());
                    JSONArray saved = new JSONArray(isolated.getString("records", "[]"));
                    assertEquals(1, saved.length());
                    assertEquals("已到黄牌", saved.getJSONObject(0).getString("status"));

                    TextView recordsButton = findText(activity.findViewById(android.R.id.content), "记录 1");
                    assertNotNull(recordsButton);
                    assertTrue(recordsButton.performClick());
                    View recordCards = (View) get(field("recordCards"), activity);
                    assertNotNull(recordCards);
                    TextView entry = findText(recordCards, "黄牌回归验证 / 已到黄牌");
                    assertNotNull(entry);
                    assertTrue(entry.isShown());
                } catch (ReflectiveOperationException | JSONException exception) {
                    throw new AssertionError(exception);
                } finally {
                    try { preferences.set(activity, original); }
                    catch (IllegalAccessException exception) { throw new AssertionError(exception); }
                    isolated.edit().clear().commit();
                }
            });
        }
    }

    private static TextView findText(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(text)) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Field field(String name) {
        try {
            Field field = MainActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) { throw new AssertionError(exception); }
    }

    private static Object get(Field field, MainActivity activity) {
        try { return field.get(activity); }
        catch (IllegalAccessException exception) { throw new AssertionError(exception); }
    }
}
