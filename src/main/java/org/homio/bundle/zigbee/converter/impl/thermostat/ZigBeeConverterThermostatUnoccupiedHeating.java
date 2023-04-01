package org.homio.bundle.zigbee.converter.impl.thermostat;

import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclThermostatCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import org.homio.bundle.zigbee.converter.impl.ZigBeeConverter;
import org.homio.bundle.zigbee.converter.impl.ZigBeeConverterTemperature;
import org.homio.bundle.zigbee.converter.impl.ZigBeeInputBaseConverter;
import org.homio.bundle.api.EntityContextVar.VariableType;

/**
 * Set the heating temperature when the room is unoccupied Converter for the thermostat unoccupied heating setpoint channel
 */
@ZigBeeConverter(
    name = "thermostat_unoccupiedheating",
    linkType = VariableType.Float,
    color = "#F349C", clientCluster = ZclThermostatCluster.CLUSTER_ID,
    category = "HVAC")
public class ZigBeeConverterThermostatUnoccupiedHeating extends ZigBeeInputBaseConverter<ZclThermostatCluster> {

  public ZigBeeConverterThermostatUnoccupiedHeating() {
    super(ZclClusterType.THERMOSTAT, ZclThermostatCluster.ATTR_UNOCCUPIEDHEATINGSETPOINT);
  }

  /*@Override
  public void handleCommand(final ZigBeeCommand command) {
      Integer value = temperatureToValue(command);

      if (value == null) {
          log.warn("[{}]: Thermostat unoccupied heating setpoint {} [{}] was not processed",
                  getEndpointEntity(), command, command.getClass().getSimpleName());
          return;
      }

      attribute.writeValue(value);
  }*/

  @Override
  protected void updateValue(Object val, ZclAttribute attribute) {
    updateChannelState(ZigBeeConverterTemperature.valueToTemperature((Integer) val));
  }
}
