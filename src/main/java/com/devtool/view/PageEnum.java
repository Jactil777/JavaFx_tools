package com.devtool.view;

import com.devtool.config.AppConfig;

/**
 * 页面枚举：统一管理所有功能页面
 * 新增业务页面：在此追加枚举即可，导航栏自动出现新菜单项
 */
public enum PageEnum {

    // 默认首页
    EMPTY("🏠 首页", AppConfig.FXML_EMPTY_PAGE),

    // ========== 业务页面 ==========
    JSON_TOOL    ("📋 JSON工具",     "/fxml/page_json.fxml"),
    PASSWORD_TOOL("🔑 随机密码",     "/fxml/page_password.fxml"),
    QRCODE_TOOL  ("📷 二维码",       "/fxml/page_qrcode.fxml"),
    CRON_TOOL    ("⏰ Cron表达式",   "/fxml/page_cron.fxml"),
    ENCRYPT_TOOL ("🔐 加密解密",    "/fxml/page_encrypt.fxml"),
    TIME_TOOL    ("⏱ 时间戳工具",  "/fxml/page_time.fxml"),
    SQL_TOOL     ("🗄 SQL工具",     "/fxml/page_sql.fxml"),
    TEXT_TOOL    ("📝 文本处理",    "/fxml/page_text.fxml"),
    GIT_REPORT   ("📊 开发日报",    "/fxml/page_git_report.fxml");

    // ========== 后续在这里追加业务页面（取消注释即可） ==========

    private final String pageName;
    private final String fxmlPath;

    PageEnum(String pageName, String fxmlPath) {
        this.pageName = pageName;
        this.fxmlPath = fxmlPath;
    }

    public String getPageName() {
        return pageName;
    }

    public String getFxmlPath() {
        return fxmlPath;
    }
}

