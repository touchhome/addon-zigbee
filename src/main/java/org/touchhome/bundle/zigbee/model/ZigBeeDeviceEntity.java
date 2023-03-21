package org.touchhome.bundle.zigbee.model;

import static com.zsmartsystems.zigbee.zcl.clusters.ZclBasicCluster.ATTR_APPLICATIONVERSION;
import static com.zsmartsystems.zigbee.zcl.clusters.ZclBasicCluster.ATTR_DATECODE;
import static com.zsmartsystems.zigbee.zcl.clusters.ZclBasicCluster.ATTR_HWVERSION;
import static com.zsmartsystems.zigbee.zcl.clusters.ZclBasicCluster.ATTR_MANUFACTURERNAME;
import static com.zsmartsystems.zigbee.zcl.clusters.ZclBasicCluster.ATTR_MODELIDENTIFIER;
import static com.zsmartsystems.zigbee.zcl.clusters.ZclBasicCluster.ATTR_STACKVERSION;
import static com.zsmartsystems.zigbee.zcl.clusters.ZclBasicCluster.ATTR_ZCLVERSION;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.ZigBeeNode.ZigBeeNodeState;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclBasicCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOtaUpgradeCluster;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextSetting;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.entity.HasStatusAndMsg;
import org.touchhome.bundle.api.entity.zigbee.ZigBeeDeviceBaseEntity;
import org.touchhome.bundle.api.entity.zigbee.ZigBeeProperty;
import org.touchhome.bundle.api.exception.NotFoundException;
import org.touchhome.bundle.api.model.ActionResponseModel;
import org.touchhome.bundle.api.model.HasEntityLog;
import org.touchhome.bundle.api.model.Status;
import org.touchhome.bundle.api.service.EntityService;
import org.touchhome.bundle.api.state.State;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldOrder;
import org.touchhome.bundle.api.ui.field.UIFieldProgress;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.action.UIContextMenuAction;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputEntity;
import org.touchhome.bundle.api.ui.field.color.UIFieldColorRef;
import org.touchhome.bundle.api.ui.field.color.UIFieldColorStatusMatch;
import org.touchhome.bundle.api.ui.field.condition.UIFieldDisableCreateTab;
import org.touchhome.bundle.api.ui.field.inline.UIFieldInlineEntities;
import org.touchhome.bundle.api.ui.field.inline.UIFieldInlineEntityWidth;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelectValueOnEmpty;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelection;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.zigbee.SelectModelIdentifierDynamicLoader;
import org.touchhome.bundle.zigbee.service.ZigBeeCoordinatorService;
import org.touchhome.bundle.zigbee.service.ZigBeeDeviceService;
import org.touchhome.bundle.zigbee.setting.ZigBeeDiscoveryClusterTimeoutSetting;
import org.touchhome.bundle.zigbee.util.DeviceConfiguration;
import org.touchhome.bundle.zigbee.util.DeviceConfigurations;


