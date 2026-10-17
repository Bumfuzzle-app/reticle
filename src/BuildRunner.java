package app.reticle;

import android.content.Context;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 74 号单（2026-10-16）· 应用内跑构建（出包）。
 *
 * 【怎么跑】以**应用自己的身份** exec：/system/bin/sh 跑 build3.sh；
 *   工具链在 nativeLibraryDir（apk 的 lib/arm64-v8a）＝系统唯一允许应用直接执行的地方 ⇒ 不依赖 Shizuku。
 *   ECJ／d8／apksigner／BuildTool 由 /system/bin/app_process ＋ CLASSPATH 跑（dex 化后不需要 JVM）。
 * 【日志与产物】日志＝运行目录 build/last-build.log（用户看得见）；产物＝产物目录里的 *.apk。
 * 【签名】用户自己的调试钥匙（BuildEnv/key.p12），第一次构建时由 CertGen 现生成——不用作者机的钥匙。
 */
public class BuildRunner {

    private static volatile Process proc = null;
    private static volatile long startedAt = 0L;
    private static volatile String lastNote = "";

    public static boolean running() {
        Process p = proc;
        if (p == null) return false;
        try { p.exitValue(); proc = null; return false; } catch (Throwable alive) { return true; }
    }

    public static File dir(Context c) { File f = new File(Home.get(c), "build"); if (!f.exists()) f.mkdirs(); return f; }
    public static File logFile(Context c) { return new File(dir(c), "last-build.log"); }
    public static File outDir(Context c) { File f = new File(dir(c), "out"); if (!f.exists()) f.mkdirs(); return f; }

    /** 默认源码树＝运行目录/工程（用户把安卓工程放这儿；也可 build.conf 的 PROJECT_DIR= 指走）。 */
    public static File defaultProject(Context c) { return new File(Home.get(c), "工程"); }

    /** 77 号单：摊一个最小可跑工程（manifest ＋ strings.xml ＋ MainActivity.java）。已有工程＝拒绝覆盖。返回人话。 */
    public static String newProject(Context c, File proj, String name, String pkg) {
        if (proj == null) return "！工程目录不可用（看 build.conf 的 PROJECT_DIR=）";
        if (!proj.exists() && !proj.mkdirs()) return "！建不了工程目录：" + proj.getAbsolutePath();
        if (new File(proj, "AndroidManifest.xml").exists() || new File(proj, "src").exists())
            return "！这里已经有工程了（" + proj.getAbsolutePath() + "）：不覆盖别人的活。要另起一个就在 build.conf 写 PROJECT_DIR= 指到别处，或先把旧的挪走。";
        String nm = name == null ? "" : name.trim();
        if (nm.length() == 0) nm = "我的应用";
        String pk = pkg == null ? "" : pkg.trim().toLowerCase(java.util.Locale.ROOT);
        if (pk.length() == 0) pk = "com.example.myapp";
        if (!pk.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+"))
            return "！包名不合规：" + pk + "（要像 com.example.myapp：小写字母／数字／下划线，至少两段，别用关键字）";
        try {
            String dir = pk.replace('.', '/');
            File src = new File(proj, "src/" + dir);
            File val = new File(proj, "res/values");
            if (!src.mkdirs() || !val.mkdirs()) return "！建不了子目录（权限？）：" + proj.getAbsolutePath();
            writeText(new File(proj, "AndroidManifest.xml"), manifestXml(pk));
            writeText(new File(val, "strings.xml"), stringsXml(nm));
            writeText(new File(src, "MainActivity.java"), mainJava(pk, nm));
            StringBuilder sb = new StringBuilder();
            sb.append("工程就绪：").append(proj.getAbsolutePath()).append("\n");
            sb.append("应用名 ").append(nm).append("；包名 ").append(pk).append("\n");
            sb.append("文件：AndroidManifest.xml ／ res/values/strings.xml ／ src/").append(dir).append("/MainActivity.java\n");
            sb.append("下一步：build action=start 出包（1~3 分钟）→ build action=status 看进度 → build action=install 装到手机。\n");
            sb.append("改界面＝改 MainActivity.java；改应用名＝改 strings.xml 的 app_name。\n");
            return sb.toString();
        } catch (Throwable t) { return "！摊工程失败：" + t; }
    }

    private static void writeText(File f, String s) throws Exception {
        FileOutputStream fo = new FileOutputStream(f);
        try { fo.write(s.getBytes("UTF-8")); } finally { try { fo.close(); } catch (Throwable t) { } }
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String manifestXml(String pk) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
             + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
             + "    package=\"" + pk + "\"\n"
             + "    android:versionCode=\"1\"\n"
             + "    android:versionName=\"1.0\">\n"
             + "    <uses-sdk android:minSdkVersion=\"21\" android:targetSdkVersion=\"33\" />\n"
             + "    <application android:label=\"@string/app_name\"\n"
             + "        android:icon=\"@android:drawable/sym_def_app_icon\"\n"
             + "        android:allowBackup=\"true\"\n"
             + "        android:theme=\"@android:style/Theme.DeviceDefault.Light\">\n"
             + "        <activity android:name=\".MainActivity\" android:exported=\"true\" android:windowSoftInputMode=\"adjustResize\">\n"
             + "            <intent-filter>\n"
             + "                <action android:name=\"android.intent.action.MAIN\" />\n"
             + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
             + "            </intent-filter>\n"
             + "        </activity>\n"
             + "    </application>\n"
             + "</manifest>\n";
    }

    private static String stringsXml(String nm) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
             + "<resources>\n"
             + "    <string name=\"app_name\">" + esc(nm) + "</string>\n"
             + "</resources>\n";
    }

