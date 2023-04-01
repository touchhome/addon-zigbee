package org.homio.bundle.zigbee.converter.impl.thermostat;

import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclThermostatCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import org.homio.bundle.zigbee.converter.impl.ZigBeeConverter;
import org.homio.bundle.zigbee.converter.impl.ZigBeeConverterTemperature;
import org.homio.bundle.zigbee.converter.impl.ZigBeeInputBaseConverter;
import org.homio.bundle.api.EntityContextVar.VariableType;

/**
 * Set the cooling temperature when the room is occupied Converter for the thermostat occupied cooling setpoint channel. This specifies the cooling mode setpoint when the room is occupied. It shall be
 * set to a value in the range defined by the MinCoolSetpointLimit and MaxCoolSetpointLimit attributes.
 */
@ZigBeeConverter(
    name = "thermostat_occupiedcooling",
    color = "#F349C", linkType = VariableType.Float,
    clientCluster = ZclThermostatCluster.CLUSTER_ID,
    category = "HVAC")
public class ZigBeeConverterThermostatOccupiedCooling extends ZigBeeInputBaseConverter<ZclThermostatCluster> {

  public ZigBeeConverterThermostatOccupiedCooling() {
    super(ZclClusterType.THERMOSTAT, ZclThermostatCluster.ATTR_OCCUPIEDCOOLINGSETPOINT);
  }

  /*@Override
  public void handleCommand(final ZigBeeCommand command) {
      Integer value = temperatureToValue(command);

      if (value == null) {
          log.warn("[{}]: Thermostat occupied cooling setpoint {} [{}] was not processed", getEndpointEntity(),
                  command, command.getClass().getSimpleName());
          return;
      }

      attribute.writeValue(value);
  }*/

  @Override
  protected void updateValue(Object val, ZclAttribute attribute) {
    updateChannelState(ZigBeeConverterTemperature.valueToTemperature((Integer) val));
  }
}
