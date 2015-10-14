package com.xzy.andoridcrop.cropImage;

import com.xzy.andoridcrop.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.FloatMath;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Random;

/**
 * Created by zhengyang on 15/3/31.
 */
public class CropImageHelper {

    public static final String TAG = CropImageHelper.class.getSimpleName();

    public static int getRotationForOrientationValue(int orientation) {
        switch (orientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                return 0;
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }

    public static int computeSampleSizeLarger(int w, int h,
            int minSideLength) {
        int initialSize = Math.max(w / minSideLength, h / minSideLength);
        if (initialSize <= 1) {
            return 1;
        }

        return initialSize <= 8
                ? prevPowerOf2(initialSize)
                : initialSize / 8 * 8;
    }

    public static int computeSampleSizeLarger(float scale) {
        int initialSize = (int) FloatMath.floor(1f / scale);
        if (initialSize <= 1) {
            return 1;
        }

//        return initialSize <= 8
//                ? prevPowerOf2(initialSize)
//                : initialSize / 8 * 8;

        return initialSize;
    }

    //use for openGL
    public static int prevPowerOf2(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException();
        }
        return Integer.highestOneBit(n);
    }

    public static String getRealPathFromURI(Context context, Uri contentUri) {
//        Cursor cursor = null;
//        try {
//            String[] proj = { MediaStore.Images.Media.DATA };
//            cursor = context.getContentResolver().query(contentUri,  proj, null, null, null);
//            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
//            cursor.moveToFirst();
//            return cursor.getString(column_index);
//        } finally {
//            if (cursor != null) {
//                cursor.close();
//            }
//        }
        Log.i("xzy", "path is: " + contentUri.getPath());
        return contentUri.getPath();
    }

    public static void closeSilently(Closeable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (IOException t) {
            Log.w(TAG, "close fail ", t);
        }
    }

    public static Bitmap resizeBitmapByScale(
            Bitmap bitmap, float scale, boolean recycle) {
        int width = Math.round(bitmap.getWidth() * scale);
        int height = Math.round(bitmap.getHeight() * scale);
        if (width == bitmap.getWidth()
                && height == bitmap.getHeight()) {
            return bitmap;
        }
        Bitmap target = Bitmap.createBitmap(width, height, getConfig(bitmap));
        Canvas canvas = new Canvas(target);
        canvas.scale(scale, scale);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        if (recycle) {
            bitmap.recycle();
        }
        return target;
    }

    private static Bitmap.Config getConfig(Bitmap bitmap) {
        Bitmap.Config config = bitmap.getConfig();
        if (config == null) {
            config = Bitmap.Config.ARGB_8888;
        }
        return config;
    }

    public static int getScreenWidth(Context context) {
        int screenWidth;
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        screenWidth = displayMetrics.widthPixels;
        if (screenWidth == 0) {
            screenWidth = 960;
        }
        return screenWidth;
    }

    /**
     * 保存图片到文件
     *
     * @param bitmap  注意，bitmap在函数内未回收，需要使用者在函数执行之后手动recyle
     * @param ctx     Context for making Toast
     * @param format  The format of the compressed image
     * @param quality Hint to the compressor, 0-100. 0 meaning compress for small
     *                size, 100 meaning compress for max quality. Some formats, like
     *                PNG which is lossless, will ignore the quality setting
     */
    public static String saveBitmap(Bitmap bitmap, Context ctx,
            Bitmap.CompressFormat format, int quality) {
        try {
            if (bitmap == null) {
                return null;
            }
            int count = 15;
            File file = null;
            RandomAccessFile accessFile = null;
            int MagicNum;
            String path = null;
            Random random = new Random();
            String dir = getSavePicPath(ctx);
            File dirFile = new File(dir);
            if (!dirFile.exists()) {
                if (!dirFile.mkdirs()) {
                    Toast.makeText(ctx, "存文件失败", Toast.LENGTH_SHORT).show();
                    return null;
                }
            }

            String suffix = null;
            if (Bitmap.CompressFormat.JPEG.equals(format)) {
                suffix = ".jpg";
            } else if (Bitmap.CompressFormat.PNG.equals(format)) {
                suffix = ".png";
            } else {
                return null;
            }

            do {
                MagicNum = (int) random.nextLong();
                path = dir + "/" + String.valueOf(MagicNum) + suffix;
                file = new File(path);
                count--;
            } while (!file.exists() && count > 0);

            ByteArrayOutputStream steam = new ByteArrayOutputStream();
            bitmap.compress(format, quality, steam);
            byte[] buffer = steam.toByteArray();

            try {
                accessFile = new RandomAccessFile(file, "rw");
                accessFile.write(buffer);
            } catch (Exception e) {
                return null;
            } finally {
                steam.close();
                if (accessFile != null) {
                    accessFile.close();
                }
            }
            return path;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * 保存图片到文件
     *
     * @param bitmap 注意，bitmap在函数内未回收，需要使用者在函数执行之后手动recyle
     */
    public static String saveBitmap(Bitmap bitmap, Context ctx) {
        return saveBitmap(bitmap, ctx, Bitmap.CompressFormat.PNG, 100);
    }

    public static String getSavePicPath(Context context) {
        String dcimDir = null;
        File externalDCIMFolder = null;

        externalDCIMFolder = Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);

        if (externalDCIMFolder == null || !externalDCIMFolder.exists()) {
            File externalDir = Environment.getExternalStorageDirectory();
            if (externalDir == null) {
                return null;
            }
            dcimDir = externalDir.getAbsolutePath() + "/DCIM";
            try {
                new File(dcimDir).mkdirs();
            } catch (Exception e) {
                //do nothing, just to avoid crash.
            }
        } else {
            dcimDir = externalDCIMFolder.getAbsolutePath();
        }
        Log.i("xzy", "dcim dir is: " + dcimDir);
        return dcimDir + "/TmallPic";
    }


    public static String getSavePicPathWithRandomName(Context ctx) {
        int MagicNum;
        Random random = new Random();
        String path = null;
        String dir = getSavePicPath(ctx);
        if (dir == null) {
            return null;
        }
        File dirFile = new File(dir);
        File file = null;
        int count = 15;
        Log.i("xzy", "dirFile is : " + dirFile);
        if (!dirFile.exists()) {
            if (!dirFile.mkdirs()) {
                Toast.makeText(ctx, "...", Toast.LENGTH_SHORT).show();
                return null;
            }
        }

        Calendar cal = new GregorianCalendar();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        String today = formatter.format(cal.getTime());
        do {
            MagicNum = (int) random.nextLong();
            path = dir + "/" + today + "_" + String.valueOf(MagicNum) + ".jpg";
            file = new File(path);
            count--;
        } while (file.exists() && count > 0);
        return path;
    }
}
