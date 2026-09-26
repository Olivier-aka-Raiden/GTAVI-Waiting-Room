package com.gtavi.news;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.util.List;
import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;

@JsonAutoDetect(fieldVisibility = ANY)
public record AnnouncementExtraction(Boolean relevant, String category, String importance,
                                     String evidence, List<Product> products, List<Fact> facts) {
    public AnnouncementExtraction(Boolean relevant,String category,String importance,String evidence,List<Product> products) {
        this(relevant,category,importance,evidence,products,List.of());
    }

    @JsonAutoDetect(fieldVisibility = ANY)
    public record Fact(String subject,String value,String evidence,String importance) {}

    @JsonAutoDetect(fieldVisibility = ANY)
    public record Product(String name, String category, String description, String imageUrl,
                          String purchaseUrl, Double price, String currency, String availability,
                          Boolean limited, Boolean gameIncluded, String evidence) {}
}
