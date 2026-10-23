package com.mght.timestopwatch;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import androidx.core.content.ContextCompat;
import java.io.IOException;

/** Keeps speech capture alive across rotation and temporary backgrounding. */
public final class RecordingService extends Service {
    private static SpeechCapture current;
    private SpeechCapture started;

    public static SpeechCapture current() { return current; }

    public static SpeechCapture begin(Context context, boolean armed) throws IOException {
        if (current != null) throw new IOException("Recording already active");
        SpeechCapture capture = new SpeechCapture(context.getApplicationContext(), armed);
        current = capture;
        try { ContextCompat.startForegroundService(context, new Intent(context, RecordingService.class)); }
        catch (RuntimeException exception) { current = null; throw new IOException("Cannot start microphone service", exception); }
        return capture;
    }

    public static void detach(SpeechCapture capture) { if (current == capture) current = null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(
                "speech_recording", "演讲录音", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "speech_recording") : new Notification.Builder(this);
        Notification notification = builder.setSmallIcon(R.drawable.ic_bell).setContentTitle("头马时间官 · 麦克风使用中")
                .setContentText("正在语音待命或录音，点此返回计时页面").setContentIntent(open).setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 30) startForeground(41, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        else startForeground(41, notification);
        SpeechCapture capture = current;
        if (capture == null) { stopSelf(); return START_NOT_STICKY; }
        if (started != capture) {
            started = capture;
            capture.start(() -> {
                if (current == capture || current == null) {
                    detach(capture);
                    stopSelf();
                }
            });
        }
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        if (current == started && started != null) { started.finish(null); current = null; }
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
