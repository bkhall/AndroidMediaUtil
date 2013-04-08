/* MediaUtil LLJTran - $RCSfile: BasicJpegIo.java,v $
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
 *	$Id: BasicJpegIo.java,v 1.5 2007/12/30 07:09:29 drogatkin Exp $
 *
 * Some ideas and algorithms were borrowed from:
 * Thomas G. Lane, and James R. Weeks
 */
package android.mediautil.image.jpeg;

import java.io.UnsupportedEncodingException;

import android.mediautil.generic.BasicIo;

// TODO: rewrite using NIO
public class BasicJpegIo extends BasicIo {
	static final byte M_SOF0 = (byte) 0xC0; // Start Of Frame N
	static final byte M_SOF1 = (byte) 0xC1; // N indicates which compression
											// process
	static final byte M_SOF2 = (byte) 0xC2; // Only SOF0-SOF2 are now in common
											// use
	static final byte M_SOF3 = (byte) 0xC3;
	public static final byte M_DHT = (byte) 0xC4;
	static final byte M_SOF5 = (byte) 0xC5; // NB: codes C4 and CC are NOT SOF
											// markers
	static final byte M_SOF6 = (byte) 0xC6;
	static final byte M_SOF7 = (byte) 0xC7;
	static final byte M_JPG = (byte) 0xC8;
	static final byte M_SOF9 = (byte) 0xC9;
	static final byte M_SOF10 = (byte) 0xCA;
	static final byte M_SOF11 = (byte) 0xCB;
	static final byte M_SOF13 = (byte) 0xCD;
	static final byte M_SOF14 = (byte) 0xCE;
	static final byte M_SOF15 = (byte) 0xCF;
	static final byte M_RST0 = (byte) 0xD0;
	static final byte M_RST1 = (byte) 0xD1;
	static final byte M_RST2 = (byte) 0xD2;
	static final byte M_RST3 = (byte) 0xD3;
	static final byte M_RST4 = (byte) 0xD4;
	static final byte M_RST5 = (byte) 0xD5;
	static final byte M_RST6 = (byte) 0xD6;
	static final byte M_RST7 = (byte) 0xD7;
	static final byte M_SOI = (byte) 0xD8; // Start Of Image (beginning of
											// datastream)
	static final byte M_EOI = (byte) 0xD9; // End Of Image (end of datastream)
	static final byte M_SOS = (byte) 0xDA; // Start Of Scan (begins compressed
											// data)
	public static final byte M_DQT = (byte) 0xDB;
	public static final byte M_DNL = (byte) 0xDC;
	public static final byte M_DRI = (byte) 0xDD;
	public static final byte M_DHP = (byte) 0xDE;
	public static final byte M_EXP = (byte) 0xDF;
	static final byte M_APP0 = (byte) 0xE0; // Application-specific marker, type
											// N
	static final byte M_APP12 = (byte) 0xEC; // (we don't bother to list all 16
												// APPn's)
	static final byte M_COM = (byte) 0xFE; // COMment
	static final byte M_PRX = (byte) 0xFF; // Prefix

	int i2bsI(int offset, int value, int length) { // for Intel
		for (int i = 0, s = 0; i < length; i++, s += 8)
			data[offset + i] = (byte) (value >> s);
		return offset + length;
	}

	int bs2i(int offset, int length) {
		int val = 0;
		for (int i = 0; i < length; i++)
			val = (val << 8) + (data[offset + i] & 255);
		return val;
	}

	int s2n(int offset, int length) {
		return s2n(offset, length, false);
	}

	int s2n(int offset, int length, boolean signed) {
		return s2n(data, offset, length, signed, intel);
	}

	void n2s(byte[] result, int offset, int value, int length) {
		if (motorola) {
			for (int i = 0; i < length; i++) {
				result[offset + length - i - 1] = (byte) (value & 255);
				value >>= 8;
			}
		} else {
			for (int i = 0; i < length; i++) {
				result[offset + i] = (byte) (value & 255);
				value >>= 8;
			}
		}
	}

	public byte[] n2s(int value, int length) {
		byte[] result = new byte[length];
		n2s(result, 0, value, length);
		return result;
	}

	String s2a(int offset, int length) {
		String result = null;
		try {
			result = new String(data, offset, length, "Default");
		} catch (UnsupportedEncodingException e) {
		}
		return result;
	}

	boolean isSignature(int offset, String signature) {
		return isSignature(data, offset, signature);
	}

	boolean intel, motorola;

	protected byte[] data;

}
