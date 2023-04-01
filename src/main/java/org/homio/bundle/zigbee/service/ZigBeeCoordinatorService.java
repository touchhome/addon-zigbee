package org.homio.bundle.zigbee.service;

import com.zsmartsystems.zigbee.ExtendedPanId;
import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeAnnounceListener;
import com.zsmartsystems.zigbee.ZigBeeChannel;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.ZigBeeEndpointAddress;
import com.zsmartsystems.zigbee.ZigBeeNetworkManager;
import com.zsmartsystems.zigbee.ZigBeeNetworkNodeListener;
import com.zsmartsystems.zigbee.ZigBeeNetworkState;
import com.zsmartsystems.zigbee.ZigBeeNetworkStateListener;
import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.ZigBeeProfileType;
import com.zsmartsystems.zigbee.ZigBeeStatus;
import com.zsmartsystems.zigbee.app.discovery.ZigBeeDiscoveryExtension;
import com.zsmartsystems.zigbee.app.otaserver.ZigBeeOtaUpgradeExtension;
import com.zsmartsystems.zigbee.security.MmoHash;
import com.zsmartsystems.zigbee.security.ZigBeeKey;
import com.zsmartsystems.zigbee.serialization.DefaultDeserializer;
import com.zsmartsystems.zigbee.serialization.DefaultSerializer;
import com.zsmartsystems.zigbee.transport.TransportConfig;
import com.zsmartsystems.zigbee.transport.TransportConfigOption;
import com.zsmartsystems.zigbee.transport.TrustCentreJoinMode;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportFirmwareUpdate;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportTransmit;
import com.zsmartsystems.zigbee.zcl.clusters.ZclBasicCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOtaUpgradeCluster;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.bundle.zigbee.ZigBeeConsolePlugin;
import org.homio.bundle.zigbee.converter.impl.ZigBeeChannelConverterFactory;
import org.homio.bundle.zigbee.internal.ZigBeeDataStore;
import org.homio.bundle.zigbee.model.ZigBeeDeviceEntity;
import org.homio.bundle.zigbee.model.ZigbeeCoordinatorEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.EntityContextBGP.ThreadContext;
import org.homio.bundle.api.entity.zigbee.ZigBeeDeviceBaseEntity;
import org.homio.bundle.api.model.ActionResponseModel;
import org.homio.bundle.api.model.HasEntityIdentifier;
import org.homio.bundle.api.model.Status;
import org.homio.bundle.api.service.EntityService.ServiceInstance;
import org.homio.bundle.api.util.CommonUtils;
import org.homio.bundle.zigbee.setting.ZigBeeDiscoveryOnStartupSetting;
import org.homio.bundle.api.util.Lang;

/**
 * The {@link ZigBeeCoordinatorService} is responsible for handling commands, which are sent to one of the zigbeeRequireEndpoints.
 * <p>
 * This is the base coordinator handler. It handles the majority of the interaction with the ZigBeeNetworkManager.
 * <p>
 * The interface coordinators are responsible for opening a ZigBeeTransport implementation and passing this to the {@link ZigBeeCoordinatorService}.
 */
