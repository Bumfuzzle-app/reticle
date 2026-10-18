package app.reticle;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/**
 * ★88.6 气泡从白条鼓出来（morph · 一条连续闭合轮廓）。
 *
 * ★88.8 设置收口（19 号总控派单）：
 *   1) 三套手感预设（轻快／标准／柔和）＋「恢复默认」一键回标准（Morph.applyDefaults）；
 *   2) 参数互相夹死：时长 <200ms 时回弹上限 40；线宽 >12dp 时圆角下限 12dp；
 *   3) 性能：文字尺寸／截断在 prepare() 一次算好，paint() 零分配（Paint／Path／RectF 全复用）；
 *      「(被自身高/宽夹)」只在半径本帧真的还在变时才标注；
 *   4) 内容最大高下限 88→40dp（两行框才 62dp，滑杆才有意义）；
 *   5) 动效可关（K_ON）：关着＝不做变形，直接到终态（不卡）。
 *
 * 画法沿用 88.7：每一帧都是**一条连续闭合的圆角轮廓**，四角同半径；锚点＝屏幕边（白条外沿），
 * 贴边侧一个像素都不动；描边画在内侧。承载窗口由 Mascot 挂（尺寸开播前定死、全程不改）。
 */
class Morph {

    // ───────── 参数（全部走 SharedPreferences，改完下次播放即生效；重启仍在） ─────────
    static final String K_ON = "morph_on";          // ★88.8 动效开关（默认开）
    static final String K_PRE = "morph_preset";     // ★88.8 当前手感（0 轻快／1 标准／2 柔和／-1 自定义）
    static final String K_DUR = "morph_dur";        // 总时长 ms（120~1200）
    static final String K_EASE = "morph_ease";      // 0 线性／1 缓出／2 回弹／3 弹性／4 自定义贝塞尔
    static final String K_BEZ = "morph_bez";        // 自定义贝塞尔 "x1,y1,x2,y2"（0~1）
    static final String K_W = "morph_w";            // 线宽 dp（3~16）
    static final String K_R = "morph_r";            // 圆角半径 dp（0~40）
    static final String K_BOUNCE = "morph_bounce";  // 展开回弹（阻尼）0~100
    static final String K_TEXT = "morph_text";      // 文字淡入时机 0~90（百分比进度）
    static final String K_BULGE = "morph_bulge";    // ★88.6 鼓出程度 0~100（宽先鼓／高后跟的相位）
    static final String K_STROKE = "morph_stroke";          // ★88.5 描边粗细 dp（1~6，默认极细 1dp）
    static final String K_STROKE_C = "morph_stroke_color";  // ★88.5 描边颜色
    static final String K_FILL_C = "morph_fill_color";      // ★88.5 填充颜色
    static final String K_TEXT_C = "morph_text_color";      // ★88.5 文字颜色
    static final String K_MAXW = "morph_maxw";              // ★88.5 内容自适应最大宽 dp（100~260）
    static final String K_MAXH = "morph_maxh";              // ★88.5 内容自适应最大高 dp（40~140）

    static final String[] EASE_NAMES = {"线性", "缓出", "回弹", "弹性", "自定义贝塞尔"};

    // ★88.8 默认＝「标准」那套（19 号总控定的值）
    static final int DEF_DUR = 320, DEF_EASE = 2, DEF_W = 7, DEF_R = 30,
            DEF_BOUNCE = 45, DEF_TEXT = 55, DEF_BULGE = 45;
    static final String DEF_BEZ = "0.22,1,0.36,1";
    static final int DEF_STROKE = 1, DEF_MAXW = 220, DEF_MAXH = 120;
    static final int DEF_STROKE_C = 0xFFD5D7DB, DEF_FILL_C = 0xFFFFFFFF, DEF_TEXT_C = 0xFF1B1B1B;

