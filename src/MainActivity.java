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
    TextView tvConfFlash;      // ★88.9：配置页顶部那一句「点完立刻看得见」的反馈（平时 GONE）
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
    // 79 号单：检查更新（首页那行＋配置页状态）
    View cardUpd;
    TextView tvUpdTitle, tvUpdDesc, tvUpdState, tvUpdSeen, tvUpdFoldTag;
    boolean uiFg = false;                          // 79 号单返工：在前台才弹窗（后台查到的先存着）
    UpdateCheck.Info pendingUpd;                   // 待弹的那一次：进前台再弹
    android.widget.ProgressBar pbUpd;
    UpdateCheck.Info updInfo;
    boolean updDownloading, updShownSession;   // 84 号单：下载中不弹；一次前台会话只弹一次
    String updApkPath;
    boolean uiReady = false;   // 86.5：首帧占位期间为 false（onResume 的重活等 startUp 收尾）

    /** 86.5 号单 · 冷启动白屏：先贴一帧占位（同底色），起服务＋建页挪到首帧之后。
     *  老版本在 onCreate 里一口气起 5 个服务、再建三页＋玻璃条，最后才 setContentView——首帧得等这些
     *  全做完，用户看到的就是"点开白一下"。现在顺序改成：主题 → 占位底色 → 真画上去这一帧 → 起服务
     *  → 建页 → 换真界面。★只挪顺序、不改功能；「用户已停」清理仍在首次进入时做（不能省）。 */
    @Override protected void onCreate(Bundle b) {
        setTheme(themeRes());
        appliedThemeRes = themeRes();
        super.onCreate(b);
        try { WindowCompat.setDecorFitsSystemWindows(getWindow(), false); } catch (Throwable t) { }
        try {
            FrameLayout ph = new FrameLayout(this);
            ph.setBackgroundColor(col(android.R.attr.colorBackground, 0xFF101418));
            setContentView(ph);
        } catch (Throwable t) { }
        final Bundle bb = b;
        // 8ms：够占位帧进绘制队列，又不让人看出"空一下"
        ui.postDelayed(new Runnable() { public void run() { startUp(bb); } }, 8);
    }

    /** 86.5 号单 · 真正的启动（原来 onCreate 里那一堆原样搬过来，只换执行时机）。 */
    void startUp(Bundle b) {
        try { McpServer.start(this); } catch (Throwable t) { }
        try { A11yWatchdog.start(this); } catch (Throwable t) { }
        // 52 号单：手动打开 App ＝「用户已停」到此为止（清掉标志 → 功能恢复）
        try { WatchdogService.setUserStopped(this, false); } catch (Throwable t) { }
        // 86.6：打开 App 就把无障碍写回（划掉卡片时系统会把我们这条摘掉），并如实告诉用户成没成
        try { restoreA11yOnOpen(); } catch (Throwable t) { }
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
        try { UpdateCheck.migrate(this); } catch (Throwable t) { }
        try { if (!OnboardActivity.done(this)) OnboardActivity.open(this); } catch (Throwable t) { }
        // 86.5：页面就位——把原来靠 onResume 做的首屏刷新补上（占位那一帧期间 onResume 已先跑过）
        uiReady = true;
        try { maybeCheckUpdate(); } catch (Throwable t) { }
        try { refresh(); } catch (Throwable t) { }
        try { if (hidePkgsDescLive != null) hidePkgsDescLive.setText(hidePkgsDescText()); } catch (Throwable t) { }
    }

    /** ★71 B 线（2026-10-15）：从「让位名单」页回来时，就地把这行说明刷成最新勾选数。 */
    View minimalKeepRow;
    TextView hidePkgsDescLive;

    @Override protected void onResume() {
        super.onResume();
        uiFg = true;
        if (!uiReady) return;   // 86.5：首帧占位期间页面还没建（重活在 startUp 收尾补做）
        try { maybeCheckUpdate(); } catch (Throwable t) { }
        try { refresh(); } catch (Throwable t) { }
        try { if (hidePkgsDescLive != null) hidePkgsDescLive.setText(hidePkgsDescText()); } catch (Throwable t) { }
        // 79 号单返工：检查是在后台跑完的 → 结果先存着，回到前台这一下才弹（只弹给"用户正在看"的那一次）
        try {
            if (pendingUpd != null) {
                UpdateCheck.Info p = pendingUpd;
                pendingUpd = null;
                showUpdate(p, false);
            }
        } catch (Throwable t) { }
    }

    @Override protected void onPause() {
        uiFg = false;
        updShownSession = false;   // 84 号单：回后台再回来算新会话
        super.onPause();
    }

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
        c.addView(quiet(Words.MA_HINT_LOCAL_BUILD));   // 76 号单：首页一句人话（能力说明，不摆手段）

        // 79 号单：有新版才出现的那一行（常态隐藏；点它＝下载，下完再点＝拉系统安装器）
        cardUpd = updateCard();
        c.addView(cardUpd);

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

        // 76 号单：打包 App 板块整块移到「高级」页——首页只留上面那句人话
        c.addView(sectionTitle(Words.MA_SEC_TOOLS));
        LinearLayout g1 = gridRow();
        g1.addView(tile(R.drawable.ic_tile_check, Words.MA_TILE_SELFTEST, Words.MA_TILE_SELFTEST_D, new Runnable() { public void run() { selfTest(); } }), gridLp(true));
        g1.addView(tile(R.drawable.ic_tile_power, Words.MA_TILE_RESTART, Words.MA_TILE_RESTART_D, new Runnable() { public void run() { restart(); } }), gridLp(false));
        c.addView(g1);
        LinearLayout g2 = gridRow();
        g2.addView(tile(R.drawable.ic_tile_refresh, Words.MA_TILE_REFRESH, Words.MA_TILE_REFRESH_D, new Runnable() { public void run() { refresh(); toast(Words.MA_MSG_REFRESHED); } }), gridLp(true));
        g2.addView(tile(R.drawable.ic_tile_open, Words.MA_TILE_A11Y_SET, Words.MA_TILE_A11Y_SET_D, new Runnable() { public void run() { openA11y(); } }), gridLp(false));
        c.addView(g2);

        c.addView(sectionTitle(Words.MA_SEC_TOOLBOX));
        c.addView(quiet(Words.MA_BUILD_HINT_LOCK));
        boxToolClusters = column();
        c.addView(boxToolClusters);

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
    static final int REQ_MASCOT = 6401;

    void pickMascotImage() {
        try {
            Intent mi = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            mi.addCategory(Intent.CATEGORY_OPENABLE);
            mi.setType("image/*");
            startActivityForResult(mi, REQ_MASCOT);
        } catch (Throwable t) {
            toast(Words.MS_IMG_PICKER_FAIL);
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_ICON) { toast(Icons.pinShortcut(this, res == RESULT_OK ? data : null)); return; }
        if (req != REQ_MASCOT) return;
        toast(Mascot.takePickedImage(this, res == RESULT_OK ? data : null));
    }

    void buildConf() {
        LinearLayout c = column();
        c.setPadding(dp(18), dp(6), dp(18), pagePadBottom());

        cConf = c;
        // ★88.9：页面顶部一句可见反馈（点完立刻看得见；平时收起来）
        tvConfFlash = txt("", 13, foldTitleColor());
        tvConfFlash.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tvConfFlash.setPadding(dp(4), dp(8), dp(4), dp(2));
        tvConfFlash.setVisibility(View.GONE);
        c.addView(tvConfFlash);
        // ★79 号单返工：配置页分两层——首屏只留最常用的（引导／连接／开关／维护），
        //   其余全部收进可折叠块（FoldSection；默认全收起，关键状态挂在母标题右侧）。
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

        // ── 开关（首屏）：静音震动 ＋ 通知入口 ──
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

        // ── 折叠：令牌与地址 ──
        c.addView(foldSection(Words.CF_SEC_TOKEN, Words.CF_FOLD_PORT_FMT + McpServer.getPort(this), new FoldBody() {
            public void build(LinearLayout fb) {
                fb.addView(quiet13(Words.CF_TOKEN_WHAT));
                fb.addView(quiet13(Words.CF_TOKEN_HOW));
                fb.addView(confBtn(Words.CF_BTN_TOKEN_NEW, Words.CF_HINT_TOKEN_NEW, new Runnable() { public void run() { confirmRegenToken(); } }));
                fb.addView(confBtn(Words.CF_BTN_HNAME_SET, Words.CF_HINT_HNAME_SET, new Runnable() { public void run() { askHeaderName(); } }));
                fb.addView(quiet13(Words.CF_LINK_WHAT));
                fb.addView(confBtn(Words.CF_BTN_PORT_NEW, Words.CF_HINT_PORT_NEW, new Runnable() { public void run() { askPort(); } }));
                fb.addView(addrCard());
            }
        }));

        // ── 折叠：桌面伙伴（形象／气泡／让位名单）──
        c.addView(foldSection(Words.CF_SEC_MASCOT, Mascot.isOn(MainActivity.this) ? Words.CF_FOLD_ON : Words.CF_FOLD_OFF, new FoldBody() {
            public void build(final LinearLayout fb) {
                fb.addView(switchItem(Words.CF_SW_MASCOT_T,
                        Words.CF_SW_MASCOT_D,
                        Mascot.isOn(MainActivity.this), true, new SwitchCB() {
                            public void onChanged(boolean v) {
                                Mascot.setOn(MainActivity.this, v);
                                toast(v ? Words.CF_MSG_MASCOT_ON : Words.CF_MSG_MASCOT_OFF);
                            }
                        }));
                fb.addView(listItem(Words.CF_ITEM_MASCOT_IMG, Words.CF_ITEM_MASCOT_IMG_D, new Runnable() { public void run() { pickMascotImage(); } }));
                fb.addView(switchItem(Words.CF_SW_MINIMAL_T, Words.CF_SW_MINIMAL_D, Mascot.minimal(MainActivity.this), true, new SwitchCB() {
                    public void onChanged(boolean v) {
                        Mascot.setMinimal(MainActivity.this, v);
                        toast(v ? Words.CF_MSG_MINIMAL_ON : Words.CF_MSG_MINIMAL_OFF);
                        applyMinimalGray(fb, v);   // ★87：开着＝常规那几项全灰
                    }
                }));
                fb.addView(fxSlider(Words.CF_MASCOT_ALPHA_T, Words.CF_MASCOT_ALPHA_D, 20, 100, 5, Math.round(Mascot.alpha(MainActivity.this) * 100), "%", new FXCB() {
                    public void onChanged(int v) { Mascot.setAlpha(MainActivity.this, v / 100f); }
                }));
                final TextView[] posDesc = new TextView[1];
                fb.addView(listItem(Words.CF_MASCOT_POS_T,
                        posDescText(),
                        new Runnable() { public void run() { cyclePos(posDesc[0]); } }, posDesc));
                final TextView[] hideDesc = new TextView[1];
                fb.addView(listItem(Words.CF_HIDE_T,
                        hideDescText(),
                        new Runnable() { public void run() { cycleHideMode(hideDesc[0]); } }, hideDesc));
                final TextView[] hidePkgsDesc = new TextView[1];
                minimalKeepRow = listItem(Words.CF_HIDE_PKGS_T,
                        hidePkgsDescText(),
                        new Runnable() { public void run() { AppPickActivity.open(MainActivity.this); } }, hidePkgsDesc);
                hidePkgsDescLive = hidePkgsDesc[0];
                fb.addView(minimalKeepRow);   // ★88.6：让位名单不受极简左右（applyMinimalGray 里跳过这一行）
                fb.addView(switchItem(Words.CF_SW_MASCOT_VERT_T, Words.CF_SW_MASCOT_VERT_D, Mascot.vertPref(MainActivity.this), true, new SwitchCB() {
                    public void onChanged(boolean v) {
                        Mascot.setVert(MainActivity.this, v);
                        toast(v ? Words.CF_MSG_MASCOT_VERT_ON : Words.CF_MSG_MASCOT_VERT_OFF);
                        Mascot.preview(Words.CF_MASCOT_POS_T + "：" + Words.CF_MASCOT_POS_NAMES[Mascot.posMode(MainActivity.this)]);
                    }
                }));
                fb.addView(switchItem(Words.CF_SW_MASCOT_MIRROR_T, Words.CF_SW_MASCOT_MIRROR_D, Mascot.mirror(MainActivity.this), true, new SwitchCB() {
                    public void onChanged(boolean v) {
                        Mascot.setMirror(MainActivity.this, v);
                        toast(v ? Words.CF_MSG_MASCOT_MIRROR_ON : Words.CF_MSG_MASCOT_MIRROR_OFF);
                    }
                }));
                applyMinimalGray(fb, Mascot.minimal(MainActivity.this));   // ★87：极简开着时进页面就是灰的
                // ───────── ★88.8 动效（外面只留：开关／手感／高级参数 ›，最下面「恢复默认」） ─────────
                fb.addView(quiet13("动效（极简，白条收放）"));
                fb.addView(switchItem("动效", "开着：白条长成框、框缩回白条。关着：不做变形，直接出框／收框。",
                        Morph.enabled(MainActivity.this), true, new SwitchCB() {
                            public void onChanged(boolean v) {
                                Morph.setEnabled(MainActivity.this, v);
                                Mascot.logLine(MainActivity.this, "conf: 动效开关=" + (v ? "开（白条⇄框有变形）" : "关（不做变形，直接出终态）"));
                                toast(v ? "动效已开" : "动效已关（不做变形）");
                            }
                        }));
                final TextView[] feelDesc = new TextView[1];
                fb.addView(listItem("手感", feelDescText(), new Runnable() { public void run() {
                    int now = Morph.preset(MainActivity.this);
                    int next = (now < 0) ? Morph.PRE_STD : (now + 1) % Morph.PRESET_NAMES.length;
                    Morph.applyPreset(MainActivity.this, next);
                    Mascot.morphReload();
                    // ★88.9：标题与下面所有滑杆立刻按新一套刷新（不是退出重进才对）
                    Mascot.logLine(MainActivity.this, "conf: 手感→" + Morph.PRESET_NAMES[next] + "（时长=" + Morph.dur(MainActivity.this)
                            + "ms 曲线=" + Morph.EASE_NAMES[Morph.easeMode(MainActivity.this)] + " 线宽=" + Morph.lineW(MainActivity.this)
                            + "dp 圆角=" + Morph.radius(MainActivity.this) + "dp 鼓出=" + Morph.bulge(MainActivity.this) + "）");
                    rebuildConfUi();
                    confFlash("手感：" + Morph.PRESET_NAMES[next]);
                } }, feelDesc));

                // ── 高级参数（折叠：点开才是所有滑杆） ──
                fb.addView(foldSection("高级参数", "时长／曲线／线宽／圆角／鼓出／颜色／最大宽高", new FoldBody() {
                    public void build(final LinearLayout gb) {
                        gb.addView(fxSlider("动效总时长", "白条长成框／框缩回白条各用这么久。改完下一次播放就生效。", 150, 900, 10, Morph.dur(MainActivity.this), " ms", new FXCB() {
                            public void onChanged(int v) { Morph.si(MainActivity.this, Morph.K_DUR, v); morphManual(feelDesc[0]); Mascot.morphReload(); }
                        }));
                        final TextView[] easeDesc = new TextView[1];
                        gb.addView(listItem("缓动曲线", easeDescText(), new Runnable() { public void run() {
                            int n = (Morph.easeMode(MainActivity.this) + 1) % Morph.EASE_NAMES.length;
                            Morph.si(MainActivity.this, Morph.K_EASE, n);
                            morphManual(feelDesc[0]);
                            if (easeDesc[0] != null) easeDesc[0].setText(easeDescText());
                            Mascot.morphReload();
                        } }, easeDesc));
                        gb.addView(fxSlider("贝塞尔 x1", "自定义曲线的四个控制点（0~100）；拖任意一个就自动切到「自定义贝塞尔」。", 0, 100, 1, Math.round(Morph.bez(MainActivity.this)[0] * 100f), "%", new FXCB() {
                            public void onChanged(int v) { setBezPart(0, v); morphManual(feelDesc[0]); }
                        }));
                        gb.addView(fxSlider("贝塞尔 y1", "控制点纵坐标（0~100）；越大起步越快。", 0, 100, 1, Math.round(Morph.bez(MainActivity.this)[1] * 100f), "%", new FXCB() {
                            public void onChanged(int v) { setBezPart(1, v); morphManual(feelDesc[0]); }
                        }));
                        gb.addView(fxSlider("贝塞尔 x2", "控制点横坐标（0~100）；越小收尾越慢。", 0, 100, 1, Math.round(Morph.bez(MainActivity.this)[2] * 100f), "%", new FXCB() {
                            public void onChanged(int v) { setBezPart(2, v); morphManual(feelDesc[0]); }
                        }));
                        gb.addView(fxSlider("贝塞尔 y2", "控制点纵坐标（0~100）；100＝收尾平。", 0, 100, 1, Math.round(Morph.bez(MainActivity.this)[3] * 100f), "%", new FXCB() {
                            public void onChanged(int v) { setBezPart(3, v); morphManual(feelDesc[0]); }
                        }));
                        gb.addView(fxSlider("线宽", "白条粗细（3~16dp，不会变成看不见）；框边那道线的粗细见下面「描边粗细」。", 3, 16, 1, Morph.lineW(MainActivity.this), " dp", new FXCB() {
                            public void onChanged(int v) { Morph.si(MainActivity.this, Morph.K_W, v); morphManual(feelDesc[0]); Mascot.morphReload(); }
                        }));
                        gb.addView(fxSlider("圆角半径", "框的四个角（0~40dp）；0＝直角。线宽 >12dp 时下限 12dp（不许细长胶囊）。", 0, 40, 1, Morph.radius(MainActivity.this), " dp", new FXCB() {
                            public void onChanged(int v) { Morph.si(MainActivity.this, Morph.K_R, v); morphManual(feelDesc[0]); Mascot.morphReload(); }
                        }));
                        gb.addView(fxSlider("展开回弹", "回弹／弹性曲线的过冲幅度（0~100）；0＝不过冲。时长 <200ms 时上限 40（短时长配大回弹＝看着抽）。", 0, 100, 5, Morph.bounce(MainActivity.this), "", new FXCB() {
                            public void onChanged(int v) { Morph.si(MainActivity.this, Morph.K_BOUNCE, v); morphManual(feelDesc[0]); Mascot.morphReload(); }
                        }));
                        gb.addView(fxSlider("文字淡入时机", "变形到百分之几时文字开始淡入（0~90%）。", 0, 90, 5, Morph.textAt(MainActivity.this), "%", new FXCB() {
                            public void onChanged(int v) { Morph.si(MainActivity.this, Morph.K_TEXT, v); morphManual(feelDesc[0]); Mascot.morphReload(); }
                        }));
                        gb.addView(fxSlider("鼓出程度", "0＝宽高同步；越大越像气球从管口吹出来（宽先鼓、高后跟），0~100。", 0, 100, 5, Morph.bulge(MainActivity.this), "", new FXCB() {
                            public void onChanged(int v) { Morph.si(MainActivity.this, Morph.K_BULGE, v); morphManual(feelDesc[0]); Mascot.morphReload(); }
                        }));
                        gb.addView(fxSlider("描边粗细", "框边／白条边那道线的粗细（1~6dp，默认极细 1dp）。线宽只管白条粗细。", 1, 6, 1, Morph.strokeW(MainActivity.this), " dp", new FXCB() {
                            public void onChanged(int v) { Morph.si(MainActivity.this, Morph.K_STROKE, v); Morph.markCustom(MainActivity.this); if (feelDesc[0] != null) feelDesc[0].setText(feelDescText()); Mascot.morphReload(); }
                        }));
                        gb.addView(colorItem("描边颜色", "点这一行循环换色（只换那道极细的边）。", Morph.K_STROKE_C, C_STROKE, Morph.DEF_STROKE_C));
                        gb.addView(colorItem("填充颜色", "点这一行循环换色（框内与白条本体）。", Morph.K_FILL_C, C_FILL, Morph.DEF_FILL_C));
                        gb.addView(colorItem("文字颜色", "点这一行循环换色（失败仍是红色）。", Morph.K_TEXT_C, C_TEXT, Morph.DEF_TEXT_C));
                        gb.addView(fxSlider("内容最大宽", "框宽按文字实测；超过这个宽度就截断加省略号（100~260dp）。", 100, 260, 5, Morph.maxWdp(MainActivity.this), " dp", new FXCB() {
                            public void onChanged(int v) { Morph.si(MainActivity.this, Morph.K_MAXW, v); Morph.markCustom(MainActivity.this); if (feelDesc[0] != null) feelDesc[0].setText(feelDescText()); Mascot.morphReload(); }
                        }));
                        gb.addView(fxSlider("内容最大高", "框高的上限（40~140dp）。框高＝行数×行高＋padding，按内容实测；40dp 大约两行就顶格。", 40, 140, 2, Morph.maxHdp(MainActivity.this), " dp", new FXCB() {
                            public void onChanged(int v) { Morph.si(MainActivity.this, Morph.K_MAXH, v); Morph.markCustom(MainActivity.this); if (feelDesc[0] != null) feelDesc[0].setText(feelDescText()); Mascot.morphReload(); }
                        }));
                    }
                }));

                fb.addView(confBtn("恢复默认", "会把手感与所有参数还原（时长／曲线／线宽／圆角／鼓出／描边／填充／文字色／最大宽高），点了立刻生效", new Runnable() { public void run() {
                    Morph.applyDefaults(MainActivity.this);
                    Mascot.morphReload();
                    // ★88.8：页面上的数值也要跟着回默认（重建配置页，滚动位置尽量留住）
                    // ★88.9：重建＝就地换页 ⇒ 先重建、再往新建的那一行写反馈
                    rebuildConfUi();
                    confFlash("已恢复默认（标准手感）");
                    refresh();
                } }));
                fb.addView(confBtn("播一遍", "白条收放，连播 2 个来回（极简模式可用）", new Runnable() { public void run() {
                    if (!Mascot.minimal(MainActivity.this)) { toast("先打开上面的「极简模式」，再点「播一遍」"); return; }
                    Mascot.morphPlay();
                } }));
            }
        }));

        // ── 折叠：无障碍与自启（看门狗 ＋ 开机自启 ＋ 内存自保那一行）──
        c.addView(foldSection(Words.CF_FOLD_WD, A11yWatchdog.isOn(this) ? Words.CF_FOLD_ON : Words.CF_FOLD_OFF, new FoldBody() {
            public void build(final LinearLayout fb) {
                fb.addView(switchItem(Words.CF_SW_WD_T,
                        Words.CF_SW_WD_D,
                        A11yWatchdog.isOn(MainActivity.this), true, new SwitchCB() {
                            public void onChanged(boolean v) {
                                A11yWatchdog.setOn(MainActivity.this, v);
                                toast(v ? Words.CF_MSG_WD_ON : Words.CF_MSG_WD_OFF);
                                refresh();
                            }
                        }));
                tvWd = quiet13(A11yWatchdog.statusLine(MainActivity.this));
                fb.addView(tvWd);
                fb.addView(listItem(Words.CF_ITEM_WD_CHECK, Words.CF_ITEM_WD_CHECK_D, new Runnable() { public void run() { wdCheckNow(); } }));
                fb.addView(quiet13(Words.CF_A11Y_LINK_LEAD));
                fb.addView(confBtn(Words.MA_BTN_A11Y_FIX, Words.MA_BTN_A11Y_FIX_D, new Runnable() { public void run() { fixA11y(); } }));
                fb.addView(switchItem(Words.CF_SW_BOOT_T,
                        Words.CF_SW_BOOT_D,
                        BootReceiver.isOn(MainActivity.this), true, new SwitchCB() {
                            public void onChanged(boolean v) {
                                BootReceiver.setOn(MainActivity.this, v);
                                toast(v ? Words.CF_MSG_BOOT_ON : Words.CF_MSG_BOOT_OFF);
                                refresh();
                            }
                        }));
                fb.addView(listItem(Words.CF_BOOT_GOTO_T, Words.CF_BOOT_GOTO_D, new Runnable() { public void run() {
                    if (!BootReceiver.openAutoStart(MainActivity.this)) toast(Words.CF_BOOT_GOTO_FAIL);
                } }));
                tvMem = quiet13(Words.MEM_LINE_PREFIX + MemGuard.statusLine(MainActivity.this));
                fb.addView(tvMem);
            }
        }));

        // ── 折叠：外观（主题 ＋ 极夜）──
        c.addView(foldSection(Words.CF_SEC_LOOK, nightActive() ? Words.CF_FOLD_NIGHT + (blackOn() ? Words.CF_FOLD_BLACK : "") : Words.CF_FOLD_LIGHT, new FoldBody() {
            public void build(final LinearLayout fb) {
                fb.addView(themeChooser());
                final boolean night = nightActive();
                View blackRow = switchItem(Words.CF_BLACK_T,
                        night ? Words.CF_BLACK_D_ON : Words.CF_BLACK_D_OFF,
                        blackOn(), night, new SwitchCB() {
                            public void onChanged(boolean v) { setBlack(v); }
                        });
                fb.addView(blackRow);
            }
        }));

        // ── 折叠：底部导航与动效（参数 ＋ 两根蒙版滑杆 ＋ 三根动效滑杆）──
        c.addView(foldSection(Words.CF_FOLD_NAVFX, null, new FoldBody() {
            public void build(final LinearLayout fb) {
                fb.addView(quiet13(Words.CF_NAV_D));
                fb.addView(navParamChooser());
                fb.addView(fxSlider(Words.CF_MASK_ALPHA_T, Words.CF_MASK_ALPHA_D, 2, 20, 1, Math.round(maskAlpha() * 100), "%", new FXCB() {
                    public void onChanged(int v) {
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putFloat(K_MASK_ALPHA, v / 100f).apply();
                        if (navGlass != null) navGlass.setMaskAlpha(v / 100f);
                    }
                }));
                fb.addView(fxSlider(Words.CF_MASK_GAP_T, Words.CF_MASK_GAP_D, 0, 400, 10, maskGapMs(), " ms", new FXCB() {
                    public void onChanged(int v) {
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(K_MASK_GAP, v).apply();
                        if (navGlass != null) navGlass.setMaskGapMs(v);
                    }
                }));
                fb.addView(quiet13(Words.CF_FX_INTRO));
                fb.addView(fxSlider(Words.CF_FX1_TITLE, Words.CF_FX1_HINT, 2, 30, 1, Math.round(fxAmp() * 100), Words.CF_FX1_UNIT, new FXCB() {
                    public void onChanged(int v) {
                        float a = v / 100f;
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putFloat(K_FX_AMP, a).apply();
                        if (navGlass != null) navGlass.setStretchAmp(a);
                    }
                }));
                fb.addView(fxSlider(Words.CF_FX2_TITLE, Words.CF_FX2_HINT, 100, 260, 5, Math.round(fxPeak() * 100), Words.CF_FX2_UNIT, new FXCB() {
                    public void onChanged(int v) {
                        float p = v / 100f;
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putFloat(K_FX_PEAK, p).apply();
                        if (navGlass != null) navGlass.setIconPeak(p);
                    }
                }));
                fb.addView(fxSlider(Words.CF_FX3_TITLE, Words.CF_FX3_HINT, 520, 1200, 20, Math.round(fxMs()), Words.CF_FX3_UNIT, new FXCB() {
                    public void onChanged(int v) {
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putFloat(K_FX_MS, v).apply();
                        if (navGlass != null) navGlass.setIconMs(v);
                    }
                }));
                fb.addView(listItem(Words.CF_ITEM_FX_RESET, Words.CF_ITEM_FX_RESET_D, new Runnable() { public void run() {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putFloat(K_FX_AMP, 0.12f).putFloat(K_FX_PEAK, 1.65f).putFloat(K_FX_MS, 640f).apply();
                    applyNavFx();
                    rebuildConfUi();
                    toast(Words.CF_MSG_FX_RESET);
                } }));
            }
        }));

        // ── 折叠：应用图标 ──
        c.addView(foldSection(Words.CF_SEC_ICON, null, new FoldBody() {
            public void build(final LinearLayout fb) {
                fb.addView(quiet13(Words.CF_ICON_LEAD));
                fb.addView(iconChooser());
                fb.addView(listItem(Words.CF_ICON_PIN_T, Words.CF_ICON_PIN_D, new Runnable() { public void run() { pickIconImage(); } }));
            }
        }));

        // ── 折叠：更新（母标题右侧常显「上次检查 15:31」；展开有开关／立即检查／源／状态／清除记录）──
        {
            final TextView[] tagOut = new TextView[1];
            c.addView(foldSection(Words.UPD_SEC, updTagText(), new FoldBody() {
                public void build(final LinearLayout fb) {
                    fb.addView(switchItem(Words.UPD_SW_T, Words.UPD_SW_D, UpdateCheck.on(MainActivity.this), true, new SwitchCB() {
                        public void onChanged(boolean v) {
                            UpdateCheck.setOn(MainActivity.this, v);
                            toast(v ? Words.UPD_MSG_ON : Words.UPD_MSG_OFF);
                        }
                    }));
                    fb.addView(listItem(Words.UPD_NOW_T, Words.UPD_NOW_D, new Runnable() { public void run() { checkUpdateManual(); } }));
                    fb.addView(listItem(Words.UPD_API_T, String.format(java.util.Locale.ROOT, Words.UPD_API_D_FMT, UpdateCheck.endpoint(MainActivity.this)), new Runnable() { public void run() { askUpdApi(); } }));
                    fb.addView(updStateCard());
                }
            }, tagOut));
            tvUpdFoldTag = tagOut[0];
        }

        // ── 维护（首屏最后一块，常显）──
        c.addView(sectionTitle(Words.CF_SEC_MAINT));
        c.addView(listItem(Words.CF_STOP_T, Words.CF_STOP_D, new Runnable() { public void run() { confirmStopAll(); } }));
        c.addView(quiet(Words.CF_STOP_NOTE));

        ScrollView sv = new ScrollView(this);
        sv.addView(c);
        pageConf.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        svConf = sv;
    }

    // ═════════ 79 号单返工：配置页折叠块（一个 FoldSection，处处复用） ═════════
    // 口径（用户 2026-10-17 验收反馈）：母标题一整行可点、右侧箭头（收起 ∨／展开 ∧）、
    //   高度动画 200ms 与箭头旋转同步；母标题 15sp 中等粗＋薰衣草紫，展开后的正文 13sp 常规；
    //   子项沿用「高级页那四个选项」的卡片样式（listItem／switchItem／confBtn），不另创一套；
    //   默认全收起；关键状态挂在母标题右侧，收起也看得见。
    interface FoldBody { void build(LinearLayout body); }

    static final int FOLD_MS = 200;                 // 高度动画与箭头旋转同一个时长
    final java.util.HashMap<String, Boolean> foldOpen = new java.util.HashMap<>();   // 标题 → 是否展开（会话内记住）

    /** 母标题的薰衣草紫：与统一对话框的强调色同一族（浅色深一点、深色/极夜亮一点） */
    int foldTitleColor() { return DLG_ACCENT[dlgTheme()]; }

    View foldSection(String title, String status, FoldBody body) { return foldSection(title, status, body, null); }

    View foldSection(final String title, String status, final FoldBody body, TextView[] tagOut) {
        final boolean open = Boolean.TRUE.equals(foldOpen.get(title));

        LinearLayout wrap = column();
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-1, -2);
        wlp.bottomMargin = dp(4);
        wrap.setLayoutParams(wlp);

        final LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(6), dp(14), dp(6), dp(12));
        header.setClickable(true);
        header.setFocusable(true);
        header.setContentDescription("折叠：" + title);
        ripple(header);

        TextView tvTitle = txt(title, 15, foldTitleColor());
        tvTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tvTitle.setSingleLine(true);
        header.addView(tvTitle);

        TextView tvTag = null;
        if (status != null && status.length() > 0) {
            tvTag = txt(status, 12, colVariant());
            tvTag.setSingleLine(true);
            tvTag.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
            tlp.leftMargin = dp(10);
            header.addView(tvTag, tlp);
        } else {
            View sp = new View(this);
            header.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        }
        if (tagOut != null && tagOut.length > 0) tagOut[0] = tvTag;

        final ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_chevron);
        arrow.setImageTintList(ColorStateList.valueOf(foldTitleColor()));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(18), dp(18));
        alp.leftMargin = dp(6);
        arrow.setLayoutParams(alp);
        arrow.setRotation(open ? 270f : 90f);       // 收起＝∨（朝下）；展开＝∧（朝上）
        header.addView(arrow);

        wrap.addView(header);

        final LinearLayout box = column();
        body.build(box);
        box.setVisibility(open ? View.VISIBLE : View.GONE);
        wrap.addView(box);

        header.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { toggleFold(header, box, arrow, title); }
        });
        tvTitle.setOnClickListener(new View.OnClickListener() {      // 标题本身也点得动（AI 的 tap_text 直点文字）
            public void onClick(View v) { toggleFold(header, box, arrow, title); }
        });
        return wrap;
    }

    /** 展开／收起：高度 200ms 动画 ＋ 箭头 200ms 旋转，同一时长同步走完。 */
    void toggleFold(final View header, final LinearLayout box, final ImageView arrow, String title) {
        final boolean willOpen = box.getVisibility() != View.VISIBLE;
        foldOpen.put(title, Boolean.valueOf(willOpen));
        int w = header.getWidth();
        if (w <= 0) w = getResources().getDisplayMetrics().widthPixels - dp(36);
        box.setVisibility(View.VISIBLE);
        int from = willOpen ? 0 : box.getHeight();
        if (!willOpen && from <= 0) {
            box.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            from = box.getMeasuredHeight();
        }
        int target = 0;
        if (willOpen) {
            box.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            target = box.getMeasuredHeight();
        }
        android.view.ViewGroup.LayoutParams lp0 = box.getLayoutParams();
        lp0.height = from;
        box.setLayoutParams(lp0);
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofInt(from, target);
        va.setDuration(FOLD_MS);
        va.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(android.animation.ValueAnimator a) {
                android.view.ViewGroup.LayoutParams l = box.getLayoutParams();
                l.height = ((Integer) a.getAnimatedValue()).intValue();
                box.setLayoutParams(l);
            }
        });
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                android.view.ViewGroup.LayoutParams l = box.getLayoutParams();
                l.height = -2;                 // WRAP_CONTENT：动画结束就放开，之后内容变高照常
                box.setLayoutParams(l);
                if (!willOpen) box.setVisibility(View.GONE);
            }
        });
        va.start();
        arrow.animate().rotation(willOpen ? 270f : 90f).setDuration(FOLD_MS).start();
    }

    /** 折叠块里的正文（13sp 常规——用户要的层级：母标题 15sp、正文 13sp） */
    TextView quiet13(String s) {
        TextView t = txt(s, 13, colVariant());
        t.setPadding(dp(4), dp(2), dp(4), dp(8));
        t.setLineSpacing(dp(2), 1f);
        return t;
    }

    /**
     * 更新块的状态卡：两行各自占一行（不挤在一条里、也不跟别的文字叠在一起）＋ 一行「清除更新记录」。
     * 上一版是一行长文本直接塞在页尾，用户第一眼都没看见 ⇒ 这里换成卡片、两行、可读性优先。
     */
    View updStateCard() {
        final MaterialCardView cardV = card(false);
        LinearLayout in = column();
        in.setPadding(dp(16), dp(14), dp(14), dp(14));
        tvUpdState = txt("", 13, col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6));
        in.addView(tvUpdState);
        tvUpdSeen = txt("", 13, colVariant());
        tvUpdSeen.setPadding(0, dp(4), 0, 0);
        in.addView(tvUpdSeen);
        TextView clr = txt(Words.UPD_CLR_T, 13, foldTitleColor());
        clr.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clr.setPadding(0, dp(12), 0, dp(2));
        clr.setClickable(true);
        clr.setFocusable(true);
        clr.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { clearUpdRecords(); }
        });
        in.addView(clr);
        TextView clrD = txt(Words.UPD_CLR_D, 12, colVariant());
        clrD.setPadding(0, dp(2), 0, 0);
        in.addView(clrD);
        cardV.addView(in);
        // 长按这张卡也行（用户说的"长按那行"）
        cardV.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { clearUpdRecords(); return true; }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        cardV.setLayoutParams(lp);
        refreshUpdState();
        return cardV;
    }

    /** 清掉「上次检查／上次提示的版本／上次用的源」（含验收侧那份），界面立刻跟着变。 */
    void clearUpdRecords() {
        UpdateCheck.clearRecords(this);
        refreshUpdState();
        toast(Words.UPD_CLR_OK);
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
    // ───────── 73 号单（返工）：应用图标 ─────────
    static final int REQ_ICON = 6402;

    void pickIconImage() {
        try {
            Intent mi = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            mi.addCategory(Intent.CATEGORY_OPENABLE);
            mi.setType("image/*");
            startActivityForResult(mi, REQ_ICON);
        } catch (Throwable t) {
            toast(Words.CF_ICON_PIN_PICKFAIL);
        }
    }

    void paintIconChoice(MaterialCardView[] cards, TextView[] tvs, int cur) {
        for (int i = 0; i < cards.length; i++) {
            boolean on = (i == cur);
            try {
                cards[i].setStrokeWidth(dp(on ? 2 : 1));
                cards[i].setStrokeColor(on ? col(com.google.android.material.R.attr.colorPrimary, 0xFF6C8CFF)
                                           : col(com.google.android.material.R.attr.colorOutline, 0x339AA0A6));
            } catch (Throwable t) { }
            tvs[i].setText(getString(Icons.LABEL[i]) + (on ? " ✓" : ""));
            tvs[i].setTextColor(on ? col(com.google.android.material.R.attr.colorPrimary, 0xFF6C8CFF) : colVariant());
        }
    }

    View iconChooser() {
        final int n = Icons.ALIAS.length;
        final int[] cur = new int[]{ Icons.current(this) };
        final MaterialCardView[] cards = new MaterialCardView[n];
        final TextView[] tvs = new TextView[n];
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        for (int i = 0; i < n; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
                rlp.topMargin = dp(6);
                wrap.addView(row, rlp);
            }
            final int idx = i;
            MaterialCardView cc = card(true);
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(dp(10), dp(12), dp(10), dp(12));
            ImageView iv = new ImageView(this);
            iv.setImageResource(Icons.PREVIEW[idx]);
            int px = dp(56);
            iv.setLayoutParams(new LinearLayout.LayoutParams(px, px));
            cell.addView(iv);
            TextView tv = txt(getString(Icons.LABEL[idx]), 12, colVariant());
            tv.setPadding(0, dp(8), 0, 0);
            cell.addView(tv);
            cc.addView(cell);
            cards[idx] = cc;
            tvs[idx] = tv;
            cc.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (idx == cur[0]) { toast(String.format(Words.CF_MSG_ICON_FMT, getString(Icons.LABEL[idx]))); return; }
                    toast(Icons.apply(MainActivity.this, idx));
                    cur[0] = idx;
                    paintIconChoice(cards, tvs, idx);
                }
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1f);
            clp.leftMargin = dp(5);
            clp.rightMargin = dp(5);
            row.addView(cc, clp);
        }
        paintIconChoice(cards, tvs, cur[0]);
        return wrap;
    }

    void buildAdv() {
        LinearLayout c = column();
        c.setPadding(dp(18), dp(6), dp(18), pagePadBottom());

        cAdv = c;
        c.addView(sectionTitle(Words.MA_SEC_TEST));
        c.addView(listItem(Words.MA_ITEM_ISLAND, Words.MA_ITEM_ISLAND_D, new Runnable() { public void run() {
            String r = Notifier.island(MainActivity.this, 4, 0, "HEALTH_REGISTER", Words.MA_ISLAND_T, Words.MA_ISLAND_C, true);
            toast(Words.MA_MSG_ISLAND + r);
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
            Notifier.cancel(MainActivity.this, 9);
            toast(Words.MA_MSG_ENDTEST);
        } }));

        // 76 号单：打包 App 板块（原首页位置 → 高级页；标题由 buildAppSection 自带）
        c.addView(quiet(Words.MA_BUILD_ADV_LEAD));
        buildAppSection(c);
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

    /** 65 号单 C 线：气泡位置——点一行循环切换（斜 45°→上→下→左→右），立刻生效。 */
    void cyclePos(TextView liveDesc) {
        int next = (Mascot.posMode(this) + 1) % Words.CF_MASCOT_POS_NAMES.length;
        Mascot.setPosMode(this, next);
        toast(String.format(java.util.Locale.ROOT, Words.CF_MSG_MASCOT_POS_FMT, Words.CF_MASCOT_POS_NAMES[next]));
        Mascot.preview(Words.CF_MASCOT_POS_T + "：" + Words.CF_MASCOT_POS_NAMES[next]);   // 65 修：不重建页面（原来把配置页滚回顶部），改成当场演一下
        if (liveDesc != null) liveDesc.setText(posDescText());   // ★66：就改这一行文字（不重建页面、不滚回顶部）
    }

    /** ★66：气泡方向那一行的说明（含"当前："，供就地更新）。 */
    String posDescText() {
        return Words.CF_MASCOT_POS_D + "\n当前：" + Words.CF_MASCOT_POS_NAMES[Mascot.posMode(this)];
    }

    /** ★87 极简模式：开着时常规那几项全灰不可选（第 0 项＝显示小形象、第 1 项＝换图、第 2 项＝极简开关，这两个永远可点）。 */
    void applyMinimalGray(LinearLayout fb, boolean on) {
        if (fb == null) return;
        try {
            for (int i = 3; i < fb.getChildCount(); i++) {
                View v = fb.getChildAt(i);
                v.setAlpha(on ? 0.38f : 1f);
                if (v == minimalKeepRow) { v.setAlpha(1f); setEnabledDeep(v, true); continue; }   // ★88.6：让位名单始终可选可用
                setEnabledDeep(v, !on);
            }
            Mascot.logLine(this, "mascot: 极简灰=" + (on ? "开（常规项不可选）" : "关（恢复可选）") + " 项数=" + Math.max(0, fb.getChildCount() - 3));
        } catch (Throwable t) { }
    }

    /** ★88：缓动那一行的说明（含当前）。 */
    String easeDescText() {
        int m = Morph.easeMode(this);
        String extra = (m == 4) ? "（" + Morph.bezStr(this) + "）" : "";
        return "点一行循环切换：线性／缓出／回弹／弹性／自定义贝塞尔\n当前：" + Morph.EASE_NAMES[m] + extra;
    }

    /** ★88.8：手感那行标题下面的一行小字（「当前：标准」／「当前：自定义」）。 */
    String feelDescText() {
        return "点一下换一套：轻快／标准／柔和。\n当前：" + Morph.presetName(this);
    }

    /** ★88.8：手动动了任何参数 ⇒ 手感变「自定义」，那行小字跟着改（不让"当前：标准"说谎）。 */
    void morphManual(TextView feel) {
        Morph.markCustom(this);
        if (feel != null) feel.setText(feelDescText());
    }

    /** ★88：改自定义贝塞尔的某一段（0~3），并自动切到「自定义贝塞尔」。 */
    void setBezPart(int idx, int pct) {
        float[] b = Morph.bez(this);
        if (idx >= 0 && idx < 4) b[idx] = pct / 100f;
        Morph.setBez(this, b[0], b[1], b[2], b[3]);
        Morph.si(this, Morph.K_EASE, 4);
        Morph.markCustom(this);
        Mascot.morphReload();
    }

    // ★88.5：颜色＝点一行循环换色（预置几档，够调、可回退；不引入颜色选择器）
    static final int[] C_STROKE = {0xFFD5D7DB, 0xFFBFC3C9, 0xFF8A8F96, 0xFF000000, 0xFF4A90E2, 0xFFE3E5E9};
    static final int[] C_FILL = {0xFFFFFFFF, 0xFFF6F7F9, 0xFFEDEFF2, 0xFF000000, 0xFF1E1E1E, 0xFF4A90E2};
    static final int[] C_TEXT = {0xFF1B1B1B, 0xFF000000, 0xFF6B6B6B, 0xFFFFFFFF, 0xFF4A90E2, 0xFFD0342C};

    static String hexColor(int c) { return String.format(java.util.Locale.ROOT, "#%06X", c & 0xFFFFFF); }

    View colorItem(String title, String hint, final String key, final int[] presets, final int def) {
        final TextView[] d = new TextView[1];
        return listItem(title, hint + "\n当前：" + hexColor(Morph.color(this, key, def)), new Runnable() {
            public void run() {
                int now = Morph.color(MainActivity.this, key, def);
                int idx = 0;
                for (int i = 0; i < presets.length; i++) if (presets[i] == now) idx = i;
                int next = presets[(idx + 1) % presets.length];
                Morph.setColor(MainActivity.this, key, next);
                Morph.markCustom(MainActivity.this);
                Mascot.morphReload();
                if (d[0] != null) d[0].setText(hint + "\n当前：" + hexColor(next));
                toast(title + "：" + hexColor(next));
            }
        }, d);
    }

    void setEnabledDeep(View v, boolean en) {
        v.setEnabled(en);
        // ★88.7：绝不碰 clickable——子 View 一旦 clickable=true 又没有自己的 listener，就会把点击吃掉（让位名单点不动的根因）
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) setEnabledDeep(g.getChildAt(i), en);
        }
    }
    /** ★69.5：在 AI 客户端里的表现——点一行循环切换三档（只留细条／全隐／只藏气泡）。 */
    void cycleHideMode(TextView liveDesc) {
        int next = (Mascot.hideMode(this) + 1) % Mascot.HIDE_MODE_NAMES.length;
        Mascot.setHideMode(this, next);
        toast(String.format(java.util.Locale.ROOT, Words.CF_MSG_HIDE_MODE_FMT, Mascot.HIDE_MODE_NAMES[next]));
        if (liveDesc != null) liveDesc.setText(hideDescText());
    }

    String hideDescText() {
        return Words.CF_HIDE_D + "\n当前：" + Mascot.HIDE_MODE_NAMES[Mascot.hideMode(this)];
    }

    String hidePkgsDescText() {
        int n = 0;
        try {
            String v = Mascot.hidePkgs(this);
            if (v != null) {
                String[] a = v.split(",");
                for (int i = 0; i < a.length; i++) if (a[i].trim().length() > 0) n++;
            }
        } catch (Throwable t) { }
        return Words.CF_HIDE_PKGS_D + "\n" + String.format(java.util.Locale.ROOT, Words.CF_HIDE_PKGS_COUNT_FMT, n);
    }

    /** ★69.5：隐藏名单＝用户自己填的包名（换客户端不用找人改代码）。 */
    void askHidePkgs(final TextView liveDesc) {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setSingleLine(true);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        et.setText(Mascot.hidePkgs(this));
        try { et.setSelection(et.getText().length()); } catch (Throwable t) { }
        reticleDialog(Words.CF_HIDE_PKGS_DLG_T, Words.CF_HIDE_PKGS_DLG_ASK, et, Words.CF_DLG_CANCEL, Words.CF_DLG_OK, new Runnable() {
            public void run() {
                String nm = et.getText() == null ? "" : et.getText().toString().trim();
                Mascot.setHidePkgs(MainActivity.this, nm);
                toast(Words.CF_MSG_HIDE_PKGS_A + Mascot.hidePkgs(MainActivity.this));
                if (liveDesc != null) liveDesc.setText(hidePkgsDescText());
            }
        });
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

    /** 33 号单：滑杆值变了 → 重建配置页（滑杆位置与数值刷新）。
     *  ★88.9：原实现只调 showTab(1)，而 pageConf 只在启动时 buildConf() 建过一次
     *          ⇒ 点「恢复默认」／换手感后屏上数字根本不刷新（用户痛点「不知道刚调了什么」）。
     *          现在真正就地重建配置页：保留滚动位置与折叠展开状态，点完那一刻就是新值。 */
    void rebuildConfUi() {
        try {
            if (pageConf == null) return;
            final int y = (svConf != null) ? svConf.getScrollY() : 0;
            pageConf.removeAllViews();
            svConf = null;
            buildConf();
            if (svConf != null) {
                final ScrollView s = svConf;
                s.post(new Runnable() { public void run() { try { s.scrollTo(0, y); } catch (Throwable t) { } } });
            }
            Mascot.logLine(this, "conf: 配置页已就地重建（滚动y=" + y + " 手感=" + Morph.presetName(this)
                    + " 时长=" + Morph.dur(this) + "ms 线宽=" + Morph.lineW(this) + "dp 圆角=" + Morph.radius(this)
                    + "dp 鼓出=" + Morph.bulge(this) + " 最大高=" + Morph.maxHdp(this) + "dp 动效=" + Morph.enabled(this) + "）");
        } catch (Throwable t) { }
    }

    /** ★88.9：配置页顶部一句可见反馈（1.6 秒后自己消失）：不弹系统 toast、不抢前台。 */
    void confFlash(String s) {
        try {
            if (tvConfFlash == null) return;
            tvConfFlash.removeCallbacks(confFlashClear);
            tvConfFlash.setText(s == null ? "" : s);
            tvConfFlash.setVisibility((s == null || s.length() == 0) ? View.GONE : View.VISIBLE);
            if (s != null && s.length() > 0) tvConfFlash.postDelayed(confFlashClear, 1600L);
            Mascot.logLine(this, "conf: 屏上反馈=" + s);
        } catch (Throwable t) { }
    }
    final Runnable confFlashClear = new Runnable() { public void run() {
        try { if (tvConfFlash != null) tvConfFlash.setVisibility(View.GONE); } catch (Throwable t) { }
    } };

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
    View listItem(String title, String desc, final Runnable r) { return listItem(title, desc, r, null); }

    /** 66：带 descOut 的版本——配置页要"就地更新那一行文字"（不重建页面、不滚回顶部）。 */
    View listItem(String title, String desc, final Runnable r, TextView[] descOut) {
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
            if (descOut != null) descOut[0] = d;   // 66：把这一行的说明文字交出去，供"就地更新"
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

    /** 86.6：打开 App＝重新挂手。划掉卡片后系统会把我们这条无障碍摘掉（实测 enabled_accessibility_services
     *  整个键都没了）；这一步把它写回，并如实说成没成。★后台线程做，主线程只负责界面先出来。 */
    void restoreA11yOnOpen() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    if (A11yLink.isConnected()) return;   // 已经连上，别吵
                    if (A11yLink.state(MainActivity.this) == A11yLink.ST_IN_LIST_NO_CONN) {
                        // 在册但没接上（划掉卡片后最常见）：删名→写回，逼系统重新绑一次
                        try { A11yLink.repair(MainActivity.this, true); } catch (Throwable x) { }
                    } else {
                        try { A11yWatchdog.tick(MainActivity.this, false); } catch (Throwable x) { }
                    }
                    // ★87（86.6 的账）：系统重新绑一次要时间，别立刻下结论——最多等 6 秒（每 200ms 看一眼）
                    boolean conn = false;
                    long t0 = System.currentTimeMillis();
                    for (int i = 0; i < 30; i++) {
                        conn = A11yLink.isConnected();
                        if (conn) break;
                        try { Thread.sleep(200L); } catch (Throwable x) { }
                    }
                    Mascot.logLine(MainActivity.this, "a11y: 打开即挂手，等 " + (System.currentTimeMillis() - t0) + "ms ⇒ " + (conn ? "已连上" : "仍未连上"));
                    final boolean ok = conn;
                    runOnUiThread(new Runnable() {
                        public void run() {
                            String m = ok ? Words.MA_A11Y_BACK_OK : Words.MA_A11Y_BACK_FAIL;
                            toast(m);
                            try { android.widget.Toast.makeText(MainActivity.this, m, android.widget.Toast.LENGTH_LONG).show(); } catch (Throwable t) { }
                        }
                    });
                } catch (Throwable t) { }
            }
        });
        t.setDaemon(true);
        t.start();
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
            refreshBuild();
            renderClusters();
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

    // ═════════ 74 号单（2026-10-16）：打包 App 板块 ＋ 工具按簇（未就绪整簇变灰、按钮贴脸） ═════════

    TextView tvBuildState = null;
    LinearLayout boxBuild = null;
    LinearLayout boxToolClusters = null;

    /** 打包 App：独立成段（状态一行 ＋ 按钮贴脸）。 */
    void buildAppSection(LinearLayout c) {
        c.addView(sectionTitle(Words.MA_SEC_BUILD));
        MaterialCardView cb = card(false);
        LinearLayout in = column();
        in.setPadding(dp(14), dp(14), dp(14), dp(14));
        tvBuildState = txt("…", 13, colVariant());
        tvBuildState.setLineSpacing(dp(3), 1f);
        in.addView(tvBuildState);
        boxBuild = column();
        in.addView(boxBuild);
        cb.addView(in);
        c.addView(cb);
    }

    /** 环境状态一变就重画：就绪＝打包/装包/看日志；没齐＝一键准备环境（按钮贴脸）。 */
    void refreshBuild() {
        if (boxBuild == null) return;
        boolean rdy = BuildEnv.ready(this);
        if (tvBuildState != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(rdy ? Words.MA_BUILD_READY : Words.MA_BUILD_NOTREADY).append("\n").append(BuildEnv.statusLine(this));
            if (!rdy) { String m = BuildEnv.missing(this); if (m.length() > 0) sb.append("\n").append(m.trim()); }
            File proj = BuildRunner.defaultProject(this);
            sb.append("\n工程：").append(new File(proj, "AndroidManifest.xml").exists() ? proj.getAbsolutePath() : (Words.MA_BUILD_NO_PROJ + proj.getAbsolutePath()));
            tvBuildState.setText(sb.toString());
        }
        boxBuild.removeAllViews();
        if (!rdy) {
            boxBuild.addView(confBtn(Words.MA_BUILD_BTN_PREP, Words.MA_BUILD_BTN_PREP_D, new Runnable() { public void run() { askPrepareEnv(); } }));
            return;
        }
        boxBuild.addView(confBtn(Words.MA_BUILD_BTN_GO, Words.MA_BUILD_BTN_GO_D, new Runnable() { public void run() { startBuild(); } }));
        boxBuild.addView(confBtn(Words.MA_BUILD_BTN_INSTALL, Words.MA_BUILD_BTN_INSTALL_D, new Runnable() { public void run() { installBuilt(); } }));
        boxBuild.addView(confBtn(Words.MA_BUILD_BTN_OPEN, BuildRunner.logFile(this).getAbsolutePath(), new Runnable() { public void run() { showBuildLog(); } }));
    }

    /** 缺件时的弹窗：为什么不可用／要什么／多大／从哪来 ＋ 一个按钮（口径由我们定死）。 */
    void askPrepareEnv() {
        reticleDialog(Words.MA_BUILD_DLG_T, Words.MA_BUILD_DLG_WHY, null,
                Words.MA_BUILD_DLG_LATER, Words.MA_BUILD_DLG_GO, new Runnable() { public void run() { runPrepareEnv(); } });
    }

    /** 真去准备：后台解 assets ＋ 下载 android.jar（进度走通知栏＋小形象气泡）。 */
    void runPrepareEnv() {
        toast(Words.MA_BUILD_DLG_RUN);
        new Thread(new Runnable() { public void run() {
            final String r = BuildEnv.prepare(MainActivity.this, BuildEnv.notifier(MainActivity.this));
            BuildEnv.notifyDone(MainActivity.this, r.length() == 0 ? "android.jar 就位（sha256 校验过），环境齐了" : r, r.length() != 0);
            ui.post(new Runnable() { public void run() {
                toast(r.length() == 0 ? Words.MA_BUILD_MSG_PREP_DONE : Words.MA_BUILD_MSG_PREP_FAIL + r);
                refreshBuild();
                renderClusters();
            } });
        } }).start();
    }

    void startBuild() {
        String r = BuildRunner.start(this, BuildRunner.defaultProject(this), null, false);
        if (r.length() > 0) { toast(Words.MA_BUILD_MSG_GO_FAIL + r); return; }
        toast(Words.MA_BUILD_MSG_GO);
        openLogView();
    }

    void installBuilt() {
        new Thread(new Runnable() { public void run() {
            final String r = BuildRunner.install(MainActivity.this);
            ui.post(new Runnable() { public void run() { toast(cut(r, 160)); refreshBuild(); } });
        } }).start();
    }

    void showBuildLog() { openLogView(); }

    /** 就地看日志（滚动文本）：跟 AI 侧 build action=status 同一份。 */
    void openLogView() {
        TextView tv = txt(BuildRunner.status(this), 12, colVariant());
        tv.setLineSpacing(dp(3), 1f);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(tv);
        reticleDialog("构建日志", null, sv, Words.CF_DLG_CANCEL, Words.CF_DLG_OK, null);
    }

    /** 工具按簇渲染：未就绪的整簇变灰 ＋ 写清为什么 ＋ 按钮贴脸。 */
    void renderClusters() {
        if (boxToolClusters == null) return;
        boxToolClusters.removeAllViews();
        boolean a11y = false;
        try { a11y = A11yLink.state(this) == A11yLink.ST_CONNECTED; } catch (Throwable t) { }
        boolean env = BuildEnv.ready(this);
        boolean shx = shxOk();
        int good = col(com.google.android.material.R.attr.colorPrimary, 0xFF6C8CFF);
        int dim = colVariant();
        for (int i = 0; i < ToolTags.clusterCount(); i++) {
            final int ci = i;
            String why = ToolTags.clusterWhy(i, a11y, env, shx);
            String[] tools = ToolTags.clusterTools(i);
            MaterialCardView cc = card(false);
            LinearLayout in = column();
            in.setPadding(dp(14), dp(12), dp(14), dp(12));
            TextView h = txt((why.length() == 0 ? "● " : "◌ ") + ToolTags.clusterName(i) + " · " + tools.length + " 个", 14, why.length() == 0 ? good : dim);
            h.setTypeface(null, Typeface.BOLD);
            in.addView(h);
            for (int k = 0; k < tools.length; k++) {
                String ic = ToolTags.icon(tools[k]);
                TextView row = txt((ic.length() == 0 ? "" : ic + " ") + ToolTags.label(tools[k]), 13, why.length() == 0
                        ? col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6) : dim);
                row.setPadding(dp(2), dp(6), dp(2), 0);
                in.addView(row);
            }
            if (why.length() > 0) {
                TextView w = txt(why, 12, dim);
                w.setPadding(dp(2), dp(8), dp(2), 0);
                w.setLineSpacing(dp(3), 1f);
                in.addView(w);
                if (ci == 0 || ci == 1) {
                    in.addView(confBtn(Words.MA_TILE_A11Y_SET, null, new Runnable() { public void run() { openA11y(); } }));
                } else if (ci == 4) {
                    if (!BuildEnv.ready(MainActivity.this)) in.addView(confBtn(Words.MA_BUILD_BTN_PREP, null, new Runnable() { public void run() { askPrepareEnv(); } }));
                }
            }
            if (why.length() > 0) in.setAlpha(0.45f);
            cc.addView(in);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(10);
            cc.setLayoutParams(lp);
            boxToolClusters.addView(cc);
        }
    }

    /** shell 身份（Shizuku）通不通——装包那一步要用它。 */
    boolean shxOk() {
        try {
            if (!rikka.shizuku.Shizuku.pingBinder()) return false;
            if (rikka.shizuku.Shizuku.isPreV11()) return true;
            return rikka.shizuku.Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) { return false; }
    }

    // ═════════ 79 号单：检查更新（检查 → 提示 → 下载 → 校验 → 拉系统安装器） ═════════

    /** 首页那一行：常态隐藏，只有真有新版才出现。点一下＝下载；下好再点＝拉起系统安装器。 */
    View updateCard() {
        MaterialCardView c = card(true);
        LinearLayout in = column();
        in.setPadding(dp(16), dp(12), dp(16), dp(12));
        tvUpdTitle = txt("", 15, col(com.google.android.material.R.attr.colorPrimary, 0xFF6C8CFF));
        tvUpdTitle.setTypeface(null, Typeface.BOLD);
        in.addView(tvUpdTitle);
        tvUpdDesc = txt("", 12, colVariant());
        tvUpdDesc.setPadding(0, dp(4), 0, 0);
        in.addView(tvUpdDesc);
        pbUpd = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pbUpd.setMax(100);
        pbUpd.setProgress(0);
        pbUpd.setVisibility(View.GONE);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dp(6));
        plp.topMargin = dp(10);
        in.addView(pbUpd, plp);
        c.addView(in);
        c.setVisibility(View.GONE);
        c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { downloadUpdate(); } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        c.setLayoutParams(lp);
        return c;
    }
    /** 84 号单：每次进前台问一次；10 分钟节流在 UpdateCheck 里。没新版／失败都静默（日志里有）。 */
    void maybeCheckUpdate() {
        if (!UpdateCheck.on(this)) return;
        if (updDownloading) { UpdateCheck.log(this, "正在下载更新 ⇒ 这次不查也不弹"); return; }
        UpdateCheck.checkOnOpen(this, new UpdateCheck.CB() {
            public void onResult(final UpdateCheck.Info inf, boolean skipped) {
                if (inf == null || inf.err.length() > 0 || !inf.newer) return;   // 无新版／失败：什么都不说
                runOnUiThread(new Runnable() { public void run() { try { showUpdate(inf, false); } catch (Throwable t) { } } });
            }
        });
    }

    /** 配置页「立即检查」：用户明确要查，跳过节流；结果当场说一句。 */
    void checkUpdateManual() {
        if (!UpdateCheck.on(this)) { toast(Words.UPD_MSG_OFF); return; }
        toast(Words.UPD_STATE_CHECKING);
        UpdateCheck.checkNow(this, new UpdateCheck.CB() {
            public void onResult(final UpdateCheck.Info inf, boolean skipped) {
                runOnUiThread(new Runnable() { public void run() {
                    refreshUpdState();
                    if (inf != null && inf.err.length() > 0) { toast(Words.UPD_STATE_FAIL); return; }
                    if (inf != null && inf.newer) { showUpdate(inf, true); return; }
                    toast(String.format(java.util.Locale.ROOT, Words.UPD_MSG_UP_TO_DATE_FMT, UpdateCheck.localVer(MainActivity.this)));
                } });
            }
        });
    }

    /**
     * 有新版：首页那一行显出来；没点过「不再提示」且本会话没弹过就弹窗。
     * force=true ＝ 用户主动点「立即检查」，不受会话去重限制。
     */
    void showUpdate(final UpdateCheck.Info inf, boolean force) {
        updInfo = inf;
        updApkPath = null;
        String v = inf.tag;
        if (v.length() > 0 && (v.charAt(0) == 'v' || v.charAt(0) == 'V')) v = v.substring(1);
        if (!uiFg) {
            pendingUpd = inf;
            UpdateCheck.log(this, "查到新版但界面不在前台 ⇒ 先存着，回前台再弹");
            return;
        }
        if (cardUpd != null && tvUpdTitle != null) {
            tvUpdTitle.setText(String.format(java.util.Locale.ROOT, Words.UPD_NEW_T_FMT, v));
            String sz = mb(inf.size);
            tvUpdDesc.setText(sz.length() == 0
                    ? String.format(java.util.Locale.ROOT, Words.UPD_NEW_D_NOSIZE_FMT, UpdateCheck.localVer(this))
                    : String.format(java.util.Locale.ROOT, Words.UPD_NEW_D_FMT, UpdateCheck.localVer(this), sz));
            if (pbUpd != null) pbUpd.setVisibility(View.GONE);
            cardUpd.setVisibility(View.VISIBLE);
        }
        refreshUpdState();
        if (updDownloading) { UpdateCheck.log(this, "正在下载更新 ⇒ 不弹窗"); return; }
        if (UpdateCheck.seenAlready(this, inf.tag)) { UpdateCheck.log(this, "这个版本点过「不再提示」⇒ 不弹"); return; }
        if (updShownSession && !force) { UpdateCheck.log(this, "本会话已经弹过 ⇒ 不弹"); return; }
        showUpdateDialog(inf, v);
    }

    /** 84 号单弹窗：版本号一行，公告区可滚（限高屏幕三分之一），两个按钮。公告纯文本，不当 HTML。 */
    void showUpdateDialog(final UpdateCheck.Info inf, String v) {
        updShownSession = true;
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
        box.setClipToOutline(true);

        TextView title = txt(String.format(java.util.Locale.ROOT, Words.UPD_DLG_T_FMT, v), 18, DLG_ACCENT[t]);
        title.setTypeface(null, Typeface.BOLD);
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));

        String sz = mb(inf.size);
        String sub = (sz.length() == 0)
                ? String.format(java.util.Locale.ROOT, Words.UPD_DLG_MSG_NOSIZE_FMT, UpdateCheck.localVer(this))
                : String.format(java.util.Locale.ROOT, Words.UPD_DLG_MSG_FMT, UpdateCheck.localVer(this), sz);
        TextView subTv = txt(sub, 13, col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(6);
        box.addView(subTv, slp);

        final TextView bodyTv = txt(UpdateCheck.plainBody(inf.body), 13, col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6));
        bodyTv.setLineSpacing(dp(4), 1f);
        CapScroll sv = new CapScroll(this, (int) (getResources().getDisplayMetrics().heightPixels / 3f));
        sv.addView(bodyTv, new android.widget.FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-1, -2);
        vlp.topMargin = dp(12);
        box.addView(sv, vlp);
        final TextView moreTv = txt("上下滑动看更多", 11, colVariant());
        moreTv.setVisibility(View.GONE);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
        mlp.topMargin = dp(4);
        box.addView(moreTv, mlp);
        sv.post(new Runnable() { public void run() {
            try { if (bodyTv.getHeight() > sv.getHeight()) moreTv.setVisibility(View.VISIBLE); } catch (Throwable t) { }
        } });

        final android.widget.ProgressBar pb = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setProgress(0);
        pb.setVisibility(View.GONE);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dp(6));
        plp.topMargin = dp(12);
        box.addView(pb, plp);

        final TextView progTv = txt("", 12, colVariant());
        progTv.setVisibility(View.GONE);
        progTv.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = dp(6);
        box.addView(progTv, tlp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = dp(16);
        box.addView(row, rowLp);

        TextView no = dlgBtn(Words.UPD_DLG_LATER, t, false);
        no.setOnClickListener(new View.OnClickListener() { public void onClick(View x) {
            try { d.dismiss(); } catch (Throwable e) { }
            UpdateCheck.markSeen(MainActivity.this, inf.tag);   // 同版本永久不弹，下个版本照弹
            refreshUpdState();
        } });
        row.addView(no);

        final TextView yes = dlgBtn(Words.UPD_DLG_NOW, t, true);
        yes.setOnClickListener(new View.OnClickListener() { public void onClick(View x) {
            if (inf.url.length() == 0) { toast(Words.UPD_STATE_FAIL); return; }
            if (!UpdateCheck.canInstall(MainActivity.this)) { toast(Words.UPD_NEED_PERM); UpdateCheck.openInstallSetting(MainActivity.this); return; }
            startDownloadInDialog(d, inf, pb, progTv, yes);
        } });
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-2, -2);
        olp.leftMargin = dp(10);
        row.addView(yes, olp);

        d.setContentView(box);
        d.setCanceledOnTouchOutside(true);   // 点外面／返回＝这次不弹，下次进前台再弹
        android.view.Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.view.WindowManager.LayoutParams wp = w.getAttributes();
            wp.dimAmount = 0.55f;
            w.setAttributes(wp);
            int wpx = getResources().getDisplayMetrics().widthPixels - dp(48);
            if (wpx < dp(240)) wpx = dp(240);
            w.setLayout(wpx, -2);
        }
        box.setAlpha(0f);
        box.setScaleX(0.94f);
        box.setScaleY(0.94f);
        d.show();
        box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start();
    }

    /** 弹窗里「立即更新」：就地显示百分比＋速度＋剩余时间；下完关窗拉系统安装器。 */
    void startDownloadInDialog(final android.app.Dialog d, final UpdateCheck.Info inf,
                               final android.widget.ProgressBar pb, final TextView progTv, final TextView btn) {
        updDownloading = true;
        updApkPath = null;
        if (btn != null) btn.setEnabled(false);
        pb.setVisibility(View.VISIBLE);
        pb.setProgress(0);
        progTv.setVisibility(View.VISIBLE);
        progTv.setText(Words.UPD_DL_START);
        UpdateCheck.downloadAsync(this, inf, new UpdateCheck.DCB() {
            public void onProgress(final int pct, final long got, final long total, final long bps, final long eta) {
                runOnUiThread(new Runnable() { public void run() {
                    if (pct < 0) { progTv.setText(Words.UPD_VERIFY); return; }
                    pb.setProgress(pct);
                    progTv.setText(String.format(java.util.Locale.ROOT, Words.UPD_DL_PCT_FMT, pct)
                            + "\n" + String.format(java.util.Locale.ROOT, Words.UPD_DL_SPD_FMT, speedText(bps))
                            + "\n" + etaLine(eta));
                } });
            }
            public void onDone(final File f, final boolean ok, final String msg) {
                runOnUiThread(new Runnable() { public void run() {
                    updDownloading = false;
                    if (btn != null) btn.setEnabled(true);
                    if (!ok) {
                        pb.setVisibility(View.GONE);
                        progTv.setText(msg);
                        toast(msg);
                        return;
                    }
                    updApkPath = f.getAbsolutePath();
                    progTv.setText(Words.UPD_READY);
                    try { d.dismiss(); } catch (Throwable t) { }
                    String err = UpdateCheck.install(MainActivity.this, f);
                    if (err != null) toast(err);
                } });
            }
        });
    }

    /** 点首页那一行：没下载过就下（带进度），下好了拉安装器。 */
    void downloadUpdate() {
        if (updDownloading) { toast(Words.UPD_DL_START); return; }
        if (updApkPath != null) {
            String err = UpdateCheck.install(this, new File(updApkPath));
            if (err != null) toast(err);
            return;
        }
        if (updInfo == null) { checkUpdateManual(); return; }
        if (!UpdateCheck.canInstall(this)) { toast(Words.UPD_NEED_PERM); UpdateCheck.openInstallSetting(this); return; }
        updDownloading = true;
        if (pbUpd != null) { pbUpd.setVisibility(View.VISIBLE); pbUpd.setProgress(0); }
        if (tvUpdDesc != null) tvUpdDesc.setText(Words.UPD_DL_START);
        UpdateCheck.downloadAsync(this, updInfo, new UpdateCheck.DCB() {
            public void onProgress(final int pct, final long got, final long total, final long bps, final long eta) {
                runOnUiThread(new Runnable() { public void run() {
                    if (pbUpd == null) return;
                    pbUpd.setVisibility(View.VISIBLE);
                    if (pct < 0) { if (tvUpdDesc != null) tvUpdDesc.setText(Words.UPD_VERIFY); return; }
                    pbUpd.setProgress(pct);
                    if (tvUpdDesc != null) tvUpdDesc.setText(String.format(java.util.Locale.ROOT, Words.UPD_DL_PCT_FMT, pct)
                            + "，" + speedText(bps) + "，" + etaLine(eta));
                } });
            }
            public void onDone(final File f, final boolean ok, final String msg) {
                runOnUiThread(new Runnable() { public void run() {
                    updDownloading = false;
                    if (pbUpd != null) pbUpd.setVisibility(View.GONE);
                    if (!ok) { if (tvUpdDesc != null) tvUpdDesc.setText(msg); toast(msg); return; }
                    updApkPath = f.getAbsolutePath();
                    if (tvUpdDesc != null) tvUpdDesc.setText(Words.UPD_READY);
                    String err = UpdateCheck.install(MainActivity.this, f);
                    if (err != null) toast(err);
                } });
            }
        });
    }

    // 84 号单：进度文案小工具（只有百分比不行，要速度与剩余时间）
    static String speedText(long bps) {
        if (bps <= 0) return Words.UPD_ETA_UNKNOWN;
        if (bps >= 1048576L) return String.format(java.util.Locale.ROOT, "%.1f MB/s", bps / 1048576.0);
        return String.format(java.util.Locale.ROOT, "%.0f KB/s", bps / 1024.0);
    }

    static String etaLine(long sec) {
        if (sec < 0) return String.format(java.util.Locale.ROOT, Words.UPD_DL_ETA_FMT, Words.UPD_ETA_UNKNOWN);
        if (sec < 60) return String.format(java.util.Locale.ROOT, Words.UPD_DL_ETA_FMT, sec + " 秒");
        long m = (sec + 59) / 60;
        if (m < 60) return String.format(java.util.Locale.ROOT, Words.UPD_DL_ETA_FMT, m + " 分钟");
        return String.format(java.util.Locale.ROOT, Words.UPD_DL_ETA_FMT, (m / 60) + " 小时 " + (m % 60) + " 分");
    }

    /** 公告区限高：ScrollView 自己压住最大高度（最多屏幕三分之一）。 */
    static class CapScroll extends android.widget.ScrollView {
        int cap;
        CapScroll(android.content.Context c, int capPx) { super(c); cap = capPx; }
        @Override protected void onMeasure(int w, int h) {
            super.onMeasure(w, h);
            if (cap > 0 && getMeasuredHeight() > cap) setMeasuredDimension(getMeasuredWidth(), cap);
        }
    }

    /** 更新块那两行状态（各自占一行，谁也不压谁）：上次检查时刻 ＋ 已提示的版本。 */
    String updWhenText() {
        long ms = UpdateCheck.lastMs(this);
        return (ms <= 0) ? Words.UPD_STATE_NEVER
                : new java.text.SimpleDateFormat("M 月 d 日 HH:mm", java.util.Locale.ROOT).format(new java.util.Date(ms));
    }

    String updSeenText() {
        String seen = UpdateCheck.seenVer(this);
        return (seen == null || seen.length() == 0) ? Words.UPD_STATE_NOBUB : seen;
    }

    /** 母标题右侧那一截（收起也看得见）：上次检查 15:31 */
    String updTagText() {
        long ms = UpdateCheck.lastMs(this);
        return (ms <= 0) ? Words.UPD_TAG_NEVER
                : String.format(java.util.Locale.ROOT, Words.UPD_TAG_FMT,
                        new java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT).format(new java.util.Date(ms)));
    }

    /** 就地刷新三处文字（状态两行 ＋ 母标题右侧），不重建页面、不滚回顶部。 */
    void refreshUpdState() {
        try {
            if (tvUpdState != null) tvUpdState.setText(String.format(java.util.Locale.ROOT, Words.UPD_LAST_FMT, updWhenText()));
            if (tvUpdSeen != null) tvUpdSeen.setText(String.format(java.util.Locale.ROOT, Words.UPD_SEEN_FMT, updSeenText()));
            if (tvUpdFoldTag != null) tvUpdFoldTag.setText(updTagText());
        } catch (Throwable t) { }
    }

    /** 改更新源（镜像／内网／验收用假源）；留空＝官方。只换检查地址，不换下载地址。 */
    void askUpdApi() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setSingleLine(true);
        et.setText(UpdateCheck.apiOverride(this));
        et.setHint(UpdateCheck.DEFAULT_API);
        reticleDialog(Words.UPD_API_DLG_T, Words.UPD_API_DLG_A, et, Words.CF_DLG_CANCEL, Words.CF_DLG_OK, new Runnable() {
            public void run() {
                String v = et.getText().toString().trim();
                if (v.length() > 0 && !(v.startsWith("http://") || v.startsWith("https://"))) { toast(Words.UPD_API_BAD); return; }
                UpdateCheck.setApiOverride(MainActivity.this, v);
                toast(Words.UPD_API_SET);
                rebuildConfUi();
            }
        });
    }

    static String mb(long b) {
        if (b <= 0) return "";
        if (b >= 1048576L) return String.format(java.util.Locale.ROOT, "%.1f MB", b / 1048576.0);
        return String.format(java.util.Locale.ROOT, "%.0f KB", b / 1024.0);
    }
}
