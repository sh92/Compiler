import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.*;

public class UCodeGenListener extends MiniCBaseListener {

    StringBuilder mainStartUcode = new StringBuilder();
    Map<String, Stack<Map<String, String>>> symbolTBLMap = new HashMap<String, Stack<Map<String, String>>>();
    ParseTreeProperty<String> exprProperty = new ParseTreeProperty<String>();
    ParseTreeProperty<String> Stmt = new ParseTreeProperty<String>();
    ParseTreeProperty<String> ReturnStmt = new ParseTreeProperty<String>();
    int defaultIndentation = 11;
    private int labelNum = 0;
    Stack<Integer> exitParentWhileLabel = new Stack<Integer>();


    @Override
    public void enterProgram(MiniCParser.ProgramContext ctx) {
        super.enterProgram(ctx);
        fileWriteUcode("", false);
        codeGen(ctx);
    }

    @Override
    public void exitProgram(MiniCParser.ProgramContext ctx) {
        fileWriteUcode(mainStartUcode.toString(), true);
    }

    @Override
    public void enterWhile_stmt(MiniCParser.While_stmtContext ctx) {
        int count = labelNum;
        for (ParseTree pst : ctx.stmt().children) {
            String ifList[] = pst.getText().split("if");
            for (String s : ifList) {
                if (s.startsWith("(")) {
                    count += 2;
                }
            }
            String whileList[] = pst.getText().split("while");
            for (String s : whileList) {
                if (s.startsWith("(")) {
                    count += 2;
                }
            }
        }
        exitParentWhileLabel.push(count + 1);
    }


    @Override
    public void exitWhile_stmt(MiniCParser.While_stmtContext ctx) {
        exitParentWhileLabel.pop();
    }


    @Override
    public void enterStmt(MiniCParser.StmtContext ctx) {
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

        for (int i = 0; i < ptr.getChildCount(); i++) {
            ParseTree child = ptr.getChild(i);
            if (global_Kind_Is(child.getText()).equals("DCL")) {
                int add_size = processDeclaration(child.getChild(0), offset);
                offset += add_size;
            } else if (global_Kind_Is(child.getText()).equals("FUNC")) {
                processFuncHeader(child.getChild(0));
            }
        }
        globalSize = offset - 1;
        StringBuilder ucode = new StringBuilder();
        ucode.append(getIndentation(defaultIndentation) + "bgn\t" + globalSize + "\n");
        ucode.append(getIndentation(defaultIndentation) + "ldp\n");
        ucode.append(getIndentation(defaultIndentation) + "call\tmain\n");
        ucode.append(getIndentation(defaultIndentation) + "end\n");
        mainStartUcode.append(ucode.toString());
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
        StringBuilder func_ucode = new StringBuilder();
        String name = ctx.getChild(1).getText();
        int offset = 1;
        offset = initUcodeParamInFunc(ctx, func_ucode, name, offset);
        offset = initUcodeLocalInFunc(ctx, func_ucode, name, offset);
        String ucode = name + getIndentation(defaultIndentation - name.length()) + "proc\t" + (offset - 1) + " 2 " + "2\n";
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
        func_ucode.append(getIndentation(defaultIndentation) + "sym\t2 " + offset + " 1\n");
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
        func_ucode.append(getIndentation(defaultIndentation) + "sym\t2 " + offset + " " + array_size + "\n");
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
            func_ucode.append(getIndentation(defaultIndentation) + "sym\t2 " + offset + " 1\n");
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
    }

