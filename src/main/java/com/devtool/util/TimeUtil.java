package com.devtool.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 时间工具类
 * 支持：时间戳转换、多时区显示、日期差计算、相对时间、常用时区列表
 */
public class TimeUtil {

    private TimeUtil() {}

    public static final DateTimeFormatter FMT_FULL    = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter FMT_MILLIS  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    public static final DateTimeFormatter FMT_DATE    = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter FMT_ISO     = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    // ─── 常用时区 ─────────────────────────────────────────────────────────

    /**
     * 开发常用时区列表（有序）
     * key = 显示名称，value = ZoneId 字符串
     */
    public static LinkedHashMap<String, String> commonZones() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("北京 (CST, UTC+8)",        "Asia/Shanghai");
        map.put("UTC (格林威治标准时间)",    "UTC");
        map.put("伦敦 (GMT/BST)",            "Europe/London");
        map.put("巴黎/柏林 (CET, UTC+1)",   "Europe/Paris");
        map.put("莫斯科 (MSK, UTC+3)",       "Europe/Moscow");
        map.put("迪拜 (GST, UTC+4)",         "Asia/Dubai");
        map.put("印度 (IST, UTC+5:30)",      "Asia/Kolkata");
        map.put("新加坡/香港 (SGT, UTC+8)", "Asia/Singapore");
        map.put("东京 (JST, UTC+9)",         "Asia/Tokyo");
        map.put("悉尼 (AEST, UTC+10)",       "Australia/Sydney");
        map.put("纽约 (EST/EDT)",            "America/New_York");
        map.put("芝加哥 (CST/CDT)",         "America/Chicago");
        map.put("洛杉矶 (PST/PDT)",         "America/Los_Angeles");
        map.put("圣保罗 (BRT, UTC-3)",       "America/Sao_Paulo");
        return map;
    }

    // ─── 时间戳 ↔ 日期 ───────────────────────────────────────────────────

    /**
     * 时间戳 → 日期字符串（自动识别秒/毫秒）
     */
    public static String timestampToDatetime(long timestamp, String zoneId) {
        ZoneId zone = parseZone(zoneId);
        Instant instant;
        boolean isMillis = timestamp > 9_999_999_999L;  // > 10位视为毫秒
        if (isMillis) {
            instant = Instant.ofEpochMilli(timestamp);
        } else {
            instant = Instant.ofEpochSecond(timestamp);
        }
        ZonedDateTime zdt = instant.atZone(zone);
        return isMillis
                ? zdt.format(FMT_MILLIS)
                : zdt.format(FMT_FULL);
    }

    /**
     * 日期字符串 → 时间戳（秒 + 毫秒均返回）
     */
    public static TimestampResult datetimeToTimestamp(String dateStr, String zoneId) throws Exception {
        ZoneId zone = parseZone(zoneId);
        ZonedDateTime zdt = parseDateTime(dateStr, zone);
        long millis = zdt.toInstant().toEpochMilli();
        long seconds = millis / 1000;
        return new TimestampResult(seconds, millis, zdt);
    }

    public record TimestampResult(long seconds, long millis, ZonedDateTime zdt) {}

    // ─── 当前时间 ─────────────────────────────────────────────────────────

    /** 返回当前时刻在所有常用时区的时间（用于多时区面板显示） */
    public static Map<String, String> nowAllZones() {
        Instant now = Instant.now();
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        commonZones().forEach((name, zoneStr) -> {
            ZonedDateTime zdt = now.atZone(ZoneId.of(zoneStr));
            result.put(name, zdt.format(FMT_FULL) + "  " + zdt.getOffset());
        });
        return result;
    }

    /** 当前时间戳（秒 + 毫秒） */
    public static TimestampResult nowTimestamp() {
        Instant now = Instant.now();
        long millis = now.toEpochMilli();
        ZonedDateTime zdt = now.atZone(ZoneId.of("Asia/Shanghai"));
        return new TimestampResult(millis / 1000, millis, zdt);
    }

    // ─── 时区转换 ─────────────────────────────────────────────────────────

    /**
     * 将某个时区的时间字符串转换到目标时区
     */
    public static String convertZone(String dateStr, String fromZoneId, String toZoneId) throws Exception {
        ZoneId fromZone = parseZone(fromZoneId);
        ZoneId toZone   = parseZone(toZoneId);
        ZonedDateTime from = parseDateTime(dateStr, fromZone);
        ZonedDateTime to   = from.withZoneSameInstant(toZone);
        return to.format(FMT_FULL) + "  (" + to.getOffset() + ")";
    }

    // ─── 日期差计算 ──────────────────────────────────────────────────────

    public record DiffResult(
        long totalDays,
        long workDays,
        long years, long months, long days,
        long hours, long minutes, long seconds
    ) {}

    /**
     * 计算两个日期之间的差值
     */
    public static DiffResult dateDiff(String dateA, String dateB) throws Exception {
        LocalDateTime a = parseLocalDateTime(dateA);
        LocalDateTime b = parseLocalDateTime(dateB);
        if (a.isAfter(b)) { LocalDateTime tmp = a; a = b; b = tmp; }

        long totalDays = ChronoUnit.DAYS.between(a.toLocalDate(), b.toLocalDate());
        long workDays  = countWorkDays(a.toLocalDate(), b.toLocalDate());

        Period period = Period.between(a.toLocalDate(), b.toLocalDate());
        long hours    = ChronoUnit.HOURS.between(a, b) % 24;
        long minutes  = ChronoUnit.MINUTES.between(a, b) % 60;
        long secs     = ChronoUnit.SECONDS.between(a, b) % 60;

        return new DiffResult(totalDays, workDays,
                period.getYears(), period.getMonths(), period.getDays(),
                hours, minutes, secs);
    }

    /** 计算工作日（排除周六周日，不含法定节假日） */
    private static long countWorkDays(LocalDate from, LocalDate to) {
        long count = 0;
        LocalDate cursor = from;
        while (cursor.isBefore(to)) {
            DayOfWeek dow = cursor.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) count++;
            cursor = cursor.plusDays(1);
        }
        return count;
    }

    // ─── 相对时间 ─────────────────────────────────────────────────────────

    /**
     * 将时间戳（秒或毫秒）转为相对时间描述
     * 例如："3分钟前" / "2小时后" / "昨天" / "3天前"
     */
    public static String relativeTime(long timestamp) {
        boolean isMillis = timestamp > 9_999_999_999L;
        Instant target = isMillis ? Instant.ofEpochMilli(timestamp) : Instant.ofEpochSecond(timestamp);
        long diffSeconds = ChronoUnit.SECONDS.between(target, Instant.now());
        boolean isFuture = diffSeconds < 0;
        long abs = Math.abs(diffSeconds);

        String desc;
        if (abs < 5)           desc = "刚刚";
        else if (abs < 60)     desc = abs + " 秒" + (isFuture ? "后" : "前");
        else if (abs < 3600)   desc = abs / 60 + " 分钟" + (isFuture ? "后" : "前");
        else if (abs < 86400)  desc = abs / 3600 + " 小时" + (isFuture ? "后" : "前");
        else if (abs < 86400 * 30) desc = abs / 86400 + " 天" + (isFuture ? "后" : "前");
        else if (abs < 86400 * 365) desc = abs / (86400 * 30) + " 个月" + (isFuture ? "后" : "前");
        else desc = abs / (86400 * 365) + " 年" + (isFuture ? "后" : "前");

        return desc;
    }

    /**
     * 将日期字符串转为相对时间描述
     */
    public static String relativeTimeFromStr(String dateStr) throws Exception {
        LocalDateTime ldt = parseLocalDateTime(dateStr);
        ZonedDateTime zdt = ldt.atZone(ZoneId.of("Asia/Shanghai"));
        return relativeTime(zdt.toInstant().toEpochMilli());
    }

    // ─── Unix 时间戳关键日期 ──────────────────────────────────────────────

    /** 返回常见的 Unix 时间戳里程碑（方便开发调试） */
    public static Map<String, Long> milestones() {
        LinkedHashMap<String, Long> m = new LinkedHashMap<>();
        ZoneId beijing = ZoneId.of("Asia/Shanghai");
        m.put("当前时间戳(秒)",  Instant.now().getEpochSecond());
        m.put("当前时间戳(毫秒)", Instant.now().toEpochMilli());
        m.put("今天零点(北京)",  LocalDate.now().atStartOfDay(beijing).toEpochSecond());
        m.put("本周一零点(北京)", LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay(beijing).toEpochSecond());
        m.put("本月1日零点(北京)", LocalDate.now().withDayOfMonth(1).atStartOfDay(beijing).toEpochSecond());
        m.put("今年元旦零点(北京)", LocalDate.now().withDayOfYear(1).atStartOfDay(beijing).toEpochSecond());
        m.put("2000-01-01 00:00:00 UTC", 946684800L);
        m.put("int最大值(2038问题)",     2147483647L);
        return m;
    }

    // ─── 内部工具 ─────────────────────────────────────────────────────────

    private static ZoneId parseZone(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) return ZoneId.of("Asia/Shanghai");
        try { return ZoneId.of(zoneId); } catch (Exception e) { return ZoneId.of("Asia/Shanghai"); }
    }

    /** 多格式尝试解析日期字符串 */
    public static LocalDateTime parseLocalDateTime(String s) throws Exception {
        s = s.trim();
        String[] formats = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd",
            "dd-MM-yyyy HH:mm:ss",
            "MM/dd/yyyy HH:mm:ss",
            "yyyyMMddHHmmss",
        };
        for (String fmt : formats) {
            try {
                DateTimeFormatter f = DateTimeFormatter.ofPattern(fmt);
                if (fmt.contains("HH")) return LocalDateTime.parse(s, f);
                else return LocalDate.parse(s, f).atStartOfDay();
            } catch (DateTimeParseException ignored) {}
        }
        throw new Exception("无法解析日期格式：" + s + "\n支持格式：yyyy-MM-dd HH:mm:ss / yyyy-MM-dd / yyyyMMddHHmmss 等");
    }

    private static ZonedDateTime parseDateTime(String s, ZoneId zone) throws Exception {
        return parseLocalDateTime(s).atZone(zone);
    }
}

