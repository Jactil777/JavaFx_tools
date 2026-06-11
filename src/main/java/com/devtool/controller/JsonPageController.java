package com.devtool.controller;

import com.devtool.util.JsonUtil;
import com.devtool.util.SystemUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class JsonPageController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(JsonPageController.class);

    // ─── Tab 按钮 ────────────────────────────────────────────────────────
    @FXML private Button tabFormat, tabCompress, tabEscape, tabUnescape, tabEntity, tabDiff;

    // ─── 普通模式 ────────────────────────────────────────────────────────
    @FXML private VBox     normalPane;
    @FXML private TextArea inputArea;
    @FXML private TreeView<String> jsonTreeView;
    @FXML private TextArea outputArea;
    @FXML private Button   btnExpandAll, btnCollapseAll, btnCompressOutput, btnToXml;
    @FXML private HBox     entityOptionsBar;
    @FXML private ComboBox<String> languageCombo;
    @FXML private TextField classNameField;
    @FXML private CheckBox lombokCheck;

    // ─── 普通模式搜索 ─────────────────────────────────────────────────────
    @FXML private HBox      normalSearchBar;
    @FXML private TextField normalSearchField;
    @FXML private Label     normalSearchHint;

    // ─── 对比模式 ────────────────────────────────────────────────────────
    @FXML private VBox     diffPane;
    @FXML private TextArea diffInputA, diffInputB;
    @FXML private ListView<DiffLineView> diffHighlightA, diffHighlightB;
    @FXML private ListView<JsonUtil.DiffLine> diffResultList;
    @FXML private Label    diffSummaryLabel;

    // ─── 对比模式搜索 ─────────────────────────────────────────────────────
    @FXML private HBox      diffSearchBar;
    @FXML private TextField diffSearchField;
    @FXML private Label     diffSearchHint;

    // ─── 状态标签 ────────────────────────────────────────────────────────
    @FXML private Label validLabel, statusLabel;

    // ─── 状态 ────────────────────────────────────────────────────────────
    private Mode    currentMode       = Mode.FORMAT;
    private String  lastFormattedJson = null;
    private List<JsonUtil.DiffLine> lastDiffResult = null;

    // 搜索状态
    private List<Integer> normalSearchMatches = new ArrayList<>();
    private int           normalSearchIdx     = -1;
    private List<Integer> diffSearchMatches   = new ArrayList<>();
    private int           diffSearchIdx       = -1;

    private enum Mode { FORMAT, COMPRESS, ESCAPE, UNESCAPE, ENTITY, DIFF }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ─── 高亮行数据类 ─────────────────────────────────────────────────────
    /**
     * 对比视图中每一行的数据：原始文本 + 高亮类型
     * hlType: "ONLY_A" | "ONLY_B" | "MODIFIED" | "SAME" | "SEARCH"
     */
    public static class DiffLineView {
        public final int    lineNo;
        public final String text;
        public final String hlType;   // 高亮类型

        public DiffLineView(int lineNo, String text, String hlType) {
            this.lineNo = lineNo;
            this.text   = text;
            this.hlType = hlType;
        }
    }

    // ─── 示例 JSON ────────────────────────────────────────────────────────
    private static final String SAMPLE_JSON = """
            {
              "id": 1001,
              "username": "devtool_user",
              "email": "user@example.com",
              "active": true,
              "score": 98.5,
              "createdAt": "2026-06-11T10:00:00",
              "address": {
                "province": "广东",
                "city": "深圳",
                "zipCode": "518000"
              },
              "tags": ["java", "spring", "backend"],
              "loginCount": 42
            }
            """;

    private static final String SAMPLE_JSON_B = """
            {
              "id": 1001,
              "username": "devtool_user_v2",
              "email": "user@example.com",
              "active": false,
              "score": 99.0,
              "createdAt": "2026-06-11T10:00:00",
              "address": {
                "province": "广东",
                "city": "广州",
                "zipCode": "510000"
              },
              "tags": ["java", "spring", "cloud"],
              "role": "admin"
            }
            """;

    // ─── 生命周期 ─────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        inputArea.textProperty().addListener((obs, o, n) -> validateInput(n));

        // 初始化语言选择器
        languageCombo.getItems().addAll("Java", "Python", "Go");
        languageCombo.setValue("Java");
        // Lombok 选项只对 Java 有效
        languageCombo.valueProperty().addListener((obs, o, n) -> {
            boolean isJava = "Java".equals(n);
            lombokCheck.setDisable(!isJava);
            if (!isJava) lombokCheck.setSelected(false);
            
            // 更新类名提示
            String hint = switch(n) {
                case "Python" -> "ClassName";
                case "Go" -> "StructName";
                default -> "ClassName";
            };
            classNameField.setPromptText(hint);
        });

        // Ctrl+Enter 执行
        KeyCodeCombination ctrlEnter = new KeyCodeCombination(KeyCode.ENTER, KeyCombination.CONTROL_DOWN);
        KeyCodeCombination ctrlF     = new KeyCodeCombination(KeyCode.F,     KeyCombination.CONTROL_DOWN);

        inputArea.setOnKeyPressed(e -> {
            if (ctrlEnter.match(e)) onRun();
            else if (ctrlF.match(e)) showNormalSearch();
        });
        outputArea.setOnKeyPressed(e -> { if (ctrlF.match(e)) showNormalSearch(); });
        diffInputA.setOnKeyPressed(e -> {
            if (ctrlEnter.match(e)) onRun();
            else if (ctrlF.match(e)) showDiffSearch();
        });
        diffInputB.setOnKeyPressed(e -> {
            if (ctrlEnter.match(e)) onRun();
            else if (ctrlF.match(e)) showDiffSearch();
        });
        // 高亮 ListView 也响应 Ctrl+F
        diffHighlightA.setOnKeyPressed(e -> { if (ctrlF.match(e)) showDiffSearch(); });
        diffHighlightB.setOnKeyPressed(e -> { if (ctrlF.match(e)) showDiffSearch(); });

        // 搜索框回车 = 下一个
        normalSearchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) onNormalSearchNext();
            else if (e.getCode() == KeyCode.ESCAPE) onNormalSearchClose();
        });
        diffSearchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) onDiffSearchNext();
            else if (e.getCode() == KeyCode.ESCAPE) onDiffSearchClose();
        });
        // 实时更新搜索匹配
        normalSearchField.textProperty().addListener((obs, o, n) -> doNormalSearch(n));
        diffSearchField.textProperty().addListener((obs, o, n)   -> doDiffSearch(n));

        // 初始化格式化模式
        showTreeView(true);
        setTreeButtonsVisible(true);

        // TreeView CellFactory
        jsonTreeView.setCellFactory(tv -> new TreeCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                int sep = item.indexOf('|');
                if (sep < 0) { setText(item); setStyle("-fx-text-fill:#d4d4d4;"); return; }
                String prefix  = item.substring(0, sep);
                String display = item.substring(sep + 1);
                setText(display);
                switch (prefix) {
                    case "OBJ"  -> setStyle("-fx-text-fill:#61afef;-fx-font-weight:bold;");
                    case "ARR"  -> setStyle("-fx-text-fill:#c678dd;-fx-font-weight:bold;");
                    case "STR"  -> setStyle("-fx-text-fill:#98c379;");
                    case "NUM"  -> setStyle("-fx-text-fill:#e5c07b;");
                    case "BOOL" -> setStyle("-fx-text-fill:#e06c75;");
                    case "NULL" -> setStyle("-fx-text-fill:#636363;-fx-font-style:italic;");
                    default     -> setStyle("-fx-text-fill:#d4d4d4;");
                }
            }
        });

        // Diff 结果 CellFactory
        diffResultList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(JsonUtil.DiffLine item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item.toString());
                switch (item.type) {
                    case ONLY_IN_A -> setStyle("-fx-text-fill:#e06c75;-fx-background-color:#3a2020;-fx-font-family:'Consolas',monospace;-fx-font-size:12px;");
                    case ONLY_IN_B -> setStyle("-fx-text-fill:#98c379;-fx-background-color:#1e3020;-fx-font-family:'Consolas',monospace;-fx-font-size:12px;");
                    case MODIFIED  -> setStyle("-fx-text-fill:#e5c07b;-fx-background-color:#2f2a18;-fx-font-family:'Consolas',monospace;-fx-font-size:12px;");
                    default        -> setStyle("-fx-text-fill:#666666;-fx-font-family:'Consolas',monospace;-fx-font-size:12px;");
                }
            }
        });

        // A/B 高亮视图 CellFactory（共用逻辑，searchIdx 用于当前命中行）
        setupHighlightListCellFactory(diffHighlightA, true);
        setupHighlightListCellFactory(diffHighlightB, false);
    }

    /** 统一设置高亮行 ListView 的 CellFactory */
    private void setupHighlightListCellFactory(ListView<DiffLineView> lv, boolean isA) {
        lv.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(DiffLineView item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }

                // 判断是否为当前搜索命中行
                List<Integer> matches = diffSearchMatches;
                boolean isCurrentMatch = !matches.isEmpty()
                        && diffSearchIdx >= 0 && diffSearchIdx < matches.size()
                        && matches.get(diffSearchIdx) == getIndex();

                String font = "-fx-font-family:'JetBrains Mono','Consolas',monospace;-fx-font-size:12px;";
                String lineNoStr = String.format("%4d  ", Integer.valueOf(item.lineNo));
                setText(lineNoStr + item.text);

                if (isCurrentMatch) {
                    // 当前搜索命中：橙色高亮
                    setStyle(font + "-fx-text-fill:#ffffff;-fx-background-color:#cc7700;");
                } else {
                    switch (item.hlType) {
                        case "ONLY_A"   -> setStyle(font + "-fx-text-fill:#e06c75;-fx-background-color:#3a2020;");
                        case "ONLY_B"   -> setStyle(font + "-fx-text-fill:#98c379;-fx-background-color:#1e3020;");
                        case "MODIFIED" -> setStyle(font + "-fx-text-fill:#e5c07b;-fx-background-color:#2f2a18;");
                        case "SEARCH"   -> setStyle(font + "-fx-text-fill:#ffffff;-fx-background-color:#8b6914;");
                        default         -> setStyle(font + "-fx-text-fill:#d4d4d4;-fx-background-color:transparent;");
                    }
                }
            }
        });
    }

    @Override public void onPageInit() { setStatus("就绪"); }

    // ─── Tab 切换 ─────────────────────────────────────────────────────────

    @FXML private void onTabFormat()   { switchTab(Mode.FORMAT,   tabFormat);   entityOptionsBar.setVisible(false); entityOptionsBar.setManaged(false); }
    @FXML private void onTabCompress() { switchTab(Mode.COMPRESS, tabCompress); entityOptionsBar.setVisible(false); entityOptionsBar.setManaged(false); }
    @FXML private void onTabEscape()   { switchTab(Mode.ESCAPE,   tabEscape);   entityOptionsBar.setVisible(false); entityOptionsBar.setManaged(false); }
    @FXML private void onTabUnescape() {
        switchTab(Mode.UNESCAPE, tabUnescape);
        entityOptionsBar.setVisible(false); entityOptionsBar.setManaged(false);
        inputArea.setPromptText("在此粘贴已转义的 JSON 字符串...");
    }
    @FXML private void onTabEntity() {
        switchTab(Mode.ENTITY, tabEntity);
        entityOptionsBar.setVisible(true); entityOptionsBar.setManaged(true);
        inputArea.setPromptText("在此粘贴 JSON 对象 {} 或对象数组 [{}]...");
    }
    @FXML private void onTabDiff() {
        switchTab(Mode.DIFF, tabDiff);
        entityOptionsBar.setVisible(false); entityOptionsBar.setManaged(false);
    }

    private void switchTab(Mode mode, Button activeBtn) {
        currentMode = mode;
        for (Button b : new Button[]{tabFormat, tabCompress, tabEscape, tabUnescape, tabEntity, tabDiff})
            b.getStyleClass().removeAll("json-tab-active");
        activeBtn.getStyleClass().add("json-tab-active");

        boolean isDiff = (mode == Mode.DIFF);
        normalPane.setVisible(!isDiff); normalPane.setManaged(!isDiff);
        diffPane.setVisible(isDiff);    diffPane.setManaged(isDiff);

        if (!isDiff) {
            boolean isFormat = (mode == Mode.FORMAT);
            showTreeView(isFormat);
            setTreeButtonsVisible(isFormat);
            outputArea.clear();
            jsonTreeView.setRoot(null);
            lastFormattedJson = null;
            if (mode != Mode.UNESCAPE && mode != Mode.ENTITY) inputArea.setPromptText("在此粘贴 JSON...");
            // 关闭搜索栏
            hideNormalSearch();
        } else {
            diffResultList.getItems().clear();
            diffHighlightA.getItems().clear();
            diffHighlightB.getItems().clear();
            // 恢复输入框
            setDiffInputVisible(true);
            diffSummaryLabel.setText("");
            lastDiffResult = null;
            hideDiffSearch();
        }
        setStatus("已切换到：" + activeBtn.getText());
    }

    private void showTreeView(boolean tree) {
        jsonTreeView.setVisible(tree); jsonTreeView.setManaged(tree);
        outputArea.setVisible(!tree);  outputArea.setManaged(!tree);
    }
    private void setTreeButtonsVisible(boolean v) {
        btnExpandAll.setVisible(v);      btnExpandAll.setManaged(v);
        btnCollapseAll.setVisible(v);    btnCollapseAll.setManaged(v);
        btnCompressOutput.setVisible(v); btnCompressOutput.setManaged(v);
        btnToXml.setVisible(v);          btnToXml.setManaged(v);
    }
    /** 切换对比模式 A/B 区域：true=输入框, false=高亮ListView */
    private void setDiffInputVisible(boolean input) {
        diffInputA.setVisible(input);      diffInputA.setManaged(input);
        diffInputB.setVisible(input);      diffInputB.setManaged(input);
        diffHighlightA.setVisible(!input); diffHighlightA.setManaged(!input);
        diffHighlightB.setVisible(!input); diffHighlightB.setManaged(!input);
    }

    // ─── 核心操作 ─────────────────────────────────────────────────────────

    @FXML public void onRun() {
        if (currentMode == Mode.DIFF) { runDiff(); return; }
        String input = inputArea.getText();
        if (input == null || input.isBlank()) { setStatus("请先输入内容"); return; }
        try {
            if (currentMode == Mode.FORMAT) {
                JsonNode root = MAPPER.readTree(input);
                lastFormattedJson = input;
                TreeItem<String> rootItem = buildJsonTree(root, "");
                rootItem.setExpanded(true);
                jsonTreeView.setShowRoot(false);
                jsonTreeView.setRoot(rootItem);
                showTreeView(true);
                setStatus("格式化成功，共 " + (countNodes(rootItem) - 1) + " 个节点");
            } else {
                String result = switch (currentMode) {
                    case COMPRESS -> JsonUtil.compress(input);
                    case ESCAPE   -> JsonUtil.escape(input);
                    case UNESCAPE -> JsonUtil.unescape(input);
                    case ENTITY   -> {
                        String cn = classNameField.getText();
                        if (cn == null || cn.isBlank()) {
                            cn = switch(languageCombo.getValue()) {
                                case "Python" -> "DemoEntity";
                                case "Go" -> "DemoStruct";
                                default -> "DemoEntity";
                            };
                        }
                        JsonUtil.Language lang = switch(languageCombo.getValue()) {
                            case "Python" -> JsonUtil.Language.PYTHON;
                            case "Go" -> JsonUtil.Language.GO;
                            default -> JsonUtil.Language.JAVA;
                        };
                        yield JsonUtil.generateEntityClass(input, cn, lang, lombokCheck.isSelected());
                    }
                    default -> "";
                };
                outputArea.setText(result);
                setStatus("执行成功，输出 " + result.length() + " 字符");
            }
        } catch (Exception e) {
            if (currentMode == Mode.FORMAT) jsonTreeView.setRoot(null);
            else outputArea.setText("执行失败：\n" + e.getMessage());
            setStatus("执行失败：" + e.getMessage());
        }
    }

    // ─── Diff 执行 ────────────────────────────────────────────────────────

    private void runDiff() {
        String a = diffInputA.getText(), b = diffInputB.getText();
        if (a == null || a.isBlank() || b == null || b.isBlank()) {
            setStatus("请先在 A、B 两侧输入 JSON"); return;
        }
        String errA = JsonUtil.validate(a), errB = JsonUtil.validate(b);
        if (errA != null) { setStatus("JSON A 不合法：" + errA); return; }
        if (errB != null) { setStatus("JSON B 不合法：" + errB); return; }

        try {
            // 格式化 A/B
            String fmtA = JsonUtil.format(a);
            String fmtB = JsonUtil.format(b);

            // 对比差异（路径级别）
            List<JsonUtil.DiffLine> lines = JsonUtil.diff(a, b);
            lastDiffResult = lines;
            diffResultList.getItems().setAll(lines);

            // 构建 A/B 高亮行（格式化后的 JSON 文本，逐行着色）
            List<DiffLineView> hlA = buildHighlightLines(fmtA, lines, true);
            List<DiffLineView> hlB = buildHighlightLines(fmtB, lines, false);
            diffHighlightA.getItems().setAll(hlA);
            diffHighlightB.getItems().setAll(hlB);

            // 切换到高亮视图
            setDiffInputVisible(false);

            // 统计
            long onlyA = lines.stream().filter(l -> l.type == JsonUtil.DiffType.ONLY_IN_A).count();
            long onlyB = lines.stream().filter(l -> l.type == JsonUtil.DiffType.ONLY_IN_B).count();
            long mod   = lines.stream().filter(l -> l.type == JsonUtil.DiffType.MODIFIED).count();

            if (onlyA == 0 && onlyB == 0 && mod == 0) {
                diffSummaryLabel.setText("两个 JSON 完全相同");
                diffSummaryLabel.setStyle("-fx-text-fill:#98c379;");
                setStatus("对比完成：完全相同");
            } else {
                diffSummaryLabel.setText("A独有: " + onlyA + "  B独有: " + onlyB + "  值不同: " + mod);
                diffSummaryLabel.setStyle("-fx-text-fill:#e5c07b;");
                setStatus("对比完成：发现 " + (onlyA + onlyB + mod) + " 处差异");
            }
        } catch (Exception e) {
            setStatus("对比失败：" + e.getMessage());
            log.warn("JSON diff 失败", e);
        }
    }

    /**
     * 将格式化后的 JSON 文本按行构建高亮行列表。
     * 策略：对每一行文本，检查它包含的字段名是否在 diff 路径中有差异，若有则标记对应颜色。
     */
    private List<DiffLineView> buildHighlightLines(String formattedJson,
                                                    List<JsonUtil.DiffLine> diffLines,
                                                    boolean isA) {
        // 收集各类型差异的字段名关键字（取路径最后一段）
        Set<String> onlyAKeys    = new HashSet<>();
        Set<String> onlyBKeys    = new HashSet<>();
        Set<String> modifiedKeys = new HashSet<>();

        for (JsonUtil.DiffLine dl : diffLines) {
            // 提取路径末尾的 key（去掉数组索引）
            String key = extractLastKey(dl.path);
            if (key.isEmpty()) continue;
            switch (dl.type) {
                case ONLY_IN_A -> onlyAKeys.add(key);
                case ONLY_IN_B -> onlyBKeys.add(key);
                case MODIFIED  -> modifiedKeys.add(key);
            }
        }

        String[] rawLines = formattedJson.split("\n", -1);
        List<DiffLineView> result = new ArrayList<>(rawLines.length);
        for (int i = 0; i < rawLines.length; i++) {
            String line = rawLines[i];
            String hlType = classifyLine(line, onlyAKeys, onlyBKeys, modifiedKeys, isA);
            result.add(new DiffLineView(i + 1, line, hlType));
        }
        return result;
    }

    /** 提取 JSON 路径最后一段 key，去掉数组索引 */
    private String extractLastKey(String path) {
        if (path == null || path.isEmpty()) return "";
        // 去掉 [n] 后缀
        String p = path.replaceAll("\\[\\d+]$", "");
        int dot = p.lastIndexOf('.');
        return dot >= 0 ? p.substring(dot + 1) : p;
    }

    /**
     * 根据行内容和差异集合判断高亮类型：
     * - 包含 ONLY_A 的字段名且是 A 视图 → ONLY_A
     * - 包含 ONLY_B 的字段名且是 B 视图 → ONLY_B
     * - 包含 MODIFIED 字段名 → MODIFIED
     * - 否则 → SAME
     */
    private String classifyLine(String line, Set<String> onlyA, Set<String> onlyB,
                                 Set<String> modified, boolean isA) {
        // 简单策略：从行中提取 "key": 形式的 key
        String extracted = extractJsonKey(line);
        if (extracted.isEmpty()) return "SAME";

        if (modified.contains(extracted))       return "MODIFIED";
        if (isA && onlyA.contains(extracted))   return "ONLY_A";
        if (!isA && onlyB.contains(extracted))  return "ONLY_B";
        // A 视图中 B 独有的字段在 A 中不存在（行中不会出现），B 视图中 A 独有同理，无需特殊处理
        return "SAME";
    }

    /** 从 JSON 行中提取字段名，如 `  "username": "xxx"` → `username` */
    private String extractJsonKey(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("\"")) return "";
        int end = trimmed.indexOf("\"", 1);
        if (end < 0) return "";
        return trimmed.substring(1, end);
    }

    // ─── Diff 输入区按钮 ──────────────────────────────────────────────────

    @FXML private void onClearDiffA() {
        diffInputA.clear();
        diffHighlightA.getItems().clear();
        setDiffInputVisible(true);
        setStatus("已清空 JSON A");
    }
    @FXML private void onClearDiffB() {
        diffInputB.clear();
        diffHighlightB.getItems().clear();
        setDiffInputVisible(true);
        setStatus("已清空 JSON B");
    }
    @FXML private void onPasteDiffA() {
        try { Clipboard cb = Clipboard.getSystemClipboard();
            if (cb.hasString()) { diffInputA.setText(cb.getString()); setDiffInputVisible(true); setStatus("已粘贴到 JSON A"); }
        } catch (Exception e) { setStatus("读取剪贴板失败"); }
    }
    @FXML private void onPasteDiffB() {
        try { Clipboard cb = Clipboard.getSystemClipboard();
            if (cb.hasString()) { diffInputB.setText(cb.getString()); setDiffInputVisible(true); setStatus("已粘贴到 JSON B"); }
        } catch (Exception e) { setStatus("读取剪贴板失败"); }
    }
    @FXML private void onSampleDiffA() { diffInputA.setText(SAMPLE_JSON.trim()); setDiffInputVisible(true); setStatus("已加载示例 A"); }
    @FXML private void onSampleDiffB() { diffInputB.setText(SAMPLE_JSON_B.trim()); setDiffInputVisible(true); setStatus("已加载示例 B"); }

    @FXML private void onCopyDiffResult() {
        if (lastDiffResult == null || lastDiffResult.isEmpty()) { setStatus("差异结果为空"); return; }
        String text = lastDiffResult.stream().map(JsonUtil.DiffLine::toString).collect(Collectors.joining("\n"));
        ClipboardContent cc = new ClipboardContent(); cc.putString(text);
        Clipboard.getSystemClipboard().setContent(cc);
        setStatus("已复制差异摘要（" + lastDiffResult.size() + " 行）");
    }

    // ─── 普通模式搜索 ─────────────────────────────────────────────────────

    private void showNormalSearch() {
        normalSearchBar.setVisible(true);
        normalSearchBar.setManaged(true);
        normalSearchField.requestFocus();
        normalSearchField.selectAll();
    }
    private void hideNormalSearch() {
        normalSearchBar.setVisible(false);
        normalSearchBar.setManaged(false);
        normalSearchMatches.clear();
        normalSearchIdx = -1;
        normalSearchHint.setText("");
    }
    @FXML private void onNormalSearchClose() { hideNormalSearch(); }

    /** 在 inputArea / outputArea 中搜索，定位到匹配位置 */
    private void doNormalSearch(String kw) {
        normalSearchMatches.clear();
        normalSearchIdx = -1;
        if (kw == null || kw.isEmpty()) { normalSearchHint.setText(""); return; }

        // 选择当前活跃的文本区
        TextArea target = outputArea.isVisible() ? outputArea : inputArea;
        String content = target.getText();
        if (content == null || content.isEmpty()) { normalSearchHint.setText("无结果"); return; }

        String lower = content.toLowerCase();
        String kwLower = kw.toLowerCase();
        int idx = 0;
        while ((idx = lower.indexOf(kwLower, idx)) >= 0) {
            normalSearchMatches.add(Integer.valueOf(idx));
            idx += kw.length();
        }
        if (normalSearchMatches.isEmpty()) {
            normalSearchHint.setText("无结果");
            normalSearchHint.setStyle("-fx-text-fill:#e06c75;");
        } else {
            normalSearchIdx = 0;
            highlightNormalMatch(target, kw);
        }
    }

    @FXML private void onNormalSearchNext() {
        if (normalSearchMatches.isEmpty()) { doNormalSearch(normalSearchField.getText()); return; }
        normalSearchIdx = (normalSearchIdx + 1) % normalSearchMatches.size();
        highlightNormalMatch(outputArea.isVisible() ? outputArea : inputArea, normalSearchField.getText());
    }
    @FXML private void onNormalSearchPrev() {
        if (normalSearchMatches.isEmpty()) return;
        normalSearchIdx = (normalSearchIdx - 1 + normalSearchMatches.size()) % normalSearchMatches.size();
        highlightNormalMatch(outputArea.isVisible() ? outputArea : inputArea, normalSearchField.getText());
    }

    private void highlightNormalMatch(TextArea target, String kw) {
        if (normalSearchIdx < 0 || normalSearchIdx >= normalSearchMatches.size()) return;
        int pos = normalSearchMatches.get(normalSearchIdx);
        target.requestFocus();
        target.selectRange(pos, pos + kw.length());
        normalSearchHint.setText((normalSearchIdx + 1) + "/" + normalSearchMatches.size());
        normalSearchHint.setStyle("-fx-text-fill:#98c379;");
    }

    // ─── 对比模式搜索 ─────────────────────────────────────────────────────

    private void showDiffSearch() {
        diffSearchBar.setVisible(true);
        diffSearchBar.setManaged(true);
        diffSearchField.requestFocus();
        diffSearchField.selectAll();
    }
    private void hideDiffSearch() {
        diffSearchBar.setVisible(false);
        diffSearchBar.setManaged(false);
        diffSearchMatches.clear();
        diffSearchIdx = -1;
        diffSearchHint.setText("");
    }
    @FXML private void onDiffSearchClose() { hideDiffSearch(); refreshDiffHighlightCells(); }

    /** 在 A/B 高亮 ListView 中搜索，按行匹配 */
    private void doDiffSearch(String kw) {
        diffSearchMatches.clear();
        diffSearchIdx = -1;
        if (kw == null || kw.isEmpty()) {
            diffSearchHint.setText("");
            refreshDiffHighlightCells();
            return;
        }
        // 搜索 A 的行（同步 B）
        List<DiffLineView> items = diffHighlightA.getItems();
        if (items.isEmpty()) {
            // 如果还在输入阶段，在文本框里搜
            searchInTextArea(diffInputA, kw);
            return;
        }
        String kwLower = kw.toLowerCase();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).text.toLowerCase().contains(kwLower)) {
                diffSearchMatches.add(Integer.valueOf(i));
            }
        }
        if (diffSearchMatches.isEmpty()) {
            diffSearchHint.setText("无结果");
            diffSearchHint.setStyle("-fx-text-fill:#e06c75;");
        } else {
            diffSearchIdx = 0;
            scrollToMatch();
        }
        refreshDiffHighlightCells();
    }

    private void searchInTextArea(TextArea ta, String kw) {
        String content = ta.getText();
        if (content == null) return;
        String lower = content.toLowerCase(), kwLower = kw.toLowerCase();
        int idx = lower.indexOf(kwLower);
        if (idx >= 0) {
            ta.requestFocus();
            ta.selectRange(idx, idx + kw.length());
            diffSearchHint.setText("文本框中找到");
            diffSearchHint.setStyle("-fx-text-fill:#98c379;");
        } else {
            diffSearchHint.setText("无结果");
            diffSearchHint.setStyle("-fx-text-fill:#e06c75;");
        }
    }

    @FXML private void onDiffSearchNext() {
        if (diffSearchMatches.isEmpty()) { doDiffSearch(diffSearchField.getText()); return; }
        diffSearchIdx = (diffSearchIdx + 1) % diffSearchMatches.size();
        scrollToMatch();
        refreshDiffHighlightCells();
    }
    @FXML private void onDiffSearchPrev() {
        if (diffSearchMatches.isEmpty()) return;
        diffSearchIdx = (diffSearchIdx - 1 + diffSearchMatches.size()) % diffSearchMatches.size();
        scrollToMatch();
        refreshDiffHighlightCells();
    }

    private void scrollToMatch() {
        if (diffSearchIdx < 0 || diffSearchIdx >= diffSearchMatches.size()) return;
        int row = diffSearchMatches.get(diffSearchIdx);
        diffHighlightA.scrollTo(row);
        diffHighlightB.scrollTo(row);
        diffHighlightA.getSelectionModel().select(row);
        diffHighlightB.getSelectionModel().select(row);
        diffSearchHint.setText((diffSearchIdx + 1) + "/" + diffSearchMatches.size());
        diffSearchHint.setStyle("-fx-text-fill:#98c379;");
    }

    /** 强制刷新 ListView 单元格（触发 CellFactory 重绘以更新当前命中高亮） */
    private void refreshDiffHighlightCells() {
        // 通过设置一个临时 null 再还原来触发刷新
        diffHighlightA.refresh();
        diffHighlightB.refresh();
    }

    // ─── 树形构建 ─────────────────────────────────────────────────────────

    private TreeItem<String> buildJsonTree(JsonNode node, String key) {
        boolean hasKey = !key.isEmpty();
        if (node.isObject()) {
            String label = hasKey ? key + ":  { " + node.size() + " fields }" : "{ " + node.size() + " fields }";
            TreeItem<String> item = new TreeItem<>("OBJ|" + label);
            item.setExpanded(true);
            node.fields().forEachRemaining(e -> item.getChildren().add(buildJsonTree(e.getValue(), e.getKey())));
            return item;
        } else if (node.isArray()) {
            String label = hasKey ? key + ":  [ " + node.size() + " items ]" : "[ " + node.size() + " items ]";
            TreeItem<String> item = new TreeItem<>("ARR|" + label);
            item.setExpanded(true);
            for (int i = 0; i < node.size(); i++) item.getChildren().add(buildJsonTree(node.get(i), "[" + i + "]"));
            return item;
        } else {
            String prefix = getTypePrefix(node);
            String val    = node.isTextual() ? "\"" + node.asText() + "\"" : node.toString();
            return new TreeItem<>(prefix + "|" + (hasKey ? key + ":  " + val : val));
        }
    }
    private String getTypePrefix(JsonNode n) {
        if (n.isTextual()) return "STR"; if (n.isNumber()) return "NUM";
        if (n.isBoolean()) return "BOOL"; if (n.isNull()) return "NULL";
        return "VAL";
    }
    private int countNodes(TreeItem<String> item) {
        int c = 1;
        for (TreeItem<String> ch : item.getChildren()) c += countNodes(ch);
        return c;
    }

    // ─── 树形视图操作按钮 ─────────────────────────────────────────────────

    @FXML private void onExpandAll()   { if (jsonTreeView.getRoot()!=null){ setExpandedAll(jsonTreeView.getRoot(),true);  setStatus("已展开全部"); } }
    @FXML private void onCollapseAll() { if (jsonTreeView.getRoot()!=null){ setExpandedAll(jsonTreeView.getRoot(),false); jsonTreeView.getRoot().setExpanded(true); setStatus("已折叠"); } }
    private void setExpandedAll(TreeItem<String> item, boolean exp) {
        item.setExpanded(exp);
        for (TreeItem<String> c : item.getChildren()) setExpandedAll(c, exp);
    }
    @FXML private void onCompressOutput() {
        if (lastFormattedJson==null){setStatus("请先执行格式化");return;}
        try { String c=JsonUtil.compress(lastFormattedJson); showTreeView(false); outputArea.setText(c); setStatus("已压缩（"+c.length()+" 字符）"); }
        catch(Exception e){setStatus("压缩失败："+e.getMessage());}
    }
    @FXML private void onToXml() {
        if (lastFormattedJson==null){setStatus("请先执行格式化");return;}
        try { String x=JsonUtil.toXml(lastFormattedJson); showTreeView(false); outputArea.setText(x); setStatus("已转 XML（"+x.length()+" 字符）"); }
        catch(Exception e){setStatus("转 XML 失败："+e.getMessage());}
    }

    // ─── 通用按钮 ─────────────────────────────────────────────────────────

    @FXML private void onClearInput()  { inputArea.clear(); validLabel.setText(""); setStatus("已清空输入"); }
    @FXML private void onPasteInput()  {
        try { Clipboard cb=Clipboard.getSystemClipboard();
            if(cb.hasString()){inputArea.setText(cb.getString());setStatus("已粘贴，共 "+cb.getString().length()+" 字符");}
            else setStatus("剪贴板为空");
        }catch(Exception e){setStatus("读取剪贴板失败");}
    }
    @FXML private void onSampleInput() { inputArea.setText(SAMPLE_JSON.trim()); setStatus("已加载示例"); }
    @FXML private void onCopyOutput()  {
        String text;
        if (currentMode==Mode.FORMAT && jsonTreeView.isVisible() && lastFormattedJson!=null) {
            try{text=JsonUtil.format(lastFormattedJson);}catch(Exception e){text=lastFormattedJson;}
        } else { text=outputArea.getText(); }
        if(text==null||text.isBlank()){setStatus("输出区为空");return;}
        SystemUtil.copyToClipboardSilent(text);
        setStatus("已复制（"+text.length()+" 字符）");
    }
    @FXML private void onClearOutput() {
        outputArea.clear(); jsonTreeView.setRoot(null); lastFormattedJson=null;
        if(currentMode==Mode.FORMAT) showTreeView(true);
        setStatus("已清空输出");
    }

    // ─── 实时校验 ─────────────────────────────────────────────────────────

    private void validateInput(String text) {
        if (text==null||text.isBlank()){validLabel.setText("");return;}
        if (currentMode==Mode.UNESCAPE){validLabel.setText("");return;}
        String err=JsonUtil.validate(text);
        if(err==null){validLabel.setText("JSON 合法");validLabel.setStyle("-fx-text-fill:#4ec97e;-fx-font-size:12px;");}
        else{validLabel.setText("JSON 不合法");validLabel.setStyle("-fx-text-fill:#e06c6c;-fx-font-size:12px;");}
    }

    private void setStatus(String msg) { if(statusLabel!=null) statusLabel.setText(msg); }
}
