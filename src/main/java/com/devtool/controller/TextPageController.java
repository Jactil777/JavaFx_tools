package com.devtool.controller;

import com.devtool.util.SystemUtil;
import com.devtool.util.TextUtil;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class TextPageController extends BaseController {

    // ─── Tab 按钮 ─────────────────────────────────────────────────────────
    @FXML private Button tabReplace, tabLines, tabCase, tabSpaces, tabEncode, tabStats;

    // ─── 主输入/输出区（所有 Tab 共用，数据持久保留）─────────────────────
    @FXML private TextArea textInputArea;
    @FXML private TextArea textOutputArea;
    @FXML private Label    textStatusLabel;

    // ─── 批量替换选项 ─────────────────────────────────────────────────────
    @FXML private VBox     replaceOptionsPane;
    @FXML private TextArea replaceRulesArea;
    @FXML private CheckBox chkReplaceRegex, chkReplaceIgnoreCase;

    // ─── 行操作选项 ───────────────────────────────────────────────────────
    @FXML private VBox     linesOptionsPane;
    @FXML private TextField filterKeywordField;
    @FXML private CheckBox chkFilterKeep, chkFilterRegex, chkFilterIgnoreCase;
    @FXML private TextField lineNumSepField;
    @FXML private CheckBox chkSortAsc, chkSortIgnoreCase;
    @FXML private CheckBox chkTrimLines;

    // ─── 编码选项 ─────────────────────────────────────────────────────────
    @FXML private VBox     encodeOptionsPane;

    // ─── 空格缩进选项 ─────────────────────────────────────────────────────
    @FXML private VBox     spacesOptionsPane;
    @FXML private TextField tabSpaceCountField;

    // ─── 统计面板 ─────────────────────────────────────────────────────────
    @FXML private VBox  statsPane;
    @FXML private Label statChars, statCharsNoSpace, statWords, statLines, statChinese, statTopChars;

    private enum Mode { REPLACE, LINES, CASE, SPACES, ENCODE, STATS }
    private Mode currentMode = Mode.REPLACE;

    // ─── 生命周期 ─────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        showPane(Mode.REPLACE);
        // 统计面板实时更新
        textInputArea.textProperty().addListener((obs, o, n) -> {
            if (currentMode == Mode.STATS) refreshStats(n);
        });
    }

    /**
     * 关键设计：onPageInit 不清空任何输入内容
     * 页面由 MainController 缓存，DOM 不销毁，TextArea 内容天然保留
     */
    @Override
    public void onPageInit() {
        setStatus("就绪 · 内容已保留");
        if (currentMode == Mode.STATS) refreshStats(textInputArea.getText());
    }

    // ─── Tab 切换 ─────────────────────────────────────────────────────────

    @FXML private void onTabReplace() { switchTab(Mode.REPLACE, tabReplace); }
    @FXML private void onTabLines()   { switchTab(Mode.LINES,   tabLines); }
    @FXML private void onTabCase()    { switchTab(Mode.CASE,    tabCase); }
    @FXML private void onTabSpaces()  { switchTab(Mode.SPACES,  tabSpaces); }
    @FXML private void onTabEncode()  { switchTab(Mode.ENCODE,  tabEncode); }
    @FXML private void onTabStats()   {
        switchTab(Mode.STATS, tabStats);
        refreshStats(textInputArea.getText());
    }

    private void switchTab(Mode mode, Button active) {
        currentMode = mode;
        for (Button b : new Button[]{tabReplace, tabLines, tabCase, tabSpaces, tabEncode, tabStats})
            b.getStyleClass().removeAll("json-tab-active");
        active.getStyleClass().add("json-tab-active");
        showPane(mode);
    }

    private void showPane(Mode mode) {
        setVisible(replaceOptionsPane, mode == Mode.REPLACE);
        setVisible(linesOptionsPane,   mode == Mode.LINES);
        setVisible(encodeOptionsPane,  mode == Mode.ENCODE);
        setVisible(spacesOptionsPane,  mode == Mode.SPACES);
        setVisible(statsPane,          mode == Mode.STATS);
        // 输出区（统计模式不显示）
        textOutputArea.setVisible(mode != Mode.STATS);
        textOutputArea.setManaged(mode != Mode.STATS);
    }


    private void setVisible(VBox pane, boolean v) {
        pane.setVisible(v); pane.setManaged(v);
    }

    // ─── 批量替换 ─────────────────────────────────────────────────────────

    @FXML private void onReplace() {
        String input = textInputArea.getText();
        String rules = replaceRulesArea.getText();
        if (input == null || input.isBlank()) { setStatus("输入区无内容"); return; }
        if (rules == null || rules.isBlank()) { setStatus("请填写替换规则"); return; }
        try {
            TextUtil.ReplaceResult result = TextUtil.replace(
                    input, rules,
                    chkReplaceRegex.isSelected(),
                    chkReplaceIgnoreCase.isSelected());
            textOutputArea.setText(result.text());
            String log = result.log().isEmpty()
                    ? "未发生任何替换（未匹配到规则）"
                    : String.join("；", result.log()) + "  共 " + result.count() + " 处";
            setStatus(log);
        } catch (Exception e) {
            setStatus("替换失败：" + e.getMessage());
        }
    }

    // ─── 行操作 ───────────────────────────────────────────────────────────

    @FXML private void onRemoveEmpty()     { applyLine(() -> TextUtil.removeEmptyLines(textInputArea.getText()),     "已去空行"); }
    @FXML private void onRemoveDuplicate() { applyLine(() -> TextUtil.removeDuplicateLines(textInputArea.getText(), chkTrimLines.isSelected()), "已去重"); }
    @FXML private void onSortLines()       { applyLine(() -> TextUtil.sortLines(textInputArea.getText(), chkSortAsc.isSelected(), chkSortIgnoreCase.isSelected()), "已排序"); }
    @FXML private void onReverseLines()    { applyLine(() -> TextUtil.reverseLines(textInputArea.getText()),         "已逆序"); }
    @FXML private void onTrimLines()       { applyLine(() -> TextUtil.trimLines(textInputArea.getText()),            "已去行首尾空格"); }
    @FXML private void onAddLineNumbers() {
        String sep = lineNumSepField.getText();
        applyLine(() -> TextUtil.addLineNumbers(textInputArea.getText(), sep.isBlank() ? ". " : sep), "已添加行号");
    }
    @FXML private void onRemoveLineNumbers() { applyLine(() -> TextUtil.removeLineNumbers(textInputArea.getText()), "已去行号"); }

    @FXML private void onFilterLines() {
        String kw = filterKeywordField.getText();
        if (kw == null || kw.isBlank()) { setStatus("请输入过滤关键词"); return; }
        applyLine(() -> TextUtil.filterLines(
                textInputArea.getText(), kw,
                chkFilterKeep.isSelected(),
                chkFilterRegex.isSelected(),
                chkFilterIgnoreCase.isSelected()),
                (chkFilterKeep.isSelected() ? "保留" : "删除") + "含「" + kw + "」的行");
    }

    private void applyLine(java.util.function.Supplier<String> fn, String msg) {
        String input = textInputArea.getText();
        if (input == null || input.isBlank()) { setStatus("输入区无内容"); return; }
        try { textOutputArea.setText(fn.get()); setStatus(msg); }
        catch (Exception e) { setStatus("操作失败：" + e.getMessage()); }
    }

    // ─── 大小写 ───────────────────────────────────────────────────────────

    @FXML private void onToUpper()     { applyCase(TextUtil::toUpperCase,  "已转大写"); }
    @FXML private void onToLower()     { applyCase(TextUtil::toLowerCase,  "已转小写"); }
    @FXML private void onToTitle()     { applyCase(TextUtil::toTitleCase,  "已转首字母大写"); }
    @FXML private void onToSnake()     { applyCase(TextUtil::toSnakeCase,  "camelCase → snake_case 完成"); }
    @FXML private void onToCamel()     { applyCase(TextUtil::toCamelCase,  "snake_case → camelCase 完成"); }

    private void applyCase(java.util.function.UnaryOperator<String> fn, String msg) {
        String input = textInputArea.getText();
        if (input == null || input.isBlank()) { setStatus("输入区无内容"); return; }
        textOutputArea.setText(fn.apply(input));
        setStatus(msg);
    }

    // ─── 空格/缩进 ────────────────────────────────────────────────────────

    @FXML private void onRemoveAllSpaces() { applySpace(TextUtil::removeAllSpaces, "已删除所有空格"); }
    @FXML private void onMergeSpaces()     { applySpace(TextUtil::mergeSpaces,     "已合并连续空格"); }
    @FXML private void onTabToSpaces() {
        int n = parseSpaceCount();
        applySpace(t -> TextUtil.tabToSpaces(t, n), "Tab → " + n + "个空格 完成");
    }
    @FXML private void onSpacesToTab() {
        int n = parseSpaceCount();
        applySpace(t -> TextUtil.spacesToTab(t, n), n + "个空格 → Tab 完成");
    }

    private int parseSpaceCount() {
        try { return Integer.parseInt(tabSpaceCountField.getText().trim()); }
        catch (Exception e) { return 4; }
    }

    private void applySpace(java.util.function.UnaryOperator<String> fn, String msg) {
        String input = textInputArea.getText();
        if (input == null || input.isBlank()) { setStatus("输入区无内容"); return; }
        textOutputArea.setText(fn.apply(input));
        setStatus(msg);
    }

    // ─── 编码转换 ─────────────────────────────────────────────────────────

    @FXML private void onToUnicode()        { applyEncode(TextUtil::toUnicode,        "文本 → Unicode 转义完成"); }
    @FXML private void onFromUnicode()      { applyEncode(TextUtil::fromUnicode,      "Unicode 转义 → 文本完成"); }
    @FXML private void onToHtmlEntities()   { applyEncode(TextUtil::toHtmlEntities,   "文本 → HTML 实体完成"); }
    @FXML private void onFromHtmlEntities() { applyEncode(TextUtil::fromHtmlEntities, "HTML 实体 → 文本完成"); }

    private void applyEncode(java.util.function.UnaryOperator<String> fn, String msg) {
        String input = textInputArea.getText();
        if (input == null || input.isBlank()) { setStatus("输入区无内容"); return; }
        try { textOutputArea.setText(fn.apply(input)); setStatus(msg); }
        catch (Exception e) { setStatus("转换失败：" + e.getMessage()); }
    }

    // ─── 统计 ─────────────────────────────────────────────────────────────

    private void refreshStats(String text) {
        TextUtil.TextStats s = TextUtil.stats(text);
        if (statChars         != null) statChars.setText(String.valueOf(s.chars()));
        if (statCharsNoSpace  != null) statCharsNoSpace.setText(String.valueOf(s.charsNoSpace()));
        if (statWords         != null) statWords.setText(String.valueOf(s.words()));
        if (statLines         != null) statLines.setText(String.valueOf(s.lines()));
        if (statChinese       != null) statChinese.setText(String.valueOf(s.chinesChars()));
        if (statTopChars      != null) {
            String top = s.topChars().entrySet().stream()
                    .map(e -> "'" + e.getKey() + "'×" + e.getValue())
                    .collect(java.util.stream.Collectors.joining("  "));
            statTopChars.setText(top.isEmpty() ? "—" : top);
        }
    }

    // ─── 通用按钮 ─────────────────────────────────────────────────────────

    @FXML private void onPasteInput() {
        try {
            javafx.scene.input.Clipboard cb = javafx.scene.input.Clipboard.getSystemClipboard();
            if (cb.hasString()) { textInputArea.setText(cb.getString()); setStatus("已粘贴"); }
        } catch (Exception e) { setStatus("读取剪贴板失败"); }
    }

    /** 将输出区内容移回输入区（可继续链式操作） */
    @FXML private void onOutputToInput() {
        String out = textOutputArea.getText();
        if (out == null || out.isBlank()) { setStatus("输出区为空"); return; }
        textInputArea.setText(out);
        textOutputArea.clear();
        setStatus("输出 → 输入（可继续操作）");
    }

    @FXML private void onCopyOutput() {
        String text = textOutputArea.getText();
        if (text == null || text.isBlank()) { setStatus("输出区为空"); return; }
        SystemUtil.copyToClipboardSilent(text);
        setStatus("已复制（" + text.length() + " 字符）");
    }

    @FXML private void onClearInput()  { textInputArea.clear();  setStatus("已清空输入"); }
    @FXML private void onClearOutput() { textOutputArea.clear(); setStatus("已清空输出"); }
    @FXML private void onClearAll() {
        textInputArea.clear(); textOutputArea.clear();
        setStatus("已清空");
    }

    private void setStatus(String msg) {
        if (textStatusLabel != null) textStatusLabel.setText(msg);
    }
}

