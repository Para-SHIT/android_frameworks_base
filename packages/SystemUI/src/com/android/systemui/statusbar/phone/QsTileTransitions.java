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

import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;

import android.view.View;

import com.android.systemui.R;
import com.android.systemui.qs.QSContainer;

public class QsTileTransitions extends BarTransitions {
    
    private final QSContainer mView;
    private final float mIconAlphaWhenOpaque;
   
    public QsTileTransitions(QSContainer view) {
        super(view, new QsTileBackgroundDrawable(view.getContext()));
        mView = view;
        final Resources res = mView.getContext().getResources();
        mIconAlphaWhenOpaque = res.getFraction(R.dimen.status_bar_icon_drawing_alpha, 1, 1);
    }

    public void init() {
        applyModeBackground(-1, getMode(), false);
    }

    protected static class QsTileBackgroundDrawable
    extends BarTransitions.BarBackgroundDrawable {
        private static  Context mContext;

        private int mOverrideColor = 0;
        private int mOverrideGradientAlpha = 0;

        public QsTileBackgroundDrawable(final Context context) {
            super(context,R.drawable.qs_background_primary, R.color.system_primary_color,
            R.color.system_primary_color,
            R.color.system_primary_color,
            com.android.internal.R.color.battery_saver_mode_color);
           
            mContext = context;

            final GradientObserver obs = new GradientObserver(this, new Handler());
            (mContext.getContentResolver()).registerContentObserver(
                GradientObserver.DYNAMIC_SYSTEM_BARS_GRADIENT_URI,
                false, obs, UserHandle.USER_ALL);

            mOverrideGradientAlpha = Settings.System.getInt(mContext.getContentResolver(),
                "DYNAMIC_GRADIENT_STATE", 0) == 1 ?
                    0xff : 0;

            BarBackgroundUpdater.addListener(new BarBackgroundUpdater.UpdateListener(this) {

                @Override
                public void onUpdateQsTileColor(final int previousColor, final int color) {
                    mOverrideColor = color;
                    generateAnimator();
                }

            });
            BarBackgroundUpdater.init(context);
        }

        @Override
        protected int getColorOpaque() {
            return mOverrideColor == 0 ? super.getColorOpaque() : mOverrideColor;
        }

        @Override
        protected int getColorSemiTransparent() {
            return (mOverrideColor == 0 ) ? super.getColorOpaque() :
                (mOverrideColor & 0x00ffffff | 0x7f000000);
        }

        @Override
        protected int getColorTransparent() {
            // TODO: Implement this method
            return mOverrideColor == 0  ? super.getColorOpaque() : mOverrideColor;
        }

        @Override
        protected int getGradientAlphaOpaque() {
            return mOverrideGradientAlpha;
        }

        @Override
        protected int getGradientAlphaSemiTransparent() {
            return mOverrideGradientAlpha;
        }

        public void setColor() {
            generateAnimator();
        }

        public void setOverrideGradientAlpha(final int alpha) {
            mOverrideGradientAlpha = alpha;
            generateAnimator();
        }
    }

    private static final class GradientObserver extends ContentObserver {
        private static final Uri DYNAMIC_SYSTEM_BARS_GRADIENT_URI = Settings.System.getUriFor(
            "DYNAMIC_GRADIENT_STATE");

        private final QsTileBackgroundDrawable mDrawable;

        private GradientObserver(final QsTileBackgroundDrawable drawable,
                final Handler handler) {
            super(handler);
            mDrawable = drawable;
        }

        @Override
        public void onChange(final boolean selfChange) {
            mDrawable.setOverrideGradientAlpha(Settings.System.getInt(
                mDrawable.mContext.getContentResolver(),
                "DYNAMIC_GRADIENT_STATE", 0) == 1 ? 0xff : 0);
        }

    }

}
