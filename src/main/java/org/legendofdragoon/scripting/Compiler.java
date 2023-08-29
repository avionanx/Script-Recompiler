package org.legendofdragoon.scripting;

import org.legendofdragoon.scripting.tokens.Data;
import org.legendofdragoon.scripting.tokens.Entry;
import org.legendofdragoon.scripting.tokens.Entrypoint;
import org.legendofdragoon.scripting.tokens.Op;
import org.legendofdragoon.scripting.tokens.Param;
import org.legendofdragoon.scripting.tokens.Script;

public class Compiler {
  public int[] compile(final Script script) {
    final int[] out = new int[script.entries.length];

    for(int entryIndex = 0; entryIndex < script.entries.length; entryIndex++) {
      final Entry entry = script.entries[entryIndex];

      if(entry instanceof final Entrypoint entrypoint) {
        out[entryIndex] = entrypoint.destination;
      } else if(entry instanceof final Data data) {
        out[entryIndex] = data.value;
      } else if(entry instanceof final Op op) {
        out[entryIndex] = op.type.opcode | op.params.length << 8 | op.headerParam << 16;

        for(final Param param : op.params) {
          for(int i = 0; i < param.type.width; i++) {
            out[++entryIndex] = param.rawValues[i];
          }
        }
      }
    }

    return out;
  }
}
