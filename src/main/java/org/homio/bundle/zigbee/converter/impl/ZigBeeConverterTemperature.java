package org.homio.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclTemperatureMeasurementCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import java.math.BigDecimal;
import java.util.function.Consumer;
import lombok.Getter;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.EntityContextVar.VariableType;
import org.homio.bundle.api.state.DecimalType;
import org.homio.bundle.zigbee.converter.config.ZclReportingConfig;


/**
 * Indicates the current temperature Converter for the temperature channel
 */
@ZigBeeConverter(
    name = "measurement_temperature",
    linkType = VariableType.Float,
    color = "#CF8E34", serverClusters = {ZclTemperatureMeasurementCluster.CLUSTER_ID},
    clientCluster = ZclTemperatureMeasurementCluster.CLUSTER_ID,
    category = "Temperature")
public class ZigBeeConverterTemperature extends ZigBeeInputBaseConverter<ZclTemperatureMeasurementCluster> {

  @Getter
  private boolean asClient;

  public ZigBeeConverterTemperature() {
    super(ZclClusterType.TEMPERATURE_MEASUREMENT, ZclTemperatureMeasurementCluster.ATTR_MEASUREDVALUE);
  }

  public static DecimalType valueToTemperature(int value) {
    return new DecimalType(BigDecimal.valueOf(value, 2)).setUnit("℃");
  }

  @Override
  public boolean acceptEndpoint(ZigBeeEndpoint endpoint, String entityID, EntityContext entityContext, Consumer<String> progressMessage) {
    if (endpoint.getOutputCluster(ZclTemperatureMeasurementCluster.CLUSTER_ID) == null
        && endpoint.getInputCluster(ZclTemperatureMeasurementCluster.CLUSTER_ID) == null) {
      log.trace("[{}]: Temperature measurement cluster not found {}", entityID, endpoint);
      return false;
    }

    return true;
  }

  @Override
  public void initialize(Consumer<String> progressMessage) {
    if (hasInputCluster(ZclTemperatureMeasurementCluster.CLUSTER_ID)) {
      super.initialize(progressMessage);
      if (configuration.isReportConfigurable()) {
        configReporting = new ZclReportingConfig(getEntity());
      }
      initializeBinding(progressMessage);
      initializeAttribute();
    } else {
      this.asClient = true;
      zclCluster = getOutputCluster(ZclTemperatureMeasurementCluster.CLUSTER_ID);
      attribute = zclCluster.getLocalAttribute(ZclTemperatureMeasurementCluster.ATTR_MEASUREDVALUE);
      attribute.setImplemented(true);
    }
  }

  @Override
  protected void updateValue(Object val, ZclAttribute attribute) {
    updateChannelState(valueToTemperature((Integer) val));
  }

  /*@Override
  public void handleCommand(final ZigBeeCommand command) {
      if (attributeClient == null) {
          log.warn("[{}]: Temperature measurement update but remote client not set", endpoint,
                  command, command.getClass().getSimpleName());
          return;
      }

      Integer value = temperatureToValue(command);

      if (value == null) {
          log.warn("[{}]: Temperature measurement update {} [{}] was not processed", endpoint,
                  command, command.getClass().getSimpleName());
          return;
      }

      attributeClient.setValue(value);
      attributeClient.reportValue(value);
  }*/
}
