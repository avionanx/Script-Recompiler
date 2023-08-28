package org.legendofdragoon.scripting;

import com.opencsv.exceptions.CsvException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

public class Parser2 {
  private final ScriptMeta meta;

  private final State state;

  private final Set<Integer> entrypoints = new HashSet<>();
  private final Set<Integer> hits = new HashSet<>();
  private final Set<Integer> branches = new HashSet<>();
  private final Set<Integer> subs = new HashSet<>();
  private final Set<Integer> reentries = new HashSet<>();

  public static void main(final String[] args) throws IOException, CsvException {
    final byte[] bytes = Files.readAllBytes(Paths.get("28"));
    final Parser2 parser = new Parser2(bytes);
    parser.parse();
  }

  public Parser2(final byte[] script) throws IOException, CsvException {
    this.meta = new ScriptMeta("https://legendofdragoon.org/scmeta");
    this.state = new State(script);
  }

  public void parse() {
    this.entrypoints.clear();
    this.hits.clear();
    this.branches.clear();
    this.subs.clear();
    this.reentries.clear();

    this.getEntrypoints();

    for(final int entrypoint : this.entrypoints) {
      this.probeBranch(entrypoint);
    }

    System.out.println("Probing complete");
    System.out.println();

    this.outputDisassembly();

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
      final int opParam = opCode >> 16;

      final Parameters[] params = new Parameters[paramCount];
      final OptionalInt[] paramValues = new OptionalInt[paramCount];
      for(int i = 0; i < params.length; i++) {
        final Parameters param = Parameters.byOpcode(this.state.paramType());
        params[i] = param;
        paramValues[i] = this.parseParamValue(this.state, param);
      }

      switch(op) {
        case CALL -> {
          final ScriptMeta.ScriptMethod method = this.meta.methods[opParam];

          if(this.meta.methods[opParam].params.length != params.length) {
            throw new RuntimeException("CALL " + opParam + " has wrong number of args! " + method.params.length + "/" + params.length);
          }

          for(int i = 0; i < params.length; i++) {
            final ScriptMeta.ScriptParam param = method.params[i];

            if(!"none".equalsIgnoreCase(param.branch)) {
              paramValues[i].ifPresentOrElse(offset1 -> {
                if("gosub".equalsIgnoreCase(param.branch)) {
                  this.subs.add(offset1);
                } else if("reentry".equalsIgnoreCase(param.branch)) {
                  this.reentries.add(offset1);
                }

                this.probeBranch(offset1);
              }, () -> System.out.printf("Skipping CALL at %x due to unknowable parameter%n", this.state.headerOffset()));
            }
          }
        }

        case JMP -> {
          paramValues[0].ifPresentOrElse(this::probeBranch, () -> System.out.printf("Skipping JUMP at %x due to unknowable parameter%n", this.state.headerOffset()));

          if(paramValues[0].isPresent()) {
            break outer;
          }
        }

        case JMP_COND, JMP_COND_0 -> {
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

        case JMP_TABLE -> System.err.printf("Unhandled JMP_TABLE @ %x%n", this.state.headerOffset()); //TODO

        case GOSUB -> paramValues[0].ifPresentOrElse(offset1 -> {
          this.subs.add(offset1);
          this.probeBranch(offset1);
        }, () -> System.out.printf("Skipping GOSUB at %x due to unknowable parameter%n", this.state.headerOffset()));

        case REWIND, RETURN, DEALLOCATE, DEALLOCATE82 -> {
          break outer;
        }

        case FORK -> System.err.printf("Unhandled FORK @ %x%n", this.state.headerOffset()); //TODO
        case FORK_REENTER -> System.err.printf("Unhandled FORK_REENTER @ %x%n", this.state.headerOffset()); //TODO
        case CONSUME -> System.err.printf("Unhandled CONSUME @ %x%n", this.state.headerOffset()); //TODO

        case GOSUB_TABLE -> paramValues[1].ifPresentOrElse(offset1 -> {
          final int startOffset = offset1;
          int subOffset;

          while(this.isValidOp(subOffset = startOffset + this.state.wordAt(offset1) * 0x4)) {
            this.subs.add(subOffset);
            this.probeBranch(subOffset);
            offset1 += 0x4;
          }
        }, () -> System.out.printf("Skipping GOSUB_TABLE at %x due to unknowable parameter%n", this.state.headerOffset()));
      }
    }

    this.state.headerOffset(oldHeaderOffset);
    this.state.currentOffset(oldCurrentOffset);
  }

  private void getEntrypoints() {
    for(int i = 0; i < 0x10; i++) {
      final int entrypoint = state.currentWord();

      if(!this.isValidOp(entrypoint)) {
        break;
      }

      this.entrypoints.add(entrypoint);
      this.state.advance();
    }
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

  private boolean isValidOp(final int offset) {
    if((offset & 0x3) != 0) {
      return false;
    }

    if(offset >= this.state.length()) {
      return false;
    }

    if(this.parseHeader(offset) == null) {
      return false;
    }

    return true;
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

  private void outputDisassembly() {
    System.out.println("Entrypoints:");
    this.entrypoints.stream().sorted().forEach(entrypoint -> System.out.printf("%x ", entrypoint));
    System.out.println();
    System.out.println();

    this.state.jump(this.entrypoints.size() * 4);

    while(this.state.hasMore()) {
      state.step();

      if(this.entrypoints.contains(this.state.currentOffset())) {
        System.out.println();
        System.out.println("; ENTRYPOINT");
      }

      if(this.subs.contains(this.state.currentOffset())) {
        System.out.println();
        System.out.println("; SUBROUTINE");
      }

      if(this.reentries.contains(this.state.currentOffset())) {
        System.out.println();
        System.out.println("; FORK RE-ENTRY");
      }

      if(this.hits.contains(this.state.currentOffset())) {
        final int opCode = this.state.currentWord();
        final Ops op = this.parseHeader(this.state.currentOffset());
        final int opParam = opCode >> 16;

        System.out.printf("%x %s", this.state.currentOffset(), op);
        this.state.advance();

        if(op == Ops.CALL) {
          System.out.printf(" %s", this.meta.methods[opParam].name);
        } else {
          if(op.headerParam != null) {
            System.out.printf(" 0x%x", opParam);
          }
        }

        // Advance over params
        final int paramCount = opCode >> 8 & 0xff;
        for(int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
          if(paramIndex != 0 || op.headerParam != null) {
            System.out.print(',');
          }

          final Parameters param = Parameters.byOpcode(this.state.paramType());
          param.act(this.state, paramIndex);
          System.out.printf(" %s", this.state.getParam(paramIndex));
        }

        if(op == Ops.CALL && this.meta.methods[opParam].params.length != 0) {
          System.out.print(" ; ");
          System.out.print(Arrays.stream(this.meta.methods[opParam].params).map(Object::toString).collect(Collectors.joining(", ")));
        }

        System.out.println();
      } else {
        System.out.printf("%x DATA 0x%x%n", this.state.currentOffset(), this.state.currentWord());
        this.state.advance();
      }
    }
  }
}
