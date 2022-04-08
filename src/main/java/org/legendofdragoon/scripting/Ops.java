package org.legendofdragoon.scripting;

import java.util.function.BiConsumer;

public enum Ops {
  SET_TO_CHILD(0x0, (state, child) -> state.setWork(child, state.currentCommand()).advance()),
  SET_TO_NEXT_CHILD(0x1, (state, child) -> state.advance().setWork(child, state.currentCommand()).advance()),
  SET_TO_STORAGE(0x2, (state, child) -> state.setWork(child, "storage[%d]".formatted(state.param2())).advance()),
  SET_TO_OTHER_OTHER_STORAGE(0x3, (state, child) -> state.setWork(child, "otherScript[otherScript[storage[%d]]->storage[%d]]->storage[%d]".formatted(state.param2(), state.param1(), state.param0())).advance()),

  SET_TO_GLOBAL(0x5, (state, child) -> state.setWork(child, "global[%d]".formatted(state.param2())).advance()),

  SET_TO_GLOBAL_ARRAY_1(0x7, (state, child) -> state.setWork(child, "global[%d][storage[%d]]".formatted(state.param2(), state.param1())).advance()),

  SET_TO_DATA_1(0x9, (state, child) -> state.setWork(child, "*0x%x".formatted(state.startIndex() + (short)state.currentCommand() * 4)).advance()),
  SET_TO_DATA_2(0xa, (state, child) -> state.setWork(child, "0x%x[storage[%d]]".formatted(state.startIndex() + (short)state.currentCommand() * 4, state.param0())).advance()),
  SET_TO_DATA_3(0xb, (state, child) -> state.setWork(child, "0x%x[0x%x[storage[%d] + 0x%x] + 0x%x]".formatted(state.startIndex(), state.startIndex(), state.param0(), (short)state.currentCommand() * 4, (short)state.currentCommand() * 4)).advance()), //TODO is this right?

  SET_TO_GLOBAL_ARRAY_2(0xf, (state, child) -> state.setWork(child, "global[%d][%d]".formatted(state.param2(), state.param1())).advance()),

  SET_TO_DATA_14(0x13, (state, child) -> state.setWork(child, "*0x%x".formatted(state.startIndex() + ((short)state.currentCommand() + state.param0()) * 4)).advance()),
  ;

  public static Ops byOpcode(final int opcode) {
    for(final Ops op : Ops.values()) {
      if(op.opcode == opcode) {
        return op;
      }
    }

    throw new IllegalArgumentException("Unsupported opcode " + Integer.toHexString(opcode));
  }

  public final int opcode;
  private final BiConsumer<State, Integer> action;

  Ops(final int opcode, final BiConsumer<State, Integer> action) {
    this.opcode = opcode;
    this.action = action;
  }

  public void act(final State state, final int childIndex) {
    this.action.accept(state, childIndex);
  }
}
