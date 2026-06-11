package com.devtool.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Git 日志工具类
 * 通过本地执行 git 命令读取提交记录，生成开发日报
 */
public class GitLogUtil {

    private GitLogUtil() {}

    // ─── 数据结构 ──────────────────────────────────────────────────────────

    /** 单条提交记录 */
    public record Commit(
        String hash,       // 短 hash
        String author,     // 作者
        String datetime,   // 提交时间
        String message,    // 提交信息（第一行）
        String body,       // 提交信息（完整 body，可能为空）
        List<String> changedFiles  // 变更文件列表
    ) {}

    /** 单个仓库的分析结果 */
    public record RepoResult(
        String repoPath,   // 仓库路径
        String repoName,   // 仓库名称
        String branch,     // 分支
        List<Commit> commits,  // 提交列表
        String error       // 如有错误则非空
    ) {}

    /** 日报配置 */
    public record DailyReportConfig(
        List<String> repoPaths,  // 仓库路径列表
        String author,           // git 作者名（支持模糊匹配）
        LocalDate date,          // 单日模式：日期（dateRange=false时使用）
        String branch,           // 分支（空=所有分支）
        boolean includeFiles,    // 是否显示变更文件
        int maxFiles             // 每次提交最多显示多少个文件
    ) {}

    /** 日期范围配置（扩展字段，date=startDate, endDate 为结束） */
    public record RangeReportConfig(
        List<String> repoPaths,
        String author,
        LocalDate startDate,
        LocalDate endDate,
        String branch,
        boolean includeFiles,
        int maxFiles
    ) {
        /** 日期范围内每天的列表（含首尾） */
        public List<LocalDate> dates() {
            List<LocalDate> list = new ArrayList<>();
            LocalDate d = startDate;
            while (!d.isAfter(endDate)) { list.add(d); d = d.plusDays(1); }
            return list;
        }
        /** 转为单日 DailyReportConfig */
        public DailyReportConfig forDate(LocalDate date) {
            return new DailyReportConfig(repoPaths, author, date, branch, includeFiles, maxFiles);
        }
    }

    /** 按日期范围拉取每天的提交结果，key=日期，value=各仓库结果 */
    public static java.util.LinkedHashMap<LocalDate, List<RepoResult>> fetchByRange(RangeReportConfig config) {
        java.util.LinkedHashMap<LocalDate, List<RepoResult>> map = new java.util.LinkedHashMap<>();
        for (LocalDate date : config.dates()) {
            map.put(date, fetchAll(config.forDate(date)));
        }
        return map;
    }

    // ─── 核心：拉取 Git 日志 ───────────────────────────────────────────────

    /**
     * 拉取指定仓库在指定日期的提交记录
     */
    public static RepoResult fetchCommits(String repoPath, String author, LocalDate date, String branch) {
        File dir = new File(repoPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return new RepoResult(repoPath, repoPath, branch, List.of(),
                    "路径不存在：" + repoPath);
        }
        // 检查是否是 git 仓库
        if (!new File(dir, ".git").exists() && !findGitDir(dir)) {
            return new RepoResult(repoPath, dir.getName(), branch, List.of(),
                    "不是 Git 仓库（未找到 .git 目录）：" + repoPath);
        }

        String repoName = dir.getName();
        String since = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " 00:00:00";
        String until = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " 23:59:59";

        // 构建 git log 命令
        List<String> cmd = new ArrayList<>(Arrays.asList(
            "git", "log",
            "--since=" + since,
            "--until=" + until,
            "--format=%H|%h|%an|%ae|%ad|%s|%b|---END---",
            "--date=format:%Y-%m-%d %H:%M:%S",
            "--no-merges"
        ));
        if (author != null && !author.isBlank()) {
            cmd.add("--author=" + author.trim());
        }
        if (branch != null && !branch.isBlank()) {
            cmd.add(branch.trim());
        } else {
            cmd.add("--all");
        }

        try {
            String raw = execGit(cmd, dir);
            List<Commit> commits = parseCommits(raw, repoPath, dir, date, author);
            return new RepoResult(repoPath, repoName, branch, commits, null);
        } catch (Exception e) {
            return new RepoResult(repoPath, repoName, branch, List.of(),
                    "读取失败：" + e.getMessage());
        }
    }

