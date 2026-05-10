package com.CodeBySonu.VoxSherpa;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;

public class AudioSampleHelper {

    private static AudioSampleHelper instance;
    private MediaPlayer mediaPlayer;
    private String currentlyPlayingUrl = "";
    private SamplePlayListener currentListener;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;

    public interface SamplePlayListener {
        void onPlayStarted();
        void onPlayStopped();
        void onError(String error);
    }

    public static synchronized AudioSampleHelper getInstance() {
        if (instance == null) {
            instance = new AudioSampleHelper();
        }
        return instance;
    }

    private AudioSampleHelper() {
    }
    public void playSample(Context context, String audioUrlOrPath, SamplePlayListener listener) {
        
        if (mediaPlayer != null && mediaPlayer.isPlaying() && currentlyPlayingUrl.equals(audioUrlOrPath)) {
            stopAudio();
            return;
        }

        stopAudio();

        currentListener = listener;
        currentlyPlayingUrl = audioUrlOrPath;
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        // 🚀 AUDIO FOCUS LOGIC
        AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

        AudioManager.OnAudioFocusChangeListener focusChangeListener = focusChange -> {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                stopAudio();
            }
        };

        int focusResult;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build();
            focusResult = audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            focusResult = audioManager.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            );
        }
        if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            if (currentListener != null) {
                currentListener.onError("Audio focus denied by system");
            }
            return;
        }

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(playbackAttributes);

            mediaPlayer.setDataSource(audioUrlOrPath);
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                if (currentListener != null) {
                    currentListener.onPlayStarted();
                }
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                stopAudio();
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e("AudioSampleHelper", "Error playing audio: " + what);
                if (currentListener != null) {
                    currentListener.onError("Failed to play sample");
                }
                stopAudio();
                return true;
            });

            mediaPlayer.prepareAsync();

        } catch (Exception e) {
            Log.e("AudioSampleHelper", "Exception: " + e.getMessage());
            if (currentListener != null) {
                currentListener.onError(e.getMessage());
            }
            stopAudio();
        }
    }

    public void stopAudio() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.reset();
                mediaPlayer.release();
            } catch (Exception e) {
                Log.e("AudioSampleHelper", "Error stopping audio: " + e.getMessage());
            } finally {
                mediaPlayer = null;
                currentlyPlayingUrl = "";
            }
        }
        if (audioManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } else {
                audioManager.abandonAudioFocus(null);
            }
        }
        
        if (currentListener != null) {
            currentListener.onPlayStopped();
            currentListener = null; 
        }
    }
}
