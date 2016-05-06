package com.example.imageeditor4;

import org.json.JSONObject;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import android.app.Application;
import android.text.TextUtils;
/***
 * Applicationを継承したシングルトンクラス
 *
 */
public class AppController extends Application {
	public static final String TAG = AppController.class.getSimpleName();
	private RequestQueue mRequestQueue;
	private static AppController mInstance;
	private JSONObject json;

	@Override
	public void onCreate() {
		super.onCreate();
		mInstance = this;
		json = new JSONObject();
	}

	public void setServerResponse(JSONObject j) {
		json = j;
	}

	public JSONObject getServerResponse() {
		return json;
	}

	public static synchronized AppController getInstance() {
		return mInstance;
	}

	public RequestQueue getRequestQueue() {
		if (mRequestQueue == null) {
			mRequestQueue = Volley.newRequestQueue(getApplicationContext());
		}

		return mRequestQueue;
	}

	public <T> void addToRequestQueue(JsonObjectRequest jsonObjectRequest, String tag) {
		// set the default tag if tag is empty
		jsonObjectRequest.setTag(TextUtils.isEmpty(tag) ? TAG : tag);
		getRequestQueue().add(jsonObjectRequest);

	}

	public <T> void addToRequestQueue(Request<T> req) {
		req.setTag(TAG);
		getRequestQueue().add(req);
	}

	public void cancelPendingRequest(Object tag) {
		if (mRequestQueue != null) {
			mRequestQueue.cancelAll(tag);
		}
	}

}
