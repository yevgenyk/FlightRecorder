package com.kolyakov.flightrecorder;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.google.gson.Gson;

import static android.app.Notification.PRIORITY_MIN;
import static android.content.Context.MEDIA_PROJECTION_SERVICE;

public class FlightRecorder {
    protected static final String TAG = FlightRecorder.class.getSimpleName();

    public static final int CREATE_SCREEN_CAPTURE = 65530;
    public static String CRASH_SUBFOLDER = "crash";

    protected int ROLL_EVERY = 12000;

    protected PowerManager    mPowerManager;
    protected int             mResultCode;
    protected Intent          mData;
    protected Timer           myTimer;
    protected int             mRoll;
    protected Context         mContext;
    protected ScreenSession   mScreenSession;

    protected static FlightRecorder         mFlightRecorder = null;
    protected static File                   mOutputRoot;
    protected static SubmitIssueUserParams  mSubmitIssueUserParams;
    protected static UploadProviderDropbox  mUploadProviderDropbox;
    protected static boolean                mHandleCrashes = true;
    protected static String                 mCastName;
    protected static Config.VideoQuality    mVideoQuality = Config.VideoQuality.HIGH;

    private static class ConfigImpl implements Config {
        public void setOutputRoot(File outputRoot) {
            mOutputRoot = outputRoot;
        }
        public void setCastName(String castName) {
            mCastName = castName;
        }
        public void setHandleCrashes(boolean handleCrashes) {
            mHandleCrashes = handleCrashes;
        }
        public void setVideoQuality(VideoQuality videoQuality) {
            mVideoQuality = videoQuality;

        }
        public void addUploadProvider(UploadProvider uploadProvider) {
            if (!(uploadProvider instanceof UploadProviderDropbox)) {
                throw new UnsupportedClassVersionError();
            }
            mUploadProviderDropbox = (UploadProviderDropbox)uploadProvider;
        }
    }

    public static Config createConfig() {
        return new ConfigImpl();
    }

    private FlightRecorder() {
        //not meant to be an instance
    }

    public static FlightRecorder createAndStart(Context context, int resultCode, Intent data) throws Exception {
        if (mFlightRecorder != null) {
              throw new Exception("must be initialized only once");
        }
        mFlightRecorder = new FlightRecorder(context, resultCode, data);
        mFlightRecorder.start(mOutputRoot, mCastName);
        return mFlightRecorder;
    }

    public static FlightRecorder getFlightRecorder() {
          return mFlightRecorder;
    }

    protected FlightRecorder(Context context, int resultCode, Intent data) {
        mContext = context;
        mResultCode = resultCode;
        mData = data;
    }

