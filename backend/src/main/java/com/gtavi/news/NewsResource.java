package com.gtavi.news;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

@Path("/api/v1/games/gta-vi/news")
@Produces(MediaType.APPLICATION_JSON)
public class NewsResource {
    @Inject NewsRepository repository;
    @Inject ProductCatalog catalog;
    @GET
    public Map<String,Object> news(@QueryParam("page") @DefaultValue("0") int page,
                                  @QueryParam("size") @DefaultValue("20") int size) {
        return Map.of("items",repository.list("articles",page,size),"total",repository.count("articles"));
    }
    @GET @Path("/{id}")
    public JsonNode article(@PathParam("id") String id) {
        if (!id.matches("[a-f0-9]{24}")) throw new NotFoundException();
        JsonNode item=repository.read("articles:"+id);
        if(item==null) throw new NotFoundException();
        return item;
    }
    @GET @Path("/products")
    public Map<String,Object> products(@QueryParam("page") @DefaultValue("0") int page,
                                      @QueryParam("size") @DefaultValue("20") int size) {
        return catalog.page(page, size);
    }
    @GET @Path("/status")
    public JsonNode status(){return repository.read("status");}
}
