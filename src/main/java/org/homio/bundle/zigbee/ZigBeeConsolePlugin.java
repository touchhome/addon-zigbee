package org.homio.bundle.zigbee;

import static org.homio.bundle.api.util.Constants.DANGER_COLOR;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.homio.bundle.zigbee.setting.header.ConsoleHeaderZigBeeDiscoveryButtonSetting;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.console.ConsolePluginTable;
import org.homio.bundle.api.model.ActionResponseModel;
import org.homio.bundle.api.model.HasEntityIdentifier;
import org.homio.bundle.api.model.Status;
import org.homio.bundle.api.setting.console.header.ConsoleHeaderSettingPlugin;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.ui.field.action.UIContextMenuAction;
import org.homio.bundle.api.ui.field.color.UIFieldColorBooleanMatch;
import org.homio.bundle.api.ui.field.color.UIFieldColorStatusMatch;
import org.homio.bundle.api.ui.field.selection.UIFieldSelectValueOnEmpty;
import org.homio.bundle.api.ui.field.selection.UIFieldSelection;
import org.homio.bundle.zigbee.model.ZigBeeDeviceEntity;
import org.homio.bundle.zigbee.service.ZigBeeCoordinatorService;

@RequiredArgsConstructor
public class ZigBeeConsolePlugin
        implements ConsolePluginTable<ZigBeeConsolePlugin.ZigBeeConsoleDescription> {

    @Getter private final EntityContext entityContext;
    private final ZigBeeCoordinatorService coordinatorService;

    @Override
    public int order() {
        return 500;
    }

    @Override
    public boolean isEnabled() {
        return coordinatorService.getEntity().getStatus() == Status.ONLINE;
    }

    @Override
    public String getParentTab() {
        return "zigbee";
    }

    @Override
    public boolean hasRefreshIntervalSetting() {
        return true;
    }

    @Override
    public Collection<ZigBeeConsoleDescription> getValue() {
        List<ZigBeeConsoleDescription> res = new ArrayList<>();
        for (ZigBeeDeviceEntity entity : coordinatorService.getEntity().getDevices()) {
            res.add(
                    new ZigBeeConsoleDescription(
                            entity.getName(),
                            entity.getIeeeAddress(),
                            entity.getStatus(),
                            entity.getStatusMessage(),
                            entity.getModelIdentifier(),
                            entity.getFetchInfoStatus(),
                            !entity.getEndpoints().isEmpty(),
                            entity.getNodeLastUpdateTime(),
                            entity.getEntityID()));
        }
        return res;
    }

    @Override
    public Map<String, Class<? extends ConsoleHeaderSettingPlugin<?>>> getHeaderActions() {
        return Collections.singletonMap(
                "zigbee.start_discovery", ConsoleHeaderZigBeeDiscoveryButtonSetting.class);
    }

    @Override
    public Class<ZigBeeConsoleDescription> getEntityClass() {
        return ZigBeeConsoleDescription.class;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ZigBeeConsoleDescription implements HasEntityIdentifier {

        @UIField(order = 1, inlineEdit = true)
        private String name;

        @UIField(order = 1)
        private String ieeeAddress;

        @UIField(order = 2)
        @UIFieldColorStatusMatch
        private Status deviceStatus;

        @UIField(order = 3, color = DANGER_COLOR)
        private String errorMessage;

        @UIField(order = 4)
        @UIFieldSelection(SelectModelIdentifierDynamicLoader.class)
        @UIFieldSelectValueOnEmpty(label = "zigbee.action.select_model_identifier")
        private String model;

        @UIField(order = 5)
        private Status fetchInfoStatus;

        @UIField(order = 6)
        @UIFieldColorBooleanMatch
        private boolean channelsInitialized;

        @UIField(order = 8)
        private long lastUpdate;

        private String entityID;

        @UIContextMenuAction("zigbee.action.permit_join")
        public ActionResponseModel permitJoin(ZigBeeDeviceEntity zigBeeDeviceEntity) {
            return zigBeeDeviceEntity.permitJoin();
        }
    }
}
