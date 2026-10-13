package app.reticle;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;

/**
 * 24 号单 · 运行目录定案：运行期数据一律落 /sdcard/Reticle/
 * （in/out 槽位、shots/loop/ocr 过程图、logs 日志、chain/tpl/fp 素材与模板）。
 * 该目录不可写时（没拿到「所有文件访问」）回退 App 私有外部目录，保证服务不倒；
 * 回退态每 60 秒重试一次公共目录，拿到权限后自动切回。
 */
public class Home {
    public static final String DIR = "/sdcard/Reticle";

    private static File cached = null;
    private static long lastRetry = 0L;

    public static synchronized File get(Context c) {
        if (cached != null && DIR.equals(cached.getAbsolutePath())) return cached;
        long now = System.currentTimeMillis();
        if (cached != null && (now - lastRetry) < 60000L) return cached;
        lastRetry = now;
        File d = new File(DIR);
        if (writable(d)) { cached = d; return cached; }
        File ext = (c == null) ? null : c.getExternalFilesDir(null);
        if (ext != null) cached = ext;
        else if (cached == null) cached = d;
        return cached;
    }

    public static File sub(Context c, String name) {
        File d = new File(get(c), name);
        try { if (!d.exists()) d.mkdirs(); } catch (Throwable t) { }
        return d;
    }

    private static boolean writable(File d) {
        try {
            if (!d.exists() && !d.mkdirs()) return false;
            if (!d.isDirectory()) return false;
            File t = new File(d, ".write-test");
            FileOutputStream o = new FileOutputStream(t);
            o.write(1);
            o.close();
            t.delete();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
