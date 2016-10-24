/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.systemui.recents;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.SearchManager;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewStub;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.android.systemui.R;
import com.android.systemui.recents.misc.DebugTrigger;
import com.android.systemui.recents.misc.ReferenceCountedTrigger;
import com.android.systemui.recents.misc.SystemServicesProxy;
import com.android.systemui.recents.misc.Utilities;
import com.android.systemui.recents.model.RecentsTaskLoadPlan;
import com.android.systemui.recents.model.RecentsTaskLoader;
import com.android.systemui.recents.model.SpaceNode;
import com.android.systemui.recents.model.Task;
import com.android.systemui.recents.model.TaskStack;
import com.android.systemui.recents.views.DebugOverlayView;
import com.android.systemui.recents.views.RecentsView;
import com.android.systemui.recents.views.SystemBarScrimViews;
import com.android.systemui.recents.views.ViewAnimation;
import com.android.systemui.statusbar.BlurUtils;
import com.android.systemui.statusbar.DisplayUtils;
import com.android.systemui.statusbar.phone.NotificationPanelView;
import com.android.systemui.statusbar.phone.PhoneStatusBar;
import com.android.systemui.SystemUIApplication;

import java.lang.reflect.Field;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * The main Recents activity that is started from AlternateRecentsComponent.
 */
