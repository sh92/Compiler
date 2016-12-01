import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.*;

public class UCodeGenListener extends MiniCBaseListener {

    Stack<String> mainStartStack = new Stack<String>();
    Map<String, Stack<Map<String, String>>> symbolTBLMap = new HashMap<String, Stack<Map<String, String>>>();
    ParseTreeProperty<String> exprProperty = new ParseTreeProperty<String>();
    ParseTreeProperty<String> Stmt = new ParseTreeProperty<String>();
    ParseTreeProperty<String> ReturnStmt = new ParseTreeProperty<String>();
    int defaultIndentation = 11;
    private int labelNum = 0;

    private int depth = 0;
    private int ifDepth = 0;
    private int whileDepth = 0;
    private static boolean isFirst = true;


    @Override
    public void enterProgram(MiniCParser.ProgramContext ctx) {
        super.enterProgram(ctx);
        fileWriteUcode("", false);
        codeGen(ctx);
    }

    @Override
    public void exitProgram(MiniCParser.ProgramContext ctx) {
        while (!mainStartStack.empty()) {
            String sym = mainStartStack.peek();
            fileWriteUcode(sym, true);
            mainStartStack.pop();
        }
    }

    @Override
    public void exitStmt(MiniCParser.StmtContext ctx) {
        super.exitStmt(ctx);
        if (ctx.getChildCount() > 0) {
            if (ctx.expr_stmt() != null) {
                printExprOutOfBlock(ctx);
            } else if (ctx.return_stmt() != null) {
                printReturnOutOfBlock(ctx);
            }
            if (Stmt.get(ctx) == null) {
                if (ChildStartWith(ctx, "{")) {
                    putStatement(ctx);
                } else if (ChildStartWith(ctx, "if")) {
                    processIFStmt(ctx);
                } else if (ChildStartWith(ctx, "while")) {
                    processWHILEStmt(ctx);
                }
            }
        }
    }

    private void printReturnOutOfBlock(MiniCParser.StmtContext ctx) {
        String returnStmt = ReturnStmt.get(ctx);
        if (returnStmt != null) {
            Stmt.put(ctx, returnStmt);
        }
    }

    @Override
    public void exitReturn_stmt(MiniCParser.Return_stmtContext ctx) {
        String s = exprProperty.get(ctx.expr());
        StringBuilder ucode = new StringBuilder(s);
        ucode.append(getIndentation(defaultIndentation) + "retv" + "\n");
        ReturnStmt.put(ctx, ucode.toString());
    }


    private void printExprOutOfBlock(MiniCParser.StmtContext ctx) {
        String s = exprProperty.get(ctx.expr_stmt());
        Stmt.put(ctx, s);
    }

    private void putStatement(MiniCParser.StmtContext ctx) {
        int count = 0;
        StringBuilder sbud = new StringBuilder();
        for (int i = 0; i < ctx.getChild(0).getChildCount(); i++) {
            String t = Stmt.get(ctx.getChild(0).getChild(i));
            if (t != null) {
                count++;
                sbud.append(t);
            }
        }
        if (count > 0) {
            Stmt.put(ctx, sbud.toString());
        }
    }

    private boolean ChildStartWith(MiniCParser.StmtContext ctx, String prefix) {
        return ctx.getChild(0).getText().startsWith(prefix);
    }

    void codeGen(MiniCParser.ProgramContext ptr) {
        int globalSize;
        int offset = 1;
        StringBuilder ucode = new StringBuilder();
        for (int i = 0; i < ptr.getChildCount(); i++) {
            ParseTree child = ptr.getChild(i);
            if (global_Kind_Is(child.getText()).equals("DCL")) {
                int add_size = processDeclaration(child.getChild(0), offset, ucode);
                offset += add_size;
            } else if (global_Kind_Is(child.getText()).equals("FUNC")) {
                processFuncHeader(child.getChild(0), ucode);
            }
        }
        globalSize = offset - 1;

        ucode.append(getIndentation(defaultIndentation) + "bgn " + globalSize + "\n");
        ucode.append(getIndentation(defaultIndentation) + "ldp\n");
        ucode.append(getIndentation(defaultIndentation) + "call main\n");
        ucode.append(getIndentation(defaultIndentation) + "end\n");
        mainStartStack.push(ucode.toString());
    }

