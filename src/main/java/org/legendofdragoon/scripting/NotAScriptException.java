package org.legendofdragoon.scripting;

public class NotAScriptException extends RuntimeException {
    public NotAScriptException() {
    }

    public NotAScriptException(String message) {
        super(message);
    }

    public NotAScriptException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotAScriptException(Throwable cause) {
        super(cause);
    }
}
