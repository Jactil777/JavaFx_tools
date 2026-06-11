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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GitReportPageController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(GitReportPageController.class);

    // ─── 配置区 ───────────────────────────────────────────────────────────
    @FXML private TextArea  repoPathsArea;       // 仓库路径列表（每行一个）
    @FXML private TextField authorField;         // 作者名
    @FXML private TextField dateField;           // 日期
    @FXML private TextField branchField;         // 分支（可空）
    @FXML private CheckBox  chkIncludeFiles;     // 是否包含变更文件
    @FXML private Spinner<Integer> maxFilesSpinner; // 最多显示文件数
    @FXML private ComboBox<String> formatCombo;  // 报告格式

    // ─── 操作区 ───────────────────────────────────────────────────────────
    @FXML private Button    btnGenerate;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Label     statusLabel;

    // ─── 结果区 ───────────────────────────────────────────────────────────
    @FXML private VBox      resultPane;
    @FXML private VBox      emptyHintPane;   // 空状态提示
    @FXML private TextArea  reportOutputArea;
    @FXML private TextArea  summaryOutputArea;   // 智能摘要（今日工作内容）
    @FXML private Label     statsLabel;          // 统计信息

    // ─── 仓库信息预览 ──────────────────────────────────────────────────────
    @FXML private VBox      repoInfoPane;
    @FXML private ListView<String> repoInfoList;

    // ─── Tab ─────────────────────────────────────────────────────────────
    @FXML private Button tabReport, tabSummary;
    @FXML private VBox   reportTabPane, summaryTabPane;

    // 最后一次生成结果（用于切格式时快速重渲染）
    private List<GitLogUtil.RepoResult> lastResults;
    private GitLogUtil.DailyReportConfig lastConfig;

    // ─── 生命周期 ─────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // 格式选项
        formatCombo.getItems().addAll("纯文本", "Markdown", "钉钉消息", "飞书消息");
        formatCombo.setValue("Markdown");

        // 日期默认今天
        dateField.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        // 格式切换时立刻重渲染（无需重新拉取）
        formatCombo.valueProperty().addListener((obs, o, n) -> {
            if (lastResults != null && lastConfig != null) rerenderReport();
        });

        // includeFiles 切换也重渲染
        chkIncludeFiles.selectedProperty().addListener((obs, o, n) -> {
            if (lastResults != null && lastConfig != null) rerenderReport();
        });

        // 默认隐藏结果区和加载指示
        resultPane.setVisible(false); resultPane.setManaged(false);
        repoInfoPane.setVisible(false); repoInfoPane.setManaged(false);
        loadingIndicator.setVisible(false); loadingIndicator.setManaged(false);
        // 空状态提示默认显示
        emptyHintPane.setVisible(true); emptyHintPane.setManaged(true);

        // 显示报告 Tab
        showTab(true);

        // 检查 git 是否可用
        checkGitAvailable();
    }

    @Override
    public void onPageInit() {
        // 不清空内容，数据持久
        setStatus("就绪");
    }

    // ─── Git 可用性检查 ───────────────────────────────────────────────────

    private void checkGitAvailable() {
        Task<Boolean> task = new Task<>() {
            @Override protected Boolean call() { return GitLogUtil.isGitAvailable(); }
        };
        task.setOnSucceeded(e -> {
            if (!task.getValue()) {
                setStatus("⚠ 未检测到 git 命令，请确保 git 已安装并加入 PATH");
                btnGenerate.setDisable(true);
            } else {
                setStatus("✓ git 可用，填写配置后点击「生成日报」");
            }
        });
        new Thread(task).start();
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

    // ─── 仓库路径辅助 ─────────────────────────────────────────────────────

    @FXML private void onBrowseRepo() {
        // 打开文件夹选择（用 JavaFX DirectoryChooser）
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("选择 Git 仓库目录");
        File initDir = new File(System.getProperty("user.home"));
        // 如果已有路径，用第一个路径作为初始目录
        String existing = repoPathsArea.getText();
        if (!existing.isBlank()) {
            String first = existing.split("\n")[0].trim();
            File f = new File(first);
            if (f.exists()) initDir = f;
        }
        chooser.setInitialDirectory(initDir);
        File selected = chooser.showDialog(repoPathsArea.getScene().getWindow());
        if (selected != null) {
            String current = repoPathsArea.getText().trim();
            if (current.isEmpty()) repoPathsArea.setText(selected.getAbsolutePath());
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
            if (!dir.exists()) {
                repoInfoList.getItems().add("❌ 路径不存在：" + path);
                continue;
            }
            if (!new File(dir, ".git").exists()) {
                repoInfoList.getItems().add("⚠ 不是 git 仓库：" + path);
                continue;
            }
            String branch  = GitLogUtil.getCurrentBranch(path);
            List<String> branches = GitLogUtil.getBranches(path);
            repoInfoList.getItems().add("✅ " + dir.getName() +
                    "  当前分支：" + branch +
                    "  本地分支：" + String.join(", ", branches));
        }
        setStatus("检测完成，共 " + paths.size() + " 个路径");
    }

    @FXML private void onFillToday() {
        dateField.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    }

    @FXML private void onFillYesterday() {
        dateField.setText(LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    }

    // ─── 核心：生成日报 ───────────────────────────────────────────────────

    @FXML private void onGenerate() {
        List<String> paths = getRepoPaths();
        if (paths.isEmpty()) { setStatus("请填写至少一个仓库路径"); return; }

        String dateStr = dateField.getText().trim();
        LocalDate date;
        try {
            date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            setStatus("日期格式错误，请使用 yyyy-MM-dd 格式");
            return;
        }

        String author = authorField.getText().trim();
        String branch = branchField.getText().trim();

        lastConfig = new GitLogUtil.DailyReportConfig(
                paths, author, date, branch,
                chkIncludeFiles.isSelected(),
                maxFilesSpinner.getValue()
        );

        // 异步拉取，避免 UI 卡住
        btnGenerate.setDisable(true);
        loadingIndicator.setVisible(true); loadingIndicator.setManaged(true);
        setStatus("正在读取 Git 日志...");

        Task<List<GitLogUtil.RepoResult>> task = new Task<>() {
            @Override
            protected List<GitLogUtil.RepoResult> call() {
                return GitLogUtil.fetchAll(lastConfig);
            }
        };

        task.setOnSucceeded(e -> {
            lastResults = task.getValue();
            btnGenerate.setDisable(false);
            loadingIndicator.setVisible(false); loadingIndicator.setManaged(false);

            // 统计
            int total = lastResults.stream()
                    .mapToInt(r -> r.commits() == null ? 0 : r.commits().size()).sum();
            long errCount = lastResults.stream().filter(r -> r.error() != null).count();
            statsLabel.setText("共 " + total + " 次提交  |  " +
                    lastResults.size() + " 个仓库  |  " +
                    (errCount > 0 ? errCount + " 个读取失败" : "全部读取成功"));

            // 渲染报告
            rerenderReport();

            // 生成智能摘要
            String summary = GitLogUtil.extractWorkSummary(lastResults);
            summaryOutputArea.setText(summary);

            resultPane.setVisible(true); resultPane.setManaged(true);
            emptyHintPane.setVisible(false); emptyHintPane.setManaged(false);
            setStatus("生成完成！" + dateStr + " 共 " + total + " 次提交");

            log.info("日报生成完成 date={} commits={}", dateStr, total);
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

    private void rerenderReport() {
        if (lastResults == null || lastConfig == null) return;

        // 重建含新选项的 config
        GitLogUtil.DailyReportConfig cfg = new GitLogUtil.DailyReportConfig(
                lastConfig.repoPaths(), lastConfig.author(), lastConfig.date(),
                lastConfig.branch(), chkIncludeFiles.isSelected(),
                maxFilesSpinner.getValue()
        );

        GitLogUtil.ReportFormat fmt = switch (formatCombo.getValue()) {
            case "Markdown"  -> GitLogUtil.ReportFormat.MARKDOWN;
            case "钉钉消息"   -> GitLogUtil.ReportFormat.DINGTALK;
            case "飞书消息"   -> GitLogUtil.ReportFormat.FEISHU;
            default          -> GitLogUtil.ReportFormat.PLAIN_TEXT;
        };

        String report = GitLogUtil.generateReport(lastResults, cfg, fmt);
        reportOutputArea.setText(report);
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

    @FXML private void onClearResult() {
        reportOutputArea.clear();
        summaryOutputArea.clear();
        statsLabel.setText("");
        resultPane.setVisible(false); resultPane.setManaged(false);
        emptyHintPane.setVisible(true); emptyHintPane.setManaged(true);
        lastResults = null; lastConfig = null;
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

