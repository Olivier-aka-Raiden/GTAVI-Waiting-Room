package com.gtavi.news;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jsoup.Jsoup;
import java.io.IOException;

/** Only follow explicitly trusted official hosts, including every redirect hop. */
@ApplicationScoped
public class OfficialPageFetcher {
    @ConfigProperty(name="gtavi.monitoring.user-agent", defaultValue="GTA-VI-Waiting-Room/1.0") String userAgent;
    public OfficialPage fetch(String url) throws IOException {
        for (int hop=0; hop<5; hop++) {
            if (!OfficialPage.allowed(url)) throw new IOException("Untrusted official source URL");
            if (!OfficialUrlPolicy.crawlable(url)) throw new NonPageResourceException("URL is not an eligible content page");
            var response=Jsoup.connect(url).userAgent(userAgent).timeout(15000)
                .maxBodySize(4*1024*1024+1).followRedirects(false).ignoreHttpErrors(true).ignoreContentType(true)
                .header("Accept-Language","en-US,en;q=0.9").execute();
            if (response.statusCode()>=300 && response.statusCode()<400) {
                url=OfficialPage.resolve(url,response.header("Location"));
                continue;
            }
            if (response.statusCode()!=200) throw new IOException("Official source HTTP "+response.statusCode());
            requirePageContentType(response.contentType());
            if (response.bodyAsBytes().length>4*1024*1024) throw new IOException("Official page exceeds size limit; content was not processed");
            return OfficialPage.parse(url,response.body());
        }
        throw new IOException("Too many official source redirects");
    }

    static void requirePageContentType(String contentType) throws NonPageResourceException {
        if (contentType == null || contentType.isBlank()) return;
        String type = contentType.split(";", 2)[0].strip().toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("text/html", "application/xhtml+xml", "text/plain", "text/xml", "application/xml").contains(type))
            throw new NonPageResourceException("Resource is not a page: " + type);
    }

    static final class NonPageResourceException extends IOException {
        NonPageResourceException(String reason) { super(reason); }
    }
}
