package coffee.waffle.modrintherator;

import com.google.gson.Gson;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.tinylog.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class Main {
  public static void main(String[] args) throws ParseException, IOException, InterruptedException {
    final Options options = new Options();
    final CommandLineParser parser = new DefaultParser();

    options.addOption("t", "token", true, "Modrinth token to use");
    options.addOption("g", "ghToken", true, "GitHub OAuth token to use");
    options.addOption("p", "projectId", true, "ID of the project to check");
    options.addOption("s", "staging", false, "Whether to use staging API");

    final CommandLine cmd = parser.parse(options, args);
    final String token = cmd.hasOption("t") ?
            cmd.getOptionValue("t") :
            System.getenv("MODRINTH_TOKEN");
    @Nullable final String ghToken = cmd.hasOption("g") ?
            cmd.getOptionValue("g") :
            System.getenv("GITHUB_OAUTH");
    final String projectId = cmd.getOptionValue("p");
    final String apiUrl = cmd.hasOption("s") ?
            "https://staging-api.modrinth.com/v2/project/" :
            "https://api.modrinth.com/api/v1/mod/";

    if (token == null || projectId == null) {
      Logger.error("Token or project ID are null");
      throw new RuntimeException("Token or project ID are null");
    }

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


  @SuppressWarnings("ConstantConditions")
  private static void findIssues(Project project, String ghToken) throws IOException {
    final GitHub gh = ghToken == null ?
            new GitHubBuilder().build() :
            new GitHubBuilder().withOAuthToken(ghToken).build();

    if (project.status.equals("approved")) {
      Logger.info("Project is approved - no need to run this.");
      return;
    }
    if (StringUtils.isEmpty(project.body) || project.body.length() < 100) {
      Logger.info("Issue found! A1: Description is too short");
    }
    if (StringUtils.contains(project.source_url, "github")) {
      tryGHLicenseCheck(project, gh);
    }
    if (!StringUtils.isAlphanumeric(project.slug) && !project.slug.contains("-")) {
      Logger.info("Issue found! C2: Slug is not alphanumeric");
    }
    try {
      if (!project.source_url.contains("http") ||
          !project.issues_url.contains("http") ||
          !project.wiki_url.contains("http") ||
          !project.discord_url.contains("http") ||
          !project.discord_url.contains("discord")) {
        Logger.info("Issue found! C2: Broken links");
      }
    } catch (NullPointerException ignored) {}
    if (project.versions.isEmpty() || project.versions.get(0).equals("")) {
      Logger.info("Issue found! D1: No versions");
    }
  }

  private static void tryGHLicenseCheck(Project project, GitHub gh) throws IOException {
    try {
      final String trimmedSourceUrl = project.source_url.replaceAll("(http(s)?://)?github\\.com/", "");
      final String ghLicense = gh.getRepository(trimmedSourceUrl).getLicense().getName()
              .replaceAll("\".*\"( or \".*\")? License", "");

      if (!(ghLicense.equals(project.license.name) ||
          ghLicense.equals(project.license.name + ".0"))) { // hack to account for LGPLv3 naming
        Logger.info("Issue found! B3: License does not match source license");
      }
      if (ghLicense.equals("GNU General Public License v3.0") ||
          ghLicense.equals("GNU Affero General Public License v3.0")) {
        Logger.info("Issue found! Uses GPLv3 or AGPLv3");
      }
    } catch (NullPointerException ignored) {}
  }
}
