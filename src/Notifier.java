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
    public static final String CH_MEDIA = "yitong_media";
    public static final String CH_LIVE = "yitong_live";
    public static final int DEF_ID = 1;

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
        NotificationChannel cm = new NotificationChannel(CH_MEDIA, Words.NT_CH_MEDIA, NotificationManager.IMPORTANCE_LOW);
        cm.setDescription(Words.NT_CH_MEDIA_D);
        cm.setShowBadge(false);
        m.createNotificationChannel(cm);
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
        try {
            channels(c);
            Notification.Builder b = base(c, CH_STAT);
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
            if (sSess != null) sSess.setActive(false);
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

    // ==== R1 媒体路线（17 号）：MediaSession + MediaStyle 通知（借"原子随身听"上岛）====
    private static android.media.session.MediaSession sSess;

    /** R1：以媒体通知＋活跃媒体会话承载助手当前状态（歌名位＝状态；歌手位＝备注）。 */
    public static String media(Context c, int id, String title, String content, boolean playing) {
        try {
            channels(c);
            android.media.session.MediaSession s = sSess;
            if (s == null) { s = new android.media.session.MediaSession(c, "Reticle"); sSess = s; }
            s.setMetadata(new android.media.MediaMetadata.Builder()
                    .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, safe(title, Words.NT_DEF_TITLE))
                    .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, safe(content, Words.NT_CH_STAT))
                    .putString(android.media.MediaMetadata.METADATA_KEY_ALBUM, Words.NT_ALBUM)
                    .build());
            long acts = android.media.session.PlaybackState.ACTION_PLAY
                    | android.media.session.PlaybackState.ACTION_PAUSE
                    | android.media.session.PlaybackState.ACTION_PLAY_PAUSE
                    | android.media.session.PlaybackState.ACTION_STOP;
            s.setPlaybackState(new android.media.session.PlaybackState.Builder()
                    .setActions(acts)
                    .setState(playing ? android.media.session.PlaybackState.STATE_PLAYING
                                      : android.media.session.PlaybackState.STATE_PAUSED, 0, 1.0f)
                    .build());
            s.setActive(true);
            Notification.Builder b = base(c, CH_MEDIA);
            b.setContentTitle(safe(title, Words.NT_DEF_TITLE));
            b.setContentText(safe(content, ""));
            b.setContentIntent(openApp(c));
            b.setOngoing(true);
            b.setOnlyAlertOnce(true);
            b.setShowWhen(false);
            b.setVisibility(Notification.VISIBILITY_PUBLIC);
            b.setStyle(new Notification.MediaStyle().setMediaSession(s.getSessionToken()).setShowActionsInCompactView(0));
            b.addAction(new Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(c, R.drawable.ic_stat_notify),
                    Words.NT_MEDIA_BTN, openApp(c)).build());
            nm(c).notify(id, b.build());
            nlog(c, "media ok id=" + id + " playing=" + playing + " title=" + safe(title, Words.NT_DEF_TITLE));
            return "ok";
        } catch (Throwable t) { nlog(c, "media err " + t); return "err: " + t; }
    }

    /** R1：结束媒体状态（会话停用＋撤通知）。 */
    public static String mediaEnd(Context c, int id) {
        try {
            if (sSess != null) sSess.setActive(false);
            nm(c).cancel(id);
            nlog(c, "mediaEnd id=" + id);
            return "ok";
        } catch (Throwable t) { return "err: " + t; }
    }

    // ==== 18 号：原生实况型通知试验（ProgressStyle＋requestPromoted；反射实现，编译期不依赖 API36）====
    public static String promoted(Context c, int id, String title, String content, int progress) {
        try {
            channels(c);
            Notification.Builder b = base(c, CH_LIVE);
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

    // ==== R1+：工具调用自动更新媒体状态（供原子岛显示“正在…”）====
    private static final android.os.Handler sMT = new android.os.Handler(android.os.Looper.getMainLooper());
    private static long sMtLast = 0;
    private static String sMtLastTool = "";
    private static int sMtSeq = 0;
    private static volatile boolean sMtBusy = false;
    private static volatile boolean sMtHeartbeat = false;
    private static volatile boolean sMediaPlaying = true;

    /** 52 号单 · 内存让路：暂停非必要的定时刷新（媒体/眼线那条的 2.5 秒轮询），恢复后自己接着刷。 */
    private static volatile boolean sQuiet = false;
    public static void setQuiet(boolean q) { sQuiet = q; }

    public static void setMediaPlaying(boolean p) { sMediaPlaying = p; }

    private static long sMtRoundStart = 0;
    private static boolean sMtTicker = false;
    private static final long BUSY_WINDOW_MS = 90000L;
    private static final long TICK_MS = 2500L;

    private static String mmss(long ms) {
        long s = ms / 1000; if (s < 0) s = 0;
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    private static boolean working() {
        return sMtLast > 0 && (System.currentTimeMillis() - sMtLast) < BUSY_WINDOW_MS;
    }

    private static String liveText() {
        String head = (sMtRoundStart > 0) ? (Words.NT_BODY_WORKED + mmss(System.currentTimeMillis() - sMtRoundStart)) : "";
        String rep = "";
        if (sReport != null && sReport.length() > 0 && (System.currentTimeMillis() - sReportAt) < REPORT_KEEP_MS) rep = cut90(sReport);
        if (rep.length() > 0) return (head.length() > 0 ? head + " · " : "") + rep;
        String last = Words.NT_BODY_LAST + safe(toolZh(sMtLastTool), "-") + " " + hhmm(sMtLast);
        return (head.length() > 0 ? head + " · " : "") + last;
    }

    private static void startTicker(final Context c) {
        if (sMtTicker) return;
        sMtTicker = true;
        sMT.postDelayed(new Runnable() { public void run() {
            try {
                if (sQuiet) { }          // 52 号单：让路档先不刷，恢复后自动继续
                else if (working()) {
                    String t = sMtBusy ? (Words.NT_ST_WORK + safe(toolZh(sMtLastTool), Words.NT_ST_BUSY)) : Words.NT_ST_OUT;
                    media(c, 9, t, liveText(), sMediaPlaying);
                } else if (sMtRoundStart > 0) {
                    sMtRoundStart = 0;
                    media(c, 9, Words.NT_ST_IDLE, bodyText(), sMediaPlaying);
                }
            } catch (Throwable t) { }
            sMT.postDelayed(this, TICK_MS);
        } }, TICK_MS);
    }

    private static String hhmm(long t) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(t);
        int h = c.get(java.util.Calendar.HOUR_OF_DAY), m = c.get(java.util.Calendar.MINUTE);
        return (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
    }

    private static String idleText() {
        if (sMtLastTool == null || sMtLastTool.length() == 0) return Words.NT_BODY_ONLINE;
        String zh = toolZh(sMtLastTool);
        if (zh == null) zh = sMtLastTool;
        return Words.NT_BODY_ONLINE_LAST + zh + " " + hhmm(sMtLast);
    }

    private static void startHeartbeat(final Context c) {
        if (sMtHeartbeat) return;
        sMtHeartbeat = true;
        sMT.postDelayed(new Runnable() { public void run() {
            try {
                media(c, 9, sMtBusy ? (Words.NT_ST_WORK + safe(toolZh(sMtLastTool), Words.NT_ST_BUSY)) : Words.NT_ST_IDLE,
                        bodyText(), sMediaPlaying);
            } catch (Throwable t) { }
            sMT.postDelayed(this, 300000L);
        } }, 300000L);
    }

    public static void mediaTouch(final Context c, final String tool) {
        try {
            String zh = toolZh(tool);
            if (zh == null) return;
            long now = System.currentTimeMillis();
            if (tool != null && tool.equals(sMtLastTool) && (now - sMtLast) < 1500 && sMtBusy) return;
            sMtLast = now; sMtLastTool = tool; sMtBusy = true;
            if (sMtRoundStart == 0) sMtRoundStart = now;
            final int seq = ++sMtSeq;
            media(c, 9, Words.NT_ST_WORK + zh, bodyText(), sMediaPlaying);
            startTicker(c);
            startHeartbeat(c);
            sMT.postDelayed(new Runnable() { public void run() {
                if (sMtSeq == seq && sMtBusy) mediaIdle(c);
            } }, 60000L);
        } catch (Throwable t) { }
    }

    public static void mediaIdle(final Context c) {
        try {
            if (!sMtBusy) return;
            sMtBusy = false;
            final int seq = ++sMtSeq;
            media(c, 9, Words.NT_ST_OUT, bodyText(), sMediaPlaying);
            sMT.postDelayed(new Runnable() { public void run() {
                if (sMtSeq == seq && !sMtBusy) media(c, 9, Words.NT_ST_IDLE, bodyText(), sMediaPlaying);
            } }, 60000L);
        } catch (Throwable t) { }
    }


    private static volatile String sReport = "";
    private static volatile long sReportAt = 0;
    private static final long REPORT_KEEP_MS = 1800000L;

    private static String cut90(String s) {
        if (s == null) return "";
        return s.length() <= 90 ? s : (s.substring(0, 90) + "…");
    }

    /** 正文＝助手的汇报（30 分钟内有效）；过期/为空则显示「助手在线 · 最近：X HH:MM」。 */
    private static String bodyText() {
        if (sReport != null && sReport.length() > 0 && (System.currentTimeMillis() - sReportAt) < REPORT_KEEP_MS) {
            return cut90(sReport);
        }
        return idleText();
    }

    /** 19.5 追加：把助手的汇报写进眼线那条通知的正文（不新建第二条）。 */
    public static String setReport(final Context c, String text) {
        try {
            sReport = text == null ? "" : text.trim();
            sReportAt = System.currentTimeMillis();
            String t = sMtBusy ? (Words.NT_ST_WORK + safe(toolZh(sMtLastTool), Words.NT_ST_BUSY)) : Words.NT_ST_IDLE;
            return media(c, 9, t, bodyText(), sMediaPlaying);
        } catch (Throwable t) { return "err: " + t; }
    }

    private static String toolZh(String t) {
        if (t == null) return null;
        if (t.equals("shot")) return Words.NT_TOOL_SHOT;
        if (t.equals("tap")) return Words.NT_TOOL_TAP;
        if (t.equals("swipe")) return Words.NT_TOOL_SWIPE;
        if (t.equals("key")) return Words.NT_TOOL_KEY;
        if (t.equals("ui")) return Words.NT_TOOL_UI;
        if (t.equals("ocr")) return Words.NT_TOOL_OCR;
        if (t.equals("input")) return Words.NT_TOOL_INPUT;
        if (t.equals("longpress")) return Words.NT_TOOL_LONGPRESS;
        if (t.equals("shell")) return Words.NT_TOOL_SHELL;
        if (t.equals("notify")) return Words.NT_TOOL_NOTIFY;
        if (t.equals("build")) return Words.NT_TOOL_BUILD;
        if (t.equals("download")) return Words.NT_TOOL_DL;
        if (t.equals("download_status")) return Words.NT_TOOL_DL;
        if (t.equals("ping")) return Words.NT_TOOL_PING;
        return t;
    }

    private static String safe(String s, String dft) {
        return (s == null || s.length() == 0) ? dft : s;
    }
}
