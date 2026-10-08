# 极简时钟 ClockApp

一个功能丰富的 Android 桌面时钟应用，支持 12 种时钟样式与多种实用功能。

## 功能特性

- **12 种时钟样式**，点击屏幕打开设置面板即可切换：
  1. 翻页（Fliqlo 风格）
  2. 极简数字
  3. 模拟表盘
  4. 霓虹灯
  5. 七段数码管（LED）
  6. 文字词钟（QLOCKTWO 风格）
  7. 计算器 LCD
  8. 8-bit 像素
  9. 科幻 HUD
  10. 二进制
  11. 圆环
  12. 渐变

- **横屏 / 竖屏支持**：自动旋转，也可在设置中锁定横屏或竖屏
- **防烧屏**：默认开启，每隔 60 秒微移表盘位置，避免 OLED 烧屏
- **白天 / 夜间模式**：跟随系统或手动切换
- **环境光自动亮度**：可开关，根据光线传感器自动调节屏幕亮度
- **屏幕常亮**：作为时钟使用时可保持屏幕常亮

## 技术要点

- Kotlin + 自定义 View 绘制全部时钟样式（无图片资源依赖）
- AGP 8.5.0 / Kotlin 1.9.24 / Gradle 8.7 / compileSdk 34 / minSdk 23
- 设置持久化使用 SharedPreferences
- 环境光传感器 `TYPE_LIGHT` 调节 `WindowManager.LayoutParams.screenBrightness`

## 构建

```bash
./gradlew assembleDebug   # Debug APK
./gradlew assembleRelease # Release APK（未签名）
```

GitHub Actions 会在推送 `ClockApp/**` 时自动构建并发布 Release APK。

## 下载

构建成功后 APK 会出现在：
- Actions 的 `clock-debug-apk` / `clock-release-apk` artifacts（7/30 天有效）
- Release 页面：https://github.com/djzrs/Android-build-environment/releases