package org.touchhome.bundle.zigbee.model;

import static java.lang.String.format;
import static org.touchhome.bundle.api.model.ActionResponseModel.showWarn;
import static org.touchhome.bundle.api.model.ActionResponseModel.success;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pivovarit.function.ThrowingFunction;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextSetting;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.entity.HasStatusAndMsg;
import org.touchhome.bundle.api.entity.PinBaseEntity;
import org.touchhome.bundle.api.model.ActionResponseModel;
import org.touchhome.bundle.api.model.Status;
import org.touchhome.bundle.api.service.EntityService;
import org.touchhome.bundle.api.state.State;
import org.touchhome.bundle.api.ui.UI.Color;
import org.touchhome.bundle.api.ui.field.ProgressBar;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldNumber;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.action.HasDynamicContextMenuActions;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.item.UIInfoItemBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.layout.UIFlexLayoutBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.layout.UILayoutBuilder;
import org.touchhome.bundle.api.ui.field.color.UIFieldColorStatusMatch;
import org.touchhome.bundle.api.ui.field.condition.UIFieldShowOnCondition;
import org.touchhome.bundle.api.ui.field.selection.UIFieldStaticSelection;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter.AttributeDescription;
import org.touchhome.bundle.zigbee.converter.config.ZclDoorLockConfig;
import org.touchhome.bundle.zigbee.converter.config.ZclFanControlConfig;
import org.touchhome.bundle.zigbee.converter.config.ZclLevelControlConfig;
import org.touchhome.bundle.zigbee.converter.config.ZclOnOffSwitchConfig;
import org.touchhome.bundle.zigbee.service.ZigbeeEndpointService;
import org.touchhome.bundle.zigbee.util.DeviceConfiguration;
import org.touchhome.bundle.zigbee.util.DeviceConfiguration.EndpointDefinition;
import org.touchhome.bundle.zigbee.util.DeviceConfigurations;
import org.touchhome.bundle.api.util.Lang;

