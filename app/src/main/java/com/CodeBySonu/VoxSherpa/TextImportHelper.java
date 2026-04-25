package com.CodeBySonu.VoxSherpa;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class TextImportHelper {

    private static final int MAX_CHAR_LIMIT = 25000; 

    public interface TextImportCallback {
        void onSuccess(String text);
        void onError(String errorMessage);
    }

    public static void _readDocument(Context context, Uri uri, boolean isPdf, TextImportCallback callback) {
        new Thread(() -> {
            try {
                String resultText;
                
                if (isPdf) {
                    resultText = _extractTextFromPdf(context, uri);
                } else {
                    resultText = _extractTextFromTxt(context, uri);
                }

                final String finalText = resultText.length() > MAX_CHAR_LIMIT 
                        ? resultText.substring(0, MAX_CHAR_LIMIT) 
                        : resultText;

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (finalText.trim().isEmpty()) {
                        callback.onError("Selected file is empty or unreadable.");
                    } else {
                        callback.onSuccess(finalText.trim());
                    }
                });

            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    callback.onError("Failed to read the file. It might be corrupted or protected.");
                });
            }
        }).start();
    }

    private static String _extractTextFromPdf(Context context, Uri uri) throws Exception {
        PDFBoxResourceLoader.init(context.getApplicationContext());
        
        StringBuilder stringBuilder = new StringBuilder();
        
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             PDDocument document = PDDocument.load(inputStream)) {
            
            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = document.getNumberOfPages();
            
            for (int i = 1; i <= totalPages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(document);
                
                pageText = _fixHindiPdfText(pageText);
                
                stringBuilder.append(pageText).append("\n");
                
                if (stringBuilder.length() >= MAX_CHAR_LIMIT) {
                    break; 
                }
            }
        }
        return stringBuilder.toString();
    }

    private static String _extractTextFromTxt(Context context, Uri uri) throws Exception {
        StringBuilder stringBuilder = new StringBuilder();
        
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
                
                if (stringBuilder.length() >= MAX_CHAR_LIMIT) {
                    break; 
                }
            }
        }
        return stringBuilder.toString();
    }

        
    private static String _fixHindiPdfText(String text) {
        if (text == null || text.isEmpty()) return "";
        text = text.replaceAll("ि([क-हक़-य़])", "$1ि");
        text = text.replaceAll("([,\\.;:'\"!?\\-])([ािीुूृेैोौंँः])", "$2$1");
        text = text.replaceAll(" ([ािीुूृेैोौंँः])", "$1 ");
        text = text.replaceAll("ँू", "ूँ");
        text = text.replaceAll("ंू", "ूं");
        text = text.replaceAll("ें", "ें");
        text = text.replaceAll(" +", " ");

        return text;
    }
    
    }