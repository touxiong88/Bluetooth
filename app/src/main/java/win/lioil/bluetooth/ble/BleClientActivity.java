package win.lioil.bluetooth.ble;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import win.lioil.bluetooth.APP;
import win.lioil.bluetooth.R;

/**
 * BLE client (host/central equipment/Central)
 */
public class BleClientActivity extends Activity {
    private static final String TAG = BleClientActivity.class.getSimpleName();
    private EditText mWriteET;
    private TextView mTips;
    private BleDevAdapter mBleDevAdapter;
    private BluetoothGatt mBluetoothGatt;
    private boolean isConnected = false;
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");

    // Callback connected to the server
    public BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            BluetoothDevice dev = gatt.getDevice();
            Log.i(TAG, String.format("onConnectionStateChange:%s,%s,%s,%s", dev.getName(), dev.getAddress(), status, newState));
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true;
                gatt.discoverServices(); //Start service discovery
            } else {
                isConnected = false;
                closeConn();
            }
            logTv(String.format(status == 0 ? (newState == 2 ? "Successfully connected to [%s]": "Disconnected from [%s]"): ("Error connecting to [%s], error code:" + status), dev));
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.i(TAG, String.format("onServicesDiscovered:%s,%s,%s", gatt.getDevice().getName(), gatt.getDevice().getAddress(), status));
            if (status == BluetoothGatt.GATT_SUCCESS) { //BLE service discovery succeeded
                UUID serviceUUID = null,notifyUUID = null;
                // Traverse to obtain all UUIDs of BLE service Services/Characteristics/Descriptors
                for (BluetoothGattService service : gatt.getServices()) {
                    serviceUUID = service.getUuid();
                    Log.i(TAG, "Service UUID: " + serviceUUID);

                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                            notifyUUID = characteristic.getUuid();
                            Log.d("TAG", "Found notify characteristic: " + notifyUUID);
                        }
                    }
                    StringBuilder allUUIDs = new StringBuilder("UUIDs={\nS=" + service.getUuid().toString());
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        allUUIDs.append(",\nC=").append(characteristic.getUuid());
                        for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors())
                            allUUIDs.append(",\nD=").append(descriptor.getUuid());
                    }
                    allUUIDs.append("}");
                    Log.i(TAG, "onServicesDiscovered:" + allUUIDs.toString());
                    logTv("found service" + allUUIDs);
                }

                //found service and characteristic
                BluetoothGattService service = mBluetoothGatt.getService(serviceUUID);//your_service_uuid 722e0001-4553-4523-5539-35022233cd4e
                BluetoothGattCharacteristic notifyCharacteristic = service.getCharacteristic(notifyUUID);//your notify uuid 722e0003-4553-4523-5539-35022233cd4e
                //BluetoothGattCharacteristic writeCharacteristic =  service.getCharacteristic(UUID.fromString("722e0002-4553-4523-5539-35022233cd4e"));
                // set notify
                mBluetoothGatt.setCharacteristicNotification(notifyCharacteristic, true);
                BluetoothGattDescriptor descriptor = notifyCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                mBluetoothGatt.writeDescriptor(descriptor);
            }
        }





        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            UUID uuid = characteristic.getUuid();
            String valueStr = new String(characteristic.getValue());
            Log.i(TAG, String.format("onCharacteristicRead:%s,%s,%s,%s,%s", gatt.getDevice().getName(), gatt.getDevice().getAddress(), uuid, valueStr, status));
            logTv("read Characteristic[" + uuid + "]:\n" + valueStr);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            UUID uuid = characteristic.getUuid();
            String valueStr = new String(characteristic.getValue());
            Log.i(TAG, String.format("onCharacteristicWrite:%s,%s,%s,%s,%s", gatt.getDevice().getName(), gatt.getDevice().getAddress(), uuid, valueStr, status));
            logTv("write Characteristic[" + uuid + "]:\n" + valueStr);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            UUID uuid = characteristic.getUuid();
            byte[] data = characteristic.getValue();
            if (data.length == 6 && data[0] == 0x56 && data[5] == 0x76) {//battery charge info 1byte head + 2 voltage + 1 charge status + 1CRC+ 1 tail
                // battery voltage
                int batteryVoltage = ByteBuffer.wrap(Arrays.copyOfRange(data, 1, 3)).getShort() & 0xFFFF;
                Log.i(TAG, "Battery voltage: " + batteryVoltage + "mv");

                short voltage = ByteBuffer.wrap(Arrays.copyOfRange(data, 1, 3)).order(ByteOrder.BIG_ENDIAN).getShort();
                byte chargingStatus = data[3];
                byte crc = data[4];

                String formattedData = String.format("Voltage: %d mv, Charging Status:  %d, CRC: 0x%02X", voltage, chargingStatus, crc);

                Log.i(TAG, String.format("onCharacteristicChanged:%s,%s,%s,%s", gatt.getDevice().getName(), gatt.getDevice().getAddress(), uuid, formattedData));
                logTv(formattedData);

            }else {
                String valueStr = new String(characteristic.getValue(), StandardCharsets.UTF_8);
                String modifiedStr = valueStr.substring(1, valueStr.length() - 1);//remove head tail

                Log.i(TAG, String.format("onCharacteristicChanged:%s,%s,%s,%s", gatt.getDevice().getName(), gatt.getDevice().getAddress(), uuid, modifiedStr));
                logTv("QR: " + modifiedStr);
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            UUID uuid = descriptor.getUuid();
            String valueStr = Arrays.toString(descriptor.getValue());
            Log.i(TAG, String.format("onDescriptorRead:%s,%s,%s,%s,%s", gatt.getDevice().getName(), gatt.getDevice().getAddress(), uuid, valueStr, status));
            logTv("read Descriptor[" + uuid + "]:\n" + valueStr);
        }
        ////If listening is successfully enabled, the onDescriptorWrite() method in BluetoothGattCallback will be called back. The processing method is as follows:
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            UUID uuid = descriptor.getUuid();
            String valueStr = Arrays.toString(descriptor.getValue());
            Log.i(TAG, String.format("onDescriptorWrite:%s,%s,%s,%s,%s", gatt.getDevice().getName(), gatt.getDevice().getAddress(), uuid, valueStr, status));
            logTv("write Descriptor[" + uuid + "]:\n" + valueStr);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //Successfully enabled listening. You can write commands to the device
                Log.e(TAG, "Successfully enabled listening");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bleclient);
        RecyclerView rv = findViewById(R.id.rv_ble);
        //Button btn_read = findViewById(R.id.btn_read);
        // = findViewById(R.id.et_write);
        mTips = findViewById(R.id.tv_tips);
        rv.setLayoutManager(new LinearLayoutManager(this));


        mBleDevAdapter = new BleDevAdapter(new BleDevAdapter.Listener() {
            @Override
            public void onItemClick(BluetoothDevice bluetoothDevice) {
                closeConn();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {// connect bluetooth device
                    mBluetoothGatt = bluetoothDevice.connectGatt(BleClientActivity.this, false, mBluetoothGattCallback, BluetoothDevice.TRANSPORT_LE);
                } else {
                    mBluetoothGatt = bluetoothDevice.connectGatt(BleClientActivity.this, false, mBluetoothGattCallback);
                }

                logTv(String.format("Start connection with [%s]............", bluetoothDevice));
            }
        });
        rv.setAdapter(mBleDevAdapter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeConn();
    }

    // The number of peripheral devices connected to the BLE central device is limited (about 2~7). Before establishing a new connection, the old connection resources must be released, otherwise connection errors are prone to occur 133
    private void closeConn() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
        }
    }

    // 扫描BLE
    public void reScan(View view) {
        if (mBleDevAdapter.isScanning)
            APP.toast("scanning...", 0);
        else
            mBleDevAdapter.reScan();
    }




    // get Gatt service
    private BluetoothGattService getGattService(UUID uuid) {
        if (!isConnected) {
            APP.toast("no connect", 0);
            return null;
        }
        BluetoothGattService service = mBluetoothGatt.getService(uuid);
        if (service == null)
            APP.toast("not found service UUID=" + uuid, 0);
        return service;
    }

    // output log
    private void logTv(final String msg) {
        if (isDestroyed())
            return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                APP.toast(msg, 0);
                mTips.append(msg + "\n\n");
            }
        });
    }
}