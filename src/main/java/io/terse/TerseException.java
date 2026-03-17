package io.terse;

/**
 * Thrown by the TERSE parser and serializer on invalid input or unsupported types.
 */
public final class TerseException extends RuntimeException {

    private final int position;
    private final String code;

    public TerseException(String message, int position) {
        super(position >= 0 ? message + " at position " + position : message);
        this.position = position;
        this.code = "PARSE_ERROR";
    }

    public TerseException(String message, int position, String code) {
        super(position >= 0 ? message + " at position " + position : message);
        this.position = position;
        this.code = code;
    }

    /** Character position in the source where the error occurred, or -1. */
    public int getPosition() { return position; }

    /** Short error code (e.g. ILLEGAL_CHARACTER, DUPLICATE_KEY, MAX_DEPTH_EXCEEDED). */
    public String getCode() { return code; }
}
