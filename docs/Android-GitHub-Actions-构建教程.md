# 零本地环境：用 GitHub Actions 编译 Android APK 完整教程

> 作者：djzrs 实验室 · 2026-10
> 适用读者：想在**没有 Android Studio、没有 JDK、没有 Android SDK** 的机器上，仅靠写代码 + 推送 GitHub 就拿到 APK 的开发者。
> 本文所有内容均来自真实项目实践，仓库源码可参考：
> **https://github.com/djzrs/Android-build-environment**

---

## 目录

1. [总体思路](#1-总体思路)
2. [仓库结构（两个项目实战）](#2-仓库结构两个项目实战)
3. [Android 项目构建配置（正确版）](#3-android-项目构建配置正确版)
4. [GitHub Actions workflow 配置](#4-github-actions-workflow-配置)
5. [推送并触发构建](#5-推送并触发构建)
6. [下载 APK](#6-下载-apk)
7. [完整踩坑记录（必看）](#7-完整踩坑记录必看)
8. [GitHub 网络不通时的备选方案](#8-github-网络不通时的备选方案)
9. [参考仓库与链接](#9-参考仓库与链接)

---

## 1. 总体思路

```
本地写代码（纯文本） ── git push ──▶ GitHub 仓库 ──触发──▶ GitHub Actions（免费 runner）
                                                              │
                                                              ▼
                                       自动下载 Gradle/依赖 → 编译 Debug/Release APK
                                                              │
                                             上传 artifacts + 创建 GitHub Release
                                                              │
                                                              ▼
                                                    用户网页端直接下载 APK
```

**核心原则：本地只负责"写代码"和"版本管理"，一切编译环境问题全部交给 GitHub Actions。**

- 本地不需要安装：JDK、Android SDK、Gradle、Android Studio
- 本地只需要：`git` + 任意文本编辑器 + `curl`（可选，用于网络诊断）

---

## 2. 仓库结构（两个项目实战）

本教程来自仓库 `djzrs/Android-build-environment` 中两个真实项目：

| 项目 | 说明 | 结果 |
|------|------|------|
| `MultiLangColorApp/` | 多语言颜色应用（4 语言） | 修复构建问题直到成功，Release v14 |
| `ClockApp/` | 极简时钟（12 种样式） | 从零新建，一次构建成功，Release clock-v26 |

推荐仓库结构（**一个仓库放多个独立项目，每个项目是独立的 Gradle 工程**）：

```
Android-build-environment/
├── README.md                        # 仓库总览（已更新）
├── docs/
│   └── Android-GitHub-Actions-构建教程.md   # 本文档
├── MultiLangColorApp/               # 项目一：独立 Gradle 工程
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradle/wrapper/gradle-wrapper.jar
│   ├── gradle/wrapper/gradle-wrapper.properties
│   └── app/
└── ClockApp/                        # 项目二：独立 Gradle 工程（同结构）
    ├── build.gradle.kts
    ├── settings.gradle.kts
    ├── gradle.properties
    ├── gradlew
    ├── gradle/wrapper/...
    └── app/
        ├── build.gradle.kts
        └── src/main/
            ├── AndroidManifest.xml
            ├── java/com/djzrs/clockapp/    # Kotlin 源码
            └── res/                       # 资源（layout/values/mipmap）
```

> 每个子项目是**完整独立**的 Gradle 工程，拥有自己的 `gradlew` 和 wrapper。这样 workflow 里用 `working-directory` 分别构建即可。

---

## 3. Android 项目构建配置（正确版）

> 以下配置是经过真实踩坑后验证的**最终正确版本**，请直接照抄。

### 3.1 根项目 `build.gradle.kts`（每个子项目根目录）

```kotlin
// 根项目只声明插件版本，必须 apply false！
plugins {
    id("com.android.application") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
```

**⚠️ 这是本项目最大的坑之一**：根项目 `apply false` 表示"只声明版本，不应用插件"；真正的插件在 `app/` 模块里应用。早期文档中曾误删 `apply false` 导致构建失败，切记根项目必须 `apply false`。

### 3.2 `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "ClockApp"   // 改成你的项目名
include(":app")
```

### 3.3 `gradle.properties`（必须，否则 AndroidX 报错）

```
android.useAndroidX=true
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
```

**坑**：项目使用 AndroidX 但没开启时，Gradle 会直接报 `This project uses AndroidX dependencies, but android.useAndroidX is not enabled`。

### 3.4 Gradle Wrapper（关键！）

- `gradlew`（官方脚本，无自定义逻辑）
- `gradle/wrapper/gradle-wrapper.jar`（官方 jar，约 43KB）
- `gradle/wrapper/gradle-wrapper.properties`：

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.7-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

**坑**：Gradle 版本不能低于 AGP 8.5.0 要求的 **8.7**。用 8.5 会报错：
`Minimum supported Gradle version is 8.7. Current version is 8.5`。

**坑**：不要用"优先调用系统 gradle"的自定义 `gradlew` 脚本！GitHub runner 预装的是 **Gradle 9.8**，与 AGP 8.5 不兼容。必须用标准 wrapper（`./gradlew` 会自动下载 `gradle-8.7-bin.zip`）。

### 3.5 `app/build.gradle.kts`

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.djzrs.clockapp"   // 包名
    compileSdk = 34

    defaultConfig {
        applicationId = "com.djzrs.clockapp"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
```

### 3.6 `AndroidManifest.xml` 要点

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.ClockApp">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|keyboardHidden|uiMode"
            android:screenOrientation="fullSensor">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

**坑**：`namespace` 已在 `build.gradle.kts` 里设置，**不要再写 `package="..."`**（Android Gradle Plugin 8 已弃用该属性，写了会报错或告警）。

**坑**：`@mipmap/ic_launcher` 引用的 PNG 必须是**有效的标准图标**。如果 PNG 是损坏/异常的占位文件，Release 构建时 AAPT2 会失败（详见踩坑 7.5）。

---

## 4. GitHub Actions workflow 配置

每个子项目一个 workflow 文件，放在仓库根目录 `.github/workflows/` 下。

> ⚠️ 必须是**仓库根目录**的 `.github/workflows/`，不能放在子项目里（这是踩过的坑）。

### 4.1 通用模板（单项目推荐）

新建 `.github/workflows/android-build.yml`：

```yaml
name: Android Build & Release

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: write          # 必须有！否则 GITHUB_TOKEN 无权创建 Release

    steps:
    - name: Checkout repository
      uses: actions/checkout@v5

    - name: Set up JDK 17
      uses: actions/setup-java@v5
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: gradle

    - name: Make gradlew executable
      run: chmod +x gradlew

    - name: Build Debug APK
      run: ./gradlew assembleDebug --no-daemon

    - name: Build Release APK
      run: ./gradlew assembleRelease --no-daemon

    - name: Upload Debug APK
      uses: actions/upload-artifact@v4
      with:
        name: debug-apk
        path: app/build/outputs/apk/debug/*.apk
        retention-days: 7

    - name: Upload Release APK
      uses: actions/upload-artifact@v4
      if: github.event_name == 'push' && github.ref == 'refs/heads/main'
      with:
        name: release-apk
        path: app/build/outputs/apk/release/*.apk
        retention-days: 30

    - name: Create Release
      if: github.event_name == 'push' && github.ref == 'refs/heads/main'
      uses: softprops/action-gh-release@v2
      with:
        tag_name: v${{ github.run_number }}
        name: Build ${{ github.run_number }}
        files: app/build/outputs/apk/release/*.apk
        draft: false
        prerelease: false
      env:
        GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

### 4.2 多项目模板（本仓库实际使用）

一个仓库多个项目时，用 `paths` 过滤 + `working-directory` 指定项目：

```yaml
on:
  push:
    branches: [ main ]
    paths:
      - 'ClockApp/**'          # 只有 ClockApp 变化才触发
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: write
    steps:
      # ... checkout / setup-java 同上 ...
    - name: Make gradlew executable
      run: chmod +x ClockApp/gradlew

    - name: Build Debug APK
      run: ./gradlew assembleDebug --no-daemon
      working-directory: ClockApp   # 关键！

    # ... 其余步骤 path 改为 ClockApp/app/build/outputs/... 即可
```

> 本仓库实际有 `android-build.yml`（MultiLangColorApp）和 `clock-app-build.yml`（ClockApp）两个 workflow，分别监听各自项目目录。

### 4.3 Actions 版本选择（重要）

| Action | 版本 | 原因 |
|--------|------|------|
| `actions/checkout` | **v5** | v4 已弃用 |
| `actions/setup-java` | **v5** | v4 已弃用 |
| `actions/upload-artifact` | **v4** | 当前版本 |
| `softprops/action-gh-release` | **v2** | Release 发布 |
| ~~`android-actions/setup-android@v3`~~ | **不要用！** | Node 20 弃用后被强制跑在 Node 24 上直接失败；runner 已预装 Android SDK，根本不需要 |

---

## 5. 推送并触发构建

```bash
git add -A
git commit -m "feat: add ClockApp"
git push origin main
```

推送后到仓库 **Actions** 页查看构建：

```
https://github.com/<你的用户名>/<仓库名>/actions
```

构建日志排查方法：如果失败，进入 run → job → 步骤，看具体错误。日志也可用 API 下载：

```bash
curl -sL -H "Authorization: Bearer $GITHUB_TOKEN" \
  "https://api.github.com/repos/{owner}/{repo}/actions/runs/{run_id}/logs"
```

---

## 6. 下载 APK

### 方式 A：GitHub Release（推荐，长期有效）

构建成功且 `permissions: contents: write` 时，`softprops/action-gh-release` 会自动创建 Release 并上传 APK：

```
https://github.com/<用户名>/<仓库名>/releases
```

本仓库实际 Release：
- `https://github.com/djzrs/Android-build-environment/releases/tag/v14`（MultiLangColorApp）
- `https://github.com/djzrs/Android-build-environment/releases/tag/clock-v26`（ClockApp）

### 方式 B：Actions Artifacts（短期，7 天）

run 页面底部 Artifacts 区域下载 `debug-apk` / `release-apk`。

> Debug APK 使用 debug 签名，**可以直接安装**；Release APK 默认 unsigned，需手动签名或用 debug 包。

---

## 7. 完整踩坑记录（必看）

以下按"构建类"和"网络类"分类，全部是真实踩过并已解决的坑。

### 7.1 构建类

#### 坑 1：根项目插件必须 `apply false`

- **现象**：构建失败，报插件未应用或重复应用错误。
- **原因**：多模块工程中，根项目 `build.gradle.kts` 只是"声明插件版本"的仓库，不应把插件应用到自身。
- **正确写法**：
  ```kotlin
  plugins {
      id("com.android.application") version "8.5.0" apply false
      id("org.jetbrains.kotlin.android") version "1.9.24" apply false
  }
  ```
- **app 模块**（`app/build.gradle.kts`）才是真正应用插件的地方：
  ```kotlin
  plugins {
      id("com.android.application")
      id("org.jetbrains.kotlin.android")
  }
  ```

#### 坑 2：未开启 AndroidX

- **现象**：`This project uses AndroidX dependencies, but android.useAndroidX is not enabled`
- **修复**：根目录新建 `gradle.properties`，写入：
  ```
  android.useAndroidX=true
  org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
  ```

#### 坑 3：自定义 gradlew 引用系统 Gradle

- **现象**：`./gradlew` 实际调用系统 `gradle`（runner 预装 **9.8**），与 AGP 8.5 不兼容，报错如 `Could not run phased build action using connection to Gradle distribution`。
- **原因**：某些"加速"教程让 gradlew 脚本优先找系统 gradle，这在本地也许方便，但 GitHub runner 的 Gradle 版本和项目不匹配。
- **修复**：使用**官方标准 wrapper**（gradlew 官方脚本 + `gradle-wrapper.jar`），让 `./gradlew` 自动下载 `gradle-8.7-bin.zip`。

#### 坑 4：Gradle 版本过低

- **现象**：`Minimum supported Gradle version is 8.7. Current version is 8.5`（或类似）
- **原因**：AGP 8.5.0 要求 Gradle >= 8.7。
- **修复**：`gradle-wrapper.properties` 里 `distributionUrl` 改为 `gradle-8.7-bin.zip`。

#### 坑 5：launcher 图标 PNG 损坏

- **现象**：Release 构建 AAPT2 报 `Unexpected error during compile`（Debug 有时却能过，因为 Release 启用资源收缩/校验更严）。
- **原因**：图标 PNG 是 122 字节的异常占位文件，AAPT2 无法解析。
- **修复**：用 Python PIL 生成标准 48/72/96/144/192px RGBA PNG，替换 `mipmap-*` 下的文件：
  ```python
  from PIL import Image, ImageDraw
  def create_icon(size):
      img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
      draw = ImageDraw.Draw(img)
      draw.rounded_rectangle([size*0.05]*2 + [size*0.95]*2, radius=size*0.18, fill=(10,10,18,255))
      # ... 画表盘/指针 ...
      return img
  for folder, size in {'mipmap-mdpi':48,'mipmap-hdpi':72,'mipmap-xhdpi':96,
                       'mipmap-xxhdpi':144,'mipmap-xxxhdpi':192}.items():
      create_icon(size).save(f'res/{folder}/ic_launcher.png')
  ```

#### 坑 6：strings.xml 多占位符警告/错误

- **现象**：`Multiple substitutions specified in non-positional format; did you mean to add formatted="false"?`
- **修复**：含 `%s`/`%d` 的字符串加 `formatted="false"`：
  ```xml
  <string name="color_detail" formatted="false">当前颜色: R:%d G:%d B:%d</string>
  ```

#### 坑 7：Manifest 写废弃的 `package` 属性

- **现象**：警告 `package="..." is deprecated`，或与 namespace 冲突。
- **修复**：`namespace` 已在 `build.gradle.kts` 设置，Manifest 里删除 `package="..."`。

#### 坑 8：弃用的 Actions（Node 20 强制升级）

- **现象**：`android-actions/setup-android@v3` 报 `Node.js 20 actions are disabled` 或运行直接失败。
- **原因**：GitHub 弃用 Node 20，旧 action 被强制跑在 Node 24 上不兼容。
- **修复**：
  - 移除 `setup-android`（runner 预装 Android SDK，不需要）
  - `actions/checkout` 升级 `@v5`
  - `actions/setup-java` 升级 `@v5`

#### 坑 9：GITHUB_TOKEN 无权创建 Release

- **现象**：`Create Release` 步骤 403 `Resource not accessible by integration`。
- **原因**：仓库默认 `GITHUB_TOKEN` 权限是 read-only，无权创建 Release。
- **修复**：在 workflow job 级添加：
  ```yaml
  permissions:
    contents: write
  ```

### 7.2 网络类（国内网络访问 GitHub 的坑）

#### 坑 10：github.com 直连全灭，但 api.github.com 正常

- **现象**：`curl https://github.com/` 超时（000），`git push` 报 `Operation too slow` 或 `TLS connection non-properly terminated`；但 `api.github.com` 稳定 200。
- **诊断**：
  ```bash
  curl -s -o /dev/null -w "%{http_code}\n" --max-time 10 https://api.github.com/   # 200
  curl -s -o /dev/null -w "%{http_code}\n" --max-time 15 https://github.com/       # 000
  ```
- **原因**：国内网络对 `github.com` 的 HTTPS 做了间歇性干扰，不同 IP 相位不同。
- **修复**：见本文第 8 节备选方案。

#### 坑 11：Contents API 上传文件（github.com 不通时的推送替代）

详见第 8 节。

#### 坑 12：git fetch 被 TLS 掐断 → 用镜像 fetch

- **现象**：`git fetch origin` 报 `GnuTLS recv error (-110): The TLS connection was non-properly terminated`。
- **修复**（用加速镜像拉取）：
  ```bash
  git fetch "https://ghfast.top/https://github.com/djzrs/Android-build-environment.git" main
  git update-ref refs/remotes/origin/main FETCH_HEAD
  git reset --hard origin/main
  ```

#### 坑 13：大文件下载不稳定

- **现象**：沙箱下载 APK / artifact 超过 10 分钟被截断，只下了几百 KB。
- **结论**：不要死磕本地下载，给用户 GitHub 公开链接即可。

---

## 8. GitHub 网络不通时的备选方案

当 `github.com` 直连不通、但 `api.github.com` 可用时（很常见），可以用 **GitHub Contents API** 代替 `git push`，同样能触发 Actions 构建。

### 8.1 原理

GitHub 网页端/API 的"上传文件"本质也是创建 commit，**同样会触发 push 事件**。所以：

```
PUT https://api.github.com/repos/{owner}/{repo}/contents/{path}
```

每调用一次 = 创建一个 commit（会触发一次 Actions）。

### 8.2 Python 脚本示例（本仓库真实使用）

```python
import os, base64, json, subprocess, urllib.request, urllib.parse

# 从 git remote 提取 PAT（远程 URL 形如 https://<token>@github.com/...）
remote = subprocess.check_output(['git','remote','get-url','origin']).decode().strip()
token = remote.split('@')[0].split('//')[1]
api = "https://api.github.com/repos/{owner}/{repo}/contents/"

for root, dirs, files in os.walk("你的项目目录"):
    for name in files:
        path = os.path.relpath(os.path.join(root, name), ".").replace(os.sep, "/")
        with open(path, "rb") as f:
            content = base64.b64encode(f.read()).decode()
        body = json.dumps({
            "message": f"Add {path}",
            "content": content,
            "branch": "main"
        }).encode()
        req = urllib.request.Request(api + urllib.parse.quote(path, safe=""),
                                     data=body, method="PUT",
                                     headers={"Authorization": f"Bearer {token}",
                                              "User-Agent": "builder"})
        resp = urllib.request.urlopen(req, timeout=60)
        print(resp.status, path)
```

### 8.3 注意事项

- **每个文件一个 commit**：27 个文件会产生 27 个 commit，**每个 commit 都触发一次 Actions**。
- **中间态会失败**：前 26 次构建因为代码不完整会失败，**这是正常的**，最后完整代码的 Run 成功即可。
- **查看最终结果**：以 `HEAD` commit（最后一个文件）对应的 Run 为准。

### 8.4 本地仓库同步（镜像 fetch）

Contents API 上传后，本地 git 与远程分叉。用镜像 fetch 同步：

```bash
git fetch "https://ghfast.top/https://github.com/djzrs/Android-build-environment.git" main
git update-ref refs/remotes/origin/main FETCH_HEAD
git reset --hard origin/main
```

---

## 9. 参考仓库与链接

### 主仓库

- **https://github.com/djzrs/Android-build-environment**

### 子项目源码

| 项目 | 源码目录 | 说明 |
|------|---------|------|
| MultiLangColorApp | `MultiLangColorApp/` | 多语言颜色应用 |
| ClockApp | `ClockApp/` | 极简时钟（12 种样式，横竖屏/防烧屏/日夜模式/环境光亮度） |

### Workflow 文件

- `MultiLangColorApp` → `.github/workflows/android-build.yml`
- `ClockApp` → `.github/workflows/clock-app-build.yml`

### 已发布 Release

- MultiLangColorApp：https://github.com/djzrs/Android-build-environment/releases/tag/v14
- ClockApp：https://github.com/djzrs/Android-build-environment/releases/tag/clock-v26

### 相关官方文档

- [Android 构建配置](https://developer.android.com/build)
- [GitHub Actions 文档](https://docs.github.com/zh/actions)
- [Gradle Wrapper 文档](https://docs.gradle.org/current/userguide/gradle_wrapper.html)

---

> 有任何问题欢迎在仓库提 Issue / PR。