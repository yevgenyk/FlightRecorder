package com.kolyakov.flightrecorder;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.display.VirtualDisplay;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

import static android.content.Context.MEDIA_PROJECTION_SERVICE;
import static android.content.Context.NOTIFICATION_SERVICE;
import static android.content.Context.WINDOW_SERVICE;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
import static android.media.MediaRecorder.OutputFormat.MPEG_4;
import static android.media.MediaRecorder.VideoEncoder.H264;
import static android.media.MediaRecorder.VideoSource.SURFACE;

final class ScreenSession {

  protected static final String TAG = ScreenSession.class.getSimpleName();
  public static String FILE_PREFIX = "SCREEN-";

  protected Context context;
  protected int resultCode;
  protected Intent data;
  protected File outputRoot;

  protected NotificationManager notificationManager;
  protected WindowManager windowManager;
  protected MediaProjectionManager projectionManager;

  protected MediaRecorder recorder;
  protected MediaProjection projection;
  protected VirtualDisplay display;
  protected String outputFile;
  protected String castName;
  protected int rollCount;

  ScreenSession(Context context, int resultCode, Intent data, File outputRoot, String castName, int rollCount) {
    this.context = context;
    this.resultCode = resultCode;
    this.data = data;
    this.rollCount = rollCount;
    this.outputRoot = outputRoot;
    this.castName = castName;

    notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
    windowManager = (WindowManager) context.getSystemService(WINDOW_SERVICE);
    projectionManager = (MediaProjectionManager) context.getSystemService(MEDIA_PROJECTION_SERVICE);
  }

  protected RecordingInfo getRecordingInfo(Config.VideoQuality videoQuality) {
    DisplayMetrics displayMetrics = new DisplayMetrics();
    WindowManager wm = (WindowManager) context.getSystemService(WINDOW_SERVICE);
    wm.getDefaultDisplay().getRealMetrics(displayMetrics);
    int displayWidth = displayMetrics.widthPixels;
    int displayHeight = displayMetrics.heightPixels;
    int displayDensity = displayMetrics.densityDpi;
    Configuration configuration = context.getResources().getConfiguration();
    boolean isLandscape = configuration.orientation == ORIENTATION_LANDSCAPE;
    CamcorderProfile camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
    if (videoQuality == Config.VideoQuality.LOW) {
        camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
    }
    int cameraWidth = camcorderProfile != null ? camcorderProfile.videoFrameWidth : -1;
    int cameraHeight = camcorderProfile != null ? camcorderProfile.videoFrameHeight : -1;
    int cameraFrameRate = 20;
    int sizePercentage = 50;
    return calculateRecordingInfo(displayWidth, displayHeight, displayDensity, isLandscape,
        cameraWidth, cameraHeight, cameraFrameRate, sizePercentage);
  }

  protected void startRecording(int val, Config.VideoQuality videoQuality) {

    if (val <=0) {
        restartLogic();
    }

    RecordingInfo recordingInfo = getRecordingInfo(videoQuality);

    recorder = new MediaRecorder();
    recorder.setVideoSource(SURFACE);
    recorder.setOutputFormat(MPEG_4);
    recorder.setVideoFrameRate(recordingInfo.frameRate);
    recorder.setVideoEncoder(H264);
    recorder.setVideoSize(recordingInfo.width, recordingInfo.height);
    recorder.setVideoEncodingBitRate(8 * 1000 * 1000);

    String outputName = FILE_PREFIX + val + ".mp4";

    outputFile = new File(outputRoot, outputName).getAbsolutePath();
    recorder.setOutputFile(outputFile);

    try {
      recorder.prepare();
    } catch (IOException e) {
      throw new RuntimeException("IOException when calling prepare MediaRecorder ", e);
    }

    projection = projectionManager.getMediaProjection(resultCode, data);

    Surface surface = recorder.getSurface();
    display = projection.createVirtualDisplay(castName, recordingInfo.width, recordingInfo.height,
            recordingInfo.density, VIRTUAL_DISPLAY_FLAG_PRESENTATION, surface, null, null);

    recorder.start();
  }

