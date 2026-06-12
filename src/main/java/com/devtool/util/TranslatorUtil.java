package com.devtool.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 翻译工具类
 * 封装 MyMemory 翻译 API + Free Dictionary API + 单词本本地存储
 */
public class TranslatorUtil {

    private static final Logger log = LoggerFactory.getLogger(TranslatorUtil.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private TranslatorUtil() {}

    // ─── MyMemory 翻译 API ────────────────────────────────────────────────

    private static final String MYMEMORY_URL = "https://api.mymemory.translated.net/get";

    /**
     * 调用 MyMemory API 翻译文本
     * @param text       待翻译文本
     * @param sourceLang 源语言代码（如 "en", "zh", "ja"），传 "auto" 自动检测
     * @param targetLang 目标语言代码
     * @return 翻译结果
     */
    public static String translate(String text, String sourceLang, String targetLang) throws Exception {
        if (text == null || text.isBlank()) return "";

        String langPair = "auto".equals(sourceLang)
                ? "autodetect|" + targetLang
                : sourceLang + "|" + targetLang;

        String urlStr = MYMEMORY_URL
                + "?q=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                + "&langpair=" + URLEncoder.encode(langPair, StandardCharsets.UTF_8);

        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            String body = sb.toString();

            JsonNode root = MAPPER.readTree(body);
            JsonNode resp = root.path("responseData");
            String translated = resp.path("translatedText").asText("");

            // 尝试获取更多匹配结果
            List<String> alternatives = new ArrayList<>();
            JsonNode matches = root.path("matches");
            if (matches.isArray()) {
                for (JsonNode m : matches) {
                    String alt = m.path("translation").asText("");
                    if (!alt.isEmpty() && !alt.equals(translated)) {
                        alternatives.add(alt);
                    }
                }
            }

            if (alternatives.isEmpty()) return translated;
            // 返回主翻译 + 备选
            return translated + "\n\n—— 其他参考 ——\n" + String.join("\n", alternatives);
        } finally {
            conn.disconnect();
        }
    }

    // ─── Free Dictionary API（英文单词释义）────────────────────────────────

    private static final String DICT_URL = "https://api.dictionaryapi.dev/api/v2/entries/en/";

    /**
     * 查询英文单词的详细释义（音标、词性、释义、例句）
     * @param word 英文单词
     * @return 格式化后的释义文本，查询失败返回 null
     */
    public static WordDefinition lookupWord(String word) {
        if (word == null || word.isBlank()) return null;
        word = word.trim().toLowerCase();

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(DICT_URL + URLEncoder.encode(word, StandardCharsets.UTF_8)).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);

