package app.reticle;

import org.json.JSONObject;

/**
 * 65 号单 · D 线：工具显示规范（26 个工具一个不许漏）。
 *
 * · 一处维护：工具名 → 图标符号 ＋ 中文短标签 ＋ 有区分度的摘要；气泡第一行＝「图标 标签 · 摘要」。
 * · 未识别工具走兜底（原名当标签、无图标符号），**不留空白**。
 * · ★敏感不上屏：input／paste 的正文一律不显（只报字数）；像令牌／口令的参数值一律打码。
 * · 图标用单色 Unicode 符号（矢量图标 res/ 里不足 26 个；符号在任何主题下都可辨、不占资源）。
 */
public class ToolTags {

    /** 26 个工具：{名字, 图标, 中文短标签}——顺序与 McpServer.tools/list 对齐。 */
    static final String[][] T = {
        {"ping",            "●", "回铃"},
        {"shell",           "⌘", "执行命令"},
        {"shot",            "◎", "截图"},
        {"tap",             "✛", "点按"},
        {"swipe",           "↔", "滑动"},
        {"key",             "⎋", "系统键"},
        {"ui",              "▤", "读界面"},
        {"ocr",             "文", "认字"},
        {"input",           "⌨", "输入文字"},
        {"longpress",       "◉", "长按"},
        {"notify",          "✦", "通知"},
        {"build",           "⚒", "打包"},
        {"download",        "⤓", "下载"},
        {"download_status", "⧗", "下载进度"},
        {"buzz",            "◈", "震动"},
        {"status",          "ℹ", "状态"},
        {"help",            "?", "工具目录"},
        {"acttest",         "✓", "动作自测"},
        {"tap_text",        "⌖", "按文字点"},
        {"wait_text",       "◷", "等文字"},
        {"wait_stable",     "◌", "等画面稳"},
        {"scroll",          "↕", "滚动"},
        {"paste",           "⧉", "粘贴"},
        {"scroll_to_text",  "⇅", "滚着找"},
        {"do_chain",        "⛓", "批量动作"},
        {"windows",         "▢", "看窗口"},
    };

    /** 工具个数（与 T 的行数一致；验收 grep 对表用）。 */
    public static int count() { return T.length; }

    static String[] row(String name) {
        if (name == null) return null;
        for (int i = 0; i < T.length; i++) if (T[i][0].equals(name)) return T[i];
        return null;
    }

    /** 图标符号（未识别工具＝空，不留白）。 */
    public static String icon(String name) {
        String[] r = row(name);
        return r == null ? "" : r[1];
    }

    /** 中文短标签（未识别工具＝原名兜底，绝不空白）。 */
    public static String label(String name) {
        String[] r = row(name);
        if (r == null) return name == null ? "?" : name;
        return r[2];
    }

    /** 气泡第一行：图标 标签 · 摘要。 */
    public static String line(String name, JSONObject a) {
        if ("shell".equals(name)) return shellAction(a == null ? "" : a.optString("command", ""));   // ★89.9 用户口径：命令只说功能、参数不上屏
        String ic = icon(name);
        String s = summary(name, a);
        String head = (ic.length() == 0 ? "" : ic + " ") + label(name);
        return s.length() == 0 ? head : head + " · " + s;
    }

