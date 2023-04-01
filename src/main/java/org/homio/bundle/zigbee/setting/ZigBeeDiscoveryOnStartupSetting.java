package org.homio.bundle.zigbee.setting;

import org.homio.bundle.api.setting.SettingPluginBoolean;

public class ZigBeeDiscoveryOnStartupSetting implements SettingPluginBoolean {

  @Override
  public int order() {
    return 20;
  }
}
