package org.touchhome.bundle.zigbee.converter.impl;

import static com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType.ILLUMINANCE_MEASUREMENT;

import com.zsmartsystems.zigbee.zcl.clusters.ZclIlluminanceMeasurementCluster;
import org.touchhome.bundle.api.EntityContextVar.VariableType;
import org.touchhome.bundle.zigbee.model.ZigBeeEndpointEntity;

@ZigBeeConverter(
    name = "measurement_illuminance",
    linkType = VariableType.Float,
    color = "#CF8E34",
    clientCluster = ZclIlluminanceMeasurementCluster.CLUSTER_ID,
    category = "Illuminance")
public class ZigBeeConverterIlluminance extends ZigBeeInputBaseConverter<ZclIlluminanceMeasurementCluster> {

  public ZigBeeConverterIlluminance() {
    super(ILLUMINANCE_MEASUREMENT, ZclIlluminanceMeasurementCluster.ATTR_MEASUREDVALUE);
  }

  @Override
  public void configureNewEndpointEntity(ZigBeeEndpointEntity endpointEntity) {
    super.configureNewEndpointEntity(endpointEntity);
    endpointEntity.setAnalogue(5000D, 10, 20000);
  }
}
