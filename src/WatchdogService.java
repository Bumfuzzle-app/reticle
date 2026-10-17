package app.reticle;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * 27 号单 · 看门狗前台服务
 *
 * 为什么需要它（2026-10-07 实测教训）：
 * 无障碍服务一被系统摘掉，Reticle 这个进程就从「服务进程」掉成「空进程」，优先级一落，
 * 系统在一分钟内就把它收走——看门狗（同一个进程里的线程）随之死掉，于是「看着自己被关，
 * 却来不及去开」。挂上前台服务后，进程优先级接近前台应用，看门狗能撑到下一轮检查，
 * 把无障碍开回来。
 *
 * 只做一件事：让看门狗活着（前台通知 + A11yWatchdog.start）；不碰别的设置。
 *
 * 52 号单加了四件事（都在下面标了 52）：
 *   ① 「用户主动划掉 ＝ 停」：onTaskRemoved（只有从最近任务划掉才回调）→ 置「用户已停」
 *      ＋主动停手＋stopSelf，**不再自动回来**（开机自启也不覆盖它），直到用户手动打开 App；
 *   ② 「被系统回收 ＝ 自愈」：这条路不走 onTaskRemoved，照旧靠 START_STICKY 回来；
 *   ③ onTrimMemory 转发给 MemGuard（内存自保的主接入点：系统主动喊话，最准最省电）；
 *   ④ 前台通知正文带一句内存档位（让"它在让路"这件事看得见）。
 */
public class WatchdogService extends Service {

    /** 前台通知编号（与「眼线」那条 1 号通知分开，免得互相盖）。 */
    public static final int NID = 2;

    /** 37 号单：上线那条日志只在状态变化时写一次（同一实例只写一条），免得刷屏。 */
    private static volatile boolean upLogged = false;

    /** 52 号单：档位变了要刷前台通知，所以留住实例。 */
    static volatile WatchdogService INSTANCE = null;

    // ───────── 52 号单 · 「用户已停」标志（存 SP：进程死了也记得） ─────────
    private static final String PREFS = "settings";
    private static final String KEY_USER_STOPPED = "user_stopped";

    /** 用户是否已经明确停过（从最近任务划掉）。 */
    public static boolean isUserStopped(Context c) {
        try { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_USER_STOPPED, false); }
        catch (Throwable t) { return false; }
    }

    /** 置／清「用户已停」：置上＝不再自动回来；清掉＝手动打开 App 之后恢复。 */
    public static void setUserStopped(Context c, boolean v) {
        try { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_USER_STOPPED, v).apply(); }
        catch (Throwable t) { }
    }

    /** 幂等拉起：Android 8+ 走 startForegroundService；失败（如后台限制）退化为普通服务。
     *  52 号单：「用户已停」期间一律不拉（开机自启、装包回调都走这里，一并挡住）。 */
    public static void ensure(Context c) {
        try { if (isUserStopped(c)) return; } catch (Throwable t) { }
        try {
            Intent i = new Intent(c, WatchdogService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Throwable t) {
            try { c.startService(new Intent(c, WatchdogService.class)); } catch (Throwable t2) { }
        }
    }

    /** 28 号单 · 一键停机用：把前台守护服务停掉 */
    public static void stop(Context c) {
        try { c.stopService(new Intent(c, WatchdogService.class)); } catch (Throwable t) { }
    }

    @Override public void onCreate() {
        super.onCreate();
        INSTANCE = this;
        try { Notifier.purgeLegacyMedia(this); } catch (Throwable t) { }   // 72 号单：启动清掉旧媒体那条（id=9）＋旧渠道 yitong_media
        up();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        up();
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() { upLogged = false; INSTANCE = null; super.onDestroy(); }

    /**
     * 52 号单 ①：「用户主动划掉 ＝ 停」。只有"从最近任务里划掉本应用"才会回调这里，
     * 被系统内存回收不走这条路（那条仍靠 START_STICKY 自愈）。
     */
    @Override public void onTaskRemoved(Intent rootIntent) {
        try {
            setUserStopped(this, true);
            A11yWatchdog.log(this, "用户在最近任务里划掉了 Reticle ⇒ 全部停手"
                    + "（看门狗／内存自保／本地服务都停；开机自启也不覆盖，手动打开 App 才恢复）");
            try { A11yWatchdog.stopLoop(this); } catch (Throwable t) { }
            try { MemGuard.stopLoop(this); } catch (Throwable t) { }
            try { McpServer.stop(this); } catch (Throwable t) { }
            try { stopForeground(true); } catch (Throwable t) { }
        } catch (Throwable t) { }
        try { stopSelf(); } catch (Throwable t) { }
        super.onTaskRemoved(rootIntent);
    }

    /** 52 号单 ③：内存自保的主接入点——系统主动喊话，转给 MemGuard。 */
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        try { MemGuard.onTrim(this, level); } catch (Throwable t) { }
    }

    private void up() {
        // 52 号单：说过"停"就不再起来（sticky 被系统拉起、或别处调 ensure 时都走这儿挡一道）。
        if (isUserStopped(this)) {
            try { A11yWatchdog.log(this, "服务被拉起，但「用户已停」⇒ 立刻退下（不自动回来）"); } catch (Throwable t) { }
            try { stopSelf(); } catch (Throwable t) { }
            return;
        }
        try { channel(); } catch (Throwable t) { }
        try {
            startForeground(NID, buildNote());
            if (!upLogged) { upLogged = true; A11yWatchdog.log(this, "前台服务已上线（通知 " + NID + "）"); }
        } catch (Throwable t) {
            A11yWatchdog.log(this, "前台服务启动失败：" + t);
        }
        try { A11yWatchdog.start(this); } catch (Throwable t) { }
        try { MemGuard.start(this); } catch (Throwable t) { }
        try { McpServer.start(this); } catch (Throwable t) { }
    }

    /** 52 号单 ④：通知正文＝常态那句 ＋（让路档时）一句人话。 */
    private Notification buildNote() {
        Notification.Builder b = (Build.VERSION.SDK_INT >= 26)
                ? new Notification.Builder(this, Notifier.CH_STAT) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat_notify);
        b.setContentTitle(Words.NT_DEF_TITLE);
        String txt = Words.NT_WD_TEXT;
        try { txt = txt + MemGuard.noteSuffix(); } catch (Throwable t) { }
        b.setContentText(txt);
        b.setOngoing(true);
        b.setShowWhen(false);
        b.setOnlyAlertOnce(true);
        try {
            Intent it = new Intent(this, MainActivity.class);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            int fl = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) fl |= PendingIntent.FLAG_IMMUTABLE;
            b.setContentIntent(PendingIntent.getActivity(this, 0, it, fl));
        } catch (Throwable t) { }
        return b.build();
    }

    /** 52 号单：内存档位变了 → 只刷前台那条通知的正文（不新建通知、不响不震）。 */
    static void refreshNote(Context ctx) {
        try {
            WatchdogService s = INSTANCE;
            if (s == null) return;
            NotificationManager m = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (m == null) return;
            m.notify(NID, s.buildNote());
        } catch (Throwable t) { }
    }

    private void channel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager m = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (m == null) return;
        NotificationChannel a = new NotificationChannel(Notifier.CH_STAT, Words.NT_CH_STAT, NotificationManager.IMPORTANCE_LOW);
        a.setDescription(Words.NT_CH_STAT_D);
        m.createNotificationChannel(a);
    }
}
