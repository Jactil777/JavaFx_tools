package com.devtool.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * AI 助手工具类：配置持久化 + OpenAI-compatible Chat Completions 调用。
 */
public class AiAssistantUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private static final Path CONFIG_DIR = Path.of(System.getProperty("user.home"), ".devtoolbox");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("ai-assistant.properties");
    private static final Path CONVERSATION_FILE = CONFIG_DIR.resolve("ai-conversations.json");

    private AiAssistantUtil() {}

    public record AiConfig(
            String endpoint,
            String model,
            String apiKey,
            String systemPrompt,
            double temperature,
            int maxTokens
    ) {
        public AiConfig normalized() {
            return new AiConfig(
                    blankToDefault(endpoint, "https://api.openai.com/v1/chat/completions"),
                    blankToDefault(model, "gpt-4o-mini"),
                    apiKey == null ? "" : apiKey.trim(),
                    blankToDefault(systemPrompt, "你是 DevToolBox 内置的开发助手，回答要简洁、准确、可执行。"),
                    clamp(temperature, 0.0, 2.0),
                    Math.max(1, maxTokens)
            );
        }
    }

    public record ChatMessage(String role, String content) {}

    public record ArkModel(String displayName, String modelId, String provider) {}

    public static class Conversation {
        public String id;
        public String title;
        public String modelId;
        public long createdAt;
        public long updatedAt;
        public List<ChatMessage> messages = new ArrayList<>();

        public Conversation() {}

        public Conversation(String title) {
            long now = Instant.now().toEpochMilli();
            this.id = UUID.randomUUID().toString();
            this.title = blankToDefault(title, "新对话");
            this.modelId = arkConfig("").model();
            this.createdAt = now;
            this.updatedAt = now;
            this.messages = new ArrayList<>();
        }

        public boolean isEmpty() {
            return messages == null || messages.isEmpty();
        }
    }

    public static AiConfig defaultConfig() {
        return arkConfig("");
    }

    public static AiConfig arkConfig(String apiKey) {
        return new AiConfig(
                "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
                "doubao-seed-2-0-mini-260428",
                apiKey == null ? "" : apiKey,
                "你是 DevToolBox 内置的开发助手，回答要简洁、准确、可执行。优先使用中文回答。",
                0.7,
                1200
        );
    }

    public static List<ArkModel> arkModels() {
        return List.of(
                new ArkModel("Doubao-Seed-2.0-mini", "doubao-seed-2-0-mini-260428", "字节跳动"),
                new ArkModel("Doubao-Seed-2.0-pro", "doubao-seed-2-0-pro-260215", "字节跳动"),
                new ArkModel("Doubao-Seed-2.0-lite", "doubao-seed-2-0-lite-260428", "字节跳动"),
                new ArkModel("Doubao-Seed-2.0-Code", "doubao-seed-2-0-code-preview-260215", "字节跳动"),
                new ArkModel("Doubao-Seed-1.8", "doubao-seed-1-8-251228", "字节跳动"),
                new ArkModel("Doubao-Seed-Character", "doubao-seed-character-251128", "字节跳动"),
                new ArkModel("GLM-4.7", "glm-4-7-251222", "智谱AI"),
                new ArkModel("DeepSeek-V3.2", "deepseek-v3-2-251201", "DeepSeek"),
                new ArkModel("DeepSeek-V4-flash", "deepseek-v4-flash-260425", "DeepSeek"),
                new ArkModel("DeepSeek-V4-pro", "deepseek-v4-pro-260425", "DeepSeek")
        );
    }

    public static List<String> arkModelIds() {
        return arkModels().stream().map(ArkModel::modelId).toList();
    }

    public static String displayNameForModel(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return "";
        }
        return arkModels().stream()
                .filter(model -> model.modelId().equals(modelId))
                .map(ArkModel::displayName)
                .findFirst()
                .orElse(modelId);
    }

    public static AiConfig loadConfig() {
        AiConfig defaults = defaultConfig();
        if (!Files.exists(CONFIG_FILE)) {
            return defaults;
        }

        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
            props.load(reader);
            return new AiConfig(
                    props.getProperty("endpoint", defaults.endpoint()),
                    props.getProperty("model", defaults.model()),
                    props.getProperty("apiKey", defaults.apiKey()),
                    props.getProperty("systemPrompt", defaults.systemPrompt()),
                    parseDouble(props.getProperty("temperature"), defaults.temperature()),
                    parseInt(props.getProperty("maxTokens"), defaults.maxTokens())
            ).normalized();
        } catch (Exception e) {
            return defaults;
        }
    }

    public static void saveConfig(AiConfig config) throws IOException {
        AiConfig normalized = config.normalized();
        Files.createDirectories(CONFIG_DIR);

        Properties props = new Properties();
        props.setProperty("endpoint", normalized.endpoint());
        props.setProperty("model", normalized.model());
        props.setProperty("apiKey", normalized.apiKey());
        props.setProperty("systemPrompt", normalized.systemPrompt());
        props.setProperty("temperature", String.valueOf(normalized.temperature()));
        props.setProperty("maxTokens", String.valueOf(normalized.maxTokens()));

        try (var writer = Files.newBufferedWriter(CONFIG_FILE, StandardCharsets.UTF_8)) {
            props.store(writer, "DevToolBox AI Assistant Settings");
        }
    }

    public static List<Conversation> loadConversations() {
        if (!Files.exists(CONVERSATION_FILE)) {
            return new ArrayList<>();
        }
        try {
            List<Conversation> conversations = MAPPER.readValue(
                    Files.readString(CONVERSATION_FILE, StandardCharsets.UTF_8),
                    new TypeReference<>() {}
            );
            conversations.removeIf(conversation -> conversation == null || conversation.id == null);
            for (Conversation conversation : conversations) {
                if (conversation.title == null || conversation.title.isBlank()) {
                    conversation.title = "未命名对话";
                }
                if (conversation.messages == null) {
                    conversation.messages = new ArrayList<>();
                }
                if (conversation.modelId == null || conversation.modelId.isBlank()) {
                    conversation.modelId = defaultConfig().model();
                }
            }
            conversations.sort(Comparator.comparingLong((Conversation c) -> c.updatedAt).reversed());
            return conversations;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static void saveConversations(List<Conversation> conversations) throws IOException {
        Files.createDirectories(CONFIG_DIR);
        List<Conversation> safeList = conversations == null ? List.of() : conversations;
        MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(CONVERSATION_FILE.toFile(), safeList);
    }

    public static String chat(AiConfig config, List<ChatMessage> messages) throws Exception {
        AiConfig normalized = config.normalized();
        if (normalized.apiKey().isBlank()) {
            throw new IllegalArgumentException("请先填写 API Key");
        }
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("请输入要发送的内容");
        }

        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", normalized.model());
        body.put("temperature", normalized.temperature());
        body.put("max_tokens", normalized.maxTokens());

        ArrayNode msgArray = body.putArray("messages");
        if (!normalized.systemPrompt().isBlank()) {
            appendMessage(msgArray, "system", normalized.systemPrompt());
        }
        for (ChatMessage message : messages) {
            if (message != null && message.content() != null && !message.content().isBlank()) {
                appendMessage(msgArray, message.role(), message.content());
            }
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalized.endpoint()))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + normalized.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(parseError(response.body(), response.statusCode()));
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new IOException("AI 返回内容为空");
        }
        return content.asText();
    }

    public static List<ChatMessage> trimHistory(List<ChatMessage> messages, int maxPairs) {
        if (messages == null || messages.size() <= maxPairs * 2) {
            return messages == null ? List.of() : new ArrayList<>(messages);
        }
        int from = Math.max(0, messages.size() - maxPairs * 2);
        return new ArrayList<>(messages.subList(from, messages.size()));
    }

    private static void appendMessage(ArrayNode array, String role, String content) {
        ObjectNode node = array.addObject();
        node.put("role", blankToDefault(role, "user"));
        node.put("content", content);
    }

    private static String parseError(String body, int statusCode) {
        try {
            JsonNode error = MAPPER.readTree(body).path("error");
            String message = error.path("message").asText();
            if (!message.isBlank()) {
                return "请求失败（HTTP " + statusCode + "）：" + message;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "请求失败（HTTP " + statusCode + "）：" + body;
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static double parseDouble(String value, double defaultValue) {
        try {
            return value == null ? defaultValue : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static int parseInt(String value, int defaultValue) {
        try {
            return value == null ? defaultValue : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
