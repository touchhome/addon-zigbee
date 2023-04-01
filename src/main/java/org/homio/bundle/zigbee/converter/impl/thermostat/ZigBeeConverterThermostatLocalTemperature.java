package org.homio.bundle.zigbee.converter.impl.thermostat;

import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclThermostatCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import org.homio.bundle.zigbee.converter.impl.ZigBeeConverter;
import org.homio.bundle.zigbee.converter.impl.ZigBeeConverterTemperature;
import org.homio.bundle.zigbee.converter.impl.ZigBeeInputBaseConverter;
import org.homio.bundle.api.EntityContextVar.VariableType;

@ZigBeeConverter(
    name = "thermostat_localtemp", linkType = VariableType.Float,
    color = "#F349C", clientCluster = ZclThermostatCluster.CLUSTER_ID, category = "HVAC")
public class ZigBeeConverterThermostatLocalTemperature extends ZigBeeInputBaseConverter<ZclThermostatCluster> {

  public ZigBeeConverterThermostatLocalTemperature() {
    super(ZclClusterType.THERMOSTAT, ZclThermostatCluster.ATTR_LOCALTEMPERATURE);
  }

  @Override
  protected void updateValue(Object val, ZclAttribute attribute) {
    Integer value = (Integer) val;
    int INVALID_TEMPERATURE = 0x8000;
    if (value != null && value != INVALID_TEMPERATURE) {
      updateChannelState(ZigBeeConverterTemperature.valueToTemperature(value));
    }
  }
}
