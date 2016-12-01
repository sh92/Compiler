import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.*;

public class MiniCPrintListener extends MiniCBaseListener{

    ParseTreeProperty<String> exprProperty = new ParseTreeProperty<String>();
    ParseTreeProperty<String> Params = new ParseTreeProperty<String>();
    ParseTreeProperty<String> Stmt = new ParseTreeProperty<String>();
    ParseTreeProperty<String> ReturnStmt = new ParseTreeProperty<String>();
    private int depth=0;

    private int ifDepth=0;
    private int whileDepth=0;
    private static String isFirstBlockType="";
    private static boolean isFirst=true;

    @Override public void enterIf_stmt(MiniCParser.If_stmtContext ctx) {
        ifDepth++;
    }

    private StringBuffer getIndentation() {
        StringBuffer indentation=new StringBuffer();
        for(int i =0;i<depth;i++){
            indentation.append("....");
        }
        return indentation;
    }

    @Override public void exitIf_stmt(MiniCParser.If_stmtContext ctx) {
        if(isFirst) {
            isFirstBlockType = "if";
            isFirst=false;
        }
        ifDepth--;
    }

    @Override public void exitStmt(MiniCParser.StmtContext ctx) {
        if(ctx.expr_stmt()!=null) {
            printExprOutOfBlock(ctx);
        }else{
            printReturnOutOfBlock(ctx);
        }
        if(Stmt.get(ctx)==null){
            if(ChildStartWith(ctx, "{")) {
                putStatement(ctx);
            }else if(ChildStartWith(ctx, "if")){
                putIfStatement(ctx);
            }else  if(ChildStartWith(ctx, "while")) {
                putWhileStatement(ctx);
            }
        }
    }

    private boolean ChildStartWith(MiniCParser.StmtContext ctx, String prefix) {
        return ctx.getChild(0).getText().startsWith(prefix);
    }

    private void putWhileStatement(MiniCParser.StmtContext ctx) {
        int count = 0;
        StringBuffer sbuf = new StringBuffer("");
        boolean makeBracelet=false;
        int index =-1;
        for (int i = 0; i < ctx.getChild(0).getChildCount(); i++) {
            String text = ctx.getChild(0).getChild(i).getText();

            String ChildStmt = Stmt.get(ctx.getChild(0).getChild(i));
            String exponential = exprProperty.get(ctx.getChild(0).getChild(i));
            if(exponential!=null){
                count++;
                sbuf.append(exponential);
            }else{
                if (ChildStmt != null) {
                    count++;
                    sbuf.append(ChildStmt);
                }else {
                    if(text.equals("while"))
                        sbuf.append("while ");
                    else if(text.equals(")")) {
                        String t_next = ctx.getChild(0).getChild(i+1).getText();
                        sbuf.append(text + "\n");
                        if(!t_next.startsWith("{")){
                            makeBracelet=true;
                            index=i+1;
                            break;
                        }
                    }
                    else
                        sbuf.append(text);
                }
            }
        }
        StringBuffer makeStmt;
        if(makeBracelet) {
            int count2 = 0;
            int backup=depth;
            while (depth<ifDepth+whileDepth)
                depth++;

            depth++;
            makeStmt= new StringBuffer(getIndentation().toString()+"{\n");
            depth++;
            for (int i = index; i < ctx.getChild(0).getChildCount(); i++) {
                String t = Stmt.get(ctx.getChild(0).getChild(i));

                if (t != null) {
                    count2++;
                    makeStmt.append(getIndentation().toString());
                    if(t.startsWith("if") || t.startsWith("while")) {
                        makeStmt.append(t+"\n");
                    }else if(t.startsWith("return")){
                        makeStmt.append(t+"\n");
                    }
                    else{
                        makeStmt.append(t+";\n");
                    }
                }
            }
            depth--;
            makeStmt.append( getIndentation().toString()+"}");
            depth=backup;

            if (count2 > 0) {
                sbuf.append(makeStmt.toString());
            }
        }
        String s2 = sbuf.toString();
        if(depth==1 && whileDepth==0 && isFirstBlockType.equals("while")) {
            System.out.println(getIndentation() + s2);
        }
        if (count > 0) {
            Stmt.put(ctx, s2);
        }
    }

