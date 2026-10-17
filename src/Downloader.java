package app.reticle;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 41 号单（2026-10-10）· Reticle 内置下载工具（眼手包的"取件"能力）。
 * 为什么这么做：1) tools/list 里直接有 download ＝ 后代助手一看就会用（不靠口口相传）；
 *              2) 不依赖 Shizuku／命令行 ＝ 备用手的备用手（第三只手瞎了它还在）；
 *              3) 一次调用带齐：进度可见（通知栏）＋ sha256 校验 ＋ 可选解包。
 * 设计口径：小文件（≤8MB）当场同步下完（MCP 客户端那条线是 60 秒，自己只等 45 秒）；
 *          大文件立刻返回任务号、下载交给后台线程，进度写进通知栏（实况进度 style）；
 *          源站不给大小（有些 CDN 走 chunked）＝先同步试 20 秒/12MB，超了自动转后台续传。
 * 落盘白名单：Home.DIR/dl、/sdcard/Download（42 单起剔除 /data/local/tmp：那儿属主是 shell，App 写只会 EACCES）；Reticle 侧目录补 .nomedia（不脏相册）。
 * 不做：不开免 token 的下载口（写盘动作只走 MCP）；不自动装机；不碰 VPN／代理／网络栈。
 */
public class Downloader {

    /** ★71 A 线（2026-10-15）：结构化失败标记——本工具自己按下（McpServer 据此判 isError，**不扫正文**）。
     *  只反映"同步返回这一趟"的结果；后台任务失败走 download_status 的任务状态。 */
    public static volatile boolean LAST_FAIL = false;

    /** ★71 A 线：按下失败标记并把文本原样返回。 */
    private static String bad(String msg) { LAST_FAIL = true; return msg; }

    /** 下载进度通知编号（与本 App 其它通知分开：眼线＝9、前台服务＝2、汇报＝1）。 */
    public static final int DID = 8;

    private static final long SYNC_MAX_BYTES = 8L * 1024 * 1024;          // 同步上限（派单口径）
    private static final long SYNC_MAX_MS = 45000L;                       // 同步上限时间（留 15 秒给 60 秒线）
    private static final long UNKNOWN_SYNC_BYTES = 12L * 1024 * 1024;     // 大小未知时的同步试跑上限
    private static final long UNKNOWN_SYNC_MS = 20000L;
    private static final long HARD_MAX_BYTES = 2L * 1024 * 1024 * 1024L;  // 不显式 force 就先拒
    private static final long EXISTS_SHA_MAX = 64L * 1024 * 1024L;        // 已存在同名文件时算 sha 的上限
    private static final int CONNECT_MS = 15000;
    private static final int READ_MS = 20000;
    private static final int KEEP_TASKS = 60;                             // 任务表最多留几条
    private static final String UA = "curl/8.16.0";
    private static final String UA_BROWSER = "Mozilla/5.0 (Linux; Android 14; V2505A) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36";

    private static final Map<String, Task> TASKS = new ConcurrentHashMap<String, Task>();
    private static final List<String> ORDER = Collections.synchronizedList(new ArrayList<String>());
    private static final ExecutorService POOL = Executors.newCachedThreadPool();
    private static final Object SEQ = new Object();
    private static int seq = 0;

    static class Task {
        String id = "";
        String url = "";
        String dir = "";
        String name = "";
        String targetPath = "";
        String partPath = "";
        String headers = "";
        String wantSha = "";
        String err = "";
        String sha = "";
        long total = -1;
        long done = 0;
        long startMs = 0;
        long endMs = 0;
        long ntMs = 0;
        long ntBytes = 0;
        String state = "queued";   // queued / running / done / error
        boolean unzip = false;
        boolean force = false;
        boolean range = false;
    }

