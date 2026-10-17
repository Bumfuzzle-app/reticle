package app.reticle;

import android.app.ActivityManager;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.os.Process;

import java.io.File;
import java.io.FileInputStream;
import java.util.Map;

/**
 * 52 号单 · 内存自保（该让路时让路，不硬扛）
 *
 * 为什么有它（2026-10-12 21:35「整机卡死」事故的对策）：Reticle 是常驻前台的服务
 * （液态玻璃重绘 ＋ 媒体心跳 ＋ 无障碍看门狗），内存一紧它就是压舱石之一。用户口径：
 * 「别靠人记着去清后台」——所以让它自己盯内存，紧了自己让路，松了自己回来。
 *
 * 三级让路（判据与时长都集中在下面常量里，用户要可调）：
 *   一级「缓」＝ 看门狗检查 10 秒 → 60 秒 ＋ 停掉非必要的定时刷新（媒体/眼线那条的轮询）；
 *   二级「收」＝ 释放自身缓存（OCR 识别器模型等）＋ 前台通知里给人话一句；
 *   三级「让」＝ 极度紧张持续 ≥1 分钟 → 暂停看门狗重试，只留最小保活。
 * 恢复：可用内存 > 12% 且持续 30 秒 → 自动升回「正常」。
 *
 * ★明令禁止（那正是事故的放大器，本类一条都不做）：**不重启自己、不在紧张时疯狂重试**。
 *
 * 判据：① 主接入点＝WatchdogService.onTrimMemory（系统主动喊话，最准最省电）；
 *       ② 轮询（45 秒）＝ ActivityManager.lowMemory ＋ /proc/meminfo 的
 *          MemAvailable/MemTotal（本机实测可读；PSI 在本机 Permission denied，不用）。
 *
 * 可观测：每次「进/出让路档」写一行日志（在 a11y-watchdog.log 里，带当时可用率／
 * lowMemory／自身 PSS），status 与 /selfcheck 各加一行显示当前档位；
 * 自测口＝槽位命令 `cmd=memtest`（lvl／hold），人工压档、不真造内存危机。
 */
public class MemGuard {

    // ══════════ 档位 ══════════
    public static final int LV_NORMAL = 0;   // 正常
    public static final int LV_EASE   = 1;   // 缓：看门狗 10s→60s，停非必要定时刷新
    public static final int LV_TRIM   = 2;   // 收：释放自身缓存
    public static final int LV_YIELD  = 3;   // 让：暂停看门狗重试，只留最小保活

    // ══════════ 阈值与时长（★集中放这里，用户要可调） ══════════
    static final int  LOW_PCT      = 8;       // MemAvailable/MemTotal < 8% → 紧张
    static final int  CRIT_PCT     = 4;       // < 4% → 极度
    static final int  OK_PCT       = 12;      // > 12% 且持续 OK_HOLD_MS → 回「正常」
    static final long OK_HOLD_MS   = 30000L;  // 恢复需要「连续宽裕」这么久
    static final long CRIT_HOLD_MS = 60000L;  // 极度持续 ≥1 分钟 → 进三级「让」
    static final long POLL_MS      = 45000L;  // 轮询周期（派单要 30~60 秒）
    static final long FIRST_DELAY_MS = 15000L;// 起服务后缓 15 秒再开始看

    // ══════════ 联动参数 ══════════
    static final long WD_MS_NORMAL = 10000L;  // 一级「缓」把看门狗周期从 10 秒放慢到 60 秒
    static final long WD_MS_EASE   = 60000L;

    // ══════════ 现场 ══════════
    static volatile int level = LV_NORMAL;
    static volatile String lastLine = "还没查过";
    static volatile String lastWhy = "";
    private static volatile long critSince = 0L;   // 「极度」从什么时候开始
    private static volatile long okSince = 0L;     // 「宽裕」从什么时候开始
    private static volatile boolean started = false;
    private static volatile Thread thread = null;
    /** 自测口：人工压住的档位与截止时刻（-1＝没压）。 */
    private static volatile int forcedLevel = -1;
    private static volatile long forcedUntil = 0L;

    // ───────── 起线程（幂等）／收工 ─────────
    public static synchronized void start(Context ctx) {
        if (started && thread != null && thread.isAlive()) return;
        started = true;
        final Context c = ctx.getApplicationContext();
        Thread t = new Thread(new Runnable() {
            public void run() {
                sleep(FIRST_DELAY_MS);
                while (true) {
                    try { poll(c, "轮询"); } catch (Throwable x) { }
                    sleep(POLL_MS);
                }
            }
        });
        t.setDaemon(true);
        thread = t;
        t.start();
        A11yWatchdog.log(c, "内存自保上线（紧张<" + LOW_PCT + "%／极度<" + CRIT_PCT + "%／宽裕>"
                + OK_PCT + "% 连续 " + (OK_HOLD_MS / 1000) + " 秒回正常；轮询 " + (POLL_MS / 1000) + " 秒）");
    }

