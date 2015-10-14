package com.xzy.andoridcrop.cropImage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Created by zhengyang on 15/3/30.
 */
public class TMImlabCropView extends View implements ScaleGestureDetector.OnScaleGestureListener {

    private Context mContext;

    private ScaleGestureDetector mScaleGestureDetector;

    private float mFirstX, mFirstY;

    private float mLastX, mLastY;

    //TODO 和完整图片的对应关系
    private float mCenterX, mCenterY;

    private float mMinScale;

//    private float mScale;

    private int mWidth, mHeight;

    private int mHalfWidth, mHalfHeight;

    private int mRotation;

    private int mSlop;

    private Matrix mMatrix = new Matrix();

    private Matrix mRotateMatrix = new Matrix();

    private Matrix mInverseRotateMatrix = new Matrix();

    private float[] mTempPoint = new float[]{0, 0};

    private boolean mTouchEnabled = true;

    private long mTouchDownTime;

    private TMImlabBitmapRegionTileSource mSource;

    private Object mLock = new Object();

    private TouchCallback mTouchCallback;

    public static final int TYPE_SCALE_FILL = 0;

    public static final int TYPE_SCALE_NORMAL = 1;

    public int mScaleType = TYPE_SCALE_FILL;

    public interface TouchCallback {

        void onTouchDown();

        void onTap();

        void onTouchUp();
    }

    public TMImlabCropView(Context context) {
        this(context, null);
    }

    public TMImlabCropView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TMImlabCropView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        mContext = context;
        mScaleGestureDetector = new ScaleGestureDetector(mContext, this);
        mMinScale = 1;
        ViewConfiguration config = ViewConfiguration.get(mContext);
        mSlop = config.getScaledTouchSlop() * config.getScaledTouchSlop();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (mSource == null || mSource.getPreview() == null) {
            return true;
        }

        int action = event.getActionMasked();
        final boolean pointerUp = action == MotionEvent.ACTION_POINTER_UP;
        final int skipIndex = pointerUp ? event.getActionIndex() : -1;

        float sumX = 0;
        float sumY = 0;
        final int count = event.getPointerCount();
        for (int i = 0; i < count; i++) {
            if (skipIndex == i) {
                continue;
            }
            sumX += event.getX();
            sumY += event.getY();
        }
        final int div = pointerUp ? count - 1 : count;
        final float x = sumX / div;
        final float y = sumY / div;

        if (action == MotionEvent.ACTION_DOWN) {
            mFirstX = event.getX();
            mFirstY = event.getY();
            mTouchDownTime = System.currentTimeMillis();
            if (mTouchCallback != null) {
                mTouchCallback.onTouchDown();
            }
        } else if (action == MotionEvent.ACTION_UP) {
            ViewConfiguration config = ViewConfiguration.get(mContext);
            float squaredDist = (mFirstX - x) * (mFirstX - x)
                    + (mFirstY - y) * (mFirstY - y);
            float slop = config.getScaledTouchSlop() * config.getScaledTouchSlop();
            long now = System.currentTimeMillis();
            if (mTouchCallback != null) {
                // only do this if it's a small movement
                if (squaredDist < slop &&
                        now < mTouchDownTime + ViewConfiguration.getTapTimeout()) {
                    mTouchCallback.onTap();
                }
                mTouchCallback.onTouchUp();
            }
            mLastX = -1;
            mLastY = -1;
        } else if (action == MotionEvent.ACTION_CANCEL) {
            mLastX = -1;
            mLastY = -1;
        }

        if (!mTouchEnabled) {
            return true;
        }

        //------------move----------

        mScaleGestureDetector.onTouchEvent(event);
        if (action == MotionEvent.ACTION_MOVE) {
            Log.i("xzy", "pointer id is : " + event.getPointerId(0));

            float[] point = mTempPoint;
            float scale = getCurrentScale();
            point[0] = (mLastX - x) / scale;
            point[1] = (mLastY - y) / scale;
            mMatrix.mapPoints(point);
            mCenterX += point[0];
            mCenterY += point[1];
            if (count < 2) {
                if (mLastX > -1 && mLastY > -1) {
//                    floatsquaredDist = (mLastX - x) * (mLastX - x)
//                            + (mLastY - y) * (mLastY - y);
//                    if (squaredDist > mSlop) {
                        translateBitmap(x - mLastX, y - mLastY, true);
//                    }
                }
                mLastX = x;
                mLastY = y;
            }
        }

