package com.xzy.andoridcrop.cropImage;

import com.xzy.andoridcrop.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

/**
 * Created by zhengyang on 15/3/30.
 */
public class TMImlabCropActivity extends Activity {

    private static final String TAG = TMImlabCropActivity.class.getSimpleName();

    private static final int DEFAULT_COMPRESS_QUALITY = 90;

    private Uri mSaveUri = null;

    private Bitmap.CompressFormat mOutputFormat = Bitmap.CompressFormat.JPEG;

    /**
     * x y的比例
     */
    private int mAspectX, mAspectY;

    private boolean mCircleCrop = false;

    /**
     * 输出的x y 值
     */
    private int mOutputX, mOutputY;

    private TMImlabCropView mCropView;

    private Bitmap mBitmap;

    /**
     * 必要参数
     */
    private Uri mImageUrl;

    private int mScreenWidth;

    private TextView mRotateButton;

    private String mCallerName = null;

//    private TextView mScaleButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.crop_activity);
//        overridePendingTransition(0, 0);
        setupViews();

        mCallerName = getIntent().getStringExtra("callerName");
        if (TextUtils.isEmpty(mCallerName)) {
            mCallerName = "other";
        }

        init();
    }

    @Override
    public void finish() {
        super.finish();
//        overridePendingTransition(R.anim.tm_fun_page_ani_rightout, R.anim.tm_fun_page_ani_leftout);
    }

    private void setupViews() {
        mCropView = (TMImlabCropView) findViewById(R.id.crop_view);

//        mScaleButton = (TextView) findViewById(R.id.scale_button);
        mRotateButton = (TextView) findViewById(R.id.rotate_button);
//        mScaleButton.setOnClickListener(mOnClickListener);
        mRotateButton.setOnClickListener(mOnClickListener);

        View view = findViewById(R.id.done);
        view.setOnClickListener(mOnClickListener);
    }

    private void init() {
        mScreenWidth = CropImageHelper.getScreenWidth(this);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            if (extras.getString("circleCrop") != null) {
                mCircleCrop = true;
                mAspectX = 1;
                mAspectY = 1;
            }

            mSaveUri = (Uri) extras.getParcelable(MediaStore.EXTRA_OUTPUT);
            if (mSaveUri != null) {
                String outputFormatString = extras.getString("outputFormat");
                if (outputFormatString != null) {
                    mOutputFormat = Bitmap.CompressFormat.valueOf(
                            outputFormatString);
                }
            } else {
                //TODO 设置壁纸，暂不支持
            }
            mAspectX = extras.getInt("aspectX");
            mAspectY = extras.getInt("aspectY");
            mOutputX = extras.getInt("outputX");
            mOutputY = extras.getInt("outputY");

        }

        mImageUrl = intent.getData();
        if (mImageUrl == null) {
            Log.e(TAG, "no url pass in this intent...exit");
            finish();
            return;
        }
        Log.i("xzy", "imageUrl is : " + mImageUrl);

        int previewSize = mScreenWidth;
        // Load image in background
        final TMImlabBitmapRegionTileSource.UriBitmapSource bitmapSource =
                new TMImlabBitmapRegionTileSource.UriBitmapSource(this, mImageUrl, previewSize);
        Runnable onLoad = new Runnable() {
            public void run() {
                if (bitmapSource.getLoadingState()
                        != TMImlabBitmapRegionTileSource.BitmapSource.State.LOADED) {
                    Toast.makeText(TMImlabCropActivity.this,
                            getString(R.string.cropview_load_error),
                            Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    //TODO make button enable
//                    mSetWallpaperButton.setEnabled(true);
                }
            }
        };
        setCropViewTileSource(bitmapSource, true, onLoad);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public void setCropViewTileSource(
            final TMImlabBitmapRegionTileSource.BitmapSource bitmapSource,
            final boolean touchEnabled,
            final Runnable postExecute) {
        final Context context = TMImlabCropActivity.this;
        final View progressView = findViewById(R.id.loading);
        final AsyncTask<Void, Void, Void> loadBitmapTask = new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void... args) {
                if (!isCancelled()) {
                    try {
                        bitmapSource.loadInBackground();
                    } catch (SecurityException securityException) {
                        if (isDestroyed()) {
                            // Temporarily granted permissions are revoked when the activity
                            // finishes, potentially resulting in a SecurityException here.
                            // Even though {@link #isDestroyed} might also return true in different
                            // situations where the configuration changes, we are fine with
                            // catching these cases here as well.
                            cancel(false);
                        } else {
                            // otherwise it had a different cause and we throw it further
                            throw securityException;
                        }
                    }
                }
                return null;
            }

            protected void onPostExecute(Void arg) {
                if (!isCancelled()) {
                    progressView.setVisibility(View.INVISIBLE);
                    if (bitmapSource.getLoadingState()
                            == TMImlabBitmapRegionTileSource.BitmapSource.State.LOADED) {
                        if (bitmapSource.getPreviewBitmap() == null) {
                            TMImlabCropActivity.this.setResult(RESULT_CANCELED);
                            TMImlabCropActivity.this.finish();
                        } else {
                            TMImlabBitmapRegionTileSource source
                                    = new TMImlabBitmapRegionTileSource(context, bitmapSource);
                            if (null == source.getPreview()) {
                                TMImlabCropActivity.this.setResult(RESULT_CANCELED);
                                TMImlabCropActivity.this.finish();
                            } else {
                                mCropView.setTileSource(source);
                                mCropView.setTouchEnabled(touchEnabled);
                            }
                        }
                    }
                }
                if (postExecute != null) {
                    postExecute.run();
                }
            }
        };
        // We don't want to show the spinner every time we load an image, because that would be
        // annoying; instead, only start showing the spinner if loading the image has taken
        // longer than 1 sec (ie 1000 ms)
        progressView.postDelayed(new Runnable() {
            public void run() {
                if (loadBitmapTask.getStatus() != AsyncTask.Status.FINISHED) {
                    progressView.setVisibility(android.view.View.VISIBLE);
                }
            }
        }, 1000);
        loadBitmapTask.execute();
    }

    private void cropImage() {
        RectF cropBounds = mCropView.getCropBounds();
        if (cropBounds == null) {
            return;
        }
        BitmapCropTask task = new BitmapCropTask(this, mImageUrl,
                cropBounds, mCropView.getCurrentRotateDegree(), mOutputX, mOutputY,
                true, null);
        task.execute();
    }

    protected class BitmapCropTask extends AsyncTask<Void, Void, Boolean> {

        Uri mInUri = null;

        Context mContext;

        String mInFilePath;

        byte[] mInImageBytes;

        int mInResId = 0;

        RectF mCropBounds = null;

        int mOutWidth, mOutHeight;

        int mRotation;

        String mOutputFormat = "jpg"; // for now

        boolean mSetWallpaper;

        boolean mSaveCroppedBitmap;

        Bitmap mCroppedBitmap;

        Runnable mOnEndRunnable;

        Resources mResources;

        boolean mNoCrop;

        public BitmapCropTask(Context c, String filePath,
                RectF cropBounds, int rotation, int outWidth, int outHeight,
                boolean saveCroppedBitmap, Runnable onEndRunnable) {
            mContext = c;
            mInFilePath = filePath;
            init(cropBounds, rotation,
                    outWidth, outHeight, saveCroppedBitmap, onEndRunnable);
        }

        public BitmapCropTask(byte[] imageBytes,
                RectF cropBounds, int rotation, int outWidth, int outHeight,
                boolean saveCroppedBitmap, Runnable onEndRunnable) {
            mInImageBytes = imageBytes;
            init(cropBounds, rotation,
                    outWidth, outHeight, saveCroppedBitmap, onEndRunnable);
        }

        public BitmapCropTask(Context c, Uri inUri,
                RectF cropBounds, int rotation, int outWidth, int outHeight,
                boolean saveCroppedBitmap, Runnable onEndRunnable) {
            mContext = c;
            mInUri = inUri;
            init(cropBounds, rotation,
                    outWidth, outHeight, saveCroppedBitmap, onEndRunnable);
        }

        public BitmapCropTask(Context c, Resources res, int inResId,
                RectF cropBounds, int rotation, int outWidth, int outHeight,
                boolean saveCroppedBitmap, Runnable onEndRunnable) {
            mContext = c;
            mInResId = inResId;
            mResources = res;
            init(cropBounds, rotation,
                    outWidth, outHeight, saveCroppedBitmap, onEndRunnable);
        }

        private void init(RectF cropBounds, int rotation, int outWidth, int outHeight,
                boolean saveCroppedBitmap, Runnable onEndRunnable) {
            mCropBounds = cropBounds;
            mRotation = rotation;
            Log.i("xzy", "rotation is : " + mRotation);
//            mRotation = 180 + mRotation;
//            mRotation = 0;
            mOutWidth = outWidth;
            mOutHeight = outHeight;
            mSaveCroppedBitmap = saveCroppedBitmap;
            mOnEndRunnable = onEndRunnable;
        }

        public void setNoCrop(boolean value) {
            mNoCrop = value;
        }

        public void setOnEndRunnable(Runnable onEndRunnable) {
            mOnEndRunnable = onEndRunnable;
        }

        // Helper to setup input stream
        private InputStream regenerateInputStream() {
            if (mInUri == null && mInResId == 0 && mInFilePath == null && mInImageBytes == null) {
                Log.w(TAG, "cannot read original file, no input URI, resource ID, or " +
                        "image byte array given");
            } else {
                try {
                    if (mInUri != null) {
                        return new BufferedInputStream(
                                mContext.getContentResolver().openInputStream(mInUri));
                    } else if (mInFilePath != null) {
                        return mContext.openFileInput(mInFilePath);
                    } else if (mInImageBytes != null) {
                        return new BufferedInputStream(new ByteArrayInputStream(mInImageBytes));
                    } else {
                        return new BufferedInputStream(mResources.openRawResource(mInResId));
                    }
                } catch (FileNotFoundException e) {
                    Log.w(TAG, "cannot read file: " + mInUri.toString(), e);
                }
            }
            return null;
        }

        public Point getImageBounds() {
            InputStream is = regenerateInputStream();
            if (is != null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(is, null, options);
                CropImageHelper.closeSilently(is);
                if (options.outWidth != 0 && options.outHeight != 0) {
                    return new Point(options.outWidth, options.outHeight);
                }
            }
            return null;
        }

        public void setCropBounds(RectF cropBounds) {
            mCropBounds = cropBounds;
        }

        public Bitmap getCroppedBitmap() {
            return mCroppedBitmap;
        }

        public boolean cropBitmap() {
            boolean failure = false;

            // Find crop bounds (scaled to original image size)
            Rect roundedTrueCrop = new Rect();
            Matrix rotateMatrix = new Matrix();
            Matrix inverseRotateMatrix = new Matrix();

            Point bounds = getImageBounds();
//            if (mRotation > 0) {
//                rotateMatrix.setRotate(mRotation);
//                inverseRotateMatrix.setRotate(-mRotation);
//
//                mCropBounds.roundOut(roundedTrueCrop);
//                mCropBounds = new RectF(roundedTrueCrop);
//
//                if (bounds == null) {
//                    Log.w(TAG, "cannot get bounds for image");
//                    failure = true;
//                    return false;
//                }
//
//                float[] rotatedBounds = new float[]{bounds.x, bounds.y};
//                rotateMatrix.mapPoints(rotatedBounds);
//                rotatedBounds[0] = Math.abs(rotatedBounds[0]);
//                rotatedBounds[1] = Math.abs(rotatedBounds[1]);
//
//                mCropBounds.offset(-rotatedBounds[0] / 2, -rotatedBounds[1] / 2);
//                inverseRotateMatrix.mapRect(mCropBounds);
//                mCropBounds.offset(bounds.x / 2, bounds.y / 2);
//
//            }

            mCropBounds.roundOut(roundedTrueCrop);

            if (roundedTrueCrop.width() <= 0 || roundedTrueCrop.height() <= 0) {
                Log.w(TAG, "crop has bad values for full size image");
                failure = true;
                return false;
            }

            // See how much we're reducing the size of the image
            int scaleDownSampleSize = Math.max(1, Math.min(roundedTrueCrop.width() / mOutWidth,
                    roundedTrueCrop.height() / mOutHeight));
            // Attempt to open a region decoder
            BitmapRegionDecoder decoder = null;
            InputStream is = null;
            try {
                is = regenerateInputStream();
                if (is == null) {
                    Log.w(TAG, "cannot get input stream for uri=" + mInUri.toString());
                    failure = true;
                    return false;
                }
                decoder = BitmapRegionDecoder.newInstance(is, false);
                CropImageHelper.closeSilently(is);
            } catch (IOException e) {
                Log.w(TAG, "cannot open region decoder for file: " + mInUri.toString(), e);
            } finally {
                CropImageHelper.closeSilently(is);
                is = null;
            }

            Bitmap crop = null;
            if (decoder != null) {
                // Do region decoding to get crop bitmap
                BitmapFactory.Options options = new BitmapFactory.Options();
                if (scaleDownSampleSize > 1) {
                    options.inSampleSize = scaleDownSampleSize;
                }
                try {
                    crop = decoder.decodeRegion(roundedTrueCrop, options);
                } catch (OutOfMemoryError e) {
                    e.printStackTrace();
                } catch (IllegalArgumentException e) {
                    //rectangle is not inside the image
                    e.printStackTrace();
                }
                decoder.recycle();
            }
            if (crop == null) {
                // BitmapRegionDecoder has failed, try to crop in-memory
                is = regenerateInputStream();
                Bitmap fullSize = null;
                if (is != null) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    if (scaleDownSampleSize > 1) {
                        options.inSampleSize = scaleDownSampleSize;
                    }
                    try {
                        fullSize = BitmapFactory.decodeStream(is, null, options);
                    } catch (OutOfMemoryError e) {
                        e.printStackTrace();
                    }
                    CropImageHelper.closeSilently(is);
                }
                if (fullSize != null) {
                    // Find out the true sample size that was used by the decoder
                    scaleDownSampleSize = bounds.x / fullSize.getWidth();
                    mCropBounds.left /= scaleDownSampleSize;
                    mCropBounds.top /= scaleDownSampleSize;
                    mCropBounds.bottom /= scaleDownSampleSize;
                    mCropBounds.right /= scaleDownSampleSize;
                    mCropBounds.roundOut(roundedTrueCrop);

                    // Adjust values to account for issues related to rounding
                    if (roundedTrueCrop.width() > fullSize.getWidth()) {
                        // Adjust the width
                        roundedTrueCrop.right = roundedTrueCrop.left + fullSize.getWidth();
                    }
                    if (roundedTrueCrop.right > fullSize.getWidth()) {
                        // Adjust the left value
                        int adjustment = roundedTrueCrop.left -
                                Math.max(0, roundedTrueCrop.right - roundedTrueCrop.width());
                        roundedTrueCrop.left -= adjustment;
                        roundedTrueCrop.right -= adjustment;
                    }
                    if (roundedTrueCrop.height() > fullSize.getHeight()) {
                        // Adjust the height
                        roundedTrueCrop.bottom = roundedTrueCrop.top + fullSize.getHeight();
                    }
                    if (roundedTrueCrop.bottom > fullSize.getHeight()) {
                        // Adjust the top value
                        int adjustment = roundedTrueCrop.top -
                                Math.max(0, roundedTrueCrop.bottom - roundedTrueCrop.height());
                        roundedTrueCrop.top -= adjustment;
                        roundedTrueCrop.bottom -= adjustment;
                    }

                    crop = Bitmap.createBitmap(fullSize, roundedTrueCrop.left,
                            roundedTrueCrop.top, roundedTrueCrop.width(),
                            roundedTrueCrop.height());
                }
            }

            if (crop == null) {
                Log.w(TAG, "cannot decode file: " + mInUri.toString());
                failure = true;
                return false;
            }
//            if (mOutWidth > 0 && mOutHeight > 0 || mRotation > 0) {
//                float[] dimsAfter = new float[]{crop.getWidth(), crop.getHeight()};
//                rotateMatrix.mapPoints(dimsAfter);
//                dimsAfter[0] = Math.abs(dimsAfter[0]);
//                dimsAfter[1] = Math.abs(dimsAfter[1]);
//
//                if (!(mOutWidth > 0 && mOutHeight > 0)) {
//                    mOutWidth = Math.round(dimsAfter[0]);
//                    mOutHeight = Math.round(dimsAfter[1]);
//                }
//
//                RectF cropRect = new RectF(0, 0, dimsAfter[0], dimsAfter[1]);
//                RectF returnRect = new RectF(0, 0, mOutWidth, mOutHeight);
//
//                Matrix m = new Matrix();
//                if (mRotation == 0) {
//                    m.setRectToRect(cropRect, returnRect, Matrix.ScaleToFit.FILL);
//                } else {
//                    Matrix m1 = new Matrix();
//                    m1.setTranslate(-crop.getWidth() / 2f, -crop.getHeight() / 2f);
//                    Matrix m2 = new Matrix();
//                    m2.setRotate(mRotation);
//                    Matrix m3 = new Matrix();
//                    m3.setTranslate(dimsAfter[0] / 2f, dimsAfter[1] / 2f);
//                    Matrix m4 = new Matrix();
//                    m4.setRectToRect(cropRect, returnRect, Matrix.ScaleToFit.FILL);
//
//                    Matrix c1 = new Matrix();
//                    c1.setConcat(m2, m1);
//                    Matrix c2 = new Matrix();
//                    c2.setConcat(m4, m3);
//                    m.setConcat(c2, c1);
//                }
//
//                Bitmap tmp = Bitmap.createBitmap((int) returnRect.width(),
//                        (int) returnRect.height(), Bitmap.Config.ARGB_8888);
//                if (tmp != null) {
//                    Canvas c = new Canvas(tmp);
//                    Paint p = new Paint();
//                    p.setFilterBitmap(true);
//                    c.drawBitmap(crop, m, p);
//                    crop = tmp;
//                }
//            }

            Bitmap tmp = null;
            try {
                tmp = Bitmap.createBitmap(crop.getWidth(), crop.getHeight(),
                        Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (tmp != null) {
                Canvas c = new Canvas(tmp);
                Paint p = new Paint();
                p.setFilterBitmap(true);
                Matrix m = new Matrix();
                m.setRotate(-mRotation, crop.getWidth() / 2, crop.getHeight() / 2);
                c.drawBitmap(crop, m, p);

                crop.recycle();
                crop = tmp;
            } else {
                Log.w(TAG, "cannot decode file: " + mInUri.toString());
                failure = true;
                return false;
            }

            if (mSaveCroppedBitmap) {
                mCroppedBitmap = crop;
                Log.i("xzy",
                        "mCroppedBitmap height is: " + mCroppedBitmap.getHeight() + " width is: "
                                + mCroppedBitmap.getWidth());

                String path = null;
                path = CropImageHelper
                        .saveBitmap(mCroppedBitmap, mContext, Bitmap.CompressFormat.JPEG, 85);
                mCroppedBitmap.recycle();
                mCroppedBitmap = null;
//                MediaScannerConnection
//                        .scanFile(mContext, new String[]{path}, null, null);
                if (TextUtils.isEmpty(path)) {
                    mSaveUri = null;
                } else {
                    mSaveUri = Uri.fromFile(new File(path));
                }
//                Log.i("xzy", "path is: " + path);
            }

            // Get output compression format
//            Bitmap.CompressFormat cf =
//                    convertExtensionToCompressFormat(getFileExtension(mOutputFormat));
//
//            // Compress to byte array
//            ByteArrayOutputStream tmpOut = new ByteArrayOutputStream(2048);
//            if (crop.compress(cf, DEFAULT_COMPRESS_QUALITY, tmpOut)) {
            // If we need to set to the wallpaper, set it
//                    if (mSetWallpaper && wallpaperManager != null) {
//                        try {
//                            byte[] outByteArray = tmpOut.toByteArray();
//                            wallpaperManager.setStream(new ByteArrayInputStream(outByteArray));
//                            if (mOnBitmapCroppedHandler != null) {
//                                mOnBitmapCroppedHandler.onBitmapCropped(outByteArray);
//                            }
//                        } catch (IOException e) {
//                            Log.w(TAG, "cannot write stream to wallpaper", e);
//                            failure = true;
//                        }
//                    }
//            } else {
//                Log.w(TAG, "cannot compress bitmap");
//                failure = true;
//            }
//            }
//            return !failure; // True if any of the operations failed
            return true;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            return cropBitmap();
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result) {
                TMImlabCropActivity.this.setResult(RESULT_CANCELED);
                TMImlabCropActivity.this.finish();
            }
            if (mOnEndRunnable != null) {
                mOnEndRunnable.run();
            }
            Intent intent = new Intent();
            intent.setData(mSaveUri);
            Log.i("xzy", "onPost Excute  ---> " + mSaveUri);
            TMImlabCropActivity.this.setResult(RESULT_OK, intent);
            TMImlabCropActivity.this.finish();
        }
    }


    protected static Bitmap.CompressFormat convertExtensionToCompressFormat(String extension) {
        return extension.equals("png") ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
    }

    protected static String getFileExtension(String requestFormat) {
        String outputFormat = (requestFormat == null)
                ? "jpg"
                : requestFormat;
        outputFormat = outputFormat.toLowerCase();
        return (outputFormat.equals("png") || outputFormat.equals("gif"))
                ? "png" // We don't support gif compression.
                : "jpg";
    }

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            final int id = v.getId();
            if (id == R.id.rotate_button) {

                mCropView.rotateBitmapWithScreenCenter();
//            } else if (id == R.id.scale_button) {
//                mCropView.switchBitmapScaleType();
            } else if (id == R.id.done) {
//                HashMap<String, Object> map = new HashMap<String, Object>();
                cropImage();
            }
        }
    };

}
