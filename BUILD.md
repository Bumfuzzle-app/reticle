# BUILD.md — 陌生人怎么把这个仓库构建成 APK

> 本文件是**命令级**指引：要自备什么、放哪、敲什么、每步期望看到什么，以及**一定会卡在哪**。
> 实测环境＝作者机（vivo／OriginOS，Android 17，shell 身份），2026-10-13。**本机跑通 ≠ 别人能跑通**，见 §5。

## 0 先说结论

- 本仓库**不含二进制**：构建工具链（aapt2／android.jar／ECJ／d8／apksigner）、第三方 jar（AndroidX／Material／Kotlin／Liquid Glass／ML Kit）都得自备。
- 本仓库**不含签名钥匙**：只能构建到**未签名 APK**；要出可安装包，得自己生成钥匙（见 §4 ⑤）。
- 实测（同一天、同一台机器，脚本一行没改）：
  - 只拿仓库、什么都不配 → **几秒内停在第 0 步**，且看不到友好提示（§4 ①）。
  - 自备工具链＋物料、**不带任何钥匙** → 一路跑到 `unsigned.apk`（28,376,079 B／888 个文件／8 个 dex／`app.reticle` 0.28.14 VC68／minSdk 30），**只停在签名那一步**。
- 仓库里的 `src/`、`res/`、`AndroidManifest.xml` 与作者的发布用源码树**逐字节相同**（`diff -rq` 无差异），所以上面那次构建出来的东西就是发布版同一份代码。

## 1 运行环境：两条路

**A 设备侧（本仓库脚本的原生路线）**
Android 设备 + shell 身份（adb shell／Shizuku／`rish` 都行），用 `/system/bin/app_process` 跑**预转好的 dex**。需要：

```
工具链/bin/aapt2            （＋同目录一堆 lib*.so，靠 LD_LIBRARY_PATH 找）
工具链/lib/android.jar      （API 35）
工具链/dex/ecj.dex  d8.dex  d8-2.dex  apksigner.dex  buildtool.dex
工具链/ecj-res/             （ECJ 的资源目录）
```
（作者的构建工具链是从 AOSP／Android SDK 的 jar 转 dex 得来的；仓库不提供，自行准备。）

**B PC 路线（仓库未提供脚本，需要自己搭）**
JDK 17+ ＋ Android SDK build-tools（`aapt2`／`d8`／`apksigner`）＋ `android.jar`（API 35）＋ `javac`。
把 `build/build2.sh` 里三处调用换成对应的命令行工具（ECJ→javac、app_process+d8.dex→d8、apksigner.dex→apksigner）即可，**Java 源码一行都不用改**。

## 2 路径与物料：三个环境变量

`build/build2.sh` 用三个变量定位东西，**默认值是作者机路径，必须自己设**：

| 变量 | 含义 | 默认值（作者机） |
|---|---|---|
| `SB` | 源码树 | `/sdcard/手机通道/通道三/工程` |
| `TC` | 工具链 | `$SB/工具链` |
| `KEEP` | 第三方物料 | `$SB/物料` |
| `KS`／`KD`／`KA` | 签名钥匙库／口令文件／钥匙别名 | `./keys/release.p12`／`./keys/口令.txt`／`release` |

物料目录照着摆（脚本按固定名字找）：

```
物料/classes/*.jar         第三方库 jar
物料/res-zips/*.zip        第三方库的资源包（aapt2 link 用 -R 逐个塞）
物料/jni/<abi>/*.so        native 库（文字识别模型，见 §4 ②）
物料/assets/               可选
物料/manifest碎片/*.xml    可选；用于给第三方库生成它们的 R 类
```

环境自检会逐个点名的 jar：`core-1.9.0.jar`、`appcompat-1.6.1.jar`、`material-1.9.0.jar`、`liquidglass-2.0.11.jar`、`vision-common-17.3.0.jar`、`play-services-tasks-18.2.0.jar`、`kotlin-stdlib-2.0.21.jar`。

## 3 敲什么

```sh
cp -r /path/to/reticle-开源 /data/local/tmp/myrepo   # 别在原目录跑：脚本会写 输出/
cd /data/local/tmp/myrepo
SB=$PWD TC=/path/to/工具链 KEEP=/path/to/物料 sh build/build2.sh
```

