package com.xzy.andoridcrop;

import com.xzy.andoridcrop.cropImage.CropImageHelper;
import com.xzy.andoridcrop.cropImage.TMImlabCropActivity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private int SELECT_PIC = 1;

    private int CROP_PIC = 2;

    private ImageView mImageView;

    private String corpFileSavePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        View button = findViewById(R.id.pick_bitmap);
        mImageView = (ImageView) findViewById(R.id.image_view);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                choosePic();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public static void cropImageUseTMImlabCropActivity(Activity activity, int requestCode,
            Uri intUri, String outPath) {
        cropImageUseTMImlabCropActivity(activity, requestCode, intUri, outPath, null);
    }

    public static void cropImageUseTMImlabCropActivity(Activity activity, int requestCode,
            Uri intUri, String outPath, HashMap<String, String> map) {
        if (TextUtils.isEmpty(outPath)) {
            return;
        }
        File file = new File(outPath);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

//        Intent intent = new Intent("com.android.camera.action.CROP");
        Intent intent = new Intent(activity, TMImlabCropActivity.class);
        intent.setDataAndType(intUri, "image/*");
        intent.putExtra("crop", "true");
        intent.putExtra("aspectX", 1);
        intent.putExtra("aspectY", 1);
        intent.putExtra("scale", true);
        intent.putExtra(MediaStore.EXTRA_OUTPUT,
                Uri.fromFile(file));
        intent.putExtra("return-data", false);
        intent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString());
        intent.putExtra("outputX", 960);
        intent.putExtra("outputY", 960);

        if ((null != map) && (map.size() > 0)) {
            Iterator<String> keys = map.keySet().iterator();

            while (keys.hasNext()) {
                String key = keys.next();
                String value = map.get(key);
                intent.putExtra(key, value);
            }
        }
        activity.startActivityForResult(intent, requestCode);
    }

    private void choosePic() {
        Intent i = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        if (isPackageInstalled(this, "com.android.gallery3d")) {
            i.setPackage("com.android.gallery3d");
        }
        startActivityForResult(i, SELECT_PIC);
    }

    public static boolean isPackageInstalled(Context ctx, String packageName) {
        {
            PackageManager manager = ctx.getPackageManager();
            List<PackageInfo> pkgList = manager.getInstalledPackages(0);
            for (int i = 0; i < pkgList.size(); i++) {
                PackageInfo pI = pkgList.get(i);
                if (pI.packageName.equalsIgnoreCase(packageName)) {
                    return true;
                }
            }
            return false;
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SELECT_PIC) {
            if (resultCode == RESULT_OK && data.getData() != null) {
                Uri selectedImage = data.getData();
                String[] filePathColumn = {MediaStore.Images.Media.DATA};

                Cursor cursor = getContentResolver().query(selectedImage,
                        filePathColumn, null, null, null);
                if (cursor != null) {
                    cursor.moveToFirst();

                    int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                    String filePath = cursor.getString(columnIndex);
                    cursor.close();
//                    cropImageUri(filePath);
                    Log.i("xzy", "file Path is: " + filePath);

                    Bitmap bitmap = BitmapFactory.decodeFile(filePath);

                    mImageView.setImageBitmap(bitmap);

                    cropImageUri(filePath);
                }
            } else {
                //ERROR
            }
        } else if (requestCode == CROP_PIC) {
            if (resultCode == RESULT_OK) {
                String filePath = null;
                Bitmap photo = null;
                Uri uri = null;
                if (data != null) {
                    if (data.getData() != null) {
//                        try {
//                            Cursor cursor = this.getContentResolver()
//                                    .query(data.getData(), null, null,
//                                            null, null);
//                            if (cursor.moveToFirst()) {
//                                filePath = cursor.getString(cursor
//                                        .getColumnIndex("_data"));
//                            }
//                            cursor.close();
//                        } catch (Exception e) {
//                        }
                        uri = data.getData();
                    } else {
                        photo = data.getParcelableExtra("data");
                    }
                }

                if (photo != null) {
                    filePath = CropImageHelper.saveBitmap(photo, this);

                    try {
                        if (photo != null) {
                            photo.recycle();
                        }
                    } catch (Exception e) {
                    }
                }

                if (TextUtils.isEmpty(filePath)) {
                    filePath = corpFileSavePath;
                }
                //裁剪后的照片有多种可能，将最终路径赋给corpFileSavePath，供activity结束时删除
                corpFileSavePath = filePath;

//                filter(corpFileSavePath);
                Bitmap bitmap = BitmapFactory.decodeFile(corpFileSavePath);


//                Uri uri = null;
//                try {
//                    uri = Uri.fromFile(new File(corpFileSavePath));
//                } catch (Exception e) {
//                    return;
//                }
                mImageView.setImageURI(uri);

//                mImageView.setImageBitmap(bitmap);
                Log.i("xzy", "corpFileSavePath is : " + corpFileSavePath);

            } else {
                finish();
            }
        }
    }

    private void cropImageUri(String path) {

        corpFileSavePath = CropImageHelper.getSavePicPathWithRandomName(this);
        if (corpFileSavePath == null) {
            Toast.makeText(this, "ERROR", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Uri uri = null;
        try {
            uri = Uri.fromFile(new File(path));
        } catch (Exception e) {
            return;
        }

        cropImageUseTMImlabCropActivity(this, CROP_PIC, uri, corpFileSavePath);
    }

    public static String saveBitmap(Bitmap bitmap, Context ctx) {
        return saveBitmap(bitmap, ctx, Bitmap.CompressFormat.PNG, 100);
    }

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
            String dir = CropImageHelper.getSavePicPath(ctx);
            File dirFile = new File(dir);
            if (!dirFile.exists()) {
                if (!dirFile.mkdirs()) {
                    Toast.makeText(ctx,
                            "保存失败",
                            Toast.LENGTH_SHORT).show();
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
}
