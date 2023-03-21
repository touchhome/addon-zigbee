package org.touchhome.bundle.zigbee.converter.impl.ias;

/**
 * Motion presence sensor Converter for the IAS presence sensor.
 */
public class ZigBeeConverterIasMotionPresence extends ZigBeeConverterIas {

  public ZigBeeConverterIasMotionPresence() {
    super(CIE_ALARM2);
  }

  @Override
  public String getName() {
    return "ias_motionpresence";
  }
}
