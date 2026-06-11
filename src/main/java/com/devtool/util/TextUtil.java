package com.devtool.util;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 文本批量处理工具类
 * 支持：批量替换、去空行/空格、大小写转换、排序去重、行号处理、编码转换、统计
 */
public class TextUtil {

    private TextUtil() {}

    // ─── 批量替换 ─────────────────────────────────────────────────────────

    /**
     * 普通替换（支持多组替换规则，每行格式: 旧文本->新文本）
     * @param text  原始文本
     * @param rules 替换规则，每行一条，格式 "旧内容->新内容"
     * @return 替换结果 + 替换次数统计
     */
    public static ReplaceResult replace(String text, String rules, boolean regex, boolean ignoreCase) {
        if (text == null) return new ReplaceResult("", 0, new ArrayList<>());
        if (rules == null || rules.isBlank()) return new ReplaceResult(text, 0, new ArrayList<>());

        List<String> log = new ArrayList<>();
        String result = text;
        int totalCount = 0;

        for (String line : rules.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || !line.contains("->")) continue;
            int arrowIdx = line.indexOf("->");
            String from = line.substring(0, arrowIdx);
            String to   = line.substring(arrowIdx + 2);
            if (from.isEmpty()) continue;

            String before = result;
            if (regex) {
                int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
                result = Pattern.compile(from, flags).matcher(result).replaceAll(to);
            } else {
                if (ignoreCase) {
                    result = result.replaceAll("(?i)" + Pattern.quote(from), to.replace("$", "\\$"));
                } else {
                    result = result.replace(from, to);
                }
            }
            int count = countOccurrences(before, from, ignoreCase);
            if (count > 0) {
                totalCount += count;
                log.add("替换「" + from + "」→「" + to + "」共 " + count + " 处");
            }
        }
        return new ReplaceResult(result, totalCount, log);
    }

    public record ReplaceResult(String text, int count, List<String> log) {}

    private static int countOccurrences(String text, String pattern, boolean ignoreCase) {
        if (pattern.isEmpty()) return 0;
        String t = ignoreCase ? text.toLowerCase() : text;
        String p = ignoreCase ? pattern.toLowerCase() : pattern;
        int count = 0, idx = 0;
        while ((idx = t.indexOf(p, idx)) >= 0) { count++; idx += p.length(); }
        return count;
    }

    // ─── 行操作 ───────────────────────────────────────────────────────────

    /** 去空行 */
    public static String removeEmptyLines(String text) {
        if (text == null) return "";
        return Arrays.stream(text.split("\n", -1))
                .filter(line -> !line.trim().isEmpty())
                .collect(Collectors.joining("\n"));
    }

    /** 去重复行（保留第一次出现） */
    public static String removeDuplicateLines(String text, boolean trimBeforeCompare) {
        if (text == null) return "";
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            String key = trimBeforeCompare ? line.trim() : line;
            if (seen.add(key)) result.add(line);
        }
        return String.join("\n", result);
    }

    /** 行排序 */
    public static String sortLines(String text, boolean ascending, boolean ignoreCase) {
        if (text == null) return "";
        String[] lines = text.split("\n", -1);
        Comparator<String> cmp = ignoreCase
                ? String.CASE_INSENSITIVE_ORDER
                : Comparator.naturalOrder();
        if (!ascending) cmp = cmp.reversed();
        return Arrays.stream(lines).sorted(cmp).collect(Collectors.joining("\n"));
    }

    /** 行逆序 */
    public static String reverseLines(String text) {
        if (text == null) return "";
        List<String> lines = Arrays.asList(text.split("\n", -1));
        Collections.reverse(lines);
        return String.join("\n", lines);
    }

    /** 添加行号 */
    public static String addLineNumbers(String text, String separator) {
        if (text == null) return "";
        String sep = (separator == null || separator.isEmpty()) ? ". " : separator;
        String[] lines = text.split("\n", -1);
        int width = String.valueOf(lines.length).length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(String.format("%" + width + "d", i + 1)).append(sep).append(lines[i]);
            if (i < lines.length - 1) sb.append("\n");
        }
        return sb.toString();
    }

    /** 去行号（去掉每行开头的数字+分隔符） */
    public static String removeLineNumbers(String text) {
        if (text == null) return "";
        return text.replaceAll("(?m)^\\s*\\d+[.\\)\\-:|>\\s]+", "");
    }

    /** 每行首尾去空格 */
    public static String trimLines(String text) {
        if (text == null) return "";
        return Arrays.stream(text.split("\n", -1))
                .map(String::trim)
                .collect(Collectors.joining("\n"));
    }

    /** 过滤包含/不包含关键词的行 */
    public static String filterLines(String text, String keyword, boolean keepMatching, boolean regex, boolean ignoreCase) {
        if (text == null) return "";
        if (keyword == null || keyword.isBlank()) return text;
        return Arrays.stream(text.split("\n", -1))
                .filter(line -> {
                    boolean matches;
                    if (regex) {
                        int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
                        matches = Pattern.compile(keyword, flags).matcher(line).find();
                    } else {
                        matches = ignoreCase
                                ? line.toLowerCase().contains(keyword.toLowerCase())
                                : line.contains(keyword);
                    }
                    return keepMatching == matches;
                })
                .collect(Collectors.joining("\n"));
    }

    // ─── 大小写转换 ───────────────────────────────────────────────────────

    public static String toUpperCase(String text) { return text == null ? "" : text.toUpperCase(); }
    public static String toLowerCase(String text) { return text == null ? "" : text.toLowerCase(); }

    /** 首字母大写（每个单词） */
    public static String toTitleCase(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c) || c == '\n') { nextUpper = true; sb.append(c); }
            else if (nextUpper) { sb.append(Character.toUpperCase(c)); nextUpper = false; }
            else { sb.append(c); }
        }
        return sb.toString();
    }

    /** camelCase → snake_case */
    public static String toSnakeCase(String text) {
        if (text == null) return "";
        return Arrays.stream(text.split("\n", -1))
                .map(line -> line.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase())
                .collect(Collectors.joining("\n"));
    }

    /** snake_case → camelCase */
    public static String toCamelCase(String text) {
        if (text == null) return "";
        return Arrays.stream(text.split("\n", -1))
                .map(line -> {
                    String[] parts = line.split("_");
                    if (parts.length <= 1) return line;
                    StringBuilder sb = new StringBuilder(parts[0].toLowerCase());
                    for (int i = 1; i < parts.length; i++) {
                        if (!parts[i].isEmpty())
                            sb.append(Character.toUpperCase(parts[i].charAt(0)))
                              .append(parts[i].substring(1).toLowerCase());
                    }
                    return sb.toString();
                })
                .collect(Collectors.joining("\n"));
    }

    // ─── 空格/缩进处理 ────────────────────────────────────────────────────

    /** 去所有空格 */
    public static String removeAllSpaces(String text) {
        return text == null ? "" : text.replaceAll("[ \\t]", "");
    }

    /** 合并多个连续空格为一个 */
    public static String mergeSpaces(String text) {
        return text == null ? "" : text.replaceAll("[ \\t]+", " ");
    }

    /** Tab 转空格 */
    public static String tabToSpaces(String text, int spaceCount) {
        if (text == null) return "";
        return text.replace("\t", " ".repeat(Math.max(1, spaceCount)));
    }

    /** 空格转 Tab */
    public static String spacesToTab(String text, int spaceCount) {
        if (text == null) return "";
        return text.replace(" ".repeat(Math.max(1, spaceCount)), "\t");
    }

    // ─── 编码转换 ─────────────────────────────────────────────────────────

    /** Convert text to Unicode escape sequences */
    public static String toUnicode(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c > 127) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.toString();
    }

    /** Unicode 转义 → 文本 */
    public static String fromUnicode(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (i + 5 < text.length() && text.charAt(i) == '\\' && text.charAt(i + 1) == 'u') {
                try {
                    int codePoint = Integer.parseInt(text.substring(i + 2, i + 6), 16);
                    sb.append((char) codePoint);
                    i += 6;
                    continue;
                } catch (NumberFormatException ignored) {}
            }
            sb.append(text.charAt(i++));
        }
        return sb.toString();
    }

    /** 文本 → HTML 实体转义 */
    public static String toHtmlEntities(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                   .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** HTML 实体 → 文本 */
    public static String fromHtmlEntities(String text) {
        if (text == null) return "";
        return text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                   .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'");
    }

    // ─── 统计信息 ─────────────────────────────────────────────────────────

    public record TextStats(int chars, int charsNoSpace, int words, int lines,
                            int chinesChars, Map<Character, Integer> topChars) {}

    public static TextStats stats(String text) {
        if (text == null || text.isEmpty())
            return new TextStats(0, 0, 0, 0, 0, new LinkedHashMap<>());

        int chars        = text.length();
        int charsNoSpace = text.replaceAll("\\s", "").length();
        int words        = text.isBlank() ? 0 : text.trim().split("[\\s\\pP]+").length;
        int lines        = text.split("\n", -1).length;
        int chinese      = (int) text.chars().filter(c -> c >= 0x4E00 && c <= 0x9FFF).count();

        // 出现频率最高的 5 个字符（非空白）
        Map<Character, Integer> freq = new LinkedHashMap<>();
        for (char c : text.toCharArray()) {
            if (!Character.isWhitespace(c)) freq.merge(c, 1, Integer::sum);
        }
        Map<Character, Integer> top = freq.entrySet().stream()
                .sorted(Map.Entry.<Character, Integer>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));

        return new TextStats(chars, charsNoSpace, words, lines, chinese, top);
    }
}

