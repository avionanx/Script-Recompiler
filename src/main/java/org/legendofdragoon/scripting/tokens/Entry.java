package org.legendofdragoon.scripting.tokens;

public abstract class Entry {
  public final int address;

  protected Entry(final int address) {
    this.address = address;
  }
}
