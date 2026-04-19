package com.CodeBySonu.VoxSherpa;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public class WaveformHelper {
    public static Bitmap createWaveformBitmap(byte[] pcmData, int width, int height) {
        if (pcmData == null || pcmData.length == 0 || width == 0 || height == 0) return null;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        
        Paint paint = new Paint();
        paint.setColor(Color.parseColor("#1D61FF")); // Blue Color
        paint.setStrokeWidth(5f); // Bar thickness
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setAntiAlias(true);

        int numBars = width / 8; // Number of vertical lines
        int samplesPerBar = pcmData.length / numBars;
        
        float centerY = height / 2f;

        for (int i = 0; i < numBars; i++) {
            long sum = 0;
            int startIndex = i * samplesPerBar;
            int endIndex = Math.min(startIndex + samplesPerBar, pcmData.length);
            
            // Calculate average amplitude for this bar (16-bit PCM)
            for (int j = startIndex; j < endIndex - 1; j += 2) {
                int sample = (pcmData[j] & 0xFF) | (pcmData[j + 1] << 8);
                sum += Math.abs((short) sample);
            }
            
            int count = (endIndex - startIndex) / 2;
            int average = count > 0 ? (int) (sum / count) : 0;
            
            // Scale amplitude to view height (Max 16-bit value is 32768)
            float fraction = (float) average / 32768f;
            float barHeight = (height * fraction * 1.5f); // 1.5 multiplier for better visual
            if (barHeight < 4f) barHeight = 4f; // Minimum bar height
            if (barHeight > height) barHeight = height - 4f;
            
            float x = (i * 8f) + 4f;
            canvas.drawLine(x, centerY - (barHeight / 2f), x, centerY + (barHeight / 2f), paint);
        }
        
        return bitmap;
    }
}
