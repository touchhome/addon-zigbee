package org.touchhome.bundle.zigbee.util;

import com.zsmartsystems.zigbee.CommandResult;
import java.time.Duration;
import java.util.concurrent.Future;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.ActionResponseModel;

@Log4j2
public final class ZigBeeUtil {

    public static ActionResponseModel toResponseModel(Future<CommandResult> command) {
        try {
            CommandResult commandResult = command.get();
            if (commandResult.isSuccess()) {
                return ActionResponseModel.success();
            } else {
                return ActionResponseModel.showError(
                    "Unable to execute command. Response: [code '" + commandResult.getStatusCode() + "', data: '" + commandResult + "']");
            }
        } catch (Exception ex) {
            return ActionResponseModel.showError(ex);
        }
    }

    public static void zigbeeScanStarted(EntityContext entityContext, String entityID, int duration, Runnable onDurationTimedOutHandler,
        Runnable stopScanHandler) {
        entityContext.ui().headerButtonBuilder("zigbee-scan-" + entityID).title("zigbee.action.stop_scan").border(1, "#899343").clickAction(() -> {
                         stopScanHandler.run();
                         return null;
                     })
                     .duration(duration)
                     .icon("fas fa-search-location", "#899343", false)
                     .build();

        entityContext.bgp().builder("zigbee-scan-killer-" + entityID).delay(Duration.ofSeconds(duration)).execute(() -> {
            log.info("[{}]: Scanning stopped", entityID);
            onDurationTimedOutHandler.run();
            entityContext.ui().removeHeaderButton("zigbee-scan-" + entityID);
        });
    }
}
