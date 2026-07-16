# AGENTS.md — AdScreenHolder

Android kiosk 广告屏轮播应用。全屏霸屏、键盘双路径输入、Material Design 3。

**当前版本**：26.7.16+1710（versionCode 2607161）

## 技术栈

- **语言**: Java 8
- **构建**: Gradle 9.6.1 + AGP 8.5.0
- **目标/编译**: targetSdk 25 / compileSdk 34
- **最低**: API 21 (Android 5.0)
- **依赖**: AppCompat 1.6.1, Material 1.10.0, CardView 1.0.0, ConstraintLayout 2.1.4

## 架构

纯 Activity 架构，无 Fragment / ViewModel。状态持久化用 `SharedPreferences`（key: `im.xrl.ad_screen_holder.prefs`）。

```
MainActivity  ←──A键──→  SettingsActivity
     ↓                        ↓
ImageCarousel            TimePicker + ImageManager + SlideshowSettings
(Handler+postDelayed)    (SharedPreferences)
     ↓                        ↓
AutoPowerService ←────── 保存时启动
(AlarmManager定时开屏/闭屏)
```

## 键盘输入双路径

这是项目最关键的架构设计——输入同时支持物理键盘和虚拟键盘：

1. **物理键盘路径**（USB/蓝牙/AVD模拟器）
   - `Activity.dispatchKeyEvent()` → `onKeyDown()` → `handleKey(int keyCode)`
   - 检查 `KeyEvent.KEYCODE_A` / `KEYCODE_Q` / `KEYCODE_S`

2. **虚拟键盘路径**（IME/软键盘）
   - `ImeInterceptorView`（自定义 View，1px 透明，继承 `View` 非 `EditText`）
   - 重写 `onCheckIsTextEditor()` → `true`
   - 重写 `onCreateInputConnection()` → 拦截 `BaseInputConnection.commitText()`
   - 回调 `OnCharListener.onChar(char)` → `handleImeChar(char)`
   - 三道防线防键盘弹出：`SOFT_INPUT_STATE_ALWAYS_HIDDEN` + `focusableInTouchMode=false` + `hideSoftInputFromWindow`

3. **兜底兼容**（`dispatchKeyEvent` 中额外检查）
   - `event.getCharacters()` — IME 通过 `sendKeyEvent` 发送的字符
   - `event.getUnicodeChar()` — KeyEvent 中嵌入的 Unicode 码点

## 沉浸模式

`enterFullScreen()` 按 API 分两路：
- **API 30+**: `WindowInsetsController.hide(statusBars|navigationBars)` + `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`
- **API 21-29**: `SYSTEM_UI_FLAG_HIDE_NAVIGATION|IMMERSIVE_STICKY|FULLSCREEN|LAYOUT_STABLE`

同时设置 `FLAG_KEEP_SCREEN_ON|SHOW_WHEN_LOCKED|DISMISS_KEYGUARD`。

## 关键常量

| 常量 | 值 | 位置 |
|------|-----|------|
| 图片目录 | `/sdcard/ads/` | `Utils.getAdsDir()` |
| 默认轮播间隔 | 10s | `SettingsActivity.DEFAULT_IMAGE_INTERVAL` |
| 轮播间隔范围 | 3–120s | `MIN_IMAGE_INTERVAL` / `MAX_IMAGE_INTERVAL` |
| 缩略图采样尺寸 | 96px | `SettingsActivity.THUMB_SIZE` |
| 缩略图缓存 | 256KB ARGB | `SettingsActivity.mThumbCache` |
| 默认开屏 | 08:00 | `SettingsActivity.DEFAULT_POWER_ON_TIME` |
| 默认闭屏 | 22:00 | `SettingsActivity.DEFAULT_POWER_OFF_TIME` |
| Prefs key | `im.xrl.ad_screen_holder.prefs` | `SettingsActivity.PREFS_NAME` |

## 图片管理

- 导入时保留原始文件名（剥离扩展名后过滤非法字符 `\/:*?"<>|`）
- 重名自动加 ` (1)` / ` (2)` 后缀（`uniquify()`）
- 新导入图片默认加入"展示"列表
- 列表项含缩略图（后台 2 线程池异步解码 + LRU 缓存）
- 每行有删除按钮，确认后删文件 + 清理 `active_images` 残留

## 自动锁屏（开屏/闭屏）

- 开屏：`AlarmReceiver` 直接用 `PowerManager.SCREEN_BRIGHT_WAKEUP | ACQUIRE_CAUSES_WAKEUP` 亮屏
- 闭屏：`DevicePolicyManager.lockNow()` 强制锁屏（需激活设备管理器）
- 保存设置时若设备管理器未激活，会弹出系统对话框引导用户开启
- `AlarmReceiver` 是 `AutoPowerService` 的静态内部类，已在 `AndroidManifest.xml` 用 `.AutoPowerService$AlarmReceiver` 注册
- `PendingIntent` 在 API 23+ 加 `FLAG_IMMUTABLE`，兼容 Android 12+

## 构建命令

```bash
export ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@25/25.0.3/libexec/openjdk.jdk/Contents/Home
./build-and-sign.sh
```

输出：`AdScreenHolder-release.apk`（已 zipalign + apksigner 签名）

## 注意事项

- 图片解码用 `RGB_565` 节省内存，旧 Bitmap 重用前先 `recycle()`
- `onBackPressed()` 空实现——屏蔽返回键
- `dispatchKeyEvent` 屏蔽 `BACK`/`HOME`/`APP_SWITCH` 三键
- `AutoPowerService` 返回 `START_STICKY`，监听 `TIME_CHANGED`/`TIMEZONE_CHANGED` 重设闹钟
- `receiver` 注册/注销在 `onResume`/`onPause` 中成对操作
- 开机自启通过 `BootReceiver` 监听 `BOOT_COMPLETED`
- 签名密钥和密码硬编码在 `build-and-sign.sh` 中，仅开发使用
