package org.homio.bundle.zigbee.converter.impl;

import static com.zsmartsystems.zigbee.zcl.clusters.ZclDoorLockCluster.ATTR_LOCKSTATE;
import static com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType.DOOR_LOCK;

import com.zsmartsystems.zigbee.zcl.clusters.ZclDoorLockCluster;
import org.homio.bundle.api.EntityContextVar.VariableType;
import org.homio.bundle.zigbee.converter.config.ZclDoorLockConfig;

/**
 * Locks and unlocks the door and maintains the lock state This channel supports changes through attribute updates to the door lock state. ON=Locked,
 * OFF=Unlocked.
 */
@ZigBeeConverter(name = "door_state", color = "#CF8E34", clientCluster = ZclDoorLockCluster.CLUSTER_ID, linkType = VariableType.Bool, category = "Door")
public class ZigBeeConverterDoorLock extends ZigBeeInputBaseConverter<ZclDoorLockCluster> {

    public ZigBeeConverterDoorLock() {
        super(DOOR_LOCK, ATTR_LOCKSTATE);
    }

    @Override
    protected void afterClusterInitialized() {
        this.configDoorLock = new ZclDoorLockConfig(getEntity(), zclCluster, log);
    }

  /*@Override
    public void handleCommand(final ZigBeeCommand command) {
        if (command == OnOffType.ON) {
            cluster.lockDoorCommand(new ByteArray(new byte[0]));
        } else {
            cluster.unlockDoorCommand(new ByteArray(new byte[0]));
        }
    }*/
}
