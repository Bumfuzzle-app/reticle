package app.reticle;

import android.content.Context;
import android.provider.Settings;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 53 号单 · 无障碍「连接态」（★在册 ≠ 能用）
 *
 * 无障碍有两个互不相同的状态：
 *   ① 在册（enabled）：系统那份 enabled_accessibility_services 名单里有我们 —— 读得到、好判断，但**不等于能用**；
 *   ② 接上（connected）：系统的 AccessibilityManagerService 真的把我们绑起来了（onServiceConnected 被调用过、
 *      binder 通着、服务对象活着）—— 这才决定 ui／shot 这些工具能不能干活。
 * 进程被杀时系统解绑、却**不会**把名字从名单里划掉 ⇒ 于是「名单里还有、实际没人」——
 * 主界面那张卡与 /selfcheck 的 a11y_in_list 都会照旧报「在册 ✓」＝会骗人（52 单暴露的缺口）。
 *
 * 本类干两件事：
 *   A 连接态判据：进程内标志（只由 VeriService 的回调改）＋「拿得到根节点吗」的轻探针（辅）；
 *   C 修复尝试：把 Reticle 自己那条从名单里删掉 → 再写回（制造一次真变化，逼系统重新绑定）。
 *
 * 诚实边界（详见 53 单交工「没做到／没验到」）：
 *   · 进程被杀后**本机不会自己回来**（52 单实测）——本类只能在"进程已经起来"之后把连接接回来；
 *   · 探针拿不到根节点也可能是屏幕锁着／当前没有窗口，所以探针只作备注，不单独当"断开"的判据；
 *   · 修复只动 Reticle 自己那条（不碰副包、不碰系统里别的辅助服务）。
 */
public class A11yLink {

    // ───────── 三态（给界面与自检） ─────────
    public static final int ST_NOT_IN_LIST = 0;      // 不在册（系统名单里没有我们）
    public static final int ST_IN_LIST_NO_CONN = 1;  // ★在册、但没接上（会骗人的那一种）
    public static final int ST_CONNECTED = 2;        // 已连接（真能用）
    public static final int ST_UNKNOWN = 3;          // 读不到系统设置

    /** 「在册但未连接」至少持续这么久，看门狗才动手自动修（别把一闪而过的中间态当故障）。 */
    static final long GRACE_MS = 15000L;
    /** 两次自动修复之间的最小间隔。 */
    private static final long AUTO_MIN_GAP_MS = 120000L;
    /** 探针最短间隔（只在慢轮询/自检里跑，别进 10 秒热循环）。 */
    private static final long PROBE_MIN_GAP_MS = 30000L;

    /** 连接态真值：只由 VeriService 的回调写（进程内；进程死了自然归零）。 */
    static volatile boolean connected = false;
    static volatile long upAt = 0L;
    static volatile long downAt = 0L;
    static volatile String lastCb = "本进程还没接到无障碍回调";
    /** 辅判据（功能探针）：1＝拿到画面 0＝拿不到 -1＝没探过。 */
    static volatile int probe = -1;
    static volatile long probeAt = 0L;
    static volatile int probeFails = 0;

    private static volatile boolean repairing = false;
    private static volatile long lastRepairAt = 0L;
    private static volatile long noConnSince = 0L;
    private static volatile int lastState = -1;
    private static volatile long lastProbeAt = 0L;

    public static void onUp(String why) {
        connected = true;
        upAt = System.currentTimeMillis();
        lastCb = why + "@" + hm(upAt);
        probe = -1;
    }

    public static void onDown(String why) {
        connected = false;
        downAt = System.currentTimeMillis();
        lastCb = why + "@" + hm(downAt);
    }

    public static boolean isConnected() { return connected; }

    /** 三态判定（在册名单 ＋ 进程内连接标志）。 */
    public static int state(Context ctx) {
        String v = A11yWatchdog.readList(ctx);
        if (v == null) return ST_UNKNOWN;
        if (!A11yWatchdog.hasSvc(v)) return ST_NOT_IN_LIST;
        return connected ? ST_CONNECTED : ST_IN_LIST_NO_CONN;
    }

