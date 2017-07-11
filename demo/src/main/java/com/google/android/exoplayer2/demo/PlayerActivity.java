/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.demo;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.session.MediaSession;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer.DecoderInitializationException;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveVideoTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.DebugTextViewHelper;
import com.google.android.exoplayer2.ui.PlaybackControlView;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An activity that plays media using {@link SimpleExoPlayer}.
 */
public class PlayerActivity extends Activity implements OnClickListener, ExoPlayer.EventListener,
    PlaybackControlView.VisibilityListener {

  public static final String DRM_SCHEME_UUID_EXTRA = "drm_scheme_uuid";
  public static final String DRM_LICENSE_URL = "drm_license_url";
  public static final String DRM_KEY_REQUEST_PROPERTIES = "drm_key_request_properties";
  public static final String PREFER_EXTENSION_DECODERS = "prefer_extension_decoders";
  public static final String CURRENT_POSITION_FOR_RESUME = "currentPosition_for_resume";
  public static final String CURRENT_PLAYING_CONTENT_URL = "current_content_url";

  public static final String ACTION_VIEW = "com.google.android.exoplayer.demo.action.VIEW";
  public static final String EXTENSION_EXTRA = "extension";

  public static final String ACTION_VIEW_LIST =
      "com.google.android.exoplayer.demo.action.VIEW_LIST";
  public static final String URI_LIST_EXTRA = "uri_list";
  public static final String EXTENSION_LIST_EXTRA = "extension_list";

  private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();
  private static final CookieManager DEFAULT_COOKIE_MANAGER;
  static {
    DEFAULT_COOKIE_MANAGER = new CookieManager();
    DEFAULT_COOKIE_MANAGER.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
  }

  private Handler mainHandler;
  private EventLogger eventLogger;
  private SimpleExoPlayerView simpleExoPlayerView;
  private LinearLayout debugRootView;
  private TextView debugTextView;
  private Button retryButton;

  private DataSource.Factory mediaDataSourceFactory;
  private SimpleExoPlayer player;
  private DefaultTrackSelector trackSelector;
  private TrackSelectionHelper trackSelectionHelper;
  private DebugTextViewHelper debugViewHelper;
  private boolean playerNeedsSource;

  private boolean shouldAutoPlay;
  private int resumeWindow;
  private long resumePosition;
  private Intent intent;
  boolean restart = false;

  private static int NOTIFICATION_ID = 10000;

  private long SEEK_TO_PREVIOUS_DEFAULT_VALUE = 1500;
  private long SEEK_TO_FOWARDS_DEFAULT_VALUE = 1500;


  private ServiceConnection mConnection = new ServiceConnection() {
    PlayerService mBindService;
    public void onServiceConnected(ComponentName className, IBinder service) {
      Log.d(TAG,"onServiceConnected");
      // Serviceとの接続確立時に呼び出される。
      // service引数には、Onbind()で返却したBinderが渡される
      mBindService = ((PlayerService.LocalBinder)service).getService();
      //必要であればmBoundServiceを使ってバインドしたServiceへの制御を行う
    }

    public void onServiceDisconnected(ComponentName className) {
      Log.d(TAG,"onServiceConnected");
      // Serviceとの切断時に呼び出される。
      mBindService = null;
    }
  };

  private boolean isBackground = false;
  private String TAG = PlayerActivity.class.getSimpleName();

  // Activity lifecycle

  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.d(TAG,"onCreate");
    super.onCreate(savedInstanceState);
    shouldAutoPlay = true;
    clearResumePosition();
    mediaDataSourceFactory = buildDataSourceFactory(true);
    mainHandler = new Handler();
    if (CookieHandler.getDefault() != DEFAULT_COOKIE_MANAGER) {
      CookieHandler.setDefault(DEFAULT_COOKIE_MANAGER);
    }

    setContentView(R.layout.player_activity);
    View rootView = findViewById(R.id.root);
    rootView.setOnClickListener(this);
    debugRootView = (LinearLayout) findViewById(R.id.controls_root);
    debugTextView = (TextView) findViewById(R.id.debug_text_view);
    retryButton = (Button) findViewById(R.id.retry_button);
    retryButton.setOnClickListener(this);

    simpleExoPlayerView = (SimpleExoPlayerView) findViewById(R.id.player_view);
    simpleExoPlayerView.setControllerVisibilityListener(this);
    simpleExoPlayerView.requestFocus();
  }

  @Override
  public void onNewIntent(Intent intent) {
    Log.d(TAG,"onNewIntent");
    shouldAutoPlay = true;
    restart = isPushedNotification(intent);
    setIntent(intent);
    //todo 以下いる?
//    if (intent.getLongExtra(CURRENT_POSITION_FOR_RESUME,0) != 0) {
//      resumePosition = intent.getLongExtra(CURRENT_POSITION_FOR_RESUME, 0);
//    } else {
//      clearResumePosition();
//    }

//    stopBackgroundService();//通知を押下することで、stopBackgroundService()が2回呼ばれてる
  }

  @Override
  public void onStart() {
    Log.d(TAG, "onStart()");
    super.onStart();
    if (Util.SDK_INT > 23) {
      //todo background からの復帰処理を追加
      if (!restart) {
        initializePlayer();
      }
    }
  }

  @Override
  public void onResume() {
    Log.d(TAG, "onResume()");
    super.onResume();
    if ((Util.SDK_INT <= 23 || player == null)) {
      //todo background からの復帰処理を追加
      if (!restart) {
        initializePlayer();
      }
    }
//    stopBackgroundService();
  }

  @Override
  protected void onRestart() {
    super.onRestart();
    unregisterReceiver(mIntentReceiver);
    goneNotification();
  }

  @Override
  public void onPause() {
    Log.d(TAG, "onPause()");
    super.onPause();
    if (Util.SDK_INT <= 23) {
      createPauseNotification();


      IntentFilter commandFilter = new IntentFilter();
      commandFilter.addAction(PlayerUtil.ACTION_RESTART_ACTIVITY);
      commandFilter.addAction(PlayerUtil.ACTION_PAUSE_INTENT);
      commandFilter.addAction(PlayerUtil.ACTION_SEEK_TO_FOWARD_INTENT);
      commandFilter.addAction(PlayerUtil.ACTION_SEEK_TO_PREVIOUS_INTENT);


      registerReceiver(mIntentReceiver, commandFilter);
//      releasePlayer();
//      startBackgroundService();
//      releasePlayer();
    }
  }

  @Override
  public void onStop() {
    Log.d(TAG, "onStop()");
    super.onStop();
    if (Util.SDK_INT > 23) {
      createPauseNotification();

      IntentFilter commandFilter = new IntentFilter();
      commandFilter.addAction(PlayerUtil.ACTION_RESTART_ACTIVITY);
      commandFilter.addAction(PlayerUtil.ACTION_PAUSE_INTENT);
      commandFilter.addAction(PlayerUtil.ACTION_SEEK_TO_FOWARD_INTENT);
      commandFilter.addAction(PlayerUtil.ACTION_SEEK_TO_PREVIOUS_INTENT);

      registerReceiver(mIntentReceiver, commandFilter);
//      releasePlayer();
//      startBackgroundService();
//      releasePlayer();
    }
  }

  @Override
  protected void onDestroy() {
    Log.d(TAG, "onDestroy()");
    super.onDestroy();
    goneNotification();
//    stopBackgroundService();
    releasePlayer();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions,
      int[] grantResults) {
    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      initializePlayer();
    } else {
      showToast(R.string.storage_permission_denied);
      finish();
    }
  }

  // Activity input

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    // Show the controls on any key event.
    simpleExoPlayerView.showController();
    // If the event was not handled then see if the player view can handle it as a media key event.
    return super.dispatchKeyEvent(event) || simpleExoPlayerView.dispatchMediaKeyEvent(event);
  }

  // OnClickListener methods

  @Override
  public void onClick(View view) {
    if (view == retryButton) {
      initializePlayer();
    } else if (view.getParent() == debugRootView) {
      MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
      if (mappedTrackInfo != null) {
        trackSelectionHelper.showSelectionDialog(this, ((Button) view).getText(),
            trackSelector.getCurrentMappedTrackInfo(), (int) view.getTag());
      }
    }
  }

  // PlaybackControlView.VisibilityListener implementation

  @Override
  public void onVisibilityChange(int visibility) {
    debugRootView.setVisibility(visibility);
  }

  // Internal methods

  private void initializePlayer() {
    Log.d(TAG, "initializePlayer");
    intent = getIntent();
    if (intent.getAction().equals(PlayerUtil.ACTION_RESTART_ACTIVITY)) {
      Log.d(TAG,"ACTION_RESTART_ACTIVITY");
      simpleExoPlayerView.setPlayer(player);
      return;
    }
    if (player == null) {
      boolean preferExtensionDecoders = intent.getBooleanExtra(PREFER_EXTENSION_DECODERS, false);
      UUID drmSchemeUuid = intent.hasExtra(DRM_SCHEME_UUID_EXTRA)
          ? UUID.fromString(intent.getStringExtra(DRM_SCHEME_UUID_EXTRA)) : null;
      DrmSessionManager<FrameworkMediaCrypto> drmSessionManager = null;
      if (drmSchemeUuid != null) {
        String drmLicenseUrl = intent.getStringExtra(DRM_LICENSE_URL);
        String[] keyRequestPropertiesArray = intent.getStringArrayExtra(DRM_KEY_REQUEST_PROPERTIES);
        Map<String, String> keyRequestProperties;
        if (keyRequestPropertiesArray == null || keyRequestPropertiesArray.length < 2) {
          keyRequestProperties = null;
        } else {
          keyRequestProperties = new HashMap<>();
          for (int i = 0; i < keyRequestPropertiesArray.length - 1; i += 2) {
            keyRequestProperties.put(keyRequestPropertiesArray[i],
                keyRequestPropertiesArray[i + 1]);
          }
        }
        try {
          drmSessionManager = buildDrmSessionManager(drmSchemeUuid, drmLicenseUrl,
              keyRequestProperties);
        } catch (UnsupportedDrmException e) {
          int errorStringId = Util.SDK_INT < 18 ? R.string.error_drm_not_supported
              : (e.reason == UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME
                  ? R.string.error_drm_unsupported_scheme : R.string.error_drm_unknown);
          showToast(errorStringId);
          return;
        }
      }

      @SimpleExoPlayer.ExtensionRendererMode int extensionRendererMode =
          ((DemoApplication) getApplication()).useExtensionRenderers()
              ? (preferExtensionDecoders ? SimpleExoPlayer.EXTENSION_RENDERER_MODE_PREFER
              : SimpleExoPlayer.EXTENSION_RENDERER_MODE_ON)
              : SimpleExoPlayer.EXTENSION_RENDERER_MODE_OFF;
      TrackSelection.Factory videoTrackSelectionFactory =
          new AdaptiveVideoTrackSelection.Factory(BANDWIDTH_METER);
      trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
      trackSelectionHelper = new TrackSelectionHelper(trackSelector, videoTrackSelectionFactory);
      player = ExoPlayerFactory.newSimpleInstance(this, trackSelector, new DefaultLoadControl(),
          drmSessionManager, extensionRendererMode);
      player.addListener(this);

      eventLogger = new EventLogger(trackSelector);
      player.addListener(eventLogger);
      player.setAudioDebugListener(eventLogger);
      player.setVideoDebugListener(eventLogger);
      player.setMetadataOutput(eventLogger);

      simpleExoPlayerView.setPlayer(player);
      player.setPlayWhenReady(shouldAutoPlay);
      debugViewHelper = new DebugTextViewHelper(player, debugTextView);
      debugViewHelper.start();
      playerNeedsSource = true;
    }
    if (playerNeedsSource) {
      String action = intent.getAction();
      Uri[] uris;
      String[] extensions;
      if (ACTION_VIEW.equals(action)) {
        uris = new Uri[] {intent.getData()};
        extensions = new String[] {intent.getStringExtra(EXTENSION_EXTRA)};
      } else if (ACTION_VIEW_LIST.equals(action)) {
        String[] uriStrings = intent.getStringArrayExtra(URI_LIST_EXTRA);
        uris = new Uri[uriStrings.length];
        for (int i = 0; i < uriStrings.length; i++) {
          uris[i] = Uri.parse(uriStrings[i]);
        }
        extensions = intent.getStringArrayExtra(EXTENSION_LIST_EXTRA);
        if (extensions == null) {
          extensions = new String[uriStrings.length];
        }
      } else {
        showToast(getString(R.string.unexpected_intent_action, action));
        return;
      }
      if (Util.maybeRequestReadExternalStoragePermission(this, uris)) {
        // The player will be reinitialized if the permission is granted.
        return;
      }
      MediaSource[] mediaSources = new MediaSource[uris.length];
      for (int i = 0; i < uris.length; i++) {
        mediaSources[i] = buildMediaSource(uris[i], extensions[i]);
      }
      MediaSource mediaSource = mediaSources.length == 1 ? mediaSources[0]
          : new ConcatenatingMediaSource(mediaSources);
      boolean haveResumePosition = resumeWindow != C.INDEX_UNSET;
      if (haveResumePosition) {
        player.seekTo(resumeWindow, resumePosition);
      }
      player.prepare(mediaSource, !haveResumePosition, false);
      playerNeedsSource = false;
      updateButtonVisibilities();
    }
  }

  private MediaSource buildMediaSource(Uri uri, String overrideExtension) {
    int type = Util.inferContentType(!TextUtils.isEmpty(overrideExtension) ? "." + overrideExtension
        : uri.getLastPathSegment());
    switch (type) {
      case C.TYPE_SS:
        return new SsMediaSource(uri, buildDataSourceFactory(false),
            new DefaultSsChunkSource.Factory(mediaDataSourceFactory), mainHandler, eventLogger);
      case C.TYPE_DASH:
        return new DashMediaSource(uri, buildDataSourceFactory(false),
            new DefaultDashChunkSource.Factory(mediaDataSourceFactory), mainHandler, eventLogger);
      case C.TYPE_HLS:
        return new HlsMediaSource(uri, mediaDataSourceFactory, mainHandler, eventLogger);
      case C.TYPE_OTHER:
        return new ExtractorMediaSource(uri, mediaDataSourceFactory, new DefaultExtractorsFactory(),
            mainHandler, eventLogger);
      default: {
        throw new IllegalStateException("Unsupported type: " + type);
      }
    }
  }

  private DrmSessionManager<FrameworkMediaCrypto> buildDrmSessionManager(UUID uuid,
      String licenseUrl, Map<String, String> keyRequestProperties) throws UnsupportedDrmException {
    if (Util.SDK_INT < 18) {
      return null;
    }
    HttpMediaDrmCallback drmCallback = new HttpMediaDrmCallback(licenseUrl,
        buildHttpDataSourceFactory(false), keyRequestProperties);
    return new DefaultDrmSessionManager<>(uuid,
        FrameworkMediaDrm.newInstance(uuid), drmCallback, null, mainHandler, eventLogger);
  }

  private void releasePlayer() {
    if (player != null) {
      debugViewHelper.stop();
      debugViewHelper = null;
      shouldAutoPlay = player.getPlayWhenReady();
      updateResumePosition();
      player.release();
      player = null;
      trackSelector = null;
      trackSelectionHelper = null;
      eventLogger = null;
    }
  }

  private void startBackgroundService() {
    Log.d(TAG, "startBackgroundService");
    long currentPosition = 0;
    isBackground = true;
    if (player != null) {
      currentPosition = player.getCurrentPosition();
    }
    bindService(buildServiceIntent(this, currentPosition), mConnection, Context.BIND_AUTO_CREATE);
  }

  private void stopBackgroundService() {
    Log.d(TAG,"stopBackgroundService");
    if (isBackground) {
      unbindService(mConnection);
      isBackground = false;

    }
  }

  private void goneNotification() {
    NotificationManager manager = (NotificationManager) getSystemService(getApplicationContext().NOTIFICATION_SERVICE);
    manager.cancel(NOTIFICATION_ID);
  }

  public final boolean preferExtensionDecoders = false; //todo 実装する?

  public Intent buildServiceIntent(Context context,long currentPosition) {
//    Intent intent = new Intent(context, PlayerService.class);
    intent.setClass(context,PlayerService.class);
//    intent.putExtra(PlayerActivity.PREFER_EXTENSION_DECODERS, preferExtensionDecoders);
    intent.putExtra(CURRENT_POSITION_FOR_RESUME, currentPosition);
    Log.d(TAG, "currentPosition : " + currentPosition);
//    if (drmSchemeUuid != null) {
//      intent.putExtra(PlayerActivity.DRM_SCHEME_UUID_EXTRA, drmSchemeUuid.toString());
//      intent.putExtra(PlayerActivity.DRM_LICENSE_URL, drmLicenseUrl);
//      intent.putExtra(PlayerActivity.DRM_KEY_REQUEST_PROPERTIES, drmKeyRequestProperties);
//    }
    return intent;
  }

  private void updateResumePosition() {
    resumeWindow = player.getCurrentWindowIndex();
    resumePosition = player.isCurrentWindowSeekable() ? Math.max(0, player.getCurrentPosition())
        : C.TIME_UNSET;
  }

  private void clearResumePosition() {
    resumeWindow = C.INDEX_UNSET;
    resumePosition = C.TIME_UNSET;
  }

  /**
   * Returns a new DataSource factory.
   *
   * @param useBandwidthMeter Whether to set {@link #BANDWIDTH_METER} as a listener to the new
   *     DataSource factory.
   * @return A new DataSource factory.
   */
  private DataSource.Factory buildDataSourceFactory(boolean useBandwidthMeter) {
    return ((DemoApplication) getApplication())
        .buildDataSourceFactory(useBandwidthMeter ? BANDWIDTH_METER : null);
  }

  /**
   * Returns a new HttpDataSource factory.
   *
   * @param useBandwidthMeter Whether to set {@link #BANDWIDTH_METER} as a listener to the new
   *     DataSource factory.
   * @return A new HttpDataSource factory.
   */
  private HttpDataSource.Factory buildHttpDataSourceFactory(boolean useBandwidthMeter) {
    return ((DemoApplication) getApplication())
        .buildHttpDataSourceFactory(useBandwidthMeter ? BANDWIDTH_METER : null);
  }

  // ExoPlayer.EventListener implementation

  @Override
  public void onLoadingChanged(boolean isLoading) {
    // Do nothing.
  }

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    if (playbackState == ExoPlayer.STATE_ENDED) {
      showControls();
    }
    updateButtonVisibilities();
  }

  @Override
  public void onPositionDiscontinuity() {
    if (playerNeedsSource) {
      // This will only occur if the user has performed a seek whilst in the error state. Update the
      // resume position so that if the user then retries, playback will resume from the position to
      // which they seeked.
      updateResumePosition();
    }
  }

  @Override
  public void onTimelineChanged(Timeline timeline, Object manifest) {
    // Do nothing.
  }

  @Override
  public void onPlayerError(ExoPlaybackException e) {
    String errorString = null;
    if (e.type == ExoPlaybackException.TYPE_RENDERER) {
      Exception cause = e.getRendererException();
      if (cause instanceof DecoderInitializationException) {
        // Special case for decoder initialization failures.
        DecoderInitializationException decoderInitializationException =
            (DecoderInitializationException) cause;
        if (decoderInitializationException.decoderName == null) {
          if (decoderInitializationException.getCause() instanceof DecoderQueryException) {
            errorString = getString(R.string.error_querying_decoders);
          } else if (decoderInitializationException.secureDecoderRequired) {
            errorString = getString(R.string.error_no_secure_decoder,
                decoderInitializationException.mimeType);
          } else {
            errorString = getString(R.string.error_no_decoder,
                decoderInitializationException.mimeType);
          }
        } else {
          errorString = getString(R.string.error_instantiating_decoder,
              decoderInitializationException.decoderName);
        }
      }
    }
    if (errorString != null) {
      showToast(errorString);
    }
    playerNeedsSource = true;
    if (isBehindLiveWindow(e)) {
      clearResumePosition();
      initializePlayer();
    } else {
      updateResumePosition();
      updateButtonVisibilities();
      showControls();
    }
  }

  @Override
  public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    updateButtonVisibilities();
    MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
    if (mappedTrackInfo != null) {
      if (mappedTrackInfo.getTrackTypeRendererSupport(C.TRACK_TYPE_VIDEO)
          == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
        showToast(R.string.error_unsupported_video);
      }
      if (mappedTrackInfo.getTrackTypeRendererSupport(C.TRACK_TYPE_AUDIO)
          == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
        showToast(R.string.error_unsupported_audio);
      }
    }
  }

  // User controls

  private void updateButtonVisibilities() {
    debugRootView.removeAllViews();

    retryButton.setVisibility(playerNeedsSource ? View.VISIBLE : View.GONE);
    debugRootView.addView(retryButton);

    if (player == null) {
      return;
    }

    MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
    if (mappedTrackInfo == null) {
      return;
    }

    for (int i = 0; i < mappedTrackInfo.length; i++) {
      TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(i);
      if (trackGroups.length != 0) {
        Button button = new Button(this);
        int label;
        switch (player.getRendererType(i)) {
          case C.TRACK_TYPE_AUDIO:
            label = R.string.audio;
            break;
          case C.TRACK_TYPE_VIDEO:
            label = R.string.video;
            break;
          case C.TRACK_TYPE_TEXT:
            label = R.string.text;
            break;
          default:
            continue;
        }
        button.setText(label);
        button.setTag(i);
        button.setOnClickListener(this);
        debugRootView.addView(button, debugRootView.getChildCount() - 1);
      }
    }
  }

  private void showControls() {
    debugRootView.setVisibility(View.VISIBLE);
  }

  private void showToast(int messageId) {
    showToast(getString(messageId));
  }

  private void showToast(String message) {
    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
  }

  private static boolean isBehindLiveWindow(ExoPlaybackException e) {
    if (e.type != ExoPlaybackException.TYPE_SOURCE) {
      return false;
    }
    Throwable cause = e.getSourceException();
    while (cause != null) {
      if (cause instanceof BehindLiveWindowException) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  private void createPlayNotification() {
    Notification.Builder builder = new Notification.Builder(this);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      builder.setColor(Color.RED);
    }
    builder.setSmallIcon(R.mipmap.ic_launcher);
    Bitmap bmp1 = BitmapFactory.decodeResource(getResources(), R.drawable.bigbuckbunny);
    builder.setLargeIcon(bmp1);
    builder.setContentTitle("TITLE iS XX");
    builder.setContentText("Text is XX");
    builder.setContentIntent(getPendingIntentWithBroadcast(PlayerUtil.ACTION_RESTART_ACTIVITY));
    builder.setDeleteIntent(getPendingIntentWithBroadcast(PlayerUtil.ACTION_DELETE_PLAYER));
    builder.addAction(R.drawable.exo_controls_previous, "<<", getPendingIntentWithBroadcast(PlayerUtil.ACTION_SEEK_TO_PREVIOUS_INTENT));
    builder.addAction(R.drawable.exo_controls_play, "Play", getPendingIntentWithBroadcast(PlayerUtil.ACTION_PAUSE_INTENT));
    builder.addAction(R.drawable.exo_controls_fastforward, ">>", getPendingIntentWithBroadcast(PlayerUtil.ACTION_SEEK_TO_FOWARD_INTENT));

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      MediaSession mediaSession = new MediaSession(getApplicationContext(), "naito");
      builder.setStyle(new Notification.MediaStyle()
              .setMediaSession(mediaSession.getSessionToken())
              .setShowActionsInCompactView(1));
    }
    NotificationManager manager = (NotificationManager) getSystemService(getApplicationContext().NOTIFICATION_SERVICE);
    manager.notify(NOTIFICATION_ID, builder.build());//todo generate random notification Id
  }

  private void createPauseNotification() {
    Notification.Builder builder = new Notification.Builder(this);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      builder.setColor(Color.RED);
    }
    builder.setSmallIcon(R.mipmap.ic_launcher);
    Bitmap bmp1 = BitmapFactory.decodeResource(getResources(), R.drawable.bigbuckbunny);
    builder.setLargeIcon(bmp1);
    builder.setContentTitle("TITLE iS XX");
    builder.setContentText("Text is XX");
    builder.setContentIntent(getPendingIntentWithBroadcast(PlayerUtil.ACTION_RESTART_ACTIVITY));
    builder.addAction(R.drawable.exo_controls_previous, "<<", getPendingIntentWithBroadcast(PlayerUtil.ACTION_SEEK_TO_PREVIOUS_INTENT));
    builder.addAction(R.drawable.exo_controls_pause, "Pause", getPendingIntentWithBroadcast(PlayerUtil.ACTION_PAUSE_INTENT));
    builder.addAction(R.drawable.exo_controls_fastforward, ">>", getPendingIntentWithBroadcast(PlayerUtil.ACTION_SEEK_TO_FOWARD_INTENT));

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      MediaSession mediaSession = new MediaSession(getApplicationContext(), "naito");
      builder.setStyle(new Notification.MediaStyle()
              .setMediaSession(mediaSession.getSessionToken())
              .setShowActionsInCompactView(1));
    }
    NotificationManager manager = (NotificationManager) getSystemService(getApplicationContext().NOTIFICATION_SERVICE);
    manager.notify(NOTIFICATION_ID, builder.build());//todo generate random notification Id
  }


  private PendingIntent getPendingIntentWithBroadcast(String action) {
    return PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(action), 0);
  }

  private void checkIntent(Intent intent) {
    if (intent == null || player == null) {
      return;
    }
    if (intent.getAction().equals(PlayerUtil.ACTION_PAUSE_INTENT)) {
      if (isPlaying()) {
        player.setPlayWhenReady(false);
        createPlayNotification();
      } else {
        player.setPlayWhenReady(true);
        createPauseNotification();
      }
    } else if (intent.getAction().equals(PlayerUtil.ACTION_SEEK_TO_PREVIOUS_INTENT)) {
      player.seekTo(player.getCurrentPosition() - SEEK_TO_PREVIOUS_DEFAULT_VALUE);
    } else if (intent.getAction().equals(PlayerUtil.ACTION_SEEK_TO_FOWARD_INTENT)) {
      player.seekTo(player.getCurrentPosition() + SEEK_TO_FOWARDS_DEFAULT_VALUE);
    } else if (intent.getAction().equals(PlayerUtil.ACTION_RESTART_ACTIVITY)) {
      Log.d(TAG, "back to playback into activity");
      Intent i = new Intent(this, PlayerActivity.class);
      i.setAction(PlayerUtil.ACTION_RESTART_ACTIVITY);
      startActivity(i);
    } else if (intent.getAction().equals(PlayerUtil.ACTION_DELETE_PLAYER)) {
      releasePlayer();
      finish();
    }
  }

  private boolean isPushedNotification(Intent intent) {
    if (intent == null || player == null) {
      return false;
    }
    if (intent.getAction().equals(PlayerUtil.ACTION_RESTART_ACTIVITY)) {
      return true;
    }
    return false;
  }

  public boolean isPlaying() {
    //Log.enter(TAG, "isPlaying", "");
    if (player == null) {
      return false;
    }
    if (player.getPlayWhenReady()) {
      if (player.getPlaybackState() == ExoPlayer.STATE_READY || player.getPlaybackState() == ExoPlayer.STATE_BUFFERING) {
        return true;
      } else {
        return false;
      }
    } else {
      return false;
    }
  }

  private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      Log.d(TAG, "onReceive : intent " + intent + " context : " + context);
      String action = intent.getAction();
      String cmd = intent.getStringExtra("command");
      checkIntent(intent);
    }
  };

}
