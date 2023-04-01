package org.homio.bundle.zigbee.util;

import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
@RequiredArgsConstructor
public class ClusterConfiguration extends ShareConfiguration {

    private final Map<Integer, ClusterAttributeConfiguration> attributeConfigurations =
            new HashMap<>();

    // get zigbee cluster by name
    private final ZclClusterType zclClusterType;

    public void addAttribute(ClusterAttributeConfiguration attributeConfiguration) {
        attributeConfigurations.put(
                attributeConfiguration.getAttributeID(), attributeConfiguration);
    }

    public @NotNull ClusterAttributeConfiguration getAttributeConfiguration(int attributeID) {
        return attributeConfigurations.computeIfAbsent(
                attributeID, integer -> new ClusterAttributeConfiguration(attributeID, this));
    }
}