    /** 有区分度的摘要（敏感一律不上屏）。 */
    public static String summary(String name, JSONObject a) {
        if (name == null) return "";
        try {
            if (name.equals("shell")) return "";   // ★89.9：命令由 line() 直出中文功能（shell—读取文件）
            if (name.equals("input")) return "内容不上屏（" + a.optString("text", "").length() + " 字）";
            if (name.equals("paste")) return "内容不上屏（" + a.optString("text", "").length() + " 字）";
            if (name.equals("tap") || name.equals("longpress")) return "(" + a.optString("x", "?") + ", " + a.optString("y", "?") + ")";
            if (name.equals("swipe")) return "(" + a.optString("x1", "?") + ", " + a.optString("y1", "?") + ")→(" + a.optString("x2", "?") + ", " + a.optString("y2", "?") + ")";
            if (name.equals("tap_text") || name.equals("wait_text") || name.equals("scroll_to_text")) {
                String t = a.optString("text", "");
                String d = a.optString("desc", "");
                if (t.length() == 0) t = d;
                if (t.length() == 0) t = a.optString("id", "");
                return t.length() == 0 ? "" : "「" + clip(clean(t), 18) + "」";
            }
            if (name.equals("scroll")) return a.optString("direction", "down");
            if (name.equals("key")) return a.optString("key", "");
            if (name.equals("ocr")) return a.optString("region", "").length() > 0 ? "指定区域" : "整屏";
            if (name.equals("ui")) return a.optString("max", "").length() > 0 ? "最多 " + a.optString("max") + " 个" : "";
            if (name.equals("notify")) {
                String c = a.optString("content", "");
                String t = a.optString("title", "");
                String s = c.length() > 0 ? c : t;
                return s.length() == 0 ? "" : clip(clean(s), 30);
            }
            if (name.equals("build")) return a.optString("action", "status");
            if (name.equals("download")) return hostOf(clean(a.optString("url", "")));
            if (name.equals("download_status")) return a.optString("id", "").length() > 0 ? "任务 " + a.optString("id") : "全部任务";
            if (name.equals("buzz")) return a.optString("duration_ms", "300") + " ms";
            if (name.equals("do_chain")) return steps(a.optString("steps", "")) + " 步";
            if (name.equals("help")) return a.optString("tool", "").length() > 0 ? a.optString("tool") : "全部";
            if (name.equals("scroll_to_text")) return "";
            return "";
        } catch (Throwable t) { return ""; }
    }

    /** shell：不显命令开头（都一样），取「关键子命令 ＋ 对象」。
     *  ★66：①看**最后一段**（`cd /x && sh a.sh` ⇒ `sh a.sh`，不被前面的定义／切换段带偏）
     *        ②跳过以 { 或 ( 结尾的 token（日志里出过 `⌘ 执行命令 · F() {`）。 */
    static String shellBrief(String cmd) {
        if (cmd == null) return "";
        String c = cmd.trim();
        for (int guard = 0; guard < 6; guard++) {
            int sp = c.indexOf(' ');
            if (sp <= 0) break;
            String head = c.substring(0, sp);
            int eq = head.indexOf('=');
            if (eq > 0 && !head.startsWith("=")) c = c.substring(sp + 1).trim(); else break;
        }
        if (c.startsWith("nohup ")) c = c.substring(6).trim();
        String best = c;
        String[] seg = c.split("&&|\\|\\||;|\\||\n");
        for (int i = seg.length - 1; i >= 0; i--) {
            String s = seg[i].trim();
            if (s.length() == 0 || isScriptToken(s)) continue;
            best = s;
            break;
        }
        String[] tk = best.split("\\s+");
        String first = "", second = "";
        for (int i = 0; i < tk.length; i++) {
            String t = tk[i];
            if (t.length() == 0 || isScriptToken(t)) continue;
            if (first.length() == 0) { first = t; continue; }
            if (t.length() > 24) {
                int sl = t.lastIndexOf('/');
                t = (sl >= 0 && sl + 1 < t.length()) ? t.substring(sl + 1) : t;
                if (t.length() > 20) t = t.substring(0, 20);
            }
            second = t;
            break;
        }
        if (first.length() == 0) first = "脚本";
        return clean(second.length() == 0 ? first : first + " " + second);
    }

    /** 是不是"脚本结构"token（`F() {`、`{`、`(subshell` 之类）：66 起一律不当摘要。 */
    private static boolean isScriptToken(String t) {
        if (t == null || t.length() == 0) return true;
        char c0 = t.charAt(0), c1 = t.charAt(t.length() - 1);
        if (c0 == '{' || c0 == '(' || c1 == '{' || c1 == '(') return true;
        if (t.indexOf('(') >= 0 || t.indexOf(')') >= 0 || t.indexOf('{') >= 0 || t.indexOf('}') >= 0) return true;
        return false;
    }

    static int steps(String json) {
        if (json == null) return 0;
        int n = 0, i = -1;
        while ((i = json.indexOf("\"act\"", i + 1)) >= 0) n++;
        return n;
    }

    static String hostOf(String url) {
        if (url == null || url.length() == 0) return "";
        try {
            int i = url.indexOf("://");
            String rest = i >= 0 ? url.substring(i + 3) : url;
            int sl = rest.indexOf('/');
            String host = sl >= 0 ? rest.substring(0, sl) : rest;
            if (host.length() == 0) return clip(url, 30);
            String tail = "";
            if (sl >= 0) {
                String p = rest.substring(sl);
                int q = p.indexOf('?');
                if (q >= 0) p = p.substring(0, q);
                int ls = p.lastIndexOf('/');
                if (ls >= 0 && ls + 1 < p.length()) tail = " " + p.substring(ls + 1);
            }
            return clip(host + tail, 42);
        } catch (Throwable t) { return clip(url, 30); }
    }

