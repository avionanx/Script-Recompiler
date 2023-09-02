package org.legendofdragoon.scripting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.legendofdragoon.scripting.tokens.Data;
import org.legendofdragoon.scripting.tokens.Entry;
import org.legendofdragoon.scripting.tokens.Entrypoint;
import org.legendofdragoon.scripting.tokens.LodString;
import org.legendofdragoon.scripting.tokens.Op;
import org.legendofdragoon.scripting.tokens.Param;
import org.legendofdragoon.scripting.tokens.PointerTable;
import org.legendofdragoon.scripting.tokens.Script;

import java.util.Arrays;
import java.util.stream.Collectors;

public class Translator {
  private static final Logger LOGGER = LogManager.getFormatterLogger();

  public String translate(final Script script, final ScriptMeta meta) {
    final StringBuilder builder = new StringBuilder();

    for(int entryIndex = 0; entryIndex < script.entries.length; entryIndex++) {
      final Entry entry = script.entries[entryIndex];
      if(script.subs.contains(entry.address)) {
        builder.append("\n; SUBROUTINE\n");
      }

      if(script.subTables.contains(entry.address)) {
        builder.append("\n; SUBROUTINE TABLE\n");
      }

      if(script.reentries.contains(entry.address)) {
        builder.append("\n; FORK RE-ENTRY\n");
      }

      if(script.jumpTables.contains(entry.address)) {
        builder.append("\n; JUMP TABLE\n");
      }

      if(script.labels.containsKey(entry.address)) {
        for(final String label : script.labels.get(entry.address)) {
          builder.append(label).append(":\n");
        }
      }

      if(entry instanceof final Entrypoint entrypoint) {
        builder.append("%x ".formatted(entry.address)).append("entrypoint :").append(entrypoint.destination).append('\n');
      } else if(entry instanceof final Data data) {
        builder.append("%x ".formatted(entry.address)).append("data 0x%x".formatted(data.value)).append('\n');
      } else if(entry instanceof final PointerTable rel) {
        if(rel.labels.length == 0) {
          throw new RuntimeException("Empty jump table %x".formatted(rel.address));
        }

        for(int i = 0; i < rel.labels.length; i++) {
          // If this table overruns something else, bail out
          if(i != 0 && !(script.entries[entryIndex] instanceof Data)) {
            LOGGER.warn("Jump table overrun at %x", entry.address);
            break;
          }

          builder.append("%x ".formatted(entry.address + i * 0x4)).append("rel :").append(rel.labels[i]).append('\n');
          entryIndex++;
        }

        entryIndex--;
      } else if(entry instanceof final LodString string) {
        builder.append("%x ".formatted(entry.address)).append("data str[").append(string).append("]\n");
        entryIndex += string.chars.length / 2;
      } else if(entry instanceof final Op op) {
        builder.append("%x ".formatted(entry.address)).append(op.type.name);

        if(op.type == OpType.CALL) {
          builder.append(' ').append(meta.methods[op.headerParam].name);
        } else if(op.type.headerParamName != null) {
          builder.append(" 0x%x".formatted(op.headerParam));
        }

        if(op.type == OpType.WAIT_CMP_0 || op.type == OpType.JMP_CMP_0) {
          builder.append(", 0");
        }

        if(op.type == OpType.MOV_0) {
          builder.append(" 0,");
        }

        for(int paramIndex = 0; paramIndex < op.params.length; paramIndex++) {
          if(paramIndex != 0 || op.type.headerParamName != null) {
            builder.append(',');
          }

          builder.append(' ').append(this.buildParam(op, op.params[paramIndex]));
        }

        if(op.type == OpType.CALL && meta.methods[op.headerParam].params.length != 0) {
          builder.append(" ; ").append(Arrays.stream(meta.methods[op.headerParam].params).map(Object::toString).collect(Collectors.joining(", ")));
        } else if(op.params.length != 0 || op.type.headerParamName != null) {
          builder.append(" ; ");

          if(op.type.headerParamName != null) {
            builder.append(op.type.headerParamName);

            if(op.params.length != 0) {
              builder.append(", ");
            }
          }

          builder.append(String.join(", ", op.type.paramNames));
        }

        builder.append('\n');
      } else if(!(entry instanceof Param)) {
        throw new RuntimeException("Unknown entry " + entry.getClass().getSimpleName());
      }
    }

    return builder.toString();
  }

