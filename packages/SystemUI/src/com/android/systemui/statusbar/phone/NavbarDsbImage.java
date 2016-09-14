package com.android.systemui.statusbar.phone;

import android.widget.ImageView;
import android.content.Context;
import android.os.Handler;
import android.database.ContentObserver;
import android.net.Uri;
import android.provider.Settings;
import java.util.ArrayList;
import android.os.UserHandle;
import com.android.systemui.R;
import android.util.AttributeSet;
import android.graphics.*;

public class NavbarDsbImage extends ImageView {
   
    private Handler mHandler;
    private int mOverrideIconColor = 0;
    ImageView iv;
	static boolean ena;
	static Context mContext;
	private SettingsObserver mObserver = null;

	public NavbarDsbImage(Context c){
		super(c);
		init(c);
	}

	public NavbarDsbImage(Context c,AttributeSet as){
		super(c,as);
		init(c);
	}

	public NavbarDsbImage(Context c,AttributeSet as,int d){
		super(c,as,d);
		init(c);
	}

	public void init(Context c){
		iv=this;
		mContext=c;
		mHandler = new Handler();
		if (mContext != null) {

            if (mObserver != null) {
                mContext.getContentResolver().unregisterContentObserver(mObserver);
            }
        }

        if (mObserver == null) {
            mObserver = new SettingsObserver(this,mHandler);
        }

		mContext.getContentResolver().registerContentObserver(
			Settings.System.getUriFor("DYNAMIC_NAVIGATION_BAR_STATE"),
			false, mObserver);
        ena= Settings.System.getIntForUser(mContext.getContentResolver(),
														   "DYNAMIC_NAVIGATION_BAR_STATE", 0, UserHandle.USER_CURRENT) == 1;

        BarBackgroundUpdater.addListener(new BarBackgroundUpdater.UpdateListener(this) {

				@Override
				public void onUpdateNavigationBarIconColor(final int previousIconColor,
													   final int iconColor) {
					mOverrideIconColor = iconColor;
                    
					apdet(mOverrideIconColor);
				}

		});

	}

	public boolean enable(){
		return ena;
	}

    public void apdet(final int targetColor){
	   mHandler.post(new Runnable() {

			@Override
			public void run() {
				if (iv!= null) {

					iv.setColorFilter(enable()?targetColor:Color.WHITE,PorterDuff.Mode.MULTIPLY);
					invalidate();

				}

			}

		});

	}

	private class SettingsObserver extends ContentObserver {
		private final NavbarDsbImage ndi;
        private SettingsObserver(final NavbarDsbImage nd,final Handler handler) {
            super(handler);
			ndi=nd;
        }

        @Override
        public final void onChange(final boolean selfChange) {
			ndi.ena=Settings.System.getInt(ndi.mContext.getContentResolver(),
										  "DYNAMIC_NAVIGATION_BAR_STATE", 0) ==1;

            ndi.apdet( Settings.System.getInt(ndi.mContext.getContentResolver(),
											 "DYNAMIC_NAVIGATION_BAR_STATE", 0) == 1?mOverrideIconColor:Color.WHITE);
        }

    }

}
