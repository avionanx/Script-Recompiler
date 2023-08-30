package org.legendofdragoon.scripting.tokens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Script {
  public final Entry[] entries;
  public final Set<Integer> entrypoints = new HashSet<>();
  public final Set<Integer> branches = new HashSet<>();
  public final Set<Integer> subs = new HashSet<>();
  public final Set<Integer> subTables = new HashSet<>();
  public final Set<Integer> reentries = new HashSet<>();
  public final Set<Integer> jumpTables = new HashSet<>();
  public final Set<Integer> jumpTableDests = new HashSet<>();
  public final Map<Integer, List<String>> labels = new HashMap<>();
  private int labelCount;

  public Script(final int length) {
    this.entries = new Entry[length];
  }

  public void addLabel(final int destAddress, final String name) {
    this.labels.computeIfAbsent(destAddress, k -> new ArrayList<>()).add(name);
    this.labelCount++;
  }

  public int getLabelCount() {
    return this.labelCount;
  }
}
