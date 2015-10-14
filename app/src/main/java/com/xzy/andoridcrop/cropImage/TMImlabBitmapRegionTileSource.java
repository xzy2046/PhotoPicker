/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xzy.andoridcrop.cropImage;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

interface SimpleBitmapRegionDecoder {

    int getWidth();

    int getHeight();

    Bitmap decodeRegion(Rect wantRegion, BitmapFactory.Options options);

}

class SimpleBitmapRegionDecoderWrapper implements SimpleBitmapRegionDecoder {

    BitmapRegionDecoder mDecoder;

    private SimpleBitmapRegionDecoderWrapper(BitmapRegionDecoder decoder) {
        mDecoder = decoder;
    }

    public static SimpleBitmapRegionDecoderWrapper newInstance(
            String pathName, boolean isShareable) {
        try {
            BitmapRegionDecoder d = BitmapRegionDecoder.newInstance(pathName, isShareable);
            if (d != null) {
                return new SimpleBitmapRegionDecoderWrapper(d);
            }
        } catch (IOException e) {
            Log.w("BitmapRegionTileSource", "getting decoder failed for path " + pathName, e);
            return null;
        }
        return null;
    }

    public static SimpleBitmapRegionDecoderWrapper newInstance(
            InputStream is, boolean isShareable) {
        try {
            BitmapRegionDecoder d = BitmapRegionDecoder.newInstance(is, isShareable);
            if (d != null) {
                return new SimpleBitmapRegionDecoderWrapper(d);
            }
        } catch (IOException e) {
            Log.w("BitmapRegionTileSource", "getting decoder failed", e);
            return null;
        }
        return null;
    }

    public int getWidth() {
        return mDecoder.getWidth();
    }

    public int getHeight() {
        return mDecoder.getHeight();
    }

    public Bitmap decodeRegion(Rect wantRegion, BitmapFactory.Options options) {
        return mDecoder.decodeRegion(wantRegion, options);
    }
}

class DumbBitmapRegionDecoder implements SimpleBitmapRegionDecoder {

    Bitmap mBuffer;

    Canvas mTempCanvas;

    Paint mTempPaint;

    private DumbBitmapRegionDecoder(Bitmap b) {
        mBuffer = b;
    }

    public static DumbBitmapRegionDecoder newInstance(String pathName) {
        Bitmap b = BitmapFactory.decodeFile(pathName);
        if (b != null) {
            return new DumbBitmapRegionDecoder(b);
        }
        return null;
    }

    public static DumbBitmapRegionDecoder newInstance(InputStream is) {
        Bitmap b = BitmapFactory.decodeStream(is);
        if (b != null) {
            return new DumbBitmapRegionDecoder(b);
        }
        return null;
    }

    public int getWidth() {
        return mBuffer.getWidth();
    }

    public int getHeight() {
        return mBuffer.getHeight();
    }

    public Bitmap decodeRegion(Rect wantRegion, BitmapFactory.Options options) {
        if (mTempCanvas == null) {
            mTempCanvas = new Canvas();
            mTempPaint = new Paint();
            mTempPaint.setFilterBitmap(true);
        }
        int sampleSize = Math.max(options.inSampleSize, 1);
        Bitmap newBitmap = Bitmap.createBitmap(
                wantRegion.width() / sampleSize,
                wantRegion.height() / sampleSize,
                Bitmap.Config.ARGB_8888);
        mTempCanvas.setBitmap(newBitmap);
        mTempCanvas.save();
        mTempCanvas.scale(1f / sampleSize, 1f / sampleSize);
        mTempCanvas.drawBitmap(mBuffer, -wantRegion.left, -wantRegion.top, mTempPaint);
        mTempCanvas.restore();
        mTempCanvas.setBitmap(null);
        return newBitmap;
    }
}

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
public class TMImlabBitmapRegionTileSource {

    private static final String TAG = "BitmapRegionTileSource";

    private static final boolean REUSE_BITMAP =
            Build.VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN;

    private static final int GL_SIZE_LIMIT = 2048;

    // This must be no larger than half the size of the GL_SIZE_LIMIT
    // due to decodePreview being allowed to be up to 2x the size of the target
//    public static final int MAX_PREVIEW_SIZE = GL_SIZE_LIMIT / 2;
    //FULL HD SCREEN
    public static final int MAX_PREVIEW_SIZE = 1080;

