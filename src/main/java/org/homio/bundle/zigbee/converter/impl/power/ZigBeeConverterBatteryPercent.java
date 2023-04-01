package org.homio.bundle.zigbee.converter.impl.power;

import static com.zsmartsystems.zigbee.zcl.clusters.ZclPowerConfigurationCluster.ATTR_BATTERYPERCENTAGEREMAINING;
import static com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType.POWER_CONFIGURATION;

import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclPowerConfigurationCluster;
import org.homio.bundle.zigbee.converter.impl.ZigBeeConverter;
import org.homio.bundle.zigbee.converter.impl.ZigBeeInputBaseConverter;
import org.homio.bundle.api.EntityContextVar.VariableType;
import org.homio.bundle.api.state.DecimalType;

/**
 * Converter for the battery percent channel.
 */
@ZigBeeConverter(name = "battery_level", linkType = VariableType.Float,
                 color = "#CF8B34", clientCluster = ZclPowerConfigurationCluster.CLUSTER_ID, category = "Battery")
public class ZigBeeConverterBatteryPercent extends ZigBeeInputBaseConverter<ZclPowerConfigurationCluster> {

  public ZigBeeConverterBatteryPercent() {
    super(POWER_CONFIGURATION, ATTR_BATTERYPERCENTAGEREMAINING);
  }

  @Override
  protected void updateValue(Object val, ZclAttribute attribute) {
    Integer value = (Integer) val;
    updateChannelState(new DecimalType(value / 2));
  }
}
