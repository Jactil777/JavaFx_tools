package com.devtool.controller;

import com.devtool.util.QrCodeUtil;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class QrCodePageController extends BaseController {

    @FXML private ToggleGroup  typeGroup;
    @FXML private RadioButton  rbText, rbUrl, rbEmail;
    @FXML private TextArea     inputArea;
    @FXML private Slider       sizeSlider;
    @FXML private Label        sizeLabel;
    @FXML private ImageView    qrImageView;
    @FXML private Label        statusLabel;
    @FXML private Button       btnSave;
    @FXML private Label        hintLabel;

    private Image currentImage;

    @FXML
    public void initialize() {
        sizeSlider.valueProperty().addListener((obs, o, n) ->
            sizeLabel.setText(n.intValue() + " px"));

        inputArea.textProperty().addListener((obs, o, n) -> onGenerate());
        typeGroup.selectedToggleProperty().addListener((obs, o, n) -> { updateHint(); onGenerate(); });

        updateHint();
    }

    @Override
    public void onPageInit() {
        setStatus("输入内容后自动生成二维码");
    }

    @FXML
    public void onGenerate() {
        String content = inputArea.getText();
        if (content == null || content.isBlank()) {
            qrImageView.setImage(null);
            currentImage = null;
            btnSave.setDisable(true);
            setStatus("请输入内容");
            return;
        }
        try {
            QrCodeUtil.ContentType type = getSelectedType();
            int size = (int) sizeSlider.getValue();
            currentImage = QrCodeUtil.generate(content, type, size);
            qrImageView.setImage(currentImage);
            qrImageView.setFitWidth(size);
            qrImageView.setFitHeight(size);
            btnSave.setDisable(false);
            setStatus("生成成功  " + size + "×" + size + " px  |  " + type.name());
        } catch (Exception e) {
            qrImageView.setImage(null);
            currentImage = null;
            btnSave.setDisable(true);
            setStatus("生成失败：" + e.getMessage());
        }
    }

    @FXML
    public void onSave() {
        if (currentImage == null) { setStatus("请先生成二维码"); return; }
        FileChooser fc = new FileChooser();
        fc.setTitle("保存二维码图片");
        fc.setInitialFileName("qrcode.png");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG 图片", "*.png"));
        File file = fc.showSaveDialog(qrImageView.getScene().getWindow());
        if (file == null) return;
        try {
            int w = (int) currentImage.getWidth();
            int h = (int) currentImage.getHeight();
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            PixelReader pr = currentImage.getPixelReader();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    Color c = pr.getColor(x, y);
                    int argb = (int)(c.getOpacity()*255)<<24
                             | (int)(c.getRed()*255)<<16
                             | (int)(c.getGreen()*255)<<8
                             | (int)(c.getBlue()*255);
                    bi.setRGB(x, y, argb);
                }
            }
            ImageIO.write(bi, "png", file);
            setStatus("已保存到：" + file.getAbsolutePath());
        } catch (Exception e) {
            setStatus("保存失败：" + e.getMessage());
        }
    }

    @FXML
    public void onClear() {
        inputArea.clear();
        qrImageView.setImage(null);
        currentImage = null;
        btnSave.setDisable(true);
        setStatus("已清空");
    }

    @FXML
    public void onCopyImage() {
        if (currentImage == null) { setStatus("请先生成二维码"); return; }
        javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
        cc.putImage(currentImage);
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
        setStatus("图片已复制到剪贴板");
    }

    @FXML
    public void onPaste() {
        javafx.scene.input.Clipboard cb = javafx.scene.input.Clipboard.getSystemClipboard();
        if (cb.hasString()) inputArea.setText(cb.getString());
        else setStatus("剪贴板无文本内容");
    }

    private QrCodeUtil.ContentType getSelectedType() {
        if (rbUrl.isSelected())   return QrCodeUtil.ContentType.URL;
        if (rbEmail.isSelected()) return QrCodeUtil.ContentType.EMAIL;
        return QrCodeUtil.ContentType.TEXT;
    }

    private void updateHint() {
        if (rbUrl.isSelected()) {
            inputArea.setPromptText("输入网址，例如：https://example.com");
            hintLabel.setText("提示：不含 http:// 前缀时自动补全 https://");
        } else if (rbEmail.isSelected()) {
            inputArea.setPromptText("输入邮箱地址，例如：user@example.com");
            hintLabel.setText("提示：将生成 mailto: 格式，扫码可直接发邮件");
        } else {
            inputArea.setPromptText("输入任意文本内容...");
            hintLabel.setText("提示：支持中文、英文、数字、特殊字符");
        }
    }

    private void setStatus(String msg) {
        if (statusLabel != null) statusLabel.setText(msg);
    }
}


