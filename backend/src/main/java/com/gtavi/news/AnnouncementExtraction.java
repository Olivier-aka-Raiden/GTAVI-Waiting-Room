package com.gtavi.news;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;
import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;

@RegisterForReflection
@JsonAutoDetect(fieldVisibility = ANY)
public record AnnouncementExtraction(Boolean relevant, String category, String importance,
                                     String evidence, List<Product> products, List<Fact> facts) {
    public AnnouncementExtraction(Boolean relevant,String category,String importance,String evidence,List<Product> products) {
        this(relevant,category,importance,evidence,products,List.of());
    }
    @RegisterForReflection
    @JsonAutoDetect(fieldVisibility = ANY)
    public record Fact(String subject,String value,String evidence,String importance) {}
    @RegisterForReflection
    @JsonAutoDetect(fieldVisibility = ANY)
    public record Product(String name, String category, String description, String imageUrl,
                          String purchaseUrl, Double price, String currency, String availability,
                          Boolean limited, Boolean gameIncluded, String evidence) {}
}
