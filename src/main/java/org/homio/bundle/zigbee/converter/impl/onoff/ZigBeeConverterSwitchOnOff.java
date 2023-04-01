package org.homio.bundle.zigbee.converter.impl.onoff;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeCommand;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.ZclCommand;
import com.zsmartsystems.zigbee.zcl.ZclCommandListener;
import com.zsmartsystems.zigbee.zcl.ZclStatus;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OffCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OffWithEffectCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OnCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OnWithTimedOffCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.ToggleCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.ZclOnOffCommand;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import org.homio.bundle.zigbee.converter.impl.ZigBeeConverter;
import org.homio.bundle.zigbee.converter.impl.ZigBeeInputBaseConverter;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.EntityContextVar.VariableType;
import org.homio.bundle.api.state.OnOffType;
import org.homio.bundle.api.ui.UI.Color;
import org.homio.bundle.api.ui.field.action.v1.UIInputBuilder;
import org.homio.bundle.api.ui.field.action.v1.layout.UIFlexLayoutBuilder;
import org.homio.bundle.zigbee.converter.config.ZclOnOffSwitchConfig;
import org.homio.bundle.zigbee.util.ZigBeeUtil;

/**
 * This channel supports changes through attribute updates, and also through received commands. This allows a switch that is not connected to a load to send
 * commands, or a switch that is connected to a load to send status (or both!).
 */
@ZigBeeConverter(name = "switch_onoff", linkType = VariableType.Bool,
                 color = "#81CF34", serverClusters = {ZclOnOffCluster.CLUSTER_ID}, clientCluster = ZclOnOffCluster.CLUSTER_ID, category = "Light")
