package com.devtool.controller;

import com.devtool.util.SystemUtil;
import com.devtool.util.TranslatorUtil;
import com.devtool.util.TranslatorUtil.WordDefinition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 翻译助手控制器
 * 支持多语言翻译、英文单词释义查询、单词本管理
 */
public class TranslatePageController extends BaseController {

    // ─── 语言代码映射 ─────────────────────────────────────────────────────
    private static final String[][] LANGS = {
        {"自动检测", "auto"},
        {"中文",     "zh"},
        {"英文",     "en"},
        {"日语",     "ja"},
        {"韩语",     "ko"},
        {"法语",     "fr"},
        {"西班牙语", "es"},
        {"俄语",     "ru"},
        {"德语",     "de"},
        {"阿拉伯语", "ar"},
        {"葡萄牙语", "pt"},
        {"意大利语", "it"},
    };

    // ─── FXML 节点 ────────────────────────────────────────────────────────
    @FXML private ComboBox<String> srcLangCombo;
    @FXML private ComboBox<String> tgtLangCombo;
    @FXML private TextArea         inputArea;
    @FXML private TextArea         resultArea;
    @FXML private Label            statusLabel;
    @FXML private Button           btnTranslate;

    // 单词卡片（英文单词翻译时显示）
    @FXML private javafx.scene.layout.VBox wordCardPane;
    @FXML private Label            cardWordLabel;
    @FXML private Label            cardPhoneticLabel;
    @FXML private javafx.scene.text.TextFlow cardMeaningFlow;
    @FXML private Label            cardFormsLabel;
    @FXML private Label            cardTranslationLabel;
    @FXML private Button           btnFavorite;

    // 单词本
    @FXML private ComboBox<String> bookCombo;
    @FXML private ListView<String> wordBookList;
    @FXML private javafx.scene.text.TextFlow wordBookDetailFlow;
    @FXML private Button           btnRemoveFromBook;

    // ─── 状态 ─────────────────────────────────────────────────────────────
    private String currentBookName = "我的生词本";
    private Map<String, String> currentBookWords = new LinkedHashMap<>();
    private String lastTranslatedWord = null;   // 最近一次翻译的单词（用于加入单词本）
    private String lastTranslationResult = "";   // 最近一次翻译结果

    // ─── 生命周期 ─────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // 初始化语言下拉框
        for (String[] lang : LANGS) {
            srcLangCombo.getItems().add(lang[0]);
            tgtLangCombo.getItems().add(lang[0]);
        }
        srcLangCombo.setValue("自动检测");
        tgtLangCombo.setValue("中文");

