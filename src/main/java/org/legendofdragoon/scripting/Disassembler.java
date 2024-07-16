package org.legendofdragoon.scripting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.legendofdragoon.scripting.meta.Meta;
import org.legendofdragoon.scripting.tokens.Data;
import org.legendofdragoon.scripting.tokens.Entry;
import org.legendofdragoon.scripting.tokens.Entrypoint;
import org.legendofdragoon.scripting.tokens.LodString;
import org.legendofdragoon.scripting.tokens.Op;
import org.legendofdragoon.scripting.tokens.Param;
import org.legendofdragoon.scripting.tokens.PointerTable;
import org.legendofdragoon.scripting.tokens.Script;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class Disassembler {
  private static final Logger LOGGER = LogManager.getFormatterLogger();
  private static final Marker DISASSEMBLY = MarkerManager.getMarker("DISASSEMBLY");

  private final Meta meta;
  private State state;

  public Disassembler(final Meta meta) {
    this.meta = meta;
  }

  public Script disassemble(final byte[] bytes, final int[] extraBranches) {
    this.state = new State(bytes);

    final Script script = new Script(this.state.length() / 4);

    this.getEntrypoints(script);

    for(final int entrypoint : script.entrypoints) {
      this.probeBranch(script, entrypoint);
    }

    for(int entryIndex = 0; entryIndex < script.entries.length; entryIndex++) {
      final Entry entry = script.entries[entryIndex];

      if(entry instanceof final PointerTable rel) {
        entryIndex++;

        for(int labelIndex = 1; labelIndex < rel.labels.length; labelIndex++) {
          // If this table overruns something else, bail out
          if(
            script.entries[entryIndex] != null && !(script.entries[entryIndex] instanceof Data) ||
            script.labels.containsKey(entryIndex * 4) // If something else points to data here, the table must have ended
          ) {
            LOGGER.warn("Jump table overrun at %x", entry.address);

            for(int toRemove = labelIndex; toRemove < rel.labels.length; toRemove++) {
              // If this is the last usage of the label, remove it
              if(script.labelUsageCount.get(rel.labels[toRemove]) <= 1) {
                for(final List<String> labels : script.labels.values()) {
                  labels.remove(rel.labels[toRemove]);
                }
              }
            }

            rel.labels = Arrays.copyOfRange(rel.labels, 0, labelIndex);
            entryIndex--; // Backtrack so we can process the data we collided with
            break;
          }

          entryIndex++;
        }
      }
    }

    for(final int extraBranch : extraBranches) {
      this.probeBranch(script, extraBranch);
    }

    script.buildStrings.forEach(Runnable::run);

    this.fillStrings(script);
    this.fillData(script);

    //LOGGER.info(DISASSEMBLY, "Probing complete");

    return script;
  }

  private void probeBranch(final Script script, final int offset) {
    // Made our way into another branch, no need to parse again
    if(script.branches.contains(offset)) {
      return;
    }

    //LOGGER.info(DISASSEMBLY, "Probing branch %x", offset);
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

        final int[] rawValues = new int[paramType.getWidth(this.state)];
        for(int n = 0; n < paramType.getWidth(this.state); n++) {
          rawValues[n] = this.state.wordAt(this.state.currentOffset() + n * 0x4);
        }

        final int paramOffset = this.state.currentOffset();
        final OptionalInt resolved = this.parseParamValue(this.state, paramType);
        final Param param = new Param(paramOffset, paramType, rawValues, resolved, paramType.isInline() && resolved.isPresent() ? script.addLabel(resolved.getAsInt(), "LABEL_" + script.getLabelCount()) : null);

        for(int n = 0; n < paramType.getWidth(param); n++) {
          script.entries[entryOffset++] = param;
        }

        if(!paramType.isInline() || resolved.orElse(0) < script.entries.length * 4) {
          op.params[i] = param;
        } else {
          LOGGER.warn("Pointer at 0x%x destination is past the end of the script, replacing with 0", paramOffset);
          op.params[i] = new Param(paramOffset, ParameterType.IMMEDIATE, new int[] {ParameterType.IMMEDIATE.opcode << 24}, OptionalInt.of(0), null);
          continue;
        }

        // Handle jump table params
        if(paramType.isInlineTable() && op.type != OpType.GOSUB_TABLE && op.type != OpType.JMP_TABLE) {
          if(op.type == OpType.CALL && !"none".equalsIgnoreCase(this.meta.methods[op.headerParam].params[i].branch)) {
            final Set<Integer> tableDestinations = switch(this.meta.methods[op.headerParam].params[i].branch.toLowerCase()) {
              case "jump" -> script.jumpTableDests;
              case "subroutine" -> script.subs;
              case "reentry" -> script.reentries;
              default -> {
                LOGGER.warn("Unknown branch type %s", this.meta.methods[op.headerParam].params[i].branch);
                yield new HashSet<>();
              }
            };

            param.resolvedValue.ifPresent(tableAddress -> this.probeTableOfBranches(script, tableDestinations, tableAddress));
          } else {
            final int finalI = i;
            param.resolvedValue.ifPresent(tableAddress -> this.handlePointerTable(script, op, finalI, tableAddress, script.buildStrings));
          }
        } else if(op.type == OpType.CALL && "string".equalsIgnoreCase(this.meta.methods[op.headerParam].params[i].type)) {
          // Resolve strings that are pointed to by a non-table inline
          param.resolvedValue.ifPresent(stringAddress ->
            script.buildStrings.add(() ->
              script.strings.add(new StringInfo(stringAddress, -1)) // We don't know the length
            )
          );
        }
      }

      switch(op.type) {
        case CALL -> {
          final Meta.ScriptMethod method = this.meta.methods[op.headerParam];

          if(this.meta.methods[op.headerParam].params.length != op.params.length) {
//            throw new RuntimeException("CALL " + op.headerParam + " (" + this.meta.methods[op.headerParam] + ") has wrong number of args! " + method.params.length + '/' + op.params.length);
          }

          for(int i = 0; i < this.meta.methods[op.headerParam].params.length; i++) {
            final Meta.ScriptParam param = method.params[i];

            if(!"none".equalsIgnoreCase(param.branch)) {
              op.params[i].resolvedValue.ifPresentOrElse(offset1 -> {
                if("gosub".equalsIgnoreCase(param.branch)) {
                  script.subs.add(offset1);
                } else if("reentry".equalsIgnoreCase(param.branch)) {
                  script.reentries.add(offset1);
                }

                this.probeBranch(script, offset1);
              }, () -> LOGGER.warn("Skipping CALL at %x due to unknowable parameter", this.state.headerOffset()));
            }
          }
        }

        case JMP -> {
          op.params[0].resolvedValue.ifPresentOrElse(offset1 -> this.probeBranch(script, offset1), () -> LOGGER.warn("Skipping JUMP at %x due to unknowable parameter", this.state.headerOffset()));

          if(op.params[0].resolvedValue.isPresent()) {
            break outer;
          }
        }

        case JMP_CMP, JMP_CMP_0 -> {
          op.params[op.params.length - 1].resolvedValue.ifPresentOrElse(addr -> {
            this.probeBranch(script, this.state.currentOffset());
            this.probeBranch(script, addr);
          }, () ->
            LOGGER.warn("Skipping %s at %x due to unknowable parameter", op.type, this.state.headerOffset())
          );

          // Jumps are terminal
          break outer;
        }

        case JMP_TABLE -> {
          op.params[1].resolvedValue.ifPresentOrElse(tableOffset -> {
            if(tableOffset != 0) { // Table out of bounds gets replaced with 0 above
              if(op.params[1].type.isInlineTable()) {
                this.probeTableOfTables(script, script.jumpTableDests, tableOffset);
              } else {
                this.probeTableOfBranches(script, script.jumpTableDests, tableOffset);
              }
            }
          }, () -> LOGGER.warn("Skipping JMP_TABLE at %x due to unknowable parameter", this.state.headerOffset()));

          // Jumps are terminal
          break outer;
        }

        case GOSUB -> op.params[0].resolvedValue.ifPresentOrElse(offset1 -> {
          script.subs.add(offset1);
          this.probeBranch(script, offset1);
        }, () -> LOGGER.warn("Skipping GOSUB at %x due to unknowable parameter", this.state.headerOffset()));

        case GOSUB_TABLE -> op.params[1].resolvedValue.ifPresentOrElse(tableOffset -> {
          if(tableOffset != 0) { // Table out of bounds gets replaced with 0 above
            if(op.params[1].type.isInlineTable()) {
              this.probeTableOfTables(script, script.subs, tableOffset);
            } else {
              this.probeTableOfBranches(script, script.subs, tableOffset);
            }
          }
        }, () -> LOGGER.warn("Skipping GOSUB_TABLE at %x due to unknowable parameter", this.state.headerOffset()));

        case REWIND, RETURN, DEALLOCATE, DEALLOCATE82, CONSUME -> {
          break outer;
        }

        // Don't need to handle re-entry because we're already probing all entry points
        // case FORK_REENTER -> System.err.printf("Unhandled FORK_REENTER @ %x", this.state.headerOffset());

        case FORK -> op.params[1].resolvedValue.ifPresentOrElse(offset1 -> {
          script.reentries.add(offset1);
          this.probeBranch(script, offset1);
        }, () -> LOGGER.warn("Skipping FORK at %x due to unknowable parameter", this.state.headerOffset()));
      }
    }

    this.state.headerOffset(oldHeaderOffset);
    this.state.currentOffset(oldCurrentOffset);
  }

  private void probeTableOfTables(final Script script, final Set<Integer> tableDestinations, final int tableAddress) {
    this.probeTable(script, script.subTables, tableDestinations, tableAddress, subtableAddress -> !this.isProbablyOp(script, subtableAddress), subtableAddress -> this.probeTableOfBranches(script, tableDestinations, subtableAddress));
  }

  private void probeTableOfBranches(final Script script, final Set<Integer> tableDestinations, final int subtableAddress) {
    this.probeTable(script, script.subTables, tableDestinations, subtableAddress, this::isValidOp, branchAddress -> this.probeBranch(script, branchAddress));
  }

  private void probeTable(final Script script, final Set<Integer> tables, final Set<Integer> tableDestinations, final int tableAddress, final Predicate<Integer> destinationAddressHeuristic, final Consumer<Integer> visitor) {
    if(tables.contains(tableAddress)) {
      return;
    }

    tables.add(tableAddress);

    int earliestDestination = this.state.length();
    int latestDestination = 0;
    final List<Integer> destinations = new ArrayList<>();
    final List<String> labels = new ArrayList<>();
    for(int entryAddress = tableAddress; entryAddress <= this.state.length() - 4 && script.entries[entryAddress / 4] == null && (this.state.wordAt(entryAddress) > 0 ? entryAddress < earliestDestination : entryAddress > latestDestination) && (!this.isProbablyOp(script, entryAddress) || this.isValidOp(tableAddress + this.state.wordAt(entryAddress) * 0x4)); entryAddress += 0x4) {
      final int destAddress = tableAddress + this.state.wordAt(entryAddress) * 0x4;

      if(destAddress < 0x4 || destAddress > this.state.length() - 0x4) {
        break;
      }

      if(!destinationAddressHeuristic.test(destAddress)) {
        break;
      }

      if(earliestDestination > destAddress) {
        earliestDestination = destAddress;
      }

      if(latestDestination < destAddress) {
        latestDestination = destAddress;
      }

      tableDestinations.add(destAddress);
      destinations.add(destAddress);
      labels.add(script.addLabel(destAddress, "JMP_%x_%d".formatted(tableAddress, labels.size())));
    }

    if(labels.isEmpty()) {
      throw new RuntimeException("Empty table at 0x%x".formatted(tableAddress));
    }

    script.entries[tableAddress / 0x4] = new PointerTable(tableAddress, labels.toArray(String[]::new));

    // Visit tables in reverse order so that it's easier to determine where tables end
    destinations.stream().distinct().sorted(Comparator.reverseOrder()).forEach(visitor);
  }

  private void handlePointerTable(final Script script, final Op op, final int paramIndex, final int tableAddress, final List<Runnable> buildStrings) {
    if(tableAddress / 4 >= script.entries.length) {
      LOGGER.warn("Op %s param %d points to invalid pointer table 0x%x", op, paramIndex, tableAddress);
      return;
    }

    if(script.entries[tableAddress / 0x4] != null) {
      return;
    }

    final List<Integer> destinations = new ArrayList<>();
    int entryCount = 0;

    int earliestDestination = this.state.length();
    int latestDestination = 0;
    for(int entryAddress = tableAddress; entryAddress <= this.state.length() - 4 && script.entries[entryAddress / 4] == null && (this.state.wordAt(entryAddress) > 0 ? entryAddress < earliestDestination : entryAddress > latestDestination); entryAddress += 0x4) {
      int destination = tableAddress + this.state.wordAt(entryAddress) * 0x4;

      if(op.type == OpType.CALL && "string".equalsIgnoreCase(this.meta.methods[op.headerParam].params[paramIndex].type)) {
        if(script.entries[entryAddress / 4] instanceof Op) {
          break;
        }

        if(this.isProbablyOp(script, entryAddress)) {
          boolean foundTerminator = false;

          // Look for a string terminator at the destination
          for(int i = destination / 4; i < destination / 4 + 300; i++) {
            // We ran into another entry or the end of the script
            if(i >= script.entries.length || script.entries[i] != null) {
              break;
            }

            final int word = this.state.wordAt(i * 0x4);
            if((word & 0xffff) == 0xa0ff || (word >> 16 & 0xffff) == 0xa0ff) {
              foundTerminator = true;
              break;
            }
          }

          if(!foundTerminator) {
            break;
          }
        }
      } else if(this.isProbablyOp(script, entryAddress)) {
        break;
      }

      if(destination >= this.state.length() - 0x4) {
        break;
      }

      if(earliestDestination > destination) {
        earliestDestination = destination;
      }

      if(latestDestination < destination) {
        latestDestination = destination;
      }

      if(op.type == OpType.GOSUB_TABLE || op.type == OpType.JMP_TABLE) {
        destination = tableAddress + this.state.wordAt(destination) * 0x4;
      }

      destinations.add(destination);
      entryCount++;
    }

    final String[] labels = new String[entryCount];
    for(int entryIndex = 0; entryIndex < entryCount; entryIndex++) {
      labels[entryIndex] = script.addLabel(destinations.get(entryIndex), "PTR_%x_%d".formatted(tableAddress, entryIndex));
    }

    final PointerTable table = new PointerTable(tableAddress, labels);
    script.entries[tableAddress / 0x4] = table;

    // Add string entries if appropriate
    if(op.type == OpType.CALL) {
      if("string".equalsIgnoreCase(this.meta.methods[op.headerParam].params[paramIndex].type)) {
        buildStrings.add(() -> {
          //IMPORTANT: we need to remove any extra elements that were truncated by the table overrun detector
          while(destinations.size() > table.labels.length) {
            destinations.removeLast();
          }

          destinations.sort(Integer::compareTo);

          for(int i = 0; i < destinations.size(); i++) {
            if(i < destinations.size() - 1) {
              script.strings.add(new StringInfo(destinations.get(i), destinations.get(i + 1) - destinations.get(i))); // String length is next string - this string
            } else {
              script.strings.add(new StringInfo(destinations.get(i), -1)); // We don't know the length
            }
          }
        });
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
    for(int i = 0; i < 0x20 && this.state.hasMore(); i++) { // Most have 0x10, some have less, player_combat_script is the only one I've seen with 0x20
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
    if(offset > this.state.length() - 4) {
      return null;
    }

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

    if(offset < 0x4 || offset >= this.state.length()) {
      return false;
    }

    return this.parseHeader(offset) != null;
  }

  private boolean isProbablyOp(final Script script, int address) {
    if((address & 0x3) != 0) {
      return false;
    }

    if(address < 0x4 || address >= this.state.length()) {
      return false;
    }

    if(script.entries[address / 4] instanceof Op) {
      return true;
    }

    final int testCount = 3;
    int certainty = 0;
    for(int opIndex = 0; opIndex < testCount; opIndex++) {
      final Op op = this.parseHeader(address);

      if(op == null) {
        certainty -= testCount - opIndex;
        break;
      }

      certainty += opIndex + 1;

      // If we read valid params that aren't immediates, it's probably an op
      address += 0x4;

      for(int paramIndex = 0; paramIndex < op.type.paramNames.length; paramIndex++) {
        final ParameterType parameterType = ParameterType.byOpcode(this.state.wordAt(address) >>> 24);

        if(parameterType != ParameterType.IMMEDIATE) {
          certainty += 1;
        }

        address += parameterType.getWidth((String)null) * 0x4; //TODO
      }
    }

    return certainty >= 2;
  }

  private OptionalInt parseParamValue(final State state, final ParameterType param) {
    final OptionalInt value = switch(param) {
      case IMMEDIATE -> OptionalInt.of(state.currentWord());
      case NEXT_IMMEDIATE -> OptionalInt.of(state.wordAt(state.currentOffset() + 4));
      //TODO case STORAGE is this possible?
      case INLINE_1, INLINE_2, INLINE_TABLE_1, INLINE_TABLE_3 -> OptionalInt.of(state.headerOffset() + (short)state.currentWord() * 0x4);
//      case INLINE_TABLE_1 -> OptionalInt.of(state.headerOffset() + ((short)state.currentWord() + state.wordAt(state.headerOffset() + (short)state.currentWord() * 0x4)) * 0x4);
      case INLINE_TABLE_2, INLINE_TABLE_4 -> OptionalInt.of(state.headerOffset() + 0x4);
      case INLINE_3 -> OptionalInt.of(state.headerOffset() + ((short)state.currentWord() + state.param2()) * 4);
//      case INLINE_TABLE_3 -> OptionalInt.of(state.headerOffset() + ((short)state.currentWord() + state.wordAt(state.headerOffset() + ((short)state.currentWord() + state.param2()) * 0x4)) * 0x4);
      default -> OptionalInt.empty();
    };

    this.state.advance(param.getWidth(state));
    return value;
  }
}
