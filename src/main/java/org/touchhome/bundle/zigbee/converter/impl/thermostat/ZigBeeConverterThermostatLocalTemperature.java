package org.touchhome.bundle.zigbee.converter.impl.thermostat;

import static org.touchhome.bundle.zigbee.converter.impl.ZigBeeConverterTemperature.valueToTemperature;

import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclThermostatCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import org.touchhome.bundle.api.EntityContextVar.VariableType;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeConverter;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeInputBaseConverter;

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
      updateChannelState(valueToTemperature(value));
    }
  }
}
