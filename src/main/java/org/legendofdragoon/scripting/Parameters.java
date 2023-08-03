package org.legendofdragoon.scripting;

import java.util.function.BiConsumer;

public enum Parameters {
  IMMEDIATE(0x0, (state, child) -> state.setParam(child, state.currentCommand()).advance()),
  NEXT_IMMEDIATE(0x1, (state, child) -> state.advance().setParam(child, state.currentCommand()).advance()),
  STORAGE(0x2, (state, child) -> state.setParam(child, "this.storage[%d]".formatted(state.param0())).advance()),
  OTHER_OTHER_STORAGE(0x3, (state, child) -> state.setParam(child, "state[state[this.storage[%d]].storage[%d]].storage[%d]".formatted(state.param0(), state.param1(), state.param2())).advance()),
  OTHER_STORAGE_OFFSET(0x4, (state, child) -> state.setParam(child, "state[this.storage[%d]].storage[%d + this.storage[%d]]".formatted(state.param0(), state.param1(), state.param2())).advance()),
  GAMEVAR_1(0x5, (state, child) -> state.setParam(child, "gameVar[%d]".formatted(state.param0())).advance()),
  GAMEVAR_2(0x6, (state, child) -> state.setParam(child, "gameVar[%d + this.storage[%d]]".formatted(state.param0(), state.param1())).advance()),
  GAMEVAR_ARRAY_1(0x7, (state, child) -> state.setParam(child, "gameVar[%d][storage[%d]]".formatted(state.param0(), state.param1())).advance()),
  GAMEVAR_ARRAY_2(0x8, (state, child) -> state.setParam(child, "gameVar[%d + this.storage[%d]][this.storage[%d]]".formatted(state.param0(), state.param1(), state.param2())).advance()),
  INLINE_1(0x9, (state, child) -> state.setParam(child, "*0x%x".formatted(state.opOffset() + (short)state.currentCommand() * 4)).advance()),
  INLINE_2(0xa, (state, child) -> state.setParam(child, "0x%x[storage[%d]]".formatted(state.opOffset() + (short)state.currentCommand() * 4, state.param2())).advance()),
  INLINE_3(0xb, (state, child) -> state.setParam(child, "0x%x[0x%x[storage[%d]]]".formatted(state.opOffset() + (short)state.currentCommand() * 4, state.opOffset() + (short)state.currentCommand() * 4, state.param2())).advance()),
  INLINE_4(0xc, (state, child) -> state.advance().setParam(child, "%1$x[%1$x[this.storage[%2$d]] + this.storage[%3$d]]".formatted(state.currentOffset(), state.param0(), state.param1())).advance()),
  OTHER_STORAGE(0xd, (state, child) -> state.setParam(child, "script[this.storage[%d]].storage[%d]".formatted(state.param0(), state.param1() + state.param2())).advance()),
  GAMEVAR_3(0xe, (state, child) -> state.setParam(child, "gameVar[%d]".formatted(state.param0() + state.param1())).advance()),
  GAMEVAR_ARRAY_3(0xf, (state, child) -> state.setParam(child, "gameVar[%d][%d]".formatted(state.param0(), state.param1())).advance()),
  GAMEVAR_ARRAY_4(0x10, (state, child) -> state.setParam(child, "gameVar[%d + this.storage[%d]][%d]".formatted(state.param0(), state.param1(), state.param2())).advance()),
  _11(0x11, (state, child) -> state.setParam(child, "gameVar[%d + %d][this.storage[%d]]".formatted(state.param0(), state.param1(), state.param2())).advance()),
  _12(0x12, (state, child) -> { throw new RuntimeException("Param type 0x12 not yet supported"); }),
  INLINE_5(0x13, (state, child) -> state.setParam(child, "*0x%x".formatted(state.opOffset() + ((short)state.currentCommand() + state.param2()) * 4)).advance()),
  INLINE_6(0x14, (state, child) -> state.advance().setParam(child, "0x%1$x[0x%1$x[this.storage[%2$d]] + %3$d]".formatted(state.currentOffset(), state.param0(), state.param1())).advance()),
  _15(0x15, (state, child) -> { throw new RuntimeException("Param type 0x15 not yet supported"); }),
  _16(0x16, (state, child) -> { throw new RuntimeException("Param type 0x16 not yet supported"); }),
  _17(0x17, (state, child) -> state.advance().setParam(child, "%1$x[%1$x[%2$d] + %3$d]".formatted(state.currentOffset(), state.param0(), state.param1())).advance()),
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
  private final BiConsumer<State, Integer> action;

  Parameters(final int opcode, final BiConsumer<State, Integer> action) {
    this.opcode = opcode;
    this.action = action;
  }

  public void act(final State state, final int childIndex) {
    this.action.accept(state, childIndex);
  }
}
