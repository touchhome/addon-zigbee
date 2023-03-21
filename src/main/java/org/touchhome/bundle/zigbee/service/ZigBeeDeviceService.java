package org.touchhome.bundle.zigbee.service;

import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeAnnounceListener;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.ZigBeeNetworkNodeListener;
import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.ZigBeeNode.ZigBeeNodeState;
import com.zsmartsystems.zigbee.ZigBeeNodeStatus;
import com.zsmartsystems.zigbee.ZigBeeProfileType;
import com.zsmartsystems.zigbee.ZigBeeStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.OptimisticLockingFailureException;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextBGP.ThreadContext;
import org.touchhome.bundle.api.model.Status;
import org.touchhome.bundle.api.service.EntityService;
import org.touchhome.bundle.api.service.EntityService.ServiceInstance;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeChannelConverterFactory;
import org.touchhome.bundle.zigbee.model.ZigBeeDeviceEntity;
import org.touchhome.bundle.zigbee.model.ZigBeeEndpointEntity;
import org.touchhome.bundle.zigbee.util.DeviceConfiguration;
import org.touchhome.bundle.zigbee.util.DeviceConfiguration.EndpointDefinition;
import org.touchhome.bundle.zigbee.util.DeviceConfigurations;
import org.touchhome.bundle.api.ui.field.ProgressBar;


@Getter
@Log4j2
public class ZigBeeDeviceService implements ZigBeeNetworkNodeListener, ZigBeeAnnounceListener, ServiceInstance<ZigBeeDeviceEntity> {

  private final Object entityUpdateSync = new Object();
  private final Object initializeSync = new Object();

  private final IeeeAddress nodeIeeeAddress;
  private final ZigBeeCoordinatorService coordinatorService;
  private final EntityContext entityContext;
  private final ZigBeeChannelConverterFactory zigBeeChannelConverterFactory;
  private final String entityID;
  private final AtomicInteger initializeZigBeeNodeRequests = new AtomicInteger(0);
  private ZigBeeDeviceEntity entity;
  private double progress = 0;
  private String progressMsg = "";

  private ThreadContext<Void> nodeInitThreadContext;
  private int discoveredEndpointsHash;
  private ProgressBar initProgressBar;
  private String deviceVariableGroup;

  public ZigBeeDeviceService(ZigBeeCoordinatorService coordinatorService, IeeeAddress nodeIeeeAddress, EntityContext entityContext, ZigBeeDeviceEntity entity) {
    this.entityID = entity.getEntityID();
    log.info("[{}]: Creating zigBee device {}", entityID, nodeIeeeAddress);
    this.entity = entity;
    this.entityContext = entityContext;

    this.coordinatorService = coordinatorService;
    this.zigBeeChannelConverterFactory =
        coordinatorService.getDiscoveryService().getChannelFactory();

    this.nodeIeeeAddress = nodeIeeeAddress;
    this.coordinatorService.addNetworkNodeListener(this);
    this.coordinatorService.addAnnounceListener(this);

    if (this.coordinatorService.getEntity().getStatus().isOnline()) {
      this.coordinatorOnline();
    } else {
      this.coordinatorOffline();
    }

    // register listener for reset timer if any updates from any endpoint
    entityContext.event().addEventListener(this.nodeIeeeAddress.toString(), state -> entity.setLastAnswerFromEndpoints(System.currentTimeMillis()));
  }

  public void tryInitializeZigBeeNode() {
    if (coordinatorService.getEntity().getStatus() == Status.ONLINE) {
      log.info("[{}]: Coordinator is ONLINE. Starting device initialisation. {}", entityID, nodeIeeeAddress);
      this.coordinatorService.rediscoverNode(nodeIeeeAddress);

      initializeZigBeeNode();
    } else {
      log.warn("[{}]: Unable to initialize device. Coordinator in '{}' state", entityID,
          coordinatorService.getEntity().getStatus());
    }
  }