    /** ★敏感打码：token=／口令／password／secret 之类的值一律 ****。 */
    static String clean(String s) {
        if (s == null) return "";
        String r = s.replace('\n', ' ');
        try {
            r = r.replaceAll("(?i)(token|passwd|password|pass|secret|apikey|api_key|key|口令|密码)([\\s]*[=:：][\\s]*)([^\\s&,\"'）)]{1,120})", "$1$2****");
        } catch (Throwable t) { }
        return r;
    }

    static String clip(String s, int max) {
        if (s == null) return "";
        s = s.replace('\n', ' ').trim();
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    // ═════════ 74 号单（2026-10-16）· 工具按「定位」分簇（界面列表用；簇顺序＝界面顺序） ═════════
    /** {簇名, 工具名（逗号分隔）}——26 个工具一个不漏、一个不重。 */
    static final String[][] CLUSTERS = {
        {"看屏幕",  "shot,ocr,ui,windows"},
        {"动手",    "tap,longpress,swipe,key,input,paste,tap_text,wait_text,wait_stable,scroll,scroll_to_text,do_chain"},
        {"消息与自检", "ping,status,help,buzz,notify"},
        {"取件",    "download,download_status"},
        {"打包 App", "build,shell,acttest"},
    };

    /** 簇数（与 CLUSTERS 行数一致；验收 grep 对表用）。 */
    public static int clusterCount() { return CLUSTERS.length; }
    public static String clusterName(int i) { return (i < 0 || i >= CLUSTERS.length) ? "" : CLUSTERS[i][0]; }
    /** 第 i 簇的工具名数组。 */
    public static String[] clusterTools(int i) {
        if (i < 0 || i >= CLUSTERS.length) return new String[0];
        String[] a = CLUSTERS[i][1].split(",");
        for (int k = 0; k < a.length; k++) a[k] = a[k].trim();
        return a;
    }

    /** 簇里工具总数（＝T 的行数时说明不漏；验收 grep 对表用）。 */
    public static int clusterToolCount() {
        int n = 0;
        for (int i = 0; i < CLUSTERS.length; i++) n += clusterTools(i).length;
        return n;
    }

    /** 未就绪的整簇变灰：返回空串＝可用；否则＝"为什么不可用／需要什么"（人话，界面直接显示）。 */
    public static String clusterWhy(int i, boolean a11yOk, boolean envOk, boolean shxOk) {
        if (i == 0 || i == 1) return a11yOk ? "" : "需要无障碍服务（眼睛和手都靠它）——先开启 Reticle 的无障碍";
        if (i == 4) {
            if (!envOk) return "需要构建环境：点下面的『一键准备环境』下载 android.jar（25.8MB，官方源）";
            if (!shxOk) return "环境齐了，能出包；但『装包』这一步要 shell 身份（Shizuku）——没通就装不上";
            return "";
        }
        return "";
    }

    // ═════════ 89.9 号单（2026-10-18）· shell 说人话：命令第一个词 → 中文功能 ═════════
    /** 命令开头 → 中文功能；气泡写成 `shell—读取文件`（长破折号连接），参数一律不进气泡。 */
    static final String[][] SH = {
        {"ls", "读取文件"}, {"find", "读取文件"}, {"stat", "读取文件"}, {"cat", "读取文件"},
        {"head", "读取文件"}, {"tail", "读取文件"}, {"wc", "读取文件"}, {"less", "读取文件"},
        {"more", "读取文件"}, {"sed", "读取文件"}, {"awk", "读取文件"}, {"sort", "读取文件"},
        {"uniq", "读取文件"}, {"cut", "读取文件"}, {"tr", "读取文件"},
        {"grep", "查找内容"},
        {"du", "查看空间"}, {"df", "查看空间"},
        {"cp", "修改文件"}, {"mv", "修改文件"}, {"rm", "修改文件"}, {"mkdir", "修改文件"},
        {"touch", "修改文件"}, {"chmod", "修改文件"}, {"ln", "修改文件"},
        {"am", "操作应用"},
        {"pm", "管理应用"},
        {"cmd", "调用系统"},
        {"settings", "修改设置"}, {"content", "修改设置"},
        {"dumpsys", "读取系统状态"},
        {"logcat", "读取日志"},
        {"getprop", "读取系统属性"}, {"setprop", "读取系统属性"},
        {"ps", "查看进程"}, {"top", "查看进程"},
        {"kill", "结束进程"},
        {"curl", "网络请求"}, {"wget", "网络请求"}, {"ping", "网络请求"}, {"nc", "网络请求"},
        {"sh", "运行脚本"}, {"bash", "运行脚本"},
        {"sleep", "等待"},
        {"date", "读取时间"},
        {"unzip", "解包"}, {"tar", "解包"},
        {"sha256sum", "计算校验"}, {"md5sum", "计算校验"}, {"base64", "计算校验"},
    };

    /** ★123：岛上胶囊只用两字（气泡标签太长，会把岛撑长）。 */
    public static String shortName(String name) {
        if (name == null) return "动作";
        if (name.equals("ping")) return "回铃";
        if (name.equals("shell")) return "命令";
        if (name.equals("shot")) return "截图";
        if (name.equals("tap")) return "点按";
        if (name.equals("swipe")) return "滑动";
        if (name.equals("key")) return "按键";
        if (name.equals("ui")) return "读屏";
        if (name.equals("ocr")) return "认字";
        if (name.equals("input")) return "输入";
        if (name.equals("longpress")) return "长按";
        if (name.equals("notify")) return "通知";
        if (name.equals("build")) return "打包";
        if (name.equals("download")) return "下载";
        if (name.equals("download_status")) return "进度";
        if (name.equals("buzz")) return "震动";
        if (name.equals("status")) return "状态";
        if (name.equals("help")) return "目录";
        if (name.equals("acttest")) return "自测";
        if (name.equals("tap_text")) return "点字";
        if (name.equals("wait_text")) return "等字";
        if (name.equals("wait_stable")) return "等稳";
        if (name.equals("scroll")) return "滚动";
        if (name.equals("paste")) return "粘贴";
        if (name.equals("scroll_to_text")) return "找字";
        if (name.equals("do_chain")) return "连做";
        if (name.equals("windows")) return "窗口";
        if (name.equals("clip")) return "剪贴";
        if (name.equals("texts")) return "全屏";
        return "动作";
    }
    /** 气泡第一行：`shell—中文功能`；认不出的一律 `shell—执行命令`（回退，不报错）。 */
    public static String shellAction(String cmd) {
        String f = shellWord(cmd);
        return "shell—" + (f.length() == 0 ? "执行命令" : f);
    }


    /** ★128（2026-10-22）：原子岛胶囊专用——照抄气泡的判定（同一个 shellWord／同一张 SH 表），但压到 4 字以内。
     *  实测 vivo 短胶囊能容 4 个汉字（岛宽约 576px＝屏宽 40%，仍在短胶囊档）。
     *  气泡那边继续用完整文案（shellAction），两边语义一致，只是长度不同。 */
    public static String shellCap(String cmd) {
        String f = shellWord(cmd);
        if (f.length() == 0) return "命令";   // 兜底：认不出时退回工具短名，跟以前一样
        if (f.equals("读取系统状态") || f.equals("读取系统属性")) return "读取系统";   // 5 字压到 4
        return f.length() > 4 ? f.substring(0, 4) : f;
    }
    /** 第一个能映射的词：跳过 cd／export／env／true／set／sudo／su／nohup 与变量赋值、选项、路径前缀。 */
    static String shellWord(String cmd) {
        if (cmd == null) return "";
        String[] tk = cmd.replace('\n', ' ').split("[\\s;|&()`<>]+");
        for (int i = 0; i < tk.length; i++) {
            String t = tk[i].trim();
            while (t.length() > 0 && (t.charAt(0) == '\'' || t.charAt(0) == '"')) t = t.substring(1);
            while (t.length() > 0 && (t.charAt(t.length() - 1) == '\'' || t.charAt(t.length() - 1) == '"')) t = t.substring(0, t.length() - 1);
            if (t.length() == 0 || t.startsWith("-") || t.startsWith("#")) continue;
            int sl = t.lastIndexOf('/');
            if (sl >= 0) { if (sl + 1 >= t.length()) continue; t = t.substring(sl + 1); }
            if (t.indexOf('=') >= 0) continue;   // A=1 变量赋值
            if (t.equals("cd") || t.equals("export") || t.equals("env") || t.equals("true")
                || t.equals("set") || t.equals("sudo") || t.equals("su") || t.equals("nohup")) continue;
            for (int k = 0; k < SH.length; k++) if (SH[k][0].equals(t)) return SH[k][1];
        }
        return "";
    }
}
