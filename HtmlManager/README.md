# HTML 管家

在手机上管理本地 HTML 文件：写一段 HTML，用外部浏览器看效果，或者开一个局域网小服务器，
让同一 Wi-Fi 下的电脑、平板扫码就能看。

<p align="center">
  <img src="docs/icon.svg" width="96" alt="HTML 管家图标">
</p>

## 它解决什么问题

手机上看自己写的 HTML 很别扭：文件管理器不会渲染，浏览器不认本地私有目录，
拷到公共目录又要存储权限。这个应用把 HTML 收在自己的私有目录里，
浏览交给系统浏览器，跨设备看交给局域网 HTTP——绕开权限，也不碰 WebView。

## 能力

- **文件列表**：按修改时间倒序，`onResume` 自动刷新；顶栏搜索按文件名实时过滤
- **新建 / 编辑**：文件名自动补 `.html`，非法字符替 `_`，重名拦截
- **编辑页**：等宽代码框，全选 / 清空（可撤销） / 复制 / 粘贴 / 查找；顶栏左上角返回，
  有未保存改动才弹三选项，保存过就直接走
- **预览**：`FileProvider` + `ACTION_VIEW` 交给系统浏览器，不做 WebView 兜底
- **局域网服务器**：NanoHTTPD 监听 8080，`/` 是自动生成的导航页，`/files/{name}` 是单文件；
  页面展示局域网地址与二维码。跑在前台服务里，切后台、退出界面都不断，通知栏可停止
- **后台悬浮窗**：授权后，服务器运行中切到后台自动挂一张小卡片，显示地址，点击回 APP；
  拖到边上松手收成小球，只留一个绿点，点一下再展开

## 装

每次 push 都出包，从 Release 拿最新的 APK 直装：

| 想要 | 去哪拿 |
| --- | --- |
| 最新一次提交的包 | [Release `Nightly`](https://cnb.cool/djzrs/AndriodCloudBuild/-/releases/tag/Nightly) |
| 某个正式版本 | 对应 tag 的 Release，如 [`v1.2.0`](https://cnb.cool/djzrs/AndriodCloudBuild/-/releases/tag/v1.2.0) |

文件名后缀说明签名：`-release` 是正式密钥签的，能上架；`-debug` 是构建机临时密钥签的，
只能自己装。配好密钥仓库后所有包都会是 `-release`，见[签名与版本号](docs/签名与版本号.md)。

最低 Android 8.0（API 26）。首次启动会申请通知权限（前台服务通知用），
悬浮窗需要单独授予「显示在其他应用上层」，在服务器页可以一键跳转授权。

## 本地构建

需要 JDK 17 或更高（CI 用 21 编译，jvmTarget 是 17）与 Android SDK 34（platform + build-tools）。

```bash
cd html-manager
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

./gradlew -q printVersion    # 看当前 versionCode / versionName / 签名方式
```

版本号跟着 git 提交数自动走，不用手改。

想指定 SDK 位置，在根目录放一个 `local.properties`：`sdk.dir=/path/to/android-sdk`。
这个文件不进提交。

## CI

根目录 [`.cnb.yml`](../.cnb.yml) 定义了三条触发：

- `main` push → 编译 → 覆盖发布到 `Nightly` Release
- `v*` tag push → 编译 → 发布到同名 Release，标为 latest
- 其他分支 push / PR → 只编译，验证能不能编过

构建环境是 [`.ci/android.Dockerfile`](.ci/android.Dockerfile)（OpenJDK 21 + SDK 34），
镜像按内容哈希缓存，源码改动不会触发镜像重建。

版本号与签名都自动：`versionCode` 跟着提交数走，签名密钥从密钥仓库注入。

## 结构

仓库是多项目共用的，本项目全部内容在 `html-manager/` 下：

```
html-manager/
├── .ci/                       构建镜像、编译脚本、签名工具
├── .cnb.yml -> 见根目录        流水线配置在仓库根，按项目分块
├── app/src/main/java/com/djzrs/htmlmanager/
├── MainActivity.kt            单 Activity，onStart/onStop 同步前后台状态
├── navigation/AppNavHost.kt   home / edit?name / server
├── ui/theme/                  色板、类型、主题
├── ui/home/                   列表、搜索、删除确认、预览
├── ui/edit/                   代码框、5 个动作 FAB、返回拦截
├── ui/server/                 启停、地址、二维码、悬浮窗授权入口
├── data/                      htmls/ 目录的唯一出入口与命名规则
├── server/                    服务器前台服务、状态桥、悬浮窗、HTTP 响应
└── util/                      局域网 IP、二维码生成
```

根目录的 `.cnb.yml` 是所有项目共用的流水线入口，本项目对应的段落带 `ifModify: html-manager/**`。

单 Activity + Navigation Compose + ViewModel + Repository，没有数据库：
文件就是数据，`context.filesDir/htmls/` 下的 `*.html` 即全部状态。

## 文档

| 文档 | 内容 |
| --- | --- |
| [开发文档 v1.2](docs/HTML管家-开发文档-v1.2.md) | 冻结版实现基线：页面规格、命名规则、服务器协议、色板 |
| [构建与发布](docs/CI与发布.md) | 流水线三条触发、产物命名、构建环境、手动出包、排障 |
| [签名与版本号](docs/签名与版本号.md) | 版本号规则、密钥生成与配置、本地正式包、指纹核对 |
| [后台运行与悬浮窗](docs/后台运行与悬浮窗.md) | 前台服务、状态桥、悬浮窗权限与交互、通知渠道 |
| [构建踩坑记录](docs/构建踩坑记录.md) | 429 限流、底包没有 JDK 17、附件上传时序、中文截断等实际踩过的坑 |

## 已知限制

- 走 `content://` 预览时，HTML 里相对路径引用的 CSS / JS / 图片可能加载不出来，
  这是浏览器侧的授权限制。要么写自包含单文件 HTML，要么用局域网服务器打开。
- 服务器 8080 端口被占用时不自动换端口，提示后需要手动重试。
- 服务器由前台服务保着，通知栏会一直有一条常驻通知；强制停止 APP 会连同服务一起结束。
