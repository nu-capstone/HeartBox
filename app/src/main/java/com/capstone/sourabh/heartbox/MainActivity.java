package com.capstone.sourabh.heartbox;

import android.provider.ContactsContract;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

// Plotting library dependencies
import com.androidplot.Plot;
import com.androidplot.xy.AdvancedLineAndPointRenderer;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.util.Redrawer;
import com.androidplot.xy.XYSeries;

import android.graphics.Paint;
import android.transition.Fade;

import java.lang.Number;
import java.lang.Thread;
import java.lang.Math;
import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity {
    private XYPlot plot;
    private Redrawer redrawer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.landscape_activity_main);

        plot = (XYPlot) findViewById(R.id.plot); //TODO: This needs to be addressed
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
                            rawdata[latestIndex] = Math.random();

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
}


