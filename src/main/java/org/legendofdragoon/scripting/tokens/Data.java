package org.legendofdragoon.scripting.tokens;

public class Data extends Entry {
  public final int value;

  public Data(final int address, final int value) {
    super(address);
    this.value = value;
  }

  @Override
  public String toString() {
    return "data " + this.value;
  }
}
