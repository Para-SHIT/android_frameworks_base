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
import android.widget.RemoteViews.*;
public class HeaderTransitions extends BarTransitions {
    
    private final StatusBarHeaderView mView;
    private final float mIconAlphaWhenOpaque;
   
    public HeaderTransitions(StatusBarHeaderView view) {
		super(view, new HeaderBackgroundDrawable(view.getContext()));
        mView = view;
        final Resources res = mView.getContext().getResources();
        mIconAlphaWhenOpaque = res.getFraction(R.dimen.status_bar_icon_drawing_alpha, 1, 1);
    }

    public void init() {
        applyModeBackground(-1, getMode(), false /*animate*/);
      
    }

	protected static class HeaderBackgroundDrawable
	extends BarTransitions.BarBackgroundDrawable {
        private static  Context mContext;

        private int mOverrideColor = 0;
		
        private int mOverrideGradientAlpha = 0;
        private static boolean isReverse= false;
		
        public HeaderBackgroundDrawable(final Context context) {
			super(context,R.drawable.notification_header_bg, R.color.system_secondary_color,
			R.color.system_secondary_color,
			R.color.system_secondary_color,
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
					public void onUpdateHeaderColor(final int previousColor, final int color) {
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
            return (mOverrideColor == 0 )? super.getColorOpaque() :
				 (mOverrideColor & 0x00ffffff | 0x7f000000);
        }

		@Override
		protected int getColorTransparent()
		{
			// TODO: Implement this method
			return mOverrideColor == 0  ? super.getColorOpaque():mOverrideColor;
		}
		

        @Override
        protected int getGradientAlphaOpaque() {
            return mOverrideGradientAlpha;
        }

        @Override
        protected int getGradientAlphaSemiTransparent() {
            return mOverrideGradientAlpha & 0x7f;
        }
        public void setColor(){
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

        private final HeaderBackgroundDrawable mDrawable;

        private GradientObserver(final HeaderBackgroundDrawable drawable,
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
