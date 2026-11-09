# 《HTML 管家》开发文档 v1.2（冻结版）

> 状态：**已冻结**。v1.2 为当前实现基线，功能范围与行为以此文档为准。
>
> v1.3 的偏差（本文未改写，以新文档为准）：
> 1. 服务器改为前台服务常驻（本文 §2/§4.3 说「不做前台服务」已作废），见[后台运行与悬浮窗](后台运行与悬浮窗.md)。
> 2. 首页列表项由「分隔线」改为「卡片 + 描边」，编辑页代码框加描边；
>    色板新增 `outline`（#AEB4BC / #5C6268）与 `outlineVariant`（#E3E5E9 / #33373C）。
> 3. 悬浮窗吸附后收成小球，点一下展开；形态与落点一起持久化，
>    见[后台运行与悬浮窗](后台运行与悬浮窗.md) §悬浮窗。
> 4. 编辑页顶栏左侧加返回按钮，与系统返回、手势返回同一条路径；
>    §4.2 的「与初值不同」改成「与上次落盘的内容/文件名不同」——
>    保存成功后基准一起前移，所以保存完再返回不会再弹一次三选项。
>    顺带把「已保存」从 Snackbar 换成 Toast：不用等它消失才退出。

## 1. 项目概述

| 项 | 值 |
| --- | --- |
| APP 名称 | HTML 管家 |
| 包名 | `com.djzrs.htmlmanager` |
| applicationId | `com.djzrs.htmlmanager` |
| minSdk / targetSdk | 26 / 34 |
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 服务器 | NanoHTTPD 2.3.1 |
| 二维码 | ZXing core 3.5.3 |
| 架构 | 单 Activity + Navigation Compose + ViewModel + Repository |

## 2. 功能范围

包含：文件列表、新建、编辑、预览（外部浏览器）、删除（二次确认）、本地 HTTP 服务器（8080 + 自动导航页 + 二维码）、首页文件名搜索、编辑页代码搜索。

不包含：语法高亮、WebView 兜底、文件导入导出、公共目录访问、前台服务常驻、端口自动切换。

## 3. 数据与存储

- 位置：`context.filesDir/htmls/`，私有目录，无存储权限。
- 命名：
  - 用户输入 `trim()` 后为空 → 阻止保存，Snackbar「请输入文件名」。
  - 不含 `.html` 后缀（大小写不敏感）→ 自动追加。
  - 非法字符 `/ \ : * ? " < > |` 替换为 `_`。
- 重名：保存前 `File.exists()` 判断；编辑模式且文件名未变视为覆盖自身，放行；其余情况提示「文件已存在」并阻止保存。
- 列表：遍历 `htmls/` 下 `*.html`，按 `lastModified()` 降序（最新在最上）。无数据库。

## 4. 页面规格

页面共 3 个：首页、编辑页、服务器页。预览不设页面，直接发 Intent。

### 4.1 首页

```
┌─────────────────────────────┐
│  HTML 管家            [🔍]  │  TopAppBar
├─────────────────────────────┤
│  index.html                 │
│                    ✏️ 👁 🗑  │
├─────────────────────────────┤
│  demo.html                  │
│                    ✏️ 👁 🗑  │
├─────────────────────────────┤
│        请添加文件。          │  空状态，居中
├─────────────────────────────┤
│  [ + 新建文件 ] [ ▶ 服务器 ]│  底部固定双按钮
└─────────────────────────────┘
```

- 列表项：文件名单行 ellipsis；右侧三个 IconButton：编辑 `Icons.Edit`、预览 `Icons.Visibility`、删除 `Icons.Delete`（error 色）。点击项本身无响应。
- 删除：AlertDialog「确定删除 {name}？」→ 确认删除，Snackbar「已删除」。
- 新建 / 编辑 → 编辑页（新建模式：空名空码；编辑模式：载入内容）。
- 预览 → 见 §5。
- 搜索：点 🔍 展开顶部搜索框，实时过滤（不区分大小写），关闭恢复全量。
- 空状态：居中「请添加文件。」，onSurfaceVariant 色。
- 底部栏：固定，两等宽按钮；「新建文件」主色填充，「服务器」主色描边。服务器运行中时，按钮左侧显示 8dp 绿色圆点 + 文案「服务器（运行中）」。
- 生命周期：`onResume` 刷新列表。

