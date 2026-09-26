package com.gtavi.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gtavi.domain.ChangeEvent;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** One grouped announcement; later commercial transitions use independently selectable preferences. */
@ApplicationScoped
public class NewsEventPolicy {
    @ConfigProperty(name="gtavi.news.alert-lookback-days",defaultValue="14") int lookbackDays;
    public List<ChangeEvent> events(ObjectNode article, JsonNode previous, List<ObjectNode> products,
                                    java.util.function.Function<String,JsonNode> oldProduct, long revision) {
        var events = new ArrayList<ChangeEvent>();
        String category = article.path("category").asText("NEWS");
        if (previous == null) {
            String type = switch (category) {
                case "COLLECTIBLE" -> "COLLECTIBLE_ANNOUNCED";
                case "MUSIC" -> "MUSIC_ANNOUNCED";
                default -> "MAJOR".equals(article.path("importance").asText()) ? "MAJOR_OFFICIAL_NEWS" : "OFFICIAL_NEWS";
            };
            events.add(event(article,type,"ANNOUNCED",article.path("title").asText(),article.path("description").asText(),recent(article)));
            return events;
        }
        var knownFacts=new java.util.HashSet<String>();
        previous.path("facts").forEach(fact->knownFacts.add(fact.path("id").asText()));
        boolean newMajorFact=false;
        for(var fact:article.path("facts")) if("MAJOR".equals(fact.path("importance").asText())
                && !knownFacts.contains(fact.path("id").asText())) newMajorFact=true;
        boolean promoted="MAJOR".equals(article.path("importance").asText()) && !"MAJOR".equals(previous.path("importance").asText());
        if((newMajorFact && !article.path("observationHash").asText().equals(previous.path("observationHash").asText())) || promoted) events.add(event(article,"MAJOR_OFFICIAL_NEWS","FACTS:"+revision,
            article.path("title").asText()+" — new details",article.path("description").asText(),true));
        boolean preorder = false;
        for (ObjectNode product : products) {
            JsonNode old = oldProduct.apply(product.path("id").asText());
            for (JsonNode offer : product.path("offers")) {
                JsonNode prior = findOffer(old,offer.path("id").asText());
                String before = prior == null ? "ANNOUNCED" : prior.path("availability").asText("ANNOUNCED");
                String after = offer.path("availability").asText("ANNOUNCED");
                if ("PREORDER".equals(after) && "ANNOUNCED".equals(before) && !previous.path("preorderAnnounced").asBoolean()) preorder = true;
                String occurrence = offer.path("id").asText() + ":" + revision;
                String name = product.path("name").asText();
                if ("OUT_OF_STOCK".equals(before) && ("AVAILABLE".equals(after) || "PREORDER".equals(after)))
                    events.add(event(article,"BACK_IN_STOCK",occurrence,name+" is back in stock","An official offer is available again.",true));
                else if (!"OUT_OF_STOCK".equals(before) && "OUT_OF_STOCK".equals(after) && prior != null)
                    events.add(event(article,"OUT_OF_STOCK",occurrence,name+" is out of stock","The official offer is currently out of stock.",true));
                if (prior != null && prior.hasNonNull("price") && offer.hasNonNull("price")
                        && prior.path("price").decimalValue().compareTo(offer.path("price").decimalValue()) != 0)
                    events.add(event(article,"PRICE_CHANGED",occurrence,name+" price updated",
                        prior.path("price").asText()+" → "+offer.path("price").asText()+" "+offer.path("currency").asText(),true));
            }
        }
        if (preorder) events.add(event(article,"COLLECTIBLE".equals(category) ? "COLLECTIBLE_PREORDER_OPENED" : "MUSIC_PREORDER_OPENED",
            "PREORDER:"+revision,article.path("title").asText()+" — pre-orders open","An official pre-order link is now available.",true));
        return events;
    }
    private JsonNode findOffer(JsonNode product,String id) {
        if(product!=null) for(JsonNode offer:product.path("offers")) if(id.equals(offer.path("id").asText())) return offer;
        return null;
    }
    private boolean recent(JsonNode article) {
        try { return OffsetDateTime.parse(article.path("publishedAt").asText()).isAfter(OffsetDateTime.now().minusDays(lookbackDays)); }
        catch(Exception ignored) { return true; }
    }
    private ChangeEvent event(ObjectNode article,String type,String occurrence,String title,String description,boolean notify) {
        var event = new ChangeEvent();
        event.setGameCode("GTA_VI"); event.setSourceCode("ROCKSTAR_NEWS");
        event.setEventType(type); event.setPriority(article.path("importance").asText("NEWS"));
        event.setTitle(title); event.setDescription(description); event.setEvidenceUrl(article.path("sourceUrl").asText());
        event.setNewValue(article.path("id").asText()); event.setDeduplicationKey("NEWS:"+article.path("id").asText()+":"+type+":"+occurrence);
        event.setDetectedAt(OffsetDateTime.now()); event.setCreatedAt(event.getDetectedAt());
        event.setUserVisible(true); event.setNotificationEligible(notify);
        return event;
    }
}
