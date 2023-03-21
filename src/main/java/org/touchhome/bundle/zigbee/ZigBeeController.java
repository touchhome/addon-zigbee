package org.touchhome.bundle.zigbee;

import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.model.ZigBeeDeviceEntity;
import org.touchhome.bundle.zigbee.model.ZigBeeEndpointEntity;
import org.touchhome.bundle.zigbee.model.ZigbeeCoordinatorEntity;

@Log4j2
@RestController
@RequestMapping("/rest/zigbee")
@RequiredArgsConstructor
public class ZigBeeController {

    private final EntityContext entityContext;

    public static boolean containsAny(ZigBeeBaseChannelConverter converter, Integer value) {
        for (int i : converter.getAdditionalClientClusters()) {
            if (i == value) {
                return true;
            }
        }
        return converter.getClientCluster() == value;
    }

    @GetMapping("/option/zcl/{clusterId}")
    public Collection<OptionModel> filterByClusterId(
            @PathVariable("clusterId") int clusterId,
            @RequestParam(value = "includeClusterName", required = false)
                    boolean includeClusterName) {
        return filterByClusterIdAndEndpointCount(clusterId, null, includeClusterName);
    }

    @GetMapping("/option/clusterName/{clusterName}")
    public Collection<OptionModel> filterByClusterName(
            @PathVariable("clusterName") String clusterName,
            @RequestParam(value = "includeClusterName", required = false)
                    boolean includeClusterName) {
        List<OptionModel> list = new ArrayList<>();
        for (ZigbeeCoordinatorEntity coordinator :
                entityContext.findAll(ZigbeeCoordinatorEntity.class)) {
            for (ZigBeeDeviceEntity device : coordinator.getOnlineDevices()) {
                ZigBeeEndpointEntity endpoint =
                        device.getEndpoints().stream()
                                .filter(f -> f.getName().equals(clusterName))
                                .findAny()
                                .orElse(null);

                // add zigBeeDevice
                if (endpoint != null) {
                    String key =
                            coordinator.getEntityID()
                                    + ":"
                                    + device.getIeeeAddress()
                                    + (includeClusterName ? "/" + endpoint.getName() : "");
                    list.add(
                            OptionModel.of(
                                    key, endpoint.getDescription() + " - " + device.getTitle()));
                }
            }
        }
        return list;
    }

    @GetMapping("/option/alarm")
    public Collection<OptionModel> getAlarmSensors() {
        return filterByClusterId(ZclIasZoneCluster.CLUSTER_ID, true);
    }

    @GetMapping("/option/buttons")
    public Collection<OptionModel> getButtons() {
        Collection<OptionModel> options =
                filterByClusterIdAndEndpointCount(ZclOnOffCluster.CLUSTER_ID, 1, false);
        options.addAll(filterByModelIdentifier("lumi.remote"));
        return options;
    }

    @GetMapping("/option/doubleButtons")
    public Collection<OptionModel> getDoubleButtons() {
        return filterByClusterIdAndEndpointCount(ZclOnOffCluster.CLUSTER_ID, 2, false);
    }

    @GetMapping("/option/model/{modelIdentifier}")
    public Collection<OptionModel> filterByModelIdentifier(
            @PathVariable("modelIdentifier") String modelIdentifier) {
        List<OptionModel> list = new ArrayList<>();
        for (ZigbeeCoordinatorEntity coordinator :
                entityContext.findAll(ZigbeeCoordinatorEntity.class)) {
            for (ZigBeeDeviceEntity zigBeeDevice : coordinator.getOnlineDevices()) {
                String deviceMI = zigBeeDevice.getModelIdentifier();
                if (deviceMI != null && deviceMI.startsWith(modelIdentifier)) {
                    list.add(
                            OptionModel.of(
                                    coordinator.getEntityID() + ":" + zigBeeDevice.getIeeeAddress(),
                                    zigBeeDevice.getTitle()));
                }
            }
        }

        return list;
    }

    private Collection<OptionModel> filterByClusterIdAndEndpointCount(
            Integer clusterId, Integer endpointCount, boolean includeClusterName) {
        List<OptionModel> list = new ArrayList<>();
        for (ZigbeeCoordinatorEntity coordinator :
                entityContext.findAll(ZigbeeCoordinatorEntity.class)) {
            for (ZigBeeDeviceEntity device : coordinator.getOnlineDevices()) {
                List<ZigBeeEndpointEntity> endpoints =
                        device.getEndpoints().stream()
                                .filter(e -> containsAny(e.getService().getCluster(), clusterId))
                                .collect(Collectors.toList());

                if (!endpoints.isEmpty()) {
                    if (endpointCount == null || endpointCount == endpoints.size()) {
                        String key =
                                coordinator.getEntityID()
                                        + ":"
                                        + device.getIeeeAddress()
                                        + (includeClusterName
                                                ? "/" + endpoints.iterator().next().getName()
                                                : "");
                        list.add(OptionModel.of(key, device.getTitle()));
                    }
                }
            }
        }
        return list;
    }
}
