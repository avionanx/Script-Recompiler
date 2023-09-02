package org.legendofdragoon.scripting.launch;

import com.opencsv.exceptions.CsvException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.jetbrains.annotations.Nullable;
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
import java.util.Objects;

public final class Compile {
  static {
    System.setProperty("log4j.skipJansi", "false");
    PluginManager.addPackage("org.legendofdragoon");
  }

  private static final Logger LOGGER = LogManager.getFormatterLogger();
  private static final Marker COMPILE_MARKER = MarkerManager.getMarker("COMPILE");


  private Compile() { }

  public static void compile(final String inFile, final @Nullable String outFile, @Nullable ScriptMeta meta) throws IOException, CsvException {
    LOGGER.info(COMPILE_MARKER, "Compiling file %s", inFile);

    if(meta == null) {
      meta = new ScriptMeta("https://legendofdragoon.org/scmeta");
    }

    final Compiler compiler = new Compiler();
    final Lexer lexer = new Lexer(meta);

    final String input = Files.readString(Path.of(inFile));

    final Script lexedDecompiledSource = lexer.lex(input);
    final int[] recompiledSource = compiler.compile(lexedDecompiledSource);

    final Path outPath;
    outPath = Path.of(Objects.requireNonNullElse(outFile, "compiled"));
    try {
      Files.createDirectories(outPath.getParent());
    } catch (IOException e) {
      LOGGER.error(COMPILE_MARKER, "Cannot create directories\n" + e);

      return;
    }
    Files.write(outPath, intsToBytes(recompiledSource), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  public static void main(final String[] args) throws IOException, CsvException {
    compile(args[0], null, null);
  }

  private static byte[] intsToBytes(final int[] ints) {
    final ByteBuffer buffer = ByteBuffer.allocate(ints.length * 0x4).order(ByteOrder.LITTLE_ENDIAN);
    buffer.asIntBuffer().put(ints);
    return buffer.array();
  }
}
