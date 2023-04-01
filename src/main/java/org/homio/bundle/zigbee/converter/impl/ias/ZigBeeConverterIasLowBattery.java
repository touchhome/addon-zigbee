package org.homio.bundle.zigbee.converter.impl.ias;

/**
 * Converter for the IAS low battery indicator.
 */
public class ZigBeeConverterIasLowBattery extends ZigBeeConverterIas {

  public ZigBeeConverterIasLowBattery() {
    super(CIE_BATTERY);
  }

  @Override
  public String getName() {
    return "ias_lowbattery";
  }
}
