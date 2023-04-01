package org.homio.bundle.zigbee.model;

import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.ZigBeeNode.ZigBeeNodeState;
import com.zsmartsystems.zigbee.zdo.field.NodeDescriptor;
import com.zsmartsystems.zigbee.zdo.field.NodeDescriptor.LogicalType;
import com.zsmartsystems.zigbee.zdo.field.PowerDescriptor;
import com.zsmartsystems.zigbee.zdo.field.PowerDescriptor.CurrentPowerModeType;
import com.zsmartsystems.zigbee.zdo.field.PowerDescriptor.PowerLevelType;
import com.zsmartsystems.zigbee.zdo.field.PowerDescriptor.PowerSourceType;
import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;
import org.homio.bundle.api.EntityContextSetting;
import org.homio.bundle.api.entity.HasJsonData;
import org.homio.bundle.api.model.HasEntityIdentifier;
import org.homio.bundle.api.model.Status;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.ui.field.UIFieldGroup;
import org.homio.bundle.api.ui.field.UIFieldType;
import org.homio.bundle.api.ui.field.color.UIFieldColorMatch;
import org.homio.bundle.api.ui.field.color.UIFieldColorStatusMatch;

/**
 * Interface hold fields that common for coordinator and end devices
 */
public interface HasNodeDescriptor extends HasJsonData, HasEntityIdentifier {

  @UIField(order = 11, hideInEdit = true)
  @UIFieldColorStatusMatch
  default Status getNodeStatus() {
    return EntityContextSetting.getStatus(this, "node", Status.UNKNOWN);
  }

  default void setNodeStatus(ZigBeeNodeState value) {
    Status status = value == null || value == ZigBeeNodeState.UNKNOWN ?
        Status.UNKNOWN : value == ZigBeeNodeState.ONLINE ? Status.ONLINE : Status.OFFLINE;
    if (getNodeStatus() != status) {
      EntityContextSetting.setStatus(this, "node", "NodeState", status);
    }
  }

  @UIField(order = 12, hideInEdit = true)
  @UIFieldColorStatusMatch
  default Status getFetchInfoStatus() {
    return EntityContextSetting.getStatus(this, "fetch_info", Status.UNKNOWN);
  }

  default void setFetchInfoStatus(Status status, @Nullable String msg) {
    EntityContextSetting.setStatus(this, "fetch_info", "FetchInfoStatus", status, msg);
  }

  @UIField(order = 1, hideInEdit = true)
  @UIFieldGroup(value = "Node", order = 25, borderColor = "#44B377")
  default int getNetworkAddress() {
    return getJsonData("na", 0);
  }

  default void setNetworkAddress(int value) {
    setJsonData("na", value);
  }

  @UIField(order = 5, hideInEdit = true)
  @UIFieldGroup("Node")
  default LogicalType getLogicalType() {
    return getJsonDataEnum("lt", LogicalType.UNKNOWN);
  }

  default boolean setLogicalType(LogicalType value) {
    if (!Objects.equals(getLogicalType(), value)) {
      setJsonDataEnum("lt", value);
      return true;
    }
    return false;
  }

  @UIField(hideInEdit = true, order = 6, hideOnEmpty = true)
  @UIFieldGroup("Node")
  default int getManufacturerCode() {
    return getJsonData("mc", 0);
  }

  default boolean setManufacturerCode(int value) {
    if (!Objects.equals(getManufacturerCode(), value)) {
      setJsonData("mc", value);
      return true;
    }
    return false;
  }

  @UIField(hideInEdit = true, order = 7, hideOnEmpty = true)
  @UIFieldGroup("Node")
  default long getNodeLastUpdateTime() {
    return getJsonData("lu", 0L);
  }

  default boolean setNodeLastUpdateTime(Date value) {
    if (value != null) {
      if (getNodeLastUpdateTime() != value.getTime()) {
        setJsonData("lu", value.getTime());
        return true;
      }
    }
    return false;
  }

