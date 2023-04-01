package org.homio.bundle.zigbee.converter.impl.ias;

import org.homio.bundle.api.EntityContextVar.VariableType;

/**
 * Movement Sensor Alarm Converter for the IAS movement sensor.
 */
public class ZigBeeConverterIasMovement extends ZigBeeConverterIas {

    public ZigBeeConverterIasMovement() {
        super(CIE_ALARM1);
    }

    @Override
    public String getName() {
        return "ias_movement";
    }

    @Override
    public VariableType getVariableType() {
        return VariableType.Bool;
    }
}