    /** download 工具正文。 */
    public static String download(final Context ctx, JSONObject args) {
        LAST_FAIL = false;
        try {
            String urlS = args.optString("url", "").trim();
            if (!(urlS.startsWith("http://") || urlS.startsWith("https://"))) return bad(Words.RP_DL_BAD_URL);
            File dir = resolveDir(ctx, args.optString("dir", "").trim());
            if (dir == null) return bad(Words.RP_DL_BAD_DIR);
            try {
                if (!dir.exists() && !dir.mkdirs()) return bad(Words.RP_DL_BAD_DIR);
            } catch (Throwable t) { return bad(Words.RP_DL_BAD_DIR); }
            if (!dir.isDirectory()) return bad(Words.RP_DL_BAD_DIR);
            ensureNomedia(dir);
            if (!writable(dir)) return bad(String.format(Locale.ROOT, Words.RP_DL_NOWRITE_FMT, pretty(dir.getAbsolutePath())));

            String name = args.optString("name", "").trim();
            if (name.length() == 0) name = nameFromUrl(urlS);
            if (name.length() == 0) name = "download.bin";
            name = sanitize(name);
            String wantSha = args.optString("sha256", "").trim().toLowerCase(Locale.ROOT);
            String headers = args.optString("headers", "").trim();
            boolean unzip = args.optInt("unzip", 0) == 1;
            boolean force = args.optInt("force", 0) == 1;

            File target = new File(dir, name);
            File part = new File(dir, name + ".part");

            Probe p = probe(urlS, headers);
            if (p.code >= 400) return bad(String.format(Locale.ROOT, Words.RP_DL_HTTP_FMT, p.code, urlS));

            if (target.exists() && !force) {
                long len = target.length();
                String sha = len <= EXISTS_SHA_MAX ? sha256Of(target) : "";
                return String.format(Locale.ROOT, Words.RP_DL_EXISTS_FMT, pretty(target.getAbsolutePath()), human(len),
                        sha.length() > 0 ? sha : Words.RP_DL_SHA_UNKNOWN);
            }
            if (p.size > HARD_MAX_BYTES && !force) {
                return String.format(Locale.ROOT, Words.RP_DL_TOO_BIG_FMT, human(p.size));
            }

            boolean syncable = (p.size < 0) || (p.size <= SYNC_MAX_BYTES);
            if (syncable) {
                Task t = newTask(urlS, dir, name, headers, wantSha, unzip, force, p.size, p.range);
                long capBytes = (p.size < 0) ? UNKNOWN_SYNC_BYTES : SYNC_MAX_BYTES;
                long capMs = (p.size < 0) ? UNKNOWN_SYNC_MS : SYNC_MAX_MS;
                String res = pump(ctx, t, capBytes, capMs, false);
                if (res != null) return res;                       // 下完（或明确失败）
                long have = t.done;                                 // 被限时/限字节打断 → 转后台续传
                startBackground(ctx, t);
                return String.format(Locale.ROOT, Words.RP_DL_TO_BG_FMT, human(have), t.id);
            }
            Task t = newTask(urlS, dir, name, headers, wantSha, unzip, force, p.size, p.range);
            startBackground(ctx, t);
            return String.format(Locale.ROOT, Words.RP_DL_START_FMT, t.id, human(p.size), t.id);
        } catch (Throwable ex) {
            return bad(String.format(Locale.ROOT, Words.RP_DL_ERR_FMT, whyText(args.optString("dir", ""), ex)));
        }
    }

    /** download_status 工具正文。 */
    public static String status(Context ctx, JSONObject args) {
        LAST_FAIL = false;
        try {
            String id = args.optString("id", "").trim();
            if (id.length() > 0) {
                Task t = TASKS.get(id);
                if (t == null) return String.format(Locale.ROOT, Words.RP_DLS_NOID_FMT, id);
                LAST_FAIL = "error".equals(t.state);
                return oneLine(t, true);
            }
            List<String> ids = new ArrayList<String>(ORDER);
            if (ids.isEmpty()) return Words.RP_DLS_NONE;
            StringBuilder sb = new StringBuilder(Words.RP_DLS_TITLE);
            int n = 0;
            for (int i = ids.size() - 1; i >= 0 && n < 20; i--) {
                Task t = TASKS.get(ids.get(i));
                if (t == null) continue;
                sb.append(oneLine(t, false)).append("\n");
                n++;
            }
            return sb.toString();
        } catch (Throwable ex) {
            return String.format(Locale.ROOT, Words.RP_DL_ERR_FMT, String.valueOf(ex));
        }
    }

