package com.wkkim.escapeball;

import java.util.Random;

import net.daum.adam.publisher.AdView;
import net.daum.adam.publisher.AdView.AnimationType;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Join;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.leaderboard.LeaderboardScore;
import com.google.android.gms.games.leaderboard.LeaderboardVariant;
import com.google.android.gms.games.leaderboard.Leaderboards;
import com.google.android.gms.games.leaderboard.Leaderboards.LoadPlayerScoreResult;
import com.google.example.games.basegameutils.BaseGameActivity;

public class MainActivity extends BaseGameActivity {
	final  String TAG = "EscapeBall";
	final boolean DEBUG_BUILD = true;
	private enum STATE {START, PLAYING, DIE};
	private STATE mState = STATE.START;
	private AdView mAdView = null;

	SurfaceView mSfView;
	SurfaceHolder mSfHolder;
	Handler mDrawHandler;
	Bitmap mBgBitmap;
	Bitmap mStartBitmap;
	Rect mStartSrcRect;
	RectF mStartDstRect;

	boolean mDestroy = false;
	float mBallX, mBallY, mBallRadius, mBallShade;
	RectF mBallRect;
	boolean mBallShadeToggle;
	Paint mBallPaint, mBgPaint, mArcPaint, mCountPaint;
	Rect mBgOriSrc, mBgSrc, mBgDst;
	long mPreTime = System.currentTimeMillis();

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
	float mBgMoveX = 0;
	float mBgMoveY = 0;
	
	final String BESTCOUNT = "BESTCOUNT";
	int mCount = 0;
	int mLevel = 0;
	int mBestCount = 0;
	SharedPreferences mSharedPreference;	

	Random mRandom;
	int mCollision = 0;
	
	private final int FRAME = 30;
	
	private SoundPool mSoundPool = null;
	private int mArcSound, mConflictSound, mClickSound;
	
	final int BALL_COLOR[][] = { {0xFFF0E68C, 0x008E2323}, {0xffafeeee, 0x00fdf5e6}, {0xffadff2f, 0x5f9ea0}, 
			{0xffffc0cb, 0xffd700},	{0xffb5a642, 0x98fb98}};
	private String LB_ID;
	
	

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		initAdam();
		
		setRequestedClients(BaseGameActivity.CLIENT_GAMES);
		LB_ID = (String) getResources().getString(R.string.ld_id);
		
