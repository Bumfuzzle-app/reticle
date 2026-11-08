package app.reticle;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * M3 表格数据面：整表进 SQLite，再用只读 SQL 查。
 *
 * · 物理命名：表固定 t1，列 c1..cN；表头文字只存在 meta 里（不拿中文当列名）。
 * · 数值列额外给 cN_v（REAL，原始数值）与 cN_c（INTEGER，×100 取整的整数分）——
 *   金额求和用 cN_c，别用文本列，避开双精度误差。
 * · 导入是流式的：XlsxReader.Sink 边扫边写，每 500 行一个事务，不把整表装进 List。
 * · 缓存键＝路径＋mtime＋大小＋表号＋表头行＋数据起始行；同一个文件第二次查不重导。
 * · 查询只开 OPEN_READONLY，并且先过白名单（只放 SELECT / WITH，禁写与 ATTACH）。
 */
public class TableStore {

    public static final String TABLE = "t1";
    public static final int SCHEMA = 3;

    public static class Impl {
        public File db;
        public int sheet, rows, cols;
        public String sheetName = "";
        public String[] headers = new String[0];
        public String[] types = new String[0];
        public boolean cached = false;
    }

    // ═══════════ 导入 ═══════════

    public static Impl importXlsx(Context c, XlsxReader.Book b, int idx, int headerRow, int dataRow) throws Exception {
        Impl im = new Impl();
        im.sheet = idx;
        im.sheetName = b.sheets.get(idx - 1).name;
        im.db = new File(dir(c), key(b.file.getAbsolutePath() + "|" + b.file.lastModified() + "|" + b.size + "|"
                + idx + "|" + headerRow + "|" + dataRow + "|" + SCHEMA) + ".db");
        if (im.db.isFile() && im.db.length() > 0) {
            if (readMeta(im)) { im.cached = true; return im; }
            im.db.delete();
        }
        XlsxReader.Scan meta = XlsxReader.scan(b, idx, 1, 1, true, headerRow);
        int cols = meta.cols;
        if (cols <= 0) throw new Exception(Words.RP_SHEET_EMPTY);
        im.cols = cols;
        fill(im, meta);
        boolean[] num = new boolean[cols + 1];
        for (int i = 1; i <= cols; i++) {
            XlsxReader.ColStat cs = (i - 1 < meta.stats.size()) ? meta.stats.get(i - 1) : null;
            num[i] = cs != null && cs.nNum > 0;
        }
        File tmp = new File(im.db.getAbsolutePath() + ".part");
        if (tmp.exists()) tmp.delete();
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(tmp, null);
        try {
            db.beginTransaction();
            StringBuilder sb = new StringBuilder("CREATE TABLE t1 (r INTEGER PRIMARY KEY");
            for (int i = 1; i <= cols; i++) {
                sb.append(", c").append(i).append(" TEXT");
                if (num[i]) sb.append(", c").append(i).append("_v REAL, c").append(i).append("_c INTEGER");
            }
            sb.append(")");
            db.execSQL(sb.toString());
            db.execSQL("CREATE TABLE meta (k TEXT PRIMARY KEY, v TEXT)");
            StringBuilder ins = new StringBuilder("INSERT INTO t1 (r");
            StringBuilder q = new StringBuilder("?");
            for (int i = 1; i <= cols; i++) {
                ins.append(", c").append(i);
                q.append(",?");
                if (num[i]) {
                    ins.append(", c").append(i).append("_v, c").append(i).append("_c");
                    q.append(",?,?");
                }
            }
            ins.append(") VALUES (").append(q).append(")");
            SQLiteStatement st = db.compileStatement(ins.toString());
            Inserter in = new Inserter(st, cols, num, Math.max(headerRow + 1, dataRow));
            XlsxReader.scan(b, idx, 0, 0, false, headerRow, in);
            in.flush();
            im.rows = in.inserted;
            putMeta(db, im);
            db.setTransactionSuccessful();
        } finally {
            try { db.endTransaction(); } catch (Throwable t) { }
            try { db.close(); } catch (Throwable t) { }
        }
        install(tmp, im.db);
        prune(c);
        return im;
    }


