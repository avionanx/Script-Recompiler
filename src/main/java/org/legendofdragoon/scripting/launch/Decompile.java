package org.legendofdragoon.scripting.launch;

import com.opencsv.exceptions.CsvException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.jetbrains.annotations.Nullable;
import org.legendofdragoon.scripting.Disassembler;
import org.legendofdragoon.scripting.NotAScriptException;
import org.legendofdragoon.scripting.ScriptMeta;
import org.legendofdragoon.scripting.Translator;
import org.legendofdragoon.scripting.tokens.Script;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public final class Decompile {
  static {
    System.setProperty("log4j.skipJansi", "false");
    PluginManager.addPackage("org.legendofdragoon");
  }

  private static final Logger LOGGER = LogManager.getFormatterLogger();
  private static final Marker DECOMPILE_MARKER = MarkerManager.getMarker("DECOMPILE");
  private static final Marker ERROR_MARKER = MarkerManager.getMarker("ERROR");

  private Decompile() { }

  public static int decompile(final String inFile, final @Nullable String outFile, @Nullable ScriptMeta meta) throws IOException, CsvException {
    LOGGER.info(DECOMPILE_MARKER, "Decompiling file %s", inFile);

    final byte[] bytes = Files.readAllBytes(Paths.get(inFile));

    if(meta == null) {
      LOGGER.info("Downloading meta...");
      meta = new ScriptMeta("https://legendofdragoon.org/scmeta");
    }

    final Disassembler disassembler = new Disassembler(meta);
    final Translator translator = new Translator();

    final Script script;
    final String decompiledOutput;
    try {
      LOGGER.info("Disassembling...");
      script = disassembler.disassemble(bytes);
      decompiledOutput = translator.translate(script, meta);
    } catch (NotAScriptException e) {
      LOGGER.error(DECOMPILE_MARKER, "No entry points detected");

      return 1;
    } catch (IndexOutOfBoundsException e) {
      LOGGER.info(ERROR_MARKER, outFile);
      LOGGER.error(ERROR_MARKER, "Entrypoint scan failed, index out of bounds");

      return 2;
    } catch (RuntimeException e) {
      LOGGER.info(ERROR_MARKER, outFile);
      LOGGER.error(ERROR_MARKER, e.getMessage());

      return 3;
    }

    final Path outPath;
    outPath = Path.of(Objects.requireNonNullElse(outFile, "decompiled.txt"));
    try {
      Files.createDirectories(outPath.getParent());
    } catch (IOException e) {
      LOGGER.error(DECOMPILE_MARKER, "Cannot create directories\n" + e);

      return 1;
    }
    Files.writeString(outPath, decompiledOutput, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

    return 0;
  }

  public static void main(final String[] args) throws IOException, CsvException {
    decompile(args[0], null, null);
  }
}
