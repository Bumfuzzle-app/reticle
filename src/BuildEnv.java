package app.reticle;

import android.content.Context;
import android.content.res.AssetManager;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 74 号单（2026-10-16）· 应用内装环境（构建线第一步）。
 *
 * 【是什么】把"能编安卓 App"的一整套东西落到手机上，让 Reticle 自己就会出包。三件：
 *   ① 工具链（aapt2＋100 个依赖 so ＋ ECJ/d8/apksigner/buildtool 的 dex）＝**打进 APK 的 lib/arm64-v8a**，
 *      安装后位于 nativeLibraryDir —— 这是系统唯一允许"应用身份直接执行"的地方
 *      （Android 10 起 app 私有目录的文件不许 exec）⇒ 不用 Shizuku、不用落地解压、不用联网。
 *   ② 配套小件（ecj-res 资源目录 ＋ 构建脚本 build3.sh）＝首次从 assets 解到应用私有目录（只读不 exec）。
 *   ③ android.jar（25.8MB，SDK 条款禁再分发）＝首次一键从官方源下载 ＋ **sha256 校验**（错了就报，不用错件）。
 *
 * 【目录】应用私有 files/buildenv/ ：ecj-res/、build3.sh、android.jar、key.p12（用户自己的调试签名钥匙）、
 *    work/（构建工作区）、out/（产物 apk）。工具链本体不在这里，在 nativeLibraryDir（只读数）。
 *
 * 【红线】android.jar 永不内置、永不随包分发 ⇒ 只能下载；本类一行都没往 assets 里塞 android.jar。
 */
public class BuildEnv {

    /** 官方源（Google SDK repository）里的 platform-35 包；解出的 android.jar 用下面这个 sha 卡死。 */
    public static final String JAR_URL = "https://dl.google.com/android/repository/platform-35_r02.zip";
    public static final String JAR_IN_ZIP = "android-35/android.jar";
    public static final String JAR_SHA = "4566663c3876e022b4fa4ced8c8697c4ab1688267f090114fd92d027b32e619b";
    public static final long JAR_SIZE = 27092450L;
    /** 环境自己的通知编号（与下载工具 8／形象 9 分开）。 */
    public static final int NID = 12;   // ★127：避开 9（大通知）／10（岛）／11（掉线告警）

    /** 工具链（在 apk 的 lib/arm64-v8a 里）需要的文件——名字就是安装后的 .so 名。 */
    private static final String[] LIB_FILES = { "libaapt2.so" };

    public static File home(Context c) { File f = new File(c.getFilesDir(), "buildenv"); if (!f.exists()) f.mkdirs(); return f; }
    public static File jar(Context c) { return new File(home(c), "android.jar"); }
    public static File res(Context c) { return new File(home(c), "ecj-res"); }
    public static File script(Context c) { return new File(home(c), "build3.sh"); }
    public static File key(Context c) { return new File(home(c), "key.p12"); }
    public static File work(Context c) { return new File(home(c), "work"); }
    public static File out(Context c) { return new File(home(c), "out"); }
    /** 工具链目录＝apk 的 lib/arm64-v8a（nativeLibraryDir）。 */
    public static File lib(Context c) { return new File(c.getApplicationInfo().nativeLibraryDir); }

    // ═══════════════════════ ★1104 工具链 dex（可外置，按需下载） ═══════════════════════
    /** 这 5 个是 dex（app_process 用 CLASSPATH 加载，放哪都行）；aapt2 与依赖 so 必须随包。 */
    public static final String[] DEX_FILES = {
        "libecj.so", "libd8.so", "libd8_2.so", "libapksigner.so", "libbuildtool.so",
    };
    static final long[] DEX_SIZE = { 2771216L, 2877720L, 8519828L, 1054856L, 5056L };
    static final String[] DEX_SHA = {
        "de3a358b98a1637b2f92c77ac97e8c238d2e76571f674a59bfdc854a34d69619",
        "12fca836dc8cb82ed6c64fbd2566354683f64899c688b2cc491d4a9453578cd0",
        "680b0a7c5e6d9e866843a900688f485f9748d7aaaf39bfb5888d492e23bde104",
        "109512d0b6507d27c4ce54646fbbc6d7e9a51c422f33e99a584fc8dd411c008e",
        "9eb9dce17807b14d351c83b0d6779224929394a15da594ed175333cdef9dd537",
    };
    /** 用 API 的 asset 端点：github.com 的下载链路在某些网络不通，api.github.com 通（匿名可下公开 release）。 */
    public static final String DEX_BASE = "https://api.github.com/repos/Bumfuzzle-app/reticle/releases/assets/";
    static final long[] DEX_IDS = { 709768263L, 709766713L, 709767164L, 709765905L, 709766601L };   // 顺序同 DEX_FILES
    public static volatile int DEX_PCT = -2;      // -2 未开始／-1 失败／0~100 下载中／101 完成
    public static volatile String DEX_MSG = "";

