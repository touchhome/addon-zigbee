package org.homio.bundle.zigbee.converter.impl.metering;

import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclMeteringCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import java.math.BigDecimal;
import org.homio.bundle.zigbee.converter.impl.ZigBeeConverter;
import org.homio.bundle.zigbee.converter.impl.ZigBeeInputBaseConverter;
import org.homio.bundle.api.EntityContextVar.VariableType;
import org.homio.bundle.api.state.DecimalType;

/**
 * The instantaneous demand from the metering system ZigBee channel converter for instantaneous demand measurement
 */
@ZigBeeConverter(name = "metering_instantdemand", linkType = VariableType.Float,
                 color = "#3479CF", clientCluster = ZclMeteringCluster.CLUSTER_ID, category = "Number")
public class ZigBeeConverterMeteringInstantaneousDemand extends ZigBeeInputBaseConverter<ZclMeteringCluster> {

  private double divisor = 1.0;
  private double multiplier = 1.0;

  public ZigBeeConverterMeteringInstantaneousDemand() {
    super(ZclClusterType.METERING, ZclMeteringCluster.ATTR_INSTANTANEOUSDEMAND);
  }

  @Override
  public void afterClusterInitialized() {
    this.divisor = readAttribute(zclCluster, ZclMeteringCluster.ATTR_DIVISOR, 1);
    this.multiplier = readAttribute(zclCluster, ZclMeteringCluster.ATTR_MULTIPLIER, 1);
  }

  @Override
  protected void updateValue(Object val, ZclAttribute attribute) {
    BigDecimal valueCalibrated = BigDecimal.valueOf((Integer) val * multiplier / divisor);
    updateChannelState(new DecimalType(valueCalibrated));
  }
}