@Log4j2
public abstract class ZigBeeCoordinatorService
    implements ZigBeeNetworkStateListener, ZigBeeNetworkNodeListener, HasEntityIdentifier,
    ServiceInstance<ZigbeeCoordinatorEntity> {

  private static final int RECONNECT_RATE = 30;
  protected final EntityContext entityContext;
  private final Object entityUpdateSync = new Object();
  private final Set<ZigBeeNetworkNodeListener> nodeListeners = new CopyOnWriteArraySet<>();
  private final Set<ZigBeeAnnounceListener> announceListeners = new CopyOnWriteArraySet<>();

  private final Object updateCoordinatorSync = new Object();

  /**
   * The factory to create the converters for the different zigbeeRequireEndpoints.
   */
  private final ZigBeeChannelConverterFactory channelFactory;
  private final Class<?> serializerClass = DefaultSerializer.class;
  private final Class<?> deserializerClass = DefaultDeserializer.class;

  @Getter
  private final ZigBeeDiscoveryService discoveryService;

  private final Object reconnectLock = new Object();
  private final String entityID;
  @Getter
  private final Set<ZigBeeDeviceService> registeredDevices = ConcurrentHashMap.newKeySet();
  @Getter
  private ZigBeeTransportTransmit zigBeeTransport;
  private ZigBeeKey linkKey;
  private ZigBeeKey networkKey;
  private ExtendedPanId extendedPanId;
  private IeeeAddress nodeIeeeAddress;
  private ZigBeeNetworkManager networkManager;
  private ZigBeeDataStore networkDataStore;
  private TransportConfig transportConfig;
  /**
   * Set to true on startup if we want to reinitialize the network
   */
  private boolean initializeNetwork = false;
  private ThreadContext<Void> reconnectPollingTimer;
  private boolean currentReconnectAttemptFinished = false;
  @Getter
  private ZigbeeCoordinatorEntity entity;
  @Getter
  private boolean initialized;

  @Getter
  private @Nullable Status desiredStatus;
  @Getter
  private @Nullable Status updatingStatus;

  public ZigBeeCoordinatorService(EntityContext entityContext, ZigbeeCoordinatorEntity entity) {
    this.entity = entity;
    this.entityID = entity.getEntityID();
    this.channelFactory = entityContext.getBean(ZigBeeChannelConverterFactory.class);
    this.entityContext = entityContext;

    this.discoveryService = new ZigBeeDiscoveryService(entityContext, channelFactory, entityID);
    this.discoveryService.setCoordinator(entity);

    this.addNetworkNodeListener(this.discoveryService);

    this.entityContext.ui().registerConsolePlugin("zigbee-console-" + entityID,
        new ZigBeeConsolePlugin(entityContext, this));

    this.entityContext.bgp().builder("zigbee-coordinator-" + entityID)
                      .delay(Duration.ofMinutes(1)).interval(Duration.ofMinutes(1)).cancelOnError(false).execute(() -> {
          for (ZigBeeDeviceService device : registeredDevices) {
            device.checkOffline(false);
          }
        });
  }

  public void initialize() {
    initialized = false;
    log.info("[{}]: Initializing ZigBee network.", entityID);

    extendedPanId = StringUtils.isEmpty(entity.getExtendedPanId()) ? null : new ExtendedPanId(entity.getExtendedPanId());

    if (extendedPanId == null || extendedPanId.equals(new ExtendedPanId()) || entity.getPanId() == 0) {
      initializeNetwork = true;
      log.debug("[{}]: ExtendedPanId or PanId not set: initializeNetwork=true", entityID);
    }

    networkKey = new ZigBeeKey(entity.getNetworkKey());
    linkKey = new ZigBeeKey(entity.getLinkKey());

    log.debug("[{}]: Initialising network", entityID);
    initializeNetwork = false;

    initializeDongle();
    entityContext.ui().headerButtonBuilder("discover-" + entityID)
                 .title("zigbee.action.start_scan")
                 .icon("fas fa-search-location", "#899343", false)
                 .availableForPage(ZigBeeDeviceBaseEntity.class)
                 .clickAction(() -> {
                   discoveryService.startScan();
                   return ActionResponseModel.success();
                 }).build();
    initialized = true;
  }

  protected abstract void initializeDongle();

  /**
   * A dongle specific initialisation method. This can be overridden by coordinator handlers and is called just before the {@link ZigBeeTransportTransmit#startup(boolean)} is called.
   */
  protected void initializeDongleSpecific() {
    // Can be overridden to provide dongle specific configuration
  }

  public void dispose() {
    log.warn("[{}]: Dispose coordinator", entityID);
    // shutdown reconnect task
    if (reconnectPollingTimer != null) {
      reconnectPollingTimer.cancel();
    }

    if (networkManager != null) {
      for (ZigBeeNetworkNodeListener listener : nodeListeners) {
        networkManager.removeNetworkNodeListener(listener);
      }
      for (ZigBeeAnnounceListener listener : announceListeners) {
        networkManager.removeAnnounceListener(listener);
      }

      for (ZigBeeDeviceService registeredDevice : registeredDevices) {
        registeredDevice.destroy();
      }

      // Shut down the ZigBee library
      networkManager.shutdown();
    }

    this.entity.setStatus(Status.OFFLINE);
    entityContext.ui().unRegisterConsolePlugin("zigbee-console-" + entityID);
    entityContext.ui().sendWarningMessage("Dispose zigBee coordinator");
    log.warn("[{}]: ZigBee network closed.", entityID);
    this.initialized = false;
  }

  /**
   * Common initialisation point for all ZigBee coordinators. Called by bridge implementations after they have initialized their interfaces.
   */
  protected void startZigBee(ZigBeeTransportTransmit zigbeeTransport, TransportConfig transportConfig) {
    this.zigBeeTransport = zigbeeTransport;
    this.transportConfig = transportConfig;

    initializeZigBee();
  }

  /**
   * Initialize the ZigBee network
   */
  private void initializeZigBee() {
    log.debug("[{}]: Initialising ZigBee coordinator", entityID);

    String networkId = entity.getNetworkId();
    log.warn("[{}]: ZigBee use networkID: <{}>", entityID, networkId);

    networkManager = new ZigBeeNetworkManager(zigBeeTransport);
    networkDataStore = new ZigBeeDataStore(networkId, entityContext, entityID);

    // Configure the network manager
    networkManager.setNetworkDataStore(networkDataStore);
    networkManager.setSerializer(serializerClass, deserializerClass);
    networkManager.addNetworkStateListener(this);
    networkManager.addNetworkNodeListener(this);

    // Initialize the network
    ZigBeeStatus initializeResponse = networkManager.initialize();

    if (zigBeeTransport instanceof ZigBeeTransportFirmwareUpdate) {
      ZigBeeTransportFirmwareUpdate firmwareTransport = (ZigBeeTransportFirmwareUpdate) zigBeeTransport;
      String localIeeeAddress = networkManager.getLocalIeeeAddress().toString();
      if (!entity.getFirmwareVersion().equals(firmwareTransport.getFirmwareVersion())
          || !Objects.equals(entity.getLocalIeeeAddress(), localIeeeAddress)) {
        synchronized (entityUpdateSync) {
          entity.setFirmwareVersion(firmwareTransport.getFirmwareVersion());
          entity.setLocalIeeeAddress(localIeeeAddress);
          entity = entityContext.save(entity);
        }
      }
    }

    switch (initializeResponse) {
      case SUCCESS:
        break;
      case BAD_RESPONSE:
        entity.setStatus(Status.OFFLINE, "zigbee.error.offline_bad_response");
        return;
      case COMMUNICATION_ERROR:
        entity.setStatus(Status.OFFLINE, "zigbee.error.offline_comms_fail");
        return;
      default:
        entity.setStatus(Status.OFFLINE, "zigbee.error.offline_initialize_fail");
        return;
    }

    // Add the extensions to the network
    ZigBeeDiscoveryExtension discoveryExtension = new ZigBeeDiscoveryExtension();
    discoveryExtension.setUpdateMeshPeriod(entity.getMeshUpdatePeriod());
    networkManager.addExtension(discoveryExtension);
    networkManager.addExtension(new ZigBeeOtaUpgradeExtension());

    // Add any listeners that were registered before the manager was registered
    for (ZigBeeNetworkNodeListener listener : nodeListeners) {
      networkManager.addNetworkNodeListener(listener);
    }

    synchronized (announceListeners) {
      for (ZigBeeAnnounceListener listener : announceListeners) {
        networkManager.addAnnounceListener(listener);
      }
    }

    // Add all the clusters that we are supporting.
    // If we don't do this, the framework will reject any packets for clusters we have not stated support for.
    channelFactory.getAllClientClusterIds()
                  .forEach(clusterId -> networkManager.addSupportedClientCluster(clusterId));
    channelFactory.getAllServerClusterIds()
                  .forEach(clusterId -> networkManager.addSupportedServerCluster(clusterId));

    networkManager.addSupportedClientCluster(ZclBasicCluster.CLUSTER_ID);
    networkManager.addSupportedClientCluster(ZclOtaUpgradeCluster.CLUSTER_ID);
    networkManager.addSupportedServerCluster(ZclBasicCluster.CLUSTER_ID);

    // Show the initial network configuration for debugging
    ZigBeeChannel currentChannel = networkManager.getZigBeeChannel();
    int currentPanId = networkManager.getZigBeePanId();
    ExtendedPanId currentExtendedPanId = networkManager.getZigBeeExtendedPanId();

    log.info("[{}]: ZigBee Initialize: Previous device configuration was: channel={}, PanID={}, EPanId={}",
        entityID, currentChannel, currentPanId, currentExtendedPanId);

    if (initializeNetwork) {
      log.debug("[{}]: Link key initialize {}", entityID, linkKey);
      log.debug("[{}]: Network key initialize {}", entityID, networkKey);
      networkManager.setZigBeeLinkKey(linkKey);
      networkManager.setZigBeeNetworkKey(networkKey);
      networkManager.setZigBeeChannel(ZigBeeChannel.create(entity.getChannelId()));
      networkManager.setZigBeePanId(entity.getPanId());
      networkManager.setZigBeeExtendedPanId(extendedPanId);
    }

    addInstallCode(entity.getInstallCode());

    transportConfig.addOption(TransportConfigOption.RADIO_TX_POWER, entity.getTxPower());

    if (entity.getTrustCentreJoinMode() != -1) {
      TrustCentreJoinMode linkMode = TrustCentreJoinMode.values()[entity.getTrustCentreJoinMode()];
      log.debug("[{}]: Config zigbee trustcentremode: {}", entityID, linkMode);
      transportConfig.addOption(TransportConfigOption.TRUST_CENTRE_JOIN_MODE, linkMode);
    }

    zigBeeTransport.updateTransportConfig(transportConfig);

    // Call startup. The setting of the bring to ONLINE will be done via the state listener.
    if (networkManager.startup(initializeNetwork) != ZigBeeStatus.SUCCESS) {
      entity.setStatus(Status.OFFLINE, "OFFLINE_STARTUP_FAIL");
      return;
    }

    // Get the final network configuration
    nodeIeeeAddress = networkManager.getLocalIeeeAddress();
    log.info("[{}]: ZigBee initialize done. channel={}, PanId={}  EPanId={}",
        entityID,
        networkManager.getZigBeeChannel(),
        networkManager.getZigBeePanId(),
        networkManager.getZigBeeExtendedPanId());

    initializeDongleSpecific();
  }

  /**
   * Process the adding of an install code
   *
   * @param installCode the string representation of the install code //     * @param transportConfig the {@link TransportConfig} to populate with the configuration
   */
  public void addInstallCode(String installCode) {
    if (installCode == null || installCode.isEmpty()) {
      return;
    }

    // Split the install code and the address
    String[] codeParts = installCode.split(":");
    if (codeParts.length != 2) {
      log.warn("[{}]: Incorrectly formatted install code configuration {} {}", entityID,
          nodeIeeeAddress, installCode);
      return;
    }

    MmoHash mmoHash = new MmoHash(codeParts[1].replace("-", ""));
    ZigBeeKey key = new ZigBeeKey(mmoHash.getHash());
    key.setAddress(new IeeeAddress(codeParts[0]));

    networkManager.setZigBeeInstallKey(key);
  }

  /**
   * Adds a {@link ZigBeeNetworkNodeListener} to receive updates on node status
   *
   * @param listener the {@link ZigBeeNetworkNodeListener} to add
   */
  public void addNetworkNodeListener(ZigBeeNetworkNodeListener listener) {
    // Save the listeners until the network is initialized
    synchronized (nodeListeners) {
      nodeListeners.add(listener);
    }

    if (networkManager != null) {
      networkManager.addNetworkNodeListener(listener);
    }
  }

  /**
   * Adds a {@link ZigBeeAnnounceListener} to receive node announce messages
   *
   * @param listener the {@link ZigBeeAnnounceListener} to add
   */
  public void addAnnounceListener(ZigBeeAnnounceListener listener) {
    // Save the listeners until the network is initialized
    synchronized (announceListeners) {
      announceListeners.add(listener);
    }

    if (networkManager != null) {
      networkManager.addAnnounceListener(listener);
    }
  }

  @Override
  public void nodeAdded(ZigBeeNode node) {
    nodeUpdated(node);
  }

  @Override
  public void nodeUpdated(ZigBeeNode node) {
    // We're only interested in the coordinator here.
    if (node.getNetworkAddress() != 0) {
      return;
    }
    synchronized (entityUpdateSync) {
      entity = entity.nodeUpdated(node, entityContext);
    }
  }

  public ZigBeeEndpoint getEndpoint(IeeeAddress address, int endpointId) {
    if (networkManager == null) {
      return null;
    }
    ZigBeeNode node = networkManager.getNode(address);
    if (node == null) {
      return null;
    }
    return node.getEndpoint(endpointId);
  }

  public Collection<ZigBeeEndpoint> getNodeEndpoints(IeeeAddress nodeIeeeAddress) {
    if (networkManager == null) {
      return Collections.emptySet();
    }
    ZigBeeNode node = networkManager.getNode(nodeIeeeAddress);
    if (node == null) {
      return Collections.emptySet();
    }

    return node.getEndpoints();
  }

  public IeeeAddress getLocalIeeeAddress() {
    return networkManager.getLocalIeeeAddress();
  }

  /**
   * Gets the local endpoint associated with the specified {@link ZigBeeProfileType}
   *
   * @param profile the {@link ZigBeeProfileType} of the endpoint
   * @return the endpoint ID
   */
  public int getLocalEndpointId(ZigBeeProfileType profile) {
    return 1;
  }

  @Override
  public void networkStateUpdated(final ZigBeeNetworkState state) {
    log.info("[{}]: networkStateUpdated {} called with state={}", entityID, nodeIeeeAddress, state);
    switch (state) {
      case ONLINE:
        entity.setStatusOnline();

        for (ZigBeeDeviceEntity device : entity.getDevices()) {
          device.getService().coordinatorOnline();
        }

        if (reconnectPollingTimer != null) {
          reconnectPollingTimer.cancel();
          reconnectPollingTimer = null;
        }
        if (entityContext.setting().getValue(ZigBeeDiscoveryOnStartupSetting.class)) {
          this.scanStart(getEntity().getDiscoveryDuration());
        }
        break;
      case OFFLINE:
        entity.setStatus(Status.OFFLINE);
        for (ZigBeeDeviceEntity device : entity.getDevices()) {
          device.getService().coordinatorOffline();
        }
        startReconnectJobIfNotRunning();
        break;
    }
    if (state != ZigBeeNetworkState.INITIALISING && state != ZigBeeNetworkState.UNINITIALISED) {
      notifyReconnectJobAboutFinishedInitialization();
    }
  }

  private void startReconnectJobIfNotRunning() {
    if (reconnectPollingTimer != null) {
      return;
    }

    log.info("Created 'reconnect' coordinator polling");
    reconnectPollingTimer = entityContext.bgp().builder("zigbee-reconnect").delay(Duration.ofSeconds(1))
                                         .interval(Duration.ofSeconds(RECONNECT_RATE)).execute(() -> {
          ZigBeeNetworkState state = networkManager.getNetworkState();
          if (state == ZigBeeNetworkState.ONLINE || state == ZigBeeNetworkState.INITIALISING) {
            return;
          }

          log.info("[{}]: ZigBee dongle inactivity timer. Reinitializing ZigBee", entityID);

          networkManager.shutdown();

          synchronized (reconnectLock) {
            currentReconnectAttemptFinished = false;
          }

          // Initialize the network again
          initializeDongle();

          waitForReconnectAttemptToFinish();
        });
  }

  private void waitForReconnectAttemptToFinish() {
    synchronized (reconnectLock) {
      try {
        while (!currentReconnectAttemptFinished) {
          reconnectLock.wait();
        }
      } catch (InterruptedException e) {
        // thread may be killed if callback reports that we are connected again
      }
    }
  }

  private void notifyReconnectJobAboutFinishedInitialization() {
    synchronized (reconnectLock) {
      currentReconnectAttemptFinished = true;
      reconnectLock.notifyAll();
    }
  }

  /**
   * Gets a node given the long address
   *
   * @param nodeIeeeAddress the {@link IeeeAddress} of the device
   * @return the {@link ZigBeeNode} or null if the node is not found
   */
  public ZigBeeNode getNode(IeeeAddress nodeIeeeAddress) {
    if (networkManager == null) {
      return null;
    }
    return networkManager.getNode(nodeIeeeAddress);
  }

  /**
   * Gets the nodes in this network manager
   *
   * @return the set of {@link ZigBeeNode}s
   */
  public Set<ZigBeeNode> getNodes() {
    if (networkManager == null) {
      return Collections.emptySet();
    }
    return networkManager.getNodes();
  }

  /**
   * Removes a node from the network manager. This does not cause the network manager to tell the node to leave the network, but will only remove the node from the network manager lists. Thus, if the
   * node is still alive, it may be able to rejoin the network.
   * <p>
   *
   * @param nodeIeeeAddress the {@link IeeeAddress} of the node to remove
   */
  public void removeNode(IeeeAddress nodeIeeeAddress) {
    ZigBeeNode node = networkManager.getNode(nodeIeeeAddress);
    if (node == null) {
      return;
    }
    networkManager.removeNode(node);
    networkDataStore.removeNode(nodeIeeeAddress);
  }

  /**
   * Permit joining only for the specified node
   *
   * @param address  the 16 bit network address of the node to enable joining
   * @param duration the duration of the join
   */
  public boolean permitJoin(IeeeAddress address, int duration) {
    log.debug("[{}]: ZigBee join command {}", entityID, address);
    ZigBeeNode node = networkManager.getNode(address);
    if (node == null) {
      log.debug("[{}]: ZigBee join command - node not found {}", entityID, address);
      return false;
    }

    log.debug("[{}]: ZigBee join {} command to {}", entityID, address, node.getNetworkAddress());

    networkManager.permitJoin(new ZigBeeEndpointAddress(node.getNetworkAddress()), duration);
    return true;
  }

  /**
   * Sends a ZDO Leave Request to a device requesting that an end device leave the network.
   * <p>
   * This method will send the ZDO message to the device itself requesting it leave the network
   *
   * @param address      the network address to leave
   * @param forceRemoval true to remove the node from the network, even if the leave request fails
   */
  public void leave(IeeeAddress address, boolean forceRemoval) {
    // First we want to make sure that join is disabled
    networkManager.permitJoin(0);

    log.debug("[{}]: ZigBee leave command {}", entityID, address);
    ZigBeeNode node = networkManager.getNode(address);
    if (node == null) {
      log.debug("[{}]: ZigBee leave command - node not found {}", entityID, address);
      return;
    }

    log.debug("[{}]: ZigBee leave {} command to {}", entityID, address, node.getNetworkAddress());

    networkManager.leave(node.getNetworkAddress(), node.getIeeeAddress(), forceRemoval);
  }

  /**
   * Search for a node - will perform a discovery on the defined {@link IeeeAddress}
   *
   * @param nodeIeeeAddress {@link IeeeAddress} of the node to discover
   */
  public void rediscoverNode(IeeeAddress nodeIeeeAddress) {
    if (networkManager != null) {
      networkManager.rediscoverNode(nodeIeeeAddress);
    } else {
      log.warn("[{}]: Unable to discover node {}. networkManager is null", entityID, nodeIeeeAddress);
    }
  }

  /**
   * Serialize the network state
   *
   * @param nodeAddress the {@link IeeeAddress} of the node to serialize
   */
  public void serializeNetwork(IeeeAddress nodeAddress) {
    if (networkManager != null) {
      networkManager.serializeNetworkDataStore(nodeAddress);
    }
  }

  public void scanStart(int duration) {
    if (entity.getStatus() != Status.ONLINE) {
      log.debug("[{}]: ZigBee coordinator is offline - aborted scan", entityID);
      throw new IllegalStateException("zigbee.error.coordinator_offline");
    } else {
      networkManager.permitJoin(duration);
    }
  }

  @Override
  public void destroy() {
    this.dispose();
    // fires when removing coordinator.
    if (networkDataStore != null) {
      networkDataStore.delete();
    }

  }

  public void restartCoordinator() {
    this.desiredStatus = entity.isStart() ? restartIfRequire(entity) : (initialized ? Status.CLOSING : null);
    scheduleUpdateStatusIfRequire();
  }

  @Override
  public boolean entityUpdated(@NotNull ZigbeeCoordinatorEntity newEntity) {
    this.entity = this.entity == null ? newEntity : entity;
    this.discoveryService.setCoordinator(newEntity);
    this.desiredStatus = newEntity.isStart() ? restartIfRequire(newEntity) : (initialized ? Status.CLOSING : null);
    this.entity = newEntity;

    scheduleUpdateStatusIfRequire();
    return false;
  }

  private void scheduleUpdateStatusIfRequire() {
    if (isRequireStartOrStop()) {
      synchronized (updateCoordinatorSync) {
        if (isRequireStartOrStop()) {
          updatingStatus = desiredStatus;
          desiredStatus = null;
          entityContext.bgp().builder("zigbee-coordinator-entity-updated-" + entityID)
                       .delay(Duration.ofSeconds(1)).execute(() -> {
                         try {
                           if (updatingStatus == Status.CLOSING && initialized) {
                             this.dispose();
                           } else if (updatingStatus == Status.INITIALIZE || updatingStatus == Status.RESTARTING) {
                             if (initialized) {
                               this.dispose();
                             }
                             this.initialize();
                           }
                           entityContext.ui().updateItem(entity);
                           // fire recursively if state updated since last time
                           scheduleUpdateStatusIfRequire();
                         } finally {
                           this.updatingStatus = null;
                         }
                       });
        }
      }
    }
  }

  @Override
  public boolean testService() {
    return false;
  }

  /**
   * Validate entity and decide if we need start/stop coordinator
   */
  private @NotNull Status restartIfRequire(ZigbeeCoordinatorEntity newEntity) {
    String error = validateEntity(newEntity);
    if (error != null) {
      newEntity.setStatusError(error);
      return Status.CLOSING;
    }

    // do check reinitialize only if coordinator already started
    if (initialized) {
      TransportConfig transportConfig = new TransportConfig();
      boolean reinitialize = false;

      if (newEntity.getTrustCentreJoinMode() != -1 &&
          newEntity.getTrustCentreJoinMode() != entity.getTrustCentreJoinMode()) {
        TrustCentreJoinMode linkMode = TrustCentreJoinMode.values()[newEntity.getTrustCentreJoinMode()];
        transportConfig.addOption(TransportConfigOption.TRUST_CENTRE_JOIN_MODE, linkMode);
      }
      if (newEntity.getChannelId() != entity.getChannelId()) {
        networkManager.setZigBeeChannel(ZigBeeChannel.create(newEntity.getChannelId()));
      }
      if (!Objects.equals(newEntity.getLinkKey(), entity.getLinkKey()) ||
          !Objects.equals(newEntity.getNetworkKey(), entity.getNetworkKey()) ||
          newEntity.getPortBaud() != entity.getPortBaud() ||
          newEntity.getFlowControl() != entity.getFlowControl() ||
          newEntity.getTxPower() != entity.getTxPower() ||
          !newEntity.getNetworkId().equals(entity.getNetworkId()) ||
          !newEntity.getNetworkKey().equals(entity.getNetworkKey()) ||
          !Objects.equals(newEntity.getPort(), entity.getPort()) ||
          !Objects.equals(newEntity.getExtendedPanId(), entity.getExtendedPanId())) {
        reinitialize = true;
      }

      if (newEntity.getMeshUpdatePeriod() != entity.getMeshUpdatePeriod()) {
        ZigBeeDiscoveryExtension extension = (ZigBeeDiscoveryExtension) networkManager
            .getExtension(ZigBeeDiscoveryExtension.class);
        if (extension != null) {
          extension.setUpdateMeshPeriod(newEntity.getMeshUpdatePeriod());
        }
      }

      if (!Objects.equals(newEntity.getInstallCode(), entity.getInstallCode())) {
        addInstallCode(newEntity.getInstallCode());
      }

      // If we added any transport layer configuration, pass it down
      if (transportConfig.getOptions().size() != 0) {
        zigBeeTransport.updateTransportConfig(transportConfig);
      }

      if (reinitialize) {
        return Status.RESTARTING;
      }
    }

    return Status.INITIALIZE;
  }

  private String validateEntity(ZigbeeCoordinatorEntity newEntity) {
    if (StringUtils.isEmpty(newEntity.getPort())) {
      return Lang.getServerMessage("zigbee.error.no_port");
    } else if (CommonUtils.getSerialPort(entity.getPort()) == null) {
      return Lang.getServerMessage("zigbee.error.port_not_found", "NAME", entity.getPort());
    }
    return null;
  }

  public void dispose(ZigBeeDeviceService service) {
    // unregister service from alive tracking
    this.registeredDevices.remove(service);

    // removeNetworkNodeListener
    nodeListeners.remove(service);
    if (networkManager != null) {
      networkManager.removeNetworkNodeListener(service);
    }

    // removeAnnounceListener
    synchronized (announceListeners) {
      announceListeners.remove(service);
    }

    if (networkManager == null) {
      return;
    }
    networkManager.removeAnnounceListener(service);
  }

  private boolean isRequireStartOrStop() {
    if (updatingStatus != null) {
      return false;
    }
    if (desiredStatus == Status.INITIALIZE && !initialized) {
      return true;
    }
    return desiredStatus == Status.CLOSING && initialized || desiredStatus == Status.RESTARTING;
  }
}
