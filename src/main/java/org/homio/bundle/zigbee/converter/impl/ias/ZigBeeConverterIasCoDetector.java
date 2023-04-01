package org.homio.bundle.zigbee.converter.impl.ias;

/**
 * Converter for the IAS CO sensor.
 */
public class ZigBeeConverterIasCoDetector extends ZigBeeConverterIas {

  public ZigBeeConverterIasCoDetector() {
    super(CIE_ALARM1);
  }

  @Override
  public String getName() {
    return "ias_cosensor";
  }
}
