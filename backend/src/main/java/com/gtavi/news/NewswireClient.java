package com.gtavi.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jsoup.Jsoup;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Locale;

/** Adapter for the public queries used by Rockstar's own Newswire application. */
@ApplicationScoped
public class NewswireClient {
    private static final String ENDPOINT = "https://graph.rockstargames.com/?origin=https://www.rockstargames.com";
    private static final String LIST = """
        query NewswireList($locale:String!,$page:Int!,$limit:Int,$tagId:Int) {
          posts(page:$page,locale:$locale,limit:$limit,tagId:$tagId) {
            paging { page pageCount nextPage count }
            results { id:id_hash url title created primary_tags { id name } }
          }
        }
        """;
    private static final String ARTICLE = """
        query NewswirePost($id_hash:String!,$locale:String!) {
          post(id_hash:$id_hash,locale:$locale) {
            id:id_hash title subtitle content created
            posts_hero { type hero } posts_jsx { markup variables_us_defaulted }
            tina { payload }
          }
        }
        """;
    @Inject ObjectMapper json;

    public JsonNode list(int page, Integer tag) throws IOException {
        var variables = json.createObjectNode().put("locale", "en_us").put("page", page).put("limit", 20);
        if (tag != null) variables.put("tagId", tag);
        JsonNode posts = query(LIST, variables).path("posts");
        if (!posts.path("results").isArray() || !posts.path("paging").has("nextPage"))
            throw new IOException("Newswire listing schema changed");
        return posts;
    }

    public OfficialPage hydrate(OfficialPage shell) throws IOException {
        String path = java.net.URI.create(shell.url()).getPath();
        String[] parts = path.split("/");
        if (parts.length < 4 || !"article".equals(parts[2])) return shell;
        var variables = json.createObjectNode().put("locale", "en_us").put("id_hash", parts[3]);
        JsonNode post = query(ARTICLE, variables).path("post");
        if (!post.isObject() || !post.hasNonNull("title")) throw new IOException("Newswire article missing");
        return fromPost(shell, post);
    }

    static OfficialPage fromPost(OfficialPage shell, JsonNode post) {
        var document = Jsoup.parse("<main></main>", shell.url());
        var main = document.selectFirst("main");
        // Article relationships come from its body, not global navigation.
        var links = new LinkedHashSet<String>();
        collect(post.path("content"), main, links, shell.url());
        collect(post.path("posts_jsx"), main, links, shell.url());
        collect(post.path("tina").path("payload").path("content"), main, links, shell.url());
        String description = post.path("tina").path("payload").path("meta").path("blurb").asText(shell.description());
        String content = post.path("title").asText(shell.title()) + "\n" + post.path("subtitle").asText() + "\n" + description + "\n" + main.text();
        String published = publicationDate(post.path("created").asText());
        if (published.isBlank()) published = publicationDate(shell.publishedAt());
        return new OfficialPage(shell.url(), post.path("title").asText(shell.title()), description,
            shell.imageUrl(), published,
            content, shell.structured() + "\n" + post.toString(), java.util.List.copyOf(links),
            main.text().length() > 120);
    }

    private static void collect(JsonNode node, org.jsoup.nodes.Element main, LinkedHashSet<String> links, String base) {
        if (node.isContainerNode()) { node.forEach(child -> collect(child, main, links, base)); return; }
        if (!node.isTextual()) return;
        String value = node.asText();
        if (value.startsWith("https://") || value.startsWith("/")) {
            String link = OfficialPage.resolve(base, value);
            if (link != null) links.add(link);
        } else if (value.contains("<")) {
            var fragment = Jsoup.parseBodyFragment(value, base);
            fragment.select("a[href]").forEach(a -> {
                String link = OfficialPage.resolve(base, a.attr("href"));
                if (link != null) links.add(link);
            });
            main.appendText(fragment.text() + "\n");
        } else if (value.contains(" ") || value.length() > 30) main.appendText(value + "\n");
    }

    static String publicationDate(String value) {
        if (value == null || value.isBlank()) return "";
        value = value.replace('\u00a0', ' ').replace('\u202f', ' ').strip();
        try { return OffsetDateTime.parse(value).toString(); } catch (Exception ignored) {}
        try { return LocalDate.parse(value).toString(); } catch (Exception ignored) {}
        try {
            // The public API supplies a localized date without an offset. Preserve its calendar date.
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("M/d/yy, h:mm a", Locale.US))
                .toLocalDate().toString();
        } catch (Exception ignored) { return ""; }
    }

    private JsonNode query(String query, JsonNode variables) throws IOException {
        var request = json.createObjectNode().put("query", query);
        request.set("variables", variables);
        var response = Jsoup.connect(ENDPOINT).timeout(15000).maxBodySize(4 * 1024 * 1024 + 1)
            .ignoreContentType(true).followRedirects(false).header("Content-Type", "application/json")
            .requestBody(request.toString()).method(org.jsoup.Connection.Method.POST).execute();
        if (response.statusCode() != 200 || response.bodyAsBytes().length > 4 * 1024 * 1024)
            throw new IOException("Newswire API response unavailable or oversized");
        JsonNode result = json.readTree(response.body());
        return responseData(result);
    }

    static JsonNode responseData(JsonNode result) throws IOException {
        JsonNode errors = result.path("errors");
        boolean failed = !errors.isMissingNode() && !errors.isNull() && !(errors.isArray() && errors.isEmpty());
        if (failed || !result.path("data").isObject()) throw new IOException("Newswire API query failed");
        return result.path("data");
    }
}
