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



public class LibraryFragmentActivity extends Fragment {
	
	private LibraryFragmentBinding binding;
	ArrayList<HashMap<String, Object>> allList;
	boolean isFavTab = false;
	MediaPlayer mediaPlayer = null;
	int currentlyPlayingPos = -1;
	RecyclerView.Adapter adapter;
	java.util.ArrayList<java.util.HashMap<String, Object>> displayList = new java.util.ArrayList<>();
	
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
		final View bottomPlayerCard = binding.getRoot().findViewById(R.id.bottom_player_card);
		final SeekBar seekBar = binding.getRoot().findViewById(R.id.seekBar);
		final TextView tvTime = binding.getRoot().findViewById(R.id.tvTime);
		final ImageView btnClosePlayer = binding.getRoot().findViewById(R.id.btnClosePlayer);
		final ImageView btnPlayPause = binding.getRoot().findViewById(R.id.btnPlayPause);
		final TextView tvMiniTitle = binding.getRoot().findViewById(R.id.tvMiniTitle);
		
		final Handler seekHandler = new Handler(Looper.getMainLooper());
		final Runnable seekRunnable = new Runnable() {
			@Override
			public void run() {
				if (mediaPlayer != null && mediaPlayer.isPlaying()) {
					int current = mediaPlayer.getCurrentPosition();
					int total = mediaPlayer.getDuration();
					seekBar.setProgress(current);
					
					int curSec = current / 1000;
					int totSec = total / 1000;
					tvTime.setText(String.format(Locale.US, "%d:%02d / %d:%02d", 
					curSec / 60, curSec % 60, totSec / 60, totSec % 60));
					
					seekHandler.postDelayed(this, 100); 
				}
			}
		};
		
