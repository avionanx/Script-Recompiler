package org.legendofdragoon.scripting.meta;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Meta {
  public final ScriptMethod[] methods;
  public final Map<String, String[]> enums;

  public Meta(final ScriptMethod[] methods, final Map<String, String[]> enums) {
    this.methods = methods;
    this.enums = enums;
  }

  private void loadMeta(final List<String[]> descriptionsCsv, final List<String[]> paramsCsv, final List<String[]> enumsCsv, final List<ScriptMethod> methods, final List<String> enumClasses) {
    for(final String[] description : descriptionsCsv) {
      final List<ScriptParam> params = new ArrayList<>();

      for(final String[] param : paramsCsv) {
        if(param[0].equals(description[0])) {
          params.add(new ScriptParam(param[1], param[2], param[3], param[4], param[5]));
        }
      }

      methods.add(new ScriptMethod(description[0], description[1], params.toArray(ScriptParam[]::new)));
    }

    for(final String[] val : enumsCsv) {
      final String className = val[0];
      enumClasses.add(className);
    }
  }

  private List<String[]> loadCsvUrl(final URL url) throws IOException, CsvException {
    final HttpURLConnection con = (HttpURLConnection)url.openConnection();
    con.setRequestMethod("GET");

    if(con.getResponseCode() != 200) {
      throw new RuntimeException("Failed to download meta " + url + ": " + con.getResponseCode() + " - " + con.getResponseMessage());
    }

    final List<String[]> csv = this.loadCsv(con.getInputStream());
    con.disconnect();

    return csv;
  }

  private List<String[]> loadCsv(final InputStream input) throws IOException, CsvException {
    try(final CSVReader reader = new CSVReader(new InputStreamReader(input))) {
      return reader.readAll();
    }
  }

  public static class ScriptMethod {
    public final String name;
    public final String description;
    public final ScriptParam[] params;

    public ScriptMethod(final String name, final String description, final ScriptParam[] params) {
      this.name = name;
      this.description = description;
      this.params = params;
    }

    @Override
    public String toString() {
      return this.name + '(' + Arrays.stream(this.params).map(Object::toString).collect(Collectors.joining(", ")) + ')';
    }
  }

  public static class ScriptParam {
    public final String direction;
    public final String type;
    public final String name;
    public final String description;
    public final String branch;

    public ScriptParam(final String direction, final String type, final String name, final String description, final String branch) {
      this.direction = direction;
      this.type = type;
      this.name = name;
      this.description = description;
      this.branch = branch;
    }

    @Override
    public String toString() {
      return this.name;
    }
  }
}
