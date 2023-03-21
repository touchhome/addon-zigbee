package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.zcl.clusters.ZclAnalogInputBasicCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import org.touchhome.bundle.api.EntityContextVar.VariableType;
import org.touchhome.bundle.zigbee.model.ZigBeeEndpointEntity;

/**
 * Analog Input Sensor(Switch) Indicates a analog input sensor state Converter for the binary input sensor.
 */
@ZigBeeConverter(name = "analog_input", linkType = VariableType.Float,
                 color = "#B976AE", clientCluster = ZclAnalogInputBasicCluster.CLUSTER_ID, category = "")
public class ZigBeeConverterAnalogInput extends ZigBeeInputBaseConverter<ZclAnalogInputBasicCluster> {

  public ZigBeeConverterAnalogInput() {
    super(ZclClusterType.ANALOG_INPUT_BASIC, ZclAnalogInputBasicCluster.ATTR_PRESENTVALUE);
  }

  @Override
  public void configureNewEndpointEntity(ZigBeeEndpointEntity endpointEntity) {
    super.configureNewEndpointEntity(endpointEntity);
    endpointEntity.setAnalogue(1D, 1, 100);
  }

  @Override
  public String getColor() {
    return "#872B2B";
  }
}