    /** 用户主动停（从最近任务划掉）时收工：不留后台线程。 */
    public static synchronized void stopLoop(Context ctx) {
        started = false;
        Thread t = thread;
        thread = null;
        if (t != null) { try { t.interrupt(); } catch (Throwable x) { } }
        A11yWatchdog.log(ctx, "内存自保下线（用户主动停）");
    }

    /** 系统主动喊话（WatchdogService.onTrimMemory 转过来）：最准最省电的入口。 */
    static void onTrim(Context ctx, int trimLevel) {
        try {
            if (!started) start(ctx);
            int lv;
            if (trimLevel == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) lv = LV_TRIM;
            else if (trimLevel == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) lv = LV_EASE;
            else return;   // 其余档位（UI 隐藏／后台／完整回收）对本前台服务不适用，不动作
            if (lv >= LV_TRIM && critSince == 0L) critSince = System.currentTimeMillis();
            apply(ctx, lv, "系统喊话 onTrimMemory=" + trimLevel);
        } catch (Throwable t) { }
    }

    // ───────── 一轮：判档 → 应用 ─────────
    static synchronized String poll(Context ctx, String why) {
        try {
            long now = System.currentTimeMillis();
            int lv;
            int forced = forcedLevel;
            if (forced >= 0 && now < forcedUntil) {
                lv = forced;
                why = why + "／自测口";
            } else {
                if (forced >= 0) { forcedLevel = -1; forcedUntil = 0L; }
                lv = compute(ctx, now);
            }
            apply(ctx, lv, why);
            return snap(ctx);
        } catch (Throwable t) { return "err:" + t; }
    }

    /** 纯判档（读内存 + 保持/恢复的迟滞），不碰任何联动。 */
    private static int compute(Context ctx, long now) {
        int pct = availPct();
        boolean low = lowMemory(ctx);
        boolean crit = (pct >= 0 && pct < CRIT_PCT);
        boolean tight = (pct >= 0 && pct < LOW_PCT) || low;
        if (crit) {
            if (critSince == 0L) critSince = now;
            okSince = 0L;
            if (now - critSince >= CRIT_HOLD_MS) return LV_YIELD;
            return LV_TRIM;
        }
        critSince = 0L;
        if (tight) { okSince = 0L; return LV_EASE; }
        if (pct > OK_PCT && !low) {
            if (okSince == 0L) okSince = now;
            if (now - okSince >= OK_HOLD_MS) return LV_NORMAL;
        } else {
            okSince = 0L;
        }
        return level;   // 中间地带（4%~12% 之间）保持现档，别来回抖
    }

    /** 档位变了才动联动、才写日志；没变只刷新那句话。 */
    private static synchronized void apply(Context ctx, int lv, String why) {
        if (lv != level) {
            int old = level;
            level = lv;
            try { A11yWatchdog.setInterval(lv >= LV_EASE ? WD_MS_EASE : WD_MS_NORMAL); } catch (Throwable t) { }
            try { A11yWatchdog.setPaused(lv >= LV_YIELD); } catch (Throwable t) { }
            try { Notifier.setQuiet(lv >= LV_EASE); } catch (Throwable t) { }
            if (lv >= LV_TRIM) { try { releaseCaches(ctx); } catch (Throwable t) { } }
            String line = "内存 档位 " + name(old) + " → " + name(lv) + "（" + why + "）｜" + snap(ctx);
            lastWhy = why;
            A11yWatchdog.log(ctx, line);
            lastLine = line;
            try { WatchdogService.refreshNote(ctx); } catch (Throwable t) { }   // 前台通知里给人话一句
        } else {
            lastWhy = why;
            lastLine = "内存 档位 " + name(lv) + "｜" + snap(ctx) + "｜最近原因=" + why;
        }
    }

    /**
     * 二级「收」：放掉自身能放的缓存引用。
     * 实际能放的就是 OCR 识别器（MLKit 中文模型，几十 MB 级，下次用会自动重建）
     * ＋主动 GC 一次；本应用没有别的位图／控件长缓存（链跑的 tpl/fp 缓存是每轮局部变量）。
     */
    private static void releaseCaches(Context ctx) {
        try { OcrEng.drop(); } catch (Throwable t) { }
        try { System.gc(); } catch (Throwable t) { }
    }