        // 输入区回车翻译
        inputArea.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.isControlDown() && e.getCode() == KeyCode.ENTER) {
                onTranslate();
                e.consume();
            }
        });

        // 单词本下拉
        refreshBookCombo();
        bookCombo.valueProperty().addListener((obs, o, n) -> {
            if (n != null) {
                currentBookName = n;
                loadCurrentBook();
            }
        });

        // 单词本列表点击显示详情
        wordBookList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) {
                String meaning = currentBookWords.get(n.toLowerCase());
                showWordBookDetail(n, meaning);
            } else {
                clearWordBookDetail();
            }
        });

        // 单词本列表双击加载到输入框
        wordBookList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String selected = wordBookList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    inputArea.setText(selected);
                    inputArea.requestFocus();
                }
            }
        });

        loadCurrentBook();
    }

    @Override
    public void onPageInit() {
        setStatus("就绪 · Ctrl+Enter 快速翻译");
        showResultView();
        updateFavoriteButton();
        inputArea.requestFocus();
    }

    // ── 翻译操作 ─────────────────────────────────────────────────────────

    @FXML
    private void onTranslate() {
        String text = inputArea.getText();
        if (text == null || text.isBlank()) {
            setStatus("请输入要翻译的内容");
            return;
        }

        btnTranslate.setDisable(true);
        // 隐藏单词卡片，显示翻译结果区
        showResultView();
        resultArea.setText("翻译中...");
        setStatus("翻译中...");

        String srcCode = getLangCode(srcLangCombo.getValue());
        String tgtCode = getLangCode(tgtLangCombo.getValue());

        // 异步翻译，避免阻塞 UI
        new Thread(() -> {
            try {
                String translated = TranslatorUtil.translate(text.trim(), srcCode, tgtCode);
                lastTranslationResult = translated;

                Platform.runLater(() -> {
                    // 如果是单个英文单词，尝试查词典并显示单词卡片
                    String trimmed = text.trim();
                    if (isSingleEnglishWord(trimmed) && ("en".equals(srcCode) || "auto".equals(srcCode))) {
                        lookupAndShowDefinition(trimmed, translated);
                    } else {
                        // 句子翻译：显示普通结果
                        showResultView();
                        resultArea.setText(translated);
                        lastTranslatedWord = null;
                        lastTranslationResult = translated;
                        updateFavoriteButton();
                    }

                    setStatus("翻译完成");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    resultArea.setText("翻译失败：" + e.getMessage());
                    setStatus("翻译失败");
                });
            } finally {
                Platform.runLater(() -> btnTranslate.setDisable(false));
            }
        }).start();
    }

    @FXML
    private void onSwapLang() {
        String src = srcLangCombo.getValue();
        String tgt = tgtLangCombo.getValue();
        if ("自动检测".equals(src)) {
            setStatus("源语言为自动检测，无法交换");
            return;
        }
        srcLangCombo.setValue(tgt);
        tgtLangCombo.setValue(src);

        // 交换输入和结果
        String inputText = inputArea.getText();
        String resultText = resultArea.getText();
        if (resultText != null && !resultText.startsWith("翻译") && !resultText.startsWith("翻译失败")) {
            inputArea.setText(resultText);
            resultArea.setText(inputText != null ? inputText : "");
        }
    }

    @FXML
    private void onClear() {
        inputArea.clear();
        resultArea.clear();
        lastTranslatedWord = null;
        lastTranslationResult = "";
        showResultView();
        updateFavoriteButton();
        setStatus("已清空 · 点击 + 添加单词 可手动收藏");
        inputArea.requestFocus();
    }

    @FXML
    private void onCopyResult() {
        String text = resultArea.getText();
        if (text == null || text.isBlank() || text.startsWith("翻译")) {
            setStatus("没有可复制的结果");
            return;
        }
        SystemUtil.copyToClipboardSilent(text);
        setStatus("已复制翻译结果");
    }

    // ─── 单词本操作 ───────────────────────────────────────────────────────

    @FXML
    private void onAddToBook() {
        String wordToAdd = lastTranslatedWord;

        // 如果没有最近翻译的单词（例如句子翻译），弹窗让用户手动输入
        if (wordToAdd == null) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("添加单词到生词本");
            dialog.setHeaderText("请输入要收藏的英文单词：");
            dialog.setContentText("单词：");
            var result = dialog.showAndWait();
            if (result.isEmpty() || result.get().isBlank()) {
                setStatus("已取消");
                return;
            }
            wordToAdd = result.get().trim();
            // 简单校验
            if (!isSingleEnglishWord(wordToAdd)) {
                setStatus("请输入有效的英文单词");
                return;
            }
            wordToAdd = wordToAdd.toLowerCase();
            // 手动输入的单词，主动查一次词典
            lastTranslatedWord = wordToAdd;
            WordDefinition def = TranslatorUtil.lookupWord(wordToAdd);
            if (def != null) {
                showWordCardView();
                cardWordLabel.setText(def.word);
                cardPhoneticLabel.setText(def.phonetic != null ? def.phonetic : "");
                buildFormattedMeanings(def);
                cardFormsLabel.setText(def.wordForms != null && !def.wordForms.isEmpty()
                        ? "变形：" + String.join("; ", def.wordForms) : "");
                // 手动添加时没有翻译结果，显示提示
                cardTranslationLabel.setText("（手动添加，无翻译结果）");
                lastTranslationResult = def.toSummary();
            } else {
                lastTranslationResult = wordToAdd; // 词典没有则存原词
                cardTranslationLabel.setText(wordToAdd);
            }
        }

        if (TranslatorUtil.isInWordBook(currentBookName, wordToAdd)) {
            setStatus("【" + wordToAdd + "】已在生词本中");
            updateFavoriteButton();
            return;
        }

        String meaning = buildWordMeaning();
        TranslatorUtil.addWord(currentBookName, wordToAdd, meaning);
        loadCurrentBook();
        wordBookList.getSelectionModel().select(wordToAdd);
        setStatus("⭐ 已收藏【" + wordToAdd + "】到【" + currentBookName + "】");
        updateFavoriteButton();
    }

    @FXML
    private void onRemoveFromBook() {
        String selected = wordBookList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatus("请先选择要删除的单词");
            return;
        }
        TranslatorUtil.removeWord(currentBookName, selected);
        loadCurrentBook();
        clearWordBookDetail();
        setStatus("已从单词本删除：" + selected);
    }

    @FXML
    private void onNewBook() {
        TextInputDialog dialog = new TextInputDialog("新单词本");
        dialog.setTitle("新建单词本");
        dialog.setHeaderText("请输入单词本名称：");
        dialog.showAndWait().ifPresent(name -> {
            if (!name.isBlank()) {
                currentBookName = name.trim();
                TranslatorUtil.saveWordBook(currentBookName, new LinkedHashMap<>());
                refreshBookCombo();
                bookCombo.setValue(currentBookName);
                setStatus("已创建单词本：【" + currentBookName + "】");
            }
        });
    }

    // ─── 内部方法 ─────────────────────────────────────────────────────────

    private void lookupAndShowDefinition(String word, String translation) {
        lastTranslatedWord = word.toLowerCase();
        // 保留中文翻译结果，不覆盖
        lastTranslationResult = translation;
        WordDefinition def = TranslatorUtil.lookupWord(word);

        if (def != null) {
            // 显示单词卡片
            showWordCardView();
            cardWordLabel.setText(def.word);
            cardPhoneticLabel.setText(def.phonetic != null ? def.phonetic : "");
            // 用 TextFlow 显示带格式的释义
            buildFormattedMeanings(def);
            cardFormsLabel.setText(def.wordForms != null && !def.wordForms.isEmpty()
                    ? "变形：" + String.join("; ", def.wordForms) : "");
            // 显示中文翻译结果
            cardTranslationLabel.setText(translation != null && !translation.isBlank() ? translation : "（无翻译结果）");
        } else {
            // 词典查询失败，显示普通翻译结果
            showResultView();
            resultArea.setText(translation);
        }
        updateFavoriteButton();
    }

    /** 构建带格式的释义 TextFlow（词性用颜色区分） */
    private void buildFormattedMeanings(WordDefinition def) {
        if (cardMeaningFlow == null) return;
        cardMeaningFlow.getChildren().clear();

        // 词性颜色映射
        Map<String, String> posColors = Map.of(
            "noun", "#e5c07b",    // 名词 - 金黄色
            "verb", "#98c379",     // 动词 - 绿色
            "adjective", "#61afef",// 形容词 - 蓝色
            "adverb", "#c678dd",   // 副词 - 紫色
            "pronoun", "#56b6c2",  // 代词 - 青色
            "preposition", "#d19a66", // 介词 - 橙色
            "conjunction", "#be5046", // 连词 - 红色
            "interjection", "#e06c75" // 感叹词 - 粉红
        );

        for (int i = 0; i < def.meanings.size(); i++) {
            var group = def.meanings.get(i);
            if (i > 0) {
                // 添加换行
                cardMeaningFlow.getChildren().add(new javafx.scene.text.Text("\n"));
            }

            // 词性标签（带背景色）
            String pos = group.partOfSpeech;
            String color = posColors.getOrDefault(pos.toLowerCase(), "#abb2bf");
            javafx.scene.text.Text posText = new javafx.scene.text.Text(pos + ".  ");
            posText.setFill(javafx.scene.paint.Color.web(color));
            posText.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
            cardMeaningFlow.getChildren().add(posText);

            // 释义列表
            for (int j = 0; j < group.definitions.size(); j++) {
                if (j > 0) {
                    cardMeaningFlow.getChildren().add(new javafx.scene.text.Text("\n    "));
                }
                javafx.scene.text.Text defText = new javafx.scene.text.Text(group.definitions.get(j));
                defText.setFill(javafx.scene.paint.Color.web("#cccccc"));
                defText.setStyle("-fx-font-size: 13px;");
                cardMeaningFlow.getChildren().add(defText);
            }
        }
    }

    /** 显示单词卡片视图，隐藏普通结果区 */
    private void showWordCardView() {
        if (wordCardPane != null) { wordCardPane.setVisible(true); wordCardPane.setManaged(true); }
        if (btnFavorite != null)  { btnFavorite.setVisible(true);  btnFavorite.setManaged(true); }
        if (resultArea != null)   { resultArea.setVisible(false);  resultArea.setManaged(false); }
    }

    /** 显示普通翻译结果区，隐藏单词卡片 */
    private void showResultView() {
        if (wordCardPane != null) { wordCardPane.setVisible(false); wordCardPane.setManaged(false); }
        if (btnFavorite != null)  { btnFavorite.setVisible(false);  btnFavorite.setManaged(false); }
        if (resultArea != null)   { resultArea.setVisible(true);    resultArea.setManaged(true); }
    }

    private String buildWordMeaning() {
        // 优先用词典释义，其次用翻译结果
        if (lastTranslatedWord != null) {
            WordDefinition def = TranslatorUtil.lookupWord(lastTranslatedWord);
            if (def != null) return def.toSummary();
        }
        return lastTranslationResult;
    }

    private void updateFavoriteButton() {
        if (btnFavorite == null) return;

        if (lastTranslatedWord != null) {
            // 刚翻译了某个单词 - 直接显示收藏按钮
            boolean alreadyIn = TranslatorUtil.isInWordBook(currentBookName, lastTranslatedWord);
            btnFavorite.setText(alreadyIn ? "⭐ 已收藏" : "⭐ 收藏单词");
            btnFavorite.setDisable(alreadyIn);
            btnFavorite.setStyle(alreadyIn
                    ? "-fx-background-color:#5a7f5a; -fx-text-fill:#cccccc; -fx-border-color:#6a8f6a; -fx-border-radius:12; -fx-background-radius:12; -fx-font-size:12px; -fx-font-weight:bold; -fx-padding:4 14; -fx-cursor:default;"
                    : "-fx-background-color:#4a6fa5; -fx-text-fill:#fff; -fx-border-color:#5a7fb5; -fx-border-radius:12; -fx-background-radius:12; -fx-font-size:12px; -fx-font-weight:bold; -fx-padding:4 14; -fx-cursor:hand;");
        } else {
            // 没有翻译单词 - 允许手动输入添加
            btnFavorite.setText("+ 添加单词");
            btnFavorite.setDisable(false);
            btnFavorite.setStyle("-fx-background-color:#505355; -fx-text-fill:#ddd; -fx-border-color:#666; -fx-border-radius:12; -fx-background-radius:12; -fx-font-size:12px; -fx-padding:4 14; -fx-cursor:hand;");
        }
    }

    private void loadCurrentBook() {
        currentBookWords = TranslatorUtil.loadWordBook(currentBookName);
        ObservableList<String> items = FXCollections.observableArrayList(currentBookWords.keySet());
        wordBookList.setItems(items);

        // 更新收藏按钮状态
        if (lastTranslatedWord != null) {
            updateFavoriteButton();
        }
    }

    private void refreshBookCombo() {
        bookCombo.getItems().clear();
        bookCombo.getItems().addAll(TranslatorUtil.listWordBooks());
        if (!bookCombo.getItems().isEmpty()) {
            bookCombo.setValue(currentBookName);
        }
    }

    private boolean isSingleEnglishWord(String text) {
        return text != null && text.matches("^[a-zA-Z][a-zA-Z'-]{0,30}$");
    }

    private String getLangCode(String langName) {
        for (String[] lang : LANGS) {
            if (lang[0].equals(langName)) return lang[1];
        }
        return "auto";
    }

    private void setStatus(String msg) {
        if (statusLabel != null) statusLabel.setText(msg);
    }

    /** 显示单词本详情（带格式） */
    private void showWordBookDetail(String word, String meaning) {
        if (wordBookDetailFlow == null) return;
        wordBookDetailFlow.getChildren().clear();

        if (meaning == null || meaning.isBlank()) {
            javafx.scene.text.Text emptyText = new javafx.scene.text.Text("（无释义）");
            emptyText.setFill(javafx.scene.paint.Color.web("#666666"));
            emptyText.setStyle("-fx-font-size: 12px; -fx-font-style: italic;");
            wordBookDetailFlow.getChildren().add(emptyText);
            return;
        }

        // 解析释义文本，尝试格式化
        String[] parts = meaning.split(";");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) continue;

            // 尝试分离词性和释义
            int dotIndex = part.indexOf('.');
            if (dotIndex > 0 && dotIndex < 15) {
                String pos = part.substring(0, dotIndex).trim();
                String def = part.substring(dotIndex + 1).trim();

                // 词性（彩色）
                javafx.scene.text.Text posText = new javafx.scene.text.Text(pos + ". ");
                posText.setFill(javafx.scene.paint.Color.web("#4a6fa5"));
                posText.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
                wordBookDetailFlow.getChildren().add(posText);

                // 释义
                javafx.scene.text.Text defText = new javafx.scene.text.Text(def);
                defText.setFill(javafx.scene.paint.Color.web("#cccccc"));
                defText.setStyle("-fx-font-size: 12px;");
                wordBookDetailFlow.getChildren().add(defText);
            } else {
                // 无法解析，直接显示
                javafx.scene.text.Text text = new javafx.scene.text.Text(part);
                text.setFill(javafx.scene.paint.Color.web("#aaaaaa"));
                text.setStyle("-fx-font-size: 12px;");
                wordBookDetailFlow.getChildren().add(text);
            }

            // 添加分隔符
            if (i < parts.length - 1) {
                javafx.scene.text.Text sep = new javafx.scene.text.Text("; ");
                sep.setFill(javafx.scene.paint.Color.web("#666666"));
                wordBookDetailFlow.getChildren().add(sep);
            }
        }
    }

    /** 清空单词本详情 */
    private void clearWordBookDetail() {
        if (wordBookDetailFlow != null) {
            wordBookDetailFlow.getChildren().clear();
        }
    }
}