    private int processDeclaration(ParseTree child, int offset) {
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


        String name = child.getChild(1).getText();
        String value;
        if(child.getChild(3)!=null){
            value = child.getChild(3).getText();
            StringBuilder txt =new StringBuilder();
            txt.append(getIndentation(defaultIndentation) + "sym\t1 " + offset + " " + "1\n");
            mainStartUcode.append(getIndentation(defaultIndentation) + "ldc\t" + value+"\n");
            mainStartUcode.append(getIndentation(defaultIndentation) + "str\t1 " + offset+"\n");
            fileWriteUcode(txt.toString(), true);
            values.put("offset", Integer.toString(offset));
            values.put("kind", "simpleVar");
            values.put("type", type);
            values.put("scope", "global");
            values.put("value", value);
            stack.push(values);
            symbolTBLMap.put(name, stack);
            return 1;
        }else{
            StringBuilder txt =new StringBuilder();
            txt.append(getIndentation(defaultIndentation) + "sym\t1 " + offset + " " + "1\n");
            fileWriteUcode(txt.toString(), true);
            values.put("offset", Integer.toString(offset));
            values.put("kind", "simpleVar");
            values.put("type", type);
            values.put("scope", "global");
            stack.push(values);
            symbolTBLMap.put(name, stack);
            return 1;
        }
    }

    private int arrayVariableDeclaration(ParseTree child, int offset) {
        Stack<Map<String, String>> stack = new Stack<Map<String, String>>();
        Map<String, String> values = new HashMap<String, String>();
        String type = child.getChild(0).getText();
        String name = child.getChild(1).getText();
        int array_size = Integer.parseInt(child.getChild(3).getText());

        String txt = getIndentation(defaultIndentation) + "sym\t1 " + offset + " " + (offset + array_size - 1) + "\n";
        fileWriteUcode(txt, true);
        values.put("kind", "arrayVar");
        values.put("offset", Integer.toString(offset));
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
                if (expr.equals("break")) {
                    sbuf.append(getIndentation(defaultIndentation) + "ujp\t" + "$$" + String.valueOf(exitParentWhileLabel.peek()) + "\n");
                    continue;
                }
                sbuf.append(expr);
                sbuf.append(getIndentation(defaultIndentation) + "fjp\t$$" + (labelNum + 1) + "\n");
                continue;
            }

