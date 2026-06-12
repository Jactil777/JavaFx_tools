package com.devtool.controller;

import com.devtool.util.CalculatorUtil;
import com.devtool.util.SystemUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CalculatorPageController extends BaseController {

    @FXML private TextField expressionField;
    @FXML private Label resultLabel;
    @FXML private Label memoryLabel;
    @FXML private Label statusLabel;
    @FXML private ListView<HistoryItem> historyList;
    @FXML private CheckBox autoScrollCheck;
    @FXML private Button btnPercent;

    private final ObservableList<HistoryItem> historyItems = FXCollections.observableArrayList();

    private BigDecimal lastResult = BigDecimal.ZERO;
    private BigDecimal memoryValue = BigDecimal.ZERO;
    private boolean hasMemory = false;

    @FXML
    public void initialize() {
        historyList.setItems(historyItems);
        historyList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(HistoryItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.displayText());
                    setStyle("-fx-font-family:'Consolas','JetBrains Mono','Courier New',monospace; -fx-font-size:12px;");
                }
            }
        });

        ContextMenu cm = new ContextMenu();
        MenuItem copyLine = new MenuItem("复制整行");
        copyLine.setOnAction(e -> copySelectedLine());
        MenuItem copyExpr = new MenuItem("复制表达式");
        copyExpr.setOnAction(e -> copySelectedExpression());
        MenuItem copyRes = new MenuItem("复制结果");
        copyRes.setOnAction(e -> copySelectedResult());
        MenuItem useExpr = new MenuItem("使用该表达式");
        useExpr.setOnAction(e -> useSelectedExpression());
        cm.getItems().addAll(copyLine, copyExpr, copyRes, useExpr);
        historyList.setContextMenu(cm);

        historyList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                useSelectedExpression();
            }
        });

        expressionField.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        // % 号在 FXML 中会被当作资源查找前缀，需在代码中设置
        btnPercent.setText("%");
        btnPercent.setUserData("%");
        setResult("0");
        updateMemoryIndicator();
        setStatus("就绪");
    }

    @Override
    public void onPageInit() {
        if (expressionField != null) {
            expressionField.requestFocus();
        }
    }

    @FXML
    public void onKeypad(ActionEvent event) {
        Object src = event.getSource();
        if (!(src instanceof javafx.scene.control.Button btn)) {
            return;
        }
        Object ud = btn.getUserData();
        String token = ud == null ? btn.getText() : String.valueOf(ud);
        handleToken(token);
    }

    @FXML
    public void onCopyResult() {
        String text = resultLabel == null ? "" : resultLabel.getText();
        if (text == null || text.isBlank()) {
            setStatus("没有可复制的结果");
            return;
        }
        SystemUtil.copyToClipboardSilent(text);
        setStatus("已复制结果");
    }

    @FXML
    public void onCopyAllHistory() {
        if (historyItems.isEmpty()) {
            setStatus("没有历史可复制");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (HistoryItem item : historyItems) {
            if (sb.length() > 0) sb.append(SystemUtil.getLineSeparator());
            sb.append(item.displayText());
        }
        ClipboardContent cc = new ClipboardContent();
        cc.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(cc);
        setStatus("已复制全部历史（" + historyItems.size() + " 条）");
    }

    @FXML
    public void onClearHistory() {
        historyItems.clear();
        setStatus("已清空历史");
    }

    private void onKeyPressed(KeyEvent e) {
        if (e.isControlDown() && e.getCode() == KeyCode.C) {
            onCopyResult();
            e.consume();
            return;
        }
        if (e.isControlDown() && e.getCode() == KeyCode.L) {
            clearAll();
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.ENTER) {
            evaluate();
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.ESCAPE) {
            clearEntry();
            e.consume();
        }
    }

    private void handleToken(String token) {
        switch (token) {
            case "AC" -> clearAll();
            case "CE" -> clearEntry();
            case "BKSP" -> backspace();
            case "EVAL", "=" -> evaluate();
            case "COPY" -> onCopyResult();
            case "MC" -> memoryClear();
            case "MR" -> memoryRecall();
            case "M+" -> memoryAdd();
            case "M-" -> memorySubtract();
            case "+/-" -> toggleSign();
            default -> insertToken(token);
        }
    }

    private void clearAll() {
        expressionField.clear();
        setResult("0");
        lastResult = BigDecimal.ZERO;
        setStatus("已清空");
    }

    private void clearEntry() {
        expressionField.clear();
        setStatus("已清空输入");
    }

    private void backspace() {
        String text = expressionField.getText();
        if (text == null || text.isEmpty()) return;
        expressionField.setText(text.substring(0, text.length() - 1));
        expressionField.requestFocus();
        expressionField.positionCaret(expressionField.getText().length());
    }

    private void insertToken(String token) {
        if (token == null || token.isBlank()) return;

        String insert = switch (token) {
            case "×" -> "*";
            case "÷" -> "/";
            case "√" -> "sqrt(";
            case "x²" -> "^2";
            case "1/x" -> "^-1";
            default -> token;
        };

        String text = expressionField.getText();
        if (text == null) text = "";

        if (isOperator(insert) && text.isBlank()) {
            String lr = CalculatorUtil.format(lastResult);
            if (!lr.equals("0")) {
                text = lr;
            }
        }

        String newText = text + insert;
        expressionField.setText(newText);
        expressionField.requestFocus();
        expressionField.positionCaret(newText.length());
    }

    private boolean isOperator(String s) {
        return s.equals("+") || s.equals("-") || s.equals("*") || s.equals("/") || s.equals("^");
    }

    private void toggleSign() {
        String text = expressionField.getText();
        if (text == null) text = "";
        text = text.trim();
        if (text.isEmpty()) {
            String lr = CalculatorUtil.format(lastResult);
            expressionField.setText(lr.equals("0") ? "-" : "-" + lr);
            expressionField.positionCaret(expressionField.getText().length());
            return;
        }
        if (text.startsWith("-")) {
            expressionField.setText(text.substring(1));
        } else {
            expressionField.setText("-" + text);
        }
        expressionField.positionCaret(expressionField.getText().length());
    }

    private void evaluate() {
        String expr = expressionField.getText();
        if (expr == null || expr.isBlank()) {
            setStatus("请输入表达式");
            return;
        }
        try {
            BigDecimal result = CalculatorUtil.evaluate(expr);
            String formatted = CalculatorUtil.format(result);
            setResult(formatted);
            lastResult = result;

            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            HistoryItem item = new HistoryItem(now, expr.trim(), formatted);
            historyItems.add(item);
            if (autoScrollCheck == null || autoScrollCheck.isSelected()) {
                historyList.scrollTo(historyItems.size() - 1);
            }
            expressionField.setText(formatted);
            expressionField.positionCaret(formatted.length());
            setStatus("已计算");
        } catch (Exception ex) {
            setStatus("计算失败：" + ex.getMessage());
        }
    }

    private void memoryClear() {
        memoryValue = BigDecimal.ZERO;
        hasMemory = false;
        updateMemoryIndicator();
        setStatus("已清除记忆");
    }

    private void memoryRecall() {
        if (!hasMemory) {
            setStatus("记忆为空");
            return;
        }
        insertToken(CalculatorUtil.format(memoryValue));
        setStatus("已取回记忆");
    }

    private void memoryAdd() {
        BigDecimal v = currentValueForMemory();
        memoryValue = memoryValue.add(v);
        hasMemory = true;
        updateMemoryIndicator();
        setStatus("已累加到记忆");
    }

    private void memorySubtract() {
        BigDecimal v = currentValueForMemory();
        memoryValue = memoryValue.subtract(v);
        hasMemory = true;
        updateMemoryIndicator();
        setStatus("已从记忆减去");
    }

    private BigDecimal currentValueForMemory() {
        try {
            String expr = expressionField.getText();
            if (expr != null && !expr.isBlank()) {
                return CalculatorUtil.evaluate(expr);
            }
        } catch (Exception ignored) {}
        return lastResult;
    }

    private void updateMemoryIndicator() {
        if (memoryLabel != null) {
            memoryLabel.setText(hasMemory ? "M" : "");
        }
    }

    private void copySelectedLine() {
        HistoryItem item = historyList.getSelectionModel().getSelectedItem();
        if (item == null) {
            setStatus("未选择历史记录");
            return;
        }
        SystemUtil.copyToClipboardSilent(item.displayText());
        setStatus("已复制整行");
    }

    private void copySelectedExpression() {
        HistoryItem item = historyList.getSelectionModel().getSelectedItem();
        if (item == null) {
            setStatus("未选择历史记录");
            return;
        }
        SystemUtil.copyToClipboardSilent(item.expression);
        setStatus("已复制表达式");
    }

    private void copySelectedResult() {
        HistoryItem item = historyList.getSelectionModel().getSelectedItem();
        if (item == null) {
            setStatus("未选择历史记录");
            return;
        }
        SystemUtil.copyToClipboardSilent(item.result);
        setStatus("已复制结果");
    }

    private void useSelectedExpression() {
        HistoryItem item = historyList.getSelectionModel().getSelectedItem();
        if (item == null) {
            setStatus("未选择历史记录");
            return;
        }
        expressionField.setText(item.expression);
        expressionField.positionCaret(item.expression.length());
        setStatus("已加载表达式");
    }

    private void setResult(String text) {
        if (resultLabel != null) {
            resultLabel.setText(text == null ? "0" : text);
        }
    }

    private void setStatus(String msg) {
        if (statusLabel != null) {
            statusLabel.setText(msg);
        }
    }

    public record HistoryItem(String time, String expression, String result) {
        public String displayText() {
            return "[" + time + "] " + expression + " = " + result;
        }
    }
}
