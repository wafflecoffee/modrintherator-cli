package coffee.waffle.modrintherator;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
final class Project {
  String slug;
  @Nullable String body;
  String status;
  License license;
  @Nullable String source_url;
  @Nullable String discord_url;

  static class License {
    String id;
    String name;
  }
}
