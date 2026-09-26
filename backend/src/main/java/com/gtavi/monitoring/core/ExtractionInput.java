package com.gtavi.monitoring.core;

import com.gtavi.news.OfficialPage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/** Keep source facts and their links, not layout markup or responsive image variants. */
final class ExtractionInput {
    private ExtractionInput() {}

    static String prepare(String source, String sourceType) {
        if ("youtube_rss".equals(sourceType)) {
            return Jsoup.parse(source, "", org.jsoup.parser.Parser.xmlParser()).outerHtml();
        }
        Document original = Jsoup.parse(source);
        Document clean = cleanedDocument(source);
        Set<String> evidence = new LinkedHashSet<>();
        add(evidence, clean.wholeText());
        // Labels stay next to their URLs; relative URLs remain source evidence, not invented links.
        clean.select("a[href], video[src], iframe[src], img[alt][src], time[datetime], meta[content]")
            .forEach(element -> {
                String attribute = element.hasAttr("href") ? "href" : element.hasAttr("src") ? "src"
                    : element.hasAttr("datetime") ? "datetime" : "content";
                String label = element.hasAttr("alt") ? element.attr("alt") : element.text();
                if (element.tagName().equals("meta")) label = element.attr("property") + element.attr("name");
                add(evidence, label + ": " + element.attr(attribute));
            });
        original.select("script[type=application/ld+json]").forEach(script -> add(evidence, script.data()));
        if (source.contains("self.__next_f.push([1,")) {
            // The same parser used by news preserves embedded text that is absent from a SPA shell.
            OfficialPage page = OfficialPage.parse("https://extraction.invalid/",
                original.select("script").outerHtml());
            for (String text : page.content().split("\\R")) add(evidence, text);
            // Relative retailer links must not acquire an invented Rockstar hostname.
            add(evidence, page.structured().replace("https://extraction.invalid", ""));
        }
        return String.join("\n", evidence);
    }

    static String cleanedHtml(String html) {
        return cleanedDocument(html).outerHtml().replaceAll("[ \\t]{2,}", " ").strip();
    }

    private static Document cleanedDocument(String html) {
        Document document = Jsoup.parse(html);
        document.outputSettings().prettyPrint(false);
        document.select("script,style,noscript,svg,path,source,link").remove();
        // Preserve navigation CTA text: a preorder button can be the only published availability signal.
        document.select("img").forEach(image -> {
            if (image.attr("alt").isBlank() || image.attr("src").startsWith("data:")) image.remove();
        });
        document.select("nav,footer,header").forEach(element -> element.unwrap());
        document.getAllElements().forEach(element -> {
            for (var attribute : new ArrayList<>(element.attributes().asList())) {
                if (!Set.of("href","src","alt","datetime","content","name","property").contains(attribute.getKey()))
                    element.removeAttr(attribute.getKey());
            }
        });
        removeComments(document);
        return document;
    }

    private static void removeComments(Node node) {
        for (Node child : new ArrayList<>(node.childNodes())) {
            if (child instanceof Comment) child.remove();
            else removeComments(child);
        }
    }

    private static void add(Set<String> evidence, String value) {
        String normalized = value.replaceAll("\\s+", " ").strip();
        if (!normalized.isBlank() && evidence.stream().noneMatch(existing -> existing.contains(normalized)))
            evidence.add(normalized);
    }
}
