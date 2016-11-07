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
import android.graphics.PorterDuff.Mode;
import android.os.Handler;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.android.systemui.statusbar.phone.BarBackgroundUpdater;

public class QSTileViewImage extends ImageView {

    private Handler mHandler;
    private int mOverrideIconColor = 0;

    static Context mContext;

    ImageView iv;

    public QSTileViewImage(Context context) {
        super(context);
        init(context);
    }

    public QSTileViewImage(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public QSTileViewImage(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public void init(Context context) {
        iv = this;
        mContext = context;
        mHandler = new Handler();

        BarBackgroundUpdater.addListener(new BarBackgroundUpdater.UpdateListener(this) {

            @Override
            public void onUpdateQsTileIconColor(final int previousIconColor,
                final int iconColor) {
                mOverrideIconColor = iconColor;
                mHandler.post(new Runnable() {

                    @Override
                    public void run() {
                        if (iv != null) {
                            updateColor(mOverrideIconColor);
                        }
                    }

                });
            }

        });

    }

    public void updateColor(final int targetColor) {
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                if (iv != null) {
                    iv.setColorFilter(targetColor, Mode.SRC_ATOP);
                    invalidate();
                }
            }

        });
    }

}
