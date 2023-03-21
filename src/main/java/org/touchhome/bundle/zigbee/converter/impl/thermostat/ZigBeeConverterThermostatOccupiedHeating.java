package org.touchhome.bundle.zigbee.converter.impl.thermostat;

import static org.touchhome.bundle.zigbee.converter.impl.ZigBeeConverterTemperature.valueToTemperature;

import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclThermostatCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import org.touchhome.bundle.api.EntityContextVar.VariableType;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeConverter;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeInputBaseConverter;

/**
 * Set the heating temperature when the room is occupied Converter for the thermostat occupied heating setpoint channel
 */
@ZigBeeConverter(
    name = "thermostat_occupiedheating",
    color = "#F349C", linkType = VariableType.Float,
    clientCluster = ZclThermostatCluster.CLUSTER_ID,
    category = "HVAC")
public class ZigBeeConverterThermostatOccupiedHeating extends ZigBeeInputBaseConverter<ZclThermostatCluster> {

  public ZigBeeConverterThermostatOccupiedHeating() {
    super(ZclClusterType.THERMOSTAT, ZclThermostatCluster.ATTR_OCCUPIEDHEATINGSETPOINT);
  }

  @Override
  protected void updateValue(Object val, ZclAttribute attribute) {
    updateChannelState(valueToTemperature((Integer) val));
  }

  /*@Override
  public void handleCommand(final ZigBeeCommand command) {
      Integer value = temperatureToValue(command);

      if (value == null) {
          log.warn("[{}]: Thermostat occupied heating setpoint {} [{}] was not processed", getEndpointEntity(),
                  command, command.getClass().getSimpleName());
          return;
      }

      attribute.writeValue(value);
  }*/
}
