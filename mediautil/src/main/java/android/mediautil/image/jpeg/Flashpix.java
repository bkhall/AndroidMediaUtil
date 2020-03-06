/* MediaUtil LLJTran - $RCSfile: Flashpix.java,v $
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
 *	$Id: Flashpix.java,v 1.3 2005/09/30 21:23:18 drogatkin Exp $
 *
 * Some ideas and algorithms were borrowed from:
 * Thomas G. Lane, and James R. Weeks
 */
package android.mediautil.image.jpeg;

/*
 See Flashpix Format Specification Version 1.0.2
 from July 2, 1998, Copyright Digital Imaging Group, Inc.
 */

import java.io.IOException;
import java.io.InputStream;

import android.mediautil.generic.FileFormatException;
import android.mediautil.generic.Log;

public class Flashpix extends AbstractImageInfo<LLJTran> {
	private final static String FORMAT = "Flaspix";

	final static int HEADER_LENGTH = 512;

	final static byte[] SIGNATURE = { (byte) 0xd0, (byte) 0xcf, (byte) 0x11,
			(byte) 0xe0, (byte) 0xa1, (byte) 0xb1, (byte) 0x1a, (byte) 0xe1 };

	Header header;

	byte fat[];

	public Flashpix() {
	}

	public Flashpix(InputStream is, byte[] data, int offset, LLJTran format)
			throws FileFormatException {
		super(is, data, offset, format);
	}

	@Override
	public void readInfo() throws FileFormatException {
		try {
			data = new byte[HEADER_LENGTH - 8];
			read(is, data);
			offset += data.length;
			header = new Header();
			header.fill(data, -8);
			intel = header.intelByteOrder;
			if (Log.debugLevel >= Log.LEVEL_DEBUG)
				android.util.Log.d(FORMAT, "Header " + header.toString());
			// skip to first FAT sector
			skip(is, header.fat[0] * header.sectorSize);
			offset += header.fat[0] * header.sectorSize;
			data = new byte[header.sectorSize]; // think about do it only once
			for (int i = 0; i < header.sectsFat; i++)
				if (Log.debugLevel >= Log.LEVEL_DEBUG)
					android.util.Log.d(FORMAT, "Fat sector [" + i + "]="
							+ header.fat[i]);
			// read first FAT sector
			read(is, data);
			offset += data.length;
			if (Log.debugLevel >= Log.LEVEL_DEBUG)
				android.util.Log.d(
						FORMAT,
						"Sector "
								+ header.sectDirStart
								+ " marker "
								+ Integer.toHexString(s2n(
										header.sectDirStart * 4, 4)));
			fat = data;
			data = new byte[header.sectorSize];
			// read directory
			read(is, data);
			offset += data.length;
			DirectoryEntry root = new DirectoryEntry();
			root.fill(data, 0);
			if (Log.debugLevel >= Log.LEVEL_DEBUG)
				android.util.Log.d(FORMAT, "Root " + root.toString());
			// go to child
			if (root._child < 0)
				return;
			DirectoryEntry child = new DirectoryEntry();
			child.fill(data, root._child * DirectoryEntry.ENTRY_SIZE);
			// start build tree here
			if (Log.debugLevel >= Log.LEVEL_DEBUG)
				android.util.Log.d(FORMAT, "Child " + child.toString());
			DirectoryEntry sibchild = new DirectoryEntry();
			if (child._leftSib > 0) {
				sibchild.fill(data, child._leftSib * DirectoryEntry.ENTRY_SIZE);
				if (Log.debugLevel >= Log.LEVEL_DEBUG)
					android.util.Log.d(FORMAT,
							"Left sib " + sibchild.toString());
			}
		} catch (IOException e) {
			if (Log.debugLevel >= Log.LEVEL_ERROR)
				e.printStackTrace();
		}

	}

}

class CLSID {
	final static int CLSID_LENGTH = 16;

	byte[] id = new byte[CLSID_LENGTH];

	void fill(byte data[], int offset) {
		System.arraycopy(data, offset, id, 0,
				(CLSID_LENGTH + offset) < data.length ? CLSID_LENGTH
						: data.length - offset);
	}

	@Override
	public String toString() {
		StringBuffer result = new StringBuffer(CLSID_LENGTH * 2);
		for (int i = 0; i < id.length; i++)
			result.append(Integer.toHexString(id[i]));
		return result.toString();
	}
}

class Header extends BasicJpegIo {
	int minorVersion;

	int dllVersion;

	boolean intelByteOrder;

	int sectorSize;

	static final int SECTORSIZEOFF = 0x1e;

	int miniSectorSize;

	static final int MINISECTORSIZEOFF = 0x20;

	int sectsFat;

	static final int SECTSFATOFF = 0x2C;

	int sectDirStart;

	static final int SECTDIRSTARTOFF = 0x30;

	int miniSectorCutoff;

	static final int MINISECTORCUTOFFOFF = 0x38;

	int sectMiniFatStart;

	static final int SECTMINIFATSTARTOFF = 0x3C;

	int sectsMiniFat;

	static final int SECTSMINIFATOFF = 0x40;

