package org.legendofdragoon.scripting;

public enum OpType {
  YIELD(0, "yield"),
  REWIND(1, "rewind"),
  WAIT(2, "wait", new String[] {"frames"}),
  WAIT_CMP(3, "wait_cmp", "operator", new String[] {"left", "right"}),
  WAIT_CMP_0(4, "wait_cmp", "operator", new String[] {"right"}),
  REWIND5(5, "rewind"),
  REWIND6(6, "rewind"),
  REWIND7(7, "rewind"),
  MOV(8, "mov", new String[] {"source", "dest"}),
  SWAP_BROKEN(9, "swap_broken", new String[] {"sourceDest", "dest"}),
  MEMCPY(10, "memcpy", new String[] {"size", "src", "dest"}),
  REWIND11(11, "rewind"),
  MOV_0(12, "mov", new String[] {"dest"}),
  REWIND13(13, "rewind"),
  REWIND14(14, "rewind"),
  REWIND15(15, "rewind"),
  AND(16, "and", new String[] {"right", "left"}),
  OR(17, "or", new String[] {"right", "left"}),
  XOR(18, "xor", new String[] {"right", "left"}),
  ANDOR(19, "andor", new String[] {"right", "left"}),
  NOT(20, "not", new String[] {"right", "left"}),
  SHL(21, "shl", new String[] {"right", "left"}),
  SHR(22, "shr", new String[] {"right", "left"}),
  ADD(24, "add", new String[] {"amount", "operand"}),
  SUB(25, "sub", new String[] {"amount", "operand"}),
  SUB_REV(26, "sub_rev", new String[] {"amount", "operand/dest"}),
  INCR(27, "incr", new String[] {"operand"}),
  DECR(28, "decr", new String[] {"operand"}),
  NEG(29, "neg", new String[] {"operand"}),
  ABS(30, "abs", new String[] {"operand"}),
  MUL(32, "mul", new String[] {"amount", "operand"}),
  DIV(33, "div", new String[] {"amount", "operand"}),
  DIV_REV(34, "div_rev", new String[] {"amount", "operand/dest"}),
  MOD(35, "mod", new String[] {"amount", "operand"}),
  MOD_REV(36, "mod_rev", new String[] {"amount", "operand/dest"}),
  MUL_12(40, "mul_12", new String[] {"amount", "operand"}),
  DIV_12(41, "div_12", new String[] {"amount", "operand"}),
  DIV_12_REV(42, "div_12_rev", new String[] {"amount", "operand"}),
  MOD43(43, "mod", new String[] {"amount", "operand"}),
  MOD_REV44(44, "mod_rev", new String[] {"amount", "operand/dest"}),
  SQRT(48, "sqrt", new String[] {"value", "dest"}),
  RAND(49, "rand", new String[] {"bound", "dest"}),
  SIN_12(50, "sin_12", new String[] {"angle", "dest"}),
  COS_12(51, "cos_12", new String[] {"angle", "dest"}),
  ATAN2_12(52, "atan2_12", new String[] {"y", "x", "dest"}),
  CALL(56, "call", "index"),
  JMP(64, "jmp", new String[] {"addr"}),
  JMP_CMP(65, "jmp_cmp", "operand", new String[] {"left", "right", "addr"}),
  JMP_CMP_0(66, "jmp_cmp", "operand", new String[] {"right", "addr"}),
  WHILE(67, "while", new String[] {"counter", "addr"}),
  JMP_TABLE(68, "jmp_table", new String[] {"index", "table"}),
  GOSUB(72, "gosub", new String[] {"addr"}),
  RETURN(73, "return"),
  GOSUB_TABLE(74, "gosub_table", new String[] {"index", "table"}),
  DEALLOCATE(80, "deallocate"),
  DEALLOCATE82(82, "deallocate"),
  DEALLOCATE_OTHER(83, "deallocate_other", new String[] {"index"}),
  FORK(86, "fork", new String[] {"index", "addr", "p2"}),
  FORK_REENTER(87, "fork_reenter", new String[] {"index", "entrypoint", "p2"}),
  CONSUME(88, "consume"),
  NOOP_96(96, "debug96", "?", new String[] {"?", "?"}),
  NOOP_97(97, "debug97"),
  NOOP_98(98, "debug98", new String[] {"?"}),
  DEPTH(99, "depth", new String[] {"dest"}),
  ;

  static {
    WAIT_CMP_0.setCommentParamNames(new String[] {"left", "right"});
    MOV_0.setCommentParamNames(new String[] {"source", "dest"});
    JMP_CMP_0.setCommentParamNames(new String[] {"left", "right", "addr"});
  }

  public static OpType byOpcode(final int opcode) {
    for(final OpType op : OpType.values()) {
      if(op.opcode == opcode) {
        return op;
      }
    }

    return null;
  }

  public static OpType byName(final String name) {
    for(final OpType op : OpType.values()) {
      if(op.name.equalsIgnoreCase(name)) {
        return op;
      }
    }

    return null;
  }

  public final int opcode;
  public final String name;
  public final String headerParamName;
  public final String[] paramNames;
  private String[] commentParamNames;

  OpType(final int opcode, final String name, final String headerParamName, final String[] paramNames) {
    this.opcode = opcode;
    this.name = name;
    this.paramNames = paramNames;
    this.headerParamName = headerParamName;
    this.commentParamNames = this.paramNames;
  }

  OpType(final int opcode, final String name, final String headerParamName) {
    this.opcode = opcode;
    this.name = name;
    this.paramNames = new String[0];
    this.headerParamName = headerParamName;
    this.commentParamNames = this.paramNames;
  }

  OpType(final int opcode, final String name, final String[] paramNames) {
    this.opcode = opcode;
    this.name = name;
    this.paramNames = paramNames;
    this.headerParamName = null;
    this.commentParamNames = this.paramNames;
  }

  OpType(final int opcode, final String name) {
    this.opcode = opcode;
    this.name = name;
    this.paramNames = new String[0];
    this.headerParamName = null;
    this.commentParamNames = this.paramNames;
  }

  private void setCommentParamNames(final String[] paramNames) {
    this.commentParamNames = paramNames;
  }

  public String[] getCommentParamNames() {
    return this.commentParamNames;
  }
}