    // ══════════ 目录与名字 ══════════

    /** 目录：只认白名单（Reticle 的 dl 目录、用户下载目录）；相对名挂在 Reticle 下。 */
    private static File resolveDir(Context ctx, String dirS) {
        try {
            String home = Home.DIR;
            try { if (Home.get(ctx) != null) home = Home.get(ctx).getAbsolutePath(); } catch (Throwable t) { }
            File d;
            if (dirS.length() == 0) d = new File(home, "dl");
            else if (dirS.startsWith("/")) d = new File(dirS);
            else d = new File(home, dirS);
            String cp = d.getCanonicalPath();
            if (!inWhite(ctx, cp)) return null;
            return new File(cp);
        } catch (Throwable t) { return null; }
    }

    private static boolean inWhite(Context ctx, String cp) {
        List<String> roots = new ArrayList<String>();
        roots.add(Home.DIR + "/dl");
        roots.add("/sdcard/Download");
        try {
            File h = Home.get(ctx);
            if (h != null) roots.add(h.getAbsolutePath() + "/dl");
        } catch (Throwable t) { }
        for (int i = 0; i < roots.size(); i++) {
            String r = roots.get(i);
            if (isUnder(cp, r)) return true;
        }
        return false;
    }

    /** 只在 Reticle 自己的地盘补 .nomedia（用户自己的 /sdcard/Download 不碰）。 */
    /** 前缀判定：两边都按真实路径比（/sdcard 是符号链接，字面比会漏）。 */
    private static boolean isUnder(String cp, String root) {
        try {
            if (cp.equals(root) || cp.startsWith(root + "/")) return true;
            String rc = new File(root).getCanonicalPath();
            return cp.equals(rc) || cp.startsWith(rc + "/");
        } catch (Throwable t) {
            return cp.equals(root) || cp.startsWith(root + "/");
        }
    }

    /** 路径说人话：/storage/emulated/0/… → /sdcard/… */
    static String pretty(String p) {
        if (p == null) return "";
        if (p.startsWith("/storage/emulated/0/")) return "/sdcard/" + p.substring("/storage/emulated/0/".length());
        return p;
    }

    // ══════════ 42 单 A1（2026-10-10）：写不进去要说人话 ══════════

    /** 目标目录真能写吗：先信 canWrite()；它说不行再真写一把探针试（vivo 上 canWrite 有时与真实权限不符），探针立刻删。 */
    private static boolean writable(File dir) {
        if (dir.canWrite()) return true;
        File probe = null;
        try {
            probe = new File(dir, ".write-probe-42");
            FileOutputStream o = new FileOutputStream(probe);
            o.write(0);
            o.close();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            try { if (probe != null) probe.delete(); } catch (Throwable t2) { }
        }
    }

    /** 42 单 A1：权限类异常 → 人话，别把 Java 原文甩给用户。 */
    private static String whyText(String dir, Throwable ex) {
        String s = String.valueOf(ex);
        boolean perm = (ex instanceof java.io.FileNotFoundException)
                || s.indexOf("EACCES") >= 0 || s.indexOf("Permission denied") >= 0
                || s.indexOf("EROFS") >= 0 || s.indexOf("Read-only") >= 0;
        if (perm) return String.format(Locale.ROOT, Words.RP_DL_NOWRITE_FMT, pretty(dir == null ? "" : dir));
        return s;
    }

