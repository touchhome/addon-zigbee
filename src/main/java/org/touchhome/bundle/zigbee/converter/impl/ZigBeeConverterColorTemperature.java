package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.colorcontrol.ColorCapabilitiesEnum;
import com.zsmartsystems.zigbee.zcl.clusters.colorcontrol.ColorModeEnum;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import java.util.function.Consumer;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextVar.VariableType;
import org.touchhome.bundle.api.state.DecimalType;

/**
 * Channel converter for color temperature, converting between the color control cluster and a percent-typed channel.
 */
@ZigBeeConverter(name = "color_temperature", linkType = VariableType.Float,
                 color = "#CF8E34", clientCluster = ZclColorControlCluster.CLUSTER_ID, category = "Color")
public class ZigBeeConverterColorTemperature extends ZigBeeInputBaseConverter<ZclColorControlCluster> {

  // Default range of 2000K to 6500K
  private final Integer DEFAULT_MIN_TEMPERATURE_IN_KELVIN = 2000;
  private final Integer DEFAULT_MAX_TEMPERATURE_IN_KELVIN = 6500;
  private ZclColorControlCluster clusterColorControl;
  private double kelvinMin;
  private double kelvinMax;
  private double kelvinRange;
  private ColorModeEnum lastColorMode;

  public ZigBeeConverterColorTemperature() {
    super(ZclClusterType.COLOR_CONTROL, ZclColorControlCluster.ATTR_COLORTEMPERATURE);
  }

  @Override
  protected void initializeBindingReport(Consumer<String> progressMessage) throws Exception {
    super.initializeBindingReport(progressMessage);

    // ColorMode reporting
    progressMessage.accept("set attr: 'ATTR_COLORMODE' report");
    CommandResult reportingResponse = zclCluster.setReporting(ZclColorControlCluster.ATTR_COLORMODE,
        1, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
    handleReportingResponse(reportingResponse);
  }

  @Override
  protected void afterClusterInitialized() {
    determineMinMaxTemperature(clusterColorControl);
  }

  @Override
  public void disposeConverter() {
    clusterColorControl.removeAttributeListener(this);
  }

   /* @Override
    public void handleCommand(final ZigBeeCommand command) {
        DecimalType colorTemperaturePercentage = DecimalType.ZERO;
        if (command instanceof DecimalType) {
            colorTemperaturePercentage = (DecimalType) command;
        } else if (command instanceof OnOffType) {
            // TODO: Should this turn the lamp on/off?
            return;
        }

        clusterColorControl.moveToColorTemperatureCommand(percentToMired(colorTemperaturePercentage), 10);
    }*/

  @Override
  public boolean acceptEndpoint(ZigBeeEndpoint endpoint, String entityID, EntityContext entityContext, Consumer<String> progressMessage) {
    ZclColorControlCluster clusterColorControl = (ZclColorControlCluster) endpoint.getInputCluster(ZclColorControlCluster.CLUSTER_ID);
    if (clusterColorControl == null) {
      log.trace("[{}]: Color control cluster not found for {}", entityID, endpoint);
      return false;
    }

    Integer capabilities = (Integer) clusterColorControl.getAttribute(ZclColorControlCluster.ATTR_COLORCAPABILITIES)
                                                        .readValue(Long.MAX_VALUE);
    if (capabilities == null && clusterColorControl.getAttribute(ZclColorControlCluster.ATTR_COLORTEMPERATURE)
                                                   .readValue(Long.MAX_VALUE) == null) {
      log.debug("[{}]: Color control color temperature attribute returned null for {}", entityID, endpoint);
      return false;
    }
    if (capabilities != null && (capabilities & ColorCapabilitiesEnum.COLOR_TEMPERATURE.getKey()) == 0) {
      // No support for color temperature
      log.debug("[{}]: Color control color temperature capability not supported for {}", entityID, endpoint);
      return false;
    }

    return true;
  }

  @Override
  public void attributeUpdated(ZclAttribute attribute, Object val) {
    log.debug("[{}]: ZigBee attribute reports {}  on {}", entityID, attribute, endpoint);
    if (attribute.getClusterType() == ZclClusterType.COLOR_CONTROL
        && attribute.getId() == ZclColorControlCluster.ATTR_COLORTEMPERATURE) {

      if (lastColorMode == null || lastColorMode == ColorModeEnum.COLOR_TEMPERATURE) {
        Integer temperatureInMired = (Integer) val;

        DecimalType percent = miredToPercent(temperatureInMired);
        if (percent != null) {
          updateChannelState(percent);
        }
      }
    } else if (attribute.getClusterType() == ZclClusterType.COLOR_CONTROL
        && attribute.getId() == ZclColorControlCluster.ATTR_COLORMODE) {
      Integer colorMode = (Integer) val;
      lastColorMode = ColorModeEnum.getByValue(colorMode);
      if (lastColorMode != ColorModeEnum.COLOR_TEMPERATURE) {
        updateChannelState(null);
      }
    }
  }

  /**
   * Convert color temperature in Mired to Kelvin.
   */
  private int miredToKelvin(int temperatureInMired) {
    return (int) (1e6 / temperatureInMired);
  }

  /**
   * Convert color temperature in Kelvin to Mired.
   */
  private int kelvinToMired(int temperatureInKelvin) {
    return (int) (1e6 / temperatureInKelvin);
  }

  /**
   * Convert color temperature given as percentage to Kelvin.
   */
  private int percentToKelvin(int temperatureInPercent) {
    double value = ((100.0 - temperatureInPercent) * kelvinRange / 100.0) + kelvinMin;
    return (int) (value + 0.5);
  }

  /**
   * Convert color temperature given as percentage to Mired.
   */
  private int percentToMired(int temperatureInPercent) {
    return kelvinToMired(percentToKelvin(temperatureInPercent));
  }

  /**
   * Convert color temperature given in Kelvin to percentage.
   */
  private DecimalType kelvinToPercent(int temperatureInKelvin) {
    double value = 100.0 - (temperatureInKelvin - kelvinMin) * 100.0 / kelvinRange;
    return new DecimalType((int) (value + 0.5));
  }

  /**
   * Convert color temperature given in Mired to percentage.
   */
  private DecimalType miredToPercent(Integer temperatureInMired) {
    if (temperatureInMired == null) {
      return null;
    }
    if (temperatureInMired == 0x0000 || temperatureInMired == 0xffff) {
      // 0x0000 indicates undefined value.
      // 0xffff indicates invalid value (possible due to color mode not being CT).
      return null;
    }
    return kelvinToPercent(miredToKelvin(temperatureInMired));
  }

  private void determineMinMaxTemperature(ZclColorControlCluster serverClusterColorControl) {
    Integer minTemperatureInMired = serverClusterColorControl.getColorTemperatureMin(Long.MAX_VALUE);
    Integer maxTemperatureInMired = serverClusterColorControl.getColorTemperatureMax(Long.MAX_VALUE);

    // High Mired values correspond to low Kelvin values, hence the max Mired value yields the min Kelvin value
    if (maxTemperatureInMired == null) {
      kelvinMin = DEFAULT_MIN_TEMPERATURE_IN_KELVIN;
    } else {
      kelvinMin = miredToKelvin(maxTemperatureInMired);
    }

    // Low Mired values correspond to high Kelvin values, hence the min Mired value yields the max Kelvin value
    if (minTemperatureInMired == null) {
      kelvinMax = DEFAULT_MAX_TEMPERATURE_IN_KELVIN;
    } else {
      kelvinMax = miredToKelvin(minTemperatureInMired);
    }

    kelvinRange = kelvinMax - kelvinMin;
  }
}
