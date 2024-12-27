package com.faytech.bluetooth.bt;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import com.faytech.bluetooth.util.Util;


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