package org.legendofdragoon.scripting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class Launcher {
  static {
    System.setProperty("log4j.skipJansi", "false");
    PluginManager.addPackage("org.legendofdragoon");
  }

  private static final Logger LOGGER = LogManager.getFormatterLogger(Parser.class);

  private Launcher() { }

  public static void main(final String[] args) throws IOException {
    LOGGER.info("Decompilation for file %s", args[0]);

    final byte[] bytes = Files.readAllBytes(Paths.get(args[0]));

/*
    final Scanner scanner = new Scanner(System.in);

    System.out.println("Which entry point would you like to use?");
    for(int i = 0; i < 0x10; i++) {
      System.out.print(String.format("%02d", i) + ": " + Long.toHexString(MathHelper.get(bytes, i * 4, 4)) + "    ");

      if((i + 1) % 4 == 0) {
        System.out.println();
      }
    }

    int entry;
    while(true) {
      try {
        entry = scanner.nextInt();
        break;
      } catch(final InputMismatchException e) {
        System.out.println("Please enter a value from 0 to 15");
      }
    }

    final Parser parser = new Parser(bytes, (int)MathHelper.get(bytes, entry * 4, 4));
*/

    final Parser parser = new Parser(bytes, 0x40);
    parser.parse();
  }
}
