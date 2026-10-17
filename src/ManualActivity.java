package app.reticle;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;

/**
 * 47 号单（2026-10-12）· 详细说明页（两级：目录 → 章内容）
 *
 * 入口：主界面大标题右边的圆形「?」；配置页「使用前须知」也进这里（直接进第 1 章）。
 * 内容全在 Manual.java（唯一数据源，原文＝ManualDoc.java）——本类只管「怎么画」：
 * 一套目录渲染 ＋ 一套 markdown 块渲染（小节标题／段落／列表／表格／提示块 ＋ 行内粗体与代码）。
 * 47 号单新增：目录 ↔ 章节 的双向转场动画（淡入＋轻微横推，220ms）。
 * 命令行直达（验收／回归）：am start -n app.reticle/.ManualActivity --ei ch 2   （ch=-1 或省略＝目录）
 */
public class ManualActivity extends AppCompatActivity {

    public static final String EXTRA_CH = "ch";     // -1＝目录；0 起＝第几章
    static final String K_CH = "chapter";

    LinearLayout root, col, head;
    ScrollView body;
    TextView tvTitle, tvSub;
    MaterialButton backBtn;
    int chapter = -1;

    /** 打开目录 */
    public static void open(Context c) { openChapter(c, -1); }

    /** 打开指定章（-1＝目录） */
    public static void openChapter(Context c, int ch) {
        try {
            Intent i = new Intent(c, ManualActivity.class);
            i.putExtra(EXTRA_CH, ch);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);   // 47.5：复用栈内实例时也要把它带到前台（原来只有 NEW_TASK，ROM 会吞掉那一步）
            c.startActivity(i);
        } catch (Throwable t) { }
    }

    @Override protected void onCreate(Bundle b) {
        setTheme(OnboardActivity.themeResOf(this));   // 与引导／主界面同一套三固定主题
        super.onCreate(b);
        buildRoot();
        setContentView(root);
        hookInsets();   // 45 号单：避开状态栏／导航条
        try {
            if (b != null) chapter = b.getInt(K_CH, -1);
            else if (getIntent() != null) chapter = getIntent().getIntExtra(EXTRA_CH, -1);
        } catch (Throwable t) { }
        if (chapter < -1 || chapter >= Manual.CH_TITLE.length) chapter = -1;
        render(false);
    }

    /** 47.5 号单：复用栈内实例时（singleTop／CLEAR_TOP）走到这里——重读目标章再画，避免「点了没反应」 */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        try { setIntent(intent); } catch (Throwable t) { }
        try {
            int ch = (intent == null) ? -1 : intent.getIntExtra(EXTRA_CH, -1);
            if (ch < -1 || ch >= Manual.CH_TITLE.length) ch = -1;
            chapter = ch;
            render(false);
        } catch (Throwable t) { }
    }

    @Override protected void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b);
        try { b.putInt(K_CH, chapter); } catch (Throwable t) { }
    }

    /** 返回键：章内容页 → 回目录（带转场）；目录页 → 退出 */
    @Override public void onBackPressed() {
        if (chapter >= 0) { chapter = -1; render(false); return; }
        super.onBackPressed();
    }

    /** 45 号单：targetSdk 35 强制 edge-to-edge → 自己按 insets 避开状态栏／导航条 */
    void hookInsets() {
        try {
            ViewCompat.setOnApplyWindowInsetsListener(root, new OnApplyWindowInsetsListener() {
                public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat ins) {
                    try {
                        Insets bars = ins.getInsets(WindowInsetsCompat.Type.systemBars());
                        if (head != null) head.setPadding(dp(20), dp(20) + bars.top, dp(20), dp(6));
                        if (col != null) col.setPadding(dp(18), dp(4), dp(18), dp(28) + bars.bottom);
                    } catch (Throwable t) { }
                    return ins;
                }
            });
            root.requestApplyInsets();
        } catch (Throwable t) { }
    }

    // ───────── 骨架 ─────────
    void buildRoot() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(col(android.R.attr.colorBackground, 0xFF101418));

        head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        head.setPadding(dp(20), dp(20), dp(20), dp(6));

        backBtn = new MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle);
        backBtn.setText(Words.MN_BACK_DIR);
        backBtn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        backBtn.setPadding(0, 0, 0, 0);
        backBtn.setMinWidth(0);
        backBtn.setMinimumWidth(0);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.bottomMargin = dp(2);
        backBtn.setLayoutParams(blp);
        backBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onBackPressed(); }
        });
        head.addView(backBtn);

        tvTitle = tv("", 28, col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6));
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        head.addView(tvTitle);

        tvSub = tv("", 13, colVariant());
        tvSub.setPadding(0, dp(6), 0, 0);
        tvSub.setLineSpacing(dp(3), 1f);
        tvSub.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        head.addView(tvSub);
        root.addView(head);

        body = new ScrollView(this);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new ScrollView.LayoutParams(-1, -2));
        col.setPadding(dp(18), dp(4), dp(18), dp(28));
        body.addView(col);
    }

    /** forward=true＝往后走（目录→章），false＝往回走（章→目录） */
    void render(boolean forward) {
        if (col == null) return;
        col.removeAllViews();
        col.setAlpha(1f);
        col.setTranslationX(0f);
        if (chapter < 0) {
            backBtn.setVisibility(View.GONE);
            tvTitle.setText(Manual.TITLE);
            tvSub.setText(Manual.SUB + verTail());
            for (int i = 0; i < Manual.CH_TITLE.length; i++) col.addView(chapterItem(i));
            col.addView(quiet(Words.MN_TAIL));
        } else {
            backBtn.setVisibility(View.VISIBLE);
            tvTitle.setText(Manual.CH_TITLE[chapter]);
            tvSub.setText(Manual.CH_SUB[chapter]);
            renderMd(Manual.CH_MD[chapter]);
        }
        body.post(new Runnable() { public void run() { try { body.scrollTo(0, 0); } catch (Throwable t) { } } });
        // 47 号单：同页切换的转场（进＝淡入＋从右侧轻推；返回＝淡入＋从左轻推）
        try {
            col.setAlpha(0f);
            col.setTranslationX(dp(forward ? 28 : -28));
            col.animate().alpha(1f).translationX(0f).setDuration(220)
                    .setInterpolator(new DecelerateInterpolator()).start();
        } catch (Throwable t) { }
    }

    /** 目录里的一行：章号 ＋ 章标题 ＋ 小节名（整条可点） */
    View chapterItem(final int idx) {
        MaterialCardView c = card(true);
        c.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { chapter = idx; render(true); }
        });
        LinearLayout in = new LinearLayout(this);
        in.setOrientation(LinearLayout.HORIZONTAL);
        in.setGravity(Gravity.CENTER_VERTICAL);
        in.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        in.setPadding(dp(16), dp(14), dp(14), dp(14));

        TextView num = tv(Manual.CH_NUM.length > idx ? Manual.CH_NUM[idx] : String.valueOf(idx + 1), 15, prim());
        num.setTypeface(null, Typeface.BOLD);
        num.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor((prim() & 0x00FFFFFF) | 0x22000000);
        num.setBackground(g);
        num.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));
        in.addView(num);

        LinearLayout c2 = new LinearLayout(this);
        c2.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1f);
        clp.leftMargin = dp(12);
        c2.setLayoutParams(clp);
        TextView t = tv(Manual.CH_TITLE[idx], 15, col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6));
        t.setTypeface(null, Typeface.BOLD);
        t.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        c2.addView(t);
        TextView d = tv(Manual.CH_SUB[idx], 12, colVariant());
        d.setPadding(0, dp(3), 0, 0);
        d.setLineSpacing(dp(2), 1f);
        d.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        c2.addView(d);
        in.addView(c2);

        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_chevron);
        arrow.setImageTintList(ColorStateList.valueOf(colVariant()));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(20), dp(20));
        alp.leftMargin = dp(10);
        arrow.setLayoutParams(alp);
        in.addView(arrow);

        c.addView(in);
        return c;
    }

    // ═════════ 47 号单 · markdown 块渲染（小节标题／段落／列表／表格／提示块） ═════════

    void renderMd(String md) {
        if (md == null) return;
        String[] ln = md.split("\n", -1);
        int i = 0;
        while (i < ln.length) {
            String raw = ln[i];
            String s = raw.trim();
            if (s.length() == 0) { i++; continue; }
            if (raw.startsWith("## ")) { i++; continue; }            // 章标题＝页面标题，正文不重复
            if (raw.startsWith("### ")) { addSecTitle(raw.substring(4).trim()); i++; continue; }
            if (s.startsWith("---")) { i++; continue; }
            if (s.startsWith(">")) {                                  // 提示块（可多行）
                StringBuilder q = new StringBuilder();
                while (i < ln.length) {
                    String t = ln[i].trim();
                    if (!t.startsWith(">")) break;
                    t = t.substring(1).trim();
                    if (t.length() > 0) { if (q.length() > 0) q.append("\n"); q.append(t); }
                    i++;
                }
                addQuote(q.toString());
                continue;
            }
            if (s.startsWith("|")) {                                  // 表格
                ArrayList<String[]> rows = new ArrayList<String[]>();
                while (i < ln.length && ln[i].trim().startsWith("|")) { rows.add(Manual.cells(ln[i].trim())); i++; }
                addTable(rows);
                continue;
            }
            if (s.startsWith("- ") || s.startsWith("* ")) {            // 无序列表（保留缩进＝层级）
                ArrayList<String> its = new ArrayList<String>();
                while (i < ln.length) {
                    String t = ln[i].trim();
                    if (!(t.startsWith("- ") || t.startsWith("* "))) break;
                    its.add(ln[i]);
                    i++;
                }
                addList(its, false);
                continue;
            }
            if (isOrdered(s)) {                                       // 有序列表（原文自带编号）
                ArrayList<String> its = new ArrayList<String>();
                while (i < ln.length && isOrdered(ln[i].trim())) { its.add(ln[i]); i++; }
                addList(its, true);
                continue;
            }
            StringBuilder p = new StringBuilder();                    // 段落
            while (i < ln.length) {
                String t = ln[i].trim();
                if (t.length() == 0 || t.startsWith("#") || t.startsWith("|") || t.startsWith(">")
                        || t.startsWith("- ") || t.startsWith("* ") || t.startsWith("---") || isOrdered(t)) break;
                if (p.length() > 0) p.append("\n");
                p.append(t);
                i++;
            }
            if (p.length() > 0) addPara(p.toString());
        }
    }

    void addPara(String s) {
        TextView t = tv(inline(s), 14, col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6));
        t.setLineSpacing(dp(6), 1f);
        t.setPadding(dp(4), dp(2), dp(4), dp(8));
        t.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        col.addView(t);
    }

    void addSecTitle(String s) {
        TextView t = tv(inline(s), 16, prim());
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(dp(4), dp(18), dp(4), dp(6));
        t.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        col.addView(t);
    }

    void addQuote(String text) {
        MaterialCardView c = new MaterialCardView(this);
        c.setRadius(dp(14));
        c.setCardElevation(0f);
        c.setStrokeWidth(dp(1));
        c.setStrokeColor((prim() & 0x00FFFFFF) | 0x66000000);
        c.setCardBackgroundColor((prim() & 0x00FFFFFF) | 0x14000000);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(2);
        lp.bottomMargin = dp(10);
        c.setLayoutParams(lp);
        LinearLayout in = new LinearLayout(this);
        in.setOrientation(LinearLayout.VERTICAL);
        in.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        in.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView t = tv(inline(text), 13, col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6));
        t.setLineSpacing(dp(5), 1f);
        t.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        in.addView(t);
        c.addView(in);
        col.addView(c);
    }

    void addList(ArrayList<String> its, boolean ordered) {
        for (int i = 0; i < its.size(); i++) {
            String raw = its.get(i);
            int lead = 0;
            while (lead < raw.length() && raw.charAt(lead) == ' ') lead++;
            String item = raw.trim();
            if (item.startsWith("- ") || item.startsWith("* ")) item = item.substring(2).trim();

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            row.setPadding(dp(4) + (lead >= 2 ? dp(14) : 0), dp(2), dp(4), dp(2));
            if (!ordered) {
                TextView dot = tv("·", 14, prim());
                dot.setTypeface(null, Typeface.BOLD);
                dot.setLayoutParams(new LinearLayout.LayoutParams(dp(14), -2));
                row.addView(dot);
            }
            TextView t = tv(inline(item), 14, col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6));
            t.setLineSpacing(dp(5), 1f);
            t.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(t);
            col.addView(row);
        }
    }

    /**
     * 表格（47 号单：窄屏可读优先，两种形态都保住「表格语义」）
     *   · 两列＝「字段名 ＋ 值」网格：左格窄、右格宽，长文自动换行（不横向溢出）；
     *   · 三列及以上＝一条记录一块：首列当小标题，其余按「列名：值」逐行。
     * 两种都不让竖线露面、也不拍平成一段话。
     */
    void addTable(ArrayList<String[]> rows) {
        ArrayList<String[]> rs = new ArrayList<String[]>();
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            boolean sep = r.length > 0;
            for (int j = 0; j < r.length; j++) if (r[j].indexOf("--") < 0) sep = false;
            if (sep) continue;                                   // 丢开「|---|---|」分隔行
            rs.add(r);
        }
        if (rs.size() == 0) return;
        int cols = 0;
        for (int i = 0; i < rs.size(); i++) cols = Math.max(cols, rs.get(i).length);
        String[] head = rs.get(0);

        MaterialCardView c = card(false);
        c.setCardBackgroundColor(col(com.google.android.material.R.attr.colorSurfaceVariant, 0xFF23272C));
        LinearLayout in = new LinearLayout(this);
        in.setOrientation(LinearLayout.VERTICAL);
        in.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        in.setPadding(dp(14), dp(12), dp(14), dp(12));
        c.addView(in);

        if (cols <= 2) {
            final boolean narrowCol = isIndexHead(head.length > 0 ? head[0] : "");   // 「# ｜ 事项」这类序号列才用窄列
            for (int i = 0; i < rs.size(); i++) {
                String[] r = rs.get(i);
                final boolean isHead = (i == 0);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));   // ★显式宽：否则 weight 不生效
                row.setPadding(0, dp(4), 0, dp(4));

                TextView lt = tv(inline(r.length > 0 ? r[0] : ""), isHead ? 12 : 13,
                        isHead ? colVariant() : prim());
                if (!isHead) lt.setTypeface(null, Typeface.BOLD);
                lt.setLineSpacing(dp(4), 1f);
                lt.setLayoutParams(narrowCol ? new LinearLayout.LayoutParams(dp(36), -2)
                        : new LinearLayout.LayoutParams(0, -2, 0.34f));
                row.addView(lt);

                TextView vt = tv(inline(r.length > 1 ? r[1] : ""), 13, col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6));
                vt.setLineSpacing(dp(4), 1f);
                if (isHead) vt.setTextColor(colVariant());
                LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(0, -2, narrowCol ? 1f : 0.66f);
                vlp.leftMargin = dp(10);
                vt.setLayoutParams(vlp);
                row.addView(vt);
                in.addView(row);

                if (isHead) {                                    // 表头下一条淡线
                    View line = new View(this);
                    line.setBackgroundColor(col(com.google.android.material.R.attr.colorOutline, 0x339AA0A6));
                    LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1)));
                    llp.topMargin = dp(2);
                    llp.bottomMargin = dp(2);
                    line.setLayoutParams(llp);
                    in.addView(line);
                }
            }
        } else {
            for (int i = 1; i < rs.size(); i++) {                 // ★跳过表头行（列名用来做「列名：值」）
                String[] r = rs.get(i);
                if (i > 1) {
                    View line = new View(this);
                    line.setBackgroundColor(col(com.google.android.material.R.attr.colorOutline, 0x339AA0A6));
                    LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1)));
                    llp.topMargin = dp(9);
                    llp.bottomMargin = dp(2);
                    line.setLayoutParams(llp);
                    in.addView(line);
                }
                TextView lt = tv(inline(r.length > 0 ? r[0] : ""), 14, prim());
                lt.setTypeface(null, Typeface.BOLD);
                lt.setPadding(0, dp(3), 0, dp(3));
                lt.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                in.addView(lt);
                for (int j = 1; j < r.length; j++) {
                    String key = (j < head.length ? head[j] : "");
                    String val = (j < r.length ? r[j] : "");
                    StringBuilder sb = new StringBuilder();
                    if (key.length() > 0) sb.append(key).append("：");
                    sb.append(val);
                    TextView vt = tv(inline(sb.toString()), 13, col(com.google.android.material.R.attr.colorOnSurface, 0xFFECF0F6));
                    vt.setLineSpacing(dp(4), 1f);
                    vt.setPadding(dp(2), dp(2), dp(2), dp(3));
                    vt.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                    in.addView(vt);
                }
            }
        }
        col.addView(c);
    }

    /** 行内 markdown：**粗体**、`代码`（等宽＋主色）；其余原样 */
    CharSequence inline(String s) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        if (s == null) return out;
        int i = 0;
        while (i < s.length()) {
            if (s.startsWith("**", i)) {
                int e = s.indexOf("**", i + 2);
                if (e < 0) { out.append(s.substring(i)); break; }
                int st = out.length();
                out.append(s.substring(i + 2, e));
                out.setSpan(new StyleSpan(Typeface.BOLD), st, out.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = e + 2;
            } else if (s.charAt(i) == '`') {
                int e = s.indexOf('`', i + 1);
                if (e < 0) { out.append(s.substring(i)); break; }
                int st = out.length();
                out.append(s.substring(i + 1, e));
                out.setSpan(new TypefaceSpan("monospace"), st, out.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new ForegroundColorSpan(prim()), st, out.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = e + 1;
            } else { out.append(s.charAt(i)); i++; }
        }
        return out;
    }

    /** 表头首格是不是「序号列」（如 #、1、2）：只有它才用窄列；中文词（工具／组件／版本）走权重列 */
    static boolean isIndexHead(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.length() == 0 || t.length() > 2) return false;
        for (int i = 0; i < t.length(); i++) {
            if (t.charAt(i) >= 0x2E80) return false;      // 含汉字／全角字符 → 不是序号列
        }
        return true;
    }

    /** 「1. 」这种有序列表行 */
    static boolean isOrdered(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') i++;
        return i > 0 && s.startsWith(". ", i);
    }

    // ───────── 小零件 ─────────
    String verTail() {
        String v = "";
        try { v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Throwable t) { }
        return v.length() == 0 ? "" : " · 应用版本 v" + v;
    }

    MaterialCardView card(boolean clickable) {
        MaterialCardView c = new MaterialCardView(this);
        c.setRadius(dp(18));
        c.setCardElevation(0f);
        c.setStrokeWidth(dp(1));
        c.setStrokeColor(col(com.google.android.material.R.attr.colorOutline, 0x339AA0A6));
        c.setCardBackgroundColor(col(com.google.android.material.R.attr.colorSurface, 0xFF1B1F24));
        if (clickable) { c.setClickable(true); c.setFocusable(true); ripple(c); }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        c.setLayoutParams(lp);
        return c;
    }

    void ripple(View v) {
        try {
            android.util.TypedValue tv = new android.util.TypedValue();
            if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true) && tv.resourceId != 0) {
                v.setForeground(getDrawable(tv.resourceId));
            }
        } catch (Throwable t) { }
    }

    TextView quiet(String s) {
        TextView t = tv(s, 12, colVariant());
        t.setPadding(dp(4), dp(10), dp(4), dp(8));
        t.setLineSpacing(dp(3), 1f);
        t.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return t;
    }

    TextView tv(CharSequence s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    int colVariant() { return col(com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF9AA0A6); }

    int prim() { return col(com.google.android.material.R.attr.colorPrimary, 0xFF6C8CFF); }

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
