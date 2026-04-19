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
			
				BottomSheetDialog sortDialog = new BottomSheetDialog(getContext());
				View dialogView = getActivity().getLayoutInflater().inflate(R.layout.sort_bottom_dialog, null);
				
				GradientDrawable gd = new GradientDrawable();
				gd.setColor(Color.parseColor("#131B2D"));
				float radius = 24f * getResources().getDisplayMetrics().density;
				gd.setCornerRadii(new float[]{
					radius, radius, 
					radius, radius, 
					0f, 0f, 
					0f, 0f
				});
				dialogView.setBackground(gd);
				sortDialog.setContentView(dialogView);
				
				View parent = (View) dialogView.getParent();
				if (parent != null) parent.setBackgroundColor(Color.TRANSPARENT);
				
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
				
				if(currentSort.equals("download")) rbDownload.setChecked(true);
				else if(currentSort.equals("installed")) rbInstalled.setChecked(true);
				else if(currentSort.equals("newest")) rbNewest.setChecked(true);
				else if(currentSort.equals("oldest")) rbOldest.setChecked(true);
				else rbAll.setChecked(true);
				
				View.OnClickListener clickListener = v -> {
					String selected = "all_models";
					if(v.getId() == R.id.layout_download) selected = "download";
					else if(v.getId() == R.id.layout_installed) selected = "installed";
					else if(v.getId() == R.id.layout_newest) selected = "newest";
					else if(v.getId() == R.id.layout_oldest) selected = "oldest";
					
					// Save preference aur dialog dismiss
					sp1.edit().putString("sort_preference", selected).apply();
					sortDialog.dismiss();
					
					// --- DATA FILTERING LOGIC ---
					String savedData = sp1.getString("models_data", "[]");
					ArrayList<HashMap<String, Object>> masterList = new Gson().fromJson(savedData, new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());
					
					if (masterList == null) masterList = new ArrayList<>();
					
					modelList.clear(); 
					
					for (HashMap<String, Object> item : masterList) {
						String onnxPath = item.containsKey("onnx_path") && item.get("onnx_path") != null ? item.get("onnx_path").toString() : "";
						boolean isInstalled = !onnxPath.isEmpty();
						
						if (selected.equals("download")) {
							if (!isInstalled) modelList.add(item);
						} else if (selected.equals("installed")) {
							if (isInstalled) modelList.add(item);
						} else {
							modelList.add(item);
						}
					}
					
					if (selected.equals("newest")) {
						Collections.reverse(modelList);
					}
					
					if (binding.recyclerviewModels.getAdapter() != null) {
						binding.recyclerviewModels.getAdapter().notifyDataSetChanged();
					}
					_updateEmptyState();
					
					// --- UI TEXT UPDATE LOGIC ---
					if (selected.equals("download")) {
						binding.sortTv.setText("Download");
					} else if (selected.equals("installed")) {
						binding.sortTv.setText("Installed");
					} else if (selected.equals("newest")) {
						binding.sortTv.setText("Newest First");
					} else if (selected.equals("oldest")) {
						binding.sortTv.setText("Oldest First");
					} else {
						binding.sortTv.setText("All Models");
					}
				};
				
				// Listeners Assign
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
	
	private void initializeLogic() {
		String initialSort = sp1.getString("sort_preference", "all_models");
		if (initialSort.equals("download")) binding.sortTv.setText("Download");
		else if (initialSort.equals("installed")) binding.sortTv.setText("Installed");
		else if (initialSort.equals("newest")) binding.sortTv.setText("Newest First");
		else if (initialSort.equals("oldest")) binding.sortTv.setText("Oldest First");
		else binding.sortTv.setText("All Models");
		
		
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
					
					// VISUAL FEEDBACK ON DIALOG
					if (dialogView != null) {
						// FIXED: Updating the correct UI elements
						com.google.android.material.card.MaterialCardView card = dialogView.findViewById(R.id.btn_select_tokens);
						android.widget.TextView tvName = dialogView.findViewById(R.id.tokens_txt_tv);
						card.setStrokeColor(android.graphics.Color.parseColor("#48BB78")); // Green Success Stroke
						tvName.setText(fileName);
						tvName.setTextColor(android.graphics.Color.parseColor("#48BB78"));
					}
				} else {
					// PROFESSIONAL SNACKBAR FOR WRONG EXTENSION
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
		
		binding.recyclerviewModels.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
			
			@NonNull
			@Override
			public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
				View itemView = getActivity().getLayoutInflater().inflate(R.layout.item_model, parent, false);
				return new RecyclerView.ViewHolder(itemView) {};
			}
			
			@Override
			public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull java.util.List<Object> payloads) {
				if (payloads.isEmpty()) {
					// Agar koi specific command nahi hai, toh normal full-draw call karo
					super.onBindViewHolder(holder, position, payloads);
				} else {
					for (Object payload : payloads) {
						if (payload.equals("PROGRESS_UPDATE")) {
							int pos = holder.getAdapterPosition();
							if (pos == RecyclerView.NO_POSITION) return;
							
							HashMap<String, Object> item = modelList.get(pos);
							
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
			public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
				View v = holder.itemView;
				Context context = v.getContext();
				
				int pos = holder.getAdapterPosition();
				if (pos == RecyclerView.NO_POSITION) return;
				
				HashMap<String, Object> item = modelList.get(pos);
				
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
				
				String currentOnnx = item.containsKey("onnx_path") ? item.get("onnx_path").toString() : "";
				boolean isDownloaded = !currentOnnx.isEmpty();
				boolean isDownloading = item.containsKey("is_downloading") && item.get("is_downloading").toString().equals("true");
				int progress = item.containsKey("download_progress") ? Integer.parseInt(item.get("download_progress").toString()) : 0;
				
				String activeOnnx = sp1.getString("active_model", "");
				
				ImageView selectIv = v.findViewById(R.id.select_iv);
				TextView useRemoveTv = v.findViewById(R.id.use_remove_tv);
				MaterialCardView btnUseVoice = v.findViewById(R.id.btn_use_voice);
				ImageView imgPreview = v.findViewById(R.id.img_preview_status);
				ProgressBar progressBar = v.findViewById(R.id.progress_download);
				MaterialCardView btnDelete = v.findViewById(R.id.btn_delete);
				TextView txtModelSub = v.findViewById(R.id.txt_model_sub);
				
				if (isDownloaded) {
					imgPreview.setImageResource(R.drawable.icon_mic);
					progressBar.setVisibility(View.GONE);
					btnDelete.setVisibility(View.VISIBLE);
					txtModelSub.setText(lang + " • " + gender + " • " + size);
					
					if (currentOnnx.equals(activeOnnx)) {
						selectIv.setVisibility(View.VISIBLE);
						useRemoveTv.setText("Remove");
						btnUseVoice.setCardBackgroundColor(Color.parseColor("#FF4B4B"));
					} else {
						selectIv.setVisibility(View.GONE);
						useRemoveTv.setText("Use Voice");
						btnUseVoice.setCardBackgroundColor(Color.parseColor("#1D61FF"));
					}
				} else if (isDownloading) {
					imgPreview.setImageResource(R.drawable.ic_download);
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
					btnUseVoice.setCardBackgroundColor(Color.parseColor("#718096"));
				} else {
					imgPreview.setImageResource(R.drawable.ic_download);
					progressBar.setVisibility(View.GONE);
					btnDelete.setVisibility(View.GONE);
					txtModelSub.setText(lang + " • " + gender + " • " + size);
					
					selectIv.setVisibility(View.GONE);
					useRemoveTv.setText("Download");
					btnUseVoice.setCardBackgroundColor(Color.parseColor("#1D61FF"));
				}
				
				final String capturedOnnx = currentOnnx;
				final String capturedTokens = item.containsKey("tokens_path") && item.get("tokens_path") != null ? item.get("tokens_path").toString() : "";
				final String capturedVoicesBin = item.containsKey("voices_bin_path") && item.get("voices_bin_path") != null ? item.get("voices_bin_path").toString() : "";
				final String capturedModelType = isKokoro ? "kokoro" : "vits";
				
				// THE FIX: Data-Driven Background Poller (Decoupled from Holder)
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
									HashMap<String, Object> m = modelList.get(i);
									if (m.containsKey("onnx_download_id") && m.get("onnx_download_id").toString().equals(String.valueOf(onnxId))) {
										targetPos = i;
										break;
									}
								}
								
								if (targetPos == -1) return; // Item deleted or removed
								
								HashMap<String, Object> currentItem = modelList.get(targetPos);
								
								if (!currentItem.containsKey("is_downloading") || currentItem.get("is_downloading").toString().equals("false")) {
									currentItem.remove("handler_running");
									return; // Stopped or Cancelled
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
											
											sp1.edit().putString("models_data", new Gson().toJson(modelList)).apply();
											
											final int finalTargetPos = targetPos;
											if (getActivity() != null) {
												getActivity().runOnUiThread(() -> {
													if (isAdded()) notifyItemChanged(finalTargetPos); // Full update for Success UI
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
													notifyItemChanged(finalTargetPos); // Full update for Failed UI
													View root = getView();
													if (root != null) {
														Snackbar.make(root, "Download Failed! Code: " + reason, Snackbar.LENGTH_LONG)
														.setBackgroundTint(Color.parseColor("#FF4B4B")).setTextColor(Color.WHITE).show();
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
													if (isAdded()) notifyItemChanged(finalTargetPos, "PROGRESS_UPDATE");
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
					if (clickedPos == RecyclerView.NO_POSITION) return;
					
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
						notifyItemChanged(clickedPos);
						
					} else if (!isDownloaded) {
						String onnxUrl = item.containsKey("model_url") ? item.get("model_url").toString() : "";
						String tokensUrl = item.containsKey("tokens_url") ? item.get("tokens_url").toString() : "";
						String voicesBinUrl = item.containsKey("voices_bin_url") ? item.get("voices_bin_url").toString() : "";
						
						if (onnxUrl.isEmpty() || tokensUrl.isEmpty() || (isKokoro && voicesBinUrl.isEmpty())) {
							Snackbar.make(v, "Unable to download. Required links are missing.", Snackbar.LENGTH_SHORT)
							.setBackgroundTint(Color.parseColor("#FF4B4B")).setTextColor(Color.WHITE).show();
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
							notifyItemChanged(clickedPos); 
							
						} catch (IllegalArgumentException e) {
							Snackbar.make(v, "Unable to initiate download. Link is invalid.", Snackbar.LENGTH_LONG)
							.setBackgroundTint(Color.parseColor("#FF4B4B")).setTextColor(Color.WHITE).show();
						} catch (Exception e) {
							Snackbar.make(v, "An unexpected error occurred.", Snackbar.LENGTH_LONG)
							.setBackgroundTint(Color.parseColor("#FF4B4B")).setTextColor(Color.WHITE).show();
						}
						
					} else {
						String currentActive = sp1.getString("active_model", "");
						
						if (capturedOnnx.equals(currentActive)) {
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
							notifyDataSetChanged();
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
				
				// DELETE BUTTON LOGIC
				v.findViewById(R.id.btn_delete).setOnClickListener(view -> {
					int safePos = holder.getAdapterPosition();
					if (safePos == RecyclerView.NO_POSITION) return;
					
					HashMap<String, Object> itemToDelete = modelList.get(safePos);
					
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
					ArrayList<HashMap<String, Object>> mList = new Gson().fromJson(allData, new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());
					
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
						sp1.edit().putString("models_data", new Gson().toJson(mList)).apply();
					}
					
					_applyFilterAndSort();
				});
			}
			
			private void proceedToLoadModel(
			RecyclerView.ViewHolder holder,
			MaterialCardView btnUseVoice,
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
										Snackbar.make(root, "Failed to load model.", Snackbar.LENGTH_LONG)
										.setBackgroundTint(Color.parseColor("#FF4B4B"))
										.setTextColor(Color.WHITE).show();
									}
								}
							} finally {
								isLoading = false;
								btnUseVoice.setEnabled(true);
								btnUseVoice.setAlpha(1.0f);
								notifyDataSetChanged();
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
				
				// MAIN FIX: 4 Mandatory Checks
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
				
				// Agar koi error hai, to custom Snackbar dikhao with "✕" action
				if (!errorMessage.isEmpty()) {
					Snackbar snackbar = Snackbar.make(dialogView, errorMessage, Snackbar.LENGTH_INDEFINITE)
					.setBackgroundTint(Color.parseColor("#FF4B4B"))
					.setTextColor(Color.WHITE)
					.setActionTextColor(Color.WHITE);
					// '✕' par click karne se snackbar dismiss hoga
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
		ArrayList<HashMap<String, Object>> masterList = new Gson().fromJson(savedData, new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());
		
		if (masterList == null) masterList = new ArrayList<>();
		
		String currentSort = sp1.getString("sort_preference", "all_models");
		
		modelList.clear(); 
	
		for (HashMap<String, Object> item : masterList) {
			String onnxPath = item.containsKey("onnx_path") && item.get("onnx_path") != null ? item.get("onnx_path").toString() : "";
			boolean isInstalled = !onnxPath.isEmpty();
			
			if (currentSort.equals("download")) {
				if (!isInstalled) modelList.add(item);
			} else if (currentSort.equals("installed")) {
				if (isInstalled) modelList.add(item);
			} else {
				modelList.add(item);
			}
		}
		
		if (currentSort.equals("newest")) {
			// List ko ulta (reverse) kar do, taaki naye aage aa jayein
			Collections.reverse(modelList);
		}
		
		if (binding.recyclerviewModels.getAdapter() != null) {
			binding.recyclerviewModels.getAdapter().notifyDataSetChanged();
		}
		_updateEmptyState();
		
		if (currentSort.equals("download")) {
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
									onlineUrls.add(onlineUrl); // URL track kar liya
									
									if (!onlineModel.containsKey("is_downloading")) onlineModel.put("is_downloading", "false");
									if (!onlineModel.containsKey("download_progress")) onlineModel.put("download_progress", "0");
									
									boolean isAlreadyExists = false;
									for (int i = 0; i < modelList.size(); i++) {
										HashMap<String, Object> localModel = modelList.get(i);
										String localUrl = localModel.containsKey("model_url") ? localModel.get("model_url").toString() : "";
										
										if (!onlineUrl.isEmpty() && onlineUrl.equals(localUrl)) {
											isAlreadyExists = true;
											
											// Update details if changed
											if (onlineModel.containsKey("name")) {
												Object onlineName = onlineModel.get("name");
												Object localName = localModel.get("name");
												if (onlineName != null && !onlineName.equals(localName)) {
													localModel.put("name", onlineName);
													isListUpdated = true;
												}
											}
											if (onlineModel.containsKey("type")) {
												localModel.put("type", onlineModel.get("type"));
											}
											if (onlineModel.containsKey("voices_bin_url")) {
												localModel.put("voices_bin_url", onlineModel.get("voices_bin_url"));
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
							
							if (!onlineUrls.contains(localUrl)) {
								
								String onnxPath = localModel.containsKey("onnx_path") ? localModel.get("onnx_path").toString() : "";
								String tokensPath = localModel.containsKey("tokens_path") ? localModel.get("tokens_path").toString() : "";
								String voicesPath = localModel.containsKey("voices_bin_path") ? localModel.get("voices_bin_path").toString() : "";
								
								if (!onnxPath.isEmpty()) new java.io.File(onnxPath).delete();
								if (!tokensPath.isEmpty()) new java.io.File(tokensPath).delete();
								if (!voicesPath.isEmpty()) new java.io.File(voicesPath).delete();
								
								// Agar active model yahi tha, toh usko bhi reset kardo
								if (onnxPath.equals(sp1.getString("active_model", ""))) {
									try { com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().destroy(); } catch (Throwable ignored) {}
									try { com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().destroy(); } catch (Throwable ignored) {}
									sp1.edit().putString("active_model", "").putString("active_tokens", "").putString("active_model_name", "").apply();
								}
								
								// List se hata do
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
