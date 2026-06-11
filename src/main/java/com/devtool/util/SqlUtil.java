package com.devtool.util;

import java.util.*;
import java.util.regex.*;

/**
 * SQL 工具类
 * 支持：格式化、压缩、关键字大写/小写、语法检测、提取表名/字段名、
 *       参数占位符替换、去注释、列别名对齐、INSERT 模板生成
 */
public class SqlUtil {

    private SqlUtil() {}

    // ─── SQL 关键字集合 ────────────────────────────────────────────────────

    private static final Set<String> KEYWORDS = new LinkedHashSet<>(Arrays.asList(
        "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "EXISTS",
        "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
        "CREATE", "TABLE", "INDEX", "VIEW", "DATABASE", "SCHEMA",
        "DROP", "ALTER", "TRUNCATE", "RENAME", "ADD", "COLUMN", "MODIFY",
        "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "FULL", "CROSS",
        "ON", "AS", "USING",
        "GROUP", "BY", "ORDER", "HAVING", "LIMIT", "OFFSET", "TOP",
        "DISTINCT", "UNION", "ALL", "INTERSECT", "EXCEPT",
        "CASE", "WHEN", "THEN", "ELSE", "END",
        "IS", "NULL", "BETWEEN", "LIKE", "ILIKE",
        "BEGIN", "COMMIT", "ROLLBACK", "TRANSACTION", "SAVEPOINT",
        "GRANT", "REVOKE", "PRIMARY", "KEY", "FOREIGN", "REFERENCES",
        "UNIQUE", "CHECK", "DEFAULT", "AUTO_INCREMENT", "AUTOINCREMENT",
        "CONSTRAINT", "INDEX", "WITH", "NOLOCK", "ASC", "DESC",
        "IF", "ELSE", "WHILE", "PROCEDURE", "FUNCTION", "TRIGGER",
        "RETURNS", "RETURN", "DECLARE", "CURSOR", "FETCH", "OPEN", "CLOSE",
        "INT", "INTEGER", "BIGINT", "SMALLINT", "TINYINT",
        "VARCHAR", "CHAR", "NVARCHAR", "TEXT", "CLOB",
        "FLOAT", "DOUBLE", "DECIMAL", "NUMERIC", "REAL",
        "DATE", "TIME", "DATETIME", "TIMESTAMP", "BOOLEAN", "BOOL",
        "BLOB", "BINARY", "VARBINARY",
        "COUNT", "SUM", "AVG", "MIN", "MAX",
        "COALESCE", "NULLIF", "IFNULL", "NVL", "ISNULL",
        "CONCAT", "SUBSTRING", "SUBSTR", "TRIM", "LTRIM", "RTRIM",
        "UPPER", "LOWER", "LEN", "LENGTH", "REPLACE", "CAST", "CONVERT",
        "DATEADD", "DATEDIFF", "GETDATE", "NOW", "SYSDATE",
        "ROW_NUMBER", "RANK", "DENSE_RANK", "OVER", "PARTITION", "WITHIN", "GROUPS",
        "EXPLAIN", "ANALYZE", "SHOW", "DESCRIBE", "USE", "EXEC", "EXECUTE"
    ));

    // 子句级关键字（格式化时独占一行）
    private static final List<String> CLAUSE_KEYWORDS = Arrays.asList(
        "SELECT", "FROM", "WHERE", "JOIN", "LEFT JOIN", "RIGHT JOIN", "INNER JOIN",
        "OUTER JOIN", "FULL JOIN", "CROSS JOIN", "GROUP BY", "ORDER BY",
        "HAVING", "LIMIT", "OFFSET", "UNION", "UNION ALL", "INTERSECT", "EXCEPT",
        "INSERT INTO", "VALUES", "UPDATE", "SET", "DELETE FROM",
        "CREATE TABLE", "CREATE INDEX", "CREATE VIEW", "CREATE DATABASE",
        "DROP TABLE", "DROP INDEX", "ALTER TABLE", "ON", "WITH"
    );

    // ─── 格式化 ────────────────────────────────────────────────────────────

    /**
     * SQL 格式化（自动关键字大写 + 子句换行 + 4空格缩进）
     */
    public static String format(String sql) {
        if (isBlank(sql)) return "";
        // 先去注释，再处理
        String cleaned = removeInlineComments(sql);
        return doFormat(cleaned, true);
    }

    /**
     * SQL 格式化（保留原始关键字大小写）
     */
    public static String formatPreserveCase(String sql) {
        if (isBlank(sql)) return "";
        return doFormat(removeInlineComments(sql), false);
    }

