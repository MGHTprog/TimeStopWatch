package com.mght.timestopwatch;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.net.Uri;
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
import android.widget.CheckBox;
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
import androidx.core.content.FileProvider;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
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
import java.util.ArrayList;
import java.util.Calendar;

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
    private TextView agendaCurrent, agendaOverall, agendaActual, agendaActualItem;
    private ProgressBar agendaProgress, agendaItemProgress, agendaActualOverallProgress, agendaActualProgress;
    private final ArrayList<AgendaItem> agendaItems = new ArrayList<>();
    private Uri pendingAgendaPhoto;
    private boolean agendaOfflineOnce;
    private int agendaOfficialStart = -1, agendaOfficialEnd = -1;
    private String agendaTitle = "", agendaDate = "";
    private int activeAgendaIndex = -1;
    private int agendaActualBaseSeconds;
    private ProgressBar timeProgress;
    private Button startButton, settingsButton, speakerButton, voiceButton;
    private TextView voiceStatus;
    private VoiceMonitor voiceMonitor;
    private SpeechCapture recording;
    private File retainedAudio;
    private boolean recordEnabled = true;
    private boolean recordingUnavailable;
    private int permissionAction;
    private LinearLayout recordCards;
    private MediaPlayer playback;
    private MediaPlayer bellPlayer;
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
    private final ActivityResultLauncher<String[]> agendaPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) parseAgendaImage(uri); else agendaOfflineOnce = false;
            });
    private final ActivityResultLauncher<Uri> agendaCamera = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), saved -> {
                if (saved && pendingAgendaPhoto != null) parseAgendaImage(pendingAgendaPhoto);
            });
    private int green = 60, yellow = 90, red = 120;
    private boolean cardsEnabled = true;
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
        cardsEnabled = preferences.getBoolean("cardsEnabled", true);
        preset = preferences.getString("preset", "即兴演讲");
        vibration = preferences.getBoolean("vibration", true);
        recordEnabled = preferences.getBoolean("recordEnabled", true);
        preferences.registerOnSharedPreferenceChangeListener(recordChanges);
        try { records = new JSONArray(preferences.getString("records", "[]")); }
        catch (JSONException ignored) { records = new JSONArray(); }
        loadAgenda();
        if (state != null) {
            timer.restore(state.getLong("elapsed"), state.getLong("started"), state.getBoolean("running"));
            fullScreen = state.getBoolean("fullScreen");
            lastStage = state.getInt("lastStage");
            bellNotified = state.getBoolean("bellNotified");
            speaker = state.getString("speaker", "");
            recordingUnavailable = state.getBoolean("recordingUnavailable");
            permissionAction = state.getInt("permissionAction");
            activeAgendaIndex = state.getInt("activeAgendaIndex", activeAgendaIndex);
            agendaActualBaseSeconds = state.getInt("agendaActualBaseSeconds", agendaActualBaseSeconds);
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
        LinearLayout content = column();
        content.setPadding(dp(fullScreen ? 8 : 12), dp(fullScreen ? 8 : 10), dp(fullScreen ? 8 : 12), dp(10));
        root.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        if (!fullScreen) {
            LinearLayout header = row();
            TextView title = label("头马时间官", 22, Color.WHITE);
            title.setTypeface(null, Typeface.BOLD);
            header.addView(title, weighted());
            Button recordsButton = button("记录 " + records.length(), PANEL, Color.WHITE);
            recordsButton.setOnClickListener(v -> showRecordsDialog());
            LinearLayout.LayoutParams recordSize = new LinearLayout.LayoutParams(dp(82), dp(44));
            recordSize.setMarginEnd(dp(6));
            header.addView(recordsButton, recordSize);
            settingsButton = button("⚙", PANEL, Color.WHITE);
            settingsButton.setContentDescription("设置");
            settingsButton.setOnClickListener(v -> showSettings());
            LinearLayout.LayoutParams gearSize = new LinearLayout.LayoutParams(dp(50), dp(44));
            header.addView(settingsButton, gearSize);
            content.addView(header);
            presetLabel = label("", 12, MUTED);
            add(content, presetLabel, 2);
            if (!agendaItems.isEmpty()) addAgendaPanel(content);
            speakerButton = button(speaker.isEmpty() ? "选择演讲者（可选）" : "演讲者：" + speaker, PANEL, MUTED);
            speakerButton.setOnClickListener(v -> editSpeaker());
            add(content, speakerButton, 4);
        }
        card = column();
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(8), dp(fullScreen ? 42 : 10), dp(8), dp(fullScreen ? 42 : 10));
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
        clock = label("00:00", fullScreen ? 76 : 50, Color.WHITE);
        clock.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        clock.setGravity(Gravity.CENTER);
        clock.setSingleLine(true);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(clock, 32, fullScreen ? 76 : 64, 2, android.util.TypedValue.COMPLEX_UNIT_SP);
        add(card, clock, 6);
        totalLabel = label("", 12, Color.WHITE);
        totalLabel.setGravity(Gravity.CENTER);
        add(card, totalLabel, 12);
        timeProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        timeProgress.setIndeterminate(false);
        timeProgress.setMax(red * 1000);
        timeProgress.setProgressBackgroundTintList(ColorStateList.valueOf(0x30000000));
        LinearLayout.LayoutParams progressSize = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        progressSize.setMargins(dp(10), dp(7), dp(10), 0);
        card.addView(timeProgress, progressSize);
        nextLabel = label("", 12, Color.WHITE);
        nextLabel.setGravity(Gravity.CENTER);
        add(card, nextLabel, 5);
        add(content, card, fullScreen ? 0 : 5);
        startButton = button("开始计时", 0xFF78DCC5, BACKGROUND);
        startButton.setOnClickListener(v -> startOrPauseTimer());
        voiceButton = button("语音待命", PANEL, Color.WHITE);
        voiceButton.setOnClickListener(v -> {
            if (voiceArmed) {
                cancelVoiceStandby();
                updateDisplay();
            } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) startVoiceStandby();
            else { permissionAction = 1; microphonePermission.launch(Manifest.permission.RECORD_AUDIO); }
        });
        LinearLayout startActions = row();
        startActions.addView(startButton, weighted());
        startActions.addView(voiceButton, weighted());
        add(content, startActions, 5);
        voiceStatus = label("", 12, MUTED);
        voiceStatus.setGravity(Gravity.CENTER);
        voiceStatus.setMaxLines(1);
        add(content, voiceStatus, 2);
        LinearLayout actions = row();
        Button reset = button("重置", PANEL, Color.WHITE);
        reset.setOnClickListener(v -> {
            if (timer.elapsed(SystemClock.elapsedRealtime()) == 0) return;
            new AlertDialog.Builder(this).setTitle("重置当前计时？")
                    .setMessage("当前计时和本轮录音将清空。如需保留，请先结束并记录。")
                    .setNegativeButton("取消", null).setPositiveButton("重置", (dialog, which) -> resetTimer()).show();
        });
        Button finish = button("停止并保存", PANEL, Color.WHITE);
        finish.setOnClickListener(v -> finishSession(false));
        actions.addView(reset, weighted());
        actions.addView(finish, weighted());
        add(content, actions, 6);
        Button finishNext = button("停止保存 · 切到下一环节", PANEL, Color.WHITE);
        finishNext.setOnClickListener(v -> finishSession(true));
        finishNext.setEnabled(nextAgendaIndex() >= 0);
        Button fullscreenButton = button(fullScreen ? "返回控制台" : "全屏举牌", PANEL, Color.WHITE);
        fullscreenButton.setOnClickListener(v -> { fullScreen = !fullScreen; buildScreen(); });
        LinearLayout secondaryActions = row();
        secondaryActions.addView(finishNext, weighted());
        secondaryActions.addView(fullscreenButton, weighted());
        add(content, secondaryActions, 5);
        if (fullScreen) {
            speakerButton = button(speaker.isEmpty() ? "选择演讲者（可选）" : "演讲者：" + speaker, PANEL, MUTED);
            speakerButton.setOnClickListener(v -> editSpeaker());
            add(content, speakerButton, 8);
        }
        if (!fullScreen) {
            recordsView = null;
            recordCards = null;
        } else {
            presetLabel = null;
            settingsButton = null;
            recordsView = null;
            recordCards = null;
        }
        updateDisplay();
    }

    private void updateDisplay() {
        long elapsed = timer.elapsed(SystemClock.elapsedRealtime());
        int automatic = cardsEnabled ? TimerSession.stage(elapsed, green, yellow, red) : 0;
        boolean bellDue = cardsEnabled && TimerSession.isBellDue(elapsed, red);
        if (bellDue && !bellNotified && timer.isRunning()) {
            bellNotified = true;
            playBell();
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
        displayState += ":cards:" + cardsEnabled + ":agendaIndex:" + activeAgendaIndex;
        if (!agendaItems.isEmpty()) displayState += ":agenda:" + (System.currentTimeMillis() / 1000);
        if (displayState.equals(displayedState)) return;
        displayedState = displayState;
        int foreground = shown == 2 ? BACKGROUND : Color.WHITE;
        int cardColor = cardsEnabled ? CARD_COLORS[shown] : PANEL;
        card.setBackground(background(cardColor));
        root.setBackgroundColor(fullScreen ? cardColor : BACKGROUND);
        WindowInsetsControllerCompat systemBars = WindowCompat.getInsetsController(getWindow(), root);
        systemBars.setAppearanceLightStatusBars(fullScreen && shown == 2);
        systemBars.setAppearanceLightNavigationBars(fullScreen && shown == 2);
        clock.setText(format(elapsed / 1000));
        clock.setTextColor(foreground);
        bellIndicator.setVisibility(bellDue ? View.VISIBLE : View.GONE);
        bellIndicator.setColorFilter(foreground);
        stageLabel.setText(cardsEnabled ? (bellDue ? "按铃 · 红牌后已满30秒" : STAGES[shown]) : "普通计时 · 不需要举牌");
        stageLabel.setTextColor(foreground);
        totalLabel.setTextColor(foreground);
        totalLabel.setText("总时间 " + format(red) + " · "
                + (elapsed < red * 1000L ? "剩余 " + format(red - elapsed / 1000)
                : "超时 +" + format(elapsed / 1000 - red)));
        timeProgress.setProgressTintList(ColorStateList.valueOf(foreground));
        nextLabel.setTextColor(foreground);
        if (!cardsEnabled) {
            nextLabel.setText(elapsed < red * 1000L ? "距离计划结束 " + format(red - elapsed / 1000)
                    : "超出计划时间 +" + format(elapsed / 1000 - red));
        } else if (automatic < 3) {
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
        if (settingsButton != null) settingsButton.setEnabled(true);
        if (presetLabel != null) presetLabel.setText(cardsEnabled
                ? preset + "   ·   " + format(green) + " / " + format(yellow) + " / " + format(red)
                : preset + "   ·   不举牌 · 计划 " + format(red));
        updateAgendaProgress();
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

    private void chooseAgendaImage() {
        new AlertDialog.Builder(this).setTitle("导入会议时间表")
                .setItems(new String[]{"拍照并用 DeepSeek 解析", "选择图片并用 DeepSeek 解析", "仅本机 OCR（离线备用）"}, (dialog, which) -> {
                    if (which == 2) {
                        agendaOfflineOnce = true;
                        agendaPicker.launch(new String[]{"image/*"});
                        return;
                    }
                    if (which == 1) {
                        agendaOfflineOnce = false;
                        agendaPicker.launch(new String[]{"image/*"});
                        return;
                    }
                    try {
                        agendaOfflineOnce = false;
                        File directory = new File(getCacheDir(), "agenda_photos");
                        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建拍照缓存");
                        File photo = new File(directory, "agenda-" + System.currentTimeMillis() + ".jpg");
                        pendingAgendaPhoto = FileProvider.getUriForFile(this, getPackageName() + ".files", photo);
                        agendaCamera.launch(pendingAgendaPhoto);
                    } catch (IOException error) {
                        Toast.makeText(this, "无法打开相机：" + error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void parseAgendaImage(Uri uri) {
        if (agendaOfflineOnce) {
            agendaOfflineOnce = false;
            recognizeAgenda(uri);
            return;
        }
        String key = SecretStore.load(this);
        if (!key.isEmpty()) {
            recognizeAgendaWithDeepSeek(uri, key);
            return;
        }
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("sk-…");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("配置 DeepSeek API Key")
                .setMessage("Key 将由 Android Keystore 加密保存在本机，不会写入 APK。图片会发送至 DeepSeek 解析。")
                .setView(input).setNegativeButton("取消", null).setPositiveButton("保存并解析", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (value.length() < 10) { input.setError("请输入有效的 API Key"); return; }
            try {
                SecretStore.save(this, value);
                dialog.dismiss();
                recognizeAgendaWithDeepSeek(uri, value);
            } catch (Exception error) {
                input.setError("无法安全保存 Key：" + error.getMessage());
            }
        }));
        dialog.show();
    }

    private void recognizeAgendaWithDeepSeek(Uri uri, String key) {
        AlertDialog waiting = new AlertDialog.Builder(this).setTitle("DeepSeek 正在解析时间表")
                .setMessage("正在理解合并单元格、活动、时间和会议角色…").setCancelable(false).create();
        waiting.show();
        DeepSeekAgendaService.parse(this, uri, key, new DeepSeekAgendaService.Callback() {
            @Override public void success(DeepSeekAgendaService.Result result) {
                runOnUiThread(() -> {
                    waiting.dismiss();
                    previewDeepSeekResult(result);
                });
            }
            @Override public void failure(Exception error) {
                runOnUiThread(() -> {
                    waiting.dismiss();
                    new AlertDialog.Builder(MainActivity.this).setTitle("DeepSeek 解析失败")
                            .setMessage(error.getMessage() == null ? "网络请求或图片解析失败。" : error.getMessage())
                            .setNegativeButton("取消", null)
                            .setNeutralButton("清除 API Key", (dialog, which) -> SecretStore.clear(MainActivity.this))
                            .setPositiveButton("改用本机 OCR", (dialog, which) -> recognizeAgenda(uri)).show();
                });
            }
        });
    }

    private void previewDeepSeekResult(DeepSeekAgendaService.Result result) {
        StringBuilder preview = new StringBuilder();
        if (!result.title.isEmpty()) preview.append(result.title).append('\n');
        preview.append("正式会议：").append(AgendaItem.clock(result.officialStart)).append("–")
                .append(AgendaItem.clock(result.officialEnd)).append("\n\n");
        for (AgendaItem item : result.items) preview.append(item.label()).append('\n');
        if (!result.warnings.isEmpty()) {
            preview.append("\n需要注意：\n");
            for (String warning : result.warnings) preview.append("• ").append(warning).append('\n');
        }
        new AlertDialog.Builder(this).setTitle("确认解析结果（共" + result.items.size() + "项）")
                .setMessage(preview.toString().trim()).setNegativeButton("取消", null)
                .setNeutralButton("编辑议程", (dialog, which) -> showAgendaEditor(result))
                .setPositiveButton("确认导入", (dialog, which) -> {
                    agendaItems.clear();
                    agendaItems.addAll(result.items);
                    agendaOfficialStart = result.officialStart;
                    agendaOfficialEnd = result.officialEnd;
                    agendaTitle = result.title;
                    agendaDate = result.date;
                    activeAgendaIndex = -1;
                    agendaActualBaseSeconds = 0;
                    preferences.edit().putInt("activeAgendaIndex", -1).putInt("agendaActualBaseSeconds", 0).apply();
                    saveAgenda();
                    buildScreen();
                }).show();
    }

    private void showAgendaEditor(DeepSeekAgendaService.Result result) {
        String[] labels = new String[result.items.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = (i + 1) + ". " + result.items.get(i).label();
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("编辑识别结果")
                .setItems(labels, (selected, which) -> editAgendaItem(result, result.items.get(which), false))
                .setNegativeButton("取消", null)
                .setNeutralButton("新增环节", null)
                .setPositiveButton("返回确认", (selected, which) -> previewDeepSeekResult(result)).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            dialog.dismiss();
            AgendaItem added = new AgendaItem("", "", 1);
            int start = result.items.isEmpty() ? result.officialStart : result.items.get(result.items.size() - 1).endMinute;
            added.startMinute = Math.max(0, start); added.endMinute = added.startMinute + 1;
            editAgendaItem(result, added, true);
        }));
        dialog.show();
    }

    private void editAgendaItem(DeepSeekAgendaService.Result result, AgendaItem item, boolean isNew) {
        LinearLayout fields = column();
        fields.setPadding(dp(24), dp(8), dp(24), 0);
        String[] names = {"活动", "角色（可空）", "开始时间", "结束时间", "章节（可空）"};
        String[] values = {item.activity, item.role, AgendaItem.clock(item.startMinute), AgendaItem.clock(item.endMinute), item.section};
        EditText[] inputs = new EditText[names.length];
        for (int i = 0; i < inputs.length; i++) {
            fields.addView(label(names[i], 12, MUTED));
            inputs[i] = new EditText(this);
            inputs[i].setSingleLine(true);
            inputs[i].setText(values[i]);
            inputs[i].setTextColor(Color.WHITE);
            inputs[i].setHintTextColor(MUTED);
            fields.addView(inputs[i]);
        }
        CheckBox cards = new CheckBox(this);
        cards.setText("这个环节需要绿/黄/红举牌"); cards.setTextColor(Color.WHITE); cards.setChecked(item.requiresCards);
        fields.addView(cards);
        CheckBox inferred = new CheckBox(this);
        inferred.setText("时间由 AI 推算"); inferred.setTextColor(MUTED); inferred.setChecked(item.inferred);
        fields.addView(inferred);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(isNew ? "新增议程环节" : "编辑议程环节")
                .setView(fields).setNegativeButton("取消", (selected, which) -> showAgendaEditor(result))
                .setNeutralButton(isNew ? null : "删除", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(ignored -> {
            if (!isNew) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                result.items.remove(item); dialog.dismiss(); showAgendaEditor(result);
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String activity = inputs[0].getText().toString().trim();
                int start = parseClockMinute(inputs[2].getText().toString());
                int end = parseClockMinute(inputs[3].getText().toString());
                if (activity.isEmpty()) { inputs[0].setError("请输入活动名称"); return; }
                if (start < 0) { inputs[2].setError("请输入 HH:mm"); return; }
                if (end <= start) { inputs[3].setError("结束时间必须晚于开始时间"); return; }
                item.activity = activity;
                item.role = inputs[1].getText().toString().trim();
                item.startMinute = start; item.endMinute = end; item.minutes = end - start;
                item.section = inputs[4].getText().toString().trim();
                item.requiresCards = cards.isChecked(); item.inferred = inferred.isChecked();
                if (isNew) result.items.add(item);
                dialog.dismiss(); showAgendaEditor(result);
            });
        });
        dialog.show();
    }

    private int parseClockMinute(String text) {
        String value = text.trim();
        if (!value.matches("(?:[01]?\\d|2[0-3]):[0-5]\\d")) return -1;
        String[] parts = value.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private void recognizeAgenda(Uri uri) {
        AlertDialog waiting = new AlertDialog.Builder(this).setTitle("正在解析时间表")
                .setMessage("正在识别活动、时间和会议角色…").setCancelable(false).create();
        waiting.show();
        AgendaRecognizer.recognize(this, uri, new AgendaRecognizer.Callback() {
            @Override public void success(java.util.List<AgendaItem> items) {
                waiting.dismiss();
                ArrayList<AgendaItem> timed = new ArrayList<>();
                for (AgendaItem item : items) if (item.startMinute >= 0) timed.add(item);
                if (timed.isEmpty()) {
                    new AlertDialog.Builder(MainActivity.this).setTitle("没有识别到议程")
                            .setMessage("请尽量正对时间表拍摄，确保“开始时间、活动、时长”几列清晰完整。")
                            .setPositiveButton("知道了", null).show();
                    return;
                }
                agendaItems.clear();
                agendaItems.addAll(timed);
                agendaOfficialStart = timed.get(0).startMinute;
                agendaOfficialEnd = timed.get(timed.size() - 1).endMinute;
                agendaTitle = "";
                agendaDate = "";
                activeAgendaIndex = -1;
                agendaActualBaseSeconds = 0;
                preferences.edit().putInt("activeAgendaIndex", -1).putInt("agendaActualBaseSeconds", 0).apply();
                saveAgenda();
                buildScreen();
                showAgendaItems();
            }
            @Override public void failure(Exception error) {
                waiting.dismiss();
                new AlertDialog.Builder(MainActivity.this).setTitle("时间表解析失败")
                        .setMessage(error.getMessage() == null ? "无法读取这张图片，请换一张清晰图片重试。" : error.getMessage())
                        .setPositiveButton("知道了", null).show();
            }
        });
    }

    private void addAgendaPanel(LinearLayout content) {
        LinearLayout panel = column();
        panel.setPadding(dp(5), dp(5), dp(5), dp(5));
        panel.setBackground(background(PANEL));

        LinearLayout timeBlocks = row();

        LinearLayout plannedPanel = column();
        plannedPanel.setPadding(dp(8), dp(7), dp(8), dp(7));
        plannedPanel.setBackground(background(BACKGROUND));
        TextView plannedTitle = label("计划时间", 14, Color.WHITE);
        plannedTitle.setTypeface(null, Typeface.BOLD);
        plannedPanel.addView(plannedTitle);
        agendaOverall = label("", 11, MUTED);
        agendaOverall.setMaxLines(2);
        add(plannedPanel, agendaOverall, 3);
        agendaProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        agendaProgress.setProgressTintList(ColorStateList.valueOf(0xFF78DCC5));
        add(plannedPanel, agendaProgress, 3);
        agendaCurrent = label("", 11, Color.WHITE);
        agendaCurrent.setMaxLines(2);
        add(plannedPanel, agendaCurrent, 4);
        agendaItemProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        agendaItemProgress.setProgressTintList(ColorStateList.valueOf(0xFFF4C542));
        add(plannedPanel, agendaItemProgress, 3);
        timeBlocks.addView(plannedPanel, weighted());

        LinearLayout actualPanel = column();
        actualPanel.setPadding(dp(8), dp(7), dp(8), dp(7));
        actualPanel.setBackground(background(BACKGROUND));
        TextView actualTitle = label("实际时间", 14, Color.WHITE);
        actualTitle.setTypeface(null, Typeface.BOLD);
        actualPanel.addView(actualTitle);
        agendaActual = label("", 11, Color.WHITE);
        agendaActual.setMaxLines(2);
        add(actualPanel, agendaActual, 3);
        agendaActualOverallProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        agendaActualOverallProgress.setProgressTintList(ColorStateList.valueOf(0xFF78DCC5));
        add(actualPanel, agendaActualOverallProgress, 3);
        agendaActualItem = label("", 11, Color.WHITE);
        agendaActualItem.setMaxLines(2);
        add(actualPanel, agendaActualItem, 4);
        agendaActualProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        agendaActualProgress.setProgressTintList(ColorStateList.valueOf(0xFF78DCC5));
        add(actualPanel, agendaActualProgress, 3);
        timeBlocks.addView(actualPanel, weighted());
        panel.addView(timeBlocks);

        Button all = button("查看议程 / 选择计时项目", BACKGROUND, Color.WHITE);
        all.setOnClickListener(v -> showAgendaItems());
        add(panel, all, 4);
        add(content, panel, 4);
    }

    private void updateAgendaProgress() {
        if (agendaItems.isEmpty() || agendaProgress == null) return;
        AgendaItem first = agendaItems.get(0);
        AgendaItem last = agendaItems.get(agendaItems.size() - 1);
        Calendar now = Calendar.getInstance();
        int secondOfDay = (now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)) * 60
                + now.get(Calendar.SECOND);
        int meetingStart = (agendaOfficialStart >= 0 ? agendaOfficialStart : first.startMinute) * 60;
        int meetingEnd = (agendaOfficialEnd > agendaOfficialStart ? agendaOfficialEnd : last.endMinute) * 60;
        int meetingLength = Math.max(1, meetingEnd - meetingStart);
        agendaProgress.setMax(meetingLength);
        agendaProgress.setProgress(Math.max(0, Math.min(meetingLength, secondOfDay - meetingStart)));
        int percent = Math.max(0, Math.min(100, (secondOfDay - meetingStart) * 100 / meetingLength));
        String nowClock = String.format(Locale.CHINA, "%02d:%02d:%02d", now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE), now.get(Calendar.SECOND));
        agendaOverall.setText((agendaTitle.isEmpty() ? "会议" : agendaTitle)
                + "\n议程安排 " + AgendaItem.clock(meetingStart / 60) + "–" + AgendaItem.clock(meetingEnd / 60)
                + " · 计划进度 " + percent + "%");

        updateActualAgendaProgress(nowClock, secondOfDay, meetingLength);

        AgendaItem current = null;
        for (AgendaItem item : agendaItems) {
            if (secondOfDay >= item.startMinute * 60 && secondOfDay < item.endMinute * 60) {
                current = item;
                break;
            }
        }
        if (current == null) {
            if (secondOfDay < meetingStart) {
                agendaCurrent.setText("会议尚未开始 · 距开始 " + format((meetingStart - secondOfDay + 59) / 60 * 60L));
                agendaItemProgress.setMax(1); agendaItemProgress.setProgress(0);
            } else if (secondOfDay >= meetingEnd) {
                agendaCurrent.setText("会议议程已结束");
                agendaItemProgress.setMax(1); agendaItemProgress.setProgress(1);
            } else {
                AgendaItem next = null;
                for (AgendaItem item : agendaItems) if (item.startMinute * 60 > secondOfDay) { next = item; break; }
                agendaCurrent.setText(next == null ? "当前处于议程空档"
                        : "当前处于议程空档 · 下一项 " + AgendaItem.clock(next.startMinute) + " " + next.activity);
                agendaItemProgress.setMax(1); agendaItemProgress.setProgress(0);
            }
            return;
        }
        int itemLength = Math.max(1, (current.endMinute - current.startMinute) * 60);
        int itemElapsed = secondOfDay - current.startMinute * 60;
        agendaItemProgress.setMax(itemLength);
        agendaItemProgress.setProgress(itemElapsed);
        agendaCurrent.setText("计划当前：" + current.activity + (current.role.isEmpty() ? "" : " · " + current.role)
                + "\n计划已进行 " + format(itemElapsed) + " · 剩余 " + format(itemLength - itemElapsed));
    }

    private void updateActualAgendaProgress(String nowClock, int secondOfDay, int meetingLength) {
        if (agendaActual == null || agendaActualItem == null || agendaActualProgress == null
                || agendaActualOverallProgress == null) return;
        if (activeAgendaIndex < 0 || activeAgendaIndex >= agendaItems.size()) {
            agendaActual.setText("当前 " + nowClock + " · 现场整体未开始");
            agendaActualItem.setText("请先选择一个现场环节");
            agendaActualOverallProgress.setMax(Math.max(1, meetingLength));
            agendaActualOverallProgress.setProgress(0);
            agendaActualProgress.setMax(1);
            agendaActualProgress.setProgress(0);
            return;
        }
        AgendaItem active = agendaItems.get(activeAgendaIndex);
        int expected = Math.max(1, active.minutes * 60);
        long elapsed = timer.elapsed(SystemClock.elapsedRealtime()) / 1000;
        long actualOverall = Math.max(0, agendaActualBaseSeconds + elapsed);
        // Compare the projected finish of the live item with its scheduled finish. During the
        // normal portion, remaining live time is included in the projection. Once overtime starts,
        // remaining time is zero, so every extra second correctly increases the schedule delay.
        long remaining = Math.max(0, expected - elapsed);
        long projectedFinish = secondOfDay + remaining;
        long scheduleDifference = active.endMinute * 60L - projectedFinish;
        int overallPercent = (int) Math.min(100, actualOverall * 100 / Math.max(1, meetingLength));
        agendaActualOverallProgress.setMax(Math.max(1, meetingLength));
        agendaActualOverallProgress.setProgress((int) Math.min(meetingLength, actualOverall));
        agendaActualOverallProgress.setContentDescription("现场整体进度 " + overallPercent + "%");
        agendaActualProgress.setMax(expected);
        agendaActualProgress.setProgress((int) Math.min(expected, elapsed));
        int actualPercent = (int) Math.min(100, elapsed * 100 / expected);
        agendaActualProgress.setContentDescription("实际环节进度 " + actualPercent + "%");
        String scheduleState;
        if (Math.abs(scheduleDifference) < 2) {
            scheduleState = "预计与计划同步";
            agendaActualOverallProgress.setProgressTintList(ColorStateList.valueOf(0xFF78DCC5));
            agendaActualProgress.setProgressTintList(ColorStateList.valueOf(0xFF78DCC5));
        } else if (scheduleDifference > 0) {
            scheduleState = "预计比计划提前 " + format(scheduleDifference);
            agendaActualOverallProgress.setProgressTintList(ColorStateList.valueOf(0xFF78DCC5));
            agendaActualProgress.setProgressTintList(ColorStateList.valueOf(0xFF78DCC5));
        } else {
            scheduleState = (elapsed > expected ? "已比计划延后 " : "预计比计划延后 ")
                    + format(-scheduleDifference);
            agendaActualOverallProgress.setProgressTintList(ColorStateList.valueOf(0xFFF4C542));
            agendaActualProgress.setProgressTintList(ColorStateList.valueOf(0xFFF4C542));
        }
        String timing = elapsed <= expected
                ? "实际已进行 " + format(elapsed) + " · 剩余 " + format(expected - elapsed)
                : "实际已进行 " + format(elapsed) + " · 超时 +" + format(elapsed - expected);
        agendaActual.setText("当前 " + nowClock + " · 整体进度 " + overallPercent + "%\n" + scheduleState);
        agendaActualItem.setText("现场环节：" + active.activity + " · " + actualPercent + "%\n" + timing);
    }

    private void showAgendaItems() {
        if (agendaItems.isEmpty()) return;
        String[] labels = new String[agendaItems.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = agendaItems.get(i).label();
        new AlertDialog.Builder(this).setTitle("会议议程（点击进入单项计时）")
                .setItems(labels, (dialog, which) -> applyAgendaItem(which))
                .setNegativeButton("关闭", null).show();
    }

    private void applyAgendaItem(int index) {
        int meetingStart = agendaOfficialStart >= 0 ? agendaOfficialStart
                : (agendaItems.isEmpty() ? 0 : agendaItems.get(0).startMinute);
        int base = index >= 0 && index < agendaItems.size()
                ? Math.max(0, (agendaItems.get(index).startMinute - meetingStart) * 60) : 0;
        applyAgendaItem(index, base);
    }

    private void applyAgendaItem(int index, int actualBaseSeconds) {
        if (index < 0 || index >= agendaItems.size()) return;
        AgendaItem item = agendaItems.get(index);
        activeAgendaIndex = index;
        agendaActualBaseSeconds = Math.max(0, actualBaseSeconds);
        preferences.edit().putInt("activeAgendaIndex", index)
                .putInt("agendaActualBaseSeconds", agendaActualBaseSeconds).apply();
        int total = item.minutes * 60;
        if (item.requiresCards) {
            int g = Math.max(1, total - (item.minutes <= 3 ? 60 : 120));
            int y = Math.max(g + 1, total - (item.minutes <= 3 ? 30 : 60));
            applyTimes(item.activity, g, y, total);
        } else applyNoCardsTime(item.activity, total);
        if (!item.role.isEmpty()) selectSpeaker(item.role);
        Toast.makeText(this, "已载入：" + item.label(), Toast.LENGTH_SHORT).show();
    }

    private int nextAgendaIndex() {
        return activeAgendaIndex >= 0 && activeAgendaIndex + 1 < agendaItems.size() ? activeAgendaIndex + 1 : -1;
    }

    private void saveAgenda() {
        JSONArray saved = new JSONArray();
        try {
            for (AgendaItem item : agendaItems) {
                JSONObject value = new JSONObject();
                value.put("activity", item.activity).put("role", item.role).put("minutes", item.minutes)
                        .put("start", item.startMinute).put("end", item.endMinute).put("section", item.section)
                        .put("inferred", item.inferred).put("requiresCards", item.requiresCards);
                saved.put(value);
            }
        } catch (JSONException error) { throw new IllegalStateException(error); }
        preferences.edit().putString("agenda", saved.toString()).apply();
        preferences.edit().putInt("agendaOfficialStart", agendaOfficialStart)
                .putInt("agendaOfficialEnd", agendaOfficialEnd).putString("agendaTitle", agendaTitle)
                .putString("agendaDate", agendaDate).apply();
    }

    private void loadAgenda() {
        agendaItems.clear();
        agendaOfficialStart = preferences.getInt("agendaOfficialStart", -1);
        agendaOfficialEnd = preferences.getInt("agendaOfficialEnd", -1);
        agendaTitle = preferences.getString("agendaTitle", "");
        agendaDate = preferences.getString("agendaDate", "");
        activeAgendaIndex = preferences.getInt("activeAgendaIndex", -1);
        agendaActualBaseSeconds = preferences.getInt("agendaActualBaseSeconds", 0);
        try {
            JSONArray saved = new JSONArray(preferences.getString("agenda", "[]"));
            for (int i = 0; i < saved.length(); i++) {
                JSONObject value = saved.optJSONObject(i);
                if (value == null) continue;
                AgendaItem item = new AgendaItem(value.optString("activity"), value.optString("role"), value.optInt("minutes"));
                item.startMinute = value.optInt("start", -1);
                item.endMinute = value.optInt("end", -1);
                item.section = value.optString("section");
                item.inferred = value.optBoolean("inferred", false);
                item.requiresCards = value.optBoolean("requiresCards", true);
                if (!item.activity.isEmpty() && item.minutes > 0 && item.startMinute >= 0) agendaItems.add(item);
            }
        } catch (JSONException ignored) { }
        if (activeAgendaIndex < 0 || activeAgendaIndex >= agendaItems.size()) activeAgendaIndex = -1;
    }

    private void showSettings() {
        boolean locked = timer.isRunning() || timer.elapsed(SystemClock.elapsedRealtime()) > 0 || voiceArmed;
        String[] options = {
                "环节与计时设置" + (locked ? "（当前计时中）" : ""),
                agendaItems.isEmpty() ? "拍照 / 图片导入会议时间表" : "重新导入会议时间表",
                "录音与文字稿：" + (recordEnabled ? "已开启" : "已关闭"),
                "震动与按铃提醒：" + (vibration ? "已开启" : "已关闭"),
                "清除 DeepSeek API Key"
        };
        new AlertDialog.Builder(this).setTitle("⚙ 设置").setItems(options, (dialog, which) -> {
            if (which == 0) {
                if (locked) Toast.makeText(this, "请先停止或重置当前计时", Toast.LENGTH_SHORT).show();
                else choosePreset();
            } else if (which == 1) {
                if (locked) Toast.makeText(this, "请先停止或重置当前计时", Toast.LENGTH_SHORT).show();
                else chooseAgendaImage();
            } else if (which == 2) {
                if (locked) {
                    Toast.makeText(this, "本轮计时结束后再修改录音设置", Toast.LENGTH_SHORT).show();
                } else {
                    recordEnabled = !recordEnabled;
                    preferences.edit().putBoolean("recordEnabled", recordEnabled).apply();
                    displayedState = ""; updateDisplay(); showSettings();
                }
            } else if (which == 3) {
                vibration = !vibration;
                preferences.edit().putBoolean("vibration", vibration).apply();
                showSettings();
            } else {
                SecretStore.clear(this);
                Toast.makeText(this, "DeepSeek API Key 已清除", Toast.LENGTH_SHORT).show();
            }
        }).setNegativeButton("关闭", null).show();
    }

    private void showRecordsDialog() {
        LinearLayout body = column();
        body.setPadding(dp(18), dp(8), dp(18), dp(12));
        recordsView = label("", 14, MUTED);
        body.addView(recordsView);
        recordCards = column();
        add(body, recordCards, 8);
        Button clear = button("清空记录", PANEL, MUTED);
        clear.setOnClickListener(v -> {
            if (records.length() == 0) return;
            new AlertDialog.Builder(this).setTitle("清空所有计时记录？")
                    .setMessage("对应的录音和文字稿也会从本机删除。")
                    .setNegativeButton("取消", null).setPositiveButton("清空", (confirm, which) -> {
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
        add(body, clear, 10);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("会议记录")
                .setView(scroll).setPositiveButton("关闭", null).create();
        dialog.setOnDismissListener(ignored -> { recordsView = null; recordCards = null; });
        showRecords();
        dialog.show();
    }

    private void choosePreset() {
        String[] names = {"即兴演讲", "演讲评估", "备稿演讲 · 5分钟", "备稿演讲 · 7分钟", "备稿演讲 · 10分钟"};
        int[][] values = {{60, 90, 120}, {120, 150, 180}, {180, 240, 300}, {300, 360, 420}, {480, 540, 600}};
        // Sort by numeric total duration (red card), rather than comparing text such as "10" and "2".
        Integer[] order = new Integer[values.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, java.util.Comparator.comparingInt(index -> values[index][2]));
        String[] options = new String[values.length + 2];
        for (int i = 0; i < order.length; i++) {
            int index = order[i];
            String title = names[index].split(" · ")[0];
            options[i] = (values[index][2] / 60) + "分钟 · " + title + " · "
                    + format(values[index][0]) + " / " + format(values[index][1]) + " / " + format(values[index][2]);
        }
        options[values.length] = "普通计时（不需要举牌）";
        options[values.length + 1] = "自定义举牌时间";
        new AlertDialog.Builder(this).setTitle("按时长升序（绿 / 黄 / 红）")
                .setItems(options, (dialog, which) -> {
                    if (which == values.length) { customNoCardsTime(); return; }
                    if (which == values.length + 1) { customTimes(); return; }
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

    private void customNoCardsTime() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("分:秒，例如 05:00");
        input.setText(format(red));
        input.setInputType(android.text.InputType.TYPE_CLASS_DATETIME | android.text.InputType.TYPE_DATETIME_VARIATION_TIME);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("不举牌计时的计划时长")
                .setView(input).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int seconds = parseTime(input.getText().toString());
            if (seconds <= 0) { input.setError("请输入 00:01 至 999:59 的分:秒"); return; }
            applyNoCardsTime("普通计时", seconds);
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
        preset = name; green = g; yellow = y; red = r; cardsEnabled = true;
        preferences.edit().putString("preset", preset).putInt("green", green)
                .putInt("yellow", yellow).putInt("red", red).putBoolean("cardsEnabled", true).apply();
        updateDisplay();
    }

    private void applyNoCardsTime(String name, int seconds) {
        preset = name; red = Math.max(1, seconds); green = Math.max(1, red / 2); yellow = Math.max(green, red * 3 / 4);
        cardsEnabled = false;
        preferences.edit().putString("preset", preset).putInt("green", green)
                .putInt("yellow", yellow).putInt("red", red).putBoolean("cardsEnabled", false).apply();
        updateDisplay();
    }

    private void playBell() {
        if (bellPlayer != null) bellPlayer.release();
        bellPlayer = MediaPlayer.create(this, R.raw.bell);
        if (bellPlayer == null) return;
        bellPlayer.setOnCompletionListener(player -> {
            player.release();
            if (bellPlayer == player) bellPlayer = null;
        });
        bellPlayer.setOnErrorListener((player, what, extra) -> {
            player.release();
            if (bellPlayer == player) bellPlayer = null;
            return true;
        });
        bellPlayer.start();
    }

    private java.util.ArrayList<String> speakerNames() {
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        try {
            JSONArray saved = new JSONArray(preferences.getString("speakers", "[]"));
            for (int i = 0; i < saved.length(); i++) {
                String name = saved.optString(i).trim();
                if (!name.isEmpty() && !names.contains(name)) names.add(name);
            }
        } catch (JSONException ignored) { }
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null) continue;
            String name = record.optString("speaker").trim();
            if (!name.isEmpty() && !name.matches("演讲者 \\d+") && !names.contains(name)) names.add(name);
        }
        if (!speaker.isEmpty() && !names.contains(speaker)) names.add(speaker);
        return names;
    }

    private void selectSpeaker(String name) {
        speaker = name;
        java.util.ArrayList<String> names = speakerNames();
        preferences.edit().putString("speakers", new JSONArray(names).toString()).apply();
        speakerButton.setText(speaker.isEmpty() ? "选择演讲者（可选）" : "演讲者：" + speaker);
    }

    private void editSpeaker() {
        java.util.ArrayList<String> names = speakerNames();
        new AlertDialog.Builder(this).setTitle("选择演讲者")
                .setSingleChoiceItems(names.toArray(new String[0]), names.indexOf(speaker), (dialog, which) -> {
                    selectSpeaker(names.get(which));
                    dialog.dismiss();
                })
                .setPositiveButton("新增演讲者", (dialog, which) -> addSpeaker())
                .setNeutralButton("不指定", (dialog, which) -> selectSpeaker(""))
                .setNegativeButton("取消", null).show();
    }

    private void addSpeaker() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("演讲者姓名");
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("新增演讲者").setView(input)
                .setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) { input.setError("请输入演讲者姓名"); return; }
            selectSpeaker(name);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void finishSession(boolean advanceAgenda) {
        cancelVoiceStandby();
        long elapsed = timer.elapsed(SystemClock.elapsedRealtime());
        if (elapsed == 0) return;
        int followingAgenda = advanceAgenda ? nextAgendaIndex() : -1;
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
            record.put("status", cardsEnabled
                    ? (elapsed < green * 1000L ? "未到绿牌" : elapsed > red * 1000L ? "超过红牌时间" : "已到绿牌")
                    : (elapsed > red * 1000L ? "超出计划时间" : "普通计时"));
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
        if (followingAgenda >= 0) {
            // Re-anchor the live timeline at the next item's position in the complete agenda.
            // Wall-clock time retains any real delay; carrying raw elapsed seconds across items
            // would incorrectly discard planned gaps between agenda entries.
            applyAgendaItem(followingAgenda);
            buildScreen();
        }
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
        state.putInt("activeAgendaIndex", activeAgendaIndex);
        state.putInt("agendaActualBaseSeconds", agendaActualBaseSeconds);
        if (retainedAudio != null) state.putString("audio", retainedAudio.getName());
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        preferences.unregisterOnSharedPreferenceChangeListener(recordChanges);
        if (bellPlayer != null) { bellPlayer.release(); bellPlayer = null; }
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
