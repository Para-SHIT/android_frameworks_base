/*
 * Copyright (C) 2008 The Android Open Source Project
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
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextSwitcher;
import android.widget.TextView;

import java.util.ArrayList;

public class TickerView extends TextSwitcher {
    Ticker mTicker;

    private final Handler mHandler;
    private final int mDefaultTextColor = 0xffffffff; // TODO use the resource value instead
    private int mOverrideTextColor = 0;

    public TickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    	mHandler = new Handler();

        BarBackgroundUpdater.addListener(new BarBackgroundUpdater.UpdateListener(this) {

            @Override
            public void onUpdateStatusBarIconColor(final int previousIconColor,
                    final int iconColor) {
                mOverrideTextColor = iconColor;

                final int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    final TextView tv = (TextView) getChildAt(i);
                    if (tv != null) {
						if (mOverrideTextColor == 0) {
                            mHandler.post(new Runnable() {

                                @Override
                                public void run() {
                                    tv.setTextColor(mDefaultTextColor);
                                }

                            });
                        } else {
							mHandler.post(new Runnable() {

								@Override
								public void run() {
									tv.setTextColor(mOverrideTextColor);
                                }

                            });
                        }
                    }
                }

            }

        });

    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mTicker != null) mTicker.reflowText();
    }

    public void setTicker(Ticker t) {
        mTicker = t;
    }

    @Override
    public void addView(final View child, final int index, final ViewGroup.LayoutParams params) {
        if (child instanceof TextView) {
            ((TextView) child).setTextColor(mOverrideTextColor == 0 ?
                    mDefaultTextColor : mOverrideTextColor);
        }

        super.addView(child, index, params);
    }
}