  @UIField(hideInEdit = true, order = 8, hideOnEmpty = true)
  @UIFieldGroup("Node")
  default String getFirmwareVersion() {
    return getJsonData("fw");
  }

  default void setFirmwareVersion(String value) {
    setJsonData("fw", value);
  }

  @UIField(hideInEdit = true, order = 1, hideOnEmpty = true)
  @UIFieldGroup(value = "Power", order = 30, borderColor = "#5F5CA1")
  @UIFieldColorMatch(value = "CRITICAL", color = "#DB4318")
  @UIFieldColorMatch(value = "LOW", color = "#D0DB18")
  @UIFieldColorMatch(value = "MEDIUM", color = "#97DB18")
  @UIFieldColorMatch(value = "FULL", color = "#4DDB18")
  @UIFieldColorMatch(value = "UNKNOWN", color = "#818744")
  default PowerLevelType getCurrentPowerLevel() {
    return getJsonDataEnum("pw", PowerLevelType.UNKNOWN);
  }

  default boolean setCurrentPowerLevel(PowerLevelType powerLevel) {
    if (!Objects.equals(getCurrentPowerLevel(), powerLevel)) {
      setJsonDataEnum("pw", powerLevel);
      return true;
    }
    return false;
  }

  @UIField(hideInEdit = true, order = 2, hideOnEmpty = true)
  @UIFieldGroup("Power")
  default CurrentPowerModeType getCurrentPowerMode() {
    return getJsonDataEnum("pm", CurrentPowerModeType.UNKNOWN);
  }

  default boolean setCurrentPowerMode(CurrentPowerModeType value) {
    if (!Objects.equals(getCurrentPowerMode(), value)) {
      setJsonDataEnum("pm", value);
      return true;
    }
    return false;
  }

  @UIField(hideInEdit = true, order = 3, hideOnEmpty = true)
  @UIFieldGroup("Power")
  default PowerSourceType getCurrentPowerSource() {
    return getJsonDataEnum("ps", PowerSourceType.UNKNOWN);
  }

  default boolean setCurrentPowerSource(PowerSourceType value) {
    if (!Objects.equals(value, getCurrentPowerSource())) {
      setJsonDataEnum("ps", value);
      return true;
    }
    return false;
  }

  @UIField(hideInEdit = true, type = UIFieldType.Chips, order = 4, hideOnEmpty = true)
  @UIFieldGroup("Power")
  default Set<String> getAvailablePowerSources() {
    return getJsonDataSet("aps");
  }

  default boolean setAvailablePowerSources(Set<String> availablePowerSources) {
    if (!Objects.equals(getAvailablePowerSources(), availablePowerSources)) {
      setJsonData("aps", String.join("~~~", availablePowerSources));
      return true;
    }
    return false;
  }

  default boolean updateFromNodeDescriptor(ZigBeeNode node) {
    boolean updated = false;

    setNodeStatus(node.getNodeState());
    if (!Objects.equals(getNetworkAddress(), node.getNetworkAddress())) {
      setNetworkAddress(node.getNetworkAddress());
      updated = true;
    }
    PowerDescriptor pd = node.getPowerDescriptor();
    if (pd != null) {
      updated |= setCurrentPowerLevel(pd.getPowerLevel());
      updated |= setCurrentPowerMode(pd.getCurrentPowerMode());
      updated |= setCurrentPowerSource(pd.getCurrentPowerSource());
      updated |= setAvailablePowerSources(pd.getAvailablePowerSources().stream().map(Enum::name).collect(Collectors.toSet()));
    }
    NodeDescriptor nd = node.getNodeDescriptor();
    if (nd != null) {
      updated |= setManufacturerCode(nd.getManufacturerCode());
      updated |= setLogicalType(nd.getLogicalType());
    }
    updated |= setNodeLastUpdateTime(node.getLastUpdateTime());
    return updated;
  }
}
