/**
 * Copyright (C) 2012 30ideas (http://30ide.as)
 * MIT licensed
 *
 * @author Josemando Sobral
 * @created Jul 2nd, 2012.
 * improved by Hongbo LU
 */
package com.darktalker.cordova.screenshot;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;


public class Screenshot extends CordovaPlugin {
    private CallbackContext mCallbackContext;
    private String mAction;
    private JSONArray mArgs;


    private String mFormat;
    private String mFileName;
    private Integer mQuality;

    protected final static String[] PERMISSIONS = {Manifest.permission.WRITE_EXTERNAL_STORAGE};
    public static final int PERMISSION_DENIED_ERROR = 20;
    public static final int SAVE_SCREENSHOT_SEC = 0;

    @Override
    public Object onMessage(String id, Object data) {
        if (id.equals("onGotXWalkBitmap")) {
            Bitmap bitmap = (Bitmap) data;
            if (bitmap != null) {
                if (mAction.equals("saveScreenshot")) {
                    saveScreenshot(bitmap, mFormat, mFileName, mQuality);
                } else if (mAction.equals("getScreenshotAsURI")) {
                    getScreenshotAsURI(bitmap, mQuality);
                }
            }
        }
        return null;
    }

    private Bitmap getBitmap() {
        Bitmap bitmap = null;

        View view = webView.getView();//.getRootView();
        view.setDrawingCacheEnabled(true);
        bitmap = Bitmap.createBitmap(view.getDrawingCache());
        view.setDrawingCacheEnabled(false);

        return bitmap;
    }

    private void saveScreenshot(Bitmap bitmap, String format, String fileName, Integer quality) {
        try {
            final ContentValues values = new ContentValues();
            String mimetype;
            CompressFormat imgFormat = null;
            if(format.equals("png")) {
                mimetype = "image/png";
                imgFormat = CompressFormat.PNG;
            }
            else if(format.equals("jpg")) {
                mimetype = "image/jpeg";
                imgFormat = CompressFormat.JPEG;
            }
            else throw new IOException("Format not recognized");
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimetype);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures");

            final ContentResolver resolver = this.cordova.getActivity().getApplicationContext()
                    .getContentResolver();

            Uri uri;

            JSONObject jsonRes = new JSONObject();
            PluginResult result;

            try {
                uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if(uri == null)
                    throw new IOException("Failed to create MediaStore record");
                try(final OutputStream stream = resolver.openOutputStream(uri)){
                    if(stream == null) throw new IOException("Failed to open output stream");
                    if(!bitmap.compress(imgFormat, quality == null ? 100 : quality, stream))
                        throw new IOException("Could not save bitmap");
                }

                jsonRes.put("filePath", uri.getPath());
                result = new PluginResult(PluginResult.Status.OK, jsonRes);
            } catch (IOException e) {
                jsonRes.put("error", e.getMessage());
                result = new PluginResult(PluginResult.Status.OK, jsonRes);
            }

            mCallbackContext.sendPluginResult(result);

        } catch (JSONException | IOException e) {
            mCallbackContext.error(e.getMessage());
        }
    }

    private void getScreenshotAsURI(Bitmap bitmap, int quality) {
        try {
            ByteArrayOutputStream jpeg_data = new ByteArrayOutputStream();

            if (bitmap.compress(CompressFormat.JPEG, quality, jpeg_data)) {
                byte[] code = jpeg_data.toByteArray();
                byte[] output = Base64.encode(code, Base64.NO_WRAP);
                String js_out = new String(output);
                js_out = "data:image/jpeg;base64," + js_out;
                JSONObject jsonRes = new JSONObject();
                jsonRes.put("URI", js_out);
                PluginResult result = new PluginResult(PluginResult.Status.OK, jsonRes);
                mCallbackContext.sendPluginResult(result);

                js_out = null;
                output = null;
                code = null;
            }

            jpeg_data = null;

        } catch (Exception e) {
            mCallbackContext.error(e.getMessage());
        }
    }

    public void saveScreenshot() throws JSONException{
        mFormat = (String) mArgs.get(0);
        mQuality = (Integer) mArgs.get(1);
        mFileName = (String) mArgs.get(2);

        super.cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mFormat.equals("png") || mFormat.equals("jpg")) {
                    Bitmap bitmap = getBitmap();
                    if (bitmap != null) {
                        saveScreenshot(bitmap, mFormat, mFileName, mQuality);
                    }
                } else {
                    mCallbackContext.error("format " + mFormat + " not found");

                }
            }
        });
    }

    public void getScreenshotAsURI() throws JSONException{
        mQuality = (Integer) mArgs.get(0);

        super.cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = getBitmap();
                if (bitmap != null) {
                    getScreenshotAsURI(bitmap, mQuality);
                }
            }
        });
    }

    public void getScreenshotAsURISync() throws JSONException{
        mQuality = (Integer) mArgs.get(0);

        Runnable r = new Runnable(){
            @Override
            public void run() {
                Bitmap bitmap = getBitmap();
                if (bitmap != null) {
                    getScreenshotAsURI(bitmap, mQuality);
                }
                synchronized (this) { this.notify(); }
            }
        };

        synchronized (r) {
            super.cordova.getActivity().runOnUiThread(r);
            try{
                r.wait();
            } catch (InterruptedException e){
                mCallbackContext.error(e.getMessage());
            }
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        // starting on ICS, some WebView methods
        // can only be called on UI threads
        mCallbackContext = callbackContext;
        mAction = action;
        mArgs = args;

        if (action.equals("saveScreenshot")) {
            if(Build.VERSION.SDK_INT >= 30 || PermissionHelper.hasPermission(this, PERMISSIONS[0])) {
                saveScreenshot();
            } else {
                PermissionHelper.requestPermissions(this, SAVE_SCREENSHOT_SEC, PERMISSIONS);
            }
            return true;
        } else if (action.equals("getScreenshotAsURI")) {
            getScreenshotAsURI();
            return true;
        } else if (action.equals("getScreenshotAsURISync")){
            getScreenshotAsURISync();
            return true;
        }
        callbackContext.error("action not found");
        return false;
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException
    {
        for(int r:grantResults)
        {
            if(r == PackageManager.PERMISSION_DENIED)
            {
                mCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, PERMISSION_DENIED_ERROR));
                return;
            }
        }
        if (requestCode == SAVE_SCREENSHOT_SEC) {
            saveScreenshot();
        }
    }


}