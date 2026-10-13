package app.reticle;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.HashMap;

import rikka.shizuku.Shizuku;

/**
 * 28 号单 · 首次引导（四步：连接 Shizuku → 开无障碍 → 所有文件访问 → 自检）
 * 35 号单（2026-10-08）：插第 3 步「所有文件访问」——只送不代点，只加这一步；自检与首页状态卡不动。
 *
 * 目标：一个从没听说过 Reticle 的人，装上 apk 后全程只在应用里点，就能用。
 *   · 只在没做过引导时出现（标记位 settings/onboard_done）；可跳过、不锁死。
 *   · 一切系统设置都由用户自己点：我们只把他「送到那一步」并读回结果（红线：不代点）。
 *   · 文案集中在 Words.java（31 单统一改风格时改那一处）。
 */
public class OnboardActivity extends AppCompatActivity {

    static final String PREFS = "settings";
    static final String K_DONE = "onboard_done";     // 引导做过／跳过 = true
    static final String SHZ_PKG = "moe.shizuku.privileged.api";
    static final String SHZ_ACT = "moe.shizuku.manager.MainActivity";
    static final int REQ_SHZ = 1001;

    final Handler ui = new Handler(Looper.getMainLooper());
    LinearLayout root, stepsRow, cardBox, bottomBar;
    ScrollView body;
    MaterialButton mainBtn, skipBtn;
    int step = 0;
    boolean waiting = false;      // 已发起授权，等 Shizuku 弹框结果

    int cShot = 0, cTap = 0, cOcr = 0, cSh = 0;   // 0 未测 / 1 通过 / -1 没过
    String fShot = "", fTap = "", fOcr = "", fSh = "";
    boolean tested = false;

    final Shizuku.OnRequestPermissionResultListener permListener = new Shizuku.OnRequestPermissionResultListener() {
        public void onRequestPermissionResult(int requestCode, int grantResult) {
            if (requestCode != REQ_SHZ) return;
            ui.post(new Runnable() { public void run() { waiting = false; try { render(); } catch (Throwable t) { } } });
        }
    };
    final Shizuku.OnBinderReceivedListener bindRecv = new Shizuku.OnBinderReceivedListener() {
        public void onBinderReceived() { ui.post(new Runnable() { public void run() { if (step == 1) { try { render(); } catch (Throwable t) { } } } }); }
    };
    final Shizuku.OnBinderDeadListener bindDead = new Shizuku.OnBinderDeadListener() {
        public void onBinderDead() { ui.post(new Runnable() { public void run() { if (step == 1) { try { render(); } catch (Throwable t) { } } } }); }
    };

    public static boolean done(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(K_DONE, false);
    }

    /** 28 号单 · 一键停机/重测用：把「做过引导」的标记清掉 */
    public static void resetDone(Context c) {
        try { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(K_DONE, false).apply(); } catch (Throwable t) { }
    }

    public static void markDone(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(K_DONE, true).apply();
    }

