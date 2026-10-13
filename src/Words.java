package app.reticle;

/**
 * 31 号单（2026-10-08）· 全部面向用户文案的唯一集中地（阶段二起，散落的文案都收到这里）。
 * 口径：说人话、够用就好；不出现命令／端口／日志／版本号这类词。
 * 命名：OB_ = 引导页；MA_ = 主界面（首页／配置页／高级页／底部导航）；NT_ = 通知与通知栏；
 *       CF_ = 配置页条目；WD_ = 看门狗那句话；TL_ = 给助手的工具说明；ST_ = 系统里露出的名字；
 *       RP_ = 给助手的回话（工具返回的那句话，界面上看不到）。
 * 改文案只改这一个文件。
 */
public class Words {

    // ═══════════ 引导页 · 通用 ═══════════
    public static final String OB_TITLE_C = "Reticle";
    public static final String OB_BYE = "Reticle 已就绪。如需重新运行引导，请在「配置」中点击「重跑一遍引导」。";
    public static final String OB_SUB = "先花一分钟看明白它是什么，再一步步开权限、试一试";
    public static final String OB_STEP0 = "是什么";
    public static final String OB_STEP1 = "连接 Shizuku";
    public static final String OB_STEP2 = "开无障碍";
    public static final String OB_STEP3 = "文件访问";
    public static final String OB_STEP4 = "试一试";
    public static final String OB_NEXT = "下一步";
    public static final String OB_DONE = "完成";
    public static final String OB_SKIP = "暂不配置（可随时在「配置」中重新运行）";
    public static final String OB_RETRY = "重新检测";

    // ═══════════ 48 号单（2026-10-12）· 引导页 · 第 0 屏：这应用是干嘛的（大白话，先讲清再要权限） ═══════════
    public static final String OB_S0_T = "Reticle 是干嘛的";
    public static final String OB_S0_P1 = "手机应用商店里的那些 AI 助手 App（豆包、Kimi、DeepSeek 之类）是封闭的：它们不让外部工具插手，接不了。";
    public static final String OB_S0_P2 = "Reticle 接的是另一类 AI 客户端——能填「服务器地址」和「请求头」的那种（目前只适配过 RikkaHub）。它自己找不来这种客户端，得你先装一个、把它配好。";
    public static final String OB_S0_P3 = "配好之后，Reticle 就是给 AI 配的手和眼睛：你在对话里让它看一眼屏幕上是什么、帮你点一下，它就能真的做到。它自己不会思考，也不会背着你干活——每一下都是你在对话里让它做的。";
    public static final String OB_S0_P4 = "下面要开的三样权限，就是给这双手和眼睛用的：";
    public static final String OB_S0_L1 = "Shizuku：让它替你点屏幕、替你跑命令";
    public static final String OB_S0_L2 = "无障碍：让它能看见屏幕上的内容、能点";
    public static final String OB_S0_L3 = "文件访问：让它能读写手机里的文件";

    // ═══════════ 引导页 · 第 1 步：Shizuku ═══════════
    public static final String OB_S1_LEAD = "Shizuku 是一款权限桥接工具：无需连接电脑，也无需 root，即可将系统权限授予 Reticle。只有借助 Shizuku，Reticle 才能执行点击与命令操作。";

    public static final String OB_S1_NONE_T = "尚未安装 Shizuku";
    public static final String OB_S1_NONE_D = "请先下载并安装 Shizuku，完成后返回此页面。";
    public static final String OB_S1_NONE_B1 = "下载 Shizuku";
    public static final String OB_S1_NONE_B2 = "装好了，检测一下";

    public static final String OB_S1_OFF_T = "已安装 Shizuku，但服务未运行";
    public static final String OB_S1_OFF_D = "点击下方按钮打开 Shizuku，并按照其页面提示启动服务（通常选择「通过无线调试启动」），随后返回此页面。部分系统会弹出「应用跳转确认」，请选择「始终打开」，以免每次重复询问。";
    public static final String OB_S1_OFF_B1 = "打开 Shizuku";
    public static final String OB_S1_OFF_B2 = "已启动，检测一下";

    public static final String OB_S1_NO_T = "最后一步：授权 Reticle";
    public static final String OB_S1_NO_D = "点击下方按钮，Shizuku 将弹出授权窗口，请选择「允许」。授权完成后，可在 Shizuku 的「应用管理」中查看 Reticle。";
    public static final String OB_S1_NO_B1 = "请求授权";
    public static final String OB_S1_WAIT = "等待授权：请在弹出的窗口中选择「允许」……";
    public static final String OB_S1_DENY = "授权未成功。请再次点击「请求授权」，并在弹出的窗口中选择「允许」。";
    public static final String OB_S1_NEED_RUN = "Shizuku 当前未在运行：请先点击上方「打开 Shizuku」启动服务，再点击「请求授权」。";
    public static final String OB_S1_REQ_FAIL = "授权请求发送失败：请确认 Shizuku 正在运行，然后再次点击「请求授权」。";
    public static final String OB_S1_NO_POPUP = "若点击后未弹出授权窗口：请切换到 Shizuku，进入「应用管理」，手动开启 Reticle 的开关（部分系统不会弹出授权窗口，需在此处手动开启）。";

    public static final String OB_S1_OK_T = "Shizuku 已连接";
    public static final String OB_S1_OK_D = "Reticle 现已获得系统权限，可以正常执行点击与命令操作。";

    // ═══════════ 引导页 · 第 2 步：无障碍 ═══════════
    public static final String OB_S2_LEAD = "Reticle 通过无障碍服务读取屏幕、识别文字并执行点击——这是其所有操作能力的基础。";
    public static final String OB_S2_T = "把 Reticle 的开关打开";
    public static final String OB_S2_D = "点击下方按钮前往系统设置，在列表中找到「Reticle」并开启开关，随后返回此页面。";
    public static final String OB_S2_FIX = "若开关为灰色、无法开启：请前往「设置 → 应用 → Reticle → 右上角「更多」→ 解除应用限制」，然后返回重试。";
    public static final String OB_S2_WD = "提示：部分系统会定期自动关闭第三方无障碍服务；Reticle 被关掉后会自己开回来（「配置」里可以关掉这个开关）。";
    public static final String OB_S2_B1 = "去打开";
    public static final String OB_S2_NO_T = "尚未检测到无障碍已开启";
    public static final String OB_S2_NO_D = "请返回系统设置页面，开启「Reticle」的无障碍开关，然后返回并点击「重新检测」。";
    public static final String OB_S2_OK_T = "无障碍已开启";
    public static final String OB_S2_OK_D = "屏幕读取、文字识别与点击能力已就绪。";

    // ═══════════ 引导页 · 第 3 步：所有文件访问（35 号单） ═══════════
    public static final String OB_S3F_LEAD = "Reticle 的工作文件保存在手机存储中专属于它的文件夹里，命令执行与结果保存都依赖它。系统默认不允许应用访问手机存储，需要你手动开启一次；不开的话，命令功能会静默失败（不报错）——建议保持开启。";
    public static final String OB_S3F_NO_T = "允许 Reticle 访问其专属文件夹";
    public static final String OB_S3F_NO_D = "点击下方按钮前往系统设置，找到「Reticle」，开启「所有文件访问」开关，随后返回此页面。";
    public static final String OB_S3F_B1 = "去打开";
    public static final String OB_S3F_B2 = "已开启，重新检查";
    public static final String OB_S3F_OK_T = "已可访问专属文件夹";
    public static final String OB_S3F_OK_D = "此权限已配置完成，无需再次设置。";
    // 45 号单：「收成一份」——这条与 OB_S3F_LEAD 讲的是同一件事，已并入 LEAD，不再单独显示
    public static final String OB_S3F_HINT = "若在该页面中找不到 Reticle：请先返回 Reticle 主界面，再次点击「去打开」；部分系统需要数秒才会显示该条目。";
    public static final String OB_S3F_OLD_T = "此系统无需单独开启";
    public static final String OB_S3F_OLD_D = "您的系统版本较低，Reticle 使用常规存储权限即可，点击「下一步」跳过。";

    // ═══════════ 引导页 · 第 4 步：自检 ═══════════
    public static final String OB_S3_LEAD = "点击「开始自检」，Reticle 将自动检测四项能力，数秒内即可完成。";
    public static final String OB_S3_B1 = "开始自检";
    public static final String OB_S3_RUNNING = "正在检测…（约 5 秒）";
    public static final String OB_S3_AGAIN = "重新检测";
    public static final String OB_S3_ALL_OK = "四项检测全部通过，可以正常使用。";
    public static final String OB_S3_SOME_BAD = "部分检测未通过——请根据上方提示修复后，点击「再测一遍」。也可以先点「完成」，稍后在「配置」中重新运行引导。";

    public static final String OB_C1 = "看屏幕";
    public static final String OB_C2 = "点屏幕";
    public static final String OB_C3 = "认字";
    public static final String OB_C4 = "跑命令";

    public static final String OB_FIX_A11Y = "修复方法：返回上一步，开启无障碍开关。";
    public static final String OB_FIX_SHZ = "修复方法：返回第 1 步，启动 Shizuku 并完成授权。";
    public static final String OB_FIX_OCR = "修复方法：文字识别依赖无障碍截图，请先开启无障碍开关；若仍失败，请重启 Reticle。";
    public static final String OB_FIX_SHOT = "修复方法：屏幕截图依赖无障碍服务，请先开启无障碍开关。";

