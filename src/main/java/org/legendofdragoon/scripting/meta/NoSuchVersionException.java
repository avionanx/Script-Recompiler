package org.legendofdragoon.scripting.meta;

public class NoSuchVersionException extends Exception {
  public NoSuchVersionException() {
    super();
  }

  public NoSuchVersionException(final String message) {
    super(message);
  }

  public NoSuchVersionException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public NoSuchVersionException(final Throwable cause) {
    super(cause);
  }

  protected NoSuchVersionException(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
