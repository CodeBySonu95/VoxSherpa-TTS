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

public class TtssettingsActivity extends AppCompatActivity {
	
	private TtssettingsBinding binding;
	java.util.ArrayList<java.util.HashMap<String, Object>> groupedLanguageList;
	
	private SharedPreferences sp1;
	
	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		binding = TtssettingsBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());
		initialize(_savedInstanceState);
		FirebaseApp.initializeApp(this);
		initializeLogic();
	}
	
	private void initialize(Bundle _savedInstanceState) {
		sp1 = getSharedPreferences("sp1", Activity.MODE_PRIVATE);
		
		binding.imgMore.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				android.widget.PopupMenu popup = new android.widget.PopupMenu(_view.getContext(), _view);
				
				popup.getMenu().add(0, 1, 0, "System TTS Settings");
				
				popup.setOnMenuItemClickListener(item -> {
					if (item.getItemId() == 1) {
						try {
							
							android.content.Intent intent =
							new android.content.Intent("com.android.settings.TTS_SETTINGS");
							
							intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
							
							_view.getContext().startActivity(intent);
							
						} catch (Exception e) {
							
							com.google.android.material.snackbar.Snackbar.make(
							_view,
							"Unable to open System TTS Settings automatically.",
							com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
							).show();
						}
						
						return true;
					}
					
					return false;
				});
				
				popup.show();
			}
		});
	}
	
	private void initializeLogic() {
		
		androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
		androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new androidx.core.view.OnApplyWindowInsetsListener() {
			@Override
			public androidx.core.view.WindowInsetsCompat onApplyWindowInsets(android.view.View v, androidx.core.view.WindowInsetsCompat insets) {
				androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
				v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
				
				return insets;
			}
		});
		
		com.CodeBySonu.VoxSherpa.system.TtsDefaultHelper.syncDefaultVoices(TtssettingsActivity.this);
		
		if (groupedLanguageList == null) {
			groupedLanguageList = new java.util.ArrayList<>();
		}
		groupedLanguageList.clear();
		
		String allData = sp1.getString("models_data", "[]");
		java.util.ArrayList<java.util.HashMap<String, Object>> downloadedModels = new com.google.gson.Gson().fromJson(allData, new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType());
		
		// Pre-fetch Kokoro paths to avoid repeated lookups
		String globalKokoroOnnx = "";
		String globalKokoroTokens = "";
		String globalKokoroVoices = "";
		
		if (downloadedModels != null) {
			boolean isKokoroDownloaded = false;
			java.util.ArrayList<java.util.HashMap<String, Object>> vitsModels = new java.util.ArrayList<>();
			
			for (java.util.HashMap<String, Object> m : downloadedModels) {
				String onnxPath = m.containsKey("onnx_path") && m.get("onnx_path") != null ? m.get("onnx_path").toString() : "";
				if (!onnxPath.isEmpty()) {
					boolean isKokoroType = m.containsKey("type") && m.get("type").toString().contains("Kokoro");
					if (isKokoroType) {
						isKokoroDownloaded = true;
						globalKokoroOnnx = onnxPath;
						globalKokoroTokens = m.containsKey("tokens_path") && m.get("tokens_path") != null ? m.get("tokens_path").toString() : "";
						globalKokoroVoices = m.containsKey("voices_bin_path") && m.get("voices_bin_path") != null ? m.get("voices_bin_path").toString() : "";
					} else {
						vitsModels.add(m);
					}
				}
			}
			
			if (isKokoroDownloaded) {
				java.util.List<String> kokoroLangs = com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.getAvailableLanguages();
				java.util.List<com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.VoiceItem> allKVoices = com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.getAllVoices();
				
				for (String lang : kokoroLangs) {
					java.util.HashMap<String, Object> group = new java.util.HashMap<>();
					group.put("language_name", lang);
					group.put("is_expanded", "false"); 
					
					java.util.ArrayList<java.util.HashMap<String, Object>> voicesList = new java.util.ArrayList<>();
					
					for (com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.VoiceItem kv : allKVoices) {
						if (kv.language.equals(lang)) {
							java.util.HashMap<String, Object> vMap = new java.util.HashMap<>();
							vMap.put("voice_id", kv.voiceKey);
							vMap.put("display_name", kv.displayName);
							vMap.put("subtitle", kv.gender + " • Studio Quality");
							vMap.put("is_kokoro", "true");
							
							// Add paths to map for SharedPreferences sp5 storage
							vMap.put("onnx_path", globalKokoroOnnx);
							vMap.put("tokens_path", globalKokoroTokens);
							vMap.put("voices_bin_path", globalKokoroVoices);
							vMap.put("speaker_id", String.valueOf(kv.speakerId));
							vMap.put("model_type", "kokoro");
							voicesList.add(vMap);
						}
					}
					group.put("voices", voicesList);
					groupedLanguageList.add(group);
				}
			}
			
			java.util.HashMap<String, java.util.ArrayList<java.util.HashMap<String, Object>>> vitsGroupedByLang = new java.util.HashMap<>();
			for (java.util.HashMap<String, Object> vits : vitsModels) {
				String lang = vits.containsKey("language") ? vits.get("language").toString() : "Unknown Language";
				if (!vitsGroupedByLang.containsKey(lang)) {
					vitsGroupedByLang.put(lang, new java.util.ArrayList<>());
				}
				vitsGroupedByLang.get(lang).add(vits);
			}
			
			for (String lang : vitsGroupedByLang.keySet()) {
				java.util.HashMap<String, Object> existingGroup = null;
				for (java.util.HashMap<String, Object> group : groupedLanguageList) {
					if (group.containsKey("language_name") && group.get("language_name").toString().equals(lang)) {
						existingGroup = group;
						break;
					}
				}
				
				java.util.ArrayList<java.util.HashMap<String, Object>> mappedVitsVoices = new java.util.ArrayList<>();
				for (java.util.HashMap<String, Object> vits : vitsGroupedByLang.get(lang)) {
					java.util.HashMap<String, Object> vMap = new java.util.HashMap<>();
					
					String op = vits.containsKey("onnx_path") && vits.get("onnx_path") != null ? vits.get("onnx_path").toString() : "";
					
					// Extract correct token path avoiding strict key dependency
					String tk = "";
					if (vits.containsKey("tokens_path") && vits.get("tokens_path") != null) {
						tk = vits.get("tokens_path").toString();
					} else if (vits.containsKey("lexicon_path") && vits.get("lexicon_path") != null) {
						tk = vits.get("lexicon_path").toString();
					} else if (vits.containsKey("config_path") && vits.get("config_path") != null) {
						tk = vits.get("config_path").toString();
					}
					
					// Use onnx_path directly as the unique identifier for UI selection state
					String vId = op;
					
					String vName = vits.containsKey("name") && vits.get("name") != null ? vits.get("name").toString() : "Standard Voice";
					String gender = vits.containsKey("gender") ? vits.get("gender").toString() : "";
					String quality = vits.containsKey("quality") ? vits.get("quality").toString() : "Standard";
					String size = vits.containsKey("size") ? vits.get("size").toString() : "";
					String sampleUrl = vits.containsKey("semple") && vits.get("semple") != null ? vits.get("semple").toString() : "";
					
					vMap.put("voice_id", vId);
					vMap.put("display_name", vName);
					vMap.put("subtitle", gender + " • " + quality + " • " + size);
					vMap.put("is_kokoro", "false");
					vMap.put("sample_url", sampleUrl);
					
					// Add paths to map for SharedPreferences sp5 storage
					vMap.put("onnx_path", op);
					vMap.put("tokens_path", tk);
					vMap.put("voices_bin_path", "");
					vMap.put("speaker_id", "-1");
					vMap.put("model_type", "vits");
					
					mappedVitsVoices.add(vMap);
				}
				
				if (existingGroup != null) {
					java.util.ArrayList<java.util.HashMap<String, Object>> existingVoices = (java.util.ArrayList<java.util.HashMap<String, Object>>) existingGroup.get("voices");
					if (existingVoices != null) {
						existingVoices.addAll(mappedVitsVoices);
					}
				} else {
					java.util.HashMap<String, Object> newGroup = new java.util.HashMap<>();
					newGroup.put("language_name", lang);
					newGroup.put("is_expanded", "false");
					newGroup.put("voices", mappedVitsVoices);
					groupedLanguageList.add(newGroup);
				}
			}
		}
		
		binding.recyclerviewVoices.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(TtssettingsActivity.this));
		androidx.recyclerview.widget.RecyclerView.ItemAnimator animator = binding.recyclerviewVoices.getItemAnimator();
		if (animator instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
			((androidx.recyclerview.widget.SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
		}
		
		binding.recyclerviewVoices.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
			
			@NonNull
			@Override
			public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
				android.view.View itemView = getLayoutInflater().inflate(R.layout.item_language_group, parent, false);
				return new androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {};
			}
			
			@Override
			public void onBindViewHolder(@NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
				int pos = holder.getAdapterPosition();
				if (pos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return;
				
				java.util.HashMap<String, Object> group = groupedLanguageList.get(pos);
				android.view.View v = holder.itemView;
				android.content.Context context = v.getContext();
				
				String langName = group.containsKey("language_name") ? group.get("language_name").toString() : "Unknown";
				boolean isExpanded = group.containsKey("is_expanded") && group.get("is_expanded").toString().equals("true");
				java.util.ArrayList<java.util.HashMap<String, Object>> voices = (java.util.ArrayList<java.util.HashMap<String, Object>>) group.get("voices");
				
				android.widget.TextView txtLangName = v.findViewById(R.id.txt_language_name);
				android.widget.ImageView imgExpandArrow = v.findViewById(R.id.img_expand_arrow);
				com.google.android.material.card.MaterialCardView mainGroupCard = v.findViewById(R.id.main_group_card);
				android.widget.RelativeLayout headerLayout = v.findViewById(R.id.header_layout);
				android.widget.LinearLayout expandableContainer = v.findViewById(R.id.expandable_voice_container);
				
				txtLangName.setText(langName);
				
				String defaultPrefKey = "default_voice_" + langName;
				String currentlySelectedVoiceId = sp1.getString(defaultPrefKey, "");
				
				if (isExpanded) {
					imgExpandArrow.setRotation(180f);
					imgExpandArrow.setColorFilter(android.graphics.Color.parseColor("#A67CFF")); 
					mainGroupCard.setStrokeColor(android.graphics.Color.parseColor("#A67CFF")); 
					txtLangName.setTextColor(android.graphics.Color.parseColor("#A67CFF"));
					expandableContainer.setVisibility(android.view.View.VISIBLE);
					
					expandableContainer.removeAllViews();
					
					if (voices != null) {
						for (int i = 0; i < voices.size(); i++) {
							java.util.HashMap<String, Object> voiceMap = voices.get(i);
							String voiceId = voiceMap.containsKey("voice_id") ? voiceMap.get("voice_id").toString() : "";
							String dispName = voiceMap.containsKey("display_name") ? voiceMap.get("display_name").toString() : "Voice";
							String subTitle = voiceMap.containsKey("subtitle") ? voiceMap.get("subtitle").toString() : "";
							String sampleUrl = voiceMap.containsKey("sample_url") ? voiceMap.get("sample_url").toString() : "";
							
							boolean isPlaying = voiceMap.containsKey("is_playing") && voiceMap.get("is_playing").equals("true");
							boolean isBuffering = voiceMap.containsKey("is_buffering") && voiceMap.get("is_buffering").equals("true");
							boolean isKokoroVoice = voiceMap.containsKey("is_kokoro") && voiceMap.get("is_kokoro").equals("true");
							
							// Save initial default model to SharedPreferences sp5
							if (currentlySelectedVoiceId.isEmpty() && i == 0) {
								sp1.edit().putString(defaultPrefKey, voiceId).apply();
								currentlySelectedVoiceId = voiceId;
								
								try {
									org.json.JSONObject sysJson = new org.json.JSONObject();
									sysJson.put("model_type", voiceMap.get("model_type"));
									sysJson.put("onnx_path", voiceMap.get("onnx_path"));
									sysJson.put("tokens_path", voiceMap.get("tokens_path"));
									sysJson.put("voices_bin_path", voiceMap.get("voices_bin_path"));
									sysJson.put("speaker_id", voiceMap.get("speaker_id"));
									context.getSharedPreferences("sp5", android.content.Context.MODE_PRIVATE)
									.edit().putString("sys_tts_" + langName, sysJson.toString()).apply();
								} catch(Exception e){}
							}
							
							android.view.View rowView = getLayoutInflater().inflate(R.layout.item_voice_row, expandableContainer, false);
							
							android.widget.TextView vName = rowView.findViewById(R.id.txt_voice_name);
							android.widget.TextView vDesc = rowView.findViewById(R.id.txt_voice_desc);
							android.widget.ImageView imgRadio = rowView.findViewById(R.id.img_radio_select);
							com.google.android.material.card.MaterialCardView btnPlay = rowView.findViewById(R.id.btn_play_sample);
							android.widget.ImageView imgPlayIcon = rowView.findViewById(R.id.img_play_icon);
							android.widget.ProgressBar progressBuffering = rowView.findViewById(R.id.progress_buffering);
							
							vName.setText(dispName);
							vDesc.setText(subTitle);
							
							if (isKokoroVoice) {
								btnPlay.setVisibility(android.view.View.GONE);
							} else {
								btnPlay.setVisibility(android.view.View.VISIBLE);
							}
							
							if (voiceId.equals(currentlySelectedVoiceId)) {
								imgRadio.setImageResource(R.drawable.icon_radio_button_checked); 
								imgRadio.setColorFilter(android.graphics.Color.parseColor("#A67CFF")); 
								vName.setTextColor(android.graphics.Color.parseColor("#A67CFF")); 
								rowView.setBackgroundColor(android.graphics.Color.parseColor("#25253A")); 
								
								btnPlay.setCardBackgroundColor(android.graphics.Color.parseColor("#A67CFF")); 
								imgPlayIcon.setColorFilter(android.graphics.Color.parseColor("#131B2D")); 
							} else {
								imgRadio.setImageResource(R.drawable.icon_radio_button_unchecked);
								imgRadio.setColorFilter(android.graphics.Color.parseColor("#A0AEC0")); 
								vName.setTextColor(android.graphics.Color.parseColor("#FFFFFF")); 
								rowView.setBackgroundColor(android.graphics.Color.TRANSPARENT); 
								
								btnPlay.setCardBackgroundColor(android.graphics.Color.parseColor("#2D2D3F")); 
								imgPlayIcon.setColorFilter(android.graphics.Color.parseColor("#A0AEC0")); 
							}
							
							if (isBuffering) {
								imgPlayIcon.setVisibility(android.view.View.GONE);
								progressBuffering.setVisibility(android.view.View.VISIBLE);
							} else {
								progressBuffering.setVisibility(android.view.View.GONE);
								imgPlayIcon.setVisibility(android.view.View.VISIBLE);
								if (isPlaying) {
									imgPlayIcon.setImageResource(R.drawable.icon_pause_circle);
								} else {
									imgPlayIcon.setImageResource(R.drawable.icon_play_circle);
								}
							}
							
							rowView.setOnClickListener(view -> {
								// Save selected model paths directly to SharedPreferences sp5 on click
								sp1.edit().putString(defaultPrefKey, voiceId).apply();
								try {
									org.json.JSONObject sysJson = new org.json.JSONObject();
									sysJson.put("model_type", voiceMap.get("model_type"));
									sysJson.put("onnx_path", voiceMap.get("onnx_path"));
									sysJson.put("tokens_path", voiceMap.get("tokens_path"));
									sysJson.put("voices_bin_path", voiceMap.get("voices_bin_path"));
									sysJson.put("speaker_id", voiceMap.get("speaker_id"));
									context.getSharedPreferences("sp5", android.content.Context.MODE_PRIVATE)
									.edit().putString("sys_tts_" + langName, sysJson.toString()).apply();
								} catch(Exception e){}
								
								if (binding.recyclerviewVoices.getAdapter() != null) {
									binding.recyclerviewVoices.getAdapter().notifyItemChanged(pos);
								}
							});
							
							btnPlay.setOnClickListener(view -> {
								if (sampleUrl.isEmpty()) {
									com.google.android.material.snackbar.Snackbar.make(v, "Sample link not available.", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
									return;
								}
								
								if (isPlaying) {
									com.CodeBySonu.VoxSherpa.AudioSampleHelper.getInstance().stopAudio();
									voiceMap.put("is_playing", "false");
									if (binding.recyclerviewVoices.getAdapter() != null) {
										binding.recyclerviewVoices.getAdapter().notifyItemChanged(pos);
									}
									return;
								}
								
								for (java.util.HashMap<String, Object> g : groupedLanguageList) {
									java.util.ArrayList<java.util.HashMap<String, Object>> gVoices = (java.util.ArrayList<java.util.HashMap<String, Object>>) g.get("voices");
									if (gVoices != null) {
										for (java.util.HashMap<String, Object> vMap : gVoices) {
											vMap.put("is_playing", "false");
											vMap.put("is_buffering", "false");
										}
									}
								}
								
								voiceMap.put("is_buffering", "true");
								if (binding.recyclerviewVoices.getAdapter() != null) {
									binding.recyclerviewVoices.getAdapter().notifyItemChanged(pos);
								}
								
								com.CodeBySonu.VoxSherpa.AudioSampleHelper.getInstance().playSample(context, sampleUrl, new com.CodeBySonu.VoxSherpa.AudioSampleHelper.SamplePlayListener() {
									@Override
									public void onPlayStarted() {
										if (!TtssettingsActivity.this.isFinishing()) {
											TtssettingsActivity.this.runOnUiThread(() -> {
												voiceMap.put("is_buffering", "false");
												voiceMap.put("is_playing", "true");
												if (binding.recyclerviewVoices.getAdapter() != null) {
													binding.recyclerviewVoices.getAdapter().notifyItemChanged(pos);
												}
											});
										}
									}
									
									@Override
									public void onPlayStopped() {
										if (!TtssettingsActivity.this.isFinishing()) {
											TtssettingsActivity.this.runOnUiThread(() -> {
												voiceMap.put("is_buffering", "false");
												voiceMap.put("is_playing", "false");
												if (binding.recyclerviewVoices.getAdapter() != null) {
													binding.recyclerviewVoices.getAdapter().notifyItemChanged(pos);
												}
											});
										}
									}
									
									@Override
									public void onError(String error) {
										if (!TtssettingsActivity.this.isFinishing()) {
											TtssettingsActivity.this.runOnUiThread(() -> {
												voiceMap.put("is_buffering", "false");
												voiceMap.put("is_playing", "false");
												if (binding.recyclerviewVoices.getAdapter() != null) {
													binding.recyclerviewVoices.getAdapter().notifyItemChanged(pos);
												}
												com.google.android.material.snackbar.Snackbar.make(v, "Error: " + error, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
											});
										}
									}
								});
							});
							
							expandableContainer.addView(rowView);
						}
					}
				} else {
					imgExpandArrow.setRotation(0f);
					imgExpandArrow.setColorFilter(android.graphics.Color.parseColor("#A0AEC0"));
					mainGroupCard.setStrokeColor(android.graphics.Color.parseColor("#2D2D3F"));
					txtLangName.setTextColor(android.graphics.Color.parseColor("#FFFFFF"));
					expandableContainer.setVisibility(android.view.View.GONE);
					expandableContainer.removeAllViews();
				}
				
				headerLayout.setOnClickListener(view -> {
					for (java.util.HashMap<String, Object> g : groupedLanguageList) {
						if (g != group) g.put("is_expanded", "false");
					}
					
					group.put("is_expanded", isExpanded ? "false" : "true");
					
					if (binding.recyclerviewVoices.getAdapter() != null) {
						binding.recyclerviewVoices.getAdapter().notifyDataSetChanged();
					}
				});
			}
			
			@Override
			public int getItemCount() {
				return groupedLanguageList.size();
			}
		});
		
	}
	
}