package app.reticle;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通道三 · 任务网引擎 v1（眼手正式工具内置，v0.5.0）
 * 语义蓝本＝归档/通道三早期与杂项/通道三-任务网设计笔记.txt（MaaFramework Pipeline 子集）：
 *   节点 = recognition + action + next/on_error + timeout/rate_limit + 冻结等待
 *   执行 = 识别驱动轮询（每轮一帧、按顺序评估候选），超时走 on_error，失败留现场
 * 识别器：always / tpl / color / fp        动作：click / longpress / swipe / wait / waitchange / waitstable / key / none
 * 坐标：一律 0~1 小数（>1 按像素）；记录分辨率 1440x3168。
 */
public class TaskEngine {

    static long nowMs() { return System.currentTimeMillis(); }

    static String safeName(String s) {
        if (s == null) return "x";
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    static String get(Map<String, String> kv, String k, String d) {
        String v = kv.get(k);
        return v == null ? d : v;
    }

    static int getInt(Map<String, String> kv, String k, int d) {
        try { return Integer.parseInt(kv.get(k).trim()); } catch (Throwable t) { return d; }
    }

    static double getDouble(Map<String, String> kv, String k, double d) {
        try { return Double.parseDouble(kv.get(k).trim()); } catch (Throwable t) { return d; }
    }

    static byte[] readAll(File f) throws Exception {
        InputStream in = new FileInputStream(f);
        try {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        } finally { in.close(); }
    }

    static void writeAll(File f, byte[] b) throws Exception {
        File d = f.getParentFile();
        if (d != null && !d.exists()) d.mkdirs();
        OutputStream o = new FileOutputStream(f);
        try { o.write(b); } finally { o.close(); }
    }

    static String readText(File f) throws Exception {
        return new String(readAll(f), StandardCharsets.UTF_8);
    }

    static void writeText(File f, String s) throws Exception {
        writeAll(f, s.getBytes(StandardCharsets.UTF_8));
    }

    static String hexOf(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) sb.append(String.format("%02x", b[i] & 0xff));
        return sb.toString();
    }

    static byte[] fromHex(String h) {
        int n = h.length() / 2;
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) b[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        return b;
    }

    static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    // 归一/像素坐标 → 像素矩形 [x,y,w,h]（值 ≤1 视作归一化）
    static int[] regionPx(JSONArray a, int W, int H) {
        if (a == null || a.length() < 4) return new int[]{0, 0, W, H};
        try {
            double x = a.getDouble(0), y = a.getDouble(1), w = a.getDouble(2), h = a.getDouble(3);
            int px = (int) Math.round(x <= 1.0 ? x * W : x);
            int py = (int) Math.round(y <= 1.0 ? y * H : y);
            int pw = (int) Math.round(w <= 1.0 ? w * W : w);
            int ph = (int) Math.round(h <= 1.0 ? h * H : h);
            px = clamp(px, 0, W - 1); py = clamp(py, 0, H - 1);
            pw = clamp(pw, 1, W - px); ph = clamp(ph, 1, H - py);
            return new int[]{px, py, pw, ph};
        } catch (Throwable t) { return new int[]{0, 0, W, H}; }
    }

    static int[] parseRect(String s, int W, int H) {
        try {
            String[] p = s.split(",");
            double x = Double.parseDouble(p[0].trim()), y = Double.parseDouble(p[1].trim());
            double w = Double.parseDouble(p[2].trim()), h = Double.parseDouble(p[3].trim());
            int px = (int) Math.round(x <= 1.0 ? x * W : x);
            int py = (int) Math.round(y <= 1.0 ? y * H : y);
            int pw = (int) Math.round(w <= 1.0 ? w * W : w);
            int ph = (int) Math.round(h <= 1.0 ? h * H : h);
            px = clamp(px, 0, W - 1); py = clamp(py, 0, H - 1);
            pw = clamp(pw, 1, W - px); ph = clamp(ph, 1, H - py);
            return new int[]{px, py, pw, ph};
        } catch (Throwable t) { return new int[]{0, 0, W, H}; }
    }

    // 单点颜色比对（RGB，无视 alpha）
    static boolean matchPx(int a, int b, int tol) {
        int dr = ((a >> 16) & 0xff) - ((b >> 16) & 0xff); if (dr < 0) dr = -dr; if (dr > tol) return false;
        int dg = ((a >> 8) & 0xff) - ((b >> 8) & 0xff); if (dg < 0) dg = -dg; if (dg > tol) return false;
        int db = (a & 0xff) - (b & 0xff); if (db < 0) db = -db; return db <= tol;
    }

    // 两帧同尺寸区域差异率（每像素 RGB 差和 >60 记一次差异）
    static double diffRatio(int[] a, int[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) return -1;
        int diff = 0, n = a.length;
        for (int i = 0; i < n; i++) {
            int p = a[i], q = b[i];
            int d = Math.abs((p & 0xff) - (q & 0xff))
                    + Math.abs(((p >> 8) & 0xff) - ((q >> 8) & 0xff))
                    + Math.abs(((p >> 16) & 0xff) - ((q >> 16) & 0xff));
            if (d > 60) diff++;
        }
        return (double) diff / n;
    }

    static int[] pixels(Bitmap b, int x, int y, int w, int h) {
        int[] arr = new int[w * h];
        b.getPixels(arr, 0, w, x, y, w, h);
        return arr;
    }

    // ================== 模型 ==================

    static class Node {
        String name;
        JSONObject rec;      // 识别（null=always）
        JSONObject act;      // 动作（null=none）
        List<String> next = new ArrayList<String>();
        List<String> onError = new ArrayList<String>();
        long timeout = 10000;
        long rateLimit = 250;
        long preDelay = 0, postDelay = 0;
        JSONObject preFreeze = null, postFreeze = null;
        boolean enabled = true;
        boolean inverse = false;
        boolean fail = false;
        String note = "";
    }

    static class Hit {
        boolean hit = false;
        double score = 0;
        int[] box = null;     // 像素 [x,y,w,h]
        String detail = "";
        String nodeName = "";
    }

    static class Scan {
        String node;
        Hit hit;
        HashMap<String, Double> lastScores = new HashMap<String, Double>();
    }

    // ================== 运行上下文 ==================

    static class Run {
        VeriService svc;
        Map<String, Node> nodes = new HashMap<String, Node>();
        String start = null;
        long t0 = 0;
        long maxSec = 240;
        boolean dry = false;
        boolean saveFrames = false;
        String label = "chain";
        File root;
        File logFile;
        int frameCount = 0;
        boolean aborted = false;
        List<String> scenes = new ArrayList<String>();
        JSONArray steps = new JSONArray();
        HashMap<String, JSONObject> fpCache = new HashMap<String, JSONObject>();
        HashMap<String, Bitmap> tplCache = new HashMap<String, Bitmap>();
        HashMap<String, Double> lastScores = new HashMap<String, Double>();
        boolean timedOut = false;

        void log(String s) {
            try {
                String line = "[+" + (SystemClock.uptimeMillis() - t0) + "ms] " + s + "\n";
                FileOutputStream o = new FileOutputStream(logFile, true);
                o.write(line.getBytes(StandardCharsets.UTF_8));
                o.close();
            } catch (Throwable t) { }
            svc.log("chain " + s);
        }

        boolean overTime() {
            if (SystemClock.uptimeMillis() - t0 > maxSec * 1000L) { timedOut = true; aborted = true; return true; }
            return false;
        }

        void parse(JSONObject plan) throws Exception {
            start = plan.optString("start", "");
            JSONObject ns = plan.getJSONObject("nodes");
            java.util.Iterator it = ns.keys();
            while (it.hasNext()) {
                String k = String.valueOf(it.next());
                JSONObject o = ns.getJSONObject(k);
                Node n = new Node();
                n.name = k;
                n.rec = o.optJSONObject("recognition");
                n.act = o.optJSONObject("action");
                JSONArray nx = o.optJSONArray("next");
                if (nx != null) for (int i = 0; i < nx.length(); i++) n.next.add(nx.getString(i));
                JSONArray oe = o.optJSONArray("on_error");
                if (oe != null) for (int i = 0; i < oe.length(); i++) n.onError.add(oe.getString(i));
                n.timeout = o.optLong("timeout", 10000);
                n.rateLimit = o.optLong("rate_limit", 250);
                n.preDelay = o.optLong("pre_delay", 0);
                n.postDelay = o.optLong("post_delay", 0);
                n.preFreeze = o.optJSONObject("pre_wait_freezes");
                n.postFreeze = o.optJSONObject("post_wait_freezes");
                n.enabled = o.optBoolean("enabled", true);
                n.inverse = o.optBoolean("inverse", false);
                n.fail = o.optBoolean("fail", false);
                n.note = o.optString("note", "");
                nodes.put(k, n);
            }
            if (start.isEmpty() || !nodes.containsKey(start)) throw new Exception(Words.RP_NODE_MISSING + start);
        }

        Node node(String name) { return nodes.get(name); }

