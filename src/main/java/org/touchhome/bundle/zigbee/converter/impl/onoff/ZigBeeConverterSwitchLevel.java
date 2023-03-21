package org.touchhome.bundle.zigbee.converter.impl.onoff;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.ZclCommand;
import com.zsmartsystems.zigbee.zcl.ZclCommandListener;
import com.zsmartsystems.zigbee.zcl.ZclStatus;
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.MoveCommand;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.MoveToLevelCommand;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.MoveToLevelWithOnOffCommand;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.MoveWithOnOffCommand;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.StepCommand;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.StepWithOnOffCommand;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.StopCommand;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.StopWithOnOffCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OffCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OffWithEffectCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OnCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OnWithTimedOffCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.ToggleCommand;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextVar.VariableType;
import org.touchhome.bundle.api.state.DecimalType;
import org.touchhome.bundle.api.state.OnOffType;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.converter.config.ZclLevelControlConfig;
import org.touchhome.bundle.zigbee.converter.config.ZclOnOffSwitchConfig;
import org.touchhome.bundle.zigbee.converter.config.ZclReportingConfig;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeConverter;
import org.touchhome.bundle.zigbee.model.ZigBeeEndpointEntity;

/**
 * Sets the level of the light Level control converter uses both the {@link ZclLevelControlCluster} and the {@link ZclOnOffCluster}.
 * <p>
 * For the server side, if the {@link ZclOnOffCluster} has reported the device is OFF, then reports from {@link ZclLevelControlCluster} are ignored. This is
 * required as devices can report via the {@link ZclLevelControlCluster} that they have a specified level, but still be OFF.
 */
@ZigBeeConverter(name = "switch_level",
                 linkType = VariableType.Bool,
                 color = "#3479CF", serverClusters = {ZclOnOffCluster.CLUSTER_ID, ZclLevelControlCluster.CLUSTER_ID},
                 clientCluster = ZclOnOffCluster.CLUSTER_ID,
                 additionalClientClusters = {ZclLevelControlCluster.CLUSTER_ID}, category = "Light")
