package app.reticle;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 19.9 · 通道二「闲置自关」通知的代发口。
 * 通道二（Termux 侧服务）在闲置关闭前用 am broadcast 打这个固定 action，
 * 由一统自己发这条通知 —— 通知栏显示的图标即一统的图标
 * （vivo 通知中心只显示"发通知应用的图标"，命令通道以 shell 身份发的通知只能是默认图标）。
 * 安全设计：① 只认这一句 action；② 不读任何外部 extras（标题/文案写死在这里，外人无法伪造内容）；
 * ③ 10 秒限流；④ 独立 id=101，不碰眼线那条（id=9）。
 */
public class NotifyBridge extends BroadcastReceiver {
    public static final String ACTION = "app.reticle.action.CHANNEL_IDLE_CLOSE";
    /** 53 号单 · 手动门：让 Reticle 自己去把无障碍连接修一次。
     *  命令行那手（shell 身份可用，★已实测；必须带显式组件 -n，光 -p 到不了）：
     *    am broadcast -n app.reticle/.NotifyBridge -a app.reticle.action.A11Y_REPAIR -p app.reticle
     *  ★实测（2026-10-13 · 53 单）：本机 vivo 下这手**能把被杀掉的主包冷启动**（进程／前台服务／MCP 全回来）＋
     *    把无障碍连接接回（1~3 秒）；只写 -p <包名> 不带 -n 的写法在本机到不了 receiver（实测没落地）。
     *  安全：只认这一句 action、不读任何 extras、10 秒限流；动作＝只动 Reticle 自己那条无障碍，不碰别人。 */
    public static final String ACTION_REPAIR = "app.reticle.action.A11Y_REPAIR";
    private static final int ID = 101;
    private static final String TITLE = Words.NT_BRIDGE_TITLE;
    private static final String TEXT = Words.NT_BRIDGE_TEXT;
    private static final long GAP_MS = 10000L;
    private static long lastAt = 0L;
    private static volatile long lastRepairAt = 0L;

    @Override
    public void onReceive(Context ctx, Intent it) {
        try {
            String a = (it == null) ? null : it.getAction();

            // 53 号单：手动门 ——「修一次无障碍连接」（删名→写回，逼系统重新绑）
            if (a != null && ACTION_REPAIR.equals(a)) {
                long nowR = System.currentTimeMillis();
                if (nowR - lastRepairAt < GAP_MS) { Notifier.nlog(ctx, "bridge repair rate-limited (gap=" + (nowR - lastRepairAt) + "ms)"); return; }
                lastRepairAt = nowR;
                try { WatchdogService.ensure(ctx); } catch (Throwable t) { }
                try { McpServer.start(ctx); } catch (Throwable t) { }
                Notifier.nlog(ctx, "bridge a11y-repair start");
                final Context c2 = ctx.getApplicationContext();
                Thread tr = new Thread(new Runnable() {
                    public void run() {
                        String r = "（没跑）";
                        try { r = A11yLink.repair(c2, true); } catch (Throwable t) { r = "异常：" + t; }
                        try { Notifier.nlog(c2, "bridge a11y-repair -> " + r); } catch (Throwable t) { }
                        A11yLink.log(c2, "手动门修复返回：" + r);
                    }
                });
                tr.setDaemon(true);
                tr.start();
                return;
            }
            // 39 号单：两条口（自己的「闲置关闭」口／系统「本包被替换」口）都先把核心服务幂等拉起：
            //   装包/被杀之后 App 不会自己起，而 shell 启动不了未导出的服务——这是「装完自己回魂」的手。
            if (a != null && (ACTION.equals(a) || "android.intent.action.MY_PACKAGE_REPLACED".equals(a))) {
                // 52 号单：覆盖安装＝用户明确的手动动作 ⇒ 顺带清掉「用户已停」，装完服务能自己回来。
                if ("android.intent.action.MY_PACKAGE_REPLACED".equals(a)) {
                    try { WatchdogService.setUserStopped(ctx, false); } catch (Throwable t) { }
                }
                try { WatchdogService.ensure(ctx); } catch (Throwable t) { Notifier.nlog(ctx, "bridge ensure err " + t); }
                try { McpServer.start(ctx); } catch (Throwable t) { }
            }
            if (a == null || !ACTION.equals(a)) {
                Notifier.nlog(ctx, "bridge ignore action=" + a);
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastAt < GAP_MS) {
                Notifier.nlog(ctx, "bridge rate-limited (gap=" + (now - lastAt) + "ms)");
                return;
            }
            lastAt = now;
            String r = Notifier.send(ctx, ID, TITLE, TEXT, false);
            Notifier.nlog(ctx, "bridge idle-close id=" + ID + " -> " + r);
        } catch (Throwable t) {
            Notifier.nlog(ctx, "bridge err " + t);
        }
    }
}
