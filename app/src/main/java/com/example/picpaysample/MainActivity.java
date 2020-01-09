package com.example.picpaysample;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;


public class MainActivity extends AppCompatActivity {
	/*-- you can customize these options for your convenience --*/
	private static String webview_url   = "file:///android_res/raw/index.html";    // web address or local file location you want to open in webview
	private static String file_type     = "image/*";    // file types to be allowed for upload
	private boolean multiple_files      = true;         // allowing multiple file upload

	/*-- MAIN VARIABLES --*/
	WebView webView;

	private static final String TAG = MainActivity.class.getSimpleName();

	private String cam_file_data = null;        // for storing camera file information
	private ValueCallback<Uri> file_data;       // data/header received after file selection
	private ValueCallback<Uri[]> file_path;     // received file(s) temp. location

	private final static int file_req_code = 1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		//TODO: Replace url from SSO here
		//String url = "https://sso.alliedpayment.com/#/";
		String url = "https://billpay.demo.alliedpayment.com/billpayv2/sso?IV=RxLr71r1lnQkz9tDYAx9DA,,&ssoToken=9wm1p44QU_OOtahvD4PfqWxJVIA8PAjWwktq9q-qUtrRDeXbZstpWoiG8S-ymIwwmkk_b1upFyrgyJhW3y2lAe4liN9s6eFec_O4juKGhARrNeEaa-qhR7E60Hoao_UZtpFt8IArm9KpsjkQ891qMg,,&signature=8D7513439C39856D094954CE82761FB3078CF15A";

		this.webView = new WebView(getApplicationContext());
		setContentView(webView);
		webView.setWebViewClient(new WebClient());
		webView.setWebChromeClient(new MyWebChromeClient());
		webView.getSettings().setJavaScriptEnabled(true);

		webView.loadUrl(url);
		setContentView(webView);

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent){
		super.onActivityResult(requestCode, resultCode, intent);
		if(Build.VERSION.SDK_INT >= 21){
			Uri[] results = null;

			/*-- if file request cancelled; exited camera. we need to send null value to make future attempts workable --*/
			if (resultCode == Activity.RESULT_CANCELED) {
				if (requestCode == file_req_code) {
					file_path.onReceiveValue(null);
					return;
				}
			}

			/*-- continue if response is positive --*/
			if(resultCode== Activity.RESULT_OK){
				if(requestCode == file_req_code){
					if(null == file_path){
						return;
					}

					ClipData clipData;
					String stringData;
					try {
						clipData = intent.getClipData();
						stringData = intent.getDataString();
					}catch (Exception e){
						clipData = null;
						stringData = null;
					}

					if (clipData == null && stringData == null && cam_file_data != null) {
						results = new Uri[]{Uri.parse(cam_file_data)};
					}else{
						if (clipData != null) { // checking if multiple files selected or not
							final int numSelectedFiles = clipData.getItemCount();
							results = new Uri[numSelectedFiles];
							for (int i = 0; i < clipData.getItemCount(); i++) {
								results[i] = clipData.getItemAt(i).getUri();
							}
						} else {
							results = new Uri[]{Uri.parse(stringData)};
						}
					}
				}
			}
			file_path.onReceiveValue(results);
			file_path = null;
		}else{
			if(requestCode == file_req_code){
				if(null == file_data) return;
				Uri result = intent == null || resultCode != RESULT_OK ? null : intent.getData();
				file_data.onReceiveValue(result);
				file_data = null;
			}
		}
	}

	/*-- creating new image file here --*/
	private File create_image() throws IOException{
		@SuppressLint("SimpleDateFormat") String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String imageFileName = "img_"+timeStamp+"_";
		File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
		return File.createTempFile(imageFileName,".jpg",storageDir);
	}

	/*-- checking and asking for required file permissions --*/
	public boolean file_permission(){
		if(Build.VERSION.SDK_INT >=23 && (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
			ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA}, 1);
			return false;
		}else{
			return true;
		}
	}

	class MyWebChromeClient extends WebChromeClient {
		String TAG = "TAG";
		// Grant permissions for cam
		@Override
		public void onPermissionRequest(final PermissionRequest request) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				request.grant(request.getResources());
			}
		}

		@Override
		public boolean onShowFileChooser(WebView mWebView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {

			if(file_permission() && Build.VERSION.SDK_INT >= 21) {
				file_path = filePathCallback;
				Intent takePictureIntent = null;
				Intent takeVideoIntent = null;

				boolean includeVideo = false;
				boolean includePhoto = false;

				/*-- checking the accept parameter to determine which intent(s) to include --*/
				paramCheck:
				for (String acceptTypes : fileChooserParams.getAcceptTypes()) {
					String[] splitTypes = acceptTypes.split(", ?+"); // although it's an array, it still seems to be the whole value; split it out into chunks so that we can detect multiple values
					for (String acceptType : splitTypes) {
						switch (acceptType) {
							case "*/*":
								includePhoto = true;
								includeVideo = true;
								break paramCheck;
							case "image/*":
								includePhoto = true;
								break;
							case "video/*":
								includeVideo = true;
								break;
						}
					}
				}

				if (fileChooserParams.getAcceptTypes().length == 0) {   //no `accept` parameter was specified, allow both photo and video
					includePhoto = true;
					includeVideo = true;
				}

				if (includePhoto) {
					takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
					if (takePictureIntent.resolveActivity(MainActivity.this.getPackageManager()) != null) {
						File photoFile = null;
						try {
							photoFile = create_image();
							takePictureIntent.putExtra("PhotoPath", cam_file_data);
						} catch (IOException ex) {
							Log.e(TAG, "Image file creation failed", ex);
						}
						if (photoFile != null) {
							cam_file_data = "file:" + photoFile.getAbsolutePath();
							takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
						} else {
							cam_file_data = null;
							takePictureIntent = null;
						}
					}
				}

				Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
				contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
				contentSelectionIntent.setType(file_type);
				if (multiple_files) {
					contentSelectionIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
				}

				Intent[] intentArray;
				if (takePictureIntent != null && takeVideoIntent != null) {
					intentArray = new Intent[]{takePictureIntent, takeVideoIntent};
				} else if (takePictureIntent != null) {
					intentArray = new Intent[]{takePictureIntent};
				} else if (takeVideoIntent != null) {
					intentArray = new Intent[]{takeVideoIntent};
				} else {
					intentArray = new Intent[0];
				}

				Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
				chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
				chooserIntent.putExtra(Intent.EXTRA_TITLE, "File chooser");
				chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);
				startActivityForResult(chooserIntent, file_req_code);
				return true;
			} else {
				return false;
			}
		}

	}

	private class WebClient extends WebViewClient {

	}
}



