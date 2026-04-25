package com.CodeBySonu.VoxSherpa.system;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;

import com.CodeBySonu.VoxSherpa.R;
import com.CodeBySonu.VoxSherpa.MainActivity;

public class VoxMediaService extends Service {

    private MediaSession mediaSession;
    private static final String CHANNEL_ID = "VoxSherpaMedia";
    private static final int NOTIFY_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();

        mediaSession = new MediaSession(this, "VoxMediaSession");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() { VoxMediaController.getInstance(VoxMediaService.this).triggerPlay(); }
            @Override
            public void onPause() { VoxMediaController.getInstance(VoxMediaService.this).triggerPause(); }
            @Override
            public void onStop() { VoxMediaController.getInstance(VoxMediaService.this).triggerStop(); }
            @Override
            public void onSkipToNext() { VoxMediaController.getInstance(VoxMediaService.this).triggerNext(); }
            @Override
            public void onSkipToPrevious() { VoxMediaController.getInstance(VoxMediaService.this).triggerPrevious(); }
        });
        mediaSession.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            
            if ("CMD_PLAY".equals(action)) {
                VoxMediaController.getInstance(this).triggerPlay();
            } else if ("CMD_PAUSE".equals(action)) {
                VoxMediaController.getInstance(this).triggerPause();
            } else if ("CMD_STOP".equals(action)) {
                VoxMediaController.getInstance(this).triggerStop();
            } else if ("CMD_PREV".equals(action)) {
                VoxMediaController.getInstance(this).triggerPrevious();
            } else if ("CMD_NEXT".equals(action)) {
                VoxMediaController.getInstance(this).triggerNext();
            } else if ("ACTION_UPDATE_STATE".equals(action)) {
                String title = intent.getStringExtra("title");
                String subtitle = intent.getStringExtra("subtitle");
                int state = intent.getIntExtra("state", VoxMediaController.STATE_STOPPED);
                boolean isLibraryMode = intent.getBooleanExtra("isLibraryMode", false);
                long duration = intent.getLongExtra("duration", -1L); 
                
                updateNotification(title, subtitle, state, isLibraryMode, duration);
            } else if ("ACTION_STOP_SERVICE".equals(action)) {
                stopForeground(true);
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }
    
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        
        // Handle app swipe-close: Stop playback, kill engines, and destroy service completely
        try {
            VoxMediaController.getInstance(this).triggerStop();
            
            new Thread(() -> {
                try { com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().cancel(); } catch (Exception ignored) {}
                try { com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().cancel(); } catch (Exception ignored) {}
            }).start();
        } catch (Exception ignored) {}

        stopForeground(true);
        stopSelf();
    }
    
    private PendingIntent getActionIntent(String action) {
        Intent intent = new Intent(this, VoxMediaService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    // Helper method to convert Vector Drawable XML to Bitmap safely
    private android.graphics.Bitmap getBitmapFromVector(int drawableId) {
        android.graphics.drawable.Drawable drawable = androidx.core.content.ContextCompat.getDrawable(this, drawableId);
        if (drawable == null) return null;

        int width = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 200;
        int height = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 200;

        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    private void updateNotification(String title, String subtitle, int state, boolean isLibraryMode, long duration) {
        
        // Convert Vector icon to Bitmap
        android.graphics.Bitmap albumArtBitmap = getBitmapFromVector(R.drawable.icon_audiotrack);
        
        // Fallback in case vector conversion fails for any reason
        if (albumArtBitmap == null) {
            albumArtBitmap = android.graphics.BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        }
        
        MediaMetadata.Builder metaBuilder = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title != null ? title : "VoxSherpa")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, subtitle != null ? subtitle : "Text-To-Speech")
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumArtBitmap);
        
        if (duration > 0) {
            metaBuilder.putLong(MediaMetadata.METADATA_KEY_DURATION, duration);
        }
        mediaSession.setMetadata(metaBuilder.build());

        int pbState = PlaybackState.STATE_NONE;
        long actions = 0; 
        
        if (state == VoxMediaController.STATE_GENERATING) {
            pbState = PlaybackState.STATE_BUFFERING;
            actions = PlaybackState.ACTION_STOP; 
        } else {
            actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SEEK_TO;
            if (isLibraryMode) {
                actions |= PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
            }
            if (state == VoxMediaController.STATE_PLAYING) {
                pbState = PlaybackState.STATE_PLAYING;
            } else if (state == VoxMediaController.STATE_PAUSED) {
                pbState = PlaybackState.STATE_PAUSED;
            }
        }

        mediaSession.setPlaybackState(new PlaybackState.Builder()
                .setState(pbState, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .setActions(actions)
                .build());

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setSmallIcon(R.drawable.icon_audiotrack)
                .setLargeIcon(albumArtBitmap)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setOngoing(state == VoxMediaController.STATE_PLAYING || state == VoxMediaController.STATE_GENERATING)
                .setVisibility(Notification.VISIBILITY_PUBLIC);

        Intent openAppIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (openAppIntent == null) openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(contentIntent);

        if (state == VoxMediaController.STATE_GENERATING) {
            builder.setProgress(0, 0, true);
        } else {
            if (isLibraryMode) builder.addAction(android.R.drawable.ic_media_previous, "Previous", getActionIntent("CMD_PREV"));

            if (state == VoxMediaController.STATE_PLAYING) {
                builder.addAction(R.drawable.icon_pause_circle, "Pause", getActionIntent("CMD_PAUSE"));
            } else {
                builder.addAction(R.drawable.icon_play_circle, "Play", getActionIntent("CMD_PLAY"));
            }

            if (isLibraryMode) {
                builder.addAction(android.R.drawable.ic_media_next, "Next", getActionIntent("CMD_NEXT"));
                builder.setStyle(new Notification.MediaStyle().setMediaSession(mediaSession.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
            } else {
                builder.setStyle(new Notification.MediaStyle().setMediaSession(mediaSession.getSessionToken()).setShowActionsInCompactView(0));
            }
        }

        startForeground(NOTIFY_ID, builder.build());
        
        if (state == VoxMediaController.STATE_STOPPED || state == VoxMediaController.STATE_PAUSED) {
            stopForeground(false);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Media Playback", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Controls for VoxSherpa audio playback");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaSession != null) mediaSession.release();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
