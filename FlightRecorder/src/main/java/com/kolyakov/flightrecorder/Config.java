package com.kolyakov.flightrecorder;

import java.io.File;

public interface Config {

    enum VideoQuality {
        LOW, HIGH;
    }
    void setOutputRoot(File file);
    void setCastName(String castName);
    void setHandleCrashes(boolean handleCrashes);
    void setVideoQuality(VideoQuality videoQuality);
    void addUploadProvider(UploadProvider uploadProvider);
}
