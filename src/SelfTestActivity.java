package app.reticle;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 38 号单（2026-10-10）· 动作自测靶（只由 MCP 工具 acttest 打开，界面上没有入口）。
 *
 * 目的：给 tap／长按／输入／滑动／返回 一个「点了也不打扰用户」的自家场地——
 * 以前助手不敢在真机上试动作类回话，只能靠读源码对照。
 * 自带自证：点击计数、输入框内容、窗口是否已关。
 * 窗口用的是系统对话框主题（小窗），用完点 back 自己关掉。
 */
public class SelfTestActivity extends Activity {

    public static volatile boolean ready = false;
    public static volatile boolean finished = false;
    public static volatile int btnX = 0, btnY = 0, editX = 0, editY = 0, w = 0, top = 0;
    public static volatile int tapCount = 0;
    public static volatile String typed = "";

    /** 由 acttest 工具调用（后台起 Activity，可能被系统拦 → 由调用方判断 ready）。 */
    public static void open(Context ctx) {
        try {
            ready = false; finished = false; tapCount = 0; typed = "";
            Intent i = new Intent(ctx, SelfTestActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            ctx.startActivity(i);
        } catch (Throwable t) { }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { requestWindowFeature(Window.FEATURE_NO_TITLE); } catch (Throwable t) { }
        final TextView tv = new TextView(this);
        tv.setText("Reticle 自测靶 · 点一下按钮");
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setPadding(20, 16, 20, 6);
        final Button btn = new Button(this);
        btn.setText("点我");
        btn.setAllCaps(false);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { tapCount++; tv.setText("Reticle 自测靶 · 点击计数 " + tapCount); }
        });
        final EditText ed = new EditText(this);
        ed.setHint("输入框（自测用）");
        ed.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        ed.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            public void onTextChanged(CharSequence s, int a, int c, int d) { }
            public void afterTextChanged(android.text.Editable e) { typed = e == null ? "" : e.toString(); }
        });
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setBackgroundColor(Color.WHITE);
        ll.setPadding(20, 20, 20, 20);
        ll.addView(tv, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ll.addView(btn, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ll.addView(ed, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(ll);
        ll.post(new Runnable() {
            public void run() {
                try {
                    int[] p1 = new int[2];
                    btn.getLocationOnScreen(p1);
                    btnX = p1[0] + btn.getWidth() / 2;
                    btnY = p1[1] + btn.getHeight() / 2;
                    int[] p2 = new int[2];
                    ed.getLocationOnScreen(p2);
                    editX = p2[0] + ed.getWidth() / 2;
                    editY = p2[1] + ed.getHeight() / 2;
                    w = ll.getWidth();
                    top = p1[1];
                    try { ed.requestFocus(); } catch (Throwable t) { }
                    ready = true;
                } catch (Throwable t) { }
            }
        });
    }

    @Override protected void onDestroy() {
        finished = true;
        ready = false;
        super.onDestroy();
    }
}
