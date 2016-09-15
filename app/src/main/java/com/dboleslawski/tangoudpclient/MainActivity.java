package com.dboleslawski.tangoudpclient;

import com.google.atap.tango.ux.TangoUx;
import com.google.atap.tango.ux.TangoUxLayout;
import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoAreaDescriptionMetaData;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import android.app.FragmentManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Message;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    // tango stuff
    private Tango mTango;
    private TangoConfig mConfig;
    private TangoUx mTangoUx;
    private TangoUx.StartParams mTangoUxConfig;
    private TangoUxLayout mTangoUxLayout;
    // udp client
    private UDPClientThread mUDPClientThread;

    // for holding adfs later
    private ArrayList<String> uuidList = new ArrayList<String>();

    // read local data
    SharedPreferences sharedPref;

    // ui elements
    private SwitchCompat tangoSwitch;
    private CheckBox highRatePoseCheckbox;
    private CheckBox smoothPoseCheckbox;
    private CheckBox driftCorrectionCheckbox;
    private Button chooseADFButton;
    private EditText ipEditText;
    private EditText portEditText;
    private SwitchCompat udpSwitch;
    private Snackbar snackbar;

    private Boolean tangoRunning = false;
    private Boolean adfLoaded = false;

    // ---------------------------------------------------------------------------------------------
    // onCreate onResume onPause
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // get permission to load ADF files
        startActivityForResult(
                Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_ADF_LOAD_SAVE),
                Tango.TANGO_INTENT_ACTIVITYCODE);

        setupUiElements();
    }

    @Override
    protected void onResume() {
        super.onResume();

        //startTango();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if(tangoRunning) { stopTango(); }

        // save input fields
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("savedIp", ipEditText.getText().toString());
        editor.putString("savedPort", portEditText.getText().toString());
        editor.apply();
    }


    // ---------------------------------------------------------------------------------------------
    // tango stuff
    private void startTango() {
        mTango = new Tango(MainActivity.this, new Runnable() {
            // own tango thread - no ui calls here
            @Override
            public void run() {
                synchronized (MainActivity.this) {
                    mTangoUxConfig = new TangoUx.StartParams();
                    mConfig = setupTangoConfig(mTango);

                    try {
                        setTangoListeners();
                    } catch (TangoErrorException e) {
                        Log.e(TAG, "tango exception!", e);
                        showErrorSnackbar("tango exception!");
                    } catch (SecurityException e) {
                        Log.e(TAG, "motion tracking permission needed!", e);
                        showErrorSnackbar("motion tracking permission needed!");
                    }

                    try {
                        mTangoUx.start(mTangoUxConfig);
                        mTango.connect(mConfig);
                    } catch (TangoOutOfDateException e) {
                        if (mTangoUx != null) {
                            mTangoUx.showTangoOutOfDate();
                        }
                    } catch (TangoErrorException e) {
                        Log.e(TAG, "tango exception!", e);
                        showErrorSnackbar("tango exception!");
                    }
                }
            }
        });

        mTangoUx = new TangoUx(MainActivity.this);
        mTangoUxLayout = (TangoUxLayout) findViewById(R.id.layout_tango_ux);
        mTangoUx.setLayout(mTangoUxLayout);

        // get all ADF UUIDs
        uuidList = mTango.listAreaDescriptions();

        adfLoaded = true;
        tangoRunning = true;
    }

    private void stopTango() {
        synchronized (this) {
            try {
                mTangoUx.stop();
                mTango.disconnect();
            } catch (TangoErrorException e) {
                Log.e(TAG, "tango exception!", e);
                showErrorSnackbar("tango exception!");
            }
        }
        tangoRunning = false;
    }

    private TangoConfig setupTangoConfig(Tango tango) {
        TangoConfig config = new TangoConfig();
        config = tango.getConfig(config.CONFIG_TYPE_DEFAULT);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true); // enable motion tracking
        config.putBoolean(TangoConfig.KEY_BOOLEAN_AUTORECOVERY, true); // automatically recover

        // correction & smoothing stuff
        config.putBoolean(TangoConfig.KEY_BOOLEAN_HIGH_RATE_POSE, true); // 100hz interpolated
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DRIFT_CORRECTION, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_SMOOTH_POSE, true);

        return config;
    }

    private void setTangoListeners() {
        // lock configuration and connect to tango & select coordinate frame pair
        final ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));

        // listen for new tango data
        mTango.connectListener(framePairs, new OnTangoUpdateListener() {
            @Override
            public void onPoseAvailable(final TangoPoseData pose) {
                logPose(pose);
                if (mTangoUx != null) {
                    mTangoUx.updatePoseStatus(pose.statusCode);
                }
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData arg0) { /* not used */ }

            @Override
            public void onTangoEvent(final TangoEvent event) {
                if (mTangoUx != null) {
                    mTangoUx.updateTangoEvent(event);
                }
            }

            @Override
            public void onFrameAvailable(int cameraId) { /* not used */ }
        });
    }

    // TODO: clean up logPose()
    // TODO: write a solid tango <-> vvvv protocol
    private void logPose(TangoPoseData pose) {
        StringBuilder stringBuilder = new StringBuilder();

        float translation[] = pose.getTranslationAsFloats();
        float orientation[] = pose.getRotationAsFloats();

        stringBuilder.append(translation[0] + "," + translation[1] + "," + translation[2] +
                "," + orientation[0] + "," + orientation[1] +
                "," + orientation[2] + "," + orientation[3]);

        if(mUDPClientThread != null) {
            Message message = mUDPClientThread.handler.obtainMessage();
            Bundle bundle = new Bundle();
            bundle.putString("out", stringBuilder.toString());
            message.setData(bundle);
            mUDPClientThread.handler.sendMessage(message);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // udp stuff
    void startUdp() {
        mUDPClientThread = new UDPClientThread("UDPClient");
        mUDPClientThread.start();
        mUDPClientThread.prepare(ipEditText.getText().toString(), Integer.parseInt(portEditText.getText().toString()));
    }

    void stopUdp() {
        //mUDPClientThread.close();
        //mUDPClientThread.interrupt();
    }

    // ---------------------------------------------------------------------------------------------
    // ui elements
    private void setupUiElements() {
        tangoSwitch = (SwitchCompat) findViewById(R.id.switch_tango);
        highRatePoseCheckbox = (CheckBox) findViewById(R.id.checkbox_high_rate_pose);
        smoothPoseCheckbox = (CheckBox) findViewById(R.id.checkbox_smooth_pose);
        driftCorrectionCheckbox = (CheckBox) findViewById(R.id.checkbox_drift_correction);
        chooseADFButton = (Button) findViewById(R.id.button_choose_adf);
        ipEditText = (EditText) findViewById(R.id.editText_ip);
        portEditText = (EditText) findViewById(R.id.editText_port);
        udpSwitch = (SwitchCompat) findViewById(R.id.switch_udp);

        // populate the fields with saved data
        sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        ipEditText.setText(sharedPref.getString("savedIp", ""));
        portEditText.setText(sharedPref.getString("savedPort", ""));

        tangoSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if(isChecked) {
                    startTango();
                    highRatePoseCheckbox.setEnabled(false);
                    smoothPoseCheckbox.setEnabled(false);
                    driftCorrectionCheckbox.setEnabled(false);
                    chooseADFButton.setEnabled(false);
                } else {
                    stopTango();
                    highRatePoseCheckbox.setEnabled(true);
                    smoothPoseCheckbox.setEnabled(true);
                    driftCorrectionCheckbox.setEnabled(true);
                    chooseADFButton.setEnabled(true);
                }
            }
        });

        chooseADFButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(adfLoaded) {
                    showADFPicker();
                } else {
                    showStandardSnackbar("Start Tango Service once to load ADFs");
                }

            }
        });

        udpSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if(isChecked) {
                    // turn back if fields empty
                    if(ipEditText.getText().toString().trim().equals("")
                            || portEditText.getText().toString().trim().equals("")) {

                        udpSwitch.setChecked(false);

                        showErrorSnackbar("Fill in IP & PORT!");
                    } else {
                        disableEditText(ipEditText);
                        disableEditText(portEditText);

                        showStandardSnackbar("Starting UDP...");

                        startUdp();
                    }
                } else {
                    stopUdp();

                    showStandardSnackbar("Closing UDP...");

                    enableEditText(ipEditText);
                    enableEditText(portEditText);
                }
            }
        });
    }

    private void showADFPicker() {
        // get the names of the ADFs
        ArrayList<String> adfList = new ArrayList<String>();
        TangoAreaDescriptionMetaData metadata;
        for (String uuid : uuidList) {
            metadata = mTango.loadAreaDescriptionMetaData(uuid);
            byte [] name = metadata.get(TangoAreaDescriptionMetaData.KEY_NAME);
            adfList.add(new String(name));
        }
        String[] adfArray = adfList.toArray(new String[adfList.size()]);

        Bundle bundle = new Bundle();
        bundle.putStringArray("adfArray", adfArray);
        FragmentManager fragmentManager = getFragmentManager();
        ADFPickerFragment adfPickerFragment = new ADFPickerFragment();
        adfPickerFragment.setArguments(bundle);
        adfPickerFragment.show(fragmentManager, "fragment_adf_picker");
    }

    // ---------------------------------------------------------------------------------------------
    // ui utils
    private void disableEditText(EditText editText) {
        editText.setFocusable(false);
        editText.setEnabled(false);
        editText.setCursorVisible(false);
    }

    private void enableEditText(EditText editText) {
        editText.setFocusable(true);
        editText.setEnabled(true);
        editText.setCursorVisible(true);
        editText.setFocusableInTouchMode(true);
    }

    private void showStandardSnackbar(String msg) {
        snackbar = Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_SHORT);
        snackbar.show();
    }

    private void showErrorSnackbar(String msg) {
        snackbar = Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_SHORT);
        View sbView = snackbar.getView();
        TextView textView = (TextView) sbView.findViewById(android.support.design.R.id.snackbar_text);
        textView.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.colorError));
        snackbar.show();
    }

}
