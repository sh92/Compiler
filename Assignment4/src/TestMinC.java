import java.io.IOException;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;


public class TestMinC {
	public static void main(String[] args) throws IOException{
		MiniCLexer lexer = new MiniCLexer(new ANTLRFileStream("test2.c"));
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		MiniCParser parser =new MiniCParser(tokens);
		ParseTree tree = parser.program();

		ParseTreeWalker walker = new ParseTreeWalker();
		walker.walk(new UCodeGenListener(), tree);
	}
}