    private void putIfStatement(MiniCParser.StmtContext ctx) {
        int count = 0;
        StringBuffer sbuf = new StringBuffer("");
        boolean makeBracelet=false;
        int index =-1;


        for (int i = 0; i < ctx.getChild(0).getChildCount(); i++) {
            String text = ctx.getChild(0).getChild(i).getText();
            String t = Stmt.get(ctx.getChild(0).getChild(i));
            String exp = exprProperty.get(ctx.getChild(0).getChild(i));
            if(exp!=null){
                count++;
                sbuf.append(exp);
            }else{
                if (t != null) {
                    count++;
                    sbuf.append(t);
                }else {
                    if(text.equals("if")) {
                        sbuf.append("if ");
                    }
                    else if(text.equals(")")) {
                        String t_next = ctx.getChild(0).getChild(i+1).getText();
                        sbuf.append(text + "\n");
                        if(!t_next.startsWith("{")){
                            makeBracelet=true;
                            index=i+1;
                            break;
                        }
                    }
                    else
                        sbuf.append(text);
                }
            }
        }
        StringBuffer makeStmt;

        if(makeBracelet) {
            int count2 = 0;
            int backup=depth;
            while (depth<ifDepth+whileDepth)
                depth++;

            depth++;
            makeStmt= new StringBuffer(getIndentation().toString()+"{\n");
            depth++;
            for (int i = index; i < ctx.getChild(0).getChildCount(); i++) {
                String t = Stmt.get(ctx.getChild(0).getChild(i));

                if (t != null) {
                    count2++;
                    makeStmt.append(getIndentation().toString());
                    if(t.startsWith("if") || t.startsWith("while")) {
                        makeStmt.append(t+"\n");
                    }else if(t.startsWith("return")){
                        makeStmt.append(t+"\n");
                    }
                    else{
                        makeStmt.append(t+";\n");
                    }
                }
            }
            depth--;
            makeStmt.append( getIndentation().toString()+"}");
            depth=backup;

            if (count2 > 0) {
                sbuf.append(makeStmt.toString());
            }

        }

        String s2 = sbuf.toString();
        if(depth==1 && ifDepth==0 && isFirstBlockType.equals("if"))
            System.out.println(getIndentation()+s2 );
        if (count > 0) {
            Stmt.put(ctx, s2);
        }
    }


    private void putStatement(MiniCParser.StmtContext ctx) {
        int count = 0;
        int backup=depth;
        while (depth<ifDepth+whileDepth)
            depth++;

        StringBuffer sbuf = new StringBuffer(getIndentation().toString()+"{\n");
        depth++;
        for (int i = 0; i < ctx.getChild(0).getChildCount(); i++) {
            String t = Stmt.get(ctx.getChild(0).getChild(i));
            if (t != null) {
                count++;
                sbuf.append(getIndentation().toString());
                if(t.startsWith("if") || t.startsWith("while")) {
                    sbuf.append(t+"\n");
                }else if(t.startsWith("return")){
                    sbuf.append(t+"\n");
                }
                else{
                    sbuf.append(t+";\n");
                }
            }
        }
        depth--;
        sbuf.append( getIndentation().toString()+"}");
        depth=backup;

        if (count > 0) {
            Stmt.put(ctx, sbuf.toString());
        }
    }

    private void printReturnOutOfBlock(MiniCParser.StmtContext ctx) {
        String returnStmt = ReturnStmt.get(ctx);
        if(returnStmt!=null){
            Stmt.put(ctx, returnStmt);
            if(ifDepth==0 && whileDepth==0 && depth==1){
                System.out.println(getIndentation()+returnStmt);
            }
        }
    }

