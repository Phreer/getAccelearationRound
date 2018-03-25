package com.example.phree.getaccelerationround;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.support.annotation.BoolRes;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.BoxInsetLayout;
import android.text.Layout;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.net.ServerSocket;

public class MainActivity extends WearableActivity {

    private static final SimpleDateFormat AMBIENT_DATE_FORMAT =
            new SimpleDateFormat("HH:mm", Locale.US);

    @Override
    protected void onDestroy() {
        sensorManager.unregisterListener(mySensorListener);
        super.onDestroy();
    }

    private BoxInsetLayout mContainerView;
    private TextView mTextView;
    private TextView mClockView;

    private static final String TAG = "GET_ACCELERATION_INFO";
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private MySensorListener mySensorListener;
    private float x_value, y_value, z_value = 0;
    private String info;
    private Boolean start_bool = false;
    private BufferedWriter writer = null;

    //Configuration
    public static final int BUFFER_SIZE = 4096;
    public static final int SEND_PORT = 9988;
    public static final int COMMAND_LEN = 5;
    public static final int SERV_PORT = 1893;
    public static final int SAMPLE_RATE = 33000;

    //state
    private static final int SW_S = 1;
    private static final int WC_S = 2;
    private static final int RC_S = 3; //writing
    private static final int CS_S = 4; //connected successfully
    private static final int ERR_S = 6; //error
    private static final int RD_S = 7; //error
    private static final int SEND_S = 8; //sending
    private static final int READY_S = 9; //ready




    private TextView x_value_view, y_value_view, z_value_view, state;
    private Button start_button, stop_button;
    private ProgressBar progress_bar;
    private LinearLayout info_layout;

