package org.touchhome.bundle.zigbee.converter.impl.ias;

/**
 * Motion intrusion sensor Converter for the IAS motion sensor.
 */
public class ZigBeeConverterIasMotionIntrusion extends ZigBeeConverterIas {

  public ZigBeeConverterIasMotionIntrusion() {
    super(CIE_ALARM1);
  }

  @Override
  public String getName() {
    return "ias_motionintrusion";
  }
}
