package org.legendofdragoon.scripting;

public enum ParameterType {
  IMMEDIATE(0x0, 1),
  NEXT_IMMEDIATE(0x1, 2),
  STORAGE(0x2, 1),
  OTHER_OTHER_STORAGE(0x3, 1),
  OTHER_STORAGE_OFFSET(0x4, 1),
  GAMEVAR_1(0x5, 1),
  GAMEVAR_2(0x6, 1),
  GAMEVAR_ARRAY_1(0x7, 1),
  GAMEVAR_ARRAY_2(0x8, 1),
  INLINE_1(0x9, 1),
  INLINE_2(0xa, 1),
  INLINE_3(0xb, 1),
  INLINE_4(0xc, 2),
  OTHER_STORAGE(0xd, 1),
  GAMEVAR_3(0xe, 1),
  GAMEVAR_ARRAY_3(0xf, 1),
  GAMEVAR_ARRAY_4(0x10, 1),
  GAMEVAR_ARRAY_5(0x11, 1),
  _12(0x12, 1),
  INLINE_5(0x13, 1),
  INLINE_6(0x14, 2),
  _15(0x15, 1),
  _16(0x16, 1),
  INLINE_7(0x17, 2),
  ;

  public static ParameterType byOpcode(final int opcode) {
    for(final ParameterType op : ParameterType.values()) {
      if(op.opcode == opcode) {
        return op;
      }
    }

    return ParameterType.IMMEDIATE;
  }

  public final int opcode;
  public final int width;

  ParameterType(final int opcode, final int width) {
    this.opcode = opcode;
    this.width = width;
  }

  /** table[index] */
  public boolean isInline() {
    return this == INLINE_1 || this == INLINE_2 || this == INLINE_3 || this == INLINE_4 || this == INLINE_5 || this == INLINE_6 || this == INLINE_7;
  }

  /** table[table[index]] */
  public boolean isRelativeInline() {
    return this == INLINE_3 || this == INLINE_4 || this == INLINE_6 || this == INLINE_7;
  }
}
