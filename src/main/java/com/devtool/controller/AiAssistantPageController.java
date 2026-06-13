package com.devtool.controller;

import com.devtool.util.AiAssistantUtil;
import com.devtool.util.AiAssistantUtil.AiConfig;
import com.devtool.util.AiAssistantUtil.ChatMessage;
import com.devtool.util.AiAssistantUtil.Conversation;
import com.devtool.util.SystemUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * AI 助手页面控制器。
 */
public class AiAssistantPageController extends BaseController {

    @FXML private TextField endpointField;
    @FXML private ComboBox<String> modelCombo;
    @FXML private PasswordField apiKeyField;
    @FXML private TextArea systemPromptArea;
    @FXML private Slider temperatureSlider;
    @FXML private Label temperatureLabel;
    @FXML private Spinner<Integer> maxTokensSpinner;

    @FXML private ListView<Conversation> conversationList;
    @FXML private ListView<ChatMessage> messageList;
    @FXML private TextArea inputArea;
    @FXML private Label currentTitleLabel;
    @FXML private Label currentMetaLabel;
    @FXML private Label currentModelLabel;
    @FXML private Label statusLabel;
    @FXML private Button btnSend;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final int REQUEST_CONTEXT_PAIRS = 8;

    private final ObservableList<Conversation> conversations = FXCollections.observableArrayList();
    private final ObservableList<ChatMessage> messageItems = FXCollections.observableArrayList();
    private Conversation currentConversation;
    private String lastAssistantReply = "";

    @FXML
    public void initialize() {
        initConfigControls();
        initConversationList();
        initMessageList();
        initInputShortcuts();
        loadConfigToUi();
        loadConversations();
    }

    @Override
    public void onPageInit() {
        setStatus("就绪 · Ctrl+Enter 发送");
        inputArea.requestFocus();
    }

    @FXML
    private void onNewConversation() {
        Conversation conversation = new Conversation("新对话");
        conversation.modelId = getSelectedModel();
        conversations.add(0, conversation);
        conversationList.getSelectionModel().select(conversation);
        persistConversations();
        inputArea.clear();
        inputArea.requestFocus();
        setStatus("已新建会话");
    }

    @FXML
    private void onDeleteConversation() {
        if (currentConversation == null) {
            setStatus("暂无可删除的会话");
            return;
        }
        conversations.remove(currentConversation);
        if (conversations.isEmpty()) {
            onNewConversation();
        } else {
            conversationList.getSelectionModel().select(0);
            persistConversations();
        }
        setStatus("已删除当前会话");
    }

    @FXML
    private void onClearCurrentConversation() {
        if (currentConversation == null) {
            setStatus("暂无会话");
            return;
        }
        currentConversation.messages.clear();
        currentConversation.updatedAt = nowMillis();
        if ("新对话".equals(currentConversation.title) || currentConversation.title == null) {
            currentConversation.title = "新对话";
        }
        lastAssistantReply = "";
        refreshCurrentConversation();
        resortConversations();
        persistConversations();
        setStatus("当前会话已清空");
        inputArea.requestFocus();
    }

