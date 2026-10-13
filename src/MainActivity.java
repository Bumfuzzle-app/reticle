package app.reticle;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Reticle 主界面 v2（26 号单 · Material 3 重建）
 * 30 号单（主题与极夜修正）：
 *   · 主题三套固定（浅色 / 深色 / 极夜），「跟随系统」在运行期选其中一套；切换主题不再 recreate，
 *     改为 setTheme + 就地重建视图树 → 无 handleRelaunchActivity、无黑↔白闪、当前页与滚动位置保持。
 *   · 极夜只在「深色生效」时应用（跟随系统＝浅色时绝不允许全黑）；极夜是记忆开关，只有用户自己能关。
 *   · 底部导航按系统 Insets 留安全区（手势条／三大金刚都不重叠）；顶部大标题加大下移，避开状态栏。
 * 结构：顶部大标题 → 分区小节 → 两列卡片网格 / 列表式条目 → 底部导航（功能·配置·高级）
 * 主题：跟随系统 / 浅色 / 深色；深色下可再开「极夜」（纯黑）
 * 点击反馈：卡片与列表项整条 ripple（M3 state layer）
 */
public class MainActivity extends AppCompatActivity {

    static final String PREFS = "settings";
    static final String K_THEME = "theme_mode";   // 0 跟随系统 / 1 浅色 / 2 深色
    static final String K_BLACK = "black_night";  // 极夜（纯黑）
    static final String Q = Character.toString((char) 34);

    FrameLayout root;                             // 29 号单：页面根＝玻璃条的背景采样源
    LinearLayout topBarView;
    LinearLayout contentCol;                      // 顶部标题＋内容宿主（玻璃条浮在它上面）
    GlassyNavBar navGlass;                        // 29 号单：底部导航（液态玻璃·悬浮胶囊）
    LinearLayout cFunc, cConf, cAdv;              // 三页内容列（底部留白随 insets／悬浮条让位）
    FrameLayout host;
    LinearLayout pageFunc, pageConf, pageAdv;
    ScrollView svFunc, svConf, svAdv;
    TextView sMcpV, sA11yV, sShzV, sNotiV, tvWd;
    TextView tvOut, tvTools, tvLog;
    TextView tvAddr, tvHName;   // 51 号单：配置页「服务地址」卡上的地址与请求头名称（换端口/改头名后跟着变）
    TextView tvMem;            // 52 号单：配置页「内存自保」当前档位那一行
    TextView tvA11yHint;       // 53 号单：无障碍「在册但没接上」时的一句人话指引（常态隐藏）
    View a11yFixBox;           // 53 号单：「修一次（重连无障碍）」按钮块（已连接时隐藏）
    final Handler ui = new Handler(Looper.getMainLooper());
    int tab = 0;
    private boolean suppressCheck = false;        // 程序性恢复分段选中时，先屏蔽回调
    private int appliedThemeRes = 0;              // 当前界面实际用的主题资源（onConfigurationChanged 比对用）
    private final int[] savedScroll = new int[]{0, 0, 0};
    private int sbTop = 0, sbBottom = 0;          // 系统栏高度（像素）
    private boolean firstInset = true;
    TextView titleView;                           // 32 号单：顶部大标题（可点翻转 Reticle ⇄ beta）
    private boolean titleBeta = false;            // 32 号单：标题当前是否是 beta 面
    private boolean titleFlipping = false;        // 32 号单：翻转动画进行中（防连点乱套）
    private boolean suppressTabAnim = false;      // 32 号单：本次 showTab 不播滑动动画（首次进入／主题就地重建）

