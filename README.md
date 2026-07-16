# 电子广告屏 (AdScreenHolder)

一款面向 Android 5.0+ 电子广告屏/信息发布场景的霸屏轮播应用。采用 Material Design 3 设计语言，全中文界面。

包名：`im.xrl.ad_screen_holder`
版本：26.7.16+1710（versionCode 2607161）

---

## 功能特性

| 功能 | 说明 |
|------|------|
| 全屏轮播 | `/sdcard/ads/` 目录下图片循环播放，带淡入淡出过渡 |
| 防退出 | 拦截触摸、Back、Home、多任务键，kiosk 霸屏 |
| 开机自启 | 监听 `BOOT_COMPLETED` 自动启动主界面与定时服务 |
| 自动开屏/闭屏 | 闹钟定时点亮/强制锁屏（默认 08:00 开屏、22:00 闭屏） |
| 双路径键盘 | 物理键盘（USB/蓝牙/模拟器）走 KeyEvent；虚拟键盘（IME/软键盘）走 InputConnection 拦截 |
| 沉浸全屏 | API 21-29 用 SYSTEM_UI_FLAG，API 30+ 用 WindowInsetsController，彻底隐藏状态栏与导航栏 |
| MD3 管理面板 | 缩略图、启用勾选、删除按钮、切换周期、原始文件名保留、设备管理器引导 |

> ⚠️ "自动开关机"概念已明确为"强制锁屏"（`lockNow()`），熄屏后物理按键仍可重新亮屏。

---

## 键盘操作

支持物理键盘和虚拟键盘两种输入方式，均映射相同功能。

| 按键 | 位置 | 功能 |
|------|------|------|
| A | 轮播界面 | 进入管理面板 |
| Q | 轮播界面 | 强制退出程序 |
| S | 管理面板 | 保存设置并返回轮播 |
| A | 管理面板 | 返回轮播（不保存）|
| Q | 管理面板 | 强制退出程序 |

### 输入路径

- **物理键盘**（USB/蓝牙/AVD 模拟器硬件键盘）→ `Activity.dispatchKeyEvent` → `onKeyDown`
- **虚拟键盘**（IME/软键盘/无障碍输入法）→ `ImeInterceptorView.onCreateInputConnection` → `commitText` 拦截
- 两种路径最终汇入同一个 handler，行为一致

> AVD 用户注意：需在模拟器设置中启用「Enable keyboard input」，并确保模拟器窗口获得 macOS 焦点。

---

## 管理面板

1. **自动锁屏时间**：点击"开屏时间 / 闭屏时间"卡片，使用系统时间选择器设置。
2. **轮播设置**：设置图片切换周期（3–120 秒，默认 10 秒）。
3. **广告图片**：
   - 点击"添加图片"从系统文件选择器导入图片
   - 导入后保留原始文件名，新图片默认勾选"展示"
   - 每行显示缩略图、文件名、"展示"勾选框、删除按钮
   - 删除会弹出确认对话框，确认后删除文件并清理启用列表
4. **保存 / 返回 / 强制退出**：底部三个操作按钮。
5. 保存时若设备管理器未激活，会弹出系统对话框引导开启（用于自动闭屏）。

---

## 安装与配置

1. 安装 APK 到设备。
2. 首次启动会提示无图片，按 **A** 进入管理面板添加图片。
3. 如需自动闭屏：
   - 在管理面板点"保存设置"时按提示激活设备管理器，或
   - 进入系统 **设置 → 安全 → 设备管理器**，激活"电子广告屏"。
4. 将本应用设为主屏幕/桌面启动器（可选，用于 kiosk 场景）。
5. 重启设备验证开机自启。

---

## 构建

```bash
export ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@25/25.0.3/libexec/openjdk.jdk/Contents/Home

# 一键签名
./build-and-sign.sh
```

> 本项目使用 JDK 25 + Gradle 9.6.1 + AGP 8.5.0。

---

## 项目结构

```
├── AdScreenHolder-release.apk
├── release.keystore
├── build-and-sign.sh              # 一键构建签名脚本
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/im/xrl/ad_screen_holder/
│   │   ├── MainActivity.java             # 全屏轮播 + 键盘拦截 + 沉浸模式
│   │   ├── SettingsActivity.java         # MD3 管理面板（图片/周期/锁屏）
│   │   ├── ImeInterceptorView.java       # IME 虚拟键盘拦截视图
│   │   ├── AutoPowerService.java         # 定时开屏/闭屏（含 AlarmReceiver）
│   │   ├── BootReceiver.java             # 开机自启
│   │   ├── DeviceAdminReceiver.java      # 设备管理器
│   │   └── Utils.java                    # 通用工具
│   └── res/{layout,values,xml,drawable}/
```

---

## 系统要求

- Android 5.0 (API 21) 及以上
- 需要存储权限以导入图片
- 需要 `WAKE_LOCK` 权限以定时亮屏
- 需要 Device Admin 权限以自动闭屏

---

## License

自由使用
