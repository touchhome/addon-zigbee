package org.touchhome.bundle.zigbee.converter.impl.metering;

import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclMeteringCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import java.math.BigDecimal;
import org.touchhome.bundle.api.EntityContextVar.VariableType;
import org.touchhome.bundle.api.state.DecimalType;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeConverter;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeInputBaseConverter;

/**
 * The total delivered from the metering system ZigBee channel converter for summation delivered measurement
 */
@ZigBeeConverter(name = "metering_sumdelivered", linkType = VariableType.Float,
                 color = "#3479CF", clientCluster = ZclMeteringCluster.CLUSTER_ID, category = "Number")
public class ZigBeeConverterMeteringSummationDelivered extends ZigBeeInputBaseConverter<ZclMeteringCluster> {

  private double divisor = 1.0;
  private double multiplier = 1.0;

  public ZigBeeConverterMeteringSummationDelivered() {
    super(ZclClusterType.METERING, ZclMeteringCluster.ATTR_CURRENTSUMMATIONDELIVERED);
  }

  @Override
  public void afterClusterInitialized() {
    this.divisor = readAttribute(zclCluster, ZclMeteringCluster.ATTR_DIVISOR, 1);
    this.multiplier = readAttribute(zclCluster, ZclMeteringCluster.ATTR_MULTIPLIER, 1);
  }

  @Override
  protected void updateValue(Object val, ZclAttribute attribute) {
    double value = ((Long) val).intValue();
    updateChannelState(new DecimalType(BigDecimal.valueOf(value * multiplier / divisor)));
  }
}
