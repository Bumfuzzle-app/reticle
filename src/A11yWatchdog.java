package app.reticle;

import android.content.Context;
import android.provider.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 27 号单 · 无障碍看门狗
 *
 * 为什么有它：vivo／一加自 2024 起有系统级机制——会定期（最长约 5 小时）或在你打开系统
 * 「无障碍」设置页时，自动禁用第三方无障碍服务；应用内无法阻止（vivo 官方 FAQ 有
 * 「无障碍被自动禁用」一条）。结果就是 Reticle 会时不时「眼瞎手瘫」。
 *
 * 它干什么：每 30 秒看一眼「本应用的无障碍还在不在系统的在册列表里」；
 * 不在 → 把它加回去（只动本应用这一条，其它辅助服务原样保留）。
 * 干不干由用户开关决定（默认开）；关掉＝本类不写任何系统设置。
 *
 * 独立于无障碍服务本身：自己起线程跑（VeriService 与主界面启动时都幂等拉起），
 * 这样即使无障碍被系统关掉，只要进程还在，看门狗就能去救它。
 *
 * 写回的两条路（按顺序试）：
 *   ① 应用内 Settings.Secure.putString——需要 WRITE_SECURE_SETTINGS（装机脚本里用 shell 授权）；
 *   ② 经 Shizuku 的 shell 跑 `content insert`——vivo 实测：settings 命令与 content update 全被系统
 *      拦下（写不进），但 content insert 能真正落地（本单 2026-10-07 实测）。
 */
public class A11yWatchdog {

    /** 系统在册列表里的本应用条目（与 MainActivity.a11yState 判据一致）。 */
    static final String SVC_FULL = "app.reticle/app.reticle.VeriService";
    static final String SVC_SHORT = "app.reticle/.VeriService";

    private static final String PREFS = "settings";
    private static final String KEY_ON = "a11y_watchdog";
    private static final String KEY_FIXES = "a11y_watchdog_fixes";
    private static final String KEY_LASTFIX = "a11y_watchdog_last_fix";
    /** 检测周期：10 秒。派单建议 30~60 秒，但实测掉线后进程很快会停摆，只能抢时间；
     *  读一次 Secure 设置只是本地 IPC，耗电可忽略（真正的重活是救回时的 shell，只在掉线时才跑）。 */
    private static final long INTERVAL_MS = 10000L;

    /** 52 号单：检测周期与「暂停重试」由内存自保（MemGuard）调——一级「缓」把 10 秒放慢到 60 秒，
     *  三级「让」暂停重试只留最小保活；恢复后自己调回来。 */
    static volatile long intervalMs = INTERVAL_MS;
    static volatile boolean paused = false;

    public static void setInterval(long ms) { intervalMs = ms > 0 ? ms : INTERVAL_MS; }
    public static void setPaused(boolean p) { paused = p; }
    public static long interval() { return intervalMs; }
    public static boolean isPaused() { return paused; }

    /** 现场（界面与自检读）。 */
    static volatile boolean inList = false;
    static volatile long lastCheckAt = 0L;
    static volatile long lastFixAt = 0L;
    static volatile int fixes = 0;
    static volatile String lastNote = "还没查过";
    static volatile String lastDetail = "";

    private static volatile boolean started = false;
    private static volatile Thread thread = null;