    private static boolean findGitDir(File dir) {
        // 向上最多找 3 层
        File f = dir.getParentFile();
        for (int i = 0; i < 3 && f != null; i++) {
            if (new File(f, ".git").exists()) return true;
            f = f.getParentFile();
        }
        return false;
    }

    /**
     * 批量拉取多个仓库的提交记录
     */
    public static List<RepoResult> fetchAll(DailyReportConfig config) {
        List<RepoResult> results = new ArrayList<>();
        for (String path : config.repoPaths()) {
            if (path == null || path.isBlank()) continue;
            results.add(fetchCommits(path.trim(), config.author(),
                    config.date(), config.branch()));
        }
        return results;
    }

    // ─── 解析 git log 输出 ────────────────────────────────────────────────

    private static List<Commit> parseCommits(String raw, String repoPath, File dir,
                                              LocalDate date, String author) {
        if (raw == null || raw.isBlank()) return List.of();

        List<Commit> commits = new ArrayList<>();
        // 用 ---END--- 分割每次提交
        String[] blocks = raw.split("---END---");
        for (String block : blocks) {
            block = block.trim();
            if (block.isEmpty()) continue;
            String[] lines = block.split("\n", 2);
            if (lines.length == 0) continue;

            String[] parts = lines[0].split("\\|", 7);
            if (parts.length < 6) continue;

            String fullHash  = parts[0].trim();
            String shortHash = parts[1].trim();
            String authorName = parts[2].trim();
            // String authorEmail = parts[3].trim();
            String datetime  = parts[4].trim();
            String subject   = parts[5].trim();
            String body      = parts.length > 6 ? parts[6].trim() : "";

            // 获取变更文件
            List<String> files = fetchChangedFiles(fullHash, dir);

            commits.add(new Commit(shortHash, authorName, datetime, subject, body, files));
        }
        return commits;
    }

    /** 获取某次提交的变更文件列表 */
    private static List<String> fetchChangedFiles(String hash, File dir) {
        try {
            List<String> cmd = Arrays.asList("git", "diff-tree", "--no-commit-id", "-r",
                    "--name-status", hash);
            String output = execGit(cmd, dir);
            List<String> files = new ArrayList<>();
            for (String line : output.split("\n")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+", 2);
                if (parts.length == 2) {
                    String status = parts[0].trim();
                    String file   = parts[1].trim();
                    String prefix = switch (status.charAt(0)) {
                        case 'A' -> "[新增]";
                        case 'D' -> "[删除]";
                        case 'M' -> "[修改]";
                        case 'R' -> "[重命名]";
                        default  -> "[变更]";
                    };
                    files.add(prefix + " " + file);
                }
            }
            return files;
        } catch (Exception e) {
            return List.of();
        }
    }

    // ─── 执行 git 命令 ────────────────────────────────────────────────────

    public static String execGit(List<String> cmd, File workDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir);
        pb.redirectErrorStream(false);
        // 设置 UTF-8 环境
        pb.environment().put("LANG", "en_US.UTF-8");
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");

        Process process = pb.start();
        StringBuilder sb = new StringBuilder();

