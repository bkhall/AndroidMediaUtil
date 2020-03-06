/* MediaUtil AbstractImageInfo - $RCSfile: AbstractImageInfo.java,v $
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
 *	$Id: AbstractImageInfo.java,v 1.10 2013/02/26 08:21:51 drogatkin Exp $
 *
 * Some ideas and algorithms were borrowed from:
 * Thomas G. Lane, and James R. Weeks
 */
package android.mediautil.image.jpeg;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import android.mediautil.generic.FileFormatException;
import android.mediautil.generic.Rational;

/**
 * This class represent additional information about image, such information
 * usually supplied with an image in additional headers. Currently only Exif
 * among derived classes provides the full capability to view and modify the
 * Thumbnail and to modify Image Header Information through the methods
 * writeInfo, getThumbnailOffset and getThumbnailLength.
 * 
 * To provide more common solution, this class has to extend
 * javax.imageio.metadata.IIOMetadata
 * 
 * @author dmitriy
 */
public abstract class AbstractImageInfo<F extends LLJTran> extends BasicJpegIo {
	public static final String CROP_REGION = "CropRegion";

	public static final String NA = "n/a";

	public final static byte[] BMP_SIG = { 0x42, 0x4D };

	public final static int BMP24_HDR_SIZE = 54;

	protected F format;

	// conversions
	public final static double[] AV_TO_FSTOP = { 1, 1.4, 2, 2.8, 4, 5.6, 8, 11,
			16, 22, 32 };

	public final static Rational[] TV_TO_SEC = { new Rational(1, 1),
			new Rational(1, 2), new Rational(1, 4), new Rational(1, 8),
			new Rational(1, 15), new Rational(1, 30), new Rational(1, 60),
			new Rational(1, 125), new Rational(1, 250), new Rational(1, 500),
			new Rational(1, 1000), new Rational(1, 2000),
			new Rational(1, 4000), new Rational(1, 8000),
			new Rational(1, 16000) };

	public AbstractImageInfo() {
	}

	/**
	 * Loads the ImageInfo using information supplied. Relies on the
	 * implementation of {@link #readInfo()} by the deriving class.
	 * 
	 * @param is
	 *            Image input. Note that LLJTran does not pass the actual
	 *            ImageInput but only the Marker Data. This is because LLJTran
	 *            will have to read further from the same Input Stream.
	 * @param data
	 *            Image Header Information Marker Data excluding the 4 jpeg
	 *            marker bytes
	 * @param offset
	 *            Offset of marker within Image Input
	 * @param name
	 *            Name of the Image File
	 * @param comments
	 *            Image comments
	 * @param format
	 *            Image Object of type LLJTran
	 */
	public AbstractImageInfo(InputStream is, byte[] data, int offset, F format)
			throws FileFormatException {
		this.is = is;
		this.data = data;
		this.offset = offset;
		this.format = format;
		readInfo();
	}

	/**
	 * writeInfo method without actual imageWidth and imageHeight
	 * 
	 * @see #writeInfo(byte[],OutputStream,int,int,boolean,int,int,String)
	 */
	public void writeInfo(byte markerData[], OutputStream out, int op,
			int options, boolean modifyImageInfo) throws IOException {
		writeInfo(markerData, out, op, options, modifyImageInfo, -1, -1);
	}

	/**
	 * writeInfo method using default encoding of ISO8859_1
	 * 
	 * @see #writeInfo(byte[],OutputStream,int,int,boolean,int,int,String)
	 */
	public void writeInfo(byte markerData[], OutputStream out, int op,
			int options, boolean modifyImageInfo, int imageWidth,
			int imageHeight) throws IOException {
		writeInfo(markerData, out, op, options, modifyImageInfo, imageWidth,
				imageHeight, "ISO8859_1");
	}

	/**
	 * Writes modified or not Exif to out. APP header and its length are not
	 * included so any wrapper should do that calculation.
	 * <p>
	 * 
	 * This method is mainly for use by LLJTran to regenerate the Appx marker
	 * Data for the imageInfo. The default implementation does nothing and is
	 * expected to be implemented by the deriving class.
	 * 
	 * @param markerData
	 *            The existing markerData
	 * @param out
	 *            Output Stream to write out the new markerData
	 * @param op
	 *            The transformation option. This is used to switch the width
	 *            and height in imageInfo if op is a ROT_90 like transform and
	 *            transform the orientation tag and Thumbnail if opted for.
	 * @param options
	 *            OPT_XFORM_.. options of LLJTran. LLJTran passes its options
	 *            directly to this method. This uses the imageInfo related flags
	 *            {@link LLJTran#OPT_XFORM_THUMBNAIL} and
	 *            {@link LLJTran#OPT_XFORM_ORIENTATION} and makes the necessary
	 *            changes to imageInfo depending on the transform specified by
	 *            <b>op</b> before writing.
	 * @param modifyImageInfo
	 *            If true the changes made to imageInfo are retained, otherwise
	 *            the state is restored at the end of the call.
	 * @param imageWidth
	 *            Actual Image Width. If this and imageHeight are positive then
	 *            they are used for the width and height in imageInfo and no
	 *            switching of width and height is done for ROT_90 like
	 *            transforms.
	 * @param imageHeight
	 *            Actaul Image Height
	 * @param encoding
	 *            Encoding to be used when for writing out Character information
	 *            like comments.
	 */
	public void writeInfo(byte markerData[], OutputStream out, int op,
			int options, boolean modifyImageInfo, int imageWidth,
			int imageHeight, String encoding) throws IOException {
	}

	/**
	 * Reads the imageInfo from the Input supplied in Constructor. This is for
	 * derived class to implement.
	 */
	public abstract void readInfo() throws FileFormatException;

	/**
	 * Method to get the offset of the Thumbnail within the imageInfo data.
	 * <p>
	 * 
	 * The default implementation returns -1 since this method is expected to be
	 * implemented by the deriving class.
	 * 
	 * @return Offset of the Thumbnail within the Appx marker data
	 */
	public int getThumbnailOffset() {
		return -1;
	}

	/**
	 * Method to get the length of the Thumbnail.
	 * <p>
	 * 
	 * The default implementation returns -1 since this method is expected to be
	 * implemented by the deriving class.
	 * 
	 * @return Length of the Thumbnail
	 */
	public int getThumbnailLength() {
		return -1;
	}

	transient protected InputStream is;

	protected int offset;

	protected String name, comments;
}
