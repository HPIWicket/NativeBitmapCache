package de.thegerman.nativebitmapcache;

import java.util.HashSet;
import java.util.Set;

import android.graphics.Bitmap;

public class BitmapPool {
	private Set<Bitmap> mBitmaps = new HashSet<Bitmap>();

	public Bitmap getSuitableBitmap(int width, int height) {
		Bitmap bitmap = null;
		synchronized (mBitmaps) {
			for (Bitmap possibleBitmap : mBitmaps) {
				if (possibleBitmap.getWidth() == width && possibleBitmap.getHeight() == height) {
					bitmap = possibleBitmap;
					break;
				}
			}
			if (bitmap != null) {
				mBitmaps.remove(bitmap);
			}
		}
		return bitmap;
	}
	
	public void addAvailableBitmap(Bitmap bitmap) {
		synchronized (mBitmaps) {
			mBitmaps.add(bitmap);
		}
	}

	public int getSize() {
		return mBitmaps.size();
	}

}
