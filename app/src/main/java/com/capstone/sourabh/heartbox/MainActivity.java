package com.capstone.sourabh.heartbox;

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

import java.lang.Number;
import java.lang.Thread;
import java.lang.Math;
import java.lang.ref.WeakReference;

// Debug
import android.view.Gravity;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private int buf_size = 500;
    private XYPlot plot;
    private Redrawer redrawer;
    private BluetoothSerial bluetoothserial;
    private String devicePrefix = "DESKTOP-9IM8JIS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Start bluetooth connection
        bluetoothserial = new BluetoothSerial(this, new BluetoothSerial.MessageHandler() {
            @Override
            public int read(int bufferSize, byte[] buffer) {
                return 0;
            }
        }, devicePrefix);
        setContentView(R.layout.landscape_activity_main);

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

    protected void onResume(Bundle SavedInstanceState) {
        super.onResume();
        bluetoothserial.onResume();
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
                            // Read bluetooth buffer and load into rawdata array
                            try {
                                Number datum = bluetoothserial.serialInputStream.read();
                                rawdata[latestIndex] = datum;
                            } catch (Exception e) {
                                Log.i("Buffer empty", "No data found in Input Stream");
                            }

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

        private void setRawData(Number data) {
            rawdata[latestIndex] = data;
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

}


