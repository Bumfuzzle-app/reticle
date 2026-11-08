package app.reticle;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 渲染宿主：透明、无动画、不抢焦点、不进最近任务。
 * WebView 必须挂真实窗口才会驱动渲染进程（离屏的 WebView，JS 回调永远不回来）。
 *
 * 取图（1.1.40 起，一次成型）：先按一屏高画一次，量页面里的黑标尺得到 CSS→物理 系数，
 * 算出内容总高，把 WebView 拉高到总高（**只改这一次**），等合成器跟上后整体画一张。
 *
 * 四条实测事实（2026-11-08，别凭直觉改）：
 *   ① 往比视口高的大画布上 wv.draw()，WebView 一个像素都不画（1588×7872 → 全白）。
 *      画布尺寸必须＝WebView 尺寸，所以先改视口再画，而不是先画大再拼。
 *   ② CSS px → 物理 px 的系数不能信 JS 报数：window.innerHeight 报 2200，实际 1 CSS 占 4 物理像素。
 *      唯一可靠的是量：页面里插一根 100 CSS px 的黑标尺，数它的物理像素高。
 *   ③ 页面自己也滚不动：布局视口 2200 CSS 装得下 2024 CSS 的文档，window.scrollTo 无处可滚
 *      （实测 scrollY 恒为 0）。所以「分段截再拼」这条老路在这台机上走不通。
 *   ④ 改完尺寸要等：合成器异步，立刻 draw 拿到的是旧帧，所以画两遍、中间隔一拍。
 */
public class RenderActivity extends Activity {

    static final String TAG = "reticle.render";
    static final int MAX_H = 16000;                  // 单张位图高度上限（防 OOM 的最后一道闸）
    static final int CANVAS_CSS_H = 2200;            // 首屏名义高 × sScale＝首屏物理高
    static final int MAX_TOTAL_PX = 32000;           // 成品总高上限，物理 px
    static final long MEM_BUDGET = 160L * 1048576L;  // 成品位图内存预算
    static final String MARK = "position:absolute;top:0;left:0;width:12px;height:100px;"
                             + "background:#000;z-index:2147483647";
    /** 标尺下面压一条白挡板：页面底色本身就是黑的，也能把扫描卡在 100 CSS px 处。 */
    static final String MARK2 = "position:absolute;top:100px;left:0;width:12px;height:10px;"
                              + "background:#fff;z-index:2147483647";

    static volatile String sMd, sHtml, sOutAbs, sErr, sNote, sTheme;
    static volatile int sWidth, sScale, sOutW, sOutH, sSegs;
    static volatile CountDownLatch sLatch;
    static volatile boolean sStarted;
    public static volatile String sDebug = "";

    /** 不给主题＝default（老调用输出不变）。 */
    public static String render(Context ctx, String md, String html, int width, int scale, String outAbs) {
        return render(ctx, md, html, width, scale, outAbs, "default");
    }