    static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': b.append("\\\\"); break;
                case '"': b.append("\\\""); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 32) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.toString();
    }

    static double round3d(double d) { return Math.round(d * 1000.0) / 1000.0; }

    static JSONArray arr(int[] v) {
        JSONArray a = new JSONArray();
        if (v != null) for (int i = 0; i < v.length; i++) a.put(v[i]);
        return a;
    }

    static JSONObject mapToJson(Map<String, Double> m) {
        JSONObject o = new JSONObject();
        if (m != null) {
            for (Map.Entry<String, Double> e : m.entrySet()) {
                try { o.put(e.getKey(), e.getValue() == null ? -1 : round3d(e.getValue().doubleValue())); } catch (Throwable t) { }
            }
        }
        return o;
    }

    static List<String> single(String s) {
        List<String> l = new ArrayList<String>();
        l.add(s);
        return l;
    }

    static void sleepQ(long ms) {
        if (ms <= 0) return;
        try { Thread.sleep(ms); } catch (InterruptedException e) { }
    }

    // ================== 对外命令：chain ==================

    public static String cmdChain(VeriService svc, Map<String, String> kv) {
        try {
            String planStr = get(kv, "plan", "");
            if (planStr == null || planStr.trim().isEmpty()) return "{\"ok\":0,\"err\":\"missing plan\"}";
            String label = safeName(get(kv, "label", "chain"));
            Run r = new Run();
            r.svc = svc;
            r.root = svc.root;
            r.label = label + "-" + nowMs();
            r.dry = "1".equals(get(kv, "dry", ""));
            r.saveFrames = "1".equals(get(kv, "frames", ""));
            r.maxSec = getInt(kv, "max_sec", 240);
            planStr = planStr.trim();
            JSONObject plan;
            if (planStr.startsWith("{")) plan = new JSONObject(planStr);
            else plan = new JSONObject(readText(new File(svc.root, planStr)));
            r.parse(plan);
            r.logFile = new File(svc.root, "logs/chain-" + r.label + ".log");
            r.log("run-start label=" + r.label + " dry=" + r.dry + " maxSec=" + r.maxSec + " nodes=" + r.nodes.size());
            r.t0 = SystemClock.uptimeMillis();
            JSONObject res = r.run();
            r.log("run-end status=" + res.optString("status"));
            try { writeText(new File(svc.root, "chain/result-" + r.label + ".json"), res.toString(2)); } catch (Throwable t) { }
            return res.toString();
        } catch (Throwable t) {
            return "{\"ok\":0,\"err\":\"chain exception: " + esc(String.valueOf(t)) + "\"}";
        }
    }

    // ================== 主循环 ==================

    JSONObject run() {
        JSONObject res = new JSONObject();
        Node cur = node(start);
        Scan sc = awaitList(single(cur.name), cur, cur.timeout);
        if (sc == null) {
            sc = handleOnError(cur, "entry-timeout");
            if (sc == null) return finishFail(res, cur, "entry-timeout");
            cur = node(sc.node);
        }
        while (true) {
            if (overTime()) return finishFail(res, cur, "max-sec");
            if (cur.fail) return finishFail(res, cur, "explicit-fail");
            JSONObject step = new JSONObject();
            try {
                step.put("i", steps.length() + 1);
                step.put("node", cur.name);
                if (cur.note != null && cur.note.length() > 0) step.put("note", cur.note);
                if (sc != null && sc.hit != null) {
                    step.put("score", round3d(sc.hit.score));
                    if (sc.hit.box != null) step.put("box", arr(sc.hit.box));
                    if (sc.hit.detail != null && sc.hit.detail.length() > 0) step.put("rec", sc.hit.detail);
                }
                if (saveFrames) saveFrame("f" + step.optInt("i") + "-" + safeName(cur.name));
                freeze(cur.preFreeze, "pre@" + cur.name);
                sleepQ(cur.preDelay);
                String actType = cur.act == null ? "none" : cur.act.optString("type", "none");
                step.put("action", actType);
                ActionRes ar;
                if (dry) {
                    ar = new ActionRes();
                    ar.ok = true;
                    ar.ms = 0;
                    ar.detail = "dry-skip";
                } else {
                    ar = doAction(cur, sc == null ? null : sc.hit);
                }
                step.put("ok", ar.ok ? 1 : 0);
                if (ar.ms > 0) step.put("ms", ar.ms);
                if (ar.detail != null && ar.detail.length() > 0) step.put("detail", ar.detail);
            } catch (Throwable t) {
                try { step.put("err", String.valueOf(t)); } catch (Throwable t2) { }
            }
            steps.put(step);
            log("step " + step.optInt("i") + " " + cur.name + " act=" + step.optString("action")
                    + " ok=" + step.optInt("ok") + (step.has("detail") ? (" " + step.optString("detail")) : ""));
            boolean actOk = step.optInt("ok", 0) == 1;
            if (!actOk) {
                Scan fsc = handleOnError(cur, "action-fail");
                if (fsc == null) return finishFail(res, cur, "action-fail");
                cur = node(fsc.node);
                sc = fsc;
                continue;
            }
            freeze(cur.postFreeze, "post@" + cur.name);
            sleepQ(cur.postDelay);
            if (cur.next == null || cur.next.isEmpty()) return finishDone(res, cur);
            Scan nsc = awaitList(cur.next, cur, cur.timeout);
            if (nsc == null) {
                Scan fsc = handleOnError(cur, "next-timeout");
                if (fsc == null) return finishFail(res, cur, "next-timeout");
                cur = node(fsc.node);
                sc = fsc;
                continue;
            }
            cur = node(nsc.node);
            sc = nsc;
        }
    }

    Scan handleOnError(Node at, String why) {
        if (aborted) return null;
        if (at.onError == null || at.onError.isEmpty()) {
            log("on_error empty @" + at.name + " (" + why + ")");
            return null;
        }
        log("fallback @" + at.name + " (" + why + ") -> " + at.onError.toString());
        Scan sc = awaitList(at.onError, at, at.timeout);
        if (sc == null) log("on_error all-miss @" + at.name);
        return sc;
    }

    Scan awaitList(List<String> names, Node parent, long budget) {
        long deadline = SystemClock.uptimeMillis() + budget;
        while (SystemClock.uptimeMillis() < deadline) {
            if (overTime()) return null;
            VeriService.Shot s = svc.takeShotSync();
            if (s == null || s.bmp == null) { sleepQ(120); continue; }
            frameCount++;
            try {
                for (int i = 0; i < names.size(); i++) {
                    String nm = names.get(i);
                    Node n = nodes.get(nm);
                    if (n == null || !n.enabled) continue;
                    Hit h = evalNode(n, s.bmp);
                    lastScores.put(nm, round3d(h.score));
                    if (n.inverse) h.hit = !h.hit;
                    if (h.hit) {
                        Scan sc = new Scan();
                        sc.node = nm;
                        sc.hit = h;
                        h.nodeName = nm;
                        sc.lastScores = new HashMap<String, Double>(lastScores);
                        return sc;
                    }
                }
            } finally { s.bmp.recycle(); }
            sleepQ(parent == null ? 150 : Math.max(0, parent.rateLimit));
        }
        return null;
    }

    JSONObject finishDone(JSONObject res, Node cur) {
        try {
            res.put("ok", 1);
            res.put("status", "done");
            res.put("end_node", cur.name);
            fillCommon(res);
            if (saveFrames) saveFrame("end-done");
            log("DONE @" + cur.name);
        } catch (Throwable t) { }
        return res;
    }

    JSONObject finishFail(JSONObject res, Node cur, String why) {
        try {
            res.put("ok", 0);
            res.put("status", timedOut ? "timeout" : "fail");
            res.put("fail_node", cur == null ? "" : cur.name);
            res.put("why", why);
            String scene = saveScene(cur, why);
            if (scene != null) scenes.add(scene);
            fillCommon(res);
            res.put("last_scores", mapToJson(lastScores));
            log("FAIL " + why + " @" + (cur == null ? "" : cur.name) + " scene=" + scene);
        } catch (Throwable t) { }
        return res;
    }

    void fillCommon(JSONObject res) {
        try {
            res.put("elapsed_ms", SystemClock.uptimeMillis() - t0);
            res.put("frames", frameCount);
            res.put("steps", steps);
            res.put("scenes", new JSONArray(scenes));
            res.put("log", "logs/chain-" + label + ".log");
            res.put("label", label);
        } catch (Throwable t) { }
    }

    String saveFrame(String tag) {
        try {
            VeriService.Shot s = svc.takeShotSync();
            if (s.bmp == null) return null;
            File f = new File(root, "chain/" + label + "-" + safeName(tag) + ".jpg");
            svc.saveJpeg(s.bmp, f, 75);
            s.bmp.recycle();
            return "chain/" + f.getName();
        } catch (Throwable t) { return null; }
    }

    String saveScene(Node cur, String why) {
        try {
            VeriService.Shot s = svc.takeShotSync();
            if (s.bmp == null) return null;
            String base = "fail-" + label + "-" + safeName(why);
            File jf = new File(root, "chain/" + base + ".jpg");
            svc.saveJpeg(s.bmp, jf, 75);
            s.bmp.recycle();
            JSONObject meta = new JSONObject();
            meta.put("label", label);
            meta.put("why", why);
            meta.put("node", cur == null ? "" : cur.name);
            meta.put("last_scores", mapToJson(lastScores));
            meta.put("ts", nowMs());
            writeText(new File(root, "chain/" + base + ".json"), meta.toString(2));
            return "chain/" + jf.getName();
        } catch (Throwable t) { return null; }
    }

    // ================== 识别器 ==================

    Hit evalNode(Node n, Bitmap frame) {
        Hit h = new Hit();
        if (n.rec == null) { h.hit = true; h.score = 1.0; h.detail = "always"; return h; }
        String type = n.rec.optString("type", "always");
        if ("always".equals(type)) { h.hit = true; h.score = 1.0; h.detail = "always"; return h; }
        if ("tpl".equals(type)) return recTpl(n.rec, frame);
        if ("color".equals(type)) return recColor(n.rec, frame);
        if ("fp".equals(type)) return recFp(n.rec, frame);
        h.detail = "unknown-rec:" + type;
        return h;
    }

    static class TplHit {
        int[] box;
        double score;
    }

    Hit recTpl(JSONObject rec, Bitmap frame) {
        Hit miss = new Hit();
        try {
            List<String> tpls = new ArrayList<String>();
            JSONArray ta = rec.optJSONArray("tpl");
            if (ta != null) { for (int i = 0; i < ta.length(); i++) tpls.add(ta.getString(i)); }
            else { String t1 = rec.optString("tpl", ""); if (t1.length() > 0) tpls.add(t1); }
            if (tpls.isEmpty()) { miss.detail = "tpl-missing-param"; return miss; }
            int W = frame.getWidth(), H = frame.getHeight();
            int[] roi = rec.has("roi") ? regionPx(rec.optJSONArray("roi"), W, H) : new int[]{0, 0, W, H};
            int tol = rec.optInt("tol", 0);
            double minScore = rec.optDouble("min_score", 0.9);
            List<Double> scales = new ArrayList<Double>();
            JSONArray sa = rec.optJSONArray("scales");
            if (sa != null) { for (int i = 0; i < sa.length(); i++) scales.add(sa.getDouble(i)); }
            else { String s1 = rec.optString("scales", ""); if (s1.length() > 0) scales.add(Double.parseDouble(s1)); }
            if (scales.isEmpty()) scales.add(Double.valueOf(1.0));
            StringBuilder tried = new StringBuilder();
            for (int ti = 0; ti < tpls.size(); ti++) {
                String path = tpls.get(ti);
                Bitmap t = tplCache.get(path);
                if (t == null) {
                    File tf = new File(root, path);
                    t = BitmapFactory.decodeFile(tf.getAbsolutePath());
                    if (t != null) tplCache.put(path, t);
                }
                if (t == null) { tried.append(" tpl-missing:").append(path); continue; }
                for (int si = 0; si < scales.size(); si++) {
                    double s = scales.get(si).doubleValue();
                    Bitmap use = t;
                    if (Math.abs(s - 1.0) > 0.001) {
                        int nw = Math.max(2, (int) Math.round(t.getWidth() * s));
                        int nh = Math.max(2, (int) Math.round(t.getHeight() * s));
                        use = Bitmap.createScaledBitmap(t, nw, nh, true);
                    }
                    Hit h = searchTpl(frame, roi, use, tol, minScore, 1);
                    if (use != t) use.recycle();
                    if (h.hit) {
                        h.detail = "tpl:" + path + "@" + s + " " + h.detail;
                        return h;
                    }
                    tried.append(" ").append(path).append("@").append(s).append(":").append(h.detail);
                }
            }
            miss.detail = "no-match" + tried.toString();
        } catch (Throwable t) {
            miss.detail = "tpl-err:" + String.valueOf(t);
        }
        return miss;
    }

    Hit searchTpl(Bitmap frame, int[] roi, Bitmap tpl, int tol, double minScore, int want) {
        List<TplHit> hits = searchTplList(frame, roi, tpl, tol, minScore, want);
        Hit out = new Hit();
        if (hits.isEmpty()) { out.detail = "scan-zero"; return out; }
        TplHit b = hits.get(0);
        out.hit = true;
        out.score = b.score;
        out.box = b.box;
        out.detail = String.format("score=%.3f box=[%d,%d,%d,%d]", b.score, b.box[0], b.box[1], b.box[2], b.box[3]);
        return out;
    }

    static List<TplHit> searchTplList(Bitmap frame, int[] roi, Bitmap tpl, int tol, double minScore, int cap) {
        List<TplHit> out = new ArrayList<TplHit>();
        try {
            int rx = roi[0], ry = roi[1], rw = roi[2], rh = roi[3];
            int tw = tpl.getWidth(), th = tpl.getHeight();
            if (tw < 2 || th < 2 || tw > rw || th > rh) return out;
            int[] fp = pixels(frame, rx, ry, rw, rh);
            int[] tp = pixels(tpl, 0, 0, tw, th);
            int[] sxs = new int[]{0, tw - 1, 0, tw - 1, tw / 2, tw / 2, tw / 2, 0, tw - 1};
            int[] sys = new int[]{0, 0, th - 1, th - 1, th / 2, 0, th - 1, th / 2, th / 2};
            int nSample = (tw >= 8 && th >= 8) ? 9 : 1;
            int total = tw * th;
            int allowed = (int) Math.floor((1.0 - minScore) * total);
            for (int row = 0; row <= rh - th; row++) {
                int base = row * rw;
                for (int col = 0; col <= rw - tw; col++) {
                    if (!matchPx(fp[base + col], tp[0], tol)) continue;
                    boolean ok = true;
                    for (int k = 1; k < nSample; k++) {
                        int tii = sys[k] * tw + sxs[k];
                        if (!matchPx(fp[base + sys[k] * rw + col + sxs[k]], tp[tii], tol)) { ok = false; break; }
                    }
                    if (!ok) continue;
                    int mism = 0;
                    for (int i = 0; i < th && mism <= allowed; i++) {
                        int fb = base + i * rw + col, tb = i * tw;
                        for (int j = 0; j < tw; j++) {
                            if (!matchPx(fp[fb + j], tp[tb + j], tol)) { mism++; if (mism > allowed) break; }
                        }
                    }
                    if (mism > allowed) continue;
                    TplHit h = new TplHit();
                    h.box = new int[]{rx + col, ry + row, tw, th};
                    h.score = 1.0 - (double) mism / total;
                    boolean dup = false;
                    for (int d = 0; d < out.size(); d++) {
                        int[] ob = out.get(d).box;
                        if (Math.abs(ob[0] - h.box[0]) <= 4 && Math.abs(ob[1] - h.box[1]) <= 4) { dup = true; break; }
                    }
                    if (!dup) out.add(h);
                    if (out.size() >= Math.max(1, cap) * 4) return out;
                }
            }
            java.util.Collections.sort(out, new java.util.Comparator<TplHit>() {
                public int compare(TplHit a, TplHit b) { return Double.compare(b.score, a.score); }
            });
            if (out.size() > cap) out = new ArrayList<TplHit>(out.subList(0, cap));
        } catch (Throwable t) { }
        return out;
    }

    Hit recColor(JSONObject rec, Bitmap frame) {
        Hit out = new Hit();
        try {
            int W = frame.getWidth(), H = frame.getHeight();
            int tr = 255, tg = 0, tb = 0;
            String rs = rec.optString("rgb", "");
            if (rs.length() > 0 && rs.startsWith("#")) {
                String h = rs.substring(1);
                if (h.length() >= 6) {
                    tr = Integer.parseInt(h.substring(0, 2), 16);
                    tg = Integer.parseInt(h.substring(2, 4), 16);
                    tb = Integer.parseInt(h.substring(4, 6), 16);
                }
            } else {
                JSONArray ra = rec.optJSONArray("rgb");
                if (ra != null && ra.length() >= 3) { tr = ra.getInt(0); tg = ra.getInt(1); tb = ra.getInt(2); }
            }
            int[] roi = rec.has("roi") ? regionPx(rec.optJSONArray("roi"), W, H) : new int[]{0, 0, W, H};
            int tol = rec.optInt("tol", 12);
            int min = rec.optInt("min", 25);
            int rx = roi[0], ry = roi[1], rw = roi[2], rh = roi[3];
            int[] px = pixels(frame, rx, ry, rw, rh);
            int count = 0;
            int minx = Integer.MAX_VALUE, miny = Integer.MAX_VALUE, maxx = -1, maxy = -1;
            for (int i = 0; i < px.length; i++) {
                int c = px[i];
                int dr = ((c >> 16) & 0xff) - tr; if (dr < 0) dr = -dr; if (dr > tol) continue;
                int dg = ((c >> 8) & 0xff) - tg; if (dg < 0) dg = -dg; if (dg > tol) continue;
                int db = (c & 0xff) - tb; if (db < 0) db = -db; if (db > tol) continue;
                count++;
                int x = i % rw, y = i / rw;
                if (x < minx) minx = x;
                if (x > maxx) maxx = x;
                if (y < miny) miny = y;
                if (y > maxy) maxy = y;
            }
            if (count >= min && maxx >= 0) {
                out.hit = true;
                out.score = Math.min(1.0, (double) count / Math.max(1, min));
                out.box = new int[]{rx + minx, ry + miny, maxx - minx + 1, maxy - miny + 1};
                out.detail = "color=#" + String.format("%02x%02x%02x", tr, tg, tb) + " count=" + count
                        + " bbox=[" + out.box[0] + "," + out.box[1] + "," + out.box[2] + "," + out.box[3] + "]";
            } else {
                out.detail = "color-count=" + count + "<" + min;
            }
        } catch (Throwable t) {
            out.detail = "color-err:" + String.valueOf(t);
        }
        return out;
    }

    // ---- fp：区域指纹（多区域组合，灰度网格比对）----

    Hit recFp(JSONObject rec, Bitmap frame) {
        Hit out = new Hit();
        try {
            String name = rec.optString("name", "");
            int tol = rec.optInt("tol", 18);
            JSONObject fp = null;
            if (name.length() > 0) {
                fp = fpCache.get(name);
                if (fp == null) {
                    File f = new File(root, name);
                    if (!f.exists()) { out.detail = "fp-missing:" + name; return out; }
                    fp = new JSONObject(readText(f));
                    fpCache.put(name, fp);
                }
            } else if (rec.has("ref")) {
                fp = rec.optJSONObject("ref");
            }
            if (fp == null) { out.detail = "fp-missing-param"; return out; }
            int W = frame.getWidth(), H = frame.getHeight();
            int gw = fp.optInt("gw", 16), gh = fp.optInt("gh", 16);
            JSONArray parts = fp.getJSONArray("parts");
            double worst = 0;
            boolean all = parts.length() > 0;
            StringBuilder det = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                JSONObject p = parts.getJSONObject(i);
                int[] rect = regionPx(p.getJSONArray("region"), W, H);
                byte[] cur = gridOf(frame, rect, gw, gh);
                byte[] ref = fromHex(p.getString("data"));
                double md = meanAbsDiff(cur, ref);
                if (md > worst) worst = md;
                if (md > tol) all = false;
                det.append(" p").append(i).append("=").append(String.format("%.1f", md));
            }
            out.hit = all;
            out.score = 1.0 - Math.min(1.0, worst / 255.0);
            if (out.hit) out.box = regionPx(parts.getJSONObject(0).getJSONArray("region"), W, H);
            out.detail = "fp:" + name + det.toString() + " tol=" + tol;
        } catch (Throwable t) {
            out.detail = "fp-err:" + String.valueOf(t);
        }
        return out;
    }

    // 灰度网格（gw x gh 块平均亮度）
    static byte[] gridOf(Bitmap frame, int[] rect, int gw, int gh) {
        int x = rect[0], y = rect[1], w = rect[2], h = rect[3];
        byte[] out = new byte[gw * gh];
        int[] px = pixels(frame, x, y, w, h);
        for (int j = 0; j < gh; j++) {
            int sy = (int) ((long) j * h / gh);
            int ey = (int) ((long) (j + 1) * h / gh);
            if (ey <= sy) ey = sy + 1;
            if (ey > h) ey = h;
            for (int i = 0; i < gw; i++) {
                int sx = (int) ((long) i * w / gw);
                int ex = (int) ((long) (i + 1) * w / gw);
                if (ex <= sx) ex = sx + 1;
                if (ex > w) ex = w;
                long sum = 0;
                int cnt = 0;
                for (int yy = sy; yy < ey; yy++) {
                    int row = yy * w;
                    for (int xx = sx; xx < ex; xx++) {
                        int c = px[row + xx];
                        int lum = (((c >> 16) & 0xff) * 299 + ((c >> 8) & 0xff) * 587 + (c & 0xff) * 114) / 1000;
                        sum += lum;
                        cnt++;
                    }
                }
                out[j * gw + i] = (byte) (cnt == 0 ? 0 : (sum / cnt));
            }
        }
        return out;
    }

    static double meanAbsDiff(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) return 255;
        long s = 0;
        for (int i = 0; i < a.length; i++) s += Math.abs((a[i] & 0xff) - (b[i] & 0xff));
        return (double) s / a.length;
    }

    // ================== 动作 ==================

    static class ActionRes {
        boolean ok = false;
        long ms = 0;
        String detail = "";
    }

    static String fmtPt(double[] pt) { return String.format("(%d,%d)", (int) pt[0], (int) pt[1]); }

    static double optCoord(JSONObject a, String k, double d, int base) {
        double v = a.optDouble(k, d);
        return v <= 1.0 ? v * base : v;
    }

    double[] target(JSONObject a, Hit hit) {
        int W = svc.screenW, H = svc.screenH;
        double px, py;
        if (a.has("x") && a.has("y")) {
            double x = a.optDouble("x", 0), y = a.optDouble("y", 0);
            px = x <= 1.0 ? x * W : x;
            py = y <= 1.0 ? y * H : y;
        } else if (hit != null && hit.box != null) {
            px = hit.box[0] + hit.box[2] / 2.0;
            py = hit.box[1] + hit.box[3] / 2.0;
        } else {
            return null;
        }
        JSONArray off = a.optJSONArray("offset");
        if (off != null && off.length() >= 2) {
            px += off.optDouble(0, 0) * W;
            py += off.optDouble(1, 0) * H;
        }
        return new double[]{px, py};
    }

    ActionRes doAction(Node n, Hit hit) {
        ActionRes r = new ActionRes();
        long tA = SystemClock.uptimeMillis();
        try {
            svc.ensureScreen();
            if (n.act == null) { r.ok = true; r.detail = "none"; return r; }
            JSONObject a = n.act;
            String type = a.optString("type", "none");
            int W = svc.screenW, H = svc.screenH;
            if ("click".equals(type)) {
                double[] pt = target(a, hit);
                if (pt == null) { r.detail = "no-target"; return r; }
                long dur = a.optLong("ms", 60);
                VeriService.GRes g = svc.runGesture(svc.clickStroke((float) pt[0], (float) pt[1], dur));
                r.ok = g.ok;
                r.detail = "click" + fmtPt(pt) + " dispatch=" + g.ms + "ms";
            } else if ("longpress".equals(type)) {
                double[] pt = target(a, hit);
                if (pt == null) { r.detail = "no-target"; return r; }
                long dur = a.optLong("ms", 800);
                VeriService.GRes g = svc.runGesture(svc.clickStroke((float) pt[0], (float) pt[1], dur));
                r.ok = g.ok;
                r.detail = "longpress" + fmtPt(pt) + "(" + dur + "ms) dispatch=" + g.ms + "ms";
            } else if ("swipe".equals(type)) {
                float x1 = (float) optCoord(a, "x1", 0.5, W);
                float y1 = (float) optCoord(a, "y1", 0.5, H);
                float x2 = (float) optCoord(a, "x2", 0.5, W);
                float y2 = (float) optCoord(a, "y2", 0.5, H);
                long dur = a.optLong("ms", 300);
                VeriService.GRes g = svc.runGesture(svc.swipeStroke(x1, y1, x2, y2, dur));
                r.ok = g.ok;
                r.detail = String.format("swipe(%d,%d)->(%d,%d) dispatch=%dms", (int) x1, (int) y1, (int) x2, (int) y2, g.ms);
            } else if ("wait".equals(type)) {
                long ms = a.optLong("ms", 0);
                if (ms <= 0) ms = (long) (a.optDouble("sec", 0.8) * 1000);
                if (ms < 0) ms = 0;
                if (ms > 15000) ms = 15000;
                sleepQ(ms);
                r.ok = true;
                r.detail = "wait " + ms + "ms";
            } else if ("waitchange".equals(type)) {
                return actWaitChange(a, W, H);
            } else if ("waitstable".equals(type)) {
                return actWaitStable(a, W, H);
            } else if ("key".equals(type)) {
                String k = a.optString("key", "back");
                boolean ok;
                if ("home".equals(k)) ok = svc.actGlobalHome();
                else if ("recents".equals(k)) ok = svc.actGlobalRecents();
                else ok = svc.actGlobalBack();
                r.ok = ok;
                r.detail = "key:" + k;
            } else if ("none".equals(type)) {
                r.ok = true;
                r.detail = "none";
            } else {
                r.detail = "unknown-act:" + type;
            }
        } catch (Throwable t) {
            r.detail = "act-err:" + String.valueOf(t);
        }
        r.ms = SystemClock.uptimeMillis() - tA;
        return r;
    }

    ActionRes actWaitChange(JSONObject a, int W, int H) {
        ActionRes r = new ActionRes();
        long tA = SystemClock.uptimeMillis();
        try {
            int[] rect = a.has("region") ? regionPx(a.optJSONArray("region"), W, H) : new int[]{0, 0, W, H};
            long timeout = a.optLong("timeout", 3000);
            double thresh = a.optDouble("thresh", 0.02);
            VeriService.Shot s0 = svc.takeShotSync();
            if (s0.bmp == null) { r.detail = "before-shot-fail"; return r; }
            int[] before = pixels(s0.bmp, rect[0], rect[1], rect[2], rect[3]);
            s0.bmp.recycle();
            long deadline = SystemClock.uptimeMillis() + timeout;
            double last = -1;
            while (SystemClock.uptimeMillis() < deadline && !aborted) {
                VeriService.Shot s = svc.takeShotSync();
                if (s.bmp == null) { sleepQ(120); continue; }
                frameCount++;
                int[] cur = pixels(s.bmp, rect[0], rect[1], rect[2], rect[3]);
                s.bmp.recycle();
                last = diffRatio(before, cur);
                if (last >= thresh) {
                    r.ok = true;
                    r.detail = "changed diff=" + String.format("%.3f", last) + " in " + (SystemClock.uptimeMillis() - tA) + "ms";
                    return r;
                }
                sleepQ(100);
            }
            r.detail = "waitchange-timeout diff=" + String.format("%.3f", last);
        } catch (Throwable t) { r.detail = "waitchange-err:" + String.valueOf(t); }
        r.ms = SystemClock.uptimeMillis() - tA;
        return r;
    }

    ActionRes actWaitStable(JSONObject a, int W, int H) {
        ActionRes r = new ActionRes();
        long tA = SystemClock.uptimeMillis();
        try {
            int[] rect = a.has("region") ? regionPx(a.optJSONArray("region"), W, H) : new int[]{0, 0, W, H};
            long time = a.optLong("time", 300);
            long timeout = a.optLong("timeout", 5000);
            double thr = a.optDouble("threshold", 0.95);
            double diffThr = Math.max(0.0005, 1.0 - thr);
            long deadline = SystemClock.uptimeMillis() + timeout;
            long stableSince = 0;
            int[] prev = null;
            while (SystemClock.uptimeMillis() < deadline && !aborted) {
                VeriService.Shot s = svc.takeShotSync();
                if (s.bmp == null) { sleepQ(100); continue; }
                frameCount++;
                int[] cur = pixels(s.bmp, rect[0], rect[1], rect[2], rect[3]);
                s.bmp.recycle();
                if (prev != null) {
                    double d = diffRatio(prev, cur);
                    if (d < diffThr) {
                        if (stableSince == 0) stableSince = SystemClock.uptimeMillis();
                        if (SystemClock.uptimeMillis() - stableSince >= time) {
                            r.ok = true;
                            r.detail = "stable " + (SystemClock.uptimeMillis() - tA) + "ms";
                            return r;
                        }
                    } else {
                        stableSince = 0;
                    }
                }
                prev = cur;
                sleepQ(100);
            }
            r.detail = "waitstable-timeout";
        } catch (Throwable t) { r.detail = "waitstable-err:" + String.valueOf(t); }
        r.ms = SystemClock.uptimeMillis() - tA;
        return r;
    }

    // 冻结等待（pre/post_wait_freezes）：连续 time 毫秒画面无大变化才继续；超时仅记录、不判失败
    void freeze(JSONObject fz, String tag) {
        if (fz == null) return;
        long tA = SystemClock.uptimeMillis();
        try {
            svc.ensureScreen();
            int W = svc.screenW, H = svc.screenH;
            int[] rect = fz.has("region") ? regionPx(fz.optJSONArray("region"), W, H) : new int[]{0, 0, W, H};
            long time = fz.optLong("time", 300);
            long timeout = fz.optLong("timeout", 5000);
            double thr = fz.optDouble("threshold", 0.95);
            double diffThr = Math.max(0.0005, 1.0 - thr);
            long deadline = SystemClock.uptimeMillis() + timeout;
            long stableSince = 0;
            int[] prev = null;
            while (SystemClock.uptimeMillis() < deadline && !aborted) {
                VeriService.Shot s = svc.takeShotSync();
                if (s.bmp == null) { sleepQ(100); continue; }
                frameCount++;
                int[] cur = pixels(s.bmp, rect[0], rect[1], rect[2], rect[3]);
                s.bmp.recycle();
                if (prev != null) {
                    double d = diffRatio(prev, cur);
                    if (d < diffThr) {
                        if (stableSince == 0) stableSince = SystemClock.uptimeMillis();
                        if (SystemClock.uptimeMillis() - stableSince >= time) { log("freeze " + tag + " ok " + (SystemClock.uptimeMillis() - tA) + "ms"); return; }
                    } else {
                        stableSince = 0;
                    }
                }
                prev = cur;
                sleepQ(100);
            }
            log("freeze " + tag + " timeout (continue)");
        } catch (Throwable t) { }
    }

    // ================== 对外命令：素材与校准 ==================

    static String errJson(String tag, String msg) {
        return "{\"ok\":0,\"err\":\"" + esc(tag + ": " + msg) + "\"}";
    }

    static int[] rectFromKv(Map<String, String> kv, int W, int H) {
        double x = getDouble(kv, "x", 0), y = getDouble(kv, "y", 0);
        double w = getDouble(kv, "w", 1), h = getDouble(kv, "h", 1);
        int px = (int) Math.round(x <= 1.0 ? x * W : x);
        int py = (int) Math.round(y <= 1.0 ? y * H : y);
        int pw = (int) Math.round(w <= 1.0 ? w * W : w);
        int ph = (int) Math.round(h <= 1.0 ? h * H : h);
        px = clamp(px, 0, W - 1); py = clamp(py, 0, H - 1);
        pw = clamp(pw, 1, W - px); ph = clamp(ph, 1, H - py);
        return new int[]{px, py, pw, ph};
    }

    static JSONArray ja4(double a, double b, double c, double d) {
        JSONArray j = new JSONArray();
        try { j.put(a); j.put(b); j.put(c); j.put(d); } catch (Throwable t) { }
        return j;
    }

    // crop：截当前屏指定区域，存为无损 PNG 模板
    public static String cmdCrop(VeriService svc, Map<String, String> kv) {
        try {
            svc.ensureScreen();
            VeriService.Shot s = svc.takeShotSync();
            if (s.bmp == null) return errJson("crop", "shot failed");
            int W = s.bmp.getWidth(), H = s.bmp.getHeight();
            int[] rect = rectFromKv(kv, W, H);
            Bitmap crop = Bitmap.createBitmap(s.bmp, rect[0], rect[1], rect[2], rect[3]);
            String name = get(kv, "save", "tpl/crop-" + nowMs() + ".png");
            File f = new File(svc.root, name);
            File d = f.getParentFile();
            if (d != null && !d.exists()) d.mkdirs();
            FileOutputStream fo = new FileOutputStream(f);
            crop.compress(Bitmap.CompressFormat.PNG, 100, fo);
            fo.close();
            int cw = crop.getWidth(), ch = crop.getHeight();
            crop.recycle();
            s.bmp.recycle();
            return "{\"ok\":1,\"file\":\"" + esc(name) + "\",\"w\":" + cw + ",\"h\":" + ch
                    + ",\"box\":[" + rect[0] + "," + rect[1] + "," + rect[2] + "," + rect[3] + "],\"res\":[" + W + "," + H + "]}";
        } catch (Throwable t) { return errJson("crop", String.valueOf(t)); }
    }

    // fp：单区域指纹采集（返回 hex；给了 name 就存文件）
    public static String cmdFp(VeriService svc, Map<String, String> kv) {
        try {
            svc.ensureScreen();
            VeriService.Shot s = svc.takeShotSync();
            if (s.bmp == null) return errJson("fp", "shot failed");
            int W = s.bmp.getWidth(), H = s.bmp.getHeight();
            int[] rect = rectFromKv(kv, W, H);
            int gw = getInt(kv, "gw", 16), gh = getInt(kv, "gh", 16);
            byte[] g = gridOf(s.bmp, rect, gw, gh);
            s.bmp.recycle();
            String hex = hexOf(g);
            String name = get(kv, "name", "");
            if (name.length() > 0) {
                JSONObject fp = new JSONObject();
                fp.put("gw", gw);
                fp.put("gh", gh);
                fp.put("w", W);
                fp.put("h", H);
                JSONArray parts = new JSONArray();
                JSONObject p = new JSONObject();
                p.put("region", ja4(getDouble(kv, "x", 0), getDouble(kv, "y", 0), getDouble(kv, "w", 1), getDouble(kv, "h", 1)));
                p.put("data", hex);
                parts.put(p);
                fp.put("parts", parts);
                writeText(new File(svc.root, name), fp.toString(2));
            }
            return "{\"ok\":1,\"hex\":\"" + hex + "\",\"gw\":" + gw + ",\"gh\":" + gh
                    + ",\"box\":[" + rect[0] + "," + rect[1] + "," + rect[2] + "," + rect[3] + "],\"file\":\"" + esc(name) + "\"}";
        } catch (Throwable t) { return errJson("fp", String.valueOf(t)); }
    }

    // mkfp：多区域组合指纹（regions="x,y,w,h;x,y,w,h;..."）
    public static String cmdMkfp(VeriService svc, Map<String, String> kv) {
        try {
            String regions = get(kv, "regions", "");
            if (regions.length() == 0) return errJson("mkfp", "missing regions");
            svc.ensureScreen();
            VeriService.Shot s = svc.takeShotSync();
            if (s.bmp == null) return errJson("mkfp", "shot failed");
            int W = s.bmp.getWidth(), H = s.bmp.getHeight();
            int gw = getInt(kv, "gw", 16), gh = getInt(kv, "gh", 16);
            JSONArray parts = new JSONArray();
            String[] segs = regions.split(";");
            StringBuilder det = new StringBuilder();
            for (int i = 0; i < segs.length; i++) {
                String seg = segs[i].trim();
                if (seg.length() == 0) continue;
                String[] p4 = seg.split(",");
                if (p4.length < 4) return errJson("mkfp", "bad region: " + seg);
                double x = Double.parseDouble(p4[0].trim()), y = Double.parseDouble(p4[1].trim());
                double w = Double.parseDouble(p4[2].trim()), h = Double.parseDouble(p4[3].trim());
                int[] rect = regionPx(ja4(x, y, w, h), W, H);
                byte[] g = gridOf(s.bmp, rect, gw, gh);
                JSONObject p = new JSONObject();
                p.put("region", ja4(x, y, w, h));
                p.put("data", hexOf(g));
                parts.put(p);
                det.append(" [").append(rect[0]).append(",").append(rect[1]).append(",").append(rect[2]).append(",").append(rect[3]).append("]");
            }
            s.bmp.recycle();
            JSONObject fp = new JSONObject();
            fp.put("gw", gw);
            fp.put("gh", gh);
            fp.put("w", W);
            fp.put("h", H);
            if (kv.containsKey("note")) fp.put("note", get(kv, "note", ""));
            fp.put("parts", parts);
            String name = get(kv, "name", "fp/auto-" + nowMs() + ".json");
            writeText(new File(svc.root, name), fp.toString(2));
            return "{\"ok\":1,\"file\":\"" + esc(name) + "\",\"parts\":" + parts.length() + ",\"boxes\":\"" + esc(det.toString().trim()) + "\"}";
        } catch (Throwable t) { return errJson("mkfp", String.valueOf(t)); }
    }

    // savefp：写入完整指纹对象（内联 json=），用于把素材从工程侧回灌进 app
    public static String cmdSaveFp(VeriService svc, Map<String, String> kv) {
        try {
            String name = get(kv, "name", "");
            String js = get(kv, "json", "");
            if (name.length() == 0 || js.length() == 0) return errJson("savefp", "missing name/json");
            JSONObject o = new JSONObject(js);
            if (!o.has("parts")) return errJson("savefp", "no parts");
            writeText(new File(svc.root, name), o.toString(2));
            return "{\"ok\":1,\"file\":\"" + esc(name) + "\",\"parts\":" + o.getJSONArray("parts").length() + "}";
        } catch (Throwable t) { return errJson("savefp", String.valueOf(t)); }
    }

    // savetpl：写入模板（data=base64(PNG)），解码校验宽高
    public static String cmdSaveTpl(VeriService svc, Map<String, String> kv) {
        try {
            String name = get(kv, "name", "");
            String data = get(kv, "data", "");
            if (name.length() == 0 || data.length() == 0) return errJson("savetpl", "missing name/data");
            byte[] png = Base64.decode(data, Base64.DEFAULT);
            if (png.length < 8 || (png[0] & 0xff) != 0x89 || (png[1] & 0xff) != 0x50) return errJson("savetpl", "not png");
            writeAll(new File(svc.root, name), png);
            Bitmap b = BitmapFactory.decodeFile(new File(svc.root, name).getAbsolutePath());
            int w = b == null ? -1 : b.getWidth();
            int h = b == null ? -1 : b.getHeight();
            if (b != null) b.recycle();
            return "{\"ok\":" + (w > 0 ? 1 : 0) + ",\"file\":\"" + esc(name) + "\",\"bytes\":" + png.length + ",\"w\":" + w + ",\"h\":" + h + "}";
        } catch (Throwable t) { return errJson("savetpl", String.valueOf(t)); }
    }

    // findtpl：在当前帧找图样（校准用；不起任务网）
    public static String cmdFindTpl(VeriService svc, Map<String, String> kv) {
        try {
            String tplsStr = get(kv, "tpl", "");
            if (tplsStr.length() == 0) return errJson("findtpl", "missing tpl");
            svc.ensureScreen();
            VeriService.Shot s = svc.takeShotSync();
            if (s.bmp == null) return errJson("findtpl", "shot failed");
            int W = s.bmp.getWidth(), H = s.bmp.getHeight();
            int[] roi;
            if (kv.containsKey("roi")) {
                roi = parseRect(get(kv, "roi", ""), W, H);
            } else {
                roi = new int[]{0, 0, W, H};
            }
            int tol = getInt(kv, "tol", 0);
            int maxhits = getInt(kv, "maxhits", 5);
            double minScore = getDouble(kv, "min_score", 0.9);
            List<Double> scales = new ArrayList<Double>();
            String sc = get(kv, "scales", "1.0");
            for (String one : sc.split(",")) { try { scales.add(Double.valueOf(one.trim())); } catch (Throwable t) { } }
            if (scales.isEmpty()) scales.add(Double.valueOf(1.0));
            String[] tpls = tplsStr.split(",");
            JSONArray hits = new JSONArray();
            StringBuilder tried = new StringBuilder();
            for (int ti = 0; ti < tpls.length; ti++) {
                String path = tpls[ti].trim();
                Bitmap t = BitmapFactory.decodeFile(new File(svc.root, path).getAbsolutePath());
                if (t == null) { tried.append(" missing:").append(path); continue; }
                for (int si = 0; si < scales.size(); si++) {
                    double sca = scales.get(si).doubleValue();
                    Bitmap use = t;
                    if (Math.abs(sca - 1.0) > 0.001) {
                        use = Bitmap.createScaledBitmap(t, Math.max(2, (int) Math.round(t.getWidth() * sca)), Math.max(2, (int) Math.round(t.getHeight() * sca)), true);
                    }
                    List<TplHit> hs = searchTplList(s.bmp, roi, use, tol, minScore, maxhits);
                    if (use != t) use.recycle();
                    for (int i = 0; i < hs.size(); i++) {
                        TplHit th = hs.get(i);
                        JSONObject o = new JSONObject();
                        o.put("tpl", path);
                        o.put("scale", sca);
                        o.put("box", arr(th.box));
                        o.put("cx", round3d((th.box[0] + th.box[2] / 2.0) / W));
                        o.put("cy", round3d((th.box[1] + th.box[3] / 2.0) / H));
                        o.put("score", round3d(th.score));
                        hits.put(o);
                    }
                }
                t.recycle();
            }
            s.bmp.recycle();
            JSONObject out = new JSONObject();
            out.put("ok", 1);
            out.put("n", hits.length());
            out.put("hits", hits);
            out.put("res", ja4(W, H, 0, 0));
            if (tried.length() > 0) out.put("warn", tried.toString().trim());
            return out.toString();
        } catch (Throwable t) { return errJson("findtpl", String.valueOf(t)); }
    }

    // findcolor：在当前帧找色（返回匹配包围盒）
    public static String cmdFindColor(VeriService svc, Map<String, String> kv) {
        try {
            String rgb = get(kv, "rgb", "#ff0000");
            svc.ensureScreen();
            VeriService.Shot s = svc.takeShotSync();
            if (s.bmp == null) return errJson("findcolor", "shot failed");
            int W = s.bmp.getWidth(), H = s.bmp.getHeight();
            JSONObject rec = new JSONObject();
            if (rgb.startsWith("#")) rec.put("rgb", rgb);
            else {
                JSONArray ra = new JSONArray();
                for (String one : rgb.split(",")) ra.put(Integer.parseInt(one.trim()));
                rec.put("rgb", ra);
            }
            if (kv.containsKey("roi")) {
                JSONArray roi = new JSONArray();
                for (String one : get(kv, "roi", "").split(",")) roi.put(Double.parseDouble(one.trim()));
                rec.put("roi", roi);
            }
            rec.put("tol", getInt(kv, "tol", 12));
            rec.put("min", getInt(kv, "min", 25));
            Run tmp = new Run();
            tmp.svc = svc;
            tmp.root = svc.root;
            Hit h = tmp.recColor(rec, s.bmp);
            s.bmp.recycle();
            JSONObject out = new JSONObject();
            out.put("ok", 1);
            out.put("hit", h.hit ? 1 : 0);
            out.put("score", round3d(h.score));
            if (h.box != null) {
                out.put("box", arr(h.box));
                out.put("cx", round3d((h.box[0] + h.box[2] / 2.0) / W));
                out.put("cy", round3d((h.box[1] + h.box[3] / 2.0) / H));
            }
            out.put("detail", h.detail);
            out.put("res", ja4(W, H, 0, 0));
            return out.toString();
        } catch (Throwable t) { return errJson("findcolor", String.valueOf(t)); }
    }

    // ================== 数字/字形模板识别（num）==================
    // 背景：ML Kit OCR 对大字/数字有误读（实测 9→6），数字改走"字形模板"——
    //   在指定区域内切出单个字形 → 归一化 → 与 tpl/num/*.png 模板集逐一比对 → 取最像的。
    //   ★识别失败必须显式报错（ok=0），绝不猜数字。
    // 模板：app 侧 tpl/num/，文件名首字符＝标签（9.png / 9-2.png；dot→. colon→: slash→/ pct→% minus→-）
    //   ★模板须为"紧贴字形的裁剪"（本命令 tplsave 生成的就是）；比对时不再二次分割模板。
    // 参数：src=（可选，app 根下相对路径的离线帧）、x,y,w,h（区域，0~1 或像素）、tpl=（目录名或 .png 列表）
    //   min_score=（默认 0.90）、margin=（默认 0.04，领先第二名的最小差距）、gap=（默认 0，字形内允许的空列）
    //   dbg=1（返回区域与各字形的 ASCII 预览）、save=（存区域截图 png）
    //   tplsave=<标签串>（★采集模式：把切出的各字形按标签存成模板）、suffix=（模板文件名后缀，多份样本用）

    static final int NUM_GW = 24, NUM_GH = 32;

    static String numTagOf(String ch) {
        if (".".equals(ch)) return "dot";
        if (":".equals(ch)) return "colon";
        if ("/".equals(ch)) return "slash";
        if ("%".equals(ch)) return "pct";
        if ("-".equals(ch)) return "minus";
        return ch;
    }

    static String numLabelOf(String fileName) {
        String b = fileName;
        int slash = b.lastIndexOf('/');
        if (slash >= 0) b = b.substring(slash + 1);
        int dot = b.lastIndexOf('.');
        if (dot > 0) b = b.substring(0, dot);
        if (b.length() == 0) return null;
        String low = b.toLowerCase();
        if (low.startsWith("dot")) return ".";
        if (low.startsWith("colon")) return ":";
        if (low.startsWith("slash")) return "/";
        if (low.startsWith("pct") || low.startsWith("percent")) return "%";
        if (low.startsWith("minus") || low.startsWith("neg")) return "-";
        char c = b.charAt(0);
        if ((c >= '0' && c <= '9') || c == '.' || c == ':' || c == '/' || c == '%' || c == '-') return String.valueOf(c);
        return null;
    }

    // 灰度网格（NUM_GW×NUM_GH 块平均亮度）★保持长宽比居中留白（窄字如"1"不会被拉宽而失真）
    static int lumOf(int c) {
        return (((c >> 16) & 0xff) * 299 + ((c >> 8) & 0xff) * 587 + (c & 0xff) * 114) / 1000;
    }

    static double[] numGrid(Bitmap b, int x, int y, int w, int h) {
        int[] px = pixels(b, x, y, w, h);
        int[] hist = new int[256];
        for (int i = 0; i < px.length; i++) hist[lumOf(px[i])]++;
        int bgv = 0, acc = 0, half = Math.max(1, px.length / 2);
        for (int v = 0; v < 256; v++) { acc += hist[v]; if (acc >= half) { bgv = v; break; } }
        double[] g = new double[NUM_GW * NUM_GH];
        double sc = Math.min((double) NUM_GW / Math.max(1, w), (double) NUM_GH / Math.max(1, h));
        int nw = Math.max(1, Math.min(NUM_GW, (int) Math.round(w * sc)));
        int nh = Math.max(1, Math.min(NUM_GH, (int) Math.round(h * sc)));
        int ox = (NUM_GW - nw) / 2, oy = (NUM_GH - nh) / 2;
        for (int j = 0; j < NUM_GH; j++) {
            for (int i = 0; i < NUM_GW; i++) {
                int vi = j * NUM_GW + i;
                if (i < ox || i >= ox + nw || j < oy || j >= oy + nh) { g[vi] = bgv; continue; }
                int sx = (int) ((long) (i - ox) * w / nw), ex = (int) ((long) (i - ox + 1) * w / nw);
                int sy = (int) ((long) (j - oy) * h / nh), ey = (int) ((long) (j - oy + 1) * h / nh);
                if (ex <= sx) ex = sx + 1;
                if (ey <= sy) ey = sy + 1;
                long s2 = 0; int c2 = 0;
                for (int yy = sy; yy < ey && yy < h; yy++) {
                    for (int xx = sx; xx < ex && xx < w; xx++) { s2 += lumOf(px[yy * w + xx]); c2++; }
                }
                g[vi] = c2 == 0 ? bgv : (double) s2 / c2;
            }
        }
        return g;
    }

    // 零均值单位方差（亮度增益/对比度无关）
    static void numNormalize(double[] g) {
        double m = 0;
        for (int i = 0; i < g.length; i++) m += g[i];
        m /= g.length;
        double s = 0;
        for (int i = 0; i < g.length; i++) { double d = g[i] - m; s += d * d; }
        s = Math.sqrt(s / g.length);
        if (s < 1e-6) s = 1;
        for (int i = 0; i < g.length; i++) g[i] = (g[i] - m) / s;
    }

    static double numCorr(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s / a.length;
    }

    // 区域"墨迹"掩码：以边框像素均色为背景，按"到背景的色距"做 Otsu 分割；去孤立点。
    // meta = {背景R, 阈值, 墨迹数, 墨迹占比%}
    static boolean[] numInk(Bitmap b, int rx, int ry, int rw, int rh, int[] meta, double gain) {
        int[] px = pixels(b, rx, ry, rw, rh);
        int bw = Math.max(1, Math.min(rw, rh) / 10);
        long br = 0, bg = 0, bb = 0; int bn = 0;
        for (int y = 0; y < rh; y++) {
            for (int x = 0; x < rw; x++) {
                if (x >= bw && x < rw - bw && y >= bw && y < rh - bw) continue;
                int c = px[y * rw + x];
                br += (c >> 16) & 0xff; bg += (c >> 8) & 0xff; bb += c & 0xff; bn++;
            }
        }
        if (bn <= 0) bn = 1;
        int mr = (int) (br / bn), mg = (int) (bg / bn), mb = (int) (bb / bn);
        int[] hist = new int[766];
        int[] dist = new int[rw * rh];
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int d = Math.abs(((c >> 16) & 0xff) - mr) + Math.abs(((c >> 8) & 0xff) - mg) + Math.abs((c & 0xff) - mb);
            if (d > 765) d = 765;
            dist[i] = d; hist[d]++;
        }
        int total = px.length;
        long sum = 0;
        for (int i = 0; i < 766; i++) sum += (long) i * hist[i];
        long sumB = 0; int wB = 0; double best = -1; int thr = 40;
        for (int t = 0; t < 766; t++) {
            wB += hist[t]; if (wB == 0) continue;
            int wF = total - wB; if (wF == 0) break;
            sumB += (long) t * hist[t];
            double mB = (double) sumB / wB, mF = (double) (sum - sumB) / wF;
            double v = (double) wB * wF * (mB - mF) * (mB - mF);
            if (v > best) { best = v; thr = t; }
        }
        int d99 = 765;
        long acc = 0, need = Math.max(1, total / 100);
        for (int i = 765; i >= 0; i--) { acc += hist[i]; if (acc >= need) { d99 = i; break; } }
        int seg = thr + (int) Math.round(gain * Math.max(0, d99 - thr));
        if (seg > 765) seg = 765;
        boolean[] m = new boolean[total];
        int ink = 0;
        for (int i = 0; i < total; i++) { if (dist[i] > seg) { m[i] = true; ink++; } }
        for (int y = 0; y < rh; y++) {
            for (int x = 0; x < rw; x++) {
                int i = y * rw + x;
                if (!m[i]) continue;
                boolean up = y > 0 && m[i - rw], dn = y < rh - 1 && m[i + rw];
                boolean lf = x > 0 && m[i - 1], rt = x < rw - 1 && m[i + 1];
                if (!up && !dn && !lf && !rt) { m[i] = false; ink--; }
            }
        }
        meta[0] = mr; meta[1] = seg; meta[2] = ink; meta[4] = thr; meta[5] = d99;
        meta[3] = (int) (100L * ink / Math.max(1, total));
        return m;
    }

    // 按列投影切字形；返回 {x,y,w,h,墨迹数}
    static int[] numCols(boolean[] m, int rw, int rh) {
        int[] cc = new int[rw];
        for (int x = 0; x < rw; x++) {
            int c = 0;
            for (int y = 0; y < rh; y++) if (m[y * rw + x]) c++;
            cc[x] = c;
        }
        return cc;
    }

    static String numColStr(int[] cc, int rw) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(rw, 600);
        for (int x = 0; x < n; x++) {
            int c = cc[x];
            sb.append(c == 0 ? '.' : (c < 10 ? (char) ('0' + c) : 'X'));
        }
        return sb.toString();
    }

    // 谷点细分：把"贴在一起"的字形切开（只靠"零列"不够用：贴着的数字没有空列）
    static void numSplitRun(boolean[] m, int rw, int rh, int[] cc, int a, int b, List<int[]> out) {
        int y0 = Integer.MAX_VALUE, y1 = -1;
        for (int x = a; x <= b; x++) {
            for (int y = 0; y < rh; y++) if (m[y * rw + x]) { if (y < y0) y0 = y; if (y > y1) y1 = y; }
        }
        int hh = Math.max(1, y1 - y0 + 1);
        int minW = Math.max(3, (int) (0.30 * hh));
        if (b - a + 1 >= 2 * minW) {
            int maxC = 0;
            for (int x = a; x <= b; x++) if (cc[x] > maxC) maxC = cc[x];
            int limit = Math.max(1, (int) (0.22 * maxC));
            int bestX = -1, bestV = Integer.MAX_VALUE;
            for (int x = a + minW; x <= b - minW; x++) if (cc[x] < bestV) { bestV = cc[x]; bestX = x; }
            if (bestX > 0 && bestV <= limit) {
                int x1 = bestX, x2 = bestX;
                while (x1 > a && cc[x1 - 1] <= limit) x1--;
                while (x2 < b && cc[x2 + 1] <= limit) x2++;
                numSplitRun(m, rw, rh, cc, a, x1 - 1, out);
                numSplitRun(m, rw, rh, cc, x2 + 1, b, out);
                return;
            }
        }
        out.add(new int[]{a, b});
    }

    static List<int[]> numGlyphs(boolean[] m, int rw, int rh, int gap) {
        int[] cc = numCols(m, rw, rh);
        List<int[]> runs = new ArrayList<int[]>();
        int start = -1, lastInk = -2;
        for (int x = 0; x < rw; x++) {
            if (cc[x] == 0) continue;
            if (start < 0) start = x;
            else if (x - lastInk - 1 > gap) { runs.add(new int[]{start, lastInk}); start = x; }
            lastInk = x;
        }
        if (start >= 0) runs.add(new int[]{start, lastInk});
        List<int[]> parts = new ArrayList<int[]>();
        for (int i = 0; i < runs.size(); i++) {
            int[] r = runs.get(i);
            numSplitRun(m, rw, rh, cc, r[0], r[1], parts);
        }
        List<int[]> out = new ArrayList<int[]>();
        for (int i = 0; i < parts.size(); i++) {
            int[] b = numBox(m, rw, rh, parts.get(i)[0], parts.get(i)[1]);
            if (b != null) out.add(b);
        }
        return out;
    }

    static int[] numBox(boolean[] m, int rw, int rh, int x0, int x1) {
        int y0 = Integer.MAX_VALUE, y1 = -1, cnt = 0;
        for (int x = x0; x <= x1; x++) {
            for (int y = 0; y < rh; y++) if (m[y * rw + x]) {
                if (y < y0) y0 = y;
                if (y > y1) y1 = y;
                cnt++;
            }
        }
        if (y1 < 0) return null;
        return new int[]{x0, y0, x1 - x0 + 1, y1 - y0 + 1, cnt};
    }

    // ASCII 预览（'#'=墨 '+'=半 '.『=空）
    static String numArt(boolean[] m, int rw, int rh, int x0, int y0, int w, int h, int cols, int rows) {
        StringBuilder sb = new StringBuilder();
        if (cols < 1) cols = 1;
        if (rows < 1) rows = 1;
        for (int j = 0; j < rows; j++) {
            int sy = y0 + (int) ((long) j * h / rows), ey = y0 + (int) ((long) (j + 1) * h / rows);
            if (ey <= sy) ey = sy + 1;
            for (int i = 0; i < cols; i++) {
                int sx = x0 + (int) ((long) i * w / cols), ex = x0 + (int) ((long) (i + 1) * w / cols);
                if (ex <= sx) ex = sx + 1;
                int ink = 0, tot = 0;
                for (int yy = sy; yy < ey && yy < rh; yy++) {
                    for (int xx = sx; xx < ex && xx < rw; xx++) { tot++; if (m[yy * rw + xx]) ink++; }
                }
                double r = tot == 0 ? 0 : (double) ink / tot;
                sb.append(r > 0.55 ? '#' : (r > 0.18 ? '+' : '.'));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    static double numParse(String txt) {
        try {
            if (txt == null || txt.length() == 0) return Double.NaN;
            for (int i = 0; i < txt.length(); i++) {
                char c = txt.charAt(i);
                if ((c < '0' || c > '9') && c != '.' && c != '-') return Double.NaN;
            }
            return Double.parseDouble(txt);
        } catch (Throwable t) { return Double.NaN; }
    }

    // 加载模板集：返回 [[label, grid]...]
    static List<Object[]> numLoadTpls(VeriService svc, String tplSpec) {
        List<File> fs = new ArrayList<File>();
        String spec = tplSpec == null ? "" : tplSpec.trim();
        if (spec.length() == 0) spec = "tpl/num";
        if (spec.contains(".png") || spec.contains(".jpg")) {
            String[] ps = spec.split(",");
            for (int i = 0; i < ps.length; i++) {
                File f = new File(svc.root, ps[i].trim());
                if (f.exists()) fs.add(f);
            }
        } else {
            File d = new File(svc.root, spec);
            File[] all = d.listFiles();
            if (all != null) {
                for (int i = 0; i < all.length; i++) {
                    String n = all[i].getName().toLowerCase();
                    if (all[i].isFile() && (n.endsWith(".png") || n.endsWith(".jpg"))) fs.add(all[i]);
                }
            }
        }
        List<Object[]> out = new ArrayList<Object[]>();
        for (int i = 0; i < fs.size(); i++) {
            String lab = numLabelOf(fs.get(i).getName());
            if (lab == null) continue;
            Bitmap tb = BitmapFactory.decodeFile(fs.get(i).getAbsolutePath());
            if (tb == null) continue;
            double[] g = numGrid(tb, 0, 0, tb.getWidth(), tb.getHeight());
            numNormalize(g);
            tb.recycle();
            out.add(new Object[]{lab, g, fs.get(i).getName()});
        }
        return out;
    }

    public static String cmdNum(VeriService svc, Map<String, String> kv) {
        long t0 = nowMs();
        Bitmap bmp = null;
        try {
            String src = get(kv, "src", "");
            if (src.length() > 0) {
                File f = src.startsWith("/") ? new File(src) : new File(svc.root, src);
                if (!f.exists()) return errJson("num", "src not found: " + src);
                bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (bmp == null) return errJson("num", "decode failed: " + src);
            } else {
                svc.ensureScreen();
                VeriService.Shot s = svc.takeShotSync();
                if (s.bmp == null) return errJson("num", "shot failed");
                bmp = s.bmp;
            }
            int W = bmp.getWidth(), H = bmp.getHeight();
            int[] rect = rectFromKv(kv, W, H);
            int rx = rect[0], ry = rect[1], rw = rect[2], rh = rect[3];
            int[] meta = new int[6];
            boolean[] m = numInk(bmp, rx, ry, rw, rh, meta, getDouble(kv, "gain", 0.0));
            if (meta[3] <= 0 || meta[3] > 95) {
                return errJson("num", String.format(java.util.Locale.ROOT, Words.RP_INK_BAD_FMT, meta[3]));
            }
            boolean dbg = "1".equals(get(kv, "dbg", ""));
            int gap = getInt(kv, "gap", 0);
            List<int[]> glyphs = numGlyphs(m, rw, rh, gap);
            // 过滤极小噪块
            long minInk = Math.max(2, (long) rw * rh / 20000);
            List<int[]> keep = new ArrayList<int[]>();
            for (int i = 0; i < glyphs.size(); i++) if (glyphs.get(i)[4] >= minInk) keep.add(glyphs.get(i));
            glyphs = keep;

            // 存区域截图（证据用）
            String saveName = get(kv, "save", "");
            if (saveName.length() > 0) {
                Bitmap c = Bitmap.createBitmap(bmp, rx, ry, rw, rh);
                File sf = new File(svc.root, saveName);
                File sd = sf.getParentFile();
                if (sd != null && !sd.exists()) sd.mkdirs();
                FileOutputStream fo = new FileOutputStream(sf);
                c.compress(Bitmap.CompressFormat.PNG, 100, fo);
                fo.close(); c.recycle();
            }

            // 采集模式：把切出的字形按标签存模板
            String tplsave = get(kv, "tplsave", "");
            if (tplsave.length() > 0) {
                if (glyphs.size() != tplsave.length()) {
                    return errJson("num", String.format(java.util.Locale.ROOT, Words.RP_TPLS_BAD_FMT, tplsave.length(), glyphs.size()));
                }
                String dir = get(kv, "tpldir", "tpl/num");
                String suf = get(kv, "suffix", "");
                File dd = new File(svc.root, dir);
                if (!dd.exists()) dd.mkdirs();
                JSONArray saved = new JSONArray();
                StringBuilder warn = new StringBuilder();
                for (int i = 0; i < glyphs.size(); i++) {
                    int[] b = glyphs.get(i);
                    String ch = tplsave.substring(i, i + 1);
                    String fn = dir + "/" + numTagOf(ch) + suf + ".png";
                    if (b[0] <= 0 || b[1] <= 0 || b[0] + b[2] >= rw || b[1] + b[3] >= rh) warn.append(" #").append(i).append(Words.RP_EDGE);
                    Bitmap c = Bitmap.createBitmap(bmp, rx + b[0], ry + b[1], b[2], b[3]);
                    FileOutputStream fo = new FileOutputStream(new File(svc.root, fn));
                    c.compress(Bitmap.CompressFormat.PNG, 100, fo);
                    fo.close(); c.recycle();
                    JSONObject o = new JSONObject();
                    o.put("ch", ch);
                    o.put("file", fn);
                    o.put("box", arr(new int[]{rx + b[0], ry + b[1], b[2], b[3]}));
                    o.put("ink", b[4]);
                    if (dbg) o.put("art", numArt(m, rw, rh, b[0], b[1], b[2], b[3], 14, 20));
                    saved.put(o);
                }
                JSONObject out = new JSONObject();
                out.put("ok", 1);
                out.put("mode", "tplsave");
                out.put("saved", saved);
                out.put("n", glyphs.size());
                out.put("ink_pct", meta[3]);
                out.put("res", ja4(W, H, 0, 0));
                out.put("ms", nowMs() - t0);
                if (warn.length() > 0) out.put("warn", Words.RP_EDGE_WARN + warn);
                if (dbg) out.put("art", numArt(m, rw, rh, 0, 0, rw, rh, Math.min(rw, 80), Math.max(1, (int) Math.round((double) rh * Math.min(rw, 80) / rw))));
                if (dbg) out.put("col", numColStr(numCols(m, rw, rh), rw));
                return out.toString();
            }

            // 识别模式
            List<Object[]> tpls = numLoadTpls(svc, get(kv, "tpl", ""));
            if (tpls.isEmpty()) {
                JSONObject o0 = new JSONObject();
                o0.put("ok", 0);
                o0.put("err", Words.RP_TPL_EMPTY);
                o0.put("n", glyphs.size());
                JSONArray d0 = new JSONArray();
                for (int i = 0; i < glyphs.size(); i++) {
                    int[] b = glyphs.get(i);
                    JSONObject od = new JSONObject();
                    od.put("i", i);
                    od.put("box", arr(new int[]{rx + b[0], ry + b[1], b[2], b[3]}));
                    if (dbg) od.put("art", numArt(m, rw, rh, b[0], b[1], b[2], b[3], 14, 20));
                    d0.put(od);
                }
                o0.put("digits", d0);
                o0.put("ink_pct", meta[3]);
                o0.put("res", ja4(W, H, 0, 0));
                if (dbg) o0.put("art", numArt(m, rw, rh, 0, 0, rw, rh, Math.min(rw, 80), Math.max(1, (int) Math.round((double) rh * Math.min(rw, 80) / rw))));
                if (dbg) o0.put("col", numColStr(numCols(m, rw, rh), rw));
                return o0.toString();
            }
            double minScore = getDouble(kv, "min_score", 0.90);
            double margin = getDouble(kv, "margin", 0.04);
            if (glyphs.isEmpty()) return errJson("num", Words.RP_NO_GLYPH);

            StringBuilder text = new StringBuilder();
            JSONArray digits = new JSONArray();
            int badIdx = -1; double badScore = 2; String badCh = ""; String badCh2 = "";
            double badMargin = 2; String badWhy = "";
            for (int i = 0; i < glyphs.size(); i++) {
                int[] b = glyphs.get(i);
                double[] g = numGrid(bmp, rx + b[0], ry + b[1], b[2], b[3]);
                numNormalize(g);
                HashMap<String, Double> byLab = new HashMap<String, Double>();
                HashMap<String, String> fileOf = new HashMap<String, String>();
                for (int t = 0; t < tpls.size(); t++) {
                    String lab = (String) tpls.get(t)[0];
                    double sc = numCorr(g, (double[]) tpls.get(t)[1]);
                    Double old = byLab.get(lab);
                    if (old == null || sc > old.doubleValue()) { byLab.put(lab, Double.valueOf(sc)); fileOf.put(lab, (String) tpls.get(t)[2]); }
                }
                String bestLab = null, secLab = null;
                double bestS = -2, secS = -2;
                for (Map.Entry<String, Double> e : byLab.entrySet()) {
                    double sc = e.getValue().doubleValue();
                    if (sc > bestS) { secS = bestS; secLab = bestLab; bestS = sc; bestLab = e.getKey(); }
                    else if (sc > secS) { secS = sc; secLab = e.getKey(); }
                }
                double mg = (secLab == null) ? 9 : (bestS - secS);
                boolean okGlyph = bestLab != null && bestS >= minScore && mg >= margin;
                JSONObject o = new JSONObject();
                o.put("i", i);
                o.put("ch", bestLab == null ? "" : bestLab);
                o.put("score", round3d(bestS));
                if (secLab != null) { o.put("ch2", secLab); o.put("score2", round3d(secS)); }
                o.put("margin", round3d(mg));
                o.put("box", arr(new int[]{rx + b[0], ry + b[1], b[2], b[3]}));
                if (dbg) o.put("art", numArt(m, rw, rh, b[0], b[1], b[2], b[3], 14, 20));
                digits.put(o);
                text.append(okGlyph ? bestLab : "?");
                if (!okGlyph) {
                    String why = bestS < minScore
                            ? String.format(java.util.Locale.ROOT, Words.RP_LIKE_FMT, bestLab, round3d(bestS), String.valueOf(minScore))
                            : String.format(java.util.Locale.ROOT, Words.RP_TOO_SIM_FMT, bestLab, secLab, round3d(mg), String.valueOf(margin));
                    if (badIdx < 0) { badIdx = i; badScore = bestS; badCh = bestLab; badCh2 = secLab; badMargin = mg; badWhy = why; }
                }
            }
            JSONObject out = new JSONObject();
            out.put("n", glyphs.size());
            out.put("tpls", tpls.size());
            out.put("ink_pct", meta[3]);
            out.put("res", ja4(W, H, 0, 0));
            out.put("ms", nowMs() - t0);
            out.put("digits", digits);
            if (dbg) out.put("art", numArt(m, rw, rh, 0, 0, rw, rh, Math.min(rw, 80), Math.max(1, (int) Math.round((double) rh * Math.min(rw, 80) / rw))));
            if (dbg) out.put("col", numColStr(numCols(m, rw, rh), rw));
            if (badIdx >= 0) {
                out.put("ok", 0);
                out.put("err", String.format(java.util.Locale.ROOT, Words.RP_GLYPH_UNK_FMT, badIdx, badWhy));
                out.put("diag", String.format(java.util.Locale.ROOT, Words.RP_DIAG_FMT, badIdx, badCh, badCh2, round3d(badMargin)));
                return out.toString();
            }
            String txt = text.toString();
            out.put("ok", 1);
            out.put("text", txt);
            double v = numParse(txt);
            if (!Double.isNaN(v) && !Double.isInfinite(v)) out.put("value", round3d(v));
            return out.toString();
        } catch (Throwable t) {
            return errJson("num", String.valueOf(t));
        } finally {
            if (bmp != null) bmp.recycle();
        }
    }
    }

    // ================== 门面（VeriService 调用的统一入口）==================

    public static String cmdChain(VeriService svc, Map<String, String> kv) { return Run.cmdChain(svc, kv); }
    public static String cmdCrop(VeriService svc, Map<String, String> kv) { return Run.cmdCrop(svc, kv); }
    public static String cmdFp(VeriService svc, Map<String, String> kv) { return Run.cmdFp(svc, kv); }
    public static String cmdMkfp(VeriService svc, Map<String, String> kv) { return Run.cmdMkfp(svc, kv); }
    public static String cmdSaveFp(VeriService svc, Map<String, String> kv) { return Run.cmdSaveFp(svc, kv); }
    public static String cmdSaveTpl(VeriService svc, Map<String, String> kv) { return Run.cmdSaveTpl(svc, kv); }
    public static String cmdFindTpl(VeriService svc, Map<String, String> kv) { return Run.cmdFindTpl(svc, kv); }
    public static String cmdFindColor(VeriService svc, Map<String, String> kv) { return Run.cmdFindColor(svc, kv); }
    public static String cmdNum(VeriService svc, Map<String, String> kv) { return Run.cmdNum(svc, kv); }
}
