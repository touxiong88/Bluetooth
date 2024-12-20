package win.lioil.bluetooth.bt;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import win.lioil.bluetooth.util.Util;


public class BtClient extends BtBase {
    BtClient(Listener listener) {
        super(listener);
    }


    public void connect(BluetoothDevice dev) {
        close();
        try {

            final BluetoothSocket socket = dev.createInsecureRfcommSocketToServiceRecord(SPP_UUID);

            Util.EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    loopRead(socket);
                }
            });
        } catch (Throwable e) {
            close();
        }
    }
}