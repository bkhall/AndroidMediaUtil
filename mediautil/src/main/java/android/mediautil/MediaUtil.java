package android.mediautil;

import android.content.Context;

public class MediaUtil {

	private static Context mContext;

	public static void initialize(Context context) {
		if (mContext == null)
			mContext = context.getApplicationContext();
	}

	public static Context getContext() {
		return mContext;
	}
}
