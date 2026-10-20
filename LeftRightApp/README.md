# 左右音 LeftRight

一款轻量的 Android 声音编辑工具：**音频剪辑、音量调整、左右声道调节**。

## 功能

- **声音剪辑**：导入音频后，拖动波形上的红色手柄选择要保留的片段
- **音量调整**：0% ~ 200% 音量增益
- **立体声推子（特色功能）**：
  - 推子在**中间** = 立体声（左右声道各 100%）
  - 推子往**左** = 左声道占比大，右声道被衰减
  - 推子往**右** = 右声道占比大，左声道被衰减
  - 单声道音频经过推子后输出为双声道立体声
- **试听**：播放时推子/音量实时生效
- **导出**：处理结果编码为 AAC `.m4a` 文件

## 技术要点

- Kotlin + 原生 View（无第三方音频库）
- `MediaExtractor` + `MediaCodec` 解码音频为 PCM
- PCM 层实现：裁剪区间、音量增益、声道推子（线性 Pan）
- `MediaCodec` 编码 AAC + `MediaMuxer` 输出 `.m4a`
- 波形显示：自定义 View 峰值条 + 可拖动手柄选区
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