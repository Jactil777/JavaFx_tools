package com.devtool;

import com.devtool.config.AppConfig;
import com.devtool.util.ExceptionHandler;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

public class Main extends Application {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    @Override
    public void start(Stage primaryStage) throws Exception {
        // 全局异常捕获
        Thread.currentThread().setUncaughtExceptionHandler(new ExceptionHandler());

        log.info("DevToolBox 启动中... version={}", AppConfig.APP_VERSION);

        // 加载主框架 FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        BorderPane root = loader.load();

        // 初始化窗口
        Scene scene = new Scene(root, AppConfig.WINDOW_WIDTH, AppConfig.WINDOW_HEIGHT);
        // 加载全局样式（防御性判空）
        var cssUrl = getClass().getResource("/css/global.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        primaryStage.setTitle(AppConfig.APP_NAME + "  v" + AppConfig.APP_VERSION);

        // 设置窗口图标（找不到图标时不崩溃）
        InputStream iconStream = getClass().getResourceAsStream("/images/app_icon.png");
        if (iconStream != null) {
            primaryStage.getIcons().add(new Image(iconStream));
        }

        primaryStage.setScene(scene);
        primaryStage.setResizable(true);
        primaryStage.setMinWidth(AppConfig.WINDOW_MIN_WIDTH);
        primaryStage.setMinHeight(AppConfig.WINDOW_MIN_HEIGHT);
        primaryStage.show();

        log.info("DevToolBox 启动完成");
    }

    public static void main(String[] args) {
        launch(args);
    }
}

