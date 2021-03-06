package com.valkcam.ian.valkcam;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.ImageButton;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.github.controlwear.virtual.joystick.android.JoystickView;

import static com.valkcam.ian.valkcam.MainActivity.SERVERPORT;
import static com.valkcam.ian.valkcam.MainActivity.SERVER_IP;
import static com.valkcam.ian.valkcam.MainActivity.SOCKET_CHECK_SEND_DELTA;

/**
 * Todo:
 *  FIX CONCURRENCY ISSUE ON LINE 286
 *  Fix connection icon
 *  turn off socket server
<<<<<<< HEAD
 *  hold down on icon ro refresh webview
=======
 *  tttttttt - hold down on icon ro refresh webview
>>>>>>> ae5b3adf138c050fc3ef37b0e5c1824246f9a70f
 *  default image when not connected
 */
public class MainActivity extends Activity  {

    public static final long SOCKET_CHECK_SEND_DELTA = 10;//ms in between checking to send
    public static final long BUTTON_SEND_DELTA = 10;//ms in between sending
    public static final int SERVERPORT = 5000;
    public static final String SERVER_IP = "192.168.4.1";
    public static final String piAddr = "http://192.168.4.1:8000/index.html";
    private CommHandler cThread;

    WebView mWebView;
    ImageButton btnSettings;
    JoystickView joystick;
    View sView;

    private boolean socCon;

    onConnectionStateListener cUL = new onConnectionStateListener() {
        @Override
        public void onUpdate(boolean connected) {
            socCon = connected;
            if(connected){
                btnSettings.setImageResource(R.drawable.conn);
                mWebView.setVisibility(View.VISIBLE);
                sView.setVisibility(View.INVISIBLE);
                mWebView.reload();
            }else{
                btnSettings.setImageResource(R.drawable.disc);
                mWebView.setVisibility(View.INVISIBLE);
                sView.setVisibility(View.VISIBLE);
            }
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if(prefs.getBoolean("pref_socketStatus", true))
            cThread = new CommHandler();
        //Remove notification bar
        //this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        FullScreencall();

        mWebView = findViewById(R.id.webview);
        sView = findViewById(R.id.staticview);
        btnSettings = findViewById(R.id.btnSettings);



        //SETTINGS
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, PrefActivity.class));
            }
        });
        btnSettings.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                    mWebView.setVisibility(View.VISIBLE);
                    sView.setVisibility(View.INVISIBLE);
                    mWebView.reload();
                return true;
            }
        });


        //JOYSTICK
        joystick = (JoystickView) findViewById(R.id.joystickView);
        joystick.setOnMoveListener(new JoystickView.OnMoveListener() {
            long sentMillis = System.currentTimeMillis();
            @Override
            public void onMove(int angle, int strength) {
                if(cThread != null && System.currentTimeMillis() - sentMillis > BUTTON_SEND_DELTA && strength > 5){
                    cThread.setVar("x", (int)(strength*Math.cos(Math.toRadians(angle))));
                    cThread.setVar("y", (int)(strength*Math.sin(Math.toRadians(angle))));
                    sentMillis = System.currentTimeMillis();
                }
            }
        });

        //CONNECTION ICON
        if(prefs.getBoolean("pref_socketStatus", true))
            cThread.addConnectionListener(cUL);

        mWebView.loadUrl(piAddr);
        sView.setVisibility(View.INVISIBLE);
        mWebView.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePrefs();
        FullScreencall();
    }

    public static boolean updatePrefs = false;
    private void updatePrefs(){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if(cThread == null && prefs.getBoolean("pref_socketStatus", true)){
            cThread = new CommHandler();
            cThread.addConnectionListener(cUL);
        }else if(cThread != null && !prefs.getBoolean("pref_socketStatus", true) ){
            cThread.interrupt();
            cThread = null;
        }

        if(Integer.parseInt(prefs.getString("pref_mode", "0")) == 0)
            updateManual(true);
        else
            updateManual(false);

        if(updatePrefs && cThread != null){
            cThread.setVar("mode", Integer.parseInt(prefs.getString("pref_mode", "0")));
            cThread.setVar("quality", Integer.parseInt(prefs.getString("pref_quality", "1")));
            cThread.setVar("update", 1);//always do last in-case data is sent in two bursts
        }
    }

    private void updateManual(boolean manual) {
        if(manual) {
            joystick.setVisibility(View.VISIBLE);
        }else{
            joystick.setVisibility(View.INVISIBLE);
        }
    }


    public void FullScreencall() {
            //for new api versions.
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);

    }

    class CommHandler extends Thread{

        private HashMap<String, Integer> variables = new HashMap<>();
        private boolean updated = false;
        private boolean restart = false;
        public boolean connected = false;
        private final List<onConnectionStateListener> listeners = new ArrayList<onConnectionStateListener>();

        CommHandler() {
            super();
            this.start();
        }

        @Override
        public void run() {
            super.run();

            Socket socket = new Socket();
            OutputStream out;
            PrintWriter output;

            loop:
            while(!interrupted()) {
                try {
                    while (!interrupted() && !connected) {
                        try {
                            socket = new Socket(SERVER_IP, SERVERPORT);
                            setConnected(true);
                        } catch (Exception e) {
                            setConnected(false);
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e1) {
                                break loop;
                            }
                        }
                    }

                    out = socket.getOutputStream();
                    output = new PrintWriter(out);

                    long sentmillis = System.currentTimeMillis();
                    while (!interrupted()) {
                        if (System.currentTimeMillis() - sentmillis > SOCKET_CHECK_SEND_DELTA) {
                            sentmillis = System.currentTimeMillis();
                            synchronized (this) {
                                output.println(variables.toString());
                            }
                            if (output.checkError()) {
                                setConnected(false);
                                break;
                            }
                            variables.clear();
                            updated = false;
                        }
                    }

                    socket.close();
                    output.close();
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    setConnected(false);
                    socket = null;
                }
            }
        }

        private void setConnected(boolean conn){
            if(conn == connected)
                return;
            connected = conn;
            final boolean c = conn;
            Log.d("Valk","Seen listener : " + conn);
            for(onConnectionStateListener listener : listeners) {
                final onConnectionStateListener l = listener;
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        l.onUpdate(c);
                    }
                });
            }
        }

        public void setVar(String s, int i){
            synchronized (this) {
                variables.put(s, i);
                updated = true;
            }
        }

        public void addConnectionListener(onConnectionStateListener listener){
            listeners.add(listener);
        }

    }

    @FunctionalInterface
    interface onConnectionStateListener {
        void onUpdate(boolean connected);
    }
}
