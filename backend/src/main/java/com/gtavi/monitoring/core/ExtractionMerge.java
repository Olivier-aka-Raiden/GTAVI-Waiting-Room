package com.gtavi.monitoring.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;

/** Merge chunk facts without inventing values or silently accepting conflicting release dates. */
final class ExtractionMerge {
    private ExtractionMerge() {}
    static final class ConflictingFactsException extends IllegalArgumentException {
        ConflictingFactsException() { super("Conflicting release dates require another extraction"); }
    }
    static void into(ObjectNode target, JsonNode addition) {
        if(addition==null || !addition.isObject()) throw new IllegalArgumentException("Extraction must be an object");
        addition.fields().forEachRemaining(entry -> {
            String field=entry.getKey();
            JsonNode value=entry.getValue();
            if(value.isNull() || value.isTextual() && value.asText().isBlank()) return;
            if(value.isArray()) {
                var items=new LinkedHashMap<String,JsonNode>();
                target.path(field).forEach(item->items.put(identity(item),item));
                value.forEach(item->{
                    String key=identity(item);
                    if(items.get(key) instanceof ObjectNode old && item.isObject()) {
                        var merged=old.deepCopy();
                        into(merged, item);
                        items.put(key,merged);
                    } else items.put(key,item);
                });
                var array=target.putArray(field);
                items.values().forEach(array::add);
            } else if("releaseDate".equals(field) && target.hasNonNull(field) && !target.get(field).equals(value))
                throw new ConflictingFactsException();
            else if(value.isBoolean()) target.put(field,value.asBoolean() || target.path(field).asBoolean());
            else target.set(field,value);
        });
    }
    private static String identity(JsonNode value) {
        if(!value.isObject()) return value.toString();
        return value.path("name").asText()+"|"+value.path("title").asText()+"|"+value.path("url").asText()
            +"|"+value.path("videoUrl").asText()+"|"+value.path("platform").asText();
    }
}
