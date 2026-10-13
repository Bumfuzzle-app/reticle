# BUILD.md — 陌生人怎么把这个仓库构建成 APK

> 本文件是**命令级**指引：要自备什么、放哪、敲什么、每步期望看到什么，以及**一定会卡在哪**。
> 实测环境＝作者机（vivo／OriginOS，Android 17，shell 身份），2026-10-13。**本机跑通 ≠ 别人能跑通**，见 §5。

## 0 先说结论

- 本仓库**不含二进制**：构建工具链（aapt2／android.jar／ECJ／d8／apksigner）、第三方 jar（AndroidX／Material／Kotlin／Liquid Glass／ML Kit）都得自备。每样东西**从哪拿、怎么造**：见 [`docs/构建环境清单.md`](docs/构建环境清单.md)。
- 本仓库**不含签名钥匙**：不带钥匙只能构建到**未签名 APK**；要出可安装包，得自己生成钥匙（见 §4 ⑤）。
- 缺件不会「静默死」：出包/装机脚本**开场先自检**、逐条点名缺什么并指路；应用内 `build` 工具还能 `action=check` 一条命令看「就绪／缺哪几个／从哪拿」。
- 实测（同一天、同一台机器）：
  - 只拿仓库、什么都不配 → 脚本开场**逐条点名缺件并把「从哪拿」指出来**，退出码 4（§4 ①）。
  - 自备工具链＋物料、**不带任何钥匙** → 一路跑到 `unsigned.apk`，**只停在签名那一步**（§4 ⑤）。
- 仓库里的 `src/`、`res/`、`AndroidManifest.xml` 与作者的发布用源码树**逐字节相同**（`diff -rq` 无差异）。

## 1 运行环境：两条路

**A 设备侧（本仓库脚本的原生路线）**
Android 设备 + shell 身份（adb shell／Shizuku／`rish` 都行），用 `/system/bin/app_process` 跑**预转好的 dex**。需要：

```
工具链/bin/aapt2            （＋同目录一堆 lib*.so，靠 LD_LIBRARY_PATH 找）
工具链/lib/android.jar      （API 35）
工具链/dex/ecj.dex  d8.dex  d8-2.dex  apksigner.dex  buildtool.dex
工具链/ecj-res/             （ECJ 的资源目录）
```
（作者的构建工具链是历史产物；**怎么从零转出来：有结论**——`buildtool.dex` 已实测可从本仓库源码完全重造（逐字节一致），其余件是「jar → d8」机械产物，步骤照抄 [`docs/构建环境清单.md`](docs/构建环境清单.md) 的重导配方。）

**B PC 路线（仓库未提供脚本，需要自己搭）**
JDK 17+ ＋ Android SDK build-tools（`aapt2`／`d8`／`apksigner`）＋ `android.jar`（API 35）＋ `javac`。
把 `build/build2.sh` 里三处调用换成对应的命令行工具（ECJ→javac、app_process+d8.dex→d8、apksigner.dex→apksigner）即可，**Java 源码一行都不用改**。（本路线未实测。）

## 2 路径与物料：环境变量或 build.conf

`build/build2.sh` 用这些变量定位东西，**默认值是作者机路径，用前设成你自己的**：

| 变量 | 含义 | 默认值（作者机） |
|---|---|---|
| `SB` | 源码树 | `/sdcard/手机通道/通道三/工程` |
| `TC` | 工具链 | `$SB/工具链` |
| `KEEP` | 第三方物料 | `$SB/物料` |
| `KS`／`KD`／`KA` | 签名钥匙库／口令文件／钥匙别名 | `./keys/release.p12`／`./keys/口令.txt`／`release` |

**应用内**（`build` 工具）另有配置文件 `build.conf`（放设备「运行目录」，主包 `/sdcard/Reticle/build.conf`；示例：`build/build.conf.example`），六个键：`PROJECT_DIR`／`TOOLCHAIN_DIR`／`MATERIAL_DIR`／`BUILD_SH`／`INSTALL_SH`／`TMP_DIR`——不写＝用默认；支持 `$PROJECT_DIR` 引用与同名环境变量覆盖。改完跑 `build action=check` 复检。

物料目录照着摆（脚本按固定名字找）：

```
物料/classes/*.jar         第三方库 jar
物料/res-zips/*.zip        第三方库的资源包（aapt2 link 用 -R 逐个塞）
物料/jni/<abi>/*.so        native 库（文字识别模型，见 §4 ②）
物料/assets/               可选
物料/manifest碎片/*.xml    可选；用于给第三方库生成它们的 R 类
```

环境自检会逐个点名的 jar：`core-1.9.0.jar`、`appcompat-1.6.1.jar`、`material-1.9.0.jar`、`liquidglass-2.0.11.jar`、`vision-common-17.3.0.jar`、`play-services-tasks-18.2.0.jar`、`kotlin-stdlib-2.0.21.jar`。（每样从哪拿：见 §4/清单页。）

