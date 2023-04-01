package org.homio.bundle.zigbee.converter.impl.power;

import static com.zsmartsystems.zigbee.zcl.clusters.ZclPowerConfigurationCluster.ATTR_BATTERYALARMSTATE;
import static com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType.POWER_CONFIGURATION;

import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclPowerConfigurationCluster;
import org.homio.bundle.zigbee.converter.impl.ZigBeeConverter;
import org.homio.bundle.zigbee.converter.impl.ZigBeeInputBaseConverter;
import org.homio.bundle.api.EntityContextVar.VariableType;
import org.homio.bundle.api.state.StringType;

/**
 * Converter for a battery alarm channel.
 * <p>
 * This converter relies on reports for the BatteryAlarmState attribute of the power configuration cluster, setting the state of the battery alarm channel depending on the bits set in the
 * BatteryAlarmState.
 * <p>
 * Possible future improvements:
 * <ul>
 * <li>The BatteryAlarmState provides battery level information for up to three batteries; this converter only considers
 * the information for the first battery.
 * <li>Devices might use alarms from the Alarms cluster instead of the BatteryAlarmState attribute to indicate battery
 * alarms. This is currently not supported by this converter.
 * <li>Devices might permit to configure the four battery level/voltage thresholds on which battery alarms are signaled;
 * such configuration is currently not supported.
 * </ul>
 */
@ZigBeeConverter(name = "battery_alarm", linkType = VariableType.Float,
                 color = "#CF8B34", clientCluster = ZclPowerConfigurationCluster.CLUSTER_ID, category = "Energy")
public class ZigBeeConverterBatteryAlarm extends ZigBeeInputBaseConverter<ZclPowerConfigurationCluster> {

  public static final String STATE_OPTION_BATTERY_THRESHOLD_1 = "threshold1";
  public static final String STATE_OPTION_BATTERY_THRESHOLD_2 = "threshold2";
  public static final String STATE_OPTION_BATTERY_THRESHOLD_3 = "threshold3";
  public static final String STATE_OPTION_BATTERY_NO_THRESHOLD = "noThreshold";
  private static final String STATE_OPTION_BATTERY_MIN_THRESHOLD = "minThreshold";

  private static final int MIN_THRESHOLD_BITMASK = 0b0001;
  private static final int THRESHOLD_1_BITMASK = 0b0010;
  private static final int THRESHOLD_2_BITMASK = 0b0100;
  private static final int THRESHOLD_3_BITMASK = 0b1000;

  public ZigBeeConverterBatteryAlarm() {
    super(POWER_CONFIGURATION, ATTR_BATTERYALARMSTATE);
  }

  @Override
  protected void updateValue(Object val, ZclAttribute attribute) {
    log.debug("[{}]:ZigBee attribute reports {} for {}", entityID, attribute, endpoint);

    // The value is a 32-bit bitmap, represented by an Integer
    Integer value = (Integer) val;

    if ((value & MIN_THRESHOLD_BITMASK) != 0) {
      updateChannelState(new StringType(STATE_OPTION_BATTERY_MIN_THRESHOLD));
    } else if ((value & THRESHOLD_1_BITMASK) != 0) {
      updateChannelState(new StringType(STATE_OPTION_BATTERY_THRESHOLD_1));
    } else if ((value & THRESHOLD_2_BITMASK) != 0) {
      updateChannelState(new StringType(STATE_OPTION_BATTERY_THRESHOLD_2));
    } else if ((value & THRESHOLD_3_BITMASK) != 0) {
      updateChannelState(new StringType(STATE_OPTION_BATTERY_THRESHOLD_3));
    } else {
      updateChannelState(new StringType(STATE_OPTION_BATTERY_NO_THRESHOLD));
    }
  }
}
