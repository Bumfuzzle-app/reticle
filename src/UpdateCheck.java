package app.reticle;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 79 号单（2026-10-17）建；84 号单（2026-10-17）收口。
 *
 * 口径（写死，别改着玩）：
 *  · 每次进前台问一次；两次检查最少隔 10 分钟（84 号单从 24 小时收紧）。
 *  · 匿名、一个 GET：固定 UA、不带设备标识、不带令牌、不带 query。
 *  · 有新版且用户没说「不再提示」⇒ 弹窗；同一次前台会话只弹一次。
 *  · 下载只在用户点「立即更新」时发生；断点续传（Range + .part），断线自动重试 2 次。
 *  · 下完按 Release 资产自带的 digest 算 sha256 比对，不符就地删除并说人话。
 *  · 装＝拉系统安装器，用户自己点确认（REQUEST_INSTALL_PACKAGES + FileProvider）。
 *  · 版本比较用「数字三段」，绝不用版本号字符串比。
 *  · 「更新源」只换检查地址；下载地址一律取返回 JSON 里的 browser_download_url。
 */
public class UpdateCheck {

    static final String PREFS = "settings";
    static final String K_ON = "upd_on";          // 开关（默认开）
    static final String K_LAST = "upd_last_ms";   // 上次检查时刻（毫秒）
    static final String K_SEEN = "upd_seen_ver";  // 用户点过「不再提示」的版本（tag）
    static final String K_API = "upd_api";        // 自定义检查地址（留空＝官方）
    static final String K_LAST_EP = "upd_last_ep";   // 上次检查用的源（换源就不受节流限制）

    public static final String DEFAULT_API = "https://api.github.com/repos/Bumfuzzle-app/reticle/releases/latest";
    static final String UA = "Reticle-UpdateCheck";        // 固定串：不带机型／账号
    static final long GAP_MS = 10L * 60L * 1000L;          // 84 号单：10 分钟节流
    static final int TIMEOUT = 10000;                      // 连接／读取各 10 秒
    static final int RETRY = 2;                            // 84 号单：失败自动重试 2 次（总试 3 趟）
    static final int BODY_MAX = 2000;                      // 公告截断（字）

    // ───────────────────────── 开关与设置 ─────────────────────────

    public static boolean on(Context c) {
        try { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(K_ON, true); }
        catch (Throwable t) { return true; }
    }

    public static void setOn(Context c, boolean v) {
        try { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(K_ON, v).apply(); } catch (Throwable t) { }
    }

    public static long lastMs(Context c) {
        try { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(K_LAST, 0L); }
        catch (Throwable t) { return 0L; }
    }

    static void setLastMs(Context c, long v) {
        try { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(K_LAST, v).apply(); } catch (Throwable t) { }
    }

