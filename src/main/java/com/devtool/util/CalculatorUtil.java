package com.devtool.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CalculatorUtil {

    private static final MathContext MC = new MathContext(34, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final Map<String, BigDecimal> CONSTANTS = Map.of(
            "pi", new BigDecimal(String.valueOf(Math.PI), MC),
            "e", new BigDecimal(String.valueOf(Math.E), MC)
    );

    private CalculatorUtil() {}

    public static BigDecimal evaluate(String expression) {
        String normalized = normalize(expression);
        if (normalized.isBlank()) {
            return BigDecimal.ZERO;
        }
        List<Token> tokens = tokenize(normalized);
        List<Token> rpn = toRpn(tokens);
        return evalRpn(rpn);
    }

    public static String format(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0, RoundingMode.UNNECESSARY);
        }
        String plain = stripped.toPlainString();
        if (plain.equals("-0")) {
            return "0";
        }
        return plain;
    }

    private static String normalize(String expression) {
        if (expression == null) {
            return "";
        }
        String s = expression.trim();
        s = s.replace('×', '*').replace('÷', '/').replace('−', '-');
        s = s.replace("√", "sqrt");
        return s;
    }

    private static List<Token> tokenize(String expr) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        Token prev = null;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '(') {
                Token t = Token.lParen();
                tokens.add(t);
                prev = t;
                i++;
                continue;
            }
            if (c == ')') {
                Token t = Token.rParen();
                tokens.add(t);
                prev = t;
                i++;
                continue;
            }
            if (c == '%') {
                Token t = Token.op("%");
                tokens.add(t);
                prev = t;
                i++;
                continue;
            }
            if (isOperatorChar(c)) {
                String op = String.valueOf(c);
                if (op.equals("+") || op.equals("-")) {
                    boolean unary = prev == null
                            || prev.type == TokenType.LPAREN
                            || (prev.type == TokenType.OP && !prev.value.equals("%"))
                            || prev.type == TokenType.FUNC;
                    if (unary) {
                        op = op.equals("-") ? "neg" : "pos";
                    }
                }
                Token t = Token.op(op);
                tokens.add(t);
                prev = t;
                i++;
                continue;
            }
            if (Character.isDigit(c) || c == '.') {
                int start = i;
                boolean dotSeen = (c == '.');
                i++;
                while (i < expr.length()) {
                    char cc = expr.charAt(i);
                    if (Character.isDigit(cc)) {
                        i++;
                        continue;
                    }
                    if (cc == '.') {
                        if (dotSeen) {
                            break;
                        }
                        dotSeen = true;
                        i++;
                        continue;
                    }
                    break;
                }
                String number = expr.substring(start, i);
                if (number.equals(".")) {
                    throw new IllegalArgumentException("非法数字：.");
                }
                Token t = Token.number(new BigDecimal(number, MC));
                tokens.add(t);
                prev = t;
                continue;
            }
            if (Character.isLetter(c)) {
                int start = i;
                i++;
                while (i < expr.length() && (Character.isLetter(expr.charAt(i)) || Character.isDigit(expr.charAt(i)))) {
                    i++;
                }
                String ident = expr.substring(start, i).toLowerCase(Locale.ROOT);
                if (CONSTANTS.containsKey(ident)) {
                    Token t = Token.number(CONSTANTS.get(ident));
                    tokens.add(t);
                    prev = t;
                    continue;
                }
                if (isFunctionName(ident)) {
                    Token t = Token.func(ident);
                    tokens.add(t);
                    prev = t;
                    continue;
                }
                throw new IllegalArgumentException("未知标识符：" + ident);
            }
            throw new IllegalArgumentException("无法解析字符：" + c);
        }
        return tokens;
    }

    private static boolean isOperatorChar(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/' || c == '^';
    }

    private static boolean isFunctionName(String ident) {
        return ident.equals("sqrt") || ident.equals("abs");
    }

    private static int precedence(Token token) {
        if (token.type != TokenType.OP && token.type != TokenType.FUNC) {
            return -1;
        }
        if (token.type == TokenType.FUNC) {
            return 5;
        }
        return switch (token.value) {
            case "%", "neg", "pos" -> 4;
            case "^" -> 3;
            case "*", "/" -> 2;
            case "+", "-" -> 1;
            default -> -1;
        };
    }

    private static boolean rightAssociative(Token token) {
        return token.type == TokenType.OP && (token.value.equals("^") || token.value.equals("neg") || token.value.equals("pos"));
    }

    private static List<Token> toRpn(List<Token> tokens) {
        List<Token> output = new ArrayList<>();
        Deque<Token> stack = new ArrayDeque<>();

        for (Token token : tokens) {
            switch (token.type) {
                case NUMBER -> output.add(token);
                case FUNC -> stack.push(token);
                case OP -> {
                    if (token.value.equals("%")) {
                        output.add(token);
                        break;
                    }
                    while (!stack.isEmpty()) {
                        Token top = stack.peek();
                        if (top.type == TokenType.FUNC) {
                            output.add(stack.pop());
                            continue;
                        }
                        if (top.type == TokenType.OP) {
                            int p1 = precedence(token);
                            int p2 = precedence(top);
                            if ((rightAssociative(token) && p1 < p2) || (!rightAssociative(token) && p1 <= p2)) {
                                output.add(stack.pop());
                                continue;
                            }
                        }
                        break;
                    }
                    stack.push(token);
                }
                case LPAREN -> stack.push(token);
                case RPAREN -> {
                    boolean foundLParen = false;
                    while (!stack.isEmpty()) {
                        Token top = stack.pop();
                        if (top.type == TokenType.LPAREN) {
                            foundLParen = true;
                            break;
                        }
                        output.add(top);
                    }
                    if (!foundLParen) {
                        throw new IllegalArgumentException("括号不匹配");
                    }
                    if (!stack.isEmpty() && stack.peek().type == TokenType.FUNC) {
                        output.add(stack.pop());
                    }
                }
            }
        }

        while (!stack.isEmpty()) {
            Token top = stack.pop();
            if (top.type == TokenType.LPAREN || top.type == TokenType.RPAREN) {
                throw new IllegalArgumentException("括号不匹配");
            }
            output.add(top);
        }
        return output;
    }

    private static BigDecimal evalRpn(List<Token> rpn) {
        Deque<BigDecimal> stack = new ArrayDeque<>();
        for (Token token : rpn) {
            switch (token.type) {
                case NUMBER -> stack.push(token.number);
                case OP -> {
                    switch (token.value) {
                        case "+" -> {
                            BigDecimal b = pop(stack);
                            BigDecimal a = pop(stack);
                            stack.push(a.add(b, MC));
                        }
                        case "-" -> {
                            BigDecimal b = pop(stack);
                            BigDecimal a = pop(stack);
                            stack.push(a.subtract(b, MC));
                        }
                        case "*" -> {
                            BigDecimal b = pop(stack);
                            BigDecimal a = pop(stack);
                            stack.push(a.multiply(b, MC));
                        }
                        case "/" -> {
                            BigDecimal b = pop(stack);
                            BigDecimal a = pop(stack);
                            if (b.compareTo(BigDecimal.ZERO) == 0) {
                                throw new IllegalArgumentException("除数不能为 0");
                            }
                            stack.push(a.divide(b, MC));
                        }
                        case "^" -> {
                            BigDecimal b = pop(stack);
                            BigDecimal a = pop(stack);
                            stack.push(pow(a, b));
                        }
                        case "neg" -> stack.push(pop(stack).negate(MC));
                        case "pos" -> stack.push(pop(stack));
                        case "%" -> stack.push(pop(stack).divide(ONE_HUNDRED, MC));
                        default -> throw new IllegalArgumentException("不支持的运算符：" + token.value);
                    }
                }
                case FUNC -> {
                    BigDecimal a = pop(stack);
                    switch (token.value) {
                        case "sqrt" -> {
                            if (a.compareTo(BigDecimal.ZERO) < 0) {
                                throw new IllegalArgumentException("平方根参数不能为负数");
                            }
                            double v = a.doubleValue();
                            stack.push(new BigDecimal(Math.sqrt(v), MC));
                        }
                        case "abs" -> stack.push(a.abs(MC));
                        default -> throw new IllegalArgumentException("不支持的函数：" + token.value);
                    }
                }
                default -> throw new IllegalStateException("非法 token: " + token.type);
            }
        }
        if (stack.size() != 1) {
            throw new IllegalArgumentException("表达式不完整");
        }
        return stack.pop();
    }

    private static BigDecimal pow(BigDecimal a, BigDecimal b) {
        try {
            int n = b.intValueExact();
            if (n < 0) {
                BigDecimal positive = a.pow(-n, MC);
                if (positive.compareTo(BigDecimal.ZERO) == 0) {
                    throw new IllegalArgumentException("除数不能为 0");
                }
                return BigDecimal.ONE.divide(positive, MC);
            }
            return a.pow(n, MC);
        } catch (ArithmeticException ignore) {
            double v = Math.pow(a.doubleValue(), b.doubleValue());
            return new BigDecimal(v, MC);
        }
    }

    private static BigDecimal pop(Deque<BigDecimal> stack) {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("表达式不完整");
        }
        return stack.pop();
    }

    private enum TokenType { NUMBER, OP, LPAREN, RPAREN, FUNC }

    private static final class Token {
        private final TokenType type;
        private final String value;
        private final BigDecimal number;

        private Token(TokenType type, String value, BigDecimal number) {
            this.type = type;
            this.value = value;
            this.number = number;
        }

        static Token number(BigDecimal n) {
            return new Token(TokenType.NUMBER, null, n);
        }

        static Token op(String op) {
            return new Token(TokenType.OP, op, null);
        }

        static Token lParen() {
            return new Token(TokenType.LPAREN, "(", null);
        }

        static Token rParen() {
            return new Token(TokenType.RPAREN, ")", null);
        }

        static Token func(String name) {
            return new Token(TokenType.FUNC, name, null);
        }
    }
}

