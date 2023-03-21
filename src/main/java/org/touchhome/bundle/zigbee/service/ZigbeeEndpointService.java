package org.touchhome.bundle.zigbee.service;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.ZigBeeProfileType;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.Status;
import org.touchhome.bundle.api.service.EntityService.ServiceInstance;
import org.touchhome.bundle.api.state.ObjectType;
import org.touchhome.bundle.api.state.State;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.model.ZigBeeEndpointEntity;
import org.touchhome.bundle.zigbee.model.ZigbeeCoordinatorEntity;
import org.touchhome.bundle.zigbee.util.DeviceConfiguration.EndpointDefinition;

@Log4j2
@Getter
public class ZigbeeEndpointService implements ServiceInstance<ZigBeeEndpointEntity> {

    @NotNull
    private final EntityContext entityContext;
    @NotNull
    private final ZigBeeBaseChannelConverter cluster;
    @NotNull
    private final ZigBeeDeviceService zigBeeDeviceService;
    @NotNull
    private final ZigbeeCoordinatorEntity coordinator;

    // node local endpoint id
    private final int localEndpointId;
    // node local ip address
    private final IeeeAddress localIpAddress;
    @Getter private final Optional<EndpointDefinition> endpointDefinition;
    private final int maxFailedPollRequests = 10;
    @Nullable private String variableId;
    private ZigBeeEndpointEntity entity;
    // TODO: NEED HANDLE into properties!
    @Setter
    @Getter
    private List<Object> configOptions;
    @Setter
    private long lastPollRequest = System.currentTimeMillis();
    private int failedPollRequests = 0;

    public ZigbeeEndpointService(ZigBeeBaseChannelConverter cluster, ZigBeeDeviceService zigBeeDeviceService,
        ZigBeeEndpointEntity entity, Optional<EndpointDefinition> endpointDefinition) {
        ZigBeeCoordinatorService coordinatorService = zigBeeDeviceService.getCoordinatorService();
        this.entityContext = zigBeeDeviceService.getEntityContext();
        this.cluster = cluster;
        this.endpointDefinition = endpointDefinition;
        this.entity = entity;
        this.zigBeeDeviceService = zigBeeDeviceService;
        this.coordinator = coordinatorService.getEntity();
        this.localEndpointId = coordinatorService.getLocalEndpointId(ZigBeeProfileType.ZIGBEE_HOME_AUTOMATION);
        this.localIpAddress = coordinatorService.getLocalIeeeAddress();

        // fire initialize endpoint
        ZigBeeEndpoint endpoint = coordinatorService.getEndpoint(zigBeeDeviceService.getNodeIeeeAddress(), entity.getAddress());
        cluster.initialize(this, endpoint);
    }

    public void updateValue(State state) {
        this.failedPollRequests = 0;
        this.lastPollRequest = System.currentTimeMillis();
        // wake up endpoint if device send request after TTL
        if (this.entity.getStatus() == Status.OFFLINE) {
            this.entity.setStatus(Status.ONLINE);
            this.zigBeeDeviceService.getEntity().updateValue(this.entity, state);
        }

        this.entity.setValue(state);
        this.entity.setLastAnswerFromEndpoint(System.currentTimeMillis());

        if (coordinator.isLogEvents()) {
            log.info("[{}]: ZigBee <{}>, event: {}", zigBeeDeviceService.getEntityID(), entity, state);
        }
        if (variableId == null) {
            variableId = entityContext.var().createVariable(zigBeeDeviceService.getDeviceVariableGroup(), entity.getVariableId(),
                entity.getVariableName(), cluster.getVariableType(), builder ->
                    builder.setDescription(entity.getVariableDescription())
                           .setColor(cluster.getColor()));
        }
        entityContext.var().set(variableId, state);

        ObjectType entityUpdated = new ObjectType(entity);
        entityContext.event().fireEvent(entity.getIeeeAddress(), entityUpdated);
        entityContext.event().fireEvent(entity.getIeeeAddress() + "_" + entity.getClusterId(), entityUpdated);
    }

    @Override
    public boolean entityUpdated(ZigBeeEndpointEntity entity) {
        this.entity = entity;
        this.cluster.updateConfiguration();
        // if entity has been updated during configuration
        if (entity.isOutdated()) {
            log.info("[{}]: Endpoint had been updated during cluster configuration", zigBeeDeviceService.getEntityID());
            entityContext.save(entity);
        }
        return false;
    }

    @Override
    public void destroy() {

    }

    @Override
    public boolean testService() {
        return false;
    }

    public void pollRequest(boolean force) {
        if (force) {
            cluster.fireRefreshAttribute(null);
            return;
        }
        if (this.failedPollRequests > maxFailedPollRequests) {
            entity.setStatus(Status.OFFLINE);
            return;
        }
        if ((System.currentTimeMillis() - lastPollRequest) / 1000 > cluster.getMinPollingInterval()) {
            log.info("[{}]: Polling endpoint {} attribute", zigBeeDeviceService.getEntityID(), entity);
            lastPollRequest = System.currentTimeMillis();
            failedPollRequests++;
            cluster.fireRefreshAttribute(null);
        }
    }
}
