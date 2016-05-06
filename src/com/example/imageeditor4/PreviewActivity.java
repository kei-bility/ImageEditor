package com.example.imageeditor4;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;

import org.json.JSONException;
import org.json.JSONObject;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

public class PreviewActivity extends Activity implements View.OnClickListener {

	/** プログレスダイアログ */
    private static ProgressDialog waitDialog;
    private static JSONObject res = new JSONObject();
	private static Bitmap bitmap = null;
	private static Bitmap __outBitmap = null;
	private static Bitmap maskImage = null;
	private static Bitmap source = null;
	private static Bitmap resultImage = null;
	private static final int REQUEST_GALLERY = 1;
	private static final int REQUEST_CHECK_ALBUM = 2;
	private static final int REQUEST_CAMERA_SOURCE = 3;
	private static final int REQUEST_SETTINGS = 4;
	private static final int KITKAT_API_LEVEL = 19;
	private static final int CATEGORY_NUM = 20;
	private static int iteration = 10000;
	ZoomImageView zoomImageView;
	private static Uri capturedUri;
	// いまどの画像を表示しているか
	// 1: セグメンテーションした画像
	// 2: 合成した画像
	private int onDisplay = 1;
	// 画像内にある認識したカテゴリーのID
	private int[] existButtonID = new int[CATEGORY_NUM];
	private boolean[] paint = new boolean[CATEGORY_NUM];
	private boolean[] existCategory = new boolean[CATEGORY_NUM];	// 画像内にそのカテゴリーがあるかどうか

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_preview);

		hideLegend(); // 凡例をすべて表示しないようにする
		setButtonID();
		Intent intent = getIntent();
		Uri data = intent.getData();
		InputStream inputStream = null;
		try {
			inputStream = getContentResolver().openInputStream(data);
		} catch (FileNotFoundException e) {
		}
		// 画像をメモリに読み込まずoption情報だけ読む
		BitmapFactory.Options imageOptions = new BitmapFactory.Options();
		imageOptions.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(inputStream, null, imageOptions);
		try {
			inputStream.close();
		} catch (IOException e3) {
		}
		int imageSizeMax = 500;
		try {
			inputStream = getContentResolver().openInputStream(data);
		} catch (FileNotFoundException e1) {
		}

		int shrink_ratio = 1;
		int long_size = 0;
		long_size = Math.max(imageOptions.outWidth, imageOptions.outHeight);  /* 長辺しか気にしなくてよい。短い辺は無視*/

		while (long_size > imageSizeMax) {
			long_size /= 2;  /* long_size = (long_size >> 1) */
			shrink_ratio *= 2;
		}
		// shrink_ratioで縮小
		BitmapFactory.Options imOpt = new BitmapFactory.Options();
		imOpt.inSampleSize = shrink_ratio;
		imOpt.inMutable = true;
		bitmap = BitmapFactory.decodeStream(inputStream, null, imOpt);

		try {
			inputStream.close();
		} catch (IOException e1) {
		}
		zoomImageView = new ZoomImageView(this);
		zoomImageView.setImageBitmap(bitmap);	// ギャラリーで選択されたorカメラで撮った画像を一旦表示

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
		byte[] bytes = stream.toByteArray();
		String strBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
		postRequest(strBase64);
		showDialog();
	}

	/**
	 * POSTリクエストでbase64エンコードした画像データを計算サーバーへ送信
	 */
	private void postRequest(String base64) {
		//final String url = "http://192.168.85.240:8080/segmentation/for_multilabel/";
		final String url = "http://mcs-server-dev15.morphoinc.com:3000/segmentation/for_multilabel/";
		final String tag_json_obj = "json_obj_req_tag";

		try {
            // サーバへ送信するデータを作る
            JSONObject jsonRequest = new JSONObject();
            jsonRequest.put("title", base64);

            // サーバへデータ送信
            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, jsonRequest, myListener, myErrorListener);
         // リクエストのタイムアウトの設定
    		jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(
    				15000,
    				DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
    				DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            // リクエストキューにリクエストを追加(これで通信がはじまってデータがサーバへ送られるっぽい)
    		AppController.getInstance().addToRequestQueue(jsonObjectRequest, tag_json_obj);
        } catch (JSONException e) {
            e.printStackTrace();
        }
	}

	/**
     * レスポンス受信のリスナー
     */
	private Listener<JSONObject> myListener = new Listener<JSONObject>() {
        @Override
        public void onResponse(JSONObject response) {
            Log.d("TEST", "Response success!");
            setResponseData(response);		// アノテーションデータをセット
    		try {
    			JSONObject json = new JSONObject();
    			json = getResponseData();
    			// アノテーションデータが受信できているかチェック
    			if (json.get("1") == JSONObject.NULL) {
    				//Log.e("json", "error");
    				return;
    			}
    			createMaskImage(json);	// AsyncTaskを裏で走らせる
    			findObject(json);// アノテーションデータを基にビットマップに色付けして凡例を表示
    			waitDialog.dismiss();
    			zoomImageView.setImageBitmap(__outBitmap);
    			//setImageView(true, __outBitmap);
    		} catch (JSONException e) {
    			// TODO 自動生成された catch ブロック
    			e.printStackTrace();
    		}
        }
    };

    /**
     * リクエストエラーのリスナー
     */
    private ErrorListener myErrorListener = new ErrorListener() {
        @Override
        public void onErrorResponse(VolleyError error) {
            Log.e("TEST", error.getMessage());
        }
    };

	/**
	 * 計算サーバーのレスポンスを基に20クラスのセグメンテーションとクラスの表示
	 * @param json
	 */
	private void findObject(JSONObject json) {
		// jsonで色付け
        Bitmap outBitMap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        int width = outBitMap.getWidth();
        int height = outBitMap.getHeight();

        Arrays.fill(existCategory, false);		// 初期値はfalseにしておく
        Arrays.fill(paint, false);
        for (int y = 0; y < height; y++) {
        	for (int x = 0; x < width; x++) {
            	int labelData = -1;
            	try {
					labelData = Integer.parseInt(json.getString(String.valueOf(y * width + x)));
				} catch (NumberFormatException e) {
					e.printStackTrace();
				} catch (JSONException e) {
				} finally {
					if (labelData == 0) {
						continue;
					}
				}
            	int pixelColor = outBitMap.getPixel(x, y);
        		int R = Color.red(pixelColor);
        		int G = Color.green(pixelColor);
        		int B = Color.blue(pixelColor);

				if (labelData > 0 && labelData < 21) {
					existCategory[labelData-1] = true;	// backgroundは表示しないので-1する
					paint[labelData-1] = true;
				}
				switch (labelData) {
				case 1:
					R = (R + 128 > 255) ? 255 : R + 128;
	        		G = (G + 0 > 255) ? 255 : G + 0;
	        		B = (R + 0 > 255) ? 255 : B + 0;
				case 2:
					R = (R + 0 > 255) ? 255 : R + 0;
	        		G = (G + 128 > 255) ? 255 : G + 128;
	        		B = (R + 0 > 255) ? 255 : B + 0;
	        		break;
				case 3:
					R = (R + 128 > 255) ? 255 : R + 128;
	        		G = (G + 128 > 255) ? 255 : G + 128;
	        		B = (R + 0 > 255) ? 255 : B + 0;
	        		break;
				case 4:
					R = (R + 0 > 255) ? 255 : R + 0;
	        		G = (G + 0 > 255) ? 255 : G + 0;
	        		B = (R + 128 > 255) ? 255 : B + 129;
	        		break;
				case 5:
					R = (R + 128 > 255) ? 255 : R + 128;
	        		G = (G + 0 > 255) ? 255 : G + 0;
	        		B = (R + 128 > 255) ? 255 : B + 128;
	        		break;
				case 6:
					R = (R + 0 > 255) ? 255 : R + 0;
	        		G = (G + 128 > 255) ? 255 : G + 128;
	        		B = (R + 128 > 255) ? 255 : B + 128;
	        		break;
				case 7:
					R = (R + 128 > 255) ? 255 : R + 128;
	        		G = (G + 128 > 255) ? 255 : G + 128;
	        		B = (R + 128 > 255) ? 255 : B + 128;
	        		break;
				case 8:
					R = (R + 64 > 255) ? 255 : R + 64;
	        		G = (G + 0 > 255) ? 255 : G + 0;
	        		B = (R + 0 > 255) ? 255 : B + 0;
	        		break;
				case 9:
					R = (R + 192 > 255) ? 255 : R + 192;
	        		G = (G + 0 > 255) ? 255 : G + 0;
	        		B = (R + 0 > 255) ? 255 : B + 0;
	        		break;
				case 10:
					R = (R + 64 > 255) ? 255 : R + 64;
	        		G = (G + 128 > 255) ? 255 : G + 128;
	        		B = (R + 0 > 255) ? 255 : B + 0;
	        		break;
				case 11:
					R = (R + 192 > 255) ? 255 : R + 192;
	        		G = (G + 128 > 255) ? 255 : G + 128;
	        		B = (R + 0 > 255) ? 255 : B + 0;
	        		break;
				case 12:
					R = (R + 64 > 255) ? 255 : R + 64;
	        		G = (G + 0 > 255) ? 255 : G + 0;
	        		B = (R + 128 > 255) ? 255 : B + 128;
	        		break;
				case 13:
					R = (R + 192 > 255) ? 255 : R + 192;
	        		G = (G + 0 > 255) ? 255 : G + 0;
	        		B = (R + 128 > 255) ? 255 : B + 128;
	        		break;
				case 14:
					R = (R + 64 > 255) ? 255 : R + 64;
	        		G = (G + 128 > 255) ? 255 : G + 128;
	        		B = (R + 128 > 255) ? 255 : B + 128;
	        		break;
				case 15:
					R = (R + 192 > 255) ? 255 : R + 192;
	        		G = (G + 128 > 255) ? 255 : G + 128;
	        		B = (R + 128 > 255) ? 255 : B + 128;
	        		break;
				case 16:
					R = (R + 0 > 255) ? 255 : R + 0;
	        		G = (G + 64 > 255) ? 255 : G + 64;
	        		B = (R + 0 > 255) ? 255 : B + 0;
	        		break;
				case 17:
					R = (R + 128 > 255) ? 255 : R + 128;
	        		G = (G + 64 > 255) ? 255 : G + 64;
	        		B = (R + 0 > 255) ? 255 : B + 0;
	        		break;
				case 18:
					R = (R + 0 > 255) ? 255 : R + 0;
	        		G = (G + 192 > 255) ? 255 : G + 192;
	        		B = (R + 0 > 255) ? 255 : B + 0;
	        		break;
				case 19:
					R = (R + 128 > 255) ? 255 : R + 128;
	        		G = (G + 192 > 255) ? 255 : G +192;
	        		B = (R + 0 > 255) ? 255 : B + 0;
	        		break;
				case 20:
					R = (R + 0 > 255) ? 255 : R + 0;
	        		G = (G + 64 > 255) ? 255 : G + 64;
	        		B = (R + 128 > 255) ? 255 : B + 128;
	        		break;
				}
				outBitMap.setPixel(x, y, Color.rgb(R, G, B));
        	}
        }
        __outBitmap = outBitMap.copy(Bitmap.Config.ARGB_8888, true);
		outBitMap.recycle();

		showLegend(existCategory);	// 凡例を表示する
	}

	private JSONObject getResponseData() {
		return res;
	}

	private void setResponseData(JSONObject json) {
		res = json;
	}

	/**
	 * findObjectメソッドで色を付けている裏でマスク画像を生成する
	 */
	private void createMaskImage(final JSONObject json) {

		AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				maskImage = bitmap.copy(Bitmap.Config.ARGB_8888, true);
				int width = maskImage.getWidth();
		        int height = maskImage.getHeight();
		        for (int y = 0; y < height; y++) {
		        	for (int x = 0; x < width; x++) {
		            	int labelData = -1;
						try {
							labelData = Integer.parseInt(json.getString(String.valueOf(y * width + x)));
						} catch (NumberFormatException e) {
						} catch (JSONException e) {
						} finally {
							if (labelData == -1) {
								return null;
							}
						}
						if (labelData == 0) {
							maskImage.setPixel(x, y, Color.rgb(255, 255, 255));// 背景を白
						} else {
							maskImage.setPixel(x, y, Color.rgb(0, 0, 0));
						}
		        	}
		        }
				return null; // Void型なのでnull値を返しておく
			}

			@Override
			protected void onPostExecute(Void result) {
			}
		};
		task.execute();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.preview, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_save) {
			// いま見ている画像を保存する
			saveImage();
		} else if (id == R.id.action_open_album) {
			// アルバムアプリを開く(画像が保存できたか見れるだけ)
			Intent intent = new Intent();
			intent.setType("image/*");
			intent.setAction(Intent.ACTION_PICK);
			startActivityForResult(intent, REQUEST_CHECK_ALBUM);
		} else if (id == R.id.action_src_select) {
			// 背景を選択...が押されたらアルバムアプリを開く
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
		} else if (id == R.id.action_src_take) {
			// 背景を撮影...が押されたらカメラを起動
			showCameraForSource();
		} else if (id == R.id.action_show_maskimage) {
			zoomImageView.setImageBitmap(maskImage);
		} else if (id == R.id.action_show_outputimage) {
			setOnDisplay(1);
			zoomImageView.setImageBitmap(__outBitmap);
		} else if (id == R.id.action_blending) {
			if (source == null) {
				Toast.makeText(this, "背景を選択してください", Toast.LENGTH_LONG).show();
				return false;
			} else {
				exeBlending();
				setOnDisplay(2);
			}
		} else if (id == R.id.action_settings) {
			Intent intent = new Intent(PreviewActivity.this, PreferenceSample.class);
			startActivityForResult(intent, REQUEST_SETTINGS);
		}

		return true;
	}

	public Point getDisplaySize() {
		Display display = getWindowManager().getDefaultDisplay();
		Point size = new Point();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
		    display.getSize(size);
		} else {
		    size.x = display.getWidth();
		    size.y = display.getHeight();
		}
		return size;
	}

	/**
	 * 画面上部に処理前と処理後の画像を表示する
	 * @param is_processed
	 * セグメンテーション前か後か
	 */
	public void setImageView(boolean is_processed, Bitmap bm)
	{
		ImageView iv = (ImageView)findViewById(R.id.imageView1);

	    //画面サイズを取得する
		LinearLayout.LayoutParams lp;
		float factor = 0;
		Point size = getDisplaySize();
		// 処理前を表示するのか処理後を表示するのかで分ける
		iv.setImageBitmap(bm);
		if (is_processed) {
			//画面の幅(Pixel)/画像ファイルの幅(Pixel)=画面いっぱいに表示する場合の倍率
			if (bm.getWidth() > 450) {
				factor =  size.x / bm.getWidth();
			} else {//450px以下なら1.2倍して大きめに
				factor =  size.x / bm.getWidth() * (float)1.2;
			}
			//表示サイズ(Pixel)を指定して、LayoutParamsを生成(ImageViewはこのサイズになる)
			lp = new LinearLayout.LayoutParams(
					(int)(bm.getWidth()*factor), (int)(bm.getHeight()*factor));
		} else {
			factor =  size.x / bm.getWidth();
			lp = new LinearLayout.LayoutParams(
					(int)(bm.getWidth()*factor), (int)(bm.getHeight()*factor));
		}

	    //中央に表示する
		lp.gravity = Gravity.CENTER;

	    //LayoutParamsをImageViewに設定
		iv.setLayoutParams(lp);

		//ImageViewのMatrixに拡大率を指定
		Matrix m = iv.getImageMatrix();
		m.reset();
		m.postScale(factor, factor);
		iv.setImageMatrix(m);
	}

	/**
	 * 背景画像が選択された後の処理
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == REQUEST_GALLERY) {
			if (resultCode != RESULT_OK) {
				return;		//画像が選択されなかった
			}
			Uri sourceImageUri = data.getData();	// 背景画像のURI
			if (sourceImageUri == null) {
				return;// 取得失敗
			}
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inMutable = true;
			InputStream inputStream;
			try {
				inputStream = getContentResolver().openInputStream(sourceImageUri);
			} catch (FileNotFoundException e1) {
				e1.printStackTrace();
				return;
			}
			Bitmap tmp_src = BitmapFactory.decodeStream(inputStream, null, options);
			source = Bitmap.createScaledBitmap(tmp_src, bitmap.getWidth(), bitmap.getHeight(), true);
			resultImage = source.copy(Bitmap.Config.ARGB_8888, true);
			zoomImageView.setImageBitmap(source);
			tmp_src.recycle();
		} else if (requestCode == REQUEST_CHECK_ALBUM) {
			// 画像が保存されたかどうか見るだけなので何もしない
		} else if (requestCode == REQUEST_CAMERA_SOURCE) {
			if (resultCode != RESULT_OK) {
				return;		//画像が選択されなかった
			}
			Uri resultUri = (data != null ? data.getData() : capturedUri);
			if (resultUri == null) {
				return;		// 取得失敗
			}
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inMutable = true;
			InputStream inputStream;
			try {
				inputStream = getContentResolver().openInputStream(capturedUri);
			} catch (FileNotFoundException e1) {
				e1.printStackTrace();
				return;
			}
			Bitmap tmp_src = BitmapFactory.decodeStream(inputStream, null, options);
			source = Bitmap.createScaledBitmap(tmp_src, bitmap.getWidth(), bitmap.getHeight(), true);
			resultImage = source.copy(Bitmap.Config.ARGB_8888, true);
			zoomImageView.setImageBitmap(source);
			tmp_src.recycle();
			return;
		} else if (requestCode == REQUEST_SETTINGS) {
			setPreferenceValues();
		}
	}

	/**
	 * 設定で選択されたイテレーション回数をセット
	 */
	private void setPreferenceValues() {

		SharedPreferences spf = PreferenceManager.getDefaultSharedPreferences(this);
		int listValue = Integer.parseInt(spf.getString("list_preference", ""));
		if (listValue == -1) {
			listValue = 100;
		}
		iteration = listValue;
	}

	public void exeBlending() {
		final ImageBlendingTask task = new ImageBlendingTask(PreviewActivity.this, zoomImageView, getDisplaySize());
		task.setIterationLoop(iteration);
		task.execute(bitmap, source, maskImage, resultImage);
	}

	private void saveImage() {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		String prefix = null;
		byte[] saveTarget = null;
		int onDisplay = getOnDisplay();		// いまどの画像を表示しているか
		switch (onDisplay) {
		case 1:
			prefix = "segment";
			__outBitmap.compress(CompressFormat.JPEG, 100, bos);
			break;
		case 2:
			prefix = "blend";
			source.compress(CompressFormat.JPEG, 100, bos);
			break;
		default:
			Toast.makeText(this, "表示画像に問題が発生しました...", Toast.LENGTH_LONG).show();
			return;
		}
		try {
			bos.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		saveTarget = bos.toByteArray();
		final Calendar calendar = Calendar.getInstance();
		final String hour = String.valueOf(calendar.get(Calendar.HOUR_OF_DAY));
		final String minute = String.valueOf(calendar.get(Calendar.MINUTE));
		final String second = String.valueOf(calendar.get(Calendar.SECOND));
		final String imagePath = Environment.getExternalStorageDirectory().getPath() + "/DCIM/ImageEditor/" + prefix + "_" + hour + "_" + minute + "_" + second + ".jpg";
		try {
			FileOutputStream foStream = new FileOutputStream(imagePath);
			foStream.write(saveTarget);
			foStream.close();
			bos.close();
			Toast.makeText(this, "画像を保存しました。", Toast.LENGTH_LONG).show();
			// ギャラリーへスキャンを促す
			MediaScannerConnection.scanFile(
					this,
					new String[]{Uri.parse(imagePath).getPath()},
					new String[]{"image/jpeg"},
					null
			);
		} catch(Error e) {
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 画像内にあったカテゴリーの凡例を表示する
	 * @param exist: button1からbutton20まで、画像内にあるか
	 */
	void showLegend(boolean[] existCategory) {
		ArrayList<Button> list = getButtonList();
		for (int i = 0; i < CATEGORY_NUM; i++) {
			Button btn = list.get(i);
			if (existCategory[i]) {
				// 画像内にあるカテゴリーだけ表示する
				btn.setVisibility(View.VISIBLE);
				paint[i] = true;
			} else {
				// ないのに表示されていたらそのボタンは表示しない
				if (btn.getVisibility() == View.VISIBLE) {
	                btn.setVisibility(View.GONE);
	            } else {
	                //btn1.setVisibility(View.INVISIBLE);
	            }
			}
		}
	}

	/**
	 * xmlでvisiblity書くと怒られるので、最初は全部隠しておく
	 */
	void hideLegend() {
		ArrayList<Button> list = getButtonList();
		for (int i = 0; i < CATEGORY_NUM; i++) {
			Button btn = list.get(i);
			btn.setVisibility(View.GONE);
			// ついでにsetOnClickListenerもしとく
			btn.setOnClickListener(this);
		}
	}


	// 凡例ボタンタップ時の処理
	public void onClick(View view) {
		// 押されたボタンが見つかったらその場所の塗ったかどうかフラグを反転
		ArrayList<Button> list = getButtonList();
		for (int i = 0; i < CATEGORY_NUM; i++) {
			if (view.getId() == existButtonID[i]) {
				paint[i] = !paint[i];
				Button btn = list.get(i);
				// ボタンの透明度を変える
				float alpha = (paint[i] == true) ? 1.0f : 0.5f;
				btn.setAlpha(alpha);
				break;
			}
		}
		repaint();	// 再描画
	}

	/**
	 * 凡例を出すボタンのリストを返す
	 */
	ArrayList<Button> getButtonList() {
		ArrayList<Button> list = new ArrayList<Button>();
		list.add((Button)findViewById(R.id.Button1));
		list.add((Button)findViewById(R.id.Button2));
		list.add((Button)findViewById(R.id.Button3));
		list.add((Button)findViewById(R.id.Button4));
		list.add((Button)findViewById(R.id.Button5));
		list.add((Button)findViewById(R.id.Button6));
		list.add((Button)findViewById(R.id.Button7));
		list.add((Button)findViewById(R.id.Button8));
		list.add((Button)findViewById(R.id.Button9));
		list.add((Button)findViewById(R.id.Button10));
		list.add((Button)findViewById(R.id.Button11));
		list.add((Button)findViewById(R.id.Button12));
		list.add((Button)findViewById(R.id.Button13));
		list.add((Button)findViewById(R.id.Button14));
		list.add((Button)findViewById(R.id.Button15));
		list.add((Button)findViewById(R.id.Button16));
		list.add((Button)findViewById(R.id.Button17));
		list.add((Button)findViewById(R.id.Button18));
		list.add((Button)findViewById(R.id.Button19));
		list.add((Button)findViewById(R.id.Button20));
		return list;
	}


	void setButtonID() {
		existButtonID[0] = R.id.Button1;
		existButtonID[1] = R.id.Button2;
		existButtonID[2] = R.id.Button3;
		existButtonID[3] = R.id.Button4;
		existButtonID[4] = R.id.Button5;
		existButtonID[5] = R.id.Button6;
		existButtonID[6] = R.id.Button7;
		existButtonID[7] = R.id.Button8;
		existButtonID[8] = R.id.Button9;
		existButtonID[9] = R.id.Button10;
		existButtonID[10] = R.id.Button11;
		existButtonID[11] = R.id.Button12;
		existButtonID[12] = R.id.Button13;
		existButtonID[13] = R.id.Button14;
		existButtonID[14] = R.id.Button15;
		existButtonID[15] = R.id.Button16;
		existButtonID[16] = R.id.Button17;
		existButtonID[17] = R.id.Button18;
		existButtonID[18] = R.id.Button19;
		existButtonID[19] = R.id.Button20;
	}

	private int getOnDisplay() {
		return onDisplay;
	}

	private void setOnDisplay(int value) {
		onDisplay = value;
	}

	/*
	 * カメラを起動する
	 */
	private void showCameraForSource() {
		// カメラの起動Intentの用意
		File pathExternalPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
		String photoName = System.currentTimeMillis() + ".jpg";
		File capturedFile = new File(pathExternalPublicDir, photoName);
		capturedUri = Uri.fromFile(capturedFile);
		Intent intentCamera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		intentCamera.putExtra(MediaStore.EXTRA_OUTPUT, capturedUri);
		startActivityForResult(intentCamera, REQUEST_CAMERA_SOURCE);
	}

	private void showDialog() {
		// インスタンス作成
        waitDialog = new ProgressDialog(this);
        // タイトル設定
        waitDialog.setTitle("処理中");
        // メッセージ設定
        waitDialog.setMessage("Please wait...");
        // スタイル設定 スピナー
        waitDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        // キャンセル可能か(バックキーでキャンセル）
        waitDialog.setCancelable(true);
        // ダイアログ表示
        waitDialog.show();
	}

	/**
	 * 塗ったかどうかのフラグを使ってセグメンテーション画像とマスクを再描画する
	 */
	private void repaint() {
        int width  = bitmap.getWidth();
        int height = bitmap.getHeight();

        for (int y = 0; y < height; y++) {
        	for (int x = 0; x < width; x++) {
        		int labelData = -1;
            	try {
					labelData = Integer.parseInt(res.getString(String.valueOf(y * width + x)));
				} catch (NumberFormatException e) {
					e.printStackTrace();
				} catch (JSONException e) {
				}

				if (labelData == 0) {
					maskImage.setPixel(x, y, Color.rgb(255, 255, 255));
					continue;	// 以降の処理は飛ばす
				} else if (paint[labelData-1] == false) {
					maskImage.setPixel(x, y, Color.rgb(255, 255, 255));		// 塗らないとしたところも背景
				} else {
					maskImage.setPixel(x, y, Color.rgb(0, 0, 0));
				}

            	int origColor = bitmap.getPixel(x, y);
        		int origR = Color.red(origColor);
        		int origG = Color.green(origColor);
        		int origB = Color.blue(origColor);
        		int R = origR;
        		int G = origG;
        		int B = origB;

				if (paint[labelData-1] == true) {
					switch (labelData) {
					case 1:
						R = (origR + 128 > 255) ? 255 : origR + 128;
		        		G = (origG + 0 > 255) ? 255 : origG + 0;
		        		B = (origR + 0 > 255) ? 255 : origB + 0;
		        		break;
					case 2:
						R = (origR + 0 > 255) ? 255 :origR + 0;
		        		G = (origG + 128 > 255) ? 255 : origG + 128;
		        		B = (origR + 0 > 255) ? 255 : origB + 0;
		        		break;
					case 3:
						R = (origR + 128 > 255) ? 255 : origR + 128;
		        		G = (origG + 128 > 255) ? 255 : origG + 128;
		        		B = (origR + 0 > 255) ? 255 : origB + 0;
		        		break;
					case 4:
						R = (origR + 0 > 255) ? 255 : origR + 0;
		        		G = (origG + 0 > 255) ? 255 : origG + 0;
		        		B = (origR + 128 > 255) ? 255 : origB + 129;
		        		break;
					case 5:
						R = (origR + 128 > 255) ? 255 : origR + 128;
		        		G = (origG + 0 > 255) ? 255 : origG + 0;
		        		B = (origR + 128 > 255) ? 255 :origB + 128;
		        		break;
					case 6:
						R = (origR + 0 > 255) ? 255 : origR + 0;
		        		G = (origG + 128 > 255) ? 255 : origG + 128;
		        		B = (origR + 128 > 255) ? 255 : origB + 128;
		        		break;
					case 7:
						R = (origR + 128 > 255) ? 255 : origR + 128;
		        		G = (origG + 128 > 255) ? 255 : origG + 128;
		        		B = (origR + 128 > 255) ? 255 : origB + 128;
		        		break;
					case 8:
						R = (origR + 64 > 255) ? 255 : origR + 64;
		        		G = (origG + 0 > 255) ? 255 : origG + 0;
		        		B = (origR + 0 > 255) ? 255 : origB + 0;
		        		break;
					case 9:
						R = (origR + 192 > 255) ? 255 : origR + 192;
		        		G = (origG + 0 > 255) ? 255 : origG + 0;
		        		B = (origB + 0 > 255) ? 255 : origB + 0;
		        		break;
					case 10:
						R = (origR + 64 > 255) ? 255 : origR + 64;
		        		G = (origG + 128 > 255) ? 255 : origG + 128;
		        		B = (origR + 0 > 255) ? 255 : origB + 0;
		        		break;
					case 11:
						R = (origR + 192 > 255) ? 255 : origR + 192;
		        		G = (origG + 128 > 255) ? 255 : origG + 128;
		        		B = (origR + 0 > 255) ? 255 : origB + 0;
		        		break;
					case 12:
						R = (origR + 64 > 255) ? 255 : origR + 64;
		        		G = (origG + 0 > 255) ? 255 : origG + 0;
		        		B = (origR + 128 > 255) ? 255 : origB + 128;
		        		break;
					case 13:
						R = (origR + 192 > 255) ? 255 : origR + 192;
		        		G = (origG + 0 > 255) ? 255 : origG + 0;
		        		B = (origR + 128 > 255) ? 255 : origB + 128;
		        		break;
					case 14:
						R = (origR + 64 > 255) ? 255 : origR + 64;
		        		G = (origG + 128 > 255) ? 255 : origG + 128;
		        		B = (origR + 128 > 255) ? 255 : origB + 128;
		        		break;
					case 15:
						R = (origR + 192 > 255) ? 255 : origR + 192;
		        		G = (origG + 128 > 255) ? 255 : origG + 128;
		        		B = (origR + 128 > 255) ? 255 : origB + 128;
		        		break;
					case 16:
						R = (origR + 0 > 255) ? 255 : origR + 0;
		        		G = (origG + 64 > 255) ? 255 : origG + 64;
		        		B = (origR + 0 > 255) ? 255 : origB + 0;
		        		break;
					case 17:
						R = (origR + 128 > 255) ? 255 : origR + 128;
		        		G = (origG + 64 > 255) ? 255 : origG + 64;
		        		B = (origR + 0 > 255) ? 255 : origB + 0;
		        		break;
					case 18:
						R = (origR + 0 > 255) ? 255 : origR + 0;
		        		G = (origG + 192 > 255) ? 255 : origG + 192;
		        		B = (origR + 0 > 255) ? 255 : origB + 0;
		        		break;
					case 19:
						R = (origR + 128 > 255) ? 255 : origR + 128;
		        		G = (origG + 192 > 255) ? 255 : origG +192;
		        		B = (origR + 0 > 255) ? 255 : origB + 0;
		        		break;
					case 20:
						R = (origR + 0 > 255) ? 255 : origR + 0;
		        		G = (origG + 64 > 255) ? 255 : origG + 64;
		        		B = (origR + 128 > 255) ? 255 : origB + 128;
		        		break;
					default:
						break;
					}
				}
				__outBitmap.setPixel(x, y, Color.rgb(R, G, B));
        	}
        }
	}
}
