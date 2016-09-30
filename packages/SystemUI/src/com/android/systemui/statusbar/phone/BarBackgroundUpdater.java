/*
 * Copyright (C) 2016 ParanoidSHIT Project
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

package com.android.systemui.statusbar.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.WindowManager;

import com.android.systemui.statusbar.phone.BarBackgroundUpdaterNative;

import java.lang.Math;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class BarBackgroundUpdater {
    private final static boolean DEBUG_ALL = false;
    private final static String LOG_TAG = BarBackgroundUpdater.class.getSimpleName();

    private final static boolean DEBUG_COLOR_CHANGE = DEBUG_ALL || false;
    private final static boolean DEBUG_EXCESSIVE_DELAY = DEBUG_ALL || false;
    private final static boolean DEBUG_FLOOD_ALL_DELAY = DEBUG_ALL || false;
    private static long sMinDelay = 5; // time to enforce between the screenshots

    private static boolean PAUSED = true;

    private final static BroadcastReceiver RECEIVER = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized(BarBackgroundUpdater.class) {
                if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                    pause();
                } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                    resume();
                }
            }
        }

    };

    private static boolean expanded(){
        return PhoneStatusBar.mExpandedVisible;
    }

    private final static Thread THREAD = new Thread(new Runnable() {

        @Override
        public void run() {
            while (true) {
                final long now = System.currentTimeMillis();

                if (PAUSED) {
                    // we have been told to do nothing; wait for notify to continue
                    synchronized (BarBackgroundUpdater.class) {
                        try {
                            BarBackgroundUpdater.class.wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                    }

                    continue;
                }

                if (mStatusEnabled || mNavigationEnabled||mHeaderEnabled) {
                    final Context context = mContext;

                    if (context == null) {
                        // we haven't been initiated yet; retry in a bit

                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            return;
                        }

                        continue;
                    }

                    final WindowManager wm =
                        (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

                    final int rotation = wm.getDefaultDisplay().getRotation();
                    final boolean isLandscape = rotation == Surface.ROTATION_90 ||
                        rotation == Surface.ROTATION_270;

                    final Resources r = context.getResources();
                    final int statusBarHeight = r.getDimensionPixelSize(
                        r.getIdentifier("status_bar_height", "dimen", "android"));
                    final int navigationBarHeight = r.getDimensionPixelSize(
                        r.getIdentifier("navigation_bar_height" + (isLandscape ?
                            "_landscape" : ""), "dimen", "android"));

                    if (navigationBarHeight <= 0 && mNavigationEnabled) {
                        // the navigation bar height is not positive - no dynamic navigation bar
                        Settings.System.putInt(context.getContentResolver(),
                            Settings.System.DYNAMIC_NAVIGATION_BAR_STATE, 0);

                        // configuration has changed - abort and retry in a bit
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            return;
                        }

                        continue;
                    }

                    final int[] colors = BarBackgroundUpdaterNative.getColors(rotation,
                            statusBarHeight, navigationBarHeight,
                            2 + (isLandscape ? navigationBarHeight : 0));

                    if (mStatusEnabled) {
                        statusBarOverrideColor = mStatusFilterEnabled ?
                                filter(colors[0], -10) : colors[0];
                        updateStatusBarColor(statusBarOverrideColor);

                        // magic from http://www.w3.org/TR/AERT#color-contrast
                        final float statusBarBrightness =
                                (0.299f * Color.red(statusBarOverrideColor) +
                                0.587f * Color.green(statusBarOverrideColor) +
                                0.114f * Color.blue(statusBarOverrideColor)) / 255;
                        final boolean isStatusBarConsistent = colors[1] == 1;
                        updateStatusBarIconColor(statusBarBrightness > 0.7f &&
                                                 isStatusBarConsistent ? Color.parseColor("#FF000000") : Color.parseColor("#FFFFFFFF"));
                    } else {
                        // dynamic status bar is disabled
                        updateStatusBarColor(0);
                        updateStatusBarIconColor(0);
                    }

                    if (mHeaderEnabled) {
                        headerOverrideColor = mStatusFilterEnabled ?
                                filter(colors[0], -10) : colors[0];
                        updateHeaderColor(headerOverrideColor);

                        // magic from http://www.w3.org/TR/AERT#color-contrast
                        final float HeaderBrightness =
                                (0.299f * Color.red(headerOverrideColor) +
                                0.587f * Color.green(headerOverrideColor) +
                                0.114f * Color.blue(headerOverrideColor)) / 255;
                        final boolean isStatusBarConsistent = colors[1] == 1;
                        updateHeaderIconColor(HeaderBrightness > 0.7f &&
                                              isStatusBarConsistent ? Color.parseColor("#FF000000") : Color.parseColor("#FFFFFFFF"));
                    } else {
                        // dynamic status bar is disabled
                        updateHeaderColor(0);
                        updateHeaderIconColor(0);
                    }

                    if (mNavigationEnabled) {
                        navigationBarOverrideColor = colors[2];
                        updateNavigationBarColor(navigationBarOverrideColor);

                        // magic from http://www.w3.org/TR/AERT#color-contrast
                        final float statusBarBrightness =
                                (0.299f * Color.red(statusBarOverrideColor) +
                                0.587f * Color.green(statusBarOverrideColor) +
                                0.114f * Color.blue(statusBarOverrideColor)) / 255;

                        final float navigationBarBrightness =
                                (0.299f * Color.red(navigationBarOverrideColor) +
                                0.587f * Color.green(navigationBarOverrideColor) +
                                0.114f * Color.blue(navigationBarOverrideColor)) / 255;
                        final boolean isNavigationBarConsistent = colors[3] == 1;
                        if (reverse && mStatusEnabled){
                            int kolor = navigationBarBrightness > 0.7f &&
                                isNavigationBarConsistent ? Color.parseColor("#FF000000") : Color.parseColor("#FFFFFFFF");
                            boolean same = statusBarBrightness == navigationBarBrightness;
                            boolean rendah = statusBarBrightness - navigationBarBrightness < 0.1f;
                            boolean uiputih = statusBarBrightness > 0.8f;
                            boolean uiblek = statusBarBrightness < 0.2f;
                            boolean navputih = navigationBarBrightness > 0.8f;
                            boolean navblek = navigationBarBrightness < 0.2f;
                            if (uiputih && navputih) {
                                updateNavigationBarIconColor(kolor);
                            } else if (uiblek && navblek) {
                                updateNavigationBarIconColor(kolor);
                            } else {
                            updateNavigationBarIconColor(same?kolor:statusBarOverrideColor);
                            }
                        } else {
                            updateNavigationBarIconColor(navigationBarBrightness > 0.7f &&
                                                         isNavigationBarConsistent ? Color.parseColor("#FF000000") : Color.parseColor("#FFFFFFFF"));
                        }
                    } else {
                        // dynamic navigation bar is disabled
                        updateNavigationBarColor(0);
                        updateNavigationBarIconColor(0);
                    }
                } else {
                    // we are disabled completely - shush
                    updateStatusBarColor(0);
                    updateHeaderColor(0);
                    updateStatusBarIconColor(0);
                    updateNavigationBarColor(0);
                    updateNavigationBarIconColor(0);
                }

                // do a quick cleanup of the listener list
                synchronized(BarBackgroundUpdater.class) {
                    final ArrayList<UpdateListener> removables = new ArrayList<UpdateListener>();

                    for (final UpdateListener listener : mListeners) {
                        if (listener.shouldGc()) {
                            removables.add(listener);
                        }
                    }

                    for (final UpdateListener removable : removables) {
                        mListeners.remove(removable);
                    }
                }

                try {
                    Thread.sleep(sMinDelay);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }

    });

    static {
        THREAD.setPriority(4);
        THREAD.start();
    }

    public static boolean mStatusEnabled = false;
    public static boolean mHeaderEnabled = false;

    private static boolean mStatusFilterEnabled = false;
    public static int mPreviousStatusBarOverrideColor = 0;
    public static int mStatusBarOverrideColor = 0;
    public static int mPreviousStatusBarIconOverrideColor = 0;
    public static int mStatusBarIconOverrideColor = 0;

    public static int mPreviousHeaderOverrideColor = 0;
    public static int mHeaderOverrideColor = 0;
    public static int mPreviousHeaderIconOverrideColor = 0;
    public static int mHeaderIconOverrideColor = 0;

    public static boolean mNavigationEnabled = false;
    public static int mPreviousNavigationBarOverrideColor = 0;
    public static int mNavigationBarOverrideColor = 0;
    public static int mPreviousNavigationBarIconOverrideColor = 0;
    public static int mNavigationBarIconOverrideColor = 0;
    public static int statusBarOverrideColor;
    public static int headerOverrideColor;

    public static int navigationBarOverrideColor;
    public static boolean reverse = false;

    private static final ArrayList<UpdateListener> mListeners = new ArrayList<UpdateListener>();
    private static Handler mHandler = null;
    private static Context mContext = null;
    private static SettingsObserver mObserver = null;

    private BarBackgroundUpdater() {

    }

    private synchronized static void setPauseState(final boolean isPaused) {
        PAUSED = isPaused;
        if (!isPaused) {
            // the thread should be notified to resume
            BarBackgroundUpdater.class.notify();
        }
    }

    private static void pause() {
        setPauseState(true);
    }

    private static void resume() {
        setPauseState(false);
    }

    public synchronized static void init(final Context context) {
        if (mContext != null) {
            mContext.unregisterReceiver(RECEIVER);

            if (mObserver != null) {
                mContext.getContentResolver().unregisterContentObserver(mObserver);
            }
        }

        mHandler = new Handler();
        mContext = context;

        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        mContext.registerReceiver(RECEIVER, filter);

        if (mObserver == null) {
            mObserver = new SettingsObserver(new Handler());
        }

        mContext.getContentResolver().registerContentObserver(
             Settings.System.getUriFor(Settings.System.DYNAMIC_STATUS_BAR_STATE),
             false, mObserver, UserHandle.USER_ALL);
        mContext.getContentResolver().registerContentObserver(
             Settings.System.getUriFor(Settings.System.DYNAMIC_HEADER_STATE),
             false, mObserver, UserHandle.USER_ALL);
        mContext.getContentResolver().registerContentObserver(
             Settings.System.getUriFor(Settings.System.DYNAMIC_NAVIGATION_BAR_STATE),
             false, mObserver, UserHandle.USER_ALL);
        mContext.getContentResolver().registerContentObserver(
             Settings.System.getUriFor(Settings.System.DYNAMIC_STATUS_BAR_FILTER_STATE),
             false, mObserver, UserHandle.USER_ALL);
        mContext.getContentResolver().registerContentObserver(
             Settings.System.getUriFor(Settings.System.DYNAMIC_ICON_TINT_STATE),
             false, mObserver, UserHandle.USER_ALL);

        mStatusEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
            Settings.System.DYNAMIC_STATUS_BAR_STATE, 0, UserHandle.USER_CURRENT) == 1;
        mHeaderEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
            Settings.System.DYNAMIC_HEADER_STATE, 0, UserHandle.USER_CURRENT) == 1;
        mNavigationEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
            Settings.System.DYNAMIC_NAVIGATION_BAR_STATE, 0, UserHandle.USER_CURRENT) == 1;
        mStatusFilterEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
            Settings.System.DYNAMIC_STATUS_BAR_FILTER_STATE, 0, UserHandle.USER_CURRENT) == 1;
        reverse = Settings.System.getIntForUser(mContext.getContentResolver(),
            Settings.System.DYNAMIC_ICON_TINT_STATE, 0, UserHandle.USER_CURRENT) == 1;

        final Display d = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();
        final Point sizePoint = new Point();
        d.getRealSize(sizePoint);
        BarBackgroundUpdaterNative.setScreenSize(d.getRotation(), sizePoint.x, sizePoint.y);

        resume();
    }

    public synchronized static void addListener(final UpdateListener... listeners) {
        for (final UpdateListener listener : listeners) {
            if (listener == null) {
                continue;
            }

            listener.onUpdateStatusBarColor(mPreviousStatusBarOverrideColor,
                     mStatusBarOverrideColor);
            listener.onUpdateHeaderColor(mPreviousHeaderOverrideColor,
                     mHeaderOverrideColor);
            listener.onUpdateHeaderIconColor(mPreviousHeaderIconOverrideColor,
                     mHeaderIconOverrideColor);
            listener.onUpdateStatusBarIconColor(mPreviousStatusBarIconOverrideColor,
                     mStatusBarIconOverrideColor);
            listener.onUpdateNavigationBarColor(mPreviousNavigationBarOverrideColor,
                     mNavigationBarOverrideColor);
            listener.onUpdateNavigationBarIconColor(mPreviousNavigationBarIconOverrideColor,
                     mNavigationBarIconOverrideColor);
            boolean shouldAdd = true;

            for (final UpdateListener existingListener : mListeners) {
                if (existingListener == listener) {
                    shouldAdd = false;
                }
            }

            if (shouldAdd) {
                mListeners.add(listener);
            }
        }
    }

    private static int filter(final int original, final float diff) {
        final int red = (int) (Color.red(original) + diff);
        final int green = (int) (Color.green(original) + diff);
        final int blue = (int) (Color.blue(original) + diff);

        return Color.argb(
                Color.alpha(original),
                red > 0 ?
                        red < 255 ?
                                red :
                                255 :
                        0,
                green > 0 ?
                        green < 255 ?
                                green :
                                255 :
                        0,
                blue > 0 ?
                        blue < 255 ?
                                blue :
                                255 :
                        0
        );
    }

    private static int getPixel(final Bitmap bitmap, final int x, final int y) {
        if (bitmap == null) {
            // just silently ignore this
            return Color.BLACK;
        }

        if (x == 0) {
            Log.w(LOG_TAG, "getPixel for x=0 is not allowed; returning a black pixel");
            return Color.BLACK;
        }

        if (y == 0) {
            Log.w(LOG_TAG, "getPixel for y=0 is not allowed; returning a black pixel");
            return Color.BLACK;
        }

        return bitmap.getPixel(x > 0 ? x : bitmap.getWidth() + x,
            y > 0 ? y : bitmap.getHeight() + y);
    }

    public synchronized static void updateStatusBarColor(final int newColor) {
        if (mStatusBarOverrideColor == newColor) {
            return;
        }

        mPreviousStatusBarOverrideColor = mStatusBarOverrideColor;
        mStatusBarOverrideColor = newColor;

        if (DEBUG_COLOR_CHANGE) {
            Log.d(LOG_TAG, "statusBarOverrideColor=" + (newColor == 0 ? "none" :
                  "0x" + Integer.toHexString(newColor)));
        }

        for (final UpdateListener listener : mListeners) {
             listener.onUpdateStatusBarColor(
             mPreviousStatusBarOverrideColor, mStatusBarOverrideColor);
        }
    }

    public synchronized static void updateHeaderColor(final int newColor) {
        if (mHeaderOverrideColor == newColor) {
            return;
        }

        if(expanded() && mHeaderEnabled){
            return;
        }

        mPreviousHeaderOverrideColor = mHeaderOverrideColor;
        mHeaderOverrideColor = newColor;

        for (final UpdateListener listener : mListeners) {
             listener.onUpdateHeaderColor(
             mPreviousHeaderOverrideColor, mHeaderOverrideColor);
        }
    }

    public synchronized static void updateStatusBarIconColor(final int newColor) {
        if (mStatusBarIconOverrideColor == newColor) {
            return;
        }

        if(expanded() && mHeaderEnabled){
            return;
        }

        mPreviousStatusBarIconOverrideColor = mStatusBarIconOverrideColor;
        mStatusBarIconOverrideColor = newColor;

        if (DEBUG_COLOR_CHANGE) {
            Log.d(LOG_TAG, "statusBarIconOverrideColor=" + (newColor == 0 ? "none" :
                  "0x" + Integer.toHexString(newColor)));
        }

        for (final UpdateListener listener : mListeners) {
             listener.onUpdateStatusBarIconColor(
             mPreviousStatusBarIconOverrideColor, mStatusBarIconOverrideColor);
        }
    }

    public synchronized static void updateHeaderIconColor(final int newColor) {
        if (mHeaderIconOverrideColor == newColor) {
            return;
        }

        if(expanded() && mHeaderEnabled){
            return;
        }

        mPreviousHeaderIconOverrideColor = mStatusBarIconOverrideColor;
        mHeaderIconOverrideColor = newColor;

        if (DEBUG_COLOR_CHANGE) {
            Log.d(LOG_TAG, "statusBarIconOverrideColor=" + (newColor == 0 ? "none" :
                  "0x" + Integer.toHexString(newColor)));
        }

        for (final UpdateListener listener : mListeners) {
             listener.onUpdateHeaderIconColor(
             mPreviousHeaderIconOverrideColor, mHeaderIconOverrideColor);
        }
    }

    public synchronized static void updateNavigationBarColor(final int newColor) {
        if (mNavigationBarOverrideColor == newColor) {
            return;
        }

        mPreviousNavigationBarOverrideColor = mNavigationBarOverrideColor;
        mNavigationBarOverrideColor = newColor;

        if (DEBUG_COLOR_CHANGE) {
            Log.d(LOG_TAG, "navigationBarOverrideColor=" + (newColor == 0 ? "none" :
                  "0x" + Integer.toHexString(newColor)));
        }

        for (final UpdateListener listener : mListeners) {
             listener.onUpdateNavigationBarColor(
             mPreviousNavigationBarOverrideColor, mNavigationBarOverrideColor);
        }
    }

    public synchronized static void updateNavigationBarIconColor(final int newColor) {
        if (mNavigationBarIconOverrideColor == newColor) {
            return;
        }

        mPreviousNavigationBarIconOverrideColor = mNavigationBarIconOverrideColor;
        mNavigationBarIconOverrideColor = newColor;

        if (DEBUG_COLOR_CHANGE) {
            Log.d(LOG_TAG, "navigationBarIconOverrideColor=" + (newColor == 0 ? "none" :
                  "0x" + Integer.toHexString(newColor)));
        }

        for (final UpdateListener listener : mListeners) {
             listener.onUpdateNavigationBarIconColor(
             mPreviousNavigationBarIconOverrideColor, mNavigationBarIconOverrideColor);
        }
    }

    public static class UpdateListener {
        private final WeakReference<Object> mRef;

        public UpdateListener(final Object ref) {
            mRef = new WeakReference<Object>(ref);
        }

        public final boolean shouldGc() {
            return mRef.get() == null;
        }

        public void onUpdateStatusBarColor(final int previousColor, final int color) {
            //return null;
        }

        public void onUpdateHeaderColor(final int previousColor, final int color) {
            //return null;
        }

        public void onUpdateHeaderIconColor(final int previousIconColor, final int iconColor) {
            //return null;
        }

        public void onUpdateStatusBarIconColor(final int previousIconColor, final int iconColor) {
            //return null;
        }

        public void onUpdateNavigationBarColor(final int previousColor, final int color) {
            //return null;
        }

        public void onUpdateNavigationBarIconColor(final int previousIconColor, final int iconColor) {
           // return null;
        }
    }

    private static final class SettingsObserver extends ContentObserver {
        private SettingsObserver(final Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(final boolean selfChange) {
            mStatusEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.DYNAMIC_STATUS_BAR_STATE, 0, UserHandle.USER_CURRENT) == 1;
            mHeaderEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.DYNAMIC_HEADER_STATE, 0, UserHandle.USER_CURRENT) == 1;
            mNavigationEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.DYNAMIC_NAVIGATION_BAR_STATE, 0, UserHandle.USER_CURRENT) == 1;
            mStatusFilterEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.DYNAMIC_STATUS_BAR_FILTER_STATE, 0, UserHandle.USER_CURRENT) == 1;
            reverse = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.DYNAMIC_ICON_TINT_STATE, 0, UserHandle.USER_CURRENT) == 1;
        }

    }

}
