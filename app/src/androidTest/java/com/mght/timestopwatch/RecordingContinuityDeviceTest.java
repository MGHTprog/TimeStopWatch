package com.mght.timestopwatch;

import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RecordingContinuityDeviceTest {
    @Test public void continuousPcmKeepsUpDuringRepeatedActivityRecreation() throws Exception {
        AtomicReference<SpeechCapture> capture = new AtomicReference<>();
        CountDownLatch closed = new CountDownLatch(1);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try { capture.set(RecordingService.begin(activity, false)); }
                catch (IOException exception) { throw new AssertionError(exception); }
            });
            try {
                long deadline = SystemClock.elapsedRealtime() + 5000;
                while (capture.get().file.length() < 32044 && SystemClock.elapsedRealtime() < deadline)
                    Thread.sleep(50);
                assertTrue("Microphone started", capture.get().file.length() >= 32044);
                long firstBytes = capture.get().file.length();
                long start = SystemClock.elapsedRealtime();
                for (int i = 0; i < 10; i++) {
                    scenario.recreate();
                    Thread.sleep(1000);
                    assertFalse("Capture remains healthy", capture.get().hasFailed());
                }
                long duration = SystemClock.elapsedRealtime() - start;
                long pcmMillis = (capture.get().file.length() - firstBytes) / 32;
                assertTrue("PCM duration " + pcmMillis + "ms versus wall time " + duration + "ms",
                        pcmMillis >= duration - 300 && pcmMillis <= duration + 300);
                android.util.Log.i("RecordingContinuityTest", "PCM=" + pcmMillis + "ms wall=" + duration + "ms");
            } finally {
                scenario.onActivity(activity -> {
                    RecordingService.detach(capture.get());
                    capture.get().finish(file -> {
                        if (file != null) file.delete();
                        closed.countDown();
                    });
                });
                assertTrue("WAV closed", closed.await(5, TimeUnit.SECONDS));
            }
        }
    }
}
