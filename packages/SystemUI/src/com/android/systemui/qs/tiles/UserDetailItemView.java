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
 * limitations under the License
 */

package com.android.systemui.qs.tiles;

import com.android.internal.util.ArrayUtils;
import com.android.internal.util.temasek.QSColorHelper;
import com.android.systemui.FontSizeUtils;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.BarBackgroundUpdater;
import com.android.systemui.statusbar.phone.UserAvatarView;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.systemui.qs.QSPanel;

/**
 * Displays one user in the {@link UserDetailView} view.
 */
public class UserDetailItemView extends LinearLayout {

    private Handler mHandler;
    private int mOverrideIconColor;
    private UserAvatarView mAvatar;
    private TextView mName;
    private Typeface mRegularTypeface;
    private Typeface mActivatedTypeface;

    public UserDetailItemView(Context context) {
        this(context, null);
    }

    public UserDetailItemView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public UserDetailItemView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public UserDetailItemView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.UserDetailItemView, defStyleAttr, defStyleRes);
        final int N = a.getIndexCount();
        for (int i = 0; i < N; i++) {
            int attr = a.getIndex(i);
            switch (attr) {
                case R.styleable.UserDetailItemView_regularFontFamily:
                    mRegularTypeface = Typeface.create(a.getString(attr), 0 /* style */);
                    break;
                case R.styleable.UserDetailItemView_activatedFontFamily:
                    mActivatedTypeface = Typeface.create(a.getString(attr), 0 /* style */);
                    break;
            }
        }
        a.recycle();
    }

    public static UserDetailItemView convertOrInflate(Context context, View convertView,
            ViewGroup root) {
        if (!(convertView instanceof UserDetailItemView)) {
            convertView = LayoutInflater.from(context).inflate(
                    R.layout.qs_user_detail_item, root, false);
        }
        return (UserDetailItemView) convertView;
    }

    public void bind(String name, Bitmap picture) {
        mName.setText(name);
        mAvatar.setBitmap(picture);
    }

    public void bind(String name, Drawable picture) {
        mName.setText(name);
        mAvatar.setDrawable(picture);
    }

    @Override
    protected void onFinishInflate() {
        mHandler = new Handler();
        mAvatar = (UserAvatarView) findViewById(R.id.user_picture);
        mName = (TextView) findViewById(R.id.user_name);
        if (mRegularTypeface == null) {
            mRegularTypeface = mName.getTypeface();
        }
        if (mActivatedTypeface == null) {
            mActivatedTypeface = mName.getTypeface();
        }
        updateTypeface();
        BarBackgroundUpdater.addListener(new BarBackgroundUpdater.UpdateListener(this) {

            @Override
            public void onUpdateQsTileIconColor(final int previousIconColor,
                final int iconColor) {
                mOverrideIconColor = iconColor;
                mHandler.post(new Runnable() {

                    @Override
                    public void run() {
                        if (mOverrideIconColor != 0) {
                            boolean activated = ArrayUtils.contains(getDrawableState(), android.R.attr.state_activated);
                            int NormalOverride = mOverrideIconColor;
                            int DeactivatedOverride = (102 << 24) | (NormalOverride & 0x00ffffff); // Text color normal with a transparency of 40%
                            mName.setTextColor(activated ? NormalOverride : DeactivatedOverride);
                            mName.invalidate();
                        }
                    }

                });
            }

        });
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        FontSizeUtils.updateFontSize(mName, R.dimen.qs_detail_item_secondary_text_size);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        updateTypeface();
    }

    private void updateTypeface() {
        //boolean activated = ArrayUtils.contains(getDrawableState(), android.R.attr.state_activated);
        mName.setTypeface(QSPanel.mFontStyle);
    }

    public void setTextColor(boolean activated) {
        boolean mQSCSwitch = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.QS_COLOR_SWITCH, 0) == 1;
        if (mQSCSwitch) {
            if (!BarBackgroundUpdater.mQsTileEnabled) {
            int textColorNormal = QSColorHelper.getTextColor(mContext);
            int textColorActivated = getContext().getResources().getColor(
                    R.color.system_accent_color);
            int textColorDeactivated = (102 << 24) | (textColorNormal & 0x00ffffff); // Text color normal with a transparency of 40%
            mName.setTextColor(activated ? textColorActivated : textColorDeactivated);
            } else {
            int textColorNormalOverride = BarBackgroundUpdater.mQsTileIconOverrideColor;
            int textColorDeactivatedOverride = (102 << 24) | (textColorNormalOverride & 0x00ffffff); // Text color normal with a transparency of 40%
            mName.setTextColor(activated ? textColorNormalOverride : textColorDeactivatedOverride);
            }
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
