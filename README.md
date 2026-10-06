# Android Build Environment

这是一个 Android 构建环境仓库，用于存放 Android 源码和构建项目。

## Sub-projects

| 项目 | 语言 | 描述 |
|------|------|------|
| **MultiLangColorApp** | Kotlin | 多语言彩蛋应用：点击屏幕切换背景颜色（随机RGB生成），支持中文/英文/日文/韩语 |

## CI/CD

- **MultiLangColorApp** 使用 GitHub Actions 自动构建
- 推送代码到 main 分支后自动编译并发布 Release

## Quick Start

```bash
# 克隆仓库
git clone https://github.com/djzrs/Android-build-environment.git

# 进入子项目
cd MultiLangColorApp

# 构建 Debug APK
./gradlew assembleDebug
```

## 仓库结构

```
Android-build-environment/
├── README.md                    # 仓库总览
├── MultiLangColorApp/           # 多语言彩蛋应用
│   ├── README.md                # 项目说明文档
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── app/
│       ├── build.gradle.kts
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── java/com/example/multilangcolorapp/MainActivity.kt
│           └── res/
│               ├── values/ (English)
│               ├── values-zh/ (中文)
│               ├── values-ja/ (日本語)
│               ├── values-ko/ (한국어)
│               └── layout/activity_main.xml
└── docs/                        # 文档和踩坑记录
    └── MultiLangColorApp.md     # 踩坑与创意记录
```