/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import com.android.systemui.R;

import java.util.List;

public class BarTransitions {
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_COLORS = false;

    public static final boolean HIGH_END = ActivityManager.isHighEndGfx();

    public static final int MODE_OPAQUE = 0;
    public static final int MODE_SEMI_TRANSPARENT = 1;
    public static final int MODE_TRANSLUCENT = 2;
    public static final int MODE_LIGHTS_OUT = 3;
    public static final int MODE_TRANSPARENT = 4;
    public static final int MODE_WARNING = 5;
    public static final int MODE_LIGHTS_OUT_TRANSPARENT = 6;
    public static final int MODE_LIGHTS_OUT_TRANSLUCENT = 7;

    public static final int LIGHTS_IN_DURATION = 250;
    public static final int LIGHTS_OUT_DURATION = 750;
    public static final int BACKGROUND_DURATION = 200;

    public static Context mContext;

    private final String mTag;
    private final View mView;
    private final BarBackgroundDrawable mBarBackground;

    private int mMode;

    public BarTransitions(View view, BarBackgroundDrawable barBackground) {
        mTag = "BarTransitions." + view.getClass().getSimpleName();
        mView = view;
        mBarBackground = barBackground;
        mContext = mView.getContext();

        if (HIGH_END) {
            mView.setBackground(mBarBackground);
        }
    }

    protected void setGradientResourceId(int gradientResourceId) {
        mBarBackground.setGradientResourceId(mView.getContext().getResources(),
                gradientResourceId);
    }

    public void updateResources(Resources res) {
        mBarBackground.updateResources(res);
    }

    public int getMode() {
        return mMode;
    }

    public void setWarningColor(int color) {
        if (mBarBackground != null) {
            mBarBackground.setWarningColor(color);
        }
    }

    public void transitionTo(int mode, boolean animate) {
        // low-end devices do not support translucent modes, fallback to opaque
        if (!HIGH_END && (mode == MODE_SEMI_TRANSPARENT || mode == MODE_TRANSLUCENT
                || mode == MODE_TRANSPARENT)) {
            mode = MODE_OPAQUE;
        }
        if (!HIGH_END && (mode == MODE_LIGHTS_OUT_TRANSPARENT || mode == MODE_LIGHTS_OUT_TRANSLUCENT)) {
            mode = MODE_LIGHTS_OUT;
        }
        if (mMode == mode) return;
        int oldMode = mMode;
        mMode = mode;
        if (DEBUG) Log.d(mTag, String.format("%s -> %s animate=%s",
                modeToString(oldMode), modeToString(mode),  animate));
        onTransition(oldMode, mMode, animate);
    }

    protected void onTransition(int oldMode, int newMode, boolean animate) {
        if (HIGH_END) {
            applyModeBackground(oldMode, newMode, animate);
        }
    }

    protected void applyModeBackground(int oldMode, int newMode, boolean animate) {
        if (DEBUG) Log.d(mTag, String.format("applyModeBackground oldMode=%s newMode=%s animate=%s",
                modeToString(oldMode), modeToString(newMode), animate));
        mBarBackground.applyMode(newMode, animate);
    }

    public static String modeToString(int mode) {
        if (mode == MODE_OPAQUE) return "MODE_OPAQUE";
        if (mode == MODE_SEMI_TRANSPARENT) return "MODE_SEMI_TRANSPARENT";
        if (mode == MODE_TRANSLUCENT) return "MODE_TRANSLUCENT";
        if (mode == MODE_LIGHTS_OUT) return "MODE_LIGHTS_OUT";
        if (mode == MODE_TRANSPARENT) return "MODE_TRANSPARENT";
        if (mode == MODE_WARNING) return "MODE_WARNING";
        if (mode == MODE_LIGHTS_OUT_TRANSPARENT) return "MODE_LIGHTS_OUT_TRANSPARENT";
        if (mode == MODE_LIGHTS_OUT_TRANSLUCENT) return "MODE_LIGHTS_OUT_TRANSLUCENT";
        if (mode == -1) return "MODE_UNKNOWN";
        throw new IllegalArgumentException("Unknown mode " + mode);
    }

    public void finishAnimations() {
        mBarBackground.finishAnimating();
    }

    protected boolean isLightsOut(int mode) {
        return mode == MODE_LIGHTS_OUT || mode == MODE_LIGHTS_OUT_TRANSPARENT || mode == MODE_LIGHTS_OUT_TRANSLUCENT;
    }

    protected static class BarBackgroundDrawable extends Drawable {

        private final Handler mHandler;
        private final Runnable mInvalidateSelf = new Runnable() {

            @Override
            public void run() {
                invalidateSelf();
            }

        };

        private int mOpaque = 0;
        private int mSemiTransparent = 0;
        private Drawable mGradient = null;
        private int mTransparent = 0;
        private int mWarning = 0;

        private int mCurrentMode = -1;
        private int mCurrentColor = 0;
        private int mCurrentGradientAlpha = 0;

