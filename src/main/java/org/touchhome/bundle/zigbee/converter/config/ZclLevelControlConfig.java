package org.touchhome.bundle.zigbee.converter.config;

import static com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster.ATTR_DEFAULTMOVERATE;
import static com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster.ATTR_OFFTRANSITIONTIME;
import static com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster.ATTR_ONLEVEL;
import static com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster.ATTR_ONOFFTRANSITIONTIME;
import static com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster.ATTR_ONTRANSITIONTIME;
import static org.touchhome.bundle.zigbee.converter.config.ZclOnOffSwitchConfig.configureAttribute;

import com.zsmartsystems.zigbee.ZigBeeEndpointAddress;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;
import java.util.concurrent.Future;
import lombok.Getter;
import org.apache.logging.log4j.Logger;
import org.touchhome.bundle.zigbee.model.ZigBeeEndpointEntity;

@Getter
public class ZclLevelControlConfig {

  private final ZclLevelControlCluster levelControlCluster;

  private final boolean supportOnLevel;
  private final boolean supportOffTransitionTime;
  private final boolean supportOnOffTransitionTime;
  private final boolean supportDefaultMoveRate;
  private final boolean supportOnTransitionTime;

  private int onOffTransitionTime;
  private int onTransitionTime;
  private int offTransitionTime;
  private int onLevel;
  private int defaultMoveRate;

  public ZclLevelControlConfig(ZigBeeEndpointEntity entity, ZclCluster cluster, Logger log) {
    this.onOffTransitionTime = entity.getOffWaitTime();
    this.onTransitionTime = entity.getOnTransitionTime();
    this.offTransitionTime = entity.getOffTransitionTime();
    this.onLevel = entity.getOnLevel();
    this.defaultMoveRate = entity.getDefaultMoveRate();

    levelControlCluster = (ZclLevelControlCluster) cluster;
    initCluster(levelControlCluster.discoverAttributes(false), log,
        levelControlCluster.getZigBeeAddress(), levelControlCluster.getClusterName());

    supportOnOffTransitionTime = levelControlCluster.isAttributeSupported(ATTR_ONOFFTRANSITIONTIME);
    supportOnTransitionTime = levelControlCluster.isAttributeSupported(ATTR_ONTRANSITIONTIME);
    supportOffTransitionTime = levelControlCluster.isAttributeSupported(ATTR_OFFTRANSITIONTIME);
    supportOnLevel = levelControlCluster.isAttributeSupported(ATTR_ONLEVEL);
    supportDefaultMoveRate = levelControlCluster.isAttributeSupported(ATTR_DEFAULTMOVERATE);
  }

  static void initCluster(Future<Boolean> booleanFuture, Logger log, ZigBeeEndpointAddress zigBeeAddress, String clusterName) {
    try {
      Boolean result = booleanFuture.get();
      if (!result) {
        log.debug("[{}]: Unable to get supported attributes for {}.", zigBeeAddress,
            clusterName);
      }
    } catch (Exception e) {
      log.error("[{}]: Error getting supported attributes for {}. ", zigBeeAddress, clusterName, e);
    }
  }

  public void updateConfiguration(ZigBeeEndpointEntity entity) {
    if (onOffTransitionTime != entity.getOnOffTransitionTime()) {
      onOffTransitionTime = entity.getOnOffTransitionTime();
      Integer response = configureAttribute(levelControlCluster, ATTR_ONOFFTRANSITIONTIME, onOffTransitionTime);
      if (response != null && response != onOffTransitionTime) {
        onOffTransitionTime = response;
        entity.setOnOffTransitionTime(response);
        entity.setOutdated(true);
      }
    }

    if (onTransitionTime != entity.getOnTransitionTime()) {
      onTransitionTime = entity.getOnTransitionTime();
      Integer response = configureAttribute(levelControlCluster, ATTR_ONTRANSITIONTIME, onTransitionTime);
      if (response != null && response != onTransitionTime) {
        onTransitionTime = response;
        entity.setOnTransitionTime(response);
        entity.setOutdated(true);
      }
    }

    if (offTransitionTime != entity.getOffTransitionTime()) {
      offTransitionTime = entity.getOffTransitionTime();
      Integer response = configureAttribute(levelControlCluster, ATTR_OFFTRANSITIONTIME, offTransitionTime);
      if (response != null && response != offTransitionTime) {
        offTransitionTime = response;
        entity.setOffTransitionTime(response);
        entity.setOutdated(true);
      }
    }

    if (onLevel != entity.getOnLevel()) {
      onLevel = entity.getOnLevel();
      Integer response = configureAttribute(levelControlCluster, ATTR_ONLEVEL, onLevel);
      if (response != null && response != onLevel) {
        onLevel = response;
        entity.setOnLevel(response);
        entity.setOutdated(true);
      }
    }

    if (defaultMoveRate != entity.getDefaultMoveRate()) {
      defaultMoveRate = entity.getDefaultMoveRate();
      Integer response = configureAttribute(levelControlCluster, ATTR_DEFAULTMOVERATE, defaultMoveRate);
      if (response != null && response != defaultMoveRate) {
        defaultMoveRate = response;
        entity.setDefaultMoveRate(response);
        entity.setOutdated(true);
      }
    }
  }
}
