package com.devtool.controller;

import com.devtool.util.CronUtil;
import com.devtool.util.SystemUtil;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.List;

public class CronPageController extends BaseController {

    // ── 可视化配置区 ──
    @FXML private TextField  secField, minField, hourField, dayField, monField, weekField;
    @FXML private Label      secDesc, minDesc, hourDesc, dayDesc, monDesc, weekDesc;

    // ── 表达式区 ──
    @FXML private TextField  exprField;
    @FXML private Label      validateLabel;

    // ── 下次触发时间 ──
    @FXML private ListView<String> triggerList;

    // ── 模板区 ──
    @FXML private ListView<CronUtil.Template> templateList;

    // ── 状态 ──
    @FXML private Label statusLabel;

    @FXML
    public void initialize() {
        // 字段变化 → 自动同步到表达式
        addSyncListener(secField,  "0-59",     "*");
        addSyncListener(minField,  "0-59",     "*");
        addSyncListener(hourField, "0-23",     "*");
        addSyncListener(dayField,  "1-31 或 ?","*");
        addSyncListener(monField,  "1-12",     "*");
        addSyncListener(weekField, "0-6(日-六) 或 ?", "*");

        // 表达式手动编辑 → 同步回各字段
        exprField.textProperty().addListener((obs, o, n) -> syncFieldsFromExpr(n));

        // 模板列表
        templateList.getItems().setAll(CronUtil.templates());
        templateList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(CronUtil.Template item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(String.format("%-12s  %s", item.name(), item.expr()));
                setStyle("-fx-font-family:'Consolas',monospace; -fx-font-size:12px;");
            }
        });
        templateList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                CronUtil.Template t = templateList.getSelectionModel().getSelectedItem();
                if (t != null) applyTemplate(t);
            }
        });

        // 初始化
        exprField.setText("0 * * * * *");
    }

    @Override
    public void onPageInit() {
        refreshTriggers();
    }

    // ── 字段 → 表达式 ─────────────────────────────────────────────────────

    private void addSyncListener(TextField field, String hint, String defaultVal) {
        field.setPromptText(hint);
        field.setText(defaultVal);
        field.textProperty().addListener((obs, o, n) -> rebuildExpr());
    }

    private void rebuildExpr() {
        String expr = String.join(" ",
            blank(secField,  "*"), blank(minField,  "*"),
            blank(hourField, "*"), blank(dayField,  "*"),
            blank(monField,  "*"), blank(weekField, "*"));
        // 暂停监听避免循环
        exprField.getProperties().put("_syncing", true);
        exprField.setText(expr);
        exprField.getProperties().remove("_syncing");
        validateAndRefresh(expr);
    }

    private String blank(TextField f, String def) {
        String t = f.getText().trim();
        return t.isEmpty() ? def : t;
    }

    // ── 表达式 → 字段 ─────────────────────────────────────────────────────

    private void syncFieldsFromExpr(String expr) {
        if (exprField.getProperties().containsKey("_syncing")) return;
        if (expr == null || expr.isBlank()) return;
        String[] p = expr.trim().split("\\s+");
        if (p.length != 6) { validateAndRefresh(expr); return; }

        TextField[] fields = {secField, minField, hourField, dayField, monField, weekField};
        for (int i = 0; i < 6; i++) {
            String val = p[i];
            TextField f = fields[i];
            if (!f.getText().equals(val)) {
                f.getProperties().put("_syncing", true);
                f.setText(val);
                f.getProperties().remove("_syncing");
            }
        }
        validateAndRefresh(expr);
    }

    // ── 校验 + 预览 ───────────────────────────────────────────────────────

    private void validateAndRefresh(String expr) {
        String err = CronUtil.validate(expr);
        if (err != null) {
            validateLabel.setText("❌ " + err);
            validateLabel.setStyle("-fx-text-fill:#e06c75; -fx-font-size:12px;");
            triggerList.getItems().clear();
            setStatus("表达式不合法");
        } else {
            validateLabel.setText("✅ 表达式合法");
            validateLabel.setStyle("-fx-text-fill:#98c379; -fx-font-size:12px;");
            refreshTriggers();
        }
    }

    private void refreshTriggers() {
        String expr = exprField.getText();
        if (CronUtil.validate(expr) != null) return;
        List<String> times = CronUtil.nextTriggers(expr, 10);
        triggerList.getItems().setAll(times);
        setStatus("已计算下次 " + times.size() + " 次触发时间");
    }

    // ── 按钮操作 ──────────────────────────────────────────────────────────

    @FXML public void onRefresh() { refreshTriggers(); }

    @FXML public void onCopy() {
        String expr = exprField.getText();
        if (expr.isBlank()) { setStatus("表达式为空"); return; }
        SystemUtil.copyToClipboardSilent(expr);
        setStatus("已复制：" + expr);
    }

    @FXML public void onReset() {
        secField.setText("*");  minField.setText("*");
        hourField.setText("*"); dayField.setText("*");
        monField.setText("*");  weekField.setText("*");
        setStatus("已重置");
    }

    private void applyTemplate(CronUtil.Template t) {
        exprField.setText(t.expr());
        setStatus("已应用模板：" + t.name() + "  —  " + t.desc());
    }

    @FXML public void onApplyTemplate() {
        CronUtil.Template t = templateList.getSelectionModel().getSelectedItem();
        if (t == null) { setStatus("请先选择一个模板"); return; }
        applyTemplate(t);
    }

    private void setStatus(String msg) {
        if (statusLabel != null) statusLabel.setText(msg);
    }
}

