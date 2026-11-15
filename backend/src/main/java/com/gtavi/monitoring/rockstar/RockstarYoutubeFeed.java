package com.gtavi.monitoring.rockstar;

import com.apptasticsoftware.rssreader.RssReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.gtavi.monitoring.core.RockstarMediaData;
import com.gtavi.monitoring.core.RockstarVideoType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Validates the feed identity and binds each extracted video to its own Atom entry. */
public final class RockstarYoutubeFeed {
    public static final String CHANNEL = "UC6VcWc1rAoWdBCM0JxrRQ3A";
    public static final String SOURCE = "ROCKSTAR_YOUTUBE_OFFICIAL";
    public static final String URL = "https://www.youtube.com/feeds/videos.xml?channel_id=" + CHANNEL;
    private static final Pattern GTA_VI = Pattern.compile("(?i)\\b(?:grand\\s+theft\\s+auto|gta)\\s*(?:vi|6)\\b");
    private static final Pattern OTHER_GAME = Pattern.compile(
        "(?i)\\b(?:(?:grand\\s+theft\\s+auto|gta)\\s*(?:v|5|online)\\b|red\\s+dead\\s+redemption\\b)");
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RockstarYoutubeFeed() {}

    public static JsonNode extract(String xml) {
        if (xml == null || xml.isBlank()) throw new IllegalArgumentException("Empty YouTube feed");
        var doc = Jsoup.parse(xml, "", Parser.xmlParser());
        Element feed = doc.selectFirst("feed");
        if (feed == null || !CHANNEL.substring(2).equals(childText(feed, "yt:channelId")))
            throw new IllegalArgumentException("YouTube feed is not the Rockstar channel");
        Element channelLink = feed.children().stream().filter(e -> e.tagName().equals("link")
            && "alternate".equals(e.attr("rel"))).findFirst().orElse(null);
        if (channelLink == null || !("https://www.youtube.com/channel/" + CHANNEL).equals(channelLink.attr("href")))
            throw new IllegalArgumentException("YouTube channel link does not match Rockstar");

        Map<String, Element> evidence = new LinkedHashMap<>();
        for (Element entry : feed.children()) {
            if (!entry.tagName().equals("entry")) continue;
            if (!CHANNEL.equals(childText(entry, "yt:channelId")))
                throw new IllegalArgumentException("YouTube entry has the wrong channel");
            String id = childText(entry, "yt:videoId");
            Element link = entry.children().stream().filter(e -> e.tagName().equals("link")
                && "alternate".equals(e.attr("rel"))).findFirst().orElse(null);
            if (!VIDEO_ID.matcher(id).matches() || link == null || !id.equals(videoId(link.attr("href"))))
                throw new IllegalArgumentException("YouTube entry has invalid video identity");
            evidence.put(id, entry);
        }
        // Camel Atom uses RssReader for Atom parsing. Match its results back to the
        // independently validated entries so extraction cannot manufacture a video.
        var selected = new LinkedHashMap<String, RockstarMediaData.VideoItem>();
        try (var items = new RssReader().read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))) {
            items.forEach(item -> {
                String url = item.getLink().orElse("");
                String id = videoId(url);
                Element entry = evidence.get(id);
                if (entry == null) throw new IllegalArgumentException("Atom item is not backed by an entry");
                String title = item.getTitle().orElse("").strip();
                if (!title.equals(childText(entry, "title")))
                    throw new IllegalArgumentException("Atom item title does not match entry");
                String description = entry.getAllElements().stream()
                    .filter(element -> element.tagName().equals("media:description"))
                    .map(Element::text).findFirst().orElse("");
                boolean titleIsGtaVi = GTA_VI.matcher(title).find();
                boolean otherGameTitle = OTHER_GAME.matcher(title).find();
                if (!titleIsGtaVi && (otherGameTitle || !GTA_VI.matcher(description).find())) return;
                String published = childText(entry, "published");
                OffsetDateTime.parse(published);
                String type = RockstarVideoType.normalize(title, "OTHER_VIDEO");
                String thumb = "https://img.youtube.com/vi/" + id + "/hqdefault.jpg";
                var video = new RockstarMediaData.VideoItem(title, type, published, url, thumb);
                selected.put(id, video);
            });
        }
        return MAPPER.valueToTree(new RockstarMediaData(new java.util.ArrayList<>(selected.values())));
    }

    private static String childText(Element parent, String tag) {
        return parent.children().stream().filter(e -> e.tagName().equals(tag))
            .map(Element::text).findFirst().orElse("").strip();
    }

    private static String videoId(String url) {
        try {
            URI u = URI.create(url);
            if (!"https".equals(u.getScheme()) || u.getUserInfo() != null || u.getPort() != -1
                || !"www.youtube.com".equalsIgnoreCase(u.getHost())) return "";
            String id = "";
            if ("/watch".equals(u.getPath()) && u.getRawQuery() != null) {
                for (String part : u.getRawQuery().split("&"))
                    if (part.startsWith("v=")) { if (!id.isEmpty()) return ""; id = part.substring(2); }
            } else if (u.getPath().startsWith("/shorts/") && u.getRawQuery() == null) {
                id = u.getPath().substring("/shorts/".length());
            }
            return VIDEO_ID.matcher(id).matches() ? id : "";
        } catch (Exception e) { return ""; }
    }
}
