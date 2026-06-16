package com.CodeBySonu.VoxSherpa;

import android.Manifest;
import android.animation.*;
import android.app.*;
import android.app.Activity;
import android.content.*;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import com.CodeBySonu.VoxSherpa.databinding.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.ValueEventListener;
import com.k2fsa.sherpa.onnx.*;
import com.tom_roush.pdfbox.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.HashMap;
import java.util.regex.*;
import org.json.*;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import android.database.Cursor;


public class ModelsFragmentActivity extends Fragment {
	
	public final int REQ_CD_FILEPICKER = 101;
	
	private FirebaseDatabase _firebase = FirebaseDatabase.getInstance();
	
	private ModelsFragmentBinding binding;
	private static final String TAG = "ModelsFragment";
	private static final Gson GSON = new Gson();
	private List<HashMap<String, Object>> modelList = new ArrayList<>();
	private volatile boolean isLoading = false;
	private String tempOnnxPath = "";
	private String tempTokensPath = "";
	private BottomSheetDialog importDialog;
	private View dialogView;
	private int lastGeneratedSampleRate = 22050;
	
	private Intent FilePicker = new Intent(Intent.ACTION_GET_CONTENT);
	private SharedPreferences sp1;
	private SharedPreferences sp2;
	private SharedPreferences sp3;
	private DatabaseReference fb = _firebase.getReference("/");
	private ChildEventListener _fb_child_listener;
	
	@NonNull
	@Override
	public View onCreateView(@NonNull LayoutInflater _inflater, @Nullable ViewGroup _container, @Nullable Bundle _savedInstanceState) {
		binding = ModelsFragmentBinding.inflate(_inflater, _container, false);
		initialize(_savedInstanceState, binding.getRoot());
		FirebaseApp.initializeApp(getContext());
		initializeLogic();
		return binding.getRoot();
	}
	