    static String lastEp(Context c) {
        try { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_LAST_EP, ""); }
        catch (Throwable t) { return ""; }
    }

    static void setLastEp(Context c, String v) {
        try { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(K_LAST_EP, v == null ? "" : v).apply(); } catch (Throwable t) { }
    }

    /** 用户点过「不再提示」的版本（正式键；验收／镜像源不写这个）。 */
    public static String seenVer(Context c) {
        try { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_SEEN, ""); }
        catch (Throwable t) { return ""; }
    }

    static void setSeenVer(Context c, String v) {
        try { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(K_SEEN, v == null ? "" : v).apply(); } catch (Throwable t) { }
    }

    // 验收／镜像源的记录单独放（dev 键），用户看得见的状态只写正式键
    // 96 号单：查到过的那一次（版本＋下载地址）落盘 —— 配置页要一直能复制，重启也在
    static final String K_INFO_TAG = "upd_info_tag";
    static final String K_INFO_URL = "upd_info_url";

    public static void saveInfo(Context c, String tag, String url) {
        try { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(K_INFO_TAG, tag == null ? "" : tag)
                .putString(K_INFO_URL, url == null ? "" : url).apply(); } catch (Throwable t) { }
    }

    public static String infoTag(Context c) { return getS(c, K_INFO_TAG); }
    public static String infoUrl(Context c) { return getS(c, K_INFO_URL); }

    public static void clearInfo(Context c) {
        try { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(K_INFO_TAG).remove(K_INFO_URL).apply(); } catch (Throwable t) { }
    }

    static final String K_SEEN_DEV = "upd_seen_ver_dev";
    static final String K_LAST_DEV = "upd_last_ms_dev";

    static String getS(Context c, String k) {
        try { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(k, ""); }
        catch (Throwable t) { return ""; }
    }

    static void putS(Context c, String k, String v) {
        try { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(k, v == null ? "" : v).apply(); } catch (Throwable t) { }
    }

    /** 当前是不是「验收／镜像源」（文件口或设置项改了地址）。是 ⇒ 提示记录只写 dev 键，绝不进用户状态。 */
    public static boolean overridden(Context c) {
        try {
            String ep = endpoint(c);
            return ep != null && ep.length() > 0 && !ep.equals(DEFAULT_API);
        } catch (Throwable t) { return false; }
    }

    /** 清除更新记录（上次检查时刻／已提示版本／上次用的源，含验收侧那份）。 */
    public static void clearRecords(Context c) {
        try {
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .remove(K_LAST).remove(K_SEEN).remove(K_LAST_EP).remove(K_INFO_TAG).remove(K_INFO_URL)
                    .remove(K_LAST_DEV).remove(K_SEEN_DEV).apply();
            log(c, "已清除更新记录（上次检查／已提示版本／上次的源）");
        } catch (Throwable t) { }
    }

    /** 开一次就做一次「残留体检」：上一版把假源验收的结果写进了正式键，搬去 dev 键。 */
    public static void migrate(Context c) {
        try {
            android.content.SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String ep = p.getString(K_LAST_EP, "");
            if (ep != null && ep.length() > 0 && !ep.equals(DEFAULT_API)) {
                String s = p.getString(K_SEEN, "");
                android.content.SharedPreferences.Editor e = p.edit().remove(K_SEEN).remove(K_LAST).remove(K_LAST_EP);
                if (s != null && s.length() > 0) e.putString(K_SEEN_DEV, s);
                e.apply();
                log(c, "清掉验收残留：上次检查/已提示版本来自非官方源（" + ep + "）");
            }
        } catch (Throwable t) { }
    }

    /** 自定义检查地址（设置项） */
    public static String apiOverride(Context c) {
        try { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_API, "").trim(); }
        catch (Throwable t) { return ""; }
    }

    public static void setApiOverride(Context c, String v) {
        try { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(K_API, v == null ? "" : v.trim()).apply(); } catch (Throwable t) { }
    }

    /**
     * 实际用的检查地址。三层（先命中先用）：
     *  ①验收／镜像口：/sdcard/Reticle/upd_endpoint.txt 里写一行完整地址
     *  ②设置项（配置页「更新源」）
     *  ③官方地址
     * ★只换「检查新版」的地址；下载地址仍取返回 JSON 里的 browser_download_url。
     */
    public static String endpoint(Context c) {
        try {
            File f = new File(Home.get(c), "upd_endpoint.txt");
            if (f.isFile()) {
                String s = readSmall(f).trim();
                if (s.length() > 0 && (s.startsWith("http://") || s.startsWith("https://"))) return s;
            }
        } catch (Throwable t) { }
        String o = apiOverride(c);
        return (o.length() > 0) ? o : DEFAULT_API;
    }

    /** 把检查结果写一行到 Reticle/logs/update.log（失败也写，界面上静默） */
    public static void log(Context c, String msg) {
        try {
            File d = Home.sub(c, "logs");
            File f = new File(d, "update.log");
            if (f.length() > 512 * 1024) f.delete();
            String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date()) + "  " + msg + "\n";
            FileOutputStream o = new FileOutputStream(f, true);
            o.write(line.getBytes("UTF-8"));
            o.close();
        } catch (Throwable t) { }
    }

    // ───────────────────────── 版本比较 ─────────────────────────

    /** "v0.28.36" / "0.28.36" → {0,28,36}；解析不出来给 null。 */
    public static int[] parseVer(String v) {
        if (v == null) return null;
        String s = v.trim();
        if (s.length() > 0 && (s.charAt(0) == 'v' || s.charAt(0) == 'V')) s = s.substring(1);
        if (s.length() == 0) return null;
        String[] p = s.split("\\.");
        if (p.length < 2) return null;
        int[] r = new int[]{0, 0, 0};
        for (int i = 0; i < 3 && i < p.length; i++) {
            StringBuilder d = new StringBuilder();
            for (int k = 0; k < p[i].length(); k++) {
                char ch = p[i].charAt(k);
                if (ch >= '0' && ch <= '9') d.append(ch); else break;
            }
            r[i] = (d.length() == 0) ? 0 : Integer.parseInt(d.toString());
        }
        return r;
    }

    /** a<b → -1；a==b → 0；a>b → 1（按数字三段逐段比，不做字符串比较） */
    public static int cmp(int[] a, int[] b) {
        if (a == null || b == null) return 0;
        for (int i = 0; i < 3; i++) {
            if (a[i] < b[i]) return -1;
            if (a[i] > b[i]) return 1;
        }
        return 0;
    }

    /** 本机版本名（PackageManager） */
    public static String localVer(Context c) {
        try {
            android.content.pm.PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return (p.versionName == null) ? "" : p.versionName;
        } catch (Throwable t) { return ""; }
    }

    // ───────────────────────── 结果 ─────────────────────────

    public static class Info {
        public String tag = "";        // 线上版本（tag，如 v0.28.36）
        public String url = "";        // 资产下载地址
        public String digest = "";     // "sha256:…"
        public String assetName = "";
        public String body = "";       // 84 号单：Release 的说明原文（markdown，当不可信文本处理）
        public long size = 0L;
        public boolean newer = false;
        public String err = "";        // 失败原因（给日志，不给人看）
    }

    public interface CB { void onResult(Info info, boolean skipped); }

    /** 84 号单：进度回调。pct<0 ＝正在校验；bps＜=0＝速度未知；etaSec<0＝剩余未知。 */
    public interface DCB {
        void onProgress(int pct, long got, long total, long bps, long etaSec);
        void onDone(File f, boolean ok, String msg);
        void onNote(String msg);   // 96 号单：换中转时跟界面说一句人话
    }

    // ───────────────────────── 检查 ─────────────────────────

    /** 每次进前台调：过 10 分钟节流才真的发请求；没到点就静默跳过。 */
    public static void checkOnOpen(final Context c, final CB cb) {
        if (!on(c)) { log(c, "跳过：开关关着"); return; }
        String epNow = endpoint(c);
        String epLast = lastEp(c);
        if (!epNow.equals(epLast)) {
            log(c, "换源（" + epLast + " → " + epNow + "）⇒ 不受节流限制，立即查");
            fetchAsync(c, cb, "换源");
            return;
        }
        long last = lastMs(c);
        long now = System.currentTimeMillis();
        if (last > 0 && now - last < GAP_MS) {
            log(c, "跳过：距上次 " + ((now - last) / 1000L) + " 秒，未满 10 分钟");
            return;
        }
        fetchAsync(c, cb, "进前台");
    }

    /** 配置页「立即检查」：跳过节流（用户明确要查），但照样记时间戳。 */
    public static void checkNow(final Context c, final CB cb) {
        fetchAsync(c, cb, "手动");
    }

    static void fetchAsync(final Context c, final CB cb, final String why) {
        final Context app = c.getApplicationContext();
        new Thread(new Runnable() { public void run() {
            final Info inf = fetch(app);
            try {
                if (inf.err.length() == 0) {
                    setLastMs(app, System.currentTimeMillis());
                    setLastEp(app, endpoint(app));
                    log(app, "检查(" + why + ") 源=" + endpoint(app) + " 线上=" + inf.tag + " 本机=" + localVer(app)
                            + (inf.newer ? " ⇒ 有新版" : " ⇒ 已是最新") + " 公告=" + inf.body.length() + " 字");
                } else {
                    log(app, "检查(" + why + ") 源=" + endpoint(app) + " 失败：" + inf.err);
                }
            } catch (Throwable t) { }
            if (cb != null) cb.onResult(inf, false);
        } }, "upd-check").start();
    }

    /** 同步查一次（后台线程里调）。不抛异常，失败写 err。 */
    public static Info fetch(Context c) {
        Info inf = new Info();
        HttpURLConnection conn = null;
        try {
            String ep = endpoint(c);
            URL u = new URL(ep);
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("Accept-Encoding", "identity");
            int code = conn.getResponseCode();
            if (code != 200) { inf.err = "HTTP " + code; return inf; }
            String body = readAll(conn.getInputStream(), 262144);
            JSONObject o = new JSONObject(body);
            inf.tag = o.optString("tag_name", "");
            inf.body = o.optString("body", "");       // 84 号单：更新公告
            JSONArray as = o.optJSONArray("assets");
            String bestName = "", bestUrl = "", bestDigest = "";
            long bestSize = 0L;
            if (as != null) {
                for (int i = 0; i < as.length(); i++) {
                    JSONObject a = as.optJSONObject(i);
                    if (a == null) continue;
                    String nm = a.optString("name", "");
                    String ur = a.optString("browser_download_url", "");
                    if (nm.length() == 0 || ur.length() == 0) continue;
                    if (!nm.toLowerCase(Locale.ROOT).endsWith(".apk")) continue;
                    bestName = nm; bestUrl = ur;
                    bestDigest = a.optString("digest", "");
                    bestSize = a.optLong("size", 0L);
                    break;
                }
            }
            inf.assetName = bestName; inf.url = bestUrl; inf.digest = bestDigest; inf.size = bestSize;
            int[] remote = parseVer(inf.tag);
            int[] local = parseVer(localVer(c));
            if (remote == null) { inf.err = "tag 解析不出数字（" + inf.tag + "）"; return inf; }
            inf.newer = cmp(remote, local) > 0;
            if (inf.newer && bestUrl.length() == 0) { inf.err = "有新版但没有 apk 资产"; inf.newer = false; }
        } catch (Throwable t) {
            String m = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : (": " + t.getMessage()));
            inf.err = m.length() > 90 ? m.substring(0, 90) : m;
        } finally {
            try { if (conn != null) conn.disconnect(); } catch (Throwable t) { }
        }
        return inf;
    }

    /** 这个版本用户是否点过「不再提示」。 */
    public static boolean seenAlready(Context c, String tag) {
        if (tag == null || tag.length() == 0) return false;
        String k = overridden(c) ? K_SEEN_DEV : K_SEEN;
        return tag.equals(getS(c, k));
    }

    /** 用户点了「不再提示」：记下当前线上版本，同版本永久不弹（下一个版本照弹）。 */
    public static void markSeen(Context c, String tag) {
        if (tag == null || tag.length() == 0) return;
        String k = overridden(c) ? K_SEEN_DEV : K_SEEN;
        putS(c, k, tag);
        log(c, "用户点了「不再提示」⇒ 记下 " + tag + (k.equals(K_SEEN) ? "" : "（只进 dev 键）"));
    }

    // ───────────────────────── 公告清洗（84 号单） ─────────────────────────

    /**
     * Release 的 body 是网络来的不可信文本：一律按纯文本显示。
     * 去掉 markdown 符号（**、#、| 表格、链接只留文字、HTML 标签、裸网址），合并空行，超 2000 字截断。
     */
    public static String plainBody(String md) {
        String r = "";
        try { r = plainBody0(md); } catch (Throwable t) { r = ""; }
        if (r.length() == 0) r = "这次没有写说明";
        return r;
    }

    static String plainBody0(String md) {
        if (md == null) return "";
        String s = md.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder sb = new StringBuilder();
        String[] lines = s.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i].trim();
            if (l.length() == 0) { sb.append('\n'); continue; }
            if (l.startsWith("```")) continue;                              // 代码围栏
            if (l.indexOf('|') >= 0) {
                boolean sep = true;
                for (int k = 0; k < l.length(); k++) {
                    char ch = l.charAt(k);
                    if (ch != '|' && ch != '-' && ch != ':' && ch != ' ' && ch != '\t') { sep = false; break; }
                }
                if (sep) continue;                                          // 表格分隔行
                l = l.replace('|', ' ');
            }
            l = l.replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "");               // 图片整条去掉
            l = l.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");            // 链接只留文字
            l = l.replaceAll("<[^>]{0,300}>", "");                          // HTML 标签
            l = l.replaceAll("https?://[^\\s)]+", "");                       // 裸网址
            l = l.replaceAll("[*_~`]+", "");                                // 强调／代码符
            l = l.replaceAll("^#{1,6}\\s*", "");                            // 标题号
            l = l.replaceAll("^>+\\s*", "");                                // 引用
            l = l.replaceAll("^[-*+]\\s+", "");                             // 无序列表符
            l = l.replaceAll("^\\d{1,3}[.)、]\\s*", "");                     // 有序列表号
            l = l.trim();
            l = l.replaceAll("[，、,和与及\\s]+$", "");   // 删掉裸网址后留下的连接词
            if (l.length() == 0) continue;
            sb.append(l).append('\n');
        }
        String out = sb.toString().replaceAll("\n{3,}", "\n\n").trim();
        if (out.length() > BODY_MAX) out = out.substring(0, BODY_MAX) + "……";
        return out;
    }

    // ───────────────────────── 下载（96 号单：直连 8 秒零字节 ⇒ 自动换中转） ─────────────────────────

    /** 中转前缀（96 号单）。界面文案里绝不出现这两个域名。 */
    static final String[] RELAYS = {"https://gh-proxy.com/", "https://ghfast.top/"};
    static final int FIRST_BYTE_MS = 8000;      // 8 秒零字节 ⇒ 判这条线走不通

    /**
     * 下到 Reticle/dl/（没有就建）；先写 <名>.part，校验通过才改名。
     * 96 号单：直连 8 秒零字节 ⇒ 换中转（按 RELAYS 顺序）；换源要跟界面说一句人话。
     * 断了接着下：Range: bytes=N-，从 .part 现有长度起；服务器不支持续传就从头。
     * 同一条线有进展但中断 ⇒ 自动重试 2 次（同一份 .part，绝不丢已下的字节）。
     */
    public static void downloadAsync(final Context c, final Info inf, final DCB cb) {
        final Context app = c.getApplicationContext();
        new Thread(new Runnable() { public void run() {
            File out = null;
            File part = null;
            try {
                if (inf == null || inf.url.length() == 0) { cb.onDone(null, false, "没有下载地址"); return; }
                File dir = Home.sub(app, "dl");
                String nm = inf.assetName.length() > 0 ? inf.assetName : ("reticle-" + inf.tag + ".apk");
                nm = nm.replace('/', '_').replace('\\', '_');
                out = new File(dir, nm);
                part = new File(dir, nm + ".part");
                long total = inf.size > 0 ? inf.size : 0L;
                long got = 0L;
                boolean ok = false;
                boolean saidRelay = false;

                String[] srcs = new String[1 + RELAYS.length];
                srcs[0] = inf.url;
                for (int i = 0; i < RELAYS.length; i++) srcs[i + 1] = RELAYS[i] + inf.url;

                for (int si = 0; si < srcs.length && !ok; si++) {
                    String src = srcs[si];
                    boolean relay = si > 0;
                    for (int attempt = 0; attempt <= RETRY && !ok; attempt++) {
                        long have = part.isFile() ? part.length() : 0L;
                        if (total > 0 && have > total) { part.delete(); have = 0L; log(app, "下载 本地残留比完整文件还大 ⇒ 丢掉重下"); }
                        if (total > 0 && have == total) { got = have; ok = true; break; }
                        if (have > 0) log(app, "下载 续传起点 Range: bytes=" + have + "-" + (relay ? "（中转）" : ""));
                        if (relay && !saidRelay) {
                            saidRelay = true;
                            log(app, "下载 直连走不通 ⇒ 换中转（第 " + si + " 个）");
                            try { cb.onNote("直连走不通，正在用中转"); } catch (Throwable t) { }
                        }

                        Try r = tryOnce(app, src, part, total, have, cb);
                        if (r.total > 0) total = r.total;
                        if (r.code == 0) { got = r.written; ok = true; break; }
                        if (r.code == 1) {   // 零字节：这条线走不通 ⇒ 换下一条，不算重试
                            log(app, "下载 这条线 8 秒一个字节都没进来（" + (relay ? "中转" : "直连") + "）");
                            if (part.isFile() && part.length() == 0) { try { part.delete(); } catch (Throwable t) { } }
                            break;
                        }
                        if (r.code == 3) {   // 404／403 之类，重试没用
                            log(app, "下载 这条线不用再试：" + r.msg);
                            break;
                        }
                        if (attempt < RETRY) {
                            try { Thread.sleep(800L * (attempt + 1)); } catch (Throwable x) { }
                        } else {
                            log(app, "下载 这条线重试 " + RETRY + " 次仍失败：" + r.msg);
                        }
                    }
                }

                if (!ok) {
                    long keep = part.isFile() ? part.length() : 0L;
                    log(app, "下载 放弃（直连与中转都没下动），已保留 " + keep + " B 供下次续传");
                    cb.onDone(null, false, keep > 0 ? "这次没下动，网络好一点再接着下" : "这次没下动，检查一下网络再试");
                    return;
                }

                cb.onProgress(-1, got, total, 0L, 0L);   // -1 ＝正在校验
                if (inf.digest == null || inf.digest.length() == 0) {
                    log(app, "校验 缺少 digest ⇒ 删除（不敢装没法核的包）");
                    try { part.delete(); } catch (Throwable t) { }
                    cb.onDone(null, false, "这次更新包没有校验值，已停止安装");
                    return;
                }
                String want = inf.digest.trim();
                int p = want.indexOf(':');
                if (p >= 0) want = want.substring(p + 1);
                want = want.trim().toLowerCase(Locale.ROOT);
                String gotSha = sha256Of(part);
                if (!want.equals(gotSha)) {
                    log(app, "校验 不符 want=" + want + " got=" + gotSha + " ⇒ 删除");
                    try { part.delete(); } catch (Throwable t) { }
                    try { if (out.exists()) out.delete(); } catch (Throwable t) { }
                    cb.onDone(null, false, "下载的文件不完整，已删除");
                    return;
                }
                if (!looksLikeApk(part)) {
                    log(app, "校验 文件头不是 PK ⇒ 删除（防下到网页）");
                    try { part.delete(); } catch (Throwable t) { }
                    cb.onDone(null, false, "下到的不是安装包，已删除");
                    return;
                }
                if (out.exists()) out.delete();
                if (!part.renameTo(out)) {
                    log(app, "改名失败（part 留在原地）");
                    cb.onDone(null, false, "文件保存失败");
                    return;
                }
                log(app, "下载 完成 " + out.getAbsolutePath() + " " + got + " B sha=" + gotSha + " 校验通过");
                cb.onDone(out, true, out.getAbsolutePath());
            } catch (Throwable t) {
                log(app, "下载 异常 " + t.getClass().getSimpleName() + " " + (t.getMessage() == null ? "" : t.getMessage()));
                cb.onDone(null, false, "这次没下成，稍后再试");
            }
        } }, "upd-dl").start();
    }

    /** apk 头两字节是 PK；不是 ⇒ 多半下到了网页／错误页。 */
    static boolean looksLikeApk(File f) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            return in.read() == 'P' && in.read() == 'K';
        } catch (Throwable t) {
            return false;
        } finally {
            try { if (in != null) in.close(); } catch (Throwable t) { }
        }
    }

    /** 一条线试一次的结果。code：0＝下完；1＝8 秒零字节（换线）；2＝有进展但断了（可续传重试）；3＝重试没用。 */
    static class Try {
        int code = 2;
        String msg = "";
        long written = 0L;
        long total = 0L;
    }

    /**
     * 试一条线（下载 URL 或中转前缀＋URL），全程最多一次。
     * 首字节监控：8 秒还没进一个字节就 disconnect，让 read 立刻抛出来（比干等 20 秒快）。
     */
    static Try tryOnce(final Context app, String src, File part, long total0, final long have0, final DCB cb) {
        final Try r = new Try();
        r.total = total0;
        r.written = have0;
        HttpURLConnection conn = null;
        Thread watch = null;
        final boolean[] gotByte = new boolean[]{false};
        final HttpURLConnection[] box = new HttpURLConnection[1];
        FileOutputStream fo = null;
        InputStream in = null;
        try {
            conn = (HttpURLConnection) new URL(src).openConnection();
            box[0] = conn;
            conn.setConnectTimeout(FIRST_BYTE_MS);
            conn.setReadTimeout(20000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Accept-Encoding", "identity");
            if (have0 > 0) conn.setRequestProperty("Range", "bytes=" + have0 + "-");

            watch = new Thread(new Runnable() { public void run() {
                try { Thread.sleep(FIRST_BYTE_MS); } catch (Throwable t) { return; }
                if (!gotByte[0]) { try { box[0].disconnect(); } catch (Throwable t) { } }
            } }, "upd-firstbyte");
            watch.setDaemon(true);
            watch.start();

            int code = conn.getResponseCode();
            boolean append;
            if (code == 206) {
                append = true;
                long t2 = crTotal(conn.getHeaderField("Content-Range"));
                if (t2 > 0) r.total = t2;
                else if (r.total <= 0) {
                    long cl = conn.getContentLength();
                    if (cl > 0) r.total = have0 + cl;
                }
                log(app, "下载 服务器给了 206，从 " + have0 + " B 接着下（总 " + r.total + " B）");
            } else if (code == 200) {
                append = false;
                long cl = conn.getContentLength();
                if (cl > 0) r.total = cl;
                if (have0 > 0) log(app, "下载 服务器不支持续传（200）⇒ 从头下");
            } else {
                r.code = 3;
                r.msg = "HTTP " + code;
                return r;
            }

            in = conn.getInputStream();
            fo = new FileOutputStream(part, append);
            byte[] buf = new byte[32768];
            long cur = append ? have0 : 0L, lastT = System.currentTimeMillis(), lastB = cur, bps = 0L, lastCb = 0L;
            long t0 = System.currentTimeMillis();
            int n;
            while ((n = in.read(buf)) > 0) {
                gotByte[0] = true;
                fo.write(buf, 0, n);
                cur += n;
                r.written = cur;
                long now = System.currentTimeMillis();
                long dt = now - lastT;
                if (dt >= 400) {
                    long inst = (cur - lastB) * 1000L / Math.max(1L, dt);
                    bps = (bps <= 0) ? inst : (bps * 6 + inst * 4) / 10;
                    lastT = now; lastB = cur;
                }
                if (now - lastCb >= 400) {
                    lastCb = now;
                    int pct = r.total > 0 ? (int) (cur * 100L / r.total) : 0;
                    long eta = (bps > 0 && r.total > 0) ? Math.max(0L, (r.total - cur) / bps) : -1L;
                    try { cb.onProgress(pct, cur, r.total, bps, eta); } catch (Throwable t) { }
                }
                if (now - t0 > 20L * 60L * 1000L) throw new java.io.IOException("总时长超了");
            }
            if (r.total > 0 && cur < r.total) throw new java.io.IOException("提前结束 " + cur + "/" + r.total);
            if (r.total <= 0) r.total = cur;
            r.written = cur;
            r.code = 0;
            return r;
        } catch (Throwable t) {
            long haveNow = part.isFile() ? part.length() : 0L;
            r.written = haveNow;
            r.msg = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : (" " + t.getMessage()));
            r.code = (haveNow <= have0) ? 1 : 2;   // 一个字节都没多进 ⇒ 这条线走不通
            log(app, "下载 本条线中断（已下 " + haveNow + " B）：" + r.msg);
            return r;
        } finally {
            try { if (watch != null) watch.interrupt(); } catch (Throwable t) { }
            try { if (in != null) in.close(); } catch (Throwable t) { }
            try { if (fo != null) fo.close(); } catch (Throwable t) { }
            try { if (conn != null) conn.disconnect(); } catch (Throwable t) { }
        }
    }

    /** Content-Range: bytes 100-999/1000 → 1000；解析不出给 -1。 */
    static long crTotal(String cr) {
        try {
            if (cr == null) return -1L;
            int p = cr.lastIndexOf('/');
            if (p < 0) return -1L;
            String s = cr.substring(p + 1).trim();
            if (s.equals("*")) return -1L;
            return Long.parseLong(s);
        } catch (Throwable t) { return -1L; }
    }
    // ───────────────────────── 安装 ─────────────────────────

    public static boolean canInstall(Context c) {
        try {
            if (Build.VERSION.SDK_INT < 26) return true;
            return c.getPackageManager().canRequestPackageInstalls();
        } catch (Throwable t) { return true; }
    }

    public static void openInstallSetting(Context c) {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            i.setData(Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Throwable t) { }
    }

    /** 拉系统安装器（用户自己点「安装」；不静默、不绕过）。返回 null＝成功拉起了安装器，否则回一句人话。 */
    public static String install(Context c, File apk) {
        try {
            if (apk == null || !apk.isFile() || apk.length() == 0) return "安装包不在了，请重新下载";
            Uri uri = FileProvider.getUriForFile(c, c.getPackageName() + ".fileprovider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return null;
        } catch (Throwable t) {
            return "没能打开系统安装器";
        }
    }

    // ───────────────────────── 小工具 ─────────────────────────

    static String readAll(InputStream in, int cap) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            sb.append(line).append('\n');
            if (sb.length() > cap) break;
        }
        r.close();
        return sb.toString();
    }

    static String readSmall(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            StringBuilder sb = new StringBuilder();
            byte[] b = new byte[1024];
            int n;
            while ((n = in.read(b)) > 0) {
                sb.append(new String(b, 0, n, "UTF-8"));
                if (sb.length() > 4096) break;
            }
            in.close();
            return sb.toString();
        } catch (Throwable t) { return ""; }
    }

    public static String sha256Of(File f) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            FileInputStream in = new FileInputStream(f);
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0) md.update(b, 0, n);
            in.close();
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < d.length; i++) {
                String h = Integer.toHexString(d[i] & 0xFF);
                if (h.length() == 1) sb.append('0');
                sb.append(h);
            }
            return sb.toString();
        } catch (Throwable t) { return ""; }
    }
}
