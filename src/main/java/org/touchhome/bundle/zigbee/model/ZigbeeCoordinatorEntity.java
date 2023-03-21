package org.touchhome.bundle.zigbee.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zsmartsystems.zigbee.ExtendedPanId;
import com.zsmartsystems.zigbee.ZigBeeNetworkManager;
import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.app.discovery.ZigBeeDiscoveryExtension;
import com.zsmartsystems.zigbee.app.discovery.ZigBeeNetworkDiscoverer;
import com.zsmartsystems.zigbee.security.ZigBeeKey;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.types.MicroControllerBaseEntity;
import org.touchhome.bundle.api.entity.zigbee.ZigBeeBaseCoordinatorEntity;
import org.touchhome.bundle.api.entity.zigbee.ZigBeeDeviceBaseEntity;
import org.touchhome.bundle.api.entity.zigbee.ZigBeeProperty;
import org.touchhome.bundle.api.exception.ProhibitedExecution;
import org.touchhome.bundle.api.model.ActionResponseModel;
import org.touchhome.bundle.api.model.Status;
import org.touchhome.bundle.api.ui.UISidebarChildren;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;
import org.touchhome.bundle.api.ui.field.action.UIContextMenuAction;
import org.touchhome.bundle.api.ui.field.color.UIFieldColorStatusMatch;
import org.touchhome.bundle.api.ui.field.condition.UIFieldShowOnCondition;
import org.touchhome.bundle.api.ui.field.inline.UIFieldInlineEntities;
import org.touchhome.bundle.api.ui.field.inline.UIFieldInlineEntityWidth;
import org.touchhome.bundle.api.ui.field.selection.UIFieldStaticSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldTreeNodeSelection;
import org.touchhome.bundle.zigbee.handler.CC2531Service;
import org.touchhome.bundle.zigbee.service.ZigBeeCoordinatorService;

