## FlightRecorder
FlightRecorder is a simple Android library for your apps while they are in a beta stage. When your app starts FlightRecorder will start recording a screen session to be posted to a Dropbox.

### Features

* FlightRecorder can pick up your custom values from your app and post them alongside the video clips or crashes. You can specify user’s identity, app state, or anything that is meaningful to your issue reporting

* FlightRecorder can be configured as a crash handler to post a video clip of what user did right before app crashed. Of course a crash callstack is also uploaded

### Simple
No more anonymous or incomplete bug reports. Just drop in a tiny aar, make two calls when your main activity starts, and you’ll get video clips, crash stack, app’s state and users identity!

### One minute video walkthrough

[![1 minute video](http://img.youtube.com/vi/jfVwJJCTKls/0.jpg)](http://www.youtube.com/watch?v=jfVwJJCTKls)

### What's inside?

Just a handful of classes. It's anti-bloatware.

### Version requirements

The code is based on Android's MediaProjectionManager which means this library is compatible with Lollipop and up.

### Getting started
1. Clone the repo and build the aar. Or include it as a module in your Android Studio project
    try {
			String myApp = "App123";
            File picturesDir = Environment.getExternalStoragePublicDirectory(DIRECTORY_MOVIES);
            File outputRoot = new File(picturesDir, myApp);
            outputRoot.mkdirs();

            UploadProviderDropbox uploadProviderDropbox = new UploadProviderDropbox();
            uploadProviderDropbox.setAppKey("gp8g0xwp03rdqok");
            uploadProviderDropbox.setAppSecret("ahwa9bdmc6uup6k");

            Config config = FlightRecorder.createConfig();
            config.setCastName(myApp);
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
            Log.i("", "flight recorder init error:", e);
        }


1. And then when user allows screen casting
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FlightRecorder.CREATE_SCREEN_CAPTURE) {
            if (resultCode == Activity.RESULT_OK) {
                try {
					Log.i("", "User permitted to capture screen");
                    FlightRecorder.createAndStart(getApplicationContext(), resultCode, data);
                } catch (Exception e) {
                    Log.i("", "createAndStart", e);
                }
            } else {
				Log.i("", "User failed to allow screen capture");
            }
        }
    }

### Gradle
At the time of writing the gradle syntax for including an aar

    compile(name:'FlightRecorder-release', ext:'aar')

And also tell gradle where to find it
    repositories{
        flatDir{
            dirs 'libs'
        }
    }

### How it works
The library continuously rolls a series of short clips. A max of about 4 is maintained. All clips are uploaded and the server is meant to string them together.

### Dropbox app keys
Use your own.

### Contributing
1.	Bug reporting and bug fixing
1.	Additional backend providers such as Jira or something simpler, like a php page
1.	A callback into to get app to get user values on every clip roll
1.	A server script to string clips together. With user values as markers