    // ═══════════ 引导页 · 第 5 屏：你只需要知道这些（45 号单） ═══════════
    // 标题／副标题／9 条正文都在 Manual.java（唯一数据源＝与「详细说明·上手章」同一份文字）
    public static final String OB_MIN_NEXT = "下一步";
    public static final String OB_MIN_GO = "知道了，开始用";

    // ═══════════ 详细说明页（45 号单：主界面大标题右边的圆形「?」） ═══════════
    public static final String MN_TITLE = "详细说明";
    public static final String MN_SUB = "Reticle 是怎么来的、能干什么、干不了什么";
    public static final String MN_BACK_DIR = "← 目录";
    public static final String MN_TAIL = "这一页随应用一起走：文案只维护一处，改一次、两处（引导与这里）一起变。";
    public static final String MA_HELP_DESC = "详细说明：Reticle 是怎么来的、能干什么、干不了什么";

    // ═══════════ 引导页 · 弹出来的短提示（toast） ═══════════
    public static final String OB_TOAST_STORE = "请先下载并安装 Shizuku。";
    public static final String OB_TOAST_SHZ = "无法打开 Shizuku，请从桌面手动启动。";
    public static final String OB_TOAST_A11Y = "无法打开系统设置，请手动前往「设置 → 快捷与辅助 → 无障碍」，开启 Reticle。";
    public static final String OB_TOAST_FILES = "无法打开系统设置，请手动前往「设置 → 应用 → Reticle → 权限」，开启「所有文件访问」。";

    // ═══════════ 主界面 · 顶部与首页 ═══════════
    public static final String MA_TITLE_A = "Reticle";
    public static final String MA_TITLE_B = "Beta";
    public static final String MA_TITLE_DESC_A = "标题 Reticle，点一下切换成 Beta";
    public static final String MA_TITLE_DESC_B = "标题 Beta，点一下切回 Reticle";
    public static final String MA_SUB_TAIL = " · 看屏幕 · 点屏幕 · 认字 · 跑命令";

    public static final String MA_SEC_STATUS = "运行状态";
    public static final String MA_STAT_MCP = "MCP 服务";
    public static final String MA_STAT_A11Y = "无障碍";
    public static final String MA_STAT_SHZ = "Shizuku";
    public static final String MA_STAT_NOTI = "通知";

    public static final String MA_MCP_ON = "运行中";
    public static final String MA_MCP_OFF = "未运行";
    public static final String MA_A11Y_ON = "在册 ✓";
    public static final String MA_A11Y_MAYBE = "疑似在册";
    public static final String MA_A11Y_OFF = "不在册 ✗";
    public static final String MA_A11Y_EMPTY = "读回为空";
    public static final String MA_A11Y_ERR = "读取失败";

    // ───────── 53 号单：无障碍「连接态」（★在册 ≠ 能用） ─────────
    public static final String MA_A11Y_CONN_OK = "已连接 ✓";
    public static final String MA_A11Y_CONN_DISC = "在册未连接 ⚠";
    public static final String MA_A11Y_CONN_NOT = "不在册 ✗";
    public static final String MA_A11Y_CONN_UNK = "读取失败";
    public static final String MA_A11Y_HINT_DISC = "无障碍还在系统的名单里、但系统没把它接上（通常是被杀过、或被杀后重开 App）。一般十几秒内会自己接回；若一直不回来：点下面「修一次」，或到系统设置里把 Reticle 的无障碍开关关一次再开（也可以直接重启手机）。";
    public static final String MA_A11Y_HINT_NOT = "无障碍不在系统的名单里——看门狗（开关开着的话）会自己写回；写不回请到系统设置里把 Reticle 的无障碍开关打开一次。";
    public static final String MA_BTN_A11Y_FIX = "修一次（重连无障碍）";
    public static final String MA_BTN_A11Y_FIX_D = "做法：把 Reticle 自己那条从系统名单里删掉、再写回，逼系统重新连一次。只动 Reticle 自己这条，不影响系统中其他辅助服务。";
    public static final String MA_MSG_A11Y_FIXING = "正在重连无障碍…（约 3~8 秒）";
    public static final String MA_MSG_A11Y_FIXED = "无障碍修复：";
    public static final String MA_SHZ_OFF = "未运行 ✗";
    public static final String MA_SHZ_OLD = "版本过旧 ✗";
    public static final String MA_SHZ_OK = "已授权 ✓";
    public static final String MA_SHZ_NO = "未授权 ✗";
    public static final String MA_SHZ_ERR = "查询异常";
    public static final String MA_NOTI_ON = "已开启";
    public static final String MA_NOTI_OFF = "未开启";
    public static final String MA_NOTI_NA = "无需";

    public static final String MA_HINT_TAP_CARD = "轻点任一状态卡片即可刷新";
    public static final String MA_MSG_STATUS_REFRESHED = "状态已刷新";

    public static final String MA_SEC_TOOLS = "常用";
    public static final String MA_TILE_SELFTEST = "一键自检";
    public static final String MA_TILE_SELFTEST_D = "一次完成回铃与核心能力检测";
    public static final String MA_TILE_RESTART = "重启服务";
    public static final String MA_TILE_RESTART_D = "重新启动 MCP 服务";
    public static final String MA_TILE_REFRESH = "刷新状态";
    public static final String MA_TILE_REFRESH_D = "重新读一遍运行状态";
    public static final String MA_MSG_REFRESHED = "已刷新";
    public static final String MA_TILE_A11Y_SET = "无障碍设置";
    public static final String MA_TILE_A11Y_SET_D = "打开系统的无障碍页";

    public static final String MA_MSG_SELFTESTING = "正在自检…（约 5 秒）";
    public static final String MA_SELFTEST_HEAD = "自检结果";
    public static final String MA_MSG_SELFTEST_ERR = "自检异常：";
    public static final String MA_MSG_RESTARTED = "已请求启动/重启（幂等，不会重复启动）。";
    public static final String MA_MSG_RESTART_ERR = "重启失败：";

    // ═══════════ 配置页 ═══════════
    public static final String CF_SEC_GUIDE = "引导";
    public static final String CF_GUIDE_T = "重跑一遍引导";
    public static final String CF_GUIDE_D = "重新运行连接、无障碍、文件访问与自检四个步骤";
    public static final String CF_GUIDE_D_TODO = "「所有文件访问」未开启——点击此处前往开启";

    public static final String CF_SEC_CONN = "连接";
    // ═══════════ 50 号单（2026-10-12）· 配置区：三个按钮＝名字即用途，各自写明「粘到哪个框」 ═══════════
    public static final String CF_BTN_HNAME = "复制请求头名称";
    public static final String CF_BTN_HVALUE = "复制请求头值（令牌）";
    public static final String CF_BTN_URL = "复制链接地址";
    public static final String CF_HINT_HNAME = "粘到 RikkaHub：「设置 → MCP 服务器 → 新建 → 请求头 · 名称」";
    public static final String CF_HINT_HVALUE = "粘到 RikkaHub：「设置 → MCP 服务器 → 新建 → 请求头 · 值」";
    public static final String CF_HINT_URL = "粘到 RikkaHub：「设置 → MCP 服务器 → 新建 → 服务器地址」，传输类型选 Streamable HTTP";
    public static final String CF_CONN_LEAD = "在 RikkaHub 里新增一条 MCP 服务器，照着下面三个按钮挨个复制、挨个粘。";
    public static final String CF_CONN_OFFICIAL = "还没装过这类 AI 客户端？RikkaHub 官网：https://rikka-ai.com";
    public static final String CF_MSG_HNAME_OK_A = "已复制请求头名称：";
    public static final String CF_MSG_HNAME_OK_B = " → 粘到 RikkaHub → 设置 → MCP 服务器 → 新建 → 请求头 · 名称";
    public static final String CF_MSG_HNAME_ERR = "复制请求头名称失败：";
    public static final String CF_MSG_HVALUE_OK_A = "已复制请求头值（含 Bearer，共 ";
    public static final String CF_MSG_HVALUE_OK_B = " 个字符）→ 粘到 RikkaHub → 设置 → MCP 服务器 → 新建 → 请求头 · 值";
    public static final String CF_MSG_HVALUE_ERR = "复制请求头值失败：";
    public static final String CF_MSG_URL_OK_A = "已复制链接地址：";
    public static final String CF_MSG_URL_OK_B = " → 粘到 RikkaHub → 设置 → MCP 服务器 → 新建 → 服务器地址（传输类型选 Streamable HTTP）";
    public static final String CF_MSG_URL_ERR = "复制链接地址失败：";
    public static final String CF_MIN_T = "使用前须知";
    public static final String CF_MIN_D = "最要紧的几条说明——就是引导最后那一屏";

    public static final String CF_SEC_ADDR = "服务地址";
    public static final String CF_ADDR_LINE = "服务地址（仅本机）· 日志更新 ";
    public static final String CF_LOG_NONE = "无日志";

    public static final String CF_SEC_BEHAV = "行为";
    public static final String CF_SW_VIB_T = "静音模式下保留震动提醒";
    public static final String CF_SW_VIB_D = "开启后，静音模式下仍会震动提醒（使用闹钟通道）；关闭后遵循系统静音设置。任何情况下均不会发声。";
    public static final String CF_MSG_VIB_ON = "已开启：静音时也震";
    public static final String CF_MSG_VIB_OFF = "已关闭：尊重系统静音";
    public static final String CF_ITEM_NOTI_SET = "通知设置";
    public static final String CF_ITEM_NOTI_SET_D = "打开本应用的通知设置页";

