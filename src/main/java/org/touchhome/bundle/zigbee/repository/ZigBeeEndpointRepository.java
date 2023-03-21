package org.touchhome.bundle.zigbee.repository;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.repository.AbstractRepository;
import org.touchhome.bundle.zigbee.model.ZigBeeEndpointEntity;

@Repository
public class ZigBeeEndpointRepository extends AbstractRepository<ZigBeeEndpointEntity> {

  public ZigBeeEndpointRepository() {
    super(ZigBeeEndpointEntity.class);
  }
}
