package com.example.imageeditor4;

import java.io.File;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

public class MainActivity extends Activity {

	private static Uri m_uri;
	//private static final int REQUEST_CHOOSER = 1000;
	private static final int REQUEST_CAMERA = 100;
	private static final int REQUEST_GALLERY = 1000;
	private static final int KITKAT_API_LEVEL = 19;

	private void setViews() {
		Button cameraButton = (Button)findViewById(R.id.button1);
		Button galleryButton = (Button)findViewById(R.id.button2);
		cameraButton.setOnClickListener(cameraButton_onClick);
		galleryButton.setOnClickListener(galleryButton_onClick);
	}

	private View.OnClickListener cameraButton_onClick = new View.OnClickListener() {

		@Override
		public void onClick(View v) {
			showCamera();
		}
	};

	private View.OnClickListener galleryButton_onClick = new View.OnClickListener() {

		@Override
		public void onClick(View v) {
			showGallery();
		}
	};

	/*
	 * カメラを起動する
	 */
	private void showCamera() {
		// カメラの起動Intentの用意
		File pathExternalPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
		String photoName = System.currentTimeMillis() + ".jpg";
		File capturedFile = new File(pathExternalPublicDir, photoName);
		m_uri = Uri.fromFile(capturedFile);
		//m_uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);

		Intent intentCamera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		intentCamera.putExtra(MediaStore.EXTRA_OUTPUT, m_uri);
		startActivityForResult(intentCamera, REQUEST_CAMERA);
	}

	/*
	 * ギャラリー(ドキュメント)を開く
	 */
	private void showGallery() {
		// ギャラリー用のIntent作成
		Intent intentGallery;
		if (Build.VERSION.SDK_INT < KITKAT_API_LEVEL) {
			intentGallery = new Intent(Intent.ACTION_GET_CONTENT);
			intentGallery.setType("image/*");
		} else {
			intentGallery = new Intent(Intent.ACTION_OPEN_DOCUMENT);
			intentGallery.addCategory(Intent.CATEGORY_OPENABLE);
			intentGallery.setType("image/*");
		}
		startActivityForResult(intentGallery, REQUEST_GALLERY);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == REQUEST_CAMERA || requestCode == REQUEST_GALLERY) {
			if (resultCode != RESULT_OK) {
				// キャンセル時
				return;
			}

			Uri resultUri = (data != null ? data.getData() : m_uri);
			if (resultUri == null) {
				// 取得失敗
				return;
			}

			// ギャラリーへスキャンを促す
			MediaScannerConnection.scanFile(
					this,
					new String[]{resultUri.getPath()},
					new String[]{"image/jpeg"},
					null
			);
			// プレビュー画面へuriを受け渡す
			Intent intent = new Intent(MainActivity.this, PreviewActivity.class);
			//Intentで受け渡す情報をセット
			intent.setData(resultUri);
			startActivity(intent);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		setViews();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.

		return super.onOptionsItemSelected(item);
	}

}
