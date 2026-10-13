# Reticle

**给 AI 助手配的一双手和一双眼睛。** Reticle 是一个 Android 应用：它在本机跑一个只监听 `127.0.0.1` 的 MCP 服务，把「截屏 / 读界面 / 认字 / 点按滑动 / 执行命令 / 下载 / 通知」这些能力以工具的形式交给 AI 客户端调用，AI 在对话里说什么，它就真的在屏幕上做出来。它自己不思考、不决定、不主动动手。

- 版本：**0.28.15**（versionCode 69）
- 包名：`app.reticle`｜本机端口：`8770`｜运行目录：`/sdcard/Reticle`

## 需要什么

| 条件 | 说明 |
|---|---|
| **Android 11 或更高** | **硬要求**。无障碍截屏接口从 Android 11（API 30）才有，低于这个版本**装不上**。 |
| 一个能自填 MCP 服务器地址的 AI 客户端 | 只适配过 **RikkaHub**（https://rikka-ai.com）；其他客户端理论上可用、未实测。 |
| Shizuku | 用于以 shell 身份执行命令。需先用「无线调试」或电脑启动服务；**本仓库/本应用不含 Shizuku 本体**，请自行安装。 |
| 无障碍服务 | 截屏、读界面、模拟触控全靠它（在系统设置里手动打开）。 |
| 所有文件访问 | 读写 `/sdcard/Reticle`（截图、下载、日志）。 |
| 通知权限（Android 13+） | 常驻状态通知。 |

30 秒自查：装好后按 `docs/换机自测清单.md` 走一遍。完整用法见 `docs/使用手册.md`（应用内「说明」页与它同源）。

## 安装（怎么装）

1. 到本仓库 **Releases** 页下载最新 `app.reticle-x.y.z-*.apk`（Android 11+），直接在手机上侧载安装——Google Play 目前不收这类「让 AI 驱动界面的应用」，**侧载是主渠道**。
2. 首次打开按界面提示走：打开无障碍服务 → 允许「所有文件访问」 → 允许通知；要用 shell 命令再自行装 Shizuku。
3. 装完照 `docs/换机自测清单.md` 走一遍（约 30 秒），确认截屏／读屏／点按都通。
4. 升级＝下载新 APK 覆盖安装即可（包名不变，配置与令牌保留）。

## 支持范围

我们的实测环境：**vivo／OriginOS（Android 17）＋ RikkaHub 客户端**。代码里没有任何厂商专属接口依赖，兼容性差异都在「行为」层面（厂商后台管控、自启动放行等）。完整声明见 **`docs/支持范围.md`**，要点：

- 截图与无障碍读屏需要 Android 11+；
- 「原子岛」「实况通知」是 **vivo 的扩展**，在别的机器上就是普通通知（功能不缺失，只是少了那个样式）；
- 部分国产系统会定期自动关闭第三方无障碍服务；被系统回收或从后台划掉后，**多数情况下不会自己重启**（实测于 vivo／OriginOS，别的机型可能不同）；
- 没有 Google 服务的机器：装 Shizuku 那一步的商店链接打不开；Reticle 本身与文字识别模型都不依赖 Google 服务。

## 不做什么（刻意不做，别当 issue 提）

- **不做敏感页守卫**：不分析、不拦截支付/密码/验证码页面。屏幕内容会交给你自己选的模型，风险自担——「别让它截敏感画面」比任何开关都可靠。
- **不做急停按钮**：应用内「一键停止所有功能初始化」就是停手入口；从后台划掉也能停，但那是系统行为、不保证。
- **不做自愈**：被整进程杀掉后不会自己重启（可从对话里发一条修复广播把冷启动叫回来）。
- **维护者不保证回复**：本仓库按作者自己的节奏走，issue／PR 可能长期没人处理（先例：RikkaHub 同样不收 PR），请别等。有想法欢迎自己 fork 一份改。

## 安全与隐私