        private int mGradientAlphaStart;
        private int mColorStart;
        private Context cont;
        private Resources res;
        private int mGradientResourceId;
        private final int mOpaqueColorResourceId;
        private final int mSemiTransparentColorResourceId;
        private final int mTransparentColorResourceId;
        private final int mWarningColorResourceId;

        public BarBackgroundDrawable(final Context context, final int gradientResourceId,
                final int opaqueColorResourceId,final int semiTransparentColorResourceId,
                final int transparentColorResourceId, final int warningColorResourceId) {
            cont = context;
            res = context.getResources();
            mHandler = new Handler();
            mGradientResourceId = gradientResourceId;
            mOpaqueColorResourceId = opaqueColorResourceId;
            mSemiTransparentColorResourceId = semiTransparentColorResourceId;
            mTransparentColorResourceId = transparentColorResourceId;
            mWarningColorResourceId = warningColorResourceId;
            updateResources(res);
        }

        @Override
        public final void draw(final Canvas canvas) {
            final int currentColor = mCurrentColor;
            if (Color.alpha(currentColor) > 0) {
                canvas.drawColor(currentColor);
            }
            final int currentGradientAlpha = mCurrentGradientAlpha;
            if (currentGradientAlpha > 0) {
                mGradient.setAlpha(currentGradientAlpha);
                mGradient.draw(canvas);
            }
        }

        public void setGradientResourceId(Resources res, int gradientResourceId) {
            mGradient = res.getDrawable(gradientResourceId);
            mGradientResourceId = gradientResourceId;
        }

        @Override
        public final int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public final void setAlpha(int alpha) {
            // noop
        }

        @Override
        public final void setColorFilter(ColorFilter colorFilter) {
            // noop
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            mGradient.setBounds(bounds);
        }

        public void setWarningColor(int color) {
            if (!DEBUG_COLORS) {
                mWarning = color;
            }
        }

        public final synchronized void updateResources(final Resources res)  {
            if (DEBUG_COLORS) {
                mOpaque = 0xff0000ff;
                mSemiTransparent = 0x7f0000ff;
                mWarning = 0xffff0000;
            } else {
                mOpaque = res.getColor(mOpaqueColorResourceId);
                mSemiTransparent = res.getColor(mSemiTransparentColorResourceId);
                mWarning = res.getColor(mWarningColorResourceId);

            }

            mOpaque = res.getColor(mOpaqueColorResourceId);
            mSemiTransparent = res.getColor(mSemiTransparentColorResourceId);
            mTransparent = res.getColor(mTransparentColorResourceId);
            mWarning = res.getColor(mWarningColorResourceId);

            final Rect bounds = mGradient == null ? new Rect() : mGradient.getBounds();
            mGradient = res.getDrawable(mGradientResourceId, cont.getTheme());
            mGradient.setBounds(bounds);

            setCurrentColor(getTargetColor());
            setCurrentGradientAlpha(getTargetGradientAlpha());
            invalidateSelf();

        }

        protected int getColorOpaque() {
            return mOpaque;
        }

        protected int getColorwarning() {
            return mWarning;
        }

        protected int getColorSemiTransparent() {
            return mSemiTransparent;
        }

        protected int getColorTransparent() {
            return mTransparent;
        }

        protected int getGradientAlphaOpaque() {
            return 0;
        }

        protected int getGradientAlphaSemiTransparent() {
            return 0;
        }

        private final int getTargetColor() {
            return getTargetColor(mCurrentMode);
        }

        private boolean isenable() {
            return BarBackgroundUpdater.mStatusEnabled || BarBackgroundUpdater.mNavigationEnabled;
        }

        public boolean ishomescreen(){
            boolean b = false;
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            String defaultHomePackage = "com.android.launcher";
            intent.addCategory(Intent.CATEGORY_HOME);
            final ResolveInfo res = mContext.getPackageManager().resolveActivity(intent, 0);
            if (res.activityInfo != null && !res.activityInfo.packageName.equals("android")) {
                defaultHomePackage = res.activityInfo.packageName;
            }
            final ActivityManager am = (ActivityManager) mContext
                 .getSystemService(Activity.ACTIVITY_SERVICE);
            List<RunningAppProcessInfo> apps = am.getRunningAppProcesses();
            for (RunningAppProcessInfo appInfo : apps) {

                // Make sure it's a foreground user application (not system,
                // root, phone, etc.)
                if (appInfo.pkgList != null && (appInfo.pkgList.length > 0)) {
                    for (String pkg : appInfo.pkgList) {
                        if (pkg.equals(defaultHomePackage)) {
                             return b = true;
                        } else {
                             return b = false;
                        }
                    }
                }
            }
            return b;
        }

        public boolean islockscreen() {
            return NotificationPanelView.mKeyguardShowing;
        }

