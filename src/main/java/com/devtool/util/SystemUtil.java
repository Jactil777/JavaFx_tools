package com.devtool.util;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;

/**
 * 系统工具类：剪贴板、系统信息、文件操作等通用方法
 */
public class SystemUtil {

    private static final Logger log = LoggerFactory.getLogger(SystemUtil.class);

    private SystemUtil() {}

    /**
     * 复制文本到系统剪贴板，并弹出提示
     */
    public static void copyToClipboard(String text) {
        copyToClipboardSilent(text);
        DialogUtil.info("提示", "已复制到剪贴板");
    }

    /**
     * 静默复制到剪贴板（不弹提示）
     */
    public static void copyToClipboardSilent(String text) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }

    /**
     * 获取系统换行符
     */
    public static String getLineSeparator() {
        return System.lineSeparator();
    }

    /**
     * 获取当前操作系统名称
     */
    public static String getOsName() {
        return System.getProperty("os.name");
    }

    /**
     * 是否为 Windows 系统
     */
    public static boolean isWindows() {
        return getOsName().toLowerCase().contains("windows");
    }

    /**
     * 用系统默认程序打开文件/文件夹
     */
    public static void openFile(File file) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file);
            }
        } catch (Exception e) {
            log.error("打开文件失败: {}", file.getAbsolutePath(), e);
            DialogUtil.error("错误", "无法打开文件：" + file.getAbsolutePath());
        }
    }

    /**
     * 用浏览器打开 URL
     */
    public static void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception e) {
            log.error("打开 URL 失败: {}", url, e);
        }
    }
}