    public static File dexDir(Context c) { File d = new File(home(c), "dex"); if (!d.exists()) d.mkdirs(); return d; }

    /** 5 个 dex 在哪：lib 里齐全就用 lib（旧包）；否则下载目录齐全才认；否则 null。 */
    public static File dexHome(Context c) {
        try {
            File d = lib(c);
            boolean all = true;
            for (int i = 0; i < DEX_FILES.length; i++) if (!new File(d, DEX_FILES[i]).isFile()) { all = false; break; }
            if (all) return d;
            File x = dexDir(c);
            for (int i = 0; i < DEX_FILES.length; i++) {
                File f = new File(x, DEX_FILES[i]);
                if (!f.isFile() || f.length() != DEX_SIZE[i]) return null;
            }
            return x;
        } catch (Throwable t) { return null; }
    }

    public static String dexState() {
        if (DEX_PCT >= 101) return "已就绪";
        if (DEX_PCT >= 0) return "下载中 " + DEX_PCT + "%";
        return DEX_MSG.length() > 0 ? DEX_MSG : "未下载";
    }

    /** 后台下载（界面点按钮用；工具调用有 60 秒上限，绝不能同步等）。 */
    public static void fetchDexAsync(final Context c) {
        if (DEX_PCT >= 0 && DEX_PCT < 101) return;
        DEX_PCT = 0; DEX_MSG = "开始下载";
        new Thread(new Runnable() {
            public void run() {
                String err = fetchDex(c.getApplicationContext());
                DEX_MSG = err == null ? "下载完成" : err;
                DEX_PCT = err == null ? 101 : -1;
            }
        }).start();
    }

    /** 同步下载 5 个 dex（gzip 解压 ＋ 大小／sha 双校验）。返回 null＝成功。 */
    static String fetchDex(Context c) {
        File d = dexDir(c);
        long totalAll = 0, done = 0;
        for (int i = 0; i < DEX_SIZE.length; i++) totalAll += DEX_SIZE[i];
        for (int i = 0; i < DEX_FILES.length; i++) {
            File dst = new File(d, DEX_FILES[i]);
            if (dst.isFile() && dst.length() == DEX_SIZE[i]) { done += DEX_SIZE[i]; continue; }
            File tmp = new File(d, DEX_FILES[i] + ".tmp");
            try {
                java.net.HttpURLConnection cn = (java.net.HttpURLConnection) new java.net.URL(DEX_BASE + DEX_IDS[i]).openConnection();
                cn.setConnectTimeout(20000);
                cn.setReadTimeout(60000);
                cn.setInstanceFollowRedirects(true);
                cn.setRequestProperty("Accept", "application/octet-stream");
                cn.setRequestProperty("User-Agent", "reticle");
                cn.connect();
                int code = cn.getResponseCode();
                if (code != 200) { cn.disconnect(); return "编译器下载失败：源回 HTTP " + code; }
                InputStream in = new java.util.zip.GZIPInputStream(cn.getInputStream(), 1 << 16);
                FileOutputStream fo = new FileOutputStream(tmp);
                byte[] buf = new byte[262144];
                int n;
                try {
                    while ((n = in.read(buf)) > 0) {
                        fo.write(buf, 0, n);
                        done += n;
                        int pct = (int) (done * 100 / totalAll);
                        if (pct > 100) pct = 100;
                        DEX_PCT = pct;
                    }
                    fo.flush();
                } finally { try { fo.close(); } catch (Throwable t) { } try { in.close(); } catch (Throwable t) { } cn.disconnect(); }
                if (tmp.length() != DEX_SIZE[i]) { long g = tmp.length(); tmp.delete(); return "编译器不完整（" + DEX_FILES[i] + " " + g + " B）"; }
                String sha = sha256(tmp);
                if (sha == null || !DEX_SHA[i].equalsIgnoreCase(sha)) { tmp.delete(); return "编译器校验不过（" + DEX_FILES[i] + "）"; }
                if (dst.exists()) dst.delete();
                if (!tmp.renameTo(dst)) { tmp.delete(); return "编译器落盘失败（" + DEX_FILES[i] + "）"; }
            } catch (Throwable t) {
                try { tmp.delete(); } catch (Throwable t2) { }
                return "编译器下载失败：" + t.getClass().getSimpleName();
            }
        }
        return null;
    }

