/*
 * Copyright (C) 2016-2017 ParaSHIT
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

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.widget.Toast;

import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.systemui.R;
import com.android.systemui.SysUIToast;
import com.android.systemui.qs.QSTile;

public class ParaSHITTile extends QSTile<QSTile.BooleanState> {
    private boolean mListening;

    private static final Intent PARASHIT = new Intent().setComponent(new ComponentName(
            "com.android.settings", "com.android.settings.Settings$ParaSHITActivity"));

    public ParaSHITTile(Host host) {
        super(host);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QUICK_SETTINGS;
    }

    @Override
    protected void handleClick() {
        mHost.collapsePanels();
        startParaSHIT();
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public void handleLongClick() {
        // Collapse the panels, so the user can see the toast.
        mHost.collapsePanels();
        SysUIToast.makeText(mContext, mContext.getString(
                R.string.quick_shit_toast),
                Toast.LENGTH_LONG).show();
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_shit_label);
    }

    protected void startParaSHIT() {
        mHost.startActivityDismissingKeyguard(PARASHIT);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.icon = ResourceIcon.get(R.drawable.ic_qs_parashit);
        state.label = mContext.getString(R.string.quick_shit_label);
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
    }
}
