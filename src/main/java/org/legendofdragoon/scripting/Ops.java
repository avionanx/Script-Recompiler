package org.legendofdragoon.scripting;

public enum Ops {
  YIELD(0, "yield"),
  REWIND(1, "rewind"),
  WAIT(2, "wait", new String[] {"frames"}),
  COMP_WAIT(3, "comp_wait", "operator", new String[] {"left, right"}),
  COMP_WAIT_0(4, "comp_wait", "operator", new String[] {"right"}),
  REWIND5(5, "rewind"),
  REWIND6(6, "rewind"),
  REWIND7(7, "rewind"),
  MOVE(8, "move", new String[] {"source", "dest"}),
  SWAP_BROKEN(9, "swap_broken", new String[] {"sourceDest", "dest"}),
  MEMCPY(10, "memcpy", new String[] {"size", "src", "dest"}),
  REWIND11(11, "rewind"),
  MOVE_0(12, "move", new String[] {"dest"}),
  REWIND13(13, "rewind"),
  REWIND14(14, "rewind"),
  REWIND15(15, "rewind"),
  AND(16, "and", new String[] {"right", "left"}),
  OR(17, "or", new String[] {"right", "left"}),
  XOR(18, "xor", new String[] {"and", "or", "left"}),
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
  UNK_40(40, "unk_40", new String[] {"?", "?"}),
  UNK_41(41, "unk_41", new String[] {"?", "?"}),
  UNK_42(42, "unk_42", new String[] {"?", "?"}),
  MOD43(43, "mod", new String[] {"amount", "operand"}),
  MOD_REV44(44, "mod_rev", new String[] {"amount", "operand/dest"}),
  SQRT(48, "sqrt", new String[] {"value", "dest"}),
  RAND(49, "rand", new String[] {"bound", "dest"}),
  SIN_12(50, "sin_12", new String[] {"angle", "dest"}),
  COS_12(51, "cos_12", new String[] {"angle", "dest"}),
  ATAN2_12(52, "atan2_12", new String[] {"y", "x", "dest"}),
  CALL(56, "call", "index"),
  JUMP(64, "jump", new String[] {"addr"}),
  COMP_JUMP(65, "comp_jump", "operand", new String[] {"left", "right", "addr"}),
  COMP_JUMP_0(66, "comp_jump", "operand", new String[] {"right", "addr"}),
  WHILE(67, "while", new String[] {"counter", "addr"}),
  JUMP_TABLE(68, "jump_table", new String[] {"index", "table"}),
  GOSUB(72, "gosub", new String[] {"addr"}),
  RETURN(73, "return"),
  GOSUB_TABLE(74, "gosub_table", new String[] {"index", "table"}),
  DEALLOCATE(80, "deallocate"),
  DEALLOCATE82(82, "deallocate"),
  DEALLOCATE_OTHER(83, "deallocate_other", new String[] {"index"}),
  ;

  public static Ops byOpcode(final int opcode) {
    for(final Ops op : Ops.values()) {
      if(op.opcode == opcode) {
        return op;
      }
    }

    return null;
  }

  public final int opcode;
  public final String name;
  public final String headerParam;
  public final String[] params;

  Ops(final int opcode, final String name, final String headerParam, final String[] params) {
    this.opcode = opcode;
    this.name = name;
    this.params = params;
    this.headerParam = headerParam;
  }

  Ops(final int opcode, final String name, final String headerParam) {
    this.opcode = opcode;
    this.name = name;
    this.params = new String[0];
    this.headerParam = headerParam;
  }

  Ops(final int opcode, final String name, final String[] params) {
    this.opcode = opcode;
    this.name = name;
    this.params = params;
    this.headerParam = null;
  }

  Ops(final int opcode, final String name) {
    this.opcode = opcode;
    this.name = name;
    this.params = new String[0];
    this.headerParam = null;
  }
}
