package app.sevacenter.web;

/** A field that passed bean validation but failed a server-side rule; reported like validation errors. */
public class InvalidFieldException extends RuntimeException {

    private final String field;

    public InvalidFieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
