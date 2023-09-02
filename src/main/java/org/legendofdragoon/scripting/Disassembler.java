package org.legendofdragoon.scripting;

import org.legendofdragoon.scripting.tokens.Data;
import org.legendofdragoon.scripting.tokens.Entrypoint;
import org.legendofdragoon.scripting.tokens.LodString;
import org.legendofdragoon.scripting.tokens.Op;
import org.legendofdragoon.scripting.tokens.Param;
import org.legendofdragoon.scripting.tokens.PointerTable;
import org.legendofdragoon.scripting.tokens.Script;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

public class Disassembler {
  private final ScriptMeta meta;
  private State state;

  public Disassembler(final ScriptMeta meta) {
    this.meta = meta;
  }

  public Script disassemble(final byte[] bytes) {
    this.state = new State(bytes);

    final Script script = new Script(this.state.length() / 4);

    this.getEntrypoints(script);

    for(final int entrypoint : script.entrypoints) {
      this.probeBranch(script, entrypoint);
    }

    this.fillStrings(script);
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

        final int[] rawValues = new int[paramType.width];
        for(int n = 0; n < paramType.width; n++) {
          rawValues[n] = this.state.wordAt(this.state.currentOffset() + n * 0x4);
        }

        final int paramOffset = this.state.currentOffset();
        final OptionalInt resolved = this.parseParamValue(this.state, paramType);
        final Param param = new Param(paramOffset, paramType, rawValues, resolved, paramType.isInline() && resolved.isPresent() ? script.addLabel(resolved.getAsInt(), "LABEL_" + script.getLabelCount()) : null);

        for(int n = 0; n < paramType.width; n++) {
          script.entries[entryOffset++] = param;
        }

        op.params[i] = param;

        // Handle jump table params
        if(paramType.isRelativeInline()) {
          final int finalI = i;
          param.resolvedValue.ifPresent(tableAddress -> this.handlePointerTable(script, op, finalI, tableAddress));
        }
      }

