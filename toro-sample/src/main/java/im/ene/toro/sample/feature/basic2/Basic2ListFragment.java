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

package im.ene.toro.sample.feature.basic2;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import im.ene.toro.OrderedPlaybackStrategy;
import im.ene.toro.Toro;
import im.ene.toro.ToroStrategy;
import im.ene.toro.sample.BaseToroFragment;
import im.ene.toro.sample.R;

/**
 * Created by eneim on 6/30/16.
 *
 * Basic sample 2 introduces <b>Ordered</b> playback list. i.e Videos are played from the very
 * first
 * Video instead of the first prepared Video. First Video order/position is provided by Adapter.
 *
 * In this sample, we manipulate our global {@link ToroStrategy} to make it be able to listen to
 * our Playback order. See {@link Basic2ListFragment#onAttach(Context)}
 */
public class Basic2ListFragment extends BaseToroFragment {

  protected RecyclerView mRecyclerView;
  protected Basic2Adapter mAdapter;

  public static Basic2ListFragment newInstance() {
    return new Basic2ListFragment();
  }

  // Restore in onDetach to prevent the playback strategy on other places.
  ToroStrategy strategyToRestore;
  // To tell Toro's strategy which is the Video to play first.
  int firstVideoPosition;

  @Override public void onAttach(Context context) {
    super.onAttach(context);
    strategyToRestore = Toro.getStrategy();
  }

  @Override public void onDetach() {
    // Restore to old Strategy.
    Toro.setStrategy(strategyToRestore);
    super.onDetach();
  }

  @Nullable @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    return inflater.inflate(R.layout.generic_recycler_view, container, false);
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2) @Override
  public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    mRecyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
    RecyclerView.LayoutManager layoutManager = getLayoutManager();
    mRecyclerView.setLayoutManager(layoutManager);
    if (layoutManager instanceof LinearLayoutManager) {
      mRecyclerView.addItemDecoration(new DividerItemDecoration(getContext(),
          ((LinearLayoutManager) layoutManager).getOrientation()));
    }

    mAdapter = new Basic2Adapter();
    // Do the magic.
    Toro.setStrategy(new OrderedPlaybackStrategy(mAdapter, strategyToRestore));

    mRecyclerView.setHasFixedSize(false);
    mRecyclerView.setAdapter(mAdapter);

    Toro.register(mRecyclerView);
  }

  @Override protected void dispatchFragmentActivated() {
    // Trick to force RecyclerView to scroll to first Video position. Note that it will trigger the
    // scroll every time the Fragment resumes, so comment out to disable.
    mRecyclerView.postDelayed(new Runnable() {
      @Override public void run() {
        if (mRecyclerView != null) {
          mRecyclerView.smoothScrollToPosition(firstVideoPosition);
        }
      }
    }, 200);
  }

  @Override protected void dispatchFragmentDeActivated() {

  }

  @Override public void onDestroyView() {
    super.onDestroyView();
    Toro.unregister(mRecyclerView);
  }

  RecyclerView.LayoutManager getLayoutManager() {
    return new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
  }
}
