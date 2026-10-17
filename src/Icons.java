package app.reticle;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 73 号单（2026-10-16）· 应用图标
 *
 * ★技术现实（写死在注释里，免得后人再绕）：非 root 下**应用图标本身不能运行时替换**——
 *   `android:icon` 与 activity-alias 的 icon 都是**编译期资源**。
 *   所以这里只做两件真能做成的事：
 *   ①**预置款切换**：manifest 里 4 个 activity-alias（各带一套图标），用
 *     `setComponentEnabledSetting` 在同一时刻只启用一个 ⇒ 桌面／最近任务里**真的换图标**
 *     （不用重装、不改包名）。★这叫"换别名"，界面上不许写成"更换应用图标"。
 *   ②**上传的图 → 桌面快捷方式**：`ShortcutInfo` ＋ `requestPinShortcut` ⇒ 桌面上多一个
 *     带这张图的入口（要用户在系统弹窗里点确认）。★那是"桌面入口"，不是"应用图标"。
 *
 * 默认款＝57 号单那套（`ic_launcher`），一个字没动。
 */
public class Icons {

    /** 4 个别名（顺序＝界面上从左到右） */
    public static final String[] ALIAS = { ".IconDefault", ".IconCyan", ".IconMin", ".IconWarm" };
    /** 预览用的图标资源 */
    public static final int[] PREVIEW = {
            R.mipmap.ic_launcher, R.mipmap.ic_launcher_2, R.mipmap.ic_launcher_3, R.mipmap.ic_launcher_4
    };
    /** 别名对应的 label 资源 id（＝桌面图标下的名字） */
    public static final int[] LABEL = { R.string.ic_lab_0, R.string.ic_lab_1, R.string.ic_lab_2, R.string.ic_lab_3 };

    private static ComponentName cn(Context c, int i) {
        return new ComponentName(c.getPackageName(), c.getPackageName() + ALIAS[i]);
    }

    /** 当前启用中的那一款（找不到＝0＝默认款；不抛） */
    public static int current(Context c) {
        try {
            PackageManager pm = c.getPackageManager();
            for (int i = 0; i < ALIAS.length; i++) {
                int st = pm.getComponentEnabledSetting(cn(c, i));
                if (st == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return i;
            }
        } catch (Throwable t) { }
        return 0;
    }

    /** 切到第 i 款。返回给用户看的一句话（成功／失败都说人话）。 */
    public static String apply(Context c, int i) {
        if (i < 0 || i >= ALIAS.length) return Words.CF_MSG_ICON_FAIL;
        try {
            PackageManager pm = c.getPackageManager();
            String name = c.getString(LABEL[i]);
            // 先把目标启用起来，再关掉其余（避免中途一个入口都没有的空窗）
            pm.setComponentEnabledSetting(cn(c, i), PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            for (int j = 0; j < ALIAS.length; j++) {
                if (j == i) continue;
                pm.setComponentEnabledSetting(cn(c, j), PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            }
            return String.format(Words.CF_MSG_ICON_FMT, name);
        } catch (Throwable t) {
            return Words.CF_MSG_ICON_FAIL + "（" + t + "）";
        }
    }

    // ───────────────── 上传图片 → 桌面快捷方式 ─────────────────

    static final String SHORTCUT_ID = "reticle_custom_icon";
    static final String ICON_PNG = "桌面图标.png";

    /**
     * 把用户挑的图 **居中裁方**（不许变形）→ 512×512 → requestPinShortcut。
     * 返回一句人话给用户（成功也只是一句"已请求，去桌面确认"——真出现要用户点系统弹窗）。
     */
    public static String pinShortcut(Context c, Intent data) {
        if (data == null || data.getData() == null) return Words.CF_ICON_PIN_CANCEL;
        Bitmap sq;
        try {
            sq = loadSquare(c, data.getData());
        } catch (TooBig e) {
            return Words.CF_ICON_PIN_TOOBIG;
        } catch (Throwable t) {
            return Words.CF_ICON_PIN_BAD;
        }
        if (sq == null) return Words.CF_ICON_PIN_BAD;
        // 留一份（下次想再添加／换掉时好找；放在外观文件夹，用户自己删得掉）
        try {
            File d = Home.sub(c, "外观");
            File f = new File(d, ICON_PNG);
            FileOutputStream o = new FileOutputStream(f);
            sq.compress(Bitmap.CompressFormat.PNG, 100, o);
            o.close();
        } catch (Throwable t) { }
        try {
            ShortcutManager sm = (ShortcutManager) c.getSystemService(Context.SHORTCUT_SERVICE);
            if (sm == null || !sm.isRequestPinShortcutSupported()) return Words.CF_ICON_PIN_NOSUP;
            Intent open = new Intent(c, MainActivity.class);
            open.setAction(Intent.ACTION_MAIN);
            open.addCategory(Intent.CATEGORY_LAUNCHER);
            ShortcutInfo info = new ShortcutInfo.Builder(c, SHORTCUT_ID)
                    .setShortLabel(c.getString(R.string.app_name))
                    .setLongLabel(c.getString(R.string.app_name) + "（自定义图标）")
                    .setIcon(Icon.createWithBitmap(sq))
                    .setIntent(open)
                    .build();
            boolean ok = sm.requestPinShortcut(info, null);
            return ok ? Words.CF_ICON_PIN_OK : Words.CF_ICON_PIN_FAIL;
        } catch (Throwable t) {
            return Words.CF_ICON_PIN_FAIL + "（" + t + "）";
        }
    }

    static class TooBig extends Exception { }

    static Bitmap loadSquare(Context c, Uri u) throws Throwable {
        InputStream in = null;
        try {
            in = c.getContentResolver().openInputStream(u);
            if (in == null) throw new Exception("no stream");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n; long tot = 0L;
            while ((n = in.read(buf)) > 0) {
                tot += n;
                if (tot > 8L * 1024 * 1024) throw new TooBig();
                bo.write(buf, 0, n);
            }
            byte[] bytes = bo.toByteArray();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int sample = 1;
            while (o.outWidth / sample > 1024 || o.outHeight / sample > 1024) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o2);
            if (b == null) return null;
            int s = Math.min(b.getWidth(), b.getHeight());
            if (s <= 0) return null;
            Bitmap cut = Bitmap.createBitmap(b, (b.getWidth() - s) / 2, (b.getHeight() - s) / 2, s, s);
            if (cut.getWidth() != 512) cut = Bitmap.createScaledBitmap(cut, 512, 512, true);
            return cut;
        } finally {
            try { if (in != null) in.close(); } catch (Throwable t) { }
        }
    }
}
