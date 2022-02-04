package coffee.waffle.modrintherator;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
final class Project {
  String slug;
  String title;
  @Nullable Object moderator_message;
  License license;
  @Nullable String source_url;

  static class License {
    String id;
    String name;
  }
}
