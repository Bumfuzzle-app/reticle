package app.reticle;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.StyleSpan;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 子集 → PDF。
 *
 * 引擎：PdfDocument（系统 PDF 写入）+ StaticLayout（系统排版，中文折行/行距归它）。
 * 硬要求：自带静态 TrueType(glyf) 单文件中文字体——.ttc / OTF(CFF) / 可变字体在 PDF 里都会
 * 退化成 Type3 轮廓（清晰但选不中、搜不到）；只有静态 TrueType 才会真嵌入 FontFile2。
 * 判定：grep -aob FontFile2 x.pdf | wc -l  > 0
 *
 * 不走 WebView 打印：PrintDocumentAdapter 的 LayoutResultCallback 构造器是 @hide。
 *
 * 分页：一律按行切（StaticLayout.getLineBottom），标题不落单，表格跨页重画表头。
 *
 * 主题：所有版式常量集中在 Theme 里，build(..., theme) 选一套；改版式只动 Theme.of 里的数字。
 */
public class PdfMake {

    static final int T_P = 0, T_H = 1, T_LI = 2, T_CODE = 3, T_QUOTE = 4, T_HR = 5, T_TABLE = 6, T_IMG = 7, T_PB = 8;

    static final int PH = 842;      // A4 高（pt）

    /** 页边距：由主题赋值（build 开头设置），所以不是 final。 */
    static int MT = 64;
    static int MB = 64;
    static int ML = 56;
    static int MR = 56;

    public static volatile int lastPages = 0;
    public static volatile long lastBytes = 0;
    public static volatile String lastFont = "";
    public static volatile String lastTheme = "default";
    public static volatile int lastLinks = 0;
    static File baseDir;

    static Theme th = Theme.of("default");

    static class Block {
        int type;
        int level;
        CharSequence text;
        List<String> lines;
        int[] aligns;      // 表格：每列对齐 0 左 / 1 中 / 2 右
    }

    // ───────────────────────── 主题 ─────────────────────────

    /**
     * 一套版式：字号、行距、留白、颜色、页眉页脚。
     * 加一套新主题＝在 of() 里加一个分支；排版逻辑不用动。
     */
    static class Theme {
        String name = "default";

        int mt = 64, mb = 64, ml = 56, mr = 56;

        float bodySize = 11f, bodyMult = 1.55f;
        int bodyColor = 0xFF222222;

        float[] hSize = {22f, 17f, 14f, 12f, 11f, 11f};
        float hMult = 1.35f;
        int hColor = 0xFF111111;
        int hSpTop1 = 14, hSpTop2 = 13, hSpTopOther = 10;
        int hSpBot1 = 8, hSpBotOther = 6;
        boolean hCenter1 = false, hCenter2 = false;
        int orphan = 40;             // 标题后至少留住的高度（约两行正文）

        int pSpTop = 6, pSpBot = 4;
        int liSpTop = 2, liSpBot = 2, liIndent = 14, liIndentStep = 16;

        float quoteSize = 10.5f, quoteMult = 1.5f;
        int quoteColor = 0xFF666666, quoteBar = 0xFFD8D8D8;
        int quoteGap = 8;

        int codeBg = 0xFFF5F6F8, codeColor = 0xFF333333;
        float codeSize = 9.5f, codeMult = 1.75f;
        int codePad = 12, codeGap = 6;

        int tableHeadBg = 0xFFF0F2F5, tableLine = 0xFFCCCCCC, tableColor = 0xFF333333;
        float tableSize = 9.5f, tableMult = 1.3f;
        int tablePadX = 8, tablePadY = 7;
        int tableMinW = 50, tableMinStart = 90;
        int tableGap = 4, tableGapBot = 6;

        int hrColor = 0xFFDDDDDD, hrGap = 8;

        int headerColor = 0xFFAAAAAA, headerLine = 0xFFE6E6E6;
        float headerSize = 8.5f;
        int footerColor = 0xFF9A9A9A;
        float footerSize = 8.5f;
        boolean header = true, footer = true;

        int imgGap = 10;
        int linkColor = 0xFF2A5C8A;
        boolean linkUnder = true;

        static Theme of(String n) {
            Theme t = new Theme();
            String k = n == null ? "" : n.trim().toLowerCase(Locale.ROOT);
            if (k.length() == 0 || k.equals("default") || k.equals("doc") || k.equals("plain"))
                return t;

            if (k.equals("poster")) {
                t.name = "poster";
                t.mt = 96; t.mb = 72; t.ml = 76; t.mr = 76;
                t.bodySize = 12.5f; t.bodyMult = 1.75f; t.bodyColor = 0xFF2A2A2A;
                t.hSize = new float[]{34f, 23f, 17f, 14f, 12f, 11f};
                t.hMult = 1.25f; t.hColor = 0xFF0F2B46;
                t.hSpTop1 = 26; t.hSpTop2 = 22; t.hSpTopOther = 16;
                t.hSpBot1 = 14; t.hSpBotOther = 10;
                t.hCenter1 = true; t.hCenter2 = true;
                t.orphan = 70;
                t.pSpTop = 10; t.pSpBot = 8;
                t.liSpTop = 3; t.liSpBot = 3; t.liIndent = 16; t.liIndentStep = 18;
                t.quoteSize = 12f; t.quoteColor = 0xFF55606B; t.quoteBar = 0xFF0F2B46;
                t.codeBg = 0xFFF1F4F8; t.codeSize = 10f; t.codePad = 14;
                t.tableHeadBg = 0xFFE8EFF7; t.tableLine = 0xFFB9C6D6;
                t.tableSize = 10f; t.tableMult = 1.4f;
                t.tableMinStart = 120; t.tableGap = 6; t.tableGapBot = 8;
                t.hrColor = 0xFF0F2B46; t.hrGap = 12;
                t.header = false; t.footer = true; t.footerSize = 8f;
                return t;
            }

            if (k.equals("invoice")) {
                t.name = "invoice";
                t.mt = 48; t.mb = 54; t.ml = 40; t.mr = 40;
                t.bodySize = 9.5f; t.bodyMult = 1.35f; t.bodyColor = 0xFF111111;
                t.hSize = new float[]{17f, 14f, 12f, 11f, 10f, 10f};
                t.hMult = 1.25f; t.hColor = 0xFF000000;
                t.hSpTop1 = 8; t.hSpTop2 = 10; t.hSpTopOther = 8;
                t.hSpBot1 = 4; t.hSpBotOther = 4;
                t.orphan = 30;
                t.pSpTop = 5; t.pSpBot = 3;
                t.liSpTop = 1; t.liSpBot = 1; t.liIndent = 12; t.liIndentStep = 12;
                t.quoteSize = 9f; t.quoteColor = 0xFF555555; t.quoteBar = 0xFFCCCCCC;
                t.quoteGap = 6;
                t.codeBg = 0xFFF7F7F7; t.codeSize = 8.5f; t.codeMult = 1.5f; t.codePad = 10;
                t.tableHeadBg = 0xFFEEEEEE; t.tableLine = 0xFF999999; t.tableColor = 0xFF111111;
                t.tableSize = 8.5f; t.tableMult = 1.3f;
                t.tablePadX = 6; t.tablePadY = 6;
                t.tableMinW = 40; t.tableMinStart = 70;
                t.hrColor = 0xFFCCCCCC;
                t.header = false; t.footer = true; t.footerSize = 8f;
                return t;
            }

            return null;
        }
    }

