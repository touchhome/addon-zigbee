package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasWdCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import java.util.function.Consumer;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextVar.VariableType;
import org.touchhome.bundle.zigbee.converter.warningdevice.SquawkType;
import org.touchhome.bundle.zigbee.converter.warningdevice.WarningType;

/**
 * Triggers warnings on a warning device Channel converter for warning devices, based on the IAS WD cluster.
 */
@ZigBeeConverter(name = "warning_device", linkType = VariableType.Float,
                 color = "#CF8E34", clientCluster = ZclIasWdCluster.CLUSTER_ID, category = "Siren")
public class ZigBeeConverterWarningDevice extends ZigBeeInputBaseConverter<ZclIasWdCluster> {

  private static final String CONFIG_PREFIX = "zigbee_iaswd_";
  private static final String CONFIG_MAXDURATION = CONFIG_PREFIX + "maxDuration";

  public ZigBeeConverterWarningDevice() {
    super(ZclClusterType.IAS_WD, null);
  }

  @Override
  public void initialize(Consumer<String> progressMessage) {
    if (zclCluster == null) {
      log.debug("[{}]: Initialising {} device cluster {}", entityID, getClass().getSimpleName(), endpoint);
      zclCluster = getInputCluster(ZclIasWdCluster.CLUSTER_ID);
    }
  }

  @Override
  public boolean acceptEndpoint(ZigBeeEndpoint endpoint, String entityID, EntityContext entityContext, Consumer<String> progressMessage) {
    return acceptEndpoint(endpoint, entityID, entityContext, ZclIasWdCluster.CLUSTER_ID,
        0, false, false, progressMessage);
  }

  /*@Override
    public void updateConfiguration( Configuration currentConfiguration,
            Map<String, Object> updatedParameters) {
        for (Entry<String, Object> updatedParameter : updatedParameters.entrySet()) {
            if (updatedParameter.getKey().startsWith(CONFIG_PREFIX)) {
                if (Objects.equals(updatedParameter.getValue(), currentConfiguration.get(updatedParameter.getKey()))) {
                    log.debug("[{}]: Configuration update: Ignored {} as no change", updatedParameter.getKey());
                } else {
                    updateConfigParameter(currentConfiguration, updatedParameter);
                }
            }
        }
    }*/

    /*private void updateConfigParameter(Configuration currentConfiguration, Entry<String, Object> updatedParameter) {
        log.debug("[{}]: Update IAS WD configuration property {}->{} ({})", iasWdCluster.getZigBeeAddress(),
                updatedParameter.getKey(), updatedParameter.getValue(),
                updatedParameter.getValue().getClass().getSimpleName());

        if (CONFIG_MAXDURATION.equals(updatedParameter.getKey())) {
            iasWdCluster.setMaxDuration(((BigDecimal) (updatedParameter.getValue())).intValue());
            Integer response = iasWdCluster.getMaxDuration(0);

            if (response != null) {
                currentConfiguration.put(updatedParameter.getKey(), BigInteger.valueOf(response));
            }
        } else {
            log.warn("[{}]: Unhandled configuration property {}", iasWdCluster.getZigBeeAddress(),
                    updatedParameter.getKey());
        }
    }*/

    /*@Override
    public void handleCommand(final ZigBeeCommand command) {
        if (iasWdCluster == null) {
            log.warn("[{}]: Warning device converter is not linked to a server and cannot accept commands",
                    getEndpointEntity());
            return;
        }

        if (!(command instanceof StringType)) {
            log.warn("[{}]: This converter only supports string-type commands", getEndpointEntity());
            return;
        }

        String commandString = ((StringType) command).stringValue();

        WarningType warningType = WarningType.parse(commandString);
        if (warningType != null) {
            sendWarning(warningType);
        } else {
            SquawkType squawkType = SquawkType.parse(commandString);
            if (squawkType != null) {
                squawk(squawkType);
            } else {
                log.warn("[{}]: Ignoring command that is neither warning nor squawk command: {}",
                        getEndpointEntity(), commandString);
            }
        }
    }*/

  private void sendWarning(WarningType warningType) {
    zclCluster.startWarningCommand(
        makeWarningHeader(warningType.getWarningMode(), warningType.isUseStrobe(), warningType.getSirenLevel()),
        (int) warningType.getDuration().getSeconds());
  }

  private int makeWarningHeader(int warningMode, boolean useStrobe, int sirenLevel) {
    int result = 0;
    result |= warningMode;
    result |= (useStrobe ? 1 : 0) << 4;
    result |= sirenLevel << 6;
    return result;
  }

  private void squawk(SquawkType squawkType) {
    zclCluster.squawk(makeSquawkHeader(squawkType.getSquawkMode(), squawkType.isUseStrobe(), squawkType.getSquawkLevel()));
  }

  private Integer makeSquawkHeader(int squawkMode, boolean useStrobe, int squawkLevel) {
    int result = 0;
    result |= squawkMode;
    result |= (useStrobe ? 1 : 0) << 4;
    result |= squawkLevel << 6;
    return result;
  }

}
