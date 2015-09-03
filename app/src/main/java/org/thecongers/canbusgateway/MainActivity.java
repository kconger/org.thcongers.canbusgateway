/*
Copyright (C) 2014 Keith Conger <keith.conger@gmail.com>

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.thecongers.canbusgateway;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends ActionBarActivity {

    private SharedPreferences sharedPrefs;

    private TextView txtRPM;
    private TextView txtThrottleValve;
    private TextView txtThrottle;
    private TextView txtClutch;
    private TextView txtKillSwitch;
    private TextView txtSignals;
    private TextView txtHighBeam;
    private TextView txtFrontSpeed;
    private TextView txtFrontDistance;
    private TextView txtRearSpeed;
    private TextView txtRearDistance;
    private TextView txtBrakeLevers;
    private TextView txtGear;
    private TextView txtInfoButton;
    private TextView txtHeatedGrips;
    private TextView txtOdometers;
    private TextView txtOilTemperature;
    private TextView txtABS;
    private TextView txtESA;
    private TextView txtFuelLevel;
    private TextView txtAirTemp;
    private TextView txtAmbientLight;
    private TextView txtWIP;
    private TextView txtRAWMessage;

    private BluetoothAdapter btAdapter = null;
    // SPP UUID service
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String TAG = "CANBusGateway";
    private final int RECEIVE_MESSAGE = 1;		// Status for Handler
    private static final int SETTINGS_RESULT = 1;
    private String address;
    private LogData logger = null;
    private static Handler canBusMessages;
    private ConnectThread btConnectThread;

    @SuppressLint("HandlerLeak")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        setTitle(R.string.app_name);

        txtRPM = (TextView) findViewById(R.id.textViewRPM);
        txtThrottleValve = (TextView) findViewById(R.id.textViewThrottleValve);
        txtThrottle = (TextView) findViewById(R.id.textViewThrottle);
        txtClutch = (TextView) findViewById(R.id.textViewClutch);
        txtKillSwitch = (TextView) findViewById(R.id.textViewKillSwitch);
        txtSignals = (TextView) findViewById(R.id.textViewSignals);
        txtHighBeam = (TextView) findViewById(R.id.textViewHighBeam);
        txtFrontSpeed = (TextView) findViewById(R.id.textViewFrontSpeed);
        txtFrontDistance = (TextView) findViewById(R.id.textViewFrontDistance);
        txtRearSpeed = (TextView) findViewById(R.id.textViewRearSpeed);
        txtRearDistance = (TextView) findViewById(R.id.textViewRearDistance);
        txtBrakeLevers = (TextView) findViewById(R.id.textViewBrakeLevers);
        txtGear = (TextView) findViewById(R.id.textViewGear);
        txtInfoButton = (TextView) findViewById(R.id.textViewInfoButton);
        txtHeatedGrips = (TextView) findViewById(R.id.textViewHeatedGrips);
        txtOdometers = (TextView) findViewById(R.id.textViewOdometer);
        txtOilTemperature = (TextView) findViewById(R.id.textViewOilTemp);
        txtABS = (TextView) findViewById(R.id.textViewABS);
        txtESA = (TextView) findViewById(R.id.textViewESA);
        txtFuelLevel = (TextView) findViewById(R.id.textViewFuelLevel);
        txtAirTemp = (TextView) findViewById(R.id.textViewAirTemp);
        txtAmbientLight = (TextView) findViewById(R.id.textViewAmbientLight);
        txtWIP = (TextView) findViewById(R.id.textViewWIP);
        txtRAWMessage = (TextView) findViewById(R.id.textViewRawMsg);

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Watch for Bluetooth Changes
        IntentFilter filter1 = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        IntentFilter filter2 = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        IntentFilter filter3 = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        this.registerReceiver(btReceiver, filter1);
        this.registerReceiver(btReceiver, filter2);
        this.registerReceiver(btReceiver, filter3);

        canBusMessages = new Handler() {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case RECEIVE_MESSAGE:
                        // Check to see if message is the correct size
                        if (msg.arg1 == 27) {
                            byte[] readBuf = (byte[]) msg.obj;
                            String message = new String(readBuf);

                            //Default Units
                            String speedUnit = "km/h";
                            String distanceUnit = "m";
                            String odometerUnit = "km";
                            String temperatureUnit = "C";

                            String[] splitMessage = message.split(",");
                            if (splitMessage[0].contains("10C")) {
                                //Throttle Valve
                                double throttleValvePercent = (Integer.parseInt(splitMessage[2], 16) / 255.0) * 100.0;
                                txtThrottleValve.setText(String.valueOf((int) Math.round(throttleValvePercent)));

                                //Throttle
                                double throttlePercent = (Integer.parseInt(splitMessage[7], 16) / 255.0) * 100.0;
                                txtThrottle.setText(String.valueOf((int) Math.round(throttlePercent)));

                                //RPM
                                int rpm = (Integer.parseInt(splitMessage[4], 16) * 255 + Integer.parseInt(splitMessage[3], 16)) / 4;
                                txtRPM.setText(Integer.toString(rpm));

                                //Clutch
                                String clutchValue = splitMessage[5].substring(1);
                                String clutchEngaged = null;
                                if (clutchValue.contains("A")|| clutchValue.contains("9")){
                                    clutchEngaged = "Disengaged";
                                }else if (clutchValue.contains("6") || clutchValue.contains("5")){
                                    clutchEngaged = "Engaged";
                                }else {
                                    clutchEngaged = "Other Value: " + clutchValue;
                                }
                                txtClutch.setText(clutchEngaged);

                                //Kill
                                String killSwitchValue = splitMessage[5].substring(1);
                                String killSwitch = "";
                                if (killSwitchValue.contains("5") || killSwitchValue.contains("9")){
                                    killSwitch = "On";
                                }else{
                                    killSwitch = "Off";
                                }
                                txtKillSwitch.setText(killSwitch);

                            }else if (splitMessage[0].contains("130")){
                                //Turn indicators
                                String indicatorValue = splitMessage[8];
                                String indicator = "";
                                txtHighBeam.setText(indicatorValue);
                                if (indicatorValue.contains("D7")){
                                    indicator = "Left";
                                }else if (indicatorValue.contains("E7")){
                                    indicator = "Right";
                                }else if (indicatorValue.contains("EF")){
                                    indicator = "Both";
                                }else{
                                    indicator = "Off";
                                }
                                txtSignals.setText(indicator);

                                //High Beam
                                String highBeamValue = splitMessage[7].substring(1);
                                String highBeam = "";
                                if (highBeamValue.contains("9")){
                                    highBeam = "On";
                                }else{
                                    highBeam = "Off";
                                }
                                txtHighBeam.setText(highBeam);

                            }else if (splitMessage[0].contains("294")){
                                //Front Wheel Speed/Distance
                                double frontSpeed = ((Integer.parseInt(splitMessage[4], 16) * 256.0 + Integer.parseInt(splitMessage[3], 16)) * 0.0609);
                                double frontDistance = ((Integer.parseInt(splitMessage[6], 16) * 256.0 + Integer.parseInt(splitMessage[5], 16)) * 0.039);
                                if (sharedPrefs.getString("prefdistance", "0").contains("0")) {
                                    speedUnit = "mph";
                                    distanceUnit = "f";
                                    frontSpeed = frontSpeed / 1.609344;
                                    frontDistance = frontDistance * 3.2808;
                                }
                                txtFrontSpeed.setText(String.valueOf((int) Math.round(frontSpeed)) + " " +  speedUnit);
                                txtFrontDistance.setText(String.valueOf((int) Math.round(frontDistance)) + " " + distanceUnit);

                                //Brake levers
                                String brakeLeverValue = splitMessage[7].substring(1);
                                String brakeLever ="";
                                if (brakeLeverValue.contains("7")){
                                    brakeLever = "Front";
                                }else if (brakeLeverValue.contains("B")){
                                    brakeLever = "Rear";
                                }else{
                                    brakeLever = "None";
                                }
                                txtBrakeLevers.setText(brakeLever);

                                //ABS
                                String absValue = splitMessage[2].substring(0,1);
                                String abs = "";
                                if (absValue.contains("B")){
                                    abs = "Off";
                                }else {
                                    abs = "On";
                                }
                                txtABS.setText(abs);

                            }else if (splitMessage[0].contains("2A8")){
                                //Rear Wheel Speed
                                double rearSpeed = ((Integer.parseInt(splitMessage[4], 16) * 256.0 + Integer.parseInt(splitMessage[3], 16)) * 0.06);
                                double rearDistance = ((Integer.parseInt(splitMessage[6], 16) * 256.0 + Integer.parseInt(splitMessage[5], 16)) * 0.03775);
                                if (sharedPrefs.getString("prefdistance", "0").contains("0")) {
                                    speedUnit = "mph";
                                    distanceUnit = "f";
                                    rearSpeed = rearSpeed / 1.609344;
                                    rearDistance = rearDistance * 3.2808;
                                }
                                txtRearSpeed.setText(String.valueOf((int) Math.round(rearSpeed)) + " " + speedUnit);
                                txtRearDistance.setText(String.valueOf((int) Math.round(rearDistance)) + " " + distanceUnit);

                            }else if (splitMessage[0].contains("2BC")){
                                // Oil Temperature
                                // TODO:WIP
                                double oilTemp = Integer.parseInt(splitMessage[3], 16 ) - 40.0;
                                if (sharedPrefs.getString("preftempf", "0").contains("0")) {
                                    // F
                                    oilTemp = (9.0 / 5.0) * oilTemp + 32.0;
                                    temperatureUnit = "F";
                                }
                                txtOilTemperature.setText(String.valueOf((int) Math.round(oilTemp)) + temperatureUnit);

                                // Gear
                                String gearValue = splitMessage[6].substring(0,1);
                                String gear;
                                if (gearValue.contains("1")){
                                    gear = "1";
                                }else if (gearValue.contains("2")){
                                    gear = "N";
                                }else if (gearValue.contains("4")){
                                    gear = "2";
                                }else if (gearValue.contains("7")){
                                    gear = "3";
                                }else if (gearValue.contains("8")){
                                    gear = "4";
                                }else if (gearValue.contains("B")){
                                    gear = "5";
                                }else if (gearValue.contains("D")){
                                    gear = "6";
                                } else {
                                    gear = "-";
                                }
                                txtGear.setText(gear);

                                // Air Intake Temperature
                                // TODO:WIP
                                double airTemp = (Integer.parseInt(splitMessage[8], 16 ) - 80.0);
                                if (sharedPrefs.getString("preftempf", "0").contains("0")) {
                                    // F
                                    airTemp = (9.0 / 5.0) * airTemp + 32.0;
                                    temperatureUnit = "F";
                                }
                                txtAirTemp.setText(String.valueOf(airTemp) + temperatureUnit);

                            }else if (splitMessage[0].contains("2D0")){
                                //Info Button
                                String infoButtonValue = splitMessage[6].substring(1);
                                String infoButton = "";
                                if (infoButtonValue.contains("5")){
                                    infoButton = "Short Press";
                                }else if (infoButtonValue.contains("6")){
                                    infoButton = "Long Press";
                                }else{
                                    infoButton = "Inactive";
                                }
                                txtInfoButton.setText(infoButton);

                                //Heated Grips
                                String heatedGripSwitchValue = splitMessage[8].substring(0,1);
                                String heatedGripSwitch = null;
                                if (heatedGripSwitchValue.contains("C")){
                                    heatedGripSwitch = "Off";
                                }else if (heatedGripSwitchValue.contains("D")){
                                    heatedGripSwitch = "Low";
                                }else if (heatedGripSwitchValue.contains("E")){
                                    heatedGripSwitch = "High";
                                }else{
                                    heatedGripSwitch = "Other: " + heatedGripSwitchValue;
                                }
                                txtHeatedGrips.setText(heatedGripSwitch);

                                //ESA Damping and Preload
                                String esaDampingValue1 = splitMessage[5].substring(1);
                                String esaDampingValue2 = splitMessage[8].substring(1);
                                String esaPreLoadValue = splitMessage[5].substring(0,1);
                                if (esaDampingValue1.contains("B") && esaDampingValue2.contains("1")){
                                    txtESA.setText("SOFT: Smooth");
                                } else if (esaDampingValue1.contains("B") && esaDampingValue2.contains("2")){
                                    txtESA.setText("NORM: Smooth");
                                } else if (esaDampingValue1.contains("B") && esaDampingValue2.contains("3")){
                                    txtESA.setText("HARD: Smooth");
                                } else if (esaDampingValue1.contains("B") && esaDampingValue2.contains("4")){
                                    txtESA.setText("SOFT: Uneven");
                                } else if (esaDampingValue1.contains("B") && esaDampingValue2.contains("5")){
                                    txtESA.setText("NORM: Uneven");
                                } else if (esaDampingValue1.contains("B") && esaDampingValue2.contains("6")){
                                    txtESA.setText("HARD: Uneven");
                                } else if (esaDampingValue1.contains("7") && esaDampingValue2.contains("1")){
                                    txtESA.setText("SOFT: Smooth");
                                } else if (esaDampingValue1.contains("7") && esaDampingValue2.contains("2")){
                                    txtESA.setText("NORM: Smooth");
                                } else if (esaDampingValue1.contains("7") && esaDampingValue2.contains("3")){
                                    txtESA.setText("HARD: Smooth");
                                } else if (esaDampingValue1.contains("7") && esaDampingValue2.contains("4")){
                                    txtESA.setText("SOFT: Uneven");
                                } else if (esaDampingValue1.contains("7") && esaDampingValue2.contains("5")){
                                    txtESA.setText("NORM: Uneven");
                                } else if (esaDampingValue1.contains("7") && esaDampingValue2.contains("6")){
                                    txtESA.setText("HARD: Uneven");
                                } else if (esaPreLoadValue.contains("1")){
                                    txtESA.setText("COMF: Solo");
                                } else if (esaPreLoadValue.contains("2")){
                                    txtESA.setText("NORM: Solo");
                                } else if (esaPreLoadValue.contains("3")){
                                    txtESA.setText("SPORT: Solo");
                                } else if (esaPreLoadValue.contains("4")){
                                    txtESA.setText("COMF: Luggage");
                                } else if (esaPreLoadValue.contains("5")){
                                    txtESA.setText("NORM: Luggage");
                                } else if (esaPreLoadValue.contains("6")){
                                    txtESA.setText("SPORT: Luggage");
                                } else if (esaPreLoadValue.contains("7")){
                                    txtESA.setText("COMF: Passenger");
                                } else if (esaPreLoadValue.contains("8")){
                                    txtESA.setText("NORM: Passenger");
                                } else if (esaPreLoadValue.contains("9")){
                                    txtESA.setText("SPORT: Passenger");
                                } else {
                                    txtESA.setText("");
                                }


                                //Fuel Level
                                double fuelLevelPercent = (Integer.parseInt(splitMessage[4], 16) / 255.0) * 100.0;
                                txtFuelLevel.setText(String.valueOf((int) Math.round(fuelLevelPercent)));
                            }else if (splitMessage[0].contains("3F8")){
                                String odometerValue = "";
                                for(int i=4;i>1;i--){
                                    odometerValue = odometerValue + splitMessage[i];
                                }
                                double odometer = Integer.parseInt(odometerValue, 16 );
                                if (sharedPrefs.getString("prefdistance", "0").contains("0")) {
                                    odometerUnit = "Miles";
                                    odometer = odometer * 0.6214;
                                }
                                txtOdometers.setText(String.valueOf((int) Math.round(odometer)) + " " + odometerUnit);
                            }else if (splitMessage[0].contains("3FF")){
                                String ambientLightValue = splitMessage[2].substring(0, 1);
                                if (ambientLightValue.contains("B")){
                                    txtAmbientLight.setText("Dark");
                                } else if (ambientLightValue.contains("7")){
                                    txtAmbientLight.setText("Light");
                                }
                            }else if (splitMessage[0].contains("7BE")){

                            } else {
                                Log.d(TAG, "Unknown CANBus Message");
                            }
                            txtRAWMessage.setText(message);
                            if (!sharedPrefs.getBoolean("prefDataLogging", false) && (logger != null)) {
                                logger.write(message);
                            }

                        } else {
                            Log.d(TAG, "Malformed message, message length: " + msg.arg1);
                        }
                        break;
                }
            }
        };

        // Try to connect to CANBusGateway
        btConnect();
    }

    @Override
    protected void onStop()
    {
        try {
            unregisterReceiver(btReceiver);
        } catch (IllegalArgumentException e){
            Log.d(TAG, "Receiver not registered");
        }
        super.onStop();
    }

    //Draw options menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    // When options menu item is selected
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
            case R.id.action_btconnect:
                // Connect Menu was selected
                btConnect();
                return true;
            case R.id.action_settings:
                // Settings Menu was selected
                Intent i = new Intent(getApplicationContext(), org.thecongers.canbusgateway.UserSettingActivity.class);
                startActivityForResult(i, SETTINGS_RESULT);
                return true;
            case R.id.action_about:
                // About was selected
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getResources().getString(R.string.alert_about_title));
                builder.setMessage(readRawTextFile(this, R.raw.about));
                builder.setPositiveButton(getResources().getString(R.string.alert_about_button), null);
                builder.show();
                return true;
            case R.id.action_exit:
                // Exit menu item was selected
                if (logger != null){
                    logger.shutdown();
                }
                finish();
                System.exit(0);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    //Runs when settings are updated
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==SETTINGS_RESULT)
        {
            updateUserSettings();
        }
    }

    // Update UI when settings are updated
    private void updateUserSettings()
    {
        // Shutdown Logger
        if (!sharedPrefs.getBoolean("prefDataLogging", false) && (logger != null)) {
            logger.shutdown();
        }
    }

    // Connect to iTPMSystem
    private boolean btConnect() {
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        checkBTState();
        if(btAdapter!=null) {
            Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
            // If there are paired devices
            if (pairedDevices.size() > 0) {
                // Loop through paired devices
                for (BluetoothDevice device : pairedDevices) {
                    if (device.getName().contains("CANBusGateway")) {
                        address = device.getAddress();
                        Log.d(TAG, "Paired CANBusGateway found: " + device.getName() + " " + device.getAddress());
                    }
                }
                if (address == null) {
                    Toast.makeText(MainActivity.this,
                            getResources().getString(R.string.toast_noPaired),
                            Toast.LENGTH_LONG).show();
                    return false;
                }
            }
            if (address != null){
                // Set up a pointer to the remote node using it's address.
                BluetoothDevice device = btAdapter.getRemoteDevice(address);
                btConnectThread = new ConnectThread(device);
                btConnectThread.start();
            } else {
                Toast.makeText(MainActivity.this,
                        getResources().getString(R.string.toast_noPaired),
                        Toast.LENGTH_LONG).show();
                return false;
            }
            return true;
        }
        Log.d(TAG, "Bluetooth not supported");
        return false;
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        if(Build.VERSION.SDK_INT >= 10){
            try {
                final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] { UUID.class });
                return (BluetoothSocket) m.invoke(device, MY_UUID);
            } catch (Exception e) {
                Log.e(TAG, "Could not create insecure RFComm Connection",e);
            }
        }
        return  device.createRfcommSocketToServiceRecord(MY_UUID);
    }

    // Listens for Bluetooth broadcasts
    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if ((BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) && (device.getName().contains("CANBusGateway"))) {
                // Do something if connected
                Log.d(TAG, "CANBusGateway Connected");
                btConnect();
            }
            else if ((BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) && (device.getName().contains("CANBusGateway"))) {
                // Do something if disconnected
                Log.d(TAG, "CANBusGateway Disconnected");
            }
        }
    };

    // Check current Bluetooth state
    private void checkBTState() {
        // Check for Bluetooth support and then check to make sure it is turned on
        if(btAdapter==null) {
            Log.d(TAG, "Bluetooth not supported");
        } else {
            if (btAdapter.isEnabled()) {
                Log.d(TAG, "Bluetooth is on");
            } else {
                //Prompt user to turn on Bluetooth
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }

    // Bluetooth Connect Thread
    private class ConnectThread extends Thread {
        private final BluetoothSocket btSocket;
        private final BluetoothDevice btDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because btSocket is final
            BluetoothSocket tmp = null;
            btDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                tmp = createBluetoothSocket(device);
            } catch (IOException e) {
                Log.d(TAG,"Bluetooth socket create failed: " + e.getMessage() + ".");
            }
            btSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            btAdapter.cancelDiscovery();
            Log.d(TAG, "Connecting to the CANBusGateway...");
            try {
                // Connect the device through the socket. This will block until it succeeds or
                // throws an exception
                btSocket.connect();
                Log.d(TAG, "Connected to: " + btDevice.getName() + " " + btDevice.getAddress());
                MainActivity.this.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this,
                                getResources().getString(R.string.toast_connectedTo) +
                                        " " + btDevice.getName() + " " + btDevice.getAddress(),
                                Toast.LENGTH_LONG).show();

                    }
                });
            } catch (IOException connectException) {
                // Unable to connect
                Log.d(TAG, "Unable to connect to the CANBusGateway...");
                try {
                    btSocket.close();
                } catch (IOException closeException) {
                    Log.d(TAG,"Unable to close socket during connection failure");
                }
                return;
            }

            // Do work to manage the connection (in a separate thread)
            ConnectedThread btConnectedThread = new ConnectedThread(btSocket);
            btConnectedThread.start();
        }

        // Cancel an in-progress connection, and close the socket
        public void cancel() {
            try {
                btSocket.close();
            } catch (IOException e) {
                Log.d(TAG, "Unable to close Bluetooth socket");
            }
        }
    }

    // Connected bluetooth thread
    private class ConnectedThread extends Thread {
        private final InputStream btInStream;

        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpInput = null;

            // Get the input stream, using temp objects because member streams are final
            try {
                tmpInput = socket.getInputStream();
            } catch (IOException e) {
                Log.d(TAG, "IO Exception getting input stream");
            }
            btInStream = tmpInput;
        }

        public void run() {
            int bytesAvailable; // Bytes returned from read()
            final byte delimiter = 59; //This is the ASCII code for a ';'
            int readBufferPosition;
            readBufferPosition = 0;
            byte[] readBuffer = new byte[1024];
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytesAvailable = btInStream.available();
                    //Log.d(TAG,"bytesAvailable: " + bytesAvailable);
                    if(bytesAvailable > 0){
                        byte[] packetBytes = new byte[bytesAvailable];
                        btInStream.read(packetBytes);
                        for(int i=0;i<bytesAvailable;i++){
                            byte b = packetBytes[i];
                            if(b == delimiter){
                                byte[] encodedBytes = new byte[readBufferPosition];
                                System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                readBufferPosition = 0;
                                canBusMessages.obtainMessage(RECEIVE_MESSAGE, encodedBytes.length, -1, encodedBytes).sendToTarget(); // Send to message queue Handler
                            }else{
                                readBuffer[readBufferPosition++] = b;
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.d(TAG, "IO Exception while reading stream");
                    btConnectThread.cancel();
                    break;
                }
            }
        }
    }

    // Read raw text file
    private static String readRawTextFile(Context ctx, int resId)
    {
        InputStream inputStream = ctx.getResources().openRawResource(resId);

        InputStreamReader inputreader = new InputStreamReader(inputStream);
        BufferedReader buffreader = new BufferedReader(inputreader);
        String line;
        StringBuilder text = new StringBuilder();

        try {
            while (( line = buffreader.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
        } catch (IOException e) {
            return null;
        }
        return text.toString();
    }
}
