package app.reticle;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * M1 表格数据面：sheet 工具（action = info / read / find）。
 *
 * 设计口径（见 交接-表格数据面-20261103.md §2.3）：
 * · 不 dump 全表：read 按 range 分页，默认 max_rows=200。
 * · 表头不自动猜：info 给前 8 行原始网格，read 的 header_row 只影响列名映射。
 * · 隐藏行必须报告：默认照常返回并注明，skip_hidden=1 才排除。
 * · 空 / 0 / 空字符串三态：空单元格是空串，0 就是 0。
 */
public class SheetTool {

    public static String run(Context c, JSONObject a) {
        String act = Csv.lower(a.optString("action", "info")).trim();
        if (act.length() == 0) act = "info";
        try {
            if ("find".equals(act)) return find(a.optString("name", ""), clamp(a.optInt("max", 10), 1, 30));
            if (!"info".equals(act) && !"read".equals(act) && !"query".equals(act)
                    && !"export".equals(act) && !"preview".equals(act)) {
                return String.format(Locale.ROOT, Words.RP_SHEET_BADACT, act);
            }
            String path = a.optString("path", "").trim();
            if (path.length() == 0) return Words.RP_SHEET_NOPATH;
            File f = resolve(c, path);
            if (f == null || !f.isFile()) return String.format(Locale.ROOT, Words.RP_SHEET_NOFILE, path);
            String low = Csv.lower(f.getName());
            if ("query".equals(act)) return query(c, f, a);
            if ("export".equals(act)) return export(c, f, a);
            if ("preview".equals(act)) return previewPdf(c, f, a);
            if (low.endsWith(".csv")) {
                String ws = a.optString("sheet", "").trim();
                if (ws.length() > 0 && !"1".equals(ws)) return String.format(Locale.ROOT, Words.RP_SHEET_CSV_ONE, ws);
                Csv.Table t = Csv.load(f);
                int hdr = Math.max(1, a.optInt("header_row", 1));
                XlsxReader.Scan s = fromCsv(t, 1, Math.max(8, hdr + 1), true, hdr);
                if ("info".equals(act)) return infoCsv(f, t, s, a);
                return readCsv(f, t, a);
            }
            if (low.endsWith(".xls") || low.endsWith(".et")) {
                return String.format(Locale.ROOT, Words.RP_SHEET_EXT, f.getName());
            }
            if (!low.endsWith(".xlsx") && !low.endsWith(".xlsm")) {
                return String.format(Locale.ROOT, Words.RP_SHEET_EXT, f.getName());
            }
            XlsxReader.Book b = XlsxReader.open(f);
            if ("info".equals(act)) return infoXlsx(f, b, a);
            return readXlsx(b, a);
        } catch (Throwable t) {
            String m = t.getMessage();
            if (m == null || m.length() == 0) m = t.getClass().getSimpleName();
            if (m.startsWith(Words.RP_SHEET_ERR)) return m;
            return String.format(Locale.ROOT, Words.RP_SHEET_FAIL, m);
        }
    }

    // ═══════════ info：工作簿画像 ═══════════

