package org.legendofdragoon.scripting;

import com.opencsv.exceptions.CsvException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.legendofdragoon.scripting.tokens.Script;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

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

//    final ScriptMeta meta = new ScriptMeta("https://legendofdragoon.org/scmeta");
    final ScriptMeta meta = new ScriptMeta(Path.of("."));

    final Disassembler disassembler = new Disassembler(meta);
    final Compiler compiler = new Compiler();
    final Lexer lexer = new Lexer(meta);
    final Translator translator = new Translator();

    final Script script = disassembler.disassemble(bytes);

    final int[] original = bytesToInts(bytes);
    final int[] directRecompile = compiler.compile(script);

    if(original.length != directRecompile.length) {
      System.err.println("Length mismatch");
      return;
    }

    for(int i = 0; i < directRecompile.length; i++) {
      if(original[i] != directRecompile[i]) {
        System.err.println("Mismatch at " + i);
      }
    }

    final String decompiledOutput = translator.translate(script, meta);

    final Script lexedDecompiledSource = lexer.lex(decompiledOutput);
    final int[] recompiledSource = compiler.compile(lexedDecompiledSource);

    Files.write(Path.of("recompiled"), intsToBytes(recompiledSource), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

    final Script redecompiledSource =  disassembler.disassemble(intsToBytes(recompiledSource));

    final String recompiledOutput = translator.translate(redecompiledSource, meta);

    Files.writeString(Path.of("a"), decompiledOutput, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    Files.writeString(Path.of("b"), recompiledOutput, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  private static int[] bytesToInts(final byte[] bytes) {
    final ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    final int[] ints = new int[bytes.length / 4];
    buffer.asIntBuffer().get(ints);
    return ints;
  }

  private static byte[] intsToBytes(final int[] ints) {
    final ByteBuffer buffer = ByteBuffer.allocate(ints.length * 0x4).order(ByteOrder.LITTLE_ENDIAN);
    buffer.asIntBuffer().put(ints);
    return buffer.array();
  }
}
