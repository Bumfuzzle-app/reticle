// ===== 29.5b · 外援版（Kimi K3）v3 · 重套 29.5 适配 =====
// ===== 29 号单 · 正式实施（0.21.0）：底部导航液态玻璃 =====
// 源＝外援版 Kimi K3 v3（探路包 app.reticle.probe.GlassyNavBarK3，已实机验收，见 任务/29-液态玻璃/证据/29.5b/）
// 相对探路集成版的差异（其它一字未改——K3 的视觉与运动逻辑保持原味）：
//   ① 包名/类名 → app.reticle.GlassyNavBar；图标资源名 ic_tab_cfg → ic_tab_conf（正式工程里的名字）
//   ② 去掉「拖动中无位移 → 强制复位」兜底（用户口径 2026-10-07：按着不动就永远不动、松手才吸附）；
//      改为按住不动时拉伸速度自然衰减（DRAG_VEL_DECAY），滴形自己收回静止形状，不做位置跳变。
//   ③ 新增「条体拖拽活性」：拖动滴期间，条体（玻璃条＋图标文字＋紫层）整体轻微缩放＋透明度略降，
//      松手弹簧恢复；滴不在这个容器里，故滴的折射采样不受影响。
//   ④ 新增静默 setSelectedIndex/getSelectedIndex（面板切页时同步用，不回调）。
// 原始件（只读）：/sdcard/给Kimi/GlassyNavBar(3).java
// 相对原件改动只有五类：①类名/构造器→GlassyNavBar；②4 处单位换算（blur→比例 / saturation→百分比 /
//   edgeHighlightOpacity→0-100 / aberrationIntensity→倍率刻度）；③新增 selectImmediate(int) 与 rawX() 退化兼容；
//   ④拖动 300ms 无位移自复位兜底。
// K3 的视觉与运动逻辑（含 v3 新增的点击状态机）未改；明细见 证据/29.5b/说明.txt。
package app.reticle;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewOutlineProvider;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.example.liquidglass.BlurMethod;
import com.example.liquidglass.GlassMaterial;
import com.example.liquidglass.LiquidGlassView;

/**
 * GlassyNavBar —— 液态玻璃底部导航条（纯 Java / 无 XML / 无 Compose，单文件单公共类）
 * 依赖：com.github.QWEA0:liquidglass:v2.0.11（主类 com.example.liquidglass.LiquidGlassView）
 *
 * 结构（z 序自下而上）：
 *   barGlass（LiquidGlassView，悬浮胶囊条体，采样源 = setBackdropSource 传入的页面根）
 *   → 3 × TabButton（真实可点 View：原色图标+文字，承担无障碍）
 *   → PurpleOverlay（紫色层，每帧按玻璃滴形状 clipPath 裁剪）
 *   → dropMask（49.5-2／54 灰蒙版：普通 View，纯填充无描边；停住后替代玻璃滴承接形体）
 *   → droplet（LiquidGlassView 玻璃滴；默认采样直接父容器中先于它绘制的内容 = 嵌套透镜，
 *      因此它同时折射条面、原色图标与紫层，和 iOS 26 / 参照图一致）
 *
 * ═══════════════════════════════════ 参 数 表 ═══════════════════════════════════
 * 【尺寸】
 *   ICON_SIZE_DP        26dp    图标边长
 *   LABEL_SIZE_SP       12sp    标签字号
 *   LABEL_GAP_DP         3dp    图标—标签间距
 *   DROPLET_H_INSET_DP  10dp    滴水平内缩：滴宽 = 槽宽(条宽/3) − 2×它（左右对称）
 *   DROPLET_V_INSET_DP   0dp    滴垂直内缩：0 = 与导航条同高（需求 1；想内缩改 4~6）
 * 【条体光学】（REGULAR 材质——CLEAR 自带压暗层会发暗，不用）
 *   BAR_BLUR_RATIO      0.05    条背景模糊（库量纲＝占视图尺寸比例 0-1）——[29.5b 单位修正]
 *   条体斜面/折射/色散  = 滴参数 ×（0.8 / 0.6 / 0.5），条要比滴克制（见 applyParamSet）
 * 【滴光学 · 三套参数集】（setParamSet(0/1/2) 切换：0=均衡，1=棱镜强，2=柔和更弹）
 *   PS_BEVEL_DP   {12,16, 9}    斜面宽度：边缘折射带厚度。库默认 48px 且短边<110dp
 *                               会被 adaptiveLensScale 按平方削没 → 本类显式关闭后给定
 *   PS_REFR_H_DP  {26,34,18}    折射高度：边缘位移幅度（要明显大于 bevel 才有压缩环）
 *   PS_FALLOFF    {2.0,2.4,1.6} 逆幂剖面指数：≥2 时弯折挤在最外几像素（细密压缩环，
 *                               内部仅轻微放大）；0 = 旧式均匀斜面
 *   PS_DISPERSION {0.35,0.60,0.20} 物理色散（光谱彩边）强度，API33+ 生效，按 0~1 假定
 *   PS_ABERR      {1.20,2.00,0.80} 经典色差强度（库刻度默认 2，作倍率乘偏移）——[29.5b 单位修正：原值×8]
 *   DROP_BLUR_ACTIVE    0.030   动中的滴内模糊（同上量纲；静止归零、随动效升/降）——[49 号单 A]
 * 【运动】（同一根弹簧：拉伸由弹簧速度推导 → 形变归零与到位严格同刻）
 *   PS_ZETA       {0.68,0.70,0.60} 阻尼比：<1 带过冲，过冲量 e^(−πζ/√(1−ζ²)) ≈ 5%/4%/9%
 *   PS_STIFF      {170,190,140}    刚度 k：ω=√k ≈ 12~14 rad/s，稳定时间 ≈ 4/(ζω) ≈ 0.4~0.5s
 *   PS_SMAX       {1.35,1.30,1.45} 最大横向拉伸比；纵向 = 1/它（等体积压缩）
 *   V_REF         2600px/s          达到满拉伸的速度（滑过一个槽位的峰值速度约 4~5k px/s）
 *   FLING_MAX     4500px/s          松手初速上限
 *   PRESS_SCALE   0.96              按住时的整体轻压（独立小弹簧 K=320/ζ=0.75）
 *   SETTLE_DX/DV  0.4px / 8px/s     弹簧收敛阈值：到位即 v=0 → 拉伸同帧归零
 *   DRAG_VEL_DECAY 0.82/帧          按住不动时拉伸自然回弹
 * 【停住换层 · 49.5-2／54】灰蒙版＝纯填充、无描边；浓度与「滴→蒙版」间隔都由配置页滑条给
 *   （setMaskAlpha／setMaskGapMs；MASK_FADE_MS 真滴淡出／MASK_IN_MS 蒙版淡入／MASK_OUT_MS 换回时长）
 * 【主题】（setThemeMode：0=浅色 1=深色 2=极夜）
 *   BAR_TINT / DROP_TINT          条与滴的 argb 染色（白玻璃 / 烟灰 / 近黑）；★49.5 起滴那档只留 ~8%（极淡纱），滴下图标才真清晰
 *   CONTENT                       未覆盖处图标文字色；PURPLE = 覆盖变色（#8E62E8 浅 / #D0BCFF 深·极夜）
 *   EDGE_OP / EDGE_W_DP           边缘高光不透明度（库量纲 0-100）/ 宽度：极夜显著调弱，黑底不刺眼
 * ══════════════════════════════════════════════════════════════════════════════════
 */
public class GlassyNavBar extends FrameLayout {

    public interface Listener { void onTabSelected(int index); }

    // ---------------------------------- 常量区 ----------------------------------

    private static final int TAB_COUNT = 3;
    private static final String[] TAB_LABELS = {Words.NAV_TAB1, Words.NAV_TAB2, Words.NAV_TAB3};
    private static final int[] TAB_ICONS = {
            R.drawable.ic_tab_func, R.drawable.ic_tab_conf, R.drawable.ic_tab_adv};

