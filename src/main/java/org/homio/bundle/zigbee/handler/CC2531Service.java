package org.homio.bundle.zigbee.handler;

import com.zsmartsystems.zigbee.dongle.cc2531.ZigBeeDongleTiCc2531;
import com.zsmartsystems.zigbee.transport.TransportConfig;
import com.zsmartsystems.zigbee.transport.TransportConfigOption;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportTransmit;
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster;
import java.util.HashSet;
import java.util.Set;
import org.homio.bundle.zigbee.internal.ZigBeeSerialPort;
import org.homio.bundle.zigbee.model.ZigbeeCoordinatorEntity;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.model.Status;
import org.homio.bundle.api.port.PortFlowControl;
import org.homio.bundle.api.util.CommonUtils;
import org.homio.bundle.zigbee.service.ZigBeeCoordinatorService;

public class CC2531Service extends ZigBeeCoordinatorService {

  public CC2531Service(EntityContext entityContext, ZigbeeCoordinatorEntity entity) {
    super(entityContext, entity);
  }

  @Override
  public String getEntityID() {
    return getClass().getSimpleName();
  }

  @Override
  protected void initializeDongle() {
    ZigBeeTransportTransmit dongle = createDongle();
    TransportConfig transportConfig = createTransportConfig();

    startZigBee(dongle, transportConfig);
  }

  private ZigBeeTransportTransmit createDongle() {
    ZigBeeSerialPort serialPort = new ZigBeeSerialPort(
        "cc2531",
        entityContext,
        CommonUtils.getSerialPort(getEntity().getPort()),
        getEntityID(),
        getEntity().getPortBaud(),
        PortFlowControl.FLOWCONTROL_OUT_RTSCTS,
        () -> getEntity().setStatus(Status.ERROR, "PORT_COMMUNICATION_ERROR"),
        (port -> {
          if (!getEntity().getPort().equals(port.getSystemPortName())) {
            getEntity().setSerialPort(port);
            entityContext.save(getEntity(), false);
          }
        }));
    return new ZigBeeDongleTiCc2531(serialPort);
  }

  private TransportConfig createTransportConfig() {
    TransportConfig transportConfig = new TransportConfig();

    // The CC2531EMK dongle doesn't pass the MatchDescriptor commands to the stack, so we can't manage our services
    // directly. Instead, register any services we want to support so the CC2531EMK can handle the MatchDescriptor.
    Set<Integer> clusters = new HashSet<>();
    clusters.add(ZclIasZoneCluster.CLUSTER_ID);
    transportConfig.addOption(TransportConfigOption.SUPPORTED_OUTPUT_CLUSTERS, clusters);
    return transportConfig;
  }
}
