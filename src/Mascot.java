package app.reticle;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 66 号单 · 形象治本与手感（65 的返工）。
 *
 * ★治本（A 线）＝把"抽搐"的根因拆掉：
 *   · 65 的窗口＝「人＋气泡」的包围盒 ⇒ 气泡每次出现/更新/收场都整块重排＋挪位（实测几分钟内
 *     win=1080x224 → 224x224 → 704x224 → 659x224），这就是用户看到的"抽搐"。
 *   · 66 拆成**两个独立窗口**：人窗（figWin）只有形象本身，气泡窗（bubWin）只有气泡。
 *     人窗的尺寸在状态切换之外**永不改变**，位置只在拖动／换边／转屏时变 ⇒ 气泡怎么冒、怎么改、
 *     怎么收，**都碰不到人**（日志里 `win=` 恒定，变的是 `bub=`）。
 *   · 热区＝可见元素范围：两个窗口都紧贴自己的内容，不留隐形大矩形挡点击。
 *   · **气泡不参与碰撞**（用户原话）：吸附、限位、左右判定一律只认**形象自己的矩形**，气泡没有碰撞箱；
 *     气泡伸到屏幕外时**按人所在侧翻到另一侧**，绝不为了塞气泡去推人（修现场拖动 bug）。
 * ★消息（B 线）：失败 60s ＞ 汇报 30s ＞ 工具摘要 8s；低优先级**不许**顶掉未到点的高优先级（改写角标）；
 *   消息到点只收气泡、**不再把状态拉回 ACTIVE**；工具摘要也不重置三态计时（用户拍）。
 * ★三态（C 线）：ACTIVE 全彩 → 10 秒 DIMMED（**只变淡、不去饱和**，α≈0.65×）→ 再 10 秒 EDGE（贴边细条）。
 * ★形状（C 线）：气泡方向＝「斜 45°／正上方」；气泡形状＝「横向优先（默认）／纵向优先」。
 * ★让路（A 线）：截图让路先试 alpha=0（不摘视图，免掉"摘了又放"的闪）；触摸让路仍摘视图，但 2 秒内的
 *   连续取图／触摸**合并成一次**（去抖）。
 */
public class Mascot {

    static final String PREFS = "settings";
    static final String K_ON = "mascot_on";
    static final String K_ALPHA = "mascot_alpha";     // 形象不透明度，默认 0.85
    static final String K_POS = "mascot_pos";         // 气泡方向：0 斜 45°（默认）／1 正上方
    static final String K_VERT = "mascot_bubble_vert"; // 气泡形状：false 横向优先（默认）／true 纵向优先
    static final String K_MIRROR = "mascot_mirror";   // 自动镜像（默认开）
    static final String K_SIDE = "mascot_side";       // 0 未定／1 贴左／2 贴右
    static final String K_FRAC = "mascot_frac";       // 纵向位置 0~1（默认 0.34）
    static final String K_HIDE_MODE = "mascot_hide_mode";   // 在 AI 客户端里的表现：0=只留细条（默认）／1=全隐／2=只藏气泡
    static final String K_HIDE_PKGS = "mascot_hide_pkgs";   // 隐藏名单（逗号分隔包名；默认 RikkaHub）
    static final String DEF_HIDE_PKGS = "me.rerere.rikkahub";
    static final String[] HIDE_MODE_NAMES = {"只留细条", "全隐", "只藏气泡"};
    static final long EXEMPT_MS = 300000L;                  // 门铃：点一下细条＝本次前台豁免 5 分钟
    static final long RELEASE_DEBOUNCE_MS = 1500L;           // ★70 B 线：解除让位的去抖——确认「非名单」后再等这么久
    static final long FG_KEEP_MS = 10000L;                   // ★70 B 线：前台包名读不到时，沿用上次判定的上限
    static final long VERIFY_MS = 220L;                      // ★70 C 线：三态切换后自校窗口尺寸的延迟
    static final String DIRNAME = "外观";
    static final String IMG = "形象.png";

    // 三态
    static final int ACTIVE = 0, DIMMED = 1, EDGE = 2;
    static final String[] STATE_NAME = {"ACTIVE", "DIMMED", "EDGE"};
    static final long T_DIM = 10000L, T_EDGE = 10000L;

    // 消息时限（只决定字留多久）
    static final int M_NORMAL = 0, M_REPORT = 1, M_FAIL = 2;
    static final long T_TALK = 8000L, T_REPORT = 30000L, T_FAIL = 60000L;
    static final long T_BADGE = 5000L;      // 角标（工具图标＋名）自己隐掉的时间

    static final long YIELD_KEEP = 2000L;   // 让路去抖：2 秒内的连续取图／触摸合并成一次
    // ★67 A 线：贴边态被消息唤醒 = 先展开（动画）再冒泡
    static final long EXPAND_MS = 180L;    // 展开时长（派单要求 150~200ms 量级）
    static final long COLLAPSE_MS = 160L;  // 收起进 EDGE 也带动画：不许瞬间跳变
    static final boolean SHOT_BY_ALPHA = false;  // 66 返工：alpha=0 实测 4 次里 1 次仍被截到 ⇒ 回退摘视图（65 已验证可靠）
    static final boolean TOUCH_BY_ALPHA = false; // 触摸让路：true＝只设 NOT_TOUCHABLE（不摘）；false＝摘视图（65 已验证稳）

    // 尺寸（dp）
    static final int FIG_DP = 56;                              // 人：56dp×56dp
    static final int EDGE_W_DP = 20, EDGE_H_DP = 100;          // EDGE 热区：20dp×100dp
    static final float EDGE_BAR_W_DP = 7.5f, EDGE_BAR_H_DP = 80f;  // EDGE 可见细线：≈7.5dp×80dp（贴边）

    private static Mascot S;

    private final AccessibilityService svc;
    private WindowManager wm;
    private final Handler ui = new Handler(Looper.getMainLooper());

    // ★两个独立窗口：人窗＝只有形象；气泡窗＝只有气泡（互不影响）
    private WindowManager.LayoutParams figLp, bubLp;
    private FrameLayout figWin;
    private ImageView img;
    private Face face;
    private View edgeBar;
    private FrameLayout bubWin;
    private ScrollView bubbleWrap;
    private TextView bubble, badge;

    private boolean figAdded = false, bubAdded = false;
    private int sw = 1080, sh = 1920;
    private int state = ACTIVE;
    private int figW = 0, figH = 0;            // 人窗当前尺寸（运行期恒定，只有状态切换/换边/转屏才变）
    private long lastSayAt = 0L;
    private int msgKind = -1;                  // 当前气泡里的消息种类；-1＝没有
    private String pendingTool = null;         // 高优先级在屏期间攒下的最新工具摘要
    private boolean badgeOn = false;
    private boolean dragging = false, moved = false;
    private float downRawX, downRawY;
    private int dragFigX0, dragFigY0, dragBubX0, dragBubY0;
    private long lastDragNote = 0L;
    private boolean yielded = false;           // 触摸让路中
    private long yieldAt = 0L;
    private Runnable restoreYield = null;
    private boolean shotHidden = false;        // 截图让路中（alpha=0）
    private boolean trustedLogged = false;
    private boolean scrollLogged = false;
    // ★67 A 线：EDGE ↔ ACTIVE 的尺寸变化走动画；动画期间气窗一律不动
    private boolean expanding = false;      // 展开动画进行中（期间来的消息只排队，不重复展开）
    private long lastExpandAt = 0L;         // 上次展开动画的时间（2 秒内再被唤醒＝直接冒泡）
    private String queuedSay = null;        // 展开动画期间攒下的消息（只留最新一条）
    private int queuedKind = -1;
    private boolean queuedForce = false;
    private ValueAnimator boxAnim = null;   // 人窗尺寸动画的唯一句柄（新动画前先取消旧的）
    private long animSeq = 0L;              // 过期动画回调作废用
    // ★69.5：在 AI 客户端里让位（只留细条／全隐／只藏气泡）——门铃＝点细条
    private boolean cliHide = false;      // 让位生效中
    private String cliPkg = null;         // 让位时的前台包名（切走即解除）
    private String lastFgPkg = null;      // ★70 B 线：最近一次明确读到的前台包名
    private long lastFgAt = 0L;
    private long lastNamedAt = 0L;        // ★70 B 线：最近一次确认「命中名单」的时刻（去抖用）
    private long exemptUntil = 0L;        // 门铃豁免到期（0＝无）
    private String exemptPkg = null;      // 豁免对应的包名
    private boolean failBreak = false;    // 失败破例冒头中

    private final Runnable goDim = new Runnable() { public void run() { stepDown(); } };
    private final Runnable goEdge = new Runnable() { public void run() { stepDown(); } };
    private final Runnable hideMsg = new Runnable() { public void run() { onMsgTimeout(); } };
    private final Runnable hideBadgeTask = new Runnable() { public void run() { setBadge(null); } };

    private Mascot(AccessibilityService s) { this.svc = s; }

    // ───────────────────────── 偏好（配置页与运行期共用） ─────────────────────────

    public static boolean isOn(Context c) {
        try { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(K_ON, true); }
        catch (Throwable t) { return true; }
    }

    public static void setOn(Context c, boolean v) {
        try { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(K_ON, v).apply(); } catch (Throwable t) { }
        final Mascot m = S;
        if (m != null) m.ui.post(new Runnable() { public void run() {
            if (isOn(m.svc)) m.ensure(); else m.detachAll();
        } });
    }

