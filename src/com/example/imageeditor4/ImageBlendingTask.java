package com.example.imageeditor4;

import java.lang.ref.WeakReference;

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Point;
import android.os.AsyncTask;
import android.util.Log;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class ImageBlendingTask extends AsyncTask<Bitmap, Integer, Bitmap> {

	public final WeakReference<ZoomImageView> mImageViewReference;
	private ProgressDialog mDialog = null;
	private Context mContext = null;
    // onPreExecute ～ onPostExecute までの判別フラグ
    private boolean isInProgress = false;
    Point size = null;
    private int iteration = 100;

	static {
		System.loadLibrary("ImageEditor4");
	}
	public native void poissonBlending(Bitmap source, Bitmap destination, Bitmap mask, Bitmap newImage, int iteration);

	public ImageBlendingTask(Context context, ZoomImageView i, Point p) {
		mContext = context;
		mImageViewReference = new WeakReference<ZoomImageView>(i);
		size = p;
		Log.d("size", "x: "+size.x+"y: "+size.y);
	}

	// Progress Dialog を表示
    public void showDialog() {
        mDialog = new ProgressDialog(mContext);
        mDialog.setTitle("処理中");
        mDialog.setMessage("Please wait...");
        mDialog.setCancelable(true);
        mDialog.setIndeterminate(false);
        // ダイアログの外部をタッチしてもダイアログを閉じない
        mDialog.setCanceledOnTouchOutside(false);
        //mDialog.setProgressNumberFormat("%1$s / %2$s bytes");
        mDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        //mDialog.setMax(0);
        //mDialog.setProgress(0);
        mDialog.show();
    }

    public void setIterationLoop(int loop) {
    	iteration = loop;
    }

	@Override
    protected void onPreExecute() {
		isInProgress = true;
		showDialog();
    }

	@Override
	protected Bitmap doInBackground(Bitmap... params) {
		Log.d("log", "doInBackground");
		poissonBlending(params[0], params[1], params[2], params[3], iteration);
		Log.d("log", "finish");
		Log.d("log", "w:"+params[1].getWidth()+", h: "+params[1].getHeight());
		return params[1];
	}

	@Override
    protected void onPostExecute(Bitmap result) {
		// ProgressDialog の削除
        if (mDialog !=  null) {
            dismissDialog();
        }
        // AsyncTaskの終了
        isInProgress = false;
		if (result.isRecycled()) {
			Log.d("Bitmap", "recycled!");
		}
		if (result != null && mImageViewReference != null) {
			final ZoomImageView imageView = mImageViewReference.get();
			if (imageView != null) {
				imageView.setImageBitmap(result);
			}
		}
		//result.recycle();
    }

	// プログレスダイアログを閉じる
    public void dismissDialog() {
        if (mDialog !=  null) {
           mDialog.dismiss();
        }
        mDialog = null;
    }

    public synchronized boolean isInProcess() {
		return isInProgress;
	}

	private void setImageView(Bitmap bm, ImageView iv) {
	    //画面サイズを取得する
		LinearLayout.LayoutParams lp;
		float factor = 0;
		iv.setImageBitmap(bm);

		//画面の幅(Pixel)/画像ファイルの幅(Pixel)=画面いっぱいに表示する場合の倍率
		if (bm.getWidth() > 450) {
			factor =  size.x / bm.getWidth();
		} else {//450px以下なら1.2倍して大きめに
			factor =  size.x / bm.getWidth() * (float)1.2;
		}
		//表示サイズ(Pixel)を指定して、LayoutParamsを生成(ImageViewはこのサイズになる)
		lp = new LinearLayout.LayoutParams(
				(int)(bm.getWidth()*factor), (int)(bm.getHeight()*factor));
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
}