    @Override protected void onCreate(Bundle b) {
        setTheme(themeRes());
        appliedThemeRes = themeRes();
        super.onCreate(b);
        try { WindowCompat.setDecorFitsSystemWindows(getWindow(), false); } catch (Throwable t) { }
        try { McpServer.start(this); } catch (Throwable t) { }
        try { A11yWatchdog.start(this); } catch (Throwable t) { }
        // 52 号单：手动打开 App ＝「用户已停」到此为止（清掉标志 → 功能恢复）
        try { WatchdogService.setUserStopped(this, false); } catch (Throwable t) { }
        try { MemGuard.start(this); } catch (Throwable t) { }
        try { WatchdogService.ensure(this); } catch (Throwable t) { }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 17);
            }
        } catch (Throwable t) { }

        buildRoot();
        setContentView(root);
        applySystemBarAppearance();
        // 29 号单：命令行直达（一键验／回归脚本用）—— am start … --ei tab 2 [--ei navparam 1]
        int startTab = getSharedPreferences(PREFS, MODE_PRIVATE).getInt("last_tab", 0);
        try {
            if (b == null && getIntent() != null) {
                startTab = getIntent().getIntExtra("tab", startTab);
                int np = getIntent().getIntExtra("navparam", -1);
                if (np >= 0 && np <= 2) setNavParam(np);
            }
        } catch (Throwable t) { }
        suppressTabAnim = true;   // 32 号单：首次进入不播滑动动画
        showTab(startTab);
        try { if (!OnboardActivity.done(this)) OnboardActivity.open(this); } catch (Throwable t) { }
    }

    @Override protected void onResume() { super.onResume(); try { refresh(); } catch (Throwable t) { } }

    @Override public void onConfigurationChanged(Configuration nc) {
        super.onConfigurationChanged(nc);
        // 跟随系统时系统深浅变了 → 就地换主题（不重建 Activity）
        if (themeRes() != appliedThemeRes) {
            applyThemeNow();
        } else {
            applySystemBarAppearance();
            if (root != null) root.requestApplyInsets();
        }
    }

    // ───────── 界面骨架 ─────────
    void buildRoot() {
        root = new FrameLayout(this);
        root.setBackgroundColor(col(android.R.attr.colorBackground, 0xFF101418));

        // —— 内容列：顶部大标题 ＋ 页面宿主（玻璃条浮在它上面，条能看见背后的内容）——
        contentCol = new LinearLayout(this);
        contentCol.setOrientation(LinearLayout.VERTICAL);
        topBarView = (LinearLayout) topBar();
        contentCol.addView(topBarView);

        host = new FrameLayout(this);
        host.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        contentCol.addView(host);
        root.addView(contentCol, new FrameLayout.LayoutParams(-1, -1));

        pageFunc = column(); pageConf = column(); pageAdv = column();
        int pageBg = col(android.R.attr.colorBackground, 0xFF101418);   // 32 号单：页面不透明，切页滑入时才盖得住旧页
        pageFunc.setBackgroundColor(pageBg); pageConf.setBackgroundColor(pageBg); pageAdv.setBackgroundColor(pageBg);
        svFunc = null; svConf = null; svAdv = null;
        buildFunc();
        buildConf();
        buildAdv();

        // —— 底部导航：29 号单换成「液态玻璃悬浮胶囊」（K3 外援版，已在探路包实机验收）——
        navGlass = new GlassyNavBar(this);
        navGlass.setLayoutParams(navGlassLp());
        navGlass.setParamSet(navParam());
        navGlass.setThemeMode(glassTheme());
        navGlass.setBackdropSource(root);
        navGlass.setListener(new GlassyNavBar.Listener() {
            public void onTabSelected(int index) { showTab(index); }
        });
        applyNavFx();               // 33 号单：把配置页滑杆的动效参数（弹跳／扩张／时长）套到导航条上
        root.addView(navGlass);

        applyInsetsPad();
        hookInsets();
    }

    /** 30 号单：顶部标题避开状态栏；29 号单：导航改成悬浮胶囊 → 安全区落在「条的底边距」上，
     *  并且给页面底部留出同高的让位，内容末行不会被条压住（条压住的地方正是要"透过玻璃"看的内容）。 */
    void applyInsetsPad() {
        if (topBarView != null) topBarView.setPadding(dp(20), dp(24) + sbTop, dp(20), dp(8));
        if (navGlass != null) navGlass.setLayoutParams(navGlassLp());
        if (cFunc != null) cFunc.setPadding(dp(18), dp(6), dp(18), pagePadBottom());
        if (cConf != null) cConf.setPadding(dp(18), dp(6), dp(18), pagePadBottom());
        if (cAdv != null) cAdv.setPadding(dp(18), dp(6), dp(18), pagePadBottom());
    }

    /** 悬浮玻璃条：左右各留 16dp、离屏底＝系统 insets ＋ 8dp、高 64dp（左右上下对称的胶囊） */
    FrameLayout.LayoutParams navGlassLp() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(64), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        lp.bottomMargin = dp(8) + sbBottom;
        return lp;
    }

    /** 页面底部留白：条高 64 ＋ 离屏底 8 ＋ 与条再留 8（＋ 底部 insets） */
    int pagePadBottom() { return dp(28) + dp(80) + sbBottom; }

    void hookInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root, new OnApplyWindowInsetsListener() {
            public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat ins) {
                try {
                    Insets bars = ins.getInsets(WindowInsetsCompat.Type.systemBars());
                    if (firstInset || bars.top != sbTop || bars.bottom != sbBottom) {
                        firstInset = false;
                        sbTop = bars.top; sbBottom = bars.bottom;
                        applyInsetsPad();
                    }
                } catch (Throwable t) { }
                return ins;
            }
        });
        root.requestApplyInsets();
    }

    void applySystemBarAppearance() {
        try {
            WindowInsetsControllerCompat wic = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (wic != null) {
                boolean night = nightActive();
                wic.setAppearanceLightStatusBars(!night);
                wic.setAppearanceLightNavigationBars(!night);
            }
        } catch (Throwable t) { }
    }

    // ───────── 主题（30 号单重写）─────────
    // 三套固定主题：浅色 / 深色 / 极夜；「跟随系统」在运行期选其中一套。
    // 不再调用 AppCompatDelegate.setDefaultNightMode()（它必然导致 Activity 重建 → 闪烁 + 滚动回顶）。
    int themeRes() {
        if (!nightActive()) return R.style.Theme_Reticle;
        return blackOn() ? R.style.Theme_Reticle_Black : R.style.Theme_Reticle_Dark;
    }

    boolean isSystemNight() {
        try {
            Context app = getApplicationContext();
            if (app == null) app = this;
            int m = app.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return m == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable t) { return false; }
    }

    /** 极夜只在「深色生效」时才应用：跟随系统＝浅色时绝不允许全黑 */
    boolean nightActive() {
        int mode = themeMode();
        if (mode == 2) return true;
        if (mode == 1) return false;
        return isSystemNight();
    }

    int themeMode() { return getSharedPreferences(PREFS, MODE_PRIVATE).getInt(K_THEME, 0); }
    boolean blackOn() { return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(K_BLACK, false); }

    void setThemeMode(int m) {
        if (m == themeMode()) return;   // 值未变就不动（防「重建→恢复选中→再重建」的循环）
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(K_THEME, m).apply();
        applyThemeNow();
    }

    /** 极夜＝记忆开关：开了就一直留着（深色下永远是极夜），直到用户自己关掉；绝不因切主题复位 */
    void setBlack(boolean v) {
        if (v == blackOn()) return;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(K_BLACK, v).apply();
        applyThemeNow();
    }

    /** 35 号单：配置页「重跑一遍引导」的副标题——「所有文件访问」没开时这里直接提醒（引导可跳过，但跳过要有入口）。 */
    String guideSub() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager())
                return Words.CF_GUIDE_D_TODO;
        } catch (Throwable t) { }
        return Words.CF_GUIDE_D;
    }

    /** 就地换主题：不 recreate，只重建视图树 → 保留当前页与滚动位置，无黑↔白闪 */
    void applyThemeNow() {
        saveScroll();
        setTheme(themeRes());
        appliedThemeRes = themeRes();
        buildRoot();
        setContentView(root);
        applySystemBarAppearance();
        suppressTabAnim = true;   // 32 号单：主题就地重建不播滑动动画
        showTab(tab);
        restoreScroll();
        try { refresh(); } catch (Throwable t) { }
    }

    ScrollView scrollOf(int idx) { return idx == 0 ? svFunc : (idx == 1 ? svConf : svAdv); }

    void saveScroll() {
        for (int i = 0; i < 3; i++) {
            ScrollView s = scrollOf(i);
            if (s != null) savedScroll[i] = s.getScrollY();
        }
    }

    void restoreScroll() {
        final ScrollView s = scrollOf(tab);
        if (s == null) return;
        final int y = savedScroll[tab];
        s.post(new Runnable() { public void run() { try { s.scrollTo(0, y); } catch (Throwable t) { } } });
    }

    View topBar() {
        LinearLayout w = new LinearLayout(this);
        w.setOrientation(LinearLayout.VERTICAL);
        w.setPadding(dp(20), dp(24), dp(20), dp(8));   // 顶部再叠加状态栏高度，见 applyInsetsPad
        // 32 号单：标题放大到 42，可点 → 翻牌切换 Reticle ⇄ beta
        final TextView t = txt(Words.MA_TITLE_A, 42, col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6));
        t.setTypeface(null, Typeface.BOLD);
        t.setClickable(true);
        t.setFocusable(true);
        t.setContentDescription(Words.MA_TITLE_DESC_A);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { flipTitle(); }
        });
        titleView = t;
        titleBeta = false;
        titleFlipping = false;
        // 45 号单：标题这一行＝大标题 ＋ 右侧圆形「?」（点进详细说明页）
        LinearLayout trow = new LinearLayout(this);
        trow.setOrientation(LinearLayout.HORIZONTAL);
        trow.setGravity(Gravity.CENTER_VERTICAL);
        trow.addView(t);
        trow.addView(new View(this), new LinearLayout.LayoutParams(0, dp(1), 1f));   // ★弹簧必须钉死高度：裸 View 在 AT_MOST 下会吃满父高（45 单踩过，见 手艺与红线）
        trow.addView(helpButton());
        w.addView(trow);

        lockTitleSize(t);   // 32.1：钉死标题宽高（两面字号不同，否则翻牌会顶动下面的内容）
        TextView s = txt("v" + ver() + Words.MA_SUB_TAIL, 13, colVariant());
        s.setPadding(0, dp(6), 0, 0);
        w.addView(s);
        return w;
    }

    /** 32 号单：点标题翻牌 —— 0°→90° 转走、换字、-90°→0° 转来；全程只见正面，不出现镜像字 */
    void flipTitle() {
        final TextView t = titleView;
        if (t == null || titleFlipping) return;
        titleFlipping = true;
        final boolean toBeta = !titleBeta;
        try { t.animate().cancel(); } catch (Throwable e) { }
        t.setRotationY(0f);
        t.animate().rotationY(90f).setDuration(110)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(new Runnable() { public void run() {
                    applyTitleFace(toBeta);
                    t.setRotationY(-90f);
                    t.animate().rotationY(0f).setDuration(130)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .withEndAction(new Runnable() { public void run() {
                                try {
                                    if (t == titleView) { t.setRotationY(0f); titleBeta = toBeta; titleFlipping = false; }
                                } catch (Throwable e) { }
                            } }).start();
                } }).start();
    }

    /** 32 号单：标题两个面的字（beta 用主色、略小一号、保持 BOLD） */
    void applyTitleFace(boolean beta) {
        final TextView t = titleView;
        if (t == null) return;
        if (beta) {
            t.setText(Words.MA_TITLE_B);
            t.setTextSize(36);
            t.setTextColor(col(com.google.android.material.R.attr.colorPrimary, 0xFF6C8CFF));
            t.setContentDescription(Words.MA_TITLE_DESC_B);
        } else {
            t.setText(Words.MA_TITLE_A);
            t.setTextSize(42);
            t.setTextColor(col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6));
            t.setContentDescription(Words.MA_TITLE_DESC_A);
        }
        t.setTypeface(null, Typeface.BOLD);
    }

    /** 32.1 修正：两面字号不同 → 先量出两面的最大宽高把标题钉死；翻牌时下方内容不跳、旋转中心也不漂 */
    void lockTitleSize(final TextView t) {
        t.post(new Runnable() { public void run() {
            try {
                if (t != titleView) return;
                int specW = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                int specH = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                int wMax = 0, hMax = 0;
                for (int i = 0; i < 2; i++) {
                    TextView probe = txt(i == 0 ? Words.MA_TITLE_A : Words.MA_TITLE_B, i == 0 ? 42 : 36, 0xFF000000);
                    probe.setTypeface(null, Typeface.BOLD);
                    probe.measure(specW, specH);
                    wMax = Math.max(wMax, probe.getMeasuredWidth());
                    hMax = Math.max(hMax, probe.getMeasuredHeight());
                }
                t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                t.setWidth(wMax + dp(2));
                t.setHeight(hMax);
            } catch (Throwable e) { }
        } });
    }

    /** 45 号单：大标题右边的圆形「?」——点一下进「详细说明」（目录＋章节，见 Manual.java / ManualActivity） */
    View helpButton() {
        final int pc = col(com.google.android.material.R.attr.colorPrimary, 0xFF6C8CFF);
        ImageView b = new ImageView(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor((pc & 0x00FFFFFF) | 0x22000000);          // 主色淡淡的圆底
        g.setStroke(Math.max(1, dp(1)), (pc & 0x00FFFFFF) | 0x99000000);
        b.setBackground(g);
        b.setImageResource(R.drawable.ic_help);
        b.setImageTintList(ColorStateList.valueOf(pc));
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int s = dp(36);
        b.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        int p = dp(8);
        b.setPadding(p, p, p, p);
        b.setClickable(true);
        b.setFocusable(true);
        b.setContentDescription(Words.MA_HELP_DESC);
        ripple(b);                                          // 按压反馈（前景层，不动圆底）
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { ManualActivity.open(MainActivity.this); }
        });
        return b;
    }

    // ───────── 功能页 ─────────
    void buildFunc() {
        LinearLayout c = column();
        c.setPadding(dp(18), dp(6), dp(18), pagePadBottom());

        cFunc = c;
        c.addView(sectionTitle(Words.MA_SEC_STATUS));
        LinearLayout r1 = gridRow();
        r1.addView(statTile(R.drawable.ic_stat_mcp, Words.MA_STAT_MCP, 0), gridLp(true));
        r1.addView(statTile(R.drawable.ic_stat_a11y, Words.MA_STAT_A11Y, 1), gridLp(false));
        c.addView(r1);
        LinearLayout r2 = gridRow();
        r2.addView(statTile(R.drawable.ic_stat_shz, Words.MA_STAT_SHZ, 2), gridLp(true));
        r2.addView(statTile(R.drawable.ic_stat_noti, Words.MA_STAT_NOTI, 3), gridLp(false));
        c.addView(r2);
        c.addView(quiet(Words.MA_HINT_TAP_CARD));

        // ── 53 号单：无障碍「在册 ≠ 能用」—— 没接上时给一句人话 ＋ 一键修复（常态隐藏） ──
        tvA11yHint = txt("", 12, col(com.google.android.material.R.attr.colorTertiary, 0xFFE9A23B));
        tvA11yHint.setPadding(dp(4), dp(8), dp(4), 0);
        tvA11yHint.setLineSpacing(dp(3), 1f);
        tvA11yHint.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        tvA11yHint.setVisibility(View.GONE);
        c.addView(tvA11yHint);
        a11yFixBox = confBtn(Words.MA_BTN_A11Y_FIX, Words.MA_BTN_A11Y_FIX_D, new Runnable() { public void run() { fixA11y(); } });
        a11yFixBox.setVisibility(View.GONE);
        c.addView(a11yFixBox);

        c.addView(sectionTitle(Words.MA_SEC_TOOLS));
        LinearLayout g1 = gridRow();
        g1.addView(tile(R.drawable.ic_tile_check, Words.MA_TILE_SELFTEST, Words.MA_TILE_SELFTEST_D, new Runnable() { public void run() { selfTest(); } }), gridLp(true));
        g1.addView(tile(R.drawable.ic_tile_power, Words.MA_TILE_RESTART, Words.MA_TILE_RESTART_D, new Runnable() { public void run() { restart(); } }), gridLp(false));
        c.addView(g1);
        LinearLayout g2 = gridRow();
        g2.addView(tile(R.drawable.ic_tile_refresh, Words.MA_TILE_REFRESH, Words.MA_TILE_REFRESH_D, new Runnable() { public void run() { refresh(); toast(Words.MA_MSG_REFRESHED); } }), gridLp(true));
        g2.addView(tile(R.drawable.ic_tile_open, Words.MA_TILE_A11Y_SET, Words.MA_TILE_A11Y_SET_D, new Runnable() { public void run() { openA11y(); } }), gridLp(false));
        c.addView(g2);

        tvOut = txt("", 13, colVariant());
        tvOut.setPadding(dp(4), dp(12), dp(4), dp(4));
        c.addView(tvOut);

        ScrollView sv = new ScrollView(this);
        sv.addView(c);
        pageFunc.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        svFunc = sv;
    }

    View statTile(int iconRes, String label, int which) {
        MaterialCardView c = card(true);
        final int w = which;
        c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { refresh(); toast(Words.MA_MSG_STATUS_REFRESHED); } });
        LinearLayout in = new LinearLayout(this);
        in.setOrientation(LinearLayout.VERTICAL);
        in.setPadding(dp(14), dp(14), dp(14), dp(14));
        ImageView ic = new ImageView(this);
        ic.setImageResource(iconRes);
        ic.setImageTintList(ColorStateList.valueOf(col(com.google.android.material.R.attr.colorPrimary, 0xFF6C8CFF)));
        ic.setLayoutParams(new LinearLayout.LayoutParams(dp(20), dp(20)));
        in.addView(ic);
        TextView l = txt(label, 12, colVariant());
        l.setPadding(0, dp(8), 0, 0);
        in.addView(l);
        TextView v = txt("…", 16, col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6));
        v.setTypeface(null, Typeface.BOLD);
        v.setPadding(0, dp(2), 0, 0);
        in.addView(v);
        if (w == 0) sMcpV = v; else if (w == 1) sA11yV = v; else if (w == 2) sShzV = v; else sNotiV = v;
        c.addView(in);
        return c;
    }

    View addrCard() {
        MaterialCardView c = card(false);
        LinearLayout in = new LinearLayout(this);
        in.setOrientation(LinearLayout.VERTICAL);
        in.setPadding(dp(14), dp(14), dp(14), dp(14));
        in.addView(txt(Words.CF_ADDR_LINE + logTime(), 12, colVariant()));
        tvAddr = txt(fullUrl(), 14, col(com.google.android.material.R.attr.colorPrimary, 0xFF6C8CFF));
        in.addView(tvAddr);
        tvHName = txt(Words.CF_ADDR_HNAME + McpServer.getHeaderName(this), 12, colVariant());
        in.addView(tvHName);
        c.addView(in);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        c.setLayoutParams(lp);
        return c;
    }

    // ───────── 配置页 ─────────
    void buildConf() {
        LinearLayout c = column();
        c.setPadding(dp(18), dp(6), dp(18), pagePadBottom());

        cConf = c;
        c.addView(sectionTitle(Words.CF_SEC_GUIDE));
        c.addView(listItem(Words.CF_GUIDE_T, guideSub(), new Runnable() { public void run() { OnboardActivity.open(MainActivity.this); } }));
        c.addView(listItem(Words.CF_MIN_T, Words.CF_MIN_D, new Runnable() { public void run() { ManualActivity.openChapter(MainActivity.this, 0); } }));

        c.addView(sectionTitle(Words.CF_SEC_CONN));
        c.addView(quiet(Words.CF_CONN_LEAD));
        c.addView(quiet(Words.CF_CONN_OFFICIAL));
        c.addView(confBtn(Words.CF_BTN_OFFICIAL, Words.CF_HINT_OFFICIAL, new Runnable() { public void run() { copyOfficial(); } }));
        c.addView(confBtn(Words.CF_BTN_HNAME, Words.CF_HINT_HNAME, new Runnable() { public void run() { copyHName(); } }));
        c.addView(confBtn(Words.CF_BTN_HVALUE, Words.CF_HINT_HVALUE, new Runnable() { public void run() { copyHValue(); } }));
        c.addView(confBtn(Words.CF_BTN_URL, Words.CF_HINT_URL, new Runnable() { public void run() { copyUrl(); } }));

        // ── 51 号单：令牌与地址（讲清令牌是什么／泄露怎么办 ＋ 三个管理按钮） ──
        c.addView(sectionTitle(Words.CF_SEC_TOKEN));
        c.addView(quiet(Words.CF_TOKEN_WHAT));
        c.addView(quiet(Words.CF_TOKEN_HOW));
        c.addView(confBtn(Words.CF_BTN_TOKEN_NEW, Words.CF_HINT_TOKEN_NEW, new Runnable() { public void run() { confirmRegenToken(); } }));
        c.addView(confBtn(Words.CF_BTN_HNAME_SET, Words.CF_HINT_HNAME_SET, new Runnable() { public void run() { askHeaderName(); } }));
        c.addView(quiet(Words.CF_LINK_WHAT));
        c.addView(confBtn(Words.CF_BTN_PORT_NEW, Words.CF_HINT_PORT_NEW, new Runnable() { public void run() { askPort(); } }));

        c.addView(sectionTitle(Words.CF_SEC_ADDR));
        c.addView(addrCard());

        c.addView(sectionTitle(Words.CF_SEC_BEHAV));
        c.addView(switchItem(Words.CF_SW_VIB_T,
                Words.CF_SW_VIB_D,
                McpServer.vibrateThroughSilent(this), true, new SwitchCB() {
                    public void onChanged(boolean v) {
                        McpServer.setVibrateThroughSilent(MainActivity.this, v);
                        toast(v ? Words.CF_MSG_VIB_ON : Words.CF_MSG_VIB_OFF);
                    }
                }));
        c.addView(listItem(Words.CF_ITEM_NOTI_SET, Words.CF_ITEM_NOTI_SET_D, new Runnable() { public void run() {
            try {
                Intent i2 = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i2.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                i2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i2);
            } catch (Throwable t) { }
        } }));

        c.addView(sectionTitle(Words.CF_SEC_WD));
        c.addView(switchItem(Words.CF_SW_WD_T,
                Words.CF_SW_WD_D,
                A11yWatchdog.isOn(this), true, new SwitchCB() {
                    public void onChanged(boolean v) {
                        A11yWatchdog.setOn(MainActivity.this, v);
                        toast(v ? Words.CF_MSG_WD_ON : Words.CF_MSG_WD_OFF);
                        refresh();
                    }
                }));
        tvWd = quiet(A11yWatchdog.statusLine(this));
        c.addView(tvWd);
        c.addView(listItem(Words.CF_ITEM_WD_CHECK, Words.CF_ITEM_WD_CHECK_D, new Runnable() { public void run() { wdCheckNow(); } }));
        // ── 53 号单：无障碍「在册 ≠ 能用」—— 讲清两件事 ＋ 一键修复入口 ──
        c.addView(quiet(Words.CF_A11Y_LINK_LEAD));
        c.addView(confBtn(Words.MA_BTN_A11Y_FIX, Words.MA_BTN_A11Y_FIX_D, new Runnable() { public void run() { fixA11y(); } }));

        // ── 52 号单：开机自启（默认关；与上面的看门狗开关各自独立）──
        c.addView(switchItem(Words.CF_SW_BOOT_T,
                Words.CF_SW_BOOT_D,
                BootReceiver.isOn(this), true, new SwitchCB() {
                    public void onChanged(boolean v) {
                        BootReceiver.setOn(MainActivity.this, v);
                        toast(v ? Words.CF_MSG_BOOT_ON : Words.CF_MSG_BOOT_OFF);
                        refresh();
                    }
                }));
        c.addView(listItem(Words.CF_BOOT_GOTO_T, Words.CF_BOOT_GOTO_D, new Runnable() { public void run() {
            if (!BootReceiver.openAutoStart(MainActivity.this)) toast(Words.CF_BOOT_GOTO_FAIL);
        } }));
        // ── 52 号单：内存自保（只读一行：当前档位；自动升降，无需手动开关）──
        tvMem = quiet(Words.MEM_LINE_PREFIX + MemGuard.statusLine(this));
        c.addView(tvMem);

        // ── 外观（30 号单：极夜常显；非深色时置灰不可点，不隐藏）──
        c.addView(sectionTitle(Words.CF_SEC_LOOK));
        c.addView(themeChooser());
        final boolean night = nightActive();
        View blackRow = switchItem(Words.CF_BLACK_T,
                night ? Words.CF_BLACK_D_ON
                      : Words.CF_BLACK_D_OFF,
                blackOn(), night, new SwitchCB() {
                    public void onChanged(boolean v) { setBlack(v); }
                });
        c.addView(blackRow);

        c.addView(sectionTitle(Words.CF_NAV_SEC));
        c.addView(quiet(Words.CF_NAV_D));
        c.addView(navParamChooser());
        c.addView(fxSlider(Words.CF_MASK_ALPHA_T, Words.CF_MASK_ALPHA_D, 2, 20, 1, Math.round(maskAlpha() * 100), "%", new FXCB() {
            public void onChanged(int v) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putFloat(K_MASK_ALPHA, v / 100f).apply();
                if (navGlass != null) navGlass.setMaskAlpha(v / 100f);
            }
        }));
        c.addView(fxSlider(Words.CF_MASK_GAP_T, Words.CF_MASK_GAP_D, 0, 400, 10, maskGapMs(), " ms", new FXCB() {
            public void onChanged(int v) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(K_MASK_GAP, v).apply();
                if (navGlass != null) navGlass.setMaskGapMs(v);
            }
        }));


        c.addView(sectionTitle(Words.CF_SEC_FX));
        c.addView(quiet(Words.CF_FX_INTRO));
        c.addView(fxSlider(Words.CF_FX1_TITLE, Words.CF_FX1_HINT, 2, 30, 1, Math.round(fxAmp() * 100), Words.CF_FX1_UNIT, new FXCB() {
            public void onChanged(int v) {
                float a = v / 100f;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putFloat(K_FX_AMP, a).apply();
                if (navGlass != null) navGlass.setStretchAmp(a);
            }
        }));
        c.addView(fxSlider(Words.CF_FX2_TITLE, Words.CF_FX2_HINT, 100, 260, 5, Math.round(fxPeak() * 100), Words.CF_FX2_UNIT, new FXCB() {
            public void onChanged(int v) {
                float p = v / 100f;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putFloat(K_FX_PEAK, p).apply();
                if (navGlass != null) navGlass.setIconPeak(p);
            }
        }));
        c.addView(fxSlider(Words.CF_FX3_TITLE, Words.CF_FX3_HINT, 520, 1200, 20, Math.round(fxMs()), Words.CF_FX3_UNIT, new FXCB() {
            public void onChanged(int v) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putFloat(K_FX_MS, v).apply();
                if (navGlass != null) navGlass.setIconMs(v);
            }
        }));
        c.addView(listItem(Words.CF_ITEM_FX_RESET, Words.CF_ITEM_FX_RESET_D, new Runnable() { public void run() {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putFloat(K_FX_AMP, 0.12f).putFloat(K_FX_PEAK, 1.65f).putFloat(K_FX_MS, 640f).apply();
            applyNavFx();
            rebuildConfUi();
            toast(Words.CF_MSG_FX_RESET);
        } }));
        c.addView(sectionTitle(Words.CF_SEC_MAINT));
        c.addView(listItem(Words.CF_STOP_T, Words.CF_STOP_D, new Runnable() { public void run() { confirmStopAll(); } }));
        c.addView(quiet(Words.CF_STOP_NOTE));

        ScrollView sv = new ScrollView(this);
        sv.addView(c);
        pageConf.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        svConf = sv;
    }

    // ───────── 29 号单：底部导航（液态玻璃）参数与主题 ─────────
    static final String K_NAVPARAM = "navbar_param";       // 0 均衡 / 1 棱镜强 / 2 柔和更弹

    /** 现用的导航条参数组（默认 0 ＝「均衡」：折射与色散居中，既看得见玻璃、又不刺眼） */
    int navParam() { return getSharedPreferences(PREFS, MODE_PRIVATE).getInt(K_NAVPARAM, 0); }

    void setNavParam(int p) {
        if (p < 0 || p > 2 || p == navParam()) return;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(K_NAVPARAM, p).apply();
        if (navGlass != null) navGlass.setParamSet(p);
    }

    // ───────── 54 号单 A：蒙版两根滑杆（浓度／间隔；持久化，即时生效） ─────────
    static final String K_MASK_ALPHA = "nav_mask_alpha";   // 蒙版浓度 0.02~0.20
    static final String K_MASK_GAP   = "nav_mask_gap_ms";  // 滴→蒙版 间隔 0~400ms

    /** 54：蒙版浓度（默认＝GlassyNavBar 的 12%；滑条 2~20%） */
    float maskAlpha() { return getSharedPreferences(PREFS, MODE_PRIVATE).getFloat(K_MASK_ALPHA, GlassyNavBar.MASK_ALPHA_DEF); }
    /** 54：真滴淡出 → 蒙版淡入 的间隔（默认 120ms；滑条 0~400ms） */
    int maskGapMs() { return getSharedPreferences(PREFS, MODE_PRIVATE).getInt(K_MASK_GAP, GlassyNavBar.MASK_GAP_DEF_MS); }

    /** 玻璃条明暗跟主题走：浅色＝白玻璃 / 深色＝烟灰 / 极夜＝近黑（黑底上高光收弱） */
    int glassTheme() { if (!nightActive()) return 0; return blackOn() ? 2 : 1; }

    /** 导航条参数三选一（配置页「底部导航」；选择持久化） */
    View navParamChooser() {
        final MaterialButtonToggleGroup g = new MaterialButtonToggleGroup(this);
        g.setSingleSelection(true);
        g.setSelectionRequired(true);
        g.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        String[] labels = new String[]{Words.CF_NAV_P1, Words.CF_NAV_P2, Words.CF_NAV_P3};
        for (int i = 0; i < 3; i++) {
            MaterialButton bt = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            bt.setText(labels[i]);
            bt.setId(2000 + i);
            bt.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            g.addView(bt);
        }
        g.addOnButtonCheckedListener(new MaterialButtonToggleGroup.OnButtonCheckedListener() {
            public void onButtonChecked(MaterialButtonToggleGroup grp, int id, boolean isChecked) {
                if (!isChecked) return;
                if (suppressCheck) return;              // 程序性恢复选中，别当用户点击
                setNavParam(id - 2000);
            }
        });
        suppressCheck = true;
        try { g.check(2000 + navParam()); } catch (Throwable t) { }
        g.post(new Runnable() { public void run() { suppressCheck = false; } });
        return g;
    }

    View themeChooser() {
        final MaterialButtonToggleGroup g = new MaterialButtonToggleGroup(this);
        g.setSingleSelection(true);
        g.setSelectionRequired(true);
        g.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        String[] labels = new String[]{Words.CF_THEME_SYS, Words.CF_THEME_LIGHT, Words.CF_THEME_DARK};
        int cur = themeMode();
        for (int i = 0; i < 3; i++) {
            MaterialButton b = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            b.setText(labels[i]);
            b.setId(1000 + i);
            b.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            g.addView(b);
        }
        g.addOnButtonCheckedListener(new MaterialButtonToggleGroup.OnButtonCheckedListener() {
            public void onButtonChecked(MaterialButtonToggleGroup grp, int id, boolean isChecked) {
                if (!isChecked) return;
                if (suppressCheck) return;              // 程序性恢复选中，别当用户点击
                int want = id - 1000;
                if (want == themeMode()) return;        // 值未变 → 不处理（死循环的关键守卫）
                setThemeMode(want);
            }
        });
        // 程序性恢复选中：先挂 flag 再 check，并延后复位（Material 的事件可能是 post 派发的）
        suppressCheck = true;
        try { g.check(1000 + cur); } catch (Throwable t) { }
        g.post(new Runnable() { public void run() { suppressCheck = false; } });
        return g;
    }

    // ───────── 高级页 ─────────
    void buildAdv() {
        LinearLayout c = column();
        c.setPadding(dp(18), dp(6), dp(18), pagePadBottom());

        cAdv = c;
        c.addView(sectionTitle(Words.MA_SEC_TEST));
        c.addView(listItem(Words.MA_ITEM_ISLAND, Words.MA_ITEM_ISLAND_D, new Runnable() { public void run() {
            String r = Notifier.island(MainActivity.this, 4, 0, "HEALTH_REGISTER", Words.MA_ISLAND_T, Words.MA_ISLAND_C, true);
            toast(Words.MA_MSG_ISLAND + r);
        } }));
        c.addView(listItem(Words.MA_ITEM_MEDIA, Words.MA_ITEM_MEDIA_D, new Runnable() { public void run() {
            String r = Notifier.media(MainActivity.this, 9, Words.MA_MEDIA_T, Words.MA_MEDIA_C, true);
            toast(Words.MA_MSG_MEDIA + r);
        } }));
        c.addView(listItem(Words.MA_ITEM_LIVE, Words.MA_ITEM_LIVE_D, new Runnable() { public void run() {
            String r = Notifier.promoted(MainActivity.this, 10, Words.MA_LIVE_T, Words.MA_LIVE_C, 66);
            toast(Words.MA_MSG_LIVE + r);
        } }));
        c.addView(listItem(Words.MA_ITEM_TESTNOTI, Words.MA_ITEM_TESTNOTI_D, new Runnable() { public void run() {
            String r = Notifier.send(MainActivity.this, 3, Words.MA_TESTNOTI_T, Words.MA_TESTNOTI_C, false);
            toast(Words.MA_MSG_NOTI + r);
        } }));
        c.addView(listItem(Words.MA_ITEM_ENDTEST, Words.MA_ITEM_ENDTEST_D, new Runnable() { public void run() {
            Notifier.cancel(MainActivity.this, 3);
            Notifier.cancel(MainActivity.this, 4);
            Notifier.cancel(MainActivity.this, 10);
            Notifier.mediaEnd(MainActivity.this, 9);
            toast(Words.MA_MSG_ENDTEST);
        } }));

        c.addView(sectionTitle(Words.MA_SEC_DIAG));
        c.addView(quiet(Words.MA_DIAG_TOOLS));
        tvTools = txt(Words.MA_DIAG_LOADING, 12, colVariant());
        tvTools.setPadding(dp(6), dp(4), dp(6), dp(10));
        c.addView(tvTools);
        c.addView(quiet(Words.MA_DIAG_LOG));
        tvLog = txt("", 11, colVariant());
        tvLog.setPadding(dp(6), dp(4), dp(6), dp(10));
        c.addView(tvLog);

        ScrollView sv = new ScrollView(this);
        sv.addView(c);
        pageAdv.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        svAdv = sv;
    }
    // ───────── 33 号单：导航动效（三根无极滑杆；拖完即记、即时生效） ─────────
    static final String K_FX_AMP = "nav_fx_amp";     // 条体横向拉伸峰值 0.02~0.30
    static final String K_FX_PEAK = "nav_fx_peak";   // 图标放大峰值 1.00~2.60
    static final String K_FX_MS = "nav_fx_ms";       // 图标动效时长 520~1200

    interface FXCB { void onChanged(int v); }

    float fxAmp() { return getSharedPreferences(PREFS, MODE_PRIVATE).getFloat(K_FX_AMP, 0.12f); }
    float fxPeak() { return getSharedPreferences(PREFS, MODE_PRIVATE).getFloat(K_FX_PEAK, 1.65f); }
    float fxMs() { return getSharedPreferences(PREFS, MODE_PRIVATE).getFloat(K_FX_MS, 640f); }

    /** 把三根滑杆的值套到导航条上（视图树每次重建后都要调一次）。 */
    void applyNavFx() {
        if (navGlass == null) return;
        try {
            navGlass.setStretchAmp(fxAmp());
            navGlass.setIconPeak(fxPeak());
            navGlass.setIconMs(fxMs());
            navGlass.setMaskAlpha(maskAlpha());    // 54 号单 A：蒙版浓度
            navGlass.setMaskGapMs(maskGapMs());    // 54 号单 A：滴→蒙版 间隔
        } catch (Throwable t) { }
    }

    /** 33 号单：一根无极滑杆（整数刻度；换算／存储／即时生效都写在 onChanged 里）。 */
    View fxSlider(String title, String hint, final int min, final int max, final int step, int cur, final String unit, final FXCB cb) {
        LinearLayout col = column();
        final TextView tv = txt("", 15, col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6));
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(dp(4), dp(16), dp(4), dp(2));
        // 33.1 修正：不要用 String.format（末尾的 % 是非法格式符，会抛异常 → 打开即闪退）；直接拼字符串
        tv.setText(title + " · " + cur + unit);
        col.addView(tv);
        SeekBar sb = new SeekBar(this);
        sb.setMax((max - min) / step);
        sb.setProgress((cur - min) / step);
        int accent = col(com.google.android.material.R.attr.colorPrimary, 0xFF6C8CFF);
        sb.setProgressTintList(ColorStateList.valueOf(accent));
        sb.setThumbTintList(ColorStateList.valueOf(accent));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int v = min + p * step;
                tv.setText(title + " · " + v + unit);
                cb.onChanged(v);
            }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        col.addView(sb);
        col.addView(quiet(hint));
        return col;
    }

    /** 33 号单：滑杆值变了 → 重建配置页（滑杆位置与数值刷新）。 */
    void rebuildConfUi() {
        try { if (tab == 1) showTab(1); } catch (Throwable t) { }
    }

    // ───────── 通用组件 ─────────
    interface SwitchCB { void onChanged(boolean v); }

    LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout gridRow() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        l.setLayoutParams(lp);
        return l;
    }

    LinearLayout.LayoutParams gridLp(boolean left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        if (left) lp.rightMargin = dp(6); else lp.leftMargin = dp(6);
        return lp;
    }

    TextView sectionTitle(String s) {
        TextView t = txt(s, 13, col(com.google.android.material.R.attr.colorPrimary, 0xFF6C8CFF));
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(dp(4), dp(20), dp(4), dp(10));
        return t;
    }

    TextView quiet(String s) {
        TextView t = txt(s, 12, colVariant());
        t.setPadding(dp(4), dp(2), dp(4), dp(8));
        return t;
    }

    MaterialCardView card(boolean clickable) {
        MaterialCardView c = new MaterialCardView(this);
        c.setRadius(dp(18));
        c.setCardElevation(0f);
        c.setStrokeWidth(dp(1));
        c.setStrokeColor(col(com.google.android.material.R.attr.colorOutline, 0x339AA0A6));
        c.setCardBackgroundColor(col(com.google.android.material.R.attr.colorSurface, 0xFF1B1F24));
        if (clickable) {
            c.setClickable(true);
            c.setFocusable(true);
            ripple(c);
        }
        return c;
    }

    View tile(int iconRes, String title, String desc, final Runnable r) {
        MaterialCardView c = card(true);
        if (r != null) c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        LinearLayout in = column();
        in.setPadding(dp(14), dp(14), dp(14), dp(14));
        ImageView ic = new ImageView(this);
        ic.setImageResource(iconRes);
        ic.setImageTintList(ColorStateList.valueOf(col(com.google.android.material.R.attr.colorPrimary, 0xFF6C8CFF)));
        ic.setLayoutParams(new LinearLayout.LayoutParams(dp(22), dp(22)));
        in.addView(ic);
        TextView t = txt(title, 15, col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6));
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(0, dp(8), 0, 0);
        in.addView(t);
        TextView d = txt(desc, 12, colVariant());
        d.setPadding(0, dp(4), 0, 0);
        in.addView(d);
        c.addView(in);
        return c;
    }

    /** 列表式条目：整块可点，按下整条变淡（M3 state layer） */
    View listItem(String title, String desc, final Runnable r) {
        MaterialCardView c = card(true);
        if (r != null) c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        LinearLayout in = new LinearLayout(this);
        in.setOrientation(LinearLayout.HORIZONTAL);
        in.setGravity(Gravity.CENTER_VERTICAL);
        in.setPadding(dp(16), dp(14), dp(14), dp(14));
        LinearLayout col = column();
        col.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        TextView t = txt(title, 15, col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6));
        col.addView(t);
        if (desc != null && desc.length() > 0) {
            TextView d = txt(desc, 12, colVariant());
            d.setPadding(0, dp(3), 0, 0);
            col.addView(d);
        }
        in.addView(col);
        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_chevron);
        arrow.setImageTintList(ColorStateList.valueOf(colVariant()));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(20), dp(20));
        alp.leftMargin = dp(10);
        arrow.setLayoutParams(alp);
        in.addView(arrow);
        c.addView(in);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        c.setLayoutParams(lp);
        return c;
    }

    View switchItem(String title, String desc, boolean checked, SwitchCB cb) {
        return switchItem(title, desc, checked, true, cb);
    }

    /** enabled=false：整行置灰、开关不可点（30 号单：极夜在非深色下常显但不可用） */
    View switchItem(String title, String desc, boolean checked, boolean enabled, final SwitchCB cb) {
        MaterialCardView c = card(false);
        LinearLayout in = new LinearLayout(this);
        in.setOrientation(LinearLayout.HORIZONTAL);
        in.setGravity(Gravity.CENTER_VERTICAL);
        in.setPadding(dp(16), dp(10), dp(12), dp(10));
        LinearLayout col = column();
        col.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(txt(title, 15, col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6)));
        TextView d = txt(desc, 12, colVariant());
        d.setPadding(0, dp(3), dp(6), 0);
        col.addView(d);
        in.addView(col);
        MaterialSwitch sw = new MaterialSwitch(this);
        sw.setChecked(checked);
        sw.setEnabled(enabled);
        if (cb != null) {
            sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(android.widget.CompoundButton b, boolean v) { cb.onChanged(v); }
            });
        }
        in.addView(sw);
        c.addView(in);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        c.setLayoutParams(lp);
        if (!enabled) { c.setAlpha(0.45f); c.setClickable(false); c.setFocusable(false); }
        return c;
    }

    /** 32 号单：当前应显示的页面对象（用来判断某张旧页是否已被换下去） */
    View curPage() { return (tab == 0) ? pageFunc : (tab == 1 ? pageConf : pageAdv); }

    void showTab(int idx) {
        final boolean noAnim = suppressTabAnim;
        suppressTabAnim = false;
        if (idx != tab) saveScroll();     // 切页前记住当前页滚动位置（主题切换的重建不走这里）
        final int old = tab;
        tab = idx;
        try { getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt("last_tab", idx).apply(); } catch (Throwable t) { }
        if (idx == 1) {
            pageConf.removeAllViews();
            buildConf();
        }
        final View page = (idx == 0) ? pageFunc : (idx == 1 ? pageConf : pageAdv);

        // —— 32 号单：切页滑动（新页从侧面滑入、盖住旧页；方向跟滴走）——
        // 先停掉在飞的动画并把页复位；只留最新那张旧页当"被盖住"的底，其余残页清掉。
        View prev = null;
        for (int i = host.getChildCount() - 1; i >= 0; i--) {
            View ch = host.getChildAt(i);
            try { ch.animate().cancel(); } catch (Throwable t) { }
            try { ch.setTranslationX(0f); } catch (Throwable t) { }
            if (ch != page && prev == null) prev = ch;
        }
        for (int i = host.getChildCount() - 1; i >= 0; i--) {
            View ch = host.getChildAt(i);
            if (ch != page && ch != prev) host.removeViewAt(i);
        }
        boolean anim = (prev != null) && !noAnim && idx != old && host.isLaidOut();
        if (!anim && prev != null) { host.removeView(prev); prev = null; }
        if (page.getParent() == host) host.removeView(page);   // 先摘下 → 再挂上去才保证在最上层
        host.addView(page, new FrameLayout.LayoutParams(-1, -1));
        if (anim) {
            final View out = prev;
            int w = host.getWidth() > 0 ? host.getWidth() : dp(360);
            page.setTranslationX((idx > old ? 1f : -1f) * w);   // 往右切 → 新页从右侧进；往左切 → 从左侧进
            page.animate().translationX(0f).setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .withEndAction(new Runnable() { public void run() {
                        try { page.setTranslationX(0f); } catch (Throwable t) { }
                        try { if (out.getParent() == host && out != curPage()) host.removeView(out); } catch (Throwable t) { }
                    } }).start();
        } else {
            page.setTranslationX(0f);
        }
        // 29 号单：与玻璃条同步选中档（条自己已经选好了就不重复动画；避免回调打回旋）
        if (navGlass != null && navGlass.getSelectedIndex() != idx) navGlass.setSelectedIndex(idx);
        restoreScroll();
    }

    void ripple(View v) {
        try {
            android.util.TypedValue tv = new android.util.TypedValue();
            if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true) && tv.resourceId != 0) {
                v.setForeground(getDrawable(tv.resourceId));
            }
        } catch (Throwable t) { }
    }

    // ═══════════ 49 号单 B：全应用统一的「Reticle 风格」对话框（圆角 ＋ 薰衣草紫 ＋ 液态玻璃感） ═══════════
    // 一处实现、处处使用：原先用系统 AlertDialog 的地方全改走 reticleDialog()；跟随浅色／深色／极夜三套主题。
    // 配色：面板＝薰衣草紫调（浅/深/极夜三档）；描边与强调＝应用标志色 PURPLE 同族（对齐 GlassyNavBar.PURPLE）；
    //       正文＝主题 colorOnSurface（对比度够）；主按钮＝实心紫＋反色字，次按钮＝紫描边＋紫字。
    private static final int[] DLG_PANEL  = {0xFFF3ECFF, 0xFF2B2440, 0xFF17131F};  // 面板底（浅 / 深 / 极夜）
    private static final int[] DLG_STROKE = {0x557C4AD8, 0x66C6AEFF, 0x55C6AEFF};  // 面板描边（紫调）
    private static final int[] DLG_ACCENT = {0xFF7C4AD8, 0xFFC6AEFF, 0xFFC6AEFF};  // 标题/按钮强调色
    private static final int[] DLG_INPUT  = {0xFFE9DFFF, 0xFF3A3155, 0xFF241E33};  // 输入框底
    private static final int[] DLG_ON_ACC = {0xFFFFFFFF, 0xFF1A1030, 0xFF1A1030};  // 实心紫按钮上的字色
    private static final int DLG_RADIUS_DP = 24;                                  // 圆角（卡片是 18dp，对话框取 24dp：同族、更像大容器）

    int dlgTheme() { if (!nightActive()) return 0; return blackOn() ? 2 : 1; }    // 与 glassTheme() 同一口径

    /** 统一对话框：标题 ＋ 正文（都可留空）＋ 可选的自定义视图（如输入框）＋ 取消/确定两颗按钮。 */
    void reticleDialog(String title, String message, View extra, String cancelLabel, String okLabel, final Runnable onOk) {
        try {
            final int t = dlgTheme();
            final android.app.Dialog d = new android.app.Dialog(this);
            d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(22), dp(20), dp(22), dp(14));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(DLG_PANEL[t]);
            bg.setCornerRadius(dp(DLG_RADIUS_DP));
            bg.setStroke(dp(1), DLG_STROKE[t]);
            box.setBackground(bg);
            box.setClipToOutline(true);              // 子视图（输入框等）不越出圆角

            if (title != null && title.length() > 0) {
                TextView tv = txt(title, 18, DLG_ACCENT[t]);
                tv.setTypeface(null, Typeface.BOLD);
                box.addView(tv, new LinearLayout.LayoutParams(-1, -2));
            }
            if (message != null && message.length() > 0) {
                TextView tv = txt(message, 13, col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6));
                tv.setLineSpacing(dp(3), 1f);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = dp(10);
                box.addView(tv, lp);                 // 提示文案原样保留（一键复制那类提示不动）
            }
            if (extra != null) {
                if (extra instanceof android.widget.EditText) {
                    android.widget.EditText et = (android.widget.EditText) extra;
                    styleDlgInput(et, t);
                    et.requestFocus();            // 49 号单补：一打开就聚焦输入框（否则助手侧 input/paste 找不到焦点框、写不进去）
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = dp(14);
                box.addView(extra, lp);              // ★显式 LayoutParams
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.topMargin = dp(16);
            box.addView(row, rowLp);

            if (cancelLabel != null && cancelLabel.length() > 0) {
                TextView c = dlgBtn(cancelLabel, t, false);
                c.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { try { d.dismiss(); } catch (Throwable x) { } }
                });
                row.addView(c);
            }
            if (okLabel != null && okLabel.length() > 0) {
                TextView o = dlgBtn(okLabel, t, true);
                o.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        try { d.dismiss(); } catch (Throwable x) { }
                        if (onOk != null) onOk.run();
                    }
                });
                LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-2, -2);
                olp.leftMargin = dp(10);
                row.addView(o, olp);
            }

            d.setContentView(box);
            d.setCanceledOnTouchOutside(true);
            android.view.Window w = d.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));   // 去掉系统方框底
                w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                android.view.WindowManager.LayoutParams wp = w.getAttributes();
                wp.dimAmount = 0.55f;
                w.setAttributes(wp);
                int wpx = getResources().getDisplayMetrics().widthPixels - dp(48);
                if (wpx < dp(240)) wpx = dp(240);
                w.setLayout(wpx, -2);                // 宽＝屏宽-48dp；高＝内容自适应
            }
            box.setAlpha(0f);                        // 出现过渡：淡入＋轻微放大（200ms）
            box.setScaleX(0.94f);
            box.setScaleY(0.94f);
            d.show();
            box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start();
        } catch (Throwable t) { toast(Words.CF_MSG_DLG_ERR + t); }
    }

    /** 对话框按钮：主＝实心紫＋反色字；次＝紫描边＋紫字。 */
    TextView dlgBtn(String label, int t, boolean primary) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTypeface(null, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(20), dp(10), dp(20), dp(10));
        b.setClickable(true);
        b.setFocusable(true);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(22));
        if (primary) {
            g.setColor(DLG_ACCENT[t]);
            b.setTextColor(DLG_ON_ACC[t]);
        } else {
            g.setColor(0x00000000);
            g.setStroke(dp(1), DLG_ACCENT[t]);
            b.setTextColor(DLG_ACCENT[t]);
        }
        b.setBackground(g);
        return b;
    }

    /** 对话框里的输入框：圆角紫底、主题字色（★功能与校验逻辑一律不动，只换皮）。 */
    void styleDlgInput(android.widget.EditText et, int t) {
        try {
            GradientDrawable g = new GradientDrawable();
            g.setColor(DLG_INPUT[t]);
            g.setCornerRadius(dp(14));
            g.setStroke(dp(1), DLG_STROKE[t]);
            et.setBackground(g);
            et.setTextColor(col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6));
            et.setHintTextColor(colVariant());
            et.setPadding(dp(14), dp(12), dp(14), dp(12));
        } catch (Throwable x) { }
    }

    /** 28 号单：一键停机 —— 先问一句，再关掉初始化 */
    void confirmStopAll() {
        reticleDialog(Words.CF_STOP_T, Words.CF_STOP_ASK, null, Words.CF_DLG_CANCEL, Words.CF_DLG_STOP, new Runnable() {
            public void run() { stopAll(); }
        });
    }

    /** 关看门狗＋停前台守护＋停本地服务＋清引导标记；重新打开 App 即恢复 */
    void stopAll() {
        try { A11yWatchdog.setOn(this, false); } catch (Throwable t) { }
        try { WatchdogService.stop(this); } catch (Throwable t) { }
        try { OnboardActivity.resetDone(this); } catch (Throwable t) { }
        try { McpServer.stop(this); } catch (Throwable t) { }
        toast(Words.CF_STOP_DONE);
        try { refresh(); } catch (Throwable t) { }
    }

    void toast(String s) {
        try { tvOut.setText(s); } catch (Throwable t) { }
    }

    TextView txt(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    int colVariant() { return col(com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF9AA0A6); }

    int col(int attr, int fallback) {
        try {
            android.util.TypedValue tv = new android.util.TypedValue();
            if (getTheme().resolveAttribute(attr, tv, true)) {
                if (tv.resourceId != 0) return getResources().getColor(tv.resourceId, getTheme());
                if (tv.data != 0) return tv.data;
            }
        } catch (Throwable t) { }
        return fallback;
    }

    // ───────── 业务（沿用 0.16.x 逻辑，只换外壳） ─────────
    void refresh() {
        try {
            int good = col(com.google.android.material.R.attr.colorPrimary, 0xFF6C8CFF);
            int bad = col(com.google.android.material.R.attr.colorError, 0xFFFF6B6B);
            boolean run = McpServer.isRunning();
            if (sMcpV != null) { sMcpV.setText(run ? Words.MA_MCP_ON : Words.MA_MCP_OFF); sMcpV.setTextColor(run ? good : bad); }
            int ast = A11yLink.ST_UNKNOWN;
            int warn = col(com.google.android.material.R.attr.colorTertiary, 0xFFE9A23B);
            try { ast = A11yLink.state(this); } catch (Throwable t) { }
            String a = A11yLink.label(ast);
            if (sA11yV != null) {
                sA11yV.setText(a);
                sA11yV.setTextColor(ast == A11yLink.ST_CONNECTED ? good : (ast == A11yLink.ST_IN_LIST_NO_CONN ? warn : bad));
            }
            try {
                String hint = A11yLink.hintText(this);
                if (tvA11yHint != null) {
                    if (hint.length() > 0) { tvA11yHint.setText(hint); tvA11yHint.setVisibility(View.VISIBLE); }
                    else { tvA11yHint.setText(""); tvA11yHint.setVisibility(View.GONE); }
                }
                if (a11yFixBox != null) a11yFixBox.setVisibility(ast == A11yLink.ST_CONNECTED ? View.GONE : View.VISIBLE);
            } catch (Throwable t) { }
            String s = shizukuState();
            if (sShzV != null) { sShzV.setText(s); sShzV.setTextColor(s.contains("✓") ? good : bad); }
            String nf = (android.os.Build.VERSION.SDK_INT >= 33)
                    ? (checkSelfPermission("android.permission.POST_NOTIFICATIONS") == android.content.pm.PackageManager.PERMISSION_GRANTED ? Words.MA_NOTI_ON : Words.MA_NOTI_OFF)
                    : Words.MA_NOTI_NA;
            if (sNotiV != null) { sNotiV.setText(nf); sNotiV.setTextColor(Words.MA_NOTI_OFF.equals(nf) ? bad : good); }
            loadTools();
            if (tvWd != null) tvWd.setText(A11yWatchdog.statusLine(this));
            if (tvLog != null) tvLog.setText(tail(12));
            if (tvAddr != null) tvAddr.setText(fullUrl());   // 51 号单：换端口后地址卡跟着变
            if (tvHName != null) tvHName.setText(Words.CF_ADDR_HNAME + McpServer.getHeaderName(this));
            if (tvMem != null) tvMem.setText(Words.MEM_LINE_PREFIX + MemGuard.statusLine(this));   // 52 号单
        } catch (Throwable t) { }
    }

    void selfTest() {
        if (tvOut != null) tvOut.setText(Words.MA_MSG_SELFTESTING);
        new Thread(new Runnable() { public void run() {
            final String st;
            try { st = McpServer.selfTest(MainActivity.this); }
            catch (Throwable t) {
                ui.post(new Runnable() { public void run() { if (tvOut != null) tvOut.setText(Words.MA_MSG_SELFTEST_ERR + t); } });
                return;
            }
            final String a = a11yCard() + "（在册：" + a11yState() + "）";
            final String s = shizukuState();
            ui.post(new Runnable() { public void run() {
                if (tvOut != null) tvOut.setText(Words.MA_SELFTEST_HEAD + "\n· 无障碍：" + a + "\n· Shizuku：" + s + "\n· 本地服务：" + st);
                refresh();
            } });
        } }).start();
    }

    void restart() {
        try {
            McpServer.start(this);
            toast(Words.MA_MSG_RESTARTED);
            refresh();
        } catch (Throwable t) { toast(Words.MA_MSG_RESTART_ERR + t); }
    }

    void wdCheckNow() {
        toast(Words.CF_MSG_CHECKING);
        new Thread(new Runnable() { public void run() {
            String r;
            try { r = A11yWatchdog.tick(MainActivity.this, false); } catch (Throwable t) { r = Words.CF_MSG_ERR_PREFIX + t; }
            final String fr = r;
            ui.post(new Runnable() { public void run() { toast(Words.CF_MSG_WD_PREFIX + fr); refresh(); } });
        } }).start();
    }

    void openA11y() {
        try {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) { }
    }

    /** 50 号单：配置页一个按钮 ＋ 一行「粘到哪儿」说明（子项一律显式 LayoutParams，不许裸着当弹簧） */
    View confBtn(String label, String hint, final Runnable r) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        col.setPadding(0, dp(10), 0, 0);
        MaterialButton b = new MaterialButton(this);
        b.setText(label);
        b.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        col.addView(b);
        TextView t = txt(hint, 12, colVariant());
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = dp(4);
        t.setLayoutParams(tlp);
        t.setLineSpacing(dp(3), 1f);
        col.addView(t);
        return col;
    }

    /** 50 号单：三个按钮各贴各的框（请求头名称 / 请求头值（令牌） / 链接地址） */
    void copyHName() {
        try {
            String hn = McpServer.getHeaderName(this);
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("mcp-hname", hn));
            toast(Words.CF_MSG_HNAME_OK_A + hn + Words.CF_MSG_HNAME_OK_B);
        } catch (Throwable t) { toast(Words.CF_MSG_HNAME_ERR + t); }
    }

    void copyHValue() {
        try {
            String bearer = "Bearer " + McpServer.getToken(this);
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("mcp-hvalue", bearer));
            toast(Words.CF_MSG_HVALUE_OK_A + bearer.length() + Words.CF_MSG_HVALUE_OK_B);
        } catch (Throwable t) { toast(Words.CF_MSG_HVALUE_ERR + t); }
    }

    void copyUrl() {
        try {
            String url = fullUrl();
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("mcp-url", url));
            toast(Words.CF_MSG_URL_OK_A + url + Words.CF_MSG_URL_OK_B);
        } catch (Throwable t) { toast(Words.CF_MSG_URL_ERR + t); }
    }

    String fullUrl() { return "http://127.0.0.1:" + McpServer.getPort(this) + "/mcp"; }

    /** 51 号单：复制官网地址（＝RikkaHub 官网；跟上面「复制链接地址」不是一回事——那个是本机服务地址） */
    void copyOfficial() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("rikka-official", Words.CF_OFFICIAL_URL));
            toast(Words.CF_MSG_OFFICIAL_OK);
        } catch (Throwable t) { toast(Words.CF_MSG_OFFICIAL_ERR + t); }
    }

    /** 51 号单：改请求头名称（默认 Authorization）——改完要提醒去 RikkaHub 同步改 */
    void askHeaderName() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setSingleLine(true);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        et.setText(McpServer.getHeaderName(this));
        try { et.setSelection(et.getText().length()); } catch (Throwable t) { }
        reticleDialog(Words.CF_HNAME_DLG_T, Words.CF_HNAME_DLG_ASK, et, Words.CF_DLG_CANCEL, Words.CF_DLG_OK, new Runnable() {
            public void run() {
                String nm = et.getText() == null ? "" : et.getText().toString().trim();
                if (!McpServer.setHeaderName(MainActivity.this, nm)) { toast(Words.CF_HNAME_BAD); return; }
                toast(Words.CF_MSG_HNAME_SET_A + McpServer.getHeaderName(MainActivity.this) + Words.CF_MSG_HNAME_SET_B);
                try { refresh(); } catch (Throwable t) { }
            }
        });
    }

    /** 51 号单：重新生成令牌（先问一句；旧令牌立刻失效，RikkaHub 里要重新粘一次） */
    void confirmRegenToken() {
        reticleDialog(Words.CF_BTN_TOKEN_NEW, Words.CF_TOKEN_ASK, null, Words.CF_DLG_CANCEL, Words.CF_DLG_OK, new Runnable() {
            public void run() {
                try { McpServer.regenerateToken(MainActivity.this); } catch (Throwable t) { }
                toast(Words.CF_MSG_TOKEN_NEW);
                try { refresh(); } catch (Throwable t) { }
            }
        });
    }

    /** 51 号单：换端口＝换新地址（服务按新端口重启；旧端口随之不再监听） */
    void askPort() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setSingleLine(true);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        reticleDialog(Words.CF_PORT_DLG_T, Words.CF_PORT_DLG_ASK_A + McpServer.getPort(this) + Words.CF_PORT_DLG_ASK_B, et,
                Words.CF_DLG_CANCEL, Words.CF_DLG_OK, new Runnable() {
            public void run() {
                int p = -1;
                try { p = Integer.parseInt(et.getText().toString().trim()); } catch (Throwable t) { p = -1; }
                if (p < 1024 || p > 65535 || !McpServer.setPort(MainActivity.this, p)) { toast(Words.CF_PORT_BAD); return; }
                try { McpServer.restart(MainActivity.this); } catch (Throwable t) { }
                toast(Words.CF_MSG_PORT_SET_A + fullUrl() + Words.CF_MSG_PORT_SET_B);
                try { refresh(); } catch (Throwable t) { }
            }
        });
    }

    void loadTools() {
        new Thread(new Runnable() { public void run() {
            final String r = mcp("{" + Q + "jsonrpc" + Q + ":" + Q + "2.0" + Q + "," + Q + "id" + Q + ":7," + Q + "method" + Q + ":" + Q + "tools/list" + Q + "," + Q + "params" + Q + ":{}" + "}");
            ui.post(new Runnable() { public void run() { renderTools(r); } });
        } }).start();
    }

    void renderTools(String r) {
        if (tvTools == null) return;
        StringBuilder sb = new StringBuilder();
        int i = 0, cnt = 0;
        String A = Q + "name" + Q + ":" + Q;
        String B = Q + "description" + Q + ":" + Q;
        while (true) {
            int p = r.indexOf(A, i);
            if (p < 0) break;
            int q = r.indexOf(Q, p + A.length());
            if (q < 0) break;
            String name = r.substring(p + A.length(), q);
            String desc = "";
            int d0 = r.indexOf(B, q);
            if (d0 > 0 && d0 - q < 300) {
                int d1 = r.indexOf(Q, d0 + B.length());
                if (d1 > 0) desc = r.substring(d0 + B.length(), d1);
            }
            sb.append("· ").append(name).append(" —— ").append(cut(desc, 56)).append("\n");
            i = q + 1;
            cnt++;
        }
        tvTools.setText(cnt == 0 ? (Words.MA_DIAG_FAIL + cut(r, 120)) : (Words.MA_DIAG_COUNT_A + cnt + Words.MA_DIAG_COUNT_B + "\n" + sb.toString()));
    }

    String mcp(String body) {
        try {
            String token = McpServer.getToken(this);
            Socket s = new Socket();
            s.connect(new InetSocketAddress("127.0.0.1", McpServer.getPort(this)), 2500);
            s.setSoTimeout(8000);
            byte[] bb = body.getBytes(StandardCharsets.UTF_8);
            String head = "POST /mcp HTTP/1.1\r\nHost: 127.0.0.1\r\n" + McpServer.getHeaderName(this) + ": Bearer " + token
                    + "\r\nContent-Type: application/json\r\nContent-Length: " + bb.length + "\r\nConnection: close\r\n\r\n";
            OutputStream o = s.getOutputStream();
            o.write(head.getBytes(StandardCharsets.UTF_8));
            o.write(bb);
            o.flush();
            ByteArrayOutputStream rb = new ByteArrayOutputStream();
            InputStream in = s.getInputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) > 0) rb.write(buf, 0, r);
            s.close();
            return new String(rb.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable t) { return "ERR " + t; }
    }

    String ver() {
        try {
            PackageInfo p = getPackageManager().getPackageInfo(getPackageName(), 0);
            return p.versionName;
        } catch (Throwable t) { return "?"; }
    }

    /** 53 号单：修一次（重连无障碍）——删名→写回，逼系统重新绑；只动 Reticle 自己那条，不占主线程。 */
    void fixA11y() {
        if (tvOut != null) tvOut.setText(Words.MA_MSG_A11Y_FIXING);
        new Thread(new Runnable() { public void run() {
            String rr;
            try { rr = A11yLink.repair(MainActivity.this, true); } catch (Throwable t) { rr = Words.A11Y_REP_ERR + t; }
            final String r = rr;
            ui.post(new Runnable() { public void run() {
                String msg = Words.MA_MSG_A11Y_FIXED + r;
                if (tvOut != null) tvOut.setText(msg);
                try { android.widget.Toast.makeText(MainActivity.this, msg, android.widget.Toast.LENGTH_LONG).show(); } catch (Throwable t) { }
                refresh();
            } });
        } }).start();
    }

    /** 53 号单：卡与自检的统一口径（在册 ＋ 有没有接上，两件事一起报）。 */
    String a11yCard() {
        try { return A11yLink.label(A11yLink.state(this)); } catch (Throwable t) { return Words.MA_A11Y_ERR; }
    }

    String a11yState() {
        try {
            String v = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (v == null) return Words.MA_A11Y_EMPTY;
            if (v.contains("app.reticle/app.reticle.VeriService")) return Words.MA_A11Y_ON;
            if (v.contains("app.reticle/.VeriService")) return Words.MA_A11Y_ON;
            if (v.contains("app.reticle")) return Words.MA_A11Y_MAYBE;
            return Words.MA_A11Y_OFF;
        } catch (Throwable t) { return Words.MA_A11Y_ERR; }
    }

    String shizukuState() {
        try {
            if (!rikka.shizuku.Shizuku.pingBinder()) return Words.MA_SHZ_OFF;
            if (rikka.shizuku.Shizuku.isPreV11()) return Words.MA_SHZ_OLD;
            boolean g = rikka.shizuku.Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED;
            return g ? Words.MA_SHZ_OK : Words.MA_SHZ_NO;
        } catch (Throwable t) { return Words.MA_SHZ_ERR; }
    }

    String logTime() {
        try {
            File f = new File(Home.sub(this, "logs"), "mcp.log");
            if (!f.exists()) return Words.CF_LOG_NONE;
            long d = System.currentTimeMillis() - f.lastModified();
            if (d < 60000) return (d / 1000) + Words.CF_LOG_SEC;
            if (d < 3600000) return (d / 60000) + Words.CF_LOG_MIN;
            return (d / 3600000) + Words.CF_LOG_HOUR;
        } catch (Throwable t) { return "?"; }
    }

    String tail(int n) {
        try {
            File f = new File(Home.sub(this, "logs"), "mcp.log");
            if (!f.exists()) return Words.MA_LOG_NOFILE;
            long len = f.length();
            long st = Math.max(0, len - 6000);
            FileInputStream in = new FileInputStream(f);
            long sk = 0;
            while (sk < st) { long k = in.skip(st - sk); if (k <= 0) break; sk += k; }
            byte[] buf = new byte[(int) (len - sk)];
            int off = 0, r;
            while (off < buf.length && (r = in.read(buf, off, buf.length - off)) > 0) off += r;
            in.close();
            String[] lines = new String(buf, 0, off, StandardCharsets.UTF_8).split("\n");
            StringBuilder sb = new StringBuilder();
            for (int i = Math.max(0, lines.length - n); i < lines.length; i++) sb.append(lines[i]).append("\n");
            String s = sb.toString();
            return s.isEmpty() ? Words.MA_LOG_EMPTY : s;
        } catch (Throwable t) { return Words.MA_LOG_ERR; }
    }

    String cut(String s, int n) {
        if (s == null) return "";
        s = s.replace("\n", " ");
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }
}