    private static String doFormat(String sql, boolean upperKeywords) {
        // Token 化
        List<Token> tokens = tokenize(sql);
        if (upperKeywords) tokens.forEach(t -> { if (t.type == TokenType.KEYWORD) t.value = t.value.toUpperCase(); });

        StringBuilder sb = new StringBuilder();
        int indent = 0;
        boolean newLine = true;

        for (int i = 0; i < tokens.size(); i++) {
            Token tok = tokens.get(i);
            if (tok.type == TokenType.WHITESPACE) continue;

            String upper = tok.value.toUpperCase();

            // 多词子句（LEFT JOIN, GROUP BY 等）
            if (tok.type == TokenType.KEYWORD) {
                // 尝试拼接下一个 keyword 形成复合子句
                String compound = upper;
                if (i + 1 < tokens.size()) {
                    int j = i + 1;
                    while (j < tokens.size() && tokens.get(j).type == TokenType.WHITESPACE) j++;
                    if (j < tokens.size() && tokens.get(j).type == TokenType.KEYWORD) {
                        String next = tokens.get(j).value.toUpperCase();
                        String maybe = upper + " " + next;
                        if (CLAUSE_KEYWORDS.contains(maybe)) {
                            compound = maybe;
                            // 合并
                            tok.value = upperKeywords ? compound : tok.value + " " + tokens.get(j).value;
                            tokens.remove(j);
                            // 跳过中间空白
                            while (j < tokens.size() && tokens.get(j).type == TokenType.WHITESPACE)
                                tokens.remove(j);
                        }
                    }
                }

                if (CLAUSE_KEYWORDS.contains(compound)) {
                    if (compound.equals("SELECT") || compound.equals("INSERT INTO")
                            || compound.equals("UPDATE") || compound.equals("DELETE FROM")
                            || compound.equals("CREATE TABLE") || compound.equals("CREATE INDEX")
                            || compound.equals("CREATE VIEW") || compound.equals("WITH")) {
                        indent = 0;
                    }
                    if (!newLine && sb.length() > 0) sb.append("\n");
                    sb.append("    ".repeat(indent)).append(tok.value).append("\n");
                    sb.append("    ".repeat(indent + 1));
                    newLine = false;
                    continue;
                }
            }

            // 左括号增加缩进
            if (tok.value.equals("(")) {
                indent++;
                sb.append("(");
                newLine = false;
                continue;
            }
            // 右括号减少缩进
            if (tok.value.equals(")")) {
                indent = Math.max(0, indent - 1);
                // 如果上一个非空白是换行，不额外加空格
                sb.append(")");
                newLine = false;
                continue;
            }

            // 逗号后换行（SELECT 字段列表、VALUES 等）
            if (tok.value.equals(",")) {
                sb.append(",\n").append("    ".repeat(indent + 1));
                newLine = false;
                continue;
            }

            // 分号单独一行
            if (tok.value.equals(";")) {
                sb.append(";\n");
                indent = 0;
                newLine = true;
                continue;
            }

            // 普通 token：加空格分隔
            String prev = sb.length() > 0 ? sb.substring(sb.length() - 1) : "";
            boolean needSpace = !newLine && !prev.equals("(") && !prev.equals(" ")
                    && !prev.equals("\n") && !tok.value.equals(")");
            if (needSpace) sb.append(" ");
            sb.append(tok.value);
            newLine = false;
        }

        return sb.toString().trim();
    }

    // ─── 压缩（去多余空白） ─────────────────────────────────────────────────

    public static String compress(String sql) {
        if (isBlank(sql)) return "";
        return removeComments(sql)
                .replaceAll("[ \\t\\r\\n]+", " ")
                .trim();
    }

    // ─── 关键字大小写 ──────────────────────────────────────────────────────

    public static String toUpperKeywords(String sql) {
        return replaceKeywords(sql, true);
    }

    public static String toLowerKeywords(String sql) {
        return replaceKeywords(sql, false);
    }

    private static String replaceKeywords(String sql, boolean upper) {
        List<Token> tokens = tokenize(sql);
        StringBuilder sb = new StringBuilder();
        for (Token t : tokens) {
            if (t.type == TokenType.KEYWORD) {
                sb.append(upper ? t.value.toUpperCase() : t.value.toLowerCase());
            } else {
                sb.append(t.value);
            }
        }
        return sb.toString();
    }

    // ─── 去注释 ────────────────────────────────────────────────────────────