    public static void initAndFireScreenCapture(Activity activity, Config config, SubmitIssueUserParams submitIssueUserParams) throws Exception {

        if (config == null) {
            throw new NullPointerException("config");
        }
        if (mOutputRoot == null) {
            throw new NullPointerException("outputRoot was not provided");
        }
        if (mCastName == null) {
            throw new Exception("CastName was not specified");
        }
        if (mUploadProviderDropbox == null) {
            throw new Exception("UploadProviderDropbox must be specified");
        }

        mSubmitIssueUserParams = submitIssueUserParams;

        if (mHandleCrashes) {
            final Thread.UncaughtExceptionHandler lastGuy = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread thread, Throwable e) {
                    if (lastGuy != null) {
                        //--->we get a crack at it first
                        handleUncaughtException(thread, e, false);
                        //--->let him finish
                        lastGuy.uncaughtException(thread, e);
                    } else {
                        handleUncaughtException(thread, e, true);
                    }
                }
            });
        }

        MediaProjectionManager manager = (MediaProjectionManager) activity.getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent intent = manager.createScreenCaptureIntent();
        activity.startActivityForResult(intent, CREATE_SCREEN_CAPTURE);
    }

    protected void start(File outputRoot, String castName) {

        mPowerManager = (PowerManager)mContext.getSystemService(Context.POWER_SERVICE);

        mScreenSession = new ScreenSession(mContext, mResultCode, mData, outputRoot, castName, 3);

        mRoll = -1;


        AndroidAuthSession session = StopActivity.buildSession(mContext,
                mUploadProviderDropbox.getAppKey(),
                mUploadProviderDropbox.getAppSecret());
        DropboxAPI<AndroidAuthSession> api = new DropboxAPI<>(session);
        if (api.getSession().isLinked()) {
            //===>We have a logged in session:
            pickupPreviousCrash(mContext, api);
        }

        resumeRecording();
    }

    protected void showNotification() {
        Context context = mContext.getApplicationContext();
        String title = context.getString(R.string.notification_recording_title);
        String subtitle = context.getString(R.string.notification_recording_subtitle);
        Notification.Builder builder = new Notification.Builder(context)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setSmallIcon(R.drawable.ic_upload)
                .setAutoCancel(true)
                .setOngoing(false)
                .setPriority(PRIORITY_MIN);

        Intent resultIntent = new Intent(context, StopActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addParentStack(StopActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );

        builder.setContentIntent(resultPendingIntent);
        Notification notification = builder.build();
        NotificationManager nm = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        notification.defaults |= Notification.DEFAULT_ALL;
        nm.notify(0, notification);
    }

    public int pauseRecording() {
        myTimer.cancel();
        mScreenSession.stopRecording(mRoll);
        return mRoll;
    }

    public int resumeRecording() {

        showNotification();

        myTimer = new Timer();
        mRoll ++ ;
        mScreenSession.startRecording(mRoll, mVideoQuality);

        myTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                nextRoll();
            }
        }, ROLL_EVERY, ROLL_EVERY);

        return mRoll;
    }

    protected void nextRoll() {
        boolean isScreenAwake = (Build.VERSION.SDK_INT < 20? mPowerManager.isScreenOn():mPowerManager.isInteractive());
        if (!isScreenAwake) {
            return;
        }
        mRoll++;
        mScreenSession.rollRecording(mRoll, mVideoQuality);
    }

    protected void moveLogic(String subfolder) {
        mScreenSession.moveLogic(subfolder);
    }

    public File getOutputFolder() {
        return mScreenSession.getOutputFolder();
    }

    protected static boolean isUIThread(){
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }

    protected static void handleUncaughtException(Thread thread, final Throwable e, final boolean exit)
    {
        e.printStackTrace();

        if (isUIThread()) {
            onCrash(e, exit);
        } else {

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    onCrash(e, exit);
                }
            });
        }
    }

    protected static void onCrash(Throwable throwable, boolean exit) {
        FlightRecorder flightRecorder = getFlightRecorder();
        if (flightRecorder != null) {
            flightRecorder.pauseRecording();
            try {
                flightRecorder.recordCrashStack(throwable);
                flightRecorder.moveLogic(FlightRecorder.CRASH_SUBFOLDER);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (exit) {
            System.exit(1);
        }
    }

    protected void maybeUserValues(OutputStream fo) {
        if (mSubmitIssueUserParams != null) {
            try {
                HashMap<String, String> userValues = mSubmitIssueUserParams.CurrentStateValues();
                if (userValues != null) {
                    Gson gson = new Gson();
                    String userValsAsString = gson.toJson(userValues);
                    if (userValsAsString != null) {
                        fo.write(userValsAsString.getBytes());
                        fo.write("\r\n\r\n".getBytes());
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    protected String recordCrashStack(Throwable throwable) throws Exception {
        if (mOutputRoot == null)
            return null;

        File file = new File(mOutputRoot + File.separator + "crash.txt");
        if (!file.exists()) {
            file.createNewFile();
        }

        OutputStream fo = new FileOutputStream(file);
        PrintWriter pw = new PrintWriter(fo);
        throwable.printStackTrace(pw);
        pw.flush();
        fo.write("\r\n\r\n".getBytes());

        maybeUserValues(fo);

        fo.close();

        return mScreenSession.getCastName();
    }


    public String recordMetaFromUser(String subject, String description) throws Exception {
        if (mOutputRoot == null)
            return null;

            File file = new File(mOutputRoot + File.separator + "user.txt");
            if (!file.exists()) {
                file.createNewFile();
            }

            OutputStream fo = new FileOutputStream(file);
            fo.write(subject.getBytes());
            fo.write("\r\n\r\n".getBytes());

            fo.write(description.getBytes());
            fo.write("\r\n\r\n".getBytes());

            maybeUserValues(fo);
            fo.close();

        return mScreenSession.getCastName();
    }


    protected void pickupPreviousCrash(final Context context, final DropboxAPI<AndroidAuthSession> api) {
        FlightRecorder flightRecorder = FlightRecorder.getFlightRecorder();
        if (flightRecorder == null) {
            return;
        }

        try {
            String castName = mScreenSession.getCastName();
            DateFormat format = new SimpleDateFormat("yyyy-MM-dd-HH-mm/", Locale.US);
            final String issueFolder = castName + "-" + format.format(new Date());
            File outputRoot = flightRecorder.getOutputFolder();
            if (outputRoot.exists()) {
                String crashFolderPath = outputRoot.getAbsoluteFile() + File.separator + CRASH_SUBFOLDER;
                File crashSubfolder = new File(crashFolderPath);
                if (crashSubfolder.exists()) {
                    final File[] files = crashSubfolder.listFiles();
                    ScreenSession.sortFilesOnSequence(files);

                    int last = files.length - 1;
                    uploadClip(context, api, crashSubfolder, files, last, issueFolder, files.length);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    void uploadClip(final Context context,
                    final DropboxAPI<AndroidAuthSession> api,
                    final File folder,
                    final File[] files,
                    final int index,
                    final String issueFolder, final int total) {
        if (index < 0) {
            if (folder.isDirectory())  {
                String[] filez = folder.list();
                for (int i = 0; i < filez.length; i++) {
                    new File(folder, filez[i]).delete();
                }
                Log.i(TAG, "---> Crash uploads completed: " + filez.length);
            }
            return;
        }

        final File file = files[index];
        DropboxUpload upload = new DropboxUpload(context, api, issueFolder, file, index, total, new DropboxUpload.UploadProgress() {
            @Override
            public void OnComplete(boolean success, String error) {
                if (!success) {
                    Log.e(TAG, "Crash upload error: " + error);
                    return;
                }
                else {
                    Log.i(TAG, "Uploaded: " + file.getAbsoluteFile());
                }
                uploadClip(context, api, folder, files, index - 1, issueFolder, total);
            }
        }, false);
        upload.execute();
    }

    protected UploadProviderDropbox getUploadProviderDropbox() {
        return mUploadProviderDropbox;
    }

}