逐步期望输出：

| 步 | 期望 |
|---|---|
| 0 | `环境自检：齐 ✓`（缺件会逐条点名，但见 §4 ①） |
| 1 | aapt2 compile 生成 `reszip/app.zip` |
| 2 | `base.apk` ≈ 3.2 MB；`库包名 62 个 → 生成 R 类` |
| 3 | `javac(ECJ) ok: 1305 个 class` |
| 4 | d8 出 8 个 dex（约 1~2 分钟，`-Xmx1536m`） |
| 5 | `unsigned.apk` ≈ 28.4 MB（888 个文件） |
| 6 | 签名；钥匙不齐就会在这儿断（§4 ⑤） |
| 7 | `输出/reticle.apk` ＋ sha256 |

装机脚本 `build/装工具.sh` 同样吃 `APK` 参数／默认作者机路径，照 `SB` 的思路改。

## 4 一定会卡的地方（按真实遇到的顺序）

① **默认路径 ⇒ 冒烟失败在没有提示的地方**。什么都不设直接跑：`cp $TC/bin/* … || true` 把错误吞了，紧跟着的 `chmod 755 $W/bin/aapt2` 在 `set -e` 下直接杀掉脚本，报 `chmod: …/reticle-build/bin/aapt2: No such file or directory`，**后面那段友好的「缺件清单」根本跑不到**。（本仓库未改这个脚本，只如实记录。）
② **ML Kit 不许随仓库发**：文字识别用的 `vision-common`、`play-services-tasks` 等 jar 与 `物料/jni/*/**.so`（中文 bundled 模型）受 Google 条款约束，需要自己从 Google Maven 取 `text-recognition-chinese` bundled 版并放进物料；缺了第 3 步编译就过不去。
③ **Liquid Glass 不带二进制**：自行取 `liquidglass-2.0.11.jar` 放 `物料/classes/`。另外它要求 `kotlin-stdlib 2.0.21`／`core-ktx 1.13.1`；物料里若同时有 1.7.10／1.8.0 之类的旧同名库，d8 会报 `defined multiple times`（脚本里已显式排除几个旧版本）。
④ **`/sdcard` 上不能执行**：aapt2 等可执行文件必须拷到 `/data/local/tmp` 或应用私有目录再跑（脚本自己会拷到 `$W/bin`）。
⑤ **没有钥匙 ⇒ 只能到 unsigned**：仓库不含钥匙，`KS` 默认 `./keys/release.p12` 找不到时，脚本会回退去用 `$TC/debug.p12`——**那是作者机工具链里的调试钥匙**，陌生人自然没有，于是在签名处报 `FileNotFoundException: …/debug.p12` 停下（此时 `unsigned.apk` 已完整可用）。要出自己的包：`./keys/release.p12` ＋ `./keys/口令.txt`（一行 `口令=你的口令`），或直接 `keytool` 生成后改 `KS/KD/KA`。
⑥ **应用内「出包／更新」工具写死作者机路径**：`src/McpServer.java` 里仍是 `/sdcard/手机通道/通道三/工程/工具链/build2.sh` 之类。fork 后要用这两个按钮（或想照搬这套设备侧构建流程），得自己改源码。本仓库只把**脚本**（`build/`）做成了可配。

## 5 实测记录（2026-10-13）

| 运行 | 命令要点 | 结果 |
|---|---|---|
| 1 | `SB=<临时副本> sh build/build2.sh`，工具链／物料全走默认（＝仓库里没有） | **停在第 0 步**（§4 ①），exit 1（构建自证日志存于仓库外的工作记录） |
| 2 | `SB=<临时副本> TC=<自备工具链> KEEP=<自备物料>`，无钥匙 | 步 1~5 全通，`unsigned.apk` 28,376,079 B／sha256 `9dd05feb…88d3`；**停在步 6 签名**（同上） |

构建产物 SHA256（未签名）：`9dd05feb20e1258b56e14f39928b5be1fb49c5e4059c280559c9c80d0eec88d3`

**没验证的**：真正陌生的机器／系统（换台电脑、换台手机、干净容器）能否构建；A 路线的工具链怎么从零转出来（作者机上的工具链是历史产物，本仓库没给生成步骤）；Windows／macOS；Gradle 化构建。