        return true;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {

        scaleBitmap(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        Log.i("xzy", "onScale end");
        mLastX = -1;
        mLastY = -1;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mSource != null && mSource.getPreview() != null) {
            Log.i("xzy", "preview width is: " + mSource.getPreview().getWidth() + " height is : "
                    + mSource
                    .getPreview().getHeight());
            canvas.drawBitmap(mSource.getPreview(), mMatrix, null);
        }
        super.onDraw(canvas);
    }

    /**
     * 宽高相等
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mWidth = w;
        mHeight = h;
        mHalfWidth = mWidth / 2;
        mHalfHeight = mHeight / 2;
        synchronized (mLock) {
            updateMatrixIfNecessaryLocked();
        }
    }

    public void setTouchEnabled(boolean touchEnabled) {
        mTouchEnabled = touchEnabled;
    }

    public void setTileSource(TMImlabBitmapRegionTileSource source) {
        //TODO
        mSource = source;
        mCenterX = source != null ? source.getPreview().getWidth() / 2 : 0;
        mCenterY = source != null ? source.getPreview().getHeight() / 2 : 0;
        mRotation = source != null ? source.getRotation() : 0;

//        mRotateMatrix.reset();
//        mRotateMatrix.setRotate(mSource.getRotation());
//        mInverseRotateMatrix.reset();
//        mInverseRotateMatrix.setRotate(-mSource.getRotation());

        synchronized (mLock) {
            updateMatrixIfNecessaryLocked();
        }

        invalidate();
    }

    private void updateMatrixIfNecessaryLocked() {
        if (getWidth() == 0 || mSource == null) {
            return;
        }
        //must not null
        if (mSource.getPreview() != null && mWidth > 0 && mHeight > 0) {

            mMatrix.reset();
//            Log.i("xzy", "rotation is: " + mSource.getRotation());
            rotateBitmap(mSource.getRotation(), mHalfWidth, mHalfHeight);
//            Log.i("xzy", "preview Width " + mSource.getPreview().getWidth() + " mWidth is : " + mWidth
//                    + " preview Height " + mSource.getPreview().getHeight() + " mHegiht is : " + mHeight);
            if (mSource.getPreview().getWidth() < mWidth
                    || mSource.getPreview().getHeight() < mHeight) {

                float scale = Math.max(mHeight / (float) mSource.getPreview().getHeight(),
                        mWidth / (float) mSource.getPreview().getWidth());
//                Log.i("xzy", "updateMatrix scale is : " + scale);
                mMinScale = scale;
                scaleBitmap(scale, mHalfWidth, mHalfHeight);
            } else if (mSource.getPreview().getWidth() > mWidth && mSource.getPreview().getHeight() > mHeight) {
                float scale = Math.max(mHeight / (float) mSource.getPreview().getHeight(),
                        mWidth / (float) mSource.getPreview().getWidth());
                mMinScale = scale;
                scaleBitmap(scale, mHalfWidth, mHalfHeight);
            }
        }
    }

    private void translateBitmap(float x, float y, boolean needInvalidate) {

//        final float[] values = new float[9];
//        mMatrix.getValues(values);
//        Log.i("xzy", " translateBitmap " + values[Matrix.MTRANS_X] + " translate y "
//                + values[Matrix.MTRANS_Y]);

        //TODO 边界限定
        RectF limitRectF = new RectF(0, 0, mSource.getPreview().getWidth(),
                mSource.getPreview().getHeight());
        mMatrix.mapRect(limitRectF);

        Log.i("xzy", "rect is : " + limitRectF.toShortString());
        if (limitRectF.left + x > 0) {
            x = 0 - (limitRectF.left);
        }
        if (limitRectF.top + y > 0) {
            y = 0 - (limitRectF.top);
        }
        if (limitRectF.right + x < mWidth) {
            x = mWidth - (limitRectF.right);
        }
        if (limitRectF.bottom + y < mHeight) {
            y = mHeight - (limitRectF.bottom);
        }

        mMatrix.postTranslate(x, y);
        invalidate();
    }

    private void scaleBitmap(float scale, float x, float y) {
        //TODO 边界限定
        float currentScale = getCurrentScale();
        Log.i("xzy", "currentScale is : " + currentScale);
        if (currentScale * scale < mMinScale) {
            scale = mMinScale / currentScale;
        }

        mMatrix.postTranslate(-x, -y);
        mMatrix.postScale(scale, scale);

        mMatrix.postTranslate(x, y);

        translateBitmap(0, 0, false);
        invalidate();
        //TODO 边界限定

    }

    /**
     * //TODO with animation
     *
     * @param degree degree of rotate
     * @param x      center x
     * @param y      center y
     */
    private void rotateBitmap(int degree, float x, float y) {
        mMatrix.postTranslate(-x, -y);
        mMatrix.postRotate(degree);
        mMatrix.postTranslate(x, y);
    }

