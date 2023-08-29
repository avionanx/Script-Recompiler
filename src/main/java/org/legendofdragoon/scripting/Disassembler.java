package org.legendofdragoon.scripting;

import org.legendofdragoon.scripting.tokens.Data;
import org.legendofdragoon.scripting.tokens.Entrypoint;
import org.legendofdragoon.scripting.tokens.Op;
import org.legendofdragoon.scripting.tokens.Param;
import org.legendofdragoon.scripting.tokens.Script;

import java.util.OptionalInt;

public class Disassembler {
  private final ScriptMeta meta;
  private final State state;

  public Disassembler(final byte[] script, final ScriptMeta meta) {
    this.meta = meta;
    this.state = new State(script);
  }

  public Script disassemble() {
    final Script script = new Script(this.state.length() / 4);

    this.getEntrypoints(script);

    for(final int entrypoint : script.entrypoints) {
      this.probeBranch(script, entrypoint);
    }

    this.fillData(script);

    System.out.println("Probing complete");
    System.out.println();

    return script;
  }

  private void probeBranch(final Script script, final int offset) {
    // Made our way into another branch, no need to parse again
    if(script.branches.contains(offset)) {
      return;
    }

    System.out.printf("Probing branch %x%n", offset);
    script.branches.add(offset);

    final int oldHeaderOffset = this.state.headerOffset();
    final int oldCurrentOffset = this.state.currentOffset();

    this.state.jump(offset);

    outer:
    while(this.state.hasMore()) {
      this.state.step();

      final Op op = this.parseHeader(this.state.currentOffset());

      if(op == null) { // Invalid op or invalid param count
        //TODO ran into invalid code
        break;
      }

      this.state.advance();

      int entryOffset = this.state.headerOffset() / 4;
      script.entries[entryOffset++] = op;

      for(int i = 0; i < op.params.length; i++) {
        final ParameterType paramType = ParameterType.byOpcode(this.state.paramType());

        final String label;
        if(paramType.isInline()) {
          label = "LABEL_" + script.getLabelCount();
        } else {
          label = null;
        }

        final int[] rawValues = new int[paramType.width];
        for(int n = 0; n < paramType.width; n++) {
          rawValues[n] = this.state.wordAt(this.state.currentOffset() + n * 0x4);
        }

        final Param param = new Param(this.state.currentOffset(), paramType, rawValues, this.parseParamValue(this.state, paramType), label);

        if(label != null && param.resolvedValue.isPresent()) {
          script.addLabel(param.resolvedValue.getAsInt(), label);
        }

        for(int n = 0; n < paramType.width; n++) {
          script.entries[entryOffset++] = param;
        }

        op.params[i] = param;
      }

      switch(op.type) {
        case CALL -> {
          final ScriptMeta.ScriptMethod method = this.meta.methods[op.headerParam];

          if(this.meta.methods[op.headerParam].params.length != op.params.length) {
            throw new RuntimeException("CALL " + op.headerParam + " has wrong number of args! " + method.params.length + "/" + op.params.length);
          }

          for(int i = 0; i < op.params.length; i++) {
            final ScriptMeta.ScriptParam param = method.params[i];

            if(!"none".equalsIgnoreCase(param.branch)) {
              op.params[i].resolvedValue.ifPresentOrElse(offset1 -> {
                if("gosub".equalsIgnoreCase(param.branch)) {
                  script.subs.add(offset1);
                } else if("reentry".equalsIgnoreCase(param.branch)) {
                  script.reentries.add(offset1);
                }

                this.probeBranch(script, offset1);
              }, () -> System.out.printf("Skipping CALL at %x due to unknowable parameter%n", this.state.headerOffset()));
            }
          }
        }

        case JMP -> {
          op.params[0].resolvedValue.ifPresentOrElse(offset1 -> this.probeBranch(script, offset1), () -> System.out.printf("Skipping JUMP at %x due to unknowable parameter%n", this.state.headerOffset()));

          if(op.params[0].resolvedValue.isPresent()) {
            break outer;
          }
        }

        case JMP_COND, JMP_COND_0 -> {
          op.params[op.params.length - 1].resolvedValue.ifPresentOrElse(addr -> {
            this.probeBranch(script, this.state.currentOffset());
            this.probeBranch(script, addr);
          }, () ->
            System.out.printf("Skipping %s at %x due to unknowable parameter%n", op.type, this.state.headerOffset())
          );

          if(op.params[op.params.length - 1].resolvedValue.isPresent()) {
            break outer;
          }
        }

        case JMP_TABLE -> {
          op.params[1].resolvedValue.ifPresentOrElse(offset1 -> {
            final int startOffset = offset1;
            int subOffset;

            script.jumpTables.add(startOffset);

            while(this.isValidOp(subOffset = startOffset + this.state.wordAt(offset1) * 0x4)) {
              script.jumpTableDests.add(subOffset);
              this.probeBranch(script, subOffset);
              offset1 += 0x4;
            }
          }, () -> System.out.printf("Skipping JMP_TABLE at %x due to unknowable parameter%n", this.state.headerOffset()));

          if(op.params[1].resolvedValue.isPresent()) {
            break outer;
          }
        }

        case GOSUB -> op.params[0].resolvedValue.ifPresentOrElse(offset1 -> {
          script.subs.add(offset1);
          this.probeBranch(script, offset1);
        }, () -> System.out.printf("Skipping GOSUB at %x due to unknowable parameter%n", this.state.headerOffset()));

        case REWIND, RETURN, DEALLOCATE, DEALLOCATE82, CONSUME -> {
          break outer;
        }

        // Don't need to handle re-entry because we're already probing all entry points
        // case FORK_REENTER -> System.err.printf("Unhandled FORK_REENTER @ %x%n", this.state.headerOffset());

        case FORK -> op.params[0].resolvedValue.ifPresentOrElse(offset1 -> {
          script.reentries.add(offset1);
          this.probeBranch(script, offset1);
        }, () -> System.out.printf("Skipping FORK at %x due to unknowable parameter%n", this.state.headerOffset()));

        case GOSUB_TABLE -> op.params[1].resolvedValue.ifPresentOrElse(offset1 -> {
          final int startOffset = offset1;
          int subOffset;

          script.subTables.add(startOffset);

          while(this.isValidOp(subOffset = startOffset + this.state.wordAt(offset1) * 0x4)) {
            script.subs.add(subOffset);
            this.probeBranch(script, subOffset);
            offset1 += 0x4;
          }
        }, () -> System.out.printf("Skipping GOSUB_TABLE at %x due to unknowable parameter%n", this.state.headerOffset()));
      }
    }

    this.state.headerOffset(oldHeaderOffset);
    this.state.currentOffset(oldCurrentOffset);
  }