    private static String infoXlsx(File f, XlsxReader.Book b, JSONObject a) throws Exception {
        int idx = sheetIndex(b, a.optString("sheet", ""));
        int hdr = Math.max(1, a.optInt("header_row", 1));
        JSONObject j = new JSONObject();
        j.put("file", f.getName());
        j.put("path", f.getAbsolutePath());
        j.put("size_kb", kb(f.length()));
        JSONArray sh = new JSONArray();
        List<String> warns = new ArrayList<String>();
        XlsxReader.Scan main = null;
        for (XlsxReader.SheetRef r : b.sheets) {
            XlsxReader.Scan m;
            if (r.index == idx) {
                m = XlsxReader.scan(b, r.index, 1, Math.max(8, hdr + 1), true, hdr);
                main = m;
            } else {
                m = XlsxReader.scan(b, r.index, 0, 0, false, 1);
            }
            JSONObject o = new JSONObject();
            o.put("index", r.index);
            o.put("name", r.name);
            o.put("rows", m.rows);
            o.put("cols", m.cols);
            if (m.hiddenRowCount > 0) o.put("hidden_rows", m.hiddenRowCount);
            if (m.hiddenColCount > 0) o.put("hidden_cols", m.hiddenColCount);
            if (m.mergeCount > 0) o.put("merges", m.mergeCount);
            if (m.formulaCount > 0) o.put("formulas", m.formulaCount);
            if (m.formulaNoCache > 0) o.put("no_cache", m.formulaNoCache);
            if (r.hidden) o.put("hidden", true);
            sh.put(o);
            if (m.hiddenRowCount > 0) warns.add(String.format(Locale.ROOT, Words.RP_SHEET_W_HIDDEN, r.index, m.hiddenRowCount));
            if (m.hiddenColCount > 0) warns.add(String.format(Locale.ROOT, Words.RP_SHEET_W_HIDDENCOL, r.index, m.hiddenColCount));
        }
        j.put("sheets", sh);
        if (b.definedNames > 0) j.put("defined_names", b.definedNames);
        j.put("sheet", idx);
        j.put("sheet_name", b.sheets.get(idx - 1).name);
        j.put("header_row", hdr);
        j.put("rows", main.rows);
        j.put("cols", main.cols);
        j.put("columns", columnsJson(main, 60));
        j.put("preview", preview(main, Math.max(8, hdr + 1), 40));
        for (String w : b.extra) warns.add(w);
        warns.addAll(dupWarns(b));
        if (main.rows > 5000) warns.add(String.format(Locale.ROOT, Words.RP_SHEET_W_BIG, main.rows));
        if (headMerged(main, hdr)) warns.add(Words.RP_SHEET_W_HEADMERGE);
        if (main.formulaNoCache > 0) warns.add(String.format(Locale.ROOT, Words.RP_SHEET_NOCACHE2, main.formulaNoCache));
        j.put("warnings", strArray(warns));
        String c1 = "A";
        String c2 = XlsxReader.colName(Math.min(main.cols, 60));
        j.put("hint", String.format(Locale.ROOT, Words.RP_SHEET_HINT, c1, c2, Math.min(main.rows, hdr + 20)));
        return j.toString(1);
    }

    private static String infoCsv(File f, Csv.Table t, XlsxReader.Scan s, JSONObject a) throws Exception {
        int hdr = Math.max(1, a.optInt("header_row", 1));
        JSONObject j = new JSONObject();
        j.put("file", f.getName());
        j.put("path", f.getAbsolutePath());
        j.put("size_kb", kb(f.length()));
        JSONArray sh = new JSONArray();
        JSONObject o = new JSONObject();
        o.put("index", 1);
        o.put("name", f.getName());
        o.put("rows", s.rows);
        o.put("cols", s.cols);
        sh.put(o);
        j.put("sheets", sh);
        j.put("sheet", 1);
        j.put("sheet_name", f.getName());
        j.put("header_row", hdr);
        j.put("rows", s.rows);
        j.put("cols", s.cols);
        j.put("columns", columnsJson(s, 60));
        j.put("preview", preview(s, Math.max(8, hdr + 1), 40));
        List<String> warns = new ArrayList<String>();
        warns.add(Words.RP_SHEET_CSVNOTE);
        if (s.rows > 5000) warns.add(String.format(Locale.ROOT, Words.RP_SHEET_W_BIG, s.rows));
        j.put("warnings", strArray(warns));
        j.put("hint", String.format(Locale.ROOT, Words.RP_SHEET_HINT, "A", XlsxReader.colName(Math.min(s.cols, 60)), Math.min(s.rows, hdr + 20)));
        return j.toString(1);
    }

    // ═══════════ read：按区域取数成 TSV ═══════════

    private static String readXlsx(XlsxReader.Book b, JSONObject a) throws Exception {
        int idx = sheetIndex(b, a.optString("sheet", ""));
        int maxRows = clamp(a.optInt("max_rows", 200), 1, 2000);
        int[] box = range(a.optString("range", ""));
        int keepTo = box[2] > 0 ? Math.min(box[2], box[0] + maxRows - 1) : box[0] + maxRows - 1;
        XlsxReader.Scan s = XlsxReader.scan(b, idx, box[0], keepTo, false, 1);
        return renderRead(Words.RP_SHEET_KIND_XLSX, b.sheets.get(idx - 1).name, idx, s, box, maxRows, a);
    }

    private static String readCsv(File f, Csv.Table t, JSONObject a) throws Exception {
        int maxRows = clamp(a.optInt("max_rows", 200), 1, 2000);
        int[] box = range(a.optString("range", ""));
        XlsxReader.Scan s = fromCsv(t, 1, box[0] + maxRows - 1, false, 1);
        return renderRead(Words.RP_SHEET_KIND_CSV, f.getName(), 1, s, box, maxRows, a);
    }

