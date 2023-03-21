package org.touchhome.bundle.zigbee;

import com.fazecast.jSerialComm.SerialPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.BundleEntrypoint;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.zigbee.ZigBeeBaseCoordinatorEntity;
import org.touchhome.bundle.zigbee.model.ZigBeeDeviceEntity;
import org.touchhome.bundle.zigbee.model.ZigbeeCoordinatorEntity;
import org.touchhome.bundle.zigbee.model.ZigbeeCoordinatorEntity.ZigbeeCoordinator;
import org.touchhome.bundle.api.util.Lang;

@Log4j2
@Component
@RequiredArgsConstructor
public class ZigBeeEntrypoint implements BundleEntrypoint {

    private final EntityContext entityContext;

    @Override
    public void init() {
        entityContext.ui().registerConsolePluginName("zigbee");
        entityContext.var().createGroup("zigbee", "ZigBee", true, "fab fa-laravel", "#ED3A3A");
        // check if new stick coordinator available
        List<ZigbeeCoordinatorEntity> coordinators = entityContext.findAll(ZigbeeCoordinatorEntity.class);

        /*for (ZigbeeCoordinatorEntity coordinator : coordinators) {
          coordinator.getService().getDiscoveryService().startScan();
        }*/

        this.checkNewCoordinator(entityContext.findAll(ZigbeeCoordinatorEntity.class), getPorts());

        // listen for port changes and reinitialize coordinator if port became available
        entityContext.event().addPortChangeStatusListener("zigbee-ports",
            port -> {
                Map<String, SerialPort> ports = getPorts();
                testCoordinators(entityContext.findAll(ZigbeeCoordinatorEntity.class), ports, coordinator -> coordinator.getService().restartCoordinator());
                this.checkNewCoordinator(coordinators, ports);
            });

        entityContext.event().addEntityUpdateListener(ZigBeeDeviceEntity.class, "zigbee-dev-change",
            (newValue, oldValue) -> {
                if (!Objects.equals(newValue.getPlace(), oldValue.getPlace()) || !Objects.equals(newValue.getName(), oldValue.getName())) {
                    newValue.createOrUpdateVarGroup(entityContext);
                }
            });
    }

    private <T extends ZigBeeBaseCoordinatorEntity> void testCoordinators(List<T> entities, Map<String, SerialPort> ports,
        Consumer<T> reInitializeCoordinatorHandler) {
        for (T coordinator : entities) {
            if (StringUtils.isNotEmpty(coordinator.getPort()) && coordinator.isStart() && coordinator.getStatus().isOffline()) {
                if (ports.containsKey(coordinator.getPort())) {
                    // try re-initialize coordinator
                    reInitializeCoordinatorHandler.accept(coordinator);
                } else {
                    // test maybe port had been changed
                    for (SerialPort serialPort : ports.values()) {
                        if (Objects.equals(serialPort.getDescriptivePortName(), coordinator.getPortD())) {
                            log.info("[{}]: Coordinator port changed from {} -> {}",
                                coordinator.getEntityID(),
                                coordinator.getPort(),
                                serialPort.getSystemPortName());
                            entityContext.save(coordinator.setSerialPort(serialPort));
                        }
                    }
                }
            }
        }
    }

    private void checkNewCoordinator(List<ZigbeeCoordinatorEntity> coordinators, Map<String, SerialPort> ports) {
        for (ZigbeeCoordinatorEntity coordinator : coordinators) {
            ports.remove(coordinator.getPort());
        }
        for (SerialPort port : ports.values()) {
            for (ZigbeeCoordinator zigbeeCoordinator : ZigbeeCoordinator.values()) {
                if (port.getPortDescription().contains(zigbeeCoordinator.getName())) {
                    handleNewCoordinator(port, zigbeeCoordinator);
                }
            }
        }
    }

    private void handleNewCoordinator(SerialPort port, ZigbeeCoordinator zigbeeCoordinator) {
        List<String> messages = new ArrayList<>();
        String coordinatorName = zigbeeCoordinator.getName();
        String name = Lang.getServerMessage(
            "zigbee.new_coordinator.coordinator", "NAME", coordinatorName);
        messages.add(Lang.getServerMessage("zigbee.new_coordinator.question"));
        messages.add(Lang.getServerMessage("zigbee.new_coordinator.name", "NAME", coordinatorName));
        messages.add(Lang.getServerMessage(
            "zigbee.new_coordinator.port", "NAME", port.getDescriptivePortName()));
        messages.add(Lang.getServerMessage(
            "zigbee.new_coordinator.port_descr", "NAME", port.getPortDescription()));
        entityContext.ui().sendConfirmation(
            "Confirm-ZigBee-Coordinator-" + port,
            Lang.getServerMessage("NEW_DEVICE.TITLE", "NAME", name),
            () -> {
                entityContext.save(new ZigbeeCoordinatorEntity()
                    .setSerialPort(port)
                    .setCoordinatorHandler(zigbeeCoordinator)
                    .setName(name));
            },
            messages, "ZIGBEE_COORDINATOR." + port);
    }

    @NotNull
    private Map<String, SerialPort> getPorts() {
        return Stream.of(SerialPort.getCommPorts()).collect(Collectors.toMap(SerialPort::getSystemPortName, p -> p));
    }

    @Override
    public int order() {
        return 600;
    }
}