@Log4j2
@Entity
@Setter
@Getter
@Accessors(chain = true)
public class ZigBeeEndpointEntity extends PinBaseEntity<ZigBeeEndpointEntity, ZigBeeDeviceEntity>
    implements HasJsonData, HasStatusAndMsg<ZigBeeEndpointEntity>,
    EntityService<ZigbeeEndpointService, ZigBeeEndpointEntity>, HasDynamicContextMenuActions {

    // uses for changes inside cluster configuration to mark that entity has to be saved
    @JsonIgnore private transient boolean outdated;

    @JsonIgnore
    public String getVariableId() {
        return getIeeeAddress() + "-" + getAddress() + "-" + getClusterName();
    }

    @JsonIgnore
    public String getVariableName() {
        return getName();
    }

    @JsonIgnore
    public String getVariableDescription() {
        return format("EP[%s] ${%s} [%s]", getAddress(), getOwnerTarget().getName(), getIeeeAddress());
    }

    @Override
    public ZigBeeEndpointEntity setStatus(@NotNull Status status, @Nullable String msg) {
        EntityContextSetting.setStatus(this, "", "Status", status, msg);
        getEntityContext().ui().updateInnerSetItem(getOwnerTarget(), "endpointClusters", this.getEntityID(), "status", status);
        return this;
    }

    @Override
    @UIField(order = 10, disableEdit = true)
    @UIFieldColorStatusMatch
    public Status getStatus() {
        return EntityContextSetting.getStatus(this, "", Status.UNKNOWN);
    }

    @UIField(order = 10, disableEdit = true)
    @UIFieldColorStatusMatch
    public Status getBindStatus() {
        return optService().map(service -> service.getCluster().getBindStatus()).orElse(Status.UNKNOWN);
    }

    @Override
    @UIField(order = 11, hideInView = true, disableEdit = true, hideOnEmpty = true)
    public String getStatusMessage() {
        return EntityContextSetting.getMessage(this, "");
    }

    @UIField(order = 2, disableEdit = true)
    @UIFieldGroup(value = "General", order = 1, borderColor = "#317175")
    public int getClusterId() {
        return getJsonData().getInt("c_id");
    }

    public ZigBeeEndpointEntity setClusterId(int clusterId) {
        setJsonData("c_id", clusterId);
        return this;
    }

    @Override
    @UIField(order = 3, disableEdit = true, label = "endpointId")
    @UIFieldGroup("General")
    public int getAddress() {
        return super.getAddress();
    }

    @Override
    @UIField(order = 4)
    @UIFieldGroup("General")
    public String getName() {
        if (StringUtils.isEmpty(super.getName())) {
            return "zigbee.endpoint.name." + getValueFromConfiguration();
        }
        return super.getName();
    }

    @UIField(order = 5, disableEdit = true, type = UIFieldType.Duration)
    @UIFieldGroup("General")
    public long getLastAnswerFromEndpoint() {
        return EntityContextSetting.getMemValue(this, "lafe", 0L);
    }

    public void setLastAnswerFromEndpoint(long currentTimeMillis) {
        EntityContextSetting.setMemValue(this, "lafe", "LastAnswerFromEndpoint", currentTimeMillis);
        getEntityContext().ui().updateInnerSetItem(getOwnerTarget(), "endpointClusters", this.getEntityID(), "updated", currentTimeMillis);
    }

    @UIField(order = 5, disableEdit = true, hideOnEmpty = true)
    @UIFieldGroup("General")
    @SuppressWarnings("unused")
    public Integer getFailedPollRequests() {
        return optService().map(ZigbeeEndpointService::getFailedPollRequests).orElse(null);
    }

    @UIField(order = 6, disableEdit = true)
    @UIFieldGroup("General")
    public String getValue() {
        return Optional.ofNullable(getLastState()).map(State::toString).orElse("");
    }

    public void setValue(State state) {
        EntityContextSetting.setMemValue(this, "last", "Value", state);
        getEntityContext().ui().updateInnerSetItem(getOwnerTarget(), "endpointClusters", this.getEntityID(), "value", state.toString());
    }

    @JsonIgnore
    public State getLastState() {
        return EntityContextSetting.getMemValue(this, "last", null);
    }

    @UIField(order = 7, hideInView = true, disableEdit = true)
    @UIFieldGroup("General")
    public String getDescription() {
        if (StringUtils.isEmpty(super.getDescription())) {
            return "zigbee.endpoint.description." + getValueFromConfiguration();
        }
        return super.getDescription();
    }

    @UIField(order = 8, disableEdit = true)
    @UIFieldGroup("General")
    public String getClusterName() {
        return getJsonData().getString("c_name");
    }

    public ZigBeeEndpointEntity setClusterName(String clusterName) {
        setJsonData("c_name", clusterName);
        return this;
    }

    public String getIeeeAddress() {
        return getJsonData().getString("ieee");
    }

    public ZigBeeEndpointEntity setIeeeAddress(String value) {
        setJsonData("ieee", value);
        return this;
    }

    // The minimum time period in seconds between device state updates
    @UIField(order = 100)
    @UIFieldShowOnCondition("return context.get('supportReporting') == 'true'")
    @UIFieldNumber(min = 1, max = 86400)
    @UIFieldGroup(value = "Reporting", order = 2, borderColor = "#517531")
    public int getReportingTimeMin() {
        return getJsonData("rt_min", 1);
    }

    public boolean setReportingTimeMin(int value) {
        if (getReportingTimeMin() != value) {
            setJsonData("rt_min", value);
            return true;
        }
        return false;
    }

    // The maximum time period in seconds between device state updates
    @UIField(order = 101)
    @UIFieldShowOnCondition("return context.get('supportReporting') == 'true'")
    @UIFieldNumber(min = 1, max = 86400)
    @UIFieldGroup("Reporting")
    public int getReportingTimeMax() {
        return getJsonData("rt_max", 900);
    }

    public boolean setReportingTimeMax(int value) {
        if (getReportingTimeMax() != value) {
            setJsonData("rt_max", value);
            return true;
        }
        return false;
    }

    @UIField(order = 102)
    @UIFieldShowOnCondition("return context.get('supportAnalogue') == 'true'") // is analogue is true, then 'supportReporting' also true
    @UIFieldNumber(minRef = "reportingChangeMin", maxRef = "reportingChangeMax")
    @UIFieldGroup("Reporting")
    public @Nullable Double getReportingChange() {
        return isSupportAnalogue() ? getJsonData("rt_ch", Double.class) : null;
    }

    public boolean setReportingChange(Number value) {
        if (getReportingChange() != null && getReportingChange() != value.doubleValue()) {
            setJsonData("rt_ch", value.doubleValue());
            return true;
        }
        return false;
    }

    @UIField(order = 103)
    @UIFieldShowOnCondition("return context.get('supportReporting') == 'true'")
    @UIFieldNumber(min = 15, max = 86400)
    @UIFieldGroup("Reporting")
    public int getPollingPeriod() {
        return getJsonData("pp", 7200);
    }

    public boolean setPollingPeriod(Integer value) {
        if (value != null && value != getPollingPeriod()) {
            setJsonData("pp", value);
            return true;
        }
        return false;
    }

    // options.add(new ParameterOption("65535", "Use On/Off times"));
    @UIField(order = 200)
    @UIFieldShowOnCondition("return context.get('supportLevelControl') == 'true'")
    @UIFieldNumber(min = 0, max = 60000)
    @UIFieldGroup(value = "LevelControl", order = 3, borderColor = "#35786f")
    public int getDefaultTransitionTime() {
        return getJsonData("ttc", 0);
    }

    public boolean setDefaultTransitionTime(int value) {
        if (getDefaultTransitionTime() != value) {
            setJsonData("ttc", value);
            return true;
        }
        return false;
    }

    @UIField(order = 201)
    @UIFieldShowOnCondition("return context.get('supportLevelControl') == 'true'")
    @UIFieldGroup("LevelControl")
    public boolean getInvertLevelControl() {
        return getJsonData("ilc", false);
    }

    public boolean setInvertLevelControl(boolean value) {
        if (getInvertLevelControl() != value) {
            setJsonData("ilc", value);
            return true;
        }
        return false;
    }

    @UIField(order = 202)
    @UIFieldShowOnCondition("return context.get('supportLevelControl') == 'true'")
    @UIFieldGroup("LevelControl")
    public boolean getInvertReportControl() {
        return getJsonData("irc", false);
    }

    public boolean setInvertReportControl(boolean value) {
        if (getInvertReportControl() != value) {
            setJsonData("irc", value);
            return true;
        }
        return false;
    }

    @UIField(order = 203)
    @UIFieldShowOnCondition("return context.get('supportOnOffTransitionTime') == 'true'")
    @UIFieldNumber(min = 0, max = 60000)
    @UIFieldGroup("LevelControl")
    public int getOnOffTransitionTime() {
        return getJsonData("onOffTT", 0);
    }

    public boolean setOnOffTransitionTime(int value) {
        if (getOnOffTransitionTime() != value) {
            setJsonData("onOffTT", value);
            return true;
        }
        return false;
    }

    // options.add(new ParameterOption("65535", "Use On/Off transition time"));
    @UIField(order = 204)
    @UIFieldShowOnCondition("return context.get('supportOnTransitionTime') == 'true'")
    @UIFieldNumber(min = 0, max = 60000)
    @UIFieldGroup("LevelControl")
    public int getOnTransitionTime() {
        return getJsonData("onTT", 65535);
    }

    public boolean setOnTransitionTime(int value) {
        if (getOnTransitionTime() != value) {
            setJsonData("onTT", value);
            return true;
        }
        return false;
    }

    // options.add(new ParameterOption("65535", "Use On/Off transition time"));
    @UIField(order = 205)
    @UIFieldShowOnCondition("return context.get('supportOffTransitionTime') == 'true'")
    @UIFieldNumber(min = 0, max = 60000)
    @UIFieldGroup("LevelControl")
    public int getOffTransitionTime() {
        return getJsonData("offTT", 65535);
    }

    public boolean setOffTransitionTime(int value) {
        if (getOffTransitionTime() != value) {
            setJsonData("offTT", value);
            return true;
        }
        return false;
    }

    // options.add(new ParameterOption("255", "Not Set"));
    @UIField(order = 206)
    @UIFieldShowOnCondition("return context.get('supportOnLevel') == 'true'")
    @UIFieldNumber(min = 0, max = 60000)
    @UIFieldGroup("LevelControl")
    public int getOnLevel() {
        return getJsonData("onLvl", 255);
    }

    public boolean setOnLevel(int value) {
        if (getOnLevel() != value) {
            setJsonData("onLvl", value);
            return true;
        }
        return false;
    }

    // options.add(new ParameterOption("255", "Not Set"));
    @UIField(order = 207)
    @UIFieldShowOnCondition("return context.get('supportDefaultMoveRate') == 'true'")
    @UIFieldNumber(min = 0, max = 60000)
    @UIFieldGroup("LevelControl")
    public int getDefaultMoveRate() {
        return getJsonData("defMoveRate", 255);
    }

    public boolean setDefaultMoveRate(int value) {
        if (getDefaultMoveRate() != value) {
            setJsonData("defMoveRate", value);
            return true;
        }
        return false;
    }

    @UIField(order = 300)
    @UIFieldShowOnCondition("return context.get('supportOffWaitTime') == 'true'")
    @UIFieldNumber(min = 0, max = 60000)
    @UIFieldGroup(value = "OnOffSwitch", order = 20, borderColor = "#B58A35")
    public int getOffWaitTime() {
        return getJsonData("offWaitTime", 0);
    }

    public boolean setOffWaitTime(int value) {
        if (getOffWaitTime() != value) {
            setJsonData("offWaitTime", value);
            return true;
        }
        return false;
    }

    @UIField(order = 301)
    @UIFieldShowOnCondition("return context.get('supportOnTime') == 'true'")
    @UIFieldNumber(min = 0, max = 60000)
    @UIFieldGroup("OnOffSwitch")
    public int getOnTime() {
        return getJsonData("onTime", 65535);
    }

    public boolean setOnTime(int value) {
        if (getOnTime() != value) {
            setJsonData("onTime", value);
            return true;
        }
        return false;
    }

    @UIField(order = 302)
    @UIFieldShowOnCondition("return context.get('supportStartupOnOff') == 'true'")
    @UIFieldGroup("OnOffSwitch")
    public boolean getStartupOnOff() {
        return getJsonData("startupOnOff", false);
    }

    public boolean setStartupOnOff(boolean value) {
        if (getStartupOnOff() != value) {
            setJsonData("startupOnOff", value);
            return true;
        }
        return false;
    }

    @UIField(order = 400)
    @UIFieldShowOnCondition("return context.get('supportFanModeSequence') == 'true'")
    @UIFieldStaticSelection({
        "0:Low/Med/High",
        "1:Low/High",
        "2:Low/Med/High/Auto",
        "3:Low/High/Auto",
        "4:On/Auto"
    })
    @UIFieldGroup("Fan")
    public int getFanModeSequence() {
        return getJsonData("fanModeSeq", 4);
    }

    public void setFanModeSequence(int value) {
        setJsonData("fanModeSeq", value);
    }

    /**
     * TODO: case 0: options.add(new StateOption("1", "Low")); options.add(new StateOption("2",
     * "Medium")); options.add(new StateOption("3", "High")); case 1: options.add(new StateOption("1",
     * "Low")); options.add(new StateOption("3", "High")); break; case 2: options.add(new
     * StateOption("1", "Low")); options.add(new StateOption("2", "Medium")); options.add(new
     * StateOption("3", "High")); options.add(new StateOption("5", "Auto")); break; case 3:
     * options.add(new StateOption("1", "Low")); options.add(new StateOption("3", "High"));
     * options.add(new StateOption("5", "Auto")); break; case 4: options.add(new StateOption("4",
     * "On")); options.add(new StateOption("5", "Auto")); break;
     */
    @UIField(order = 500)
    @UIFieldStaticSelection({"0:Silent", "1:Low", "2:High"})
    @UIFieldShowOnCondition("return context.get('supportSoundVolume') == 'true'")
    @UIFieldGroup("DoorLock")
    public int getSoundVolume() {
        return getJsonData("dl_sv", 4);
    }

    public void setSoundVolume(int value) {
        setJsonData("dl_sv", value);
    }

    @UIField(order = 501)
    @UIFieldSlider(min = 0, max = 3600)
    @UIFieldShowOnCondition("return context.get('supportAutoRelockTime') == 'true'")
    @UIFieldGroup("DoorLock")
    public int getEnableAutoRelockTime() {
        return getJsonData("dl_art", 0);
    }

    public void setEnableAutoRelockTime(int value) {
        setJsonData("dl_art", value);
    }

    @UIField(order = 502)
    @UIFieldShowOnCondition("return context.get('supportLocalProgramming') == 'true'")
    @UIFieldGroup("DoorLock")
    public boolean getEnableLocalProgramming() {
        return getJsonData("dl_lp", false);
    }

    public void setEnableLocalProgramming(boolean value) {
        setJsonData("dl_lp", value);
    }

    @UIField(order = 503)
    @UIFieldShowOnCondition("return context.get('supportEnableOneTouchLocking') == 'true'")
    @UIFieldGroup("DoorLock")
    public boolean getEnableOneTouchLocking() {
        return getJsonData("dl_otl", false);
    }

    public void setEnableOneTouchLocking(boolean value) {
        setJsonData("dl_otl", value);
    }

    @UIField(order = 600)
    @UIFieldShowOnCondition("return context.get('supportColorControl') == 'true'")
    @UIFieldGroup("ColorControl")
    public ControlMethod getColorControlMethod() {
        return getJsonDataEnum("cc_ccm", ControlMethod.AUTO);
    }

    @SuppressWarnings("unused")
    public void setColorControlMethod(ControlMethod value) {
        setJsonDataEnum("cc_ccm", value);
    }

    // configurable by cluster if analogue is true
    public int getReportingChangeMin() {
        return getJsonData("rt_ch_min", 0);
    }

    // configurable by cluster if analogue is true
    public int getReportingChangeMax() {
        return getJsonData("rt_ch_max", 0);
    }

    @SuppressWarnings("unused")
    public boolean isSupportColorControl() {
        return optService().map(s -> s.getCluster().isSupportConfigColorControl()).orElse(false);
    }

    public boolean isSupportOffWaitTime() {
        return optService()
            .map(service -> Optional.ofNullable(service.getCluster().getConfigOnOff())
                                    .map(ZclOnOffSwitchConfig::isSupportOffWaitTime)
                                    .orElse(false))
            .orElse(false);
    }

    public boolean isSupportOnTime() {
        return optService()
            .map(service -> Optional.ofNullable(service.getCluster().getConfigOnOff())
                                    .map(ZclOnOffSwitchConfig::isSupportOnTime)
                                    .orElse(false))
            .orElse(false);
    }

    public boolean isSupportStartupOnOff() {
        return optService()
            .map(service ->
                Optional.ofNullable(service.getCluster().getConfigOnOff())
                        .map(ZclOnOffSwitchConfig::isSupportStartupOnOff)
                        .orElse(false))
            .orElse(false);
    }

    @SuppressWarnings("unused")
    public boolean isSupportFanModeSequence() {
        return optService()
            .map(service -> Optional.ofNullable(service.getCluster().getConfigFanControl())
                                    .map(ZclFanControlConfig::isSupportFanModeSequence)
                                    .orElse(false))
            .orElse(false);
    }

    @SuppressWarnings("unused")
    public boolean isSupportSoundVolume() {
        return optService()
            .map(service -> Optional.ofNullable(service.getCluster().getConfigDoorLock())
                                    .map(ZclDoorLockConfig::isSupportSoundVolume)
                                    .orElse(false))
            .orElse(false);
    }

    @SuppressWarnings("unused")
    public boolean isSupportAutoRelockTime() {
        return optService()
            .map(service -> Optional.ofNullable(service.getCluster().getConfigDoorLock())
                                    .map(ZclDoorLockConfig::isSupportAutoRelockTime)
                                    .orElse(false))
            .orElse(false);
    }

    @SuppressWarnings("unused")
    public boolean isSupportEnableOneTouchLocking() {
        return optService()
            .map(service -> Optional.ofNullable(service.getCluster().getConfigDoorLock())
                                    .map(ZclDoorLockConfig::isSupportEnableOneTouchLocking)
                                    .orElse(false))
            .orElse(false);
    }

    @SuppressWarnings("unused")
    public boolean isSupportLocalProgramming() {
        return optService()
            .map(service -> Optional.ofNullable(service.getCluster().getConfigDoorLock())
                                    .map(ZclDoorLockConfig::isSupportLocalProgramming)
                                    .orElse(false))
            .orElse(false);
    }

    public boolean isSupportAnalogue() {
        return getJsonData("analogue", false);
    }

    public boolean isSupportLevelControl() {
        return optService().map(s -> s.getCluster().getConfigLevelControl() != null).orElse(false);
    }

    public boolean isSupportReporting() {
        return optService().map(s -> s.getCluster().getConfigReporting() != null).orElse(false);
    }

    public boolean isSupportOnOffTransitionTime() {
        return optService()
            .map(service -> Optional.ofNullable(service.getCluster().getConfigLevelControl())
                                    .map(ZclLevelControlConfig::isSupportOnOffTransitionTime)
                                    .orElse(false))
            .orElse(false);
    }

    public boolean isSupportOnTransitionTime() {
        return optService()
            .map(service -> Optional.ofNullable(service.getCluster().getConfigLevelControl())
                                    .map(ZclLevelControlConfig::isSupportOnTransitionTime)
                                    .orElse(false))
            .orElse(false);
    }

    public boolean isSupportOffTransitionTime() {
        return optService()
            .map(service -> Optional.ofNullable(service.getCluster().getConfigLevelControl())
                                    .map(ZclLevelControlConfig::isSupportOffTransitionTime)
                                    .orElse(false))
            .orElse(false);
    }

    public boolean isSupportOnLevel() {
        return optService()
            .map(service -> Optional.ofNullable(service.getCluster().getConfigLevelControl())
                                    .map(ZclLevelControlConfig::isSupportOnLevel)
                                    .orElse(false))
            .orElse(false);
    }

    public boolean isSupportDefaultMoveRate() {
        return optService()
            .map(service -> Optional.ofNullable(service.getCluster().getConfigLevelControl())
                                    .map(ZclLevelControlConfig::isSupportDefaultMoveRate)
                                    .orElse(false))
            .orElse(false);
    }

    @Override
    public @NotNull Class<ZigbeeEndpointService> getEntityServiceItemClass() {
        return ZigbeeEndpointService.class;
    }

    @Override
    public ZigbeeEndpointService createService(@NotNull EntityContext entityContext) {
        return null;
    }

    @Override
    public String toString() {
        return "[ieeeAddress='" + getIeeeAddress() + "', clusterId=" + getClusterId() + ", endpointId=" + getAddress() + ", clusterName='" + getClusterName() + "']";
    }

    public void setAnalogue(Double defaultChange, Integer minimumChange, Integer maximumChange) {
        setJsonData("analogue", true);
        if (minimumChange != getReportingChangeMin()) {
            setJsonData("rt_ch_min", minimumChange);
        }
        if (maximumChange != getReportingChangeMax()) {
            setJsonData("rt_ch_max", maximumChange);
        }
        if (getReportingChange() == null || defaultChange != getReportingChange().doubleValue()) {
            setReportingChange(defaultChange);
        }
    }

    @Override
    public void assembleActions(UIInputBuilder builder) {
        this.addStatusInfo(builder, getBindStatus());

        optService().ifPresent(service -> {
            ZigBeeBaseChannelConverter cluster = service.getCluster();

            // add general info
            this.addText(builder, "field.minPollingInterval", cluster.getMinPollingInterval() / 60 + "min.");
            this.addText(builder, "field.failedPollRequests", service.getFailedPollRequests() + "/" + service.getMaxFailedPollRequests());

            long lastPollRequest = Duration.ofMillis(System.currentTimeMillis() - service.getLastPollRequest()).toMinutes();
            this.addText(builder, "field.lastPollRequest", lastPollRequest + "min. ago");

            long nextPoll = Duration.ofSeconds(cluster.getMinPollingInterval()).minusMillis(System.currentTimeMillis() -
                service.getLastPollRequest()).toMinutes();
            this.addText(builder, "field.expectedNextPollRequest", "in " + nextPoll + "min.");

            if (cluster.getBindStatusMsg() != null) {
                this.addText(builder, "field.bindErrorMessage", cluster.getBindStatusMsg())
                    .setColor(cluster.getBindStatus() == Status.OFFLINE ? Color.PRIMARY_COLOR : Color.RED).appendStyle("max-width", "200px");
                builder.addSelectableButton("zigbee.action.rebind", "fas fa-litecoin-sign", "#3961B7",
                    (entityContext, params) -> {
                        boolean success = entityContext.ui().runWithProgressAndGet("bind-" + getEntityID(), false,
                            progressBar -> cluster.tryBind(), null);
                        return success ? success() : showWarn("ACTION.NOT_SUCCESS");
                    });
            }

            if (!isSupportReporting()) {
                builder.addFlex("pp", flex -> {
                    Integer pollingPeriod = cluster.getPollingPeriod();
                    addText(flex, "field.pollingPeriod", pollingPeriod == null ? "Not Set" : pollingPeriod / 60 + "min.");
                });
            }

            builder.addSelectableButton("zigbee.action.poll_values", "fas fa-download", "#A939B7",
                (entityContext, params) -> {
                    entityContext.ui().runWithProgress("poll-" + getEntityID(), false,
                        progressBar -> cluster.fireRefreshAttribute(message -> progressBar.progress(0, message)), null);
                    return success();
                });
            builder.addSelectableButton("zigbee.action.read_attributes", "fas fa-satellite-dish", "#39AEB7",
                (entityContext, params) -> {
                    List<AttributeDescription> attributes = entityContext.ui().runWithProgressAndGet("read-attr-" + getEntityID(), false,
                        cluster::readAllAttributes, null);
                    return ActionResponseModel.showJson(Lang.getServerMessage("zigbee.attributes", "NAME", cluster.getName()), attributes);
                });

            assembleReportConfigActions(builder);
            assembleLevelControlActions(builder);
            assembleOnOfSwitchActions(builder);

            // TODO: not all actions are assembled!!! i.e. fan, door lock, etc...

            cluster.assembleActions(builder);
        });
    }

    private void addNumber(UILayoutBuilder builder, String name, String infoName, int value, int min, int max, BiFunction<Integer, EntityContext, ActionResponseModel> handler) {
        builder.addFlex(name, flex -> {
            flex.addInfo(infoName).appendStyle("min-width", "200px");
            flex.addNumberInput(name + "_input", (float) value, (float) min, (float) max, (entityContext, params) ->
                    handler.apply(params.getInt("value"), entityContext)).appendStyle("width", "100px")
                .setTitle(format("Range '%s..%s'", min, max));
        });
    }

    private void addNumberWithButton(UILayoutBuilder builder, String name, String infoName, String btnName, int btnValue,
        int value, BiFunction<Integer, EntityContext, ActionResponseModel> handler) {
        builder.addFlex(name, flex -> {
            flex.addInfo(infoName).appendStyle("min-width", "200px");
            flex.addButton(btnName, null, null, (entityContext, params) -> handler.apply(btnValue, entityContext));
            flex.addNumberInput(name + "_input", (float) value, (float) 0, 60000F, (entityContext, params) ->
                handler.apply(params.getInt("value"), entityContext)).appendStyle("width", "100px");
        });
    }

    private void addStatusInfo(UILayoutBuilder layoutBuilder, Status status) {
        addText(layoutBuilder, "field.bindStatus", status.toString()).setColor(status.getColor());
    }

    private UIInfoItemBuilder addText(UILayoutBuilder layoutBuilder, String name, String value) {
        UIFlexLayoutBuilder flex = layoutBuilder.addFlex(name);
        flex.addInfo(name).appendStyle("min-width", "200px");
        return flex.addInfo(value);
    }

    private void assembleOnOfSwitchActions(UIInputBuilder builder) {
        if (isSupportOffWaitTime() || isSupportOnTime() || isSupportStartupOnOff()) {
            UIFlexLayoutBuilder onOffSwitchFlex = builder.addFlex("OnOffSwitch").setBorderArea("OnOffSwitch"
            ).setBorderColor("#B58A35").columnFlexDirection();

            if (isSupportOffWaitTime()) {
                onOffSwitchFlex.addSlider("field.offWaitTime", getOffWaitTime(), 0, 60000,
                    (entityContext, params) -> saveField(entityContext, entity -> entity.setOffWaitTime(params.getInt("value")))).appendStyle("width", "200px");
            }

            if (isSupportOnTime()) {
                addNumberWithButton(onOffSwitchFlex, "ot", "field.onTime", "field.notSet", 65535, getOnTime(),
                    (value, entityContext) -> saveField(entityContext, entity -> entity.setOnTime(value)));
            }

            if (isSupportStartupOnOff()) {
                onOffSwitchFlex.addCheckbox("field.startupOnOff", getStartupOnOff(),
                    (entityContext, params) -> saveField(entityContext, entity -> entity.setStartupOnOff(params.getBoolean("value"))));
            }
        }
    }

    private ActionResponseModel saveField(EntityContext entityContext, Function<ZigBeeEndpointEntity, Boolean> updater) {
        ZigBeeEndpointEntity entity = entityContext.getEntity(getEntityID(), false);
        if (updater.apply(entity)) {
            updater.apply(this);
            entityContext.save(entity);
            return success();
        }
        return null;
    }

    private void assembleLevelControlActions(UIInputBuilder builder) {
        if (isSupportLevelControl()) {
            UIFlexLayoutBuilder levelControlFlex = builder.addFlex("LevelControl").setBorderArea("LevelControl")
                                                          .setBorderColor("#35786f").columnFlexDirection();

            addNumberWithButton(levelControlFlex, "DTT", "def-trans-time", "field.useOnOffTime", 65535, getDefaultTransitionTime(),
                (value, entityContext) -> saveField(entityContext, entity -> entity.setDefaultTransitionTime(value)));

            levelControlFlex.addCheckbox("field.invertLevelControl", getInvertLevelControl(),
                (entityContext, params) -> saveField(entityContext, entity -> entity.setInvertLevelControl(params.getBoolean("value"))));

            levelControlFlex.addCheckbox("field.invertReportControl", getInvertReportControl(),
                (entityContext, params) -> saveField(entityContext, entity -> entity.setInvertReportControl(params.getBoolean("value"))));

            if (isSupportOnOffTransitionTime()) {
                addNumber(levelControlFlex, "onOffTT", "field.onOffTransitionTime", getOnOffTransitionTime(),
                    0, 60000, (value, entityContext) -> saveField(entityContext, entity -> entity.setOnOffTransitionTime(value)));
            }

            if (isSupportOnTransitionTime()) {
                addNumberWithButton(levelControlFlex, "onTT", "field.defaultTransitionTime", "field.useOnOffTransitionTime", 65535,
                    getOnOffTransitionTime(), (value, entityContext) -> saveField(entityContext, entity -> entity.setOnTransitionTime(value)));
            }

            if (isSupportOffTransitionTime()) {
                addNumberWithButton(levelControlFlex, "offTT", "field.offTransitionTime", "field.useOffTransitionTime", 65535,
                    getOffTransitionTime(), (value, entityContext) -> saveField(entityContext, entity -> entity.setOffTransitionTime(value)));
            }

            if (isSupportOnLevel()) {
                addNumberWithButton(levelControlFlex, "onLVL", "field.offTransitionTime", "field.notSet", 255,
                    getOnLevel(), (value, entityContext) -> saveField(entityContext, entity -> entity.setOnLevel(value)));
            }

            if (isSupportDefaultMoveRate()) {
                addNumberWithButton(levelControlFlex, "DMR", "field.defaultMoveRate", "field.notSet", 255,
                    getDefaultMoveRate(), (value, entityContext) -> saveField(entityContext, entity -> entity.setDefaultMoveRate(value)));
            }
        }
    }

    private void assembleReportConfigActions(UIInputBuilder builder) {
        if (isSupportReporting()) {
            UIFlexLayoutBuilder reportFlex = builder.addFlex("Reporting").setBorderArea("Reporting")
                                                    .setBorderColor("#35B2B5").columnFlexDirection();

            addNumber(reportFlex, "rtmin", "field.reportingTimeMin", getReportingTimeMin(),
                1, 86400, (value, entityContext) -> saveField(entityContext, entity -> entity.setReportingTimeMin(value)));
            addNumber(reportFlex, "rtmax", "field.reportingTimeMax", getReportingTimeMax(),
                1, 86400, (value, entityContext) -> saveField(entityContext, entity -> entity.setReportingTimeMax(value)));
            addNumber(reportFlex, "pp", "field.pollingPeriod", getPollingPeriod(),
                15, 86400, (value, entityContext) -> saveField(entityContext, entity -> entity.setPollingPeriod(value)));

            if (isSupportAnalogue() && getReportingChange() != null) {
                addNumber(reportFlex, "rc", "field.reportingChange", getReportingChange().intValue(),
                    getReportingChangeMin(), getReportingChangeMax(), (value, entityContext) ->
                        saveField(entityContext, entity -> entity.setReportingChange(getReportingChange())));
            }
        }
    }

    private String getValueFromConfiguration() {
        ZigBeeDeviceEntity owner = getOwnerTarget();
        if (owner != null && owner.getModelIdentifier() != null) {
            Optional<DeviceConfiguration> deviceDefinitionOptional = DeviceConfigurations.getDeviceDefinition(owner.getModelIdentifier());
            if (deviceDefinitionOptional.isPresent()) {
                DeviceConfiguration deviceConfiguration = deviceDefinitionOptional.get();
                EndpointDefinition endpointDefinition = deviceConfiguration.getEndpoint(getAddress(), getClusterName());
                if (endpointDefinition != null) {
                    return endpointDefinition.getId();
                }
            }
        }
        return getClusterName();
    }

    public enum ControlMethod {
        AUTO,
        HUE,
        XY
    }
}