    private Handler handler = new Handler(){
        public void handleMessage(Message msg){
            switch(msg.what){
                case SW_S:
                    state.setTextColor(Color.BLACK);
                    state.setText("Socket opened, wait for connection...");
                    start_button.setVisibility(View.INVISIBLE);
                    break;
                case WC_S:
                    state.setText("Wait for command...");break;
                case RC_S: //record
                    state.setText("Record...");
                    break;
                case CS_S: //connected successfully
                    state.setText("Connected.");
                    break;
                case ERR_S:
                    state.setTextColor(Color.RED);
                    state.setText("Error!");
                    start_button.setVisibility(View.VISIBLE);
                    break;
                case RD_S: //record done
                    state.setTextColor(Color.BLACK);
                    state.setText("Record Done.");
                    break;
                case SEND_S:
                    state.setText("Sending...");
                    break;
                case READY_S:
                    state.setText("Ready.");
                    start_button.setVisibility(View.VISIBLE);
                    break;
                default:
                    break;

            }
        }
    };
    private DecimalFormat df = new DecimalFormat("#####0.000000");
    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");


    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(mySensorListener, accelerometer, SensorManager.SENSOR_DELAY_UI, SAMPLE_RATE);
    }
    @Override
    protected void onPause() {
        sensorManager.unregisterListener(mySensorListener);
        super.onPause();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }
        setAmbientEnabled();

        mContainerView = (BoxInsetLayout) findViewById(R.id.container);
        mTextView = (TextView) findViewById(R.id.text);
        mClockView = (TextView) findViewById(R.id.clock);
        x_value_view = (TextView) findViewById(R.id.x_value);
        y_value_view = (TextView) findViewById(R.id.y_value);
        z_value_view = (TextView) findViewById(R.id.z_value);
        start_button = (Button) findViewById(R.id.start_button);
        stop_button = (Button) findViewById(R.id.stop_button);
        info_layout = (LinearLayout) findViewById(R.id.info_layout);
        progress_bar = (ProgressBar) findViewById(R.id.progress_bar);
        state = (TextView) findViewById(R.id.state);

        mySensorListener = new MySensorListener();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        start_button.setOnClickListener(new StartButtonListener());
        start_button.setVisibility(View.VISIBLE);
        stop_button.setOnClickListener(new StopButtonListener());
        stop_button.setVisibility(View.GONE);

    }

    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);
        updateDisplay();
    }

    @Override
    public void onUpdateAmbient() {
        super.onUpdateAmbient();
        updateDisplay();
    }

    @Override
    public void onExitAmbient() {
        updateDisplay();
        super.onExitAmbient();
    }

    @Override
    protected void onStop() {
        sensorManager.unregisterListener(mySensorListener);
        super.onStop();
    }

    private void updateDisplay() {
        if (isAmbient()) {
            mContainerView.setBackgroundColor(getResources().getColor(android.R.color.black));
            mTextView.setTextColor(getResources().getColor(android.R.color.white));
            mClockView.setVisibility(View.VISIBLE);

            mClockView.setText(AMBIENT_DATE_FORMAT.format(new Date()));
        } else {
            mContainerView.setBackground(null);
            mTextView.setTextColor(getResources().getColor(android.R.color.black));
            mClockView.setVisibility(View.GONE);
        }
    }

    class MySensorListener implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent event){
            x_value = event.values[0];
            y_value = event.values[1];
            z_value = event.values[2];
            x_value_view.setText(Float.toString(x_value));
            y_value_view.setText(Float.toString(y_value));
            z_value_view.setText(Float.toString(z_value));
            if(start_bool){
                String date = sdf.format(new Date());
                info = date + "," + df.format(x_value) + "," + df.format(y_value) +
                        "," + df.format(z_value) + "\n";
                write_info(info);
            }
        }
        private void write_info(String info){
            assert writer != null;
            try{
                writer.write(info);
            }catch (IOException e){
                e.printStackTrace();
                state.setTextColor(Color.RED);
                state.setText("An error occured while writing files!");
                stop_button.callOnClick();
            }
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            Log.i(TAG, "ACCURACY CHANGED!");
        }
    }

    class StartButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {

            v.setVisibility(View.GONE);
            new Thread(new Runnable(){
                @Override
                public void run(){
                    String filename = "test.txt";
                    try(ServerSocket s = new ServerSocket(SERV_PORT)){
                        Message msg1 = Message.obtain();
                        msg1.what = SW_S;
                        handler.sendMessage(msg1);
                        try(Socket incoming = s.accept()){
                            Message msg2 = Message.obtain();
                            msg2.what = WC_S;
                            handler.sendMessage(msg2);
                            InetAddress cli_address = incoming.getInetAddress();
                            InputStream inStream = incoming.getInputStream();
                            byte[] recvStream = new byte[COMMAND_LEN];
                            for(;;){
                                if((inStream.available())>=COMMAND_LEN){
                                    inStream.read(recvStream, 0, COMMAND_LEN);
                                    String message = new String(recvStream, 0, COMMAND_LEN);
                                    System.out.println("Recieve mesaage [" + message + "]");
                                    if(message.equals("HELLO")){
                                        Message msg3 = Message.obtain();
                                        msg3.what = CS_S;
                                        handler.sendMessage(msg3);
                                        Log.i(TAG, "onClick: Connected successfully.");
                                    }else if(message.equals("START")){
                                        FileOutputStream out = null;
                                        try{
                                            out = openFileOutput(filename, MODE_APPEND);
                                            writer = new BufferedWriter(new OutputStreamWriter(out));
                                        }
                                        catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                        Message msg4 = Message.obtain();
                                        if(writer!=null){
                                            start_bool = true;
                                            msg4.what = RC_S;
                                        }else{
                                            start_bool = false;
                                            msg4.what = ERR_S;
                                        }
                                        handler.sendMessage(msg4);
                                        Log.i(TAG, "onClick: Start");

                                    }else if(message.equals("STOP0")){
                                        //v.setVisibility(View.GONE);
                                        try{
                                            if(writer!=null){
                                                writer.close();

                                            }
                                        }catch (IOException e){
                                            e.printStackTrace();
                                        }
                                        start_bool = false;

                                        Log.i(TAG, "onClick: Stop");
                                    }else if(message.equals("SEND0")){
                                        Message msg5 = Message.obtain();

                                        msg5.what = SEND_S;
                                        handler.sendMessage(msg5);
                                        sendFile(cli_address, SEND_PORT, getBaseContext().getFilesDir(), filename);
                                        Message msg6 = Message.obtain();
                                        msg6.what = READY_S;
                                        handler.sendMessage(msg6);
//                                        else{
//                                            Message msg7 = Message.obtain();
//                                            msg7.what = ERR_S;
//                                            handler.sendMessage(msg7);
//                                        }
                                    }else{
                                        System.out.println("Unknown command.");
                                    }
                                }
                            }
                        }
                    }catch(IOException e){
                        e.printStackTrace();                    }
                }
            }).start();

        }
        private void sendFile(InetAddress cli_addr, int cli_port, File parent, String filename){
            // TODO Auto-generated method stub
            //FileInputStream in = null;
            //BufferedReader reader = null;
            try{
                byte[] buffer = new byte[BUFFER_SIZE];
                File file = new File(parent, filename);
                InputStream in = new FileInputStream(file);

                //in = openFileInput("test.txt");
                long s_len = file.length();
                byte[] byteLen = ByteBuffer.allocate(8).putLong(s_len).array();
                byte[] byteLen4 = Arrays.copyOfRange(byteLen, 4, 8);

                Socket s = new Socket(cli_addr, cli_port);//connect to cli_addr:port directly

                OutputStream out = s.getOutputStream();
                out.write(byteLen4, 0, 4);
                System.out.println("Write successfully.");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                for(;;){
                    int toSend = in.read(buffer);
                    if(toSend==-1) break;
                    out.write(buffer, 0, toSend);
                }
                s.close();
                in.close();
                out.close();
            }catch (Exception e){
                e.printStackTrace();            }
        }
    }
    class StopButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            progress_bar.setVisibility(View.VISIBLE);
            try{
                if(writer!=null){
                    writer.close();
                }
            }catch (IOException e){
                e.printStackTrace();
            }
            start_bool = false;
            progress_bar.setVisibility(View.GONE);
            Message msg = Message.obtain();
            msg.what = READY_S;
            handler.sendMessage(msg);
        }
    }
}