    public static Impl importCsv(Context c, Csv.Table t, File f, int headerRow, int dataRow) throws Exception {
        Impl im = new Impl();
        im.sheet = 1;
        im.sheetName = f.getName();
        im.db = new File(dir(c), key(f.getAbsolutePath() + "|" + f.lastModified() + "|" + f.length() + "|1|"
                + headerRow + "|" + dataRow + "|csv|" + SCHEMA) + ".db");
        if (im.db.isFile() && im.db.length() > 0) {
            if (readMeta(im)) { im.cached = true; return im; }
            im.db.delete();
        }
        int cols = t.cols;
        if (cols <= 0 || t.rows.isEmpty()) throw new Exception(Words.RP_SHEET_EMPTY);
        im.cols = cols;
        im.headers = new String[cols];
        im.types = new String[cols];
        int start = Math.max(headerRow + 1, dataRow);
        String[] hdr = (headerRow >= 1 && headerRow <= t.rows.size()) ? t.rows.get(headerRow - 1) : new String[0];
        boolean[] num = new boolean[cols + 1];
        for (int i = 1; i <= cols; i++) {
            im.headers[i - 1] = (i - 1) < hdr.length ? Csv.flat(hdr[i - 1]) : "";
            int seen = 0, nums = 0;
            for (int r = start; r <= t.rows.size() && r < start + 200; r++) {
                String[] row = t.rows.get(r - 1);
                String v = (i - 1) < row.length ? Csv.flat(row[i - 1]) : "";
                if (v.length() == 0) continue;
                seen++;
                if (looksNum(v)) nums++;
            }
            num[i] = seen > 0 && nums == seen;
            im.types[i - 1] = num[i] ? "number" : "text";
        }
        File tmp = new File(im.db.getAbsolutePath() + ".part");
        if (tmp.exists()) tmp.delete();
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(tmp, null);
        try {
            db.beginTransaction();
            StringBuilder sb = new StringBuilder("CREATE TABLE t1 (r INTEGER PRIMARY KEY");
            for (int i = 1; i <= cols; i++) {
                sb.append(", c").append(i).append(" TEXT");
                if (num[i]) sb.append(", c").append(i).append("_v REAL, c").append(i).append("_c INTEGER");
            }
            sb.append(")");
            db.execSQL(sb.toString());
            db.execSQL("CREATE TABLE meta (k TEXT PRIMARY KEY, v TEXT)");
            StringBuilder ins = new StringBuilder("INSERT INTO t1 (r");
            StringBuilder q = new StringBuilder("?");
            for (int i = 1; i <= cols; i++) {
                ins.append(", c").append(i);
                q.append(",?");
                if (num[i]) { ins.append(", c").append(i).append("_v, c").append(i).append("_c"); q.append(",?,?"); }
            }
            ins.append(") VALUES (").append(q).append(")");
            SQLiteStatement st = db.compileStatement(ins.toString());
            int inserted = 0;
            for (int r = start; r <= t.rows.size(); r++) {
                String[] row = t.rows.get(r - 1);
                st.clearBindings();
                st.bindLong(1, r);
                int ix = 2;
                for (int i = 1; i <= cols; i++) {
                    String v = (i - 1) < row.length ? Csv.flat(row[i - 1]) : "";
                    if (v.length() == 0) st.bindNull(ix++); else st.bindString(ix++, v);
                    if (num[i]) {
                        if (v.length() > 0 && looksNum(v)) {
                            double d = Double.parseDouble(v);
                            st.bindDouble(ix++, d);
                            st.bindLong(ix++, Math.round(d * 100.0));
                        } else { st.bindNull(ix++); st.bindNull(ix++); }
                    }
                }
                st.executeInsert();
                inserted++;
            }
            im.rows = inserted;
            putMeta(db, im);
            db.setTransactionSuccessful();
        } finally {
            try { db.endTransaction(); } catch (Throwable x) { }
            try { db.close(); } catch (Throwable x) { }
        }
        install(tmp, im.db);
        prune(c);
        return im;
    }

    // ═══════════ 查询 ═══════════