    public static float alpha(Context c) {
        try { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat(K_ALPHA, 0.85f); }
        catch (Throwable t) { return 0.85f; }
    }

    public static void setAlpha(Context c, float v) {
        if (v < 0.2f) v = 0.2f;
        if (v > 1f) v = 1f;
        try { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(K_ALPHA, v).apply(); } catch (Throwable t) { }
        final Mascot m = S;
        if (m != null) m.ui.post(new Runnable() { public void run() { m.applyAlpha(); } });
    }

    /** 气泡方向：0 斜 45°（默认）／1 正上方。★66：旧值（2/3/4）一律按 0 读，用户拍的"只留两项"。 */
    public static int posMode(Context c) {
        try {
            int v = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(K_POS, 0);
            return (v == 1) ? 1 : 0;
        } catch (Throwable t) { return 0; }
    }

    public static void setPosMode(Context c, int v) {
        try { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(K_POS, (v == 1) ? 1 : 0).apply(); } catch (Throwable t) { }
        final Mascot m = S;
        if (m != null) m.ui.post(new Runnable() { public void run() { m.layOutChildren("改气泡方向"); } });
    }

    public static boolean mirror(Context c) {
        try { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(K_MIRROR, true); }
        catch (Throwable t) { return true; }
    }

    public static void setMirror(Context c, boolean v) {
        try { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(K_MIRROR, v).apply(); } catch (Throwable t) { }
        final Mascot m = S;
        if (m != null) m.ui.post(new Runnable() { public void run() { m.applyMirror(); } });
    }

    /** ★66：气泡形状（不是"气泡在上还是在下"）——true＝纵向优先（气泡竖长、只占屏幕侧边）。 */
    public static boolean vertPref(Context c) {
        try { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(K_VERT, false); }
        catch (Throwable t) { return false; }
    }

    public static void setVert(Context c, boolean v) {
        try { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(K_VERT, v).apply(); } catch (Throwable t) { }
        final Mascot m = S;
        if (m != null) m.ui.post(new Runnable() { public void run() { m.layOutChildren("改气泡形状"); } });
    }

    public static int hideMode(Context c) {
        try { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(K_HIDE_MODE, 0); } catch (Throwable t) { return 0; }
    }

    public static void setHideMode(Context c, int v) {
        try { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(K_HIDE_MODE, (v < 0 || v > 2) ? 0 : v).apply(); } catch (Throwable t) { }
    }

