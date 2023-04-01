package org.homio.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.ZclCommandListener;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.SneakyThrows;
import org.homio.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.model.Status;
import org.homio.bundle.api.state.DecimalType;
import org.homio.bundle.api.state.State;
import org.homio.bundle.api.util.CommonUtils;
import org.homio.bundle.zigbee.converter.config.ZclReportingConfig;
import org.homio.bundle.zigbee.model.ZigBeeEndpointEntity;
import org.homio.bundle.zigbee.util.ClusterAttributeConfiguration;
import org.homio.bundle.zigbee.util.ClusterConfiguration;
import org.homio.bundle.zigbee.util.ClusterConfigurations;
import org.homio.bundle.zigbee.util.DeviceConfiguration.EndpointDefinition;
import org.homio.bundle.api.ui.field.ProgressBar;


public abstract class ZigBeeInputBaseConverter<Cluster extends ZclCluster> extends ZigBeeBaseChannelConverter
    implements ZclAttributeListener {

    @Getter private final ZclClusterType zclClusterType;
    @Getter @Nullable protected Integer attributeId;
    protected @Nullable ZclAttribute attribute;

    protected Cluster zclCluster;
    @Getter @NotNull protected ClusterAttributeConfiguration configuration;

    public ZigBeeInputBaseConverter(ZclClusterType zclClusterType, @Nullable Integer attributeId) {
        this.zclClusterType = zclClusterType;
        this.attributeId = attributeId;

        ClusterConfiguration configuration = ClusterConfigurations.getClusterConfiguration(zclClusterType.getId());
        // get or create configuration
        this.configuration = configuration.getAttributeConfiguration(attributeId == null ? -1 : attributeId);
    }

    /**
     * Test cluster. must be override if attributeID is null
     */
    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint, String entityID, EntityContext entityContext, Consumer<String> progressMessage) {
        if (attributeId == null) {
            throw new IllegalStateException("Cluster with null attributeId must override acceptEndpoint(...) method");
        }
        return acceptEndpoint(endpoint, entityID, entityContext, zclClusterType.getId(), attributeId,
            configuration.isDiscoverAttributes(), configuration.isReadAttribute(), progressMessage);
    }

    protected void afterClusterInitialized() {
    }

    @Override
    public void initialize(Consumer<String> progressMessage) {
        if (zclCluster == null) {
            log.debug("[{}]: Initialising {} device cluster {}", entityID, getClass().getSimpleName(), endpoint);
            zclCluster = getInputCluster(zclClusterType.getId());

            if (configuration.isReportConfigurable()) {
                configReporting = new ZclReportingConfig(getEntity());
            }
            afterClusterInitialized();
        }
        initializeBinding(progressMessage);
        initializeAttribute();
    }

    protected void initializeBinding(Consumer<String> progressMessage) {
        if (bindStatus != Status.DONE) {
            try {
                initializeBindingReport(progressMessage);
            } catch (Exception ex) {
                bindStatus = Status.ERROR;
                log.error("[{}]: Exception setting reporting {}. Msg: {}", entityID, endpoint, CommonUtils.getErrorMessage(ex));
                if (configuration.getBindFailedPollingPeriod() != null) {
                    pollingPeriod = configuration.getBindFailedPollingPeriod();
                }
            }
        }
    }

    protected void initializeAttribute() {
        if (attribute == null && attributeId != null) {
            attribute = zclCluster.getAttribute(attributeId);
            if (attribute == null) {
                log.error("[{}]: Error opening device {} attribute {}", entityID, zclClusterType, endpoint);
                throw new RuntimeException("Error opening device attribute");
            }

            zclCluster.addAttributeListener(this);
        }
    }

    @Override
    public void disposeConverter() {
        log.debug("[{}]: Closing device input cluster {}. {}", entityID, zclClusterType, endpoint);
        zclCluster.removeAttributeListener(this);

        if (this instanceof ZclCommandListener) {
            zclCluster.removeCommandListener((ZclCommandListener) this);
        }
    }

    @Override
    protected void handleRefresh(@Nullable Consumer<String> progressMessage) {
        if (attribute != null) {
            if (progressMessage != null) {
                progressMessage.accept("read attr: '" + attribute.getName() + "'");
            }
            attribute.readValue(0);
        }
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        if (attributeId == null) {
            throw new IllegalStateException("Cluster with null attributeId must override attributeUpdated(...) method");
        }

        log.debug("[{}]: ZigBee attribute reports {}. {}. Value: {}", entityID, attribute, endpoint, val);
        if (attribute.getClusterType() == zclClusterType) {
            if (attribute.getId() == attributeId) {
                updateValue(val, attribute);
            } else {
                log.debug("[{}]: Got value for another attribute: {}. {}. Value: {}", entityID, attribute, endpoint, val);
            }
        }
    }

    protected void updateValue(Object val, ZclAttribute attribute) {
        String unit = getEndpointService().getEndpointDefinition().map(EndpointDefinition::getUnit).orElse(null);
        if (val instanceof Number) {
            updateChannelState(new DecimalType((Number) val).setUnit(unit));
        } else if (val instanceof Boolean) {
            updateChannelState(State.of(val));
        } else {
            throw new IllegalStateException("Unable to find value handler for type: " + val);
        }
    }

    @Override
    public void updateConfiguration() {
        if (configReporting != null && configReporting.updateConfiguration(getEntity())) {
            updateDeviceReporting(zclCluster, attributeId, true);
        }
    }

    @Override
    public boolean tryBind() throws Exception {
        CommandResult commandResult = bind(zclCluster);
        return commandResult.isSuccess();
    }

    protected void initializeBindingReport(Consumer<String> progressMessage) throws Exception {
        if (attributeId == null) {
            throw new IllegalStateException("Cluster with null attributeId must override initializeBindingReport(...) method");
        }
        progressMessage.accept("binding cluster");
        CommandResult bindResponse = bind(zclCluster);
        if (bindResponse.isSuccess()) {
            ZclAttribute attribute = zclCluster.getAttribute(attributeId);

            ZigBeeEndpointEntity endpointEntity = getEndpointService().getEntity();
            progressMessage.accept("set attr: '" + attribute.getName() + "' report");
            CommandResult reportingResponse = attribute.setReporting(
                configuration.getReportMinInterval(endpointEntity),
                configuration.getReportMaxInterval(endpointEntity),
                configuration.getReportChange(endpointEntity)).get();

            handleReportingResponse(reportingResponse, configuration.getFailedPollingInterval(), configuration.getSuccessMaxReportInterval(endpointEntity));
        } else {
            if (configuration.getBindFailedPollingPeriod() != null) {
                pollingPeriod = configuration.getBindFailedPollingPeriod();
            }
            log.warn("[{}]: Could not bind '{}'. Response code: {}", entityID, zclClusterType.name(), bindResponse.getStatusCode());
        }
    }

    @SneakyThrows
    @Override
    public List<AttributeDescription> readAllAttributes(ProgressBar progressBar) {
        List<AttributeDescription> list = new ArrayList<>();
        progressBar.progress(0, getName() + ":discovery attributes");
        if (zclCluster.discoverAttributes(false).get()) {
            Set<Integer> supportedAttributes = zclCluster.getSupportedAttributes();
            float delta = 99F / supportedAttributes.size();
            float progress = 1;
            for (Integer attributeId : supportedAttributes) {
                ZclAttribute attribute = zclCluster.getAttribute(attributeId);
                progressBar.progress(progress, getName() + ":read:" + attribute.getName());
                list.add(new AttributeDescription(attribute, attribute.readValue(0)));
                progress += delta;
            }
        }
        return list;
    }
}
