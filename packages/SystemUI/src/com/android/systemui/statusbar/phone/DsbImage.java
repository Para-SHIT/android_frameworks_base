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
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.widget.ImageView;

public class DsbImage extends ImageView {

    private Handler mHandler;
    private int mOverrideIconColor = 0;
    ImageView iv;
    static Context mContext;

    public DsbImage(Context c, AttributeSet as) {
        super(c,as);
        iv = this;
        mContext = c;
        mHandler = new Handler();

        BarBackgroundUpdater.addListener(new BarBackgroundUpdater.UpdateListener(this) {

            @Override
            public void onUpdateStatusBarIconColor(final int previousIconColor,
                final int iconColor) {

                mOverrideIconColor = iconColor;
                final int targetColor = (mOverrideIconColor == 0) ? 0xffffffff : mOverrideIconColor;

                apdet(targetColor);

            }

        });

    }

    public void apdet(final int targetColor) {
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                if (iv != null) {
                    iv.setColorFilter(targetColor);
                    invalidate();
                }
            }

        });

    }

}
