package org.homio.bundle.zigbee.converter.impl.ias;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.ZclCommand;
import com.zsmartsystems.zigbee.zcl.ZclCommandListener;
import com.zsmartsystems.zigbee.zcl.ZclStatus;
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster;
import com.zsmartsystems.zigbee.zcl.clusters.iaszone.ZoneStatusChangeNotificationCommand;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import java.util.function.Consumer;
import org.homio.bundle.zigbee.converter.impl.HasClusterDefinition;
import org.homio.bundle.zigbee.converter.impl.ZigBeeInputBaseConverter;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.EntityContextVar.VariableType;
import org.homio.bundle.api.exception.ProhibitedExecution;
import org.homio.bundle.api.state.OnOffType;

/**
 * Converter for the IAS zone sensors. This is an abstract class used as a base for different IAS sensors.
 */
public abstract class ZigBeeConverterIas extends ZigBeeInputBaseConverter<ZclIasZoneCluster>
    implements ZclCommandListener, ZclAttributeListener, HasClusterDefinition {

  /**
   * CIE Zone Status Attribute flags
   */
  protected static final int CIE_ALARM1 = 0x0001;
  protected static final int CIE_ALARM2 = 0x0002;
  protected static final int CIE_TAMPER = 0x0004;
  protected static final int CIE_BATTERY = 0x0008;
  protected static final int CIE_SUPERVISION = 0x0010;
  protected static final int CIE_RESTORE = 0x0020;
  protected static final int CIE_TROUBLE = 0x0040;
  protected static final int CIE_ACMAINS = 0x0080;
  protected static final int CIE_TEST = 0x0100;
  protected static final int CIE_BATTERYDEFECT = 0x0200;
  protected int bitTest;

  public ZigBeeConverterIas(int bitTest) {
    super(ZclClusterType.IAS_ZONE, ZclIasZoneCluster.ATTR_ZONESTATUS);
    this.bitTest = bitTest;
  }

  @Override
  public VariableType getVariableType() {
    return VariableType.Float;
  }

  @Override
  public int getClientCluster() {
    return ZclIasZoneCluster.CLUSTER_ID;
  }

  @Override
  public String getColor() {
    return "#34B8CF";
  }

  // must be called programmatically
  public boolean acceptEndpoint(ZigBeeEndpoint endpoint, String entityID, EntityContext entityContext, Consumer<String> progressMessage) {
    throw new ProhibitedExecution();
  }

  @Override
  protected void afterClusterInitialized() {
    zclCluster.addCommandListener(this);
  }

  @Override
  public boolean commandReceived(ZclCommand command) {
    log.debug("[{}]: ZigBee command report {}. {}", entityID, command, endpoint);
    if (command instanceof ZoneStatusChangeNotificationCommand) {
      ZoneStatusChangeNotificationCommand zoneStatus = (ZoneStatusChangeNotificationCommand) command;
      updateChannelState(zoneStatus.getZoneStatus());

      zclCluster.sendDefaultResponse(command, ZclStatus.SUCCESS);
      return true;
    }

    return false;
  }

  @Override
  protected void updateValue(Object val, ZclAttribute attribute) {
    updateChannelState((Integer) val);
  }

  private void updateChannelState(Integer state) {
    updateChannelState(((state & bitTest) != 0) ? OnOffType.ON : OnOffType.OFF);
  }
}
