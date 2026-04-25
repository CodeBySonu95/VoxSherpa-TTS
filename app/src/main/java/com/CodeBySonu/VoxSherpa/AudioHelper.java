package com.CodeBySonu.VoxSherpa;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;

public class AudioHelper {

    public static String saveWavFile(byte[] pcmData, String fileName, int sampleRate, Context context) {
        try {
            File musicDir = new File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_MUSIC), "VoxEngine");
            if (!musicDir.exists()) musicDir.mkdirs();

            File wavFile = new File(musicDir, fileName);

            try (FileOutputStream fos = new FileOutputStream(wavFile)) {
                long totalAudioLen = pcmData.length;
                long totalDataLen  = totalAudioLen + 36;
                long longSampleRate = sampleRate;
                int  channels      = 1;
                long byteRate      = 16L * longSampleRate * channels / 8;

                byte[] header = new byte[44];
                header[0]  = 'R'; header[1]  = 'I'; header[2]  = 'F'; header[3]  = 'F';
                header[4]  = (byte) (totalDataLen & 0xff);
                header[5]  = (byte) ((totalDataLen >> 8)  & 0xff);
                header[6]  = (byte) ((totalDataLen >> 16) & 0xff);
                header[7]  = (byte) ((totalDataLen >> 24) & 0xff);
                header[8]  = 'W'; header[9]  = 'A'; header[10] = 'V'; header[11] = 'E';
                header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
                header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
                header[20] = 1;  header[21] = 0;
                header[22] = (byte) channels; header[23] = 0;
                header[24] = (byte) (longSampleRate & 0xff);
                header[25] = (byte) ((longSampleRate >> 8)  & 0xff);
                header[26] = (byte) ((longSampleRate >> 16) & 0xff);
                header[27] = (byte) ((longSampleRate >> 24) & 0xff);
                header[28] = (byte) (byteRate & 0xff);
                header[29] = (byte) ((byteRate >> 8)  & 0xff);
                header[30] = (byte) ((byteRate >> 16) & 0xff);
                header[31] = (byte) ((byteRate >> 24) & 0xff);
                header[32] = (byte) (2 * 16 / 8); header[33] = 0;
                header[34] = 16; header[35] = 0;
                header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
                header[40] = (byte) (totalAudioLen & 0xff);
                header[41] = (byte) ((totalAudioLen >> 8)  & 0xff);
                header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
                header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

                fos.write(header, 0, 44);
                fos.write(pcmData);
            }

            return wavFile.getAbsolutePath();
        } catch (Exception e) {
            return "";
        }
    }
}
