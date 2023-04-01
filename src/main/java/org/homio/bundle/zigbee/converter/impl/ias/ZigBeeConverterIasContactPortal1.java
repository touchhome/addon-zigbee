package org.homio.bundle.zigbee.converter.impl.ias;

/**
 * Contact sensor
 */
public class ZigBeeConverterIasContactPortal1 extends ZigBeeConverterIas {

  public ZigBeeConverterIasContactPortal1() {
    super(CIE_ALARM1);
  }

  @Override
  public String getName() {
    return "ias_contactportal1";
  }
}
