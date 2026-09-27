package com.gtavi.news;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductCatalogTest {
    private final ObjectMapper json = new ObjectMapper();
    @Test void productionAlbumObservationsBecomeOneCardWithDistinctFormats() throws Exception {
        var raw = json.readTree(getClass().getResourceAsStream("/fixtures/incident-products-2026-09-26.json"));
        var rows = new ArrayList<JsonNode>();
        raw.path("items").forEach(rows::add);
        var products = ProductCatalog.group(rows);
        var albums = products.stream().filter(p -> "ALBUM".equals(p.path("category").asText())).toList();
        assertEquals(1, albums.size());
        assertEquals(3, albums.getFirst().path("offers").size());
        assertFalse(albums.getFirst().has("limited"));
        assertTrue(albums.getFirst().path("offers").toString().contains("limitededitionvinyl"));
        assertEquals(1, products.stream().filter(p -> "COLLECTIBLE".equals(p.path("category").asText())).count());
        assertEquals(products, ProductCatalog.group(rows), "Grouping must be stable across refreshes");
    }
    @Test void separateSkuVariantsAndCurrenciesAreNotCollapsed() throws Exception {
        ObjectNode first = (ObjectNode)json.readTree("""
            {"id":"a","name":"GTA VI Collector Box","category":"COLLECTIBLE","purchaseUrl":"https://store.rockstargames.com/products/box",
             "offers":[{"purchaseUrl":"https://store.rockstargames.com/products/box","variant":"red","price":40,"currency":"EUR"}]}
            """);
        ObjectNode second = first.deepCopy();
        second.put("id","b");
        ((ObjectNode)second.path("offers").get(0)).put("variant","blue").put("currency","CHF");
        assertEquals(2, ProductCatalog.group(List.of(first, second)).size());
    }
    @Test void shortLegacyFormatsAndNestedPurchaseLinksBelongToOneAlbum() throws Exception {
        var rows = new ArrayList<JsonNode>();
        rows.add(json.readTree("""
            {"id":"parent","name":"Grand Theft Auto VI: The Album","category":"ALBUM",
             "sourceUrl":"https://www.rockstargames.com/VI/music","articleId":"album-news"}
            """));
        for (String format : List.of("CD", "Vinyl", "Limited Edition Vinyl")) {
            ObjectNode item = json.createObjectNode().put("id", format).put("name", format)
                .put("category", format.equals("CD") ? "CD" : "VINYL")
                .put("sourceUrl", "https://www.rockstargames.com/VI/music").put("articleId", "album-news");
            item.putArray("offers").addObject().put("purchaseUrl", "https://music.example/" + format.replace(" ", "-"))
                .put("price", 30).put("currency", "EUR");
            if (format.startsWith("Limited")) item.put("limited", true);
            rows.add(item);
        }
        var products = ProductCatalog.group(rows);
        assertEquals(1, products.size());
        assertEquals("Grand Theft Auto VI: The Album", products.getFirst().path("name").asText());
        assertEquals(3, products.getFirst().path("offers").size());
        assertTrue(products.getFirst().path("offers").toString().contains("Limited Edition Vinyl"));
        assertTrue(products.getFirst().path("offers").toString().contains("EUR"));
        assertFalse(products.getFirst().has("limited"));
        Collections.reverse(rows);
        assertEquals(products.getFirst().path("id"), ProductCatalog.group(rows).getFirst().path("id"));
    }
    @Test void nestedAlbumLinksIdentifyFormatsButUnrelatedMusicStaysSeparate() throws Exception {
        var album = json.readTree("""
            {"id":"a","name":"CD","category":"CD","offers":[{"purchaseUrl":"https://gtavithealbum.lnk.to/cd"}]}
            """);
        var other = json.readTree("""
            {"id":"b","name":"Another Artist Album Vinyl","category":"VINYL","sourceUrl":"https://www.rockstargames.com/VI/music","purchaseUrl":"https://music.example/other"}
            """);
        var unknown = json.readTree("""
            {"id":"c","name":"Vinyl","category":"VINYL","sourceUrl":"https://music.example/unrelated","purchaseUrl":"https://music.example/unknown"}
            """);
        var products = ProductCatalog.group(List.of(album, other, unknown));
        assertEquals(3, products.size());
        assertEquals(1, products.stream().filter(p -> p.path("category").asText().equals("ALBUM")).count());
    }
    @Test void currentProductionShortLabelsDoNotRepeatTheAlbumCard() throws Exception {
        var raw = json.readTree(getClass().getResourceAsStream("/fixtures/incident-music-cards-2026-09-26.json"));
        var rows = new ArrayList<JsonNode>();
        raw.path("items").forEach(rows::add);
        var music = ProductCatalog.group(rows).stream()
            .filter(p -> Set.of("ALBUM", "CD", "VINYL", "MUSIC").contains(p.path("category").asText())).toList();
        assertEquals(1, music.size());
        assertEquals("Grand Theft Auto VI: The Album", music.getFirst().path("name").asText());
        assertEquals(4, music.getFirst().path("offers").size());
        assertTrue(music.getFirst().path("imageUrl").asText().contains("50105de3da220dff6dd47fad6865b1912e7466ca.jpg"));
    }

    @Test void collectionContentsAndUnpricedDuplicatesLeaveOnePricedBox() throws Exception {
        var rows = new ArrayList<JsonNode>();
        rows.add(json.readTree("""
            {"id":"box","name":"Grand Theft Auto VI: The Goodtime State – Vice City Collection","category":"COLLECTIBLE",
             "articleId":"collection",
             "sourceUrl":"https://store.rockstargames.com/merchandise/gtavi-goodtime-state-vice-city-collection",
             "purchaseUrl":"https://store.rockstargames.com/merchandise/gtavi-goodtime-state-vice-city-collection",
             "price":399.99,"currency":"EUR","limited":true,"updatedAt":"2026-09-27T15:08:03Z",
             "imageUrl":"https://images.example/box.png",
             "description":"A premium Collector’s Box featuring all the essentials for a good time.",
             "offers":[
               {"purchaseUrl":"https://store.rockstargames.com/merchandise/gtavi-goodtime-state-vice-city-collection",
                "variant":"box","availability":"PREORDER","verifiedAt":"2026-09-27T15:02:03Z"},
               {"purchaseUrl":"https://store.rockstargames.com/merchandise/gtavi-goodtime-state-vice-city-collection",
                "variant":"box","availability":"PREORDER","price":399.99,"currency":"EUR","verifiedAt":"2026-09-27T15:08:03Z"}]}
            """));
        rows.add(json.readTree("""
            {"id":"article","name":"Grand Theft Auto VI: The Goodtime State – Vice City Collection","category":"COLLECTIBLE",
             "articleId":"collection","purchaseUrl":"https://store.rockstargames.com",
             "sourceUrl":"https://www.rockstargames.com/newswire/article/box","updatedAt":"2026-09-27T09:01:09Z",
             "description":"Introducing the collection. The Goodtime State – Vice City Collection includes: a Macca the Gator figure, sunglasses, a snapback hat, a magnetic mirror, a swizzle spoon, a keychain, a crossbody bag, a shot glass, an enamel pin set, stickers and a double-sided poster.",
             "offers":[{"purchaseUrl":"https://store.rockstargames.com","variant":"box",
                        "availability":"PREORDER","verifiedAt":"2026-09-27T09:01:09Z"}]}
            """));
        for (String entry : List.of(
                "{\"id\":\"gear\",\"name\":\"GOODTIME GEAR\",\"category\":\"MERCHANDISE\",\"offers\":[]}",
                "{\"id\":\"figure\",\"name\":\"Macca the Gator Figure\",\"category\":\"COLLECTIBLE\",\"offers\":[]}",
                "{\"id\":\"alias\",\"name\":\"Vice City Collection\",\"category\":\"COLLECTIBLE\",\"offers\":[]}"))
            rows.add(((ObjectNode) json.readTree(entry)).put("articleId", "collection")
                .put("sourceUrl", "https://www.rockstargames.com/VI/vice-city-collection"));

        var products = ProductCatalog.group(rows);

        assertEquals(1, products.size(), "The box is the product; its contents are not separate cards");
        var box = products.getFirst();
        assertEquals("Grand Theft Auto VI: The Goodtime State – Vice City Collection", box.path("name").asText());
        assertEquals(1, box.path("offers").size(), "The priced offer replaces its unpriced duplicate");
        assertEquals(399.99, box.path("offers").get(0).path("price").asDouble());
        assertEquals("EUR", box.path("offers").get(0).path("currency").asText());
        assertTrue(box.path("description").asText().contains("includes:"),
            "The kept card carries the describing contents list");
        assertEquals(products, ProductCatalog.group(rows), "Grouping must be stable across refreshes");
    }

    @Test void linklessAnnouncementWithoutALinkedBoxStaysVisible() throws Exception {
        var rows = List.<JsonNode>of(
            json.readTree("{\"id\":\"a\",\"name\":\"GTA VI collectible box\",\"category\":\"COLLECTIBLE\",\"articleId\":\"announced\",\"offers\":[]}"),
            json.readTree("{\"id\":\"b\",\"name\":\"Macca the Gator Figure\",\"category\":\"COLLECTIBLE\",\"articleId\":\"announced\",\"offers\":[]}"));
        assertEquals(2, ProductCatalog.group(rows).size());
    }
}
