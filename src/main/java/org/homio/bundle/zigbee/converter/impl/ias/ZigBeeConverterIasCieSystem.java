package org.homio.bundle.zigbee.converter.impl.ias;

/**
 * IAS System Alarm Channel Converter for the IAS Standard CIE System sensor.
 */
public class ZigBeeConverterIasCieSystem extends ZigBeeConverterIas {

  public ZigBeeConverterIasCieSystem() {
    super(CIE_ALARM1);
  }

  @Override
  public String getName() {
    return "ias_standard_system";
  }
}
