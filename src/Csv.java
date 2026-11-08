package app.reticle;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * M1 表格数据面：csv 读写的最小实现（RFC 4180，引号内可含换行，BOM 处理，分隔符探测）。
 * 读：整份进内存（csv 不会有 xlsx 那种体量），写：给 M4 的 export 复用。
 */
public class Csv {

    public static class Table {
        public List<String[]> rows = new ArrayList<String[]>();
        public int cols = 0;
        public char delim = ',';
        public boolean bom = false;
    }

    public static Table load(File f) throws Exception {
        Table t = new Table();
        String s = readAll(f);
        if (s.length() > 0 && s.charAt(0) == '\uFEFF') { t.bom = true; s = s.substring(1); }
        if (s.indexOf('\u0000') >= 0) throw new Exception("看起来不是文本文件");
        t.delim = detect(s);
        parse(s, t);
        return t;
    }

    private static String readAll(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(f.length() + 16, 1 << 24));
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            try { in.close(); } catch (Throwable x) { }
        }
    }

    /** 第一非空行里，引号外的 , \t ; 谁多听谁的。 */
    private static char detect(String s) {
        String line = "";
        StringBuilder sb = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < s.length() && sb.length() < 4000; i++) {
            char c = s.charAt(i);
            if (c == '"') q = !q;
            if (!q && (c == '\n' || c == '\r')) {
                if (sb.toString().trim().length() > 0) { line = sb.toString(); break; }
                sb.setLength(0);
                continue;
            }
            sb.append(c);
        }
        if (line.length() == 0) line = sb.toString();
        int comma = 0, tab = 0, semi = 0;
        q = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') { q = !q; continue; }
            if (q) continue;
            if (c == ',') comma++;
            else if (c == '\t') tab++;
            else if (c == ';') semi++;
        }
        if (tab > comma && tab >= semi) return '\t';
        if (semi > comma && semi > tab) return ';';
        return ',';
    }

    private static void parse(String s, Table t) {
        List<String> cur = new ArrayList<String>();
        StringBuilder cell = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (q) {
                if (c == '"') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '"') { cell.append('"'); i++; }
                    else q = false;
                } else cell.append(c);
                continue;
            }
            if (c == '"') { q = true; continue; }
            if (c == t.delim) {
                cur.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n') i++;
                cur.add(cell.toString());
                cell.setLength(0);
                push(t, cur);
                cur = new ArrayList<String>();
            } else {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !cur.isEmpty()) {
            cur.add(cell.toString());
            push(t, cur);
        }
    }

    private static void push(Table t, List<String> cells) {
        String[] a = cells.toArray(new String[cells.size()]);
        int last = a.length;
        while (last > 0 && (a[last - 1] == null || a[last - 1].trim().length() == 0)) last--;
        if (last == 0) return;
        String[] keep = new String[last];
        System.arraycopy(a, 0, keep, 0, last);
        t.rows.add(keep);
        if (last > t.cols) t.cols = last;
    }

    /** 一格 TSV 安全化：制表符与换行折成空格。 */
    public static String flat(String s) {
        if (s == null) return "";
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    public static String quote(String s, char delim) {
        if (s == null) s = "";
        boolean need = s.indexOf(delim) >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        if (!need) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    /** M4：把 Grid 写成 csv（UTF-8 带 BOM，Excel 与 WPS 都不会乱码；引号与逗号按 RFC 4180 转义）。 */
    public static void write(File f, Grid g) throws Exception {
        if (g == null || g.cols.length == 0) throw new Exception(Words.RP_SHEET_GRID_NONE);
        File d = f.getParentFile();
        if (d != null) d.mkdirs();
        java.io.Writer w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(f), "UTF-8");
        try {
            w.write('\uFEFF');
            for (int c = 0; c < g.cols.length; c++) {
                if (c > 0) w.write(',');
                w.write(quote(g.cols[c], ','));
            }
            w.write("\r\n");
            for (int r = 0; r < g.rows.size(); r++) {
                String[] row = g.rows.get(r);
                for (int c = 0; c < g.cols.length; c++) {
                    if (c > 0) w.write(',');
                    String v = (c < row.length && row[c] != null) ? row[c] : "";
                    w.write(quote(v, ','));
                }
                w.write("\r\n");
            }
        } finally {
            try { w.close(); } catch (Throwable t) { }
        }
    }

    public static String lower(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }
}
