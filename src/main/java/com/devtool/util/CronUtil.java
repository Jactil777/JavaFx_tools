package com.devtool.util;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cron 表达式工具类
 * 支持 Spring/Quartz 6 位格式：秒 分 时 日 月 周
 */
public class CronUtil {

    private CronUtil() {}

    public record CronPart(String second, String minute, String hour,
                           String day, String month, String week) {
        public String toExpression() {
            return second + " " + minute + " " + hour + " " + day + " " + month + " " + week;
        }
    }

    /** 校验 Cron 表达式基本格式 */
    public static String validate(String expr) {
        if (expr == null || expr.isBlank()) return "表达式不能为空";
        String[] parts = expr.trim().split("\\s+");
        if (parts.length != 6) return "格式错误：需要 6 位（秒 分 时 日 月 周）";
        return null; // null = 合法
    }

    /**
     * 计算从 baseTime 起，下 n 次触发时间
     */
    public static List<String> nextTriggers(String expr, int count) {
        String err = validate(expr);
        if (err != null) return List.of("表达式错误：" + err);

        String[] parts = expr.trim().split("\\s+");
        String second = parts[0];
        String minute = parts[1];
        String hour   = parts[2];
        String day    = parts[3];
        String month  = parts[4];
        String week   = parts[5];

        LocalDateTime now = LocalDateTime.now().withNano(0).plusSeconds(1);
        List<String> results = new ArrayList<>();

        // 简单穷举（扫描 2 年内）
        LocalDateTime cursor = now;
        LocalDateTime limit  = now.plusYears(2);
        int found = 0;

        while (cursor.isBefore(limit) && found < count) {
            if (matches(cursor, second, minute, hour, day, month, week)) {
                results.add(String.format("%04d-%02d-%02d %02d:%02d:%02d",
                    cursor.getYear(), cursor.getMonthValue(), cursor.getDayOfMonth(),
                    cursor.getHour(), cursor.getMinute(), cursor.getSecond()));
                found++;
            }
            cursor = cursor.plusSeconds(1);
        }

        if (results.isEmpty()) results.add("在未来 2 年内无触发时间");
        return results;
    }

    private static boolean matches(LocalDateTime t, String sec, String min,
                                    String hr, String day, String mon, String week) {
        return matchField(t.getSecond(), sec, 0, 59)
            && matchField(t.getMinute(), min, 0, 59)
            && matchField(t.getHour(),   hr,  0, 23)
            && matchMonth(t.getMonthValue(), mon)
            && matchDayOrWeek(t, day, week);
    }

    private static boolean matchDayOrWeek(LocalDateTime t, String day, String week) {
        boolean dayWild  = day.equals("*")  || day.equals("?");
        boolean weekWild = week.equals("*") || week.equals("?");
        if (dayWild && weekWild) return true;
        if (!dayWild && !weekWild) {
            return matchField(t.getDayOfMonth(), day, 1, 31)
                || matchWeekField(t.getDayOfWeek().getValue() % 7, week);
        }
        if (!dayWild) return matchField(t.getDayOfMonth(), day, 1, 31);
        // dow: 1=SUN,2=MON,...7=SAT in Quartz; here use 0=SUN
        return matchWeekField(t.getDayOfWeek().getValue() % 7, week);
    }

    /** 匹配月份（支持英文缩写 JAN-DEC） */
    private static boolean matchMonth(int val, String expr) {
        String e = expr.toUpperCase()
            .replace("JAN","1").replace("FEB","2").replace("MAR","3")
            .replace("APR","4").replace("MAY","5").replace("JUN","6")
            .replace("JUL","7").replace("AUG","8").replace("SEP","9")
            .replace("OCT","10").replace("NOV","11").replace("DEC","12");
        return matchField(val, e, 1, 12);
    }

    /** 匹配星期（支持 SUN-SAT，0=SUN） */
    private static boolean matchWeekField(int val, String expr) {
        String e = expr.toUpperCase()
            .replace("SUN","0").replace("MON","1").replace("TUE","2")
            .replace("WED","3").replace("THU","4").replace("FRI","5").replace("SAT","6");
        return matchField(val, e, 0, 6);
    }

    /** 通用字段匹配：支持 * / , - */
    static boolean matchField(int val, String expr, int min, int max) {
        if (expr.equals("*") || expr.equals("?")) return true;

        // 处理逗号分隔
        if (expr.contains(",")) {
            for (String part : expr.split(",")) {
                if (matchField(val, part.trim(), min, max)) return true;
            }
            return false;
        }

        // 处理 /（步进）
        if (expr.contains("/")) {
            String[] p = expr.split("/", 2);
            int start = p[0].equals("*") ? min : Integer.parseInt(p[0]);
            int step  = Integer.parseInt(p[1]);
            for (int i = start; i <= max; i += step) {
                if (i == val) return true;
            }
            return false;
        }

        // 处理 -（范围）
        if (expr.contains("-")) {
            String[] p = expr.split("-", 2);
            int from = Integer.parseInt(p[0]);
            int to   = Integer.parseInt(p[1]);
            return val >= from && val <= to;
        }

        // 精确值
        try {
            return Integer.parseInt(expr) == val;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 常用 Cron 模板 */
    public record Template(String name, String expr, String desc) {}

    public static List<Template> templates() {
        return List.of(
            new Template("每秒",         "* * * * * *",   "每秒执行一次"),
            new Template("每分钟",       "0 * * * * *",   "每分钟第 0 秒"),
            new Template("每5分钟",      "0 0/5 * * * *", "每 5 分钟执行"),
            new Template("每10分钟",     "0 0/10 * * * *","每 10 分钟执行"),
            new Template("每30分钟",     "0 0/30 * * * *","每 30 分钟执行"),
            new Template("每小时",       "0 0 * * * *",   "每小时整点"),
            new Template("每天零点",     "0 0 0 * * *",   "每天 00:00:00"),
            new Template("每天早8点",    "0 0 8 * * *",   "每天 08:00:00"),
            new Template("每天中午",     "0 0 12 * * *",  "每天 12:00:00"),
            new Template("工作日早9点",  "0 0 9 * * 1-5", "周一到周五 09:00:00"),
            new Template("每周一零点",   "0 0 0 * * 1",   "每周一 00:00:00"),
            new Template("每月1日零点",  "0 0 0 1 * *",   "每月 1 号 00:00:00"),
            new Template("每年元旦",     "0 0 0 1 1 *",   "每年 1 月 1 日 00:00:00"),
            new Template("每季度首日",   "0 0 0 1 1,4,7,10 *", "每季度首日零点")
        );
    }
}

