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
import android.view.View.*;
import android.view.animation.*;
import android.webkit.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
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
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import android.media.AudioManager;
import android.media.AudioFocusRequest;
import android.os.Build;
import android.content.Context;
import com.CodeBySonu.VoxSherpa.system.VoxMediaController;


public class LibraryFragmentActivity extends Fragment {
	
	private LibraryFragmentBinding binding;
	ArrayList<HashMap<String, Object>> allList;
	boolean isFavTab = false;
	MediaPlayer mediaPlayer = null;
	int currentlyPlayingPos = -1;
	RecyclerView.Adapter adapter;
	java.util.ArrayList<java.util.HashMap<String, Object>> displayList = new java.util.ArrayList<>();
	android.os.Handler seekHandler;
	Runnable seekRunnable;
	android.media.AudioManager audioManager;
	android.media.AudioManager.OnAudioFocusChangeListener focusListener;
	
	private SharedPreferences sp2;
	private SharedPreferences sp1;
	private SharedPreferences sp3;
	
	@NonNull
	@Override
	public View onCreateView(@NonNull LayoutInflater _inflater, @Nullable ViewGroup _container, @Nullable Bundle _savedInstanceState) {
		binding = LibraryFragmentBinding.inflate(_inflater, _container, false);
		initialize(_savedInstanceState, binding.getRoot());
		FirebaseApp.initializeApp(getContext());
		initializeLogic();
		return binding.getRoot();
	}
	
	private void initialize(Bundle _savedInstanceState, View _view) {
		sp2 = getContext().getSharedPreferences("sp2", Activity.MODE_PRIVATE);
		sp1 = getContext().getSharedPreferences("sp1", Activity.MODE_PRIVATE);
		sp3 = getContext().getSharedPreferences("sp3", Activity.MODE_PRIVATE);
	}
	
