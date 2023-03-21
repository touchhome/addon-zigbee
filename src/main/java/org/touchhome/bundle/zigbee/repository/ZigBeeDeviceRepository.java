package org.touchhome.bundle.zigbee.repository;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.repository.AbstractRepository;
import org.touchhome.bundle.zigbee.model.ZigBeeDeviceEntity;

@Repository
public class ZigBeeDeviceRepository extends AbstractRepository<ZigBeeDeviceEntity> {

  public ZigBeeDeviceRepository() {
    super(ZigBeeDeviceEntity.class);
  }
}
