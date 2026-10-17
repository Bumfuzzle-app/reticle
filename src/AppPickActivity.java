package app.reticle;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 71 号单 B 线（2026-10-15）· 「让位名单」＝**勾选应用列表**（用户 2026-10-15 提：
 * "应用内在 AI 客户端的选项，居然要用户自己输包名才能塞进白名单，不能拉取应用列表让用户自己画勾吗？"）
 *
 * 三条路，能拿哪个用哪个：
 *   ① PackageManager 枚举「能启动的应用」（ACTION_MAIN + CATEGORY_LAUNCHER）→ 中文名＋图标＋勾选框
 *      ★为此在 manifest 里声明了 QUERY_ALL_PACKAGES：我们**不在 Play 上架**（走 GitHub Release 自装），
 *        不受该权限的商店政策约束；不声明的话 Android 11+ 只能看到"包可见性"允许的那几个应用。
 *   ② ★兜底：Shizuku 的 shell 身份 `pm list packages`（只给包名，至少让用户能勾）。
 *   ③ 手动输入包名（列表里找不到的：刚装的、被 ROM 藏起来的、别的客户端）——老路保留，不拆。
 *
 * 隐私：列表**只在本机读、只写进本机 prefs**，不上传、不进通知；日志里只写**数量**，不写包名串。
 * 勾选后**立刻生效**（Mascot 每次都现读 prefs，不必重启无障碍）。
 */
public class AppPickActivity extends AppCompatActivity {

    static final String PREFS = "settings";
    static final String K_THEME = "theme_mode";
    static final String K_BLACK = "black_night";

    static final int ICON_DP = 40;

    final Handler ui = new Handler(Looper.getMainLooper());
    final List<Item> all = new ArrayList<Item>();
    final List<Item> shown = new ArrayList<Item>();
    final Set<String> manual = new LinkedHashSet<String>();   // 不在列表里的手输包名（保存时不许丢）

    ListView list;
    EditText search;
    CheckBox showSys;
    TextView hint, empty;
    ItemAdapter adapter;
    boolean loading = true;
    String source = "";

