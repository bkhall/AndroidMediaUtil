/* MediaUtil LLJTran - $RCSfile: JFXX.java,v $
 * Copyright (C) 1999-2005 Dmitriy Rogatkin, Suresh Mahalingam.  All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *  THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 *  ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE FOR
 *  ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *	$Id: JFXX.java,v 1.4 2007/12/15 01:44:24 drogatkin Exp $
 *
 * Some ideas and algorithms were borrowed from:
 * Thomas G. Lane, and James R. Weeks
 */
package android.mediautil.image.jpeg;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Hashtable;
import java.util.StringTokenizer;

import android.mediautil.generic.FileFormatException;
import android.mediautil.generic.Log;

public class JFXX extends AbstractImageInfo<LLJTran> {
	final static String FORMAT = "JFXX";

	static final String PICTURE_INFO = "picture info";

	static final String CAMERA_INFO = "camera info";

	static final String DIAG_INFO = "diag info";

	static final String USER = "user";

	static final String END = "end";

	static final String FILE_INFO = "file info";

	// [picture info]
	static final String TIMEDATE = "TimeDate";

	static final String SHUTTER = "Shutter";

	static final String FNUMBER = "Fnumber";

	static final String CFNUMBER = "FNumber";

	static final String ZOOM = "Zoom";

	static final String RESOLUTION = "Resolution";

	static final String IMAGESIZE = "ImageSize";

	static final String FLASH = "Flash";

	// [camera info]
	static final String ID = "ID";

	static final String TYPE = "Type";

	public JFXX(InputStream is, byte[] data, int offset, LLJTran format)
			throws FileFormatException {
		super(is, data, offset, format);
		// an unusual problem is here
		// no own variables are initialized here
		// but super's constructor calls our method read, which is using
		// uninitialized local variables, so they are moved to parent
	}

	public static byte[] getMarkerData() {
		return new byte[] { (byte) 0xff, (byte) 0xe0, (byte) 0x00, (byte) 0x10,
				(byte) 0x4a, (byte) 0x46, (byte) 0x49, (byte) 0x46,
				(byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00,
				(byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00 };
	}

	@Override
	public void readInfo() {
		try {
			readAPP0X();
			readAPP12();
		} catch (NullPointerException e) {
			if (Log.debugLevel >= Log.LEVEL_ERROR)
				e.printStackTrace();
		} catch (IOException e) {
			if (Log.debugLevel >= Log.LEVEL_ERROR)
				e.printStackTrace();
		}
		data = null; // for gc
	}

	void readAPP0X() {
		// x'FF', APP0, length, extension_code, extension_data
		switch (data[5]) {
		case 0x10:
			image = new byte[data.length - 6];
			System.arraycopy(data, 6, image, 0, image.length);
		case 0x0F:
		case 0x11:
		case 0x13:
		}
	}

	void readAPP12() throws IOException {
		data = new byte[4];
		is.read(data);
		if (data[0] == M_PRX && data[1] == M_APP12) {
			int len = bs2i(2, 2) - 2;
			data = new byte[len];
			BasicJpegIo.read(is, data);
			ParserAPP12 parser = new ParserAPP12(0);
			parser.next(); // skip make
			parser.next(); // skip size
			pictureinfo = new Hashtable<String, String>();
			camerainfo = new Hashtable<String, String>();
			diaginfo = new Hashtable<String, String>();
			fileinfo = new Hashtable<String, String>();
			Hashtable<String, String> currentinfo = null;
			String el;
			while (parser.hasMore()) {
				el = parser.next();
				if (el.startsWith("[")) {
					if (el.indexOf(PICTURE_INFO) == 1)
						currentinfo = pictureinfo;
					else if (el.indexOf(CAMERA_INFO) == 1)
						currentinfo = camerainfo;
					else if (el.indexOf(DIAG_INFO) == 1)
						currentinfo = diaginfo;
					else if (el.indexOf(FILE_INFO) == 1)
						currentinfo = fileinfo;
					else
						currentinfo = null;
				} else {
					if (currentinfo == null)
						continue;
					StringTokenizer st = new StringTokenizer(el, "=");
					if (st.hasMoreTokens()) {
						String key = st.nextToken();
						if (st.hasMoreTokens()) {
							currentinfo.put(key, st.nextToken());
						}
					}
				}
			}
		}
	}

	class ParserAPP12 {
		int curpos;

		ParserAPP12(int offset) {
			curpos = offset;
		}

		boolean hasMore() {
			return curpos < data.length - 1;
		}

		String next() {
			int startpos = curpos;
			while (curpos < data.length && data[curpos] != 0
					&& data[curpos] != 0x0A && data[curpos] != 0x0D)
				curpos++;
			String result = null;
			try {
				result = new String(data, startpos, curpos - startpos,
						"Default");
			} catch (UnsupportedEncodingException e) {

			}
			// skip unused
			while (curpos < data.length
					&& (data[curpos] == 0 || data[curpos] == 0x0A || data[curpos] == 0x0D))
				curpos++;
			return result;
		}
	}

	// JFIF specific
	public Hashtable<String, String> getPictureInfo() {
		return pictureinfo;
	}

	public Hashtable<String, String> getCameraInfo() {
		return camerainfo;
	}

	public Hashtable<String, String> getDiagInfo() {
		return diaginfo;
	}

	public Hashtable<String, String> getFileInfo() {
		return fileinfo;
	}

	private byte[] image;

	private Hashtable<String, String> pictureinfo, camerainfo, diaginfo,
			fileinfo;
}
