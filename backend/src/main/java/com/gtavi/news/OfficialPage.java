package com.gtavi.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** An observation, not an AI assertion. Preserve metadata, links and embedded content before cleanup. */
public record OfficialPage(String url, String title, String description, String imageUrl,
                           String publishedAt, String content, String structured, List<String> links,
                           boolean complete) {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> HOSTS = Set.of("www.rockstargames.com", "rockstargames.com",
        "store.rockstargames.com", "gtavi-thealbum.com", "www.gtavi-thealbum.com");
    public static boolean allowed(String url) {
        try {
            URI u = URI.create(url);
            return "https".equals(u.getScheme()) && u.getUserInfo() == null
                && (u.getPort() == -1 || u.getPort() == 443) && HOSTS.contains(u.getHost());
        } catch (Exception e) { return false; }
    }
    public static String resolve(String base, String value) {
        try {
            if (value == null || value.isBlank()) return null;
            URI u = URI.create(base).resolve(value.strip()).normalize();
            if (!Set.of("http", "https").contains(u.getScheme()) || u.getHost() == null || u.getUserInfo() != null) return null;
            return u.toString();
        } catch (Exception e) { return null; }
    }
    public static String canonical(String url) {
        URI u = URI.create(url);
        String path = u.getPath().replaceAll("/+$", "");
        // Tracking parameters are not identities; article slugs can change.
        if (path.matches(".*/newswire/article/[^/]+/.*")) path = path.replaceFirst("(/newswire/article/[^/]+)/.*", "$1");
        String query="";
        if (!path.contains("/newswire/article/") && u.getRawQuery()!=null) {
            query=Arrays.stream(u.getRawQuery().split("&"))
                .filter(part -> !part.toLowerCase(Locale.ROOT).matches("(utm_[^=]*|fbclid|gclid)=.*"))
                .sorted().collect(java.util.stream.Collectors.joining("&"));
        }
        String host=u.getHost().toLowerCase(Locale.ROOT);
        if ("rockstargames.com".equals(host)) host="www.rockstargames.com";
        return "https://" + host + path + (query.isEmpty() ? "" : "?"+query);
    }
    public static String identity(String url) { return fingerprint(canonical(url)); }
    public static String fingerprint(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 24); }
        catch (Exception e) { throw new IllegalArgumentException(e); }
    }
    public static OfficialPage parse(String url, String html) {
        var doc = Jsoup.parse(html, url);
        String title = first(meta(doc, "og:title"), doc.select("h1").text(), doc.title());
        String description = first(meta(doc, "og:description"), meta(doc, "description"));
        String image = resolve(url, meta(doc, "og:image"));
        String published = first(meta(doc, "article:published_time"), doc.select("time[datetime]").attr("datetime"));
        var links = new LinkedHashSet<String>();
        for (Element link : doc.select("a[href]")) addLink(links, resolve(url, link.attr("href")));
        var structured = new StringBuilder();
        var commerce = JSON.createObjectNode();
        var items = commerce.putArray("purchaseLinks");
        for (Element anchor : doc.select("a[href]")) {
            String label = anchor.text();
            String target = resolve(url,anchor.attr("href"));
            if (target != null && label.toLowerCase(Locale.ROOT).matches("(?s).*(pre.?order|buy|vinyl|soundtrack|compact disc).*")) {
                items.addObject().put("name",label).put("url",target);
            }
        }

        for (Element script : doc.select("script[type=application/ld+json], script#__NEXT_DATA__")) {
            if ("__NEXT_DATA__".equals(script.id())) {
                try { collectSemantic(JSON.readTree(script.data()), structured); }
                catch (Exception ignored) {}
            } else {
                try { structured.append(JSON.readTree(script.data())).append('\n'); }
                catch (Exception ignored) { structured.append(script.data()).append('\n'); }
            }
        }
        // React server payloads contain routes, product links, and text missing from the plain DOM.
        var embedded = new StringBuilder();
        int cursor=0;
        while ((cursor=html.indexOf("self.__next_f.push([1,",cursor))>=0) {
            cursor += "self.__next_f.push([1,".length();
            while (cursor<html.length() && Character.isWhitespace(html.charAt(cursor))) cursor++;
            int end=quotedEnd(html,cursor);
            if(end<0) break;
            try {
                String payload=JSON.readValue(html.substring(cursor,end),String.class);
                for (String record : payload.split("\n")) {
                    int separator = record.indexOf(':');
                    if (separator < 0) continue;
                    String value = record.substring(separator + 1);
                    if (!value.startsWith("[") && !value.startsWith("{")) continue;
                    try { collectPurchaseLinks(JSON.readTree(value), items, url); }
                    catch (Exception ignored) { /* Other RSC record types are handled by the string scan. */ }
                }
                for(int at=0; at<payload.length(); at++) {
                    if(payload.charAt(at)!='"') continue;
                    int stop=quotedEnd(payload,at);
                    if(stop<0) break;
                    String value=JSON.readValue(payload.substring(at,stop),String.class);
                    if(value.startsWith("https://") || value.startsWith("/VI/"))
                        addLink(links,resolve(url,value));
                    else if(value.length()>=12 && value.contains(" ") && !value.startsWith("data:"))
                        embedded.append(value).append('\n');
                    at=stop-1;
                }
            } catch(Exception ignored) {}
            cursor=end;
        }
        structured.append(commerce).append('\n');
        doc.select("script,style,svg,noscript,nav,footer,header").remove();
        Element body = doc.selectFirst("article");
        if (body == null) body = doc.selectFirst("main");
        if (body == null) body = doc.body();
        String text = body == null ? "" : body.wholeText().replaceAll("[ \\t]+", " ").strip();
        String content = title + "\n" + description + "\n" + text + "\n" + embedded;
        return new OfficialPage(url, title, description, image, published, content,
            structured.toString(), List.copyOf(links), text.length() > 120 || embedded.length() > 120);
    }
    public List<String> chunks(int size) {
        // Every character is covered; callers budget how many pages they process, not which page facts survive.
        if (size < 1) throw new IllegalArgumentException("Chunk size must be positive");
        String all = content + "\nStructured data:\n" + structured + "\nLinks:\n" + String.join("\n", links);
        var result = new ArrayList<String>();
        for (int offset = 0; offset < all.length(); offset += Math.max(1, size - Math.min(500, size / 4)))
            result.add(all.substring(offset, Math.min(all.length(), offset + size)));
        return result;
    }
    private static void collectPurchaseLinks(com.fasterxml.jackson.databind.JsonNode node,
            com.fasterxml.jackson.databind.node.ArrayNode links, String base) {
        if (node.isObject() && node.hasNonNull("href")) {
            String label = node.path("children").asText("");
            if (label.isBlank()) label = node.path("analytics").path("text").asText("");
            String target = resolve(base, node.path("href").asText());
            if (target != null && label.toLowerCase(Locale.ROOT).matches("(?s).*(shop now|pre.?order|buy|vinyl|soundtrack|compact disc).*"))
                links.addObject().put("name", label).put("url", target);
        }
        if (node.isContainerNode()) node.forEach(child -> collectPurchaseLinks(child, links, base));
    }
    private static int quotedEnd(String value,int start) {
        if(start>=value.length() || value.charAt(start)!='"') return -1;
        for(int i=start+1;i<value.length();i++) {
            char c=value.charAt(i);
            if(c=='\\') i++;
            else if(c=='"') return i+1;
        }
        return -1;
    }
    private static void collectSemantic(com.fasterxml.jackson.databind.JsonNode node, StringBuilder result) {
        if (node.isArray()) { node.forEach(n -> collectSemantic(n,result)); return; }
        if (!node.isObject()) return;
        node.fields().forEachRemaining(entry -> {
            if (Set.of("name","title","description","price","priceCurrency","availability","url","image","href","content","body").contains(entry.getKey())
                && entry.getValue().isValueNode()) {
                var value=JSON.createObjectNode();
                value.set(entry.getKey(),entry.getValue());
                result.append(value).append('\n');
            } else if (entry.getValue().isContainerNode()) collectSemantic(entry.getValue(),result);
        });
    }
    private static String meta(Element root, String key) {
        for (Element e : root.select("meta"))
            if (key.equals(e.attr("property")) || key.equals(e.attr("name"))) return e.attr("content");
        return "";
    }
    private static String first(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v.strip();
        return "";
    }
    private static void addLink(Set<String> links, String value) { if (value != null && resolve(value,value) != null) links.add(value); }
}
