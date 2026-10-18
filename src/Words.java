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
    public static final String OB_BYE = "Reticle 已就绪。\n重跑引导：配置页「重跑一遍引导」。";
    public static final String OB_SUB = "先看它是什么，再开权限、试一试。";
    public static final String OB_STEP0 = "是什么";
    public static final String OB_STEP1 = "连接 Shizuku";
    public static final String OB_STEP2 = "开无障碍";
    public static final String OB_STEP3 = "文件访问";
    public static final String OB_STEP4 = "试一试";
    public static final String OB_NEXT = "下一步";
    public static final String OB_DONE = "完成";
    public static final String OB_SKIP = "暂不配置，稍后可在配置页重跑。";
    public static final String OB_RETRY = "重新检测";

    // ═══════════ 48 号单（2026-10-12）· 引导页 · 第 0 屏：这应用是干嘛的（大白话，先讲清再要权限） ═══════════
    public static final String OB_S0_T = "Reticle 是干嘛的";
    public static final String OB_S0_P1 = "豆包、Kimi 这类 App 不让外部工具插手。";
    public static final String OB_S0_P2 = "Reticle 接能填地址和请求头的客户端。\n目前适配 RikkaHub。";
    public static final String OB_S0_P3 = "配好后，它替 AI 看屏、点屏。\n每一下都由你发起。";
    public static final String OB_S0_P4 = "三样权限，给它当手和眼睛。";
    public static final String OB_S0_L1 = "Shizuku：让它替你点屏幕、替你跑命令";
    public static final String OB_S0_L2 = "无障碍：让它能看见屏幕上的内容、能点";
    public static final String OB_S0_L3 = "文件访问：让它能读写手机里的文件";

    // ═══════════ 引导页 · 第 1 步：Shizuku ═══════════
    public static final String OB_S1_LEAD = "Shizuku 把系统权限借给 Reticle。\n不用电脑和 root。";

    public static final String OB_S1_NONE_T = "尚未安装 Shizuku";
    public static final String OB_S1_NONE_D = "先装 Shizuku，再回本页。";
    public static final String OB_S1_NONE_B1 = "下载 Shizuku";
    public static final String OB_S1_NONE_B2 = "装好了，检测一下";

    public static final String OB_S1_OFF_T = "已安装 Shizuku，但服务未运行";
    public static final String OB_S1_OFF_D = "点下面按钮打开 Shizuku，按提示启动。\n然后回本页。";
    public static final String OB_S1_OFF_B1 = "打开 Shizuku";
    public static final String OB_S1_OFF_B2 = "已启动，检测一下";

    public static final String OB_S1_NO_T = "最后一步：授权 Reticle";
    public static final String OB_S1_NO_D = "点下面按钮，弹窗选「允许」。";
    public static final String OB_S1_NO_B1 = "请求授权";
    public static final String OB_S1_WAIT = "等授权：弹窗选「允许」。";
    public static final String OB_S1_DENY = "没成功。\n再点「请求授权」，选「允许」。";
    public static final String OB_S1_NEED_RUN = "Shizuku 没运行。\n先点「打开 Shizuku」再授权。";
    public static final String OB_S1_REQ_FAIL = "请求没发出。确认 Shizuku 在运行再点。";
    public static final String OB_S1_NO_POPUP = "没弹窗？到 Shizuku 应用管理里手动打开。";

    public static final String OB_S1_OK_T = "Shizuku 已连接";
    public static final String OB_S1_OK_D = "已获系统权限，点击与命令可用。";

    // ═══════════ 引导页 · 第 2 步：无障碍 ═══════════
    public static final String OB_S2_LEAD = "无障碍让 Reticle 读屏、认字、点击。";
    public static final String OB_S2_T = "把 Reticle 的开关打开";
    public static final String OB_S2_D = "点下面按钮去设置，打开 Reticle 开关。\n然后回本页。";
    public static final String OB_S2_FIX = "开关灰色：设置 › 应用 ›\nReticle，点「解除限制」。";
    public static final String OB_S2_WD = "部分系统会关掉无障碍。\n本应用可自动恢复，能关。";
    public static final String OB_S2_B1 = "去打开";
    public static final String OB_S2_NO_T = "尚未检测到无障碍已开启";
    public static final String OB_S2_NO_D = "去设置打开无障碍开关，再点「重新检测」。";
    public static final String OB_S2_OK_T = "无障碍已开启";
    public static final String OB_S2_OK_D = "屏幕读取、文字识别与点击能力已就绪。";

    // ═══════════ 引导页 · 第 3 步：所有文件访问（35 号单） ═══════════
    public static final String OB_S3F_LEAD = "命令和结果存在本应用文件夹。\n开「所有文件访问」才能读写。";
    public static final String OB_S3F_NO_T = "允许 Reticle 访问其专属文件夹";
    public static final String OB_S3F_NO_D = "点下面按钮，开「所有文件访问」。";
    public static final String OB_S3F_B1 = "去打开";
    public static final String OB_S3F_B2 = "已开启，重新检查";
    public static final String OB_S3F_OK_T = "已可访问专属文件夹";
    public static final String OB_S3F_OK_D = "此权限已配置完成，无需再次设置。";
    // 45 号单：「收成一份」——这条与 OB_S3F_LEAD 讲的是同一件事，已并入 LEAD，不再单独显示
    public static final String OB_S3F_HINT = "找不到？回主界面再点「去打开」，等几秒。";
    public static final String OB_S3F_OLD_T = "此系统无需单独开启";
    public static final String OB_S3F_OLD_D = "本系统用常规存储权限即可。\n点「下一步」。";

    // ═══════════ 引导页 · 第 4 步：自检 ═══════════
    public static final String OB_S3_LEAD = "点「开始自检」，自动检测四项。";
    public static final String OB_S3_B1 = "开始自检";
    public static final String OB_S3_RUNNING = "正在检测…（约 5 秒）";
    public static final String OB_S3_AGAIN = "重新检测";
    public static final String OB_S3_ALL_OK = "四项检测全部通过，可以正常使用。";
    public static final String OB_S3_SOME_BAD = "有项目没过。\n修好再点「再测一遍」。";

    public static final String OB_C1 = "看屏幕";
    public static final String OB_C2 = "点屏幕";
    public static final String OB_C3 = "认字";
    public static final String OB_C4 = "跑命令";

    public static final String OB_FIX_A11Y = "修法：回上一步打开无障碍。";
    public static final String OB_FIX_SHZ = "修法：回第 1 步启动 Shizuku 并授权。";
    public static final String OB_FIX_OCR = "修法：先开无障碍；仍失败就重启。";
    public static final String OB_FIX_SHOT = "修法：先打开无障碍。";

    // ═══════════ 引导页 · 第 5 屏：你只需要知道这些（45 号单） ═══════════
    // 标题／副标题／9 条正文都在 Manual.java（唯一数据源＝与「详细说明·上手章」同一份文字）
    public static final String OB_MIN_NEXT = "下一步";
    public static final String OB_MIN_GO = "知道了，开始用";

    // ═══════════ 详细说明页（45 号单：主界面大标题右边的圆形「?」） ═══════════
    public static final String MN_TITLE = "详细说明";
    public static final String MN_SUB = "Reticle 是怎么来的、能干什么、干不了什么";
    public static final String MN_BACK_DIR = "← 目录";
    public static final String MN_TAIL = "这一页随应用一起走：文案只维护一处，改一次、两处（引导与这里）一起变。";
    public static final String MA_HELP_DESC = "详细说明：来历、能干什么、干不了什么。";

    // ═══════════ 引导页 · 弹出来的短提示（toast） ═══════════
    public static final String OB_TOAST_STORE = "请先下载并安装 Shizuku。";
    public static final String OB_TOAST_SHZ = "无法打开 Shizuku，请从桌面手动启动。";
    public static final String OB_TOAST_A11Y = "打不开设置。手动去：\n设置 › 快捷与辅助 › 无障碍。";
    public static final String OB_TOAST_FILES = "打不开设置。手动去：\n设置 › 应用 › Reticle，\n点「权限」。";

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
    public static final String MA_A11Y_OFF = "不在册";
    public static final String MA_A11Y_EMPTY = "读回为空";
    public static final String MA_A11Y_ERR = "读取失败";

    // ───────── 53 号单：无障碍「连接态」（★在册 ≠ 能用） ─────────
    public static final String MA_A11Y_CONN_OK = "已连接 ✓";
    public static final String MA_A11Y_CONN_DISC = "在册未连接";
    public static final String MA_A11Y_CONN_NOT = "不在册";
    public static final String MA_A11Y_CONN_UNK = "读取失败";
    public static final String MA_A11Y_HINT_DISC = "在册但没接上。\n等十几秒，或点「修一次」。";
    public static final String MA_A11Y_HINT_NOT = "不在册。看门狗会自动写回；\n写不回就去设置打开。";
    public static final String MA_BTN_A11Y_FIX = "修一次（重连无障碍）";
    public static final String MA_BTN_A11Y_FIX_D = "把自己那条删掉再写回。\n只动本应用。";
    public static final String MA_MSG_A11Y_FIXING = "正在重连无障碍…（约 3~8 秒）";
    public static final String MA_MSG_A11Y_FIXED = "无障碍修复：";
    public static final String MA_SHZ_OFF = "未运行";
    public static final String MA_SHZ_OLD = "版本过旧";
    public static final String MA_SHZ_OK = "已授权 ✓";
    public static final String MA_SHZ_NO = "未授权";
    public static final String MA_SHZ_ERR = "查询异常";
    public static final String MA_NOTI_ON = "已开启";
    public static final String MA_NOTI_OFF = "未开启";
    public static final String MA_NOTI_NA = "无需";

    public static final String MA_HINT_TAP_CARD = "轻点任一状态卡片即可刷新";
    public static final String MA_HINT_LOCAL_BUILD = "本机可自己打包 App。\n由 AI 调用。";
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
    public static final String MA_MSG_RESTARTED = "已请求重启，不会重复启动。";
    public static final String MA_MSG_RESTART_ERR = "重启失败：";

    // ═══════════ 配置页 ═══════════
    public static final String CF_SEC_GUIDE = "引导";
    public static final String CF_GUIDE_T = "重跑一遍引导";
    public static final String CF_GUIDE_D = "重跑连接、无障碍、文件访问、自检。";
    public static final String CF_GUIDE_D_TODO = "「所有文件访问」未开，点这里去开。";

    public static final String CF_SEC_CONN = "连接";
    // ═══════════ 50 号单（2026-10-12）· 配置区：三个按钮＝名字即用途，各自写明「粘到哪个框」 ═══════════
    public static final String CF_BTN_HNAME = "复制请求头名称";
    public static final String CF_BTN_HVALUE = "复制请求头值（令牌）";
    public static final String CF_BTN_URL = "复制链接地址";
    public static final String CF_HINT_HNAME = "新建服务器后填「请求头 · 名称」。";
    public static final String CF_HINT_HVALUE = "同一页填「请求头 · 值」。";
    public static final String CF_HINT_URL = "填「服务器地址」，\n类型选 Streamable HTTP。";
    public static final String CF_CONN_LEAD = "在 RikkaHub 新建 MCP 服务器。\n下三个按钮逐个复制、粘贴。";
    public static final String CF_CONN_OFFICIAL = "没装客户端？官网：https://rikka-ai.com";
    public static final String CF_MSG_HNAME_OK_A = "已复制请求头名称：";
    public static final String CF_MSG_HNAME_OK_B = "。粘到 RikkaHub 的「请求头 · 名称」。";
    public static final String CF_MSG_HNAME_ERR = "复制请求头名称失败：";
    public static final String CF_MSG_HVALUE_OK_A = "已复制请求头值（含 Bearer，共 ";
    public static final String CF_MSG_HVALUE_OK_B = " 个字符）。粘到「请求头 · 值」。";
    public static final String CF_MSG_HVALUE_ERR = "复制请求头值失败：";
    public static final String CF_MSG_URL_OK_A = "已复制链接地址：";
    public static final String CF_MSG_URL_OK_B = "。粘到 RikkaHub 的「服务器地址」。";
    public static final String CF_MSG_URL_ERR = "复制链接地址失败：";
    public static final String CF_MIN_T = "使用前须知";
    public static final String CF_MIN_D = "最要紧的几条，就是引导最后一屏。";

    public static final String CF_SEC_ADDR = "服务地址";
    public static final String CF_ADDR_LINE = "服务地址（仅本机）· 日志更新 ";
    public static final String CF_LOG_NONE = "无日志";

    public static final String CF_SEC_BEHAV = "行为";
    public static final String CF_SW_VIB_T = "静音模式下保留震动提醒";
    public static final String CF_SW_VIB_D = "开＝静音时也震。\n关＝跟随系统。都不发声。";
    public static final String CF_MSG_VIB_ON = "已开启：静音时也震";
    public static final String CF_MSG_VIB_OFF = "已关闭：尊重系统静音";
    public static final String CF_ITEM_NOTI_SET = "通知设置";
    public static final String CF_ITEM_NOTI_SET_D = "打开本应用的通知设置页";

    public static final String CF_SEC_WD = "无障碍看门狗";
    public static final String CF_SW_WD_T = "掉了自动重开";
    public static final String CF_SW_WD_D = "被系统关掉后自动重开本应用的无障碍。";
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
    public static final String CF_BLACK_D_ON = "开＝纯黑，OLED 更省电。\n关＝深灰。";
    public static final String CF_BLACK_D_OFF = "切换至「深色」主题后可开启此选项";

    public static final String CF_NAV_SEC = "底部导航";
    public static final String CF_NAV_D = "选导航条手感：均衡、棱镜强、柔和更弹。";
    public static final String CF_NAV_P1 = "均衡";
    public static final String CF_NAV_P2 = "棱镜强";
    public static final String CF_NAV_P3 = "柔和更弹";
    // 54 号单 A：底部导航蒙版第二版的两根滑杆（配置页「底部导航」区）
    public static final String CF_MASK_ALPHA_T = "蒙版浓度";
    public static final String CF_MASK_ALPHA_D = "停住后蒙版的深浅。默认 12%。";
    public static final String CF_MASK_GAP_T = "滴到蒙版间隔";
    public static final String CF_MASK_GAP_D = "滴停稳后多久蒙版淡入。默认 120 毫秒。";

    public static final String CF_SEC_FX = "导航动效";
    public static final String CF_FX_INTRO = "点图标或拖玻璃滴到图标上，\n图标会转、会放大。";
    public static final String CF_FX1_TITLE = "弹跳幅度";
    public static final String CF_FX1_HINT = "导航条拉长与回弹幅度（2%–30%）";
    public static final String CF_FX1_UNIT = "%";
    public static final String CF_FX2_TITLE = "扩张幅度";
    public static final String CF_FX2_HINT = "图标放大峰值 100% 到 260%。";
    public static final String CF_FX2_UNIT = "%";
    public static final String CF_FX3_TITLE = "动画时长";
    public static final String CF_FX3_HINT = "图标旋转一周所需时长（520–1200 毫秒）";
    public static final String CF_FX3_UNIT = " 毫秒";
    public static final String CF_ITEM_FX_RESET = "恢复默认动效";
    public static final String CF_ITEM_FX_RESET_D = "弹跳 12% · 扩张 165% · 时长 640 毫秒";
    public static final String CF_MSG_FX_RESET = "动效已恢复默认";

    public static final String CF_SEC_MAINT = "维护";
    public static final String CF_STOP_T = "一键停止所有功能初始化";
    public static final String CF_STOP_D = "关看门狗和本地服务，清引导标记。";
    public static final String CF_STOP_ASK = "将做三件事：\n关掉无障碍自动重开。\n停止后台服务和常驻通知。\n下次打开重走引导。\n无障碍开关不受影响。\n重开应用即恢复。";
    public static final String CF_DLG_CANCEL = "取消";
    public static final String CF_DLG_STOP = "停止";
    public static final String CF_STOP_DONE = "已停止。重开应用即恢复。";
    public static final String CF_STOP_NOTE = "停止期间助手连不上。";
    public static final String CF_MSG_DLG_ERR = "弹窗失败：";

    // ═══════════ 高级页 ═══════════
    public static final String MA_SEC_TEST = "试验";
    public static final String MA_ITEM_ISLAND = "原子岛试验";
    public static final String MA_ITEM_ISLAND_D = "在原子岛发一张常驻卡片";
    public static final String MA_MSG_ISLAND = "原子岛(创建)：";
    public static final String MA_ISLAND_T = "Reticle 试验";
    public static final String MA_ISLAND_C = "岛上有我，说明本地方式可行";
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
    public static final String NT_CH_LIVE = "助手实况";
    public static final String NT_CH_LIVE_D = "实况型通知（试验）";
    public static final String NT_DEF_TITLE = "Reticle";
    public static final String NT_WD_TEXT = "看门狗运行中，被系统关掉会自动恢复。";
    public static final String NT_ST_IDLE = "Reticle·待命中";
    public static final String NT_ST_WORK = "Reticle·正在";
    public static final String NT_ST_OUT = "Reticle·输出中";
    public static final String NT_ST_BUSY = "忙";
    public static final String NT_BODY_ONLINE = "助手在线 · 点这里打开";
    public static final String MA_A11Y_BACK_OK = "无障碍已自动恢复";
    public static final String MA_A11Y_BACK_FAIL = "没恢复。去设置手动打开无障碍。";
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

    // ═══════════ 86 号单 · 26 个工具的中文名（一张表；通知／手册／气泡共用） ═══════════
    public static final String NT_TOOL_DL = "下载";   // 86：提到表前（ECJ 不许引用后定义的字段）
    public static final String NT_TOOL_BUZZ = "震动";
    public static final String NT_TOOL_STATUS = "状态";
    public static final String NT_TOOL_HELP = "帮助";
    public static final String NT_TOOL_ACTTEST = "动作自测";
    public static final String NT_TOOL_TAP_TEXT = "按字点";
    public static final String NT_TOOL_WAIT_TEXT = "等字";
    public static final String NT_TOOL_WAIT_STABLE = "等画面";
    public static final String NT_TOOL_SCROLL = "滚屏";
    public static final String NT_TOOL_PASTE = "粘贴";
    public static final String NT_TOOL_SCROLL_TO_TEXT = "滚找字";
    public static final String NT_TOOL_DO_CHAIN = "批量动作";
    public static final String NT_TOOL_WINDOWS = "看窗口";

    // ═══════════ 87 号单 · 极简模式（气泡只说人话＋三种状态） ═══════════
    public static final String MS_MIN_DONE = "已返回";
    public static final String MS_MIN_FAIL = "失败";
    public static final String MS_MIN_TIMEOUT = "超时失败";

    /** 工具名 → 中文名。★已译的沿用原译；不在这里＝返回 null（别乱起名）。 */
    public static final String[][] TOOL_ZH = {
            {"shot", NT_TOOL_SHOT}, {"tap", NT_TOOL_TAP}, {"swipe", NT_TOOL_SWIPE}, {"key", NT_TOOL_KEY},
            {"ui", NT_TOOL_UI}, {"ocr", NT_TOOL_OCR}, {"input", NT_TOOL_INPUT}, {"longpress", NT_TOOL_LONGPRESS},
            {"shell", NT_TOOL_SHELL}, {"notify", NT_TOOL_NOTIFY}, {"build", NT_TOOL_BUILD},
            {"download", NT_TOOL_DL}, {"download_status", NT_TOOL_DL}, {"ping", NT_TOOL_PING},
            {"buzz", NT_TOOL_BUZZ}, {"status", NT_TOOL_STATUS}, {"help", NT_TOOL_HELP},
            {"acttest", NT_TOOL_ACTTEST}, {"tap_text", NT_TOOL_TAP_TEXT}, {"wait_text", NT_TOOL_WAIT_TEXT},
            {"wait_stable", NT_TOOL_WAIT_STABLE}, {"scroll", NT_TOOL_SCROLL}, {"paste", NT_TOOL_PASTE},
            {"scroll_to_text", NT_TOOL_SCROLL_TO_TEXT}, {"do_chain", NT_TOOL_DO_CHAIN},
            {"windows", NT_TOOL_WINDOWS}
    };

    public static String toolZh(String t) {
        if (t == null) return null;
        for (int i = 0; i < TOOL_ZH.length; i++) {
            if (TOOL_ZH[i][0].equals(t)) return TOOL_ZH[i][1];
        }
        return null;
    }

    // ═══════════ 86 号单 · 大通知的三种结果与守护那行 ═══════════
    public static final String NT_R_DONE = "完成";
    public static final String NT_R_FAIL = "没成";
    public static final String NT_R_TIMEOUT = "超时没成";
    public static final String NT_R_BUSY = "进行中";
    public static final String NT_GUARD_ON = "守护：在线";
    public static final String NT_GUARD_HIDE = "隐藏守护";
    public static final String NT_GUARD_SHOW = "显示守护";
    public static final String NT_A11Y_LOST = "无障碍掉线了 正在重连";
    public static final String NT_A11Y_LOST_SUB = "助手暂时看不见屏幕。接回后消失。";
    public static final String NT_BRIDGE_TITLE = "手机控制通道";
    public static final String NT_BRIDGE_TEXT = "通道闲置已关闭，重启服务即恢复。";

    // ═══════════ 看门狗那一句（配置页显示） ═══════════
    public static final String WD_LINE_OFF = "看门狗：已关闭。不碰系统设置。";
    public static final String WD_LINE_PREFIX = "当前状态：";
    public static final String WD_A11Y_ON = "无障碍已连接 ✓";
    public static final String WD_A11Y_OFF = "无障碍未开启";
    public static final String WD_A11Y_DISC = "无障碍在册但没接上";
    public static final String WD_DISC_HINT = "；点「修一次」，或关开一次无障碍";
    public static final String CF_A11Y_LINK_LEAD = "在册只是有资格，接上才能读屏和点。\n没接上就点「修一次」。";
    public static final String WD_FIXES_A = "；已自动恢复 ";
    public static final String WD_FIXES_B = " 次";
    public static final String WD_FIXES_C = "（最近 ";
    public static final String WD_FIXES_D = "）";
    public static final String WD_CHECKED = "；最近检查 ";
    public static final String WD_FAIL = "；上次恢复失败，请手动打开无障碍";

    // ═══════════ 系统设置里露出的字都放在 res/values/strings.xml（app_name／a11y_desc／a11y_summary／fgs_watchdog_desc） ═══════════

    // ═══════════ 给助手的工具说明（高级页「诊断 → MCP 工具」里也逐条显示） ═══════════
    public static final String TL_PING = "回铃：确认 Reticle 在线";
    public static final String TL_SHELL = "以 shell 身份执行一条命令，回输出与退出码";
    public static final String TL_SHELL_CMD = "命令（例 id、ls /sdcard）";
    public static final String TL_SHOT = "截当前屏幕（系统级，游戏画面也不黑屏）。\n与 ocr 同时调会互撞。\n一条一条来。";
    public static final String TL_TAP = "点屏幕一点；x、y 是 0~1 比例（正中＝0.5,0.5）";
    public static final String TL_TAP_X = "0~1 比例";
    public static final String TL_TAP_Y = "0~1 比例";
    public static final String TL_TAP_MS = "按住毫秒（默认 60）";
    public static final String TL_SWIPE = "从一点滑到另一点（坐标 0~1 比例）";
    public static final String TL_SWIPE_X1 = "0~1 比例";
    public static final String TL_SWIPE_Y1 = "0~1 比例";
    public static final String TL_SWIPE_X2 = "0~1 比例";
    public static final String TL_SWIPE_Y2 = "0~1 比例";
    public static final String TL_SWIPE_MS = "毫秒（默认 300）";
    public static final String TL_KEY = "按系统键：back／home／recents";
    public static final String TL_KEY_VALUE = "back／home／recents";
    public static final String TL_UI = "读当前界面控件（文字/描述/边界/可点）";
    public static final String TL_UI_MAX = "最多几个（默认 120）";
    public static final String TL_OCR = "离线中文认字（整屏或指定区域）";
    public static final String TL_OCR_REGION = "0~1 的 x,y,w,h（不填＝整屏）";
    public static final String TL_OCR_SRC = "图片路径（不填＝当场截屏）";
    public static final String TL_INPUT = "往当前聚焦的输入框写文字（中文也行）";
    public static final String TL_INPUT_TEXT = "要写的文字";
    public static final String TL_LONGPRESS = "长按某点（坐标 0~1 比例）";
    public static final String TL_LONGPRESS_MS = "按住毫秒（默认 800）";
    public static final String TL_NOTIFY = "发／更新／清一条通知；report= 可同时回助手";
    public static final String TL_NOTIFY_TITLE = "标题（默认 Reticle）";
    public static final String TL_NOTIFY_CONTENT = "正文";
    public static final String TL_NOTIFY_ID = "编号（同号＝更新，默认 1）";
    public static final String TL_NOTIFY_ONGOING = "1＝不可划掉（默认 1）";
    public static final String TL_NOTIFY_CLEAR = "1＝清除该条";
    public static final String TL_NOTIFY_ISLAND = "原子岛场景名";
    public static final String TL_NOTIFY_OP = "原子岛：0 建／1 改／2 结（默认 0）";
    public static final String TL_NOTIFY_PROMOTED = "1＝实况型通知";
    public static final String TL_NOTIFY_PROGRESS = "进度 0~100（默认 60）";
    public static final String TL_NOTIFY_REPORT = "汇报文本（写进眼线通知正文）";
    public static final String TL_BUILD = "构建并安装 App（工序 check→new→start→status→install）。\ninstall 交给系统安装器。\n要用户点确认。\n装完会重启。";
    public static final String TL_BUILD_ACTION = "check／new／start／status／install（默认 status）";
    public static final String TL_BUILD_NAME = "应用名（action=new 用；默认「我的应用」）";
    public static final String TL_BUILD_PKG = "包名（action=new 用，如 com.example.myapp）";
    public static final String TL_BUZZ = "震动一次（纯震动：不发声、不弹通知、不亮屏）";
    public static final String TL_BUZZ_MS = "毫秒（默认 300，上限 10000）";
    public static final String CF_LOG_SEC = " 秒前";
    public static final String CF_LOG_MIN = " 分钟前";
    public static final String CF_LOG_HOUR = " 小时前";
    public static final String NT_ISLAND_STATUS = "胶囊";

    // ═══════════ 助手回话 · 工具结果（RP_ = reply；界面上看不到，是给助手看的那几句话） ═══════════
    public static final String RP_HELLO = "hello：Reticle 在线（MCP 回铃成功）";

    // buzz
    public static final String RP_BUZZ_FMT = "已震动 %dms（纯震动：不发声／不弹通知／不亮屏；静音穿透＝%s）";
    public static final String RP_BUZZ_ON = "开";
    public static final String RP_BUZZ_OFF = "关";
    public static final String RP_BUZZ_NO_VIB = "震动失败：本机没有震动器";
    public static final String RP_BUZZ_ERR = "震动异常：";
    public static final String TL_BUZZ_DRY = "1＝只验通路不震（默认 0）";
    public static final String RP_BUZZ_DRY = "自检 dry：通路 ok，未震动（%dms）——只有助手显式 buzz 才会真震";

    // 通知 / 原子岛 / 实况 / 汇报（★媒体路线已于 72 号单整体移除）
    public static final String RP_REPORT = "已汇报（写进眼线那条通知的正文）：";
    public static final String RP_NT_CLEARED = "已清除通知 #";
    public static final String RP_NT_CLEAR_ERR = "清除失败：";
    public static final String RP_ISLAND_OK = "已发原子岛 #";
    public static final String RP_ISLAND_OP = " op=";
    public static final String RP_ISLAND_SCENE = " scene=";
    public static final String RP_ISLAND_ERR = "原子岛发送失败：";
    public static final String RP_MEDIA_GONE = "媒体路线已移除（不再占用系统媒体通道）；这条通知改走普通样式（不要填 media 或 playing），助手汇报照旧看得见。";
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
    public static final String RP_INPUT_FMT = "已写入 %d 字。\n目标：前台应用的输入框。\n读回：%s\n方式：%s";
    public static final String RP_OP_ERR = "操作失败：";
    public static final String RP_SHOT_FMT = "已截图。\n截图时小形象临时摘掉。\n文件 %s（%dx%d，%dms）";
    public static final String RP_SHOT_ERR = "截图失败：";
    public static final String RP_TAP_TAIL_CHANGED = "，页面已变化（+%dms）";
    public static final String RP_TAP_TAIL_NOCHANGE = "，但页面没变。";
    public static final String RP_OCR_HUMAN_FMT = "已识出 %d 行。\n大字认不出就明说。\n";

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
    public static final String TL_STATUS = "报状态：版本／三件套／看门狗／前台包／MCP 端口";
    public static final String TL_HELP = "工具用法：不带参数＝先知道的事＋目录；tool=<名>＝单个工具全文";
    public static final String TL_ACTTEST = "动作自测靶：依次试 tap／长按／输入／滑动／返回";

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
    public static final String RP_CHK_CONF_NO = "（不存在，全部用内置默认路径；不是你的路径就照仓库 build/build.conf.example 写一份放到这里）";
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
    public static final String TL_DOWNLOAD = "下载文件到手机（≤8MB 当场完，大的转后台）";
    public static final String TL_DOWNLOAD_URL = "http/https 完整网址";
    public static final String TL_DOWNLOAD_DIR = "存哪（默认 Reticle 的 dl 目录）";
    public static final String TL_DOWNLOAD_NAME = "文件名（默认取网址末段）";
    public static final String TL_DOWNLOAD_SHA = "给校验值则核对";
    public static final String TL_DOWNLOAD_HEADERS = "请求头（如 Referer）";
    public static final String TL_DOWNLOAD_UNZIP = "1＝顺带解包（默认 0）";
    public static final String TL_DOWNLOAD_FORCE = "1＝覆盖／破 2GB（默认 0）";
    public static final String TL_DOWNLOAD_STATUS = "查下载进度／列出下载任务";
    public static final String TL_DOWNLOAD_STATUS_ID = "任务 id（不填＝列全部）";

    // 下载进度那条通知（实况进度样式）
    public static final String DL_NT_TITLE = "正在下载 ";
    public static final String DL_NT_DONE_T = "下载完成 ";
    public static final String DL_NT_FAIL_T = "下载失败 ";
    public static final String DL_NT_LINE_FMT = "已下 %s / %s（%d%%）· %s";
    public static final String DL_NT_LINE_NOSIZE_FMT = "已下 %s · %s";
    public static final String DL_NT_SPEED_UNKNOWN = "速度--";

    // 下载回话（给助手看的那几句）
    public static final String RP_DL_BAD_URL = "下载失败：url 得是 http/https 开头的完整网址";
    public static final String RP_DL_BAD_DIR = "下载失败：这个目录不在允许清单里（只允许 Reticle 的 dl 目录、/sdcard/Download）";
    public static final String RP_DL_NOWRITE_FMT = "这个目录我写不进去（系统权限）：%s\n请改用 /sdcard/Reticle/dl（dir 不填就是它）或 /sdcard/Download。";
    public static final String RP_DL_HTTP_FMT = "下载失败：源站回了 HTTP %d（%s）";
    public static final String RP_DL_PROBE_FAIL_FMT = "下载失败：连不上或探测不到这个地址（%s）";
    public static final String RP_DL_EXISTS_FMT = "这个文件已经在：%s（%s）\nsha256=%s\n要重下就加 force=1";
    public static final String RP_DL_SHA_UNKNOWN = "（文件较大，没现算）";
    public static final String RP_DL_TOO_BIG_FMT = "这份有 %s，超过 2GB 上限：确认要下就加 force=1";
    public static final String RP_DL_OK_FMT = "已下载 %s 到 %s。\n%s\nsha256=%s\n用时 %d 秒";
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
    public static final String TL_HELP_TOOL = "工具名（不填＝目录）";
    public static final String HP_INDEX_HEAD = "Reticle 工具（共 %d 个，一行一个）：\n";
    public static final String HP_INDEX_POINT = "某工具详情：help tool=<名字>（例 help tool=scroll_to_text）\n";
    public static final String HP_INDEX_TAIL = "另一条路：在手机上做 App 的工序＝build action=new（摊工程）→ start（出包）→ status（看进度）→ install（装到手机）；工程目录默认 /sdcard/Reticle/工程（也可 build.conf 写 PROJECT_DIR=）。\n";
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
    public static final String TL_TAP_TEXT = "按文字/描述/ID 找控件并点它（找不到不乱点）";
    public static final String TL_TAP_TEXT_TEXT = "文字（默认包含匹配）";
    public static final String TL_TAP_TEXT_DESC = "content-desc";
    public static final String TL_TAP_TEXT_ID = "控件 ID（可只写后半段）";
    public static final String TL_TAP_TEXT_INDEX = "第几个（默认 0）";
    public static final String TL_TAP_TEXT_TIMEOUT = "毫秒（默认 1500）";
    public static final String TL_TAP_TEXT_DURATION = "毫秒（默认 80）";
    public static final String TL_TAP_TEXT_EXACT = "1＝完全相等";
    public static final String TL_TAP_TEXT_EXPECT = "点后等这段字出现";
    public static final String TL_TAP_TEXT_EXPECT_PKG = "前台校验（不是就拒绝）";
    public static final String TL_TAP_TEXT_OCR = "0＝关认字兜底";
    public static final String TL_TAP_TEXT_WAIT = "点后等反应毫秒（默认 900）";
    public static final String TL_WAIT_TEXT = "等文字出现／消失（替代 sleep+反复截图）";
    public static final String TL_WAIT_TEXT_TEXT = "等的文字";
    public static final String TL_WAIT_TEXT_STATE = "appear（默认）／disappear";
    public static final String TL_WAIT_TEXT_TIMEOUT = "毫秒（默认 5000）";
    public static final String TL_WAIT_STABLE = "等画面稳定（控件树静默若干毫秒）";
    public static final String TL_WAIT_STABLE_QUIET = "静默毫秒（默认 600）";
    public static final String TL_WAIT_STABLE_TIMEOUT = "毫秒（默认 5000）";
    public static final String TL_SCROLL = "滚动列表/页面（走屏幕中段，避开手势区）";
    public static final String TL_SCROLL_DIR = "down（默认）／up／left／right";
    public static final String TL_SCROLL_DIST = "0.05~0.6（默认 0.5）";
    public static final String TL_SCROLL_PKG = "前台校验（不是就拒绝）";

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
    public static final String RP_TAP_FMT2 = "已点 x=%d y=%d。\n当前前台：%s。\n动作 %dms%s";
    public static final String RP_INPUT_CLIP_BACK = "（用完已把你原来的剪贴板还原）";
    public static final String RP_VIA_ANCESTOR = "在可点的父控件上点，不靠坐标";
    public static final String RP_VIA_ACTION = "控件级点击，不靠坐标";
    public static final String RP_VIA_GESTURE = "控件中心手势点";
    public static final String RP_VIA_GESTURE_ANC = "父控件中心手势点";
    public static final String RP_VIA_OCR = "截图认字定位后点中心（这类界面没有控件树）";
    public static final String TL_INPUT_X = "先点这里（0~1，可选）";
    public static final String TL_INPUT_Y = "先点这里的 y（0~1，可选）";
    public static final String TL_INPUT_PKG = "前台校验（不是就拒绝）";
    public static final String RP_INPUT_FMT2 = "已写入 %d 字。\n目标：前台应用的输入框。\n界面读不到回显；方式 %s";

    // ═══════════ 44.5 号单（2026-10-11）· 剪贴板直贴 / 滚找目标 / 批量动作 / 窗口探针 ═══════════
    public static final String TL_PASTE = "剪贴板直贴到当前输入框（治微信这类应用）";
    public static final String TL_PASTE_TEXT = "要贴的文字";
    public static final String TL_PASTE_X = "先点这里（0~1，可选）";
    public static final String TL_PASTE_Y = "先点这里的 y（0~1，可选）";
    public static final String TL_PASTE_RESTORE = "1＝还原剪贴板（默认 1）";
    public static final String TL_PASTE_PKG = "前台校验（不是就拒绝）";

    public static final String TL_SCROLL_TO_TEXT = "边滚边找目标（tap=1 顺手点它）";
    public static final String TL_SCROLLTO_TEXT = "文字（默认包含匹配）";
    public static final String TL_SCROLLTO_DESC = "content-desc";
    public static final String TL_SCROLLTO_ID = "控件 ID";
    public static final String TL_SCROLLTO_EXACT = "1＝完全相等";
    public static final String TL_SCROLLTO_INDEX = "第几个（默认 0）";
    public static final String TL_SCROLLTO_DIR = "down（默认）／up";
    public static final String TL_SCROLLTO_MAX = "最多几屏（默认 10，上限 40）";
    public static final String TL_SCROLLTO_TIMEOUT = "毫秒（默认 12000）";
    public static final String TL_SCROLLTO_TAP = "1＝找到就点（默认 0）";
    public static final String TL_SCROLLTO_PKG = "前台校验（不是就拒绝）";

    public static final String TL_DO_CHAIN = "批量动作：一次提交多步，每步可带 expect（顶层 ok=0＝有步失败）";
    public static final String TL_DO_CHAIN_STEPS = "步骤数组 [{act,参数…,expect,timeout_ms}]";
    public static final String TL_DO_CHAIN_STOP = "1＝出错即停（默认 1）";
    public static final String TL_DO_CHAIN_OBSERVE = "第 N 步后回屏上现状（0＝不回）";

    public static final String TL_WINDOWS = "列出可见窗口（查分屏／小窗／悬浮窗）";

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
    // 82 号单（2026-10-17）：do_chain 的话说清「跑没跑」——被拒／steps 有问题＝一步都没跑；stop_on_error=0 跑完但不成立＝跑了多少步、几步没过
    public static final String RP_CHAIN_NORUN_FMT = "没跑成：整条链一步都没跑——原因：%s。改好再发一条 do_chain（一次就能连做多步）。";
    public static final String RP_CHAIN_PART_FMT = "链跑完了但不成立：%d 步跑完、%d 步没过（%s）；没过的步骤在 detail 里，屏上现状见 screen。";
    // ═══════════ 51 号单（2026-10-12）· 令牌与链接管理 ═══════════
    /** 官网地址（一键复制用；与「复制链接地址」区分：那个复制的是本机服务地址） */
    public static final String CF_OFFICIAL_URL = "https://rikka-ai.com";
    public static final String CF_BTN_OFFICIAL = "复制官网地址";
    public static final String CF_HINT_OFFICIAL = "复制 RikkaHub 官网，不是本机地址。";
    public static final String CF_MSG_OFFICIAL_OK = "已复制官网地址。";
    public static final String CF_MSG_OFFICIAL_ERR = "复制官网地址失败：";

    public static final String CF_SEC_TOKEN = "令牌与地址";
    public static final String CF_TOKEN_WHAT = "令牌是本机通行证，助手每次都要带。\n别外传。";
    public static final String CF_TOKEN_HOW = "怀疑泄露就重新生成，旧令牌立即作废。";
    public static final String CF_LINK_WHAT = "地址只在本机可用。\n换端口后 RikkaHub 也要改。";
    public static final String CF_BTN_TOKEN_NEW = "重新生成令牌";
    public static final String CF_HINT_TOKEN_NEW = "旧令牌立即失效。\n换完重粘「请求头 · 值」。";
    public static final String CF_TOKEN_ASK = "确定重新生成？\n旧令牌立即作废，连接会断。\n然后去 RikkaHub 重粘一次。";
    public static final String CF_MSG_TOKEN_NEW = "新令牌已生成。\n重粘「请求头 · 值」。";
    public static final String CF_BTN_HNAME_SET = "修改请求头名称";
    public static final String CF_HINT_HNAME_SET = "默认 Authorization。\n改后 RikkaHub 填同名。";
    public static final String CF_HNAME_DLG_T = "修改请求头名称";
    public static final String CF_HNAME_DLG_ASK = "只填名称，例如 Authorization。";
    public static final String CF_HNAME_BAD = "字母开头，只含字母、数字和「-」。";
    public static final String CF_MSG_HNAME_SET_A = "请求头名称已改为：";
    public static final String CF_MSG_HNAME_SET_B = "。去 RikkaHub 改成同名。";
    public static final String CF_BTN_PORT_NEW = "换个新地址（换端口）";
    public static final String CF_HINT_PORT_NEW = "换完得到新地址。旧地址失效。\nRikkaHub 里也要改。";
    public static final String CF_PORT_DLG_T = "换个新地址";
    public static final String CF_PORT_DLG_ASK_A = "填 1024~65535 之间的端口号。现在用的是 ";
    public static final String CF_PORT_DLG_ASK_B = "。填 1024 到 65535 之间未占用的号。";
    public static final String CF_PORT_BAD = "填 1024 到 65535 之间，且未被占用。";
    public static final String CF_MSG_PORT_SET_A = "服务地址已换成：";
    public static final String CF_MSG_PORT_SET_B = "。去 RikkaHub 改服务器地址。";
    public static final String CF_DLG_OK = "确定";
    public static final String CF_ADDR_HNAME = "请求头名称：";

    // ═══════════ 52 号单（2026-10-12）· 开机自启与内存自保 ═══════════
    public static final String CF_SW_BOOT_T = "开机自动恢复";
    public static final String CF_SW_BOOT_D = "开机自动开无障碍，并拉起服务。\n需要 Shizuku。\n部分系统要放行自启动。";
    public static final String CF_MSG_BOOT_ON = "已开启。记得去系统里放行「自启动」。";
    public static final String CF_MSG_BOOT_OFF = "已关闭：开机不做任何事";
    public static final String CF_BOOT_GOTO_T = "去系统「自启动」设置";
    public static final String CF_BOOT_GOTO_D = "去放行后台运行那页，\n把 Reticle 加白名单。";
    public static final String CF_BOOT_GOTO_FAIL = "没找到。去系统设置搜「自启动」。";
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
    // ═══════════ 64 号单 · 屏幕上的小形象（看板娘） ═══════════
    public static final String CF_SEC_MASCOT = "屏幕上的小形象";
    public static final String CF_SW_MASCOT_T = "显示小形象";
    // ★87 极简模式：开关（默认关）。开着时常规那几项全灰，极简自己的参数留给 89 单。
    public static final String CF_SW_MINIMAL_T = "极简模式";
    public static final String CF_SW_MINIMAL_D = "开＝只留白条和气泡，不显示形象。\n关＝常规样子。";
    public static final String CF_MSG_MINIMAL_ON = "极简模式：只留白条和气泡";
    public static final String CF_MSG_MINIMAL_OFF = "已回到常规样子";
    public static final String CF_SW_MASCOT_D = "开＝贴边显示小形象。\n工具和汇报会冒一句。\n闲置变淡，再收成细条。\n可点可拖，位置会记住。\n截图和点击前会让开。";
    public static final String CF_MSG_MASCOT_ON = "小形象已打开";
    public static final String CF_MSG_MASCOT_OFF = "小形象已关掉";
    public static final String CF_ITEM_MASCOT_IMG = "给小形象换一张图";
    public static final String CF_ITEM_MASCOT_IMG_D = "从相册挑图当形象。\n换回自带小脸：删掉形象.png。";
    public static final String MS_IMG_CANCEL = "没选图片，形象保持原样";
    public static final String MS_IMG_OK = "已换成你选的图（立刻生效，不用重启）";
    public static final String MS_IMG_NOACCESS = "读不到这张图（系统没给访问权），形象保持原样";
    public static final String MS_IMG_TOOBIG = "这张图太大（超过 8MB），换张小点的吧";
    public static final String MS_IMG_BAD = "这张图解不出来（可能不是图片或已损坏），形象仍是自带的小脸";
    public static final String MS_IMG_FAIL = "换图没成功";
    public static final String MS_IMG_PICKER_FAIL = "打不开系统的图片选择器——换个文件管理器，把图片放到下面这个路径也行：";

    // ═══════════ 65 号单 · 桌面伙伴（可点·可拖·气泡自由·工具标签） ═══════════
    public static final String CF_MASCOT_ALPHA_T = "形象不透明度";
    public static final String CF_MASCOT_ALPHA_D = "形象的明显程度，默认 85%。";
    public static final String CF_MASCOT_POS_T = "气泡位置";
    public static final String CF_MASCOT_POS_D = "气泡从哪边冒出。\n点这一行切换：斜 45° 或上方。";
    public static final String[] CF_MASCOT_POS_NAMES = { "斜 45°", "正上方" };   // 65 四版：只留这两个（用户拍）
    public static final String CF_HIDE_T = "在 AI 客户端里";
    public static final String CF_HIDE_D = "在客户端里缩成细线，不冒泡。\n点细条出来 5 分钟。\n点这一行切三档。";
    // ★71 B 线（2026-10-15）：让位名单＝勾选应用列表（用户提：别让我自己输包名）
    public static final String CF_HIDE_PKGS_T = "让位名单（勾选应用）";
    public static final String CF_HIDE_PKGS_D = "进列表勾选应用。\n勾上的在前台时它让位。\n也能手输包名。";
    public static final String CF_HIDE_PKGS_COUNT_FMT = "当前：勾了 %d 个应用";
    public static final String CF_HIDE_PKGS_DLG_T = "让位名单";
    public static final String CF_HIDE_PKGS_DLG_ASK = "客户端包名，逗号分隔。";
    public static final String CF_MSG_HIDE_PKGS_A = "已保存，让位名单：";
    public static final String AP_T = "让位名单：勾上谁，谁在前台时它让位";
    public static final String AP_D = "默认勾着 RikkaHub（AI 客户端）。\n勾上立刻生效，不用重启。\n名单只在本机读、只存本机。\n找不到的应用可「手动输入包名」。";
    public static final String AP_SEARCH = "搜索应用名或包名";
    public static final String AP_SHOW_SYS = "显示系统应用";
    public static final String AP_MANUAL = "手动输入包名";
    public static final String AP_MANUAL_T = "手动输入包名";
    public static final String AP_MANUAL_D = "逗号分隔，例：\nme.rerere.rikkahub\ntv.danmaku.bili\n找不到的应用用这里加。";
    public static final String AP_DONE = "完成";
    public static final String AP_HINT_FMT = "列出来 %d 个，已勾 %d 个";
    public static final String AP_LOADING = "正在读应用列表…";
    public static final String AP_EMPTY = "没读到应用。可以「手动输入包名」。";
    public static final String AP_SRC_PM = "PackageManager";
    public static final String AP_SRC_SHZ = "Shizuku 只读包名";
    public static final String AP_SHZ_NOTE = "；只拿到包名（系统不让列清单）";
    public static final String AP_SYS_TAG = "（系统应用）";
    public static final String AP_LOG_FMT = "应用列表：%d 个可启动，已勾 %d";
    public static final String CF_MSG_HIDE_MODE_FMT = "在 AI 客户端里：%s";
    public static final String CF_MSG_MASCOT_POS_FMT = "气泡位置：%s";
    public static final String CF_SW_MASCOT_MIRROR_T = "自动镜像";
    public static final String CF_SW_MASCOT_MIRROR_D = "贴左边时左右翻转，脸朝屏内。";
    public static final String CF_MSG_MASCOT_MIRROR_ON = "已开：贴左时自动镜像";
    public static final String CF_MSG_MASCOT_MIRROR_OFF = "已关：不再镜像";
    public static final String CF_SW_MASCOT_VERT_T = "气泡形状：纵向优先";
    public static final String CF_SW_MASCOT_VERT_D = "关＝气泡在旁边。\n开＝气泡贴屏边、在上方。";
    public static final String CF_MSG_MASCOT_VERT_ON = "气泡形状：纵向优先";
    public static final String CF_MSG_MASCOT_VERT_OFF = "气泡形状：横向优先";

    // ═══════════ 73 号单（2026-10-16）· 应用图标 ═══════════
    public static final String CF_SEC_ICON = "应用图标";
    public static final String CF_ICON_LEAD = "这里换桌面图标。\n四款自带的点一下就用。\n最后一行用自己的图生成入口。\n应用图标要重打包才能换。";
    public static final String CF_ICON_PIN_T = "用我自己的图做个桌面图标";
    public static final String CF_ICON_PIN_D = "选一张图，居中裁成方。\n在弹窗点「添加」，桌面多一个入口。";
    public static final String CF_MSG_ICON_FMT = "已切到「%s」。";
    public static final String CF_MSG_ICON_FAIL = "图标没换成";
    public static final String CF_ICON_PIN_CANCEL = "没选图片，桌面图标保持原样";
    public static final String CF_ICON_PIN_OK = "已请求添加，去桌面点「添加」确认。";
    public static final String CF_ICON_PIN_FAIL = "没能加进桌面（系统没接受这次请求）";
    public static final String CF_ICON_PIN_NOSUP = "这个桌面不支持添加入口。\n换自带图标，或换桌面。";
    public static final String CF_ICON_PIN_PICKFAIL = "打不开图片选择器，换个文件管理器。";
    public static final String CF_ICON_PIN_TOOBIG = "这张图太大（超过 8MB），换张小点的吧";
    public static final String CF_ICON_PIN_BAD = "这张图读不出来，可能已损坏。";

    // ═════════ 74 号单（2026-10-16）· 应用内装环境（打包 App） ═════════
    public static final String MA_SEC_BUILD = "打包 App";
    public static final String MA_SEC_TOOLBOX = "工具";
    public static final String MA_BUILD_READY = "环境就绪，本机可以自己打包。";
    public static final String MA_BUILD_ADV_LEAD = "本机可把安卓工程编成 apk。\n首次要下载平台文件，约 26 MB。\n一般由 AI 调用。";
    public static final String MA_BUILD_NOTREADY = "环境还没齐";
    public static final String MA_BUILD_BTN_PREP = "一键准备环境";
    public static final String MA_BUILD_BTN_PREP_D = "要下载 android.jar，约 25.8MB。\n下完校验 sha256。";
    public static final String MA_BUILD_BTN_GO = "打包 App";
    public static final String MA_BUILD_BTN_GO_D = "把「工程」编成 apk，约 1 到 3 分钟。";
    public static final String MA_BUILD_BTN_INSTALL = "装这次出的包";
    public static final String MA_BUILD_BTN_INSTALL_D = "把刚出的 apk 装到本机，需要 Shizuku。";
    public static final String MA_BUILD_BTN_OPEN = "看构建日志";
    public static final String MA_BUILD_NO_PROJ = "还没找到工程：把安卓工程放进 ";
    public static final String MA_BUILD_HINT_LOCK = "灰色＝现在用不了。\n看下面缺什么，点一下补。";
    public static final String MA_BUILD_TITLE_ENV = "环境";
    public static final String MA_BUILD_MSG_PREP_DONE = "环境准备好了 ✓";
    public static final String MA_BUILD_MSG_PREP_FAIL = "环境没准备好：";
    public static final String MA_BUILD_MSG_GO = "开始打包。进度看日志或通知栏。";
    public static final String MA_BUILD_MSG_GO_FAIL = "没能开始：";
    public static final String MA_BUILD_MSG_RUNNING = "上一次还在跑，先看日志";
    public static final String MA_BUILD_DLG_T = "准备构建环境";
    public static final String MA_BUILD_DLG_WHY = "缺 android.jar，约 25.8MB。\n只能联网取一次，下完校验 sha256。";
    public static final String MA_BUILD_DLG_GO = "开始下载";
    public static final String MA_BUILD_DLG_LATER = "以后再说";
    public static final String MA_BUILD_DLG_RUN = "正在准备…";

    public static final String RP_BUILD_ENV_HEAD = "构建环境：";
    public static final String RP_BUILD_ENV_OK = "就绪（aapt2／android.jar／物料 jar 都在）\n";
    public static final String RP_BUILD_ENV_NO = "环境未就绪：";
    public static final String RP_BUILD_NEED_ENV = "缺构建环境（android.jar 25.8MB 未下载）：本工具现在**不能用**。补法：①用户在应用里点『打包 App → 一键准备环境』；②或调 build action=env 让 Reticle 自己下（官方源 dl.google.com，下完自动 sha256 校验）。";
    public static final String RP_BUILD_NEED_PROJ = "找不到安卓工程：把一个含 AndroidManifest.xml／res／src 的目录放到 ";
    public static final String RP_BUILD_ENV_STARTED = "已在下载 android.jar（约 61MB 的官方包），进度见通知栏；下完 build action=check 看就绪。";

    // ═══════════ 77 号单（2026-10-16）· 工具全文（tools/list 只留一行，全文挪到这里，help tool=X 取用） ═══════════
    public static final String TD_PING = "回铃测试：确认 Reticle 在线（返回一句 hello）";
    public static final String TD_SHELL = "在你的手机上以 shell 身份执行一条命令，返回输出和退出码。exit=真实退出码；30 秒没跑完＝叫停进程并回 timeout=1；stdout／stderr 都空时回 ‹空›。★本机（实测环境）常遇到的坑：settings put 常不落地（改用 content insert，且值里不能带冒号）；cat -n 不可用（用 sed -n '1,$p' | nl 或 awk）。";
    public static final String TD_SHELL_CMD = "要执行的命令，例如 id、ls /sdcard";
    public static final String TD_SHOT = "截取当前屏幕（系统级截图，游戏画面也不黑屏）。返回图片与保存路径。";
    public static final String TD_TAP = "在屏幕上点一下。x、y 用 0~1 的比例（相对屏幕宽高）：屏幕正中＝0.5,0.5。";
    public static final String TD_TAP_X = "横坐标比例 0~1";
    public static final String TD_TAP_Y = "纵坐标比例 0~1";
    public static final String TD_TAP_MS = "按住时长毫秒（可选，默认 60）";
    public static final String TD_SWIPE = "从一点滑到另一点（坐标 0~1 比例）。";
    public static final String TD_SWIPE_X1 = "起点横坐标 0~1";
    public static final String TD_SWIPE_Y1 = "起点纵坐标 0~1";
    public static final String TD_SWIPE_X2 = "终点横坐标 0~1";
    public static final String TD_SWIPE_Y2 = "终点纵坐标 0~1";
    public static final String TD_SWIPE_MS = "滑动时长毫秒（可选，默认 300）";
    public static final String TD_KEY = "按系统按键：返回(back)/主页(home)/最近任务(recents)。";
    public static final String TD_KEY_VALUE = "back 或 home 或 recents";
    public static final String TD_UI = "读取当前界面的控件（文字/描述/边界/是否可点），用于定位要点哪里；读不到会如实说明（游戏等自绘界面读不到）。";
    public static final String TD_UI_MAX = "最多返回几个控件（可选，默认 120）";
    public static final String TD_OCR = "识别屏幕上或指定区域的文字（离线中文）；region 用 0~1 比例 x,y,w,h，可选。";
    public static final String TD_OCR_REGION = "区域 0~1 比例：x,y,w,h（可选，不填＝整屏）";
    public static final String TD_OCR_SRC = "指定图片路径（可选，默认当场截屏）";
    public static final String TD_INPUT = "往当前聚焦的输入框写入文字（中文也行）。输入框读不到回显时会走剪贴板粘贴，并尽量把你原来的剪贴板还原；微信这类界面可先给 x/y 让它点一下输入框。";
    public static final String TD_INPUT_TEXT = "要输入的文字";
    public static final String TD_LONGPRESS = "长按屏幕上某点（坐标 0~1 比例）。";
    public static final String TD_LONGPRESS_MS = "按住时长毫秒（可选，默认 800）";
    public static final String TD_NOTIFY = "发送/更新/清除一条手机通知（可选：原子岛试验／实况试验）。同 id 重复发送＝更新同一条。★report=<文本>＝把汇报写进眼线那条通知的正文（不新建第二条，只回助手）。★媒体样式那条路已整体移除（2026-10-16，不再占用系统媒体通道）。";
    public static final String TD_NOTIFY_TITLE = "标题（默认：Reticle）";
    public static final String TD_NOTIFY_CONTENT = "内容文本";
    public static final String TD_NOTIFY_ID = "通知编号：同编号＝更新同一条（默认 1）";
    public static final String TD_NOTIFY_ONGOING = "1＝常驻不可划掉（默认 1）";
    public static final String TD_NOTIFY_CLEAR = "1＝清除该编号通知";
    public static final String TD_NOTIFY_ISLAND = "原子岛场景名（如 HEALTH_REGISTER；填了就走原子岛）";
    public static final String TD_NOTIFY_OP = "原子岛操作：0 创建/1 更新/2 结束（默认 0）";
    public static final String TD_NOTIFY_PROMOTED = "1＝实况型通知试验（ProgressStyle）";
    public static final String TD_NOTIFY_PROGRESS = "实况进度 0~100（默认 60）";
    public static final String TD_NOTIFY_REPORT = "助手汇报文本：填了＝写进眼线那条通知的正文（不新建第二条）";
    public static final String TD_BUILD = "构建并安装 App（设备侧工具链，不联网）。action：check＝看环境（缺什么／从哪拿）；new＝摊一个最小 Java 工程到工程目录（可带 name=应用名 pkg=包名；已有工程＝拒绝覆盖）；start＝开始出包（后台跑，约 1~3 分钟）；status＝看进度／日志尾／产物（默认）；install＝把产物装到手机；help＝这段说明。★推荐工序：check → new（或已有工程）→ start → status → install。★工程目录＝默认 /sdcard/Reticle/工程（里面放 AndroidManifest.xml ＋ res/ ＋ src/）；也可在 /sdcard/Reticle/build.conf 写 PROJECT_DIR= 指走。";
    public static final String TD_BUILD_ACTION = "check / start / status / install / help（默认 status）";
    public static final String TD_BUZZ = "震动提醒：使手机震动一次，表示「有消息找你」。仅震动——不发声、不弹出通知、不点亮屏幕。";
    public static final String TD_BUZZ_MS = "震动时长毫秒（可选，默认 300；上限 10000）";
    public static final String TD_BUZZ_DRY = "dry：1＝只验通路、不震动（自检／健康巡检用；默认 0＝真震）";
    public static final String TD_STATUS = "一句话报状态：版本、三件套（无障碍在册＋连接态／所有文件访问／自检结论）、看门狗累计救回次数与最近时间、前台包名、MCP 端口、日志尾几行。";
    public static final String TD_HELP = "工具目录与用法（只回助手，界面上不显示）：不带参数＝目录（一行一个工具，名字 ＋ 半句用途）；tool=<名字>＝这一个工具的完整说明与参数。工具失败时，回话末尾会带 help tool=<名> 的指路。";
    public static final String TD_ACTTEST = "动作类自测靶：开 Reticle 自己的小窗口，依次试 tap／长按／输入／滑动／返回并回报每步结果（会短暂占用前台，只在需要自测动作时用）。";
    public static final String TD_DOWNLOAD = "下载一个文件到手机（不用开浏览器，也不依赖 Shizuku）。小文件（默认 8MB 以内）当场下完并回报路径＋sha256；大文件立刻返回任务号，下载转后台继续，进度显示在通知栏，用 download_status 查。只能存到 Reticle 的 dl 目录或 /sdcard/Download（别的目录写不进去，会当面回人话）。";
    public static final String TD_DOWNLOAD_URL = "下载地址（http/https 完整网址）";
    public static final String TD_DOWNLOAD_DIR = "存到哪个目录（可选，默认 Reticle 的 dl 目录；只允许 Reticle 的 dl 目录、/sdcard/Download）";
    public static final String TD_DOWNLOAD_NAME = "存成什么文件名（可选，默认取网址最后一段）";
    public static final String TD_DOWNLOAD_SHA = "sha256 校验值（可选）：下完不一致会明确报错";
    public static final String TD_DOWNLOAD_HEADERS = "自定义请求头（可选，防盗链站用）：如 {\"Referer\":\"https://…\"}，或「Referer: https://…」一行一条";
    public static final String TD_DOWNLOAD_UNZIP = "unzip：1＝下完顺带解包到同名文件夹（默认 0）";
    public static final String TD_DOWNLOAD_FORCE = "force：1＝覆盖同名文件、突破 2GB 上限（默认 0）";
    public static final String TD_DOWNLOAD_STATUS = "查下载进度：给 id 报那一条（进度／速度／已下与总量／完成路径＋sha256）；不给 id 就列出最近的下载任务。";
    public static final String TD_DOWNLOAD_STATUS_ID = "任务 id（download 起任务时给的那个；可选）";
    public static final String TD_HELP_TOOL = "工具名（可选）：填了＝只回这一个工具的详情；不填＝回目录";
    public static final String TD_TAP_TEXT = "按文字/描述/ID 找控件并点它：会自己往上找可点的祖先，用控件级点击（不靠坐标）。界面没有控件树（微信、游戏等自绘界面）会自动改用截图认字兜底点中心。找不到就报人话，绝不乱点。★别盲信节点的 bounds（应用可能报怪值），能控件级点就控件级点。";
    public static final String TD_TAP_TEXT_TEXT = "要点的文字（默认包含匹配；exact=1 则完全相等）";
    public static final String TD_TAP_TEXT_DESC = "按控件描述 content-desc 找";
    public static final String TD_TAP_TEXT_ID = "按控件 ID 找（可只写后半段，如 send_btn）";
    public static final String TD_TAP_TEXT_INDEX = "匹配到多个时点第几个（默认 0＝第一个）";
    public static final String TD_TAP_TEXT_TIMEOUT = "找不到就等，最多等多少毫秒（默认 1500）";
    public static final String TD_TAP_TEXT_DURATION = "按住时长毫秒（默认 80）";
    public static final String TD_TAP_TEXT_EXACT = "1＝文字完全相等（默认包含匹配）";
    public static final String TD_TAP_TEXT_EXPECT = "点完等这段文字出现，出现了即算点中";
    public static final String TD_TAP_TEXT_EXPECT_PKG = "先确认前台是这个包名，不是就拒绝动手（防点错应用）";
    public static final String TD_TAP_TEXT_OCR = "0＝关掉截图认字兜底（默认开）";
    public static final String TD_TAP_TEXT_WAIT = "点完最多等多少毫秒看反应（默认 900）";
    public static final String TD_WAIT_TEXT = "等某段文字出现或消失（先读控件树，读不到就截图认字）。用它代替「sleep+反复截图」。";
    public static final String TD_WAIT_TEXT_TEXT = "等的文字";
    public static final String TD_WAIT_TEXT_STATE = "appear（默认，等出现）/ disappear（等消失）";
    public static final String TD_WAIT_TEXT_TIMEOUT = "最多等多少毫秒（默认 5000）";
    public static final String TD_WAIT_STABLE = "等画面稳定：控件树连续若干毫秒没变化就返回（默认 600ms）。点完/滑完用它等页面停下来，不用猜 sleep。";
    public static final String TD_WAIT_STABLE_QUIET = "连续多少毫秒没变化算稳定（默认 600）";
    public static final String TD_WAIT_STABLE_TIMEOUT = "最多等多少毫秒（默认 5000）";
    public static final String TD_SCROLL = "滚动列表/页面：只走屏幕中段，自动避开底部手势区与边缘返回区；可先校验前台包名。direction＝down（默认，往下看后面的内容）/up/left/right。";
    public static final String TD_SCROLL_DIR = "down（默认）/ up / left / right";
    public static final String TD_SCROLL_DIST = "滚动距离，屏幕比例 0.05~0.6（默认 0.5）";
    public static final String TD_SCROLL_PKG = "先确认前台是这个包名，不是就拒绝";
    public static final String TD_INPUT_X = "先点这个位置（可选，屏幕比例 0~1）——微信这类没有可读输入框的界面，用它先聚焦";
    public static final String TD_INPUT_Y = "先点这个位置的纵坐标（可选，0~1）";
    public static final String TD_INPUT_PKG = "先确认前台是这个包名，不是就拒绝";
    public static final String TD_PASTE = "剪贴板直贴：把内容写进剪贴板，再贴到当前输入框（治微信这类「不支持无障碍输入」的应用）。会先找聚焦输入框；页面上没有可读输入框时用 x/y 先点一下。贴完读回校验。★内部按 ①控件级粘贴 ②长按输入框点「粘贴」菜单 ③按键 279 依次试；界面慢时可能两通道各贴一次——贴重要内容后请用 ui／ocr 读回确认。★原来的剪贴板内容读不到（系统不让后台应用读），restore 多数情况下还原不了。";
    public static final String TD_PASTE_TEXT = "要贴进去的文字";
    public static final String TD_PASTE_X = "先点这个位置（可选，屏幕比例 0~1）：没有可读输入框的界面（微信）用它先聚焦";
    public static final String TD_PASTE_Y = "先点这个位置的纵坐标（可选，0~1）";
    public static final String TD_PASTE_RESTORE = "restore：1（默认）＝贴完还原你原来的剪贴板；0＝把这次的内容留在剪贴板";
    public static final String TD_PASTE_PKG = "先确认前台是这个包名，不是就拒绝";
    public static final String TD_SCROLL_TO_TEXT = "滚着找目标：边滚边找（控件树优先，读不到树就截图认字），找到就告诉你在第几屏、要不要顺手点（tap=1）。滚到尽头/滚满上限/超时都如实回报，绝不在没找到时乱点。";
    public static final String TD_SCROLLTO_TEXT = "要找的文字（默认包含匹配）";
    public static final String TD_SCROLLTO_DESC = "按控件描述 content-desc 找（可选）";
    public static final String TD_SCROLLTO_ID = "按控件 ID 找（可选）";
    public static final String TD_SCROLLTO_EXACT = "1＝文字完全相等（默认包含匹配）";
    public static final String TD_SCROLLTO_INDEX = "匹配到多个时点第几个（默认 0＝第一个）";
    public static final String TD_SCROLLTO_DIR = "往哪边找：down（默认，往下翻）/ up";
    public static final String TD_SCROLLTO_MAX = "最多滚几屏（默认 10，上限 40）";
    public static final String TD_SCROLLTO_TIMEOUT = "最多花多少毫秒（默认 12000）";
    public static final String TD_SCROLLTO_TAP = "tap：1＝找到就用 tap_text 点它（点完验货）；默认 0＝只找到、不点";
    public static final String TD_SCROLLTO_PKG = "先确认前台是这个包名，不是就拒绝";
    public static final String TD_DO_CHAIN = "批量动作：一次提交多步，设备端连着执行，省来回。每步各带自己的后置条件（expect）：动作做完就等它出现，等不到＝当场停在那一步，并回「第几步、什么命令、当时屏幕现状」。★界面一变就退回单步（这工具不是「界面不可预测也硬批量」）。";
    public static final String TD_DO_CHAIN_STEPS = "步骤数组，每步一个对象：{\"act\":\"tap_text\",\"text\":\"发送\",\"expect\":\"已发送\",\"timeout_ms\":3000}。act＝动作名（tap/tap_text/input/paste/longpress/swipe/key/scroll/wait_text/wait_stable/ui/ocr/shot），act 之外的字段原样当该动作的参数；expect＝本步后置条件（可选）；timeout_ms＝等 expect 的上限（默认 5000）";
    public static final String TD_DO_CHAIN_STOP = "stop_on_error：1（默认）＝任一步失败或 expect 超时就停；0＝失败也继续往下走（每步结果都记在 detail 里）";
    public static final String TD_DO_CHAIN_OBSERVE = "observe：在第 N 步（1 起算）之后把当时的屏幕现状（前台包名＋可见文字）一并回给你；0＝不额外回";
    public static final String TD_WINDOWS = "列出当前无障碍看得见的窗口（类型/包名/边界/是否整屏/是否活动），用来查分屏·小窗·悬浮窗——也是分屏下坐标排查的探针。split=1 表示当前可能是分屏/多窗口。★多窗口时 ui 只给「有焦点的那个窗口」的节点：要操作另一个窗口，先点它一下，或用 tap_text 的认字兜底。";
    // ═══════════ 77 号单（2026-10-16）· 起项目 ＋ 自解释 ＋ 瘦身 ═══════════
    /** 工程目录＝写进 build 描述（陌生 AI 不必翻现场就知道往哪儿放手写工程）。 */
    public static final String TL_BUILD_WHERE = "工程目录默认 /sdcard/Reticle/工程（也可在 /sdcard/Reticle/build.conf 写 PROJECT_DIR= 指走）";
    /** help 不带参数时先说的话（面向陌生用户：这机器怎么用、要干嘛先看哪）。 */
    public static final String HP_FIRST = "你该先知道的几件事：\n"
        + "· 你在一台真手机上：tap／swipe／input 都是真动手。动手前先用 ui（控件树）或 ocr（认字）看清屏幕，别盲点。\n"
        + "· 坐标一律 0~1 比例（正中＝0.5,0.5）；按文字找东西优先用 tap_text。\n"
        + "· 眼睛：ui → ocr → shot（自己看图）；手：shell 跑命令，download 取网络文件。\n"
        + "· 要在这台手机上做个 App：build action=new name=<应用名> pkg=<包名> 摊一个最小工程 → start → status → install。\n"
        + "· 每个工具的全文：help tool=<名字>。\n";
    /** help tool=build 的推荐工序。 */
    public static final String HP_BUILD_FLOW = "推荐工序：check（看环境）→ new（摊工程；已有工程就跳过）→ start（出包）→ status（看进度／产物）→ install（装到手机）。\n";
    public static final String RP_BUILD_NEXT_RUN = "→ 还在跑：等它完，再 build action=status。\n";
    public static final String RP_BUILD_NEXT_APK = "→ 下一步：build action=install 装到手机；装完点桌面图标或 am start 打开。\n";
    public static final String RP_BUILD_NEXT_NEW = "→ 下一步：build action=start 出包（1~3 分钟）；还没有工程就先 build action=new name=<应用名> pkg=<包名> 摊一个最小工程。\n";
    public static final String RP_BUILD_NOTRUN = "构建：没在跑";
    /** 77.5 号单（2026-10-17）：工程来历 ＋ 两条路；★不再播报上一轮构建状态。 */
    public static final String RP_BUILD_PROJ_ROADS = "   ↳ 两条路：①改它＝直接改上面这个工程（改完 build action=start 出包）；②换目录＝在 /sdcard/Reticle/build.conf 写一行 PROJECT_DIR=<你的工程目录>，这个工程原样留着、不覆盖。\n";
    public static final String RP_BUILD_PROJ_NONE = "还没有工程（默认位置 %s；也可在 /sdcard/Reticle/build.conf 写 PROJECT_DIR= 指到别处）\n   ↳ 下一步：build action=new name=<应用名> pkg=<包名> 摊一个最小工程，再 build action=start。\n";
    // ═══════════ 77.6 号单（2026-10-17）· 工具可靠性收口：结构化失败 ＋ 指路 ═══════════
    /** 后置条件（expect）没等到：说清"没验到"，并给下一步。 */
    public static final String RP_TT_EXPECT_FAIL = "点下去了，但『%s』一直没出现（画面没走到那一步）——算没做成";
    /** 写盘失败（目录不可写／被换成同名文件）——不许报成"已截图"／"已认字"。 */
    public static final String RP_SHOT_WRITE_FAIL = "截图拍到了，但写盘失败（目标目录不可写或不是目录）：";
    public static final String RP_OCR_WRITE_FAIL = "认字算完了，但结果写盘失败（目标目录不可写或不是目录）：";
    /** 失败原因 → 下一步（返回是给 AI 读的：素、给下一步）。 */
    public static final String RP_REASON_NOT_FOUND = "（下一步：ui 看控件树／shot 自己看图，再换文字或 desc 找）";
    public static final String RP_REASON_EXPECT = "（下一步：先 wait_text 等目标出现，或 ui／shot 看现在到哪一步）";
    public static final String RP_REASON_PKG = "（下一步：确认前台包名——status 看「前台包」，或用 windows 看窗口）";
    public static final String RP_REASON_WRITE = "（下一步：把目标目录恢复成目录——清目录要「清内容留目录」）";
    /** 裸动作（tap／swipe／key／longpress）的 expect_pkg 参数说明（共用一处）。 */
    public static final String TL_ACT_PKG = "前台校验：不是这个包名就拒绝动手（可选，传了才校验）";
    /** 每个工具短描述末尾的指路（tools/list 素化：一行一个，不展开全文）。 */
    public static final String HP_TOOL_SUFFIX = "（详见 help tool=%s）";
    /** help 无参：目录地图（东西都在，只指路；不写长）。 */
    public static final String HP_MAP = "东西都在哪（目录地图）：\n"
        + "· 工程／产物：工程目录＝/sdcard/Reticle/工程（build.conf 的 PROJECT_DIR= 可指走）；出包产物＝/sdcard/Reticle/out/。\n"
        + "· 构建日志：/sdcard/Reticle/build/last-build.log（8 阶段＋签名指纹＋产物 sha256）。\n"
        + "· 四个日志：/sdcard/Reticle/logs/——audit-YYYYMMDD.log（每次工具调用一行，含 ok／err 与原因）、notifier.log、mascot.log、service.log。\n"
        + "· 截图／认字／下载：/sdcard/Reticle/shots/、/sdcard/Reticle/ocr/、/sdcard/Reticle/dl/。\n"
        + "· 素材与外观：/sdcard/Reticle/外观/（用户自己的图标与形象，别动）、tpl/（模板）、chain/（链结果）、loop/（循环截图）。\n"
        + "· 参数名三套但同义：ms（tap／swipe／longpress）＝duration（input／tap_text）＝毫秒；新增参数一律叫 timeout_ms。\n"
        + "· 单个工具全文：help tool=<名字>。\n";
    /** scroll_to_text 找到了、但点它那一步没成（含内嵌 expect 未满足）。 */
    public static final String RP_SCROLLTO_TAPFAIL = "找到了，但点它这一步没成（或点后要等的字没等到）——算没做成";

    // ═══════════ 79 号单（2026-10-17）· 检查更新（第一次主动联网：说清"会联网、多久一次、发什么、怎么关"） ═══════════
    public static final String UPD_SEC = "更新";
    public static final String UPD_SW_T = "检查更新";
    public static final String UPD_SW_D = "开＝进前台问一次新版。\n匿名请求，最少隔 10 分钟。\n关＝不联网。";
    public static final String UPD_NOW_T = "立即检查";
    public static final String UPD_NOW_D = "现在检查一次，不受 24 小时限制。";
    public static final String UPD_STATE_FMT = "上次检查：%s · 上次提示的版本：%s";
    public static final String UPD_STATE_NEVER = "还没检查过";
    public static final String UPD_STATE_NOBUB = "无";
    public static final String UPD_STATE_CHECKING = "正在检查…";
    public static final String UPD_STATE_FAIL = "刚才没连上，下次再试。";
    public static final String UPD_MSG_UP_TO_DATE_FMT = "已是最新版本（本机 v%s）";
    public static final String UPD_NEW_T_FMT = "发现新版本 v%s";
    public static final String UPD_NEW_D_FMT = "当前 v%s · 点这里下载（约 %s）";
    public static final String UPD_NEW_D_NOSIZE_FMT = "当前 v%s · 点这里下载";
    public static final String UPD_DL_FMT = "正在下载 %d%%，%s（共 %s）";
    public static final String UPD_VERIFY = "下载完成，正在核对文件…";
    public static final String UPD_READY = "下载完成，点这里安装。";
    public static final String UPD_BAD_CHECK = "下载的文件不完整，已删除";
    public static final String UPD_NEED_PERM = "先允许 Reticle 安装应用。";
    public static final String UPD_BUBBLE_FMT = "Reticle 出了新版本 v%s";
    public static final String UPD_ALREADY = "已是最新，不用更新";
    public static final String UPD_MSG_ON = "已开启。进前台问一次，隔 10 分钟。";
    public static final String UPD_MSG_OFF = "已关闭：不再联网检查，也不会自动下载";
    public static final String UPD_API_T = "更新源";
    public static final String UPD_API_D_FMT = "当前：%s\n只换检查地址\n下载地址取自 JSON";
    public static final String UPD_API_DLG_T = "更新源地址";
    public static final String UPD_API_DLG_A = "填完整网址，一般以 /releases/latest 结尾。";
    public static final String UPD_API_SET = "已改，下次检查就用这个地址";
    public static final String UPD_API_BAD = "要填 http:// 或 https:// 开头的网址。";
    public static final String UPD_DL_START = "正在下载…";
    public static final String UPD_BODY_EMPTY = "这次没有写说明";
    public static final String UPD_PLAIN_T = "更新说明";
    public static final String UPD_DL_PCT_FMT = "已完成 %d%%";
    public static final String UPD_DL_SPD_FMT = "速度 %s";
    public static final String UPD_DL_ETA_FMT = "还剩约 %s";
    public static final String UPD_ETA_UNKNOWN = "还在算";
    // ── 79 号单返工（2026-10-17 用户验收反馈）──
    public static final String UPD_DLG_T_FMT = "有新版本 v%s";
    public static final String UPD_DLG_MSG_FMT = "本机 v%s，约 %s";
    public static final String UPD_DLG_MSG_NOSIZE_FMT = "本机 v%s";
    public static final String UPD_DLG_LATER = "不再提示";
    public static final String UPD_DLG_NOW = "立即更新";
    public static final String UPD_LAST_FMT = "上次检查：%s";
    public static final String UPD_SEEN_FMT = "上次提示的版本：%s";
    public static final String UPD_TAG_FMT = "上次检查 %s";
    public static final String UPD_TAG_NEVER = "还没检查";
    public static final String UPD_CLR_T = "清除更新记录";
    public static final String UPD_CLR_D = "清空检查时间和提示版本，开关不动。";
    public static final String UPD_CLR_OK = "已清除更新记录";
    // 配置页折叠块（母标题右侧的状态小字）
    public static final String CF_FOLD_WD = "无障碍与自启";
    public static final String CF_FOLD_NAVFX = "底部导航与动效";
    public static final String CF_FOLD_PORT_FMT = "端口 ";
    public static final String CF_FOLD_ON = "已开";
    public static final String CF_FOLD_OFF = "已关";
    public static final String CF_FOLD_NIGHT = "深色";
    public static final String CF_FOLD_BLACK = " · 极夜";
    public static final String CF_FOLD_LIGHT = "浅色";
}
