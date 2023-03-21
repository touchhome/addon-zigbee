package org.touchhome.bundle.zigbee.internal;

import static com.zsmartsystems.zigbee.transport.ZigBeePort.FlowControl.FLOWCONTROL_OUT_NONE;
import static com.zsmartsystems.zigbee.transport.ZigBeePort.FlowControl.FLOWCONTROL_OUT_RTSCTS;

import com.fazecast.jSerialComm.SerialPort;
import com.zsmartsystems.zigbee.transport.ZigBeePort;
import java.io.IOException;
import java.util.function.Consumer;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.port.BaseSerialPort;
import org.touchhome.bundle.api.port.PortFlowControl;

/**
 * The default/reference Java serial port implementation using serial events to provide a non-blocking read call.
 */
@Log4j2
public class ZigBeeSerialPort extends BaseSerialPort implements ZigBeePort {

  /**
   * The length of the receive buffer
   */
  private static final int RX_BUFFER_LEN = 512;

  /**
   * The circular fifo queue for receive data
   */
  private final int[] buffer = new int[RX_BUFFER_LEN];

  /**
   * Synchronisation object for buffer queue manipulation
   */
  private final Object bufferSynchronisationObject = new Object();

  /**
   * The receive buffer end pointer (where we put the newly received data)
   */
  private int end = 0;

  /**
   * The receive buffer start pointer (where we take the data to pass to the application)
   */
  private int start = 0;

  public ZigBeeSerialPort(String coordinator,
      EntityContext entityContext,
      SerialPort serialPort,
      String entityID,
      int baudRate,
      PortFlowControl flowControl,
      Runnable portUnavailableListener,
      Consumer<SerialPort> portOpenSuccessListener) {
    super(coordinator, entityID, entityContext, baudRate, flowControl, portUnavailableListener, portOpenSuccessListener, log);
    this.serialPort = serialPort;
  }

  @Override
  public boolean open(int baudRate, FlowControl fc) {
    return open(baudRate, fc == FLOWCONTROL_OUT_NONE ? PortFlowControl.FLOWCONTROL_OUT_NONE :
        (fc == FLOWCONTROL_OUT_RTSCTS ? PortFlowControl.FLOWCONTROL_OUT_RTSCTS : PortFlowControl.FLOWCONTROL_OUT_XONOFF));
  }

  @Override
  public int read() {
    return read(9999999);
  }

  @Override
  public int read(int timeout) {
    long endTime = System.currentTimeMillis() + timeout;

    try {
      while (System.currentTimeMillis() < endTime) {
        synchronized (bufferSynchronisationObject) {
          if (start != end) {
            int value = buffer[start++];
            if (start >= RX_BUFFER_LEN) {
              start = 0;
            }
            return value;
          }
        }

        synchronized (this) {
          if (serialPort == null) {
            return -1;
          }

          wait(endTime - System.currentTimeMillis());
        }
      }
      return -1;
    } catch (InterruptedException ignore) {
    }
    return -1;
  }

  @Override
  protected void handleSerialEvent(byte[] buf) {
    for (byte b : buf) {
      buffer[end++] = b & 0xff;
      if (end >= RX_BUFFER_LEN) {
        end = 0;
      }
      if (end == start) {
        log.warn("[{}]: Processing DATA_AVAILABLE event: Serial buffer overrun", entityID);
        if (++start == RX_BUFFER_LEN) {
          start = 0;
        }

      }
    }
  }

  @Override
  public void write(int[] outArray) {
    if (outputStream == null) {
      return;
    }
    byte[] bytes = new byte[outArray.length];
    int cnt = 0;
    for (int value : outArray) {
      bytes[cnt++] = (byte) value;
    }
    try {
      outputStream.write(bytes);
    } catch (IOException e) {
    }
  }

  @Override
  public void purgeRxBuffer() {
    synchronized (bufferSynchronisationObject) {
      start = 0;
      end = 0;
    }
  }
}
