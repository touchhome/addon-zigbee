package org.touchhome.bundle.zigbee.setting;

import org.touchhome.bundle.api.setting.SettingPluginSlider;

public class ZigBeeDiscoveryClusterTimeoutSetting implements SettingPluginSlider {

  @Override
  public int order() {
    return 10;
  }

  @Override
  public int defaultValue() {
    return 60;
  }

  @Override
  public Integer getMin() {
    return 30;
  }

  @Override
  public Integer getMax() {
    return 600;
  }
}
