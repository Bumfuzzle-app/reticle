package app.reticle;

import android.graphics.Bitmap;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

// ML Kit 中文文字识别（bundled 离线版 16.0.1）真实现。
// 依赖：物料/classes 里 ML Kit 全家桶 + 物料/assets 的 OCR 模型 + 物料/jni 的原生库。
public class OcrEng {
    public static final boolean AVAILABLE = true;
    private static TextRecognizer recognizer;

    private static synchronized TextRecognizer rec() {
        if (recognizer == null) {
            recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        }
        return recognizer;
    }

    public static String ocr(Bitmap bmp) {
        try {
            InputImage img = InputImage.fromBitmap(bmp, 0);
            Text t = Tasks.await(rec().process(img));
            StringBuilder sb = new StringBuilder();
            for (Text.TextBlock b : t.getTextBlocks()) {
                if (b.getText() != null) sb.append(b.getText()).append("\n");
            }
            return sb.toString();
        } catch (Throwable t) {
            return "ocr error: " + t;
        }
    }

    /** 52 号单 · 内存二级「收」：放掉 OCR 识别器（MLKit 中文模型，几十 MB 级）；下次用会自动重建。 */
    public static synchronized void drop() {
        try { if (recognizer != null) recognizer.close(); } catch (Throwable t) { }
        recognizer = null;
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') b.append("\\\\");
            else if (c == '"') b.append("\\\"");
            else if (c == '\n') b.append("\\n");
            else if (c == '\r') b.append("\\r");
            else if (c == '\t') b.append("\\t");
            else b.append(c);
        }
        return b.toString();
    }

    private static double r3(double v) { return Math.round(v * 1000.0) / 1000.0; }

    // 逐行 OCR，带框：框用"整帧像素坐标"（offX/offY 为裁剪偏移），另给归一化中心 cx/cy
    public static String linesJson(Bitmap bmp, int offX, int offY, int W, int H) {
        StringBuilder sb = new StringBuilder("[");
        try {
            InputImage img = InputImage.fromBitmap(bmp, 0);
            Text t = Tasks.await(rec().process(img));
            boolean first = true;
            for (Text.TextBlock b : t.getTextBlocks()) {
                for (Text.Line ln : b.getLines()) {
                    android.graphics.Rect r = ln.getBoundingBox();
                    String s = ln.getText();
                    if (r == null || s == null || s.length() == 0) continue;
                    int x = r.left + offX, y = r.top + offY;
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("{\"t\":\"").append(esc(s)).append("\",\"box\":[").append(x).append(",").append(y)
                      .append(",").append(r.width()).append(",").append(r.height()).append("],\"cx\":")
                      .append(r3((x + r.width() / 2.0) / W)).append(",\"cy\":")
                      .append(r3((y + r.height() / 2.0) / H)).append("}");
                }
            }
        } catch (Throwable t) { }
        sb.append("]");
        return sb.toString();
    }
}
