package app.reticle;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Path;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.content.pm.PackageManager;
import android.util.Base64;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;
import rikka.shizuku.ShizukuProvider;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.graphics.Rect;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class VeriService extends AccessibilityService {

    private static final String ROOT_FALLBACK = Home.DIR;

    File root, inDir, outDir, logFile;
    private volatile boolean running = false;
    private volatile boolean connected = false;
    private Thread worker;
    private ExecutorService cbExec;
    int screenW = 0, screenH = 0;
    private final List<String> processed = new ArrayList<String>();
    private final java.util.Map<String, String> lastSeq = new java.util.HashMap<String, String>();
    private static long lastShotAt = 0;
    static volatile VeriService INSTANCE;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onServiceConnected() {
        Boot.mark(this, "t4", "无障碍 onServiceConnected");   // 86.5
        connected = true;
        A11yLink.onUp("onServiceConnected");   // 53 号单：连接态判据（进程内真值）
        INSTANCE = this;
        root = Home.get(this);
        if (root == null) root = new File(Home.DIR);
        inDir = new File(root, "in");
        outDir = new File(root, "out");
        mkdirs(inDir);
        mkdirs(outDir);
        mkdirs(new File(root, "shots"));
        mkdirs(new File(root, "loop"));
        mkdirs(new File(root, "ocr"));
        mkdirs(new File(root, "logs"));
        mkdirs(new File(root, "chain"));
        mkdirs(new File(root, "tpl"));
        mkdirs(new File(root, "fp"));
        for (int i = 1; i <= 8; i++) {
            File sf = new File(inDir, "slot_" + i + ".cmd");
            if (!sf.exists()) {
                try { writeAll(sf, new byte[0]); } catch (Throwable t) { log("slot create fail " + i + " : " + t); }
            }
        }
        try {
            DisplayMetrics dm0 = getResources().getDisplayMetrics();
            int w0 = dm0 != null ? dm0.widthPixels : 0;
            int h0 = dm0 != null ? dm0.heightPixels : 0;
            writeAll(new File(outDir, "service_status.json"),
                    ("{\"connected_at\":" + System.currentTimeMillis() + ",\"screen\":[" + w0 + "," + h0 + "]}\n").getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) { }
        logFile = new File(root, "logs/service.log");
        DisplayMetrics dm = getResources().getDisplayMetrics();
        if (dm != null) { screenW = dm.widthPixels; screenH = dm.heightPixels; }
        if (cbExec == null) cbExec = Executors.newSingleThreadExecutor();
        log("connected screen=" + screenW + "x" + screenH + " root=" + root.getAbsolutePath());
        shizukuBootstrap();
        try { A11yWatchdog.start(this); } catch (Throwable t) { }
        try { WatchdogService.ensure(this); } catch (Throwable t) { }
        McpServer.start(this);
        running = true;
        if (worker == null || !worker.isAlive()) {
            worker = new Thread(new Runnable() { public void run() { loop(); } });
            worker.setDaemon(true);
            worker.start();
        }
        try { Mascot.attach(this); } catch (Throwable t) { log("mascot attach fail " + t); }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            // ★69.5：被动接前台窗口变化（配置里已开 typeWindowStateChanged）——不轮询、不费电
            if (event != null && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) Mascot.onFgChange(event.getPackageName());
        } catch (Throwable t) { }
    }

    /** 65 号单：转屏后把形象收回屏幕内（位置按新屏幕尺寸重新夹紧，不出屏）。 */
    @Override
    public void onConfigurationChanged(android.content.res.Configuration nc) {
        super.onConfigurationChanged(nc);
        try { Mascot.onRotate(); } catch (Throwable t) { }
    }

    @Override
    /** 53 号单：onInterrupt 不等于"断开"（系统只是要求中断反馈），但值得查一眼——探一次根节点，并让看门狗去核实。 */
    public void onInterrupt() {
        log("onInterrupt");
        try { A11yLink.observe(this); } catch (Throwable t) { }
        try { A11yLink.probeNow(); } catch (Throwable t) { }
        try { A11yWatchdog.nudge(this); } catch (Throwable t) { }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        running = false;
        log("onUnbind");
        A11yLink.onDown("onUnbind");   // 53 号单
        try { Mascot.detach(); } catch (Throwable t) { }
        try { A11yWatchdog.nudge(this); } catch (Throwable t) { }
        if (INSTANCE == this) INSTANCE = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        running = false;
        log("onDestroy");
        A11yLink.onDown("onDestroy");   // 53 号单
        try { Mascot.detach(); } catch (Throwable t) { }
        try { A11yWatchdog.nudge(this); } catch (Throwable t) { }
        if (INSTANCE == this) INSTANCE = null;
        super.onDestroy();
    }

    // ---------------- 命令循环 ----------------

    private void loop() {
        while (running) {
            try {
                scanIncoming();
                Thread.sleep(120);
            } catch (InterruptedException ie) {
                break;
            } catch (Throwable t) {
                log("loop err: " + t);
                sleepQuiet(400);
            }
        }
    }

    private void scanIncoming() {
        File[] fs = inDir.listFiles();
        if (fs == null) return;
        List<File> cmds = new ArrayList<File>();
        for (int i = 0; i < fs.length; i++) {
            File f = fs[i];
            String nm = f.getName();
            if (f.isFile() && nm.endsWith(".cmd") && !processed.contains(nm)) cmds.add(f);
        }
        Collections.sort(cmds, new Comparator<File>() {
            public int compare(File a, File b) { return a.getName().compareTo(b.getName()); }
        });
        for (int i = 0; i < cmds.size(); i++) process(cmds.get(i));
    }

    private void process(File f) {
        String name = f.getName();
        String base = name.substring(0, name.length() - 4);
        String text;
        try {
            text = new String(readAll(f), StandardCharsets.UTF_8).trim();
        } catch (Throwable t) {
            processed.add(name);
            log("skip unreadable " + name + " : " + t);
            return;
        }
        if (text.length() == 0) return;
        if (!(text.equals("z=1") || text.endsWith("\nz=1"))) return; // 命令文件须以 z=1 结尾（防半截写入）
        Map<String, String> kv = new HashMap<String, String>();
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() == 0 || line.startsWith("#")) continue;
            int p = line.indexOf('=');
            if (p > 0) kv.put(line.substring(0, p).trim(), line.substring(p + 1).trim());
        }
        String seq = kv.get("seq");
        if (seq == null || seq.length() == 0) seq = "t" + System.currentTimeMillis();
        if (seq.equals(lastSeq.get(name))) return;
        String outName = base + "-" + seq + ".json";
        try {
            String res = dispatch(kv);
            writeAll(new File(outDir, outName), res.getBytes(StandardCharsets.UTF_8));
            log("done " + name + " seq=" + seq);
        } catch (Throwable t) {
            try {
                writeAll(new File(outDir, outName),
                        ("{\"ok\":0,\"err\":\"exception: " + esc(String.valueOf(t)) + "\"}").getBytes(StandardCharsets.UTF_8));
            } catch (Throwable t2) { }
            log("failed " + name + " : " + t);
        }
        lastSeq.put(name, seq);
        try {
            if (name.startsWith("slot_")) {
                writeAll(f, new byte[0]);
            } else if (!f.delete()) {
                writeAll(f, new byte[0]);
            }
        } catch (Throwable t) {
            log("clear fail " + name);
            processed.add(name);
        }
    }

    private String dispatch(Map<String, String> kv) {
        String cmd = kv.get("cmd");
        if (cmd == null) return "{\"ok\":0,\"err\":\"no cmd\"}";
        try {
            if (cmd.equals("ping")) return ping();
            if (cmd.equals("shot")) return shot(kv);
            if (cmd.equals("shotn")) return shotN(kv);
            if (cmd.equals("tap")) return tap(kv);
            if (cmd.equals("longpress")) return hold(kv);
            if (cmd.equals("input")) return inputText(kv);
            if (cmd.equals("swipe")) return swipeCmd(kv);
            if (cmd.equals("pinch")) return pinch(kv);
            if (cmd.equals("loop")) return loopTest(kv);
            if (cmd.equals("click50")) return click50(kv);
            if (cmd.equals("gback")) return globalKey(kv, "back");
            if (cmd.equals("ghome")) return globalKey(kv, "home");
            if (cmd.equals("grecents")) return globalKey(kv, "recents");
            if (cmd.equals("ocr")) return ocrCmd(kv);
            if (cmd.equals("chain")) return TaskEngine.cmdChain(this, kv);
            if (cmd.equals("crop")) return TaskEngine.cmdCrop(this, kv);
            if (cmd.equals("fp")) return TaskEngine.cmdFp(this, kv);
            if (cmd.equals("mkfp")) return TaskEngine.cmdMkfp(this, kv);
            if (cmd.equals("savefp")) return TaskEngine.cmdSaveFp(this, kv);
            if (cmd.equals("savetpl")) return TaskEngine.cmdSaveTpl(this, kv);
            if (cmd.equals("findtpl")) return TaskEngine.cmdFindTpl(this, kv);
            if (cmd.equals("findcolor")) return TaskEngine.cmdFindColor(this, kv);
            if (cmd.equals("num")) return TaskEngine.cmdNum(this, kv);
            if (cmd.equals("ui")) return dumpUi(kv);
            if (cmd.equals("tap_text")) return tapText(kv);
            if (cmd.equals("wait_text")) return waitText(kv);
            if (cmd.equals("wait_stable")) return waitStable(kv);
            if (cmd.equals("scroll")) return scrollCmd(kv);
            if (cmd.equals("paste")) return pasteText(kv);
            if (cmd.equals("scroll_to_text")) return scrollToText(kv);
            if (cmd.equals("do_chain")) return doChain(kv);
            if (cmd.equals("windows")) return windowsCmd(kv);
            if (cmd.equals("shx")) return shxCmd(kv);
            if (cmd.equals("notify")) return notifyCmd(kv);
            if (cmd.equals("report")) return reportCmd(kv);
            if (cmd.equals("mcptest")) return McpServer.selfTest(this);
            if (cmd.equals("wd")) return wdCmd(kv);
            if (cmd.equals("front")) return frontCmd(kv);
            if (cmd.equals("a11ytest")) return A11yWatchdog.selfTest(this);
            if (cmd.equals("a11yrepair")) return A11yLink.repair(this, true);   // 53 号单：删名→写回，逼系统重连
            if (cmd.equals("memtest")) return MemGuard.selfTest(this, kv);   // 52 号单：人工压内存档位（不真造危机）
        } catch (Throwable t) {
            return "{\"ok\":0,\"err\":\"" + esc(String.valueOf(t)) + "\"}";
        }
        return "{\"ok\":0,\"err\":\"unknown cmd " + esc(cmd) + "\"}";
    }

    /** ★77.6：系统键（back／home／recents）——可选 expect_pkg 前台校验（裸动作不再无校验就动手）。 */
    private String globalKey(Map<String, String> kv, String which) {
        String bad = pkgGuard(kv.containsKey("expect_pkg") ? kv.get("expect_pkg") : "");
        if (bad.length() > 0) return "{\"ok\":0,\"reason\":\"pkg_mismatch\",\"err\":\"" + esc(bad) + "\"}";
        boolean r;
        if (which.equals("home")) r = performGlobalAction(GLOBAL_ACTION_HOME);
        else if (which.equals("recents")) r = performGlobalAction(GLOBAL_ACTION_RECENTS);
        else r = performGlobalAction(GLOBAL_ACTION_BACK);
        if (!r) return "{\"ok\":0,\"reason\":\"key_failed\",\"err\":\"系统键没下发成功（无障碍没连？）\"}";
        return "{\"ok\":1,\"res\":1}";
    }

    // ---------------- 具体命令 ----------------
    // ---- 17 号：通知/原子岛（槽位命令 notify；mode=a/b/c 对应三种试验形态）----
    private String reportCmd(Map<String, String> kv) {
        String text = kv.get("text"); if (text == null) text = "";
        String r = Notifier.setReport(this, text);
        return "{\"ok\":" + ("ok".equals(r) ? 1 : 0) + "}";
    }

    private String notifyCmd(Map<String, String> kv) {
        int id = parseInt(kv.get("id"), 4);
        if ("1".equals(kv.get("clear"))) {
            String cr = Notifier.cancel(this, id);
            return "{\"ok\":" + ("ok".equals(cr) ? 1 : 0) + ",\"msg\":\"clear #" + id + "\"}";
        }
        String mode = kv.get("mode"); if (mode == null) mode = "a";
        String scene = kv.get("scene"); if (scene == null) scene = "HEALTH_REGISTER";
        String title = kv.get("title"); if (title == null) title = "Reticle";
        String content = kv.get("content"); if (content == null) content = "";
        int op = parseInt(kv.get("op"), 0);
        boolean ong = !"0".equals(kv.get("ongoing"));
        String r;
        if ("p".equals(mode)) r = Notifier.promoted(this, id, title, content, parseInt(kv.get("progress"), 60));
        else if ("m".equals(mode)) r = Words.RP_MEDIA_GONE;   // 72 号单：媒体路线已整体移除
        else if ("b".equals(mode)) r = Notifier.islandB(this, id, op, scene, title, content, ong);
        else if ("c".equals(mode)) r = Notifier.islandC(this, id, op, scene, title, content, ong);
        else r = Notifier.island(this, id, op, scene, title, content, ong);
        boolean ok = "ok".equals(r);
        return "{\"ok\":" + (ok ? 1 : 0) + ",\"mode\":\"" + esc(mode) + "\",\"id\":" + id + ",\"op\":" + op + (ok ? "" : ",\"err\":\"" + esc(r) + "\"") + "}";
    }

    /** 38 号单 · 槽位命令 wd：重跑一轮看门狗（替代被系统拒的 am start-foreground-service）。 */
    private String wdCmd(Map<String, String> kv) {
        try {
            A11yWatchdog.start(this);
            String t = A11yWatchdog.tick(this, false);
            return "{\"ok\":1,\"tick\":\"" + esc(t) + "\",\"line\":\"" + esc(A11yWatchdog.statusLine(this)) + "\"}";
        } catch (Throwable t) { return "{\"ok\":0,\"err\":\"" + esc(String.valueOf(t)) + "\"}"; }
    }

    /** 38 号单 · 槽位命令 front：重拉前台守护服务（幂等）。 */
    private String frontCmd(Map<String, String> kv) {
        try {
            WatchdogService.ensure(this);
            sleepQuiet(600);
            return "{\"ok\":1,\"line\":\"" + esc(A11yWatchdog.statusLine(this)) + "\"}";
        } catch (Throwable t) { return "{\"ok\":0,\"err\":\"" + esc(String.valueOf(t)) + "\"}"; }
    }

    boolean actGlobalBack() { return performGlobalAction(GLOBAL_ACTION_BACK); }
    boolean actGlobalHome() { return performGlobalAction(GLOBAL_ACTION_HOME); }
    boolean actGlobalRecents() { return performGlobalAction(GLOBAL_ACTION_RECENTS); }
    // ================= Shizuku 直连（一统探路 P1） =================
    private static final int SHIZUKU_REQ = 1101;
    private volatile boolean shizukuBootDone = false;

    private void shizukuBootstrap() {
        if (shizukuBootDone) return;
        shizukuBootDone = true;
        try {
            Shizuku.addBinderReceivedListener(new Shizuku.OnBinderReceivedListener() {
                public void onBinderReceived() { log("shizuku: binder received"); }
            });
            Shizuku.addBinderDeadListener(new Shizuku.OnBinderDeadListener() {
                public void onBinderDead() { log("shizuku: binder dead"); }
            });
            Shizuku.addRequestPermissionResultListener(new Shizuku.OnRequestPermissionResultListener() {
                public void onRequestPermissionResult(int requestCode, int grantResult) {
                    log("shizuku: perm result req=" + requestCode + " grant=" + grantResult);
                }
            });
            log("shizuku: bootstrap ok");
            try { if (!Shizuku.pingBinder()) ShizukuProvider.requestBinderForNonProviderProcess(this); } catch (Throwable t) { }
        } catch (Throwable t) {
            log("shizuku: bootstrap fail " + t);
        }
    }

    private String shizukuInfo() {
        try {
            boolean b = Shizuku.pingBinder();
            boolean g = false;
            if (b) {
                try { g = Shizuku.isPreV11() || Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; } catch (Throwable t) { }
            }
            return ",\"shizuku\":{\"binder\":" + (b ? 1 : 0) + ",\"granted\":" + (g ? 1 : 0) + "}";
        } catch (Throwable t) {
            return ",\"shizuku\":{\"binder\":0,\"granted\":0}";
        }
    }

    private String shxCmd(Map<String, String> kv) {
        try {
            if (!Shizuku.pingBinder()) {
                try { ShizukuProvider.requestBinderForNonProviderProcess(this); } catch (Throwable t) { }
                return "{\"ok\":0,\"st\":0,\"err\":\"" + Words.RP_BINDER_MISS + "\"}";
            }
            boolean granted = Shizuku.isPreV11() || Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                if (kv.containsKey("st")) return "{\"ok\":0,\"st\":2,\"err\":\"" + Words.RP_NO_AUTH + "\"}";
                try { Shizuku.requestPermission(SHIZUKU_REQ); } catch (Throwable t) { }
                return "{\"ok\":0,\"st\":2,\"err\":\"" + Words.RP_NO_AUTH_REQ + "\"}";
            }
            if (kv.containsKey("st")) return "{\"ok\":1,\"st\":1,\"msg\":\"" + Words.RP_AUTH_OK + "\"}";
            String c = kv.get("sh");
            if ((c == null || c.length() == 0) && kv.containsKey("b64")) {
                try { c = new String(Base64.decode(kv.get("b64"), Base64.DEFAULT), StandardCharsets.UTF_8); } catch (Throwable t) { }
            }
            if (c == null || c.length() == 0) return "{\"ok\":0,\"st\":1,\"err\":\"no cmd\"}";
            return shxRun(c);
        } catch (Throwable t) {
            return "{\"ok\":0,\"st\":-1,\"err\":\"" + esc(String.valueOf(t)) + "\"}";
        }
    }

    /** 38 号单：默认 30 秒超时。 */
    static String shxRun(String c) { return shxRun(c, 30000L); }

    /** 38 号单：阻塞等退出码（原 exitValue() 非阻塞 + 立刻 destroy ⇒「永远 -888／偶发空返回」的根因）。 */
    static String shxRun(String c, long timeoutMs) {
        long t0 = SystemClock.uptimeMillis();
        try {
            java.lang.reflect.Method npx = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            npx.setAccessible(true);
            final ShizukuRemoteProcess p = (ShizukuRemoteProcess) npx.invoke(null, new String[]{"sh", "-c", c}, null, null);
            final ByteArrayOutputStream ob = new ByteArrayOutputStream();
            final ByteArrayOutputStream eb = new ByteArrayOutputStream();
            Thread to = new Thread(new Runnable() { public void run() { pipe(p.getInputStream(), ob); } });
            Thread te = new Thread(new Runnable() { public void run() { pipe(p.getErrorStream(), eb); } });
            to.start(); te.start();
            int code = -888;
            boolean timeout = false;
            try {
                timeout = !p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Throwable t) {
                long t1 = SystemClock.uptimeMillis();
                while (SystemClock.uptimeMillis() - t1 < timeoutMs) {
                    try { code = p.exitValue(); break; } catch (Throwable t2) { try { Thread.sleep(50); } catch (Throwable t3) { } }
                }
                if (code == -888) timeout = true;
            }
            if (timeout) {
                try { p.destroy(); } catch (Throwable t) { }
            } else if (code == -888) {
                try { code = p.exitValue(); } catch (Throwable t) { }
            }
            try { to.join(timeout ? 2000 : 8000); } catch (Throwable t) { }
            try { te.join(timeout ? 2000 : 8000); } catch (Throwable t) { }
            long ms = SystemClock.uptimeMillis() - t0;
            String out = new String(ob.toByteArray(), StandardCharsets.UTF_8);
            String err = new String(eb.toByteArray(), StandardCharsets.UTF_8);
            return "{\"ok\":1,\"st\":1,\"exit\":" + code + ",\"timeout\":" + (timeout ? 1 : 0) + ",\"killed\":" + (timeout ? 1 : 0) + ",\"ms\":" + ms
                    + ",\"out\":\"" + esc(clip(out)) + "\",\"err\":\"" + esc(clip(err)) + "\"}";
        } catch (Throwable t) {
            return "{\"ok\":0,\"st\":1,\"err\":\"" + esc(String.valueOf(t)) + "\"}";
        }
    }

    /** 38 号单：status 用——当前前台包名（读不到回空串）。 */
    static String foregroundPkg() {
        try {
            VeriService s = INSTANCE;
            if (s == null) return "";
            AccessibilityNodeInfo r = null;
            try { r = s.getRootInActiveWindow(); } catch (Throwable t) { r = null; }
            if (r == null) return "";
            CharSequence p = r.getPackageName();
            return p == null ? "" : p.toString();
        } catch (Throwable t) { return ""; }
    }

    private static void pipe(InputStream in, OutputStream out) {
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (Throwable t) { }
    }

    private static String clip(String s) {
        if (s == null) return "";
        int max = 12000;
        if (s.length() <= max) return s;
        return s.substring(0, max) + Words.RP_CLIP_TAIL1 + (s.length() - max) + Words.RP_CLIP_TAIL2;
    }


    private String verName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) { return "?"; }
    }

    private String ping() {
        boolean canShot = false;
        boolean canGest = false;
        try {
            AccessibilityServiceInfo si = getServiceInfo();
            if (si != null) {
                int caps = si.getCapabilities();
                canShot = (caps & AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT) != 0;
                canGest = (caps & AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES) != 0;
            }
        } catch (Throwable t) { }
        return "{\"ok\":1,\"connected\":" + (connected ? 1 : 0)
                + ",\"canShot\":" + (canShot ? 1 : 0)
                + ",\"canGesture\":" + (canGest ? 1 : 0)
                + ",\"ver\":\"" + verName() + "\""
                + ",\"screen\":[" + screenW + "," + screenH + "]"
                + ",\"root\":\"" + esc(root == null ? "" : root.getAbsolutePath()) + "\""
                + shizukuInfo()
                + ",\"ts\":" + now() + "}";
    }

    private String shot(Map<String, String> kv) {
        String label = kv.containsKey("label") ? safe(kv.get("label")) : ("shot-" + now());
        Shot s = takeShotSync();
        if (s.bmp == null) return "{\"ok\":0,\"reason\":\"shot_failed\",\"err\":\"screenshot failed\",\"code\":" + s.err + "}";
        File dir = new File(root, "shots");
        if (!dir.isDirectory()) dir.mkdirs();                // ★77.6：写盘前先把目录建出来（目录被换成同名文件时也会失败）
        File dst = new File(dir, label + ".jpg");
        int w = s.bmp.getWidth(), h = s.bmp.getHeight();
        long t1 = SystemClock.uptimeMillis();
        boolean wok = saveJpeg(s.bmp, dst, 80);
        long saveMs = SystemClock.uptimeMillis() - t1;
        s.bmp.recycle();
        if (!wok) return "{\"ok\":0,\"reason\":\"write_failed\",\"err\":\"" + esc(Words.RP_SHOT_WRITE_FAIL + dir.getAbsolutePath()) + "\"}";
        return "{\"ok\":1,\"file\":\"shots/" + esc(dst.getName()) + "\",\"latency_ms\":" + s.latencyMs
                + ",\"save_ms\":" + saveMs + ",\"w\":" + w + ",\"h\":" + h + ",\"ts\":" + s.takenAt + "}";
    }

    private String shotN(Map<String, String> kv) {
        int n = parseInt(kv.get("n"), 20);
        int interval = parseInt(kv.get("interval"), 420);
        String prefix = safe(kv.containsKey("prefix") ? kv.get("prefix") : "lat");
        List<Long> lat = new ArrayList<Long>();
        StringBuilder sb = new StringBuilder();
        int okCount = 0;
        sb.append("{\"ok\":1,\"n\":").append(n).append(",\"interval_ms\":").append(interval).append(",\"items\":[");
        File sdir = new File(root, "shots");
        if (!sdir.isDirectory()) sdir.mkdirs();              // ★77.6：写盘前先建目录
        for (int i = 0; i < n; i++) {
            Shot s = takeShotSync();
            boolean ok = s.bmp != null;
            long saveMs = -1;
            String file = "";
            if (ok) {
                File dst = new File(sdir, prefix + "_" + pad2(i + 1) + ".jpg");
                long t1 = SystemClock.uptimeMillis();
                boolean wok = saveJpeg(s.bmp, dst, 75);
                saveMs = SystemClock.uptimeMillis() - t1;
                if (wok) { file = dst.getName(); lat.add(s.latencyMs); okCount++; } else { ok = false; }
                s.bmp.recycle();
            }
            if (i > 0) sb.append(",");
            sb.append("{\"i\":").append(i + 1).append(",\"ok\":").append(ok ? 1 : 0)
              .append(",\"latency_ms\":").append(s.latencyMs).append(",\"save_ms\":").append(saveMs)
              .append(",\"file\":\"").append(esc(file)).append("\",\"err\":").append(s.err).append("}");
            if (i < n - 1) sleepQuiet(interval);
        }
        sb.append("],\"ok_count\":").append(okCount)
          .append(",\"p50\":").append(pct(lat, 50))
          .append(",\"p95\":").append(pct(lat, 95))
          .append(",\"max\":").append(lat.isEmpty() ? -1 : Collections.max(lat).longValue());
        sb.append("}");
        return sb.toString();
    }

    private String tapOld(Map<String, String> kv) {
        ensureScreen();
        float x = normX(kv.get("x"), screenW);
        float y = normY(kv.get("y"), screenH);
        long dur = parseInt(kv.get("duration"), 60);
        GRes g = runGesture(clickStroke(x, y, dur));
        return "{\"ok\":" + (g.ok ? 1 : 0) + ",\"dispatch_ms\":" + g.ms + ",\"err\":" + g.err + "}";
    }

    private String hold(Map<String, String> kv) {
        ensureScreen();
        String bad = pkgGuard(kv.containsKey("expect_pkg") ? kv.get("expect_pkg") : "");
        if (bad.length() > 0) return "{\"ok\":0,\"reason\":\"pkg_mismatch\",\"err\":\"" + esc(bad) + "\"}";
        float x = normX(kv.get("x"), screenW);
        float y = normY(kv.get("y"), screenH);
        long dur = parseInt(kv.get("ms"), 800);
        GRes g = runGesture(clickStroke(x, y, dur));
        String via = "gesture";
        if (!g.ok) {
            // 44.5 实测：本机无障碍长按手势偶发 err=-1（未接受）→ 用 Shizuku 的 input 兜底（同一坐标，实测可用）
            try {
                String hr = shxRun("input swipe " + (int) x + " " + (int) y + " " + (int) x + " " + (int) y + " " + dur);
                if (hr != null && hr.indexOf("\"ok\":1") >= 0) { g.ok = true; via = "sh_input"; }
            } catch (Throwable t) { }
        }
        return "{\"ok\":" + (g.ok ? 1 : 0) + ",\"via\":\"" + via + "\",\"dispatch_ms\":" + g.ms + ",\"err\":" + g.err + "}";
    }

    private String swipeCmd(Map<String, String> kv) {
        ensureScreen();
        String bad = pkgGuard(kv.containsKey("expect_pkg") ? kv.get("expect_pkg") : "");
        if (bad.length() > 0) return "{\"ok\":0,\"reason\":\"pkg_mismatch\",\"err\":\"" + esc(bad) + "\"}";
        float x1 = normX(kv.get("x1"), screenW), y1 = normY(kv.get("y1"), screenH);
        float x2 = normX(kv.get("x2"), screenW), y2 = normY(kv.get("y2"), screenH);
        long dur = parseInt(kv.get("ms"), 300);
        GRes g = runGesture(swipeStroke(x1, y1, x2, y2, dur));
        return "{\"ok\":" + (g.ok ? 1 : 0) + ",\"dispatch_ms\":" + g.ms + ",\"err\":" + g.err + "}";
    }

    private String pinch(Map<String, String> kv) {
        ensureScreen();
        float cx = normX(kv.get("cx"), screenW), cy = normY(kv.get("cy"), screenH);
        float sp0 = Float.parseFloat(kv.get("from").trim()) * screenW;
        float sp1 = Float.parseFloat(kv.get("to").trim()) * screenW;
        long dur = parseInt(kv.get("ms"), 300);
        GestureDescription.Builder b = new GestureDescription.Builder();
        Path p1 = new Path();
        p1.moveTo(cx - sp0, cy);
        p1.lineTo(cx - sp1, cy);
        b.addStroke(new GestureDescription.StrokeDescription(p1, 0, dur));
        Path p2 = new Path();
        p2.moveTo(cx + sp0, cy);
        p2.lineTo(cx + sp1, cy);
        b.addStroke(new GestureDescription.StrokeDescription(p2, 0, dur));
        GRes g = runGesture(b.build());
        return "{\"ok\":" + (g.ok ? 1 : 0) + ",\"dispatch_ms\":" + g.ms + ",\"err\":" + g.err + "}";
    }

    private String loopTest(Map<String, String> kv) {
        String[] ap = kv.get("a").split(",");
        String[] bp = kv.get("b").split(",");
        String[] rg = kv.get("region").split(",");
        int rounds = parseInt(kv.get("rounds"), 20);
        int timeout = parseInt(kv.get("timeout"), 3000);
        int poll = parseInt(kv.get("poll"), 420);
        double thresh = parseDouble(kv.get("thresh"), 0.02);
        String prefix = safe(kv.containsKey("prefix") ? kv.get("prefix") : "loop");

        Shot s0 = takeShotSync();
        if (s0.bmp == null) return "{\"ok\":0,\"err\":\"baseline shot failed\",\"code\":" + s0.err + "}";
        int W = s0.bmp.getWidth(), H = s0.bmp.getHeight();
        int rx = (int) (Float.parseFloat(rg[0]) * W);
        int ry = (int) (Float.parseFloat(rg[1]) * H);
        int rw = (int) (Float.parseFloat(rg[2]) * W);
        int rh = (int) (Float.parseFloat(rg[3]) * H);
        if (rx < 0) rx = 0;
        if (ry < 0) ry = 0;
        if (rx > W - 1) rx = W - 1;
        if (ry > H - 1) ry = H - 1;
        if (rw > W - rx) rw = W - rx;
        if (rh > H - ry) rh = H - ry;
        if (rw < 1) rw = 1;
        if (rh < 1) rh = 1;
        float ax = normX(ap[0].trim(), W), ay = normY(ap[1].trim(), H);
        float bx = normX(bp[0].trim(), W), by = normY(bp[1].trim(), H);
        s0.bmp.recycle();

        StringBuilder sb = new StringBuilder();
        sb.append("{\"ok\":1,\"rounds\":").append(rounds)
          .append(",\"region_px\":[").append(rx).append(",").append(ry).append(",").append(rw).append(",").append(rh).append("]")
          .append(",\"a_px\":[").append((int) ax).append(",").append((int) ay).append("]")
          .append(",\"b_px\":[").append((int) bx).append(",").append((int) by).append("]")
          .append(",\"thresh\":").append(thresh)
          .append(",\"results\":[");
        int success = 0;
        for (int i = 1; i <= rounds; i++) {
            boolean useA = (i % 2 == 1);
            float px = useA ? ax : bx;
            float py = useA ? ay : by;
            String pTag = useA ? "a" : "b";
            Shot s1 = takeShotSync();
            if (s1.bmp == null) {
                if (i > 1) sb.append(",");
                sb.append("{\"i\":").append(i).append(",\"pt\":\"").append(pTag)
                  .append("\",\"ok\":0,\"err\":\"before shot failed ").append(s1.err).append("\"}");
                continue;
            }
            int[] before = getRegion(s1.bmp, rx, ry, rw, rh);
            saveCrop(s1.bmp, rx, ry, rw, rh, new File(root, "loop/" + prefix + "_" + pad2(i) + "_b.jpg"));
            s1.bmp.recycle();

            GRes g = runGesture(clickStroke(px, py, 60));

            long tStart = SystemClock.uptimeMillis();
            boolean changed = false;
            long confirmMs = -1;
            double diff = -1;
            Bitmap lastBmp = null;
            while (SystemClock.uptimeMillis() - tStart <= timeout) {
                Shot s2 = takeShotSync();
                if (s2.bmp == null) {
                    sleepQuiet(poll);
                    continue;
                }
                int[] after = getRegion(s2.bmp, rx, ry, rw, rh);
                diff = diffRatio(before, after);
                if (diff >= thresh) {
                    changed = true;
                    confirmMs = SystemClock.uptimeMillis() - tStart;
                    if (lastBmp != null) lastBmp.recycle();
                    lastBmp = s2.bmp;
                    break;
                }
                if (lastBmp != null) lastBmp.recycle();
                lastBmp = s2.bmp;
                sleepQuiet(poll);
            }
            if (lastBmp != null) {
                saveCrop(lastBmp, rx, ry, rw, rh, new File(root, "loop/" + prefix + "_" + pad2(i) + "_a.jpg"));
                lastBmp.recycle();
                lastBmp = null;
            }
            if (changed) success++;
            if (i > 1) sb.append(",");
            sb.append("{\"i\":").append(i).append(",\"pt\":\"").append(pTag)
              .append("\",\"ok\":").append(changed ? 1 : 0)
              .append(",\"click_ms\":").append(g.ms)
              .append(",\"confirm_ms\":").append(confirmMs)
              .append(",\"diff\":").append(round3(diff))
              .append(",\"imgs\":[\"loop/").append(prefix).append("_").append(pad2(i)).append("_b.jpg\",\"loop/")
              .append(prefix).append("_").append(pad2(i)).append("_a.jpg\"]}");
        }
        sb.append("],\"success\":").append(success).append("}");
        return sb.toString();
    }

    private String click50(Map<String, String> kv) {
        ensureScreen();
        int n = parseInt(kv.get("n"), 50);
        int interval = parseInt(kv.get("interval"), 180);
        float x = normX(kv.get("x"), screenW);
        float y = normY(kv.get("y"), screenH);
        long t0 = SystemClock.uptimeMillis();
        long tw = System.currentTimeMillis();
        int okCount = 0;
        StringBuilder marks = new StringBuilder();
        for (int i = 0; i < n; i++) {
            GRes g = runGesture(clickStroke(x, y, 50));
            if (g.ok) okCount++;
            long off = SystemClock.uptimeMillis() - t0;
            if (i > 0) marks.append(",");
            marks.append("[").append(tw + off).append(",").append(g.ok ? 1 : 0).append(",").append(g.ms).append("]");
            sleepQuiet(interval);
        }
        long total = SystemClock.uptimeMillis() - t0;
        return "{\"ok\":1,\"n\":" + n + ",\"sent_ok\":" + okCount + ",\"interval_ms\":" + interval
                + ",\"total_ms\":" + total + ",\"t0_wall\":" + tw + ",\"sends\":[" + marks + "]}";
    }

    private String ocrCmd(Map<String, String> kv) {
        Bitmap bmp = null;
        try {
            String src = kv.containsKey("src") ? kv.get("src") : "";
            if (src.length() > 0) {
                File f = src.startsWith("/") ? new File(src) : new File(root, src);
                if (!f.exists()) return "{\"ok\":0,\"err\":\"src not found\"}";
                bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (bmp == null) return "{\"ok\":0,\"err\":\"decode failed\"}";
            }
        } catch (Throwable t) { return "{\"ok\":0,\"err\":\"src: " + esc(String.valueOf(t)) + "\"}"; }
        if (bmp == null) {
            Shot s = takeShotSync();
            if (s.bmp == null) return "{\"ok\":0,\"err\":\"screenshot failed\",\"code\":" + s.err + "}";
            bmp = s.bmp;
        }
        int fW = bmp.getWidth(), fH = bmp.getHeight();
        Bitmap use = bmp;
        Bitmap crop = null;
        int ox = 0, oy = 0;
        if (kv.containsKey("region")) {
            String[] rg = kv.get("region").split(",");
            int rx = (int) (Float.parseFloat(rg[0]) * fW);
            int ry = (int) (Float.parseFloat(rg[1]) * fH);
            int rw = Math.max(1, (int) (Float.parseFloat(rg[2]) * fW));
            int rh = Math.max(1, (int) (Float.parseFloat(rg[3]) * fH));
            if (rx < 0) rx = 0;
            if (ry < 0) ry = 0;
            if (rx + rw > fW) rw = fW - rx;
            if (ry + rh > fH) rh = fH - ry;
            crop = Bitmap.createBitmap(use, rx, ry, rw, rh);
            use = crop;
            ox = rx; oy = ry;
        }
        long t0 = SystemClock.uptimeMillis();
        String text = null;
        try { text = OcrEng.ocr(use); } catch (Throwable t) { text = "ocr error: " + t; }
        long ms = SystemClock.uptimeMillis() - t0;
        String lines = "[]";
        if (OcrEng.AVAILABLE && kv.containsKey("boxes")) {
            try { lines = OcrEng.linesJson(use, ox, oy, fW, fH); } catch (Throwable t) { lines = "[]"; }
        }
        long ts = now();
        File odir = new File(root, "ocr");
        if (!odir.isDirectory()) odir.mkdirs();              // ★77.6
        File ev = new File(odir, "ocr_" + ts + ".jpg");
        boolean wok = saveJpeg(use, ev, 80);
        File tf = new File(odir, "ocr_" + ts + ".txt");
        boolean tok = false;
        try { writeAll(tf, (text == null ? "" : text).getBytes(StandardCharsets.UTF_8)); tok = tf.exists(); } catch (IOException e) { }
        if (crop != null) crop.recycle();
        bmp.recycle();
        if (!wok || !tok) return "{\"ok\":0,\"reason\":\"write_failed\",\"err\":\"" + esc(Words.RP_OCR_WRITE_FAIL + odir.getAbsolutePath()) + "\"}";
        return "{\"ok\":" + (OcrEng.AVAILABLE ? 1 : 0) + ",\"available\":" + (OcrEng.AVAILABLE ? 1 : 0)
                + ",\"ms\":" + ms + ",\"text\":\"" + esc(text) + "\",\"shot\":\"" + ev.getName()
                + "\",\"txt\":\"" + tf.getName() + "\",\"framesize\":[" + fW + "," + fH + "]"
                + ",\"lines\":" + lines + "}";
    }

    // ---------------- 截图 / 手势 ----------------

    static class Shot { Bitmap bmp; long latencyMs; long takenAt; int err = -1; }

    private static final Object SHOT_LOCK = new Object();   // ★77.6：截图／认字并发互撞（code=3）⇒ 串行化

    Shot takeShotSync() { synchronized (SHOT_LOCK) { return takeShotSync0(); } }

    Shot takeShotSync0() {
        throttleShot();
        final Shot s = new Shot();
        if (Build.VERSION.SDK_INT < 30) { s.err = -99; return s; }
        try { Mascot.hideForShot(); } catch (Throwable t) { }
        final CountDownLatch latch = new CountDownLatch(1);
        final long t0 = SystemClock.uptimeMillis();
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, cbExec, new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult res) {
                    try {
                        s.latencyMs = SystemClock.uptimeMillis() - t0;
                        s.takenAt = System.currentTimeMillis();
                        HardwareBuffer hb = res.getHardwareBuffer();
                        Bitmap hw = Bitmap.wrapHardwareBuffer(hb, res.getColorSpace());
                        if (hw != null) s.bmp = hw.copy(Bitmap.Config.ARGB_8888, false);
                        hb.close();
                        if (s.bmp == null) s.err = -98;
                    } catch (Throwable t) { s.err = -98; }
                    latch.countDown();
                }
                @Override
                public void onFailure(int errorCode) {
                    s.err = errorCode;
                    latch.countDown();
                }
            });
            latch.await(6, TimeUnit.SECONDS);
        } catch (Throwable t) { s.err = -97; } finally { try { Mascot.restoreAfterShot(); } catch (Throwable t) { } }
        return s;
    }

    static class GRes { boolean ok; long ms; int err = -1; }
    private void throttleShot() {
        long t = SystemClock.uptimeMillis();
        long wait = 350 - (t - lastShotAt);
        if (wait > 0) sleepQuiet(wait);
        lastShotAt = SystemClock.uptimeMillis();
    }


    GestureDescription clickStroke(float x, float y, long dur) {
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, Math.max(30, dur)));
        return b.build();
    }

    GestureDescription swipeStroke(float x1, float y1, float x2, float y2, long dur) {
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, Math.max(50, dur)));
        return b.build();
    }

    GRes runGesture(GestureDescription g) {
        final GRes r = new GRes();
        final CountDownLatch latch = new CountDownLatch(1);
        final long t0 = SystemClock.uptimeMillis();
        try {
            boolean acc = dispatchGesture(g, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription d) {
                    r.ok = true;
                    r.err = 0;
                    r.ms = SystemClock.uptimeMillis() - t0;
                    latch.countDown();
                }
                @Override
                public void onCancelled(GestureDescription d) {
                    r.ok = false;
                    r.err = -2;
                    r.ms = SystemClock.uptimeMillis() - t0;
                    latch.countDown();
                }
            }, null);
            if (!acc) {
                r.err = -1;
                r.ms = SystemClock.uptimeMillis() - t0;
                return r;
            }
            latch.await(5, TimeUnit.SECONDS);
        } catch (Throwable t) { r.err = -97; }
        return r;
    }

    // ---------------- 工具 ----------------

    void ensureScreen() {
        try {
            android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
            if (wm != null) {
                DisplayMetrics dm = new DisplayMetrics();
                wm.getDefaultDisplay().getRealMetrics(dm);
                if (dm.widthPixels > 0 && dm.heightPixels > 0) {
                    screenW = dm.widthPixels;
                    screenH = dm.heightPixels;
                }
            }
        } catch (Throwable t) { }
        if (screenW > 0 && screenH > 0) return;
        Shot s = takeShotSync();
        if (s.bmp != null) {
            screenW = s.bmp.getWidth();
            screenH = s.bmp.getHeight();
            s.bmp.recycle();
        }
        if (screenW <= 0) { screenW = 1080; screenH = 2400; }
    }

    private float normX(String s, int W) {
        float f = Float.parseFloat(s.trim());
        return f <= 1.0f ? f * W : f;
    }

    private float normY(String s, int H) {
        float f = Float.parseFloat(s.trim());
        return f <= 1.0f ? f * H : f;
    }

    private int[] getRegion(Bitmap b, int x, int y, int w, int h) {
        int[] arr = new int[w * h];
        b.getPixels(arr, 0, w, x, y, w, h);
        return arr;
    }

    private double diffRatio(int[] a, int[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) return -1;
        int diff = 0;
        int n = a.length;
        for (int i = 0; i < n; i++) {
            int p = a[i], q = b[i];
            int d = Math.abs((p & 0xff) - (q & 0xff))
                    + Math.abs(((p >> 8) & 0xff) - ((q >> 8) & 0xff))
                    + Math.abs(((p >> 16) & 0xff) - ((q >> 16) & 0xff));
            if (d > 60) diff++;
        }
        return (double) diff / n;
    }

    boolean saveJpeg(Bitmap b, File f, int q) {
        FileOutputStream o = null;
        try {
            File d = f.getParentFile();
            if (d != null && !d.isDirectory()) d.mkdirs();   // ★77.6：写盘前先建目录（目录被人换成同名文件时，这里同样会失败）
            o = new FileOutputStream(f);
            boolean c = b.compress(Bitmap.CompressFormat.JPEG, q, o);
            o.flush();
            return c && f.length() > 0;                      // ★77.6：真写进去才算成功（失败不许报 ok）
        } catch (Throwable t) {
            log("saveJpeg fail " + f + " " + t);
            return false;
        } finally {
            if (o != null) try { o.close(); } catch (IOException e) { }
        }
    }

    void saveCrop(Bitmap src, int x, int y, int w, int h, File f) {
        try {
            Bitmap c = Bitmap.createBitmap(src, x, y, w, h);
            saveJpeg(c, f, 75);
            c.recycle();
        } catch (Throwable t) {
            log("saveCrop fail " + f + " " + t);
        }
    }

    private static long pct(List<Long> v, int p) {
        if (v.isEmpty()) return -1;
        List<Long> c = new ArrayList<Long>(v);
        Collections.sort(c);
        int idx = (int) Math.ceil(p / 100.0 * c.size()) - 1;
        if (idx < 0) idx = 0;
        if (idx >= c.size()) idx = c.size() - 1;
        return c.get(idx).longValue();
    }

    private static int parseInt(String s, int d) {
        try { return Integer.parseInt(s.trim()); } catch (Throwable t) { return d; }
    }

    private static double parseDouble(String s, double d) {
        try { return Double.parseDouble(s.trim()); } catch (Throwable t) { return d; }
    }

    private static String round3(double d) { return String.format("%.3f", d); }

    private static String pad2(int i) { return i < 10 ? "0" + i : String.valueOf(i); }

    private static String safe(String s) {
        return s == null ? "x" : s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private void sleepQuiet(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { }
    }

    private static void mkdirs(File d) {
        if (d != null && !d.exists()) d.mkdirs();
    }

    private static long now() { return System.currentTimeMillis(); }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': b.append("\\\\"); break;
                case '"': b.append("\\\""); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 32) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.toString();
    }

    // ===== 一统搬家 P2：MCP 工具入口（复用本服务的截图/手势/按键/读界面） =====
    /** 53 号单 · 功能探针：拿得到根节点吗（1＝拿到 0＝拿不到）。只在自检/慢轮询里调，不进 10 秒热循环。 */
    static int probeRoot() {
        VeriService s = INSTANCE;
        if (s == null) return 0;
        try {
            AccessibilityNodeInfo r = s.getRootInActiveWindow();
            if (r != null) return 1;
        } catch (Throwable t) { }
        try {
            java.util.List<android.view.accessibility.AccessibilityWindowInfo> ws = s.getWindows();
            if (ws != null && ws.size() > 0) return 1;
        } catch (Throwable t) { }
        return 0;
    }

    static String mcpDispatch(String cmd, java.util.Map<String, String> kv) {
        VeriService s = INSTANCE;
        if (s == null) return "{\"ok\":0,\"err\":\"" + Words.RP_A11Y_NOCONN + "\"}";
        java.util.Map<String, String> k = new java.util.HashMap<String, String>();
        if (kv != null) k.putAll(kv);
        k.put("cmd", cmd);
        try { return s.dispatch(k); } catch (Throwable t) { return "{\"ok\":0,\"err\":\"" + esc(String.valueOf(t)) + "\"}"; }
    }

    private String dumpUi(java.util.Map<String, String> kv) {
        int maxNodes = parseInt(kv.get("max"), 120);
        int maxDepth = parseInt(kv.get("depth"), 20);
        AccessibilityNodeInfo root = null;
        try { root = getRootInActiveWindow(); } catch (Throwable t) { root = null; }
        if (root == null) return "{\"ok\":0,\"err\":\"" + Words.RP_UI_EMPTY + "\"}";
        StringBuilder sb = new StringBuilder();
        int[] cnt = new int[]{0};
        try { walkUi(root, 0, maxDepth, maxNodes, sb, cnt); } catch (Throwable t) { log("dumpUi err " + t); }
        String pkg = "";
        try { CharSequence p = root.getPackageName(); if (p != null) pkg = p.toString(); } catch (Throwable t) { }
        return "{\"ok\":1,\"package\":\"" + esc(pkg) + "\",\"count\":" + cnt[0] + ",\"nodes\":[" + sb.toString() + "]}";
    }

    private void walkUiOld(AccessibilityNodeInfo n, int depth, int maxDepth, int maxNodes, StringBuilder sb, int[] cnt) {
        if (n == null || cnt[0] >= maxNodes || depth > maxDepth) return;
        String tx = "", dc = "", vid = "", cls = "";
        try { CharSequence c1 = n.getText(); if (c1 != null) tx = c1.toString(); } catch (Throwable e) { }
        try { CharSequence c2 = n.getContentDescription(); if (c2 != null) dc = c2.toString(); } catch (Throwable e) { }
        try { String v = n.getViewIdResourceName(); if (v != null) vid = v; } catch (Throwable e) { }
        try { CharSequence c3 = n.getClassName(); if (c3 != null) cls = c3.toString(); } catch (Throwable e) { }
        boolean clk = false;
        try { clk = n.isClickable(); } catch (Throwable e) { }
        if (tx.length() > 0 || dc.length() > 0 || vid.length() > 0 || clk) {
            int L = 0, T = 0, R = 0, Bd = 0;
            try { Rect rc = new Rect(); n.getBoundsInScreen(rc); L = rc.left; T = rc.top; R = rc.right; Bd = rc.bottom; } catch (Throwable e) { }
            if (cnt[0] > 0) sb.append(",");
            sb.append("{\"t\":\"").append(esc(clipS(tx, 100)))
              .append("\",\"d\":\"").append(esc(clipS(dc, 80)))
              .append("\",\"id\":\"").append(esc(shortId(vid)))
              .append("\",\"c\":\"").append(esc(shortCls(cls)))
              .append("\",\"b\":[").append(L).append(",").append(T).append(",").append(R).append(",").append(Bd)
              .append("],\"clk\":").append(clk ? 1 : 0).append("}");
            cnt[0]++;
        }
        int cn = 0;
        try { cn = n.getChildCount(); } catch (Throwable e) { }
        for (int i = 0; i < cn && cnt[0] < maxNodes; i++) {
            AccessibilityNodeInfo ch = null;
            try { ch = n.getChild(i); } catch (Throwable e) { }
            if (ch != null) walkUi(ch, depth + 1, maxDepth, maxNodes, sb, cnt);
        }
    }

    private static String clipS(String s, int max) {
        if (s == null) return "";
        s = s.replace("\n", " ").replace("\r", " ");
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private static String shortId(String v) {
        if (v == null || v.length() == 0) return "";
        int i = v.indexOf(":id/");
        return i >= 0 ? v.substring(i + 4) : v;
    }

    private static String shortCls(String c) {
        if (c == null || c.length() == 0) return "";
        int i = c.lastIndexOf('.');
        return i >= 0 ? c.substring(i + 1) : c;
    }

    private static byte[] readAll(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        } finally {
            in.close();
        }
    }

    private static void writeAll(File f, byte[] b) throws IOException {
        OutputStream o = new FileOutputStream(f);
        try {
            o.write(b);
        } finally {
            o.close();
        }
    }

    void log(String s) {
        try {
            if (logFile == null && root != null) logFile = new File(root, "logs/service.log");
            if (logFile == null) return;
            FileOutputStream o = new FileOutputStream(logFile, true);
            o.write((now() + " " + s + "\n").getBytes(StandardCharsets.UTF_8));
            o.close();
        } catch (Throwable t) { }
    }
    // ---- 一统 13：输入文本（MCP 工具 input）----
    private String inputTextOld(Map<String, String> kv) {
        String text = kv.get("text");
        if (text == null) text = "";
        AccessibilityNodeInfo focus = null;
        try { focus = findFocus(AccessibilityNodeInfo.FOCUS_INPUT); } catch (Throwable t) { }
        if (focus == null) {
            AccessibilityNodeInfo root = null;
            try { root = getRootInActiveWindow(); } catch (Throwable t) { }
            focus = findEditable(root, 0);
        }
        if (focus == null) return "{\"ok\":0,\"err\":\"" + Words.RP_INPUT_NOFOCUS + "\"}";
        boolean ok = false;
        try {
            Bundle b = new Bundle();
            b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            ok = focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
        } catch (Throwable t) { }
        String readback = "";
        try { CharSequence cs = focus.getText(); if (cs != null) readback = cs.toString(); } catch (Throwable t) { }
        String via = "set_text";
        if (!ok) {
            try {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("mcp-input", text));
                    String r = shxRun("input keyevent 279");
                    ok = (r != null);
                    via = "clipboard_paste";
                    try { Thread.sleep(400); } catch (Throwable t) { }
                    try { CharSequence cs2 = focus.getText(); if (cs2 != null) readback = cs2.toString(); } catch (Throwable t) { }
                }
            } catch (Throwable t) { }
        }
        if (!ok) return "{\"ok\":0,\"via\":\"" + via + "\",\"readback\":\"" + esc(clipS(readback, 80)) + "\",\"err\":\"" + Words.RP_INPUT_FAIL + "\"}";
        return "{\"ok\":1,\"via\":\"" + via + "\",\"readback\":\"" + esc(clipS(readback, 80)) + "\"}";
    }

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 12) return null;
        try { if (n.isEditable()) return n; } catch (Throwable t) { }
        int cn = 0;
        try { cn = n.getChildCount(); } catch (Throwable t) { }
        for (int i = 0; i < cn; i++) {
            AccessibilityNodeInfo ch = null;
            try { ch = n.getChild(i); } catch (Throwable t) { }
            AccessibilityNodeInfo r = findEditable(ch, depth + 1);
            if (r != null) return r;
        }
        return null;
    }

    // ════════════ 44 号单（2026-10-11）· 无障碍优化与自明化 ════════════
    // 病根：只会"点坐标"、点完不验货、不会找可点祖先、等页面靠 sleep；微信等自绘界面根本没有控件树。
    // 本段新增：按文字/描述/ID 找控件（可点祖先 + 控件级点击 + 截图认字兜底）、等出现/等消失/等稳定、
    //          scroll（走中段、避让手势区、先验前台）、点击后验货。

    private AccessibilityNodeInfo rootNow() {
        try { return getRootInActiveWindow(); } catch (Throwable t) { return null; }
    }


    /** 控件树快照摘要。没有任何可读内容的树（微信等自绘界面）一律当"读不到"→ 返回空串。 */
    private String treeSig() {
        AccessibilityNodeInfo r = rootNow();
        if (r == null) return "";
        StringBuilder sb = new StringBuilder();
        int[] cnt = new int[]{0};
        try { sigWalk(r, 0, sb, cnt); } catch (Throwable t) { }
        if (cnt[0] == 0) return "";
        return Integer.toHexString(sb.toString().hashCode()) + "/" + sb.length();
    }

    private void sigWalk(AccessibilityNodeInfo n, int depth, StringBuilder sb, int[] cnt) {
        if (n == null || depth > 24 || sb.length() > 6000) return;
        try {
            boolean any = false;
            CharSequence t = n.getText(); if (t != null && t.length() > 0) { sb.append(t); any = true; }
            CharSequence d = n.getContentDescription(); if (d != null && d.length() > 0) { sb.append(d); any = true; }
            CharSequence i = n.getViewIdResourceName(); if (i != null && i.length() > 0) { sb.append(i); any = true; }
            if (any) cnt[0]++;
            if (n.isClickable()) sb.append('C');
            if (n.isScrollable()) sb.append('S');
            android.graphics.Rect rc = new android.graphics.Rect();
            n.getBoundsInScreen(rc);
            sb.append(rc.left).append(',').append(rc.top).append(';');
        } catch (Throwable t) { }
        int cn = 0;
        try { cn = n.getChildCount(); } catch (Throwable t) { }
        sb.append('[').append(cn).append(']');
        for (int k = 0; k < cn; k++) {
            AccessibilityNodeInfo ch = null;
            try { ch = n.getChild(k); } catch (Throwable t) { }
            sigWalk(ch, depth + 1, sb, cnt);
        }
    }

    /** 这段文字出现了吗：控件树可读就查树；树读不到（微信/游戏）就截图认字。 */
    private boolean seenText(String s) {
        if (s == null || s.length() == 0) return false;
        String ts = treeSig();
        if (ts.length() > 0) return textPresent(s);
        return ocrFind(s, false) != null;
    }

    private void collectNodes(AccessibilityNodeInfo n, int depth, String text, boolean exact,
                              String desc, String id, List<AccessibilityNodeInfo> out, int max) {
        if (n == null || depth > 28 || out.size() >= max) return;
        String tx = "", dc = "", vid = "";
        try { CharSequence c = n.getText(); if (c != null) tx = c.toString(); } catch (Throwable t) { }
        try { CharSequence c = n.getContentDescription(); if (c != null) dc = c.toString(); } catch (Throwable t) { }
        try { String v = n.getViewIdResourceName(); if (v != null) vid = v; } catch (Throwable t) { }
        boolean hit = true;
        if (text.length() > 0) hit = exact ? (tx.equals(text) || dc.equals(text)) : (tx.contains(text) || dc.contains(text));
        if (hit && desc.length() > 0) hit = exact ? (dc.equals(desc) || tx.equals(desc)) : (dc.contains(desc) || tx.contains(desc));
        if (hit && id.length() > 0) {
            String sid = shortId(vid), qid = shortId(id);
            hit = exact ? (sid.equals(qid) || vid.equals(id)) : (vid.contains(id) || sid.contains(qid) || vid.contains(qid));
        }
        if (hit && (tx.length() > 0 || dc.length() > 0 || vid.length() > 0)) out.add(n);
        int cn = 0;
        try { cn = n.getChildCount(); } catch (Throwable t) { }
        for (int k = 0; k < cn && out.size() < max; k++) {
            AccessibilityNodeInfo ch = null;
            try { ch = n.getChild(k); } catch (Throwable t) { }
            collectNodes(ch, depth + 1, text, exact, desc, id, out, max);
        }
    }

    private List<AccessibilityNodeInfo> findNodes(String text, boolean exact, String desc, String id, int max) {
        List<AccessibilityNodeInfo> out = new ArrayList<AccessibilityNodeInfo>();
        AccessibilityNodeInfo r = rootNow();
        if (r != null) collectNodes(r, 0, text, exact, desc, id, out, max);
        return out;
    }

    /** 向上找最近的"可点且可用"祖先（含自身）——微信里文字节点常 clk=0，可点的是它祖先。 */
    private AccessibilityNodeInfo clickableAncestor(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo cur = n;
        int guard = 0;
        while (cur != null && guard++ < 14) {
            try { if (cur.isClickable() && cur.isEnabled()) return cur; } catch (Throwable t) { }
            AccessibilityNodeInfo p = null;
            try { p = cur.getParent(); } catch (Throwable t) { }
            cur = p;
        }
        return null;
    }

    private String nodeBounds(AccessibilityNodeInfo n) {
        try {
            android.graphics.Rect rc = new android.graphics.Rect();
            n.getBoundsInScreen(rc);
            return rc.left + "," + rc.top + "," + rc.right + "," + rc.bottom;
        } catch (Throwable t) { return ""; }
    }

    private String nodeCls(AccessibilityNodeInfo n) {
        try { CharSequence c = n.getClassName(); if (c != null) return c.toString(); } catch (Throwable t) { }
        return "";
    }

    private String nodeBrief(AccessibilityNodeInfo n) {
        String t = "", d = "", id = "";
        try { CharSequence c = n.getText(); if (c != null) t = clipS(c.toString(), 40); } catch (Throwable e) { }
        try { CharSequence c = n.getContentDescription(); if (c != null) d = clipS(c.toString(), 40); } catch (Throwable e) { }
        try { String v = n.getViewIdResourceName(); if (v != null) id = shortId(v); } catch (Throwable e) { }
        return "{\"t\":\"" + esc(t) + "\",\"d\":\"" + esc(d) + "\",\"id\":\"" + esc(id)
                + "\",\"cls\":\"" + esc(shortCls(nodeCls(n))) + "\",\"b\":[" + nodeBounds(n) + "]}";
    }

    private boolean textPresent(String s) {
        if (s == null || s.length() == 0) return false;
        return findNodes(s, false, "", "", 1).size() > 0;
    }

    /** 截图认字兜底：整屏认字，找包含 needle 的行 → {cx, cy, 文本}；找不到 null。 */
    private String[] ocrFindOld(String needle, boolean exact) {
        if (needle == null || needle.length() == 0) return null;
        Shot s = takeShotSync();
        if (s.bmp == null) return null;
        String js = null;
        int W = s.bmp.getWidth(), H = s.bmp.getHeight();
        try { js = OcrEng.linesJson(s.bmp, 0, 0, W, H); } catch (Throwable t) { }
        try { s.bmp.recycle(); } catch (Throwable t) { }
        if (js == null) return null;
        try {
            org.json.JSONArray arr = new org.json.JSONArray(js);
            for (int pass = 0; pass < 2; pass++) {
                String bestT = null;
                double bestCx = 0, bestCy = 0;
                int bestLen = -1;
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject o = arr.getJSONObject(i);
                    String t = o.optString("t", "");
                    if (t.length() == 0) continue;
                    boolean m = (pass == 0) ? t.equals(needle) : t.contains(needle);
                    if (!m) continue;
                    int len = t.length();
                    if (bestT == null || len < bestLen) {
                        bestT = t; bestLen = len;
                        bestCx = o.optDouble("cx", 0); bestCy = o.optDouble("cy", 0);
                    }
                }
                if (bestT != null) return new String[]{String.valueOf(bestCx), String.valueOf(bestCy), bestT};
            }
        } catch (Throwable t) { }
        return null;
    }

    private String describeQuery(String text, String desc, String id) {
        if (text != null && text.trim().length() > 0) return text.trim();
        if (desc != null && desc.trim().length() > 0) return Words.RP_TT_DESC_PREFIX + desc.trim();
        return id == null ? "" : id.trim();
    }

    /** 点完验货：前台包名变了／树变了／期待文字出现了，都算"有反应"。 */
    private String waitVerdict(String beforePkg, String beforeSig, String expect, long waitMs) {
        long t0 = SystemClock.uptimeMillis();
        boolean changed = false;
        int expectHit = 0;
        long afterMs = -1;
        boolean needExpect = expect != null && expect.length() > 0;
        String pkgAfter = beforePkg;
        while (true) {
            try { pkgAfter = foregroundPkg(); } catch (Throwable t) { }
            String sig = "";
            try { sig = sceneSig(); } catch (Throwable t) { }
            if (needExpect && seenText(expect)) { expectHit = 1; changed = true; afterMs = SystemClock.uptimeMillis() - t0; break; }
            if (!pkgAfter.equals(beforePkg) || !sig.equals(beforeSig)) { changed = true; afterMs = SystemClock.uptimeMillis() - t0; break; }
            if (SystemClock.uptimeMillis() - t0 >= waitMs) break;
            sleepQuiet(90);
        }
        return ",\"pkg_before\":\"" + esc(beforePkg) + "\",\"pkg_after\":\"" + esc(pkgAfter) + "\""
             + ",\"changed\":" + (changed ? 1 : 0) + ",\"after_ms\":" + afterMs
             + ",\"expect_hit\":" + expectHit;
    }

    /** 等画面稳定（连续 quiet 毫秒没变化就返回），带超时。返回耗时 ms。 */
    private long waitStableQuiet(long quiet, long timeout) {
        long t0 = SystemClock.uptimeMillis();
        String last = sceneSig();
        long lastChange = t0;
        while (true) {
            sleepQuiet(100);
            long nw = SystemClock.uptimeMillis();
            String cur = sceneSig();
            if (!cur.equals(last)) { last = cur; lastChange = nw; }
            if (nw - lastChange >= quiet || nw - t0 >= timeout) return nw - t0;
        }
    }

    /** 前台包名校验（自明化 S2）：不是目标应用就别动手。错就返回人话，对就返回空串。 */
    private String pkgGuard(String expectPkg) {
        if (expectPkg == null || expectPkg.length() == 0) return "";
        String fp = "";
        try { fp = foregroundPkg(); } catch (Throwable t) { }
        if (expectPkg.equals(fp)) return "";
        return String.format(Words.RP_TT_WRONG_PKG, expectPkg, fp.length() == 0 ? Words.RP_TT_PKG_UNK : fp);
    }

    // ---- T1：按文字/描述/ID 点（本单正主）----
    private String tapText(Map<String, String> kv) {
        ensureScreen();
        String text = kv.containsKey("text") ? kv.get("text") : "";
        String desc = kv.containsKey("desc") ? kv.get("desc") : "";
        String id = kv.containsKey("id") ? kv.get("id") : "";
        if (text.trim().length() == 0 && desc.trim().length() == 0 && id.trim().length() == 0)
            return "{\"ok\":0,\"err\":\"" + esc(Words.RP_TT_NEED) + "\"}";
        boolean exact = "1".equals(kv.get("exact"));
        int index = parseInt(kv.get("index"), 0);
        long timeout = parseInt(kv.get("timeout_ms"), 1500);
        long dur = parseInt(kv.get("duration"), 80);
        boolean allowOcr = !"0".equals(kv.get("ocr"));
        String expect = kv.containsKey("expect") ? kv.get("expect") : "";
        String bad = pkgGuard(kv.containsKey("expect_pkg") ? kv.get("expect_pkg") : "");
        if (bad.length() > 0) return "{\"ok\":0,\"reason\":\"pkg_mismatch\",\"err\":\"" + esc(bad) + "\"}";
        String beforePkg = "";
        try { beforePkg = foregroundPkg(); } catch (Throwable t) { }
        String beforeSig = sceneSig();
        long t0 = SystemClock.uptimeMillis();
        AccessibilityNodeInfo hit = null;
        int seen = 0;
        String ocrT = null;
        float ocrX = 0, ocrY = 0;
        while (true) {
            List<AccessibilityNodeInfo> found = findNodes(text, exact, desc, id, 400);
            seen = found.size();
            if (seen > index) { hit = found.get(index); break; }
            if (allowOcr && text.trim().length() > 0) {
                String[] o = ocrFind(text.trim(), exact);
                if (o != null) { ocrX = Float.parseFloat(o[0]); ocrY = Float.parseFloat(o[1]); ocrT = o[2]; break; }
            }
            if (SystemClock.uptimeMillis() - t0 >= timeout) break;
            sleepQuiet(140);
        }
        long findMs = SystemClock.uptimeMillis() - t0;
        if (hit == null && ocrT == null)
            return "{\"ok\":0,\"reason\":\"not_found\",\"err\":\"" + esc(String.format(Words.RP_TT_NOTFOUND, describeQuery(text, desc, id), timeout))
                    + "\",\"tree_hits\":0,\"find_ms\":" + findMs + "}";
        boolean clickOk = false;
        String via = "";
        String target = "null";
        int ocrUsed = 0;
        if (hit != null) {
            AccessibilityNodeInfo tgt = clickableAncestor(hit);
            int isAnc = 0;
            if (tgt == null) { tgt = hit; }
            else if (tgt != hit) { isAnc = 1; }
            target = nodeBrief(tgt);
            try { clickOk = tgt.performAction(AccessibilityNodeInfo.ACTION_CLICK); } catch (Throwable t) { clickOk = false; }
            via = isAnc == 1 ? "action_click_ancestor" : "action_click";
            if (!clickOk) {
                try {
                    android.graphics.Rect rc = new android.graphics.Rect();
                    tgt.getBoundsInScreen(rc);
                    if (rc.width() > 0 && rc.height() > 0) {
                        GRes g = runGesture(clickStroke(rc.centerX(), rc.centerY(), dur));
                        clickOk = g.ok;
                        via = isAnc == 1 ? "gesture_ancestor_center" : "gesture_center";
                    }
                } catch (Throwable t) { }
            }
        } else {
            ocrUsed = 1;
            target = "{\"t\":\"" + esc(clipS(ocrT, 40)) + "\",\"via\":\"ocr\"}";
            GRes g = runGesture(clickStroke(ocrX * screenW, ocrY * screenH, dur));
            clickOk = g.ok;
            via = "ocr_center";
        }
        String verdict = waitVerdict(beforePkg, beforeSig, expect, parseInt(kv.get("wait_ms"), 1200));
        int retried = 0;
        if (hit != null && via.startsWith("action_click") && verdict.indexOf("\"changed\":1") < 0) {
            try {
                AccessibilityNodeInfo t2 = clickableAncestor(hit);
                if (t2 == null) t2 = hit;
                android.graphics.Rect rc = new android.graphics.Rect();
                t2.getBoundsInScreen(rc);
                if (rc.width() > 0 && rc.height() > 0) {
                    GRes g2 = runGesture(clickStroke(rc.centerX(), rc.centerY(), dur));
                    clickOk = clickOk || g2.ok;
                    retried = 1;
                    via = via + "_then_gesture_center";
                    verdict = waitVerdict(beforePkg, beforeSig, expect, parseInt(kv.get("wait_ms"), 1200));
                }
            } catch (Throwable t) { }
        }
        boolean needExp = expect.length() > 0;
        boolean expOk = verdict.indexOf("\"expect_hit\":1") >= 0;
        boolean okAll = clickOk && (!needExp || expOk);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"ok\":").append(okAll ? 1 : 0)
          .append(",\"via\":\"").append(via).append("\"")
          .append(",\"target\":").append(target)
          .append(",\"tree_hits\":").append(seen)
          .append(",\"index\":").append(index)
          .append(",\"ocr_fallback\":").append(ocrUsed)
          .append(",\"find_ms\":").append(findMs)
          .append(",\"click_ok\":").append(clickOk ? 1 : 0)
          .append(",\"retried\":").append(retried)
          .append(verdict);
        if (!clickOk) sb.append(",\"err\":\"").append(esc(Words.RP_TT_CLICK_FAIL)).append("\"");
        else if (!expOk) sb.append(",\"reason\":\"expect_timeout\",\"err\":\"").append(esc(String.format(Words.RP_TT_EXPECT_FAIL, expect))).append("\"");
        sb.append("}");
        return sb.toString();
    }

    // ---- T2a：等出现 / 等消失 ----
    private String waitText(Map<String, String> kv) {
        ensureScreen();
        String text = kv.containsKey("text") ? kv.get("text") : "";
        String desc = kv.containsKey("desc") ? kv.get("desc") : "";
        String id = kv.containsKey("id") ? kv.get("id") : "";
        if (text.trim().length() == 0 && desc.trim().length() == 0 && id.trim().length() == 0)
            return "{\"ok\":0,\"err\":\"" + esc(Words.RP_TT_NEED) + "\"}";
        String state = kv.containsKey("state") ? kv.get("state").trim() : "appear";
        boolean wantGone = state.equalsIgnoreCase("disappear") || state.equals("消失");
        long timeout = parseInt(kv.get("timeout_ms"), 5000);
        boolean allowOcr = !"0".equals(kv.get("ocr"));
        boolean exact = "1".equals(kv.get("exact"));
        long t0 = SystemClock.uptimeMillis();
        while (true) {
            List<AccessibilityNodeInfo> found = findNodes(text, exact, desc, id, 20);
            boolean present = found.size() > 0;
            String via = present ? "tree" : "";
            String node = present ? nodeBrief(found.get(0)) : "null";
            if (!present && allowOcr && text.trim().length() > 0) {
                String[] o = ocrFind(text.trim(), exact);
                if (o != null) {
                    present = true;
                    via = "ocr";
                    node = "{\"t\":\"" + esc(clipS(o[2], 40)) + "\",\"cx\":" + o[0] + ",\"cy\":" + o[1] + "}";
                }
            }
            boolean done = wantGone ? !present : present;
            long ms = SystemClock.uptimeMillis() - t0;
            if (done) return "{\"ok\":1,\"state\":\"" + (wantGone ? "disappear" : "appear") + "\",\"present\":"
                    + (present ? 1 : 0) + ",\"via\":\"" + via + "\",\"node\":" + node + ",\"ms\":" + ms + "}";
            if (ms >= timeout)
                return "{\"ok\":0,\"err\":\"" + esc(String.format(wantGone ? Words.RP_WAIT_GONE_TIMEOUT : Words.RP_WAIT_APPEAR_TIMEOUT,
                        describeQuery(text, desc, id), timeout)) + "\",\"ms\":" + ms + "}";
            sleepQuiet(150);
        }
    }

    // ---- T2b：等画面稳定 ----
    private String waitStable(Map<String, String> kv) {
        ensureScreen();
        long quiet = parseInt(kv.get("quiet_ms"), 600);
        long timeout = parseInt(kv.get("timeout_ms"), 5000);
        long t0 = SystemClock.uptimeMillis();
        String last = sceneSig();
        long lastChange = t0;
        while (true) {
            sleepQuiet(120);
            long nw = SystemClock.uptimeMillis();
            String cur = sceneSig();
            if (!cur.equals(last)) { last = cur; lastChange = nw; }
            if (nw - lastChange >= quiet)
                return "{\"ok\":1,\"stable_ms\":" + quiet + ",\"ms\":" + (nw - t0) + ",\"sig\":\"" + esc(last) + "\"}";
            if (nw - t0 >= timeout)
                return "{\"ok\":0,\"err\":\"" + esc(String.format(Words.RP_STABLE_TIMEOUT, timeout)) + "\",\"ms\":" + (nw - t0) + "}";
        }
    }

    // ---- S2：滚动（只走中段，避让底部手势区与边缘返回区）----
    private String scrollCmd(Map<String, String> kv) {
        ensureScreen();
        String dir = kv.containsKey("direction") ? kv.get("direction").trim() : "down";
        double dist = parseDouble(kv.get("distance"), 0.5);
        if (dist < 0.05) dist = 0.05;
        if (dist > 0.6) dist = 0.6;
        long dur = parseInt(kv.get("duration"), parseInt(kv.get("ms"), 320));
        String bad = pkgGuard(kv.containsKey("expect_pkg") ? kv.get("expect_pkg") : "");
        if (bad.length() > 0) return "{\"ok\":0,\"reason\":\"pkg_mismatch\",\"err\":\"" + esc(bad) + "\"}";
        String beforePkg = "";
        try { beforePkg = foregroundPkg(); } catch (Throwable t) { }
        String beforeSig = sceneSig();
        float px1, py1, px2, py2;
        float d = (float) dist;
        if (dir.equals("up") || dir.equals("down") || dir.equals("上") || dir.equals("下")) {
            if (dir.equals("down") || dir.equals("下")) {
                py1 = Math.min(0.78f, 0.55f + d / 2f);
                py2 = Math.max(0.18f, py1 - d);
            } else {
                py1 = Math.max(0.22f, 0.45f - d / 2f);
                py2 = Math.min(0.82f, py1 + d);
            }
            px1 = 0.5f; px2 = 0.5f;
        } else {
            if (dir.equals("right") || dir.equals("右")) { px1 = 0.85f; px2 = Math.max(0.20f, px1 - d); }
            else { px1 = 0.15f; px2 = Math.min(0.80f, px1 + d); }
            py1 = 0.5f; py2 = 0.5f;
        }
        GRes g = runGesture(swipeStroke(px1 * screenW, py1 * screenH, px2 * screenW, py2 * screenH, dur));
        String verdict = waitVerdict(beforePkg, beforeSig, "", parseInt(kv.get("wait_ms"), 700));
        return "{\"ok\":" + (g.ok ? 1 : 0) + ",\"direction\":\"" + esc(dir)
                + "\",\"from\":[" + round3(px1) + "," + round3(py1) + "],\"to\":[" + round3(px2) + "," + round3(py2)
                + "],\"dispatch_ms\":" + g.ms + verdict + "}";
    }

    // ---- T3：tap 点后判定（前台包名/树变化/expect 文字）----
    // ═══════════ 44.5 A：剪贴板直贴（paste）：写剪贴板→找聚焦框→粘贴→读回校验→尽量还原 ═══════════
    private boolean isEditableNode(AccessibilityNodeInfo n) {
        try { return n != null && n.isEditable(); } catch (Throwable t) { return false; }
    }

    /** 粘贴后的读回校验：① 节点文本 ② 整屏认字 ③ 画面变化；返回 0/1/2/3（0＝没验到）。 */
    private int verifyPaste(String text, AccessibilityNodeInfo focus, String beforeSig) {
        try {
            if (focus != null) { String rb = readTextOf(focus); if (readbackOk(rb, text)) return 1; }
        } catch (Throwable t) { }
        try {
            String probe = text.length() > 8 ? text.substring(0, 8) : text;
            if (probe.length() > 0 && ocrFind(probe, false) != null) return 2;
        } catch (Throwable t) { }
        try { if (beforeSig != null && beforeSig.length() > 0 && !sceneSig().equals(beforeSig)) return 3; } catch (Throwable t) { }
        return 0;
    }

    /** 点弹出来的「粘贴」菜单项（微信这类自绘界面）：先按文字找控件点，再退到认字点中心。 */
    private String tapPasteMenuItem() {
        try {
            List<AccessibilityNodeInfo> ms = findNodes("粘贴", false, "", "", 6);
            if (ms.size() > 0) {
                AccessibilityNodeInfo t = clickableAncestor(ms.get(0));
                if (t == null) t = ms.get(0);
                try { if (t.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return "tree_click"; } catch (Throwable e) { }
                try {
                    android.graphics.Rect rc = new android.graphics.Rect();
                    t.getBoundsInScreen(rc);
                    if (rc.width() > 0 && rc.height() > 0) {
                        GRes g = runGesture(clickStroke(rc.centerX(), rc.centerY(), 80));
                        if (g.ok) return "tree_center";
                    }
                } catch (Throwable e) { }
            }
        } catch (Throwable t) { }
        try {
            String[] o = ocrFind("粘贴", false);
            if (o != null && Double.parseDouble(o[1]) < 0.75) {
                GRes g = runGesture(clickStroke(Float.parseFloat(o[0]) * screenW, Float.parseFloat(o[1]) * screenH, 80));
                if (g.ok) return "ocr_center";
            }
        } catch (Throwable t) { }
        return "";
    }

    /** A1：一次调用完成「写剪贴板→找聚焦输入框→粘贴→读回校验→尽量还原剪贴板」。
     *  粘贴通道按序试：① 无障碍 ACTION_PASTE ② 长按输入框→点「粘贴」菜单 ③ KEYCODE_PASTE(279)。
     *  聚焦判定：优先 focused 且 editable 的节点；没有就用传入 x/y 落点；都没有＝回人话、不乱贴。 */
    private String pasteText(Map<String, String> kv) {
        ensureScreen();
        String text = kv.get("text");
        if (text == null) text = "";
        if (text.length() == 0) return "{\"ok\":0,\"err\":\"" + esc(Words.RP_PASTE_NEEDTEXT) + "\"}";
        String bad = pkgGuard(kv.containsKey("expect_pkg") ? kv.get("expect_pkg") : "");
        if (bad.length() > 0) return "{\"ok\":0,\"reason\":\"pkg_mismatch\",\"err\":\"" + esc(bad) + "\"}";
        boolean restore = !"0".equals(kv.get("restore"));
        long dur = parseInt(kv.get("duration"), 80);
        boolean tapped = false;
        float tx = 0, ty = 0;
        long tapMs = -1;
        if (kv.containsKey("x") && kv.containsKey("y")) {
            tx = normX(kv.get("x"), screenW);
            ty = normY(kv.get("y"), screenH);
            long t1 = SystemClock.uptimeMillis();
            try { runGesture(clickStroke(tx, ty, dur)); } catch (Throwable t) { }
            tapped = true;
            tapMs = SystemClock.uptimeMillis() - t1;
            sleepQuiet(400);
        }
        AccessibilityNodeInfo focus = null;
        int focusKind = 0;   // 1＝focused+editable 2＝仅 editable
        try {
            AccessibilityNodeInfo f0 = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (isEditableNode(f0)) { focus = f0; focusKind = 1; }
        } catch (Throwable t) { }
        if (focus == null) {
            AccessibilityNodeInfo ed = findEditable(rootNow(), 0);
            if (ed != null) { focus = ed; focusKind = 2; }
        }
        if (focus == null && !tapped)
            return "{\"ok\":0,\"err\":\"" + esc(Words.RP_PASTE_NOFOCUS) + "\"}";
        float lx = tx, ly = ty;
        if (!tapped && focus != null) {
            try {
                android.graphics.Rect rc = new android.graphics.Rect();
                focus.getBoundsInScreen(rc);
                if (rc.width() > 0 && rc.height() > 0) { lx = rc.centerX(); ly = rc.centerY(); }
            } catch (Throwable t) { }
        }
        if (lx <= 0 && ly <= 0) { lx = screenW * 0.5f; ly = screenH * 0.88f; }
        String savedClip = null;
        boolean clipHad = false;
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                android.content.ClipData cd = cm.getPrimaryClip();
                if (cd != null && cd.getItemCount() > 0) {
                    CharSequence cs = cd.getItemAt(0).coerceToText(this);
                    if (cs != null) { savedClip = cs.toString(); clipHad = true; }
                }
            }
        } catch (Throwable t) { }
        int clipSet = 0;
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) { cm.setPrimaryClip(android.content.ClipData.newPlainText("reticle-paste", text)); clipSet = 1; }
        } catch (Throwable t) { }
        if (clipSet == 0) return "{\"ok\":0,\"err\":\"" + esc(Words.RP_PASTE_NOCLIP) + "\"}";
        String beforeSig = sceneSig();
        int ch1 = 0, ch2 = 0, ch3 = 0;
        String via = "";
        int verified = 0;
        if (focus != null) {
            try { ch1 = focus.performAction(AccessibilityNodeInfo.ACTION_PASTE) ? 1 : 0; } catch (Throwable t) { }
            if (ch1 == 1) {

                for (int vk = 0; vk < 5; vk++) { sleepQuiet(280); verified = verifyPaste(text, focus, beforeSig); if (verified > 0) break; }
                if (verified > 0) via = "action_paste";
            }
        }
        if (verified == 0 && focus != null) {
            try { runGesture(clickStroke(lx, ly, 700)); } catch (Throwable t) { }
            sleepQuiet(650);
            String m = tapPasteMenuItem();
            if (m.length() > 0) {
                ch2 = 1;

                for (int vk = 0; vk < 5; vk++) { sleepQuiet(280); verified = verifyPaste(text, focus, beforeSig); if (verified > 0) break; }
                if (verified > 0) via = "menu_paste_" + m;
            } else {
                try { actGlobalBack(); } catch (Throwable t) { }
                sleepQuiet(220);
            }
        }
        if (verified == 0) {
            try {
                String r3 = shxRun("input keyevent 279");
                if (r3 != null && r3.indexOf("\"ok\":1") >= 0) {
                    ch3 = 1;

                for (int vk = 0; vk < 5; vk++) { sleepQuiet(280); verified = verifyPaste(text, focus, beforeSig); if (verified > 0) break; }
                    if (verified > 0) via = "keycode_paste_279";
                }
            } catch (Throwable t) { }
        }
        int clipRestored = 0;
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) {
                if (clipHad && savedClip != null) {
                    if (restore) {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("reticle-restore", savedClip));
                        clipRestored = 1;
                    } else {
                        clipRestored = 4;
                    }
                } else {
                    clipRestored = 3;   // 读不到原剪贴板（系统限制）：不乱动
                }
            }
        } catch (Throwable t) { }
        waitStableQuiet(240, 900);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"ok\":").append(verified > 0 ? 1 : 0)
          .append(",\"via\":\"").append(via).append("\"")
          .append(",\"verified\":").append(verified)
          .append(",\"focus\":").append(focusKind)
          .append(",\"tap_ms\":").append(tapMs)
          .append(",\"tries\":{\"action_paste\":").append(ch1).append(",\"menu\":").append(ch2).append(",\"keycode279\":").append(ch3).append("}")
          .append(",\"clip_set\":").append(clipSet)
          .append(",\"clip_restored\":").append(clipRestored)
          .append(",\"split\":").append(splitFlag());
        if (verified == 0) sb.append(",\"err\":\"").append(esc(Words.RP_PASTE_FAIL)).append("\"");
        sb.append("}");
        return sb.toString();
    }
    // ═══════════ 44.5 B：scroll_to_text（滚找目标：树路优先、认字兜底；到头/超时都如实回） ═══════════
    private AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 24) return null;
        try { if (n.isScrollable() && n.isVisibleToUser()) return n; } catch (Throwable t) { }
        int cn = 0;
        try { cn = n.getChildCount(); } catch (Throwable t) { }
        for (int i = 0; i < cn; i++) {
            AccessibilityNodeInfo ch = null;
            try { ch = n.getChild(i); } catch (Throwable t) { }
            AccessibilityNodeInfo r = findScrollable(ch, depth + 1);
            if (r != null) return r;
        }
        return null;
    }

    /** 滚一屏：树上有可滚容器就用它的 ACTION_SCROLL_FORWARD/BACKWARD（1）；没有就屏幕中段手势滑（2）；失败 0。 */
    private int scrollOnce(String dir) {
        boolean down = !(dir.equals("up") || dir.equals("上"));
        AccessibilityNodeInfo sc = findScrollable(rootNow(), 0);
        if (sc != null) {
            try {
                if (sc.performAction(down ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return 1;
            } catch (Throwable t) { }
        }
        float py1 = down ? 0.68f : 0.32f;
        float py2 = down ? 0.28f : 0.72f;
        GRes g = runGesture(swipeStroke(0.5f * screenW, py1 * screenH, 0.5f * screenW, py2 * screenH, 320));
        return g.ok ? 2 : 0;
    }

    private void countWalk(AccessibilityNodeInfo n, int depth, int[] cnt) {
        if (n == null || depth > 24 || cnt[0] > 400) return;
        cnt[0]++;
        int cn = 0;
        try { cn = n.getChildCount(); } catch (Throwable t) { }
        for (int i = 0; i < cn; i++) {
            AccessibilityNodeInfo ch = null;
            try { ch = n.getChild(i); } catch (Throwable t) { }
            countWalk(ch, depth + 1, cnt);
        }
    }

    private int treeNodeCount() {
        AccessibilityNodeInfo r = rootNow();
        if (r == null) return 0;
        int[] cnt = new int[]{0};
        try { countWalk(r, 0, cnt); } catch (Throwable t) { }
        return cnt[0];
    }

    /** B1：滚着找目标。树路＝在可滚容器上滚一屏→重新 dump；认字路＝截屏认字→未命中滑一屏。
     *  到头判定＝滚动后画面签名不变（按内容比，不按行号）。命中后目标须落在屏幕中段才交给 tap_text 点。 */
    private String scrollToText(Map<String, String> kv) {
        ensureScreen();
        String text = kv.containsKey("text") ? kv.get("text") : "";
        String desc = kv.containsKey("desc") ? kv.get("desc") : "";
        String id = kv.containsKey("id") ? kv.get("id") : "";
        if (text.trim().length() == 0 && desc.trim().length() == 0 && id.trim().length() == 0)
            return "{\"ok\":0,\"err\":\"" + esc(Words.RP_TT_NEED) + "\"}";
        boolean exact = "1".equals(kv.get("exact"));
        int index = parseInt(kv.get("index"), 0);
        String dir = kv.containsKey("direction") ? kv.get("direction").trim() : "down";
        int maxScroll = parseInt(kv.get("max_scroll"), 10);
        if (maxScroll < 1) maxScroll = 1;
        if (maxScroll > 40) maxScroll = 40;
        long timeout = parseInt(kv.get("timeout_ms"), 12000);
        boolean allowOcr = !"0".equals(kv.get("ocr"));
        boolean doTap = "1".equals(kv.get("tap"));
        String bad = pkgGuard(kv.containsKey("expect_pkg") ? kv.get("expect_pkg") : "");
        if (bad.length() > 0) return "{\"ok\":0,\"reason\":\"pkg_mismatch\",\"err\":\"" + esc(bad) + "\"}";
        long t0 = SystemClock.uptimeMillis();
        int screens = 0;
        int edgeHit = 0;
        int scrollKind = 0;
        int treeSeen = 0;
        String end = "";
        String via = "";
        String target = "null";
        String hitText = "";
        List<AccessibilityNodeInfo> hit = null;
        String[] ocrHit = null;
        while (true) {
            List<AccessibilityNodeInfo> found = findNodes(text, exact, desc, id, 400);
            int tn = treeNodeCount();
            if (tn > 0) treeSeen = 1;
            if (found.size() > index) { hit = found; via = "tree"; end = "found"; break; }
            if (allowOcr && text.trim().length() > 0) {
                String[] o = ocrFind(text.trim(), exact);
                if (o != null) {
                    double ocy = 0;
                    try { ocy = Double.parseDouble(o[1]); } catch (Throwable t) { }
                    if (ocy > 0.06 && ocy < 0.90) { ocrHit = o; via = "ocr"; end = "found"; break; }
                    edgeHit = 1;
                }
            }
            if (screens >= maxScroll) { end = "max_scroll"; break; }
            if (SystemClock.uptimeMillis() - t0 >= timeout) { end = "timeout"; break; }
            String sigBefore = sceneSig();
            int sc = scrollOnce(dir);
            if (sc == 0) { end = "scroll_fail"; break; }
            scrollKind = sc;
            sleepQuiet(340);
            waitStableQuiet(220, 800);
            String sigAfter = sceneSig();
            if (sigAfter.equals(sigBefore)) { end = "end_of_list"; break; }
            screens++;
        }
        if (end.equals("found")) {
            StringBuilder tinfo = new StringBuilder();
            if (via.equals("tree")) {
                AccessibilityNodeInfo tgt = hit.get(index);
                target = nodeBrief(tgt);
                try { CharSequence c = tgt.getText(); if (c != null) hitText = c.toString(); } catch (Throwable t) { }
                if (hitText.length() == 0) {
                    try { CharSequence c = tgt.getContentDescription(); if (c != null) hitText = c.toString(); } catch (Throwable t) { }
                }
            } else {
                hitText = ocrHit[2];
                target = "{\"t\":\"" + esc(clipS(ocrHit[2], 40)) + "\",\"cx\":" + ocrHit[0] + ",\"cy\":" + ocrHit[1] + ",\"via\":\"ocr\"}";
            }
            tinfo.append("{\"ok\":1,\"found\":1,\"via\":\"").append(via).append("\"")
                 .append(",\"screens\":").append(screens)
                 .append(",\"scroll_by\":").append(scrollKind)
                 .append(",\"tree_seen\":").append(treeSeen)
                 .append(",\"target\":").append(target)
                 .append(",\"target_text\":\"").append(esc(clipS(hitText, 60))).append("\"");
            if (doTap) {
                Map<String, String> tk = new HashMap<String, String>();
                if (text.trim().length() > 0) tk.put("text", text);
                if (desc.trim().length() > 0) tk.put("desc", desc);
                if (id.trim().length() > 0) tk.put("id", id);
                if (exact) tk.put("exact", "1");
                tk.put("index", String.valueOf(index));
                if (kv.containsKey("expect")) tk.put("expect", kv.get("expect"));
                if (kv.containsKey("expect_pkg")) tk.put("expect_pkg", kv.get("expect_pkg"));
                tk.put("wait_ms", kv.containsKey("wait_ms") ? kv.get("wait_ms") : "1200");
                String tr = tapText(tk);
                tinfo.append(",\"tap\":").append(tr);
                try {
                    if (new org.json.JSONObject(tr).optInt("ok", 0) != 1) {
                        // ★77.6：内嵌的 tap（含 expect 后置条件）没成 ⇒ 顶层也算没做成（不再"找到了"就算 ok）
                        int ip = tinfo.indexOf("\"ok\":1");
                        if (ip >= 0) tinfo.replace(ip, ip + 7, "\"ok\":0");
                        tinfo.append(",\"reason\":\"tap_failed\",\"err\":\"").append(esc(Words.RP_SCROLLTO_TAPFAIL)).append("\"");
                    }
                } catch (Throwable tt) { }
            }
            tinfo.append(",\"ms\":").append(SystemClock.uptimeMillis() - t0).append("}");
            return tinfo.toString();
        }
        String why = Words.RP_SCROLLTO_END;
        if (end.equals("timeout")) why = String.format(Words.RP_SCROLLTO_TIMEOUT, timeout);
        else if (end.equals("max_scroll")) why = String.format(Words.RP_SCROLLTO_MAXSCROLL, maxScroll);
        else if (end.equals("scroll_fail")) why = Words.RP_SCROLLTO_NOSCROLL;
        StringBuilder sb = new StringBuilder();
        sb.append("{\"ok\":0,\"reason\":\"not_found\",\"found\":0,\"err\":\"").append(esc(String.format(Words.RP_SCROLLTO_NOTFOUND, screens, describeQuery(text, desc, id), why))).append("\"")
          .append(",\"screens\":").append(screens)
          .append(",\"end\":\"").append(end).append("\"")
          .append(",\"tree_seen\":").append(treeSeen)
          .append(",\"edge_hit\":").append(edgeHit)
          .append(",\"ms\":").append(SystemClock.uptimeMillis() - t0)
          .append(",\"tapped\":0}");
        return sb.toString();
    }
    // ═══════════ 44.5 C：do_chain（一次提交多步；每步各带后置条件，不成立就停在那步并报屏幕现状） ═══════════
    /** 只允许"动作类"命令进链（不许从链里跑 shell／notify／build／download，防越权与递归）。 */
    private static final String CHAIN_ALLOW = " tap tap_text input paste longpress swipe key scroll wait_text wait_stable ui ocr shot ";

    private void collectTexts(AccessibilityNodeInfo n, int depth, List<String> out, int max) {
        if (n == null || depth > 24 || out.size() >= max) return;
        try {
            CharSequence c = n.getText();
            if (c == null || c.length() == 0) c = n.getContentDescription();
            if (c != null && c.length() > 0) {
                String s = clipS(c.toString(), 40);
                if (s.trim().length() > 0) out.add(s);
            }
        } catch (Throwable t) { }
        int cn = 0;
        try { cn = n.getChildCount(); } catch (Throwable t) { }
        for (int i = 0; i < cn && out.size() < max; i++) {
            AccessibilityNodeInfo ch = null;
            try { ch = n.getChild(i); } catch (Throwable t) { }
            collectTexts(ch, depth + 1, out, max);
        }
    }

    /** 屏幕现状（停下时回给助手看）：前台包名 + 树上的可见文字（树读不到就整屏认字前若干行）。 */
    private String screenBrief() {
        String pkg = "";
        try { pkg = foregroundPkg(); } catch (Throwable t) { }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"pkg\":\"").append(esc(pkg)).append("\"");
        List<String> ts = new ArrayList<String>();
        AccessibilityNodeInfo r = rootNow();
        if (r != null) collectTexts(r, 0, ts, 12);
        if (ts.size() > 0) {
            sb.append(",\"via\":\"tree\",\"texts\":[");
            for (int i = 0; i < ts.size(); i++) { if (i > 0) sb.append(","); sb.append("\"").append(esc(ts.get(i))).append("\""); }
            sb.append("]");
        } else {
            String via = "none";
            sb.append(",\"via\":\"");
            Shot s = takeShotSync();
            if (s.bmp != null) {
                String js = null;
                int W = s.bmp.getWidth(), H = s.bmp.getHeight();
                try { js = OcrEng.linesJson(s.bmp, 0, 0, W, H); } catch (Throwable t) { }
                try { s.bmp.recycle(); } catch (Throwable t) { }
                via = "ocr";
                sb.append(via).append("\",\"texts\":[");
                try {
                    org.json.JSONArray arr = new org.json.JSONArray(js == null ? "[]" : js);
                    int n = Math.min(arr.length(), 12);
                    for (int i = 0; i < n; i++) {
                        if (i > 0) sb.append(",");
                        sb.append("\"").append(esc(clipS(arr.getJSONObject(i).optString("t", ""), 40))).append("\"");
                    }
                } catch (Throwable t) { }
                sb.append("]");
            } else {
                sb.append("none\",\"texts\":[]");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    /** C1：steps＝JSON 数组，每步 {"act":"tap_text","text":"发送","expect":"已发送","timeout_ms":3000}。
     *  act 之外的字段原样当该动作的参数；expect＝该步的后置条件（等它出现，超时＝停）。
     *  任一步动作失败或 expect 超时 → 立刻停，回「第几步／什么命令／当时屏幕现状」。 */
    private String doChain(Map<String, String> kv) {
        ensureScreen();
        String stepsStr = kv.containsKey("steps") ? kv.get("steps").trim() : "";
        if (stepsStr.length() == 0) return "{\"ok\":0,\"err\":\"" + esc(Words.RP_CHAIN_NEED) + "\"}";
        org.json.JSONArray steps = null;
        try { steps = new org.json.JSONArray(stepsStr); } catch (Throwable t) { steps = null; }
        if (steps == null) return "{\"ok\":0,\"err\":\"" + esc(Words.RP_CHAIN_BAD) + "\"}";
        boolean stopOnErr = !"0".equals(kv.get("stop_on_error"));
        int observe = parseInt(kv.get("observe"), 0);
        org.json.JSONArray detail = new org.json.JSONArray();
        int done = 0;
        int actFail = 0, expFail = 0;   // ★77.6：动作／后置条件失败计数（顶层 ok 如实反映，不再一律 ok:1）
        long t0 = SystemClock.uptimeMillis();
        for (int i = 0; i < steps.length(); i++) {
            org.json.JSONObject st = steps.optJSONObject(i);
            if (st == null) return chainStop(detail, i + 1, "(空步骤)", Words.RP_CHAIN_BAD, done, t0);
            String act = st.optString("act", "").trim();
            if (CHAIN_ALLOW.indexOf(" " + act + " ") < 0)
                return chainStop(detail, i + 1, act.length() == 0 ? "(缺 act)" : act, Words.RP_CHAIN_NOTALLOW, done, t0);
            String expect = st.optString("expect", "");
            long tmo = st.has("timeout_ms") ? st.optLong("timeout_ms", 5000) : 5000;
            Map<String, String> kv2 = new HashMap<String, String>();
            java.util.Iterator<String> it = st.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (k.equals("act") || k.equals("expect") || k.equals("timeout_ms") || k.equals("desc2")) continue;
                Object v = st.opt(k);
                if (v != null) kv2.put(k, String.valueOf(v));
            }
            String cmd = act;
            if (act.equals("key")) {
                String kk = kv2.containsKey("key") ? kv2.get("key").trim() : "";
                if (kk.equals("back") || kk.equals("返回")) cmd = "gback";
                else if (kk.equals("home") || kk.equals("主页")) cmd = "ghome";
                else if (kk.equals("recents") || kk.equals("最近任务")) cmd = "grecents";
                else return chainStop(detail, i + 1, act, Words.RP_KEY_BAD, done, t0);
            }
            kv2.put("cmd", cmd);
            long s0 = SystemClock.uptimeMillis();
            String res = "";
            try { res = dispatch(kv2); } catch (Throwable t) { res = "{\"ok\":0,\"err\":\"" + esc(String.valueOf(t)) + "\"}"; }
            long stepMs = SystemClock.uptimeMillis() - s0;
            int actOk = 0;
            try { actOk = new org.json.JSONObject(res).optInt("ok", 0); } catch (Throwable t) { }
            int expOk = 1;
            long expMs = 0;
            if (expect.length() > 0) {
                long e0 = SystemClock.uptimeMillis();
                expOk = 0;
                while (true) {
                    if (seenText(expect)) { expOk = 1; break; }
                    if (SystemClock.uptimeMillis() - e0 >= tmo) break;
                    sleepQuiet(180);
                }
                expMs = SystemClock.uptimeMillis() - e0;
            }
            org.json.JSONObject d = new org.json.JSONObject();
            try {
                d.put("i", i + 1);
                d.put("act", act);
                d.put("ms", stepMs);
                d.put("ok", actOk);
                d.put("expect_hit", expOk);
                d.put("expect_ms", expMs);
                d.put("res", clipS(res.replace("\n", " "), 200));
                d.put("reason", actOk != 1 ? "act_failed" : (expOk != 1 ? "expect_timeout" : ""));
            } catch (Throwable t) { }
            detail.put(d);
            done = i + 1;
            if (actOk != 1) actFail++;
            if (expOk != 1) expFail++;
            if (actOk != 1 || expOk != 1) {
                String why = actOk != 1 ? String.format(Words.RP_CHAIN_ACTFAIL, i + 1, act)
                                        : String.format(Words.RP_CHAIN_EXPFAIL, i + 1, expect, tmo);
                if (stopOnErr) return chainStop(detail, i + 1, act, why, i, t0);
            }
        }
        boolean anyFail = (actFail > 0 || expFail > 0);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"ok\":").append(anyFail ? 0 : 1).append(",\"steps_done\":").append(done).append(",\"total\":").append(steps.length())
          .append(",\"ms\":").append(SystemClock.uptimeMillis() - t0)
          .append(",\"detail\":").append(detail.toString());
        if (anyFail) sb.append(",\"reason\":\"").append(expFail > 0 && actFail == 0 ? "expect_timeout" : "act_failed").append("\",\"failed_steps\":").append(actFail + expFail);
        if (observe > 0) sb.append(",\"screen\":").append(screenBrief());
        sb.append("}");
        return sb.toString();
    }

    private String chainStop(org.json.JSONArray detail, int idx, String act, String why, int done, long t0) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"ok\":0,\"steps_done\":").append(done)
          .append(",\"stop_at\":").append(idx)
          .append(",\"stop_act\":\"").append(esc(clipS(act, 40))).append("\"")
          .append(",\"why\":\"").append(esc(why)).append("\"")
          .append(",\"detail\":").append(detail.toString())
          .append(",\"screen\":").append(screenBrief())
          .append(",\"ms\":").append(SystemClock.uptimeMillis() - t0)
          .append("}");
        return sb.toString();
    }

    // ═══════════ 44.5 D：窗口与分屏探针（windows／splitFlag） ═══════════
    /** 分屏/多窗口快速判定：可见窗口 >1 且至少一个不是整屏 → 1。读不到就 0（不猜）。 */
    int splitFlag() {
        // 44.5 实测口径：小窗／分屏／卡片式多任务（原子工作台）＝「焦点应用窗口不是整屏」。
        // 口径来源：实测三种环境下 getBoundsInScreen 都是整屏坐标系、按像素点零偏移；
        // 该标记只做"提醒"用（提示助手：ui 只给焦点窗口，另一个窗口要先点一下或用 tap_text 认字兜底）。
        try {
            java.util.List<android.view.accessibility.AccessibilityWindowInfo> ws = getWindows();
            if (ws == null) return 0;
            for (int wi = 0; wi < ws.size(); wi++) {
                android.view.accessibility.AccessibilityWindowInfo w = ws.get(wi);
                if (w == null) continue;
                int wt = -1;
                try { wt = w.getType(); } catch (Throwable t) { }
                if (wt != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                boolean foc = false;
                try { foc = w.isFocused(); } catch (Throwable t) { }
                if (!foc) continue;
                android.graphics.Rect rc = new android.graphics.Rect();
                try { w.getBoundsInScreen(rc); } catch (Throwable t) { return 0; }
                boolean full = (rc.left <= 0 && rc.top <= 0 && rc.right >= screenW && rc.bottom >= screenH);
                return full ? 0 : 1;
            }
            return 0;
        } catch (Throwable t) { return 0; }
    }

    /** 列出当前无障碍可见的窗口（探针：分屏／小窗／悬浮窗排查用）。 */
    private String windowsCmd(Map<String, String> kv) {
        ensureScreen();
        org.json.JSONArray arr = new org.json.JSONArray();
        int total = 0, vis = 0;
        try {
            java.util.List<android.view.accessibility.AccessibilityWindowInfo> ws = getWindows();
            if (ws != null) {
                total = ws.size();
                for (int i = 0; i < ws.size(); i++) {
                    android.view.accessibility.AccessibilityWindowInfo w = ws.get(i);
                    if (w == null) continue;
                    boolean v = false, act = false, foc = false;
                    int type = -1, layer = -1;
                    try { v = (w.getRoot() != null); } catch (Throwable t) { }
                    try { act = w.isActive(); } catch (Throwable t) { }
                    try { foc = w.isFocused(); } catch (Throwable t) { }
                    try { type = w.getType(); } catch (Throwable t) { }
                    try { layer = w.getLayer(); } catch (Throwable t) { }
                    if (v) vis++;
                    android.graphics.Rect rc = new android.graphics.Rect();
                    try { w.getBoundsInScreen(rc); } catch (Throwable t) { }
                    String pk = "";
                    try {
                        AccessibilityNodeInfo r = w.getRoot();
                        if (r != null) { CharSequence c = r.getPackageName(); if (c != null) pk = c.toString(); }
                    } catch (Throwable t) { }
                    boolean full = (rc.left <= 0 && rc.top <= 0 && rc.right >= screenW && rc.bottom >= screenH);
                    org.json.JSONObject o = new org.json.JSONObject();
                    try {
                        o.put("i", i);
                        o.put("type", type);
                        o.put("app", type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION ? 1 : 0);
                        o.put("layer", layer);
                        o.put("pkg", pk);
                        o.put("b", rc.left + "," + rc.top + "," + rc.right + "," + rc.bottom);
                        o.put("visible", v ? 1 : 0);
                        o.put("active", act ? 1 : 0);
                        o.put("focused", foc ? 1 : 0);
                        o.put("full", full ? 1 : 0);
                    } catch (Throwable t) { }
                    arr.put(o);
                }
            }
        } catch (Throwable t) { }
        return "{\"ok\":1,\"screen\":[" + screenW + "," + screenH + "],\"windows_total\":" + total
                + ",\"visible\":" + vis + ",\"split\":" + splitFlag() + ",\"list\":" + arr.toString() + "}";
    }
    private String tap(Map<String, String> kv) {
        ensureScreen();
        String bad = pkgGuard(kv.containsKey("expect_pkg") ? kv.get("expect_pkg") : "");
        if (bad.length() > 0) return "{\"ok\":0,\"reason\":\"pkg_mismatch\",\"err\":\"" + esc(bad) + "\"}";
        float x = normX(kv.get("x"), screenW);
        float y = normY(kv.get("y"), screenH);
        long dur = parseInt(kv.get("duration"), parseInt(kv.get("ms"), 80));   // S4：默认 60 → 80ms
        String expect = kv.containsKey("expect") ? kv.get("expect") : "";
        String beforePkg = "";
        try { beforePkg = foregroundPkg(); } catch (Throwable t) { }
        String beforeSig = sceneSig();
        GRes g = runGesture(clickStroke(x, y, dur));
        String verdict = waitVerdict(beforePkg, beforeSig, expect, parseInt(kv.get("wait_ms"), 1200));
        boolean needExp = expect.length() > 0;
        boolean expOk = verdict.indexOf("\"expect_hit\":1") >= 0;
        boolean okAll = g.ok && (!needExp || expOk);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"ok\":").append(okAll ? 1 : 0)
          .append(",\"dispatch_ms\":").append(g.ms)
          .append(",\"at\":[").append((int) x).append(",").append((int) y).append("]")
          .append(",\"expect\":\"").append(esc(expect)).append("\"")
          .append(verdict);
        sb.append(",\"split\":").append(splitFlag());
        if (!g.ok) sb.append(",\"err\":").append(g.err);
        else if (!expOk) sb.append(",\"reason\":\"expect_timeout\",\"err\":\"").append(esc(String.format(Words.RP_TT_EXPECT_FAIL, expect))).append("\"");
        sb.append("}");
        return sb.toString();
    }

    // ---- S1 + 读回校验：输入（SET_TEXT 不听使唤时走剪贴板，用完还原用户剪贴板）----
    /** 49 号单 C：重新取一次「当前焦点输入框」节点——无障碍节点是快照，SET_TEXT 之前拿到的那份
     *  getText() 不会跟着刷新（实测读回会滞后一拍），轮询读回必须用新取的节点。 */
    private AccessibilityNodeInfo focusNow() {
        try {
            AccessibilityNodeInfo f = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (isEditableNode(f)) return f;
        } catch (Throwable t) { }
        try { return findEditable(rootNow(), 0); } catch (Throwable t) { return null; }
    }

    /** 读回当前输入框文本：优先重新取节点（防快照滞后），取不到就退回传入的旧节点。 */
    private String readbackNow(AccessibilityNodeInfo fallback) {
        AccessibilityNodeInfo n = focusNow();
        return readTextOf(n != null ? n : fallback);
    }

    private String readTextOf(AccessibilityNodeInfo n) {
        try { CharSequence cs = n.getText(); if (cs != null) return cs.toString(); } catch (Throwable t) { }
        return "";
    }

    private boolean readbackOk(String readback, String want) {
        if (readback == null || want == null) return false;
        String a = readback.trim(), b = want.trim();
        if (b.length() == 0) return a.length() == 0;
        if (a.length() == 0) return false;
        return a.contains(b) || b.contains(a);
    }

    private String inputTextV2(Map<String, String> kv) {
        String text = kv.get("text");
        if (text == null) text = "";
        AccessibilityNodeInfo focus = null;
        try { focus = findFocus(AccessibilityNodeInfo.FOCUS_INPUT); } catch (Throwable t) { }
        if (focus == null) focus = findEditable(rootNow(), 0);
        if (focus == null) return "{\"ok\":0,\"err\":\"" + Words.RP_INPUT_NOFOCUS + "\"}";
        String savedClip = null;
        boolean clipHad = false;
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                android.content.ClipData cd = cm.getPrimaryClip();
                if (cd != null && cd.getItemCount() > 0) {
                    CharSequence cs = cd.getItemAt(0).coerceToText(this);
                    if (cs != null) { savedClip = cs.toString(); clipHad = true; }
                }
            }
        } catch (Throwable t) { }
        boolean ok = false;
        String via = "set_text";
        try {
            Bundle b = new Bundle();
            b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            ok = focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
        } catch (Throwable t) { }
        sleepQuiet(220);
        String readback = readTextOf(focus);
        boolean verified = readbackOk(readback, text);
        if (!verified) {
            // 44 单实测：微信里 SET_TEXT 返回 true 但一个字没进去 → 读回校验，未生效改走剪贴板+粘贴
            try {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("reticle-input", text));
                    shxRun("input keyevent 279");
                    via = "clipboard_paste";
                    sleepQuiet(450);
                    readback = readTextOf(focus);
                    verified = readbackOk(readback, text);
                    ok = verified;
                }
            } catch (Throwable t) { }
        }
        int clipRestored = 0;
        try {
            if (via.equals("clipboard_paste")) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) {
                    if (clipHad && savedClip != null) {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("reticle-restore", savedClip));
                        clipRestored = 1;
                    } else {
                        clipRestored = 3;   // 读不到原剪贴板（系统限制）：不乱动
                    }
                }
            }
        } catch (Throwable t) { }
        long imeMs = waitStableQuiet(300, 1200);   // S3：输入完自动等键盘/界面稳定
        if (!ok || !verified)
            return "{\"ok\":0,\"via\":\"" + via + "\",\"readback\":\"" + esc(clipS(readback, 60))
                    + "\",\"clip_restored\":" + clipRestored + ",\"ime_wait_ms\":" + imeMs
                    + ",\"err\":\"" + Words.RP_INPUT_FAIL + "\"}";
        return "{\"ok\":1,\"via\":\"" + via + "\",\"readback\":\"" + esc(clipS(readback, 60))
                + "\",\"verified\":1,\"clip_restored\":" + clipRestored + ",\"ime_wait_ms\":" + imeMs + "}";
    }

    // ---- S5：ui 节点补 scrollable（scr）与"最近可点祖先"序号（ck），让助手一眼看出该点谁 ----
    private void walkUi(AccessibilityNodeInfo n, int depth, int maxDepth, int maxNodes, StringBuilder sb, int[] cnt) {
        walkUiFull(n, depth, maxDepth, maxNodes, sb, cnt, -1);
    }

    private void walkUiFull(AccessibilityNodeInfo n, int depth, int maxDepth, int maxNodes, StringBuilder sb, int[] cnt, int clickableIdx) {
        if (n == null || cnt[0] >= maxNodes || depth > maxDepth) return;
        String tx = "", dc = "", vid = "", cls = "";
        try { CharSequence c1 = n.getText(); if (c1 != null) tx = c1.toString(); } catch (Throwable e) { }
        try { CharSequence c2 = n.getContentDescription(); if (c2 != null) dc = c2.toString(); } catch (Throwable e) { }
        try { String v = n.getViewIdResourceName(); if (v != null) vid = v; } catch (Throwable e) { }
        try { CharSequence c3 = n.getClassName(); if (c3 != null) cls = c3.toString(); } catch (Throwable e) { }
        boolean clk = false, scr = false;
        try { clk = n.isClickable(); } catch (Throwable e) { }
        try { scr = n.isScrollable(); } catch (Throwable e) { }
        int myIdx = -1;
        if (tx.length() > 0 || dc.length() > 0 || vid.length() > 0 || clk) {
            int L = 0, T = 0, R = 0, Bd = 0;
            try { Rect rc = new Rect(); n.getBoundsInScreen(rc); L = rc.left; T = rc.top; R = rc.right; Bd = rc.bottom; } catch (Throwable e) { }
            if (cnt[0] > 0) sb.append(",");
            sb.append("{\"t\":\"").append(esc(clipS(tx, 100)))
              .append("\",\"d\":\"").append(esc(clipS(dc, 80)))
              .append("\",\"id\":\"").append(esc(shortId(vid)))
              .append("\",\"c\":\"").append(esc(shortCls(cls)))
              .append("\",\"b\":[").append(L).append(",").append(T).append(",").append(R).append(",").append(Bd)
              .append("],\"clk\":").append(clk ? 1 : 0)
              .append(",\"scr\":").append(scr ? 1 : 0)
              .append(",\"ck\":").append(clickableIdx)
              .append("}");
            myIdx = cnt[0];
            cnt[0]++;
        }
        int childClk = clk ? myIdx : clickableIdx;
        int cn = 0;
        try { cn = n.getChildCount(); } catch (Throwable e) { }
        for (int i = 0; i < cn && cnt[0] < maxNodes; i++) {
            AccessibilityNodeInfo ch = null;
            try { ch = n.getChild(i); } catch (Throwable e) { }
            if (ch != null) walkUiFull(ch, depth + 1, maxDepth, maxNodes, sb, cnt, childClk);
        }
    }


    /** 场景签名：控件树能用就用树；树读不到（微信/游戏等自绘界面）就用截图像素采样兜底。 */
    private String sceneSig() {
        String ts = treeSig();
        if (ts.length() > 0) return ts;
        Shot s = takeShotSync();
        if (s.bmp == null) return ts;
        StringBuilder sb = new StringBuilder();
        int W = s.bmp.getWidth(), H = s.bmp.getHeight();
        int sx = Math.max(1, W / 24), sy = Math.max(1, H / 24);
        for (int y = sy; y < H; y += sy) {
            for (int x = sx; x < W; x += sx) {
                try { sb.append(Integer.toHexString(s.bmp.getPixel(x, y) & 0xFFFFFF)); } catch (Throwable t) { }
            }
        }
        try { s.bmp.recycle(); } catch (Throwable t) { }
        return "img" + Integer.toHexString(sb.toString().hashCode()) + "/" + sb.length();
    }

    // ---- S1 + 读回校验（v2）：输入。可先点一下目标框（x/y）；SET_TEXT 不听使唤就走剪贴板+粘贴，用完还原用户剪贴板。
    private String inputText(Map<String, String> kv) {
        String text = kv.get("text");
        if (text == null) text = "";
        String bad = pkgGuard(kv.containsKey("expect_pkg") ? kv.get("expect_pkg") : "");
        if (bad.length() > 0) return "{\"ok\":0,\"reason\":\"pkg_mismatch\",\"err\":\"" + esc(bad) + "\"}";
        long dur = parseInt(kv.get("duration"), 80);
        long tapMs = -1;
        if (kv.containsKey("x") && kv.containsKey("y")) {
            float px = normX(kv.get("x"), screenW), py = normY(kv.get("y"), screenH);
            long t1 = SystemClock.uptimeMillis();
            try { runGesture(clickStroke(px, py, dur)); } catch (Throwable t) { }
            tapMs = SystemClock.uptimeMillis() - t1;
            sleepQuiet(380);
        }
        AccessibilityNodeInfo focus = null;
        try { focus = findFocus(AccessibilityNodeInfo.FOCUS_INPUT); } catch (Throwable t) { }
        if (focus == null) focus = findEditable(rootNow(), 0);
        String savedClip = null;
        boolean clipHad = false;
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                android.content.ClipData cd = cm.getPrimaryClip();
                if (cd != null && cd.getItemCount() > 0) {
                    CharSequence cs = cd.getItemAt(0).coerceToText(this);
                    if (cs != null) { savedClip = cs.toString(); clipHad = true; }
                }
            }
        } catch (Throwable t) { }
        String via = "";
        String readback = "";
        boolean verified = false;
        // 49 号单 C：两条通道都改成「轮询校验再决定是否换通道」——原先 SET_TEXT 只等 220ms 就判失败，
        // 而它其实已经写进去了（输入法异步／组合文本），于是又贴一遍 → 内容翻倍。现在：
        //   ① SET_TEXT 后轮询最多 5×280ms（≈1.4s）才判没生效；② 未验到才换剪贴板通道，
        //      换之前先用「读回／认字／画面」确认它确实没生效（防叠加）；③ 写入前记录原文本，已含目标文本＝成功、不再写。
        final int POLL_N = 5;
        final long POLL_MS = 280;
        if (focus != null) {
            String before = readbackNow(focus);                 // 写入前原文本
            // ★精确比较（不是"包含"）：输入框里是 "BBBBAuthorization"、目标 "Authorization" 只是它的子串，
            //   那种情况必须照常改写；用包含判定会误报"已输入"、其实一个字没改。
            if (before != null && before.trim().equals(text.trim())) {   // 内容已经就是目标文本 → 不重复写
                readback = before;
                verified = true;
                via = "set_text_already";
            } else {
                boolean ok = false;
                try {
                    Bundle b = new Bundle();
                    b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                    ok = focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
                } catch (Throwable t) { }
                String beforeSig = sceneSig();
                for (int vk = 0; vk < POLL_N && !verified; vk++) {
                    sleepQuiet(POLL_MS);
                    readback = readbackNow(focus);
                    verified = readbackOk(readback, text);      // 已含目标文本即算成功（防叠加）
                }
                if (verified) {
                    via = "set_text";
                } else {
                    // SET_TEXT 也可能已生效、只是读不回来（组合文本）→ 认字/画面再确认一次；确认没生效才换通道
                    boolean seen = false;
                    String probe = text.length() > 8 ? text.substring(0, 8) : text;
                    try { seen = probe.length() > 0 && ocrFind(probe, false) != null; } catch (Throwable t) { }
                    if (!seen) { try { seen = beforeSig.length() > 0 && !sceneSig().equals(beforeSig); } catch (Throwable t) { } }
                    if (seen) {
                        readback = readbackNow(focus);
                        verified = true;
                        via = "set_text_late";
                    } else {
                        via = ok ? "set_text_nobite" : "set_text_fail";
                    }
                }
            }
        }
        int clipPasted = 0;
        if (!verified) {
            String beforeSig2 = sceneSig();
            try {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("reticle-input", text));
                    shxRun("input keyevent 279");
                    clipPasted = 1;
                    via = "clipboard_paste";
                    for (int vk = 0; vk < POLL_N && !verified; vk++) {     // 剪贴板通道同样轮询（原来只等 480ms 一次）
                        sleepQuiet(POLL_MS);
                        if (focus != null) {
                            readback = readbackNow(focus);
                            verified = readbackOk(readback, text);
                        }
                    }
                    if (!verified) {
                        String afterSig = sceneSig();
                        if (!afterSig.equals(beforeSig2)) { verified = true; via = "clipboard_paste_scene"; }
                        else {
                            String probe = text.length() > 8 ? text.substring(0, 8) : text;
                            if (probe.length() > 0 && ocrFind(probe, false) != null) { verified = true; via = "clipboard_paste_ocr"; }
                        }
                    }
                }
            } catch (Throwable t) { }
        }
        int clipRestored = 0;
        try {
            if (clipPasted == 1) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) {
                    if (clipHad && savedClip != null) {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("reticle-restore", savedClip));
                        clipRestored = 1;
                    } else {
                        clipRestored = 3;   // 读不到原剪贴板（系统限制）：不乱动
                    }
                }
            }
        } catch (Throwable t) { }
        long imeMs = waitStableQuiet(300, 1200);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"ok\":").append(verified ? 1 : 0)
          .append(",\"via\":\"").append(via).append("\"")
          .append(",\"readback\":\"").append(esc(clipS(readback, 60))).append("\"")
          .append(",\"verified\":").append(verified ? 1 : 0)
          .append(",\"tap_ms\":").append(tapMs)
          .append(",\"clip_restored\":").append(clipRestored)
          .append(",\"ime_wait_ms\":").append(imeMs)
          .append(",\"len\":").append(text.length());
        if (!verified) sb.append(",\"err\":\"").append(Words.RP_INPUT_FAIL).append("\"");
        sb.append("}");
        return sb.toString();
    }

    /** 截图认字兜底：整屏认字，找包含 needle 的行 → {cx, cy, 文本}；找不到返回 null。
     *  行里常有几段话拼一起（如"输入框内容…发送"）→ 按字样在行内的位置把点校正过去，避免只点行中心。 */
    private String[] ocrFind(String needle, boolean exact) {
        if (needle == null || needle.length() == 0) return null;
        Shot s = takeShotSync();
        if (s.bmp == null) return null;
        String js = null;
        int W = s.bmp.getWidth(), H = s.bmp.getHeight();
        try { js = OcrEng.linesJson(s.bmp, 0, 0, W, H); } catch (Throwable t) { }
        try { s.bmp.recycle(); } catch (Throwable t) { }
        if (js == null || W <= 0 || H <= 0) return null;
        try {
            org.json.JSONArray arr = new org.json.JSONArray(js);
            for (int pass = exact ? 0 : 1; pass < 2; pass++) {
                String bestT = null;
                double bestCx = 0, bestCy = 0;
                int bestLen = -1;
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject o = arr.getJSONObject(i);
                    String t = o.optString("t", "");
                    if (t.length() == 0) continue;
                    boolean m = (pass == 0) ? t.equals(needle) : t.contains(needle);
                    if (!m) continue;
                    int len = t.length();
                    if (bestT == null || len < bestLen) {
                        bestT = t;
                        bestLen = len;
                        bestCx = o.optDouble("cx", 0);
                        bestCy = o.optDouble("cy", 0);
                        int hit = t.indexOf(needle);
                        org.json.JSONArray bx = o.optJSONArray("box");
                        if (hit >= 0 && bx != null && bx.length() >= 4) {
                            double x0 = bx.optDouble(0, 0), w0 = bx.optDouble(2, 0);
                            double frac = (hit + needle.length() / 2.0) / t.length();
                            if (w0 > 0) bestCx = (x0 + w0 * frac) / W;
                        }
                    }
                }
                if (bestT != null) return new String[]{String.valueOf(bestCx), String.valueOf(bestCy), bestT};
            }
        } catch (Throwable t) { }
        return null;
    }
    /** 86.6：用户划掉卡片＝把手交还系统。
     *  disableSelf() 让系统把我们这条从 enabled_accessibility_services 里摘掉并解绑；只 stopSelf
     *  而把无障碍留在系统里，系统为「绑无障碍」会把进程一遍遍拉回来（本单那场拉锯的根）。
     *  ★只摘自己，不碰副包、不碰别人的无障碍。 */
    public static void handBackA11y() {
        VeriService s = INSTANCE;
        if (s == null) return;
        try {
            s.disableSelf();
            A11yWatchdog.log(s, "已把无障碍交还系统（disableSelf）");
        } catch (Throwable t) {
            A11yWatchdog.log(s, "交还无障碍失败：" + t);
        }
    }
}