### 4.2 编辑页

```
┌─────────────────────────────┐
│ [文件名________]   [ 保存 ] │  顶栏
├─────────────────────────────┤
│                             │
│  <html>...                  │  代码框，等宽，占满
│                             │
│                       (全选)│
│                       (清空)│  Small FAB 竖向常显
│                       (复制)│
│                       (粘贴)│
│                       (搜索)│
└─────────────────────────────┘
```

- 顶栏：OutlinedTextField（文件名，单行）+ Button（保存），同一行。
- 代码框：占满剩余空间，`FontFamily.Monospace`，无高亮，可滚动。状态用 `TextFieldValue`（管理 selection 与滚动）。
- 保存：
  1. 文件名校验（§3）失败 → Snackbar 提示，留在页面。
  2. 通过 → 写入 → Snackbar「已保存」→ 返回首页。
- 返回拦截：内容与初值不同 → AlertDialog 三选项：
  - 放弃修改：直接返回，丢弃。
  - 继续编辑：关闭对话框。
  - 保存并退出：走保存流程，成功才返回；失败留在页面提示。
  - 无修改 → 直接返回。
- 右下 5 个 `SmallFloatingActionButton`（40dp，竖向，右对齐 16dp，间距 12dp，底部 16dp，常显）：

| 按钮 | 行为 |
| --- | --- |
| 全选 | `selection = TextRange(0, text.length)` |
| 清空 | 清空代码框（不动文件名），保存快照，Snackbar「已清空」+「撤销」 |
| 复制 | 有选中复制选中，否则复制全文，Toast「已复制」 |
| 粘贴 | 读系统剪贴板插入光标处（有选中则替换）；空则 Toast「剪贴板为空」 |
| 搜索 | 底部弹出搜索栏：输入词 +「上一个 / 下一个」+ 关闭。命中即选中匹配文本并滚动可见，循环查找；不做高亮着色；关闭清空选择 |

### 4.3 服务器页

```
┌─────────────────────────────┐
│  ← 本地服务器                │
├─────────────────────────────┤
│   状态：● 运行中             │
│   http://192.168.1.5:8080   │
│   [ 复制地址 ]               │
│   ┌─────────┐               │
│   │  QR 码  │               │
│   └─────────┘               │
│   局域网设备扫码或输入地址访问 │
│   [ 停止服务器 ]             │
└─────────────────────────────┘
```

- 进入即自动启动（已在运行则直接展示状态）。
- 地址：遍历 `NetworkInterface` 取 `siteLocalAddress` 的 IPv4；取不到显示 `127.0.0.1:8080` + 提示「未连接局域网，仅本机可访问」。
- 二维码：ZXing 生成该地址的 Bitmap，160dp 显示。
- 「复制地址」→ 写入剪贴板，Toast「已复制」。
- 「停止服务器」→ 停止，按钮变「启动服务器」。
- 生命周期：服务器跑在 `ServerService`（前台服务），与 Activity 解耦；退出界面、切后台都继续提供服务，
  只有用户主动停止才结束。见[后台运行与悬浮窗](后台运行与悬浮窗.md)。
- 首页按钮状态与服务器运行状态联动（观察同一 ViewModel）。

## 5. 预览（content:// + 外部浏览器，无兜底）

流程：

1. 不读取内容，直接为文件生成 `FileProvider.getUriForFile(context, "com.djzrs.htmlmanager.fileprovider", file)`。
2. 构造 Intent：

```kotlin
Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(uri, "text/html")
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
```

3. `resolveActivity(packageManager)` 非空 → `startActivity`；为 null → Toast「未找到可用的浏览器」。
4. 不做 WebView 兜底。

配置：

- `AndroidManifest.xml` 注册 FileProvider：

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="com.djzrs.htmlmanager.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

- `res/xml/file_paths.xml`：

```xml
<paths>
    <files-path name="htmls" path="htmls/" />
</paths>
```

已知限制（写入文档，不做代码处理）：通过 `content://` 打开时，HTML 内相对路径引用的外部 CSS/JS/图片可能加载失败；建议用户使用自包含单文件 HTML。这是浏览器侧限制。

## 6. 服务器规格