    // ───────────────────────── 入口 ─────────────────────────

    public static String build(Context ctx, String md, int width, File out) {
        return build(ctx, md, width, out, "default");
    }

    public static String build(Context ctx, String md, int width, File out, String theme) {
        lastPages = 0; lastBytes = 0; lastFont = ""; lastLinks = 0;
        Theme t = Theme.of(theme);
        if (t == null)
            return "主题只认 default / poster / invoice（现在是 " + theme + "）";
        th = t;
        lastTheme = t.name;
        MT = t.mt; MB = t.mb; ML = t.ml; MR = t.mr;

        baseDir = Home.get(ctx);
        List<Block> blocks = parse(md);
        if (blocks.isEmpty()) return "Markdown 里没有可排版的内容";

        Typeface tf;
        try {
            tf = typeface(ctx);
        } catch (Throwable e) {
            return "取字体失败：" + e;
        }

        int W = (int) Math.round(width * 0.75f);
        if (W < 400) W = 400;
        if (W > 1050) W = 1050;

        PdfDocument doc = new PdfDocument();
        Pager pg = new Pager(doc, tf, W, title);
        try {
            pg.layout(blocks);
            pg.finish();
            File p = out.getParentFile();
            if (p != null) p.mkdirs();
            FileOutputStream fo = new FileOutputStream(out);
            doc.writeTo(fo);
            fo.close();
        } catch (Throwable e) {
            return "生成 PDF 出错：" + e;
        } finally {
            try { doc.close(); } catch (Throwable e) { }
        }
        lastPages = pg.pageNo;
        if (pg.links.size() > 0) lastLinks = addLinks(out, pg.links);
        lastBytes = out.length();
        return null;
    }

    // ───────────────────────── 字体 ─────────────────────────

    /** ★1104 字体降级为「工程骨架」：不随包、不下载，直接用系统里的中文静态 TTF。
     *  为什么不用 NotoSansCJK：它是 .ttc 字体集合，PDF 会退化成 Type3 路径（能看、选不中、搜不到）。
     *  为什么先找 Fallback：单文件静态 TrueType，能真嵌入（FontFile2）且带 ToUnicode，文字可搜索。
     *  要换字体：把 ttf 放到 运行目录/fonts/pdf.ttf，优先用它 —— 这就是以后「字体选择」的扩展口。 */
    static final String[] SYS_TTF = {
        "/system/fonts/DroidSansFallback.ttf",
        "/system/fonts/DroidSansFallbackBBK.ttf",
        "/system/fonts/DroidSansFallbackMonster.ttf",
        "/system/fonts/NotoSansHans-Regular.ttf",
        "/system/fonts/NotoSansSC-Regular.ttf",
        "/system/fonts/NotoSansCJK-Regular.ttf",
    };

    static Typeface typeface(Context ctx) {
        File user = new File(Home.get(ctx), "fonts/pdf.ttf");
        if (user.isFile() && user.length() > 100000) {
            lastFont = "运行时字体（fonts/pdf.ttf）";
            try { return Typeface.createFromFile(user); } catch (Throwable t) { }
        }
        for (int i = 0; i < SYS_TTF.length; i++) {
            File f = new File(SYS_TTF[i]);
            if (!f.isFile() || f.length() < 100000) continue;
            try {
                Typeface tf = Typeface.createFromFile(f);
                if (tf != null) { lastFont = "系统字体（" + f.getName() + "）"; return tf; }
            } catch (Throwable t) { }
        }
        lastFont = "系统字体（sans-serif；中文可能退化成路径）";
        return Typeface.create("sans-serif", Typeface.NORMAL);
    }

    // ───────────────────────── 解析 ─────────────────────────

    static String title;