public class ZigBeeConverterSwitchLevel extends ZigBeeBaseChannelConverter
    implements ZclAttributeListener, ZclCommandListener {

    private final AtomicBoolean currentOnOffState = new AtomicBoolean(true);
    private ZclOnOffCluster clusterOnOffClient;
    private ZclLevelControlCluster clusterLevelControlClient;
    private ZclOnOffCluster clusterOnOffServer;
    private ZclLevelControlCluster clusterLevelControlServer;
    private ZclAttribute attributeOnOff;
    private ZclAttribute attributeLevel;
    private DecimalType lastLevel = DecimalType.HUNDRED;
    private ScheduledExecutorService updateScheduler;
    private ScheduledFuture<?> updateTimer = null;

    //  private Command lastCommand;

    public static DecimalType levelToPercent(int level) {
        return new DecimalType((int) (level * 100.0 / 254.0 + 0.5));
    }

    private boolean initializeDeviceServer() {
        ZclLevelControlCluster serverClusterLevelControl = getInputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (serverClusterLevelControl == null) {
            log.trace("[{}]: Error opening device level controls {}", entityID, endpoint);
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverClusterLevelControl);
            if (bindResponse.isSuccess()) {
                // Configure reporting
                ZigBeeEndpointEntity endpointEntity = getEndpointService().getEntity();
                CommandResult reportingResponse = serverClusterLevelControl.setReporting(ZclLevelControlCluster.ATTR_CURRENTLEVEL,
                    endpointEntity.getReportingTimeMin(),
                    endpointEntity.getReportingTimeMax(), 1).get();
                handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH,
                    endpointEntity.getPollingPeriod());
            } else {
                pollingPeriod = POLLING_PERIOD_HIGH;
                log.debug("[{}]: Failed to bind level control cluster {}", entityID, endpoint);
            }
        } catch (Exception e) {
            log.error("[{}]: Exception setting level control reporting {}", entityID, endpoint, e);
            return false;
        }

        ZclOnOffCluster serverClusterOnOff = getInputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (serverClusterOnOff == null) {
            log.trace("[{}]: Error opening device level controls {}", entityID, endpoint);
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverClusterOnOff);
            if (bindResponse.isSuccess()) {
                // Configure reporting
                ZigBeeEndpointEntity endpointEntity = getEndpointService().getEntity();
                CommandResult reportingResponse = serverClusterLevelControl.setReporting(ZclOnOffCluster.ATTR_ONOFF,
                    endpointEntity.getReportingTimeMin(),
                    endpointEntity.getReportingTimeMax()).get();
                handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH,
                    endpointEntity.getPollingPeriod());
            } else {
                pollingPeriod = POLLING_PERIOD_HIGH;
                log.debug("[{}]: Failed to bind on off control cluster {}", entityID, endpoint);
                return false;
            }
        } catch (Exception e) {
            log.error("[{}]: Exception setting on off reporting {}", entityID, endpoint, e);
            return false;
        }

        return true;
    }

    @Override
    public Integer getPollingPeriod() {
        if (configReporting != null) {
            return configReporting.getPollingPeriod();
        }
        return null;
    }

    private boolean initializeDeviceClient() {
        ZclLevelControlCluster clusterLevelControl = getOutputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (clusterLevelControl == null) {
            log.trace("[{}]: Error opening device level controls {}", entityID, endpoint);
            return false;
        }

        try {
            CommandResult bindResponse = bind(clusterLevelControl);
            if (!bindResponse.isSuccess()) {
                log.error("[{}]: Error 0x{} setting client binding {}", entityID,
                    Integer.toHexString(bindResponse.getStatusCode()).toUpperCase(), endpoint);
            }
        } catch (Exception e) {
            log.error("[{}]: Exception setting level control reporting {}", entityID, endpoint, e);
            return false;
        }

        ZclOnOffCluster clusterOnOff = getOutputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (clusterOnOff == null) {
            log.trace("[{}]: Error opening device on off controls {}", entityID, endpoint);
            return false;
        }

        try {
            CommandResult bindResponse = bind(clusterOnOff);
            if (!bindResponse.isSuccess()) {
                log.error("[{}]: Error 0x{} setting client binding {}", entityID,
                    Integer.toHexString(bindResponse.getStatusCode()).toUpperCase(), endpoint);
            }
        } catch (Exception e) {
            log.error("[{}]: Exception setting on off reporting {}", entityID, endpoint, e);
            return false;
        }

        return true;
    }

    @Override
    public synchronized void initialize(Consumer<String> progressMessage) {
        if (updateScheduler == null) {
            updateScheduler = Executors.newSingleThreadScheduledExecutor();

            if (initializeDeviceServer()) {
                log.debug("[{}]: Level control device initialized as server {}", entityID, this.endpoint);
                initializeConverterServer();
            } else if (initializeDeviceClient()) {
                log.debug("[{}]: Level control device initialized as client {}", entityID, this.endpoint);
                initializeConverterClient();
            }
        }
    }

    private boolean initializeConverterServer() {
        clusterLevelControlServer = getInputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (clusterLevelControlServer == null) {
            log.trace("[{}]: Error opening device level controls {}", entityID, endpoint);
            return false;
        }

        clusterOnOffServer = getInputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (clusterOnOffServer == null) {
            log.trace("[{}]: Error opening device on off controls {}", entityID, endpoint);
            return false;
        }

        attributeOnOff = clusterOnOffServer.getAttribute(ZclOnOffCluster.ATTR_ONOFF);
        attributeLevel = clusterLevelControlServer.getAttribute(ZclLevelControlCluster.ATTR_CURRENTLEVEL);

        // Add a listeners
        clusterOnOffServer.addAttributeListener(this);
        clusterLevelControlServer.addAttributeListener(this);

        // Set the currentOnOffState to ON. This will ensure that we only ignore levelControl reports AFTER we have
        // really received an OFF report, thus confirming ON_OFF reporting is working
        currentOnOffState.set(true);

        // Create a configuration handler and get the available options
        configReporting = new ZclReportingConfig(getEntity());
        configLevelControl = new ZclLevelControlConfig(getEntity(), clusterLevelControlServer, log);
        configOnOff = new ZclOnOffSwitchConfig(getEntity(), clusterOnOffServer, log);

        return true;
    }

    private boolean initializeConverterClient() {
        clusterLevelControlClient = getOutputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (clusterLevelControlClient == null) {
            log.trace("[{}]: Error opening device level controls {}", entityID, endpoint);
            return false;
        }

        clusterOnOffClient = getOutputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (clusterOnOffClient == null) {
            log.trace("[{}]: Error opening device on off controls {}", entityID, endpoint);
            return false;
        }

        // Add a listeners
        clusterOnOffClient.addCommandListener(this);
        clusterLevelControlClient.addCommandListener(this);

        // Set the currentOnOffState to ON. This will ensure that we only ignore levelControl reports AFTER we have
        // really received an OFF report, thus confirming ON_OFF reporting is working
        currentOnOffState.set(true);

        //configOptions = new ArrayList<>();

        return true;
    }

    @Override
    public void disposeConverter() {
        if (clusterOnOffClient != null) {
            clusterOnOffClient.removeCommandListener(this);
        }
        if (clusterLevelControlClient != null) {
            clusterLevelControlClient.removeCommandListener(this);
        }
        if (clusterOnOffServer != null) {
            clusterOnOffServer.removeAttributeListener(this);
        }
        if (clusterLevelControlServer != null) {
            clusterLevelControlServer.removeAttributeListener(this);
        }

        stopTransitionTimer();
        updateScheduler.shutdownNow();
    }

    /**
     * If we support the OnOff cluster then we should perform the same function as the SwitchOnoffConverter. Otherwise, interpret ON commands as moving to level
     * 100%, and OFF commands as moving to level 0%.
     */
    /*private void handleOnOffCommand(OnOffType cmdOnOff) {
        if (clusterOnOffServer != null) {
            if (cmdOnOff == OnOffType.ON) {
                clusterOnOffServer.onCommand();
            } else {
                clusterOnOffServer.offCommand();
            }
        } else {
            if (cmdOnOff == OnOffType.ON) {
                moveToLevel(DecimalType.HUNDRED);
            } else {
                moveToLevel(DecimalType.ZERO);
            }
        }
    }*/

   /* private void handlePercentCommand(DecimalType cmdPercent) {
        moveToLevel(cmdPercent);
    }*/

    /*private void moveToLevel(DecimalType percent) {
        if (clusterOnOffServer != null) {
            if (percent.equals(DecimalType.ZERO)) {
                clusterOnOffServer.offCommand();
            } else {
                clusterLevelControlServer.moveToLevelWithOnOffCommand(percentToLevel(percent),
                        configLevelControl.getDefaultTransitionTime());
            }
        } else {
            clusterLevelControlServer.moveToLevelCommand(percentToLevel(percent),
                    configLevelControl.getDefaultTransitionTime());
        }
    }*/
    @Override
    protected void handleRefresh(@Nullable Consumer<String> progressMessage) {
        if (attributeOnOff != null) {
            if (progressMessage != null) {
                progressMessage.accept("read attr: '" + attributeOnOff.getName() + "'");
            }
            attributeOnOff.readValue(0);
        }
        if (attributeLevel != null) {
            if (progressMessage != null) {
                progressMessage.accept("read attr: '" + attributeLevel.getName() + "'");
            }
            attributeLevel.readValue(0);
        }
    }

    /**
     * The IncreaseDecreaseType in openHAB is defined as a STEP command. however we want to use this for the Move/Stop command which is not available in
     * openHAB. When the first IncreaseDecreaseType is received, we send the Move command and start a timer to send the Stop command when no further
     * IncreaseDecreaseType commands are received. We use the lastCommand to check if the current command is the same IncreaseDecreaseType, and if so we just
     * restart the timer. When the timer times out and sends the Stop command, it also sets lastCommand to null.
     * <p>
     * // * @param cmdIncreaseDecrease the command received
     */
   /* private void handleIncreaseDecreaseCommand(IncreaseDecreaseType cmdIncreaseDecrease) {
        if (!cmdIncreaseDecrease.equals(lastCommand)) {
            switch (cmdIncreaseDecrease) {
                case INCREASE:
                    clusterLevelControlServer.moveWithOnOffCommand(0, 50);
                    break;
                case DECREASE:
                    clusterLevelControlServer.moveWithOnOffCommand(1, 50);
                    break;
                default:
                    break;
            }
        }
        startStopTimer(INCREASEDECREASE_TIMEOUT);
    }*/
    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint, String entityID, EntityContext entityContext, Consumer<String> progressMessage) {
        if (endpoint.getInputCluster(ZclLevelControlCluster.CLUSTER_ID) == null
            && endpoint.getOutputCluster(ZclLevelControlCluster.CLUSTER_ID) == null) {
            log.trace("[{}]: Level control cluster not found {}", entityID, endpoint);
            return false;
        }

        return true;
    }

  /*  @Override
    public void handleCommand(final ZigBeeCommand command) {
        if (command instanceof OnOffType) {
            handleOnOffCommand((OnOffType) command);
        } else if (command instanceof DecimalType) {
            handlePercentCommand((DecimalType) command);
        } else if (command instanceof IncreaseDecreaseType) {
            handleIncreaseDecreaseCommand((IncreaseDecreaseType) command);
        } else {
            log.warn("[{}]: Level converter only accepts DecimalType, IncreaseDecreaseType and OnOffType - not {}",
                    endpoint, command.getClass().getSimpleName());
        }

        // Some functionality (eg IncreaseDecrease) requires that we know the last command received
        lastCommand = command;
    }*/

    @Override
    public void updateConfiguration() {
        if (configReporting != null) {
            if (configReporting.updateConfiguration(getEntity())) {
                updateDeviceReporting(clusterLevelControlServer, ZclOnOffCluster.ATTR_ONOFF, true);
                updateDeviceReporting(clusterLevelControlServer, ZclLevelControlCluster.ATTR_CURRENTLEVEL, false);
            }
        }

        if (configLevelControl != null) {
            configLevelControl.updateConfiguration(getEntity());
        }
        if (configOnOff != null) {
            configOnOff.updateConfiguration(getEntity());
        }
    }

    @Override
    public synchronized void attributeUpdated(ZclAttribute attribute, Object val) {
        log.debug("[{}]: ZigBee attribute reports {}. {}", entityID, attribute, endpoint);
        if (attribute.getClusterType() == ZclClusterType.LEVEL_CONTROL
            && attribute.getId() == ZclLevelControlCluster.ATTR_CURRENTLEVEL) {
            //lastLevel = levelToPercent((Integer) val);
            if (currentOnOffState.get()) {
                // Note that state is only updated if the current On/Off state is TRUE (ie ON)
                updateChannelState(lastLevel);
            }
        } else if (attribute.getClusterType() == ZclClusterType.ON_OFF && attribute.getId() == ZclOnOffCluster.ATTR_ONOFF) {
            if (attribute.getLastValue() != null) {
                currentOnOffState.set((Boolean) val);
                updateChannelState(currentOnOffState.get() ? lastLevel : OnOffType.OFF);
            }
        }
    }

    @Override
    public boolean commandReceived(ZclCommand command) {
        log.debug("[{}]: ZigBee command received {} for {}", entityID, command, endpoint);

        // OnOff Cluster Commands
        if (command instanceof OnCommand) {
            currentOnOffState.set(true);
            //lastLevel = DecimalType.HUNDRED;
            //updateChannelState(lastLevel);
            clusterOnOffClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            return true;
        }
        if (command instanceof OnWithTimedOffCommand) {
            currentOnOffState.set(true);
            OnWithTimedOffCommand timedCommand = (OnWithTimedOffCommand) command;
            //lastLevel = DecimalType.HUNDRED;
            //updateChannelState(lastLevel);
            clusterOnOffClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            startOffTimer(timedCommand.getOnTime() * 100);
            return true;
        }
        if (command instanceof OffCommand) {
            currentOnOffState.set(false);
            //lastLevel = DecimalType.ZERO;
            //updateChannelState(lastLevel);
            return true;
        }
        if (command instanceof ToggleCommand) {
            currentOnOffState.set(!currentOnOffState.get());
            //lastLevel = currentOnOffState.get() ? DecimalType.HUNDRED : DecimalType.ZERO;
            //updateChannelState(lastLevel);
            clusterOnOffClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            return true;
        }
        if (command instanceof OffWithEffectCommand) {
            OffWithEffectCommand offEffect = (OffWithEffectCommand) command;
            startOffEffect(offEffect.getEffectIdentifier(), offEffect.getEffectVariant());
            clusterOnOffClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            return true;
        }

        // LevelControl Cluster Commands
        if (command instanceof MoveToLevelCommand || command instanceof MoveToLevelWithOnOffCommand) {
            int time;
            int level;

            if (command instanceof MoveToLevelCommand) {
                MoveToLevelCommand levelCommand = (MoveToLevelCommand) command;
                time = levelCommand.getTransitionTime();
                level = levelCommand.getLevel();
            } else {
                MoveToLevelWithOnOffCommand levelCommand = (MoveToLevelWithOnOffCommand) command;
                time = levelCommand.getTransitionTime();
                level = levelCommand.getLevel();
            }
            clusterLevelControlClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            startTransitionTimer(time * 100, levelToPercent(level).doubleValue());
            return true;
        }
        if (command instanceof MoveCommand || command instanceof MoveWithOnOffCommand) {
            int mode;
            int rate;

            if (command instanceof MoveCommand) {
                MoveCommand levelCommand = (MoveCommand) command;
                mode = levelCommand.getMoveMode();
                rate = levelCommand.getRate();
            } else {
                MoveWithOnOffCommand levelCommand = (MoveWithOnOffCommand) command;
                mode = levelCommand.getMoveMode();
                rate = levelCommand.getRate();
            }

            clusterLevelControlClient.sendDefaultResponse(command, ZclStatus.SUCCESS);

            // Get percent change per step period
            double stepRatePerSecond = levelToPercent(rate).doubleValue();
            double distance;

            if (mode == 0) {
                distance = 100.0 - lastLevel.doubleValue();
            } else {
                distance = lastLevel.doubleValue();
            }
            int transitionTime = (int) (distance / stepRatePerSecond * 1000);

            startTransitionTimer(transitionTime, mode == 0 ? 100.0 : 0.0);
            return true;
        }
        if (command instanceof StepCommand || command instanceof StepWithOnOffCommand) {
            int mode;
            int step;
            int time;

            if (command instanceof StepCommand) {
                StepCommand levelCommand = (StepCommand) command;
                mode = levelCommand.getStepMode();
                step = levelCommand.getStepSize();
                time = levelCommand.getTransitionTime();
            } else {
                StepWithOnOffCommand levelCommand = (StepWithOnOffCommand) command;
                mode = levelCommand.getStepMode();
                step = levelCommand.getStepSize();
                time = levelCommand.getTransitionTime();
            }

            double value;
            if (mode == 0) {
                value = lastLevel.doubleValue() + levelToPercent(step).doubleValue();
            } else {
                value = lastLevel.doubleValue() - levelToPercent(step).doubleValue();
            }
            if (value < 0.0) {
                value = 0.0;
            } else if (value > 100.0) {
                value = 100.0;
            }

            clusterLevelControlClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            startTransitionTimer(time * 100, value);
            return true;
        }
        if (command instanceof StopCommand || command instanceof StopWithOnOffCommand) {
            clusterLevelControlClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            stopTransitionTimer();
            return true;
        }

        return false;
    }

    private void stopTransitionTimer() {
        if (updateTimer != null) {
            updateTimer.cancel(true);
            updateTimer = null;
        }
    }

    /**
     * Starts a timer to transition to finalState with transitionTime milliseconds. The state will be updated every STATE_UPDATE_RATE milliseconds.
     *
     * @param transitionTime the number of milliseconds to move the finalState
     * @param finalState     the final level to move to
     */
    private void startTransitionTimer(int transitionTime, double finalState) {
        stopTransitionTimer();

        log.debug("[{}]: Level transition move to {} in {}ms. {}", entityID, finalState, transitionTime, endpoint);
        final int steps = transitionTime / STATE_UPDATE_RATE;
        if (steps == 0) {
            log.debug("[{}]: Level transition timer has 0 steps. Setting to {}. {}", entityID, finalState, endpoint);
            lastLevel = new DecimalType((int) finalState);
            currentOnOffState.set(finalState != 0);
            // updateChannelState(lastLevel);
            return;
        }
        final double start = lastLevel.doubleValue();
        final double step = (finalState - lastLevel.doubleValue()) / steps;

        updateTimer = updateScheduler.scheduleAtFixedRate(new Runnable() {
            private int count = 0;
            private double state = start;

            @Override
            public void run() {
                state += step;
                if (state < 0.0) {
                    state = 0.0;
                } else if (state > 100.0) {
                    state = 100.0;
                }
                lastLevel = new DecimalType((int) state);
                log.debug("[{}]: Level transition timer {}/{} updating to {}. {}", entityID, count, steps, lastLevel, endpoint);
                currentOnOffState.set(state != 0);
                // updateChannelState(lastLevel);

                if (state == 0.0 || state == 100.0 || ++count == steps) {
                    log.debug("[{}]: Level transition timer complete {}", entityID, endpoint);
                    updateTimer.cancel(true);
                    updateTimer = null;
                }
            }
        }, 0, STATE_UPDATE_RATE, TimeUnit.MILLISECONDS);
    }

    /**
     * Starts a timer after which the state will be set to OFF
     *
     * @param delay the number of milliseconds to wait before setting the value to OFF
     */
    private void startOffTimer(int delay) {
        stopTransitionTimer();

        updateTimer = updateScheduler.schedule(() -> {
            log.debug("[{}]: OnOff auto OFF timer expired {}", entityID, endpoint);
            lastLevel = DecimalType.ZERO;
            currentOnOffState.set(false);
            updateChannelState(OnOffType.OFF);
            updateTimer = null;
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Starts a timer to perform the off effect
     *
     * @param effectId      the effect type
     * @param effectVariant the effect variant
     */
    private void startOffEffect(int effectId, int effectVariant) {
        stopTransitionTimer();

        int effect = effectId << 8 + effectVariant;

        switch (effect) {
            case 0x0002:
                // 50% dim down in 0.8 seconds then fade to off in 12 seconds
                break;

            case 0x0100:
                // 20% dim up in 0.5s then fade to off in 1 second
                break;

            default:
                log.debug("[{}]: Off effect {} unknown {}", entityID, String.format("%04", effect), endpoint);

            case 0x0000:
                // Fade to off in 0.8 seconds
            case 0x0001:
                // No fade
                startTransitionTimer(800, 0.0);
                break;
        }
    }
    // The number of milliseconds between state updates into OH when handling level control changes at a rate
    private static final int STATE_UPDATE_RATE = 50;

    /*
     * Starts a timer after which the Stop command will be sent
     *
     * @param delay the number of milliseconds to wait before setting the value to OFF
     */
    /*private void startStopTimer(int delay) {
        stopTransitionTimer();

        updateTimer = updateScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                log.debug("[{}]: IncreaseDecrease Stop timer expired", endpoint);
                clusterLevelControlServer.stopWithOnOffCommand();
                // lastCommand = null;
                updateTimer = null;
            }
        }, delay, TimeUnit.MILLISECONDS);
    }*/
    // The number of milliseconds after the last IncreaseDecreaseType is received before sending the Stop command
    private static final int INCREASEDECREASE_TIMEOUT = 200;
}
