package com.devtool.controller;

import com.devtool.util.PasswordUtil;
import com.devtool.util.SystemUtil;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.util.List;

public class PasswordPageController extends BaseController {

    @FXML private Slider    lengthSlider;
    @FXML private Label     lengthValueLabel;
    @FXML private TextField customLengthField;

    @FXML private CheckBox  chkUppercase;
    @FXML private CheckBox  chkLowercase;
    @FXML private CheckBox  chkDigits;
    @FXML private CheckBox  chkSymbols;
    @FXML private CheckBox  chkNoAmbiguous;
    @FXML private CheckBox  chkEachType;

    @FXML private TextField customSymbolsField;
    @FXML private Spinner<Integer> countSpinner;

    @FXML private ListView<String> resultList;
    @FXML private Label strengthLabel;
    @FXML private Label charSetLabel;
    @FXML private Label statusLabel;

    @FXML
    public void initialize() {
        // 滑块联动显示数值
        lengthSlider.valueProperty().addListener((obs, o, n) -> {
            int v = n.intValue();
            lengthValueLabel.setText(String.valueOf(v));
            customLengthField.setText(String.valueOf(v));
        });

        // 自定义长度输入框联动滑块
        customLengthField.textProperty().addListener((obs, o, n) -> {
            try {
                int v = Integer.parseInt(n.trim());
                if (v >= 4 && v <= 128) {
                    if (v <= 64) lengthSlider.setValue(v);
                    lengthValueLabel.setText(String.valueOf(v));
                }
            } catch (NumberFormatException ignored) {}
        });

        // 初始化 Spinner
        SpinnerValueFactory<Integer> factory =
            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 50, 1);
        countSpinner.setValueFactory(factory);
        countSpinner.setEditable(true);

        // 结果列表双击复制
        resultList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) onCopySelected();
        });

        // 结果列表单元格样式
        resultList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    // 根据强度着色
                    PasswordUtil.Strength s = PasswordUtil.evaluate(item);
                    String color = switch (s) {
                        case VERY_STRONG -> "-fx-text-fill:#4ec97e;";
                        case STRONG      -> "-fx-text-fill:#98c379;";
                        case MEDIUM      -> "-fx-text-fill:#e5c07b;";
                        default          -> "-fx-text-fill:#e06c75;";
                    };
                    setStyle(color + "-fx-font-family:'Consolas','Courier New',monospace; -fx-font-size:14px;");
                }
            }
        });

        // 初始触发一次更新字符集描述
        updateCharSetLabel();
        chkUppercase.selectedProperty().addListener((o, a, b) -> updateCharSetLabel());
        chkLowercase.selectedProperty().addListener((o, a, b) -> updateCharSetLabel());
        chkDigits   .selectedProperty().addListener((o, a, b) -> updateCharSetLabel());
        chkSymbols  .selectedProperty().addListener((o, a, b) -> updateCharSetLabel());
        chkNoAmbiguous.selectedProperty().addListener((o, a, b) -> updateCharSetLabel());
        customSymbolsField.textProperty().addListener((o, a, b) -> updateCharSetLabel());
    }

    @Override
    public void onPageInit() {
        setStatus("就绪");
    }

    // ─── 生成密码 ─────────────────────────────────────────────────────────

    @FXML
    public void onGenerate() {
        if (!chkUppercase.isSelected() && !chkLowercase.isSelected()
                && !chkDigits.isSelected() && !chkSymbols.isSelected()) {
            setStatus("请至少选择一种字符集！");
            strengthLabel.setText("—");
            strengthLabel.setStyle("-fx-text-fill:#e06c75; -fx-font-size:13px; -fx-font-weight:bold;");
            return;
        }

        try {
            PasswordUtil.Config cfg = buildConfig();
            int count = countSpinner.getValue();

            List<String> passwords = PasswordUtil.generate(cfg, count);
            resultList.getItems().setAll(passwords);

            // 显示第一个密码的强度
            if (!passwords.isEmpty()) {
                updateStrengthLabel(passwords.get(0));
            }

            setStatus("已生成 " + count + " 个密码，长度 " + cfg.length + " 位");
        } catch (Exception e) {
            setStatus("生成失败：" + e.getMessage());
        }
    }

    // ─── 复制操作 ─────────────────────────────────────────────────────────

    @FXML
    public void onCopySelected() {
        String selected = resultList.getSelectionModel().getSelectedItem();
        if (selected == null || selected.isBlank()) {
            if (!resultList.getItems().isEmpty()) {
                selected = resultList.getItems().get(0);
                resultList.getSelectionModel().select(0);
            } else {
                setStatus("没有可复制的密码");
                return;
            }
        }
        SystemUtil.copyToClipboardSilent(selected);
        setStatus("已复制：" + selected);
    }

    @FXML
    public void onCopyAll() {
        if (resultList.getItems().isEmpty()) { setStatus("没有可复制的密码"); return; }
        String all = String.join("\n", resultList.getItems());
        ClipboardContent cc = new ClipboardContent();
        cc.putString(all);
        Clipboard.getSystemClipboard().setContent(cc);
        setStatus("已复制全部 " + resultList.getItems().size() + " 个密码");
    }

    @FXML
    public void onClear() {
        resultList.getItems().clear();
        strengthLabel.setText("—");
        strengthLabel.setStyle("-fx-font-size:13px; -fx-font-weight:bold; -fx-text-fill:#888;");
        setStatus("已清空");
    }

    // ─── 工具方法 ─────────────────────────────────────────────────────────

    private PasswordUtil.Config buildConfig() {
        PasswordUtil.Config cfg = new PasswordUtil.Config();

        // 优先用文本框的值（支持超出滑块范围的长度）
        try {
            int len = Integer.parseInt(customLengthField.getText().trim());
            cfg.length = Math.max(1, Math.min(128, len));
        } catch (NumberFormatException e) {
            cfg.length = (int) lengthSlider.getValue();
        }

        cfg.useUppercase = chkUppercase.isSelected();
        cfg.useLowercase = chkLowercase.isSelected();
        cfg.useDigits    = chkDigits.isSelected();
        cfg.useSymbols   = chkSymbols.isSelected();
        cfg.noAmbiguous  = chkNoAmbiguous.isSelected();
        cfg.eachTypeMustExist = chkEachType.isSelected();
        cfg.customSymbols = customSymbolsField.getText().trim();
        return cfg;
    }

    private void updateStrengthLabel(String password) {
        PasswordUtil.Strength s = PasswordUtil.evaluate(password);
        String text  = switch (s) {
            case VERY_STRONG -> "非常强 💪";
            case STRONG      -> "强 ✅";
            case MEDIUM      -> "中等 ⚠️";
            default          -> "弱 ❌";
        };
        String color = switch (s) {
            case VERY_STRONG -> "-fx-text-fill:#4ec97e;";
            case STRONG      -> "-fx-text-fill:#98c379;";
            case MEDIUM      -> "-fx-text-fill:#e5c07b;";
            default          -> "-fx-text-fill:#e06c75;";
        };
        strengthLabel.setText(text);
        strengthLabel.setStyle(color + "-fx-font-size:13px; -fx-font-weight:bold;");
    }

    private void updateCharSetLabel() {
        try {
            PasswordUtil.Config cfg = buildConfig();
            charSetLabel.setText(PasswordUtil.describePool(cfg));
        } catch (Exception ignored) {}
    }

    private void setStatus(String msg) {
        if (statusLabel != null) statusLabel.setText(msg);
    }
}

