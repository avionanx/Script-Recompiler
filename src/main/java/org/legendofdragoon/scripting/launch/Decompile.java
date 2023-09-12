package org.legendofdragoon.scripting.launch;

import com.opencsv.exceptions.CsvException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.legendofdragoon.scripting.Disassembler;
import org.legendofdragoon.scripting.ScriptMeta;
import org.legendofdragoon.scripting.Translator;
import org.legendofdragoon.scripting.tokens.Script;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public final class Decompile {
  static {
    System.setProperty("log4j.skipJansi", "false");
    PluginManager.addPackage("org.legendofdragoon");
  }

  private static final Logger LOGGER = LogManager.getFormatterLogger();

  private Decompile() { }

  public static void main(final String[] args) throws IOException, CsvException {
    LOGGER.info("Decompiling file %s", args[0]);

    final Path inputFile = Paths.get(args[0]);
    final byte[] bytes = Files.readAllBytes(inputFile);

    final ScriptMeta meta = new ScriptMeta("https://legendofdragoon.org/scmeta");

    final Disassembler disassembler = new Disassembler(meta);
    final Translator translator = new Translator();

    final Script script = disassembler.disassemble(bytes);
    final String decompiledOutput = translator.translate(script, meta);

    Files.writeString(Paths.get(inputFile.getFileName() + ".txt"), decompiledOutput, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }
}
