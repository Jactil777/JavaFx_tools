package com.devtool.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 金价数据工具类
 * 数据来源：新浪财经免费API（无需注册）
 * - 现货黄金/伦敦金: hf_XAU
 * - COMEX纽约黄金: hf_GC
 * - 上海黄金期货: nf_AU0
 */
public class GoldPriceUtil {

    private static final Logger log = LoggerFactory.getLogger(GoldPriceUtil.class);

    private static final String SINA_API = "https://hq.sinajs.cn/list=";
    private static final int TIMEOUT = 8000;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** 金价品种 */
    public enum GoldSymbol {
        XAU("hf_XAU", "现货黄金", "伦敦金", "USD/盎司"),
        GC("hf_GC", "COMEX黄金", "纽约黄金", "USD/盎司"),
        AU0("nf_AU0", "沪金连续", "上海黄金", "元/克");

        private final String code;
        private final String shortName;
        private final String fullName;
        private final String unit;

        GoldSymbol(String code, String shortName, String fullName, String unit) {
            this.code = code;
            this.shortName = shortName;
            this.fullName = fullName;
            this.unit = unit;
        }

        public String getCode() { return code; }
        public String getShortName() { return shortName; }
        public String getFullName() { return fullName; }
        public String getUnit() { return unit; }
    }

    /** 金价数据模型 */
    public static class GoldPrice {
        private final GoldSymbol symbol;
        private final double currentPrice;
        private final double change;
        private final double changePercent;
        private final double open;
        private final double high;
        private final double low;
        private final double prevClose;
        private final String updateTime;
        private final boolean success;

        public GoldPrice(GoldSymbol symbol, double currentPrice, double change, double changePercent,
                         double open, double high, double low, double prevClose,
                         String updateTime, boolean success) {
            this.symbol = symbol;
            this.currentPrice = currentPrice;
            this.change = change;
            this.changePercent = changePercent;
            this.open = open;
            this.high = high;
            this.low = low;
            this.prevClose = prevClose;
            this.updateTime = updateTime;
            this.success = success;
        }

        public GoldSymbol getSymbol() { return symbol; }
        public double getCurrentPrice() { return currentPrice; }
        public double getChange() { return change; }
        public double getChangePercent() { return changePercent; }
        public double getOpen() { return open; }
        public double getHigh() { return high; }
        public double getLow() { return low; }
        public double getPrevClose() { return prevClose; }
        public String getUpdateTime() { return updateTime; }
        public boolean isSuccess() { return success; }

        /** 格式化当前价格 */
        public String formatPrice() {
            if (symbol == GoldSymbol.AU0) {
                return String.format("%.2f", currentPrice);
            }
            return String.format("%.2f", currentPrice);
        }

        /** 格式化涨跌幅（带正负号） */
        public String formatChange() {
            String sign = change >= 0 ? "+" : "";
            if (symbol == GoldSymbol.AU0) {
                return sign + String.format("%.2f", change);
            }
            return sign + String.format("%.2f", change);
        }

        /** 格式化涨跌百分比（带正负号和百分号） */
        public String formatChangePercent() {
            String sign = changePercent >= 0 ? "+" : "";
            return sign + String.format("%.2f", changePercent) + "%";
        }

        /** 涨跌方向：1=涨, -1=跌, 0=平 */
        public int getDirection() {
            if (change > 0) return 1;
            if (change < 0) return -1;
            return 0;
        }
    }

