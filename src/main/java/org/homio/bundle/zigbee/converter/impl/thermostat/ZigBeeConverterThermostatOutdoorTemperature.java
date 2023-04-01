package org.homio.bundle.zigbee.converter.impl.thermostat;

import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclThermostatCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import org.homio.bundle.zigbee.converter.impl.ZigBeeConverter;
import org.homio.bundle.zigbee.converter.impl.ZigBeeConverterTemperature;
import org.homio.bundle.zigbee.converter.impl.ZigBeeInputBaseConverter;
import org.homio.bundle.api.EntityContextVar.VariableType;

/**
 * Indicates the outdoor temperature provided by the thermostat Converter for the thermostat outdoor temperature channel
 */
@ZigBeeConverter(
    name = "thermostat_outdoortemp",
    color = "#F349C", linkType = VariableType.Float,
    clientCluster = ZclThermostatCluster.CLUSTER_ID,
    category = "HVAC")
public class ZigBeeConverterThermostatOutdoorTemperature extends ZigBeeInputBaseConverter<ZclThermostatCluster> {

  public ZigBeeConverterThermostatOutdoorTemperature() {
    super(ZclClusterType.THERMOSTAT, ZclThermostatCluster.ATTR_OUTDOORTEMPERATURE);
  }

  @Override
  protected void updateValue(Object val, ZclAttribute attribute) {
    updateChannelState(ZigBeeConverterTemperature.valueToTemperature((Integer) val));
  }
}
