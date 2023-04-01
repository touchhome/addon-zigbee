package org.homio.bundle.zigbee.setting.header;

import org.json.JSONObject;
import org.homio.bundle.api.setting.SettingPluginButton;
import org.homio.bundle.api.setting.console.header.ConsoleHeaderSettingPlugin;

public class ConsoleHeaderZigBeeDiscoveryButtonSetting implements ConsoleHeaderSettingPlugin<JSONObject>, SettingPluginButton {

  @Override
  public String getIcon() {
    return "fas fa-search-location";
  }

  @Override
  public int order() {
    return 100;
  }

  @Override
  public String getConfirmMsg() {
    return null;
  }
}
