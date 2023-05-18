package org.legendofdragoon.scripting;

public class State {
  private final byte[] script;
  private final String[] params = new String[10];
  private int paramCount = 0;

  private int opOffset;
  private int currentOffset;

  public State(final byte[] script) {
    this.script = script;
  }

  public void step() {
    this.opOffset = this.currentOffset;
  }

  public int opOffset() {
    return this.opOffset;
  }

  public int currentOffset() {
    return this.opOffset;
  }

  public long currentCommand() {
    return MathHelper.get(this.script, this.currentOffset, 4);
  }

  public long commandAt(final int index) {
    return MathHelper.get(this.script, index, 4);
  }

  public int op() {
    return this.script[this.currentOffset + 3] & 0xff;
  }

  public int param0() {
    return this.script[this.currentOffset] & 0xff;
  }

  public int param1() {
    return this.script[this.currentOffset + 1] & 0xff;
  }

  public int param2() {
    return this.script[this.currentOffset + 2] & 0xff;
  }

  public State advance() {
    this.currentOffset += 0x4;
    return this;
  }

  public State jump(final int index) {
    this.currentOffset = index;
    return this;
  }

  public boolean hasMore() {
    return this.currentOffset < this.script.length;
  }

  public int index() {
    return this.currentOffset;
  }

  public State setParam(final int paramIndex, final String value) {
    this.params[paramIndex] = value;
    return this;
  }

  public State setParam(final int paramIndex, final long value) {
    return this.setParam(paramIndex, "0x" + Long.toHexString(value));
  }

  public String getParam(final int paramIndex) {
    return this.params[paramIndex];
  }

  public State setParamCount(final int count) {
    this.paramCount = count;
    return this;
  }

  public int getParamCount() {
    return this.paramCount;
  }
}
