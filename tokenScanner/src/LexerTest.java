import sun.jvm.hotspot.debugger.cdbg.Sym;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * Created by withGod on 9/8/16.
 */
public class LexerTest {
    public static void main(String[] args) throws FileNotFoundException {
        FileInputStream stream = new FileInputStream("test.ad");
        InputStreamReader reader = new InputStreamReader(stream);
        Lexer lexer = new Lexer(reader);
        SymbolTBL symbolTBL = new SymbolTBL();

//        while(!noMoreLexer(lexer)) {

        String token = lexer.nextToken().toString();
            if (leftParenthesis(token)) {
//                while (true){
                    token = lexer.nextToken().toString();
                    if (rightParenthesis(token) || !noMoreLexer(token)) {
//                        break;
                    }
                    if (defineKeyword(token)) {
                        defineStatement(lexer, symbolTBL);
                    }

//                    }else if(reduceKeyword(lexer) ){
//                        reduceStatement(lexer, symbolTBL);
//                    }else if(printResult(lexer)){
//                        String identifier = lexer.nextToken().toString();
//                        int result = (int)symbolTBL.getValue(identifier);
//                        System.out.println(result);
//                    }

//                }

            }
        }
//    }

    private static void reduceStatement(Lexer lexer, SymbolTBL symbolTBL) {
        String identifier = lexer.nextToken().toString();
        ArrayList identifierValue = (ArrayList)symbolTBL.getValue(identifier);

        String operation = lexer.nextToken().toString();

        String initialization = lexer.nextToken().toString();
        int init = Integer.parseInt(initialization);

        String resultIdentifier = lexer.nextToken().toString();
        symbolTBL.appendTBL(resultIdentifier,init);

        int result = (int)symbolTBL.getValue(resultIdentifier);

        switch (operation){
            case "PLUS":
                for(int i=0;i<identifierValue.size();i++){
                    result += Integer.parseInt(identifierValue.get(i).toString());
                }
                break;
            case "STAR":
                for(int i=0;i<identifierValue.size();i++){
                    result *= Integer.parseInt(identifierValue.get(i).toString());
                }
                break;

        }
        symbolTBL.setValue(resultIdentifier,result);
    }


    private static void defineStatement(Lexer lexer, SymbolTBL symbolTBL) {

        String identifier = lexer.nextToken().toString();
        ArrayList list = new ArrayList();
        String token = lexer.nextToken().toString();

        if (listStart(token) || noMoreLexer(token)) {
            while (true) {
                token = lexer.nextToken().toString();
                System.out.println(token);
                if (listEnd(token)) {
                    break;
                }
                list.add(token);
            }
        }

        symbolTBL.appendTBL(identifier, list);
        Lexme lexme=(Lexme)symbolTBL.getValue(identifier);
        System.out.println(lexme.getTokenValue());

    }


    private static boolean noMoreLexer(String token) {
        return token==null;
    }
    private static boolean printResult(String token) {
        return token.equals("PRINT");
    }

    private static boolean reduceKeyword(String token) {
        return token.equals("REDUCE");
    }

    private static boolean listEnd(String token) {
        return token.equals("ListEnd");
    }

    private static boolean listStart(String token) {
        return token.equals("ListStart");
    }

    private static boolean defineKeyword(String token) {
        return token.equals("DEF");
    }

    private static boolean rightParenthesis(String token) {
        return token.equals("RPAR");
    }

    private static boolean leftParenthesis(String token) {
        return token.equals("LPAR");
    }
}
