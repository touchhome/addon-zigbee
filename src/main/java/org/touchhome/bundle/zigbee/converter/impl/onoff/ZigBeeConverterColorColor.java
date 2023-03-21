package org.touchhome.bundle.zigbee.converter.impl.onoff;

import static com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster.ATTR_COLORCAPABILITIES;
import static com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster.ATTR_COLORMODE;
import static com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster.ATTR_CURRENTHUE;
import static com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster.ATTR_CURRENTSATURATION;
import static com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster.ATTR_CURRENTX;
import static com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster.ATTR_CURRENTY;
import static com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster.ATTR_CURRENTLEVEL;
import static com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster.ATTR_ONOFF;
import static org.touchhome.bundle.zigbee.converter.impl.onoff.ZigBeeConverterSwitchLevel.levelToPercent;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import com.zsmartsystems.zigbee.zcl.clusters.colorcontrol.ColorCapabilitiesEnum;
import com.zsmartsystems.zigbee.zcl.clusters.colorcontrol.ColorModeEnum;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.MoveToLevelCommand;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.MoveToLevelWithOnOffCommand;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.ZclLevelControlCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OffCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OnCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeConverter;
import org.touchhome.bundle.zigbee.model.ZigBeeEndpointEntity.ControlMethod;

/**
 * The color channel allows to control the color of a light. It is also possible to dim values and switch the light on and off. Converter for color control.
 * Uses the {@link ZclColorControlCluster}, and may also use the {@link ZclLevelControlCluster} and {@link ZclOnOffCluster} if available.
 */
@ZigBeeConverter(name = "color_color",
                 color = "#3479CF", clientCluster = ZclOnOffCluster.CLUSTER_ID, linkType = VariableType.Float,
                 additionalClientClusters = {ZclLevelControlCluster.CLUSTER_ID, ZclColorControlCluster.CLUSTER_ID}, category = "ColorLight")
public class ZigBeeConverterColorColor extends ZigBeeBaseChannelConverter implements ZclAttributeListener {

    private final Object colorUpdateSync = new Object();
    private final AtomicBoolean currentOnOffState = new AtomicBoolean(true);
    // private HSBType lastHSB = new HSBType("0,0,100");

    private ZclColorControlCluster clusterColorControl;
    private ZclLevelControlCluster clusterLevelControl;
    private ZclOnOffCluster clusterOnOff;

    private boolean delayedColorChange = false; // Wait for brightness transition before changing color
    private ScheduledExecutorService colorUpdateScheduler;
    private ScheduledFuture<?> colorUpdateTimer = null;
    private boolean supportsHue = false;
    private int lastHue = -1;
    private int lastSaturation = -1;
    private boolean hueChanged = false;
    private boolean saturationChanged = false;
    private int lastX = -1;
    private int lastY = -1;
    private boolean xChanged = false;
    private boolean yChanged = false;
    private ColorModeEnum lastColorMode;

    @Override
    public void updateConfiguration() {
        if (configLevelControl != null) {
            configLevelControl.updateConfiguration(getEntity());
        }
        if (configOnOff != null) {
            configOnOff.updateConfiguration(getEntity());
        }
    }