	private void initialize(Bundle _savedInstanceState, View _view) {
		FilePicker.setType("*/*");
		FilePicker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
		sp1 = getContext().getSharedPreferences("sp1", Activity.MODE_PRIVATE);
		sp2 = getContext().getSharedPreferences("sp2", Activity.MODE_PRIVATE);
		sp3 = getContext().getSharedPreferences("sp3", Activity.MODE_PRIVATE);
		
		binding.btnFilter.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				binding.imageview8.animate().rotation(-90f).setDuration(300).start();
				com.google.android.material.bottomsheet.BottomSheetDialog sortDialog = new com.google.android.material.bottomsheet.BottomSheetDialog(getContext());
				View dialogView = getActivity().getLayoutInflater().inflate(R.layout.sort_bottom_dialog, null);
				android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
				gd.setColor(android.graphics.Color.parseColor("#131B2D"));
				float radius = 24f * getResources().getDisplayMetrics().density;
				gd.setCornerRadii(new float[]{
					radius, radius,
					radius, radius,
					0f, 0f,
					0f, 0f
				});
				dialogView.setBackground(gd);
				sortDialog.setContentView(dialogView);
				sortDialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
					@Override
					public void onShow(android.content.DialogInterface dialog) {
						com.google.android.material.bottomsheet.BottomSheetDialog d = (com.google.android.material.bottomsheet.BottomSheetDialog) dialog;
						View bottomSheetInternal = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
						if (bottomSheetInternal != null) {
							bottomSheetInternal.setBackgroundColor(android.graphics.Color.TRANSPARENT);
						}
					}
				});
				LinearLayout layoutAll = dialogView.findViewById(R.id.layout_all_models);
				LinearLayout layoutDownload = dialogView.findViewById(R.id.layout_download);
				LinearLayout layoutInstalled = dialogView.findViewById(R.id.layout_installed);
				LinearLayout layoutNewest = dialogView.findViewById(R.id.layout_newest);
				LinearLayout layoutOldest = dialogView.findViewById(R.id.layout_oldest);
				RadioButton rbAll = dialogView.findViewById(R.id.rb_all_models);
				RadioButton rbDownload = dialogView.findViewById(R.id.rb_download);
				RadioButton rbInstalled = dialogView.findViewById(R.id.rb_installed);
				RadioButton rbNewest = dialogView.findViewById(R.id.rb_newest);
				RadioButton rbOldest = dialogView.findViewById(R.id.rb_oldest);
				String currentSort = sp1.getString("sort_preference", "all_models");
				String currentLanguage = sp1.getString("language_filter", "All Languages");
				if(currentSort.equals("download")) rbDownload.setChecked(true);
				else if(currentSort.equals("installed")) rbInstalled.setChecked(true);
				else if(currentSort.equals("newest")) rbNewest.setChecked(true);
				else if(currentSort.equals("oldest")) rbOldest.setChecked(true);
				else rbAll.setChecked(true);
				Runnable applyFilterAndSort = () -> {
					String activeSort = sp1.getString("sort_preference", "all_models");
					String activeLang = sp1.getString("language_filter", "All Languages");
					String savedData = sp1.getString("models_data", "[]");
					java.util.ArrayList<java.util.HashMap<String, Object>> masterList = new com.google.gson.Gson().fromJson(
					savedData,
					new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType()
					);
					if (masterList == null) masterList = new java.util.ArrayList<>();
					modelList.clear();
					for (java.util.HashMap<String, Object> item : masterList) {
						String onnxPath = item.containsKey("onnx_path") && item.get("onnx_path") != null ? item.get("onnx_path").toString() : "";
						boolean isInstalled = !onnxPath.isEmpty();
						String itemLang = item.containsKey("language") && item.get("language") != null ? item.get("language").toString() : "";
						boolean languageMatch = activeLang.equals("All Languages") || activeLang.equals(itemLang);
						if (languageMatch) {
							if (activeSort.equals("download")) {
								if (!isInstalled) modelList.add(item);
							} else if (activeSort.equals("installed")) {
								if (isInstalled) modelList.add(item);
							} else {
								modelList.add(item);
							}
						}
					}
					if (activeSort.equals("newest")) {
						java.util.Collections.reverse(modelList);
					}
					if (binding.recyclerviewModels.getAdapter() != null) {
						binding.recyclerviewModels.getAdapter().notifyDataSetChanged();
					}
					_updateEmptyState();
					if (!activeLang.equals("All Languages")) {
						binding.sortTv.setText(activeLang);
					} else if (activeSort.equals("download")) {
						binding.sortTv.setText("Download");
					} else if (activeSort.equals("installed")) {
						binding.sortTv.setText("Installed");
					} else if (activeSort.equals("newest")) {
						binding.sortTv.setText("Newest First");
					} else if (activeSort.equals("oldest")) {
						binding.sortTv.setText("Oldest First");
					} else {
						binding.sortTv.setText("All Models");
					}
				};
				String[] languagesFromXml = getResources().getStringArray(R.array.language_list);
				String[] finalLanguages = new String[languagesFromXml.length + 1];
				finalLanguages[0] = "All Languages";
				System.arraycopy(languagesFromXml, 0, finalLanguages, 1, languagesFromXml.length);
				android.widget.ArrayAdapter<String> langAdapter = new android.widget.ArrayAdapter<>(getContext(), R.layout.custom_dropdown_item, R.id.tv_drop_item, finalLanguages);
				android.widget.AutoCompleteTextView dropdownLang = dialogView.findViewById(R.id.dropdown_lang);
				if(dropdownLang != null) {
					dropdownLang.setAdapter(langAdapter);
					dropdownLang.setText(currentLanguage, false);
					dropdownLang.setOnItemClickListener((parentView, view, position, id) -> {
						String selectedLang = finalLanguages[position];
						sp1.edit().putString("language_filter", selectedLang).apply();
						sortDialog.dismiss();
						applyFilterAndSort.run();
					});
				}
				View.OnClickListener clickListener = v -> {
					String selected = "all_models";
					if(v.getId() == R.id.layout_download) selected = "download";
					else if(v.getId() == R.id.layout_installed) selected = "installed";
					else if(v.getId() == R.id.layout_newest) selected = "newest";
					else if(v.getId() == R.id.layout_oldest) selected = "oldest";
					sp1.edit().putString("sort_preference", selected).apply();
					sortDialog.dismiss();
					applyFilterAndSort.run();
				};
				layoutAll.setOnClickListener(clickListener);
				layoutDownload.setOnClickListener(clickListener);
				layoutInstalled.setOnClickListener(clickListener);
				layoutNewest.setOnClickListener(clickListener);
				layoutOldest.setOnClickListener(clickListener);
				sortDialog.setOnDismissListener(dialogInterface -> {
					binding.imageview8.animate().rotation(0f).setDuration(300).start();
				});
				sortDialog.show();
				
			}
		});
		
		_fb_child_listener = new ChildEventListener() {
			@Override
			public void onChildAdded(DataSnapshot _param1, String _param2) {
				GenericTypeIndicator<HashMap<String, Object>> _ind = new GenericTypeIndicator<HashMap<String, Object>>() {};
				final String _childKey = _param1.getKey();
				final HashMap<String, Object> _childValue = _param1.getValue(_ind);
				
			}
			
			@Override
			public void onChildChanged(DataSnapshot _param1, String _param2) {
				GenericTypeIndicator<HashMap<String, Object>> _ind = new GenericTypeIndicator<HashMap<String, Object>>() {};
				final String _childKey = _param1.getKey();
				final HashMap<String, Object> _childValue = _param1.getValue(_ind);
				
			}
			
			@Override
			public void onChildMoved(DataSnapshot _param1, String _param2) {
				
			}
			
			@Override
			public void onChildRemoved(DataSnapshot _param1) {
				GenericTypeIndicator<HashMap<String, Object>> _ind = new GenericTypeIndicator<HashMap<String, Object>>() {};
				final String _childKey = _param1.getKey();
				final HashMap<String, Object> _childValue = _param1.getValue(_ind);
				
			}
			
			@Override
			public void onCancelled(DatabaseError _param1) {
				final int _errorCode = _param1.getCode();
				final String _errorMessage = _param1.getMessage();
				
			}
		};
		fb.addChildEventListener(_fb_child_listener);
	}

	@Override
	public void onResume() {
		super.onResume();
		_syncMmsCatalog();
		_applyFilterAndSort();
	}

	private void initializeLogic() {
		String initialSort = sp1.getString("sort_preference", "all_models");
		if (initialSort.equals("download")) binding.sortTv.setText("Download");
		else if (initialSort.equals("installed")) binding.sortTv.setText("Installed");
		else if (initialSort.equals("newest")) binding.sortTv.setText("Newest First");
		else if (initialSort.equals("oldest")) binding.sortTv.setText("Oldest First");
		else binding.sortTv.setText("All Models");
		
		
		_syncMmsCatalog();
		_setupDataAndStorage();
		_fetchFirebaseModels();
		_setupRecyclerViewAdapter();
		
		_setupFabAndImportDialog();
	}
	
	@Override
	public void onActivityResult(int _requestCode, int _resultCode, Intent _data) {
		super.onActivityResult(_requestCode, _resultCode, _data);
		if (_resultCode == android.app.Activity.RESULT_OK && _data != null) {
			android.net.Uri uri = (_data.getClipData() != null) ? _data.getClipData().getItemAt(0).getUri() : _data.getData();
			String path = uri.toString();
			String mode = sp1.getString("picking_mode", "");
			
			// --- SMART LOGIC: Get File Name and Size from URI ---
			String fileName = "Unknown_File";
			String fileSizeStr = "0 MB";
			android.database.Cursor cursor = getContext().getContentResolver().query(uri, null, null, null, null);
			if (cursor != null && cursor.moveToFirst()) {
				int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
				int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
				if (nameIndex != -1) fileName = cursor.getString(nameIndex);
				if (sizeIndex != -1) {
					long size = cursor.getLong(sizeIndex);
					fileSizeStr = String.format(java.util.Locale.US, "%.1f MB", (size / (1024.0 * 1024.0)));
				}
				cursor.close();
			}
			
			// --- MODE: ONNX ---
			if (mode.equals("onnx")) {
				if (fileName.toLowerCase().endsWith(".onnx")) {
					tempOnnxPath = path;
					// Save details to SP
					sp1.edit().putString("temp_onnx_name", fileName).apply();
					sp1.edit().putString("temp_onnx_size", fileSizeStr).apply();
					
					// VISUAL FEEDBACK ON DIALOG
					if (dialogView != null) {
						com.google.android.material.card.MaterialCardView card = dialogView.findViewById(R.id.btn_choose_onnx);
						android.widget.TextView tvName = dialogView.findViewById(R.id.onnx_name_tv);
						card.setStrokeColor(android.graphics.Color.parseColor("#1D61FF")); // Blue Success Stroke
						tvName.setText(fileName);
						tvName.setTextColor(android.graphics.Color.parseColor("#1D61FF"));
					}
				} else {
					// PROFESSIONAL SNACKBAR FOR WRONG EXTENSION
					com.google.android.material.snackbar.Snackbar snack = com.google.android.material.snackbar.Snackbar.make(getActivity().findViewById(android.R.id.content), "Invalid File! Please select a .onnx model file.", com.google.android.material.snackbar.Snackbar.LENGTH_LONG);
					snack.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B"));
					snack.setTextColor(android.graphics.Color.WHITE);
					snack.show();
				}
			} 
			// --- MODE: TOKENS ---
			else if (mode.equals("tokens")) {
				if (fileName.toLowerCase().endsWith(".txt")) {
					tempTokensPath = path;
					if (dialogView != null) {
						com.google.android.material.card.MaterialCardView card = dialogView.findViewById(R.id.btn_select_tokens);
						android.widget.TextView tvName = dialogView.findViewById(R.id.tokens_txt_tv);
						card.setStrokeColor(android.graphics.Color.parseColor("#48BB78")); // Green Success Stroke
						tvName.setText(fileName);
						tvName.setTextColor(android.graphics.Color.parseColor("#48BB78"));
					}
				} else {
					com.google.android.material.snackbar.Snackbar snack = com.google.android.material.snackbar.Snackbar.make(getActivity().findViewById(android.R.id.content), "Invalid File! Please select the tokens.txt file.", com.google.android.material.snackbar.Snackbar.LENGTH_LONG);
					snack.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B"));
					snack.setTextColor(android.graphics.Color.WHITE);
					snack.show();
				}
			}
		}
		
		switch (_requestCode) {
			
			default:
			break;
		}
	}
	
	public void _updateEmptyState() {
		boolean empty = modelList.isEmpty();
		binding.recyclerviewModels.setVisibility(empty ? View.GONE : View.VISIBLE);
		binding.emptyStateView.setVisibility(empty ? View.VISIBLE : View.GONE);
		binding.modelCountTv.setText("MODELS LIST (" + modelList.size() + ")");
	}
	
	
	public void _setupRecyclerViewAdapter() {
		androidx.recyclerview.widget.RecyclerView.ItemAnimator animator = binding.recyclerviewModels.getItemAnimator();
		if (animator instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
			((androidx.recyclerview.widget.SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
		}
		binding.recyclerviewModels.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
			@NonNull
			@Override
			public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
				View itemView = getActivity().getLayoutInflater().inflate(R.layout.item_model, parent, false);
				return new androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {};
			}
			@Override
			public void onBindViewHolder(@NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position, @NonNull java.util.List<Object> payloads) {
				if (payloads.isEmpty()) {
					super.onBindViewHolder(holder, position, payloads);
				} else {
					for (Object payload : payloads) {
						if (payload.equals("PROGRESS_UPDATE")) {
							int pos = holder.getAdapterPosition();
							if (pos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return;
							java.util.HashMap<String, Object> item = modelList.get(pos);
							ProgressBar progressBar = holder.itemView.findViewById(R.id.progress_download);
							TextView txtModelSub = holder.itemView.findViewById(R.id.txt_model_sub);
							int progress = item.containsKey("download_progress") ? Integer.parseInt(item.get("download_progress").toString()) : 0;
							String lang = item.containsKey("language") ? item.get("language").toString() : "Unknown";
							String gender = item.containsKey("gender") ? item.get("gender").toString() : "Unknown";
							String size = item.containsKey("size") ? item.get("size").toString() : "0 MB";
							if (progress > 0) {
								progressBar.setIndeterminate(false);
								progressBar.setProgress(progress);
								txtModelSub.setText(lang + " • " + gender + " • " + progress + "% of " + size);
							}
						}
					}
				}
			}
			@Override
			public void onBindViewHolder(@NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
				View v = holder.itemView;
				Context context = v.getContext();
				int pos = holder.getAdapterPosition();
				if (pos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return;
				java.util.HashMap<String, Object> item = modelList.get(pos);
				boolean isKokoro = item.containsKey("type") && item.get("type").toString().contains("Kokoro");
				String modelName = "Unknown Model";
				if (item.containsKey("name") && item.get("name") != null && !item.get("name").toString().trim().isEmpty()) {
					modelName = item.get("name").toString();
				} else if (item.containsKey("language") && item.containsKey("gender")) {
					modelName = item.get("language").toString() + " " + item.get("gender").toString();
					if (item.containsKey("quality")) modelName += " (" + item.get("quality").toString() + ")";
				}
				((TextView) v.findViewById(R.id.txt_model_id)).setText(modelName);
				final String finalModelName = modelName;
				String lang = item.containsKey("language") ? item.get("language").toString() : "Unknown";
				String gender = item.containsKey("gender") ? item.get("gender").toString() : "Unknown";
				String size = item.containsKey("size") ? item.get("size").toString() : "0 MB";
				final String audioUrlToPlay = item.containsKey("semple") && item.get("semple") != null ? item.get("semple").toString().trim() : "";
				String currentOnnx = item.containsKey("onnx_path") ? item.get("onnx_path").toString() : "";
				boolean isDownloaded = !currentOnnx.isEmpty();
				boolean isDownloading = item.containsKey("is_downloading") && item.get("is_downloading").toString().equals("true");
				int progress = item.containsKey("download_progress") ? Integer.parseInt(item.get("download_progress").toString()) : 0;
				String activeOnnx = sp1.getString("active_model", "");
				ImageView selectIv = v.findViewById(R.id.select_iv);
				TextView useRemoveTv = v.findViewById(R.id.use_remove_tv);
				com.google.android.material.card.MaterialCardView btnUseVoice = v.findViewById(R.id.btn_use_voice);
				com.google.android.material.card.MaterialCardView btnDelete = v.findViewById(R.id.btn_delete);
				TextView txtModelSub = v.findViewById(R.id.txt_model_sub);
				com.google.android.material.card.MaterialCardView boxPreviewStatus = v.findViewById(R.id.box_preview_status);
				ImageView imgPreview = v.findViewById(R.id.img_preview_status);
				ProgressBar progressBar = v.findViewById(R.id.progress_download);
				ProgressBar progressBuffering = v.findViewById(R.id.progress_buffering);
				if (isDownloaded) {
					progressBar.setVisibility(View.GONE);
					btnDelete.setVisibility(View.VISIBLE);
					txtModelSub.setText(lang + " • " + gender + " • " + size);
					if (currentOnnx.equals(activeOnnx)) {
						selectIv.setVisibility(View.VISIBLE);
						useRemoveTv.setText("Remove");
						btnUseVoice.setCardBackgroundColor(android.graphics.Color.parseColor("#FF4B4B"));
					} else {
						selectIv.setVisibility(View.GONE);
						useRemoveTv.setText("Use Voice");
						btnUseVoice.setCardBackgroundColor(android.graphics.Color.parseColor("#1D61FF"));
					}
				} else if (isDownloading) {
					progressBar.setVisibility(View.VISIBLE);
					progressBar.setIndeterminate(false);
					progressBar.setProgress(progress);
					btnDelete.setVisibility(View.GONE);
					if (progress > 0) {
						txtModelSub.setText(lang + " • " + gender + " • " + progress + "% of " + size);
					} else {
						txtModelSub.setText(lang + " • " + gender + " • Starting...");
					}
					selectIv.setVisibility(View.GONE);
					useRemoveTv.setText("Cancel");
					btnUseVoice.setCardBackgroundColor(android.graphics.Color.parseColor("#718096"));
				} else {
					progressBar.setVisibility(View.GONE);
					btnDelete.setVisibility(View.GONE);
					txtModelSub.setText(lang + " • " + gender + " • " + size);
					selectIv.setVisibility(View.GONE);
					useRemoveTv.setText("Download");
					btnUseVoice.setCardBackgroundColor(android.graphics.Color.parseColor("#1D61FF"));
				}
				if (item.containsKey("is_playing") && item.get("is_playing").equals("true")) {
					imgPreview.setVisibility(View.VISIBLE);
					imgPreview.setImageResource(R.drawable.icon_pause_circle);
					if (progressBuffering != null) progressBuffering.setVisibility(View.GONE);
				} else if (item.containsKey("is_buffering") && item.get("is_buffering").equals("true")) {
					imgPreview.setVisibility(View.GONE);
					if (progressBuffering != null) progressBuffering.setVisibility(View.VISIBLE);
			} else {
				if (audioUrlToPlay.isEmpty()) {
					boxPreviewStatus.setVisibility(View.GONE);
				} else {
					boxPreviewStatus.setVisibility(View.VISIBLE);
					imgPreview.setVisibility(View.VISIBLE);
					imgPreview.setImageResource(R.drawable.icon_play_circle);
				}
				if (progressBuffering != null) progressBuffering.setVisibility(View.GONE);
			}
				boxPreviewStatus.setOnClickListener(view -> {
					if (audioUrlToPlay.isEmpty()) {
						com.google.android.material.snackbar.Snackbar.make(v, "Sample audio not available.", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
						return;
					}
					if (item.containsKey("is_playing") && item.get("is_playing").equals("true")) {
						com.CodeBySonu.VoxSherpa.AudioSampleHelper.getInstance().stopAudio();
						item.put("is_playing", "false");
						if (binding.recyclerviewModels.getAdapter() != null) {
							binding.recyclerviewModels.getAdapter().notifyItemChanged(pos);
						}
						return;
					}
					for (int i = 0; i < modelList.size(); i++) {
						java.util.HashMap<String, Object> m = modelList.get(i);
						if ("true".equals(m.get("is_playing")) || "true".equals(m.get("is_buffering"))) {
							m.put("is_playing", "false");
							m.put("is_buffering", "false");
							if (binding.recyclerviewModels.getAdapter() != null) {
								binding.recyclerviewModels.getAdapter().notifyItemChanged(i);
							}
						}
					}
					item.put("is_buffering", "true");
					if (binding.recyclerviewModels.getAdapter() != null) {
						binding.recyclerviewModels.getAdapter().notifyItemChanged(pos);
					}
					com.CodeBySonu.VoxSherpa.AudioSampleHelper.getInstance().playSample(v.getContext(), audioUrlToPlay, new com.CodeBySonu.VoxSherpa.AudioSampleHelper.SamplePlayListener() {
						@Override
						public void onPlayStarted() {
							if (getActivity() != null) {
								getActivity().runOnUiThread(() -> {
									item.put("is_buffering", "false");
									item.put("is_playing", "true");
									if (binding.recyclerviewModels.getAdapter() != null) {
										binding.recyclerviewModels.getAdapter().notifyItemChanged(pos);
									}
								});
							}
						}
						@Override
						public void onPlayStopped() {
							if (getActivity() != null) {
								getActivity().runOnUiThread(() -> {
									item.put("is_playing", "false");
									item.put("is_buffering", "false");
									if (binding.recyclerviewModels.getAdapter() != null) {
										binding.recyclerviewModels.getAdapter().notifyItemChanged(pos);
									}
								});
							}
						}
						@Override
						public void onError(String error) {
							if (getActivity() != null) {
								getActivity().runOnUiThread(() -> {
									item.put("is_playing", "false");
									item.put("is_buffering", "false");
									if (binding.recyclerviewModels.getAdapter() != null) {
										binding.recyclerviewModels.getAdapter().notifyItemChanged(pos);
									}
									com.google.android.material.snackbar.Snackbar.make(v, "Playback Error", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
								});
							}
						}
					});
				});
				final String capturedOnnx = currentOnnx;
				final String capturedTokens = item.containsKey("tokens_path") && item.get("tokens_path") != null ? item.get("tokens_path").toString() : "";
				final String capturedVoicesBin = item.containsKey("voices_bin_path") && item.get("voices_bin_path") != null ? item.get("voices_bin_path").toString() : "";
				final String capturedModelType = isKokoro ? "kokoro" : "vits";
				if (isDownloading && !item.containsKey("handler_running")) {
					item.put("handler_running", "true");
					long onnxId = item.containsKey("onnx_download_id") ? Long.parseLong(item.get("onnx_download_id").toString()) : -1;
					if (onnxId != -1) {
						Handler handler = new Handler(Looper.getMainLooper());
						Runnable progressRunnable = new Runnable() {
							@Override
							public void run() {
								int targetPos = -1;
								for (int i = 0; i < modelList.size(); i++) {
									java.util.HashMap<String, Object> m = modelList.get(i);
									if (m.containsKey("onnx_download_id") && m.get("onnx_download_id").toString().equals(String.valueOf(onnxId))) {
										targetPos = i;
										break;
									}
								}
								if (targetPos == -1) return;
								java.util.HashMap<String, Object> currentItem = modelList.get(targetPos);
								if (!currentItem.containsKey("is_downloading") || currentItem.get("is_downloading").toString().equals("false")) {
									currentItem.remove("handler_running");
									return;
								}
								DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
								DownloadManager.Query q = new DownloadManager.Query();
								q.setFilterById(onnxId);
								Cursor cursor = dm.query(q);
								if (cursor != null && cursor.moveToFirst()) {
									int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
									int downloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
									int totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
									if (statusIndex >= 0) {
										int status = cursor.getInt(statusIndex);
										long bytesDownloaded = downloadedIndex >= 0 ? cursor.getLong(downloadedIndex) : 0;
										long bytesTotal = totalIndex >= 0 ? cursor.getLong(totalIndex) : -1;
										if (status == DownloadManager.STATUS_SUCCESSFUL) {
											currentItem.put("is_downloading", "false");
											currentItem.put("download_progress", "100");
											currentItem.remove("handler_running");
											String targetOnnxName = currentItem.containsKey("target_onnx_name") ? currentItem.get("target_onnx_name").toString() : "";
											String targetTokensName = currentItem.containsKey("target_tokens_name") ? currentItem.get("target_tokens_name").toString() : "";
											String targetVoicesName = currentItem.containsKey("target_voices_name") ? currentItem.get("target_voices_name").toString() : "";
											File onnxFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), targetOnnxName);
											File tokensFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), targetTokensName);
											currentItem.put("onnx_path", onnxFile.getAbsolutePath());
											currentItem.put("tokens_path", tokensFile.getAbsolutePath());
											boolean isKokoroType = currentItem.containsKey("type") && currentItem.get("type").toString().contains("Kokoro");
											if (isKokoroType && !targetVoicesName.isEmpty()) {
												File voicesFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), targetVoicesName);
												currentItem.put("voices_bin_path", voicesFile.getAbsolutePath());
											}
											sp1.edit().putString("models_data", new com.google.gson.Gson().toJson(modelList)).apply();
											com.CodeBySonu.VoxSherpa.system.TtsDefaultHelper.syncDefaultVoices(context);
											final int finalTargetPos = targetPos;
											if (getActivity() != null) {
												getActivity().runOnUiThread(() -> {
													if (isAdded() && binding.recyclerviewModels.getAdapter() != null) {
														binding.recyclerviewModels.getAdapter().notifyItemChanged(finalTargetPos);
													}
												});
											}
											cursor.close();
											return;
										} else if (status == DownloadManager.STATUS_FAILED) {
											int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
											int reason = reasonIndex >= 0 ? cursor.getInt(reasonIndex) : -1;
											currentItem.put("is_downloading", "false");
											currentItem.put("download_progress", "0");
											currentItem.remove("handler_running");
											final int finalTargetPos = targetPos;
											if (getActivity() != null) {
												getActivity().runOnUiThread(() -> {
													if (!isAdded()) return;
													if(binding.recyclerviewModels.getAdapter() != null) {
														binding.recyclerviewModels.getAdapter().notifyItemChanged(finalTargetPos);
													}
													View root = getView();
													if (root != null) {
														com.google.android.material.snackbar.Snackbar.make(root, "Download Failed! Code: " + reason, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
														.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B")).setTextColor(android.graphics.Color.WHITE).show();
													}
												});
											}
											cursor.close();
											return;
										} else if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PAUSED) {
											if (bytesTotal > 0) {
												int prog = (int) ((bytesDownloaded * 100) / bytesTotal);
												currentItem.put("download_progress", String.valueOf(prog));
											}
											final int finalTargetPos = targetPos;
											if (getActivity() != null) {
												getActivity().runOnUiThread(() -> {
													if (isAdded() && binding.recyclerviewModels.getAdapter() != null) {
														binding.recyclerviewModels.getAdapter().notifyItemChanged(finalTargetPos, "PROGRESS_UPDATE");
													}
												});
											}
										}
									}
									cursor.close();
								}
								handler.postDelayed(this, 500);
							}
						};
						handler.post(progressRunnable);
					}
				}
				btnUseVoice.setOnClickListener(view -> {
					if (isLoading) return;
					int clickedPos = holder.getAdapterPosition();
					if (clickedPos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return;
					if (isDownloading) {
						try {
							long onnxId = Long.parseLong(item.get("onnx_download_id").toString());
							long tokensId = Long.parseLong(item.get("tokens_download_id").toString());
							DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
							dm.remove(onnxId, tokensId);
							if (isKokoro && item.containsKey("voices_bin_download_id")) {
								long voicesId = Long.parseLong(item.get("voices_bin_download_id").toString());
								dm.remove(voicesId);
							}
						} catch (Exception e) {}
						item.put("is_downloading", "false");
						item.put("download_progress", "0");
						if(binding.recyclerviewModels.getAdapter() != null) {
							binding.recyclerviewModels.getAdapter().notifyItemChanged(clickedPos);
						}
					} else if (!isDownloaded) {
						String onnxUrl = item.containsKey("model_url") ? item.get("model_url").toString() : "";
						String tokensUrl = item.containsKey("tokens_url") ? item.get("tokens_url").toString() : "";
						String voicesBinUrl = item.containsKey("voices_bin_url") ? item.get("voices_bin_url").toString() : "";
						if (onnxUrl.isEmpty() || tokensUrl.isEmpty() || (isKokoro && voicesBinUrl.isEmpty())) {
							com.google.android.material.snackbar.Snackbar.make(v, "Unable to download. Required links are missing.", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
							.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B")).setTextColor(android.graphics.Color.WHITE).show();
							return;
						}
						try {
							DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
							DownloadManager.Request reqOnnx = new DownloadManager.Request(Uri.parse(onnxUrl));
							String onnxFileName = "model_" + System.currentTimeMillis() + ".onnx";
							reqOnnx.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, onnxFileName);
							reqOnnx.setTitle("Downloading Voice Model");
							reqOnnx.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
							long onnxId = dm.enqueue(reqOnnx);
							DownloadManager.Request reqTokens = new DownloadManager.Request(Uri.parse(tokensUrl));
							String tokensFileName = "tokens_" + System.currentTimeMillis() + ".txt";
							reqTokens.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, tokensFileName);
							reqTokens.setTitle("Downloading Tokens");
							long tokensId = dm.enqueue(reqTokens);
							long voicesBinId = -1;
							String voicesBinFileName = "";
							if (isKokoro) {
								DownloadManager.Request reqVoices = new DownloadManager.Request(Uri.parse(voicesBinUrl));
								voicesBinFileName = "voices_" + System.currentTimeMillis() + ".bin";
								reqVoices.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, voicesBinFileName);
								reqVoices.setTitle("Downloading Voices Library");
								voicesBinId = dm.enqueue(reqVoices);
								item.put("voices_bin_download_id", String.valueOf(voicesBinId));
							}
							item.put("target_onnx_name", onnxFileName);
							item.put("target_tokens_name", tokensFileName);
							if (isKokoro) item.put("target_voices_name", voicesBinFileName);
							item.put("is_downloading", "true");
							item.put("download_progress", "0");
							item.put("onnx_download_id", String.valueOf(onnxId));
							item.put("tokens_download_id", String.valueOf(tokensId));
							if(binding.recyclerviewModels.getAdapter() != null) {
								binding.recyclerviewModels.getAdapter().notifyItemChanged(clickedPos);
							}
						} catch (IllegalArgumentException e) {
							com.google.android.material.snackbar.Snackbar.make(v, "Unable to initiate download. Link is invalid.", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
							.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B")).setTextColor(android.graphics.Color.WHITE).show();
						} catch (Exception e) {
							com.google.android.material.snackbar.Snackbar.make(v, "An unexpected error occurred.", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
							.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B")).setTextColor(android.graphics.Color.WHITE).show();
						}
					} else {
						if (capturedOnnx.equals(activeOnnx)) {
							isLoading = true;
							btnUseVoice.setEnabled(false);
							btnUseVoice.setAlpha(0.5f);
							useRemoveTv.setText("Loading...");
							try {
								com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().destroy();
								try {
									com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().destroy();
								} catch (Throwable ignoredKokoro) {}
							} catch (Throwable ignored) {}
							sp1.edit()
							.putString("active_model", "")
							.putString("active_tokens", "")
							.putString("active_model_name", "")
							.putString("active_model_type", "")
							.putString("active_voices_bin", "")
							.putString("active_language", "")
							.apply();
							isLoading = false;
							btnUseVoice.setEnabled(true);
							btnUseVoice.setAlpha(1.0f);
							if(binding.recyclerviewModels.getAdapter() != null) {
								binding.recyclerviewModels.getAdapter().notifyDataSetChanged();
							}
							return;
						}
						if (isKokoro) {
							new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
							.setTitle("Studio Quality Voice")
							.setMessage("Kokoro is a high-fidelity neural model. Due to its complex architecture, synthesis may take longer than standard voices. Generation speed depends entirely on your device's processor capabilities.")
							.setPositiveButton("I Understand", (dialog, which) -> {
								proceedToLoadModel(holder, btnUseVoice, useRemoveTv, capturedOnnx, capturedTokens, capturedVoicesBin, capturedModelType, finalModelName, lang);
							})
							.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
							.setCancelable(true)
							.show();
						} else {
							proceedToLoadModel(holder, btnUseVoice, useRemoveTv, capturedOnnx, capturedTokens, capturedVoicesBin, capturedModelType, finalModelName, lang);
						}
					}
				});
				v.findViewById(R.id.btn_delete).setOnClickListener(view -> {
					int safePos = holder.getAdapterPosition();
					if (safePos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return;
					java.util.HashMap<String, Object> itemToDelete = modelList.get(safePos);
					String onnxToDelete   = itemToDelete.containsKey("onnx_path")   && itemToDelete.get("onnx_path")   != null ? itemToDelete.get("onnx_path").toString()   : "";
					String tokensToDelete = itemToDelete.containsKey("tokens_path") && itemToDelete.get("tokens_path") != null ? itemToDelete.get("tokens_path").toString() : "";
					String voicesToDelete = itemToDelete.containsKey("voices_bin_path") && itemToDelete.get("voices_bin_path") != null ? itemToDelete.get("voices_bin_path").toString() : "";
					if (onnxToDelete.equals(sp1.getString("active_model", ""))) {
						try {
							com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().destroy();
							try {
								com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().destroy();
							} catch (Throwable ignored) {}
						} catch (Throwable ignored) {}
						sp1.edit()
						.putString("active_model", "")
						.putString("active_tokens", "")
						.putString("active_model_name", "")
						.putString("active_model_type", "")
						.putString("active_voices_bin", "")
						.putString("active_language", "")
						.apply();
					}
					if (!onnxToDelete.isEmpty())   new File(onnxToDelete).delete();
					if (!tokensToDelete.isEmpty()) new File(tokensToDelete).delete();
					if (!voicesToDelete.isEmpty()) new File(voicesToDelete).delete();
					String allData = sp1.getString("models_data", "[]");
					java.util.ArrayList<java.util.HashMap<String, Object>> mList = new com.google.gson.Gson().fromJson(allData, new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType());
					if (mList != null) {
						String nameToDelete = itemToDelete.containsKey("name") ? itemToDelete.get("name").toString() : "";
						for (int i = 0; i < mList.size(); i++) {
							String mName = mList.get(i).containsKey("name") ? mList.get(i).get("name").toString() : "";
							if (mName.equals(nameToDelete)) {
								if (itemToDelete.containsKey("model_url")) {
									mList.get(i).remove("onnx_path");
									mList.get(i).remove("tokens_path");
									mList.get(i).remove("voices_bin_path");
								} else {
									mList.remove(i);
								}
								break;
							}
						}
						sp1.edit().putString("models_data", new com.google.gson.Gson().toJson(mList)).apply();
					}
					_applyFilterAndSort();
				});
			}
			private void proceedToLoadModel(
			androidx.recyclerview.widget.RecyclerView.ViewHolder holder,
			com.google.android.material.card.MaterialCardView btnUseVoice,
			TextView useRemoveTv,
			String capturedOnnx,
			String capturedTokens,
			String capturedVoicesBin,
			String capturedModelType,
			String finalModelName,
			String capturedLanguage) {
				boolean isKokoroType = capturedModelType.equals("kokoro");
				isLoading = true;
				btnUseVoice.setEnabled(false);
				btnUseVoice.setAlpha(0.5f);
				useRemoveTv.setText("Loading...");
				new Thread(() -> {
					try { com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().cancel(); } catch (Throwable ignored) {}
					try { com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().cancel(); } catch (Throwable ignored) {}
					try { Thread.sleep(200); } catch (Exception ignored) {}
					try {
						com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().destroy();
					} catch (Throwable ignored) {}
					try {
						com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().destroy();
					} catch (Throwable ignored) {}
					String result;
					try {
						if (isKokoroType) {
							result = com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().loadModel(
							holder.itemView.getContext(), capturedOnnx, capturedTokens, capturedVoicesBin
							);
						} else {
							result = com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().loadModel(
							holder.itemView.getContext(), capturedOnnx, capturedTokens
							);
						}
					} catch (Throwable t) {
						result = "Error: Invalid or corrupt model.";
					}
					final String finalResult = result;
					if (getActivity() != null) {
						getActivity().runOnUiThread(() -> {
							if (!isAdded()) return;
							View root = getView();
							try {
								if ("Success".equals(finalResult)) {
									sp1.edit()
									.putString("active_model", capturedOnnx)
									.putString("active_tokens", capturedTokens)
									.putString("active_model_name", finalModelName)
									.putString("active_model_type", capturedModelType)
									.putString("active_voices_bin", capturedVoicesBin)
									.putString("active_language", capturedLanguage)
									.apply();
								} else {
									if (root != null) {
										com.google.android.material.snackbar.Snackbar.make(root, "Failed to load model.", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
										.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B"))
										.setTextColor(android.graphics.Color.WHITE).show();
									}
								}
							} finally {
								isLoading = false;
								btnUseVoice.setEnabled(true);
								btnUseVoice.setAlpha(1.0f);
								if(binding.recyclerviewModels.getAdapter() != null) {
									binding.recyclerviewModels.getAdapter().notifyDataSetChanged();
								}
							}
						});
					}
				}).start();
			}
			@Override
			public int getItemCount() {
				return modelList.size();
			}
		});
		
	}
	
	
	public void _setupFabAndImportDialog() {
		binding.fabAddModel.setOnClickListener(v -> {
			importDialog = new com.google.android.material.bottomsheet.BottomSheetDialog(getContext());
			dialogView   = getActivity().getLayoutInflater().inflate(R.layout.dialog_import_model, null);
			importDialog.setContentView(dialogView);
			
			importDialog.setOnShowListener(dialogInterface -> {
				View bottomSheet = importDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
				if (bottomSheet != null) {
					bottomSheet.setBackgroundColor(Color.TRANSPARENT);
				}
			});
			
			tempOnnxPath   = "";
			tempTokensPath = "";
			sp1.edit().remove("temp_onnx_name").remove("temp_onnx_size").apply();
			
			String[] languages = getResources().getStringArray(R.array.language_list);
			ArrayAdapter<String> langAdapter = new ArrayAdapter<>(getContext(), R.layout.custom_dropdown_item, R.id.tv_drop_item, languages);
			AutoCompleteTextView dropdownLang = dialogView.findViewById(R.id.dropdown_lang);
			if(dropdownLang != null) dropdownLang.setAdapter(langAdapter);
			
			String[] genders = new String[]{"Male", "Female", "Neutral"};
			ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(getContext(), R.layout.custom_dropdown_item, R.id.tv_drop_item, genders);
			AutoCompleteTextView dropdownGender = dialogView.findViewById(R.id.dropdown_gender);
			if(dropdownGender != null) dropdownGender.setAdapter(genderAdapter);
			
			dialogView.findViewById(R.id.btn_close).setOnClickListener(view -> importDialog.dismiss());
			
			dialogView.findViewById(R.id.btn_choose_onnx).setOnClickListener(view -> {
				sp1.edit().putString("picking_mode", "onnx").apply();
				FilePicker.setType("*/*");
				startActivityForResult(FilePicker, REQ_CD_FILEPICKER);
			});
			
			dialogView.findViewById(R.id.btn_select_tokens).setOnClickListener(view -> {
				sp1.edit().putString("picking_mode", "tokens").apply();
				FilePicker.setType("text/plain");
				startActivityForResult(FilePicker, REQ_CD_FILEPICKER);
			});
			
			dialogView.findViewById(R.id.btn_import_to_library).setOnClickListener(view -> {
				
				String selectedLang = dropdownLang != null ? dropdownLang.getText().toString().trim() : "";
				String selectedGender = dropdownGender != null ? dropdownGender.getText().toString().trim() : "";
				String errorMessage = "";
				
				if (tempOnnxPath.isEmpty()) {
					errorMessage = "Please add an .onnx model file!";
				} else if (tempTokensPath.isEmpty()) {
					errorMessage = "Please add a tokens.txt file!";
				} else if (selectedLang.isEmpty()) {
					errorMessage = "Please select a Language!";
				} else if (selectedGender.isEmpty()) {
					errorMessage = "Please select a Gender!";
				}
				
				if (!errorMessage.isEmpty()) {
					Snackbar snackbar = Snackbar.make(dialogView, errorMessage, Snackbar.LENGTH_INDEFINITE)
					.setBackgroundTint(Color.parseColor("#FF4B4B"))
					.setTextColor(Color.WHITE)
					.setActionTextColor(Color.WHITE);
					snackbar.setAction("✕", v1 -> snackbar.dismiss());
					snackbar.show();
					return;
				}
				
				String newModelName = sp1.getString("temp_onnx_name", "Unknown Model");
				
				boolean isDuplicate = false;
				for (HashMap<String, Object> model : modelList) {
					if (model.containsKey("name") && newModelName.equals(model.get("name").toString())) {
						isDuplicate = true;
						break;
					}
				}
				
				if (isDuplicate) {
					Snackbar snackbar = Snackbar.make(dialogView, "This model is already in your library", Snackbar.LENGTH_INDEFINITE)
					.setBackgroundTint(Color.parseColor("#FF4B4B"))
					.setTextColor(Color.WHITE)
					.setActionTextColor(Color.WHITE);
					snackbar.setAction("✕", v1 -> snackbar.dismiss());
					snackbar.show();
					return;
				}
				
				view.setEnabled(false);
				view.setAlpha(0.5f);
				
				final Context ctx = getContext().getApplicationContext();
				final String finalModelName       = newModelName;
				final String finalLang            = selectedLang;
				final String finalGender          = selectedGender;
				final String finalOnnxUri         = tempOnnxPath;
				final String finalTokensUri       = tempTokensPath;
				final String finalSize            = sp1.getString("temp_onnx_size", "Unknown Size");
				
				new Thread(() -> {
					File internalOnnx   = null;
					File internalTokens = null;
					boolean copySuccess = false;
					
					try {
						File modelsDir = new File(ctx.getFilesDir(), "PiperModels");
						if (!modelsDir.exists()) modelsDir.mkdirs();
						
						String safeName = finalModelName.replaceAll("[^a-zA-Z0-9]", "_") + "_" + System.currentTimeMillis();
						internalOnnx   = new File(modelsDir, safeName + ".onnx");
						internalTokens = new File(modelsDir, safeName + ".txt");
						
						try (InputStream is1 = ctx.getContentResolver().openInputStream(android.net.Uri.parse(finalOnnxUri));
						FileOutputStream os1 = new FileOutputStream(internalOnnx)) {
							if (is1 == null) throw new Exception("ONNX stream failed");
							byte[] buf = new byte[16384]; int len;
							while ((len = is1.read(buf)) != -1) os1.write(buf, 0, len);
						}
						
						try (InputStream is2 = ctx.getContentResolver().openInputStream(android.net.Uri.parse(finalTokensUri));
						FileOutputStream os2 = new FileOutputStream(internalTokens)) {
							if (is2 == null) throw new Exception("Tokens stream failed");
							byte[] buf = new byte[16384]; int len;
							while ((len = is2.read(buf)) != -1) os2.write(buf, 0, len);
						}
						
						copySuccess = true;
						
					} catch (Exception e) {
						if (internalOnnx   != null && internalOnnx.exists())   internalOnnx.delete();
						if (internalTokens != null && internalTokens.exists()) internalTokens.delete();
						
						if (getActivity() != null) {
							getActivity().runOnUiThread(() -> {
								view.setEnabled(true);
								view.setAlpha(1.0f);
								Snackbar snackbar = Snackbar.make(dialogView, "Failed to import files.", Snackbar.LENGTH_INDEFINITE)
								.setBackgroundTint(Color.parseColor("#FF4B4B"))
								.setTextColor(Color.WHITE)
								.setActionTextColor(Color.WHITE);
								snackbar.setAction("✕", v1 -> snackbar.dismiss());
								snackbar.show();
							});
						}
					}
					
					if (copySuccess) {
						final String savedOnnxPath   = internalOnnx.getAbsolutePath();
						final String savedTokensPath = internalTokens.getAbsolutePath();
						
						if (getActivity() != null) {
							getActivity().runOnUiThread(() -> {
								HashMap<String, Object> newModel = new HashMap<>();
								newModel.put("name",        finalModelName);
								newModel.put("onnx_path",   savedOnnxPath);
								newModel.put("tokens_path", savedTokensPath);
								newModel.put("size",        finalSize);
								newModel.put("language",    finalLang);
								newModel.put("gender",      finalGender);
								
								modelList.add(newModel);
								sp1.edit().putString("models_data", new Gson().toJson(modelList)).apply();
								
								tempOnnxPath   = "";
								tempTokensPath = "";
								importDialog.dismiss();
								
								if (binding.recyclerviewModels.getAdapter() != null) {
									binding.recyclerviewModels.getAdapter().notifyItemInserted(modelList.size() - 1);
									_updateEmptyState();
								} else {
									binding.recyclerviewModels.setAdapter(binding.recyclerviewModels.getAdapter()); 
								}
							});
						}
					}
				}).start();
			});
			
			importDialog.show();
		});
		
	}
	
	
	public void _applyFilterAndSort() {
		String savedData = sp1.getString("models_data", "[]");
		java.util.ArrayList<java.util.HashMap<String, Object>> masterList = new com.google.gson.Gson().fromJson(savedData, new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType());
		if (masterList == null) masterList = new java.util.ArrayList<>();
		String currentSort = sp1.getString("sort_preference", "all_models");
		String currentLanguage = sp1.getString("language_filter", "All Languages");
		modelList.clear();
		for (java.util.HashMap<String, Object> item : masterList) {
			String onnxPath = item.containsKey("onnx_path") && item.get("onnx_path") != null ? item.get("onnx_path").toString() : "";
			boolean isInstalled = !onnxPath.isEmpty();
			String itemLang = item.containsKey("language") && item.get("language") != null ? item.get("language").toString() : "";
			boolean languageMatch = currentLanguage.equals("All Languages") || currentLanguage.equals(itemLang);
			if (languageMatch) {
				if (currentSort.equals("download")) {
					if (!isInstalled) modelList.add(item);
				} else if (currentSort.equals("installed")) {
					if (isInstalled) modelList.add(item);
				} else {
					modelList.add(item);
				}
			}
		}
		if (currentSort.equals("newest")) {
			java.util.Collections.reverse(modelList);
		}
		if (binding.recyclerviewModels.getAdapter() != null) {
			binding.recyclerviewModels.getAdapter().notifyDataSetChanged();
		}
		_updateEmptyState();
		if (!currentLanguage.equals("All Languages")) {
			binding.sortTv.setText(currentLanguage);
		} else if (currentSort.equals("download")) {
			binding.sortTv.setText("Download");
		} else if (currentSort.equals("installed")) {
			binding.sortTv.setText("Installed");
		} else if (currentSort.equals("newest")) {
			binding.sortTv.setText("Newest First");
		} else if (currentSort.equals("oldest")) {
			binding.sortTv.setText("Oldest First");
		} else {
			binding.sortTv.setText("All Models");
		}
		
	}
	
	
	public void _setupDataAndStorage() {
		binding.recyclerviewModels.setLayoutManager(new LinearLayoutManager(getContext()));
		
		String savedData = sp1.getString("models_data", "[]");
		modelList = new Gson().fromJson(savedData, new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());
		
		try {
			StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
			long bytesTotal     = stat.getBlockSizeLong() * stat.getBlockCountLong();
			long bytesAvailable = stat.getBlockSizeLong() * stat.getAvailableBlocksLong();
			long bytesUsed      = bytesTotal - bytesAvailable;
			
			double gbTotal = bytesTotal / (1024.0 * 1024.0 * 1024.0);
			double gbUsed  = bytesUsed  / (1024.0 * 1024.0 * 1024.0);
			int progressPercent = (int) ((bytesUsed * 100) / bytesTotal);
			
			binding.storageUsedTv.setText(String.format(Locale.US, "%.1f GB of %.1f GB used", gbUsed, gbTotal));
			binding.storageUsedProgressbar.setProgress(progressPercent);
		} catch (Exception e) {
			binding.storageUsedTv.setText("Storage info unavailable");
			binding.storageUsedProgressbar.setProgress(0);
		}
		
		_updateEmptyState();
		
	}
	
	
	public void _syncMmsCatalog() {
		SharedPreferences sp3 = getContext().getSharedPreferences("sp3", android.content.Context.MODE_PRIVATE);
		boolean mmsEnabled = sp3.getBoolean("mms_models_enabled", false);

		String savedData = sp1.getString("models_data", "[]");
		java.util.ArrayList<HashMap<String, Object>> masterList = GSON.fromJson(savedData, new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());
		if (masterList == null) masterList = new java.util.ArrayList<>();

		if (mmsEnabled) {
			java.util.List<HashMap<String, Object>> catalog = com.CodeBySonu.VoxSherpa.MmsCatalog.loadModelEntries(getContext());
			if (catalog == null || catalog.isEmpty()) return;

			java.util.HashSet<String> existingUrls = new java.util.HashSet<>();
			for (HashMap<String, Object> m : masterList) {
				if (m.containsKey("model_url")) existingUrls.add(m.get("model_url").toString());
			}

			boolean changed = false;
			for (HashMap<String, Object> entry : catalog) {
				String url = entry.containsKey("model_url") ? entry.get("model_url").toString() : "";
				if (!url.isEmpty() && !existingUrls.contains(url)) {
					masterList.add(entry);
					existingUrls.add(url);
					changed = true;
				}
			}

			if (changed) {
				sp1.edit().putString("models_data", GSON.toJson(masterList)).apply();
			}
		} else {
			boolean changed = false;
			java.util.Iterator<HashMap<String, Object>> it = masterList.iterator();
			while (it.hasNext()) {
				HashMap<String, Object> m = it.next();
				boolean isMms = m.containsKey("type") && m.get("type").toString().contains("MMS");
				String onnxPath = m.containsKey("onnx_path") && m.get("onnx_path") != null ? m.get("onnx_path").toString() : "";
				if (isMms && onnxPath.isEmpty()) {
					it.remove();
					changed = true;
				}
			}
			if (changed) {
				sp1.edit().putString("models_data", GSON.toJson(masterList)).apply();
			}
		}
	}


	public void _fetchFirebaseModels() {
		fb.addListenerForSingleValueEvent(new ValueEventListener() {
			@Override
			public void onDataChange(@NonNull DataSnapshot snapshot) {
				if (snapshot.exists()) {
					boolean isListUpdated = false;
					java.util.ArrayList<String> onlineUrls = new java.util.ArrayList<>();
					for (DataSnapshot categorySnapshot : snapshot.getChildren()) {
						for (DataSnapshot child : categorySnapshot.getChildren()) {
							try {
								HashMap<String, Object> onlineModel = (HashMap<String, Object>) child.getValue();
								if (onlineModel != null && onlineModel.containsKey("model_url")) {
									String onlineUrl = onlineModel.get("model_url").toString();
									onlineUrls.add(onlineUrl);
									if (!onlineModel.containsKey("is_downloading")) onlineModel.put("is_downloading", "false");
									if (!onlineModel.containsKey("download_progress")) onlineModel.put("download_progress", "0");
									boolean isAlreadyExists = false;
									for (int i = 0; i < modelList.size(); i++) {
										HashMap<String, Object> localModel = modelList.get(i);
										String localUrl = localModel.containsKey("model_url") ? localModel.get("model_url").toString() : "";
										if (!onlineUrl.isEmpty() && onlineUrl.equals(localUrl)) {
											isAlreadyExists = true;
											String[] keysToSync = {"name", "type", "voices_bin_url", "semple", "gender", "language", "size", "quality", "tokens_url"};
											for (String key : keysToSync) {
												if (onlineModel.containsKey(key)) {
													Object onlineVal = onlineModel.get(key);
													Object localVal = localModel.get(key);
													if (onlineVal != null && !onlineVal.equals(localVal)) {
														localModel.put(key, onlineVal);
														isListUpdated = true;
													}
												}
											}
											break;
										}
									}
									if (!isAlreadyExists) {
										modelList.add(onlineModel);
										isListUpdated = true;
									}
								}
							} catch (Exception e) {}
						}
					}
					for (int i = modelList.size() - 1; i >= 0; i--) {
						HashMap<String, Object> localModel = modelList.get(i);
						if (localModel.containsKey("model_url")) {
							String localUrl = localModel.get("model_url").toString();
							if (!onlineUrls.contains(localUrl) && !localUrl.contains("mms-tts-multilingual-models-onnx")) {
								String onnxPath = localModel.containsKey("onnx_path") ? localModel.get("onnx_path").toString() : "";
								String tokensPath = localModel.containsKey("tokens_path") ? localModel.get("tokens_path").toString() : "";
								String voicesPath = localModel.containsKey("voices_bin_path") ? localModel.get("voices_bin_path").toString() : "";
								if (!onnxPath.isEmpty()) new java.io.File(onnxPath).delete();
								if (!tokensPath.isEmpty()) new java.io.File(tokensPath).delete();
								if (!voicesPath.isEmpty()) new java.io.File(voicesPath).delete();
								if (onnxPath.equals(sp1.getString("active_model", ""))) {
									try { com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().destroy(); } catch (Throwable ignored) {}
									try { com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().destroy(); } catch (Throwable ignored) {}
									sp1.edit().putString("active_model", "").putString("active_tokens", "").putString("active_model_name", "").apply();
								}
								modelList.remove(i);
								isListUpdated = true;
							}
						}
					}
					if (isListUpdated) {
						sp1.edit().putString("models_data", new com.google.gson.Gson().toJson(modelList)).apply();
						_updateEmptyState();
						if (binding.recyclerviewModels.getAdapter() != null) {
							binding.recyclerviewModels.getAdapter().notifyDataSetChanged();
						}
					}
				}
			}
			@Override
			public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
				com.google.android.material.snackbar.Snackbar.make(binding.getRoot(), "Failed to fetch models from server.", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
			}
		});
		
	}
	
}