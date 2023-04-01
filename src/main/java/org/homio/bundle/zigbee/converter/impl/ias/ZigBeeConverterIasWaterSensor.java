package org.homio.bundle.zigbee.converter.impl.ias;

import org.homio.bundle.api.EntityContextVar.VariableType;

/**
 * Converter for the IAS water sensor.
 */
public class ZigBeeConverterIasWaterSensor extends ZigBeeConverterIas {

    public static final String CLUSTER_NAME = "ias_water";

    public ZigBeeConverterIasWaterSensor() {
        super(CIE_ALARM1);
    }

    @Override
    public String getName() {
        return CLUSTER_NAME;
    }

    @Override
    public VariableType getVariableType() {
        return VariableType.Bool;
    }
}
