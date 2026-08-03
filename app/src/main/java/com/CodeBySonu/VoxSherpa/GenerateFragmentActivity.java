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
import android.text.Editable;
import android.text.TextWatcher;
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
import com.CodeBySonu.VoxSherpa.databinding.*;
import com.google.firebase.FirebaseApp;
import com.k2fsa.sherpa.onnx.*;
import com.tom_roush.pdfbox.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.android.material.snackbar.Snackbar;
import android.media.AudioManager;
import android.media.AudioFocusRequest;
import android.os.Build;
import android.content.Context;
import com.CodeBySonu.VoxSherpa.system.VoxMediaController;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.concurrent.LinkedBlockingQueue;
import com.CodeBySonu.VoxSherpa.system.VoxMediaService;
import android.os.Process;


public class GenerateFragmentActivity extends Fragment {
	
	public final int REQ_CD_FILEPICKER = 101;
	
	private GenerateFragmentBinding binding;
	private boolean isAudioGeneratedForCurrentText = false;
	private String lastGeneratedText = "";
	private byte[] lastGeneratedPcmData = null;
	private android.media.AudioTrack audioTrack;
	private android.animation.ValueAnimator playheadAnimator;
	androidx.appcompat.widget.ListPopupWindow listPopupWindow;
	android.widget.ArrayAdapter<String> voiceAdapter;
	private int lastGeneratedSampleRate = 22050;
	private volatile boolean isCancelled = false;
	private volatile boolean isGenerating = false;
	com.CodeBySonu.VoxSherpa.GenerationParams lastParams = null;
	private volatile int currentGenerationId = 0;
	private String currentUiModelPath = "";
	private android.media.AudioTrack liveStreamTrack = null;
	private String lastSettingsState = "";
	android.media.AudioManager audioManager;
	android.media.AudioManager.OnAudioFocusChangeListener focusListener;
	android.media.AudioFocusRequest focusRequest;
	
	private SharedPreferences sp1;
	private SharedPreferences sp2;
	private SharedPreferences sp3;
	private Intent FilePicker = new Intent(Intent.ACTION_GET_CONTENT);
	private SharedPreferences spHistory;
	
	@NonNull
	@Override
	public View onCreateView(@NonNull LayoutInflater _inflater, @Nullable ViewGroup _container, @Nullable Bundle _savedInstanceState) {
		binding = GenerateFragmentBinding.inflate(_inflater, _container, false);
		initialize(_savedInstanceState, binding.getRoot());
		FirebaseApp.initializeApp(getContext());
		initializeLogic();
		return binding.getRoot();
	}
	