    public static abstract class BitmapSource {

        private SimpleBitmapRegionDecoder mDecoder;

        private Bitmap mPreview;

        private int mPreviewSize;

        private int mRotation;

        public enum State {NOT_LOADED, LOADED, ERROR_LOADING}

        ;

        private State mState = State.NOT_LOADED;

        public BitmapSource(int previewSize) {
            mPreviewSize = previewSize;
        }

        public boolean loadInBackground() {
            ExifInterface ei = null;
            try {
                ei = new ExifInterface(getFileName());
            } catch (IOException e) {
                ei = null;
                e.printStackTrace();
            }
            if (ei != null) {
                int ori = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1);
                if (ori != -1) {
                    mRotation = CropImageHelper.getRotationForOrientationValue(ori);
                }
            }
            mDecoder = loadBitmapRegionDecoder();
            if (mDecoder == null) {
                mState = State.ERROR_LOADING;
                return false;
            } else {
                int width = mDecoder.getWidth();
                int height = mDecoder.getHeight();
                if (mPreviewSize != 0) {
                    int previewSize = Math.min(mPreviewSize, MAX_PREVIEW_SIZE);
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    opts.inPreferQualityOverSpeed = true;

//                    float scale = (float) previewSize / Math.max(width, height);
                    float scale = (float) previewSize / Math.min(width, height);
                    opts.inSampleSize = CropImageHelper.computeSampleSizeLarger(scale);
                    opts.inJustDecodeBounds = false;
                    mPreview = loadPreviewBitmap(opts);
                }
                mState = State.LOADED;
                return true;
            }
        }

        public State getLoadingState() {
            return mState;
        }

        public SimpleBitmapRegionDecoder getBitmapRegionDecoder() {
            return mDecoder;
        }

        public Bitmap getPreviewBitmap() {
            return mPreview;
        }

        public int getPreviewSize() {
            return mPreviewSize;
        }

        public int getRotation() {
            return mRotation;
        }

//        public abstract boolean readExif(ExifInterface ei);

        public abstract String getFileName();

        public abstract SimpleBitmapRegionDecoder loadBitmapRegionDecoder();