    public static final String CF_SEC_WD = "无障碍看门狗";
    public static final String CF_SW_WD_T = "掉了自动重开";
    public static final String CF_SW_WD_D = "部分国产系统（实测于 vivo／一加）会自动关闭本应用的无障碍服务（最长约每 5 小时一次，或当您打开系统「无障碍」设置页时）。开启此开关后，若被关闭，Reticle 将自动恢复——仅恢复本应用，不影响其他辅助服务；关闭开关后，Reticle 不会修改系统设置。";
    public static final String CF_MSG_WD_ON = "已开启：掉了自动重开";
    public static final String CF_MSG_WD_OFF = "已关闭：不再自动重开";
    public static final String CF_ITEM_WD_CHECK = "立即检查一次";
    public static final String CF_ITEM_WD_CHECK_D = "立即检查；如未开启将自动恢复";
    public static final String CF_MSG_CHECKING = "检查中…";
    public static final String CF_MSG_WD_PREFIX = "看门狗：";
    public static final String CF_MSG_ERR_PREFIX = "异常：";

    public static final String CF_SEC_LOOK = "外观";
    public static final String CF_THEME_SYS = "跟随系统";
    public static final String CF_THEME_LIGHT = "浅色";
    public static final String CF_THEME_DARK = "深色";
    public static final String CF_BLACK_T = "极夜";
    public static final String CF_BLACK_D_ON = "开启＝纯黑背景（OLED 屏幕省电）；关闭＝深灰背景";
    public static final String CF_BLACK_D_OFF = "切换至「深色」主题后可开启此选项";

    public static final String CF_NAV_SEC = "底部导航";
    public static final String CF_NAV_D = "导航条参数：需要更强的玻璃质感，可选择「棱镜强」；需要更柔和的弹性效果，可选择「柔和更弹」。修改立即生效，重启应用后保留。";
    public static final String CF_NAV_P1 = "均衡";
    public static final String CF_NAV_P2 = "棱镜强";
    public static final String CF_NAV_P3 = "柔和更弹";
    // 54 号单 A：底部导航蒙版第二版的两根滑杆（配置页「底部导航」区）
    public static final String CF_MASK_ALPHA_T = "蒙版浓度";
    public static final String CF_MASK_ALPHA_D = "停住后盖在图标上的那层极淡蒙版的深浅。越淡越看不出边界，太淡又显不出停在哪一页。默认 12%。";
    public static final String CF_MASK_GAP_T = "滴→蒙版 间隔";
    public static final String CF_MASK_GAP_D = "滴停稳后先淡出，隔这么久蒙版再淡入；0 ＝ 两者同时（原来的交叉淡化）。默认 120 毫秒。";

    public static final String CF_SEC_FX = "导航动效";
    public static final String CF_FX_INTRO = "轻点图标，或将玻璃滴拖动至某个图标上松开时，该图标将旋转并放大；拖动过程中导航条会拉长变细，松开后回弹。下方三个滑杆可随时调整，修改后自动保存。";
    public static final String CF_FX1_TITLE = "弹跳幅度";
    public static final String CF_FX1_HINT = "导航条拉长与回弹幅度（2%–30%）";
    public static final String CF_FX1_UNIT = "%";
    public static final String CF_FX2_TITLE = "扩张幅度";
    public static final String CF_FX2_HINT = "图标放大峰值（100%–260%），放大范围自动限制在导航条内";
    public static final String CF_FX2_UNIT = "%";
    public static final String CF_FX3_TITLE = "动画时长";
    public static final String CF_FX3_HINT = "图标旋转一周所需时长（520–1200 毫秒）";
    public static final String CF_FX3_UNIT = " 毫秒";
    public static final String CF_ITEM_FX_RESET = "恢复默认动效";
    public static final String CF_ITEM_FX_RESET_D = "弹跳 12% · 扩张 165% · 时长 640 毫秒";
    public static final String CF_MSG_FX_RESET = "动效已恢复默认";

    public static final String CF_SEC_MAINT = "维护";
    public static final String CF_STOP_T = "一键停止所有功能初始化";
    public static final String CF_STOP_D = "关闭看门狗与本地服务，并清除引导标记——恢复至初始状态；重新打开 Reticle 即可恢复";
    public static final String CF_STOP_ASK = "将执行以下操作：\n① 关闭「无障碍掉线自动重开」；\n② 停止 Reticle 的后台服务与常驻通知；\n③ 下次打开应用时将重新显示引导。\n系统的无障碍开关不在此管理范围内，如需关闭请前往系统设置。\n完成后重新打开 Reticle 即可恢复使用。";
    public static final String CF_DLG_CANCEL = "取消";
    public static final String CF_DLG_STOP = "停止";
    public static final String CF_STOP_DONE = "已停止全部功能。重新打开 Reticle 即可恢复（引导将重新显示）。";
    public static final String CF_STOP_NOTE = "停止后，助手暂时无法连接 Reticle；重新打开应用即可恢复。";
    public static final String CF_MSG_DLG_ERR = "弹窗失败：";

    // ═══════════ 高级页 ═══════════
    public static final String MA_SEC_TEST = "试验";
    public static final String MA_ITEM_ISLAND = "原子岛试验";
    public static final String MA_ITEM_ISLAND_D = "在原子岛发一张常驻卡片";
    public static final String MA_MSG_ISLAND = "原子岛(创建)：";
    public static final String MA_ISLAND_T = "Reticle 试验";
    public static final String MA_ISLAND_C = "岛上有我，说明本地方式可行";
    public static final String MA_ITEM_MEDIA = "媒体试验";
    public static final String MA_ITEM_MEDIA_D = "发一条媒体样式通知（原子随身听）";
    public static final String MA_MSG_MEDIA = "媒体(播放)：";
    public static final String MA_MEDIA_T = "Reticle·正在截图";
    public static final String MA_MEDIA_C = "媒体路线试验（通知栏/原子随身听查看）";
    public static final String MA_ITEM_LIVE = "实况试验";
    public static final String MA_ITEM_LIVE_D = "ProgressStyle 实况通知";
    public static final String MA_MSG_LIVE = "实况：";
    public static final String MA_LIVE_T = "Reticle·正在处理";
    public static final String MA_LIVE_C = "实况通知试验（18 号）";
    public static final String MA_ITEM_TESTNOTI = "测试通知";
    public static final String MA_ITEM_TESTNOTI_D = "发一条普通通知";
    public static final String MA_MSG_NOTI = "通知：";
    public static final String MA_TESTNOTI_T = "Reticle 测试";
    public static final String MA_TESTNOTI_C = "看到此通知，说明通知功能已开启";
    public static final String MA_ITEM_ENDTEST = "结束全部试验";
    public static final String MA_ITEM_ENDTEST_D = "清掉上面的试验通知";
    public static final String MA_MSG_ENDTEST = "已清掉试验通知";

    public static final String MA_SEC_DIAG = "诊断";
    public static final String MA_DIAG_TOOLS = "MCP 工具（给 RikkaHub 用）";
    public static final String MA_DIAG_LOADING = "读取中…";
    public static final String MA_DIAG_FAIL = "（读取失败）";
    public static final String MA_DIAG_COUNT_A = "共 ";
    public static final String MA_DIAG_COUNT_B = " 个工具";
    public static final String MA_DIAG_LOG = "最近日志";
    public static final String MA_LOG_NOFILE = "（还没有日志）";
    public static final String MA_LOG_EMPTY = "（日志为空）";
    public static final String MA_LOG_ERR = "（读日志失败）";

    // ═══════════ 底部导航（三页共用） ═══════════
    public static final String NAV_TAB1 = "功能";
    public static final String NAV_TAB2 = "配置";
    public static final String NAV_TAB3 = "高级";
    public static final String NAV_SELECTED = "，已选中";

    // ═══════════ 通知与通知栏 ═══════════
    public static final String NT_CH_STAT = "助手状态";
    public static final String NT_CH_STAT_D = "助手状态提示（静默）";
    public static final String NT_CH_TEST = "通知试验";
    public static final String NT_CH_TEST_D = "通知与原子岛能力试验（会提醒）";
    public static final String NT_CH_MEDIA = "助手状态·媒体";
    public static final String NT_CH_MEDIA_D = "以媒体样式承载助手状态（供原子随身听/原子岛）";
    public static final String NT_CH_LIVE = "助手实况";
    public static final String NT_CH_LIVE_D = "原生实况型通知试验（18 号·ProgressStyle）";
    public static final String NT_DEF_TITLE = "Reticle";
    public static final String NT_WD_TEXT = "无障碍看门狗运行中：被系统关闭后将自动恢复";
    public static final String NT_MEDIA_BTN = "打开 Reticle";
    public static final String NT_ST_IDLE = "Reticle·待命中";
    public static final String NT_ST_WORK = "Reticle·正在";
    public static final String NT_ST_OUT = "Reticle·输出中";
    public static final String NT_ST_BUSY = "忙";
    public static final String NT_BODY_ONLINE = "助手在线";
    public static final String NT_BODY_ONLINE_LAST = "助手在线 · 最近：";
    public static final String NT_BODY_LAST = "最近：";
    public static final String NT_BODY_WORKED = "已工作 ";

