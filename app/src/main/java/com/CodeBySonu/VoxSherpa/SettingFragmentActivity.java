package com.CodeBySonu.VoxSherpa;

import android.animation.*;
import android.app.*;
import android.app.Activity;
import android.content.*;
import android.content.SharedPreferences;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.net.*;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.util.*;
import android.view.*;
import android.view.View;
import android.view.View.*;
import android.view.animation.*;
import android.webkit.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.CodeBySonu.VoxSherpa.databinding.*;
import com.google.android.material.*;
import com.google.firebase.FirebaseApp;
import com.k2fsa.sherpa.onnx.*;
import com.tom_roush.pdfbox.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.io.File;

import android.graphics.Color;
import android.content.pm.PackageInfo;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;


public class SettingFragmentActivity extends Fragment {
	
	private SettingFragmentBinding binding;
	
	private SharedPreferences sp3;
	private SharedPreferences sp2;
	private SharedPreferences sp1;
	
	@NonNull
	@Override
	public View onCreateView(@NonNull LayoutInflater _inflater, @Nullable ViewGroup _container, @Nullable Bundle _savedInstanceState) {
		binding = SettingFragmentBinding.inflate(_inflater, _container, false);
		initialize(_savedInstanceState, binding.getRoot());
		FirebaseApp.initializeApp(getContext());
		initializeLogic();
		return binding.getRoot();
	}
	