    private void printExprOutOfBlock(MiniCParser.StmtContext ctx) {
        String s=exprProperty.get(ctx.expr_stmt());
        if (ifDepth == 0 && whileDepth == 0 && depth == 1 )
            System.out.println(getIndentation().toString() + s + ";");
        Stmt.put(ctx,s);
    }

    // 아래는 보조 메소드이다.
    boolean isBinaryOperation(MiniCParser.ExprContext ctx){
        return ctx.getChildCount() == 3
                && ctx.getChild(1) != ctx.expr();
        // 자식 3개짜리 expr 중 ‘(‘ expr ’)’를 배제
    }
    @Override
    public void exitExpr(MiniCParser.ExprContext ctx) {
        String s1 = null, s2 = null, op = null;
        if (isBinaryOperation(ctx)) {
            // 예: expr ‘+’ expr
            s1 = exprProperty.get(ctx.expr(0));
            s2 = exprProperty.get(ctx.expr(1));
            op = ctx.getChild(1).getText();
            if(op.equals("=")){
                exprProperty.put(ctx, ctx.getChild(0).getText()+" "+op+" "+s1);
            }else {
                exprProperty.put(ctx, s1 + " " + op + " " + s2);
            }
        }else {
            exprProperty.put(ctx,ctx.getText());
        }
    }


    @Override public void exitVar_decl(MiniCParser.Var_declContext ctx) {
        int i;
        for(i=0;i<ctx.getChildCount()-2;i++){
            System.out.print(ctx.getChild(i).getText()+" ");
        }
        System.out.print(ctx.getChild(i));
        i++;
        System.out.print(ctx.getChild(i));
        System.out.println();
    }
    @Override public void enterFun_decl(MiniCParser.Fun_declContext ctx) {
        System.out.print(ctx.getChild(0).getText()+" "+ctx.getChild(1));
    }
    @Override public void exitFun_decl(MiniCParser.Fun_declContext ctx) {
        System.out.println("}");
        depth--;
    }
    @Override public void enterParams(MiniCParser.ParamsContext ctx) {
        System.out.print("(");
    }
    @Override public void exitParams(MiniCParser.ParamsContext ctx) {
        int i;
        String s;
        for(i =0;i<ctx.param().size()-1;i++){
            s=Params.get(ctx.param(i));
            if(s!=null)
                System.out.print(Params.get(ctx.param(i))+", ");
        }
        s=Params.get(ctx.param(i));
        if(s!=null)
            System.out.print(s);
        System.out.println(")\n{");
        depth++;
    }
    @Override public void exitParam(MiniCParser.ParamContext ctx) {

        StringBuilder sbd = new StringBuilder();
        for(ParseTree pst: ctx.children){
            sbd.append(pst.getText()+" ");
        }
        sbd.delete(sbd.length()-1,sbd.length());
        Params.put(ctx,sbd.toString());
    }
    @Override public void exitExpr_stmt(MiniCParser.Expr_stmtContext ctx) {
        MiniCParser.ExprContext px = ctx.expr();
        String s= exprProperty.get(px);
        exprProperty.put(ctx,s);
    }
    @Override public void enterWhile_stmt(MiniCParser.While_stmtContext ctx) {
        if(isFirst) {
            isFirstBlockType = "while";
            isFirst=false;
        }
        whileDepth++;
    }
    @Override public void exitWhile_stmt(MiniCParser.While_stmtContext ctx) {
        whileDepth--;
    }

    @Override public void exitLocal_decl(MiniCParser.Local_declContext ctx) {
        StringBuffer indentation = getIndentation();
        int i;
        for( i =0;i<ctx.getChildCount()-2;i++){
            System.out.print(indentation.toString()+ctx.getChild(i).getText()+" ");
        }
        System.out.print(ctx.getChild(i));
        i++;
        System.out.println(ctx.getChild(i));
    }

    @Override public void exitReturn_stmt(MiniCParser.Return_stmtContext ctx) {
        ParserRuleContext psr = ctx.getParent();
        ReturnStmt.put(psr,ctx.getChild(0)+" "+ctx.expr().getText()+ctx.getChild(2));
    }

}