@Log4j2
@Getter
@Setter
@Entity
public class ZigBeeDeviceEntity extends ZigBeeDeviceBaseEntity<ZigBeeDeviceEntity>
    implements HasJsonData, HasNodeDescriptor, HasEntityLog, HasStatusAndMsg<ZigBeeDeviceEntity>,
    EntityService<ZigBeeDeviceService, ZigBeeDeviceEntity> {

  public static final String PREFIX = "zb_";

  @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "owner", orphanRemoval = true)
  @UIField(order = 1000, hideInView = true)
  @UIFieldDisableCreateTab
  private Set<ZigBeeEndpointEntity> endpoints;

  @ManyToOne
  @JsonIgnore
  @JoinColumn(name = "parent_id")
  private ZigbeeCoordinatorEntity parent;

  @Override
  @UIFieldOrder(10)
  public String getPlace() {
    return super.getPlace();
  }

  @UIField(order = 9999, hideInEdit = true)
  @UIFieldInlineEntities(bg = "#27FF000D")
  @SuppressWarnings("unused")
  public List<ZigBeeEndpointClusterEntity> getEndpointClusters() {
    if (endpoints == null) {
      return Collections.emptyList();
    }
    return endpoints.stream()
                    .sorted(Comparator.comparingInt(o -> o.getAddress() * 1000 + o.getClusterId()))
                    .map(ZigBeeEndpointClusterEntity::new)
                    .collect(Collectors.toList());
  }

  @UIField(order = 1, hideInEdit = true, hideOnEmpty = true)
  public UIFieldProgress.Progress getInitProgress() {
    return optService().map(service -> {
      double progress = service.getProgress();
      return progress == 0 ? null : UIFieldProgress.Progress.of((int) progress, 100, service.getProgressMsg(), true);
    }).orElse(null);
  }

  @UIField(order = 11, hideInEdit = true)
  @UIFieldColorStatusMatch
  public Status getNodeInitializationStatus() {
    return EntityContextSetting.getStatus(this, "node_init", Status.UNKNOWN);
  }

  public void setNodeInitializationStatus(Status status) {
    EntityContextSetting.setStatus(this, "node_init", "NodeInitializationStatus", status);
    getEntityContext().ui().updateItem(this, "nodeInitializationStatus", getNodeInitializationStatus());
  }

  @UIField(hideInEdit = true, order = 1)
  @UIFieldGroup(value = "General", order = 1, borderColor = "#317175")
  @Override
  public String getIeeeAddress() {
    return super.getIeeeAddress();
  }

  @Override
  public String getName() {
    String name = super.getName();
    String modelIdentifier = StringUtils.trimToNull(getModelIdentifier());
    if (StringUtils.isEmpty(name) && modelIdentifier != null) {
      DeviceConfiguration deviceConfiguration = DeviceConfigurations.getDeviceDefinition(modelIdentifier).orElse(null);
      return "zigbee.device." + (deviceConfiguration == null ? modelIdentifier : deviceConfiguration.getFirstModel());
    }
    return getIeeeAddress();
  }

  @UIField(order = 3, hideInEdit = true, type = UIFieldType.Duration)
  @UIFieldGroup("General")
  public long getLastAnswerFromEndpoints() {
    return EntityContextSetting.getMemValue(this, "lafe", 0L);
  }

  public void setLastAnswerFromEndpoints(long currentTimeMillis) {
    EntityContextSetting.setMemValue(this, "lafe", "", currentTimeMillis);
    getEntityContext().ui().updateItem(this, "lastAnswerFromEndpoints", getLastAnswerFromEndpoints());
  }

  @UIField(hideInEdit = true, order = 2, hideOnEmpty = true)
  @UIFieldGroup("Node")
  public String getManufacturer() {
    return getJsonData("man");
  }

  public boolean setManufacturer(String manufacturer) {
    if (!Objects.equals(trimToEmpty(manufacturer), getManufacturer())) {
      setJsonData("man", manufacturer);
      return true;
    }
    return false;
  }

  @UIField(order = 3)
  @UIFieldSelection(value = SelectModelIdentifierDynamicLoader.class, allowInputRawText = true)
  @UIFieldSelectValueOnEmpty(label = "zigbee.action.select_model_identifier")
  @UIFieldGroup("Node")
  public String getModelIdentifier() {
    return getJsonData("m_id");
  }

  public boolean setModelIdentifier(String modelIdentifier) {
    if (!Objects.equals(trimToEmpty(modelIdentifier), getModelIdentifier())) {
      setJsonData("m_id", modelIdentifier);
      return true;
    }
    return false;
  }

  @UIField(order = 4, hideInEdit = true)
  @UIFieldGroup("Node")
  @SuppressWarnings("unused")
  public String getImageIdentifier() {
    if (!getJsonData().has("i_id") && isNotEmpty(getModelIdentifier())) {
      DeviceConfiguration deviceConfiguration = DeviceConfigurations.getDeviceDefinition(getModelIdentifier()).orElse(null);
      if (deviceConfiguration != null) {
        return deviceConfiguration.getImage();
      }
    }
    return getJsonData().optString("i_id");
  }

  @SuppressWarnings("unused")
  public void setImageIdentifier(String imageIdentifier) {
    setJsonData("i_id", imageIdentifier);
  }

  @UIField(hideInEdit = true, order = 1, hideOnEmpty = true)
  @UIFieldGroup(value = "Version", order = 100, borderColor = "#86AD2A")
  public Integer getHwVersion() {
    return getJsonDataNumber("hw_v").map(Number::intValue).orElse(null);
  }

  public boolean setHwVersion(Integer hwVersion) {
    if (!Objects.equals(hwVersion, getHwVersion())) {
      setJsonData("hw_v", hwVersion);
      return true;
    }
    return false;
  }

  @UIField(hideInEdit = true, order = 2, hideOnEmpty = true)
  @UIFieldGroup("Version")
  public Integer getAppVersion() {
    return getJsonDataNumber("app_v").map(Number::intValue).orElse(null);
  }

  public boolean setAppVersion(Integer appVersion) {
    if (!Objects.equals(appVersion, getAppVersion())) {
      setJsonData("app_v", appVersion);
      return true;
    }
    return false;
  }

  @UIField(hideInEdit = true, order = 3, hideOnEmpty = true)
  @UIFieldGroup("Version")
  public Integer getStackVersion() {
    return getJsonDataNumber("stack_v").map(Number::intValue).orElse(null);
  }

  public boolean setStackVersion(Integer stackVersion) {
    if (!Objects.equals(stackVersion, getStackVersion())) {
      setJsonData("stack_v", stackVersion);
      return true;
    }
    return false;
  }

  @UIField(hideInEdit = true, order = 4, hideOnEmpty = true)
  @UIFieldGroup("Version")
  public Integer getZclVersion() {
    return getJsonDataNumber("zcl_v").map(Number::intValue).orElse(null);
  }

  public boolean setZclVersion(Integer zclVersion) {
    if (!Objects.equals(zclVersion, getZclVersion())) {
      setJsonData("zcl_v", zclVersion);
      return true;
    }
    return false;
  }

  @UIField(hideInEdit = true, order = 5, hideOnEmpty = true)
  @UIFieldGroup("Version")
  public String getDateCode() {
    return getJsonData("d_c");
  }

  public boolean setDateCode(String dateCode) {
    if (!Objects.equals(trimToEmpty(dateCode), getDateCode())) {
      setJsonData("d_c", dateCode);
      return true;
    }
    return false;
  }

  @UIField(order = 7, hideInEdit = true, hideOnEmpty = true)
  @UIFieldGroup("General")
  @SuppressWarnings("unused")
  public String getFetchInfoStatusMessage() {
    return EntityContextSetting.getMessage(this, "fetch_info");
  }

  @UIField(order = 8, hideInEdit = true)
  @UIFieldGroup("General")
  @UIFieldColorStatusMatch
  @SuppressWarnings("unused")
  public Status getFetchBasicCluster() {
    return EntityContextSetting.getStatus(this, "fetch_basic", Status.UNKNOWN);
  }

  @UIContextMenuAction(value = "zigbee.action.poll_values", icon = "fas fa-download", iconColor = "#A939B7")
  public ActionResponseModel pollValues() {
    getService().checkOffline(true);
    return ActionResponseModel.success();
  }

  @UIContextMenuAction(value = "zigbee.action.permit_join", icon = "fas fa-arrows-to-eye", iconColor = "#1D8EB3")
  public ActionResponseModel permitJoin() {
    if (parent.getStatus() != Status.ONLINE) {
      throw new IllegalStateException("zigbee.error.coordinator_offline");
    }
    /*TODO: ZigBeeCoordinatorService zigBeeCoordinatorService = parent.getService();
    boolean join = zigBeeCoordinatorService.permitJoin(toIeeeAddress(), parent.getDiscoveryDuration());
    return join ? ActionResponseModel.success() : ActionResponseModel.showError("ACTION.RESPONSE.ERROR");*/
    return null;
  }

  @Override
  public String getDefaultName() {
    return getModelIdentifier() == null ? "Unknown zigbee device" : getModelIdentifier();
  }

  @Override
  public String refreshName() {
    return null; // uses when persist this entity
  }

  @Override
  public String toString() {
    return "ZigBee device '" + getTitle() + "'. [IeeeAddress='" + getIeeeAddress() + ", ModelIdentifier=" + getModelIdentifier() + "]";
  }

  @Override
  public void afterDelete(EntityContext entityContext) {
    /*TODO:parent.optService().ifPresent(service -> {
      try {
        service.removeNode(toIeeeAddress());
        service.leave(toIeeeAddress(), true);
      } catch (Exception ex) {
        log.error("[{}]: Something went wrong during detaching removed ZigBeeDeviceEntity from coordinator: {}",
            getEntityID(), parent.getTitle());
      }
    });*/
  }

  @Override
  public String getEntityPrefix() {
    return PREFIX;
  }

  public @NotNull ZigBeeEndpointEntity getEndpointRequired(@NotNull Integer endpointId, @NotNull String clusterName) {
    ZigBeeEndpointEntity endpoint = findEndpoint(endpointId, clusterName);
    if (endpoint == null) {
      throw new NotFoundException("Unable to find endpoint: EndpointId: " + endpointId + " with cluster: " + clusterName);
    }
    return endpoint;
  }

  public @Nullable ZigBeeEndpointEntity findEndpoint(@NotNull Integer endpointId, @NotNull String clusterName) {
    if (endpoints == null) {
      return null;
    }
    for (ZigBeeEndpointEntity endpoint : endpoints) {
      if (endpoint.getAddress() == endpointId && endpoint.getClusterName().equals(clusterName)) {
        return endpoint;
      }
    }
    return null;
  }

  public List<ZigBeeEndpointEntity> filterEndpoints(int clusterId) {
    if (endpoints == null) {
      return Collections.emptyList();
    }
    return endpoints.stream().filter(e -> e.getClusterId() == clusterId).collect(Collectors.toList());
  }

  public void updateFromNode(ZigBeeNode node, EntityContext entityContext, Consumer<String> progressMessage) {
    try {
      log.info("[{}]: Starting fetch info from ZigBeeNode: <{}>", getEntityID(), node.getIeeeAddress().toString());
      setFetchInfoStatus(Status.RUNNING, null);

      boolean updated = updateFromNodeDescriptor(node);
      progressMessage.accept("read ota cluster");
      updated |= updateFromOtaCluster(node);
      progressMessage.accept("read basic cluster");
      updated |= updateFromBasicCluster(node, entityContext);

      log.info("[{}]: Finished fetch info from ZigBeeNode: <{}>", getEntityID(), node.getIeeeAddress());
      setFetchInfoStatus(Status.DONE, null);

      if (updated) {
        entityContext.save(this);
      }
    } catch (Exception ex) {
      setFetchInfoStatus(Status.ERROR, TouchHomeUtils.getErrorMessage(ex));
      throw ex;
    }
  }

  private boolean updateFromBasicCluster(ZigBeeNode node, EntityContext entityContext) {
    boolean updated = false;
    ZclBasicCluster basicCluster = (ZclBasicCluster) node.getEndpoints().stream().map(
        ep -> ep.getInputCluster(ZclBasicCluster.CLUSTER_ID)).filter(Objects::nonNull).findFirst().orElse(null);

    if (basicCluster == null) {
      log.warn("[{}]: Node {} doesn't support basic cluster", getEntityID(), node.getIeeeAddress());
      return false;
    }

    log.debug("[{}]: ZigBee node {} property discovery using basic cluster on endpoint {}",
        getEntityID(), node.getIeeeAddress(), basicCluster.getZigBeeAddress());

    // Attempt to read all properties with a single command.
    // If successful, this updates the cache with the property values.
    try {
      // Try to get the supported attributes, so we can reduce the number of attribute read requests
      int timeout = entityContext.setting().getValue(ZigBeeDiscoveryClusterTimeoutSetting.class);
      basicCluster.discoverAttributes(false).get(timeout, TimeUnit.SECONDS);
      List<Integer> attributes =
          new ArrayList<>(
              Arrays.asList(
                  ATTR_MANUFACTURERNAME,
                  ATTR_MODELIDENTIFIER,
                  ATTR_HWVERSION,
                  ATTR_APPLICATIONVERSION,
                  ATTR_STACKVERSION,
                  ATTR_ZCLVERSION,
                  ATTR_DATECODE));

      // filter attributes that already fetched
      attributes.removeIf(attributeId -> basicCluster.getAttribute(attributeId).isLastValueCurrent(Long.MAX_VALUE));

      if (!attributes.isEmpty()) {
        basicCluster.readAttributes(attributes).get(timeout, TimeUnit.SECONDS);
      }
      EntityContextSetting.setStatus(this, "fetch_basic", "FetchBasicCluster", Status.DONE);
    } catch (Exception ex) {
      EntityContextSetting.setStatus(this, "fetch_basic", "FetchBasicCluster", Status.ERROR);
      log.warn("[{}]: There was an error when trying to read all properties. {}. Msg: {}",
          getEntityID(), node.getIeeeAddress(), TouchHomeUtils.getErrorMessage(ex));
    }

    if (this.setModelIdentifier((String) basicCluster.getAttribute(ATTR_MODELIDENTIFIER).readValue(Long.MAX_VALUE)) |
        this.setManufacturer((String) basicCluster.getAttribute(ATTR_MANUFACTURERNAME).readValue(Long.MAX_VALUE)) |
        this.setHwVersion((Integer) basicCluster.getAttribute(ATTR_HWVERSION).readValue(Long.MAX_VALUE)) |
        this.setAppVersion((Integer) basicCluster.getAttribute(ATTR_APPLICATIONVERSION).readValue(Long.MAX_VALUE)) |
        this.setStackVersion((Integer) basicCluster.getAttribute(ATTR_STACKVERSION).readValue(Long.MAX_VALUE)) |
        this.setZclVersion((Integer) basicCluster.getAttribute(ATTR_ZCLVERSION).readValue(Long.MAX_VALUE)) |
        this.setDateCode((String) basicCluster.getAttribute(ATTR_DATECODE).readValue(Long.MAX_VALUE))) {
      updated = true;
    }

    return updated;
  }

  private boolean updateFromOtaCluster(ZigBeeNode node) {
    ZclOtaUpgradeCluster otaCluster = (ZclOtaUpgradeCluster) node.getEndpoints().stream().map(
        ep -> ep.getOutputCluster(ZclOtaUpgradeCluster.CLUSTER_ID)).filter(Objects::nonNull).findFirst().orElse(null);

    if (otaCluster != null) {
      log.debug("[{}]: ZigBee node {} property discovery using OTA cluster on endpoint {}", getEntityID(), node.getIeeeAddress(), otaCluster.getZigBeeAddress());

      ZclAttribute attribute = otaCluster.getAttribute(ZclOtaUpgradeCluster.ATTR_CURRENTFILEVERSION);
      Object fileVersion = attribute.readValue(Long.MAX_VALUE);
      if (fileVersion != null) {
        String firmwareVersion = format("0x%08X", fileVersion);
        if (!Objects.equals(getFirmwareVersion(), firmwareVersion)) {
          this.setFirmwareVersion(firmwareVersion);
          return true;
        }
      } else {
        log.debug("[{}]: Could not get OTA firmware version from device {}", getEntityID(), node.getIeeeAddress());
      }
    } else {
      log.debug("[{}]: Node doesn't support OTA cluster {}", getEntityID(), node.getIeeeAddress());
    }
    return false;
  }

  private IeeeAddress toIeeeAddress() {
    return new IeeeAddress(getIeeeAddress());
  }

  @Override
  public @NotNull Class<ZigBeeDeviceService> getEntityServiceItemClass() {
    return ZigBeeDeviceService.class;
  }

  @Override
  public ZigBeeDeviceService createService(@NotNull EntityContext entityContext) {
    /*TODO: ZigBeeCoordinatorService coordinatorService = parent.getOrCreateService(entityContext).orElseThrow(
        () -> new RuntimeException("Unable to create zigbee discovery service"));
    return new ZigBeeDeviceService(coordinatorService, toIeeeAddress(), entityContext, this);*/
    return null;
  }

  @Override
  public void logBuilder(EntityLogBuilder entityLogBuilder) {
    entityLogBuilder.addTopicFilterByEntityID("org.touchhome.bundle.zigbee");
    entityLogBuilder.addTopic("com.zsmartsystems.zigbee", "ieeeAddress");
  }

  @Override
  public void getAllRelatedEntities(Set<BaseEntity> set) {
    set.addAll(getEndpoints());
  }

  @JsonIgnore
  public String getGroupDescription() {
    String name = getName();
    if (!name.equals(getIeeeAddress())) {
      return format("${%s} [%s]", name, getIeeeAddress());
    }
    return name;
  }

  public String createOrUpdateVarGroup(EntityContext entityContext) {
    String groupId = "zigbee-" + getIeeeAddress();
    String groupName = format("${%s} [${%s}]", getName(), StringUtils.defaultIfEmpty(getPlace(), "PLACE_NOT_SET"));
    DeviceConfiguration deviceDefinition = DeviceConfigurations.getDeviceDefinition(getModelIdentifier()).orElse(null);
    String icon = "fas fa-server";
    String iconColor = UI.Color.random();
    if (deviceDefinition != null) {
      icon = StringUtils.defaultString(deviceDefinition.getIcon(), icon);
      iconColor = StringUtils.defaultString(deviceDefinition.getIconColor(), iconColor);
    }
    entityContext.var().createGroup("zigbee", groupId, groupName, true, icon, iconColor, getGroupDescription());

    // rename variables if already exists
    for (ZigBeeEndpointEntity endpointEntity : endpoints) {
      entityContext.var().renameVariable(endpointEntity.getVariableId(), endpointEntity.getVariableName(), endpointEntity.getVariableDescription());
    }

    return groupId;
  }

  public void updateValue(ZigBeeEndpointEntity entity, State state) {
    if (getStatus() == Status.OFFLINE) {
      setStatus(Status.ONLINE);
    }
    setNodeStatus(ZigBeeNodeState.ONLINE);
  }

  @Override
  public @Nullable String getIcon() {
    throw new NotImplementedException();
  }

  @Override
  public @Nullable String getIconColor() {
    throw new NotImplementedException();
  }

  @Override
  public @Nullable String getDescription() {
    throw new NotImplementedException();
  }

  @Override
  public @NotNull String getDeviceFullName() {
    throw new NotImplementedException();
  }

  @Override
  public @NotNull Map<String, ZigBeeProperty> getProperties() {
    throw new NotImplementedException();
  }

  @Getter
  @NoArgsConstructor
  public static class ZigBeeEndpointClusterEntity {

    private String entityID;

    @UIField(order = 1)
    @UIFieldInlineEntityWidth(12)
    private String cls;

    @UIField(order = 2)
    @UIFieldColorRef("nc")
    private String name;

    @UIField(order = 3, type = UIFieldType.Duration)
    @UIFieldInlineEntityWidth(15)
    private long updated;

    @UIField(order = 4)
    @UIFieldInlineEntityWidth(15)
    private String value;

    @UIField(order = 5)
    @UIFieldColorStatusMatch
    @UIFieldInlineEntityWidth(15)
    private Status status;

    @UIField(order = 6, hideInEdit = true, label = "none")
    @UIFieldInlineEntityWidth(8)
    private Collection<UIInputEntity> actions;

    private String nc;

    @JsonIgnore private int order;

    public ZigBeeEndpointClusterEntity(ZigBeeEndpointEntity endpoint) {
      this.entityID = endpoint.getEntityID();
      this.cls = endpoint.getClusterId() + "/" + endpoint.getAddress();
      this.name = endpoint.getTitle();
      this.status = endpoint.getStatus();
      this.updated = endpoint.getLastAnswerFromEndpoint();
      this.value = endpoint.getValue();
      this.order = endpoint.getAddress();
      endpoint.optService().ifPresent(service ->
          this.nc = service.getCluster().getBindStatus() == Status.DONE ? "#BB8CE1" : null);
      UIInputBuilder uiInputBuilder = endpoint.getEntityContext().ui().inputBuilder();
      endpoint.assembleActions(uiInputBuilder);
      actions = uiInputBuilder.buildAll();
    }
  }
}
