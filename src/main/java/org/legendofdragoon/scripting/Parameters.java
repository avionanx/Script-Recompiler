package org.legendofdragoon.scripting;

import java.util.function.BiConsumer;

public enum Parameters {
  IMMEDIATE(0x0, 1, (state, child) -> state.setParam(child, state.currentWord()).advance()),
  NEXT_IMMEDIATE(0x1, 2, (state, child) -> state.advance().setParam(child, state.currentWord()).advance()),
  STORAGE(0x2, 1, (state, child) -> state.setParam(child, "this.storage[%d]".formatted(state.param0())).advance()),
  OTHER_OTHER_STORAGE(0x3, 1, (state, child) -> state.setParam(child, "state[state[this.storage[%d]].storage[%d]].storage[%d]".formatted(state.param0(), state.param1(), state.param2())).advance()),
  OTHER_STORAGE_OFFSET(0x4, 1, (state, child) -> state.setParam(child, "state[this.storage[%d]].storage[%d + this.storage[%d]]".formatted(state.param0(), state.param1(), state.param2())).advance()),
  GAMEVAR_1(0x5, 1, (state, child) -> state.setParam(child, "gameVar[%d]".formatted(state.param0())).advance()),
  GAMEVAR_2(0x6, 1, (state, child) -> state.setParam(child, "gameVar[%d + this.storage[%d]]".formatted(state.param0(), state.param1())).advance()),
  GAMEVAR_ARRAY_1(0x7, 1, (state, child) -> state.setParam(child, "gameVar[%d][storage[%d]]".formatted(state.param0(), state.param1())).advance()),
  GAMEVAR_ARRAY_2(0x8, 1, (state, child) -> state.setParam(child, "gameVar[%d + this.storage[%d]][this.storage[%d]]".formatted(state.param0(), state.param1(), state.param2())).advance()),
  INLINE_1(0x9, 1, (state, child) -> state.setParam(child, "*0x%x".formatted(state.headerOffset() + (short)state.currentWord() * 4)).advance()),
  INLINE_2(0xa, 1, (state, child) -> state.setParam(child, "0x%x[storage[%d]]".formatted(state.headerOffset() + (short)state.currentWord() * 4, state.param2())).advance()),
  INLINE_3(0xb, 1, (state, child) -> state.setParam(child, "0x%x[0x%x[storage[%d]]]".formatted(state.headerOffset() + (short)state.currentWord() * 4, state.headerOffset() + (short)state.currentWord() * 4, state.param2())).advance()),
  INLINE_4(0xc, 2, (state, child) -> state.advance().setParam(child, "%1$x[%1$x[this.storage[%2$d]] + this.storage[%3$d]]".formatted(state.headerOffset(), state.param0(), state.param1())).advance()),
  OTHER_STORAGE(0xd, 1, (state, child) -> state.setParam(child, "script[this.storage[%d]].storage[%d]".formatted(state.param0(), state.param1() + state.param2())).advance()),
  GAMEVAR_3(0xe, 1, (state, child) -> state.setParam(child, "gameVar[%d]".formatted(state.param0() + state.param1())).advance()),
  GAMEVAR_ARRAY_3(0xf, 1, (state, child) -> state.setParam(child, "gameVar[%d][%d]".formatted(state.param0(), state.param1())).advance()),
  GAMEVAR_ARRAY_4(0x10, 1, (state, child) -> state.setParam(child, "gameVar[%d + this.storage[%d]][%d]".formatted(state.param0(), state.param1(), state.param2())).advance()),
  _11(0x11, 1, (state, child) -> state.setParam(child, "gameVar[%d + %d][this.storage[%d]]".formatted(state.param0(), state.param1(), state.param2())).advance()),
  _12(0x12, 1, (state, child) -> { throw new RuntimeException("Param type 0x12 not yet supported"); }),
  INLINE_5(0x13, 1, (state, child) -> state.setParam(child, "*0x%x".formatted(state.headerOffset() + ((short)state.currentWord() + state.param2()) * 4)).advance()),
  INLINE_6(0x14, 2, (state, child) -> state.advance().setParam(child, "0x%1$x[0x%1$x[this.storage[%2$d]] + %3$d]".formatted(state.headerOffset(), state.param0(), state.param1())).advance()),
  _15(0x15, 1, (state, child) -> { throw new RuntimeException("Param type 0x15 not yet supported"); }),
  _16(0x16, 1, (state, child) -> { throw new RuntimeException("Param type 0x16 not yet supported"); }),
  _17(0x17, 2, (state, child) -> state.advance().setParam(child, "%1$x[%1$x[%2$d] + %3$d]".formatted(state.headerOffset(), state.param0(), state.param1())).advance()),
  ;

  public static Parameters byOpcode(final int opcode) {
    for(final Parameters op : Parameters.values()) {
      if(op.opcode == opcode) {
        return op;
      }
    }

    return Parameters.IMMEDIATE;
  }

  public final int opcode;
  public final int width;
  private final BiConsumer<State, Integer> action;

  Parameters(final int opcode, final int width, final BiConsumer<State, Integer> action) {
    this.opcode = opcode;
    this.width = width;
    this.action = action;
  }

  public void act(final State state, final int paramIndex) {
    this.action.accept(state, paramIndex);
  }
}
