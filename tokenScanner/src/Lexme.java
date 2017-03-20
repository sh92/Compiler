/**
 * Created by withGod on 9/8/16.
 */
public class Lexme {
    private int tokenNumber;
    private Object tokenValue;

    public Lexme(int _tokenNumber, Object tokenValue) {
        this.tokenNumber=_tokenNumber;
        this.tokenValue=tokenValue;
    }


    public Object getTokenValue() {
        return tokenValue;
    }


    public void setTokenValue(int tokenValue) {
        this.tokenValue = tokenValue;
    }
}
