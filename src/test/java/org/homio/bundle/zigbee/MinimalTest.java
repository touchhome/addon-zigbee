package org.homio.bundle.zigbee;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.homio.bundle.zigbee.util.ClusterConfigurations;
import org.homio.bundle.zigbee.util.DeviceConfigurations;

public class MinimalTest {

    @SneakyThrows
    @Test
    public void startupTest() {
        DeviceConfigurations.getDefineEndpoints();
        ClusterConfigurations.getClusterConfigurations();
    }
}
