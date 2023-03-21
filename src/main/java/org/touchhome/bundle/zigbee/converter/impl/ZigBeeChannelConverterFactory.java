package org.touchhome.bundle.zigbee.converter.impl;

import static com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster.ATTR_ZONETYPE;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster;
import com.zsmartsystems.zigbee.zcl.clusters.iaszone.ZoneTypeEnum;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.converter.impl.ias.ZigBeeConverterIas;
import org.touchhome.bundle.zigbee.converter.impl.ias.ZigBeeConverterIasLowBattery;
import org.touchhome.bundle.zigbee.converter.impl.ias.ZigBeeConverterIasTamper;
import org.touchhome.bundle.zigbee.converter.impl.ias.ZoneTypeClusterEnum;


@Log4j2
@Component
public final class ZigBeeChannelConverterFactory {

  /**
   * List of all @ZigBeeConverter
   */
  private final List<ConverterContext> allConverters;

  @Getter
  private final Set<Integer> allClientClusterIds = new HashSet<>();
  @Getter
  private final Set<Integer> allServerClusterIds;

  public ZigBeeChannelConverterFactory(EntityContext entityContext) {
    List<Class<? extends ZigBeeBaseChannelConverter>> converters = entityContext.getClassesWithAnnotation(ZigBeeConverter.class);

    allConverters = converters.stream().map(ConverterContext::new).collect(Collectors.toList());
    allServerClusterIds = allConverters.stream().flatMapToInt(c -> IntStream.of(c.zigBeeConverter.serverClusters()))
                                       .boxed().collect(Collectors.toSet());
    for (ConverterContext context : allConverters) {
      allClientClusterIds.add(context.zigBeeConverter.clientCluster());
      for (int additionalCluster : context.zigBeeConverter.additionalClientClusters()) {
        allClientClusterIds.add(additionalCluster);
      }
    }
  }

  public int getConverterCount() {
    return allConverters.size();
  }

  public Collection<ZigBeeBaseChannelConverter> findAllMatchConverters(ZigBeeEndpoint endpoint, String entityID,
      EntityContext entityContext, Consumer<String> unitDone, Consumer<String> progressMessage) {
    Map<String, ZigBeeBaseChannelConverter> fitEndpoints = createZigBeeChannels(context -> {
      try {
        unitDone.accept("Check '" + context.zigBeeConverter.name() + "' converter");
        return context.converter.acceptEndpoint(endpoint, entityID, entityContext, message -> {
          progressMessage.accept("ep[" + endpoint.getEndpointId() + "]" + context.zigBeeConverter.name() + ":" + message);
        });
      } catch (Exception ex) {
        log.error("[{}]: Unable to evaluate acceptEndpoint for converter: {}. Endpoint: {}. Error: {}", entityID,
            context.converter.getClass().getSimpleName(), endpoint, TouchHomeUtils.getErrorMessage(ex));
        return false;
      }
    });

    // Remove ON/OFF if we support LEVEL
    if (fitEndpoints.containsKey("switch_level")) {
      fitEndpoints.remove("switch_onoff");
    }

    // Remove LEVEL if we support COLOR
    if (fitEndpoints.containsKey("color_color")) {
      fitEndpoints.remove("switch_onoff");
    }
    unitDone.accept("Check 'IAS' cluster");
    createIasClusters(endpoint, entityID, fitEndpoints, progressMessage);

    return fitEndpoints.values();
  }

  private Map<String, ZigBeeBaseChannelConverter> createZigBeeChannels(Predicate<ConverterContext> acceptConverter) {
    Map<String, ZigBeeBaseChannelConverter> fitEndpoints = new HashMap<>();
    for (ConverterContext context : allConverters) {
      if (acceptConverter.test(context)) {
        ZigBeeBaseChannelConverter newConverter = TouchHomeUtils.newInstance(context.converter.getClass());
        newConverter.setAnnotation(context.zigBeeConverter);
        fitEndpoints.put(context.zigBeeConverter.name(), newConverter);
      }
    }

    return fitEndpoints;
  }

  private void createIasClusters(ZigBeeEndpoint endpoint, String entityID, Map<String, ZigBeeBaseChannelConverter> converters,
      Consumer<String> unitDone) {
    ZclCluster cluster = endpoint.getInputCluster(ZclIasZoneCluster.CLUSTER_ID);
    if (cluster != null) {
      addConverters(converters, new ZigBeeConverterIasLowBattery());
      addConverters(converters, new ZigBeeConverterIasTamper());

      Integer zoneTypeId = null;
      ZclAttribute zclAttribute = cluster.getAttribute(ATTR_ZONETYPE);
      for (int retry = 0; retry < 5; retry++) {
        unitDone.accept("Reading IAS 'ZoneType' attribute " + (retry + 1) + "/5");
        zoneTypeId = (Integer) zclAttribute.readValue(Long.MAX_VALUE);
        if (zoneTypeId != null) {
          break;
        }
      }
      if (zoneTypeId != null) {
        ZoneTypeEnum zoneType = ZoneTypeEnum.getByValue(zoneTypeId);
        log.info("[{}]: IAS zone type {} {}", entityID, zoneType, endpoint);
        ZoneTypeClusterEnum zoneTypeClusterEnum = ZoneTypeClusterEnum.valueOf(zoneType.name());
        for (Class<? extends ZigBeeConverterIas> iasConverterClass : zoneTypeClusterEnum.getIasConverterClasses()) {
          ZigBeeConverterIas iasConverter = TouchHomeUtils.newInstance(iasConverterClass);
          addConverters(converters, iasConverter);
        }
      }
    }
  }

  private void addConverters(Map<String, ZigBeeBaseChannelConverter> converters, ZigBeeConverterIas converter) {
    converters.put(converter.getName(), converter);
  }

  private static class ConverterContext {

    private final @NotNull ZigBeeConverter zigBeeConverter;
    private final @NotNull ZigBeeBaseChannelConverter converter;

    public ConverterContext(Class<? extends ZigBeeBaseChannelConverter> converterClass) {
      zigBeeConverter = Optional.ofNullable(AnnotationUtils.getAnnotation(converterClass, ZigBeeConverter.class)).orElseThrow(
          () -> new IllegalStateException("Unable to get ZigBeeConverter annotation from type: " + converterClass.getSimpleName()));
      converter = TouchHomeUtils.newInstance(converterClass);
      if (converter == null) {
        throw new IllegalStateException("Unable to create instance of type: " + converterClass.getSimpleName());
      }
    }
  }
}
