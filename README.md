# 头马时间官 · TimeStopWatch

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

<img src="design/app-icon.svg" width="108" alt="头马时间官图标" />

面向头马俱乐部时间官的 Android 计时、自动举牌、演讲录音及离线中文转写工具。


Android 计时与举牌工具。应用代码使用 Java，界面使用 Android 原生 View；现有 `.gradle.kts` 文件仅用于 Gradle 构建配置。

## 使用

1. 选择演讲环节，或设置绿、黄、红牌时间（分:秒）；可填写演讲者。计时中、暂停时或全屏举牌时均可添加、修改演讲者，计时不中断，结束记录使用最新姓名。
2. 开始计时，到达阈值自动切换颜色并震动。计时期间屏幕常亮。总时间按红牌时间显示，进度条随计时推进；到达红牌后进度填满，并显示超时时间。
3. 可暂停、继续，或进入全屏举牌。绿、黄、红牌始终按设定时间自动切换，无需手动举牌。红牌后累计计时满 30 秒自动显示铃铛，并震动 300 毫秒、间隔 200 毫秒、再震动 300 毫秒；红色背景和计时继续保持。暂停期间不会累计这 30 秒。震动开关控制举牌和按铃提醒，关闭震动仍会显示铃铛。每轮计时只提醒一次，旋转屏幕或暂停续计不会重复按铃。
4. 红牌后继续计时，点击“结束并记录”保存本机记录并准备下一位演讲者。

默认时间：即兴演讲 1:00 / 1:30 / 2:00，备稿演讲 5分钟档 3:00 / 4:00 / 5:00、7分钟档 5:00 / 6:00 / 7:00、10分钟档 8:00 / 9:00 / 10:00，演讲评估 2:00 / 2:30 / 3:00。请按本次会议要求修改。记录中的状态只描述举牌时间，不判定比赛资格或额外宽限期。

计时采用 `SystemClock.elapsedRealtime()`，旋转屏幕与暂时切到后台不会产生累计漂移。后台期间不主动触发震动；回到页面会按当前时间更新举牌。记录和预设保存在本机，卸载应用会删除数据。强行停止应用或重启设备后不恢复未完成的计时。

## 构建

建议使用完整的 JDK 21（需包含 `jlink`）及 Android SDK 36.1。首次构建需要联网下载 Gradle 和 Maven 依赖；中文离线模型已包含在源码的 assets 中。

```sh
git clone https://github.com/MGHTprog/TimeStopWatch.git
cd TimeStopWatch
```

配置 `ANDROID_HOME`，或在本机 `local.properties` 中设置 `sdk.dir`。然后构建：

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。

## IDEA 真机运行

项目内已保存 `.run/TimeStopWatch-device.run.xml`，运行配置名称为 **TimeStopWatch 真机**。

1. 手机打开 USB 调试，USB 连接电脑，在手机上允许调试。
2. IDEA 使用完整 JDK 21 作为 Gradle JVM，等待项目同步结束。
3. 选择 **TimeStopWatch 真机** 配置及已连接的手机，点击运行。配置会构建、安装 APK，并启动 Manifest 中的默认 Launcher Activity。

## 真机测试

Java 仪器测试 `TimerDeviceTest` 覆盖自定义时间、绿黄红自动切换、暂停续计、全屏、红牌30秒后自动显示铃铛及恢复页面后保持提醒状态、Activity 重建、记录保存及不足一秒时重置。测试将添加名为“真机验证”的记录，并在完成后恢复即兴演讲预设。

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mght.timestopwatch.TimerDeviceTest
```

如果 Gradle 的测试宿主依赖下载失败，可直接用 Android 测试运行器执行相同测试：

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class com.mght.timestopwatch.TimerDeviceTest com.mght.timestopwatch.test/androidx.test.runner.AndroidJUnitRunner
```

## 语音待命

点击“语音待命”，首次使用允许麦克风权限。保持短暂安静，待页面显示“语音待命中，请开始讲话”后，持续讲话声会自动开始计时，并以“一次短震（100毫秒）＋间隔150毫秒＋一次普通震动（350毫秒）”确认开始（受震动提醒开关控制）；后续绿黄红牌及红牌后30秒按铃均自动执行。可点击“取消语音待命”，或直接手动开始。已有计时须先重置或结束，才能再次语音待命。

待命阶段声音仅在本机内存中短暂缓存；开启录音时，检测到讲话后保存演讲音频，并保留触发前约半秒以避免遗漏开头。音频不会上传。此功能按声音能量、过零率及持续时间检测讲话声，约200毫秒持续有效声音即可触发，不识别说话内容或区分演讲者；持续噪声、其他人讲话或音乐可能误触发。建议靠近演讲者，在嘈杂场合使用手动开始。离开应用或旋转重建页面会取消待命并释放麦克风；已开始的计时仍正常保留。

## 演讲录音与结束后文字稿

“录音并在结束后生成中文文字稿（仅本机）”默认开启，可在新一轮计时前关闭。手动开始或语音自动开始均会录音。暂停计时会暂停写入音频，继续计时后恢复；旋转屏幕不打断正在进行的录音，临时切到后台时通过前台录音服务继续采集。重置会删除当前轮录音，结束并记录会保存 WAV 并在后台生成中文文字稿。请在演讲结束时按“结束并记录”，强行停止或划掉应用不保证保存未结束的这一轮。

记录中可以回放录音、查看文字稿、复制全文；转写失败或应用被中断后可重试，原录音保留。无清晰讲话时会提示未识别到清晰中文，不生成虚构文字。文字稿为识别初稿，专有名词、口音、音乐、噪声与中英混说可能导致错误，请对照录音校对。

采用 Vosk 中文小型离线模型，模型随 APK 打包，首次转写会解压到本机。无需 API 密钥或网络连接；当前仅针对中文，不提供英文模型。模型和组件来源及许可证见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。录音与文字稿保存在应用私有目录，不进入云备份；卸载或清空记录会删除对应文件。APK 约86MB（含多架构识别库与约42MB的压缩模型）。

`RecordingTranscriptionDeviceTest` 验证 WAV 录音暂停、屏幕重建后续录、结束后自动转写、已知中文合成语音的实际识别、查看与复制文字稿。中文测试音频由本机系统语音合成到文件，不通过扬声器播放。

## 应用图标

三色举牌秒表图标使用自有矢量设计。Android 8+ 使用自适应图标，Android 13+ 提供独立单色主题图层，旧版使用 mdpi 至 xxxhdpi 的普通与圆形 PNG。原稿为 `design/app-icon.svg`，运行 `python3 tools/generate_launcher_icons.py` 可重新生成全部图标（需要桌面 JDK）。预览见 [图标预览](docs/images/icon-preview.png)。

## 开源与许可证

本项目原创代码、图标及工具以 [MIT License](LICENSE) 开源，版权归 MGHTprog。欢迎提交 Issue 和 Pull Request。

Vosk、中文离线模型、JNA、AndroidX、Material Components 和 Gradle Wrapper 保留各自的许可证，不因本项目使用 MIT 而改变。第三方来源和授权见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。MIT 和 Apache 2.0 文本也随 APK 打包。

项目仓库不包含真实演讲录音、用户文字稿、本机验证日志、设备截图或签名密钥。`app/src/androidTest/assets/mandarin.wav` 是用于测试的中文合成语音，不是真实演讲者的录音。

可安装 APK 将放在 [GitHub Releases](https://github.com/MGHTprog/TimeStopWatch/releases)。当前 APK 为调试签名版本，可用于安装体验；自行正式分发时应配置并妥善保存自己的发布签名。