	/*	if (DEBUG_BUILD) {
			enableDebugLog(true, "MyActivity");
		}*/
		
		
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
				mDrawHandler.sendEmptyMessageDelayed(0, 1000/FRAME);
				drawSurface();
			}
		};
		mBallPaint = new Paint();
		mSfHolder.setFormat(PixelFormat.TRANSPARENT);
		mSfHolder.addCallback(new BallViewCallback());
		mBgBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.background);
		int bgWidth = mBgBitmap.getWidth();
		int bgHeight = mBgBitmap.getHeight();
		mBgSrc = new Rect((int)(bgWidth * 0.2), (int)(bgHeight * 0.2)
				, (int)(bgWidth * 0.8), (int)(bgHeight*0.8));
		
		BitmapShader bgShader = new BitmapShader(mBgBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
		mBgPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
		mBgPaint.setShader(bgShader);

		mStartBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.start);
		mStartSrcRect = new Rect(0, 0, mStartBitmap.getWidth(), mStartBitmap.getHeight());
		mStartDstRect = new RectF(mBallX - mBallRadius * 3, mBallY + mBallRadius * 2
				, mBallX + mBallRadius * 3, mBallY + mBallRadius * 4);
		
		mArcPaint = new Paint();
		mArcPaint.setStyle(Paint.Style.STROKE);
		mArcPaint.setStrokeJoin(Join.ROUND);
		mArcPaint.setStrokeWidth(30);
		mArcPaint.setColor(Color.DKGRAY);
		mArcRect = new RectF();
		//mArcAccel = mBallRadius / 200;
		
		mCountPaint = new Paint();
		mCountPaint.setColor(0x88CCFF99);
		mCountPaint.setTextAlign(Paint.Align.CENTER);
		mCountPaint.setTextSize(mBallRadius * 3);

		mSharedPreference = getPreferences(Context.MODE_PRIVATE);
		mBestCount = mSharedPreference.getInt(BESTCOUNT, 0);
		
		initSoundPool();
		resetValue();
	}

	@Override
	protected void onPause() {
		super.onPause();

	}

	@Override
	protected void onResume() {
		super.onResume();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mAdView != null) {
			mAdView.destroy();
			mAdView = null;
		}
		releaseSoundPool();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		if (isSignedIn()) {};

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.score:
			if (isSignedIn()) {
				startActivityForResult(Games.Leaderboards.getLeaderboardIntent(getApiClient(), LB_ID), 0);
			} else {
				beginUserInitiatedSignIn();
			}
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	private void initSoundPool() {
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		mSoundPool = new SoundPool(2, AudioManager.STREAM_MUSIC, 0);
		mArcSound = mSoundPool.load(getApplicationContext(), R.raw.arc, 1);
		mConflictSound = mSoundPool.load(getApplicationContext(), R.raw.conflict, 1);
		mClickSound = mSoundPool.load(getApplicationContext(), R.raw.click, 1);
	}
	
	private void releaseSoundPool() {
		if (mSoundPool != null) {
			mSoundPool.release();
			mSoundPool = null;
		}
	}

	private void resetValue() {
		mArcAccel = mBallRadius / 50;
		mCount = 0;
		mLevel = 0;
		mMoveX = mMoveY = mBgMoveX = mBgMoveY = 0;
		mArcDistance = -1f;
		int bgWidth = mBgBitmap.getWidth();
		int bgHeight = mBgBitmap.getHeight();
		mBgSrc.set((int)(bgWidth * 0.2), (int)(bgHeight * 0.2)
				, (int)(bgWidth * 0.8), (int)(bgHeight*0.8));
	}


	private void drawArc(Canvas cvs) {
		
		if (mMoveX == 0 && mState == STATE.PLAYING) {
			if (mTouchX > 0) {
				mSoundPool.play(mClickSound, 1.0f, 1.0f, 0, 0, 1.0f);
				float x = (mTouchX - mBallX) * -1f;
				float y = (mTouchY - mBallY) * -1f;
				float len = (float) Math.sqrt(x*x + y*y);
				float mul = mBallRadius / len;
				mMoveX = x * mul;
				mMoveY = y * mul;
				mBgMoveX = mMoveX;
				mBgMoveY = mMoveY;
				mTouchX = -1;
			}
			mArcSpeed += mArcAccel;
			mArcDistance -= mArcSpeed/2;
			if (mArcDistance <= 0) {
				mArcDistance = mBallRadius * 14;
				mArcSpeed = mBallRadius / 4;
				mArcStart = (float)mRandom.nextInt(360);
				//mArcPaint.setColor(Color.LTGRAY);
				mArcPaint.setARGB(255, mRandom.nextInt(200) + 55, mRandom.nextInt(200) + 55, mRandom.nextInt(200) + 55);
				//Log.d(TAG,"Color : " + mArcPaint.getColor());
				mCount++;
				mArcAccel *= 1.05;
				if (mCount % 10 == 0) {
					mLevel++;
					if (mLevel >= BALL_COLOR.length)
						mLevel = 0;
				}

				mSoundPool.stop(mArcSound);
				mSoundPool.play(mArcSound, 1.0f, 1.0f, 0, 0, 1.0f);
			}
		}

		mArcRect.set(mBallX - mArcDistance + mMoveX, mBallY - mArcDistance + mMoveY
				, mBallX + mArcDistance + mMoveX, mBallY + mArcDistance + mMoveY);
		
		if (mState == STATE.PLAYING) {
			if (mMoveX * mMoveX + mMoveY * mMoveY > mBallRadius * mBallRadius * 144) {
				mMoveX = mMoveY = mBgMoveX = mBgMoveY = 0;
				mArcDistance = -1f;
			} else {
				mMoveX += mMoveX * .25F;
				mMoveY += mMoveY * .25F;
			}
		} else { /* DIE */
			mMoveX += mMoveX * 0.005F;
			mMoveY += mMoveY * 0.005F;
		}
		
		if (mState == STATE.DIE) {
			if (--mCollision == 0) {
				mState = STATE.START;
				resetValue();
			}
		}

		cvs.drawArc(mArcRect, mArcStart, mArcSweep, false, mArcPaint);

	}
	


	private void drawBackground(Canvas cvs) {
		mBgSrc.offset((int)(mBgMoveX/10), (int)(mBgMoveY/10));
		if (mBgSrc.left < 0 || mBgSrc.top < 0 || mBgSrc.right > mBgBitmap.getWidth() || mBgSrc.bottom > mBgBitmap.getHeight()) {
			int bgWidth = mBgBitmap.getWidth();
			int bgHeight = mBgBitmap.getHeight();
			mBgSrc.set((int)(bgWidth * 0.2), (int)(bgHeight * 0.2)
					, (int)(bgWidth * 0.8), (int)(bgHeight*0.8));
		}
		cvs.drawBitmap(mBgBitmap, mBgSrc, mBgDst, mBgPaint);
		//cvs.drawColor(Color.WHITE);
	}
	
	private void drawStart(Canvas cvs) {
		cvs.drawBitmap(mStartBitmap, mStartSrcRect, mStartDstRect, mBgPaint);
	}
	
	private void drawBall(Canvas cvs) {
		if (mBallShadeToggle)
			mBallShade += 2f;
		else
			mBallShade -= 2f;

		if (mBallShade >= mBallRadius * 2)
			mBallShadeToggle = false;
		else if (mBallShade < mBallRadius / 2)
			mBallShadeToggle = true;

		if (mState == STATE.DIE) {
			mBallPaint.setShader(new RadialGradient(mBallX, mBallY, mBallShade, Color.BLACK, Color.WHITE, TileMode.REPEAT));
		} else {
			//mBallPaint.setShader(new RadialGradient(mBallX, mBallY, mBallShade, 0xFFF0E68C, 0x8E2323, TileMode.CLAMP));
			mBallPaint.setShader(new RadialGradient(mBallX, mBallY, mBallShade, BALL_COLOR[mLevel][0], BALL_COLOR[mLevel][1], TileMode.CLAMP));
		}
		cvs.drawCircle(mBallX, mBallY, mBallRadius, mBallPaint);
		
	}

	private void drawSurface() {
		Canvas cvs = mSfHolder.lockCanvas();
		if (cvs == null) {
			Log.d(TAG, "Lock canvas fail");
			return;
		}
		/*
 		long curTime = System.currentTimeMillis();
		Log.d(TAG, "drawDiff : " + (curTime - mPreTime));
		mPreTime = curTime;
		 */
		drawBackground(cvs);
		drawBall(cvs);
		drawCount(cvs);
		
		if (mState == STATE.START)
			drawStart(cvs);
		else { /* PLAYING or DIE */
			drawArc(cvs);
			if (mState == STATE.PLAYING) {
				/* DIE!! */
				if (detectCollision(cvs)) {
					mSoundPool.stop(mArcSound);
					mSoundPool.play(mConflictSound, 1.0f, 1.0f, 0, 0, 1.0f);
					mState = STATE.DIE;
					mCollision = 40;
					int preBestCount = mSharedPreference.getInt(BESTCOUNT, 0);
					if (mBestCount > preBestCount) {
						SharedPreferences.Editor editor = mSharedPreference.edit();
						editor.putInt(BESTCOUNT, mBestCount);
						editor.commit();
					}
					if (isSignedIn()) { /* submit everygame */
						Games.Leaderboards.submitScore(getApiClient(), LB_ID, mBestCount);
						if (mBestCount > preBestCount) {
							startActivityForResult(Games.Leaderboards.getLeaderboardIntent(getApiClient(), LB_ID), 0);
						}
					}
				}
			}
		}

		mSfHolder.unlockCanvasAndPost(cvs);
	}

	private void drawCount(Canvas cvs) {
		//mCountPaint.setColor(mArcPaint.getColor());
		if (mBestCount < mCount)
			mBestCount = mCount;
		cvs.drawText(mCount + "/" + mBestCount, mBallX, mBallY/4, mCountPaint);
	}



	private boolean detectCollision(Canvas cvs) {
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

			//cvs.drawRect(interRect, p);
			if (interRect.contains(mBallRect)) {
				return false;
			}

			if (mBallRect.contains(arcInnerRect)) {
				return true;
			}

			p.setColor(Color.BLACK);
			//Log.d(TAG, "Interrect : " + interRect + " Ball " + mBallRect

			if (mBallRect.top == interRect.top && mBallRect.bottom == interRect.bottom) {
				if (mBallRect.left == interRect.left) {
					a.set(interRect.right, interRect.top);
					b.set(interRect.right, interRect.bottom);
					//Log.d(TAG, "left " + a + b);
				} else {
					a.set(interRect.left, interRect.top);
					b.set(interRect.left, interRect.bottom);
					//Log.d(TAG, "right " + a + b);
				}
			} else if (mBallRect.left == interRect.left && mBallRect.right == interRect.right) {
				if (mBallRect.top == interRect.top) {
					a.set(interRect.left, interRect.bottom);
					b.set(interRect.right, interRect.bottom);
					//Log.d(TAG, "top " + a + b);
				} else {
					a.set(interRect.left, interRect.top);
					b.set(interRect.right, interRect.top);
					//Log.d(TAG, "bottom " + a + b);
				}
			} else if (mBallRect.left == interRect.left) {  
				if (interRect.top == mBallRect.top) {
					a.set(interRect.left, interRect.bottom);
					b.set(interRect.right, interRect.top);
					//Log.d(TAG, "left top " + a + b);
				} else if (interRect.bottom == mBallRect.bottom) {
					a.set(interRect.left, interRect.top);
					b.set(interRect.right, interRect.bottom);
					//Log.d(TAG, "left bottom " + a + b);
				} else {
					a.set(interRect.left, interRect.top);
					b.set(interRect.left, interRect.bottom);
					//Log.d(TAG, "left inc " + a + b);
				}
			} else if (mBallRect.right == interRect.right) {
				if (interRect.top == mBallRect.top) {
					a.set(interRect.left, interRect.top);
					b.set(interRect.right, interRect.bottom);
					//Log.d(TAG, "right top " + a + b);
				} else if (interRect.bottom == mBallRect.bottom) {
					a.set(interRect.left, interRect.bottom);
					b.set(interRect.right, interRect.top);
					//Log.d(TAG, "right bottom " + a + b);
				} else {
					a.set(interRect.right, interRect.top);
					b.set(interRect.right, interRect.bottom);
					//Log.d(TAG, "right inc " + a + b);
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
			final float MARGIN_DEGREE = 10;

			//Log.d(TAG, "-> Ang A : " + angA + "  Ang B : " + angB + " (" + arcHoleStart + "/" + arcHoleEnd +")");
			if (arcHoleStart < arcHoleEnd) { 
				if (angA < arcHoleStart - MARGIN_DEGREE || angB < arcHoleStart - MARGIN_DEGREE
						|| angA > arcHoleEnd + MARGIN_DEGREE || angB > arcHoleEnd + MARGIN_DEGREE) {
					Log.d(TAG, "Ooop1 Ang A : " + angA + "  Ang B : " + angB + " (" + arcHoleStart + "/" + arcHoleEnd +")");
					return true;

				}
			} else {
				if ((angA < arcHoleStart - MARGIN_DEGREE && angA > arcHoleEnd + MARGIN_DEGREE)
						|| (angB < arcHoleStart - MARGIN_DEGREE && angB > arcHoleEnd + MARGIN_DEGREE) ) {
					Log.d(TAG, "Ooops2 Ang A : " + angA + "  Ang B : " + angB + " (" + arcHoleStart + "/" + arcHoleEnd +")");

					return true;
				}
			}

			//Log.d(TAG, "After Ang A : " + angA + "  Ang B : " + angB);

		}
		//cvs.drawLine(a.x, a.y, b.x, b.y, p);
		return false;
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
			mBgDst = holder.getSurfaceFrame();
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
						if (mState == STATE.START) {
							if (mStartDstRect.contains(event.getX(), event.getY())) {
								mSoundPool.play(mClickSound, 1.0f, 1.0f, 0, 0, 1.0f);
								mState = STATE.PLAYING;
							}
						}
						mTouchX = mTouchY = -1;
						break;
					}

					return true;
				}
			});
			
			//mBgBitmap = Bitmap.createScaledBitmap(mBgBitmap, mBgDst.width(), mBgDst.height(), false);
			mDrawHandler.sendEmptyMessage(0);
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			mDrawHandler.removeMessages(0);
		}
	}


	@Override
	public void onSignInFailed() {
	}

	@Override
	public void onSignInSucceeded() {
		PendingResult<Leaderboards.LoadPlayerScoreResult> score = 
				Games.Leaderboards.loadCurrentPlayerLeaderboardScore(getApiClient(), LB_ID,
						LeaderboardVariant.TIME_SPAN_ALL_TIME, LeaderboardVariant.COLLECTION_PUBLIC);
		score.setResultCallback(new ResultCallback<Leaderboards.LoadPlayerScoreResult>() {
			@Override
			public void onResult(LoadPlayerScoreResult lpsr) {
				LeaderboardScore score = lpsr.getScore();
				if (score != null) {
					String strScore = score.getDisplayScore();
					int numScore = Integer.parseInt(strScore);
					Log.i(TAG, "onResult calling " + numScore);
					if (numScore > mBestCount) {
						mBestCount = numScore;
						SharedPreferences.Editor editor = mSharedPreference.edit();
						editor.putInt(BESTCOUNT, mBestCount);
						editor.commit();
					}
				}
			}} );

	}
	private void initAdam() {
		mAdView = (AdView) findViewById(R.id.adview);
		mAdView.setClientId("8a46Z46T14486e3e750");
		mAdView.setRequestInterval(30);
		//mAdView.setAnimationType(AnimationType.FLIP_HORIZONTAL);
		mAdView.setAnimationType(AnimationType.NONE);
		mAdView.setVisibility(View.VISIBLE);
	}
}