  /**
   * synchronized to handle from multiple threads
   */
  public void initializeZigBeeNode() {
    synchronized (initializeSync) {
      if (this.nodeInitThreadContext == null || this.nodeInitThreadContext.isStopped()) {
        this.nodeInitThreadContext = entityContext.bgp().runWithProgress("zigbee-node-init-" + this.nodeIeeeAddress, false, progressBar -> {
          try {
            doNodeInitialisation(progressBar);
          } catch (Exception ex) {
            log.error("[{}]: Unknown error during node initialization", entityID, ex);
          } finally {
            while (this.initializeZigBeeNodeRequests.get() > 0) {
              this.initializeZigBeeNodeRequests.set(0);
              doNodeInitialisation(progressBar);
            }
          }
        });
      } else {
        log.info("[{}]: Node {} initialization already started", entityID, nodeIeeeAddress);
        this.initializeZigBeeNodeRequests.incrementAndGet();
      }
    }
  }

  private void doNodeInitialisation(ProgressBar progressBar) {
    try {
      this.initProgressBar = progressBar;
      ZigBeeNode node = this.coordinatorService.getNode(nodeIeeeAddress);
      if (node == null) {
        log.debug("[{}]: Node not found {}", entityID, nodeIeeeAddress);
        throw new RuntimeException("zigbee.error.offline_node_not_found");
      }

      if (this.entity.getStatus().isOnline() && node.isDiscovered() && node.getNodeState() == ZigBeeNodeState.ONLINE
          && this.discoveredEndpointsHash == this.calcEndpointHash(node.getEndpoints())) {
        updateEntityNode(node, false); // check for model if some internal state has been changed
        log.debug("Ignore initialize node with same requested node state");
        return;
      }

      this.discoveredEndpointsHash = this.calcEndpointHash(node.getEndpoints());
      log.info("[{}]: Initialization zigBee device {}", entityID, nodeIeeeAddress);
      this.entity.setStatus(Status.INITIALIZE, null);
      this.entity.setNodeInitializationStatus(Status.INITIALIZE);

      // Check if discovery is complete, and we know all the services the node supports
      if (!node.isDiscovered()) {
        log.warn("[{}]: Node has not finished discovery {}", entityID, nodeIeeeAddress);
        entity.setNodeInitializationStatus(Status.UNKNOWN);
        entity.setStatus(Status.NOT_READY);
        return;
      }

      log.info("[{}]: Start initialising ZigBee channels {}", entityID, nodeIeeeAddress);
      log.info("[{}]: Initial endpoints: {}", entityID, node.getEndpoints().stream().map(ZigBeeEndpoint::toString).collect(Collectors.joining("\n")));

      addToProgress(1, "Fetch node info");
      updateEntityNode(node, true);
      addToProgress(1, "Create missing endpoints");

      if (isNotEmpty(entity.getModelIdentifier())) {
        createMissingEndpointInZigBeeNetwork(node);
      }

      createDynamicEndpoints();
      // Progress = 30

      double initChannelDelta = 60D / entity.getEndpoints().size();
      initializeZigBeeChannelConverters(entity.getEndpoints(),
          message -> addToProgress(initChannelDelta, message),
          message -> addToProgress(0, message));

      entity.setLastAnswerFromEndpoints(System.currentTimeMillis());
      coordinatorService.getRegisteredDevices().add(this);

      // Update the binding table.
      // We're not doing anything with the information here, but we want it up to date, so it's
      // ready for use later.
      try {
        addToProgress(5, "init binding table");
        ZigBeeStatus zigBeeStatus = node.updateBindingTable().get();
        if (zigBeeStatus != ZigBeeStatus.SUCCESS) {
          log.debug("[{}]: Error getting binding table. {}. Actual status: <{}>", entityID, nodeIeeeAddress, zigBeeStatus);
        }
      } catch (Exception e) {
        log.error("[{}]: Exception getting binding table {}", entityID, nodeIeeeAddress, e);
      }

      entity.setNodeInitializationStatus(Status.DONE);
      log.info("[{}]: Done initialising ZigBee device {}", entityID, nodeIeeeAddress);

      // Save the network state
      coordinatorService.serializeNetwork(node.getIeeeAddress());
      entity.setStatusOnline();
    } catch (Exception ex) {
      entity.setStatusError(ex);
      this.entity.setNodeInitializationStatus(Status.UNKNOWN);
    } finally {
      addToProgress(100, ""); // reset progress
    }
  }

