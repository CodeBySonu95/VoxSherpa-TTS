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
				// 1. Icon ko rotate karna (Top ki taraf) jab dialog khule
				binding.imageview8.animate().rotation(-90f).setDuration(300).start();
				
				// 2. Dialog Setup
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
				
				// Fix for black corners in BottomSheet
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
				
				// Views ko find karna
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
				
				// Get current selections from SP1
				String currentSort = sp1.getString("sort_preference", "all_models");
				String currentLanguage = sp1.getString("language_filter", "All Languages");
				
				if(currentSort.equals("download")) rbDownload.setChecked(true);
				else if(currentSort.equals("installed")) rbInstalled.setChecked(true);
				else if(currentSort.equals("newest")) rbNewest.setChecked(true);
				else if(currentSort.equals("oldest")) rbOldest.setChecked(true);
				else rbAll.setChecked(true);
				
				// 3. COMBINED FILTER & SORT LOGIC (Runnable block to avoid duplicate code)
				Runnable applyFilterAndSort = () -> _applyFilterAndSort();
				
				// 4. SETUP LANGUAGE DROPDOWN
				String[] languagesFromXml = getResources().getStringArray(R.array.language_list);
				String[] finalLanguages = new String[languagesFromXml.length + 1];
				finalLanguages[0] = "All Languages";
				System.arraycopy(languagesFromXml, 0, finalLanguages, 1, languagesFromXml.length);
				
				android.widget.ArrayAdapter<String> langAdapter = new android.widget.ArrayAdapter<>(getContext(), R.layout.custom_dropdown_item, R.id.tv_drop_item, finalLanguages);
				android.widget.AutoCompleteTextView dropdownLang = dialogView.findViewById(R.id.dropdown_lang);
				
				if(dropdownLang != null) {
					dropdownLang.setAdapter(langAdapter);
					
					// Show the currently selected language in the dropdown UI
					dropdownLang.setText(currentLanguage, false);
					
					dropdownLang.setOnItemClickListener((parentView, view, position, id) -> {
						String selectedLang = finalLanguages[position];
						
						// Save the new language filter
						sp1.edit().putString("language_filter", selectedLang).apply();
						sortDialog.dismiss();
						
						// Trigger the combined filter logic
						applyFilterAndSort.run();
					});
				}
				
				// 5. STANDARD SORT BUTTONS CLICK LISTENER
				View.OnClickListener clickListener = v -> {
					String selected = "all_models";
					if(v.getId() == R.id.layout_download) selected = "download";
					else if(v.getId() == R.id.layout_installed) selected = "installed";
					else if(v.getId() == R.id.layout_newest) selected = "newest";
					else if(v.getId() == R.id.layout_oldest) selected = "oldest";
					
					// Save the new sort preference
					sp1.edit().putString("sort_preference", selected).apply();
					sortDialog.dismiss();
					
					// Trigger the combined filter logic
					applyFilterAndSort.run();
				};
				
				layoutAll.setOnClickListener(clickListener);
				layoutDownload.setOnClickListener(clickListener);
				layoutInstalled.setOnClickListener(clickListener);
				layoutNewest.setOnClickListener(clickListener);
				layoutOldest.setOnClickListener(clickListener);
				
				// 6. Dialog Band Hone Par Icon Reset
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
	
	
	@Override
	public void onResume() {
		super.onResume();
		_syncMmsCatalog();
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
			public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
				android.view.View itemView = getActivity().getLayoutInflater().inflate(R.layout.item_model, parent, false);
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
							
							android.widget.ProgressBar progressBar = holder.itemView.findViewById(R.id.progress_download);
							android.widget.TextView txtModelSub = holder.itemView.findViewById(R.id.txt_model_sub);
							
							int progress = ModelStorageHelper.parseIntSafe(item.get("download_progress"), 0);
							String lang = item.containsKey("language") && item.get("language") != null ? item.get("language").toString() : "Unknown";
							String gender = item.containsKey("gender") && item.get("gender") != null ? item.get("gender").toString() : "Unknown";
							String size = item.containsKey("size") && item.get("size") != null ? item.get("size").toString() : "0 MB";
							
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
				android.view.View v = holder.itemView;
				android.content.Context context = v.getContext();
				
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
				((android.widget.TextView) v.findViewById(R.id.txt_model_id)).setText(modelName);
				
				final String finalModelName = modelName;
				
				String lang = item.containsKey("language") ? item.get("language").toString() : "Unknown";
				String gender = item.containsKey("gender") ? item.get("gender").toString() : "Unknown";
				String size = item.containsKey("size") ? item.get("size").toString() : "0 MB";
				
				String currentOnnx = item.containsKey("onnx_path") && item.get("onnx_path") != null ? item.get("onnx_path").toString() : "";
				final String audioUrlToPlay = item.containsKey("semple") && item.get("semple") != null ? item.get("semple").toString().trim() : "";
				
				boolean isDownloaded = !currentOnnx.isEmpty();
				boolean isDownloading = "true".equals(ModelStorageHelper.getString(item, "is_downloading"));
				int progress = ModelStorageHelper.parseIntSafe(item.get("download_progress"), 0);
				
				String activeOnnx = sp1.getString("active_model", "");
				
				android.widget.ImageView selectIv = v.findViewById(R.id.select_iv);
				android.widget.TextView useRemoveTv = v.findViewById(R.id.use_remove_tv);
				com.google.android.material.card.MaterialCardView btnUseVoice = v.findViewById(R.id.btn_use_voice);
				com.google.android.material.card.MaterialCardView btnDelete = v.findViewById(R.id.btn_delete);
				android.widget.TextView txtModelSub = v.findViewById(R.id.txt_model_sub);
				
				com.google.android.material.card.MaterialCardView boxPreviewStatus = v.findViewById(R.id.box_preview_status);
				android.widget.ImageView imgPreview = v.findViewById(R.id.img_preview_status);
				android.widget.ProgressBar progressBar = v.findViewById(R.id.progress_download);
				android.widget.ProgressBar progressBuffering = v.findViewById(R.id.progress_buffering);
				
				if (isDownloaded) {
					progressBar.setVisibility(android.view.View.GONE);
					btnDelete.setVisibility(android.view.View.VISIBLE);
					txtModelSub.setText(lang + " • " + gender + " • " + size);
					
					if (currentOnnx.equals(activeOnnx)) {
						selectIv.setVisibility(android.view.View.VISIBLE);
						useRemoveTv.setText("Remove");
						btnUseVoice.setCardBackgroundColor(android.graphics.Color.parseColor("#FF4B4B"));
					} else {
						selectIv.setVisibility(android.view.View.GONE);
						useRemoveTv.setText("Use Voice");
						btnUseVoice.setCardBackgroundColor(android.graphics.Color.parseColor("#1D61FF"));
					}
				} else if (isDownloading) {
					progressBar.setVisibility(android.view.View.VISIBLE);
					progressBar.setIndeterminate(false);
					progressBar.setProgress(progress);
					btnDelete.setVisibility(android.view.View.GONE);
					
					if (progress > 0) {
						txtModelSub.setText(lang + " • " + gender + " • " + progress + "% of " + size);
					} else {
						txtModelSub.setText(lang + " • " + gender + " • Starting...");
					}
					
					selectIv.setVisibility(android.view.View.GONE);
					useRemoveTv.setText("Cancel");
					btnUseVoice.setCardBackgroundColor(android.graphics.Color.parseColor("#718096"));
				} else {
					progressBar.setVisibility(android.view.View.GONE);
					btnDelete.setVisibility(android.view.View.GONE);
					txtModelSub.setText(lang + " • " + gender + " • " + size);
					
					selectIv.setVisibility(android.view.View.GONE);
					useRemoveTv.setText("Download");
					btnUseVoice.setCardBackgroundColor(android.graphics.Color.parseColor("#1D61FF"));
				}
				
				if (item.containsKey("is_playing") && item.get("is_playing").equals("true")) {
					imgPreview.setVisibility(android.view.View.VISIBLE);
					imgPreview.setImageResource(R.drawable.icon_pause_circle); 
					if (progressBuffering != null) progressBuffering.setVisibility(android.view.View.GONE);
				} else if (item.containsKey("is_buffering") && item.get("is_buffering").equals("true")) {
					imgPreview.setVisibility(android.view.View.GONE);
					if (progressBuffering != null) progressBuffering.setVisibility(android.view.View.VISIBLE);
				} else {
					if (audioUrlToPlay.isEmpty()) {
						boxPreviewStatus.setVisibility(android.view.View.GONE);
					} else {
						boxPreviewStatus.setVisibility(android.view.View.VISIBLE);
						imgPreview.setVisibility(android.view.View.VISIBLE);
						imgPreview.setImageResource(R.drawable.icon_play_circle); 
					}
					if (progressBuffering != null) progressBuffering.setVisibility(android.view.View.GONE);
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
				
				// Ensure proper model type passes correctly for Explicit MMS check
				final String capturedModelType = isKokoro ? "kokoro" : (item.containsKey("type") && item.get("type").toString().equals("MMS") ? "mms" : "vits");
				
				if (isDownloading && !item.containsKey("handler_running")) {
					item.put("handler_running", "true");
					long onnxId = ModelStorageHelper.parseLongSafe(item.get("onnx_download_id"), -1L);
					long tokensId = ModelStorageHelper.parseLongSafe(item.get("tokens_download_id"), -1L);
					long voicesId = isKokoro ? ModelStorageHelper.parseLongSafe(item.get("voices_bin_download_id"), -1L) : -1L;
					final Context appContext = context.getApplicationContext();
					
					if (onnxId != -1) {
						android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
						Runnable progressRunnable = new Runnable() {
							@Override
							public void run() {
								if (!isAdded() || getActivity() == null) {
									return;
								}
								
								int targetPos = -1;
								for (int i = 0; i < modelList.size(); i++) {
									java.util.HashMap<String, Object> m = modelList.get(i);
									if (String.valueOf(onnxId).equals(ModelStorageHelper.getString(m, "onnx_download_id"))) {
										targetPos = i;
										break;
									}
								}
								
								if (targetPos == -1) return; 
								
								java.util.HashMap<String, Object> currentItem = modelList.get(targetPos);
								
								if (!"true".equals(ModelStorageHelper.getString(currentItem, "is_downloading"))) {
									currentItem.remove("handler_running");
									return; 
								}
								
								android.app.DownloadManager dm = (android.app.DownloadManager) appContext.getSystemService(Context.DOWNLOAD_SERVICE);
								long[] onnxInfo = _queryDownloadInfo(dm, onnxId);
								long[] tokensInfo = tokensId != -1 ? _queryDownloadInfo(dm, tokensId) : new long[]{ModelStorageHelper.STATUS_SUCCESSFUL, 0, 0, -1};
								Integer voicesStatus = null;
								long voicesReason = -1;
								if (isKokoro) {
									long[] voicesInfo = voicesId != -1 ? _queryDownloadInfo(dm, voicesId) : new long[]{ModelStorageHelper.STATUS_FAILED, 0, 0, -1};
									voicesStatus = (int) voicesInfo[0];
									voicesReason = voicesInfo[3];
								}
								
								int onnxStatus = (int) onnxInfo[0];
								int tokensStatus = (int) tokensInfo[0];
								ModelStorageHelper.BatchDownloadState batchState = ModelStorageHelper.evaluateDownloadBatch(onnxStatus, tokensStatus, voicesStatus);
								
								if (batchState == ModelStorageHelper.BatchDownloadState.SUCCESSFUL) {
									currentItem.put("is_downloading", "false");
									currentItem.put("download_progress", "100");
									currentItem.remove("handler_running");
									
									final String targetOnnxName = ModelStorageHelper.getString(currentItem, "target_onnx_name");
									final String targetTokensName = ModelStorageHelper.getString(currentItem, "target_tokens_name");
									final String targetVoicesName = ModelStorageHelper.getString(currentItem, "target_voices_name");
									final boolean isKokoroType = ModelStorageHelper.getString(currentItem, "type").contains("Kokoro");
									final java.util.HashMap<String, Object> itemSnapshot = new java.util.HashMap<>(currentItem);
									
									new Thread(() -> {
										try {
											java.io.File downloadsDir = appContext.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
											java.io.File internalModelsDir = new java.io.File(appContext.getFilesDir(), "secure_models");
											ModelStorageHelper.InstalledModelFiles installed = ModelStorageHelper.installDownloadedFiles(
											downloadsDir, internalModelsDir, targetOnnxName, targetTokensName, targetVoicesName, isKokoroType);
											
											itemSnapshot.put("onnx_path", installed.onnxFile.getAbsolutePath());
											itemSnapshot.put("tokens_path", installed.tokensFile.getAbsolutePath());
											if (isKokoroType && installed.voicesBinFile != null) {
												itemSnapshot.put("voices_bin_path", installed.voicesBinFile.getAbsolutePath());
											}
											
											if (getActivity() != null) {
												getActivity().runOnUiThread(() -> {
													currentItem.put("onnx_path", installed.onnxFile.getAbsolutePath());
													currentItem.put("tokens_path", installed.tokensFile.getAbsolutePath());
													if (isKokoroType && installed.voicesBinFile != null) {
														currentItem.put("voices_bin_path", installed.voicesBinFile.getAbsolutePath());
													}
													ModelStorageHelper.clearDownloadState(currentItem);
													currentItem.put("is_downloading", "false");
													currentItem.put("download_progress", "100");
													
													java.util.List<java.util.HashMap<String, Object>> updatedMaster =
													ModelStorageHelper.mergeDownloadedModel(_loadMasterModels(), itemSnapshot);
													_saveMasterModels(updatedMaster);
													com.CodeBySonu.VoxSherpa.system.TtsDefaultHelper.syncDefaultVoices(appContext);
													
													if (isAdded()) {
														_applyFilterAndSort();
													}
												});
											}
										} catch (Exception e) {
											android.util.Log.e(TAG, "Model install failed: " + e.getMessage(), e);
											if (getActivity() != null) {
												getActivity().runOnUiThread(() -> {
													ModelStorageHelper.clearDownloadState(currentItem);
													currentItem.put("is_downloading", "false");
													currentItem.put("download_progress", "0");
													if (isAdded()) {
														if (binding.recyclerviewModels.getAdapter() != null) {
															binding.recyclerviewModels.getAdapter().notifyDataSetChanged();
														}
														android.view.View root = getView();
														if (root != null) {
															com.google.android.material.snackbar.Snackbar.make(root, "Download incomplete: " + e.getMessage(), com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
															.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B")).setTextColor(android.graphics.Color.WHITE).show();
														}
													}
												});
											}
										}
									}).start();
									return;
									
								} else if (batchState == ModelStorageHelper.BatchDownloadState.FAILED) {
									long reason = onnxStatus == ModelStorageHelper.STATUS_FAILED ? onnxInfo[3]
									: (tokensStatus == ModelStorageHelper.STATUS_FAILED ? tokensInfo[3] : voicesReason);
									
									_cancelItemDownloads(appContext, currentItem);
									ModelStorageHelper.clearDownloadState(currentItem);
									currentItem.put("is_downloading", "false");
									currentItem.put("download_progress", "0");
									
									final int finalTargetPos = targetPos;
									if (getActivity() != null) {
										getActivity().runOnUiThread(() -> {
											if (!isAdded()) return;
											if (binding.recyclerviewModels.getAdapter() != null) {
												if (finalTargetPos >= 0 && finalTargetPos < modelList.size()) {
													binding.recyclerviewModels.getAdapter().notifyItemChanged(finalTargetPos);
												} else {
													binding.recyclerviewModels.getAdapter().notifyDataSetChanged();
												}
											}
											android.view.View root = getView();
											if (root != null) {
												com.google.android.material.snackbar.Snackbar.make(root, "Download Failed! Code: " + reason, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
												.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B")).setTextColor(android.graphics.Color.WHITE).show();
											}
										});
									}
									return;
									
								} else {
									long bytesDownloaded = onnxInfo[1];
									long bytesTotal = onnxInfo[2];
									if (bytesTotal > 0) {
										int prog = (int) ((bytesDownloaded * 100) / bytesTotal);
										if (prog >= 100) prog = 99;
										currentItem.put("download_progress", String.valueOf(prog));
									}
									
									final int finalTargetPos = targetPos;
									if (getActivity() != null) {
										getActivity().runOnUiThread(() -> {
											if (isAdded() && binding.recyclerviewModels.getAdapter() != null
											&& finalTargetPos >= 0 && finalTargetPos < modelList.size()) {
												binding.recyclerviewModels.getAdapter().notifyItemChanged(finalTargetPos, "PROGRESS_UPDATE");
											}
										});
									}
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
					
					if (com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().isBusy() 
					|| com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().isBusy()) {
						
						android.widget.Toast.makeText(context, "Please wait, audio generation in progress...", android.widget.Toast.LENGTH_SHORT).show();
						
						try { com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().cancel(); } catch (Throwable ignored) {}
						try { com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().cancel(); } catch (Throwable ignored) {}
						return;
					}
					
					if (isDownloading) {
						_cancelItemDownloads(context.getApplicationContext(), item);
						ModelStorageHelper.clearDownloadState(item);
						item.put("is_downloading", "false");
						item.put("download_progress", "0");
						if (binding.recyclerviewModels.getAdapter() != null) {
							binding.recyclerviewModels.getAdapter().notifyItemChanged(clickedPos);
						}
						
					} else if (!isDownloaded) {
						String onnxUrl = ModelStorageHelper.getString(item, "model_url");
						String tokensUrl = ModelStorageHelper.getString(item, "tokens_url");
						String voicesBinUrl = ModelStorageHelper.getString(item, "voices_bin_url");
						
						if (onnxUrl.isEmpty() || tokensUrl.isEmpty() || (isKokoro && voicesBinUrl.isEmpty())) {
							com.google.android.material.snackbar.Snackbar.make(v, "Unable to download. Required links are missing.", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
							.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B")).setTextColor(android.graphics.Color.WHITE).show();
							return;
						}
						
						try {
							android.app.DownloadManager dm = (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
							String uniqueTag = System.currentTimeMillis() + "_" + (System.nanoTime() & 0xFFFF);
							
							android.app.DownloadManager.Request reqOnnx = new android.app.DownloadManager.Request(android.net.Uri.parse(onnxUrl));
							String onnxFileName = "model_" + uniqueTag + ".onnx";
							reqOnnx.setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, onnxFileName);
							reqOnnx.setTitle("Downloading Voice Model");
							reqOnnx.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE);
							long onnxId = dm.enqueue(reqOnnx);
							
							android.app.DownloadManager.Request reqTokens = new android.app.DownloadManager.Request(android.net.Uri.parse(tokensUrl));
							String tokensFileName = "tokens_" + uniqueTag + ".txt";
							reqTokens.setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, tokensFileName);
							reqTokens.setTitle("Downloading Tokens");
							long tokensId = dm.enqueue(reqTokens);
							
							long voicesBinId = -1;
							String voicesBinFileName = "";
							
							if (isKokoro) {
								android.app.DownloadManager.Request reqVoices = new android.app.DownloadManager.Request(android.net.Uri.parse(voicesBinUrl));
								voicesBinFileName = "voices_" + uniqueTag + ".bin";
								reqVoices.setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, voicesBinFileName);
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
							
							if (getActivity() != null) {
								getActivity().runOnUiThread(() -> {
									if (binding.recyclerviewModels.getAdapter() != null) {
										binding.recyclerviewModels.getAdapter().notifyItemChanged(clickedPos); 
									}
								});
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
							if (binding.recyclerviewModels.getAdapter() != null) {
								binding.recyclerviewModels.getAdapter().notifyDataSetChanged();
							}
							return;
						}
						
						if (isKokoro) {
							new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
							.setTitle("Studio Quality Voice")
							.setMessage("Kokoro is a high-fidelity neural model. Due to its complex architecture, synthesis may take longer than standard voices.")
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
							try { com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().destroy(); } catch (Throwable ignored) {}
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
					
					if (!onnxToDelete.isEmpty())   new java.io.File(onnxToDelete).delete();
					if (!tokensToDelete.isEmpty()) new java.io.File(tokensToDelete).delete();
					if (!voicesToDelete.isEmpty()) new java.io.File(voicesToDelete).delete();
					
					_cancelItemDownloads(context.getApplicationContext(), itemToDelete);
					java.util.List<java.util.HashMap<String, Object>> updatedMaster =
					ModelStorageHelper.removeDownloadedModel(_loadMasterModels(), itemToDelete);
					_saveMasterModels(updatedMaster);
					
					_applyFilterAndSort();
				});
			}
			
			private void proceedToLoadModel(
			androidx.recyclerview.widget.RecyclerView.ViewHolder holder,
			com.google.android.material.card.MaterialCardView btnUseVoice,
			android.widget.TextView useRemoveTv,
			String capturedOnnx,
			String capturedTokens,
			String capturedVoicesBin,
			String capturedModelType,
			String finalModelName,
			String capturedLanguage) { 
				
				boolean isKokoroType = capturedModelType.equals("kokoro");
				// FIX: Extract the explicit MMS flag from the type string
				boolean isMmsType = capturedModelType.equals("mms");
				
				isLoading = true;
				btnUseVoice.setEnabled(false);
				btnUseVoice.setAlpha(0.5f);
				useRemoveTv.setText("Loading...");
				
				new Thread(() -> {
					try { com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().cancel(); } catch (Throwable ignored) {}
					try { com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().cancel(); } catch (Throwable ignored) {}
					
					try { Thread.sleep(200); } catch (Exception ignored) {}
					
					try { com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().destroy(); } catch (Throwable ignored) {}
					try { com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().destroy(); } catch (Throwable ignored) {}
					
					String result = "Error";
					int totalSpeakers = 1;
					
					try {
						if (isKokoroType) {
							result = com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().loadModel(
							holder.itemView.getContext(), capturedOnnx, capturedTokens, capturedVoicesBin
							);
							if ("Success".equals(result)) {
								totalSpeakers = com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().getNumSpeakers();
							}
						} else {
							// FIX: Pass explicit isMmsType (Boolean) flag to the updated Engine method
							result = com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().loadModel(
							holder.itemView.getContext(), capturedOnnx, capturedTokens, capturedLanguage, isMmsType
							);
							if ("Success".equals(result)) {
								totalSpeakers = com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().getNumSpeakers();
							}
						}
					} catch (Throwable t) {
						result = "Error: Invalid or corrupt model.";
					}
					
					final String finalResult = result;
					final int finalTotalSpeakers = totalSpeakers; 
					
					if (getActivity() != null) {
						getActivity().runOnUiThread(() -> {
							try {
								if (!isAdded()) return;
								android.view.View root = getView();
								
								if ("Success".equals(finalResult)) {
									sp1.edit()
									.putString("active_model", capturedOnnx)
									.putString("active_tokens", capturedTokens)
									.putString("active_model_name", finalModelName)
									.putString("active_model_type", capturedModelType)
									.putString("active_voices_bin", capturedVoicesBin)
									.putString("active_language", capturedLanguage) 
									.putInt("active_speaker_count", finalTotalSpeakers) 
									.apply();
									
								} else {
									if (root != null) {
										String errMsg = (finalResult != null && !finalResult.isEmpty())
										? "Failed to load model: " + finalResult
										: "Failed to load model.";
										com.google.android.material.snackbar.Snackbar.make(root, errMsg, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
										.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B"))
										.setTextColor(android.graphics.Color.WHITE).show();
									}
								}
							} finally {
								isLoading = false;
								btnUseVoice.setEnabled(true);
								btnUseVoice.setAlpha(1.0f);
								if (isAdded() && binding.recyclerviewModels.getAdapter() != null) {
									binding.recyclerviewModels.getAdapter().notifyDataSetChanged();
								}
							}
						});
					} else {
						isLoading = false;
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
								
								java.util.List<java.util.HashMap<String, Object>> updatedMaster =
								ModelStorageHelper.mergeDownloadedModel(_loadMasterModels(), newModel);
								_saveMasterModels(updatedMaster);
								
								tempOnnxPath   = "";
								tempTokensPath = "";
								importDialog.dismiss();
								
								_applyFilterAndSort();
							});
						}
					}
				}).start();
			});
			
			importDialog.show();
		});
		
		
	}
	
	
	public void _applyFilterAndSort() {
		// Preserve any currently in-flight downloads before rebuilding modelList from sp1
		java.util.HashMap<String, java.util.HashMap<String, Object>> inFlightByUrl = new java.util.HashMap<>();
		for (java.util.HashMap<String, Object> existing : modelList) {
			if ("true".equals(ModelStorageHelper.getString(existing, "is_downloading"))) {
				String url = ModelStorageHelper.getString(existing, "model_url");
				if (!url.isEmpty()) {
					inFlightByUrl.put(url, existing);
				}
			}
		}
		
		java.util.ArrayList<java.util.HashMap<String, Object>> masterList = _loadMasterModels();
		if (!inFlightByUrl.isEmpty()) {
			for (int i = 0; i < masterList.size(); i++) {
				String url = ModelStorageHelper.getString(masterList.get(i), "model_url");
				if (!url.isEmpty() && inFlightByUrl.containsKey(url)) {
					masterList.set(i, inFlightByUrl.get(url));
				}
			}
		}
		
		String currentSort = sp1.getString("sort_preference", "all_models");
		String currentLanguage = sp1.getString("language_filter", "All Languages");
		
		modelList.clear();
		modelList.addAll(ModelStorageHelper.filterAndSort(masterList, currentSort, currentLanguage));
		
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
	
	
	public void _fetchFirebaseModels() {
		// =========================================================================
		// SMART SYNC LOGIC (FOR NORMAL TTS MODELS)
		// =========================================================================
		fb.addListenerForSingleValueEvent(new ValueEventListener() {
			@Override
			public void onDataChange(@NonNull DataSnapshot snapshot) {
				if (snapshot.exists()) {
					boolean isListUpdated = false;
					java.util.ArrayList<HashMap<String, Object>> masterList = _loadMasterModels();
					
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
									for (int i = 0; i < masterList.size(); i++) {
										HashMap<String, Object> localModel = masterList.get(i);
										String localUrl = localModel.containsKey("model_url") && localModel.get("model_url") != null ? localModel.get("model_url").toString() : "";
										
										if (!onlineUrl.isEmpty() && onlineUrl.equals(localUrl)) {
											isAlreadyExists = true;
											
											// Update details if changed (Smart Sync)
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
										masterList.add(onlineModel);
										isListUpdated = true;
									}
								}
							} catch (Exception e) {
								android.util.Log.e("VoxSherpa", "Model Parse Error: " + e.getMessage());
							}
						}
					}
					
					for (int i = masterList.size() - 1; i >= 0; i--) {
						HashMap<String, Object> localModel = masterList.get(i);
						
						if (localModel.containsKey("model_url") && localModel.get("model_url") != null) {
							String localUrl = localModel.get("model_url").toString();
							
							// Retain MMS protection logic to prevent deletion of offline MMS models
							if (!onlineUrls.contains(localUrl) && !localUrl.contains("mms-tts-multilingual-models-onnx")) {
								String onnxPath = ModelStorageHelper.getString(localModel, "onnx_path");
								String tokensPath = ModelStorageHelper.getString(localModel, "tokens_path");
								String voicesPath = ModelStorageHelper.getString(localModel, "voices_bin_path");
								
								if (!onnxPath.isEmpty()) new java.io.File(onnxPath).delete();
								if (!tokensPath.isEmpty()) new java.io.File(tokensPath).delete();
								if (!voicesPath.isEmpty()) new java.io.File(voicesPath).delete();
								
								if (onnxPath.equals(sp1.getString("active_model", ""))) {
									try { com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().destroy(); } catch (Throwable ignored) {}
									try { com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().destroy(); } catch (Throwable ignored) {}
									sp1.edit().putString("active_model", "").putString("active_tokens", "").putString("active_model_name", "").apply();
								}
								
								masterList.remove(i);
								isListUpdated = true;
							}
						}
					}
					
					if (isListUpdated) {
						_saveMasterModels(masterList);
						if (isAdded()) {
							_applyFilterAndSort();
						}
					}
				}
			}
			
			@Override
			public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
				com.google.android.material.snackbar.Snackbar.make(binding.getRoot(), "Failed to fetch models from server.", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
				android.util.Log.e("VoxSherpa", "Firebase Sync Cancelled: " + error.getMessage());
			}
		});
		
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
				_saveMasterModels(masterList);
				if (binding != null && isAdded()) {
					_applyFilterAndSort();
				}
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
				_saveMasterModels(masterList);
				if (binding != null && isAdded()) {
					_applyFilterAndSort();
				}
			}
		}
		
	}
	
	private java.util.ArrayList<java.util.HashMap<String, Object>> _loadMasterModels() {
		String savedData = sp1.getString("models_data", "[]");
		java.util.ArrayList<java.util.HashMap<String, Object>> list = GSON.fromJson(
		savedData,
		new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType()
		);
		return list != null ? list : new ArrayList<>();
	}
	
	private void _saveMasterModels(java.util.List<java.util.HashMap<String, Object>> masterList) {
		java.util.List<java.util.HashMap<String, Object>> clean = ModelStorageHelper.sanitizeForPersistence(masterList);
		sp1.edit().putString("models_data", GSON.toJson(clean)).apply();
	}
	
	private long[] _queryDownloadInfo(android.app.DownloadManager dm, long downloadId) {
		long[] info = new long[]{ModelStorageHelper.STATUS_FAILED, 0, -1, -1};
		if (dm == null || downloadId == -1) return info;
		android.database.Cursor cursor = null;
		try {
			android.app.DownloadManager.Query q = new android.app.DownloadManager.Query();
			q.setFilterById(downloadId);
			cursor = dm.query(q);
			if (cursor != null && cursor.moveToFirst()) {
				int statusIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS);
				int dlIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
				int totalIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
				int reasonIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_REASON);
				if (statusIdx >= 0) info[0] = cursor.getInt(statusIdx);
				if (dlIdx >= 0) info[1] = cursor.getLong(dlIdx);
				if (totalIdx >= 0) info[2] = cursor.getLong(totalIdx);
				if (reasonIdx >= 0) info[3] = cursor.getInt(reasonIdx);
			}
		} catch (Exception ignored) {
		} finally {
			if (cursor != null) {
				try { cursor.close(); } catch (Exception ignored) {}
			}
		}
		return info;
	}
	
	private void _cancelItemDownloads(Context appContext, java.util.HashMap<String, Object> item) {
		if (appContext == null || item == null) return;
		try {
			android.app.DownloadManager dm = (android.app.DownloadManager) appContext.getSystemService(Context.DOWNLOAD_SERVICE);
			if (dm == null) return;
			long onnxId = ModelStorageHelper.parseLongSafe(item.get("onnx_download_id"), -1L);
			long tokensId = ModelStorageHelper.parseLongSafe(item.get("tokens_download_id"), -1L);
			long voicesId = ModelStorageHelper.parseLongSafe(item.get("voices_bin_download_id"), -1L);
			if (onnxId != -1L) dm.remove(onnxId);
			if (tokensId != -1L) dm.remove(tokensId);
			if (voicesId != -1L) dm.remove(voicesId);
		} catch (Exception ignored) {}
	}
	
}