    private String global_Kind_Is(String text) {
        int func_idx = text.indexOf("(");
        if (func_idx == -1) {
            return "DCL";
        } else {
            return "FUNC";
        }
    }


    public void enterFun_decl(MiniCParser.Fun_declContext ctx) {
        depth++;
        StringBuilder func_ucode = new StringBuilder();
        String name = ctx.getChild(1).getText();
        int offset = 1;
        offset = initUcodeParamInFunc(ctx, func_ucode, name, offset);
        offset = initUcodeLocalInFunc(ctx, func_ucode, name, offset);
        String ucode = name + getIndentation(defaultIndentation - name.length()) + "proc " + (offset - 1) + " 2 " + "2\n";
        ucode += func_ucode.toString();

        fileWriteUcode(ucode, true);
    }

    private int initUcodeLocalInFunc(MiniCParser.Fun_declContext ctx, StringBuilder func_ucode, String name, int offset) {
        Stack<Map<String, String>> value_stack;
        for (MiniCParser.Local_declContext localdecl : ctx.compound_stmt().local_decl()) {
            Map<String, String> values = new HashMap<String, String>();
            String varType = localdecl.getChild(0).getText();
            String varName = localdecl.getChild(1).getText();
            values.put("scope", name);
            values.put("offset", String.valueOf(offset));
            values.put("type", varType);

            if (localdecl.getText().contains("[")) {
                offset = initUcodeLocalArray(func_ucode, offset, localdecl, values, varName);
            } else {
                offset = initUcodeLocalSimple(func_ucode, offset, values, varName);
            }
        }
        return offset;
    }

    private int initUcodeLocalSimple(StringBuilder func_ucode, int offset, Map<String, String> values, String varName) {
        Stack<Map<String, String>> value_stack;
        values.put("kind", "simpleVar");
        value_stack = symbolTBLMap.get(varName);
        if (value_stack == null) {
            value_stack = new Stack<Map<String, String>>();
        }
        value_stack.push(values);
        symbolTBLMap.put(varName, value_stack);
        func_ucode.append(getIndentation(defaultIndentation) + "sym 2 " + offset + " 1\n");
        offset++;
        return offset;
    }

    private int initUcodeLocalArray(StringBuilder func_ucode, int offset, MiniCParser.Local_declContext localdecl, Map<String, String> values, String varName) {
        Stack<Map<String, String>> value_stack;
        values.put("kind", "arrayVar");
        String array_size = localdecl.getChild(3).getText();
        values.put("array_size", array_size);
        value_stack = symbolTBLMap.get(varName);
        if (value_stack == null) {
            value_stack = new Stack<Map<String, String>>();
        }
        value_stack.push(values);
        symbolTBLMap.put(varName, value_stack);
        func_ucode.append(getIndentation(defaultIndentation) + "sym 2 " + offset + " " + array_size + "\n");
        offset += Integer.parseInt(array_size);
        return offset;
    }

    private int initUcodeParamInFunc(MiniCParser.Fun_declContext ctx, StringBuilder func_ucode, String name, int offset) {
        for (ParseTree param : ctx.params().param()) {
            Stack<Map<String, String>> param_stack = new Stack<Map<String, String>>();
            Map<String, String> values = new HashMap<String, String>();
            String paramType = param.getChild(0).getText();
            String paramName = param.getChild(1).getText();
            values.put("scope", name);
            values.put("offset", String.valueOf(offset));
            values.put("type", paramType);
            if (param.getText().contains("[")) {
                values.put("kind", "arrayVar");
            } else {
                values.put("kind", "simpleVar");
            }
            param_stack.push(values);
            symbolTBLMap.put(paramName, param_stack);
            func_ucode.append(getIndentation(defaultIndentation) + "sym 2 " + offset + " 1\n");
            offset++;
        }
        return offset;
    }

