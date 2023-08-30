package org.legendofdragoon.scripting.tokens;

public class Entrypoint extends Entry {
  public final String destination;

  public Entrypoint(final int address, final String destination) {
    super(address);
    this.destination = destination;
  }

  @Override
  public String toString() {
    return "entrypoint " + this.destination;
  }
}
