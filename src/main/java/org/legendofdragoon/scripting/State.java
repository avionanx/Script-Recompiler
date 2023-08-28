package org.legendofdragoon.scripting;

public class State {
  private final byte[] script;
  private final String[] params = new String[10];
  private int paramCount = 0;

  private int headerOffset;
  private int currentOffset;

  public State(final byte[] script) {
    this.script = script;
  }

  public int length() {
    return this.script.length;
  }

  public void step() {
    this.headerOffset = this.currentOffset;
  }

  public int headerOffset() {
    return this.headerOffset;
  }

  public void headerOffset(final int opOffset) {
    this.headerOffset = opOffset;
  }

  public int currentOffset() {
    return this.currentOffset;
  }

  public void currentOffset(final int currentOffset) {
    this.currentOffset = currentOffset;
  }

  public int currentWord() {
    return this.wordAt(this.currentOffset);
  }

  public int wordAt(final int index) {
    return MathHelper.get(this.script, index, 4);
  }

  public int paramType() {
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
    return this.advance(1);
  }

  public State advance(final int words) {
    this.currentOffset += words * 0x4;
    return this;
  }

  public State jump(final int index) {
    this.currentOffset = index;
    return this;
  }

  public boolean hasMore() {
    return this.currentOffset < this.script.length;
  }

  @Deprecated
  public State setParam(final int paramIndex, final String value) {
    this.params[paramIndex] = value;
    return this;
  }

  @Deprecated
  public State setParam(final int paramIndex, final int value) {
    return this.setParam(paramIndex, "0x" + Integer.toHexString(value));
  }

  @Deprecated
  public String getParam(final int paramIndex) {
    return this.params[paramIndex];
  }

  @Deprecated
  public State setParamCount(final int count) {
    this.paramCount = count;
    return this;
  }

  @Deprecated
  public int getParamCount() {
    return this.paramCount;
  }
}
