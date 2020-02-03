package com.example.picpaysample;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
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
	/*-- MAIN VARIABLES --*/
	private WebView webView;
	private static String file_type = "image/*";    // file types to be allowed for upload
	private String cam_file_data = null;        // for storing camera file information
	private ValueCallback<Uri> file_data;       // data/header received after file selection
	private ValueCallback<Uri[]> file_path;     // received file temp. location
	private final static int file_req_code = 1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		//TODO: Replace url from SSO here
		String url = "https://billpay.final.alliedpaymentnetwork.com/BillPayV2/Account/Login";

		//Create the webview
		this.webView = new WebView(getApplicationContext());

		//Create the webview clients
		this.webView.setWebViewClient(new WebViewClient());
		this.webView.setWebChromeClient(new MyWebChromeClient());

		//Enable javascript
		this.webView.getSettings().setJavaScriptEnabled(true);
		this.webView.getSettings().setDomStorageEnabled(true);

		//Load the URL
		this.webView.loadUrl(url);
		setContentView(this.webView);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent){
		super.onActivityResult(requestCode, resultCode, intent);

		if(Build.VERSION.SDK_INT >= 21){
			Uri[] results = null;

			//Check for cancel from the user to send null so we can try again later
			if (resultCode == Activity.RESULT_CANCELED) {
				if (requestCode == file_req_code) {
					file_path.onReceiveValue(null);
					return;
				}
			}

			//Continue if response is positive
			if(resultCode== Activity.RESULT_OK){
				if(requestCode == file_req_code){
					if(null == file_path){
						return;
					}

					String stringData;

					//Get file data
					try {
						stringData = intent.getDataString();
					}catch (Exception e){
						stringData = null;
					}

					//Parse results
					if (stringData == null && cam_file_data != null) {
						results = new Uri[]{Uri.parse(cam_file_data)};
					} else {
						results = new Uri[]{Uri.parse(stringData)};
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

	//Creates a new image file
	private File create_image() throws IOException{
		@SuppressLint("SimpleDateFormat") String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String imageFileName = "img_"+timeStamp+"_";
		File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
		return File.createTempFile(imageFileName,".jpg",storageDir);
	}

	//Checks and requests for required file permissions
	public boolean file_permission(){
		if(Build.VERSION.SDK_INT >=23 && (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
			ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA}, 1);
			return false;
		}else{
			return true;
		}
	}



	class MyWebChromeClient extends WebChromeClient {
		// Grant permissions for cam
		@Override
		public void onPermissionRequest(final PermissionRequest request) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				request.grant(request.getResources());
			}
		}

		//Create and execute the intent to take a picture
		@Override
		public boolean onShowFileChooser(WebView mWebView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {

			if(file_permission() && Build.VERSION.SDK_INT >= 21) {
				file_path = filePathCallback;

				//Create the intent to take a picture
				Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

				if (takePictureIntent.resolveActivity(MainActivity.this.getPackageManager()) != null) {
					File photoFile = null;

					//Attempt to create the photo
					try {
						photoFile = create_image();
						takePictureIntent.putExtra("PhotoPath", cam_file_data);
					} catch (IOException ex) {
						Log.e("TAG", "Image file creation failed", ex);
					}

					//Check if image was created
					if (photoFile != null) {
						cam_file_data = "file:" + photoFile.getAbsolutePath();
						takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
					} else {
						cam_file_data = null;
						takePictureIntent = null;
					}
				}

				//Create the intent that will pick or create the image
				Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
				contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
				contentSelectionIntent.setType(file_type);

				//Verify we have an intent to take a picture
				Intent[] intentArray;
				if (takePictureIntent != null) {
					intentArray = new Intent[]{takePictureIntent};
				} else {
					intentArray = new Intent[0];
				}

				//Create popup to select camera or local pictures
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
}