public class ZigBeeConverterSwitchOnOff extends ZigBeeInputBaseConverter<ZclOnOffCluster>
    implements ZclAttributeListener, ZclCommandListener {

    private final AtomicBoolean currentOnOffState = new AtomicBoolean(true);
    private ZclOnOffCluster clusterOnOffClient;
    private ScheduledExecutorService updateScheduler;
    private ScheduledFuture<?> updateTimer = null;

    public ZigBeeConverterSwitchOnOff() {
        super(ZclClusterType.ON_OFF, ZclOnOffCluster.ATTR_ONOFF);
    }

    @Override
    public void initialize(Consumer<String> progressMessage) {
        if (zclCluster == null && clusterOnOffClient == null) {
            updateScheduler = Executors.newSingleThreadScheduledExecutor();

            if (hasInputCluster(ZclOnOffCluster.CLUSTER_ID)) {
                super.initialize(progressMessage);
                configOnOff = new ZclOnOffSwitchConfig(getEntity(), zclCluster, log);
            }

            clusterOnOffClient = getOutputCluster(ZclOnOffCluster.CLUSTER_ID);
            if (clusterOnOffClient != null) {
                try {
                    CommandResult bindResponse = bind(clusterOnOffClient);
                    if (!bindResponse.isSuccess()) {
                        log.error("[{}]: Error 0x{} setting client binding {}", entityID,
                            Integer.toHexString(bindResponse.getStatusCode()).toUpperCase(), this.endpoint);
                    }
                } catch (Exception e) {
                    log.error("[{}]: Exception setting binding {}", entityID, this.endpoint, e);
                }
                clusterOnOffClient.addCommandListener(this);
            }
        }
    }

    @Override
    public void disposeConverter() {
        super.disposeConverter();
        log.debug("[{}]: Closing device on/off cluster {}", entityID, endpoint);

        if (clusterOnOffClient != null) {
            clusterOnOffClient.removeCommandListener(this);
        }

        stopOffTimer();
        updateScheduler.shutdownNow();
    }

    @Override
    public Integer getPollingPeriod() {
        if (configReporting != null) {
            return configReporting.getPollingPeriod();
        }
        return null;
    }

    @Override
    public Future<CommandResult> handleCommand(final ZigBeeCommand command) {
        if (zclCluster == null) {
            log.warn("[{}]: OnOff converter is not linked to a server and cannot accept commands {}", entityID, endpoint);
            return null;
        }

        if (command instanceof ZclOnOffCommand) {
            return zclCluster.sendCommand((ZclOnOffCommand) command);
        }
        return null;
    }

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint, String entityID, EntityContext entityContext, Consumer<String> progressMessage) {
        if (endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID) == null
            && endpoint.getOutputCluster(ZclOnOffCluster.CLUSTER_ID) == null) {
            log.trace("[{}]: OnOff cluster not found {}", entityID, endpoint);
            return false;
        }

        return true;
    }

    @Override
    public void updateConfiguration() {
        if (zclCluster == null) {
            return;
        }
        if (configReporting != null && configReporting.updateConfiguration(getEntity())) {
            updateDeviceReporting(zclCluster, ZclOnOffCluster.ATTR_ONOFF, false);
        }
        if (configOnOff != null) {
            configOnOff.updateConfiguration(getEntity());
        }
    }

    @SneakyThrows
    @Override
    protected void updateValue(Object val, ZclAttribute attribute) {
        // sleep 1ms to avoid multiple click at same time and able to save clicks with different variable id(which is current time in milliseconds)
        Thread.sleep(1);
        Boolean value = (Boolean) val;
        if (value != null && value) {
            updateChannelState(OnOffType.ON);
        } else {
            updateChannelState(OnOffType.OFF);
        }
    }

    @Override
    public boolean commandReceived(ZclCommand command) {
        log.debug("[{}]: ZigBee command received {}. {}", entityID, command, endpoint);
        if (command instanceof OnCommand) {
            currentOnOffState.set(true);
            updateChannelState(OnOffType.ON);
            clusterOnOffClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            return true;
        }
        if (command instanceof OnWithTimedOffCommand) {
            currentOnOffState.set(true);
            updateChannelState(OnOffType.ON);
            OnWithTimedOffCommand timedCommand = (OnWithTimedOffCommand) command;
            clusterOnOffClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            startOffTimer(timedCommand.getOnTime() * 100);
            return true;
        }
        if (command instanceof OffCommand || command instanceof OffWithEffectCommand) {
            currentOnOffState.set(false);
            updateChannelState(OnOffType.OFF);
            clusterOnOffClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            return true;
        }
        if (command instanceof ToggleCommand) {
            currentOnOffState.set(!currentOnOffState.get());
            updateChannelState(currentOnOffState.get() ? OnOffType.ON : OnOffType.OFF);
            clusterOnOffClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            return true;
        }

        return false;
    }

    private void stopOffTimer() {
        if (updateTimer != null) {
            updateTimer.cancel(true);
            updateTimer = null;
        }
    }

    private void startOffTimer(int delay) {
        stopOffTimer();

        updateTimer = updateScheduler.schedule(() -> {
            log.debug("[{}]: OnOff auto OFF timer expired {}", entityID, endpoint);
            updateChannelState(OnOffType.OFF);
            updateTimer = null;
        }, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {
        super.assembleActions(uiInputBuilder);
        if (zclCluster != null) {
            UIFlexLayoutBuilder flex = uiInputBuilder.addFlex("switch-cluster")
                                                     .setBorderArea("zigbee.switch_cluster")
                                                     .setBorderColor("#D4D852");
            flex.addButton("on", "fas fa-toggle-on", Color.GREEN, (entityContext, params) ->
                    ZigBeeUtil.toResponseModel(zclCluster.sendCommand(new OnCommand())))
                .setText("Turn on switch").appendStyle("margin-right", "10px");
            flex.addButton("off", "fas fa-toggle-off", Color.GREEN, (entityContext, params) ->
                    ZigBeeUtil.toResponseModel(zclCluster.sendCommand(new OffCommand())))
                .setText("Turn off switch");
        }
    }
}
