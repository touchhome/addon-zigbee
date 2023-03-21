package org.touchhome.bundle.zigbee.util;

import lombok.Setter;

@Setter
public abstract class ShareConfiguration {

    protected Boolean discoverAttributes;
    protected Boolean readAttribute;

    protected Integer reportingTimeMin;
    protected Integer reportingTimeMax;
    protected Integer reportingChange;
    protected Boolean reportConfigurable;

    protected Integer failedPollingInterval;
    protected Integer successMaxReportInterval;
    protected Integer bindFailedPollingInterval;
}
