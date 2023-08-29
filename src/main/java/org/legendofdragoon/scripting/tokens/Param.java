package org.legendofdragoon.scripting.tokens;

import org.legendofdragoon.scripting.ParameterType;

import java.util.OptionalInt;

public class Param extends Entry {
  public final ParameterType type;
  public final int[] rawValues;
  public final OptionalInt resolvedValue;
  public final String label;

  public Param(final int address, final ParameterType type, final int[] rawValues, final OptionalInt resolvedValue, final String label) {
    super(address);
    this.type = type;
    this.rawValues = rawValues;
    this.resolvedValue = resolvedValue;
    this.label = label;
  }
}
