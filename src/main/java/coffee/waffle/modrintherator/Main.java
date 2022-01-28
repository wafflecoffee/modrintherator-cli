package coffee.waffle.modrintherator;

import com.google.gson.Gson;
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

public class Main {
  public static void main(String[] args) throws IOException, InterruptedException {
    final String token = System.getenv("MODRINTH_TOKEN");
    final String ghToken = System.getenv("GITHUB_OAUTH");

    final List<String> arguments = Arrays.stream(args).toList();
    if (arguments.isEmpty() || !token.startsWith("gho_")) {
      Logger.error("Token or project ID are null");
      throw new RuntimeException("Token or project ID are null");
    }

    final boolean staging = arguments.contains("staging");
    final String projectId = arguments.get(0).replaceAll(
            "(http(s)?://)?(staging)?(-)?(api)?(\\.)?modrinth\\.com/(api/)?(v1/|v2/)?(mod|modpack)?/",
            "");
    final String apiUrl = staging ?
            "https://staging-api.modrinth.com/v2/project/" :
            "https://api.modrinth.com/api/v1/mod/";

    HttpResponse<String> response = request(token, apiUrl + projectId);
    Project project = parse(response);
    findIssues(project, ghToken);
  }

  private static HttpResponse<String> request(String token, String apiUrl) throws IOException, InterruptedException {
    final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    final HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Authorization", token)
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

  private static void findIssues(Project project, String ghToken) throws IOException {
    final GitHub gh = ghToken == null ?
            new GitHubBuilder().build() :
            new GitHubBuilder().withOAuthToken(ghToken).build();

    if (project.status.equals("approved")) {
      Logger.info("Project is approved - no need to run this.");
    }
    if (isNothing(project.body) || project.body.length() < 100) {
      Logger.info("Issue found! A1: Description is too short");
    }
    if (isNothing(project.source_url) && project.license.name.contains("Public License")) {
      Logger.info("Issue found! B2: Project has copyleft license, but doesn't provide sources");
    }
    if (!isNothing(project.source_url) && project.source_url.contains("github")) {
      final String trimmedSourceUrl = project.source_url.replaceAll("(http(s)?://)?github\\.com/", "");
      final String ghLicense = gh.getRepository(trimmedSourceUrl).getLicense().getName()
              .replaceAll("\".*\"( or \".*\")? License", "");

      if (ghLicense.matches(project.license.name + "(.0)?")) {
        Logger.info("Issue found! B3: License does not match source license");
      }
      if (ghLicense.matches("GNU (Affero )?General Public License v3\\.0")) {
        Logger.info("Issue found! Uses GPLv3 or AGPLv3");
      }
    }
    if (project.license.name.contains("Custom")) {
      Logger.info("Potential issue found! B4: License may be crayon");
    }
    if (!project.slug.matches("[\\-a-zA-Z0-9_]")) { // FIXME this isn't matching dashes
      Logger.info("Issue found! C2: Slug is not alphanumeric");
    }
    if (!isNothing(project.source_url) && !project.source_url.contains("http")) {
      Logger.info("Issue found! C2: Broken source link");
    }
    if (!isNothing(project.issues_url) && !project.issues_url.contains("http")) {
      Logger.info("Issue found! C2: Broken issues link");
    }
    if (!isNothing(project.wiki_url) && !project.wiki_url.contains("http")) {
      Logger.info("Issue found! C2: Broken wiki link");
    }
    if (!isNothing(project.discord_url) &&
        !project.discord_url.contains("http") &&
        !project.discord_url.contains("discord")) {
      Logger.info("Issue found! C2: Broken Discord link");
    }
    if (project.versions.isEmpty() || project.versions.get(0).equals("")) {
      Logger.info("Issue found! D1: No versions");
    }
  }

  private static boolean isNothing(String s) {
    return s == null || s.isEmpty();
  }
}