    static List<Block> parse(String md) {
        List<Block> out = new ArrayList<Block>();
        title = null;
        String s = md.replace("\r\n", "\n").replace("\r", "\n");
        String[] ls = s.split("\n", -1);
        int i = 0;
        while (i < ls.length) {
            String raw = ls[i];
            String t = raw.trim();
            if (t.length() == 0) { i++; continue; }
            String pb = t.replace(" ", "");
            if (pb.equals("<!--pagebreak-->")) {
                Block b = new Block(); b.type = T_PB; out.add(b); i++; continue;
            }

            if (t.startsWith("```")) {
                List<String> code = new ArrayList<String>();
                i++;
                while (i < ls.length && !ls[i].trim().startsWith("```")) { code.add(ls[i]); i++; }
                i++;
                Block b = new Block(); b.type = T_CODE; b.lines = code; out.add(b);
                continue;
            }
            if (t.equals("---") || t.equals("***") || t.equals("___")) {
                Block b = new Block(); b.type = T_HR; out.add(b); i++; continue;
            }
            if (t.charAt(0) == '#') {
                int lv = 0;
                while (lv < t.length() && t.charAt(lv) == '#') lv++;
                if (lv <= 6) {
                    String rest = t.substring(lv).trim();
                    Block b = new Block(); b.type = T_H; b.level = lv; b.text = inl(rest); out.add(b);
                    if (lv == 1 && title == null) title = rest;
                    i++; continue;
                }
            }
            if (t.startsWith(">")) {
                StringBuilder q = new StringBuilder();
                while (i < ls.length && ls[i].trim().startsWith(">")) {
                    String x = ls[i].trim().substring(1).trim();
                    if (q.length() > 0) q.append(' ');
                    q.append(x);
                    i++;
                }
                Block b = new Block(); b.type = T_QUOTE; b.text = inl(q.toString()); out.add(b);
                continue;
            }
            if (t.startsWith("|") && i + 1 < ls.length && isSep(ls[i + 1])) {
                List<String> rows = new ArrayList<String>();
                rows.add(t);
                int[] al = aligns(ls[i + 1]);
                i += 2;
                while (i < ls.length && ls[i].trim().startsWith("|")) { rows.add(ls[i].trim()); i++; }
                Block b = new Block(); b.type = T_TABLE; b.lines = rows; b.aligns = al; out.add(b);
                continue;
            }
            if (t.startsWith("![") && t.indexOf("](") > 2) {
                // 同一行可以有多张图：全部收进 lines，排版时并排
                List<String> srcs = new ArrayList<String>();
                String alt = "";
                int p = 0;
                while (p < t.length()) {
                    int b1 = t.indexOf("![", p);
                    if (b1 < 0) break;
                    int e1 = t.indexOf("](", b1 + 2);
                    if (e1 < 0) break;
                    int e2 = t.indexOf(')', e1 + 2);
                    if (e2 < 0) break;
                    if (srcs.isEmpty()) alt = t.substring(b1 + 2, e1).trim();
                    srcs.add(t.substring(e1 + 2, e2).trim());
                    p = e2 + 1;
                }
                if (!srcs.isEmpty()) {
                    Block b = new Block();
                    b.type = T_IMG;
                    b.text = inl(alt);
                    b.lines = srcs;
                    out.add(b);
                    i++;
                    continue;
                }
            }
            if (isLi(t)) {
                int ind = 0;
                int j = 0;
                while (j < raw.length() && (raw.charAt(j) == ' ' || raw.charAt(j) == '\t')) { ind += (raw.charAt(j) == '\t' ? 2 : 1); j++; }
                String body = t;
                String pre = "\u00b7 ";
                if (Character.isDigit(body.charAt(0))) {
                    int d = 0;
                    while (d < body.length() && Character.isDigit(body.charAt(d))) d++;
                    if (d < body.length() && (body.charAt(d) == '.' || body.charAt(d) == ')')) {
                        pre = body.substring(0, d + 1) + " ";
                        body = body.substring(d + 1).trim();
                    }
                } else {
                    if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ")) body = t.substring(2).trim();
                    else body = t.substring(1).trim();
                }
                Block b = new Block(); b.type = T_LI; b.level = ind / 2;
                SpannableStringBuilder sb = new SpannableStringBuilder(pre);
                sb.append(inl(body));
                b.text = sb;
                out.add(b);
                i++; continue;
            }

            int startI = i;
            StringBuilder p = new StringBuilder();
            while (i < ls.length) {
                String x = ls[i].trim();
                if (x.length() == 0) break;
                if (p.length() > 0 && isBlockStart(ls, i)) break;
                if (p.length() > 0) p.append(' ');
                p.append(x);
                i++;
            }
            if (i == startI) { i++; p.append(t); }
            Block b = new Block(); b.type = T_P; b.text = inl(p.toString()); out.add(b);
        }
        return out;
    }

    static boolean isLi(String t) {
        if (t.length() < 2) return false;
        if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ")) return true;
        int d = 0;
        while (d < t.length() && Character.isDigit(t.charAt(d))) d++;
        if (d > 0 && d < t.length() - 1 && (t.charAt(d) == '.' || t.charAt(d) == ')') && t.charAt(d + 1) == ' ') return true;
        return false;
    }

