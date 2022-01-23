package coffee.waffle.modrintherator;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;

class Project {
  String slug;
  @Nullable String body;
  String status;
  License license;
  ArrayList<String> versions;
  @Nullable String issues_url;
  @Nullable String source_url;
  @Nullable String wiki_url;
  @Nullable String discord_url;

  static class License {
    String name;
  }
}
