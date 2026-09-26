package com.gtavi.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;

/** Retain observed offers separately by variant, purchase URL, currency, and explicit market. */
@ApplicationScoped
public class ProductOffers {
    @Inject NewsRepository repository;
    public ObjectNode merge(ObjectNode product) {
        JsonNode previous = repository.read("products:" + product.path("id").asText());
        var offers = new LinkedHashMap<String, JsonNode>();
        if (previous != null) previous.path("offers").forEach(offer -> offers.put(offer.path("id").asText(), offer));
        if (product.path("offers").isArray()) product.path("offers").forEach(offer -> add(offers, (ObjectNode) offer, product));
        else add(offers, product, product);
        var merged = previous instanceof ObjectNode old ? old.deepCopy() : product.objectNode();
        product.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isNull() && !entry.getValue().asText().equals("")) merged.set(entry.getKey(), entry.getValue());
        });
        // Containers do not have a text representation; copy them explicitly.
        product.fields().forEachRemaining(entry -> {
            if (entry.getValue().isContainerNode()) merged.set(entry.getKey(),entry.getValue());
        });
        var array = merged.putArray("offers");
        offers.values().forEach(array::add);
        return merged;
    }
    private void add(LinkedHashMap<String, JsonNode> offers, ObjectNode source, ObjectNode product) {
        String url = source.path("purchaseUrl").asText(product.path("purchaseUrl").asText());
        if (OfficialPage.resolve(url,url) == null) return;
        String currency = source.path("currency").asText("");
        String market = source.path("market").asText("");
        String variant = source.path("variant").asText(product.path("variant").asText(""));
        String id = OfficialPage.fingerprint(OfficialPage.canonical(url) + "|" + variant + "|" + currency + "|" + market);
        ObjectNode offer = offers.get(id) instanceof ObjectNode old ? old.deepCopy() : product.objectNode();
        offer.put("id",id).put("purchaseUrl",url).put("currency",currency).put("market",market).put("variant",variant);
        if (source.hasNonNull("price") && source.path("price").asDouble() > 0 && currency.matches("[A-Z]{3}"))
            offer.set("price",source.get("price"));
        String availability = source.path("availability").asText("ANNOUNCED");
        if (!"ANNOUNCED".equals(availability) || !offer.has("availability")) offer.put("availability",availability);
        offer.put("verifiedAt",java.time.OffsetDateTime.now().toString());
        offers.put(id,offer);
    }
}