    @Override
    public void exitFun_decl(MiniCParser.Fun_declContext ctx) {

        String return_type = ctx.getChild(0).getText();
        String type = ctx.type_spec().getText();
        String name = ctx.getChild(1).getText();

        StringBuilder ucode = new StringBuilder();
        for (MiniCParser.StmtContext stmt : ctx.compound_stmt().stmt()) {
            MiniCParser.Expr_stmtContext expr = stmt.expr_stmt();
            String exprUcode = exprProperty.get(expr);
            String stmtUcode = Stmt.get(stmt);
            String retStmt = ReturnStmt.get(stmt.return_stmt());
            if (retStmt != null) {
                ucode.append(retStmt);
            } else if (exprUcode != null) {
                ucode.append(exprUcode);
            } else if (stmtUcode != null) {
                ucode.append(stmtUcode);
            }
        }
        if (return_type.equals("void")) {
            ucode.append(getIndentation(defaultIndentation) + "ret\n");
        }
        ucode.append(getIndentation(defaultIndentation) + "end\n");
        fileWriteUcode(ucode.toString(), true);
        for (String key : symbolTBLMap.keySet()) {
            Stack<Map<String, String>> tmpStack = symbolTBLMap.get(key);
            if (!tmpStack.empty())
                if (tmpStack.peek().get("scope").equals(name))
                    tmpStack.pop();
        }
        depth--;
    }

    private int processDeclaration(ParseTree child, int offset, StringBuilder ucode) {
        if (child.getChild(2).getText().equals("[")) {
            return arrayVariableDeclaration(child, offset);
        } else {
            return simpleVariableDeclaration(child, offset);
        }

    }

    private int simpleVariableDeclaration(ParseTree child, int offset) {
        Stack<Map<String, String>> stack = new Stack<Map<String, String>>();
        Map<String, String> values = new HashMap<String, String>();
        String type = child.getChild(0).getText();
        switch (type) {
            case "int":
                break;
            default:
        }
        String txt = getIndentation(defaultIndentation) + "sym 1 " + offset + " " + "1\n";
        fileWriteUcode(txt, true);
        String name = child.getChild(1).getText();
        String value = child.getChild(3).getText();
        values.put("kind", "simpleVar");
        values.put("type", type);
        values.put("scope", "global");
        values.put("value", value);
        stack.push(values);
        symbolTBLMap.put(name, stack);
        return 1;
    }

    private int arrayVariableDeclaration(ParseTree child, int offset) {
        Stack<Map<String, String>> stack = new Stack<Map<String, String>>();
        Map<String, String> values = new HashMap<String, String>();
        String type = child.getChild(0).getText();
        String name = child.getChild(1).getText();
        int array_size = Integer.parseInt(child.getChild(3).getText());
        String txt = getIndentation(defaultIndentation) + "sym 1 " + offset + " " + (offset + array_size - 1) + "\n";
        fileWriteUcode(txt, true);
        System.out.print(txt);
        values.put("kind", "arrayVar");
        values.put("type", type);
        values.put("array_size", String.valueOf(array_size));
        values.put("scope", "global");
        stack.push(values);
        symbolTBLMap.put(name, stack);
        return array_size;
    }


    private void processWHILEStmt(MiniCParser.StmtContext stmt) {

        int count = 0;
        StringBuffer sbuf = new StringBuffer("");
        sbuf.append("$$" + labelNum + getIndentation(defaultIndentation - 2 - String.valueOf(labelNum).length()) + "nop\n");

        for (int i = 0; i < stmt.getChild(0).getChildCount(); i++) {
            String ChildStmt = Stmt.get(stmt.getChild(0).getChild(i));
            String expr = exprProperty.get(stmt.getChild(0).getChild(i));
            if (expr != null) {
                count++;
                sbuf.append(expr);
                sbuf.append(getIndentation(defaultIndentation) + "fjp $$" + (labelNum + 1) + "\n");
                continue;
            }

            if (ChildStmt != null) {
                count++;
                sbuf.append(ChildStmt);
            }

        }
        sbuf.append(getIndentation(defaultIndentation) + "ujp " + "$$" + String.valueOf(labelNum) + "\n");
        sbuf.append("$$" + (labelNum + 1) + getIndentation(defaultIndentation - 2 - String.valueOf(labelNum + 1).length()) + "nop\n");

        String str = sbuf.toString();
//        if (depth == 1 && whileDepth == 0)
//            Stmt.put(stmt, str);

        if (count > 0) {
            Stmt.put(stmt, str);
        }

        labelNum += 2;
    }

