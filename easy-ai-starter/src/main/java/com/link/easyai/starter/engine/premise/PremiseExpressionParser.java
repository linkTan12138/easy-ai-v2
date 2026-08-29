package com.link.easyai.starter.engine.premise;

import com.link.easyai.starter.engine.config.PremiseConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 前提条件表达式解析器。
 * <p>
 * 将 {@code @AiPremise} 中的表达式字符串解析为 {@link PremiseConfig} 树。
 * <p>
 * 支持语法：
 * <ul>
 *   <li>存在性：{@code field != null} / {@code field == null}</li>
 *   <li>比较：{@code ==} / {@code !=} / {@code >} / {@code <} / {@code >=} / {@code <=}</li>
 *   <li>逻辑：{@code AND} / {@code OR} / {@code !}（同时支持 {@code &&} / {@code ||}）</li>
 *   <li>包含：{@code field in ('v1','v2')}</li>
 *   <li>分组：{@code ()}</li>
 * </ul>
 * <p>
 * 采用递归下降解析，运算符优先级（从低到高）：OR → AND → NOT → 比较/in → 主键
 */
public class PremiseExpressionParser {

    private final String input;
    private final List<Token> tokens;
    private int pos;

    public PremiseExpressionParser(String expression) {
        this.input = expression;
        this.tokens = tokenize(expression);
        this.pos = 0;
    }

    /**
     * 解析表达式为 PremiseConfig 树。
     *
     * @return 解析后的 PremiseConfig
     * @throws PremiseParseException 语法错误时抛出
     */
    public PremiseConfig parse() {
        if (tokens.isEmpty()) {
            throw new PremiseParseException("表达式为空");
        }
        PremiseConfig result = parseOr();
        if (pos < tokens.size()) {
            throw new PremiseParseException("意外的 token: " + tokens.get(pos).value +
                    " (位置 " + tokens.get(pos).position + ")");
        }
        return result;
    }

    // ---- 递归下降解析 ----

    /** OR 表达式：and_expr (OR and_expr)* */
    private PremiseConfig parseOr() {
        PremiseConfig left = parseAnd();
        while (match(TokenType.OR)) {
            PremiseConfig right = parseAnd();
            left = combine("OR", left, right);
        }
        return left;
    }

    /** AND 表达式：not_expr (AND not_expr)* */
    private PremiseConfig parseAnd() {
        PremiseConfig left = parseNot();
        while (match(TokenType.AND)) {
            PremiseConfig right = parseNot();
            left = combine("AND", left, right);
        }
        return left;
    }

    /** NOT 表达式：! not_expr | comparison */
    private PremiseConfig parseNot() {
        if (match(TokenType.NOT)) {
            PremiseConfig operand = parseNot();
            // NOT 用 OR + NOT_EXISTS 模拟：NOT(a) 等价于 对 a 的取反
            // 这里用一个特殊的 NOT 组合，实际求值时需要支持
            return PremiseConfig.builder()
                    .operator("NOT")
                    .conditions(List.of(operand))
                    .build();
        }
        return parseComparison();
    }

    /** 比较表达式：primary op primary | primary in (list) | primary */
    private PremiseConfig parseComparison() {
        PremiseConfig left = parsePrimary();

        // in 表达式：field in (v1, v2, ...)
        if (match(TokenType.IN)) {
            expect(TokenType.LPAREN, "in 后面需要 '('");
            List<Object> values = new ArrayList<>();
            if (!check(TokenType.RPAREN)) {
                values.add(parseLiteralValue());
                while (match(TokenType.COMMA)) {
                    values.add(parseLiteralValue());
                }
            }
            expect(TokenType.RPAREN, "in 列表需要 ')'");
            if (left.getField() == null) {
                throw new PremiseParseException("in 操作符左边必须是字段名");
            }
            return PremiseConfig.builder()
                    .field(left.getField())
                    .conditionOperator("in")
                    .values(new ArrayList<>(values))
                    .build();
        }

        // 比较操作符
        TokenType op = null;
        if (match(TokenType.EQ)) op = TokenType.EQ;
        else if (match(TokenType.NEQ)) op = TokenType.NEQ;
        else if (match(TokenType.GTE)) op = TokenType.GTE;
        else if (match(TokenType.LTE)) op = TokenType.LTE;
        else if (match(TokenType.GT)) op = TokenType.GT;
        else if (match(TokenType.LT)) op = TokenType.LT;

        if (op != null) {
            PremiseConfig right = parsePrimary();

            // 处理 == null / != null
            if (right.getField() != null && "null".equalsIgnoreCase(right.getField())) {
                String conditionOp = (op == TokenType.EQ) ? "notExists" : "exists";
                if (left.getField() == null) {
                    throw new PremiseParseException("null 比较左边必须是字段名");
                }
                return PremiseConfig.builder()
                        .field(left.getField())
                        .conditionOperator(conditionOp)
                        .build();
            }

            // 普通比较：field op value
            if (left.getField() == null) {
                throw new PremiseParseException("比较操作符左边必须是字段名");
            }
            Object value = right.getField() != null ? right.getField() : right.getValue();
            return PremiseConfig.builder()
                    .field(left.getField())
                    .conditionOperator(op.operator)
                    .value(value)
                    .build();
        }

        // 单独的字段名（隐式 exists）
        if (left.getField() != null && left.getConditionOperator() == null) {
            return PremiseConfig.builder()
                    .field(left.getField())
                    .conditionOperator("exists")
                    .build();
        }

        return left;
    }