    /** Remove all comments (-- line comments and block comments) */
    public static String removeComments(String sql) {
        // 先去块注释
        sql = sql.replaceAll("/\\*[\\s\\S]*?\\*/", " ");
        // 再去行注释
        sql = sql.replaceAll("--[^\\n]*", "");
        return sql;
    }

    /** Remove only inline -- comments, preserve block comments */
    public static String removeInlineComments(String sql) {
        sql = sql.replaceAll("/\\*[\\s\\S]*?\\*/", " ");
        sql = sql.replaceAll("--[^\\n]*", "");
        return sql;
    }

    // ─── 语法检测 ──────────────────────────────────────────────────────────

    public record SyntaxError(int position, String message, String token) {}

    /**
     * 基础语法检测：检查括号配对、常见语法错误
     * 返回 error 列表（空=无问题）
     */
    public static List<SyntaxError> checkSyntax(String sql) {
        List<SyntaxError> errors = new ArrayList<>();
        if (isBlank(sql)) return errors;

        String clean = removeComments(sql).trim();
        List<Token> tokens = tokenize(clean);
        List<Token> meaningful = tokens.stream()
                .filter(t -> t.type != TokenType.WHITESPACE)
                .toList();

        // 1. 括号配对
        Deque<Token> parenStack = new ArrayDeque<>();
        for (Token t : meaningful) {
            if (t.value.equals("(")) parenStack.push(t);
            else if (t.value.equals(")")) {
                if (parenStack.isEmpty()) {
                    errors.add(new SyntaxError(t.pos, "多余的右括号 ')'", t.value));
                } else {
                    parenStack.pop();
                }
            }
        }
        while (!parenStack.isEmpty()) {
            Token t = parenStack.pop();
            errors.add(new SyntaxError(t.pos, "缺少对应的右括号 ')'（左括号位于此处）", "("));
        }

        // 2. SELECT 后必须有 FROM（除非是 SELECT 1 这种）
        checkSelectFrom(meaningful, errors);

        // 3. WHERE 后不能直接接 AND/OR
        checkWhereAndOr(meaningful, errors);

        // 4. 连续两个逗号
        for (int i = 0; i < meaningful.size() - 1; i++) {
            if (meaningful.get(i).value.equals(",") && meaningful.get(i + 1).value.equals(",")) {
                errors.add(new SyntaxError(meaningful.get(i).pos, "连续两个逗号，可能有多余逗号", ",,"));
            }
        }

        // 5. JOIN 后必须有 ON 或 USING（基本检查）
        checkJoinOn(meaningful, errors);

        // 6. 尾部多余逗号（最后一个字段后有逗号接 FROM/WHERE 等）
        checkTrailingComma(meaningful, errors);

        // 7. UPDATE 必须有 WHERE（提示警告，不算硬错误）
        checkUpdateWithoutWhere(meaningful, errors);

        // 8. DELETE 必须有 WHERE（提示警告）
        checkDeleteWithoutWhere(meaningful, errors);

        return errors;
    }

