package app.reticle;

import android.content.Context;
import android.content.SharedPreferences;
import rikka.shizuku.Shizuku;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

/**
 * 一统探路 P2：眼手包内置的最小 MCP 服务（Streamable HTTP，仅本机 127.0.0.1:8770）。
 * 工具：ping（回铃）、shell（以 shell 身份执行命令，走 Shizuku）。
 * 令牌：随机生成，存 App 私有 SharedPreferences，不落 /sdcard。
 * 14 加固：单请求超时（60s，超时＝断开该连接并记一行）；极简看门狗（30s 查 serve 线程，不活拉起）。
 */
public class McpServer {
    /** 51 号单：出厂默认端口（用户可在配置页改；运行时端口一律走 getPort()）。 */
    public static final int PORT_DEFAULT = 8770;
    /** 51 号单：出厂默认请求头名称（用户可在配置页改；运行时走 getHeaderName()）。 */
    public static final String HNAME_DEFAULT = "Authorization";
    private static final String PREFS = "mcp";
    private static final String KEY_TOKEN = "token";
    /** 51 号单：请求头名称／端口的可改值（与令牌同一份 SP）。 */
    private static final String KEY_HNAME = "header_name";
    private static final String KEY_PORT = "port";
    /** 46 号单：已注册工具表（name → tools/list 里那一项）；help 的目录与详情都读它，说明只维护一处。 */
    private static final java.util.LinkedHashMap<String, JSONObject> TOOL_REG = new java.util.LinkedHashMap<String, JSONObject>();
    private static volatile boolean started = false;
    private static volatile boolean running = false;
    private static volatile Thread serveThread = null;
    private static volatile Thread watchdogThread = null;
    /** 28 号单：用户一键停机后，不再自动拉起自己 */
    private static volatile boolean userStopped = false;
    private static volatile ServerSocket ssRef = null;
    /** 14 服务加固 P1：单请求处理上限；超时＝断开该连接并记一行。 */
    private static final long REQ_TIMEOUT_MS = 60000L;
    /** 38 号单：shell 调用超时（秒级）——超时＝叫停进程并明确回 timeout=1。 */
    private static final long SHELL_TIMEOUT_MS = 30000L;
    /** 14 服务加固 P2：看门狗开关（默认开）；false＝与本单之前行为一致。 */
    private static final boolean WATCHDOG_ON = true;
    private static final long WATCHDOG_INTERVAL_MS = 30000L;
    private static final java.util.concurrent.ExecutorService REQ_POOL =
            java.util.concurrent.Executors.newCachedThreadPool();

    /** 28 号单 · 一键停机：关掉本地服务；重新打开 Reticle 就会自己恢复 */
    public static synchronized void stop(Context ctx) {
        userStopped = true;
        try { if (ssRef != null) ssRef.close(); } catch (Throwable t) { }
        ssRef = null;
        running = false;
        started = false;
        serveThread = null;
        mlog(ctx, "用户一键停机：本地服务已停（重开 App 即恢复）");
    }

    public static boolean isRunning() { return running; }

