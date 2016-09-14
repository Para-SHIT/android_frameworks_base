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

public class DsbImage extends ImageView {
   
    private Handler mHandler;
    private int mOverrideIconColor = 0;
    ImageView iv;
	static Context mContext;
	
	public DsbImage(Context c,AttributeSet as){
		super(c,as);
		iv=this;
		mContext=c;
		mHandler = new Handler();

        BarBackgroundUpdater.addListener(new BarBackgroundUpdater.UpdateListener(this) {

				@Override
				public void onUpdateStatusBarIconColor(final int previousIconColor,
														   final int iconColor) {
					mOverrideIconColor = iconColor;
					
					final int targetColor = (mOverrideIconColor== 0)? 0xffffffff :mOverrideIconColor;

					apdet(targetColor);
				}

		});

	}

    public void apdet(final int targetColor){
		mHandler.post(new Runnable() {

				@Override
				public void run() {
					if (iv!= null) {

						iv.setColorFilter(targetColor);
						invalidate();

					}
				}

		});
	}

}