	private void initializeLogic() {
		final android.view.View bottomPlayerCard = binding.getRoot().findViewById(R.id.bottom_player_card); 
		final android.widget.SeekBar seekBar = binding.getRoot().findViewById(R.id.seekBar); 
		final android.widget.TextView tvTime = binding.getRoot().findViewById(R.id.tvTime); 
		final android.widget.ImageView btnClosePlayer = binding.getRoot().findViewById(R.id.btnClosePlayer); 
		final android.widget.ImageView btnPlayPause = binding.getRoot().findViewById(R.id.btnPlayPause); 
		final android.widget.TextView tvMiniTitle = binding.getRoot().findViewById(R.id.tvMiniTitle); 
		com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).setLibraryListener(new com.CodeBySonu.VoxSherpa.system.VoxMediaController.MediaCommandListener() { 
			@Override 
			public void onPlay() { 
				if (getActivity() != null) getActivity().runOnUiThread(() -> btnPlayPause.performClick()); 
			} 
			@Override 
			public void onPause() { 
				if (getActivity() != null) getActivity().runOnUiThread(() -> btnPlayPause.performClick()); 
			} 
			@Override 
			public void onStop() { 
				if (getActivity() != null) getActivity().runOnUiThread(() -> btnClosePlayer.performClick()); 
			} 
			@Override 
			public void onNext() { 
				if (getActivity() != null) getActivity().runOnUiThread(() -> { 
					if (currentlyPlayingPos != -1 && currentlyPlayingPos < displayList.size() - 1) { 
						_playLibraryItem((double) (currentlyPlayingPos + 1)); 
					} 
				}); 
			} 
			@Override 
			public void onPrevious() { 
				if (getActivity() != null) getActivity().runOnUiThread(() -> { 
					if (currentlyPlayingPos > 0) { 
						_playLibraryItem((double) (currentlyPlayingPos - 1)); 
					} 
				}); 
			} 
		}); 
		seekHandler = new android.os.Handler(android.os.Looper.getMainLooper()); 
		seekRunnable = new Runnable() { 
			@Override 
			public void run() { 
				if (mediaPlayer != null && mediaPlayer.isPlaying()) { 
					int current = mediaPlayer.getCurrentPosition(); 
					int total = mediaPlayer.getDuration(); 
					seekBar.setProgress(current); 
					int curSec = current / 1000; 
					int totSec = total / 1000; 
					tvTime.setText(String.format(java.util.Locale.US, "%d:%02d / %d:%02d", 
					curSec / 60, curSec % 60, totSec / 60, totSec % 60)); 
					seekHandler.postDelayed(this, 100); 
				} 
			} 
		}; 
		audioManager = (android.media.AudioManager) getContext().getSystemService(android.content.Context.AUDIO_SERVICE); 
		focusListener = focusChange -> { 
			if (focusChange == android.media.AudioManager.AUDIOFOCUS_LOSS || focusChange == android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) { 
				if (mediaPlayer != null && mediaPlayer.isPlaying()) { 
					mediaPlayer.pause(); 
					btnPlayPause.setImageResource(R.drawable.icon_play_circle); 
					seekHandler.removeCallbacks(seekRunnable); 
					if (adapter != null && currentlyPlayingPos != -1) adapter.notifyItemChanged(currentlyPlayingPos); 
					_updateMediaNotification(2.0); 
				} 
			} 
		}; 
		binding.recyclerviewHistory.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext())); 
		bottomPlayerCard.setVisibility(android.view.View.GONE); 
		androidx.recyclerview.widget.RecyclerView.ItemAnimator animator = binding.recyclerviewHistory.getItemAnimator(); 
		if (animator instanceof androidx.recyclerview.widget.SimpleItemAnimator) { 
			((androidx.recyclerview.widget.SimpleItemAnimator) animator).setSupportsChangeAnimations(false); 
		} 
		btnClosePlayer.setOnClickListener(v -> { 
			bottomPlayerCard.setVisibility(android.view.View.GONE); 
			seekHandler.removeCallbacks(seekRunnable); 
			if (mediaPlayer != null) { 
				if (mediaPlayer.isPlaying()) mediaPlayer.stop(); 
				mediaPlayer.release(); 
				mediaPlayer = null; 
			} 
			int oldPos = currentlyPlayingPos; 
			currentlyPlayingPos = -1; 
			if (adapter != null && oldPos != -1) adapter.notifyItemChanged(oldPos); 
			com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).hideNotification(); 
		}); 
		btnPlayPause.setOnClickListener(v -> { 
			if (mediaPlayer != null) { 
				if (mediaPlayer.isPlaying()) { 
					mediaPlayer.pause(); 
					btnPlayPause.setImageResource(R.drawable.icon_play_circle); 
					seekHandler.removeCallbacks(seekRunnable); 
					_updateMediaNotification(2.0); 
				} else { 
					if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) { 
						android.media.AudioFocusRequest focusRequest = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN) 
						.setOnAudioFocusChangeListener(focusListener).build(); 
						audioManager.requestAudioFocus(focusRequest); 
					} else { 
						audioManager.requestAudioFocus(focusListener, android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.AUDIOFOCUS_GAIN); 
					} 
					mediaPlayer.start(); 
					btnPlayPause.setImageResource(R.drawable.icon_pause_circle); 
					seekHandler.post(seekRunnable); 
					_updateMediaNotification(1.0); 
				} 
				if (adapter != null && currentlyPlayingPos != -1) adapter.notifyItemChanged(currentlyPlayingPos); 
			} 
		}); 
		seekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() { 
			@Override 
			public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser) { 
				if (fromUser && mediaPlayer != null) { 
					mediaPlayer.seekTo(progress); 
				} 
			} 
			@Override 
			public void onStartTrackingTouch(android.widget.SeekBar bar) { } 
			@Override 
			public void onStopTrackingTouch(android.widget.SeekBar bar) { } 
		}); 
		android.view.View.OnClickListener tabListener = new android.view.View.OnClickListener() { 
			@Override 
			public void onClick(android.view.View v) { 
				if (v.getId() == binding.textview6.getId() || v.getId() == binding.cardview4.getId()) { 
					isFavTab = false; 
					binding.cardview4.setCardBackgroundColor(android.graphics.Color.parseColor("#2D3748")); 
					binding.textview6.setTextColor(android.graphics.Color.parseColor("#FFFFFF")); 
					binding.textview7.setBackgroundColor(android.graphics.Color.TRANSPARENT); 
					binding.textview7.setTextColor(android.graphics.Color.parseColor("#718096")); 
				} else if (v.getId() == binding.textview7.getId()) { 
					isFavTab = true; 
					binding.cardview4.setCardBackgroundColor(android.graphics.Color.TRANSPARENT); 
					binding.textview6.setTextColor(android.graphics.Color.parseColor("#718096")); 
					binding.textview7.setBackgroundColor(android.graphics.Color.parseColor("#2D3748")); 
					binding.textview7.setTextColor(android.graphics.Color.parseColor("#FFFFFF")); 
				} 
				displayList.clear(); 
				if (isFavTab) { 
					for (java.util.HashMap<String, Object> item : allList) { 
						boolean favCheck = item.containsKey("is_favorite") && item.get("is_favorite") != null && String.valueOf(item.get("is_favorite")).equals("true"); 
						if (favCheck) { 
							displayList.add(item); 
						} 
					} 
				} else { 
					displayList.addAll(allList); 
				} 
				if (displayList.isEmpty()) { 
					binding.recyclerviewHistory.setVisibility(android.view.View.GONE); 
					binding.linear4.setVisibility(android.view.View.VISIBLE); 
					binding.historyStatusTv.setText(isFavTab ? "No Favorites Yet" : "No Saved Audios"); 
				} else { 
					binding.recyclerviewHistory.setVisibility(android.view.View.VISIBLE); 
					binding.linear4.setVisibility(android.view.View.GONE); 
				} 
				if (adapter != null) adapter.notifyDataSetChanged(); 
			} 
		}; 
		binding.textview6.setOnClickListener(tabListener); 
		binding.cardview4.setOnClickListener(tabListener); 
		binding.textview7.setOnClickListener(tabListener); 
		String libraryData = sp2.getString("library_list", "[]"); 
		allList = new com.google.gson.Gson().fromJson(libraryData, new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType()); 
		if (allList == null) allList = new java.util.ArrayList<>(); 
		tabListener.onClick(binding.cardview4); 
		adapter = new androidx.recyclerview.widget.RecyclerView.Adapter() { 
			@androidx.annotation.NonNull 
			@Override 
			public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(@androidx.annotation.NonNull android.view.ViewGroup parent, int viewType) { 
				return new androidx.recyclerview.widget.RecyclerView.ViewHolder(getActivity().getLayoutInflater().inflate(R.layout.item_history, parent, false)) {}; 
			} 
			@Override 
			public void onBindViewHolder(@androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) { 
				android.view.View v = holder.itemView; 
				java.util.HashMap<String, Object> item = displayList.get(holder.getAdapterPosition()); 
				android.widget.TextView txtTitle = v.findViewById(R.id.txt_title); 
				txtTitle.setText(item.containsKey("text") ? item.get("text").toString() : "Unknown"); 
				((android.widget.TextView) v.findViewById(R.id.txt_voice_name)).setText(item.containsKey("voice_name") ? item.get("voice_name").toString() : "Unknown Voice"); 
				String dur = item.containsKey("duration") ? item.get("duration").toString() : "0:00"; 
				String dateStr = "Unknown Date"; 
				if (item.containsKey("timestamp")) { 
					try { 
						long millis = Long.parseLong(item.get("timestamp").toString()); 
						java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US); 
						dateStr = sdf.format(new java.util.Date(millis)); 
					} catch (Exception e){} 
				} 
				((android.widget.TextView) v.findViewById(R.id.txt_meta)).setText(dur + " • " + dateStr); 
				android.widget.ImageView favBtn = v.findViewById(R.id.btn_favorite); 
				boolean isFav = item.containsKey("is_favorite") && item.get("is_favorite") != null && String.valueOf(item.get("is_favorite")).equals("true"); 
				if (isFav) { 
					favBtn.setImageResource(R.drawable.icon_favorite); 
					favBtn.setColorFilter(android.graphics.Color.parseColor("#FF4B4B")); 
				} else { 
					favBtn.setImageResource(R.drawable.icon_favorite_outline); 
					favBtn.setColorFilter(android.graphics.Color.parseColor("#A0AEC0")); 
				} 
				android.widget.ImageView playIcon = v.findViewById(R.id.img_play_pause); 
				if (currentlyPlayingPos == holder.getAdapterPosition() && mediaPlayer != null && mediaPlayer.isPlaying()) { 
					playIcon.setImageResource(R.drawable.icon_pause_circle); 
				} else { 
					playIcon.setImageResource(R.drawable.icon_play_circle); 
				} 
				favBtn.setOnClickListener(view -> { 
					int currentPos = holder.getAdapterPosition(); 
					if (currentPos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return; 
					java.util.HashMap<String, Object> currentItem = displayList.get(currentPos); 
					boolean currentFavState = currentItem.containsKey("is_favorite") && currentItem.get("is_favorite") != null && String.valueOf(currentItem.get("is_favorite")).equals("true"); 
					boolean newFavState = !currentFavState; 
					currentItem.put("is_favorite", String.valueOf(newFavState)); 
					for(int i=0; i<allList.size(); i++){ 
						if(allList.get(i).get("timestamp").equals(currentItem.get("timestamp"))){ 
							allList.get(i).put("is_favorite", String.valueOf(newFavState)); 
							break; 
						} 
					} 
					sp2.edit().putString("library_list", new com.google.gson.Gson().toJson(allList)).apply(); 
					if (isFavTab && !newFavState) { 
						displayList.remove(currentPos); 
						notifyItemRemoved(currentPos); 
						if(displayList.isEmpty()){ 
							binding.recyclerviewHistory.setVisibility(android.view.View.GONE); 
							binding.linear4.setVisibility(android.view.View.VISIBLE); 
							binding.historyStatusTv.setText("No favorites yet"); 
						} 
					} else { 
						notifyItemChanged(currentPos); 
					} 
				}); 
				v.findViewById(R.id.btn_play_item).setOnClickListener(view -> { 
					int currentPos = holder.getAdapterPosition(); 
					if (currentPos != androidx.recyclerview.widget.RecyclerView.NO_POSITION) { 
						_playLibraryItem((double) currentPos); 
					} 
				}); 
				v.findViewById(R.id.btn_share).setOnClickListener(view -> { 
					int currentPos = holder.getAdapterPosition(); 
					if (currentPos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return; 
					java.util.HashMap<String, Object> currentItem = displayList.get(currentPos); 
					String path = currentItem.containsKey("path") ? currentItem.get("path").toString() : ""; 
					if(!path.isEmpty()){ 
						try { 
							java.io.File file = new java.io.File(path); 
							android.os.StrictMode.VmPolicy.Builder builder = new android.os.StrictMode.VmPolicy.Builder(); 
							android.os.StrictMode.setVmPolicy(builder.build()); 
							android.content.Intent intentShare = new android.content.Intent(android.content.Intent.ACTION_SEND); 
							intentShare.setType("audio/wav"); 
							intentShare.putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.fromFile(file)); 
							startActivity(android.content.Intent.createChooser(intentShare, "Share Audio")); 
						} catch(Exception e){ 
							com.google.android.material.snackbar.Snackbar.make(v, "Error sharing file", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show(); 
						} 
					} 
				}); 
				v.setOnLongClickListener(view -> { 
					int currentPos = holder.getAdapterPosition(); 
					if (currentPos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return true; 
					com.google.android.material.bottomsheet.BottomSheetDialog bottomDialog = new com.google.android.material.bottomsheet.BottomSheetDialog(getContext()); 
					android.widget.LinearLayout sheetLayout = new android.widget.LinearLayout(getContext()); 
					sheetLayout.setOrientation(android.widget.LinearLayout.VERTICAL); 
					int pad = (int)(24 * getResources().getDisplayMetrics().density); 
					sheetLayout.setPadding(pad, pad, pad, pad); 
					sheetLayout.setBackgroundColor(android.graphics.Color.parseColor("#131B2D")); 
					android.widget.TextView titleTv = new android.widget.TextView(getContext()); 
					titleTv.setText("Delete Recording?"); 
					titleTv.setTextSize(20f); 
					titleTv.setTypeface(null, android.graphics.Typeface.BOLD); 
					titleTv.setTextColor(android.graphics.Color.WHITE); 
					sheetLayout.addView(titleTv); 
					android.widget.TextView msgTv = new android.widget.TextView(getContext()); 
					msgTv.setText("Are you sure you want to delete this audio file forever? This cannot be undone."); 
					msgTv.setTextSize(14f); 
					msgTv.setTextColor(android.graphics.Color.parseColor("#94A3B8")); 
					android.widget.LinearLayout.LayoutParams msgParams = new android.widget.LinearLayout.LayoutParams( 
					android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT); 
					msgParams.setMargins(0, (int)(8 * getResources().getDisplayMetrics().density), 0, (int)(24 * getResources().getDisplayMetrics().density)); 
					msgTv.setLayoutParams(msgParams); 
					sheetLayout.addView(msgTv); 
					android.widget.Button deleteBtn = new android.widget.Button(getContext()); 
					deleteBtn.setText("Delete Forever"); 
					deleteBtn.setTextColor(android.graphics.Color.WHITE); 
					deleteBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF4B4B"))); 
					deleteBtn.setAllCaps(false); 
					sheetLayout.addView(deleteBtn); 
					deleteBtn.setOnClickListener(delView -> { 
						java.util.HashMap<String, Object> deleteItem = displayList.get(currentPos); 
						String path = deleteItem.containsKey("path") ? deleteItem.get("path").toString() : ""; 
						if(!path.isEmpty()){ 
							new java.io.File(path).delete(); 
						} 
						if(currentlyPlayingPos == currentPos){ 
							if(mediaPlayer != null) { 
								mediaPlayer.stop(); 
								mediaPlayer.release(); 
								mediaPlayer = null; 
							} 
							bottomPlayerCard.setVisibility(android.view.View.GONE); 
							seekHandler.removeCallbacks(seekRunnable); 
							currentlyPlayingPos = -1; 
							com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).hideNotification(); 
						} 
						String ts = deleteItem.get("timestamp").toString(); 
						displayList.remove(currentPos); 
						for(int i=0; i<allList.size(); i++){ 
							if(allList.get(i).get("timestamp").equals(ts)){ 
								allList.remove(i); 
								break; 
							} 
						} 
						sp2.edit().putString("library_list", new com.google.gson.Gson().toJson(allList)).apply(); 
						notifyItemRemoved(currentPos); 
						if(displayList.isEmpty()){ 
							binding.recyclerviewHistory.setVisibility(android.view.View.GONE); 
							binding.linear4.setVisibility(android.view.View.VISIBLE); 
							binding.historyStatusTv.setText(isFavTab ? "No favorites yet" : "No history found"); 
						} 
						bottomDialog.dismiss(); 
					}); 
					bottomDialog.setContentView(sheetLayout); 
					android.view.View parentView = (android.view.View) sheetLayout.getParent(); 
					if (parentView != null) parentView.setBackgroundColor(android.graphics.Color.TRANSPARENT); 
					bottomDialog.show(); 
					return true; 
				}); 
			} 
			@Override 
			public int getItemCount() { return displayList.size(); } 
		}; 
		binding.recyclerviewHistory.setAdapter(adapter); 
		
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		if (mediaPlayer != null) {
			mediaPlayer.release();
			mediaPlayer = null;
		}
		
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		String libraryData = sp2.getString("library_list", "[]");
		
		allList = new com.google.gson.Gson().fromJson(libraryData, new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType());
		if (allList == null) allList = new java.util.ArrayList<>();
		
		displayList.clear();
		
		if (isFavTab) {
			for (java.util.HashMap<String, Object> item : allList) {
				boolean favCheck = item.containsKey("is_favorite") && item.get("is_favorite") != null && String.valueOf(item.get("is_favorite")).equals("true");
				if (favCheck) {
					displayList.add(item);
				}
			}
		} else {
			displayList.addAll(allList);
		}
		
		if (displayList.isEmpty()) {
			binding.recyclerviewHistory.setVisibility(android.view.View.GONE);
			binding.linear4.setVisibility(android.view.View.VISIBLE);
			binding.historyStatusTv.setText(isFavTab ? "No favorites yet" : "No history found");
		} else {
			binding.recyclerviewHistory.setVisibility(android.view.View.VISIBLE);
			binding.linear4.setVisibility(android.view.View.GONE);
		}
		
		if (adapter != null) {
			adapter.notifyDataSetChanged();
		}
	}
	
	public void _updateMediaNotification(final double _state) {
		int finalState = (int) _state; 
		if (currentlyPlayingPos != -1 && currentlyPlayingPos < displayList.size()) { 
			java.util.HashMap<String, Object> item = displayList.get(currentlyPlayingPos); 
			String title = item.containsKey("text") ? item.get("text").toString() : "Unknown Audio"; 
			String subtitle = item.containsKey("voice_name") ? item.get("voice_name").toString() : "Library"; 
			long trackDuration = -1L; 
			if (mediaPlayer != null) { 
				try { 
					trackDuration = mediaPlayer.getDuration(); 
				} catch (Exception ignored) {} 
			} 
			com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).updatePlaybackState(title, subtitle, finalState, true); 
			android.content.Intent durIntent = new android.content.Intent(getContext(), com.CodeBySonu.VoxSherpa.system.VoxMediaService.class); 
			durIntent.setAction("ACTION_UPDATE_STATE"); 
			durIntent.putExtra("title", title); 
			durIntent.putExtra("subtitle", subtitle); 
			durIntent.putExtra("state", finalState); 
			durIntent.putExtra("isLibraryMode", true); 
			if (trackDuration > 0) { 
				durIntent.putExtra("duration", trackDuration); 
			} 
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) { 
				getContext().startForegroundService(durIntent); 
			} else { 
				getContext().startService(durIntent); 
			} 
		} else { 
			com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).hideNotification(); 
		} 
		
	}
	
	
	public void _playLibraryItem(final double _currentPos) {
		int currentPos = (int) _currentPos; 
		if (currentPos < 0 || currentPos >= displayList.size()) return; 
		java.util.HashMap<String, Object> currentItem = displayList.get(currentPos); 
		String path = currentItem.containsKey("path") ? currentItem.get("path").toString() : ""; 
		if (path.isEmpty()) { 
			com.google.android.material.snackbar.Snackbar.make(binding.getRoot(), "File not found", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show(); 
			return; 
		} 
		final android.view.View bottomPlayerCard = binding.getRoot().findViewById(R.id.bottom_player_card); 
		final android.widget.ImageView btnPlayPause = binding.getRoot().findViewById(R.id.btnPlayPause); 
		final android.widget.TextView tvMiniTitle = binding.getRoot().findViewById(R.id.tvMiniTitle); 
		final android.widget.SeekBar seekBar = binding.getRoot().findViewById(R.id.seekBar); 
		bottomPlayerCard.setVisibility(android.view.View.VISIBLE); 
		String titleText = currentItem.containsKey("text") ? currentItem.get("text").toString() : "Unknown"; 
		tvMiniTitle.setText(titleText); 
		try { 
			if (currentlyPlayingPos == currentPos) { 
				if (mediaPlayer != null && mediaPlayer.isPlaying()) { 
					mediaPlayer.pause(); 
					btnPlayPause.setImageResource(R.drawable.icon_play_circle); 
					seekHandler.removeCallbacks(seekRunnable); 
					if (adapter != null) adapter.notifyItemChanged(currentPos); 
					_updateMediaNotification(2.0); 
				} else if (mediaPlayer != null) { 
					if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) { 
						android.media.AudioFocusRequest focusRequest = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN) 
						.setOnAudioFocusChangeListener(focusListener).build(); 
						audioManager.requestAudioFocus(focusRequest); 
					} else { 
						audioManager.requestAudioFocus(focusListener, android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.AUDIOFOCUS_GAIN); 
					} 
					mediaPlayer.start(); 
					btnPlayPause.setImageResource(R.drawable.icon_pause_circle); 
					seekHandler.post(seekRunnable); 
					if (adapter != null) adapter.notifyItemChanged(currentPos); 
					_updateMediaNotification(1.0); 
				} 
			} else { 
				if (mediaPlayer != null) { 
					mediaPlayer.release(); 
				} 
				mediaPlayer = new android.media.MediaPlayer(); 
				mediaPlayer.setDataSource(path); 
				mediaPlayer.prepare(); 
				if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) { 
					android.media.AudioFocusRequest focusRequest = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN) 
					.setOnAudioFocusChangeListener(focusListener).build(); 
					audioManager.requestAudioFocus(focusRequest); 
				} else { 
					audioManager.requestAudioFocus(focusListener, android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.AUDIOFOCUS_GAIN); 
				} 
				mediaPlayer.start(); 
				btnPlayPause.setImageResource(R.drawable.icon_pause_circle); 
				seekBar.setMax(mediaPlayer.getDuration()); 
				seekHandler.post(seekRunnable); 
				int oldPos = currentlyPlayingPos; 
				currentlyPlayingPos = currentPos; 
				if (adapter != null) { 
					if (oldPos != -1) adapter.notifyItemChanged(oldPos); 
					adapter.notifyItemChanged(currentlyPlayingPos); 
				} 
				_updateMediaNotification(1.0); 
				mediaPlayer.setOnCompletionListener(mp -> { 
					int compPos = currentlyPlayingPos; 
					currentlyPlayingPos = -1; 
					btnPlayPause.setImageResource(R.drawable.icon_play_circle); 
					seekBar.setProgress(0); 
					seekHandler.removeCallbacks(seekRunnable); 
					if (adapter != null && compPos != -1) { 
						adapter.notifyItemChanged(compPos); 
					} 
					VoxMediaController.getInstance(getContext()).hideNotification(); 
				}); 
			} 
		} catch (Exception e) { 
			com.google.android.material.snackbar.Snackbar.make(binding.getRoot(), "Error playing file", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show(); 
		} 
		
	}
	
}