    private static final float ICON_SIZE_DP = 29f;
    private static final float LABEL_SIZE_SP = 13.5f;
    private static final float LABEL_GAP_DP = 3f;
    private static final float DROPLET_H_INSET_DP = 10f;
    private static final float DROPLET_V_INSET_DP = 0f;   // 需求 1：静止与条同高

    private static final float BAR_BLUR_RATIO = 0.05f;
    // ══ 49 号单 A：滴内模糊「随动效走」══
    // 静止＝完全无模糊（滴停在图标文字上也清楚）；动效一开始（按下／拖动／弹射／切 tab）→ 短时间内升到
    // DROP_BLUR_ACTIVE；停驻（弹簧收敛，SETTLE_DX/DV 那刻起）→ 在 DROP_BLUR_FALL_MS 内线性回落到 0。
    // 升／降两条曲线各给常量，便于调；只在数值真变了才推给库（逐帧计算在 stepFrame 里做，不在 onDraw 里做重活）。
    private static final float DROP_BLUR_ACTIVE  = 0.18f;    // 动中的滴内模糊（库量纲＝占视图尺寸比例 0-1；实测把滴内图标清晰度压掉约 1/5，是"看得出来但不糊"的档）
    private static final float DROP_BLUR_RISE_MS = 100f;     // 0 → 满值 的上升时间量级
    private static final float DROP_BLUR_FALL_MS = 150f;     // 停稳 → 0 的回落时间量级（派单口径 ~150ms）
    // ══ 49.5 号单（第二轮 · 换方向）：停住后把「玻璃」整个换掉 ══
    // 起因：滴停住时滴下图标看着不清，罪魁是玻璃本身（折射／边光／残纱）——不再跟材质较劲。
    // 做法（54 起两段式）：弹簧收敛那刻起，真玻璃滴先淡出；隔 maskGapMs 后再淡入下一层——一个
    //      「长得跟滴一样、但没有一点玻璃性质」的普通 View（灰蒙版，纯填充无描边）承接。
    //      一有动效（按下／拖动／弹射／切 tab）→ 在 MASK_OUT_MS 内快速换回真玻璃滴（防双影）。
    // ★隐藏真滴只用 alpha，绝不用 setVisibility（GONE／INVISIBLE 会丢触摸）。
    // ★54 号单 A（蒙版第二版）：① 去描边＝只留填充（人眼看不出边界）；② 浓度与「滴→蒙版」的间隔
    //   都改成运行时值（配置页两根滑杆，持久化；setMaskAlpha／setMaskGapMs）。
    //   时序（两段式）：停稳 t=0 起真滴在 MASK_FADE_MS 内 alpha 1→0；到 maskGapMs 那一刻起，
    //   蒙版在 MASK_IN_MS 内 alpha 0→1。★gap=0 ＝ 两者同时起跑 ＝ 49.5-2 原来的交叉淡化（逐帧等价）。
    //   反向（按下／拖动／弹射／切 tab）→ 仍在 MASK_OUT_MS 内快切换回真滴（防双影、不受滑条影响）。
    private static final int[] MASK_INK     = {0x000000, 0xFFFFFF, 0xFFFFFF}; // 墨色：浅色主题黑、深色／极夜白
    public static final float MASK_ALPHA_MIN = 0.02f;   // 浓度下限（配置页滑条 2%）
    public static final float MASK_ALPHA_MAX = 0.20f;   // 浓度上限（20%）
    public static final float MASK_ALPHA_DEF = 0.12f;   // 默认 12% ＝ 49.5-2 的「稍显」档
    public static final int   MASK_GAP_MIN_MS = 0;      // 间隔下限（＝同时交叉淡化）
    public static final int   MASK_GAP_MAX_MS = 400;    // 间隔上限
    public static final int   MASK_GAP_DEF_MS = 120;    // 默认间隔
    private static final float MASK_FADE_MS = 170f;     // 真滴淡出时长（停稳 → alpha 1→0）
    private static final float MASK_IN_MS   = 170f;     // 蒙版淡入时长
    private static final float MASK_OUT_MS  = 70f;      // 一有动效 → 快切换回真玻璃滴

    private static final float[] PS_BEVEL_DP   = {12f, 16f, 9f};
    private static final float[] PS_REFR_H_DP  = {26f, 34f, 18f};
    private static final float[] PS_FALLOFF    = {2.0f, 2.4f, 1.6f};
    private static final float[] PS_DISPERSION = {0.35f, 0.60f, 0.20f};
    private static final float[] PS_ABERR      = {1.20f, 2.00f, 0.80f};
    private static final float[] PS_ZETA       = {0.68f, 0.70f, 0.60f};
    private static final float[] PS_STIFF      = {170f, 190f, 140f};
    private static final float[] PS_SMAX       = {1.35f, 1.30f, 1.45f};

    private static final float V_REF = 2600f;
    private static final float FLING_MAX = 4500f;
    private static final float PRESS_SCALE = 0.96f;
    private static final float PRESS_K = 320f;
    private static final float PRESS_C = 2f * 0.75f * (float) Math.sqrt(PRESS_K);
    private static final float SETTLE_DX = 0.4f;
    private static final float SETTLE_DV = 8f;
    private static final float DRAG_VEL_DECAY = 0.82f;
    private static final float MOVE_VEL_BLEND = 0.35f;

    private static final int[] BAR_TINT    = {0x54FFFFFF, 0x662A2A30, 0x730E0E12};
    // ══ 49.5 号单：摘掉滴上那层纱（滴下图标才真清晰）══
    // 原值 {0x78FFFFFF, 0x6E3A3A42, 0x6E1B1B22}：浅色那档＝47% 纯白，等于给滴下图标罩了一层白纱——
    // 实测「滴在」的对比度只剩「滴不在」的 48%（94 vs 195）、边缘能量只剩 49%（1.48 vs 3.04）；
    // 这层纱与模糊无关（blur 归零它也不消失）——49 单量到的「停稳逐字节同基线」只证明 blur 归零。
    // 现降到 ~8%：三套主题下滴只剩很淡一层本体（靠边光与透镜折射认滴），滴下图标与不带滴时几乎一样清。
    private static final int[] DROP_TINT   = {0x14FFFFFF, 0x143A3A42, 0x141B1B22};
    private static final int[] CONTENT     = {0xFF3C3C46, 0xFFDCDCE6, 0xFFB6B6C4};
    private static final int[] PURPLE      = {0xFF7C4AD8, 0xFFC6AEFF, 0xFFC6AEFF};
    private static final float[] EDGE_OP   = {50f, 32f, 16f};
    private static final float[] EDGE_W_DP = {1.2f, 1.0f, 0.8f};
    private static final float[] SAT       = {110f, 106f, 100f};

    /** API 33+ 走 AGSL 透镜管线，可用 setPrimaryShape 做逐帧形变；更低版本降级为 view 边界模式。 */
    private static final boolean SHAPE_MODE = Build.VERSION.SDK_INT >= 33;

    // ---------------------------------- 状态区 ----------------------------------

    private LiquidGlassView barGlass;
    private LiquidGlassView droplet;
    private TabButton[] tabs;
    private PurpleOverlay overlay;
    private FrameLayout barContent;              // 条体内容容器（玻璃条＋tab＋紫层）：拖动滴时整体轻微缩放＋降透明
    // ══ 33 单：动效状态 ══
    private float barScaleX = 1f, barScaleY = 1f; // 条体非等比缩放（皮筋：横长纵瘦）
    private float stretch = 0f, stretchVel = 0f;  // 皮筋量（0 = 常态）
    private int lastPulseSlot = -1;               // 最近一次播过图标动效的槽（去重）
    private float stretchAmpV = 0.12f;            // 条体横向拉伸峰值（配置页滑杆可调，0.02~0.30）
    private float iconPeakV = 1.65f;              // 图标放大峰值（配置页滑杆可调，1.00~2.60）
    private float iconMsV = 640f;                 // 图标动效时长 ms（配置页滑杆可调，520~1200）
    private final RectF clipTmp = new RectF();   // 紫层裁剪的反向补偿用（复用，不逐帧分配）

