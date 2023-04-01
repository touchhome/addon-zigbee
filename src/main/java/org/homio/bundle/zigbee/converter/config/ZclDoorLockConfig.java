package org.homio.bundle.zigbee.converter.config;

import static org.homio.bundle.zigbee.converter.config.ZclOnOffSwitchConfig.configureAttribute;

import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclDoorLockCluster;
import lombok.Getter;
import org.apache.logging.log4j.Logger;
import org.homio.bundle.zigbee.model.ZigBeeEndpointEntity;

/**
 * Configuration handler for the {@link ZclDoorLockCluster}
 */
@Getter
public class ZclDoorLockConfig {

  private final ZclDoorLockCluster doorLockCluster;
  private final boolean supportSoundVolume;
  private final boolean supportAutoRelockTime;
  private final boolean supportEnableOneTouchLocking;
  private final boolean supportLocalProgramming;

  private int soundVolume;
  private int enableAutoRelockTime;
  private boolean enableLocalProgramming;
  private boolean enableOneTouchLocking;

  public ZclDoorLockConfig(ZigBeeEndpointEntity entity, ZclCluster cluster, Logger log) {
    doorLockCluster = (ZclDoorLockCluster) cluster;
    ZclLevelControlConfig.initCluster(doorLockCluster.discoverAttributes(false), log,
        doorLockCluster.getZigBeeAddress(), doorLockCluster.getClusterName());

    this.supportSoundVolume = doorLockCluster.isAttributeSupported(ZclDoorLockCluster.ATTR_SOUNDVOLUME);
    this.supportAutoRelockTime = doorLockCluster.isAttributeSupported(ZclDoorLockCluster.ATTR_AUTORELOCKTIME);
    this.supportEnableOneTouchLocking = doorLockCluster.isAttributeSupported(ZclDoorLockCluster.ATTR_ENABLEONETOUCHLOCKING);
    this.supportLocalProgramming = doorLockCluster.isAttributeSupported(ZclDoorLockCluster.ATTR_ENABLELOCALPROGRAMMING);

    this.soundVolume = entity.getSoundVolume();
    this.enableAutoRelockTime = entity.getEnableAutoRelockTime();
    this.enableLocalProgramming = entity.getEnableLocalProgramming();
    this.enableOneTouchLocking = entity.getEnableOneTouchLocking();
  }

  public void updateConfiguration(ZigBeeEndpointEntity entity) {
    if (soundVolume != entity.getSoundVolume()) {
      soundVolume = entity.getSoundVolume();
      Integer response = configureAttribute(doorLockCluster, ZclDoorLockCluster.ATTR_ENABLEONETOUCHLOCKING, soundVolume);
      if (response != null && response != soundVolume) {
        soundVolume = response;
        entity.setSoundVolume(response);
        entity.setOutdated(true);
      }
    }

    if (enableAutoRelockTime != entity.getEnableAutoRelockTime()) {
      enableAutoRelockTime = entity.getEnableAutoRelockTime();
      Integer response = configureAttribute(doorLockCluster, ZclDoorLockCluster.ATTR_AUTORELOCKTIME, enableAutoRelockTime);
      if (response != null && response != enableAutoRelockTime) {
        enableAutoRelockTime = response;
        entity.setEnableAutoRelockTime(response);
        entity.setOutdated(true);
      }
    }

    if (enableLocalProgramming != entity.getEnableLocalProgramming()) {
      enableLocalProgramming = entity.getEnableLocalProgramming();
      Boolean response = configureAttribute(doorLockCluster, ZclDoorLockCluster.ATTR_ENABLELOCALPROGRAMMING, enableLocalProgramming);
      if (response != null && response != enableLocalProgramming) {
        enableLocalProgramming = response;
        entity.setEnableLocalProgramming(enableLocalProgramming);
        entity.setOutdated(true);
      }
    }

    if (enableOneTouchLocking != entity.getEnableOneTouchLocking()) {
      enableOneTouchLocking = entity.getEnableLocalProgramming();
      Boolean response = configureAttribute(doorLockCluster, ZclDoorLockCluster.ATTR_ENABLEONETOUCHLOCKING, enableOneTouchLocking);
      if (response != null && response != enableOneTouchLocking) {
        enableOneTouchLocking = response;
        entity.setEnableOneTouchLocking(enableOneTouchLocking);
        entity.setOutdated(true);
      }
    }
  }
}
