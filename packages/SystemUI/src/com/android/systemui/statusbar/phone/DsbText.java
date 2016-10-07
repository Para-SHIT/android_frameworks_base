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
import android.os.Handler;
import android.util.AttributeSet;
import android.widget.TextView;

public class DsbText extends TextView {

    private static Handler mHandler;
    private int mOverrideIconColor = 0;
    TextView tv;
    private static Runnable doOverride;
    static Context mContext;

    public DsbText(Context c) {
        super(c);
        init(c);
    }

    public DsbText(Context c, AttributeSet as) {
        super(c, as);
        init(c);
    }

    public DsbText(Context c, AttributeSet as, int d) {
        super(c,as,d);
        init(c);
    }

    public void init(Context c) {
        tv = this;
        mContext = c;
        mHandler = new Handler();

        BarBackgroundUpdater.addListener(new BarBackgroundUpdater.UpdateListener(this) {

            @Override
            public void onUpdateStatusBarIconColor(final int previousIconColor,
                final int iconColor) {
                mOverrideIconColor = iconColor;

                apdet(mOverrideIconColor);

            }

        });

    }

    public void apdet(final int targetColor) {
        doOverride = new Runnable() {

            @Override
            public void run() {
                setTextColor(targetColor);
                invalidate();
                mHandler.removeCallbacks(doOverride);
                mHandler.postDelayed(doOverride, 50);
            }

        };
        mHandler.removeCallbacks(doOverride);
        mHandler.postDelayed(doOverride, 50);
    }

}
