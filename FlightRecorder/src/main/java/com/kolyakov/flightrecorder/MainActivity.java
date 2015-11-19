package com.kolyakov.flightrecorder;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import static android.os.Environment.DIRECTORY_MOVIES;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button button1 = (Button)findViewById(R.id.button1);
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Object o = null;
                o.equals(null);
            }
        });

        try {
            File picturesDir = Environment.getExternalStoragePublicDirectory(DIRECTORY_MOVIES);
            File outputRoot = new File(picturesDir, "App123");
            outputRoot.mkdirs();

            UploadProviderDropbox uploadProviderDropbox = new UploadProviderDropbox();
            uploadProviderDropbox.setAppKey("gp8g0xwp03rdqok");
            uploadProviderDropbox.setAppSecret("ahwa9bdmc6uup6k");

            Config config = FlightRecorder.createConfig();
            config.setCastName("App123");
            config.setHandleCrashes(true);
            config.setOutputRoot(outputRoot);
            config.setVideoQuality(Config.VideoQuality.HIGH);
            config.addUploadProvider(uploadProviderDropbox);

            FlightRecorder.initAndFireScreenCapture(this, config, new SubmitIssueUserParams() {
                @Override
                public HashMap<String, String> CurrentStateValues() {
                    HashMap<String, String> myValues = new HashMap<>();
                    myValues.put("key1", "value1");
                    myValues.put("key2", "value2");
                    return myValues;
                }
            });
        }
        catch (Exception e) {
            Log.i("===", "flight recorder init error:", e);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FlightRecorder.CREATE_SCREEN_CAPTURE) {
            if (resultCode == Activity.RESULT_OK) {
                try {
                    Log.i("===", "User permitted to capture screen");
                    FlightRecorder.createAndStart(getApplicationContext(), resultCode, data);
                } catch (Exception e) {
                    Log.i("===", "===", e);
                }
            } else {
                Log.i("", "User failed to allow screen capture");
            }
        }

    }
}
