package com.mght.timestopwatch;

/** Lightweight acoustic trigger, not speech transcription or speaker identification. */
public final class VoiceStartDetector {
    private static final int CALIBRATION_FRAMES = 30;
    private int calibrationFrames;
    private double noiseSum;
    private double noiseFloor;
    private int voicedFrames;
    private int quietFrames;
    private long onset;

    public boolean isReady() { return calibrationFrames >= CALIBRATION_FRAMES; }

    /** Accepts 20ms, 16kHz mono PCM frames. Returns the onset time, or -1. */
    public long accept(short[] pcm, int count, long now) {
        if (count <= 0) return -1;
        double energy = 0;
        int crossings = 0;
        for (int i = 0; i < count; i++) {
            energy += (double) pcm[i] * pcm[i];
            if (i > 0 && ((pcm[i] >= 0) != (pcm[i - 1] >= 0))) crossings++;
        }
        double rms = Math.sqrt(energy / count);
        if (!isReady()) {
            noiseSum += rms;
            calibrationFrames++;
            noiseFloor = noiseSum / calibrationFrames;
            return -1;
        }
        double crossingRate = (double) crossings / count;
        boolean voiced = rms > Math.max(300, noiseFloor * 2.5)
                && crossingRate >= 0.012 && crossingRate <= 0.40;
        if (voiced) {
            if (voicedFrames == 0) onset = now - 20;
            voicedFrames++;
            quietFrames = 0;
            if (voicedFrames >= 10) return onset;
        } else {
            quietFrames++;
            if (quietFrames > 2) voicedFrames = 0;
        }
        return -1;
    }
}
