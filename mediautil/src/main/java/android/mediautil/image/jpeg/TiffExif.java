/* MediaUtil LLJTran - $RCSfile: TiffExif.java,v $
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
 *	$Id: TiffExif.java,v 1.4 2006/12/29 03:24:21 drogatkin Exp $
 *
 * Some ideas and algorithms were borrowed from:
 * Thomas G. Lane, and James R. Weeks
 */
package android.mediautil.image.jpeg;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import android.mediautil.generic.FileFormatException;
import android.mediautil.generic.Rational;

public class TiffExif extends Exif {
	final static int FAKE_JPEG_OFFSET = FIRST_IFD_OFF + 1;

	public TiffExif(InputStream is, byte[] data, int offset, boolean intel,
			LLJTran format) throws FileFormatException {
		this.intel = intel;
		motorola = !intel;
		this.is = is;
		this.data = data;
		this.offset = offset;
		this.format = format;
		readInfo();
	}

	@Override
	public void readInfo() {
		ifds = new IFD[2];
		processAllIFDs();
		// offset and size of thumbnails for Nikon images
		if (getTagValue(JPEGINTERCHANGEFORMATLENGTH, 0, false) == null) {
			Entry e = getTagValue(STRIPOFFSETS, true);
			if (e != null) {
				setTagValue(
						JPEGINTERCHANGEFORMATLENGTH,
						0,
						new Entry(LONG,
								new Object[] { Integer.valueOf(((Integer) e
										.getValue(0))
										- getThumbnailOffset()
										+ DIR_ENTRY_SIZE) }), false); // Exif
																		// add
																		// this
																		// value
																		// to
																		// offset
			}
		}
		if (getTagValue(JPEGINTERCHANGEFORMAT, 0, false) == null)
			setTagValue(JPEGINTERCHANGEFORMAT, 0, new Entry(LONG,
					new Object[] { Integer.valueOf(offset - FIRST_IFD_OFF) }),
					false); // Exif add this value to offset
		offset = 0;
	}

	public void writeInfo(OutputStream out, int op, String encoding)
			throws IOException {
		throw new RuntimeException("writeInfo not supported for TIFF EXIF");
	}

	@Override
	protected int firstIFD() {
		return s2n(2, 4) - offset + FAKE_JPEG_OFFSET;
	}

	@Override
	protected int nextIFD(int ifd) {
		return s2n(data.length - 4, 4) - offset + FAKE_JPEG_OFFSET;
	}

	@Override
	public void storeIFD(int ifdoffset, IFD ifd_p) {
		int entries = 0;
		byte[] ifd, value_data;
		try {
			skip(is, ifdoffset - FAKE_JPEG_OFFSET);
			offset += ifdoffset - FAKE_JPEG_OFFSET;
			data = new byte[2];
			offset += is.read(data);
			entries = s2n(0, 2);
			ifd = new byte[DIR_ENTRY_SIZE * entries + 4];
			read(is, ifd);
			offset += ifd.length;
			int entry, data_off, count, typelen, type, tag;
			data = ifd;
			value_data = new byte[getExifSize(0, entries) - offset];
			read(is, value_data);
			offset += value_data.length;
			data = ifd;
			for (int i = 0; i < entries; i++) {
				entry = DIR_ENTRY_SIZE * i;
				tag = s2n(entry, 2);
				type = s2n(entry + 2, 2);
				if (type < 1 || type > 10)
					continue; // not handled
				typelen = TYPELENGTH[type - 1];
				count = s2n(entry + 4, 4);
				data_off = entry + 8;
				// System.err.println("Data off 0x"+Integer.toHexString(s2n(data_off,
				// 4))+
				// " count 0x"+Integer.toHexString(count)+" type "+type+" len "+typelen);
				if (count * typelen > 4) {
					data_off = s2n(data_off, 4) - (offset - value_data.length);
					data = value_data;
				}
				if (type == ASCII) {
					try {
						ifd_p.addEntry(tag, new Entry(type, new String(data,
								data_off, count - 1, "US-ASCII")));
					} catch (UnsupportedEncodingException e) {
					}
				} else {
					Object[] values = new Object[count];
					boolean signed = (type >= SSHORT);
					for (int j = 0; j < count; j++) {
						if (type % RATIONAL != 0) // Not a fraction
							values[j] = s2n(data_off, typelen, signed);
						else
							// The type is either 5 or 10
							values[j] = new Rational(s2n(data_off, 4, signed),
									s2n(data_off + 4, 4, signed));
						data_off += typelen;
						if (tag == EXIFOFFSET && j == 0
								&& ((Integer) values[0]).intValue() > 0) {
							IFD iifd;
							storeIFD(((Integer) values[0]).intValue() - offset
									+ FAKE_JPEG_OFFSET, iifd = new IFD(tag,
									type));
							ifd_p.addIFD(iifd);
						} else
							ifd_p.addEntry(tag, new Entry(type, values));
					}
				}
				data = ifd;
			}
		} catch (IOException e) {
		}
	}

	protected int getExifSize(int e_o, int entries) {
		// Prognosing of the maximum of space occupied by Exif
		int data_off = 0, count = 0, entry;
		for (int i = 0; i < entries; i++) {
			entry = DIR_ENTRY_SIZE * i + e_o;
			if (TYPELENGTH[s2n(entry + 2, 2) - 1] * s2n(entry + 4, 4) > 4
					&& data_off < s2n(entry + 8, 4)) {
				data_off = s2n(entry + 8, 4);
				count = TYPELENGTH[s2n(entry + 2, 2) - 1] * s2n(entry + 4, 4);
			}
		}
		return data_off + count;
	}
}
