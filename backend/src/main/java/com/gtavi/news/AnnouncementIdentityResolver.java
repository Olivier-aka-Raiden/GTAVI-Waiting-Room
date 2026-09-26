package com.gtavi.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.LinkedHashSet;
import java.util.Set;

/** Merge only explicit shared product/topic links, never fuzzy title similarity. */
@ApplicationScoped
public class AnnouncementIdentityResolver {
    @Inject NewsRepository repository;
    public String resolve(ObjectNode article) {
        Set<String> claims = new LinkedHashSet<>();
        claims.add(article.path("sourceUrl").asText());
        String category = article.path("category").asText();
        JsonNode sourceAlias = repository.read("identity:" + OfficialPage.identity(article.path("sourceUrl").asText()));
        if (sourceAlias!=null) {
            JsonNode known = repository.read("articles:"+sourceAlias.path("id").asText());
            if (known!=null && "NEWS".equals(category)) {
                category=known.path("category").asText(category);
                article.put("category",category);
            }
        }
        if (Set.of("MUSIC", "COLLECTIBLE").contains(category)) {
            for (JsonNode product : article.path("products")) addProductLink(claims, product.path("purchaseUrl").asText(), category);
            for (JsonNode link : article.path(article.has("relationshipLinks") ? "relationshipLinks" : "links")) addProductLink(claims, link.asText(), category);
        }
        String selected = article.path("id").asText();
        for (String claim : claims) {
            JsonNode alias = repository.read("identity:" + OfficialPage.identity(claim));
            if (alias != null) { selected = alias.path("id").asText(selected); break; }
        }
        ObjectNode alias = article.objectNode().put("id", selected);
        for (String claim : claims) repository.write("identity:" + OfficialPage.identity(claim), alias);
        return selected;
    }
    private void addProductLink(Set<String> claims, String link, String category) {
        if (OfficialPage.resolve(link, link) == null) return;
        var uri = java.net.URI.create(link);
        String path = uri.getPath().toLowerCase(java.util.Locale.ROOT);
        if (path.startsWith("/merchandise/") || path.startsWith("/products/")
                || ("COLLECTIBLE".equals(category) && path.matches("/vi/[^/]+") && !path.equals("/vi/editions") && !path.equals("/vi/music"))
                || ("MUSIC".equals(category) && (path.equals("/vi/music")
                || uri.getHost().equals("gtavithealbum.lnk.to")))) claims.add(link);
    }
}
