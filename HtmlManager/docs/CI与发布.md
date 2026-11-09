# 构建与发布（CI）

> 目标：每次提交都有包可下。main 的提交出 Nightly 包，`v*` tag 出正式包，
> 其他分支和 PR 只验证能不能编过。
>
> 流水线配置在仓库根目录的 [`.cnb.yml`](../../.cnb.yml)——本仓库多项目共用，
> 根配置按项目分块，每块用 `ifModify` 只在自己目录有改动时才跑。

## 一、三条触发

| 触发 | 做什么 | 产物在哪 |
| --- | --- | --- |
| `main` push（`html-manager/**` 有改动） | 编译 → 更新 `Nightly` Release → 上传附件 | Release `Nightly` |
| `v*` tag push | 编译 → 更新同名 Release（标 latest）→ 上传附件 | 该 tag 的 Release |
| 其他分支 push / PR | 只编译，不发布 | 无（仅看日志） |

`ifModify: html-manager/**` 意味着只改根 README 或别的项目时不会触发本项目的构建。
实测：只改根 `README.md` 的那次 push，流水线 328ms 就结束（跳过编译）。
新建分支的首次 push 没有对比基准，`ifModify` 会被忽略——那时照常构建，这是预期行为。

发布顺序不能反：`cnbcool/attachments` 上传 release 附件要求 Release 已存在，
所以 `git:release` 必须排在附件上传前面。

`git:release` 默认「先删后建」（`overlying: false`），Nightly 每轮都会把上一轮的旧 APK 清掉。
重复推同一个 tag 会重建 Release。

## 二、产物命名

| 场景 | 文件名 |
| --- | --- |
| Nightly（debug 签名） | `html-manager-nightly-<提交短 SHA>-debug.apk` |
| Nightly（release 签名） | `html-manager-nightly-<提交短 SHA>-release.apk` |
| tag（debug 签名） | `html-manager-<tag>-debug.apk` |
| tag（release 签名） | `html-manager-<tag>-release.apk` |

文件名后缀直接标出签名类型——`release` 是能上架的正式签名，`debug` 只能装不能用。
文件名带提交短 SHA，是为了让「手机上这个包到底是哪次构建」一眼能对出来。

## 三、版本号

**每次提交自动递增**，不需要人改文件：

- `versionCode` = 仓库提交数
- `versionName` = `1.2.<提交数>`；打 tag 时取 tag 名（`v1.3.0` → `1.3.0`）

细节、边界与覆盖方式见[签名与版本号](./签名与版本号.md)。

实测：分支上第 9 个提交构建出 `versionCode=9`；打 `v1.3.0` tag 构建出
`versionName=1.3.0`、`versionCode=9`。

## 四、签名

签名材料（keystore + 密码）放在**密钥仓库**，流水线通过 `imports` 注入。
没配密钥仓库时自动退回 debug 签名，构建不会失败。

完整步骤：[签名与版本号](./签名与版本号.md)。一句话版本：

```bash
bash .ci/setup-signing.sh          # 生成密钥 + 打印要粘进密钥仓库的 YAML
```

然后把 `.cnb.yml` 里 `html-manager-build` 的 `imports` 指向密钥仓库中的那份文件。

## 五、构建环境

镜像由 [`.ci/android.Dockerfile`](../.ci/android.Dockerfile) 声明：Debian trixie 底 +
OpenJDK 21 + commandline-tools + `platforms;android-34` + `build-tools;34.0.0`。

为什么用 `docker.build` 而不是现成镜像：平台缺省镜像既没有 JDK 也没有 Android SDK，
现装一次要七八分钟。声明成 Dockerfile 后，镜像按内容哈希缓存在制品库，
Dockerfile 不变就复用。实测第二轮起编译阶段 17 秒，整条流水线 29 秒。

`by` 只列 Dockerfile 自己——镜像内容只由它决定，不该跟着源码改动重建。

Gradle 的 `~/.gradle` 挂成 `copy-on-write` 数据卷，依赖和 wrapper 分发包复用节点上的副本。

## 六、依赖源

`settings.gradle.kts` 里阿里云镜像排在官方源前面，官方源保留作回落。
原因见[构建踩坑记录](./构建踩坑记录.md)第一条：`repo.maven.apache.org` 对构建节点
间歇返回 HTTP 429。

## 七、手动出包

```bash
# 本地出包（未配签名 -> debug 包）
./gradlew assembleDebug

# 本地出正式包（先按签名文档配好 .ci/signing.properties）
./gradlew assembleRelease

# 用平台触发一次（事件必须是 api_trigger 或其前缀）
cnb build start-build --repo djzrs/AndriodCloudBuild --branch main \
  --event api_trigger --title "手动出包" \
  --config "$(cat my-pipeline.yml)"
```

`--config` 可以整体替换流水线配置，自测发布链路时用它，不用为了试一次就推一次真代码。
注意 `--config` 替换的是整份配置，里面的分支名要改成实际要跑的分支。

## 八、排障

```bash
cnb pulls get-ci-logs        # 自动定位最近失败的构建
cnb build get-build-logs --repo djzrs/AndriodCloudBuild --sourceRef <分支>
```

| 现象 | 多半是 |
| --- | --- |
| `Received status code 429` | 阿里云镜像没命中、回落到了官方源，检查 `settings.gradle.kts` |
| `SDK location not found` | `ANDROID_SDK_ROOT` 没传进去，`.ci/build.sh` 用它写 `local.properties` |
| `docker.build.dockerfile is not found` | 触发的那次提交里没有 `html-manager/.ci/android.Dockerfile` |
| `Failed to install ... SDK packages` | Dockerfile 里少装 `platforms;android-34` / `build-tools;34.0.0` |
| Prepare 阶段就失败、日志为空 | `imports` 的路径不存在。签名占位文件 `html-manager/.ci/signing.yml` 不能删 |
| `NO_RELEASE_ID` | Release 还没建，确认 `git:release` 排在附件上传之前 |
| 附件上传 401 / 403 | PR 事件的 `CNB_TOKEN` 是只读的，发布步骤只在 push / tag_push 里跑 |
| 产物名带 `-debug` | 没拿到签名材料，按[签名与版本号](./签名与版本号.md)配密钥仓库 |