        // 读取标准输出（优先 UTF-8，失败时尝试 GBK）
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            // 读取错误输出
            try (BufferedReader errReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder errSb = new StringBuilder();
                String line;
                while ((line = errReader.readLine()) != null) errSb.append(line).append("\n");
                String err = errSb.toString().trim();
                if (!err.isEmpty()) throw new Exception(err);
            }
        }
        return sb.toString();
    }

    /** 验证 git 是否可用 */
    public static boolean isGitAvailable() {
        try {
            execGit(Arrays.asList("git", "--version"), new File(System.getProperty("user.home")));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 获取仓库当前分支名 */
    public static String getCurrentBranch(String repoPath) {
        try {
            String result = execGit(Arrays.asList("git", "rev-parse", "--abbrev-ref", "HEAD"),
                    new File(repoPath));
            return result.trim();
        } catch (Exception e) {
            return "";
        }
    }

    /** 获取仓库所有本地分支 */
    public static List<String> getBranches(String repoPath) {
        try {
            String result = execGit(Arrays.asList("git", "branch"), new File(repoPath));
            List<String> branches = new ArrayList<>();
            for (String line : result.split("\n")) {
                String b = line.replace("*", "").trim();
                if (!b.isEmpty()) branches.add(b);
            }
            return branches;
        } catch (Exception e) {
            return List.of();
        }
    }

    // ─── 日报生成 ─────────────────────────────────────────────────────────

    public enum ReportFormat { PLAIN_TEXT, MARKDOWN, DINGTALK, FEISHU }

    /**
     * 生成日报文本
     */
    public static String generateReport(List<RepoResult> results, DailyReportConfig config,
                                         ReportFormat format) {
        return switch (format) {
            case MARKDOWN   -> buildMarkdown(results, config);
            case DINGTALK   -> buildDingTalk(results, config);
            case FEISHU     -> buildFeiShu(results, config);
            default         -> buildPlainText(results, config);
        };
    }

    private static String buildPlainText(List<RepoResult> results, DailyReportConfig config) {
        StringBuilder sb = new StringBuilder();
        String dateStr = config.date().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
        String author  = config.author().isBlank() ? "所有人" : config.author();

        sb.append("========================================\n");
        sb.append("  开发日报 · ").append(dateStr).append("\n");
        sb.append("  开发人员：").append(author).append("\n");
        sb.append("========================================\n\n");

        int totalCommits = 0;
        for (RepoResult repo : results) {
            if (repo.error() != null) {
                sb.append("【").append(repo.repoName()).append("】读取失败：").append(repo.error()).append("\n\n");
                continue;
            }
            totalCommits += repo.commits().size();
            sb.append("【").append(repo.repoName()).append("】");
            if (repo.branch() != null && !repo.branch().isBlank()) {
                sb.append("  分支：").append(repo.branch());
            }
            sb.append("  共 ").append(repo.commits().size()).append(" 次提交\n");
            sb.append("----------------------------------------\n");

            if (repo.commits().isEmpty()) {
                sb.append("  本日无提交记录\n");
            } else {
                for (Commit c : repo.commits()) {
                    sb.append("  [").append(c.datetime(), 11, 16).append("] ")
                      .append(c.hash()).append("  ").append(c.message()).append("\n");
                    if (!c.body().isBlank()) {
                        for (String line : c.body().split("\n")) {
                            if (!line.isBlank()) sb.append("    ").append(line.trim()).append("\n");
                        }
                    }
                    if (config.includeFiles() && !c.changedFiles().isEmpty()) {
                        int limit = config.maxFiles() > 0 ? config.maxFiles() : 5;
                        List<String> shown = c.changedFiles().subList(0,
                                Math.min(limit, c.changedFiles().size()));
                        for (String f : shown) sb.append("    ").append(f).append("\n");
                        if (c.changedFiles().size() > limit)
                            sb.append("    ... 共 ").append(c.changedFiles().size()).append(" 个文件\n");
                    }
                }
            }
            sb.append("\n");
        }

        sb.append("========================================\n");
        sb.append("  今日合计：").append(totalCommits).append(" 次提交\n");
        sb.append("========================================\n");
        return sb.toString();
    }

    private static String buildMarkdown(List<RepoResult> results, DailyReportConfig config) {
        StringBuilder sb = new StringBuilder();
        String dateStr = config.date().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String author  = config.author().isBlank() ? "全体" : config.author();

        sb.append("# 开发日报 · ").append(dateStr).append("\n\n");
        sb.append("> **开发人员：** ").append(author).append("  \n");
        sb.append("> **生成时间：** ").append(java.time.LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n\n");
        sb.append("---\n\n");

        int totalCommits = 0;
        for (RepoResult repo : results) {
            if (repo.error() != null) {
                sb.append("## ").append(repo.repoName()).append("\n\n");
                sb.append("> ⚠️ 读取失败：").append(repo.error()).append("\n\n");
                continue;
            }
            totalCommits += repo.commits().size();
            sb.append("## ").append(repo.repoName());
            if (repo.branch() != null && !repo.branch().isBlank())
                sb.append("  `").append(repo.branch()).append("`");
            sb.append("\n\n");

            if (repo.commits().isEmpty()) {
                sb.append("*本日无提交记录*\n\n");
            } else {
                for (Commit c : repo.commits()) {
                    sb.append("- **[").append(c.datetime(), 11, 16).append("]**  `")
                      .append(c.hash()).append("`  ").append(c.message()).append("\n");
                    if (!c.body().isBlank()) {
                        for (String line : c.body().split("\n")) {
                            if (!line.isBlank()) sb.append("  > ").append(line.trim()).append("\n");
                        }
                    }
                    if (config.includeFiles() && !c.changedFiles().isEmpty()) {
                        int limit = config.maxFiles() > 0 ? config.maxFiles() : 5;
                        sb.append("\n  <details><summary>变更文件（")
                          .append(c.changedFiles().size()).append(" 个）</summary>\n\n");
                        c.changedFiles().stream().limit(limit)
                         .forEach(f -> sb.append("  - `").append(f).append("`\n"));
                        if (c.changedFiles().size() > limit)
                            sb.append("  - *...还有 ").append(c.changedFiles().size() - limit).append(" 个文件*\n");
                        sb.append("  </details>\n");
                    }
                }
            }
            sb.append("\n");
        }

        sb.append("---\n\n");
        sb.append("**今日合计：** ").append(totalCommits).append(" 次提交\n");
        return sb.toString();
    }

    private static String buildDingTalk(List<RepoResult> results, DailyReportConfig config) {
        StringBuilder sb = new StringBuilder();
        String dateStr = config.date().format(DateTimeFormatter.ofPattern("MM月dd日"));
        String author  = config.author().isBlank() ? "今日" : config.author();

        sb.append("【开发日报】").append(dateStr).append(" ").append(author).append("\n\n");

        int totalCommits = 0;
        for (RepoResult repo : results) {
            if (repo.error() != null) {
                sb.append("❌ ").append(repo.repoName()).append("：").append(repo.error()).append("\n\n");
                continue;
            }
            totalCommits += repo.commits().size();
            if (repo.commits().isEmpty()) continue;
            sb.append("📁 **").append(repo.repoName()).append("**（")
              .append(repo.commits().size()).append("次提交）\n");
            for (Commit c : repo.commits()) {
                sb.append("  ▸ [").append(c.datetime(), 11, 16).append("] ").append(c.message()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("────────────────\n");
        sb.append("📊 今日合计：").append(totalCommits).append(" 次提交");
        return sb.toString();
    }

    private static String buildFeiShu(List<RepoResult> results, DailyReportConfig config) {
        // 飞书 Markdown 格式（与标准 Markdown 略有差异）
        StringBuilder sb = new StringBuilder();
        String dateStr = config.date().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String author  = config.author().isBlank() ? "全体" : config.author();

        sb.append("**📅 开发日报 ").append(dateStr).append("**\n");
        sb.append("👤 开发人员：").append(author).append("\n\n");

        int totalCommits = 0;
        for (RepoResult repo : results) {
            if (repo.error() != null) {
                sb.append("❌ **").append(repo.repoName()).append("** — 读取失败\n\n");
                continue;
            }
            totalCommits += repo.commits().size();
            if (repo.commits().isEmpty()) continue;
            sb.append("**📂 ").append(repo.repoName()).append("**");
            if (repo.branch() != null && !repo.branch().isBlank())
                sb.append(" `").append(repo.branch()).append("`");
            sb.append("\n");
            for (Commit c : repo.commits()) {
                sb.append("- `").append(c.hash()).append("` [")
                  .append(c.datetime(), 11, 16).append("] ").append(c.message()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("---\n");
        sb.append("✅ **今日合计 ").append(totalCommits).append(" 次提交**");
        return sb.toString();
    }

    // ─── 多日汇总报告生成 ──────────────────────────────────────────────────

    /**
     * 生成多日汇总报告：每天一节，末尾附总统计
     */
    public static String generateRangeReport(
            java.util.LinkedHashMap<LocalDate, List<RepoResult>> dailyMap,
            RangeReportConfig config,
            ReportFormat format) {
        if (dailyMap.size() == 1) {
            // 只有一天，直接用原有单日生成
            Map.Entry<LocalDate, List<RepoResult>> entry = dailyMap.entrySet().iterator().next();
            return generateReport(entry.getValue(), config.forDate(entry.getKey()), format);
        }
        return switch (format) {
            case MARKDOWN  -> buildRangeMarkdown(dailyMap, config);
            case DINGTALK  -> buildRangeDingTalk(dailyMap, config);
            case FEISHU    -> buildRangeFeiShu(dailyMap, config);
            default        -> buildRangePlainText(dailyMap, config);
        };
    }

    private static String buildRangePlainText(
            java.util.LinkedHashMap<LocalDate, List<RepoResult>> dailyMap,
            RangeReportConfig config) {
        StringBuilder sb = new StringBuilder();
        String author = config.author().isBlank() ? "所有人" : config.author();
        String range  = config.startDate().format(DateTimeFormatter.ofPattern("MM月dd日"))
                      + " ~ " + config.endDate().format(DateTimeFormatter.ofPattern("MM月dd日"));

        sb.append("========================================\n");
        sb.append("  开发周报 / 区间日报 ").append(range).append("\n");
        sb.append("  开发人员：").append(author).append("\n");
        sb.append("========================================\n\n");

        int grandTotal = 0;
        for (Map.Entry<LocalDate, List<RepoResult>> entry : dailyMap.entrySet()) {
            LocalDate date = entry.getKey();
            List<RepoResult> results = entry.getValue();
            int dayTotal = results.stream().mapToInt(r -> r.commits() == null ? 0 : r.commits().size()).sum();
            grandTotal += dayTotal;

            sb.append("【").append(date.format(DateTimeFormatter.ofPattern("MM-dd"))).append(" 共 ")
              .append(dayTotal).append(" 次提交】\n");
            if (dayTotal == 0) {
                sb.append("  当日无提交\n\n");
                continue;
            }
            for (RepoResult repo : results) {
                if (repo.commits().isEmpty()) continue;
                sb.append("  ").append(repo.repoName()).append("：\n");
                for (Commit c : repo.commits()) {
                    sb.append("    [").append(c.datetime(), 11, 16).append("] ")
                      .append(c.hash()).append("  ").append(c.message()).append("\n");
                    if (config.includeFiles() && !c.changedFiles().isEmpty()) {
                        int limit = config.maxFiles() > 0 ? config.maxFiles() : 5;
                        c.changedFiles().stream().limit(limit)
                         .forEach(f -> sb.append("      ").append(f).append("\n"));
                    }
                }
            }
            sb.append("\n");
        }
        sb.append("========================================\n");
        sb.append("  区间合计：").append(grandTotal).append(" 次提交  /  ")
          .append(dailyMap.size()).append(" 天\n");
        sb.append("========================================\n");
        return sb.toString();
    }

    private static String buildRangeMarkdown(
            java.util.LinkedHashMap<LocalDate, List<RepoResult>> dailyMap,
            RangeReportConfig config) {
        StringBuilder sb = new StringBuilder();
        String author = config.author().isBlank() ? "全体" : config.author();
        String range  = config.startDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                      + " ~ " + config.endDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        sb.append("# 开发日报 · ").append(range).append("\n\n");
        sb.append("> **开发人员：** ").append(author).append("  \n");
        sb.append("> **生成时间：** ").append(java.time.LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n\n");
        sb.append("---\n\n");

        int grandTotal = 0;
        for (Map.Entry<LocalDate, List<RepoResult>> entry : dailyMap.entrySet()) {
            LocalDate date = entry.getKey();
            List<RepoResult> results = entry.getValue();
            int dayTotal = results.stream().mapToInt(r -> r.commits() == null ? 0 : r.commits().size()).sum();
            grandTotal += dayTotal;

            sb.append("## ").append(date.format(DateTimeFormatter.ofPattern("MM月dd日")))
              .append("（").append(date.getDayOfWeek().getDisplayName(
                      java.time.format.TextStyle.FULL, java.util.Locale.CHINESE)).append("）");
            if (dayTotal == 0) { sb.append("  *无提交*\n\n"); continue; }
            sb.append("  — ").append(dayTotal).append(" 次提交\n\n");

            for (RepoResult repo : results) {
                if (repo.error() != null) {
                    sb.append("> ⚠️ ").append(repo.repoName()).append("：").append(repo.error()).append("\n\n");
                    continue;
                }
                if (repo.commits().isEmpty()) continue;
                sb.append("**").append(repo.repoName()).append("**\n\n");
                for (Commit c : repo.commits()) {
                    sb.append("- **[").append(c.datetime(), 11, 16).append("]**  `")
                      .append(c.hash()).append("`  ").append(c.message()).append("\n");
                    if (!c.body().isBlank()) {
                        for (String line : c.body().split("\n"))
                            if (!line.isBlank()) sb.append("  > ").append(line.trim()).append("\n");
                    }
                    if (config.includeFiles() && !c.changedFiles().isEmpty()) {
                        int limit = config.maxFiles() > 0 ? config.maxFiles() : 5;
                        sb.append("\n  <details><summary>变更文件（")
                          .append(c.changedFiles().size()).append(" 个）</summary>\n\n");
                        c.changedFiles().stream().limit(limit)
                         .forEach(f -> sb.append("  - `").append(f).append("`\n"));
                        if (c.changedFiles().size() > limit)
                            sb.append("  - *...还有 ").append(c.changedFiles().size() - limit).append(" 个*\n");
                        sb.append("  </details>\n");
                    }
                }
                sb.append("\n");
            }
        }
        sb.append("---\n\n");
        sb.append("**区间合计：** ").append(grandTotal).append(" 次提交 / ")
          .append(dailyMap.size()).append(" 天\n");
        return sb.toString();
    }

    private static String buildRangeDingTalk(
            java.util.LinkedHashMap<LocalDate, List<RepoResult>> dailyMap,
            RangeReportConfig config) {
        StringBuilder sb = new StringBuilder();
        String author = config.author().isBlank() ? "全体" : config.author();
        String range  = config.startDate().format(DateTimeFormatter.ofPattern("MM.dd"))
                      + "-" + config.endDate().format(DateTimeFormatter.ofPattern("MM.dd"));

        sb.append("【开发日报】").append(range).append(" ").append(author).append("\n\n");
        int grandTotal = 0;
        for (Map.Entry<LocalDate, List<RepoResult>> entry : dailyMap.entrySet()) {
            LocalDate date = entry.getKey();
            List<RepoResult> results = entry.getValue();
            int dayTotal = results.stream().mapToInt(r -> r.commits() == null ? 0 : r.commits().size()).sum();
            grandTotal += dayTotal;
            if (dayTotal == 0) continue;
            sb.append("📅 **").append(date.format(DateTimeFormatter.ofPattern("MM-dd")))
              .append("**（").append(dayTotal).append("次）\n");
            for (RepoResult repo : results) {
                if (repo.commits().isEmpty()) continue;
                for (Commit c : repo.commits()) {
                    sb.append("  ▸ [").append(repo.repoName()).append("] ")
                      .append(c.message()).append("\n");
                }
            }
            sb.append("\n");
        }
        sb.append("────────────────\n");
        sb.append("📊 合计：").append(grandTotal).append(" 次提交 / ").append(dailyMap.size()).append(" 天");
        return sb.toString();
    }

    private static String buildRangeFeiShu(
            java.util.LinkedHashMap<LocalDate, List<RepoResult>> dailyMap,
            RangeReportConfig config) {
        StringBuilder sb = new StringBuilder();
        String author = config.author().isBlank() ? "全体" : config.author();
        String range  = config.startDate().format(DateTimeFormatter.ofPattern("MM-dd"))
                      + " 至 " + config.endDate().format(DateTimeFormatter.ofPattern("MM-dd"));
        sb.append("**📅 开发日报 ").append(range).append("**\n");
        sb.append("👤 ").append(author).append("\n\n");
        int grandTotal = 0;
        for (Map.Entry<LocalDate, List<RepoResult>> entry : dailyMap.entrySet()) {
            LocalDate date = entry.getKey();
            List<RepoResult> results = entry.getValue();
            int dayTotal = results.stream().mapToInt(r -> r.commits() == null ? 0 : r.commits().size()).sum();
            grandTotal += dayTotal;
            if (dayTotal == 0) continue;
            sb.append("**").append(date.format(DateTimeFormatter.ofPattern("MM月dd日"))).append("**\n");
            for (RepoResult repo : results) {
                if (repo.commits().isEmpty()) continue;
                sb.append("📂 ").append(repo.repoName()).append("\n");
                for (Commit c : repo.commits()) {
                    sb.append("- `").append(c.hash()).append("` ")
                      .append(c.message()).append("\n");
                }
            }
            sb.append("\n");
        }
        sb.append("---\n");
        sb.append("✅ **合计 ").append(grandTotal).append(" 次提交 / ").append(dailyMap.size()).append(" 天**");
        return sb.toString();
    }

    /**
     * 从多日结果中提取按天分类的工作摘要
     */
    public static String extractRangeWorkSummary(java.util.LinkedHashMap<LocalDate, List<RepoResult>> dailyMap) {
        StringBuilder sb = new StringBuilder();
        sb.append("区间工作内容摘要：\n\n");
        for (Map.Entry<LocalDate, List<RepoResult>> entry : dailyMap.entrySet()) {
            LocalDate date = entry.getKey();
            List<RepoResult> results = entry.getValue();
            int total = results.stream().mapToInt(r -> r.commits() == null ? 0 : r.commits().size()).sum();
            if (total == 0) continue;
            sb.append("【").append(date.format(DateTimeFormatter.ofPattern("MM-dd")))
              .append("  共 ").append(total).append(" 次提交】\n");
            String daySummary = extractWorkSummary(results);
            // 缩进
            for (String line : daySummary.split("\n")) {
                if (!line.isBlank()) sb.append("  ").append(line).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    // ─── 工作内容智能提取 ─────────────────────────────────────────────────

    /**
     * 从提交记录中智能提取"今日工作内容"摘要
     * 将提交信息按前缀分类（feat/fix/refactor/docs/chore 等）
     */
    public static String extractWorkSummary(List<RepoResult> results) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        grouped.put("✨ 新功能", new ArrayList<>());
        grouped.put("🐛 Bug修复", new ArrayList<>());
        grouped.put("♻️ 重构优化", new ArrayList<>());
        grouped.put("📝 文档/注释", new ArrayList<>());
        grouped.put("🔧 配置/工程", new ArrayList<>());
        grouped.put("🧪 测试", new ArrayList<>());
        grouped.put("📦 其他", new ArrayList<>());

        for (RepoResult repo : results) {
            if (repo.commits() == null) continue;
            for (Commit c : repo.commits()) {
                String msg = c.message().trim();
                String key = classifyCommit(msg);
                // 去掉 conventional commits 前缀，保留描述
                String clean = msg.replaceAll("^(?:feat|fix|refactor|docs|chore|test|style|perf|build|ci|revert)(\\([^)]+\\))?[!:]\\s*", "");
                grouped.getOrDefault(key, grouped.get("📦 其他")).add(clean);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("今日工作内容：\n");
        for (Map.Entry<String, List<String>> e : grouped.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            sb.append(e.getKey()).append("\n");
            for (String item : e.getValue()) {
                sb.append("  - ").append(item).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private static String classifyCommit(String msg) {
        String lower = msg.toLowerCase();
        if (lower.startsWith("feat") || lower.startsWith("feature") || lower.startsWith("add"))
            return "✨ 新功能";
        if (lower.startsWith("fix") || lower.startsWith("bug") || lower.startsWith("hotfix"))
            return "🐛 Bug修复";
        if (lower.startsWith("refactor") || lower.startsWith("perf") || lower.startsWith("optimize") || lower.startsWith("improvement"))
            return "♻️ 重构优化";
        if (lower.startsWith("docs") || lower.startsWith("doc") || lower.startsWith("comment"))
            return "📝 文档/注释";
        if (lower.startsWith("chore") || lower.startsWith("build") || lower.startsWith("ci") || lower.startsWith("config"))
            return "🔧 配置/工程";
        if (lower.startsWith("test"))
            return "🧪 测试";
        if (lower.startsWith("style"))
            return "📝 文档/注释";
        return "📦 其他";
    }
}

