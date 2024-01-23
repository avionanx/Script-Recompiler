package org.legendofdragoon.scripting.meta;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetaManager {
  private final URI baseUri;
  private final Path cacheDir;
  private String[] versions;

  public MetaManager(final URI baseUri, final Path cacheDir) {
    this.baseUri = baseUri;
    this.cacheDir = cacheDir;
  }

  public String[] getVersions() throws IOException, CsvException {
    if(this.versions == null) {
      this.versions = this.requestCsv(this.baseUri.resolve("versions.php").toURL()).getFirst();
    }

    return this.versions;
  }

  public Meta loadMeta(final String version) throws IOException, CsvException, NoSuchVersionException {
    // Snapshot always loads from the server
    if("snapshot".equals(version)) {
      return this.loadMeta(this.baseUri.resolve(version + '/'), null);
    }

    // Load cache
    final Path versionDir = this.cacheDir.resolve(version);
    if(Files.exists(versionDir)) {
      return this.loadMeta(versionDir);
    }

    // Pull from server
    final List<String> versions = Arrays.asList(this.getVersions());

    if(!versions.contains(version)) {
      throw new NoSuchVersionException("Invalid version: " + version);
    }

    return this.loadMeta(this.baseUri.resolve(version + '/'), this.cacheDir.resolve(version));
  }

  private Meta loadMeta(final Path basePath) throws IOException, CsvException {
    final List<String[]> descriptionsCsv = this.loadCsvFile(basePath.resolve("descriptions.csv"));
    final List<String[]> paramsCsv = this.loadCsvFile(basePath.resolve("params.csv"));
    final List<String[]> enumsCsv = this.loadCsvFile(basePath.resolve("enums.csv"));

    final List<Meta.ScriptMethod> methods = new ArrayList<>();
    final List<String> enumClasses = new ArrayList<>();
    this.loadMeta(descriptionsCsv, paramsCsv, enumsCsv, methods, enumClasses);
    final Meta.ScriptMethod[] methodsArr = methods.toArray(Meta.ScriptMethod[]::new);
    final Map<String, String[]> enums = new HashMap<>();

    for(final String className : enumClasses) {
      final String[] values = this.loadCsvFile(basePath.resolve(className + ".csv")).stream().map(v -> v[0]).toArray(String[]::new);
      enums.put(className, values);
    }

    return new Meta(methodsArr, enums);
  }

  private Meta loadMeta(final URI uri, final Path cache) throws IOException, CsvException {
    final List<String[]> descriptionsCsv = this.requestCsv(uri.resolve("descriptions.csv").toURL(), this.child(cache, "descriptions.csv"));
    final List<String[]> paramsCsv = this.requestCsv(uri.resolve("params.csv").toURL(), this.child(cache, "params.csv"));
    final List<String[]> enumsCsv = this.requestCsv(uri.resolve("enums.csv").toURL(), this.child(cache, "enums.csv"));

    final List<Meta.ScriptMethod> methods = new ArrayList<>();
    final List<String> enumClasses = new ArrayList<>();
    this.loadMeta(descriptionsCsv, paramsCsv, enumsCsv, methods, enumClasses);
    final Meta.ScriptMethod[] methodsArr = methods.toArray(Meta.ScriptMethod[]::new);
    final Map<String, String[]> enums = new HashMap<>();

    for(final String className : enumClasses) {
      final String[] values = this.requestCsv(uri.resolve(className + ".csv").toURL(), this.child(cache, className + ".csv")).stream().map(v -> v[0]).toArray(String[]::new);
      enums.put(className, values);
    }

    return new Meta(methodsArr, enums);
  }

  private void loadMeta(final List<String[]> descriptionsCsv, final List<String[]> paramsCsv, final List<String[]> enumsCsv, final List<Meta.ScriptMethod> methods, final List<String> enumClasses) {
    for(final String[] description : descriptionsCsv) {
      final List<Meta.ScriptParam> params = new ArrayList<>();

      for(final String[] param : paramsCsv) {
        if(param[0].equals(description[0])) {
          params.add(new Meta.ScriptParam(param[1], param[2], param[3], param[4], param[5]));
        }
      }

      methods.add(new Meta.ScriptMethod(description[0], description[1], params.toArray(Meta.ScriptParam[]::new)));
    }

    for(final String[] val : enumsCsv) {
      final String className = val[0];
      enumClasses.add(className);
    }
  }

  private Path child(final Path path, final String child) {
    if(path == null) {
      return null;
    }

    return path.resolve(child);
  }

  private List<String[]> requestCsv(final URL url) throws IOException, CsvException {
    return this.requestCsv(url, null);
  }

  private List<String[]> requestCsv(final URL url, final Path cache) throws IOException, CsvException {
    final HttpURLConnection con = (HttpURLConnection)url.openConnection();
    con.setRequestMethod("GET");

    if(con.getResponseCode() != 200) {
      throw new RuntimeException("Failed to download meta " + url + ": " + con.getResponseCode() + " - " + con.getResponseMessage());
    }

    final InputStream stream = con.getInputStream();
    final byte[] data = stream.readAllBytes();
    if(cache != null) {
      Files.createDirectories(cache.getParent());
      Files.write(cache, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    final List<String[]> csv = this.loadCsv(new ByteArrayInputStream(data));
    con.disconnect();

    return csv;
  }

  private List<String[]> loadCsvFile(final Path file) throws IOException, CsvException {
    return this.loadCsv(Files.newInputStream(file));
  }

  private List<String[]> loadCsv(final InputStream input) throws IOException, CsvException {
    try(final CSVReader reader = new CSVReader(new InputStreamReader(input))) {
      return reader.readAll();
    }
  }
}