    public static String hidePkgs(Context c) {
        try { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_HIDE_PKGS, DEF_HIDE_PKGS); } catch (Throwable t) { return DEF_HIDE_PKGS; }
    }

    public static void setHidePkgs(Context c, String v) {
        try { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(K_HIDE_PKGS, v == null ? DEF_HIDE_PKGS : v.trim()).apply(); } catch (Throwable t) { }
    }

    static String hideModeName(int v) { return (v >= 0 && v < HIDE_MODE_NAMES.length) ? HIDE_MODE_NAMES[v] : HIDE_MODE_NAMES[0]; }

    public static String imagePath(Context c) {
        return new File(Home.sub(c, DIRNAME), IMG).getAbsolutePath();
    }

    public static boolean hasOwnImage(Context c) {
        try { return new File(imagePath(c)).isFile(); } catch (Throwable t) { return false; }
    }

    private int sidePref() {
        try { return svc.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(K_SIDE, 0); } catch (Throwable t) { return 0; }
    }

    private float fracPref() {
        try { return svc.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat(K_FRAC, 0.34f); } catch (Throwable t) { return 0.34f; }
    }

    private void savePlace(int side, float frac) {
        try {
            svc.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(K_SIDE, side).putFloat(K_FRAC, frac).apply();
        } catch (Throwable t) { }
    }

    // ───────────────────────── 生命周期 ─────────────────────────

    static synchronized void attach(AccessibilityService s) {
        try { if (S != null) S.destroy(); } catch (Throwable t) { }
        S = new Mascot(s);
        S.setup();
    }

    static synchronized void detach() {
        Mascot m = S;
        S = null;
        if (m != null) m.destroy();
    }

    /** 转屏：按新屏幕尺寸重摆（人仍贴边、气泡朝内、不出屏）。 */
    static synchronized void onRotate() {
        final Mascot m = S;
        if (m == null) return;
        m.ui.post(new Runnable() {
            public void run() {
                DisplayMetrics dm = m.svc.getResources().getDisplayMetrics();
                if (dm != null) { m.sw = dm.widthPixels; m.sh = dm.heightPixels; }
                int fy = (int) (m.sh * m.fracPref());
                m.figLp.y = fy;
                m.layOutChildren("转屏");
            }
        });
    }

    private void setup() {
        try {
            DisplayMetrics dm = svc.getResources().getDisplayMetrics();
            if (dm != null) { sw = dm.widthPixels; sh = dm.heightPixels; }
            wm = (WindowManager) svc.getSystemService(Context.WINDOW_SERVICE);

            // ── 人窗：只有形象本身（气泡与它无关） ──
            figWin = new FrameLayout(svc);
            face = new Face(svc);
            figWin.addView(face, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            img = new ImageView(svc);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            figWin.addView(img, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            edgeBar = new View(svc);
            GradientDrawable eg = new GradientDrawable();
            eg.setColor(0x99FFFFFF);
            eg.setCornerRadius(dp(2));
            eg.setStroke(dp(1), 0x40000000);
            edgeBar.setBackground(eg);
            edgeBar.setVisibility(View.GONE);
            figWin.addView(edgeBar, edgeBarParams());
            figWin.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent e) { return onFigTouch(e); }
            });
            // 三版④：先给 LayoutParams 再调尺寸（65 的 D3 空指针就出在这里）
            figWin.setLayoutParams(new FrameLayout.LayoutParams(dp(FIG_DP), dp(FIG_DP)));

            figLp = baseLp();
            figW = dp(FIG_DP);
            figH = dp(FIG_DP);
            figLp.width = figW;
            figLp.height = figH;

            // ── 气泡窗：只有气泡（紧贴内容，有它才存在） ──
            bubWin = new FrameLayout(svc);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFFFFFFFF);
            bg.setCornerRadius(dp(10));
            bg.setStroke(dp(1), 0x33000000);
            bubWin.setBackground(bg);
            bubWin.setPadding(dp(10), dp(7), dp(10), dp(7));
            bubble = new TextView(svc);
            bubble.setTextColor(0xFF1B1B1B);
            bubble.setTextSize(11.5f);
            bubbleWrap = new ScrollView(svc);
            bubbleWrap.setVerticalScrollBarEnabled(true);
            bubbleWrap.addView(bubble, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            badge = new TextView(svc);
            badge.setTextColor(0xFFFFFFFF);
            badge.setTextSize(10f);
            badge.setSingleLine(true);
            badge.setEllipsize(android.text.TextUtils.TruncateAt.END);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(dp(6), dp(2), dp(6), dp(2));
            GradientDrawable bgb = new GradientDrawable();
            bgb.setColor(0xCC3A3A3A);
            bgb.setCornerRadius(dp(7));
            badge.setBackground(bgb);
            badge.setVisibility(View.GONE);
            bubWin.addView(bubbleWrap);
            bubWin.addView(badge);
            bubbleWrap.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent e) {
                    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        note("bubble touch"); if (state != ACTIVE) setState(ACTIVE, "碰了气泡"); scheduleIdle();
                    }
                    return false;   // 不吃事件：气泡里照样滚文字
                }
            });
            bubWin.setLayoutParams(new FrameLayout.LayoutParams(dp(120), dp(40)));
            bubLp = baseLp();

            // 初始位置（之后由 layOutChildren 维护；★运行期只有拖动/换边/转屏/换状态才改）
            int fy = (int) (sh * fracPref());
            if (fy < 0) fy = 0;
            if (fy + figH > sh) fy = Math.max(0, sh - figH);
            figLp.y = fy;
            figLp.x = isRightSide() ? Math.max(0, sw - figW) : 0;

            loadImage();
            applyMirror();
            layOutChildren("setup");
            try { applyTrusted(); } catch (Throwable t) { noteFail("trusted", t); }
            applyAlpha();
            ensure();
            scheduleIdle();
        } catch (Throwable t) {
            noteFail("setup", t);
        }
    }

    private WindowManager.LayoutParams baseLp() {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams();
        p.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        p.format = PixelFormat.TRANSLUCENT;
        p.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        p.gravity = Gravity.TOP | Gravity.START;
        p.width = WindowManager.LayoutParams.WRAP_CONTENT;
        p.height = WindowManager.LayoutParams.WRAP_CONTENT;
        return p;
    }

    /** Android 12+ 的 setTrustedOverlay 是隐藏 API ⇒ 反射试，能／不能都留原始证据。 */
    private void applyTrusted() {
        String r;
        try {
            java.lang.reflect.Method mm = WindowManager.LayoutParams.class.getMethod("setTrustedOverlay", boolean.class);
            mm.invoke(figLp, Boolean.TRUE);
            mm.invoke(bubLp, Boolean.TRUE);
            r = "trustedOverlay=ok(api" + Build.VERSION.SDK_INT + ")";
        } catch (Throwable t) {
            r = "trustedOverlay=NO(" + t.getClass().getSimpleName() + "：" + t.getMessage() + "／api" + Build.VERSION.SDK_INT + ")";
        }
        if (!trustedLogged) { trustedLogged = true; note(r); }
    }

    private void destroy() {
        try { ui.removeCallbacksAndMessages(null); } catch (Throwable t) { }
        detachAll();
        wm = null;
    }

    private void ensure() {
        if (figWin == null || wm == null) return;
        if (!isOn(svc)) return;
        if (!figAdded) {
            try { wm.addView(figWin, figLp); figAdded = true; note("addView ok（人窗 " + figW + "x" + figH + " at " + figLp.x + "," + figLp.y + "）"); }
            catch (Throwable t) { noteFail("addView", t); }   // 机型不许自绘：如实记一笔，静默降级
        }
        if (bubShown()) showBubWin();
    }

    private void detachAll() {
        hideBubWin();
        if (!figAdded || figWin == null || wm == null) { figAdded = false; return; }
        try { wm.removeView(figWin); } catch (Throwable t) { }
        figAdded = false;
    }

    private void showBubWin() {
        if (bubAdded || bubWin == null || wm == null || !isOn(svc)) return;
        try { wm.addView(bubWin, bubLp); bubAdded = true; note("addView ok（气泡窗 " + bubLp.width + "x" + bubLp.height + " at " + bubLp.x + "," + bubLp.y + "）"); }
        catch (Throwable t) { noteFail("addBub", t); }
    }

    private void hideBubWin() {
        if (!bubAdded || bubWin == null || wm == null) { bubAdded = false; return; }
        try { wm.removeView(bubWin); } catch (Throwable t) { }
        bubAdded = false;
    }

    private int dp(int v) {
        try { return (int) (v * svc.getResources().getDisplayMetrics().density + 0.5f); } catch (Throwable t) { return v * 3; }
    }

    private int dpf(float v) {
        try { return (int) (v * svc.getResources().getDisplayMetrics().density + 0.5f); } catch (Throwable t) { return (int) (v * 3); }
    }

    private static int clampI(int v, int lo, int hi) { if (hi < lo) hi = lo; return v < lo ? lo : (v > hi ? hi : v); }

    // ───────────────────────── 三态 ─────────────────────────

    private void scheduleIdle() {
        ui.removeCallbacks(goDim);
        ui.removeCallbacks(goEdge);
        if (state == ACTIVE) ui.postDelayed(goDim, T_DIM);
    }

    private void stepDown() {
        if (state == ACTIVE) { setState(DIMMED, "10 秒无指令无点击"); ui.postDelayed(goEdge, T_EDGE); return; }
        if (state == DIMMED) {
            // 长时限消息（汇报 30 秒／失败 60 秒）还挂着：EDGE 等它收场，但每 3 秒只让一步（绝不永久等）
            if (bubShown() && (msgKind == M_REPORT || msgKind == M_FAIL)) { ui.postDelayed(goEdge, 3000L); return; }
            setState(EDGE, "再 10 秒无动静");
        }
    }

    private void setState(int st, String why) {
        if (state == st) { ui.postDelayed(verifyBoxR, VERIFY_MS); return; }   // ★70 C 线：同态也校一次（竞态正是「状态没变、尺寸错了」）
        ui.postDelayed(verifyBoxR, VERIFY_MS);   // ★70 C 线：三态切换后自校一次窗口尺寸
        int old = state;
        state = st;
        note("state " + STATE_NAME[old] + " -> " + STATE_NAME[st] + "（" + why + "）");
        if (st == ACTIVE) {
            if (edgeBar != null) edgeBar.setVisibility(View.GONE);
            setEdgeBarSide();
            showFig();
            if (old == EDGE && expanding) {
                // ★67 A 线：贴边态 → 前台态 ＝ "长回来"，必须带动画；动画期间气泡窗一律不动
                layOutChildren("开始展开");
                note("expand start（EDGE -> ACTIVE，" + EXPAND_MS + "ms 动画）");
                animateFigBox(dp(FIG_DP), dp(FIG_DP), EXPAND_MS, new Runnable() { public void run() {
                    expanding = false;
                    note("expand end（win=" + figW + "x" + figH + "，接着冒泡）");
                    String t = queuedSay;
                    int k = queuedKind;
                    boolean f = queuedForce;
                    queuedSay = null;
                    queuedKind = -1;
                    queuedForce = false;
                    scheduleIdle();
                    if (t != null) say(t, k, f);
                }});
                applyAlpha();
                return;
            }
            setFigBox(dp(FIG_DP), dp(FIG_DP));   // 尺寸变（状态切换）——中心不变，人不会跳
            layOutChildren("回前台态");
        } else if (st == DIMMED) {
            // ★66：只变淡、不去饱和（"变灰"用户不认）——只在 applyAlpha 里调透明度
            note("DIMMED 只调透明度（不去饱和）");
        } else {
            enterEdge();
        }
        applyAlpha();
        if (st == ACTIVE) scheduleIdle();
    }

    /** EDGE：收成**真正贴边的一条细线**（可见 ≈7.5dp×80dp，热区 20dp×100dp 跟着缩）。
     *  ★67：收起也走动画（不许瞬间跳变）；贴边态一律不留气泡（"线＋气泡"这种半吊子状态不许存在）。 */
    private void enterEdge() {
        if (figWin == null) return;
        if (expanding) {   // 展开动画被贴边打断：丢弃排队消息，别让回调在半路再冒泡
            expanding = false;
            queuedSay = null;
            queuedKind = -1;
            note("展开动画被贴边打断，丢弃排队消息");
        }
        if (bubShown()) hideBubble("贴边");
        if (edgeBar != null) edgeBar.setVisibility(View.VISIBLE);
        setEdgeBarSide();
        hideFig();
        note("collapse start（进 EDGE，" + COLLAPSE_MS + "ms 动画）");
        animateFigBox(dp(EDGE_W_DP), dp(EDGE_H_DP), COLLAPSE_MS, new Runnable() { public void run() {
            note("collapse end（win=" + figW + "x" + figH + "）");
            layOutChildren("贴边完成");
        }});
        layOutChildren("贴边");
    }

    /** 尺寸变化的唯一入口：保持"人"的中心不动（65 的抽搐有一半是这里跳出来的）。 */
    /** ★70 C 线兜底：三态切换后校一次「窗口尺寸与 state 是否相符」，不符就纠正并记一行（抓竞态用）。 */
    private final Runnable verifyBoxR = new Runnable() { public void run() { verifyBox(); } };

    private void verifyBox() {
        try {
            if (boxAnim != null) { ui.postDelayed(verifyBoxR, VERIFY_MS); return; }   // 动画还在飞：等它飞完再看
            if (cliHide && hideMode(svc) == 1) return;                              // 全隐档：窗口已摘，不校
            int ew = (state == EDGE) ? dp(EDGE_W_DP) : dp(FIG_DP);
            int eh = (state == EDGE) ? dp(EDGE_H_DP) : dp(FIG_DP);
            if (figW != ew || figH != eh) {
                note("自校：state=" + STATE_NAME[state] + " win=" + figW + "x" + figH + " → 纠正为 " + ew + "x" + eh);
                setFigBox(ew, eh);
                placeFig();
            }
        } catch (Throwable t) { noteFail("verifyBox", t); }
    }

    private void setFigBox(int w, int h) {
        try {
            // ★70 C 线：在飞的尺寸动画先作废（否则它的回调会把窗口回写成旧尺寸 ⇒「state=ACTIVE 但窗口是细条」）
            animSeq++;
            if (boxAnim != null) { try { boxAnim.cancel(); } catch (Throwable t) { } boxAnim = null; }
            int cy = figH > 0 ? figLp.y + figH / 2 : (int) (sh * fracPref());
            figW = w;
            figH = h;
            figLp.width = w;
            figLp.height = h;
            figLp.y = clampI(cy - h / 2, 0, Math.max(0, sh - h));
        } catch (Throwable t) { noteFail("setFigBox", t); }
    }

    /**
     * ★67 A 线：人窗尺寸的**唯一动画通道**——只有状态切换（EDGE ↔ ACTIVE）走它。
     * 气泡的出现/更新/收场一律不碰人窗（66 的治本口径）。贴边同一边、纵向中心不变 ⇒ "长回来"而不是跳。
     */
    private void animateFigBox(final int tw, final int th, long dur, final Runnable end) {
        final int w0 = figW, h0 = figH;
        final int cy = figH > 0 ? figLp.y + figH / 2 : (int) (sh * fracPref());
        final boolean right = isRightSide();
        final long seq = ++animSeq;
        if (boxAnim != null) { try { boxAnim.cancel(); } catch (Throwable t) { } boxAnim = null; }
        try {
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(dur);
            va.setInterpolator(new DecelerateInterpolator());
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) {
                    if (seq != animSeq) return;
                    float f = (Float) a.getAnimatedValue();
                    int w = Math.round(w0 + (tw - w0) * f);
                    int h = Math.round(h0 + (th - h0) * f);
                    figW = w;
                    figH = h;
                    figLp.width = w;
                    figLp.height = h;
                    figLp.y = clampI(cy - h / 2, 0, Math.max(0, sh - h));
                    figLp.x = right ? Math.max(0, sw - w) : 0;
                    placeFig();
                }
            });
            va.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    if (seq != animSeq) return;
                    boxAnim = null;
                    figW = tw;
                    figH = th;
                    figLp.width = tw;
                    figLp.height = th;
                    figLp.y = clampI(cy - th / 2, 0, Math.max(0, sh - th));
                    figLp.x = right ? Math.max(0, sw - tw) : 0;
                    placeFig();
                    if (end != null) end.run();
                }
            });
            boxAnim = va;
            va.start();
        } catch (Throwable t) {
            animSeq++;
            boxAnim = null;
            noteFail("animateFigBox", t);
            setFigBox(tw, th);
            figLp.x = right ? Math.max(0, sw - tw) : 0;
            placeFig();
            if (end != null) end.run();
        }
    }

    private void setEdgeBarSide() {
        try {
            if (edgeBar == null) return;
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dpf(EDGE_BAR_W_DP), dpf(EDGE_BAR_H_DP));
            p.gravity = (isRightSide() ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL;
            edgeBar.setLayoutParams(p);
        } catch (Throwable t) { }
    }

    private FrameLayout.LayoutParams edgeBarParams() {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dpf(EDGE_BAR_W_DP), dpf(EDGE_BAR_H_DP));
        p.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        return p;
    }

    /** 65：图形（自带脸／自选图）显示与收起——EDGE 态只留细线。 */
    private void showFig() {
        try {
            if (edgeBar != null) edgeBar.setVisibility(View.GONE);
            boolean hasImg = img != null && img.getDrawable() != null;
            if (img != null) img.setVisibility(hasImg ? View.VISIBLE : View.GONE);
            if (face != null) face.setVisibility(hasImg ? View.GONE : View.VISIBLE);
        } catch (Throwable t) { }
    }

    private void hideFig() {
        try {
            if (img != null) img.setVisibility(View.GONE);
            if (face != null) face.setVisibility(View.GONE);
        } catch (Throwable t) { }
    }

    // ───────────────────────── 说话（消息 · 优先级 · 角标） ─────────────────────────

    /** 74 号单（2026-10-16）：给构建环境等外部流程一个冒泡入口（内部走 say；形象不在＝什么都不做）。 */
    public static void bubble(final String text) {
        final Mascot m = S;
        if (m == null || text == null || text.length() == 0) return;
        m.ui.post(new Runnable() { public void run() { try { m.say(text, M_NORMAL, false); } catch (Throwable t) { } } });
    }

    static void watch(Context c, String req, String resp) {
        final Mascot m = S;
        if (m == null || req == null) return;
        try {
            JSONObject o = new JSONObject(req);
            if (!"tools/call".equals(o.optString("method", ""))) return;
            JSONObject p = o.optJSONObject("params");
            if (p == null) return;
            final String name = p.optString("name", "");
            if (name.length() == 0) return;
            JSONObject a = p.optJSONObject("arguments");
            if (a == null) a = new JSONObject();
            final String rep = a.optString("report", "");
            final String talk = ToolTags.line(name, a);
            final boolean bad = looksBad(resp);
            final String badTail = bad ? tailText(resp) : "";
            m.ui.post(new Runnable() {
                public void run() {
                    if (bad) { m.say("⚠ " + ToolTags.label(name) + " 没成：" + badTail, M_FAIL, false); return; }   // ★70 A 线：失败优先于汇报，不许被 report 盖掉
                    if (rep != null && rep.length() > 0) { m.say(rep, M_REPORT, false); return; }
                    m.say(talk, M_NORMAL, false);
                }
            });
        } catch (Throwable t) { }
    }

    /**
     * 说话。kind 决定「字留多久」＋优先级：
     *   M_FAIL(60s) ＞ M_REPORT(30s) ＞ M_NORMAL(8s)；★低优先级不许顶掉未到点的高优先级（改走角标）。
     *   ★工具摘要（M_NORMAL）既不回 ACTIVE、也不重置三态计时（用户拍：气泡照显示 8 秒，形象该淡就淡）。
     */
    private void say(String text, int kind, boolean force) {
        if (text == null || text.length() == 0 || bubWin == null) return;
        long now = System.currentTimeMillis();
        if (kind == M_NORMAL && now - lastSayAt < 120) { if (face != null) face.setMood(Face.TALK); return; }
        // ★69.5：客户端让位期间，报告与工具摘要一律不冒泡（只记日志）；唯一穿孔＝失败（M_FAIL）
        boolean vis = visible(force);
        if (!vis) {
            if (kind == M_FAIL && canFailBreakout()) {
                failBreak = true;
                note("失败破例冒头（客户端让位中）");
                ensure();
                if (!figAdded) { failBreak = false; return; }
            } else {
                note("让位中不冒泡（kind=" + kind + "）：" + clip(text, 24));
                return;
            }
        }
        ensure();
        if (!figAdded) return;

        // ★B 线：高优先级（汇报／失败）还在屏上、又没到点 ⇒ 工具摘要只更新角标
        if (kind == M_NORMAL && bubShown() && (msgKind == M_REPORT || msgKind == M_FAIL)) {
            pendingTool = text;
            setBadge(compactBadge(text));
            note("badge show（" + clip(text, 24) + "）——高优先级未到点，不顶掉");
            return;
        }

        // ★67 遗留一行修（69.5 D 线）：动画进行中来的任何消息都先排队 —— 这一判断必须在 state == EDGE 之外
        if (expanding) {
            queuedSay = text;
            queuedKind = kind;
            queuedForce = force;
            note("expand 中来的消息先排队（只留最新）：" + clip(text, 24));
            return;
        }

        // ★67 A 线：任何消息落在 EDGE 态 ⇒ 先播"展开"动画，展开完再冒泡（顺序不许反）
        if (state == EDGE) {
            if (now - lastExpandAt < 2000L) {   // 去抖：2 秒内刚展开过 ⇒ 直接回前台态＋冒泡
                note("edge wake（2 秒内已展开过：直接冒泡，不再展开）");
                setState(ACTIVE, "贴边态遇消息（刚展开过）");
            } else {
                queuedSay = text;
                queuedKind = kind;
                queuedForce = force;
                lastExpandAt = now;
                expanding = true;
                note("edge wake（贴边态遇消息：先展开，" + EXPAND_MS + "ms 动画）");
                setState(ACTIVE, "贴边态遇消息（先展开）");
                return;   // 展开动画结束时再冒泡
            }
        }
        lastSayAt = now;
        if (kind != M_NORMAL) {   // 汇报／失败＝一次真活动：回全彩、重设计时
            if (state != ACTIVE) setState(ACTIVE, "有汇报／失败");
            ui.removeCallbacks(goDim);
            ui.removeCallbacks(goEdge);
            ui.postDelayed(goDim, T_DIM);
        }
        msgKind = kind;
        long ttl = kind == M_REPORT ? T_REPORT : (kind == M_FAIL ? T_FAIL : T_TALK);
        String show = ToolTags.clean(text);   // 令牌／口令一律不上屏；超上限交给 ScrollView 滚
        if (bubble != null) bubble.setText(show.length() > 1500 ? show.substring(0, 1500) + "…" : show);
        if (face != null) face.setMood(kind == M_FAIL ? Face.FAIL : Face.TALK);
        boolean needAdd = !bubAdded;
        layOutChildren("冒泡");   // ★先把尺寸／位置算好再 addView（否则窗口先按上一轮尺寸出现一帧：实测出过 600x1216 → 483x136）
        if (needAdd) showBubWin();
        note("bubble show kind=" + kind + " ttl=" + ttl + " text=" + clip(text, 40));
        ui.removeCallbacks(hideMsg);
        ui.postDelayed(hideMsg, ttl);
    }

    /** 消息到点：只收气泡。★不再 setState(ACTIVE)（"消息到点"不算活动——抽搐的另一半根因）。 */
    private void onMsgTimeout() {
        hideBubble("到点");
        if (pendingTool != null) {   // 排队里最新的那条工具摘要，这时才顶上
            String t = pendingTool;
            pendingTool = null;
            say(t, M_NORMAL, false);
        }
    }

    private boolean bubShown() {
        return msgKind >= 0;
    }

    private void hideBubble(String why) {
        boolean was = msgKind >= 0;
        msgKind = -1;
        pendingTool = null;
        ui.removeCallbacks(hideMsg);
        ui.removeCallbacks(hideBadgeTask);
        badgeOn = false;
        if (badge != null) badge.setVisibility(View.GONE);
        hideBubWin();
        if (bubble != null) bubble.setText("");
        if (face != null) face.setMood(Face.NORMAL);
        if (was) note("bubble hide（" + why + "）");
        if (failBreak) {   // ★69.5：失败冒过头了，收场后立刻回到"让位"
            failBreak = false;
            note("失败破例收场 → 回到让位");
            applyClientHide(cliPkg);
        }
    }

    /** 角标：高优先级消息在屏时，工具活动只在气泡右侧点一下（★出现才占宽，收起就还回去）。 */
    private void setBadge(final String t) {
        if (badge == null) return;
        ui.removeCallbacks(hideBadgeTask);
        if (t == null || t.length() == 0) {
            if (!badgeOn) return;
            badgeOn = false;
            badge.setVisibility(View.GONE);
            note("badge hide");
            relayoutBub("角标收起");
            return;
        }
        badge.setText(t);
        badge.setVisibility(View.VISIBLE);
        badgeOn = true;
        relayoutBub("角标出现");
        ui.postDelayed(hideBadgeTask, T_BADGE);
    }

    /** 角标出现/收起会让气泡让出或收回那一小块宽度（只动气泡窗，形象不动）。 */
    private void relayoutBub(String why) {
        if (msgKind < 0 || !bubAdded) return;
        layOutChildren(why);
    }

    private static String compactBadge(String line) {
        if (line == null) return "";
        int i = line.indexOf(" · ");
        String head = i > 0 ? line.substring(0, i) : line;
        return clip(head, 10);
    }

    private boolean bubbleShown() { return bubShown(); }

    // ───────────────────────── ★69.5：在 AI 客户端里让位 ─────────────────────────

    /** 当前前台应用的包名（读不到＝null）。用现成机制：无障碍的 getRootInActiveWindow。
     *  ★70 B 线：读空时沿用上一次判定（FG_KEEP_MS 之内），避免瞬时读空被误判成「离开客户端」。 */
    private String fgPkg() {
        try {
            android.view.accessibility.AccessibilityNodeInfo r = svc.getRootInActiveWindow();
            CharSequence p = r == null ? null : r.getPackageName();
            String pk = p == null ? null : p.toString();
            long now = System.currentTimeMillis();
            if (pk != null && pk.length() > 0) { lastFgPkg = pk; lastFgAt = now; return pk; }
            if (lastFgPkg != null && now - lastFgAt < FG_KEEP_MS) return lastFgPkg;
            return null;
        } catch (Throwable t) { return lastFgPkg; }
    }

    /** ★70 B 线：让位去抖 —— 命中名单立即生效；「解除」＝确认非名单后再等 RELEASE_DEBOUNCE_MS。 */
    private boolean namedStable(boolean named) {
        long now = System.currentTimeMillis();
        if (named) { lastNamedAt = now; return true; }
        if (lastNamedAt > 0L && now - lastNamedAt < RELEASE_DEBOUNCE_MS) return true;
        return false;
    }

    /** 名单命中（含 Reticle 自己）。名单＝设置里那串包名，逗号分隔。 */
    private boolean inHideList(String pk) {
        if (pk == null) return false;
        try {
            if (pk.startsWith(svc.getPackageName())) return true;
            String s = hidePkgs(svc);
            if (s == null) return false;
            String[] a = s.split(",");
            for (int i = 0; i < a.length; i++) { String x = a[i].trim(); if (x.length() > 0 && pk.equals(x)) return true; }
        } catch (Throwable t) { }
        return false;
    }

    /** 门铃豁免是否还有效（切走即作废）。 */
    private boolean exemptNow(String pk) {
        if (exemptUntil <= 0L) return false;
        if (System.currentTimeMillis() > exemptUntil) { exemptUntil = 0L; exemptPkg = null; return false; }
        if (exemptPkg != null && pk != null && !pk.equals(exemptPkg)) { exemptUntil = 0L; exemptPkg = null; note("门铃豁免作废（已切走）"); return false; }
        return true;
    }

    /** 让位：按档收成细条／摘窗／只禁冒泡。★报告与工具摘要一律不冒泡。 */
    private void applyClientHide(String pk) {
        try {
            int m = hideMode(svc);
            if (m == 1) {                                  // 全隐：连条也没有
                if (figAdded) { note("让位：全隐（摘窗）"); takeAway(); }
            } else if (m == 0) {                           // 只留细条：缩成 20dp×100dp 那条
                ensure();
                if (state != EDGE) setState(EDGE, "让位（只留细条）");
            } else {                                       // 只藏气泡：小脸留着，只是不冒泡
                ensure();
            }
        } catch (Throwable t) { noteFail("applyClientHide", t); }
    }

    /** 解除让位：窗口放回＋按正常三态（回来时亮一下）。 */
    private void clearClientHide(String why) {
        try {
            ensure();
            if (!figAdded) return;
            if (state == EDGE) setState(ACTIVE, "让位解除：" + why);
            scheduleIdle();
        } catch (Throwable t) { }
    }

    /** 失败破例：只有"必失败"能钻这个孔，且息屏／锁屏／手动关一律不钻。 */
    private boolean canFailBreakout() {
        try {
            if (!isOn(svc)) return false;
            PowerManager pm = (PowerManager) svc.getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) return false;
            KeyguardManager km = (KeyguardManager) svc.getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null && km.isKeyguardLocked()) return false;
            String pk = fgPkg();
            return pk != null && !pk.startsWith(svc.getPackageName()) && inHideList(pk);
        } catch (Throwable t) { return false; }
    }

    /** 被动钩子：前台窗口变了（VeriService 的 TYPE_WINDOW_STATE_CHANGED 转进来）——不轮询、不费电。 */
    static void onFgChange(CharSequence pkg) {
        final Mascot m = S;
        if (m == null) return;
        final String pk = pkg == null ? null : pkg.toString();
        if (pk == null) return;                 // ★70 B 线：拿不到包名＝不动判定（防空包名把让位解除掉）
        m.ui.post(new Runnable() { public void run() {
            try {
                boolean self = pk != null && pk.startsWith(m.svc.getPackageName());
                boolean named = !self && m.inHideList(pk);
                if (named && m.exemptNow(pk)) return;                 // 门铃豁免中：按正常规则
                if (named && !m.cliHide) {
                    m.cliHide = true; m.cliPkg = pk;
                    m.note("客户端让位：" + pk + "（档=" + hideModeName(hideMode(m.svc)) + "）");
                    m.applyClientHide(pk);
                } else if (!named && m.cliHide) {
                    String old = m.cliPkg;
                    m.cliHide = false; m.cliPkg = null;
                    m.note("离开客户端：" + old + " → 解除让位");
                    m.clearClientHide("离开客户端");
                }
            } catch (Throwable t) { }
        }});
    }
    /** 三条闭嘴：息屏／锁屏、Reticle 自己在前台、用户手动关。 */
    /** 四条闭嘴（★69.5 优先级：Reticle 自己前台 ＞ 客户端名单 ＞ 息屏／锁屏 ＞ 手动关）。 */
    private boolean visible(boolean force) {
        try {
            if (!isOn(svc)) return false;                                   // 4) 手动关（最高否决）
            String pk = fgPkg();
            boolean self = pk != null && pk.startsWith(svc.getPackageName());
            if (self && !force) return false;                               // 1) Reticle 自己在前台
            boolean named = !self && namedStable(inHideList(pk));
            if (named && !force && !exemptNow(pk)) {                        // 2) 客户端名单 ⇒ 让位
                if (!cliHide || cliPkg == null || !cliPkg.equals(pk)) {
                    cliHide = true; cliPkg = pk;
                    note("客户端让位：" + pk + "（档=" + hideModeName(hideMode(svc)) + "）");
                }
                applyClientHide(pk);
                return failBreak;                                           // ★唯一穿孔：失败破例中才放行
            }
            if (cliHide) { String old = cliPkg; cliHide = false; cliPkg = null; note("离开客户端：" + old + " → 解除让位"); clearClientHide("离开客户端"); }
            PowerManager pm = (PowerManager) svc.getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) return false;            // 3) 息屏
            KeyguardManager km = (KeyguardManager) svc.getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null && km.isKeyguardLocked()) return false;          //    锁屏
            return true;
        } catch (Throwable t) { return true; }
    }

    // ───────────────────────── 位置／朝向／透明度 ─────────────────────────

    private boolean isRightSide() {
        int s = sidePref();
        if (s == 1) return false;
        return true;     // 1＝左；2＝右；未定也按右
    }

    /** 贴左镜像、贴右不镜像（自动镜像＝开关）。★66：位置一变就调它，并留 `mirror side=… sx=…` 证据。 */
    private void applyMirror() {
        try {
            if (img == null || face == null) return;
            boolean left = !isRightSide();
            boolean on = mirror(svc);
            float sx = (on && left) ? -1f : 1f;
            img.setScaleX(sx);
            face.setScaleX(sx);
            note("mirror side=" + (left ? "左" : "右") + " sx=" + (sx < 0f ? "-1" : "1") + " on=" + (on ? 1 : 0));
        } catch (Throwable t) { }
    }

    private void applyAlpha() {
        try {
            if (figWin == null) return;
            float base = alpha(svc);
            float f = base;
            if (state == DIMMED) f = Math.max(0.30f, base * 0.65f);   // 66：淡，但不去饱和
            if (state == EDGE) f = Math.min(base, 0.55f);
            float bf = Math.min(1f, base + 0.1f);
            if (shotHidden) { f = 0f; bf = 0f; }                      // 截图让路：透明（不摘视图）
            figWin.setAlpha(f);
            if (bubWin != null) bubWin.setAlpha(bf);
        } catch (Throwable t) { }
    }

    /**
     * 摆位（核心）。★人＝贴边＋纵向比例；气泡＝紧贴内容、以人矩形为锚、伸到屏外就翻边。
     * 人窗的尺寸／位置在这里**只在必要时**改（拖动、换边、转屏、状态切换），气泡的出现/更新/收场都不碰它。
     */
    private void layOutChildren(String why) {
        if (figWin == null || figLp == null || bubWin == null || bubLp == null) return;
        try {
            boolean right = isRightSide();
            int fx = right ? Math.max(0, sw - figW) : 0;
            int fy = clampI(figLp.y, 0, Math.max(0, sh - figH));
            figLp.width = figW;
            figLp.height = figH;
            figLp.x = fx;
            figLp.y = fy;

            int dir = -9, bw = 0, bh = 0, bx = 0, by = 0;
            if (bubShown()) {
                int[] wh = measureBubble();
                bw = wh[0];
                bh = wh[1];
                int gap = dp(3);
                if (vertPref(svc)) {
                    // ★66 返工（用户拍）：纵向优先＝气泡**完全紧贴屏幕侧边（人所在那一侧）＋在人的上方**；
                    //   上方放不下就翻到下方；横向一律贴边，不再居中压在形象身上。
                    bx = right ? Math.max(0, sw - bw) : 0;
                    by = fy - gap - bh;
                    if (by < 0) { by = fy + figH + gap; dir = 2; } else dir = 0;
                } else {
                    dir = bubbleDir(right, fy, bh);
                    int[] xy = bubbleXY(dir, fx, fy, bw, bh, gap);
                    dir = xy[0];
                    bx = xy[1];
                    by = xy[2];
                }
                bx = clampI(bx, 0, Math.max(0, sw - bw));
                by = clampI(by, 0, Math.max(0, sh - bh));
                bubLp.width = bw;
                bubLp.height = bh;
                bubLp.x = bx;
                bubLp.y = by;
            }
            placeFig();
            if (bubAdded) placeBub();
            if (why != null && why.length() > 0)
                note("layout(" + why + ") side=" + (right ? "右" : "左") + " dir=" + dir
                        + " win=" + figW + "x" + figH + " at " + figLp.x + "," + figLp.y
                        + " bub=" + bw + "x" + bh + " at " + bx + "," + by);
            applyMirror();   // ★66：位置一变就重新镜像
        } catch (Throwable t) { noteFail("layout", t); }
    }

    /** 气泡尺寸：★只包住内容——角标放**文字上方**（不占右侧宽度、不撑宽气泡），收起就把高度还回去。
     *  横向优先＝形象旁边横着一条；纵向优先＝贴屏幕侧边、在形象上方。超出上限交给 ScrollView 滚动（手滑）。 */
    private int[] measureBubble() {
        boolean vert = vertPref(svc);
        int maxW = vert ? Math.min((int) (sw * 0.36f), dp(200)) : Math.min(dp(170), (int) (sw * 0.45f));
        int maxH = vert ? (int) (sh * 0.20f) : (int) (sh * 0.14f);
        int padL = dp(10), padR = dp(10), padT = dp(7), padB = dp(7);
        if (bubble == null) return new int[] { Math.max(dp(80), maxW), maxH };
        boolean hasBadge = badgeOn && (msgKind == M_REPORT || msgKind == M_FAIL);
        int bH = 0, gapV = 0, bW = 0;
        try {
            if (hasBadge) {
                badge.setMaxWidth(dp(150));
                badge.measure(View.MeasureSpec.makeMeasureSpec(dp(150), View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.UNSPECIFIED);
                bH = badge.getMeasuredHeight();
                bW = badge.getMeasuredWidth();
                gapV = dp(4);
            }
        } catch (Throwable t) { bH = 0; bW = 0; gapV = 0; }
        int contentMax = Math.max(dp(56), maxW - padL - padR);
        bubble.setMaxWidth(contentMax);
        bubble.measure(View.MeasureSpec.makeMeasureSpec(contentMax, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.UNSPECIFIED);
        int tw = clampI(bubble.getMeasuredWidth(), dp(40), contentMax);
        int availH = Math.max(dp(20), maxH - padT - padB - bH - gapV);
        int th = clampI(bubble.getMeasuredHeight(), dp(20), availH);
        int w = tw + padL + padR;
        int h = th + padT + padB + bH + gapV;
        try {
            if (bH > 0) {   // ★角标在**文字上方**，宽度不超过文字列 ⇒ 不再往右占地方
                FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(Math.min(bW, tw), bH);
                blp.gravity = Gravity.TOP | Gravity.START;
                badge.setLayoutParams(blp);
                badge.setVisibility(View.VISIBLE);
            } else {
                badge.setVisibility(View.GONE);
            }
            FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(tw, th);
            wlp.gravity = Gravity.TOP | Gravity.START;
            wlp.topMargin = bH + gapV;
            bubbleWrap.setLayoutParams(wlp);
            bubble.setLayoutParams(new ScrollView.LayoutParams(tw, ViewGroup.LayoutParams.WRAP_CONTENT));
            boolean scroll = bubble.getMeasuredHeight() > th;
            if (scroll && !scrollLogged) { scrollLogged = true; note("bubble 滚动到上限（文字高 " + bubble.getMeasuredHeight() + " > 可视 " + th + "，可滚）"); }
        } catch (Throwable t) { noteFail("measureBubble", t); }
        return new int[] { w, h };
    }

    private int bubbleDir(boolean right, int fy, int bh) {
        int mode = posMode(svc);
        if (mode == 1) return (fy - dp(3) - bh >= 0) ? 0 : 2;   // 正上方；上方放不下就翻到下面
        return right ? -1 : 1;                                   // 斜 45°＝朝屏内
    }

    /** 返回 {dir, x, y}：出屏就按**人所在侧**翻到另一侧；绝不为了塞气泡去推人。 */
    private int[] bubbleXY(int dir, int fx, int fy, int bw, int bh, int gap) {
        int bx = 0, by = 0;
        if (dir == -1) { bx = fx - gap - bw; by = fy + (figH - bh) / 2; }
        else if (dir == 1) { bx = fx + figW + gap; by = fy + (figH - bh) / 2; }
        else if (dir == 0) { bx = fx + (figW - bw) / 2; by = fy - gap - bh; }
        else { bx = fx + (figW - bw) / 2; by = fy + figH + gap; }

        if (dir == -1 && bx < 0) { dir = 1; bx = fx + figW + gap; }
        else if (dir == 1 && bx + bw > sw) { dir = -1; bx = fx - gap - bw; }
        else if (dir == 0 && by < 0) { dir = 2; by = fy + figH + gap; }
        else if (dir == 2 && by + bh > sh) { dir = 0; by = fy - gap - bh; }
        // 翻完仍越界：气泡自己让步（夹到屏内），人不动
        bx = clampI(bx, 0, Math.max(0, sw - bw));
        by = clampI(by, 0, Math.max(0, sh - bh));
        return new int[] { dir, bx, by };
    }

    private String dirName(int dir) {
        if (dir == -1) return "左";
        if (dir == 1) return "右";
        if (dir == 0) return "上";
        if (dir == 2) return "下";
        return "无";
    }

    private void placeFig() {
        if (!figAdded || wm == null) return;
        try { wm.updateViewLayout(figWin, figLp); } catch (Throwable t) { noteFail("placeFig", t); }
    }

    private void placeBub() {
        if (!bubAdded || wm == null) return;
        try { wm.updateViewLayout(bubWin, bubLp); } catch (Throwable t) { noteFail("placeBub", t); }
    }

    /** 配置页／助手要"当场演一下"：绕过"自己在前台就闭嘴"，让用户立刻看见气泡效果。 */
    static void preview(String text) {
        final Mascot m = S;
        if (m == null || text == null) return;
        m.ui.post(new Runnable() {
            public void run() { m.layOutChildren("预览"); m.say("🎈 " + text, M_NORMAL, true); }
        });
    }

    /** 按压缩放 → 回弹（Q 弹；动效期间热区跟着缩放，漂移小）。 */
    private void pressFx() {
        try {
            if (figWin == null) return;
            if (state == EDGE) setState(ACTIVE, "点了贴边条");
            figWin.animate().cancel();
            figWin.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).withEndAction(new Runnable() {
                public void run() {
                    figWin.animate().scaleX(1.05f).scaleY(1.05f).setDuration(130).withEndAction(new Runnable() {
                        public void run() { figWin.animate().scaleX(1f).scaleY(1f).setDuration(90); }
                    });
                }
            });
        } catch (Throwable t) { }
    }

    private void toastSelf(String s) { note("click " + s); }

    // ───────────────────────── 触摸：点击／拖动（★气泡不参与碰撞） ─────────────────────────

    private boolean onFigTouch(MotionEvent e) {
        try {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragging = false; moved = false;
                    downRawX = e.getRawX(); downRawY = e.getRawY();
                    dragFigX0 = figLp.x; dragFigY0 = figLp.y;
                    dragBubX0 = bubLp.x; dragBubY0 = bubLp.y;
                    pressFx();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - downRawX, dy = e.getRawY() - downRawY;
                    if (!moved && (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6))) { moved = true; dragging = true; }
                    if (dragging) {
                        // ★限位只认"图形（人）自己的矩形"——气泡没有碰撞箱（用户原话），所以长气泡顶到边也走得过半屏
                        int nx = clampI((int) (dragFigX0 + dx), 0, Math.max(0, sw - figW));
                        int ny = clampI((int) (dragFigY0 + dy), 0, Math.max(0, sh - figH));
                        figLp.x = nx;
                        figLp.y = ny;
                        if (bubAdded) {   // 气泡跟着人走（只挪位置、尺寸不变）
                            bubLp.x = dragBubX0 + (int) dx;
                            bubLp.y = dragBubY0 + (int) dy;
                            placeBub();
                        }
                        placeFig();
                        long now = System.currentTimeMillis();
                        if (now - lastDragNote > 200L) {
                            lastDragNote = now;
                            note("drag fig=(" + nx + "," + ny + ") side=" + ((nx + figW / 2) > sw / 2 ? "右" : "左")
                                    + " bubbleDir=" + dirName(bubDirNow()));
                        }
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    if (dragging) {
                        snapToEdge();
                        return true;
                    }
                    if (!moved) {
                        toastSelf("点了一下");
                        // ★69.5 门铃：在客户端里点一下（那会儿只有细条）＝ 本次前台豁免，恢复正常三态与冒泡
                        String fpk = fgPkg();
                        if (fpk != null && !fpk.startsWith(svc.getPackageName()) && inHideList(fpk)) {
                            exemptPkg = fpk;
                            exemptUntil = System.currentTimeMillis() + EXEMPT_MS;
                            cliHide = false; cliPkg = null; failBreak = false;
                            note("门铃：点细条 ⇒ 本次前台豁免 " + (EXEMPT_MS / 1000L) + " 秒（" + fpk + "）");
                        }
                        if (state != ACTIVE) setState(ACTIVE, "用户点了一下");
                        if (bubShown()) hideBubble("点一下收起");
                        scheduleIdle();
                    }
                    return true;
                }
                default: return false;
            }
        } catch (Throwable t) { noteFail("touch", t); return false; }
    }

    private int bubDirNow() {
        if (!bubShown()) return -9;
        int gap = dp(3);
        return bubbleDir(isRightSide(), figLp.y, bubLp.height > 0 ? bubLp.height : dp(40));
    }

    /** 松手吸左/右边缘：★左右判定只认"图形自己的矩形"（65 用窗口/整体判断 ⇒ 长气泡时判反、弹回原侧）。 */
    private void snapToEdge() {
        boolean right = (figLp.x + figW / 2) > sw / 2;
        float frac = sh > 0 ? (float) figLp.y / (float) sh : 0.34f;
        frac = frac < 0f ? 0f : (frac > 0.92f ? 0.92f : frac);
        savePlace(right ? 2 : 1, frac);
        layOutChildren("吸边");
        note("snap side=" + (right ? "右" : "左") + " fig=(" + figLp.x + "," + figLp.y + ") bubbleDir=" + dirName(bubDirNow()));
        if (state != ACTIVE) setState(ACTIVE, "拖完回前台态");
        scheduleIdle();
    }

    // ───────────────────────── 让路（截图 ＋ 触摸类工具） ─────────────────────────

    /** 截图让路：alpha=0（不摘视图 ⇒ 没有"摘掉再放回"的闪）。65 只证过"挪窗口无效"，alpha 从没试过。 */
    /** ★67 B 线（66 残留）：截图让路**回退成摘视图**（`SHOT_BY_ALPHA=false`）。
     *  66 实测 alpha=0 让路 4 次里 1 次仍把气泡截进图 ⇒ "自读自话"的风险没消；摘视图会闪一下，但可靠。 */
    static void hideForShot() {
        final Mascot m = S;
        if (m == null) return;
        m.runSync(new Runnable() {
            public void run() {
                m.shotHidden = true;
                if (SHOT_BY_ALPHA) {
                    m.applyAlpha();
                    m.note("yield on（截图·alpha=0 不摘视图）");
                    return;
                }
                if (m.yielded) { m.note("yield on（截图·复用触摸让路，窗口已摘）"); return; }
                m.takeAway();
                m.note("yield on（截图·摘视图）");
            }
        });
    }

    static void restoreAfterShot() {
        final Mascot m = S;
        if (m == null) return;
        m.runSync(new Runnable() {
            public void run() {
                m.shotHidden = false;
                if (SHOT_BY_ALPHA) {
                    m.applyAlpha();
                    m.note("yield off（截图·恢复 alpha）");
                    return;
                }
                if (m.yielded) { m.note("yield off（截图·触摸让路还占着，窗口先不放）"); return; }
                m.putBack();
                m.applyAlpha();
                m.note("yield off（截图·窗口放回）");
            }
        });
    }

    /** 触摸类工具：触点可能落在形象上时先让路；★2 秒内的连续取图／触摸合并成一次（去抖）。 */
    static boolean yieldForTouch(String req) {
        final Mascot m = S;
        if (m == null || req == null) return false;
        try {
            JSONObject o = new JSONObject(req);
            if (!"tools/call".equals(o.optString("method", ""))) return false;
            JSONObject p = o.optJSONObject("params");
            if (p == null) return false;
            String name = p.optString("name", "");
            JSONObject a = p.optJSONObject("arguments");
            if (a == null) a = new JSONObject();
            if (!touchy(name)) return false;
            boolean hit;
            if (name.equals("tap") || name.equals("longpress")) hit = m.covers(a.optDouble("x", -1), a.optDouble("y", -1));
            else if (name.equals("swipe")) hit = m.covers(a.optDouble("x1", -1), a.optDouble("y1", -1)) || m.covers(a.optDouble("x2", -1), a.optDouble("y2", -1));
            else hit = true;   // 按文字点／滚动／输入／批量／按键：算不出坐标 ⇒ 保守让路（安全超集）
            if (!hit) return false;
            m.beginYield(name);
            return true;
        } catch (Throwable t) { return false; }
    }

    static void endYield() {
        final Mascot m = S;
        if (m == null || !m.yielded) return;
        m.scheduleRestore(YIELD_KEEP);
        m.note("yield off（排队 " + (YIELD_KEEP / 1000) + " 秒再恢复，期间的连续动作用同一次让路）");
    }

    private void beginYield(String why) {
        yieldAt = System.currentTimeMillis();
        if (restoreYield != null) { ui.removeCallbacks(restoreYield); restoreYield = null; }
        if (yielded) { note("yield on（" + why + "·沿用同一次让路）"); return; }
        yielded = true;
        runSync(new Runnable() {
            public void run() {
                takeAway();
                note("yield on（触摸：摘视图）");
            }
        });
    }

    private void scheduleRestore(long delay) {
        if (restoreYield == null) restoreYield = new Runnable() { public void run() { restoreNow(); } };
        ui.removeCallbacks(restoreYield);
        ui.postDelayed(restoreYield, delay);
    }

    private void restoreNow() {
        restoreYield = null;
        long idle = System.currentTimeMillis() - yieldAt;
        if (yielded && idle < YIELD_KEEP) { scheduleRestore(YIELD_KEEP - idle); return; }
        yielded = false;
        putBack();
        note("yield restore（窗口放回）");
    }

    private void takeAway() {
        try {
            if (bubAdded) { wm.removeView(bubWin); bubAdded = false; }
            if (figAdded) { wm.removeView(figWin); figAdded = false; }
        } catch (Throwable t) { noteFail("takeAway", t); }
    }

    private void putBack() {
        try {
            if (wm == null || !isOn(svc)) return;
            if (!figAdded) { wm.addView(figWin, figLp); figAdded = true; }
            if (bubShown() && !bubAdded) { wm.addView(bubWin, bubLp); bubAdded = true; }
        } catch (Throwable t) { noteFail("putBack", t); }
    }

    private static boolean touchy(String n) {
        return n.equals("tap") || n.equals("swipe") || n.equals("longpress") || n.equals("tap_text")
            || n.equals("scroll") || n.equals("do_chain") || n.equals("key") || n.equals("input")
            || n.equals("paste") || n.equals("scroll_to_text");
    }

    /** 触点是否落在形象可触矩形里（★tap/swipe/longpress 传的是**屏幕比例 0~1**，先换算成像素）。 */
    private boolean covers(double px, double py) {
        try {
            if (px < 0 || py < 0) return false;
            if (px <= 1.0 && py <= 1.0) { px = px * sw; py = py * sh; }
            if (hitLp(figLp, px, py)) return true;
            if (bubAdded && hitLp(bubLp, px, py)) return true;
            return false;
        } catch (Throwable t) { return false; }
    }

    private static boolean hitLp(WindowManager.LayoutParams p, double x, double y) {
        if (p == null) return false;
        return x >= p.x && x <= p.x + p.width && y >= p.y && y <= p.y + p.height;
    }

    /** 主线程直跑；别的线程等它跑完（让路必须"当场生效"，不能 post 完就截）。 */
    /** 主线程直跑；别的线程等它跑完，**并等两帧**（让 alpha／摘视图真的合成出去，否则截图可能抓在改动之前）。 */
    private void runSync(final Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) { r.run(); return; }
        final CountDownLatch l = new CountDownLatch(1);
        ui.post(new Runnable() {
            public void run() {
                try { r.run(); } catch (Throwable t) { }
                try {
                    figWin.postOnAnimation(new Runnable() {
                        public void run() {
                            figWin.postOnAnimation(new Runnable() { public void run() { l.countDown(); } });
                        }
                    });
                } catch (Throwable t) { l.countDown(); }
            }
        });
        try { l.await(600, TimeUnit.MILLISECONDS); } catch (Throwable t) { }
    }

    // ───────────────────────── 自选图 ─────────────────────────

    private void loadImage() {
        Bitmap b = null;
        try {
            File f = new File(imagePath(svc));
            if (f.isFile()) b = BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (Throwable t) { b = null; }
        if (b != null) {
            img.setImageBitmap(b);
            img.setVisibility(View.VISIBLE);
            face.setVisibility(View.GONE);
        } else {
            img.setVisibility(View.GONE);
            face.setVisibility(View.VISIBLE);
        }
        applyMirror();   // ★66：加了自选图之后也调一次镜像
    }

    static void reloadImage() {
        final Mascot m = S;
        if (m == null) return;
        m.ui.post(new Runnable() { public void run() { m.loadImage(); } });
    }

    /** 配置页「换一张图」：复制到「运行目录/外观/形象.png」；失败／取消都给一句人话。 */
    public static String takePickedImage(Context c, Intent data) {
        if (data == null || data.getData() == null) return Words.MS_IMG_CANCEL;
        InputStream in = null; OutputStream out = null;
        File tmp = null;
        try {
            File dir = Home.sub(c, DIRNAME);
            File dst = new File(dir, IMG);
            tmp = new File(dir, IMG + ".tmp");
            Uri u = data.getData();
            in = c.getContentResolver().openInputStream(u);
            if (in == null) return Words.MS_IMG_NOACCESS;
            out = new FileOutputStream(tmp);
            byte[] buf = new byte[8192];
            int n; long tot = 0L;
            while ((n = in.read(buf)) > 0) {
                tot += n;
                if (tot > 8L * 1024 * 1024) { out.close(); out = null; tmp.delete(); return Words.MS_IMG_TOOBIG; }
                out.write(buf, 0, n);
            }
            out.close(); out = null;
            if (dst.exists()) dst.delete();
            if (!tmp.renameTo(dst)) { tmp.delete(); return Words.MS_IMG_FAIL; }
            Bitmap chk = null;
            try { chk = BitmapFactory.decodeFile(dst.getAbsolutePath()); } catch (Throwable t) { chk = null; }
            if (chk == null) { dst.delete(); reloadImage(); return Words.MS_IMG_BAD; }
            reloadImage();
            return Words.MS_IMG_OK;
        } catch (Throwable t) {
            return Words.MS_IMG_FAIL + "（" + t + "）";
        } finally {
            try { if (in != null) in.close(); } catch (Throwable t) { }
            try { if (out != null) out.close(); } catch (Throwable t) { }
        }
    }

    // ───────────────────────── 小工具 ─────────────────────────

    /** ★71 A 线：形象的"失败"判据也只看协议结构化字段——**不许扫正文**
     *  （70 号的 `"ok":0`／`"error":` 是全串找词：读一份 JSON 日志、翻一段文本就可能被当成失败）。
     *  ①JSON-RPC 顶层 error ②result.isError:true —— 这两处才是"真失败"。 */
    private static boolean looksBad(String resp) {
        if (resp == null) return false;
        try {
            JSONObject o = new JSONObject(resp);
            if (o.has("error")) return true;
            JSONObject r = o.optJSONObject("result");
            return r != null && r.optBoolean("isError", false);
        } catch (Throwable t) { return false; }
    }

    /** 72 号单（71 遗留）：失败气泡只显示人话，不再从 JSON 中间截一段出来。 */
    private static String tailText(String resp) {
        String s = resp == null ? "" : resp;
        try {
            String m = pickMsg(new org.json.JSONObject(s.trim()), 0);
            if (m != null && m.length() > 0) return clip(m, 60);
        } catch (Throwable t) { }
        return clip(s, 60);
    }

    /** 从工具结果里挑一句人话：err／message／error.message → result.content[0].text；嵌套 JSON 再钻一层。 */
    private static String pickMsg(org.json.JSONObject o, int depth) {
        if (o == null || depth > 3) return null;
        String[] direct = {"err", "message", "msg", "error"};
        for (int i = 0; i < direct.length; i++) {
            Object v = o.opt(direct[i]);
            if (v == null) continue;
            String sv = String.valueOf(v);
            if (sv.length() == 0 || "null".equals(sv)) continue;
            if (sv.trim().startsWith("{")) {
                try {
                    String inner = pickMsg(new org.json.JSONObject(sv.trim()), depth + 1);
                    if (inner != null && inner.length() > 0) return inner;
                } catch (Throwable t) { }
                continue;
            }
            return sv;
        }
        org.json.JSONObject err = o.optJSONObject("error");
        if (err != null) { String m = pickMsg(err, depth + 1); if (m != null && m.length() > 0) return m; }
        org.json.JSONObject r = o.optJSONObject("result");
        if (r != null) {
            org.json.JSONArray ct = r.optJSONArray("content");
            if (ct != null && ct.length() > 0) {
                org.json.JSONObject c0 = ct.optJSONObject(0);
                if (c0 != null) {
                    String t2 = c0.optString("text", "");
                    if (t2.length() > 0) return strip(t2);
                }
            }
            String m2 = pickMsg(r, depth + 1);
            if (m2 != null && m2.length() > 0) return m2;
        }
        return null;
    }

    /** 人话里若夹着整块 JSON（我们自家的 {"ok":0,"err":…}），只留引号里那句话。 */
    private static String strip(String s) {
        String t = s.trim();
        if (t.startsWith("{") && t.endsWith("}")) {
            try {
                String m = pickMsg(new org.json.JSONObject(t), 3);
                if (m != null && m.length() > 0) return m;
            } catch (Throwable x) { }
        }
        return s;
    }

    static void noteFace(int w, int h, int vis, int mood) {
        Mascot m = S;
        if (m != null) m.writeLog("face onDraw w=" + w + " h=" + h + " vis=" + vis + " mood=" + mood);
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        s = s.replace('\n', ' ').trim();
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    void note(String s) { writeLog("mascot: " + s); }

    private void noteFail(String where, Throwable t) { writeLog("mascot FAIL " + where + ": " + t); }

    /** 三态／触摸／气泡都写这里（验收要"截图＋日志"双证）。 */
    /** ★71 B 线（2026-10-15）：界面侧的话（应用列表这类）也落同一份日志。
     *  ★隐私纪律：调用方只许写数量，不写整串包名。 */
    public static void logLine(Context c, String line) {
        try {
            File d = Home.sub(c, "logs");
            if (d == null) return;
            File f = new File(d, "mascot.log");
            if (f.length() > 256 * 1024) f.delete();
            OutputStream o = new FileOutputStream(f, true);
            o.write((System.currentTimeMillis() + " " + line + "\n").getBytes("UTF-8"));
            o.close();
        } catch (Throwable t) { }
    }

    void writeLog(String line) {
        try {
            File d = Home.sub(svc, "logs");
            File f = new File(d, "mascot.log");
            if (f.length() > 256 * 1024) f.delete();
            OutputStream o = new FileOutputStream(f, true);
            o.write((System.currentTimeMillis() + " " + line + "\n").getBytes("UTF-8"));
            o.close();
        } catch (Throwable t) { }
    }

    /** 默认形象：原创极简几何小脸（圆脸＋两眼＋嘴）；★66 加一颗脸颊痣——不镜像时在右颊、镜像后在左颊，
     *  这样"自动镜像开了没生效"肉眼/截图都看得出来（对称图形是看不出镜像的）。 */
    static class Face extends View {
        static boolean Logged = false;
        static final int NORMAL = 0, TALK = 1, FAIL = 2;
        private int mood = NORMAL;
        private boolean dim = false;
        Face(Context c) { super(c); }
        void setMood(int m) { if (mood != m) { mood = m; invalidate(); } }
        void setDim(boolean d) { if (dim != d) { dim = d; invalidate(); } }
        @Override protected void onDraw(Canvas cv) {
            if (!Logged) { Logged = true; noteFace(getWidth(), getHeight(), getVisibility(), mood); }
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            float r = Math.min(w, h) / 2f - 1f;
            if (r <= 2f) return;
            int ink = dim ? 0xFF9A9A9A : 0xFF2A2A2A;
            int fill = dim ? 0xE8FFFFFF : 0xF5FFFFFF;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(fill);
            float rr = dim ? r * 0.42f : r;
            cv.drawCircle(cx, cy, rr, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, rr * 0.10f));
            p.setColor(ink);
            cv.drawCircle(cx, cy, rr, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(ink);
            float ex = rr * 0.34f, ey = -rr * 0.10f, er = rr * 0.14f;
            cv.drawCircle(cx - ex, cy + ey, er, p);
            cv.drawCircle(cx + ex, cy + ey, er, p);
            cv.drawCircle(cx + rr * 0.55f, cy + rr * 0.42f, Math.max(1f, rr * 0.085f), p);   // ★脸颊痣（不对称，用来看镜像）
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, rr * 0.09f));
            if (mood == FAIL) {
                RectF rf = new RectF(cx - rr * 0.40f, cy + rr * 0.30f, cx + rr * 0.40f, cy + rr * 0.80f);
                cv.drawArc(rf, 200, 140, false, p);
            } else if (mood == TALK) {
                p.setStyle(Paint.Style.FILL);
                RectF rf = new RectF(cx - rr * 0.22f, cy + rr * 0.22f, cx + rr * 0.22f, cy + rr * 0.62f);
                cv.drawOval(rf, p);
            } else {
                RectF rf = new RectF(cx - rr * 0.38f, cy + rr * 0.10f, cx + rr * 0.38f, cy + rr * 0.62f);
                cv.drawArc(rf, 20, 140, false, p);
            }
        }
    }
}