    @FXML
    private void onSend() {
        String question = inputArea.getText();
        if (question == null || question.isBlank()) {
            setStatus("请输入要发送的问题");
            return;
        }

        AiConfig config = readConfigFromUi();
        if (config.apiKey().isBlank()) {
            setStatus("请先填写 API Key");
            apiKeyField.requestFocus();
            return;
        }

        try {
            AiAssistantUtil.saveConfig(config);
        } catch (Exception e) {
            setStatus("配置保存失败：" + e.getMessage());
            return;
        }

        ensureConversation();
        String trimmedQuestion = question.trim();
        currentConversation.modelId = config.model();
        currentConversation.messages.add(new ChatMessage("user", trimmedQuestion));
        currentConversation.title = titleFor(currentConversation, trimmedQuestion);
        currentConversation.updatedAt = nowMillis();
        inputArea.clear();

        refreshCurrentConversation();
        resortConversations();
        persistConversations();
        scrollToBottom();

        btnSend.setDisable(true);
        setStatus("AI 正在回复...");

        List<ChatMessage> requestMessages = AiAssistantUtil.trimHistory(currentConversation.messages, REQUEST_CONTEXT_PAIRS);
        Conversation targetConversation = currentConversation;
        Thread worker = new Thread(() -> {
            try {
                String reply = AiAssistantUtil.chat(config, requestMessages);
                Platform.runLater(() -> {
                    targetConversation.messages.add(new ChatMessage("assistant", reply));
                    targetConversation.updatedAt = nowMillis();
                    lastAssistantReply = reply;
                    if (targetConversation == currentConversation) {
                        refreshCurrentConversation();
                        scrollToBottom();
                    }
                    resortConversations();
                    persistConversations();
                    setStatus("回复完成");
                    btnSend.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    String error = "请求失败：" + e.getMessage();
                    targetConversation.messages.add(new ChatMessage("system", error));
                    targetConversation.updatedAt = nowMillis();
                    if (targetConversation == currentConversation) {
                        refreshCurrentConversation();
                        scrollToBottom();
                    }
                    resortConversations();
                    persistConversations();
                    setStatus("请求失败");
                    btnSend.setDisable(false);
                });
            }
        }, "ai-assistant-request");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void onSaveConfig() {
        try {
            AiAssistantUtil.saveConfig(readConfigFromUi());
            setStatus("配置已保存");
        } catch (Exception e) {
            setStatus("配置保存失败：" + e.getMessage());
        }
    }

    @FXML
    private void onApplyArkPreset() {
        String existingKey = apiKeyField.getText();
        AiConfig config = AiAssistantUtil.arkConfig(existingKey);
        applyConfigToUi(config);
        setStatus("已应用火山方舟预设，模型需先在方舟控制台开通");
    }

    @FXML
    private void onCopyLastReply() {
        if (lastAssistantReply == null || lastAssistantReply.isBlank()) {
            setStatus("暂无可复制的 AI 回复");
            return;
        }
        SystemUtil.copyToClipboardSilent(lastAssistantReply);
        setStatus("已复制最近一次 AI 回复");
    }

    @FXML
    private void onPasteInput() {
        var clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        if (clipboard.hasString()) {
            inputArea.setText(clipboard.getString());
            inputArea.requestFocus();
            inputArea.positionCaret(inputArea.getText().length());
            setStatus("已粘贴到输入框");
        } else {
            setStatus("剪贴板没有文本");
        }
    }

    @FXML
    private void onClearInput() {
        inputArea.clear();
        setStatus("输入已清空");
        inputArea.requestFocus();
    }

    private void initConfigControls() {
        modelCombo.setEditable(true);
        modelCombo.setItems(FXCollections.observableArrayList(AiAssistantUtil.arkModelIds()));
        maxTokensSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(128, 8192, 1200, 128));
        temperatureSlider.valueProperty().addListener((obs, oldVal, newVal) ->
                temperatureLabel.setText(String.format("%.1f", newVal.doubleValue())));
    }

