package com.devtool.controller;

import com.devtool.util.SystemUtil;
import com.devtool.util.TimeUtil;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class TimePageController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(TimePageController.class);

    // ─── Tab 按钮 ─────────────────────────────────────────────────────────
    @FXML private Button tabConvert, tabZone, tabDiff, tabMilestone;

    // ─── 时间戳转换面板 ───────────────────────────────────────────────────
    @FXML private VBox convertPane;
    @FXML private Label  liveSecLabel, liveMillisLabel, liveDateLabel;
    @FXML private TextField tsInputField;
    @FXML private ComboBox<String> tsZoneCombo;
    @FXML private Label  tsResultLabel, tsRelativeLabel;
    @FXML private TextField dateInputField;
    @FXML private ComboBox<String> dateZoneCombo;
    @FXML private Label  dateSecLabel, dateMillisLabel, dateRelativeLabel;
    @FXML private Label  convertStatusLabel;

    // ─── 多时区面板 ───────────────────────────────────────────────────────
    @FXML private VBox zonePane;
    @FXML private ListView<String> zoneListView;
    @FXML private TextField zoneConvertInput;
    @FXML private ComboBox<String> fromZoneCombo, toZoneCombo;
    @FXML private Label  zoneConvertResult, zoneStatusLabel;

    // ─── 日期差计算面板 ───────────────────────────────────────────────────
    @FXML private VBox diffPane;
    @FXML private TextField diffDateA, diffDateB;
    @FXML private Label  diffTotalDays, diffWorkDays, diffDetail;
    @FXML private Label  diffStatusLabel;

    // ─── 常用时间戳里程碑面板 ─────────────────────────────────────────────
    @FXML private VBox milestonePane;
    @FXML private ListView<String> milestoneListView;

    // ─── 实时时钟定时器 ──────────────────────────────────────────────────
    private Timeline clockTimeline;

    private enum Mode { CONVERT, ZONE, DIFF, MILESTONE }
    private Mode currentMode = Mode.CONVERT;

    // ─── 生命周期 ─────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // 初始化时区下拉列表
        java.util.List<String> zoneNames = new java.util.ArrayList<>(TimeUtil.commonZones().keySet());
        tsZoneCombo.getItems().addAll(zoneNames);
        dateZoneCombo.getItems().addAll(zoneNames);
        fromZoneCombo.getItems().addAll(zoneNames);
        toZoneCombo.getItems().addAll(zoneNames);

        // 默认北京时间
        tsZoneCombo.setValue(zoneNames.get(0));
        dateZoneCombo.setValue(zoneNames.get(0));
        fromZoneCombo.setValue(zoneNames.get(0));
        toZoneCombo.setValue(zoneNames.get(1)); // UTC

        // 输入框回车触发
        tsInputField.setOnAction(e -> onTsConvert());
        dateInputField.setOnAction(e -> onDateConvert());

        // 初始显示转换面板
        showPane(Mode.CONVERT);
        startClock();
    }

    @Override
    public void onPageInit() {
        startClock();
        refreshMilestones();
    }

    @Override
    public void onPageDestroy() {
        stopClock();
    }

    // ─── 实时时钟 ─────────────────────────────────────────────────────────

    private void startClock() {
        if (clockTimeline != null && clockTimeline.getStatus() == Animation.Status.RUNNING) return;
        clockTimeline = new Timeline(new KeyFrame(Duration.millis(100), e -> updateClock()));
        clockTimeline.setCycleCount(Animation.INDEFINITE);
        clockTimeline.play();
    }

    private void stopClock() {
        if (clockTimeline != null) clockTimeline.stop();
    }

    private void updateClock() {
        if (currentMode != Mode.CONVERT) return;
        try {
            TimeUtil.TimestampResult now = TimeUtil.nowTimestamp();
            if (liveSecLabel    != null) liveSecLabel.setText(String.valueOf(now.seconds()));
            if (liveMillisLabel != null) liveMillisLabel.setText(String.valueOf(now.millis()));
            if (liveDateLabel   != null) liveDateLabel.setText(now.zdt().format(TimeUtil.FMT_MILLIS));
        } catch (Exception ignored) {}
    }

    // ─── Tab 切换 ─────────────────────────────────────────────────────────

    @FXML private void onTabConvert()   { switchTab(Mode.CONVERT,   tabConvert); }
    @FXML private void onTabZone()      { switchTab(Mode.ZONE,      tabZone); refreshZoneList(); }
    @FXML private void onTabDiff()      { switchTab(Mode.DIFF,      tabDiff); }
    @FXML private void onTabMilestone() { switchTab(Mode.MILESTONE, tabMilestone); refreshMilestones(); }

    private void switchTab(Mode mode, Button active) {
        currentMode = mode;
        for (Button b : new Button[]{tabConvert, tabZone, tabDiff, tabMilestone})
            b.getStyleClass().removeAll("json-tab-active");
        active.getStyleClass().add("json-tab-active");
        showPane(mode);
    }

    private void showPane(Mode mode) {
        convertPane.setVisible(mode == Mode.CONVERT);   convertPane.setManaged(mode == Mode.CONVERT);
        zonePane.setVisible(mode == Mode.ZONE);         zonePane.setManaged(mode == Mode.ZONE);
        diffPane.setVisible(mode == Mode.DIFF);         diffPane.setManaged(mode == Mode.DIFF);
        milestonePane.setVisible(mode == Mode.MILESTONE); milestonePane.setManaged(mode == Mode.MILESTONE);
    }

    // ─── 时间戳转换 ───────────────────────────────────────────────────────

    /** 时间戳 → 日期 */
    @FXML private void onTsConvert() {
        String input = tsInputField.getText();
        if (input == null || input.isBlank()) { setConvertStatus("请输入时间戳"); return; }
        try {
            long ts = Long.parseLong(input.trim());
            String zoneId = getZoneId(tsZoneCombo.getValue());
            String result = TimeUtil.timestampToDatetime(ts, zoneId);
            tsResultLabel.setText(result);
            tsRelativeLabel.setText(TimeUtil.relativeTime(ts));
            setConvertStatus("转换成功");
        } catch (NumberFormatException e) {
            tsResultLabel.setText("无效的时间戳（请输入纯数字）");
            setConvertStatus("输入无效");
        } catch (Exception e) {
            tsResultLabel.setText("转换失败：" + e.getMessage());
            setConvertStatus("转换失败");
        }
    }

    /** 复制时间戳转换结果 */
    @FXML private void onCopyTsResult() {
        String text = tsResultLabel.getText();
        if (text == null || text.isBlank() || text.startsWith("—")) return;
        SystemUtil.copyToClipboardSilent(text);
        setConvertStatus("已复制：" + text);
    }

    /** 填入当前时间戳 */
    @FXML private void onFillNowTs() {
        tsInputField.setText(String.valueOf(System.currentTimeMillis() / 1000));
        onTsConvert();
    }

    /** 日期 → 时间戳 */
    @FXML private void onDateConvert() {
        String input = dateInputField.getText();
        if (input == null || input.isBlank()) { setConvertStatus("请输入日期"); return; }
        try {
            String zoneId = getZoneId(dateZoneCombo.getValue());
            TimeUtil.TimestampResult result = TimeUtil.datetimeToTimestamp(input, zoneId);
            dateSecLabel.setText(String.valueOf(result.seconds()));
            dateMillisLabel.setText(String.valueOf(result.millis()));
            dateRelativeLabel.setText(TimeUtil.relativeTime(result.millis()));
            setConvertStatus("转换成功");
        } catch (Exception e) {
            dateSecLabel.setText("—");
            dateMillisLabel.setText("—");
            setConvertStatus("解析失败：" + e.getMessage());
        }
    }

    /** 填入当前日期 */
    @FXML private void onFillNowDate() {
        dateInputField.setText(TimeUtil.nowTimestamp().zdt().format(TimeUtil.FMT_FULL));
        onDateConvert();
    }

    @FXML private void onCopyDateSec()    { copyLabel(dateSecLabel); }
    @FXML private void onCopyDateMillis() { copyLabel(dateMillisLabel); }

    private void setConvertStatus(String msg) {
        if (convertStatusLabel != null) convertStatusLabel.setText(msg);
    }

    // ─── 多时区面板 ───────────────────────────────────────────────────────

    private void refreshZoneList() {
        Map<String, String> zones = TimeUtil.nowAllZones();
        zoneListView.getItems().clear();
        zones.forEach((name, time) ->
            zoneListView.getItems().add(String.format("%-32s  %s", name, time))
        );
    }

    @FXML private void onRefreshZones() {
        refreshZoneList();
        setZoneStatus("已刷新（" + java.time.LocalTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")) + "）");
    }

    @FXML private void onZoneConvert() {
        String input = zoneConvertInput.getText();
        if (input == null || input.isBlank()) { setZoneStatus("请输入时间"); return; }
        try {
            String fromZoneId = getZoneId(fromZoneCombo.getValue());
            String toZoneId   = getZoneId(toZoneCombo.getValue());
            String result = TimeUtil.convertZone(input, fromZoneId, toZoneId);
            zoneConvertResult.setText(result);
            setZoneStatus("转换成功");
        } catch (Exception e) {
            zoneConvertResult.setText("转换失败：" + e.getMessage());
            setZoneStatus("转换失败");
        }
    }

    @FXML private void onCopyZoneResult() {
        String text = zoneConvertResult.getText();
        if (text == null || text.isBlank()) return;
        SystemUtil.copyToClipboardSilent(text);
        setZoneStatus("已复制");
    }

    @FXML private void onSwapZones() {
        String from = fromZoneCombo.getValue();
        String to   = toZoneCombo.getValue();
        fromZoneCombo.setValue(to);
        toZoneCombo.setValue(from);
        setZoneStatus("已互换时区");
    }

    @FXML private void onFillZoneNow() {
        zoneConvertInput.setText(TimeUtil.nowTimestamp().zdt().format(TimeUtil.FMT_FULL));
    }

    private void setZoneStatus(String msg) {
        if (zoneStatusLabel != null) zoneStatusLabel.setText(msg);
    }

    // ─── 日期差计算 ───────────────────────────────────────────────────────

    @FXML private void onCalcDiff() {
        String a = diffDateA.getText(), b = diffDateB.getText();
        if (a == null || a.isBlank() || b == null || b.isBlank()) {
            setDiffStatus("请输入两个日期"); return;
        }
        try {
            TimeUtil.DiffResult r = TimeUtil.dateDiff(a, b);
            diffTotalDays.setText(r.totalDays() + " 天");
            diffWorkDays.setText(r.workDays() + " 个工作日");
            diffDetail.setText(String.format("%d 年 %d 个月 %d 天  %d 时 %d 分 %d 秒",
                r.years(), r.months(), r.days(), r.hours(), r.minutes(), r.seconds()));
            setDiffStatus("计算完成");
        } catch (Exception e) {
            setDiffStatus("计算失败：" + e.getMessage());
        }
    }

    @FXML private void onFillDiffToday() {
        String today = TimeUtil.nowTimestamp().zdt().format(TimeUtil.FMT_DATE);
        if (diffDateA.getText().isBlank()) diffDateA.setText(today);
        else diffDateB.setText(today);
    }

    @FXML private void onSwapDiffDates() {
        String a = diffDateA.getText(), b = diffDateB.getText();
        diffDateA.setText(b); diffDateB.setText(a);
    }

    private void setDiffStatus(String msg) {
        if (diffStatusLabel != null) diffStatusLabel.setText(msg);
    }

    // ─── 里程碑面板 ───────────────────────────────────────────────────────

    private void refreshMilestones() {
        if (milestoneListView == null) return;
        milestoneListView.getItems().clear();
        TimeUtil.milestones().forEach((name, ts) -> {
            String dateStr = TimeUtil.timestampToDatetime(ts, "Asia/Shanghai");
            milestoneListView.getItems().add(
                String.format("%-22s  %d  (%s)", name, ts, dateStr)
            );
        });
    }

    @FXML private void onRefreshMilestones() {
        refreshMilestones();
    }

    @FXML private void onCopyMilestone() {
        String selected = milestoneListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        // 提取时间戳数字部分
        String[] parts = selected.trim().split("\\s+");
        for (String p : parts) {
            if (p.matches("\\d{9,13}")) {
                SystemUtil.copyToClipboardSilent(p);
                return;
            }
        }
        SystemUtil.copyToClipboardSilent(selected);
    }

    // ─── 工具方法 ─────────────────────────────────────────────────────────

    /** 从显示名称（如"北京 (CST, UTC+8)"）获取 ZoneId 字符串 */
    private String getZoneId(String displayName) {
        if (displayName == null) return "Asia/Shanghai";
        return TimeUtil.commonZones().getOrDefault(displayName, "Asia/Shanghai");
    }

    private void copyLabel(Label label) {
        String text = label.getText();
        if (text == null || text.equals("—") || text.isBlank()) return;
        SystemUtil.copyToClipboardSilent(text);
        setConvertStatus("已复制：" + text);
    }
}

