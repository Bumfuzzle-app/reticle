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
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.animation.OvershootInterpolator;
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
    static final String K_MINIMAL = "mascot_minimal";      // ★87 极简模式：只有白条＋气泡，没有脸（默认关）
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
    private Bar edgeBar;
    // ★89 白条的手：手势状态 ＋ 拖动层（拖动时整屏一次开够，只画面平移，绝不每帧改窗口）
    private static final int G_NONE = 0, G_SWIPE = 1, G_DRAG = 2;
    private static final int BAR_LONG_MS = 300;        // 长按挤压（可调常量 1）
    private static final float BAR_SLOP_DP = 8f;       // 离开"点"的判定（可调常量 2）
    private static final float BAR_SWIPE_DP = 44f;     // 内滑阈值参考（成形按进度 55% 判；可调常量 3）
    // ★89.5 整改（19 号验收）：慢拖最多离边 1cm（tanh 硬阻尼，前段 1:1 跟手）；松手 200ms 回弹；换边靠甩 350ms 飞对面
    private static final float BAR_DETACH_CM = 1.0f;    // 慢拖：白条离边上限（cm）
    private static final float BAR_DAMP_SW = 0.55f;     // ★89.7 阻尼上限 Dmax＝屏宽 55%（指数渐近，不是硬顶）
    private static final float BAR_BARRIER_SW = 0.40f;  // ★89.7 判定线＝屏宽 40%（按 applied 算；不显示）
    private static final int BAR_BACK_MS = 200;         // 慢拖松手：位置＋尺寸同一段动画回边（easeOutCubic，无过冲）
    private static final int BAR_FLY_MS = 350;          // 越过判定线：飞到对面（easeOutCubic，无过冲）
    private static final long MORPH_MAX_MS = 200L;      // morph 动画最大存活＝时长＋200ms，超时强制收尾
    // ★89.7 整改三：grow 一条 easeOutCubic（460ms），不再有过冲段（原来 682+242+176ms 两波已删）
    private static final int MORPH_GROW_MS = 460;      // ★89.7：一条 easeOutCubic（无过冲，出手快收尾慢）
    // ★89.6 整改七：按下一条减速曲线到顶（长按只解锁「可以拖」）
    private static final int BAR_PRESS_MS = 450;
    private int gMode = G_NONE;
    private boolean gPreDrag = false;          // ★89.6：已上拖动层但位移还没超过 8dp（松手仍算「点」）
    private float gPrevFx = 0f, gPrevCx = 0f;   // ★89.6：1:1 跟手证据（上一帧手指 x／白条中心 x）
    private boolean gMoved = false, gLongFired = false;
    private float gDownX = 0f, gDownY = 0f, gDownCy = 0f, gLastCy = 0f, gApplied = 0f;
    private float gOffsetX = 0f, gOffsetY = 0f, gDownCx = 0f;   // ★89.5 按下那一刻：手指与白条中心的偏移
    private float gGainLast = 1f;                              // ★89.5 逐段增益（证据用）
    private float gDbgLastD = -1f;                             // ★89.5 逐段增益算用
    private float gHalf0 = 14f;                                 // ★89.5 拖动时白条半粗（按手指贴合用，不随 boost 变）
    private long gDownAt = 0L, gNoteAt = 0L;
    private int gFigY0 = 0;
    private final long[] gFrames = new long[900];
    private int gFrameN = 0;
    private FrameLayout dragWin;
    private Bar dragBar;
    private WindowManager.LayoutParams dragLp;
    private boolean dragAdded = false;
    private ValueAnimator dragAnim = null;      // ★89.5 松手回弹／甩飞对面
    private ValueAnimator barPressAnim = null;  // ★89.5 按下⇒缓缓变粗（300ms 线性，不跳）
    private VelocityTracker gVt = null;         // ★89.5 甩判据（速度）
    private String lastL1 = "", lastL2 = "";   // ★89 内滑拉出「最近一条」用
    private int lastKind = M_NORMAL;
    private ValueAnimator barAnim = null;
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
    // ★87 极简模式：没有脸、白条常驻、气泡照冒
    private boolean minMode = false;          // 极简模式生效中（setup／改开关时读偏好）
    // ★88 气泡从白条长出来（morph）：大窗不可触摸、尺寸提前定死；窄窗永远可点
    private Morph morph;
    private FrameLayout morphWin;
    private WindowManager.LayoutParams morphLp;
    private boolean morphAdded = false;
    private ValueAnimator morphAnim = null;
    private long morphSeq = 0L;
    private int morphState = 0;   // 0＝白条态（无大窗）／1＝生长中／2＝成框／3＝收起中
    // ★89.9 整改二：1.5 秒内两条并列（只留最近两条）／整改一：白条视觉互斥／整改五：名单闸门
    private static final long STACK_MS = 1500L;
    private String stackPrev = null;      // 上一条（正文，状态已接在行末）
    private long stackAt = 0L;            // 上一条出现的时刻
    private boolean noStack = false;      // 一次性：notify 单行、不参与叠加
    static final int BV_NONE = 0, BV_BAR = 1, BV_MORPH = 2, BV_DRAG = 3, BV_FLY = 4;
    private int bvLast = -1;
    // ★90：飞行层自成一格；barFrozen＝切换中（窗建好了先不点亮，或已切到无还没收内容）
    private boolean flying = false;
    private boolean barFrozen = false;
    // ★89.5 整改一：动画最大存活（时长＋200ms）超时强制收尾；任何打断都走到终态，不许停在中间
    private Runnable morphWatch = null;
    private long morphWatchSeq = 0L;
    private boolean morphWatchGrow = true;
    // ★89.5 整改六.3：框成形后叠一个真正的 ScrollView+TextView 做内容层（可滚、随框走、随动画淡入）
    private ScrollView morphScroll = null;
    private TextView morphText = null;
    private int morphPadPx = 0;
    // ★88.5（§八 用户拍 B）：框整窗可触摸——点＝收起，按住往边框滑＝跟着反向缩回
    private float mTouchX = 0f, mTouchY = 0f, mTouchP = 1f;
    private boolean mTouchDrag = false;
    private boolean mScrollDrag = false;   // ★89.5 内容层滚动中（触摸不漏给下层）
    private boolean morphNeedScroll = false;   // ★89.5：内容高 > 可视高（layoutContent 实测，不依赖 getHeight）
    private float mLastScrollY = 0f;

    private boolean bubNeedTouch = false;     // 气泡内容超出可视高 ⇒ 要能滚（可触摸）；不超 ⇒ 整窗不接收触摸
    private long quietUntil = 0L;             // 拖动收起后的静默期（60 秒）：普通调用不弹，notify 与失败照冒
    private float bubDragX0, bubDragY0;
    private boolean bubDragging = false;
    private boolean badgeOn = false;
    private boolean dragging = false, moved = false;
    private boolean bubTouchInit = false;     // 气泡触摸标志位是否已同步过一次
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

    // ───────────── ★87 极简模式：偏好（配置页与运行期共用） ─────────────
    public static boolean minimal(Context c) {
        try { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(K_MINIMAL, false); }
        catch (Throwable t) { return false; }
    }

    public static void setMinimal(Context c, boolean v) {
        try { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(K_MINIMAL, v).apply(); } catch (Throwable t) { }
        final Mascot m = S;
        if (m != null) m.ui.post(new Runnable() { public void run() { m.applyMinimal("开关"); } });
    }

    /** 极简模式的运行时切换：开＝收成白条（没有脸）、白条常驻、不参与三态与让位；关＝回到正常三态。 */
    private void applyMinimal(String why) {
        try {
            boolean on = minimal(svc);
            boolean was = minMode;
            minMode = on;
            note("极简模式 " + (on ? "开" : "关") + "（" + why + "，原=" + (was ? "开" : "关") + "）");
            if (on) {
                state = ACTIVE;
                ui.removeCallbacks(goDim);
                ui.removeCallbacks(goEdge);
                setFigBox(dp(EDGE_W_DP), dp(EDGE_H_DP));
                // ★88：极简不用气泡窗；morph 层从白条态开始
                if (bubAdded) hideBubWin();
                if (morphState != 0) { morphState = 0; morphRemove(); }
                setEdgeBarSide();
                if (bubble != null) bubble.setTextSize(10.3f);   // ★87：字号同步缩到 ≈89.4%
                barVisual("极简开");
                if (img != null) img.setVisibility(View.GONE);
                if (face != null) face.setVisibility(View.GONE);
                ensure();
                layOutChildren("极简开");
            } else {
                // ★88 切回常规：大窗必须摘掉，气泡窗接管
                if (morphState != 0) { morphState = 0; morphRemove(); }
                barVisual("极简关");
                if (bubble != null) bubble.setTextSize(11.5f);
                setFigBox(dp(FIG_DP), dp(FIG_DP));
                showFig();
                ensure();
                layOutChildren("极简关");
                scheduleIdle();
            }
            applyAlpha();
        } catch (Throwable t) { noteFail("applyMinimal", t); }
    }

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
                if (m.morphState == 1) m.morphSettle(m.morph.progress() > 0.5f, "转屏");
                DisplayMetrics dm = m.svc.getResources().getDisplayMetrics();
                if (dm != null) { m.sw = dm.widthPixels; m.sh = dm.heightPixels; }
                int fy = Math.round(m.fracPref() * Math.max(0, m.sh - m.figH));   // ★89：同口径
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
            edgeBar = new Bar(svc);                    // ★89 白条自己画（粗细/长短可变，窗口一动不动）
            edgeBar.setVisibility(View.GONE);
            figWin.addView(edgeBar, edgeBarParams());
            barLayerSetup();                           // ★89 拖动层（整屏、不可触摸）
            figWin.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent e) { return onFigTouch(e); }
            });
            // 三版④：先给 LayoutParams 再调尺寸（65 的 D3 空指针就出在这里）
            figWin.setLayoutParams(new FrameLayout.LayoutParams(dp(FIG_DP), dp(FIG_DP)));

            figLp = baseLp();
            figW = dp(FIG_DP);
            figH = dp(FIG_DP);
            minMode = minimal(svc);                       // ★87：极简模式初始就是白条尺寸
            if (minMode) { figW = dp(EDGE_W_DP); figH = dp(EDGE_H_DP); }
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
            bubbleWrap.setVerticalScrollBarEnabled(false);   // ★89.9 整改三：只在真能滚时开（见 measureBubble）
            bubbleWrap.setScrollbarFadingEnabled(true);
            try { int bw0 = bubbleWrap.getVerticalScrollbarWidth(); if (bw0 > 0) bubbleWrap.setScrollBarSize(Math.max(2, Math.round(bw0 / 3f))); } catch (Throwable t) { }
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
                    int act = e.getActionMasked();
                    if (act == MotionEvent.ACTION_DOWN) {
                        bubDragX0 = e.getRawX(); bubDragY0 = e.getRawY();
                        note("bubble touch"); if (state != ACTIVE) setState(ACTIVE, "碰了气泡"); scheduleIdle();
                    } else if (act == MotionEvent.ACTION_MOVE) {
                        // ★87：按住气泡往最近的屏幕边缘横着滑 ⇒ 收起（只有"要滚"时窗口才收得到触摸）
                        float dx = e.getRawX() - bubDragX0, dy = e.getRawY() - bubDragY0;
                        if (Math.abs(dx) > dp(60) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                            note("气泡拖动收起（往" + (dx > 0 ? "右" : "左") + "滑 " + Math.round(dx) + "px）");
                            collapseBubbleByGesture();
                            return true;
                        }
                    }
                    return false;   // 不吃事件：气泡里照样滚文字
                }
            });
            bubWin.setLayoutParams(new FrameLayout.LayoutParams(dp(120), dp(40)));
            bubLp = baseLp();

            // ★88：极简的自绘大窗（不接收触摸，开播前定尺寸）
            morphSetup();

            // 初始位置（之后由 layOutChildren 维护；★运行期只有拖动/换边/转屏/换状态才改）
            int fy = Math.round(fracPref() * Math.max(0, sh - figH));   // ★89：frac＝沿边比例（0~1，按可用高算）
            if (fy < 0) fy = 0;
            if (fy + figH > sh) fy = Math.max(0, sh - figH);
            figLp.y = fy;
            figLp.x = isRightSide() ? Math.max(0, sw - figW) : 0;

            loadImage();
            applyMirror();
            layOutChildren("setup");
            try { applyTrusted(); } catch (Throwable t) { noteFail("trusted", t); }
            applyAlpha();
            applyMinimal("setup");
            barVisual("setup 完");
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
            if (figLp != null) mm.invoke(figLp, Boolean.TRUE);
            if (bubLp != null) mm.invoke(bubLp, Boolean.TRUE);
            // ★89.6：气泡窗也要 trusted —— 不然真机触摸根本不进气泡（"能上下滚"永远验不出来）
            if (morphLp != null) mm.invoke(morphLp, Boolean.TRUE);
            r = "trustedOverlay=ok(api" + Build.VERSION.SDK_INT + "，morph=" + (morphLp != null) + ")";
        } catch (Throwable t) {
            r = "trustedOverlay=NO(" + t.getClass().getSimpleName() + "：" + t.getMessage() + "／api" + Build.VERSION.SDK_INT + ")";
        }
        note(r);
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
        if (minMode) return;   // ★87：极简的白条常驻，不降档
        ui.removeCallbacks(goDim);
        ui.removeCallbacks(goEdge);
        if (state == ACTIVE) ui.postDelayed(goDim, T_DIM);
    }

    private void stepDown() {
        if (minMode) return;   // ★87：极简不降档
        if (state == ACTIVE) { setState(DIMMED, "10 秒无指令无点击"); ui.postDelayed(goEdge, T_EDGE); return; }
        if (state == DIMMED) {
            // 长时限消息（汇报 30 秒／失败 60 秒）还挂着：EDGE 等它收场，但每 3 秒只让一步（绝不永久等）
            if (bubShown() && (msgKind == M_REPORT || msgKind == M_FAIL)) { ui.postDelayed(goEdge, 3000L); return; }
            setState(EDGE, "再 10 秒无动静");
        }
    }

    private void setState(int st, String why) {
        if (minMode) { state = ACTIVE; ui.removeCallbacks(goDim); ui.removeCallbacks(goEdge); return; }   // ★87：极简不进三态
        if (state == st) { ui.postDelayed(verifyBoxR, VERIFY_MS); return; }   // ★70 C 线：同态也校一次（竞态正是「状态没变、尺寸错了」）
        ui.postDelayed(verifyBoxR, VERIFY_MS);   // ★70 C 线：三态切换后自校一次窗口尺寸
        int old = state;
        state = st;
        note("state " + STATE_NAME[old] + " -> " + STATE_NAME[st] + "（" + why + "）");
        if (st == ACTIVE) {
            barVisual("回前台态");
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
        if (minMode) { note("极简模式：不进退边（白条本来就在）"); return; }
        if (expanding) {   // 展开动画被贴边打断：丢弃排队消息，别让回调在半路再冒泡
            expanding = false;
            queuedSay = null;
            queuedKind = -1;
            note("展开动画被贴边打断，丢弃排队消息");
        }
        if (bubShown()) hideBubble("贴边");
        barVisual("进贴边");
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
            if (minMode) {   // ★87：极简的窗口常驻白条尺寸
                int mw = dp(EDGE_W_DP), mh = dp(EDGE_H_DP);
                if (figW != mw || figH != mh) {
                    note("自校（极简）：win=" + figW + "x" + figH + " → 纠正为 " + mw + "x" + mh);
                    setFigBox(mw, mh);
                    placeFig();
                }
                return;
            }
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
            if (minMode) {   // ★88/89：极简的白条＝自己画的一条，粗细/颜色跟着 morph（收放最后一帧接得上，不闪）
                barStyle(edgeBar);
            } else {
                edgeBar.setSide(isRightSide());
                edgeBar.setMetrics(dpf(EDGE_BAR_W_DP), dpf(EDGE_BAR_H_DP));
                edgeBar.setStyle(0x99FFFFFF, 0x40000000, dp(1));
                edgeBar.setCornerPx(dpf(2f));   // ★89.6 整改五：常规模式（有脸）恢复 2dp 圆角，不再画成半圆端头
            }
        } catch (Throwable t) { }
    }
    private FrameLayout.LayoutParams edgeBarParams() {
        // ★89：宽度铺满窄窗（20dp），粗细由 Bar 自己画 ⇒ 挤压加粗不会被窗口裁掉
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpf(EDGE_BAR_H_DP + 12f));
        p.gravity = Gravity.CENTER_VERTICAL;
        return p;
    }

    /** 65：图形（自带脸／自选图）显示与收起——EDGE 态只留细线。 */
    private void showFig() {
        try {
            if (minMode) {   // ★87：极简＝没有脸，白条常驻
                if (img != null) img.setVisibility(View.GONE);
                if (face != null) face.setVisibility(View.GONE);
                barVisual("showFig 极简");
                setEdgeBarSide();
                return;
            }
            barVisual("回前台态");
            boolean hasImg = img != null && img.getDrawable() != null;
            if (img != null) img.setVisibility(hasImg ? View.VISIBLE : View.GONE);
            if (face != null) face.setVisibility(hasImg ? View.GONE : View.VISIBLE);
        } catch (Throwable t) { }
    }

    private void hideFig() {
        try {
            if (minMode) {   // ★87：极简的白条常驻——收脸不收条
                barVisual("hideFig 极简");
                return;
            }
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
            final String zh = Words.toolZh(name);
            final String mline = "shell".equals(name) ? ToolTags.line(name, a) : ((zh == null || zh.length() == 0) ? name : name + " " + zh);   // ★89.9 shell 说人话
            final boolean timesUp = resp != null && (resp.indexOf("timeout=1") >= 0 || resp.indexOf("超时") >= 0);
            final boolean isNotify = name.equals("notify");
            String nRaw = a.optString("content", "");
            if (nRaw == null || nRaw.length() == 0) nRaw = rep;
            if (nRaw == null) nRaw = "";
            final String nText = nRaw.replace('\n', ' ').trim();   // ★88.5：notify 第一行说人话（content／report）

            m.ui.post(new Runnable() {
                public void run() {
                    if (m.minMode) {
                        // ★87 极简：只有「工具名 中文名」＋三种状态，不跟命令、不跟退出码、不跟返回内容
                        String st = bad ? (timesUp ? Words.MS_MIN_TIMEOUT : Words.MS_MIN_FAIL) : "";   // ★89.9：成功只剩第一行（不显示"已返回"）
                        boolean quiet = System.currentTimeMillis() < m.quietUntil;
                        if (quiet && !bad && !isNotify) { m.note("静默期不冒泡（极简）：" + name); return; }
                        // ★88.5：notify 是助手对用户说话的通道 ⇒ 第一行就是 content（没 content 用 report），
                        //         成功不再补「已返回」；失败／超时仍显示「失败」「超时失败」。
                        if (isNotify) {   // ★89.9：notify 仍单行显示 content 原文，不参与叠加、不过名单闸门
                            String head = nText.length() > 0 ? nText : mline;
                            m.noStack = true;
                            try {
                                if (bad) m.say(head + "\n" + st, M_FAIL, false);
                                else m.say(head, M_NORMAL, false);
                            } finally { m.noStack = false; }
                            return;
                        }
                        m.say(bad ? mline + "\n" + st : mline, bad ? M_FAIL : M_NORMAL, false);
                        return;
                    }

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
        // ★89.9 整改五：自动冒泡（普通／失败／超时）**只走一个闸门** allowedByList（现读前台包，不缓存）；
        //   手拉（内边滑拉最近一条）与 notify 例外。名单命中 ⇒ 不冒（失败也不冒，坏消息仍在大通知里）＋收掉已有内容。
        String whySay = kind == M_FAIL ? "失败" : (kind == M_REPORT ? "汇报" : "工具");
        if (!force && !noStack) {
            if (!allowedByList(whySay)) { dropShownForList(whySay); note("名单闸门 ⇒ 不冒（" + whySay + "）：" + clip(text, 24)); return; }
        }
        if (!visible(force)) { note("闭嘴不冒（" + whySay + "）：" + clip(text, 24)); return; }
        ensure();
        if (minMode) { morphSay(text, kind); return; }   // ★88 极简：一个元素从白条长成框
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
        if (minMode && text.indexOf('\n') > 0) {   // ★87：极简两行（工具名＋状态）不许被 clean 压成一行
            String[] parts = text.split("\n");
            StringBuilder sb2 = new StringBuilder();
            for (int i = 0; i < parts.length; i++) { if (i > 0) sb2.append('\n'); sb2.append(ToolTags.clean(parts[i])); }
            show = sb2.toString();
        }
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
        return msgKind >= 0 && !minMode;   // ★88：极简由 morph 层画，气泡窗永不参与
    }

    private void hideBubble(String why) {
        stackPrev = null; stackAt = 0L;   // ★89.9：气泡收场 ⇒ 下一条不叠加
        if (minMode) {   // ★88 极简：气泡是 morph 画出来的，收起＝反向变形（框→白条）
            boolean was0 = msgKind >= 0;
            msgKind = -1;
            pendingTool = null;
            ui.removeCallbacks(hideMsg);
            ui.removeCallbacks(hideBadgeTask);
            morphHide(why);
            if (was0) note("bubble hide（" + why + "）");
            return;
        }
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
        if (minMode) {
            // ★90：进名单只改一次可见性——先切「无」，再收内容，最后摘窗
            note("极简 · 名单命中 ⇒ 全隐（白条与框都不出现）：" + pk);
            barFrozen = true;
            barVisual("名单命中·先切无");
            dropShownForList(pk);
            if (figAdded) takeAway();
            barFrozen = false;
            barVisual("名单命中全隐");
            return;
        }
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
            if (minMode) {
                // ★90：出名单只改一次可见性——先建窄窗（条先暗着），下一帧再点亮
                barFrozen = true;
                ensure();
                if (img != null) img.setVisibility(View.GONE);
                if (face != null) face.setVisibility(View.GONE);
                barVisual("离开名单·先建窗");
                ui.post(new Runnable() { public void run() {
                    if (dragAdded || flying || !figAdded) { barFrozen = false; return; }
                    barFrozen = false;
                    barVisual("离开名单·白条显示");
                }});
                note("极简 · 离开名单 ⇒ 白条恢复（" + why + "）");
                return;
            }
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
                    m.dropShownForList(pk);
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
            if (self && !force && !minMode) return false;                  // 1) Reticle 自己在前台（★89.5：极简照样冒，用户才能看到每个工具）
            // ★89.9 整改五：名单判定不在这里（冒泡前只过 allowedByList 一个闸门；这里只留"不在名单了要解除让位"）
            boolean named = !self && inHideList(pk);
            if (!named && cliHide) { String old = cliPkg; cliHide = false; cliPkg = null; note("离开客户端：" + old + " → 解除让位"); clearClientHide("离开客户端"); }
            PowerManager pm = (PowerManager) svc.getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) return false;            // 3) 息屏
            KeyguardManager km = (KeyguardManager) svc.getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null && km.isKeyguardLocked()) return false;          //    锁屏
            return true;
        } catch (Throwable t) { return true; }
    }

    // ───────────────── ★89.9 整改五：名单闸门（一个闸门、现读前台包） ─────────────────

    /** 前台包**当场读**（不走缓存）：读不到／为空 ⇒ null。 */
    private String fgPkgFresh() {
        try {
            android.view.accessibility.AccessibilityNodeInfo r = svc.getRootInActiveWindow();
            CharSequence p = r == null ? null : r.getPackageName();
            String pk = p == null ? null : p.toString();
            return (pk != null && pk.length() > 0) ? pk : null;
        } catch (Throwable t) { return null; }
    }

    /** ★89.9 整改五：冒泡的唯一闸门。读不到／为空 ⇒ 按「在名单里」处理（保守：宁可不冒）。 */
    private boolean allowedByList(String why) {
        String pk = fgPkgFresh();
        if (pk == null) { note("名单闸门：前台=读不到 名单命中=是（保守） ⇒ 不冒（" + why + "）"); return false; }
        boolean self = pk.startsWith(svc.getPackageName());
        boolean named = !self && inHideList(pk);
        boolean allow = !named;
        note("名单闸门：前台=" + pk + " 名单命中=" + (named ? "是" : "否") + " ⇒ " + (allow ? "冒" : "不冒") + "（" + why + "）");
        return allow;
    }

    /** ★89.9 整改五.3：名单命中 ⇒ 把已在显示的内容直接收掉（不许"切进去了还留着上一条"）。 */
    private void dropShownForList(String why) {
        try {
            if (!minMode) { if (msgKind >= 0 || bubAdded) hideBubble("名单命中：" + why); return; }
            boolean was = msgKind >= 0 || morphAdded;
            msgKind = -1;
            pendingTool = null;
            ui.removeCallbacks(hideMsg);
            if (morphAnim != null) { try { morphAnim.cancel(); } catch (Throwable t) { } morphAnim = null; }
            morphSeq++;
            morphState = 0;
            if (morph != null) morph.setP(0f);
            morphRemove();
            stackPrev = null; stackAt = 0L;
            if (was) note("名单命中 ⇒ 已在显示的内容直接收掉（" + why + "）");
        } catch (Throwable t) { noteFail("dropShownForList", t); }
    }

    // ───────────────── ★89.9 整改一：白条视觉互斥（唯一开关） ─────────────────

    private static String bvName(int v) {
        if (v == BV_BAR) return "窄窗";
        if (v == BV_MORPH) return "morph";
        if (v == BV_DRAG) return "拖动";
        if (v == BV_FLY) return "飞行";
        return "无";
    }

    private int bvWant() {
        if (flying) return BV_FLY;                                  // 飞行那一秒：只有飞行层
        if (dragAdded) return BV_DRAG;                              // 拖动层里的条
        if (morphAdded) return BV_MORPH;                            // morph 里的贴边条
        if (!barFrozen && figAdded && (minMode || state == EDGE)) return BV_BAR;   // 窄窗白条
        return BV_NONE;
    }

    /** 白条视觉的唯一开关：窄窗／morph／拖动／飞行四层互斥；切换中（barFrozen）一律按"无"。
     *  每次调用记一行 `白条视觉=…`；数到两个就当场掐掉多余，并记断言失败。 */
    private void barVisual(String why) {
        try {
            int want = bvWant();
            if (edgeBar != null) {
                int vis;
                if (want == BV_BAR) vis = View.VISIBLE;
                else if (want == BV_NONE && !figAdded) vis = View.GONE;
                else vis = View.INVISIBLE;      // 留着占位，回头点亮不重新测量
                edgeBar.setVisibility(vis);
            }
            if (dragBar != null && dragAdded) dragBar.setVisibility(View.VISIBLE);
            int n = (want == BV_BAR ? 1 : 0) + (morphAdded ? 1 : 0)
                    + (flying ? 1 : 0) + ((dragAdded && !flying) ? 1 : 0);
            if (n > 1) {
                if (edgeBar != null && want != BV_BAR) edgeBar.setVisibility(View.INVISIBLE);
                if (morphAdded && want != BV_MORPH && morphWin != null && wm != null) {
                    try { wm.removeView(morphWin); } catch (Throwable t) { }
                    morphAdded = false; morphState = 0; morphSeq++;
                }
                note("白条视觉断言失败（" + n + " 个）⇒ 已掐掉多余：want=" + bvName(want) + "（" + why + "）");
                n = (want == BV_BAR ? 1 : 0) + (morphAdded ? 1 : 0)
                        + (flying ? 1 : 0) + ((dragAdded && !flying) ? 1 : 0);
            }
            bvLast = want;
            note("白条视觉=" + bvName(want) + "（" + n + " 个；" + why + "）");
        } catch (Throwable t) { noteFail("barVisual", t); }
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
            applyBubTouch();   // ★87：内容不超出 ⇒ 气泡窗不接收触摸（不挡手）
        } catch (Throwable t) { noteFail("layout", t); }
    }

    /** 气泡尺寸：★只包住内容——角标放**文字上方**（不占右侧宽度、不撑宽气泡），收起就把高度还回去。
     *  横向优先＝形象旁边横着一条；纵向优先＝贴屏幕侧边、在形象上方。超出上限交给 ScrollView 滚动（手滑）。 */
    private int[] measureBubble() {
        boolean vert = vertPref(svc);
        int maxW = vert ? Math.min((int) (sw * 0.36f), dp(200)) : Math.min(dp(170), (int) (sw * 0.45f));
        int maxH = vert ? (int) (sh * 0.20f) : (int) (sh * 0.14f);
        int padL = dp(10), padR = dp(10), padT = dp(7), padB = dp(7);
        if (minMode) {   // ★87 极简：气泡整体缩到常规的 ≈89.4%（宽×高面积 ≈80%）
            maxW = Math.round(maxW * 0.894f);
            maxH = Math.round(maxH * 0.894f);
            padL = dpf(8.9f); padR = dpf(8.9f); padT = dpf(6.3f); padB = dpf(6.3f);
        }
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
            bubNeedTouch = bubble.getMeasuredHeight() > th;   // ★87：内容高度 vs 窗口高度（不按字数猜）
            boolean scroll = bubNeedTouch;
            bubbleWrap.setVerticalScrollBarEnabled(bubNeedTouch && bubble.getMeasuredHeight() > th + dp(4));
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
    /** 79 号单：应用自己的提醒也要冒一次泡（此时 Reticle 在前台，普通 bubble 会被「自己在前台」挡掉）。 */
    public static void alert(final String text) {
        final Mascot m = S;
        if (m == null || text == null || text.length() == 0) return;
        m.ui.post(new Runnable() { public void run() { try { m.layOutChildren("应用内提醒"); m.say(text, M_NORMAL, true); } catch (Throwable t) { } } });
    }

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

    /** ★87：气泡挡不挡手——内容不超出可视高（不用滚）⇒ 整窗不接收触摸，手指落到底下的应用；
     *  内容超出（要能滚）⇒ 恢复可触摸。判据＝measureBubble 的「内容高 vs 窗口高」，不按字数猜。 */
    private void applyBubTouch() {
        if (bubWin == null || bubLp == null) return;

        try {
            boolean want = bubNeedTouch;   // true＝要触摸
            boolean has = (bubLp.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0;
            if (want == has && bubTouchInit) return;
            bubTouchInit = true;
            if (want) bubLp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            else bubLp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            if (bubAdded) wm.updateViewLayout(bubWin, bubLp);
            note("bub touch=" + (want ? "on（要滚・可触摸）" : "off（不挡手・整窗穿透）")
                    + " flags=0x" + Integer.toHexString(bubLp.flags) + " win=" + bubLp.width + "x" + bubLp.height);
        } catch (Throwable t) { noteFail("applyBubTouch", t); }
    }

    /** ★87：按住气泡往边滑（或点白条）⇒ 收起气泡，并静默 60 秒：
     *  普通工具调用不再弹、notify 照弹、失败照冒；期间白条照旧可点，安静期不出任何提示。 */
    private void collapseBubbleByGesture() {
        quietStart("收起（往边滑／点白条）");
    }

    /** ★88.6：所有「收起」入口（点白条／划框／点框）走同一套——收起 ＋ 进 60 秒静默：
     *  普通调用不弹、notify 照弹、失败照冒；静默期白条照旧可点。 */
    private void quietStart(String why) {
        try {
            hideBubble(why);
            quietUntil = System.currentTimeMillis() + 60000L;
            note("静默 60 秒开始（" + why + "）：普通调用不弹／notify 与失败照冒");
            ui.postDelayed(new Runnable() { public void run() { note("静默 60 秒结束，恢复正常冒泡"); } }, 60000L);
        } catch (Throwable t) { noteFail("quietStart", t); }
    }

    private void toastSelf(String s) { note("click " + s); }
    // ───────────────── ★88：气泡从白条长出来（极简 morph 层：一个元素两形态连续变形） ─────────────────

    private boolean morphShown() { return morphState == 1 || morphState == 2; }

    /** 大窗＝极简的自绘层。★88.5（用户拍 B）：框整窗可触摸——按住往边框滑＝跟着反向缩回，点一下＝收起；尺寸开播前一次定好。 */
    private void morphSetup() {
        try {
            morph = new Morph(svc);
            morphWin = new FrameLayout(svc);
            morphWin.addView(morph.view(), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            // ★89.5 整改六.3：框上叠一个真正的 TextView（放在 ScrollView 里）当内容层：可滚、随框走、随动画淡入
            morphText = new TextView(svc);
            morphText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
            morphText.setTextColor(Morph.textColor(svc));
            morphText.setLineSpacing(dpf(2f), 1f);
            morphScroll = new ScrollView(svc);
            morphScroll.setFillViewport(true);
            morphScroll.setVerticalScrollBarEnabled(true);
            morphScroll.setScrollbarFadingEnabled(true);   // ★89.9 整改三：停手约 1 秒淡出
            try {                                            // ★89.9 整改三：粗细＝系统默认的 1/3
                int sbW0 = morphScroll.getVerticalScrollbarWidth();
                if (sbW0 > 0) morphScroll.setScrollBarSize(Math.max(2, Math.round(sbW0 / 3f)));
                note("morph 滚动条：默认宽 " + sbW0 + "px ⇒ 设为 " + Math.max(2, Math.round(sbW0 / 3f)) + "px，停手淡出");
            } catch (Throwable t) { noteFail("scrollbar size", t); }
            morphScroll.addView(morphText, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            morphScroll.setVisibility(View.INVISIBLE);
            morphScroll.setEnabled(false);   // ★89.5 内容层不自己吃触摸（滚动由 Mascot 代做，触摸绝不漏给下层）
            morphScroll.setAlpha(0f);
            morphWin.addView(morphScroll, new FrameLayout.LayoutParams(dp(120), dp(60)));
            morph.setContent(morphScroll);
            morphWin.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent e) { return onMorphTouch(e); }
            });
            morphWin.setLayoutParams(new FrameLayout.LayoutParams(dp(120), dp(120)));
            morphLp = baseLp();
            note("morph ready（点＝收起／按住往边框滑＝跟着缩；★89.5 叠了 ScrollView+TextView 内容层，可滚）flags=0x" + Integer.toHexString(morphLp.flags));
            try { applyTrusted(); } catch (Throwable t) { noteFail("trusted-morph", t); }   // ★89.6：气泡窗 trusted ⇒ 能收触摸（滚动）
        } catch (Throwable t) { noteFail("morphSetup", t); }
    }

    /** ★89.5 整改六.3：内容层大小／位置跟着框（终态）走；文本用完整内容（能换行、能滚）。 */
    private void layoutContent() {
        try {
            if (morph == null || morphScroll == null || morphText == null) return;
            float padX = dpf(13), padY = dpf(9);
            int cw = Math.max(1, Math.round(morph.frameW - padX * 2f));
            int chh = Math.max(1, Math.round(morph.frameH - padY * 2f));
            int ml = isRightSide() ? Math.round(morph.winW - morph.frameW + padX) : Math.round(padX);
            int mt = Math.round(morph.winCy() - morph.frameH / 2f + padY);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(cw, chh);
            lp.leftMargin = ml; lp.topMargin = mt;
            morphScroll.setLayoutParams(lp);
            morphScroll.scrollTo(0, 0);
            morph.contentCy = morph.winCy();
            String t1 = ToolTags.clean(morph.l1), t2 = ToolTags.clean(morph.l2);
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(t1);
            sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    0, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (t2 != null && t2.length() > 0) {
                int at = sb.length();
                sb.append('\n');
                sb.append(t2);
                sb.setSpan(new android.text.style.AbsoluteSizeSpan(11, true),
                        at + 1, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            morphText.setText(sb);
            morphText.setTextColor(Morph.textColor(svc));
            morphText.measure(View.MeasureSpec.makeMeasureSpec(cw, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int needH = morphText.getMeasuredHeight();
            // ★89.9 整改三：只在真的能滚时才出现（内容高 > 可视高 + 4px），否则关掉
            boolean canScroll = needH > chh + dp(4);
            morphNeedScroll = canScroll;
            morphScroll.setVerticalScrollBarEnabled(canScroll);
            note("morph 内容层：TextView 可视 " + cw + "x" + chh + " at " + ml + "," + mt + "；内容高 " + needH
                    + "（+4px 判据 ⇒ " + (canScroll ? "可滚，滚动条开" : "不用滚，滚动条关") + "）；文本=" + clip(t1 + "／" + t2, 34));
        } catch (Throwable t) { noteFail("layoutContent", t); }
    }

    /** ★89.5 整改一：手指接管后停住／事件丢了 ⇒ 1.2 秒后强制收尾（不许停在中间态）。 */
    private Runnable morphStuck = null;
    private void armStuckWatch(final long seq) { armStuckWatch(seq, 1200L); }
    private void armStuckWatch(final long seq, final long ms) {
        disarmStuckWatch();
        morphStuck = new Runnable() { public void run() {
            if (seq != morphSeq) return;
            if (morphState != 1 && morphState != 3) return;
            boolean toBubble = morphState == 1 && morph != null && morph.progress() > 0.5f;
            morphSettle(toBubble, "手指接管后停住／事件丢了 1.2s");
        } };
        ui.postDelayed(morphStuck, ms);
    }
    private void disarmStuckWatch() {
        if (morphStuck != null) { ui.removeCallbacks(morphStuck); morphStuck = null; }
    }

    /** ★88.5：框上的手指——点＝收起；按住往边框滑＝实时反向缩回；上下划＝滚内容层。
     *  ★89.5：动画中途被按住 ⇒ 明确"跟手接管"，并挂 1.2 秒兜底看门狗（松手/事件丢都走终态）。 */
    private int mScrollEdge = -99999;
    /** ★89.6 整改三：滚到顶／底给一次边界反馈（轻震＋日志），证明判据和滚动都真在跑。 */
    private void scrollEdgeCheck() {
        try {
            int sy = morphScroll.getScrollY();
            int ch = morphScroll.getChildAt(0) == null ? 0 : morphScroll.getChildAt(0).getHeight();
            int maxY = Math.max(0, ch - morphScroll.getHeight());
            if (sy <= 0 || sy >= maxY) {
                if (mScrollEdge != sy) {
                    mScrollEdge = sy;
                    morphScroll.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                    note("morph 滚到" + (sy <= 0 ? "顶" : "底") + "（边界反馈：scrollY=" + sy + "／范围 0~" + maxY + "，内容高 " + ch + " 可视 " + morphScroll.getHeight() + "）");
                }
            } else mScrollEdge = -99999;
        } catch (Throwable t) { }
    }

    private boolean onMorphTouch(MotionEvent e) {
        try {
            int act = e.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN) {
                mTouchX = e.getRawX(); mTouchY = e.getRawY();
                mTouchP = morph.progress();
                mTouchDrag = false;
                mScrollDrag = false;
                mLastScrollY = e.getRawY();
                if (morphAnim != null) {   // 生长到一半被按住 ⇒ 先停帧（不许和手指抢）
                    morphSeq++;
                    try { morphAnim.cancel(); } catch (Throwable t) { }
                    morphAnim = null;
                    if (morphWatch != null) { ui.removeCallbacks(morphWatch); morphWatch = null; }
                    note("morph 动画被手指打断 ⇒ 跟手接管（p=" + Math.round(mTouchP * 100f) + "%，松手后统一走到终态）");
                }
                ui.removeCallbacks(hideMsg);
                armStuckWatch(morphSeq);
                note("morph touch down（p=" + Math.round(mTouchP * 100f) + "%）");
                return true;
            }
            if (act == MotionEvent.ACTION_MOVE) {
                float rawDx = e.getRawX() - mTouchX, rawDy = e.getRawY() - mTouchY;
                if (!mTouchDrag && !mScrollDrag && Math.abs(rawDy) > dp(10) && Math.abs(rawDy) > Math.abs(rawDx)) {
                    boolean canScroll = morphNeedScroll;   // ★89.6：layoutContent 实测「内容高 > 可视高」
                    if (canScroll && morphState == 2) {
                        mScrollDrag = true;
                        morphScroll.scrollBy(0, (int) (mLastScrollY - e.getRawY()));
                        mLastScrollY = e.getRawY();
                        note("morph 内容层跟着手指滚：scrollY=" + morphScroll.getScrollY() + "（触摸没漏给下层）");
                        scrollEdgeCheck();
                        return true;
                    }
                }
                if (mScrollDrag) {
                    morphScroll.scrollBy(0, (int) (mLastScrollY - e.getRawY()));
                    mLastScrollY = e.getRawY();
                    scrollEdgeCheck();
                    return true;
                }
                float dx = isRightSide() ? rawDx : -rawDx;
                float dy = Math.abs(rawDy);
                float gap = Math.max(dp(24), morph.frameW - morph.barW);
                if (!mTouchDrag && dx > dp(6) && dx > dy) mTouchDrag = true;
                if (mTouchDrag) {
                    morphState = 1;
                    morph.setP(mTouchP - dx / gap);
                    note("morph 跟手缩 t=" + Math.round(morph.progress() * 100f) + "%（往" + (isRightSide() ? "右" : "左") + "滑 " + Math.round(dx) + "px）");
                }
                return true;
            }
            if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                disarmStuckWatch();
            gMode = G_NONE;   // ★89.5：强制收尾时手势状态一起复位（不许后续 MOVE 再改 p）
                note("morph touch up（" + (mScrollDrag ? "滚内容" : (mTouchDrag ? "划框" : "点框")) + " ⇒ 收起）");
                quietStart(mScrollDrag ? "滚完内容收起" : (mTouchDrag ? "划框收起" : "点框收起"));
                return true;
            }
        } catch (Throwable t) { noteFail("onMorphTouch", t); }
        return true;
    }
    private void morphAdd(int[] r) {
        if (morph == null || morphWin == null || wm == null) return;
        morphLp.x = r[0]; morphLp.y = r[1]; morphLp.width = r[2]; morphLp.height = r[3];
        try {
            if (!morphAdded) { wm.addView(morphWin, morphLp); morphAdded = true; }
            else wm.updateViewLayout(morphWin, morphLp);
            layoutContent();   // ★89.5：内容层（TextView）跟着框的位置／大小／文字走
            note("morph win=" + r[2] + "x" + r[3] + " at " + r[0] + "," + r[1] + "（提前定好，全程不改；框 " + morph.frameW + "x" + morph.frameH + " 行=" + morph.lineCount + "）");
        } catch (Throwable t) { noteFail("morphAdd", t); }
    }
    private void morphRemove() {
        try { if (morphAdded && morphWin != null && wm != null) wm.removeView(morphWin); } catch (Throwable t) { }
        morphAdded = false;
        barVisual("morph 摘窗");
    }

    /** 极简下的冒泡：白条 → 框（首次）；框已在就只换字（不重播、不动窗口）。
     *  ★89.9 整改二：1.5 秒内来的第二条与上一条**并列两行**（各带自己的状态），只留最近两条；
     *  超过 1.5 秒＝照旧替换；notify 不参与叠加（单行原文）。 */
    private void morphSay(String text, int kind) {
        if (morphWin == null || !isOn(svc)) return;
        if (kind == M_NORMAL && msgKind >= 0 && (msgKind == M_REPORT || msgKind == M_FAIL)) {   // ★89.5 六.1：改成照显（每个工具都要看到），只记一笔
            note("morph 顶掉上一条（" + (msgKind == 2 ? "失败" : "汇报") + "→ 普通）：" + clip(text, 24));
        }
        lastSayAt = System.currentTimeMillis();
        msgKind = kind;

        // ── 本条：正文一行；状态（失败／超时失败）接在**行末**，不占第二行 ──
        String raw = text == null ? "" : text;   // ★88.5：先按行拆、再逐行 clean（clean 会把换行压成空格）
        int nl = raw.indexOf('\n');
        String base = ToolTags.clean(nl >= 0 ? raw.substring(0, nl) : raw);
        String st = nl >= 0 ? ToolTags.clean(raw.substring(nl + 1)) : "";
        if (base.length() == 0) base = "Reticle";
        String cur = st.length() > 0 ? base + " " + st : base;

        // ── 叠加：1.5 秒内的第二条 ⇒ 上一行＋这一行（第三条起把最老的挤掉） ──
        long now2 = System.currentTimeMillis();
        String l1, l2;
        if (!noStack && stackPrev != null && stackAt > 0L && now2 - stackAt < STACK_MS) {
            l1 = stackPrev;
            l2 = cur;
            note("叠加两行（间隔 " + (now2 - stackAt) + "ms < " + STACK_MS + "ms）：" + clip(l1 + "／" + l2, 44));
        } else {
            l1 = cur;
            l2 = "";
        }
        if (noStack) { stackPrev = null; stackAt = 0L; }
        else { stackPrev = cur; stackAt = now2; }

        long ttl = kind == M_REPORT ? T_REPORT : (kind == M_FAIL ? T_FAIL : T_TALK);
        ui.removeCallbacks(hideMsg);
        ui.postDelayed(hideMsg, ttl);

        lastL1 = l1; lastL2 = l2; lastKind = kind;   // ★89：内滑拉出「最近一条」（自动消失过也留着）

        if (morphState == 2) {   // 已在框：换字，并按新内容重算矩形（框随内容大小）
            int[] r2 = morph.prepare(isRightSide(), sw, sh, figLp.y, figH, l1, l2, kind);
            morphAdd(r2);
            morph.setP(1f);
            barVisual("morph 换字");
            note("morph text update（已在框，按新内容重算）框 " + morph.frameW + "x" + morph.frameH + "px：" + clip(l1 + "／" + l2, 40));
            return;
        }
        int[] r = morph.prepare(isRightSide(), sw, sh, figLp.y, figH, l1, l2, kind);
        // ★89.9 整改一：进 morph 前**先藏窄窗白条**（不许重叠一帧，屏幕上任何时候只有一个白条视觉）
        if (edgeBar != null) edgeBar.setVisibility(View.INVISIBLE);
        morphAdd(r);
        barVisual("morph 开播");
        note("morph prepare 框=" + morph.frameW + "x" + morph.frameH + "px maxW=" + Morph.maxWdp(svc) + "dp maxH=" + Morph.maxHdp(svc) + "dp 窗=" + r[2] + "x" + r[3] + " 行=" + morph.lineCount);
        if (morphState == 0) morph.setP(0f);   // ★88：从当前进度接着长（收起中途又冒泡不跳回白条）
        morphState = 1;
        morphAnim(true, new Runnable() { public void run() {
            morphState = 2;
            barVisual("morph 成框");
            note("morph grow end（框 " + morph.frameW + "x" + morph.frameH + "px，白条 " + Math.round(morph.barW) + "x" + Math.round(morph.barH) + "）");
        } });
        note("morph show kind=" + kind + " ttl=" + ttl + " text=" + clip(text, 40));
    }
    /** 形变动画的唯一通道（grow／shrink）。
     *  ★89.7 整改三（19 号第三轮，用户已授权）：**彻底抛掉尺寸过冲**——
     *   p 沿一条 easeOutCubic 单调走（grow 0→1、shrink →0），从不越过终态；出手快、收尾慢，看着"不硬"但不抽。
     *   白条自身的"变细变粗"保留：flick 按 sin(πt) 走（两端都是 0），只画在厚度上，框上不做。
     */
    private void morphAnim(final boolean grow, final Runnable end) {
        try {
            final long seq = ++morphSeq;   // ★88：先作废旧回调用（cancel 会触发旧动画的 onAnimationEnd）
            if (morphAnim != null) { try { morphAnim.cancel(); } catch (Throwable t) { } morphAnim = null; }
            final int dur = grow ? Math.max(Morph.dur(svc), MORPH_GROW_MS) : Morph.dur(svc);
            armMorphWatch(seq, grow, dur);
            if (!Morph.enabled(svc)) {
                morph.setP(grow ? 1f : 0f);
                morph.setFlick(0f);
                note("morph " + (grow ? "grow" : "shrink") + " 跳过（动效关）");
                if (morphWatch != null) { ui.removeCallbacks(morphWatch); morphWatch = null; }
                if (end != null) end.run();
                return;
            }
            final StringBuilder trace = new StringBuilder(8192);
            final long[] ts = new long[640];
            final int[] tn = {0};
            final int[] lines = {0};
            final float p0 = morph.progress();
            final float[] lastW = {-1f};
            final float[] dwMax = {0f};
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(dur);
            va.setInterpolator(new android.view.animation.LinearInterpolator());   // 曲线自己算（easeOutCubic，无过冲）
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) {
                    if (seq != morphSeq) return;
                    float t = (Float) a.getAnimatedValue();
                    float v, fl;
                    float k = easeOutCubic(t);                       // ★89.7：删掉全部尺寸过冲，只留一条减速曲线
                    if (grow) {
                        v = p0 + (1f - p0) * k;                       // 单调爬到 1，从不越过终态
                        fl = -0.20f * (float) Math.sin(Math.PI * t);  // 变细只做在白条厚度上，两端为 0（收尾不跳）
                    } else {
                        v = p0 * (1f - k);                            // 单调收回 0，从不越过
                        fl = 0.12f * (float) Math.sin(Math.PI * t);   // 变粗也只做在白条厚度上
                    }
                    morph.setP(v);
                    morph.setFlick(fl);
                    float wNow = morph.curW;
                    float d = (lastW[0] > 0f) ? Math.abs(wNow - lastW[0]) : 0f;
                    if (d > dwMax[0]) dwMax[0] = d;
                    lastW[0] = wNow;
                    if (tn[0] < 640) ts[tn[0]++] = System.nanoTime();
                    if (lines[0] < 180) {
                        lines[0]++;
                        trace.append("morph ").append(grow ? "grow" : "shrink")
                                .append(" t=").append(Math.round(t * 100f)).append("%")
                                .append(" p=").append(Math.round(morph.progress() * 100f)).append("%")
                                .append(" 厚=").append(fmt1(morph.barW * (1f + morph.flick()) / dens())).append("dp")
                                .append(" 帧=").append(Math.round(morph.curW)).append("x").append(Math.round(morph.curH))
                                .append(" 帧差=").append(Math.round(d)).append("px")
                                .append(" win=").append(morphLp.width).append("x").append(morphLp.height)
                                .append(" ").append(morph.outline()).append("\n");
                    }
                }
            });
            va.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    if (seq != morphSeq) return;
                    morphAnim = null;
                    if (morphWatch != null) { ui.removeCallbacks(morphWatch); morphWatch = null; }
                    morph.setFlick(0f);
                    flushTrace(trace);
                    frameStat(ts, tn[0]);
                    note("morph " + (grow ? "grow" : "shrink") + " end：p=" + Math.round(morph.progress() * 100f) + "% 帧="
                            + Math.round(morph.curW) + "x" + Math.round(morph.curH) + "（单调到终态，无过冲）"
                            + " 最大逐帧差=" + Math.round(dwMax[0]) + "px＝终态宽 " + Math.round(morph.frameW) + " 的 "
                            + fmt1(morph.frameW <= 0 ? 0f : dwMax[0] * 100f / morph.frameW) + "%");
                    if (end != null) end.run();
                }
            });
            morphAnim = va;
            va.start();
            note("morph " + (grow ? "grow" : "shrink") + " start（" + dur + "ms 线宽=" + Morph.lineW(svc) + "dp 圆角=" + Morph.radius(svc)
                    + "dp 最大宽=" + Morph.maxWdp(svc) + "dp 最大高=" + Morph.maxHdp(svc) + "dp 手感=" + Morph.presetName(svc)
                    + " 曲线=easeOutCubic（无过冲）" + "）");
        } catch (Throwable t) { noteFail("morphAnim", t); if (end != null) end.run(); }
    }

    /** ★89.7：无过冲的减速曲线（出手快、收尾慢，且永远不越过终态）。 */
    private float easeOutCubic(float x) {
        if (x <= 0f) return 0f;
        if (x >= 1f) return 1f;
        float u = 1f - x;
        return 1f - u * u * u;
    }

    /** ★89.6 主体曲线：x∈[0,1] 的「缓起 5% → 匀速 → 缓停 5%」（每帧位移最小，不跳不抖）。 */
    private float growBase(float x) {
        final float A = 0.05f, T1 = 0.10f, T2 = 0.86f;
        if (x <= 0f) return 0f;
        if (x >= 1f) return 1f;
        if (x < T1) { float k = x / T1; return A * k * k * (3f - 2f * k); }
        if (x > T2) { float k = (x - T2) / (1f - T2); return 0.95f + A * k * k * (3f - 2f * k); }
        return A + (0.95f - A) * (x - T1) / (T2 - T1);
    }

    /** ★89.6 半个正弦的平方：两端斜率都是 0（接得上，不产生拐点），中间 1。 */
    private float pulse(float t, float a, float b) {
        if (t <= a || t >= b) return 0f;
        return sq((t - a) / Math.max(0.0001f, b - a));
    }
    private float sq(float u) {
        float s = (float) Math.sin(Math.PI * u);
        return s * s;
    }

    /** ★89.5 整改一：动画最大存活（时长＋200ms）——到点强制收尾到终态。 */
    private void armMorphWatch(final long seq, final boolean grow, int dur) {
        if (morphWatch != null) { ui.removeCallbacks(morphWatch); morphWatch = null; }
        morphWatchSeq = seq;
        morphWatchGrow = grow;
        morphWatch = new Runnable() { public void run() {
            if (seq != morphSeq) return;
            if (morphState != 1 && morphState != 3) return;
            morphSettle(grow, "超时 " + (Morph.dur(svc) + MORPH_MAX_MS) + "ms");
        } };
        ui.postDelayed(morphWatch, dur + MORPH_MAX_MS);
    }

    /** ★89.5 整改一：任何打断都走到终态（要么成框，要么干净回到白条），绝不停在中间。 */
    private void morphSettle(boolean toBubble, String why) {
        try {
            if (morph == null) return;
            morphSeq++;                                  // 作废旧回调
            if (morphAnim != null) { try { morphAnim.cancel(); } catch (Throwable t) { } morphAnim = null; }
            if (morphWatch != null) { ui.removeCallbacks(morphWatch); morphWatch = null; }
            disarmStuckWatch();
            gMode = G_NONE;   // ★89.5：强制收尾时手势状态一起复位（不许后续 MOVE 再改 p）
            morph.setFlick(0f);
            if (toBubble) {
                morph.setP(1f);
                morphState = 2;
                note("morph 强制收尾（" + why + "）⇒ 终态＝框 p=100%");
            } else {
                morph.setP(0f);
                morphState = 0;
                morphRemove();
                barVisual("morph 强制收尾");
                note("morph 强制收尾（" + why + "）⇒ 终态＝白条 p=0%");
            }
        } catch (Throwable t) { noteFail("morphSettle", t); }
    }
    /** ★88.8 逐帧轨迹一次落盘（一次 open／write／close）。 */
    /** ★88.8 动画期间的帧时间（每帧一次 update 的时间戳）：均值／最差／超过 16.7ms 的帧数。 */
    private void frameStat(long[] ts, int n) {
        try {
            if (n < 3) return;
            long sum = 0, max = 0;
            int over = 0;
            for (int i = 1; i < n; i++) {
                long d = ts[i] - ts[i - 1];
                sum += d;
                if (d > max) max = d;
                if (d > 16700000L) over++;
            }
            int cnt = n - 1;
            note(String.format(java.util.Locale.ROOT, "morph frame stat 帧=%d 均值=%.1fms 最差=%.1fms 超16.7ms=%d", cnt, (sum / 1000000.0) / cnt, max / 1000000.0, over));
        } catch (Throwable t) { }
    }

    private void flushTrace(StringBuilder sb) {
        try { if (sb != null && sb.length() > 0) writeLog(sb.toString().trim()); } catch (Throwable t) { }
    }

    /** 极简下收起：反向变形（框 → 白条），演完摘大窗、把白条还给窄窗。 */
    /** 极简下收起：反向变形（框 → 白条），演完摘大窗、把白条还给窄窗。 */
    private void morphHide(String why) {
        if (morph == null || morphWin == null) return;
        if (morphState == 0) { if (morphAdded) morphRemove(); return; }
        if (morphState == 3 && morphAnim != null) return;   // ★89.5：被打断后 state 卡在 3（旧动画 end 作废）不许把收起挡死
        note("morph hide（" + why + "）");
        morphState = 3;
        morphAnim(false, new Runnable() { public void run() {
            morphState = 0;
            morphRemove();
            barVisual("morph 收起完");
            if (morphText != null) morphText.setText("");
        } });
    }
    /** 配置页「播一遍」：白条 → 框 → 白条，跑 2 个来回（不用重装）。 */
    static void morphPlay() {
        final Mascot m = S;
        if (m == null) return;
        m.ui.post(new Runnable() { public void run() {
            try {
                if (!m.minMode) { m.note("morph 播一遍：极简模式没开，先开极简"); return; }
                m.note("morph 播一遍 start（2 个来回）");
                m.morphDemo(2);
            } catch (Throwable t) { m.noteFail("morphPlay", t); }
        } });
    }

    private void morphDemo(final int rounds) {
        if (morph == null || !minMode) return;
        if (morphState == 0) {
            int[] r = morph.prepare(isRightSide(), sw, sh, figLp.y, figH, "动效演示 · 播一遍", "白条收放", M_NORMAL);
            morphAdd(r);
        note("morph prepare 框=" + morph.frameW + "x" + morph.frameH + "px maxW=" + Morph.maxWdp(svc) + "dp maxH=" + Morph.maxHdp(svc) + "dp 窗=" + r[2] + "x" + r[3]);
            // ★88.7：同 morphSay——大窗先接管白条，下一帧再把窄窗白条藏起来（不闪）
            final View eb2 = edgeBar;
            if (eb2 != null) {
                eb2.setVisibility(View.VISIBLE);
                ui.post(new Runnable() { public void run() {
                    try { if (eb2 == edgeBar && morphState != 0) eb2.setVisibility(View.INVISIBLE); } catch (Throwable t) { }
                } });
            }
        }
        if (morphState == 1) return;
        morphState = 1;
        morph.setP(0f);
        morphDemoGrow(rounds);
    }

    private void morphDemoGrow(final int rounds) {
        morphAnim(true, new Runnable() { public void run() {
            morphState = 2;
            morphAnim(false, new Runnable() { public void run() {
                morphState = 0;
                if (rounds > 1) { morphState = 1; morphDemoGrow(rounds - 1); }
                else { morphRemove(); note("morph 播一遍 end（2 个来回完）"); }
            } });
        } });
    }

    /** 调参板改完立刻生效：成框时按新线宽／圆角重算窗口并重画；白条态只记一笔（下次播放生效）。 */
    static void morphReload() {
        final Mascot m = S;
        if (m == null) return;
        m.ui.post(new Runnable() { public void run() {
            try {
                if (m.morph == null) return;
                m.setEdgeBarSide();   // 白条粗细跟着线宽（收放首尾一帧要接得上）
                if (m.morphState == 2) {
                    int[] r = m.morph.prepare(m.isRightSide(), m.sw, m.sh, m.figLp.y, m.figH, m.morph.l1, m.morph.l2, m.morph.kind);
                    m.morphAdd(r);
                    m.morph.setP(1f);
                    m.note("morph 调参生效（重画）win=" + r[2] + "x" + r[3]);
                } else {
                    m.note("morph 调参已存（下次播放生效）dur=" + Morph.dur(m.svc) + "ms 线宽=" + Morph.lineW(m.svc)
                            + "dp 圆角=" + Morph.radius(m.svc) + "dp 鼓出=" + Morph.bulge(m.svc) + "% 回弹=" + Morph.bounce(m.svc));
                }
            } catch (Throwable t) { m.noteFail("morphReload", t); }
        } });
    }


    // ───────────────────────── 触摸：点击／拖动（★气泡不参与碰撞） ─────────────────────────

    // ───────────────── ★89 白条的手（极简：点／内滑／长按／拖动） ─────────────────
    // 三条可调常量（只真机调得出来）：离开"点"的判定 BAR_SLOP_DP／内滑成形阈值 BAR_SWIPE_DP／长按时长 BAR_LONG_MS
    // 阻尼：实际位移 = Dmax·(1-e^(-d/Dmax))，Dmax=0.55·屏宽（★89.7 指数渐近：越往外越沉，但能拖到 40% 判定线）。

    private float dens() { try { return svc.getResources().getDisplayMetrics().density; } catch (Throwable t) { return 3f; } }
    static String fmt1(float v) { return String.format(java.util.Locale.ROOT, "%.1f", v); }
    static String fmt2(float v) { return String.format(java.util.Locale.ROOT, "%.2f", v); }
    static String fmt3(float v) { return String.format(java.util.Locale.ROOT, "%.3f", v); }

    /** ★89.5 整改四：慢拖离边上限 1cm（用屏幕真实 dpi 换算，不用 dp 猜）。 */
    private float cmPx() {
        try {
            DisplayMetrics dm = svc.getResources().getDisplayMetrics();
            float dpi = dm.xdpi > 0 ? dm.xdpi : 400f;
            return dpi / 2.54f * BAR_DETACH_CM;
        } catch (Throwable t) { return dpf(38f); }
    }

    /** 白条的粗细／颜色／贴边：一律跟 Morph 参数走（与框的白条态像素一致，收放最后一帧不闪）。 */
    private void barStyle(Bar b) {
        if (b == null) return;
        b.setMetrics(dpf(Math.max(1f, (float) Morph.lineW(svc))), dpf(Bar.LEN_DP));
        b.setStyle(Morph.fillColor(svc), Morph.strokeColor(svc), Math.max(1, dpf(Morph.strokeW(svc))));
        b.setCornerPx(-1f);                      // ★89.6：极简白条＝圆头（半径＝粗细/2）
        b.setSide(isRightSide());
    }

    /** 拖动层：整屏、不可触摸、只画一条白条。拖动开始时一次开够 ⇒ 拖动期间只改画的东西，不改窗口。 */
    private void barLayerSetup() {
        try {
            dragBar = new Bar(svc);
            dragBar.setFloating(true);
            dragWin = new FrameLayout(svc);
            dragWin.addView(dragBar, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            dragWin.setLayoutParams(new FrameLayout.LayoutParams(sw, sh));
            dragLp = baseLp();
            dragLp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;   // ★拖动层永不吃触摸
            dragLp.width = sw; dragLp.height = sh; dragLp.x = 0; dragLp.y = 0;
            note("bar 拖动层 ready（flags=0x" + Integer.toHexString(dragLp.flags) + "，整屏一次开够，拖动期间不改窗口）");
        } catch (Throwable t) { noteFail("barLayerSetup", t); }
    }

    /** 点一下（框不在屏）⇒ 弹一下：从当前粗细冲到峰再回原样，140ms；不拉气泡。 */
    private void barTapBounce() {
        try {
            if (edgeBar == null) return;
            if (barAnim != null) { try { barAnim.cancel(); } catch (Throwable t) { } barAnim = null; }
            final long[] ts = new long[240];
            final int[] n = new int[] {0};
            final float peakT = (edgeBar.baseThick + dens() * Bar.BOOST_THICK_DP) / dens();
            final float peakL = (edgeBar.baseLen + dens() * Bar.BOOST_LEN_DP) / dens();
            ValueAnimator va = ValueAnimator.ofFloat(edgeBar.boost(), 1f, 0f);
            va.setDuration(140);
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) {
                    if (edgeBar != null) edgeBar.setBoost((Float) a.getAnimatedValue());
                    if (n[0] < ts.length) ts[n[0]++] = System.nanoTime();
                }
            });
            va.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    if (edgeBar != null) edgeBar.setBoost(0f);
                    barAnim = null;
                    note("bar 点白条 ⇒ 弹一下：厚 " + fmt1(edgeBar == null ? 0f : edgeBar.baseThick / dens()) + "dp → 峰 " + fmt1(peakT)
                            + "dp → 回原样；长 → 峰 " + fmt1(peakL) + "dp；用时=140ms；气泡没拉");
                    frameStatTag("bar frame stat（弹一下）", ts, n[0]);
                }
            });
            barAnim = va;
            va.start();
            note("bar tap bounce start（140ms，纯视觉，从当前 boost=" + fmt2(edgeBar.boost()) + " 起）");
        } catch (Throwable t) { noteFail("barTapBounce", t); }
    }

    /** 挤压／回弹／收回的唯一通道（boost 0~1）。★89.5：逐帧记厚度，证明单调、不跳变。 */
    private void barBoost(final float to, final int ms, final String tag) {
        try {
            if (edgeBar == null) return;
            if (barAnim != null) { try { barAnim.cancel(); } catch (Throwable t) { } barAnim = null; }
            if (barPressAnim != null) { try { barPressAnim.cancel(); } catch (Throwable t) { } barPressAnim = null; }
            final long[] ts = new long[240];
            final int[] n = new int[] {0};
            final StringBuilder fr = new StringBuilder(2048);
            final int[] fn = {0};
            final long t0 = System.nanoTime();
            float from = edgeBar.boost();
            if (Math.abs(to - from) < 0.02f) { edgeBar.setBoost(to); note(tag + "（已在该状态 boost=" + fmt1(to) + "）"); return; }
            ValueAnimator va = ValueAnimator.ofFloat(from, to);
            va.setDuration(ms);
            va.setInterpolator(new DecelerateInterpolator());
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) {
                    if (edgeBar != null) edgeBar.setBoost((Float) a.getAnimatedValue());
                    if (n[0] < ts.length) ts[n[0]++] = System.nanoTime();
                    if (fn[0] < 40 && edgeBar != null) {   // 逐帧：厚度／长度随时间（单调性从这里读）
                        fn[0]++;
                        fr.append("bar 逐帧 ms=").append((System.nanoTime() - t0) / 1000000L)
                                .append(" boost=").append(fmt2(edgeBar.boost()))
                                .append(" 厚=").append(fmt2(edgeBar.thickPx() / dens())).append("dp")
                                .append(" 长=").append(fmt2(edgeBar.lenPx() / dens())).append("dp")
                                .append(" 离边=").append(fmt2(edgeBar.liftPx() / dens())).append("dp\n");
                    }
                }
            });
            va.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    if (edgeBar != null) edgeBar.setBoost(to);
                    barAnim = null;
                    note(tag + "：厚 " + fmt1(edgeBar == null ? 0f : edgeBar.thickPx() / dens())
                            + "dp 长 " + fmt1(edgeBar == null ? 0f : edgeBar.lenPx() / dens()) + "dp 用时=" + ms + "ms");
                    flushTrace(fr);
                    frameStatTag("bar frame stat（挤压）", ts, n[0]);
                }
            });
            barAnim = va;
            va.start();
        } catch (Throwable t) { noteFail("barBoost", t); }
    }

    /** ★89.6 整改七：一条曲线到底——按下就开始 450ms **减速**曲线 boost 0→1（先快后慢、单峰单调下降、中途不换段）。
     *  长按 300ms 只解锁"可以拖"，不再额外加 boost（上一版到点又推一把＝"两座山峰"的根因）。 */
    private void barPressRamp() {
        try {
            if (edgeBar == null) return;
            if (barPressAnim != null) { try { barPressAnim.cancel(); } catch (Throwable t) { } barPressAnim = null; }
            final long[] ts = new long[240];
            final int[] n = {0};
            final StringBuilder fr = new StringBuilder(3072);
            final int[] fn = {0};
            final long t0 = System.nanoTime();
            final long[] lastMs = {0L};
            final float[] prevTh = {edgeBar.thickPx() / dens()};
            ValueAnimator va = ValueAnimator.ofFloat(edgeBar.boost(), 1f);
            va.setDuration(BAR_PRESS_MS);
            va.setInterpolator(new DecelerateInterpolator());   // speed ∝ 2(1−t)：单峰、单调下降
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) {
                    if (edgeBar == null) return;
                    edgeBar.setBoost((Float) a.getAnimatedValue());
                    if (n[0] < ts.length) ts[n[0]++] = System.nanoTime();
                    if (fn[0] < 40) {
                        fn[0]++;
                        long ms = (System.nanoTime() - t0) / 1000000L;
                        float th = edgeBar.thickPx() / dens();
                        float sp = (ms > lastMs[0]) ? (th - prevTh[0]) / (ms - lastMs[0]) : 0f;
                        prevTh[0] = th; lastMs[0] = ms;
                        fr.append("bar 按下逐帧 ms=").append(ms)
                                .append(" boost=").append(fmt2(edgeBar.boost()))
                                .append(" 厚=").append(fmt2(th)).append("dp")
                                .append(" 长=").append(fmt2(edgeBar.lenPx() / dens())).append("dp")
                                .append(" 速度=").append(fmt3(sp)).append("dp/ms\n");
                    }
                }
            });
            va.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    barPressAnim = null;
                    note("bar 按下 ⇒ 一路变粗到 " + fmt1(edgeBar == null ? 0f : edgeBar.thickPx() / dens())
                            + "dp（" + BAR_PRESS_MS + "ms 减速曲线，单峰单调下降，中途不换段；长按不再额外推）");
                    flushTrace(fr);
                }
            });
            barPressAnim = va;
            va.start();
        } catch (Throwable t) { noteFail("barPressRamp", t); }
    }

    private final Runnable barLongPressR = new Runnable() {
        public void run() {
            try {
                if (gMode != G_NONE || gMoved || morphState != 0) return;
                gLongFired = true;
                note("bar 长按 " + BAR_LONG_MS + "ms ⇒ 只解锁「可以拖」（boost 继续走按下那条曲线，当前=" + fmt2(edgeBar == null ? 0f : edgeBar.boost()) + "，不再额外加力）");
            } catch (Throwable t) { noteFail("barLongPress", t); }
        }
    };

    private final Choreographer.FrameCallback dragFrameCb = new Choreographer.FrameCallback() {
        public void doFrame(long ns) {
            try {
                if (gFrameN < gFrames.length) gFrames[gFrameN++] = ns;
                if (gMode == G_DRAG || dragAnim != null) Choreographer.getInstance().postFrameCallback(this);
            } catch (Throwable t) { }
        }
    };

    /** ★89.6 极简白条的手：点＝弹一下／收起；往屏内滑＝拉出气泡；长按＝一路变粗（单峰）；拖＝**第一帧就 1:1 跟手**。
     *  整改八（19 号第二轮）：8dp 只判「点还是拖」，不再当"开始动"的门槛——有位移就上拖动层，
     *  白条当场按偏移对齐；偏移在 DOWN 记下后整段手势绝不再改（长按达标／进拖动层／第一次 MOVE 都不改）。 */
    private boolean onBarTouch(MotionEvent e) {
        try {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    gMode = G_NONE; gMoved = false; gLongFired = false; gPreDrag = false;
                    gDownX = e.getRawX(); gDownY = e.getRawY();
                    gDownAt = System.currentTimeMillis(); gNoteAt = 0L;
                    gApplied = 0f; gFigY0 = figLp.y; gDbgLastD = -1f; gGainLast = 1f;
                    float bx = figLp.x + (edgeBar == null ? figW / 2f : edgeBar.centerX());
                    float by = figLp.y + figH / 2f;
                    gDownCx = bx; gDownCy = by;
                    gOffsetX = gDownX - bx;    // ★89.6：偏移＝手指−白条中心，整段手势里绝不再改
                    gOffsetY = gDownY - by;
                    if (gVt == null) gVt = VelocityTracker.obtain();
                    gVt.clear();
                    gVt.addMovement(e);
                    barPressRamp();
                    note("bar down 按下：手指(" + Math.round(gDownX) + "," + Math.round(gDownY) + ") 白条中心(" + Math.round(bx) + ","
                            + Math.round(by) + ") 偏移=(" + Math.round(gOffsetX) + "," + Math.round(gOffsetY) + ")（本段手势内不再改）");
                    ui.removeCallbacks(barLongPressR);
                    ui.postDelayed(barLongPressR, BAR_LONG_MS);
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (gVt != null) gVt.addMovement(e);
                    float dx = e.getRawX() - gDownX, dy = e.getRawY() - gDownY;
                    float ax = Math.abs(dx), ay = Math.abs(dy);
                    float inward = isRightSide() ? -dx : dx;
                    if (gMode == G_NONE) {
                        // ★89.6：有位移就上拖动层（例外：像"往屏内横划拉气泡"，等内滑判据，条不乱跳）
                        boolean maybeSwipe = inward > dpf(6f) && ax > ay;
                        if (!maybeSwipe && (ax > 1f || ay > 1f)) {
                            ui.removeCallbacks(barLongPressR);
                            if (barPressAnim != null) { try { barPressAnim.cancel(); } catch (Throwable t) { } barPressAnim = null; }
                            if (morphShown()) hideBubble("拖白条收起");
                            gPreDrag = true;
                            barDragBegin();
                            gMode = G_DRAG;
                            barDragMove(e);
                            note("bar 第一帧跟手：位移(" + Math.round(dx) + "," + Math.round(dy) + ") ⇒ 白条已按偏移对齐（无死区、无瞬移）");
                            return true;
                        }
                        int decided = barDecide(dx, dy);
                        if (decided == G_NONE) return true;
                        ui.removeCallbacks(barLongPressR);
                        if (barPressAnim != null) { try { barPressAnim.cancel(); } catch (Throwable t) { } barPressAnim = null; }
                        if (decided == G_SWIPE) { gMode = G_SWIPE; barSwipeBegin(); return true; }
                        if (morphShown()) hideBubble("拖白条收起");
                        gPreDrag = true;
                        barDragBegin();
                        gMode = G_DRAG;
                        barDragMove(e);
                        return true;
                    }
                    if (gMode == G_SWIPE) { barSwipeMove(e); return true; }
                    if (gMode == G_DRAG) {
                        if (gPreDrag && !gLongFired && morphState == 0 && inward > dpf(20f) && ax > ay * 1.2f) {
                            dragAbort();
                            gPreDrag = false;
                            gMode = G_SWIPE;
                            barSwipeBegin();
                            note("bar 拖动转内滑：横移 " + Math.round(inward) + "px ⇒ 拉气泡（拖动层已撤）");
                            return true;
                        }
                        if (gPreDrag && (ax > dpf(BAR_SLOP_DP) || ay > dpf(BAR_SLOP_DP))) gMoved = true;
                        barDragMove(e);
                        return true;
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    ui.removeCallbacks(barLongPressR);
                    if (gVt != null) gVt.addMovement(e);
                    int mode = gMode;
                    boolean pre = gPreDrag;
                    gMode = G_NONE;
                    gPreDrag = false;
                    if (mode == G_SWIPE) { barSwipeEnd(); releaseVt(); return true; }
                    if (mode == G_DRAG) {
                        if (pre && !gMoved) {   // 位移没超过 8dp ⇒ 还算「点」：撤拖动层，白条回边，走点的路
                            dragAbort();
                            note("bar 松手：位移 < " + fmt1(BAR_SLOP_DP) + "dp ⇒ 仍按「点」处理（拖动层已撤）");
                        } else { barDragEnd(e); releaseVt(); return true; }
                    }
                    if (gLongFired) {   // 长按后松手：回弹，位置不动
                        barBoost(0f, 120, "bar 长按松手 ⇒ 回弹（白条位置没动：" + figLp.x + "," + figLp.y + "）");
                        releaseVt();
                        return true;
                    }
                    if (barPressAnim != null) { try { barPressAnim.cancel(); } catch (Throwable t) { } barPressAnim = null; }
                    if (morphShown()) {   // 框在屏：点＝收起＋进 60 秒静默（88.9 口径不动）
                        barBoost(0f, 100, "bar 点白条 ⇒ 变粗收回（框开着）");
                        quietStart("点白条收起");
                        releaseVt();
                        return true;
                    }
                    String fpk = fgPkg();
                    if (fpk != null && !fpk.startsWith(svc.getPackageName()) && inHideList(fpk)) {
                        exemptPkg = fpk;
                        exemptUntil = System.currentTimeMillis() + EXEMPT_MS;
                        cliHide = false; cliPkg = null; failBreak = false;
                        note("门铃：点白条 ⇒ 本次前台豁免 " + (EXEMPT_MS / 1000L) + " 秒（" + fpk + "）");
                    }
                    barTapBounce();
                    releaseVt();
                    return true;
                }
                default: return false;
            }
        } catch (Throwable t) { noteFail("barTouch", t); return false; }
    }

    private void releaseVt() {
        try { if (gVt != null) { gVt.recycle(); gVt = null; } } catch (Throwable t) { }
    }

    /** ★89 三种手势的判定（可调）：纵向为主／横向朝外／已长按过 ⇒ 拖；近水平朝屏内 ⇒ 内滑拉气泡。
     *  判不清就返回 G_NONE（继续等手指给更明确的方向），绝不在 8dp 抖动上抢跑。
     *  ★89.5：死区只在这一个判定里；判定为拖的那一刻起按偏移 1:1 跟手（见 barDragMove）。 */
    private int barDecide(float dx, float dy) {
        float ax = Math.abs(dx), ay = Math.abs(dy);
        float slop = dpf(BAR_SLOP_DP);
        if (ax <= slop && ay <= slop) return G_NONE;
        gMoved = true;
        boolean right = isRightSide();
        float inward = right ? -dx : dx;
        if (gLongFired) return G_DRAG;                                   // 长按过＝明说要拖
        if (ay > ax * 0.9f) return G_DRAG;                                // 纵向为主／斜着 ⇒ 拖
        if (inward > dpf(20f) && morphState == 0) return G_SWIPE;         // 近水平朝屏内 ⇒ 拉气泡
        if (inward < -dpf(14f)) return G_DRAG;                            // 往屏外拽 ⇒ 拖（贴着边不动）
        return G_NONE;
    }
    // ── 内滑：从边框往屏幕内滑 ⇒ 把气泡拉出来（内容＝最近一条，跟着手走） ──
    private void barSwipeBegin() {
        try {
            if (morph == null || morphWin == null) { gMode = G_DRAG; barDragBegin(); return; }
            String l1 = lastL1.length() > 0 ? lastL1 : "还没有消息";
            String l2 = lastL2;
            int[] r = morph.prepare(isRightSide(), sw, sh, figLp.y, figH, l1, l2, lastKind);
            morphAdd(r);
            if (morphAnim != null) { try { morphAnim.cancel(); } catch (Throwable t) { } morphAnim = null; }
            morphSeq++;
            morph.setP(0f);
            morphState = 1;
            barVisual("内滑拉出");
            if (edgeBar != null) edgeBar.setBoost(0f);
            armStuckWatch(morphSeq, 2500L);   // ★89.5：内滑后手指停住／UP 丢了 ⇒ 2.5s 强制收尾
            note("bar swipe 开始（往屏内滑＝拉出最近一条）：" + clip(l1 + "／" + l2, 40) + " 窗=" + r[2] + "x" + r[3] + " 行=" + morph.lineCount);
        } catch (Throwable t) { noteFail("barSwipeBegin", t); }
    }

    private void barSwipeMove(MotionEvent e) {
        try {
            if (morph == null) return;
            float dx = e.getRawX() - gDownX;
            float inward = isRightSide() ? -dx : dx;
            float gap = Math.max(dpf(72f), Math.max(1f, morph.frameW - morph.barW));
            float p = (inward - dpf(10f)) / gap;
            if (p < 0f) p = 0f;
            if (p > 1f) p = 1f;
            morph.setP(p);
            long now = System.currentTimeMillis();
            if (now - gNoteAt > 120L) {
                gNoteAt = now;
                note("bar swipe 跟手：手指内滑 " + Math.round(Math.max(0f, inward)) + "px ⇒ p=" + Math.round(p * 100f) + "%（成形 55%）");
            }
        } catch (Throwable t) { noteFail("barSwipeMove", t); }
    }

    private void barSwipeEnd() {
        disarmStuckWatch();   // ★89.5
        try {
            if (morph == null) return;
            float p = morph.progress();
            if (p >= 0.55f) {
                morphState = 1;
                final int pw = Math.round(p * 100f);
                morphAnim(true, new Runnable() { public void run() {
                    morphState = 2;
                    note("bar swipe 成形：框 " + morph.frameW + "x" + morph.frameH + "px，内容＝最近一条（松手时 " + pw + "%）");
                }});
                note("bar swipe 松手 ≥55% ⇒ 直接成形");
            } else {
                morphState = 1;
                final int pw = Math.round(p * 100f);
                morphAnim(false, new Runnable() { public void run() {
                    morphState = 0;
                    morphRemove();
                    note("bar swipe 松手 <55%（" + pw + "%）⇒ 回弹归位，没有残留半开的框");
                }});
                note("bar swipe 松手 p=" + pw + "% <55% ⇒ 回弹归位");
            }
        } catch (Throwable t) { noteFail("barSwipeEnd", t); }
    }

    // ── 拖动：★89.6 按偏移 1:1 跟手（第一帧就对齐、无死区）＋ 离边 ≤1cm 硬阻尼（tanh）；松手 200ms 回弹／甩 350ms 飞对面 ──
    private void barDragBegin() {
        try {
            if (dragBar == null || dragWin == null) barLayerSetup();
            if (dragBar == null || dragWin == null) return;
            if (edgeBar != null) {
                dragBar.copyLookFrom(edgeBar);      // ★89.6：粗细／长短／颜色／圆角整套照抄 ⇒ 切层那一帧不跳
                dragBar.setBoost(edgeBar.boost());  // 接着按下的粗细走
            } else {
                barStyle(dragBar);
                dragBar.setBoost(0.35f);
            }
            gHalf0 = dragBar.thickPx() / 2f;        // 拖动期间固定半粗（boost 变化不吃掉跟手位移）
            dragBar.setCenter(gDownCx, gDownCy);    // 起点＝按下那一刻的白条中心
            gLastCy = gDownCy;
            gPrevFx = gDownX; gPrevCx = gDownCx;
            gApplied = 0f;
            dragLp.x = 0; dragLp.y = 0; dragLp.width = sw; dragLp.height = sh;
            barFrozen = false; flying = false;
            if (!dragAdded) { wm.addView(dragWin, dragLp); dragAdded = true; }
            barVisual("拖动开始");
            gFrameN = 0;
            Choreographer.getInstance().postFrameCallback(dragFrameCb);
            note("bar drag 拎起来：偏移=(" + Math.round(gOffsetX) + "," + Math.round(gOffsetY) + ")，起点中心(" + Math.round(gDownCx)
                    + "," + Math.round(gDownCy) + ") 半粗=" + fmt1(gHalf0) + "px（拖动层与白条同一套屏幕坐标，不换算）");
        } catch (Throwable t) { noteFail("barDragBegin", t); }
    }

    /** 撤销拖动层（"点"或转内滑时用）：白条还给窄窗，位置回边。 */
    private void dragAbort() {
        try {
            if (dragAnim != null) { try { dragAnim.cancel(); } catch (Throwable t) { } dragAnim = null; }
            if (dragAdded && dragWin != null && wm != null) { try { wm.removeView(dragWin); } catch (Throwable t) { } }
            dragAdded = false;
            flying = false; barFrozen = false;
            barVisual("拖动撤销");
        } catch (Throwable t) { noteFail("dragAbort", t); }
    }

    /** ★89.7 整改二：指数渐近阻尼 applied = Dmax·(1 − e^(−d/Dmax))，Dmax＝55% 屏宽。
     *  越往外越沉（跟手增益越来越小），但**能一路拖到判定线**；不再有 1cm 硬顶。 */
    private float barDamp(float d) {
        if (d <= 0f) return 0f;
        float Dmax = Math.max(1f, sw * BAR_DAMP_SW);
        return Dmax * (1f - (float) Math.exp(-d / Dmax));
    }

    private void barDragMove(MotionEvent e) {
        try {
            boolean right = isRightSide();
            float half = gHalf0;                     // 固定半粗（boost 变化不吃掉跟手位移）
            float fx = e.getRawX() - gOffsetX;       // ★89.6：白条中心＝手指−偏移（偏移整段手势不变）
            float fy = e.getRawY() - gOffsetY;
            float dWant = right ? (sw - half - fx) : (fx - half);
            float d = dWant < 0f ? 0f : dWant;
            float before = gApplied;
            gApplied = barDamp(d);
            gGainLast = (d > 1f && (d - gDbgLastD) > 1f) ? (gApplied - before) / (d - gDbgLastD) : gGainLast;
            gDbgLastD = d;
            float cx = right ? (sw - half - gApplied) : (half + gApplied);
            float lenHalf = (dragBar == null ? dpf(80f) : dragBar.lenPx()) / 2f;
            float cy = fy;
            float lim = lenHalf + dpf(6f);
            if (cy < lim) cy = lim;
            if (cy > sh - lim) cy = sh - lim;
            gLastCy = cy;
            if (dragBar != null) dragBar.setCenter(cx, cy);
            float dF = e.getRawX() - gPrevFx, dC = cx - gPrevCx;   // ★89.6 1:1 证据：手指走了多少、白条走了多少
            gPrevFx = e.getRawX(); gPrevCx = cx;
            long now = System.currentTimeMillis();
            if (now - gNoteAt > 120L) {
                gNoteAt = now;
                note("bar drag 跟手：手指(" + Math.round(e.getRawX()) + "," + Math.round(e.getRawY()) + ") ⇒ 白条中心("
                        + Math.round(cx) + "," + Math.round(cy) + ") 理论=" + Math.round(fx) + " 差=" + Math.round(cx - fx)
                        + "px 本次手指Δ=" + Math.round(dF) + " 白条Δ=" + Math.round(dC) + " 离边=" + Math.round(gApplied)
                        + "px 增益=" + fmt2(gGainLast) + "（Dmax " + Math.round(sw * BAR_DAMP_SW) + "px）");
            }
        } catch (Throwable t) { noteFail("barDragMove", t); }
    }

    private void barDragEnd(MotionEvent e) {
        try {
            float vx = 0f;
            if (gVt != null) { try { gVt.computeCurrentVelocity(1000); vx = gVt.getXVelocity(); } catch (Throwable t) { } }
            boolean right = isRightSide();
            float fingerDx = e.getRawX() - gDownX;
            float line = sw * BAR_BARRIER_SW;      // ★89.7：判定线＝屏宽 40%（按 applied 算；不画出来）
            boolean cross = gApplied >= line;
            note("bar drag 松手：applied=" + Math.round(gApplied) + "px（判定线 " + Math.round(line) + "px＝屏宽 40%）"
                    + " 手指横移=" + Math.round(fingerDx) + "px 速度=" + Math.round(vx) + "px/s（只记数据，不作判据）⇒ "
                    + (cross ? "越过判定线 ⇒ 去对面" : "没越过 ⇒ 回原边"));
            if (cross) barFlyTo(!right, vx);
            else barSpringBack(right);
        } catch (Throwable t) { noteFail("barDragEnd", t); }
    }

    /** ★89.7 整改一（19 号第三轮）：慢拖松手 ⇒ **200ms 一段动画**：位置回边 ＋ boost 从当前值 → 0（尺寸同时缩回常态），
     *  同一个 ValueAnimator、同一个时钟、同一条 easeOutCubic（无过冲、绝不越过终态）。逐帧记厚／长，证明单调无跳变。 */
    private void barSpringBack(final boolean right) {
        try {
            if (dragBar == null) { dragEndCommon(); return; }
            if (dragAnim != null) { try { dragAnim.cancel(); } catch (Throwable t) { } dragAnim = null; }
            final float from = gApplied;
            final float fromCy = gLastCy;
            final float b0 = dragBar.boost();
            final StringBuilder fr = new StringBuilder(2048);
            final int[] fn = {0};
            final long t0 = System.nanoTime();
            if (gFrameN == 0) Choreographer.getInstance().postFrameCallback(dragFrameCb);
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(BAR_BACK_MS);
            va.setInterpolator(new android.view.animation.LinearInterpolator());   // 曲线自己算（easeOutCubic，无过冲）
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) {
                    if (dragBar == null) return;
                    float k = easeOutCubic((Float) a.getAnimatedValue());
                    float applied = from * (1f - k);      // 位置：单调回边，不越过（没有负过冲）
                    dragBar.setBoost(b0 * (1f - k));      // 尺寸：同一段动画、同一个 k 缩回常态
                    float half = dragBar.thickPx() / 2f;
                    float cx = right ? (sw - half - applied) : (half + applied);
                    dragBar.setCenter(cx, fromCy);
                    if (fn[0] < 40) {
                        fn[0]++;
                        fr.append("bar 回位逐帧 ms=").append((System.nanoTime() - t0) / 1000000L)
                                .append(" k=").append(fmt2(k))
                                .append(" 厚=").append(fmt2(dragBar.thickPx() / dens())).append("dp")
                                .append(" 长=").append(fmt2(dragBar.lenPx() / dens())).append("dp")
                                .append(" 离边=").append(Math.round(applied)).append("px\n");
                    }
                }
            });
            va.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    dragAnim = null;
                    note("bar 回位到边：离边 " + Math.round(from) + "px → 0、boost " + fmt2(b0) + " → 0（同一段 " + BAR_BACK_MS
                            + "ms easeOutCubic：位置与尺寸同一个时钟，单调、不过冲）");
                    flushTrace(fr);
                    settleDragRight(right);
                }
            });
            dragAnim = va;
            va.start();
            note("bar 回位 start（" + BAR_BACK_MS + "ms，起：离边 " + Math.round(from) + "px／boost " + fmt2(b0) + "）");
        } catch (Throwable t) { noteFail("barSpringBack", t); }
    }

    /** ★89.7 整改二 / 三：越过判定线 ⇒ 350ms easeOutCubic 飞到对面（无过冲）；boost 同一段动画回落到 0。 */
    private void barFlyTo(final boolean toRight, final float vx) {
        try {
            if (dragBar == null) { dragEndCommon(); return; }
            if (dragAnim != null) { try { dragAnim.cancel(); } catch (Throwable t) { } dragAnim = null; }
            // ★90：飞行层自成一格——旧边窄窗立刻收干净，飞行中只许它画条
            flying = true;
            barVisual("飞行开始·旧边收起");
            final float x0 = dragBar.centerX();
            final float cy = gLastCy;
            final float b0 = dragBar.boost();
            final StringBuilder fr = new StringBuilder(2048);
            final int[] fn = {0};
            final long t0 = System.nanoTime();
            if (gFrameN == 0) Choreographer.getInstance().postFrameCallback(dragFrameCb);
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(BAR_FLY_MS);
            va.setInterpolator(new android.view.animation.LinearInterpolator());
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) {
                    if (dragBar == null) return;
                    float k = easeOutCubic((Float) a.getAnimatedValue());
                    dragBar.setBoost(b0 * (1f - k));                    // 先按常态半粗算终点，边走边缩
                    float half = dragBar.thickPx() / 2f;
                    float xt = toRight ? (sw - half) : half;
                    dragBar.setCenter(x0 + (xt - x0) * k, cy);          // 单调到达对面，不越过
                    if (fn[0] < 40) {
                        fn[0]++;
                        fr.append("bar 飞对面逐帧 ms=").append((System.nanoTime() - t0) / 1000000L)
                                .append(" k=").append(fmt2(k))
                                .append(" x=").append(Math.round(dragBar.centerX()))
                                .append(" 厚=").append(fmt2(dragBar.thickPx() / dens())).append("dp\n");
                    }
                }
            });
            va.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    dragAnim = null;
                    note("bar 飞到对面：中心 x " + Math.round(x0) + " → " + Math.round(dragBar == null ? 0 : dragBar.centerX())
                            + "（" + BAR_FLY_MS + "ms easeOutCubic，无过冲；厚度同段回落到常态）");
                    flushTrace(fr);
                    settleDragRight(toRight);   // ★90：落地在 dragEndCommon 里分两步显示新边
                }
            });
            dragAnim = va;
            va.start();
            note("bar 去对面 start（" + BAR_FLY_MS + "ms，从 x=" + Math.round(x0) + " 去「" + (toRight ? "右" : "左") + "」边，"
                    + "旧边窄窗已隐藏，新边等落地）");
        } catch (Throwable t) { noteFail("barFlyTo", t); }
    }

    /** 拖／甩结束：定案到某条边，存「哪边＋沿边比例」，再摘拖动层、放回白条。 */
    private void settleDragRight(boolean right) {
        try {
            float lenHalf = dragBar == null ? dpf(40f) : dragBar.lenPx() / 2f;
            int y = Math.round(gLastCy - figH / 2f);   // 白条中心对窗口中心（松手不跳）
            y = clampI(y, 0, Math.max(0, sh - figH));
            float range = Math.max(1f, sh - figH);
            float frac = y / range;
            if (frac < 0f) frac = 0f;
            if (frac > 1f) frac = 1f;
            savePlace(right ? 2 : 1, frac);
            figLp.x = right ? Math.max(0, sw - figW) : 0;
            figLp.y = y;
            placeFig();
            note("bar drag 定案：吸「" + (right ? "右" : "左") + "」边（只吸左右）；位置存 side=" + (right ? 2 : 1)
                    + " frac=" + fmt2(frac) + "（沿边 " + Math.round(frac * 100f) + "% 段，y=" + y + "／屏高 " + sh + "）");
        } catch (Throwable t) { noteFail("settleDragRight", t); }
        dragEndCommon();
    }

    private void dragEndCommon() {
        if (dragAnim != null) { try { dragAnim.cancel(); } catch (Throwable t) { } dragAnim = null; }
        if (dragAdded && dragWin != null && wm != null) { try { wm.removeView(dragWin); } catch (Throwable t) { } }
        dragAdded = false;
        flying = false;
        // ★90：落地只改一次可见性——先按「无」收干净（旧边已摘、新边还没亮）
        barFrozen = true;
        setEdgeBarSide();
        if (edgeBar != null) edgeBar.setBoost(0f);
        barVisual("拖动结束归位·先切无");
        frameStatTag("bar drag frame stat", gFrames, gFrameN);
        gFrameN = 0;
        gDbgLastD = -1f;
        // ★90：下一帧才让新边窄窗出现（飞行结束 → 显示，分两步）
        ui.post(new Runnable() { public void run() {
            if (dragAdded || flying) return;
            barFrozen = false;
            barVisual("飞行落地·新边显示");
        }});
    }

    private void frameStatTag(String tag, long[] ts, int n) {
        try {
            if (n < 3) { note(tag + " 样本太少（" + n + " 帧），不算"); return; }
            long sum = 0, max = 0;
            int over = 0;
            for (int i = 1; i < n; i++) {
                long d = ts[i] - ts[i - 1];
                sum += d;
                if (d > max) max = d;
                if (d > 16700000L) over++;
            }
            int cnt = n - 1;
            note(String.format(java.util.Locale.ROOT, "%s 帧=%d 均值=%.1fms 最差=%.1fms 超16.7ms=%d",
                    tag, cnt, (sum / 1000000.0) / cnt, max / 1000000.0, over));
        } catch (Throwable t) { }
    }
    private boolean onFigTouch(MotionEvent e) {
        if (minMode) return onBarTouch(e);   // ★89 极简＝白条的手（点/滑/按/拖）；常规模式原样不动
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
                    if (moved && morphShown()) hideBubble("拖白条收起");   // ★88：拖白条的时候框不许留在原地
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
                        if (bubShown() || morphShown()) {   // ★88：白条／框在屏时点一下＝收起（动画中途点也算）
                            if (minMode) collapseBubbleByGesture();
                            else hideBubble("点一下收起");
                        }
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
            if (morphAdded && morphWin != null) { wm.removeView(morphWin); morphAdded = false; morphState = 0; morphSeq++; if (morphAnim != null) { morphAnim.cancel(); morphAnim = null; } }   // ★88：截图／触摸让路时大窗一起摘
            if (figAdded) { wm.removeView(figWin); figAdded = false; }
            barVisual("让路摘窗");
        } catch (Throwable t) { noteFail("takeAway", t); }
    }

    private void putBack() {
        try {
            if (wm == null || !isOn(svc)) return;
            if (!figAdded) { wm.addView(figWin, figLp); figAdded = true; }
            if (bubShown() && !bubAdded) { wm.addView(bubWin, bubLp); bubAdded = true; }
            barVisual("让路放回");
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