		final AudioManager audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
		final AudioManager.OnAudioFocusChangeListener focusListener = focusChange -> {
			if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
				if (mediaPlayer != null && mediaPlayer.isPlaying()) {
					mediaPlayer.pause();
					btnPlayPause.setImageResource(R.drawable.icon_play_circle);
					seekHandler.removeCallbacks(seekRunnable); // Ab ye error nahi dega!
					if (adapter != null && currentlyPlayingPos != -1) adapter.notifyItemChanged(currentlyPlayingPos);
				}
			}
		};
		
		// 1. Setup Layout & Hide Bottom Player Initially
		binding.recyclerviewHistory.setLayoutManager(new LinearLayoutManager(getContext()));
		bottomPlayerCard.setVisibility(View.GONE);
		RecyclerView.ItemAnimator animator = binding.recyclerviewHistory.getItemAnimator();
		if (animator instanceof SimpleItemAnimator) {
			((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
		}
		
		// BOTTOM PLAYER CONTROLS
		btnClosePlayer.setOnClickListener(v -> {
			bottomPlayerCard.setVisibility(View.GONE);
			seekHandler.removeCallbacks(seekRunnable);
			if (mediaPlayer != null) {
				if (mediaPlayer.isPlaying()) mediaPlayer.stop();
				mediaPlayer.release();
				mediaPlayer = null;
			}
			int oldPos = currentlyPlayingPos;
			currentlyPlayingPos = -1;
			if (adapter != null && oldPos != -1) adapter.notifyItemChanged(oldPos);
		});
		
		btnPlayPause.setOnClickListener(v -> {
			if (mediaPlayer != null) {
				if (mediaPlayer.isPlaying()) {
					mediaPlayer.pause();
					btnPlayPause.setImageResource(R.drawable.icon_play_circle);
					seekHandler.removeCallbacks(seekRunnable);
				} else {
					// Audio Focus Request before play
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
						AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
						.setOnAudioFocusChangeListener(focusListener).build();
						audioManager.requestAudioFocus(focusRequest);
					} else {
						audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
					}
					mediaPlayer.start();
					btnPlayPause.setImageResource(R.drawable.icon_pause_circle);
					seekHandler.post(seekRunnable);
				}
				if (adapter != null && currentlyPlayingPos != -1) adapter.notifyItemChanged(currentlyPlayingPos);
			}
		});
		
		seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
				if (fromUser && mediaPlayer != null) {
					mediaPlayer.seekTo(progress);
				}
			}
			@Override
			public void onStartTrackingTouch(SeekBar bar) { }
			@Override
			public void onStopTrackingTouch(SeekBar bar) { }
		});
		
		// 2. TABS SWITCHING LOGIC
		View.OnClickListener tabListener = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (v.getId() == binding.textview6.getId() || v.getId() == binding.cardview4.getId()) {
					isFavTab = false;
					binding.cardview4.setCardBackgroundColor(Color.parseColor("#2D3748"));
					binding.textview6.setTextColor(Color.parseColor("#FFFFFF"));
					binding.textview7.setBackgroundColor(Color.TRANSPARENT);
					binding.textview7.setTextColor(Color.parseColor("#718096"));
				} else if (v.getId() == binding.textview7.getId()) {
					isFavTab = true;
					binding.cardview4.setCardBackgroundColor(Color.TRANSPARENT);
					binding.textview6.setTextColor(Color.parseColor("#718096"));
					binding.textview7.setBackgroundColor(Color.parseColor("#2D3748"));
					binding.textview7.setTextColor(Color.parseColor("#FFFFFF"));
				}
				
				displayList.clear();
				if (isFavTab) {
					for (HashMap<String, Object> item : allList) {
						boolean favCheck = item.containsKey("is_favorite") && item.get("is_favorite") != null && String.valueOf(item.get("is_favorite")).equals("true");
						if (favCheck) {
							displayList.add(item);
						}
					}
				} else {
					displayList.addAll(allList);
				}
				
				if (displayList.isEmpty()) {
					binding.recyclerviewHistory.setVisibility(View.GONE);
					binding.linear4.setVisibility(View.VISIBLE);
					binding.historyStatusTv.setText(isFavTab ? "No favorites yet" : "No history found");
				} else {
					binding.recyclerviewHistory.setVisibility(View.VISIBLE);
					binding.linear4.setVisibility(View.GONE);
				}
				
				if (adapter != null) adapter.notifyDataSetChanged();
			}
		};
		
		binding.textview6.setOnClickListener(tabListener);
		binding.cardview4.setOnClickListener(tabListener);
		binding.textview7.setOnClickListener(tabListener);
		
		// 3. INITIAL DATA LOAD
		String libraryData = sp2.getString("library_list", "[]");
		allList = new Gson().fromJson(libraryData, new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());
		if (allList == null) allList = new ArrayList<>();
		
		tabListener.onClick(binding.cardview4); 
		
		// 4. RECYCLERVIEW ADAPTER
		adapter = new RecyclerView.Adapter() {
			@androidx.annotation.NonNull
			@Override
			public RecyclerView.ViewHolder onCreateViewHolder(@androidx.annotation.NonNull ViewGroup parent, int viewType) {
				return new RecyclerView.ViewHolder(getActivity().getLayoutInflater().inflate(R.layout.item_history, parent, false)) {};
			}
			
			@Override
			public void onBindViewHolder(@androidx.annotation.NonNull RecyclerView.ViewHolder holder, int position) {
				View v = holder.itemView;
				HashMap<String, Object> item = displayList.get(holder.getAdapterPosition());
				
				TextView txtTitle = v.findViewById(R.id.txt_title);
				txtTitle.setText(item.containsKey("text") ? item.get("text").toString() : "Unknown");
				((TextView) v.findViewById(R.id.txt_voice_name)).setText(item.containsKey("voice_name") ? item.get("voice_name").toString() : "Unknown Voice");
				
				String dur = item.containsKey("duration") ? item.get("duration").toString() : "0:00";
				String dateStr = "Unknown Date";
				if (item.containsKey("timestamp")) {
					try {
						long millis = Long.parseLong(item.get("timestamp").toString());
						SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
						dateStr = sdf.format(new Date(millis));
					} catch (Exception e){}
				}
				((TextView) v.findViewById(R.id.txt_meta)).setText(dur + " • " + dateStr);
				
				ImageView favBtn = v.findViewById(R.id.btn_favorite);
				boolean isFav = item.containsKey("is_favorite") && item.get("is_favorite") != null && String.valueOf(item.get("is_favorite")).equals("true");
				
				if (isFav) {
					favBtn.setImageResource(R.drawable.icon_favorite); 
					favBtn.setColorFilter(Color.parseColor("#FF4B4B")); 
				} else {
					favBtn.setImageResource(R.drawable.icon_favorite_outline);
					favBtn.setColorFilter(Color.parseColor("#A0AEC0")); 
				}
				
				ImageView playIcon = v.findViewById(R.id.img_play_pause);
				if (currentlyPlayingPos == holder.getAdapterPosition() && mediaPlayer != null && mediaPlayer.isPlaying()) {
					playIcon.setImageResource(R.drawable.icon_pause_circle);
				} else {
					playIcon.setImageResource(R.drawable.icon_play_circle);
				}
				
				favBtn.setOnClickListener(view -> {
					int currentPos = holder.getAdapterPosition();
					if (currentPos == RecyclerView.NO_POSITION) return;
					
					HashMap<String, Object> currentItem = displayList.get(currentPos);
					boolean currentFavState = currentItem.containsKey("is_favorite") && currentItem.get("is_favorite") != null && String.valueOf(currentItem.get("is_favorite")).equals("true");
					boolean newFavState = !currentFavState;
					currentItem.put("is_favorite", String.valueOf(newFavState));
					
					for(int i=0; i<allList.size(); i++){
						if(allList.get(i).get("timestamp").equals(currentItem.get("timestamp"))){
							allList.get(i).put("is_favorite", String.valueOf(newFavState));
							break;
						}
					}
					
					sp2.edit().putString("library_list", new Gson().toJson(allList)).apply();
					
					if (isFavTab && !newFavState) {
						displayList.remove(currentPos);
						notifyItemRemoved(currentPos);
						if(displayList.isEmpty()){
							binding.recyclerviewHistory.setVisibility(View.GONE);
							binding.linear4.setVisibility(View.VISIBLE);
							binding.historyStatusTv.setText("No favorites yet");
						}
					} else {
						notifyItemChanged(currentPos);
					}
				});
				
				//  PLAY AUDIO CLICK
				v.findViewById(R.id.btn_play_item).setOnClickListener(view -> {
					int currentPos = holder.getAdapterPosition();
					if (currentPos == RecyclerView.NO_POSITION) return;
					
					HashMap<String, Object> currentItem = displayList.get(currentPos);
					String path = currentItem.containsKey("path") ? currentItem.get("path").toString() : "";
					
					if(path.isEmpty()){
						Snackbar.make(v, "File not found", Snackbar.LENGTH_SHORT).show();
						return;
					}
					
					bottomPlayerCard.setVisibility(View.VISIBLE);
					tvMiniTitle.setText(txtTitle.getText().toString());
					
					try {
						if (currentlyPlayingPos == currentPos) {
							if (mediaPlayer != null && mediaPlayer.isPlaying()) {
								mediaPlayer.pause();
								btnPlayPause.setImageResource(R.drawable.icon_play_circle);
								seekHandler.removeCallbacks(seekRunnable);
								notifyItemChanged(currentPos);
							} else if (mediaPlayer != null) {
								// Audio Focus Request before resuming
								if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
									AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
									.setOnAudioFocusChangeListener(focusListener).build();
									audioManager.requestAudioFocus(focusRequest);
								} else {
									audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
								}
								mediaPlayer.start();
								btnPlayPause.setImageResource(R.drawable.icon_pause_circle);
								seekHandler.post(seekRunnable);
								notifyItemChanged(currentPos);
							}
						} else {
							if (mediaPlayer != null) {
								mediaPlayer.release();
							}
							mediaPlayer = new MediaPlayer();
							mediaPlayer.setDataSource(path);
							mediaPlayer.prepare();
							
							// Audio Focus Request before playing new file
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
								AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
								.setOnAudioFocusChangeListener(focusListener).build();
								audioManager.requestAudioFocus(focusRequest);
							} else {
								audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
							}
							mediaPlayer.start();
							
							btnPlayPause.setImageResource(R.drawable.icon_pause_circle);
							seekBar.setMax(mediaPlayer.getDuration());
							seekHandler.post(seekRunnable);
							
							int oldPos = currentlyPlayingPos;
							currentlyPlayingPos = currentPos;
							
							if(oldPos != -1) notifyItemChanged(oldPos);
							notifyItemChanged(currentlyPlayingPos);
							
							mediaPlayer.setOnCompletionListener(mp -> {
								int compPos = currentlyPlayingPos; 
								currentlyPlayingPos = -1; 
								btnPlayPause.setImageResource(R.drawable.icon_play_circle);
								seekBar.setProgress(0);
								seekHandler.removeCallbacks(seekRunnable);
								if(compPos != -1) {
									notifyItemChanged(compPos); 
								}
							});
						}
					} catch (Exception e) {
						Snackbar.make(v, "Error playing file", Snackbar.LENGTH_SHORT).show();
					}
				});
				
				// Share Click
				v.findViewById(R.id.btn_share).setOnClickListener(view -> {
					int currentPos = holder.getAdapterPosition();
					if (currentPos == RecyclerView.NO_POSITION) return;
					
					HashMap<String, Object> currentItem = displayList.get(currentPos);
					String path = currentItem.containsKey("path") ? currentItem.get("path").toString() : "";
					if(!path.isEmpty()){
						try {
							File file = new File(path);
							StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
							StrictMode.setVmPolicy(builder.build());
							
							Intent intentShare = new Intent(Intent.ACTION_SEND);
							intentShare.setType("audio/wav");
							intentShare.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
							startActivity(Intent.createChooser(intentShare, "Share Audio"));
						} catch(Exception e){
							Snackbar.make(v, "Error sharing file", Snackbar.LENGTH_SHORT).show();
						}
					}
				});
				
				// Delete Long Click
				v.setOnLongClickListener(view -> {
					int currentPos = holder.getAdapterPosition();
					if (currentPos == RecyclerView.NO_POSITION) return true;
					
					BottomSheetDialog bottomDialog = new BottomSheetDialog(getContext());
					
					LinearLayout sheetLayout = new LinearLayout(getContext());
					sheetLayout.setOrientation(LinearLayout.VERTICAL);
					int pad = (int)(24 * getResources().getDisplayMetrics().density);
					sheetLayout.setPadding(pad, pad, pad, pad);
					sheetLayout.setBackgroundColor(Color.parseColor("#131B2D")); 
					
					TextView titleTv = new TextView(getContext());
					titleTv.setText("Delete Recording?");
					titleTv.setTextSize(20f);
					titleTv.setTypeface(null, Typeface.BOLD);
					titleTv.setTextColor(Color.WHITE);
					sheetLayout.addView(titleTv);
					
					TextView msgTv = new TextView(getContext());
					msgTv.setText("Are you sure you want to delete this audio file forever? This cannot be undone.");
					msgTv.setTextSize(14f);
					msgTv.setTextColor(Color.parseColor("#94A3B8"));
					LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
					msgParams.setMargins(0, (int)(8 * getResources().getDisplayMetrics().density), 0, (int)(24 * getResources().getDisplayMetrics().density));
					msgTv.setLayoutParams(msgParams);
					sheetLayout.addView(msgTv);
					
					Button deleteBtn = new Button(getContext());
					deleteBtn.setText("Delete Forever");
					deleteBtn.setTextColor(Color.WHITE);
					deleteBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF4B4B")));
					deleteBtn.setAllCaps(false);
					sheetLayout.addView(deleteBtn);
					
					deleteBtn.setOnClickListener(delView -> {
						HashMap<String, Object> deleteItem = displayList.get(currentPos);
						String path = deleteItem.containsKey("path") ? deleteItem.get("path").toString() : "";
						
						if(!path.isEmpty()){
							new File(path).delete(); 
						}
						
						if(currentlyPlayingPos == currentPos){
							if(mediaPlayer != null) {
								mediaPlayer.stop();
								mediaPlayer.release();
								mediaPlayer = null;
							}
							bottomPlayerCard.setVisibility(View.GONE);
							seekHandler.removeCallbacks(seekRunnable);
							currentlyPlayingPos = -1;
						}
						
						String ts = deleteItem.get("timestamp").toString();
						displayList.remove(currentPos);
						
						for(int i=0; i<allList.size(); i++){
							if(allList.get(i).get("timestamp").equals(ts)){
								allList.remove(i);
								break;
							}
						}
						
						sp2.edit().putString("library_list", new Gson().toJson(allList)).apply();
						
						notifyItemRemoved(currentPos);
						
						if(displayList.isEmpty()){
							binding.recyclerviewHistory.setVisibility(View.GONE);
							binding.linear4.setVisibility(View.VISIBLE);
							binding.historyStatusTv.setText(isFavTab ? "No favorites yet" : "No history found");
						}
						bottomDialog.dismiss();
					});
					
					bottomDialog.setContentView(sheetLayout);
					
					View parentView = (View) sheetLayout.getParent();
					if (parentView != null) parentView.setBackgroundColor(Color.TRANSPARENT);
					
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
	

}
