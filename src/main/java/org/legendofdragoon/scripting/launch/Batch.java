package org.legendofdragoon.scripting.launch;

import com.opencsv.exceptions.CsvException;
import org.apache.logging.log4j.*;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.legendofdragoon.scripting.Compiler;
import org.legendofdragoon.scripting.Disassembler;
import org.legendofdragoon.scripting.Lexer;
import org.legendofdragoon.scripting.Translator;
import org.legendofdragoon.scripting.meta.Meta;
import org.legendofdragoon.scripting.meta.MetaManager;
import org.legendofdragoon.scripting.meta.NoSuchVersionException;
import org.legendofdragoon.scripting.tokens.Script;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import static org.legendofdragoon.scripting.Shell.intsToBytes;

public class Batch {
  static {
    System.setProperty("log4j.skipJansi", "false");
    System.setProperty("log4j.configurationFile", "log4j2.xml");
    PluginManager.addPackage("org.legendofdragoon");
  }

  private static final Logger LOGGER = LogManager.getFormatterLogger();
  private static final Marker ERROR_MARKER = MarkerManager.getMarker("ERROR");

  private Batch() {
  }

  public static void main(final String[] args) throws IOException, NoSuchVersionException, CsvException, URISyntaxException {
    final Path cacheDir = Path.of("./cache");
    final MetaManager metaManager = new MetaManager(new URI("https://legendofdragoon.org/scmeta/"), cacheDir);
    final Meta meta = metaManager.loadMeta("snapshot");

    final Lexer lexer = new Lexer(meta);
    final Disassembler disassembler = new Disassembler(meta);
    final Translator translator = new Translator();

    final Compiler compiler = new Compiler();

    final Path root = Paths.get(args[0]);
    Files.walk(root).toList().forEach(path -> {
      if (path.toFile().isFile()) {
        final Path relPath = root.relativize(path);
        final String decompPath = Paths.get(args[1]).resolve(relPath) + ".txt";
        try {
          final byte[] bytes = Files.readAllBytes(path);
          if (bytes.length != 0) {
            final Script script = disassembler.disassemble(bytes, new int[]{});
            final String decompiledOutput = translator.translate(script, meta);
            Files.createDirectories(Paths.get(args[1]).resolve(relPath).getParent());
            Files.writeString(Path.of(decompPath), decompiledOutput, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            if (args.length == 3 && !args[2].isEmpty()) {
              try {
                final String input = Files.readString(Path.of(decompPath));
                final Script lexedDecompiledSource = lexer.lex(input);
                final int[] recompiledSource = compiler.compile(lexedDecompiledSource);
                Files.createDirectories(Paths.get(args[2]).resolve(relPath).getParent());
                Files.write(Paths.get(args[2]).resolve(relPath), intsToBytes(recompiledSource), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                //compiler.compile(decompPath, Paths.get(args[2]).resolve(relPath).toString(), meta);
              } catch (RuntimeException r) {
                LOGGER.log(Level.INFO, ERROR_MARKER, decompPath);
                LOGGER.log(Level.ERROR, ERROR_MARKER, r.getMessage());
              }
            }
        }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }
}
