package com.mght.timestopwatch;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

/** One microphone stream supports standby, pre-roll and the complete speech recording. */
public final class SpeechCapture {
    public interface Listener {
        void onReady();
        void onVoice(long onset);
        void onError();
    }
    public final File file;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile Listener listener;
    private volatile boolean active = true;
    private volatile boolean paused;
    private volatile boolean armed;
    private volatile boolean finished;
    private boolean started;
    private volatile boolean failed;
    private volatile boolean ready;
    private volatile long voiceOnset = -1;
    private Consumer<File> completion;

    public SpeechCapture(Context context, boolean armed) throws IOException {
        File directory = new File(context.getFilesDir(), "recordings");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create recordings");
        file = new File(directory, UUID.randomUUID() + ".wav");
        this.armed = armed;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        if (listener != null) {
            if (failed) listener.onError();
            else if (voiceOnset >= 0) listener.onVoice(voiceOnset);
            else if (ready && armed) listener.onReady();
        }
    }
    public boolean isArmed() { return armed && active; }
    public boolean hasFailed() { return failed; }
    public void setPaused(boolean value) { paused = value; }
    public void start(Runnable stopped) {
        if (started) return;
        started = true;
        if (!active) { finished = true; deliver(); stopped.run(); return; }
        new Thread(() -> capture(stopped), "speech-recording").start();
    }

    /** Called on the main thread, with the result also delivered on the main thread. */
    public void finish(Consumer<File> callback) {
        listener = null;
        completion = callback;
        active = false;
        if (!started) finished = true;
        if (finished) deliver();
    }

    private void deliver() {
        Consumer<File> callback = completion;
        completion = null;
        if (callback != null) callback.accept(file.isFile() && file.length() > 44 ? file : null);
    }

    @SuppressLint("MissingPermission")
    private void capture(Runnable stopped) {
        AudioRecord input = null;
        try (WavFile wav = new WavFile(file)) {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
            int minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) throw new IOException("Microphone format unsupported");
            if (!active) return;
            input = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum, 32000));
            if (input.getState() != AudioRecord.STATE_INITIALIZED) throw new IOException("Microphone unavailable");
            input.startRecording();
            if (input.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IOException("Microphone unavailable");
            short[] samples = new short[320];
            short[] preRoll = new short[8000];
            int head = 0, stored = 0;
            VoiceStartDetector detector = new VoiceStartDetector();
            while (active) {
                int count = input.read(samples, 0, samples.length, AudioRecord.READ_BLOCKING);
                if (count < 0) throw new IOException("Microphone read failed");
                if (!active) break;
                if (armed) {
                    for (int i = 0; i < count; i++) { preRoll[head] = samples[i]; head = (head + 1) % preRoll.length; stored = Math.min(stored + 1, preRoll.length); }
                    long onset = detector.accept(samples, count, SystemClock.elapsedRealtime());
                    if (!ready && detector.isReady()) {
                        ready = true;
                        main.post(() -> { Listener callback = listener; if (active && callback != null) callback.onReady(); });
                    }
                    if (onset >= 0) {
                        short[] opening = new short[stored];
                        int first = (head - stored + preRoll.length) % preRoll.length;
                        for (int i = 0; i < stored; i++) opening[i] = preRoll[(first + i) % preRoll.length];
                        wav.write(opening, opening.length);
                        armed = false;
                        voiceOnset = onset;
                        main.post(() -> { Listener callback = listener; if (active && callback != null) callback.onVoice(onset); });
                    }
                } else if (!paused) wav.write(samples, count);
            }
        } catch (IOException | RuntimeException exception) {
            failed = true;
            main.post(() -> { Listener callback = listener; if (callback != null) callback.onError(); });
        } finally {
            active = false;
            if (input != null) {
                try { if (input.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) input.stop(); }
                catch (IllegalStateException ignored) { }
                finally { input.release(); }
            }
            main.post(() -> { finished = true; deliver(); stopped.run(); });
        }
    }
}
