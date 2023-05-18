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
      entries.add((int)state.currentCommand());
      state.advance();
    }

    state.jump(this.startingIndex);

    while(state.hasMore()) {
      state.step();

      final long parentCommand = state.currentCommand();
      final int callbackIndex = (int)(parentCommand & 0xffL);
      final int paramCount = (int)(parentCommand >> 8 & 0xffL);
      final int parentParam = (int)(parentCommand >> 16);

      state.advance();

      try {
        for(int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
          Ops.byOpcode(state.op()).act(state, paramIndex);
        }

        state.setParamCount(paramCount);
      } catch(final IndexOutOfBoundsException e) {
        lines.put(state.opOffset(), "0x%x".formatted(parentCommand));
        state.jump(state.opOffset() + 4);
        continue;
      }

      switch(callbackIndex) {
        case 0 -> lines.put(state.opOffset(), "pause;");

        case 1 -> lines.put(
          state.opOffset(),
          """
          rewind;
          pause;"""
        );

        case 2 ->
          lines.put(
            state.opOffset(),
            """
            // Wait for n frames
            if(%s != 0) {
              %s--;
              // Repeat this same if statement next frame
              rewind;
              pause;
            }""".formatted(state.getParam(0), state.getParam(0))
          );

        case 3 -> {
          final String operand = switch(parentParam) {
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

          lines.put(
            state.opOffset(),
            """
            if(!(%s %s %s)) {
              rewind;
              pause;
            }""".formatted(state.getParam(0), operand, state.getParam(1))
          );
        }

        case 4 -> {
          final String operand = switch(parentParam) {
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

          lines.put(
            state.opOffset(),
            """
            if(!(0 %s %s)) {
              rewind;
              pause;
            }""".formatted(operand, state.getParam(0))
          );
        }

        case 8 -> // Move
          lines.put(state.opOffset(), "%s = %s;".formatted(state.getParam(1), state.getParam(0)));

        case 9 -> // Broken swap
          lines.put(state.opOffset(), """
          { // Broken swapScript 
            tmp = %1$s;
            %2$s = tmp;
            %1$s = tmp;
          }""".formatted(state.getParam(0), state.getParam(1)));

        case 10 -> // memcpy
          lines.put(state.opOffset(), "memcpy(%s, %s, %s); // dst, src, size".formatted(state.getParam(2), state.getParam(1), state.getParam(0)));

        case 12 -> // Set 0
          lines.put(state.opOffset(), "%s = 0;".formatted(state.getParam(0)));

        case 16 -> // And
          lines.put(state.opOffset(), "%s &= %s;".formatted(state.getParam(1), state.getParam(0)));

        case 17 -> // Or
          lines.put(state.opOffset(), "%s |= %s;".formatted(state.getParam(1), state.getParam(0)));

        case 18 -> // Xor
          lines.put(state.opOffset(), "%s ^= %s;".formatted(state.getParam(1), state.getParam(0)));

        case 19 -> // And+Or
          lines.put(state.opOffset(), "%s = %s & %s | %s;".formatted(state.getParam(2), state.getParam(2), state.getParam(0), state.getParam(1)));

        case 20 -> // Not
          lines.put(state.opOffset(), "%s = ~%s;".formatted(state.getParam(0), state.getParam(0)));

        case 21 -> // Shift left
          lines.put(state.opOffset(), "%s <<= %s;".formatted(state.getParam(1), state.getParam(0)));

        case 22 -> // Shift right
          lines.put(state.opOffset(), "%s >>= %s;".formatted(state.getParam(1), state.getParam(0)));

        case 24 -> // Add
          lines.put(state.opOffset(), "%s += %s;".formatted(state.getParam(1), state.getParam(0)));

        case 25 -> // Sub
          lines.put(state.opOffset(), "%s -= %s;".formatted(state.getParam(1), state.getParam(0)));

        case 26 -> // Sub2
          lines.put(state.opOffset(), "%s = %s - %s;".formatted(state.getParam(1), state.getParam(0), state.getParam(1)));

        case 27 -> // Incr
          lines.put(state.opOffset(), "%s++;".formatted(state.getParam(0)));

        case 28 -> // Decr
          lines.put(state.opOffset(), "%s--;".formatted(state.getParam(0)));

        case 29 -> // Neg
          lines.put(state.opOffset(), "%s = -%s;".formatted(state.getParam(0), state.getParam(0)));

        case 30 -> // Abs
          lines.put(state.opOffset(), "%s = |%s|;".formatted(state.getParam(0), state.getParam(0)));

        case 32 -> // Mul
          lines.put(state.opOffset(), "%s *= %s;".formatted(state.getParam(1), state.getParam(0)));

        case 33 -> // Div
          lines.put(state.opOffset(), "%s /= %s;".formatted(state.getParam(1), state.getParam(0)));

        case 34 -> // Div2
          lines.put(state.opOffset(), "%s = %s / %s;".formatted(state.getParam(1), state.getParam(0), state.getParam(1)));

        case 35, 43 -> // Mod
          lines.put(state.opOffset(), "%s %%= %s;".formatted(state.getParam(1), state.getParam(0)));

        case 36, 44 -> // Mod2
          lines.put(state.opOffset(), "%s = %s %% %s;".formatted(state.getParam(1), state.getParam(0), state.getParam(1)));

        case 40 -> // ?
          lines.put(state.opOffset(), "%s = ((%s >> 4) / (%s >> 4)) >> 4;".formatted(state.getParam(1), state.getParam(1), state.getParam(0)));

        case 41 -> // ?
          lines.put(state.opOffset(), "%s = (%s << 4) * %s << 8;".formatted(state.getParam(1), state.getParam(1), state.getParam(0)));

        case 42 -> // ?
          lines.put(state.opOffset(), "%s = (%s << 4) * %s << 8;".formatted(state.getParam(1), state.getParam(0), state.getParam(1)));

        case 48 -> // Sqrt0
          lines.put(state.opOffset(), "%s = sqrt(%s);".formatted(state.getParam(1), state.getParam(0)));

        case 49 -> // Rand
          lines.put(state.opOffset(), "%s = rand(%s);".formatted(state.getParam(1), state.getParam(0)));

        case 50 -> // Sin12
          lines.put(state.opOffset(), "%s = sin(%s); // 12-bit fixed-point decimal".formatted(state.getParam(1), state.getParam(0)));

        case 51 -> // Cos12
          lines.put(state.opOffset(), "%s = cos(%s); // 12-bit fixed-point decimal".formatted(state.getParam(1), state.getParam(0)));

        case 52 -> // Ratan2
          lines.put(state.opOffset(), "%s = ratan2(%s, %s); // 12-bit fixed-point decimal".formatted(state.getParam(2), state.getParam(0), state.getParam(1)));

        case 56 -> { // Subfunc
          switch(parentParam) {
            case 5 -> { // Read script flag
              lines.put(
                state.opOffset(),
                """
                flag = *(%s);
                index = flag >>> 5; // Bitset index
                shift = flag & 0x1f; // Bit number
                %s = scriptFlags2[index] & (1 << shift); // Read a script flag""".formatted(state.getParam(0), state.getParam(1))
              );
            }

            case 195 -> lines.put(state.opOffset(), "%s = ((uint*)0x800be358)[%s] | ((uint*)0x800bdf38)[%s]; // unknown".formatted(state.getParam(1), state.getParam(0), state.getParam(0)));

            default -> {
              final String[] params = new String[state.getParamCount()];
              Arrays.setAll(params, state::getParam);

              lines.put(state.opOffset(), "subfunc(%d)(%s)".formatted(parentParam, String.join(", ", params)));
            }
          }
        }

        case 64 -> // Jump
          lines.put(state.opOffset(), "this.jump(%s);".formatted(state.getParam(0)));

        case 65 -> { // Jump cmp
          final String operand = switch(parentParam) {
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

          lines.put(
            state.opOffset(),
            """
            if(%s %s %s) {
              this.jump(%s);
            }""".formatted(state.getParam(0), operand, state.getParam(1), state.getParam(2))
          );
        }

        case 66 -> { // Jump cmp 0
          final String operand = switch(parentParam) {
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

          lines.put(
            state.opOffset(),
            """
            if(0 %s %s) {
              this.jump(%s);
            }""".formatted(operand, state.getParam(0), state.getParam(1)));
        }

        case 67 ->
          lines.put(
            state.opOffset(),
            """
            %s--; // while loop
            if(%s != 0) {
              this.jump(%s);
            }""".formatted(state.getParam(0), state.getParam(0), state.getParam(1))
          );

        case 68 -> lines.put(state.opOffset(), "this.jump(%s[%s[%s]])".formatted(state.getParam(1), state.getParam(1), state.getParam(0)));

        case 72 -> { // Jump and link
          lines.put(state.opOffset(), "gosub %s;".formatted(state.getParam(0)));

          try {
            functions.add(Integer.parseInt(state.getParam(0).substring(2), 16));
          } catch(final NumberFormatException ignored) { }
        }

        case 73 -> // Return
          lines.put(state.opOffset(), "return;");

        case 74 -> { // Jump and link table
          lines.put(state.opOffset(), "gosub %s + %s[%s] * 4;".formatted(state.getParam(1), state.getParam(1), state.getParam(0)));

          try {
            functions.add(Integer.parseInt(state.getParam(0).substring(2), 16));
          } catch(final NumberFormatException ignored) { }
        }

        case 80 ->
          lines.put(
            state.opOffset(),
            """
            deallocate;
            pause;
            rewind;"""
          );

        case 82 ->
          lines.put(
            state.opOffset(),
            """
            deallocate children;
            deallocate;
            pause;
            rewind;"""
          );

        case 83 ->
          lines.put(
            state.opOffset(),
            """
            deallocate %s;
            if(%s is self) {
              pause;
              rewind;
            }""".formatted(state.getParam(0), state.getParam(0))
          );

        case 86 ->
          lines.put(
            state.opOffset(),
            """
            // Fork and jump script that was forked
            state[%1$s].fork();
            state[%1$s].jump(*%2$s);
            state[%1$s].storage[32] = %3$s""".formatted(state.getParam(0), state.getParam(1), state.getParam(2))
          );

        case 87 ->
          lines.put(
            state.opOffset(),
            """
            // Fork and re-enter script that was forked
            state[%1$s].fork();
            state[%1$s].jump(entry @ *%2$s);
            state[%1$s].storage[32] = %3$s""".formatted(state.getParam(0), state.getParam(1), state.getParam(2))
          );

        case 88 ->
          lines.put(
            state.opOffset(),
            """
            consume child;
            deallocate child;
            jump to child offset;"""
          );

        case 96, 97, 98 -> lines.put(state.opOffset(), "// noop");

        case 99 -> lines.put(state.opOffset(), "%s = this.stackDepth".formatted(state.getParam(0)));

        case 5, 6, 7, 11, 13, 14, 15 -> lines.put(state.opOffset(), "// not implemented - pause and rewind");

        default -> {
          lines.put(state.opOffset(), "0x%x //TODO Unknown function %d, data?".formatted(parentCommand, callbackIndex));
          state.jump(state.opOffset() + 4);
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