    // ═══════════════════════ 就绪判据 ═══════════════════════

    /** 工具链：aapt2 与依赖 so 随包；5 个 dex 在 lib 或下载目录都行。 */
    public static boolean tcOk(Context c) {
        try {
            File d = lib(c);
            for (int i = 0; i < LIB_FILES.length; i++) {
                File f = new File(d, LIB_FILES[i]);
                if (!f.exists() || f.length() == 0) return false;
            }
            if (!new File(d, "libabsl_base.so").exists()) return false;
            return dexHome(c) != null;
        } catch (Throwable t) { return false; }
    }
    public static boolean jarOk(Context c) { try { File f = jar(c); return f.exists() && f.length() == JAR_SIZE; } catch (Throwable t) { return false; } }
    public static boolean resOk(Context c) { try { return new File(res(c), "ecj.1").exists() || new File(res(c), "about.html").exists(); } catch (Throwable t) { return false; } }
    public static boolean scriptOk(Context c) { try { return script(c).exists() && script(c).length() > 0; } catch (Throwable t) { return false; } }
    public static boolean keyOk(Context c) { try { return key(c).exists() && key(c).length() > 0; } catch (Throwable t) { return false; } }

    /** 三件齐＋签名钥匙有＝能出包（钥匙缺件会在第一次构建时自动生成，所以不算"缺件"）。 */
    public static boolean ready(Context c) { return tcOk(c) && jarOk(c) && resOk(c) && scriptOk(c); }

    /** 缺什么（空串＝齐）——人话，一句一件；给 AI 与用户共用。 */
    public static String missing(Context c) {
        StringBuilder sb = new StringBuilder();
        if (!tcOk(c)) {
            if (new File(lib(c), "libaapt2.so").exists()) sb.append("· 编译器还没下载（6.2MB）：" + dexState() + "，点『一键准备环境』补\n");
            else sb.append("· 工具链没带上（apk 的 lib/arm64-v8a 里看不到 aapt2）——本包只保证 arm64 机型\n");
        }
        if (!resOk(c)) sb.append("· 编译器资源没解出来（ecj-res；首次自动解，失败可重开应用或点『一键准备环境』）\n");
        if (!scriptOk(c)) sb.append("· 构建脚本没解出来（build3.sh；同上）\n");
        if (!jarOk(c)) sb.append("· android.jar 还没下载（25.8MB；SDK 条款不许随包分发，只能下载）\n");
        return sb.toString();
    }

    /** 一行状态（界面与 AI 共用）。 */
    public static String statusLine(Context c) {
        if (ready(c)) return "就绪：工具链 ✓　android.jar ✓（25.8MB，已校验）";
        if (!tcOk(c)) return "未就绪：缺编译器（6.2MB）——" + dexState();
        if (!jarOk(c)) return "未就绪：缺 android.jar（25.8MB，未下载）";
        return "未就绪：缺编译资源／脚本（可点『一键准备环境』补）";
    }

    /** 结构化状态（给 AI：tools/list 描述与 build 的错误都读它）。 */
    public static JSONObject statusJson(Context c) {
        JSONObject o = new JSONObject();
        try {
            o.put("ready", ready(c) ? 1 : 0);
            o.put("toolchain", tcOk(c) ? 1 : 0);
            o.put("androidJar", jarOk(c) ? 1 : 0);
            o.put("ecjRes", resOk(c) ? 1 : 0);
            o.put("script", scriptOk(c) ? 1 : 0);
            o.put("key", keyOk(c) ? 1 : 0);
            o.put("abi", tcOk(c) ? "arm64-v8a" : "");
            String miss = missing(c);
            if (miss.length() > 0) o.put("need", miss.replace("\n", " ").trim());
            o.put("howTo", ready(c) ? "可以直接 build action=start 出包" : "先补环境：应用内『打包 App』→『一键准备环境』，或 build action=env");
        } catch (Throwable t) { }
        return o;
    }

