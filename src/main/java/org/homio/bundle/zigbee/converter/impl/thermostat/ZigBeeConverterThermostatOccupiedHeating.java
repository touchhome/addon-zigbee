package org.homio.bundle.zigbee.converter.impl.thermostat;

import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclThermostatCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import org.homio.bundle.zigbee.converter.impl.ZigBeeConverter;
import org.homio.bundle.zigbee.converter.impl.ZigBeeConverterTemperature;
import org.homio.bundle.zigbee.converter.impl.ZigBeeInputBaseConverter;
import org.homio.bundle.api.EntityContextVar.VariableType;

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
    updateChannelState(ZigBeeConverterTemperature.valueToTemperature((Integer) val));
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
