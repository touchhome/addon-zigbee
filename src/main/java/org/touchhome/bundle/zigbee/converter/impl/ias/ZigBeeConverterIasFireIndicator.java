package org.touchhome.bundle.zigbee.converter.impl.ias;

/**
 * Fire Indication Alarm Converter for the IAS fire indicator.
 */
public class ZigBeeConverterIasFireIndicator extends ZigBeeConverterIas {

  public static final String CLUSTER_NAME = "ias_fire";

  public ZigBeeConverterIasFireIndicator() {
    super(CIE_ALARM1);
  }

  @Override
  public String getName() {
    return CLUSTER_NAME;
  }
}
