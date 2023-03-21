package org.touchhome.bundle.zigbee.util;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;

public class JsonReaderUtil {

    public static Boolean getBoolean(JsonNode jsonNode, String key, Boolean defaultValue) {
        if (jsonNode.has(key)) {
            return jsonNode.get(key).asBoolean();
        }
        return defaultValue;
    }

    public static Integer getNumber(JsonNode jsonNode, String key) {
        if (jsonNode.has(key)) {
            return jsonNode.get(key).asInt();
        }
        return null;
    }

    public static Double getDouble(JsonNode jsonNode, String key) {
        if (jsonNode.has(key)) {
            return jsonNode.get(key).asDouble();
        }
        return null;
    }

    public static Set<String> getOptStringArray(JsonNode clusterNode, String key) {
        Set<String> values = new HashSet<>();
        if (clusterNode.has(key)) {
            for (JsonNode jsonNode : clusterNode.get(key)) {
                values.add(jsonNode.asText());
            }
        }
        return values;
    }

    public static Set<Integer> getOptIntegerArray(JsonNode clusterNode, String key) {
        Set<Integer> values = new HashSet<>();
        if (clusterNode.has(key)) {
            for (JsonNode jsonNode : clusterNode.get(key)) {
                values.add(jsonNode.asInt());
            }
        }
        return values;
    }
}