    // ───────── 读内存 ─────────
    /** MemAvailable / MemTotal（%）；读不到回 -1。 */
    static int availPct() {
        try {
            long avail = -1, total = -1;
            File f = new File("/proc/meminfo");
            FileInputStream in = new FileInputStream(f);
            byte[] b = new byte[8192];
            int n = in.read(b);
            in.close();
            if (n <= 0) return -1;
            String s = new String(b, 0, n, "UTF-8");
            String[] lines = s.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String ln = lines[i];
                if (ln.startsWith("MemTotal:")) total = num(ln);
                else if (ln.startsWith("MemAvailable:")) avail = num(ln);
            }
            if (total <= 0 || avail < 0) return -1;
            return (int) Math.floor(avail * 100.0 / total);
        } catch (Throwable t) { return -1; }
    }

    private static long num(String line) {
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c >= '0' && c <= '9') sb.append(c);
                else if (sb.length() > 0) break;
            }
            return sb.length() == 0 ? -1 : Long.parseLong(sb.toString());
        } catch (Throwable t) { return -1; }
    }

    /** 系统自己的「内存紧张」判据。 */
    static boolean lowMemory(Context ctx) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return false;
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return mi.lowMemory;
        } catch (Throwable t) { return false; }
    }

    /** 自身 PSS（KB）；读不到回 -1。 */
    static int pssKb(Context ctx) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return -1;
            android.os.Debug.MemoryInfo[] a = am.getProcessMemoryInfo(new int[]{Process.myPid()});
            if (a == null || a.length == 0) return -1;
            return a[0].getTotalPss();
        } catch (Throwable t) { return -1; }
    }

    // ───────── 一句话（status／selfcheck／日志用） ─────────
    static String name(int lv) {
        if (lv == LV_EASE) return Words.MEM_LV1;
        if (lv == LV_TRIM) return Words.MEM_LV2;
        if (lv == LV_YIELD) return Words.MEM_LV3;
        return Words.MEM_LV0;
    }

    /** 数据串：可用率／lowMemory／自身 PSS（给日志与排查用，带数字）。 */
    static String snap(Context ctx) {
        int pct = availPct();
        int pss = pssKb(ctx);
        return "可用 " + (pct < 0 ? "?" : (pct + "%")) + "｜lowMemory=" + (lowMemory(ctx) ? 1 : 0)
                + "｜自身 PSS " + (pss < 0 ? "?" : (pss / 1024 + "MB"));
    }

    /** 给界面／status 的一行：当前档位（正常／让路…）＋当时数据。 */
    public static String statusLine(Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append(name(level));
        int pct = availPct();
        sb.append(Words.MEM_AVAIL_A).append(pct < 0 ? "?" : String.valueOf(pct)).append(Words.MEM_AVAIL_B);
        int pss = pssKb(ctx);
        if (pss >= 0) sb.append(Words.MEM_SELF_A).append(pss / 1024).append(Words.MEM_SELF_B);
        if (level > 0 && lastWhy.length() > 0) sb.append(Words.MEM_WHY_A).append(lastWhy);
        return sb.toString();
    }

    /** 前台通知正文后缀：让路档才加一句人话（正常档回空串）。 */
    static String noteSuffix() {
        if (level == LV_EASE) return Words.NT_MEM1;
        if (level == LV_TRIM) return Words.NT_MEM2;
        if (level == LV_YIELD) return Words.NT_MEM3;
        return "";
    }

    // ───────── 自测口（槽位命令 memtest） ─────────
    /**
     * 人工压一个档位 hold 毫秒，看联动是否跟着走；到点自动升回「正常」。
     * ★不真造内存危机（派单硬要求：别把系统压到危险）。
     * 参数：lvl=1|2|3（默认 3）、hold=毫秒（默认 30000，限 1000~300000）。
     */
    public static String selfTest(Context ctx, Map<String, String> kv) {
        try {
            final Context c = ctx.getApplicationContext();
            start(c);
            int lv = (int) parse(kv.get("lvl"), LV_YIELD);
            if (lv < LV_NORMAL || lv > LV_YIELD) lv = LV_YIELD;
            long hold = parse(kv.get("hold"), 30000L);
            if (hold < 1000L) hold = 1000L;
            if (hold > 300000L) hold = 300000L;
            forcedLevel = lv;
            forcedUntil = System.currentTimeMillis() + hold;
            String before = statusLine(c);
            poll(c, "自测口压档");
            final long holdMs = hold;
            Thread t = new Thread(new Runnable() {
                public void run() {
                    sleep(holdMs);
                    forcedLevel = -1;
                    forcedUntil = 0L;
                    poll(c, "自测结束（自测口自动升回）");
                }
            });
            t.setDaemon(true);
            t.start();
            return "{\"ok\":1,\"before\":\"" + sq(before) + "\",\"level\":" + level
                    + ",\"interval\":" + A11yWatchdog.interval() + ",\"paused\":" + (A11yWatchdog.isPaused() ? 1 : 0)
                    + ",\"note\":\"" + sq(statusLine(c)) + "\",\"after\":\"" + sq(statusLine(c)) + "\""
                    + ",\"hold\":" + hold + ",\"want\":" + lv + "}";
        } catch (Throwable t) {
            return "{\"ok\":0,\"err\":\"" + sq(String.valueOf(t)) + "\"}";
        }
    }

    private static long parse(String s, long dft) {
        try { return (s == null || s.length() == 0) ? dft : Long.parseLong(s.trim()); }
        catch (Throwable t) { return dft; }
    }

    private static String sq(String s) {
        if (s == null) return "";
        return s.replace("\\", "").replace("\"", "'").replace("\n", " ");
    }

    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (Throwable t) { } }
}