    private static String renderRead(String kind, String name, int idx, XlsxReader.Scan s,
                                     int[] box, int maxRows, JSONObject a) {
        int rowsTotal = s.rows, colsTotal = s.cols;
        if (rowsTotal == 0 || colsTotal == 0) return Words.RP_SHEET_EMPTY;
        int r1 = box[0], c1 = box[1];
        if (r1 > rowsTotal) r1 = rowsTotal;
        if (c1 > colsTotal) c1 = colsTotal;
        int c2 = box[3] > 0 ? Math.min(box[3], colsTotal) : Math.min(colsTotal, c1 + 59);
        int endRow = box[2] > 0 ? Math.min(box[2], rowsTotal) : Math.min(rowsTotal, r1 + maxRows - 1);
        if (endRow < r1) endRow = r1;
        int capRow = r1 + maxRows - 1;
        if (endRow > capRow) endRow = capRow;
        boolean skipH = a.optInt("skip_hidden", 0) == 1;
        int skippedInRange = 0;
        if (skipH) {
            for (int r = r1; r <= endRow; r++) if (s.hiddenSet.contains(r)) skippedInRange++;
        }
        String rangeTxt = XlsxReader.colName(c1) + r1 + ":" + XlsxReader.colName(c2) + endRow;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, Words.RP_SHEET_META, kind, idx, name, rangeTxt, r1, endRow, rowsTotal)).append('\n');
        if (s.hiddenRowCount > 0) {
            if (skipH) sb.append(String.format(Locale.ROOT, Words.RP_SHEET_SKIPPED2, skippedInRange, s.hiddenRowCount)).append('\n');
            else sb.append(String.format(Locale.ROOT, Words.RP_SHEET_HIDDEN, joinRows(s.hiddenRows))).append('\n');
        }
        if (s.formulaNoCache > 0) sb.append(String.format(Locale.ROOT, Words.RP_SHEET_NOCACHE, s.formulaNoCache)).append('\n');
        sb.append('#').append('\t').append(Words.RP_SHEET_ROWCOL);
        for (int c = c1; c <= c2; c++) sb.append('\t').append(XlsxReader.colName(c));
        sb.append('\n');
        for (int r = r1; r <= endRow; r++) {
            if (skipH && s.hiddenSet.contains(r)) continue;
            List<XlsxReader.Cell> line = s.grid.get(r);
            if (line == null) continue;
            boolean any = false;
            for (int c = c1; c <= c2 && c - 1 < line.size(); c++) {
                if (line.get(c - 1) != null && line.get(c - 1).text.length() > 0) { any = true; break; }
            }
            if (!any) continue;
            sb.append(r);
            for (int c = c1; c <= c2; c++) {
                sb.append('\t');
                if (c - 1 < line.size() && line.get(c - 1) != null) sb.append(Csv.flat(line.get(c - 1).text));
            }
            sb.append('\n');
        }
        if (endRow < rowsTotal) {
            sb.append(String.format(Locale.ROOT, Words.RP_SHEET_TRUNC, endRow, rowsTotal - endRow, XlsxReader.colName(c1), endRow + 1)).append('\n');
        }
        return sb.toString();
    }

    // ═══════════ find：按文件名找表格 ═══════════

    private static String find(String name, int max) {
        String raw = name == null ? "" : name.trim();
        String pat = Csv.lower(raw);
        List<File> hits = new ArrayList<File>();
        String[] dirs = {"/sdcard/Download/WeiXin", "/sdcard/Download", "/sdcard/Documents"};
        for (String d : dirs) {
            File dir = new File(d);
            File[] fs = null;
            try { fs = dir.listFiles(); } catch (Throwable t) { }
            if (fs == null) continue;
            for (File f : fs) {
                if (f == null || !f.isFile()) continue;
                String n = Csv.lower(f.getName());
                if (!(n.endsWith(".xlsx") || n.endsWith(".xlsm") || n.endsWith(".csv") || n.endsWith(".xls") || n.endsWith(".et"))) continue;
                boolean hit = pat.length() == 0 || (pat.indexOf('*') >= 0 || pat.indexOf('?') >= 0 ? glob(n, pat) : n.indexOf(pat) >= 0);
                if (hit && !hits.contains(f)) hits.add(f);
            }
        }
        if (hits.isEmpty()) return String.format(Locale.ROOT, Words.RP_SHEET_FIND_NONE, raw.length() == 0 ? "*" : raw);
        Collections.sort(hits, new Comparator<File>() {
            public int compare(File x, File y) {
                long a = x.lastModified(), b = y.lastModified();
                if (a == b) return 0;
                return a < b ? 1 : -1;
            }
        });
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, Words.RP_SHEET_FIND_HEAD, Math.min(max, hits.size()))).append('\n');
        boolean xls = false;
        for (int i = 0; i < max && i < hits.size(); i++) {
            File f = hits.get(i);
            String low = Csv.lower(f.getName());
            if (low.endsWith(".xls") || low.endsWith(".et")) xls = true;
            sb.append(String.format(Locale.ROOT, Words.RP_SHEET_FIND_LINE, i + 1, f.getAbsolutePath(), kb(f.length()) + " KB", stamp(f.lastModified()))).append('\n');
        }
        if (hits.size() > max) sb.append(String.format(Locale.ROOT, Words.RP_SHEET_FIND_MORE, hits.size() - max)).append('\n');
        if (xls) sb.append(Words.RP_SHEET_FIND_XLS).append('\n');
        sb.append(Words.RP_SHEET_FIND_HINT);
        return sb.toString();
    }

    private static boolean glob(String n, String pat) {
        StringBuilder re = new StringBuilder();
        for (int i = 0; i < pat.length(); i++) {
            char ch = pat.charAt(i);
            if (ch == '*') re.append(".*");
            else if (ch == '?') re.append('.');
            else re.append(java.util.regex.Pattern.quote(String.valueOf(ch)));
        }
        try { return n.matches(re.toString()); } catch (Throwable t) { return false; }
    }

    private static String stamp(long ms) {
        try {
            return new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.ROOT).format(new java.util.Date(ms));
        } catch (Throwable t) { return ""; }
    }

    // ═══════════ csv → 同一套 Scan ═══════════

    private static XlsxReader.Scan fromCsv(Csv.Table t, int keepFrom, int keepTo, boolean wantStats, int hdr) {
        XlsxReader.Scan s = new XlsxReader.Scan();
        s.rows = t.rows.size();
        s.cols = t.cols;
        for (int i = 0; i < t.rows.size(); i++) {
            int row = i + 1;
            String[] arr = t.rows.get(i);
            List<XlsxReader.Cell> line = new ArrayList<XlsxReader.Cell>();
            for (int c = 0; c < arr.length; c++) {
                String v = Csv.flat(arr[c]);
                if (v.length() == 0) { line.add(null); continue; }
                XlsxReader.Cell cell = new XlsxReader.Cell();
                cell.text = v;
                if (numLike(v)) { cell.type = XlsxReader.T_NUM; cell.raw = v; }
                else if (dateLike(v)) cell.type = XlsxReader.T_DATE;
                else cell.type = XlsxReader.T_TEXT;
                line.add(cell);
            }
            if (row >= keepFrom && row <= keepTo) s.grid.put(row, line);
            if (wantStats) {
                for (int c = 0; c < line.size(); c++) {
                    XlsxReader.Cell cell = line.get(c);
                    if (cell != null) XlsxReader.addStat(s, row, c + 1, cell, hdr);
                }
            }
        }
        if (wantStats) XlsxReader.finishStats(s, hdr);
        return s;
    }

    private static boolean numLike(String s) {
        return s.matches("-?[0-9]+(\\.[0-9]+)?") || s.matches("-?\\.[0-9]+");
    }

    private static boolean dateLike(String s) {
        return s.matches("[0-9]{4}[-/][0-9]{1,2}[-/][0-9]{1,2}.*");
    }

    // ═══════════ 共用小件 ═══════════

    private static int sheetIndex(XlsxReader.Book b, String want) throws Exception {
        String w = want == null ? "" : want.trim();
        if (w.length() == 0) return 1;
        String low = Csv.lower(w);
        int n = -1;
        try { n = Integer.parseInt(low); } catch (Throwable t) { }
        if (n > 0) {
            if (n > b.sheets.size()) {
                throw new Exception(String.format(Locale.ROOT, Words.RP_SHEET_NOSHEET, n, b.sheets.size(), names(b)));
            }
            return n;
        }
        for (XlsxReader.SheetRef r : b.sheets) {
            if (Csv.lower(r.name).indexOf(low) >= 0) return r.index;
        }
        throw new Exception(String.format(Locale.ROOT, Words.RP_SHEET_NONAME, w, names(b)));
    }

    private static String names(XlsxReader.Book b) {
        StringBuilder sb = new StringBuilder();
        for (XlsxReader.SheetRef r : b.sheets) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(r.index).append('.').append(r.name);
        }
        return sb.toString();
    }

    /** "A1:AF30" → {1,1,30,32}；"A1" → {1,1,0,0}（从那格起按 max_rows 读）。 */
    private static int[] range(String range) throws Exception {
        int[] out = new int[]{1, 1, 0, 0};
        String s = range == null ? "" : range.trim();
        if (s.length() == 0) return out;
        int k = s.indexOf(':');
        int[] p1 = XlsxReader.parseRef(k < 0 ? s : s.substring(0, k));
        if (p1[0] == 0 || p1[1] == 0) throw new Exception(String.format(Locale.ROOT, Words.RP_SHEET_BADRANGE, s));
        out[0] = p1[0];
        out[1] = p1[1];
        if (k >= 0) {
            int[] p2 = XlsxReader.parseRef(s.substring(k + 1));
            if (p2[0] == 0 || p2[1] == 0) throw new Exception(String.format(Locale.ROOT, Words.RP_SHEET_BADRANGE, s));
            out[2] = p2[0];
            out[3] = p2[1];
        }
        return out;
    }

    private static File resolve(Context c, String p) {
        try {
            File f = new File(p);
            if (f.isAbsolute()) return f;
            String home = Home.get(c).getAbsolutePath();
            String[] dirs = {"/sdcard/Download/WeiXin", "/sdcard/Download", "/sdcard/Documents", home, "/sdcard/工作目录"};
            for (String d : dirs) {
                File g = new File(d, p);
                if (g.isFile()) return g;
            }
        } catch (Throwable t) { }
        return null;
    }

    private static JSONArray columnsJson(XlsxReader.Scan s, int cap) throws Exception {
        JSONArray arr = new JSONArray();
        int n = Math.min(s.cols, cap);
        for (int c = 1; c <= n; c++) {
            XlsxReader.ColStat cs = (c - 1 < s.stats.size()) ? s.stats.get(c - 1) : null;
            JSONObject j = new JSONObject();
            j.put("ref", XlsxReader.colName(c));
            j.put("header", cs == null ? "" : cs.header);
            j.put("sql", "c" + c);
            j.put("type", cs == null ? "empty" : cs.type);
            j.put("nulls", cs == null ? 0 : cs.nulls);
            j.put("distinct", cs == null ? 0 : cs.distinct);
            if (cs != null && cs.capped) j.put("distinct_capped", true);
            arr.put(j);
        }
        return arr;
    }

    private static String preview(XlsxReader.Scan s, int maxRow, int maxCol) {
        int lastRow = Math.min(s.rows, maxRow);
        int lastCol = Math.min(s.cols, maxCol);
        StringBuilder sb = new StringBuilder();
        sb.append('#').append('\t').append(Words.RP_SHEET_ROWCOL);
        for (int c = 1; c <= lastCol; c++) sb.append('\t').append(XlsxReader.colName(c));
        for (int r = 1; r <= lastRow; r++) {
            sb.append('\n').append(r);
            List<XlsxReader.Cell> line = s.grid.get(r);
            for (int c = 1; c <= lastCol; c++) {
                sb.append('\t');
                if (line != null && c - 1 < line.size() && line.get(c - 1) != null) sb.append(Csv.flat(line.get(c - 1).text));
            }
        }
        if (s.rows > lastRow || s.cols > lastCol) {
            sb.append('\n').append(String.format(Locale.ROOT, Words.RP_SHEET_PREVIEW_CAP, lastRow, lastCol, s.rows, s.cols));
        }
        return sb.toString();
    }

    /** 同名前缀提醒：该报表有「4.项目营业收入」与「4.工程及设施结算表」这种，选表用序号。 */
    private static List<String> dupWarns(XlsxReader.Book b) {
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < b.sheets.size(); i++) {
            for (int k = i + 1; k < b.sheets.size(); k++) {
                String x = pre(b.sheets.get(i).name), y = pre(b.sheets.get(k).name);
                if (x.length() > 0 && x.equals(y)) {
                    out.add(String.format(Locale.ROOT, Words.RP_SHEET_W_DUP, b.sheets.get(i).name, b.sheets.get(k).name));
                }
            }
        }
        return out;
    }

    private static String pre(String n) {
        int k = n.indexOf('.');
        if (k >= 0 && k <= 3) return n.substring(0, k + 1);
        return "";
    }

    private static boolean headMerged(XlsxReader.Scan s, int hdr) {
        for (String m : s.merges) {
            int[] rc = XlsxReader.parseRef(m);
            if (rc[0] > 0 && rc[0] <= hdr) return true;
        }
        return false;
    }

    private static JSONArray strArray(List<String> list) {
        JSONArray arr = new JSONArray();
        for (String s : list) arr.put(s);
        return arr;
    }

    private static String joinRows(List<Integer> rows) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            if (i >= 12) { sb.append("…"); break; }
            if (i > 0) sb.append(',');
            sb.append(rows.get(i));
        }
        return sb.toString();
    }

    private static double kb(long n) {
        return Math.round(n / 102.4) / 10.0;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // ═══════════ query：整表进库再查 ═══════════


    // ═══════════ M4：export 出表 / preview 出 PDF ═══════════

    /**
     * 取数：给了 sql 就走 SQLite（与 query 完全同一条通路）；没给 sql 就按 range 取区域，
     * 区域首行当表头（表头格子空的话用列字母）。range 只给一格（如 C5）时从那里往下取。
     */
    private static Grid grid(Context c, File f, JSONObject a, int defRows, int cap) throws Exception {
        String sql = a.optString("sql", "").trim();
        String low = Csv.lower(f.getName());
        int maxRows = clamp(a.optInt("max_rows", defRows), 1, cap);
        if (sql.length() > 0) {
            int hdr = Math.max(1, a.optInt("header_row", 1));
            int dataRow = a.optInt("data_row", hdr + 1);
            if (dataRow <= hdr) dataRow = hdr + 1;
            TableStore.Impl im;
            if (low.endsWith(".csv")) {
                im = TableStore.importCsv(c, Csv.load(f), f, hdr, dataRow);
            } else {
                XlsxReader.Book b = XlsxReader.open(f);
                im = TableStore.importXlsx(c, b, sheetIndex(b, a.optString("sheet", "")), hdr, dataRow);
            }
            return TableStore.fetch(im, sql, maxRows);
        }
        String rg = a.optString("range", "").trim();
        if (rg.length() == 0) return null;
        int[] box = range(rg);
        if (low.endsWith(".csv")) return gridCsv(Csv.load(f), box, maxRows);
        XlsxReader.Book b = XlsxReader.open(f);
        return gridScan(b, sheetIndex(b, a.optString("sheet", "")), box, maxRows);
    }

    private static Grid gridScan(XlsxReader.Book b, int idx, int[] box, int maxRows) throws Exception {
        int r1 = Math.max(1, box[0]);
        int wantEnd = box[2] > 0 ? box[2] : r1 + maxRows - 1;
        int endRow = wantEnd;
        if (endRow - r1 + 1 > maxRows) endRow = r1 + maxRows - 1;
        XlsxReader.Scan s = XlsxReader.scan(b, idx, r1, endRow, false, 1);
        if (s.rows == 0 || s.cols == 0) throw new Exception(Words.RP_SHEET_EMPTY);
        int c1 = Math.max(1, box[1]);
        if (c1 > s.cols) c1 = s.cols;
        int c2 = box[3] > 0 ? Math.min(box[3], s.cols) : s.cols;
        if (c2 < c1) c2 = c1;
        int n = c2 - c1 + 1;
        Grid g = new Grid();
        g.cols = headOf(s.grid.get(r1), c1, n);
        for (int r = r1 + 1; r <= endRow; r++) {
            String[] row = straight(s.grid.get(r), c1, n);
            if (row != null) g.rows.add(row);
        }
        g.total = g.rows.size();
        if (endRow < wantEnd || (box[2] == 0 && endRow < s.rows)) g.more = true;
        return g;
    }

    private static Grid gridCsv(Csv.Table t, int[] box, int maxRows) throws Exception {
        if (t.rows.isEmpty() || t.cols == 0) throw new Exception(Words.RP_SHEET_EMPTY);
        int r1 = Math.max(1, box[0]);
        int c1 = Math.max(1, box[1]);
        if (c1 > t.cols) c1 = t.cols;
        int c2 = box[3] > 0 ? Math.min(box[3], t.cols) : t.cols;
        if (c2 < c1) c2 = c1;
        int n = c2 - c1 + 1;
        Grid g = new Grid();
        String[] h = (r1 - 1 < t.rows.size()) ? t.rows.get(r1 - 1) : new String[0];
        g.cols = new String[n];
        for (int i = 0; i < n; i++) {
            String v = (c1 - 1 + i < h.length && h[c1 - 1 + i] != null) ? h[c1 - 1 + i].trim() : "";
            g.cols[i] = v.length() == 0 ? XlsxReader.colName(c1 + i) : v;
        }
        int start = r1 + 1;
        int wantEnd = box[2] > 0 ? Math.min(box[2], t.rows.size()) : t.rows.size();
        int end = wantEnd;
        if (end - start + 1 > maxRows) end = start + maxRows - 1;
        for (int r = start; r <= end && r <= t.rows.size(); r++) {
            String[] src = t.rows.get(r - 1);
            String[] row = new String[n];
            boolean any = false;
            for (int i = 0; i < n; i++) {
                String v = (c1 - 1 + i < src.length && src[c1 - 1 + i] != null) ? src[c1 - 1 + i] : "";
                row[i] = v;
                if (v.trim().length() > 0) any = true;
            }
            if (any) g.rows.add(row);
        }
        g.total = g.rows.size();
        if (end < wantEnd) g.more = true;
        return g;
    }

    private static String[] headOf(List<XlsxReader.Cell> line, int c1, int n) {
        String[] cols = new String[n];
        for (int i = 0; i < n; i++) {
            String v = "";
            if (line != null && c1 - 1 + i < line.size() && line.get(c1 - 1 + i) != null) {
                v = Csv.flat(line.get(c1 - 1 + i).text).trim();
            }
            cols[i] = v.length() == 0 ? XlsxReader.colName(c1 + i) : v;
        }
        return cols;
    }

    /** 整行都空就回 null（空行不进出表，与 read 的口径一致）。 */
    private static String[] straight(List<XlsxReader.Cell> line, int c1, int n) {
        if (line == null) return null;
        String[] row = new String[n];
        boolean any = false;
        for (int i = 0; i < n; i++) {
            String v = "";
            if (c1 - 1 + i < line.size() && line.get(c1 - 1 + i) != null) {
                v = Csv.flat(line.get(c1 - 1 + i).text);
            }
            row[i] = v;
            if (v.trim().length() > 0) any = true;
        }
        return any ? row : null;
    }

    private static String export(Context c, File f, JSONObject a) {
        try {
            String fmt = Csv.lower(a.optString("format", "xlsx")).trim();
            if (fmt.length() == 0) fmt = "xlsx";
            if (!"xlsx".equals(fmt) && !"csv".equals(fmt)) {
                return String.format(Locale.ROOT, Words.RP_SHEET_FMT, fmt);
            }
            Grid g = grid(c, f, a, 5000, 20000);
            if (g == null) return Words.RP_SHEET_NEEDQ;
            if (g.cols.length == 0 || g.rows.isEmpty()) return Words.RP_SHEET_GRID_NONE;
            File out = outFile(c, a.optString("out", ""), fmt);
            if ("csv".equals(fmt)) Csv.write(out, g);
            else XlsxWriter.write(out, tabName(f, a), g);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.ROOT, Words.RP_SHEET_EXPORT_OK, g.rows.size(), g.cols.length,
                    out.getAbsolutePath(), Math.max(1L, (out.length() + 1023) / 1024)));
            if (g.more) sb.append('\n').append(Words.RP_SHEET_EXPORT_TRUNC);
            return sb.toString();
        } catch (Throwable t) {
            return m4fail(t);
        }
    }

    private static String previewPdf(Context c, File f, JSONObject a) {
        long t0 = System.currentTimeMillis();
        try {
            Grid g = grid(c, f, a, 200, 5000);
            if (g == null) return Words.RP_SHEET_NEEDQ;
            if (g.cols.length == 0 || g.rows.isEmpty()) return Words.RP_SHEET_GRID_NONE;
            int maxCols = clamp(a.optInt("max_cols", 8), 1, 40);
            String theme = a.optString("theme", "default").trim();
            if (theme.length() == 0) theme = "default";
            String md = mdTable(g, maxCols, tabName(f, a), f.getName());
            File out = outFile(c, a.optString("out", ""), "pdf");
            String err = PdfMake.build(c, md, 794, out, theme);
            if (err != null) return String.format(Locale.ROOT, Words.RP_SHEET_PDF_FAIL, err);
            long ms = System.currentTimeMillis() - t0;
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.ROOT, Words.RP_SHEET_PDF_OK, out.getAbsolutePath(),
                    PdfMake.lastPages, Math.max(1L, (out.length() + 1023) / 1024),
                    g.rows.size(), g.cols.length, ms));
            int cut = Math.min(g.cols.length, maxCols);
            if (g.cols.length > maxCols) {
                sb.append('\n').append(String.format(Locale.ROOT, Words.RP_SHEET_PDF_CUTCOL, cut));
            }
            if (g.more) {
                sb.append('\n').append(String.format(Locale.ROOT, Words.RP_SHEET_PDF_CUTROW, g.rows.size()));
            }
            return sb.toString();
        } catch (Throwable t) {
            return m4fail(t);
        }
    }

    /** Grid → md 表格。数值列右对齐，单元格里的竖线按 md 表格规则转义。 */
    private static String mdTable(Grid g, int maxCols, String title, String src) {
        int n = Math.min(g.cols.length, maxCols);
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title.length() > 0 ? title : stripExt(src)).append("\n\n");
        sb.append("共 ").append(g.rows.size()).append(" 行 × ").append(g.cols.length).append(" 列");
        if (g.cols.length > n) sb.append("，只画前 ").append(n).append(" 列");
        if (g.more) sb.append("，只画前 ").append(g.rows.size()).append(" 行");
        sb.append("。\n\n|");
        for (int c = 0; c < n; c++) sb.append(' ').append(cell(g.cols[c])).append(" |");
        sb.append("\n|");
        for (int c = 0; c < n; c++) sb.append(g.isNumCol(c) ? " ---: |" : " --- |");
        sb.append('\n');
        for (int r = 0; r < g.rows.size(); r++) {
            String[] row = g.rows.get(r);
            sb.append('|');
            for (int c = 0; c < n; c++) {
                sb.append(' ').append(cell(c < row.length ? row[c] : "")).append(" |");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String cell(String s) {
        if (s == null) return "";
        String v = Csv.flat(s).replace("|", "\\|");
        return v.length() > 200 ? v.substring(0, 200) + "…" : v;
    }

    private static String stripExt(String n) {
        if (n == null) return "";
        int k = n.lastIndexOf('.');
        return k > 0 ? n.substring(0, k) : n;
    }

    /** 输出文件：相对名落到运行目录 out/，绝对路径照用；扩展名按 format 强制。 */
    private static File outFile(Context c, String name, String ext) {
        String n = name == null ? "" : name.trim();
        if (n.length() == 0) {
            n = "表格-" + new java.text.SimpleDateFormat("MMdd-HHmmss", Locale.ROOT).format(new java.util.Date());
        }
        File f = new File(n);
        if (!f.isAbsolute()) f = new File(new File(Home.get(c), "out"), n);
        String base = f.getName();
        int k = base.lastIndexOf('.');
        if (k > 0) base = base.substring(0, k);
        File dir = f.getParentFile();
        if (dir != null) dir.mkdirs();
        return new File(dir, base + "." + ext);
    }

    /** 表名：sheet 给了名字就用它；给了序号或没给，就去工作簿里取真实表名（取不到退文件名）。 */
    private static String tabName(File f, JSONObject a) {
        String want = a.optString("sheet", "").trim();
        if (want.length() > 0 && !want.matches("[0-9]+")) return want;
        String low = Csv.lower(f.getName());
        if (low.endsWith(".xlsx") || low.endsWith(".xlsm")) {
            try {
                XlsxReader.Book b = XlsxReader.open(f);
                int idx = sheetIndex(b, want);
                String n = b.sheets.get(idx - 1).name;
                if (n != null && n.trim().length() > 0) return n.trim();
            } catch (Throwable t) { }
        }
        return stripExt(f.getName());
    }

    private static String m4fail(Throwable t) {
        String m = t.getMessage();
        if (m == null || m.length() == 0) m = t.getClass().getSimpleName();
        if (m.startsWith(Words.RP_SHEET_ERR)) return m;
        return String.format(Locale.ROOT, Words.RP_SHEET_FAIL, m);
    }
    private static String query(Context c, File f, JSONObject a) {
        String sql = a.optString("sql", "").trim();
        if (sql.length() == 0) return Words.RP_SHEET_NOSQL;
        String low = Csv.lower(f.getName());
        if (!low.endsWith(".csv") && !low.endsWith(".xlsx") && !low.endsWith(".xlsm")) {
            return String.format(Locale.ROOT, Words.RP_SHEET_EXT, f.getName());
        }
        int hdr = Math.max(1, a.optInt("header_row", 1));
        int dataRow = a.optInt("data_row", hdr + 1);
        if (dataRow <= hdr) dataRow = hdr + 1;
        int maxRows = clamp(a.optInt("max_rows", 200), 1, 2000);
        try {
            TableStore.Impl im;
            if (low.endsWith(".csv")) {
                Csv.Table t = Csv.load(f);
                im = TableStore.importCsv(c, t, f, hdr, dataRow);
            } else {
                XlsxReader.Book b = XlsxReader.open(f);
                int idx = sheetIndex(b, a.optString("sheet", ""));
                im = TableStore.importXlsx(c, b, idx, hdr, dataRow);
            }
            return TableStore.run(im, sql, maxRows);
        } catch (Throwable t) {
            String m = t.getMessage();
            if (m == null || m.length() == 0) m = t.getClass().getSimpleName();
            if (m.startsWith(Words.RP_SHEET_ERR)) return m;
            return String.format(Locale.ROOT, Words.RP_SHEET_FAIL, m);
        }
    }
}
