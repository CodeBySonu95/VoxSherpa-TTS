package com.CodeBySonu.VoxSherpa;

import android.animation.*;
import android.app.*;
import android.content.*;
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
import com.google.android.play.corecommon.*;
import com.google.firebase.FirebaseApp;
import com.k2fsa.sherpa.onnx.*;
import com.tom_roush.pdfbox.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;
import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import android.view.View;

import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import androidx.viewpager2.widget.ViewPager2;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.google.android.gms.tasks.Task;
import android.content.IntentSender;
import com.google.android.material.snackbar.Snackbar;
import android.graphics.Color;

public class MainActivity extends AppCompatActivity {
	
	private MainBinding binding;
	public String sharedProcessText = "";
	private boolean isFirstTabLoad = true;
	private java.util.LinkedList<Integer> tabHistory = new java.util.LinkedList<>();
	private boolean isPoppingBackStack = false;
	
	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		binding = MainBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());
		initialize(_savedInstanceState);
		FirebaseApp.initializeApp(this);
		initializeLogic();
	}
	
	private void initialize(Bundle _savedInstanceState) {
	}
	
	private void initializeLogic() {
		com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(getApplicationContext());
		
		com.CodeBySonu.VoxSherpa.system.TtsDefaultHelper.syncDefaultVoices(this);
		
		// 4. UI & Window Settings
		getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
		androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
		android.view.View rootLayout = binding.getRoot();
		
		androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootLayout, new androidx.core.view.OnApplyWindowInsetsListener() {
			@Override
			public androidx.core.view.WindowInsetsCompat onApplyWindowInsets(android.view.View v, androidx.core.view.WindowInsetsCompat windowInsets) {
				androidx.core.graphics.Insets insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
				
				if (binding.viewpager != null) {
					android.view.ViewGroup.MarginLayoutParams vpParams = (android.view.ViewGroup.MarginLayoutParams) binding.viewpager.getLayoutParams();
					vpParams.topMargin = insets.top;
					binding.viewpager.setLayoutParams(vpParams);
				}
				
				android.view.View bottomNav = findViewById(R.id.bottom_nav_container);
				if (bottomNav != null) {
					int baseHeightPx = (int) (75 * getResources().getDisplayMetrics().density); 
					android.view.ViewGroup.LayoutParams navParams = bottomNav.getLayoutParams();
					navParams.height = baseHeightPx + insets.bottom;
					bottomNav.setLayoutParams(navParams);
					bottomNav.setPadding(0, 0, 0, insets.bottom);
				}
				
				return windowInsets;
			}
		});
		
		// 5. ViewPager2 Setup
		binding.viewpager.setAdapter(new androidx.viewpager2.adapter.FragmentStateAdapter(this) {
			@androidx.annotation.NonNull
			@Override
			public androidx.fragment.app.Fragment createFragment(int position) {
				switch (position) {
					case 0: return new GenerateFragmentActivity();
					case 1: return new ModelsFragmentActivity();
					case 2: return new LibraryFragmentActivity();
					case 3: return new SettingFragmentActivity();
					default: return new GenerateFragmentActivity();
				}
			}
			@Override
			public int getItemCount() { return 4; }
		});
		
		binding.viewpager.setOffscreenPageLimit(3); 
		
		binding.viewpager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
			@Override
			public void onPageSelected(int position) {
				super.onPageSelected(position);
				
				// --- CUSTOM HISTORY ANTI-LOOP LOGIC START ---
				// Update history only when the user is not pressing the back button
				if (!isPoppingBackStack) {
					// If this tab is already in the history, remove it (No duplicates)
					if (tabHistory.contains(position)) {
						tabHistory.remove(Integer.valueOf(position));
					}
					// Push the current tab to the top of the history
					tabHistory.push(position);
				}
				// Reset the flag for the next normal click
				isPoppingBackStack = false; 
				// --- CUSTOM HISTORY ANTI-LOOP LOGIC END ---
				
				int inactiveColor = android.graphics.Color.parseColor("#8E99AF");
				int activeColor = android.graphics.Color.parseColor("#1D61FF");
				
				binding.bgGenerate.setCardBackgroundColor(android.graphics.Color.TRANSPARENT);
				binding.bgModels.setCardBackgroundColor(android.graphics.Color.TRANSPARENT);
				binding.bgLibrary.setCardBackgroundColor(android.graphics.Color.TRANSPARENT);
				binding.bgSettings.setCardBackgroundColor(android.graphics.Color.TRANSPARENT);
				
				binding.iconGenerate.setColorFilter(inactiveColor);
				binding.iconModels.setColorFilter(inactiveColor);
				binding.iconLibrary.setColorFilter(inactiveColor);
				binding.iconSettings.setColorFilter(inactiveColor);
				
				binding.txtGenerate.setTextColor(inactiveColor);
				binding.txtModels.setTextColor(inactiveColor);
				binding.txtLibrary.setTextColor(inactiveColor);
				binding.txtSettings.setTextColor(inactiveColor);
				
				if (position == 0) {
					binding.bgGenerate.setCardBackgroundColor(activeColor);
					binding.iconGenerate.setColorFilter(android.graphics.Color.WHITE);
					binding.txtGenerate.setTextColor(android.graphics.Color.WHITE);
				} else if (position == 1) {
					binding.bgModels.setCardBackgroundColor(activeColor);
					binding.iconModels.setColorFilter(android.graphics.Color.WHITE);
					binding.txtModels.setTextColor(android.graphics.Color.WHITE);
				} else if (position == 2) {
					binding.bgLibrary.setCardBackgroundColor(activeColor);
					binding.iconLibrary.setColorFilter(android.graphics.Color.WHITE);
					binding.txtLibrary.setTextColor(android.graphics.Color.WHITE);
				} else if (position == 3) {
					binding.bgSettings.setCardBackgroundColor(activeColor);
					binding.iconSettings.setColorFilter(android.graphics.Color.WHITE);
					binding.txtSettings.setTextColor(android.graphics.Color.WHITE);
				}
				
				// Ad logic completely removed to keep it free and open-source.
				isFirstTabLoad = false;
			}
		});
		
		// Navigation Clicks
		binding.navGenerate.setOnClickListener(v -> binding.viewpager.setCurrentItem(0, false));
		binding.navModels.setOnClickListener(v -> binding.viewpager.setCurrentItem(1, false));
		binding.navLibrary.setOnClickListener(v -> binding.viewpager.setCurrentItem(2, false));
		binding.navSettings.setOnClickListener(v -> binding.viewpager.setCurrentItem(3, false));
		
	}
	
	
	@Override
	public void onStart() {
		super.onStart();
		android.content.Intent currentIntent = getIntent();
		if (currentIntent != null) {
			String action = currentIntent.getAction();
			String type = currentIntent.getType();
			
			// 1. Text Selection (Process Text) logic
			if (android.content.Intent.ACTION_PROCESS_TEXT.equals(action)) {
				CharSequence incomingText = currentIntent.getCharSequenceExtra(android.content.Intent.EXTRA_PROCESS_TEXT);
				if (incomingText != null) {
					sharedProcessText = incomingText.toString();
				}
			} 
			// 2. Share Menu (Action Send) logic
			else if (android.content.Intent.ACTION_SEND.equals(action) && type != null) {
				if ("text/plain".equals(type)) {
					// Check if it's actual text and NOT a file stream
					if (currentIntent.hasExtra(android.content.Intent.EXTRA_TEXT) && !currentIntent.hasExtra(android.content.Intent.EXTRA_STREAM)) {
						String sharedText = currentIntent.getStringExtra(android.content.Intent.EXTRA_TEXT);
						if (sharedText != null) {
							sharedProcessText = sharedText;
						}
					}
				}
			}
			
			// Intent clear karna taaki screen rotation par text dobara paste na ho
			currentIntent.setAction(null);
			setIntent(currentIntent);
		}
	}
	
	@Override
	public void onBackPressed() {
		
		if (tabHistory.size() > 1) {
			tabHistory.pop();
			int previousTab = tabHistory.peek();
			isPoppingBackStack = true;
			binding.viewpager.setCurrentItem(previousTab, false);
		} else {
			super.onBackPressed();
		}
	}
}