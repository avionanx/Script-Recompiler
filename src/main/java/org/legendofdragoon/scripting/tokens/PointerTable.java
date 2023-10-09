package org.legendofdragoon.scripting.tokens;

import java.util.Arrays;

public class PointerTable extends Entry {
  public String[] labels;

  public PointerTable(final int address, final String[] labels) {
    super(address);
    this.labels = labels;
  }

  @Override
  public String toString() {
    return "rel %x :%s".formatted(this.address, Arrays.toString(this.labels));
  }
}