    private void initConversationList() {
        conversationList.setItems(conversations);
        conversationList.setCellFactory(list -> new ListCell<>() {
            private final Label titleLabel = new Label();
            private final Label previewLabel = new Label();
            private final Label timeLabel = new Label();
            private final VBox textBox = new VBox(4, titleLabel, previewLabel);
            private final HBox root = new HBox(8, textBox, timeLabel);

            {
                titleLabel.setStyle("-fx-text-fill:#e7edf7; -fx-font-size:13px; -fx-font-weight:bold;");
                previewLabel.setStyle("-fx-text-fill:#7f8792; -fx-font-size:11px;");
                previewLabel.setMaxHeight(32);
                previewLabel.setWrapText(true);
                timeLabel.setStyle("-fx-text-fill:#5d6673; -fx-font-size:10px;");
                root.setAlignment(Pos.TOP_LEFT);
                HBox.setHgrow(textBox, Priority.ALWAYS);
            }

            @Override
            protected void updateItem(Conversation item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setStyle("-fx-background-color:transparent;");
                    return;
                }
                titleLabel.setText(item.title == null || item.title.isBlank() ? "未命名对话" : item.title);
                previewLabel.setText(previewOf(item));
                timeLabel.setText(formatTime(item.updatedAt));
                setGraphic(root);
                setStyle(isSelected()
                        ? "-fx-background-color:#314766; -fx-background-radius:8; -fx-padding:9 10;"
                        : "-fx-background-color:transparent; -fx-padding:9 10;");
            }

            @Override
            public void updateSelected(boolean selected) {
                super.updateSelected(selected);
                updateItem(getItem(), isEmpty());
            }
        });
        conversationList.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                currentConversation = selected;
                if (selected.modelId != null && !selected.modelId.isBlank()) {
                    modelCombo.setValue(selected.modelId);
                    modelCombo.getEditor().setText(selected.modelId);
                }
                refreshCurrentConversation();
            }
        });
    }

    private void initMessageList() {
        messageList.setItems(messageItems);
        messageList.setCellFactory(list -> new ListCell<>() {
            private final Label roleLabel = new Label();
            private final Label contentLabel = new Label();
            private final VBox bubble = new VBox(5, roleLabel, contentLabel);
            private final Region spacer = new Region();
            private final HBox root = new HBox(10);

            {
                contentLabel.setWrapText(true);
                contentLabel.maxWidthProperty().bind(messageList.widthProperty().multiply(0.70));
                bubble.setMaxWidth(920);
                HBox.setHgrow(spacer, Priority.ALWAYS);
            }

            @Override
            protected void updateItem(ChatMessage item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setStyle("-fx-background-color:transparent;");
                    return;
                }

                boolean user = "user".equalsIgnoreCase(item.role());
                boolean system = "system".equalsIgnoreCase(item.role());
                roleLabel.setText(user ? "你" : system ? "系统" : "AI 助手");
                contentLabel.setText(item.content());
                roleLabel.setStyle("-fx-font-size:11px; -fx-font-weight:bold; -fx-text-fill:" +
                        (user ? "#9ecbff" : system ? "#e06c75" : "#98c379") + ";");
                contentLabel.setTextFill(Color.web("#e8eaed"));
                contentLabel.setStyle("-fx-font-size:13px; -fx-line-spacing:3;");
                bubble.setStyle(system
                        ? "-fx-background-color:#3a2b2b; -fx-border-color:#6b3a3a; -fx-border-radius:10; -fx-background-radius:10; -fx-padding:10 12;"
                        : user
                        ? "-fx-background-color:#264766; -fx-border-color:#3f6f9f; -fx-border-radius:10; -fx-background-radius:10; -fx-padding:10 12;"
                        : "-fx-background-color:#2f3437; -fx-border-color:#485158; -fx-border-radius:10; -fx-background-radius:10; -fx-padding:10 12;");

                root.getChildren().clear();
                if (user) {
                    root.setAlignment(Pos.TOP_RIGHT);
                    root.getChildren().addAll(spacer, bubble);
                } else {
                    root.setAlignment(Pos.TOP_LEFT);
                    root.getChildren().addAll(bubble, spacer);
                }
                setGraphic(root);
                setStyle("-fx-background-color:transparent; -fx-padding:8 16;");
            }
        });
    }

    private void initInputShortcuts() {
        inputArea.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.ENTER) {
                onSend();
                event.consume();
            }
        });
    }

    private void loadConfigToUi() {
        AiConfig config = AiAssistantUtil.loadConfig();
        applyConfigToUi(config);
    }

    private void applyConfigToUi(AiConfig config) {
        endpointField.setText(config.endpoint());
        modelCombo.setValue(config.model());
        modelCombo.getEditor().setText(config.model());
        apiKeyField.setText(config.apiKey());
        systemPromptArea.setText(config.systemPrompt());
        temperatureSlider.setValue(config.temperature());
        temperatureLabel.setText(String.format("%.1f", config.temperature()));
        maxTokensSpinner.getValueFactory().setValue(config.maxTokens());
    }

    private void loadConversations() {
        conversations.setAll(AiAssistantUtil.loadConversations());
        if (conversations.isEmpty()) {
            conversations.add(new Conversation("新对话"));
        }
        conversationList.getSelectionModel().select(0);
    }

    private void ensureConversation() {
        if (currentConversation == null) {
            onNewConversation();
        }
    }

    private void refreshCurrentConversation() {
        if (currentConversation == null) {
            messageItems.clear();
            currentTitleLabel.setText("新对话");
            currentMetaLabel.setText("0 条消息");
            currentModelLabel.setText(AiAssistantUtil.displayNameForModel(getSelectedModel()) + "  ·  " + getSelectedModel());
            lastAssistantReply = "";
            return;
        }

        messageItems.setAll(currentConversation.messages);
        currentTitleLabel.setText(currentConversation.title == null ? "未命名对话" : currentConversation.title);
        currentMetaLabel.setText(currentConversation.messages.size() + " 条消息 · 更新于 " + formatTime(currentConversation.updatedAt));
        String modelId = currentConversation.modelId == null || currentConversation.modelId.isBlank()
                ? getSelectedModel()
                : currentConversation.modelId;
        currentModelLabel.setText(AiAssistantUtil.displayNameForModel(modelId) + "  ·  " + modelId);
        lastAssistantReply = currentConversation.messages.stream()
                .filter(message -> "assistant".equalsIgnoreCase(message.role()))
                .reduce((first, second) -> second)
                .map(ChatMessage::content)
                .orElse("");
        conversationList.refresh();
    }

    private AiConfig readConfigFromUi() {
        return new AiConfig(
                endpointField.getText(),
                getSelectedModel(),
                apiKeyField.getText(),
                systemPromptArea.getText(),
                temperatureSlider.getValue(),
                maxTokensSpinner.getValue()
        ).normalized();
    }

    private String getSelectedModel() {
        String editorText = modelCombo.getEditor() == null ? null : modelCombo.getEditor().getText();
        if (editorText != null && !editorText.isBlank()) {
            return editorText.trim();
        }
        String value = modelCombo.getValue();
        return value == null ? "" : value.trim();
    }

    private void resortConversations() {
        Conversation selected = currentConversation;
        conversations.sort(Comparator.comparingLong((Conversation c) -> c.updatedAt).reversed());
        if (selected != null) {
            conversationList.getSelectionModel().select(selected);
        }
        conversationList.refresh();
    }

    private void persistConversations() {
        try {
            AiAssistantUtil.saveConversations(conversations);
        } catch (Exception e) {
            setStatus("会话保存失败：" + e.getMessage());
        }
    }

    private void scrollToBottom() {
        if (!messageItems.isEmpty()) {
            messageList.scrollTo(messageItems.size() - 1);
        }
    }

    private String titleFor(Conversation conversation, String question) {
        if (conversation != null && conversation.messages.size() > 1
                && conversation.title != null && !"新对话".equals(conversation.title)) {
            return conversation.title;
        }
        String oneLine = question.replaceAll("\\s+", " ").trim();
        if (oneLine.length() > 22) {
            return oneLine.substring(0, 22) + "...";
        }
        return oneLine.isBlank() ? "新对话" : oneLine;
    }

    private String previewOf(Conversation conversation) {
        if (conversation == null || conversation.messages == null || conversation.messages.isEmpty()) {
            return "还没有消息";
        }
        ChatMessage last = conversation.messages.get(conversation.messages.size() - 1);
        String prefix = "user".equalsIgnoreCase(last.role()) ? "你：" :
                "assistant".equalsIgnoreCase(last.role()) ? "AI：" : "";
        String text = last.content() == null ? "" : last.content().replaceAll("\\s+", " ").trim();
        if (text.length() > 36) {
            text = text.substring(0, 36) + "...";
        }
        return prefix + text;
    }

    private String formatTime(long millis) {
        if (millis <= 0) {
            return "--";
        }
        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
        return TIME_FORMAT.format(time);
    }

    private long nowMillis() {
        return Instant.now().toEpochMilli();
    }

    private void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }
}