    /** 主键：IDENTIFIER | STRING | NUMBER | (expr) */
    private PremiseConfig parsePrimary() {
        Token token = peek();
        if (token == null) {
            throw new PremiseParseException("表达式意外结束");
        }

        switch (token.type) {
            case IDENTIFIER:
                next();
                return PremiseConfig.builder().field(token.value).build();
            case STRING:
                next();
                return PremiseConfig.builder().field(null).value(token.value).build();
            case NUMBER:
                next();
                return PremiseConfig.builder().field(null).value(parseNumber(token.value)).build();
            case LPAREN:
                next();
                PremiseConfig expr = parseOr();
                expect(TokenType.RPAREN, "需要 ')'");
                return expr;
            default:
                throw new PremiseParseException("意外的 token: " + token.value +
                        " (位置 " + token.position + ")");
        }
    }

    /** 解析字面量值（用于 in 列表） */
    private Object parseLiteralValue() {
        Token token = peek();
        if (token == null) {
            throw new PremiseParseException("in 列表中意外结束");
        }
        if (token.type == TokenType.STRING || token.type == TokenType.IDENTIFIER) {
            next();
            return token.value;
        }
        if (token.type == TokenType.NUMBER) {
            next();
            return parseNumber(token.value);
        }
        throw new PremiseParseException("in 列表中需要字面量值，实际: " + token.value);
    }

    // ---- 辅助方法 ----

    private PremiseConfig combine(String operator, PremiseConfig left, PremiseConfig right) {
        List<PremiseConfig> conditions = new ArrayList<>();
        // 扁平化：如果左操作数已经是同操作符的组合，直接合并
        if (operator.equals(left.getOperator()) && left.getConditions() != null) {
            conditions.addAll(left.getConditions());
        } else {
            conditions.add(left);
        }
        if (operator.equals(right.getOperator()) && right.getConditions() != null) {
            conditions.addAll(right.getConditions());
        } else {
            conditions.add(right);
        }
        return PremiseConfig.builder()
                .operator(operator)
                .conditions(conditions)
                .build();
    }

    private Object parseNumber(String value) {
        if (value.contains(".") || value.contains("e") || value.contains("E")) {
            return Double.parseDouble(value);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return Double.parseDouble(value);
        }
    }

    // ---- Token 操作 ----

    private Token peek() {
        return pos < tokens.size() ? tokens.get(pos) : null;
    }

    private Token next() {
        return tokens.get(pos++);
    }

    private boolean check(TokenType type) {
        Token t = peek();
        return t != null && t.type == type;
    }

    private boolean match(TokenType type) {
        if (check(type)) {
            pos++;
            return true;
        }
        return false;
    }

    private void expect(TokenType type, String message) {
        if (!match(type)) {
            Token t = peek();
            throw new PremiseParseException(message + "，实际: " + (t != null ? t.value : "EOF"));
        }
    }

    // ---- Tokenizer ----

