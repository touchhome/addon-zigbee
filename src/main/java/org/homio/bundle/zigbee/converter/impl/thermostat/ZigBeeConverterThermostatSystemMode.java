package org.homio.bundle.zigbee.converter.impl.thermostat;

import com.zsmartsystems.zigbee.zcl.clusters.ZclThermostatCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import org.homio.bundle.zigbee.converter.impl.ZigBeeConverter;
import org.homio.bundle.zigbee.converter.impl.ZigBeeInputBaseConverter;
import org.homio.bundle.api.EntityContextVar.VariableType;

/**
 * Set the system mode of the thermostat Converter for the thermostat system mode channel. The SystemMode attribute specifies the current operating mode of the thermostat,
 */
@ZigBeeConverter(name = "thermostat_systemmode", linkType = VariableType.Float,
                 color = "#F349C", clientCluster = ZclThermostatCluster.CLUSTER_ID, category = "HVAC")
public class ZigBeeConverterThermostatSystemMode extends ZigBeeInputBaseConverter<ZclThermostatCluster> {

   /* private final static int STATE_MIN = 0;
    private final static int STATE_MAX = 9;
    private final static int STATE_OFF = 0;
    private final static int STATE_AUTO = 1;*/

  public ZigBeeConverterThermostatSystemMode() {
    super(ZclClusterType.THERMOSTAT, ZclThermostatCluster.ATTR_SYSTEMMODE);
  }

  /**
   * // Sequence of operation defines the allowable modes
   *         ZclAttribute attribute = cluster.getAttribute(ZclThermostatCluster.ATTR_CONTROLSEQUENCEOFOPERATION);
   *         Integer states = (Integer) attribute.readValue(Long.MAX_VALUE);
   *         /*List<StateOption> options = new ArrayList<>();
   *         options.add(new StateOption("0", "Off"));
   *         options.add(new StateOption("1", "Auto"));
   *         if (states != null && states != 0 && states != 1) {
   *             options.add(new StateOption("4", "Heat"));
   *             options.add(new StateOption("5", "Emergency Heating"));
   *         }
   *         if (states != null && states != 3 && states != 6) {
   *             options.add(new StateOption("3", "Cool"));
   *             options.add(new StateOption("6", "Precooling"));
   *         }
   *         options.add(new StateOption("7", "Fan Only"));
   *         options.add(new StateOption("8", "Dry"));
   *         options.add(new StateOption("9", "Sleep"));
   *
   *         stateDescription = new StateDescription(BigDecimal.ZERO, BigDecimal.valueOf(9), BigDecimal.valueOf(1), "", false,
   *                 options);
   **/

    /*@Override
    public void handleCommand(final ZigBeeCommand command) {
        Integer value = null;
        if (command instanceof OnOffType) {
            // OnOff switches between OFF=OFF and ON=AUTO
            value = ((OnOffType) command) == OnOffType.ON ? STATE_AUTO : STATE_OFF;
        } else if (command instanceof Number) {
            value = ((Number) command).intValue();
        }

        if (value == null) {
            log.warn("[{}]: System mode command {} [{}] was not processed", endpoint, command,
                    command.getClass().getSimpleName());
            return;
        }

        if (value < STATE_MIN || value > STATE_MAX) {
            log.warn("[{}]: System mode command {} [{}], value {}, was out of limits", endpoint,
                    command, command.getClass().getSimpleName(), value);
            return;
        }

        attribute.writeValue(value);
    }*/
}