  public void rollRecording(int val, Config.VideoQuality videoQuality) {

      keepLogic(rollCount);

      stopRecording(val);
      startRecording(val, videoQuality);
  }

  public void stopRecording(int val) {

      projection.stop();
      recorder.stop();
      display.release();
      recorder.release();

      Log.d(TAG, "stopped: ===> " + val);
  }

  static RecordingInfo calculateRecordingInfo(int displayWidth, int displayHeight,
      int displayDensity, boolean isLandscapeDevice, int cameraWidth, int cameraHeight,
      int cameraFrameRate, int sizePercentage) {

    displayWidth = displayWidth * sizePercentage / 100;
    displayHeight = displayHeight * sizePercentage / 100;

    if (cameraWidth == -1 && cameraHeight == -1) {
      return new RecordingInfo(displayWidth, displayHeight, cameraFrameRate, displayDensity);
    }

    int frameWidth = isLandscapeDevice ? cameraWidth : cameraHeight;
    int frameHeight = isLandscapeDevice ? cameraHeight : cameraWidth;
    if (frameWidth >= displayWidth && frameHeight >= displayHeight) {
      return new RecordingInfo(displayWidth, displayHeight, cameraFrameRate, displayDensity);
    }
    if (isLandscapeDevice) {
      frameWidth = displayWidth * frameHeight / displayHeight;
    } else {
      frameHeight = displayHeight * frameWidth / displayWidth;
    }
    return new RecordingInfo(frameWidth, frameHeight, cameraFrameRate, displayDensity);
  }

  static final class RecordingInfo {
    final int width;
    final int height;
    final int frameRate;
    final int density;

    RecordingInfo(int width, int height, int frameRate, int density) {
      this.width = width;
      this.height = height;
      this.frameRate = frameRate;
      this.density = density;
    }
  }

    protected void restartLogic() {
        if (outputRoot.exists()) {
            File[] files = outputRoot.listFiles();
            for (int i = 0; i < files.length; ++i) {
                File file = files[i];
                file.delete();
            }
        }
    }

    public void moveLogic(String subfolder) {
        if (outputRoot.exists()) {
            String subfolderFullPath = outputRoot.getAbsoluteFile() + File.separator + subfolder;
            File folder = new File(subfolderFullPath);
            folder.mkdirs();

            File[] files = outputRoot.listFiles();
            for (int i = 0; i < files.length; ++i) {
                File file = files[i];
                String  newPath = outputRoot + File.separator + subfolder + File.separator + file.getName();
                File to = new File(newPath);
                file.renameTo(to);
            }
        }
    }

    protected void keepLogic(int keep) {
        if (outputRoot.exists()) {
            File[] files = outputRoot.listFiles();
            sortFilesOnSequence(files);
            for (int i = files.length - 1 - keep; i >= 0; --i) {
                File file = files[i];
                file.delete();
            }
        }
    }

    public static void sortFilesOnSequence(File [] files) {
        Arrays.sort(files, new Comparator() {
            public int compare(Object o1, Object o2) {
                String f1 = ((File) o1).getName();
                String f2 = ((File) o2).getName();
                f1 = f1.replace(FILE_PREFIX, "");
                f1 = f1.replace(".mp4", "");
                f2 = f2.replace(FILE_PREFIX, "");
                f2 = f2.replace(".mp4", "");
                long f1int = Long.MIN_VALUE;
                long f2int = Long.MIN_VALUE;
                try {
                    f1int = Long.parseLong(f1);
                }catch (Exception e) {
                }
                try {
                    f2int = Long.parseLong(f2);
                }
                catch (Exception e) {
                }
                if (f1int < f2int) {
                    return -1;
                } else if (f1int > f2int) {
                    return +1;
                } else {
                    return 0;
                }
            }
        });
    }

    public File getOutputFolder() {
        return outputRoot;
    }

    public String getCastName() { return  castName; }
}
