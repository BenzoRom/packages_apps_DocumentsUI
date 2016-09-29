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

package com.android.documentsui;

import android.content.ClipData;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.DragEvent;
import android.view.View;
import android.view.View.OnDragListener;

import com.android.documentsui.ItemDragListener.DragHost;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Timer;
import java.util.TimerTask;

import javax.annotation.Nullable;

/**
 * An {@link OnDragListener} that adds support for "spring loading views". Use this when you want
 * items to pop-open when user hovers on them during a drag n drop.
 */
public class ItemDragListener<H extends DragHost> implements OnDragListener {

    private static final String TAG = "ItemDragListener";

    @VisibleForTesting
    static final int SPRING_TIMEOUT = 1500;

    protected final H mDragHost;
    private final Timer mHoverTimer;

    public ItemDragListener(H dragHost) {
        this(dragHost, new Timer());
    }

    @VisibleForTesting
    protected ItemDragListener(H dragHost, Timer timer) {
        mDragHost = dragHost;
        mHoverTimer = timer;
    }

    @Override
    public boolean onDrag(final View v, DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return true;
            case DragEvent.ACTION_DRAG_ENTERED:
                handleEnteredEvent(v, event);
                return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                handleLocationEvent(v, event.getX(), event.getY());
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
            case DragEvent.ACTION_DRAG_ENDED:
                handleExitedEndedEvent(v);
                return true;
            case DragEvent.ACTION_DROP:
                return handleDropEvent(v, event);
        }

        return false;
    }

    private void handleEnteredEvent(View v, DragEvent event) {
        mDragHost.onDragEntered(v);
        @Nullable TimerTask task = createOpenTask(v, event);
        if (task == null) {
            return;
        }
        mDragHost.setDropTargetHighlight(v, true);
        v.setTag(R.id.drag_hovering_tag, task);
        mHoverTimer.schedule(task, SPRING_TIMEOUT);
    }

    private void handleLocationEvent(View v, float x, float y) {
        Drawable background = v.getBackground();
        if (background != null) {
            background.setHotspot(x, y);
        }
    }

    private void handleExitedEndedEvent(View v) {
        mDragHost.setDropTargetHighlight(v, false);

        TimerTask task = (TimerTask) v.getTag(R.id.drag_hovering_tag);
        if (task != null) {
            task.cancel();
        }
    }

    private boolean handleDropEvent(View v, DragEvent event) {
        ClipData clipData = event.getClipData();
        if (clipData == null) {
            Log.w(TAG, "Received invalid drop event with null clipdata. Ignoring.");
            return false;
        }

        return handleDropEventChecked(v, event);
    }

    /**
     * Sub-classes such as {@link DirectoryDragListener} can override this method and return null.
     */
    public @Nullable TimerTask createOpenTask(final View v, DragEvent event) {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                mDragHost.runOnUiThread(() -> {
                    mDragHost.onViewHovered(v);
                });
            }
        };
        return task;
    }

    /**
     * Handles a drop event. Override it if you want to do something on drop event. It's called when
     * {@link DragEvent#ACTION_DROP} happens. ClipData in DragEvent is guaranteed not null.
     *
     * @param v The view where user drops.
     * @param event the drag event.
     * @return true if this event is consumed; false otherwise
     */
    public boolean handleDropEventChecked(View v, DragEvent event) {
        return false; // we didn't handle the drop
    }

    /**
     * An interface {@link ItemDragListener} uses to make some callbacks.
     */
    public interface DragHost {

        /**
         * Runs this runnable in main thread.
         */
        void runOnUiThread(Runnable runnable);

        /**
         * Highlights/unhighlights the view to visually indicate this view is being hovered.
         * @param v the view being hovered
         * @param highlight true if highlight the view; false if unhighlight it
         */
        void setDropTargetHighlight(View v, boolean highlight);

        /**
         * Notifies hovering timeout has elapsed
         * @param v the view being hovered
         */
        void onViewHovered(View v);

        /**
         * Notifies right away when drag shadow enters the view
         * @param v the view which drop shadow just entered
         */
        void onDragEntered(View v);
    }
}