    private List<Token> tokenize(String expr) {
        List<Token> result = new ArrayList<>();
        int i = 0;
        int len = expr.length();

        while (i < len) {
            char c = expr.charAt(i);

            // 跳过空白
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // 字符串字面量（单引号或双引号）
            if (c == '\'' || c == '"') {
                char quote = c;
                i++;
                StringBuilder sb = new StringBuilder();
                while (i < len && expr.charAt(i) != quote) {
                    if (expr.charAt(i) == '\\' && i + 1 < len) {
                        i++;
                        sb.append(expr.charAt(i));
                    } else {
                        sb.append(expr.charAt(i));
                    }
                    i++;
                }
                if (i >= len) {
                    throw new PremiseParseException("字符串未闭合: " + sb);
                }
                i++; // 跳过结束引号
                result.add(new Token(TokenType.STRING, sb.toString(), i - sb.length() - 1));
                continue;
            }

            // 多字符操作符
            if (c == '=' && i + 1 < len && expr.charAt(i + 1) == '=') {
                result.add(new Token(TokenType.EQ, "==", i));
                i += 2;
                continue;
            }
            if (c == '!' && i + 1 < len && expr.charAt(i + 1) == '=') {
                result.add(new Token(TokenType.NEQ, "!=", i));
                i += 2;
                continue;
            }
            if (c == '>' && i + 1 < len && expr.charAt(i + 1) == '=') {
                result.add(new Token(TokenType.GTE, ">=", i));
                i += 2;
                continue;
            }
            if (c == '<' && i + 1 < len && expr.charAt(i + 1) == '=') {
                result.add(new Token(TokenType.LTE, "<=", i));
                i += 2;
                continue;
            }
            if (c == '&' && i + 1 < len && expr.charAt(i + 1) == '&') {
                result.add(new Token(TokenType.AND, "&&", i));
                i += 2;
                continue;
            }
            if (c == '|' && i + 1 < len && expr.charAt(i + 1) == '|') {
                result.add(new Token(TokenType.OR, "||", i));
                i += 2;
                continue;
            }

            // 单字符操作符
            if (c == '!') {
                result.add(new Token(TokenType.NOT, "!", i));
                i++;
                continue;
            }
            if (c == '>') {
                result.add(new Token(TokenType.GT, ">", i));
                i++;
                continue;
            }
            if (c == '<') {
                result.add(new Token(TokenType.LT, "<", i));
                i++;
                continue;
            }
            if (c == '(') {
                result.add(new Token(TokenType.LPAREN, "(", i));
                i++;
                continue;
            }
            if (c == ')') {
                result.add(new Token(TokenType.RPAREN, ")", i));
                i++;
                continue;
            }
            if (c == ',') {
                result.add(new Token(TokenType.COMMA, ",", i));
                i++;
                continue;
            }

            // 标识符 / 关键字 / 数字
            if (Character.isJavaIdentifierStart(c) || Character.isDigit(c) || c == '-') {
                StringBuilder sb = new StringBuilder();
                int start = i;
                boolean isNumber = Character.isDigit(c) || c == '-';
                while (i < len && (Character.isJavaIdentifierPart(expr.charAt(i)) ||
                        (isNumber && (expr.charAt(i) == '.' || expr.charAt(i) == 'e' || expr.charAt(i) == 'E' ||
                                expr.charAt(i) == '+' || expr.charAt(i) == '-')))) {
                    sb.append(expr.charAt(i));
                    i++;
                }
                String word = sb.toString();

                // 关键字识别
                String upper = word.toUpperCase();
                switch (upper) {
                    case "AND":
                        result.add(new Token(TokenType.AND, word, start));
                        break;
                    case "OR":
                        result.add(new Token(TokenType.OR, word, start));
                        break;
                    case "NOT":
                        result.add(new Token(TokenType.NOT, word, start));
                        break;
                    case "IN":
                        result.add(new Token(TokenType.IN, word, start));
                        break;
                    case "NULL":
                        result.add(new Token(TokenType.IDENTIFIER, "null", start));
                        break;
                    default:
                        if (isNumber || (word.length() > 1 && word.charAt(0) == '-' && Character.isDigit(word.charAt(1)))) {
                            result.add(new Token(TokenType.NUMBER, word, start));
                        } else {
                            result.add(new Token(TokenType.IDENTIFIER, word, start));
                        }
                }
                continue;
            }

            throw new PremiseParseException("无法识别的字符: '" + c + "' (位置 " + i + ")");
        }

        return result;
    }

    // ---- Token 定义 ----

    private enum TokenType {
        IDENTIFIER("identifier"),
        STRING("string"),
        NUMBER("number"),
        NULL("null"),
        IN("in"),
        AND("and"),
        OR("or"),
        NOT("not"),
        EQ("eq"),
        NEQ("neq"),
        GT("gt"),
        LT("lt"),
        GTE("gte"),
        LTE("lte"),
        LPAREN("("),
        RPAREN(")"),
        COMMA(",");

        final String operator;

        TokenType(String operator) {
            this.operator = operator;
        }
    }

    private static class Token {
        final TokenType type;
        final String value;
        final int position;

        Token(TokenType type, String value, int position) {
            this.type = type;
            this.value = value;
            this.position = position;
        }
    }

    /**
     * 表达式解析异常。
     */
    public static class PremiseParseException extends RuntimeException {
        public PremiseParseException(String message) {
            super(message);
        }
    }
}
