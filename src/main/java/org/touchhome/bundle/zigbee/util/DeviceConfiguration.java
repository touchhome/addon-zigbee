package org.touchhome.bundle.zigbee.util;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
public class DeviceConfiguration {

    private @Nullable String category;
    private @NotNull Set<String> models;
    private @NotNull String vendor;
    private @Nullable String image;
    private @Nullable String icon;
    private @Nullable String iconColor;
    private Map<Integer, Map<String, EndpointDefinition>> endpoints = new HashMap<>();

    public String getImage() {
        return image == null ? "zigbee-" + getFirstModel() : image;
    }

    public List<EndpointDefinition> getEndpoints() {
        List<EndpointDefinition> definitions = new ArrayList<>();
        for (Map<String, EndpointDefinition> entry : endpoints.values()) {
            definitions.addAll(entry.values());
        }
        return definitions;
    }

    public String getFirstModel() {
        return models.iterator().next();
    }

    public @Nullable JsonNode findMetadata(int endpointId, String converterName) {
        EndpointDefinition endpointDefinition = getEndpoint(endpointId, converterName);
        if (endpointDefinition != null) {
            return endpointDefinition.getMetadata();
        }
        return null;
    }

    public @Nullable EndpointDefinition getEndpoint(int endpointId, String converterName) {
        Map<String, EndpointDefinition> map = endpoints.get(endpointId);
        if (map != null) {
            return map.get(converterName);
        }
        return null;
    }

    public void addEndpoint(EndpointDefinition endpointDefinition) {
        endpoints
                .computeIfAbsent(endpointDefinition.getEndpoint(), e -> new HashMap<>())
                .put(endpointDefinition.getTypeId(), endpointDefinition);
    }

    @Getter
    @Setter
    @ToString
    public static class EndpointDefinition {

        private @Nullable String id;
        private int endpoint;
        private @NotNull Set<Integer> inputClusters;
        private @NotNull String typeId;
        private @Nullable String unit;

        private JsonNode metadata;

        public String getId() {
            return id == null ? typeId : id;
        }

        public String getLabel() {
            return "zigbee.endpoint.name." + getId();
        }

        public String getDescription() {
            return "zigbee.endpoint.description." + getId();
        }
    }
}
