package com.example.cerebro;

import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.content.Intent;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.util.Log;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.media.MediaPlayer;

public class MainActivity extends AppCompatActivity {

    private Camera camera;
    private MediaPlayer mp;
    private boolean isFlashOn = false;
    private boolean isSoundOn = false;
    private String IPport = "";
    private int freq = 3;
    private Parameters params;
    private MQTTclient cl;
    private Handler handler;
    private WifiReceiver wifiReceiver;
    private WifiManager wifiManager;
    private String regex = "((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(?:\\.|$)){4}";
    final Pattern ptn = Pattern.compile(regex);
    SharedPreferences.Editor editor;

    ColorDrawable[] BackGroundOn = {
            new ColorDrawable(Color.parseColor("#0F702C")),
            new ColorDrawable(Color.parseColor("#303030")),
    };
    ColorDrawable[] BackGroundOff = {
            new ColorDrawable(Color.parseColor("#841F13")),
            new ColorDrawable(Color.parseColor("#303030"))
    };

    TransitionDrawable transitiondrawableOn, transitiondrawableOff;
    LinearLayout background;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setIcon(R.mipmap.ic_launcher);

        mp = MediaPlayer.create(getApplicationContext(), R.raw.sound);
        getCamera();

        cl = new MQTTclient();
        handler = new Handler();
        wifiReceiver = new WifiReceiver();

        final Button btnOn = findViewById(R.id.btnOn);
        final Button btnOff = findViewById(R.id.btnOff);
        background = findViewById(R.id.linearLayout);
        transitiondrawableOn = new TransitionDrawable(BackGroundOn);
        transitiondrawableOff = new TransitionDrawable(BackGroundOff);