	int sectDifStart;

	static final int SECTDIFSTARTOFF = 0x44;

	int sectsDif;

	static final int SECTSDIFOFF = 0x48;

	int[] fat;

	static final int FATOFF = 0x4C;

	static final int FIRST_FAT_SIZE = 109;

	void fill(byte data[], int offset) {
		this.data = data;
		intelByteOrder = intel = true; // (data[0x1c-8] & 255) == 0xff &&
										// (data[0x1c-8+1] & 255) == 0xfe
		sectorSize = 1 << s2n(SECTORSIZEOFF + offset, 2);
		miniSectorSize = 1 << s2n(MINISECTORSIZEOFF + offset, 2);
		sectsFat = s2n(SECTSFATOFF + offset, 4);
		sectDirStart = s2n(SECTDIRSTARTOFF + offset, 4);
		miniSectorCutoff = s2n(MINISECTORCUTOFFOFF + offset, 4);
		sectMiniFatStart = s2n(SECTMINIFATSTARTOFF + offset, 4);
		sectsMiniFat = s2n(SECTSMINIFATOFF + offset, 4);
		sectDifStart = s2n(SECTDIFSTARTOFF + offset, 4);
		sectsDif = s2n(SECTSDIFOFF + offset, 4);
		fat = new int[FIRST_FAT_SIZE];
		for (int i = 0; i < FIRST_FAT_SIZE; i++)
			fat[i] = s2n(FATOFF + offset + 4 * i, 4);
	}

	@Override
	public String toString() {
		String result = "Sector size " + sectorSize + ", mini sector size "
				+ miniSectorSize + ", sectors in FAT " + sectsFat
				+ ", directory sector " + sectDirStart
				+ ", max size of mini stream " + miniSectorCutoff
				+ ", mini FAT starts " + sectMiniFatStart
				+ ", sectors in mini FAT " + sectsMiniFat
				+ ", first DIF and numbers " + sectDifStart + ':' + sectsDif;
		return result;
	}
}

class DirectoryEntry extends BasicJpegIo {
	static final int ENTRY_SIZE = 128;

	static final int STGTY_INVALID = 0;

	static final int STGTY_STORAGE = 1;

	static final int STGTY_STREAM = 2;

	static final int STGTY_LOCKBYTES = 3;

	static final int STGTY_PROPERTY = 4;

	static final int STGTY_ROOT = 5;

	static final int DE_RED = 0;

	static final int DE_BLACK = 1;

	static final int AB_SIZE = 32;

	char[] _ab;

	int _cb;

	static final int CB_OFF = 0x40;

	byte _mse;

	static final int MSE_OFF = 0x42;

	byte _flags;

	static final int FLAGS_OFF = 0x43;

	int _leftSib;

	static final int LEFTSIB_OFF = 0x44;

	int _rightSib;

	static final int RIGHTSIB_OFF = 0x48;

	int _child;

	static final int CHILD_OFF = 0x4C;

	CLSID _id;

	static final int CLSID_OFF = 0x50;

	int _userFlags;

	static final int USERFLAGS_OFF = 0x60;

	long _timeCreate;

	static final int TIMECREATE_OFF = 0x64;

	long _timeModify;

	static final int TIMEMODIFY_OFF = 0x6b;

	int _sectStart;

	static final int SECTSTART_OFF = 0x74;

	int _size;

	static final int SIZE_OFF = 0x78;

	void fill(byte data[], int offset) {
		this.data = data;
		intel = true;
		_mse = (byte) (s2n(MSE_OFF + offset, 1) & 255);
		if (_mse >= types.length || _mse < 0)
			_mse = 0;
		_cb = s2n(CB_OFF + offset, 2) / 2;
		_ab = new char[_cb];
		for (int i = 0; i < _cb && i < AB_SIZE; i++)
			_ab[i] = (char) /* s2n */bs2i(offset + 2 * i
					+ (_mse == STGTY_ROOT ? 0 : 1), 2);
		_flags = (byte) (s2n(FLAGS_OFF + offset, 1) & 255);
		_leftSib = s2n(LEFTSIB_OFF + offset, 4);
		_rightSib = s2n(RIGHTSIB_OFF + offset, 4);
		_child = s2n(CHILD_OFF + offset, 4);
		_id = new CLSID();
		_id.fill(data, CLSID_OFF + offset);
		_userFlags = s2n(USERFLAGS_OFF + offset, 4);
		_timeCreate = s2n(TIMECREATE_OFF + offset, 8);
		_timeModify = s2n(TIMEMODIFY_OFF + offset, 8);
		_sectStart = s2n(SECTSTART_OFF + offset, 4);
		_size = s2n(SIZE_OFF + offset, 4);
	}

	static final String types[] = { "INVALID", "STORAGE", "STREAM",
			"LOCKBYTES", "PROPERTY", "ROOT" };

	@Override
	public String toString() {
		return "Directory " + types[_mse] + " entry " + new String(_ab, 0, _cb)
				+ ", mse " + _mse + ", child " + _child + ", left sib "
				+ _leftSib + ", right sib " + _rightSib;
	}
}
