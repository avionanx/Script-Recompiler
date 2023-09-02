package org.legendofdragoon.scripting.launch;

import com.opencsv.exceptions.CsvException;
import org.apache.logging.log4j.*;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.legendofdragoon.scripting.ScriptMeta;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.legendofdragoon.scripting.launch.Compile.compile;
import static org.legendofdragoon.scripting.launch.Decompile.decompile;

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

  public static void main(final String[] args) throws IOException, CsvException {
    final Path root = Paths.get(args[0]);
    final ScriptMeta meta = new ScriptMeta("https://legendofdragoon.org/scmeta");
    Files.walk(root).toList().parallelStream().forEach(path -> {
      if (path.toFile().isFile()) {
        final Path relPath = root.relativize(path);
        final String decompPath = Paths.get(args[1]).resolve(relPath) + ".txt";
        try {
          final int ret = decompile(path.toString(), decompPath, meta);
          if (ret == 0 && args.length == 3 && !args[2].equals("")) {
            try {
              compile(decompPath, Paths.get(args[2]).resolve(relPath).toString(), meta);
            } catch (RuntimeException r) {
              LOGGER.log(Level.INFO, ERROR_MARKER, decompPath);
              LOGGER.log(Level.ERROR, ERROR_MARKER, r.getMessage());
            }
          }
        } catch (IOException | CsvException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }
}
