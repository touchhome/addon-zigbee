package org.touchhome.bundle.zigbee.converter.impl.thermostat;

import static org.touchhome.bundle.zigbee.converter.impl.ZigBeeConverterTemperature.valueToTemperature;

import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclThermostatCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import org.touchhome.bundle.api.EntityContextVar.VariableType;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeConverter;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeInputBaseConverter;

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
    updateChannelState(valueToTemperature((Integer) val));
  }
}
