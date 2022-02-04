package coffee.waffle.modrintherator;

import com.google.gson.Gson;
import org.kohsuke.github.GHLicense;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.tinylog.Logger;

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

  public static void main(String[] args) throws IOException, InterruptedException {
    final List<String> arguments = Arrays.stream(args).toList();
    if (arguments.isEmpty() || !MODRINTH_TOKEN.startsWith("gho_")) {
      Logger.error("Token or project ID are invalid");
      throw new RuntimeException("Token or project ID are invalid");
    }

    final String projectId = arguments.get(0).replaceAll(
            "(http(s)?://)?(staging)?(-)?(api)?(\\.)?modrinth\\.com/(api/)?(v1/|v2/)?(mod|modpack)?/",
            "");
    final String apiUrl = arguments.contains("staging") ?
            "https://staging-api.modrinth.com/v2/project/" :
            "https://api.modrinth.com/v2/project/";

    HttpResponse<String> response = request(apiUrl + projectId);
    Project project = parse(response);
    findIssues(project);
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

  private static Project parse(HttpResponse<String> response) {
    final int code = response.statusCode();
    if (code != 200) {
      throw new RuntimeException("Response code was not okay! Expected 200 but got " + code);
    }

    return new Gson().fromJson(response.body(), Project.class);
  }

  private static void findIssues(Project project) throws IOException {
    if (project.status.equals("approved")) {
      Logger.info("Project is approved - no need to run this.");
    }
    if (isNothing(project.body) || project.body.length() < 100) {
      Logger.info("Issue found! A1: Description is too short");
    }
    if (isNothing(project.source_url) && project.license.name.contains("Public License")) {
      Logger.info("Issue found! B2: Project uses the " + project.license.id.toUpperCase(Locale.ROOT) + ", but doesn't provide sources");
    }
    if (!isNothing(project.source_url) && project.source_url.contains("github")) {
      final GitHub gh = GITHUB_TOKEN == null ?
              new GitHubBuilder().build() :
              new GitHubBuilder().withOAuthToken(GITHUB_TOKEN).build();
      final String trimmedSourceUrl = project.source_url.replaceAll("(http(s)?://)?github\\.com/", "");
      final GHLicense ghLicense = gh.getRepository(trimmedSourceUrl).getLicense();
      final String ghLicenseName = ghLicense.getName().replaceAll("\".*\"( or \".*\")? License", "");
      final String ghLicenseKey = ghLicense.getKey().toUpperCase(Locale.ROOT);

      if (!ghLicenseName.matches(project.license.name + "(.0)?")) {
        Logger.info("Issue found! B3: License " + project.license.id + " does not match source license " + ghLicenseKey);
      }
      if (ghLicenseName.matches("GNU (Affero )?General Public License v3\\.0")) {
        Logger.info("Issue found! Project uses the " + ghLicenseKey);
      }
    }
    if (project.license.name.contains("Custom")) {
      Logger.info("Potential issue found! B4: License may be crayon");
    }
    if (!project.slug.matches("[-a-zA-Z0-9_]{3,20}")) {
      Logger.info("Issue found! C2: Slug " + project.slug + " is not alphanumeric");
    }
    if (!isNothing(project.source_url) && !project.source_url.contains("http")) {
      Logger.info("Issue found! C2: Broken source link: " + project.source_url);
    }
    if (!isNothing(project.issues_url) && !project.issues_url.contains("http")) {
      Logger.info("Issue found! C2: Broken issues link: " + project.issues_url);
    }
    if (!isNothing(project.wiki_url) && !project.wiki_url.contains("http")) {
      Logger.info("Issue found! C2: Broken wiki link: " + project.wiki_url);
    }
    if (!isNothing(project.discord_url) &&
        !project.discord_url.contains("http") &&
        !project.discord_url.contains("discord")) {
      Logger.info("Issue found! C2: Broken Discord link: " + project.discord_url);
    }
  }

  private static boolean isNothing(String s) {
    return s == null || s.isEmpty();
  }
}
