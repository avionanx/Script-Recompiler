package org.legendofdragoon.scripting.tokens;

public class Entrypoint extends Entry {
  public final int destination;

  public Entrypoint(final int address, final int destination) {
    super(address);
    this.destination = destination;
  }
}
