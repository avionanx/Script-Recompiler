package org.legendofdragoon.scripting;

import com.github.difflib.patch.PatchFailedException;
import com.opencsv.exceptions.CsvException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.legendofdragoon.scripting.meta.Meta;
import org.legendofdragoon.scripting.meta.MetaManager;
import org.legendofdragoon.scripting.meta.NoSuchVersionException;
import org.legendofdragoon.scripting.tokens.Script;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public final class Shell {
  private Shell() { }

  static {
    System.setProperty("log4j.skipJansi", "false");
    PluginManager.addPackage("org.legendofdragoon");
  }

  private static final Logger LOGGER = LogManager.getFormatterLogger();

  public static void main(final String[] args) throws IOException, URISyntaxException, CsvException, NoSuchVersionException, PatchFailedException {
    if(args.length == 0) {
      LOGGER.info("Commands: [v]ersions, [d]ecompile, [c]ompile, [g]enpatch, [a]pplypatch");
      System.exit(1);
      return;
    }

    final Path cacheDir = Path.of("./cache");
    final MetaManager metaManager = new MetaManager(new URI("https://legendofdragoon.org/scmeta/"), cacheDir);

    if("v".equals(args[0]) || "versions".equals(args[0])) {
      LOGGER.info("Fetching...");
      final String[] versions = metaManager.getVersions();

      LOGGER.info("Versions:");
      for(final String version : versions) {
        LOGGER.info(version);
      }

      System.exit(0);
      return;
    }

    if("g".equals(args[0]) || "genpatch".equals(args[0])) {
      generateDiff(args);
      System.exit(0);
      return;
    }

    if("a".equals(args[0]) || "applypatch".equals(args[0])) {
      applyDiff(args);
      System.exit(0);
      return;
    }

    final Options options = new Options();
    options.addOption("v", "version", true, "The meta version to use");
    options.addRequiredOption("i", "in", true, "The input file");
    options.addRequiredOption("o", "out", true, "The output file");

    final CommandLine cmd;
    final CommandLineParser parser = new DefaultParser();
    final HelpFormatter helper = new HelpFormatter();

    try {
      cmd = parser.parse(options, args);
    } catch(final ParseException e) {
      LOGGER.error(e.getMessage());
      helper.printHelp("Usage:", options);
      System.exit(1);
      return;
    }

    final String version = cmd.getOptionValue("version", "snapshot");

    LOGGER.info("Loading meta %s...", version);
    final Meta meta = metaManager.loadMeta(version);

    final Path inputFile = Paths.get(cmd.getOptionValue("in")).toAbsolutePath();
    final Path outputFile = Paths.get(cmd.getOptionValue("out")).toAbsolutePath();

    if(!Files.exists(inputFile)) {
      LOGGER.error("Error: input file does not exist");
      System.exit(1);
      return;
    }

    switch(args[0]) {
      case "d", "decompile" -> {
        LOGGER.info("Decompiling %s...", inputFile);

        final Disassembler disassembler = new Disassembler(meta);
        final Translator translator = new Translator();

        final byte[] bytes = Files.readAllBytes(inputFile);
        final Script script = disassembler.disassemble(bytes);
        final String decompiledOutput = translator.translate(script, meta);

        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, decompiledOutput, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      }

      case "c", "compile" -> {
        LOGGER.info("Compiling... %s", inputFile);

        final Compiler compiler = new Compiler();
        final Lexer lexer = new Lexer(meta);

        final String input = Files.readString(inputFile);
        final Script lexedDecompiledSource = lexer.lex(input);
        final int[] recompiledSource = compiler.compile(lexedDecompiledSource);

        Files.createDirectories(outputFile.getParent());
        Files.write(outputFile, intsToBytes(recompiledSource), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      }

      default -> {
        System.out.println("Commands: [v]ersions, [d]ecompile, [c]ompile");
        System.exit(1);
      }
    }
  }

  private static void generateDiff(final String[] args) throws IOException {
    final Options options = new Options();
    options.addRequiredOption("a", "original", true, "The original file");
    options.addRequiredOption("b", "modified", true, "The modified file");
    options.addRequiredOption("o", "out", true, "The output file");

    final CommandLine cmd;
    final CommandLineParser parser = new DefaultParser();
    final HelpFormatter helper = new HelpFormatter();

    try {
      cmd = parser.parse(options, args);
    } catch(final ParseException e) {
      LOGGER.error(e.getMessage());
      helper.printHelp("Usage:", options);
      System.exit(1);
      return;
    }

    final Path originalFile = Paths.get(cmd.getOptionValue("original")).toAbsolutePath();
    final Path modifiedFile = Paths.get(cmd.getOptionValue("modified")).toAbsolutePath();
    final Path outputFile = Paths.get(cmd.getOptionValue("out")).toAbsolutePath();

    if(!Files.exists(originalFile) || !Files.exists(modifiedFile)) {
      LOGGER.error("Error: one or both input files do not exist");
      System.exit(1);
      return;
    }

    LOGGER.info("Generating diff...");
    LOGGER.info("Original: %s", originalFile);
    LOGGER.info("Modified: %s", modifiedFile);
    LOGGER.info("Output: %s", outputFile);

    final String output = Patcher.generatePatch(originalFile, modifiedFile);
    Files.createDirectories(outputFile.getParent());
    Files.writeString(outputFile, output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  private static void applyDiff(final String[] args) throws IOException, PatchFailedException {
    final Options options = new Options();
    options.addRequiredOption("a", "original", true, "The original file");
    options.addRequiredOption("b", "patch", true, "The patch file");
    options.addRequiredOption("o", "out", true, "The output file");

    final CommandLine cmd;
    final CommandLineParser parser = new DefaultParser();
    final HelpFormatter helper = new HelpFormatter();

    try {
      cmd = parser.parse(options, args);
    } catch(final ParseException e) {
      LOGGER.error(e.getMessage());
      helper.printHelp("Usage:", options);
      System.exit(1);
      return;
    }

    final Path originalFile = Paths.get(cmd.getOptionValue("original")).toAbsolutePath();
    final Path patchFile = Paths.get(cmd.getOptionValue("patch")).toAbsolutePath();
    final Path outputFile = Paths.get(cmd.getOptionValue("out")).toAbsolutePath();

    if(!Files.exists(originalFile) || !Files.exists(patchFile)) {
      LOGGER.error("Error: one or both input files do not exist");
      System.exit(1);
      return;
    }

    LOGGER.info("Applying diff...");
    LOGGER.info("Original: %s", originalFile);
    LOGGER.info("Patch: %s", patchFile);
    LOGGER.info("Output: %s", outputFile);

    final String output = Patcher.applyPatch(originalFile, patchFile);
    Files.createDirectories(outputFile.getParent());
    Files.writeString(outputFile, output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  private static byte[] intsToBytes(final int[] ints) {
    final ByteBuffer buffer = ByteBuffer.allocate(ints.length * 0x4).order(ByteOrder.LITTLE_ENDIAN);
    buffer.asIntBuffer().put(ints);
    return buffer.array();
  }
}