    private void processIFStmt(MiniCParser.StmtContext stmt) {
        int count = 0;
        StringBuffer sbuf = new StringBuffer("");
        sbuf.append("$$" + labelNum + getIndentation(defaultIndentation - 2 - String.valueOf(labelNum).length()) + "nop\n");
        for (int i = 0; i < stmt.getChild(0).getChildCount(); i++) {
            String ChildStmt = Stmt.get(stmt.getChild(0).getChild(i));
            String expr = exprProperty.get(stmt.getChild(0).getChild(i));
            if (expr != null) {
                count++;
                sbuf.append(expr);
                sbuf.append(getIndentation(defaultIndentation) + "fjp $$" + (labelNum + 1) + "\n");
                continue;
            }
            if (ChildStmt != null) {
                count++;
                if (ChildStmt.equals("break")) {
                    sbuf.append(getIndentation(defaultIndentation) + "ujp " + "$$" + String.valueOf(labelNum + 3) + "\n");
                    continue;
                }
                sbuf.append(ChildStmt);
            }
        }
        sbuf.append("$$" + (labelNum + 1) + getIndentation(defaultIndentation - 2 - String.valueOf(labelNum + 1).length()) + "nop\n");

        String str = sbuf.toString();

        if (depth == 1 && ifDepth == 0)
            Stmt.put(stmt, str);

        if (count > 0)
            Stmt.put(stmt, str);

        labelNum = labelNum + 2;
    }

    private void processFuncHeader(ParseTree child, StringBuilder ucode) {
        Stack<Map<String, String>> stack = new Stack<Map<String, String>>();
        Map<String, String> values = new HashMap<String, String>();
        String return_type = child.getChild(0).getText();
        String name = child.getChild(1).getText();
        String params = child.getChild(3).getText();

        String[] str = params.split(",");
        values.put("kind", "func");
        values.put("return", return_type);

        StringBuilder sbuf = new StringBuilder();
        for (int i = 0; i < str.length; i++) {
            if (str[i].contains("int")) {
                sbuf.append("int,");
            } else if (str[i].contains("float")) {
                sbuf.append("float,");
            } else if (str[i].contains("double")) {
                sbuf.append("double,");
            } else if (str[i].contains("char")) {
                sbuf.append("char,");
            }
        }
        String ParamsString = sbuf.toString();
        if (ParamsString.lastIndexOf(',') == -1) {
            values.put("Params", "");
        } else {
            values.put("Params", ParamsString.substring(0, ParamsString.lastIndexOf(',')));
        }
        values.put("scope", "global");
        stack.push(values);
        symbolTBLMap.put(name, stack);
    }


    @Override
    public void exitExpr(MiniCParser.ExprContext ctx) {
        String s1 = null, s2 = null, op = null;
        StringBuilder ucode = new StringBuilder();


        if (isBinaryOperation(ctx)) {
            op = ctx.getChild(1).getText();
            if (op.equals("=")) {
                expr_binary_assign_ucode(ctx, ucode);
            } else {
                expr_binaryOP_Ucode(ctx, op);
            }
            return;
        }

        if (isIncrement(ctx)) {
            s1 = exprProperty.get(ctx.expr(0));
            ucode.append(s1).
                    append(getIndentation(defaultIndentation) + "inc" + "\n").
                    append(s1.replace("lod", "str"));

            exprProperty.put(ctx, ucode.toString());
            return;
        }

        if (isDecremetn(ctx)) {
            s1 = exprProperty.get(ctx.expr(0));
            ucode.append(s1).
                    append(getIndentation(defaultIndentation) + "dec" + "\n").
                    append(s1.replace("lod", "str"));
            ;
            exprProperty.put(ctx, ucode.toString());
            return;
        }


        if (ctx.getText().contains("(")) {
            expr_call_function_ucode(ctx, ucode);
            return;
        }

        String str = ctx.getText();

        if (str.equals("break")) {
            ucode.append(str);
            exprProperty.put(ctx, ucode.toString());
            return;
        }

        if (isInteger(str)) {
            exprConstantUcode(ctx, ucode, str);
            return;
        }
        if (str.contains("[")) {
            exprArrayUcode(ctx, ucode, str);
            return;
        }
        exprVariableUcode(ctx, ucode);
    }

