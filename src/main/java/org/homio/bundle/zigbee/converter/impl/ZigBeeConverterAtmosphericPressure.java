package org.homio.bundle.zigbee.converter.impl;

import static com.zsmartsystems.zigbee.zcl.clusters.ZclPressureMeasurementCluster.ATTR_MEASUREDVALUE;
import static com.zsmartsystems.zigbee.zcl.clusters.ZclPressureMeasurementCluster.ATTR_SCALE;
import static com.zsmartsystems.zigbee.zcl.clusters.ZclPressureMeasurementCluster.ATTR_SCALEDVALUE;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclPressureMeasurementCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Consumer;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.EntityContextVar.VariableType;
import org.homio.bundle.api.state.DecimalType;


/**
 * Indicates the current pressure Converter for the atmospheric pressure channel. This channel will attempt to detect if the device is supporting the enhanced
 * (scaled) value reports and use them if they are available.
 */
@ZigBeeConverter(name = "measurement_pressure", linkType = VariableType.Float,
                 color = "#CF8E34", clientCluster = ZclPressureMeasurementCluster.CLUSTER_ID, category = "Pressure")
public class ZigBeeConverterAtmosphericPressure extends ZigBeeInputBaseConverter<ZclPressureMeasurementCluster>
    implements ZclAttributeListener {

  /**
   * If enhancedScale is null, then the binding will use the MeasuredValue report, otherwise it will use the ScaledValue report
   */
  private Integer enhancedScale = null;

  public ZigBeeConverterAtmosphericPressure() {
    super(ZclClusterType.PRESSURE_MEASUREMENT, null);
  }

  @Override
  public boolean acceptEndpoint(ZigBeeEndpoint endpoint, String entityID, EntityContext entityContext, Consumer<String> progressMessage) {
    return endpoint.getInputCluster(ZclPressureMeasurementCluster.CLUSTER_ID) != null;
  }

  @Override
  protected void afterClusterInitialized() {
    // Check if the enhanced attributes are supported
    determineEnhancedScale();
  }

  @Override
  protected void initializeAttribute() {
    this.attributeId = enhancedScale == null ? ATTR_MEASUREDVALUE : ATTR_SCALEDVALUE;
    super.initializeAttribute();
  }

  @Override
  public synchronized void attributeUpdated(ZclAttribute attribute, Object value) {
    log.debug("[{}]: ZigBee attribute reports {} for {}", entityID, attribute, endpoint);
    if (attribute.getClusterType() != ZclClusterType.PRESSURE_MEASUREMENT) {
      return;
    }

    // Handle automatic reporting of the enhanced attribute configuration
    switch (attribute.getId()) {
      case ATTR_SCALE:
        enhancedScale = (Integer) value;
        if (enhancedScale != null) {
          enhancedScale *= -1;
        }
        break;
      case ATTR_SCALEDVALUE:
        if (enhancedScale != null) {
          updateChannelState(new DecimalType(BigDecimal.valueOf((Integer) value)
                                                       .setScale(enhancedScale, RoundingMode.DOWN)).setUnit("hPa"));
        }
        break;
      case ATTR_MEASUREDVALUE:
        if (enhancedScale == null) {
          updateChannelState(new DecimalType(BigDecimal.valueOf((Integer) value)
                                                       .setScale(0, RoundingMode.DOWN)).setUnit("hPa"));
        }
        break;
      default:
        log.error("[{}]: Unable to find handler for attribute: {}. AtmosphericPressure cluster", entityID, attribute.getName());
    }
  }

  private void determineEnhancedScale() {
    if (zclCluster.getAttribute(ATTR_SCALEDVALUE).readValue(Long.MAX_VALUE) != null) {
      enhancedScale = (Integer) zclCluster.getAttribute(ATTR_SCALE).readValue(Long.MAX_VALUE);
      if (enhancedScale != null) {
        enhancedScale *= -1;
        attribute = zclCluster.getAttribute(ATTR_SCALEDVALUE);
      } else {
        attribute = zclCluster.getAttribute(ATTR_MEASUREDVALUE);
      }
    }
  }
}
