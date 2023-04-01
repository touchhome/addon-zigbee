package org.homio.bundle.zigbee.converter.config;

import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import lombok.Getter;
import org.apache.logging.log4j.Logger;
import org.homio.bundle.zigbee.model.ZigBeeEndpointEntity;

/**
 * Configuration handler for the
 */
@Getter
public class ZclOnOffSwitchConfig {

  private final ZclOnOffCluster onoffCluster;

  private final boolean supportOffWaitTime;
  private final boolean supportOnTime;
  private final boolean supportStartupOnOff;

  private int offWaitTime;
  private int onTime;
  private int startupOnOff;

  public ZclOnOffSwitchConfig(ZigBeeEndpointEntity entity, ZclCluster cluster, Logger log) {
    this.offWaitTime = entity.getOffWaitTime();
    this.onTime = entity.getOnTime();
    this.startupOnOff = entity.getStartupOnOff() ? 1 : 0;

    onoffCluster = (ZclOnOffCluster) cluster;
    ZclLevelControlConfig.initCluster(onoffCluster.discoverAttributes(false), log,
        onoffCluster.getZigBeeAddress(), onoffCluster.getClusterName());

    this.supportOffWaitTime = onoffCluster.isAttributeSupported(ZclOnOffCluster.ATTR_OFFWAITTIME);
    this.supportOnTime = onoffCluster.isAttributeSupported(ZclOnOffCluster.ATTR_ONTIME);
    this.supportStartupOnOff = onoffCluster.isAttributeSupported(ZclOnOffCluster.ATTR_STARTUPONOFF);
  }

  public static Integer configureAttribute(ZclCluster zclCluster, int attributeId, int value) {
    ZclAttribute attribute = zclCluster.getAttribute(attributeId);
    attribute.writeValue(value);
    return (Integer) attribute.readValue(0);
  }

  public static Boolean configureAttribute(ZclCluster zclCluster, int attributeId, boolean value) {
    ZclAttribute attribute = zclCluster.getAttribute(attributeId);
    attribute.writeValue(value);
    return (Boolean) attribute.readValue(0);
  }

  public void updateConfiguration(ZigBeeEndpointEntity entity) {
    if (offWaitTime != entity.getOffWaitTime()) {
      offWaitTime = entity.getOffWaitTime();
      Integer response = configureAttribute(onoffCluster, ZclOnOffCluster.ATTR_OFFWAITTIME, offWaitTime);
      if (response != null && response != offWaitTime) {
        offWaitTime = response;
        entity.setOffWaitTime(response);
        entity.setOutdated(true);
      }
    }

    if (onTime != entity.getOnTime()) {
      onTime = entity.getOnTime();
      Integer response = configureAttribute(onoffCluster, ZclOnOffCluster.ATTR_ONTIME, onTime);
      if (response != null && response != onTime) {
        onTime = response;
        entity.setOnTime(response);
        entity.setOutdated(true);
      }
    }

    int entityStartupOnOff = entity.getStartupOnOff() ? 1 : 0;
    if (startupOnOff != entityStartupOnOff) {
      startupOnOff = entityStartupOnOff;
      Integer response = configureAttribute(onoffCluster, ZclOnOffCluster.ATTR_STARTUPONOFF, startupOnOff);
      if (response != null && response != startupOnOff) {
        startupOnOff = response;
        entity.setStartupOnOff(response == 1);
        entity.setOutdated(true);
      }
    }
  }
}
