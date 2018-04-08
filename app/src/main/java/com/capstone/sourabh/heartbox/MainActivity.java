package com.capstone.sourabh.heartbox;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import android.content.Intent;
import android.content.Context;
import android.provider.ContactsContract;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

// Plotting library dependencies
import com.androidplot.xy.AdvancedLineAndPointRenderer;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.util.Redrawer;
import com.androidplot.xy.XYSeries;

import android.graphics.Paint;

import java.io.IOException;
import java.io.InputStream;
import java.lang.Number;
import java.lang.Thread;
import java.lang.Math;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

// Debug
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private int buf_size = 500;
    private XYPlot plot;
    private Redrawer redrawer;
    private BluetoothAdapter btAdapter;
    private BluetoothDevice btDevice;
    private BluetoothServerSocket btServerSocket;
    private InputStream btInputStream;
    private BluetoothSocket btSocket;
    private SerialReader serialReader;
    private boolean connected = false;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00815F9B34FB");
    private ConcurrentLinkedQueue<byte[]> btRawData = new ConcurrentLinkedQueue<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.landscape_activity_main);
        Context tmp = getApplicationContext();
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            Log.i("Bluetooth", "Device does not support bluetooth");
            Toast.makeText(tmp, "Device doesn't have bt", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(tmp, "Bluetooth adapter found", Toast.LENGTH_SHORT).show();
        }
        int REQUEST_ENABLE_BT = 22;
        if (!btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        // Query paired devices
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        if (pairedDevices.size() == 0) {
            Log.i("Bluetooth", "Could not find any connected devices");
            Toast.makeText(tmp, "No bluetooth device connected", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getApplicationContext(), "Found device", Toast.LENGTH_SHORT).show();
        }
        List<BluetoothDevice> pairedDevicesList = new ArrayList<BluetoothDevice>(pairedDevices);
        btDevice = pairedDevicesList.get(0);

        // Establish connection with chosen device
        // Need to figure out the MAC and UUID situation
        // NOTE: App will not begin plotting until a bluetooth connection is established (i.e.
        // UI does not respond)
        // TODO: Move to a separate thread
        BluetoothServerSocket tempServerSocket = null;
        try {
            tempServerSocket =
                    btAdapter.listenUsingRfcommWithServiceRecord("HeartBox", MY_UUID);
        } catch (Exception io) {
            Log.i("IO", "Could not listen for RFCOMM channel");
        }
        btServerSocket = tempServerSocket;
        try {
            btSocket = btServerSocket.accept();
            btInputStream = btSocket.getInputStream();
            btServerSocket.close();
            connected = true;
            Toast.makeText(getApplicationContext(), "Established RFCOMM channel",
                    Toast.LENGTH_LONG).show();
        } catch (Exception io) {
            // Needs something here!!!!!
            Log.i("IO", "Could not establish RFCOMM connection");
        }
        serialReader = new SerialReader();
        serialReader.start();
        plot = findViewById(R.id.plot);
        DataModel dataModel = new DataModel(buf_size, 60); // normally 2000
        FadeFormatter formatter = new FadeFormatter(100); // was 500
        formatter.setLegendIconEnabled(false);
        plot.addSeries(dataModel, formatter);
        plot.setRangeBoundaries(0, 10, BoundaryMode.FIXED);
        plot.setDomainBoundaries(0, 500, BoundaryMode.FIXED); //ub normally 2000
        plot.setLinesPerRangeLabel(3);

        // Start collecting data from dataModel
        Log.i("Plotting", "Plotting thread started");
        dataModel.start(new WeakReference<>(plot.getRenderer(AdvancedLineAndPointRenderer.class)));
        redrawer = new Redrawer(plot, 30, true);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == 22) {
            if (resultCode == RESULT_CANCELED) {
                Log.i("User", "Bluetooth access denied by user");
            }
        }
    }

    protected void onDestroy() {
        super.onDestroy();
        try {
            serialReader.join(1000);
            btSocket.close();
        } catch (Exception io) {
            Log.i("onDestroy", "Could not close socket");
        }
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
        private final Number[] ecgdata;
        private int latestIndex;
        private final Thread thread;
        private long delayMs;
        private boolean keepRunning;
        private float SPO2 = 0;
        private float BP = 0;
        private TextView textView = (TextView) findViewById(R.id.SPO2BP);

        private WeakReference<AdvancedLineAndPointRenderer> renderRef;
        /**
         *
         * @param size Sample size contained within this model
         */
        private DataModel(int size, int updateFreq) {
            latestIndex = 0;
            delayMs = 1000 / updateFreq;
            ecgdata = new Number[size];
            for(int i = 0; i < ecgdata.length; i++) {
                ecgdata[i] = 0;
            }
            textView.setText("hello");
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (keepRunning) {
                            if (latestIndex >= ecgdata.length) {
                                latestIndex = 0;
                            }
                            // Read bluetooth buffer and load into rawdata array
                            try {
                                Message next_message = new Message(btRawData.poll());
                                Log.i("DataModel", "Received message " +
                                        Float.toString(next_message.get_value()));
                                if(next_message.get_type() == MessageType.ECG) {
                                    ecgdata[latestIndex] = next_message.get_value();
                                } else {
                                    Log.i("DataModel", "Setting text");
                                    if (next_message.get_type() == MessageType.SPO2) {
                                        textView.setText(get_spo2bp_message(
                                                next_message.get_value(), "SPO2"));
                                    } else if (next_message.get_type() == MessageType.BP) {
                                        textView.setText(get_spo2bp_message(
                                                next_message.get_value(), "BP"));
                                    }
                                }
                            } catch (Exception e) {}

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

        private String get_spo2bp_message(float new_val, String new_type) {
            if (new_type.equals("SPO2")) {
                return "SPO2 " + Float.toString(new_val) + " BP " + Float.toString(BP);
            } else {
                return "SPO2 " + Float.toString(SPO2) + " BP " + Float.toString(new_val);
            }
        }

        private void start(final WeakReference<AdvancedLineAndPointRenderer> renderRef) {
            this.renderRef = renderRef;
            keepRunning = true;
            thread.start();
        }

        public Number getY(int index) {
            return ecgdata[index];
        }
        public Number getX(int index) { return index; }
        public String getTitle() {
            return "ECG";
        }
        public int size() {
            return ecgdata.length;
        }
    }

    public int available() throws IOException{
        if (connected)
            return btInputStream.available();

        throw new RuntimeException("Connection lost, reconnecting now.");
    }


    public int read() throws IOException {
        if (connected) {
            return btInputStream.read();
        }
        throw new IOException("Connection lost, reconnecting");
    }

    public int read(byte[] buffer) throws IOException{
        if (connected)
            return btInputStream.read(buffer);

        throw new RuntimeException("Connection lost, reconnecting now.");
    }

    public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException{
        if (connected)
            return btInputStream.read(buffer, byteOffset, byteCount);

        throw new RuntimeException("Connection lost, reconnecting now.");
    }



    // Thread class that manages the input stream, inspired by github/jpetrocik/bluetoothserial
    private class SerialReader extends Thread {
        int MESSAGE_SIZE = 5;
        private static final int MAX_BYTES = 125; // TODO: Is this buffer necessary?
        byte[] buffer = new byte[MAX_BYTES];
        int buf_index = 0;

        @Override
        public void run() {
            byte[] tmp_buffer = new byte[MESSAGE_SIZE];
            Log.i("SerialReader", "Beginning SerialReader instance");
            while (!isInterrupted()) {
                try {
                    if (available() == MESSAGE_SIZE) {
                        int newBytes = read(tmp_buffer, 0, MESSAGE_SIZE);
                        if (newBytes > 0) {
                            buf_index += newBytes;
                            Log.i("data", Arrays.toString(tmp_buffer));
                        }
                        Log.d("Bluetooth", "read " + newBytes);
                        btRawData.add(tmp_buffer);
                    }
                } catch(Exception io) {}
            }
        }
    }

    public void close() {
        connected = false;
        if (btInputStream != null)
            serialReader.interrupt();
        try {
            serialReader.join(1000);
        } catch(InterruptedException ie) {}
        try {
            btInputStream.close();
        } catch (Exception e) {
            Log.e("Bluetooth", "Failed releasing inputstream connection");
        }
        Log.i("Bluetooth", "Released bluetooth connections");
    }

}