  private void fillData(final Script script) {
    for(int i = 0; i < script.entries.length; i++) {
      if(script.entries[i] == null) {
        script.entries[i] = new Data(i * 0x4, this.state.wordAt(i * 0x4));
      }
    }
  }

  private void getEntrypoints(final Script script) {
    for(int i = 0; i < 0x10; i++) {
      final int entrypoint = state.currentWord();

      if(!this.isValidOp(entrypoint)) {
        break;
      }

      script.entries[i] = new Entrypoint(i * 0x4, entrypoint);
      script.entrypoints.add(entrypoint);
      this.state.advance();
    }
  }

  private Op parseHeader(final int offset) {
    final int opcode = this.state.wordAt(offset);
    final OpType type = OpType.byOpcode(opcode & 0xff);

    if(type == null) {
      return null;
    }

    //TODO once we implement all subfuncs, add their param counts too
    final int paramCount = opcode >> 8 & 0xff;
    if(type != OpType.CALL && type.paramNames.length != paramCount) {
      return null;
    }

    final int opParam = opcode >> 16;
    return new Op(offset, type, opParam, paramCount);
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

  private OptionalInt parseParamValue(final State state, final ParameterType param) {
    final OptionalInt value = switch(param) {
      case IMMEDIATE -> OptionalInt.of(state.currentWord());
      case NEXT_IMMEDIATE -> OptionalInt.of(state.wordAt(state.currentOffset() + 4));
      //TODO case STORAGE is this possible?
      case INLINE_1, INLINE_2, INLINE_3 -> OptionalInt.of(state.headerOffset() + (short)state.currentWord() * 0x4);
      case INLINE_4, INLINE_6, INLINE_7 -> OptionalInt.of(state.headerOffset() + 0x4);
      case INLINE_5 -> OptionalInt.of(state.headerOffset() + ((short)state.currentWord() + state.param2()) * 4);
      default -> OptionalInt.empty();
    };

    this.state.advance(param.width);
    return value;
  }
}
