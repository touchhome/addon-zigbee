package org.homio.bundle.zigbee.converter.impl.ias;

import com.zsmartsystems.zigbee.zcl.clusters.iaszone.ZoneTypeEnum;
import lombok.Getter;

@Getter
public enum ZoneTypeClusterEnum {
  STANDARD_CIE(ZoneTypeEnum.STANDARD_CIE, ZigBeeConverterIasCieSystem.class),
  MOTION_SENSOR(ZoneTypeEnum.MOTION_SENSOR, ZigBeeConverterIasMotionIntrusion.class, ZigBeeConverterIasMotionPresence.class),
  CONTACT_SWITCH(ZoneTypeEnum.CONTACT_SWITCH, ZigBeeConverterIasContactPortal1.class),
  FIRE_SENSOR(ZoneTypeEnum.FIRE_SENSOR, ZigBeeConverterIasFireIndicator.class),
  WATER_SENSOR(ZoneTypeEnum.WATER_SENSOR, ZigBeeConverterIasWaterSensor.class),
  CO_SENSOR(ZoneTypeEnum.CO_SENSOR, ZigBeeConverterIasCoDetector.class),
  PERSONAL_EMERGENCY_DEVICE(ZoneTypeEnum.PERSONAL_EMERGENCY_DEVICE),
  VIBRATION_MOVEMENT_SENSOR(ZoneTypeEnum.VIBRATION_MOVEMENT_SENSOR, ZigBeeConverterIasMovement.class, ZigBeeConverterIasVibration.class),
  REMOTE_CONTROL(ZoneTypeEnum.REMOTE_CONTROL),
  KEY_FOB(ZoneTypeEnum.KEY_FOB),
  KEY_PAD(ZoneTypeEnum.KEY_PAD),
  STANDARD_WARNING_DEVICE(ZoneTypeEnum.STANDARD_WARNING_DEVICE),
  GLASS_BREAK_SENSOR(ZoneTypeEnum.GLASS_BREAK_SENSOR),
  SECURITY_REPEATER(ZoneTypeEnum.SECURITY_REPEATER);

  private final ZoneTypeEnum zoneType;
  private final Class<? extends ZigBeeConverterIas>[] iasConverterClasses;

  ZoneTypeClusterEnum(ZoneTypeEnum zoneType, Class<? extends ZigBeeConverterIas>... iasConverterClasses) {
    this.zoneType = zoneType;
    this.iasConverterClasses = iasConverterClasses;
  }
}
