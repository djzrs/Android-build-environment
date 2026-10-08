# Android Build Environment

这是一个 Android 构建环境仓库，用于存放 Android 源码和构建项目。
**本机不需要 Android Studio / JDK / SDK，编译完全由 GitHub Actions 完成。**

## 📘 教程

👉 **[零本地环境：用 GitHub Actions 编译 Android APK 完整教程](./docs/Android-GitHub-Actions-构建教程.md)**

包含：
- Android 项目构建配置（正确版，含所有坑）
- GitHub Actions workflow 配置（单项目 + 多项目模板）
- 完整踩坑记录（构建类 9 个 + 网络类 4 个）
- GitHub 网络不通时的备选方案（Contents API 上传触发构建）

## Sub-projects

| 项目 | 语言 | 描述 |
|------|------|------|
| **MultiLangColorApp** | Kotlin | 多语言彩蛋应用：点击屏幕切换背景颜色（随机RGB生成），支持中文/英文/日文/韩语 |
| **ClockApp** | Kotlin | 极简时钟：12 种时钟样式（翻页/数码管/霓虹/文字词钟等），支持横竖屏、防烧屏、日夜模式、环境光自动亮度 |

## CI/CD

- 每个子项目使用独立的 GitHub Actions workflow：
  - `MultiLangColorApp` → `.github/workflows/android-build.yml`
  - `ClockApp` → `.github/workflows/clock-app-build.yml`
- 推送代码到 main 分支后自动编译并发布 Release

## Quick Start

```bash
# 克隆仓库
git clone https://github.com/djzrs/Android-build-environment.git

# 进入子项目
cd ClockApp

# 构建 Debug APK
./gradlew assembleDebug
```

## 已发布 Release

- MultiLangColorApp: https://github.com/djzrs/Android-build-environment/releases/tag/v14
- ClockApp: https://github.com/djzrs/Android-build-environment/releases/tag/clock-v26

## 仓库结构

```
Android-build-environment/
├── README.md                               # 仓库总览
├── docs/
│   ├── Android-GitHub-Actions-构建教程.md   # 完整教程（推荐先读）
│   └── MultiLangColorApp.md               # 早期开发记录
├── MultiLangColorApp/                      # 多语言彩蛋应用
│   ├── README.md
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradle/wrapper/
│   └── app/
│       ├── build.gradle.kts
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── java/com/example/multilangcolorapp/
│           └── res/
└── ClockApp/                               # 极简时钟
    ├── README.md
    ├── build.gradle.kts
    ├── settings.gradle.kts
    ├── gradle.properties
    ├── gradlew
    ├── gradle/wrapper/
    └── app/
        ├── build.gradle.kts
        └── src/main/
            ├── AndroidManifest.xml
            ├── java/com/djzrs/clockapp/
            └── res/
```