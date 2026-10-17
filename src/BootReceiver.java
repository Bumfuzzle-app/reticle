package app.reticle;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

/**
 * 52 号单 · 开机自启（默认关，开关在「配置 → 无障碍看门狗」那一段）
 *
 * 干什么：手机开机后，若开关是开的——① 把本应用的无障碍写回系统在册列表（复用现成的
 * A11yWatchdog.tick）；② 幂等拉起 WatchdogService（MCP 随之启动）。
 *
 * ★三个如实前提（选项旁也写了）：
 *   ① 这功能**依赖系统权限**（WRITE_SECURE_SETTINGS／Shizuku）——Android 不允许应用自己
 *      启用无障碍，没有这条权限只能提示用户手动去开；
 *   ② 国产 ROM（vivo 等）还有第二道闸：要在系统里放行「自启动／允许后台运行」，
 *      否则这条开机广播**根本收不到**（所以顺手给了「去系统自启动设置」的入口）；
 *   ③ 用户若曾从最近任务里**划掉**过 Reticle（＝明确表示"停了"），开机**也不覆盖**它。
 *
 * ★开机时不许弹界面（Android 10+ 后台禁启 Activity）——本类只起服务、只写设置，不弹窗、不抢前台。
 * ★不改任何"用户已停"以外的状态；开关是关的 ⇒ 什么都不做（只写一行日志）。
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String PREFS = "settings";
    private static final String KEY_BOOT = "boot_autostart";

    /** 开关（默认关：用户拍的口径）。 */
    public static boolean isOn(Context c) {
        try { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_BOOT, false); }
        catch (Throwable t) { return false; }
    }

    public static void setOn(Context c, boolean v) {
        try { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_BOOT, v).apply(); } catch (Throwable t) { }
    }

    @Override public void onReceive(Context ctx, Intent it) {
        if (ctx == null) return;
        final Context c = ctx.getApplicationContext();
        final String act = (it == null || it.getAction() == null) ? "" : it.getAction();
        // tick() 里要 sleep 等写回落地（1~3 秒），不能占着 onReceive 的主线程 → goAsync 挪到后台线程。
        final PendingResult pr = goAsync();
        Thread t = new Thread(new Runnable() {
            public void run() {
                try { work(c, act); } catch (Throwable t) { log(c, "开机自启出错：" + t); }
                finally { try { pr.finish(); } catch (Throwable t) { } }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void work(Context c, String act) {
        if (WatchdogService.isUserStopped(c)) {
            log(c, "开机广播（" + act + "）：用户此前从最近任务里停过 Reticle ⇒ 不动作（手动打开 App 才恢复）");
            return;
        }
        if (!isOn(c)) {
            log(c, "开机广播（" + act + "）：开机自启开关是关的 ⇒ 不动作");
            return;
        }
        log(c, "开机广播（" + act + "）：开关是开的 ⇒ 开始恢复");
        String r;
        try { r = A11yWatchdog.tick(c, false); } catch (Throwable t) { r = "异常：" + t; }
        log(c, "开机：无障碍写回 → " + r);
        try { WatchdogService.ensure(c); log(c, "开机：已拉起前台服务（MCP 随之启动）"); }
        catch (Throwable t) { log(c, "开机：拉起前台服务失败：" + t); }
    }

    /**
     * 「去系统自启动设置」入口：按已知的厂商页面依次试（vivo／华为／OPPO／小米／三星），
     * 都不行就退回「本应用的应用详情页」。只负责**把页面打开**，点不点、怎么设由用户自己决定
     * （红线：不替用户点设置）。
     */
    public static boolean openAutoStart(Context c) {
        String[][] list = new String[][]{
            {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
            {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
            {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"},
            {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity"},
            {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
            {"com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"},
            {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
            {"com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"},
            {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
            {"com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"},
            {"com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"},
        };
        for (int i = 0; i < list.length; i++) {
            try {
                Intent x = new Intent();
                x.setComponent(new ComponentName(list[i][0], list[i][1]));
                x.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(x);
                log(c, "打开自启动设置页：" + list[i][0] + "/" + list[i][1]);
                return true;
            } catch (Throwable t) { }
        }
        try {
            Intent x = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            x.setData(Uri.fromParts("package", c.getPackageName(), null));
            x.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(x);
            log(c, "没找到自启动设置页 ⇒ 打开本应用的应用详情页");
            return true;
        } catch (Throwable t) { }
        return false;
    }

    private static void log(Context c, String s) {
        try { A11yWatchdog.log(c, "开机自启｜" + s); } catch (Throwable t) { }
    }
}
