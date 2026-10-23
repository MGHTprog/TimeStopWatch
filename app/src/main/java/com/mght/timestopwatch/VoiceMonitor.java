package com.mght.timestopwatch;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;

/** Owns the microphone only while armed. No audio is stored or sent anywhere. */
public final class VoiceMonitor {
    public interface Listener {
        void onReady();
        void onVoice(long onset);
        void onError();
    }

    private volatile boolean active = true;
    private final Listener listener;

    public VoiceMonitor(Listener listener) { this.listener = listener; }
    public void start() { new Thread(this::listen, "voice-standby").start(); }
    public void stop() { active = false; }

    // Caller checks RECORD_AUDIO permission immediately before starting the monitor.
    @SuppressLint("MissingPermission")
    private void listen() {
        AudioRecord recorder = null;
        try {
            if (!active) return;
            int minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) throw new IllegalStateException("Unsupported microphone format");
            recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minimum, 320 * 2 * 8));
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED)
                throw new IllegalStateException("Microphone unavailable");
            recorder.startRecording();
            if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
                throw new IllegalStateException("Microphone could not start");
            VoiceStartDetector detector = new VoiceStartDetector();
            short[] samples = new short[320];
            boolean ready = false;
            while (active) {
                int count = recorder.read(samples, 0, samples.length, AudioRecord.READ_BLOCKING);
                if (count < 0) throw new IllegalStateException("Microphone read failed");
                long onset = detector.accept(samples, count, SystemClock.elapsedRealtime());
                if (!active) break;
                if (!ready && detector.isReady()) { ready = true; listener.onReady(); }
                if (onset >= 0) { active = false; listener.onVoice(onset); }
            }
        } catch (RuntimeException exception) {
            if (active) { active = false; listener.onError(); }
        } finally {
            if (recorder != null) {
                try {
                    if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) recorder.stop();
                } finally { recorder.release(); }
            }
        }
    }
}