  private void updateEntityNode(ZigBeeNode node, boolean addToProgress) {
    try {
      entity.updateFromNode(node, entityContext, message -> {
        if (addToProgress) {addToProgress(1, message);}
      });
    } catch (OptimisticLockingFailureException ex) {
      // try update entity and call updateFromNode again
      progress -= 2;
      entity = entityContext.getEntity(entityID);
      entity.updateFromNode(node, entityContext, message -> {
        if (addToProgress) {addToProgress(1, message);}
      });
    }
  }

  private void addToProgress(double value, String message) {
    progress += value;
    progressMsg = message;
    if (progress >= 100) {
      progress = 0;
      progressMsg = "";
    }
    initProgressBar.progress(progress, progressMsg);
    entityContext.ui().updateItem(getEntity(), "initProgress", entity.getInitProgress());
  }

  private void createDynamicEndpoints() {
    // Dynamically create the zigBeeConverterEndpoints from the device
    // Process all the endpoints for this device and add all zigBeeConverterEndpoints as derived
    // from the supported clusters
    log.info("[{}]: Try out to find zigbee endpoints {}", entityID, nodeIeeeAddress);
    Collection<ZigBeeEndpoint> nodeEndpoints = this.coordinatorService.getNodeEndpoints(nodeIeeeAddress);
    // +1 because we check IAS cluster separately
    double delta = 25D / (nodeEndpoints.size() * (zigBeeChannelConverterFactory.getConverterCount() + 4));
    for (ZigBeeEndpoint endpoint : nodeEndpoints) {
      Collection<ZigBeeBaseChannelConverter> matchConverters = zigBeeChannelConverterFactory
          .findAllMatchConverters(endpoint, entityID, entityContext,
              message -> addToProgress(delta, message),
              message -> addToProgress(0, message));
      createEndpoints(endpoint.getEndpointId(), matchConverters);
    }

    log.info("[{}]: Dynamically created {} zigBeeConverterEndpoints {}", entityID, entity.getEndpoints().size(), nodeIeeeAddress);
  }

  private void createMissingEndpointInZigBeeNetwork(ZigBeeNode node) {
    DeviceConfigurations.getDeviceDefinition(entity.getModelIdentifier()).ifPresent(dd -> {
      log.info("[{}]: Found device '{}' definition", entityID, entity.getModelIdentifier());
      for (EndpointDefinition ed : dd.getEndpoints()) {
        ZigBeeEndpoint endpoint = node.getEndpoint(ed.getEndpoint());
        if (endpoint == null) {
          int profileId = ZigBeeProfileType.ZIGBEE_HOME_AUTOMATION.getKey();
          log.debug("[{}]: Creating statically defined device {} endpoint {} with profile {}", entityID, nodeIeeeAddress, ed.getEndpoint(), ZigBeeProfileType.getByValue(profileId));
          endpoint = new ZigBeeEndpoint(node, ed.getEndpoint());
          endpoint.setProfileId(profileId);
          node.addEndpoint(endpoint);
        }

        // add input clusters if found any
        List<Integer> inputClusters = processClusters(endpoint.getInputClusterIds(), ed.getInputClusters());
        if (!inputClusters.isEmpty()) {
          endpoint.setInputClusterIds(inputClusters);
          node.updateEndpoint(endpoint);
        }
      }
    });
  }

