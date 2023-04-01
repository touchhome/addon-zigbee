package org.homio.bundle.zigbee.service;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeNetworkNodeListener;
import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.zdo.field.NodeDescriptor;
import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.bundle.zigbee.converter.impl.ZigBeeChannelConverterFactory;
import org.homio.bundle.zigbee.model.ZigBeeDeviceEntity;
import org.homio.bundle.zigbee.model.ZigbeeCoordinatorEntity;
import org.homio.bundle.zigbee.util.ZigBeeUtil;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.model.Status;

@Log4j2
@Getter
@RequiredArgsConstructor
public class ZigBeeDiscoveryService implements ZigBeeNetworkNodeListener {

  private final EntityContext entityContext;
  private final ZigBeeChannelConverterFactory channelFactory;
  private final String entityID;

  private volatile boolean scanStarted = false;

  private ZigbeeCoordinatorEntity coordinator;

  public void setCoordinator(ZigbeeCoordinatorEntity coordinator) {
    this.coordinator = coordinator;
  }

  @Override
  public void nodeAdded(ZigBeeNode node) {
    if (!coordinator.isJoinDeviceDuringScanOnly() || scanStarted) {
      nodeDiscovered(node);
    }
  }

  @Override
  public void nodeRemoved(ZigBeeNode node) {
    log.debug("[{}]: Node removed: <{}>", entityID, node);
  }

  @Override
  public void nodeUpdated(ZigBeeNode node) {
    log.debug("[{}]: Node updated: <{}>", entityID, node);
  }

  public void startScan() {
    if (scanStarted) {
      throw new IllegalStateException("zigbee.error.scan_already_started");
    }
    if (!coordinator.getStatus().isOnline()) {
      throw new IllegalStateException("zigbee.error.coordinator_offline");
    }
    try {
      log.info("[{}]: Start scanning...", entityID);
      scanStarted = true;

      for (ZigBeeNode node : coordinator.getService().getNodes()) {
        if (node.getNetworkAddress() == 0) {
          continue;
        }

        nodeDiscovered(node);
      }

      int duration = coordinator.getDiscoveryDuration();
      coordinator.getService().scanStart(duration);

      ZigBeeUtil.zigbeeScanStarted(entityContext, entityID, duration, () -> scanStarted = false, () -> {
        coordinator.getService().scanStart(0);
        scanStarted = false;
      });
    } catch (Exception ex) {
      scanStarted = false;
      throw ex;
    }
  }

  private void nodeDiscovered(ZigBeeNode node) {
    ZigBeeCoordinatorService coordinatorService = coordinator.getService();

    // If this is the coordinator (NWK address 0), ignore this device
    if (node.getLogicalType() == NodeDescriptor.LogicalType.COORDINATOR || node.getNetworkAddress() == 0) {
      return;
    }

    entityContext.bgp().builder("zigbee-node-discover-" + node.getIeeeAddress())
                 .delay(Duration.ofMillis(10))
                 .execute(() -> {
                   log.info("[{}]: Starting ZigBee device discovery {}", entityID, node.getIeeeAddress());
                   ZigBeeDeviceEntity zigBeeDeviceEntity = addZigBeeDevice(node);

                   if (!node.isDiscovered()) {
                     log.debug("[{}]: Node discovery not complete {}", entityID, node.getIeeeAddress());
                     zigBeeDeviceEntity.setStatus(Status.NOT_READY);
                   } else {
                     log.debug("[{}]: Node discovery complete {}", entityID, node.getIeeeAddress());
                     zigBeeDeviceEntity.getService().tryInitializeZigBeeNode();
                     coordinatorService.serializeNetwork(node.getIeeeAddress());
                   }
                 });
  }

  /**
   * Add discovered not to DB and in memory
   */
  private synchronized ZigBeeDeviceEntity addZigBeeDevice(ZigBeeNode node) {
    IeeeAddress ieeeAddress = node.getIeeeAddress();

    ZigBeeDeviceEntity entity = entityContext.getEntity(ZigBeeDeviceEntity.PREFIX + ieeeAddress);
    if (entity == null) {
      entity = new ZigBeeDeviceEntity();
      entity.setEntityID(ZigBeeDeviceEntity.PREFIX + ieeeAddress);
      entity.setIeeeAddress(ieeeAddress.toString());
      entity.setParent(coordinator);
      entity.updateFromNodeDescriptor(node);

      entity = entityContext.save(entity);
    }
    return entity;
  }
}
