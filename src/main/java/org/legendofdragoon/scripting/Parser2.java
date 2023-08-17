package org.legendofdragoon.scripting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;

public class Parser2 {
  private final State state;

  private final Set<Integer> hits = new HashSet<>();
  private final Set<Integer> branches = new HashSet<>();

  public static void main(final String[] args) throws IOException {
    final byte[] bytes = Files.readAllBytes(Paths.get("1"));
    final Parser2 parser = new Parser2(bytes);
    parser.parse();
  }

  public Parser2(final byte[] script) {
    this.state = new State(script);
  }

  public void parse() {
    final Set<Integer> entrypoints = this.getEntrypoints();

    for(final int entrypoint : entrypoints) {
      this.probeBranch(entrypoint);
    }

    System.out.println();
  }

  private void probeBranch(final int offset) {
    // Made our way into another branch, no need to parse again
    if(this.branches.contains(offset)) {
      return;
    }

    System.out.printf("Probing branch %x%n", offset);
    this.branches.add(offset);

    final int oldHeaderOffset = this.state.headerOffset();
    final int oldCurrentOffset = this.state.currentOffset();

    this.state.jump(offset);

    outer:
    while(this.state.hasMore()) {
      this.state.step();

      final int opCode = this.state.currentWord();
      final Ops op = this.parseHeader(this.state.currentOffset());
      this.hits.add(this.state.currentOffset());
      this.state.advance();

      if(op == null) { // Invalid op or invalid param count
        //TODO ran into invalid code
        break;
      }

      final int paramCount = opCode >> 8 & 0xff;
      final int parentParam = opCode >> 16;

      final Parameters[] params = new Parameters[paramCount];
      final OptionalInt[] paramValues = new OptionalInt[paramCount];
      for(int i = 0; i < params.length; i++) {
        final Parameters param = Parameters.byOpcode(this.state.paramType());
        params[i] = param;
        paramValues[i] = this.parseParamValue(this.state, param);
      }

      switch(op) {
        case CALL -> {} //TODO do we need to do anything with this?

        case JUMP -> {
          paramValues[0].ifPresentOrElse(this::probeBranch, () -> System.out.printf("Skipping JUMP at %x due to unknowable parameter%n", this.state.headerOffset()));

          if(paramValues[0].isPresent()) {
            break outer;
          }
        }

        case COMP_JUMP, COMP_JUMP_0 -> {
          paramValues[op.params.length - 1].ifPresentOrElse(addr -> {
            this.probeBranch(this.state.currentOffset());
            this.probeBranch(addr);
          }, () ->
            System.out.printf("Skipping %s at %x due to unknowable parameter%n", op, this.state.headerOffset())
          );

          if(paramValues[op.params.length - 1].isPresent()) {
            break outer;
          }
        }

        case JUMP_TABLE -> {} //TODO

        case GOSUB -> paramValues[0].ifPresentOrElse(this::probeBranch, () -> System.out.printf("Skipping GOSUB at %x due to unknowable parameter%n", this.state.headerOffset()));

        case REWIND, RETURN, DEALLOCATE, DEALLOCATE82 -> {
          break outer;
        }

        case FORK, FORK_REENTER, CONSUME -> {} //TODO

        case GOSUB_TABLE -> {} //TODO
      }
    }

    this.state.headerOffset(oldHeaderOffset);
    this.state.currentOffset(oldCurrentOffset);
  }

  private Set<Integer> getEntrypoints() {
    final Set<Integer> entries = new HashSet<>();

    for(int i = 0; i < 0x10; i++) {
      final int entrypoint = state.currentWord();

      if((entrypoint & 0x3) != 0) {
        break;
      }

      if(entrypoint >= this.state.length()) {
        break;
      }

      if(this.parseHeader(entrypoint) == null) {
        break;
      }

      entries.add(entrypoint);
      this.state.advance();
    }

    return entries;
  }

  private Ops parseHeader(final int offset) {
    final int op = this.state.wordAt(offset);
    final Ops opcode = Ops.byOpcode(op & 0xff);

    if(opcode == null) {
      return null;
    }

    //TODO once we implement all subfuncs, add their param counts too
    final int paramCount = op >> 8 & 0xff;
    if(opcode != Ops.CALL && opcode.params.length != paramCount) {
      return null;
    }

    return opcode;
  }

  private OptionalInt parseParamValue(final State state, final Parameters param) {
    final OptionalInt value = switch(param) {
      case IMMEDIATE -> OptionalInt.of(state.currentWord());
      case NEXT_IMMEDIATE -> OptionalInt.of(state.wordAt(state.currentOffset() + 4));
      //TODO case STORAGE is this possible?
      case INLINE_1 -> OptionalInt.of(state.headerOffset() + (short)state.currentWord() * 0x4);
      default -> OptionalInt.empty();
    };

    this.state.advance(param.width);
    return value;
  }
}