    private static void checkSelectFrom(List<Token> tokens, List<SyntaxError> errors) {
        boolean hasSelect = false;
        int selectPos = 0;
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).value.equalsIgnoreCase("SELECT")) {
                hasSelect = true;
                selectPos = tokens.get(i).pos;
            }
        }
        if (!hasSelect) return;
        // 简单检查：SELECT 后必须找到 FROM（排除 SELECT 1, SELECT version() 等）
        boolean hasFrom = tokens.stream().anyMatch(t -> t.value.equalsIgnoreCase("FROM"));
        boolean hasInsert = tokens.stream().anyMatch(t -> t.value.equalsIgnoreCase("INSERT"));
        if (!hasFrom && !hasInsert) {
            // 只有 SELECT，但没有 FROM，可能是 SELECT 1 这类，不报错
        }
    }

    private static void checkWhereAndOr(List<Token> tokens, List<SyntaxError> errors) {
        for (int i = 0; i < tokens.size() - 1; i++) {
            String cur  = tokens.get(i).value.toUpperCase();
            String next = tokens.get(i + 1).value.toUpperCase();
            if (cur.equals("WHERE") && (next.equals("AND") || next.equals("OR"))) {
                errors.add(new SyntaxError(tokens.get(i + 1).pos,
                    "WHERE 后不应直接跟 " + next + "，WHERE 子句条件缺失", next));
            }
        }
    }

    private static void checkJoinOn(List<Token> tokens, List<SyntaxError> errors) {
        for (int i = 0; i < tokens.size(); i++) {
            String cur = tokens.get(i).value.toUpperCase();
            if (cur.equals("JOIN")) {
                // 在后续 tokens 中找 ON 或 USING，直到下一个 JOIN/WHERE/GROUP/ORDER
                boolean foundOn = false;
                for (int j = i + 1; j < tokens.size(); j++) {
                    String v = tokens.get(j).value.toUpperCase();
                    if (v.equals("ON") || v.equals("USING")) { foundOn = true; break; }
                    if (v.equals("JOIN") || v.equals("WHERE") || v.equals("GROUP")
                            || v.equals("ORDER") || v.equals("HAVING") || v.equals(";")) break;
                }
                if (!foundOn) {
                    errors.add(new SyntaxError(tokens.get(i).pos,
                        "JOIN 后缺少 ON 或 USING 条件", "JOIN"));
                }
            }
        }
    }

    private static void checkTrailingComma(List<Token> tokens, List<SyntaxError> errors) {
        for (int i = 0; i < tokens.size() - 1; i++) {
            if (tokens.get(i).value.equals(",")) {
                String next = tokens.get(i + 1).value.toUpperCase();
                if (CLAUSE_KEYWORDS.contains(next) || next.equals("FROM") || next.equals("WHERE")
                        || next.equals("GROUP") || next.equals("ORDER") || next.equals("HAVING")
                        || next.equals(")") || next.equals(";")) {
                    errors.add(new SyntaxError(tokens.get(i).pos,
                        "尾部多余的逗号（逗号后紧跟关键字/括号）", ","));
                }
            }
        }
    }

    private static void checkUpdateWithoutWhere(List<Token> tokens, List<SyntaxError> errors) {
        boolean hasUpdate = tokens.stream().anyMatch(t -> t.value.equalsIgnoreCase("UPDATE"));
        boolean hasWhere  = tokens.stream().anyMatch(t -> t.value.equalsIgnoreCase("WHERE"));
        if (hasUpdate && !hasWhere) {
            errors.add(new SyntaxError(0, "⚠ UPDATE 语句没有 WHERE 条件，将更新全表所有行！", "UPDATE"));
        }
    }

    private static void checkDeleteWithoutWhere(List<Token> tokens, List<SyntaxError> errors) {
        boolean hasDelete = tokens.stream().anyMatch(t -> t.value.equalsIgnoreCase("DELETE"));
        boolean hasWhere  = tokens.stream().anyMatch(t -> t.value.equalsIgnoreCase("WHERE"));
        if (hasDelete && !hasWhere) {
            errors.add(new SyntaxError(0, "⚠ DELETE 语句没有 WHERE 条件，将删除全表所有行！", "DELETE"));
        }
    }

    // ─── 提取表名、字段名 ──────────────────────────────────────────────────

    public record SqlMeta(List<String> tables, List<String> fields) {}

    public static SqlMeta extractMeta(String sql) {
        List<String> tables = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        String clean = removeComments(sql).replaceAll("\\s+", " ").trim();

        // 提取表名：FROM xxx, JOIN xxx
        Pattern fromP = Pattern.compile("(?i)(?:FROM|JOIN)\\s+([`\\[]?[\\w.]+[`\\]]?)(?:\\s+(?:AS\\s+)?([\\w]+))?");
        Matcher fromM = fromP.matcher(clean);
        while (fromM.find()) tables.add(fromM.group(1).replaceAll("[`\\[\\]]", ""));

        // 提取字段（SELECT 和 UPDATE SET 中的字段）
        Pattern selP = Pattern.compile("(?i)SELECT\\s+(.+?)\\s+FROM");
        Matcher selM = selP.matcher(clean);
        if (selM.find()) {
            String[] parts = selM.group(1).split(",");
            for (String p : parts) {
                p = p.trim().replaceAll("(?i)\\s+AS\\s+\\w+", "").trim();
                if (!p.equals("*")) fields.add(p);
            }
        }

        return new SqlMeta(tables, fields);
    }

    // ─── 参数占位符替换 ────────────────────────────────────────────────────

    /**
     * 将 ? 占位符替换为具体值
     * @param sql    含 ? 的 SQL
     * @param params 参数列表（字符串自动加引号）
     */
    public static String fillParams(String sql, List<String> params) {
        if (params == null || params.isEmpty()) return sql;
        StringBuilder sb = new StringBuilder();
        int paramIdx = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            // 跳过字符串内部的 ?
            if (c == '\'' || c == '"') {
                char quote = c;
                sb.append(c);
                i++;
                while (i < sql.length() && sql.charAt(i) != quote) {
                    sb.append(sql.charAt(i++));
                }
                if (i < sql.length()) sb.append(sql.charAt(i));
            } else if (c == '?' && paramIdx < params.size()) {
                String v = params.get(paramIdx++);
                // 判断是否需要加引号
                boolean isNum = v.matches("-?\\d+(\\.\\d+)?");
                boolean isBool = v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false");
                boolean isNull = v.equalsIgnoreCase("null");
                if (isNum || isBool || isNull) sb.append(v);
                else sb.append("'").append(v.replace("'", "''")).append("'");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ─── INSERT 模板生成 ───────────────────────────────────────────────────

    /**
     * 根据表名+字段列表生成 INSERT 语句模板
     */
    public static String generateInsertTemplate(String tableName, List<String> columns) {
        if (isBlank(tableName) || columns == null || columns.isEmpty())
            throw new IllegalArgumentException("表名和字段列表不能为空");
        String cols = String.join(", ", columns);
        String placeholders = columns.stream().map(c -> "?").collect(java.util.stream.Collectors.joining(", "));
        return "INSERT INTO " + tableName + " (\n    " +
               String.join(",\n    ", columns) +
               "\n) VALUES (\n    " +
               columns.stream().map(c -> "/* " + c + " */").collect(java.util.stream.Collectors.joining(",\n    ")) +
               "\n);";
    }

    // ─── Tokenizer ─────────────────────────────────────────────────────────

    private enum TokenType { KEYWORD, IDENTIFIER, STRING, NUMBER, OPERATOR, PUNCTUATION, WHITESPACE, COMMENT }

    private static class Token {
        TokenType type;
        String value;
        int pos;
        Token(TokenType type, String value, int pos) {
            this.type = type; this.value = value; this.pos = pos;
        }
    }

    static List<Token> tokenize(String sql) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);

            // 空白
            if (Character.isWhitespace(c)) {
                int start = i;
                while (i < sql.length() && Character.isWhitespace(sql.charAt(i))) i++;
                tokens.add(new Token(TokenType.WHITESPACE, sql.substring(start, i), start));
                continue;
            }

            // 行注释 --
            if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                int start = i;
                while (i < sql.length() && sql.charAt(i) != '\n') i++;
                tokens.add(new Token(TokenType.COMMENT, sql.substring(start, i), start));
                continue;
            }

            // 块注释 /* */
            if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                int start = i;
                i += 2;
                while (i < sql.length() - 1 && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) i++;
                i += 2;
                tokens.add(new Token(TokenType.COMMENT, sql.substring(start, Math.min(i, sql.length())), start));
                continue;
            }

            // 字符串
            if (c == '\'' || c == '"' || c == '`') {
                char quote = c;
                int start = i;
                i++;
                while (i < sql.length()) {
                    if (sql.charAt(i) == quote) {
                        if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) { i += 2; continue; }
                        break;
                    }
                    i++;
                }
                i++;
                String raw = sql.substring(start, Math.min(i, sql.length()));
                tokens.add(new Token(quote == '`' ? TokenType.IDENTIFIER : TokenType.STRING, raw, start));
                continue;
            }

            // 数字
            if (Character.isDigit(c) || (c == '.' && i + 1 < sql.length() && Character.isDigit(sql.charAt(i + 1)))) {
                int start = i;
                while (i < sql.length() && (Character.isDigit(sql.charAt(i)) || sql.charAt(i) == '.')) i++;
                tokens.add(new Token(TokenType.NUMBER, sql.substring(start, i), start));
                continue;
            }

            // 标识符 / 关键字
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < sql.length() && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_')) i++;
                String word = sql.substring(start, i);
                boolean isKw = KEYWORDS.contains(word.toUpperCase());
                tokens.add(new Token(isKw ? TokenType.KEYWORD : TokenType.IDENTIFIER, word, start));
                continue;
            }

            // 操作符
            if ("=<>!".indexOf(c) >= 0) {
                int start = i;
                i++;
                if (i < sql.length() && "=<>".indexOf(sql.charAt(i)) >= 0) i++;
                tokens.add(new Token(TokenType.OPERATOR, sql.substring(start, i), start));
                continue;
            }

            // 标点
            tokens.add(new Token(TokenType.PUNCTUATION, String.valueOf(c), i));
            i++;
        }
        return tokens;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