            if (conn.getResponseCode() != 200) return null;

            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                JsonNode root = MAPPER.readTree(sb.toString());
                return parseDefinition(root, word);
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            log.debug("单词查询失败: {}", word, e);
            return null;
        }
    }

    private static WordDefinition parseDefinition(JsonNode root, String word) {
        JsonNode entry = root.isArray() ? root.get(0) : root;
        if (entry == null) return null;

        // 音标
        String phonetic = entry.path("phonetic").asText("");
        if (phonetic.isEmpty()) {
            JsonNode phonetics = entry.path("phonetics");
            for (JsonNode p : phonetics) {
                String pt = p.path("text").asText("");
                if (!pt.isEmpty()) { phonetic = pt; break; }
            }
        }

        // 各词性的释义
        List<MeaningGroup> groups = new ArrayList<>();
        JsonNode meanings = entry.path("meanings");
        for (JsonNode m : meanings) {
            String partOfSpeech = m.path("partOfSpeech").asText("");
            List<String> defs = new ArrayList<>();
            JsonNode definitions = m.path("definitions");
            for (JsonNode d : definitions) {
                String def = d.path("definition").asText("");
                String example = d.path("example").asText("");
                if (!def.isEmpty()) {
                    defs.add(example.isEmpty() ? def : def + "  （例：" + example + "）");
                }
            }
            if (!defs.isEmpty()) {
                groups.add(new MeaningGroup(partOfSpeech, defs));
            }
        }

        // 变形词
        List<String> forms = new ArrayList<>();
        JsonNode formsArr = entry.path("forms");
        for (JsonNode f : formsArr) {
            String form = f.asText("");
            if (!form.isEmpty() && !form.equalsIgnoreCase(word)) forms.add(form);
        }

        if (groups.isEmpty() && phonetic.isEmpty()) return null;
        return new WordDefinition(word, phonetic, groups, forms);
    }

    /**
     * 单词释义数据
     */
    public static class WordDefinition {
        public final String word;
        public final String phonetic;
        public final List<MeaningGroup> meanings;
        public final List<String> wordForms;

        public WordDefinition(String word, String phonetic, List<MeaningGroup> meanings, List<String> wordForms) {
            this.word = word;
            this.phonetic = phonetic;
            this.meanings = meanings;
            this.wordForms = wordForms;
        }

        /** 生成用于单词本存储的简洁摘要 */
        public String toSummary() {
            StringBuilder sb = new StringBuilder();
            if (!phonetic.isEmpty()) sb.append(phonetic).append("  ");
            for (MeaningGroup g : meanings) {
                sb.append(g.partOfSpeech).append(". ");
                sb.append(String.join("; ", g.definitions));
                sb.append(";  ");
            }
            return sb.toString().trim();
        }

        /** 生成用于 UI 显示的完整格式化文本 */
        public String toDisplayText() {
            StringBuilder sb = new StringBuilder();
            sb.append(word);
            if (!phonetic.isEmpty()) sb.append("   ").append(phonetic);
            sb.append("\n");
            for (MeaningGroup g : meanings) {
                sb.append("  ").append(g.partOfSpeech).append(".  ");
                for (int i = 0; i < g.definitions.size(); i++) {
                    if (i > 0) sb.append("\n       ");
                    sb.append(g.definitions.get(i));
                }
                sb.append("\n");
            }
            if (!wordForms.isEmpty()) {
                sb.append("  变形: ").append(String.join(", ", wordForms));
            }
            return sb.toString();
        }
    }

    public static class MeaningGroup {
        public final String partOfSpeech;
        public final List<String> definitions;

        public MeaningGroup(String partOfSpeech, List<String> definitions) {
            this.partOfSpeech = partOfSpeech;
            this.definitions = definitions;
        }
    }

    // ─── 单词本本地存储 ───────────────────────────────────────────────────

    private static final String WORD_BOOK_DIR = "wordbook";
    private static final String DEFAULT_BOOK = "我的生词本";

    /**
     * 获取单词本存储目录
     */
    private static Path getBookDir() {
        Path dir = Path.of(WORD_BOOK_DIR);
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        return dir;
    }

    /**
     * 加载单词本（JSON 文件 → Map<单词, 释义摘要>）
     */
    public static Map<String, String> loadWordBook(String bookName) {
        Path file = getBookDir().resolve(sanitizeFileName(bookName) + ".json");
        if (!Files.exists(file)) return new LinkedHashMap<>();
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonNode root = MAPPER.readTree(json);
            Map<String, String> map = new LinkedHashMap<>();
            root.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue().asText("")));
            return map;
        } catch (Exception e) {
            log.error("加载单词本失败: {}", bookName, e);
            return new LinkedHashMap<>();
        }
    }

    /**
     * 保存单词本
     */
    public static void saveWordBook(String bookName, Map<String, String> words) {
        Path file = getBookDir().resolve(sanitizeFileName(bookName) + ".json");
        try {
            JsonNode node = MAPPER.valueToTree(words);
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("保存单词本失败: {}", bookName, e);
        }
    }

    /**
     * 添加单词到单词本
     */
    public static void addWord(String bookName, String word, String meaning) {
        Map<String, String> book = loadWordBook(bookName);
        book.put(word.toLowerCase(), meaning);
        saveWordBook(bookName, book);
    }

    /**
     * 从单词本删除单词
     */
    public static void removeWord(String bookName, String word) {
        Map<String, String> book = loadWordBook(bookName);
        book.remove(word.toLowerCase());
        saveWordBook(bookName, book);
    }

    /**
     * 判断单词是否已在单词本中
     */
    public static boolean isInWordBook(String bookName, String word) {
        Map<String, String> book = loadWordBook(bookName);
        return book.containsKey(word.toLowerCase());
    }

    /**
     * 获取所有单词本名称
     */
    public static List<String> listWordBooks() {
        Path dir = getBookDir();
        List<String> names = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                  .forEach(p -> names.add(p.getFileName().toString().replace(".json", "")));
        } catch (IOException ignored) {}
        if (names.isEmpty()) names.add(DEFAULT_BOOK);
        return names;
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]", "_");
    }
}
