package com.devtool.controller;

import com.devtool.view.PageEnum;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 主框架控制器：左侧导航栏 + 右侧内容区页面切换
 */
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    @FXML
    private ListView<String> navListView;   // 左侧导航栏

    @FXML
    private BorderPane contentPane;         // 右侧内容容器

    @FXML
    private Label statusLabel;             // 底部状态栏

    /** 页面缓存，避免重复加载 FXML */
    private final Map<PageEnum, Pane> pageCache = new HashMap<>();

    /** 当前激活页面 */
    private BaseController currentController;

    @FXML
    public void initialize() {
        initNavList();
        initPageListener();
        // 默认加载首页
        switchPage(PageEnum.EMPTY);
    }

    /**
     * 初始化左侧导航菜单
     */
    private void initNavList() {
        navListView.getItems().clear();
        PageEnum[] pages = PageEnum.values();
        for (PageEnum page : pages) {
            navListView.getItems().add(page.getPageName());
        }
        navListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || getIndex() < 0 || getIndex() >= pages.length) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                PageEnum page = pages[getIndex()];
                Label iconLabel = new Label(page.getIcon());
                iconLabel.setMinWidth(20);
                iconLabel.setStyle("-fx-text-fill:" + page.getIconColor() + "; -fx-font-size:15px; -fx-font-weight:bold;");

                Label textLabel = new Label(page.getDisplayName());
                textLabel.textFillProperty().bind(Bindings.when(selectedProperty())
                        .then(Color.WHITE)
                        .otherwise(Color.web("#bbbbbb")));
                textLabel.setStyle("-fx-font-size:13px;");

                HBox row = new HBox(7, iconLabel, textLabel);
                row.setStyle("-fx-alignment:center-left;");

                setText(null);
                setGraphic(row);
            }
        });
        navListView.getSelectionModel().select(0);
    }

    /**
     * 导航点击监听，切换页面
     */
    private void initPageListener() {
        navListView.getSelectionModel().selectedIndexProperty().addListener((obs, oldVal, newVal) -> {
            int index = newVal.intValue();
            PageEnum[] pages = PageEnum.values();
            if (index >= 0 && index < pages.length) {
                switchPage(pages[index]);
            }
        });
    }

    /**
     * 切换页面核心方法（带缓存）
     */
    private void switchPage(PageEnum page) {
        try {
            // 通知当前页面即将失活
            if (currentController != null) {
                currentController.onPageDestroy();
            }

            // 从缓存取，没有则加载
            Pane pageRoot = pageCache.get(page);
            BaseController controller = null;

            if (pageRoot == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(page.getFxmlPath()));
                pageRoot = loader.load();
                pageCache.put(page, pageRoot);
                Object ctrl = loader.getController();
                if (ctrl instanceof BaseController baseCtrl) {
                    // 存控制器以便后续 onPageDestroy 调用
                    pageRoot.setUserData(baseCtrl);
                    controller = baseCtrl;
                }
                log.debug("页面 [{}] 首次加载完成", page.getPageName());
            } else {
                Object userData = pageRoot.getUserData();
                if (userData instanceof BaseController baseCtrl) {
                    controller = baseCtrl;
                }
            }

            contentPane.setCenter(pageRoot);
            currentController = controller;

            // 触发页面初始化
            if (controller != null) {
                controller.onPageInit();
            }

            updateStatus("当前工具：" + page.getPageName());
        } catch (IOException e) {
            log.error("页面切换失败: {}", page.getPageName(), e);
        }
    }

    private void updateStatus(String msg) {
        if (statusLabel != null) {
            statusLabel.setText(msg);
        }
    }
}