    public static final String NT_TOOL_SHOT = "截图";
    public static final String NT_TOOL_TAP = "点击";
    public static final String NT_TOOL_SWIPE = "滑动";
    public static final String NT_TOOL_KEY = "返回/主页";
    public static final String NT_TOOL_UI = "读界面";
    public static final String NT_TOOL_OCR = "认字";
    public static final String NT_TOOL_INPUT = "输入";
    public static final String NT_TOOL_LONGPRESS = "长按";
    public static final String NT_TOOL_SHELL = "执行命令";
    public static final String NT_TOOL_NOTIFY = "更新状态";
    public static final String NT_TOOL_BUILD = "打包";
    public static final String NT_TOOL_PING = "回铃";

    public static final String NT_BRIDGE_TITLE = "手机控制通道";
    public static final String NT_BRIDGE_TEXT = "通道已自动关闭（长时间未使用）。需要时重新启动通道服务即可恢复。";

    // ═══════════ 看门狗那一句（配置页显示） ═══════════
    public static final String WD_LINE_OFF = "看门狗：已关闭 —— 我们不碰系统设置。";
    public static final String WD_LINE_PREFIX = "当前状态：";
    public static final String WD_A11Y_ON = "无障碍已连接 ✓";
    public static final String WD_A11Y_OFF = "无障碍未开启 ✗";
    public static final String WD_A11Y_DISC = "无障碍在册但没接上 ⚠";
    public static final String WD_DISC_HINT = "；按「配置 → 无障碍」里的「修一次」重连，或到系统设置里把开关关一次再开";
    public static final String CF_A11Y_LINK_LEAD = "无障碍有「在册」和「接上」两件事：在系统名单里只算有资格；系统真的接上了（能读屏、能点）才算能用。上面那句「当前状态」两件事一起报——显示「在册但没接上 ⚠」时，用下面的「修一次」逼它重连。";
    public static final String WD_FIXES_A = "；已自动恢复 ";
    public static final String WD_FIXES_B = " 次";
    public static final String WD_FIXES_C = "（最近 ";
    public static final String WD_FIXES_D = "）";
    public static final String WD_CHECKED = "；最近检查 ";
    public static final String WD_FAIL = "；⚠ 最近一次自动恢复失败——请前往系统无障碍设置页手动开启";

    // ═══════════ 系统设置里露出的字都放在 res/values/strings.xml（app_name／a11y_desc／a11y_summary／fgs_watchdog_desc） ═══════════

    // ═══════════ 给助手的工具说明（高级页「诊断 → MCP 工具」里也逐条显示） ═══════════
    public static final String TL_PING = "回铃测试：确认 Reticle 在线（返回一句 hello）";
    public static final String TL_SHELL = "在你的手机上以 shell 身份执行一条命令，返回输出和退出码。exit=真实退出码；30 秒没跑完＝叫停进程并回 timeout=1；stdout／stderr 都空时回 ‹空›。★本机（实测环境）常遇到的坑：settings put 常不落地（改用 content insert，且值里不能带冒号）；cat -n 不可用（用 sed -n '1,$p' | nl 或 awk）。";
    public static final String TL_SHELL_CMD = "要执行的命令，例如 id、ls /sdcard";
    public static final String TL_SHOT = "截取当前屏幕（系统级截图，游戏画面也不黑屏）。返回图片与保存路径。";
    public static final String TL_TAP = "在屏幕上点一下。x、y 用 0~1 的比例（相对屏幕宽高）：屏幕正中＝0.5,0.5。";
    public static final String TL_TAP_X = "横坐标比例 0~1";
    public static final String TL_TAP_Y = "纵坐标比例 0~1";
    public static final String TL_TAP_MS = "按住时长毫秒（可选，默认 60）";
    public static final String TL_SWIPE = "从一点滑到另一点（坐标 0~1 比例）。";
    public static final String TL_SWIPE_X1 = "起点横坐标 0~1";
    public static final String TL_SWIPE_Y1 = "起点纵坐标 0~1";
    public static final String TL_SWIPE_X2 = "终点横坐标 0~1";
    public static final String TL_SWIPE_Y2 = "终点纵坐标 0~1";
    public static final String TL_SWIPE_MS = "滑动时长毫秒（可选，默认 300）";
    public static final String TL_KEY = "按系统按键：返回(back)/主页(home)/最近任务(recents)。";
    public static final String TL_KEY_VALUE = "back 或 home 或 recents";
    public static final String TL_UI = "读取当前界面的控件（文字/描述/边界/是否可点），用于定位要点哪里；读不到会如实说明（游戏等自绘界面读不到）。";
    public static final String TL_UI_MAX = "最多返回几个控件（可选，默认 120）";
    public static final String TL_OCR = "识别屏幕上或指定区域的文字（离线中文）；region 用 0~1 比例 x,y,w,h，可选。";
    public static final String TL_OCR_REGION = "区域 0~1 比例：x,y,w,h（可选，不填＝整屏）";
    public static final String TL_OCR_SRC = "指定图片路径（可选，默认当场截屏）";
    public static final String TL_INPUT = "往当前聚焦的输入框写入文字（中文也行）。输入框读不到回显时会走剪贴板粘贴，并尽量把你原来的剪贴板还原；微信这类界面可先给 x/y 让它点一下输入框。";
    public static final String TL_INPUT_TEXT = "要输入的文字";
    public static final String TL_LONGPRESS = "长按屏幕上某点（坐标 0~1 比例）。";
    public static final String TL_LONGPRESS_MS = "按住时长毫秒（可选，默认 800）";
    public static final String TL_NOTIFY = "发送/更新/清除一条手机通知（可选：原子岛试验／媒体样式／实况试验）。同 id 重复发送＝更新同一条。★report=<文本>＝把汇报写进眼线那条通知的正文（不新建第二条，只回助手）。";
    public static final String TL_NOTIFY_TITLE = "标题（默认：Reticle）";
    public static final String TL_NOTIFY_CONTENT = "内容文本";
    public static final String TL_NOTIFY_ID = "通知编号：同编号＝更新同一条（默认 1）";
    public static final String TL_NOTIFY_ONGOING = "1＝常驻不可划掉（默认 1）";
    public static final String TL_NOTIFY_CLEAR = "1＝清除该编号通知";
    public static final String TL_NOTIFY_ISLAND = "原子岛场景名（如 HEALTH_REGISTER；填了就走原子岛）";
    public static final String TL_NOTIFY_OP = "原子岛操作：0 创建/1 更新/2 结束（默认 0）";
    public static final String TL_NOTIFY_MEDIA = "1＝以媒体样式发送（供原子随身听上岛）";
    public static final String TL_NOTIFY_PLAYING = "媒体态：1＝播放中（默认）/0＝暂停";
    public static final String TL_NOTIFY_PROMOTED = "1＝实况型通知试验（ProgressStyle）";
    public static final String TL_NOTIFY_PROGRESS = "实况进度 0~100（默认 60）";
    public static final String TL_NOTIFY_REPORT = "助手汇报文本：填了＝写进眼线那条通知的正文（不新建第二条）";
    public static final String TL_BUILD = "构建安装包（用设备侧工具链；先 action=check 看环境是否就绪）。action：check＝环境自检（缺什么／从哪拿）；start＝开始构建（后台运行，约 1~3 分钟）；status＝查看进度（默认）；install＝安装已构建的包；help＝查看说明。";
    public static final String TL_BUILD_ACTION = "check / start / status / install / help（默认 status）";
    public static final String TL_BUZZ = "震动提醒：使手机震动一次，表示「有消息找你」。仅震动——不发声、不弹出通知、不点亮屏幕。";
    public static final String TL_BUZZ_MS = "震动时长毫秒（可选，默认 300；上限 10000）";
    public static final String CF_LOG_SEC = " 秒前";
    public static final String CF_LOG_MIN = " 分钟前";
    public static final String CF_LOG_HOUR = " 小时前";
    public static final String NT_ALBUM = "Reticle·助手状态";
    public static final String NT_ISLAND_STATUS = "胶囊";

    // ═══════════ 助手回话 · 工具结果（RP_ = reply；界面上看不到，是给助手看的那几句话） ═══════════
    public static final String RP_HELLO = "hello：Reticle 在线（MCP 回铃成功）";

    // buzz
    public static final String RP_BUZZ_FMT = "已震动 %dms（纯震动：不发声／不弹通知／不亮屏；静音穿透＝%s）";
    public static final String RP_BUZZ_ON = "开";
    public static final String RP_BUZZ_OFF = "关";
    public static final String RP_BUZZ_NO_VIB = "震动失败：本机没有震动器";
    public static final String RP_BUZZ_ERR = "震动异常：";
    public static final String TL_BUZZ_DRY = "dry：1＝只验通路、不震动（自检／健康巡检用；默认 0＝真震）";
    public static final String RP_BUZZ_DRY = "自检 dry：通路 ok，未震动（%dms）——只有助手显式 buzz 才会真震";

    // 通知 / 原子岛 / 媒体 / 实况 / 汇报
    public static final String RP_REPORT = "已汇报（写进眼线那条通知的正文）：";
    public static final String RP_NT_CLEARED = "已清除通知 #";
    public static final String RP_NT_CLEAR_ERR = "清除失败：";
    public static final String RP_ISLAND_OK = "已发原子岛 #";
    public static final String RP_ISLAND_OP = " op=";
    public static final String RP_ISLAND_SCENE = " scene=";
    public static final String RP_ISLAND_ERR = "原子岛发送失败：";
    public static final String RP_MEDIA_OK = "已发媒体通知 #";
    public static final String RP_MEDIA_ERR = "媒体通知失败：";
    public static final String RP_PROMO_OK = "已发实况通知 #";
    public static final String RP_PROMO_ERR = "实况通知失败：";
    public static final String RP_SEND_OK = "已发通知 #";
    public static final String RP_SEND_ERR = "发送失败：";

