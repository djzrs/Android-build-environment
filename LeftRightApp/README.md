# 左右音 LeftRight

一款轻量的 Android 声音编辑工具：**音频剪辑、音量调整、分段左右声道调节**。

## 功能

- **声音剪辑**：导入音频后，拖动波形上的红色手柄选择要保留的片段
- **分段左右声道调节（特色功能）**：
  - **长按波形**任意位置 = 添加分割点，把音频切成多个段落
  - **点击段落**选中它（黄色高亮边框），然后用推子单独设置该段的左右声道比例
  - 每段有独立的推子值，互不影响
  - 推子在**中间** = 立体声（左右各 100%，单声道变双声道）
  - 推子往**左** = 左声道占比大；往**右** = 右声道占比大
  - 例如：第 1 段偏左、第 2 段偏右、第 3 段立体声，均可独立设置
- **音量调整**：0% ~ 200% 音量增益，整段统一
- **试听**：播放时自动按当前所在段落应用对应声道比例
- **导出**：每段按各自推子值处理，编码为 AAC `.m4a`

## 技术要点

- Kotlin + 原生 View（无第三方音频库）
- `MediaExtractor` + `MediaCodec` 解码音频为 PCM
- 分段处理：按段落分别解码 → 各自应用 `pan` → 拼接 → 编码
- `MediaCodec` 编码 AAC + `MediaMuxer` 输出 `.m4a`
- 波形自定义 View：峰值条、选区手柄、分段色块、分割点、点击/长按手势
- 文件选择/保存：Android SAF（`GetContent` / `CreateDocument`），无需存储权限

## 声道推子算法

```
lGain = clamp(1 - pan, 0, 1)
rGain = clamp(1 + pan, 0, 1)

pan =  0  -> L 100%, R 100%  （立体声）
pan = -1  -> L 100%, R   0%  （全左）
pan = +1  -> L   0%, R 100%  （全右）
```

## 构建

```bash
./gradlew assembleDebug   # Debug APK
./gradlew assembleRelease # Release APK（已签名）
```

GitHub Actions 会在推送 `LeftRightApp/**` 时自动构建并发布 Release APK。

## 下载

- Release 页面：https://github.com/djzrs/Android-build-environment/releases
- APK 出现在 Actions 的 `leftright-debug-apk` / `leftright-release-apk` artifacts 及 Release 资产中