public class RecentsActivity extends Activity implements RecentsView.RecentsViewCallbacks,
        RecentsAppWidgetHost.RecentsAppWidgetHostCallbacks,
        DebugOverlayView.DebugOverlayViewCallbacks {

    private static final HashMap<String, Field> fieldCache = new HashMap<String, Field>();
    RecentsConfiguration mConfig;
    long mLastTabKeyEventTime;

    // Top level views
    RecentsView mRecentsView;
    SystemBarScrimViews mScrimViews;
    ViewStub mEmptyViewStub;
    ViewStub mDebugOverlayStub;
    View mEmptyView;
    DebugOverlayView mDebugOverlay;

    public static boolean mBlurredRecentAppsEnabled;

    private static int mBlurScale;
    private static int mBlurRadius;
    private static Context mContext;
    private static BlurUtils mBlurUtils;
    private static ColorFilter mColorFilter;
    private static int mBlurDarkColorFilter;
    private static int mBlurMixedColorFilter;
    private static int mBlurLightColorFilter;
    private static RecentsActivity mRecentsActivity;
    private static FrameLayout mRecentsActivityRootView;

    public int[] id = {0, R.anim.bounce, R.anim.slow_fade_in, R.anim.grow_from_top, R.anim.grow_from_center, R.anim.grow_from_bottom,
        R.anim.grow_from_left, R.anim.grow_from_right,  R.anim.push_down_in, R.anim.push_up_in, R.anim.push_left_in,
        R.anim.push_right_in, R.anim.last_app_in, R.anim.rotate, R.anim.turn_in, R.anim.zoom_in};

    // Search AppWidget
    RecentsAppWidgetHost mAppWidgetHost;
    AppWidgetProviderInfo mSearchAppWidgetInfo;
    AppWidgetHostView mSearchAppWidgetHostView;

    // Runnables to finish the Recents activity
    FinishRecentsRunnable mFinishLaunchHomeRunnable;

    public static void startBlurTask() {

        if (mRecentsActivityRootView != null)
            mRecentsActivityRootView.setBackground(null);

        if (!mBlurredRecentAppsEnabled)
            return;

        BlurTask.setBlurTaskCallback(new BlurUtils.BlurTaskCallback() {

            @Override
            public void blurTaskDone(final Bitmap blurredBitmap) {

                if (blurredBitmap != null) {
                    if (mRecentsActivityRootView != null) {
                        mRecentsActivityRootView.post(new Runnable() {
                            @Override
                            public void run() {
                                BitmapDrawable blurredDrawable = new BitmapDrawable(blurredBitmap);

                                blurredDrawable.setColorFilter(mColorFilter);

                                mRecentsActivityRootView.setBackground(blurredDrawable);
                            }
                        });
                    }
                }
            }

            @Override
            public void dominantColor(int color) {
                double lightness = DisplayUtils.getColorLightness(color);

                if (lightness >= 0.0 && color <= 1.0) {
                    if (lightness <= 0.33) {
                        mColorFilter = new PorterDuffColorFilter(mBlurLightColorFilter, PorterDuff.Mode.MULTIPLY);

                    } else if (lightness >= 0.34 && lightness <= 0.66) {
                        mColorFilter = new PorterDuffColorFilter(mBlurMixedColorFilter, PorterDuff.Mode.MULTIPLY);

                    } else if (lightness >= 0.67 && lightness <= 1.0) {
                        mColorFilter = new PorterDuffColorFilter(mBlurDarkColorFilter, PorterDuff.Mode.MULTIPLY);

                    }

                } else {
                    mColorFilter = new PorterDuffColorFilter(mBlurMixedColorFilter, PorterDuff.Mode.MULTIPLY);
                }
            }
        });

        BlurTask.setBlurEngine(BlurUtils.BlurEngine.RenderScriptBlur);

        new BlurTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

    }

    public static void onConfigurationChanged() {
        RecentsActivity.startBlurTask();
    }

    public static class BlurTask extends AsyncTask<Void, Void, Bitmap> {
        private static int[] mScreenDimens;
        private static Bitmap mScreenBitmap;
        private static BlurUtils.BlurEngine mBlurEngine;
        private static BlurUtils.BlurTaskCallback mCallback;

        public static void setBlurEngine(BlurUtils.BlurEngine blurEngine) {
            mBlurEngine = blurEngine;
        }

        private Bitmap drawableToBitmap(Drawable drawable) {
            Bitmap bitmap = null;
            if (drawable instanceof BitmapDrawable) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
                if(bitmapDrawable.getBitmap() != null) {
                    return bitmapDrawable.getBitmap();
                }
            }
            if(drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
                bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); // Single color bitmap will be created of 1x1 pixel
            } else {
                bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            }
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bitmap;
        }

        public static void setBlurTaskCallback(BlurUtils.BlurTaskCallback callBack) {
            mCallback = callBack;
        }

        public static int[] getRealScreenDimensions() {
            return mScreenDimens;
        }

        public static Bitmap getLastBlurredBitmap() {
            return mScreenBitmap;
        }

        @Override
        protected void onPreExecute() {
            mScreenDimens = DisplayUtils.getRealScreenDimensions(mContext);

            WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
            DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
            float screenh = dm.heightPixels;
            float screenw = dm.widthPixels;
            float bmheight = screenh;
            float bmwidth = screenw;
            float bmratio = 1;
            Bitmap bback = drawableToBitmap(wallpaperManager.getDrawable());
            float bbh = bback.getHeight();
            float bbw = bback.getWidth();
            if (bmheight > bbh) {
                bmratio = bbh/bmheight;
                bmheight = (bmratio*screenh);
            }
            bmwidth = (bmwidth*bmratio);
            if (bmwidth > bbw) {
                bmratio = bbw/screenw;
                bmwidth = (bmratio*screenw);
                bmheight = (bmratio*screenh);
            }
            Bitmap mScreenBitmap2 = Bitmap.createBitmap(bback, 0, 0, (int)bmwidth, (int)bmheight);
            mScreenBitmap = Bitmap.createScaledBitmap(
                    mScreenBitmap2, (int)(bmwidth / 20), (int)(bmheight / 20), false);
        }

        @Override
        protected Bitmap doInBackground(Void... arg0) {
            try {
                if (mScreenBitmap == null)
                    return null;

                mCallback.dominantColor(DisplayUtils.getDominantColorByPixelsSampling(mScreenBitmap, 10, 10));

                if (mBlurEngine == BlurUtils.BlurEngine.RenderScriptBlur) {
                    mScreenBitmap = mBlurUtils.renderScriptBlur(mScreenBitmap, mBlurRadius);

                } else if (mBlurEngine == BlurUtils.BlurEngine.StackBlur) {
                    mScreenBitmap = mBlurUtils.stackBlur(mScreenBitmap, mBlurRadius);

                } else if (mBlurEngine == BlurUtils.BlurEngine.FastBlur) {
                    mBlurUtils.fastBlur(mScreenBitmap, mBlurRadius);
                }
                return mScreenBitmap;

            } catch (OutOfMemoryError e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null) {
                mCallback.blurTaskDone(bitmap);

            } else {
                mCallback.blurTaskDone(null);
            }
        }
    }

    private PhoneStatusBar mStatusBar;
    private ReferenceCountedTrigger mExitTrigger;

    /**
     * A common Runnable to finish Recents either by calling finish() (with a custom animation) or
     * launching Home with some ActivityOptions.  Generally we always launch home when we exit
     * Recents rather than just finishing the activity since we don't know what is behind Recents in
     * the task stack.  The only case where we finish() directly is when we are cancelling the full
     * screen transition from the app.
     */
    class FinishRecentsRunnable implements Runnable {
        Intent mLaunchIntent;
        ActivityOptions mLaunchOpts;
        boolean mAbort = false;

        /**
         * Creates a finish runnable that starts the specified intent, using the given
         * ActivityOptions.
         */
        public FinishRecentsRunnable(Intent launchIntent, ActivityOptions opts) {
            mLaunchIntent = launchIntent;
            mLaunchOpts = opts;
        }

        public void setAbort(boolean run) {
            this.mAbort = run;
        }

        @Override
        public void run() {
            if (mAbort) {
                return;
            }
            // Finish Recents
            if (mLaunchIntent != null) {
                if (mLaunchOpts != null) {
                    startActivityAsUser(mLaunchIntent, mLaunchOpts.toBundle(), UserHandle.CURRENT);
                } else {
                    startActivityAsUser(mLaunchIntent, UserHandle.CURRENT);
                }
            } else {
                finish();
                overridePendingTransition(R.anim.recents_to_launcher_enter,
                        R.anim.recents_to_launcher_exit);
            }
        }
    }

    /**
     * Broadcast receiver to handle messages from AlternateRecentsComponent.
     */
    final BroadcastReceiver mServiceBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(AlternateRecentsComponent.ACTION_HIDE_RECENTS_ACTIVITY)) {
                if (intent.getBooleanExtra(AlternateRecentsComponent.EXTRA_TRIGGERED_FROM_ALT_TAB, false)) {
                    // If we are hiding from releasing Alt-Tab, dismiss Recents to the focused app
                    dismissRecentsToFocusedTaskOrHome(false);
                } else if (intent.getBooleanExtra(AlternateRecentsComponent.EXTRA_TRIGGERED_FROM_HOME_KEY, false)) {
                    // Otherwise, dismiss Recents to Home
                    dismissRecentsToHome(true);
                } else {
                    // Do nothing, another activity is being launched on top of Recents
                }
            } else if (action.equals(AlternateRecentsComponent.ACTION_TOGGLE_RECENTS_ACTIVITY)) {
                // If we are toggling Recents, then first unfilter any filtered stacks first
                dismissRecentsToFocusedTaskOrHome(true);
            } else if (action.equals(AlternateRecentsComponent.ACTION_START_ENTER_ANIMATION)) {
                // Trigger the enter animation
                onEnterAnimationTriggered();
                // Notify the fallback receiver that we have successfully got the broadcast
                // See AlternateRecentsComponent.onAnimationStarted()
                setResultCode(Activity.RESULT_OK);
            }
        }
    };

    /**
     * Broadcast receiver to handle messages from the system
     */
    final BroadcastReceiver mSystemBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                // When the screen turns off, dismiss Recents to Home
                dismissRecentsToHome(false);
            } else if (action.equals(SearchManager.INTENT_GLOBAL_SEARCH_ACTIVITY_CHANGED)) {
                // When the search activity changes, update the Search widget
                refreshSearchWidget();
            }
        }
    };

    /**
     * A custom debug trigger to listen for a debug key chord.
     */
    final DebugTrigger mDebugTrigger = new DebugTrigger(new Runnable() {
        @Override
        public void run() {
            onDebugModeTriggered();
        }
    });

    private static void recycle() {
        if (mRecentsActivityRootView == null)
            return;

        if (mRecentsActivityRootView.getBackground() != null) {
            Bitmap bitmap = ((BitmapDrawable) mRecentsActivityRootView.getBackground()).getBitmap();

            if (bitmap != null) {
                bitmap.recycle();
                bitmap = null;
            }
            mRecentsActivityRootView.setBackground(null);
        }
    }

    /** Updates the set of recent tasks */
    void updateRecentsTasks(Intent launchIntent) {
        // If AlternateRecentsComponent has preloaded a load plan, then use that to prevent
        // reconstructing the task stack
        RecentsTaskLoader loader = RecentsTaskLoader.getInstance();
        RecentsTaskLoadPlan plan = AlternateRecentsComponent.consumeInstanceLoadPlan();
        if (plan == null) {
            plan = loader.createLoadPlan(this);
        }

        // Start loading tasks according to the load plan
        if (plan.getTaskStack() == null) {
            loader.preloadTasks(plan, mConfig.launchedFromHome);
        }
        RecentsTaskLoadPlan.Options loadOpts = new RecentsTaskLoadPlan.Options();
        loadOpts.runningTaskId = mConfig.launchedToTaskId;
        loadOpts.numVisibleTasks = mConfig.launchedNumVisibleTasks;
        loadOpts.numVisibleTaskThumbnails = mConfig.launchedNumVisibleThumbnails;
        loader.loadTasks(this, plan, loadOpts);

        SpaceNode root = plan.getSpaceNode();
        ArrayList<TaskStack> stacks = root.getStacks();
        boolean hasTasks = root.hasTasks();
        if (hasTasks) {
            mRecentsView.setTaskStacks(stacks);
        }
        mConfig.launchedWithNoRecentTasks = !hasTasks;

        // Create the home intent runnable
        Intent homeIntent = new Intent(Intent.ACTION_MAIN, null);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        mFinishLaunchHomeRunnable = new FinishRecentsRunnable(homeIntent,
            ActivityOptions.makeCustomAnimation(this,
                mConfig.launchedFromSearchHome ? R.anim.recents_to_search_launcher_enter :
                        R.anim.recents_to_launcher_enter,
                    mConfig.launchedFromSearchHome ? R.anim.recents_to_search_launcher_exit :
                        R.anim.recents_to_launcher_exit));
        setImmersiveRecents();
        updatePreferences(mContext);

        // Mark the task that is the launch target
        int taskStackCount = stacks.size();
        if (mConfig.launchedToTaskId != -1) {
            for (int i = 0; i < taskStackCount; i++) {
                TaskStack stack = stacks.get(i);
                ArrayList<Task> tasks = stack.getTasks();
                int taskCount = tasks.size();
                for (int j = 0; j < taskCount; j++) {
                    Task t = tasks.get(j);
                    if (t.key.id == mConfig.launchedToTaskId) {
                        t.isLaunchTarget = true;
                        break;
                    }
                }
            }
        }

        boolean enableShakeCleanByUser = Settings.System.getInt(getContentResolver(),
            Settings.System.SHAKE_TO_CLEAN_RECENTS, 0) == 1;

        // Update the top level view's visibilities
        if (mConfig.launchedWithNoRecentTasks) {
            if (mEmptyView == null) {
                mEmptyView = mEmptyViewStub.inflate();
            }
            mRecentsView.enableShake(false);
            mEmptyView.setVisibility(View.VISIBLE);
            mEmptyView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dismissRecentsToHome(true);
                }
            });
            mRecentsView.setSearchBarVisibility(View.GONE);
            findViewById(R.id.floating_action_button).setVisibility(View.GONE);
        } else {
            if (mEmptyView != null) {
                mEmptyView.setVisibility(View.GONE);
                mEmptyView.setOnClickListener(null);
            }
            mRecentsView.enableShake(true && enableShakeCleanByUser);
            findViewById(R.id.floating_action_button).setVisibility(View.VISIBLE);
            boolean showSearchBar = Settings.System.getInt(getContentResolver(),
                       Settings.System.RECENTS_SHOW_SEARCH_BAR, 1) == 1;
            if (mRecentsView.hasSearchBar()) {
                if (showSearchBar) {
                    mRecentsView.setSearchBarVisibility(View.VISIBLE);
                } else {
                    mRecentsView.setSearchBarVisibility(View.GONE);
                }
            } else {
                if (showSearchBar) {
                    addSearchBarAppWidgetView();
                }
            }
        }

        // Animate the SystemUI scrims into view
        mScrimViews.prepareEnterRecentsAnimation();
    }

    /** Attempts to allocate and bind the search bar app widget */
    void bindSearchBarAppWidget() {
        if (Constants.DebugFlags.App.EnableSearchLayout) {
            SystemServicesProxy ssp = RecentsTaskLoader.getInstance().getSystemServicesProxy();

            // Reset the host view and widget info
            mSearchAppWidgetHostView = null;
            mSearchAppWidgetInfo = null;

            // Try and load the app widget id from the settings
            int appWidgetId = mConfig.searchBarAppWidgetId;
            if (appWidgetId >= 0) {
                mSearchAppWidgetInfo = ssp.getAppWidgetInfo(appWidgetId);
                if (mSearchAppWidgetInfo == null) {
                    // If there is no actual widget associated with that id, then delete it and
                    // prepare to bind another app widget in its place
                    ssp.unbindSearchAppWidget(mAppWidgetHost, appWidgetId);
                    appWidgetId = -1;
                }
            }

            // If there is no id, then bind a new search app widget
            if (appWidgetId < 0) {
                Pair<Integer, AppWidgetProviderInfo> widgetInfo =
                        ssp.bindSearchAppWidget(mAppWidgetHost);
                if (widgetInfo != null) {
                    // Save the app widget id into the settings
                    mConfig.updateSearchBarAppWidgetId(this, widgetInfo.first);
                    mSearchAppWidgetInfo = widgetInfo.second;
                }
            }
        }
    }

    /** Creates the search bar app widget view */
    void addSearchBarAppWidgetView() {
        if (Constants.DebugFlags.App.EnableSearchLayout) {
            int appWidgetId = mConfig.searchBarAppWidgetId;
            if (appWidgetId >= 0) {
                mSearchAppWidgetHostView = mAppWidgetHost.createView(this, appWidgetId,
                        mSearchAppWidgetInfo);
                Bundle opts = new Bundle();
                opts.putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
                        AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX);
                mSearchAppWidgetHostView.updateAppWidgetOptions(opts);
                // Set the padding to 0 for this search widget
                mSearchAppWidgetHostView.setPadding(0, 0, 0, 0);
                mRecentsView.setSearchBar(mSearchAppWidgetHostView);
            } else {
                mRecentsView.setSearchBar(null);
            }
        }
    }

    /** Dismisses recents if we are already visible and the intent is to toggle the recents view */
    boolean dismissRecentsToFocusedTaskOrHome(boolean checkFilteredStackState) {
        SystemServicesProxy ssp = RecentsTaskLoader.getInstance().getSystemServicesProxy();
        if (ssp.isRecentsTopMost(ssp.getTopMostTask(), null)) {
            // If we currently have filtered stacks, then unfilter those first
            if (checkFilteredStackState &&
                mRecentsView.unfilterFilteredStacks()) return true;
            // If we have a focused Task, launch that Task now
            if (mRecentsView.launchFocusedTask()) return true;
            // If we launched from Home, then return to Home
            if (mConfig.launchedFromHome) {
                dismissRecentsToHomeRaw(true);
                return true;
            }
            // Otherwise, try and return to the Task that Recents was launched from
            if (mRecentsView.launchPreviousTask()) return true;
            // If none of the other cases apply, then just go Home
            dismissRecentsToHomeRaw(true);
            return true;
        }
        return false;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus && mExitTrigger != null && mExitTrigger.getCount() > 0) {
            // we are animating recents out and the window has lost focus during the
            // animation. we need to stop everything we're doing now and get out
            // without any animations (since we were already animating)
            mFinishLaunchHomeRunnable.setAbort(true);
            finish();
            overridePendingTransition(0, 0);
        }
    }

    /** Dismisses Recents directly to Home. */
    void dismissRecentsToHomeRaw(boolean animated) {
        if (animated) {
            mExitTrigger = new ReferenceCountedTrigger(this,
                    null, mFinishLaunchHomeRunnable, null);
            mRecentsView.startExitToHomeAnimation(
                    new ViewAnimation.TaskViewExitContext(mExitTrigger));
        } else {
            mFinishLaunchHomeRunnable.run();
        }
    }

    /** Dismisses Recents directly to Home if we currently aren't transitioning. */
    boolean dismissRecentsToHome(boolean animated) {
        SystemServicesProxy ssp = RecentsTaskLoader.getInstance().getSystemServicesProxy();
        if (ssp.isRecentsTopMost(ssp.getTopMostTask(), null)) {
            // Return to Home
            dismissRecentsToHomeRaw(animated);
            return true;
        }
        return false;
    }

    /** Called with the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // For the non-primary user, ensure that the SystemServicesProxy and configuration is
        // initialized
        RecentsTaskLoader.initialize(this);
        SystemServicesProxy ssp = RecentsTaskLoader.getInstance().getSystemServicesProxy();
        mConfig = RecentsConfiguration.reinitialize(this, ssp);

        // Initialize the widget host (the host id is static and does not change)
        mAppWidgetHost = new RecentsAppWidgetHost(this, Constants.Values.App.AppWidgetHostId);

        // Set the Recents layout
        setContentView(R.layout.recents);
        mRecentsView = (RecentsView) findViewById(R.id.recents_view);
        mRecentsView.setCallbacks(this);
        mRecentsView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mEmptyViewStub = (ViewStub) findViewById(R.id.empty_view_stub);
        mDebugOverlayStub = (ViewStub) findViewById(R.id.debug_overlay_stub);
        mScrimViews = new SystemBarScrimViews(this, mConfig);
        mStatusBar = ((SystemUIApplication) getApplication())
                .getComponent(PhoneStatusBar.class);
        inflateDebugOverlay();

        // Bind the search app widget when we first start up
        bindSearchBarAppWidget();

        // Register the broadcast receiver to handle messages when the screen is turned off
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(SearchManager.INTENT_GLOBAL_SEARCH_ACTIVITY_CHANGED);
        registerReceiver(mSystemBroadcastReceiver, filter);

        // Private API calls to make the shadows look better
        try {
            Utilities.setShadowProperty("ambientRatio", String.valueOf(1.5f));
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        try {
            RecentsView mRecentsView = (RecentsView) getObjectField(this, "mRecentsView");

            mRecentsActivityRootView = (FrameLayout) mRecentsView.getParent();

            Bitmap lastBlurredBitmap = BlurTask.getLastBlurredBitmap();

            if ((mBlurredRecentAppsEnabled) && (lastBlurredBitmap != null)) {

                BitmapDrawable blurredDrawable = new BitmapDrawable(lastBlurredBitmap);
                blurredDrawable.setColorFilter(mColorFilter);
                mRecentsActivityRootView.setBackground(blurredDrawable);
            }
        } catch (Exception e) {
        }
    }

    //#################################################################################################
    public static Object getObjectField(Object obj, String fieldName) {
        try {
            return findField(obj.getClass(), fieldName).get(obj);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    /**
     * Look up a field in a class and set it to accessible. The result is cached.
     * If the field was not found, a {@link NoSuchFieldError} will be thrown.
     */
    public static Field findField(Class<?> clazz, String fieldName) {
        StringBuilder sb = new StringBuilder(clazz.getName());
        sb.append('#');
        sb.append(fieldName);
        String fullFieldName = sb.toString();

        if (fieldCache.containsKey(fullFieldName)) {
            Field field = fieldCache.get(fullFieldName);
            if (field == null)
                throw new NoSuchFieldError(fullFieldName);
            return field;
        }

        try {
            Field field = findFieldRecursiveImpl(clazz, fieldName);
            field.setAccessible(true);
            fieldCache.put(fullFieldName, field);
            return field;
        } catch (NoSuchFieldException e) {
            fieldCache.put(fullFieldName, null);
            throw new NoSuchFieldError(fullFieldName);
        }
    }

    private static Field findFieldRecursiveImpl(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            while (true) {
                clazz = clazz.getSuperclass();
                if (clazz == null || clazz.equals(Object.class))
                    break;

                try {
                    return clazz.getDeclaredField(fieldName);
                } catch (NoSuchFieldException ignored) {}
            }
            throw e;
        }
    }

    /** Inflates the debug overlay if debug mode is enabled. */
    void inflateDebugOverlay() {
        if (!Constants.DebugFlags.App.EnableDebugMode) return;

        if (mConfig.debugModeEnabled && mDebugOverlay == null) {
            // Inflate the overlay and seek bars
            mDebugOverlay = (DebugOverlayView) mDebugOverlayStub.inflate();
            mDebugOverlay.setCallbacks(this);
            mRecentsView.setDebugOverlay(mDebugOverlay);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        // Clear any debug rects
        if (mDebugOverlay != null) {
            mDebugOverlay.clear();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        RecentsTaskLoader loader = RecentsTaskLoader.getInstance();
        SystemServicesProxy ssp = loader.getSystemServicesProxy();
        AlternateRecentsComponent.notifyVisibilityChanged(this, ssp, true);

        // Register the broadcast receiver to handle messages from our service
        IntentFilter filter = new IntentFilter();
        filter.addAction(AlternateRecentsComponent.ACTION_HIDE_RECENTS_ACTIVITY);
        filter.addAction(AlternateRecentsComponent.ACTION_TOGGLE_RECENTS_ACTIVITY);
        filter.addAction(AlternateRecentsComponent.ACTION_START_ENTER_ANIMATION);
        registerReceiver(mServiceBroadcastReceiver, filter);

        // Register any broadcast receivers for the task loader
        loader.registerReceivers(this, mRecentsView);

        // Update the recent tasks
        updateRecentsTasks(getIntent());

        // If this is a new instance from a configuration change, then we have to manually trigger
        // the enter animation state
        if (mConfig.launchedHasConfigurationChanged) {
            onEnterAnimationTriggered();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        mExitTrigger = null;

        RecentsTaskLoader loader = RecentsTaskLoader.getInstance();
        SystemServicesProxy ssp = loader.getSystemServicesProxy();
        AlternateRecentsComponent.notifyVisibilityChanged(this, ssp, false);

        // Notify the views that we are no longer visible
        mRecentsView.onRecentsHidden();

        // Unregister the RecentsService receiver
        unregisterReceiver(mServiceBroadcastReceiver);

        // Unregister any broadcast receivers for the task loader
        loader.unregisterReceivers();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Unregister the system broadcast receivers
        unregisterReceiver(mSystemBroadcastReceiver);

        // Stop listening for widget package changes if there was one bound
        mAppWidgetHost.stopListening();
    }

    public void onEnterAnimationTriggered() {
        // Try and start the enter animation (or restart it on configuration changed)
        ReferenceCountedTrigger t = new ReferenceCountedTrigger(this, null, null, null);
        ViewAnimation.TaskViewEnterContext ctx = new ViewAnimation.TaskViewEnterContext(t);
        mRecentsView.startEnterRecentsAnimation(ctx);
        int a = Settings.System.getInt(this.getContentResolver(), Settings.System.RECENTS_ENTER_ANIMATIONS, 0);
        int anim = id[a];
        if (anim == 0) {
            return;
        } else {
            mRecentsView.startAnimation(AnimationUtils.loadAnimation(this, anim));
        }
        if (mConfig.searchBarAppWidgetId >= 0) {
            final WeakReference<RecentsAppWidgetHost.RecentsAppWidgetHostCallbacks> cbRef =
                    new WeakReference<RecentsAppWidgetHost.RecentsAppWidgetHostCallbacks>(
                            RecentsActivity.this);
            ctx.postAnimationTrigger.addLastDecrementRunnable(new Runnable() {
                @Override
                public void run() {
                    // Start listening for widget package changes if there is one bound
                    RecentsAppWidgetHost.RecentsAppWidgetHostCallbacks cb = cbRef.get();
                    if (cb != null) {
                        mAppWidgetHost.startListening(cb);
                    }
                }
            });
        }

        // Animate the SystemUI scrim views
        mScrimViews.startEnterRecentsAnimation();
    }

    @Override
    public void onTrimMemory(int level) {
        RecentsTaskLoader loader = RecentsTaskLoader.getInstance();
        if (loader != null) {
            loader.onTrimMemory(level);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_TAB: {
                boolean hasRepKeyTimeElapsed = (SystemClock.elapsedRealtime() -
                        mLastTabKeyEventTime) > mConfig.altTabKeyDelay;
                if (event.getRepeatCount() <= 0 || hasRepKeyTimeElapsed) {
                    // Focus the next task in the stack
                    final boolean backward = event.isShiftPressed();
                    mRecentsView.focusNextTask(!backward);
                    mLastTabKeyEventTime = SystemClock.elapsedRealtime();
                }
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_UP: {
                mRecentsView.focusNextTask(true);
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_DOWN: {
                mRecentsView.focusNextTask(false);
                return true;
            }
            case KeyEvent.KEYCODE_DEL:
            case KeyEvent.KEYCODE_FORWARD_DEL: {
                mRecentsView.dismissFocusedTask();
                return true;
            }
            default:
                break;
        }
        // Pass through the debug trigger
        mDebugTrigger.onKeyEvent(keyCode);
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onUserInteraction() {
        mRecentsView.onUserInteraction();
    }

    @Override
    public void onBackPressed() {
        // Test mode where back does not do anything
        if (mConfig.debugModeEnabled) return;

        // Dismiss Recents to the focused Task or Home
        dismissRecentsToFocusedTaskOrHome(true);

        // Hide clear recents button before return to home
        mRecentsView.startHideClearRecentsButtonAnimation();
    }

    /** Called when debug mode is triggered */
    public void onDebugModeTriggered() {
        if (mConfig.developerOptionsEnabled) {
            SharedPreferences settings = getSharedPreferences(getPackageName(), 0);
            if (settings.getBoolean(Constants.Values.App.Key_DebugModeEnabled, false)) {
                // Disable the debug mode
                settings.edit().remove(Constants.Values.App.Key_DebugModeEnabled).apply();
                mConfig.debugModeEnabled = false;
                inflateDebugOverlay();
                if (mDebugOverlay != null) {
                    mDebugOverlay.disable();
                }
            } else {
                // Enable the debug mode
                settings.edit().putBoolean(Constants.Values.App.Key_DebugModeEnabled, true).apply();
                mConfig.debugModeEnabled = true;
                inflateDebugOverlay();
                if (mDebugOverlay != null) {
                    mDebugOverlay.enable();
                }
            }
            Toast.makeText(this, "Debug mode (" + Constants.Values.App.DebugModeVersion + ") " +
                (mConfig.debugModeEnabled ? "Enabled" : "Disabled") + ", please restart Recents now",
                Toast.LENGTH_SHORT).show();
        }
    }

    private void setImmersiveRecents() {
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int immersiveRecents = isPrimary ? getImmersiveRecents() : 0;

        if (immersiveRecents == 0) {
         // default AOSP action
        }
        if (immersiveRecents == 1) {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
        if (immersiveRecents == 2) {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
        if (immersiveRecents == 3) {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private int getImmersiveRecents() {
        return Settings.System.getInt(getContentResolver(),
                Settings.System.IMMERSIVE_RECENTS, 0);
    }

    /**** RecentsView.RecentsViewCallbacks Implementation ****/

    @Override
    public void onExitToHomeAnimationTriggered() {
        // Animate the SystemUI scrim views out
        mScrimViews.startExitRecentsAnimation();
    }

    @Override
    public void onTaskViewClicked() {
    }

    @Override
    public void onTaskLaunchFailed() {
        // Return to Home
        dismissRecentsToHomeRaw(true);

        // Hide clear recents button before return to home
        mRecentsView.startHideClearRecentsButtonAnimation();
    }

    @Override
    public void onAllTaskViewsDismissed() {
        mFinishLaunchHomeRunnable.run();
    }

    @Override
    public void onScreenPinningRequest() {
        if (mStatusBar != null) {
            mStatusBar.showScreenPinningRequest(false);
        }
    }

    /**** RecentsAppWidgetHost.RecentsAppWidgetHostCallbacks Implementation ****/

    @Override
    public void refreshSearchWidget() {
        bindSearchBarAppWidget();
        addSearchBarAppWidgetView();
    }

    /**** DebugOverlayView.DebugOverlayViewCallbacks ****/

    @Override
    public void onPrimarySeekBarChanged(float progress) {
        // Do nothing
    }

    @Override
    public void onSecondarySeekBarChanged(float progress) {
        // Do nothing
    }

    public static void init(Context context) {
        mContext = context;
        mBlurUtils = new BlurUtils(mContext);
    }

    public static void updatePreferences(Context mContext) {
        mBlurScale = 20;
        mBlurRadius = 3;
        mBlurDarkColorFilter = Color.LTGRAY;
        mBlurMixedColorFilter = Color.GRAY;
        mBlurLightColorFilter = Color.DKGRAY;
        try {
            mBlurredRecentAppsEnabled = (Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.RECENT_APPS_ENABLED_PREFERENCE_KEY, 0) == 1);
        } catch(Exception e) {}
    }
}