        public boolean isplaystore() {
            boolean b = false;
            boolean ps = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.DYNAMIC_TRANSPARENT_PS, 0) == 1;
            final Intent intent = new Intent(Intent.ACTION_MAIN);

            final ActivityManager am = (ActivityManager) mContext
                  .getSystemService(Activity.ACTIVITY_SERVICE);
            List<RunningAppProcessInfo> apps = am.getRunningAppProcesses();
            for (RunningAppProcessInfo appInfo : apps) {

                // Make sure it's a foreground user application (not system,
                // root, phone, etc.)
                if (appInfo.pkgList != null && (appInfo.pkgList.length > 0)) {
                    for (String pkg : appInfo.pkgList) {
                        if (pkg.equals("com.android.vending")) {
                             return b = ps ? true : false;
                        } else {
                             return b = false;
                        }
                    }
                }
            }
            return b;
        }

        private final int getTargetColor(final int mode) {

            switch (mode) {
                case MODE_LIGHTS_OUT_TRANSPARENT:
                    if (isenable()) {
                        return getColorOpaque();
                    } else {
                        return getColorTransparent();
                    }
                case MODE_TRANSPARENT:
                    if (isenable()) {
                        if (ishomescreen()) {
                            return getColorTransparent();
                        } else if (islockscreen()) {
                            return getColorTransparent();
                        } else if (isplaystore()) {
                            return getColorTransparent();
                        } else {
                            return getColorOpaque();
                        }
                    } else {
                        return getColorTransparent();
                    }
                case MODE_WARNING:
                    if (isenable()) {
                        if (ishomescreen()) {
                            return getColorTransparent();
                        } else if (islockscreen()) {
                            return getColorTransparent();
                        } else {
                            return getColorOpaque();
                        }
                    } else {
                        return getColorwarning();
                    }
                case MODE_TRANSLUCENT:
                    if (isenable()) {
                        return 0;
                    } else {
                        return getColorSemiTransparent();
                    }
                case MODE_SEMI_TRANSPARENT:
                    if (isenable()) {
                        return getColorOpaque();
                    } else {
                        return getColorSemiTransparent();
                    }
                default:
                    return getColorOpaque();
            }
        }

        private final int getTargetGradientAlpha() {
            return getTargetGradientAlpha(mCurrentMode);
        }

        private final int getTargetGradientAlpha(final int mode) {
            switch (mode) {
                case MODE_TRANSPARENT:
                    return getGradientAlphaOpaque();
                case MODE_TRANSLUCENT:
                    return 0xff;
                case MODE_SEMI_TRANSPARENT:
                    return getGradientAlphaSemiTransparent();
                default:
                    return getGradientAlphaOpaque();
            }
        }

        protected final void setCurrentColor(final int color) {
            mCurrentColor = color;
        }

        protected final void setCurrentGradientAlpha(final int alpha) {
            mCurrentGradientAlpha = alpha;
        }

        public final synchronized void applyMode(final int mode, final boolean animate) {
            mCurrentMode = mode;

            mHandler.post(new Runnable() {

                @Override
                public void run() {
                    final int targetColor = getTargetColor(mode);
                    final int targetGradientAlpha = getTargetGradientAlpha(mode);

                    if (targetColor != mCurrentColor ||
                        targetGradientAlpha != mCurrentGradientAlpha) {
                        setCurrentColor(targetColor);
                        setCurrentGradientAlpha(targetGradientAlpha);
                        invalidateSelf();
                    }

                }

            });

        }

        public final void finishAnimating() {
            mHandler.post(new Runnable() {

                @Override
                public void run() {
                    final int targetColor = getTargetColor();
                    final int targetGradientAlpha = getTargetGradientAlpha();

                    if (targetColor != mCurrentColor ||
                        targetGradientAlpha != mCurrentGradientAlpha) {
                        setCurrentColor(targetColor);
                        setCurrentGradientAlpha(targetGradientAlpha);
                        invalidateSelf();
                    }

                }

            });
        }

        protected final void generateAnimator() {
            generateAnimator(mCurrentMode);
        }

        protected final void generateAnimator(final int targetMode) {
            final int targetColor = getTargetColor(targetMode);
            final int targetGradientAlpha = getTargetGradientAlpha(targetMode);

            if (targetColor == mCurrentColor && targetGradientAlpha == mCurrentGradientAlpha) {
                // no values are changing - nothing to do
                return ;
            }

            mHandler.post(new Runnable() {

                @Override
                public void run() {
                    if (targetColor == mCurrentColor ) {
                        // color value is not changing - only gradient alpha is changing
                        setCurrentGradientAlpha(targetGradientAlpha);
                    }

                    if (targetGradientAlpha == mCurrentGradientAlpha ) {
                        // gradient alpha is not changing - only color value is changing
                        setCurrentColor(targetColor);
                    }
                    setCurrentColor(targetColor);
                    setCurrentGradientAlpha(targetGradientAlpha);
                    invalidateSelf();
                }

            });

        }

    }
}