    private static void ensureNomedia(File dir) {
        try {
            String hp = new File(Home.DIR).getCanonicalPath();
            if (!isUnder(dir.getCanonicalPath(), hp)) return;
            File nm = new File(dir, ".nomedia");
            if (!nm.exists()) {
                FileOutputStream o = new FileOutputStream(nm);
                o.write(0);
                o.close();
            }
        } catch (Throwable t) { }
    }

    /** 从网址末段取文件名（去掉查询串，做 URL 解码）。 */
    private static String nameFromUrl(String u) {
        try {
            String s = u;
            int q = s.indexOf('?');
            if (q >= 0) s = s.substring(0, q);
            int h = s.indexOf('#');
            if (h >= 0) s = s.substring(0, h);
            int sl = s.lastIndexOf('/');
            if (sl >= 0) s = s.substring(sl + 1);
            if (s.length() == 0) return "";
            return URLDecoder.decode(s, "UTF-8");
        } catch (Throwable t) { return ""; }
    }

    /** 文件名清洗：不留路径分隔符、不留前导点（防 .part 串味）、别太长。 */
    private static String sanitize(String s) {
        String r = s == null ? "" : s.replace('/', '_').replace('\\', '_').replace('\u0000', '_').trim();
        while (r.startsWith(".")) r = r.substring(1);
        if (r.length() == 0) r = "download.bin";
        if (r.length() > 120) r = r.substring(0, 120);
        return r;
    }

    private static String baseName(String n) {
        int i = n.lastIndexOf('.');
        return i > 0 ? n.substring(0, i) : n;
    }

    // ══════════ 探测与连接 ══════════

    static class Probe {
        int code = 0;
        long size = -1;
        boolean range = false;
        String ctype = "";
    }

    /** 探测：GET 只看响应头（拿到就断开）→ 大小／是否支持 Range；探测失败＝大小未知，交给真正的下载去判定。 */
    private static Probe probe(String urlS, String headers) {
        Probe p = new Probe();
        HttpURLConnection c = null;
        try {
            c = open(urlS, headers, UA);
            // 41 单实测：探测改成「GET 只看响应头、立刻断开」——HEAD 在清华镜像的 WAF 下会被拦，那样会把「能下」误判成「连不上」
            c.setRequestProperty("Accept-Encoding", "identity");
            int code = c.getResponseCode();
            if (code >= 200 && code < 300) {
                long cl = c.getContentLength();
                if (cl > 0) p.size = cl;
            } else {
                code = 0;   // 探测被拒：不当结论——大小未知，交给真正的下载去判定
            }
            p.code = code;
            String ar = c.getHeaderField("Accept-Ranges");
            p.range = (ar != null && ar.toLowerCase(Locale.ROOT).contains("bytes"));
            String ct = c.getHeaderField("Content-Type");
            if (ct != null) p.ctype = ct;
        } catch (Throwable t) {
            p.code = -1;
        } finally {
            close(c);
        }
        return p;
    }

