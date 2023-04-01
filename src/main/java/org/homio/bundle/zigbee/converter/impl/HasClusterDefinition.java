package org.homio.bundle.zigbee.converter.impl;

import org.homio.bundle.api.EntityContextVar.VariableType;

public interface HasClusterDefinition {

  VariableType getVariableType();

  int getClientCluster();

  String getName();
}
