package org.touchhome.bundle.zigbee.converter.impl.power;

import static com.zsmartsystems.zigbee.zcl.clusters.ZclPowerConfigurationCluster.ATTR_MAINSVOLTAGE;
import static com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType.POWER_CONFIGURATION;

import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclPowerConfigurationCluster;
import java.math.BigDecimal;
import org.touchhome.bundle.api.EntityContextVar.VariableType;
import org.touchhome.bundle.api.state.DecimalType;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeConverter;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeInputBaseConverter;


/**
 * Battery Voltage The current battery voltage
 */
@ZigBeeConverter(name = "battery_mains_voltage", linkType = VariableType.Float,
                 color = "#CF8B34", clientCluster = ZclPowerConfigurationCluster.CLUSTER_ID, category = "Energy")
public class ZigBeeConverterBatteryMainsVoltage extends ZigBeeInputBaseConverter<ZclPowerConfigurationCluster> {

    public ZigBeeConverterBatteryMainsVoltage() {
        super(POWER_CONFIGURATION, ATTR_MAINSVOLTAGE);
    }

    @Override
    protected void updateValue(Object val, ZclAttribute attribute) {
        Integer value = (Integer) val;
        BigDecimal valueInVolt = BigDecimal.valueOf(value, 1);
        updateChannelState(new DecimalType(valueInVolt).setUnit("V"));
    }
}
