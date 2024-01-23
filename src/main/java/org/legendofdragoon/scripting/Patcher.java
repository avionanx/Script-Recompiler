package org.legendofdragoon.scripting;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Patcher {
  public static String generatePatch(final Path originalFile, final Path modifiedFile) throws IOException {
    final List<String> originalLines = Files.readAllLines(originalFile);
    final List<String> modifiedLines = Files.readAllLines(modifiedFile);

    final Patch<String> patch = DiffUtils.diff(originalLines, modifiedLines);
    final List<String> diff = UnifiedDiffUtils.generateUnifiedDiff("original", "modified", originalLines, patch, 0);
    final StringBuilder output = new StringBuilder();

    for(final String line : diff) {
      output.append(line).append('\n');
    }

    return output.toString();
  }

  public static String applyPatch(final Path originalFile, final Path patchFile) throws IOException, PatchFailedException {
    final List<String> originalLines = Files.readAllLines(originalFile);
    final List<String> patchLines = Files.readAllLines(patchFile);

    final Patch<String> patch = UnifiedDiffUtils.parseUnifiedDiff(patchLines);
    final List<String> patched = DiffUtils.patch(originalLines, patch);
    final StringBuilder output = new StringBuilder();

    for(final String line : patched) {
      output.append(line).append('\n');
    }

    return output.toString();
  }
}
