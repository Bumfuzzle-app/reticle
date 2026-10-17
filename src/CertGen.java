package app.reticle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;

/**
 * 74 号单（2026-10-16）· 应用内自签调试钥匙（不随包分发任何钥匙）。
 *
 * 【为什么自己写 DER】出包要签名；apksigner 需要"私钥＋证书"。手机上只有一个 JVM 之外的 dex，
 *   没有 keytool，也没有 sun.security.x509 ⇒ 用最朴素的 ASN.1 DER 手写一张 X.509 v1 自签证书
 *   （RSA-2048／SHA256withRSA），存成 PKCS12。
 * 【口径】这是"用户自己的调试钥匙"，跟作者机那把（备份/Reticle-签名/）无关，也不进包。
 */
public class CertGen {

    /** 生成钥匙库（已存在且能打开＝不重做）。返回空串＝成功。 */
    public static String ensure(File p12, String pass, String cn) {
        try {
            if (p12.exists() && p12.length() > 0 && open(p12, pass) != null) return "";
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KeyPair kp = g.generateKeyPair();
            X509Certificate cert = selfSign(kp, cn);
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            ks.setKeyEntry("reticle", kp.getPrivate(), pass.toCharArray(), new Certificate[]{cert});
            File p = p12.getParentFile();
            if (p != null && !p.exists()) p.mkdirs();
            FileOutputStream os = new FileOutputStream(p12);
            try { ks.store(os, pass.toCharArray()); os.flush(); } finally { try { os.close(); } catch (Throwable t) { } }
            return "";
        } catch (Throwable t) { return "生成签名钥匙失败：" + t; }
    }

    /** 能不能打开（口令对不对、文件坏没坏）。 */
    public static KeyStore open(File p12, String pass) {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            java.io.FileInputStream in = new java.io.FileInputStream(p12);
            try { ks.load(in, pass.toCharArray()); } finally { try { in.close(); } catch (Throwable t) { } }
            return ks.getKey("reticle", pass.toCharArray()) == null ? null : ks;
        } catch (Throwable t) { return null; }
    }

    // ───────────── 自签证书（X.509 v1，够 apksigner 与系统安装校验用） ─────────────

    static X509Certificate selfSign(KeyPair kp, String cn) throws Exception {
        long now = System.currentTimeMillis();
        String nb = utc(new Date(now - 86400000L));
        String na = utc(new Date(now + 20L * 365 * 86400000L));   // 20 年（UTCTime 只到 2049）
        byte[] name = seq(set(seq(oid("2.5.4.3"), utf8(cn == null || cn.length() == 0 ? "Reticle Debug" : cn))));
        byte[] validity = seq(utcRaw(nb), utcRaw(na));
        byte[] alg = seq(oid("1.2.840.113549.1.1.11"), nul());
        byte[] spki = seq(seq(oid("1.2.840.113549.1.1.1"), nul()), bit(rsaPub((RSAPublicKey) kp.getPublic())));
        byte[] tbs = seq(
                intv(BigInteger.valueOf(System.currentTimeMillis() / 1000L)),   // 序列号（v1：省略 version 字段）
                alg, name, validity, name, spki);
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign((PrivateKey) kp.getPrivate());
        sig.update(tbs);
        byte[] cert = seq(tbs, alg, bit(sig.sign()));
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(cert));
    }

    /** RSAPublicKey ::= SEQUENCE { modulus INTEGER, publicExponent INTEGER } */
    static byte[] rsaPub(RSAPublicKey k) {
        byte[] n = k.getModulus().toByteArray();
        if (n.length > 1 && n[0] == 0) { byte[] t = new byte[n.length - 1]; System.arraycopy(n, 1, t, 0, t.length); n = t; }
        return seq(intv(k.getModulus()), intv(k.getPublicExponent()));
    }

    // ───────────── 最小 DER 编码器 ─────────────

    static byte[] seq(byte[]... ps) { return tlv(0x30, cat(ps)); }
    static byte[] set(byte[]... ps) { return tlv(0x31, cat(ps)); }
    static byte[] nul() { return new byte[]{0x05, 0x00}; }
    static byte[] utf8(String s) { try { return tlv(0x0C, s.getBytes("UTF-8")); } catch (Throwable t) { return tlv(0x0C, s.getBytes()); } }
    static byte[] intv(BigInteger v) { byte[] b = v.toByteArray(); return tlv(0x02, b); }
    static byte[] bit(byte[] b) { byte[] r = new byte[b.length + 1]; System.arraycopy(b, 0, r, 1, b.length); return tlv(0x03, r); }

    static String utc(Date d) {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyMMddHHmmss", java.util.Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return f.format(d) + "Z";
    }
    static byte[] utcRaw(String s) { try { return tlv(0x17, s.getBytes("US-ASCII")); } catch (Throwable t) { return tlv(0x17, s.getBytes()); } }

    static byte[] oid(String dotted) {
        String[] p = dotted.split("\\.");
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(40 * Integer.parseInt(p[0]) + Integer.parseInt(p[1]));
        for (int i = 2; i < p.length; i++) {
            long v = Long.parseLong(p[i]);
            byte[] tmp = new byte[9];
            int k = 8;
            tmp[k--] = (byte) (v & 0x7F);
            v >>= 7;
            while (v > 0) { tmp[k--] = (byte) ((v & 0x7F) | 0x80); v >>= 7; }
            o.write(tmp, k + 1, 8 - k);
        }
        return tlv(0x06, o.toByteArray());
    }

    static byte[] cat(byte[]... ps) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (int i = 0; i < ps.length; i++) o.write(ps[i], 0, ps[i].length);
        return o.toByteArray();
    }

    static byte[] tlv(int tag, byte[] body) {
        byte[] h;
        int n = body.length;
        if (n < 128) h = new byte[]{(byte) tag, (byte) n};
        else if (n < 256) h = new byte[]{(byte) tag, (byte) 0x81, (byte) n};
        else if (n < 65536) h = new byte[]{(byte) tag, (byte) 0x82, (byte) (n >> 8), (byte) n};
        else h = new byte[]{(byte) tag, (byte) 0x83, (byte) (n >> 16), (byte) (n >> 8), (byte) n};
        byte[] r = new byte[h.length + n];
        System.arraycopy(h, 0, r, 0, h.length);
        System.arraycopy(body, 0, r, h.length, n);
        return r;
    }
}
