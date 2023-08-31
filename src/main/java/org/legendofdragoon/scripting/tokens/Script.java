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

  /** Uses an existing label if one already points to this address */
  public String addLabel(final int destAddress, final String name) {
    if(this.labels.containsKey(destAddress)) {
      return this.labels.get(destAddress).get(0);
    }

    this.labels.computeIfAbsent(destAddress, k -> new ArrayList<>()).add(name);
    this.labelCount++;
    return name;
  }

  /** Forces adding a label even if another label already points to this address */
  public String addUniqueLabel(final int destAddress, final String name) {
    this.labels.computeIfAbsent(destAddress, k -> new ArrayList<>()).add(name);
    this.labelCount++;
    return name;
  }

  public int getLabelCount() {
    return this.labelCount;
  }
}
