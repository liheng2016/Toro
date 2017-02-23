/*
 * Copyright 2017 eneim@Eneim Labs, nam@ene.im
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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.ViewParent;
import java.util.List;

/**
 * Created by eneim on 2/21/17.
 *
 * @since 2.2.0
 */

public final class OrderedPlaybackStrategy implements ToroStrategy {

  @NonNull private final OrderedPlayback orderedPlayback;

  @NonNull private final ToroStrategy strategy;

  private boolean firstPlayerDone = false;

  public OrderedPlaybackStrategy(@NonNull OrderedPlayback orderedPlayback,
      @NonNull ToroStrategy strategy) {
    this.orderedPlayback = orderedPlayback;
    this.strategy = strategy;
  }

  @SuppressWarnings("unused") public OrderedPlaybackStrategy(OrderedPlayback orderedPlayback) {
    this(orderedPlayback, Toro.Strategies.FIRST_PLAYABLE_TOP_DOWN);
  }

  @Override public String getDescription() {
    return "Playback Strategy by order";
  }

  @Nullable @Override public ToroPlayer findBestPlayer(List<ToroPlayer> candidates) {
    return strategy.findBestPlayer(candidates);
  }

  @Override public boolean allowsToPlay(ToroPlayer player, ViewParent parent) {
    boolean allowToPlay = (firstPlayerDone || //
        player.getPlayOrder() == orderedPlayback.getFirstPlayableAdapterPosition()) &&  //
        strategy.allowsToPlay(player, parent);

    // A work-around to keep track of first video on top.
    firstPlayerDone = firstPlayerDone ||  //
        player.getPlayOrder() == orderedPlayback.getFirstPlayableAdapterPosition();

    return allowToPlay;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof OrderedPlaybackStrategy)) return false;

    OrderedPlaybackStrategy that = (OrderedPlaybackStrategy) o;

    if (!orderedPlayback.equals(that.orderedPlayback)) return false;
    return strategy.equals(that.strategy);
  }

  @Override public int hashCode() {
    int result = orderedPlayback.hashCode();
    result = 31 * result + strategy.hashCode();
    return result;
  }
}
