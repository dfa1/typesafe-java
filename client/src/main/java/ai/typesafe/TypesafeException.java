package ai.typesafe;

public class TypesafeException extends RuntimeException {

    private final int statusCode;
    private final String body;

    public TypesafeException(int statusCode, String body) {
        super("TypeSafe API error " + statusCode + ": " + body);
        this.statusCode = statusCode;
        this.body = body;
    }

    public int statusCode() {
        return statusCode;
    }

    public String body() {
        return body;
    }
}