	private void initialize(Bundle _savedInstanceState, View _view) {
		sp3 = getContext().getSharedPreferences("sp3", Activity.MODE_PRIVATE);
		sp2 = getContext().getSharedPreferences("sp2", Activity.MODE_PRIVATE);
		sp1 = getContext().getSharedPreferences("sp1", Activity.MODE_PRIVATE);
		
		binding.relativelayout24.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				Intent i = new Intent(Intent.ACTION_VIEW);
				i.setData(Uri.parse("https://codebysonu95.github.io/VoxSherpa-TTS/assets/Privacy.html"));
				startActivity(i);
			}
		});
		
		binding.linear2.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				Intent intent = new Intent(Intent.ACTION_VIEW,
				android.net.Uri.parse("https://github.com/CodeBySonu95/VoxSherpa-TTS"));
				
				if (intent.resolveActivity(getContext().getPackageManager()) != null) {
					startActivity(intent);
				} else {
					android.widget.Toast.makeText(getContext(), "No browser found", android.widget.Toast.LENGTH_SHORT).show();
				}
			}
		});
		
		binding.linear4.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				PackageManager pm = getActivity().getPackageManager();
				String versionName = "unknown";
				
				try {
					PackageInfo pInfo = pm.getPackageInfo(getActivity().getPackageName(), 0);
					versionName = pInfo.versionName;
				} catch (Exception e) {}
				
				String body =
				"App Version: " + versionName + "\n" +
				"Device: " + Build.MODEL + "\n" +
				"Android: " + Build.VERSION.RELEASE + "\n\n" +
				"Your feedback:\n";
				
				// 🚀 NAYA LOGIC: ACTION_SENDTO aur mailto: scheme ka use karein
				Intent intent = new Intent(Intent.ACTION_SENDTO);
				intent.setData(Uri.parse("mailto:")); // Ye STRICTLY sirf email apps ko bulayega (PayPal, WhatsApp blocked)
				intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"codebysonu95@gmail.com"});
				intent.putExtra(Intent.EXTRA_SUBJECT, "VoxSherpa Feedback");
				intent.putExtra(Intent.EXTRA_TEXT, body);
				
				try {
					startActivity(Intent.createChooser(intent, "Send Feedback via..."));
				} catch (ActivityNotFoundException e) {
					if (getView() != null) {
						Snackbar.make(getView(), "No email app found on your device.", Snackbar.LENGTH_SHORT)
						.setBackgroundTint(Color.parseColor("#FF4B4B")).setTextColor(Color.WHITE).show();
					} else {
						Toast.makeText(getContext(), "No email app found on your device.", Toast.LENGTH_SHORT).show();
					}
				}
				
			}
		});
	}
	
	private void initializeLogic() {
		try { 
			android.content.pm.PackageInfo pInfo = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0); 
			String version = pInfo.versionName; 
			binding.textview73.setText("v" + version + "-stable"); 
		} catch (Exception e) { 
			binding.textview73.setText("v1.0.0-stable"); 
		} 
		boolean isPunctuationOn = sp3.getBoolean("smart_punct", false); 
		boolean isEmotionOn = sp3.getBoolean("emotion_tags", false); 
		binding.switchPunctuation.setChecked(isPunctuationOn); 
		binding.switchEmotion.setChecked(isEmotionOn); 
		binding.switchPunctuation.setOnCheckedChangeListener((buttonView, isChecked) -> { 
			sp3.edit().putBoolean("smart_punct", isChecked).apply(); 
		}); 
		binding.switchEmotion.setOnCheckedChangeListener((buttonView, isChecked) -> { 
			if (isChecked) { 
				new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext()) 
				.setTitle("Beta Feature") 
				.setMessage("Emotion tagging is currently in beta. Please note that it may not perform perfectly with all voice models. Do you wish to continue?") 
				.setPositiveButton("Continue", (dialog, which) -> { 
					sp3.edit().putBoolean("emotion_tags", true).apply(); 
					dialog.dismiss(); 
				}) 
				.setNegativeButton("Cancel", (dialog, which) -> { 
					buttonView.setChecked(false); 
					dialog.dismiss(); 
				}) 
				.setCancelable(false) 
				.show(); 
			} else { 
				sp3.edit().putBoolean("emotion_tags", false).apply(); 
			} 
		}); 
		float currentPitch = sp3.getFloat("voice_pitch", 1.0f); 
		int pitchProgress; 
		if (currentPitch <= 1.0f) { 
			pitchProgress = (int) (((currentPitch - 0.25f) / 0.75f) * 50f); 
		} else { 
			pitchProgress = 50 + (int) (((currentPitch - 1.0f) / 1.0f) * 50f); 
		} 
		binding.seekbarPitch.setMax(100); 
		binding.seekbarPitch.setProgress(pitchProgress); 
		binding.pitchTv.setText(String.format(java.util.Locale.US, "%.2f", currentPitch)); 
		binding.seekbarPitch.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() { 
			@Override 
			public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) { 
				float pitchValue; 
				if (progress <= 50) { 
					pitchValue = 0.25f + (progress / 50f) * 0.75f; 
				} else { 
					pitchValue = 1.0f + ((progress - 50) / 50f) * 1.0f; 
				} 
				binding.pitchTv.setText(String.format(java.util.Locale.US, "%.2f", pitchValue)); 
				if (fromUser) { 
					sp3.edit().putFloat("voice_pitch", pitchValue).apply(); 
				} 
			} 
			@Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {} 
			@Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {} 
		}); 
		float currentSpeed = sp3.getFloat("voice_speed", 1.0f); 
		int speedProgress; 
		if (currentSpeed <= 1.0f) { 
			speedProgress = (int) (((currentSpeed - 0.25f) / 0.75f) * 50f); 
		} else { 
			speedProgress = 50 + (int) (((currentSpeed - 1.0f) / 1.0f) * 50f); 
		} 
		binding.seekbarSpeed.setMax(100); 
		binding.seekbarSpeed.setProgress(speedProgress); 
		binding.speedTv.setText(String.format(java.util.Locale.US, "%.2fx", currentSpeed)); 
		binding.seekbarSpeed.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() { 
			@Override 
			public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) { 
				float speedValue; 
				if (progress <= 50) { 
					speedValue = 0.25f + (progress / 50f) * 0.75f; 
				} else { 
					speedValue = 1.0f + ((progress - 50) / 50f) * 1.0f; 
				} 
				binding.speedTv.setText(String.format(java.util.Locale.US, "%.2fx", speedValue)); 
				if (fromUser) { 
					sp3.edit().putFloat("voice_speed", speedValue).apply(); 
				} 
			} 
			@Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {} 
			@Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {} 
		}); 
		String savedData = sp1.getString("models_data", "[]"); 
		int downloadedModelCount = 0; 
		long totalActualSizeBytes = 0; 
		try { 
			java.util.ArrayList<java.util.HashMap<String, Object>> modelList = new com.google.gson.Gson().fromJson(savedData, new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType()); 
			if (modelList != null) { 
				for (java.util.HashMap<String, Object> model : modelList) { 
					String onnxPath = model.containsKey("onnx_path") && model.get("onnx_path") != null ? model.get("onnx_path").toString() : ""; 
					String tokensPath = model.containsKey("tokens_path") && model.get("tokens_path") != null ? model.get("tokens_path").toString() : ""; 
					if (!onnxPath.isEmpty()) { 
						downloadedModelCount++; 
						java.io.File onnxFile = new java.io.File(onnxPath); 
						if (onnxFile.exists()) { 
							totalActualSizeBytes += onnxFile.length(); 
						} 
						if (!tokensPath.isEmpty()) { 
							java.io.File tokensFile = new java.io.File(tokensPath); 
							if (tokensFile.exists()) { 
								totalActualSizeBytes += tokensFile.length(); 
							} 
						} 
					} 
				} 
			} 
		} catch (Exception e) { } 
		binding.textview68.setText(downloadedModelCount + " local models imported"); 
		double sizeInMB = totalActualSizeBytes / (1024.0 * 1024.0); 
		if (sizeInMB > 1024) { 
			binding.totalSizeCountTv.setText(String.format(java.util.Locale.US, "%.2f GB", sizeInMB / 1024.0)); 
		} else { 
			binding.totalSizeCountTv.setText(String.format(java.util.Locale.US, "%.2f MB", sizeInMB)); 
		} 
		binding.clearCiv.setOnClickListener(view -> { 
			new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext()) 
			.setTitle("Clear All Cache") 
			.setMessage("This will delete all imported ONNX models and reset settings. Are you sure?") 
			.setPositiveButton("Clear", (dialog, which) -> { 
				String currentData = sp1.getString("models_data", "[]"); 
				try { 
					java.util.ArrayList<java.util.HashMap<String, Object>> mList = new com.google.gson.Gson().fromJson(currentData, new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType()); 
					if (mList != null) { 
						for (java.util.HashMap<String, Object> model : mList) { 
							String oPath = model.containsKey("onnx_path") && model.get("onnx_path") != null ? model.get("onnx_path").toString() : ""; 
							String tPath = model.containsKey("tokens_path") && model.get("tokens_path") != null ? model.get("tokens_path").toString() : ""; 
							if (!oPath.isEmpty()) new java.io.File(oPath).delete(); 
							if (!tPath.isEmpty()) new java.io.File(tPath).delete(); 
						} 
					} 
				} catch (Exception e) {} 
				java.io.File modelsDir = new java.io.File(getContext().getFilesDir(), "PiperModels"); 
				if (modelsDir.exists() && modelsDir.isDirectory()) { 
					java.io.File[] files = modelsDir.listFiles(); 
					if (files != null) { 
						for (java.io.File f : files) f.delete(); 
					} 
				} 
				sp1.edit().clear().apply(); 
				sp3.edit().clear().apply(); 
				binding.textview68.setText("0 local models imported"); 
				binding.totalSizeCountTv.setText("0.00 MB"); 
				binding.switchPunctuation.setChecked(false); 
				binding.switchEmotion.setChecked(false); 
				binding.seekbarPitch.setProgress(50); 
				binding.seekbarSpeed.setProgress(50); 
				com.google.android.material.snackbar.Snackbar.make(view, "Cache and models cleared successfully", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT) 
				.setBackgroundTint(android.graphics.Color.parseColor("#1D61FF")).setTextColor(android.graphics.Color.WHITE).show(); 
			}) 
			.setNegativeButton("Cancel", null) 
			.show(); 
		}); 
		
	}
	
}