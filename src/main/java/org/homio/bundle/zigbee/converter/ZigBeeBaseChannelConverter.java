package org.homio.bundle.zigbee.converter;

import static java.lang.String.format;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeCommand;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.ZigBeeProfileType;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zdo.command.BindResponse;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.homio.bundle.zigbee.converter.impl.HasClusterDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.EntityContextVar.VariableType;
import org.homio.bundle.api.model.Status;
import org.homio.bundle.api.state.State;
import org.homio.bundle.api.ui.field.ProgressBar;
import org.homio.bundle.api.ui.field.action.v1.UIInputBuilder;
import org.homio.bundle.api.util.CommonUtils;
import org.homio.bundle.zigbee.converter.config.ZclDoorLockConfig;
import org.homio.bundle.zigbee.converter.config.ZclFanControlConfig;
import org.homio.bundle.zigbee.converter.config.ZclLevelControlConfig;
import org.homio.bundle.zigbee.converter.config.ZclOnOffSwitchConfig;
import org.homio.bundle.zigbee.converter.config.ZclReportingConfig;
import org.homio.bundle.zigbee.converter.impl.ZigBeeConverter;
import org.homio.bundle.zigbee.model.ZigBeeEndpointEntity;
import org.homio.bundle.zigbee.service.ZigBeeDeviceService;
import org.homio.bundle.zigbee.service.ZigbeeEndpointService;
import org.homio.bundle.zigbee.setting.ZigBeeDiscoveryClusterTimeoutSetting;


public abstract class ZigBeeBaseChannelConverter implements HasClusterDefinition {

  public static final int REPORTING_PERIOD_DEFAULT_MAX = 7200;
  public static final int POLLING_PERIOD_HIGH = 60;
  protected final Logger log = LogManager.getLogger(getClass());
  protected int pollingPeriod = 7200;
  @Getter protected int minimalReportingPeriod = Integer.MAX_VALUE;
  protected ZigBeeEndpoint endpoint;
  // device entityID
  protected String entityID;
  @Getter @Nullable protected ZclReportingConfig configReporting;
  @Getter @Nullable protected ZclLevelControlConfig configLevelControl;
  @Getter @Nullable protected ZclOnOffSwitchConfig configOnOff;
  @Getter @Nullable protected ZclFanControlConfig configFanControl;
  @Getter @Nullable protected ZclDoorLockConfig configDoorLock;
  @Getter protected boolean supportConfigColorControl;
  // binding result
  @Getter @NotNull protected Status bindStatus = Status.UNKNOWN;
  @Setter @Nullable private ZigBeeConverter annotation;
  @Getter private ZigbeeEndpointService endpointService;
  @Getter @Nullable private String bindStatusMsg;

  public Integer getPollingPeriod() {
    return configReporting == null ? pollingPeriod : configReporting.getPollingPeriod();
  }

  public void initialize(ZigbeeEndpointService endpointService, ZigBeeEndpoint endpoint) {
    this.endpointService = endpointService;
    this.endpoint = endpoint;
    this.entityID = endpointService.getZigBeeDeviceService().getEntityID();
  }

  public int getDiscoveryTimeout(EntityContext entityContext) {
    return entityContext.setting().getValue(ZigBeeDiscoveryClusterTimeoutSetting.class);
  }

  public ZigBeeEndpointEntity getEntity() {
    return endpointService.getEntity();
  }

  protected <T> T getInputCluster(int clusterId) {
    return (T) endpoint.getInputCluster(clusterId);
  }

  protected boolean hasInputCluster(int clusterId) {
    return endpoint.getInputCluster(clusterId) != null;
  }

  protected <T> T getOutputCluster(int clusterId) {
    return (T) endpoint.getOutputCluster(clusterId);
  }

  /**
   * Initialize the converter. This is called by the {@link ZigBeeDeviceService} when the channel is created. The converter should initialize any internal states, open any clusters, add reporting and
   * binding that it needs to operate.
   *
   * <p>
   */
  public abstract void initialize(Consumer<String> progressMessage);

  /**
   * Closes the converter and releases any resources.
   */
  public void disposeConverter() {
    // Overridable if the converter has cleanup to perform
  }

  public Future<CommandResult> handleCommand(final ZigBeeCommand command) {
    // Overridable if a channel can be commanded
    return null;
  }

