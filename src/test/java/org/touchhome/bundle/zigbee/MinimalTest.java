package org.touchhome.bundle.zigbee;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.touchhome.bundle.zigbee.util.ClusterConfigurations;
import org.touchhome.bundle.zigbee.util.DeviceConfigurations;

public class MinimalTest {

    @SneakyThrows
    @Test
    public void startupTest() {
        DeviceConfigurations.getDefineEndpoints();
        ClusterConfigurations.getClusterConfigurations();
    }
}
