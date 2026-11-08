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
    public static final String CH_LIVE2 = "yitong_live2";   // ★102：实况提升专用（LOW 档会被系统剥掉 PROMOTED_ONGOING）
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
        // ★102：实况提升专用档——DEFAULT 但全程静音（LOW 档会被系统剥掉 PROMOTED_ONGOING）
        NotificationChannel cl2 = new NotificationChannel(CH_LIVE2, Words.NT_CH_LIVE, NotificationManager.IMPORTANCE_DEFAULT);
        cl2.setDescription(Words.NT_CH_LIVE_D);
        cl2.setShowBadge(false);
        cl2.setSound(null, null);
        cl2.enableVibration(false);
        cl2.enableLights(false);
        m.createNotificationChannel(cl2);
    }

    private static Notification.Builder base(Context c, String ch) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, ch) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_act_eye);   // ★133：默认图标（单色 alpha，系统着色；各工具另按命令分派）
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
            sLiveIds.remove(Integer.valueOf(id));
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
    /** ★120：已发过实况的 id（只第一次发时点岛展开，避免更新时反复点）。 */
    static final java.util.HashSet<Integer> sLiveIds = new java.util.HashSet<Integer>();
    /** ★122：实况通知固定用这一条 id —— vivo 按「通知」算岛活动，两个 id 会让岛分裂成主岛＋侧岛。 */
    public static final int ISLAND_ID = 10;
    public static String promoted(Context c, int id, String title, String content, int progress) {
        return promoted(c, id, title, content, progress, Words.ISLAND_CAP);
    }

    // ★133（2026-10-22）· 胶囊左侧图标：按工具／命令内容选，全部是单色 alpha（系统着色）
    static volatile int sToolIcon = 0;

    static int iconFor(String name, org.json.JSONObject args) {
        try {
            if ("shell".equals(name) && args != null) {
                String w = ToolTags.shellWord(args.optString("command", ""));
                if (w.equals("读取文件")) return R.drawable.ic_act_read;
                if (w.equals("修改文件")) return R.drawable.ic_act_write;
                if (w.equals("运行脚本")) return R.drawable.ic_act_run;
                if (w.equals("网络请求")) return R.drawable.ic_act_net;
                if (w.equals("读取系统状态") || w.equals("读取系统属性") || w.equals("读取日志")
                        || w.equals("修改设置") || w.equals("操作应用") || w.equals("管理应用")
                        || w.equals("调用系统") || w.equals("查看进程") || w.equals("结束进程"))
                    return R.drawable.ic_act_sys;
                if (w.equals("解包") || w.equals("计算校验")) return R.drawable.ic_act_box;
                if (w.equals("等待")) return R.drawable.ic_act_ecg;
                return R.drawable.ic_act_run;
            }
            if (name.equals("notify")) return R.drawable.ic_act_bell;
            if (name.equals("ping")) return R.drawable.ic_act_loop;
            if (name.equals("shot") || name.equals("ocr") || name.equals("texts") || name.equals("ui"))
                return R.drawable.ic_act_cam;
            if (name.equals("tap") || name.equals("tap_text")) return R.drawable.ic_act_tap;
            if (name.equals("swipe") || name.equals("scroll") || name.equals("scroll_to_text"))
                return R.drawable.ic_act_slide;
            if (name.equals("key")) return R.drawable.ic_act_key;
            if (name.equals("input") || name.equals("paste") || name.equals("clip")) return R.drawable.ic_act_input;
            if (name.equals("longpress")) return R.drawable.ic_act_hold;
            if (name.equals("build") || name.equals("acttest")) return R.drawable.ic_act_box;
            if (name.equals("download") || name.equals("download_status")) return R.drawable.ic_act_down;
            if (name.equals("buzz")) return R.drawable.ic_act_vib;
            if (name.equals("status") || name.equals("wait_stable")) return R.drawable.ic_act_ecg;
            if (name.equals("help") || name.equals("windows")) return R.drawable.ic_act_list;
            if (name.equals("do_chain")) return R.drawable.ic_act_chain;
            if (name.equals("wait_text")) return R.drawable.ic_act_text;
        } catch (Throwable t) { }
        return R.drawable.ic_act_eye;
    }

    /** ★123：工具调用推上岛。标题=Reticle·功能，正文=AI 写的 note（没有就退摘要），胶囊=两字。 */
    public static String islandTool(Context c, String name, org.json.JSONObject args) {
        try {
            // ★128（2026-10-22）：shell 的胶囊跟气泡同步——按命令内容判定（同一张 SH 表），压到 4 字以内；
            //   其余工具仍用两字短名。以前 shell 永远显示「命令」，跟气泡的「修改文件」对不上。
            if (notifyHolding()) return "hold";   // ★132：notify 保护期内，别的东西不覆盖岛
            sToolIcon = iconFor(name, args);   // ★133：先定图标
            String cap = ("shell".equals(name) && args != null)
                    ? ToolTags.shellCap(args.optString("command", ""))
                    : ToolTags.shortName(name);
            String title = "Reticle·" + ToolTags.label(name);
            String body = (args == null) ? "" : args.optString("note", "");
            if (body.length() == 0) body = ToolTags.summary(name, args);
            if (body.length() == 0) body = ToolTags.line(name, args);
            String r = promoted(c, ISLAND_ID, title, body, 0, cap);
            touchIdle(c);   // ★140：重置空闲定时器（idleSec 秒后进工具态，notify 另有 15 秒保护期）
            return r;
        } catch (Throwable t) { return "err: " + t; }
    }

    public static String promoted(Context c, int id, String title, String content, int progress, String cap) {
        id = ISLAND_ID;   // ★122：不管调用方传什么，实况一律共用 10 号
        // ★120·岛宽：胶囊文本用短词（vivo 岛展开后左右槽位各固定 208px，总宽下限 528px）
        String capT = (cap == null || cap.length() == 0) ? Words.ISLAND_CAP : cap;
        boolean chipIcon = true;
        String styMode = "std";
        try {
            channels(c);
            Notification.Builder b = base(c, CH_LIVE2);
            b.setDefaults(0);
            b.setContentTitle(safe(title, Words.NT_DEF_TITLE));   // ★120：卡片标题用长文案；岛胶囊吃 shortCriticalText（短）
            b.setContentText(safe(content, safe(title, "")));
            b.setContentIntent(openApp(c));
            b.setOngoing(true);
            b.setOnlyAlertOnce(true);
            b.setShowWhen(false);
            b.setCategory(Notification.CATEGORY_PROGRESS);
            try { Notification.Builder.class.getMethod("setRequestPromotedOngoing", boolean.class).invoke(b, true); }
            catch (NoSuchMethodException ns) { nlog(c, "promoted: setRequestPromotedOngoing 不存在，降级 extra"); }
            catch (Throwable t) { nlog(c, "promoted: setRequestPromotedOngoing err " + t); }
            try { Notification.Builder.class.getMethod("setShortCriticalText", CharSequence.class).invoke(b, capT); }
            catch (Throwable t) { }
            try {
                if ("big".equals(styMode)) {
                    Notification.BigTextStyle bs2 = new Notification.BigTextStyle();
                    if (content != null) bs2.bigText(content);
                    Notification.Builder.class.getMethod("setStyle", Notification.Style.class).invoke(b, bs2);
                } else if (!"std".equals(styMode)) {
                Class<?> psC = Class.forName("android.app.Notification$ProgressStyle");
                Object ps = psC.getDeclaredConstructor().newInstance();
                // 实验:不设进度 psC.getMethod("setProgress", int.class).invoke(ps, progress);
                try { psC.getMethod("setChipIconVisible", boolean.class).invoke(ps, chipIcon); } catch (Throwable t2) { }
                try { psC.getMethod("setShortCriticalText", CharSequence.class).invoke(ps, capT); } catch (Throwable t2) { }
                Class<?> stC = Class.forName("android.app.Notification$Style");
                Notification.Builder.class.getMethod("setStyle", stC).invoke(b, ps);
                }
            } catch (Throwable t) { nlog(c, "promoted: ProgressStyle err " + t); }
            { int ic = sToolIcon; sToolIcon = 0; if (ic != 0) { try { b.setSmallIcon(ic); } catch (Throwable t) { } } }   // ★133：胶囊左侧图标
            Notification n = b.build();
            try { if (n.extras != null) {
                if (!n.extras.containsKey("android.requestPromotedOngoing")) n.extras.putBoolean("android.requestPromotedOngoing", true);
                n.extras.putCharSequence("android.shortCriticalText", capT);   // ★120：vivo 岛胶囊就吃这个（放长标题会把岛撑到 776px）
                if (!n.extras.containsKey("android.selfDisplayName")) n.extras.putCharSequence("android.selfDisplayName", capT);
                n.extras.putCharSequence("android.title.capsule", capT);
            } } catch (Throwable t) { }
            n.flags |= 0x40000;   // ★102：FLAG_PROMOTED_ONGOING 直设（API35 编译环境下反射拿不到）
            { int pf = 0; try { pf = Notification.class.getField("FLAG_PROMOTED_ONGOING").getInt(null); } catch (Throwable t) { }
              nlog(c, "promoted flags=0x" + Integer.toHexString(n.flags) + " pf=0x" + Integer.toHexString(pf) + " hit=" + ((n.flags & pf) != 0)); }
            nm(c).notify(id, n);
            // ★125（2026-10-22）：砍掉「发完用无障碍点岛展开」。坐标盲点跟用户的手打架——点已展开的岛＝收起并跳进 App（下载类工具高频更新时必中）；胶囊文字自己会更新，展开交给用户手点。
            nlog(c, "promoted ok id=" + id + " progress=" + progress);
            return "ok";
        } catch (Throwable t) { nlog(c, "promoted err " + t); return "err: " + t; }
    }

    // ==== ★129（2026-10-22）· 空闲态：30 秒没动作就交给小工具／黑胶囊 ====
    /** 多少毫秒没动作就进入空闲态。 */
    private static Runnable sIdleTask = null;

    // ---- ★131（2026-10-22）· 空闲小工具（本机自用）----
    public static final int WIDGET_OFF = 0;      // 黑胶囊（全角空格）
    public static final int WIDGET_POWER = 1;    // 功耗，1 秒刷新
    public static final int WIDGET_TEXT = 2;     // 两段自定义文字轮播
    static final long WIDGET_SEG_MS = 20000L;    // 每段文字显示 20 秒
    private static Runnable sWidgetTick = null;
    private static long sWidgetStart = 0L;

    static android.content.SharedPreferences wsp(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    public static int widgetMode(Context c) {
        try { return wsp(c).getInt("idle_widget", WIDGET_OFF); } catch (Throwable t) { return WIDGET_OFF; }
    }
    public static void setWidgetMode(Context c, int m) {
        try { wsp(c).edit().putInt("idle_widget", m).apply(); } catch (Throwable t) { }
        stopWidgetLoop();
        // ★135（2026-10-23）：切换后必须重新起计时——原来只停循环不再起，岛会卡在最后一帧不动。
        touchIdle(c);
    }
    public static String widgetTextA(Context c) {
        try { return wsp(c).getString("idle_text_a", ""); } catch (Throwable t) { return ""; }
    }
    public static String widgetTextB(Context c) {
        try { return wsp(c).getString("idle_text_b", ""); } catch (Throwable t) { return ""; }
    }
    public static void setWidgetText(Context c, String a, String b) {
        try { wsp(c).edit().putString("idle_text_a", a == null ? "" : a).putString("idle_text_b", b == null ? "" : b).apply(); } catch (Throwable t) { }
        stopWidgetLoop();
    }

    /** ★131：功耗＝电压(mV)×电流(µA)。充电为正、放电为负，一位小数。
     *  sysfs 被 SELinux 挡死（连 shell 都 Permission denied），所以只走 BatteryManager。 */
    public static String powerText(Context c) {
        try {
            android.os.BatteryManager bm = (android.os.BatteryManager) c.getSystemService(Context.BATTERY_SERVICE);
            long uA = (bm == null) ? Long.MIN_VALUE
                    : bm.getLongProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            int mV = 0;
            try {
                android.content.IntentFilter f = new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED);
                android.content.Intent bi = c.registerReceiver(null, f);
                if (bi != null) mV = bi.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0);
            } catch (Throwable t) { }
            if (uA == Long.MIN_VALUE || uA == 0L || mV <= 0) return "--W";
            double w = (mV / 1000.0) * (uA / 1000000.0);
            return (w >= 0 ? "+" : "") + String.format(java.util.Locale.US, "%.1f", w) + "W";
        } catch (Throwable t) { return "--W"; }
    }
    /** ★135：文字段（每行一段，行序＝轮播顺序）。兼容旧的 idle_text_a/b。 */
    public static String widgetTexts(Context c) {
        try {
            String all = wsp(c).getString("idle_texts", null);
            if (all != null) return all;
            String a = widgetTextA(c), b = widgetTextB(c);
            if (a.length() == 0 && b.length() == 0) return "";
            return a + "\n" + b;
        } catch (Throwable t) { return ""; }
    }
    public static void setWidgetTexts(Context c, String all) {
        try { wsp(c).edit().putString("idle_texts", all == null ? "" : all).apply(); } catch (Throwable t) { }
        stopWidgetLoop();
        touchIdle(c);
    }
    public static int widgetSegSec(Context c) {
        try { return wsp(c).getInt("idle_seg_sec", 20); } catch (Throwable t) { return 20; }
    }
    public static void setWidgetSegSec(Context c, int s) {
        try { wsp(c).edit().putInt("idle_seg_sec", Math.max(1, s)).apply(); } catch (Throwable t) { }
        stopWidgetLoop();
        touchIdle(c);
    }
    static String[] widgetSegs(Context c) {
        String all = widgetTexts(c);
        if (all == null || all.trim().length() == 0) return new String[0];
        String[] raw = all.split("\n");
        java.util.ArrayList<String> out = new java.util.ArrayList<String>();
        for (int i = 0; i < raw.length; i++) {
            String s = raw[i].trim();
            if (s.length() > 0) out.add(s);
        }
        return out.toArray(new String[0]);
    }
    /** ★135：按段轮播，每段秒数可配；顺序＝文本行序。 */
    static String rotateText(Context c) {
        String[] seg = widgetSegs(c);
        if (seg.length == 0) return "　";
        if (sWidgetStart == 0L) sWidgetStart = System.currentTimeMillis();
        long ms = Math.max(1, widgetSegSec(c)) * 1000L;
        int idx = (int) (((System.currentTimeMillis() - sWidgetStart) / ms) % seg.length);
        String t = seg[idx];
        return (t == null || t.length() == 0) ? "　" : t;
    }

    /** ★151（2026-11-01）· 岛保活：进程/服务一起来就先推一次，不等工具调用；之后心跳每 5 秒强制重发。 */
    static long sLastForce = 0L;
    static final long ISLAND_HEART_MS = 5000L;

    /** ★151：是不是岛包（伪装成地图的本地自用包）。 */
    public static boolean islandPkg(Context c) {
        try { return "com.google.android.apps.maps".equals(c.getPackageName()); } catch (Throwable t) { return false; }
    }

    /** ★151：服务/界面启动时恢复岛——清去重、立刻进空闲态并开始循环。 */
    public static void resumeIsland(Context c) {
        try {
            if (!islandPkg(c)) return;
            final Context app = (c.getApplicationContext() == null) ? c : c.getApplicationContext();
            if (!liveEnabled(app)) return;
            sMT.postDelayed(new Runnable() {
                public void run() {
                    try {
                        if (!liveEnabled(app)) return;
                        if (notifyHolding()) { touchIdle(app, NOTIFY_HOLD_MS); return; }
                        if (sInFlight > 0) { touchIdle(app); return; }
                        sLiveLast = null;
                        sWidgetStart = 0L;
                        if (keepIdle(app)) { promotedIdle(app); startWidgetLoop(app); }
                        else touchIdle(app);
                    } catch (Throwable t) { }
                }
            }, 800L);
        } catch (Throwable t) { }
    }

    /** ★131：空闲期间每秒刷一次（功耗要 1 秒一跳）。 */
    static void startWidgetLoop(final Context c) {
        stopWidgetLoop();
        final Context app = (c.getApplicationContext() == null) ? c : c.getApplicationContext();
        sWidgetTick = new Runnable() {
            public void run() {
                try {
                    long now = System.currentTimeMillis();
                    if (now - sLastForce >= ISLAND_HEART_MS) { sLastForce = now; sLiveLast = null; }
                    promotedIdle(app);
                } catch (Throwable t) { }
                sMT.postDelayed(this, 500L);
            }
        };
        sMT.postDelayed(sWidgetTick, 500L);
    }
    static void stopWidgetLoop() {
        if (sWidgetTick != null) { sMT.removeCallbacks(sWidgetTick); sWidgetTick = null; }
        sWidgetStart = 0L;
    }

    /** 每次工具调用重置计时并退出小工具循环；到点进入空闲态。 */
    // ★132（2026-10-22）· notify 保护期：有 notify 通知时，15 秒内别的东西不许覆盖岛
    static final long NOTIFY_HOLD_MS = 15000L;
    static volatile long sNotifyHold = 0L;
    static boolean notifyHolding() { return System.currentTimeMillis() < sNotifyHold; }

    /** notify 上岛并锁 15 秒；到期前别的工具调用不覆盖，空闲也推迟到 15 秒后。 */
    public static void holdNotify(Context c, String title, String content) {
        try {
            sNotifyHold = System.currentTimeMillis() + NOTIFY_HOLD_MS;
            promoted(c, ISLAND_ID, safe(title, "Reticle"), safe(content, ""), 0, "通知");
            touchIdle(c, NOTIFY_HOLD_MS);
        } catch (Throwable t) { nlog(c, "holdNotify err " + t); }
    }

    static int sInFlight = 0;
    static long sInFlightAt = 0L;

    /** ★142（2026-10-24）：工具开始。期间不开始空闲计时——一条命令跑完才算"空闲"。 */
    public static void beginTool(Context c) {
        synchronized (Notifier.class) {
            if (sInFlight > 0 && sInFlightAt > 0 && System.currentTimeMillis() - sInFlightAt > 600000L) {
                sInFlight = 0;   // 兜底：某次工具异常没配对 endTool，超过 10 分钟强制归零
            }
            if (sInFlight == 0) sInFlightAt = System.currentTimeMillis();
            sInFlight++;
        }
        try {
            if (sIdleTask != null) { sMT.removeCallbacks(sIdleTask); sIdleTask = null; }
            stopWidgetLoop();
        } catch (Throwable t) { }
    }

    /** ★142：工具结束。并行调用时，最后一个结束时才开始计时。 */
    public static void endTool(Context c) {
        boolean zero = false;
        synchronized (Notifier.class) {
            if (sInFlight > 0) sInFlight--;
            if (sInFlight == 0) { zero = true; sInFlightAt = 0L; }
        }
        if (zero) touchIdle(c);
    }

    public static void touchIdle(Context c) {   // ★140：⑤设置项接线（之前硬编码 5 秒）
        if (sInFlight > 0) {   // ★142：还有工具在跑，只取消计时、不排新的
            if (sIdleTask != null) { sMT.removeCallbacks(sIdleTask); sIdleTask = null; }
            stopWidgetLoop();
            return;
        }
        touchIdle(c, idleSec(c) * 1000L);
    }
    public static void touchIdle(Context c, final long delayMs) {
        final Context app = (c.getApplicationContext() == null) ? c : c.getApplicationContext();
        if (sIdleTask != null) { sMT.removeCallbacks(sIdleTask); sIdleTask = null; }
        stopWidgetLoop();
        // ★139：总开关关闭 → 不发并收回；①关闭 → 只留最后一条，到点收回
        if (!liveEnabled(app)) { cancel(app, ISLAND_ID); return; }
        if (!keepIdle(app)) {
            final long after = Math.max(1, lastSec(app)) * 1000L;
            sIdleTask = new Runnable() {
                public void run() {
                    try { if (liveEnabled(app) && !keepIdle(app)) cancel(app, ISLAND_ID); } catch (Throwable t) { }
                }
            };
            sMT.postDelayed(sIdleTask, after);
            return;
        }
        sIdleTask = new Runnable() {
            public void run() {
                try { sLiveStart = System.currentTimeMillis(); sLiveLast = ""; promotedIdle(app); startWidgetLoop(app); } catch (Throwable t) { }
            }
        };
        sMT.postDelayed(sIdleTask, delayMs);
    }

    /** 空闲态：按小工具模式决定内容。关闭时用全角空格绕开「空串回退标题」＋ 1×1 透明图标。 */
    public static String promotedIdleLegacy(Context c) {   // ★139：旧三选一渲染，保留兜底，不再调用
        try {
            channels(c);
            int mode = widgetMode(c);
            String cap, ttl, bdy;
            if (mode == WIDGET_POWER) {
                String p = powerText(c);
                cap = p; ttl = "Reticle·功耗"; bdy = p;
            } else if (mode == WIDGET_TEXT) {
                String tx = rotateText(c);
                cap = tx; ttl = "Reticle"; bdy = tx;
            } else {
                cap = "　"; ttl = "　"; bdy = "　";
            }
            Notification.Builder b = base(c, CH_LIVE2);
            b.setDefaults(0);
            b.setContentTitle(ttl);
            b.setContentText(bdy);
            b.setContentIntent(openApp(c));
            b.setOngoing(true);
            b.setOnlyAlertOnce(true);
            b.setShowWhen(false);
            b.setCategory(Notification.CATEGORY_PROGRESS);
            if (mode == WIDGET_OFF) {
                try { b.setSmallIcon(android.graphics.drawable.Icon.createWithBitmap(blankBitmap())); }
                catch (Throwable t) { nlog(c, "idle icon err " + t); }
            } else if (mode == WIDGET_POWER) {
                b.setSmallIcon(R.drawable.ic_act_bolt);
            } else {
                b.setSmallIcon(R.drawable.ic_act_text);
            }
            try { Notification.Builder.class.getMethod("setRequestPromotedOngoing", boolean.class).invoke(b, true); } catch (Throwable t) { }
            try { Notification.Builder.class.getMethod("setShortCriticalText", CharSequence.class).invoke(b, cap); } catch (Throwable t) { }
            Notification n = b.build();
            try { if (n.extras != null) {
                if (!n.extras.containsKey("android.requestPromotedOngoing")) n.extras.putBoolean("android.requestPromotedOngoing", true);
                n.extras.putCharSequence("android.shortCriticalText", cap);
                n.extras.putCharSequence("android.title.capsule", cap);
            } } catch (Throwable t) { }
            n.flags |= 0x40000;
            nm(c).notify(ISLAND_ID, n);
            return "ok";
        } catch (Throwable t) { nlog(c, "idle err " + t); return "err: " + t; }
    }

    /** 1×1 全透明位图（缓存一份）。 */
    private static android.graphics.Bitmap sBlankBmp = null;
    private static android.graphics.Bitmap blankBitmap() {
        if (sBlankBmp == null) {
            sBlankBmp = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888);
            sBlankBmp.eraseColor(0);
        }
        return sBlankBmp;
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
        b.setContentTitle(Words.NT_DEF_TITLE);   // ★124：折叠标题固定 Reticle —— 当前工具已经由原子岛承担，不要再重复一条活动式标题
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
            // ★127（2026-10-22）：三道防误报。以前只有 sAlertOn 这一道，所以状态一抖就响：
            //   ① 进程刚起来 20 秒内不报——装包/开机/强停后无障碍还没被系统接上，属正常过渡
            //   ② 同进程内不重复（sAlertOn）
            //   ③ 跨进程最小间隔 60 秒（存 SP，进程重启也生效）
            long now = System.currentTimeMillis();
            if (now - sProcStart < PROC_GRACE_MS) { nlog(c, "alert skip：启动宽限"); return; }
            long lastAt = 0L;
            try { lastAt = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_ALERT_AT, 0L); } catch (Throwable t) { }
            if (lastAt > 0L && now - lastAt < ALERT_MIN_GAP_MS) { sAlertOn = true; nlog(c, "alert skip：距上次报警不足 60 秒"); return; }
            try { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_ALERT_AT, now).apply(); } catch (Throwable t) { }
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
    private static final int ALERT_ID = 11;   // ★127：以前是 10，与 ISLAND_ID 撞号——掉线恢复时会 cancel 掉岛那条
    /** ★127：告警防抖参数。sProcStart 在类加载时取值，等价于「进程起来那一刻」。 */
    private static final long sProcStart = System.currentTimeMillis();
    private static final long PROC_GRACE_MS = 20000L;
    private static final long ALERT_MIN_GAP_MS = 60000L;
    private static final String KEY_ALERT_AT = "alert_at";

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

    // ==== ★139（2026-10-23）· 安卓实况通知 V2：总开关、分层设置、多工具轮询 ====
    static final String P_LIVE = "live_on";
    static final String P_KEEP = "live_keep_idle";
    static final String P_LAST = "live_last_sec";
    static final String P_BLACK = "live_black";
    static final String P_IDLE = "live_idle_sec";
    static final String P_ROT = "live_rot_sec";
    static final String P_ROT_RAND = "live_rot_rand";
    static final String P_PON = "live_power_on";
    static final String P_PSEC = "live_power_ms";
    static final String P_TON = "live_text_on";
    static final String P_TN = "live_text_n";
    static final String P_TSEC = "live_text_sec";
    static final String P_TRAND = "live_text_rand";
    static final String P_MIG = "live_migrated";

    static boolean bGet(Context c, String k, boolean d) {
        try { return wsp(c).getBoolean(k, d); } catch (Throwable t) { return d; }
    }
    static void bSet(Context c, String k, boolean v) {
        try { wsp(c).edit().putBoolean(k, v).apply(); } catch (Throwable t) { }
    }
    static int iGet(Context c, String k, int d) {
        try { return wsp(c).getInt(k, d); } catch (Throwable t) { return d; }
    }
    static void iSet(Context c, String k, int v) {
        try { wsp(c).edit().putInt(k, v).apply(); } catch (Throwable t) { }
    }
    static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    /** 旧版三选一（idle_widget）迁移到新开关；只跑一次。 */
    public static void migrateLive(Context c) {
        try {
            if (bGet(c, P_MIG, false)) return;
            int old = widgetMode(c);
            if (old == WIDGET_POWER) { bSet(c, P_PON, true); bSet(c, P_TON, false); bSet(c, P_BLACK, false); }
            else if (old == WIDGET_TEXT) { bSet(c, P_PON, false); bSet(c, P_TON, true); bSet(c, P_BLACK, false); }
            else { bSet(c, P_PON, true); bSet(c, P_TON, false); bSet(c, P_BLACK, true); }
            iSet(c, P_TSEC, widgetSegSec(c));
            bSet(c, P_MIG, true);
        } catch (Throwable t) { }
    }

    public static boolean liveEnabled(Context c) { migrateLive(c); return bGet(c, P_LIVE, true); }
    public static void setLiveEnabled(Context c, boolean v) {
        bSet(c, P_LIVE, v);
        if (!v) {
            stopWidgetLoop();
            if (sIdleTask != null) { sMT.removeCallbacks(sIdleTask); sIdleTask = null; }
            sNotifyHold = 0L;
            cancel(c, ISLAND_ID);
        } else touchIdle(c);
    }
    public static boolean keepIdle(Context c) { migrateLive(c); return bGet(c, P_KEEP, true); }
    public static void setKeepIdle(Context c, boolean v) { bSet(c, P_KEEP, v); touchIdle(c); }
    public static int lastSec(Context c) { return clamp(iGet(c, P_LAST, 15), 1, 3600); }
    public static void setLastSec(Context c, int v) { iSet(c, P_LAST, clamp(v, 1, 3600)); touchIdle(c); }
    public static boolean blackIdle(Context c) { return bGet(c, P_BLACK, true); }
    public static void setBlackIdle(Context c, boolean v) { bSet(c, P_BLACK, v); touchIdle(c); }
    public static int idleSec(Context c) { return clamp(iGet(c, P_IDLE, 5), 1, 600); }
    public static void setIdleSec(Context c, int v) { iSet(c, P_IDLE, clamp(v, 1, 600)); touchIdle(c); }
    public static int rotSec(Context c) { return clamp(iGet(c, P_ROT, 20), 5, 600); }
    public static void setRotSec(Context c, int v) { iSet(c, P_ROT, clamp(v, 5, 600)); touchIdle(c); }
    public static boolean rotRand(Context c) { return bGet(c, P_ROT_RAND, false); }
    public static void setRotRand(Context c, boolean v) { bSet(c, P_ROT_RAND, v); touchIdle(c); }
    public static boolean powerOn(Context c) { return bGet(c, P_PON, true); }
    public static void setPowerOn(Context c, boolean v) { bSet(c, P_PON, v); touchIdle(c); }
    public static int powerMs(Context c) { return clamp(iGet(c, P_PSEC, 1000), 500, 60000); }
    public static void setPowerMs(Context c, int v) { iSet(c, P_PSEC, clamp(v, 500, 60000)); touchIdle(c); }
    public static boolean textOn(Context c) { return bGet(c, P_TON, false); }
    public static void setTextOn(Context c, boolean v) { bSet(c, P_TON, v); touchIdle(c); }
    public static int textN(Context c) { return clamp(iGet(c, P_TN, 2), 1, 20); }
    public static void setTextN(Context c, int v) { iSet(c, P_TN, clamp(v, 1, 20)); touchIdle(c); }
    public static int textSec(Context c) { migrateLive(c); return clamp(iGet(c, P_TSEC, 20), 1, 600); }
    public static void setTextSec(Context c, int v) { iSet(c, P_TSEC, clamp(v, 1, 600)); touchIdle(c); }
    public static boolean textRand(Context c) { return bGet(c, P_TRAND, false); }
    public static void setTextRand(Context c, boolean v) { bSet(c, P_TRAND, v); touchIdle(c); }
    public static int toolCount(Context c) { int n = 0; if (powerOn(c)) n++; if (cpuOn(c)) n++; if (ctempOn(c)) n++; if (gpuOn(c)) n++; if (memOn(c)) n++; if (cdOn(c)) n++; if (textOn(c)) n++; return n; }

    /** 参与轮播的文字条（按“条数”截断）。 */
    static String[] textLines(Context c) {
        String[] seg = widgetSegs(c);
        int n = textN(c);
        if (seg.length <= n) return seg;
        String[] out = new String[n];
        System.arraycopy(seg, 0, out, 0, n);
        return out;
    }

    /** 轮询单元：P＝功耗；T＝文字整体；T0/T1…＝拆开的文字条目（随机插入时）。 */
    // ==== ★141（2026-10-24）· 新工具：CPU／CPU 温度／GPU／可用内存／倒计时 ====
    static final String P_CPU = "live_cpu_on";
    static final String P_CTEMP = "live_cputemp_on";
    static final String P_GPU = "live_gpu_on";
    static final String P_MEM = "live_mem_on";
    static final String P_CD = "live_cd_on";
    static final String P_CDTXT = "live_cd_texts";
    static final String P_CDN = "live_cd_n";
    static final String P_CDSEC = "live_cd_sec";
    static final String P_TICK = "live_tick_ms";

    public static boolean cpuOn(Context c) { return bGet(c, P_CPU, false); }
    public static void setCpuOn(Context c, boolean v) { bSet(c, P_CPU, v); touchIdle(c); }
    public static boolean ctempOn(Context c) { return bGet(c, P_CTEMP, false); }
    public static void setCtempOn(Context c, boolean v) { bSet(c, P_CTEMP, v); touchIdle(c); }
    public static boolean gpuOn(Context c) { return bGet(c, P_GPU, false); }
    public static void setGpuOn(Context c, boolean v) { bSet(c, P_GPU, v); touchIdle(c); }
    public static boolean memOn(Context c) { return bGet(c, P_MEM, false); }
    public static void setMemOn(Context c, boolean v) { bSet(c, P_MEM, v); touchIdle(c); }
    public static boolean cdOn(Context c) { return bGet(c, P_CD, false); }
    public static void setCdOn(Context c, boolean v) { bSet(c, P_CD, v); touchIdle(c); }
    public static int tickMs(Context c) { return clamp(iGet(c, P_TICK, 2000), 500, 10000); }
    public static void setTickMs(Context c, int v) { iSet(c, P_TICK, clamp(v, 500, 10000)); touchIdle(c); }
    public static int cdN(Context c) { return clamp(iGet(c, P_CDN, 3), 1, 20); }
    public static void setCdN(Context c, int v) { iSet(c, P_CDN, clamp(v, 1, 20)); touchIdle(c); }
    public static int cdSec(Context c) { return clamp(iGet(c, P_CDSEC, 10), 1, 600); }
    public static void setCdSec(Context c, int v) { iSet(c, P_CDSEC, clamp(v, 1, 600)); touchIdle(c); }
    public static String cdTexts(Context c) {
        try { String s = wsp(c).getString(P_CDTXT, ""); return s == null ? "" : s; } catch (Throwable t) { return ""; }
    }
    public static void setCdTexts(Context c, String v) {
        try { wsp(c).edit().putString(P_CDTXT, v == null ? "" : v).apply(); } catch (Throwable t) { }
        touchIdle(c);
    }

    static String rdS(String p) {
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(p));
            StringBuilder o = new StringBuilder();
            String l;
            int n = 0;
            while ((l = r.readLine()) != null && n < 8) { if (o.length() > 0) o.append('\n'); o.append(l); n++; }
            r.close();
            return o.toString();
        } catch (Throwable t) { return null; }
    }

    static long rdl(String p) {
        try {
            String s = rdS(p);
            if (s == null) return -1;
            return Long.parseLong(s.trim().split("\\s+")[0]);
        } catch (Throwable t) { return -1; }
    }

    /** ★141：以 shell 域起一条短命令；不可用返回 null（不是"没数据"，是"这条路断了"）。 */
    static String shExec(String cmd, long timeoutMs) {
        try {
            java.lang.reflect.Method npx = rikka.shizuku.Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            npx.setAccessible(true);
            Object p = npx.invoke(null, new String[]{"sh", "-c", cmd}, null, null);
            java.io.InputStream in = (java.io.InputStream) p.getClass().getMethod("getInputStream").invoke(p);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            long end = System.currentTimeMillis() + Math.max(200, timeoutMs);
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > 8192 || System.currentTimeMillis() > end) break;
            }
            try { in.close(); } catch (Throwable t) { }
            try { p.getClass().getMethod("destroy").invoke(p); } catch (Throwable t) { }
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Throwable t) { return null; }
    }

    static long sCpuB = 0L, sCpuT = 0L, sCpuAt = 0L, sShFailAt = 0L;
    static int sShFail = 0;
    static String sCpuVal = "CPU --";

    /** ★141：CPU 使用率。shell 域读 /proc/stat 是真值；拿不到就退化为频率估算（前面加 ~）。 */
    static String cpuText(Context c) {
        long now = System.currentTimeMillis();
        if (now - sCpuAt < tickMs(c)) return sCpuVal;
        sCpuAt = now;
        if (now >= sShFailAt) {
            String s = shExec("cat /proc/stat", 1200);
            if (s == null || s.length() == 0) {
                sShFail++;
                sShFailAt = now + (sShFail >= 3 ? 60000L : 8000L);   // 连错三次就歇一分钟
            } else {
                sShFail = 0;
                try {
                    String line = s.split("\n")[0];
                    String[] f = line.trim().split("\\s+");
                    long u = Long.parseLong(f[1]), ni = Long.parseLong(f[2]), sy = Long.parseLong(f[3]);
                    long id = Long.parseLong(f[4]), io = Long.parseLong(f[5]);
                    long irq = Long.parseLong(f[6]), sirq = Long.parseLong(f[7]);
                    long st = f.length > 8 ? Long.parseLong(f[8]) : 0L;
                    long busy = u + ni + sy + irq + sirq + st;
                    long total = busy + id + io;
                    if (sCpuB > 0 && total > sCpuT) {
                        double p = (busy - sCpuB) * 100.0 / (total - sCpuT);
                        if (p < 0) p = 0;
                        if (p > 100) p = 100;
                        sCpuVal = "CPU " + Math.round(p) + "%";
                    } else if (sCpuVal == null || sCpuVal.length() == 0) sCpuVal = "CPU --";
                    sCpuB = busy;
                    sCpuT = total;
                } catch (Throwable t) { }
            }
        }
        if (sCpuB == 0L) { String e = cpuEst(c); if (e != null) sCpuVal = e; }
        return sCpuVal;
    }

    /** 频率估算：App 域自己就能读（不需要 shell），只是精度差，前面加 ~ 标明。 */
    static String cpuEst(Context c) {
        try {
            double sum = 0;
            int n = 0;
            for (int i = 0; i < 8; i++) {
                String base = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/";
                long cur = rdl(base + "scaling_cur_freq");
                long mx = rdl(base + "cpuinfo_max_freq");
                long mn = rdl(base + "cpuinfo_min_freq");
                if (cur <= 0 || mx <= mn) continue;
                double v = (cur - mn) * 1.0 / (mx - mn);
                if (v < 0) v = 0;
                if (v > 1) v = 1;
                sum += v;
                n++;
            }
            if (n == 0) return "CPU --";
            return "CPU ~" + Math.round(sum / n * 100) + "%";
        } catch (Throwable t) { return "CPU --"; }
    }

    static String[] sTzPaths = null;
    static long sHeatAt = 0L;
    static String sHeatVal = "--℃";

    static String[] scanTz() {
        try {
            java.io.File[] fs = new java.io.File("/sys/class/thermal").listFiles();
            if (fs == null) return new String[0];
            java.util.ArrayList<String> out = new java.util.ArrayList<String>();
            for (int i = 0; i < fs.length; i++) {
                String ty = rdS(new java.io.File(fs[i], "type").getPath());
                if (ty == null) continue;
                ty = ty.trim();
                if (!(ty.startsWith("cpu-") || ty.startsWith("cpullc"))) continue;
                if (ty.indexOf("trip") >= 0) continue;   // cpu-hw-trip-* 是 105℃ 阈值常量，不是实测
                out.add(new java.io.File(fs[i], "temp").getPath());
            }
            return out.toArray(new String[0]);
        } catch (Throwable t) { return new String[0]; }
    }

    static String heatText(Context c) {
        long now = System.currentTimeMillis();
        if (now - sHeatAt < tickMs(c)) return sHeatVal;
        sHeatAt = now;
        if (sTzPaths == null) sTzPaths = scanTz();
        long mx = -1;
        for (int i = 0; i < sTzPaths.length; i++) {
            long v = rdl(sTzPaths[i]);
            if (v > mx) mx = v;
        }
        if (mx <= 0) { sHeatVal = "--℃"; return sHeatVal; }
        sHeatVal = Math.round(mx / 1000.0) + "℃";
        return sHeatVal;
    }

    static long sGpuAt = 0L;
    static String sGpuVal = "GPU --";

    static String gpuText(Context c) {
        long now = System.currentTimeMillis();
        if (now - sGpuAt < tickMs(c)) return sGpuVal;
        sGpuAt = now;
        String s = rdS("/sys/class/kgsl/kgsl-3d0/gpubusy");
        if (s == null) { sGpuVal = "GPU --"; return sGpuVal; }
        try {
            String[] f = s.trim().split("\\s+");
            double b = Double.parseDouble(f[0]);
            double t = f.length > 1 ? Double.parseDouble(f[1]) : 0;
            if (t <= 0) { sGpuVal = "GPU --"; return sGpuVal; }
            double p = b * 100.0 / t;
            if (p < 0) p = 0;
            if (p > 100) p = 100;
            sGpuVal = "GPU " + Math.round(p) + "%";
        } catch (Throwable t) { sGpuVal = "GPU --"; }
        return sGpuVal;
    }

    static long sMemAt = 0L;
    static String sMemVal = "可用 --";

    static String memText(Context c) {
        long now = System.currentTimeMillis();
        if (now - sMemAt < tickMs(c)) return sMemVal;
        sMemAt = now;
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader("/proc/meminfo"));
            String l;
            while ((l = r.readLine()) != null) {
                if (l.startsWith("MemAvailable:")) {
                    String[] f = l.trim().split("\\s+");
                    long kb = Long.parseLong(f[1]);
                    double g = kb / 1048576.0;
                    sMemVal = "可用 " + String.format(java.util.Locale.US, "%.1f", g) + "G";
                    r.close();
                    return sMemVal;
                }
            }
            r.close();
            sMemVal = "可用 --";
        } catch (Throwable t) { sMemVal = "可用 --"; }
        return sMemVal;
    }

    /** 倒计时/纪念日：每行「名称|日期」，日期 2027-02-06 为一次性、02-06 为每年重复。 */
    static String[] cdLines(Context c) {
        String all = cdTexts(c);
        if (all == null || all.trim().length() == 0) return new String[0];
        String[] raw = all.split("\n");
        java.util.ArrayList<String> out = new java.util.ArrayList<String>();
        for (int i = 0; i < raw.length && out.size() < cdN(c); i++) {
            String s = raw[i].trim();
            if (s.length() > 0) out.add(s);
        }
        return out.toArray(new String[0]);
    }

    static long dayStart(long t) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    static long mk(int y, int m, int d) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.YEAR, y);
        c.set(java.util.Calendar.MONTH, m - 1);
        c.set(java.util.Calendar.DAY_OF_MONTH, d);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    static String cdOne(String raw) {
        try {
            String name = raw, ds = "";
            int bar = raw.lastIndexOf('|');
            if (bar >= 0) {
                name = raw.substring(0, bar).trim();
                ds = raw.substring(bar + 1).trim();
            } else {
                // 没写「名称|」时，也认「生日 02-06」这种空格写法
                int sp2 = raw.lastIndexOf(' ');
                if (sp2 > 0 && raw.substring(sp2 + 1).matches("\\d{1,4}-\\d{1,2}(-\\d{1,2})?")) {
                    name = raw.substring(0, sp2).trim();
                    ds = raw.substring(sp2 + 1).trim();
                }
            }
            if (name.length() == 0) name = "倒计时";
            if (ds.length() == 0) return name;
            String[] p = ds.split("-");
            long now = System.currentTimeMillis();
            long t0;
            if (p.length == 3) {
                t0 = mk(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
            } else if (p.length == 2) {
                int m = Integer.parseInt(p[0].trim()), d = Integer.parseInt(p[1].trim());
                java.util.Calendar c = java.util.Calendar.getInstance();
                int y = c.get(java.util.Calendar.YEAR);
                t0 = mk(y, m, d);
                if (t0 < dayStart(now)) t0 = mk(y + 1, m, d);
            } else return name;
            long days = Math.round((dayStart(t0) - dayStart(now)) / 86400000.0);
            if (days > 0) return name + " " + days + "天";
            if (days == 0) return "今天 " + name;
            return name + " 过" + (-days) + "天";
        } catch (Throwable t) { return raw; }
    }

    static String cdText(Context c) {
        String[] seg = cdLines(c);
        if (seg.length == 0) return "　";
        if (sWidgetStart == 0L) sWidgetStart = System.currentTimeMillis();
        long ms = Math.max(1, cdSec(c)) * 1000L;
        int idx = (int) (((System.currentTimeMillis() - sWidgetStart) / ms) % seg.length);
        return cdOne(seg[idx]);
    }

    static int iconOfUnit(String u) {
        if ("P".equals(u)) return R.drawable.ic_act_bolt;
        if ("C".equals(u)) return R.drawable.ic_act_cpu;
        if ("H".equals(u)) return R.drawable.ic_act_temp;
        if ("G".equals(u)) return R.drawable.ic_act_gpu;
        if ("M".equals(u)) return R.drawable.ic_act_mem;
        if ("D".equals(u)) return R.drawable.ic_act_timer;
        return R.drawable.ic_act_text;
    }

    static java.util.ArrayList<String> liveUnits(Context c) {
        java.util.ArrayList<String> u = new java.util.ArrayList<String>();
        if (powerOn(c)) u.add("P");
        if (cpuOn(c)) u.add("C");
        if (ctempOn(c)) u.add("H");
        if (gpuOn(c)) u.add("G");
        if (memOn(c)) u.add("M");
        if (cdOn(c) && cdLines(c).length > 0) u.add("D");
        if (textOn(c)) {
            if (textRand(c) && u.size() >= 1) {
                String[] seg = textLines(c);
                for (int i = 0; i < seg.length; i++) u.add("T" + i);
            } else u.add("T");
        }
        return u;
    }

    static long sLiveStart = 0L;
    static String sLiveLast = "";
    static long sPWAt = 0L;
    static String sPWVal = "--W";

    /** 当前时隙（随机时用确定性伪随机，保证不在相邻两格重复）。 */
    static int liveSlot(Context c, int n) {
        long rotMs = Math.max(1, rotSec(c)) * 1000L;
        long seq = (System.currentTimeMillis() - sLiveStart) / rotMs;
        int idx = (int) (seq % n);
        if (rotRand(c) && n > 1) {
            long h = Math.abs(seq * 1103515245L + 12345L);
            idx = (int) ((h / 65536L) % n);
            long h2 = Math.abs((seq - 1) * 1103515245L + 12345L);
            int prev = (int) ((h2 / 65536L) % n);
            if (idx == prev) idx = (idx + 1) % n;
        }
        return idx;
    }

    static String powerCached(Context c) {
        long now = System.currentTimeMillis();
        if (now - sPWAt >= powerMs(c) || sPWVal == null) { sPWVal = powerText(c); sPWAt = now; }
        return sPWVal;
    }

    static String liveUnitText(Context c, String u) {
        if ("P".equals(u)) return powerCached(c);
        if ("C".equals(u)) return cpuText(c);
        if ("H".equals(u)) return heatText(c);
        if ("G".equals(u)) return gpuText(c);
        if ("M".equals(u)) return memText(c);
        if ("D".equals(u)) return cdText(c);
        if ("T".equals(u)) {
            String[] seg = textLines(c);
            if (seg.length == 0) return "　";
            long ms = Math.max(1, textSec(c)) * 1000L;
            int i = (int) (((System.currentTimeMillis() - sLiveStart) / ms) % seg.length);
            String t = seg[i];
            return (t == null || t.length() == 0) ? "　" : t;
        }
        if (u.startsWith("T")) {
            try {
                int k = Integer.parseInt(u.substring(1));
                String[] seg = textLines(c);
                if (k >= 0 && k < seg.length && seg[k] != null && seg[k].length() > 0) return seg[k];
            } catch (Throwable t) { }
            return "　";
        }
        return "　";
    }

    /** ★139：空闲态渲染（V2）：总开关／继续展示／黑胶囊／多工具轮询。 */
    public static String promotedIdle(Context c) {
        try {
            if (!liveEnabled(c)) { cancel(c, ISLAND_ID); return "off"; }
            if (!keepIdle(c)) return "hold";
            if (sLiveStart == 0L) sLiveStart = System.currentTimeMillis();
            channels(c);
            boolean black = blackIdle(c);
            String cap, ttl, bdy;
            int icon = 0;
            if (black) {
                cap = "　"; ttl = "　"; bdy = "　";
            } else {
                java.util.ArrayList<String> u = liveUnits(c);
                if (u.isEmpty()) return "hold";
                String unit = u.get(liveSlot(c, u.size()));
                cap = liveUnitText(c, unit);
                ttl = "Reticle·实况";
                bdy = cap;
                icon = iconOfUnit(unit);
            }
            String key = (black ? "B" : cap + "|" + bdy);
            if (key.equals(sLiveLast)) return "same";
            sLiveLast = key;
            Notification.Builder b = base(c, CH_LIVE2);
            b.setDefaults(0);
            b.setContentTitle(ttl);
            b.setContentText(bdy);
            b.setContentIntent(openApp(c));
            b.setOngoing(true);
            b.setOnlyAlertOnce(true);
            b.setShowWhen(false);
            b.setCategory(Notification.CATEGORY_PROGRESS);
            if (black) {
                try { b.setSmallIcon(android.graphics.drawable.Icon.createWithBitmap(blankBitmap())); }
                catch (Throwable t) { nlog(c, "idle icon err " + t); }
            } else if (icon != 0) {
                try { b.setSmallIcon(icon); } catch (Throwable t) { }
            }
            try { Notification.Builder.class.getMethod("setRequestPromotedOngoing", boolean.class).invoke(b, true); } catch (Throwable t) { }
            try { Notification.Builder.class.getMethod("setShortCriticalText", CharSequence.class).invoke(b, cap); } catch (Throwable t) { }
            Notification n = b.build();
            try { if (n.extras != null) {
                if (!n.extras.containsKey("android.requestPromotedOngoing")) n.extras.putBoolean("android.requestPromotedOngoing", true);
                n.extras.putCharSequence("android.shortCriticalText", cap);
                n.extras.putCharSequence("android.title.capsule", cap);
            } } catch (Throwable t) { }
            n.flags |= 0x40000;
            nm(c).notify(ISLAND_ID, n);
            return "ok";
        } catch (Throwable t) { nlog(c, "idle err " + t); return "err: " + t; }
    }
}