    /** 供配置页「重跑一遍引导」用 */
    public static void open(Context c) {
        try { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(K_DONE, false).apply(); } catch (Throwable t) { }
        try {
            Intent i = new Intent(c, OnboardActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            c.startActivity(i);
        } catch (Throwable t) { }
    }

    @Override protected void onCreate(Bundle b) {
        setTheme(themeResOf(this));
        super.onCreate(b);
        try { Shizuku.addRequestPermissionResultListener(permListener); } catch (Throwable t) { }
        try { Shizuku.addBinderReceivedListenerSticky(bindRecv); } catch (Throwable t) { try { Shizuku.addBinderReceivedListener(bindRecv); } catch (Throwable t2) { } }
        try { Shizuku.addBinderDeadListener(bindDead); } catch (Throwable t) { }
        buildRoot();
        setContentView(root);
        render();
    }

    @Override protected void onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(permListener); } catch (Throwable t) { }
        try { Shizuku.removeBinderReceivedListener(bindRecv); } catch (Throwable t) { }
        try { Shizuku.removeBinderDeadListener(bindDead); } catch (Throwable t) { }
        super.onDestroy();
    }

    /** 47.5 号单：复用栈内实例时（singleTop／CLEAR_TOP）走到这里——重画当前步（原来会「点了没反应」） */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        try { setIntent(intent); } catch (Throwable t) { }
        try { render(); } catch (Throwable t) { }
    }

    @Override protected void onResume() { super.onResume(); try { render(); } catch (Throwable t) { } }

    /** 从这里走掉（返回键）＝跳过；下次启动不再拦人 */
    @Override public void onBackPressed() {
        markDone(this);
        super.onBackPressed();
    }

    // ───────── 主题（与 MainActivity 同口径 · 30 号单三套固定主题）─────────
    static int themeResOf(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int mode = sp.getInt("theme_mode", 0);
        boolean night;
        if (mode == 2) night = true;
        else if (mode == 1) night = false;
        else night = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (!night) return R.style.Theme_Reticle;
        return sp.getBoolean("black_night", false) ? R.style.Theme_Reticle_Black : R.style.Theme_Reticle_Dark;
    }

    // ───────── 状态检测 ─────────
    /** 0 没装 / 1 装了没跑 / 2 在跑没授权 / 3 已授权 */
    int shizukuStage() {
        if (!installed(SHZ_PKG)) return 0;
        boolean ping = false;
        try { ping = Shizuku.pingBinder(); } catch (Throwable t) { ping = false; }
        if (!ping) {
            try { rikka.shizuku.ShizukuProvider.requestBinderForNonProviderProcess(this); } catch (Throwable t) { }
            try { ping = Shizuku.pingBinder(); } catch (Throwable t) { ping = false; }
        }
        if (!ping) return 1;
        boolean g = false;
        try {
            if (Shizuku.isPreV11()) g = false;
            else g = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) { g = false; }
        return g ? 3 : 2;
    }

    boolean installed(String pkg) {
        try { getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Throwable t) { return false; }
    }

    boolean a11yOn() {
        try {
            if (A11yWatchdog.hasSvc(A11yWatchdog.readList(this))) return true;
        } catch (Throwable t) { }
        return false;
    }

    // ───────── 骨架 ─────────
    void buildRoot() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(col(android.R.attr.colorBackground, 0xFF101418));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(20), dp(24), dp(20), dp(10));
        TextView t = txt(Words.OB_TITLE_C, 30, col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6));
        t.setTypeface(null, Typeface.BOLD);
        head.addView(t);
        TextView s = txt(Words.OB_SUB, 13, colVariant());
        s.setPadding(0, dp(6), 0, 0);
        head.addView(s);
        root.addView(head);

        stepsRow = new LinearLayout(this);
        stepsRow.setOrientation(LinearLayout.HORIZONTAL);
        stepsRow.setPadding(dp(14), 0, dp(14), dp(6));
        root.addView(stepsRow);

        body = new ScrollView(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, 0, 1f);
        root.addView(body, blp);
        cardBox = new LinearLayout(this);
        cardBox.setOrientation(LinearLayout.VERTICAL);
        cardBox.setPadding(dp(18), dp(6), dp(18), dp(20));
        body.addView(cardBox);

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setPadding(dp(18), dp(6), dp(18), dp(18));
        mainBtn = new MaterialButton(this);
        mainBtn.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        skipBtn = new MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(2);
        skipBtn.setLayoutParams(slp);
        skipBtn.setText(Words.OB_SKIP);
        skipBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { markDone(OnboardActivity.this); finish(); }
        });
        bottomBar.addView(mainBtn);
        bottomBar.addView(skipBtn);
        root.addView(bottomBar);
    }

    void render() {
        if (stepsRow == null) return;
        stepsRow.removeAllViews();
        stepsRow.addView(stepChip(R.drawable.ic_eye, Words.OB_STEP0, step > 0, step == 0));
        stepsRow.addView(stepChip(R.drawable.ic_link, Words.OB_STEP1, doneOf(0), step == 1));
        stepsRow.addView(stepChip(R.drawable.ic_stat_a11y, Words.OB_STEP2, doneOf(1), step == 2));
        stepsRow.addView(stepChip(R.drawable.ic_tile_open, Words.OB_STEP3, doneOf(2), step == 3));
        stepsRow.addView(stepChip(R.drawable.ic_check_circle, Words.OB_STEP4, doneOf(3) || step == 5, step == 4));

        cardBox.removeAllViews();
        if (step == 0) fillIntro();
        else if (step == 1) fillStep1();
        else if (step == 2) fillStep2();
        else if (step == 3) fillStep3Files();
        else if (step == 4) fillStepTest();
        else fillMin();

        if (step >= 4) skipBtn.setVisibility(View.GONE); else skipBtn.setVisibility(View.VISIBLE);
        syncMainBtn();
    }

    boolean doneOf(int i) {
        if (i == 0) return shizukuStage() == 3;
        if (i == 1) return a11yOn();
        if (i == 2) return needsFilesStep() ? allFilesOk() : true;
        return cShot == 1 && cTap == 1 && cOcr == 1 && cSh == 1;
    }

    View stepChip(int iconRes, String label, boolean done, boolean cur) {
        LinearLayout w = new LinearLayout(this);
        w.setOrientation(LinearLayout.VERTICAL);
        w.setGravity(Gravity.CENTER);
        w.setPadding(dp(4), dp(8), dp(4), dp(8));
        w.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        int c = cur ? col(com.google.android.material.R.attr.colorPrimary, 0xFF6C8CFF)
                    : (done ? col(com.google.android.material.R.attr.colorPrimary, 0xFF6C8CFF) : colVariant());
        ImageView ic = new ImageView(this);
        ic.setImageResource(iconRes);
        ic.setImageTintList(ColorStateList.valueOf(c));
        ic.setLayoutParams(new LinearLayout.LayoutParams(dp(22), dp(22)));
        w.addView(ic);
        TextView t = txt(label, 12, c);
        t.setTypeface(null, (cur || done) ? Typeface.BOLD : Typeface.NORMAL);
        t.setPadding(0, dp(4), 0, 0);
        w.addView(t);
        if (cur) {
            GradientDrawable g = new GradientDrawable();
            g.setColor((c & 0x00FFFFFF) | 0x22000000);
            g.setCornerRadius(dp(14));
            w.setBackground(g);
        }
        return w;
    }

    // ───────── 第 1 步：连接 Shizuku ─────────
    void fillStep1() {
        int st = shizukuStage();
        cardBox.addView(para(Words.OB_S1_LEAD));
        if (st == 0) {
            cardBox.addView(head(Words.OB_S1_NONE_T));
            cardBox.addView(para(Words.OB_S1_NONE_D));
            cardBox.addView(btnFill(Words.OB_S1_NONE_B1, new Runnable() { public void run() { openStore(); } }));
            cardBox.addView(btnLine(Words.OB_S1_NONE_B2, new Runnable() { public void run() { render(); } }));
        } else if (st == 1) {
            cardBox.addView(head(Words.OB_S1_OFF_T));
            cardBox.addView(para(Words.OB_S1_OFF_D));
            cardBox.addView(btnFill(Words.OB_S1_OFF_B1, new Runnable() { public void run() { openShizuku(); } }));
            cardBox.addView(btnLine(Words.OB_S1_OFF_B2, new Runnable() { public void run() { render(); } }));
        } else if (st == 2) {
            cardBox.addView(head(Words.OB_S1_NO_T));
            cardBox.addView(para(Words.OB_S1_NO_D));
            if (waiting) {
                cardBox.addView(quiet(Words.OB_S1_WAIT));
            } else {
                cardBox.addView(btnFill(Words.OB_S1_NO_B1, new Runnable() { public void run() { askShizuku(); } }));
                cardBox.addView(quiet(Words.OB_S1_DENY));
                cardBox.addView(quiet(Words.OB_S1_NO_POPUP));
            }
            cardBox.addView(btnLine(Words.OB_RETRY, new Runnable() { public void run() { render(); } }));
        } else {
            cardBox.addView(head(Words.OB_S1_OK_T));
            cardBox.addView(para(Words.OB_S1_OK_D));
            cardBox.addView(resultRow(R.drawable.ic_stat_shz, Words.OB_STEP1, 1, ""));
        }
    }

    void openStore() {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=Shizuku"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            try {
                Intent i2 = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + SHZ_PKG));
                i2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i2);
            } catch (Throwable t2) {
                toast(Words.OB_TOAST_STORE);
            }
        }
    }

    /** 只把 Shizuku 的界面拉到前台（普通 startActivity，不需要它的权限） */
    void openShizuku() {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName(SHZ_PKG, SHZ_ACT));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return;
        } catch (Throwable t) { }
        try {
            Intent i2 = getPackageManager().getLaunchIntentForPackage(SHZ_PKG);
            if (i2 != null) { i2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i2); return; }
            toast(Words.OB_TOAST_SHZ);
        } catch (Throwable t2) {
            toast(Words.OB_TOAST_SHZ);
        }
    }

    /** 发起 Shizuku 的正规授权弹框（这一步是 28 号单的核心：让 Reticle 出现在它的「应用管理」里） */
    void askShizuku() {
        boolean ping = false;
        try { ping = Shizuku.pingBinder(); } catch (Throwable t) { ping = false; }
        if (!ping) {
            try { rikka.shizuku.ShizukuProvider.requestBinderForNonProviderProcess(this); } catch (Throwable t) { }
            try { ping = Shizuku.pingBinder(); } catch (Throwable t) { ping = false; }
        }
        if (!ping) { toast(Words.OB_S1_NEED_RUN); render(); return; }
        try {
            waiting = true;
            render();
            Shizuku.requestPermission(REQ_SHZ);
        } catch (Throwable t) {
            waiting = false;
            toast(Words.OB_S1_REQ_FAIL);
            render();
        }
    }

    // ───────── 第 2 步：开无障碍 ─────────
    void fillStep2() {
        cardBox.addView(para(Words.OB_S2_LEAD));
        if (a11yOn()) {
            cardBox.addView(head(Words.OB_S2_OK_T));
            cardBox.addView(para(Words.OB_S2_OK_D));
            cardBox.addView(quiet(Words.OB_S2_WD));
        } else {
            cardBox.addView(head(Words.OB_S2_NO_T));
            cardBox.addView(para(Words.OB_S2_D));
            cardBox.addView(btnFill(Words.OB_S2_B1, new Runnable() { public void run() { openA11y(); } }));
            cardBox.addView(btnLine(Words.OB_RETRY, new Runnable() { public void run() { render(); } }));
            cardBox.addView(quiet(Words.OB_S2_FIX));
            cardBox.addView(quiet(Words.OB_S2_WD));
        }
    }

    void openA11y() {
        try {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            toast(Words.OB_TOAST_A11Y);
        }
    }

    // ───────── 第 3 步：所有文件访问（35 号单） ─────────
    /** API 30+ 才有「所有文件访问」这一项；更低版本用普通存储权限，本步直接跳过。 */
    static boolean needsFilesStep() { return Build.VERSION.SDK_INT >= 30; }

    /** 是否已拿到「所有文件访问」（API 30+）——实时读，不缓存。 */
    boolean allFilesOk() {
        try {
            if (!needsFilesStep()) return true;
            return Environment.isExternalStorageManager();
        } catch (Throwable t) { return false; }
    }

    void fillStep3Files() {
        if (!needsFilesStep()) {
            cardBox.addView(head(Words.OB_S3F_OLD_T));
            cardBox.addView(para(Words.OB_S3F_OLD_D));
            return;
        }
        if (allFilesOk()) {
            cardBox.addView(head(Words.OB_S3F_OK_T));
            cardBox.addView(para(Words.OB_S3F_OK_D));
        } else {
            cardBox.addView(para(Words.OB_S3F_LEAD));
            cardBox.addView(head(Words.OB_S3F_NO_T));
            cardBox.addView(para(Words.OB_S3F_NO_D));
            cardBox.addView(btnFill(Words.OB_S3F_B1, new Runnable() { public void run() { openAllFiles(); } }));
            cardBox.addView(btnLine(Words.OB_S3F_B2, new Runnable() { public void run() { render(); } }));
            cardBox.addView(quiet(Words.OB_S3F_HINT));
        }
    }

    /** 只把系统的「所有文件访问」页拉到前台（红线：不代点系统设置）。 */
    void openAllFiles() {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return;
        } catch (Throwable t) { }
        try {
            Intent i2 = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            i2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i2);
            return;
        } catch (Throwable t2) { }
        toast(Words.OB_TOAST_FILES);
    }

    // ───────── 第 4 步：自检（35 号单：原第 3 步） ─────────
    void fillStepTest() {
        cardBox.addView(para(tested ? (allOk() ? Words.OB_S3_ALL_OK : Words.OB_S3_SOME_BAD) : Words.OB_S3_LEAD));
        cardBox.addView(resultRow(R.drawable.ic_eye, Words.OB_C1, cShot, fShot));
        cardBox.addView(resultRow(R.drawable.ic_tap, Words.OB_C2, cTap, fTap));
        cardBox.addView(resultRow(R.drawable.ic_ocr, Words.OB_C3, cOcr, fOcr));
        cardBox.addView(resultRow(R.drawable.ic_stat_shz, Words.OB_C4, cSh, fSh));
        if (!tested) {
            cardBox.addView(btnFill(Words.OB_S3_B1, new Runnable() { public void run() { runSelfTest(); } }));
        } else {
            cardBox.addView(btnLine(Words.OB_S3_AGAIN, new Runnable() { public void run() { runSelfTest(); } }));
        }
    }

    boolean allOk() { return cShot == 1 && cTap == 1 && cOcr == 1 && cSh == 1; }

    void runSelfTest() {
        cShot = cTap = cOcr = cSh = 0;
        fShot = fTap = fOcr = fSh = "";
        tested = false;
        render();
        toast(Words.OB_S3_RUNNING);
        new Thread(new Runnable() { public void run() {
            // ① 看屏幕：真截一张
            try {
                String r = VeriService.mcpDispatch("shot", new HashMap<String, String>());
                if (r != null && r.contains("\"ok\":1")) cShot = 1; else { cShot = -1; fShot = Words.OB_FIX_SHOT; }
            } catch (Throwable t) { cShot = -1; fShot = Words.OB_FIX_SHOT; }
            postRender();
            // ② 点屏幕：能读到当前界面 ＝ 无障碍通道是活的（真要是点一下会点到这页，所以只读不点）
            try {
                String r = VeriService.mcpDispatch("ui", new HashMap<String, String>());
                boolean ok = r != null && r.contains("\"ok\":1");
                if (!ok) ok = (cShot == 1);
                if (ok) cTap = 1; else { cTap = -1; fTap = Words.OB_FIX_A11Y; }
            } catch (Throwable t) { cTap = -1; fTap = Words.OB_FIX_A11Y; }
            postRender();
            // ③ 认字：真认一屏
            try {
                String r = VeriService.mcpDispatch("ocr", new HashMap<String, String>());
                if (r != null && r.contains("\"ok\":1")) cOcr = 1; else { cOcr = -1; fOcr = Words.OB_FIX_OCR; }
            } catch (Throwable t) { cOcr = -1; fOcr = Words.OB_FIX_OCR; }
            postRender();
            // ④ 跑命令：借 Shizuku 跑一句 id
            try {
                boolean granted = false;
                try { granted = Shizuku.pingBinder() && (Shizuku.isPreV11() || Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED); } catch (Throwable t) { granted = false; }
                if (granted) {
                    String r = VeriService.shxRun("id");
                    if (r != null && r.contains("\"ok\":1") && r.contains("uid=")) cSh = 1; else { cSh = -1; fSh = Words.OB_FIX_SHZ; }
                } else { cSh = -1; fSh = Words.OB_FIX_SHZ; }
            } catch (Throwable t) { cSh = -1; fSh = Words.OB_FIX_SHZ; }
            postRender();
            ui.post(new Runnable() { public void run() { tested = true; render(); } });
        } }).start();
    }

    void postRender() {
        ui.post(new Runnable() { public void run() { try { render(); } catch (Throwable t) { } } });
    }

    // ───────── 底部主按钮各处一处 ─────────
    void syncMainBtn() {
        if (step == 0) {
            mainBtn.setText(Words.OB_NEXT);
            mainBtn.setEnabled(true);
            mainBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { step = 1; render(); } });
        } else if (step == 1) {
            final boolean ok = shizukuStage() == 3;
            mainBtn.setText(Words.OB_NEXT);
            mainBtn.setEnabled(ok);
            mainBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (shizukuStage() == 3) { step = 2; render(); } } });
        } else if (step == 2) {
            final boolean ok = a11yOn();
            mainBtn.setText(Words.OB_NEXT);
            mainBtn.setEnabled(ok);
            mainBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (a11yOn()) { step = 3; render(); } } });
        } else if (step == 3) {
            final boolean okAll = (!needsFilesStep() || allFilesOk());
            mainBtn.setText(Words.OB_NEXT);
            mainBtn.setEnabled(okAll);
            mainBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (!needsFilesStep() || allFilesOk()) { step = 4; render(); } } });
        } else if (step == 4) {
            // 45 号单：自检全过 → 接「你只需要知道这些」；没全过也允许直接完成（照旧）
            final boolean allPass = tested && allOk();
            mainBtn.setText(allPass ? Words.OB_MIN_NEXT : Words.OB_DONE);
            mainBtn.setEnabled(tested);
            mainBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (tested && allOk()) { step = 5; render(); return; }
                    markDone(OnboardActivity.this); toast(Words.OB_BYE); finish();
                }
            });
        } else {
            mainBtn.setText(Words.OB_MIN_GO);
            mainBtn.setEnabled(true);
            mainBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { markDone(OnboardActivity.this); toast(Words.OB_BYE); finish(); }
            });
        }
    }

    // ───────── 第 5 屏：你只需要知道这些（45 号单）─────────
    /** 9 条正文＝Manual.MIN（同一份文字，详细说明第 1 章也用它） */
    void fillMin() {
        cardBox.addView(head(Manual.MIN_TITLE));
        cardBox.addView(para(Manual.MIN_SUB));
        for (int i = 0; i < Manual.MIN.length; i++) cardBox.addView(minRow(i + 1, Manual.MIN[i]));
        cardBox.addView(quiet(Words.MN_TAIL));
    }

    // ───────── 第 0 屏：这应用是干嘛的（48 号单）─────────
    /** 大白话开场：先讲清「它是什么」，再说三样权限是干什么用的；整屏不出现技术词 */
    void fillIntro() {
        cardBox.addView(head(Words.OB_S0_T));
        cardBox.addView(para(Words.OB_S0_P1));
        cardBox.addView(para(Words.OB_S0_P2));
        cardBox.addView(para(Words.OB_S0_P3));
        cardBox.addView(para(Words.OB_S0_P4));
        cardBox.addView(bulletRow(R.drawable.ic_stat_shz, Words.OB_S0_L1));
        cardBox.addView(bulletRow(R.drawable.ic_stat_a11y, Words.OB_S0_L2));
        cardBox.addView(bulletRow(R.drawable.ic_tile_open, Words.OB_S0_L3));
    }

    /** 一行「图标 ＋ 一句话」的卡片（显式 LayoutParams：行容器不许裸着当弹簧） */
    View bulletRow(int iconRes, String s) {
        MaterialCardView c = card();
        LinearLayout in = new LinearLayout(this);
        in.setOrientation(LinearLayout.HORIZONTAL);
        in.setGravity(Gravity.CENTER_VERTICAL);
        in.setPadding(dp(14), dp(12), dp(14), dp(12));
        in.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        ImageView ic = new ImageView(this);
        ic.setImageResource(iconRes);
        ic.setImageTintList(ColorStateList.valueOf(col(com.google.android.material.R.attr.colorPrimary, 0xFF6C8CFF)));
        ic.setLayoutParams(new LinearLayout.LayoutParams(dp(20), dp(20)));
        in.addView(ic);
        TextView t = txt(s, 14, col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6));
        t.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.leftMargin = dp(12);
        t.setLayoutParams(tlp);
        in.addView(t);
        c.addView(in, new android.widget.FrameLayout.LayoutParams(-1, -2));
        return c;
    }

    View minRow(int n, String s) {
        MaterialCardView c = card();
        LinearLayout in = new LinearLayout(this);
        in.setOrientation(LinearLayout.HORIZONTAL);
        in.setPadding(dp(12), dp(10), dp(12), dp(10));
        TextView num = txt(String.valueOf(n), 14, col(com.google.android.material.R.attr.colorPrimary, 0xFF6C8CFF));
        num.setTypeface(null, Typeface.BOLD);
        num.setLayoutParams(new LinearLayout.LayoutParams(dp(24), -2));
        in.addView(num);
        TextView t = txt(s, 14, col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6));
        t.setLineSpacing(dp(4), 1f);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        in.addView(t);
        c.addView(in);
        return c;
    }

    // ───────── 小零件 ─────────
    View head(String s) {
        TextView t = txt(s, 19, col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6));
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(dp(2), dp(10), dp(2), dp(6));
        return t;
    }

    View para(String s) {
        TextView t = txt(s, 14, col(com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF9AA0A6));
        t.setPadding(dp(2), dp(2), dp(2), dp(10));
        t.setLineSpacing(dp(4), 1f);
        return t;
    }

    View quiet(String s) {
        TextView t = txt(s, 12, colVariant());
        t.setPadding(dp(2), dp(4), dp(2), dp(8));
        t.setLineSpacing(dp(3), 1f);
        return t;
    }

    MaterialCardView card() {
        MaterialCardView c = new MaterialCardView(this);
        c.setRadius(dp(18));
        c.setCardElevation(0f);
        c.setStrokeWidth(dp(1));
        c.setStrokeColor(col(com.google.android.material.R.attr.colorOutline, 0x339AA0A6));
        c.setCardBackgroundColor(col(com.google.android.material.R.attr.colorSurface, 0xFF1B1F24));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        c.setLayoutParams(lp);
        return c;
    }

    View resultRow(int iconRes, String name, int state, String fix) {
        MaterialCardView c = card();
        LinearLayout in = new LinearLayout(this);
        in.setOrientation(LinearLayout.VERTICAL);
        in.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = new ImageView(this);
        ic.setImageResource(iconRes);
        ic.setImageTintList(ColorStateList.valueOf(col(com.google.android.material.R.attr.colorPrimary, 0xFF6C8CFF)));
        ic.setLayoutParams(new LinearLayout.LayoutParams(dp(20), dp(20)));
        row.addView(ic);
        TextView t = txt(name, 15, col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6));
        t.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        t.setPadding(dp(12), 0, dp(6), 0);
        row.addView(t);
        if (state == 1 || state == -1) {
            ImageView st = new ImageView(this);
            st.setImageResource(state == 1 ? R.drawable.ic_check_circle : R.drawable.ic_close_circle);
            st.setImageTintList(ColorStateList.valueOf(state == 1
                    ? col(com.google.android.material.R.attr.colorPrimary, 0xFF6C8CFF)
                    : col(com.google.android.material.R.attr.colorError, 0xFFFF6B6B)));
            st.setLayoutParams(new LinearLayout.LayoutParams(dp(20), dp(20)));
            row.addView(st);
        } else {
            row.addView(txt("…", 15, colVariant()));
        }
        in.addView(row);
        if (state == -1 && fix != null && fix.length() > 0) {
            TextView f = txt(fix, 12, col(com.google.android.material.R.attr.colorError, 0xFFFF6B6B));
            f.setPadding(dp(32), dp(6), 0, 0);
            in.addView(f);
        }
        c.addView(in);
        return c;
    }

    MaterialButton btnFill(String s, final Runnable r) {
        MaterialButton b = new MaterialButton(this);
        b.setText(s);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(4);
        b.setLayoutParams(lp);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { try { r.run(); } catch (Throwable t) { } } });
        return b;
    }

    MaterialButton btnLine(String s, final Runnable r) {
        MaterialButton b = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(s);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(6);
        b.setLayoutParams(lp);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { try { r.run(); } catch (Throwable t) { } } });
        return b;
    }

    void toast(String s) {
        try { android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable t) { }
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
}
