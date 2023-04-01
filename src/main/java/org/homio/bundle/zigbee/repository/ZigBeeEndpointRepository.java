package org.homio.bundle.zigbee.repository;

import org.springframework.stereotype.Repository;
import org.homio.bundle.api.repository.AbstractRepository;
import org.homio.bundle.zigbee.model.ZigBeeEndpointEntity;

@Repository
public class ZigBeeEndpointRepository extends AbstractRepository<ZigBeeEndpointEntity> {

  public ZigBeeEndpointRepository() {
    super(ZigBeeEndpointEntity.class);
  }
}