  /**
   * Execute refresh method. This method is called every time a binding item is refreshed and the corresponding node should be sent a message.
   *
   * <p>This is run in a separate thread by the Thing Handler so the converter doesn't need to worry
   * about returning quickly.
   */
  protected void handleRefresh(@Nullable Consumer<String> progressMessage) {
    // Overridable if a channel can be refreshed
  }

  public final void fireRefreshAttribute(@Nullable Consumer<String> progressMessage) {
    this.handleRefresh(progressMessage);
  }

  /**
   * Check if this converter supports features from the {@link ZigBeeEndpoint} If the converter doesn't support any features, it returns null.
   */
  public abstract boolean acceptEndpoint(ZigBeeEndpoint endpoint, String entityID, EntityContext entityContext, Consumer<String> progressMessage);

  public boolean acceptEndpoint(ZigBeeEndpoint endpoint, String entityID, EntityContext entityContext,
      int clusterID, int attributeId, boolean discoverAttribute, boolean readAttribute, Consumer<String> progressMessage) {
    ZclCluster cluster = endpoint.getInputCluster(clusterID);
    if (cluster == null) {
      log.trace("[{}]: Cluster '{}' not found {}", entityID, clusterID, endpoint);
      return false;
    }

    if (discoverAttribute) {
      try {
        progressMessage.accept("discovery attributes");
        if (!cluster.discoverAttributes(false).get(getDiscoveryTimeout(entityContext), TimeUnit.SECONDS)
            && !cluster.isAttributeSupported(attributeId)) {
          log.debug("[{}]: Error discover attribute {}. {}", entityID, attributeId, endpoint);
          return false;
        }
      } catch (Exception e) {
        log.debug("[{}]: Exception discovering attributes in {}", entityID, endpoint, e);
        return false;
      }
    }

    if (readAttribute) {
      ZclAttribute zclAttribute = cluster.getAttribute(attributeId);
      progressMessage.accept("read attr");
      Object value = zclAttribute.readValue(Long.MAX_VALUE);
      if (value == null) {
        log.debug(
            "[{}]: Exception reading attribute {} in cluster, {}", entityID, attributeId, endpoint);
        return false;
      }
    }
    return true;
  }

  protected void handleReportingResponse(CommandResult reportResponse) {
    handleReportingResponse(reportResponse, REPORTING_PERIOD_DEFAULT_MAX, REPORTING_PERIOD_DEFAULT_MAX);
  }

  /**
   * Sets the {@code pollingPeriod} and {@code maxReportingPeriod} depending on the success or failure of the given reporting response.
   *
   * @param reportResponse                    a {@link CommandResult} representing the response to a reporting request
   * @param reportingFailedPollingInterval    the polling interval to be used in case configuring reporting has failed
   * @param reportingSuccessMaxReportInterval the maximum reporting interval in case reporting is successfully configured
   */
  protected void handleReportingResponse(CommandResult reportResponse, int reportingFailedPollingInterval, int reportingSuccessMaxReportInterval) {
    if (!reportResponse.isSuccess()) {
      // we want the minimum of all pollingPeriods
      pollingPeriod = Math.min(pollingPeriod, reportingFailedPollingInterval);
    } else {
      // we want to know the minimum of all maximum reporting periods to be used as a timeout value
      minimalReportingPeriod = Math.min(minimalReportingPeriod, reportingSuccessMaxReportInterval);
    }
  }

  /**
   * Creates a binding from the remote cluster to the local {@link ZigBeeProfileType#ZIGBEE_HOME_AUTOMATION} endpoint
   *
   * @param cluster the remote {@link ZclCluster} to bind to
   * @return the future {@link CommandResult}
   */
  protected CommandResult bind(ZclCluster cluster) throws Exception {
    this.bindStatus = Status.ERROR;
    try {
      CommandResult commandResult = cluster.bind(endpointService.getLocalIpAddress(), endpointService.getLocalEndpointId()).get();
      this.bindStatus = commandResult.isSuccess() ? Status.DONE : Status.OFFLINE;
      if (this.bindStatus != Status.DONE) {
        if (commandResult.getResponse() == null) {
          this.bindStatusMsg = format("code: '%s'. '%s'", Integer.toHexString(commandResult.getStatusCode()).toUpperCase(), commandResult);
        } else {
          this.bindStatusMsg = format("code: '%s'. status: '%s'", Integer.toHexString(commandResult.getStatusCode()).toUpperCase(),
              ((BindResponse) commandResult.getResponse()).getStatus().name());
        }
      }
      return commandResult;
    } catch (Exception ex) {
      this.bindStatusMsg = format("code: '-'. msg: '%s'", CommonUtils.getErrorMessage(ex));
      throw ex;
    }
  }

