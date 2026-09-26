package com.gtavi.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gtavi.domain.ChangeEvent;
import com.gtavi.notification.NotificationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/** Persist a complete replay plan before changing projections or creating deliveries. */
@ApplicationScoped
public class NewsProcessor {
    @Inject NewsRepository repository;
    @Inject NotificationService notifications;
    @Inject AnnouncementIdentityResolver identities;
    @Inject ProductOffers offers;
    @Inject NewsEventPolicy policy;
    @Inject ObjectMapper json;

    public void process(ObjectNode observation) {
        String observationId = observation.path("id").asText();
        replay(observationId);
        if (!observation.path("relevant").asBoolean()) return;
        stage(observation,observationId);
        replay(observationId);
    }

    private void stage(ObjectNode observation,String observationId) {
        ObjectNode article = observation.deepCopy();
        String id = identities.resolve(article);
        article.put("id",id);
        var previous = repository.read("articles:"+id);
        var sources = new LinkedHashSet<String>();
        if(previous!=null) previous.path("sources").forEach(source -> sources.add(source.asText()));
        sources.add(article.path("sourceUrl").asText());
        sources.forEach(article.putArray("sources")::add);
        if(previous!=null && previous.path("sourceUrl").asText().contains("/newswire/article/")
                && !article.path("sourceUrl").asText().contains("/newswire/article/")) {
            for(String field:java.util.List.of("title","description","sourceUrl","imageUrl","publishedAt"))
                if(previous.hasNonNull(field)) article.set(field,previous.get(field));
        }
        if(previous!=null) {
            var facts=new java.util.LinkedHashMap<String,com.fasterxml.jackson.databind.JsonNode>();
            previous.path("facts").forEach(fact->facts.put(fact.path("id").asText(),fact));
            article.path("facts").forEach(fact->facts.put(fact.path("id").asText(),fact));
            var combined=article.putArray("facts");
            facts.values().forEach(combined::add);
            if("MAJOR".equals(previous.path("importance").asText())) article.put("importance","MAJOR");
        }
        article.put("updatedAt",java.time.OffsetDateTime.now().toString());
        var products = new ArrayList<ObjectNode>();
        for(var item:article.path("products")) {
            ObjectNode product = offers.merge((ObjectNode)item);
            product.put("articleId",id).put("updatedAt",article.path("updatedAt").asText());
            products.add(product);
        }
        boolean preorder = article.path("preorderAnnounced").asBoolean()
            || (previous!=null && previous.path("preorderAnnounced").asBoolean());
        for (var product:products) for (var offer:product.path("offers"))
            preorder |= "PREORDER".equals(offer.path("availability").asText());
        article.put("preorderAnnounced",preorder);
        long revision = repository.nextRevision(id);
        var plan = json.createObjectNode();
        plan.set("article",article);
        var plannedProducts = plan.putArray("products");
        products.forEach(plannedProducts::add);
        plan.set("events",json.valueToTree(policy.events(article,previous,products,
            productId -> repository.read("products:"+productId),revision)));
        repository.write("pending:"+observationId,plan);
    }
    public void replay(String observationId) {
        var pending = repository.read("pending:"+observationId);
        if (!(pending instanceof ObjectNode plan)) return;
        // Upgrade pending observations written by the first implementation before applying new plans.
        if (!plan.has("article")) {
            stage(plan,observationId);
            replay(observationId);
            return;
        }
        ObjectNode article = (ObjectNode)plan.path("article");
        repository.upsert("articles",article);
        for(var item:plan.path("products")) repository.upsert("products",(ObjectNode)item);
        for(var item:plan.path("events")) {
            try { notifications.saveEventAndQueue(json.treeToValue(item,ChangeEvent.class)); }
            catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Invalid pending event",e); }
        }
        repository.delete("pending:"+observationId);
    }
}