    private static String mainJava(String pk, String nm) {
        return "package " + pk + ";\n"
             + "\n"
             + "import android.app.Activity;\n"
             + "import android.os.Bundle;\n"
             + "import android.widget.TextView;\n"
             + "\n"
             + "/** 最小可跑界面：改这个文件就是改你的 App。 */\n"
             + "public class MainActivity extends Activity {\n"
             + "    @Override\n"
             + "    protected void onCreate(Bundle b) {\n"
             + "        super.onCreate(b);\n"
             + "        TextView t = new TextView(this);\n"
             + "        t.setText(\"" + nm.replace("\\", "").replace("\"", "") + " 起好了\\n改 MainActivity.java 就是改界面\");\n"
             + "        t.setTextSize(18);\n"
             + "        setContentView(t);\n"
             + "    }\n"
             + "}\n";
    }

    /** 启动一次构建。返回空串＝已开跑；否则人话原因。 */
    public static String start(Context c, File sb, File keep, boolean release) {
        synchronized (BuildRunner.class) {
            if (running()) return "还在跑的是上一次 build action=start（已跑 " + secs() + " 秒）；先 build action=status 看进度";
            if (!BuildEnv.ready(c)) return "环境没就绪：\n" + BuildEnv.missing(c) + "→ 先在应用里点『打包 App · 一键准备环境』（或 build action=env）";
            if (sb == null || !new File(sb, "AndroidManifest.xml").exists())
                return "没找到工程：要一个含 AndroidManifest.xml 的目录（现在是 " + (sb == null ? "—" : sb.getAbsolutePath()) + "）";
            String kr = CertGen.ensure(BuildEnv.key(c), keyPass(), "Reticle Debug");
            if (kr.length() > 0) return kr;
            try {
                BuildEnv.unpack(c);
                File log = logFile(c);
                if (log.exists()) log.delete();

                ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", BuildEnv.script(c).getAbsolutePath());
                pb.directory(BuildEnv.home(c));
                pb.redirectErrorStream(true);
                pb.redirectOutput(ProcessBuilder.Redirect.to(log));
                java.util.Map<String, String> e = pb.environment();
                e.put("LIB", BuildEnv.lib(c).getAbsolutePath());
                e.put("RES", BuildEnv.res(c).getAbsolutePath());
                e.put("JAR", BuildEnv.jar(c).getAbsolutePath());
                e.put("SB", sb.getAbsolutePath());
                e.put("KEEP", keep == null ? "" : keep.getAbsolutePath());
                e.put("OUT", outDir(c).getAbsolutePath());
                e.put("W", new File(BuildEnv.work(c), "run").getAbsolutePath());
                e.put("KEY", BuildEnv.key(c).getAbsolutePath());
                e.put("KPASS", keyPass());
                e.put("RELEASE", release ? "1" : "0");
                proc = pb.start();
                startedAt = System.currentTimeMillis();
                lastNote = "已开跑：" + sb.getName();
                
                new Thread(new Runnable() { public void run() { try { proc.waitFor(); } catch (Throwable t) { } lastNote = "跑完（" + secs() + "s）"; } }).start();
                return "";
            } catch (Throwable t) {
                return "启动构建失败：" + t;
            }
        }
    }



