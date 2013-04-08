/* MediaUtil LLJTran - $RCSfile: JPEG.java,v $
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
 *	$Id: JPEG.java,v 1.7 2008/03/02 04:42:16 drogatkin Exp $
 *
 * Some ideas and algorithms were borrowed from:
 * Thomas G. Lane, and James R. Weeks
 */
package android.mediautil.image.jpeg;

import java.io.InputStream;
import android.mediautil.generic.FileFormatException;
import android.mediautil.generic.Log;

public class JPEG extends AbstractImageInfo<LLJTran> {
	int width, height, precision;

	byte[] header = new byte[2];

	public JPEG(int width, int height, int precision, LLJTran format)
			throws FileFormatException {
		this.width = width;
		this.height = height;
		this.precision = precision;
		this.format = format;
	}

	public JPEG(InputStream is, byte[] data, int offset, int width, int height,
			LLJTran format) throws FileFormatException {
		super(is, data, offset, format);
		// if (format == null)
		// new Exception("CALLED WITH NULL").printStackTrace();
		if (this.width <= 0 && width > 0) {
			this.width = width;
			this.height = height;
		} else {
			int len;
			if (data.length == 2) { // too bad, marker can be unread
				len = readMarker(is, true);
				if (len <= 0)
					return;
				if (data[1] >= M_SOF0 && data[1] <= M_SOF15 && data[1] != M_DHT
						&& data[1] != M_JPG) {
					precision = (this.data[0] & 255) * (this.data[5] & 255);
					this.width = bs2i(3, 2);
					this.height = bs2i(1, 2);
					return;
				}
			}
			do {
				len = readMarker(is, false);
				if (len <= 0)
					break;
				if (header[1] >= M_SOF0 && header[1] <= M_SOF15
						&& header[1] != M_DHT && header[1] != M_JPG) {
					precision = (this.data[0] & 255) * (this.data[5] & 255);
					this.width = bs2i(3, 2);
					this.height = bs2i(1, 2);
					break;
				}
			} while (true);
		}
	}

	int readMarker(InputStream is, boolean bodyOnly) {
		try {
			if (bodyOnly == false)
				if (is.read(header) < header.length)
					return -1;
			data = new byte[2];
			if (is.read(data) < data.length)
				return -1;
			int len = bs2i(0, 2) - 2;
			data = new byte[len];
			return read(is, data) + header.length + 2;
		} catch (Exception e) {
			if (Log.debugLevel >= Log.LEVEL_ERROR)
				e.printStackTrace();
			return -1;
		}
	}

	@Override
	public void readInfo() throws FileFormatException {
		data = null; // for gc
	}
}
