package win.lioil.bluetooth.bt;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Environment;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.UUID;

import win.lioil.bluetooth.APP;
import win.lioil.bluetooth.util.Util;

/**
 * The base class of the client and server, which is used to manage the socket long connection
 */
public class BtBase {
    static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String FILE_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/bluetooth/";
    private static final int FLAG_MSG = 0;  //Message tag
    private static final int FLAG_FILE = 1; //file mark

    private BluetoothSocket mSocket;
    private DataOutputStream mOut;
    private Listener mListener;
    private boolean isRead;
    private boolean isSending;

    BtBase(Listener listener) {
        mListener = listener;
    }

    /**
     * Cyclically read the other party's data (if there is no data, block and wait)
     */
    void loopRead(BluetoothSocket socket) {
        mSocket = socket;
        try {
            if (!mSocket.isConnected())
                mSocket.connect();
            notifyUI(Listener.CONNECTED, mSocket.getRemoteDevice());
            mOut = new DataOutputStream(mSocket.getOutputStream());
            DataInputStream in = new DataInputStream(mSocket.getInputStream());
            isRead = true;
            while (isRead) { //Endless loop reading
                switch (in.readInt()) {
                    case FLAG_MSG: //Read SMS
                        String msg = in.readUTF();
                        notifyUI(Listener.MSG, "Receive short messages：" + msg);
                        break;
                    case FLAG_FILE: //read a file
                        Util.mkdirs(FILE_PATH);
                        String fileName = in.readUTF(); //file name
                        long fileLen = in.readLong(); //file length
                        // Read file content
                        long len = 0;
                        int r;
                        byte[] b = new byte[4 * 1024];
                        FileOutputStream out = new FileOutputStream(FILE_PATH + fileName);
                        notifyUI(Listener.MSG, "Receiving files(" + fileName + "),Please wait...");
                        while ((r = in.read(b)) != -1) {
                            out.write(b, 0, r);
                            len += r;
                            if (len >= fileLen)
                                break;
                        }
                        notifyUI(Listener.MSG, "Document receiving completed (stored in:" + FILE_PATH + ")");
                        break;
                }
            }
        } catch (Throwable e) {
            close();
        }
    }


    public void sendMsg(String msg) {
        if (checkSend()) return;
        isSending = true;
        try {
            mOut.writeInt(FLAG_MSG);
            mOut.writeUTF(msg);
            mOut.flush();
            notifyUI(Listener.MSG, "Send SMS：" + msg);
        } catch (Throwable e) {
            close();
        }
        isSending = false;
    }


    public void sendFile(final String filePath) {
        if (checkSend()) return;
        isSending = true;
        Util.EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    FileInputStream in = new FileInputStream(filePath);
                    File file = new File(filePath);
                    mOut.writeInt(FLAG_FILE);
                    mOut.writeUTF(file.getName());
                    mOut.writeLong(file.length());
                    int r;
                    byte[] b = new byte[4 * 1024];
                    notifyUI(Listener.MSG, "Sending files（" + filePath + "),Please wait...");
                    while ((r = in.read(b)) != -1)
                        mOut.write(b, 0, r);
                    mOut.flush();
                    notifyUI(Listener.MSG, "File sending completed.");
                } catch (Throwable e) {
                    close();
                }
                isSending = false;
            }
        });
    }


    public void unListener() {
        mListener = null;
    }


    public void close() {
        try {
            isRead = false;
            mSocket.close();
            notifyUI(Listener.DISCONNECTED, null);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }


    public boolean isConnected(BluetoothDevice dev) {
        boolean connected = (mSocket != null && mSocket.isConnected());
        if (dev == null)
            return connected;
        return connected && mSocket.getRemoteDevice().equals(dev);
    }

    // ============================================通知UI===========================================================
    private boolean checkSend() {
        if (isSending) {
            APP.toast("Sending other data, please send it later...", 0);
            return true;
        }
        return false;
    }

    private void notifyUI(final int state, final Object obj) {
        APP.runUi(new Runnable() {
            @Override
            public void run() {
                try {
                    if (mListener != null)
                        mListener.socketNotify(state, obj);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public interface Listener {
        int DISCONNECTED = 0;
        int CONNECTED = 1;
        int MSG = 2;

        void socketNotify(int state, Object obj);
    }
}