    public static String label(int st) {
        if (st == ST_CONNECTED) return Words.MA_A11Y_CONN_OK;
        if (st == ST_IN_LIST_NO_CONN) return Words.MA_A11Y_CONN_DISC;
        if (st == ST_NOT_IN_LIST) return Words.MA_A11Y_CONN_NOT;
        return Words.MA_A11Y_CONN_UNK;
    }

    /** 看门狗每轮调用：维护「未连接持续多久」＋状态变化日志，返回三态。 */
    static int observe(Context ctx) {
        int st = state(ctx);
        long now = System.currentTimeMillis();
        if (st == ST_IN_LIST_NO_CONN) {
            if (noConnSince == 0L) {
                noConnSince = now;
                log(ctx, "发现「在册但未连接」（名单里有我们、系统没接上；上次回调 " + lastCb + "）");
            }
        } else if (noConnSince != 0L) {
            noConnSince = 0L;
        }
        if (st != lastState) {
            log(ctx, "状态：" + label(st) + "（" + lastCb + "）");
            lastState = st;
        }
        return st;
    }

    public static long noConnFor() { return noConnSince == 0L ? 0L : (System.currentTimeMillis() - noConnSince); }

    /** 看门狗自动修的门：不在修、两次之间 ≥2 分钟。 */
    static boolean autoRepairAllowed() {
        return !repairing && (System.currentTimeMillis() - lastRepairAt >= AUTO_MIN_GAP_MS);
    }

    /** 给界面用：卡上那句 ＋ 需要时的人话指引。 */
    public static String cardText(Context ctx) { return label(state(ctx)); }

    public static String hintText(Context ctx) {
        int st = state(ctx);
        if (st == ST_IN_LIST_NO_CONN) return Words.MA_A11Y_HINT_DISC;
        if (st == ST_NOT_IN_LIST) return Words.MA_A11Y_HINT_NOT;
        return "";
    }

    /** 一行备注（连接/断开时刻 ＋ 探针）——界面小字与日志用。 */
    public static String detailLine() {
        StringBuilder sb = new StringBuilder();
        sb.append(connected ? "已连接：" : "已断开：").append(lastCb);
        if (connected && upAt > 0) sb.append("（已连 ").append(ago(upAt)).append("）");
        else if (!connected && downAt > 0) sb.append("（已断 ").append(ago(downAt)).append("）");
        if (probe >= 0) sb.append("；探针 ").append(probe == 1 ? "拿到画面 ✓" : "没拿到画面");
        return sb.toString();
    }

    /** 轻探针：拿一次根节点（1＝拿到 0＝拿不到 -1＝没条件探）。只在自检/慢轮询里调。 */
    public static int probeNow() {
        if (!connected) { probe = -1; return probe; }
        long now = System.currentTimeMillis();
        if (now - lastProbeAt < PROBE_MIN_GAP_MS && probe >= 0) return probe;
        lastProbeAt = now;
        probeAt = now;
        int r;
        try { r = VeriService.probeRoot(); } catch (Throwable t) { r = 0; }
        probe = r;
        if (r == 1) probeFails = 0; else probeFails++;
        return probe;
    }

