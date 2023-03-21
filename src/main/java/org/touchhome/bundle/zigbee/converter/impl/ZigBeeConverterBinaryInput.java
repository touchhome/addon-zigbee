package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.zcl.clusters.ZclBinaryInputBasicCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import org.touchhome.bundle.api.EntityContextVar.VariableType;

/**
 * Binary Input Sensor(Switch) Indicates a binary input sensor state Converter for the binary input sensor.
 */
@ZigBeeConverter(name = "binaryinput", linkType = VariableType.Float,
                 color = "#AA4C49", clientCluster = ZclBinaryInputBasicCluster.CLUSTER_ID, category = "")
public class ZigBeeConverterBinaryInput extends ZigBeeInputBaseConverter<ZclBinaryInputBasicCluster> {

  public ZigBeeConverterBinaryInput() {
    super(ZclClusterType.BINARY_INPUT_BASIC, ZclBinaryInputBasicCluster.ATTR_PRESENTVALUE);
  }
}
