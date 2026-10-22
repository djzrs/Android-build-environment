# 一键常亮（OneTapAlwaysOn）

极简屏幕常亮控制工具。开启开关后出现可拖动悬浮球，屏幕保持常亮；关闭或移除悬浮球后恢复系统默认熄屏策略。

- 包名：`com.onetap.alwayson`
- 最低系统：Android 8.0（API 26）
- 开发语言：Kotlin

## 功能

- 主界面：标题 + 开关，开关与悬浮球服务严格绑定
- 悬浮球：约 10mm 物理直径（63dp），可拖动、松手贴边、贴边点击/向内滑动变回球
- 常亮：`FLAG_KEEP_SCREEN_ON`（App 在前台时生效）
- 前台服务承载悬浮球，带「关闭」动作通知

## 构建

```bash
./gradlew assembleDebug   # Debug APK
./gradlew assembleRelease # Release APK（已签名）
```

GitHub Actions 推送后自动构建并发布 Release。