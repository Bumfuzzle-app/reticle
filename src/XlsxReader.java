package app.reticle;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * M1 表格数据面：xlsx 只读解析（零第三方依赖）。
 *
 * · 容器用 java.util.zip，XML 用 android.util.Xml 的 XmlPullParser 流式读。
 * · 一次只留需要的行窗口（keepFrom..keepTo），29,120 个单元格不整表进内存。
 * · 公式只信缓存值 <v>；没有缓存就返回公式原文并标「不可计算」，不做公式引擎。
 * · 日期按 numFmtId 判断，不按内容猜（该报表 339 个 cellXfs，格式极杂）。
 */
public class XlsxReader {

    public static final int T_EMPTY = 0, T_TEXT = 1, T_NUM = 2, T_DATE = 3, T_BOOL = 4, T_ERR = 5, T_FORMULA = 6;

    /** 一个单元格：type 决定语义，text 是给人看的文本，raw 是原始数值字符串。 */
    public static class Cell {
        public int type = T_EMPTY;
        public String text = "";
        public String raw = "";
        public int fmt = 0;
        public boolean formula = false;
    }

    public static class SheetRef {
        public int index;
        public String name = "";
        public String path = "";
        public boolean hidden = false;
    }

    /** 工作簿级元数据：工作表清单、共享字符串、样式映射、外部链接等提示。 */
    public static class Book {
        public File file;
        public long size;
        public List<SheetRef> sheets = new ArrayList<SheetRef>();
        public String[] sst = new String[0];
        public int[] xfFmt = new int[0];
        public Map<Integer, String> customFmt = new HashMap<Integer, String>();
        public boolean date1904 = false;
        public int definedNames = 0;
        public List<String> extra = new ArrayList<String>();
    }

    /** 每列统计：类型投票、空值数、去重数（封顶 500）、表头文字。 */
    public static class ColStat {
        public int col;
        public String header = "";
        public int nonNull, nulls, distinct;
        public boolean capped = false;
        public int nDate, nNum, nText, nBool, nErr;
        public String type = "empty";
        public Set<String> set = new HashSet<String>();
    }

    /** 一次扫描的结果：元数据 ＋ 行窗口内的单元格（Map 行号 → 该行单元格）。 */
    public static class Scan {
        public int rows, cols;
        public String dim = "";
        public int cellCount, formulaCount, formulaNoCache;
        public int hiddenRowCount, hiddenColCount;
        public List<Integer> hiddenRows = new ArrayList<Integer>();
        public Set<Integer> hiddenSet = new HashSet<Integer>();
        public List<String> merges = new ArrayList<String>();
        public int mergeCount;
        public Map<Integer, List<Cell>> grid = new LinkedHashMap<Integer, List<Cell>>();
        public List<ColStat> stats = new ArrayList<ColStat>();
    }

    // ═══════════ 打开工作簿（元数据一次读完；工作表按需逐次流式扫） ═══════════