- MCP 服务**只监听 `127.0.0.1`**，同 Wi-Fi 的其他设备也连不上；每次调用必须带令牌（在应用「配置 → 令牌与地址」里管理，可一键重生成）。
- 屏幕内容与界面信息只交给**你自己配置的那个 AI 客户端**，之后是否出网取决于你选的模型与线路；Reticle 本身不上传任何东西。唯一的主动联网行为是「下载文件」（按给定 URL 取）。
- 审计日志写在 `/sdcard/Reticle/logs/audit-YYYYMMDD.log`（不记录令牌）。
- 令牌只存在本机配置里；本仓库不含任何密钥、口令或签名文件。

**安全漏洞上报**：请在仓库页 **Security → Report a vulnerability**（GitHub 私有漏洞上报）提交——报告者无需邮箱，作者也不必公开邮箱。不要开公开 issue。安全问题以外的功能建议，本仓库不受理。

## 构建

- 源码：`src/`（Java，21 个文件）；资源：`res/`；清单：`AndroidManifest.xml`。
- **`src/ManualDoc.java` 是《使用手册》的产物**（由 `docs/使用手册.md` 机械转换而来）：要改说明文字请改 md 再重新生成，**别手改这个文件**。
- 构建脚本见 `build/`（`build2.sh` 出包、`装工具.sh` 装机）：**脚本默认路径是我机器的，要指到你自己的**——① 出包/装机脚本开场先自检、缺件逐条点名（连「从哪拿」一起给）；② 应用内 `build` 工具 `action=check` 一条命令看「就绪／缺哪几个／从哪拿」；③ 路径还能写进 `build.conf`（示例见 `build/build.conf.example`）或用环境变量。**从零到出包的命令级步骤、要自备什么、每样东西从哪拿／放哪／怎么验，都在 [`BUILD.md`](BUILD.md) 与 [`docs/构建环境清单.md`](docs/构建环境清单.md)**。设备侧依赖 `aapt2`、ECJ、d8、apksigner、`android.jar`（API 35）等（用 `/system/bin/app_process` 跑预转 dex），另需第三方 jar（见下）。
- **本仓库不含二进制依赖**：
  - `Liquid Glass for Android`（QWEA0/Liquid-Glass-Android，v2.0.11，MIT）：请自行获取 `liquidglass-2.0.11.jar` 放入 `物料/classes/`；
  - `Google ML Kit`（text-recognition 中文 bundled 模型）：受 ML Kit 条款约束，不能再分发，请自行添加依赖；
  - AndroidX / AppCompat / Material / Kotlin stdlib / Shizuku-API 等，按各自上游获取。
- **fork 请改身份**，否则与原包撞名、覆盖安装：`AndroidManifest.xml` 的 `package`、`res/values/strings.xml`、`src/` 里 `app.reticle` 常量、端口 `8770`、目录 `/sdcard/Reticle`。`build/build2.sh` 里有一段「ID=B」的身份替换示例（把主包名/端口/目录换成副实例），可照着改成你自己的。
- 本仓库**不是 Gradle 工程**：`AndroidManifest.xml` 里的 `package` 就是 applicationId；fork 时把它、端口、目录、`src/` 里的 `app.reticle` 常量一起改成你自己的，否则会与原包撞名、互相覆盖安装。

## 许可与致谢

- 本项目：**Apache License 2.0**（见 `LICENSE`）。
- 第三方组件与许可见 `NOTICE`；许可查证记录见 `docs/许可查证.md`。

## 目录

```
src/            21 个 Java 源文件（含 ManualDoc.java＝手册产物）
res/            资源（strings/themes/图标/无障碍配置）
AndroidManifest.xml
BUILD.md        陌生人构建指引（自备什么／敲什么命令／卡在哪）
docs/           使用手册、支持范围、换机自测清单、许可查证、构建环境清单
build/          出包与装机脚本（路径可配）＋构建配置示例 build.conf.example
LICENSE         Apache-2.0 全文
NOTICE          第三方组件与许可
```
