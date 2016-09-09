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
public class DsbImage extends ImageView
{   
    private Handler mHandler;
    //private int mReverseOverrideIconColor = 0;
    private int mOverrideIconColor = 0;
    ImageView a;
	//private Runnable anu;
	//static boolean isReverse= false;
	static Context mContext;
	//private SettingsObserver mObserver = null;

	/*public DsbImage(Context c){
		super(c);
		init(c);
	}*/
	public DsbImage(Context c,AttributeSet as){
		super(c,as);
		/*init(c);
	}
	public DsbImage(Context c,AttributeSet as,int d){
		super(c,as,d);
		init(c);
	}
	public void init(Context c){*/
		a=this;
		mContext=c;
		mHandler = new Handler();
		/*if (mContext != null) {
            //mContext.unregisterReceiver(sReceiver);

            if (mObserver != null) {
                mContext.getContentResolver().unregisterContentObserver(mObserver);
            }
        }

        if (mObserver == null) {
            mObserver = new SettingsObserver(this,mHandler);
        }

        mContext.getContentResolver().registerContentObserver(
			Settings.System.getUriFor("QS_REVERSE"),
			false, mObserver);

        isReverse= Settings.System.getInt(mContext.getContentResolver(),
										  "QS_REVERSE", 0) == 0;*/

        BarBackgroundUpdater.addListener(new BarBackgroundUpdater.UpdateListener(this) {

				@Override
				public void onUpdateStatusBarIconColor(final int previousIconColor,
														   final int iconColor) {
					//mPreviousOverrideIconColor = previousIconColor;
					mOverrideIconColor = iconColor;
					/*anu=new Runnable() {
					 @Override
					 public void run() {
					 invalidate();
					 }};*/
					final int targetColor = (/*isReverse?*/mOverrideIconColor== 0 /*: mReverseOverrideIconColor==0*/)? 0xffffffff :/*isReverse?*/mOverrideIconColor/*:mReverseOverrideIconColor*/ ;

					apdet(targetColor);
				}
				//@Override
				//public void onUpdateNavigationBarColor(final int previousIconColor,
				//							   final int iconColor) {
				//mPreviousOverrideIconColor = previousIconColor;
				//mReverseOverrideIconColor = iconColor;
				/*anu=new Runnable() {
				 @Override
				 public void run() {
				 invalidate();
				 }};*/
				//final int targetColor = (isReverse?mOverrideIconColor== 0 : mReverseOverrideIconColor==0)? 0xffffffff :isReverse?mOverrideIconColor:mReverseOverrideIconColor ;

				//apdet(targetColor);
				//}


			});
		/*if (a!= null) {
		 final int targetColor = (isReverse?mOverrideIconColor== 0 : mReverseOverrideIconColor==0)? 0xffffffff :isReverse?mOverrideIconColor:mReverseOverrideIconColor ;

		 a.setColorFilter(targetColor);
		 // mHandler.removeCallbacks(anu);
		 //mHandler.postDelayed(anu, 50);

		 }*/
	}
    public void apdet(final int targetColor){
		mHandler.post(new Runnable() {

				@Override
				public void run() {
					if (a!= null) {
						//final int targetColor = (isReverse?mOverrideIconColor== 0 : mReverseOverrideIconColor==0)? 0xffffffff :isReverse?mOverrideIconColor:mReverseOverrideIconColor ;
						//if(targetColor!= 0){
						a.setColorFilter(targetColor);
						invalidate();
						//mHandler.removeCallbacks(anu);
						//mHandler.postDelayed(anu, 5);
						//}


					}

				}


			});
		//mHandler.removeCallbacks(anu);
		//mHandler.postDelayed(anu, 5);

	}
	/*private class SettingsObserver extends ContentObserver {
		private final DsbImage ds;
        private SettingsObserver(final DsbImage d,final Handler handler) {
            super(handler);
			ds=d;
        }

        @Override
        public final void onChange(final boolean selfChange) {
			ds.isReverse=Settings.System.getInt(ds.mContext.getContentResolver(),
												"QS_REVERSE", 0) ==1;

            ds.apdet( Settings.System.getInt(ds.mContext.getContentResolver(),
											 "QS_REVERSE", 0) == 1?mOverrideIconColor:mReverseOverrideIconColor );


            //ds.invalidate();
			//ds.apdet();
        }
    }*/


}