    private static HttpURLConnection open(String urlS, String headers, String ua) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlS).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(CONNECT_MS);
        c.setReadTimeout(READ_MS);
        c.setRequestProperty("User-Agent", (ua == null || ua.length() == 0) ? UA : ua);
        c.setRequestProperty("Accept", "*/*");
        if (headers != null && headers.length() > 0) {
            List<String> kvs = splitHeaders(headers);
            for (int i = 0; i < kvs.size(); i++) {
                String kv = kvs.get(i);
                int p = kv.indexOf(':');
                if (p > 0) c.setRequestProperty(kv.substring(0, p).trim(), kv.substring(p + 1).trim());
            }
        }
        return c;
    }

    /** 请求头参数：接受 {"Referer":"https://…"} 这种 JSON，也接受「Referer: https://…」一行一条。 */
    private static List<String> splitHeaders(String s) {
        List<String> out = new ArrayList<String>();
        try {
            String t = s.trim();
            if (t.startsWith("{")) {
                JSONObject j = new JSONObject(t);
                java.util.Iterator<String> it = j.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    out.add(k + ": " + j.optString(k, ""));
                }
                return out;
            }
            String[] lines = t.split("[;\n\r]+");
            for (int i = 0; i < lines.length; i++) {
                String ln = lines[i].trim();
                if (ln.length() > 0) out.add(ln);
            }
        } catch (Throwable t2) { }
        return out;
    }

    /** "bytes 0-0/51301092" → 总长。 */
    private static long parseContentRange(String cr) {
        try {
            if (cr == null) return -1;
            int p = cr.indexOf('/');
            if (p < 0) return -1;
            String tot = cr.substring(p + 1).trim();
            if (tot.equals("*")) return -1;
            return Long.parseLong(tot);
        } catch (Throwable t) { return -1; }
    }

    private static void close(HttpURLConnection c) {
        try { if (c != null) c.disconnect(); } catch (Throwable t) { }
    }

    // ══════════ 下载主循环 ══════════

    /**
     * 下载循环（同步与后台同一份代码）：返回 null ＝ 被限时／限字节打断（可续传，转后台）；
     * 返回非 null ＝ 结果文本（下好了 或 明确失败）。
     */
    private static String pump(Context ctx, Task t, long capBytes, long capMs, boolean bg) {
        long t0 = System.currentTimeMillis();
        HttpURLConnection c = null;
        InputStream in = null;
        OutputStream fo = null;
        MessageDigest md = null;
        long have = 0;
        File part = new File(t.partPath);
        File target = new File(t.targetPath);
        try {
            md = MessageDigest.getInstance("SHA-256");
            have = part.exists() ? part.length() : 0;
            if (have > 0 && t.range) {
                feedDigest(md, part);
            } else if (have > 0) {
                part.delete();
                have = 0;
            }
            if (have > 0 && t.total > 0 && have == t.total) {
                return finish(ctx, t, md, part, target, have, bg, false);
            }
            c = open(t.url, t.headers, UA);
            c.setRequestProperty("Accept-Encoding", "identity");
            if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
            int code = c.getResponseCode();
            if (code == 403 || code == 401 || code == 406) {
                // 41 单实测：清华镜像的 WAF 把「浏览器 UA＋下载行为」当爬虫（403，页面写着"非常用软件的特征"）→ 换一种 UA 再试一次
                close(c);
                c = open(t.url, t.headers, UA_BROWSER);
                c.setRequestProperty("Accept-Encoding", "identity");
                if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
                code = c.getResponseCode();
            }
            if ((code == 416 || code == 400) && have > 0) {
                close(c);
                c = null;
                part.delete();
                have = 0;
                md = MessageDigest.getInstance("SHA-256");
                c = open(t.url, t.headers, UA);
                c.setRequestProperty("Accept-Encoding", "identity");
                code = c.getResponseCode();
            }
            if (code >= 400) {
                return fail(ctx, t, String.format(Locale.ROOT, Words.RP_DL_HTTP_FMT, code, t.url), bg);
            }
            long total = t.total;
            if (code == 206) {
                long tot = parseContentRange(c.getHeaderField("Content-Range"));
                if (tot > 0) total = tot;
                t.range = true;
            } else {
                if (have > 0) {                       // 服务端忽略了 Range → 老实从头下
                    part.delete();
                    have = 0;
                    md = MessageDigest.getInstance("SHA-256");
                }
                long cl = c.getContentLength();
                if (cl > 0) total = cl;
            }
            t.total = total;
            t.state = "running";
            long now0 = System.currentTimeMillis();
            if (t.startMs == 0) t.startMs = now0;
            t.done = have;
            if (bg) notifyProgress(ctx, t, true);
            fo = new FileOutputStream(part, have > 0);
            in = c.getInputStream();
            byte[] buf = new byte[65536];
            int r;
            long lastTick = now0;
            while ((r = in.read(buf)) > 0) {
                fo.write(buf, 0, r);
                md.update(buf, 0, r);
                have += r;
                t.done = have;
                long now = System.currentTimeMillis();
                if (bg && (now - lastTick) >= 900) {
                    lastTick = now;
                    notifyProgress(ctx, t, false);
                }
                if (t.total <= 0 && !t.force && have > HARD_MAX_BYTES) {
                    closeQuietly(fo);
                    closeQuietly(in);
                    close(c);
                    return fail(ctx, t, String.format(Locale.ROOT, Words.RP_DL_TOO_BIG_FMT, human(have)), bg);
                }
                if (capBytes > 0 && have > capBytes) {
                    closeQuietly(fo);
                    closeQuietly(in);
                    close(c);
                    t.done = have;
                    return null;
                }
                if (capMs > 0 && (now - t0) > capMs) {
                    closeQuietly(fo);
                    closeQuietly(in);
                    close(c);
                    t.done = have;
                    return null;
                }
            }
            closeQuietly(fo);
            fo = null;
            closeQuietly(in);
            in = null;
            close(c);
            c = null;
            return finish(ctx, t, md, part, target, have, bg, true);
        } catch (Throwable ex) {
            closeQuietly(fo);
            closeQuietly(in);
            close(c);
            return fail(ctx, t, whyText(t.dir, ex), bg);
        }
    }

    private static String finish(Context ctx, Task t, MessageDigest md, File part, File target, long have, boolean bg, boolean checkLen) {
        if (!bg) LAST_FAIL = false;
        try {
            if (checkLen && t.total > 0 && have != t.total) {
                return fail(ctx, t, String.format(Locale.ROOT, Words.RP_DL_LEN_FMT, human(have), human(t.total)), bg);
            }
            String sha = hexDigest(md);
            if (target.exists()) target.delete();
            if (!part.renameTo(target)) {
                copyFile(part, target);
                part.delete();
            }
            t.sha = sha;
            t.done = have;
            t.state = "done";
            t.endMs = System.currentTimeMillis();
            long secs = Math.max(1L, (t.endMs - (t.startMs > 0 ? t.startMs : t.endMs)) / 1000L);
            if (t.wantSha.length() > 0 && !sha.equalsIgnoreCase(t.wantSha)) {
                t.state = "error";
                t.err = String.format(Locale.ROOT, Words.RP_DL_SHA_BAD_FMT, sha, t.wantSha);
                if (!bg) LAST_FAIL = true;
                if (bg) notifyEnd(ctx, t, false);
                return String.format(Locale.ROOT, Words.RP_DL_SHA_BAD_LINE_FMT,
                        pretty(target.getAbsolutePath()), human(have), secs, sha, t.wantSha);
            }
            if (bg) notifyEnd(ctx, t, true);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.ROOT, Words.RP_DL_OK_FMT, human(have), new File(t.dir).getName(), pretty(target.getAbsolutePath()), sha, secs));
            sb.append("\n").append(t.wantSha.length() > 0 ? Words.RP_DL_SHA_OK : Words.RP_DL_SHA_UNCHECKED);
            if (t.unzip) sb.append("\n").append(unzip(target, new File(t.dir, baseName(t.name))));
            return sb.toString();
        } catch (Throwable ex) {
            return fail(ctx, t, whyText(t.dir, ex), bg);
        }
    }

    private static String fail(Context ctx, Task t, String why, boolean bg) {
        t.state = "error";
        t.err = why;
        t.endMs = System.currentTimeMillis();
        if (bg) notifyEnd(ctx, t, false);
        if (!bg) LAST_FAIL = true;
        return String.format(Locale.ROOT, Words.RP_DL_ERR_FMT, why);
    }

    private static void startBackground(final Context ctx, final Task t) {
        try { t.state = "running"; } catch (Throwable x) { }
        POOL.submit(new Runnable() {
            public void run() {
                try {
                    pump(ctx, t, 0, 0, true);
                } catch (Throwable ex) {
                    fail(ctx, t, String.valueOf(ex), true);
                }
            }
        });
    }

    // ══════════ 通知栏（实况进度） ══════════

    private static void notifyProgress(Context ctx, Task t, boolean force) {
        try {
            long now = System.currentTimeMillis();
            if (!force && t.ntMs > 0 && (now - t.ntMs) < 900) return;
            long dBytes = t.done - t.ntBytes;
            long dMs = t.ntMs > 0 ? (now - t.ntMs) : 0;
            String speed = Words.DL_NT_SPEED_UNKNOWN;
            if (dMs > 200 && dBytes > 0) speed = human((long) (dBytes * 1000.0 / dMs)) + "/s";
            t.ntMs = now;
            t.ntBytes = t.done;
            int pct = 0;
            if (t.total > 0) pct = (int) Math.min(100L, t.done * 100L / t.total);
            String line = (t.total > 0)
                    ? String.format(Locale.ROOT, Words.DL_NT_LINE_FMT, human(t.done), human(t.total), pct, speed)
                    : String.format(Locale.ROOT, Words.DL_NT_LINE_NOSIZE_FMT, human(t.done), speed);
            Notifier.promoted(ctx, DID, cut(Words.DL_NT_TITLE + t.name, 40), line, pct);
        } catch (Throwable x) { }
    }

    private static void notifyEnd(Context ctx, Task t, boolean ok) {
        try {
            if (ok) {
                Notifier.send(ctx, DID, cut(Words.DL_NT_DONE_T + t.name, 40),
                        cut(human(t.done) + " · " + pretty(t.targetPath), 90), false);
            } else {
                Notifier.send(ctx, DID, cut(Words.DL_NT_FAIL_T + t.name, 40), cut(t.err, 90), false);
            }
        } catch (Throwable x) { }
    }

    // ══════════ 顺带解包（只在 unzip=1 时做；防 zip slip） ══════════

    private static String unzip(File zip, File outDir) {
        ZipInputStream zi = null;
        FileInputStream fi = null;
        int n = 0;
        try {
            if (!outDir.exists()) outDir.mkdirs();
            String root = outDir.getCanonicalPath();
            fi = new FileInputStream(zip);
            zi = new ZipInputStream(fi);
            ZipEntry e;
            byte[] buf = new byte[65536];
            while ((e = zi.getNextEntry()) != null) {
                String nm = e.getName();
                if (nm == null || nm.length() == 0) continue;
                File out = new File(outDir, nm);
                String cp = out.getCanonicalPath();
                if (!cp.equals(root) && !cp.startsWith(root + File.separator)) continue;   // zip slip：越界条目丢掉
                if (e.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                File par = out.getParentFile();
                if (par != null && !par.exists()) par.mkdirs();
                FileOutputStream fo = new FileOutputStream(out);
                int r;
                while ((r = zi.read(buf)) > 0) fo.write(buf, 0, r);
                fo.close();
                n++;
            }
            return String.format(Locale.ROOT, Words.RP_DL_UNZIP_FMT, n, pretty(outDir.getAbsolutePath()));
        } catch (Throwable t) {
            return String.format(Locale.ROOT, Words.RP_DL_UNZIP_ERR_FMT, whyText(outDir.getAbsolutePath(), t));
        } finally {
            closeQuietly(zi);
            closeQuietly(fi);
        }
    }

    // ══════════ 小工具 ══════════

    private static Task newTask(String urlS, File dir, String name, String headers, String wantSha, boolean unzip, boolean force, long total, boolean range) {
        Task t = new Task();
        t.id = nextId();
        t.url = urlS;
        t.dir = dir.getAbsolutePath();
        t.name = name;
        t.targetPath = new File(dir, name).getAbsolutePath();
        t.partPath = new File(dir, name + ".part").getAbsolutePath();
        t.headers = headers == null ? "" : headers;
        t.wantSha = wantSha == null ? "" : wantSha;
        t.unzip = unzip;
        t.force = force;
        t.total = total;
        t.range = range;
        TASKS.put(t.id, t);
        ORDER.add(t.id);
        while (ORDER.size() > KEEP_TASKS) {
            String old = ORDER.remove(0);
            Task ot = TASKS.get(old);
            if (ot == null || !"running".equals(ot.state)) TASKS.remove(old);
        }
        return t;
    }

    private static String nextId() {
        synchronized (SEQ) {
            seq++;
            return "d" + (System.currentTimeMillis() / 1000L) + "-" + seq;
        }
    }

    private static String oneLine(Task t, boolean full) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.id).append("｜");
        if ("done".equals(t.state)) sb.append(Words.RP_DLS_DONE);
        else if ("error".equals(t.state)) sb.append(Words.RP_DLS_ERR);
        else sb.append(Words.RP_DLS_RUN);
        sb.append("｜").append(progressText(t));
        sb.append("\n   ").append(pretty(t.targetPath));
        if (full) {
            sb.append("\n   来源 ").append(t.url);
            if (t.wantSha.length() > 0) sb.append("\n   期望 sha256=").append(t.wantSha);
            if (t.sha.length() > 0) sb.append("\n   实际 sha256=").append(t.sha);
            if (t.startMs > 0 && t.endMs > 0) sb.append("\n   用时 ").append((t.endMs - t.startMs) / 1000L).append(" 秒");
            if (t.err.length() > 0) sb.append("\n   错因 ").append(t.err);
        }
        return sb.toString();
    }

    private static String progressText(Task t) {
        StringBuilder sb = new StringBuilder();
        if ("done".equals(t.state)) {
            sb.append(Words.RP_DLS_FIN);
        } else if ("error".equals(t.state)) {
            sb.append(cut(t.err, 60));
        } else if (t.total > 0) {
            int pct = (int) Math.min(100L, t.done * 100L / t.total);
            sb.append(pct).append("%（").append(human(t.done)).append("/").append(human(t.total)).append("）");
        } else if (t.state.equals("running")) {
            sb.append(Words.RP_DLS_RUN).append("（已下 ").append(human(t.done)).append("）");
        } else {
            sb.append(Words.RP_DLS_QUEUED);
        }
        return sb.toString();
    }

    private static void feedDigest(MessageDigest md, File f) throws Exception {
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) > 0) md.update(buf, 0, r);
        } finally {
            closeQuietly(in);
        }
    }

    private static void copyFile(File from, File to) throws Exception {
        InputStream in = new FileInputStream(from);
        try {
            OutputStream o = new FileOutputStream(to);
            try {
                byte[] buf = new byte[65536];
                int r;
                while ((r = in.read(buf)) > 0) o.write(buf, 0, r);
            } finally {
                closeQuietly(o);
            }
        } finally {
            closeQuietly(in);
        }
    }

    private static String sha256Of(File f) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            feedDigest(md, f);
            return hexDigest(md);
        } catch (Throwable t) {
            return "";
        }
    }

    private static String hexDigest(MessageDigest md) {
        byte[] d = md.digest();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d.length; i++) sb.append(String.format(Locale.ROOT, "%02x", d[i] & 0xff));
        return sb.toString();
    }

    /** 字节数说人话。 */
    static String human(long b) {
        if (b < 0) return Words.RP_DL_SIZE_UNKNOWN;
        if (b < 1024) return b + " B";
        double k = b / 1024.0;
        if (k < 1024.0) return String.format(Locale.ROOT, "%.1f KB", k);
        double m = k / 1024.0;
        if (m < 1024.0) return String.format(Locale.ROOT, "%.1f MB", m);
        return String.format(Locale.ROOT, "%.2f GB", m / 1024.0);
    }

    private static String cut(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n);
    }

    private static void closeQuietly(Object o) {
        try {
            if (o == null) return;
            if (o instanceof InputStream) ((InputStream) o).close();
            else if (o instanceof OutputStream) ((OutputStream) o).close();
            else if (o instanceof ZipInputStream) ((ZipInputStream) o).close();
        } catch (Throwable t) { }
    }
}