    /**
     * C · 修复：删名 → 写回（只动 Reticle 自己那条）。
     * force=false 时给"未连接持续多久"留门（看门狗自动用）；用户点按钮时传 force=true。
     * 返回一句人话。
     */
    public static synchronized String repair(Context ctx, boolean force) {
        if (connected) return Words.A11Y_REP_ALREADY;
        if (repairing) return Words.A11Y_REP_BUSY;
        if (!force && noConnSince != 0L && noConnFor() < GRACE_MS) return Words.A11Y_REP_TOOSOON;
        repairing = true;
        lastRepairAt = System.currentTimeMillis();
        try {
            String cur = A11yWatchdog.readList(ctx);
            if (cur == null) { log(ctx, "修复：读不到系统设置，放弃"); return Words.A11Y_REP_NOREAD; }
            if (!A11yWatchdog.hasSvc(cur)) {
                // 不在册：直接写回（不走 tick，免得与看门狗互锁）
                String want = A11yWatchdog.join(cur);
                log(ctx, "修复：不在册 → 直接写回 [" + want + "]");
                writeList(ctx, want);
                sleep(1300L);
                boolean back = A11yWatchdog.hasSvc(A11yWatchdog.readList(ctx));
                log(ctx, "修复：写回后读回＝" + (back ? "在册" : "仍不在册"));
                waitConnect(6000L);
                if (connected) { log(ctx, "修复：成功（写回名单）"); return Words.A11Y_REP_OK; }
                return back ? Words.A11Y_REP_WROTE_NO_CONN : Words.A11Y_REP_WRITEFAIL;
            }
            // ★在册但未连接：制造一次真变化 —— 先把自己那条删掉，再写回
            String off = stripSelf(cur);
            log(ctx, "修复：删名 →[" + off + "]");
            writeList(ctx, off);
            sleep(1300L);
            String mid = A11yWatchdog.readList(ctx);
            boolean removed = !A11yWatchdog.hasSvc(mid);
            log(ctx, "修复：删名读回＝" + (removed ? "已剔掉" : "还在") + " [" + (mid == null ? "null" : mid) + "]");
            writeList(ctx, cur);
            sleep(1300L);
            String back2 = A11yWatchdog.readList(ctx);
            boolean restored = A11yWatchdog.hasSvc(back2);
            log(ctx, "修复：写回读回＝" + (restored ? "在册" : "不在册") + " [" + (back2 == null ? "null" : back2) + "]");
            waitConnect(7000L);
            if (connected) { log(ctx, "修复：成功（删名→写回）"); return Words.A11Y_REP_OK; }
            if (!removed) {
                log(ctx, "修复：删名那一步没落地");
                return Words.A11Y_REP_NOSTRIP;
            }
            if (!restored) {
                log(ctx, "修复：名单没写回去");
                return Words.A11Y_REP_WRITEFAIL;
            }
            log(ctx, "修复：删名→写回都做了，系统仍没接上（时长 " + (System.currentTimeMillis() - lastRepairAt) + "ms）");
            return Words.A11Y_REP_NOGO;
        } catch (Throwable t) {
            log(ctx, "修复异常：" + t);
            return Words.A11Y_REP_ERR + t;
        } finally {
            repairing = false;
        }
    }

    /** 把 Reticle 自己那条从名单里剔掉（副包 app.reticle.b/… 与系统里别的辅助服务原样保留）。 */
    static String stripSelf(String v) {
        if (v == null) return "";
        StringBuilder sb = new StringBuilder();
        String[] parts = v.split(":");
        for (int i = 0; i < parts.length; i++) {
            String s = parts[i] == null ? "" : parts[i].trim();
            if (s.length() == 0) continue;
            if (s.startsWith("app.reticle/")) continue;
            if (s.equals("reticle-selftest")) continue;
            if (sb.length() > 0) sb.append(':');
            sb.append(s);
        }
        return sb.toString();
    }

    private static void writeList(Context ctx, String v) {
        try {
            Settings.Secure.putString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, v == null ? "" : v);
        } catch (Throwable t) { log(ctx, "写名单失败：" + t); }
    }

    private static void waitConnect(long maxMs) {
        long t0 = System.currentTimeMillis();
        while (!connected && System.currentTimeMillis() - t0 < maxMs) sleep(400L);
    }

    private static String ago(long t) {
        long d = Math.max(0L, System.currentTimeMillis() - t);
        if (d < 60000) return (d / 1000) + " 秒";
        if (d < 3600000) return (d / 60000) + " 分钟";
        return (d / 3600000) + " 小时";
    }

    private static String hm(long ms) {
        try { return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(ms)); } catch (Throwable t) { return ""; }
    }

    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (Throwable t) { } }

    static void log(Context ctx, String s) {
        try { A11yWatchdog.log(ctx, "连接态｜" + s); } catch (Throwable t) { }
    }
}
