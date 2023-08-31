package org.legendofdragoon.scripting.launch;

import com.opencsv.exceptions.CsvException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.legendofdragoon.scripting.Compiler;
import org.legendofdragoon.scripting.Lexer;
import org.legendofdragoon.scripting.ScriptMeta;
import org.legendofdragoon.scripting.tokens.Script;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class Compile {
  static {
    System.setProperty("log4j.skipJansi", "false");
    PluginManager.addPackage("org.legendofdragoon");
  }

  private static final Logger LOGGER = LogManager.getFormatterLogger();

  private Compile() { }

  public static void main(final String[] args) throws IOException, CsvException {
    LOGGER.info("Compiling file %s", args[0]);

    final ScriptMeta meta = new ScriptMeta("https://legendofdragoon.org/scmeta");

    final Compiler compiler = new Compiler();
    final Lexer lexer = new Lexer(meta);

    final String input = Files.readString(Path.of(args[0]));

    final Script lexedDecompiledSource = lexer.lex(input);
    final int[] recompiledSource = compiler.compile(lexedDecompiledSource);

    Files.write(Path.of("compiled"), intsToBytes(recompiledSource), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  private static byte[] intsToBytes(final int[] ints) {
    final ByteBuffer buffer = ByteBuffer.allocate(ints.length * 0x4).order(ByteOrder.LITTLE_ENDIAN);
    buffer.asIntBuffer().put(ints);
    return buffer.array();
  }
}
