package org.homio.bundle.zigbee.converter.impl.ias;

/**
 * Indicates if a device is tampered with Converter for the IAS tamper.
 */
public class ZigBeeConverterIasTamper extends ZigBeeConverterIas {

  public ZigBeeConverterIasTamper() {
    super(CIE_TAMPER);
  }

  @Override
  public String getName() {
    return "ias_tamper";
  }
}
