package org.homio.bundle.zigbee.converter.impl;

import static com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType.WINDOW_COVERING;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeCommand;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.clusters.ZclWindowCoveringCluster;
import com.zsmartsystems.zigbee.zcl.clusters.windowcovering.WindowCoveringDownClose;
import com.zsmartsystems.zigbee.zcl.clusters.windowcovering.WindowCoveringUpOpen;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.EntityContextVar.VariableType;

/**
 * Sets the window covering level - supporting open/close and up/down type commands Window Covering Lift Sets the window covering level - supporting open/close
 * and up/down type commands
 */
@ZigBeeConverter(name = "windowcovering_lift", linkType = VariableType.Bool,
                 color = "#CF8E34", category = "Blinds", clientCluster = ZclWindowCoveringCluster.CLUSTER_ID)
public class ZigBeeConverterWindowCoveringLift extends ZigBeeInputBaseConverter<ZclWindowCoveringCluster> {

    public ZigBeeConverterWindowCoveringLift() {
        super(WINDOW_COVERING, ZclWindowCoveringCluster.ATTR_CURRENTPOSITIONLIFTPERCENTAGE);
    }

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint, String entityID, EntityContext entityContext, Consumer<String> progressMessage) {
        if (super.acceptEndpoint(endpoint, entityID, entityContext, progressMessage)) {
            ZclWindowCoveringCluster serverCluster = (ZclWindowCoveringCluster) endpoint
                .getInputCluster(ZclWindowCoveringCluster.CLUSTER_ID);
            try {
                if (serverCluster.discoverCommandsReceived(false).get()) {
                    if (!(serverCluster.getSupportedCommandsReceived().contains(WindowCoveringDownClose.COMMAND_ID)
                        && serverCluster.getSupportedCommandsReceived().contains(WindowCoveringUpOpen.COMMAND_ID))) {
                        log.debug("[{}]: Window covering cluster up/down commands not supported {}",
                            entityID, endpoint.getIeeeAddress());
                        return false;
                    }
                }
            } catch (Exception e) {
                log.warn("[{}]: Exception discovering received commands in window covering cluster {}", entityID, endpoint, e);
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public Future<CommandResult> handleCommand(ZigBeeCommand command) {
        // ZclWindowCoveringCommand zclCommand = null;
        // UpDown MoveStop Percent Refresh
        /*if (command instanceof UpDownType) {
            switch ((UpDownType) command) {
                case UP:
                    zclCommand = new WindowCoveringUpOpen();
                    break;
                case DOWN:
                    zclCommand = new WindowCoveringDownClose();
                    break;
                default:
                    break;
            }
        } else if (command instanceof StopMoveType) {
            switch ((StopMoveType) command) {
                case STOP:
                    zclCommand = new WindowCoveringStop();
                    break;
                default:
                    break;
            }
        } else if (command instanceof PercentType) {
            zclCommand = new WindowCoveringGoToLiftPercentage(((PercentType) command).intValue());
        }

        if (command == null) {
            log.debug("[{}]: Command was not converted - {}", getEndpointEntity(), command);
            return;
        }

        clusterServer.sendCommand(zclCommand);*/
        return null;
    }
}