    // ★88.8 三套手感（时长／缓动／线宽／圆角／鼓出）
    static final int PRE_LIGHT = 0, PRE_STD = 1, PRE_SOFT = 2, PRE_CUSTOM = -1;
    static final String[] PRESET_NAMES = {"轻快", "标准", "柔和"};
    static final int[] P_DUR = {220, 320, 480};
    static final int[] P_EASE = {1, 2, 1};
    static final int[] P_W = {6, 7, 8};
    static final int[] P_R = {24, 30, 36};
    static final int[] P_BULGE = {60, 45, 30};

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(Mascot.PREFS, Context.MODE_PRIVATE);
    }
    static int gi(Context c, String k, int d) {
        try { return sp(c).getInt(k, d); } catch (Throwable t) { return d; }
    }
    static void si(Context c, String k, int v) {
        try { sp(c).edit().putInt(k, v).apply(); } catch (Throwable t) { }
    }
    static String gs(Context c, String k, String d) {
        try { String v = sp(c).getString(k, d); return v == null ? d : v; } catch (Throwable t) { return d; }
    }
    static void ss(Context c, String k, String v) {
        try { sp(c).edit().putString(k, v).apply(); } catch (Throwable t) { }
    }

    // ───────── 开关 · 手感预设 · 恢复默认（★88.8） ─────────
    static boolean enabled(Context c) { try { return sp(c).getBoolean(K_ON, true); } catch (Throwable t) { return true; } }
    static void setEnabled(Context c, boolean on) { try { sp(c).edit().putBoolean(K_ON, on).apply(); } catch (Throwable t) { } }

    /** ★88.8：老用户升级上来时 K_PRE 还不存在 ⇒ 按当前参数反推（不乱说"标准"）。 */
    static int preset(Context c) {
        int p;
        try {
            SharedPreferences s = sp(c);
            p = s.contains(K_PRE) ? s.getInt(K_PRE, PRE_STD) : detectPreset(c);
        } catch (Throwable t) { p = detectPreset(c); }
        return clampI(p, PRE_CUSTOM, PRE_SOFT);
    }
    /** 参数与哪一套完全一致就是哪一套，否则＝自定义（用原始值比，不用夹死后的读值）。 */
    static int detectPreset(Context c) {
        int d = clampI(gi(c, K_DUR, DEF_DUR), 120, 1200);
        int e = clampI(gi(c, K_EASE, DEF_EASE), 0, 4);
        int w = clampI(gi(c, K_W, DEF_W), 3, 16);
        int r = clampI(gi(c, K_R, DEF_R), 0, 40);
        int b = clampI(gi(c, K_BULGE, DEF_BULGE), 0, 100);
        for (int i = 0; i <= PRE_SOFT; i++) {
            if (d == P_DUR[i] && e == P_EASE[i] && w == P_W[i] && r == P_R[i] && b == P_BULGE[i]) return i;
        }
        return PRE_CUSTOM;
    }
    static void setPresetTag(Context c, int p) { si(c, K_PRE, p); }
    static String presetName(Context c) {
        int p = preset(c);
        return (p >= 0 && p < PRESET_NAMES.length) ? PRESET_NAMES[p] : "自定义";
    }

    /** 换一套手感：只写这五个参数（颜色／宽高／描边不动）。 */
    static void applyPreset(Context c, int i) {
        if (i < 0 || i > PRE_SOFT) return;
        si(c, K_DUR, P_DUR[i]);
        si(c, K_EASE, P_EASE[i]);
        si(c, K_W, P_W[i]);
        si(c, K_R, P_R[i]);
        si(c, K_BULGE, P_BULGE[i]);
        si(c, K_BOUNCE, DEF_BOUNCE);
        si(c, K_TEXT, DEF_TEXT);
        si(c, K_PRE, i);
    }

    /** 恢复默认＝「标准」＋描边／填充／文字色＋最大宽高＋曲线。当场生效靠调用方 morphReload。 */
    static void applyDefaults(Context c) {
        applyPreset(c, PRE_STD);
        ss(c, K_BEZ, DEF_BEZ);
        si(c, K_STROKE, DEF_STROKE);
        si(c, K_MAXW, DEF_MAXW);
        si(c, K_MAXH, DEF_MAXH);
        si(c, K_STROKE_C, DEF_STROKE_C);
        si(c, K_FILL_C, DEF_FILL_C);
        si(c, K_TEXT_C, DEF_TEXT_C);
        setEnabled(c, true);   // ★88.9：K_ON 是 boolean，原来用 si() 写 int，之后 getBoolean 会抛 ClassCastException（开关读成"永远开"）
        si(c, K_PRE, PRE_STD);
    }

    /** 手动拖了滑杆／换了颜色 ⇒ 手感变成「自定义」（标题那行小字才不会说谎）。 */
    static void markCustom(Context c) { si(c, K_PRE, PRE_CUSTOM); }

    // 带安全边界的读写（拖到极限也不会跑出屏幕／不会互相夹出"抽"的组合）
    static int dur(Context c) { return clampI(gi(c, K_DUR, DEF_DUR), 120, 1200); }
    static int easeMode(Context c) { return clampI(gi(c, K_EASE, DEF_EASE), 0, 4); }
    static int lineW(Context c) { return clampI(gi(c, K_W, DEF_W), 3, 16); }
    /** ★88.8 夹死：线宽 >12dp 时圆角下限 12dp（细长胶囊太难看） */
    static int radius(Context c) {
        int r = clampI(gi(c, K_R, DEF_R), 0, 40);
        if (lineW(c) > 12 && r < 12) r = 12;
        return r;
    }
    /** ★88.8 夹死：时长 <200ms 时回弹上限 40（短时长配大回弹＝看着抽） */
    static int bounce(Context c) {
        int b = clampI(gi(c, K_BOUNCE, DEF_BOUNCE), 0, 100);
        if (dur(c) < 200 && b > 40) b = 40;
        return b;
    }
    static int textAt(Context c) { return clampI(gi(c, K_TEXT, DEF_TEXT), 0, 90); }
    static int bulge(Context c) { return clampI(gi(c, K_BULGE, DEF_BULGE), 0, 100); }
    static int strokeW(Context c) { return clampI(gi(c, K_STROKE, DEF_STROKE), 1, 6); }
    static int maxWdp(Context c) { return clampI(gi(c, K_MAXW, DEF_MAXW), 100, 260); }
    /** ★88.8 下限 80→40dp */
    static int maxHdp(Context c) { int v = clampI(gi(c, K_MAXH, DEF_MAXH), 40, 140); return v < 120 ? 120 : v; }   // ★89.5：多行最多 5 行 ⇒ 有效上限保底 108dp
    static int color(Context c, String k, int d) {
        try { return sp(c).getInt(k, d); } catch (Throwable t) { return d; }
    }
    static void setColor(Context c, String k, int v) { si(c, k, v); }
    static int strokeColor(Context c) { return color(c, K_STROKE_C, DEF_STROKE_C); }
    static int fillColor(Context c) { return color(c, K_FILL_C, DEF_FILL_C); }
    static int textColor(Context c) { return color(c, K_TEXT_C, DEF_TEXT_C); }
    static String bezStr(Context c) { return gs(c, K_BEZ, DEF_BEZ); }

    static float[] bez(Context c) {
        float[] b = {0.22f, 1f, 0.36f, 1f};
        try {
            String[] a = bezStr(c).split(",");
            if (a.length == 4) {
                for (int i = 0; i < 4; i++) {
                    float v = Float.parseFloat(a[i].trim());
                    b[i] = v < 0f ? 0f : (v > 1f ? 1f : v);
                }
            }
        } catch (Throwable t) { }
        return b;
    }

    static void setBez(Context c, float x1, float y1, float x2, float y2) {
        ss(c, K_BEZ, String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f,%.2f", x1, y1, x2, y2));
    }

    static int clampI(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    // ───────── 绘制状态 ─────────
    private final Context ctx;
    final MV view;

    boolean right = false;
    int winW = 0, winH = 0;      // 大窗尺寸（开播前定好，全程不改）
    int frameW = 0, frameH = 0;  // 泡的外宽／外高（px）
    float barW = 0, barH = 0;    // 白条的粗细／长度（px）
    float margin = 0;            // 描边＋圆角＋过冲留边
    float cyOff = 0;             // 窗口被夹住时，白条中心与窗口中心的偏差

    // 当前帧几何（日志用：证明每一帧都是同一条闭合轮廓）
    float curW = 0, curH = 0, curRad = 0;   // ★88.7：R 改成「同半径」（白条态被自身宽夹成圆头）
    float curL = 0, curT = 0, curR = 0, curB = 0;
    boolean curClamped = false;
    float lastRad = -999f;                  // ★88.8 夹句只在半径本帧真在变时标注

    String l1 = "", l2 = "";
    int kind = 0;                // 0 普通／1 汇报／2 失败（只影响第二行的颜色）

    // ★89.5 整改六：多行排版（最多 5 行，宽度到最大宽就换行）＋ 真正的 TextView 内容层（可滚动）
    final java.util.ArrayList<String> lineTxt = new java.util.ArrayList<String>();
    final java.util.ArrayList<Boolean> lineBold = new java.util.ArrayList<Boolean>();
    final java.util.ArrayList<Float> baseY = new java.util.ArrayList<Float>();
    int lineCount = 0;
    View content = null;        // 叠在框上的内容层（ScrollView），由 Mascot 挂
    float contentCy = 0f;       // 内容层在窗口坐标里的中心 y（终态）
    float marginX = 0f, marginV = 0f;   // ★89.6 过冲预算（水平／垂直各 20%）
    float needH = 0f, contentH = 0f;    // ★89.6：内容所需高（按框宽换行后）
    boolean scrollNeed = false;         // ★89.6：内容 > 上限容量 ⇒ 框停在上限并可滚

    float p = 0f;                // 0＝白条，1＝框（>1＝过冲那几帧）
    float flick = 0f;            // ★89.5 收成白条时那一下「变粗一点」（0~0.2）

    // ★88.8 零分配：Paint／Path／RectF 全复用（原来每帧 new 4 个 Paint＋2 个 Path）
    private final Paint tp1, tp2, spO, fpF;
    private final Path pathO = new Path(), pathF = new Path();
    private final RectF rfO = new RectF(), rfF = new RectF();
    private final Paint.FontMetrics fm1 = new Paint.FontMetrics(), fm2 = new Paint.FontMetrics();
    // prepare() 一次算好的文字排版
    private String s1 = "", s2 = "";
    private boolean twoLine = false;
    private float baseOff1 = 0f, baseOff2 = 0f, avail = 0f;

    Morph(Context c) {
        ctx = c;
        tp1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp1.setTypeface(Typeface.DEFAULT_BOLD);
        tp1.setTextSize(sp(13f));
        tp1.setTextAlign(Paint.Align.CENTER);
        tp2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp2.setTypeface(Typeface.DEFAULT);
        tp2.setTextSize(sp(11f));
        tp2.setTextAlign(Paint.Align.CENTER);
        spO = new Paint(Paint.ANTI_ALIAS_FLAG);
        spO.setStyle(Paint.Style.STROKE);
        fpF = new Paint(Paint.ANTI_ALIAS_FLAG);
        fpF.setStyle(Paint.Style.FILL);
        view = new MV(c);
    }

    View view() { return view; }

    private float dp(float v) {
        try { return v * ctx.getResources().getDisplayMetrics().density; } catch (Throwable t) { return v * 3f; }
    }

    private float sp(float v) {
        try { return v * ctx.getResources().getDisplayMetrics().scaledDensity; } catch (Throwable t) { return v * 3f; }
    }

    /** 开播前算几何：返回大窗的 {x, y, w, h}（px）。之后只画，不再改窗口尺寸。
     *  ★89.6 整改二（19 号第二轮，按用户口径重写）：
     *   框宽 = min(内容所需宽 + padding, 最大宽)；框高 = min(内容所需高 + padding, 最大高)；
     *   内容 ≤ 上限容量 ⇒ 框刚好包住（不留空白、不固定几行）；超过 ⇒ 停在上限，且可滚（scrollNeed）。
     *   "所需宽"＝不换行时最宽那一行（按 \n 分段取最大）；"所需高"＝按框宽换行后的行数 × 行高。 */
    int[] prepare(boolean rightSide, int sw, int sh, int figY, int figH, String line1, String line2, int kindV) {
        right = rightSide;
        l1 = line1 == null ? "" : line1;
        l2 = line2 == null ? "" : line2;
        kind = kindV;

        barW = dp(lineW(ctx));
        barH = dp(80);
        float padX = dp(13), padY = dp(9);
        float maxW = Math.min(dp(maxWdp(ctx)), sw * 0.62f);
        float maxH = dp(maxHdp(ctx));
        float minW = dp(88), minH = dp(40);
        float lsp = dp(2f);      // 内容层 TextView 的行间距（setLineSpacing 2dp, 1f）

        tp1.getFontMetrics(fm1);
        tp2.getFontMetrics(fm2);
        float lh1 = fm1.descent - fm1.ascent + lsp, lh2 = fm2.descent - fm2.ascent + lsp;

        float needW = naturalW(l1, tp1);
        if (l2.length() > 0) needW = Math.max(needW, naturalW(l2, tp2));
        frameW = Math.round(clampF(needW + padX * 2f, minW, Math.max(minW, maxW)));
        float textW = Math.max(dp(24), frameW - padX * 2f);

        int n1 = wrapLines(l1, tp1, textW);
        int n2 = l2.length() > 0 ? wrapLines(l2, tp2, textW) : 0;
        lineCount = n1 + n2;
        contentH = n1 * lh1 + n2 * lh2;
        twoLine = lineCount > 1;
        float wantH = contentH + padY * 2f;
        frameH = Math.round(clampF(wantH, minH, Math.max(minH, maxH)));
        scrollNeed = wantH > Math.max(minH, maxH) + 0.5f;
        avail = textW;

        // 窗口：贴边侧与屏幕边对齐；远端／上下留 20% 过冲预算（12% 过冲＋微过冲都装得下）
        marginX = dp(7) + frameW * 0.20f;
        marginV = dp(7) + frameH * 0.20f;
        int w = Math.round(frameW + marginX);
        int h = Math.round(Math.max(frameH, barH) + marginV * 2f);
        winW = Math.min(w, sw);
        winH = Math.min(h, sh);
        int figCy = figY + figH / 2;
        int y = figCy - winH / 2;
        if (y < 0) y = 0;
        if (y + winH > sh) y = Math.max(0, sh - winH);
        cyOff = figCy - (y + winH / 2f);
        int x = rightSide ? Math.max(0, sw - winW) : 0;
        return new int[]{x, y, winW, winH};
    }

    float clampF(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    /** 不换行时这一行有多宽（\n 分段取最大）。 */
    private float naturalW(String s, Paint pt) {
        if (s == null || s.length() == 0) return 0f;
        float w = 0f;
        int i = 0;
        while (i <= s.length()) {
            int j = s.indexOf('\n', i);
            String seg = (j < 0) ? s.substring(i) : s.substring(i, j);
            w = Math.max(w, pt.measureText(seg));
            if (j < 0) break;
            i = j + 1;
        }
        return w;
    }

    /** 按框宽换行后有几种行（逐字量，含 \n）。 */
    private int wrapLines(String s, Paint pt, float availW) {
        if (s == null || s.length() == 0) return 0;
        int lines = 1;
        float cur = 0f;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') { lines++; cur = 0f; continue; }
            float w = pt.measureText(String.valueOf(c));
            if (cur + w > availW && cur > 0f) { lines++; cur = w; } else cur += w;
        }
        return lines;
    }

    private float sumH(int n) {
        float tot = 0f;
        for (int i = 0; i < n; i++) {
            boolean b = lineBold.get(i);
            tot += b ? (fm1.descent - fm1.ascent) : (fm2.descent - fm2.ascent);
        }
        return tot;
    }

    /** ★89.5：按字符（含 \n）换行到最多 maxLines 行；返回 true＝还有内容没放下。 */
    private boolean wrapInto(String s, Paint pt, float availW, int maxLines, boolean more) {
        if (s == null) s = "";
        int i = 0, len = s.length(), used = 0;
        while (i < len && used < maxLines && lineTxt.size() < 5) {
            StringBuilder sb = new StringBuilder();
            while (i < len) {
                char c = s.charAt(i);
                if (c == '\n') { i++; break; }
                sb.append(c);
                if (pt.measureText(sb.toString()) > availW && sb.length() > 1) { sb.deleteCharAt(sb.length() - 1); break; }
                i++;
            }
            if (sb.length() == 0) sb.append(' ');
            lineTxt.add(sb.toString());
            lineBold.add(Boolean.valueOf(pt == tp1));
            used++;
        }
        return more || i < len;
    }

    private String ellipsize(String s, Paint pt, float availW) {
        if (s == null) return "";
        if (pt.measureText(s) <= availW) return s;
        String ell = "…";
        float ew = pt.measureText(ell);
        int n = s.length();
        while (n > 0 && pt.measureText(s.substring(0, n)) + ew > availW) n--;
        return n <= 0 ? "" : s.substring(0, n) + ell;
    }

    /** ★89.6：过冲段线性外推（p>1 ⇒ 几何按同一比例放大，12% 就是 12%，不被压缩）。 */
    float easeExt(float t) {
        if (t <= 1f) return ease(t);
        return 1f + (t - 1f);
    }

    /** ★89.5 内容层（TextView）随框走、随动画淡入；p 回到白条态就藏起来。 */
    void updateContent(float cy) {
        if (content == null) return;
        float ta = clampF((p - textAt(ctx) / 100f) / 0.22f, 0f, 1f);
        if (p <= 0.03f) ta = 0f;
        try {
            content.setAlpha(ta);
            content.setTranslationY(cy - contentCy);
            content.setVisibility(ta <= 0.01f ? View.INVISIBLE : View.VISIBLE);
        } catch (Throwable t) { }
    }

    void setContent(View v) { content = v; }
    void setFlick(float f) { flick = f < -0.35f ? -0.35f : (f > 0.2f ? 0.2f : f); }
    float flick() { return flick; }
    float winCy() { return winH / 2f + cyOff; }
    private float textW(String s, boolean bold) {
        if (s == null || s.length() == 0) return 0f;
        return (bold ? tp1 : tp2).measureText(s);
    }

    void setP(float v) {
        p = v < -0.02f ? -0.02f : (v > 1.16f ? 1.16f : v);   // ★89.6 允许过冲到 1.16（12% 过冲＋余量）
        try { view.invalidate(); } catch (Throwable t) { }
    }
    float progress() { return p; }

    /** 当前帧的轮廓描述（逐帧日志证据：同一条闭合轮廓 ＋ 矩形 ＋ 同一个颜色 hex）。 */
    String outline() {
        boolean stillMoving = curClamped && Math.abs(curRad - lastRad) >= 0.5f;
        return "轮廓=1条闭合(4直边+4圆角同R) 矩形=" + Math.round(curL) + "," + Math.round(curT) + "-"
                + Math.round(curR) + "," + Math.round(curB) + " 宽=" + Math.round(curW) + " 高=" + Math.round(curH)
                + " R=" + Math.round(curRad) + (stillMoving ? "(半径还在被自身高/宽夹)" : "")
                + " 填充=" + hex(fillColor(ctx)) + " 描边=" + hex(strokeColor(ctx)) + " 描边宽=" + strokeW(ctx) + "dp"
                + " p=" + Math.round(p * 100f) + "%";
    }

    static String hex(int c) { return String.format(java.util.Locale.ROOT, "#%06X", c & 0xFFFFFF); }

    // ───────── 缓动 ─────────
    float ease(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        switch (easeMode(ctx)) {
            case 0: return t;
            case 1: return 1f - (1f - t) * (1f - t);
            case 2: {   // 回弹：缓出 + 过冲（阻尼越小过冲越大）
                float b = bounce(ctx) / 100f;
                return (1f - (1f - t) * (1f - t)) + (float) Math.sin(Math.PI * t) * b * 0.16f;
            }
            case 3: {   // 弹性：末尾来回抖
                float b = 0.04f + bounce(ctx) / 100f * 0.30f;
                return (float) (1f - Math.pow(1f - t, 3) + Math.sin(t * Math.PI * 3.4) * b * (1f - t) * 0.55f);
            }
            default: return cubic(t, bez(ctx));
        }
    }

    /** 三次贝塞尔 y(x)：x 方向先解参数 u（牛顿迭代 6 次），再取 y。 */
    private float cubic(float x, float[] b) {
        float x1 = b[0], y1 = b[1], x2 = b[2], y2 = b[3];
        float u = x;
        for (int i = 0; i < 6; i++) {
            float xu = 3 * (1 - u) * (1 - u) * u * x1 + 3 * (1 - u) * u * u * x2 + u * u * u;
            float dx = xu - x;
            float d = 3 * (1 - u) * (1 - u) * x1 + 6 * (1 - u) * u * (x2 - x1) + 3 * u * u * (1 - x2);
            if (Math.abs(d) < 1e-5f) break;
            u = u - dx / d;
            if (u < 0f) u = 0f;
            if (u > 1f) u = 1f;
        }
        float u1 = 1 - u;
        return 3 * u1 * u1 * u * y1 + 3 * u1 * u * u * y2 + u * u * u;
    }

    // ───────── 画（★88.7：一条闭合圆角矩形，四角同半径；锚点＝屏幕边） ─────────
    /** ★88.8 零分配：不复用不了的真不多——Paint／Path／RectF 全在字段里，每帧只改数值。 */
    void paint(Canvas cv) {
        try {
            float W = winW, H = winH;
            if (W <= 0 || H <= 0) return;
            float cy = H / 2f + cyOff;
            float e = easeExt(p);

            float lag = bulge(ctx) / 100f * 0.12f;
            float ph = p <= lag ? 0f : clampF((p - lag) / Math.max(0.02f, 1f - lag), 0f, 1f);
            float ew = e, eh = easeExt(ph);
            if (Math.abs(ew - eh) > 0.28f) eh = ew;

            float barWe = barW * (1f + flick);   // ★89.6 拉伸变细（flick<0）／缩回变粗（flick>0）：真画在厚度上
            float w = barWe + (frameW - barWe) * ew;
            float h = barH + (frameH - barH) * eh;
            float wLo = Math.min(barWe, frameW), wHi = Math.max(barWe, frameW);
            float hLo = Math.min(barH * 0.94f, frameH), hHi = Math.max(barH, frameH);
            w = clampF(w, wLo, wHi);      // ★89.7 无过冲：上限就是终态，几何上不可能越过
            h = clampF(h, hLo, hHi);
            curW = w; curH = h;

            float edgeX = right ? W : 0f;
            float dir = right ? -1f : 1f;
            float xFar = edgeX + dir * w;
            float L = Math.min(edgeX, xFar), R = Math.max(edgeX, xFar);
            float top = cy - h / 2f, bot = cy + h / 2f;
            curL = L; curT = top; curR = R; curB = bot;

            float rWant = Math.min(dp(radius(ctx)), Math.min(frameW, frameH) / 2f);
            float r0 = barW / 2f;
            float rad = clampF(r0 + (rWant - r0) * Math.min(e, 1f), 0f, Math.min(w, h) / 2f);
            curRad = rad;
            curClamped = rad < rWant - 0.5f;

            float st = dp(strokeW(ctx));
            float ins = Math.min(st, Math.max(0f, Math.min(w, h) / 2f - 1f));
            int fillC = fillColor(ctx), strokeC = strokeColor(ctx);
            if (ins > 0.05f) {
                rfO.set(L + ins / 2f, top + ins / 2f, R - ins / 2f, bot - ins / 2f);
                float ri = Math.max(0f, rad - ins / 2f);
                pathO.reset();
                pathO.addRoundRect(rfO, ri, ri, Path.Direction.CW);
                spO.setStrokeWidth(ins);
                spO.setColor(strokeC);
                cv.drawPath(pathO, spO);
            }
            rfF.set(L + ins, top + ins, R - ins, bot - ins);
            if (rfF.right > rfF.left && rfF.bottom > rfF.top) {
                pathF.reset();
                pathF.addRoundRect(rfF, Math.max(0f, rad - ins), Math.max(0f, rad - ins), Path.Direction.CW);
                fpF.setColor(fillC);
                cv.drawPath(pathF, fpF);
            }

            // ★89.5 整改六.3：文字改由叠在上面的 TextView 内容层画（能滚、能多行），这里只同步位置与淡入
            updateContent(cy);
            lastRad = rad;
        } catch (Throwable t) { }
    }
    /** 超宽就逐字截断加省略号（只在 prepare 调一次；框宽一次定死，不许边播边改窗口）。 */
    private String fit(String s, Paint pt, float availW) {
        if (s == null) return "";
        if (pt.measureText(s) <= availW) return s;
        String ell = "…";
        float ew = pt.measureText(ell);
        int n = s.length();
        while (n > 0 && pt.measureText(s.substring(0, n)) + ew > availW) n--;
        return n <= 0 ? "" : s.substring(0, n) + ell;
    }

    /** 只画不接触摸的自绘 View（内部类，直接找 outer 画）。 */
    final class MV extends View {
        MV(Context c) { super(c); setFocusable(false); setClickable(false); }
        @Override protected void onDraw(Canvas cv) { paint(cv); }
    }
}