    @Override
    public void initialize(Consumer<String> progressMessage) {
        if (colorUpdateScheduler == null) {
            colorUpdateScheduler = Executors.newSingleThreadScheduledExecutor();

            ZclColorControlCluster serverClusterColorControl = getInputCluster(ZclColorControlCluster.CLUSTER_ID);
            if (serverClusterColorControl == null) {
                log.error("[{}]: Error opening device color controls {}", entityID, this.endpoint);
                throw new RuntimeException("Error opening device color controls");
            }

            discoverSupportedColorCommands(serverClusterColorControl);

            // Bind to attribute reports, add listeners, then request the status
            // Configure reporting - no faster than once per second - no slower than 10 minutes.
            try {
                CommandResult bindResponse = bind(serverClusterColorControl);
                if (bindResponse.isSuccess()) {
                    CommandResult reportingResponse;
                    if (supportsHue) {
                        reportingResponse = serverClusterColorControl.setReporting(serverClusterColorControl.getAttribute(ATTR_CURRENTHUE), 1,
                            REPORTING_PERIOD_DEFAULT_MAX, 1).get();
                        handleReportingResponseHigh(reportingResponse);

                        reportingResponse = serverClusterColorControl.setReporting(serverClusterColorControl.getAttribute(ATTR_CURRENTSATURATION), 1,
                            REPORTING_PERIOD_DEFAULT_MAX, 1).get();
                        handleReportingResponseHigh(reportingResponse);
                    } else {
                        reportingResponse = serverClusterColorControl.setReporting(ATTR_CURRENTX, 1, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
                        handleReportingResponseHigh(reportingResponse);

                        reportingResponse = serverClusterColorControl.setReporting(ATTR_CURRENTY, 1, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
                        handleReportingResponseHigh(reportingResponse);
                    }
                } else {
                    log.error("[{}]: Error 0x{} setting server binding {}", entityID,
                        Integer.toHexString(bindResponse.getStatusCode()).toUpperCase(), this.endpoint);
                    pollingPeriod = POLLING_PERIOD_HIGH;
                    throw new RuntimeException("Error setting server binding");
                }
            } catch (Exception e) {
                log.debug("[{}]: Exception configuring color reporting {}", this.endpoint, e);
            }

            ZclLevelControlCluster serverClusterLevelControl = getInputCluster(ZclLevelControlCluster.CLUSTER_ID);
            if (serverClusterLevelControl == null) {
                log.warn("[{}]: Device does not support level control {}", entityID, this.endpoint);
            } else {
                try {
                    CommandResult bindResponse = bind(serverClusterLevelControl);
                    if (!bindResponse.isSuccess()) {
                        pollingPeriod = POLLING_PERIOD_HIGH;
                    }

                    CommandResult reportingResponse = serverClusterLevelControl
                        .setReporting(ATTR_CURRENTLEVEL, 1, REPORTING_PERIOD_DEFAULT_MAX, 1)
                        .get();
                    handleReportingResponseHigh(reportingResponse);
                } catch (Exception e) {
                    log.debug("[{}]: Exception configuring level reporting {}", this.endpoint, e);
                }
            }

            ZclOnOffCluster serverClusterOnOff = getInputCluster(ZclOnOffCluster.CLUSTER_ID);
            if (serverClusterOnOff == null) {
                log.debug("[{}]: Device does not support on/off control {}", entityID, this.endpoint);
            } else {
                try {
                    CommandResult bindResponse = bind(serverClusterOnOff);
                    if (!bindResponse.isSuccess()) {
                        pollingPeriod = POLLING_PERIOD_HIGH;
                    }
                    CommandResult reportingResponse = serverClusterOnOff
                        .setReporting(ATTR_ONOFF, 1, REPORTING_PERIOD_DEFAULT_MAX).get();
                    handleReportingResponseHigh(reportingResponse);
                } catch (Exception e) {
                    log.debug("[{}]: Exception configuring on/off reporting {}", this.endpoint, e);
                    throw new RuntimeException("Exception configuring on/off reporting");
                }
            }
        }

        clusterColorControl = getInputCluster(ZclColorControlCluster.CLUSTER_ID);
        if (clusterColorControl == null) {
            log.error("[{}]: Error opening device color controls {}", entityID, endpoint);
            throw new RuntimeException("Error opening device color controls");
        }

        List<Object> configOptions = new ArrayList<>();

        clusterLevelControl = getInputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (clusterLevelControl == null) {
            log.warn("[{}]: Device does not support level control {}", entityID, endpoint);
        } else {
            configLevelControl = new ZclLevelControlConfig(getEntity(), clusterLevelControl, log);
        }

        clusterOnOff = getInputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (clusterOnOff == null) {
            log.debug("[{}]: Device does not support on/off control {}", entityID, endpoint);
        } else {
            configOnOff = new ZclOnOffSwitchConfig(getEntity(), clusterOnOff, log);
        }

        this.supportConfigColorControl = true;

        discoverSupportedColorCommands(clusterColorControl);

        clusterColorControl.addAttributeListener(this);
        clusterLevelControl.addAttributeListener(this);
        clusterOnOff.addAttributeListener(this);

        getEndpointService().setConfigOptions(configOptions);
    }

    @Override
    public void disposeConverter() {
        // Stop the timer and shutdown the scheduler
        if (colorUpdateTimer != null) {
            colorUpdateTimer.cancel(true);
            colorUpdateTimer = null;
        }
        colorUpdateScheduler.shutdownNow();

        clusterColorControl.removeAttributeListener(this);

        if (clusterLevelControl != null) {
            clusterLevelControl.removeAttributeListener(this);
        }

        if (clusterOnOff != null) {
            clusterOnOff.removeAttributeListener(this);
        }

        synchronized (colorUpdateSync) {
            if (colorUpdateTimer != null) {
                colorUpdateTimer.cancel(true);
            }
        }
    }

    @Override
    protected void handleRefresh(@Nullable Consumer<String> progressMessage) {
        if (clusterOnOff != null) {
            if (progressMessage != null) {
                progressMessage.accept("read attr: 'ATTR_ONOFF'");
            }
            clusterOnOff.readAttribute(ATTR_ONOFF);
        }

        List<Integer> colorAttributes = new ArrayList<>();
        List<String> colorAttributeNames = new ArrayList<>();
        if (supportsHue) {
            colorAttributes.add(ATTR_CURRENTHUE);
            colorAttributeNames.add("ATTR_CURRENTHUE");
            colorAttributes.add(ATTR_CURRENTSATURATION);
            colorAttributeNames.add("ATTR_CURRENTSATURATION");
        } else {
            colorAttributes.add(ATTR_CURRENTX);
            colorAttributeNames.add("ATTR_CURRENTX");
            colorAttributes.add(ATTR_CURRENTY);
            colorAttributeNames.add("ATTR_CURRENTY");
        }
        colorAttributes.add(ATTR_COLORMODE);
        if (progressMessage != null) {
            progressMessage.accept("read attrs: '" + String.join(", ", colorAttributeNames) + "'");
        }
        clusterColorControl.readAttributes(colorAttributes);

        if (clusterLevelControl != null) {
            if (progressMessage != null) {
                progressMessage.accept("read attr: 'ATTR_CURRENTLEVEL'");
            }
            clusterLevelControl.readAttribute(ATTR_CURRENTLEVEL);
        }
    }

    private Future<CommandResult> changeOnOff(OnOffType onoff) throws InterruptedException, ExecutionException {
        boolean on = onoff == OnOffType.ON;

        if (clusterOnOff == null) {
            if (clusterLevelControl == null) {
                log.warn("[{}]: ignoring on/off command {}", entityID, endpoint);

                return null;
            } else {
                return changeBrightness(on ? DecimalType.HUNDRED : DecimalType.ZERO);
            }
        }

        return clusterOnOff.sendCommand(on ? new OnCommand() : new OffCommand());
    }

    private Future<CommandResult> changeBrightness(DecimalType brightness) throws InterruptedException, ExecutionException {
        if (clusterLevelControl == null) {
            if (clusterOnOff == null) {
                log.warn("[{}]: ignoring brightness command {}", entityID, endpoint);
                return null;
            } else {
                return changeOnOff(brightness.intValue() == 0 ? OnOffType.OFF : OnOffType.ON);
            }
        }

        int level = percentToLevel(brightness);

        ZclLevelControlCommand command;
        if (clusterOnOff != null) {
            if (brightness.equals(DecimalType.ZERO)) {
                return clusterOnOff.sendCommand(new OffCommand());
            } else {
                command = new MoveToLevelWithOnOffCommand(level, getEntity().getDefaultTransitionTime());
            }
        } else {
            command = new MoveToLevelCommand(level, getEntity().getDefaultTransitionTime());
        }

        return clusterLevelControl.sendCommand(command);
    }

/*  private Future<CommandResult> changeColorHueSaturation(HSBType color) {
    int hue = (int) (color.getHue().floatValue() * 254.0f / 360.0f + 0.5f);
    int saturation = percentToLevel(color.getSaturation());

    return clusterColorControl.sendCommand(
        new MoveToHueAndSaturationCommand(hue, saturation, getEntity().getDefaultTransitionTime()));
  }

  private Future<CommandResult> changeColorXY(HSBType color) {
    DecimalType[] xy = color.toXY();

    log.debug("[{}]: Change Color HSV ({}, {}, {}) -> XY ({}, {}) for {}", entityID, color.getHue(),
        color.getSaturation(), color.getBrightness(), xy[0], xy[1], endpoint);
    int x = (int) (xy[0].floatValue() / 100.0f * 65536.0f + 0.5f); // up to 65279
    int y = (int) (xy[1].floatValue() / 100.0f * 65536.0f + 0.5f); // up to 65279

    return clusterColorControl.sendCommand(new MoveToColorCommand(x, y, getEntity().getDefaultTransitionTime()));
  }*/

  /*@Override
    public void handleCommand(final ZigBeeCommand command) {
        try {
            List<Future<CommandResult>> futures = new ArrayList<>();
            if (command instanceof HSBType) {
                HSBType color = (HSBType) command;
                PercentType brightness = color.getBrightness();

                futures.add(changeBrightness(brightness));

                if (delayedColorChange && brightness.intValue() != lastHSB.getBrightness().intValue()) {
                    Thread.sleep(1100);
                }

                if (supportsHue) {
                    futures.add(changeColorHueSaturation(color));
                } else {
                    futures.add(changeColorXY(color));
                }
            } else if (command instanceof PercentType) {
                futures.add(changeBrightness((PercentType) command));
            } else if (command instanceof OnOffType) {
                futures.add(changeOnOff((OnOffType) command));
            }

            super.monitorCommandResponse(command, futures);
        } catch (Exception e) {
            logger.warn("{}: Exception processing command", endpoint.getIeeeAddress(), e);
        }
    }*/

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint, String entityID, EntityContext entityContext, Consumer<String> progressMessage) {
        ZclColorControlCluster clusterColorControl = (ZclColorControlCluster) endpoint.getInputCluster(ZclColorControlCluster.CLUSTER_ID);
        if (clusterColorControl == null) {
            log.trace("[{}]: Color control cluster not found for {}", entityID, endpoint);
            return false;
        }

        // Device is not supporting attribute reporting - instead, just read the attributes
        Integer capabilities = (Integer) clusterColorControl.getAttribute(ATTR_COLORCAPABILITIES).readValue(Long.MAX_VALUE);
        if (capabilities == null && clusterColorControl.getAttribute(ATTR_CURRENTX).readValue(Long.MAX_VALUE) == null
            && clusterColorControl.getAttribute(ATTR_CURRENTHUE).readValue(Long.MAX_VALUE) == null) {
            log.debug("[{}]: Color control XY and Hue returned null for {}", entityID, endpoint);
            return false;
        }
        if (capabilities != null && ((capabilities & (ColorCapabilitiesEnum.HUE_AND_SATURATION.getKey()
            | ColorCapabilitiesEnum.XY_ATTRIBUTE.getKey())) == 0)) {
            // No support for hue or XY
            log.debug("[{}]: Color control XY and Hue capabilities not supported for {}", entityID, endpoint);
            return false;
        }
        return true;
    }

    private void updateOnOff(boolean on) {
    /*currentOnOffState.set(on);

    if (lastColorMode != ColorModeEnum.COLOR_TEMPERATURE) {
      // Extra temp variable to avoid thread sync concurrency issues on lastHSB
      HSBType oldHSB = lastHSB;
      HSBType newHSB = on ? lastHSB : new HSBType(oldHSB.getHue(), oldHSB.getSaturation(), DecimalType.ZERO);
      updateChannelState(newHSB);
    } else if (!on) {
      updateChannelState(OnOffType.OFF);
    }*/
    }

    private void updateBrightness(DecimalType brightness) {
   /* // Extra temp variable to avoid thread sync concurrency issues on lastHSB
    HSBType oldHSB = lastHSB;
    HSBType newHSB = new HSBType(oldHSB.getHue(), oldHSB.getSaturation(), brightness);
    lastHSB = newHSB;
    if (currentOnOffState.get() && lastColorMode != ColorModeEnum.COLOR_TEMPERATURE) {
      updateChannelState(newHSB);
    }*/
    }

    private void updateColorHSB(DecimalType hue, DecimalType saturation) {
    /*// Extra temp variable to avoid thread sync concurrency issues on lastHSB
    HSBType oldHSB = lastHSB;
    HSBType newHSB = new HSBType(hue, saturation, oldHSB.getBrightness());
    lastHSB = newHSB;
    if (currentOnOffState.get() && lastColorMode != ColorModeEnum.COLOR_TEMPERATURE) {
      updateChannelState(newHSB);
    }*/
    }

    private void updateColorXY(DecimalType x, DecimalType y) {
    /*HSBType color = HSBType.fromXY(x.floatValue() / 100.0f, y.floatValue() / 100.0f);
    log.debug("[{}]: Update Color XY ({}, {}) -> HSV ({}, {}, {}) for {}", entityID, x.toString(),
        y.toString(), color.getHue(), color.getSaturation(), lastHSB.getBrightness(), endpoint);
    updateColorHSB(color.getHue(), color.getSaturation());*/
    }

    private void updateColorHSB() {
        float hueValue = lastHue * 360.0f / 254.0f;
        float saturationValue = lastSaturation * 100.0f / 254.0f;
        DecimalType hue = new DecimalType(hueValue);
        DecimalType saturation = new DecimalType(saturationValue);
        updateColorHSB(hue, saturation);
        hueChanged = false;
        saturationChanged = false;
    }

    private void updateColorXY() {
        float xValue = lastX / 65536.0f;
        float yValue = lastY / 65536.0f;
        DecimalType x = new DecimalType(Float.valueOf(xValue * 100.0f));
        DecimalType y = new DecimalType(Float.valueOf(yValue * 100.0f));
        updateColorXY(x, y);
        xChanged = false;
        yChanged = false;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        log.debug("[{}]: ZigBee attribute reports {} for {}", entityID, attribute, endpoint);

        synchronized (colorUpdateSync) {
            try {
                if (attribute.getClusterType().getId() == ZclOnOffCluster.CLUSTER_ID) {
                    if (attribute.getId() == ATTR_ONOFF) {
                        Boolean value = (Boolean) val;
                        updateOnOff(value);
                    }
                } else if (attribute.getClusterType().getId() == ZclLevelControlCluster.CLUSTER_ID) {
                    if (attribute.getId() == ATTR_CURRENTLEVEL) {
                        DecimalType brightness = levelToPercent((Integer) val);
                        updateBrightness(brightness);
                    }
                } else if (attribute.getClusterType().getId() == ZclColorControlCluster.CLUSTER_ID) {
                    if (attribute.getId() == ATTR_CURRENTHUE) {
                        int hue = (Integer) val;
                        if (hue != lastHue) {
                            lastHue = hue;
                            hueChanged = true;
                        }
                    } else if (attribute.getId() == ATTR_CURRENTSATURATION) {
                        int saturation = (Integer) val;
                        if (saturation != lastSaturation) {
                            lastSaturation = saturation;
                            saturationChanged = true;
                        }
                    } else if (attribute.getId() == ATTR_CURRENTX) {
                        int x = (Integer) val;
                        if (x != lastX) {
                            lastX = x;
                            xChanged = true;
                        }
                    } else if (attribute.getId() == ATTR_CURRENTY) {
                        int y = (Integer) val;
                        if (y != lastY) {
                            lastY = y;
                            yChanged = true;
                        }
                    } else if (attribute.getId() == ATTR_COLORMODE) {
                        Integer colorMode = (Integer) val;
                        lastColorMode = ColorModeEnum.getByValue(colorMode);
                        if (lastColorMode == ColorModeEnum.COLOR_TEMPERATURE) {
                            updateChannelState(null);
                        } else if (currentOnOffState.get()) {
                            //  updateChannelState(lastHSB);
                        }
                    }
                }

                if (hueChanged || saturationChanged || xChanged || yChanged) {
                    if (colorUpdateTimer != null) {
                        colorUpdateTimer.cancel(true);
                        colorUpdateTimer = null;
                    }

                    if (hueChanged && saturationChanged) {
                        updateColorHSB();
                    } else if (xChanged && yChanged) {
                        updateColorXY();
                    } else {
                        // Wait some time and update anyway if only one attribute in each pair is updated
                        colorUpdateTimer = colorUpdateScheduler.schedule(() -> {
                            synchronized (colorUpdateSync) {
                                try {
                                    if ((hueChanged || saturationChanged) && lastHue >= 0.0f
                                        && lastSaturation >= 0.0f) {
                                        updateColorHSB();
                                    } else if ((xChanged || yChanged) && lastX >= 0.0f && lastY >= 0.0f) {
                                        updateColorXY();
                                    }
                                } catch (Exception e) {
                                    log.debug("[{}]: Exception in deferred attribute update {}", endpoint, e);
                                }

                                colorUpdateTimer = null;
                            }
                        }, 500, TimeUnit.MILLISECONDS);
                    }
                }
            } catch (Exception e) {
                log.debug("[{}]: Exception in attribute update {}", endpoint, e);
            }
        }
    }

    private void discoverSupportedColorCommands(ZclColorControlCluster serverClusterColorControl) {
        // If the configuration is not set to AUTO, then we can override the control method
        if (isSupportConfigColorControl() && getEntity().getColorControlMethod() != ControlMethod.AUTO) {
            supportsHue = getEntity().getColorControlMethod() == ControlMethod.HUE;
            return;
        }

        // Discover whether the device supports HUE/SAT or XY color set of commands
        try {
            int discoveryTimeout = getDiscoveryTimeout(getEndpointService().getEntityContext());
            if (!serverClusterColorControl.discoverAttributes(false).get(discoveryTimeout, TimeUnit.SECONDS)) {
                log.warn("[{}]: Cannot determine whether device supports RGB color. Assuming it supports HUE/SAT {}", entityID, endpoint);
                supportsHue = true;
            } else if (serverClusterColorControl.getSupportedAttributes().contains(ATTR_CURRENTHUE)) {
                log.debug("[{}]: Device supports Hue/Saturation color set of commands {}", entityID, endpoint);
                supportsHue = true;
            } else if (serverClusterColorControl.getSupportedAttributes()
                                                .contains(ATTR_CURRENTX)) {
                log.debug("[{}]: Device supports XY color set of commands {}", entityID, endpoint);
                supportsHue = false;
                delayedColorChange = true; // For now, only for XY lights till this is configurable
            } else {
                log.warn("[{}]: Device supports neither RGB color nor XY color {}", entityID, endpoint);
                pollingPeriod = POLLING_PERIOD_HIGH;
                throw new RuntimeException("Device supports neither RGB color nor XY color");
            }
        } catch (Exception e) {
            log.warn("[{}]: Exception checking whether device endpoint supports RGB color. Assuming it supports HUE/SAT {}", entityID, endpoint, e);
            supportsHue = true;
        }
    }

    private void handleReportingResponseHigh(CommandResult reportResponse) {
        handleReportingResponse(reportResponse, POLLING_PERIOD_HIGH, REPORTING_PERIOD_DEFAULT_MAX);
    }

    private int percentToLevel(DecimalType percent) {
        return (int) (percent.floatValue() * 254.0f / 100.0f + 0.5f);
    }
}
