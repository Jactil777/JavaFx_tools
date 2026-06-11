package com.devtool.controller;

import com.devtool.util.EncryptUtil;
import com.devtool.util.SystemUtil;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EncryptPageController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(EncryptPageController.class);

    // ─── Tab 按钮 ─────────────────────────────────────────────────────────
    @FXML private Button tabHash, tabAes, tabBase64, tabUrl;

    // ─── Hash 面板 ────────────────────────────────────────────────────────
    @FXML private VBox   hashPane;
    @FXML private TextArea hashInputArea;
    @FXML private CheckBox chkUppercase, chkGrouped;
    @FXML private Label  resultMd5, resultSha1, resultSha256, resultSha512;
    @FXML private TextField hmacKeyField;
    @FXML private Label  resultHmacSha1, resultHmacSha256, resultHmacSha512;
    @FXML private Label  hashStatusLabel;

    // ─── AES 面板 ─────────────────────────────────────────────────────────
    @FXML private VBox   aesPane;
    @FXML private TextArea aesInputArea, aesOutputArea;
    @FXML private TextField aesKeyField, aesIvField;
    @FXML private ComboBox<String> aesModeCombo;
    @FXML private HBox   aesIvRow;
    @FXML private Label  aesStatusLabel;

    // ─── Base64 面板 ──────────────────────────────────────────────────────
    @FXML private VBox   base64Pane;
    @FXML private TextArea b64InputArea, b64OutputArea;
    @FXML private ComboBox<String> b64ModeCombo;
    @FXML private Label  b64StatusLabel;

    // ─── URL 面板 ─────────────────────────────────────────────────────────
    @FXML private VBox   urlPane;
    @FXML private TextArea urlInputArea, urlOutputArea;
    @FXML private Label  urlStatusLabel;

    private enum Mode { HASH, AES, BASE64, URL }
    private Mode currentMode = Mode.HASH;

    // ─── 生命周期 ─────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Hash 实时计算（输入即触发）
        hashInputArea.textProperty().addListener((obs, o, n) -> doHashRealtime(n));
        hmacKeyField.textProperty().addListener((obs, o, n) -> doHashRealtime(hashInputArea.getText()));
        chkUppercase.selectedProperty().addListener((obs, o, n) -> doHashRealtime(hashInputArea.getText()));
        chkGrouped.selectedProperty().addListener((obs, o, n) -> doHashRealtime(hashInputArea.getText()));

        // AES 模式切换控制 IV 行显示
        aesModeCombo.getItems().addAll("AES/CBC (推荐)", "AES/ECB");
        aesModeCombo.setValue("AES/CBC (推荐)");
        aesModeCombo.valueProperty().addListener((obs, o, n) -> {
            boolean isCbc = n.contains("CBC");
            aesIvRow.setVisible(isCbc);
            aesIvRow.setManaged(isCbc);
        });

        // Base64 模式
        b64ModeCombo.getItems().addAll("标准 Base64", "URL 安全 Base64 (无填充)");
        b64ModeCombo.setValue("标准 Base64");

        // 初始显示 Hash 面板
        showPane(Mode.HASH);
    }

    @Override
    public void onPageInit() {
        setStatus("就绪");
    }

    // ─── Tab 切换 ─────────────────────────────────────────────────────────

    @FXML private void onTabHash()   { switchTab(Mode.HASH,   tabHash); }
    @FXML private void onTabAes()    { switchTab(Mode.AES,    tabAes); }
    @FXML private void onTabBase64() { switchTab(Mode.BASE64, tabBase64); }
    @FXML private void onTabUrl()    { switchTab(Mode.URL,    tabUrl); }

    private void switchTab(Mode mode, Button active) {
        currentMode = mode;
        for (Button b : new Button[]{tabHash, tabAes, tabBase64, tabUrl})
            b.getStyleClass().removeAll("json-tab-active");
        active.getStyleClass().add("json-tab-active");
        showPane(mode);
    }

    private void showPane(Mode mode) {
        hashPane.setVisible(mode == Mode.HASH);   hashPane.setManaged(mode == Mode.HASH);
        aesPane.setVisible(mode == Mode.AES);     aesPane.setManaged(mode == Mode.AES);
        base64Pane.setVisible(mode == Mode.BASE64); base64Pane.setManaged(mode == Mode.BASE64);
        urlPane.setVisible(mode == Mode.URL);     urlPane.setManaged(mode == Mode.URL);
    }

    // ─── Hash 面板：实时计算 ──────────────────────────────────────────────

    private void doHashRealtime(String input) {
        if (input == null || input.isEmpty()) {
            clearHashResults();
            setHashStatus("输入内容后自动计算");
            return;
        }
        boolean upper   = chkUppercase.isSelected();
        boolean grouped = chkGrouped.isSelected();
        try {
            resultMd5.setText(fmt(EncryptUtil.md5(input), upper, grouped));
            resultSha1.setText(fmt(EncryptUtil.sha1(input), upper, grouped));
            resultSha256.setText(fmt(EncryptUtil.sha256(input), upper, grouped));
            resultSha512.setText(fmt(EncryptUtil.sha512(input), upper, grouped));

            String key = hmacKeyField.getText();
            if (key != null && !key.isEmpty()) {
                resultHmacSha1.setText(fmt(EncryptUtil.hmacSha1(input, key), upper, grouped));
                resultHmacSha256.setText(fmt(EncryptUtil.hmacSha256(input, key), upper, grouped));
                resultHmacSha512.setText(fmt(EncryptUtil.hmacSha512(input, key), upper, grouped));
            } else {
                resultHmacSha1.setText("— 请输入 HMAC 密钥 —");
                resultHmacSha256.setText("— 请输入 HMAC 密钥 —");
                resultHmacSha512.setText("— 请输入 HMAC 密钥 —");
            }
            setHashStatus("计算完成（" + input.length() + " 字符）");
        } catch (Exception e) {
            setHashStatus("计算失败：" + e.getMessage());
        }
    }

    private String fmt(String hex, boolean upper, boolean grouped) {
        return EncryptUtil.formatHash(hex, upper, grouped);
    }

    private void clearHashResults() {
        String placeholder = "—";
        resultMd5.setText(placeholder); resultSha1.setText(placeholder);
        resultSha256.setText(placeholder); resultSha512.setText(placeholder);
        resultHmacSha1.setText(placeholder); resultHmacSha256.setText(placeholder);
        resultHmacSha512.setText(placeholder);
    }

    @FXML private void onCopyMd5()       { copyLabel(resultMd5); }
    @FXML private void onCopySha1()      { copyLabel(resultSha1); }
    @FXML private void onCopySha256()    { copyLabel(resultSha256); }
    @FXML private void onCopySha512()    { copyLabel(resultSha512); }
    @FXML private void onCopyHmacSha1()  { copyLabel(resultHmacSha1); }
    @FXML private void onCopyHmacSha256(){ copyLabel(resultHmacSha256); }
    @FXML private void onCopyHmacSha512(){ copyLabel(resultHmacSha512); }

    @FXML private void onClearHash() {
        hashInputArea.clear();
        hmacKeyField.clear();
        clearHashResults();
        setHashStatus("已清空");
    }

    @FXML private void onPasteHash() {
        try {
            javafx.scene.input.Clipboard cb = javafx.scene.input.Clipboard.getSystemClipboard();
            if (cb.hasString()) { hashInputArea.setText(cb.getString()); setHashStatus("已粘贴"); }
        } catch (Exception e) { setHashStatus("读取剪贴板失败"); }
    }

    private void copyLabel(Label label) {
        String text = label.getText();
        if (text == null || text.startsWith("—")) { setHashStatus("没有可复制的内容"); return; }
        SystemUtil.copyToClipboardSilent(text);
        setHashStatus("已复制：" + text.substring(0, Math.min(text.length(), 20)) + "...");
    }

    private void setHashStatus(String msg) {
        if (hashStatusLabel != null) hashStatusLabel.setText(msg);
    }

    // ─── AES 面板 ─────────────────────────────────────────────────────────

    @FXML private void onAesEncrypt() {
        String input = aesInputArea.getText();
        if (input == null || input.isBlank()) { setAesStatus("请输入明文"); return; }
        try {
            EncryptUtil.AesMode mode = aesModeCombo.getValue().contains("CBC")
                    ? EncryptUtil.AesMode.CBC : EncryptUtil.AesMode.ECB;
            String result = EncryptUtil.aesEncrypt(input, aesKeyField.getText(),
                    aesIvField.getText(), mode);
            aesOutputArea.setText(result);
            setAesStatus("加密成功（" + result.length() + " 字符 Base64）");
        } catch (Exception e) {
            aesOutputArea.setText("加密失败：\n" + e.getMessage());
            setAesStatus("加密失败：" + e.getMessage());
        }
    }

    @FXML private void onAesDecrypt() {
        String input = aesInputArea.getText();
        if (input == null || input.isBlank()) { setAesStatus("请输入密文（Base64）"); return; }
        try {
            EncryptUtil.AesMode mode = aesModeCombo.getValue().contains("CBC")
                    ? EncryptUtil.AesMode.CBC : EncryptUtil.AesMode.ECB;
            String result = EncryptUtil.aesDecrypt(input, aesKeyField.getText(),
                    aesIvField.getText(), mode);
            aesOutputArea.setText(result);
            setAesStatus("解密成功（" + result.length() + " 字符）");
        } catch (Exception e) {
            aesOutputArea.setText("解密失败：\n" + e.getMessage());
            setAesStatus("解密失败：" + e.getMessage());
        }
    }

    @FXML private void onAesSwap() {
        String out = aesOutputArea.getText();
        String in  = aesInputArea.getText();
        aesInputArea.setText(out);
        aesOutputArea.setText(in);
        setAesStatus("已互换输入/输出");
    }

    @FXML private void onAesCopyOutput() {
        String text = aesOutputArea.getText();
        if (text == null || text.isBlank()) { setAesStatus("输出为空"); return; }
        SystemUtil.copyToClipboardSilent(text);
        setAesStatus("已复制");
    }

    @FXML private void onAesClear() {
        aesInputArea.clear(); aesOutputArea.clear();
        setAesStatus("已清空");
    }

    private void setAesStatus(String msg) {
        if (aesStatusLabel != null) aesStatusLabel.setText(msg);
    }

    // ─── Base64 面板 ──────────────────────────────────────────────────────

    @FXML private void onB64Encode() {
        String input = b64InputArea.getText();
        if (input == null || input.isBlank()) { setB64Status("请输入内容"); return; }
        try {
            boolean urlSafe = b64ModeCombo.getValue().contains("URL");
            String result = urlSafe
                    ? EncryptUtil.base64UrlEncode(input)
                    : EncryptUtil.base64Encode(input);
            b64OutputArea.setText(result);
            setB64Status("编码成功（" + result.length() + " 字符）");
        } catch (Exception e) {
            setB64Status("编码失败：" + e.getMessage());
        }
    }

    @FXML private void onB64Decode() {
        String input = b64InputArea.getText();
        if (input == null || input.isBlank()) { setB64Status("请输入 Base64 字符串"); return; }
        try {
            boolean urlSafe = b64ModeCombo.getValue().contains("URL");
            String result = urlSafe
                    ? EncryptUtil.base64UrlDecode(input)
                    : EncryptUtil.base64Decode(input);
            b64OutputArea.setText(result);
            setB64Status("解码成功（" + result.length() + " 字符）");
        } catch (Exception e) {
            b64OutputArea.setText("解码失败：\n" + e.getMessage());
            setB64Status("解码失败：" + e.getMessage());
        }
    }

    @FXML private void onB64Swap() {
        String out = b64OutputArea.getText();
        String in  = b64InputArea.getText();
        b64InputArea.setText(out); b64OutputArea.setText(in);
        setB64Status("已互换");
    }

    @FXML private void onB64CopyOutput() {
        String text = b64OutputArea.getText();
        if (text == null || text.isBlank()) { setB64Status("输出为空"); return; }
        SystemUtil.copyToClipboardSilent(text);
        setB64Status("已复制");
    }

    @FXML private void onB64Clear() {
        b64InputArea.clear(); b64OutputArea.clear(); setB64Status("已清空");
    }

    private void setB64Status(String msg) {
        if (b64StatusLabel != null) b64StatusLabel.setText(msg);
    }

    // ─── URL 面板 ─────────────────────────────────────────────────────────

    @FXML private void onUrlEncode() {
        String input = urlInputArea.getText();
        if (input == null || input.isBlank()) { setUrlStatus("请输入内容"); return; }
        try {
            String result = EncryptUtil.urlEncode(input);
            urlOutputArea.setText(result);
            setUrlStatus("编码成功");
        } catch (Exception e) {
            setUrlStatus("编码失败：" + e.getMessage());
        }
    }

    @FXML private void onUrlDecode() {
        String input = urlInputArea.getText();
        if (input == null || input.isBlank()) { setUrlStatus("请输入内容"); return; }
        try {
            String result = EncryptUtil.urlDecode(input);
            urlOutputArea.setText(result);
            setUrlStatus("解码成功");
        } catch (Exception e) {
            setUrlStatus("解码失败：" + e.getMessage());
        }
    }

    @FXML private void onUrlSwap() {
        String out = urlOutputArea.getText();
        String in  = urlInputArea.getText();
        urlInputArea.setText(out); urlOutputArea.setText(in);
        setUrlStatus("已互换");
    }

    @FXML private void onUrlCopyOutput() {
        String text = urlOutputArea.getText();
        if (text == null || text.isBlank()) { setUrlStatus("输出为空"); return; }
        SystemUtil.copyToClipboardSilent(text);
        setUrlStatus("已复制");
    }

    @FXML private void onUrlClear() {
        urlInputArea.clear(); urlOutputArea.clear(); setUrlStatus("已清空");
    }

    private void setUrlStatus(String msg) {
        if (urlStatusLabel != null) urlStatusLabel.setText(msg);
    }

    private void setStatus(@SuppressWarnings("unused") String msg) {
        // 各 tab 各自有 statusLabel，此方法预留
    }
}