    static boolean isSep(String s) {
        String t = s.trim();
        if (t.length() < 2 || t.charAt(0) != '|') return false;
        boolean dash = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '-') dash = true;
            else if (c != '|' && c != ':' && c != ' ') return false;
        }
        return dash;
    }

    /** 从表格分隔行解析每列对齐：`:--` 左 / `:--:` 中 / `--:` 右 */
    static int[] aligns(String sep) {
        List<String> cs = Pager.cells(sep);
        int[] a = new int[cs.size()];
        for (int i = 0; i < a.length; i++) {
            String v = cs.get(i);
            boolean l = v.startsWith(":");
            boolean r = v.endsWith(":");
            a[i] = (l && r) ? 1 : (r ? 2 : 0);
        }
        return a;
    }

    static boolean isBlockStart(String[] ls, int i) {
        String x = ls[i].trim();
        if (x.length() == 0) return true;
        if (x.startsWith("#") || x.startsWith(">") || x.startsWith("```") || x.startsWith("|")) return true;
        if (x.equals("---") || x.equals("***") || x.equals("___")) return true;
        return isLi(x) || (x.startsWith("![") && x.indexOf("](") > 2);
    }

    /**
     * 行内格式：**粗体**、`代码`、[文字](链接) → 文字（并挂 URLSpan，供 PDF 链接注释取位置）。
     * lim：无空格长串每隔 lim 个字符插一个零宽空格，制造折行点；不插则横向溢出被裁掉。
     */
    static CharSequence inl(String s) {
        return inl(s, 20);
    }

    static CharSequence inl(String s, int lim) {
        if (lim < 4) lim = 4;
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int i = 0;
        int run = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == '*' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                int e = s.indexOf("**", i + 2);
                if (e > i + 2) {
                    int st = sb.length();
                    sb.append(brk(s.substring(i + 2, e), lim));
                    sb.setSpan(new StyleSpan(Typeface.BOLD), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = e + 2;
                    run = 0;
                    continue;
                }
            }
            if (ch == '`') {
                int e = s.indexOf('`', i + 1);
                if (e > i + 1) { sb.append(brk(s.substring(i + 1, e), lim)); i = e + 1; run = 0; continue; }
            }
            if (ch == '[') {
                int e1 = s.indexOf("](", i);
                if (e1 > i) {
                    int e2 = s.indexOf(')', e1 + 2);
                    if (e2 > e1) {
                        String txt = s.substring(i + 1, e1);
                        String url = s.substring(e1 + 2, e2).trim();
                        int st = sb.length();
                        sb.append(brk(txt.length() == 0 ? url : txt, lim));
                        if (url.length() > 3 && !url.startsWith("#"))
                            sb.setSpan(new android.text.style.URLSpan(url), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        i = e2 + 1;
                        run = 0;
                        continue;
                    }
                }
            }
            sb.append(ch);
            if (ch > 32 && ch < 0x2E80 && ch != '\uFEFF') {
                if (++run >= lim) { sb.append('\u200B'); run = 0; }
            } else {
                run = 0;
            }
            i++;
        }
        return sb;
    }

    /** 无空格长串里插零宽空格：给排版引擎制造折行点，U+200B 不占宽度也不出字形 */
    static String brk(String s, int lim) {
        if (s == null) return "";
        if (lim < 4) lim = 4;
        StringBuilder sb = new StringBuilder(s.length() + 8);
        int run = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            sb.append(ch);
            if (ch > 32 && ch < 0x2E80) {
                if (++run >= lim) { sb.append('\u200B'); run = 0; }
            } else {
                run = 0;
            }
        }
        return sb.toString();
    }

    // ───────────────────────── 排版 ─────────────────────────

    static class Pager {
        final PdfDocument doc;
        final Typeface tf;
        final int W;
        final int CW;
        final String title;

        PdfDocument.Page page;
        Canvas c;
        int y;
        int pageNo;
        List<Lk> links = new ArrayList<Lk>();

        Pager(PdfDocument d, Typeface t, int w, String title) {
            doc = d; tf = t; W = w; CW = w - ML - MR; this.title = title;
        }

        StaticLayout lay(CharSequence cs, float size, int w, float mult, int color, boolean bold) {
            return lay(cs, size, w, mult, color, bold, Layout.Alignment.ALIGN_NORMAL);
        }

        StaticLayout lay(CharSequence cs, float size, int w, float mult, int color, boolean bold, Layout.Alignment al) {
            TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            tp.setTypeface(tf);
            tp.setTextSize(size);
            tp.setColor(color);
            tp.linkColor = th.linkColor;
            tp.setFakeBoldText(bold);
            if (w < 24) w = 24;
            StaticLayout.Builder b = StaticLayout.Builder.obtain(cs, 0, cs.length(), tp, w);
            b.setLineSpacing(0f, mult);
            b.setIncludePad(false);
            b.setAlignment(al);
            return b.build();
        }

        void newPage() {
            if (page != null) {
                footer();
                doc.finishPage(page);
            }
            pageNo++;
            page = doc.startPage(new PdfDocument.PageInfo.Builder(W, PH, pageNo).create());
            c = page.getCanvas();
            c.drawColor(Color.WHITE);
            y = MT;
            if (th.header && title != null && pageNo > 1) {
                Paint hp = new Paint(Paint.ANTI_ALIAS_FLAG);
                hp.setTypeface(tf);
                hp.setColor(th.headerColor);
                hp.setTextSize(th.headerSize);
                c.drawText(title, ML, 38, hp);
                Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG);
                lp.setColor(th.headerLine);
                lp.setStrokeWidth(0.7f);
                c.drawLine(ML, 46, W - MR, 46, lp);
            }
        }

        void footer() {
            if (!th.footer || c == null || pageNo <= 0) return;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setTypeface(tf);
            p.setColor(th.footerColor);
            p.setTextSize(th.footerSize);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText("- " + pageNo + " -", W / 2f, PH - 26, p);
        }

        void finish() {
            if (page != null) {
                footer();
                doc.finishPage(page);
                page = null;
            } else {
                newPage();
                footer();
                doc.finishPage(page);
                page = null;
            }
        }

        /** 按行切页地画一个 StaticLayout；顺带记下链接矩形（供 PDF /Annots） */
        void draw(StaticLayout lay, int indent) {
            int total = lay.getHeight();
            if (total <= 0) return;
            int top = 0;
            int left = ML + indent;
            int right = W - MR;
            if (page == null) newPage();
            while (top < total) {
                int bottom = PH - MB;
                int avail = bottom - y;
                if (avail < 12) { newPage(); continue; }
                int fit = top + avail;
                int line = lay.getLineForVertical(Math.min(total - 1, fit));
                int cut = lay.getLineBottom(line);
                while (cut > fit && line > 0) { line--; cut = lay.getLineBottom(line); }
                if (cut <= top) { newPage(); continue; }
                int y0 = y;
                c.save();
                c.clipRect(left, y, right, bottom);
                c.translate(left, y - top);
                lay.draw(c);
                c.restore();
                y += (cut - top);
                markLinks(lay, indent, y0, top, cut);
                top = cut;
                if (top < total) newPage();
            }
        }

        /** 记下这一段里链接占的行矩形（画布坐标，原点左上），并画细下划线 */
        void markLinks(StaticLayout lay, int indent, int y0, int top, int cut) {
            CharSequence cs = lay.getText();
            if (!(cs instanceof Spanned)) return;
            Spanned sp = (Spanned) cs;
            android.text.style.URLSpan[] us = sp.getSpans(0, sp.length(), android.text.style.URLSpan.class);
            if (us == null || us.length == 0) return;
            int left = ML + indent;
            int maxX = W - MR;
            Paint ul = new Paint(Paint.ANTI_ALIAS_FLAG);
            ul.setColor(th.linkColor);
            ul.setStrokeWidth(0.6f);
            int added = 0;
            for (int k = 0; k < us.length; k++) {
                String url = us[k].getURL();
                int s = sp.getSpanStart(us[k]);
                int e = sp.getSpanEnd(us[k]);
                if (e <= s || url == null || url.length() == 0) continue;
                int ls = lay.getLineForOffset(s);
                int le = lay.getLineForOffset(e - 1);
                for (int i = ls; i <= le; i++) {
                    if (lay.getLineTop(i) < top || lay.getLineBottom(i) > cut) continue;
                    float x1 = (i == ls) ? lay.getPrimaryHorizontal(s) : lay.getLineLeft(i);
                    float x2 = (i == le) ? lay.getPrimaryHorizontal(e) : lay.getLineRight(i);
                    if (x2 < x1) { float t2 = x1; x1 = x2; x2 = t2; }
                    x1 += left;
                    x2 += left;
                    if (x2 > maxX) x2 = maxX;
                    if (x2 - x1 < 2) continue;
                    int lt = y0 + lay.getLineTop(i) - top;
                    int lb = Math.min(y0 + lay.getLineBottom(i) - top, PH - MB);
                    Lk lk = new Lk();
                    lk.page = pageNo;
                    lk.x1 = x1; lk.y1 = lt; lk.x2 = x2; lk.y2 = lb;
                    lk.url = url;
                    links.add(lk);
                    added++;
                }
            }
        }

        /** 表格单元格里也有链接：按单元格原点记矩形（单元格不分页，整块记） */
        void markAt(StaticLayout lay, float left, float originY, float maxRight, int maxY) {
            CharSequence cs = lay.getText();
            if (!(cs instanceof Spanned)) return;
            Spanned sp = (Spanned) cs;
            android.text.style.URLSpan[] us = sp.getSpans(0, sp.length(), android.text.style.URLSpan.class);
            if (us == null || us.length == 0) return;
            Paint ul = new Paint(Paint.ANTI_ALIAS_FLAG);
            ul.setColor(th.linkColor);
            ul.setStrokeWidth(0.6f);
            for (int k = 0; k < us.length; k++) {
                String url = us[k].getURL();
                int s = sp.getSpanStart(us[k]);
                int e = sp.getSpanEnd(us[k]);
                if (e <= s || url == null || url.length() == 0) continue;
                int ls = lay.getLineForOffset(s);
                int le = lay.getLineForOffset(e - 1);
                for (int i = ls; i <= le; i++) {
                    float x1 = (i == ls) ? lay.getPrimaryHorizontal(s) : lay.getLineLeft(i);
                    float x2 = (i == le) ? lay.getPrimaryHorizontal(e) : lay.getLineRight(i);
                    if (x2 < x1) { float t2 = x1; x1 = x2; x2 = t2; }
                    x1 += left;
                    x2 += left;
                    if (x2 > maxRight) x2 = maxRight;
                    if (x2 - x1 < 2) continue;
                    int lt = Math.round(originY + lay.getLineTop(i));
                    int lb = Math.min(Math.round(originY + lay.getLineBottom(i)), maxY);
                    Lk lk = new Lk();
                    lk.page = pageNo;
                    lk.x1 = x1; lk.y1 = lt; lk.x2 = x2; lk.y2 = lb;
                    lk.url = url;
                    links.add(lk);
                }
            }
        }

        void layout(List<Block> bs) {
            for (int k = 0; k < bs.size(); k++) {
                Block b = bs.get(k);
                if (b.type == T_PB) { if (page != null) newPage(); continue; }
                if (b.type == T_H) {
                    int lv = b.level < 1 ? 1 : (b.level > 6 ? 6 : b.level);
                    float size = th.hSize[lv - 1];
                    int spTop = lv == 1 ? th.hSpTop1 : (lv == 2 ? th.hSpTop2 : th.hSpTopOther);
                    int spBot = lv <= 2 ? th.hSpBot1 : th.hSpBotOther;
                    boolean center = (lv == 1 && th.hCenter1) || (lv == 2 && th.hCenter2);
                    StaticLayout lay = lay(b.text, size, CW, th.hMult, th.hColor, true,
                        center ? Layout.Alignment.ALIGN_CENTER : Layout.Alignment.ALIGN_NORMAL);
                    int need = spTop + lay.getHeight() + spBot + th.orphan;
                    if (page != null && y + need > PH - MB) newPage();
                    y += spTop;
                    draw(lay, 0);
                    y += spBot;
                } else if (b.type == T_P) {
                    StaticLayout lay = lay(b.text, th.bodySize, CW, th.bodyMult, th.bodyColor, false);
                    y += th.pSpTop;
                    draw(lay, 0);
                    y += th.pSpBot;
                } else if (b.type == T_LI) {
                    int ind = th.liIndent + b.level * th.liIndentStep;
                    StaticLayout lay = lay(b.text, th.bodySize, CW - ind, th.bodyMult, th.bodyColor, false);
                    y += th.liSpTop;
                    draw(lay, ind);
                    y += th.liSpBot;
                } else if (b.type == T_QUOTE) {
                    StaticLayout lay = lay(b.text, th.quoteSize, CW - 18, th.quoteMult, th.quoteColor, false);
                    if (page != null && y + th.quoteGap * 2 + lay.getHeight() > PH - MB) newPage();
                    y += th.quoteGap;
                    int y0 = y;
                    draw(lay, 18);
                    Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
                    bp.setColor(th.quoteBar);
                    bp.setStrokeWidth(2.4f);
                    if (y > y0) c.drawLine(ML + 8, y0, ML + 8, Math.min(y, PH - MB), bp);
                    y += th.quoteGap;
                } else if (b.type == T_IMG) {
                    imgs(b.lines);
                } else if (b.type == T_CODE) {
                    code(b.lines);
                } else if (b.type == T_HR) {
                    if (page == null) newPage();
                    if (y + th.hrGap * 2 + 2 > PH - MB) newPage();
                    y += th.hrGap;
                    Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG);
                    lp.setColor(th.hrColor);
                    lp.setStrokeWidth(0.8f);
                    c.drawLine(ML, y, W - MR, y, lp);
                    y += th.hrGap;
                } else if (b.type == T_TABLE) {
                    table(b.lines, b.aligns);
                }
            }
        }

        void code(List<String> lines) {
            if (lines == null || lines.isEmpty()) return;
            float size = th.codeSize;
            int lh = Math.round(size * th.codeMult);
            int w = CW - th.codePad * 2;
            Paint bg = new Paint();
            bg.setColor(th.codeBg);
            y += th.codeGap;
            for (int i = 0; i < lines.size(); i++) {
                String s = lines.get(i);
                if (s.length() == 0) s = " ";
                StaticLayout lay = lay(brk(s, Math.max(8, (int) (w / (size * 0.56f)))), size, w, th.codeMult, th.codeColor, false);
                int h = Math.max(lh, lay.getHeight());
                if (page == null) newPage();
                if (y + h > PH - MB) newPage();
                c.drawRect(ML, y, W - MR, y + h, bg);
                c.save();
                c.translate(ML + th.codePad, y);
                lay.draw(c);
                c.restore();
                y += h;
            }
            y += th.codeGap;
        }

        /** 同一行的多张图并排（一张时与旧版一致，左对齐） */
        void imgs(List<String> srcs) {
            if (srcs == null || srcs.isEmpty()) return;
            int n = srcs.size();
            int gap = n > 1 ? th.imgGap : 0;
            int cellW = (CW - gap * (n - 1)) / n;
            int maxH = PH - MT - MB - 10;
            Bitmap[] bs = new Bitmap[n];
            int hMax = 0, totW = 0;
            for (int i = 0; i < n; i++) {
                bs[i] = loadScaled(srcs.get(i), cellW, maxH);
                if (bs[i] != null) {
                    if (bs[i].getHeight() > hMax) hMax = bs[i].getHeight();
                    totW += bs[i].getWidth();
                }
            }
            if (hMax == 0) {
                StringBuilder miss = new StringBuilder();
                for (int i = 0; i < n; i++) {
                    if (i > 0) miss.append("  ");
                    miss.append("[图片读不到] ").append(srcs.get(i));
                }
                StaticLayout lay = lay(miss.toString(), th.codeSize, CW, 1.3f, 0xFF999999, false);
                y += th.tableGap;
                draw(lay, 0);
                y += th.tableGap;
                return;
            }
            int x = ML;
            if (n > 1) x = ML + Math.max(0, (CW - (totW + gap * (n - 1))) / 2);
            if (page == null) newPage();
            if (y + hMax > PH - MB) newPage();
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            for (int i = 0; i < n; i++) {
                if (bs[i] == null) continue;
                c.drawBitmap(bs[i], null, new RectF(x, y, x + bs[i].getWidth(), y + bs[i].getHeight()), p);
                x += bs[i].getWidth() + gap;
                bs[i].recycle();
            }
            y += hMax + th.imgGap;
        }

        Bitmap loadScaled(String src, int maxW, int maxH) {
            try {
                File f = new File(src);
                if (!f.isAbsolute()) f = new File(baseDir, src);
                if (!f.isFile()) return null;
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                int w = o.outWidth, h = o.outHeight;
                if (w <= 0 || h <= 0) return null;
                double k = Math.min(1.0, Math.min((double) maxW / w, (double) maxH / h));
                int tw = Math.max(1, (int) Math.round(w * k));
                int hh = Math.max(1, (int) Math.round(h * k));
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                int sample = 1;
                while (w / sample > tw * 2) sample *= 2;
                o2.inSampleSize = sample;
                Bitmap full = BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
                if (full == null) return null;
                Bitmap out = Bitmap.createScaledBitmap(full, tw, hh, true);
                if (out != full) full.recycle();
                return out;
            } catch (Throwable t) { return null; }
        }

        /** 拆表格行：认反斜杠转义 \| ，反引号里的 | 不切 */
        static List<String> cells(String line) {
            List<String> out = new ArrayList<String>();
            String t = line.trim();
            if (t.startsWith("|")) t = t.substring(1);
            if (t.endsWith("|") && !t.endsWith("\\|")) t = t.substring(0, t.length() - 1);
            StringBuilder cur = new StringBuilder();
            boolean code = false;
            for (int i = 0; i < t.length(); i++) {
                char ch = t.charAt(i);
                if (ch == '\\' && i + 1 < t.length() && t.charAt(i + 1) == '|') { cur.append('|'); i++; continue; }
                if (ch == '`') { code = !code; cur.append(ch); continue; }
                if (ch == '|' && !code) { out.add(cur.toString().trim()); cur.setLength(0); continue; }
                cur.append(ch);
            }
            out.add(cur.toString().trim());
            return out;
        }

        void table(List<String> rows, int[] aligns) {
            if (rows == null || rows.isEmpty()) return;
            List<List<String>> cl = new ArrayList<List<String>>();
            int cols = 0;
            for (int i = 0; i < rows.size(); i++) {
                List<String> cs = cells(rows.get(i));
                cl.add(cs);
                if (cs.size() > cols) cols = cs.size();
            }
            if (cols == 0) return;

            int[] wgt = new int[cols];
            for (int j = 0; j < cols; j++) wgt[j] = 4;
            for (int i = 0; i < cl.size(); i++) {
                List<String> cs = cl.get(i);
                for (int j = 0; j < cols; j++) {
                    String v = j < cs.size() ? cs.get(j) : "";
                    int w = 0;
                    for (int k = 0; k < v.length(); k++) w += (v.charAt(k) > 0x2E80 ? 2 : 1);
                    if (w > wgt[j]) wgt[j] = w;
                }
            }
            int minW = Math.max(th.tableMinW, CW / (cols * 2));
            int[] sw = new int[cols];
            long sq = 0;
            for (int j = 0; j < cols; j++) { sw[j] = (int) (Math.sqrt(wgt[j]) * 100); sq += sw[j]; }
            int[] cw = new int[cols];
            int tot = 0;
            for (int j = 0; j < cols; j++) {
                cw[j] = (int) (CW * (long) sw[j] / sq);
                if (cw[j] < minW) cw[j] = minW;
                tot += cw[j];
            }
            if (tot > CW) {
                for (int j = 0; j < cols; j++) cw[j] = (int) ((long) cw[j] * CW / tot);
                tot = 0;
                for (int j = 0; j < cols; j++) tot += cw[j];
            }

            y += th.tableGap;
            if (page != null && y + th.tableMinStart > PH - MB) newPage();
            for (int i = 0; i < cl.size(); i++) {
                boolean moved = row(cl.get(i), cw, tot, i == 0, aligns);
                if (moved && i > 0) row(cl.get(0), cw, tot, true, aligns);
            }
            y += th.tableGapBot;
        }

        boolean row(List<String> cs, int[] cw, int totW, boolean head, int[] aligns) {
            int cols = cw.length;
            float size = th.tableSize;
            StaticLayout[] lays = new StaticLayout[cols];
            int h = 0;
            for (int j = 0; j < cols; j++) {
                String v = j < cs.size() ? cs.get(j) : "";
                v = v.replace("<br />", "\n").replace("<br/>", "\n").replace("<br>", "\n");
                int inner = Math.max(24, cw[j] - th.tablePadX * 2);
                int lim = Math.max(8, (int) (inner / (size * 0.56f)));
                int a = (aligns != null && j < aligns.length) ? aligns[j] : 0;
                Layout.Alignment al = a == 1 ? Layout.Alignment.ALIGN_CENTER
                        : (a == 2 ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_NORMAL);
                lays[j] = lay(inl(v, lim), size, inner, th.tableMult, th.tableColor, head, al);
                if (lays[j].getHeight() > h) h = lays[j].getHeight();
            }
            h += th.tablePadY * 2 - 2;
            if (page == null) newPage();
            boolean moved = false;
            if (y + h > PH - MB) { newPage(); moved = true; }
            Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
            bp.setColor(th.tableLine);
            bp.setStrokeWidth(0.6f);
            Paint bg = new Paint();
            bg.setColor(th.tableHeadBg);
            int x = ML;
            for (int j = 0; j < cols; j++) {
                if (head) c.drawRect(x, y, x + cw[j], y + h, bg);
                c.save();
                c.clipRect(x, y, x + cw[j], y + h);
                c.translate(x + th.tablePadX, y + th.tablePadY - 2);
                lays[j].draw(c);
                c.restore();
                markAt(lays[j], x + th.tablePadX, y + th.tablePadY - 2, x + cw[j], y + h);
                x += cw[j];
            }
            c.drawLine(ML, y, ML + totW, y, bp);
            c.drawLine(ML, y + h, ML + totW, y + h, bp);
            int xx = ML;
            c.drawLine(xx, y, xx, y + h, bp);
            for (int j = 0; j < cols; j++) { xx += cw[j]; c.drawLine(xx, y, xx, y + h, bp); }
            y += h;
            return moved;
        }
    }

    // ────────────── PDF 链接注释（PdfDocument 没有 /Annot 接口，只能事后追加） ──────────────

    /** 一个链接：页码（从 1 起）＋画布坐标矩形（原点左上）＋地址 */
    static class Lk {
        int page;
        float x1, y1, x2, y2;
        String url;
    }

    /**
     * 给 PdfDocument 写出的 PDF 追加 /Annot /Link /URI。走「增量更新」：
     * 原文件一个字节不动，只在尾部追加「改写的页对象＋注释对象＋新 xref＋trailer(/Prev=旧 xref)」。
     * 返回挂上的链接数；任何一步不对就返回 0（宁可没有链接，不能弄坏文件）。
     */
    static int addLinks(File f, List<Lk> links) {
        if (f == null || !f.isFile() || links == null || links.isEmpty()) return 0;
        try {
            RandomAccessFile raf = new RandomAccessFile(f, "rw");
            long len = raf.length();
            byte[] buf = new byte[(int) len];
            raf.readFully(buf);
            String s = new String(buf, "ISO-8859-1");

            int sx = s.lastIndexOf("startxref");
            if (sx < 0) { raf.close(); return 0; }
            String sn = s.substring(sx + 9).trim();
            int e0 = 0;
            while (e0 < sn.length() && sn.charAt(e0) >= '0' && sn.charAt(e0) <= '9') e0++;
            if (e0 == 0) { raf.close(); return 0; }
            long prevXref = Long.parseLong(sn.substring(0, e0));

            int rt = s.lastIndexOf("/Root");
            if (rt < 0) { raf.close(); return 0; }
            String rootRef = objRef(s, rt + 5);
            if (rootRef == null) { raf.close(); return 0; }

            // 切对象
            List<Integer> nums = new ArrayList<Integer>();
            List<String> bodies = new ArrayList<String>();
            Matcher m = Pattern.compile("(?m)^(\\d+)\\s+0\\s+obj").matcher(s);
            while (m.find()) {
                int bo = m.end();
                int eo = s.indexOf("endobj", bo);
                if (eo < 0) continue;
                nums.add(Integer.parseInt(m.group(1)));
                bodies.add(s.substring(bo, eo));
            }
            // 页对象：/Type /Page（不是 /Pages）；按 /Contents 对象号排序即页序
            List<Integer> pIdx = new ArrayList<Integer>();
            List<Integer> pCon = new ArrayList<Integer>();
            for (int i = 0; i < bodies.size(); i++) {
                String b = bodies.get(i);
                if (!Pattern.compile("/Type\\s*/Page[^s]").matcher(b).find()) continue;
                Matcher cm = Pattern.compile("/Contents\\s+(\\d+)\\s+0\\s+R").matcher(b);
                pIdx.add(i);
                pCon.add(cm.find() ? Integer.parseInt(cm.group(1)) : 900000 + i);
            }
            if (pIdx.isEmpty()) { raf.close(); return 0; }
            Integer[] order = new Integer[pIdx.size()];
            for (int i = 0; i < order.length; i++) order[i] = i;
            for (int i = 0; i < order.length; i++)
                for (int j = i + 1; j < order.length; j++)
                    if (pCon.get(order[j]) < pCon.get(order[i])) { Integer t2 = order[i]; order[i] = order[j]; order[j] = t2; }

            int maxObj = 0;
            for (int i = 0; i < nums.size(); i++) if (nums.get(i) > maxObj) maxObj = nums.get(i);
            int next = maxObj + 1;

            StringBuilder add = new StringBuilder();
            List<long[]> xr = new ArrayList<long[]>();   // {对象号, 文件偏移}
            int total = 0;
            long off = len;
            for (int oi = 0; oi < order.length; oi++) {
                int pageNo = oi + 1;
                StringBuilder refs = new StringBuilder();
                for (int i = 0; i < links.size(); i++) {
                    Lk lk = links.get(i);
                    if (lk.page != pageNo) continue;
                    // 注释对象
                    float px1 = lk.x1, px2 = lk.x2;
                    float py1 = PH - lk.y2, py2 = PH - lk.y1;
                    String ob = "<</Type/Annot/Subtype/Link/Rect["
                            + f2(px1) + " " + f2(py1) + " " + f2(px2) + " " + f2(py2)
                            + "]/Border[0 0 0]/F 4/A<</S/URI/URI" + pdfStr(lk.url) + ">>>>";
                    String txt = "\n" + next + " 0 obj\n" + ob + "\nendobj\n";
                    xr.add(new long[]{next, off});
                    add.append(txt);
                    off += txt.length();
                    refs.append(next).append(" 0 R ");
                    next++;
                    total++;
                }
                if (refs.length() == 0) continue;
                // 改写这一页：把 /Annots 插在页字典最后一个 >> 之前
                String body = bodies.get(pIdx.get(order[oi]));
                int lastEnd = body.lastIndexOf(">>");
                if (lastEnd < 0) continue;
                String nb = body.substring(0, lastEnd) + "/Annots[" + refs.toString().trim() + "]" + body.substring(lastEnd);
                String txt = "\n" + nums.get(pIdx.get(order[oi])) + " 0 obj\n" + nb + "\nendobj\n";
                xr.add(new long[]{nums.get(pIdx.get(order[oi])), off});
                add.append(txt);
                off += txt.length();
            }
            if (total == 0) { raf.close(); return 0; }

            // 新 xref（只列改动的对象，按对象号升序；连续号合并成一节）
            long xrefOff = len + add.length();
            StringBuilder xs = new StringBuilder();
            xs.append("xref\n");
            int i2 = 0;
            List<long[]> sorted = new ArrayList<long[]>();
            int cnt = xr.size();
            for (int n = 0; n < cnt; n++) {
                long[] best = null;
                for (int i = 0; i < xr.size(); i++) {
                    long[] e = xr.get(i);
                    if (best == null || e[0] < best[0]) best = e;
                }
                sorted.add(best);
                xr.remove(best);
            }
            while (i2 < sorted.size()) {
                int j = i2;
                while (j + 1 < sorted.size() && sorted.get(j + 1)[0] == sorted.get(j)[0] + 1) j++;
                xs.append(sorted.get(i2)[0]).append(" ").append(j - i2 + 1).append("\n");
                for (int k = i2; k <= j; k++) {
                    String o = String.valueOf(sorted.get(k)[1]);
                    while (o.length() < 10) o = "0" + o;
                    xs.append(o).append(" 00000 n \n");
                }
                i2 = j + 1;
            }
            xs.append("trailer\n<</Size ").append(next).append("/Prev ").append(prevXref)
              .append("/Root ").append(rootRef).append(" 0 R>>\nstartxref\n")
              .append(xrefOff).append("\n%%EOF\n");

            raf.seek(len);
            raf.write(add.toString().getBytes("ISO-8859-1"));
            raf.write(xs.toString().getBytes("ISO-8859-1"));
            raf.close();
            return total;
        } catch (Throwable e) {
            return 0;
        }
    }

    /** 从 "/Root 56 0 R" 这类片段里取出 "56" */
    static String objRef(String s, int from) {
        int i = from;
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\n' || s.charAt(i) == '\r')) i++;
        int j = i;
        while (j < s.length() && s.charAt(j) >= '0' && s.charAt(j) <= '9') j++;
        if (j == i) return null;
        return s.substring(i, j);
    }

    /** 小数转两位去掉多余零 */
    static String f2(float v) {
        String x = String.format(Locale.ROOT, "%.2f", v);
        while (x.indexOf('.') > 0 && (x.endsWith("0") || x.endsWith("."))) x = x.substring(0, x.length() - 1);
        return x;
    }

    /** URL 写成 PDF 字面字符串，转义 \ ( ) */
    static String pdfStr(String u) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < u.length(); i++) {
            char ch = u.charAt(i);
            if (ch == '\\' || ch == '(' || ch == ')') sb.append('\\');
            if (ch < 32 || ch > 126) { sb.append(String.format(Locale.ROOT, "\\%03o", (int) ch & 0xFF)); continue; }
            sb.append(ch);
        }
        sb.append(')');
        return sb.toString();
    }
}
