package org.homio.bundle.zigbee.converter.impl.ias;

import org.homio.bundle.api.EntityContextVar.VariableType;

/**
 * Vibration Sensor Alarm Converter for the IAS vibration sensor.
 */
public class ZigBeeConverterIasVibration extends ZigBeeConverterIas {

    public ZigBeeConverterIasVibration() {
        super(CIE_ALARM2);
    }

    @Override
    public String getName() {
        return "ias_vibration";
    }

    @Override
    public VariableType getVariableType() {
        return VariableType.Bool;
    }
}
