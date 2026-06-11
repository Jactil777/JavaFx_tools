package com.devtool.controller;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 摸鱼神器控制器
 * 内嵌浏览器 + Boss键伪装 + 深色注入
 */
public class FishPageController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(FishPageController.class);

    // ─── FXML 节点 ────────────────────────────────────────────────────────
    @FXML private TextField     urlField;
    @FXML private Button        btnGo, btnBack, btnForward, btnRefresh, btnBoss;
    @FXML private Label         statusLabel, loadingLabel;
    @FXML private ProgressBar   loadProgress;
    @FXML private StackPane     browserContainer;   // WebView 父容器
    @FXML private VBox          bossPane;           // Boss键假界面
    @FXML private VBox          browserPane;        // 正常浏览器区域
    @FXML private ComboBox<String> quickLinkCombo;  // 快捷网址
    @FXML private CheckBox      chkDarkInject;      // 深色注入开关
    @FXML private Slider        zoomSlider;         // 缩放

    // ─── 状态 ─────────────────────────────────────────────────────────────
    private WebView  webView;
    private WebEngine engine;
    private boolean  bossMode = false;

    // 预置快捷网址（伪装成技术类）
    private static final List<String[]> QUICK_LINKS = List.of(
        new String[]{"Hacker News",     "https://news.ycombinator.com"},
        new String[]{"掘金",            "https://juejin.cn"},
        new String[]{"知乎",            "https://www.zhihu.com"},
        new String[]{"微博",            "https://weibo.com"},
        new String[]{"bilibili",        "https://www.bilibili.com"},
        new String[]{"GitHub",          "https://github.com"},
        new String[]{"Stack Overflow",  "https://stackoverflow.com"},
        new String[]{"廖雪峰教程",      "https://www.liaoxuefeng.com"},
        new String[]{"CSDN",            "https://www.csdn.net"},
        new String[]{"百度",            "https://www.baidu.com"}
    );

    // Boss键显示的"假代码"
    private static final String BOSS_CODE = """
// =========================================================
// UserServiceImpl.java  |  代码审查中...
// =========================================================

@Service
@Transactional(rollbackFor = Exception.class)
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_KEY = "user:detail:";

    @Override
    public UserDTO getUserById(Long userId) {
        // 先查缓存
        String cacheKey = CACHE_KEY + userId;
        UserDTO cached = (UserDTO) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("命中缓存 userId={}", userId);
            return cached;
        }
        // 查数据库
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        UserDTO dto = UserConverter.INSTANCE.toDTO(user);
        // 写入缓存，TTL 30分钟
        redisTemplate.opsForValue().set(cacheKey, dto, 30, TimeUnit.MINUTES);
        return dto;
    }

    @Override
    public PageResult<UserDTO> pageList(UserQueryParam param) {
        Page<User> page = new Page<>(param.getPage(), param.getSize());
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
            .like(StringUtils.hasText(param.getName()), User::getUsername, param.getName())
            .eq(param.getStatus() != null, User::getStatus, param.getStatus())
            .orderByDesc(User::getCreateTime);
        IPage<User> result = userMapper.selectPage(page, wrapper);
        return PageResult.of(result, UserConverter.INSTANCE::toDTO);
    }

    @Override
    @CacheEvict(value = "user", key = "#userId")
    public void updateUserStatus(Long userId, Integer status) {
        User user = new User();
        user.setId(userId);
        user.setStatus(status);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        // 清除 Redis 缓存
        redisTemplate.delete(CACHE_KEY + userId);
        log.info("用户状态更新 userId={} status={}", userId, status);
    }
}

// TODO: 补充单元测试，覆盖率需达到 80%
// FIXME: 并发场景下 Redis 缓存击穿问题待优化
""";

    // ─── 深色注入 CSS ─────────────────────────────────────────────────────
    // 注入到网页中，尽量让页面背景变深色
    private static final String DARK_CSS = """
        html, body {
            background-color: #2b2b2b !important;
            color: #cccccc !important;
        }
        body > * {
            background-color: #2b2b2b !important;
        }
        a { color: #6fa0d0 !important; }
        input, textarea, select {
            background-color: #3c3f41 !important;
            color: #cccccc !important;
            border-color: #555 !important;
        }
        """;

    // ─── 生命周期 ─────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // 初始化快捷网址
        quickLinkCombo.getItems().add("-- 快捷网址 --");
        QUICK_LINKS.forEach(link -> quickLinkCombo.getItems().add(link[0]));
        quickLinkCombo.setValue("-- 快捷网址 --");
        quickLinkCombo.valueProperty().addListener((obs, o, n) -> {
            if (n != null && !n.startsWith("--")) {
                QUICK_LINKS.stream()
                    .filter(l -> l[0].equals(n))
                    .findFirst()
                    .ifPresent(l -> navigate(l[1]));
                quickLinkCombo.setValue("-- 快捷网址 --");
            }
        });

        // 延迟初始化 WebView（避免影响启动速度）
        Platform.runLater(this::initWebView);

        // Boss键初始状态隐藏
        bossPane.setVisible(false); bossPane.setManaged(false);

        // 缩放滑块默认1.0
        zoomSlider.setValue(1.0);
    }

    private void initWebView() {
        webView = new WebView();
        engine = webView.getEngine();

        // 允许 JavaScript
        engine.setJavaScriptEnabled(true);

        // 页面加载进度
        engine.getLoadWorker().progressProperty().addListener((obs, o, n) -> {
            loadProgress.setProgress(n.doubleValue());
            if (n.doubleValue() >= 1.0) {
                loadProgress.setVisible(false);
                loadingLabel.setVisible(false);
                // 注入深色 CSS
                if (chkDarkInject.isSelected()) injectDarkCss();
            }
        });

        // 页面状态
        engine.getLoadWorker().stateProperty().addListener((obs, o, n) -> {
            switch (n) {
                case RUNNING -> {
                    loadProgress.setVisible(true);
                    loadingLabel.setVisible(true);
                    loadingLabel.setText("加载中...");
                    setStatus("加载中：" + engine.getLocation());
                }
                case SUCCEEDED -> {
                    String title = engine.getTitle();
                    setStatus("✓ " + (title != null ? title : engine.getLocation()));
                    updateNavButtons();
                }
                case FAILED -> {
                    loadingLabel.setVisible(false);
                    loadProgress.setVisible(false);
                    setStatus("加载失败");
                }
            }
        });

        // URL 地址栏同步
        engine.locationProperty().addListener((obs, o, n) -> {
            if (n != null && !n.isEmpty()) urlField.setText(n);
        });

        // 缩放绑定
        webView.zoomProperty().bind(zoomSlider.valueProperty());

        // 设置 WebView 背景（尽量深色）
        webView.setStyle("-fx-background-color:#2b2b2b;");
        webView.setPrefHeight(Double.MAX_VALUE);

        // 注入到容器
        browserPane.getChildren().add(0, webView);

        // 默认加载页面
        navigate("https://juejin.cn");

        // 绑定 Boss 快捷键（Ctrl+W 或 Esc）
        webView.setOnKeyPressed(e -> {
            if (new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN).match(e)
                || e.getCode() == KeyCode.ESCAPE) {
                toggleBossMode();
            }
        });
    }

    @Override
    public void onPageInit() {
        setStatus("就绪 · 按 Ctrl+W 切换掩护模式");
    }

    // ─── 导航操作 ─────────────────────────────────────────────────────────

    @FXML private void onGo() {
        String url = urlField.getText().trim();
        if (url.isEmpty()) return;
        navigate(url);
    }

    @FXML private void onBack()    { if (engine != null) engine.getHistory().go(-1); }
    @FXML private void onForward() { if (engine != null) engine.getHistory().go(1); }
    @FXML private void onRefresh() { if (engine != null) engine.reload(); }

    @FXML private void onHome() {
        navigate("https://juejin.cn");
    }

    private void navigate(String url) {
        if (engine == null) return;
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            // 判断是搜索词还是网址
            if (url.contains(".") && !url.contains(" ")) {
                url = "https://" + url;
            } else {
                // 当搜索词，用百度搜索
                try {
                    url = "https://www.baidu.com/s?wd=" + java.net.URLEncoder.encode(url, "UTF-8");
                } catch (Exception ex) {
                    url = "https://www.baidu.com/s?wd=" + url;
                }
            }
        }
        urlField.setText(url);
        engine.load(url);
    }

    // ─── URL 回车 ─────────────────────────────────────────────────────────

    @FXML private void onUrlKeyPressed(javafx.scene.input.KeyEvent e) {
        if (e.getCode() == KeyCode.ENTER) onGo();
    }

    // ─── Boss键切换 ───────────────────────────────────────────────────────

    @FXML public void toggleBossMode() {
        bossMode = !bossMode;
        browserPane.setVisible(!bossMode); browserPane.setManaged(!bossMode);
        bossPane.setVisible(bossMode);    bossPane.setManaged(bossMode);
        btnBoss.setText(bossMode ? "🟢 恢复" : "🔴 Boss键");
        setStatus(bossMode ? "【掩护模式】代码审查中... 按 Ctrl+W 恢复" : "就绪 · 按 Ctrl+W 切换掩护模式");
    }

    // ─── 深色注入 ─────────────────────────────────────────────────────────

    private void injectDarkCss() {
        if (engine == null) return;
        try {
            String js = "var style = document.createElement('style');" +
                    "style.textContent = '" + DARK_CSS.replace("\n", " ").replace("'", "\\'") + "';" +
                    "document.head.appendChild(style);";
            engine.executeScript(js);
        } catch (Exception e) {
            // 部分页面限制脚本注入，忽略
        }
    }

    @FXML private void onDarkInjectChanged() {
        if (chkDarkInject.isSelected()) injectDarkCss();
    }

    // ─── 导航按钮状态 ─────────────────────────────────────────────────────

    private void updateNavButtons() {
        if (engine == null) return;
        var history = engine.getHistory();
        btnBack.setDisable(history.getCurrentIndex() <= 0);
        btnForward.setDisable(history.getCurrentIndex() >= history.getEntries().size() - 1);
    }

    // ─── 工具 ─────────────────────────────────────────────────────────────

    private void setStatus(String msg) {
        if (statusLabel != null) Platform.runLater(() -> statusLabel.setText(msg));
    }
}

