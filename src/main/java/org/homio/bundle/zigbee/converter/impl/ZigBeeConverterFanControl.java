package org.homio.bundle.zigbee.converter.impl;

import static com.zsmartsystems.zigbee.zcl.clusters.ZclFanControlCluster.ATTR_FANMODE;
import static com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType.FAN_CONTROL;

import com.zsmartsystems.zigbee.zcl.clusters.ZclFanControlCluster;
import org.homio.bundle.api.EntityContextVar.VariableType;
import org.homio.bundle.zigbee.converter.config.ZclFanControlConfig;

/**
 * Set the fan mode This channel supports fan control
 */
@ZigBeeConverter(name = "fancontrol", color = "#CF8E34", linkType = VariableType.Float, clientCluster = ZclFanControlCluster.CLUSTER_ID, category = "HVAC")
public class ZigBeeConverterFanControl extends ZigBeeInputBaseConverter<ZclFanControlCluster> {

  private static final int MODE_OFF = 0;
  private static final int MODE_LOW = 1;
  private static final int MODE_MEDIUM = 2;
  private static final int MODE_HIGH = 3;
  private static final int MODE_ON = 4;
  private static final int MODE_AUTO = 5;

  public ZigBeeConverterFanControl() {
    super(FAN_CONTROL, ATTR_FANMODE);
  }

  @Override
  protected void afterClusterInitialized() {
    configFanControl = new ZclFanControlConfig(getEntity(), zclCluster, log);
  }

  @Override
  public void updateConfiguration() {
    if (configFanControl != null) {
      configFanControl.updateConfiguration(getEntity());
    }
  }

  /*@Override
    public void handleCommand(final ZigBeeCommand command) {
        int value;
        if (command instanceof OnOffType) {
            value = command == OnOffType.ON ? MODE_ON : MODE_OFF;
        } else if (command instanceof DecimalType) {
            value = ((DecimalType) command).intValue();
        } else {
            log.debug("[{}]: Unabled to convert fan mode {}", getEndpointEntity(),endpoint.getEndpointId(), command);
            return;
        }

        fanModeAttribute.writeValue(value);
    }*/
}
