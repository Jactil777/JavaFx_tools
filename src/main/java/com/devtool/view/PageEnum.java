package com.devtool.view;

import com.devtool.config.AppConfig;

/**
 * 页面枚举：统一管理所有功能页面
 * 新增业务页面：在此追加枚举即可，导航栏自动出现新菜单项
 */
public enum PageEnum {

    // 默认首页
    EMPTY("\uD83C\uDFE0", "首页", "#4a6fa5", AppConfig.FXML_EMPTY_PAGE),

    // ========== 业务页面 ==========
    JSON_TOOL      ("\uD83D\uDCCB", "JSON工具",   "#e5c07b", "/fxml/page_json.fxml"),
    PASSWORD_TOOL  ("\uD83D\uDD11", "随机密码",   "#98c379", "/fxml/page_password.fxml"),
    QRCODE_TOOL    ("\uD83D\uDCF7", "二维码",     "#61afef", "/fxml/page_qrcode.fxml"),
    CRON_TOOL      ("⏰", "Cron表达式", "#c678dd", "/fxml/page_cron.fxml"),
    ENCRYPT_TOOL   ("\uD83D\uDD10", "加密解密",   "#be5046", "/fxml/page_encrypt.fxml"),
    TIME_TOOL      ("⏱", "时间戳工具", "#56b6c2", "/fxml/page_time.fxml"),
    CALCULATOR_TOOL("\uD83E\uDDEE", "计算器",     "#d19a66", "/fxml/page_calculator.fxml"),
    AI_ASSISTANT   ("\uD83E\uDD16", "AI助手",     "#56b6c2", "/fxml/page_ai_assistant.fxml"),
    TRANSLATE_TOOL ("\uD83D\uDCDA", "翻译助手",   "#4a6fa5", "/fxml/page_translate.fxml"),
    SQL_TOOL       ("\uD83D\uDDC4", "SQL工具",    "#98c379", "/fxml/page_sql.fxml"),
    TEXT_TOOL      ("\uD83D\uDCDD", "文本处理",   "#e06c75", "/fxml/page_text.fxml"),
    GIT_REPORT     ("\uD83D\uDCCA", "开发日报",   "#61afef", "/fxml/page_git_report.fxml"),
    FISH_TOOL      ("\uD83D\uDD27", "调试工具",   "#c678dd", "/fxml/page_fish.fxml");

    // ========== 后续在这里追加业务页面（取消注释即可） ==========

    private final String icon;
    private final String displayName;
    private final String iconColor;
    private final String fxmlPath;

    PageEnum(String icon, String displayName, String iconColor, String fxmlPath) {
        this.icon = icon;
        this.displayName = displayName;
        this.iconColor = iconColor;
        this.fxmlPath = fxmlPath;
    }

    public String getPageName() {
        return icon + " " + displayName;
    }

    public String getIcon() {
        return icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIconColor() {
        return iconColor;
    }

    public String getFxmlPath() {
        return fxmlPath;
    }
}