## 3 敲什么

```sh
cp -r /path/to/reticle-开源 /data/local/tmp/myrepo   # 别在原目录跑：脚本会写 输出/
cd /data/local/tmp/myrepo
SB=$PWD TC=/path/to/工具链 KEEP=/path/to/物料 sh build/build2.sh
```

逐步期望输出：

| 步 | 期望 |
|---|---|
| 0 | `环境自检：齐 ✓`（缺件会逐条点名＋指路并退出，见 §4 ①） |
| 1 | aapt2 compile 生成 `reszip/app.zip` |
| 2 | `base.apk` ≈ 3.2 MB；`库包名 62 个 → 生成 R 类` |
| 3 | `javac(ECJ) ok: 1305 个 class` |
| 4 | d8 出 8 个 dex（约 1~3 分钟，`-Xmx1536m`） |
| 5 | `unsigned.apk` ≈ 28.4 MB |
| 6 | 签名；钥匙不齐就会在这儿断（§4 ⑤） |
| 7 | `输出/reticle.apk` ＋ sha256 |

装机脚本 `build/装工具.sh` 用第一个参数或 `APK=<路径>` 环境变量指定包；都不给才用默认路径（作者机路径，记得改）。

## 4 一定会卡的地方（按真实遇到的顺序）

① **缺件**：出包/装机脚本**开场先自检**（59 号单起），缺件逐条点名并给「从哪拿」指引，退出码 4——不会跑到一半静默死。应用内 `build action=check` 同样一眼看清「就绪／缺哪几个／从哪拿」。
② **ML Kit 不许随仓库发**：文字识别用的 `vision-common`、`play-services-tasks` 等 jar 与 `物料/jni/*/**.so`（中文 bundled 模型）受 Google 条款约束，需要自己从 Google Maven 取 `text-recognition-chinese` bundled 版并放进物料；缺了第 3 步编译就过不去。
③ **Liquid Glass 不带二进制**：自行取 `liquidglass-2.0.11.jar` 放 `物料/classes/`。另外它要求 `kotlin-stdlib 2.0.21`／`core-ktx 1.13.1`；物料里若同时有 1.7.10／1.8.0 之类的旧同名库，d8 会报 `defined multiple times`（脚本里已显式排除几个旧版本）。
④ **`/sdcard` 上不能执行**：aapt2 等可执行文件必须拷到 `/data/local/tmp` 或应用私有目录再跑（脚本自己会拷到 `$W/bin`）。
⑤ **没有钥匙 ⇒ 只能到 unsigned**：仓库不含钥匙，`KS` 默认 `./keys/release.p12` 找不到时会回退去用 `$TC/debug.p12`——那要你自己的工具链里放了调试钥匙；都没有，就在签名处报 `FileNotFoundException` 停下（此时 `unsigned.apk` 已完整可用）。要出自己的包：`./keys/release.p12` ＋ `./keys/口令.txt`（一行 `口令=你的口令`），或直接 `keytool` 生成后改 `KS/KD/KA`。
⑥ **应用内「出包／更新」路径全部可配**（59 号单起）：默认＝作者机路径；未配置且默认路径不存在时，会**明确报「不可用＋缺什么」**（不是静默）。在自己的机器上用它：写 `build.conf`（示例 `build/build.conf.example`），或按 §2 把脚本跑通后照抄路径。

## 5 实测记录（最近在上）

| 运行 | 命令要点 | 结果 |
|---|---|---|
| 59-3 | 配置临时指向不存在路径（陌生人场景）→ `build`／`install`／`check` | 逐条人话＋「从哪拿」指引（见 59 单证据） |
| 59-2 | `buildtool.dex` 重造：ECJ 编译 `工具/BuildTool.java` → d8 `--min-api 24` | **sha256 与现役逐字节一致**（`9eb9dce1…dd537`） |
| 59-1 | 改后默认路径重跑 `build2.sh` | 全通 → `0.28.15／VC69`；unsigned 28,378,832 B；`输出/reticle.apk` sha256 `349239ed…f259` |
| 58-2 | （59 修复前）自备工具链＋物料、无钥匙 | 到 `unsigned.apk`、停在签名 |
| 58-1 | （59 修复前）只拿仓库、什么都不配 | 当时无提示直退；**59 起改为一开场逐条点名＋指路** |

**没验证的**：真正陌生的机器／系统（换台电脑、换台手机、干净容器）能否构建；PC 路线（JDK＋SDK）；Windows／macOS；Gradle 化构建；`aapt2`／`android.jar`／`ECJ`／`d8`／`apksigner` 的原料不在本机，**「从原料重转」除 `buildtool.dex`（已实测）外均未实测**。
