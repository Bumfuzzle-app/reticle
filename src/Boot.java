package app.reticle;

import android.content.Context;
import android.os.SystemClock;

/**
 * 86.5 号单 · 「装完到能用」打点（t0~t5）。
 *
 * 为什么要有它：装完/开机后要多久才能干活，以前只有 awatchdog 日志里散着两句，靠感觉估出"20 秒"。
 * 本类只做一件事——每个关键站台打一行带毫秒差的日志，全部相对 t0。
 *
 * t0＝收到装包广播（MY_PACKAGE_REPLACED）或开机广播（BOOT_COMPLETED）；这两条都没收到
 *     （用户手动点开 App 的冷启动）时，t0 退化为「本进程启动时刻」（Process.getStartElapsedTime）。
 * t1＝WatchdogService.onCreate 进入
 * t2＝startForeground 返回（失败记异常原文）
 * t3＝McpServer 监听 8770（端口）成功
 * t4＝无障碍 VeriService.onServiceConnected
 * t5＝第一个工具请求成功（只记一次）
 *
 * ★只写日志，不改任何状态；每个点都 try 住，日志失败不许影响主流程（开机路径尤其不能因此崩）。
 */
public class Boot {

    /** 本进程启动时刻（elapsedRealtime 基准）；取不到就退化为本类初始化那一刻。 */
    static final long PROC = procStart();

    private static volatile long rxAt = 0L;
    private static volatile String rxWhat = "";
    private static volatile long t5At = 0L;

    private static long now() { return SystemClock.elapsedRealtime(); }

    /**
     * 本进程启动时刻（elapsedRealtime 基准）。
     * ★Process.getStartElapsedTime() 是隐藏 API（android.jar 里没有，ECJ 直接报未定义）⇒ 改读 /proc：
     *   进程存活时长 = /proc/uptime 的第一列（秒）− /proc/self/stat 第 22 列（开机后 jiffies，USER_HZ=100）。
     *   两者都是"不含深睡"的时钟，差出来正好是进程年龄；再用 elapsedRealtime 倒推即可。
     *   取不到就退化为本类初始化那一刻（误差只在几十毫秒级，够量 5 秒的门槛）。
     */
    private static long procStart() {
        long n = now();
        try {
            String stat;
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader("/proc/self/stat"));
            try { stat = r.readLine(); } finally { r.close(); }
            int i = stat.lastIndexOf(')');                     // comm 里可能带空格/括号，从最后一个 ) 之后切
            String[] f = stat.substring(i + 2).split(" ");
            long startTicks = Long.parseLong(f[19].trim());     // 第 22 字段（state 起算第 20 个）
            String up;
            java.io.BufferedReader u = new java.io.BufferedReader(new java.io.FileReader("/proc/uptime"));
            try { up = u.readLine(); } finally { u.close(); }
            double upSec = Double.parseDouble(up.split(" ")[0].trim());
            long ageMs = (long) (upSec * 1000.0) - startTicks * 10L;
            if (ageMs < 0L || ageMs > 86400000L) return n;
            return n - ageMs;
        } catch (Throwable t) { return n; }
    }

    /** t0：收到装包/开机广播（只认第一次；同一次启动里补发的广播不重置基准）。 */
    public static void rx(Context c, String what) {
        try {
            if (rxAt == 0L) { rxAt = now(); rxWhat = what == null ? "广播" : what; }
            mark(c, "t0", "收到 " + (what == null ? "广播" : what));
        } catch (Throwable t) { }
    }

    /** 任意一站：tag＝t1..t5，what＝人话。 */
    public static void mark(Context c, String tag, String what) {
        try {
            long n = now();
            long base = rxAt != 0L ? rxAt : PROC;
            String zero = rxAt != 0L ? ("收到 " + rxWhat) : "进程启动";
            String s = "装完打点｜" + tag + " " + what + " ｜ +" + (n - base) + "ms（进程起后 +" + (n - PROC) + "ms；t0＝" + zero + "）";
            A11yWatchdog.log(c, s);
        } catch (Throwable t) { }
    }

    /** t5：第一个工具请求成功——只记第一次，之后不刷屏。 */
    public static void firstToolOk(Context c, String tool) {
        try {
            if (t5At != 0L) return;
            t5At = now();
            mark(c, "t5", "第一个工具请求成功（" + tool + "）");
        } catch (Throwable t) { }
    }

    /** 给自检/交工取证用：当前 t0 基准是不是"收到广播"（false＝进程启动兜底）。 */
    public static boolean hasRx() { return rxAt != 0L; }
    /** 86.6：本次开机起来多久（秒；含深睡，读 /proc/uptime）。读不到回 -1。 */
    public static long upSec() {
        try {
            java.io.BufferedReader u = new java.io.BufferedReader(new java.io.FileReader("/proc/uptime"));
            try { return (long) Double.parseDouble(u.readLine().split(" ")[0].trim()); } finally { u.close(); }
        } catch (Throwable t) { return -1L; }
    }

    /** 86.6：这条 BOOT_COMPLETED 是不是「真开机」。
     *  vivo 在用户「划掉卡片／一键清理」后会把 BOOT_COMPLETED 重放给应用（本单实测：11 秒里 30 条）：
     *  按它去做开机恢复＝一边被系统收走、一边又把无障碍挂回去，进程被反复踢，点桌面图标就点不动。
     *  开机不到 10 分钟的才算真开机。 */
    /** ★87（86.6 的账）：不再用"开机 <10 分钟"这个时间窗——vivo 划掉卡片后重放 BOOT_COMPLETED 时，
     *  只要还落在这个窗口里就会被当成真开机。改用开机次数（Settings.Global.BOOT_COUNT）：
     *  这次的开机序号没见过＝真开机；见过＝同一次开机的重放。读不到（老系统）才退回时间窗。 */
    public static boolean realBoot(Context c) {
        int cnt = bootCount(c);
        if (cnt < 0) {
            long up = upSec();
            long alive = up >= 0 ? up * 1000L : SystemClock.elapsedRealtime();
            return alive < 10L * 60L * 1000L;
        }
        try {
            android.content.SharedPreferences sp = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
            int last = sp.getInt("boot_count_seen", -1);
            if (last == cnt) return false;
            sp.edit().putInt("boot_count_seen", cnt).apply();
            return true;
        } catch (Throwable t) { return true; }
    }

    /** 开机次数（每次真开机 +1）。读不到回 -1。 */
    public static int bootCount(Context c) {
        try {
            return android.provider.Settings.Global.getInt(c.getContentResolver(), android.provider.Settings.Global.BOOT_COUNT);
        } catch (Throwable t) { return -1; }
    }
    /** 86.6：同一次开机只做一次开机恢复（广播重放／重复投递都挡掉）。 */
    public static boolean bootHandled(Context c) {
        try {
            long up = upSec();
            if (up < 0) return false;
            android.content.SharedPreferences sp = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
            long last = sp.getLong("boot_handled_up", -1L);
            if (last >= 0 && Math.abs(up - last) < 300L) return true;
            sp.edit().putLong("boot_handled_up", up).apply();
            return false;
        } catch (Throwable t) { return false; }
    }
}