  public void createEndpoints(int endpointId, Collection<ZigBeeBaseChannelConverter> matchConverters) {
    String ieeeAddressStr = nodeIeeeAddress.toString();

    // create variable group before any variables
    this.deviceVariableGroup = entity.createOrUpdateVarGroup(entityContext);
    Optional<DeviceConfiguration> deviceDefinition = DeviceConfigurations.getDeviceDefinition(entity.getModelIdentifier());
    for (ZigBeeBaseChannelConverter cluster : matchConverters) {
      int clientCluster = cluster.getClientCluster();
      String clusterName = cluster.getName();
      ZigBeeEndpointEntity endpointEntity = entity.findEndpoint(endpointId, clusterName);

      Optional<EndpointDefinition> endpointDefinition = Optional.empty();
      if (deviceDefinition.isPresent()) {
        endpointDefinition = Optional.ofNullable(deviceDefinition.get().getEndpoint(endpointId, clusterName));
      }

      if (endpointEntity == null) {
        endpointEntity = new ZigBeeEndpointEntity()
            .setEntityID(ieeeAddressStr + "_" + endpointId + "_" + clusterName)
            .setIeeeAddress(ieeeAddressStr)
            .setClusterId(clientCluster)
            .setClusterName(clusterName);
        endpointEntity.setAddress(endpointId);
        endpointEntity.setOwner(entity);

        cluster.configureNewEndpointEntity(endpointEntity);
        endpointEntity = entityContext.save(endpointEntity);
      }

      if (!EntityService.entityToService.containsKey(endpointEntity.getEntityID())) {
        endpointEntity.setStatus(Status.WAITING, null);
        var endpointService = new ZigbeeEndpointService(cluster, this, endpointEntity, endpointDefinition);
        EntityService.entityToService.put(endpointEntity.getEntityID(), endpointService);
      }
    }
  }

  private void initializeZigBeeChannelConverters(Collection<ZigBeeEndpointEntity> endpoints,
      Consumer<String> runUnit, Consumer<String> progressMessage) {
    for (ZigBeeEndpointEntity endpoint : endpoints) {
      try {
        endpoint.setStatus(Status.INITIALIZE);
        ZigBeeBaseChannelConverter cluster = endpoint.getService().getCluster();
        runUnit.accept("ep[" + endpoint.getAddress() + "]" + endpoint.getClusterName() + ":init cluster");

        try {
          cluster.initialize(message -> progressMessage.accept("ep[" + endpoint.getAddress() + "]" +
              endpoint.getClusterName() + ":" + message));
        } catch (Exception ex) {
          log.warn("[{}]: Failed to initialize converter {}. {}", entityID, endpoint, TouchHomeUtils.getErrorMessage(ex));
          continue;
        }

        cluster.fireRefreshAttribute(progressMessage);

        endpoint.setStatusOnline();
      } catch (Exception ex) {
        endpoint.setStatusError(ex);
      }
    }
    log.debug("[{}]: Channel initialisation complete {}", entityID, nodeIeeeAddress);
  }

  public void dispose() {
    log.debug("[{}]: Handler dispose {}", entityID, nodeIeeeAddress);

    if (nodeIeeeAddress != null) {
      if (this.coordinatorService != null) {
        this.coordinatorService.dispose(this);
      }
    }

    for (ZigBeeEndpointEntity endpoint : entity.getEndpoints()) {
      endpoint.getService().getCluster().disposeConverter();
      endpoint.setStatus(Status.OFFLINE, "Dispose");
    }

    entity.setStatus(Status.OFFLINE, null);
  }

  @Override
  public void deviceStatusUpdate(ZigBeeNodeStatus deviceStatus, Integer networkAddress, IeeeAddress ieeeAddress) {
    // A node has joined - or come back online
    if (!nodeIeeeAddress.equals(ieeeAddress)) {
      return;
    }
    // Use this to update channel information - e.g. bulb state will likely change when the device
    // was powered off/on.
    for (ZigBeeEndpointEntity endpoint : getEntity().getEndpoints()) {
      endpoint.getService().getCluster().fireRefreshAttribute(null);
    }
  }