    // 手势 / 输入 / 截图
    public static final String RP_TAP_FMT = "已点按（本机执行 %dms）";
    public static final String RP_SWIPE_FMT = "已滑动（本机执行 %dms）";
    public static final String RP_LONGPRESS_FMT = "已长按（本机执行 %dms）";
    public static final String RP_KEY_OK = "已按键";
    public static final String RP_KEY_BAD = "key 只能是 back/home/recents";
    public static final String RP_INPUT_FMT = "已输入：%s（方式 %s）";
    public static final String RP_OP_ERR = "操作失败：";
    public static final String RP_SHOT_FMT = "已截图：%s（%dx%d，%dms）";
    public static final String RP_SHOT_ERR = "截图失败：";

    // Shizuku / shell
    public static final String RP_SHX_BINDER_ERR = "错误：Shizuku binder 不可用（确认 Shizuku 在运行）";
    public static final String RP_SHX_ABSENT = "Shizuku 不在（binder 不可用）";
    public static final String RP_SHX_SKIP_COLON = "跳过：本 ROM 的 content insert 写不进含冒号的值（多服务列表必然含冒号）";
    public static final String RP_SHX_LOCAL_OK = "已调用（无异常）";
    public static final String RP_EXEC_ERR = "执行失败：";
    public static final String RP_EXEC_EXC = "执行异常：";
    public static final String RP_BINDER_MISS = "binder 未到：已请求重新获取，请稍后重试（若仍不行，重开无障碍开关或重启应用）";
    public static final String RP_NO_AUTH = "未授权";
    public static final String RP_NO_AUTH_REQ = "未授权->已请求：请在弹窗点『允许』后重试（若未弹窗，到 Shizuku 应用里恢复）";
    public static final String RP_AUTH_OK = "shizuku 已授权";
    public static final String RP_CLIP_TAIL1 = "\n...(截断 ";
    public static final String RP_CLIP_TAIL2 = " 字符)";

    // 无障碍
    public static final String RP_A11Y_NOCONN = "无障碍服务未连接（Reticle 进程在、但系统没把无障碍接上）：先等十几秒，通常会自动接回；仍不行请打开 Reticle，在「配置 → 无障碍」点「修一次」，或到系统设置里把 Reticle 的无障碍开关关一次再开。";
    public static final String RP_ST_CONN = "｜连接：";
    public static final String RP_ST_CONN_OK = "已接上 ✓";
    public static final String RP_ST_CONN_DISC = "在册但没接上 ⚠（等它自己接，或点「修一次」）";
    public static final String RP_ST_CONN_NOT = "不在册";
    public static final String RP_ST_CONN_UNK = "读不到";
    public static final String A11Y_REP_ALREADY = "已是连接态，不用修。";
    public static final String A11Y_REP_BUSY = "正在修，稍等。";
    public static final String A11Y_REP_TOOSOON = "刚断开没多久，先让它自己接（十几秒）；要立刻修就点按钮。";
    public static final String A11Y_REP_NOREAD = "读不到系统设置，修不了（可能缺 WRITE_SECURE_SETTINGS 权限）。";
    public static final String A11Y_REP_OK = "已接回 ✓（把 Reticle 那条从名单里删掉再写回，系统重新绑上了）";
    public static final String A11Y_REP_WROTE_NO_CONN = "名单已写回（在册 ✓），但系统还没接上——请到系统设置里把 Reticle 的无障碍开关关一次再开，或重启手机。";
    public static final String A11Y_REP_WRITEFAIL = "系统名单没写进去（被拦下了）——请到系统设置里把 Reticle 的无障碍开关关一次再开，或重启手机。";
    public static final String A11Y_REP_NOSTRIP = "删名那一步没落地（系统把名单又写回来了）——这条对本机无效；请手动关开一次无障碍开关，或重启手机。";
    public static final String A11Y_REP_NOGO = "删名→写回都做了，系统还是没接上——请到系统设置里把 Reticle 的无障碍开关关一次再开，或重启手机。";
    public static final String A11Y_REP_ERR = "修复出错：";
    public static final String RP_UI_EMPTY = "读不到界面：无障碍未连接，或当前是游戏/自绘画面（没有可读控件树）；可改用 shot 截图或 ocr 认字。";
    public static final String RP_INPUT_NOFOCUS = "没有找到输入框：请先点一下要输入的地方（让它获得焦点）";
    public static final String RP_INPUT_FAIL = "输入失败：该应用可能不吃无障碍输入（退路可用 MCP shell 的命令行输入）";

    // build（路径留在代码里，这里只放话）
    public static final String RP_BUILD_START = "已开始构建（约1~3分钟），用 action=status 看进度";
    public static final String RP_BUILD_INSTALL = "已开始安装（约半分钟），完成后 ping 验证版本";
    public static final String RP_BUILD_HELP = "build：check=环境自检 / start=开始构建 / status=看进度 / install=装刚打好的包";
    public static final String RP_BUILD_DIR = "工具链目录：";
    public static final String RP_BUILD_LOG = "构建日志：";
    public static final String RP_BUILD_NOLOG = "还没有构建日志（先 action=check 看环境，再 action=start）";
    // 注：清单 329「（还没有日志）」/ 330「（日志为空）」/ 331「（读日志失败）」＝界面用，已在上方 MA_LOG_NOFILE / MA_LOG_EMPTY / MA_LOG_ERR。

    // 找图 / 认数字（TaskEngine 的 num 那一套）
    public static final String RP_NODE_MISSING = "start 节点缺失: ";
    public static final String RP_INK_BAD_FMT = "区域墨迹占比异常(%d%%)——区域选得不对？";
    public static final String RP_TPLS_BAD_FMT = "tplsave 标签数(%d)与切出字形数(%d)不符：切分失败，别硬存";
    public static final String RP_NO_GLYPH = "区域内没切到字形";
    public static final String RP_EDGE = "贴边";
    public static final String RP_EDGE_WARN = "贴边(裁剪可能不全):";
    public static final String RP_TPL_EMPTY = "模板集为空：先按标签采模板（tplsave）到 tpl/num/";
    public static final String RP_LIKE_FMT = "最像'%s'仅%s<%s";
    public static final String RP_TOO_SIM_FMT = "'%s'与'%s'太像(差%s<%s)";
    public static final String RP_GLYPH_UNK_FMT = "字形#%d未识别：%s";
    public static final String RP_DIAG_FMT = "第%d位最像'%s'/次'%s'，两位差%s";

    // ═══════════ 38 号单（2026-10-10）· 新增：状态可见性 / 自测 / 帮助 ═══════════
    public static final String TL_STATUS = "一句话报状态：版本、三件套（无障碍在册＋连接态／所有文件访问／自检结论）、看门狗累计救回次数与最近时间、前台包名、MCP 端口、日志尾几行。";
    public static final String TL_HELP = "工具目录与用法（只回助手，界面上不显示）：不带参数＝目录（一行一个工具，名字 ＋ 半句用途）；tool=<名字>＝这一个工具的完整说明与参数。工具失败时，回话末尾会带 help tool=<名> 的指路。";
    public static final String TL_ACTTEST = "动作类自测靶：开 Reticle 自己的小窗口，依次试 tap／长按／输入／滑动／返回并回报每步结果（会短暂占用前台，只在需要自测动作时用）。";

    public static final String RP_SHELL_EMPTY = "‹空›（命令没有任何输出）";
    public static final String RP_SHELL_TIMEOUT_FMT = "  timeout=1（%d 秒没跑完，进程已叫停）";
    public static final String RP_BUILD_MISSING = "缺件（补齐再出包）：";
    public static final String RP_BUILD_ENVOK = "环境齐 ✓（aapt2／android.jar／物料关键 jar 都在）";
    /** 59 号单 · 构建配置与自检（给陌生人的指路）。 */
    public static final String RP_BUILD_WHERE = "\n——缺什么／从哪拿／路径怎么配：见仓库 BUILD.md「路径与物料」一节；改完再跑一次 build action=check 复检。\n";
    public static final String RP_INSTALL_MISSING = "装机不可用：";
    public static final String RP_CHK_HEAD = "== 构建环境自检（build action=check）==";
    public static final String RP_CHK_CONF = "配置文件：";
    public static final String RP_CHK_CONF_YES = "（存在；下面是它生效后的值）";
    public static final String RP_CHK_CONF_NO = "（不存在，全部用内置默认＝作者机路径；不是你的路径就照仓库 build/build.conf.example 写一份放到这里）";
    public static final String RP_CHK_OK = "出包环境：就绪 ✓（start 可用）";
    public static final String RP_CHK_MISS = "出包环境：不可用，缺件如下 ↓";
    public static final String RP_CHK_INSTALL_OK = "装机环境：就绪 ✓（install 可用）";
    public static final String RP_CHK_INSTALL_MISS = "装机环境：不可用，缺件如下 ↓";
    public static final String RP_CHK_QUOTE = "⚠ 配置项 %s 含单引号或换行，会破坏脚本调用：请改掉";
    public static final String RP_INSTALL_NOTE = "正在替换 App：连接会断 1 秒级（通常一两秒、自己回来）；恢复后用 status 复查，或读 /sdcard/Reticle/logs/install-last.txt。安装已在后台开始。";

