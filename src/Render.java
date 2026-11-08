package app.reticle;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * render 工具后端：把「内容」变成「成品文件」。
 *   md/html  →  png（WebView 渲染，任意 CSS/SVG，见 RenderActivity）
 *   html     →  html（原样落盘）
 *   md       →  pdf （PdfDocument + StaticLayout + 自带 TrueType 字体，见 PdfMake）
 */
public class Render {

    /** 最近一次产出的相对路径（相对 Home），给 MCP 回传缩略图用（只有 png 有）。 */
    public static volatile String lastRel = null;

    public static String run(Context ctx, JSONObject a) {
        long t0 = System.currentTimeMillis();
        lastRel = null;
        String md = a.optString("md", "");
        String html = a.optString("html", "");
        String format = a.optString("format", "png").trim().toLowerCase(Locale.ROOT);
        int width = a.optInt("width", 794);
        int scale = a.optInt("scale", 2);
        String out = a.optString("out", "").trim();
        String theme = a.optString("theme", "default").trim();
        if (theme.length() == 0) theme = "default";
        if (PdfMake.Theme.of(theme) == null)
            return "主题只认 default / poster / invoice（现在是 " + theme + "）";

        if (md.length() == 0 && html.length() == 0)
            return "缺内容：md（Markdown 原文）和 html（完整页面）至少给一个";
        if (width < 120) width = 794;
        if (width > 2000) width = 2000;
        if (scale < 1) scale = 2;
        if (scale > 4) scale = 4;

        File dir = Home.get(ctx);
        File outDir = new File(dir, "out");
        outDir.mkdirs();

        String base = out.length() == 0
            ? "render-" + new SimpleDateFormat("MMdd-HHmmss", Locale.ROOT).format(new Date())
            : out;
        base = base.replace("/", "_").replace("\\", "_");
        if (base.endsWith(".png") || base.endsWith(".html") || base.endsWith(".pdf"))
            base = base.substring(0, base.lastIndexOf('.'));

        if (format.equals("html")) {
            if (html.length() == 0)
                return "format=html 需要给完整 html（要 Markdown 请用 format=png，或先自己转成 HTML）";
            File f = new File(outDir, base + ".html");
            try {
                FileOutputStream fo = new FileOutputStream(f);
                fo.write(html.getBytes("UTF-8"));
                fo.close();
            } catch (Throwable t) {
                return "失败：写 HTML 出错 " + t;
            }
            return "已写出 HTML\n路径：" + f.getAbsolutePath() + "\n大小：" + f.length() + " B";
        }

        if (format.equals("pdf")) {
            if (md.length() == 0)
                return "format=pdf 只吃 md（Markdown）；html 请先自己转成 md，或改用 format=png 出图";
            File f = new File(outDir, base + ".pdf");
            lastRel = null;
            String err = PdfMake.build(ctx, md, width, f, theme);
            long ms = System.currentTimeMillis() - t0;
            if (err != null) return "生成 PDF 失败：" + err;
            StringBuilder sb = new StringBuilder();
            sb.append("已生成 PDF\n");
            sb.append("路径：").append(f.getAbsolutePath()).append("\n");
            sb.append("页数：").append(PdfMake.lastPages)
              .append("（A4 竖版，").append(Math.round(width * 0.75f)).append("×842 pt）\n");
            sb.append("大小：").append(f.length() / 1024).append(" KB\n");
            sb.append("主题：").append(PdfMake.lastTheme).append("\n");
            sb.append("字体：").append(PdfMake.lastFont).append("\n");
            if (PdfMake.lastLinks > 0)
                sb.append("链接：").append(PdfMake.lastLinks).append(" 个（可点击）\n");
            sb.append("耗时：").append(ms).append(" ms");
            return sb.toString();
        }

        if (!format.equals("png")) {
            return "format 只认 png / html / pdf（现在是 " + format + "）";
        }

        File png = new File(outDir, base + ".png");
        lastRel = "out/" + base + ".png";
        String err = RenderActivity.render(ctx, md, html, width, scale, png.getAbsolutePath(), theme);
        long ms = System.currentTimeMillis() - t0;
        if (err != null) return err.startsWith("渲染窗口没起来") ? err : ("渲染失败：" + err);
        if (!png.exists()) return "渲染失败：没有产出文件";

        StringBuilder sb = new StringBuilder();
        sb.append("已渲染 PNG\n");
        sb.append("路径：").append(png.getAbsolutePath()).append("\n");
        sb.append("主题：").append(theme).append("\n");
        sb.append("尺寸：").append(RenderActivity.sOutW).append("×").append(RenderActivity.sOutH)
          .append("（").append(width).append(" CSS px × scale ").append(scale).append("）\n");
        sb.append("大小：").append(png.length() / 1024).append(" KB\n");
        sb.append("耗时：").append(ms).append(" ms");
        if (RenderActivity.sNote != null) sb.append("\n提示：").append(RenderActivity.sNote);

        return sb.toString();
    }
}
