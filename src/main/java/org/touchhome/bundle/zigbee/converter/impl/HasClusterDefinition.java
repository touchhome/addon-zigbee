package org.touchhome.bundle.zigbee.converter.impl;

import org.touchhome.bundle.api.EntityContextVar.VariableType;

public interface HasClusterDefinition {

  VariableType getVariableType();

  int getClientCluster();

  String getName();
}