    // ───────── 用户开关（默认开：不然部分国产系统用户等于用不了） ─────────
    public static boolean isOn(Context ctx) {
        try { return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, true); }
        catch (Throwable t) { return false; }
    }

    public static void setOn(Context ctx, boolean v) {
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, v).apply();
            log(ctx, "开关 " + (v ? "打开" : "关闭"));
        } catch (Throwable t) { }
    }

    private static int savedInt(Context ctx, String k) {
        try { return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(k, 0); } catch (Throwable t) { return 0; }
    }

    private static long savedLong(Context ctx, String k) {
        try { return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(k, 0L); } catch (Throwable t) { return 0L; }
    }

    // ───────── 起线程（幂等） ─────────
    public static synchronized void start(Context ctx) {
        if (started && thread != null && thread.isAlive()) return;
        started = true;
        final Context c = ctx.getApplicationContext();
        Thread t = new Thread(new Runnable() {
            public void run() {
                try { Thread.sleep(10000L); } catch (Throwable x) { }
                while (true) {
                    if (!paused) { try { tick(c, true); } catch (Throwable x) { } }
                    try { Thread.sleep(paused ? 5000L : intervalMs); } catch (Throwable x) { return; }
                }
            }
        });
        t.setDaemon(true);
        thread = t;
        t.start();
        inList = hasSvc(readList(c));
        log(c, "看门狗上线（开关=" + (isOn(c) ? "开" : "关") + "，周期 " + (intervalMs / 1000) + " 秒；当前" + (inList ? "在册" : "不在册") + "）");
    }

    /**
     * 走一轮：读在册 → 缺了就写回 → 读回验证。
     * @param respectSwitch true＝听用户开关（自动轮询用）；false＝用户手动点了「立即检查」，强制执行一次。
     * @return 一句人话结果（给界面 toast／状态行用）。
     */
    /** 掉线瞬间从无障碍服务的回调里叫一声：立刻（并稍后再）查一轮，别等周期。 */
    public static void nudge(final Context ctx) {
        try {
            final Context c = ctx.getApplicationContext();
            Thread t = new Thread(new Runnable() {
                public void run() {
                    sleep(800L);
                    if (!paused) { try { tick(c, true); } catch (Throwable x) { } }
                    sleep(3000L);
                    if (!paused) { try { tick(c, true); } catch (Throwable x) { } }
                }
            });
            t.setDaemon(true);
            t.start();
        } catch (Throwable t) { }
    }

    public static synchronized String tick(Context ctx, boolean respectSwitch) {
        lastCheckAt = System.currentTimeMillis();
        if (respectSwitch && !isOn(ctx)) { lastNote = "已关闭（不检查）"; return "已关闭（不检查）"; }
        String cur = readList(ctx);
        int st = A11yLink.observe(ctx);   // 53 号单：连接态判据（含「未连接持续多久」的记账）
        // ★86：掉线就发那条会响的告警（CH_TEST）；接回来自动撤掉。主条永远静默。
        try {
            // ★127（2026-10-22）：在册但未连接要熬过 GRACE_MS 才算真掉线。以前一判定就报警，
            //   而 nudge 在无障碍刚连上 800ms 后就 tick、那时必然是 ST_IN_LIST_NO_CONN，于是每重连一次响一声。
            boolean lost = (cur == null) || !hasSvc(cur)
                    || (st == A11yLink.ST_IN_LIST_NO_CONN && A11yLink.noConnFor() >= A11yLink.GRACE_MS);
            Notifier.guardState(ctx, !lost);
        } catch (Throwable t) { }
        if (ERR.equals(cur)) { lastNote = "读不到系统设置"; return "读不到系统设置"; }
        if (cur == null) log(ctx, "名单键是空的（系统删掉了：强停/装包后常见）→ 按空名单处理，直接写回主包");   // 86.5
        if (hasSvc(cur)) {
            inList = true;
            // 53 号单：★在册 ≠ 能用 —— 再看一眼「系统到底有没有把我们接上」
            if (st == A11yLink.ST_IN_LIST_NO_CONN) {
                long forMs = A11yLink.noConnFor();
                if (forMs >= A11yLink.GRACE_MS && A11yLink.autoRepairAllowed()) {
                    log(ctx, "在册但未连接（已持续 " + (forMs / 1000) + " 秒）→ 自动修一次（删名→写回）");
                    String rr = A11yLink.repair(ctx, false);
                    if (A11yLink.isConnected()) { lastNote = "已自动接回（在册未连接 → 删名写回）"; lastDetail = rr; return "已自动接回：" + rr; }
                    lastNote = "在册但没接上（自动修没成）";
                    lastDetail = rr;
                    return "在册但未连接 ⚠：" + rr;
                }
                lastNote = "在册但未连接 ⚠";
                lastDetail = "名单里有我们、系统没接上（上次回调 " + A11yLink.lastCb + "）";
                return "在册但未连接 ⚠";
            }
            if (st == A11yLink.ST_UNKNOWN) { lastNote = "读不到系统设置"; return "读不到系统设置"; }
            // 53 号单（辅判据）：说"连着"时，隔一会儿探一次根节点；连续探不到只记备注，不单独当"断开"
            try { A11yLink.probeNow(); } catch (Throwable t) { }
            if (A11yLink.probeFails >= 3) lastDetail = "连接态在、但连续 " + A11yLink.probeFails + " 次探不到画面（屏幕可能锁着）";
            lastNote = "在册 ✓";
            return "在册 ✓";
        }

        inList = false;
        String nv = join(cur);
        log(ctx, "发现掉线：现[" + cur + "] → 目标[" + nv + "]");
        // ① 应用内直写
        String e1 = writeLocal(ctx, nv);
        sleep(1200L);
        if (hasSvc(readList(ctx))) { done(ctx, "应用内写入"); return "已自动重开（应用内写入）"; }
        // ② 经 Shizuku 的 shell（本机实测主路径）
        String e2 = writeShell(nv);
        sleep(1500L);
        if (hasSvc(readList(ctx))) { done(ctx, "shell 写入"); return "已自动重开（shell 写入）"; }

        lastNote = "重开失败";
        lastDetail = "应用内：" + e1 + " ｜ shell：" + clip(e2);
        log(ctx, "重开失败｜" + lastDetail);
        return "重开失败：系统拦下了写设置（需要手动开一次）";
    }

    private static void done(Context ctx, String how) {
        lastFixAt = System.currentTimeMillis();
        fixes++;
        inList = true;
        lastNote = "已自动重开（" + how + "）";
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putInt(KEY_FIXES, fixes).putLong(KEY_LASTFIX, lastFixAt).apply();
        } catch (Throwable t) { }
        log(ctx, "已重开（" + how + "），累计 " + fixes + " 次");
    }

    /** 52 号单 · 用户主动停（从最近任务划掉）时收工：让轮询线程退出，不再自己回来。 */
    public static synchronized void stopLoop(Context ctx) {
        started = false;
        Thread t = thread;
        thread = null;
        if (t != null) { try { t.interrupt(); } catch (Throwable x) { } }
        log(ctx, "看门狗下线（用户主动停）");
    }

    // ───────── 读写 ─────────
    /**
     * 读系统在册名单。
     * ★86.5 号单：要区分两种"没有名单"——
     *   ① 键存在但为空/不存在 ⇒ 系统给 null（**这不算读不到**，是"还没登记"，应该直接写回主包）；
     *   ② 读的时候抛异常（缺 WRITE_SECURE_SETTINGS、系统拦了）⇒ 返回哨兵 ERR，调用方按"读不到"处理。
     *   以前两者都返回 null，于是被强停一次（系统会把该无障碍服务从名单里删掉，键整个没了）之后，
     *   看门狗与「修一次」都只说"读不到系统设置，修不了"，自己永远回不来。
     */
    static final String ERR = "\u0000ERR";
    static String readList(Context ctx) {
        try { return Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES); }
        catch (Throwable t) { return ERR; }
    }

    static boolean hasSvc(String v) {
        return v != null && (v.contains(SVC_FULL) || v.contains(SVC_SHORT));
    }

    static String join(String cur) {
        return unionMainFirst(cur);
    }

    /**
     * 86.5 号单 · 名单规整（幂等：规整两次结果相同）。
     *
     * 老账：名单里曾出现 `app.reticle.b/.VeriService`（副包项）在首、主包不在，装完要等看门狗
     * 改回主包才连得上。规矩改成——
     *   ① 主包统一写成一个全名 `app.reticle/app.reticle.VeriService`，并且**排第一位**（主包优先）；
     *   ② 副包项（`app.reticle.b/…`）**不抢**：保留、但挪到末尾（用户自己装的副包不替他删）；
     *   ③ 别的应用的无障碍项原样保留、顺序不变（不丢用户的其它无障碍）；
     *   ④ 历史残留 `reticle-selftest` 剔掉（不是真服务名，系统读到只会当无效项）。
     * ★改这里要一起想到：A11yLink.stripSelf（删名）与 MainActivity.a11yState（判在册）都以主包名为准。
     */
    static String unionMainFirst(String cur) {
        StringBuilder oth = new StringBuilder();
        StringBuilder sub = new StringBuilder();
        String[] parts = (cur == null ? "" : cur).split(":");
        for (int i = 0; i < parts.length; i++) {
            String s = parts[i] == null ? "" : parts[i].trim();
            if (s.length() == 0 || s.equalsIgnoreCase("null")) continue;
            if (s.startsWith("app.reticle/") || s.equals("reticle-selftest")) continue;   // 本应用主包项：统一成一个全名放最前
            if (s.startsWith("app.reticle.b/")) {                                          // 副包项：保留、靠后
                if (sub.length() > 0) sub.append(':');
                sub.append(s);
                continue;
            }
            if (oth.length() > 0) oth.append(':');
            oth.append(s);
        }
        StringBuilder r = new StringBuilder(SVC_FULL);
        if (oth.length() > 0) r.append(':').append(oth);
        if (sub.length() > 0) r.append(':').append(sub);
        return r.toString();
    }

    /** ① 应用内直写（要 WRITE_SECURE_SETTINGS）；返回一句结果。 */
    private static String writeLocal(Context ctx, String nv) {
        try {
            Settings.Secure.putString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, nv);
            try { Settings.Secure.putInt(ctx.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1); } catch (Throwable t) { }
            return Words.RP_SHX_LOCAL_OK;
        } catch (Throwable t) { return String.valueOf(t); }
    }

    /** ② shell 路径：content insert。vivo 实测 settings 命令与 content update 都被拦，insert 能落地，
     *  但**值里不能含冒号**（toybox 的 --bind 按冒号切分；多服务列表必然含冒号）→ 只对"列表为空"时可用。 */
    private static String writeShell(String nv) {
        try {
            if (nv.indexOf(':') >= 0) return Words.RP_SHX_SKIP_COLON;
            if (!rikka.shizuku.Shizuku.pingBinder()) return Words.RP_SHX_ABSENT;
            String cmd = "content insert --uri content://settings/secure"
                    + " --bind name:s:enabled_accessibility_services --bind value:s:\"" + nv + "\""
                    + "; content insert --uri content://settings/secure"
                    + " --bind name:s:accessibility_enabled --bind value:s:1";
            String r = VeriService.shxRun(cmd);
            return r == null ? "" : r;
        } catch (Throwable t) { return String.valueOf(t); }
    }

    /** 38 号单 · 自测正门（只由 in/ 槽位 `cmd=a11ytest` 触发，不占 MCP）：
     *  故意把 enabled_accessibility_services 写坏 → 立刻跑一轮 → 看能否自愈；无论成败都复位。 */
    public static String selfTest(Context ctx) {
        try {
            String before = readList(ctx);
            String w = writeRaw("reticle-selftest");
            sleep(1200);
            String wrote = readList(ctx);
            String t = tick(ctx, false);
            sleep(800);
            String after = readList(ctx);
            boolean ok = hasSvc(after);
            if (!ok) {
                try { Settings.Secure.putString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, join(after)); } catch (Throwable x) { }
                sleep(800);
                after = readList(ctx);
                ok = hasSvc(after);
            }
            // 39 号单：无条件复位（原来只在「救回失败」时复位，成功时会残留 reticle-selftest 前缀）。
            String reset = resetList(ctx, after);
            ok = hasSvc(reset);
            return "{\"ok\":" + (ok ? 1 : 0) + ",\"before\":\"" + sq(before) + "\",\"wrote\":\"" + sq(wrote)
                    + "\",\"tick\":\"" + sq(t) + "\",\"after\":\"" + sq(after) + "\",\"reset\":\"" + sq(reset) + "\"}";
        } catch (Throwable t) {
            try { Settings.Secure.putString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, resetList(ctx, readList(ctx))); } catch (Throwable x) { }
            return "{\"ok\":0,\"err\":\"" + sq(String.valueOf(t)) + "\"}";
        }
    }

    /** 38 号单：往 enabled_accessibility_services 写一个指定值（自测用；值不得含冒号）。 */
    private static String writeRaw(String v) {
        try {
            String cmd = "content insert --uri content://settings/secure"
                    + " --bind name:s:enabled_accessibility_services --bind value:s:\"" + v + "\"";
            String r = VeriService.shxRun(cmd);
            return r == null ? "" : r;
        } catch (Throwable t) { return String.valueOf(t); }
    }

    /** 39 号单 · 把在册列表复位成「干净值」：剔掉自测写进去的伪条目（reticle-selftest）与不含 '/' 的杂项，
     *  只留合法组件条目；空则退回本应用那条。写完读回，返回读回值（读不回就返回打算写的值）。 */
    static String resetList(Context ctx, String cur) {
        String clean = cleanList(cur);
        try {
            Settings.Secure.putString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, clean);
            Settings.Secure.putInt(ctx.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            sleep(600);
            String back = readList(ctx);
            if (back != null && back.trim().length() > 0 && !back.trim().equalsIgnoreCase("null")) return back;
        } catch (Throwable t) { }
        return clean;
    }

    /** 39 号单：洗干净在册列表（纯函数，便于单测）。 */
    static String cleanList(String v) {
        if (v == null) return SVC_FULL;
        String t = v.trim();
        if (t.length() == 0 || t.equalsIgnoreCase("null")) return SVC_FULL;
        StringBuilder sb = new StringBuilder();
        String[] parts = t.split(":");
        for (int i = 0; i < parts.length; i++) {
            String s = parts[i] == null ? "" : parts[i].trim();
            if (s.length() == 0) continue;
            if (s.equals("reticle-selftest")) continue;
            if (s.indexOf('/') < 0) continue;
            if (sb.length() > 0) sb.append(':');
            sb.append(s);
        }
        return sb.length() == 0 ? SVC_FULL : sb.toString();
    }

    private static String sq(String s) {
        if (s == null) return "";
        return s.replace("\\", "").replace("\"", "'").replace("\n", " ");
    }

    // ───────── 给界面看的一句话 ─────────
    public static String statusLine(Context ctx) {
        if (!isOn(ctx)) return Words.WD_LINE_OFF;
        StringBuilder sb = new StringBuilder();
        int st = A11yLink.ST_UNKNOWN;
        try { st = A11yLink.state(ctx); } catch (Throwable t) { }
        sb.append(Words.WD_LINE_PREFIX)
          .append(st == A11yLink.ST_CONNECTED ? Words.WD_A11Y_ON
                  : st == A11yLink.ST_IN_LIST_NO_CONN ? Words.WD_A11Y_DISC
                  : st == A11yLink.ST_NOT_IN_LIST ? Words.WD_A11Y_OFF : Words.MA_A11Y_ERR);
        if (st == A11yLink.ST_IN_LIST_NO_CONN) sb.append(Words.WD_DISC_HINT);
        int n = fixes > 0 ? fixes : savedInt(ctx, KEY_FIXES);
        long lf = Math.max(lastFixAt, savedLong(ctx, KEY_LASTFIX));
        if (n > 0) sb.append(Words.WD_FIXES_A).append(n).append(Words.WD_FIXES_B).append(lf > 0 ? Words.WD_FIXES_C + hm(lf) + Words.WD_FIXES_D : "");
        if (lastCheckAt > 0) sb.append(Words.WD_CHECKED + hm(lastCheckAt));
        if ("重开失败".equals(lastNote)) sb.append(Words.WD_FAIL);
        return sb.toString();
    }

    // ───────── 杂项 ─────────
    private static String hm(long ms) {
        try { return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(ms)); } catch (Throwable t) { return ""; }
    }

    private static String clip(String s) {
        if (s == null) return "";
        s = s.replace("\n", " ");
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }

    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (Throwable t) { } }

    static void log(Context ctx, String s) {
        try {
            File d = Home.sub(ctx, "logs");
            if (d == null) return;
            File f = new File(d, "a11y-watchdog.log");
            if (f.length() > 200000) f.delete();
            FileOutputStream o = new FileOutputStream(f, true);
            o.write((System.currentTimeMillis() + " " + s + "\n").getBytes("UTF-8"));
            o.close();
        } catch (Throwable t) { }
    }
    /** 86.6：用户划掉卡片＝把无障碍交还系统。
     *  先 disableSelf（能连上时最干净：系统自己把我们这条从名单里摘掉并解绑）；
     *  连不上／还没接上时 disableSelf 叫不动，就直接把名单改成"不含本应用"。
     *  ★只摘自己，副包与别人的无障碍原样保留；写完不睡、不读回（onTaskRemoved 在主线程，要快）。 */
    static void handBackList(Context ctx) {
        try {
            String cur = readList(ctx);
            if (ERR.equals(cur)) { log(ctx, "划掉＝交还：读不到系统设置（读时抛异常）⇒ 只能等打开 App 时再恢复"); return; }
            if (!hasSvc(cur)) { log(ctx, "划掉＝交还：名单里本来就没有本应用（OK）"); return; }
            String off = A11yLink.stripSelf(cur);
            try {
                if (off.length() == 0) {
                    Settings.Secure.putString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "");
                } else {
                    Settings.Secure.putString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, off);
                }
                log(ctx, "划掉＝交还：已把本应用从无障碍名单摘掉 →[" + off + "]");
            } catch (Throwable t) {
                log(ctx, "划掉＝交还：摘名单失败（系统拦下了）：" + t);
            }
        } catch (Throwable t) {
            log(ctx, "划掉＝交还：异常 " + t);
        }
    }
}
