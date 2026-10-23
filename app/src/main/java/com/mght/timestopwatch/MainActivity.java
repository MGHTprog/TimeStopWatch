package com.mght.timestopwatch;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.media.MediaPlayer;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.widget.TextViewCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int BACKGROUND = Color.rgb(16, 24, 39);
    private static final int PANEL = Color.rgb(29, 41, 59);
    private static final int MUTED = Color.rgb(159, 176, 199);
    private static final int[] CARD_COLORS = {PANEL, 0xFF138650, 0xFFF4C542, 0xFFCE3545};
    private static final String[] STAGES = {"等待举牌", "绿牌 · 已达最低时间", "黄牌 · 接近规定时间", "红牌 · 已达规定时间"};
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TimerSession timer = new TimerSession();
    private SharedPreferences preferences;
    private FrameLayout root;
    private LinearLayout card;
    private ImageView bellIndicator;
    private boolean bellNotified;
    private TextView clock, stageLabel, nextLabel, recordsView, presetLabel, totalLabel;
    private ProgressBar timeProgress;
    private Button startButton, settingsButton, speakerButton, voiceButton;
    private TextView voiceStatus;
    private VoiceMonitor voiceMonitor;
    private SpeechCapture recording;
    private File retainedAudio;
    private boolean recordEnabled = true;
    private boolean recordingUnavailable;
    private int permissionAction;
    private SwitchCompat recordingSwitch;
    private LinearLayout recordCards;
    private MediaPlayer playback;
    private AlertDialog playbackDialog;
    private final SharedPreferences.OnSharedPreferenceChangeListener recordChanges = (prefs, key) -> {
        if ("records".equals(key)) {
            try { records = new JSONArray(prefs.getString("records", "[]")); }
            catch (JSONException ignored) { records = new JSONArray(); }
            showRecords();
        }
    };
    private boolean voiceArmed;
    private int voiceGeneration;
    private String voiceMessage = "点击语音待命，听到讲话声后自动开始计时";
    private final ActivityResultLauncher<String> microphonePermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    if (permissionAction == 2) startOrPauseTimer();
                    else startVoiceStandby();
                }
                else {
                    voiceMessage = "未获得麦克风权限，可手动开始；权限可在系统设置中开启";
                    if (permissionAction == 2) {
                        recordingUnavailable = true;
                        timer.start(SystemClock.elapsedRealtime());
                        voiceMessage = "未获得麦克风权限，本轮仅计时，未录音";
                    }
                    displayedState = "";
                    updateDisplay();
                }
            });
    private int green = 60, yellow = 90, red = 120;
    private int lastStage;
    private boolean fullScreen;
    private boolean vibration = true;
    private String preset = "即兴演讲";
    private String speaker = "";
    private JSONArray records = new JSONArray();
    private String displayedState = "";

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateDisplay();
            handler.postDelayed(this, 100);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        preferences = getSharedPreferences("time_officer", MODE_PRIVATE);
        green = preferences.getInt("green", 60);
        yellow = preferences.getInt("yellow", 90);
        red = preferences.getInt("red", 120);
        preset = preferences.getString("preset", "即兴演讲");
        vibration = preferences.getBoolean("vibration", true);
        recordEnabled = preferences.getBoolean("recordEnabled", true);
        preferences.registerOnSharedPreferenceChangeListener(recordChanges);
        try { records = new JSONArray(preferences.getString("records", "[]")); }
        catch (JSONException ignored) { records = new JSONArray(); }
        if (state != null) {
            timer.restore(state.getLong("elapsed"), state.getLong("started"), state.getBoolean("running"));
            fullScreen = state.getBoolean("fullScreen");
            lastStage = state.getInt("lastStage");
            bellNotified = state.getBoolean("bellNotified");
            speaker = state.getString("speaker", "");
            recordingUnavailable = state.getBoolean("recordingUnavailable");
            permissionAction = state.getInt("permissionAction");
            String audio = state.getString("audio", "");
            if (audio.matches("[a-f0-9-]+\\.wav")) retainedAudio = new File(getFilesDir(), "recordings/" + audio);
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (fullScreen) { fullScreen = false; buildScreen(); }
                else if (timer.isRunning()) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("退出计时？").setMessage("退出将暂停当前计时。返回应用后可继续。")
                            .setNegativeButton("继续计时", null)
                            .setPositiveButton("暂停并退出", (dialog, which) -> {
                                timer.pause(SystemClock.elapsedRealtime());
                                if (recording != null) recording.setPaused(true);
                                updateDisplay();
                                moveTaskToBack(true);
                            }).show();
                } else {
                    moveTaskToBack(true);
                }
            }
        });
        buildScreen();
    }

    private void buildScreen() {
        displayedState = "";
        root.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll);
        LinearLayout content = column();
        content.setPadding(dp(fullScreen ? 12 : 22), dp(20), dp(fullScreen ? 12 : 22), dp(24));
        scroll.addView(content);
        if (!fullScreen) {
            content.addView(label("TOASTMASTERS  /  TIMEKEEPER", 11, MUTED));
            TextView title = label("头马时间官", 30, Color.WHITE);
            title.setTypeface(null, Typeface.BOLD);
            add(content, title, 8);
            add(content, label("专注每一秒，让每一次举牌恰到好处", 13, MUTED), 6);
            presetLabel = label("", 16, Color.WHITE);
            add(content, presetLabel, 24);
            settingsButton = button("选择环节 / 设置举牌时间", PANEL, Color.WHITE);
            settingsButton.setOnClickListener(v -> choosePreset());
            add(content, settingsButton, 10);
            speakerButton = button(speaker.isEmpty() ? "＋ 添加演讲者（可选）" : "演讲者：" + speaker, PANEL, MUTED);
            speakerButton.setOnClickListener(v -> editSpeaker());
            add(content, speakerButton, 8);
        }
        card = column();
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(12), dp(fullScreen ? 54 : 32), dp(12), dp(fullScreen ? 54 : 32));
        stageLabel = label("", 18, Color.WHITE);
        stageLabel.setGravity(Gravity.CENTER);
        bellIndicator = new ImageView(this);
        bellIndicator.setImageResource(R.drawable.ic_bell);
        bellIndicator.setContentDescription("红牌后30秒，按铃提醒");
        bellIndicator.setVisibility(View.GONE);
        LinearLayout.LayoutParams bellSize = new LinearLayout.LayoutParams(dp(64), dp(64));
        bellSize.bottomMargin = dp(12);
        card.addView(bellIndicator, bellSize);
        card.addView(stageLabel);
        clock = label("00:00", fullScreen ? 76 : 64, Color.WHITE);
        clock.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        clock.setGravity(Gravity.CENTER);
        clock.setSingleLine(true);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(clock, 32, fullScreen ? 76 : 64, 2, android.util.TypedValue.COMPLEX_UNIT_SP);
        add(card, clock, 16);
        totalLabel = label("", 14, Color.WHITE);
        totalLabel.setGravity(Gravity.CENTER);
        add(card, totalLabel, 12);
        timeProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        timeProgress.setIndeterminate(false);
        timeProgress.setMax(red * 1000);
        timeProgress.setProgressBackgroundTintList(ColorStateList.valueOf(0x30000000));
        LinearLayout.LayoutParams progressSize = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        progressSize.setMargins(dp(12), dp(16), dp(12), 0);
        card.addView(timeProgress, progressSize);
        nextLabel = label("", 14, Color.WHITE);
        nextLabel.setGravity(Gravity.CENTER);
        add(card, nextLabel, 12);
        add(content, card, fullScreen ? 0 : 20);
        startButton = button("开始计时", 0xFF78DCC5, BACKGROUND);
        startButton.setOnClickListener(v -> startOrPauseTimer());
        add(content, startButton, 18);
        voiceButton = button("语音待命", PANEL, Color.WHITE);
        voiceButton.setOnClickListener(v -> {
            if (voiceArmed) {
                cancelVoiceStandby();
                updateDisplay();
            } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) startVoiceStandby();
            else { permissionAction = 1; microphonePermission.launch(Manifest.permission.RECORD_AUDIO); }
        });
        add(content, voiceButton, 8);
        voiceStatus = label("", 12, MUTED);
        voiceStatus.setGravity(Gravity.CENTER);
        add(content, voiceStatus, 6);
        LinearLayout actions = row();
        Button reset = button("重置", PANEL, Color.WHITE);
        reset.setOnClickListener(v -> {
            if (timer.elapsed(SystemClock.elapsedRealtime()) == 0) return;
            new AlertDialog.Builder(this).setTitle("重置当前计时？")
                    .setMessage("当前计时和本轮录音将清空。如需保留，请先结束并记录。")
                    .setNegativeButton("取消", null).setPositiveButton("重置", (dialog, which) -> resetTimer()).show();
        });
        Button finish = button("结束并记录", PANEL, Color.WHITE);
        finish.setOnClickListener(v -> finishSession());
        actions.addView(reset, weighted());
        actions.addView(finish, weighted());
        add(content, actions, 8);
        Button fullscreenButton = button(fullScreen ? "返回控制台" : "全屏举牌", PANEL, Color.WHITE);
        fullscreenButton.setOnClickListener(v -> { fullScreen = !fullScreen; buildScreen(); });
        add(content, fullscreenButton, 8);
        if (fullScreen) {
            speakerButton = button(speaker.isEmpty() ? "＋ 添加演讲者（可选）" : "演讲者：" + speaker, PANEL, MUTED);
            speakerButton.setOnClickListener(v -> editSpeaker());
            add(content, speakerButton, 8);
        }
        if (!fullScreen) {
            recordingSwitch = new SwitchCompat(this);
            recordingSwitch.setText("录音并在结束后生成中文文字稿（仅本机）");
            recordingSwitch.setTextColor(MUTED);
            recordingSwitch.setChecked(recordEnabled);
            recordingSwitch.setOnCheckedChangeListener((view, checked) -> {
                recordEnabled = checked;
                preferences.edit().putBoolean("recordEnabled", checked).apply();
                displayedState = "";
                updateDisplay();
            });
            add(content, recordingSwitch, 18);
            SwitchCompat vibrationSwitch = new SwitchCompat(this);
            vibrationSwitch.setText("震动提醒（举牌一次，按铃两次）");
            vibrationSwitch.setTextColor(MUTED);
            vibrationSwitch.setChecked(vibration);
            vibrationSwitch.setOnCheckedChangeListener((view, checked) -> {
                vibration = checked;
                preferences.edit().putBoolean("vibration", checked).apply();
            });
            add(content, vibrationSwitch, 18);
            add(content, label("绿黄红牌全自动切换；红牌30秒后显示铃铛并震动两次。", 12, MUTED), 6);
            add(content, label("红牌后继续计时，点击“结束并记录”保存成绩。", 12, MUTED), 6);
            add(content, label("本次会议记录", 20, Color.WHITE), 26);
            recordsView = label("", 14, MUTED);
            add(content, recordsView, 12);
            recordCards = column();
            add(content, recordCards, 8);
            Button clear = button("清空记录", PANEL, MUTED);
            clear.setOnClickListener(v -> {
                if (records.length() == 0) return;
                new AlertDialog.Builder(this).setTitle("清空所有计时记录？").setMessage("对应的录音和文字稿也会从本机删除。")
                        .setNegativeButton("取消", null).setPositiveButton("清空", (dialog, which) -> {
                            stopPlayback();
                            for (int i = 0; i < records.length(); i++) {
                                JSONObject old = records.optJSONObject(i);
                                if (old != null) SpeechTranscriber.deleteRecording(this, old);
                            }
                            records = new JSONArray();
                            saveRecords();
                            showRecords();
                        }).show();
            });
            add(content, clear, 10);
            showRecords();
        } else {
            presetLabel = null;
            settingsButton = null;
            recordsView = null;
            recordCards = null;
            recordingSwitch = null;
        }
        updateDisplay();
    }

    private void updateDisplay() {
        long elapsed = timer.elapsed(SystemClock.elapsedRealtime());
        int automatic = TimerSession.stage(elapsed, green, yellow, red);
        boolean bellDue = TimerSession.isBellDue(elapsed, red);
        if (bellDue && !bellNotified && timer.isRunning()) {
            bellNotified = true;
            if (vibration) vibrate(true);
        } else if (!bellDue && automatic > lastStage && timer.isRunning() && vibration) {
            vibrate(false);
        }
        lastStage = automatic;
        timeProgress.setMax(red * 1000);
        timeProgress.setProgress((int) Math.min(elapsed, red * 1000L));
        timeProgress.setContentDescription("计时进度 " + Math.min(100, elapsed * 100 / (red * 1000L)) + "%");
        int shown = automatic;
        String displayState = (elapsed / 1000) + ":" + (elapsed > 0) + ":" + shown + ":" + timer.isRunning()
                + ":" + recordEnabled + ":" + recordingUnavailable + ":" + voiceArmed + ":" + voiceMessage + ":" + bellDue + ":" + fullScreen + ":" + preset + ":" + green + ":" + yellow + ":" + red;
        if (displayState.equals(displayedState)) return;
        displayedState = displayState;
        int foreground = shown == 2 ? BACKGROUND : Color.WHITE;
        card.setBackground(background(CARD_COLORS[shown]));
        root.setBackgroundColor(fullScreen ? CARD_COLORS[shown] : BACKGROUND);
        WindowInsetsControllerCompat systemBars = WindowCompat.getInsetsController(getWindow(), root);
        systemBars.setAppearanceLightStatusBars(fullScreen && shown == 2);
        systemBars.setAppearanceLightNavigationBars(fullScreen && shown == 2);
        clock.setText(format(elapsed / 1000));
        clock.setTextColor(foreground);
        bellIndicator.setVisibility(bellDue ? View.VISIBLE : View.GONE);
        bellIndicator.setColorFilter(foreground);
        stageLabel.setText(bellDue ? "按铃 · 红牌后已满30秒" : STAGES[shown]);
        stageLabel.setTextColor(foreground);
        totalLabel.setTextColor(foreground);
        totalLabel.setText("总时间 " + format(red) + " · "
                + (elapsed < red * 1000L ? "剩余 " + format(red - elapsed / 1000)
                : "超时 +" + format(elapsed / 1000 - red)));
        timeProgress.setProgressTintList(ColorStateList.valueOf(foreground));
        nextLabel.setTextColor(foreground);
        if (automatic < 3) {
            int threshold = new int[]{green, yellow, red}[automatic];
            nextLabel.setText("距离" + new String[]{"绿牌", "黄牌", "红牌"}[automatic] + " " + format(Math.max(0, threshold - elapsed / 1000)));
        } else if (!bellDue) {
            nextLabel.setText("红牌后 +" + format(elapsed / 1000 - red)
                    + " · 距离按铃 " + format(red + 30L - elapsed / 1000));
        } else nextLabel.setText("红牌后 +" + format(elapsed / 1000 - red));
        startButton.setText(timer.isRunning() ? "暂停计时" : elapsed > 0 ? "继续计时" : "开始计时");
        voiceButton.setText(voiceArmed ? "取消语音待命" : "语音待命");
        voiceButton.setEnabled(voiceArmed || (!timer.isRunning() && elapsed == 0));
        String audioStatus = "";
        if (recording != null && !recording.hasFailed() && !voiceArmed)
            audioStatus = timer.isRunning() ? "\n正在录音，结束后在本机生成中文文字稿" : "\n录音已暂停，继续计时后恢复";
        else if (recordEnabled && !recordingUnavailable && recording == null)
            audioStatus = "\n本轮将录音，结束后自动生成中文文字稿";
        voiceStatus.setText(voiceMessage + audioStatus);
        boolean locked = timer.isRunning() || elapsed > 0 || voiceArmed;
        if (settingsButton != null) settingsButton.setEnabled(!locked);
        if (recordingSwitch != null) recordingSwitch.setEnabled(!locked);
        if (presetLabel != null) presetLabel.setText(preset + "   ·   " + format(green) + " / " + format(yellow) + " / " + format(red));
        if (timer.isRunning() || fullScreen || voiceArmed) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void startOrPauseTimer() {
        stopPlayback();
        cancelVoiceStandby();
        if (timer.isRunning()) {
            timer.pause(SystemClock.elapsedRealtime());
            if (recording != null) recording.setPaused(true);
        } else {
            if (recordEnabled && recording == null && !recordingUnavailable) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    permissionAction = 2;
                    microphonePermission.launch(Manifest.permission.RECORD_AUDIO);
                    return;
                }
                try {
                    recording = RecordingService.begin(this, false);
                    retainedAudio = recording.file;
                    attachRecording();
                } catch (IOException exception) {
                    recordingUnavailable = true;
                    voiceMessage = "无法录音，本轮仅计时，请检查麦克风权限和占用情况";
                }
            }
            if (recording != null) recording.setPaused(false);
            timer.start(SystemClock.elapsedRealtime());
        }
        updateDisplay();
    }

    private void startVoiceStandby() {
        if (voiceArmed || timer.isRunning() || timer.elapsed(SystemClock.elapsedRealtime()) > 0) return;
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
            voiceMessage = "此设备没有麦克风，请手动开始";
            updateDisplay();
            return;
        }
        stopPlayback();
        voiceArmed = true;
        voiceMessage = "正在适应环境声音，请稍候…";
        final int generation = ++voiceGeneration;
        if (recordEnabled) {
            try {
                recording = RecordingService.begin(this, true);
                retainedAudio = recording.file;
                attachRecording();
            } catch (IOException exception) {
                voiceArmed = false;
                voiceMessage = "无法使用麦克风，请关闭占用麦克风的应用后重试";
            }
        } else {
            voiceMonitor = new VoiceMonitor(new VoiceMonitor.Listener() {
                @Override public void onReady() { handler.post(() -> voiceReady(generation)); }
                @Override public void onVoice(long onset) { handler.post(() -> voiceStarted(generation, onset)); }
                @Override public void onError() {
                    handler.post(() -> {
                        if (!voiceArmed || generation != voiceGeneration) return;
                        cancelVoiceStandby();
                        voiceMessage = "无法使用麦克风，请关闭占用麦克风的应用后重试";
                        updateDisplay();
                    });
                }
            });
            voiceMonitor.start();
        }
        updateDisplay();
    }

    private void attachRecording() {
        SpeechCapture capture = recording;
        if (capture == null) return;
        int generation = voiceGeneration;
        capture.setListener(new SpeechCapture.Listener() {
            @Override public void onReady() { if (recording == capture) voiceReady(generation); }
            @Override public void onVoice(long onset) { if (recording == capture) voiceStarted(generation, onset); }
            @Override public void onError() {
                if (recording != capture) return;
                voiceArmed = false;
                recordingUnavailable = true;
                voiceMessage = "麦克风录音中断，计时仍可继续；已采集的音频会保留";
                updateDisplay();
            }
        });
    }

    private void voiceReady(int generation) {
        if (!voiceArmed || generation != voiceGeneration) return;
        voiceMessage = "语音待命中，请开始讲话（嘈杂环境可能误触发）";
        updateDisplay();
    }

    private void voiceStarted(int generation, long onset) {
        if (!voiceArmed || generation != voiceGeneration) return;
        // Capture keeps the same microphone and pre-roll, preserving the opening words.
        voiceArmed = false;
        cancelVoiceStandby();
        voiceMessage = "已检测到讲话声，自动计时中";
        timer.start(onset);
        if (vibration) vibrate(new long[]{0, 100, 150, 350});
        updateDisplay();
    }

    private void cancelVoiceStandby() {
        boolean wasArmed = voiceArmed;
        voiceGeneration++;
        if (voiceMonitor != null) { voiceMonitor.stop(); voiceMonitor = null; }
        voiceArmed = false;
        if (wasArmed) discardRecording();
        voiceMessage = "点击语音待命，听到讲话声后自动开始计时";
    }

    private void discardRecording() {
        SpeechCapture capture = recording;
        recording = null;
        File previous = retainedAudio;
        retainedAudio = null;
        if (capture != null) {
            RecordingService.detach(capture);
            capture.finish(file -> { if (file != null) file.delete(); else capture.file.delete(); });
        } else if (previous != null) previous.delete();
    }

    /** A bell is two pulses separated by silence, with no repeating vibration. */
    private boolean vibrate(boolean bell) {
        return vibrate(bell ? new long[]{0, 300, 200, 300} : new long[]{0, 350});
    }

    private boolean vibrate(long[] pattern) {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return false;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } else {
            vibrator.vibrate(pattern, -1);
        }
        return true;
    }

    private void choosePreset() {
        String[] names = {"即兴演讲", "演讲评估", "备稿演讲 · 5分钟", "备稿演讲 · 7分钟", "备稿演讲 · 10分钟"};
        int[][] values = {{60, 90, 120}, {120, 150, 180}, {180, 240, 300}, {300, 360, 420}, {480, 540, 600}};
        // Sort by numeric total duration (red card), rather than comparing text such as "10" and "2".
        Integer[] order = new Integer[values.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, java.util.Comparator.comparingInt(index -> values[index][2]));
        String[] options = new String[values.length + 1];
        for (int i = 0; i < order.length; i++) {
            int index = order[i];
            String title = names[index].split(" · ")[0];
            options[i] = (values[index][2] / 60) + "分钟 · " + title + " · "
                    + format(values[index][0]) + " / " + format(values[index][1]) + " / " + format(values[index][2]);
        }
        options[values.length] = "自定义举牌时间";
        new AlertDialog.Builder(this).setTitle("按时长升序（绿 / 黄 / 红）")
                .setItems(options, (dialog, which) -> {
                    if (which == values.length) { customTimes(); return; }
                    int index = order[which];
                    applyTimes(names[index], values[index][0], values[index][1], values[index][2]);
                }).show();
    }

    private void customTimes() {
        LinearLayout fields = column();
        fields.setPadding(dp(24), dp(8), dp(24), 0);
        EditText[] inputs = new EditText[3];
        int[] values = {green, yellow, red};
        String[] names = {"绿牌", "黄牌", "红牌"};
        for (int i = 0; i < 3; i++) {
            TextView name = new TextView(this);
            name.setText(names[i] + "时间（分:秒，例如 01:30）");
            fields.addView(name);
            inputs[i] = new EditText(this);
            inputs[i].setSingleLine(true);
            inputs[i].setInputType(android.text.InputType.TYPE_CLASS_DATETIME | android.text.InputType.TYPE_DATETIME_VARIATION_TIME);
            inputs[i].setText(format(values[i]));
            fields.addView(inputs[i]);
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("自定义举牌时间")
                .setView(fields).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int[] parsed = new int[3];
            for (int i = 0; i < 3; i++) {
                parsed[i] = parseTime(inputs[i].getText().toString());
                if (parsed[i] <= 0) { inputs[i].setError("请输入 00:01 至 999:59 的分:秒"); return; }
            }
            if (parsed[0] >= parsed[1] || parsed[1] >= parsed[2]) {
                inputs[2].setError("时间必须满足：绿牌 < 黄牌 < 红牌"); return;
            }
            applyTimes("自定义", parsed[0], parsed[1], parsed[2]);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private int parseTime(String text) {
        if (!text.trim().matches("\\d{1,3}:[0-5]\\d")) return -1;
        String[] parts = text.trim().split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private void applyTimes(String name, int g, int y, int r) {
        preset = name; green = g; yellow = y; red = r;
        preferences.edit().putString("preset", preset).putInt("green", green)
                .putInt("yellow", yellow).putInt("red", red).apply();
        updateDisplay();
    }

    private void editSpeaker() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("演讲者姓名");
        input.setText(speaker);
        new AlertDialog.Builder(this).setTitle("演讲者").setView(input)
                .setNegativeButton("取消", null).setPositiveButton("保存", (dialog, which) -> {
                    speaker = input.getText().toString().trim();
                    speakerButton.setText(speaker.isEmpty() ? "＋ 添加演讲者（可选）" : "演讲者：" + speaker);
                }).show();
    }

    private void finishSession() {
        cancelVoiceStandby();
        long elapsed = timer.elapsed(SystemClock.elapsedRealtime());
        if (elapsed == 0) return;
        timer.pause(SystemClock.elapsedRealtime());
        SpeechCapture completed = recording;
        File audio = retainedAudio;
        recording = null;
        retainedAudio = null;
        if (completed != null) RecordingService.detach(completed);
        String id = UUID.randomUUID().toString();
        try {
            JSONObject record = new JSONObject();
            record.put("id", id);
            record.put("speaker", speaker.isEmpty() ? "演讲者 " + (records.length() + 1) : speaker);
            record.put("preset", preset);
            record.put("duration", format(elapsed / 1000));
            record.put("status", elapsed < green * 1000L ? "未到绿牌" : elapsed > red * 1000L ? "超过红牌时间" : "已到绿牌");
            record.put("time", new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date()));
            if (audio != null) {
                record.put("audio", audio.getName());
                record.put("transcriptionState", "正在保存录音…");
            }
            records.put(record);
            saveRecords();
        } catch (JSONException exception) { throw new IllegalStateException(exception); }
        android.content.Context app = getApplicationContext();
        if (completed != null) completed.finish(file -> {
            if (file != null) SpeechTranscriber.submit(app, id, file);
            else SpeechTranscriber.recordingFailed(app, id);
        });
        else if (audio != null && audio.isFile()) SpeechTranscriber.submit(app, id, audio);
        speaker = "";
        timer.reset(); lastStage = 0; bellNotified = false;
        recordingUnavailable = false;
        fullScreen = false;
        buildScreen();
    }

    private void resetTimer() {
        cancelVoiceStandby();
        discardRecording();
        recordingUnavailable = false;
        timer.reset(); lastStage = 0; bellNotified = false;
        updateDisplay();
    }

    private void saveRecords() { preferences.edit().putString("records", records.toString()).apply(); }

    private void showRecords() {
        if (recordsView == null || recordCards == null) return;
        recordCards.removeAllViews();
        recordsView.setText(records.length() == 0 ? "暂无记录。结束一次计时后，成绩会保存在这里。" : "共 " + records.length() + " 条记录");
        for (int i = records.length() - 1; i >= 0; i--) {
            JSONObject record = records.optJSONObject(i);
            if (record == null) continue;
            LinearLayout entry = column();
            entry.setTag("speech-record-" + record.optString("id"));
            entry.setPadding(dp(14), dp(14), dp(14), dp(14));
            entry.setBackground(background(PANEL));
            entry.addView(label(record.optString("speaker") + "  ·  " + record.optString("duration")
                    + "\n" + record.optString("preset") + " / " + record.optString("status")
                    + "  ·  " + record.optString("time"), 14, Color.WHITE));
            String filename = record.optString("audio");
            if (filename.matches("[a-f0-9-]+\\.wav")) {
                File audio = new File(getFilesDir(), "recordings/" + filename);
                String id = record.optString("id");
                String state = record.optString("transcriptionState", "待生成文字稿");
                if (!SpeechTranscriber.isPending(id) && (state.equals("正在生成文字稿…") || state.equals("正在保存录音…")))
                    state = "录音已保存，可生成或重试文字稿";
                add(entry, label(state, 12, MUTED), 8);
                Button play = button("回放录音", BACKGROUND, Color.WHITE);
                play.setEnabled(audio.isFile() && audio.length() > 44);
                play.setOnClickListener(v -> playRecording(audio));
                add(entry, play, 8);
                String transcript = record.optString("transcript");
                if (!transcript.isEmpty()) {
                    Button view = button("查看文字稿", BACKGROUND, Color.WHITE);
                    view.setOnClickListener(v -> showTranscript(record));
                    add(entry, view, 6);
                }
                if (!SpeechTranscriber.isPending(id)) {
                    Button retry = button(transcript.isEmpty() ? "生成 / 重试文字稿" : "重新生成文字稿", BACKGROUND, MUTED);
                    retry.setEnabled(audio.isFile() && audio.length() > 44);
                    retry.setOnClickListener(v -> SpeechTranscriber.submit(this, id, audio));
                    add(entry, retry, 6);
                }
            }
            add(recordCards, entry, 10);
        }
    }

    private void playRecording(File audio) {
        stopPlayback();
        MediaPlayer player = new MediaPlayer();
        playback = player;
        try {
            player.setDataSource(audio.getAbsolutePath());
            player.setOnPreparedListener(ready -> { if (playback == ready) ready.start(); });
            player.setOnCompletionListener(done -> { if (playback == done) stopPlayback(); });
            player.setOnErrorListener((failed, what, extra) -> {
                if (playback == failed) stopPlayback();
                Toast.makeText(this, "无法回放此录音", Toast.LENGTH_SHORT).show();
                return true;
            });
            playbackDialog = new AlertDialog.Builder(this).setTitle("录音回放")
                    .setMessage("正在播放当前演讲的录音")
                    .setPositiveButton("停止", (dialog, which) -> stopPlayback()).create();
            playbackDialog.setOnDismissListener(dialog -> stopPlayback());
            playbackDialog.show();
            player.prepareAsync();
        } catch (IOException | RuntimeException exception) {
            stopPlayback();
            Toast.makeText(this, "录音文件不可用", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPlayback() {
        AlertDialog dialog = playbackDialog;
        playbackDialog = null;
        if (dialog != null) dialog.dismiss();
        if (playback != null) { playback.release(); playback = null; }
    }

    private void showTranscript(JSONObject record) {
        TextView text = new TextView(this);
        String transcript = record.optString("transcript");
        text.setText(transcript);
        text.setTextIsSelectable(true);
        text.setPadding(dp(22), dp(16), dp(22), dp(16));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);
        new AlertDialog.Builder(this).setTitle(record.optString("speaker") + " · 文字稿")
                .setView(scroll).setPositiveButton("关闭", null)
                .setNeutralButton("复制全文", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("演讲文字稿", transcript));
                    Toast.makeText(this, "文字稿已复制", Toast.LENGTH_SHORT).show();
                }).show();
    }

    @Override protected void onStart() {
        super.onStart();
        SpeechCapture active = RecordingService.current();
        if (active != null) {
            recording = active;
            retainedAudio = active.file;
            attachRecording();
        }
        handler.post(tick);
    }
    @Override protected void onStop() {
        if (voiceArmed) { cancelVoiceStandby(); updateDisplay(); }
        if (recording != null) recording.setListener(null);
        stopPlayback();
        handler.removeCallbacks(tick);
        super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("elapsed", timer.getAccumulated());
        state.putLong("started", timer.getStartedAt());
        state.putBoolean("running", timer.isRunning());
        state.putBoolean("fullScreen", fullScreen);
        state.putInt("lastStage", lastStage);
        state.putBoolean("bellNotified", bellNotified);
        state.putString("speaker", speaker);
        state.putBoolean("recordingUnavailable", recordingUnavailable);
        state.putInt("permissionAction", permissionAction);
        if (retainedAudio != null) state.putString("audio", retainedAudio.getName());
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        preferences.unregisterOnSharedPreferenceChangeListener(recordChanges);
        super.onDestroy();
    }

    private String format(long seconds) { return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.HORIZONTAL); return layout; }
    private TextView label(String text, int size, int color) {
        TextView view = new androidx.appcompat.widget.AppCompatTextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private GradientDrawable background(int color) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(18)); return drawable;
    }
    private Button button(String text, int color, int textColor) {
        Button view = new androidx.appcompat.widget.AppCompatButton(this);
        view.setText(text); view.setTextSize(15); view.setAllCaps(false); view.setTextColor(textColor);
        view.setBackground(background(color)); view.setMinHeight(dp(52)); view.setMinimumHeight(dp(52));
        view.setPadding(dp(8), dp(10), dp(8), dp(10));
        return view;
    }
    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(dp(3), 0, dp(3), 0); return params;
    }
    private void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(top); parent.addView(child, params);
    }
}