  @Override
  public void nodeAdded(ZigBeeNode node) {
    nodeUpdated(node);
  }

  @Override
  public void nodeUpdated(ZigBeeNode node) {
    // Make sure it's this node that's updated
    if (!node.getIeeeAddress().equals(nodeIeeeAddress)) {
      return;
    }
    log.debug("[{}]: Node {} has been updated. Fire initialization...", entityID, nodeIeeeAddress);
    initializeZigBeeNode();
  }

  @Override
  public void nodeRemoved(ZigBeeNode node) {
    if (!node.getIeeeAddress().equals(nodeIeeeAddress)) {
      return;
    }
    entity.setStatus(Status.OFFLINE, "zigbee.error.removed_by_dongle");
  }

  @Override
  public boolean entityUpdated(@NotNull ZigBeeDeviceEntity entity) {
    this.entity = entity;
    return false;
  }

  @Override
  public void destroy() {
    this.dispose();
  }

  @Override
  public boolean testService() {
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ZigBeeDeviceService service = (ZigBeeDeviceService) o;
    return entityID.equals(service.entityID);
  }

  @Override
  public int hashCode() {
    return entityID.hashCode();
  }

  public void checkOffline(boolean force) {
    if (this.entity.getStatus() == Status.ONLINE) {
      // set device to offline of all endpoint offline
      if (entity.getEndpoints().stream().allMatch(e -> e.getStatus() == Status.OFFLINE)) {
        log.warn("[{}]: Timeout has been reached for zigBeeDevice {}", entityID, nodeIeeeAddress);
        entity.setStatus(Status.OFFLINE, "zigbee.error.alive_timeout_reached");
      }
    }
    if (force || this.entity.getStatus().isOnline()) {
      // check if cluster need refresh attribute.
      // if 10+ polls and no answer from attribute - set endpoint to OFFLINE status
      for (ZigBeeEndpointEntity endpoint : entity.getEndpoints()) {
        endpoint.getService().pollRequest(force);
      }
    }
  }

  public void coordinatorOffline() {
    this.entity.setStatus(Status.OFFLINE);
    this.entity.setNodeInitializationStatus(Status.UNKNOWN);
  }

  public void coordinatorOnline() {
    log.info("[{}]: Fire discovery node: {}", entityID, nodeIeeeAddress);
    getCoordinatorService().rediscoverNode(nodeIeeeAddress);
    initializeZigBeeNode();
  }

  private List<Integer> processClusters(Collection<Integer> initialClusters, Set<Integer> newClusters) {
    if (newClusters == null || newClusters.size() == 0) {
      return Collections.emptyList();
    }

    Set<Integer> clusters = new HashSet<>();
    clusters.addAll(initialClusters);
    clusters.addAll(newClusters);
    if (clusters.size() == initialClusters.size()) {
      return Collections.emptyList();
    }
    return new ArrayList<>(clusters);
  }

  private int calcEndpointHash(Collection<ZigBeeEndpoint> endpoints) {
    StringBuilder hashBuilder = new StringBuilder(endpoints.size() + "_");
    List<ZigBeeEndpoint> zigBeeEndpoints = new ArrayList<>(endpoints);
    zigBeeEndpoints.sort(Comparator.comparingInt(ZigBeeEndpoint::getEndpointId));
    for (ZigBeeEndpoint endpoint : zigBeeEndpoints) {
      hashBuilder.append(endpoint.getInputClusterIds().stream().map(String::valueOf).collect(Collectors.joining("i")));
      hashBuilder.append(endpoint.getOutputClusterIds().stream().map(String::valueOf).collect(Collectors.joining("o")));
    }
    return hashBuilder.toString().hashCode();
  }
}
