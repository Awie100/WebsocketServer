public class HttpParseException extends Exception {
    int status;
    public HttpParseException(int status, String s) {
        super(s);
        this.status = status;
    }
}
