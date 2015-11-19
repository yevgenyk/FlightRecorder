package com.kolyakov.flightrecorder;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.VideoView;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.session.AppKeyPair;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StopActivity extends Activity {

    private static final String TAG = StopActivity.class.getSimpleName();
    VideoView mVideoView;
    Button mLeft, mRight, mSubmit;
    int mOffsetOffEnd;
    EditText mEditTextSubject;
    EditText mEditTextDescription;

    DropboxAPI<AndroidAuthSession> mApi;

    private boolean mLoggedIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stop);

        mVideoView = (VideoView) findViewById(R.id.videoView);
        mLeft = (Button) findViewById(R.id.left_button);
        mLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mOffsetOffEnd = moveToClip(mOffsetOffEnd-1);
            }
        });
        mRight = (Button) findViewById(R.id.right_button);
        mRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mOffsetOffEnd = moveToClip(mOffsetOffEnd + 1);
            }
        });

        mSubmit = (Button) findViewById(R.id.buttonSubmit);
        mSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLoggedIn) {
                    //===SUBMIT===//
                    startSubmission();
                } else {
                    //===Auth Business===//
                    if (mApi != null) {
                        mApi.getSession().startOAuth2Authentication(StopActivity.this);
                    }
                }
            }
        });

        mEditTextSubject = (EditText) findViewById(R.id.editTextSubject);
        mEditTextDescription = (EditText) findViewById(R.id.editTextDescription);

        FlightRecorder flightRecorder = FlightRecorder.getFlightRecorder();
        if (flightRecorder != null) {
            UploadProviderDropbox db = flightRecorder.getUploadProviderDropbox();
            AndroidAuthSession session = buildSession(this, db.getAppKey(), db.getAppSecret());
            mApi = new DropboxAPI<>(session);
            setLoggedIn(mApi.getSession().isLinked());
        }
        else {
            //--->Instanciated as a dead activity.
            //App was killed and notification was still around and user tapped on it
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();

        FlightRecorder flightRecorder = FlightRecorder.getFlightRecorder();
        if (flightRecorder != null) {
            flightRecorder.pauseRecording();

            File outputRoot = flightRecorder.getOutputFolder();
            if (outputRoot.exists()) {
                File[] files = outputRoot.listFiles();
                ScreenSession.sortFilesOnSequence(files);
                for (int i = files.length - 1; i >= 0; i--) {
                    File file = files[i];
                    Log.d(TAG, "video start: ===> " + file);
                    mOffsetOffEnd = i;
                    mVideoView.setTag(R.string.ClipIndexTag, i);
                    mVideoView.setVideoPath(file.getPath());
                    mVideoView.requestFocus();
                    mVideoView.start();
                    mVideoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                        @Override
                        public boolean onError(MediaPlayer mp, int what, int extra) {
                          return true;
                        }
                    });

                    break;
                }
            }

            UploadProviderDropbox db = flightRecorder.getUploadProviderDropbox();
            if (db != null) {
                AndroidAuthSession session = buildSession(this, db.getAppKey(), db.getAppSecret());
                if (session.authenticationSuccessful()) {
                    try {
                        session.finishAuthentication();
                        storeAuth(session);
                        mApi = new DropboxAPI<>(session);
                        setLoggedIn(true);
                    } catch (IllegalStateException e) {
                        showToast("Couldn't authenticate with Dropbox:" + e.getLocalizedMessage());
                        Log.i(TAG, "Error authenticating", e);
                    }
                }
            }
        }
    }

    private void showToast(String msg) {
        Toast error = Toast.makeText(this, msg, Toast.LENGTH_LONG);
        error.show();
    }

    private void storeAuth(AndroidAuthSession session) {
        String oauth2AccessToken = session.getOAuth2AccessToken();
        if (oauth2AccessToken != null) {
            SharedPreferences prefs = getSharedPreferences(UploadProviderDropbox.ACCOUNT_PREFS_NAME, 0);
            SharedPreferences.Editor edit = prefs.edit();
            edit.putString(UploadProviderDropbox.ACCESS_KEY_NAME, "oauth2:");
            edit.putString(UploadProviderDropbox.ACCESS_SECRET_NAME, oauth2AccessToken);
            edit.commit();
            return;
        }
    }

    @Override
    protected void onPause() {
        disconnect();
        super.onPause();
    }

    private void disconnect() {
        FlightRecorder flightRecorder = FlightRecorder.getFlightRecorder();
        if (flightRecorder != null) {
            flightRecorder.resumeRecording();
        }
    }

    private int moveToClip(int offsetOffEnd) {
        FlightRecorder flightRecorder = FlightRecorder.getFlightRecorder();
        if (flightRecorder == null) {
            return 0;
        }

        File outputRoot = flightRecorder.getOutputFolder();
        if (outputRoot.exists()) {
            File[] files = outputRoot.listFiles();
            int newPos = offsetOffEnd;
            if (newPos < 0) {
                newPos = 0;
            }
            if (offsetOffEnd >= files.length) {
                newPos = files.length - 1;
            }
            ScreenSession.sortFilesOnSequence(files);
            for (int i = newPos; i >= 0; i--) {
                File file = files[i];
                Log.d(TAG, "video start: ===> " + file);
                mVideoView.setTag(R.string.ClipIndexTag, i);
                mVideoView.setVideoPath(file.getPath());
                mVideoView.requestFocus();
                mVideoView.start();
                mVideoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mp, int what, int extra) {
                        return true;
                    }
                });

                return i;
            }
        }
        Log.d(TAG, "cant moveToClip: ===> " + offsetOffEnd);
        return 0;
    }

    private void setLoggedIn(boolean loggedIn) {
        mLoggedIn = loggedIn;
        if (loggedIn) {
            mSubmit.setText("Submit Issue");
        } else {
            mSubmit.setText("Login to Dropbox");
        }
    }

    private static void loadAuth(Context context, AndroidAuthSession session) {
        SharedPreferences prefs = context.getSharedPreferences(UploadProviderDropbox.ACCOUNT_PREFS_NAME, 0);
        String key = prefs.getString(UploadProviderDropbox.ACCESS_KEY_NAME, null);
        String secret = prefs.getString(UploadProviderDropbox.ACCESS_SECRET_NAME, null);
        if (key == null || secret == null || key.length() == 0 || secret.length() == 0) return;

        if (key.equals("oauth2:")) {
            // If the key is set to "oauth2:", then we can assume the token is for OAuth 2.
            session.setOAuth2AccessToken(secret);
        }
    }

    public static AndroidAuthSession buildSession(Context context, String key, String secret) {
        AppKeyPair appKeyPair = new AppKeyPair(key, secret);
        AndroidAuthSession session = new AndroidAuthSession(appKeyPair);
        loadAuth(context, session);
        return session;
    }

    void startSubmission() {
        FlightRecorder flightRecorder = FlightRecorder.getFlightRecorder();
        if (flightRecorder == null) {
            return;
        }

        try {
            String castName = flightRecorder.recordMetaFromUser(mEditTextSubject.getText().toString(), mEditTextDescription.getText().toString());
            if (castName==null) {
                return;
            }

            DateFormat format = new SimpleDateFormat("yyyy-MM-dd-HH-mm/", Locale.US);
            final String issueFolder = castName + "-" + format.format(new Date());
            File outputRoot = flightRecorder.getOutputFolder();
            if (outputRoot.exists()) {

                final File[] files = outputRoot.listFiles();
                ScreenSession.sortFilesOnSequence(files);

                int last = files.length - 1;
                uploadClip(files, last, issueFolder, files.length);
            }
        }
        catch (Exception e) {
            showToast(e.getMessage());
        }
    }

    void uploadClip(final File[] files, final int index, final String issueFolder, final int total) {
        if (index < 0) {
            finish();
            return;
        }

        File file = files[index];
        DropboxUpload upload = new DropboxUpload(this, mApi, issueFolder, file, index, total, new DropboxUpload.UploadProgress() {
            @Override
            public void OnComplete(boolean success, String error) {
                if (!success) {
                    showToast(error);
                    return;
                }
                uploadClip(files, index - 1, issueFolder, total);
            }
        }, true);
        upload.execute();
    }
}