    /** 51 号单：重新生成令牌——新的立刻生效（旧的一律 401）。返回新令牌。 */
    public static synchronized String regenerateToken(Context ctx) {
        byte[] b = new byte[24];
        new java.security.SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) sb.append(String.format("%02x", (b[i] & 0xff)));
        String t = sb.toString();
        try { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TOKEN, t).apply(); } catch (Throwable x) { }
        return t;
    }

    /** 51 号单：当前服务端口（默认 8770，用户可在配置页改）。 */
    public static synchronized int getPort(Context ctx) {
        try {
            int p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_PORT, PORT_DEFAULT);
            if (p < 1024 || p > 65535) return PORT_DEFAULT;
            return p;
        } catch (Throwable t) { return PORT_DEFAULT; }
    }

    /** 51 号单：改端口（只落配置，改完要 restart() 才生效）；不合法／被占用＝回 false。 */
    public static synchronized boolean setPort(Context ctx, int p) {
        if (p < 1024 || p > 65535) return false;
        if (p == getPort(ctx)) return true;
        ServerSocket probe = null;
        try {
            probe = new ServerSocket();
            probe.setReuseAddress(true);
            probe.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), p));
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_PORT, p).apply();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            try { if (probe != null) probe.close(); } catch (Throwable t) { }
        }
    }

    /** 51 号单：当前请求头名称（默认 Authorization）。 */
    public static synchronized String getHeaderName(Context ctx) {
        try {
            String s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_HNAME, HNAME_DEFAULT);
            if (s == null || s.trim().length() == 0) return HNAME_DEFAULT;
            return s.trim();
        } catch (Throwable t) { return HNAME_DEFAULT; }
    }

    /** 51 号单：改请求头名称；名字不合法＝回 false（界面据此给一句人话）。 */
    public static synchronized boolean setHeaderName(Context ctx, String name) {
        if (name == null) return false;
        name = name.trim();
        if (!name.matches("[A-Za-z][A-Za-z0-9-]{0,63}")) return false;
        try { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_HNAME, name).apply(); return true; } catch (Throwable t) { return false; }
    }

    /** 51 号单：换端口后重启监听（关掉旧监听、按当前端口重开；旧端口随之不再响应）。 */
    public static synchronized void restart(Context ctx) {
        try { if (ssRef != null) ssRef.close(); } catch (Throwable t) { }
        ssRef = null;
        running = false;
        Thread old = serveThread;
        serveThread = null;
        started = false;
        if (old != null) { try { old.join(2500); } catch (Throwable t) { } }
        userStopped = false;
        start(ctx);
    }

    public static synchronized String getToken(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String t = sp.getString(KEY_TOKEN, null);
        if (t == null || t.length() == 0) {
            byte[] b = new byte[24];
            new java.security.SecureRandom().nextBytes(b);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < b.length; i++) sb.append(String.format("%02x", (b[i] & 0xff)));
            t = sb.toString();
            sp.edit().putString(KEY_TOKEN, t).apply();
        }
        return t;
    }

    /** 25 追加单：静音模式下是否保留震动提醒（默认关＝尊重系统静音；开＝走闹钟通道强震）。 */
    private static final String PREFS_SETTINGS = "settings";
    private static final String KEY_VIBRATE_SILENT = "vibrate_through_silent";

    public static boolean vibrateThroughSilent(Context ctx) {
        try {
            return ctx.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).getBoolean(KEY_VIBRATE_SILENT, false);
        } catch (Throwable t) { return false; }
    }

    public static void setVibrateThroughSilent(Context ctx, boolean v) {
        try {
            ctx.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).edit().putBoolean(KEY_VIBRATE_SILENT, v).apply();
        } catch (Throwable t) { }
    }

    /** 静音也能震的通道：闹钟类 AudioAttributes（只影响振动策略，不发声、不亮屏）。 */
    private static android.media.AudioAttributes alarmAttrs() {
        return new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).build();
    }

    public static synchronized void start(final Context ctx) {
        userStopped = false;
        if (started && serveThread != null && serveThread.isAlive()) return;
        started = true;
        Thread t = new Thread(new Runnable() {
            public void run() { serve(ctx.getApplicationContext()); }
        });
        t.setDaemon(true);
        serveThread = t;
        t.start();
        if (WATCHDOG_ON) startWatchdog(ctx.getApplicationContext());
    }

    /**
     * 14 服务加固 P2：极简看门狗——只做「查自己 → 不活就拉起自己」。
     * 每约 30 秒查一次本类 serve 线程；不活则复用幂等 start 重起。
     * 不探测外部、不重启别的组件、不写告警文件、不发通知。
     */
    private static synchronized void startWatchdog(final Context ctx) {
        if (watchdogThread != null && watchdogThread.isAlive()) return;
        Thread w = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try { Thread.sleep(WATCHDOG_INTERVAL_MS); } catch (Throwable t) { return; }
                    Thread st = serveThread;
                    if (!userStopped && (st == null || !st.isAlive())) {
                        mlog(ctx, "watchdog: serve down, restart");
                        start(ctx);
                    }
                }
            }
        });
        w.setDaemon(true);
        watchdogThread = w;
        w.start();
    }

    /** 42 单 B：连接类异常写人话（装包换 App／客户端先走那一瞬的正常断连，别把 Java 原文写进日志）。 */
    private static String why(Throwable t) {
        String s = String.valueOf(t);
        if (s.indexOf("Broken pipe") >= 0 || s.indexOf("Socket closed") >= 0
                || s.indexOf("Connection reset") >= 0 || s.indexOf("SocketException") >= 0) {
            return "连接被掐断（装机换 App／对方先走了，属正常）";
        }
        return s;
    }

    private static void mlog(Context ctx, String s) {
        try {
            File d = Home.sub(ctx, "logs");
            if (d == null) return;
            File f = new File(d, "mcp.log");
            if (f.length() > 200000) f.delete();
            FileOutputStream o = new FileOutputStream(f, true);
            o.write((System.currentTimeMillis() + " " + s + "\n").getBytes(StandardCharsets.UTF_8));
            o.close();
        } catch (Throwable t) { }
    }

    // ═══════════ 43 号单（2026-10-11）· B 审计（低价版）：每次 tools/call 追加一行 ═══════════
    /** 审计日志：一行一次调用，格式 = ISO时间 | 工具名 | 参数摘要(≤120字) | ok/err | 耗时ms
     *  落 logs/audit-YYYYMMDD.log（副包自动落 /sdcard/ReticleB/logs/）；单文件满 1MB 换 .1。
     *  硬约束：只记请求体（method/params），**不碰 Authorization 头 → token 永不落**；摘要一律截断。 */
    private static final Object AUDIT_LOCK = new Object();

    private static String auditClip(String s) {
        if (s == null) return "";
        s = s.replace("\r", " ").replace("\n", " ").replace("|", "¦");
        if (s.length() > 120) s = s.substring(0, 120) + "…";
        return s;
    }

    private static void audit(Context ctx, String req, String resp, long ms) {
        try {
            JSONObject m = new JSONObject(req);
            if (!"tools/call".equals(m.optString("method", ""))) return;   // 只记工具调用，不记 initialize/tools/list 噪音
            String tool = "";
            String summary = "";
            JSONObject p = m.optJSONObject("params");
            if (p != null) {
                tool = p.optString("name", "");
                JSONObject a = p.optJSONObject("arguments");
                if (a != null) summary = auditClip(a.toString().replace("\\/", "/"));
            }
            if (tool.length() == 0) tool = "(未命名)";
            String res = "ok";
            if (resp == null) {
                res = "err";
            } else {
                try {
                    JSONObject r = new JSONObject(resp);
                    if (r.has("error")) res = "err";
                    JSONObject rr = r.optJSONObject("result");
                    if (rr != null && rr.optBoolean("isError", false)) res = "err";
                } catch (Throwable t) { res = "err"; }
            }
            synchronized (AUDIT_LOCK) {
                File d = Home.sub(ctx, "logs");
                if (d == null) return;
                String day = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.ROOT).format(new java.util.Date());
                File f = new File(d, "audit-" + day + ".log");
                if (f.length() > 1048576L) {
                    File old = new File(d, "audit-" + day + ".log.1");
                    if (old.exists()) old.delete();
                    if (!f.renameTo(old)) { try { new FileOutputStream(f, false).close(); } catch (Throwable t) { } }
                }
                String ts = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.ROOT).format(new java.util.Date());
                String why = "";
                if (res.equals("err")) { try { why = FAILDED.remove(failKey(req)); } catch (Throwable t) { } if (why == null) why = ""; }   // ★77.6：err 行带原因（process 在请求池线程 ⇒ 用跨线程表，不用 ThreadLocal）
                try { LASTFAIL.remove(); } catch (Throwable t) { }
                String cmk = null;
                try { cmk = CONCDED.remove(failKey(req)); } catch (Throwable t) { }
                String col = why.length() == 0 ? res : (res + ":" + auditClip(why));
                if (cmk != null && cmk.length() > 0) col = col + " 并发 " + auditClip(cmk);
                FileOutputStream o = new FileOutputStream(f, true);
                o.write((ts + " | " + tool + " | " + summary + " | " + col + " | " + ms + "ms\n").getBytes(StandardCharsets.UTF_8));
                o.close();
            }
        } catch (Throwable t) { }
    }

    private static void serve(Context ctx) {
        ServerSocket ss = null;
        try {
            ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), getPort(ctx)));
            ssRef = ss;
            running = true;
            mlog(ctx, "mcp server started on 127.0.0.1:" + getPort(ctx) + "（进程起后 +" + (android.os.SystemClock.elapsedRealtime() - Boot.PROC) + "ms）");   // 86.5：这句带上毫秒
            Boot.mark(ctx, "t3", "MCP 监听成功 127.0.0.1:" + getPort(ctx));   // 86.5
            while (true) {
                final Socket s = ss.accept();
                final Context c = ctx;
                Thread th = new Thread(new Runnable() {
                    public void run() {
                        try { handle(s, c); } catch (Throwable t) { mlog(c, why(t)); }
                        finally { try { s.close(); } catch (Throwable t) { } }
                    }
                });
                th.setDaemon(true);
                th.start();
            }
        } catch (Throwable t) {
            running = false;
            started = false;
            mlog(ctx, "监听断了：" + why(t));
        }
    }

    private static void handle(Socket s, Context ctx) throws Exception {
        s.setSoTimeout(15000);
        InputStream in = s.getInputStream();
        ByteArrayOutputStream hb = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            hb.write(c);
            int n = hb.size();
            byte[] a = hb.toByteArray();
            if (n >= 4 && a[n - 4] == '\r' && a[n - 3] == '\n' && a[n - 2] == '\r' && a[n - 1] == '\n') break;
            if (n > 65536) break;
        }
        String head = new String(hb.toByteArray(), StandardCharsets.UTF_8);
        String[] lines = head.split("\r\n");
        String reqLine = lines.length > 0 ? lines[0] : "";
        String[] rp = reqLine.split(" ");
        String method = rp.length > 0 ? rp[0] : "";
        String path = rp.length > 1 ? rp[1] : "";
        // 51 号单：请求头名称可自定义（默认 authorization）——只认这个名字，别的名字一律当没有
        String hnameKey = getHeaderName(ctx).toLowerCase(java.util.Locale.ROOT);
        boolean selfreq = false;   // ★82：这条请求是不是 /selfcheck 自己发的
        String auth = "";
        int clen = 0;
        for (int i = 1; i < lines.length; i++) {
            String ln = lines[i];
            int p = ln.indexOf(':');
            if (p <= 0) continue;
            String k = ln.substring(0, p).trim().toLowerCase();
            String v = ln.substring(p + 1).trim();
            if (k.equals(hnameKey)) auth = v;
            if (k.equals("x-reticle-selftest")) selfreq = true;   // ★82：自检自己发的内部调用
            if (k.equals("content-length")) { try { clen = Integer.parseInt(v); } catch (Throwable t) { } }
        }
        byte[] body = new byte[0];
        if (clen > 0 && clen < 2000000) {
            ByteArrayOutputStream bb = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int left = clen;
            while (left > 0) {
                int r = in.read(buf, 0, Math.min(buf.length, left));
                if (r <= 0) break;
                bb.write(buf, 0, r);
                left -= r;
            }
            body = bb.toByteArray();
        }
        OutputStream out = s.getOutputStream();
        String token = getToken(ctx);
        boolean isMcp = path.startsWith("/mcp");
        if ("GET".equals(method) && path.startsWith("/selfcheck")) { respond(out, 200, selfCheckJson(ctx)); return; }
        if ("DELETE".equals(method) && isMcp) { respond(out, 200, "{}"); return; }
        if (!("POST".equals(method) && isMcp)) { respond(out, 404, "{\"error\":\"not found\"}"); return; }
        if (!auth.equals("Bearer " + token)) { respond(out, 401, "{\"error\":\"unauthorized\"}"); return; }
        String req = new String(body, StandardCharsets.UTF_8);
        long t0 = System.currentTimeMillis();   // 43 号单 B：审计计时（写 audit-YYYYMMDD.log）
        String resp;
        java.util.concurrent.Future<String> fu = null;
        try {
            final String reqF = req;
            final boolean selfF = selfreq;   // ★82：自检内部调用（不进并发层）
            fu = REQ_POOL.submit(new java.util.concurrent.Callable<String>() {
                public String call() {
                    try { return process(ctx, reqF, selfF); }
                    finally { try { Notifier.toolIdle(ctx); } catch (Throwable t) { } }
                }
            });
            resp = fu.get(REQ_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException te) {
            if (fu != null) fu.cancel(true);
            mlog(ctx, "请求超过 " + (REQ_TIMEOUT_MS / 1000) + " 秒没人接（客户端可能已断开）：" + method + " " + path);
            // 39 号单 · 断流人话：先回一句中文结果再关连接，别让助手看到 okhttp 的英文堆栈。
            try { respond(out, 200, busyJson(req, Words.RP_BUSY_TIMEOUT)); } catch (Throwable t) { }
            audit(ctx, req, null, System.currentTimeMillis() - t0);
            try { s.close(); } catch (Throwable t) { }
            return;
        } catch (Throwable t) {
            mlog(ctx, "请求处理出意外：" + why(t));
            try { respond(out, 200, busyJson(req, Words.RP_BUSY_ERR)); } catch (Throwable x) { }
            audit(ctx, req, null, System.currentTimeMillis() - t0);
            try { s.close(); } catch (Throwable t2) { }
            return;
        }
        // 86.5 号单 · t5：第一个工具请求真的成功了（只记一次；失败/超时不算）
        if (resp != null && resp.indexOf("\"isError\":true") < 0) {
            String tn5 = "首个请求";
            try { org.json.JSONObject j5 = new org.json.JSONObject(req); org.json.JSONObject p5 = j5.optJSONObject("params"); if (p5 != null) tn5 = p5.optString("name", tn5); } catch (Throwable t5x) { }
            Boot.firstToolOk(ctx, tn5);
        }
        respond(out, resp == null ? 202 : 200, resp == null ? "" : resp);
        audit(ctx, req, resp, System.currentTimeMillis() - t0);
    }

    private static void respond(OutputStream out, int code, String body) throws Exception {
        byte[] rb = body.getBytes(StandardCharsets.UTF_8);
        String msg = code == 200 ? "OK" : (code == 202 ? "Accepted" : (code == 401 ? "Unauthorized" : "Not Found"));
        String head = "HTTP/1.1 " + code + " " + msg + "\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + rb.length + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(rb);
        out.flush();
    }

    /** 39 号单 · 断流人话：把「服务端忙／连接会断」翻成一句中文结果，助手读得懂（不再甩几十行异常堆栈）。
     *  只在请求超时或服务端出意外时用；正常请求照旧走 process()。 */
    private static String busyJson(String req, String why) {
        String idv = "null";
        String tool = "";
        try {
            JSONObject j = new JSONObject(req);
            Object i = j.opt("id");
            if (i != null) idv = i.toString();
            JSONObject p = j.optJSONObject("params");
            if (p != null) tool = p.optString("name", "");
        } catch (Throwable t) { }
        if (!(idv.equals("null") || idv.matches("-?[0-9]+"))) idv = "\"" + idv.replace("\"", "") + "\"";
        String text = Words.RP_BUSY_PREFIX + why + Words.RP_BUSY_TAIL;
        if (tool.length() > 0) text = text + "（工具：" + tool + "）";
        text = text.replace("\\", "").replace("\"", "'");
        return "{\"jsonrpc\":\"2.0\",\"id\":" + idv + ",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"" + text + "\"}],\"isError\":false}}";
    }

    /**
     * 81 号单（2026-10-17）· 并发分级（该拒的拒，该放的放，能说的说清）
     *
     * 为什么要这一层：MCP 是 HTTP 短连接、无 session ⇒ 服务端分不出「哪个对话／哪个客户端」，
     * 只能按「在途请求 ＋ 时间先后」判 ⇒ 文案一律说「另一条调用」，绝不写「另一个对话」。
     *
     * 规则（照派单表）：
     *   写／动作 碰 写／动作  ⇒ 后到的当场拒（ok:0 ＋ 原因 ＋ 指路 do_chain）
     *   只读 碰 任何          ⇒ 都放行，返回里加一句并发标记（工具名＋距今几秒）
     *   写 碰 只读            ⇒ 放行，写那件也带标记
     *   do_chain              ⇒ 独占窗口（它本身算写；链内各步不经本层 ⇒ 互不拒）
     *   没有并发              ⇒ 零变化（不拒、不加标记、正文一字不动）
     */
    static final class Conc {
        static final int R = 0;   // 只读
        static final int W = 1;   // 写／动作

        private static final java.util.ArrayList<Item> LIVE = new java.util.ArrayList<Item>();
        private static long SEQ = 0L;

        private static final class Item {
            long id;
            String tool;
            long at;
            int cls;
        }

        /** 一次请求的判定结果：ok=0 时 why 是拒绝原因（不含 ok:0 前缀）；mark／audit 只在放行且此刻有别的调用时非空。 */
        static final class D {
            long id = -1L;
            boolean ok = true;
            String why = "";
            String mark = "";
            String audit = "";
        }

        private static final java.util.HashSet<String> WRITE = new java.util.HashSet<String>();
        static {
            String[] w = {"tap", "swipe", "longpress", "input", "paste", "key", "tap_text",
                    "scroll", "scroll_to_text", "do_chain", "download", "acttest"};
            for (int i = 0; i < w.length; i++) WRITE.add(w[i]);
        }

        private static final java.util.HashSet<String> RO_FIRST = new java.util.HashSet<String>();
        static {
            String[] r = {"ls", "cat", "grep", "ps", "dumpsys", "getprop", "wc", "head", "tail",
                    "df", "du", "find", "date", "id", "ping", "echo", "sed", "sha256sum", "md5sum",
                    "stat", "file", "uname", "whoami", "uptime", "free", "pwd", "which"};
            for (int i = 0; i < r.length; i++) RO_FIRST.add(r[i]);
        }

        /** 工具归类：写／只读。表外的（notify／buzz／status／help／ping／windows／wait_* …）一律当只读放行。 */
        static int classOf(String tool, String argsJson) {
            if (tool == null || tool.length() == 0) return R;
            if (tool.equals("shell")) return roShell(argsJson) ? R : W;
            if (tool.equals("build")) {
                String a = optStr(argsJson, "action");
                if (a.length() == 0) a = "status";
                if (a.equals("new") || a.equals("start") || a.equals("install")) return W;
                return R;   // check／help／env／status：只读
            }
            return WRITE.contains(tool) ? W : R;
        }

        private static String optStr(String argsJson, String k) {
            try {
                if (argsJson == null || argsJson.length() == 0) return "";
                return new org.json.JSONObject(argsJson).optString(k, "");
            } catch (Throwable t) { return ""; }
        }

        /** shell 判只读：先看显式声明（write=1／ro=1），再按「首词白名单 ＋ 写特征黑名单」判，判不准一律当写。 */
        static boolean roShell(String argsJson) {
            String cmd = "";
            int wr = 0, ro = 0;
            try {
                org.json.JSONObject a = new org.json.JSONObject(argsJson == null || argsJson.length() == 0 ? "{}" : argsJson);
                cmd = a.optString("command", "");
                wr = a.optInt("write", 0);
                ro = a.optInt("ro", 0);
            } catch (Throwable t) { }
            if (wr == 1) return false;
            if (ro == 1) return true;
            return roCmd(cmd);
        }

        static boolean roCmd(String cmd) {
            if (cmd == null) return false;
            String t = cmd.trim();
            if (t.length() == 0) return false;
            if (t.indexOf('>') >= 0) return false;            // 重定向（含 >>）
            if (t.indexOf('&') >= 0) return false;            // 后台 ＆ &&
            String[] bad = {"rm", "mv", "cp", "tee", "dd", "nohup", "input", "am", "touch", "mkdir",
                    "chmod", "chown", "kill", "setprop", "ln", "truncate", "mount", "reboot", "svc", "cmd"};
            for (int i = 0; i < bad.length; i++) if (hit(t, bad[i])) return false;
            if (t.matches("(?s).*sed\\s+-i.*")) return false;
            if (t.matches("(?s).*content\\s+(insert|update|delete).*")) return false;   // content insert|update|delete
            if (t.matches("(?s).*settings\\s+put.*")) return false;
            if (t.matches("(?s).*pm\\s+install.*")) return false;
            if (t.matches("(?s).*find\\s+[^|]*\\s-delete.*")) return false;
            String first = t.split("\\s+")[0];
            int sl = first.lastIndexOf('/');
            if (sl >= 0) first = first.substring(sl + 1);
            return RO_FIRST.contains(first);
        }

        private static boolean hit(String cmd, String word) {
            try {
                return java.util.regex.Pattern.compile("(^|[\\s;|()])" + word + "(\\s|$)").matcher(cmd).find();
            } catch (Throwable t) { return true; }
        }

        /** 进窗口：写碰写＝拒（不登记）；否则登记并给标记（此刻有别的调用才给）。 */
        static D enter(String tool, String argsJson) {
            D d = new D();
            int cls = classOf(tool, argsJson);
            long now = System.currentTimeMillis();
            synchronized (LIVE) {
                Item w = null;
                Item any = null;
                for (int i = LIVE.size() - 1; i >= 0; i--) { if (now - LIVE.get(i).at > 120000L) LIVE.remove(i); }   // 防泄漏：在途超过 120 秒的一律算挂了
                for (int i = LIVE.size() - 1; i >= 0; i--) {
                    Item it = LIVE.get(i);
                    if (any == null) any = it;
                    if (w == null && it.cls == W) w = it;
                }
                if (cls == W && w != null) {
                    d.ok = false;
                    d.why = reject(tool, w, now);
                    return d;
                }
                if (any != null) {
                    d.mark = mark(any, now);
                    d.audit = any.tool + " " + sec(any, now) + "s 前";
                }
                d.id = ++SEQ;
                Item me = new Item();
                me.id = d.id;
                me.tool = tool;
                me.at = now;
                me.cls = cls;
                LIVE.add(me);
            }
            return d;
        }

        /** 出窗口。 */
        static void exit(D d) {
            if (d == null || d.id <= 0) return;
            synchronized (LIVE) {
                for (int i = 0; i < LIVE.size(); i++) {
                    if (LIVE.get(i).id == d.id) { LIVE.remove(i); return; }
                }
            }
        }

        private static String sec(Item it, long now) {
            double s = (now - it.at) / 1000.0;
            return String.format(java.util.Locale.ROOT, "%.1f", s);
        }

        /** 拒的理由：说清是谁在跑、几秒前开始、下一步怎么走；不许出现「另一个对话」。 */
        private static String reject(String tool, Item w, long now) {
            String who = "另一条写动作 " + w.tool + " 在 " + sec(w, now) + " 秒前开始";
            if (tool.equals("do_chain"))   // ★82：整条链被拒＝一步都没跑，别让助手以为已经跑了几步
                return "ok:0 并发冲突：这条 do_chain 被拒——整条链一步都没跑（" + who + "）。等它回来再发；连做多步仍然一次发一条 do_chain（链内各步互不拒）。";
            return "ok:0 并发冲突：这条 " + tool + " 没执行——此刻还有另一条写动作 " + w.tool
                    + " 在跑（" + sec(w, now) + " 秒前开始）。等它回来再发；要连做多步用 do_chain。";
        }

        /** 放行时的并发标记：工具名 ＋ 距今几秒。 */
        private static String mark(Item it, long now) {
            return "（并发：此刻还有另一条调用 " + it.tool + "，" + sec(it, now) + " 秒前开始的）";
        }

        /** 自检／单测用：当前在途条数。 */
        static int live() {
            synchronized (LIVE) { return LIVE.size(); }
        }
    }
    /** 81 号单 · 并发标记／拒绝原因的跨线程表（process 在请求池线程、audit 在 handle 线程 ⇒ 不用 ThreadLocal）。 */
    private static final java.util.concurrent.ConcurrentHashMap<String, String> CONCDED = new java.util.concurrent.ConcurrentHashMap<String, String>();

    /** 81 号单 · 并发拒绝：后到的写动作当场失败（正文 ok:0 ＋ 原因 ＋ 指路），同一句原因进 audit。 */
    private static String concRejectJson(String req, String text) {
        String idv = "null";
        try {
            JSONObject j = new JSONObject(req);
            Object i = j.opt("id");
            if (i != null) idv = i.toString();
        } catch (Throwable t) { }
        if (!(idv.equals("null") || idv.matches("-?[0-9]+"))) idv = "\"" + idv.replace("\"", "") + "\"";
        try { FAILDED.put(failKey(req), text); } catch (Throwable t) { }
        String tx = text.replace("\\", "").replace("\"", "'").replace("\n", " ");
        return "{\"jsonrpc\":\"2.0\",\"id\":" + idv + ",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"" + tx + "\"}],\"isError\":true}}";
    }

    /** 81 号单 · 并发标记：只在「此刻还有别的调用」时追加一块文本，正文一个字不改（ui／ocr 的 JSON 也不受影响）。 */
    private static String concMarkJson(String req, String resp, String mark, String audit) {
        try {
            JSONObject j = new JSONObject(resp);
            JSONObject res = j.optJSONObject("result");
            if (res == null) return resp;
            JSONArray c = res.optJSONArray("content");
            if (c == null) return resp;
            c.put(textBlock(mark));
            if (audit != null && audit.length() > 0) CONCDED.put(failKey(req), audit);
            return j.toString();
        } catch (Throwable t) { return resp; }
    }

    private static String process(Context ctx, String req) { return process(ctx, req, false); }

    /** ★82：selftest=1 ＝ /selfcheck 自己的内部调用（ping/buzz 等）——不进并发层：不打标记、不占窗口、audit 也不带并发。 */
    private static String process(Context ctx, String req, boolean selftest) {
        String rp;
        try { FAIL.remove(); } catch (Throwable t) { }   // ★71 A 线：每个请求开头清标记
        try { LASTFAIL.remove(); } catch (Throwable t) { }   // ★77.6：每个请求开头清失败原因
        // ★81 并发分级：写／动作碰写 ⇒ 后到的当场拒；只读一律放行（返回里加一句标记）；没有并发＝零变化
        String cTool = "";
        String cArgs = "";
        try {
            JSONObject m0 = new JSONObject(req);
            if ("tools/call".equals(m0.optString("method", ""))) {
                JSONObject p0 = m0.optJSONObject("params");
                if (p0 != null) {
                    cTool = p0.optString("name", "");
                    JSONObject a0 = p0.optJSONObject("arguments");
                    if (a0 != null) cArgs = a0.toString();
                }
            }
        } catch (Throwable t) { }
        Conc.D cd = null;
        if (cTool.length() > 0 && !selftest) {   // ★82：自检自己吵自己——/selfcheck 的内部调用一律不进并发层
            try { cd = Conc.enter(cTool, cArgs); } catch (Throwable t) { cd = null; }
            if (cd != null && !cd.ok) return concRejectJson(req, cd.why);
        }
        boolean yielded = false;
        try { yielded = Mascot.yieldForTouch(req); } catch (Throwable t) { }   // 65：触摸类工具，触点可能压在形象上就先让路
        try { Notifier.selfMode(selftest); } catch (Throwable t) { }
        try { rp = process0(ctx, req); } catch (Throwable t) { rp = null; }
        finally { if (yielded) { try { Mascot.endYield(); } catch (Throwable t) { } } }
        try { rp = markIsError(req, rp); } catch (Throwable t) { }   // ★70 A 线：工具失败＝MCP 原生的 isError（必须排在 Mascot.watch 之前）
        // ★86：工具收尾带成败——大通知那一行说人话（完成／没成／超时没成）；失败不受节流限制
        if (cTool.length() > 0) {   // ★86：所有工具都要收尾（含自检调用，免得列表里留「进行中」）
            try {
                boolean okT = rp != null && rp.indexOf("\"isError\":true") < 0;
                String lfT = null;
                try { lfT = LASTFAIL.get(); } catch (Throwable t) { }
                boolean toT = !okT && lfT != null && lfT.toLowerCase(java.util.Locale.ROOT).indexOf("timeout") >= 0;
                Notifier.toolDone(ctx, cTool, okT, toT);
            } catch (Throwable t) { }
        }
        try { Notifier.selfMode(false); } catch (Throwable t) { }
        try { Mascot.watch(ctx, req, rp); } catch (Throwable t) { }
        if (cd != null && cd.mark.length() > 0 && rp != null) { try { rp = concMarkJson(req, rp, cd.mark, cd.audit); } catch (Throwable t) { } }
        try { Conc.exit(cd); } catch (Throwable t) { }
        return rp;
    }

    /**
     * ★71 A 线（2026-10-15）· 工具失败判据（收紧版）——为什么改：
     * 70 号的判据在**整段响应正文里找词**（`exit=` 后面的数字、`timeout=1`）⇒ 一条**成功**的命令，
     * 只要它的输出里"提到"这些字样（读日志、读证据文档、翻手册），就被判成失败并冒泡（15 号已复现）。
     * 正文是"用户内容"，它可能提到任何字眼 ⇒ **判据绝不许看正文**。
     *
     * 现在唯一的判据来源＝**工具自己按下的结构化标记**（工具自己知道成没成）：
     *   1) execShell（shell／build）：Shizuku binder 不通／`ok=0`／`exit≠0`／`timeout=1`／抛异常
     *   2) failText()：动作类工具（tap／swipe／key／input／paste／scroll／tap_text／wait_text／
     *      wait_stable／longpress／scroll_to_text／do_chain）JSON 里的 `ok=0`
     *   3) download／download_status：Downloader 自己按下的失败标记（含 sha 不符／长度不符／源站 4xx）
     *   4) notify／buzz：内部返回码不是 "ok"（含无震动器）
     *   5) shot：截图 JSON `ok=0`
     *   6) ui／ocr／windows：正文就是工具自己的 JSON ⇒ 只读它的 `ok` 字段
     *   7) 已经是 isError:true 的：幂等，不再重复处理
     * ★正常结果（exit=0、下好了、已写入…）绝不改判；★正文里出现「失败」「timeout=1」一律不算失败。
     */
    private static final ThreadLocal<String> FAIL = new ThreadLocal<String>();

    /** ★77.6：这次请求的失败原因（audit 的 err 行要如实带上它）。 */
    /** ★77.6：这次请求的失败原因——process 跑在请求线程池、audit 跑在 handle 线程 ⇒ 必须跨线程传。 */
    private static final java.util.concurrent.ConcurrentHashMap<String, String> FAILDED = new java.util.concurrent.ConcurrentHashMap<String, String>();
    private static String failKey(String req) { return req == null ? "?" : (Integer.toHexString(req.hashCode()) + ":" + req.length()); }
    private static final ThreadLocal<String> LASTFAIL = new ThreadLocal<String>();

    /** ★71 A 线：工具代码自己按下"这次真失败了"。同一请求内以第一次按下为准。 */
    static void failMark(String why) {
        try { if (FAIL.get() == null) FAIL.set(why == null ? "fail" : why); } catch (Throwable t) { }
    }

    /** ★71 A 线：取走并清空失败标记（判据只读它，不看正文）。 */
    static String failTake() {
        try { String s = FAIL.get(); FAIL.remove(); return s; } catch (Throwable t) { return null; }
    }

    /** ★71 A 线：按下失败标记，并把结果文本原样返回（省得每处都写两行）。 */
    static String failR(String text, String why) { failMark(why); return text; }

    private static boolean looksFail(String tool, String text) {
        String why = failTake();
        if (why != null) { try { LASTFAIL.set(why); } catch (Throwable t) { } return true; }
        if (tool.equals("ui") || tool.equals("ocr") || tool.equals("windows")) {
            try {
                JSONObject o = new JSONObject(text == null ? "" : text.trim());
                if (o.has("ok") && o.optInt("ok", 1) != 1) {
                    String rs = o.optString("reason", "");
                    String es = o.optString("err", "");
                    try { LASTFAIL.set(tool + " ok=0" + (rs.length() > 0 ? " reason=" + rs : (es.length() > 0 ? " err=" + es : ""))); } catch (Throwable t) { }
                    return true;
                }
                return false;
            } catch (Throwable t) { return false; }
        }
        return false;
    }

    /** ★70 A 线：把 tools/call 的结果按判据表改成 isError:true（只动 MCP 原生字段，正文一个字不改）。 */
    private static String markIsError(String req, String resp) {
        if (resp == null) return resp;
        if (resp.indexOf("\"isError\":false") < 0) return resp;
        try {
            JSONObject j = new JSONObject(resp);
            JSONObject res = j.optJSONObject("result");
            if (res == null || res.optBoolean("isError", false)) return resp;
            JSONArray c = res.optJSONArray("content");
            if (c == null || c.length() == 0) return resp;
            JSONObject b = c.optJSONObject(0);
            if (b == null || !"text".equals(b.optString("type", ""))) return resp;
            String name = "";
            try {
                JSONObject p = new JSONObject(req).optJSONObject("params");
                if (p != null) name = p.optString("name", "");
            } catch (Throwable t) { }
            if (!looksFail(name, b.optString("text", ""))) return resp;
            res.put("isError", true);
            try { String w2 = LASTFAIL.get(); if (w2 != null && w2.length() > 0) FAILDED.put(failKey(req), w2); LASTFAIL.remove(); } catch (Throwable t) { }
            return j.toString();
        } catch (Throwable t) { return resp; }
    }

    private static String process0(Context ctx, String req) {
        try {
            JSONObject m = new JSONObject(req);
            Object id = m.opt("id");
            String method = m.optString("method", "");
            if (method.length() == 0) return err(id, -32600, "invalid request");
            if (id == null || m.isNull("id")) return null;
            if (method.equals("initialize")) {
                JSONObject result = new JSONObject();
                result.put("protocolVersion", "2024-11-05");
                JSONObject caps = new JSONObject();
                caps.put("tools", new JSONObject());
                result.put("capabilities", caps);
                JSONObject si = new JSONObject();
                si.put("name", "mcp-eyeshot");
                si.put("version", pkgVer(ctx));
                result.put("serverInfo", si);
                return ok(id, result);
            }
            if (method.equals("ping")) return ok(id, new JSONObject());
            if (method.equals("tools/list")) {
                S_CTX = ctx;
                JSONArray tools = buildTools();
                JSONObject result = new JSONObject();
                result.put("tools", tools);
                return ok(id, result);
            }
            if (method.equals("tools/call")) {
                JSONObject params = m.optJSONObject("params");
                if (params == null) return err(id, -32602, "no params");
                String name = params.optString("name", "");
                JSONObject args = params.optJSONObject("arguments");
                if (args == null) args = new JSONObject();
                Notifier.toolTouch(ctx, name);   // 86：26 个工具都记账（buzz 也记，中文名要能在通知里看见）
                S_CTX = ctx;
                if (name.equals("status")) {
                    JSONObject result = new JSONObject();
                    result.put("content", content(statusText(ctx)));
                    result.put("isError", false);
                    return ok(id, result);
                }
                if (name.equals("help")) {
                    JSONObject result = new JSONObject();
                    result.put("content", content(helpText(args.optString("tool", ""))));
                    result.put("isError", false);
                    return ok(id, result);
                }
                if (name.equals("acttest")) {
                    JSONObject result = new JSONObject();
                    result.put("content", content(actSelfTest(ctx)));
                    result.put("isError", false);
                    return ok(id, result);
                }
                if (name.equals("ping")) {
                    JSONObject result = new JSONObject();
                    result.put("content", content(Words.RP_HELLO));
                    result.put("isError", false);
                    return ok(id, result);
                }
                if (name.equals("buzz")) {
                    int dms = args.optInt("duration_ms", 300);
                    if (dms < 50) dms = 50;
                    if (dms > 10000) dms = 10000;
                    boolean thru = vibrateThroughSilent(ctx);
                    boolean dry = args.optInt("dry", 0) == 1;   // 40 单：dry=1＝只验通路、不震动（自检／健康巡检专用；只有助手显式 buzz 才真震）
                    String brz;
                    try {
                        android.os.Vibrator vb = (android.os.Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
                        if (dry) {
                            brz = String.format(java.util.Locale.ROOT, Words.RP_BUZZ_DRY, dms);
                        } else if (vb == null || !vb.hasVibrator()) {
                            brz = failR(Words.RP_BUZZ_NO_VIB, "no vibrator");
                        } else if (android.os.Build.VERSION.SDK_INT >= 26) {
                            if (thru) {
                                vb.vibrate(android.os.VibrationEffect.createOneShot(dms, android.os.VibrationEffect.DEFAULT_AMPLITUDE), alarmAttrs());
                            } else {
                                vb.vibrate(android.os.VibrationEffect.createOneShot(dms, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                            }
                            brz = String.format(java.util.Locale.ROOT, Words.RP_BUZZ_FMT, dms, thru ? Words.RP_BUZZ_ON : Words.RP_BUZZ_OFF);
                        } else {
                            if (thru) vb.vibrate(dms, alarmAttrs()); else vb.vibrate(dms);
                            brz = String.format(java.util.Locale.ROOT, Words.RP_BUZZ_FMT, dms, thru ? Words.RP_BUZZ_ON : Words.RP_BUZZ_OFF);
                        }
                    } catch (Throwable t) { brz = failR(Words.RP_BUZZ_ERR + t, "buzz exception"); }
                    JSONObject rbz = new JSONObject();
                    rbz.put("content", content(brz));
                    rbz.put("isError", false);
                    return ok(id, rbz);
                }
                if (name.equals("download")) {
                    JSONObject result = new JSONObject();
                    String dlt = Downloader.download(ctx, args);
                    if (Downloader.LAST_FAIL) failMark("download failed");
                    result.put("content", content(dlt));
                    result.put("isError", false);
                    return ok(id, result);
                }
                if (name.equals("download_status")) {
                    JSONObject result = new JSONObject();
                    String dst = Downloader.status(ctx, args);
                    if (Downloader.LAST_FAIL) failMark("download_status failed");
                    result.put("content", content(dst));
                    result.put("isError", false);
                    return ok(id, result);
                }
                if (name.equals("build")) {
                    String action = args.optString("action", "status");
                    java.util.LinkedHashMap<String, String> cf = buildConf(ctx);
                    File proj = new File(cf.get("PROJECT_DIR"));
                    File keep = new File(cf.get("MATERIAL_DIR"));
                    if (action.equals("check") || action.equals("help")) {
                        boolean rdy = BuildEnv.ready(ctx);
                        StringBuilder sb = new StringBuilder();
                        sb.append(Words.RP_BUILD_ENV_HEAD).append(rdy ? Words.RP_BUILD_ENV_OK : Words.RP_BUILD_ENV_NO);
                        if (!rdy) sb.append(BuildEnv.missing(ctx));
                        boolean pok = proj.isDirectory() && new File(proj, "AndroidManifest.xml").exists();
                        if (pok) {
                            sb.append("工程：").append(BuildRunner.brief(proj)).append("\n");
                            sb.append(Words.RP_BUILD_PROJ_ROADS);
                        } else {
                            sb.append(String.format(java.util.Locale.ROOT, Words.RP_BUILD_PROJ_NONE, BuildRunner.defaultProject(ctx).getAbsolutePath()));
                        }
                        sb.append("产物：").append(BuildRunner.outDir(ctx).getAbsolutePath()).append("\n");
                        if (action.equals("help")) sb.append(Words.RP_BUILD_HELP).append("\n");
                        sb.append(BuildRunner.status(ctx));
                        JSONObject cres = new JSONObject();
                        cres.put("content", content(sb.toString()));
                        cres.put("isError", false);
                        return ok(id, cres);
                    }
                    if (action.equals("env")) {
                        if (BuildEnv.ready(ctx)) {
                            JSONObject j0 = new JSONObject();
                            j0.put("content", content(Words.RP_BUILD_ENV_OK));
                            j0.put("isError", false);
                            return ok(id, j0);
                        }
                        final Context ac = ctx.getApplicationContext();
                        new Thread(new Runnable() { public void run() {
                            String r = BuildEnv.prepare(ac, BuildEnv.notifier(ac));
                            BuildEnv.notifyDone(ac, r.length() == 0 ? "android.jar 就位（sha256 校验过），环境齐了" : r, r.length() != 0);
                        } }).start();
                        JSONObject j1 = new JSONObject();
                        j1.put("content", content(Words.RP_BUILD_ENV_STARTED));
                        j1.put("isError", false);
                        return ok(id, j1);
                    }
                    if (action.equals("new")) {
                        String r = BuildRunner.newProject(ctx, proj, args.optString("name", ""), args.optString("pkg", ""));
                        boolean bad = r.startsWith("！");
                        if (bad) failMark("build new 不成");
                        JSONObject jn = new JSONObject();
                        jn.put("content", content(r));
                        jn.put("isError", bad);
                        return ok(id, jn);
                    }
                    if (action.equals("start")) {
                        String r = BuildRunner.start(ctx, proj, keep, args.optInt("release", 0) == 1);
                        JSONObject j = new JSONObject();
                        if (r.length() > 0) { failMark("build start 不成"); j.put("content", content(r)); j.put("isError", true); }
                        else { j.put("content", content(Words.RP_BUILD_START + "（产物目录：" + BuildRunner.outDir(ctx).getAbsolutePath() + "）；进度用 action=status 看")); j.put("isError", false); }
                        return ok(id, j);
                    }
                    if (action.equals("install")) {
                        String r = BuildRunner.install(ctx);
                        boolean bad = !r.startsWith("已拉起系统安装器");
                        if (bad) failMark("build install 不成");
                        JSONObject j = new JSONObject();
                        j.put("content", content(r));
                        j.put("isError", bad);
                        return ok(id, j);
                    }
                    JSONObject j = new JSONObject();
                    j.put("content", content(BuildRunner.status(ctx)));
                    j.put("isError", false);
                    return ok(id, j);
                }
                if (name.equals("shell")) {
                    String command = args.optString("command", "");
                    if (command.length() == 0) return err(id, -32602, "no command" + String.format(java.util.Locale.ROOT, Words.HP_POINT, "shell"));
                    String r = execShell(ctx, command);
                    JSONObject result = new JSONObject();
                    result.put("content", content(r));
                    result.put("isError", false);
                    return ok(id, result);
                }
                if (name.equals("shot")) {
                    String rs = VeriService.mcpDispatch("shot", argsToKv(args));
                    JSONArray ct = new JSONArray();
                    JSONObject rj = null;
                    try { rj = new JSONObject(rs); } catch (Throwable t) { }
                    if (rj != null && rj.optInt("ok", 0) == 1) {
                        ct.put(textBlock(String.format(java.util.Locale.ROOT, Words.RP_SHOT_FMT, rj.optString("file", ""), rj.optInt("w"), rj.optInt("h"), rj.optInt("latency_ms"))));
                        addImage(ctx, ct, rj.optString("file", ""));
                    } else {
                        String whyR = "";
                        failMark("shot ok=0" + (whyR.length() > 0 ? " reason=" + whyR : ""));
                        ct.put(textBlock(Words.RP_SHOT_ERR + rs + nextStep(whyR)));
                    }
                    JSONObject result = new JSONObject();
                    result.put("content", ct);
                    result.put("isError", false);
                    return ok(id, result);
                }
                if (name.equals("notify")) {
                    int nid = args.has("id") ? args.optInt("id", Notifier.DEF_ID) : Notifier.DEF_ID;   // 86：不带 id ⇒ 并进大通知（9）
                    String repN0 = args.optString("report", "");
                    if (repN0.length() > 0) {
                        Notifier.setReport(ctx, repN0);
                        JSONObject rres = new JSONObject();
                        rres.put("content", content(Words.RP_REPORT + repN0));
                        rres.put("isError", false);
                        return ok(id, rres);
                    }
                    String outS;
                    if (args.optInt("clear", 0) == 1) {
                        String rN = Notifier.cancel(ctx, nid);
                        outS = "ok".equals(rN) ? (Words.RP_NT_CLEARED + nid) : failR(Words.RP_NT_CLEAR_ERR + rN, "notify cancel");
                    } else {
                        String titleN = args.optString("title", "Reticle");
                        String contN = args.optString("content", "");
                        boolean ongN = args.optInt("ongoing", 1) == 1;
                        String islN = args.optString("island", "");
                        String rN;
                        if (islN.length() > 0) {
                            int opN = args.optInt("op", 0);
                            rN = Notifier.island(ctx, nid, opN, islN, titleN, contN, ongN);
                            outS = "ok".equals(rN) ? (Words.RP_ISLAND_OK + nid + Words.RP_ISLAND_OP + opN + Words.RP_ISLAND_SCENE + islN) : failR(Words.RP_ISLAND_ERR + rN, "notify island");
                        } else if (args.has("media") || args.has("playing")) {
                            outS = Words.RP_MEDIA_GONE;
                        } else if (args.optInt("promoted", 0) == 1) {
                            rN = Notifier.promoted(ctx, nid, titleN, contN, args.optInt("progress", 60));
                            outS = "ok".equals(rN) ? (Words.RP_PROMO_OK + nid) : failR(Words.RP_PROMO_ERR + rN, "notify promoted");
                        } else {
                            rN = Notifier.send(ctx, nid, titleN, contN, ongN);
                            outS = "ok".equals(rN) ? (Words.RP_SEND_OK + nid) : failR(Words.RP_SEND_ERR + rN, "notify send");
                        }
                    }
                    JSONObject result = new JSONObject();
                    result.put("content", content(outS));
                    result.put("isError", false);
                    return ok(id, result);
                }
                if (name.equals("input")) {
                    JSONObject result = new JSONObject();
                    result.put("content", content(humanize("input", VeriService.mcpDispatch("input", argsToKv(args)))));
                    result.put("isError", false);
                    return ok(id, result);
                }
                if (name.equals("tap") || name.equals("swipe") || name.equals("key") || name.equals("ui") || name.equals("ocr") || name.equals("longpress") || name.equals("tap_text") || name.equals("wait_text") || name.equals("wait_stable") || name.equals("scroll") || name.equals("paste") || name.equals("scroll_to_text") || name.equals("clip") || name.equals("texts") || name.equals("do_chain") || name.equals("windows")) {
                    String cmd;
                    if (name.equals("key")) {
                        String k = args.optString("key", "").trim();
                        if (k.equals("back") || k.equals("返回")) cmd = "gback";
                        else if (k.equals("home") || k.equals("主页")) cmd = "ghome";
                        else if (k.equals("recents") || k.equals("最近任务")) cmd = "grecents";
                        else return err(id, -32602, Words.RP_KEY_BAD + String.format(java.util.Locale.ROOT, Words.HP_POINT, "key"));
                    } else if (name.equals("ui")) { cmd = "ui";
                    } else if (name.equals("ocr")) { cmd = "ocr";
                    } else { cmd = name; }
                    String rr = VeriService.mcpDispatch(cmd, argsToKv(args));
                    JSONObject result = new JSONObject();
                    result.put("content", content(humanize(name, rr)));
                    result.put("isError", false);
                    return ok(id, result);
                }
                return err(id, -32602, "unknown tool " + name + String.format(java.util.Locale.ROOT, Words.HP_POINT, "help"));
            }
            return err(id, -32601, "no method " + method);
        } catch (Throwable t) {
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":\"parse error\"}}";
        }
    }

    private static JSONObject objSchema(String[] props, String[] types, String[] descs, String[] required) {
        JSONObject propsO = new JSONObject();
        try {
            for (int i = 0; i < props.length; i++) {
                JSONObject p = new JSONObject();
                p.put("type", types[i]);
                p.put("description", descs[i]);
                propsO.put(props[i], p);
            }
            JSONObject s = new JSONObject();
            s.put("type", "object");
            s.put("properties", propsO);
            if (required != null && required.length > 0) {
                JSONArray r = new JSONArray();
                for (int i = 0; i < required.length; i++) r.put(required[i]);
                s.put("required", r);
            }
            return s;
        } catch (Throwable t) { return propsO; }
    }

    private static Map<String, String> argsToKv(JSONObject args) {
        Map<String, String> m = new HashMap<String, String>();
        try {
            Iterator<String> it = args.keys();
            while (it.hasNext()) {
                String k = it.next();
                Object v = args.opt(k);
                if (v != null) m.put(k, String.valueOf(v));
            }
        } catch (Throwable t) { }
        return m;
    }

    private static JSONObject textBlock(String text) {
        JSONObject o = new JSONObject();
        try { o.put("type", "text"); o.put("text", text); } catch (Throwable t) { }
        return o;
    }

    private static String humanizeOld(String name, String r) {
        try {
            JSONObject o = new JSONObject(r);
            if (name.equals("ui") || name.equals("ocr")) return r;
            if (o.optInt("ok", 0) != 1) return failText(name, o, r);
            if (name.equals("tap")) return String.format(java.util.Locale.ROOT, Words.RP_TAP_FMT, o.optInt("dispatch_ms"));
            if (name.equals("swipe")) return String.format(java.util.Locale.ROOT, Words.RP_SWIPE_FMT, o.optInt("dispatch_ms"));
            if (name.equals("key")) return Words.RP_KEY_OK;
            if (name.equals("input")) {
                String rb = o.optString("readback", "");
                String vv = o.optString("via", "");
                String base = rb.length() > 0
                        ? String.format(java.util.Locale.ROOT, Words.RP_INPUT_FMT, rb, vv)
                        : String.format(java.util.Locale.ROOT, Words.RP_INPUT_FMT2, vv);
                return base + (o.optInt("clip_restored", 0) == 1 ? Words.RP_INPUT_CLIP_BACK : "");
            }
        } catch (Throwable t) { }
        return r;
    }

    private static void addImage(Context ctx, JSONArray content, String rel) {
        try {
            File d = Home.get(ctx);
            if (d == null) return;
            File f = new File(d, rel);
            if (!f.exists()) return;
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b == null) return;
            int w = b.getWidth();
            int maxW = 720;
            if (w > maxW) {
                int nh = (int) ((long) b.getHeight() * maxW / w);
                Bitmap s = Bitmap.createScaledBitmap(b, maxW, nh, true);
                if (s != b) { b.recycle(); b = s; }
            }
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, 70, bo);
            b.recycle();
            byte[] jb = bo.toByteArray();
            if (jb.length > 700000) return;
            JSONObject img = new JSONObject();
            img.put("type", "image");
            img.put("data", Base64.encodeToString(jb, Base64.NO_WRAP));
            img.put("mimeType", "image/jpeg");
            content.put(img);
        } catch (Throwable t) { }
    }

    private static JSONArray content(String text) {
        JSONArray arr = new JSONArray();
        try {
            JSONObject o = new JSONObject();
            o.put("type", "text");
            o.put("text", text);
            arr.put(o);
        } catch (Throwable t) { }
        return arr;
    }

    /** 46 号单：登记进 TOOL_REG（help 与 tools/list 同源，避免两套说明）。 */
    private static JSONObject tool(String name, String desc, JSONObject schema) {
        JSONObject o = new JSONObject();
        try {
            if (desc != null && desc.indexOf("help tool=") < 0)
                desc = desc + String.format(java.util.Locale.ROOT, Words.HP_TOOL_SUFFIX, name);   // ★77.6：短描述末尾指路（不展开全文）
            o.put("name", name);
            o.put("description", desc);
            o.put("inputSchema", schema);
            TOOL_REG.put(name, o);
        } catch (Throwable t) { }
        return o;
    }

    private static String ok(Object id, JSONObject result) {
        try {
            JSONObject o = new JSONObject();
            o.put("jsonrpc", "2.0");
            o.put("id", id);
            o.put("result", result);
            return o.toString();
        } catch (Throwable t) { return "{\"jsonrpc\":\"2.0\",\"id\":null}"; }
    }

    private static String err(Object id, int code, String msg) {
        try {
            JSONObject o = new JSONObject();
            o.put("jsonrpc", "2.0");
            o.put("id", id);
            JSONObject e = new JSONObject();
            e.put("code", code);
            e.put("message", msg);
            o.put("error", e);
            return o.toString();
        } catch (Throwable t) { return "{\"jsonrpc\":\"2.0\",\"id\":null}"; }
    }

    /** 38 号单：shell 工具（退出码原样透出；空输出回 ‹空›；带 ms/timeout，便于区分「真没内容／被杀／超时」）。 */
    private static String execShell(Context ctx, String command) {
        try {
            if (!Shizuku.pingBinder()) {
                try { rikka.shizuku.ShizukuProvider.requestBinderForNonProviderProcess(ctx); } catch (Throwable t) { }
                try { Thread.sleep(150); } catch (Throwable t) { }
                if (!Shizuku.pingBinder()) return failR(Words.RP_SHX_BINDER_ERR, "shizuku binder");
            }
            String r = VeriService.shxRun(command, SHELL_TIMEOUT_MS);
            JSONObject o = new JSONObject(r);
            int okv = o.optInt("ok", 0);
            int exit = o.optInt("exit", -888);
            int timeout = o.optInt("timeout", 0);
            int ms = o.optInt("ms", 0);
            if (timeout == 1) failMark("shell timeout=1");
            else if (exit != 0 && o.optString("err", "").trim().length() > 0) failMark("shell exit=" + exit + "（有 stderr）");   // ★88.7：exit≠0 且 stderr 空＝结果否定，不算失败
            String out = o.optString("out", "");
            String err = o.optString("err", "");
            if (okv != 1) return failR(Words.RP_EXEC_ERR + o.optString("err", "unknown"), "exec ok=0");
            StringBuilder sb = new StringBuilder();
            sb.append("exit=").append(exit);
            if (timeout == 1) sb.append(String.format(java.util.Locale.ROOT, Words.RP_SHELL_TIMEOUT_FMT, SHELL_TIMEOUT_MS / 1000));
            sb.append("  ms=").append(ms).append("\n");
            if (out.length() > 0) { sb.append("--- stdout ---\n").append(out); if (!out.endsWith("\n")) sb.append("\n"); }
            if (err.length() > 0) { sb.append("--- stderr ---\n").append(err); if (!err.endsWith("\n")) sb.append("\n"); }
            if (out.length() == 0 && err.length() == 0) sb.append("--- ").append(Words.RP_SHELL_EMPTY).append(" ---\n");
            return sb.toString();
        } catch (Throwable t) {
            return failR(Words.RP_EXEC_EXC + t, "exec exception");
        }
    }

    public static String selfTest(Context ctx) {
        StringBuilder sb = new StringBuilder();
        int pass = 0, total = 0;
        String token = getToken(ctx);
        total++; String r1 = httpReq(ctx, "bad-token", "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        if (r1.startsWith("HTTP/1.1 401")) { pass++; sb.append("badtoken=401ok "); } else { sb.append("badtoken=[").append(clipHead(r1)).append("] "); }
        total++; String r2 = httpReq(ctx, token, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\",\"clientInfo\":{\"name\":\"selftest\",\"version\":\"1\"}}}");
        if (r2.contains("\"serverInfo\"")) { pass++; sb.append("init=ok "); } else { sb.append("init=[").append(clipHead(r2)).append("] "); }
        total++; String r3 = httpReq(ctx, token, "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/list\",\"params\":{}}");
        if (r3.contains("\"ping\"") && r3.contains("\"shell\"") && r3.contains("\"shot\"") && r3.contains("\"tap\"") && r3.contains("\"swipe\"") && r3.contains("\"key\"") && r3.contains("\"ui\"") && r3.contains("\"input\"") && r3.contains("\"longpress\"") && r3.contains("\"buzz\"")) { pass++; sb.append("toollist=ok "); } else { sb.append("toollist=[").append(clipHead(r3)).append("] "); }
        // 41 号单：确认新工具确实注册上了（/selfcheck 免 token 也能看到这条结论）
        total++; String r3b = httpReq(ctx, token, "{\"jsonrpc\":\"2.0\",\"id\":31,\"method\":\"tools/list\",\"params\":{}}");
        if (r3b.contains("\"download\"") && r3b.contains("\"download_status\"")) { pass++; sb.append("tooldl=ok "); } else { sb.append("tooldl=[").append(clipHead(r3b)).append("] "); }
        // 42 单 C：把已注册的工具全名列出来（助手不必重启 RikkaHub 也能核对「注册了没、叫什么名」）；只作可核对信息，不计入 pass/total。
        sb.append("tlsize=").append(r3.length()).append(" ");
        sb.append("tools=").append(toolNames(r3b)).append(" ");
        total++; String r4 = httpReq(ctx, token, "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"ping\",\"arguments\":{}}}");
        if (r4.contains("hello")) { pass++; sb.append("callping=ok "); } else { sb.append("callping=[").append(clipHead(r4)).append("] "); }
        // 40 单：自检只验「buzz 通路」、不真震（原写法＝每一轮健康巡检都震用户一下，39 单轮询自检时用户实测每 1~2 秒一震）
        total++; String r5 = httpReq(ctx, token, "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"buzz\",\"arguments\":{\"duration_ms\":400,\"dry\":1}}}");
        if (r5.contains("未震动")) { pass++; sb.append("callbuzz=ok "); } else { sb.append("callbuzz=[").append(clipHead(r5)).append("] "); }
        return "{\"ok\":" + (pass == total ? 1 : 0) + ",\"pass\":" + pass + ",\"total\":" + total + ",\"detail\":\"" + sb.toString().replace("\\", "").replace("\"", "'") + "\"}";
    }

    private static String pkgVer(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Throwable t) { return "?"; }
    }

    /** 42 单 C：从 tools/list 的原始回包里抠出工具名清单（抠不到就回「?」）。 */
    private static String toolNames(String raw) {
        try {
            int from = raw.indexOf("\"tools\"");
            if (from < 0) from = 0;
            StringBuilder sb = new StringBuilder();
            int i = from;
            while (true) {
                int k = raw.indexOf("\"name\":\"", i);
                if (k < 0) break;
                int s0 = k + 8;
                int e0 = raw.indexOf('"', s0);
                if (e0 < 0) break;
                String nm = raw.substring(s0, e0);
                if (nm.length() > 0 && nm.length() < 32) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(nm);
                }
                i = e0 + 1;
            }
            return sb.length() > 0 ? sb.toString() : "?";
        } catch (Throwable t) { return "?"; }
    }

    private static String clipHead(String s) {
        if (s == null) return "";
        s = s.replace("\r", " ").replace("\n", " ");
        return s.length() > 60 ? s.substring(0, 60) : s;
    }

    private static String httpReq(Context ctx, String token, String body) {
        Socket s = null;
        try {
            s = new Socket();
            s.connect(new InetSocketAddress("127.0.0.1", getPort(ctx)), 3000);
            s.setSoTimeout(8000);
            byte[] bb = body.getBytes(StandardCharsets.UTF_8);
            String head = "POST /mcp HTTP/1.1\r\nHost: 127.0.0.1\r\n" + getHeaderName(ctx) + ": Bearer " + token
                    + "\r\nX-Reticle-Selftest: 1\r\nContent-Type: application/json\r\nContent-Length: " + bb.length + "\r\nConnection: close\r\n\r\n";
            OutputStream o = s.getOutputStream();
            o.write(head.getBytes(StandardCharsets.UTF_8));
            o.write(bb);
            o.flush();
            ByteArrayOutputStream rb = new ByteArrayOutputStream();
            InputStream in = s.getInputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) > 0) rb.write(buf, 0, r);
            return new String(rb.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "ERR " + t;
        } finally {
            try { if (s != null) s.close(); } catch (Throwable t) { }
        }
    }

    // ═══════════ 38 号单 · 新增：状态可见性 / 免 token 自检口 / 出包环境自检 / 动作自测靶 ═══════════

    /** status 工具正文：一句话给全（版本 / 三件套 / 看门狗 / 前台 / MCP / 日志尾）。 */
    private static String statusText(Context ctx) {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append(Words.RP_ST_TITLE).append(pkgVerVc(ctx)).append("\n");
            boolean in = false;
            try { in = A11yWatchdog.hasSvc(A11yWatchdog.readList(ctx)); } catch (Throwable t) { }
            boolean sw = false;
            try { sw = A11yWatchdog.isOn(ctx); } catch (Throwable t) { }
            int ast = A11yLink.ST_UNKNOWN;
            try { ast = A11yLink.state(ctx); } catch (Throwable t) { }
            sb.append(Words.RP_ST_A11Y).append(in ? Words.RP_ST_IN : Words.RP_ST_OUT)
              .append(Words.RP_ST_CONN)
              .append(ast == A11yLink.ST_CONNECTED ? Words.RP_ST_CONN_OK
                    : ast == A11yLink.ST_IN_LIST_NO_CONN ? Words.RP_ST_CONN_DISC
                    : ast == A11yLink.ST_NOT_IN_LIST ? Words.RP_ST_CONN_NOT : Words.RP_ST_CONN_UNK)
              .append(Words.RP_ST_SW).append(sw ? Words.RP_ST_ON : Words.RP_ST_OFF).append("\n");
            boolean mg = false;
            try { mg = android.os.Environment.isExternalStorageManager(); } catch (Throwable t) { }
            sb.append(Words.RP_ST_MANAGE).append(mg ? Words.RP_ST_ALLOW : Words.RP_ST_DENY).append("\n");
            sb.append(Words.RP_ST_MEM);
            try { sb.append(MemGuard.statusLine(ctx)); } catch (Throwable t) { sb.append(Words.RP_ST_UNKNOWN); }
            sb.append("\n");
            sb.append(Words.RP_ST_WD);
            try { sb.append(A11yWatchdog.statusLine(ctx)); } catch (Throwable t) { sb.append(Words.RP_ST_UNKNOWN); }
            sb.append("\n");
            boolean zb = false, zg = false;
            try {
                zb = Shizuku.pingBinder();
                if (zb) zg = Shizuku.isPreV11() || Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED;
            } catch (Throwable t) { }
            sb.append(Words.RP_ST_SHX).append(zb ? 1 : 0).append(" granted=").append(zg ? 1 : 0).append("\n");
            String fg = "";
            try { fg = VeriService.foregroundPkg(); } catch (Throwable t) { }
            sb.append(Words.RP_ST_FG).append(fg.length() > 0 ? fg : Words.RP_ST_UNKNOWN).append("\n");
            sb.append(Words.RP_ST_MCP).append(getPort(ctx)).append(Words.RP_ST_HNAME).append(getHeaderName(ctx)).append(Words.RP_ST_NOTOKEN).append("\n");
            sb.append(Words.RP_ST_CHK);
            try { sb.append(selfTest(ctx)); } catch (Throwable t) { sb.append(Words.RP_ST_UNKNOWN); }
            sb.append("\n").append(Words.RP_ST_LOG).append("\n").append(tailLog(ctx, 3));
        } catch (Throwable t) { }
        return sb.toString();
    }

    /** 版本名（带 VC）。 */
    private static String pkgVerVc(Context ctx) {
        try {
            android.content.pm.PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName + "（VC" + pi.versionCode + "）";
        } catch (Throwable t) { return "?"; }
    }

    /** 看门狗日志尾 n 行（只读）。 */
    private static String tailLog(Context ctx, int n) {
        try {
            File d = Home.sub(ctx, "logs");
            if (d == null) return "（读日志失败）";
            File f = new File(d, "a11y-watchdog.log");
            if (!f.exists()) return "（还没有日志）";
            byte[] b = new byte[(int) f.length()];
            java.io.FileInputStream fi = new java.io.FileInputStream(f);
            int off = 0, r;
            while (off < b.length && (r = fi.read(b, off, b.length - off)) > 0) off += r;
            fi.close();
            String s = new String(b, 0, off, StandardCharsets.UTF_8);
            String[] lines = s.split("\n");
            StringBuilder sb = new StringBuilder();
            int start = Math.max(0, lines.length - n);
            for (int i = start; i < lines.length; i++) if (lines[i].length() > 0) sb.append(lines[i]).append("\n");
            return sb.length() == 0 ? "（日志为空）" : sb.toString();
        } catch (Throwable t) { return "（读日志失败）"; }
    }

    // ═══════════ 59 号单 · 构建路径配置（去作者环境假设） ═══════════
    // 配置＝「运行目录/build.conf」（主包 /sdcard/Reticle/build.conf；副包自动指 ReticleB）。
    // 行式「键=值」（也认全角＝）；# 或 // 起注释；值可引用 $PROJECT_DIR 等；同名环境变量优先。
    // 未配置／缺省项＝内置默认（＝作者机原路径，作者机行为零变化）。
    private static final String[] CONF_KEYS = new String[]{"PROJECT_DIR", "TOOLCHAIN_DIR", "MATERIAL_DIR", "BUILD_SH", "INSTALL_SH", "TMP_DIR"};

    private static String confDefault(String k) {
        if (k.equals("PROJECT_DIR")) return "/sdcard/Reticle/工程";
        if (k.equals("TOOLCHAIN_DIR")) return "$PROJECT_DIR/工具链";
        if (k.equals("MATERIAL_DIR")) return "$PROJECT_DIR/物料";
        if (k.equals("BUILD_SH")) return "$PROJECT_DIR/工具链/build2.sh";
        if (k.equals("INSTALL_SH")) return "$PROJECT_DIR/工具/装工具.sh";
        if (k.equals("TMP_DIR")) return "/data/local/tmp";
        return "";
    }

    private static String confFile(Context ctx) { return Home.get(ctx).getAbsolutePath() + "/build.conf"; }

    /** 拼路径（去尾斜杠，防双斜杠）。 */
    private static String pj(String a, String b) {
        if (a == null) a = "";
        while (a.length() > 1 && a.endsWith("/")) a = a.substring(0, a.length() - 1);
        return a + "/" + b;
    }

    /** 逐行读 build.conf（行式 键=值）＋环境变量覆盖＋$引用展开。 */
    private static java.util.LinkedHashMap<String, String> buildConf(Context ctx) {
        java.util.LinkedHashMap<String, String> m = new java.util.LinkedHashMap<String, String>();
        for (int i = 0; i < CONF_KEYS.length; i++) m.put(CONF_KEYS[i], confDefault(CONF_KEYS[i]));
        try {
            File f = new File(confFile(ctx));
            if (f.exists()) {
                byte[] b = new byte[(int) f.length()];
                java.io.FileInputStream fi = new java.io.FileInputStream(f);
                int off = 0, r;
                while (off < b.length && (r = fi.read(b, off, b.length - off)) > 0) off += r;
                fi.close();
                String[] lines = new String(b, 0, off, StandardCharsets.UTF_8).split("\n");
                for (int i = 0; i < lines.length; i++) {
                    String ln = lines[i].trim();
                    if (ln.length() == 0 || ln.startsWith("#") || ln.startsWith("//")) continue;
                    int e = ln.indexOf('=');
                    int e2 = ln.indexOf('＝');
                    if (e2 >= 0 && (e < 0 || e2 < e)) e = e2;
                    if (e <= 0) continue;
                    String k = ln.substring(0, e).trim();
                    String v = ln.substring(e + 1).trim();
                    if (k.matches("[A-Za-z_][A-Za-z0-9_]*")) m.put(k, v);
                }
            }
        } catch (Throwable t) { }
        for (int i = 0; i < CONF_KEYS.length; i++) {
            String ev = null;
            try { ev = System.getenv(CONF_KEYS[i]); } catch (Throwable t) { }
            if (ev != null && ev.length() > 0) m.put(CONF_KEYS[i], ev);
        }
        for (int round = 0; round < 3; round++) {
            boolean changed = false;
            java.util.Iterator<java.util.Map.Entry<String, String>> it = m.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<String, String> en = it.next();
                String nv = confExpand(en.getValue(), m);
                if (!nv.equals(en.getValue())) { en.setValue(nv); changed = true; }
            }
            if (!changed) break;
        }
        return m;
    }

    /** $KEY／${KEY} → 配置值（键不存在则原样保留）。 */
    private static String confExpand(String v, java.util.Map<String, String> m) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < v.length()) {
            char c = v.charAt(i);
            if (c == '$' && i + 1 < v.length()) {
                int j = i + 1;
                if (v.charAt(j) == '{') {
                    int k = v.indexOf('}', j);
                    if (k > j) {
                        String rv = m.get(v.substring(j + 1, k));
                        if (rv != null) { sb.append(rv); i = k + 1; continue; }
                    }
                } else if (Character.isLetterOrDigit(v.charAt(j)) || v.charAt(j) == '_') {
                    int k = j;
                    while (k < v.length() && (Character.isLetterOrDigit(v.charAt(k)) || v.charAt(k) == '_')) k++;
                    String rv = m.get(v.substring(j, k));
                    if (rv != null) { sb.append(rv); i = k; continue; }
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /** 出包前环境自检：缺什么点名什么（空串＝齐）。59 号单：全部来自 build.conf（默认＝作者机原路径）。 */
    private static String envMissing(Context ctx) {
        java.util.LinkedHashMap<String, String> m = buildConf(ctx);
        String tc = m.get("TOOLCHAIN_DIR"), sb = m.get("PROJECT_DIR"), kp = m.get("MATERIAL_DIR");
        StringBuilder miss = new StringBuilder();
        String[] must = new String[]{
            pj(tc, "bin/aapt2"),
            pj(tc, "lib/android.jar"),
            pj(tc, "dex/ecj.dex"),
            pj(tc, "dex/d8.dex"),
            pj(tc, "dex/d8-2.dex"),
            pj(tc, "dex/apksigner.dex"),
            pj(tc, "dex/buildtool.dex"),
            pj(tc, "ecj-res"),
            pj(sb, "AndroidManifest.xml"),
            pj(sb, "src"),
            pj(sb, "res")
        };
        String[] hint = new String[]{
            "工具链·aapt2（Termux 可装，需带同目录 lib*.so）",
            "工具链·android.jar（SDK platform-35）",
            "工具链·ECJ 3.13.x 转的 dex",
            "工具链·d8（R8）转的 dex",
            "工具链·d8 拆出的第二份 dex",
            "工具链·apksigner.jar 转的 dex",
            "工具链·工程自带 工具/BuildTool.java 编译转的 dex",
            "工具链·ECJ 资源目录（properties／*.rsc）",
            "源码树（build.conf 的 PROJECT_DIR= 指到你的）",
            "源码树同上",
            "源码树同上"
        };
        for (int i = 0; i < must.length; i++) { if (!new File(must[i]).exists()) miss.append("· 缺 ").append(must[i]).append("（").append(hint[i]).append("）\n"); }
        String bsh = m.get("BUILD_SH");
        if (!new File(bsh).exists()) miss.append("· 缺 出包脚本 ").append(bsh).append("（工程 工具链/build2.sh；路径可改 build.conf 的 BUILD_SH=）\n");
        String[] jars = new String[]{"core-1.9.0.jar", "appcompat-1.6.1.jar", "material-1.9.0.jar", "liquidglass-2.0.11.jar", "vision-common-17.3.0.jar", "play-services-tasks-18.2.0.jar", "kotlin-stdlib-2.0.21.jar"};
        for (int i = 0; i < jars.length; i++) {
            String jp = pj(pj(kp, "classes"), jars[i]);
            if (!new File(jp).exists()) miss.append("· 缺 ").append(jp).append("（第三方 jar，从各上游获取；见 BUILD.md 第 2 节）\n");
        }
        return miss.toString();
    }

    /** install 可用性：装机脚本在不在（不在＝明确不可用＋缺什么）。 */
    private static String installMissing(Context ctx) {
        java.util.LinkedHashMap<String, String> m = buildConf(ctx);
        String ish = m.get("INSTALL_SH");
        if (!new File(ish).exists()) return "· 缺 装机脚本 " + ish + "（工程 工具/装工具.sh；路径可改 build.conf 的 INSTALL_SH=）\n";
        return "";
    }

    /** build action=check：一眼看清「就绪／缺哪几个／从哪拿」。 */
    private static String envReport(Context ctx) {
        StringBuilder sb = new StringBuilder();
        java.util.LinkedHashMap<String, String> m = buildConf(ctx);
        String f = confFile(ctx);
        sb.append(Words.RP_CHK_HEAD).append("\n");
        boolean hasConf = new File(f).exists();
        sb.append(Words.RP_CHK_CONF).append(f).append(hasConf ? Words.RP_CHK_CONF_YES : Words.RP_CHK_CONF_NO).append("\n");
        sb.append("  PROJECT_DIR=").append(m.get("PROJECT_DIR")).append("\n");
        sb.append("  TOOLCHAIN_DIR=").append(m.get("TOOLCHAIN_DIR")).append("\n");
        sb.append("  MATERIAL_DIR=").append(m.get("MATERIAL_DIR")).append("\n");
        sb.append("  BUILD_SH=").append(m.get("BUILD_SH")).append("\n");
        sb.append("  INSTALL_SH=").append(m.get("INSTALL_SH")).append("\n");
        sb.append("  TMP_DIR=").append(m.get("TMP_DIR")).append("\n");
        String miss = envMissing(ctx);
        if (miss.length() == 0) sb.append(Words.RP_CHK_OK).append("\n");
        else sb.append(Words.RP_CHK_MISS).append("\n").append(miss).append(Words.RP_BUILD_WHERE);
        String imiss = installMissing(ctx);
        if (imiss.length() == 0) sb.append(Words.RP_CHK_INSTALL_OK).append("\n");
        else sb.append(Words.RP_CHK_INSTALL_MISS).append("\n").append(imiss).append(Words.RP_BUILD_WHERE);
        for (int i = 0; i < CONF_KEYS.length; i++) {
            String v = m.get(CONF_KEYS[i]);
            if (v.indexOf('\'') >= 0 || v.indexOf('\n') >= 0) sb.append(String.format(java.util.Locale.ROOT, Words.RP_CHK_QUOTE, CONF_KEYS[i])).append("\n");
        }
        return sb.toString();
    }

    /** 免 token、只读、只本机的自检摘要（GET /selfcheck）。不泄露 token、不暴露任何写/执行动作。 */
    private static String selfCheckJson(Context ctx) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", 1);
            o.put("ver", pkgVerVc(ctx));
            o.put("a11y_in_list", A11yWatchdog.hasSvc(A11yWatchdog.readList(ctx)) ? 1 : 0);
            o.put("a11y_connected", A11yLink.isConnected() ? 1 : 0);
            o.put("a11y_probe", A11yLink.probe);
            o.put("a11y_state", A11yLink.state(ctx));
            o.put("a11y_link", A11yLink.detailLine());
            o.put("a11y_switch", A11yWatchdog.isOn(ctx) ? 1 : 0);
            boolean mg = false;
            try { mg = android.os.Environment.isExternalStorageManager(); } catch (Throwable t) { }
            o.put("manage_storage", mg ? 1 : 0);
            o.put("foreground", VeriService.foregroundPkg());
            o.put("watchdog", A11yWatchdog.statusLine(ctx));
            o.put("mem", MemGuard.statusLine(ctx));   // 52 号单：内存自保当前档位
            o.put("mem_lv", MemGuard.level);
            o.put("selftest", selfTest(ctx));
            o.put("ts", System.currentTimeMillis());
            return o.toString();
        } catch (Throwable t) { return "{\"ok\":0,\"err\":\"selfcheck failed\"}"; }
    }

    /** 动作自测靶：在 Reticle 自家小窗口上依次试 tap／长按／输入／滑动／返回，并回报每步回话（走与真机同一条 humanize 路径）。 */
    private static String actSelfTest(Context ctx) {
        StringBuilder sb = new StringBuilder("动作自测靶（Reticle 自家小窗口；会短暂占用前台）\n");
        try {
            SelfTestActivity.open(ctx);
            Thread.sleep(1600);
            if (!SelfTestActivity.ready) return sb.append("！窗口没起来（可重试一次；仍不行＝被系统拦）").toString();
            JSONObject a1 = new JSONObject(); a1.put("x", SelfTestActivity.btnX); a1.put("y", SelfTestActivity.btnY);
            sb.append("· tap       → ").append(humanize("tap", VeriService.mcpDispatch("tap", argsToKv(a1)))).append("\n");
            Thread.sleep(400);
            JSONObject a2 = new JSONObject(); a2.put("x", SelfTestActivity.btnX); a2.put("y", SelfTestActivity.btnY); a2.put("ms", 900);
            sb.append("· longpress → ").append(humanize("longpress", VeriService.mcpDispatch("longpress", argsToKv(a2)))).append("\n");
            Thread.sleep(400);
            JSONObject a3 = new JSONObject(); a3.put("text", "reticle-selftest");
            sb.append("· input     → ").append(humanize("input", VeriService.mcpDispatch("input", argsToKv(a3)))).append("\n");
            Thread.sleep(500);
            JSONObject a4 = new JSONObject();
            a4.put("x1", SelfTestActivity.w / 2); a4.put("y1", SelfTestActivity.top + 30);
            a4.put("x2", SelfTestActivity.w / 2); a4.put("y2", SelfTestActivity.top + 130); a4.put("ms", 300);
            sb.append("· swipe     → ").append(humanize("swipe", VeriService.mcpDispatch("swipe", argsToKv(a4)))).append("\n");
            Thread.sleep(400);
            JSONObject a5 = new JSONObject(); a5.put("key", "back");
            sb.append("· key(back) → ").append(humanize("key", VeriService.mcpDispatch("gback", argsToKv(a5)))).append("\n");
            Thread.sleep(600);
            sb.append("靶自证：点击计数=").append(SelfTestActivity.tapCount).append("（期望≥1）｜输入框=")
              .append(SelfTestActivity.typed).append("（期望 reticle-selftest）｜窗口已关=")
              .append(SelfTestActivity.finished ? "是" : "否");
        } catch (Throwable t) {
            sb.append("！异常：").append(t);
        }
        return sb.toString();
    }

    // ═══════════ 44 号单（2026-10-11）· 工具回话的人话化（点完说清"发生了什么"）═══════════
    private static String viaCn(String via) {
        if (via == null) return "";
        if (via.equals("action_click_ancestor")) return Words.RP_VIA_ANCESTOR;
        if (via.equals("action_click")) return Words.RP_VIA_ACTION;
        if (via.equals("gesture_center")) return Words.RP_VIA_GESTURE;
        if (via.equals("gesture_ancestor_center")) return Words.RP_VIA_GESTURE_ANC;
        if (via.equals("ocr_center")) return Words.RP_VIA_OCR;
        return via;
    }

    private static String humanize(String name, String r) {
        try {
            JSONObject o = new JSONObject(r);
            if (name.equals("ui")) return r;
            if (name.equals("clip") || name.equals("texts")) return r;
            if (name.equals("ocr")) {
                if (o.optInt("ok", 0) != 1) return r;
                String tx = o.optString("text", "");
                String[] ls = tx.split("\n");
                int n = 0;
                for (int i = 0; i < ls.length; i++) if (ls[i].trim().length() > 0) n++;
                return String.format(java.util.Locale.ROOT, Words.RP_OCR_HUMAN_FMT, n) + r;
            }
            if (name.equals("windows")) return r;
            if (name.equals("paste")) {
                if (o.optInt("ok", 0) != 1) return failText(name, o, r);
                String vv = o.optString("via", "");
                String vcn = vv.equals("action_paste") ? Words.RP_VIA_PASTE_NODE
                        : (vv.indexOf("menu_paste") == 0 ? Words.RP_VIA_PASTE_MENU
                        : (vv.indexOf("keycode") == 0 ? Words.RP_VIA_PASTE_KEY : vv));
                String clipBack = o.optInt("clip_restored", 0) == 1 ? Words.RP_INPUT_CLIP_BACK
                        : (o.optInt("clip_restored", 0) == 3 ? Words.RP_PASTE_CLIP_UNKNOWN : "");
                return String.format(java.util.Locale.ROOT, Words.RP_PASTE_FMT2, vcn, clipBack);
            }
            if (name.equals("scroll_to_text")) {
                if (o.optInt("found", 0) != 1) return failText(name, o, r);
                if (o.optInt("ok", 0) != 1) return failText(name, o, r);   // ★77.6：找到了但内嵌 tap 没成（含 expect 未满足）
                String tail = o.has("tap") ? Words.RP_SCROLLTO_TAPPED : Words.RP_SCROLLTO_NOTAP;
                return String.format(java.util.Locale.ROOT, Words.RP_SCROLLTO_FMT, o.optInt("screens"), o.optString("target_text", ""), viaCn(o.optString("via", "")), tail);
            }
            if (name.equals("do_chain")) {
                if (o.optInt("ok", 0) == 1) {
                    String okLine = String.format(java.util.Locale.ROOT, Words.RP_CHAIN_FMT, o.optInt("steps_done"), o.optInt("ms"));
                    JSONObject okSc = o.optJSONObject("screen");
                    if (okSc == null) return okLine;
                    String okT = "";
                    JSONArray okA = okSc.optJSONArray("texts");
                    if (okA != null) for (int i = 0; i < okA.length() && i < 6; i++) okT += okA.optString(i) + " / ";
                    return okLine + String.format(java.util.Locale.ROOT, Words.RP_CHAIN_SCREEN_FMT, okSc.optString("pkg", ""), okT);
                }
                int cStop = o.optInt("stop_at", 0);
                int cDone = o.optInt("steps_done", 0);
                JSONObject sc = o.optJSONObject("screen");
                String szt = "";
                if (sc != null) {
                    JSONArray arr = sc.optJSONArray("texts");
                    if (arr != null) for (int i = 0; i < arr.length() && i < 6; i++) szt += arr.optString(i) + " / ";
                }
                if (cStop > 0) {   // 停在第几步：说清第几步、为什么
                    failMark("do_chain ok=0 停在第" + cStop + "步 act=" + o.optString("stop_act", "") + " why=" + o.optString("why", ""));   // ★78：审计带「停在哪步／为什么」
                    return String.format(java.util.Locale.ROOT, Words.RP_CHAIN_STOP_FMT, cStop,
                            o.optString("why", ""), sc == null ? "" : sc.optString("pkg", ""), szt) + String.format(java.util.Locale.ROOT, Words.HP_POINT, "do_chain");
                }
                if (cDone > 0) {   // ★82：stop_on_error=0 跑完了但不成立——别再说「停在第 0 步」
                    String rz = o.optString("reason", "");
                    String rzcn = rz.equals("expect_timeout") ? "后置条件超时" : (rz.equals("act_failed") ? "动作失败" : rz);
                    failMark("do_chain ok=0 跑完但不成立 已跑" + cDone + "步 没过" + o.optInt("failed_steps", 0) + "步 reason=" + rz);
                    return String.format(java.util.Locale.ROOT, Words.RP_CHAIN_PART_FMT, cDone, o.optInt("failed_steps", 0), rzcn)
                            + (sc == null ? "" : ("当时前台 " + sc.optString("pkg", "") + "，屏上文字：" + szt))
                            + String.format(java.util.Locale.ROOT, Words.HP_POINT, "do_chain");
                }
                String nrw = o.optString("why", "");   // ★82：一步都没跑（被拒／steps 缺失或不是数组）——不许写成「停在第 0 步」
                if (nrw.length() == 0) nrw = o.optString("err", "");
                if (nrw.length() == 0) nrw = "没说原因";
                failMark("do_chain ok=0 整条链被拒（一步都没跑）" + nrw);
                return String.format(java.util.Locale.ROOT, Words.RP_CHAIN_NORUN_FMT, nrw) + String.format(java.util.Locale.ROOT, Words.HP_POINT, "do_chain");
            }
            if (name.equals("tap_text")) {
                if (o.optInt("ok", 0) != 1) return failText(name, o, r);
                JSONObject tg = o.optJSONObject("target");
                String what = "";
                if (tg != null) {
                    what = tg.optString("t", "");
                    if (what.length() == 0) what = tg.optString("d", "");
                }
                String tail;
                if (o.optInt("changed", 0) == 1) {
                    if (o.optInt("expect_hit", 0) == 1)
                        tail = String.format(java.util.Locale.ROOT, Words.RP_TT_TAIL_EXPECT, o.optString("expect", ""), o.optInt("after_ms"));
                    else
                        tail = String.format(java.util.Locale.ROOT, Words.RP_TT_TAIL_CHANGED, o.optInt("after_ms"));
                } else {
                    tail = Words.RP_TT_TAIL_NOCHANGE;
                }
                return String.format(java.util.Locale.ROOT, Words.RP_TT_FMT, what, viaCn(o.optString("via", "")), tail);
            }
            if (name.equals("scroll")) {
                if (o.optInt("ok", 0) != 1) return failText(name, o, r);
                String tail = o.optInt("changed", 0) == 1
                        ? String.format(java.util.Locale.ROOT, Words.RP_TT_TAIL_CHANGED, o.optInt("after_ms"))
                        : Words.RP_TT_TAIL_NOCHANGE;
                return String.format(java.util.Locale.ROOT, Words.RP_SCROLL_FMT, o.optString("direction", ""), o.optInt("dispatch_ms"), tail);
            }
            if (name.equals("wait_text")) {
                if (o.optInt("ok", 0) != 1) return failText(name, o, r);
                JSONObject nd = o.optJSONObject("node");
                String t = "";
                if (nd != null) {
                    t = nd.optString("t", "");
                    if (t.length() == 0) t = nd.optString("d", "");
                }
                return String.format(java.util.Locale.ROOT, Words.RP_WAIT_OK_FMT, o.optString("state", ""), t, o.optString("via", ""), o.optInt("ms"));
            }
            if (name.equals("wait_stable")) {
                if (o.optInt("ok", 0) != 1) return failText(name, o, r);
                return String.format(java.util.Locale.ROOT, Words.RP_STABLE_OK_FMT, o.optInt("stable_ms"), o.optInt("ms"));
            }
            if (o.optInt("ok", 0) != 1) return failText(name, o, r);
            if (name.equals("tap")) {
                String tail = o.optInt("changed", 0) == 1
                        ? String.format(java.util.Locale.ROOT, Words.RP_TAP_TAIL_CHANGED, o.optInt("after_ms"))
                        : Words.RP_TAP_TAIL_NOCHANGE;
                JSONArray at = o.optJSONArray("at");
                int px = 0, py = 0;
                if (at != null && at.length() >= 2) { px = at.optInt(0); py = at.optInt(1); }
                String pk = o.optString("pkg_after", "");
                if (pk.length() == 0) pk = "未知";
                return String.format(java.util.Locale.ROOT, Words.RP_TAP_FMT2, px, py, pk, o.optInt("dispatch_ms"), tail);
            }
            if (name.equals("swipe")) return String.format(java.util.Locale.ROOT, Words.RP_SWIPE_FMT, o.optInt("dispatch_ms"));
            if (name.equals("key")) return Words.RP_KEY_OK;
            if (name.equals("input")) {
                String rb = o.optString("readback", "");
                String vv = o.optString("via", "");
                int nw = o.optInt("len", rb.length());
                String base = rb.length() > 0
                        ? String.format(java.util.Locale.ROOT, Words.RP_INPUT_FMT, nw, rb, vv)
                        : String.format(java.util.Locale.ROOT, Words.RP_INPUT_FMT2, nw, vv);
                return base + (o.optInt("clip_restored", 0) == 1 ? Words.RP_INPUT_CLIP_BACK : "");
            }
            if (name.equals("longpress")) return String.format(java.util.Locale.ROOT, Words.RP_LONGPRESS_FMT, o.optInt("dispatch_ms"));
        } catch (Throwable t) { }
        return r;
    }

    /** 74 号单：当前请求的上下文（tools/list 的动态状态要用；每次请求刷新）。 */
    private static volatile Context S_CTX = null;

    /** 74 号单：build 的描述**动态带状态**（上锁但对 AI 也可见＝缺什么、怎么补都写在描述里）。 */
    /** 77 号单：工具全文（Words 里的 TD_<名字>，全大写）；没有就退回 tools/list 那一行。 */
    private static String longDesc(String key, String fallback) {
        try {
            java.lang.reflect.Field f = Words.class.getField("TD_" + key.toUpperCase(java.util.Locale.ROOT));
            Object v = f.get(null);
            if (v instanceof String && ((String) v).length() > 0) return (String) v;
        } catch (Throwable t) { }
        return fallback;
    }

    private static String buildDesc(Context c) {
        if (c == null) return Words.TL_BUILD;
        try {
            if (BuildEnv.ready(c)) return Words.TL_BUILD + Words.TL_BUILD_WHERE + " ｜ 环境就绪（工具链内置＋android.jar 已 sha256 校验）：可直接 action=new 摊工程，或 action=start 出包。";
            String miss = BuildEnv.missing(c).replace("\n", " ").trim();
            return Words.TL_BUILD + Words.TL_BUILD_WHERE + " ｜ ★环境未就绪，还不能出包 —— " + miss
                 + " 补法：①用户在应用里点『打包 App → 一键准备环境』（弹窗里会写为什么、多大、从哪来）；②或调 build action=env 让 Reticle 自己下（官方 dl.google.com，下完自动校验）。";
        } catch (Throwable t) { return Words.TL_BUILD; }
    }

    /** 46 号单：工具注册表——tools/list 与 help 共用这一处（避免里外两套说明）。 */
    private static JSONArray buildTools() {
        JSONArray tools = new JSONArray();
        try {
        JSONObject ssh = new JSONObject();
        ssh.put("type", "string");
        ssh.put("description", Words.TL_SHELL_CMD);
        JSONObject props = new JSONObject();
        props.put("command", ssh);
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        schema.put("properties", props);
        JSONArray req2 = new JSONArray();
        req2.put("command");
        schema.put("required", req2);
        tools.put(tool("ping", Words.TL_PING, new JSONObject()));
        tools.put(tool("shell", Words.TL_SHELL, schema));
        tools.put(tool("shot", Words.TL_SHOT, objSchema(new String[]{}, new String[]{}, new String[]{}, null)));
        tools.put(tool("tap", Words.TL_TAP, objSchema(new String[]{"x", "y", "ms", "expect_pkg"}, new String[]{"number", "number", "number", "string"}, new String[]{Words.TL_TAP_X, Words.TL_TAP_Y, Words.TL_TAP_MS, Words.TL_ACT_PKG}, new String[]{"x", "y"})));
        tools.put(tool("swipe", Words.TL_SWIPE, objSchema(new String[]{"x1", "y1", "x2", "y2", "ms", "expect_pkg"}, new String[]{"number", "number", "number", "number", "number", "string"}, new String[]{Words.TL_SWIPE_X1, Words.TL_SWIPE_Y1, Words.TL_SWIPE_X2, Words.TL_SWIPE_Y2, Words.TL_SWIPE_MS, Words.TL_ACT_PKG}, new String[]{"x1", "y1", "x2", "y2"})));
        tools.put(tool("key", Words.TL_KEY, objSchema(new String[]{"key", "expect_pkg"}, new String[]{"string", "string"}, new String[]{Words.TL_KEY_VALUE, Words.TL_ACT_PKG}, new String[]{"key"})));
        tools.put(tool("ui", Words.TL_UI, objSchema(new String[]{"max"}, new String[]{"number"}, new String[]{Words.TL_UI_MAX}, null)));
        tools.put(tool("ocr", Words.TL_OCR, objSchema(new String[]{"region", "src"}, new String[]{"string", "string"}, new String[]{Words.TL_OCR_REGION, Words.TL_OCR_SRC}, null)));
        tools.put(tool("input", Words.TL_INPUT, objSchema(new String[]{"text", "x", "y", "expect_pkg", "duration"}, new String[]{"string", "number", "number", "string", "number"}, new String[]{Words.TL_INPUT_TEXT, Words.TL_INPUT_X, Words.TL_INPUT_Y, Words.TL_INPUT_PKG, Words.TL_TAP_TEXT_DURATION}, new String[]{"text"})));
        tools.put(tool("longpress", Words.TL_LONGPRESS, objSchema(new String[]{"x", "y", "ms", "expect_pkg"}, new String[]{"number", "number", "number", "string"}, new String[]{Words.TL_TAP_X, Words.TL_TAP_Y, Words.TL_LONGPRESS_MS, Words.TL_ACT_PKG}, new String[]{"x", "y"})));
        tools.put(tool("notify", Words.TL_NOTIFY, objSchema(new String[]{"title", "content", "id", "ongoing", "clear", "island", "op", "promoted", "progress", "report"}, new String[]{"string", "string", "number", "number", "number", "string", "number", "number", "number", "string"}, new String[]{Words.TL_NOTIFY_TITLE, Words.TL_NOTIFY_CONTENT, Words.TL_NOTIFY_ID, Words.TL_NOTIFY_ONGOING, Words.TL_NOTIFY_CLEAR, Words.TL_NOTIFY_ISLAND, Words.TL_NOTIFY_OP, Words.TL_NOTIFY_PROMOTED, Words.TL_NOTIFY_PROGRESS, Words.TL_NOTIFY_REPORT}, null)));
        tools.put(tool("build", buildDesc(S_CTX), objSchema(new String[]{"action", "name", "pkg"}, new String[]{"string", "string", "string"}, new String[]{Words.TL_BUILD_ACTION, Words.TL_BUILD_NAME, Words.TL_BUILD_PKG}, null)));
        tools.put(tool("download", Words.TL_DOWNLOAD, objSchema(new String[]{"url", "dir", "name", "sha256", "headers", "unzip", "force"}, new String[]{"string", "string", "string", "string", "string", "number", "number"}, new String[]{Words.TL_DOWNLOAD_URL, Words.TL_DOWNLOAD_DIR, Words.TL_DOWNLOAD_NAME, Words.TL_DOWNLOAD_SHA, Words.TL_DOWNLOAD_HEADERS, Words.TL_DOWNLOAD_UNZIP, Words.TL_DOWNLOAD_FORCE}, new String[]{"url"})));
        tools.put(tool("download_status", Words.TL_DOWNLOAD_STATUS, objSchema(new String[]{"id"}, new String[]{"string"}, new String[]{Words.TL_DOWNLOAD_STATUS_ID}, null)));
        tools.put(tool("buzz", Words.TL_BUZZ, objSchema(new String[]{"duration_ms", "dry"}, new String[]{"number"}, new String[]{Words.TL_BUZZ_MS, Words.TL_BUZZ_DRY}, null)));
        tools.put(tool("status", Words.TL_STATUS, objSchema(new String[]{}, new String[]{}, new String[]{}, null)));
        tools.put(tool("help", Words.TL_HELP, objSchema(new String[]{}, new String[]{}, new String[]{}, null)));
        tools.put(tool("acttest", Words.TL_ACTTEST, objSchema(new String[]{}, new String[]{}, new String[]{}, null)));
        tools.put(tool("tap_text", Words.TL_TAP_TEXT, objSchema(new String[]{"text", "desc", "id", "index", "timeout_ms", "duration", "exact", "expect", "expect_pkg", "ocr", "wait_ms"}, new String[]{"string", "string", "string", "number", "number", "number", "number", "string", "string", "number", "number"}, new String[]{Words.TL_TAP_TEXT_TEXT, Words.TL_TAP_TEXT_DESC, Words.TL_TAP_TEXT_ID, Words.TL_TAP_TEXT_INDEX, Words.TL_TAP_TEXT_TIMEOUT, Words.TL_TAP_TEXT_DURATION, Words.TL_TAP_TEXT_EXACT, Words.TL_TAP_TEXT_EXPECT, Words.TL_TAP_TEXT_EXPECT_PKG, Words.TL_TAP_TEXT_OCR, Words.TL_TAP_TEXT_WAIT}, null)));
        tools.put(tool("wait_text", Words.TL_WAIT_TEXT, objSchema(new String[]{"text", "desc", "id", "state", "timeout_ms"}, new String[]{"string", "string", "string", "string", "number"}, new String[]{Words.TL_WAIT_TEXT_TEXT, Words.TL_TAP_TEXT_DESC, Words.TL_TAP_TEXT_ID, Words.TL_WAIT_TEXT_STATE, Words.TL_WAIT_TEXT_TIMEOUT}, null)));
        tools.put(tool("wait_stable", Words.TL_WAIT_STABLE, objSchema(new String[]{"quiet_ms", "timeout_ms"}, new String[]{"number", "number"}, new String[]{Words.TL_WAIT_STABLE_QUIET, Words.TL_WAIT_STABLE_TIMEOUT}, null)));
        tools.put(tool("scroll", Words.TL_SCROLL, objSchema(new String[]{"direction", "distance", "expect_pkg", "duration"}, new String[]{"string", "number", "string", "number"}, new String[]{Words.TL_SCROLL_DIR, Words.TL_SCROLL_DIST, Words.TL_SCROLL_PKG, Words.TL_TAP_TEXT_DURATION}, null)));
        tools.put(tool("paste", Words.TL_PASTE, objSchema(new String[]{"text", "x", "y", "restore", "expect_pkg"}, new String[]{"string", "number", "number", "number", "string"}, new String[]{Words.TL_PASTE_TEXT, Words.TL_PASTE_X, Words.TL_PASTE_Y, Words.TL_PASTE_RESTORE, Words.TL_PASTE_PKG}, new String[]{"text"})));
        tools.put(tool("scroll_to_text", Words.TL_SCROLL_TO_TEXT, objSchema(new String[]{"text", "desc", "id", "exact", "index", "direction", "max_scroll", "timeout_ms", "tap", "expect", "expect_pkg", "ocr"}, new String[]{"string", "string", "string", "number", "number", "string", "number", "number", "number", "string", "string", "number"}, new String[]{Words.TL_SCROLLTO_TEXT, Words.TL_SCROLLTO_DESC, Words.TL_SCROLLTO_ID, Words.TL_SCROLLTO_EXACT, Words.TL_SCROLLTO_INDEX, Words.TL_SCROLLTO_DIR, Words.TL_SCROLLTO_MAX, Words.TL_SCROLLTO_TIMEOUT, Words.TL_SCROLLTO_TAP, Words.TL_TAP_TEXT_EXPECT, Words.TL_SCROLLTO_PKG, Words.TL_TAP_TEXT_OCR}, null)));
        tools.put(tool("do_chain", Words.TL_DO_CHAIN, objSchema(new String[]{"steps", "stop_on_error", "observe"}, new String[]{"string", "number", "number"}, new String[]{Words.TL_DO_CHAIN_STEPS, Words.TL_DO_CHAIN_STOP, Words.TL_DO_CHAIN_OBSERVE}, new String[]{"steps"})));
        tools.put(tool("windows", Words.TL_WINDOWS, objSchema(new String[]{}, new String[]{}, new String[]{}, null)));
        tools.put(tool("clip", Words.TL_CLIP, objSchema(new String[]{}, new String[]{}, new String[]{}, null)));
        tools.put(tool("texts", Words.TL_TEXTS, objSchema(new String[]{"max"}, new String[]{"number"}, new String[]{Words.TD_TEXTS_MAX}, null)));
        } catch (Throwable t) { }
        return tools;
    }

    // ═══════════ 46 号单：help 精简分层（无参＝目录；tool=<名>＝单工具详情） ═══════════
    /** 确保工具表已建（help 可能在 tools/list 之前被调用）。 */
    private static void ensureTools() {
        try { buildTools(); } catch (Throwable t) { }
    }

    /** help 正文：不填 tool＝目录；填了＝该工具详情（说明＋参数）；名字不对＝点名并附目录。 */
    private static String helpText(String want) {
        ensureTools();
        String w = want == null ? "" : want.trim();
        if (w.length() == 0) return indexText();
        JSONObject t = TOOL_REG.get(w);
        if (t == null) return String.format(java.util.Locale.ROOT, Words.HP_TOOL_NONE, w) + indexText();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(java.util.Locale.ROOT, Words.HP_TOOL_HEAD, w, longDesc(w, t.optString("description", ""))));
        JSONObject sc = t.optJSONObject("inputSchema");
        JSONObject props = sc == null ? null : sc.optJSONObject("properties");
        if (props == null || props.length() == 0) { sb.append(Words.HP_TOOL_NOARG); return sb.toString(); }
        String req = ",";
        JSONArray ra = sc.optJSONArray("required");
        if (ra != null) for (int i = 0; i < ra.length(); i++) req = req + ra.optString(i) + ",";
        sb.append(Words.HP_TOOL_ARGS);
        Iterator<String> it = props.keys();
        while (it.hasNext()) {
            String k = it.next();
            JSONObject p = props.optJSONObject(k);
            sb.append(String.format(java.util.Locale.ROOT, Words.HP_TOOL_ARG, k,
                    longDesc(w + "_" + k, p == null ? "" : p.optString("description", "")),
                    req.indexOf("," + k + ",") >= 0 ? Words.HP_TOOL_REQ : ""));
        }
        return sb.toString();
    }

    /** 目录：一行一个工具（名字 ＋ 半句用途），末尾给「怎么取单个工具」与两个旁路口。 */
    private static String indexText() {
        ensureTools();
        StringBuilder sb = new StringBuilder(Words.HP_FIRST);
        sb.append(String.format(java.util.Locale.ROOT, Words.HP_INDEX_HEAD, TOOL_REG.size()));
        Iterator<java.util.Map.Entry<String, JSONObject>> it = TOOL_REG.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<String, JSONObject> e = it.next();
            sb.append(String.format(java.util.Locale.ROOT, Words.HP_ITEM_LINE, e.getKey(), brief(e.getValue().optString("description", ""))));
        }
        return sb.append(Words.HP_INDEX_POINT).append(Words.HP_MAP).append(Words.HP_INDEX_TAIL).toString();
    }

    /** 半句用途：取说明开头到第一个分隔符，最多 18 字（目录要短）。 */
    /** 目录一行的半句用途：说明本身就是一行，最多 34 字。 */
    private static String brief(String d) {
        if (d == null) return "";
        String s = d.trim();
        int ip = s.indexOf("（详见 help tool=");
        if (ip > 0) s = s.substring(0, ip);   // ★77.6：目录行不带"详见"后缀（那截半句难看）
        return s.length() > 44 ? s.substring(0, 44) : s;   // ★78：34→44（好让 build 那行工序写全 check→new→start→status→install）
    }

    /** 46 号单：失败回话统一带指路（与 help 同源、一处维护）——模型报错时必读这句。 */
    /** 46 号单：失败回话统一带指路（与 help 同源、一处维护）——模型报错时必读这句。
     *  ★77.6：带上结构化 reason 与「下一步」；原因随 failMark 进审计。 */
    private static String failText(String name, JSONObject o, String raw) {
        String err = o == null ? raw : o.optString("err", raw);
        String reason = o == null ? "" : o.optString("reason", "");
        failMark(name + " ok=0" + (reason.length() > 0 ? " reason=" + reason : ""));
        return Words.RP_OP_ERR + err + nextStep(reason) + String.format(java.util.Locale.ROOT, Words.HP_POINT, name);
    }

    /** 失败原因 → 下一步（素：一句）。 */
    private static String nextStep(String reason) {
        if (reason == null || reason.length() == 0) return "";
        if (reason.equals("expect_timeout")) return Words.RP_REASON_EXPECT;
        if (reason.equals("not_found")) return Words.RP_REASON_NOT_FOUND;
        if (reason.equals("pkg_mismatch")) return Words.RP_REASON_PKG;
        if (reason.equals("write_failed")) return Words.RP_REASON_WRITE;
        return "";
    }
}
