/**
 * Created by withGod on 9/9/16.
 */
public class SymbolTBLEntry {
    private String identifier;
    private Lexme lexme;

    public SymbolTBLEntry(String identifier, Lexme lexme) {
        this.identifier = identifier;
        this.lexme=lexme;
    }


    public String getIdentifier() {
        return identifier;
    }

    public Object getValue() {
        return lexme;
    }

    public void setValue(Lexme lexme) {
        this.lexme = lexme;
    }
}