        // Setting up listeners for the on/off buttons
        btnOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                turnOnSound();
                turnOnFlash();
                background.setBackground(transitiondrawableOn);
                transitiondrawableOn.startTransition(5000);
            }
        });
        btnOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                turnOffSound();
                turnOffFlash();
                background.setBackground(transitiondrawableOff);
                transitiondrawableOff.startTransition(1000);
            }
        });

        // Preferences to save the user input for IP and port
        editor = getSharedPreferences("IPport", MODE_PRIVATE).edit();
        editor.putString("IP", "");
        editor.putString("port", "");
        editor.apply();
    }

    // Starting the mqtt connection and setting up a listener for the incoming messages
    private void startMqtt() {
        cl.runClient();
        cl.setListener(new MQTTclient.ChangeListener() {
            @Override
            public void onChange() {
                if (cl.getMessage_string().equals("turn On")) {
                    turnOnSound();
                    turnOnFlash();
//                    background.setBackground(transitiondrawableOn);
//                    transitiondrawableOn.startTransition(5000);
                } else if (cl.getMessage_string().equals("turn Off")) {
                    turnOffSound();
                    turnOffFlash();
//                    background.setBackground(transitiondrawableOff);
//                    transitiondrawableOff.startTransition(1000);
                }
            }
        });
    }

    // Get the camera
    private void getCamera() {
        if (camera == null) {
            try {
                camera = Camera.open();
                params = camera.getParameters();
            } catch (RuntimeException e) {
                Log.e("getCamera failed: ", e.getMessage());
            }
        }
    }

    // Turning on sound
    private void turnOnSound() {
        turnOffSound();
        if (!isSoundOn) {
            mp.start();
            isSoundOn = true;
        }
    }

    // Turning off sound
    private void turnOffSound() {
        if (isSoundOn) {
            mp.pause();
            mp.seekTo(0);
            isSoundOn = false;
        }
    }

    // Turning On flash
    private void turnOnFlash() {
        if (!isFlashOn) {
            if (camera == null || params == null) {
                return;
            }
            params = camera.getParameters();
            params.setFlashMode(Parameters.FLASH_MODE_TORCH);
            camera.setParameters(params);
            camera.startPreview();
            isFlashOn = true;

            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    turnOffFlash();
                }
            }, 5000);  // keep the flashlight on while the sound is on
        }
    }

    // Turning Off flash
    private void turnOffFlash() {
        if (isFlashOn) {
            if (camera == null || params == null) {
                return;
            }
            params = camera.getParameters();
            params.setFlashMode(Parameters.FLASH_MODE_OFF);
            camera.setParameters(params);
            camera.stopPreview();
            isFlashOn = false;
        }
    }

    // alert the user that the app is about to exit
    @Override
    public void onBackPressed() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Exit");
        builder.setMessage("Do you really want to exit? ");
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        });
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        builder.show();
    }

    // mqtt disconnects, sound stops and the camera is released
    @Override
    protected void onDestroy() {
        super.onDestroy();
        cl.disconnect();
        mp.stop();
        if (camera != null) {
            camera.release();
            camera = null;
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getCamera();

        // on starting the app checks internet connection
        final int delay = 8; // seconds
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        handler.postDelayed(new Runnable(){
            public void run(){
                registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
                wifiManager.startScan();
                handler.postDelayed(this, delay * 1000);
            }
        }, delay);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (this.wifiReceiver != null) {
            unregisterReceiver(wifiReceiver);
            wifiReceiver = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        // IP and port input
        if (id == R.id.action_settings) {
            SharedPreferences prefs = getSharedPreferences("IPport", MODE_PRIVATE);
            String IPpref = prefs.getString("IP", "No IP defined");
            String portPref = prefs.getString("port", "No port defined");

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Connection Settings");
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            View view = View.inflate(this, R.layout.settings_dialog, layout);

            TextView IPview = view.findViewById(R.id.ip_input);
            IPview.setText("IP address:", TextView.BufferType.EDITABLE);
            final EditText IP = view.findViewById(R.id.IP);
            IP.setText(IPpref);

            TextView portView = view.findViewById(R.id.port_input);
            portView.setText("Port:", TextView.BufferType.EDITABLE);
            final EditText port = view.findViewById(R.id.port);
            port.setText(portPref);

            // mqtt connects if the input is correct
            builder.setPositiveButton("Done", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    Matcher mtch = ptn.matcher(IP.getText().toString());
                    if (mtch.find()) {
                        IPport = "tcp://" + IP.getText().toString() + ":" + port.getText().toString();
                        Log.i("IPportInput", IPport);
                        cl.setIPport(IPport);
                        startMqtt();

                        editor = getSharedPreferences("IPport", MODE_PRIVATE).edit();
                        editor.putString("IP", IP.getText().toString());
                        editor.putString("port", port.getText().toString());
                        editor.apply();
                    } else {
                        Log.e("Input Error", "IP is not in correct form");
                    }
                }
            });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                }
            });
            builder.setView(layout);
            builder.show();
        }

        // frequency input, choose between 1 and 10 and send message to java app
        if (id == R.id.action_frequency) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Set Frequency");
            LayoutInflater inflater = this.getLayoutInflater();
            View dialogView = inflater.inflate(R.layout.frequency_dialog, null);
            builder.setView(dialogView);

            final NumberPicker numberPicker = dialogView.findViewById(R.id.dialog_number_picker);
            numberPicker.setMinValue(1);
            numberPicker.setMaxValue(10);
            numberPicker.setValue(freq);
            numberPicker.setWrapSelectorWheel(true);

            numberPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
                @Override
                public void onValueChange(NumberPicker numberPicker, int id, int i) {
                    Log.d("numberPicker", "onValueChange: ");
                }
            });
            TextView IPview = dialogView.findViewById(R.id.sec);
            IPview.setText("sec", TextView.BufferType.EDITABLE);

            builder.setPositiveButton("Done", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    Log.i("frequencyInput", "" + numberPicker.getValue());
                    freq = numberPicker.getValue();
                    cl.sendMessage(String.valueOf(freq));
                }
            });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) { dialog.cancel(); }
            });
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        }

        // exit app
        if (id == R.id.action_exit) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }

        return super.onOptionsItemSelected(item);
    }
}
