package com.devtool.controller;

import com.devtool.config.AppConfig;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * 首页（空白欢迎页）控制器
 */
public class EmptyPageController extends BaseController {

    @FXML
    private Label titleLabel;

    @FXML
    private Label descLabel;

    @FXML
    private Label versionLabel;

    @Override
    public void onPageInit() {
        if (versionLabel != null) {
            versionLabel.setText("版本：v" + AppConfig.APP_VERSION);
        }
    }
}

