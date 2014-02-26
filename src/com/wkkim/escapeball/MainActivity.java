package com.wkkim.escapeball;

import java.util.Random;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Join;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader.TileMode;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

public class MainActivity extends Activity {
	final  String TAG = "EscapeBall";
	
	SurfaceView mSfView;
	SurfaceHolder mSfHolder;
	Handler mDrawHandler;
	Bitmap mBgBitmap;
	
	boolean mDestroy = false;
	float mBallX, mBallY, mBallRadius, mBallShade;
	RectF mBallRect;
	boolean mBallShadeToggle;
	Paint mBallPaint, mBgPaint, mArcPaint, mCountPaint;
	Rect mBgSrc, mBgDst;
	long preTime = System.currentTimeMillis();
	
	float mArcDistance = 0f;
	float mArcSpeed = 0f;
	float mArcAccel = 0f;
	float mArcStart = 0f;
	float mArcSweep = 270f;
	RectF mArcRect = null;
	
	float mTouchX = -1;
	float mTouchY = -1;
	
	float mMoveX = 0;
	float mMoveY = 0;
	
	final String BESTCOUNT = "BESTCOUNT";
	int mCount = 0;
	int mBestCount = 0;
	SharedPreferences mSharedPreference;	
	
	Random mRandom;
	int mCollision = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mRandom = new Random(System.currentTimeMillis());
		mSfView = (SurfaceView)findViewById(R.id.sfView);
		mSfView.setDrawingCacheEnabled(true);
		mSfHolder = mSfView.getHolder();
		
		DisplayMetrics screenMetrics = new DisplayMetrics();		
		getWindowManager().getDefaultDisplay().getMetrics(screenMetrics);
		
		mBallX = screenMetrics.widthPixels / 2f;
		mBallY = screenMetrics.heightPixels / 2f;
		mBallRadius = (mBallX < mBallY 
				? mBallX : mBallY)  / 7f;
		mBallShade = mBallRadius / 2;
		mBallShadeToggle = true;
		
		float len = (float) (mBallRadius / Math.sqrt(2));
		mBallRect = new RectF(mBallX - len, mBallY - len, mBallX + len, mBallY + len);
		
		mDrawHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				mDrawHandler.sendEmptyMessageDelayed(0, 20);
				drawBall();
			}
		};
		mBallPaint = new Paint();
		mSfHolder.setFormat(PixelFormat.TRANSPARENT);
		mSfHolder.addCallback(new BallViewCallback());
		mBgBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.background);
		mBgSrc = new Rect(0, 0, mBgBitmap.getWidth(), mBgBitmap.getHeight());
		mBgPaint = new Paint();
		mArcPaint = new Paint();
		mArcPaint.setStyle(Paint.Style.STROKE);
		mArcPaint.setStrokeJoin(Join.ROUND);
		mArcPaint.setStrokeWidth(30);
		//mArcAccel = mBallRadius / 200;
		
		mSharedPreference = getPreferences(Context.MODE_PRIVATE);
		mBestCount = mSharedPreference.getInt(BESTCOUNT, 0);
		
		resetValue();
	}

	@Override
	protected void onPause() {
		super.onPause();
		
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	private void resetValue() {
		mArcAccel = mBallRadius / 200;
		mCount = 0;
	}
	
	
	private void drawArc(Canvas cvs) {
		if (mMoveX == 0) {
			mArcSpeed += mArcAccel;
			mArcDistance -= mArcSpeed;
			if (mCount % 5 == 0) {
				mArcAccel *= 1.01; 
			}
			if (mArcDistance < 0) {
				mArcDistance = mBallRadius * 5;
				mArcSpeed = 0;
				mArcStart = (float)mRandom.nextInt(360);
				mArcPaint.setColor(Color.LTGRAY);
				mArcPaint.setARGB(255, mRandom.nextInt(255), mRandom.nextInt(255), mRandom.nextInt(255));
				mCount++;
			}
		}

		mArcRect = new RectF(mBallX - mArcDistance + mMoveX, mBallY - mArcDistance + mMoveY
				, mBallX + mArcDistance + mMoveX, mBallY + mArcDistance + mMoveY);
		
		if (Math.abs(mMoveX) > mBallRadius * 8) {
			mMoveX = mMoveY = 0;
			mArcDistance = -1f;
		} else {
			if (mCollision < 10) {
				mMoveX += mMoveX * .2F;
				mMoveY += mMoveY * .2F;
			}
		}
		//Log.d(TAG, "ARC start " + mArcStart);
		
		cvs.drawArc(mArcRect, mArcStart, mArcSweep, false, mArcPaint);
	
	}
	
	void drawBall() {
		Canvas cvs = mSfHolder.lockCanvas();
		if (cvs == null)
			return;

		/* To check performance */
/*		long curTime = System.currentTimeMillis();
		Log.d(TAG, "Time : " + (curTime - preTime));
		preTime = curTime; */
		
		/* Draw Background */
		cvs.drawBitmap(mBgBitmap, 0f, 0f, mBgPaint);
		
		/* Draw Ball */
		if (mBallShadeToggle)
			mBallShade += 2f;
		else
			mBallShade -= 2f;;
		
		if (mBallShade >= mBallRadius * 2)
			mBallShadeToggle = false;
		else if (mBallShade < mBallRadius / 2)
			mBallShadeToggle = true;
		
		if (mCollision > 0) {
			mBallPaint.setShader(new RadialGradient(mBallX, mBallY, mBallShade, Color.BLACK, Color.WHITE, TileMode.REPEAT));
			//mCollision--;
		} else {
			mBallPaint.setShader(new RadialGradient(mBallX, mBallY, mBallShade, Color.RED, Color.GREEN, TileMode.CLAMP));
		}
		cvs.drawCircle(mBallX, mBallY, mBallRadius, mBallPaint);
		if (mTouchX > 0 && mMoveX == 0) {
			float x = (mTouchX - mBallX) * -1f;
			float y = (mTouchY - mBallY) * -1f;
			float len = (float) Math.sqrt(x*x + y*y);
			//Log.d(TAG, "Len : " + len + " x : " + x + " y : "+y);
			float mul = mBallRadius / len;
			mMoveX = x * mul;
			mMoveY = y * mul;
			mTouchX = -1;
		}
		drawArc(cvs);
		drawCount(cvs);
		if (detectCollision(cvs) && mCollision == 0) {
			mCollision = 100;
			resetValue();
			SharedPreferences.Editor editor = mSharedPreference.edit();
			editor.putInt(BESTCOUNT, mBestCount);
			editor.commit();
		}
			
		mSfHolder.unlockCanvasAndPost(cvs);
	}
	
	private void drawCount(Canvas cvs) {
		if (mCount == 1) {
			mCountPaint = new Paint();
			mCountPaint.setTextSize(mBallRadius * 3);
		}
		mCountPaint.setColor(mArcPaint.getColor());
		if (mBestCount < mCount)
			mBestCount = mCount;
		cvs.drawText(mCount + "/" + mBestCount, mBallX - mBallRadius, mBallY/4, mCountPaint);
	}
	
	
	
	boolean flag = false;
	private boolean detectCollision(Canvas cvs) {
		/*float arcX = mArcRect.right - mArcRect.left - mBallX;
		float arcY = mArcRect.bottom - mArcRect.top - mBallY;
		float left = mArcRect.left - mBallX;
		float right = mArcRect.right - mBallX;
		float top = mArcRect.top - mBallY;
		float bottom = mArcRect.bottom - mBallY;
		*/
		RectF interRect = new RectF();
		PointF a = new PointF();
		PointF b = new PointF();
		//Log.d(TAG, mBallRect + " " + mArcRect);
		Paint p = new Paint();
		p.setStrokeWidth(20);
		p.setColor(Color.WHITE);
		
		float arcRadius = mArcRect.right - mArcRect.centerX();
		float arcInnerLine = (float) (arcRadius / Math.sqrt(2));
		if (mBallRect.contains(mArcRect)) {
			return true;
		}

		RectF arcInnerRect = new RectF(mArcRect.centerX() - arcInnerLine, mArcRect.centerY() - arcInnerLine
				, mArcRect.centerX() + arcInnerLine, mArcRect.centerY() + arcInnerLine);
		if (interRect.setIntersect(mBallRect, arcInnerRect)) {
			
			cvs.drawRect(interRect, p);
			
			if (interRect.contains(mBallRect)) {
				return false;
			}
			p.setColor(Color.BLACK);
			//Log.d(TAG, "Interrect : " + interRect + " Ball " + mBallRect
			
			if (mBallRect.top == interRect.top && mBallRect.bottom == interRect.bottom) {
				if (mBallRect.left == interRect.left) {
					a.set(interRect.right, interRect.top);
					b.set(interRect.right, interRect.bottom);
					Log.d(TAG, "left " + a + b);
				} else {
					a.set(interRect.left, interRect.top);
					b.set(interRect.left, interRect.bottom);
					Log.d(TAG, "right " + a + b);
				}
			} else if (mBallRect.left == interRect.left && mBallRect.right == interRect.right) {
				if (mBallRect.top == interRect.top) {
					a.set(interRect.left, interRect.bottom);
					b.set(interRect.right, interRect.bottom);
					Log.d(TAG, "top " + a + b);
				} else {
					a.set(interRect.left, interRect.top);
					b.set(interRect.right, interRect.top);
					Log.d(TAG, "bottom " + a + b);
				}
			} else if (mBallRect.left == interRect.left) {
				if (interRect.top == mBallRect.top) {
					a.set(interRect.left, interRect.bottom);
					b.set(interRect.right, interRect.top);
					Log.d(TAG, "left top " + a + b);
				} else if (interRect.bottom == mBallRect.bottom) {
					a.set(interRect.left, interRect.top);
					b.set(interRect.right, interRect.bottom);
					Log.d(TAG, "left bottom " + a + b);
				} else {
					a.set(interRect.left, interRect.top);
					b.set(interRect.left, interRect.bottom);
					Log.d(TAG, "left inc " + a + b);
				}
			} else if (mBallRect.right == interRect.right) {
				if (interRect.top == mBallRect.top) {
					a.set(interRect.left, interRect.top);
					b.set(interRect.right, interRect.bottom);
					Log.d(TAG, "right top " + a + b);
				} else if (interRect.bottom == mBallRect.bottom) {
					a.set(interRect.left, interRect.bottom);
					b.set(interRect.right, interRect.top);
					Log.d(TAG, "right bottom " + a + b);
				} else {
					a.set(interRect.right, interRect.top);
					b.set(interRect.right, interRect.bottom);
					Log.d(TAG, "right inc " + a + b);
				}
			}
			a.set(a.x - mArcRect.centerX(), (a.y - mArcRect.centerY()));
			b.set(b.x - mArcRect.centerX(), (b.y - mArcRect.centerY()));
			float angA = (float) (Math.atan(a.y/a.x) * 180 / Math.PI);
			float angB = (float) (Math.atan(b.y/b.x) * 180 / Math.PI);
			
			//Log.d(TAG, "Before Ang A : " + angA + "  Ang B : " + angB + " mArcStart : " + mArcStart);
			angA = changeDegree(a, angA);
			angB = changeDegree(b, angB);
			//mArcStart, mArcSweep
			float arcHoleStart = mArcStart + mArcSweep;
			if (arcHoleStart > 360)
				arcHoleStart -= 360;
			float arcHoleEnd = mArcStart;
			final float MARGIN_DEGREE = 0;
			
			Log.d(TAG, "-> Ang A : " + angA + "  Ang B : " + angB + " (" + arcHoleStart + "/" + arcHoleEnd +")");
			if (arcHoleStart < arcHoleEnd) { 
				if (angA < arcHoleStart - MARGIN_DEGREE || angB < arcHoleStart - MARGIN_DEGREE
						|| angA > arcHoleEnd + MARGIN_DEGREE || angB > arcHoleEnd + MARGIN_DEGREE) {
					if (flag == false) {
						Log.d(TAG, "Ooop1 Ang A : " + angA + "  Ang B : " + angB + " (" + arcHoleStart + "/" + arcHoleEnd +")");
						flag = true;
					}
					return true;
					
				}
			} else {
				if ((angA < arcHoleStart - MARGIN_DEGREE && angA > arcHoleEnd + MARGIN_DEGREE)
						|| (angB < arcHoleStart - MARGIN_DEGREE && angB > arcHoleEnd + MARGIN_DEGREE) ) {
					if (flag == false) {
						Log.d(TAG, "Ooops2 Ang A : " + angA + "  Ang B : " + angB + " (" + arcHoleStart + "/" + arcHoleEnd +")");
						flag = true;
					}
					return true;
				}
			}
			
			//Log.d(TAG, "After Ang A : " + angA + "  Ang B : " + angB);
			
		}
		return false;
		//cvs.drawLine(a.x, a.y, b.x, b.y, p);
		
	}
	
	private float changeDegree(PointF p, float degree) {
		if ((p.x < 0 && p.y > 0) || (p.x < 0 && p.y < 0))
			return 180f + degree;
		if (p.x > 0 && p.y < 0)
			return 360f + degree;
		return degree;
	}
	
	
	private class BallViewCallback implements SurfaceHolder.Callback {

		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			mSfView.setOnTouchListener(new View.OnTouchListener() {
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					switch(event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						mTouchX = event.getX();
						mTouchY = event.getY();
						break;
					case MotionEvent.ACTION_UP:
						mTouchX = mTouchY = -1;
						break;
					}

					return true;
				}
			});
			mBgDst = holder.getSurfaceFrame();
			mBgBitmap = Bitmap.createScaledBitmap(mBgBitmap, mBgDst.width(), mBgDst.height(), false);
			mDrawHandler.sendEmptyMessage(0);
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			mDrawHandler.removeMessages(0);
		}
	}
	
}
