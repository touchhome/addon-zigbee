package org.touchhome.bundle.zigbee.converter.impl.thermostat;

import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclThermostatCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import org.touchhome.bundle.api.EntityContextVar.VariableType;
import org.touchhome.bundle.api.state.DecimalType;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeConverter;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeInputBaseConverter;
import org.touchhome.bundle.zigbee.model.ZigBeeEndpointEntity;


/**
 * The level of heating currently demanded by the thermostat
 */
@ZigBeeConverter(
    name = "thermostat_heatingdemand",
    linkType = VariableType.Float,
    color = "#F349C", clientCluster = ZclThermostatCluster.CLUSTER_ID,
    category = "HVAC")
public class ZigBeeConverterThermostatPiHeatingDemand extends ZigBeeInputBaseConverter<ZclThermostatCluster> {

    public ZigBeeConverterThermostatPiHeatingDemand() {
        super(ZclClusterType.THERMOSTAT, ZclThermostatCluster.ATTR_PIHEATINGDEMAND);
    }

    @Override
    protected void updateValue(Object val, ZclAttribute attribute) {
        updateChannelState(new DecimalType((Integer) val).setUnit("%"));
    }

    @Override
    public void configureNewEndpointEntity(ZigBeeEndpointEntity endpointEntity) {
        super.configureNewEndpointEntity(endpointEntity);
        endpointEntity.setAnalogue(1D, 1, 100);
    }
}