    /** 39 号单 · 断流人话（请求超时／服务端出意外时回给助手的那句，替代英文堆栈）。 */
    public static final String RP_BUSY_TIMEOUT = "请求处理超时（超过 60 秒）";
    public static final String RP_BUSY_ERR = "服务端处理时出意外中断";
    public static final String RP_BUSY_PREFIX = "这条请求没跑完：";
    public static final String RP_BUSY_TAIL = "。常见于设备端正在装包/换 App（连接会断，1 秒级、自己回来，属正常）：稍等片刻用 status（或 /selfcheck）复查即可；装机时若无人回话，重新打开一次 App 或发一条广播即可叫醒它。";

    public static final String RP_ST_TITLE = "Reticle ";
    public static final String RP_ST_A11Y = "无障碍：";
    public static final String RP_ST_IN = "在册 ✓";
    public static final String RP_ST_OUT = "不在册 ✗";
    public static final String RP_ST_SW = "｜看门狗开关=";
    public static final String RP_ST_ON = "开";
    public static final String RP_ST_OFF = "关";
    public static final String RP_ST_MANAGE = "所有文件访问：";
    public static final String RP_ST_ALLOW = "allow ✓";
    public static final String RP_ST_DENY = "未允许 ✗";
    public static final String RP_ST_WD = "看门狗：";
    public static final String RP_ST_SHX = "Shizuku：binder=";
    public static final String RP_ST_FG = "前台：";
    public static final String RP_ST_MCP = "MCP：127.0.0.1:";
    public static final String RP_ST_NOTOKEN = "（token 不外露）";
    public static final String RP_ST_HNAME = "，请求头名称：";   // 51 号单：状态里带出当前头名（默认 Authorization）
    public static final String RP_ST_CHK = "自检：";
    public static final String RP_ST_LOG = "看门狗日志尾 3 行：";
    public static final String RP_ST_UNKNOWN = "（读不到）";

    // ═══════════ 41 号单（2026-10-10）· 内置下载工具（download / download_status） ═══════════
    public static final String TL_DOWNLOAD = "下载一个文件到手机（不用开浏览器，也不依赖 Shizuku）。小文件（默认 8MB 以内）当场下完并回报路径＋sha256；大文件立刻返回任务号，下载转后台继续，进度显示在通知栏，用 download_status 查。只能存到 Reticle 的 dl 目录或 /sdcard/Download（别的目录写不进去，会当面回人话）。";
    public static final String TL_DOWNLOAD_URL = "下载地址（http/https 完整网址）";
    public static final String TL_DOWNLOAD_DIR = "存到哪个目录（可选，默认 Reticle 的 dl 目录；只允许 Reticle 的 dl 目录、/sdcard/Download）";
    public static final String TL_DOWNLOAD_NAME = "存成什么文件名（可选，默认取网址最后一段）";
    public static final String TL_DOWNLOAD_SHA = "sha256 校验值（可选）：下完不一致会明确报错";
    public static final String TL_DOWNLOAD_HEADERS = "自定义请求头（可选，防盗链站用）：如 {\"Referer\":\"https://…\"}，或「Referer: https://…」一行一条";
    public static final String TL_DOWNLOAD_UNZIP = "unzip：1＝下完顺带解包到同名文件夹（默认 0）";
    public static final String TL_DOWNLOAD_FORCE = "force：1＝覆盖同名文件、突破 2GB 上限（默认 0）";
    public static final String TL_DOWNLOAD_STATUS = "查下载进度：给 id 报那一条（进度／速度／已下与总量／完成路径＋sha256）；不给 id 就列出最近的下载任务。";
    public static final String TL_DOWNLOAD_STATUS_ID = "任务 id（download 起任务时给的那个；可选）";

    // 下载进度那条通知（实况进度样式）
    public static final String DL_NT_TITLE = "正在下载 ";
    public static final String DL_NT_DONE_T = "下载完成 ";
    public static final String DL_NT_FAIL_T = "下载失败 ";
    public static final String DL_NT_LINE_FMT = "已下 %s / %s（%d%%）· %s";
    public static final String DL_NT_LINE_NOSIZE_FMT = "已下 %s · %s";
    public static final String DL_NT_SPEED_UNKNOWN = "速度--";
    public static final String NT_TOOL_DL = "下载";

    // 下载回话（给助手看的那几句）
    public static final String RP_DL_BAD_URL = "下载失败：url 得是 http/https 开头的完整网址";
    public static final String RP_DL_BAD_DIR = "下载失败：这个目录不在允许清单里（只允许 Reticle 的 dl 目录、/sdcard/Download）";
    public static final String RP_DL_NOWRITE_FMT = "这个目录我写不进去（系统权限）：%s\n请改用 /sdcard/Reticle/dl（dir 不填就是它）或 /sdcard/Download。";
    public static final String RP_DL_HTTP_FMT = "下载失败：源站回了 HTTP %d（%s）";
    public static final String RP_DL_PROBE_FAIL_FMT = "下载失败：连不上或探测不到这个地址（%s）";
    public static final String RP_DL_EXISTS_FMT = "这个文件已经在：%s（%s）\nsha256=%s\n要重下就加 force=1";
    public static final String RP_DL_SHA_UNKNOWN = "（文件较大，没现算）";
    public static final String RP_DL_TOO_BIG_FMT = "这份有 %s，超过 2GB 上限：确认要下就加 force=1";
    public static final String RP_DL_OK_FMT = "下好了：%s（%s，%d 秒）\nsha256=%s";
    public static final String RP_DL_SHA_OK = "sha256：与给定值一致 ✓";
    public static final String RP_DL_SHA_UNCHECKED = "sha256：没给校验值，未核对（上面那串就是实际值）";
    public static final String RP_DL_SHA_BAD_FMT = "sha256 不一致（实际 %s，给定 %s）";
    public static final String RP_DL_SHA_BAD_LINE_FMT = "没下对：%s（%s，%d 秒）\n实际 sha256=%s\n给定 sha256=%s\n——文件留在原处，别当成功用";
    public static final String RP_DL_START_FMT = "已后台下载：任务 %s（%s）\n进度看通知栏；查进度：download_status id=%s";
    public static final String RP_DL_TO_BG_FMT = "这份偏大或下得慢（已下 %s），已转后台继续：任务 %s\n进度看通知栏；查进度：download_status id=%s";
    public static final String RP_DL_ERR_FMT = "下载失败：%s";
    public static final String RP_DL_LEN_FMT = "长度不符（实收 %s，源站声明 %s）";
    public static final String RP_DL_SIZE_UNKNOWN = "大小未知";
    public static final String RP_DL_UNZIP_FMT = "已解包 %d 个文件 → %s";
    public static final String RP_DL_UNZIP_ERR_FMT = "文件下好了，但解包失败：%s";
    public static final String RP_DLS_TITLE = "下载任务（最近的在前）：\n";
    public static final String RP_DLS_NONE = "还没有下载任务。";
    public static final String RP_DLS_NOID_FMT = "没有这个任务号：%s（download_status 不带 id 可看全部）";
    public static final String RP_DLS_DONE = "已完成";
    public static final String RP_DLS_ERR = "失败";
    public static final String RP_DLS_RUN = "进行中";
    public static final String RP_DLS_QUEUED = "排队中";
    public static final String RP_DLS_FIN = "完成";

    // ═══════════ 46 号单（2026-10-11）· help 精简分层：无参＝目录；tool=<名>＝单工具详情 ═══════════
    // 口径：本页只管「怎么用」；每个工具的说明以它自己的 TL_* 常量为唯一来源（一处定义、多处引用）。
    public static final String TL_HELP_TOOL = "工具名（可选）：填了＝只回这一个工具的详情；不填＝回目录";
    public static final String HP_INDEX_HEAD = "Reticle 工具（共 %d 个，一行一个）：\n";
    public static final String HP_INDEX_POINT = "某工具详情：help tool=<名字>（例 help tool=scroll_to_text）\n";
    public static final String HP_INDEX_TAIL = "另：/selfcheck＝免 token 自检口（GET 127.0.0.1:<端口>/selfcheck；当前端口见 status）；槽位命令＝写 /sdcard/Reticle/in/slot_N.cmd（末行 z=1）→ out/，子命令 wd／front／a11ytest／mcptest。\n";
    public static final String HP_ITEM_LINE = "· %s —— %s\n";
    public static final String HP_TOOL_HEAD = "【%s】%s\n";
    public static final String HP_TOOL_ARGS = "参数：\n";
    public static final String HP_TOOL_ARG = "  - %s：%s%s\n";
    public static final String HP_TOOL_REQ = "（必填）";
    public static final String HP_TOOL_NOARG = "（无参数）\n";
    public static final String HP_TOOL_NONE = "没有这个工具：『%s』。可用的名字：\n";
    public static final String HP_POINT_PLAIN = "（用法与参数：help tool=%s）";
    public static final String HP_POINT = "\n" + HP_POINT_PLAIN;