            if (ChildStmt != null) {
                count++;
                if (ChildStmt.equals("break")) {
                    sbuf.append(getIndentation(defaultIndentation) + "ujp\t" + "$$" + String.valueOf(exitParentWhileLabel.peek()) + "\n");
                    continue;
                }
                sbuf.append(ChildStmt);
            }

        }
        sbuf.append(getIndentation(defaultIndentation) + "ujp\t" + "$$" + String.valueOf(labelNum) + "\n");
        sbuf.append("$$" + (labelNum + 1) + getIndentation(defaultIndentation - 2 - String.valueOf(labelNum + 1).length()) + "nop\n");

        String str = sbuf.toString();

        if (count > 0) {
            Stmt.put(stmt, str);
        }

        labelNum += 2;
    }

    private void processIFStmt(MiniCParser.StmtContext stmt) {
        StringBuffer sbuf = new StringBuffer("");
        sbuf.append("$$" + labelNum + getIndentation(defaultIndentation - 2 - String.valueOf(labelNum).length()) + "nop\n");

        if(stmt.getText().contains("else")){
            String condition =exprProperty.get((stmt.getChild(0).getChild(2)));
            if(condition!=null){
                sbuf.append(condition);
                sbuf.append(getIndentation(defaultIndentation) + "tjp\t$$if" + (labelNum) + "\n");
                sbuf.append(getIndentation(defaultIndentation) + "ujp\t$$else" + (labelNum) + "\n");
            }
            sbuf.append("$$if"+(labelNum) + getIndentation(defaultIndentation - 4-Integer.toString(labelNum).length() ) + "nop\n");
            String stmtIfTrue = Stmt.get(stmt.getChild(0).getChild(4));
            String exprStmtIfTrue= exprProperty.get(stmt.getChild(0).getChild(4));

            if (exprStmtIfTrue != null) {
                sbuf.append(exprStmtIfTrue);
            }else if (stmtIfTrue != null) {
                if (stmtIfTrue.equals("break")) {
                    sbuf.append(getIndentation(defaultIndentation) + "ujp\t" + "$$" + String.valueOf(exitParentWhileLabel.peek()) + "\n");
                }else {
                    sbuf.append(stmtIfTrue);
                }
            }
            sbuf.append(getIndentation(defaultIndentation) + "ujp\t$$" + (labelNum+1) + "\n");

            sbuf.append("$$else"+(labelNum) + getIndentation(defaultIndentation - 6-Integer.toString(labelNum).length() ) + "nop\n");
            String stmtElse = Stmt.get(stmt.getChild(0).getChild(6));
            String exprStmtElse= exprProperty.get(stmt.getChild(0).getChild(6));


            if (exprStmtElse != null) {
                sbuf.append(exprStmtIfTrue);
            }else if (stmtElse != null) {
                if (stmtElse.equals("break")) {
                    sbuf.append(getIndentation(defaultIndentation) + "ujp\t" + "$$" + String.valueOf(exitParentWhileLabel.peek()) + "\n");
                }else {
                    sbuf.append(stmtElse);
                }
            }


            sbuf.append("$$" + (labelNum + 1) + getIndentation(defaultIndentation - 2 - String.valueOf(labelNum + 1).length()) + "nop\n");

            labelNum += 2;

        }else {
            for (int i = 0; i < stmt.getChild(0).getChildCount(); i++) {
                String ChildStmt = Stmt.get(stmt.getChild(0).getChild(i));
                String expr = exprProperty.get(stmt.getChild(0).getChild(i));
                if (expr != null) {
                    sbuf.append(expr);
                    sbuf.append(getIndentation(defaultIndentation) + "fjp\t$$" + (labelNum + 1) + "\n");
                    continue;
                }



                if (ChildStmt != null) {
                    if (ChildStmt.equals("break")) {
                        sbuf.append(getIndentation(defaultIndentation) + "ujp\t" + "$$" + String.valueOf(exitParentWhileLabel.peek()) + "\n");
                        continue;
                    }
                    sbuf.append(ChildStmt);
                }
            }

            sbuf.append("$$" + (labelNum + 1) + getIndentation(defaultIndentation - 2 - String.valueOf(labelNum + 1).length()) + "nop\n");
            labelNum += 2;

        }

        Stmt.put(stmt, sbuf.toString());


    }

    private void processFuncHeader(ParseTree child) {
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
                expr_binary_assign_ucode(ctx);
            } else {
                expr_binaryOP_Ucode(ctx, op);
            }
            return;
        }

        if (isIncrement(ctx)) {
            if (ctx.expr(0) != null) {
                s1 = exprProperty.get(ctx.expr(0));
                ucode.append(s1).
                        append(getIndentation(defaultIndentation) + "inc" + "\n").
                        append(s1.replace("lod", "str"));
            } else {
                String s = ctx.getText();
                s = s.replace("++", "");
                String offset = symbolTBLMap.get(s).peek().get("offset");
                ucode.append(getIndentation(defaultIndentation) + "lod\t"+getScope(s)+" " + offset + "\n");
                ucode.append(getIndentation(defaultIndentation) + "inc\t\n");
                ucode.append(getIndentation(defaultIndentation) + "str\t"+getScope(s)+" " + offset + "\n");
            }

            exprProperty.put(ctx, ucode.toString());
            return;
        }

        if (isDecremetn(ctx)) {
            if (ctx.expr(0) != null) {
                s1 = exprProperty.get(ctx.expr(0));
                ucode.append(s1).
                        append(getIndentation(defaultIndentation) + "dec" + "\n").
                        append(s1.replace("lod", "str"));
            } else {
                String s = ctx.getText();
                s = s.replace("--", "");
                String offset = symbolTBLMap.get(s).peek().get("offset");
                ucode.append(getIndentation(defaultIndentation) + "lod\t"+getScope(s)+" " + offset + "\n");
                ucode.append(getIndentation(defaultIndentation) + "dec\t\n");
                ucode.append(getIndentation(defaultIndentation) + "str\t"+getScope(s)+" " + offset + "\n");
            }


            exprProperty.put(ctx, ucode.toString());
            return;
        }
        if (isUniaryOperator(ctx, ucode, ctx.getText())) {
            return;
        }

        if (isInteger(ctx.getText())) {
            String str = ctx.getText();
            if (str.startsWith("-")) {
                str = str.substring(1);
                ucode.append(getIndentation(defaultIndentation) + "ldc\t" + str + "\n");
                ucode.append(getIndentation(defaultIndentation) + "neg" + "\n");
                exprProperty.put(ctx, ucode.toString());
                return;
            }
            ucode.append(getIndentation(defaultIndentation) + "ldc\t" + str + "\n");
            exprProperty.put(ctx, ucode.toString());
            return;
        }

        String str = ctx.getText();
        if (ctx.getText().contains("(")) {
            expr_call_function_ucode(ctx, ucode);
            return;
        }


        if (str.equals("break")) {
            ucode.append(str);
            exprProperty.put(ctx, ucode.toString());
            return;
        }

        if (str.contains("[")) {
            exprArrayVariableUcode(ctx, ucode, str);
            return;
        }

        exprVariableUcode(ctx, ucode);
    }



    private void exprVariableUcode(MiniCParser.ExprContext ctx, StringBuilder ucode) {
        String str = symbolTBLMap.get(ctx.getText()).peek().get("offset");
        String scope = symbolTBLMap.get(ctx.getText()).peek().get("scope");
        ucode.append(getIndentation(defaultIndentation) + "lod\t"+getScope(ctx.getText())+" " + str + "\n");
        exprProperty.put(ctx, ucode.toString());
    }

    private boolean isUniaryOperator(MiniCParser.ExprContext ctx, StringBuilder ucode, String str) {
        if (str.startsWith("!")) {
            str = str.substring(1);
            String s1 = exprProperty.get(ctx.expr(0));
            if (s1 != null) {
                ucode.append(s1 + getIndentation(defaultIndentation) + "notop\n");
                ucode.append(getIndentation(defaultIndentation) + "ldc\t1\n");
                ucode.append(getIndentation(defaultIndentation) + "eq\n");
                exprProperty.put(ctx, ucode.toString());
            } else if (isInteger(str)) {
                ucode.append(getIndentation(defaultIndentation) + "ldc\t" + str + "\n");
                ucode.append(getIndentation(defaultIndentation) + "notop\n");
                ucode.append(getIndentation(defaultIndentation) + "ldc\t1\n");
                ucode.append(getIndentation(defaultIndentation) + "eq\n");
                exprProperty.put(ctx, ucode.toString());
            } else {
                String sym_offset = symbolTBLMap.get(str).peek().get("offset");
                ucode.append(getIndentation(defaultIndentation) + "lod\t"+getScope(str)+" " + sym_offset + "\n");
                ucode.append(getIndentation(defaultIndentation) + "notop\n");
                ucode.append(getIndentation(defaultIndentation) + "ldc\t1\n");
                ucode.append(getIndentation(defaultIndentation) + "eq\n");
                exprProperty.put(ctx, ucode.toString());
            }
            return true;
        }
        return false;
    }

    private void expr_binaryOP_Ucode(MiniCParser.ExprContext ctx, String op) {
        String s1 = exprProperty.get(ctx.expr(0));
        String s2 = exprProperty.get(ctx.expr(1));

        if (s1 != null && s2 != null) {
            String operation = binaryOP_Ucode(op);
            StringBuilder result = new StringBuilder();
            result.append(s1).append(s2).append(getIndentation(defaultIndentation) + operation + "\n");
            exprProperty.put(ctx, result.toString());
        }
    }

    private void expr_binary_assign_ucode(MiniCParser.ExprContext ctx) {
        String varName = ctx.getChild(0).getText();
        String s2 = exprProperty.get(ctx.expr(0));
        Stack<Map<String, String>> valueStack = symbolTBLMap.get(varName);
        Map<String, String> values = valueStack.peek();
        StringBuilder appendedUcode = new StringBuilder();
        String expr0 = ctx.expr(0).getText();
        if (expr0.startsWith("++") ||expr0.startsWith("--")) {
            expr0 = expr0.replace("++", "");
            appendedUcode.append(s2);
            appendedUcode.append(getIndentation(defaultIndentation) + "lod\t"+getScope(expr0)+" " + symbolTBLMap.get(expr0).peek().get("offset") + "\n");
            appendedUcode.append(getIndentation(defaultIndentation) + "str\t"+getScope(expr0)+" " + values.get("offset") + "\n");
        } else if (expr0.contains("++")) {
                expr0 = expr0.replace("++", "");
                if (isInteger(expr0)) {
                    appendedUcode.append(getIndentation(defaultIndentation) + "ldc\t" + expr0+"\n");
                    appendedUcode.append(getIndentation(defaultIndentation) + "str\t"+getScope(varName)+" " + values.get("offset") + "\n");
                } else {
                    appendedUcode.append(getIndentation(defaultIndentation) + "lod\t"+getScope(expr0)+" "+ symbolTBLMap.get(expr0).peek().get("offset") + "\n");
                    appendedUcode.append(getIndentation(defaultIndentation) + "str\t"+getScope(varName)+" " + values.get("offset") + "\n");
                    appendedUcode.append(getIndentation(defaultIndentation) + "lod\t"+getScope(expr0)+" " + symbolTBLMap.get(expr0).peek().get("offset") + "\n");
                    appendedUcode.append(getIndentation(defaultIndentation) + "inc\t\n");
                    appendedUcode.append(getIndentation(defaultIndentation) + "str\t"+getScope(expr0)+" " + symbolTBLMap.get(expr0).peek().get("offset") + "\n");
                }
        }else if (expr0.contains("--")) {
                expr0 = expr0.replace("--", "");

                if (isInteger(expr0)) {
                    appendedUcode.append(getIndentation(defaultIndentation) + "ldc\t" + expr0+"\n");
                    appendedUcode.append(getIndentation(defaultIndentation) + "str\t"+getScope(expr0)+" " + values.get("offset") + "\n");
                } else {
                    appendedUcode.append(getIndentation(defaultIndentation) + "lod\t"+getScope(expr0)+" " + symbolTBLMap.get(expr0).peek().get("offset") + "\n");
                    appendedUcode.append(getIndentation(defaultIndentation) + "str\t"+getScope(varName)+" " + values.get("offset") + "\n");
                    appendedUcode.append(getIndentation(defaultIndentation) + "lod\t"+getScope(expr0)+" " + symbolTBLMap.get(expr0).peek().get("offset") + "\n");
                    appendedUcode.append(getIndentation(defaultIndentation) + "dec\t\n");
                    appendedUcode.append(getIndentation(defaultIndentation) + "str\t"+getScope(varName)+" " + values.get("offset") + "\n");
                }

        }else{
            appendedUcode.append(s2);
            appendedUcode.append(getIndentation(defaultIndentation) + "str\t"+getScope(varName)+" " + values.get("offset") + "\n");
        }
        exprProperty.put(ctx, appendedUcode.toString());
    }

    private void expr_call_function_ucode(MiniCParser.ExprContext ctx, StringBuilder ucode) {
        if (ctx.args() != null) {
            ucode.append(getIndentation(defaultIndentation) + "ldp\n");
            for(MiniCParser.ExprContext expr :ctx.args().expr()){
                String arg = expr.getText();
                Stack<Map<String, String>> tmpStack = symbolTBLMap.get(arg);
                if (isInteger(arg)) {
                    ucode.append(getIndentation(defaultIndentation) + "ldc\t" + arg + "\n");
                } else if (expr!=null) {
                    String s =exprProperty.get(expr);
                    ucode.append(s);
                }else {

                    if (tmpStack.peek().get("kind").equals("arrayVar")) {
                        ucode.append(getIndentation(defaultIndentation) + "lda\t"+getScope(arg)+" ");
                    } else if (tmpStack.peek().get("kind").equals("simpleVar")) {
                        ucode.append(getIndentation(defaultIndentation) + "lod\t"+getScope(arg)+" ");
                    }
                    ucode.append(tmpStack.peek().get("offset") + "\n");
                }
            }

            ucode.append(getIndentation(defaultIndentation) + "call\t" + ctx.getChild(0).getText() + "\n");
            exprProperty.put(ctx, ucode.toString());
        }
    }

    private void exprArrayVariableUcode(MiniCParser.ExprContext ctx, StringBuilder ucode, String str) {
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
        if (isInteger(index)) {
            ucode.append(getIndentation(defaultIndentation) + "ldc\t" + index + "\n");
            ucode.append(getIndentation(defaultIndentation) + "lod\t"+ getScope(name)+" "+ name_offset + "\n");
            ucode.append(getIndentation(defaultIndentation) + "add\n");
            ucode.append(getIndentation(defaultIndentation) + "ldi\n");
            exprProperty.put(ctx, ucode.toString());
        } else {

            String offset_index = symbolTBLMap.get(index).peek().get("offset");
            ucode.append(getIndentation(defaultIndentation) + "lod\t"+ getScope(index)+" " + offset_index + "\n");
            ucode.append(getIndentation(defaultIndentation) + "lod\t"+ getScope(name)+" " + name_offset + "\n");
            ucode.append(getIndentation(defaultIndentation) + "add\n");
            ucode.append(getIndentation(defaultIndentation) + "ldi\n");
            exprProperty.put(ctx, ucode.toString());
        }

    }

    private String getScope(String name) {
        String scope = symbolTBLMap.get(name).peek().get("scope");
        if(scope.equals("global")){
            return "1";
        }else {
            return "2";
        }
    }

    private void expr_array_assign(MiniCParser.ExprContext ctx, StringBuilder ucode, String str) {
        String name = str.substring(0, str.indexOf("["));
        String name_offset = symbolTBLMap.get(name).peek().get("offset");
        String index = ctx.expr(0).getText();
        if (isInteger(index)) {
            ucode.append(getIndentation(defaultIndentation) + "ldc\t" + index + "\n");
            ucode.append(getIndentation(defaultIndentation) + "lod\t"+getScope(name)+" " + name_offset + "\n");
            ucode.append(getIndentation(defaultIndentation) + "add\n");
        } else {
            String offset_index = symbolTBLMap.get(index).peek().get("offset");
            ucode.append(getIndentation(defaultIndentation) + "lod\t"+getScope(index)+" " + offset_index + "\n");
            ucode.append(getIndentation(defaultIndentation) + "lod\t"+getScope(name)+" " + name_offset + "\n");
            ucode.append(getIndentation(defaultIndentation) + "add\n");
        }
        String value = ctx.expr(1).getText();
        if (isInteger(value)) {
            ucode.append(getIndentation(defaultIndentation) + "ldc\t" + value + "\n");
            ucode.append(getIndentation(defaultIndentation) + "sti\n");
            exprProperty.put(ctx, ucode.toString());
        } else {
            String offset_value = symbolTBLMap.get(value).peek().get("offset");
            ucode.append(getIndentation(defaultIndentation) + "lod\t"+getScope(value)+" " + offset_value + "\n");
            ucode.append(getIndentation(defaultIndentation) + "sti\n");
            exprProperty.put(ctx, ucode.toString());
        }
    }


    @Override
    public void exitExpr_stmt(MiniCParser.Expr_stmtContext ctx) {
        MiniCParser.ExprContext px = ctx.expr();
        String s = exprProperty.get(px);
        exprProperty.put(ctx, s);
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
                return "mult";
            case "/":
                return "div";
            case "%":
                return "mod";
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