    private void exprVariableUcode(MiniCParser.ExprContext ctx, StringBuilder ucode) {
        String str;
        str = symbolTBLMap.get(ctx.getText()).peek().get("offset");
        ucode.append(getIndentation(defaultIndentation) + "lod 2 " + str + "\n");
        exprProperty.put(ctx, ucode.toString());
    }

    private void exprConstantUcode(MiniCParser.ExprContext ctx, StringBuilder ucode, String str) {
        if (str.startsWith("-")) {
            str = str.substring(1);
            ucode.append(getIndentation(defaultIndentation) + "ldc " + str + "\n");
            ucode.append(getIndentation(defaultIndentation) + "neg" + "\n");
        } else {
            ucode.append(getIndentation(defaultIndentation) + "ldc " + str + "\n");
        }
        exprProperty.put(ctx, ucode.toString());
        return;
    }

    private void expr_binaryOP_Ucode(MiniCParser.ExprContext ctx, String op) {
        String s1 = exprProperty.get(ctx.expr(0));
        String s2 = exprProperty.get(ctx.expr(1));

        if (s1 != null && s2 != null) {
            String operation = null;
            operation = binaryOP_Ucode(op);
            StringBuilder result = new StringBuilder();
            result.append(s1).append(s2).append(getIndentation(defaultIndentation) + operation + "\n");
            exprProperty.put(ctx, result.toString());
        }
    }

    private void expr_binary_assign_ucode(MiniCParser.ExprContext ctx, StringBuilder ucode) {
        String s2;
        Stack<Map<String, String>> valueStack;
        Map<String, String> values = null;
        Map<String, String> values2 = null;
        s2 = exprProperty.get(ctx.expr(0));
        String varName = ctx.getChild(0).getText();
        valueStack = symbolTBLMap.get(varName);
        values = valueStack.peek();
        ucode.append(s2);
        ucode.append(getIndentation(defaultIndentation) + "str 2 " + values.get("offset") + "\n");
        exprProperty.put(ctx, ucode.toString());
    }

    private void expr_call_function_ucode(MiniCParser.ExprContext ctx, StringBuilder ucode) {
        if (ctx.args() != null) {
            ucode.append(getIndentation(defaultIndentation) + "ldp\n");
            for (String arg : ctx.args().getText().split(",")) {
                Stack<Map<String, String>> tmpStack = symbolTBLMap.get(arg);
                if (isInteger(arg)) {
                    ucode.append(getIndentation(defaultIndentation) + "ldc " + arg + "\n");
                } else {
                    ucode.append(getIndentation(defaultIndentation) + "lod 2 ");
                    ucode.append(tmpStack.peek().get("offset") + "\n");
                }
            }
            ucode.append(getIndentation(defaultIndentation) + "call " + ctx.getChild(0).getText() + "\n");
            exprProperty.put(ctx, ucode.toString());
        }
    }

    private void exprArrayUcode(MiniCParser.ExprContext ctx, StringBuilder ucode, String str) {
        if (str.contains("=")) {
            expr_array_assign(ctx, ucode, str);
            return;
        }
        expr_call_assign(ctx, ucode, str);
    }