    // 29 号单③「条体拖拽活性」：先给保守值，做成常量便于调（scale ≈0.985 / alpha ≈0.92 量级起步）
    private static final float BAR_SQUEEZE_SCALE = 0.985f;
    private static final float BAR_SQUEEZE_ALPHA = 0.92f;

    // ══ 33 单：图标动效 ＋ 条体"皮筋"（幅度与时长的"无极值"由配置页滑杆给；弹性固定中档）══
    private static final float BAR_THIN      = 0.65f;   // 纵向变瘦 = 横向拉伸 × 该系数
    private static final float STRETCH_ZETA  = 0.45f;   // 皮筋阻尼（越小越弹、过冲越大）
    private static final float FX_STRETCH_K  = 210f;    // 皮筋刚度
    private static final float FX_KICK_V     = 6.0f;    // 轻点／松手给皮筋的速度冲量
    private static final float FX_SPIN       = 360f;    // 旋转角（转满一圈）

    private Listener listener;
    private View backdropPending;

    private int themeMode = 0;
    private int paramSet = 0;
    private int selectedIndex = 0;

    private float density;
    private float touchSlop;

    // 几何
    private int barW, barH, slotW;
    private float restW, restH;              // 滴静止尺寸（restH == barH，与条同高）
    private boolean geometryReady;

    // 运动（同一根弹簧驱动位置；拉伸/压缩全部由弹簧速度推导）
    private float curX, velX, targetX;       // px / px·s⁻¹
    private boolean animating, dragging, touchMoved;
    private boolean tapCandidate;            // 滴外按下、位移未超 slop 的轻点候选
    private float grabOffRaw, downRawX;      // 用 rawX 做拖拽数学，规避局部坐标系差异
    private float downRawY, downParentX;     // downParentX：落点的条内坐标（轻点命中槽位用）
    private float lastMoveRawX;
    private long lastMoveTime;
    private float pressScale = 1f, pressVel, pressTarget = 1f;
    private VelocityTracker tracker;
    private float dropBlur = 0f;                  // 49 号单 A：滴内模糊当前值（0＝静止完全无模糊；由 tickDropletBlur 逐帧推）
    private DropMask dropMask;                    // 49.5-2：灰蒙版（普通 View，无玻璃／模糊／染色；停住后承接滴的形体）
    private float maskFillA = MASK_ALPHA_DEF;     // 54：蒙版浓度（配置页滑条 0.02~0.20）
    private int   maskGapMs = MASK_GAP_DEF_MS;    // 54：真滴淡出 → 蒙版淡入 的间隔
    private float maskClock = -1f;                // 54：停稳后的时间轴（ms；<0 ＝ 有动效、时间轴未开始）
    private float dropA = 0f, maskA = 1f;         // 54：真滴／蒙版 各自的不透明度（初始＝停稳态）
    private float dropA0 = 1f, maskA0 = 0f;       // 54：本轮「停稳 →」的起跑值（中途被打断也不跳变）
    private boolean ticking;
    private long lastFrameNanos;

    private final int[] tmpLoc = new int[2];      // getLocationOnScreen 复用
    private final RectF dropRect = new RectF();   // 复用，不在每帧分配

    // ---------------------------------- 构造 ----------------------------------

    public GlassyNavBar(Context c, AttributeSet a) {
        super(c, a);
        density = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(c).getScaledTouchSlop();
        setClipChildren(false);                    // 放行条玻璃的软阴影
        buildChildren(c);
        applyParamSet();
        applyTheme();
        tabs[0].setSelected(true);
        tabs[0].updateDesc();
    }

    /** 便捷构造：正式面板用代码建视图，不需要 AttributeSet。 */
    public GlassyNavBar(Context c) { this(c, null); }

