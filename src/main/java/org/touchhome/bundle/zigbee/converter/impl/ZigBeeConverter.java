package org.touchhome.bundle.zigbee.converter.impl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.touchhome.bundle.api.EntityContextVar.VariableType;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ZigBeeConverter {

  /**
   * Gets the cluster IDs that are implemented within the converter on the client side.
   *
   * @return Set of cluster IDs supported by the converter
   */
  int clientCluster();

  int[] additionalClientClusters() default 0;

  /**
   * Gets the cluster IDs that are implemented within the converter on the server side.
   *
   * @return Set of cluster IDs supported by the converter
   */
  int[] serverClusters() default 0;

  String name();

  VariableType linkType(); // Any mean NONE!

  String category();

  String color();
}
