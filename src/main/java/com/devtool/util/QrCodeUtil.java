package com.devtool.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.util.HashMap;
import java.util.Map;

/**
 * 二维码生成工具类（基于 ZXing）
 */
public class QrCodeUtil {

    private QrCodeUtil() {}

    /** 内容类型 */
    public enum ContentType { TEXT, URL, EMAIL }

    /**
     * 生成二维码 JavaFX Image
     *
     * @param content 内容
     * @param type    内容类型
     * @param size    像素尺寸（正方形）
     * @param fgColor 前景色（ARGB int，默认黑色 0xFF000000）
     * @param bgColor 背景色（ARGB int，默认白色 0xFFFFFFFF）
     */
    public static Image generate(String content, ContentType type, int size,
                                  int fgColor, int bgColor) throws WriterException {
        if (content == null || content.isBlank())
            throw new IllegalArgumentException("内容不能为空");

        // 根据类型处理内容
        String encoded = switch (type) {
            case URL   -> content.startsWith("http") ? content : "https://" + content;
            case EMAIL -> content.startsWith("mailto:") ? content : "mailto:" + content;
            default    -> content;
        };

        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);

        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(encoded, BarcodeFormat.QR_CODE, size, size, hints);

        return toFxImage(matrix, fgColor, bgColor);
    }

    private static Image toFxImage(BitMatrix matrix, int fgColor, int bgColor) {
        int width  = matrix.getWidth();
        int height = matrix.getHeight();
        WritableImage image = new WritableImage(width, height);
        PixelWriter pw = image.getPixelWriter();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = matrix.get(x, y) ? fgColor : bgColor;
                pw.setArgb(x, y, argb);
            }
        }
        return image;
    }

    /** 默认黑白二维码 */
    public static Image generate(String content, ContentType type, int size) throws WriterException {
        return generate(content, type, size, 0xFF1e1e1e, 0xFFf5f5f5);
    }
}

