package org.homio.bundle.zigbee.converter.warningdevice;

/**
 * Possible values for the squawk mode in a squawk type.
 */
public enum SquawkMode {
  ARMED(0),
  DISARMED(1);

  private final int value;

  SquawkMode(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