    // ═══════════ 44 号单（2026-10-11）· 按文字点 / 等页面 / 滚动：工具说明与回话 ═══════════
    public static final String TL_TAP_TEXT = "按文字/描述/ID 找控件并点它：会自己往上找可点的祖先，用控件级点击（不靠坐标）。界面没有控件树（微信、游戏等自绘界面）会自动改用截图认字兜底点中心。找不到就报人话，绝不乱点。★别盲信节点的 bounds（应用可能报怪值），能控件级点就控件级点。";
    public static final String TL_TAP_TEXT_TEXT = "要点的文字（默认包含匹配；exact=1 则完全相等）";
    public static final String TL_TAP_TEXT_DESC = "按控件描述 content-desc 找";
    public static final String TL_TAP_TEXT_ID = "按控件 ID 找（可只写后半段，如 send_btn）";
    public static final String TL_TAP_TEXT_INDEX = "匹配到多个时点第几个（默认 0＝第一个）";
    public static final String TL_TAP_TEXT_TIMEOUT = "找不到就等，最多等多少毫秒（默认 1500）";
    public static final String TL_TAP_TEXT_DURATION = "按住时长毫秒（默认 80）";
    public static final String TL_TAP_TEXT_EXACT = "1＝文字完全相等（默认包含匹配）";
    public static final String TL_TAP_TEXT_EXPECT = "点完等这段文字出现，出现了即算点中";
    public static final String TL_TAP_TEXT_EXPECT_PKG = "先确认前台是这个包名，不是就拒绝动手（防点错应用）";
    public static final String TL_TAP_TEXT_OCR = "0＝关掉截图认字兜底（默认开）";
    public static final String TL_TAP_TEXT_WAIT = "点完最多等多少毫秒看反应（默认 900）";
    public static final String TL_WAIT_TEXT = "等某段文字出现或消失（先读控件树，读不到就截图认字）。用它代替「sleep+反复截图」。";
    public static final String TL_WAIT_TEXT_TEXT = "等的文字";
    public static final String TL_WAIT_TEXT_STATE = "appear（默认，等出现）/ disappear（等消失）";
    public static final String TL_WAIT_TEXT_TIMEOUT = "最多等多少毫秒（默认 5000）";
    public static final String TL_WAIT_STABLE = "等画面稳定：控件树连续若干毫秒没变化就返回（默认 600ms）。点完/滑完用它等页面停下来，不用猜 sleep。";
    public static final String TL_WAIT_STABLE_QUIET = "连续多少毫秒没变化算稳定（默认 600）";
    public static final String TL_WAIT_STABLE_TIMEOUT = "最多等多少毫秒（默认 5000）";
    public static final String TL_SCROLL = "滚动列表/页面：只走屏幕中段，自动避开底部手势区与边缘返回区；可先校验前台包名。direction＝down（默认，往下看后面的内容）/up/left/right。";
    public static final String TL_SCROLL_DIR = "down（默认）/ up / left / right";
    public static final String TL_SCROLL_DIST = "滚动距离，屏幕比例 0.05~0.6（默认 0.5）";
    public static final String TL_SCROLL_PKG = "先确认前台是这个包名，不是就拒绝";

    public static final String RP_TT_NEED = "要给我 text／desc／id 里至少一个，我才知道点哪儿";
    public static final String RP_TT_NOTFOUND = "没找到『%s』：等了 %dms，控件树里没有、截图认字也没认到——我没点，先不乱点";
    public static final String RP_TT_CLICK_FAIL = "找到目标了，但点击没下发成功（被系统拦了？）";
    public static final String RP_TT_WRONG_PKG = "前台不是目标应用：要 %s，实际 %s——已拒绝，没动手";
    public static final String RP_TT_PKG_UNK = "读不到";
    public static final String RP_TT_DESC_PREFIX = "描述:";
    public static final String RP_TT_FMT = "已按文字点『%s』（%s）%s";
    public static final String RP_TT_TAIL_EXPECT = "，并且『%s』已出现（+%dms）";
    public static final String RP_TT_TAIL_CHANGED = "，页面已变化（+%dms）";
    public static final String RP_TT_TAIL_NOCHANGE = "，但页面没变化——可能没点中，建议确认一下";
    public static final String RP_SCROLL_FMT = "已滚动 %s（本机执行 %dms）%s";
    public static final String RP_WAIT_OK_FMT = "等到了：%s（%s，方式 %s，用时 %dms）";
    public static final String RP_STABLE_OK_FMT = "画面已稳定（静默 %dms，用时 %dms）";
    public static final String RP_WAIT_APPEAR_TIMEOUT = "等『%s』出现超时（%dms）：控件树里没有、截图认字也没认到";
    public static final String RP_WAIT_GONE_TIMEOUT = "等『%s』消失超时（%dms）：它还在";
    public static final String RP_STABLE_TIMEOUT = "等稳定超时（%dms）：画面一直在动";
    public static final String RP_TAP_FMT2 = "已点按（本机执行 %dms）%s";
    public static final String RP_INPUT_CLIP_BACK = "（用完已把你原来的剪贴板还原）";
    public static final String RP_VIA_ANCESTOR = "在可点的父控件上点，不靠坐标";
    public static final String RP_VIA_ACTION = "控件级点击，不靠坐标";
    public static final String RP_VIA_GESTURE = "控件中心手势点";
    public static final String RP_VIA_GESTURE_ANC = "父控件中心手势点";
    public static final String RP_VIA_OCR = "截图认字定位后点中心（这类界面没有控件树）";
    public static final String TL_INPUT_X = "先点这个位置（可选，屏幕比例 0~1）——微信这类没有可读输入框的界面，用它先聚焦";
    public static final String TL_INPUT_Y = "先点这个位置的纵坐标（可选，0~1）";
    public static final String TL_INPUT_PKG = "先确认前台是这个包名，不是就拒绝";
    public static final String RP_INPUT_FMT2 = "已输入（界面读不到回显，按画面确认；方式 %s）";

    // ═══════════ 44.5 号单（2026-10-11）· 剪贴板直贴 / 滚找目标 / 批量动作 / 窗口探针 ═══════════
    public static final String TL_PASTE = "剪贴板直贴：把内容写进剪贴板，再贴到当前输入框（治微信这类「不支持无障碍输入」的应用）。会先找聚焦输入框；页面上没有可读输入框时用 x/y 先点一下。贴完读回校验。★内部按 ①控件级粘贴 ②长按输入框点「粘贴」菜单 ③按键 279 依次试；界面慢时可能两通道各贴一次——贴重要内容后请用 ui／ocr 读回确认。★原来的剪贴板内容读不到（系统不让后台应用读），restore 多数情况下还原不了。";
    public static final String TL_PASTE_TEXT = "要贴进去的文字";
    public static final String TL_PASTE_X = "先点这个位置（可选，屏幕比例 0~1）：没有可读输入框的界面（微信）用它先聚焦";
    public static final String TL_PASTE_Y = "先点这个位置的纵坐标（可选，0~1）";
    public static final String TL_PASTE_RESTORE = "restore：1（默认）＝贴完还原你原来的剪贴板；0＝把这次的内容留在剪贴板";
    public static final String TL_PASTE_PKG = "先确认前台是这个包名，不是就拒绝";

    public static final String TL_SCROLL_TO_TEXT = "滚着找目标：边滚边找（控件树优先，读不到树就截图认字），找到就告诉你在第几屏、要不要顺手点（tap=1）。滚到尽头/滚满上限/超时都如实回报，绝不在没找到时乱点。";
    public static final String TL_SCROLLTO_TEXT = "要找的文字（默认包含匹配）";
    public static final String TL_SCROLLTO_DESC = "按控件描述 content-desc 找（可选）";
    public static final String TL_SCROLLTO_ID = "按控件 ID 找（可选）";
    public static final String TL_SCROLLTO_EXACT = "1＝文字完全相等（默认包含匹配）";
    public static final String TL_SCROLLTO_INDEX = "匹配到多个时点第几个（默认 0＝第一个）";
    public static final String TL_SCROLLTO_DIR = "往哪边找：down（默认，往下翻）/ up";
    public static final String TL_SCROLLTO_MAX = "最多滚几屏（默认 10，上限 40）";
    public static final String TL_SCROLLTO_TIMEOUT = "最多花多少毫秒（默认 12000）";
    public static final String TL_SCROLLTO_TAP = "tap：1＝找到就用 tap_text 点它（点完验货）；默认 0＝只找到、不点";
    public static final String TL_SCROLLTO_PKG = "先确认前台是这个包名，不是就拒绝";

    public static final String TL_DO_CHAIN = "批量动作：一次提交多步，设备端连着执行，省来回。每步各带自己的后置条件（expect）：动作做完就等它出现，等不到＝当场停在那一步，并回「第几步、什么命令、当时屏幕现状」。★界面一变就退回单步（这工具不是「界面不可预测也硬批量」）。";
    public static final String TL_DO_CHAIN_STEPS = "步骤数组，每步一个对象：{\"act\":\"tap_text\",\"text\":\"发送\",\"expect\":\"已发送\",\"timeout_ms\":3000}。act＝动作名（tap/tap_text/input/paste/longpress/swipe/key/scroll/wait_text/wait_stable/ui/ocr/shot），act 之外的字段原样当该动作的参数；expect＝本步后置条件（可选）；timeout_ms＝等 expect 的上限（默认 5000）";
    public static final String TL_DO_CHAIN_STOP = "stop_on_error：1（默认）＝任一步失败或 expect 超时就停；0＝失败也继续往下走（每步结果都记在 detail 里）";
    public static final String TL_DO_CHAIN_OBSERVE = "observe：在第 N 步（1 起算）之后把当时的屏幕现状（前台包名＋可见文字）一并回给你；0＝不额外回";

    public static final String TL_WINDOWS = "列出当前无障碍看得见的窗口（类型/包名/边界/是否整屏/是否活动），用来查分屏·小窗·悬浮窗——也是分屏下坐标排查的探针。split=1 表示当前可能是分屏/多窗口。★多窗口时 ui 只给「有焦点的那个窗口」的节点：要操作另一个窗口，先点它一下，或用 tap_text 的认字兜底。";