    public static String keyPass() { return "reticle"; }

    public static long secs() { return startedAt == 0 ? 0 : (System.currentTimeMillis() - startedAt) / 1000; }

    /** 状态：在跑／跑完＋日志尾 ＋ 产物清单（含 sha256）。 */
    public static String status(Context c) {
        StringBuilder sb = new StringBuilder();
        sb.append(running() ? ("在跑，" + secs() + "s（日志：" + logFile(c).getAbsolutePath() + "）") : Words.RP_BUILD_NOTRUN);
        sb.append("\n");
        String tail = running() ? tail(logFile(c), 12) : "";
        if (tail.length() > 0) sb.append("--- 日志尾 ---\n").append(tail);
        File[] apks = apks(c);
        if (apks != null && apks.length > 0) {
            sb.append("--- 产物 ---\n");
            for (int i = 0; i < apks.length; i++) {
                sb.append(apks[i].getName()).append("  ").append(apks[i].length()).append(" B  sha256=").append(clip(BuildEnv.sha256(apks[i]))).append("\n");
            }
        }
        if (running()) sb.append(Words.RP_BUILD_NEXT_RUN);
        else if (apks != null && apks.length > 0) sb.append(Words.RP_BUILD_NEXT_APK);
        else sb.append(Words.RP_BUILD_NEXT_NEW);
        return sb.toString();
    }

    public static JSONObject statusJson(Context c) {
        JSONObject o = new JSONObject();
        try {
            o.put("running", running() ? 1 : 0);
            o.put("seconds", secs());
            o.put("log", logFile(c).getAbsolutePath());
            File[] a = apks(c);
            o.put("apk", (a != null && a.length > 0) ? a[0].getAbsolutePath() : "");
            o.put("apkSha256", (a != null && a.length > 0) ? clip(BuildEnv.sha256(a[0])) : "");
            o.put("ready", BuildEnv.ready(c) ? 1 : 0);
        } catch (Throwable t) { }
        return o;
    }

