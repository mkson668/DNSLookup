package ca.ubc.cs.cs317.dnslookup;


public class UrlAndPosition {
    private String textResult;
    private int posAfterReading;

    public UrlAndPosition(String textResult, int posAfterReading) {
        this.textResult = textResult;
        this.posAfterReading = posAfterReading;
    }

    public String getTextResult() {
        return this.textResult;
    }

    public int getPosAfterReading() {
        return this.posAfterReading;
    }

}

