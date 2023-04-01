package org.homio.bundle.zigbee.repository;

import org.springframework.stereotype.Repository;
import org.homio.bundle.api.repository.AbstractRepository;
import org.homio.bundle.zigbee.model.ZigBeeDeviceEntity;

@Repository
public class ZigBeeDeviceRepository extends AbstractRepository<ZigBeeDeviceEntity> {

  public ZigBeeDeviceRepository() {
    super(ZigBeeDeviceEntity.class);
  }
}