      switch(op.type) {
        case CALL -> {
          final ScriptMeta.ScriptMethod method = this.meta.methods[op.headerParam];

          if(this.meta.methods[op.headerParam].params.length != op.params.length) {
            throw new RuntimeException("CALL " + op.headerParam + " has wrong number of args! " + method.params.length + '/' + op.params.length);
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

        case JMP_CMP, JMP_CMP_0 -> {
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
          op.params[1].resolvedValue.ifPresentOrElse(tableOffset -> this.handleRelativeTable(script, script.jumpTables, script.jumpTableDests, tableOffset), () -> System.out.printf("Skipping JMP_TABLE at %x due to unknowable parameter%n", this.state.headerOffset()));

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

        case GOSUB_TABLE -> op.params[1].resolvedValue.ifPresentOrElse(tableOffset -> this.handleRelativeTable(script, script.subTables, script.subs, tableOffset), () -> System.out.printf("Skipping GOSUB_TABLE at %x due to unknowable parameter%n", this.state.headerOffset()));
      }
    }

    this.state.headerOffset(oldHeaderOffset);
    this.state.currentOffset(oldCurrentOffset);
  }

  private void handleRelativeTable(final Script script, final Set<Integer> tables, final Set<Integer> destinations, final int tableOffset) {
    if(tables.contains(tableOffset)) {
      return;
    }

    tables.add(tableOffset);

    int destOffset;
    int entryCount = 0;
    while(script.entries[tableOffset / 4 + entryCount] == null && this.isValidOp(destOffset = tableOffset + this.state.wordAt(tableOffset + entryCount * 0x4) * 0x4)) {
      destinations.add(destOffset);
      this.probeBranch(script, destOffset);
      entryCount++;
    }

    final String[] labels = new String[entryCount];
    for(int i = 0; i < entryCount; i++) {
      final int address = tableOffset + i * 0x4;
      labels[i] = script.addLabel(tableOffset + this.state.wordAt(address) * 0x4, "JMP_%x_%d".formatted(tableOffset, i));
    }

    script.entries[tableOffset / 0x4] = new PointerTable(tableOffset, labels);
  }

  private void handlePointerTable(final Script script, final Op op, final int paramIndex, final int tableAddress) {
    if(script.entries[tableAddress / 0x4] != null) {
      return;
    }

    final List<Integer> destinations = new ArrayList<>();
    int entryCount = 0;

    int earliestDestination = this.state.length();
    int latestDestination = 0;
    for(int entryAddress = tableAddress; entryAddress <= this.state.length() - 4 && script.entries[entryAddress / 4] == null && (this.state.wordAt(entryAddress) > 0 ? entryAddress < earliestDestination : entryAddress > latestDestination); entryAddress += 0x4) {
      final int destination = tableAddress + this.state.wordAt(entryAddress) * 0x4;

      if(destination >= this.state.length() - 0x4) {
        break;
      }

      if(earliestDestination > destination) {
        earliestDestination = destination;
      }

      if(latestDestination < destination) {
        latestDestination = destination;
      }

      // Heuristic check: if it's a string param, check if the destination is a param. Some params can look like chars, so we only accept ones that have params.
      if(op.type == OpType.CALL && "string".equalsIgnoreCase(this.meta.methods[op.headerParam].params[paramIndex].type)) {
        final Op destOp = this.parseHeader(destination);

        if(destOp != null && destOp.type.paramNames.length != 0) {
          break;
        }
      }

      destinations.add(destination);
      entryCount++;
    }

    final String[] labels = new String[entryCount];
    for(int entryIndex = 0; entryIndex < entryCount; entryIndex++) {
      labels[entryIndex] = script.addLabel(destinations.get(entryIndex), "PTR_%x_%d".formatted(tableAddress, entryIndex));
    }

    script.entries[tableAddress / 0x4] = new PointerTable(tableAddress, labels);

    // Add string entries if appropriate
    if(op.type == OpType.CALL) {
      if("string".equalsIgnoreCase(this.meta.methods[op.headerParam].params[paramIndex].type)) {
        destinations.sort(Integer::compareTo);

        for(int i = 0; i < destinations.size(); i++) {
          if(i < destinations.size() - 1) {
            script.strings.add(new StringInfo(destinations.get(i), destinations.get(i + 1) - destinations.get(i))); // String length is next string - this string
          } else {
            script.strings.add(new StringInfo(destinations.get(i), -1)); // We don't know the length
          }
        }
      }
    }
  }

  private void fillStrings(final Script script) {
    for(final StringInfo string : script.strings) {
      this.fillString(script, string.start, string.maxLength);
    }
  }

  private void fillString(final Script script, final int address, final int maxLength) {
    final List<Integer> chars = new ArrayList<>();

    for(int i = 0; i < (maxLength != -1 ? maxLength : script.entries.length * 0x4 - address); i++) {
      final int chr = this.state.wordAt(address + i / 2 * 0x4) >>> i % 2 * 16 & 0xffff;

      // String end
      if(chr == 0xa0ff) {
        break;
      }

      chars.add(chr);
    }

    final LodString string = new LodString(address, chars.stream().mapToInt(Integer::intValue).toArray());

    for(int i = 0; i < string.chars.length / 2; i++) {
      script.entries[address / 0x4 + i] = string;
    }
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
      final int entrypoint = this.state.currentWord();

      if(!this.isValidOp(entrypoint)) {
        break;
      }

      final String label = "ENTRYPOINT_" + i;

      script.entries[i] = new Entrypoint(i * 0x4, label);
      script.entrypoints.add(entrypoint);
      script.addUniqueLabel(entrypoint, label);
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

    if(type.headerParamName == null && opParam != 0) {
      return null;
    }

    return new Op(offset, type, opParam, paramCount);
  }

  private boolean isValidOp(final int offset) {
    if((offset & 0x3) != 0) {
      return false;
    }

    if(offset >= this.state.length()) {
      return false;
    }

    return this.parseHeader(offset) != null;
  }

  private OptionalInt parseParamValue(final State state, final ParameterType param) {
    final OptionalInt value = switch(param) {
      case IMMEDIATE -> OptionalInt.of(state.currentWord());
      case NEXT_IMMEDIATE -> OptionalInt.of(state.wordAt(state.currentOffset() + 4));
      //TODO case STORAGE is this possible?
      case INLINE_1, INLINE_2, INLINE_3, INLINE_6 -> OptionalInt.of(state.headerOffset() + (short)state.currentWord() * 0x4);
      case INLINE_4, INLINE_7 -> OptionalInt.of(state.headerOffset() + 0x4);
      case INLINE_5 -> OptionalInt.of(state.headerOffset() + ((short)state.currentWord() + state.param2()) * 4);
      default -> OptionalInt.empty();
    };

    this.state.advance(param.width);
    return value;
  }
}