    /**
     * theme 只认 default / poster / invoice（Render 侧已校验）。
     * 坑：App 在后台时系统会拦掉 Activity 启动（vivo 尤其），
     * 表现为「谁也不报错、干等 40 秒」。所以起窗口后先等 2.5 秒，看它有没有真的创建。
     */
    public static String render(Context ctx, String md, String html, int width, int scale, String outAbs, String theme) {
        sMd = md == null ? "" : md;
        sTheme = theme == null ? "default" : theme;
        sHtml = html == null ? "" : html;
        sOutAbs = outAbs;
        sWidth = width; sScale = scale;
        sErr = null; sNote = null; sOutW = 0; sOutH = 0; sSegs = 1;
        sStarted = false;
        final CountDownLatch latch = new CountDownLatch(1);
        sLatch = latch;
        try {
            Intent i = new Intent(ctx, RenderActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            ctx.startActivity(i);
        } catch (Throwable t) {
            return "打不开渲染窗口：" + t;
        }
        try {
            if (!latch.await(2500, TimeUnit.MILLISECONDS)) {
                if (!sStarted) return Words.RP_RENDER_NOSTART;
                if (!latch.await(37500, TimeUnit.MILLISECONDS)) return "渲染超时（40 秒）";
            }
        } catch (InterruptedException e) {
            return "渲染被中断";
        }
        return sErr;
    }

    WebView wv;
    boolean done;
    Bitmap big;
    Canvas bigCanvas;
    Bitmap probe;
    Canvas probeCanvas;
    int bigW, bigTotal, contentPx, vw, vh, wantCss;
    int capPx; int attempt;
    double factor = 2;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            getWindow().setDimAmount(0f);
            getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        } catch (Throwable t) { }
        build();
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        build();
    }

    void build() {
        done = false;
        sStarted = true;
        try {
            if (wv != null) { try { wv.destroy(); } catch (Throwable t) { } wv = null; }
            vw = sWidth * sScale;
            vh = Math.min(MAX_H, CANVAS_CSS_H * sScale);
            wv = new WebView(this);
            FrameLayout root = new FrameLayout(this);
            root.setBackgroundColor(Color.WHITE);
            root.addView(wv, new FrameLayout.LayoutParams(vw, vh));
            setContentView(root);

            WebSettings st = wv.getSettings();
            st.setJavaScriptEnabled(true);
            st.setDomStorageEnabled(true);
            st.setAllowFileAccess(true);
            st.setAllowContentAccess(false);
            st.setUseWideViewPort(true);
            st.setLoadWithOverviewMode(false);
            st.setTextZoom(100);

            wv.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView v, String url) {
                    v.postDelayed(new Runnable() { public void run() { inject(); } }, 260);
                }
            });

            String doc = (sHtml != null && sHtml.length() > 0) ? sHtml : template(sWidth, sTheme);
            wv.loadDataWithBaseURL("file:///android_asset/render/", doc, "text/html", "utf-8", null);

            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                public void run() { if (!done) fail("超时"); }
            }, 36000);
        } catch (Throwable t) {
            fail("建窗口失败 " + t);
        }
    }

    /** 主题 → 样式文件；Render 已校验过名字，这里的兜底是 base.css。 */
    static String cssFor(String theme) {
        String k = theme == null ? "" : theme.trim().toLowerCase(java.util.Locale.ROOT);
        if (k.equals("poster")) return "poster.css";
        if (k.equals("invoice")) return "invoice.css";
        return "base.css";
    }

    static String template(int w, String theme) {
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=" + w + ",initial-scale=1,maximum-scale=1\">"
            + "<link rel=\"stylesheet\" href=\"" + cssFor(theme) + "\"></head><body>"
            + "<div id=\"__m\" style=\"" + MARK + "\"></div>"
            + "<div id=\"__s\" style=\"" + MARK2 + "\"></div>"
            + "<article id=\"c\"></article>"
            + "<script src=\"marked.min.js\"></script>"
            + "<script>function __renderMD(md){"
            + "var t=document.getElementById('c');t.innerHTML=marked.parse(md);"
            + "var r=Math.ceil(t.getBoundingClientRect().height+80);"
            + "var b=Math.ceil(document.body.scrollHeight);"
            + "var l=0;try{var e=t.lastElementChild;if(e)l=Math.ceil(e.getBoundingClientRect().bottom);}catch(x){}"
            + "window.__dbg=r+','+b+','+l;return 1;}</script></body></html>";
    }

    void inject() {
        if (sMd == null || sMd.length() == 0) { measureRaw(); return; }
        try {
            wv.evaluateJavascript("__renderMD(" + JSONObject.quote(sMd) + ")",
                new ValueCallback<String>() {
                    @Override public void onReceiveValue(String v) { measure(); }
                });
        } catch (Throwable t) { fail("注入 Markdown 失败 " + t); }
    }

    /** 量内容高（CSS px）；量不到就退回 scrollHeight。 */
    void measure() {
        wv.postDelayed(new Runnable() {
            public void run() {
                try {
                    wv.evaluateJavascript("window.__dbg",
                        new ValueCallback<String>() {
                            @Override public void onReceiveValue(String v) {
                                sDebug = v;
                                int css = 0;
                                try {
                                    String s = v == null ? "" : v.replace("\"", "").trim();
                                    String[] pp = s.split(",");
                                    int r = 0, b = 0, l = 0;
                                    if (pp.length >= 1) r = Integer.parseInt(pp[0].trim());
                                    if (pp.length >= 2) b = Integer.parseInt(pp[1].trim());
                                    if (pp.length >= 3) l = Integer.parseInt(pp[2].trim());
                                    css = Math.max(r, Math.max(b, l > 0 ? l + 80 : 0)) + 8;
                                } catch (Throwable t) { }
                                if (css <= 0) { measureRaw(); return; }
                                shoot(css);
                            }
                        });
                } catch (Throwable t) { fail("量尺寸失败 " + t); }
            }
        }, 460);
    }

    /** html 直给（没有 #c）：补一根标尺，再用 body 的 scrollHeight 当内容高。 */
    void measureRaw() {
        wv.postDelayed(new Runnable() {
            public void run() {
                try {
                    wv.evaluateJavascript(
                        "(function(){try{if(!document.getElementById('__m')){var d=document.createElement('div');"
                        + "d.id='__m';d.setAttribute('style','" + MARK + "');document.body.appendChild(d);"
                        + "var sp=document.createElement('div');sp.id='__s';sp.setAttribute('style','" + MARK2 + "');document.body.appendChild(sp);}}catch(x){}"
                        + "return Math.max(Math.ceil(document.body?document.body.scrollHeight:0),"
                        + "Math.ceil(document.documentElement.scrollHeight));})()",
                        new ValueCallback<String>() {
                            @Override public void onReceiveValue(String v) {
                                int css = 0;
                                try { css = Integer.parseInt(v == null ? "" : v.replace("\"", "").trim()) + 48; } catch (Throwable t) { }
                                shoot(css);
                            }
                        });
                } catch (Throwable t) { fail("量尺寸失败 " + t); }
            }
        }, 460);
    }

    /** 第一步：按一屏画一次，用来量标尺（此刻画布尺寸＝视口，能正常画出来）。 */
    void shoot(int cssH) {
        wantCss = cssH;
        try {
            probe = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888);
            probeCanvas = new Canvas(probe);
        } catch (Throwable t) {
            fail("内存不够：临时位图 " + vw + "×" + vh + " 分配不了；调小 scale 或 width 再试");
            return;
        }
        wv.postDelayed(new Runnable() { public void run() { calibrate(); } }, 150);
    }

    void calibrate() {
        try {
            probeCanvas.drawColor(Color.WHITE);
            wv.draw(probeCanvas);
            factor = measureFactor();
        } catch (Throwable t) { factor = sScale; }
        try { probe.recycle(); } catch (Throwable t) { }
        probe = null; probeCanvas = null;
        try {
            wv.evaluateJavascript("var a=[document.getElementById('__m'),document.getElementById('__s')];for(var i=0;i<a.length;i++){if(a[i]&&a[i].parentNode)a[i].parentNode.removeChild(a[i]);}",
                new ValueCallback<String>() {
                    @Override public void onReceiveValue(String v) {
                        wv.postDelayed(new Runnable() { public void run() { resizeAndPrepare(); } }, 180);
                    }
                });
        } catch (Throwable t) { resizeAndPrepare(); }
    }

    /** 数标尺的物理像素高：黑标尺 100 CSS px 高 → 系数＝像素高/100。 */
    double measureFactor() {
        try {
            int x = Math.min(vw - 1, 2);
            int h = 0;
            int lim = Math.min(vh, 4000);
            for (int y = 0; y < lim; y++) {
                int p = probe.getPixel(x, y);
                int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
                if (r + g + b < 90) h++;
                else break;
            }
            if (h >= 20 && h <= 1000) return h / 100.0;
        } catch (Throwable t) { }
        return sScale;
    }

    /** 第二步：按真实系数定总高 → 把 WebView 拉高（只改这一次）→ 分配成品位图。 */
    void resizeAndPrepare() {
        int need = vh;
        if (wantCss > 0) need = (int) Math.ceil(wantCss * factor) + 60;
        if (need < vh) need = vh;
        int cap = (int) Math.min((long) MAX_TOTAL_PX, Math.max((long) vh, MEM_BUDGET / (Math.max(1, vw) * 4L)));
        int total = Math.min(need, cap);
        capPx = cap;
        contentPx = need;
        sNote = null;
        if (wantCss <= 0)
            sNote = "量不到内容高度（html 直给时常见），只出了第一屏；要长图请用 md";
        else if (need > cap)
            sNote = "内容太长（约 " + Math.round(need / factor) + " CSS px），画到 " + Math.round(cap / factor)
                  + " CSS px 就截断了；要完整请调小 scale 或 width，或分两份出";

        try {
            big = Bitmap.createBitmap(vw, total, Bitmap.Config.ARGB_8888);
        } catch (Throwable t1) {
            int retry = Math.max(vh, total * 6 / 10);
            try {
                total = Math.min(retry, cap);
                big = Bitmap.createBitmap(vw, total, Bitmap.Config.ARGB_8888);
                sNote = "内存不够画整张，只画到 " + Math.round(total / factor) + " CSS px；要整张请调小 scale 或 width";
            } catch (Throwable t2) {
                fail("内存不够：位图 " + vw + "×" + total + " 约 " + ((long) vw * total * 4 / 1048576) + " MB 分配不了；调小 scale 或 width 再试");
                return;
            }
        }
        bigW = vw; bigTotal = total;
        try {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) wv.getLayoutParams();
            lp.height = total;
            wv.setLayoutParams(lp);
            wv.requestLayout();
            wv.invalidate();
        } catch (Throwable t) { }
        wv.postDelayed(new Runnable() { public void run() { grab(false); } }, 480);
    }

    /** 画两遍：第一遍可能拿到改尺寸前的旧帧，第二遍才是新高度下的完整画面。 */
    void grab(final boolean second) {
        try {
            if (bigCanvas == null) bigCanvas = new Canvas(big);
            bigCanvas.drawColor(Color.WHITE);
            wv.draw(bigCanvas);
        } catch (Throwable t) { fail("截图失败 " + t); return; }
        if (!second) {
            wv.postDelayed(new Runnable() { public void run() { grab(true); } }, 300);
        } else {
            recheck();
        }
    }


    /** 收尾前再看一眼：图片/字体可能在量高之后才落位。内容变高了就重排一次（最多一轮）。 */
    void recheck() {
        if (attempt >= 1) { finishShot(); return; }
        try {
            wv.evaluateJavascript(
                "Math.max(Math.ceil(document.body.scrollHeight),"
                + "Math.ceil((document.getElementById('c')||document.body).getBoundingClientRect().height+80))",
                new ValueCallback<String>() {
                    @Override public void onReceiveValue(String v) {
                        int css2 = 0;
                        try { css2 = Integer.parseInt(v == null ? "" : v.replace("\"", "").trim()); } catch (Throwable t) { }
                        int need2 = css2 > 0 ? (int) Math.ceil((css2 + 8) * factor) + 60 : 0;
                        if (need2 > bigTotal + 40 && need2 <= capPx) {
                            attempt++;
                            bigTotal = need2;
                            contentPx = need2;
                            try { if (big != null) big.recycle(); } catch (Throwable t) { }
                            big = null; bigCanvas = null;
                            try {
                                big = Bitmap.createBitmap(bigW, bigTotal, Bitmap.Config.ARGB_8888);
                            } catch (Throwable t) {
                                fail("内存不够：位图 " + bigW + "×" + bigTotal + " 分配不了；调小 scale 或 width 再试");
                                return;
                            }
                            try {
                                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) wv.getLayoutParams();
                                lp.height = bigTotal;
                                wv.setLayoutParams(lp);
                                wv.requestLayout();
                                wv.invalidate();
                            } catch (Throwable t) { }
                            wv.postDelayed(new Runnable() { public void run() { grab(false); } }, 480);
                        } else {
                            finishShot();
                        }
                    }
                });
        } catch (Throwable t) { finishShot(); }
    }
    /** 收尾：底部留白多就裁掉（少于 240px 的尾巴留着不折腾，省一次整图拷贝）。 */
    void finishShot() {
        int cut = bigTotal;
        try {
            int[] row = new int[bigW];
            int from = Math.min(bigTotal - 1, contentPx + 24);
            if (from < 0) from = 0;
            int lastY = -1;
            for (int y = from; y >= 0; y -= 3) {
                big.getPixels(row, 0, bigW, 0, y, bigW, 1);
                boolean blank = true;
                for (int x = 4; x < bigW - 4; x += 9) {
                    if (row[x] != Color.WHITE) { blank = false; break; }
                }
                if (!blank) { lastY = y; break; }
            }
            if (lastY > 0 && bigTotal - (lastY + 44) > 240) cut = Math.min(bigTotal, lastY + 44);
        } catch (Throwable t) { }

        try {
            Bitmap out = big;
            if (cut < bigTotal) {
                out = Bitmap.createBitmap(big, 0, 0, bigW, cut);
                try { big.recycle(); } catch (Throwable t) { }
            }
            File f = new File(sOutAbs);
            File p = f.getParentFile();
            if (p != null) p.mkdirs();
            FileOutputStream fo = new FileOutputStream(f);
            out.compress(Bitmap.CompressFormat.PNG, 100, fo);
            fo.close();
            try { out.recycle(); } catch (Throwable t) { }
            sOutW = bigW; sOutH = cut;
            sErr = null;
            done = true;
        } catch (Throwable t) {
            sErr = "截图失败 " + t;
            done = true;
        } finally {
            try { if (probe != null) probe.recycle(); } catch (Throwable t) { }
            big = null; bigCanvas = null;
            if (sLatch != null) sLatch.countDown();
            try { finish(); } catch (Throwable t) { }
        }
    }

    void fail(String why) {
        if (done) return;
        done = true;
        sErr = why;
        try { if (big != null) big.recycle(); } catch (Throwable t) { }
        try { if (probe != null) probe.recycle(); } catch (Throwable t) { }
        big = null; bigCanvas = null;
        if (sLatch != null) sLatch.countDown();
        try { finish(); } catch (Throwable t) { }
    }

    @Override protected void onDestroy() {
        try { if (wv != null) wv.destroy(); } catch (Throwable t) { }
        try { if (big != null) big.recycle(); } catch (Throwable t) { }
        try { if (probe != null) probe.recycle(); } catch (Throwable t) { }
        super.onDestroy();
    }
}
