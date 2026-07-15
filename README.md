# 电子广告屏 (AdScreenHolder)

一款面向 Android 5.0–7.x 电子广告屏/信息发布场景的霸屏轮播应用。采用 Material Design 3 设计语言，全中文界面。

包名：`im.xrl.ad_screen_holder`

---

## 功能特性

| 功能 | 说明 |
|------|------|
| 全屏轮播 | `/sdcard/ads/` 目录下图片循环播放，带淡入淡出过渡 |
| 防退出 | 拦截触摸、Back、Home、多任务键， kiosk 霸屏 |
| 开机自启 | 监听 `BOOT_COMPLETED` 自动启动主界面与定时服务 |
| 自动开关屏 | 闹钟定时亮屏/熄屏，默认 08:00 开机、22:00 熄屏 |
| 键盘管理 | USB/物理键盘 A 键进入管理，Q 键强制退出 |
| MD3 界面 | 管理面板采用 Material Design 3 卡片 + 时间选择器 |

---

## 键盘操作

| 按键 | 位置 | 功能 |
|------|------|------|
| A | 轮播界面 | 进入管理面板 |
| Q | 轮播界面 | 强制退出程序 |
| S | 管理面板 | 保存设置并返回轮播 |
| A | 管理面板 | 返回轮播（不保存） |
| Q | 管理面板 | 强制退出程序 |

---

## 管理面板

1. **自动开关机时间**：点击“开机时间 / 关机时间”卡片，使用系统时间选择器设置。
2. **广告图片**：点击“添加图片”从系统文件选择器导入图片；勾选“展示”以在轮播中显示。
3. **保存 / 返回 / 强制退出**：底部三个操作按钮。

---

## 安装与配置

1. 安装并签名 APK（见下方构建脚本）。
2. 首次启动会提示无图片，按 **A** 进入管理面板添加图片。
3. 如需霸屏锁屏熄屏：
   - 进入系统 **设置 → 安全 → 设备管理器**，激活“电子广告屏”。
4. 将本应用设为主屏幕/桌面启动器（可选，用于 kiosk 场景）。
5. 重启设备验证开机自启。

---

## 构建

```bash
export ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@25/25.0.3/libexec/openjdk.jdk/Contents/Home

# 编译 release APK
gradle assembleRelease

# 一键签名
./build-and-sign.sh
```

> 本项目使用 JDK 25 + Gradle 9.6.1 + AGP 8.5.0。

---

## 项目结构

```
├── AdScreenHolder-release.apk
├── release.keystore
├── build-and-sign.sh           # 一键构建签名脚本
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/im/xrl/ad_screen_holder/
│   │   ├── MainActivity.java          # 全屏轮播 + 键盘拦截
│   │   ├── SettingsActivity.java      # MD3 管理面板
│   │   ├── AutoPowerService.java      # 定时开关屏
│   │   ├── BootReceiver.java          # 开机自启
│   │   ├── DeviceAdminReceiver.java   # 设备管理器
│   │   └── Utils.java                 # 通用工具
│   └── res/{layout,values,xml,drawable}/
```

---

## 系统要求

- Android 5.0 (API 21) 及以上
- 需要存储权限以导入图片
- 需要 `WAKE_LOCK` 权限以定时亮屏

---


## License

自由使用
