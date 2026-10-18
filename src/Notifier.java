package app.reticle;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import java.io.File;
import java.io.FileOutputStream;

/**
 * 17 号 · 通知与原子岛（眼手包的"嘴"）。
 * 普通通知：创建/更新/清除（助手可见的状态出口，替代旧 phone_notify 能力）。
 * 原子岛：vivo 原生通知 API 扩展参数 notification.superx.*（本地创建/更新/结束）。
 * 形态 A＝官方推送指南示例；形态 B/C＝对照系统内既有超级通知（钱包/Jovi）的字段结构试验。
 */
public class Notifier {
    public static final String CH_STAT = "yitong_stat";
    public static final String CH_TEST = "yitong_test";
    public static final String CH_LIVE = "yitong_live";
    public static final int DEF_ID = 9;   // 86：notify 默认 id ⇒ 并进大通知（EYE_ID）

    /** ★87（86 遗留）：主通知显式成组——别让系统把「主通知＋工具通知」自动折成「Reticle 2」。 */
    static final String GROUP_MAIN = "app.reticle.main";

    private static NotificationManager nm(Context c) {
        return (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager m = nm(c);
        if (m == null) return;
        NotificationChannel a = new NotificationChannel(CH_STAT, Words.NT_CH_STAT, NotificationManager.IMPORTANCE_LOW);
        a.setDescription(Words.NT_CH_STAT_D);
        m.createNotificationChannel(a);
        NotificationChannel b = new NotificationChannel(CH_TEST, Words.NT_CH_TEST, NotificationManager.IMPORTANCE_HIGH);
        b.setDescription(Words.NT_CH_TEST_D);
        m.createNotificationChannel(b);
        try { m.deleteNotificationChannel("yitong_media"); } catch (Throwable t) { }
        NotificationChannel cl = new NotificationChannel(CH_LIVE, Words.NT_CH_LIVE, NotificationManager.IMPORTANCE_LOW);
        cl.setDescription(Words.NT_CH_LIVE_D);
        cl.setShowBadge(false);
        m.createNotificationChannel(cl);
    }

    private static Notification.Builder base(Context c, String ch) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, ch) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_stat_notify);
        return b;
    }

    private static PendingIntent openApp(Context c) {
        Intent it = new Intent(c, MainActivity.class);
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) fl |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(c, 0, it, fl);
    }

    static void nlog(Context c, String s) {
        try {
            File d = Home.sub(c, "logs");
            if (d == null) return;
            File f = new File(d, "notifier.log");
            if (f.length() > 100000) f.delete();
            FileOutputStream o = new FileOutputStream(f, true);
            o.write((System.currentTimeMillis() + " " + s + "\n").getBytes("UTF-8"));
            o.close();
        } catch (Throwable t) { }
    }

    /** 普通通知：创建或更新（同 id）。返回 "ok" 或 "err:…"。 */
    public static String send(Context c, int id, String title, String content, boolean ongoing) {
        if (id == EYE_ID) return setNote(c, title, content);   // 86：默认 id／9 ⇒ 并进大通知，不单发第二条
        try {
            channels(c);
            Notification.Builder b = base(c, CH_STAT);
            b.setGroup("app.reticle.n" + id);   // ★87：显式成组，不留"自动摘要"
            b.setContentTitle(safe(title, Words.NT_DEF_TITLE));
            b.setContentText(safe(content, ""));
            b.setContentIntent(openApp(c));
            b.setOngoing(ongoing);
            b.setOnlyAlertOnce(true);
            b.setAutoCancel(!ongoing);
            nm(c).notify(id, b.build());
            nlog(c, "send ok id=" + id + " title=" + safe(title, Words.NT_DEF_TITLE));
            return "ok";
        } catch (Throwable t) { nlog(c, "send err " + t); return "err: " + t; }
    }

    /** 清除通知。 */
    public static String cancel(Context c, int id) {
        try {
        if (id == EYE_ID) { sReport = ""; sNote = ""; refresh(c, true); nlog(c, "cancel slot #" + id); return "ok"; }   // 86：clear 只清槽，不动整条
            nm(c).cancel(id);
            nlog(c, "cancel id=" + id);
            return "ok";
        } catch (Throwable t) { return "err: " + t; }
    }

    /**
     * 形态 A：官方推送指南《客户端本地创建》示例字段。
     * op：0=创建 1=更新 2=结束；scene 例：HEALTH_REGISTER / TAXI / TAKEOUT / NAVIGATION。
     */
    public static String island(Context c, int id, int op, String scene, String title, String content, boolean ongoing) {
        try {
            channels(c);
            Notification.Builder b = base(c, CH_TEST);
            b.setGroup("app.reticle.n" + id);   // ★87：显式成组，不留"自动摘要"
            b.setContentTitle(safe(title, Words.NT_DEF_TITLE));
            b.setContentText(safe(content, ""));
            b.setContentIntent(openApp(c));
            b.setOngoing(ongoing);
            b.setOnlyAlertOnce(true);
            Bundle x = new Bundle();
            x.putInt("notification.superx.operation", op);
            x.putBoolean("notification.superx.showNotify", true);
            x.putInt("notification.superx.template", 1);
            x.putString("notification.superx.scene", safe(scene, "HEALTH_REGISTER"));
            x.putParcelable("notification.superx.clickResp", openApp(c));
            Bundle bi = new Bundle();
            try {
                android.graphics.Bitmap ic = android.graphics.BitmapFactory.decodeResource(c.getResources(), R.mipmap.ic_launcher_foreground);
                if (ic != null) bi.putParcelable("notification.superx.baseInfos.icon", ic);
            } catch (Throwable t) { }
            bi.putCharSequence("notification.superx.baseInfos.title", safe(title, Words.NT_DEF_TITLE));
            bi.putCharSequence("notification.superx.baseInfos.content", safe(content, ""));
            x.putBundle("notification.superx.baseInfos", bi);
            b.setExtras(x);
            nm(c).notify(id, b.build());
            nlog(c, "islandA ok id=" + id + " op=" + op + " scene=" + safe(scene, "HEALTH_REGISTER"));
            return "ok";
        } catch (Throwable t) { nlog(c, "islandA err " + t); return "err: " + t; }
    }

    /** 形态 C 试验：形态 A 字段 + islandNotify + tag（对照 A 判断这两项是否关键）。 */
    public static String islandC(Context c, int id, int op, String scene, String title, String content, boolean ongoing) {
        try {
            channels(c);
            Notification.Builder b = base(c, CH_TEST);
            b.setGroup("app.reticle.n" + id);   // ★87：显式成组，不留"自动摘要"
            b.setContentTitle(safe(title, Words.NT_DEF_TITLE));
            b.setContentText(safe(content, ""));
            b.setContentIntent(openApp(c));
            b.setOngoing(ongoing);
            b.setOnlyAlertOnce(true);
            Bundle x = new Bundle();
            x.putInt("notification.superx.operation", op);
            x.putBoolean("notification.superx.showNotify", true);
            x.putInt("notification.superx.template", 1);
            x.putString("notification.superx.scene", safe(scene, "HEALTH_REGISTER"));
            x.putBoolean("notification.superx.islandNotify", true);
            x.putParcelable("notification.superx.clickResp", openApp(c));
            Bundle bi = new Bundle();
            try {
                android.graphics.Bitmap ic = android.graphics.BitmapFactory.decodeResource(c.getResources(), R.mipmap.ic_launcher_foreground);
                if (ic != null) bi.putParcelable("notification.superx.baseInfos.icon", ic);
            } catch (Throwable t) { }
            bi.putCharSequence("notification.superx.baseInfos.title", safe(title, Words.NT_DEF_TITLE));
            bi.putCharSequence("notification.superx.baseInfos.content", safe(content, ""));
            x.putBundle("notification.superx.baseInfos", bi);
            b.setExtras(x);
            nm(c).notify("VIVO_SUPERX_TAG", id, b.build());
            nlog(c, "islandC ok id=" + id + " op=" + op + " scene=" + safe(scene, "HEALTH_REGISTER"));
            return "ok";
        } catch (Throwable t) { nlog(c, "islandC err " + t); return "err: " + t; }
    }

    /** 形态 B 试验：对照系统内既有超级通知（钱包）的字段结构（island Bundle + eventInfo + tag）。 */
    public static String islandB(Context c, int id, int op, String scene, String title, String content, boolean ongoing) {
        try {
            channels(c);
            Notification.Builder b = base(c, CH_TEST);
            b.setGroup("app.reticle.n" + id);   // ★87：显式成组，不留"自动摘要"
            b.setContentTitle(safe(title, Words.NT_DEF_TITLE));
            b.setContentText(safe(content, ""));
            b.setContentIntent(openApp(c));
            b.setOngoing(ongoing);
            b.setOnlyAlertOnce(true);
            Bundle x = new Bundle();
            x.putInt("notification.superx.operation", op);
            x.putBoolean("notification.superx.showNotify", true);
            x.putBoolean("notification.superx.islandNotify", true);
            x.putString("notification.superx.eventInfo",
                    "{\"island_content\":\"" + safe(title, Words.NT_DEF_TITLE) + "\",\"app_trigger\":\"app.reticle\",\"island_status\":\"" + Words.NT_ISLAND_STATUS + "\",\"app_extra_info\":\"\"}");
            Bundle isl = new Bundle();
            isl.putInt("island.superx.template", 4);
            isl.putInt("island.superx.leftTemplate", 1);
            isl.putInt("island.superx.rightTemplate", 4);
            isl.putInt("island.superx.showTime", 180);
            isl.putBoolean("island.superx.forceShowCard", false);
            isl.putInt("island.superx.islandClick", 0);
            isl.putInt("island.superx.islandAfterSlideCard", 0);
            Bundle li = new Bundle();
            li.putCharSequence("island.superx.text", safe(title, Words.NT_DEF_TITLE));
            isl.putBundle("island.superx.leftInfo", li);
            Bundle ri = new Bundle();
            ri.putCharSequence("island.superx.text", safe(content, ""));
            isl.putBundle("island.superx.rightInfo", ri);
            Bundle bi = new Bundle();
            try {
                android.graphics.Bitmap ic = android.graphics.BitmapFactory.decodeResource(c.getResources(), R.mipmap.ic_launcher_foreground);
                if (ic != null) bi.putParcelable("island.superx.icon", ic);
            } catch (Throwable t) { }
            bi.putCharSequence("island.superx.title", safe(title, Words.NT_DEF_TITLE));
            bi.putCharSequence("island.superx.content", safe(content, ""));
            isl.putBundle("island.superx.baseInfos", bi);
            x.putBundle("notification.superx.island", isl);
            b.setExtras(x);
            nm(c).notify("VIVO_SUPERX_TAG", id, b.build());
            nlog(c, "islandB ok id=" + id + " op=" + op);
            return "ok";
        } catch (Throwable t) { nlog(c, "islandB err " + t); return "err: " + t; }
    }

    // ==== 18 号：原生实况型通知试验（ProgressStyle＋requestPromoted；反射实现，编译期不依赖 API36）====
    public static String promoted(Context c, int id, String title, String content, int progress) {
        try {
            channels(c);
            Notification.Builder b = base(c, CH_LIVE);
            b.setGroup("app.reticle.n" + id);   // ★87：显式成组，不留"自动摘要"
            b.setContentTitle(safe(title, Words.NT_DEF_TITLE));
            b.setContentText(safe(content, ""));
            b.setContentIntent(openApp(c));
            b.setOngoing(true);
            b.setOnlyAlertOnce(true);
            b.setShowWhen(false);
            b.setCategory(Notification.CATEGORY_PROGRESS);
            try { Notification.Builder.class.getMethod("setRequestPromotedOngoing", boolean.class).invoke(b, true); }
            catch (NoSuchMethodException ns) { nlog(c, "promoted: setRequestPromotedOngoing 不存在，降级 extra"); }
            catch (Throwable t) { nlog(c, "promoted: setRequestPromotedOngoing err " + t); }
            try { Notification.Builder.class.getMethod("setShortCriticalText", CharSequence.class).invoke(b, "Reticle"); }
            catch (Throwable t) { }
            try {
                Class<?> psC = Class.forName("android.app.Notification$ProgressStyle");
                Object ps = psC.getDeclaredConstructor().newInstance();
                psC.getMethod("setProgress", int.class).invoke(ps, progress);
                Class<?> stC = Class.forName("android.app.Notification$Style");
                Notification.Builder.class.getMethod("setStyle", stC).invoke(b, ps);
            } catch (Throwable t) { nlog(c, "promoted: ProgressStyle err " + t); }
            Notification n = b.build();
            try { if (n.extras != null && !n.extras.containsKey("android.requestPromotedOngoing")) n.extras.putBoolean("android.requestPromotedOngoing", true); } catch (Throwable t) { }
            nm(c).notify(id, n);
            nlog(c, "promoted ok id=" + id + " progress=" + progress);
            return "ok";
        } catch (Throwable t) { nlog(c, "promoted err " + t); return "err: " + t; }
    }


    // ==== 72 号单（2026-10-16）· 媒体路线整体砍除 ====
    // 原来这里是一套「MediaSession ＋ MediaStyle ＋ 2.5 秒 ticker」，目的是借「原子随身听」把助手状态送上岛；
    // 首条真实外部反馈（B 站用户）：它在后台一直占着音频播放器通道、岛上一直显示在放音乐，观感不好 ⇒ 用户拍「直接砍」。
    // 从 0.28.23 起：ticker／心跳／MediaSession／MediaStyle 全没了（`dumpsys media_session` 里连会话都不该剩）。
    // 助手状态的出口改成「眼线那条」＝**普通通知**（渠道 CH_STAT、id 9）＋ 小形象气泡（Mascot 那条，另有）。
    // ★本文件从此不许再出现 MediaSession／MediaStyle／USAGE_MEDIA；要「汇报上岛」走 island()／promoted()（另开单）。

    // ==== 86 号单（2026-10-18）· 三条并一条 ====
    // 之前通知栏里有三条：眼线那条（id 9）／notify 工具默认那条（id 1）／看门狗前台服务那条（id 2）。
    // 现在只有一条：id＝9、渠道 CH_STAT（LOW，静默），前台服务也绑它；折叠一行是最近结果，展开是列表。
    private static final android.os.Handler sMT = new android.os.Handler(android.os.Looper.getMainLooper());
    /** 「眼线那条」＝大通知：助手状态、工具记录、汇报、守护全在这一条。 */
    public static final int EYE_ID = 9;
    /** notify 不带 id／用默认 id ⇒ 并进大通知（不再单发第二条）。 */

    /** 52 号单 · 内存让路：暂停非必要的刷新（眼线那条），恢复后自己接着刷。 */
    private static volatile boolean sQuiet = false;
    public static void setQuiet(boolean q) { sQuiet = q; }

    private static volatile boolean sMtBusy = false;
    private static volatile long sMtLast = 0L;
    private static volatile String sMtLastTool = "";
    private static int sMtSeq = 0;

    /** 86：一行工具记录。st：-1 进行中／1 完成／0 没成／2 超时没成。 */
    private static final class Line {
        String tool = "";
        String zh = "";
        int st = -1;
        int times = 1;
        long at = 0L;
    }

    private static final int MAX_LINES = 5;
    private static final java.util.ArrayDeque<Line> LINES = new java.util.ArrayDeque<Line>();

    private static volatile String sReport = "";
    private static volatile long sReportAt = 0L;
    private static final long REPORT_KEEP_MS = 1800000L;

    /** 86：notify（默认 id）的正文槽。与汇报槽各占一位，互不顶掉。 */
    private static volatile String sNote = "";

    /** 86：守护行是否被用户收起（SP ⇒ 重启后仍隐藏）。 */
    private static final String PREFS = "settings";
    private static final String KEY_HIDE_GUARD = "guard_hidden";

    private static volatile boolean sAlertOn = false;   // 掉线告警条在不在
    private static long sLastFlush = 0L;
    private static volatile boolean sFlushPending = false;

    /** 86：刷新的两道刹车——静默去抖（一条链刷完只落一次）＋ 最小间隔 1 秒。 */
    private static final long DEBOUNCE_MS = 1200L;
    private static final long FLUSH_GAP_MS = 1000L;

    private static synchronized void push(String tool, int st) { push(tool, st, 0L); }

    private static synchronized void push(String tool, int st, long at) {
        long now = at > 0 ? at : System.currentTimeMillis();
        String zh = Words.toolZh(tool);
        if (zh == null) zh = tool == null ? "" : tool;
        Line top = LINES.peekFirst();
        if (st == -1 && top != null && top.st == -1 && tool != null && tool.equals(top.tool)
                && (now - top.at) < 800L) {
            top.times = top.times + 1;      // 同一工具 800ms 内重复 ⇒ 只留一条，记次数
            top.at = now;
            return;
        }
        Line l = new Line();
        l.tool = tool == null ? "" : tool;
        l.zh = zh;
        l.st = st;
        l.at = now;
        LINES.addFirst(l);
        while (LINES.size() > MAX_LINES) LINES.removeLast();
    }

    /** 86：把某一工具的「进行中」行收成结果行。ok=false 时按 timedOut 分「没成／超时没成」。 */
    private static synchronized void settle(String tool, boolean ok, boolean timedOut) {
        Line hit = null;
        for (Line l : LINES) {
            if (l.st < 0 && l.tool != null && l.tool.equals(tool)) { hit = l; break; }
        }
        if (hit == null) {
            Line l = new Line();
            l.tool = tool == null ? "" : tool;
            String zh = Words.toolZh(l.tool); l.zh = zh == null ? l.tool : zh;
            hit = l;
            LINES.addFirst(hit);
            while (LINES.size() > MAX_LINES) LINES.removeLast();
        }
        hit.st = ok ? 1 : (timedOut ? 2 : 0);
        hit.at = System.currentTimeMillis();
    }

    /** 86：折叠那一行——最近一条有结果的行；还没结果就显示「正在 X」；从没调过＝助手在线。 */
    private static String foldText() {
        Line busy = null;
        synchronized (Notifier.class) {
            for (Line l : LINES) {
                if (l.st >= 0) return l.zh + " " + stText(l.st) + " · " + hhmm(l.at);
                if (busy == null) busy = l;
            }
        }
        if (busy != null) return busy.zh + " " + Words.NT_R_BUSY;
        return Words.NT_BODY_ONLINE;
    }

    private static String stText(int st) {
        if (st == 1) return Words.NT_R_DONE;
        if (st == 0) return Words.NT_R_FAIL;
        if (st == 2) return Words.NT_R_TIMEOUT;
        return Words.NT_R_BUSY;
    }

    private static String lineText(Line l) {
        String n = l.times > 1 ? ("×" + l.times) : "";
        return l.zh + "（" + l.tool + "）" + n + " " + stText(l.st);
    }

    /** 86：正文槽——汇报（30 分钟内有效）优先，其次 notify 写进来的正文。 */
    private static String bodyText() {
        if (sReport != null && sReport.length() > 0 && (System.currentTimeMillis() - sReportAt) < REPORT_KEEP_MS) {
            return cut90(sReport);
        }
        return cut90(sNote);
    }

    static boolean guardHidden(Context c) {
        try { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_HIDE_GUARD, false); }
        catch (Throwable t) { return false; }
    }

    /** 守护那一行收起／放开；记住（SP ⇒ 重启后仍生效）。 */
    static void setGuardHidden(Context c, boolean v) {
        try {
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_HIDE_GUARD, v).apply();
            nlog(c, "guardHidden=" + v);
        } catch (Throwable t) { }
        refresh(c, true);
    }

    /** 大通知本体（前台服务与普通更新共用同一条）。 */
    static Notification buildMain(Context c) {
        channels(c);
        Notification.Builder b = base(c, CH_STAT);
        String fold = foldText();
        b.setGroup(GROUP_MAIN);   // ★87：主通知显式成组
        b.setContentTitle(fold);
        String body = bodyText();
        if (body.length() > 0) b.setContentText(body);
        b.setContentIntent(openApp(c));
        b.setOngoing(true);
        b.setAutoCancel(false);
        b.setOnlyAlertOnce(true);
        b.setShowWhen(false);
        try {
            Notification.InboxStyle st = new Notification.InboxStyle();
            st.setBigContentTitle(fold);
            java.util.ArrayList<Line> snap;
            synchronized (Notifier.class) { snap = new java.util.ArrayList<Line>(LINES); }
            int n = 0;
            for (Line l : snap) {
                if (n++ >= MAX_LINES) break;
                st.addLine(lineText(l));
            }
            if (body.length() > 0) st.addLine(body);
            if (!guardHidden(c)) st.addLine(Words.NT_GUARD_ON + guardSuffix());
            b.setStyle(st);
        } catch (Throwable t) { nlog(c, "inbox err " + t); }
        try {
            Intent hi = new Intent(c, NotifyBridge.class);
            boolean hd = guardHidden(c);
            hi.setAction(hd ? NotifyBridge.ACTION_SHOW_GUARD : NotifyBridge.ACTION_HIDE_GUARD);
            int fl = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) fl |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getBroadcast(c, 11, hi, fl);
            b.addAction(R.drawable.ic_stat_notify, hd ? Words.NT_GUARD_SHOW : Words.NT_GUARD_HIDE, pi);
        } catch (Throwable t) { nlog(c, "guard action err " + t); }
        return b.build();
    }

    /** 立刻把大通知重画一遍（走前台服务那条路，保证前台标记不掉）。 */
    static void showNow(Context c) {
        try {
            Notification n = buildMain(c);
            WatchdogService sv = WatchdogService.INSTANCE;
            if (sv != null) {
                try { sv.startForeground(EYE_ID, n); sLastFlush = System.currentTimeMillis(); return; } catch (Throwable t) { }
            }
            NotificationManager m = nm(c);
            if (m != null) m.notify(EYE_ID, n);
            sLastFlush = System.currentTimeMillis();
        } catch (Throwable t) { nlog(c, "showNow err " + t); }
    }

    /** 刷新：force＝坏消息／汇报，立刻刷；否则等链路静默 1.2 秒再刷（攒着一起刷）。 */
    private static void refresh(final Context c, boolean force) {
        try {
            if (sQuiet) return;
            final Context app = c.getApplicationContext() == null ? c : c.getApplicationContext();
            if (force) { showNow(app); return; }
            if (sFlushPending) return;
            sFlushPending = true;
            sMT.postDelayed(new Runnable() {
                public void run() {
                    sFlushPending = false;
                    long now = System.currentTimeMillis();
                    long wait = FLUSH_GAP_MS - (now - sLastFlush);
                    if (wait > 0) {
                        sFlushPending = true;
                        sMT.postDelayed(this, wait);
                        return;
                    }
                    showNow(app);
                }
            }, DEBOUNCE_MS);
        } catch (Throwable t) { }
    }

    /** 刷新（外部用：内存档位变了等）。 */
    public static void refreshNow(Context c) { refresh(c, true); }

    /** 86：工具调用开始——只记一行，画面上不急着刷（等这一轮做完一起刷）。 */

    /** ★86：/selfcheck 自己的内部调用不进列表（否则每次 status 都塞进「回铃／震动」两行）。 */
    private static final ThreadLocal<Boolean> SELF = new ThreadLocal<Boolean>();
    public static void selfMode(boolean on) {
        try { if (on) SELF.set(Boolean.TRUE); else SELF.remove(); } catch (Throwable t) { }
    }
    private static boolean selfNow() { try { return Boolean.TRUE.equals(SELF.get()); } catch (Throwable t) { return false; } }
    public static void toolTouch(final Context c, final String tool) {
            if (selfNow()) return;
        try {
            if (tool == null || tool.length() == 0) return;
            long now = System.currentTimeMillis();
            sMtLast = now; sMtLastTool = tool; sMtBusy = true;
            push(tool, -1, now);
            final int seq = ++sMtSeq;
            boolean first = false;
            synchronized (Notifier.class) { first = LINES.size() == 1; }
            if (first) showNow(c); else refresh(c, false);
            sMT.postDelayed(new Runnable() {
                public void run() {
                    if (sMtSeq == seq && sMtBusy) toolDone(c, tool, false, true);
                }
            }, 60000L);
        } catch (Throwable t) { }
    }

    /** 86：工具调用收尾（成败都报；坏消息立刻刷，不节流）。 */
    public static void toolDone(final Context c, String tool, boolean ok, boolean timedOut) {
        try {
            if (selfNow()) return;
            if (tool == null || tool.length() == 0) return;
            settle(tool, ok, timedOut);
            nlog(c, "line " + tool + (ok ? " 完成" : (timedOut ? " 超时没成" : " 没成")));
            sMtBusy = false;
            ++sMtSeq;
            refresh(c, !ok);
        } catch (Throwable t) { }
    }

    /** 兼容旧调用：没有结果信息的收尾（当成完成，别把话憋在通知里）。 */
    public static void toolIdle(final Context c) {
        try {
            if (!sMtBusy) return;
            sMtBusy = false;
            refresh(c, false);
        } catch (Throwable t) { }
    }

    /** 19.5 追加 · 72 号单改路／86 号单并条：汇报写进大通知正文。 */
    public static String setReport(final Context c, String text) {
        try {
            sReport = text == null ? "" : text.trim();
            sReportAt = System.currentTimeMillis();
            refresh(c, true);
            return "ok";
        } catch (Throwable t) { return "err: " + t; }
    }

    /** 86：notify（不带 id／默认 id）⇒ 写正文槽，并进大通知，不新建第二条。 */
    public static String setNote(Context c, String title, String content) {
        try {
            String t = title == null ? "" : title.trim();
            String s = content == null ? "" : content.trim();
            if (t.length() > 0 && !t.equals(Words.NT_DEF_TITLE)) {
                sNote = s.length() > 0 ? (t + "：" + s) : t;
            } else {
                sNote = s;
            }
            refresh(c, true);
            nlog(c, "setNote ok");
            return "ok";
        } catch (Throwable x) { return "err: " + x; }
    }

    /** 86：掉线告警条（唯一允许会响的一条：CH_TEST／HIGH）。掉线发，恢复撤。 */
    public static void guardState(Context c, boolean connected) {
        try {
            if (connected) {
                if (sAlertOn) { sAlertOn = false; nm(c).cancel(ALERT_ID); nlog(c, "alert clear"); }
                return;
            }
            if (sAlertOn) return;
            sAlertOn = true;
            channels(c);
            Notification.Builder b = base(c, CH_TEST);
            b.setGroup("app.reticle.alert");   // ★87：显式成组，不留"自动摘要"
            b.setContentTitle(Words.NT_A11Y_LOST);
            b.setContentText(Words.NT_A11Y_LOST_SUB);
            b.setContentIntent(openApp(c));
            b.setOngoing(false);
            b.setAutoCancel(true);
            b.setOnlyAlertOnce(true);
            nm(c).notify(ALERT_ID, b.build());
            nlog(c, "alert up");
        } catch (Throwable t) { nlog(c, "alert err " + t); }
    }
    private static final int ALERT_ID = 10;

    /** 72 号单 · 启动清理：撤掉旧版残留的媒体通知（id=9）＋删掉旧通知渠道 yitong_media。
     *  不然通知栏里那条划不掉、系统设置里还挂着「助手状态·媒体」＝等于砍一半。 */
    public static void purgeLegacyMedia(Context c) {
        // ★86.5 号单 · 这里**只删旧渠道 yitong_media，绝不许 cancel 任何 id**。
        //   86 号单起主通知（前端服务那条）＝id 9（Notifier.EYE_ID），这条 purge 在 WatchdogService.onCreate
        //   里每进程跑一次——如果哪天真有人写回 m.cancel(9) 或 m.cancelAll()，前台服务会被自己撤掉，
        //   装完/开机就"服务在跑、通知没了、系统随时能杀"。要清理旧通知请按 id 精确点名，且先确认那不是 EYE_ID。
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager m = nm(c);
                if (m != null) m.deleteNotificationChannel("yitong_media");
            }
        } catch (Throwable t) { }
        nlog(c, "purgeLegacyMedia: delete channel yitong_media（id 9 已是大通知，不再 cancel）");
    }

    private static String hhmm(long t) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(t);
        int h = c.get(java.util.Calendar.HOUR_OF_DAY), m = c.get(java.util.Calendar.MINUTE);
        return (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
    }

    private static String cut90(String s) {
        if (s == null) return "";
        return s.length() <= 90 ? s : (s.substring(0, 90) + "…");
    }


    /** 86：守护行尾随的内存档位那句（MemGuard 的让路提示，别丢）。 */
    private static String guardSuffix() {
        try { String s = MemGuard.noteSuffix(); return s == null ? "" : s; } catch (Throwable t) { return ""; }
    }
    private static String safe(String s, String dft) {
        return (s == null || s.length() == 0) ? dft : s;
    }
}
