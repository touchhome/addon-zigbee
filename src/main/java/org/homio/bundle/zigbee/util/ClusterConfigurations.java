package org.homio.bundle.zigbee.util;

import static org.homio.bundle.api.util.CommonUtils.OBJECT_MAPPER;
import static org.homio.bundle.zigbee.util.JsonReaderUtil.getBoolean;
import static org.homio.bundle.zigbee.util.JsonReaderUtil.getNumber;

import com.fasterxml.jackson.databind.JsonNode;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.homio.bundle.api.util.CommonUtils;


@Log4j2
public final class ClusterConfigurations {

    @Getter
    private static final Map<Integer, ClusterConfiguration> clusterConfigurations = new HashMap<>();

    static {
        var objectNode =
            CommonUtils.readAndMergeJSON(
                        "cluster-configuration.json", OBJECT_MAPPER.createObjectNode());
        for (Iterator<Entry<String, JsonNode>> iterator = objectNode.fields();
                iterator.hasNext(); ) {
            Entry<String, JsonNode> entry = iterator.next();

            String clusterName = entry.getKey();
            JsonNode clusterNode = entry.getValue();
            ZclClusterType zclClusterType = ZclClusterType.valueOf(clusterName);
            var clusterConfiguration = new ClusterConfiguration(zclClusterType);

            assembleShareConfiguration(clusterConfiguration, clusterNode);

            if (clusterNode.has("attributes")) {
                for (JsonNode attributeNode : clusterNode.get("attributes")) {
                    var attributeConfiguration =
                            new ClusterAttributeConfiguration(
                                    Integer.decode(attributeNode.get("id").asText()),
                                    clusterConfiguration);
                    assembleShareConfiguration(attributeConfiguration, attributeNode);
                    clusterConfiguration.addAttribute(attributeConfiguration);
                }
            }
            clusterConfigurations.put(zclClusterType.getId(), clusterConfiguration);
        }
    }

    private static void assembleShareConfiguration(
            ShareConfiguration configuration, JsonNode jsonNode) {
        configuration.setReadAttribute(getBoolean(jsonNode, "readAttributes", null));
        configuration.setDiscoverAttributes(getBoolean(jsonNode, "discoverAttributes", null));

        if (jsonNode.has("report")) {
            JsonNode reportNode = jsonNode.get("report");
            configuration.setReportingTimeMin(getNumber(reportNode, "min"));
            configuration.setReportingTimeMax(getNumber(reportNode, "max"));
            configuration.setReportingChange(getNumber(reportNode, "change"));
            configuration.setReportConfigurable(getBoolean(reportNode, "configurable", null));
        }

        if (jsonNode.has("failedPollingInterval")) {
            configuration.setFailedPollingInterval(jsonNode.get("failedPollingInterval").asInt());
        }
        if (jsonNode.has("bindFailedPollingInterval")) {
            configuration.setBindFailedPollingInterval(
                    jsonNode.get("bindFailedPollingInterval").asInt());
        }
        if (jsonNode.has("successMaxReportInterval")) {
            configuration.setSuccessMaxReportInterval(
                    jsonNode.get("successMaxReportInterval").asInt());
        }
    }

    public static @NotNull ClusterConfiguration getClusterConfiguration(int clusterId) {
        return clusterConfigurations.computeIfAbsent(
                clusterId, id -> new ClusterConfiguration(ZclClusterType.getValueById(clusterId)));
    }
}
