package org.legendofdragoon.scripting;

import java.util.function.BiConsumer;

public enum ParameterType {
  IMMEDIATE(0x0, 1, (state, param) -> state.setParam(param, "0x%x".formatted(state.currentWord())).advance()),
  NEXT_IMMEDIATE(0x1, 2, (state, param) -> state.advance().setParam(param, "0x%x".formatted(state.currentWord())).advance()),
  STORAGE(0x2, 1, (state, param) -> state.setParam(param, "stor[%d]".formatted(state.param0())).advance()),
  OTHER_OTHER_STORAGE(0x3, 1, (state, param) -> state.setParam(param, "state[state[stor[%d]].stor[%d]].stor[%d]".formatted(state.param0(), state.param1(), state.param2())).advance()),
  OTHER_STORAGE_OFFSET(0x4, 1, (state, param) -> state.setParam(param, "state[stor[%d]].stor[%d + this.stor[%d]]".formatted(state.param0(), state.param1(), state.param2())).advance()),
  GAMEVAR_1(0x5, 1, (state, param) -> state.setParam(param, "var[%d]".formatted(state.param0())).advance()),
  GAMEVAR_2(0x6, 1, (state, param) -> state.setParam(param, "var[%d + stor[%d]]".formatted(state.param0(), state.param1())).advance()),
  GAMEVAR_ARRAY_1(0x7, 1, (state, param) -> state.setParam(param, "var[%d][stor[%d]]".formatted(state.param0(), state.param1())).advance()),
  GAMEVAR_ARRAY_2(0x8, 1, (state, param) -> state.setParam(param, "var[%d + stor[%d]][stor[%d]]".formatted(state.param0(), state.param1(), state.param2())).advance()),
  INLINE_1(0x9, 1, (state, param) -> state.setParam(param, "inl[0x%x]".formatted(state.headerOffset() + (short)state.currentWord() * 4)).advance()),
  INLINE_2(0xa, 1, (state, param) -> state.setParam(param, "inl[0x%x[stor[%d]]]".formatted(state.headerOffset() + (short)state.currentWord() * 4, state.param2())).advance()),
  INLINE_3(0xb, 1, (state, param) -> state.setParam(param, "inl[0x%1$x[0x%1$x[stor[%2$d]]]]".formatted(state.headerOffset() + (short)state.currentWord() * 4, state.param2())).advance()),
  INLINE_4(0xc, 2, (state, param) -> state.advance().setParam(param, "inl[%1$x[%1$x[stor[%2$d]] + stor[%3$d]]]".formatted(state.headerOffset(), state.param0(), state.param1())).advance()),
  OTHER_STORAGE(0xd, 1, (state, param) -> state.setParam(param, "script[stor[%d]].stor[%d]".formatted(state.param0(), state.param1() + state.param2())).advance()),
  GAMEVAR_3(0xe, 1, (state, param) -> state.setParam(param, "var[%d + %d]".formatted(state.param0(), state.param1())).advance()),
  GAMEVAR_ARRAY_3(0xf, 1, (state, param) -> state.setParam(param, "var[%d][%d]".formatted(state.param0(), state.param1())).advance()),
  GAMEVAR_ARRAY_4(0x10, 1, (state, param) -> state.setParam(param, "var[%d + stor[%d]][%d]".formatted(state.param0(), state.param1(), state.param2())).advance()),
  GAMEVAR_ARRAY_5(0x11, 1, (state, param) -> state.setParam(param, "var[%d + %d][stor[%d]]".formatted(state.param0(), state.param1(), state.param2())).advance()),
  _12(0x12, 1, (state, param) -> { throw new RuntimeException("Param type 0x12 not yet supported"); }),
  INLINE_5(0x13, 1, (state, param) -> state.setParam(param, "inl[0x%x]".formatted(state.headerOffset() + ((short)state.currentWord() + state.param2()) * 4)).advance()),
  INLINE_6(0x14, 2, (state, param) -> state.advance().setParam(param, "inl[0x%1$x[0x%1$x[stor[%2$d]] + %3$d]]".formatted(state.headerOffset(), state.param0(), state.param1())).advance()),
  _15(0x15, 1, (state, param) -> { throw new RuntimeException("Param type 0x15 not yet supported"); }),
  _16(0x16, 1, (state, param) -> { throw new RuntimeException("Param type 0x16 not yet supported"); }),
  INLINE_7(0x17, 2, (state, param) -> state.advance().setParam(param, "inl[%1$x[%1$x[%2$d] + %3$d]]".formatted(state.headerOffset(), state.param0(), state.param1())).advance()),
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
  private final BiConsumer<State, Integer> action;

  ParameterType(final int opcode, final int width, final BiConsumer<State, Integer> action) {
    this.opcode = opcode;
    this.width = width;
    this.action = action;
  }

  public String act(final State state, final int paramIndex) {
    this.action.accept(state, paramIndex);
    return state.getParam(paramIndex); //TODO hacky
  }

  public boolean isInline() {
    return this == INLINE_1 || this == INLINE_2 || this == INLINE_3 || this == INLINE_4 || this == INLINE_5 || this == INLINE_6 || this == INLINE_7;
  }
}
