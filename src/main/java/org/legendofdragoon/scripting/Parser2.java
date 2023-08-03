package org.legendofdragoon.scripting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Parser2 {
  private final State state;

  private final Map<Integer, Integer> hits = new HashMap<>();

  public static void main(final String[] args) throws IOException {
    final byte[] bytes = Files.readAllBytes(Paths.get("28"));
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
    state.jump(offset);
    this.addHit(offset);
  }

  private void addHit(final int offset) {
    this.hits.putIfAbsent(offset, 0);
    this.hits.compute(offset, (key, val) -> val + 1);
  }

  private Set<Integer> getEntrypoints() {
    final Set<Integer> entries = new HashSet<>();

    for(int i = 0; i < 0x10; i++) {
      final int entrypoint = state.currentCommand();

      if((entrypoint & 0x3) != 0) {
        break;
      }

      if(!this.looksLikeHeader(entrypoint)) {
        break;
      }

      if(entrypoint >= this.state.length()) {
        break;
      }

      entries.add(entrypoint);
      this.state.advance();
    }

    return entries;
  }

  private boolean looksLikeHeader(final int offset) {
    final int op = this.state.commandAt(offset);
    final Ops opcode = Ops.byOpcode(op & 0xff);

    if(opcode == null) {
      return false;
    }

    //TODO once we implement all subfuncs, add their param counts too
    final int paramCount = op >> 8 & 0xff;
    return opcode == Ops.CALL || opcode.params.length == paramCount;
  }
}
