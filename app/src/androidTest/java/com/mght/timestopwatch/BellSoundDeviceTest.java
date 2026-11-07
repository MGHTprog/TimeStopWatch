package com.mght.timestopwatch;

import android.media.MediaPlayer;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BellSoundDeviceTest {
    @Test public void singleBellStrikePlaysOnceWithVibrationOff() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    Field vibration = field("vibration");
                    vibration.setBoolean(activity, false);
                    TimerSession timer = (TimerSession) field("timer").get(activity);
                    int red = field("red").getInt(activity);
                    timer.restore((red + 30L) * 1000, SystemClock.elapsedRealtime(), true);
                    Method update = MainActivity.class.getDeclaredMethod("updateDisplay");
                    update.setAccessible(true);
                    update.invoke(activity);
                    MediaPlayer player = (MediaPlayer) field("bellPlayer").get(activity);
                    assertNotNull(player);
                    assertTrue("Single bell strike is playing", player.isPlaying());
                    assertEquals(2000, player.getDuration(), 100);
                    update.invoke(activity);
                    assertSame("Repeated display updates keep the same playback", player, field("bellPlayer").get(activity));
                } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
            });
            Thread.sleep(2600);
            scenario.onActivity(activity -> {
                try {
                    assertNull("Playback is released and does not repeat", field("bellPlayer").get(activity));
                    ((TimerSession) field("timer").get(activity)).reset();
                } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
            });
        }
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
