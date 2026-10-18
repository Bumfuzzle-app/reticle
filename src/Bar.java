package app.reticle;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * ★89 白条的手：白条**自己画**（粗细／长短可变），承载窗口的尺寸与位置一动不动。
 *
 * 两种用法：
 *   ① 窄窗里的贴边白条（贴左／贴右，自己算 x）——常态／按下反馈／弹一下／长按挤压都在这里画；
 *   ② 拖动层的悬浮白条（浮在指下，任意 x,y）——拖动期间整屏窗口一次开够，内部只平移画，
 *      **绝不每帧 updateViewLayout 改窗口**（88 已验证会抖）。
 *
 * 画法与 88 的 Morph 白条态同一套：圆角＝粗细/2，描边画在内侧，填充/描边颜色跟 Morph 参数走，
 * 所以「收放最后那一帧」与窄窗白条像素一致（不闪）。
 */
class Bar extends View {

    static final float LEN_DP = 80f;             // 白条基准长（＝Morph.barH）
    static final float BOOST_THICK_DP = 2.6f;    // 挤压/弹一下最多加粗多少 dp
    static final float BOOST_LEN_DP = 8f;        // 挤压/弹一下最多加长多少 dp

    private final Paint pF = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pS = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();
    private final float dens;

    private boolean right = true;        // 贴哪一侧（窄窗模式用）
    private boolean floating = false;    // true＝拖动层：按 fx/fy 画
    private float fx = 0f, fy = 0f;      // 悬浮白条的中心（屏幕坐标 px）
    private float boost = 0f;            // 0＝常态；1＝挤压到顶
    float baseThick = 0f, baseLen = 0f;  // px
    private int fillC = 0xFFFFFFFF, strokeC = 0xFFD5D7DB;
    private float strokePx = 1f;

    Bar(Context c) {
        super(c);
        pS.setStyle(Paint.Style.STROKE);
        pF.setStyle(Paint.Style.FILL);
        dens = c.getResources().getDisplayMetrics().density;
    }

    void setSide(boolean isRight) { right = isRight; invalidate(); }
    void setFloating(boolean f) { floating = f; invalidate(); }
    /** ★89.6 整改五：圆角参数。<0＝自动（半径＝粗细/2，圆头）；>=0＝指定（常规模式 2dp，恢复原样）。 */
    private float cornerPx = -1f;
    void setCornerPx(float v) { cornerPx = v; invalidate(); }
    float cornerVal() { return cornerPx; }
    /** ★89.6：整套外观照抄（拖动层与窄窗白条切换那一帧不跳）。 */
    void copyLookFrom(Bar o) {
        if (o == null) return;
        baseThick = o.baseThick; baseLen = o.baseLen;
        fillC = o.fillC; strokeC = o.strokeC; strokePx = o.strokePx;
        cornerPx = o.cornerPx; right = o.right; boost = o.boost;
        invalidate();
    }
    void setCenter(float x, float y) { fx = x; fy = y; invalidate(); }
    void setBoost(float b) { boost = b < 0f ? 0f : (b > 1f ? 1f : b); invalidate(); }
    float boost() { return boost; }

    void setMetrics(float thickPx, float lenPx) {
        baseThick = thickPx < 1f ? 1f : thickPx;
        baseLen = lenPx < 8f ? 8f : lenPx;
        invalidate();
    }

    void setStyle(int fill, int stroke, float swPx) {
        fillC = fill; strokeC = stroke;
        strokePx = swPx < 0.5f ? 0.5f : swPx;
        invalidate();
    }

    float thickPx() { return baseThick + dens * BOOST_THICK_DP * boost; }
    float lenPx() { return baseLen + dens * BOOST_LEN_DP * boost; }

    /** 白条中心 x（窄窗模式用；拖动层的中心由 Mascot 直接给）。 */
    static final float LIFT_DP = 1.6f;   // ★89.5 挤压/长按时离边一点（被拎起来），最大 1.6dp
    float liftPx() { return dens * LIFT_DP * boost; }

    float centerX() {
        if (floating) return fx;
        float t = thickPx();
        float lf = liftPx();
        return right ? getWidth() - t / 2f - lf : t / 2f + lf;
    }

    @Override
    protected void onDraw(Canvas cv) {
        try {
            float t = thickPx(), L = lenPx();
            if (t <= 0.5f || L <= 0.5f) return;
            float cx = floating ? fx : centerX();
            float cy = floating ? fy : getHeight() / 2f;
            float l = cx - t / 2f, r = cx + t / 2f;
            float tp = cy - L / 2f, bt = cy + L / 2f;
            float rad = (cornerPx >= 0f) ? Math.min(cornerPx, Math.min(t, L) / 2f) : Math.min(t, L) / 2f;

            float ins = Math.min(strokePx, Math.max(0f, Math.min(t, L) / 2f - 0.5f));
            if (ins > 0.05f) {
                rf.set(l + ins / 2f, tp + ins / 2f, r - ins / 2f, bt - ins / 2f);
                path.reset();
                path.addRoundRect(rf, Math.max(0f, rad - ins / 2f), Math.max(0f, rad - ins / 2f), Path.Direction.CW);
                pS.setStrokeWidth(ins);
                pS.setColor(strokeC);
                cv.drawPath(path, pS);
            }
            rf.set(l + ins, tp + ins, r - ins, bt - ins);
            if (rf.right > rf.left && rf.bottom > rf.top) {
                path.reset();
                path.addRoundRect(rf, Math.max(0f, rad - ins), Math.max(0f, rad - ins), Path.Direction.CW);
                pF.setColor(fillC);
                cv.drawPath(path, pF);
            }
        } catch (Throwable t) { }
    }
}