@Log4j2
@Entity
@UISidebarChildren(icon = "fas fa-circle-nodes", color = "#D46A06")
public class ZigbeeCoordinatorEntity extends MicroControllerBaseEntity<ZigbeeCoordinatorEntity>
    implements HasNodeDescriptor, ZigBeeBaseCoordinatorEntity<ZigbeeCoordinatorEntity, ZigBeeCoordinatorService> {

    /**
     * Default ZigBeeAlliance09 link key
     */
    public final static ZigBeeKey KEY_ZIGBEE_ALLIANCE_O9 = new ZigBeeKey(new int[]{0x5A, 0x69, 0x67, 0x42, 0x65,
        0x65, 0x41, 0x6C, 0x6C, 0x69, 0x61, 0x6E, 0x63, 0x65, 0x30, 0x39});

    public static final String PREFIX = "zbc_";

    @Getter
    @OneToMany(mappedBy = "parent", fetch = FetchType.EAGER, cascade = CascadeType.REMOVE, orphanRemoval = true)
    private Set<ZigBeeDeviceEntity> devices;

    @UIField(order = 15, hideInEdit = true, hideOnEmpty = true)
    @UIFieldColorStatusMatch
    public Status getDesiredStatus() {
        return optService().map(ZigBeeCoordinatorService::getDesiredStatus).orElse(null);
    }

    @UIField(order = 15, hideInEdit = true, hideOnEmpty = true)
    @UIFieldColorStatusMatch
    public Status getUpdatingStatus() {
        return optService().map(ZigBeeCoordinatorService::getUpdatingStatus).orElse(null);
    }

    @UIField(order = 9999, disableEdit = true)
    @UIFieldInlineEntities(bg = "#27FF000D")
    public List<ZigBeeCoordinatorDeviceEntity> getCoordinatorDevices() {
        return devices == null ? Collections.emptyList() : devices.stream().map(ZigBeeCoordinatorDeviceEntity::new).collect(Collectors.toList());
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public String getPlace() {
        throw new ProhibitedExecution();
    }

    @Override
    public String getDefaultName() {
        return "ZigBee";
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @UIField(order = 2, inlineEdit = true)
    @UIFieldGroup("General")
    public boolean isLogEvents() {
        return getJsonData("le", false);
    }

    public void setLogEvents(boolean value) {
        setJsonData("le", value);
    }

    @UIField(order = 4)
    @UIFieldGroup("General")
    public ZigbeeCoordinator getCoordinatorHandler() {
        return getJsonDataEnum("ch", ZigbeeCoordinator.CC2531Handler);
    }

    public ZigbeeCoordinatorEntity setCoordinatorHandler(ZigbeeCoordinator zigbeeCoordinator) {
        setJsonDataEnum("ch", zigbeeCoordinator);
        return this;
    }

    @UIField(order = 180)
    @UIFieldStaticSelection(value = {"38400", "57600", "115200"})
    @UIFieldGroup("Port")
    public int getPortBaud() {
        return getJsonData("pb", 115200);
    }

    public void setPortBaud(int value) {
        setJsonData("pb", value);
    }

    @UIField(order = 220)
    @UIFieldStaticSelection({"0:None", "1:Hardware (CTS/RTS)", "2:Software (XOn/XOff)"})
    @UIFieldGroup("Port")
    @UIFieldShowOnCondition("return context.get('coordinatorHandler') == 'EmberHandler'")
    public int getFlowControl() {
        return getJsonData("fc", 1);
    }

    public void setFlowControl(int value) {
        setJsonData("fc", value);
    }

    @UIField(order = 1, hideOnEmpty = true)
    @UIFieldTreeNodeSelection(rootPath = "zigbee", allowSelectDirs = true, allowSelectFiles = false)
    @UIFieldGroup(value = "Network", order = 10, borderColor = "#4f8a4e")
    public String getNetworkId() {
        return getJsonData("nid");
    }

    public void setNetworkId(String value) {
        setJsonData("nid", value);
    }

    @UIField(order = 2, hideOnEmpty = true)
    @UIFieldGroup("Network")
    public String getExtendedPanId() {
        return getJsonData("epid", "0000000000000000");
    }

    public void setExtendedPanId(String value) {
        setJsonData("epid", value);
    }

    @UIField(order = 3)
    @UIFieldGroup(value = "Network", order = 10, borderColor = "#4f8a4e")
    public String getNetworkKey() {
        return getJsonData("nk", "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00");
    }

    public void setNetworkKey(String value) {
        setJsonData("nk", value);
    }

    @UIField(order = 4)
    @UIFieldSlider(min = 0, max = 65535)
    @UIFieldGroup("Network")
    public int getPanId() {
        return getJsonData("pid", 65535);
    }

    public void setPanId(int value) {
        setJsonData("pid", value);
    }

    @UIField(order = 5)
    @UIFieldGroup("Network")
    @UIFieldStaticSelection({"11..25;Channel %s"})
    public int getChannelId() {
        return getJsonData("cid", 11);
    }

    public void setChannelId(int value) {
        setJsonData("cid", value);
    }

    @UIField(order = 6, hideOnEmpty = true)
    @UIFieldGroup("Network")
    public String getLinkKey() {
        return getJsonData("lk", "");
    }

    public void setLinkKey(String value) {
        setJsonData("lk", value);
    }

    @UIField(order = 2)
    @UIFieldGroup("Discovery")
    public boolean isJoinDeviceDuringScanOnly() {
        return getJsonData("jddso", false);
    }

    public void setJoinDeviceDuringScanOnly(boolean value) {
        setJsonData("jddso", value);
    }

    @UIField(order = 3)
    @UIFieldStaticSelection({"0:Never", "300:5 Minutes", "1800:30 Minutes", "3600:1 Hour", "21600:6 Minutes",
        "86400:1 Day", "604800:1 Week"})
    @UIFieldGroup("Discovery")
    public int getMeshUpdatePeriod() {
        return getJsonData("mup", 86400);
    }

    public void setMeshUpdatePeriod(int value) {
        setJsonData("mup", value);
    }

    @UIField(order = 1)
    @UIFieldStaticSelection({"-1:None", "0:Deny", "1:Insecure", "2:Secure", "3:InstallCode"})
    @UIFieldGroup(value = "Dongle", order = 20, borderColor = "#3E7792")
    public int getTrustCentreJoinMode() {
        return getJsonData("tc", -1);
    }

    public void setTrustCentreJoinMode(int value) {
        setJsonData("tc", value);
    }

    @UIField(order = 2)
    @UIFieldSlider(min = 0, max = 8)
    @UIFieldGroup("Dongle")
    public int getTxPower() {
        return getJsonData("txp", 0);
    }

    public void setTxPower(int value) {
        setJsonData("txp", value);
    }

    @UIField(order = 3, hideOnEmpty = true)
    @UIFieldGroup("Dongle")
    public String getInstallCode() {
        return getJsonData("ic", "");
    }

    public void setInstallCode(String value) {
        setJsonData("ic", value);
    }

    @UIField(order = 4)
    @UIFieldStaticSelection(value = {"0:Normal", "1:Boost"})
    @UIFieldGroup("Dongle")
    @UIFieldShowOnCondition("return context.get('coordinatorHandler') == 'EmberHandler'")
    public int getPowerMode() {
        return getJsonData("pm", 1);
    }

    public void setPowerMode(int value) {
        setJsonData("pm", value);
    }

    @UIField(hideInEdit = true, order = 100, hideOnEmpty = true)
    @UIFieldGroup("Node")
    public String getLocalIeeeAddress() {
        return getJsonData("lia");
    }

    public void setLocalIeeeAddress(String value) {
        setJsonData("lia", value);
    }

    @UIContextMenuAction(value = "zigbee.action.start_scan", icon = "fas fa-search-location", iconColor = "#899343")
    public ActionResponseModel scan() {
        getService().getDiscoveryService().startScan();
        return ActionResponseModel.showSuccess("SUCCESS");
    }

    @Override
    protected void beforePersist() {
        fixEntity();
    }

    @Override
    protected void beforeUpdate() {
        fixEntity();
    }

    @JsonIgnore
    public Set<ZigBeeDeviceEntity> getOnlineDevices() {
        return getDevices().stream().filter(d -> d.getStatus() == Status.ONLINE).collect(Collectors.toSet());
    }

    @Override
    public Class<ZigBeeCoordinatorService> getEntityServiceItemClass() {
        return ZigBeeCoordinatorService.class;
    }

    @Override
    public @NotNull ZigBeeCoordinatorService createService(@NotNull EntityContext entityContext) {
        return getCoordinatorHandler().coordinatorSupplier.apply(entityContext, this);
    }

    public ZigbeeCoordinatorEntity nodeUpdated(ZigBeeNode node, EntityContext entityContext) {
        setFetchInfoStatus(Status.RUNNING, null);
        boolean requireSave = this.updateFromNodeDescriptor(node);
        setFetchInfoStatus(Status.ONLINE, null);
        if (requireSave) {
            return entityContext.save(this);
        }
        return this;
    }

    private void fixEntity() {
        // fix network id
        setNetworkId(StringUtils.defaultIfEmpty(getNetworkId(), UUID.randomUUID().toString()));

        // fix network key
        ZigBeeKey zigBeeKey;
        try {
            zigBeeKey = new ZigBeeKey(getNetworkKey());
        } catch (IllegalArgumentException e) {
            zigBeeKey = new ZigBeeKey();
            log.debug("[{}]: Network Key String has invalid format. Revert to default key [{}]", getEntityID(), getNetworkKey());
        }
        if (!zigBeeKey.isValid()) {
            zigBeeKey = ZigBeeKey.createRandom();
            log.debug("[{}]: Network key initialized {}", getEntityID(), zigBeeKey);
        }
        setNetworkKey(zigBeeKey.toString());

        // fix pan id
        if (getPanId() == 0) {
            setPanId((int) Math.floor((Math.random() * 65534)));
            log.debug("[{}]: Create random ZigBee PAN ID [{}]", getEntityID(), String.format("%04X", getPanId()));
        }

        // fix extended pan id
        if (StringUtils.isNotEmpty(getExtendedPanId())) {
            ExtendedPanId extendedPanId = new ExtendedPanId(getExtendedPanId());
            if (!extendedPanId.isValid()) {
                int[] pan = new int[8];
                for (int cnt = 0; cnt < 8; cnt++) {
                    pan[cnt] = (int) Math.floor((Math.random() * 255));
                }
                extendedPanId = new ExtendedPanId(pan);
                log.debug("[{}]: Created random ZigBee extended PAN ID [{}]", getEntityID(), extendedPanId);
                setExtendedPanId(extendedPanId.toString());
            }
        }

        // fix link key
        try {
            new ZigBeeKey(getLinkKey());
        } catch (IllegalArgumentException e) {
            setLinkKey(KEY_ZIGBEE_ALLIANCE_O9.toString());
            log.debug("[{}]: Link Key String has invalid format. Revert to default key", getEntityID());
        }
    }

    @Override
    public String toString() {
        return "ZigBee coordinator '" + getTitle() + "' [" + getCoordinatorHandler().getName() + "]";
    }

    @Override
    public void logBuilder(EntityLogBuilder entityLogBuilder) {
        entityLogBuilder.addTopicFilterByEntityID("org.touchhome.bundle.zigbee");
        entityLogBuilder.addTopic(ZigBeeNetworkManager.class);
        entityLogBuilder.addTopic(ZigBeeDiscoveryExtension.class);
        entityLogBuilder.addTopic(ZigBeeNetworkDiscoverer.class);
    }

    @Override
    public void getAllRelatedEntities(Set<BaseEntity> relations) {
        super.getAllRelatedEntities(relations);
        if (devices != null) {
            relations.addAll(devices);
            for (ZigBeeDeviceEntity device : devices) {
                device.getAllRelatedEntities(relations);
            }
        }
    }

    @Override
    public @NotNull Map<String, Map<String, ? extends ZigBeeProperty>> getCoordinatorTree() {
        throw new NotImplementedException();
    }

    @Override
    public @NotNull Collection<ZigBeeDeviceBaseEntity> getZigBeeDevices() {
        throw new NotImplementedException();
    }

    @Override
    public @Nullable ZigBeeDeviceBaseEntity getZigBeeDevice(@NotNull String ieeeAddress) {
        throw new NotImplementedException();
    }

    @RequiredArgsConstructor
    public enum ZigbeeCoordinator {
        CC2531Handler("CC2531", (entityContext, entity) -> new CC2531Service(entityContext, entity));

        @Getter
        private final String name;
        private final BiFunction<EntityContext, ZigbeeCoordinatorEntity, ZigBeeCoordinatorService> coordinatorSupplier;
    }

    @Getter
    @NoArgsConstructor
    private static class ZigBeeCoordinatorDeviceEntity {

        @UIField(order = 1)
        @UIFieldInlineEntityWidth(35)
        private String ieeeAddress;

        @UIField(order = 2)
        private String name;

        @UIField(order = 3)
        @UIFieldColorStatusMatch
        @UIFieldInlineEntityWidth(15)
        private Status status;

        @UIField(order = 4)
        @UIFieldInlineEntityWidth(10)
        private int endpointsCount;

        public ZigBeeCoordinatorDeviceEntity(ZigBeeDeviceEntity device) {
            this.ieeeAddress = device.getIeeeAddress();
            this.name = device.getName();
            this.status = device.getStatus();
            Set<ZigBeeEndpointEntity> endpoints = device.getEndpoints();
            this.endpointsCount = endpoints == null ? 0 : endpoints.size();
        }
    }
}