    /**
     * 获取单个品种的金价数据
     */
    public static GoldPrice fetchPrice(GoldSymbol symbol) {
        try {
            String urlStr = SINA_API + symbol.getCode();
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Referer", "https://finance.sina.com.cn");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);

            int code = conn.getResponseCode();
            if (code != 200) {
                log.warn("新浪财经API返回状态码: {} for {}", code, symbol.getCode());
                return createEmpty(symbol);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), Charset.forName("GBK")))) {
                String line = reader.readLine();
                if (line == null || line.isEmpty()) {
                    return createEmpty(symbol);
                }
                return parseResponse(symbol, line);
            }
        } catch (Exception e) {
            log.error("获取金价失败: {}", symbol.getCode(), e);
            return createEmpty(symbol);
        }
    }

    /**
     * 获取当日分时历史数据（分钟级）
     * 使用新浪财经分时数据API
     * @return List<double[]> [时间戳毫秒, 价格]
     */
    public static List<double[]> fetchIntradayHistory(GoldSymbol symbol) {
        List<double[]> history = new ArrayList<>();
        try {
            // 新浪分时数据API: https://quotes.sina.cn/cn/api/jsonp_v2.php/var%20hq_str_{symbol}=/CN_Service.getStockInfo?symbol={symbol}&num=240
            // 对于国际期货，使用不同的API
            String apiUrl;
            if (symbol == GoldSymbol.AU0) {
                // 国内期货: nf_AU0
                apiUrl = "https://quotes.sina.cn/cn/api/jsonp_v2.php/var%20hq_str_nf_AU0=/CN_Service.getStockInfo?symbol=nf_AU0&num=240";
            } else {
                // 国际期货: hf_XAU, hf_GC - 使用不同的接口
                apiUrl = "https://stock.finance.sina.com.cn/futures/api/jsonp.php/var%20" + 
                         symbol.getCode() + "_data=/GlobalFuturesService.getGlobalFuturesDailyKLine?symbol=" + 
                         symbol.getCode().substring(3) + "&type=1&_=1";
            }

            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Referer", "https://finance.sina.com.cn");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);

            int code = conn.getResponseCode();
            if (code != 200) {
                log.warn("分时数据API返回状态码: {}", code);
                return history;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                String response = sb.toString();
                
                // 解析JSONP响应
                return parseIntradayData(symbol, response);
            }
        } catch (Exception e) {
            log.error("获取分时数据失败: {}", symbol.getCode(), e);
        }
        return history;
    }

    /**
     * 批量获取所有金价数据
     */
    public static List<GoldPrice> fetchAllPrices() {
        List<GoldPrice> results = new ArrayList<>();
        for (GoldSymbol symbol : GoldSymbol.values()) {
            results.add(fetchPrice(symbol));
        }
        return results;
    }

    /**
     * 解析新浪API响应
     * 国际期货格式: var hq_str_hf_XAU="现货黄金,伦敦金,4216.83,12.53,0.30%,...";
     * 国内期货格式: var hq_str_nf_AU0="沪金连续,au0,570.00,...";
     */
    private static GoldPrice parseResponse(GoldSymbol symbol, String rawLine) {
        try {
            // 提取引号内的数据
            int start = rawLine.indexOf('"');
            int end = rawLine.lastIndexOf('"');
            if (start < 0 || end <= start) {
                log.warn("无法提取数据: {}", rawLine);
                return createEmpty(symbol);
            }
            String data = rawLine.substring(start + 1, end);
            String[] fields = data.split(",");
            
            // 调试日志：打印原始字段
            log.info("{} API返回 {} 个字段: {}", symbol.getCode(), fields.length, data);

            if (symbol == GoldSymbol.AU0) {
                return parseDomesticFuture(symbol, fields);
            } else {
                return parseInternationalFuture(symbol, fields);
            }
        } catch (Exception e) {
            log.error("解析金价数据失败: {}", symbol.getCode(), e);
            return createEmpty(symbol);
        }
    }

    /**
     * 解析国际期货数据（XAU, GC）
     * 实际格式: 当前价,昨收价,当前价,最高价,最低价,开盘价,时间,买价,卖价,...,日期,名称
     * 示例 hf_XAU: 4216.83,4210.58,4216.83,4217.51,4246.40,4170.14,04:55:00,4210.58,4216.94,0,0,0,2026-06-13,伦敦金
     */
    private static GoldPrice parseInternationalFuture(GoldSymbol symbol, String[] fields) {
        if (fields.length < 6) {
            log.warn("国际期货字段不足: {} 共{}个字段", symbol.getCode(), fields.length);
            return createEmpty(symbol);
        }

        // 根据实际API返回解析
        double currentPrice = parseDouble(fields[0]);   // 当前价
        double prevClose = parseDouble(fields[1]);      // 昨收价
        double high = parseDouble(fields[3]);           // 最高价
        double low = parseDouble(fields[4]);            // 最低价
        double open = parseDouble(fields[5]);           // 开盘价
        
        // 自己计算涨跌额和涨跌幅
        double change = currentPrice - prevClose;
        double changePercent = prevClose != 0 ? (change / prevClose * 100) : 0;

        // 提取时间：可能在 [6] 或 [12]
        String time = "";
        if (fields.length > 6 && fields[6].matches("\\d{2}:\\d{2}:\\d{2}")) {
            time = fields[6];
        }
        
        String date = "";
        for (int i = 6; i < fields.length; i++) {
            if (fields[i].matches("\\d{4}-\\d{2}-\\d{2}")) {
                date = fields[i];
                break;
            }
        }
        
        if (!date.isEmpty() && !time.isEmpty()) {
            time = date + " " + time;
        } else if (!date.isEmpty()) {
            time = date;
        } else if (time.isEmpty()) {
            time = java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

        return new GoldPrice(symbol, currentPrice, change, changePercent,
                open, high, low, prevClose, time, true);
    }

    /**
     * 解析国内期货数据（AU0）
     * 实际格式: 名称,代码,当前价,最高价,最低价,?,买价,卖价,结算价,?,昨收价,...,日期
     * 示例 nf_AU0: 黄金连续,023000,912.90,923.60,910.80,0.00,920.88,921.66,921.66,0.00,900.90,...,2026-06-13
     */
    private static GoldPrice parseDomesticFuture(GoldSymbol symbol, String[] fields) {
        if (fields.length < 5) {
            log.warn("国内期货字段不足: {} 共{}个字段", symbol.getCode(), fields.length);
            return createEmpty(symbol);
        }

        // 根据实际API返回解析
        double currentPrice = parseDouble(fields[2]);   // 当前价
        double high = parseDouble(fields[3]);           // 最高价
        double low = parseDouble(fields[4]);            // 最低价
        
        // 昨收价可能在 [10]
        double prevClose = 0;
        if (fields.length > 10) {
            prevClose = parseDouble(fields[10]);
        }
        
        // 开盘价可能在 [5] 或其他位置，如果没有则用昨收
        double open = parseDouble(fields[5]);
        if (open == 0) open = prevClose;
        
        // 自己计算涨跌额和涨跌幅
        double change = currentPrice - prevClose;
        double changePercent = prevClose != 0 ? (change / prevClose * 100) : 0;

        // 提取日期
        String date = "";
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].matches("\\d{4}-\\d{2}-\\d{2}")) {
                date = fields[i];
                break;
            }
        }
        
        String time = date.isEmpty() 
            ? java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            : date + " 15:00:00";  // 国内期货收盘时间

        return new GoldPrice(symbol, currentPrice, change, changePercent,
                open, high, low, prevClose, time, true);
    }

    /**
     * 解析分时数据API响应
     * 国际期货返回格式: var hf_XAU_data=[{"d":"2026-06-13","t":"1718251200","c":"4216.83"},...];
     * 国内期货返回格式: var hq_str_nf_AU0=[{time:"09:00",price:"912.90"},...];
     */
    private static List<double[]> parseIntradayData(GoldSymbol symbol, String response) {
        List<double[]> history = new ArrayList<>();
        try {
            // 提取JSON数组部分
            int start = response.indexOf('[');
            int end = response.lastIndexOf(']');
            if (start < 0 || end <= start) {
                log.warn("分时数据格式错误: {}", response.substring(0, Math.min(100, response.length())));
                return history;
            }
            
            String jsonArray = response.substring(start, end + 1);
            JsonNode arrayNode = objectMapper.readTree(jsonArray);
            
            if (!arrayNode.isArray()) {
                return history;
            }

            java.time.LocalDate today = java.time.LocalDate.now();
            
            for (JsonNode item : arrayNode) {
                double price = 0;
                long timestamp = 0;
                
                if (symbol == GoldSymbol.AU0) {
                    // 国内期货格式: {time:"09:00",price:"912.90"}
                    String timeStr = item.has("time") ? item.get("time").asText() : "";
                    price = item.has("price") ? item.get("price").asDouble() : 0;
                    
                    if (!timeStr.isEmpty() && price > 0) {
                        try {
                            String[] parts = timeStr.split(":");
                            int hour = Integer.parseInt(parts[0]);
                            int minute = Integer.parseInt(parts[1]);
                            java.time.LocalDateTime ldt = today.atTime(hour, minute);
                            timestamp = ldt.atZone(java.time.ZoneId.systemDefault())
                                    .toInstant().toEpochMilli();
                        } catch (Exception e) {
                            continue;
                        }
                    }
                } else {
                    // 国际期货格式: {d:"2026-06-13",t:"1718251200",c:"4216.83"}
                    price = item.has("c") ? item.get("c").asDouble() : 0;
                    
                    if (item.has("t")) {
                        String tStr = item.get("t").asText();
                        try {
                            long ts = Long.parseLong(tStr);
                            if (ts < 10000000000L) {
                                ts = ts * 1000; // 秒转毫秒
                            }
                            timestamp = ts;
                        } catch (NumberFormatException e) {
                            continue;
                        }
                    }
                }
                
                if (price > 0 && timestamp > 0) {
                    history.add(new double[]{timestamp, price});
                }
            }
            
            log.info("{} 分时数据: {} 条", symbol.getCode(), history.size());
        } catch (Exception e) {
            log.error("解析分时数据失败: {}", symbol.getCode(), e);
        }
        return history;
    }

    private static double parseDouble(String s) {
        if (s == null || s.isBlank()) return 0;
        try {
            return Double.parseDouble(s.trim().replace("%", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static GoldPrice createEmpty(GoldSymbol symbol) {
        return new GoldPrice(symbol, 0, 0, 0, 0, 0, 0, 0,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), false);
    }
}
