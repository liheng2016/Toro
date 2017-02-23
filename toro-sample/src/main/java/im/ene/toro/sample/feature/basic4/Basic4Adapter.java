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

package im.ene.toro.sample.feature.basic4;

import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import im.ene.toro.BaseAdapter;
import im.ene.toro.OrderedPlayback;
import im.ene.toro.ToroAdapter;
import im.ene.toro.sample.data.SimpleVideoObject;

/**
 * Created by eneim on 6/29/16.
 */
public class Basic4Adapter extends BaseAdapter<ToroAdapter.ViewHolder> implements OrderedPlayback {

  static int TYPE_VIDEO = 1;

  static int TYPE_NORMAL = 2;

  private LayoutInflater inflater;

  public Basic4Adapter() {
    super();
  }

  @Override public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    final View view;
    final ViewHolder viewHolder;
    if (inflater == null) {
      inflater = LayoutInflater.from(parent.getContext());
    }

    if (viewType == TYPE_VIDEO) {
      view = inflater.inflate(Basic4VideoViewHolder.LAYOUT_RES, parent, false);
      viewHolder = new Basic4VideoViewHolder(view);
    } else {
      view = inflater.inflate(Basic4NormalViewHolder.LAYOUT_RES, parent, false);
      viewHolder = new Basic4NormalViewHolder(view);
    }

    return viewHolder;
  }

  // Comment out because parent class already deal with this.
  // TODO Un-comment for custom behaviour
  //@Override public void onBindViewHolder(ToroAdapter.ViewHolder holder, int position) {
  //  super.onBindViewHolder(holder, position);
  //}

  @Nullable @Override protected Object getItem(int position) {
    //if (position % 3 == 0) {
    //  return new SimpleVideoObject("http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4");
    //} else {
    //  return new SimpleObject();
    //}
    return new SimpleVideoObject("http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4");
  }

  @Override public int getItemViewType(int position) {
    return getItem(position) instanceof SimpleVideoObject ? TYPE_VIDEO : TYPE_NORMAL;
  }

  @Override public int getItemCount() {
    return 512;
  }

  @Override public void remove() throws Exception {
    super.remove();
    this.inflater = null;
  }

  @Override public int getFirstPlayableAdapterPosition() {
    return 0;
  }
}