        public abstract Bitmap loadPreviewBitmap(BitmapFactory.Options options);
    }

    public static class FilePathBitmapSource extends BitmapSource {

        private String mPath;

        public FilePathBitmapSource(String path, int previewSize) {
            super(previewSize);
            mPath = path;
        }

        @Override
        public String getFileName() {
            return mPath;
        }

        @Override
        public SimpleBitmapRegionDecoder loadBitmapRegionDecoder() {
            SimpleBitmapRegionDecoder d;
            d = SimpleBitmapRegionDecoderWrapper.newInstance(mPath, true);
            if (d == null) {
                d = DumbBitmapRegionDecoder.newInstance(mPath);
            }
            return d;
        }

        @Override
        public Bitmap loadPreviewBitmap(BitmapFactory.Options options) {
            return BitmapFactory.decodeFile(mPath, options);
        }
//        @Override
//        public boolean readExif(ExifInterface ei) {
//            try {
//                ei.readExif(mPath);
//                return true;
//            } catch (NullPointerException e) {
//                Log.w("BitmapRegionTileSource", "reading exif failed", e);
//                return false;
//            } catch (IOException e) {
//                Log.w("BitmapRegionTileSource", "getting decoder failed", e);
//                return false;
//            }
//        }
    }

    public static class UriBitmapSource extends BitmapSource {

        private Context mContext;

        private Uri mUri;

        public UriBitmapSource(Context context, Uri uri, int previewSize) {
            super(previewSize);
            mContext = context;
            mUri = uri;
        }

        private InputStream regenerateInputStream() throws FileNotFoundException {
            InputStream is = mContext.getContentResolver().openInputStream(mUri);
            return new BufferedInputStream(is);
        }

        @Override
        public String getFileName() {
            return CropImageHelper.getRealPathFromURI(mContext, mUri);
        }

        @Override
        public SimpleBitmapRegionDecoder loadBitmapRegionDecoder() {
            try {
                InputStream is = regenerateInputStream();
                SimpleBitmapRegionDecoder regionDecoder =
                        SimpleBitmapRegionDecoderWrapper.newInstance(is, false);
                CropImageHelper.closeSilently(is);
                if (regionDecoder == null) {
                    is = regenerateInputStream();
                    regionDecoder = DumbBitmapRegionDecoder.newInstance(is);
                    CropImageHelper.closeSilently(is);
                }
                return regionDecoder;
            } catch (FileNotFoundException e) {
                Log.e("BitmapRegionTileSource", "Failed to load URI " + mUri, e);
                return null;
            }
        }

        @Override
        public Bitmap loadPreviewBitmap(BitmapFactory.Options options) {
            try {
                InputStream is = regenerateInputStream();
                Bitmap b = BitmapFactory.decodeStream(is, null, options);
                CropImageHelper.closeSilently(is);
                return b;
            } catch (FileNotFoundException e) {
                Log.e("BitmapRegionTileSource", "Failed to load URI " + mUri, e);
                return null;
            } catch (OutOfMemoryError e) {
                return null;
            }
        }
    }

    SimpleBitmapRegionDecoder mDecoder;

    int mWidth;

    int mHeight;

    int mTileSize;

    private Bitmap mPreview;

    private final int mRotation;

    // For use only by getTile
    private Rect mWantRegion = new Rect();

    private Rect mOverlapRegion = new Rect();

    private BitmapFactory.Options mOptions;

    private Canvas mCanvas;

    public TMImlabBitmapRegionTileSource(Context context, BitmapSource source) {
        mRotation = source.getRotation();
        mDecoder = source.getBitmapRegionDecoder();
        if (mDecoder != null) {
            mWidth = mDecoder.getWidth();
            mHeight = mDecoder.getHeight();
            mOptions = new BitmapFactory.Options();
            mOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
            mOptions.inPreferQualityOverSpeed = true;
            mOptions.inTempStorage = new byte[16 * 1024];
            int previewSize = source.getPreviewSize();
            if (previewSize != 0) {
                previewSize = Math.min(previewSize, MAX_PREVIEW_SIZE);
                // Although this is the same size as the Bitmap that is likely already
                // loaded, the lifecycle is different and interactions are on a different
                // thread. Thus to simplify, this source will decode its own bitmap.
                Bitmap preview = decodePreview(source, previewSize);
//                if (preview.getWidth() <= GL_SIZE_LIMIT && preview.getHeight() <= GL_SIZE_LIMIT) {
                mPreview = preview;
//                } else {
//                    Log.w(TAG, String.format(
//                            "Failed to create preview of apropriate size! "
//                                    + " in: %dx%d, out: %dx%d",
//                            mWidth, mHeight,
//                            preview.getWidth(), preview.getHeight()));
//                }
            }
        }
    }

    public int getTileSize() {
        return mTileSize;
    }

    public int getImageWidth() {
        return mWidth;
    }

    public int getImageHeight() {
        return mHeight;
    }

    public Bitmap getPreview() {
        return mPreview;
    }

    public int getRotation() {
        return mRotation;
    }

    /**
     * Note that the returned bitmap may have a long edge that's longer
     * than the targetSize, but it will always be less than 2x the targetSize
     */
    private Bitmap decodePreview(BitmapSource source, int targetSize) {
        Bitmap result = source.getPreviewBitmap();
        if (result == null) {
            return null;
        }

        // We need to resize down if the decoder does not support inSampleSize
        // or didn't support the specified inSampleSize (some decoders only do powers of 2)
//        float scale = (float) targetSize / (float) (Math.max(result.getWidth(), result.getHeight()));
        float scale = (float) targetSize / (float) (Math
                .min(result.getWidth(), result.getHeight()));

        Log.i("xzy", "decode preview scale is: " + scale);

        if (scale <= 0.5) {
            result = CropImageHelper.resizeBitmapByScale(result, scale, true);
        }
        return ensureGLCompatibleBitmap(result);
    }

    private static Bitmap ensureGLCompatibleBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.getConfig() != null) {
            return bitmap;
        }
        Bitmap newBitmap = bitmap.copy(Config.ARGB_8888, false);
        bitmap.recycle();
        return newBitmap;
    }
}
