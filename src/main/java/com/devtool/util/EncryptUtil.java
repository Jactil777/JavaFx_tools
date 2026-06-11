package com.devtool.util;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 加密解密工具类
 * 支持：MD5 / SHA系列 / HMAC / AES / Base64 / URL编解码
 */
public class EncryptUtil {

    private EncryptUtil() {}

    // ─── Hash 摘要 ──────────────────────────────────────────────────────────

    public static String md5(String input) throws Exception {
        return hash(input, "MD5");
    }

    public static String sha1(String input) throws Exception {
        return hash(input, "SHA-1");
    }

    public static String sha256(String input) throws Exception {
        return hash(input, "SHA-256");
    }

    public static String sha512(String input) throws Exception {
        return hash(input, "SHA-512");
    }

    private static String hash(String input, String algorithm) throws Exception {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(bytes);
    }

    // ─── HMAC ──────────────────────────────────────────────────────────────

    public static String hmacSha256(String input, String key) throws Exception {
        return hmac(input, key, "HmacSHA256");
    }

    public static String hmacSha1(String input, String key) throws Exception {
        return hmac(input, key, "HmacSHA1");
    }

    public static String hmacSha512(String input, String key) throws Exception {
        return hmac(input, key, "HmacSHA512");
    }

    private static String hmac(String input, String key, String algorithm) throws Exception {
        if (key == null || key.isEmpty()) throw new IllegalArgumentException("HMAC 密钥不能为空");
        Mac mac = Mac.getInstance(algorithm);
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), algorithm);
        mac.init(keySpec);
        byte[] bytes = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(bytes);
    }

    // ─── AES ───────────────────────────────────────────────────────────────

    public enum AesMode { ECB, CBC }

    /**
     * AES 加密
     * @param plaintext 明文
     * @param key       密钥（ECB: 16/32字节；CBC: 同上）
     * @param iv        IV 向量（CBC 模式必须传 16 字节；ECB 传 null）
     * @param mode      ECB / CBC
     * @return Base64 编码的密文
     */
    public static String aesEncrypt(String plaintext, String key, String iv, AesMode mode) throws Exception {
        Cipher cipher = buildAesCipher(Cipher.ENCRYPT_MODE, key, iv, mode);
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * AES 解密
     * @param ciphertext Base64 编码的密文
     * @param key        密钥
     * @param iv         IV 向量（CBC 必须与加密时相同）
     * @param mode       ECB / CBC
     * @return 明文
     */
    public static String aesDecrypt(String ciphertext, String key, String iv, AesMode mode) throws Exception {
        Cipher cipher = buildAesCipher(Cipher.DECRYPT_MODE, key, iv, mode);
        byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(ciphertext.trim()));
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private static Cipher buildAesCipher(int opmode, String key, String iv, AesMode mode) throws Exception {
        byte[] keyBytes = paddedKey(key);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        String transformation = (mode == AesMode.CBC) ? "AES/CBC/PKCS5Padding" : "AES/ECB/PKCS5Padding";
        Cipher cipher = Cipher.getInstance(transformation);
        if (mode == AesMode.CBC) {
            if (iv == null || iv.isEmpty()) throw new IllegalArgumentException("CBC 模式必须提供 IV（16 字符）");
            byte[] ivBytes = paddedIv(iv);
            cipher.init(opmode, keySpec, new IvParameterSpec(ivBytes));
        } else {
            cipher.init(opmode, keySpec);
        }
        return cipher;
    }

    /** 自动补全/截断密钥到 16 或 32 字节（优先 16，超过 16 则 32） */
    private static byte[] paddedKey(String key) {
        if (key == null || key.isEmpty()) throw new IllegalArgumentException("AES 密钥不能为空");
        byte[] raw = key.getBytes(StandardCharsets.UTF_8);
        int targetLen = raw.length <= 16 ? 16 : 32;
        byte[] result = new byte[targetLen];
        System.arraycopy(raw, 0, result, 0, Math.min(raw.length, targetLen));
        return result;
    }

    /** 自动补全/截断 IV 到 16 字节 */
    private static byte[] paddedIv(String iv) {
        byte[] raw = iv.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[16];
        System.arraycopy(raw, 0, result, 0, Math.min(raw.length, 16));
        return result;
    }

    // ─── Base64 ────────────────────────────────────────────────────────────

    /** Base64 标准编码 */
    public static String base64Encode(String input) {
        return Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }

    /** Base64 标准解码 */
    public static String base64Decode(String input) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(input.trim());
        return new String(decoded, StandardCharsets.UTF_8);
    }

    /** Base64 URL 安全编码（无 +/= 替换为 -_）*/
    public static String base64UrlEncode(String input) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }

    /** Base64 URL 安全解码 */
    public static String base64UrlDecode(String input) throws Exception {
        byte[] decoded = Base64.getUrlDecoder().decode(input.trim());
        return new String(decoded, StandardCharsets.UTF_8);
    }

    // ─── URL 编解码 ────────────────────────────────────────────────────────

    public static String urlEncode(String input) throws Exception {
        return URLEncoder.encode(input, StandardCharsets.UTF_8)
                .replace("+", "%20");  // 空格统一用 %20
    }

    public static String urlDecode(String input) throws Exception {
        return URLDecoder.decode(input, StandardCharsets.UTF_8);
    }

    // ─── 工具方法 ──────────────────────────────────────────────────────────

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** 格式化 Hash 输出：原始 hex / 大写 hex / 分组显示 */
    public static String formatHash(String hex, boolean uppercase, boolean grouped) {
        String result = uppercase ? hex.toUpperCase() : hex.toLowerCase();
        if (grouped) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < result.length(); i += 8) {
                if (i > 0) sb.append(' ');
                sb.append(result, i, Math.min(i + 8, result.length()));
            }
            return sb.toString();
        }
        return result;
    }

    /** 判断字符串是否是合法 Base64 */
    public static boolean isValidBase64(String s) {
        try {
            Base64.getDecoder().decode(s.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

