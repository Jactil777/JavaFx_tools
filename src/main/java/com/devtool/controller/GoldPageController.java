package com.devtool.controller;

import com.devtool.util.GoldPriceUtil;
import com.devtool.util.GoldPriceUtil.GoldPrice;
import com.devtool.util.GoldPriceUtil.GoldSymbol;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * 金价看板控制器
 * 实时展示现货黄金、COMEX黄金、沪金的价格和走势
 */
public class GoldPageController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(GoldPageController.class);

    // ========== FXML 绑定 ==========
    @FXML private Button refreshBtn;
    @FXML private Button autoRefreshBtn;
    @FXML private WebView chartWebView;
    @FXML private Label chartLoadingLabel;
    @FXML private Label chartSymbolLabel;

    // XAU 卡片
    @FXML private Label xauPrice, xauChange, xauChangePct, xauTime, xauTrend;
    @FXML private Label xauOpen, xauHigh, xauLow, xauPrevClose;
    // GC 卡片
    @FXML private Label gcPrice, gcChange, gcChangePct, gcTime, gcTrend;
    // AU0 卡片
    @FXML private Label au0Price, au0Change, au0ChangePct, au0Time, au0Trend;
    // 底部
    @FXML private Label lastUpdateTime;

    // ========== 状态 ==========
    private GoldSymbol currentChartSymbol = GoldSymbol.XAU;
    private boolean autoRefreshEnabled = false;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> refreshTask;
    private final Map<GoldSymbol, List<double[]>> priceHistory = new ConcurrentHashMap<>();
    private boolean chartReady = false;

    // 颜色常量
    private static final String COLOR_UP = "#ff4757";       // 涨 - 红色
    private static final String COLOR_DOWN = "#2ed573";     // 跌 - 绿色
    private static final String COLOR_FLAT = "#a4b0be";     // 平 - 灰色
    private static final String COLOR_GOLD = "#ffd700";     // 金色

    @FXML
    public void initialize() {
        // WebView 延迟到 onPageInit 中初始化，避免 D3D 渲染崩溃
    }

    @Override
    public void onPageInit() {
        if (!chartReady) {
            initChart();
        }
        // 先加载历史分时数据，再刷新实时价格
        loadIntradayHistory();
        refreshPrices();
        startAutoRefresh();
    }

    /**
     * 加载当日分时历史数据
     */
    private void loadIntradayHistory() {
        CompletableFuture.supplyAsync(() -> {
            Map<GoldSymbol, List<double[]>> allHistory = new HashMap<>();
            for (GoldSymbol symbol : GoldSymbol.values()) {
                List<double[]> history = GoldPriceUtil.fetchIntradayHistory(symbol);
                if (!history.isEmpty()) {
                    allHistory.put(symbol, history);
                }
            }
            return allHistory;
        }).thenAccept(historyMap -> Platform.runLater(() -> {
            priceHistory.putAll(historyMap);
            log.info("加载分时数据完成: {} 个品种", historyMap.size());
            if (chartReady) {
                updateChart();
            }
        })).exceptionally(ex -> {
            log.error("加载分时数据失败", ex);
            return null;
        });
    }

    @Override
    public void onPageDestroy() {
        stopAutoRefresh();
    }

    // ========== 数据刷新 ==========

    @FXML
    public void onRefresh() {
        refreshPrices();
    }

    @FXML
    public void onToggleAutoRefresh() {
        autoRefreshEnabled = !autoRefreshEnabled;
        if (autoRefreshEnabled) {
            startAutoRefresh();
            autoRefreshBtn.setText("⏱ 停止刷新");
            autoRefreshBtn.setStyle("-fx-background-color:#ff4757; -fx-text-fill:white; -fx-border-color:#ff4757; -fx-border-radius:6; -fx-background-radius:6; -fx-padding:6 14; -fx-cursor:hand; -fx-font-size:12px;");
        } else {
            stopAutoRefresh();
            autoRefreshBtn.setText(" 自动刷新");
            autoRefreshBtn.setStyle("");
        }
    }

    @FXML
    public void onCardClick(javafx.scene.input.MouseEvent event) {
        Object source = event.getSource();
        if (source instanceof javafx.scene.layout.VBox vbox) {
            Object userData = vbox.getUserData();
            if (userData instanceof String symbolStr) {
                try {
                    GoldSymbol symbol = GoldSymbol.valueOf(symbolStr);
                    switchChartSymbol(symbol);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    private void refreshPrices() {
        // 在后台线程获取数据
        CompletableFuture.supplyAsync(() -> GoldPriceUtil.fetchAllPrices())
                .thenAccept(prices -> Platform.runLater(() -> updateUI(prices)))
                .exceptionally(ex -> {
                    log.error("刷新金价失败", ex);
                    return null;
                });
    }

    private void updateUI(List<GoldPrice> prices) {
        for (GoldPrice price : prices) {
            if (!price.isSuccess()) continue;

            switch (price.getSymbol()) {
                case XAU -> updateCard(xauPrice, xauChange, xauChangePct, xauTime, xauTrend,
                        xauOpen, xauHigh, xauLow, xauPrevClose, price);
                case GC -> updateCard(gcPrice, gcChange, gcChangePct, gcTime, gcTrend,
                        null, null, null, null, price);
                case AU0 -> updateCard(au0Price, au0Change, au0ChangePct, au0Time, au0Trend,
                        null, null, null, null, price);
            }

            // 记录历史数据用于图表
            recordHistory(price);
        }

        // 更新底部详情（以当前图表品种为准）
        Optional<GoldPrice> currentPrice = prices.stream()
                .filter(p -> p.getSymbol() == currentChartSymbol)
                .findFirst();
        currentPrice.ifPresent(p -> {
            // 底部卡片始终显示XAU的数据（作为参考）
            if (xauOpen != null) xauOpen.setText(formatNum(p.getOpen(), p.getSymbol()));
            if (xauHigh != null) xauHigh.setText(formatNum(p.getHigh(), p.getSymbol()));
            if (xauLow != null) xauLow.setText(formatNum(p.getLow(), p.getSymbol()));
            if (xauPrevClose != null) xauPrevClose.setText(formatNum(p.getPrevClose(), p.getSymbol()));
            if (lastUpdateTime != null) lastUpdateTime.setText(p.getUpdateTime());
        });

        // 更新图表
        if (chartReady) {
            updateChart();
        }
    }

    private void updateCard(Label priceLabel, Label changeLabel, Label changePctLabel,
                            Label timeLabel, Label trendLabel,
                            Label openLabel, Label highLabel, Label lowLabel, Label prevCloseLabel,
                            GoldPrice price) {
        String color = getColor(price.getDirection());

        if (priceLabel != null) {
            priceLabel.setText(price.formatPrice());
            priceLabel.setStyle("-fx-text-fill:" + color + ";");
        }
        if (changeLabel != null) {
            changeLabel.setText(price.formatChange());
            changeLabel.setStyle("-fx-text-fill:" + color + ";");
        }
        if (changePctLabel != null) {
            changePctLabel.setText(price.formatChangePercent());
            changePctLabel.setStyle("-fx-text-fill:" + color + "; -fx-font-weight:bold;");
        }
        if (timeLabel != null) {
            timeLabel.setText(price.getUpdateTime());
        }
        if (trendLabel != null) {
            String trendText = switch (price.getDirection()) {
                case 1 -> "▲ 上涨";
                case -1 -> "▼ 下跌";
                default -> "● 持平";
            };
            trendLabel.setText(trendText);
            trendLabel.setStyle("-fx-text-fill:" + color + "; -fx-font-size:11px; -fx-font-weight:bold;");
        }
    }

    // ========== 图表 ==========

    private void initChart() {
        chartWebView.getEngine().setJavaScriptEnabled(true);

        chartWebView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                chartReady = true;
                if (chartLoadingLabel != null) {
                    chartLoadingLabel.setVisible(false);
                }
                updateChart();
            }
        });

        // 加载包含ECharts的HTML页面
        String html = buildChartHtml();
        chartWebView.getEngine().loadContent(html);
    }

    private String buildChartHtml() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { background: #1e1e2e; overflow: hidden; font-family: 'Microsoft YaHei', sans-serif; }
                    #chart { width: 100vw; height: 100vh; }
                </style>
                <script src="https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js"></script>
            </head>
            <body>
                <div id="chart"></div>
                <script>
                    var chart = echarts.init(document.getElementById('chart'));
                    
                    function updateChart(data, symbolName, color) {
                        var timeData = data.map(function(item) { return item[0]; });
                        var priceData = data.map(function(item) { return item[1]; });
                        
                        // 计算Y轴范围，留出上下10%空间
                        var minPrice = Math.min.apply(null, priceData);
                        var maxPrice = Math.max.apply(null, priceData);
                        var priceRange = maxPrice - minPrice;
                        if (priceRange < 1) priceRange = 1; // 最小范围
                        var yMin = minPrice - priceRange * 0.1;
                        var yMax = maxPrice + priceRange * 0.1;
                        
                        var option = {
                            backgroundColor: 'transparent',
                            grid: {
                                top: 30, right: 60, bottom: 30, left: 70,
                                containLabel: false
                            },
                            tooltip: {
                                trigger: 'axis',
                                backgroundColor: 'rgba(30,30,46,0.95)',
                                borderColor: '#555',
                                borderWidth: 1,
                                textStyle: { color: '#ddd', fontSize: 12 },
                                formatter: function(params) {
                                    var p = params[0];
                                    return '<div style="padding:4px;">' +
                                           '<div style="color:#888;margin-bottom:4px;">' + p.axisValue + '</div>' +
                                           '<div style="color:' + color + ';font-weight:bold;font-size:15px;">' + 
                                           p.seriesName + ': ' + p.value.toFixed(2) + '</div>' +
                                           '</div>';
                                }
                            },
                            xAxis: {
                                type: 'category',
                                data: timeData,
                                axisLine: { lineStyle: { color: '#444' } },
                                axisLabel: { 
                                    color: '#888', fontSize: 10,
                                    interval: Math.max(0, Math.floor(timeData.length / 8) - 1),
                                    rotate: timeData.length > 10 ? 15 : 0
                                },
                                splitLine: { show: false }
                            },
                            yAxis: {
                                type: 'value',
                                scale: true,
                                min: yMin,
                                max: yMax,
                                axisLine: { lineStyle: { color: '#444' } },
                                axisLabel: { 
                                    color: '#888', fontSize: 11,
                                    formatter: function(value) { return value.toFixed(2); }
                                },
                                splitLine: { lineStyle: { color: '#333', type: 'dashed' } }
                            },
                            series: [{
                                name: symbolName,
                                type: 'line',
                                data: priceData,
                                smooth: true,
                                symbol: data.length <= 5 ? 'circle' : 'none',
                                symbolSize: 6,
                                lineStyle: { color: color, width: 2.5 },
                                itemStyle: { color: color },
                                areaStyle: {
                                    color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                                        { offset: 0, color: color.replace('rgb', 'rgba').replace(')', ',0.25)') },
                                        { offset: 1, color: color.replace('rgb', 'rgba').replace(')', ',0.02)') }
                                    ])
                                }
                            }],
                            dataZoom: [{
                                type: 'inside',
                                start: Math.max(0, 100 - Math.min(100, 500 / Math.max(1, data.length) * 100)),
                                end: 100
                            }]
                        };
                        chart.setOption(option, true);
                    }
                    
                    window.addEventListener('resize', function() { chart.resize(); });
                </script>
            </body>
            </html>
            """;
    }

    private void updateChart() {
        if (!chartReady) return;

        List<double[]> history = priceHistory.getOrDefault(currentChartSymbol, Collections.emptyList());
        if (history.isEmpty()) return;

        String symbolName = switch (currentChartSymbol) {
            case XAU -> "现货黄金 (USD)";
            case GC -> "COMEX黄金 (USD)";
            case AU0 -> "沪金 (CNY/克)";
        };

        String color = switch (currentChartSymbol) {
            case XAU -> "rgb(255, 215, 0)";
            case GC -> "rgb(255, 165, 0)";
            case AU0 -> "rgb(255, 71, 87)";
        };

        // 构建JSON数据，使用格式化时间作为x轴
        java.time.format.DateTimeFormatter timeFmt = 
            java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
        
        StringBuilder jsonBuilder = new StringBuilder("[");
        for (int i = 0; i < history.size(); i++) {
            double[] point = history.get(i);
            if (i > 0) jsonBuilder.append(",");
            String timeStr = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli((long)point[0]), 
                java.time.ZoneId.systemDefault()
            ).format(timeFmt);
            jsonBuilder.append("[\"").append(timeStr).append("\",").append(point[1]).append("]");
        }
        jsonBuilder.append("]");

        String js = String.format(
            "updateChart(%s, '%s', '%s');",
            jsonBuilder, symbolName, color
        );

        chartWebView.getEngine().executeScript(js);
    }

    private void switchChartSymbol(GoldSymbol symbol) {
        currentChartSymbol = symbol;

        String displayName = switch (symbol) {
            case XAU -> "现货黄金 (XAU)";
            case GC -> "COMEX黄金 (GC)";
            case AU0 -> "沪金连续 (AU0)";
        };
        if (chartSymbolLabel != null) {
            chartSymbolLabel.setText(displayName);
        }

        // 更新底部详情
        for (Map.Entry<GoldSymbol, List<double[]>> entry : priceHistory.entrySet()) {
            if (entry.getKey() == symbol && !entry.getValue().isEmpty()) {
                double[] latest = entry.getValue().get(entry.getValue().size() - 1);
                // 这里只是更新显示，实际数据从刷新中获取
                break;
            }
        }

        updateChart();
    }

    // ========== 历史数据 ==========

    private void recordHistory(GoldPrice price) {
        List<double[]> history = priceHistory.computeIfAbsent(price.getSymbol(), k -> new ArrayList<>());

        // 使用当前时间作为 x 轴值（用毫秒时间戳）
        long now = System.currentTimeMillis();
        
        // 避免重复记录（5秒内不重复）
        if (!history.isEmpty()) {
            double[] last = history.get(history.size() - 1);
            if (now - (long)last[0] < 5000) {
                // 更新最后一条记录的价格
                last[1] = price.getCurrentPrice();
                return;
            }
        }

        // 限制历史数据量（最多200条）
        if (history.size() >= 200) {
            history.remove(0);
        }

        history.add(new double[]{now, price.getCurrentPrice()});
    }

    // ========== 定时器 ==========

    private void startAutoRefresh() {
        if (scheduler != null && !scheduler.isShutdown()) return;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GoldPriceRefresh");
            t.setDaemon(true);
            return t;
        });

        refreshTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                refreshPrices();
            } catch (Exception e) {
                log.error("定时刷新金价异常", e);
            }
        }, 30, 30, TimeUnit.SECONDS);  // 每30秒刷新一次
    }

    private void stopAutoRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    // ========== 工具方法 ==========

    private String getColor(int direction) {
        return switch (direction) {
            case 1 -> COLOR_UP;
            case -1 -> COLOR_DOWN;
            default -> COLOR_FLAT;
        };
    }

    private String formatNum(double value, GoldSymbol symbol) {
        if (value == 0) return "--";
        return String.format("%.2f", value);
    }
}
