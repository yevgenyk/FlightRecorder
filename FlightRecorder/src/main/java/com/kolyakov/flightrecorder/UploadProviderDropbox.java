package com.kolyakov.flightrecorder;

public class UploadProviderDropbox implements UploadProvider  {

    protected String mAppKey;
    protected String mAppSecret;

    public static final String ACCOUNT_PREFS_NAME = "DROPBOX_prefs";
    public static final String ACCESS_KEY_NAME = "DROPBOX_ACCESS_KEY";
    public static final String ACCESS_SECRET_NAME = "DROPBOX_ACCESS_SECRET";


    public void setAppKey(String appKey) {
        mAppKey = appKey;

    }
    public void setAppSecret(String appSecret) {
        mAppSecret = appSecret;
    }

    public String getAppKey() {
        return mAppKey;
    }

    public String getAppSecret() {
        return mAppSecret;
    }
}
