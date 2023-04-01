package org.homio.bundle.zigbee.converter.config;

import static org.homio.bundle.zigbee.converter.config.ZclOnOffSwitchConfig.configureAttribute;

import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclFanControlCluster;
import lombok.Getter;
import org.apache.logging.log4j.Logger;
import org.homio.bundle.zigbee.model.ZigBeeEndpointEntity;

@Getter
public class ZclFanControlConfig {

  private final ZclFanControlCluster fanControlCluster;
  private final boolean supportFanModeSequence;

  private int fanModeSequence;

  public ZclFanControlConfig(ZigBeeEndpointEntity entity, ZclCluster cluster, Logger log) {
    this.fanModeSequence = entity.getFanModeSequence();

    fanControlCluster = (ZclFanControlCluster) cluster;
    ZclLevelControlConfig.initCluster(fanControlCluster.discoverAttributes(false), log,
        fanControlCluster.getZigBeeAddress(), fanControlCluster.getClusterName());

    this.supportFanModeSequence = fanControlCluster.isAttributeSupported(ZclFanControlCluster.ATTR_FANMODESEQUENCE);
  }

  public void updateConfiguration(ZigBeeEndpointEntity entity) {
    if (fanModeSequence != entity.getFanModeSequence()) {
      fanModeSequence = entity.getFanModeSequence();
      Integer response = configureAttribute(fanControlCluster, ZclFanControlCluster.ATTR_FANMODESEQUENCE, fanModeSequence);
      if (response != null && response != fanModeSequence) {
        fanModeSequence = response;
        entity.setFanModeSequence(response);
        entity.setOutdated(true);
      }
    }
  }
}
