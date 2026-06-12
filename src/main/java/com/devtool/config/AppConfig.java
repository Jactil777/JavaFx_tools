package com.devtool.config;

/**
 * 全局应用配置常量
 */
public class AppConfig {

    private AppConfig() {}

    // 应用基础信息
    public static final String APP_NAME    = "DevToolBox 开发者工具箱";
    public static final String APP_VERSION = "1.0.10";
    public static final String APP_AUTHOR  = "DevToolBox";

    // 主窗口尺寸
    public static final int WINDOW_WIDTH      = 1000;
    public static final int WINDOW_HEIGHT     = 700;
    public static final int WINDOW_MIN_WIDTH  = 700;
    public static final int WINDOW_MIN_HEIGHT = 500;

    // FXML 页面路径常量
    public static final String FXML_MAIN       = "/fxml/main.fxml";
    public static final String FXML_EMPTY_PAGE = "/fxml/page_empty.fxml";
}