    public static void open(Context c) {
        try {
            Intent i = new Intent(c, AppPickActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            c.startActivity(i);
        } catch (Throwable t) { }
    }

    /** 静态内部类：一行应用。 */
    static class Item {
        String pkg;
        String label;
        boolean sys;
        Drawable icon;
        boolean checked;
    }

    @Override
    protected void onCreate(Bundle b) {
        setTheme(themeRes());
        super.onCreate(b);
        buildUi();
        new Thread(new Runnable() { public void run() { load(); } }).start();
    }

    int themeRes() {
        try {
            boolean night;
            int m = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(K_THEME, 0);
            if (m == 2) night = true;
            else if (m == 1) night = false;
            else night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            if (!night) return R.style.Theme_Reticle;
            return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(K_BLACK, false) ? R.style.Theme_Reticle_Black : R.style.Theme_Reticle_Dark;
        } catch (Throwable t) { return R.style.Theme_Reticle; }
    }

    // ───────────── 界面 ─────────────

    void buildUi() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = dp(18);
        col.setPadding(p, p, p, dp(8));

        // 72 号单（71 遗留）：顶部标题别被状态栏／刘海盖住
        try {
            final int pl = p, pr = p, pb = dp(8);
            col.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets ins) {
                    int top = 0;
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= 30) top = ins.getInsets(android.view.WindowInsets.Type.statusBars()).top;
                        else top = ins.getSystemWindowInsetTop();
                    } catch (Throwable t) { }
                    v.setPadding(pl, pl + top, pr, pb);
                    return ins;
                }
            });
            col.requestApplyInsets();
        } catch (Throwable t) { }

        TextView t = new TextView(this);
        t.setText(Words.AP_T);
        t.setTextSize(20);
        t.setTypeface(null, Typeface.BOLD);
        col.addView(t);

        TextView d = new TextView(this);
        d.setText(Words.AP_D);
        d.setTextSize(12);
        d.setPadding(0, dp(6), 0, dp(10));
        col.addView(d);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint(Words.AP_SEARCH);
        col.addView(search);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { refilter(); }
            public void afterTextChanged(Editable s) { }
        });

        showSys = new CheckBox(this);
        showSys.setText(Words.AP_SHOW_SYS);
        showSys.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { refilter(); }
        });
        col.addView(showSys);

        hint = new TextView(this);
        hint.setTextSize(12);
        hint.setPadding(0, dp(6), 0, dp(6));
        hint.setText(Words.AP_LOADING);
        col.addView(hint);

        list = new ListView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        list.setLayoutParams(lp);
        adapter = new ItemAdapter();
        list.setAdapter(adapter);
        col.addView(list);

        empty = new TextView(this);
        empty.setTextSize(12);
        empty.setText(Words.AP_EMPTY);
        empty.setVisibility(View.GONE);
        col.addView(empty);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(0, dp(10), 0, 0);
        MaterialButton mb = new MaterialButton(this);
        mb.setText(Words.AP_MANUAL);
        mb.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { askManual(); } });
        bar.addView(mb, new LinearLayout.LayoutParams(0, -2, 1f));
        MaterialButton done = new MaterialButton(this);
        done.setText(Words.AP_DONE);
        done.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        bar.addView(done, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(bar);

        setContentView(col);
    }

    // ───────────── 读列表（后台线程） ─────────────

    void load() {
        final List<Item> got = new ArrayList<Item>();
        String src = Words.AP_SRC_PM;
        Set<String> checked = checkedFromPrefs();
        try {
            PackageManager pm = getPackageManager();
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> ris = pm.queryIntentActivities(i, 0);
            Set<String> seen = new LinkedHashSet<String>();
            if (ris != null) {
                for (int k = 0; k < ris.size(); k++) {
                    ResolveInfo ri = ris.get(k);
                    if (ri == null || ri.activityInfo == null || ri.activityInfo.applicationInfo == null) continue;
                    String pk = ri.activityInfo.applicationInfo.packageName;
                    if (pk == null || pk.length() == 0 || !seen.add(pk)) continue;
                    Item it = new Item();
                    it.pkg = pk;
                    it.sys = (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    try { it.label = String.valueOf(ri.loadLabel(pm)); } catch (Throwable x) { it.label = pk; }
                    try { it.icon = ri.loadIcon(pm); } catch (Throwable x) { it.icon = null; }
                    got.add(it);
                }
            }
        } catch (Throwable x) { }
        // ★兜底：拿不到可启动列表（包可见性被限制／枚举失败）⇒ 走 Shizuku 的 pm list packages，只给包名
        if (got.isEmpty()) {
            src = Words.AP_SRC_SHZ;
            String[] pks = shzPackages();
            Set<String> seen = new LinkedHashSet<String>();
            for (int k = 0; k < pks.length; k++) {
                String pk = pks[k];
                if (pk.length() == 0 || !seen.add(pk)) continue;
                Item it = new Item();
                it.pkg = pk;
                it.label = pk;
                it.sys = false;
                got.add(it);
            }
        }
        // 已在名单里、但列表里没有的（自己、别的客户端、被 ROM 藏起来的）⇒ 补进来，让用户看得见、能取消
        Set<String> have = new LinkedHashSet<String>();
        for (int k = 0; k < got.size(); k++) have.add(got.get(k).pkg);
        for (String pk : checked) {
            if (pk.length() == 0 || have.contains(pk)) continue;
            Item it = new Item();
            it.pkg = pk;
            it.label = lookupLabel(pk);
            it.sys = false;
            got.add(it);
        }
        for (int k = 0; k < got.size(); k++) got.get(k).checked = checked.contains(got.get(k).pkg);
        final int n = got.size(), m = countChecked(got);
        final String srcF = src;
        ui.post(new Runnable() {
            public void run() {
                all.clear();
                all.addAll(got);
                loading = false;
                source = srcF;
                refilter();
                // ★隐私：日志只写数量、来源，绝不写包名串
                Mascot.logLine(AppPickActivity.this, String.format(Locale.ROOT, Words.AP_LOG_FMT, n, m) + "（" + srcF + "）");
            }
        });
    }

    /** Shizuku 的 shell 身份读包名（兜底；失败就回空）。 */
    String[] shzPackages() {
        try {
            if (!rikka.shizuku.Shizuku.pingBinder()) return new String[0];
            String r = VeriService.shxRun("pm list packages", 12000L);
            JSONObject o = new JSONObject(r);
            if (o.optInt("ok", 0) != 1) return new String[0];
            String out = o.optString("out", "");
            String[] lines = out.split("\n");
            List<String> l = new ArrayList<String>();
            for (int k = 0; k < lines.length; k++) {
                String s = lines[k].trim();
                if (s.startsWith("package:")) l.add(s.substring("package:".length()).trim());
            }
            return l.toArray(new String[0]);
        } catch (Throwable t) { return new String[0]; }
    }

    String lookupLabel(String pk) {
        try { return String.valueOf(getPackageManager().getApplicationInfo(pk, 0).loadLabel(getPackageManager())); }
        catch (Throwable t) { return pk; }
    }

    Set<String> checkedFromPrefs() {
        Set<String> s = new LinkedHashSet<String>();
        try {
            String v = Mascot.hidePkgs(this);
            if (v == null) return s;
            String[] a = v.split(",");
            for (int k = 0; k < a.length; k++) { String x = a[k].trim(); if (x.length() > 0) s.add(x); }
        } catch (Throwable t) { }
        return s;
    }

    int countChecked(List<Item> l) {
        int n = 0;
        for (int k = 0; k < l.size(); k++) if (l.get(k).checked) n++;
        return n;
    }

    // ───────────── 过滤／排序／保存 ─────────────

    void refilter() {
        String q = search == null || search.getText() == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        boolean sysToo = showSys != null && showSys.isChecked();
        shown.clear();
        for (int k = 0; k < all.size(); k++) {
            Item it = all.get(k);
            if (!sysToo && it.sys && !it.checked) continue;      // 系统应用默认折叠（已勾的永远看得见）
            if (q.length() > 0) {
                String lb = it.label == null ? "" : it.label.toLowerCase(Locale.ROOT);
                if (lb.indexOf(q) < 0 && it.pkg.toLowerCase(Locale.ROOT).indexOf(q) < 0) continue;
            }
            shown.add(it);
        }
        Collections.sort(shown, new Comparator<Item>() {
            public int compare(Item a, Item c) {
                if (a.checked != c.checked) return a.checked ? -1 : 1;   // 已勾的置顶
                String x = a.label == null ? a.pkg : a.label;
                String y = c.label == null ? c.pkg : c.label;
                return x.compareToIgnoreCase(y);
            }
        });
        if (adapter != null) adapter.notifyDataSetChanged();
        if (hint != null) {
            hint.setText(loading ? Words.AP_LOADING
                    : String.format(Locale.ROOT, Words.AP_HINT_FMT, shown.size(), countChecked(all))
                      + (Words.AP_SRC_SHZ.equals(source) ? Words.AP_SHZ_NOTE : ""));
        }
        if (empty != null) empty.setVisibility((!loading && shown.isEmpty()) ? View.VISIBLE : View.GONE);
    }

    /** 勾选＝立刻生效（写 prefs；Mascot 每次现读）。 */
    void onToggle(Item it, boolean on) {
        it.checked = on;
        save();
        refilter();
    }

    void save() {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < all.size(); k++) {
            Item it = all.get(k);
            if (it.checked) { if (sb.length() > 0) sb.append(","); sb.append(it.pkg); }
        }
        for (String pk : manual) {   // 手输的（不在列表里）原样留着
            if (pk.length() == 0 || hasPkg(pk)) continue;
            if (sb.length() > 0) sb.append(",");
            sb.append(pk);
        }
        Mascot.setHidePkgs(this, sb.toString());
    }

    boolean hasPkg(String pk) {
        for (int k = 0; k < all.size(); k++) if (all.get(k).pkg.equals(pk)) return true;
        return false;
    }

    /** 手输包名：老路保留（列表里找不到的用这个）。 */
    void askManual() {
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setText(Mascot.hidePkgs(this));
        try { et.setSelection(et.getText().length()); } catch (Throwable t) { }
        new android.app.AlertDialog.Builder(this)
                .setTitle(Words.AP_MANUAL_T)
                .setMessage(Words.AP_MANUAL_D)
                .setView(et)
                .setNegativeButton(Words.CF_DLG_CANCEL, (DialogInterface.OnClickListener) null)
                .setPositiveButton(Words.CF_DLG_OK, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String v = et.getText() == null ? "" : et.getText().toString().trim();
                        Mascot.setHidePkgs(AppPickActivity.this, v);
                        Set<String> now = checkedFromPrefs();
                        manual.clear();
                        for (String pk : now) if (!hasPkg(pk)) manual.add(pk);
                        for (int k = 0; k < all.size(); k++) all.get(k).checked = now.contains(all.get(k).pkg);
                        refilter();
                    }
                })
                .show();
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    // ───────────── 列表适配器 ─────────────

    class ItemAdapter extends BaseAdapter {
        public int getCount() { return shown.size(); }
        public Object getItem(int i) { return shown.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int pos, View cv, ViewGroup parent) {
            LinearLayout row;
            if (cv == null) {
                row = new LinearLayout(AppPickActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(8), 0, dp(8));
                row.setTag("row");
            } else { row = (LinearLayout) cv; }
            row.removeAllViews();
            final Item it = shown.get(pos);
            ImageView iv = new ImageView(AppPickActivity.this);
            int s = dp(ICON_DP);
            iv.setLayoutParams(new LinearLayout.LayoutParams(s, s));
            if (it.icon != null) iv.setImageDrawable(it.icon);
            row.addView(iv);
            LinearLayout txtCol = new LinearLayout(AppPickActivity.this);
            txtCol.setOrientation(LinearLayout.VERTICAL);
            txtCol.setPadding(dp(12), 0, dp(8), 0);
            TextView nm = new TextView(AppPickActivity.this);
            nm.setText(it.label == null ? it.pkg : it.label);
            nm.setTextSize(15);
            txtCol.addView(nm);
            TextView pk = new TextView(AppPickActivity.this);
            pk.setText(it.pkg + (it.sys ? Words.AP_SYS_TAG : ""));
            pk.setTextSize(11);
            txtCol.addView(pk);
            row.addView(txtCol, new LinearLayout.LayoutParams(0, -2, 1f));
            CheckBox cb = new CheckBox(AppPickActivity.this);
            cb.setChecked(it.checked);
            cb.setOnCheckedChangeListener(null);
            cb.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(android.widget.CompoundButton v, boolean on) { onToggle(it, on); }
            });
            row.addView(cb);
            return row;
        }
    }
}
