package com.devtool.controller;

import com.devtool.config.AppConfig;
import com.devtool.view.PageEnum;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * 首页控制器
 */
public class EmptyPageController extends BaseController {

    @FXML private Label titleLabel;
    @FXML private Label versionLabel;
    @FXML private Label statsToolLabel;
    @FXML private Label copyrightLabel;

    @Override
    public void onPageInit() {
        if (versionLabel != null) {
            versionLabel.setText("v" + AppConfig.APP_VERSION);
        }
        if (statsToolLabel != null) {
            // 减去首页自身
            int toolCount = PageEnum.values().length - 1;
            statsToolLabel.setText(toolCount + " 个工具");
        }
    }
}
