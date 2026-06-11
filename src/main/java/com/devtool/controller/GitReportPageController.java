package com.devtool.controller;

import com.devtool.util.GitLogUtil;
import com.devtool.util.SystemUtil;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class GitReportPageController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(GitReportPageController.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ─── 配置区 ───────────────────────────────────────────────────────────
    @FXML private TextArea  repoPathsArea;
    @FXML private TextField authorField;
    @FXML private TextField startDateField;   // 开始日期
    @FXML private TextField endDateField;     // 结束日期
    @FXML private TextField branchField;
    @FXML private CheckBox  chkIncludeFiles;
    @FXML private Spinner<Integer> maxFilesSpinner;
    @FXML private ComboBox<String> formatCombo;

    // ─── 操作区 ───────────────────────────────────────────────────────────
    @FXML private Button    btnGenerate;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Label     statusLabel;

    // ─── 结果区 ───────────────────────────────────────────────────────────
    @FXML private VBox      resultPane;
    @FXML private VBox      emptyHintPane;
    @FXML private TextArea  reportOutputArea;
    @FXML private TextArea  summaryOutputArea;
    @FXML private Label     statsLabel;

    // ─── 分天导航栏 ───────────────────────────────────────────────────────
    @FXML private HBox      dayNavBar;          // 日期导航条（多天时显示）
    @FXML private ComboBox<String> dayNavCombo; // 日期选择下拉
    @FXML private Label     dayNavHint;         // 当天提交数提示

    // ─── 仓库信息预览 ──────────────────────────────────────────────────────
    @FXML private VBox      repoInfoPane;
    @FXML private ListView<String> repoInfoList;

    // ─── Tab ─────────────────────────────────────────────────────────────
    @FXML private Button tabReport, tabSummary;
    @FXML private VBox   reportTabPane, summaryTabPane;

    // ─── 数据状态 ─────────────────────────────────────────────────────────
    // 多日结果：key=日期字符串 "yyyy-MM-dd"，value=各仓库结果
    private LinkedHashMap<LocalDate, List<GitLogUtil.RepoResult>> lastDailyMap;
    private GitLogUtil.RangeReportConfig lastRangeConfig;
    // 多日各天缓存的报告文本（key=日期字符串，value=报告文本）
    private final Map<String, String> reportCache   = new LinkedHashMap<>();
    private final Map<String, String> summaryCache  = new LinkedHashMap<>();

    // ─── 生命周期 ─────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        formatCombo.getItems().addAll("纯文本", "Markdown", "钉钉消息", "飞书消息");
        formatCombo.setValue("Markdown");

        // 默认：开始=今天，结束=今天
        String today = LocalDate.now().format(FMT);
        startDateField.setText(today);
        endDateField.setText(today);

        // 格式/includeFiles 切换 → 重渲染（无需重新拉取）
        formatCombo.valueProperty().addListener((obs, o, n) -> {
            if (lastDailyMap != null) rerenderAll();
        });
        chkIncludeFiles.selectedProperty().addListener((obs, o, n) -> {
            if (lastDailyMap != null) rerenderAll();
        });

        // 日期导航 combo 切换 → 显示对应天的报告
        dayNavCombo.valueProperty().addListener((obs, o, n) -> {
            if (n != null && lastDailyMap != null) showDayReport(n);
        });

        // 初始状态
        resultPane.setVisible(false); resultPane.setManaged(false);
        repoInfoPane.setVisible(false); repoInfoPane.setManaged(false);
        loadingIndicator.setVisible(false); loadingIndicator.setManaged(false);
        emptyHintPane.setVisible(true); emptyHintPane.setManaged(true);
        dayNavBar.setVisible(false); dayNavBar.setManaged(false);
        showTab(true);
        checkGitAvailable();
    }

    @Override
    public void onPageInit() { setStatus("就绪"); }

    // ─── Git 可用性检查 ───────────────────────────────────────────────────

    private void checkGitAvailable() {
        Task<Boolean> t = new Task<>() { @Override protected Boolean call() { return GitLogUtil.isGitAvailable(); } };
        t.setOnSucceeded(e -> {
            if (!t.getValue()) { setStatus("⚠ 未检测到 git 命令，请确保 git 已安装并加入 PATH"); btnGenerate.setDisable(true); }
            else setStatus("✓ git 可用，填写配置后点击「生成日报」");
        });
        new Thread(t).start();
    }

    // ─── Tab 切换 ─────────────────────────────────────────────────────────

    @FXML private void onTabReport()  { showTab(true); }
    @FXML private void onTabSummary() { showTab(false); }

    private void showTab(boolean isReport) {
        tabReport.getStyleClass().removeAll("json-tab-active");
        tabSummary.getStyleClass().removeAll("json-tab-active");
        if (isReport) tabReport.getStyleClass().add("json-tab-active");
        else          tabSummary.getStyleClass().add("json-tab-active");
        reportTabPane.setVisible(isReport);   reportTabPane.setManaged(isReport);
        summaryTabPane.setVisible(!isReport); summaryTabPane.setManaged(!isReport);
    }

    // ─── 日期快捷填入 ─────────────────────────────────────────────────────

    @FXML private void onFillToday() {
        String today = LocalDate.now().format(FMT);
        startDateField.setText(today);
        endDateField.setText(today);
    }

    @FXML private void onFillYesterday() {
        String yest = LocalDate.now().minusDays(1).format(FMT);
        startDateField.setText(yest);
        endDateField.setText(yest);
    }

    @FXML private void onFillThisWeek() {
        LocalDate today = LocalDate.now();
        // 本周一到今天
        LocalDate monday = today.with(java.time.DayOfWeek.MONDAY);
        startDateField.setText(monday.format(FMT));
        endDateField.setText(today.format(FMT));
    }

    @FXML private void onFillLastWeek() {
        LocalDate today = LocalDate.now();
        LocalDate lastMonday = today.minusWeeks(1).with(java.time.DayOfWeek.MONDAY);
        LocalDate lastSunday = lastMonday.plusDays(6);
        startDateField.setText(lastMonday.format(FMT));
        endDateField.setText(lastSunday.format(FMT));
    }

    @FXML private void onFillThisMonth() {
        LocalDate today = LocalDate.now();
        startDateField.setText(today.withDayOfMonth(1).format(FMT));
        endDateField.setText(today.format(FMT));
    }

    // ─── 仓库路径辅助 ─────────────────────────────────────────────────────

    @FXML private void onBrowseRepo() {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("选择 Git 仓库目录");
        File initDir = new File(System.getProperty("user.home"));
        String existing = repoPathsArea.getText();
        if (!existing.isBlank()) {
            String first = existing.split("\n")[0].trim();
            File f = new File(first);
            if (f.exists()) initDir = f.getParentFile() != null ? f.getParentFile() : initDir;
        }
        chooser.setInitialDirectory(initDir);
        File selected = chooser.showDialog(repoPathsArea.getScene().getWindow());
        if (selected != null) {
            String cur = repoPathsArea.getText().trim();
            if (cur.isEmpty()) repoPathsArea.setText(selected.getAbsolutePath());
            else repoPathsArea.appendText("\n" + selected.getAbsolutePath());
        }
    }

    @FXML private void onCheckRepos() {
        List<String> paths = getRepoPaths();
        if (paths.isEmpty()) { setStatus("请先填写仓库路径"); return; }
        repoInfoList.getItems().clear();
        repoInfoPane.setVisible(true); repoInfoPane.setManaged(true);
        for (String path : paths) {
            File dir = new File(path);
            if (!dir.exists()) { repoInfoList.getItems().add("❌ 路径不存在：" + path); continue; }
            if (!new File(dir, ".git").exists()) { repoInfoList.getItems().add("⚠ 不是 git 仓库：" + path); continue; }
            String branch = GitLogUtil.getCurrentBranch(path);
            List<String> branches = GitLogUtil.getBranches(path);
            repoInfoList.getItems().add("✅ " + dir.getName() + "  当前分支：" + branch + "  本地分支：" + String.join(", ", branches));
        }
        setStatus("检测完成，共 " + paths.size() + " 个路径");
    }

    // ─── 核心：生成日报 ───────────────────────────────────────────────────

    @FXML private void onGenerate() {
        List<String> paths = getRepoPaths();
        if (paths.isEmpty()) { setStatus("请填写至少一个仓库路径"); return; }

        LocalDate startDate, endDate;
        try {
            startDate = LocalDate.parse(startDateField.getText().trim(), FMT);
            endDate   = LocalDate.parse(endDateField.getText().trim(), FMT);
        } catch (Exception e) {
            setStatus("日期格式错误，请使用 yyyy-MM-dd 格式"); return;
        }
        if (endDate.isBefore(startDate)) {
            setStatus("结束日期不能早于开始日期"); return;
        }
        long dayCount = startDate.until(endDate, java.time.temporal.ChronoUnit.DAYS) + 1;
        if (dayCount > 90) {
            setStatus("日期范围不能超过 90 天（当前 " + dayCount + " 天）"); return;
        }

        lastRangeConfig = new GitLogUtil.RangeReportConfig(
                paths,
                authorField.getText().trim(),
                startDate, endDate,
                branchField.getText().trim(),
                chkIncludeFiles.isSelected(),
                maxFilesSpinner.getValue()
        );

        btnGenerate.setDisable(true);
        loadingIndicator.setVisible(true); loadingIndicator.setManaged(true);
        setStatus("正在读取 " + dayCount + " 天的 Git 日志...");

        Task<LinkedHashMap<LocalDate, List<GitLogUtil.RepoResult>>> task = new Task<>() {
            @Override
            protected LinkedHashMap<LocalDate, List<GitLogUtil.RepoResult>> call() {
                return GitLogUtil.fetchByRange(lastRangeConfig);
            }
        };

        task.setOnSucceeded(e -> {
            lastDailyMap = task.getValue();
            btnGenerate.setDisable(false);
            loadingIndicator.setVisible(false); loadingIndicator.setManaged(false);

            // 统计
            int grandTotal = lastDailyMap.values().stream()
                    .flatMap(List::stream)
                    .mapToInt(r -> r.commits() == null ? 0 : r.commits().size()).sum();
            long activeDays = lastDailyMap.entrySet().stream()
                    .filter(en -> en.getValue().stream().anyMatch(r -> !r.commits().isEmpty()))
                    .count();
            statsLabel.setText("共 " + grandTotal + " 次提交  |  " + dayCount + " 天（有提交 "
                    + activeDays + " 天）  |  " + paths.size() + " 个仓库");

            // 生成各天报告缓存 + 摘要缓存
            rerenderAll();

            // 初始化日期导航
            setupDayNav();

            resultPane.setVisible(true);   resultPane.setManaged(true);
            emptyHintPane.setVisible(false); emptyHintPane.setManaged(false);
            setStatus("生成完成！" + startDate.format(FMT) + " ~ " + endDate.format(FMT)
                    + "  共 " + grandTotal + " 次提交");
            log.info("日报生成完成 range={}/{}  commits={}", startDate, endDate, grandTotal);
        });

        task.setOnFailed(e -> {
            btnGenerate.setDisable(false);
            loadingIndicator.setVisible(false); loadingIndicator.setManaged(false);
            setStatus("生成失败：" + task.getException().getMessage());
            log.error("日报生成失败", task.getException());
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ─── 渲染 ─────────────────────────────────────────────────────────────

    /** 重新生成所有天的报告和摘要缓存，更新输出区 */
    private void rerenderAll() {
        if (lastDailyMap == null || lastRangeConfig == null) return;

        GitLogUtil.RangeReportConfig cfg = new GitLogUtil.RangeReportConfig(
                lastRangeConfig.repoPaths(), lastRangeConfig.author(),
                lastRangeConfig.startDate(), lastRangeConfig.endDate(),
                lastRangeConfig.branch(), chkIncludeFiles.isSelected(),
                maxFilesSpinner.getValue()
        );
        GitLogUtil.ReportFormat fmt = getFormat();

        reportCache.clear();
        summaryCache.clear();

        // 生成每天的报告
        for (Map.Entry<LocalDate, List<GitLogUtil.RepoResult>> entry : lastDailyMap.entrySet()) {
            LocalDate date = entry.getKey();
            String key = date.format(FMT);
            String report = GitLogUtil.generateReport(entry.getValue(), cfg.forDate(date), fmt);
            reportCache.put(key, report);
            summaryCache.put(key, GitLogUtil.extractWorkSummary(entry.getValue()));
        }

        // 汇总报告
        boolean isSingleDay = lastDailyMap.size() == 1;
        if (isSingleDay) {
            String key = lastDailyMap.keySet().iterator().next().format(FMT);
            reportOutputArea.setText(reportCache.get(key));
            summaryOutputArea.setText(summaryCache.get(key));
        } else {
            // 多日：默认显示汇总
            String allReport = GitLogUtil.generateRangeReport(lastDailyMap, cfg, fmt);
            String allSummary = GitLogUtil.extractRangeWorkSummary(lastDailyMap);
            reportOutputArea.setText(allReport);
            summaryOutputArea.setText(allSummary);
        }

        // 刷新日期导航（若已初始化）
        refreshDayNavHint();
    }

    /** 初始化日期导航条 */
    private void setupDayNav() {
        boolean isMultiDay = lastDailyMap.size() > 1;
        dayNavBar.setVisible(isMultiDay); dayNavBar.setManaged(isMultiDay);

        if (!isMultiDay) return;

        dayNavCombo.getItems().clear();
        // 第一项是汇总
        dayNavCombo.getItems().add("📊 全部汇总（" + lastDailyMap.size() + " 天）");
        for (LocalDate date : lastDailyMap.keySet()) {
            List<GitLogUtil.RepoResult> results = lastDailyMap.get(date);
            int cnt = results.stream().mapToInt(r -> r.commits() == null ? 0 : r.commits().size()).sum();
            dayNavCombo.getItems().add(date.format(FMT) + "  (" + cnt + " 次提交)");
        }
        dayNavCombo.setValue(dayNavCombo.getItems().get(0));
    }

    /** 日期导航切换时显示对应天的报告 */
    private void showDayReport(String navValue) {
        if (navValue == null || navValue.startsWith("📊")) {
            // 汇总
            GitLogUtil.RangeReportConfig cfg = new GitLogUtil.RangeReportConfig(
                    lastRangeConfig.repoPaths(), lastRangeConfig.author(),
                    lastRangeConfig.startDate(), lastRangeConfig.endDate(),
                    lastRangeConfig.branch(), chkIncludeFiles.isSelected(),
                    maxFilesSpinner.getValue()
            );
            String allReport  = GitLogUtil.generateRangeReport(lastDailyMap, cfg, getFormat());
            String allSummary = GitLogUtil.extractRangeWorkSummary(lastDailyMap);
            reportOutputArea.setText(allReport);
            summaryOutputArea.setText(allSummary);
            dayNavHint.setText("展示全部 " + lastDailyMap.size() + " 天汇总");
        } else {
            // 取日期前10位
            String dateKey = navValue.substring(0, 10);
            reportOutputArea.setText(reportCache.getOrDefault(dateKey, "（无数据）"));
            summaryOutputArea.setText(summaryCache.getOrDefault(dateKey, "（无数据）"));
            dayNavHint.setText(dateKey);
        }
    }

    private void refreshDayNavHint() {
        if (dayNavCombo.getValue() != null) showDayReport(dayNavCombo.getValue());
    }

    private GitLogUtil.ReportFormat getFormat() {
        return switch (formatCombo.getValue()) {
            case "Markdown"  -> GitLogUtil.ReportFormat.MARKDOWN;
            case "钉钉消息"   -> GitLogUtil.ReportFormat.DINGTALK;
            case "飞书消息"   -> GitLogUtil.ReportFormat.FEISHU;
            default          -> GitLogUtil.ReportFormat.PLAIN_TEXT;
        };
    }

    // ─── 复制操作 ─────────────────────────────────────────────────────────

    @FXML private void onCopyReport() {
        String text = reportOutputArea.getText();
        if (text == null || text.isBlank()) { setStatus("报告为空"); return; }
        SystemUtil.copyToClipboardSilent(text);
        setStatus("已复制报告（" + text.length() + " 字符）");
    }

    @FXML private void onCopySummary() {
        String text = summaryOutputArea.getText();
        if (text == null || text.isBlank()) { setStatus("摘要为空"); return; }
        SystemUtil.copyToClipboardSilent(text);
        setStatus("已复制工作摘要");
    }

    @FXML private void onCopyAllDays() {
        // 将所有天的报告拼成一个文件复制
        if (reportCache.isEmpty()) { setStatus("请先生成日报"); return; }
        StringBuilder sb = new StringBuilder();
        for (String v : reportCache.values()) sb.append(v).append("\n\n");
        SystemUtil.copyToClipboardSilent(sb.toString());
        setStatus("已复制全部 " + reportCache.size() + " 天的日报");
    }

    @FXML private void onClearResult() {
        reportOutputArea.clear();
        summaryOutputArea.clear();
        statsLabel.setText("");
        reportCache.clear(); summaryCache.clear();
        dayNavCombo.getItems().clear();
        dayNavBar.setVisible(false); dayNavBar.setManaged(false);
        resultPane.setVisible(false); resultPane.setManaged(false);
        emptyHintPane.setVisible(true); emptyHintPane.setManaged(true);
        lastDailyMap = null; lastRangeConfig = null;
        setStatus("已清空");
    }

    // ─── 工具 ─────────────────────────────────────────────────────────────

    private List<String> getRepoPaths() {
        String text = repoPathsArea.getText();
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                .collect(Collectors.toList());
    }

    private void setStatus(String msg) {
        if (statusLabel != null) Platform.runLater(() -> statusLabel.setText(msg));
    }
}