    public static final String RP_PASTE_NEEDTEXT = "要给我 text（要贴进去的内容）";
    public static final String RP_PASTE_NOFOCUS = "没找到输入框：这次没动手。可给我输入框的位置（x/y，屏幕比例 0~1）让我先点一下再贴";
    public static final String RP_PASTE_NOCLIP = "写剪贴板失败（被系统拦了），这次没贴";
    public static final String RP_PASTE_FAIL = "三条粘贴通道都试过了（控件粘贴／长按菜单／按键粘贴），但没验到内容进去——可能没点中输入框";
    public static final String RP_PASTE_FMT = "已贴入：%s（方式 %s）%s";
    public static final String RP_PASTE_FMT2 = "已贴入（界面读不到回显，按画面确认；方式 %s）%s";

    public static final String RP_SCROLLTO_NOTFOUND = "滚了 %d 屏也没找到『%s』：%s——我没点";
    public static final String RP_SCROLLTO_END = "已经滚到尽头了（画面不再变化）";
    public static final String RP_SCROLLTO_TIMEOUT = "滚到时间上限（%dms）";
    public static final String RP_SCROLLTO_MAXSCROLL = "滚满了 %d 屏";
    public static final String RP_SCROLLTO_NOSCROLL = "这个界面滚不动（下发滚动没成功）";
    public static final String RP_SCROLLTO_FMT = "滚了 %d 屏找到了『%s』（%s）%s";

    public static final String RP_CHAIN_NEED = "要给我 steps（步骤数组），我才能连着走";
    public static final String RP_CHAIN_BAD = "steps 不是合法的 JSON 数组：每步写成 {\"act\":\"tap_text\",\"text\":\"发送\",\"expect\":\"已发送\"}";
    public static final String RP_CHAIN_NOTALLOW = "这一步的动作不在允许清单里（链里只允许 tap／tap_text／input／paste／longpress／swipe／key／scroll／wait_text／wait_stable／ui／ocr／shot）——已停下";
    public static final String RP_CHAIN_ACTFAIL = "第 %d 步（动作 %s）没执行成功";
    public static final String RP_CHAIN_EXPFAIL = "第 %d 步做完后没等到『%s』（等了 %dms）";
    public static final String RP_CHAIN_FMT = "链走完：%d 步（本机共 %dms）";
    public static final String RP_VIA_PASTE_NODE = "控件级粘贴（无障碍）";
    public static final String RP_VIA_PASTE_MENU = "长按输入框→点「粘贴」菜单";
    public static final String RP_VIA_PASTE_KEY = "按键粘贴（279）";
    public static final String RP_PASTE_CLIP_UNKNOWN = "（原来的剪贴板读不到、未能还原——系统不让后台应用读剪贴板；现在剪贴板里是刚贴进去的内容）";
    public static final String RP_SCROLLTO_TAPPED = "，并顺手点了它";
    public static final String RP_SCROLLTO_NOTAP = "（只找了、没点；要顺手点就加 tap=1）";
    public static final String RP_CHAIN_STOP_FMT = "没走完：停在第 %d 步（%s）；当时前台 %s，屏上文字：%s";
    // ═══════════ 51 号单（2026-10-12）· 令牌与链接管理 ═══════════
    /** 官网地址（一键复制用；与「复制链接地址」区分：那个复制的是本机服务地址） */
    public static final String CF_OFFICIAL_URL = "https://rikka-ai.com";
    public static final String CF_BTN_OFFICIAL = "复制官网地址";
    public static final String CF_HINT_OFFICIAL = "复制的是 RikkaHub 官网（用浏览器打开就能下载）——不是上面那个本机服务地址";
    public static final String CF_MSG_OFFICIAL_OK = "已复制官网地址：https://rikka-ai.com（在浏览器里打开，就是 RikkaHub 的下载页）";
    public static final String CF_MSG_OFFICIAL_ERR = "复制官网地址失败：";

    public static final String CF_SEC_TOKEN = "令牌与地址";
    public static final String CF_TOKEN_WHAT = "令牌＝只发给本机的通行证：AI 助手每次找 Reticle 都要带上它，对不上就不理。要是令牌被别人拿到、他又能访问你手机的 127.0.0.1，他也能指挥 Reticle。";
    public static final String CF_TOKEN_HOW = "怀疑被人看到、或者曾经发给过别人：点「重新生成令牌」——旧令牌立刻作废，再到 RikkaHub 里把请求头值重新粘一次就恢复了。";
    public static final String CF_LINK_WHAT = "服务地址＝Reticle 在本机的地址（只有本机能连）。换一个端口就是换一个新地址，换完 RikkaHub 里的服务器地址也要跟着改。";
    public static final String CF_BTN_TOKEN_NEW = "重新生成令牌";
    public static final String CF_HINT_TOKEN_NEW = "旧令牌立刻失效；换完要到 RikkaHub 里重新粘一次「请求头 · 值」";
    public static final String CF_TOKEN_ASK = "确定重新生成令牌吗？\n\n① 旧令牌立刻作废，正在用旧令牌的连接会断；\n② 然后到上面点「复制请求头值（令牌）」，在 RikkaHub 的「请求头 · 值」里重新粘一次，就恢复了。";
    public static final String CF_MSG_TOKEN_NEW = "已生成新令牌（旧的已失效）→ 点「复制请求头值（令牌）」，到 RikkaHub 里重新粘一次";
    public static final String CF_BTN_HNAME_SET = "修改请求头名称";
    public static final String CF_HINT_HNAME_SET = "默认 Authorization；改了以后，RikkaHub 里的「请求头 · 名称」要填一样的";
    public static final String CF_HNAME_DLG_T = "修改请求头名称";
    public static final String CF_HNAME_DLG_ASK = "只填名称本身，例如 Authorization 或 X-Reticle-Token；改完请到 RikkaHub 的「请求头 · 名称」里填一样的名字。";
    public static final String CF_HNAME_BAD = "这个名字不能用作请求头：只允许英文字母、数字和「-」，并且要以字母开头";
    public static final String CF_MSG_HNAME_SET_A = "请求头名称已改为：";
    public static final String CF_MSG_HNAME_SET_B = " → 记得去 RikkaHub 里把「请求头 · 名称」改成一样的";
    public static final String CF_BTN_PORT_NEW = "换个新地址（换端口）";
    public static final String CF_HINT_PORT_NEW = "换完拿到新地址；RikkaHub 里的服务器地址要改成新的（旧地址立刻失效）";
    public static final String CF_PORT_DLG_T = "换个新地址";
    public static final String CF_PORT_DLG_ASK_A = "填 1024~65535 之间的端口号。现在用的是 ";
    public static final String CF_PORT_DLG_ASK_B = "；填一个没被别的应用占用的号就行（想改回来，再填 8770 就行）。";
    public static final String CF_PORT_BAD = "这个端口号用不了：请填 1024~65535 之间的数字，且不能是别的应用正在用的";
    public static final String CF_MSG_PORT_SET_A = "服务地址已换成：";
    public static final String CF_MSG_PORT_SET_B = " → 记得去 RikkaHub 里把「服务器地址」改成新的";
    public static final String CF_DLG_OK = "确定";
    public static final String CF_ADDR_HNAME = "请求头名称：";

    // ═══════════ 52 号单（2026-10-12）· 开机自启与内存自保 ═══════════
    public static final String CF_SW_BOOT_T = "开机自动恢复";
    public static final String CF_SW_BOOT_D = "开机后自动把 Reticle 的无障碍打开、并把后台服务拉起来，让助手一开机就能用。★两个前提（做不到就只能您手动开）：① 要系统权限（Shizuku）配合——手机上不允许应用自己开无障碍；② 部分系统（如 vivo，实测）还要另外放行「自启动／允许后台运行」，否则开机时根本收不到通知。此开关与上面的「掉了自动重开」各自独立。";
    public static final String CF_MSG_BOOT_ON = "已开启：开机自动恢复（记得先去系统里放行「自启动」）";
    public static final String CF_MSG_BOOT_OFF = "已关闭：开机不做任何事";
    public static final String CF_BOOT_GOTO_T = "去系统「自启动」设置";
    public static final String CF_BOOT_GOTO_D = "打开系统里放行后台运行／自启动的那一页，把 Reticle 加进白名单（不同品牌位置不同；打不开就到系统设置里搜「自启动」）";
    public static final String CF_BOOT_GOTO_FAIL = "这台机器上没找到「自启动」设置页，请到系统设置里搜「自启动」或「后台运行」";
    public static final String MEM_LINE_PREFIX = "内存：";
    public static final String MEM_LV0 = "正常";
    public static final String MEM_LV1 = "放慢（缓）";
    public static final String MEM_LV2 = "收紧（收）";
    public static final String MEM_LV3 = "让路（暂停重试）";
    public static final String MEM_AVAIL_A = "｜可用 ";
    public static final String MEM_AVAIL_B = "%";
    public static final String MEM_SELF_A = "｜自身 PSS ";
    public static final String MEM_SELF_B = "MB";
    public static final String MEM_WHY_A = "｜原因：";
    public static final String NT_MEM1 = "（内存吃紧，检查已放慢）";
    public static final String NT_MEM2 = "（内存吃紧，已收紧自身占用）";
    public static final String NT_MEM3 = "（内存极紧，已暂停重试）";
    public static final String RP_ST_MEM = "内存：";
}