	private void initialize(Bundle _savedInstanceState, View _view) {
		sp1 = getContext().getSharedPreferences("sp1", Activity.MODE_PRIVATE);
		sp2 = getContext().getSharedPreferences("sp2", Activity.MODE_PRIVATE);
		sp3 = getContext().getSharedPreferences("sp3", Activity.MODE_PRIVATE);
		FilePicker.setType("*/*");
		FilePicker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
		spHistory = getContext().getSharedPreferences("spHistory", Activity.MODE_PRIVATE);
		
		binding.btnGenerate.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				String inputText = binding.etInput.getText().toString().trim();
				
				if (inputText.isEmpty()) {
					if (getView() != null) {
						com.google.android.material.snackbar.Snackbar.make(getView(), "Please enter some text first.", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
						.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B")).setTextColor(android.graphics.Color.WHITE).show();
					}
					return;
				}
				
				String currentOnnxModel = sp1.getString("active_model", "");
				String currentTokens    = sp1.getString("active_tokens", "");
				String currentModelType = sp1.getString("active_model_type", "vits");
				String currentVoicesBin = sp1.getString("active_voices_bin", "");
				
				boolean isPunctOn   = sp3.getBoolean("smart_punct", false);
				boolean isEmotionOn = sp3.getBoolean("emotion_tags", false);
				boolean isMultiSpeakerOn = sp3.getBoolean("multi_speaker_tags", false);
				float currentSpeed  = sp3.getFloat("voice_speed", 1.0f);
				float currentPitch  = sp3.getFloat("voice_pitch", 1.0f);
				float currentSilence = sp3.getFloat("silence_scale", 0.2f);
				int currentVoiceStyle = sp3.getInt("voice_style", 1); 
				int currentKokoroVoiceId = sp1.getInt("active_kokoro_speaker", 31);
				final int currentPiperVoiceId = sp1.getInt("active_piper_speaker", 0); 
				
				// Standard check for missing models or tokens
				if (currentOnnxModel.isEmpty() || currentTokens.isEmpty()) {
					if (getView() != null) {
						com.google.android.material.snackbar.Snackbar.make(getView(), "Please select a Voice Model from Models tab.", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
						.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B")).setTextColor(android.graphics.Color.WHITE).show();
					}
					return;
				}
				
				boolean isVoiceParamChanged = false;
				if (lastParams == null ||
				!currentOnnxModel.equals(lastParams.onnxModel) ||
				currentKokoroVoiceId != lastParams.kokoroVoiceId ||
				currentPiperVoiceId != sp3.getInt("last_piper_speaker", -1) || 
				currentSpeed != lastParams.speed ||
				currentPitch != lastParams.pitch ||
				isPunctOn != lastParams.punctOn ||
				isEmotionOn != lastParams.emotionOn ||
				isMultiSpeakerOn != sp3.getBoolean("last_multi_speaker_state", false) ||
				currentSilence != sp3.getFloat("last_silence_scale", -1f) ||
				currentVoiceStyle != sp3.getInt("last_voice_style", -1)) { 
					
					isVoiceParamChanged = true;
					sp3.edit()
					.putBoolean("last_multi_speaker_state", isMultiSpeakerOn)
					.putFloat("last_silence_scale", currentSilence)
					.putInt("last_voice_style", currentVoiceStyle) 
					.putInt("last_piper_speaker", currentPiperVoiceId) 
					.apply();
				}
				
				if (isGenerating) {
					if (!isCancelled) {
						new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext())
						.setTitle("Cancel Generation")
						.setMessage("Are you sure you want to cancel the voice synthesis?")
						.setPositiveButton("Yes, Cancel", (dialog, which) -> {
							_cancelGeneration();
							dialog.dismiss();
						})
						.setNegativeButton("No", (dialog, which) -> dialog.dismiss())
						.show();
					}
					return;
				}
				
				if (isAudioGeneratedForCurrentText && !isVoiceParamChanged) {
					_toggleGeneratePlayback();
				} else {
					final com.CodeBySonu.VoxSherpa.GenerationParams params = new com.CodeBySonu.VoxSherpa.GenerationParams(
					inputText, currentOnnxModel, currentTokens, currentModelType, currentVoicesBin,
					currentKokoroVoiceId, currentSpeed, currentPitch, isPunctOn, isEmotionOn
					);
					
					_forceResetToIdle();
					isGenerating = true;
					isCancelled = false;
					final int myGenId = currentGenerationId;
					
					binding.btnGenerate.setEnabled(true);
					binding.btnGenerate.setAlpha(1.0f);
					binding.imageview65.setVisibility(android.view.View.GONE);
					binding.progressGenerating.setVisibility(android.view.View.VISIBLE);
					binding.textview69.setTextColor(android.graphics.Color.parseColor("#1D61FF"));
					binding.textview92.setText("Cancel");
					binding.imageview52.setImageResource(R.drawable.ic_close);
					
					final boolean isKokoro = params.modelType.equals("kokoro");
					// NEW: Extract explicit MMS flag for loading the model safely
					final boolean isMmsModel = params.modelType.equals("mms");
					
					java.util.List<String> sentences = new java.util.ArrayList<>();
					String[] parts = params.text.split("(?<=[.!?\\n\u0964])\\s+");
					for (String part : parts) {
						if (!part.trim().isEmpty()) sentences.add(part.trim());
					}
					final int totalSentences = sentences.size();
					if (totalSentences == 0) sentences.add(params.text);
					
					binding.textview69.setText("GENERATING VOICE... 0/" + totalSentences);
					
					com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).updatePlaybackState("VoxSherpa Audio", "Generating...", com.CodeBySonu.VoxSherpa.system.VoxMediaController.STATE_GENERATING, false);
					
					new Thread(() -> {
						android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
						
						String loadResult = "";
						byte[] finalGeneratedPcm = null;
						int generatedSampleRate = 24000;
						java.io.ByteArrayOutputStream pcmStream = new java.io.ByteArrayOutputStream();
						
						float noise = 0.667f;
						float noiseW = 0.80f;
						
						if (currentVoiceStyle == 0) { 
							noise = 0.33f;
							noiseW = 0.50f;
						} else if (currentVoiceStyle == 2) { 
							noise = 0.85f;
							noiseW = 0.90f;
						}
						
						if (isKokoro) {
							com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().setActiveSpeakerId(params.kokoroVoiceId); 
							loadResult = com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().loadModel(getContext(), params.onnxModel, params.tokens, params.voicesBin);
							if ("Success".equals(loadResult)) generatedSampleRate = com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().getSampleRate();
						} else {
							com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().setActiveSpeakerId(currentPiperVoiceId); 
							com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().setNoiseScale(noise);
							com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().setNoiseScaleW(noiseW);
							
							String activeLanguage = sp1.getString("active_language", "");
							// FIX: Use the overloaded loadModel that accepts explicitly Boolean explicitIsMms
							loadResult = com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().loadModel(getContext(), params.onnxModel, params.tokens, activeLanguage, isMmsModel);
							if ("Success".equals(loadResult)) generatedSampleRate = com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().getSampleRate();
						}
						
						if (generatedSampleRate <= 0) {
							if (isKokoro) generatedSampleRate = 24000;
							else generatedSampleRate = 22050;
						}
						
						final boolean[] doHandoff = {false};
						final int[] handoffHeadPos = {0};
						final int[] totalFramesWritten = {0};
						
						if ("Success".equals(loadResult)) {
							int minBufferSize = android.media.AudioTrack.getMinBufferSize(generatedSampleRate, 
							android.media.AudioFormat.CHANNEL_OUT_MONO, 
							android.media.AudioFormat.ENCODING_PCM_16BIT);
							
							if (minBufferSize <= 0) {
								minBufferSize = generatedSampleRate * 2; 
							}
							
							liveStreamTrack = new android.media.AudioTrack.Builder()
							.setAudioAttributes(new android.media.AudioAttributes.Builder()
							.setUsage(android.media.AudioAttributes.USAGE_MEDIA)
							.setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
							.build())
							.setAudioFormat(new android.media.AudioFormat.Builder()
							.setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
							.setSampleRate(generatedSampleRate)
							.setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
							.build())
							.setBufferSizeInBytes(minBufferSize)
							.setTransferMode(android.media.AudioTrack.MODE_STREAM)
							.build();
							
							java.util.concurrent.LinkedBlockingQueue<byte[]> audioQueue = new java.util.concurrent.LinkedBlockingQueue<>();
							
							Thread playerThread = new Thread(() -> {
								android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
								try {
									if (focusRequest != null && audioManager != null) {
										audioManager.requestAudioFocus(focusRequest);
									}
									
									if (liveStreamTrack != null) liveStreamTrack.play();
									
									while (true) {
										if (isCancelled || myGenId != currentGenerationId) break;
										
										if (doHandoff[0]) break; 
										
										byte[] chunk = audioQueue.take();
										if (chunk.length == 0) break; 
										
										while (liveStreamTrack != null && liveStreamTrack.getPlayState() != android.media.AudioTrack.PLAYSTATE_PLAYING && !isCancelled) {
											Thread.sleep(50); 
										}
										
										if (liveStreamTrack != null && !isCancelled) {
											int written = liveStreamTrack.write(chunk, 0, chunk.length);
											if (written > 0) totalFramesWritten[0] += (written / 2); 
										}
									}
								} catch (InterruptedException e) {
									Thread.currentThread().interrupt();
								} catch (Exception ignored) {
								} finally {
									try {
										if (liveStreamTrack != null) {
											handoffHeadPos[0] = liveStreamTrack.getPlaybackHeadPosition();
											liveStreamTrack.stop();
											liveStreamTrack.release();
											liveStreamTrack = null;
										}
									} catch (Exception ignored) {}
									
									try {
										if (audioManager != null && focusRequest != null) {
											audioManager.abandonAudioFocusRequest(focusRequest);
										}
									} catch (Exception ignored) {}
								}
							});
							playerThread.start();
							
							int doneCount = 0;
							for (String sentence : sentences) {
								if (isCancelled || myGenId != currentGenerationId) break;
								
								if (isMultiSpeakerOn) {
									sentence = com.CodeBySonu.VoxSherpa.PiperVoiceHelper.processAndSetSpeaker(sentence, isKokoro);
								}
								
								if (sentence.isEmpty()) {
									doneCount++;
									continue;
								}
								
								byte[] chunkData = null;
								
								if (params.punctOn || params.emotionOn) {
									chunkData = com.CodeBySonu.VoxSherpa.AudioEmotionHelper.processAndGenerate(sentence, params.punctOn, params.emotionOn, params.speed, params.pitch, 1.0f, currentSilence);
								} else {
									if (isKokoro) {
										chunkData = com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().generateAudioPCM(sentence, params.speed, params.pitch);
									} else {
										chunkData = com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().generateAudioPCM(sentence, params.speed, params.pitch);
									}
								}
								
								if (isCancelled || myGenId != currentGenerationId) break;
								
								if (chunkData != null && chunkData.length > 0) {
									try {
										pcmStream.write(chunkData);
										audioQueue.put(chunkData);
									} catch (Exception ignored) {}
								}
								doneCount++;
								final int current = doneCount;
								
								if (getActivity() != null && isAdded()) {
									getActivity().runOnUiThread(() -> {
										if (!isCancelled && myGenId == currentGenerationId) {
											binding.textview69.setText("GENERATING... " + current + "/" + totalSentences);
										}
									});
								}
							}
							
							doHandoff[0] = true;
							try {
								audioQueue.put(new byte[0]);
								playerThread.join(3000); 
							} catch (Exception ignored) {}
						}
						
						finalGeneratedPcm = pcmStream.toByteArray();
						
						try {
							pcmStream.close(); 
						} catch (Exception ignored) {}
						
						final byte[] finalPcm = finalGeneratedPcm;
						final String finalLoadResult = loadResult;
						final int finalSampleRate = generatedSampleRate;
						final int finalHead = handoffHeadPos[0];
						
						if (getActivity() != null && isAdded()) {
							getActivity().runOnUiThread(() -> {
								if (myGenId != currentGenerationId) return;
								isGenerating = false;
								binding.btnGenerate.setEnabled(true);
								binding.btnGenerate.setAlpha(1.0f);
								binding.progressGenerating.setVisibility(android.view.View.GONE);
								
								if ("Success".equals(finalLoadResult) && finalPcm != null && finalPcm.length > 0) {
									lastGeneratedPcmData = finalPcm;
									lastGeneratedSampleRate = finalSampleRate;
									isAudioGeneratedForCurrentText = true;
									lastParams = params;
									
									binding.layoutIdleState.setVisibility(android.view.View.GONE);
									binding.layoutGeneratedState.setVisibility(android.view.View.VISIBLE);
									binding.textview69.setText("GENERATION COMPLETE");
									
									final int totalFrames = finalPcm.length / 2;
									final double totalSeconds = (double) totalFrames / finalSampleRate;
									final int w = binding.imgWaveform.getWidth() > 0 ? binding.imgWaveform.getWidth() : 800;
									
									android.graphics.Bitmap waveBmp = com.CodeBySonu.VoxSherpa.WaveformHelper.createWaveformBitmap(finalPcm, w, 150);
									if (waveBmp != null) binding.imgWaveform.setImageBitmap(waveBmp);
									
									try {
										if (audioTrack != null) {
											audioTrack.release();
											audioTrack = null;
										}
										
										int staticBufferSize = finalPcm.length > 0 ? finalPcm.length : 1;
										
										audioTrack = new android.media.AudioTrack.Builder()
										.setAudioAttributes(new android.media.AudioAttributes.Builder()
										.setUsage(android.media.AudioAttributes.USAGE_MEDIA)
										.setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
										.build())
										.setAudioFormat(new android.media.AudioFormat.Builder()
										.setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
										.setSampleRate(finalSampleRate)
										.setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
										.build())
										.setBufferSizeInBytes(staticBufferSize)
										.setTransferMode(android.media.AudioTrack.MODE_STATIC)
										.build();
										
										audioTrack.write(finalPcm, 0, finalPcm.length);
										
										binding.playheadLine.setVisibility(android.view.View.VISIBLE);
										
										android.content.Intent durIntent = new android.content.Intent(getContext(), com.CodeBySonu.VoxSherpa.system.VoxMediaService.class);
										durIntent.setAction("ACTION_UPDATE_STATE");
										durIntent.putExtra("title", "VoxSherpa Audio");
										durIntent.putExtra("subtitle", "Playing...");
										durIntent.putExtra("state", com.CodeBySonu.VoxSherpa.system.VoxMediaController.STATE_PLAYING);
										durIntent.putExtra("isLibraryMode", false);
										durIntent.putExtra("duration", (long)(totalSeconds * 1000));
										
										if (finalHead < (totalFrames - 5000) && finalHead > 0) {
											audioTrack.setPlaybackHeadPosition(finalHead);
											audioTrack.play();
											
											binding.imageview52.setImageResource(R.drawable.icon_pause_circle);
											binding.textview92.setText("Pause");
											
											if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
												getContext().startForegroundService(durIntent);
											} else {
												getContext().startService(durIntent);
											}
											
											float progressFrac = (float) finalHead / totalFrames;
											float startX = progressFrac * w;
											binding.playheadLine.setTranslationX(startX);
											
											long remainingMs = (long) ((1.0 - progressFrac) * totalSeconds * 1000);
											
											if (playheadAnimator != null) playheadAnimator.cancel();
											playheadAnimator = android.animation.ValueAnimator.ofFloat(startX, (float) w);
											playheadAnimator.setDuration(remainingMs);
											playheadAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
											playheadAnimator.addUpdateListener(anim -> {
												float tx = (float) anim.getAnimatedValue();
												binding.playheadLine.setTranslationX(tx);
												double elapsed = (tx / w) * totalSeconds;
												binding.tvDuration.setText(String.format(java.util.Locale.US, "%d:%02d / %d:%02d", (int)(elapsed/60), (int)(elapsed%60), (int)(totalSeconds/60), (int)(totalSeconds%60)));
											});
											
											playheadAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
												@Override
												public void onAnimationEnd(android.animation.Animator animation) {
													if (binding.playheadLine.getTranslationX() >= w - 10) {
														binding.imageview52.setImageResource(R.drawable.icon_play_circle);
														binding.textview92.setText("Play");
														binding.playheadLine.setTranslationX(0f);
														binding.tvDuration.setText(String.format(java.util.Locale.US, "0:00 / %d:%02d", (int)(totalSeconds/60), (int)(totalSeconds%60)));
														
														try {
															if (audioTrack != null) {
																audioTrack.pause();
																audioTrack.flush();
																audioTrack.stop();
																audioTrack.reloadStaticData();
																audioTrack.setPlaybackHeadPosition(0);
															}
														} catch (Exception ignored) {}
														
														com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).hideNotification();
													}
												}
											});
											
											playheadAnimator.start();
										} else {
											audioTrack.setPlaybackHeadPosition(totalFrames);
											binding.imageview52.setImageResource(R.drawable.icon_play_circle);
											binding.textview92.setText("Play");
											binding.playheadLine.setTranslationX(0f);
											binding.tvDuration.setText(String.format(java.util.Locale.US, "0:00 / %d:%02d", (int)(totalSeconds/60), (int)(totalSeconds%60)));
											com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).hideNotification();
										}
										
										audioTrack.setNotificationMarkerPosition(totalFrames);
										audioTrack.setPlaybackPositionUpdateListener(new android.media.AudioTrack.OnPlaybackPositionUpdateListener() {
											@Override
											public void onMarkerReached(android.media.AudioTrack track) {
												if (getActivity() != null && isAdded()) {
													getActivity().runOnUiThread(() -> {
														binding.imageview52.setImageResource(R.drawable.icon_play_circle);
														binding.textview92.setText("Play");
														binding.playheadLine.setTranslationX(0f);
														if (playheadAnimator != null) playheadAnimator.cancel();
														
														try {
															track.pause();
															track.flush();
															track.stop();
															track.reloadStaticData();
															track.setPlaybackHeadPosition(0);
														} catch (Exception ignored) {}
														
														com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).hideNotification();
													});
												}
											}
											@Override public void onPeriodicNotification(android.media.AudioTrack track) {}
										});
										
									} catch (Exception ignored) {}
								} else {
									binding.progressGenerating.setVisibility(android.view.View.GONE);
									binding.imageview65.setVisibility(android.view.View.VISIBLE);
									binding.textview69.setText("SYNTHESIS FAILED");
									binding.textview69.setTextColor(android.graphics.Color.parseColor("#FF4B4B"));
									binding.textview92.setText("Generate");
									binding.imageview52.setImageResource(R.drawable.icon_play_circle);
									
									com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).hideNotification();
									
									if (getView() != null) {
										String userFriendlyMessage = (finalLoadResult != null && (finalLoadResult.contains("missing") || finalLoadResult.contains("empty")))
										? "Please select a valid voice model to continue."
										: "Unable to generate voice at the moment. Please try again.";
										
										com.google.android.material.snackbar.Snackbar.make(getView(), userFriendlyMessage, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
										.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B")).setTextColor(android.graphics.Color.WHITE).show();
									}
								}
							});
						}
					}).start();
				}
				
			}
		});
		
		binding.save.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				_saveAudioAction();
			}
		});
		
		binding.etInput.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence _param1, int _param2, int _param3, int _param4) {
				final String _charSeq = _param1.toString();
				
				int length = _charSeq.length();
				
				// NAYA: Hidden 25000 character limit
				if (length > 25000) {
					binding.etInput.setText(_charSeq.substring(0, 25000));
					binding.etInput.setSelection(25000);
				}
				
				// Reset state on text change
				String currentText = binding.etInput.getText().toString().trim();
				
				if (!currentText.equals(lastGeneratedText) && !isGenerating) {
					isAudioGeneratedForCurrentText = false;
					
					// UI STATE RESET
					binding.layoutIdleState.setVisibility(android.view.View.VISIBLE);
					binding.layoutGeneratedState.setVisibility(android.view.View.GONE);
					binding.progressGenerating.setVisibility(android.view.View.GONE);
					binding.imageview65.setVisibility(android.view.View.VISIBLE);
					
					binding.textview92.setText("Generate & Play");
					binding.imageview52.setImageResource(R.drawable.icon_play_circle); 
					
					binding.textview69.setText("READY TO SYNTHESIZE");
					binding.textview69.setTextColor(android.graphics.Color.parseColor("#3F4B61"));
					
					if (audioTrack != null && audioTrack.getState() != android.media.AudioTrack.STATE_UNINITIALIZED) {
						try { audioTrack.stop(); } catch (Exception e) {}
					}
					if (playheadAnimator != null) playheadAnimator.cancel();
				}
				
			}
			
			@Override
			public void beforeTextChanged(CharSequence _param1, int _param2, int _param3, int _param4) {
				
			}
			
			@Override
			public void afterTextChanged(Editable _param1) {
				
			}
		});
		
		binding.opneDropdown.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				// Use the exact model type instead of guessing from the model name
				String currentModelType = sp1.getString("active_model_type", "vits");
				boolean kokoroActive = currentModelType.equalsIgnoreCase("kokoro");
				
				String activePath = sp1.getString("active_model", "");
				int activeSpeakerCount = sp1.getInt("active_speaker_count", 1);
				
				// Return silently if no model is selected
				if (activePath.isEmpty()) {
					return;
				}
				
				// Show snackbar only if it is explicitly a Piper model and has 1 or fewer speakers
				if (!kokoroActive && activeSpeakerCount <= 1) {
					if (_view != null) {
						com.google.android.material.snackbar.Snackbar
						.make(_view, "This Voice model has only one voice.",
						com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
						.setBackgroundTint(android.graphics.Color.parseColor("#3F4B61"))
						.setTextColor(android.graphics.Color.WHITE)
						.show();
					}
					return;
				}
				
				if (listPopupWindow != null) {
					if (listPopupWindow.isShowing()) {
						listPopupWindow.dismiss();
					} else {
						if (kokoroActive) {
							int savedSpeakerId = sp1.getInt("active_kokoro_speaker", 31);
							java.util.List<com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.VoiceItem> voices =
							com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.getAllVoices();
							
							for (int i = 0; i < voices.size(); i++) {
								if (voices.get(i).speakerId == savedSpeakerId) {
									listPopupWindow.setSelection(i);
									break;
								}
							}
						} else {
							// Piper multi-speaker selection logic
							int savedPiperSpeakerId = sp1.getInt("active_piper_speaker", 0);
							listPopupWindow.setSelection(savedPiperSpeakerId);
						}
						listPopupWindow.show();
					}
				}
				
			}
		});
		
		binding.btnExport.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				_saveAudioAction();
			}
		});
	}
	
	private void initializeLogic() {
		com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).setGenerateListener(new com.CodeBySonu.VoxSherpa.system.VoxMediaController.MediaCommandListener() {
			
			android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
			
			@Override
			public void onPlay() {
				mainHandler.post(() -> { try { _toggleGeneratePlayback(); } catch(Exception e) {} });
			}
			
			@Override
			public void onPause() {
				mainHandler.post(() -> { try { _toggleGeneratePlayback(); } catch(Exception e) {} });
			}
			
			@Override
			public void onStop() {
				mainHandler.post(() -> { try { _cancelGeneration(); } catch(Exception e) {} });
			}
			
			@Override
			public void onNext() {} 
			
			@Override
			public void onPrevious() {} 
		});
		
		// AUDIO FOCUS LOGIC
		audioManager = (android.media.AudioManager) getContext().getSystemService(android.content.Context.AUDIO_SERVICE);
		
		focusListener = focusChange -> {
			if (focusChange == android.media.AudioManager.AUDIOFOCUS_LOSS || 
			focusChange == android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
				
				if (liveStreamTrack != null && liveStreamTrack.getPlayState() == android.media.AudioTrack.PLAYSTATE_PLAYING) {
					liveStreamTrack.pause();
				}
				
				if (audioTrack != null && audioTrack.getPlayState() == android.media.AudioTrack.PLAYSTATE_PLAYING) {
					audioTrack.pause();
					if (getActivity() != null) {
						getActivity().runOnUiThread(() -> {
							binding.imageview52.setImageResource(R.drawable.icon_play_circle);
							binding.textview92.setText("Play");
							if (playheadAnimator != null) playheadAnimator.pause();
							com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).updatePlaybackState("VoxSherpa Audio", "Paused", com.CodeBySonu.VoxSherpa.system.VoxMediaController.STATE_PAUSED, false);
						});
					}
				}
			} else if (focusChange == android.media.AudioManager.AUDIOFOCUS_GAIN) {
				if (liveStreamTrack != null) liveStreamTrack.play();
			}
		};
		
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
			focusRequest = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
			.setOnAudioFocusChangeListener(focusListener)
			.build();
		}
		
		String activeModel = sp1.getString("active_model", "");
		String activeModelType = sp1.getString("active_model_type", "vits");
		String activeModelName = sp1.getString("active_model_name", "Custom Voice");
		int activeSpeakerCount = sp1.getInt("active_speaker_count", 1);
		boolean isKokoro = activeModelType.equalsIgnoreCase("kokoro");
		if (activeModel.isEmpty()) {
			binding.voiceNameTv.setText("No Model Selected");
			binding.voiceNameTv.setTextColor(android.graphics.Color.parseColor("#FF4B4B"));
			binding.opneDropdown.setVisibility(android.view.View.GONE);
		} else {
			if (isKokoro || activeSpeakerCount > 1) {
				binding.opneDropdown.setVisibility(android.view.View.VISIBLE);
			} else {
				binding.opneDropdown.setVisibility(android.view.View.GONE);
			}
			if (isKokoro) {
				try {
					int savedSpeakerId = sp1.getInt("active_kokoro_speaker", 31);
					com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().setActiveSpeakerId(savedSpeakerId);
					String voiceName = com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().getActiveVoiceName();
					binding.voiceNameTv.setText((voiceName != null && !voiceName.isEmpty()) ? voiceName : "Kokoro Voice");
				} catch (Throwable t) {
					binding.voiceNameTv.setText("Kokoro Voice");
				}
			} else {
				String piperLang = "Unknown";
				String piperGender = "Unknown";
				String modelLabel = "Piper";
				try {
					String modelsDataRaw = sp1.getString("models_data", "[]");
					if (!modelsDataRaw.equals("[]")) {
						java.util.ArrayList<java.util.HashMap<String, Object>> mList =
						new com.google.gson.Gson().fromJson(modelsDataRaw,
						new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType());
						if (mList != null) {
							for (java.util.HashMap<String, Object> model : mList) {
								String onnxPath = model.containsKey("onnx_path") && model.get("onnx_path") != null ? model.get("onnx_path").toString() : "";
								if (activeModel.equals(onnxPath)) {
									if (model.containsKey("language") && model.get("language") != null) {
										piperLang = model.get("language").toString().trim();
									}
									if (model.containsKey("gender") && model.get("gender") != null) {
										piperGender = model.get("gender").toString().trim();
									}
									if (model.containsKey("type") && model.get("type").toString().contains("MMS")) {
										modelLabel = "MMS";
									}
									break;
								}
							}
						}
					}
				} catch (Exception e) {}
				if (!piperLang.equals("Unknown") && piperLang.length() > 0) {
					piperLang = piperLang.substring(0, 1).toUpperCase() + piperLang.substring(1).toLowerCase();
				}
				if (!piperGender.equals("Unknown") && piperGender.length() > 0) {
					piperGender = piperGender.substring(0, 1).toUpperCase() + piperGender.substring(1).toLowerCase();
				}
				if (activeSpeakerCount > 1) {
					int currentPiperId = sp1.getInt("active_piper_speaker", 0);
					binding.voiceNameTv.setText(modelLabel + " • Speaker " + currentPiperId + " • " + piperLang);
				} else {
					binding.voiceNameTv.setText(modelLabel + " • " + piperGender + " • " + piperLang);
				}
			}
			binding.voiceNameTv.setTextColor(android.graphics.Color.WHITE);
		}
		listPopupWindow = new androidx.appcompat.widget.ListPopupWindow(getContext());
		listPopupWindow.setAnchorView(binding.voiceNameTv);
		listPopupWindow.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#131B2D")));
		java.util.List<String> voiceNames = new java.util.ArrayList<>();
		if (isKokoro) {
			java.util.List<com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.VoiceItem> allVoices = com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.getAllVoices();
			for (com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.VoiceItem vItem : allVoices) {
				try {
					String label = vItem.getFullLabel();
					voiceNames.add((label != null && !label.isEmpty()) ? label : "Unknown Voice");
				} catch (Throwable t) {
					voiceNames.add("Unknown Voice");
				}
			}
		} else if (activeSpeakerCount > 1) {
			for (int i = 0; i < activeSpeakerCount; i++) {
				voiceNames.add("Speaker " + i);
			}
		}
		voiceAdapter = new android.widget.ArrayAdapter<>(getContext(), R.layout.dropdown_item, R.id.text1, voiceNames);
		listPopupWindow.setAdapter(voiceAdapter);
		listPopupWindow.setOnItemClickListener((parent, view, position, id) -> {
			try {
				String selectedLabel = voiceAdapter.getItem(position);
				if (selectedLabel == null) return;
				String currentType = sp1.getString("active_model_type", "vits");
				boolean currentlyKokoro = currentType.equalsIgnoreCase("kokoro");
				if (currentlyKokoro) {
					binding.voiceNameTv.setText(selectedLabel);
					for (com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.VoiceItem vItem : com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.getAllVoices()) {
						try {
							if (selectedLabel.equals(vItem.getFullLabel())) {
								com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().setActiveSpeakerId(vItem.speakerId);
								sp1.edit().putInt("active_kokoro_speaker", vItem.speakerId).apply();
								break;
							}
						} catch (Throwable t) {}
					}
				} else {
					sp1.edit().putInt("active_piper_speaker", position).apply();
					com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().setActiveSpeakerId(position);
					String currentText = binding.voiceNameTv.getText().toString();
					String langPart = "Unknown";
					String modelPart = "Piper";
					String[] parts = currentText.split(" • ");
					if (parts.length >= 1) modelPart = parts[0];
					if (parts.length >= 3) langPart = parts[2];
					binding.voiceNameTv.setText(modelPart + " • Speaker " + position + " • " + langPart);
				}
				try { com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().cancel(); } catch (Throwable ignored) {}
				try { com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().cancel(); } catch (Throwable ignored) {}
				currentGenerationId++;
				isCancelled = true;
				isGenerating = false;
				isAudioGeneratedForCurrentText = false;
				try {
					if (audioTrack != null) {
						audioTrack.stop();
						audioTrack.flush();
						audioTrack.release();
						audioTrack = null;
					}
				} catch (Exception ignored) {}
				try {
					if (liveStreamTrack != null) {
						liveStreamTrack.stop();
						liveStreamTrack.release();
						liveStreamTrack = null;
					}
				} catch (Exception ignored) {}
				lastGeneratedPcmData = null;
				lastParams = null;
				if (playheadAnimator != null) playheadAnimator.cancel();
				binding.playheadLine.setTranslationX(0f);
				binding.btnGenerate.setAlpha(1.0f);
				binding.btnGenerate.setEnabled(true);
				binding.layoutGeneratedState.setVisibility(android.view.View.GONE);
				binding.layoutIdleState.setVisibility(android.view.View.VISIBLE);
				binding.progressGenerating.setVisibility(android.view.View.GONE);
				binding.imageview65.setVisibility(android.view.View.VISIBLE);
				binding.textview69.setText("READY TO GENERATE");
				binding.textview69.setTextColor(android.graphics.Color.parseColor("#718096"));
				binding.textview92.setText("Generate");
				binding.imageview52.setImageResource(R.drawable.icon_play_circle);
			} catch (Throwable t) {}
			listPopupWindow.dismiss();
		});
		
		binding.imgWaveform.setOnTouchListener((v, motionEvent) -> {
			if (lastGeneratedPcmData == null || audioTrack == null) return false;
			
			int w = binding.imgWaveform.getWidth();
			if (w <= 0) return false;
			
			int totalFrames = lastGeneratedPcmData.length / 2;
			double totalSeconds = (double) totalFrames / lastGeneratedSampleRate;
			int totalMin = (int)(totalSeconds / 60);
			int totalSec = (int)(totalSeconds % 60);
			
			float touchX = Math.max(0f, Math.min(motionEvent.getX(), (float) w));
			float fraction = touchX / w;
			int targetFrame = (int)(fraction * totalFrames);
			
			if (motionEvent.getAction() == android.view.MotionEvent.ACTION_DOWN) {
				if (playheadAnimator != null) playheadAnimator.pause();
				try { audioTrack.pause(); } catch (Exception ignored) {}
			}
			
			if (motionEvent.getAction() == android.view.MotionEvent.ACTION_DOWN
			|| motionEvent.getAction() == android.view.MotionEvent.ACTION_MOVE) {
				binding.playheadLine.setTranslationX(touchX);
				double seekSec = fraction * totalSeconds;
				binding.tvDuration.setText(String.format(java.util.Locale.US, "%d:%02d / %d:%02d",
				(int)(seekSec / 60), (int)(seekSec % 60), totalMin, totalSec));
			}
			
			if (motionEvent.getAction() == android.view.MotionEvent.ACTION_UP
			|| motionEvent.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
				try {
					audioTrack.stop();
					audioTrack.reloadStaticData();
					audioTrack.setPlaybackHeadPosition(targetFrame);
					
					// 🚀 FIX: Naya object nahi banega — fragment-level same object reuse hoga
					_requestAudioFocus();
					
					audioTrack.play();
				} catch (Exception ignored) {}
				
				float remainingSec = (float)(totalFrames - targetFrame) / lastGeneratedSampleRate;
				if (remainingSec < 0) remainingSec = 0f;
				
				if (playheadAnimator != null) playheadAnimator.cancel();
				playheadAnimator = android.animation.ValueAnimator.ofFloat(touchX, (float) w);
				playheadAnimator.setDuration((long)(remainingSec * 1000));
				playheadAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
				playheadAnimator.addUpdateListener(anim -> {
					float tx = (float) anim.getAnimatedValue();
					binding.playheadLine.setTranslationX(tx);
					// tvDuration live update during playback after seek
					double elapsed = (tx / w) * totalSeconds;
					binding.tvDuration.setText(String.format(java.util.Locale.US, "%d:%02d / %d:%02d",
					(int)(elapsed / 60), (int)(elapsed % 60), totalMin, totalSec));
				});
				playheadAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(android.animation.Animator animation) {
						if (binding.playheadLine.getTranslationX() >= w - 10) {
							binding.imageview52.setImageResource(R.drawable.icon_play_circle);
							binding.textview92.setText("Play");
							binding.playheadLine.setTranslationX(0f);
							binding.tvDuration.setText(String.format(java.util.Locale.US,
							"0:00 / %d:%02d", totalMin, totalSec));
							try {
								if (audioTrack != null) {
									audioTrack.stop();
									audioTrack.reloadStaticData();
								}
							} catch (Exception ignored) {}
						}
					}
				});
				playheadAnimator.start();
				
				binding.imageview52.setImageResource(R.drawable.icon_pause_circle);
				binding.textview92.setText("Pause");
			}
			
			return true;
		});
		
	}
	
	@Override
	public void onActivityResult(int _requestCode, int _resultCode, Intent _data) {
		super.onActivityResult(_requestCode, _resultCode, _data);
		if (_requestCode == 100) {
			if (_resultCode == android.app.Activity.RESULT_OK && _data != null) {
				java.util.ArrayList<String> result = _data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
				if (result != null && !result.isEmpty()) {
					String spokenText = result.get(0);
					String currentText = binding.etInput.getText().toString();
					String finalText;
					if (!currentText.trim().isEmpty()) {
						finalText = currentText + " " + spokenText;
					} else {
						finalText = spokenText;
					}
					if (finalText.length() > 25000) {
						finalText = finalText.substring(0, 25000);
					}
					binding.etInput.setText(finalText);
					binding.etInput.setSelection(finalText.length());
				}
			}
		}
		if (_requestCode == 101) {
			if (_resultCode == android.app.Activity.RESULT_OK && _data != null && _data.getData() != null) {
				android.net.Uri fileUri = _data.getData();
				boolean isPdf = false;
				String mimeType = getContext().getContentResolver().getType(fileUri);
				if ("application/pdf".equalsIgnoreCase(mimeType)) {
					isPdf = true;
				} else {
					String path = fileUri.getPath();
					if (path != null && path.toLowerCase().endsWith(".pdf")) {
						isPdf = true;
					}
				}
				com.CodeBySonu.VoxSherpa.TextImportHelper._readDocument(getContext(), fileUri, isPdf, new com.CodeBySonu.VoxSherpa.TextImportHelper.TextImportCallback() {
					@Override
					public void onSuccess(String text) {
						if (text != null) {
							String formattedText = text.trim();
							if (formattedText.length() > 25000) {
								formattedText = formattedText.substring(0, 25000);
							}
							binding.etInput.setText(formattedText);
							binding.etInput.setSelection(formattedText.length());
						}
					}
					@Override
					public void onError(String errorMessage) {
						if (getView() != null) {
							com.google.android.material.snackbar.Snackbar.make(getView(), errorMessage, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
							.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B"))
							.setTextColor(android.graphics.Color.WHITE)
							.show();
						}
					}
				});
			}
		}
		
		switch (_requestCode) {
			
			default:
			break;
		}
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		// Fragment me sirf AudioTrack aur UI Animator ko release karna hai
		if (audioTrack != null) {
			try {
				if (audioTrack.getState() != android.media.AudioTrack.STATE_UNINITIALIZED) {
					audioTrack.stop();
				}
				audioTrack.release();
				audioTrack = null;
			} catch (Exception ignored) {}
		}
		
		if (playheadAnimator != null) {
			playheadAnimator.cancel();
			playheadAnimator = null;
		}
		
	}
	
	@Override
	public void onResume() {
		super.onResume();
		String activeModel = sp1.getString("active_model", "");
		String activeModelType = sp1.getString("active_model_type", "vits");
		String activeModelName = sp1.getString("active_model_name", "Custom Voice");
		int activeSpeakerCount = sp1.getInt("active_speaker_count", 1);
		boolean isKokoro = activeModelType.equalsIgnoreCase("kokoro") || activeModelName.toLowerCase().contains("kokoro");
		if (activeModel.isEmpty()) {
			binding.voiceNameTv.setText("No Model Selected");
			binding.voiceNameTv.setTextColor(android.graphics.Color.parseColor("#FF4B4B"));
			binding.opneDropdown.setVisibility(android.view.View.GONE);
		} else {
			if (isKokoro || activeSpeakerCount > 1) {
				binding.opneDropdown.setVisibility(android.view.View.VISIBLE);
			} else {
				binding.opneDropdown.setVisibility(android.view.View.GONE);
			}
			if (isKokoro) {
				try {
					int savedSpeakerId = sp1.getInt("active_kokoro_speaker", 31);
					com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().setActiveSpeakerId(savedSpeakerId);
					String voiceName = com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().getActiveVoiceName();
					binding.voiceNameTv.setText((voiceName != null && !voiceName.isEmpty()) ? voiceName : "Kokoro Voice");
				} catch (Throwable t) {
					binding.voiceNameTv.setText("Kokoro Voice");
				}
			} else {
				String piperLang = "Unknown";
				String piperGender = "Unknown";
				String modelLabel = "Piper";
				try {
					String modelsDataRaw = sp1.getString("models_data", "[]");
					if (!modelsDataRaw.equals("[]")) {
						java.util.ArrayList<java.util.HashMap<String, Object>> mList =
						new com.google.gson.Gson().fromJson(modelsDataRaw,
						new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType());
						if (mList != null) {
							for (java.util.HashMap<String, Object> model : mList) {
								String onnxPath = model.containsKey("onnx_path") && model.get("onnx_path") != null ? model.get("onnx_path").toString() : "";
								if (activeModel.equals(onnxPath)) {
									if (model.containsKey("language") && model.get("language") != null) {
										piperLang = model.get("language").toString().trim();
									}
									if (model.containsKey("gender") && model.get("gender") != null) {
										piperGender = model.get("gender").toString().trim();
									}
									if (model.containsKey("type") && model.get("type").toString().contains("MMS")) {
										modelLabel = "MMS";
									}
									break;
								}
							}
						}
					}
				} catch (Exception e) {}
				if (!piperLang.equals("Unknown") && piperLang.length() > 0) {
					piperLang = piperLang.substring(0, 1).toUpperCase() + piperLang.substring(1).toLowerCase();
				}
				if (!piperGender.equals("Unknown") && piperGender.length() > 0) {
					piperGender = piperGender.substring(0, 1).toUpperCase() + piperGender.substring(1).toLowerCase();
				}
				if (activeSpeakerCount > 1) {
					int currentPiperId = sp1.getInt("active_piper_speaker", 0);
					binding.voiceNameTv.setText(modelLabel + " • Speaker " + currentPiperId + " • " + piperLang);
				} else {
					binding.voiceNameTv.setText(modelLabel + " • " + piperGender + " • " + piperLang);
				}
			}
			binding.voiceNameTv.setTextColor(android.graphics.Color.WHITE);
		}
		if (listPopupWindow != null) {
			java.util.List<String> newVoiceNames = new java.util.ArrayList<>();
			if (isKokoro) {
				java.util.List<com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.VoiceItem> allVoices = com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.getAllVoices();
				for (com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.VoiceItem vItem : allVoices) {
					try {
						String label = vItem.getFullLabel();
						newVoiceNames.add((label != null && !label.isEmpty()) ? label : "Unknown Voice");
					} catch (Throwable t) {
						newVoiceNames.add("Unknown Voice");
					}
				}
			} else if (activeSpeakerCount > 1) {
				for (int i = 0; i < activeSpeakerCount; i++) {
					newVoiceNames.add("Speaker " + i);
				}
			}
			if (voiceAdapter != null) {
				voiceAdapter.clear();
				voiceAdapter.addAll(newVoiceNames);
				voiceAdapter.notifyDataSetChanged();
			}
		}
		String currentSettingsState = sp3.getFloat("voice_speed", 1.0f) + "_" +
		sp3.getFloat("voice_pitch", 1.0f) + "_" +
		sp3.getFloat("silence_scale", 0.2f) + "_" +
		sp3.getBoolean("smart_punct", false) + "_" +
		sp3.getBoolean("emotion_tags", false) + "_" +
		sp3.getBoolean("multi_speaker_tags", false) + "_" +
		sp3.getInt("voice_style", 1);
		boolean isSettingChanged = !lastSettingsState.isEmpty() && !currentSettingsState.equals(lastSettingsState);
		boolean isModelChanged = !currentUiModelPath.isEmpty() && !activeModel.equals(currentUiModelPath);
		if (isModelChanged || isSettingChanged) {
			_forceResetToIdle();
		}
		currentUiModelPath = activeModel;
		lastSettingsState = currentSettingsState;
		
	}
	
	
	@Override
	public void onStart() {
		super.onStart();
		try {
			// Check if Activity is not null and is an instance of MainActivity
			if (getActivity() != null && getActivity() instanceof MainActivity) {
				MainActivity mainActivity = (MainActivity) getActivity();
				if (mainActivity.sharedProcessText != null && !mainActivity.sharedProcessText.isEmpty()) {
					binding.etInput.setText(mainActivity.sharedProcessText);
					binding.etInput.setSelection(mainActivity.sharedProcessText.length());
					mainActivity.sharedProcessText = ""; 
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	public void _saveAudioAction() {
		// ─── SAVE BUTTON LOGIC ──────────────────────────────────────────────────────
		
		// Pehle check karenge ki audio generate hui hai ya nahi.
		if (!isAudioGeneratedForCurrentText || lastGeneratedPcmData == null) {
			if (getView() != null) {
				com.google.android.material.snackbar.Snackbar.make(getView(), "Please generate audio first!", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
				.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B"))
				.setTextColor(android.graphics.Color.WHITE)
				.show();
			}
			return;
		}
		
		// Yahan se aapka original save logic shuru hota hai (Bina kisi ads ke)
		int sampleRateToSave = lastGeneratedSampleRate > 0 ? lastGeneratedSampleRate : 22050;
		
		String cleanFileName = "Vox_" + System.currentTimeMillis() + ".wav";
		String savedPath = com.CodeBySonu.VoxSherpa.AudioHelper.saveWavFile(lastGeneratedPcmData, cleanFileName, sampleRateToSave, getContext());
		
		if (!savedPath.isEmpty()) {
			try {
				String libraryData = sp2.getString("library_list", "[]");
				java.util.ArrayList<java.util.HashMap<String, Object>> libList = new com.google.gson.Gson().fromJson(
				libraryData, new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType()
				);
				
				if (libList == null) {
					libList = new java.util.ArrayList<>();
				}
				
				java.util.HashMap<String, Object> newItem = new java.util.HashMap<>();
				newItem.put("title", cleanFileName);
				
				// Extract the first sentence from the generated text for a clean title
				String rawText = (lastParams != null && lastParams.text != null) ? lastParams.text : lastGeneratedText;
				if (rawText == null || rawText.trim().isEmpty()) {
					rawText = binding.etInput.getText().toString().trim();
				}
				
				String displayTitle = rawText.trim();
				int dotIndex = displayTitle.indexOf('.');
				int newlineIndex = displayTitle.indexOf('\n');
				int endIndex = -1;
				
				if (dotIndex > 0 && newlineIndex > 0) {
					endIndex = Math.min(dotIndex, newlineIndex);
				} else if (dotIndex > 0) {
					endIndex = dotIndex;
				} else if (newlineIndex > 0) {
					endIndex = newlineIndex;
				}
				
				if (endIndex > 0) {
					displayTitle = displayTitle.substring(0, endIndex + 1).trim();
				} else if (displayTitle.length() > 50) {
					displayTitle = displayTitle.substring(0, 50).trim() + "...";
				}
				
				if (displayTitle.isEmpty()) {
					displayTitle = "Saved Audio";
				}
				
				newItem.put("text", displayTitle);
				newItem.put("full_text", rawText); 
				newItem.put("path", savedPath);
				
				// Exact duration calculation to prevent UI formatting bugs
				double totalSeconds = (lastGeneratedPcmData.length / 2.0) / sampleRateToSave;
				int min = (int) (totalSeconds / 60);
				int sec = (int) (totalSeconds % 60);
				String cleanDuration = String.format(java.util.Locale.US, "%d:%02d", min, sec);
				
				newItem.put("duration", cleanDuration);
				newItem.put("timestamp", String.valueOf(System.currentTimeMillis()));
				newItem.put("voice_name", binding.voiceNameTv.getText().toString());
				newItem.put("is_favorite", false);
				
				libList.add(0, newItem);
				sp2.edit().putString("library_list", new com.google.gson.Gson().toJson(libList)).apply();
				
				if (getView() != null) {
					com.google.android.material.snackbar.Snackbar.make(getView(), "Audio saved to Library!", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
					.setBackgroundTint(android.graphics.Color.parseColor("#1D61FF"))
					.setTextColor(android.graphics.Color.WHITE)
					.show();
				}
			} catch (Exception e) {
				if (getView() != null) {
					com.google.android.material.snackbar.Snackbar.make(getView(), "Failed to update library data.", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
					.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B"))
					.setTextColor(android.graphics.Color.WHITE)
					.show();
				}
			}
		} else {
			if (getView() != null) {
				com.google.android.material.snackbar.Snackbar.make(getView(), "Failed to save audio file.", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
				.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B"))
				.setTextColor(android.graphics.Color.WHITE)
				.show();
			}
		}
		// ─── SAVE BUTTON LOGIC END ──────────────────────────────────────────────────
		
	}
	
	
	public void _forceResetToIdle() {
		// CRITICAL SIGSEGV FIX: Cancel native engines to stop C++ background generation immediately
		try {
			com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().cancel();
			com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().cancel();
		} catch (Throwable ignored) {}
		
		currentGenerationId++; 
		isCancelled = true;
		isGenerating = false;
		isAudioGeneratedForCurrentText = false;
		lastParams = null;
		lastGeneratedPcmData = null;
		
		try {
			if (audioTrack != null) {
				audioTrack.stop();
				audioTrack.release();
				audioTrack = null;
			}
		} catch (Exception ignored) {}
		
		try {
			if (liveStreamTrack != null) {
				liveStreamTrack.stop();
				liveStreamTrack.release();
				liveStreamTrack = null;
			}
		} catch (Exception ignored) {}
		
		if (playheadAnimator != null) playheadAnimator.cancel();
		
		binding.playheadLine.setTranslationX(0f);
		binding.layoutGeneratedState.setVisibility(android.view.View.GONE);
		binding.layoutIdleState.setVisibility(android.view.View.VISIBLE);
		binding.progressGenerating.setVisibility(android.view.View.GONE);
		binding.imageview65.setVisibility(android.view.View.VISIBLE);
		
		binding.textview92.setText("Generate"); // No confusion
		binding.imageview52.setImageResource(R.drawable.icon_play_circle);
		binding.textview69.setText("READY TO SYNTHESIZE");
		binding.textview69.setTextColor(android.graphics.Color.parseColor("#718096"));
		
	}
	
	
	public void _requestAudioFocus() {
		
		if (audioManager == null) return;
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
			if (focusRequest != null) audioManager.requestAudioFocus(focusRequest);
		} else {
			audioManager.requestAudioFocus(focusListener, android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.AUDIOFOCUS_GAIN);
		}
	}
	
	
	public void _toggleGeneratePlayback() {
		
		if (!isAudioGeneratedForCurrentText || audioTrack == null || lastGeneratedPcmData == null) return;
		
		int playState = audioTrack.getPlayState();
		int currentHead = audioTrack.getPlaybackHeadPosition();
		int totalFrames = lastGeneratedPcmData.length / 2;
		
		if (playState == android.media.AudioTrack.PLAYSTATE_PLAYING) {
			audioTrack.pause();
			binding.imageview52.setImageResource(R.drawable.icon_play_circle);
			binding.textview92.setText("Play");
			if (playheadAnimator != null) playheadAnimator.pause();
			
			com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).updatePlaybackState("VoxSherpa Audio", "Paused", com.CodeBySonu.VoxSherpa.system.VoxMediaController.STATE_PAUSED, false);
		} else {
			final double totalSeconds = (double) totalFrames / lastGeneratedSampleRate;
			final int _totalMin = (int)(totalSeconds / 60);
			final int _totalSec = (int)(totalSeconds % 60);
			final int w = binding.imgWaveform.getWidth() > 0 ? binding.imgWaveform.getWidth() : 800;
			
			// 🚀 FIX: Removed 'flush()' which was causing Silent Play. Added proper reset loop.
			if (currentHead >= totalFrames - 4000 || playState == android.media.AudioTrack.PLAYSTATE_STOPPED) {
				try {
					audioTrack.stop();
					audioTrack.reloadStaticData();
					audioTrack.setPlaybackHeadPosition(0);
				} catch (Exception ignored) {}
				
				binding.playheadLine.setTranslationX(0f);
				if (playheadAnimator != null) playheadAnimator.cancel();
				
				playheadAnimator = android.animation.ValueAnimator.ofFloat(0f, (float) w);
				playheadAnimator.setDuration((long) (totalSeconds * 1000));
				playheadAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
				playheadAnimator.addUpdateListener(anim -> {
					float tx = (float) anim.getAnimatedValue();
					binding.playheadLine.setTranslationX(tx);
					double elapsed = (tx / w) * totalSeconds;
					binding.tvDuration.setText(String.format(java.util.Locale.US, "%d:%02d / %d:%02d",
					(int)(elapsed / 60), (int)(elapsed % 60), _totalMin, _totalSec));
				});
				
				playheadAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(android.animation.Animator animation) {
						if (binding.playheadLine.getTranslationX() >= w - 10) {
							binding.imageview52.setImageResource(R.drawable.icon_play_circle);
							binding.textview92.setText("Play");
							binding.playheadLine.setTranslationX(0f);
							binding.tvDuration.setText(String.format(java.util.Locale.US,
							"0:00 / %d:%02d", _totalMin, _totalSec));
							try {
								if (audioTrack != null) {
									audioTrack.stop();
									audioTrack.reloadStaticData();
									audioTrack.setPlaybackHeadPosition(0);
								}
							} catch (Exception ignored) {}
							
							com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).hideNotification();
						}
					}
				});
			}
			
			_requestAudioFocus();
			audioTrack.play();
			binding.imageview52.setImageResource(R.drawable.icon_pause_circle);
			binding.textview92.setText("Pause");
			
			com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).updatePlaybackState("VoxSherpa Audio", "Playing...", com.CodeBySonu.VoxSherpa.system.VoxMediaController.STATE_PLAYING, false);
			
			if (playheadAnimator != null) {
				if (playheadAnimator.isPaused()) {
					playheadAnimator.resume();
				} else {
					playheadAnimator.start();
				}
			}
		}
	}
	
	
	public void _cancelGeneration() {
		if (isGenerating && !isCancelled) {
			isCancelled = true;
			isGenerating = false;
			new Thread(() -> {
				try { com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().cancel(); } catch (Exception ignored) {}
				try { com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().cancel(); } catch (Exception ignored) {}
			}).start();
			
			// LiveStream track (Instant Silence)
			try {
				if (liveStreamTrack != null) {
					liveStreamTrack.pause();
					liveStreamTrack.flush();
					liveStreamTrack.stop();
				}
			} catch (Exception ignored) {}
			
			if (binding != null) {
				try {
					binding.textview92.setText("Canceling...");
					binding.btnGenerate.setAlpha(0.5f);
					binding.btnGenerate.setEnabled(false);
				} catch (Exception ignored) {}
			}
			
			try {
				com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).hideNotification();
			} catch (Exception ignored) {}
		}
		
	}
	
}
