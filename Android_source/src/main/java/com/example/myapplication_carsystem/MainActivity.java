package com.example.myapplication_carsystem;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends Activity {
        private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
        private SpeechRecognizer speechRecognizer;
        private Intent recognitionIntent;
        private static final String LOG_TAG = "VoiceRecognition";
        private boolean inInputMode = false;

        private static final String TAG = MainActivity.class.getSimpleName();
        private static final int REQUEST_PERMISSION_CODE = 2;
        private BluetoothManager mBluetoothManager;
        private BluetoothAdapter mBluetoothAdapter;
        private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
        private BluetoothGattServer mBluetoothGattServer;
        private BluetoothGattCharacteristic mCharacteristic;
        private boolean mAdvertising;
        private BluetoothDevice mDevice = null;

        private static final UUID MY_SERVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        private static final UUID MY_CHARACTERISTIC_UUID = UUID.fromString("00002201-0000-1000-8000-00805F9B34FB");
        private TextView messageTextView;
        private LinearLayout mainLayout;
        private String message = "Message not entered.";
        private String inputMessage = "";

        @Override
        protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_main);

                mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                mBluetoothAdapter = mBluetoothManager.getAdapter();

                if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                        finish();
                }

                mBluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
                requestPermissions();

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                recognitionIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                recognitionIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ja");
                recognitionIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this.getPackageName());
                recognitionIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
                recognitionIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

                messageTextView = findViewById(R.id.messageTextView);
                messageTextView.setVisibility(View.VISIBLE);
                messageTextView.setText(message);

                mainLayout = findViewById(R.id.main_layout);
        }

        @Override
        protected void onDestroy() {
                super.onDestroy();
                stopAdvertising();
                if (speechRecognizer != null) {
                        speechRecognizer.destroy();
                }
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                switch (requestCode) {
                        case REQUEST_PERMISSION_CODE:
                                boolean allPermissionsGranted = true;
                                for (int grantResult : grantResults) {
                                        if (grantResult != PackageManager.PERMISSION_GRANTED) {
                                                allPermissionsGranted = false;
                                                break;
                                        }
                                }
                                if (allPermissionsGranted) {
                                        startAdvertising();
                                        startVoiceRecognition();
                                } else {
                                        Toast.makeText(this, "All permissions not granted", Toast.LENGTH_SHORT).show();
                                }
                                break;
                }
        }

        private void requestPermissions() {
                List<String> permissionsNeeded = new ArrayList<>();
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                        permissionsNeeded.add(Manifest.permission.BLUETOOTH);
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                        permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADMIN);
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                        permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADVERTISE);
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
                }
                if (!permissionsNeeded.isEmpty()) {
                        ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), REQUEST_PERMISSION_CODE);
                } else {
                        startAdvertising();
                        startVoiceRecognition();
                }
        }

        private void startAdvertising() {
                if (!mAdvertising) {
                        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                                .setConnectable(true)
                                .build();

                        ParcelUuid serviceUuid = new ParcelUuid(MY_SERVICE_UUID);
                        AdvertiseData data = new AdvertiseData.Builder()
                                .setIncludeDeviceName(true)
                                .addServiceUuid(serviceUuid)
                                .build();

                        startGattServer();

                        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                                return;
                        }
                        mBluetoothLeAdvertiser.startAdvertising(settings, data, mAdvertiseCallback);
                        mAdvertising = true;
                        Log.d(TAG, "Advertising started.");
                }
        }

        private void startGattServer() {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return;
                }

                mBluetoothGattServer = mBluetoothManager.openGattServer(this, mGattServerCallback);

                BluetoothGattService service = new BluetoothGattService(MY_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

                mCharacteristic = new BluetoothGattCharacteristic(MY_CHARACTERISTIC_UUID,
                        BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                        BluetoothGattCharacteristic.PERMISSION_WRITE);

                service.addCharacteristic(mCharacteristic);

                mBluetoothGattServer.addService(service);
        }

        private void stopAdvertising() {
                if (mAdvertising) {
                        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                                return;
                        }

                        mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
                        mBluetoothGattServer.close();

                        mAdvertising = false;
                        Log.d(TAG, "Advertising stopped.");
                }
        }

        private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
                @Override
                public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                        Log.d(TAG, "AdvertiseCallback onStartSuccess: " + settingsInEffect.toString());
                }

                @Override
                public void onStartFailure(int errorCode) {
                        Log.e(TAG, "AdvertiseCallback onStartFailure: " + errorCode);
                }
        };

        private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
                @Override
                public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                        super.onConnectionStateChange(device, status, newState);
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                                mDevice = device;
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                mDevice = null;
                        }
                        Log.d(TAG, "GattServerCallback onConnectionStateChange: " + newState);
                        if(newState == BluetoothProfile.STATE_CONNECTED) {
                                new Handler(Looper.getMainLooper()).post(() -> startVoiceRecognition());
                        }
                }

                @Override
                public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                        super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
                        Log.d(TAG, "GattServerCallback onCharacteristicWriteRequest: " + new String(value));
                        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                                return;
                        }
                        mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value);
                        String message = new String(value);
                        runOnUiThread(() -> {
                                messageTextView.setVisibility(View.VISIBLE);
                                messageTextView.setText(message);
                                MainActivity.this.message = message;
                        });
                }
        };

        private void startVoiceRecognition() {
                if (speechRecognizer != null) {
                        speechRecognizer.setRecognitionListener(new RecognitionListener() {
                                @Override
                                public void onReadyForSpeech(Bundle params) {
                                        Log.i(LOG_TAG, "onReadyForSpeech");
                                }

                                @Override
                                public void onBeginningOfSpeech() {
                                        Log.i(LOG_TAG, "onBeginningOfSpeech");
                                }

                                @Override
                                public void onRmsChanged(float rmsdB) {
                                }

                                @Override
                                public void onBufferReceived(byte[] buffer) {
                                        Log.i(LOG_TAG, "onBufferReceived");
                                }

                                @Override
                                public void onEndOfSpeech() {
                                        Log.i(LOG_TAG, "onEndOfSpeech");
                                }

                                @Override
                                public void onError(int error) {
                                        speechRecognizer.startListening(recognitionIntent);
                                }

                                @Override
                                public void onResults(Bundle results) {
                                        messageTextView.setText(message);
                                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                                        if (matches != null && !matches.isEmpty()) {
                                                if (!inInputMode) {
                                                        for (String match : matches) {
                                                                if (match.equals("メッセージ入力")) {
                                                                        inInputMode = true;
                                                                        mainLayout.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_light));
                                                                        messageTextView.setVisibility(View.VISIBLE);
                                                                        messageTextView.setText(message);
                                                                        break;
                                                                }
                                                        }
                                                } else {
                                                        String message = matches.get(0);
                                                        Log.d(LOG_TAG,  message);
                                                        sendMessageOverBLE(message);
                                                        inputMessage = message;
                                                        inInputMode = false;
                                                        messageTextView.setVisibility(View.VISIBLE);
                                                        messageTextView.setText(inputMessage);
                                                        mainLayout.setBackgroundColor(getResources().getColor(R.color.default_background_color));
                                                }
                                        }
                                        speechRecognizer.startListening(recognitionIntent);
                                        runOnUiThread(() -> {
                                                messageTextView.setText(inputMessage);
                                        });
                                }

                                @Override
                                public void onPartialResults(Bundle partialResults) {
                                        Log.i(LOG_TAG, "onPartialResults");
                                }

                                @Override
                                public void onEvent(int eventType, Bundle params) {
                                        Log.i(LOG_TAG, "onEvent");
                                }
                        });
                        speechRecognizer.startListening(recognitionIntent);
                }
        }

        private void sendMessageOverBLE(String message) {
                int index = 0;
                int maxLength = 6;

                while(index < message.length()) {
                        String subMessage;
                        if(index + maxLength <= message.length()) {
                                subMessage = message.substring(index, index + maxLength);
                        } else {
                                subMessage = message.substring(index);
                        }
                        mCharacteristic.setValue(subMessage);
                        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                                return;
                        }
                        if (mDevice != null) {
                                mBluetoothGattServer.notifyCharacteristicChanged(mDevice, mCharacteristic, false);
                        } else {
                                Log.w(TAG, "No connected device to notify.");
                        }
                        index += maxLength;
                }

                mCharacteristic.setValue("end");
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return;
                }
                if (mDevice != null) {
                        mBluetoothGattServer.notifyCharacteristicChanged(mDevice, mCharacteristic, false);
                } else {
                        Log.w(TAG, "No connected device to notify.");
                }

                runOnUiThread(() -> {
                        messageTextView.setText(message);
                        MainActivity.this.message = message;
                });
        }
}