  private String buildParam(final Op op, final Param param) {
    if(param.label != null) {
      final String label = ':' + param.label;

      return switch(param.type) {
        case INLINE_2 -> "inl[%s[stor[%d]]]".formatted(label, param.rawValues[0] >> 16 & 0xff);
        case INLINE_3 -> "inl[%1$s[%1$s[stor[%2$d]]]]".formatted(label, param.rawValues[0] >> 16 & 0xff);
        case INLINE_4 -> "inl[%1$s[%1$s[stor[%2$d]] + stor[%3$d]]]".formatted(label, param.rawValues[1] & 0xff, param.rawValues[1] >> 8 & 0xff);
        case INLINE_6 -> "inl[%1$s + inl[%1$s + 0x%2$x]]".formatted(label, param.rawValues[0] >> 16 & 0xff);
        case _12 -> throw new RuntimeException("Param type 0x12 not yet supported");
        case _15 -> throw new RuntimeException("Param type 0x15 not yet supported");
        case _16 -> throw new RuntimeException("Param type 0x16 not yet supported");
        case INLINE_7 -> "inl[%1$s[%1$s[%2$d] + %3$d]]".formatted(label, param.rawValues[1] & 0xff, param.rawValues[1] >> 8 & 0xff);
        default -> "inl[:" + param.label + ']';
      };
    }

    return switch(param.type) {
      case IMMEDIATE -> "0x%x".formatted(param.rawValues[0]);
      case NEXT_IMMEDIATE -> "0x%x".formatted(param.rawValues[1]);
      case STORAGE -> "stor[%d]".formatted(param.rawValues[0] & 0xff);
      case OTHER_OTHER_STORAGE -> "stor[stor[stor[%d], %d], %d]".formatted(param.rawValues[0] & 0xff, param.rawValues[0] >> 8 & 0xff, param.rawValues[0] >> 16 & 0xff);
      case OTHER_STORAGE_OFFSET -> "stor[stor[%d], %d + stor[%d]]".formatted(param.rawValues[0] & 0xff, param.rawValues[0] >> 8 & 0xff, param.rawValues[0] >> 16 & 0xff);
      case GAMEVAR_1 -> "var[%d]".formatted(param.rawValues[0] & 0xff);
      case GAMEVAR_2 -> "var[%d + stor[%d]]".formatted(param.rawValues[0] & 0xff, param.rawValues[0] >> 8 & 0xff);
      case GAMEVAR_ARRAY_1 -> "var[%d][stor[%d]]".formatted(param.rawValues[0] & 0xff, param.rawValues[0] >> 8 & 0xff);
      case GAMEVAR_ARRAY_2 -> "var[%d + stor[%d]][stor[%d]]".formatted(param.rawValues[0] & 0xff, param.rawValues[0] >> 8 & 0xff, param.rawValues[0] >> 16 & 0xff);
      case INLINE_1 -> "inl[0x%x]".formatted(op.address + (short)param.rawValues[0] * 4);
      case INLINE_2 -> "inl[0x%x[stor[%d]]]".formatted(op.address + (short)param.rawValues[0] * 4, param.rawValues[0] >> 16 & 0xff);
      case INLINE_3 -> "inl[0x%1$x[0x%1$x[stor[%2$d]]]]".formatted(op.address + (short)param.rawValues[0] * 4, param.rawValues[0] >> 16 & 0xff);
      case INLINE_4 -> "inl[0x%1$x[0x%1$x[stor[%2$d]] + stor[%3$d]]]".formatted(op.address, param.rawValues[1] & 0xff, param.rawValues[1] >> 8 & 0xff);
      case OTHER_STORAGE -> "stor[stor[%d], %d]".formatted(param.rawValues[0] & 0xff, param.rawValues[0] >> 8 & 0xff + param.rawValues[0] >> 16 & 0xff);
      case GAMEVAR_3 -> "var[%d + %d]".formatted(param.rawValues[0] & 0xff, param.rawValues[0] >> 8 & 0xff);
      case GAMEVAR_ARRAY_3 -> "var[%d][%d]".formatted(param.rawValues[0] & 0xff, param.rawValues[0] >> 8 & 0xff);
      case GAMEVAR_ARRAY_4 -> "var[%d + stor[%d]][%d]".formatted(param.rawValues[0] & 0xff, param.rawValues[0] >> 8 & 0xff, param.rawValues[0] >> 16 & 0xff);
      case GAMEVAR_ARRAY_5 -> "var[%d + %d][stor[%d]]".formatted(param.rawValues[0] & 0xff, param.rawValues[0] >> 8 & 0xff, param.rawValues[0] >> 16 & 0xff);
      case _12 -> throw new RuntimeException("Param type 0x12 not yet supported");
      case INLINE_5 -> "inl[0x%x]".formatted(op.address + ((short)param.rawValues[0] + param.rawValues[0] >> 16 & 0xff) * 4);
      case INLINE_6 -> "inl[0x%1$x + inl[0x%1$x + 0x%2$x]]".formatted(op.address + (short)param.rawValues[0] * 4, (param.rawValues[0] >> 16 & 0xff) * 4);
      case _15 -> throw new RuntimeException("Param type 0x15 not yet supported");
      case _16 -> throw new RuntimeException("Param type 0x16 not yet supported");
      case INLINE_7 -> "inl[0x%1$x[0x%1$x[%2$d] + %3$d]]".formatted(op.address, param.rawValues[1] & 0xff, param.rawValues[1] >> 8 & 0xff);
    };
  }
}
