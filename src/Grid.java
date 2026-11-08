package app.reticle;

import java.util.ArrayList;
import java.util.List;

/**
 * M4：查询结果与区域读取的共同中间态。
 * query 用它出 TSV，export 用它写 xlsx / csv，preview 用它排 PDF 表格。
 * 单元格一律存「给人看的字符串」（数值已经过 TableStore.num 处理，不会出现科学计数法）。
 */
public class Grid {

    public String[] cols = new String[0];
    public List<String[]> rows = new ArrayList<String[]>();
    /** 实际取到的行数（不含截断）。 */
    public int total = 0;
    /** 还有更多行没取（被 max_rows 截断）。 */
    public boolean more = false;

    private boolean[] num;
    private int[] dec;
    private boolean scanned = false;

    public int width() { return cols.length; }

    /** 逐列判断是不是「整列都是数值」，顺带记最大小数位。 */
    private void scan() {
        if (scanned) return;
        scanned = true;
        int n = cols.length;
        num = new boolean[n];
        dec = new int[n];
        for (int c = 0; c < n; c++) {
            boolean all = true;
            int seen = 0, d = 0;
            for (int r = 0; r < rows.size(); r++) {
                String[] row = rows.get(r);
                String v = (c < row.length && row[c] != null) ? row[c].trim() : "";
                if (v.length() == 0) continue;
                seen++;
                int k = decimals(v);
                if (k < 0) { all = false; break; }
                if (k > d) d = k;
            }
            num[c] = all && seen > 0;
            dec[c] = d > 6 ? 6 : d;
        }
    }

    /** 是数字就回小数位数（0 起），不是数字回 -1。前导零、超长整数一律当文本。 */
    public static int decimals(String v) {
        if (v == null) return -1;
        String s = v.trim();
        if (s.length() == 0 || s.length() > 24) return -1;
        int i = 0;
        if (s.charAt(0) == '-') i = 1;
        if (i >= s.length()) return -1;
        int dot = -1, digits = 0;
        for (int k = i; k < s.length(); k++) {
            char ch = s.charAt(k);
            if (ch == '.') {
                if (dot >= 0) return -1;
                dot = k;
            } else if (ch < '0' || ch > '9') {
                return -1;
            } else digits++;
        }
        if (digits == 0 || digits > 15) return -1;
        // 前导零：007 / 0123 是编号，不是数值
        int lead = i;
        while (lead < s.length() && s.charAt(lead) == '0') lead++;
        if (lead - i > 1 && lead < (dot < 0 ? s.length() : dot)) return -1;
        if (dot < 0) return 0;
        return s.length() - dot - 1;
    }

    public boolean isNumCol(int c) { scan(); return c >= 0 && c < num.length && num[c]; }

    public int decimalsOf(int c) { scan(); return (c >= 0 && c < dec.length) ? dec[c] : 0; }
}
