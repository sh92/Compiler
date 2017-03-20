/**
 * Created by withGod on 9/8/16.
 */
public class SymbolTBL {
    private static final int LIMITSYMBOLTBLNUMBER=100;
    private int tokenNumber=0;
    private SymbolTBLEntry[] symbolTBLEntry;

    public SymbolTBL(){
        symbolTBLEntry=new SymbolTBLEntry[LIMITSYMBOLTBLNUMBER];
    }

    public void appendTBL(String identifier, Object value){
        Lexme lexme = new Lexme(tokenNumber,value);
        symbolTBLEntry[tokenNumber]=new SymbolTBLEntry(identifier,lexme);
        tokenNumber++;
    }

    public Object getValue(String resultIdentifier) {
        int idx = findSymbolTBLIndex(resultIdentifier);
        if (idx != -1) return symbolTBLEntry[idx].getValue();
        return null;
    }

    private int findSymbolTBLIndex(String resultIdentifier) {
        for(int i =0;i<tokenNumber;i++){
            if(symbolTBLEntry[i].getIdentifier().equals(resultIdentifier)){
                return i;
            }
        }
        return -1;
    }

    public void setValue(String resultIdentifier, int result) {
        int idx = findSymbolTBLIndex(resultIdentifier);
        Lexme lexme = (Lexme)symbolTBLEntry[idx].getValue();
        lexme.setTokenValue(result);
        symbolTBLEntry[idx].setValue(lexme);
    }
}
