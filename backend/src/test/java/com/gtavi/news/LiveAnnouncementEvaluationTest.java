package com.gtavi.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.service.SystemMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in three-request smoke evaluation against the configured production model; never sends push. */
@EnabledIfEnvironmentVariable(named="GTAVI_LIVE_AI_EVALUATION",matches="true")
class LiveAnnouncementEvaluationTest {
    @Test void productionModelRecognizesBothAnnouncementsAndRejectsUnrelatedGame() throws Exception {
        String key=System.getenv("DEEPSEEK_API_KEY");
        assumeTrue(key!=null && !key.isBlank(),"Configured production-model credential is unavailable");
        var json=new ObjectMapper();
        String prompt=String.join("\n",AnnouncementExtractor.class.getMethod("extract",String.class,String.class,String.class)
            .getAnnotation(SystemMessage.class).value());
        var album=OfficialPage.parse(NewsPipelineTest.ARTICLE,NewsPipelineTest.fixture("album-article.html"));
        album=NewswireClient.fromPost(album,json.readTree(NewsPipelineTest.fixture("article-7599a881942544.json")).path("data").path("post"));
        var collector=OfficialPage.parse(NewsPipelineTest.STORE,NewsPipelineTest.fixture("collector-store.html"));
        var unrelated=OfficialPage.parse("https://www.rockstargames.com/newswire/article/unrelated",
            "<h1>Grand Theft Auto V music</h1><p>An official GTA V soundtrack album, unrelated to the next game.</p>");
        var pages=List.of(album,collector,unrelated);
        try(var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            for(int index=0;index<pages.size();index++) {
                var page=pages.get(index);
                var body=json.createObjectNode().put("model",System.getenv().getOrDefault("DEEPSEEK_MODEL","deepseek-chat"))
                    .put("temperature",0).put("max_tokens",1800);
                body.putObject("response_format").put("type","json_object");
                var messages=body.putArray("messages");
                messages.addObject().put("role","system").put("content",prompt+" Return one JSON object.");
                messages.addObject().put("role","user").put("content","Source: "+page.url()+"\nTitle: "+page.title()+"\nEvidence:\n"+page.chunks(12000).getFirst());
                String base=System.getenv().getOrDefault("DEEPSEEK_BASE_URL","https://api.deepseek.com").replaceAll("/+$","");
                var request=HttpRequest.newBuilder(URI.create(base+"/chat/completions")).timeout(Duration.ofSeconds(30))
                    .header("Authorization","Bearer "+key).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
                var response=client.send(request,HttpResponse.BodyHandlers.ofString());
                assertEquals(200,response.statusCode(),"Live model request failed");
                String content=json.readTree(response.body()).path("choices").path(0).path("message").path("content").asText();
                var extracted=json.readTree(content);
                assertEquals(index<2,extracted.path("relevant").asBoolean(),"Live model relevance for case "+index);
                if(index<2) assertEquals(index==0?"MUSIC":"COLLECTIBLE",extracted.path("category").asText());
            }
        }
    }
}
