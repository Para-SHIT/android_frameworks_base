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
package com.android.systemui.qs;

import android.content.Context;
import android.content.res.Resources;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.BarBackgroundUpdater;
import com.android.systemui.statusbar.phone.BarTransitions;

public class QSTransitions extends BarTransitions {
    
    private final QSContainer mView;
    private final float mIconAlphaWhenOpaque;
   
    public QSTransitions(QSContainer view) {
        super(view, new QSBackgroundDrawable(view.getContext()));
        mView = view;
        final Resources res = mView.getContext().getResources();
        mIconAlphaWhenOpaque = res.getFraction(R.dimen.status_bar_icon_drawing_alpha, 1, 1);
    }

    public void init() {
        applyModeBackground(-1, getMode(), false);
    }

    protected static class QSBackgroundDrawable extends BarTransitions.BarBackgroundDrawable {
        private static  Context mContext;

        private int mOverrideColor = 0;

        public QSBackgroundDrawable(final Context context) {
            super(context, R.drawable.qs_background_primary, R.color.system_primary_color,
                    R.color.system_primary_color,
                    R.color.system_primary_color,
                    com.android.internal.R.color.battery_saver_mode_color);
            mContext = context;

            BarBackgroundUpdater.addListener(new BarBackgroundUpdater.UpdateListener(this) {

                @Override
                public void onUpdateQsTileColor(final int previousColor,
                    final int color) {
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

        public void setColor() {
            generateAnimator();
        }
    }

}
