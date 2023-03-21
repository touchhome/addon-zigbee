package org.touchhome.bundle.zigbee.setting;

import org.touchhome.bundle.api.setting.SettingPluginBoolean;

public class ZigBeeDiscoveryOnStartupSetting implements SettingPluginBoolean {

  @Override
  public int order() {
    return 20;
  }
}