    public static Book open(File f) throws Exception {
        Book b = new Book();
        b.file = f;
        b.size = f.length();
        ZipFile zf = new ZipFile(f);
        try {
            Map<String, String> rels = new HashMap<String, String>();
            readRels(zf, "xl/_rels/workbook.xml.rels", rels);
            InputStream in = entry(zf, "xl/workbook.xml");
            if (in == null) throw new Exception("缺 xl/workbook.xml");
            List<String> names = new ArrayList<String>();
            List<String> rids = new ArrayList<String>();
            List<Boolean> hidden = new ArrayList<Boolean>();
            XmlPullParser p = newPull();
            p.setInput(in, "UTF-8");
            int ev = p.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String t = local(p.getName());
                    if ("sheet".equals(t)) {
                        names.add(nz(attr(p, "name")));
                        String rid = attr(p, "r:id");
                        if (rid == null) rid = attr(p, "id");
                        rids.add(nz(rid));
                        String st = nz(attr(p, "state"));
                        hidden.add("hidden".equals(st) || "veryHidden".equals(st));
                    } else if ("workbookPr".equals(t)) {
                        String d = nz(attr(p, "date1904"));
                        b.date1904 = "1".equals(d) || "true".equalsIgnoreCase(d);
                    } else if ("definedName".equals(t)) {
                        b.definedNames++;
                    }
                }
                ev = p.next();
            }
            in.close();
            for (int i = 0; i < names.size(); i++) {
                SheetRef s = new SheetRef();
                s.index = i + 1;
                s.name = names.get(i);
                s.hidden = hidden.get(i);
                s.path = target(rels.get(rids.get(i)));
                b.sheets.add(s);
            }
            if (b.sheets.isEmpty()) throw new Exception("工作簿里没有工作表");
            readStyles(zf, b);
            readSst(zf, b);
            boolean ext = false, cm = false, dr = false;
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName();
                if (n.startsWith("xl/externalLinks/") && n.endsWith(".xml")) ext = true;
                if (n.startsWith("xl/comments") && n.endsWith(".xml")) cm = true;
                if (n.startsWith("xl/drawings/") && n.endsWith(".xml")) dr = true;
            }
            if (ext) b.extra.add("有外部链接，公式缓存值可能过期");
            if (cm) b.extra.add("有批注，read 不返回批注");
            if (dr) b.extra.add("有图形对象，read 只返回单元格");
            if (b.date1904) b.extra.add("用的是 1904 日期系统，日期已按它换算");
            if (b.definedNames > 0) b.extra.add("有 " + b.definedNames + " 个定义名称（不是工作表）");
        } finally {
            try { zf.close(); } catch (Throwable t) { }
        }
        return b;
    }

    private static void readRels(ZipFile zf, String path, Map<String, String> out) throws Exception {
        InputStream in = entry(zf, path);
        if (in == null) return;
        XmlPullParser p = newPull();
        p.setInput(in, "UTF-8");
        int ev = p.getEventType();
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                String t = local(p.getName());
                if ("Relationship".equals(t)) {
                    String id = nz(attr(p, "Id"));
                    String tg = nz(attr(p, "Target"));
                    if (id.length() > 0 && tg.length() > 0) out.put(id, tg);
                }
            }
            ev = p.next();
        }
        in.close();
    }

    private static void readStyles(ZipFile zf, Book b) throws Exception {
        InputStream in = entry(zf, "xl/styles.xml");
        if (in == null) return;
        List<Integer> xfs = new ArrayList<Integer>();
        XmlPullParser p = newPull();
        p.setInput(in, "UTF-8");
        int ev = p.getEventType();
        boolean inXfs = false;
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                String t = local(p.getName());
                if ("numFmt".equals(t)) {
                    int id = num(attr(p, "numFmtId"), -1);
                    if (id >= 0) b.customFmt.put(id, nz(attr(p, "formatCode")));
                } else if ("cellXfs".equals(t)) {
                    inXfs = true;
                } else if ("xf".equals(t) && inXfs) {
                    xfs.add(num(attr(p, "numFmtId"), 0));
                }
            } else if (ev == XmlPullParser.END_TAG) {
                if ("cellXfs".equals(local(p.getName()))) inXfs = false;
            }
            ev = p.next();
        }
        in.close();
        b.xfFmt = new int[xfs.size()];
        for (int i = 0; i < xfs.size(); i++) b.xfFmt[i] = xfs.get(i);
    }

    /** 共享字符串：<si> 里可能有多段 <t>（富文本），全部拼起来。 */
    private static void readSst(ZipFile zf, Book b) throws Exception {
        InputStream in = entry(zf, "xl/sharedStrings.xml");
        if (in == null) return;
        List<String> list = new ArrayList<String>();
        XmlPullParser p = newPull();
        p.setInput(in, "UTF-8");
        int ev = p.getEventType();
        boolean inSi = false, inT = false;
        StringBuilder sb = null;
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                String t = local(p.getName());
                if ("si".equals(t)) { inSi = true; sb = new StringBuilder(); }
                else if ("t".equals(t) && inSi) inT = true;
            } else if (ev == XmlPullParser.TEXT) {
                if (inSi && inT && sb != null) sb.append(p.getText());
            } else if (ev == XmlPullParser.END_TAG) {
                String t = local(p.getName());
                if ("t".equals(t)) inT = false;
                else if ("si".equals(t)) { inSi = false; list.add(sb == null ? "" : sb.toString()); }
            }
            ev = p.next();
        }
        in.close();
        b.sst = list.toArray(new String[list.size()]);
    }

    // ═══════════ 流式扫一张表 ═══════════

    /**
     * 扫第 idx 张表（1 起）：返回元数据 ＋ keepFrom..keepTo 行窗口里的单元格。
     * wantStats 只统计第 hdrRow 行以下，避免表头把列类型带成 mixed。
     */
    /** 流式回调：导入 SQLite 时边扫边写，不保留整表。 */
    public interface Sink {
        void cell(int row, int col, Cell c);
    }

    public static Scan scan(Book b, int idx, int keepFrom, int keepTo, boolean wantStats, int hdrRow) throws Exception {
        return scan(b, idx, keepFrom, keepTo, wantStats, hdrRow, null);
    }

    public static Scan scan(Book b, int idx, int keepFrom, int keepTo, boolean wantStats, int hdrRow, Sink sink) throws Exception {
        Scan s = new Scan();
        if (idx < 1 || idx > b.sheets.size()) throw new Exception("没有第 " + idx + " 个工作表");
        SheetRef ref = b.sheets.get(idx - 1);
        int maxRow = 0, maxCol = 0;
        ZipFile zf = new ZipFile(b.file);
        try {
            InputStream in = entry(zf, ref.path);
            if (in == null) throw new Exception("工作表文件缺失 " + ref.path);
            XmlPullParser p = newPull();
            p.setInput(in, "UTF-8");
            int ev = p.getEventType();
            int curRow = 0, curCol = 0;
            boolean inCell = false, inF = false, inV = false, inIs = false, inT = false;
            String cType = "", cStyle = "";
            StringBuilder fBuf = null, vBuf = null, tBuf = null;
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String t = local(p.getName());
                    if ("dimension".equals(t)) {
                        String d = nz(attr(p, "ref"));
                        if (d.length() > 0) s.dim = d;
                    } else if ("col".equals(t)) {
                        if (isTrue(attr(p, "hidden"))) {
                            int a = num(attr(p, "min"), 0), z = num(attr(p, "max"), a);
                            if (a > 0 && z >= a) s.hiddenColCount += (z - a + 1);
                        }
                    } else if ("row".equals(t)) {
                        String r = attr(p, "r");
                        curRow = (r == null) ? curRow + 1 : num(r, curRow + 1);
                        if (isTrue(attr(p, "hidden"))) {
                            s.hiddenRowCount++;
                            if (s.hiddenRows.size() < 40) s.hiddenRows.add(curRow);
                            s.hiddenSet.add(curRow);
                        }
                    } else if ("c".equals(t)) {
                        inCell = true;
                        cType = nz(attr(p, "t"));
                        cStyle = nz(attr(p, "s"));
                        curCol = colOf(attr(p, "r"), curCol + 1);
                        fBuf = null; vBuf = null; tBuf = null;
                        inF = false; inV = false; inIs = false; inT = false;
                    } else if ("f".equals(t) && inCell) {
                        inF = true; fBuf = new StringBuilder();
                    } else if ("v".equals(t) && inCell) {
                        inV = true; vBuf = new StringBuilder();
                    } else if ("is".equals(t) && inCell) {
                        inIs = true;
                    } else if ("t".equals(t) && inCell && inIs) {
                        inT = true;
                        if (tBuf == null) tBuf = new StringBuilder();
                    } else if ("mergeCell".equals(t)) {
                        String mr = attr(p, "ref");
                        if (mr != null) {
                            s.mergeCount++;
                            if (s.merges.size() < 30) s.merges.add(mr);
                        }
                    }
                } else if (ev == XmlPullParser.TEXT) {
                    if (inF && fBuf != null) fBuf.append(p.getText());
                    else if (inV && vBuf != null) vBuf.append(p.getText());
                    else if (inT && tBuf != null) tBuf.append(p.getText());
                } else if (ev == XmlPullParser.END_TAG) {
                    String t = local(p.getName());
                    if ("f".equals(t)) inF = false;
                    else if ("v".equals(t)) inV = false;
                    else if ("is".equals(t)) { inIs = false; inT = false; }
                    else if ("t".equals(t)) inT = false;
                    else if ("c".equals(t)) {
                        if (inCell) {
                            String vs = vBuf == null ? null : vBuf.toString();
                            String fs = fBuf == null ? null : fBuf.toString();
                            String is = tBuf == null ? null : tBuf.toString();
                            Cell c = makeCell(b, cType, cStyle, vs, fs, is);
                            int row = curRow, col = curCol;
                            if (c != null && row > maxRow) maxRow = row;
                            if (c != null && col > maxCol) maxCol = col;
                            if (c != null) {
                                s.cellCount++;
                                if (sink != null) sink.cell(row, col, c);
                                if (c.formula) {
                                    s.formulaCount++;
                                    if (c.type == T_FORMULA) s.formulaNoCache++;
                                }
                                if (row >= keepFrom && row <= keepTo && col > 0) {
                                    List<Cell> line = s.grid.get(row);
                                    if (line == null) { line = new ArrayList<Cell>(); s.grid.put(row, line); }
                                    while (line.size() < col - 1) line.add(null);
                                    if (line.size() == col - 1) line.add(c);
                                    else line.set(col - 1, c);
                                }
                                if (wantStats) addStat(s, row, col, c, hdrRow);
                            }
                        }
                        inCell = false;
                    }
                }
                ev = p.next();
            }
            in.close();
        } finally {
            try { zf.close(); } catch (Throwable t) { }
        }
        s.rows = maxRow;
        s.cols = maxCol;
        if (wantStats) finishStats(s, hdrRow);
        return s;
    }

    // ═══════════ 单元格 → 文本（类型、日期、百分比、公式） ═══════════

    private static Cell makeCell(Book b, String t, String sAttr, String v, String f, String inline) {
        boolean hasF = (f != null);
        boolean hasV = (v != null && v.trim().length() > 0);
        if (!hasV && !hasF && inline == null) return null;
        Cell c = new Cell();
        c.fmt = fmtOf(b, sAttr);
        c.formula = hasF;
        if ("s".equals(t)) {
            int i = num(v, -1);
            if (hasV && i >= 0 && i < b.sst.length) c.text = b.sst[i];
            c.type = T_TEXT;
            if (!hasV) { c.type = T_FORMULA; c.text = "=" + trim(f); }
            return c;
        }
        if ("inlineStr".equals(t)) {
            c.type = T_TEXT;
            c.text = inline == null ? "" : inline;
            return c;
        }
        if ("str".equals(t)) {
            c.type = T_TEXT;
            c.text = hasV ? v : "=" + trim(f);
            return c;
        }
        if ("b".equals(t)) {
            c.type = T_BOOL;
            c.text = ("1".equals(trim(v)) || "true".equalsIgnoreCase(trim(v))) ? "TRUE" : "FALSE";
            return c;
        }
        if ("e".equals(t)) {
            c.type = T_ERR;
            c.text = hasV ? trim(v) : "#ERR";
            return c;
        }
        if ("d".equals(t)) {
            c.type = T_DATE;
            c.text = trim(v);
            return c;
        }
        if (!hasV) {
            c.type = T_FORMULA;
            c.text = "=" + trim(f);
            return c;
        }
        String raw = trim(v);
        double d;
        try { d = Double.parseDouble(raw); } catch (Throwable ex) { c.type = T_TEXT; c.text = raw; return c; }
        c.raw = raw;
        if (isDateFmt(b, c.fmt)) {
            c.type = T_DATE;
            c.text = formatDate(b, d);
        } else if (isPercentFmt(b, c.fmt)) {
            c.type = T_NUM;
            c.text = pct(d);
        } else {
            c.type = T_NUM;
            c.text = raw;
        }
        return c;
    }

    private static int fmtOf(Book b, String styleIdx) {
        int i = num(styleIdx, 0);
        if (i < 0 || i >= b.xfFmt.length) return 0;
        return b.xfFmt[i];
    }

    /** 内置日期格式 ＋ 自定义格式里的 y/d/h/s 判断；引号与方括号里的字面量先剥掉。 */
    public static boolean isDateFmt(Book b, int fmt) {
        if ((fmt >= 14 && fmt <= 22) || (fmt >= 27 && fmt <= 36) || (fmt >= 45 && fmt <= 47) || (fmt >= 50 && fmt <= 58)) return true;
        String code = b.customFmt.get(fmt);
        if (code == null) return false;
        String s = stripLiterals(code).toLowerCase(Locale.ROOT);
        if (s.indexOf('y') >= 0 || s.indexOf('d') >= 0) return true;
        if (s.indexOf('h') >= 0) return true;
        if (s.indexOf('m') >= 0 && s.indexOf('s') >= 0) return true;
        return false;
    }

    public static boolean isPercentFmt(Book b, int fmt) {
        if (fmt == 9 || fmt == 10) return true;
        String code = b.customFmt.get(fmt);
        return code != null && stripLiterals(code).indexOf('%') >= 0;
    }

    private static String stripLiterals(String code) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < code.length(); i++) {
            char ch = code.charAt(i);
            if (ch == '\\') { i++; continue; }
            if (ch == '"') {
                int j = code.indexOf('"', i + 1);
                if (j < 0) break;
                i = j;
                continue;
            }
            if (ch == '[') {
                int j = code.indexOf(']', i + 1);
                if (j > 0) { i = j; continue; }
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    /** 序列号 → yyyy-MM-dd（带时间就补 HH:mm）；1900 系统那条假闰日单处方一次。 */
    public static String formatDate(Book b, double serial) {
        double base = b.date1904 ? 24107.0 : 25569.0;
        if (!b.date1904 && serial < 61.0) serial += 1.0;
        double frac = serial - Math.floor(serial);
        long ms = Math.round((serial - base) * 86400000.0);
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT);
        c.setTimeInMillis(ms);
        String s = String.format(Locale.ROOT, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        if (frac > 0.00001) s += String.format(Locale.ROOT, " %02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
        return s;
    }

    private static String pct(double d) {
        double v = d * 100.0;
        if (Math.abs(v - Math.round(v)) < 1e-9) return String.valueOf(Math.round(v)) + "%";
        String s = String.format(Locale.ROOT, "%.2f", v);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s + "%";
    }

    // ═══════════ 列统计 ═══════════

    static void addStat(Scan s, int row, int col, Cell c, int hdrRow) {
        while (s.stats.size() < col) {
            ColStat cs = new ColStat();
            cs.col = s.stats.size() + 1;
            s.stats.add(cs);
        }
        ColStat cs = s.stats.get(col - 1);
        if (row == hdrRow) {
            if (cs.header.length() == 0 && c.text.length() > 0) cs.header = c.text;
            return;
        }
        cs.nonNull++;
        if (c.type == T_DATE) cs.nDate++;
        else if (c.type == T_NUM) cs.nNum++;
        else if (c.type == T_TEXT) cs.nText++;
        else if (c.type == T_BOOL) cs.nBool++;
        else if (c.type == T_ERR || c.type == T_FORMULA) cs.nErr++;
        if (!cs.capped) {
            cs.set.add(c.text);
            if (cs.set.size() >= 500) { cs.capped = true; cs.set.clear(); }
        }
        cs.distinct = cs.capped ? 500 : cs.set.size();
    }

    static void finishStats(Scan s, int hdrRow) {
        int total = Math.max(0, s.rows - Math.max(1, hdrRow));
        for (ColStat cs : s.stats) {
            cs.nulls = Math.max(0, total - cs.nonNull);
            int kinds = 0;
            if (cs.nDate > 0) kinds++;
            if (cs.nNum > 0) kinds++;
            if (cs.nText > 0) kinds++;
            if (cs.nBool > 0) kinds++;
            if (cs.nErr > 0) kinds++;
            if (kinds == 0) cs.type = "empty";
            else if (kinds > 1) cs.type = "mixed";
            else if (cs.nDate > 0) cs.type = "date";
            else if (cs.nNum > 0) cs.type = "number";
            else if (cs.nText > 0) cs.type = "text";
            else if (cs.nBool > 0) cs.type = "boolean";
            else cs.type = "error";
        }
    }

    // ═══════════ 小工具 ═══════════

    private static XmlPullParser newPull() throws Exception {
        XmlPullParser p = Xml.newPullParser();
        try { p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false); } catch (Throwable t) { }
        return p;
    }

    private static InputStream entry(ZipFile zf, String path) {
        try {
            ZipEntry e = zf.getEntry(path);
            if (e == null) return null;
            return zf.getInputStream(e);
        } catch (Throwable t) { return null; }
    }

    private static String target(String t) {
        if (t == null) return "";
        if (t.startsWith("/")) return t.substring(1);
        if (t.startsWith("xl/")) return t;
        return "xl/" + t;
    }

    private static String local(String name) {
        if (name == null) return "";
        int i = name.indexOf(':');
        return i < 0 ? name : name.substring(i + 1);
    }

    private static String attr(XmlPullParser p, String name) {
        try {
            String v = p.getAttributeValue(null, name);
            if (v != null) return v;
            for (int i = 0; i < p.getAttributeCount(); i++) {
                if (name.equals(local(p.getAttributeName(i)))) return p.getAttributeValue(i);
            }
        } catch (Throwable t) { }
        return null;
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static String trim(String s) { return s == null ? "" : s.trim(); }
    private static boolean isTrue(String s) { return "1".equals(s) || "true".equalsIgnoreCase(s); }

    private static int num(String s, int dflt) {
        if (s == null) return dflt;
        try { return Integer.parseInt(s.trim()); } catch (Throwable t) { return dflt; }
    }

    /** "AF30" → {30, 32}；认不出给 {0,0}。 */
    public static int[] parseRef(String ref) {
        int[] out = new int[]{0, 0};
        if (ref == null) return out;
        String s = ref.trim().toUpperCase(Locale.ROOT);
        int col = 0, row = 0, i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch >= 'A' && ch <= 'Z') { col = col * 26 + (ch - 'A' + 1); i++; }
            else break;
        }
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') { row = row * 10 + (ch - '0'); i++; }
            else break;
        }
        out[0] = row;
        out[1] = col;
        return out;
    }

    private static int colOf(String ref, int dflt) {
        int[] rc = parseRef(ref);
        return rc[1] > 0 ? rc[1] : dflt;
    }

    /** 1 → A，27 → AA。 */
    public static String colName(int n) {
        if (n <= 0) return "?";
        StringBuilder sb = new StringBuilder();
        while (n > 0) {
            int r = (n - 1) % 26;
            sb.insert(0, (char) ('A' + r));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }
}
