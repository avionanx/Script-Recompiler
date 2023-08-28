package org.legendofdragoon.scripting;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ScriptMeta {
  public final ScriptMethod[] methods;

  public ScriptMeta(final String baseUrl) throws IOException, CsvException {
    final List<String[]> descriptionsCsv = loadCsvUrl(new URL(baseUrl + "/descriptions.csv"));
    final List<String[]> paramsCsv = loadCsvUrl(new URL(baseUrl + "/params.csv"));

    final List<ScriptMethod> methods = new ArrayList<>();
    for(final String[] description : descriptionsCsv) {
      final List<ScriptParam> params = new ArrayList<>();

      for(final String[] param : paramsCsv) {
        if(param[0].equals(description[0])) {
          params.add(new ScriptParam(param[1], param[2], param[3], param[4], param[5]));
        }
      }

      methods.add(new ScriptMethod(description[0], description[1], params.toArray(ScriptParam[]::new)));
    }

    this.methods = methods.toArray(ScriptMethod[]::new);
  }

  private List<String[]> loadCsvUrl(final URL url) throws IOException, CsvException {
    HttpURLConnection con = (HttpURLConnection)url.openConnection();
    con.setRequestMethod("GET");

    if(con.getResponseCode() != 200) {
      throw new RuntimeException("Failed to download meta: " + con.getResponseCode() + " - " + con.getResponseMessage());
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
