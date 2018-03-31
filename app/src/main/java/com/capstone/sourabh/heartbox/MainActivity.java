package com.capstone.sourabh.heartbox;

import android.provider.ContactsContract;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

// Plotting library dependencies
import com.androidplot.xy.AdvancedLineAndPointRenderer;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.util.Redrawer;
import com.androidplot.xy.XYSeries;

import android.graphics.Paint;

import java.lang.Number;
import java.lang.Thread;
import java.lang.Math;
import java.lang.ref.WeakReference;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Bluetooth objects
// Assistance from github.com/jpetrocik/bluetoothserial/
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class MainActivity extends AppCompatActivity {
    private XYPlot plot;
    private Redrawer redrawer;

    private BluetoothDevice bondedDevice;
    private BluetoothAdapter mAdapter;
    private BluetoothSocket serialSocket;
    InputStream serialInputStream;
    OutputStream serialOutputStream;
    SerialReader serialReader;
    MessageHandler messageHandler;
    Context context;
    AsyncTask<Void, Void, BluetoothDevice> connectionTask;
    String devicePrefix;
    private static String BMX_BLUETOOTH = "BMXBluetooth";
    public static String BLUETOOTH_CONNECTED = "bluetooth-connection-started";
    public static String BLUETOOTH_DISCONNECTED = "bluetooth-connection-lost";
    public static String BLUETOOTH_FAILED = "bluetooth-connection-failed";
    boolean connected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.landscape_activity_main);

        plot = findViewById(R.id.plot);
        DataModel dataModel = new DataModel(2000, 60);
        FadeFormatter formatter = new FadeFormatter(2000);

        formatter.setLegendIconEnabled(false);
        plot.addSeries(dataModel, formatter);
        plot.setRangeBoundaries(0, 10, BoundaryMode.FIXED);
        plot.setDomainBoundaries(0, 2000, BoundaryMode.FIXED);
        plot.setLinesPerRangeLabel(3);

        // Start collecting data from dataModel
        dataModel.start(new WeakReference<>(plot.getRenderer(AdvancedLineAndPointRenderer.class)));
        redrawer = new Redrawer(plot, 30, true);
    }

    /*
    Special class that implements a fading line. Lifted primarily from the ecg example code on
    github: https://github.com/halfhp/androidplot/blob/master/demoapp/src/main/java/com/androidplot
    /demos/ECGExample.java:81. (Designed to be used in conjunction with a circular buffer).
    */
    public class FadeFormatter extends AdvancedLineAndPointRenderer.Formatter {
        private int trailsize;

        private FadeFormatter(int trail_size) {
            this.trailsize = trail_size;
        }

        @Override
        public Paint getLinePaint(int thisIndex, int latestIndex, int seriesSize) {
            int offset;
            if(thisIndex > latestIndex) {
                offset = latestIndex + (seriesSize - thisIndex);
            } else {
                offset =  latestIndex - thisIndex;
            }
            float scale = 255f / trailsize;
            int alpha = (int) (255 - (offset * scale));
            getLinePaint().setAlpha(alpha > 0 ? alpha : 0);
            return getLinePaint();
        }
    }

    /*
    Special class that takes in data via bluetooth/usb and gets it into XYSeries to make it
    plottable. This class extends the XYSeries class
     */
    public class DataModel implements XYSeries {
        private final Number[] rawdata;
        private int latestIndex;
        private final Thread thread;
        private long delayMs;
        private boolean keepRunning;

        private WeakReference<AdvancedLineAndPointRenderer> renderRef;
        /**
         *
         * @param size Sample size contained within this model
         */
        private DataModel(int size, int updateFreq) {
            latestIndex = 0;
            delayMs = 1000 / updateFreq;
            rawdata = new Number[size];
            for(int i = 0; i < rawdata.length; i++) {
                rawdata[i] = 0;
            }
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (keepRunning) {
                            if (latestIndex >= rawdata.length) {
                                latestIndex = 0;
                            }

                            // Collect data and store in rawdata variable
                            rawdata[latestIndex] = 42;

                            if (renderRef.get() != null) {
                                renderRef.get().setLatestIndex(latestIndex);
                                Thread.sleep(delayMs);
                            } else {
                                keepRunning = false;
                            }
                            latestIndex++;
                        }
                    }
                    catch (InterruptedException e) {
                        keepRunning = false;
                    }
                }
            });
        }

        private void start(final WeakReference<AdvancedLineAndPointRenderer> renderRef) {
            this.renderRef = renderRef;
            keepRunning = true;
            thread.start();
        }

        public Number getY(int index) {
            return rawdata[index];
        }

        public Number getX(int index) {
            return index;
        }

        public String getTitle() {
            return "ECG";
        }

        public int size() {
            return rawdata.length;
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        redrawer.finish();
    }
    public void connect() {
        if(connected) {
            return;
        }
        if (connectionTask != null && connectionTask.getStatus()==AsyncTask.Status.RUNNING){
            Log.e(BMX_BLUETOOTH,"Connection request while attempting connection");
            return;
        }
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter== null || !bluetoothAdapter.isEnabled()) {
            return;
        }
        final List<BluetoothDevice> pairedDevices =
                new ArrayList<BluetoothDevice>(bluetoothAdapter.getBondedDevices());
        if(pairedDevices.size() > 0) {
            bluetoothAdapter.cancelDiscovery();
            // use AsyncTask to handle getting the connection
            connectionTask = new AsyncTask<Void, Void, BluetoothDevice>() {
                int MAX_ATTEMPTS = 20;
                int attempt_counter = 0;
                @Override
                protected BluetoothDevice doInBackground(Void... voids) {
                    while(!isCancelled()) {
                        for (BluetoothDevice device : pairedDevices) {
                            if (device.getName().toUpperCase().startsWith(devicePrefix)) {
                                Log.i(BMX_BLUETOOTH, attempt_counter +
                                        ": Attempting connection to " + device.getName());
                                try {
                                    try {
                                        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
                                        serialSocket = device.createRfcommSocketToServiceRecord(uuid);
                                    } catch (Exception ce) {
                                        serialSocket = connectViaReflection(device);
                                    }
                                    serialSocket.connect();
                                    serialInputStream = serialSocket.getInputStream();
                                    serialOutputStream = serialSocket.getOutputStream();
                                    connected = true;
                                    Log.i(BMX_BLUETOOTH, "Connected to " + device.getName());
                                    return device;
                                } catch (Exception e) {
                                    serialSocket = null;
                                    serialInputStream = null;
                                    serialOutputStream = null;
                                    Log.i(BMX_BLUETOOTH, e.getMessage());
                                }
                            }
                        }
                        try {
                            attempt_counter = attempt_counter + 1;
                            if (attempt_counter > MAX_ATTEMPTS)
                                this.cancel(false);
                            else
                                Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                    Log.i(BMX_BLUETOOTH, "Stopping connection attempts");
                    Intent intent = new Intent(BLUETOOTH_FAILED);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                    return null;
                }
                @Override
                protected void onPostExecute(BluetoothDevice result) {
                    super.onPostExecute(result);
                    bondedDevice = result;
                    serialReader = new SerialReader();
                    serialReader.start();
                    Intent intent = new Intent(BLUETOOTH_CONNECTED);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                }

            };
            connectionTask.execute();
        }
    }

    private BluetoothSocket connectViaReflection(BluetoothDevice device) throws Exception {
        Method m = device.getClass().getMethod("createRfcommSocket", new Class[] {int.class});
        return (BluetoothSocket) m.invoke(device, 1);
    }

    public int available() throws IOException{
        if (connected)
            return serialInputStream.available();

        throw new RuntimeException("Connection lost, reconnecting now.");
    }

    public int read() throws IOException{
        if (connected)
            return serialInputStream.read();

        throw new RuntimeException("Connection lost, reconnecting now.");
    }

    public int read(byte[] buffer) throws IOException{
        if (connected)
            return serialInputStream.read(buffer);

        throw new RuntimeException("Connection lost, reconnecting now.");
    }

    public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException{
        if (connected)
            return serialInputStream.read(buffer, byteOffset, byteCount);

        throw new RuntimeException("Connection lost, reconnecting now.");
    }

    public void write(byte[] buffer) throws IOException{
        if (connected)
            serialOutputStream.write(buffer);

        throw new RuntimeException("Connection lost, reconnecting now.");
    }

    public void write(int oneByte) throws IOException{
        if (connected)
            serialOutputStream.write(oneByte);

        throw new RuntimeException("Connection lost, reconnecting now.");
    }

    public void write(byte[] buffer, int offset, int count) throws IOException {
        serialOutputStream.write(buffer, offset, count);

        throw new RuntimeException("Connection lost, reconnecting now.");
    }

    public static interface MessageHandler {
        public int read(int bufferSize, byte[] buffer);
    }

    private class SerialReader extends Thread {
        private static final int MAX_BYTES = 125;
        byte[] buffer = new byte[MAX_BYTES];
        int bufferSize = 0;
        public void run() {
            Log.i("serialReader", "Starting serial loop");
            while(!isInterrupted()) {
                try {
                    if(available() > 0) {
                        int newBytes = read(buffer, bufferSize, MAX_BYTES - bufferSize);
                        if (newBytes > 0)
                            bufferSize += newBytes;
                        Log.d(BMX_BLUETOOTH, "read " + newBytes);

                    }
                    if(bufferSize > 0) {
                        int read = messageHandler.read(bufferSize, buffer);
                        if (read > 0) {
                            int index = 0;
                            for (int i = read; i < bufferSize; i++) {
                                buffer[index++] = buffer[i];
                            }
                            bufferSize = index;
                        }
                    } else {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ie) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    Log.e(BMX_BLUETOOTH, "Error reading serial data", e);
                }
            }
            Log.i(BMX_BLUETOOTH, "Closing serial loop");
        }

    }
}


