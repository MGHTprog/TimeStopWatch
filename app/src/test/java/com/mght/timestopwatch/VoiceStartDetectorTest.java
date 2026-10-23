package com.mght.timestopwatch;

import org.junit.Test;
import static org.junit.Assert.*;

public class VoiceStartDetectorTest {
    private final short[] silence = new short[320];

    private VoiceStartDetector calibrated() {
        VoiceStartDetector detector = new VoiceStartDetector();
        for (int i = 0; i < 30; i++) assertEquals(-1, detector.accept(silence, 320, i * 20L));
        assertTrue(detector.isReady());
        return detector;
    }

    private short[] wave(int frequency, int amplitude) {
        short[] pcm = new short[320];
        for (int i = 0; i < pcm.length; i++)
            pcm[i] = (short) (amplitude * Math.sin(2 * Math.PI * frequency * i / 16000));
        return pcm;
    }

    @Test public void silenceAndBriefNoiseDoNotStartTimer() {
        VoiceStartDetector detector = calibrated();
        short[] voiceBand = wave(500, 3000);
        for (int i = 0; i < 5; i++) assertEquals(-1, detector.accept(voiceBand, 320, 620 + i * 20));
        for (int i = 0; i < 100; i++) assertEquals(-1, detector.accept(silence, 320, 800 + i * 20));
    }

    @Test public void sustainedVoiceBandReturnsOriginalOnset() {
        VoiceStartDetector detector = calibrated();
        short[] voiceBand = wave(500, 3000);
        for (int i = 0; i < 9; i++) assertEquals(-1, detector.accept(voiceBand, 320, 620 + i * 20));
        assertEquals(600, detector.accept(voiceBand, 320, 800));
    }

    @Test public void quieterSustainedVoiceCanTrigger() {
        VoiceStartDetector detector = calibrated();
        short[] quietVoice = wave(500, 500);
        for (int i = 0; i < 9; i++) assertEquals(-1, detector.accept(quietVoice, 320, 620 + i * 20));
        assertEquals(600, detector.accept(quietVoice, 320, 800));
    }

    @Test public void lowHumAndFaintSoundsDoNotTrigger() {
        VoiceStartDetector detector = calibrated();
        for (int i = 0; i < 50; i++) assertEquals(-1, detector.accept(wave(50, 5000), 320, 620 + i * 20));
        for (int i = 0; i < 50; i++) assertEquals(-1, detector.accept(wave(500, 100), 320, 2000 + i * 20));
    }

    @Test public void calibratingNoiseRaisesTheTriggerThreshold() {
        VoiceStartDetector detector = new VoiceStartDetector();
        for (int i = 0; i < 30; i++) detector.accept(wave(500, 1000), 320, i * 20);
        for (int i = 0; i < 50; i++) assertEquals(-1, detector.accept(wave(500, 1800), 320, 620 + i * 20));
    }
}
