/*
 * Copyright 2016 eneim@Eneim Labs, nam@ene.im
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.ene.toro;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewParent;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static android.os.Build.VERSION.SDK_INT;

/**
 * Created by eneim on 1/31/16.
 *
 * Control Application's lifecycle to properly handling callbacks, prevent Memory leak and
 * unexpected behavior;
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)  //
public final class Toro implements Application.ActivityLifecycleCallbacks {

  static final String TAG = "ToroLib";

  public static final double DEFAULT_OFFSET = 0.75;

  /**
   * Stop playback strategy
   */
  private static final ToroStrategy REST = new ToroStrategy() {
    @Override public String getDescription() {
      return "'Do nothing' Strategy";
    }

    @Override public ToroPlayer findBestPlayer(List<ToroPlayer> candidates) {
      return null;
    }

    @Override public boolean allowsToPlay(ToroPlayer player, ViewParent parent) {
      return false;
    }
  };

  // Singleton, GOD object
  static volatile Toro sInstance;

  // Used to swap strategies if need. It should be a strong reference.
  private static ToroStrategy cachedStrategy;

  // It requires client to detach Activity/unregister View to prevent Memory leak
  // Use RecyclerView#hashCode() to sync between maps
  private final LinkedHashMap<RecyclerView, PlayerManager> managers = new LinkedHashMap<>();
  private final LinkedHashMap<RecyclerView, OnScrollListenerImpl> listeners = new LinkedHashMap<>();
  private final LinkedHashMap<PlayerManager, MediaDataObserver> observers = new LinkedHashMap<>();

  // Default strategy
  private ToroStrategy strategy = Strategies.MOST_VISIBLE_TOP_DOWN;

  /**
   * Attach an activity to Toro. Toro register activity's life cycle to properly handle Screen
   * visibility: free necessary resource if User doesn't need it anymore
   *
   * @param activity the Activity to which Toro gonna attach to
   */
  public static void attach(@NonNull Activity activity) {
    init(activity.getApplication());
  }

  /**
   * Same purpose to {@link Toro#attach(Activity)}, but support overall the Application
   *
   * @param application the Application where Toro will apply for.
   */
  public static void init(Application application) {
    if (sInstance == null) {
      synchronized (Toro.class) {
        if (sInstance == null) {
          sInstance = new Toro();
          application.registerActivityLifecycleCallbacks(sInstance);
          // TODO remove on release
          application.registerActivityLifecycleCallbacks(new LifeCycleDebugger());
        }
      }
    }
  }

  /**
   * Carefully detach current Activity from Toro. Should be coupled with {@link
   * Toro#attach(Activity)}
   *
   * @param activity The host Activity where Toro will detach from.
   */
  public static void detach(@NonNull Activity activity) {
    //noinspection ConstantConditions
    if (activity == null) {
      throw new NullPointerException("Activity must not be null.");
    }

    if (sInstance != null) {
      activity.getApplication().unregisterActivityLifecycleCallbacks(sInstance);
      sInstance = null;
    }

    // Cleanup
    for (RecyclerView view : sInstance.managers.keySet()) {
      unregister(view);
    }
  }

  public static ToroStrategy getStrategy() {
    return sInstance.strategy;
  }

  /**
   * Support custom playing policy
   *
   * @param strategy requested policy from client
   */
  public static void setStrategy(@NonNull ToroStrategy strategy) {
    if (sInstance.strategy == strategy) {
      // Nothing changes
      return;
    }

    sInstance.strategy = strategy;
    dispatchStrategyChanged(strategy);
  }

  /**
   * Register a View (currently, must be one of RecyclerView) to listen to its Videos
   *
   * @param view which will be registered
   */
  public static void register(RecyclerView view) {
    if (sInstance == null) {
      throw new RuntimeException(
          "Toro has not been initialized. Please call Toro#init(Application).");
    }

    if (view == null) {
      throw new NullPointerException("View must not be null.");
    }

    if (sInstance.managers.containsKey(view) && sInstance.listeners.containsKey(view)) {
      sInstance.managers.get(view).onRegistered();
      return;
    }

    // 1. Retrieve current PlayerManager instance
    final PlayerManager playerManager;
    RecyclerView.Adapter adapter = view.getAdapter();
    // Client of this API should implement PlayerManager to its Adapter.
    if (adapter instanceof PlayerManager) {
      playerManager = (PlayerManager) adapter;
    } else {
      // Toro 2.2.0+ and 3+ will force the implementation of PlayerManager. Of course, there is delegation
      throw new RuntimeException("Adapter must be a PlayerManager");
    }

    MediaDataObserver observer = new MediaDataObserver(playerManager);
    adapter.registerAdapterDataObserver(observer);
    sInstance.observers.put(playerManager, observer);

    sInstance.managers.put(view, playerManager);
    // setup new scroll listener
    OnScrollListenerImpl listener = new OnScrollListenerImpl();
    view.addOnScrollListener(listener);
    sInstance.listeners.put(view, listener);

    // Done registering new View
    playerManager.onRegistered();

    // in case the Manager/Adapter has a preset Player and a saved playback state
    // (either coming back from Stopped state or a predefined one)
    ToroPlayer player = playerManager.getPlayer();
    if (player != null && playerManager.getPlaybackState(player.getMediaId()) != null) {
      if (player.wantsToPlay() && player.wantsToPlay() && //
          Toro.getStrategy().allowsToPlay(player, view)) {
        if (!player.isPrepared()) {
          player.preparePlayer(false);
        } else if (!player.isPlaying()) {
          playerManager.restorePlaybackState(player.getMediaId());
          playerManager.startPlayback();
        }
      }
    }
  }

  /**
   * Unregister a registered View
   *
   * @param view which will be unregistered
   */
  public static void unregister(RecyclerView view) {
    if (view == null) {
      throw new NullPointerException("Un-registering View must not be null");
    }

    OnScrollListenerImpl listener = sInstance.listeners.remove(view);
    PlayerManager manager = sInstance.managers.remove(view);
    MediaDataObserver observer = sInstance.observers.remove(manager);
    if (manager.getPlayer() != null) {
      final ToroPlayer player = manager.getPlayer();
      manager.savePlaybackState(player.getMediaId(), //
          player.getCurrentPosition(), player.getDuration());
      if (player.isPlaying()) {
        manager.pausePlayback();
      }

      player.releasePlayer();
    }

    manager.onUnregistered();
    view.removeOnScrollListener(listener);

    try {
      observer.remove();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Nullable static PlayerManager getManager(ViewParent viewParent) {
    return viewParent instanceof RecyclerView ? sInstance.managers.get(viewParent) : null;
  }

  public static void resume() {
    rest(false);
  }

  public static void pause() {
    rest(true);
  }

  public static boolean isActive() {
    return !isResting();  // not resting!!
  }

  @Deprecated public static void rest(boolean willPause) {
    if (willPause) {
      cachedStrategy = getStrategy();
      setStrategy(REST);
    } else {
      // Don't allow to resume if Toro has not been in paused state. Be careful.
      if (getStrategy() != REST) {
        throw new IllegalStateException("Toro has already resumed.");
      }

      if (cachedStrategy != null) { // Actually, cachedStrategy would not be null here.
        setStrategy(cachedStrategy);
        cachedStrategy = null;  // release
      }
    }
  }

  // Experiment
  @Deprecated public static boolean isResting() {
    return getStrategy() == REST;
  }

  private static void dispatchStrategyChanged(ToroStrategy newStrategy) {
    for (Map.Entry<RecyclerView, OnScrollListenerImpl> entry : sInstance.listeners.entrySet()) {
      entry.getValue().onScrollStateChanged(entry.getKey(), RecyclerView.SCROLL_STATE_IDLE);
    }
  }

  @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    // Do nothing here
  }

  @Override public void onActivityStarted(Activity activity) {
    if (SDK_INT > 23) { // Android N and up
      dispatchOnActivityActive(activity);
    }
  }

  @Override public void onActivityResumed(Activity activity) {
    if (SDK_INT <= 23) {
      dispatchOnActivityActive(activity);
    }
  }

  @Override public void onActivityPaused(Activity activity) {
    if (SDK_INT <= 23) {
      dispatchOnActivityInactive(activity);
    }
  }

  @Override public void onActivityStopped(Activity activity) {
    if (SDK_INT > 23) { // Android N and up
      dispatchOnActivityInactive(activity);
    }
  }

  @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    // TODO: deal with orientation changes
  }

  @Override public void onActivityDestroyed(Activity activity) {
    for (Map.Entry<RecyclerView, PlayerManager> entry : managers.entrySet()) {
      if (entry.getKey().getContext() == activity) {
        PlayerManager manager = entry.getValue();
        try {
          manager.remove();
          observers.get(manager).remove();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }

    for (Map.Entry<RecyclerView, OnScrollListenerImpl> entry : listeners.entrySet()) {
      if (entry.getKey().getContext() == activity) {
        try {
          entry.getValue().remove();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }

  // Built-in Strategies
  public static final class Strategies {

    /**
     * Among all playable Videos, select the most visible item (Max {@link
     * ToroPlayer#visibleAreaOffset()}). In case there are more than one item, chose the first item
     * on the top
     */
    public static final ToroStrategy MOST_VISIBLE_TOP_DOWN = new ToroStrategy() {

      @Override public String getDescription() {
        return "Most visible item, top - down";
      }

      @Nullable @Override public ToroPlayer findBestPlayer(List<ToroPlayer> candidates) {
        if (candidates == null || candidates.size() < 1) {
          return null;
        }

        // 1. Sort candidates by the order of player
        Collections.sort(candidates, new Comparator<ToroPlayer>() {
          @Override public int compare(ToroPlayer lhs, ToroPlayer rhs) {
            return lhs.getPlayOrder() - rhs.getPlayOrder();
          }
        });

        // 2. Sort candidates by the visible offset
        Collections.sort(candidates, new Comparator<ToroPlayer>() {
          @Override public int compare(ToroPlayer lhs, ToroPlayer rhs) {
            return Float.compare(rhs.visibleAreaOffset(), lhs.visibleAreaOffset());
          }
        });

        return candidates.get(0);
      }

      @Override public boolean allowsToPlay(ToroPlayer player, ViewParent parent) {
        return doAllowsToPlay(player, parent);
      }
    };

    /**
     * Among all playable Videos, select the most visible item (Max {@link
     * ToroPlayer#visibleAreaOffset()}). In case there are more than one item, chose the first item
     * on the top. But if current player is still playable, but not staying on the top, we still
     * keep it.
     */
    public static final ToroStrategy MOST_VISIBLE_TOP_DOWN_KEEP_LAST = new ToroStrategy() {
      @Override public String getDescription() {
        return "Most visible item, top - down. Keep last playing item.";
      }

      @Nullable @Override public ToroPlayer findBestPlayer(List<ToroPlayer> candidates) {
        if (candidates == null || candidates.size() < 1) {
          return null;
        }

        // Sort candidates by the visible offset
        Collections.sort(candidates, new Comparator<ToroPlayer>() {
          @Override public int compare(ToroPlayer lhs, ToroPlayer rhs) {
            return Float.compare(rhs.visibleAreaOffset(), lhs.visibleAreaOffset());
          }
        });

        return candidates.get(0);
      }

      @Override public boolean allowsToPlay(ToroPlayer player, ViewParent parent) {
        return doAllowsToPlay(player, parent);
      }
    };

    /**
     * Scan top down of candidates, chose the first playable Video
     */
    public static final ToroStrategy FIRST_PLAYABLE_TOP_DOWN = new ToroStrategy() {
      @Override public String getDescription() {
        return "First playable item, top - down";
      }

      @Nullable @Override public ToroPlayer findBestPlayer(List<ToroPlayer> candidates) {
        if (candidates == null || candidates.size() < 1) {
          return null;
        }

        // 1. Sort candidates by the order of player
        Collections.sort(candidates, new Comparator<ToroPlayer>() {
          @Override public int compare(ToroPlayer lhs, ToroPlayer rhs) {
            return lhs.getPlayOrder() - rhs.getPlayOrder();
          }
        });

        return candidates.get(0);
      }

      @Override public boolean allowsToPlay(ToroPlayer player, ViewParent parent) {
        return doAllowsToPlay(player, parent);
      }
    };

    /**
     * Scan top down (by layout direction) of candidates, chose the first playable Video. But if
     * current player is still playable, but not on the top, we keep using it
     */
    public static final ToroStrategy FIRST_PLAYABLE_TOP_DOWN_KEEP_LAST = new ToroStrategy() {

      @Override public String getDescription() {
        return "First playable item, top - down. Keep last playing item.";
      }

      @Nullable @Override public ToroPlayer findBestPlayer(List<ToroPlayer> candidates) {
        if (candidates == null || candidates.size() < 1) {
          return null;
        }

        return candidates.get(0);
      }

      @Override public boolean allowsToPlay(ToroPlayer player, ViewParent parent) {
        return doAllowsToPlay(player, parent);
      }
    };
  }

  /**
   * @hide
   */
  @SuppressWarnings("WeakerAccess") //
  static boolean doAllowsToPlay(ToroPlayer player, ViewParent parent) {
    Rect windowRect = new Rect();
    Rect parentRect = new Rect();
    if (parent instanceof View) {
      // 1. Get Window's vision from parent
      ((View) parent).getWindowVisibleDisplayFrame(windowRect);
      // 2. Get parent's global rect
      ((View) parent).getGlobalVisibleRect(parentRect, null);
    }
    // 3. Get player global rect
    View videoView = player.getPlayerView();
    Rect videoRect = new Rect();
    // Headache !!!
    int[] screenLoc = new int[2];
    videoView.getLocationOnScreen(screenLoc);
    videoRect.left += screenLoc[0];
    videoRect.right += screenLoc[0] + videoView.getWidth();
    videoRect.top += screenLoc[1];
    videoRect.bottom += screenLoc[1] + videoView.getHeight();

    // Condition: window contains parent, and parent contains Video or parent intersects Video
    return windowRect.contains(parentRect) && //
        (parentRect.contains(videoRect) || parentRect.intersect(videoRect));
  }

  // Centralize Video state callbacks

  void onVideoPrepared(@NonNull ToroPlayer player, @NonNull View itemView,
      @Nullable ViewParent parent) {
    if (!player.wantsToPlay() || !Toro.getStrategy().allowsToPlay(player, parent)) {
      return;
    }

    PlayerManager manager = getManager(parent);
    if (manager == null) {
      return;
    }

    // 1. Check if current manager wrapped this player
    if (player == manager.getPlayer()) {
      // player.isPlaying() is always false here
      manager.restorePlaybackState(player.getMediaId());
      manager.startPlayback();
    } else {
      // There is no current player, but this guy is prepared, so let's him go ...
      if (manager.getPlayer() == null) {
        // ... if it's possible
        if (player.wantsToPlay() && Toro.getStrategy().allowsToPlay(player, parent)) {
          manager.setPlayer(player);
          // player.isPrepared() is always true here
          manager.restorePlaybackState(player.getMediaId());
          manager.startPlayback();
        }
      }
    }
  }

  void onPlaybackCompletion(@NonNull ToroPlayer player) {
    // 1. Internal jobs
    PlayerManager manager = null;
    for (PlayerManager playerManager : sInstance.managers.values()) {
      if (player == playerManager.getPlayer()) {
        manager = playerManager;
        break;
      }
    }

    // Update video position as 0
    if (manager != null) {
      manager.savePlaybackState(player.getMediaId(), 0L, player.getDuration());
    }
  }

  boolean onPlaybackError(@NonNull ToroPlayer player, @NonNull Exception error) {
    for (PlayerManager manager : sInstance.managers.values()) {
      if (player.equals(manager.getPlayer())) {
        manager.savePlaybackState(player.getMediaId(), 0L, player.getDuration());
        manager.pausePlayback();
      }
    }

    return true;
  }

  // Update to correctly support API 24+
  private void dispatchOnActivityInactive(Activity activity) {
    for (Map.Entry<RecyclerView, PlayerManager> entry : managers.entrySet()) {
      if (entry.getKey().getContext() == activity) {
        PlayerManager manager = entry.getValue();
        if (manager.getPlayer() != null) {
          if (manager.getPlayer().isPlaying()) {
            manager.savePlaybackState(manager.getPlayer().getMediaId(),
                manager.getPlayer().getCurrentPosition(), manager.getPlayer().getDuration());
            manager.pausePlayback();
          }
          manager.getPlayer().releasePlayer();
          manager.getPlayer().onActivityInactive();
        }
      }
    }
  }

  private void dispatchOnActivityActive(Activity activity) {
    for (Map.Entry<RecyclerView, PlayerManager> entry : managers.entrySet()) {
      if (entry.getKey().getContext() == activity) {  // reference equality
        PlayerManager manager = entry.getValue();
        if (manager.getPlayer() != null) {
          manager.getPlayer().onActivityActive();
          if (!manager.getPlayer().isPrepared()) {
            manager.getPlayer().preparePlayer(false);
          } else {
            manager.restorePlaybackState(manager.getPlayer().getMediaId());
            manager.startPlayback();
          }
        }
      }
    }
  }
}