    // ═══════════════════════ 落地：assets 里的小件 ＋ android.jar ═══════════════════════

    /** 把 assets/tools/{ecj-res,build3.sh} 解到私有目录（幂等；已解过就跳过）。返回空串＝成功，否则人话原因。 */
    public static String unpack(Context c) {
        try {
            AssetManager am = c.getAssets();
            if (!resOk(c)) copyAssetDir(am, "tools/ecj-res", res(c));
            copyAssetFile(am, "tools/build3.sh", script(c));   // ★74 修：脚本每次都覆盖（改脚本不必重装环境）
            work(c).mkdirs(); out(c).mkdirs();
            if (!resOk(c) || !scriptOk(c)) return "解出来还是缺件（assets 里的 tools/ 不全？）";
            return "";
        } catch (Throwable t) { return "解压失败：" + t; }
    }

    /** assets 目录递归复制（AssetManager 只能逐层 list）。 */
    private static void copyAssetDir(AssetManager am, String from, File to) throws Exception {
        String[] kids = am.list(from);
        if (kids == null || kids.length == 0) { copyAssetFile(am, from, to); return; }
        if (!to.exists()) to.mkdirs();
        for (int i = 0; i < kids.length; i++) {
            String a = from + "/" + kids[i];
            File dst = new File(to, kids[i]);
            String[] k2 = am.list(a);
            if (k2 != null && k2.length > 0) copyAssetDir(am, a, dst); else copyAssetFile(am, a, dst);
        }
    }

