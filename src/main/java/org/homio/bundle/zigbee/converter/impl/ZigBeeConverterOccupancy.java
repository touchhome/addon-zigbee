package org.homio.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOccupancySensingCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import org.homio.bundle.api.EntityContextVar.VariableType;
import org.homio.bundle.api.state.OnOffType;

@ZigBeeConverter(name = "sensor_occupancy", linkType = VariableType.Bool,
                 color = "#CF8E34", clientCluster = ZclOccupancySensingCluster.CLUSTER_ID, category = "Motion")
public class ZigBeeConverterOccupancy extends ZigBeeInputBaseConverter<ZclOccupancySensingCluster> {

    public ZigBeeConverterOccupancy() {
        super(ZclClusterType.OCCUPANCY_SENSING, ZclOccupancySensingCluster.ATTR_OCCUPANCY);
    }

    @Override
    protected void updateValue(Object val, ZclAttribute attribute) {
        Integer value = (Integer) val;
        if (value != null && value == 1) {
            updateChannelState(OnOffType.ON);
        } else {
            updateChannelState(OnOffType.OFF);
        }
    }
}
