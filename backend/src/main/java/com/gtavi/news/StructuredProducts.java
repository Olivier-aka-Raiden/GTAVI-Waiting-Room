package com.gtavi.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Locale;

/** Deterministic commerce extraction, independent of model availability. */
@ApplicationScoped
public class StructuredProducts {
    public void extract(JsonNode node, OfficialPage page, ArrayNode products) {
        extract(node, page, products, false);
    }
    private void extract(JsonNode node, OfficialPage page, ArrayNode products, boolean isVariant) {
        if(node.isArray()){node.forEach(child -> extract(child,page,products,isVariant));return;}
        if(!node.isObject()) return;
        musicLinks(node,page,products);
        for(String field:List.of("@graph","mainEntity","hasVariant")) if(node.has(field)) extract(node.get(field),page,products,isVariant || "hasVariant".equals(field));
        if(!"Product".equals(node.path("@type").asText())) return;
        String name=node.path("name").asText();
        String description=node.path("description").asText(page.description());
        if(!NewsAnalyzer.related(name+" "+description+" "+page.title()) || name.matches("(?is).*GTA\\s*V\\b.*")) return;
        ObjectNode product=products.objectNode();
        String purchase=OfficialPage.resolve(page.url(),node.path("url").asText(page.url()));
        String variant = node.path("sku").asText(node.path("productID").asText(""));
        product.put("id", !isVariant || variant.isBlank() ? OfficialPage.identity(purchase) : OfficialPage.fingerprint(purchase + "|" + variant))
            .put("name",name).put("description",description);
        product.put("purchaseUrl",purchase).put("sourceUrl",page.url());
        String type=NewsAnalyzer.category(name+" "+description);
        product.put("category","MUSIC".equals(type) ? musicType(name) : "COLLECTIBLE".equals(type) ? type : "MERCHANDISE");
        JsonNode image=node.path("image");
        product.put("imageUrl",OfficialPage.resolve(page.url(),image.isArray()?image.path(0).asText():image.asText(page.imageUrl())));
        var offers=product.putArray("offers");
        JsonNode raw=node.path("offers");
        if(raw.isArray()) raw.forEach(offer -> offers.add(offer(offer,page,purchase)));
        else if(raw.isObject()) offers.add(offer(raw,page,purchase));
        if (!variant.isBlank()) offers.forEach(value -> ((ObjectNode)value).put("variant", variant));
        // Compatibility scalar fields describe the first observed offer, never an inferred market.
        if(!offers.isEmpty()) {
            JsonNode first=offers.get(0);
            for(String field:List.of("price","currency","availability")) if(first.hasNonNull(field)) product.set(field,first.get(field));
        }
        String text=(description+" "+page.content()).toLowerCase(Locale.ROOT);
        if(text.contains("game sold separately")) product.put("gameIncluded",false);
        if(text.contains("limited edition") || text.contains("limited-edition")) product.put("limited",true);
        products.add(product);
    }
    private ObjectNode offer(JsonNode raw,OfficialPage page,String purchase) {
        ObjectNode result=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        result.put("purchaseUrl",OfficialPage.resolve(page.url(),raw.path("url").asText(purchase)));
        String currency=raw.path("priceCurrency").asText("");
        if(currency.matches("[A-Z]{3}")) {
            result.put("currency",currency);
            try {
                var price=new java.math.BigDecimal(raw.path("price").asText());
                if(price.signum()>0) result.put("price",price);
            } catch(Exception ignored) {}
        }
        JsonNode region=raw.path("eligibleRegion");
        String market=region.isTextual()?region.asText():region.path("name").asText("");
        if(!market.isBlank()) result.put("market",market);
        if(raw.hasNonNull("sku")) result.put("variant",raw.path("sku").asText());
        String availability=raw.path("availability").asText();
        result.put("availability",availability.endsWith("PreOrder")?"PREORDER":availability.endsWith("InStock")?"AVAILABLE":
            availability.endsWith("OutOfStock") || availability.endsWith("SoldOut")?"OUT_OF_STOCK":"ANNOUNCED");
        return result;
    }
    private void musicLinks(JsonNode node,OfficialPage page,ArrayNode products) {
        if(!node.has("purchaseLinks") || !NewsAnalyzer.related(page.title()+" "+page.description())
                || !"MUSIC".equals(NewsAnalyzer.category(page.title()+" "+page.description()))) return;
        for(JsonNode link:node.path("purchaseLinks")) {
            String purchase=OfficialPage.resolve(page.url(),link.path("url").asText());
            if(purchase==null) continue;
            String label=link.path("name").asText();
            var product=products.objectNode().put("id",OfficialPage.identity(purchase))
                .put("name",page.title().replaceFirst(" - Rockstar Games$","")+" — "+label)
                .put("description",page.description()).put("imageUrl",page.imageUrl())
                .put("purchaseUrl",purchase).put("sourceUrl",page.url()).put("category",musicType(label))
                .put("availability",label.toLowerCase(Locale.ROOT).contains("pre-order")?"PREORDER":"ANNOUNCED");
            if(label.toLowerCase(Locale.ROOT).contains("limited")) product.put("limited",true);
            products.add(product);
        }
    }
    private String musicType(String name) {
        String lower=name.toLowerCase(Locale.ROOT);
        if(lower.contains("vinyl") && !lower.contains("cd")) return "VINYL";
        if(lower.contains("cd") && !lower.contains("vinyl")) return "CD";
        return "ALBUM";
    }
}
