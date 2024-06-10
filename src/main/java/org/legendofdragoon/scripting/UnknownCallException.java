package org.legendofdragoon.scripting;

public class UnknownCallException extends RuntimeException {
  public UnknownCallException() {
    super();
  }

  public UnknownCallException(final String message) {
    super(message);
  }

  public UnknownCallException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public UnknownCallException(final Throwable cause) {
    super(cause);
  }

  protected UnknownCallException(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
