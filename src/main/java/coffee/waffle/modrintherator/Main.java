package coffee.waffle.modrintherator;

import com.google.gson.Gson;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHLicense;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.tinylog.Logger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class Main {
  private static final String MODRINTH_TOKEN = System.getenv("MODRINTH_TOKEN");
  private static final String GITHUB_TOKEN = System.getenv("GITHUB_OAUTH");
  private static final String API_BASE = "https://api.modrinth.com/v2/project/";

  public static void main(String[] args) throws IOException, InterruptedException {
    final List<String> arguments = Arrays.stream(args).toList();
    final GitHub gh = new GitHubBuilder().withOAuthToken(GITHUB_TOKEN).build();

    for (String id : arguments) {
      HttpResponse<String> response = request(API_BASE + id);
      Project project = new Gson().fromJson(response.body(), Project.class);

      if (project == null ||
        project.slug == null ||
        project.source_url == null ||
        !project.source_url.contains("github") ||
        project.moderator_message != null ||
        project.source_url.startsWith("/") ||
        project.source_url.endsWith("/") ||
        !project.source_url.matches(".*/.*")) {
        continue;
      }
      Logger.info(project.source_url);
      findIssues(project, gh);
    }
  }

  private static HttpResponse<String> request(String apiUrl) throws IOException, InterruptedException {
    final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    final HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Authorization", MODRINTH_TOKEN)
            .header("Accept", "application/json")
            .GET()
            .build();

    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static void findIssues(Project project, GitHub gh) throws IOException {
    final String trimmedSourceUrl = project.source_url.replaceAll("(http(s)?://)?github\\.com/", "");
    GHLicense ghLicense;
    try {
      ghLicense = gh.getRepository(trimmedSourceUrl).getLicense();
    } catch (GHFileNotFoundException e) { return; }
    if (ghLicense == null) {
      return;
    }
    final String ghLicenseName = ghLicense.getName().replaceAll("\".*\"( or \".*\")? License", "");
    final String ghLicenseKey = ghLicense.getKey().toUpperCase(Locale.ROOT);

    if (ghLicenseName.matches("GNU (Affero )?General Public License v3\\.0")) {
      Logger.info(project.title + " uses the " + ghLicenseKey);
      BufferedWriter writer = new BufferedWriter(new FileWriter("gpl.sh", true));
      writer.append("mpatch '{\"moderator_message\":\"\\[Automatic\\] GPL usage\"," +
        "\"moderator_message_body\":\"\\[Automated message - if this is an error, feel free to dismiss.\\] " +
        "GPL invalid yadda yadda\"}' " + API_BASE).append(project.slug).append("\n");
      writer.close();
      return;
    }
    if (!ghLicenseName.matches(project.license.name + "(.0)?")) {
      Logger.info(project.title + "'s " + project.license.id.toUpperCase(Locale.ROOT) +
        " does not match GitHub license " + ghLicenseKey);
      BufferedWriter writer = new BufferedWriter(new FileWriter("incorrect.sh", true));
      writer.append("mpatch '{\"moderator_message\":\"\\[Automatic\\] Incorrect license listed\"," +
        "\"moderator_message_body\":\"\\[Automated message - if this is an error, feel free to dismiss.\\] " +
        "This mod has the license of ").append(project.license.id.toUpperCase(Locale.ROOT))
        .append(", but its GitHub repository has the ").append(ghLicenseKey)
        .append(" license. Please make sure this lines up across the board.\"}' ").append(API_BASE)
        .append(project.slug).append("\n");
      writer.close();
    }
  }

}
