package org.homio.bundle.zigbee.converter.config;

import lombok.Getter;
import org.homio.bundle.zigbee.model.ZigBeeEndpointEntity;

@Getter
public class ZclReportingConfig {

  private int reportingTimeMin;
  private int reportingTimeMax;

  private Double reportingChange;
  private int pollingPeriod;

  public ZclReportingConfig(ZigBeeEndpointEntity entity) {
    this.reportingTimeMin = entity.getReportingTimeMin();
    this.reportingTimeMax = entity.getReportingTimeMax();
    this.reportingChange = entity.getReportingChange();
    this.pollingPeriod = entity.getPollingPeriod();
  }

  public boolean updateConfiguration(ZigBeeEndpointEntity entity) {
    boolean updated = false;
    if (reportingTimeMin != entity.getReportingTimeMin()) {
      reportingTimeMin = entity.getReportingTimeMin();
      updated = true;
    }
    if (reportingTimeMax != entity.getReportingTimeMax()) {
      reportingTimeMax = entity.getReportingTimeMax();
      updated = true;
    }
    Double newReportingChange = entity.getReportingChange();
    if (newReportingChange != null && Double.compare(reportingChange, newReportingChange) != 0) {
      reportingChange = newReportingChange;
      updated = true;
    }
    if (pollingPeriod != entity.getPollingPeriod()) {
      pollingPeriod = entity.getPollingPeriod();
      updated = true;
    }
    return updated;
  }
}
