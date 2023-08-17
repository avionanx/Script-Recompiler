package org.legendofdragoon.scripting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class Parser {
  private static final Logger LOGGER = LogManager.getFormatterLogger(Parser.class);

  private final byte[] script;
  private final int startingIndex;

  public Parser(final byte[] script, final int startingIndex) {
    this.script = script;
    this.startingIndex = startingIndex;
  }

  public Parser(final byte[] script) {
    this(script, 0x40);
  }

  public void parse() {
    final Map<Integer, String> lines = new LinkedHashMap<>();
    final Set<Integer> functions = new HashSet<>();
    final Set<Integer> entries = new HashSet<>();

    final State state = new State(script);

    for(int i = 0; i < 0x10; i++) {
      entries.add(state.currentWord());
      state.advance();
    }

    state.jump(this.startingIndex);

    while(state.hasMore()) {
      state.step();

      final int parentCommand = state.currentWord();
      final int callbackIndex = parentCommand & 0xff;
      final int paramCount = parentCommand >> 8 & 0xff;
      final int parentParam = parentCommand >> 16;

      state.advance();

      try {
        for(int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
          Parameters.byOpcode(state.paramType()).act(state, paramIndex);
        }

        state.setParamCount(paramCount);
      } catch(final IndexOutOfBoundsException e) {
        lines.put(state.headerOffset(), "0x%x".formatted(parentCommand));
        state.jump(state.headerOffset() + 4);
        continue;
      }

      switch(callbackIndex) {
        case 0 -> lines.put(state.headerOffset(), "yield");
        case 1 -> lines.put(state.headerOffset(), "rewind");
        case 2 -> lines.put(state.headerOffset(), "wait %s".formatted(state.getParam(0)));
        case 3 -> lines.put(state.headerOffset(), "comp_wait %s %s %s".formatted(getOperator(parentParam), state.getParam(0), state.getParam(1)));
        case 4 -> lines.put(state.headerOffset(), "comp_wait %s 0 %s".formatted(getOperator(parentParam), state.getParam(0)));
        case 8 -> lines.put(state.headerOffset(), "move %s %s".formatted(state.getParam(1), state.getParam(0)));
        case 9 -> lines.put(state.headerOffset(), "swap_broken %s %s".formatted(state.getParam(0), state.getParam(1)));
        case 10 -> lines.put(state.headerOffset(), "memcpy %s %s %s".formatted(state.getParam(2), state.getParam(1), state.getParam(0)));
        case 12 -> lines.put(state.headerOffset(), "move %s 0".formatted(state.getParam(0)));
        case 16 -> lines.put(state.headerOffset(), "and %s %s".formatted(state.getParam(1), state.getParam(0)));
        case 17 -> lines.put(state.headerOffset(), "or %s %s".formatted(state.getParam(1), state.getParam(0)));
        case 18 -> lines.put(state.headerOffset(), "xor %s %s".formatted(state.getParam(1), state.getParam(0)));
        case 19 -> lines.put(state.headerOffset(), "andor %s %s %s".formatted(state.getParam(2), state.getParam(0), state.getParam(1)));
        case 20 -> lines.put(state.headerOffset(), "not %s".formatted(state.getParam(0)));
        case 21 -> lines.put(state.headerOffset(), "shl %s %s".formatted(state.getParam(1), state.getParam(0)));
        case 22 -> lines.put(state.headerOffset(), "shr %s %s".formatted(state.getParam(1), state.getParam(0)));
        case 24 -> lines.put(state.headerOffset(), "add %s %s".formatted(state.getParam(1), state.getParam(0)));
        case 25 -> lines.put(state.headerOffset(), "sub %s %s".formatted(state.getParam(1), state.getParam(0)));
        case 26 -> lines.put(state.headerOffset(), "sub_rev = %s %s".formatted(state.getParam(1), state.getParam(0)));
        case 27 -> lines.put(state.headerOffset(), "incr %s".formatted(state.getParam(0)));
        case 28 -> lines.put(state.headerOffset(), "decr %s".formatted(state.getParam(0)));
        case 29 -> lines.put(state.headerOffset(), "neg %s".formatted(state.getParam(0)));
        case 30 -> lines.put(state.headerOffset(), "abs %s".formatted(state.getParam(0)));
        case 32 -> lines.put(state.headerOffset(), "mul %s %s".formatted(state.getParam(1), state.getParam(0)));
        case 33 -> lines.put(state.headerOffset(), "div %s %s".formatted(state.getParam(1), state.getParam(0)));
        case 34 -> lines.put(state.headerOffset(), "div_rev %s %s".formatted(state.getParam(1), state.getParam(0)));
        case 35, 43 -> lines.put(state.headerOffset(), "mod %s %s".formatted(state.getParam(1), state.getParam(0)));
        case 36, 44 -> lines.put(state.headerOffset(), "mod_rev %s %s".formatted(state.getParam(1), state.getParam(0)));
        case 40 -> lines.put(state.headerOffset(), "unk_40 %s %s".formatted(state.getParam(1), state.getParam(0)));
        case 41 -> lines.put(state.headerOffset(), "unk_41 %s %s".formatted(state.getParam(1), state.getParam(0)));
        case 42 -> lines.put(state.headerOffset(), "unk_42 %s %s".formatted(state.getParam(1), state.getParam(0)));
        case 48 -> lines.put(state.headerOffset(), "sqrt %s %s".formatted(state.getParam(1), state.getParam(0)));
        case 49 -> lines.put(state.headerOffset(), "rand %s %s".formatted(state.getParam(1), state.getParam(0)));
        case 50 -> lines.put(state.headerOffset(), "sin_12 %s %s".formatted(state.getParam(1), state.getParam(0)));
        case 51 -> lines.put(state.headerOffset(), "cos_12 %s %s".formatted(state.getParam(1), state.getParam(0)));
        case 52 -> lines.put(state.headerOffset(), "atan2_12 %s %s %s".formatted(state.getParam(2), state.getParam(0), state.getParam(1)));

        case 56 -> {
          final String[] params = new String[state.getParamCount()];
          Arrays.setAll(params, state::getParam);
          lines.put(state.headerOffset(), "call %d (%s)".formatted(parentParam, String.join(", ", params)));
        }

        case 64 -> lines.put(state.headerOffset(), "jump %s".formatted(state.getParam(0)));
        case 65 -> lines.put(state.headerOffset(), "comp_jump %s %s %s %s".formatted(getOperator(parentParam), state.getParam(0), state.getParam(1), state.getParam(2)));
        case 66 -> lines.put(state.headerOffset(), "comp_jump %s 0 %s %s".formatted(getOperator(parentParam), state.getParam(0), state.getParam(1)));
        case 67 -> lines.put(state.headerOffset(), "while %s %s".formatted(state.getParam(0), state.getParam(1)));
        case 68 -> lines.put(state.headerOffset(), "jump_table %s %s".formatted(state.getParam(1), state.getParam(0)));
        case 72 -> {
          lines.put(state.headerOffset(), "gosub %s".formatted(state.getParam(0)));

          try {
            functions.add(Integer.parseInt(state.getParam(0).substring(2), 16));
          } catch(final NumberFormatException ignored) { }
        }

        case 73 -> lines.put(state.headerOffset(), "return");
        case 74 -> {
          lines.put(state.headerOffset(), "gosub_table %s %s".formatted(state.getParam(1), state.getParam(0)));

          try {
            functions.add(Integer.parseInt(state.getParam(0).substring(2), 16));
          } catch(final NumberFormatException ignored) { }
        }

        case 80 -> lines.put(state.headerOffset(), "deallocate");
        case 82 -> lines.put(state.headerOffset(), "deallocate");
        case 83 -> lines.put(state.headerOffset(), "deallocate %s".formatted(state.getParam(0)));
        case 86 -> lines.put(state.headerOffset(), "fork %s %s %s".formatted(state.getParam(0), state.getParam(1), state.getParam(2)));
        case 87 -> lines.put(state.headerOffset(), "fork_reenter %s %s %s".formatted(state.getParam(0), state.getParam(1), state.getParam(2)));
        case 88 -> lines.put(state.headerOffset(), "consume");
        case 96, 97, 98 -> lines.put(state.headerOffset(), "noop");
        case 99 -> lines.put(state.headerOffset(), "depth %s".formatted(state.getParam(0)));

        case 5, 6, 7, 11, 13, 14, 15 -> lines.put(state.headerOffset(), "rewind");

        default -> {
          lines.put(state.headerOffset(), "0x%x //TODO Unknown function %d, data?".formatted(parentCommand, callbackIndex));
          state.jump(state.headerOffset() + 4);
        }
      }
    }

    for(final Map.Entry<Integer, String> entry : lines.entrySet()) {
      if(functions.contains(entry.getKey())) {
        LOGGER.info("");
        LOGGER.info("// Function 0x%x", entry.getKey());
      }

      if(entries.contains(entry.getKey())) {
        LOGGER.info("");
        LOGGER.info("// Entry point 0x%x", entry.getKey());
      }

      if(!entry.getValue().contains("\n")) {
        LOGGER.info("%06x    %s", entry.getKey(), entry.getValue());
      } else {
        final String[] parts = entry.getValue().split("\n");
        LOGGER.info("%06x    %s", entry.getKey(), parts[0]);

        for(int i = 1; i < parts.length; i++) {
          LOGGER.info("          %s", parts[i]);
        }
      }
    }
  }

  private static String getOperator(final int index) {
    return switch(index) {
      case 0 -> "<=";
      case 1 -> "<";
      case 2 -> "==";
      case 3 -> "!=";
      case 4 -> ">";
      case 5 -> ">=";
      case 6 -> "&";
      case 7 -> "!&";
      default -> "//TODO <invalid operand>";
    };
  }

  private static final String[] functions = {
    "scriptReturnOne",
    "scriptReturnTwo",
    "scriptDecrementIfPossible",
    "scriptCompare",
    "FUN_80016744",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptMove *work[1] = *work[0]",
    "FUN_80016790",
    "scriptMemCopy",
    "scriptNotImplemented",
    "FUN_80016854",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "FUN_80016868",
    "FUN_8001688c",
    "FUN_800168b0",
    "FUN_800168d4",
    "FUN_80016900",
    "scriptShiftLeft",
    "scriptShiftRightArithmetic",
    "scriptNotImplemented",
    "scriptAdd",
    "scriptSubtract",
    "FUN_800169b0",
    "scriptIncrementBy1",
    "scriptDecrementBy1",
    "FUN_80016a14",
    "FUN_80016a34",
    "scriptNotImplemented",
    "scriptMultiply",
    "scriptDivide",
    "FUN_80016ab0",
    "FUN_80016adc",
    "FUN_80016b04",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "FUN_80016b2c",
    "FUN_80016b5c",
    "FUN_80016b8c",
    "FUN_80016adc",
    "FUN_80016b04",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptSquareRoot",
    "FUN_80016c00",
    "FUN_80016c4c",
    "FUN_80016c80",
    "FUN_80016cb4",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptExecuteSubFunc",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptJump",
    "scriptConditionalJump",
    "scriptConditionalJump0",
    "FUN_80016dec",
    "FUN_80016e1c",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptJumpAndLink",
    "scriptJumpReturn",
    "FUN_80016ffc",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "FUN_800170f4",
    "scriptNotImplemented",
    "FUN_80017138",
    "FUN_80017160",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "FUN_800171c0",
    "FUN_80017234",
    "FUN_800172c0",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "FUN_800172f4",
    "FUN_800172fc",
    "FUN_80017304",
    "FUN_8001730c",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
    "scriptNotImplemented",
  };
}
