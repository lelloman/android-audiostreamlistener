package com.lelloman.audiostreamlistener.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.Animation;

import com.lelloman.audiostreamlistener.R;

/**
 * 	an animated wifi icon
 */
public class ProbingLoader extends View implements Animation.AnimationListener {

	private Paint mXferPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private Paint mColorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

	private Bitmap mAlphaMask;
	private Bitmap mBitmap;
	private Drawable mDrawable;
	private int mAnimationCounter;

	public ProbingLoader(Context context) {
		this(context, null);
	}

	public ProbingLoader(Context context, AttributeSet attrs) {
		super(context, attrs);

		mXferPaint.setColor(Color.BLACK);
		mXferPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

		mColorPaint.setColor(getAccentColor());
		mColorPaint.setStyle(Paint.Style.FILL);

		mDrawable = getResources().getDrawable(R.drawable.ic_probe_network_white);

		Animation animation = new Animation(){};
		animation.setDuration(200);
		animation.setAnimationListener(this);
		animation.setRepeatCount(Animation.INFINITE);
		startAnimation(animation);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);

		if(mBitmap != null){
			mBitmap.recycle();
			mAlphaMask.recycle();
		}

		mAlphaMask = Bitmap.createBitmap(w,h, Bitmap.Config.ARGB_8888);
		mBitmap = Bitmap.createBitmap(w,h, Bitmap.Config.ARGB_8888);

		drawBitmap();
	}

	private void drawBitmap(){
		int w = getWidth();
		int h = getHeight();

		mDrawable.setBounds(0,0,w,h);
		mDrawable.draw(new Canvas(mAlphaMask));

		Canvas canvasBitmap = new Canvas(mBitmap);
		canvasBitmap.drawColor(0xffffffff);
		canvasBitmap.drawCircle(w/2,h, (float) ((h/2.95) * mAnimationCounter),mColorPaint);
		canvasBitmap.drawBitmap(mAlphaMask,0,0, mXferPaint);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		if(mBitmap != null){
			canvas.drawBitmap(mBitmap,0,0,null);
		}
	}

	@Override
	public void onAnimationStart(Animation animation) {

	}

	@Override
	public void onAnimationEnd(Animation animation) {

	}

	@Override
	public void onAnimationRepeat(Animation animation) {
		mAnimationCounter++;
		if(mAnimationCounter > 3)
			mAnimationCounter = 0;

		drawBitmap();
	}

	private int getAccentColor() {
		try {
			TypedValue typedValue = new TypedValue();

			TypedArray a = getContext().obtainStyledAttributes(typedValue.data, new int[]{android.R.attr.colorAccent});
			int color = a.getColor(0, 0);

			a.recycle();
			return color;
		}catch (Exception e){
			return 0xff000000;
		}
	}
}