    private static void copyAssetFile(AssetManager am, String from, File to) throws Exception {
        InputStream in = am.open(from);
        try {
            File p = to.getParentFile(); if (p != null && !p.exists()) p.mkdirs();
            OutputStream os = new FileOutputStream(to);
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                os.flush();
            } finally { try { os.close(); } catch (Throwable t) { } }
        } finally { try { in.close(); } catch (Throwable t) { } }
    }

    /** 进度回调（界面弹窗与通知栏都读它）。 */
    public interface Progress { void on(int pct, String line); }

    /** 一键准备环境＝解 assets 小件 ＋ 缺编译器就下载 ＋（缺就）下载 android.jar。返回空串＝成功。 */
    public static String prepare(Context c, Progress p) {
        String u = unpack(c);
        if (u.length() > 0) return u;
        if (dexHome(c) == null) {
            if (DEX_PCT >= 0 && DEX_PCT < 101) return "编译器下载中 " + DEX_PCT + "%：下完再点一次";
            if (p != null) p.on(0, "开始下载编译器（6.2MB）");
            fetchDexAsync(c);
            return "编译器开始下载（6.2MB，约 3 分钟）：下完再点一次『一键准备环境』";
        }
        if (jarOk(c)) { if (p != null) p.on(100, "环境已就绪（android.jar 在）"); return ""; }
        return fetchJar(c, p);
    }

    /** 下载 platform-35 包 → 取出 android.jar → sha256 校验 → 放进私有目录。失败重试 1 次。 */
    public static String fetchJar(Context c, Progress p) {
        String last = "";
        for (int attempt = 1; attempt <= 2; attempt++) {
            File zip = new File(home(c), "platform-35.zip.tmp");
            try {
                if (zip.exists()) zip.delete();
                if (p != null) p.on(0, "开始下载 android.jar（" + (attempt > 1 ? "第 " + attempt + " 次重试" : "约 61MB 安装包") + "）");
                long total = -1;
                HttpURLConnection cn = (HttpURLConnection) new URL(JAR_URL).openConnection();
                cn.setConnectTimeout(20000);
                cn.setReadTimeout(30000);
                cn.setInstanceFollowRedirects(true);
                cn.connect();
                int code = cn.getResponseCode();
                if (code != 200) { last = "下载源回了 HTTP " + code; cn.disconnect(); continue; }
                total = cn.getContentLength();
                InputStream in = cn.getInputStream();
                OutputStream os = new FileOutputStream(zip);
                byte[] buf = new byte[262144];
                long got = 0; int n, lastPct = -1;
                try {
                    while ((n = in.read(buf)) > 0) {
                        os.write(buf, 0, n);
                        got += n;
                        if (total > 0) {
                            int pct = (int) (got * 100 / total);
                            if (pct != lastPct) { lastPct = pct; if (p != null) p.on(pct, "下载中 " + pct + "%（" + (got / 1048576) + "/" + (total / 1048576) + "MB）"); }
                        }
                    }
                    os.flush();
                } finally { try { os.close(); } catch (Throwable t) { } try { in.close(); } catch (Throwable t) { } cn.disconnect(); }
                if (p != null) p.on(100, "下载完，正在取出 android.jar…");
                File dst = new File(home(c), "android.jar.tmp");
                if (dst.exists()) dst.delete();
                String ex = extractOne(zip, JAR_IN_ZIP, dst);
                if (ex.length() > 0) { last = ex; zip.delete(); continue; }
                String sha = sha256(dst);
                if (!JAR_SHA.equalsIgnoreCase(sha)) {
                    last = "android.jar 校验不过（拿到 " + (sha == null ? "?" : sha.substring(0, 16)) + "…，应为 " + JAR_SHA.substring(0, 16) + "…）";
                    dst.delete(); zip.delete(); continue;
                }
                if (dst.length() != JAR_SIZE) {
                    last = "android.jar 大小不对（" + dst.length() + "，应为 " + JAR_SIZE + "）";
                    dst.delete(); zip.delete(); continue;
                }
                if (jar(c).exists()) jar(c).delete();
                if (!dst.renameTo(jar(c))) { copyFile(dst, jar(c)); dst.delete(); }
                zip.delete();
                if (p != null) p.on(100, "android.jar 就位（校验通过 " + JAR_SHA.substring(0, 12) + "…）");
                return "";
            } catch (Throwable t) {
                last = String.valueOf(t);
                try { if (zip.exists()) zip.delete(); } catch (Throwable t2) { }
            }
        }
        return "下载 android.jar 没成：" + last + "（源＝官方 dl.google.com；网络通不通、是否被系统拦，都要看）";
    }

    /** 从 zip 里抽一个条目。返回空串＝成功。 */
    private static String extractOne(File zip, String entry, File dst) {
        try {
            ZipInputStream zs = new ZipInputStream(new FileInputStream(zip));
            try {
                ZipEntry e;
                byte[] buf = new byte[262144];
                while ((e = zs.getNextEntry()) != null) {
                    if (!e.getName().equals(entry)) continue;
                    OutputStream os = new FileOutputStream(dst);
                    try {
                        int n;
                        while ((n = zs.read(buf)) > 0) os.write(buf, 0, n);
                        os.flush();
                    } finally { try { os.close(); } catch (Throwable t) { } }
                    return "";
                }
            } finally { try { zs.close(); } catch (Throwable t) { } }
            return "包里没有 " + entry + "（源换了？）";
        } catch (Throwable t) { return "解包失败：" + t; }
    }

    public static String sha256(File f) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            FileInputStream in = new FileInputStream(f);
            try {
                byte[] buf = new byte[262144];
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            } finally { try { in.close(); } catch (Throwable t) { } }
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < d.length; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Throwable t) { return null; }
    }

    private static void copyFile(File src, File dst) throws Exception {
        InputStream in = new FileInputStream(src);
        try {
            OutputStream os = new FileOutputStream(dst);
            try {
                byte[] buf = new byte[262144];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                os.flush();
            } finally { try { os.close(); } catch (Throwable t) { } }
        } finally { try { in.close(); } catch (Throwable t) { } }
    }

    // ═══════════════════════ 进度双通道（通知栏 ＋ 小形象气泡） ═══════════════════════

    /** 进度回调：通知栏（实况型常驻）＋ 小形象气泡；一处维护，界面与 AI 触发都走它。 */
    public static Progress notifier(final Context c) {
        return new Progress() {
            private String last = "";
            public void on(int pct, String line) {
                try { Notifier.promoted(c, NID, "构建环境", line, pct); } catch (Throwable t) { }
                if (line == null || line.equals(last)) return;   // 气泡不刷同一条
                last = line;
                try { Mascot.bubble("⚒ 环境 · " + line); } catch (Throwable t) { }
            }
        };
    }

    /** 收尾：成功留一条普通通知（可以从通知栏划掉），失败留红色内容。 */
    public static void notifyDone(Context c, String line, boolean bad) {
        try { Notifier.send(c, NID, bad ? "构建环境 · 没成" : "构建环境 · 完成", line, false); } catch (Throwable t) { }
        try { Mascot.bubble((bad ? "⚠ " : "✓ ") + "环境 · " + line); } catch (Throwable t) { }
    }
}