  protected void updateChannelState(State state) {
    log.debug("[{}]: Channel <{}> updated to <{}> for {}", entityID, getClass().getSimpleName(), state, endpoint);
    endpointService.updateValue(state);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ZigBeeBaseChannelConverter that = (ZigBeeBaseChannelConverter) o;
    return this.endpointService.getEntity().equals(that.getEndpointService().getEntity());
  }

  @Override
  public int hashCode() {
    return this.endpointService.getEntity().hashCode();
  }

  // Configure reporting
  protected void updateDeviceReporting(@NotNull ZclCluster serverCluster, @Nullable Integer attributeId, boolean setChange) {
    if (configReporting == null) {
      throw new IllegalArgumentException("configReporting is null");
    }
    if (attributeId == null) {
      throw new IllegalStateException("Cluster with null attributeId must override updateDeviceReporting(...) method");
    }
    try {
      CommandResult reportingResponse = serverCluster.setReporting(attributeId,
          configReporting.getReportingTimeMin(),
          configReporting.getReportingTimeMax(),
          setChange ? configReporting.getReportingChange() : null).get();
      handleReportingResponse(reportingResponse, configReporting.getPollingPeriod(), configReporting.getReportingTimeMax());
    } catch (Exception e) {
      log.debug("[{}]: Exception setting reporting", entityID, e);
    }
  }

  public void updateConfiguration() {
  }

  public Integer getMinPollingInterval() {
    return Math.min(this.pollingPeriod, this.minimalReportingPeriod);
  }

  public <T> T readAttribute(ZclCluster zclCluster, int attributeID, T defaultValue) {
    ZclAttribute divisorAttribute = zclCluster.getAttribute(attributeID);
    Object value = divisorAttribute.readValue(Long.MAX_VALUE);
    return value == null ? defaultValue : (T) value;
  }

  public void configureNewEndpointEntity(ZigBeeEndpointEntity endpointEntity) {
    endpointEntity.setPollingPeriod(getPollingPeriod());
  }

  public void assembleActions(UIInputBuilder uiInputBuilder) {
  }

  public boolean tryBind() throws Exception {

    return false;
  }

  @Override
  public VariableType getVariableType() {
    assert annotation != null;
    return annotation.linkType();
  }

  @Override
  public int getClientCluster() {
    assert annotation != null;
    return annotation.clientCluster();
  }

  @Override
  public String getName() {
    assert annotation != null;
    return annotation.name();
  }

  public int[] getAdditionalClientClusters() {
    if (annotation != null) {
      return annotation.additionalClientClusters();
    }
    return new int[0];
  }

  public List<AttributeDescription> readAllAttributes(ProgressBar progressBar) {
    return Collections.emptyList();
  }

  public String getColor() {
    return annotation.color();
  }

  @Getter
  public static class AttributeDescription {

    private final String id;
    private final String name;
    private final String value;
    private final String dataType;
    private final boolean reportable;
    private final boolean readable;
    private final boolean writable;
    private final int reportingTimeout;
    private final int minReportingPeriod;
    private final int maxReportingPeriod;
    private final String reportingChange;

    public AttributeDescription(ZclAttribute attribute, Object value) {
      this.id = Integer.toHexString(attribute.getId());
      this.name = attribute.getName();
      this.value = value == null ? "N/A" : value.toString();
      this.dataType = attribute.getDataType().name();
      this.reportable = attribute.isReportable();
      this.readable = attribute.isReadable();
      this.writable = attribute.isWritable();
      this.reportingTimeout = attribute.getReportingTimeout();
      this.minReportingPeriod = attribute.getMinimumReportingPeriod();
      this.maxReportingPeriod = attribute.getMaximumReportingPeriod();
      this.reportingChange = attribute.getReportingChange() == null ? "N/A" : attribute.getReportingChange().toString();
    }
  }
}