    /**
     * use for rotate button
     */
    public void rotateBitmapWithScreenCenter() {
        rotateBitmap(90, mHalfWidth, mHalfHeight);
        invalidate();
    }

    /**
     * use for scale button
     */
    public void switchBitmapScaleType() {

        if (mScaleType == TYPE_SCALE_FILL) {
            mScaleType = TYPE_SCALE_NORMAL;

            if (mSource != null && mSource.getPreview() != null && mWidth > 0 && mHeight > 0) {
                mMatrix.reset();
                rotateBitmap(mSource.getRotation(), mHalfWidth, mHalfHeight);

                if (mSource.getPreview().getWidth() < mWidth
                        || mSource.getPreview().getHeight() < mHeight) {

                    float scale = Math.min(mHeight / (float) mSource.getPreview().getHeight(),
                            mWidth / (float) mSource.getPreview().getWidth());
                    Log.i("xzy", "111updateMatrix scale is : " + scale);
                    mMinScale = scale;
                    scaleBitmap(scale, mHalfWidth, mHalfHeight);
                } else {
                    float scale = Math.min(mHeight / (float) mSource.getPreview().getHeight(),
                            mWidth / (float) mSource.getPreview().getWidth());
                    Log.i("xzy", "222updateMatrix scale is : " + scale);
                    mMinScale = scale;
                    scaleBitmap(scale, mHalfWidth, mHalfHeight);
                }
                RectF rectF = new RectF(0, 0, mWidth, mHeight);
                mMatrix.mapRect(rectF);
                Log.i("xzy", "rectF is: " + rectF.toShortString());
                translateBitmap(-rectF.left / 2, -rectF.top / 2, false);
            }
        } else {
            mScaleType = TYPE_SCALE_FILL;
            updateMatrixIfNecessaryLocked();
        }

        invalidate();
    }


    public float getCurrentScale() {
        final float[] values = new float[9];
        mMatrix.getValues(values);

        float scaleX = values[Matrix.MSCALE_X];
        float skewY = values[Matrix.MSKEW_Y];
        float scale = (float) Math.sqrt(scaleX * scaleX + skewY * skewY);

        Log.i("xzy", "current Scale is : " + scale);
        return scale;
    }

    public int getCurrentRotateDegree() {
        final float[] values = new float[9];
        mMatrix.getValues(values);
        int degree = (int) Math
                .round(Math.atan2(values[Matrix.MSKEW_X], values[Matrix.MSCALE_X]) * (180
                        / Math.PI));
        Log.i("xzy", " ===== degree is : " + degree);
        return degree;
    }


    public RectF getCropBounds() {

        if (mSource == null || mSource.getPreview() == null) {
            return null;
        }

        //1 算旋转角度
        float scale = getCurrentRotateDegree() /*- mSource.getRotation()*/;
        Matrix rotateMatrix = new Matrix(mMatrix);

        //旋转回0度
        rotateMatrix.postTranslate(-mHalfWidth, -mHalfHeight);
        rotateMatrix.postRotate(-scale);
        rotateMatrix.postTranslate(mHalfWidth, mHalfHeight);

        RectF tmpRect = new RectF(0, 0, mWidth, mHeight);
        float inSampleSize = mSource.getImageWidth() / mSource.getPreview().getWidth();
        if (rotateMatrix.invert(rotateMatrix)) {
            rotateMatrix.mapRect(tmpRect);
        }
        RectF result = new RectF();
        result.set(tmpRect);
        result.left *= inSampleSize;
        result.top *= inSampleSize;
        result.right *= inSampleSize;
        result.bottom *= inSampleSize;

        Log.i("xzy", "====> scale is: " + scale);
        Log.i("xzy", "====> result is : " + result.toShortString());
        return result;
    }

    public TMImlabBitmapRegionTileSource getSource() {
        return mSource;
    }
}