    public static File[] apks(Context c) {
        File[] fs = outDir(c).listFiles();
        if (fs == null) return null;
        java.util.ArrayList<File> r = new java.util.ArrayList<File>();
        for (int i = 0; i < fs.length; i++) if (fs[i].getName().endsWith(".apk")) r.add(fs[i]);
        if (r.size() == 0) return null;
        File[] out = r.toArray(new File[0]);
        java.util.Arrays.sort(out, new java.util.Comparator<File>() { public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); } });
        return out;
    }

    private static String clip(String s) { return s == null ? "?" : s; }

    static String tail(File f, int lines) {
        try {
            if (!f.exists()) return "";
            java.io.RandomAccessFile ra = new java.io.RandomAccessFile(f, "r");
            try {
                long len = ra.length();
                long from = Math.max(0, len - 8192);
                ra.seek(from);
                byte[] b = new byte[(int) (len - from)];
                ra.readFully(b);
                String s = new String(b, "UTF-8");
                String[] ls = s.split("\n");
                int st = Math.max(0, ls.length - lines);
                StringBuilder sb = new StringBuilder();
                for (int i = st; i < ls.length; i++) sb.append(ls[i]).append("\n");
                return sb.toString();
            } finally { try { ra.close(); } catch (Throwable t) { } }
        } catch (Throwable t) { return ""; }
    }

    /** 77.5 号单：工程来历一句话（包名／文件三件／最近改动）——让陌生 AI 一眼看清这是谁的工程。 */
    public static String brief(File proj) {
        if (proj == null || !proj.isDirectory()) return "—";
        String pk = manifestPkg(new File(proj, "AndroidManifest.xml"));
        StringBuilder sb = new StringBuilder();
        sb.append(proj.getAbsolutePath()).append("（包名 ").append(pk.length() > 0 ? pk : "没读到");
        sb.append("｜文件 AndroidManifest.xml ").append(new File(proj, "AndroidManifest.xml").exists() ? "有" : "缺");
        sb.append("／res ").append(new File(proj, "res").isDirectory() ? "有" : "缺");
        sb.append("／src ").append(new File(proj, "src").isDirectory() ? "有" : "缺");
        long lm = newest(proj, 0);
        if (lm > 0) sb.append("｜最近改动 ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT).format(new java.util.Date(lm)));
        sb.append("）");
        return sb.toString();
    }

    /** 从 AndroidManifest.xml 里取 package="..."（读前 4KB 够用）；读不到＝空串。 */
    private static String manifestPkg(File m) {
        try {
            if (!m.exists()) return "";
            byte[] b = new byte[(int) Math.min(m.length(), 4096L)];
            java.io.FileInputStream in = new java.io.FileInputStream(m);
            int n = 0;
            try { n = in.read(b); } finally { try { in.close(); } catch (Throwable t) { } }
            if (n <= 0) return "";
            String s = new String(b, 0, n, "UTF-8");
            int i = s.indexOf("package=\"");
            if (i < 0) return "";
            int j = s.indexOf('"', i + 9);
            return j < 0 ? "" : s.substring(i + 9, j);
        } catch (Throwable t) { return ""; }
    }

    /** 工程里最新的文件改动时间（深度限 6 层）；读不到＝0。 */
    private static long newest(File f, int depth) {
        if (f == null || depth > 6) return 0L;
        if (f.isFile()) return f.lastModified();
        File[] fs = f.listFiles();
        if (fs == null) return 0L;
        long m = 0L;
        for (int i = 0; i < fs.length; i++) {
            long t = newest(fs[i], depth + 1);
            if (t > m) m = t;
        }
        return m;
    }

    /** 83 号单：装包走系统安装器。先把 apk 复制到 dl/（FileProvider 允许的目录），再拉安装器，用户自己点确认。 */
    public static String install(Context c) {
        File[] a = apks(c);
        if (a == null || a.length == 0) return "产物目录里还没有 apk。\n先 build action=start 出包。";
        File apk = a[0];
        String fg = "";
        try { fg = VeriService.foregroundPkg(); } catch (Throwable t) { }
        if (fg.length() > 0 && !fg.equals(c.getPackageName())) return "先切回 Reticle 再装。\n安装器要 Reticle 在前台。";
        File dl = new File(Home.get(c), "dl");
        if (!dl.isDirectory()) dl.mkdirs();
        File dst = new File(dl, apk.getName());
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(apk);
            java.io.FileOutputStream fo = new java.io.FileOutputStream(dst);
            try {
                byte[] b = new byte[65536];
                int n;
                while ((n = in.read(b)) > 0) fo.write(b, 0, n);
            } finally {
                try { in.close(); } catch (Throwable t) { }
                try { fo.close(); } catch (Throwable t) { }
            }
        } catch (Throwable t) { return "复制安装包到 dl 失败。\n" + dst.getAbsolutePath(); }
        String r = UpdateCheck.install(c, dst);
        if (r != null) return r;
        return "已拉起系统安装器。\n等你点确认。\n装完 Reticle 会重启。\nMCP 断一两分钟。";
    }

    /** 走 Shizuku 以 shell 身份跑一条命令（不通＝null）。 */
    private static String shx(String cmd) {
        try {
            String r = VeriService.shxRun(cmd, 180000L);
            JSONObject o = new JSONObject(r);
            if (o.optInt("ok", 0) != 1) return null;
            String out = o.optString("out", "") + o.optString("err", "");
            return out.length() == 0 ? "exit=" + o.optInt("exit", -1) : out;
        } catch (Throwable t) { return null; }
    }
}
