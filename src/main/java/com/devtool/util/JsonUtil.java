package com.devtool.util;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.*;
import java.util.LinkedHashMap;
/**
 * JSON 工具类
 * 提供：格式化、压缩、转义/反转义、生成 Java 实体类、转 XML、JSON 对比
 */
public class JsonUtil {
    private JsonUtil() {}

    /**
     * 支持的编程语言
     */
    public enum Language {
        JAVA, PYTHON, GO
    }

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final ObjectMapper COMPACT_MAPPER = new ObjectMapper()
            .disable(SerializationFeature.INDENT_OUTPUT);
    // --- 格式化 --
    public static String format(String json) throws Exception {
        if (isBlank(json)) return "";
        JsonNode node = MAPPER.readTree(json);
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    }
    // --- 压缩 --
    public static String compress(String json) throws Exception {
        if (isBlank(json)) return "";
        JsonNode node = COMPACT_MAPPER.readTree(json);
        return COMPACT_MAPPER.writeValueAsString(node);
    }
    // --- 转义 / 反转义 --
    public static String escape(String json) throws Exception {
        if (isBlank(json)) return "";
        COMPACT_MAPPER.readTree(json);
        String compressed = COMPACT_MAPPER.writeValueAsString(COMPACT_MAPPER.readTree(json));
        String escaped = COMPACT_MAPPER.writeValueAsString(compressed);
        return escaped.substring(1, escaped.length() - 1);
    }
    public static String unescape(String escaped) throws Exception {
        if (isBlank(escaped)) return "";
        String raw = COMPACT_MAPPER.readValue("\"" + escaped + "\"", String.class);
        return format(raw);
    }
    // --- 合法性验证 --
    public static String validate(String json) {
        if (isBlank(json)) return "JSON 内容不能为空";
        try {
            COMPACT_MAPPER.readTree(json);
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }
    // --- 生成实体类（支持 Java/Python/Go）--
    public static String generateEntityClass(String json, String className, Language language, boolean useLombok) throws Exception {
        if (isBlank(json)) throw new IllegalArgumentException("JSON 内容不能为空");
        if (isBlank(className)) className = "DemoEntity";
        
        JsonNode root = COMPACT_MAPPER.readTree(json);
        if (root.isArray()) {
            ArrayNode arr = (ArrayNode) root;
            if (arr.isEmpty()) throw new IllegalArgumentException("JSON 数组为空，无法推断字段");
            root = arr.get(0);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("顶层 JSON 必须为对象 {} 或对象数组 [{}]");
        }

        return switch (language) {
            case JAVA -> generateJavaClass(root, className, useLombok);
            case PYTHON -> generatePythonClass(root, className);
            case GO -> generateGoStruct(root, className);
        };
    }

    // 兼容旧方法签名
    public static String generateJavaClass(String json, String className, boolean useLombok) throws Exception {
        return generateEntityClass(json, className, Language.JAVA, useLombok);
    }

    // --- 实体类 → JSON 示例 --
    /**
     * 将 Java / Python / Go 实体类代码解析为示例 JSON
     * 策略：正则提取字段名+类型，按类型填充占位值
     */
    public static String entityToJson(String code, Language language) throws Exception {
        if (isBlank(code)) throw new IllegalArgumentException("代码内容不能为空");
        Map<String, Object> map = new LinkedHashMap<>();
        switch (language) {
            case JAVA   -> parseJavaFields(code, map);
            case PYTHON -> parsePythonFields(code, map);
            case GO     -> parseGoFields(code, map);
        }
        if (map.isEmpty()) throw new IllegalArgumentException("未能从代码中解析出任何字段，请检查格式");
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(map);
    }

    private static void parseJavaFields(String code, Map<String, Object> map) {
        // 匹配：private/public/protected Type fieldName;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "(?:private|public|protected)\\s+([\\w<>,\\s]+?)\\s+(\\w+)\\s*;"
        );
        java.util.regex.Matcher m = p.matcher(code);
        while (m.find()) {
            String type = m.group(1).trim().replaceAll("\\s+", " ");
            String name = m.group(2).trim();
            if (name.equals("serialVersionUID")) continue;
            map.put(name, javaTypePlaceholder(type));
        }
    }