    private void expr_call_assign(MiniCParser.ExprContext ctx, StringBuilder ucode, String str) {
        String name = str.substring(0, str.indexOf("["));
        String name_offset = symbolTBLMap.get(name).peek().get("offset");
        String index = ctx.expr(0).getText();
        String offset_index = symbolTBLMap.get(index).peek().get("offset");
        ucode.append(getIndentation(11) + "lod 2 " + offset_index + "\n");
        ucode.append(getIndentation(11) + "lod 2 " + name_offset + "\n");
        ucode.append(getIndentation(11) + "add\n");
        ucode.append(getIndentation(11) + "ldi\n");
        exprProperty.put(ctx, ucode.toString());
    }

    private void expr_array_assign(MiniCParser.ExprContext ctx, StringBuilder ucode, String str) {
        String name = str.substring(0, str.indexOf("["));
        String name_offset = symbolTBLMap.get(name).peek().get("offset");
        String index = ctx.expr(0).getText();
        String offset_index = symbolTBLMap.get(index).peek().get("offset");
        String value = ctx.expr(1).getText();
        String offset_value = symbolTBLMap.get(value).peek().get("offset");
        ucode.append(getIndentation(defaultIndentation) + "lod 2 " + offset_index + "\n");
        ucode.append(getIndentation(defaultIndentation) + "lod 2 " + name_offset + "\n");
        ucode.append(getIndentation(defaultIndentation) + "add\n");
        ucode.append(getIndentation(defaultIndentation) + "lod 2 " + offset_value + "\n");
        ucode.append(getIndentation(defaultIndentation) + "sti\n");
        exprProperty.put(ctx, ucode.toString());
    }


    @Override
    public void exitExpr_stmt(MiniCParser.Expr_stmtContext ctx) {
        MiniCParser.ExprContext px = ctx.expr();
        String s = exprProperty.get(px);
        exprProperty.put(ctx, s);
    }


    @Override
    public void enterIf_stmt(MiniCParser.If_stmtContext ctx) {
        ifDepth++;
        depth++;
        if (isFirst) {
            isFirst = false;
        }
    }

    @Override
    public void exitIf_stmt(MiniCParser.If_stmtContext ctx) {
        if (ifDepth == 1) {
            isFirst = true;
        }
        depth--;
        ifDepth--;
    }

    @Override
    public void enterWhile_stmt(MiniCParser.While_stmtContext ctx) {
        if (isFirst) {
            isFirst = false;
        }
        depth++;
        whileDepth++;
    }

    @Override
    public void exitWhile_stmt(MiniCParser.While_stmtContext ctx) {
        if (whileDepth == 1) {
            isFirst = true;
        }
        depth--;
        whileDepth--;
    }

    private StringBuilder getIndentation(int indent) {
        StringBuilder indentation = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            indentation.append(" ");
        }
        return indentation;
    }

    // 아래는 보조 메소드이다.
    boolean isBinaryOperation(MiniCParser.ExprContext ctx) {
        return ctx.getChildCount() == 3
                && ctx.getChild(1) != ctx.expr();
        // 자식 3개짜리 expr 중 ‘(‘ expr ’)’를 배제
    }

    private void fileWriteUcode(String txt, Boolean dontReWrite) {
        System.out.print(txt);
        String fileName = "out.txt";
        try {
            BufferedWriter fw = new BufferedWriter(new FileWriter(fileName, dontReWrite));
            fw.write(txt);
            fw.flush();
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isDecremetn(MiniCParser.ExprContext ctx) {
        return ctx.getChildCount() == 2 && ctx.getText().contains("--");
    }

    private boolean isIncrement(MiniCParser.ExprContext ctx) {
        return ctx.getChildCount() == 2 && ctx.getText().contains("++");
    }

    private boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String binaryOP_Ucode(String op) {
        switch (op) {
            case "+":
                return "add";
            case "-":
                return "sub";
            case "*":
                return "mul";
            case "/":
                return "div";
            case ">":
                return "gt";
            case "<":
                return "lt";
            case ">=":
                return "ge";
            case "<=":
                return "le";
            case "==":
                return "eq";
            case "!=":
                return "ne";
        }
        return "";
    }
}