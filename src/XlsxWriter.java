package app.reticle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * M4：最小合法 xlsx 写出，零新增 jar。
 *
 * 组成就七个部件（[Content_Types].xml / _rels/.rels / workbook / workbook.rels / sheet1 / styles），
 * 字符串走 inlineStr（不建 sharedStrings），数值写真数字（WPS 才能继续算）。
 * 表头加粗带底色、全表细边框、冻结首行、自动筛选、列宽按内容估（中文按两格）。
 * 不写公式、不写合并、不写数据透视——M4 只出「能直接打开看和算」的表。
 */
public class XlsxWriter {

    public static void write(File out, String sheetName, Grid g) throws Exception {
        if (g == null || g.cols.length == 0) throw new Exception(Words.RP_SHEET_GRID_NONE);
        File dir = out.getParentFile();
        if (dir != null) dir.mkdirs();
        String sn = safeName(sheetName);
        int cols = g.cols.length;
        int nrows = g.rows.size();
        String last = XlsxReader.colName(cols) + (nrows + 1);

        ZipOutputStream zos = new ZipOutputStream(new java.io.BufferedOutputStream(new FileOutputStream(out), 1 << 16));
        try {
            put(zos, "[Content_Types].xml", CONTENT);
            put(zos, "_rels/.rels", RELS);
            put(zos, "xl/workbook.xml", String.format(Locale.ROOT, WORKBOOK, esc(sn)));
            put(zos, "xl/_rels/workbook.xml.rels", WBRELS);
            put(zos, "xl/styles.xml", STYLES);

            zos.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            Writer w = new OutputStreamWriter(zos, "UTF-8");
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
            w.write("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
            w.write("<dimension ref=\"A1:" + last + "\"/>");
            w.write("<sheetViews><sheetView tabSelected=\"1\" workbookViewId=\"0\">");
            w.write("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>");
            w.write("<selection pane=\"bottomLeft\" activeCell=\"A2\" sqref=\"A2\"/>");
            w.write("</sheetView></sheetViews>");
            w.write("<sheetFormatPr defaultRowHeight=\"16\"/>");
            w.write("<cols>");
            double[] wid = widths(g);
            for (int c = 0; c < cols; c++) {
                w.write("<col min=\"" + (c + 1) + "\" max=\"" + (c + 1) + "\" width=\""
                        + String.format(Locale.ROOT, "%.1f", wid[c]) + "\" customWidth=\"1\"/>");
            }
            w.write("</cols>");
            w.write("<sheetData>");
            w.write("<row r=\"1\" ht=\"20\" customHeight=\"1\">");
            for (int c = 0; c < cols; c++) {
                text(w, XlsxReader.colName(c + 1) + "1", 1, g.cols[c] == null ? "" : g.cols[c]);
            }
            w.write("</row>");
            for (int r = 0; r < nrows; r++) {
                String[] row = g.rows.get(r);
                w.write("<row r=\"" + (r + 2) + "\">");
                for (int c = 0; c < cols; c++) {
                    String v = (c < row.length && row[c] != null) ? row[c] : "";
                    if (v.length() == 0) continue;
                    String ref = XlsxReader.colName(c + 1) + (r + 2);
                    if (Grid.decimals(v) >= 0) {
                        w.write("<c r=\"" + ref + "\" s=\"" + (Grid.decimals(v) > 0 ? 4 : 3) + "\"><v>" + v + "</v></c>");
                    } else {
                        text(w, ref, 2, v);
                    }
                }
                w.write("</row>");
            }
            w.write("</sheetData>");
            w.write("<autoFilter ref=\"A1:" + last + "\"/>");
            w.write("</worksheet>");
            w.flush();
            zos.closeEntry();
        } finally {
            try { zos.close(); } catch (Throwable t) { }
        }
    }

    // ── 部件 ──

    static final String CONTENT = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
            + "</Types>";

    static final String RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
            + "</Relationships>";

    static final String WORKBOOK = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
            + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
            + "<sheets><sheet name=\"%s\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";

    static final String WBRELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
            + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
            + "</Relationships>";

    /** 0 默认｜1 表头｜2 文本｜3 整数（#,##0）｜4 两位小数（#,##0.00） */
    static final String STYLES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
            + "<fonts count=\"2\">"
            + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
            + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
            + "</fonts>"
            + "<fills count=\"3\">"
            + "<fill><patternFill patternType=\"none\"/></fill>"
            + "<fill><patternFill patternType=\"gray125\"/></fill>"
            + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFEDF1F6\"/><bgColor indexed=\"64\"/></patternFill></fill>"
            + "</fills>"
            + "<borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border>"
            + "<border><left style=\"thin\"><color indexed=\"64\"/></left><right style=\"thin\"><color indexed=\"64\"/></right>"
            + "<top style=\"thin\"><color indexed=\"64\"/></top><bottom style=\"thin\"><color indexed=\"64\"/></bottom><diagonal/></border></borders>"
            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
            + "<cellXfs count=\"5\">"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment vertical=\"center\"/></xf>"
            + "<xf numFmtId=\"3\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"right\" vertical=\"center\"/></xf>"
            + "<xf numFmtId=\"4\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"right\" vertical=\"center\"/></xf>"
            + "</cellXfs>"
            + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
            + "</styleSheet>";

    // ── 小件 ──

    static void put(ZipOutputStream zos, String name, String body) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(body.getBytes("UTF-8"));
        zos.closeEntry();
    }

    static void text(Writer w, String ref, int style, String v) throws IOException {
        String s = esc(v);
        w.write("<c r=\"" + ref + "\" s=\"" + style + "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">"
                + s + "</t></is></c>");
    }

    static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                default:
                    if (c == '\n' || c == '\r' || c == '\t') sb.append(' ');
                    else if (c < 0x20) { }
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 工作表名：去掉非法字符，最长 31，空则 Sheet1。 */
    static String safeName(String s) {
        String n = s == null ? "" : s.trim();
        n = n.replaceAll("[\\[\\]:*?/\\\\]", "_");
        if (n.length() > 31) n = n.substring(0, 31);
        if (n.length() == 0) n = "Sheet1";
        return n;
    }

    /** 列宽估算：中文与全角按 2，其余按 1.1，夹在 6 ~ 40。 */
    static double[] widths(Grid g) {
        int cols = g.cols.length;
        double[] w = new double[cols];
        for (int c = 0; c < cols; c++) w[c] = disp(g.cols[c]);
        int sample = Math.min(g.rows.size(), 200);
        for (int r = 0; r < sample; r++) {
            String[] row = g.rows.get(r);
            for (int c = 0; c < cols && c < row.length; c++) {
                double d = disp(row[c]);
                if (d > w[c]) w[c] = d;
            }
        }
        for (int c = 0; c < cols; c++) {
            double v = w[c] * 1.1 + 1.5;
            if (v < 6) v = 6;
            if (v > 40) v = 40;
            w[c] = v;
        }
        return w;
    }

    static double disp(String s) {
        if (s == null) return 0;
        double d = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            d += (c > 0x2E80) ? 2 : 1.05;
        }
        return d;
    }
}