    private static void parsePythonFields(String code, Map<String, Object> map) {
        // 匹配 dataclass 风格：field_name: type  或  field_name: type = default
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "^\\s{4}(\\w+)\\s*:\\s*([\\w\\[\\],\\s]+?)(?:\\s*=.*)?$",
            java.util.regex.Pattern.MULTILINE
        );
        java.util.regex.Matcher m = p.matcher(code);
        while (m.find()) {
            String name = m.group(1).trim();
            String type = m.group(2).trim();
            if (name.startsWith("#") || name.equals("pass")) continue;
            map.put(name, pythonTypePlaceholder(type));
        }
    }

    private static void parseGoFields(String code, Map<String, Object> map) {
        // 匹配 FieldName Type `json:"tag"`  或  FieldName Type
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "^\\s+(\\w+)\\s+(\\S+).*?(?:json:\"([^\"]+)\")?",
            java.util.regex.Pattern.MULTILINE
        );
        java.util.regex.Matcher m = p.matcher(code);
        while (m.find()) {
            String goName = m.group(1).trim();
            String type   = m.group(2).trim();
            String jsonTag = m.group(3);
            if (goName.equals("//") || type.startsWith("//")) continue;
            // 用 json tag 作 key（如有），否则转 camelCase
            String key = (jsonTag != null && !jsonTag.isEmpty() && !jsonTag.equals("-"))
                    ? jsonTag : toCamelCase(goName);
            map.put(key, goTypePlaceholder(type));
        }
    }

    private static Object javaTypePlaceholder(String type) {
        String t = type.toLowerCase();
        if (t.equals("string"))               return "example";
        if (t.matches("int|integer"))         return 0;
        if (t.matches("long"))                return 0L;
        if (t.matches("double|float|bigdecimal")) return 0.0;
        if (t.matches("boolean"))             return false;
        if (t.startsWith("list<") || t.startsWith("arraylist<")) return new java.util.ArrayList<>();
        if (t.startsWith("map<"))             return new LinkedHashMap<>();
        if (t.equals("localdate"))            return "2026-01-01";
        if (t.equals("localdatetime"))        return "2026-01-01T00:00:00";
        if (t.equals("date"))                 return "2026-01-01T00:00:00";
        if (t.equals("biginteger"))           return 0;
        return null;
    }

    private static Object pythonTypePlaceholder(String type) {
        String t = type.toLowerCase().replaceAll("\\s", "");
        if (t.equals("str"))                  return "example";
        if (t.equals("int"))                  return 0;
        if (t.equals("float"))                return 0.0;
        if (t.equals("bool"))                 return false;
        if (t.startsWith("list["))            return new java.util.ArrayList<>();
        if (t.startsWith("dict[") || t.startsWith("dict")) return new LinkedHashMap<>();
        if (t.startsWith("optional["))        return null;
        return null;
    }

    private static Object goTypePlaceholder(String type) {
        return switch (type) {
            case "string"               -> "example";
            case "int", "int32", "int16", "int8", "uint", "uint32" -> 0;
            case "int64", "uint64"      -> 0;
            case "float32"              -> 0.0;
            case "float64"              -> 0.0;
            case "bool"                 -> false;
            default -> {
                if (type.startsWith("[]"))    yield new java.util.ArrayList<>();
                if (type.startsWith("map["))  yield new LinkedHashMap<>();
                if (type.startsWith("*"))     yield null;
                yield null;
            }
        };
    }

    // --- Java 类生成 ---
    private static String generateJavaClass(JsonNode obj, String className, boolean useLombok) {
        List<String> innerClasses = new ArrayList<>();
        String mainBody = buildJavaClassBody(obj, className, useLombok, innerClasses, true);
        StringBuilder sb = new StringBuilder();
        sb.append(mainBody);
        for (String inner : innerClasses) {
            sb.append("\n\n").append(inner);
        }
        return sb.toString();
    }

    private static String buildJavaClassBody(JsonNode obj, String className,
                                          boolean useLombok, List<String> innerClasses,
                                          boolean isTopLevel) {
        StringBuilder sb = new StringBuilder();
        if (isTopLevel && useLombok) {
            sb.append("import lombok.Data;\n");
            sb.append("import lombok.NoArgsConstructor;\n");
            sb.append("import lombok.AllArgsConstructor;\n");
            sb.append("import java.util.List;\n\n");
            sb.append("@Data\n@NoArgsConstructor\n@AllArgsConstructor\n");
        } else if (isTopLevel) {
            sb.append("import java.util.List;\n\n");
        }
        
        sb.append("public ").append(isTopLevel ? "" : "static ").append("class ").append(className).append(" {\n\n");

        Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
        List<String> fieldNames = new ArrayList<>();
        List<String> fieldTypes = new ArrayList<>();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String fieldName = toCamelCase(entry.getKey());
            String fieldType = inferJavaType(entry.getValue(), fieldName, useLombok, innerClasses);
            fieldNames.add(fieldName);
            fieldTypes.add(fieldType);
            sb.append("    /** ").append(entry.getKey()).append(" */\n");
            sb.append("    private ").append(fieldType).append(" ").append(fieldName).append(";\n\n");
        }

        if (!useLombok) {
            for (int i = 0; i < fieldNames.size(); i++) {
                String fn = fieldNames.get(i);
                String ft = fieldTypes.get(i);
                String capName = capitalize(fn);
                sb.append("    public ").append(ft).append(" get").append(capName).append("() {\n");
                sb.append("        return ").append(fn).append(";\n    }\n\n");
                sb.append("    public void set").append(capName)
                  .append("(").append(ft).append(" ").append(fn).append(") {\n");
                sb.append("        this.").append(fn).append(" = ").append(fn).append(";\n    }\n\n");
            }
        }

        sb.append("}");
        return sb.toString();
    }

    private static String inferJavaType(JsonNode node, String fieldName,
                                     boolean useLombok, List<String> innerClasses) {
        if (node.isNull())    return "Object";
        if (node.isBoolean()) return "Boolean";
        if (node.isInt() || node.isShort()) return "Integer";
        if (node.isLong())    return "Long";
        if (node.isFloat())   return "Float";
        if (node.isDouble() || node.isBigDecimal()) return "Double";
        if (node.isTextual()) return "String";
        if (node.isObject()) {
            String innerClassName = capitalize(fieldName);
            innerClasses.add(buildJavaClassBody(node, innerClassName, useLombok, innerClasses, false));
            return innerClassName;
        }
        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            if (!arr.isEmpty()) {
                return "List<" + inferJavaType(arr.get(0), fieldName + "Item", useLombok, innerClasses) + ">";
            }
            return "List<Object>";
        }
        return "Object";
    }

    // --- Python 类生成 ---
    private static String generatePythonClass(JsonNode obj, String className) {
        List<String> innerClasses = new ArrayList<>();
        return buildPythonClassBody(obj, className, innerClasses, true);
    }

    private static String buildPythonClassBody(JsonNode obj, String className, 
                                                List<String> innerClasses, boolean isTopLevel) {
        StringBuilder sb = new StringBuilder();
        
        if (isTopLevel) {
            sb.append("from typing import List, Optional, Any\n");
            sb.append("from dataclasses import dataclass\n\n");
        }

        sb.append("@dataclass\n");
        sb.append("class ").append(className).append(":\n");

        Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
        boolean hasFields = false;
        while (fields.hasNext()) {
            hasFields = true;
            Map.Entry<String, JsonNode> entry = fields.next();
            String fieldName = toSnakeCase(entry.getKey());
            String fieldType = inferPythonType(entry.getValue(), fieldName, innerClasses);
            sb.append("    ").append(fieldName).append(": ").append(fieldType);
            sb.append("  # ").append(entry.getKey()).append("\n");
        }

        if (!hasFields) {
            sb.append("    pass\n");
        }

        // 添加内部类
        for (String inner : innerClasses) {
            sb.append("\n\n").append(inner);
        }

        return sb.toString();
    }

    private static String inferPythonType(JsonNode node, String fieldName, List<String> innerClasses) {
        if (node.isNull())    return "Optional[Any]";
        if (node.isBoolean()) return "bool";
        if (node.isInt() || node.isLong() || node.isShort()) return "int";
        if (node.isFloat() || node.isDouble() || node.isBigDecimal()) return "float";
        if (node.isTextual()) return "str";
        if (node.isObject()) {
            String innerClassName = capitalize(fieldName);
            innerClasses.add(buildPythonClassBody(node, innerClassName, innerClasses, false));
            return innerClassName;
        }
        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            if (!arr.isEmpty()) {
                return "List[" + inferPythonType(arr.get(0), fieldName + "Item", innerClasses) + "]";
            }
            return "List[Any]";
        }
        return "Any";
    }

    // --- Go 结构体生成 ---
    private static String generateGoStruct(JsonNode obj, String structName) {
        List<String> innerStructs = new ArrayList<>();
        String mainBody = buildGoStructBody(obj, structName, innerStructs);
        
        StringBuilder sb = new StringBuilder();
        sb.append("package main\n\n");
        sb.append(mainBody);
        
        // 添加内部结构体
        for (String inner : innerStructs) {
            sb.append("\n\n").append(inner);
        }
        
        return sb.toString();
    }

    private static String buildGoStructBody(JsonNode obj, String structName, List<String> innerStructs) {
        StringBuilder sb = new StringBuilder();
        sb.append("type ").append(structName).append(" struct {\n");

        Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String fieldName = capitalize(toCamelCase(entry.getKey()));
            String fieldType = inferGoType(entry.getValue(), fieldName, innerStructs);
            String jsonTag = entry.getKey();
            sb.append("    ").append(fieldName).append(" ").append(fieldType);
            sb.append(" `json:\"").append(jsonTag).append("\"`");
            sb.append("  // ").append(entry.getKey()).append("\n");
        }

        sb.append("}");
        return sb.toString();
    }

    private static String inferGoType(JsonNode node, String fieldName, List<String> innerStructs) {
        if (node.isNull())    return "interface{}";
        if (node.isBoolean()) return "bool";
        if (node.isInt() || node.isShort()) return "int";
        if (node.isLong())    return "int64";
        if (node.isFloat())   return "float32";
        if (node.isDouble() || node.isBigDecimal()) return "float64";
        if (node.isTextual()) return "string";
        if (node.isObject()) {
            innerStructs.add(buildGoStructBody(node, fieldName, innerStructs));
            return fieldName;
        }
        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            if (!arr.isEmpty()) {
                return "[]" + inferGoType(arr.get(0), fieldName + "Item", innerStructs);
            }
            return "[]interface{}";
        }
        return "interface{}";
    }
    // --- 私有工具方法 --
    private static String toCamelCase(String name) {
        if (name == null || name.isEmpty()) return name;
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (char c : name.toCharArray()) {
            if (c == '_' || c == '-') { nextUpper = true; }
            else if (nextUpper) { sb.append(Character.toUpperCase(c)); nextUpper = false; }
            else { sb.append(c); }
        }
        if (!sb.isEmpty()) sb.setCharAt(0, Character.toLowerCase(sb.charAt(0)));
        return sb.toString();
    }
    
    private static String toSnakeCase(String name) {
        if (name == null || name.isEmpty()) return name;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else if (c == '-') {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
    
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
    
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
    // --- 转 XML --
    public static String toXml(String json) throws Exception {
        if (isBlank(json)) return "";
        JsonNode node = COMPACT_MAPPER.readTree(json);
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        nodeToXml(node, "root", sb, 0);
        return sb.toString();
    }
    private static void nodeToXml(JsonNode node, String tag, StringBuilder sb, int indent) {
        String pad = "  ".repeat(indent);
        String safeTag = tag.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
        if (!safeTag.isEmpty() && Character.isDigit(safeTag.charAt(0))) safeTag = "item_" + safeTag;
        if (safeTag.isEmpty()) safeTag = "item";
        if (node.isObject()) {
            sb.append(pad).append("<").append(safeTag).append(">\n");
            node.fields().forEachRemaining(e -> nodeToXml(e.getValue(), e.getKey(), sb, indent + 1));
            sb.append(pad).append("</").append(safeTag).append(">\n");
        } else if (node.isArray()) {
            sb.append(pad).append("<").append(safeTag).append(">\n");
            for (int i = 0; i < node.size(); i++) nodeToXml(node.get(i), "item", sb, indent + 1);
            sb.append(pad).append("</").append(safeTag).append(">\n");
        } else {
            String text = node.isTextual() ? escapeXml(node.asText()) : node.toString();
            sb.append(pad).append("<").append(safeTag).append(">")
              .append(text).append("</").append(safeTag).append(">\n");
        }
    }
    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
    // --- JSON 对比 --
    public enum DiffType {
        SAME, MODIFIED, ONLY_IN_A, ONLY_IN_B, ARRAY_REORDER
    }
    public static class DiffLine {
        public final DiffType type;
        public final String   path;
        public final String   valA;
        public final String   valB;
        public DiffLine(DiffType type, String path, String valA, String valB) {
            this.type = type;
            this.path = path;
            this.valA = valA;
            this.valB = valB;
        }
        @Override
        public String toString() {
            return switch (type) {
                case SAME          -> "  " + path + "  =  " + valA;
                case MODIFIED      -> "~ " + path + "  :  " + valA + "  ->  " + valB;
                case ONLY_IN_A     -> "- " + path + "  :  " + valA;
                case ONLY_IN_B     -> "+ " + path + "  :  " + valB;
                case ARRAY_REORDER -> "# " + path;
            };
        }
    }
    public static List<DiffLine> diff(String jsonA, String jsonB) throws Exception {
        JsonNode nodeA = COMPACT_MAPPER.readTree(jsonA);
        JsonNode nodeB = COMPACT_MAPPER.readTree(jsonB);
        List<DiffLine> result = new ArrayList<>();
        diffNodesInternal(nodeA, nodeB, "", result, false);
        return result;
    }
    public static List<DiffLine> diffAll(String jsonA, String jsonB) throws Exception {
        JsonNode nodeA = COMPACT_MAPPER.readTree(jsonA);
        JsonNode nodeB = COMPACT_MAPPER.readTree(jsonB);
        List<DiffLine> result = new ArrayList<>();
        diffNodesInternal(nodeA, nodeB, "", result, true);
        return result;
    }
    private static void diffNodesInternal(JsonNode a, JsonNode b, String path,
                                           List<DiffLine> out, boolean includeSame) {
        if (a == null && b == null) return;
        if (a == null) { collectAllPaths(b, path, out, DiffType.ONLY_IN_B); return; }
        if (b == null) { collectAllPaths(a, path, out, DiffType.ONLY_IN_A); return; }
        if (!a.getNodeType().equals(b.getNodeType())) {
            out.add(new DiffLine(DiffType.MODIFIED, path, nodeDisplay(a), nodeDisplay(b)));
            return;
        }
        if (a.isObject() && b.isObject()) {
            Set<String> keys = new LinkedHashSet<>();
            a.fieldNames().forEachRemaining(keys::add);
            b.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) {
                String childPath = path.isEmpty() ? key : path + "." + key;
                JsonNode childA = a.get(key);
                JsonNode childB = b.get(key);
                if (childA == null) {
                    collectAllPaths(childB, childPath, out, DiffType.ONLY_IN_B);
                } else if (childB == null) {
                    collectAllPaths(childA, childPath, out, DiffType.ONLY_IN_A);
                } else {
                    diffNodesInternal(childA, childB, childPath, out, includeSame);
                }
            }
        } else if (a.isArray() && b.isArray()) {
            int lenA = a.size(), lenB = b.size();
            int common = Math.min(lenA, lenB);
            for (int i = 0; i < common; i++) {
                diffNodesInternal(a.get(i), b.get(i), path + "[" + i + "]", out, includeSame);
            }
            for (int i = common; i < lenA; i++) {
                collectAllPaths(a.get(i), path + "[" + i + "]", out, DiffType.ONLY_IN_A);
            }
            for (int i = common; i < lenB; i++) {
                collectAllPaths(b.get(i), path + "[" + i + "]", out, DiffType.ONLY_IN_B);
            }
        } else {
            String dispA = nodeDisplay(a);
            String dispB = nodeDisplay(b);
            if (dispA.equals(dispB)) {
                if (includeSame) out.add(new DiffLine(DiffType.SAME, path, dispA, dispB));
            } else {
                out.add(new DiffLine(DiffType.MODIFIED, path, dispA, dispB));
            }
        }
    }
    private static void collectAllPaths(JsonNode node, String path, List<DiffLine> out, DiffType type) {
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> {
                String childPath = path.isEmpty() ? e.getKey() : path + "." + e.getKey();
                collectAllPaths(e.getValue(), childPath, out, type);
            });
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                collectAllPaths(node.get(i), path + "[" + i + "]", out, type);
            }
        } else {
            String val = nodeDisplay(node);
            if (type == DiffType.ONLY_IN_A) {
                out.add(new DiffLine(type, path, val, null));
            } else {
                out.add(new DiffLine(type, path, null, val));
            }
        }
    }
    private static String nodeDisplay(JsonNode node) {
        if (node == null)     return "null";
        if (node.isTextual()) return "\"" + node.asText() + "\"";
        if (node.isObject())  return "{ " + node.size() + " fields }";
        if (node.isArray())   return "[ " + node.size() + " items ]";
        return node.toString();
    }
}