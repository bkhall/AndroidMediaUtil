/* MediaUtil LLJTran - $RCSfile: BasicIo.java,v $
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
 *  $Id: BasicIo.java,v 1.3 2005/09/30 21:23:18 drogatkin Exp $
 *
 */
package android.mediautil.generic;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

/**
 * This is a utility class containing io and other general methods. This is
 * mainly for use by other MediaUtil classes but may also be useful otherwise.
 */
public class BasicIo {

	/**
	 * Converts bytes to a number
	 * 
	 * @param buf
	 *            Bytes to convert
	 * @param offset
	 *            Start offset of bytes
	 * @param length
	 *            Length of bytes to convert
	 * @param signed
	 *            If signed
	 * @param intel
	 *            Specifies If data is of intel type (buf[0] is Most Significant
	 *            Byte). If false then data is of Motorola Type (buf[0] is LSB)
	 */
	public static int s2n(byte[] buf, int offset, int length, boolean signed,
			boolean intel) {
		int val = 0;
		if (intel) {
			int shift = 0;
			for (int i = offset; i < (length + offset) && i < buf.length; i++) {
				val = val + ((buf[i] & 255) << shift);
				shift += 8;
			}
		} else {
			for (int i = 0; i < length; i++)
				val = (val << 8) + (buf[offset + i] & 255);
		}
		if (signed) {
			int msb = 1 << (8 * length - 1);
			if ((val & msb) > 0)
				val = val - (msb << 1);
		}
		return val;
	}

	/**
	 * Converts the passed int value to bytes of Intel Type (byte[0] is MSB)
	 * 
	 * @param result
	 *            Output bytes
	 * @param offset
	 *            Start offset to store the result
	 * @param value
	 *            Value to convert
	 * @param length
	 *            Length of bytes required
	 */
	public static void in2s(byte[] result, int offset, int value, int length) {
		for (int i = 0; i < length; i++) {
			result[offset + i] = (byte) (value & 255);
			value >>= 8;
		}
	}

	/**
	 * Converts the passed int value to bytes of Motorola Type (byte[0] is LSB)
	 * 
	 * @param result
	 *            Output bytes
	 * @param offset
	 *            Start offset to store the result
	 * @param value
	 *            Value to convert
	 * @param length
	 *            Length of bytes required
	 */
	public static void bn2s(byte[] result, int offset, int value, int length) {
		for (int i = 0; i < length; i++) {
			result[offset + length - i - 1] = (byte) (value & 255);
			value >>= 8;
		}
	}

	/**
	 * Converts the passed int value to bytes of Motorola Type (byte[0] is LSB)
	 * 
	 * @param value
	 *            Value to convert
	 * @param length
	 *            Length of bytes required
	 * @return Output bytes stored in a new byte array of size <b>length</b>
	 */
	public static byte[] bn2s(int value, int length) {
		byte[] result = new byte[length];
		bn2s(result, 0, value, length);
		return result;
	}

	/**
	 * Checks if the bytes in <b>markerData</b> match the signature String.
	 * 
	 * @param markerData
	 *            Marker Data
	 * @param offset
	 *            Start offset of bytes
	 * @param signature
	 *            Signature String. All the characters in this String are
	 *            compared with the bytes starting from <b>offset</b>
	 * @return true if the signature matches, false otherwise
	 */
	public static boolean isSignature(byte markerData[], int offset,
			String signature) {
		for (int i = 0; i < signature.length(); i++) {
			if (signature.charAt(i) != (markerData[offset + i] & 255))
				return false;
		}
		return true;
	}

	/**
	 * Definitely Skips n bytes unless End of Stream is reached. This is unlike
	 * the skip method of the InputStream which may skip less than the required
	 * number of bytes. This calls the InputStream's skip method repeatedly.
	 * 
	 * @param is
	 *            Input Stream
	 * @param n
	 *            Number bytes to skip
	 * @return Number of bytes actually skipped this equals n unless End Of
	 *         Stream is reached.
	 */
	public static long skip(InputStream is, long n) throws IOException {
		long lefttoskip = n;
		if (n > 0) {
			long skipLen;
			do {
				skipLen = is.skip(lefttoskip);
				if (skipLen < 1 && is.read() != -1)
					skipLen = 1;
				lefttoskip -= skipLen;
			} while (lefttoskip > 0 && skipLen > 0);
		}
		return n - lefttoskip;
	}

	/**
	 * Attempts to read <b>n</b> bytes and definitely reads <b>minBytes</b>.
	 * This is unlike the read method of the InputStream which may read less
	 * than than the required number of bytes. This calls the InputStream's read
	 * method repeatedly. This method does not return -1 on End Of Stream.
	 * 
	 * @param is
	 *            Input Stream
	 * @param b
	 *            Byte Array to read into
	 * @param off
	 *            Offset to store data from
	 * @param minBytes
	 *            Minimum bytes to read
	 * @param n
	 *            Number of bytes to try to read
	 * @return Number of bytes actually read. This is &gt;= minBytes and &lt;= n
	 *         unless End Of Stream is reached in which case it could be &lt;
	 *         minBytes. The return value is always &gt;= 0.
	 * @exception IOException
	 *                If <b>n</b> &lt; 0 or if <b>minBytes</b> &gt; <b>n</b> or
	 *                if <b>is.read(..)</b> throws and Exception.
	 */
	public static int read(InputStream is, byte b[], int off, int minBytes,
			int n) throws IOException {
		if (n < 0 || minBytes > n)
			throw new IOException("Invalid parameters minBytes = " + minBytes
					+ " n = " + n);
		int lefttoread = n;
		if (n > 0) {
			int minLeft = minBytes;
			int readLen;
			do {
				readLen = is.read(b, off, lefttoread);
				if (readLen > 0) {
					off += readLen;
					lefttoread -= readLen;
					minLeft -= readLen;
				}
			} while (minLeft > 0 && readLen >= 0);
		}
		return n - lefttoread;
	}

	/**
	 * Definitely Reads data.length bytes unless End of Stream is reached. This
	 * is unlike the read method of the InputStream which may read less than the
	 * required number of bytes. This calls the InputStream's read method
	 * repeatedly. This method does not return -1 on End Of Stream.
	 * 
	 * @param is
	 *            Input Stream
	 * @param data
	 *            Byte array to read to
	 * @return Number of bytes actually read which equals data.length unless End
	 *         Of Stream is reached. The return value is always &gt;= 0.
	 */
	public static int read(InputStream is, byte[] data) throws IOException {
		return read(is, data, 0, data.length, data.length);
	}

	public static final String FACTOR_ABVS[] = { "", "KB", "MB", "GB", "TB",
			"BB" };

	public static String convertLength(long l) {
		for (int i = 0;; i++) {
			if (l < 1024)
				return "" + l + FACTOR_ABVS[i];
			l /= 1024;
		}
	}

	public static int asInt(String str) {
		try {
			return ((ByteBuffer) ByteBuffer.allocate(4)
					.put(str.getBytes("ISO8859_1"))
					.order(ByteOrder.LITTLE_ENDIAN).flip()).getInt();
		} catch (UnsupportedEncodingException uee) {
		}
		throw new IllegalArgumentException("Can't represent  " + str
				+ " as int");
	}

	public static String asString(int i) {
		return Charset
				.forName("ISO8859_1")
				.decode((ByteBuffer) ByteBuffer.allocate(4)
						.order(ByteOrder.LITTLE_ENDIAN).putInt(i).flip())
				.toString();
	}
}
