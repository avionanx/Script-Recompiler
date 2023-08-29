package org.legendofdragoon.scripting;

import com.opencsv.exceptions.CsvException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.legendofdragoon.scripting.tokens.Script;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class Launcher {
  static {
    System.setProperty("log4j.skipJansi", "false");
    PluginManager.addPackage("org.legendofdragoon");
  }

  private static final Logger LOGGER = LogManager.getFormatterLogger();

  private Launcher() { }

  public static void main(final String[] args) throws IOException, CsvException {
    LOGGER.info("Decompilation for file %s", args[0]);

    final byte[] bytes = Files.readAllBytes(Paths.get(args[0]));

    final ScriptMeta meta = new ScriptMeta("https://legendofdragoon.org/scmeta");

    final Disassembler parser = new Disassembler(bytes, meta);
    final Script script = parser.disassemble();

    final String output = new Translator().translate(script, meta);
    LOGGER.info(output);
  }
}