| 项 | 规格 |
| --- | --- |
| 端口 | 8080；启动失败（占用）→ 捕获异常，Toast「端口 8080 被占用」，显示「启动服务器」按钮供重试 |
| 绑定 | `0.0.0.0` |
| `/` | 自动生成导航页，`text/html; charset=utf-8` |
| `/files/{name}` | 返回 `htmls/{name}`；校验 `File(name).name == name` 且后缀 `.html` 且存在，否则 404，防目录穿越 |
| 其他 | 404 |
| 导航页 | 内联生成，见下 |

导航页模板（内联 CSS，Material 风，主色 `#1A73E8`）：

```html
<!DOCTYPE html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>HTML 管家</title>
<style>body{font-family:system-ui;background:#F8F9FA;margin:0;padding:24px;color:#202124}
h1{font-size:22px;color:#1A73E8}ul{list-style:none;padding:0}
li{margin:8px 0}a{display:block;padding:14px 16px;background:#fff;border-radius:16px;
color:#202124;text-decoration:none;box-shadow:0 1px 3px rgba(0,0,0,.08)}</style>
</head><body><h1>HTML 管家</h1><ul>
<!-- 按时间倒序生成 -->
<li><a href="/files/xxx.html">xxx.html</a></li>
</ul></body></html>
```

## 7. UI 规范（Material 3 / Google 风）

| Token | Light | Dark |
| --- | --- | --- |
| primary | #1A73E8 | #8AB4F8 |
| onPrimary | #FFFFFF | #202124 |
| background | #F8F9FA | #121212 |
| surface | #FFFFFF | #1E1E1E |
| onSurface | #202124 | #E8EAED |
| onSurfaceVariant | #5F6368 | #9AA0A6 |
| error | #D93025 | #F28B82 |

- 圆角：卡片 16dp / FAB 28dp / Small FAB 圆形 / 输入框 12dp。
- 字体：系统默认；代码框 Monospace。
- 图标：Material Symbols Outlined。
- 反馈统一 Snackbar（底部，避开 FAB 区）；短提示用 Toast。
- 深色模式跟随系统。

## 8. 权限与异常

| 场景 | 处理 |
| --- | --- |
| 文件读写 | 私有目录，无需权限 |
| 服务器 | Manifest 声明 `INTERNET`、`ACCESS_NETWORK_STATE` |
| 端口占用 | 捕获，Toast 提示，可重试 |
| 剪贴板空 | Toast「剪贴板为空」 |
| 文件名非法 | 自动替换为 `_` |
| 无浏览器 | Toast「未找到可用的浏览器」 |
| IP 获取失败 | 显示 `127.0.0.1` + 提示 |
| 保存 IO 异常 | Snackbar「保存失败」 |

## 9. 项目结构

```
com.djzrs.htmlmanager
├── MainActivity.kt
├── navigation/AppNavHost.kt
├── ui/theme/{Color,Theme,Type}.kt
├── ui/home/{HomeScreen,HomeViewModel}.kt
├── ui/edit/{EditScreen,EditViewModel}.kt
├── ui/server/{ServerScreen,ServerViewModel}.kt
├── data/{FileRepository,HtmlFile}.kt
├── server/AppHttpServer.kt
└── util/{NetworkUtils,QrCodeUtils}.kt
```

依赖：`compose-bom`、`navigation-compose`、`lifecycle-viewmodel-compose`、`nanohttpd:2.3.1`、`zxing:core:3.5.3`。

`build.gradle.kts` 对应项：

```kotlin
android {
    namespace = "com.djzrs.htmlmanager"
    defaultConfig {
        applicationId = "com.djzrs.htmlmanager"
        minSdk = 26
        targetSdk = 34
    }
}
```

## 10. 开发里程碑

1. **M1 骨架**：Compose + Navigation + M3 主题，三页空壳跳转。
2. **M2 文件层**：FileRepository（list/read/write/delete/exists/命名规则），首页列表 + 删除确认 + 空状态 + 搜索。
3. **M3 编辑页**：双框 + 保存校验 + 三选项返回拦截 + 5 个 FAB 全功能。
4. **M4 预览**：FileProvider + Intent，无浏览器提示。
5. **M5 服务器**：NanoHTTPD + 导航页 + IP + 二维码 + 生命周期 + 首页状态联动。
6. **M6 打磨**：Snackbar 统一、深浅色、图标落地（深海军蓝 + 白文件页 + `</>` + 眼睛）。

---

文档冻结。
