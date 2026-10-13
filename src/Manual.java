package app.reticle;

import java.util.ArrayList;

/**
 * 47 号单（2026-10-12）· 说明书内容（唯一数据源；界面只写一套渲染）
 *
 * 原文＝{@link ManualDoc}（照抄《Reticle 使用手册》原文，机械转换、逐字不改）；
 * 本类只做「切章 ＋ 抽 9 条 ＋ 表格切格」，不含任何自撰文案。
 * 一处定义、两处渲染：引导最后一屏与详细说明页都读这里。
 */
public class Manual {

    /** 手册标题（取手册第一行「# …」） */
    public static final String TITLE;
    /** 手册头部一行（版本 ＋ 更新日期），目录页副标题用 */
    public static final String SUB;

    /** 第 N 章的编号（"1"…"10"） */
    public static final String[] CH_NUM;
    /** 第 N 章的标题（手册「## N. …」去掉编号） */
    public static final String[] CH_TITLE;
    /** 第 N 章的小节名（由该章「### 」小节名拼成，目录页副标题用） */
    public static final String[] CH_SUB;
    /** 第 N 章的正文 markdown（含「## 」标题行与各小节；块级解析见 ManualActivity） */
    public static final String[] CH_MD;

    /** 引导最后一屏的标题＝手册 §1.2 的小节名 */
    public static final String MIN_TITLE;
    public static final String MIN_SUB = "看到这里就够了；想看更多，回主界面点标题右边的「?」。";
    /** 9 条正文＝手册 §1.2 那张表格的 9 行（同一份文字，改手册两处齐） */
    public static final String[] MIN;

    static {
        String[] L = ManualDoc.L;
        String title = "", sub = "", minTitle = "";
        ArrayList<String> aNum = new ArrayList<String>();
        ArrayList<String> aTtl = new ArrayList<String>();
        ArrayList<String> aSub = new ArrayList<String>();
        ArrayList<String> aMd = new ArrayList<String>();
        ArrayList<String> aMin = new ArrayList<String>();

        int ci = -1;                       // 当前章下标
        StringBuilder cur = null;          // 当前章 markdown
        StringBuilder secs = null;         // 当前章的小节名
        boolean inMin = false;             // 正走在手册 §1.2 的表格里

        for (int i = 0; i < L.length; i++) {
            String s = L[i];
            if (s.startsWith("# ")) { title = s.substring(2).trim(); continue; }
            if (s.startsWith("**版本**")) { sub = clean(s); continue; }
            if (s.startsWith("**更新日期**")) { sub = (sub.length() == 0 ? "" : sub + " · ") + clean(s); continue; }
            if (s.startsWith("## ")) {
                if (ci >= 0 && cur != null) aMd.set(ci, cur.toString());   // 收上一章正文
                String h = s.substring(3).trim();
                if (h.equals("目录")) { cur = null; continue; }     // 界面自带目录页，手册这一节不重复渲染
                String n = String.valueOf(aTtl.size() + 1), nm = h;
                int sp = h.indexOf(' ');
                if (sp > 0) { n = h.substring(0, sp); nm = h.substring(sp + 1); }
                if (n.endsWith(".")) n = n.substring(0, n.length() - 1);
                aNum.add(n); aTtl.add(nm); aSub.add(""); aMd.add("");
                ci = aTtl.size() - 1;
                cur = new StringBuilder();
                secs = new StringBuilder();
                inMin = false;
                cur.append(s).append('\n');
                continue;
            }
            if (cur == null) continue;                               // 手册头部（# 标题／版本行）与目录节
            if (s.startsWith("### ")) {
                String sh = s.substring(4).trim();
                String sn = sh;
                int sp = sh.indexOf(' ');
                if (sp > 0) sn = sh.substring(sp + 1);
                if (secs.length() > 0) secs.append(" · ");
                secs.append(sn);
                aSub.set(ci, secs.toString());
                inMin = sh.startsWith("1.2 ");                        // 手册 §1.2 使用前须知
                if (inMin) minTitle = sn;
                cur.append(s).append('\n');
                continue;
            }
            cur.append(s).append('\n');
            if (inMin && s.startsWith("|")) {
                String[] cl = cells(s);
                if (cl.length >= 2 && !cl[0].equals("#") && cl[0].indexOf("--") < 0) aMin.add(cl[1]);
            }
        }

        if (ci >= 0 && cur != null) aMd.set(ci, cur.toString());           // 收最后一章正文
        TITLE = title.length() == 0 ? "Reticle 使用手册" : title;
        SUB = sub;
        CH_NUM = aNum.toArray(new String[0]);
        CH_TITLE = aTtl.toArray(new String[0]);
        CH_SUB = aSub.toArray(new String[0]);
        CH_MD = aMd.toArray(new String[0]);
        MIN_TITLE = minTitle.length() == 0 ? "使用前须知" : minTitle;
        MIN = aMin.toArray(new String[0]);
    }

    /** 去掉 markdown 的星号（头部「**版本** 0.27.0」→「版本 0.27.0」） */
    static String clean(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '*') continue;
            sb.append(s.charAt(i));
        }
        return sb.toString().trim();
    }

    /** 切表格一行：{@code | a | b |} → ["a","b"]（渲染器与 9 条抽取共用） */
    public static String[] cells(String s) {
        ArrayList<String> r = new ArrayList<String>();
        int i = (s != null && s.startsWith("|")) ? 1 : 0;
        StringBuilder cur = new StringBuilder();
        if (s == null) return new String[0];
        for (; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '|') { r.add(cur.toString().trim()); cur.setLength(0); }
            else cur.append(ch);
        }
        String last = cur.toString().trim();
        if (last.length() > 0) r.add(last);
        return r.toArray(new String[0]);
    }
}
