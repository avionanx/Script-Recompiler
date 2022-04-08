package org.legendofdragoon.scripting;

public class State {
  private final byte[] script;
  private final String[] work = new String[8];

  private int startIndex;
  private int scriptIndex;

  public State(final byte[] script) {
    this.script = script;
  }

  public void step() {
    this.startIndex = this.scriptIndex;
  }

  public int startIndex() {
    return this.startIndex;
  }

  public long currentCommand() {
    return MathHelper.get(this.script, this.scriptIndex, 4);
  }

  public long commandAt(final int index) {
    return MathHelper.get(this.script, index, 4);
  }

  public int op() {
    return this.script[this.scriptIndex + 3] & 0xff;
  }

  public int param0() {
    return this.script[this.scriptIndex + 2] & 0xff;
  }

  public int param1() {
    return this.script[this.scriptIndex + 1] & 0xff;
  }

  public int param2() {
    return this.script[this.scriptIndex] & 0xff;
  }

  public State advance() {
    this.scriptIndex += 0x4;
    return this;
  }

  public State jump(final int index) {
    this.scriptIndex = index;
    return this;
  }

  public boolean hasMore() {
    return this.scriptIndex < this.script.length;
  }

  public int index() {
    return this.scriptIndex;
  }

  public State setWork(final int childIndex, final String value) {
    this.work[childIndex] = value;
    return this;
  }

  public State setWork(final int childIndex, final long value) {
    return this.setWork(childIndex, "0x" + Long.toHexString(value));
  }

  public String getWork(final int childIndex) {
    return this.work[childIndex];
  }
}