    private void buildChildren(Context c) {
        // 0) 条体内容容器：玻璃条 ＋ 三个 tab ＋ 紫层（拖动滴时整体轻微缩放＋降透明；滴不在里面）
        barContent = new FrameLayout(c);
        barContent.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        barContent.setClipChildren(false);
        addView(barContent);

        // 0.1) 条体玻璃（最底）
        barGlass = new LiquidGlassView(c);
        barGlass.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        barGlass.setClickable(false);
        barGlass.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        barContent.addView(barGlass);
        // 34 号单：条体玻璃加「圆角裁剪」——库会把贴着形状外侧的边缘衰减也画出来，
        // 浅色下就＝四个角淡淡的灰蒙版；把绘制裁进胶囊轮廓即可（光影/动效一律不动）。
        barGlass.setClipToOutline(true);
        barGlass.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                int w = v.getWidth(), h = v.getHeight();
                if (w > 0 && h > 0) o.setRoundRect(0, 0, w, h, h / 2f);
                else o.setEmpty();
            }
        });

        // 1) 三个真实可点 tab（原色内容 + 无障碍）
        tabs = new TabButton[TAB_COUNT];
        for (int i = 0; i < TAB_COUNT; i++) {
            tabs[i] = new TabButton(c, i);
            tabs[i].setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            barContent.addView(tabs[i]);
        }

        // 1.5) 49.5-2：灰蒙版（普通 View，按滴形裁剪；停住后由它承接滴的形体）
        //      层级＝原色 tab 之上、紫层之下（紫层再把紫色图标画在它上面，图标一点不受影响）；
        //      真玻璃滴在 barContent 之外，永远压在最顶。
        dropMask = new DropMask(c);
        dropMask.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dropMask.setAlpha(maskA);                   // 54：初始＝停稳态（蒙版在、玻璃滴隐）
        barContent.addView(dropMask);

        // 2) 紫色覆盖层（按滴形状裁剪；不拦截触摸）
        overlay = new PurpleOverlay(c);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        barContent.addView(overlay);

        // 3) 玻璃滴（最顶：采样父容器中先于它绘制的全部内容；放在 barContent 之外 → 不受条体缩放/降透明影响）
        droplet = new LiquidGlassView(c);
        if (SHAPE_MODE) {
            droplet.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            droplet.setLayoutParams(new FrameLayout.LayoutParams(0, 0, Gravity.TOP | Gravity.LEFT));
        }
        droplet.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        droplet.setOnTouchListener(dropletTouch);
        addView(droplet);
        applyMaskAlpha();                           // 49.5-2：初始＝停稳态（灰蒙版可见、玻璃滴 alpha 0）
    }

    // -------------------------------- 对外接口 --------------------------------

    public void setThemeMode(int mode) {
        if (mode < 0 || mode > 2) return;
        themeMode = mode;
        applyTheme();
    }

    public void setParamSet(int set) {
        if (set < 0 || set > 2) return;
        paramSet = set;
        applyParamSet();
    }

    public int getParamSet() { return paramSet; }

    public void setListener(Listener l) { listener = l; }

    public void setBackdropSource(View v) {
        backdropPending = v;
        if (barGlass != null) barGlass.setBackdropSource(v);
        // 滴不设源：它采样直接父容器（先绘制的条+内容），且始终被夹在条内，不会采空。
    }

    // -------------------------------- 参数/主题 --------------------------------

    private void applyParamSet() {
        int p = paramSet;
        // —— 条体：克制版光学 ——
        barGlass.setMaterial(GlassMaterial.REGULAR);          // CLEAR 自带压暗层，不用
        barGlass.setAdaptiveLensScale(false);                 // 陷阱：短边<110dp 会把折射按平方削没
        barGlass.setEnableDynamicBackground(true);            // 陷阱：背景/玻璃会动必须 true
        barGlass.setBlurMethod(BlurMethod.SMART);
        barGlass.setBlurAmount(BAR_BLUR_RATIO);
        barGlass.setEnablePressEffect(false);                 // 按压形变由本类弹簧负责
        barGlass.setBevelWidth(dp(PS_BEVEL_DP[p]) * 0.8f);
        barGlass.setRefractionHeight(dp(PS_REFR_H_DP[p]) * 0.6f);
        barGlass.setRefractionFalloff(PS_FALLOFF[p]);
        barGlass.setDispersionStrength(PS_DISPERSION[p] * 0.5f);
        barGlass.setEnableChromaticDispersion(true);
        barGlass.setAberrationIntensity(PS_ABERR[p] * 0.5f);
        barGlass.setEnableChromaticAberration(true);
        barGlass.setEnableEdgeHighlight(true);
        if (backdropPending != null) barGlass.setBackdropSource(backdropPending);

        // —— 滴：完整版光学 ——
        droplet.setMaterial(GlassMaterial.REGULAR);
        droplet.setAdaptiveLensScale(false);
        droplet.setEnableDynamicBackground(true);
        droplet.setBlurMethod(BlurMethod.SMART);
        droplet.setBlurAmount(0f);                            // 49 号单 A：静止＝零模糊；动中由 tickDropletBlur 逐帧推
        dropBlur = 0f;                                        // 同步状态（下次动效再从 0 升）
        droplet.setEnablePressEffect(false);
        droplet.setEnableShadow(false);                       // 滴在条内，不需要投影
        droplet.setBevelWidth(dp(PS_BEVEL_DP[p]));
        droplet.setRefractionHeight(dp(PS_REFR_H_DP[p]));
        droplet.setRefractionFalloff(PS_FALLOFF[p]);
        droplet.setDispersionStrength(PS_DISPERSION[p]);
        droplet.setEnableChromaticDispersion(true);
        droplet.setAberrationIntensity(PS_ABERR[p]);
        droplet.setEnableChromaticAberration(true);
        droplet.setEnableEdgeHighlight(true);
    }

    private void applyTheme() {
        int t = themeMode;
        barGlass.setGlassTint(BAR_TINT[t]);
        barGlass.setSaturation(SAT[t]);
        barGlass.setEdgeHighlightBorderWidth(dp(EDGE_W_DP[t]));
        barGlass.setEdgeHighlightOpacity(EDGE_OP[t]);
        barGlass.setEnableShadow(t != 2);                     // 极夜：黑底上弱光效、关投影

        droplet.setGlassTint(DROP_TINT[t]);
        droplet.setSaturation(SAT[t]);
        droplet.setEdgeHighlightBorderWidth(dp(EDGE_W_DP[t]));
        droplet.setEdgeHighlightOpacity(Math.min(100f, EDGE_OP[t] * 1.2f));

        for (int i = 0; i < TAB_COUNT; i++) tabs[i].setContentColor(CONTENT[t]);
        overlay.setPurple(PURPLE[t]);
        dropMask.setInk(MASK_INK[t], maskFillA);
    }

    /** 54 号单 A：蒙版浓度（0.02~0.20；配置页滑条，持久化在 MainActivity 侧）。 */
    public void setMaskAlpha(float a) {
        if (a < MASK_ALPHA_MIN) a = MASK_ALPHA_MIN;
        if (a > MASK_ALPHA_MAX) a = MASK_ALPHA_MAX;
        maskFillA = a;
        if (dropMask != null) dropMask.setInk(MASK_INK[themeMode], maskFillA);
    }

    /** 54 号单 A：真滴淡出 → 蒙版淡入 的间隔（0~400ms；0 ＝ 同时起跑＝原来的交叉淡化）。 */
    public void setMaskGapMs(int ms) {
        if (ms < MASK_GAP_MIN_MS) ms = MASK_GAP_MIN_MS;
        if (ms > MASK_GAP_MAX_MS) ms = MASK_GAP_MAX_MS;
        maskGapMs = ms;
    }

    // ---------------------------------- 布局 ----------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        barW = w; barH = h;
        slotW = w / TAB_COUNT;
        restW = slotW - 2 * dp(DROPLET_H_INSET_DP);
        if (restW <= 0) restW = slotW;
        restH = h - 2 * dp(DROPLET_V_INSET_DP);
        if (restH > h) restH = h;
        barGlass.setCornerRadius(h / 2f);                     // 胶囊
        barGlass.invalidateOutline();   // 34 号单：条体尺寸变了 → 同步圆角裁剪轮廓
        if (!geometryReady) {
            curX = targetX = slotCenter(selectedIndex);
            geometryReady = true;
        }
        if (!dragging && !animating) {          // 尺寸变化后静止吸附回选中槽位
            curX = targetX = slotCenter(selectedIndex);
            velX = 0f;
        }
        curX = clampCenter(curX);
        updateDropletFrame();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        int h = b - t;
        if (slotW <= 0 || h <= 0) return;
        for (int i = 0; i < TAB_COUNT; i++) {                 // 三等分槽位
            TabButton tb = tabs[i];
            tb.measure(MeasureSpec.makeMeasureSpec(slotW, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            tb.layout(i * slotW, 0, (i + 1) * slotW, h);
        }
    }

    private float slotCenter(int i) { return slotW * i + slotW * 0.5f; }

    private int slotOf(float x) {
        int i = (int) Math.floor(x / slotW);
        if (i < 0) i = 0;
        if (i > TAB_COUNT - 1) i = TAB_COUNT - 1;
        return i;
    }

    private float clampCenter(float x) {
        float half = restW / 2f;
        if (x < half) return half;
        if (x > barW - half) return barW - half;
        return x;
    }

    // ------------------------------ 逐帧滴形更新 ------------------------------

    /** 由当前弹簧速度推导滴形：拉伸比 s = 1+(SMAX-1)·min(|v|/V_REF,1)，纵向等体积压缩。 */
    private void updateDropletFrame() {
        if (!geometryReady || barW <= 0 || barH <= 0) return;
        float ratio = Math.abs(velX) / V_REF;
        if (ratio > 1f) ratio = 1f;
        float s = 1f + (PS_SMAX[paramSet] - 1f) * ratio;
        float w = restW * s * pressScale;
        float h = restH / s * pressScale;
        if (h > barH) h = barH;                               // 永不高出条（需求 1）
        float halfW = w / 2f;
        float cx = curX < halfW ? halfW : (curX > barW - halfW ? barW - halfW : curX);
        float left = cx - halfW;
        float top = (barH - h) / 2f;                          // 上下对称
        dropRect.set(left, top, left + w, top + h);
        float radius = h / 2f;
        if (SHAPE_MODE) {
            droplet.setPrimaryShape(dropRect, radius);        // view 不动，只动形状：零 layout 开销
        } else {                                              // 降级：view 边界即透镜形状
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) droplet.getLayoutParams();
            int L = Math.round(left), T = Math.round(top);
            int W = Math.round(w), H = Math.round(h);
            if (lp.width != W || lp.height != H || lp.leftMargin != L || lp.topMargin != T) {
                lp.width = W; lp.height = H; lp.leftMargin = L; lp.topMargin = T;
                droplet.setLayoutParams(lp);
            }
            droplet.setCornerRadius(radius);
        }
        // 33 探路：条体＝"皮筋" —— 横向拉长（1 + 幅度·stretch）、纵向等体积变瘦；按压仍叠一层轻微收缩
        float sq = (1f - pressScale) / (1f - PRESS_SCALE);
        if (sq < 0f) sq = 0f;
        if (sq > 1f) sq = 1f;
        float press = 1f + (BAR_SQUEEZE_SCALE - 1f) * sq;
        barScaleX = press * (1f + stretchAmpV * stretch);
        barScaleY = press * (1f - stretchAmpV * BAR_THIN * stretch);
        if (barContent != null) {
            barContent.setScaleX(barScaleX);
            barContent.setScaleY(barScaleY);
            barContent.setAlpha(1f + (BAR_SQUEEZE_ALPHA - 1f) * sq);
        }
        // 紫层跟着条体一起被缩放 → 裁剪矩形按 (1/sx, 1/sy) 反向放回，可见裁剪边界仍等于滴形
        if (barScaleX != 1f || barScaleY != 1f) {
            float px = barW / 2f, py = barH / 2f, sx = barScaleX, sy = barScaleY;
            clipTmp.set(px + (dropRect.left - px) / sx, py + (dropRect.top - py) / sy,
                        px + (dropRect.right - px) / sx, py + (dropRect.bottom - py) / sy);
            overlay.setClip(clipTmp, radius / sy);
            if (dropMask != null) dropMask.setShape(clipTmp, radius / sy);  // 49.5-2：蒙版与滴同形同位
        } else {
            overlay.setClip(dropRect, radius);                    // 紫层逐帧跟随滴形
            if (dropMask != null) dropMask.setShape(dropRect, radius);      // 49.5-2：蒙版与滴同形同位
        }
    }

    /** 49 号单 A：按「动效活跃度」推滴内模糊。
     *  活跃（拖动／弹簧在跑／按压弹簧／条体皮筋未归零）→ 升向 DROP_BLUR_ACTIVE；
     *  停稳 → 在 DROP_BLUR_FALL_MS 量级内线性回到 0（静止＝完全无模糊）。
     *  返回 true ＝ 还在斜坡上，帧循环得继续跑；一到目标就返回 false（不额外续帧）。 */
    private boolean tickDropletBlur(float dt) {
        boolean active = dragging || animating
                || pressScale != pressTarget || pressVel != 0f
                || stretch != 0f || stretchVel != 0f;
        float target = active ? DROP_BLUR_ACTIVE : 0f;
        if (dropBlur == target) return false;
        float step = dt * 1000f / (target > dropBlur ? DROP_BLUR_RISE_MS : DROP_BLUR_FALL_MS) * DROP_BLUR_ACTIVE;
        if (step <= 0f) {                                     // 极端保护（dt 异常为 0）：直接到位，不空转
            dropBlur = target;
            droplet.setBlurAmount(dropBlur);
            return false;
        }
        if (target > dropBlur) dropBlur = Math.min(target, dropBlur + step);
        else                   dropBlur = Math.max(0f, dropBlur - step);
        droplet.setBlurAmount(dropBlur);
        return dropBlur != target;
    }

    /** 54 号单 A：两段式时序推进（取代 49.5-2 的同步交叉淡化）。
     *  有动效 → 目标「真滴在」：dropA→1、maskA→0，MASK_OUT_MS（70ms，沿用）内快切，不受滑条影响；
     *  停稳   → 时间轴从 0 起：真滴先在 MASK_FADE_MS 内淡出，隔 maskGapMs 后蒙版在 MASK_IN_MS 内淡入。
     *  maskGapMs=0 时两者同时起跑、时长都是 170ms → 与 49.5-2 的交叉淡化逐帧等价。
     *  返回 true ＝ 还在斜坡上，帧循环得继续跑。 */
    private boolean tickDropletMask(float dt) {
        boolean active = dragging || animating
                || pressScale != pressTarget || pressVel != 0f
                || stretch != 0f || stretchVel != 0f;
        if (active) {
            maskClock = -1f;                                  // 时间轴作废（下次停稳重新从 0 起）
            if (dropA >= 1f && maskA <= 0f) return false;     // 已在「真滴在」稳态：不额外续帧
            float step = dt * 1000f / MASK_OUT_MS;
            if (step <= 0f) { dropA = 1f; maskA = 0f; }       // 极端保护（dt 异常为 0）
            else { dropA = Math.min(1f, dropA + step); maskA = Math.max(0f, maskA - step); }
            applyMaskAlpha();
            return dropA < 1f || maskA > 0f;
        }
        if (maskClock < 0f) {                              // 刚停稳：时间轴归零，并记下起跑值
            maskClock = 0f; dropA0 = dropA; maskA0 = maskA;
        } else {
            maskClock += dt * 1000f;
        }
        dropA = dropA0 * (1f - clamp01(maskClock / MASK_FADE_MS));   // 54：真滴先淡出（从起跑值起）
        maskA = maskA0 + (1f - maskA0) * clamp01((maskClock - maskGapMs) / MASK_IN_MS);  // 54：隔 gap 后蒙版淡入
        applyMaskAlpha();
        return maskA < 1f;
    }

    /** 54：两层各自的不透明度 → 真滴与蒙版（同一刻只有一层是可见主体；形状同位，不会看着像两个滴）。 */
    private void applyMaskAlpha() {
        if (droplet != null) droplet.setAlpha(dropA);
        if (dropMask != null) dropMask.setAlpha(maskA);
    }

    // -------------------------------- 弹簧驱动 --------------------------------

    private final Choreographer.FrameCallback frameCb = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) { stepFrame(frameTimeNanos); }
    };

    private void startTicking() {
        if (!ticking) {
            ticking = true;
            lastFrameNanos = 0;
            Choreographer.getInstance().postFrameCallback(frameCb);
        }
    }

    private void stepFrame(long now) {
        if (!ticking) return;
        float dt = lastFrameNanos == 0 ? 1f / 60f
                : Math.min((now - lastFrameNanos) / 1e9f, 1f / 30f);
        lastFrameNanos = now;
        boolean more = false;

        if (!dragging && animating) {                          // 半隐式欧拉，单一弹簧
            float k = PS_STIFF[paramSet];
            float c = 2f * PS_ZETA[paramSet] * (float) Math.sqrt(k);
            float a = -k * (curX - targetX) - c * velX;
            velX += a * dt;
            curX += velX * dt;
            if (Math.abs(curX - targetX) < SETTLE_DX && Math.abs(velX) < SETTLE_DV) {
                curX = targetX;                                // 到位 ⇒ v=0 ⇒ 拉伸同帧归零
                velX = 0f;
                animating = false;
            } else {
                more = true;
            }
        }
        if (dragging) {
            // 29 号单②：用户口径（2026-10-07）＝按着不动就永远不动、松手才吸附。
            // 这里不再做「无位移 → 强制复位」的兜底（那会误伤"按住滴想一下再拖"）；
            // 按住不动时只让拉伸速度自然衰减 → 滴形自己收回静止形状，位置不动、无跳变。
            if (Math.abs(velX) > 1f) {
                velX *= DRAG_VEL_DECAY;
                more = true;
            } else {
                velX = 0f;
            }
        }
        if (pressScale != pressTarget || pressVel != 0f) {     // 按压小弹簧
            float a = -PRESS_K * (pressScale - pressTarget) - PRESS_C * pressVel;
            pressVel += a * dt;
            pressScale += pressVel * dt;
            if (Math.abs(pressScale - pressTarget) < 0.002f && Math.abs(pressVel) < 0.02f) {
                pressScale = pressTarget;
                pressVel = 0f;
            } else {
                more = true;
            }
        }

        // 33 探路：图标动效推进（进度 -1 = 空闲；到 1 收工）
        for (int i = 0; i < TAB_COUNT; i++) {
            TabButton tb = tabs[i];
            if (tb.animT >= 0f) {
                tb.animT += dt * 1000f / iconMsV;
                if (tb.animT > 1f) tb.animT = -1f;
                tb.invalidate();
                more = true;
            }
        }
        // 33 探路：皮筋 —— 拖动时跟手拉伸；其余时候弹簧回零（ζ 小 → 回弹过冲）
        if (dragging) {
            float v = Math.abs(velX) / V_REF;
            if (v > 1f) v = 1f;
            stretch = v * 0.9f;
            stretchVel = 0f;
            more = true;      // 33.1（用户口径）：拖动中不播图标动效——按住划来划去不算，要"确定停下"才播
        } else {
            float k = FX_STRETCH_K;
            float cz = 2f * STRETCH_ZETA * (float) Math.sqrt(k);
            float a = -k * stretch - cz * stretchVel;
            stretchVel += a * dt;
            stretch += stretchVel * dt;
            if (Math.abs(stretch) < 0.002f && Math.abs(stretchVel) < 0.01f) {
                stretch = 0f; stretchVel = 0f;
            } else {
                more = true;
            }
        }

        updateDropletFrame();
        if (tickDropletBlur(dt)) more = true;                 // 49 号单 A：滴内模糊随动效升/降（停了也要跑完归零）
        if (tickDropletMask(dt)) more = true;                 // 49.5-2：停住 → 真滴淡出＋灰蒙版淡入（一有动效立刻换回）
        if (more) {
            Choreographer.getInstance().postFrameCallback(frameCb);
        } else {
            ticking = false;
            lastFrameNanos = 0;
        }
    }

    // -------------------------------- 滴的触摸 --------------------------------
    //
    // 手势状态机（修复「点击无效」）：
    // 库的 LiquidGlassView.onTouchEvent 对 DOWN/MOVE/UP 无条件 return true（实机定位结论），
    // 上一版「滴外 return false 让位给 tab 按钮」因此不成立——false 之后事件仍被库自己吃掉。
    // 本版把轻点判定收进本监听器：监听器对所有手势 return true（事件根本到不了库的 onTouchEvent）。
    //   · 滴内按下：dragging=true，跟手拖动（行为与上一版逐行一致）；
    //   · 滴外按下：tapCandidate 候选轻点——横向超 slop → 召唤滴跟手（保持落点偏移，零跳变）；
    //     纵向超 slop → 整个手势忽略；未超 slop 抬起 → 命中落点所在槽 → 切换。
    // 边界模式（API<33，droplet=滴本身）下滴外事件根本到不了本监听器，仍走 TabButton.onClick。

    private final OnTouchListener dropletTouch = new OnTouchListener() {
        @Override public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    if (!geometryReady) return false;
                    downRawX = rawX(e);
                    downRawY = e.getRawY();
                    downParentX = parentXOf(e);
                    lastMoveRawX = downRawX;
                    lastMoveTime = e.getEventTime();
                    grabOffRaw = curX - downRawX;          // 召唤跟手时保持的落点偏移
                    touchMoved = false;
                    animating = false;                     // 接管弹簧
                    boolean downInside = !SHAPE_MODE || dropRect.contains(e.getX(), e.getY());
                    if (downInside) {
                        dragging = true;                   // 按住滴：与上一版一致
                        tapCandidate = false;
                        pressTarget = PRESS_SCALE;
                    } else {
                        dragging = false;
                        tapCandidate = true;               // 滴外按下：先当轻点候选
                    }
                    if (tracker != null) tracker.recycle();
                    tracker = VelocityTracker.obtain();
                    tracker.addMovement(e);
                    startTicking();
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (!dragging && !tapCandidate) return true;   // 已忽略的手势：吞掉即可
                    if (tracker != null) tracker.addMovement(e);
                    if (tapCandidate) {
                        float dx = rawX(e) - downRawX;
                        float dy = e.getRawY() - downRawY;
                        if (Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx)) {
                            tapCandidate = false;          // 纵向滑：忽略（不召唤、不切换）
                        } else if (Math.abs(dx) > touchSlop) {
                            tapCandidate = false;
                            dragging = true;               // 横向拖：召唤滴跟手
                            pressTarget = PRESS_SCALE;
                        }
                    }
                    if (dragging) {
                        curX = clampCenter(rawX(e) + grabOffRaw);
                        long nowT = e.getEventTime();
                        long dtMs = nowT - lastMoveTime;
                        if (dtMs > 0) {
                            float instantV = (rawX(e) - lastMoveRawX) * 1000f / dtMs;
                            velX = velX * (1f - MOVE_VEL_BLEND) + instantV * MOVE_VEL_BLEND;
                        }
                        lastMoveTime = nowT;
                        lastMoveRawX = rawX(e);
                        touchMoved = true;
                        startTicking();
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    float fling = 0f;
                    if (tracker != null) {
                        tracker.computeCurrentVelocity(1000, FLING_MAX);
                        fling = tracker.getXVelocity();
                        tracker.recycle();
                        tracker = null;
                    }
                    pressTarget = 1f;
                    kickStretch();                         // 33 探路：松手 → 皮筋回弹（带过冲）
                    if (dragging && touchMoved) {          // 拖拽松手：原逻辑（含召唤拖）
                        dragging = false;
                        velX = clampF(fling, -FLING_MAX, FLING_MAX); // 速度连续交接 → 无顿挫
                        int target = slotOf(curX);
                        targetX = slotCenter(target);
                        animating = true;
                        startTicking();
                        if (target != selectedIndex) selectTab(target, false, true);
                        else pulseIcon(target);            // 33.1：拖回原槽也是"确定停下"→ 播一次
                        return true;
                    }
                    if (dragging) {                        // 滴内轻点（=点当前档）：仅轻微反馈
                        dragging = false;
                        tapBounce();
                        return true;
                    }
                    if (tapCandidate) {                    // 滴外轻点：命中所在槽 → 切换
                        tapCandidate = false;
                        int slot = slotOf(downParentX);
                        if (slot != selectedIndex) {
                            selectTab(slot, true, true);   // 同一根弹簧滑过去，回调即刻发出
                        } else {
                            tapBounce();                   // 当前档：无操作，仅轻微反馈
                        }
                        return true;
                    }
                    return true;                           // 纵向滑忽略
                }
                case MotionEvent.ACTION_CANCEL: {
                    if (tracker != null) { tracker.recycle(); tracker = null; }
                    pressTarget = 1f;
                    tapCandidate = false;
                    if (dragging) {
                        dragging = false;
                        velX = 0f;
                        targetX = slotCenter(selectedIndex);
                        animating = true;
                        startTicking();
                    }
                    return true;
                }
                default:
                    return true;                           // 已接管的手势流，其余事件一律消费
            }
        }
    };

    // -------------------------------- 选中逻辑 --------------------------------

    private void onTabClicked(int i) {          // performClick（TalkBack/键盘）与低版本滴外点击入口
        if (i == selectedIndex) return;         // 当前档：无操作（修复要求 5）
        selectTab(i, true, true);               // 同一根弹簧滑过去，速度推导拉伸
    }

    private void selectTab(int i, boolean animate, boolean fire) {
        selectedIndex = i;
        for (int t = 0; t < TAB_COUNT; t++) {
            tabs[t].setSelected(t == i);
            tabs[t].updateDesc();
        }
        if (animate) {
            targetX = slotCenter(i);
            animating = true;
            startTicking();
        }
        if (i != lastPulseSlot) pulseIcon(i);              // 33 探路：切到这个槽 → 该槽图标转一圈
        if (fire) fireListener(i);
    }

    private void fireListener(int i) {
        if (listener != null) listener.onTabSelected(i);
    }

    // -------------------------------- 生命周期 --------------------------------

    @Override
    protected void onDetachedFromWindow() {
        ticking = false;
        Choreographer.getInstance().removeFrameCallback(frameCb);
        if (tracker != null) { tracker.recycle(); tracker = null; }
        super.onDetachedFromWindow();
    }

    // -------------------------------- 小工具 --------------------------------

    /**
     * [29.5b 必要适配] 取「有效原始 X」：注入事件（Accessibility / input）可能不带 raw 坐标（返回 0），
     * 此时退化为「本视图内坐标 + 视图在屏幕上的位置」。真手指路径 rawX>0，原逻辑不变。
     */
    private float rawX(MotionEvent e) {
        float rx = e.getRawX();
        if (rx > 0f) return rx;
        getLocationOnScreen(tmpLoc);
        return tmpLoc[0] + e.getX();
    }

    /**
     * [29.5b 必要适配] 按参数直接定位选中档（供命令行出图用），语义与我方版 selectImmediate 一致：
     * 只跳过动画、把位置/形状立刻摆正；不改任何视觉与运动参数。
     */
    public void selectImmediate(int i) {
        if (i < 0 || i >= TAB_COUNT) return;
        selectedIndex = i;
        for (int t = 0; t < TAB_COUNT; t++) { tabs[t].setSelected(t == i); tabs[t].updateDesc(); }
        curX = targetX = slotCenter(i);
        velX = 0f;
        animating = false;
        dragging = false;
        tapCandidate = false;
        updateDropletFrame();
        dropA = 0f; maskA = 1f; maskClock = 1e9f; applyMaskAlpha();   // 54：立刻到位＝停稳态（命令行出图用）
        if (listener != null) listener.onTabSelected(i);
    }

    /** 静默定位到某档：动画面、不回调（面板 showTab 同步用；点/拖引起的切换仍走 selectTab 并回调）。 */
    public void setSelectedIndex(int i) {
        if (i < 0 || i >= TAB_COUNT) return;
        selectTab(i, true, false);
    }

    /** 当前选中档（0 功能 / 1 配置 / 2 高级）。 */
    public int getSelectedIndex() { return selectedIndex; }

    private float dp(float v) { return v * density; }
    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
    private int dpRound(float v) { return Math.round(v * density); }
    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                getResources().getDisplayMetrics());
    }
    private static float clampF(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** 事件落点的条内 x 坐标：rawX 优先（与集成方的坐标兼容策略一致）。仅在 DOWN 时调用。 */
    private float parentXOf(MotionEvent e) {
        getLocationOnScreen(tmpLoc);
        return rawX(e) - tmpLoc[0];
    }

    /** 点当前档的轻微反馈：滴做一次小幅压缩回弹（复用按压小弹簧，无新增参数）。 */
    private void tapBounce() {
        pressScale = 0.97f;
        pressVel = 0f;
        pressTarget = 1f;
        startTicking();
        kickStretch();                             // 33 探路：轻点 → 条体也走同一套皮筋节奏
    }

    /** 33 探路：让某个 tab 的图标转一圈＋放大（33.3 起放大夹在条体内、不再越出）。 */
    void pulseIcon(int i) {
        if (i < 0 || i >= TAB_COUNT) return;
        lastPulseSlot = i;
        tabs[i].animT = 0f;
        tabs[i].invalidate();
        startTicking();
    }

    /** 33 探路：给条体皮筋一次速度冲量（轻点／松手；弹簧回零时会过冲）。 */
    private void kickStretch() {
        stretchVel += FX_KICK_V;
        startTicking();
    }


    private static float easeOutCubic(float x) {
        float u = 1f - x;
        return 1f - u * u * u;
    }

    /**
     * 33.1：把一个图标按动效进度画出来（转满 360° ＋ 放大）。
     * 原色层（TabButton）与紫色覆盖层（PurpleOverlay）共用本方法 → 两层严丝合缝，看着就是"原来那个图标在动"。
     * 33.3（A 方案）：撤掉"向上越出条体"——放大峰值自动夹在条体内（图标上沿不越过条体顶边、不留越界脏块）。
     */
    private void drawIconAnimated(Canvas canvas, Drawable d, Rect b, float t) {
        float e = t > 1f ? 1f : t;
        float spin = FX_SPIN * easeOutCubic(e);
        float bell = (float) Math.sin(Math.PI * e);
        float sc = 1f + (iconPeakV - 1f) * bell;
        float cx = b.exactCenterX(), cy = b.exactCenterY();
        float half = b.height() * 0.5f;
        float allow = half > 0f ? (cy / half) : 1f;   // 图标上沿贴到条体顶边时的最大缩放
        if (allow < 1f) allow = 1f;
        if (sc > allow) sc = allow;
        canvas.save();
        canvas.rotate(spin, cx, cy);
        canvas.scale(sc, sc, cx, cy);
        d.draw(canvas);
        canvas.restore();
    }

    /** 33 单：弹跳幅度（条体横向拉伸峰值），配置页滑杆用。 */
    public void setStretchAmp(float a) {
        if (a < 0.02f) a = 0.02f;
        if (a > 0.30f) a = 0.30f;
        stretchAmpV = a;
    }

    public float getStretchAmp() { return stretchAmpV; }

    /** 33 单：扩张幅度（图标放大峰值），配置页滑杆用。 */
    public void setIconPeak(float p) {
        if (p < 1.0f) p = 1.0f;
        if (p > 2.6f) p = 2.6f;
        iconPeakV = p;
    }

    public float getIconPeak() { return iconPeakV; }

    /** 33 单：图标动效时长（毫秒），配置页滑杆用。 */
    public void setIconMs(float ms) {
        if (ms < 520f) ms = 520f;
        if (ms > 1200f) ms = 1200f;
        iconMsV = ms;
    }

    public float getIconMs() { return iconMsV; }

    // ================================ 内部类 ================================

    /** 单个 tab：真实可点 View，绘制原色图标+文字，承担无障碍。 */
    private final class TabButton extends View {
        final int index;
        Drawable icon;
        final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Rect iconBounds = new Rect();
        float labelX, labelBaseline;
        boolean geometrySet;
        float animT = -1f;                 // 33 探路：图标动效进度（-1 = 空闲）

        TabButton(Context c, int i) {
            super(c);
            index = i;
            icon = ContextCompat.getDrawable(c, TAB_ICONS[i]);
            if (icon != null) icon = icon.mutate();
            labelPaint.setTextAlign(Paint.Align.CENTER);
            labelPaint.setTextSize(sp(LABEL_SIZE_SP));
            setClickable(true);
            setFocusable(true);
            updateDesc();
            setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) { onTabClicked(index); }
            });
        }

        void setContentColor(int color) {
            if (icon != null) DrawableCompat.setTint(icon, color);
            labelPaint.setColor(color);
            invalidate();
        }

        void updateDesc() {
            setContentDescription(TAB_LABELS[index] + (isSelected() ? Words.NAV_SELECTED : ""));
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            int iconPx = dpRound(ICON_SIZE_DP);
            int gap = dpRound(LABEL_GAP_DP);
            Paint.FontMetrics fm = labelPaint.getFontMetrics();
            int textH = (int) Math.ceil(fm.descent - fm.ascent);
            int group = iconPx + gap + textH;
            int top = Math.max(0, (h - group) / 2);
            int left = (w - iconPx) / 2;
            iconBounds.set(left, top, left + iconPx, top + iconPx);
            if (icon != null) icon.setBounds(iconBounds);
            labelX = w / 2f;
            labelBaseline = top + iconPx + gap - fm.ascent;
            geometrySet = true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (!geometrySet) return;
            if (icon != null) {
                if (animT < 0f) icon.draw(canvas);          // 常态：原样
                else drawIconAnimated(canvas, icon, iconBounds, animT);
            }
            canvas.drawText(TAB_LABELS[index], labelX, labelBaseline, labelPaint);
        }
    }

    /** 49.5-2／54：灰蒙版 —— 普通 View（无玻璃／模糊／染色），按滴形裁剪，只画极淡填充（54 起去描边、看不出边界）。
     *  停住后由它承接滴的形体：滴下的图标文字透过它仍然完全清晰（这正是本单要的效果）。
     *  形状每帧由 updateDropletFrame() 推来（与滴同形同位），不参与任何光学管线。 */
    private final class DropMask extends View {
        final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();                  // Path 复用，不逐帧分配
        final RectF shape = new RectF();               // 形状逐帧在变，拷一份
        float radius;
        boolean hasShape;

        DropMask(Context c) {
            super(c);
            fillPaint.setStyle(Paint.Style.FILL);
            setClickable(false);                       // 触摸穿透（真滴的监听器仍在最顶接着）
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        /** 54 号单 A：只有填充、没有描边（ink＝argb 墨色，fillA＝0~1 不透明度）。 */
        void setInk(int ink, float fillA) {
            fillPaint.setColor(ink);
            fillPaint.setAlpha(Math.round(255f * fillA));
            invalidate();
        }

        void setShape(RectF r, float radiusPx) {
            shape.set(r);
            radius = radiusPx;
            hasShape = true;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (!hasShape) return;
            path.reset();
            path.addRoundRect(shape, radius, radius, Path.Direction.CW);
            canvas.drawPath(path, fillPaint);
        }
    }

    /** 紫色覆盖层：画全部 3 个 tab 的紫色版，每帧被滴形状 clipPath 裁剪；位于滴玻璃之下，随滴被折射。 */
    private final class PurpleOverlay extends View {
        final Drawable[] icons = new Drawable[TAB_COUNT];
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path clipPath = new Path();
        final RectF clipRect = new RectF();
        final Rect[] iconBounds = new Rect[TAB_COUNT];
        final float[] labelXs = new float[TAB_COUNT];
        final float[] labelBaselines = new float[TAB_COUNT];
        float clipRadius;
        boolean hasClip, geometrySet;

        PurpleOverlay(Context c) {
            super(c);
            for (int i = 0; i < TAB_COUNT; i++) {
                icons[i] = ContextCompat.getDrawable(c, TAB_ICONS[i]);
                if (icons[i] != null) icons[i] = icons[i].mutate();
                iconBounds[i] = new Rect();
            }
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(sp(LABEL_SIZE_SP));
            setClickable(false);                               // 触摸穿透到 tab 按钮
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setPurple(int color) {
            for (int i = 0; i < TAB_COUNT; i++) {
                if (icons[i] != null) DrawableCompat.setTint(icons[i], color);
            }
            paint.setColor(color);
            invalidate();
        }

        void setClip(RectF r, float radius) {
            clipRect.set(r);
            clipRadius = radius;
            hasClip = true;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            int sw = w / TAB_COUNT;                            // 与 onLayout 的槽位划分保持一致
            int iconPx = dpRound(ICON_SIZE_DP);
            int gap = dpRound(LABEL_GAP_DP);
            Paint.FontMetrics fm = paint.getFontMetrics();
            int textH = (int) Math.ceil(fm.descent - fm.ascent);
            int group = iconPx + gap + textH;
            int top = Math.max(0, (h - group) / 2);
            for (int i = 0; i < TAB_COUNT; i++) {
                int slotL = i * sw;
                int left = slotL + (sw - iconPx) / 2;
                iconBounds[i].set(left, top, left + iconPx, top + iconPx);
                if (icons[i] != null) icons[i].setBounds(iconBounds[i]);
                labelXs[i] = slotL + sw / 2f;
                labelBaselines[i] = top + iconPx + gap - fm.ascent;
            }
            geometrySet = true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (!hasClip || !geometrySet) return;
            canvas.save();
            clipPath.reset();                                  // Path 复用，无逐帧分配
            clipPath.addRoundRect(clipRect, clipRadius, clipRadius, Path.Direction.CW);
            canvas.clipPath(clipPath);
            for (int i = 0; i < TAB_COUNT; i++) {
                if (icons[i] != null) {
                    float at = tabs[i].animT;   // 33.1：紫层与原色层用同一变换 → 不再像"两个图标"
                    if (at < 0f) icons[i].draw(canvas);
                    else drawIconAnimated(canvas, icons[i], iconBounds[i], at);
                }
                canvas.drawText(TAB_LABELS[i], labelXs[i], labelBaselines[i], paint);
            }
            canvas.restore();
        }
    }
}

/* ═══════════════════════════════ 需求对照（1~9）═══════════════════════════════
 * 1 悬浮胶囊：条体 cornerRadius=高/2 + enableShadow；滴 restH=条高、上下左右对称，
 *   仅拖动/按压时形变（s=1 when v=0）；两侧留白/底部安全区由布局 margin 给（见下）。
 * 2 通透：REGULAR 材质 + 小模糊（条 10dp / 滴 6dp）+ 染色 alpha（49.5：滴那档 45%→8%，摘掉白纱）。
 * 3 边缘光学：falloff≥2 的逆幂剖面把弯折挤进最外几像素（压缩环）+ 色散开；并显式
 *   setAdaptiveLensScale(false)，防止短边<110dp 时折射被按平方削没。
 * 4 覆盖变色：TabButton 画原色层，PurpleOverlay 画紫色层（浅 #8E62E8 / 深·极夜 #D0BCFF），
 *   每帧用滴当前形状 clipPath 裁剪；紫层在滴玻璃之下，随滴一起被折射（同参照图拖动态）。
 * 5 拖拽 Q 弹：单一弹簧驱动位置；拉伸/压缩由弹簧速度推导 ⇒ 形变归零与到位严格同刻；
 *   ζ∈{0.60,0.68,0.70} 带过冲；松手初速由 VelocityTracker 连续交接；吸附最近 tab。
 * 6 切换：点 tab（监听器内轻点判定 / performClick→onTabClicked）或拖滴松手（slotOf）均回调
 *   onTabSelected，同一根弹簧滑行动画带液态拉伸；点当前档无操作，仅滴做轻微回弹。
 * 7 三态：浅色=白玻璃 / 深色=烟灰 / 极夜=近黑+弱描边高光+关投影（见 applyTheme）。
 * 8 无障碍：TabButton 保持可点可聚焦、OnClickListener 保留（TalkBack performClick 可切换）、
 *   setSelected 真实、contentDescription「功能，已选中」式；条/滴/紫层全部 IMPORTANT_FOR_ACCESSIBILITY_NO。
 * 9 陷阱：条用 setBackdropSource 指页面根（否则只剩空染色）；滴采样直接父容器且始终被
 *   夹在条内（含最右档），不会采空；REGULAR 非 CLEAR；adaptiveLensScale 关闭；
 *   enableDynamicBackground(true) 两个玻璃都设了。
 * ═══════════════════════════════ 接入与调参（3 行）═══════════════════════════════
 * 1) 把本 View 叠在内容上（FrameLayout/CoordinatorLayout）：宽 match_parent、高 64dp，
 *    layout_gravity=bottom，左右 margin 16dp，底 margin = 系统导航 insets + 8dp。
 * 2) nav.setBackdropSource(整页根布局或 android.R.id.content)；nav.setListener(...)。
 * 3) setThemeMode(0/1/2) 切浅/深/极夜；setParamSet(0/1/2) 对比预设，改顶部常量即时生效。
 * ═══════════════════ 我假设存在、但清单未明确语义的 API ═══════════════════════════
 * · setPrimaryShape(RectF,float)：假定坐标为 View 本地 px、第二参为圆角 px。
 *   若实际是全局/归一化坐标，只需改 updateDropletFrame() 一处。
 * · setDispersionStrength / setAberrationIntensity：假定 0~1 归一化；若实为 px 或 0~100，
 *   请等比缩放 PS_DISPERSION / PS_ABERR。
 * · setBlurAmount：假定为 px 半径（与库默认 bevel=48px 同量级）；本类按 dp→px 传入。
 * · setGlassTint(int)：特意用单参 argb 版本，避开双参版 alpha 量纲歧义。
 * · 库类包名 com.example.liquidglass（llms.txt 已确认）；枚举同包假定。
 * · 触摸行为：v2.0.11 的 LiquidGlassView.onTouchEvent 无条件消费触摸（贵方实机定位结论）。
 *   本版据此让 droplet 监听器消费整条手势流（return true 后事件到不了库的 onTouchEvent）；
 *   若未来版本库改为不消费，本逻辑依然成立（监听器先消费，行为不变）。
 * ═══════════════════════════════ 我不确定的地方 ══════════════════════════════════
 * · setSecondaryShape(RectF,float,float) 第三参语义（融合半径？）llms.txt 未说明 → 未使用；
 *   液态拉伸由单 RectF 等体积缩放实现。若它支持双圆融合（metaball），拖尾质感可更进一步。
 * · setElasticity / setDisplacementScale / setPressScale 是库内置交互参数，为避免与自研
 *   弹簧叠加已全部弃用（setEnablePressEffect(false)）。
 * · 色散/色差只在「有细节的背景」上可见（库特性）；纯黑极夜背景下彩边天然偏弱属正常。
 * · API 24–32 无 AGSL：setPrimaryShape 静默失效，本类自动切「view 边界模式」——滴仍是
 *   可拖拽的圆角模糊透镜（带色差彩边），但无真折射；视觉上比 33+ 平一些，属预期降级。
 * · LiquidGlassTabBar（库自带 iOS26 标签条）未使用：接口要求自组导航条。
 * · 本次修复为纯触摸逻辑，我处无 Android 实机/模拟器，未做运行时验证；建议回归五条用例：
 *   点击切换 / 滴内拖拽 / 滴外横向召唤拖 / 纵向滑忽略 / 连续快点 + TalkBack performClick。
 */