    public static String run(Impl im, String sql, int maxRows) {
        Grid g;
        try {
            g = fetch(im, sql, maxRows);
        } catch (Throwable t) {
            String m = t.getMessage();
            if (m == null || m.length() == 0) m = t.getClass().getSimpleName();
            if (m.startsWith(Words.RP_SHEET_ERR)) return m;
            return String.format(Locale.ROOT, Words.RP_SHEET_SQL_ERR, m);
        }
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, Words.RP_SHEET_IMP, im.sheet, im.sheetName, im.rows, im.cols,
                im.cached ? Words.RP_SHEET_IMP_CACHE : "")).append('\n');
        out.append(String.format(Locale.ROOT, Words.RP_SHEET_QMAP, map(im))).append('\n');
        if (money(im)) out.append(Words.RP_SHEET_MONEY_HINT).append('\n');
        StringBuilder head = new StringBuilder("#\t").append(Words.RP_SHEET_ROWCOL);
        for (int i = 0; i < g.cols.length; i++) head.append('\t').append(g.cols[i]);
        out.append(head).append('\n');
        for (int i = 0; i < g.rows.size(); i++) {
            String[] row = g.rows.get(i);
            out.append(i + 1);
            for (int c = 0; c < g.cols.length; c++) {
                out.append('\t');
                if (c < row.length && row[c] != null) out.append(row[c]);
            }
            out.append('\n');
        }
        if (g.more) out.append(String.format(Locale.ROOT, Words.RP_SHEET_QFULL, maxRows)).append('\n');
        else out.append(String.format(Locale.ROOT, Words.RP_SHEET_QROWS, g.total)).append('\n');
        return out.toString();
    }

    /**
     * M4：查询结果取成 Grid，query / export / preview 共用同一条通路
     * （所以 export 出去的表与 query 屏幕上看到的一模一样，不会一个四舍五入一个不四舍五入）。
     */
    public static Grid fetch(Impl im, String sql, int maxRows) {
        String bad = checkSql(sql);
        if (bad != null) throw new RuntimeException(String.format(Locale.ROOT, Words.RP_SHEET_SQL_BAD, bad));
        String s = sql.trim();
        while (s.endsWith(";")) s = s.substring(0, s.length() - 1).trim();
        Grid g = new Grid();
        SQLiteDatabase db = null;
        Cursor cu = null;
        try {
            db = SQLiteDatabase.openDatabase(im.db.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            cu = db.rawQuery(s, null);
            String[] names = cu.getColumnNames();
            g.cols = new String[names.length];
            for (int i = 0; i < names.length; i++) g.cols[i] = names[i] == null ? "?" : names[i];
            int n = 0;
            while (cu.moveToNext()) {
                n++;
                if (n > maxRows) { g.more = true; n--; break; }
                String[] row = new String[names.length];
                for (int i = 0; i < names.length; i++) {
                    String v = "";
                    try {
                        if (!cu.isNull(i)) {
                            int ty = cu.getType(i);
                            if (ty == Cursor.FIELD_TYPE_FLOAT) v = Csv.flat(num(cu.getDouble(i)));
                            else if (ty == Cursor.FIELD_TYPE_INTEGER) v = String.valueOf(cu.getLong(i));
                            else v = Csv.flat(cu.getString(i));
                        }
                    } catch (Throwable t) { }
                    row[i] = v;
                }
                g.rows.add(row);
            }
            g.total = n;
        } catch (Throwable t) {
            String m = t.getMessage();
            if (m == null || m.length() == 0) m = t.getClass().getSimpleName();
            throw new RuntimeException(String.format(Locale.ROOT, Words.RP_SHEET_SQL_ERR, m));
        } finally {
            try { if (cu != null) cu.close(); } catch (Throwable t) { }
            try { if (db != null) db.close(); } catch (Throwable t) { }
        }
        return g;
    }

    /** 只放一条 SELECT / WITH；禁写、禁 ATTACH / PRAGMA。 */
    static String checkSql(String sql) {
        String s = sql == null ? "" : sql.trim();
        while (s.endsWith(";")) s = s.substring(0, s.length() - 1).trim();
        if (s.length() == 0) return Words.RP_SHEET_SQL_EMPTY;
        String low = s.toLowerCase(Locale.ROOT);
        if (!low.startsWith("select") && !low.startsWith("with")) return Words.RP_SHEET_SQL_ONLY;
        if (low.indexOf(';') >= 0) return Words.RP_SHEET_SQL_ONE;
        String[] bad = {"attach", "detach", "pragma", "insert", "update", "delete", "drop", "create",
                "alter", "replace", "vacuum", "reindex", "trigger", "begin", "commit", "rollback"};
        for (String w : bad) {
            if (Pattern.compile("\\b" + w + "\\b").matcher(low).find()) {
                return String.format(Locale.ROOT, Words.RP_SHEET_SQL_BAN, w);
            }
        }
        return null;
    }

    // ═══════════ 内部小件 ═══════════

    static class Inserter implements XlsxReader.Sink {
        final SQLiteStatement st;
        final int cols, dataRow;
        final boolean[] num;
        String[] txt;
        double[] val;
        boolean[] has;
        int cur = -1, inserted = 0;

        Inserter(SQLiteStatement st, int cols, boolean[] num, int dataRow) {
            this.st = st;
            this.cols = cols;
            this.num = num;
            this.dataRow = dataRow;
            txt = new String[cols + 1];
            val = new double[cols + 1];
            has = new boolean[cols + 1];
        }

        public void cell(int row, int col, XlsxReader.Cell c) {
            if (row < dataRow || col < 1 || col > cols) return;
            if (row != cur) { flush(); cur = row; }
            txt[col] = c.text;
            if (num[col] && c.raw != null && c.raw.length() > 0) {
                try { val[col] = Double.parseDouble(c.raw); has[col] = true; } catch (Throwable t) { has[col] = false; }
            }
        }

        void flush() {
            if (cur < 0) return;
            st.clearBindings();
            st.bindLong(1, cur);
            int ix = 2;
            for (int i = 1; i <= cols; i++) {
                if (txt[i] == null) st.bindNull(ix++); else st.bindString(ix++, txt[i]);
                if (num[i]) {
                    if (has[i]) { st.bindDouble(ix++, val[i]); st.bindLong(ix++, Math.round(val[i] * 100.0)); }
                    else { st.bindNull(ix++); st.bindNull(ix++); }
                }
            }
            st.executeInsert();
            inserted++;
            for (int i = 0; i <= cols; i++) { txt[i] = null; has[i] = false; val[i] = 0; }
        }
    }

    static void fill(Impl im, XlsxReader.Scan meta) {
        im.headers = new String[im.cols];
        im.types = new String[im.cols];
        for (int i = 1; i <= im.cols; i++) {
            XlsxReader.ColStat cs = (i - 1 < meta.stats.size()) ? meta.stats.get(i - 1) : null;
            im.headers[i - 1] = cs == null || cs.header == null ? "" : cs.header;
            im.types[i - 1] = cs == null ? "text" : cs.type;
        }
    }

    static File dir(Context c) {
        File d = new File(Home.get(c), "tmp/sheet");
        try { if (!d.exists()) d.mkdirs(); } catch (Throwable t) { }
        return d;
    }

    /** 缓存键：FNV 展开成 16 进制（确定性，不依赖平台 hashCode）。 */
    static String key(String s) {
        long h = 1125899906842597L;
        for (int i = 0; i < s.length(); i++) h = 31 * h + s.charAt(i);
        return Long.toHexString(h);
    }

    static void install(File tmp, File db) throws Exception {
        if (db.exists() && !db.delete()) throw new Exception(Words.RP_SHEET_DBFAIL);
        if (!tmp.renameTo(db)) throw new Exception(Words.RP_SHEET_DBFAIL);
    }

    static void prune(Context c) {
        try {
            File[] fs = dir(c).listFiles();
            if (fs == null || fs.length <= 6) return;
            List<File> all = new ArrayList<File>();
            for (File f : fs) if (f.getName().endsWith(".db")) all.add(f);
            while (all.size() > 6) {
                File old = null;
                for (File f : all) if (old == null || f.lastModified() < old.lastModified()) old = f;
                if (old == null) break;
                all.remove(old);
                old.delete();
            }
        } catch (Throwable t) { }
    }

    static void putMeta(SQLiteDatabase db, Impl im) {
        try {
            exec(db, "schema", String.valueOf(SCHEMA));
            exec(db, "sheet", String.valueOf(im.sheet));
            exec(db, "name", im.sheetName);
            exec(db, "rows", String.valueOf(im.rows));
            exec(db, "cols", String.valueOf(im.cols));
            exec(db, "headers", join(im.headers));
            exec(db, "types", join(im.types));
        } catch (Throwable t) { }
    }

    static void exec(SQLiteDatabase db, String k, String v) {
        db.execSQL("INSERT OR REPLACE INTO meta (k, v) VALUES (?, ?)", new Object[]{k, v});
    }

    static String join(String[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append('\u0001');
            sb.append(a[i] == null ? "" : a[i]);
        }
        return sb.toString();
    }

    static String[] split(String s, int n) {
        String[] out = new String[n];
        String[] parts = s == null ? new String[0] : s.split("\u0001", -1);
        for (int i = 0; i < n; i++) out[i] = i < parts.length ? parts[i] : "";
        return out;
    }

    static boolean readMeta(Impl im) {
        SQLiteDatabase db = null;
        Cursor c = null;
        try {
            db = SQLiteDatabase.openDatabase(im.db.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            c = db.rawQuery("SELECT k, v FROM meta", null);
            String schema = null, rows = null, cols = null, name = null, hs = null, ts = null, sh = null;
            while (c.moveToNext()) {
                String k = c.getString(0), v = c.getString(1);
                if ("schema".equals(k)) schema = v;
                else if ("sheet".equals(k)) sh = v;
                else if ("name".equals(k)) name = v;
                else if ("rows".equals(k)) rows = v;
                else if ("cols".equals(k)) cols = v;
                else if ("headers".equals(k)) hs = v;
                else if ("types".equals(k)) ts = v;
            }
            if (!String.valueOf(SCHEMA).equals(schema)) return false;
            im.rows = rows == null ? 0 : Integer.parseInt(rows);
            im.cols = cols == null ? 0 : Integer.parseInt(cols);
            im.sheet = sh == null ? 1 : Integer.parseInt(sh);
            im.sheetName = name == null ? "" : name;
            im.headers = split(hs, im.cols);
            im.types = split(ts, im.cols);
            return im.rows > 0;
        } catch (Throwable t) {
            return false;
        } finally {
            try { if (c != null) c.close(); } catch (Throwable t) { }
            try { if (db != null) db.close(); } catch (Throwable t) { }
        }
    }

    static String map(Impl im) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(im.cols, 16);
        for (int i = 1; i <= limit; i++) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append('c').append(i);
            String h = (i - 1) < im.headers.length ? im.headers[i - 1] : "";
            if (h != null && h.length() > 0) {
                if (h.length() > 12) h = h.substring(0, 12);
                sb.append(' ').append(h);
            }
        }
        if (im.cols > limit) sb.append(String.format(Locale.ROOT, Words.RP_SHEET_MORE_COLS, im.cols));
        return sb.toString();
    }

    static boolean money(Impl im) {
        for (String t : im.types) if ("number".equals(t)) return true;
        return false;
    }

    /** 数值写成人看的样子：普通范围给全位数（不带科学计数法），极大极小才用「首位×10 的 n 次方」。 */
    static String num(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
        double a = Math.abs(d);
        if (a >= 1e-4 && a < 1e15) {
            java.math.BigDecimal bd = java.math.BigDecimal.valueOf(d);
            bd = bd.setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
            return bd.toPlainString();
        }
        int e = (int) Math.floor(Math.log10(a));
        double m = d / Math.pow(10, e);
        String ms = java.math.BigDecimal.valueOf(m).setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        return ms + "×10^" + e;
    }

    static boolean looksNum(String s) {
        if (s == null) return false;
        String v = s.trim();
        if (v.length() == 0) return false;
        return v.matches("-?[0-9]+(\\.[0-9]+)?") || v.matches("-?\\.[0-9]+");
    }
}
