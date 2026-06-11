package com.devtool.controller;

import com.devtool.util.SqlUtil;
import com.devtool.util.SystemUtil;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SqlPageController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(SqlPageController.class);

    // ─── Tab 按钮 ─────────────────────────────────────────────────────────
    @FXML private Button tabFormat, tabCheck, tabKeyword, tabParams, tabInsert, tabExtract;

    // ─── 公共输入区（格式化/语法/关键字/提取） ────────────────────────────
    @FXML private VBox   mainPane;
    @FXML private TextArea sqlInputArea, sqlOutputArea;
    @FXML private Label  sqlStatusLabel;

    // ─── 关键字选项 ───────────────────────────────────────────────────────
    @FXML private HBox   keywordOptionBar;
    @FXML private Button btnKeyUpper, btnKeyLower;

    // ─── 语法检测结果 ─────────────────────────────────────────────────────
    @FXML private VBox   checkResultPane;
    @FXML private ListView<String> checkResultList;
    @FXML private Label  checkSummaryLabel;

    // ─── 参数填充面板 ─────────────────────────────────────────────────────
    @FXML private VBox   paramsPane;
    @FXML private TextArea paramsSqlInput;
    @FXML private TextArea paramsValuesInput;
    @FXML private TextArea paramsOutputArea;
    @FXML private Label  paramsStatusLabel;

    // ─── INSERT 模板生成面板 ──────────────────────────────────────────────
    @FXML private VBox   insertPane;
    @FXML private TextField insertTableField;
    @FXML private TextArea  insertColumnsArea;
    @FXML private TextArea  insertOutputArea;
    @FXML private Label  insertStatusLabel;

    // ─── 提取元信息面板 ───────────────────────────────────────────────────
    @FXML private VBox   extractPane;
    @FXML private TextArea extractInputArea;
    @FXML private Label  extractTablesLabel, extractFieldsLabel;
    @FXML private Label  extractStatusLabel;

    private enum Mode { FORMAT, CHECK, KEYWORD, PARAMS, INSERT, EXTRACT }
    private Mode currentMode = Mode.FORMAT;

    // ─── 样例 SQL ─────────────────────────────────────────────────────────
    private static final String SAMPLE_SQL = """
        SELECT
            u.id,
            u.username,
            u.email,
            o.order_id,
            o.total_amount,
            o.created_at
        FROM users u
        INNER JOIN orders o ON u.id = o.user_id
        WHERE u.active = 1
            AND o.total_amount > 100
            AND o.created_at >= '2026-01-01'
        ORDER BY o.created_at DESC
        LIMIT 20;
        """;

    // ─── 生命周期 ─────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // 初始显示格式化面板
        showPane(Mode.FORMAT);
    }

    @Override
    public void onPageInit() {
        setStatus("就绪");
    }

    // ─── Tab 切换 ─────────────────────────────────────────────────────────

    @FXML private void onTabFormat()  { switchTab(Mode.FORMAT,  tabFormat); }
    @FXML private void onTabCheck()   { switchTab(Mode.CHECK,   tabCheck); }
    @FXML private void onTabKeyword() { switchTab(Mode.KEYWORD, tabKeyword); }
    @FXML private void onTabParams()  { switchTab(Mode.PARAMS,  tabParams); }
    @FXML private void onTabInsert()  { switchTab(Mode.INSERT,  tabInsert); }
    @FXML private void onTabExtract() { switchTab(Mode.EXTRACT, tabExtract); }

    private void switchTab(Mode mode, Button active) {
        currentMode = mode;
        for (Button b : new Button[]{tabFormat, tabCheck, tabKeyword, tabParams, tabInsert, tabExtract})
            b.getStyleClass().removeAll("json-tab-active");
        active.getStyleClass().add("json-tab-active");
        showPane(mode);
    }

    private void showPane(Mode mode) {
        // 主面板（格式化/语法检测/关键字/提取共用）
        boolean showMain = mode == Mode.FORMAT || mode == Mode.CHECK
                        || mode == Mode.KEYWORD || mode == Mode.EXTRACT;
        mainPane.setVisible(showMain);   mainPane.setManaged(showMain);
        paramsPane.setVisible(mode == Mode.PARAMS);  paramsPane.setManaged(mode == Mode.PARAMS);
        insertPane.setVisible(mode == Mode.INSERT);  insertPane.setManaged(mode == Mode.INSERT);

        // 语法检测结果区
        checkResultPane.setVisible(mode == Mode.CHECK);  checkResultPane.setManaged(mode == Mode.CHECK);
        // 关键字选项
        keywordOptionBar.setVisible(mode == Mode.KEYWORD); keywordOptionBar.setManaged(mode == Mode.KEYWORD);
        // 提取元信息
        extractPane.setVisible(mode == Mode.EXTRACT); extractPane.setManaged(mode == Mode.EXTRACT);

        // 格式化/语法/关键字/提取模式：显示输出区
        boolean showOutput = mode == Mode.FORMAT || mode == Mode.KEYWORD;
        sqlOutputArea.setVisible(showOutput);  sqlOutputArea.setManaged(showOutput);
    }

    // ─── 格式化 ───────────────────────────────────────────────────────────

    @FXML private void onFormat() {
        String sql = sqlInputArea.getText();
        if (sql == null || sql.isBlank()) { setStatus("请输入 SQL"); return; }
        try {
            String result = SqlUtil.format(sql);
            sqlOutputArea.setText(result);
            setStatus("格式化完成");
        } catch (Exception e) {
            setStatus("格式化失败：" + e.getMessage());
            log.warn("SQL 格式化失败", e);
        }
    }

    @FXML private void onCompress() {
        String sql = sqlInputArea.getText();
        if (sql == null || sql.isBlank()) { setStatus("请输入 SQL"); return; }
        sqlOutputArea.setText(SqlUtil.compress(sql));
        setStatus("压缩完成");
    }

    @FXML private void onRemoveComments() {
        String sql = sqlInputArea.getText();
        if (sql == null || sql.isBlank()) { setStatus("请输入 SQL"); return; }
        sqlOutputArea.setText(SqlUtil.removeComments(sql));
        setStatus("注释已去除");
    }

    // ─── 语法检测 ─────────────────────────────────────────────────────────

    @FXML private void onCheckSyntax() {
        String sql = sqlInputArea.getText();
        if (sql == null || sql.isBlank()) { setStatus("请输入 SQL"); return; }
        List<SqlUtil.SyntaxError> errors = SqlUtil.checkSyntax(sql);
        checkResultList.getItems().clear();
        if (errors.isEmpty()) {
            checkResultList.getItems().add("✅  未发现语法问题");
            checkSummaryLabel.setText("✅  语法检测通过");
            checkSummaryLabel.setStyle("-fx-text-fill:#98c379; -fx-font-size:12px;");
            setStatus("语法检测通过");
        } else {
            for (SqlUtil.SyntaxError e : errors) {
                String icon = e.message().startsWith("⚠") ? "⚠ " : "❌ ";
                checkResultList.getItems().add(
                    icon + "[位置 " + e.position() + "] " + e.message()
                );
            }
            long errCount  = errors.stream().filter(e -> !e.message().startsWith("⚠")).count();
            long warnCount = errors.stream().filter(e -> e.message().startsWith("⚠")).count();
            checkSummaryLabel.setText("发现 " + errCount + " 个错误，" + warnCount + " 个警告");
            checkSummaryLabel.setStyle("-fx-text-fill:#e06c75; -fx-font-size:12px;");
            setStatus("发现 " + errors.size() + " 个问题");
        }
    }

    // ─── 关键字大小写 ─────────────────────────────────────────────────────

    @FXML private void onKeyUpper() {
        String sql = sqlInputArea.getText();
        if (sql == null || sql.isBlank()) { setStatus("请输入 SQL"); return; }
        sqlOutputArea.setText(SqlUtil.toUpperKeywords(sql));
        setStatus("关键字已转大写");
    }

    @FXML private void onKeyLower() {
        String sql = sqlInputArea.getText();
        if (sql == null || sql.isBlank()) { setStatus("请输入 SQL"); return; }
        sqlOutputArea.setText(SqlUtil.toLowerKeywords(sql));
        setStatus("关键字已转小写");
    }

    // ─── 通用操作按钮 ─────────────────────────────────────────────────────

    @FXML private void onSqlPaste() {
        try {
            javafx.scene.input.Clipboard cb = javafx.scene.input.Clipboard.getSystemClipboard();
            if (cb.hasString()) sqlInputArea.setText(cb.getString());
            setStatus("已粘贴");
        } catch (Exception e) { setStatus("读取剪贴板失败"); }
    }

    @FXML private void onSqlClear() {
        sqlInputArea.clear(); sqlOutputArea.clear();
        checkResultList.getItems().clear();
        checkSummaryLabel.setText("");
        extractTablesLabel.setText("—"); extractFieldsLabel.setText("—");
        setStatus("已清空");
    }

    @FXML private void onSqlSample() {
        sqlInputArea.setText(SAMPLE_SQL.trim());
        setStatus("已加载示例");
    }

    @FXML private void onSqlCopyOutput() {
        String text = sqlOutputArea.getText();
        if (text == null || text.isBlank()) { setStatus("输出为空"); return; }
        SystemUtil.copyToClipboardSilent(text);
        setStatus("已复制");
    }

    @FXML private void onSqlSwap() {
        String in = sqlInputArea.getText();
        String out = sqlOutputArea.getText();
        sqlInputArea.setText(out);
        sqlOutputArea.setText(in);
        setStatus("已互换输入/输出");
    }

    // ─── 参数填充 ─────────────────────────────────────────────────────────

    @FXML private void onFillParams() {
        String sql = paramsSqlInput.getText();
        String valuesText = paramsValuesInput.getText();
        if (sql == null || sql.isBlank()) { setParamsStatus("请输入 SQL"); return; }
        if (valuesText == null || valuesText.isBlank()) { setParamsStatus("请输入参数值（每行一个）"); return; }
        try {
            List<String> params = Arrays.stream(valuesText.split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            String result = SqlUtil.fillParams(sql, params);
            paramsOutputArea.setText(result);
            setParamsStatus("填充完成，共 " + params.size() + " 个参数");
        } catch (Exception e) {
            setParamsStatus("填充失败：" + e.getMessage());
        }
    }

    @FXML private void onCopyParamsResult() {
        String text = paramsOutputArea.getText();
        if (text == null || text.isBlank()) { setParamsStatus("输出为空"); return; }
        SystemUtil.copyToClipboardSilent(text);
        setParamsStatus("已复制");
    }

    @FXML private void onClearParams() {
        paramsSqlInput.clear(); paramsValuesInput.clear(); paramsOutputArea.clear();
        setParamsStatus("已清空");
    }

    private void setParamsStatus(String msg) {
        if (paramsStatusLabel != null) paramsStatusLabel.setText(msg);
    }

    // ─── INSERT 模板 ──────────────────────────────────────────────────────

    @FXML private void onGenInsert() {
        String table = insertTableField.getText();
        String colsText = insertColumnsArea.getText();
        if (table == null || table.isBlank()) { setInsertStatus("请输入表名"); return; }
        if (colsText == null || colsText.isBlank()) { setInsertStatus("请输入字段列表（每行一个或逗号分隔）"); return; }
        try {
            List<String> cols = Arrays.stream(colsText.split("[,\n]"))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            String result = SqlUtil.generateInsertTemplate(table, cols);
            insertOutputArea.setText(result);
            setInsertStatus("生成完成（" + cols.size() + " 个字段）");
        } catch (Exception e) {
            setInsertStatus("生成失败：" + e.getMessage());
        }
    }

    @FXML private void onCopyInsert() {
        String text = insertOutputArea.getText();
        if (text == null || text.isBlank()) { setInsertStatus("输出为空"); return; }
        SystemUtil.copyToClipboardSilent(text);
        setInsertStatus("已复制");
    }

    @FXML private void onClearInsert() {
        insertTableField.clear(); insertColumnsArea.clear(); insertOutputArea.clear();
        setInsertStatus("已清空");
    }

    private void setInsertStatus(String msg) {
        if (insertStatusLabel != null) insertStatusLabel.setText(msg);
    }

    // ─── 提取元信息 ───────────────────────────────────────────────────────

    @FXML private void onExtractMeta() {
        String sql = sqlInputArea.getText();
        if (sql == null || sql.isBlank()) { setStatus("请输入 SQL"); return; }
        SqlUtil.SqlMeta meta = SqlUtil.extractMeta(sql);
        extractTablesLabel.setText(meta.tables().isEmpty() ? "（未识别）" : String.join("  ,  ", meta.tables()));
        extractFieldsLabel.setText(meta.fields().isEmpty() ? "（未识别 / SELECT *）" : String.join("  ,  ", meta.fields()));
        setStatus("提取完成：" + meta.tables().size() + " 张表，" + meta.fields().size() + " 个字段");
    }

    // ─── 工具 ─────────────────────────────────────────────────────────────

    private void setStatus(String msg) {
        if (sqlStatusLabel != null) sqlStatusLabel.setText(msg);
    }
}

