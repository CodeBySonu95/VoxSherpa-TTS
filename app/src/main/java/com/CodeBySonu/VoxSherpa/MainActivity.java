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
import com.google.android.material.*;
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

import androidx.fragment.app.Fragment;

public class MainActivity extends AppCompatActivity {
	
	private MainBinding binding;
	
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
		
		
		
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
		
		WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
		View rootLayout = binding.getRoot();
		ViewCompat.setOnApplyWindowInsetsListener(rootLayout, new androidx.core.view.OnApplyWindowInsetsListener() {
			@Override
			public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat windowInsets) {
				Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
				v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
				return WindowInsetsCompat.CONSUMED;
			}
		});
		
		binding.viewpager.setAdapter(new FragmentStateAdapter(this) {
			@NonNull
			@Override
			public Fragment createFragment(int position) {
				switch (position) {
					case 0: return new GenerateFragmentActivity();
					case 1: return new ModelsFragmentActivity();
					case 2: return new LibraryFragmentActivity();
					case 3: return new SettingFragmentActivity();
					default: return new GenerateFragmentActivity();
				}
			}
			
			@Override
			public int getItemCount() {
				return 4;
			}
		});
		
		binding.viewpager.setOffscreenPageLimit(3); 
		
		// 1. SYNC UI WITH SWIPES & CLICKS
		binding.viewpager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
			@Override
			public void onPageSelected(int position) {
				super.onPageSelected(position);
				
				int inactiveColor = Color.parseColor("#8E99AF");
				int activeColor = Color.parseColor("#1D61FF");
				
				binding.bgGenerate.setCardBackgroundColor(Color.TRANSPARENT);
				binding.bgModels.setCardBackgroundColor(Color.TRANSPARENT);
				binding.bgLibrary.setCardBackgroundColor(Color.TRANSPARENT);
				binding.bgSettings.setCardBackgroundColor(Color.TRANSPARENT);
				
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
					binding.iconGenerate.setColorFilter(Color.WHITE);
					binding.txtGenerate.setTextColor(Color.WHITE);
				} else if (position == 1) {
					binding.bgModels.setCardBackgroundColor(activeColor);
					binding.iconModels.setColorFilter(Color.WHITE);
					binding.txtModels.setTextColor(Color.WHITE);
				} else if (position == 2) {
					binding.bgLibrary.setCardBackgroundColor(activeColor);
					binding.iconLibrary.setColorFilter(Color.WHITE);
					binding.txtLibrary.setTextColor(Color.WHITE);
				} else if (position == 3) {
					binding.bgSettings.setCardBackgroundColor(activeColor);
					binding.iconSettings.setColorFilter(Color.WHITE);
					binding.txtSettings.setTextColor(Color.WHITE);
				}
			}
		});
		
		// 2. BOTTOM NAV CLICK LISTENERS (Instant Switch Trick)
		binding.navGenerate.setOnClickListener(v -> {
			binding.viewpager.setCurrentItem(0, false); 
		});
		
		binding.navModels.setOnClickListener(v -> {
			binding.viewpager.setCurrentItem(1, false);
		});
		
		binding.navLibrary.setOnClickListener(v -> {
			binding.viewpager.setCurrentItem(2, false);
		});
		
		binding.navSettings.setOnClickListener(v -> {
			binding.viewpager.setCurrentItem(3, false);
		});
		
	}
	
}