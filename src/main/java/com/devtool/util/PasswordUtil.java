package com.devtool.util;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 随机密码生成工具类
 */
public class PasswordUtil {

    private PasswordUtil() {}

    private static final SecureRandom RANDOM = new SecureRandom();

    public static final String UPPERCASE  = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String LOWERCASE  = "abcdefghijklmnopqrstuvwxyz";
    public static final String DIGITS     = "0123456789";
    public static final String SYMBOLS    = "!@#$%^&*()-_=+[]{}|;:,.<>?";

    // 易混淆字符
    private static final String AMBIGUOUS = "0O1lI";

    public enum Strength { WEAK, MEDIUM, STRONG, VERY_STRONG }

    /** 密码生成配置 */
    public static class Config {
        public int     length        = 16;
        public boolean useUppercase  = true;
        public boolean useLowercase  = true;
        public boolean useDigits     = true;
        public boolean useSymbols    = true;
        public boolean noAmbiguous   = false;
        public boolean eachTypeMustExist = true;
        public String  customSymbols = "";   // 为空则用默认
    }

    /**
     * 生成一批密码
     */
    public static List<String> generate(Config cfg, int count) {
        String pool = buildPool(cfg);
        if (pool.isEmpty()) throw new IllegalArgumentException("请至少选择一种字符集");
        if (cfg.length < 1 || cfg.length > 128)
            throw new IllegalArgumentException("密码长度必须在 1-128 之间");

        List<String> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            results.add(generateOne(cfg, pool));
        }
        return results;
    }

    private static String generateOne(Config cfg, String pool) {
        List<Character> chars = new ArrayList<>(cfg.length);

        // 如果要求每种类型至少一个
        if (cfg.eachTypeMustExist) {
            if (cfg.useUppercase) chars.add(randomChar(filtered(UPPERCASE, cfg.noAmbiguous)));
            if (cfg.useLowercase) chars.add(randomChar(filtered(LOWERCASE, cfg.noAmbiguous)));
            if (cfg.useDigits)    chars.add(randomChar(filtered(DIGITS,    cfg.noAmbiguous)));
            if (cfg.useSymbols) {
                String sym = cfg.customSymbols.isBlank() ? SYMBOLS : cfg.customSymbols;
                chars.add(randomChar(filtered(sym, cfg.noAmbiguous)));
            }
        }

        // 补足剩余长度
        while (chars.size() < cfg.length) {
            chars.add(randomChar(pool));
        }

        // 截断到指定长度（确保不超过）
        while (chars.size() > cfg.length) {
            chars.remove(RANDOM.nextInt(chars.size()));
        }

        // 打乱顺序
        Collections.shuffle(chars, RANDOM);

        StringBuilder sb = new StringBuilder(cfg.length);
        for (char c : chars) sb.append(c);
        return sb.toString();
    }

    /** 构建字符池 */
    private static String buildPool(Config cfg) {
        StringBuilder sb = new StringBuilder();
        if (cfg.useUppercase) sb.append(filtered(UPPERCASE, cfg.noAmbiguous));
        if (cfg.useLowercase) sb.append(filtered(LOWERCASE, cfg.noAmbiguous));
        if (cfg.useDigits)    sb.append(filtered(DIGITS,    cfg.noAmbiguous));
        if (cfg.useSymbols) {
            String sym = cfg.customSymbols.isBlank() ? SYMBOLS : cfg.customSymbols;
            sb.append(filtered(sym, cfg.noAmbiguous));
        }
        return sb.toString();
    }

    /** 过滤易混淆字符 */
    private static String filtered(String input, boolean noAmbiguous) {
        if (!noAmbiguous) return input;
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (AMBIGUOUS.indexOf(c) < 0) sb.append(c);
        }
        return sb.toString();
    }

    private static char randomChar(String pool) {
        if (pool.isEmpty()) return '?';
        return pool.charAt(RANDOM.nextInt(pool.length()));
    }

    /**
     * 评估密码强度
     */
    public static Strength evaluate(String password) {
        if (password == null || password.length() < 4) return Strength.WEAK;

        int score = 0;
        boolean hasUpper   = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower   = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit   = password.chars().anyMatch(Character::isDigit);
        boolean hasSymbol  = password.chars().anyMatch(c -> SYMBOLS.indexOf(c) >= 0);
        int     len        = password.length();

        if (len >= 8)  score++;
        if (len >= 12) score++;
        if (len >= 16) score++;
        if (hasUpper)  score++;
        if (hasLower)  score++;
        if (hasDigit)  score++;
        if (hasSymbol) score++;

        if (score <= 2) return Strength.WEAK;
        if (score <= 4) return Strength.MEDIUM;
        if (score <= 5) return Strength.STRONG;
        return Strength.VERY_STRONG;
    }

    /** 描述字符集组成 */
    public static String describePool(Config cfg) {
        List<String> parts = new ArrayList<>();
        if (cfg.useUppercase) parts.add("大写");
        if (cfg.useLowercase) parts.add("小写");
        if (cfg.useDigits)    parts.add("数字");
        if (cfg.useSymbols)   parts.add("符号");
        int poolSize = buildPool(cfg).length();
        return String.join(" + ", parts) + "  |  字符池 " + poolSize + " 个";
    }
}

