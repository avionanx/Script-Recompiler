package org.legendofdragoon.scripting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class Parser {
  private static final Logger LOGGER = LogManager.getFormatterLogger(Parser.class);

  private final byte[] script;
  private final int startingIndex;

  private int currentIndex;

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
      final int childCount = (int)(parentCommand >> 8 & 0xffL);
      final int parentParam = (int)(parentCommand >> 16);

      state.advance();

      final long[] children = new long[childCount];

      try {
        for(int childIndex = 0; childIndex < childCount; childIndex++) {
          children[childIndex] = state.currentCommand();
          Ops.byOpcode(state.op()).act(state, childIndex);
        }
      } catch(final IndexOutOfBoundsException | IllegalArgumentException e) {
        lines.put(state.startIndex(), "0x%x".formatted(parentCommand));
        state.jump(state.startIndex() + 4);
        continue;
      }

      switch(callbackIndex) {
        case 0x0 -> lines.put(state.startIndex(), "pause;");

        case 0x1 -> lines.put(
          state.startIndex(),
          """
          rewind;
          pause;"""
        );

        case 0x2 -> {
          lines.put(
            state.startIndex(),
            """
            // Wait for n frames
            if(%s != 0) {
              %s--;
              // Repeat this same if statement next frame
              rewind;
              pause;
            }""".formatted(state.getWork(0), state.getWork(0))
          );
        }

        case 0x8 -> { // Move
          lines.put(state.startIndex(), "%s = %s;".formatted(state.getWork(1), state.getWork(0)));
        }

        case 0xa -> { // memcpy
          lines.put(state.startIndex(), "memcpy(%s, %s, %s); // dst, src, size".formatted(state.getWork(2), state.getWork(1), state.getWork(0)));
        }

        case 0xc -> { // Set 0
          lines.put(state.startIndex(), "%s = 0;".formatted(state.getWork(0)));
        }

        case 0x10 -> { // And
          lines.put(state.startIndex(), "%s &= %s;".formatted(state.getWork(1), state.getWork(0)));
        }

        case 0x15 -> { // Shift left
          lines.put(state.startIndex(), "%s <<= %s;".formatted(state.getWork(1), state.getWork(0)));
        }

        case 0x16 -> { // Shift right
          lines.put(state.startIndex(), "%s >>= %s;".formatted(state.getWork(1), state.getWork(0)));
        }

        case 0x18 -> { // Add
          lines.put(state.startIndex(), "%s += %s;".formatted(state.getWork(1), state.getWork(0)));
        }

        case 0x19 -> { // sub
          lines.put(state.startIndex(), "%s -= %s;".formatted(state.getWork(1), state.getWork(0)));
        }

        case 0x1b -> { // Incr
          lines.put(state.startIndex(), "%s++;".formatted(state.getWork(0)));
        }

        case 0x1c -> { // Decr
          lines.put(state.startIndex(), "%s--;".formatted(state.getWork(0)));
        }

        case 0x1d -> { // Neg
          lines.put(state.startIndex(), "%s = -%s;".formatted(state.getWork(0), state.getWork(0)));
        }

        case 0x20 -> { // Mul
          lines.put(state.startIndex(), "%s *= %s;".formatted(state.getWork(1), state.getWork(0)));
        }

        case 0x21 -> { // Div
          lines.put(state.startIndex(), "%s /= %s;".formatted(state.getWork(1), state.getWork(0)));
        }

        case 0x24 -> { // Mod
          lines.put(state.startIndex(), "%s = %s %% %s;".formatted(state.getWork(1), state.getWork(0), state.getWork(1)));
        }

        case 0x28 -> { // ?
          lines.put(state.startIndex(), "%s = ((%s >> 4) * (%s >> 4)) >> 4;".formatted(state.getWork(1), state.getWork(1), state.getWork(0)));
        }

        case 0x30 -> { // Sqrt0
          lines.put(state.startIndex(), "%s = sqrt(%s);".formatted(state.getWork(1), state.getWork(0)));
        }

        case 0x32 -> { // Sin12
          lines.put(state.startIndex(), "%s = sin(%s); // 12-bit fixed-point decimal".formatted(state.getWork(1), state.getWork(0)));
        }

        case 0x33 -> { // Cos12
          lines.put(state.startIndex(), "%s = cos(%s); // 12-bit fixed-point decimal".formatted(state.getWork(1), state.getWork(0)));
        }

        case 0x38 -> { // Subfunc
          switch(parentParam) {
            case 0x5 -> { // Read script flag
              lines.put(
                state.startIndex(),
                """
                flag = *(%s);
                index = flag >>> 5; // Bitset index
                shift = flag & 0x1f; // Bit number
                %s = scriptFlags2[index] & (1 << shift); // Read a script flag""".formatted(state.getWork(0), state.getWork(1))
              );
            }

            case 0xc3 -> lines.put(state.startIndex(), "%s = ((uint*)0x800be358)[%s] | ((uint*)0x800bdf38)[%s]; // unknown".formatted(state.getWork(1), state.getWork(0), state.getWork(0)));

            default -> lines.put(state.startIndex(), "//TODO Unknown sub-function 0x%02x".formatted(parentParam));
          }
        }

        case 0x40 -> { // Jump
          lines.put(state.startIndex(), "jump %s;".formatted(state.getWork(0)));
        }

        case 0x41 -> { // Jump cmp
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

          lines.put(state.startIndex(), "if(%s %s %s) jump %s;".formatted(state.getWork(0), operand, state.getWork(1), state.getWork(2)));
        }

        case 0x42 -> { // Jump cmp 0
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

          lines.put(state.startIndex(), "if(0 %s %s) jump %s;".formatted(operand, state.getWork(0), state.getWork(1)));
        }

        case 0x43 -> {
          lines.put(
            state.startIndex(),
            """
            %s--;
            if(%s != 0) jump %s;""".formatted(state.getWork(0), state.getWork(0), state.getWork(1))
          );
        }

        case 0x48 -> { // Jump and link
          lines.put(state.startIndex(), "func %s;".formatted(state.getWork(0)));

          try {
            functions.add(Integer.parseInt(state.getWork(0).substring(2), 16));
          } catch(final NumberFormatException ignored) { }
        }

        case 0x49 -> { // Return
          lines.put(state.startIndex(), "return;");
        }

        case 0x4a -> { // Jump and link table
          lines.put(state.startIndex(), "func %s + %s[%s] * 4;".formatted(state.getWork(1), state.getWork(1), state.getWork(0)));

          try {
            functions.add(Integer.parseInt(state.getWork(0).substring(2), 16));
          } catch(final NumberFormatException ignored) { }
        }

        case 0x57 -> {
          lines.put(state.startIndex(), "//TODO Unknown function 0x57");
        }

        default -> {
          lines.put(state.startIndex(), "0x%x //TODO Unknown function 0x%02x, data?".formatted(parentCommand, callbackIndex));

          for(int i = 0; i < children.length; i++) {
            lines.put(state.startIndex() + (i + 1) * 4, "0x%x".formatted(children[i]));
          }
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

  public void parseOld() {
    for(this.currentIndex = this.startingIndex; this.currentIndex < this.script.length; ) {
      int currentIndex = this.currentIndex;

      final long parentCommand = this.currentCommand();
      final int callbackIndex = (int)(parentCommand & 0xffL);
      final int childCount = (int)(parentCommand >> 8 & 0xffL);
      final int parentParam = (int)(parentCommand >> 16);

      LOGGER.info("%04x   0x%08x (children: 0x%02x, callback: 0x%02x, param: 0x%04x)", this.currentIndex, parentCommand, childCount, callbackIndex, parentParam);

      this.currentIndex += 4;

      for(int childIndex = 0; childIndex < childCount; childIndex++) {
        final long childCommand = this.currentCommand();
        final int operation = (int)(childCommand >>> 24);
        final int param0 = (int)(childCommand >>> 16 & 0xff);
        final int param1 = (int)(childCommand >>> 8 & 0xff);
        final int param2 = (int)(childCommand & 0xff);

        LOGGER.info("%04x   | 0x%08x (op: 0x%02x, params: 0x%02x, 0x%02x, 0x%02x)", this.currentIndex, childCommand, operation, param0, param1, param2);

        this.currentIndex += 4;

        switch(operation) {
          case 0x0 -> LOGGER.info("       | work[%d] = &currentChild; // Set work array to current child (0x%08x)", childIndex, childCommand);
          case 0x1 -> {
            LOGGER.info("       | work[%d] = &nextChild; // Set work array to next child (0x%08x) and advance", childIndex, this.currentCommand());
            this.currentIndex += 0x4L;
          }
          case 0x2 -> LOGGER.info("       | work[%d] = &storage[%d]; // Set work array to storage[param2]", childIndex, param2);
          case 0x5 -> LOGGER.info("       | work[%d] = &global[%d]; // Set work array to global[param2]", childIndex, param2); //TODO global name?
          case 0x9 -> LOGGER.info("       | work[%d] = &0x%x; // Set work array to script index", childIndex, currentIndex + (short)childCommand * 4);
          case 0xa -> LOGGER.info("       | work[%d] = &0x%x + storage[%d] * 4; // Set work array to script index", childIndex, currentIndex + (short)childCommand * 4, param0);
          default -> LOGGER.info("       | //TODO Not yet implemented");
        }
      }

      switch(callbackIndex) {
        case 8 -> { // Move
          LOGGER.info("       \\ *work[1] = *work[0]; // function scriptMove");
        }

        case 0x1b -> { // Incr
          LOGGER.info("       \\ *work[0]++; // function increment work[0] by one");
        }

        case 0x38 -> { // Subfunc
          switch(parentParam) {
            case 0x5 -> { // Read script flag
              LOGGER.info("       \\ index = *work[0] >>> 5; // Bitset index");
              LOGGER.info("       \\ shift = *work[0] & 0x1f; // Bit number");
              LOGGER.info("       \\ *work[1] = scriptFlags2[index] & (1 << shift); // Read a script flag");
            }

            case 0xc3 -> LOGGER.info("       \\ *work[1] = ((uint*)0x800be358)[*work[0]] | ((uint*)0x800bdf38)[*work[0]]; // unknown");

            default -> LOGGER.info("       \\ //TODO Unknown sub-function 0x%02x", parentParam);
          }
        }

        case 0x40 -> { // Jump
          LOGGER.info("       \\ goto *work[0]; // jump");
        }

        case 0x42 -> { // Jump cmp 0
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

          LOGGER.info("       \\ if 0 %s *work[0] goto *work[1]; // conditional jump", operand);
        }

        case 0x48 -> { // Jump and link
          LOGGER.info("       | push 0x%x; // push return address to stack", this.currentIndex);
          LOGGER.info("       \\ goto *work[0]; // jump");
        }

        case 0x49 -> { // Return
          LOGGER.info("       \\ pop; // pop return address");
          LOGGER.info("       \\ return; // return to return address");
        }

        default -> {
          LOGGER.info("       \\ //TODO Unknown function 0x%02x", callbackIndex);
        }
      }
    }
  }

  private long currentCommand() {
    long value = 0;

    for(int i = 0; i < 4; i++) {
      value |= (long)(this.script[this.currentIndex + i] & 0xff) << i * 8;
    }

    return value;
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
