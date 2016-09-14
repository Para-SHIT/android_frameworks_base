package com.android.systemui.statusbar.phone;

import android.widget.TextView;
import android.content.Context;
import android.os.Handler;
import android.database.ContentObserver;
import android.net.Uri;
import android.provider.Settings;
import android.os.UserHandle;
import java.util.ArrayList;
import com.android.systemui.R;
import android.util.AttributeSet;

public class DsbText extends TextView {

    private static Handler mHandler;
	private int mOverrideIconColor = 0;
    TextView tv;
	static Context mContext;

	public DsbText(Context c,AttributeSet as){
		super(c,as);
		tv=this;
		mContext=c;
		mHandler= new Handler();

		BarBackgroundUpdater.addListener(new BarBackgroundUpdater.UpdateListener(this) {

				@Override
				public void onUpdateStatusBarIconColor(final int previousIconColor,
													   final int iconColor) {
					mOverrideIconColor=iconColor;

					final int targetColor = (mOverrideIconColor== 0 )? 0xffffffff :mOverrideIconColor ;

					apdet(targetColor);
				}

		});

	}

	public void apdet(final int targetColor){
		mHandler.post(new Runnable() {

			@Override
			public void run() {

				setTextColor(targetColor);
				invalidate();

			}

		});

	}

}
