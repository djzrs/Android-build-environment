# MultiLangColorApp 开发与踩坑记录

## 项目概述
- Kotlin Android 项目，支持 4 语言（中文/English/日本語/한국어）
- 随机 RGB 颜色生成，ViewBinding，minSdk 24，targetSdk 34
- 仓库：`https://github.com/djzrs/Android-build-environment.git`

## 踩坑记录

### 1. GitHub 网络访问失败
- **现象**: `github.com:443` 超时 135s，`api.github.com` 正常
- **诊断**:
  - `github.com` → `140.82.112-114.4`, `20.205.243.166`, `20.207.73.82`
  - `raw.githubusercontent.com` 等 → `185.199.108-111.133` (Fastly)
- **修复**: 修改 `/etc/hosts`，备份保存到 `/etc/hosts.backup.*`
- **注意**: 用户要求"不用定时检测"，保留脚本手动跑

### 2. Gradle Plugin `apply false` 错误
- **现象**: 构建失败，插件未应用到模块
- **原因**: `build.gradle.kts` 顶层错误地使用了 `apply false`
  ```kotlin
  // ❌ 错误：插件不应用到当前模块
  plugins {
      id("com.android.application") version "8.5.0" apply false
      id("org.jetbrains.kotlin.android") version "1.9.24" apply false
  }
  ```
- **修复**: 移除 `apply false`
  ```kotlin
  // ✅ 正确：插件应用到当前模块
  plugins {
      id("com.android.application") version "8.5.0"
      id("org.jetbrains.kotlin.android") version "1.9.24"
  }
  ```
- **注意**: 根项目 `build.gradle.kts` 应用 `apply false`，app 模块直接应用插件

### 3. GitHub Actions 工作流位置
- **坑**: 工作流文件必须放在仓库根目录的 `.github/workflows/`
- **不能**: 放在子文件夹（如 `MultiLangColorApp/.github/workflows/`）

### 4. gradlew 包装脚本
- 需 `chmod +x` 赋予执行权限
- Gradle 8.5 + wrapper 配置

### 5. Token 认证格式
- PAT 格式: `https://TOKEN@github.com/...`
- Fine-grained PAT 推荐，最小权限（Contents 读写）

## 工作流配置
- 触发: push 到 main，路径 `MultiLangColorApp/**`
- JDK 17 (Temurin) + Android SDK
- 构建 debug/release APK，上传 artifacts，创建 Release

## 验证命令
```bash
# 本地构建
cd MultiLangColorApp && ./gradlew assembleDebug --no-daemon

# 推送
git add -A && git commit -m "message" && git push origin main
```
