package com.CodeBySonu.VoxSherpa.system;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class VoxMediaController {
    
    private static VoxMediaController instance;
    private Context appContext;
    
    private MediaCommandListener generateListener;
    private MediaCommandListener libraryListener;
    
    public static final int MODE_GENERATE = 0;
    public static final int MODE_LIBRARY = 1;
    private int activeMode = MODE_GENERATE;

    public static final int STATE_STOPPED = 0;
    public static final int STATE_PLAYING = 1;
    public static final int STATE_PAUSED = 2;
    public static final int STATE_GENERATING = 3; 

    public interface MediaCommandListener {
        void onPlay();
        void onPause();
        void onStop(); 
        void onNext();
        void onPrevious();
    }

    private VoxMediaController(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static VoxMediaController getInstance(Context context) {
        if (instance == null) {
            instance = new VoxMediaController(context);
        }
        return instance;
    }

    public void setGenerateListener(MediaCommandListener listener) {
        this.generateListener = listener;
    }
    
    public void setLibraryListener(MediaCommandListener listener) {
        this.libraryListener = listener;
    }

    private MediaCommandListener getActiveListener() {
        return (activeMode == MODE_LIBRARY) ? libraryListener : generateListener;
    }

    public void triggerPlay() { if (getActiveListener() != null) getActiveListener().onPlay(); }
    public void triggerPause() { if (getActiveListener() != null) getActiveListener().onPause(); }
    public void triggerStop() { if (getActiveListener() != null) getActiveListener().onStop(); }
    public void triggerNext() { if (getActiveListener() != null) getActiveListener().onNext(); }
    public void triggerPrevious() { if (getActiveListener() != null) getActiveListener().onPrevious(); }

    public void updatePlaybackState(String title, String subtitle, int state, boolean isLibraryMode) {
        int requestedMode = isLibraryMode ? MODE_LIBRARY : MODE_GENERATE;
        
        if ((state == STATE_PAUSED || state == STATE_STOPPED) && this.activeMode != requestedMode) {
            return; 
        }
        
        this.activeMode = requestedMode;
        
        Intent intent = new Intent(appContext, VoxMediaService.class);
        intent.setAction("ACTION_UPDATE_STATE");
        intent.putExtra("title", title);
        intent.putExtra("subtitle", subtitle);
        intent.putExtra("state", state);
        intent.putExtra("isLibraryMode", isLibraryMode);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent);
        } else {
            appContext.startService(intent);
        }
    }

    public void hideNotification() {
        Intent intent = new Intent(appContext, VoxMediaService.class);
        intent.setAction("ACTION_STOP_SERVICE");
        appContext.startService(intent);
    }
}
