/* MediaUtil LLJTran - $RCSfile: LLJTran.java,v $
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
 *  $Id: LLJTran.java,v 1.13 2008/09/01 04:10:12 drogatkin Exp $
 *
 * Some ideas and algorithms were borrowed from:
 * Thomas G. Lane, and James R. Weeks
 *
 */
package android.mediautil.image.jpeg;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import android.graphics.Rect;
import android.mediautil.generic.BasicIo;
import android.mediautil.generic.FileFormatException;
import android.mediautil.generic.Log;
import android.mediautil.generic.ProgressCallback;
import android.mediautil.generic.directio.IterativeReader;
import android.mediautil.generic.directio.IterativeWriter;

class IterativeReadVars {
	public static final int READING_STAGE = 1;
	public static final int READING_DCT_STAGE = 2;
	public static final int READING_APPX_STAGE = 3;
	public static final int IMAGE_READ_STAGE = 4;
	public static final int DONE_STAGE = 5;

	// For info
	public int minReadRequest;
	public int maxReadRequest;

	public InputStream is;
	public int readUpto;
	public int stage;
	public int sections;
	public boolean keep_appxs, appxsCleared;
	public int appxPos, appxLen;
	public boolean throwException;

	// Vars for readDCT
	public double currentProgress, callbackProgress, progressPerMcu;
	public int[] last_dc;
	public int[][] DCT;
	public int next_restart_num;
	public int ix, iy;
}

class IterativeWriteVars {
	public static final int WRITE_BEGIN = 0;
	public static final int WRITE_COMMENTS = 1;
	public static final int WRITE_APPXS = 2;
	public static final int WRITE_DQT = 3;
	public static final int WRITE_DHT = 4;
	public static final int WRITE_START = 5;
	public static final int WRITE_DCT = 6;
	public static final int WRITE_COMPLETE = 7;

	// For info
	public int minWriteRequest;
	public int maxWriteRequest;

	public OutputStream os;
	public int op;
	public int options;
	public int restart_interval;
	public String comment;
	public Class<?> custom_appx;
	public int state = WRITE_COMPLETE;
	public byte huffTables[];
	public int currentAppxPos, currentAppx;

	public byte saveAppxs[];
	public int svX;
	public int svY;
	public int svWidthMCU;
	public int svHeightMCU;

	// initWriteDCT vars
	public boolean transformDct;
	public int[][][][][] new_dct_coefs;

	public double currentProgress, callbackProgress, progressPerMcu;
	public int[] last_dc;
	public int restarts_to_go;
	public int xCropOffsetMCU;
	public int yCropOffsetMCU;

	public boolean handleXEdge;
	public boolean handleYEdge;
	public int new_ix, new_iy;
	public boolean pullDownMode;

	// For unused method writeJpeg
	public boolean restoreVars;

	// if transformDct true it indicates if full Dct array needs to be allocated
	// or if the rows of old dct array can be reused.
	public boolean reuseDctRows = true;

	public void freeMemory() {
		huffTables = null;
		new_dct_coefs = null;
		last_dc = null;
	}
}

/**
 * LLJTran is a class for Lossless Jpeg Transformation.
 * <p>
 * 
 * With the help of Supporting classes like Exif it can also be used to change
 * Image Header Information (Exif header) like Date, Thumbnail, Orientation etc.
 * <p>
 * 
 * Following are the key features:
 * <p>
 * 
 * <ul>
 * <li>Supports lossless rotation, transpose, transverse and crop
 * <li>Trimming or relocating Non Transformable edge blocks similiar to <a
 * target=_blank href="http://www.ijg.org">jpegtran</a> or processing them like
 * regular MCU blocks. Please see the documentation for the
 * {@link #OPT_XFORM_TRIM} and {@link #OPT_XFORM_ADJUST_EDGES} for more info on
 * this.
 * <li>Reading and Modifying Image Header Information (Exif) including Thumbnail
 * <li>Built-in transformation of Thumbnail and Orientation marker
 * <li>Supports directio Interfaces {@link IterativeReader} and
 * {@link IterativeWriter} enabling things like Sharing the jpeg input file with
 * say jkd's ImageReader while reading
 * <li>Does <b>not</b> Support Multi-Threading for the same Object to be used
 * simultaneously by more than one thread. However different threads can have
 * their own LLJTran Objects.
 * </ul>
 * 
 * @author Dmitriy Rogatkin &amp; Suresh Mahalingam (msuresh@cheerful.com)
 */
public class LLJTran extends BasicJpegIo implements IterativeReader,
		IterativeWriter {
	/** Name of the JFIF format */
	public static final String JFIF = "JFIF";

	/** Name of the FPXR format */
	public static final String FPXR = "FPXR";

	/** Name of the JPEG format */
	public static final String JPEG = "JPEG";

	private static final String TAG = "LLJTran";

	// TODO: move rotation constants to some interface
	/** Identifies the identity transformation or no transformation */
	public static final int NONE = 0;
	/** Identifies the Horizontal Flip transformation */
	public static final int FLIP_H = 1;
	/** Identifies the Vertical Flip transformation */
	public static final int FLIP_V = 2;
	/** Identifies the Transpose transformation */
	public static final int TRANSPOSE = 3;
	/** Identifies the Transverse transformation */
	public static final int TRANSVERSE = 4;
	/** Identifies the Rotate 90 degrees clockwise transformation */
	public static final int ROT_90 = 5;
	/** Identifies the Rotate 180 degrees transformation */
	public static final int ROT_180 = 6;
	/** Identifies the Rotate 270 degrees clockwise transformation */
	public static final int ROT_270 = 7;
	/** Identifies the crop transformation */
	public static final int CROP = 8;
	/**
	 * Identifies the comment transformation. This is not used in LLJTran as of
	 * now
	 */
	public static final int COMMENT = 9;

	protected static final int DCTSIZE2 = 64;
	protected static final int DCTSIZE = 8;
	protected static final int BYTE_SIZE = 8;

	/**
	 * Identifies that no part of the Image has been read successfully. Not a
	 * valid option while reading
	 */
	public static final int READ_NONE = 0;
	/** Identifies the option to read upto the Image Header Info while reading */
	public static final int READ_INFO = 1;
	/**
	 * Identifies the option to read upto the Image Header including image
	 * dimensions, parameters and tables for decoding while reading
	 */
	public static final int READ_HEADER = 2;
	/** Identifies the option to read the entire image while reading */
	public static final int READ_ALL = 3;

	/**
	 * Flag to specify that the Image Header information (Exif width, height and
	 * resolution) should be adjusted when transforming the image
	 */
	public final static int OPT_XFORM_APPX = 0x1;
	/**
	 * Flag to specify that the Thumbnail in the Image Header Information (Exif)
	 * should be transformed when transforming the image. OPT_XFORM_APPX must be
	 * set for this to take effect
	 */
	public final static int OPT_XFORM_THUMBNAIL = 0x2;
	/**
	 * Flag to specify that Non Transformable edge blocks should be removed when
	 * transforming the image. Depending on the transformation the Image width,
	 * height or both may be trimmed to the nearest multiple of 8 or 16 pixels
	 * before the transform. Please note that this leads to loss of image pixels
	 * in the partial edge(es). When this flag is set the flag
	 * OPT_XFORM_ADJUST_EDGES is ignored even if set.
	 * <p>
	 * 
	 * More Info: You have partial MCU blocks when the image width is not a
	 * multiple of getMCUWidth() or image height is not a multiple of
	 * getMCUHeight(). getMCUWidth() or getMCUHeight() are a multiple of 8,
	 * usually 8, 16 and sometimes 24.
	 * <p>
	 * 
	 * Below lists the effect of Setting this flag on the Lossles
	 * Transformations:
	 * <p>
	 * 
	 * FLIP_H: Image bottom trimmed before transform
	 * <p>
	 * FLIP_V: Image right trimmed before transform
	 * <p>
	 * TRANSPOSE: No trimming required before transform
	 * <p>
	 * TRANSVERSE: Image right and bottom trimmed before transform
	 * <p>
	 * ROT_90: Image bottom trimmed before transform
	 * <p>
	 * ROT_180: Image right and bottom trimmed before transform
	 * <p>
	 * ROT_270: Image right trimmed before transform
	 * <p>
	 * CROP: The x and y coordinates of the top-left corner of the crop area is
	 * adjusted to the nearest MCU boundary.
	 */
	public final static int OPT_XFORM_TRIM = 0x4;;
	/**
	 * Flag to specifiy that Non Transformable edge blocks should be adjusted
	 * accordingly when transforming the image. Depending on the transformation
	 * either the right strip or the bottom strip of the image or both if
	 * present after the nearest MCU boundary maybe adjusted with a different
	 * transform.
	 * <p>
	 * 
	 * This option is ignored if OPT_XFORM_TRIM is set. If both these flags are
	 * not set the transformation is applied without accounting for partial MCU
	 * edges which may result in visual distortion at the edges.
	 * <p>
	 * 
	 * Below lists the effect of Setting this flag on the Lossles
	 * Transformations:
	 * <p>
	 * 
	 * FLIP_H: Right partial MCU strip is left unchanged
	 * <p>
	 * FLIP_V: Bottom partial MCU strip is left unchanged
	 * <p>
	 * TRANSPOSE: No adjustment required
	 * <p>
	 * TRANSVERSE: Result is same as ROT_90 followed by FLIP_V using LLJTran. So
	 * the right partial MCU strip is rotated 90, bottom rotated 270 and
	 * bottom-right corner MCU block transposed.
	 * <p>
	 * ROT_90: Bottom partial MCU strip is transposed and comes in the right
	 * edge the new image.
	 * <p>
	 * ROT_180: Result is the same as if FLIP_H is followed by a FLIP_V using
	 * LLJTran. So the bottom partial MCU strip mirrored horizontal, the right
	 * strip mirrored vertical and the bottom-right corner MCU block is left
	 * unchanged.
	 * <p>
	 * ROT_270: Right partial MCU strip is transposed and comes in the bottom
	 * edge the new image.
	 * <p>
	 * CROP: The x and y coordinates of the top-left corner of the crop area is
	 * adjusted to the nearest MCU boundary.
	 */
	public final static int OPT_XFORM_ADJUST_EDGES = 0x8;
	/**
	 * Flag to specify that the Orientation marker in the Image header
	 * information (Exif) should be changed to reflect the new Orientation of
	 * the image after transformation.
	 */
	public final static int OPT_XFORM_ORIENTATION = 0x10;
	/**
	 * Flag to specify that the App markers should be written when saving the
	 * image
	 */
	public final static int OPT_WRITE_APPXS = 0x100;
	/**
	 * Flag to specify that Jpeg comments should be written when saving the
	 * image
	 */
	public final static int OPT_WRITE_COMMENTS = 0x200;
	/**
	 * Flag containing all OPT_WRITE_XXX flags except OPT_WRITE_OPTIMIZE_HUFF
	 * for convenience
	 */
	public final static int OPT_WRITE_ALL = OPT_WRITE_APPXS
			| OPT_WRITE_COMMENTS;
	/**
	 * Flag to specify that the Huffman tables should be optimized before saving
	 * the image. This leads to a slightly reduced image file size.
	 */
	public final static int OPT_WRITE_OPTIMIZE_HUFF = 0x400;
	/**
	 * Flag containing defaults for convenience. Includes OPT_XFORM_APPX,
	 * OPT_XFORM_ADJUST_EDGES and OPT_WRITE_ALL flags
	 */
	public final static int OPT_DEFAULTS = (OPT_XFORM_APPX
			| OPT_XFORM_ADJUST_EDGES | OPT_WRITE_ALL);

	/**
	 * Flag indicating that an entity should be replaced with what is there in
	 * the LLJTran object during
	 * {@link #xferInfo(InputStream, OutputStream, int, int) xferInfo(..)}
	 */
	public final static int REPLACE = 0;
	/**
	 * Flag indicating that an entity should be retained during
	 * {@link #xferInfo(InputStream, OutputStream, int, int) xferInfo(..)}
	 */
	public final static int RETAIN = 1;
	/**
	 * Flag indicating that an entity should be removed during
	 * {@link #xferInfo(InputStream, OutputStream, int, int) xferInfo(..)}
	 */
	public final static int REMOVE = 2;

	/**
	 * Flag which specifies that the image width or x coordinate of the crop
	 * origin is not okay for a perfect transform.
	 * 
	 * @see #checkPerfect(int, Rect)
	 */
	public final static int IMPERFECT_X = 1;
	/**
	 * Flag which specifies that the image height or y coordinate of the crop
	 * origin is not okay for a perfect transform.
	 * 
	 * @see #checkPerfect(int, Rect)
	 */
	public final static int IMPERFECT_Y = 2;

	// New LLJTran internal read flags
	/** Internal flag indicating header section */
	protected static final int HEADER_SECTION = 2;
	/** Internal flag indicating body section */
	protected static final int BODY_SECTION = 4;
	// Specifies if ImageInfo needs to be created
	// If only this flag is set it means that the info
	// is read and the rest is thrown as it was in BasicJpeg
	/** Internal flag indicating image header info section */
	protected static final int INFO_SECTION = 1;
	/** Internal flag indicating all sections */
	protected static final int ALL_SECTIONS = 7;

	/*
	 * For Huffman Table Generation. assumed maximum initial code length before
	 * the JPEG mandate of limiting it to 16 is done.
	 */
	private static final int MAX_CLEN = 32;

	/** Table to get jpeg zigzag order for coefficient arrays */
	protected static final int jpegzigzagorder[] = { 0, 1, 5, 6, 14, 15, 27,
			28, 2, 4, 7, 13, 16, 26, 29, 42, 3, 8, 12, 17, 25, 30, 41, 43, 9,
			11, 18, 24, 31, 40, 44, 53, 10, 19, 23, 32, 39, 45, 52, 54, 20, 22,
			33, 38, 46, 51, 55, 60, 21, 34, 37, 47, 50, 56, 59, 61, 35, 36, 48,
			49, 57, 58, 62, 63 };

	/** Table to get the natural order index for a zigzag order index */
	protected static final int jpegnaturalorder[] = { 0, 1, 8, 16, 9, 2, 3, 10,
			17, 24, 32, 25, 18, 11, 4, 5, 12, 19, 26, 33, 40, 48, 41, 34, 27,
			20, 13, 6, 7, 14, 21, 28, 35, 42, 49, 56, 57, 50, 43, 36, 29, 22,
			15, 23, 30, 37, 44, 51, 58, 59, 52, 45, 38, 31, 39, 46, 53, 60, 61,
			54, 47, 55, 62, 63, 63, 63, 63, 63, 63, 63, 63, 63, // extra entries
																// for safety in
																// decoder
			63, 63, 63, 63, 63, 63, 63, 63 };

	// Borrowed from jcparam.c in libjpeg 6b
	private static final byte stdHuffTables[] = {
			0, // Code to indicate DC Table 0(Luminance). Below are entries
				// #Symbols of lengths 1-16
			0,
			1,
			5,
			1,
			1,
			1,
			1,
			1,
			1,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			// Symbols
			0,
			1,
			2,
			3,
			4,
			5,
			6,
			7,
			8,
			9,
			10,
			11,
			1, // Code to indicate DC Table 1(Chrominance). Below are entries
				// #Symbols of lengths 1-16
			0,
			3,
			1,
			1,
			1,
			1,
			1,
			1,
			1,
			1,
			1,
			0,
			0,
			0,
			0,
			0,
			// Symbols
			0,
			1,
			2,
			3,
			4,
			5,
			6,
			7,
			8,
			9,
			10,
			11,
			16, // Code to indicate AC Table 0(Luminance). Below are entries
			// #Symbols of lengths 1-16
			0,
			2,
			1,
			3,
			3,
			2,
			4,
			3,
			5,
			5,
			4,
			4,
			0,
			0,
			1,
			(byte) 0x7d,
			// Symbols
			(byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x00, (byte) 0x04,
			(byte) 0x11, (byte) 0x05, (byte) 0x12, (byte) 0x21, (byte) 0x31,
			(byte) 0x41, (byte) 0x06, (byte) 0x13, (byte) 0x51, (byte) 0x61,
			(byte) 0x07, (byte) 0x22, (byte) 0x71, (byte) 0x14, (byte) 0x32,
			(byte) 0x81, (byte) 0x91, (byte) 0xa1, (byte) 0x08, (byte) 0x23,
			(byte) 0x42, (byte) 0xb1, (byte) 0xc1, (byte) 0x15, (byte) 0x52,
			(byte) 0xd1, (byte) 0xf0, (byte) 0x24, (byte) 0x33, (byte) 0x62,
			(byte) 0x72, (byte) 0x82, (byte) 0x09, (byte) 0x0a, (byte) 0x16,
			(byte) 0x17, (byte) 0x18, (byte) 0x19, (byte) 0x1a, (byte) 0x25,
			(byte) 0x26, (byte) 0x27, (byte) 0x28, (byte) 0x29, (byte) 0x2a,
			(byte) 0x34, (byte) 0x35, (byte) 0x36, (byte) 0x37, (byte) 0x38,
			(byte) 0x39, (byte) 0x3a, (byte) 0x43, (byte) 0x44, (byte) 0x45,
			(byte) 0x46, (byte) 0x47, (byte) 0x48, (byte) 0x49,
			(byte) 0x4a,
			(byte) 0x53,
			(byte) 0x54,
			(byte) 0x55,
			(byte) 0x56,
			(byte) 0x57,
			(byte) 0x58,
			(byte) 0x59,
			(byte) 0x5a,
			(byte) 0x63,
			(byte) 0x64,
			(byte) 0x65,
			(byte) 0x66,
			(byte) 0x67,
			(byte) 0x68,
			(byte) 0x69,
			(byte) 0x6a,
			(byte) 0x73,
			(byte) 0x74,
			(byte) 0x75,
			(byte) 0x76,
			(byte) 0x77,
			(byte) 0x78,
			(byte) 0x79,
			(byte) 0x7a,
			(byte) 0x83,
			(byte) 0x84,
			(byte) 0x85,
			(byte) 0x86,
			(byte) 0x87,
			(byte) 0x88,
			(byte) 0x89,
			(byte) 0x8a,
			(byte) 0x92,
			(byte) 0x93,
			(byte) 0x94,
			(byte) 0x95,
			(byte) 0x96,
			(byte) 0x97,
			(byte) 0x98,
			(byte) 0x99,
			(byte) 0x9a,
			(byte) 0xa2,
			(byte) 0xa3,
			(byte) 0xa4,
			(byte) 0xa5,
			(byte) 0xa6,
			(byte) 0xa7,
			(byte) 0xa8,
			(byte) 0xa9,
			(byte) 0xaa,
			(byte) 0xb2,
			(byte) 0xb3,
			(byte) 0xb4,
			(byte) 0xb5,
			(byte) 0xb6,
			(byte) 0xb7,
			(byte) 0xb8,
			(byte) 0xb9,
			(byte) 0xba,
			(byte) 0xc2,
			(byte) 0xc3,
			(byte) 0xc4,
			(byte) 0xc5,
			(byte) 0xc6,
			(byte) 0xc7,
			(byte) 0xc8,
			(byte) 0xc9,
			(byte) 0xca,
			(byte) 0xd2,
			(byte) 0xd3,
			(byte) 0xd4,
			(byte) 0xd5,
			(byte) 0xd6,
			(byte) 0xd7,
			(byte) 0xd8,
			(byte) 0xd9,
			(byte) 0xda,
			(byte) 0xe1,
			(byte) 0xe2,
			(byte) 0xe3,
			(byte) 0xe4,
			(byte) 0xe5,
			(byte) 0xe6,
			(byte) 0xe7,
			(byte) 0xe8,
			(byte) 0xe9,
			(byte) 0xea,
			(byte) 0xf1,
			(byte) 0xf2,
			(byte) 0xf3,
			(byte) 0xf4,
			(byte) 0xf5,
			(byte) 0xf6,
			(byte) 0xf7,
			(byte) 0xf8,
			(byte) 0xf9,
			(byte) 0xfa,
			17, // Code to indicate AC Table 1(Chrominance). Below are entries
			// #Symbols of lengths 1-16
			0,
			2,
			1,
			2,
			4,
			4,
			3,
			4,
			7,
			5,
			4,
			4,
			0,
			1,
			2,
			(byte) 0x77,
			// Symbols
			(byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x11,
			(byte) 0x04, (byte) 0x05, (byte) 0x21, (byte) 0x31, (byte) 0x06,
			(byte) 0x12, (byte) 0x41, (byte) 0x51, (byte) 0x07, (byte) 0x61,
			(byte) 0x71, (byte) 0x13, (byte) 0x22, (byte) 0x32, (byte) 0x81,
			(byte) 0x08, (byte) 0x14, (byte) 0x42, (byte) 0x91, (byte) 0xa1,
			(byte) 0xb1, (byte) 0xc1, (byte) 0x09, (byte) 0x23, (byte) 0x33,
			(byte) 0x52, (byte) 0xf0, (byte) 0x15, (byte) 0x62, (byte) 0x72,
			(byte) 0xd1, (byte) 0x0a, (byte) 0x16, (byte) 0x24, (byte) 0x34,
			(byte) 0xe1, (byte) 0x25, (byte) 0xf1, (byte) 0x17, (byte) 0x18,
			(byte) 0x19, (byte) 0x1a, (byte) 0x26, (byte) 0x27, (byte) 0x28,
			(byte) 0x29, (byte) 0x2a, (byte) 0x35, (byte) 0x36, (byte) 0x37,
			(byte) 0x38, (byte) 0x39, (byte) 0x3a, (byte) 0x43, (byte) 0x44,
			(byte) 0x45, (byte) 0x46, (byte) 0x47, (byte) 0x48, (byte) 0x49,
			(byte) 0x4a, (byte) 0x53, (byte) 0x54, (byte) 0x55, (byte) 0x56,
			(byte) 0x57, (byte) 0x58, (byte) 0x59, (byte) 0x5a, (byte) 0x63,
			(byte) 0x64, (byte) 0x65, (byte) 0x66, (byte) 0x67, (byte) 0x68,
			(byte) 0x69, (byte) 0x6a, (byte) 0x73, (byte) 0x74, (byte) 0x75,
			(byte) 0x76, (byte) 0x77, (byte) 0x78, (byte) 0x79, (byte) 0x7a,
			(byte) 0x82, (byte) 0x83, (byte) 0x84, (byte) 0x85, (byte) 0x86,
			(byte) 0x87, (byte) 0x88, (byte) 0x89, (byte) 0x8a, (byte) 0x92,
			(byte) 0x93, (byte) 0x94, (byte) 0x95, (byte) 0x96, (byte) 0x97,
			(byte) 0x98, (byte) 0x99, (byte) 0x9a, (byte) 0xa2, (byte) 0xa3,
			(byte) 0xa4, (byte) 0xa5, (byte) 0xa6, (byte) 0xa7, (byte) 0xa8,
			(byte) 0xa9, (byte) 0xaa, (byte) 0xb2, (byte) 0xb3, (byte) 0xb4,
			(byte) 0xb5, (byte) 0xb6, (byte) 0xb7, (byte) 0xb8, (byte) 0xb9,
			(byte) 0xba, (byte) 0xc2, (byte) 0xc3, (byte) 0xc4, (byte) 0xc5,
			(byte) 0xc6, (byte) 0xc7, (byte) 0xc8, (byte) 0xc9, (byte) 0xca,
			(byte) 0xd2, (byte) 0xd3, (byte) 0xd4, (byte) 0xd5, (byte) 0xd6,
			(byte) 0xd7, (byte) 0xd8, (byte) 0xd9, (byte) 0xda, (byte) 0xe2,
			(byte) 0xe3, (byte) 0xe4, (byte) 0xe5, (byte) 0xe6, (byte) 0xe7,
			(byte) 0xe8, (byte) 0xe9, (byte) 0xea, (byte) 0xf2, (byte) 0xf3,
			(byte) 0xf4, (byte) 0xf5, (byte) 0xf6, (byte) 0xf7, (byte) 0xf8,
			(byte) 0xf9, (byte) 0xfa };

	/** Artist info. Not used by LLJTran as of now. */
	protected String artist;

	private String enc;

	private void commonInit() {
		iWriteVars = new IterativeWriteVars();
		iReadVars = new IterativeReadVars();
	}

	/**
	 * Constructor.
	 * 
	 * @param file
	 *            File object specifying the file to read image from. An
	 *            internal input stream is created which is closed when the
	 *            image is fully read or if an error occurs. The internal input
	 *            Stream can be closed explicity by calling
	 *            {@link #closeInternalInputStream()}
	 */
	public LLJTran(File file) {
		commonInit();
		this.file = file;
		markerid = new byte[2];
		prevHuffOption = -1;
		readProgressCallback = null;
		writeProgressCallback = null;
	}

	/**
	 * Constructor.
	 * 
	 * @param inStream
	 *            Source to read the image. This stream is not closed by LLJTran
	 *            and it is the callers responsibility to do so.
	 */
	public LLJTran(InputStream inStream) {
		commonInit();
		markerid = new byte[2];
		this.inStream = inStream;
		prevHuffOption = -1;
		readProgressCallback = null;
		writeProgressCallback = null;
	}

	/**
	 * Resets the input for loading the image. This method is mainly for loading
	 * the image after reading upto READ_INFO and closing the input. false can
	 * be passed for the keep_appxs param for the read following this to retain
	 * the existing Image Header Information (Exif). A Runtime exception results
	 * if the image is already read beyond INFO_SECTION.
	 * <p>
	 * 
	 * The method uses inStream as the new input, sets readUpto to READ_NONE and
	 * closes any internal Input Stream.
	 * 
	 * @param inStream
	 *            New Input. If null then the previous input must have been a
	 *            file which will be used.
	 */
	public void resetInput(InputStream inStream) {
		if (readUpto > READ_INFO)
			throw new RuntimeException(
					"Restting Input not allowed if current input read beyond READ_INFO");
		if (inStream == null) {
			if (file == null)
				throw new RuntimeException(
						"inStream null and no existing file to read from");
		} else
			file = null;
		closeInternalInputStream();
		this.inStream = inStream;
		readUpto = READ_NONE;
		unprocessed_marker = 0;
	}

	/**
	 * Resets the input for loading the image. This method is mainly for loading
	 * the image after reading upto READ_INFO and closing the input. false can
	 * be passed for the keep_appxs param for the read following this to retain
	 * the existing Image Header Information (Exif). A Runtime exception results
	 * if the image is already read beyond INFO_SECTION.
	 * <p>
	 * 
	 * The method uses file as the new input, sets readUpto to READ_NONE and
	 * closes any internal Input Stream.
	 * 
	 * @param file
	 *            New Input file.
	 */
	public void resetInput(File file) {
		if (readUpto > READ_INFO)
			throw new RuntimeException(
					"Restting Input not allowed if current input read beyond READ_INFO");
		closeInternalInputStream();
		this.file = file;
		inStream = null;
		readUpto = READ_NONE;
		unprocessed_marker = 0;
	}

	/**
	 * Sets the encoding for Jpeg comments.
	 * 
	 * @param enc
	 *            Name of a jdk supported Charset or null to use default
	 *            encoding
	 */
	public void setEncoding(String enc) {
		this.enc = enc;
	}

	/**
	 * Gets the encoding which will be used for comments.
	 * 
	 * @return Encoding or null to indicate platform default encoding.
	 */
	public String getEncoding() {
		return enc;
	}

	/**
	 * Gets the Name of the Source file.
	 * 
	 * @return Name of the Source file. Returns Unknown/Stream if reading from a
	 *         stream
	 */
	public String getName() {
		if (file == null)
			return "Unknown/Stream";
		return file.getName();
	}

	/**
	 * Gets the Name of the Source file.
	 * 
	 * @see #getName()
	 */
	@Override
	public String toString() {
		return getName();
	}

	/**
	 * Returns the File source or null if reading from a Stream
	 */
	public File getFile() {
		return file;
	}

	/**
	 * Checks if JPEG objects are equal TODO: point to the same file can be not
	 * sufficient
	 * 
	 * @param o
	 * @return True if equal, false otherwise
	 */
	@Override
	public boolean equals(Object o) {
		if (o instanceof LLJTran && ((LLJTran) o).getFile().equals(file))
			return true;
		return false;
	}

	/**
	 * This gets the current error message because of which LLJTran has stopped
	 * processing the jpeg input. This results in an exception when using
	 * regular read methods. However when using initRead and nextRead via
	 * classes in android.mediautil.generic.directio package this method should
	 * be called to check for error messages due to which the image was not
	 * processed.
	 * 
	 * @return The error message due to which LLJTran cannot proceed. Null if
	 *         there is no error message
	 * @see #nextRead(int)
	 */
	public String getErrorMsg() {
		return errorMsg;
	}

	/**
	 * Returns pending error message. This occurs when you read only upto the
	 * header section and the jpeg format is unsupported. You can read the Image
	 * Header Info (Exif) and jpeg comments but the next call to a read method
	 * will give an error. Note that if getErrorMsg() returns a non null error
	 * message this will return the same message.
	 * 
	 * @return pending error message which will cause an exception for the next
	 *         read call or null if no pending error message
	 */
	public String getPendingErrorMsg() {
		return unprocessedError;
	}

	/** method for sub classes to set an error */
	protected void setErrorMsg(String msg) {
		errorMsg = msg;
	}

	/**
	 * Gets the exception if any corresponding an error.
	 * 
	 * @return The exception if any corresponding to an error message.
	 * @see #getErrorMsg()
	 */
	public Exception getException() {
		return lljtError;
	}

	/** method for sub classes to set an exception */
	protected void setException(Exception e) {
		lljtError = e;
	}

	/**
	 * Internal method to create an input stream from File for reading image.
	 */
	protected InputStream createInputStream() {
		try {
			if (valid) {
				readcounter = 0;
				writecounter = 0;
				if (file == null) {
					if (inStream != null)
						return inStream;
					else
						valid = false;
				} else {
					return new BufferedInputStream(new FileInputStream(file));
				}
			}
		} catch (FileNotFoundException e) {
			valid = false;
		}
		return null;
	}

	/**
	 * Returns an instance of the Image Header Info. The Class of the Object
	 * returned is a specific implementation of AbstractImageInfo like Exif. If
	 * there is no specific Image Header Information like Exif an instance of
	 * JPEG containing basic Image Info is returned.
	 * 
	 * @return Image Header Information
	 */
	public AbstractImageInfo<?> getImageInfo() {
		return imageinfo;
	}

	/**
	 * Gets the Jpeg comment present in the image or null if none present.
	 * 
	 * @return Jpeg comment present in the image or null if none present
	 */
	public String getComment() {
		return out_comment;
	}

	/**
	 * Sets the jpeg comment to be written.
	 * 
	 * @param comment
	 *            Comment to be written or null if none is to be written
	 */
	public void setComment(String comment) {
		out_comment = comment;
	}

	/**
	 * Gets full path of the File source or null if reading from a stream
	 * 
	 * @return Full path of the File source or null if reading from a stream
	 */
	public String getLocationName() {
		return file == null ? null : file.getAbsolutePath();
	}

	/**
	 * Sets callback to update the progress of Image read.
	 * 
	 * @param callback
	 *            Callback to update the progress of read or null for no
	 *            callback
	 */
	public void setReadProgressCallback(ProgressCallback callback) {
		readProgressCallback = callback;
	}

	/**
	 * Sets callback to update the progress of Image write.
	 * 
	 * @param callback
	 *            Callback to update the progress of write or null for no
	 *            callback
	 */
	public void setWriteProgressCallback(ProgressCallback callback) {
		writeProgressCallback = callback;
	}

	/**
	 * Gets the current Callback Object for Image Read progress or null if no
	 * callback is present.
	 * 
	 * @return Current Callback Object for Image Read progress or null if no
	 *         callback is present
	 */
	public ProgressCallback getReadProgressCallback() {
		return readProgressCallback;
	}

	/**
	 * Gets the current Callback Object for Image Write progress or null if no
	 * callback is present.
	 * 
	 * @return Current Callback Object for Image Write progress or null if no
	 *         callback is present
	 */
	public ProgressCallback getWriteProgressCallback() {
		return writeProgressCallback;
	}

	/**
	 * Internal method which transforms the Image Header Info (Like Exif) and
	 * updates the Appxs array. This method essentially calls writeInfo on the
	 * stored imageInfo.
	 * 
	 * @param op
	 *            The transform operation
	 * @param options
	 *            Options specifies how to manage exif or other header content
	 *            including embedded thumbnail transformation. Please pass a
	 *            bitwise OR (|) of the required set of OPT_XFORM_.. flags.
	 * @param modifyImageInfo
	 *            specifies if the instance of the imageInfo should be modified
	 *            or just the appxs array should be updated
	 * @return Returns true if the method was successful or false if no data was
	 *         written by the imageInfo's writeInfo method. Note that the
	 *         default implementation provided in AbstractImageInfo is empty and
	 *         this method would fail unless the subclass overrides it which is
	 *         done as of now only in Exif.
	 * @see AbstractImageInfo
	 */
	protected boolean transformAppHeader(int op, int options,
			boolean modifyImageInfo) {
		boolean retVal = false;
		if (imageinfo != null && appxs != null && appHdrIndex >= 0) {
			try {
				ByteArrayOutputStream buf = new ByteArrayOutputStream(2 * 1024);
				/*
				 * TODO: Put in BasicJpeg if required if (imageinfo instanceof
				 * Exif && artist != null) {
				 * 
				 * Exif exif = ((Exif)imageinfo); Entry e =
				 * exif.getTagValue(Exif.ARTIST, true); // TODO: localization,
				 * put in resources if (e == null) { e = new Entry
				 * ("Camera owner, "+artist); exif.setTagValue(Exif.ARTIST,
				 * Exif.EXIFOFFSET, e, true); } else e.setValue(0,
				 * "Camera owner, "+artist); }
				 */
				// Write out 4 bytes for the marker
				buf.write(markerid);
				buf.write(markerid);
				imageinfo.writeInfo(appxs[appHdrIndex], buf, op, options,
						modifyImageInfo, frm_x, frm_y);
				int len = buf.size() - 4;
				if (len > 0) {
					byte appCode = appxs[appHdrIndex][1];
					appxs[appHdrIndex] = buf.toByteArray();
					appxs[appHdrIndex][0] = M_PRX;
					appxs[appHdrIndex][1] = appCode;
					bn2s(appxs[appHdrIndex], 2, len + 2, 2);
					retVal = true;
				} else if (Log.debugLevel >= Log.LEVEL_WARNING)
					android.util.Log
							.w(TAG,
									"Warning: transform: Unable to transform App Hdr possibly because the format is not fully supported");
				buf.close();
				buf = null;
			} catch (Exception e) {
				if (Log.debugLevel >= Log.LEVEL_ERROR)
					e.printStackTrace();
				throw new RuntimeException(e.getMessage());
			}
		}

		return retVal;
	}

	private void validateCropBounds(Rect bounds) {
		if (bounds.width() <= 0 || bounds.height() <= 0 || bounds.left < 0
				|| bounds.left >= frm_x || bounds.top < 0
				|| bounds.top >= frm_y)
			throw new ArrayIndexOutOfBoundsException("Invalid Crop Request: "
					+ bounds.left + ", " + bounds.top + ", " + bounds.width()
					+ "x" + bounds.height() + " frame: " + frm_x + ", " + frm_y);

		// Adjust origin to closest MCU boundary
		int xBoundary = getMCUWidth();
		int yBoundary = getMCUHeight();
		int rem;

		rem = bounds.left % xBoundary;
		cropBounds.left = bounds.left - rem;
		if (rem > xBoundary / 2 && cropBounds.left + xBoundary < frm_x)
			cropBounds.left = cropBounds.left + xBoundary;
		rem = bounds.top % yBoundary;
		cropBounds.top = bounds.top - rem;
		if (rem > yBoundary / 2 && cropBounds.top + yBoundary < frm_y)
			cropBounds.top = cropBounds.top + yBoundary;

		cropBounds.right = (cropBounds.left + bounds.width() > frm_x) ? (frm_x - cropBounds.left)
				: bounds.width();
		cropBounds.bottom = (cropBounds.top + bounds.height() > frm_y) ? (frm_y - cropBounds.top)
				: bounds.height();
	}

	/**
	 * Checks if the current image is suitable for a perfect transform.
	 * 
	 * @return A bitwise OR (|) of the 2 flags IMPERFECT_X and IMPERFECT_Y.
	 *         <p>
	 * 
	 *         IMPERFECT_X is set in the return value if the width (x origin in
	 *         case op is CROP) has to be adjusted/trimmed.
	 *         <p>
	 * 
	 *         IMPERFECT_Y is set in the return value if the height (y origin in
	 *         case op is CROP) has to be adjusted/trimmed.
	 *         <p>
	 * 
	 *         Thus 0 means the transform will be perfect.
	 *         <p>
	 * 
	 *         Note that though checkPerfect returns 0 depending on <b>op</b>,
	 *         the transformation operation either or both the image width and
	 *         height need not be a multiple of the MCU width/height.
	 * @see #IMPERFECT_X
	 */
	public int checkPerfect(int op, Rect cropBounds) {
		int retVal = 0;
		int xBoundary = getMCUWidth();
		int yBoundary = getMCUHeight();

		if (op == CROP) {
			if (cropBounds.left % xBoundary != 0)
				retVal |= IMPERFECT_X;
			if (cropBounds.top % yBoundary != 0)
				retVal |= IMPERFECT_Y;
		}

		if ((op == ROT_270 || op == TRANSVERSE || op == ROT_180 || op == FLIP_H)
				&& frm_x % xBoundary != 0)
			retVal |= IMPERFECT_X;

		if ((op == ROT_90 || op == TRANSVERSE || op == ROT_180 || op == FLIP_V)
				&& frm_y % yBoundary != 0)
			retVal |= IMPERFECT_Y;

		return retVal;
	}

	/**
	 * Transorms the current image using OPT_DEFAULTS. This method may be called
	 * multiple times before saving the image using save. The jpeg image must be
	 * fully read by LLJTran first.
	 * 
	 * @param op
	 *            Specifies the transformation operation like NONE, ROT_90 etc.
	 *            CROP is treated as NONE.
	 * @see #save(OutputStream, int)
	 */
	public void transform(int op) {
		transform(op, OPT_DEFAULTS);
	}

	/**
	 * Transorms the current image using supplied options. This method may be
	 * called multiple times before saving the image using save. The jpeg image
	 * must be fully read by LLJTran first.
	 * 
	 * @param op
	 *            Specifies the transformation like NONE, ROT_90 etc. CROP is
	 *            treated as NONE.
	 * @param options
	 *            options specifies how to manage exif or other header content
	 *            including embedded thumbnail transformation. Please pass a
	 *            bitwise OR (|) of the required set of OPT_XFORM_.. flags.
	 * @see #save(OutputStream, int)
	 */
	public void transform(int op, int options) {
		if (op == CROP)
			op = NONE;
		transform(op, options, null);
	}

	/**
	 * This is a high level method for lossless transformation of JPEG image.
	 * This method may be called multiple times before saving the image using
	 * save. The jpeg image must be fully read by LLJTran first.
	 * <p>
	 * 
	 * Use this method for CROP operation.
	 * 
	 * @param op
	 *            One of operations, like CROP, or ROT_xxx
	 * @param options
	 *            Options specifies how to manage exif or other header content
	 *            including embedded thumbnail transformation. Please pass a
	 *            bitwise OR (|) of the required set of OPT_XFORM_.. flags.
	 * @param bounds
	 *            Crop bounds. This must be passed if op is CROP, ignored
	 *            otherwise. The x and y values of bounds must be within the
	 *            image and are adjusted to the closest MCU boundary. The width
	 *            and height must be positive, but are automatically limited to
	 *            imageWidth-bounds.x and imageHieght-bounds.y respectively. The
	 *            bounds Object passed is unchanged.
	 * @see #save(OutputStream, int)
	 */
	public void transform(int op, int options, Rect bounds) {
		prevHuffOption = -1;
		if (readUpto < READ_ALL)
			throw new RuntimeException(
					"Transform cannot be performed since No Jpeg has been successfully Read");
		if ((options & OPT_XFORM_APPX) == 0)
			options &= ~OPT_XFORM_THUMBNAIL;
		if ((options & OPT_XFORM_THUMBNAIL) != 0 && !appxs_read)
			if (Log.debugLevel >= Log.LEVEL_WARNING)
				android.util.Log
						.w(TAG,
								"Warning: Thumbnail transformation cannot be performed since keep_appxs was passed as false while reading");
		if (op == CROP)
			validateCropBounds(bounds);
		adjustImageParameters(op, options);
		switch (op) {
		case TRANSPOSE:
		case TRANSVERSE:
		case ROT_90:
		case ROT_270:
			transposeImageParameters();
			transposeQTable();
			break;
		case ROT_180:
		case FLIP_H:
		case FLIP_V:
		case NONE:
		default:
		}

		try {
			if ((options & OPT_XFORM_APPX) != 0)
				transformAppHeader(op, options, true);
			writeDCT(null, op, options, 0, true);
		} catch (IOException e) {
			if (Log.debugLevel >= Log.LEVEL_ERROR) {
				android.util.Log.w(TAG,
						"Warning:transform: Exception while transforming Thumbnail: "
								+ e.getMessage());
				e.printStackTrace();
			}
		}
	}

	/**
	 * Saves the current image after transforming it using OPT_DEFAULTS. The
	 * transformation is applied only while saving the image. The image itself
	 * is restored to its original state on completion of the method. This
	 * method may be called multiple times. The jpeg image must be fully read by
	 * LLJTran first.
	 * 
	 * @param outStream
	 *            Output Stream to which the jpeg image should be written
	 * @param op
	 *            Specifies the transformation like NONE, ROT_90 etc. CROP is
	 *            treated as NONE.
	 */
	public void transform(OutputStream outStream, int op) throws IOException {
		transform(outStream, op, OPT_DEFAULTS);
	}

	/**
	 * Saves the current image after transforming it using supplied options. The
	 * transformation is applied only while saving the image. The image itself
	 * is restored to its original state on completion of the method. This
	 * method may be called multiple times. The jpeg image must be fully read by
	 * LLJTran first.
	 * 
	 * @param outStream
	 *            Output Stream to which the jpeg image should be written
	 * @param op
	 *            Specifies the transformation like NONE, ROT_90 etc. CROP is
	 *            treated as NONE.
	 * @param options
	 *            Options specifies how to manage exif or other header content
	 *            including embedded thumbnail transformation and also the
	 *            header information to write. Please pass a bitwise OR (|) of
	 *            the required set of OPT_XFORM_.. and OPT_WRITE_.. flags.
	 */
	public void transform(OutputStream outStream, int op, int options)
			throws IOException {
		if (op == CROP)
			op = NONE;
		transform(outStream, op, options, null);
	}

	/**
	 * Saves the current image after transforming it using supplied options. The
	 * transformation is applied only while saving the image. The image itself
	 * is restored to its original state on completion of the method. This
	 * method may be called multiple times. The jpeg image must be fully read by
	 * LLJTran first.
	 * <p>
	 * 
	 * Use this method for CROP operation.
	 * 
	 * @param outStream
	 *            Output Stream to which the jpeg image should be written
	 * @param op
	 *            Specifies the transformation like NONE, ROT_90 etc
	 * @param options
	 *            Options specifies how to manage exif or other header content
	 *            including embedded thumbnail transformation and also the
	 *            header information to write. Please pass a bitwise OR (|) of
	 *            the required set of OPT_XFORM_.. and OPT_WRITE_.. flags.
	 * @param bounds
	 *            Crop bounds. This must be passed if op is CROP, ignored
	 *            otherwise. The x and y values of bounds must be within the
	 *            image and are adjusted to the closest MCU boundary. The width
	 *            and height must be positive, but are automatically limited to
	 *            imageWidth-bounds.x and imageHieght-bounds.y respectively. The
	 *            bounds Object passed is unchanged.
	 */
	public void transform(OutputStream outStream, int op, int options,
			Rect bounds) throws IOException {
		transform(outStream, op, options, bounds, 0);
	}

	/**
	 * Saves the current image after transforming it using supplied options and
	 * with restart_markers at the specified MCU interval. The transformation is
	 * applied only while saving the image. The image itself is restored to its
	 * original state on completion of the method. This method may be called
	 * multiple times. The jpeg image must be fully read by LLJTran first.
	 * <p>
	 * 
	 * Use this method for CROP operation.
	 * 
	 * @param outStream
	 *            Output Stream to which the jpeg image should be written
	 * @param op
	 *            Specifies the transformation like NONE, ROT_90 etc
	 * @param options
	 *            Options specifies how to manage exif or other header content
	 *            including embedded thumbnail transformation and also the
	 *            header information to write. Please pass a bitwise OR (|) of
	 *            the required set of OPT_XFORM_.. and OPT_WRITE_.. flags.
	 * @param bounds
	 *            Crop bounds. This must be passed if op is CROP, ignored
	 *            otherwise. The x and y values of bounds must be within the
	 *            image and are adjusted to the closest MCU boundary. The width
	 *            and height must be positive, but are automatically limited to
	 *            imageWidth-bounds.x and imageHieght-bounds.y respectively. The
	 *            bounds Object passed is unchanged.
	 * @param restart_interval
	 *            Specifies to write a restart marker every restart_interval MCU
	 *            block. No restart markers are written if this parameter is
	 *            passed as 0
	 */
	public void transform(OutputStream outStream, int op, int options,
			Rect bounds, int restart_interval) throws IOException {
		transform(outStream, op, options, bounds, restart_interval, null);
	}

	/**
	 * This method is to be used for the Iterative version of the transform and
	 * save methods. The main use of this method is to get an IterativeWriter
	 * for use with InStreamFromIterativeWriter class to read the transformed
	 * image directly from LLJTran instead of writing it out.
	 * 
	 * @param outStream
	 *            Output Stream to which the jpeg image should be written
	 * @param op
	 *            Specifies the transformation like NONE, ROT_90 etc
	 * @param options
	 *            Options specifies how to manage exif or other header content
	 *            including embedded thumbnail transformation and also the
	 *            header information to write. Please pass a bitwise OR (|) of
	 *            the required set of OPT_XFORM_.. and OPT_WRITE_.. flags.
	 * @param bounds
	 *            Crop bounds. This must be passed if op is CROP, ignored
	 *            otherwise. The x and y values of bounds must be within the
	 *            image and are adjusted to the closest MCU boundary. The width
	 *            and height must be positive, but are automatically limited to
	 *            imageWidth-bounds.x and imageHieght-bounds.y respectively. The
	 *            bounds Object passed is unchanged.
	 * @param restart_interval
	 *            Specifies to write a restart marker every restart_interval MCU
	 *            block. No restart markers are written if this parameter is
	 *            passed as 0
	 * @return An instance of IterativeWriter. calls to the nextWrite method of
	 *         this would write the next few bytes of the image to outStream.
	 *         Though the return value is currently the LLJTran object(this) the
	 *         code should use the return value only since it is possible to
	 *         change it in future to support an LLJTran object writing in
	 *         multiple threads.
	 * 
	 * @see android.mediautil.generic.directio.InStreamFromIterativeWriter
	 *      InStreamFromIterativeWriter
	 */
	public IterativeWriter initWrite(OutputStream outStream, int op,
			int options, Rect bounds, int restart_interval) throws IOException {
		return initWrite(outStream, op, options, bounds, restart_interval,
				false);
	}

	/**
	 * This method is to be used for the Iterative version of the transform and
	 * save methods. The main use of this method is to get an IterativeWriter
	 * for use with InStreamFromIterativeWriter class to read the transformed
	 * image directly from LLJTran instead of writing it out.
	 * 
	 * @param outStream
	 *            Output Stream to which the jpeg image should be written
	 * @param op
	 *            Specifies the transformation like NONE, ROT_90 etc
	 * @param options
	 *            Options specifies how to manage exif or other header content
	 *            including embedded thumbnail transformation and also the
	 *            header information to write. Please pass a bitwise OR (|) of
	 *            the required set of OPT_XFORM_.. and OPT_WRITE_.. flags.
	 * @param bounds
	 *            Crop bounds. This must be passed if op is CROP, ignored
	 *            otherwise. The x and y values of bounds must be within the
	 *            image and are adjusted to the closest MCU boundary. The width
	 *            and height must be positive, but are automatically limited to
	 *            imageWidth-bounds.x and imageHieght-bounds.y respectively. The
	 *            bounds Object passed is unchanged.
	 * @param restart_interval
	 *            Specifies to write a restart marker every restart_interval MCU
	 *            block. No restart markers are written if this parameter is
	 *            passed as 0
	 * @param pullDownMode
	 *            If this parameter is true then memory is freed as the jpeg is
	 *            written making the LLJTran useless after the call. This option
	 *            can be used in case the Object reading the image allocates
	 *            memory in increments so that the maximum memory requirement
	 *            would come down which is not the case with java's ImageReader
	 *            which allocates the Buffered memory in one stroke. Also using
	 *            this option for operations which change the orientation by 90
	 *            degrees (ROT_90, ROT_270, TRANSPOSE and TRANSVERSE) is illegal
	 *            and results in a Runtime Exception.
	 * @return An instance of IterativeWriter. calls to the nextWrite method of
	 *         this would write the next few bytes of the image to outStream.
	 *         Though the return value is currently the LLJTran object(this) the
	 *         code should use the return value only since it is possible this
	 *         may change in future to support an LLJTran object writing in
	 *         multiple threads.
	 * 
	 * @see android.mediautil.generic.directio.InStreamFromIterativeWriter
	 *      InStreamFromIterativeWriter
	 */
	public IterativeWriter initWrite(OutputStream outStream, int op,
			int options, Rect bounds, int restart_interval, boolean pullDownMode)
			throws IOException {
		return initWrite(outStream, op, options, bounds, restart_interval,
				pullDownMode, null);
	}

	/**
	 * This internal method is used for the Iterative version of the transform
	 * and save methods. The main use of this method is to get an
	 * IterativeWriter for use with InStreamFromIterativeWriter class to read
	 * the transformed image directly from LLJTran instead of writing it out.
	 * 
	 * @param outStream
	 *            Output Stream to which the jpeg image should be written
	 * @param op
	 *            Specifies the transformation like NONE, ROT_90 etc
	 * @param options
	 *            Options specifies how to manage exif or other header content
	 *            including embedded thumbnail transformation and also the
	 *            header information to write. Please pass a bitwise OR (|) of
	 *            the required set of OPT_XFORM_.. and OPT_WRITE_.. flags.
	 * @param bounds
	 *            Crop bounds. This must be passed if op is CROP, ignored
	 *            otherwise. The x and y values of bounds must be within the
	 *            image and are adjusted to the closest MCU boundary. The width
	 *            and height must be positive, but are automatically limited to
	 *            imageWidth-bounds.x and imageHieght-bounds.y respectively. The
	 *            bounds Object passed is unchanged.
	 * @param restart_interval
	 *            Specifies to write a restart marker every restart_interval MCU
	 *            block. No restart markers are written if this parameter is
	 *            passed as 0
	 * @param pullDownMode
	 *            If this parameter is true then memory is freed as the jpeg is
	 *            written, making the LLJTran useless after the call. This
	 *            option can be used in case the Object reading the image
	 *            allocates memory in increments so that the maximum memory
	 *            requirement would come down which is not the case with java's
	 *            ImageReader which allocates the memory in one stroke. Also
	 *            using this option for operations which change the orientation
	 *            by 90 degrees (ROT_90, ROT_270, TRANSPOSE and TRANSVERSE) is
	 *            illegal and results in a Runtime Exception.
	 * @return An instance of IterativeWriter. calls to the nextWrite method of
	 *         this would write the next few bytes of the image to outStream.
	 *         Though the return value is currently the LLJTran object(this) the
	 *         code should use the return value only since it is possible this
	 *         may change in future to support an LLJTran object writing in
	 *         multiple threads.
	 * @param custom_appx
	 *            Class specifying type of custom marker. Not used in LLJTran.
	 * 
	 * @see android.mediautil.generic.directio.InStreamFromIterativeWriter
	 *      InStreamFromIterativeWriter
	 */
	protected IterativeWriter initWrite(OutputStream outStream, int op,
			int options, Rect bounds, int restart_interval,
			boolean pullDownMode, Class<?> custom_appx) throws IOException {
		prevHuffOption = -1;
		iWriteVars.maxWriteRequest = 0;
		iWriteVars.minWriteRequest = 100000000;
		iWriteVars.restoreVars = true;
		iWriteVars.saveAppxs = null;
		boolean writeAppxs = ((options & OPT_WRITE_APPXS) != 0);
		if (op == NONE || !writeAppxs)
			options &= ~OPT_XFORM_APPX;
		if ((options & OPT_XFORM_APPX) == 0)
			options &= ~OPT_XFORM_THUMBNAIL;
		if (readUpto < READ_ALL)
			throw new RuntimeException(
					"Transform cannot be performed since No Jpeg has been successfully Read");
		if (op == CROP)
			if (bounds != null)
				validateCropBounds(bounds);
			else
				new IllegalArgumentException("Crop boundaries are null.)");
		if (!appxs_read) {
			if ((options & OPT_XFORM_THUMBNAIL) != 0)
				if (Log.debugLevel >= Log.LEVEL_WARNING)
					android.util.Log
							.w(TAG,
									"Warning:transform: Thumbnail transformation cannot be performed since keep_appxs was passed as false while reading");
			if (writeAppxs)
				if (Log.debugLevel >= Log.LEVEL_WARNING)
					android.util.Log
							.w(TAG,
									"Warning:transform: Cannot write APPXS since keep_appxs was passed as false while reading");
		}
		if (op != NONE) {
			iWriteVars.svX = frm_x;
			iWriteVars.svY = frm_y;
			iWriteVars.svWidthMCU = widthMCU;
			iWriteVars.svHeightMCU = heightMCU;
			adjustImageParameters(op, options);
			switch (op) {
			case TRANSPOSE:
			case TRANSVERSE:
			case ROT_90:
			case ROT_270:
				transposeImageParameters();
				transposeQTable();
				break;
			case ROT_180:
			case FLIP_H:
			case FLIP_V:
			case NONE:
			default:
			}
			if ((options & OPT_XFORM_APPX) != 0 && appxs != null
					&& appHdrIndex >= 0) {
				iWriteVars.saveAppxs = appxs[appHdrIndex];
				transformAppHeader(op, options, false);
			}
		}
		return initWriteJpeg(outStream, op, null, options, custom_appx,
				restart_interval, pullDownMode);
	}

	/**
	 * This method should be called when using the initWrite method with
	 * nextWrite in writing out the jpeg image. It ensures that the state of the
	 * image is restored.
	 * 
	 * @param writer
	 *            The writer that was obtained from the initWriter call
	 */
	public void wrapupIterativeWrite(IterativeWriter writer) {
		// Restore Image details
		if (iWriteVars.state != IterativeWriteVars.WRITE_COMPLETE) {
			if (iWriteVars.restoreVars && iWriteVars.op != NONE) {
				switch (iWriteVars.op) {
				case TRANSPOSE:
				case TRANSVERSE:
				case ROT_90:
				case ROT_270:
					transposeImageParameters();
					transposeQTable();
					break;
				case ROT_180:
				case FLIP_H:
				case FLIP_V:
				case NONE:
				default:
				}
				if (iWriteVars.saveAppxs != null)
					appxs[appHdrIndex] = iWriteVars.saveAppxs;
				frm_x = iWriteVars.svX;
				frm_y = iWriteVars.svY;
				widthMCU = iWriteVars.svWidthMCU;
				heightMCU = iWriteVars.svHeightMCU;
			}
			iWriteVars.freeMemory();
			iWriteVars.state = IterativeWriteVars.WRITE_COMPLETE;
		}
	}

	/**
	 * Internal method which the current image after transforming it using
	 * supplied options and with restart_markers at the specified MCU interval.
	 * The transformation is applied only while saving the image. The image
	 * itself is restored to its original state on completion of the method.
	 * This method may be called multiple times. The jpeg image must be fully
	 * read by LLJTran first.
	 * <p>
	 * 
	 * Use this method for CROP operation.
	 * 
	 * @param outStream
	 *            Output Stream to which the jpeg image should be written
	 * @param op
	 *            Specifies the transformation like NONE, ROT_90 etc
	 * @param options
	 *            Options specifies how to manage exif or other header content
	 *            including embedded thumbnail transformation and also the
	 *            header information to write. Please pass a bitwise OR (|) of
	 *            the required set of OPT_XFORM_.. and OPT_WRITE_.. flags.
	 * @param bounds
	 *            Crop bounds. This must be passed if op is CROP, ignored
	 *            otherwise. The x and y values of bounds must be within the
	 *            image and are adjusted to the closest MCU boundary. The width
	 *            and height must be positive, but are automatically limited to
	 *            imageWidth-bounds.x and imageHieght-bounds.y respectively. The
	 *            bounds Object passed is unchanged.
	 * @param restart_interval
	 *            Specifies to write a restart marker every restart_interval MCU
	 *            block. No restart markers are written if this parameter is
	 *            passed as 0
	 * @param custom_appx
	 *            Class specifying type of custom marker. Not used in LLJTran.
	 */
	protected void transform(OutputStream outStream, int op, int options,
			Rect bounds, int restart_interval, Class<?> custom_appx)
			throws IOException {
		initWrite(outStream, op, options, bounds, restart_interval, false,
				custom_appx);
		do
			; while (nextWrite(10000000) == IterativeReader.CONTINUE);
	}

	private byte[] generateHuffTables(HuffGenerator huff) throws IOException {
		byte retVal[];

		if (huff != null) {
			ByteArrayOutputStream bs = new ByteArrayOutputStream();

			/* Write out huff info */
			huff.writeHuffTables(bs);

			data = bs.toByteArray();
		} else
			data = (byte[]) stdHuffTables;

		/* Re-initialize Hufftables */
		dc_valoffset = new int[0][0];
		dc_maxcode = new int[0][0];
		dc_huffval = new int[0][0];
		enc_dc_matrix = new int[0][][];
		dc_huffbits = new int[0][0];
		dc_ix = new int[0];

		ac_valoffset = new int[0][0];
		ac_maxcode = new int[0][0];
		ac_huffval = new int[0][0];
		enc_ac_matrix = new int[0][][];
		ac_huffbits = new int[0][0];
		ac_ix = new int[0];
		readDHT(null, data.length);
		retVal = data;
		data = null;
		return retVal;
	}

	/**
	 * Saves the current image using OPT_WRITE_ALL option. The jpeg image must
	 * be fully read by LLJTran first and can then be transformed zero or more
	 * times using the transform.
	 * 
	 * @param os
	 *            Output Stream to which the jpeg image should be written
	 * @see #transform(int)
	 */
	public void save(OutputStream os) throws IOException {
		save(os, OPT_DEFAULTS, 0);
	}

	/**
	 * Saves the current image using supplied options. The jpeg image must be
	 * fully read by LLJTran first and can then be transformed zero or more
	 * times using the transform.
	 * 
	 * @param os
	 *            Output Stream to which the jpeg image should be written
	 * @param options
	 *            Options specifies the header information to write. Please pass
	 *            a bitwise OR (|) of the required set of OPT_WRITE_.. flags.
	 * @see #transform(int)
	 */
	public void save(OutputStream os, int options) throws IOException {
		save(os, options, 0);
	}

	/**
	 * Saves the current image using supplied options and with restart_markers
	 * at the specified MCU interval. The jpeg image must be fully read by
	 * LLJTran first and can then be transformed zero or more times using the
	 * transform.
	 * 
	 * @param os
	 *            Output Stream to which the jpeg image should be written
	 * @param options
	 *            Options specifies the header information to write. Please pass
	 *            a bitwise OR (|) of the required set of OPT_WRITE_.. flags.
	 * @param restart_interval
	 *            Specifies to write a restart marker every restart_interval MCU
	 *            block. No restart markers are written if this parameter is
	 *            passed as 0
	 */
	public void save(OutputStream os, int options, int restart_interval)
			throws IOException {
		boolean writeAppxs = ((options & OPT_WRITE_APPXS) != 0);
		if (readUpto < READ_ALL)
			throw new RuntimeException(
					"Jpeg cannot be written since No Jpeg has been successfully Read");
		if (writeAppxs && !appxs_read)
			if (Log.debugLevel >= Log.LEVEL_WARNING)
				android.util.Log
						.w(TAG,
								"Warning:save: Cannot write APPXS since keep_appxs was passed as false while reading");
		IterativeWriter iWriter = initWrite(os, NONE, options, null,
				restart_interval);
		do
			; while (iWriter.nextWrite(10000000) == IterativeReader.CONTINUE);
	}

	/**
	 * Saves the Thumbnail image in the Image Header Information (Exif).
	 * 
	 * @param out
	 *            Output Stream to save the Thumbnail. This is not flushed or
	 *            closed.
	 * @return Number of bytes written. This will be zero if Image Header
	 *         Information is not present or was not read during read.
	 */
	public int writeThumbnail(OutputStream out) throws IOException {
		int retVal = 0;
		int offset, len;
		if (appxs != null && appHdrIndex >= 0 && imageinfo != null
				&& (offset = imageinfo.getThumbnailOffset()) > 0
				&& (len = imageinfo.getThumbnailLength()) > 0) {
			out.write(appxs[appHdrIndex], offset + 4, len);
			retVal = len;
		}

		return retVal;
	}

	/**
	 * Provides an InputStream to read the Thumbnail Information.
	 * 
	 * @return InputStream to read the Thumbnail from. Null if there is not
	 *         Thumbnail information. Caller can close the returned Stream.
	 */
	public InputStream getThumbnailAsStream() {
		ByteArrayInputStream retVal = null;
		int offset, len;
		if (appxs != null && appHdrIndex >= 0 && imageinfo != null
				&& (offset = imageinfo.getThumbnailOffset()) > 0
				&& (len = imageinfo.getThumbnailLength()) > 0)
			retVal = new ByteArrayInputStream(appxs[appHdrIndex], offset + 4,
					len);

		return retVal;
	}

	/**
	 * Refreshes the appx information array corresponding to any changes done in
	 * imageinfo. This method is to be used only if making any changes to the
	 * Image Information Header (Exif) apart from changing the Thumbnail.
	 * Currently only Exif is supported.
	 * 
	 * @return True on success, false otherwise
	 * @see #getImageInfo()
	 * @see Exif
	 */
	public boolean refreshAppx() {
		boolean retVal = false;
		if (imageinfo != null)
			retVal = transformAppHeader(NONE, 0, true);
		return retVal;
	}

	private boolean partialYMCU;
	private boolean partialXMCU;

	private void adjustImageParameters(int op, int options) {
		int xBoundary = getMCUWidth();
		int yBoundary = getMCUHeight();
		if (op == CROP) {
			frm_x = cropBounds.width();
			frm_y = cropBounds.height();
			widthMCU = (frm_x + xBoundary - 1) / xBoundary;
			heightMCU = (frm_y + yBoundary - 1) / yBoundary;
		}
		int yMCURem = frm_y % yBoundary;
		int xMCURem = frm_x % xBoundary;
		partialYMCU = yMCURem != 0;
		partialXMCU = xMCURem != 0;
		if ((options & OPT_XFORM_TRIM) != 0) {
			if (partialYMCU
					&& (op == ROT_90 || op == TRANSVERSE || op == ROT_180 || op == FLIP_V)
					&& heightMCU > 1) {
				partialYMCU = false;
				frm_y -= yMCURem;
				heightMCU--;
			}

			if (partialXMCU
					&& (op == ROT_270 || op == TRANSVERSE || op == ROT_180 || op == FLIP_H)
					&& widthMCU > 1) {
				partialXMCU = false;
				frm_x -= xMCURem;
				widthMCU--;
			}
		}
	}

	/**
	 * This internal method is used for saving the image after applying the
	 * given transformation. The image is restored to the original state after
	 * the completion of the method.
	 * 
	 * @param os
	 *            Output Stream to which the jpeg image should be written
	 * @param op
	 *            Specifies the transformation like NONE, ROT_90 etc
	 * @param comment
	 *            Jpeg comment to Save. If null no comment will be written. If
	 *            "" existing comment will be retained
	 * @param options
	 *            Options specifies how to manage exif or other header content
	 *            including embedded thumbnail transformation and also the
	 *            header information to write. Please pass a bitwise OR (|) of
	 *            the required set of OPT_XFORM_.. and OPT_WRITE_.. flags.
	 * @param bounds
	 *            Crop bounds. This must be passed if op is CROP, ignored
	 *            otherwise. The x and y values of bounds must be within the
	 *            image and are adjusted to the closest MCU boundary. The width
	 *            and height must be positive, but are automatically limited to
	 *            imageWidth-bounds.x and imageHieght-bounds.y respectively. The
	 *            bounds Object passed is unchanged.
	 * @param custom_appx
	 *            Class specifying type of custom marker. Not used in LLJTran.
	 * @param restart_interval
	 *            Specifies to write a restart marker every restart_interval MCU
	 *            block. No restart markers are written if this parameter is
	 *            passed as 0
	 * @param pullDownMode
	 *            If this parameter is true then memory is freed as the jpeg is
	 *            written making the LLJTran useless after the call. This option
	 *            can be used in case the Object reading the image allocates
	 *            memory in increments so that the maximum memory requirement
	 *            would come down which is not the case with java's ImageReader
	 *            which allocates the Buffered memory in one stroke. Also using
	 *            this option for operations which change the orientation by 90
	 *            degrees (ROT_90, ROT_270, TRANSPOSE and TRANSVERSE) is illegal
	 *            and results in a Runtime Exception.
	 */
	protected void writeJpeg(OutputStream os, int op, String comment,
			int options, Rect bounds, Class<?> custom_appx,
			int restart_interval, boolean pullDownMode) throws IOException {
		if (op == CROP)
			validateCropBounds(bounds);
		initWriteJpeg(os, op, comment, options, custom_appx, restart_interval,
				pullDownMode);
		iWriteVars.restoreVars = false;
		do
			; while (nextWrite(10000000) == IterativeReader.CONTINUE);
		if (Log.debugLevel >= Log.LEVEL_INFO)
			android.util.Log.i(TAG, "0x" + Integer.toHexString(writecounter)
					+ "(" + writecounter + ") byte(s) Written Successfully");
	}

	// If comment is null no comment will be written. If "" then existing
	// comment data if any will be written, else comment will be written.
	private IterativeWriter initWriteJpeg(OutputStream os, int op,
			String comment, int options, Class<?> custom_appx,
			int restart_interval, boolean pullDownMode) throws IOException {
		if (pullDownMode
				&& (op == ROT_90 || op == ROT_270 || op == TRANSPOSE || op == TRANSVERSE))
			throw new RuntimeException(
					"PullDownMode not allowed for Vertical<->Horizontal transform: "
							+ op);
		writecounter = 0;
		HuffGenerator lHuffGen = null;
		byte optimizeHuff = (byte) ((options & OPT_WRITE_OPTIMIZE_HUFF) == 0 ? 0
				: 1);
		byte huffTables[] = null;
		iWriteVars.os = os;
		iWriteVars.op = op;
		iWriteVars.comment = comment;
		iWriteVars.options = options;
		iWriteVars.custom_appx = custom_appx;
		iWriteVars.restart_interval = restart_interval;
		iWriteVars.pullDownMode = pullDownMode;
		if (canBeProcessed) {
			if (prevHuffOption != optimizeHuff) {
				prevHuffOption = optimizeHuff;
				if (optimizeHuff != 0) {
					// Huffman tables are recalculated since there may be new
					// coeffs
					// not present in the exisint Huffman Tables.
					if (huffGen == null)
						huffGen = new HuffGenerator();
					huffGen.init();
					gatheringStats = true;
					// Dry Run to get frequency counts of DC/AC symbols to be
					// encoded
					// No output is written. The transforming routines like
					// rotate90DCT called by writeDCT also honour gatheringStats
					// variable and do not actually modify the DCT array.
					try {
						writeDCT(null, op, options, 0, false);
					} catch (IOException e) {
						if (Log.debugLevel >= Log.LEVEL_ERROR) {
							android.util.Log.e(
									TAG,
									"Totally Unexpected IOException: "
											+ e.getMessage());
							e.printStackTrace();
						}
					}
					// Disable Stat gathering and enabling writeDCT to write
					// output
					gatheringStats = false;
					lHuffGen = huffGen;
				}
				huffTables = generateHuffTables(lHuffGen); // Load the new
															// Huffman tables
				if (lHuffGen != null)
					lHuffGen.freeMemory();
			}
		}
		iWriteVars.huffTables = huffTables;
		iWriteVars.state = IterativeWriteVars.WRITE_BEGIN;
		return this;
	}

	/**
	 * Writes atleast the next numBytes bytes. initWrite should have been called
	 * before. Usually this method will be called via the IterativeWriter
	 * interface and need not be called directly on an LLJTran object. 750 bytes
	 * is a good value of the minimum number of bytes that would be written and
	 * also for the writeCushion by which the actual number of bytes written may
	 * exceed numBytes. The application appx markers are split while writing to
	 * take care of the numBytes passed but not the comments. So long jpeg
	 * comments may cause a large number of bytes to be written.
	 * 
	 * @see #initWrite(OutputStream,int,int,Rect,int)
	 * @see android.mediautil.generic.directio.InStreamFromIterativeWriter
	 *      InStreamFromIterativeWriter
	 * @return IterativeReader.CONTINUE in case the writing is not complete.
	 *         Returns IterativeReader.STOP on completion of write.
	 * @exception IOException
	 *                in case of any errors.
	 */
	@Override
	public int nextWrite(int numBytes) throws IOException {
		if (iWriteVars.state == IterativeWriteVars.WRITE_COMPLETE)
			throw new IllegalStateException(
					"nextWrite Called without initialization or after write completion");

		if (numBytes > iWriteVars.maxWriteRequest)
			iWriteVars.maxWriteRequest = numBytes;
		if (numBytes < iWriteVars.minWriteRequest)
			iWriteVars.minWriteRequest = numBytes;

		OutputStream os = iWriteVars.os;
		int op = iWriteVars.op;
		int options = iWriteVars.options;
		int restart_interval = iWriteVars.restart_interval;
		int nextState = iWriteVars.state;
		int markCounter = writecounter;
		int remaining = numBytes;
		do {
			switch (nextState) {
			case IterativeWriteVars.WRITE_BEGIN:
				writeMarkerSOI(os);
				nextState = IterativeWriteVars.WRITE_COMMENTS;
				if ((options & OPT_WRITE_APPXS) != 0) {
					nextState = IterativeWriteVars.WRITE_APPXS;
					writeNewMarker(os, iWriteVars.custom_appx);
					initWriteMarkerAppXs();
					break;
				}
				break;
			case IterativeWriteVars.WRITE_APPXS:
				// write format specific marker
				if (!writeNextMarkerAppXs(remaining))
					nextState = IterativeWriteVars.WRITE_COMMENTS;
				break;
			case IterativeWriteVars.WRITE_COMMENTS:
				nextState = IterativeWriteVars.WRITE_DQT;
				if ((options & OPT_WRITE_COMMENTS) != 0) {
					if (iWriteVars.comment == null
							|| iWriteVars.comment.length() == 0)
						writeMarkerComment(os, out_comment, enc);
					else
						writeMarkerComment(os, iWriteVars.comment, enc);
					break;
				}
				// Else fallthrough to next state
			case IterativeWriteVars.WRITE_DQT:
				writeMarkerDQT(os);
				nextState = IterativeWriteVars.WRITE_DHT;
				break;
			case IterativeWriteVars.WRITE_DHT:
				writeMarkerDHT(os, iWriteVars.huffTables);
				writeMarkerDRI(os, restart_interval);
				nextState = canBeProcessed ? IterativeWriteVars.WRITE_START
						: IterativeWriteVars.WRITE_COMPLETE;
				break;
			case IterativeWriteVars.WRITE_START:
				writeMarkerSOF0(os);
				writeMarkerSOS(os);
				initWriteDCT(os, op, options, restart_interval, false);
				nextState = IterativeWriteVars.WRITE_DCT;
				break;
			case IterativeWriteVars.WRITE_DCT:
				if (!writeNextDCT(remaining)) {
					writeMarkerEOI(os);
					nextState = IterativeWriteVars.WRITE_COMPLETE;
				}
				break;
			}
			remaining = numBytes - (writecounter - markCounter);
		} while (remaining > 0
				&& nextState != IterativeWriteVars.WRITE_COMPLETE);

		int retVal = IterativeReader.CONTINUE;
		if (nextState == IterativeWriteVars.WRITE_COMPLETE) {
			if (iWriteVars.pullDownMode)
				freeMemory();
			else
				wrapupIterativeWrite(this);
			retVal = IterativeReader.STOP;
		}

		iWriteVars.state = nextState;

		return retVal;
	}

	/**
	 * This method can be called at anytime the LLJTran object is not required.
	 * Frees internal structures for Garbage collection by setting them to null
	 * and also closes any open Internal Input Stream.
	 */
	public void freeMemory() {
		dct_coefs = null;
		dc_valoffset = null;
		dc_maxcode = null;
		dc_huffval = null;
		enc_dc_matrix = null;
		dc_huffbits = null;
		dc_ix = null;

		ac_valoffset = null;
		ac_maxcode = null;
		ac_huffval = null;
		enc_ac_matrix = null;
		ac_huffbits = null;
		ac_ix = null;
		q_table = null;
		q_ix = null;
		q_prec = null;
		// free extra storage for
		appxs = null;
		appHdrIndex = -1;
		encoder = null;
		readUpto = READ_NONE;
		closeInternalInputStream();
	}

	private void allocateTables() {
		// empty table creation
		dc_valoffset = new int[0][0];
		dc_maxcode = new int[0][0];
		dc_huffval = new int[0][0];
		enc_dc_matrix = new int[0][][];
		dc_huffbits = new int[0][0];
		dc_ix = new int[0];

		ac_valoffset = new int[0][0];
		ac_maxcode = new int[0][0];
		ac_huffval = new int[0][0];
		enc_ac_matrix = new int[0][][];
		ac_huffbits = new int[0][0];
		ac_ix = new int[0];
		q_table = new int[0][0];
		q_ix = new int[0];
		q_prec = new int[0];
	}

	/**
	 * Reads the Image from the File or InputStream specified in the
	 * Constructor.
	 * 
	 * @param keep_appxs
	 *            Specifies if Image Information Header (Exif) is to be
	 *            retained. This should be passed as true if it is later
	 *            required to be written out. Note that if this is passed as
	 *            false existing Image Information Header is unchanged.
	 * @exception LLJTranException
	 *                If a fatal error is encountered.
	 */
	public void read(boolean keep_appxs) throws LLJTranException {
		read(READ_ALL, keep_appxs);
	}

	private static final int APPXS_NONE = 0;
	private static final int APPXS_JFIF = 1;
	private static final int APPXS_JFXX = 2;
	private static final int APPXS_EXIF = 3;
	private static final int APPXS_CIFF = 4;
	private static final int APPXS_FPXR = 4;

	private int processAppMarker(byte markerData[], int offset,
			AbstractImageInfo<?> imageinfo[], boolean retHandledAppHdr[])
			throws FileFormatException {
		boolean handledAppHdr = false;
		int markerType = APPXS_NONE;
		if (isSignature(markerData, offset, JFIF)) {
			markerType = APPXS_JFIF;
			// x'FF', APP0, length, identifier, version,
			// units, Xdensity, Ydensity, Xthumbnail, Ythumbnail, (RGB)n
			// int version = bs2i(5, 2);
			// int units = bs2i(7, 1);
			// int xden = bs2i(8, 2);
			// int yden = bs2i(10, 2);
			int x = bs2i(12, 1);
			int y = bs2i(13, 1);
			// int thumbnailsize = 3*x*y;
			if (x > 0 && y > 0)
				if (Log.debugLevel >= Log.LEVEL_INFO)
					android.util.Log.i(TAG, "Thumbnail " + x + "x" + y
							+ " in APP0");
		} else if (isSignature(markerData, offset, JFXX.FORMAT)) {
			markerType = APPXS_JFXX;
			handledAppHdr = true;
		} else if (isSignature(markerData, offset, Exif.FORMAT)) {
			markerType = APPXS_EXIF;
			handledAppHdr = true;
		} else if ((isSignature(markerData, offset, CIFF.II) || isSignature(
				markerData, offset, CIFF.MM))
				&& isSignature(markerData, offset + 6, CIFF.FORMAT)) {
			markerType = APPXS_CIFF;
			handledAppHdr = true;
		} else if (isSignature(markerData, offset, FPXR))
			markerType = APPXS_FPXR;

		if (retHandledAppHdr != null)
			retHandledAppHdr[0] = handledAppHdr;

		if (imageinfo != null && handledAppHdr) {
			if (offset != 0) {
				// Create 0 offset based array since that is what ImageInfo
				// constructors expect.
				byte newData[] = new byte[markerData.length - offset];
				System.arraycopy(markerData, offset, newData, 0,
						markerData.length - offset);
				markerData = newData;
			}

			// TODO: Do not know if this is ok. Have tried only Exif which
			// does not use InputStream is
			ByteArrayInputStream is = new ByteArrayInputStream(markerData);

			switch (markerType) {
			case APPXS_JFXX:
				imageinfo[0] = new JFXX(is, markerData, readcounter, this);
				break;
			case APPXS_EXIF:
				imageinfo[0] = new Exif(is, markerData, readcounter, this);
				break;
			case APPXS_CIFF:
				imageinfo[0] = new CIFF(is, markerData, readcounter, this);
				break;
			default:
				break;
			}
		}

		return markerType;
	}

	private String initReadInternal(int sections, boolean keep_appxs,
			boolean throwException) {

		// Needs to be set before return since it is used in finally block
		String retVal = null;
		sections &= ALL_SECTIONS;

		iReadVars.maxReadRequest = 0;
		iReadVars.minReadRequest = 100000000;
		iReadVars.sections = sections;
		iReadVars.keep_appxs = keep_appxs;
		iReadVars.throwException = throwException;
		iReadVars.appxsCleared = false;
		appxs_read = keep_appxs;

		if (unprocessed_marker == 0 && (sections & HEADER_SECTION) == 0
				&& (sections & BODY_SECTION) != 0)
			retVal = "Attempt to Read only the Body section when Header has not been successfully read";

		if (out_comment == null)
			out_comment = "";
		// comment_data = null;
		if (file != null)
			valid = file.isFile();
		else
			valid = inStream != null;
		if (retVal == null && !valid)
			retVal = "Invalid input: " + getName()
					+ (file != null ? " (" + file + ")" : " stream null");

		if (xferDone)
			retVal = "Cannot Read Further, Input has been Transferred using xferInfo()";

		if (retVal == null) {
			InputStream is = currentStream;
			if (is == null)
				is = createInputStream();
			iReadVars.is = is;
			if (valid) {
				currentStream = is;
				valid = false;
				canBeProcessed = true; // 22
			} else
				retVal = "Unable to Read from input: " + getName();
		}

		iReadVars.stage = IterativeReadVars.READING_STAGE;

		return retVal;
	}

	/**
	 * This method is to be used for the Iterative version of the read method.
	 * The main use of this method is to get an IterativeReader for use with
	 * SplitInputStream and OutStreamToIterativeReader classes to Share the
	 * Image File with another Object while reading or to save an image from
	 * another Object (Say BufferedImage) directly to an LLJTran Object thus
	 * reducing disk io.
	 * 
	 * @param readUpto
	 *            The section upto which to read. You can pass READ_ALL to read
	 *            the entire image or READ_INFO to read only the Image Header
	 *            Information (Exif) to modify and rewrite using
	 *            {@link #xferInfo(InputStream, OutputStream, int, int)
	 *            xferInfo(..)} or READ_HEADER to read the header information
	 *            and decoding tables but not the image data. You can make
	 *            subsequent calls to read/initRead,nextReads with READ_ALL to
	 *            complete reading the image provided you are not reading using
	 *            directio classes like SplitInputStream.
	 * @param keep_appxs
	 *            Specifies if Image Information Header (Exif) is to be
	 *            retained. This should be passed as true if it is later
	 *            required to be written out. Note that if this is passed as
	 *            false existing Image Information Header is unchanged.
	 * @param throwException
	 *            Specifies if an exception is to be thrown on encountering an
	 *            error during a call of nextRead(). If this parameter is passed
	 *            as true then an IOException is thrown which gets propogated.
	 *            This option is useful for use with OutStreamToIterativeReader
	 *            where there is no point in continuing to write without anybody
	 *            to read. If this parameter is passed as false then then on
	 *            encountering an error the nextRead() call sets the error
	 *            message and returns IterativeReader.STOP. The error can be
	 *            retrieved later using getErrorMsg() call. This option may be
	 *            useful for use with SplitInputStream where the main Reader can
	 *            continue to read data even if the LLJTran subReader errors
	 *            out.
	 * @exception LLJTranException
	 *                If there is an error in the readUpto parameter passed.
	 * 
	 * @see IterativeReader
	 * @see android.mediautil.generic.directio.SplitInputStream SplitInputStream
	 * @see android.mediautil.generic.directio.OutStreamToIterativeReader
	 *      OutStreamToIterativeReader
	 */
	public void initRead(int readUpto, boolean keep_appxs,
			boolean throwException) throws LLJTranException {
		String msg = null;

		if (readUpto < READ_INFO || readUpto > READ_ALL)
			msg = "Error:read: Invalid value " + readUpto
					+ " for parameter readUpto";
		else if (readUpto <= this.readUpto) {
			if (Log.debugLevel >= Log.LEVEL_WARNING)
				android.util.Log.w(TAG, "Warning:initRead: Have already read "
						+ uptoName[this.readUpto]
						+ ", Exiting without doing anything");
		} else if (unprocessedError != null)
			msg = unprocessedError;
		else {
			int sections = 0;
			if (this.readUpto < READ_INFO)
				sections = INFO_SECTION;
			if (readUpto >= READ_HEADER) {
				if (this.readUpto < READ_HEADER)
					sections |= HEADER_SECTION;
				if (readUpto >= READ_ALL)
					sections |= BODY_SECTION;
			}
			iReadVars.readUpto = readUpto;
			msg = initReadInternal(sections, keep_appxs, throwException);
			if (msg != null)
				msg = "Error:initRead: " + msg;
		}

		if (msg != null)
			throw new LLJTranException(msg);
	}

	private void wrapupIterativeRead() {
		this.readUpto = iReadVars.readUpto;
	}

	/**
	 * Reads the Image from the File or InputStream specified in the
	 * Constructor.
	 * 
	 * @param readUpto
	 *            The section upto which to read. You can pass READ_ALL to read
	 *            the entire image or READ_INFO to read only the Image Header
	 *            Information (Exif) to modify and rewrite using
	 *            {@link #xferInfo(InputStream, OutputStream, int, int)
	 *            xferInfo(..)} or READ_HEADER to read the header information
	 *            and decoding tables but not the image data. You can make
	 *            subsequent calls to read/initRead,nextReads with READ_ALL to
	 *            complete reading the image provided you are not reading using
	 *            directio classes like SplitInputStream.
	 * @param keep_appxs
	 *            Specifies if Image Information Header (Exif) is to be
	 *            retained. This should be passed as true if it is later
	 *            required to be written out. Note that if this is passed as
	 *            false existing Image Information Header is unchanged.
	 */
	public void read(int readUpto, boolean keep_appxs) throws LLJTranException {
		initRead(readUpto, keep_appxs, false);
		try {
			while (nextRead(10000000) == IterativeReader.CONTINUE)
				;
		} catch (IOException e) {
		} // Do not expect an IOException since last parameter to initRead is
			// false.

		String msg = getErrorMsg();

		if (msg != null)
			throw new LLJTranException(msg);
	}

	/**
	 * Returns upto what stage the image has been read.
	 * 
	 * @return One of the READ_.. flags including READ_NONE if nothing has been
	 *         read successfully.
	 */
	public int getReadUpto() {
		return readUpto;
	}

	/**
	 * Reads atleast the next numBytes bytes. initRead should have been called
	 * before. Usually this method will be called via the IterativeReader
	 * interface. 750 bytes is a good value of the minimum number of bytes that
	 * would be read and also for the readCushion by which the actual number of
	 * bytes read may exceed numBytes. The application appx markers are split
	 * while reading to take care of the numBytes passed but not the comments.
	 * So long jpeg comments may cause a large number of bytes to be read.
	 * 
	 * @return IterativeReader.CONTINUE in case the reading is not complete.
	 *         Returns IterativeReader.STOP on completion of read or in case of
	 *         an error. getErrorMsg() should be called to check for errors.
	 * @exception IOException
	 *                If there is an error and throwException was initialized as
	 *                true.
	 * @see #initRead(int,boolean,boolean)
	 * @see android.mediautil.generic.directio.SplitInputStream SplitInputStream
	 * @see android.mediautil.generic.directio.OutStreamToIterativeReader
	 *      OutStreamToIterativeReader
	 */
	@Override
	public int nextRead(int numBytes) throws IOException {
		// Needs to be set before return since it is used in finally block
		InputStream is = iReadVars.is;
		boolean keep_appxs = iReadVars.keep_appxs;
		int sections = iReadVars.sections;
		String msg = null;
		int len;
		int retVal = IterativeReader.CONTINUE;
		int remaining = numBytes, markCounter;
		int stage = iReadVars.stage;

		if (numBytes > iReadVars.maxReadRequest)
			iReadVars.maxReadRequest = numBytes;
		if (numBytes < iReadVars.minReadRequest)
			iReadVars.minReadRequest = numBytes;

		markCounter = readcounter;
		try {
			markers: do {
				if (sections == 0)
					break markers;

				if (unprocessedError != null) {
					msg = unprocessedError;
					break markers;
				}

				if (stage < IterativeReadVars.READING_STAGE
						|| stage >= IterativeReadVars.DONE_STAGE) {
					msg = "read: nextRead called without initRead or called after completion of read";
					break markers;
				}

				if (stage == IterativeReadVars.READING_DCT_STAGE)
					if (readNextDCT(remaining))
						continue;
					else
						stage = IterativeReadVars.IMAGE_READ_STAGE;

				if (stage == IterativeReadVars.READING_APPX_STAGE)
					if (readNextAppx(remaining))
						continue;
					else {
						stage = IterativeReadVars.READING_STAGE;
						if (keep_appxs) {
							// In LLJTran ImageInfo is loaded only if
							// keep_appxs is set. This is incompatible with
							// BasicJpeg reading info section and appxs data
							// separately.
							addAppx();

							boolean handledAppHdr[] = new boolean[1];
							if (sections == INFO_SECTION)
								valid = true;
							if (Log.debugLevel >= Log.LEVEL_DEBUG)
								android.util.Log.d(TAG, "Signature "
										+ new String(data, 0, 4));
							AbstractImageInfo<?> curImageInfo[] = null;
							if ((sections & INFO_SECTION) != 0)
								curImageInfo = new AbstractImageInfo[1];
							int markerType = processAppMarker(data, 4,
									curImageInfo, handledAppHdr);
							if (markerType == APPXS_NONE && imageinfo == null) {
								len = data.length;
								if (Log.debugLevel >= Log.LEVEL_DEBUG)
									android.util.Log
											.d(TAG,
													"unhandled APP marker "
															+ Integer
																	.toHexString(markerid[1])
															+ " length "
															+ len
															+ " data "
															+ new String(data,
																	0, len));
							}

							if (curImageInfo != null && curImageInfo[0] != null)
								imageinfo = curImageInfo[0];

							if (handledAppHdr[0] && imageinfo != null
									&& keep_appxs)
								appHdrIndex = appxs.length - 1;
							if (Log.debugLevel >= Log.LEVEL_DEBUG)
								android.util.Log.d(TAG, "Image info "
										+ imageinfo);
						}
					}

				if (unprocessed_marker == 0) {
					if (is.read(markerid) != markerid.length) {
						// Wrong length read for marker header
						msg = "Unexpected End Of Input";
						break markers;
					}
					readcounter += markerid.length;
				} else {
					markerid[0] = M_PRX;
					markerid[1] = (byte) unprocessed_marker;
					unprocessed_marker = 0;
				}

				if (stage == IterativeReadVars.IMAGE_READ_STAGE
						&& (markerid[0] != M_PRX || markerid[1] != M_EOI)) {
					valid = true;
					stage = IterativeReadVars.DONE_STAGE;
					if (Log.debugLevel >= Log.LEVEL_WARNING)
						android.util.Log.w(TAG, "Warning:read: Found Bytes 0x"
								+ Integer.toHexString(markerid[0]) + ", 0x"
								+ Integer.toHexString(markerid[1])
								+ " instead of EOI, Ignoring remaining input");
					break markers;
				}

				if (markerid[0] != M_PRX) {
					// Wrong start signature markerid[0]
					if (readcounter == markerid.length) {
						// try TIFF
						intel = markerid[0] == 'I' && markerid[1] == 'I';
						motorola = markerid[0] == 'M' && markerid[1] == 'M';
						if (intel || motorola) {
							data = new byte[6];
							if (is.read(data) == data.length
									&& ((intel && data[0] == 42 && data[1] == 0) || (motorola
											&& data[1] == 42 && data[0] == 0))) {
								readcounter += data.length;
								imageinfo = new TiffExif(is, data, readcounter,
										intel, this);
							}
						} else if (markerid[0] == Flashpix.SIGNATURE[0]
								&& markerid[1] == Flashpix.SIGNATURE[1]) { // try
																			// Flashpix
							data = new byte[6];
							if (is.read(data) == data.length
									&& data[0] == Flashpix.SIGNATURE[2]
									&& data[1] == Flashpix.SIGNATURE[3]
									&& data[2] == Flashpix.SIGNATURE[4]
									&& data[3] == Flashpix.SIGNATURE[5]
									&& data[4] == Flashpix.SIGNATURE[6]
									&& data[5] == Flashpix.SIGNATURE[7]) {
								readcounter += data.length;
								// disable flashpix
								/*
								 * try { imageinfo = new Flashpix(is, data,
								 * readcounter, getName()); }
								 * catch(FileFormatException ffe) { }
								 */
							}
						}
					}
					valid = imageinfo != null;
					canBeProcessed = false;
					msg = "Not a Jpeg File";
					break markers;
				}
				byte markercode = markerid[1];
				switch (markercode) {
				case M_SOI:
					// proceed to next marker
					allocateTables();
					break;
				case M_APP0:
				case M_APP0 + 1:
				case M_APP0 + 2:
				case M_APP0 + 3:
				case M_APP0 + 4:
				case M_APP0 + 5:
				case M_APP0 + 6:
				case M_APP0 + 7:
				case M_APP0 + 8:
				case M_APP0 + 9:
				case M_APP0 + 10:
				case M_APP0 + 11:
				case M_APP12:
				case M_APP12 + 1:
				case M_APP12 + 2:
				case M_APP12 + 3:
					// application specific marker found, just skip it
					initReadAppx(markercode);
					stage = IterativeReadVars.READING_APPX_STAGE;
					break;
				case M_DQT:
					if (sections == INFO_SECTION) {
						valid = true;
						data = markerid;
						unprocessed_marker = markercode;
						stage = IterativeReadVars.DONE_STAGE;
						break markers;
					}
					len = readMarker(is);
					int[] wt1d,
					wt2d[];
					int pos = 0;
					int lim;
					while (pos < len) {
						int tabnum = q_prec.length;
						wt1d = new int[tabnum + 1];
						System.arraycopy(q_ix, 0, wt1d, 0, tabnum);
						q_ix = wt1d;
						q_ix[tabnum] = data[pos] & 15;
						wt1d = new int[tabnum + 1];
						System.arraycopy(q_prec, 0, wt1d, 0, tabnum);
						q_prec = wt1d;
						q_prec[tabnum] = ((data[pos++] >> 4) & 15) == 0 ? 8
								: 16;
						wt2d = new int[tabnum + 1][DCTSIZE2];
						System.arraycopy(q_table, 0, wt2d, 0, tabnum);
						q_table = wt2d;
						lim = pos + DCTSIZE2;
						if (lim > len)
							lim = len;
						for (int i = 0; pos < lim; i++)
							q_table[tabnum][i] = data[pos++] & 255;
					}
					break;
				case M_DHT:
					if (sections == INFO_SECTION) {
						valid = true;
						data = markerid;
						unprocessed_marker = markercode;
						stage = IterativeReadVars.DONE_STAGE;
						break markers;
					}
					len = readDHT(is, 0);
					break;
				case M_SOF0:
				case M_SOF1:
					if (sections == INFO_SECTION) {
						valid = true;
						data = markerid;
						if (Log.debugLevel >= Log.LEVEL_INFO)
							android.util.Log.i(TAG, "Abandoned M_SOF0 "
									+ M_SOF0 + "   " + markerid[1]);
						unprocessed_marker = markercode;
						stage = IterativeReadVars.DONE_STAGE;
						break markers;
					}
					len = readMarker(is);
					frm_precision = data[0] & 255;
					frm_x = bs2i(3, 2);
					frm_y = bs2i(1, 2);
					components_in_frame = data[5] & 255;
					if (Log.debugLevel >= Log.LEVEL_INFO) {
						android.util.Log.i(TAG, "Frame, precision "
								+ frm_precision);
						System.out.println("X= " + frm_x + ", Y= " + frm_y);
						System.out.println("Components " + components_in_frame
								+ " (" + getLocationName() + ")");
					}
					V = new int[components_in_frame];
					H = new int[components_in_frame];
					QT = new int[components_in_frame];
					ID = new int[components_in_frame];
					pos = 6;
					maxHi = maxVi = 0;
					int sampling = 0;
					mcusize = 0;
					for (int i = 0; i < components_in_frame; i++) {
						ID[i] = data[pos++] & 255;
						sampling = ((sampling << 8) + (data[pos] & 255));
						H[i] = (data[pos] >> 4) & 15;
						if (H[i] > maxHi)
							maxHi = H[i];
						V[i] = data[pos++] & 15;
						if (V[i] > maxVi)
							maxVi = V[i];
						mcusize += H[i] * V[i];
						QT[i] = data[pos++] & 255;
					}
					widthMCU = (frm_x + DCTSIZE * maxHi - 1)
							/ (DCTSIZE * maxHi);
					heightMCU = (frm_y + DCTSIZE * maxVi - 1)
							/ (DCTSIZE * maxVi);
					if (Log.debugLevel >= Log.LEVEL_INFO)
						android.util.Log.i(TAG, "Size in MCU " + widthMCU + "x"
								+ heightMCU);
					break;
				case M_SOF2:
					len = readMarker(is);
					if (Log.debugLevel >= Log.LEVEL_ERROR)
						android.util.Log.e(TAG,
								"Progressive, Huffman not supported in " + " ("
										+ getLocationName() + ")");
					canBeProcessed = false; // 22
					msg = "Progressive, Huffman not supported";
					if ((sections & BODY_SECTION) != 0)
						addMarker(len, markercode);
					break;
				case M_SOF9:
					len = readMarker(is);
					if (Log.debugLevel >= Log.LEVEL_ERROR)
						android.util.Log.e(TAG,
								"Extended sequential, arithmetic not supported"
										+ " (" + getLocationName() + ")");
					canBeProcessed = false; // 22
					msg = "Extended sequential, arithmetic not supported";
					if ((sections & BODY_SECTION) != 0)
						addMarker(len, markercode);
					break;
				case M_SOF10:
					len = readMarker(is);
					if (Log.debugLevel >= Log.LEVEL_ERROR)
						android.util.Log.e(TAG,
								"Progressive, arithmetic not supported" + " ("
										+ getLocationName() + ")");
					canBeProcessed = false; // 22
					msg = "Progressive, arithmetic not supported";
					if ((sections & BODY_SECTION) != 0)
						addMarker(len, markercode);
					break;
				case M_SOF3:
				case M_SOF5:
				case M_SOF6:
				case M_SOF7:
				case M_JPG:
				case M_SOF11:
				case M_SOF13:
				case M_SOF14:
				case M_SOF15:
					len = readMarker(is);
					if (Log.debugLevel >= Log.LEVEL_ERROR)
						android.util.Log
								.e(TAG,
										"One of the unsupported SOF markers:\n"
												+ "Lossless, Huffman\n"
												+ "Differential sequential, Huffman\n"
												+ "Differential progressive, Huffman\n"
												+ "Differential lossless, Huffman\n"
												+ "Reserved for JPEG extensions\n"
												+ "Lossless, arithmetic\n"
												+ "Differential sequential, arithmetic\n"
												+ "Differential progressive, arithmetic\n"
												+ "Differential lossless, arithmetic"
												+ " (" + getLocationName()
												+ ")");
					canBeProcessed = false; // 22
					msg = "Unsupported SOF marker";
					if ((sections & BODY_SECTION) != 0)
						addMarker(len, markercode);
					break;
				case M_SOS:
					if ((sections & BODY_SECTION) == 0) {
						valid = true;
						unprocessed_marker = markercode;
						stage = IterativeReadVars.DONE_STAGE;
						break markers;
					}

					len = readMarker(is);
					if (canBeProcessed) { // 22
						components_in_scan = data[0] & 255;
						pos = 1;
						comp_ids = new int[components_in_scan];
						dc_table = new int[components_in_scan];
						ac_table = new int[components_in_scan];
						for (int i = 0; i < components_in_scan; i++) {
							comp_ids[i] = data[pos++] & 255;
							dc_table[i] = (data[pos] >> 4) & 15;
							ac_table[i] = data[pos++] & 15;
						}
						_Ss = data[pos++] & 255;
						_Se = data[pos++] & 255;
						_Ah = (data[pos] >> 4) & 15;
						_Al = data[pos] & 15;
						initReadDCT();
						stage = IterativeReadVars.READING_DCT_STAGE;
					} else {
						addMarker(len, markercode);
						if (Log.debugLevel >= Log.LEVEL_WARNING)
							android.util.Log.w(TAG, "Warning: Read raw dct");
						readRawDCT(is);
						valid = true;
						break markers;
					}
					break;
				case M_COM:
					len = readMarker(is);
					// comment_data = data;
					if (out_comment.length() > 0)
						out_comment += '\n';
					try {
						out_comment += new String(data, 0, len, enc);
					} catch (UnsupportedEncodingException uee) {
						out_comment += new String(data, 0, len);
					} catch (NullPointerException npe) {
						out_comment += new String(data, 0, len);
					}
					break;
				case M_EOI:
					valid = true;
					stage = IterativeReadVars.DONE_STAGE;
					break markers;
				case M_DRI:
					if (sections == INFO_SECTION) {
						valid = true;
						data = markerid;
						unprocessed_marker = markercode;
						stage = IterativeReadVars.DONE_STAGE;
						break markers;
					}
					len = readMarker(is);
					if (len != 2)
						throw new IOException("Wrong length of DRI marker "
								+ len + " (" + getLocationName() + ")");
					restart_interval = bs2i(0, 2);
					if (Log.debugLevel >= Log.LEVEL_INFO)
						android.util.Log.i(TAG, "Restart interval "
								+ restart_interval);
					break;
				case M_PRX:
					// skip 0xFF filling
					break;
				default:
					len = readMarker(is);
					if ((0xfffffff0 & markercode) == 0xfffffff0) {
						msg = "Not a Jpeg File, but can be MP3 file";
						break markers; // it's MP3
					} else if (Log.debugLevel >= Log.LEVEL_WARNING)
						android.util.Log.w(
								TAG,
								"Unsupported marker "
										+ Integer.toHexString(markercode)
										+ " length " + len + " ("
										+ getLocationName() + ")");
				}
				if (markercode >= M_SOF2 && markercode <= M_SOF15
						&& markercode != M_DHT && markercode != M_JPG) {
					frm_precision = data[0] & 255;
					frm_x = bs2i(3, 2);
					frm_y = bs2i(1, 2);
					components_in_frame = data[5] & 255;
				}
			} while ((remaining = numBytes - (readcounter - markCounter)) > 0);
			if ((sections & INFO_SECTION) != 0 && imageinfo == null)
				imageinfo = new JPEG(frm_x, frm_y, frm_precision
						* components_in_frame, this);
			if (valid) {
				if ((sections & BODY_SECTION) == 0 && canBeProcessed) {
					unprocessedError = msg;
					msg = null;
				} else if (Log.debugLevel >= Log.LEVEL_INFO)
					android.util.Log.i(TAG,
							"0x" + Integer.toHexString(readcounter) + "("
									+ readcounter + ") byte(s) read in "
									+ getName());
			}
		} catch (Exception e) { // NullPointerException, IOException
			valid = false;
			msg = "Unexpected Error encountered during Read";
			setException(e);
			if (Log.debugLevel >= Log.LEVEL_ERROR)
				e.printStackTrace();
		} finally {
			// System.err.printf("Message %s, stage %d, %s, %d%n", msg, stage,
			// unprocessedError, sections);
			iReadVars.stage = stage;
			if (msg != null
					&& (unprocessedError == null || unprocessedError
							.indexOf("Previous Error") != 0))
				unprocessedError = "Previous Error: " + msg;
			if (msg != null || stage == IterativeReadVars.DONE_STAGE)
				retVal = IterativeReader.STOP;
			if (retVal == IterativeReader.STOP && currentStream != null) {
				if (((sections & BODY_SECTION) != 0 || msg != null)
						&& currentStream != inStream) {
					try {
						currentStream.close();
					} catch (IOException ioe) {
						// can't do much
					}
					currentStream = null;
				}
			}

			if (stage == IterativeReadVars.DONE_STAGE)
				wrapupIterativeRead();

			setErrorMsg(msg);

			if (msg != null && iReadVars.throwException)
				throw new IOException("Image Read Error In LLJTran:: " + msg);
		}
		return retVal;
	}

	// Not used, but coded anyways
	/**
	 * Internal method to read the image.
	 * 
	 * @param sections
	 *            The sections to read. This is a bitwise OR (|) of one or more
	 *            of the flags INFO_SECTION, HEADER_SECTION and BODY_SECTION.
	 * @param keep_appxs
	 *            Specifies if Image Information Header (Exif) is to be
	 *            retained. This should be passed as true if it is later
	 *            required to be written out. Note that if this is passed as
	 *            false existing Image Information Header is unchanged.
	 */
	protected String readInternal(int sections, boolean keep_appxs) {
		String retVal = initReadInternal(sections, keep_appxs, false);
		if (retVal == null) {
			String savedMsg = getErrorMsg();
			setErrorMsg(null);
			try {
				do
					; while (nextRead(10000000) == IterativeReader.CONTINUE);
			} catch (IOException e) {
			} // Do not expect an IOException since last parameter to
				// initReadInternal is false.
			retVal = getErrorMsg();
			setErrorMsg(savedMsg);
		}
		return retVal;
	}

	/**
	 * This method is for modifying only the Image Header Information (Exif)
	 * without processing the actual image. If you read the image fully and
	 * write it back the jpeg decoding tables and the DCT coefficients will be
	 * read into memory and then written back which is not the case with this
	 * method.
	 * <p>
	 * 
	 * For using this method you first read the image upto READ_INFO or if you
	 * want to process the jpeg comments also it would be better to read upto
	 * READ_HEADER since jpeg comments sometimes appear after the decoding
	 * tables. Then you make the necessary changes to the imageInfo and call
	 * refreshAppx after which you call xferInfo to read the image again and
	 * write it out with the changed header and or comments. Also note that
	 * since READ_HEADER defers the errors due to unsupported jpeg formats like
	 * Progressive jpeg you can use this method to process the Image Header
	 * Information (Exif) and/or jpeg comments for those files as well.
	 * 
	 * @param is
	 *            Image input to change the Image Header Information. The image
	 *            marker sections are read one by one and copied to the output
	 *            except the Image Header Information and/or comments which can
	 *            be either copied, replaced by what is present in the LLJTran
	 *            Object or removed. Once the header is read the rest of the
	 *            image is copied as it is.
	 *            <p>
	 * 
	 *            This parameter can be null to indicate that LLJTran continue
	 *            to read from the internal input stream where it had stopped.
	 *            Below are the restrictions when passing it as null:
	 *            <ul>
	 *            <li>The image should have been read only upto READ_INFO so
	 *            that all the decoding tables can be copied as it is.
	 *            <li>Since You can only read upto READ_INFO, in this case
	 *            trying to modify the comments is not fool proof since the
	 *            comments may not have been read.
	 *            <li>You cannot pass RETAIN for the parameters appxsOption or
	 *            commentOption since they have already been read.
	 *            <li>If you pass REPLACE for the parameters appxsOption or
	 *            commentOption and comments and appx headers are encountered in
	 *            the internal input they are also written (It is quite possible
	 *            to encounter comments after READ_INFO since comments might
	 *            come after decoding tables, but should not be the case with
	 *            appx markers. But still..)
	 *            <li>The LLJTran Object cannot be used after this method the
	 *            input is already consumed.
	 *            </ul>
	 * 
	 * @param os
	 *            Output to write the image to.
	 * @param appxsOption
	 *            One of RETAIN, REPLACE or REMOVE indicating whether the
	 *            application specific markers (appxs) including Image Header
	 *            Information like Exif are to be retained from the input
	 *            <b>is</b> or replaced with what is there in the LLJTran Object
	 *            or be removed.
	 * @param commentOption
	 *            One of RETAIN, REPLACE or REMOVE indicating whether the jpeg
	 *            comment section is to be retained from the input <b>is</b> or
	 *            replaced with what is there in the LLJTran Object or be
	 *            removed.
	 * @see #read(int,boolean)
	 * @see #getImageInfo()
	 * @see Exif
	 */
	public void xferInfo(InputStream is, OutputStream os, int appxsOption,
			int commentOption) throws LLJTranException {
		String msg = null;
		int pendingMarker = 0;
		int jpegMarkers = 0;
		boolean internalTransfer = false;

		if (is == null) {
			pendingMarker = unprocessed_marker;
			if (readUpto == READ_INFO) {
				if (xferDone)
					msg = "Cannot Read Further, Input has been Transferred using xferInfo()";
				if (appxsOption == RETAIN || commentOption == RETAIN)
					msg = "Cannot Retain appxs or comments when tranferring from internal stream";
				is = iReadVars.is;
			} else
				msg = "Can transfer from internal stream only if previously read upto READ_INFO";
		}

		if (is == iReadVars.is) {
			xferDone = true;
			internalTransfer = true;
		}

		if (msg == null)
			try {
				writeMarkerSOI(os);
				if (appxsOption == REPLACE)
					writeMarkerAppXs(os);
				if (commentOption == REPLACE)
					writeMarkerComment(os, out_comment, enc);
				if (data.length < 1024)
					data = new byte[1024];
				markers: do {
					if (pendingMarker == 0) {
						if (is.read(markerid) != markerid.length) {
							// Wrong length read for marker header
							msg = "Unexpected End Of Input";
							break markers;
						}
						if (markerid[0] != M_PRX) {
							msg = "Invalid Marker found";
							break markers;
						}
					} else {
						markerid[0] = M_PRX;
						markerid[1] = (byte) pendingMarker;
						pendingMarker = 0;
					}

					byte markercode = markerid[1];
					switch (markercode) {
					case M_SOI:
						break;
					case M_APP0:
					case M_APP0 + 1:
					case M_APP0 + 2:
					case M_APP0 + 3:
					case M_APP0 + 4:
					case M_APP0 + 5:
					case M_APP0 + 6:
					case M_APP0 + 7:
					case M_APP0 + 8:
					case M_APP0 + 9:
					case M_APP0 + 10:
					case M_APP0 + 11:
					case M_APP12:
					case M_APP12 + 1:
					case M_APP12 + 2:
					case M_APP12 + 3:
						xferMarker(is, os, markercode, appxsOption != RETAIN
								&& (appxsOption == REMOVE || !internalTransfer));
						break;
					case M_COM:
						xferMarker(
								is,
								os,
								markercode,
								commentOption != RETAIN
										&& (commentOption == REMOVE || !internalTransfer));
						break;
					case M_DRI:
						xferMarker(is, os, markercode, false);
						break;
					case M_PRX:
						break;
					case M_DQT:
					case M_DHT:
					case M_SOF0:
					case M_SOF1:
					case M_SOF2:
					case M_SOF9:
					case M_SOF10:
					case M_SOF3:
					case M_SOF5:
					case M_SOF6:
					case M_SOF7:
					case M_JPG:
					case M_SOF11:
					case M_SOF13:
					case M_SOF14:
					case M_SOF15:
						++jpegMarkers;
						xferMarker(is, os, markercode, false);
						break;
					case M_SOS:
						if (jpegMarkers >= 3) {
							xferMarker(is, os, markercode, false);
							xferData(is, os, -1);
							os.flush();
							break markers;
						} else
							msg = "All Jpeg Markers not Encountered. A likely error";
						break;
					case M_EOI:
						msg = "Unexpected EOI marker found";
						break;
					default:
						if (Log.debugLevel >= Log.LEVEL_WARNING)
							android.util.Log.w(TAG,
									"Warning: xferAppxs(): Unhandled Marker "
											+ Integer.toHexString(markercode));
						xferMarker(is, os, markercode, false);
						break;
					}
				} while (msg == null);
			} catch (Exception e) { // NullPointerException, IOException
				msg = "Unexpected Error encountered during Read";
				if (Log.debugLevel >= Log.LEVEL_ERROR)
					e.printStackTrace();
			}

		if (internalTransfer)
			closeInternalInputStream();

		if (msg != null)
			throw new LLJTranException(msg);
	}

	/**
	 * Closes the internal Input Stream in case LLJTran was constructed with a
	 * File. Note that This does not close the InputStream if passed in the
	 * constructor.
	 */
	public void closeInternalInputStream() {
		if (inStream == null && currentStream != null) {
			try {
				currentStream.close();
			} catch (IOException ioe) {
			}
			currentStream = null;
			unprocessed_marker = 0;
		}
	}

	/**
	 * Gets the Image Width. The image should have been successfully read upto
	 * READ_HEADER.
	 * 
	 * @return Image Width in pixels
	 */
	public int getWidth() {
		return frm_x;
	}

	/**
	 * Gets the Image Height. The image should have been successfully read upto
	 * READ_HEADER.
	 * 
	 * @return Image Height in pixels
	 */
	public int getHeight() {
		return frm_y;
	}

	/**
	 * Gets the Image Width in number of MCU blocks. The image should have been
	 * successfully read upto READ_HEADER.
	 * 
	 * @return Image Width in MCU blocks
	 */
	public int getWidthInMCU() {
		return widthMCU;
	}

	/**
	 * Gets the Image Height in number of MCU blocks. The image should have been
	 * successfully read upto READ_HEADER.
	 * 
	 * @return Image Height in MCU blocks
	 */
	public int getHeightInMCU() {
		return heightMCU;
	}

	/**
	 * Gets the Maximum Horizontal Sampling factor, A jpeg compression
	 * parameter. The image should have been successfully read upto READ_HEADER.
	 * 
	 * @return Max Horizontal Sampling Factor
	 */
	public int getMaxHSamplingFactor() {
		return maxHi;
	}

	/**
	 * Gets the Maximum Vertical Sampling factor, A jpeg compression parameter.
	 * The image should have been successfully read upto READ_HEADER.
	 * 
	 * @return Max Vertical Sampling Factor
	 */
	public int getMaxVSamplingFactor() {
		return maxVi;
	}

	/**
	 * Gets the width of an MCU block which equals 8*getMaxHSamplingFactor().
	 * The image should have been successfully read upto READ_HEADER.
	 * 
	 * @return Width in pixels of an MCU block
	 */
	public int getMCUWidth() {
		return DCTSIZE * maxHi;
	}

	/**
	 * Gets the height of an MCU block which equals 8*getMaxVSamplingFactor().
	 * The image should have been successfully read upto READ_HEADER.
	 * 
	 * @return Height in pixels of an MCU block
	 */
	public int getMCUHeight() {
		return DCTSIZE * maxVi;
	}

	/**
	 * Returns the number of components in Image, which is usually 3 for a RGB
	 * color image.
	 * 
	 * @return Number of Components
	 */
	public int getNumComponents() {
		return components_in_frame;
	}

	/**
	 * Returns the Horizontal Sampling factor for the given component.
	 * 
	 * @param componentIndex
	 *            Index of the component which should be between 0 and
	 *            getNumComponents()-1
	 * @return Horizontal Sampling Factor of the Component
	 */
	public int getHSamplingFactor(int componentIndex) {
		return H[componentIndex];
	}

	/**
	 * Returns the Vertical Sampling factor for the given component.
	 * 
	 * @param componentIndex
	 *            Index of the component which should be between 0 and
	 *            getNumComponents()-1
	 * @return Vertical Sampling Factor of the Component
	 */
	public int getVSamplingFactor(int componentIndex) {
		return V[componentIndex];
	}

	/**
	 * Gets the Restart Interval.
	 * 
	 * @return Restart Interval of Restart Markers or 0 if there are no restart
	 *         markers.
	 */
	public int getRestartInterval() {
		return restart_interval;
	}

	/**
	 * Gets the Number of Quantization Tables
	 * 
	 * @return Number of Quantization Tables
	 */
	public int getNumQTables() {
		return q_table.length;
	}

	/**
	 * Gets a Quantization Table
	 * 
	 * @param tableIndex
	 *            Table Index. Note that this could be different from the
	 *            componentIndex.
	 * @return Quantization Table for the given tableIndex
	 * @see #getQTableIndexForComponent(int)
	 */
	public int[] getQTable(int tableIndex) {
		int qTable[] = q_table[tableIndex];
		int retVal[] = new int[qTable.length];
		for (int i = 0; i < qTable.length; ++i)
			retVal[i] = qTable[jpegzigzagorder[i]];
		return retVal;
	}

	/**
	 * Gets the Quantization Table Index for a given component
	 * 
	 * @param componentIndex
	 *            Component Index for which Quantization Table Index is required
	 * @return The Quantization Table Index for the given Component Index
	 */
	public int getQTableIndexForComponent(int componentIndex) {
		int tableIndex = -1;
		int tableNum = QT[componentIndex];
		int i;
		for (i = 0; i < q_ix.length; ++i)
			if (q_ix[i] == tableNum)
				break;

		if (i < q_ix.length)
			tableIndex = i;

		return tableIndex;
	}

	/**
	 * Gets Number of Application Specific Markers (Appxs) Read.
	 * 
	 * @return Number of Application Specific Markers (Appxs) Read
	 */
	public int getNumAppxs() {
		int retVal = 0;
		if (appxs != null)
			retVal = appxs.length;
		return retVal;
	}

	/**
	 * Gets the Marker Code for the give Appx index.
	 * 
	 * @param index
	 *            Index of the Marker (0 to getNumAppxs()-1)
	 * @return The Marker Code for the give Appx index
	 */
	public int getAppxMarker(int index) {
		return 0xFF & appxs[index][1];
	}

	/**
	 * Gets the length of the Appxs marker at the given index.
	 * 
	 * @param index
	 *            Index of the Marker (0 to getNumAppxs()-1)
	 * @return The Length of the Appxs marker at the given index. This includes
	 *         the 2 bytes for the 0xFF and markerCode.
	 */
	public int getAppxLen(int index) {
		return appxs[index].length;
	}

	/**
	 * Copies a portion of or the entire data of an Appx marker.
	 * 
	 * @param index
	 *            Index of the Marker (0 to getNumAppxs()-1)
	 * @param markerData
	 *            Array to copy the markerData. The array must be large enough.
	 * @param startIndex
	 *            Index in markerData to start copying from
	 * @param len
	 *            Length to be copied. This must be <= getAppxLen(index)
	 */
	public int getAppx(int index, byte markerData[], int startIndex, int len) {
		byte appxsData[] = null;
		appxsData = appxs[index];
		System.arraycopy(appxsData, 0, markerData, startIndex, len);

		return appxsData.length;
	}

	/**
	 * Gets the Appx index of the Image Header Information (Exif) Data. The
	 * actual appx can be retrieved using this index.
	 * 
	 * @return The Appx index of the Image Header Information (Exif) Data.
	 *         Returns -1 if No Image Header Information Appx marker is
	 *         identified.
	 */
	public int getImageInfoAppxIndex() {
		return appHdrIndex;
	}

	/**
	 * Changes the Appx marker data at the given index.
	 * 
	 * @param index
	 *            Index of the Marker (0 to getNumAppxs()-1)
	 * @param markerData
	 *            Array to copy the markerData.
	 * @param startIndex
	 *            Index in markerData to start copying from
	 * @param len
	 *            Length to be copied
	 * @param forImageInfo
	 *            If this parameter is passed as true it means the Appx marker
	 *            contains Image Header Information such as Exif. The data is
	 *            attempted to be parsed. If successfull the imageInfo is
	 *            updated. Otherwise an instance of JPEG is created for basic
	 *            image information.
	 */
	public void setAppx(int index, byte markerData[], int startIndex, int len,
			boolean forImageInfo) {
		validateAppxs(markerData, startIndex, len);
		byte newAppxs[] = new byte[len];
		System.arraycopy(markerData, startIndex, newAppxs, 0, len);
		appxs[index] = newAppxs;

		// Reload imageinfo
		if (forImageInfo) {
			AbstractImageInfo<?> curImageInfo[] = new AbstractImageInfo[1];

			try {
				processAppMarker(newAppxs, 4, curImageInfo, null);
			} catch (Exception e) {
				if (Log.debugLevel >= Log.LEVEL_ERROR) {
					android.util.Log.e(TAG, "Error Parsing ImageInfo:");
					e.printStackTrace();
				}
				curImageInfo[0] = null;
			}

			imageinfo = curImageInfo[0];

			appHdrIndex = index;
		} else if (index == appHdrIndex)
			imageinfo = null; // Remove existing imageinfo

		if (imageinfo == null) {
			appHdrIndex = -1;
			if (frm_x != 0 && frm_y != 0)
				try {
					imageinfo = new JPEG(frm_x, frm_y, frm_precision
							* components_in_frame, this);
				} catch (Exception e) {
					if (Log.debugLevel >= Log.LEVEL_ERROR)
						e.printStackTrace();
				}
		}
	}

	/**
	 * Inserts the Appx marker data at the given index.
	 * 
	 * @param index
	 *            Index where to insert the new Marker (0 to getNumAppxs())
	 * @param markerData
	 *            Array to copy the markerData.
	 * @param startIndex
	 *            Index in markerData to start copying from
	 * @param len
	 *            Length to be copied
	 * @param forImageInfo
	 *            If this parameter is passed as true it means the Appx marker
	 *            contains Image Header Information such as Exif. The data is
	 *            attempted to be parsed. If successfull the imageInfo is
	 *            updated. Otherwise an instance of JPEG is created for basic
	 *            image information.
	 */
	public void insertAppx(int index, byte markerData[], int startIndex,
			int len, boolean forImageInfo) {
		validateAppxs(markerData, startIndex, len);
		if (index == 0 && appxs == null)
			appxs = new byte[0][];
		byte[] ta[] = new byte[appxs.length + 1][];
		if (index > 0)
			System.arraycopy(appxs, 0, ta, 0, index);
		if (index < appxs.length)
			System.arraycopy(appxs, index, ta, index + 1, appxs.length - index);
		appxs = ta;
		if (index <= appHdrIndex)
			++appHdrIndex;
		setAppx(index, markerData, startIndex, len, forImageInfo);
	}

	/**
	 * Adds the Appx marker data at the end. Equivalent to calling
	 * insertAppx(getNumAppxs(), markerData, startIndex, len, forImageInfo);
	 * 
	 * @param markerData
	 *            Array to copy the markerData.
	 * @param startIndex
	 *            Index in markerData to start copying from
	 * @param len
	 *            Length to be copied
	 * @param forImageInfo
	 *            If this parameter is passed as true it means the Appx marker
	 *            contains Image Header Information such as Exif. The data is
	 *            attempted to be parsed. If successfull the imageInfo is
	 *            updated. Otherwise an instance of JPEG is created for basic
	 *            image information.
	 */
	public void addAppx(byte markerData[], int startIndex, int len,
			boolean forImageInfo) {
		insertAppx(getNumAppxs(), markerData, startIndex, len, forImageInfo);
	}

	/**
	 * Removes the Appx marker data at the given index. In case the appx to be
	 * removed contains Image Header Information (Exif) the imageInfo is
	 * replaced with a JPEG instance containing basic Image Information.
	 * 
	 * @param index
	 *            Index of the marker to remove (0 to getNumAppxs()-1)
	 */
	public void removeAppx(int index) {
		byte[] ta[] = new byte[appxs.length - 1][];
		if (index > 0)
			System.arraycopy(appxs, 0, ta, 0, index);
		if (index < appxs.length - 1)
			System.arraycopy(appxs, index + 1, ta, index, appxs.length - index
					- 1);
		appxs = ta;
		if (index == appHdrIndex) {
			imageinfo = null;
			if (frm_x != 0 && frm_y != 0)
				try {
					imageinfo = new JPEG(frm_x, frm_y, frm_precision
							* components_in_frame, this);
				} catch (Exception e) {
					if (Log.debugLevel >= Log.LEVEL_ERROR)
						e.printStackTrace();
				}
		}
		if (index > appHdrIndex)
			--appHdrIndex;
	}

	private void validateAppxs(byte markerData[], int startIndex, int len) {
		RuntimeException e = null;
		int firstByte = markerData[0];
		int secondByte = markerData[1];
		if (firstByte != M_PRX || secondByte < M_APP0 + 1
				|| secondByte > M_APP0 + 15)
			e = new RuntimeException(
					"validateAppxs: Incorrect 1st two bytes for App marker: 0x"
							+ Integer.toHexString(firstByte) + ":0x"
							+ Integer.toHexString(secondByte));

		// Validate if 3rd and 4th bytes correspond to len
		if (e == null) {
			data = markerData;
			int expectedLen = bs2i(startIndex + 2, 2) + 2;
			if (len != expectedLen)
				e = new RuntimeException("validateAppxs: Incorrect Length "
						+ (expectedLen - 2) + " in 3rd and 4th bytes. "
						+ " expected = " + (len - 2));
		}

		if (e != null)
			throw e;

		// Throw Array bounds exception if array length not enough.
		// byte testByte = markerData[startIndex + len - 1];
		// the above code can be eliminated due optimization by a compiler
		if (markerData.length < startIndex + len)
			throw new ArrayIndexOutOfBoundsException(
					"Size of markerData insufficient to keep "
							+ (startIndex + len) + "bytes");
	}

	/**
	 * Internal method to store markers of an image which cannot be processed.
	 * The LLJTran implementation does nothing.
	 * 
	 * @param len
	 *            length of marker to be read
	 * @param markercode
	 *            Code of marker to be read
	 */
	protected void addMarker(int len, byte markercode) {
	}

	private void initReadAppx(byte markercode) throws IOException {
		if (iReadVars.is.read(markerid) != markerid.length) {
			throw new FileFormatException("Wrong length read for marker header");
		}
		readcounter += markerid.length;
		data = markerid;
		int len = bs2i(0, 2);
		if (iReadVars.keep_appxs) {
			// Clear any previous values
			if (!iReadVars.appxsCleared) {
				appxs = null;
				imageinfo = null;
				iReadVars.appxsCleared = true;
			}

			data = new byte[len + 2];
			data[0] = M_PRX;
			data[1] = markercode;
			System.arraycopy(markerid, 0, data, 2, 2);
		}
		iReadVars.appxLen = len + 2;
		iReadVars.appxPos = 4;
	}

	private boolean readNextAppx(int numBytes) throws IOException {
		InputStream is = iReadVars.is;
		int len = iReadVars.appxLen - iReadVars.appxPos;
		int readLen;
		boolean retVal;
		if (len > numBytes) {
			retVal = true;
			len = numBytes;
		} else
			retVal = false;

		readLen = iReadVars.keep_appxs ? BasicIo.read(is, data,
				iReadVars.appxPos, len, len) : (int) BasicIo.skip(is, len);
		iReadVars.appxPos += readLen;
		readcounter += readLen;

		if (readLen < len)
			retVal = false; // Unexpected EOF will error out later

		return retVal;
	}

	private void addAppx() {
		if (appxs == null)
			appxs = new byte[0][];
		byte[] ta[] = new byte[appxs.length + 1][];
		System.arraycopy(appxs, 0, ta, 0, appxs.length);
		appxs = ta;
		appxs[appxs.length - 1] = data;
	}

	private int xferData(InputStream is, OutputStream os, int len)
			throws IOException {
		int bytesRead = 0, remaining = len;
		int readLen, requestLen;

		requestLen = data.length;

		if (len == -1)
			remaining = requestLen;

		while (remaining > 0) {
			if (requestLen > remaining)
				requestLen = remaining;

			readLen = is.read(data, 0, requestLen);
			if (readLen < 0)
				break;

			if (readLen > 0) {
				os.write(data, 0, readLen);
				bytesRead += readLen;
				if (len != -1)
					remaining -= readLen;
			}
		}

		return bytesRead;
	}

	private void xferMarker(InputStream is, OutputStream os, int markercode,
			boolean skip) throws IOException {
		if (!skip) {
			os.write(M_PRX);
			os.write(markercode);
		}
		if (is.read(markerid) != markerid.length) {
			throw new FileFormatException("Wrong length read for marker header");
		}
		byte saveData[] = data;
		data = markerid;
		int len = bs2i(0, 2) - 2;
		data = saveData;
		if (skip)
			skip(is, len);
		else {
			os.write(markerid);
			xferData(is, os, len);
		}
	}

	private int readMarker(InputStream is) throws IOException,
			FileFormatException {
		if (is.read(markerid) != markerid.length) {
			throw new FileFormatException("Wrong length read for marker header");
		}
		readcounter += markerid.length;
		data = markerid;
		int len = bs2i(0, 2) - 2;
		if (len == 0)
			throw new FileFormatException("Zero length marker header");
		data = new byte[len];
		read(is, data);
		readcounter += len;
		return len;
	}

	// writes stored APPs
	private void initWriteMarkerAppXs() throws IOException {
		iWriteVars.currentAppx = 0;
		iWriteVars.currentAppxPos = 0;
	}

	// writes stored APPs
	private boolean writeNextMarkerAppXs(int numBytes) throws IOException {
		int i = iWriteVars.currentAppx;
		OutputStream os = iWriteVars.os;
		int pos = iWriteVars.currentAppxPos;
		int newPos;
		boolean retVal = false;
		int remaining = numBytes;

		if (appxs != null) {
			retVal = true;
			do {
				if (i >= appxs.length) {
					retVal = false;
					break;
				}

				if (remaining <= 0)
					break;

				byte curAppxs[] = appxs[i];
				int len = curAppxs.length - pos;
				if (len > remaining) {
					len = remaining;
					newPos = pos + remaining;
				} else {
					i++;
					newPos = 0;
				}
				os.write(curAppxs, pos, len);
				pos = newPos;
				remaining -= len;
				writecounter += len;
			} while (true);

			iWriteVars.currentAppx = i;
			iWriteVars.currentAppxPos = pos;

			if (i >= appxs.length)
				retVal = false;
		}

		return retVal;
	}

	// writes stored APPs
	/**
	 * Internal method to write out the appx marker data
	 * 
	 * @param os
	 *            Image output to write the marker data
	 */
	protected void writeMarkerAppXs(OutputStream os) throws IOException {
		if (appxs == null)
			return;
		for (int i = 0; i < appxs.length; i++) {
			os.write(appxs[i]);
			writecounter++;
		}
	}

	/**
	 * Internal method to Write a SOI marker
	 * 
	 * @param os
	 *            Image output to write to
	 */
	protected void writeMarkerSOI(OutputStream os) throws IOException {
		os.write(M_PRX);
		os.write(M_SOI);
		writecounter += 2;
	}

	// TODO: this method should be exclusive with writeMarkerAppXs
	/**
	 * Method to write marker data corresponding to a Custom ImageInfo class.
	 * This method is not used by LLJTran.
	 * 
	 * @param os
	 *            Image output to write to
	 * @param custom_appx
	 *            Custom Image Info Class
	 */
	protected void writeNewMarker(OutputStream os, Class<?> custom_appx)
			throws IOException {
		byte b[];
		if (custom_appx == null)
			return;
		if (custom_appx == JFXX.class) {
			b = JFXX.getMarkerData();
			os.write(b);
			writecounter += b.length;
		} else if (custom_appx == Exif.class) {
			b = Exif.getMarkerData();
			os.write(b);
			writecounter += b.length;
		} else if (custom_appx == AbstractImageInfo.class) {
			String name = getName();
			int dp = name.lastIndexOf('.');
			if (dp > 0)
				name = name.substring(0, dp + 1);
			else
				name += '.';
			File ff;
			if (file != null
					&& (ff = new File(file.getParent(), name + Exif.FORMAT))
							.exists()) {
				try {
					byte[] buf = new byte[(int) ff.length()];
					FileInputStream fis = new FileInputStream(ff);
					read(fis, buf);
					os.write(buf);
					writecounter++;
					fis.close();
				} catch (IOException e) {
					if (Log.debugLevel >= Log.LEVEL_ERROR)
						android.util.Log.e(TAG,
								"Exception in reading exif marker " + e);
				}
			}
		}
	}

	/**
	 * Internal method to write the Jpeg comment marker
	 * 
	 * @param os
	 *            Output
	 * @param comment_data
	 *            Comment data
	 */
	protected void writeMarkerComment(OutputStream os, byte comment_data[])
			throws IOException {
		if (comment_data != null && comment_data.length > 0) {
			os.write(M_PRX);
			os.write(M_COM);
			int size = 2;
			size += comment_data.length;
			os.write(size >> 8);
			os.write(size & 255);
			os.write(comment_data);
			writecounter += (size + 2);
		}
	}

	/**
	 * Internal method to write the Jpeg comment marker
	 * 
	 * @param os
	 *            Output
	 * @param comment
	 *            Comment String
	 * @param enc
	 *            Encoding to be used to get the data
	 */
	protected void writeMarkerComment(OutputStream os, String comment,
			String enc) throws IOException {
		try {
			data = comment.getBytes(enc);
		} catch (UnsupportedEncodingException uee) {
			data = comment.getBytes();
		} catch (NullPointerException npe) {
			data = comment.getBytes();
		}
		writeMarkerComment(os, data);
	}

	/**
	 * Internal method to write the Huffman Tables
	 */
	protected void writeMarkerDHT(OutputStream os, byte huffTables[])
			throws IOException {
		os.write(M_PRX);
		os.write(M_DHT);
		int size = 2;

		if (huffTables != null)
			size += huffTables.length;
		else {
			for (int i = 0; i < ac_ix.length; i++)
				size += 1 + 16 + ac_huffval[i].length;
			for (int i = 0; i < dc_ix.length; i++)
				size += 1 + 16 + dc_huffval[i].length;
		}

		os.write(size >> 8);
		os.write(size & 255);

		if (huffTables != null)
			os.write(huffTables);
		else {
			for (int i = 0; i < dc_ix.length; i++) {
				os.write(dc_ix[i]);
				for (int k = 0; k < dc_huffbits[i].length; k++)
					os.write(dc_huffbits[i][k]);
				for (int k = 0; k < dc_huffval[i].length; k++)
					os.write(dc_huffval[i][k]);
			}

			for (int i = 0; i < ac_ix.length; i++) {
				os.write(ac_ix[i] + 0x10);
				for (int k = 0; k < ac_huffbits[i].length; k++)
					os.write(ac_huffbits[i][k]);
				for (int k = 0; k < ac_huffval[i].length; k++)
					os.write(ac_huffval[i][k]);
			}
		}
		writecounter += (size + 2);
	}

	/**
	 * Internal method to write the Quantization Tables
	 */
	protected void writeMarkerDQT(OutputStream os) throws IOException {
		if (!valid)
			throw new IOException(
					"Can't write marker DQT, because an error happened at reading ("
							+ getLocationName() + ")");
		os.write(M_PRX);
		os.write(M_DQT);
		int size = 2 + q_ix.length * (1 + DCTSIZE2);
		os.write(size >> 8);
		os.write(size & 255);
		for (int i = 0; i < q_ix.length; i++) {
			os.write(q_ix[i] + (q_prec[i] == 8 ? 0 : 0x10));
			for (int k = 0; k < DCTSIZE2; k++)
				os.write(q_table[i][k]);
		}
		writecounter += (size + 2);
	}

	/**
	 * Internal method to write Restart Marker
	 */
	protected void writeMarkerDRI(OutputStream os, int restart_interval)
			throws IOException {
		if (restart_interval > 0) {
			os.write(M_PRX);
			os.write(M_DRI);
			os.write(0);
			os.write(4);
			os.write(restart_interval >> 8);
			os.write(restart_interval & 255);
			writecounter += 6;
		}
	}

	/**
	 * Internal method to write SOF0 marker
	 */
	protected void writeMarkerSOF0(OutputStream os) throws IOException {
		os.write(M_PRX);
		os.write(M_SOF0);
		int size = 2 + 1 + 2 + 2 + 1 + components_in_frame * (1 + 1 + 1);
		os.write((size >> 8) & 255);
		os.write(size & 255);
		os.write(frm_precision);
		os.write(frm_y >> 8);
		os.write(frm_y & 255);
		os.write(frm_x >> 8);
		os.write(frm_x & 255);
		os.write(components_in_frame);
		for (int i = 0; i < components_in_frame; i++) {
			os.write(ID[i]);
			os.write((H[i] << 4) + V[i]);
			os.write(QT[i]);
		}
		writecounter += (size + 2);
	}

	/**
	 * Internal method to write SOS marker
	 */
	protected void writeMarkerSOS(OutputStream os) throws IOException {
		os.write(M_PRX);
		os.write(M_SOS);
		int size = 2 + 1 + components_in_scan * (1 + 1) + 1 + 1 + 1;
		os.write(size >> 8);
		os.write(size & 255);
		os.write(components_in_scan);
		for (int i = 0; i < components_in_scan; i++) {
			os.write(comp_ids[i]);
			os.write((dc_table[i] << 4) + ac_table[i]);
		}
		os.write(_Ss);
		os.write(_Se);
		os.write((_Ah << 4) + _Al);
		writecounter += (size + 2);
	}

	/**
	 * Internal method to write EOI marker
	 */
	protected void writeMarkerEOI(OutputStream os) throws IOException {
		os.write(M_PRX);
		os.write(M_EOI);
		writecounter += 2;
	}

	void readRawDCT(InputStream is) throws IOException {
	}

	/**
	 * Internal method to read DHT marker
	 * 
	 * @param is
	 *            Input. Ignored if lenAvailable &gt; 0
	 * @param lenAvailable
	 *            Length of marker data read into data array. Passed as 0 if
	 *            marker is to be read first.
	 */
	protected int readDHT(InputStream is, int lenAvailable) throws IOException {
		int result = lenAvailable;
		if (result <= 0)
			result = readMarker(is);
		int base = 0;
		do {
			boolean is_ac = (data[base] & 255) > 15;
			int tbl_ix;
			if (is_ac)
				tbl_ix = (data[base] & 255) - 16;
			else
				tbl_ix = data[base] & 255;
			int[][] wt2d, enc_matrix, wt3d[];
			int[] wt1d;
			int tabnum = 0;
			int ii;
			if (!is_ac) {
				enc_matrix = new int[12][2];
				tabnum = dc_valoffset.length;
				wt2d = new int[tabnum + 1][];
				System.arraycopy(dc_valoffset, 0, wt2d, 0, tabnum);
				dc_valoffset = wt2d;
				wt2d = new int[tabnum + 1][];
				System.arraycopy(dc_maxcode, 0, wt2d, 0, tabnum);
				dc_maxcode = wt2d;
				wt2d = new int[tabnum + 1][];
				System.arraycopy(dc_huffval, 0, wt2d, 0, tabnum);
				dc_huffval = wt2d;
				wt2d = new int[tabnum + 1][];
				System.arraycopy(dc_huffbits, 0, wt2d, 0, tabnum);
				dc_huffbits = wt2d;
				wt3d = new int[tabnum + 1][][];
				System.arraycopy(enc_dc_matrix, 0, wt3d, 0, tabnum);
				enc_dc_matrix = wt3d;
				wt1d = new int[tabnum + 1];
				System.arraycopy(dc_ix, 0, wt1d, 0, tabnum);
				dc_ix = wt1d;
				dc_ix[tabnum] = tbl_ix;
			} else {
				enc_matrix = new int[255][2];
				tabnum = ac_valoffset.length;
				wt2d = new int[tabnum + 1][];
				System.arraycopy(ac_valoffset, 0, wt2d, 0, tabnum);
				ac_valoffset = wt2d;
				wt2d = new int[tabnum + 1][];
				System.arraycopy(ac_maxcode, 0, wt2d, 0, tabnum);
				ac_maxcode = wt2d;
				wt2d = new int[tabnum + 1][];
				System.arraycopy(ac_huffval, 0, wt2d, 0, tabnum);
				ac_huffval = wt2d;
				wt2d = new int[tabnum + 1][];
				System.arraycopy(ac_huffbits, 0, wt2d, 0, tabnum);
				ac_huffbits = wt2d;
				wt3d = new int[tabnum + 1][][];
				System.arraycopy(enc_ac_matrix, 0, wt3d, 0, tabnum);
				enc_ac_matrix = wt3d;
				wt1d = new int[tabnum + 1];
				System.arraycopy(ac_ix, 0, wt1d, 0, tabnum);
				ac_ix = wt1d;
				ac_ix[tabnum] = tbl_ix;
			}
			int[] huffsize = new int[257];
			int[] huffcode = new int[257];
			int[] huffbits = new int[16];
			int p = 0;
			for (int l = 1; l <= 16; l++) {
				huffbits[l - 1] = ii = (data[base + l] & 255);
				while (ii-- > 0)
					huffsize[p++] = l;
			}
			huffsize[p] = 0;
			int numsymbols = p;
			// check for legal huffman code tree
			int code = 0;
			int si = huffsize[0];
			p = 0;
			while (huffsize[p] != 0) {
				while (huffsize[p] == si) {
					huffcode[p++] = code++;
				}
				// code is now 1 more than the last code used for codelength si;
				// but
				// it must still fit in si bits, since no code is allowed to be
				// all ones.
				if (code >= (1 << si))
					throw new IOException("Bad huffman code table ("
							+ getLocationName() + ")");
				code <<= 1;
				si++;
			}
			// Figure F.15: generate decoding tables for bit-sequential decoding
			int[] valoffset = new int[17];
			int[] maxcode = new int[18];
			p = 0;
			for (int l = 1; l <= 16; l++) {
				if (data[base + l] != 0) {
					// valoffset[l] = huffval[] index of 1st symbol of code
					// length l,
					// minus the minimum code of length l
					valoffset[l] = p - huffcode[p];
					p += (data[base + l] & 255);
					maxcode[l] = huffcode[p - 1];
				} else {
					maxcode[l] = -1; // -1 if no codes of this length
				}
			}
			maxcode[17] = -1;
			int[] huffval = new int[numsymbols];
			// fill values
			for (int l = 0; l < numsymbols; l++) {
				huffval[l] = data[base + l + 17] & 255;
				enc_matrix[huffval[l]][0] = huffcode[l];
				enc_matrix[huffval[l]][1] = huffsize[l];
			}

			if (!is_ac) {
				dc_valoffset[tabnum] = valoffset;
				dc_maxcode[tabnum] = maxcode;
				dc_huffval[tabnum] = huffval;
				enc_dc_matrix[tabnum] = enc_matrix;
				dc_huffbits[tabnum] = huffbits;
			} else {
				ac_valoffset[tabnum] = valoffset;
				ac_maxcode[tabnum] = maxcode;
				ac_huffval[tabnum] = huffval;
				enc_ac_matrix[tabnum] = enc_matrix;
				ac_huffbits[tabnum] = huffbits;
			}
			base += (numsymbols + 17);
		} while (base < result);
		return result;
	}

	private int restarts_to_go;

	private HuffDecoder decoder;

	private void initReadDCT() throws IOException {
		iReadVars.currentProgress = 0.01;
		iReadVars.callbackProgress = 0;
		iReadVars.last_dc = new int[components_in_scan];
		iReadVars.DCT = new int[2][DCTSIZE2];
		iReadVars.next_restart_num = 0;
		restarts_to_go = restart_interval;
		if (_Ss != 0 || _Se != (DCTSIZE2 - 1) || _Ah != 0 || _Al != 0)
			if (Log.debugLevel >= Log.LEVEL_ERROR)
				android.util.Log.e(TAG, "Not sequential image, Ss=" + _Ss
						+ " Se=" + _Se + " Ah=" + _Ah + " Al=" + _Al);
		decoder = new HuffDecoder(iReadVars.is);
		dct_coefs = new int[heightMCU][][][][];
		iReadVars.progressPerMcu = (0.99 / heightMCU) / widthMCU;
		if (readProgressCallback != null
				&& iReadVars.currentProgress - iReadVars.callbackProgress > readProgressCallback
						.getCallbackInterval()) {
			iReadVars.callbackProgress = iReadVars.currentProgress;
			readProgressCallback.progressHandler(iReadVars.callbackProgress,
					(int) Math.round(iReadVars.callbackProgress * 100));
		}
		iReadVars.ix = 0;
		iReadVars.iy = 0;
	}

	private boolean readNextDCT(int numBytes) throws IOException {
		InputStream is = iReadVars.is;
		int ix = iReadVars.ix;
		int iy = iReadVars.iy;
		int curcoef;
		int markCounter = readcounter;
		int[] last_dc = iReadVars.last_dc;
		int[][] DCT = iReadVars.DCT;
		int next_restart_num = iReadVars.next_restart_num;
		double currentProgress = iReadVars.currentProgress;
		double progressPerMcu = iReadVars.progressPerMcu;
		double callbackProgress = iReadVars.callbackProgress;

		boolean retVal = true;
		enough: for (; iy < heightMCU; iy++) {
			if (dct_coefs[iy] == null)
				dct_coefs[iy] = new int[widthMCU][mcusize][][];
			for (; ix < widthMCU; ix++) {
				if (readcounter - markCounter >= numBytes)
					break enough;
				// start decode MCU
				int mcuc = 0;
				try {
					for (int c = 0; c < components_in_scan; c++) {
						for (int b = 0; b < V[c] * H[c]; b++) {
							decoder.setTables(false, dc_table[c]);
							last_dc[c] = decoder.extend(decoder.decode(1))
									+ last_dc[c];
							curcoef = 0;
							DCT[0][curcoef] = last_dc[c];
							DCT[1][curcoef++] = 0;
							// decode ACs
							decoder.setTables(true, ac_table[c]);
							int ac, v;
							for (int ci = 1; ci < DCTSIZE2; ci++) {
								ac = decoder.decode(1);
								v = (ac >> 4);
								ac &= 15;
								if (ac != 0) {
									ci += v;
									if (ci > DCTSIZE2 - 1) {
										if (Log.debugLevel >= Log.LEVEL_ERROR)
											android.util.Log.e(TAG,
													"Error: Invalid AC index "
															+ ci);
										ci = DCTSIZE2 - 1;
									}
									ac = decoder.extend(ac);
									DCT[0][curcoef] = ac;
									DCT[1][curcoef++] = ci;
								} else {
									if (v != 15)
										break;
									ci += v;
								}
							}
							dct_coefs[iy][ix][mcuc] = new int[2][curcoef];
							System.arraycopy(DCT[0], 0,
									dct_coefs[iy][ix][mcuc][0], 0, curcoef);
							System.arraycopy(DCT[1], 0,
									dct_coefs[iy][ix][mcuc][1], 0, curcoef);
							mcuc++;
						}
					}
					restarts_to_go--;
					if (restart_interval != 0 && restarts_to_go == 0) {
						// We expect a restart marker. Let us see if we find it
						// correctly

						// First check unprocessed_marker in case restart
						// marker was encountered before decoding of DCT
						// block
						int markercode = unprocessed_marker;
						unprocessed_marker = 0;
						if (markercode == 0) {
							// If no restart marker encountered while decoding
							// try to read a restart marker
							markercode = is.read();
							readcounter++;
							if (markercode != 0xff)
								throw new IOException(
										"0x"
												+ Integer
														.toHexString(markercode)
												+ " found instead of restart marker prefix 0xff at 0x"
												+ Integer
														.toHexString(readcounter)
												+ " (" + getLocationName()
												+ ")");

							// Skip 0xff filling
							do {
								markercode = is.read();
								readcounter++;
							} while (markercode == 0xff);
						}
						if (markercode == ((M_RST0 & 255) + next_restart_num))
							next_restart_num = (next_restart_num + 1) & 7;
						else {
							if (iy == heightMCU - 1 && ix == widthMCU - 1)
								// Forgive missing restart marker at the end.
								// Pass it on as an unprocessed_marker to be
								// processed by the main loop
								unprocessed_marker = markercode;
							else
								throw new IOException(
										"Restart markers are messed up at "
												+ readcounter
												+ "(0x"
												+ Integer
														.toHexString(readcounter)
												+ ") (" + getLocationName()
												+ ")");
						}
						restarts_to_go = restart_interval;
						for (int k = 0; k < last_dc.length; k++)
							last_dc[k] = 0;
						decoder.restart();
					} else if (unprocessed_marker != 0)
						throw new IOException("Unexpected Restart marker 0x"
								+ Integer.toHexString(unprocessed_marker)
								+ " with restart_interval=" + restart_interval
								+ " and restarts_to_go=" + restarts_to_go
								+ " at " + Integer.toHexString(readcounter)
								+ " (" + getLocationName() + ")");
				} catch (RestartException re) {
					restarts_to_go = 0;
					if (Log.debugLevel >= Log.LEVEL_INFO)
						android.util.Log.i(TAG, "Restart exception ");
				}
				currentProgress += progressPerMcu;
				if (readProgressCallback != null
						&& currentProgress - callbackProgress > readProgressCallback
								.getCallbackInterval()) {
					callbackProgress = currentProgress;
					readProgressCallback.progressHandler(callbackProgress,
							(int) Math.round(callbackProgress * 100));
				}
			}
			ix = 0;
		}

		iReadVars.ix = ix;
		iReadVars.iy = iy;
		iReadVars.next_restart_num = next_restart_num;
		iReadVars.currentProgress = currentProgress;
		iReadVars.callbackProgress = callbackProgress;

		if (iy >= heightMCU) {
			retVal = false;
			// Cleanup
			iReadVars.last_dc = null;
			iReadVars.DCT = null;
			decoder = null;
		}

		return retVal;
	}

	/**
	 * Internal method to Read DCT coefficients
	 */
	protected void readDCT(InputStream is) throws IOException {
		iReadVars.is = is;
		initReadDCT();
		do
			; while (readNextDCT(10000000));
	}

	private void transposeImageParameters() {
		int t = frm_x;
		frm_x = frm_y;
		frm_y = t;
		for (int c = 0; c < components_in_scan; c++) {
			t = V[c];
			V[c] = H[c];
			H[c] = t;
		}
		t = widthMCU;
		widthMCU = heightMCU;
		heightMCU = t;
		t = maxHi;
		maxHi = maxVi;
		maxVi = t;
	}

	private void transposeQTable() {
		int t;
		for (int k = 0; k < q_table.length; k++) {
			for (int i = 0; i < DCTSIZE; i++) {
				for (int j = 0; j < i; j++) {
					t = q_table[k][jpegzigzagorder[i * DCTSIZE + j]];
					q_table[k][jpegzigzagorder[i * DCTSIZE + j]] = q_table[k][jpegzigzagorder[j
							* DCTSIZE + i]];
					q_table[k][jpegzigzagorder[j * DCTSIZE + i]] = t;
				}
			}
		}
	}

	private HuffEncoder encoder;

	// class variables partialXMCU and partialYMCU should be set to indicate
	// partial X & Y blocks without transpose
	private void initWriteDCT(OutputStream os, int op, int options,
			int restart_interval, boolean transformDct) throws IOException {
		if (!valid)
			throw new IOException(
					"Can't write DCT, because an error happened at reading ("
							+ getLocationName() + ")");
		iWriteVars.os = os;
		iWriteVars.op = op;
		iWriteVars.options = options;
		iWriteVars.restart_interval = restart_interval;
		iWriteVars.transformDct = transformDct;

		iWriteVars.new_dct_coefs = null;

		iWriteVars.currentProgress = 0.01;
		iWriteVars.callbackProgress = 0;
		iWriteVars.last_dc = null;
		encoder = null;
		iWriteVars.restarts_to_go = restart_interval;

		boolean edgeOption = ((options & OPT_XFORM_ADJUST_EDGES) != 0);
		boolean handleXEdge = false;
		boolean handleYEdge = false;

		// if transformDct true it indicates if full Dct array needs to be
		// allocated
		// or if the rows of old dct array can be reused.
		boolean reuseDctRows = true;

		if (edgeOption) {
			handleXEdge = partialXMCU;
			handleYEdge = partialYMCU;
		}

		if (widthMCU != dct_coefs[0].length || op == TRANSPOSE || op == ROT_90
				|| op == ROT_270 || op == TRANSVERSE)
			reuseDctRows = false;

		iWriteVars.handleXEdge = handleXEdge;
		iWriteVars.handleYEdge = handleYEdge;

		iWriteVars.progressPerMcu = (0.99 / dct_coefs.length)
				/ dct_coefs[0].length;
		if (!transformDct
				&& writeProgressCallback != null
				&& iWriteVars.currentProgress - iWriteVars.callbackProgress > writeProgressCallback
						.getCallbackInterval()) {
			iWriteVars.callbackProgress = iWriteVars.currentProgress;
			writeProgressCallback.progressHandler(iWriteVars.callbackProgress,
					(int) Math.round(iWriteVars.callbackProgress * 100));
		}

		// Whenever we copy reordered coefs from another array we will use that
		// array as the next temp_mcu array to avoid doing a new everytime,
		// saving trouble for the garbage collector
		if (transformDct) {
			if (reuseDctRows) {
				iWriteVars.new_dct_coefs = new int[heightMCU][][][][];
				// Allocate space for new_dct_row
				if (heightMCU > 0)
					iWriteVars.new_dct_coefs[0] = new int[widthMCU][][][];
			} else
				iWriteVars.new_dct_coefs = new int[heightMCU][widthMCU][][][];

			// Allocate space for first new_mcu
			if (heightMCU > 0 && widthMCU > 0)
				iWriteVars.new_dct_coefs[0][0] = new int[mcusize][][];
		} else {
			reuseDctRows = false;
			iWriteVars.last_dc = new int[components_in_scan];
			encoder = new HuffEncoder(os);
		}

		retainDct = !transformDct;

		int xCropOffsetMCU = 0;
		int yCropOffsetMCU = 0;

		// Calculate crop offsets in MCU if CROP operation
		if (op == CROP) {
			xCropOffsetMCU = cropBounds.left / getMCUWidth();
			yCropOffsetMCU = cropBounds.top / getMCUHeight();
		}

		iWriteVars.reuseDctRows = reuseDctRows;
		iWriteVars.xCropOffsetMCU = xCropOffsetMCU;
		iWriteVars.yCropOffsetMCU = yCropOffsetMCU;
		iWriteVars.new_ix = 0;
		iWriteVars.new_iy = 0;
	}

	// class variables partialXMCU and partialYMCU should be set to indicate
	// partial X & Y blocks without transpose
	private boolean writeNextDCT(int numBytes) throws IOException {
		boolean retVal = true;
		int op = iWriteVars.op;
		boolean transformDct = iWriteVars.transformDct;

		int[][][][][] new_dct_coefs = iWriteVars.new_dct_coefs;
		int[][][] new_mcu = null;
		int[][][] next_mcu;

		int[] last_dc = iWriteVars.last_dc;
		int off;

		boolean handleXEdge = iWriteVars.handleXEdge;
		boolean handleYEdge = iWriteVars.handleYEdge;

		// if transformDct true it indicates if full Dct array needs to be
		// allocated
		// or if the rows of old dct array can be reused.
		boolean reuseDctRows = iWriteVars.reuseDctRows;
		int dctOp;
		int xCropOffsetMCU = iWriteVars.xCropOffsetMCU;
		int yCropOffsetMCU = iWriteVars.yCropOffsetMCU;
		int new_off, ix, iy = 0;
		int new_ix = iWriteVars.new_ix;
		int new_iy = iWriteVars.new_iy;
		int[][][][] new_dct_row = null;
		int markCounter = writecounter;

		int restart_interval = iWriteVars.restart_interval;
		int restarts_to_go = iWriteVars.restarts_to_go;
		double currentProgress = iWriteVars.currentProgress;
		double callbackProgress = iWriteVars.callbackProgress;
		double progressPerMcu = iWriteVars.progressPerMcu;
		boolean pullDownMode = iWriteVars.pullDownMode;

		if (transformDct && new_iy < heightMCU) {
			new_dct_row = new_dct_coefs[new_iy];
			if (new_ix < widthMCU)
				new_mcu = new_dct_coefs[new_iy][new_ix];
		}
		enough: for (; new_iy < heightMCU; new_iy++) {
			if (reuseDctRows)
				new_dct_coefs[new_iy] = new_dct_row;
			for (; new_ix < widthMCU; new_ix++) {
				if (transformDct)
					new_dct_coefs[new_iy][new_ix] = new_mcu;

				if (writecounter - markCounter >= numBytes)
					break enough;
				off = 0;
				new_off = 0;
				dctOp = op;
				switch (op) {
				case TRANSPOSE:
					ix = new_iy;
					iy = new_ix;
					break;
				case ROT_90:
					ix = new_iy;
					iy = widthMCU - 1 - new_ix;
					if (handleYEdge) {
						if (iy > 0)
							iy--;
						else {
							iy = widthMCU - 1;
							dctOp = TRANSPOSE;
						}
					}
					break;
				case ROT_270:
					ix = heightMCU - 1 - new_iy;
					iy = new_ix;
					if (handleXEdge) {
						if (ix > 0)
							ix--;
						else {
							ix = heightMCU - 1;
							dctOp = TRANSPOSE;
						}
					}
					break;
				case TRANSVERSE:
					ix = heightMCU - 1 - new_iy;
					iy = widthMCU - 1 - new_ix;
					if (handleXEdge) {
						if (ix > 0)
							ix--;
						else {
							ix = heightMCU - 1;
							dctOp = ROT_90;
						}
					}
					if (handleYEdge) {
						if (iy > 0)
							iy--;
						else {
							iy = widthMCU - 1;
							dctOp = dctOp == TRANSVERSE ? ROT_270 : TRANSPOSE;
						}
					}
					break;
				case FLIP_H:
					ix = widthMCU - 1 - new_ix;
					iy = new_iy;
					if (handleXEdge) {
						if (ix > 0)
							ix--;
						else {
							ix = widthMCU - 1;
							dctOp = NONE;
						}
					}
					break;
				case FLIP_V:
					ix = new_ix;
					iy = heightMCU - 1 - new_iy;
					if (handleYEdge) {
						if (iy > 0)
							iy--;
						else {
							iy = heightMCU - 1;
							dctOp = NONE;
						}
					}
					break;
				case ROT_180:
					ix = widthMCU - 1 - new_ix;
					iy = heightMCU - 1 - new_iy;
					if (handleXEdge) {
						if (ix > 0)
							ix--;
						else {
							ix = widthMCU - 1;
							dctOp = FLIP_V;
						}
					}
					if (handleYEdge) {
						if (iy > 0)
							iy--;
						else {
							iy = heightMCU - 1;
							dctOp = dctOp == ROT_180 ? FLIP_H : NONE;
						}
					}
					break;
				case CROP:
					ix = new_ix + xCropOffsetMCU;
					iy = new_iy + yCropOffsetMCU;
					dctOp = NONE;
					break;
				case NONE:
				default:
					ix = new_ix;
					iy = new_iy;
					break;
				}
				next_mcu = dct_coefs[iy][ix];

				try {
					for (int c = 0; c < components_in_scan; c++) {
						if (!transformDct)
							encoder.setTables(ac_table[c], dc_table[c]);
						switch (dctOp) {
						case TRANSPOSE:
							for (int mx = 0; mx < V[c]; mx++) {
								for (int my = 0; my < H[c]; my++) {
									int dct[][] = next_mcu[off + my * V[c] + mx];
									int new_dct[][] = transposeDCT(dct);
									if (transformDct)
										new_mcu[new_off++] = new_dct;
									else
										last_dc[c] = encoder.encode(new_dct,
												last_dc[c], dct[0].length);
								}
							}
							break;
						case ROT_90:
							for (int mx = 0; mx < V[c]; mx++) {
								for (int my = H[c] - 1; my >= 0; my--) {
									int dct[][] = next_mcu[off + my * V[c] + mx];
									int new_dct[][] = rotate90DCT(dct);
									if (transformDct)
										new_mcu[new_off++] = new_dct;
									else
										last_dc[c] = encoder.encode(new_dct,
												last_dc[c], dct[0].length);
								}
							}
							break;
						case ROT_270:
							for (int mx = V[c] - 1; mx >= 0; mx--) {
								for (int my = 0; my < H[c]; my++) {
									int dct[][] = next_mcu[off + my * V[c] + mx];
									int new_dct[][] = rotate270DCT(dct);
									if (transformDct)
										new_mcu[new_off++] = new_dct;
									else
										last_dc[c] = encoder.encode(new_dct,
												last_dc[c], dct[0].length);
								}
							}
							break;
						case TRANSVERSE:
							for (int mx = V[c] - 1; mx >= 0; mx--) {
								for (int my = H[c] - 1; my >= 0; my--) {
									int dct[][] = next_mcu[off + my * V[c] + mx];
									int new_dct[][] = transverseDCT(dct);
									if (transformDct)
										new_mcu[new_off++] = new_dct;
									else
										last_dc[c] = encoder.encode(new_dct,
												last_dc[c], dct[0].length);
								}
							}
							break;
						case FLIP_H:
							for (int my = 0; my < V[c]; my++) {
								for (int mx = H[c] - 1; mx >= 0; mx--) {
									int dct[][] = next_mcu[off + my * H[c] + mx];
									int new_dct[][] = flipHDct(dct);
									if (transformDct)
										new_mcu[new_off++] = new_dct;
									else
										last_dc[c] = encoder.encode(new_dct,
												last_dc[c], dct[0].length);
								}
							}
							break;
						case FLIP_V:
							for (int my = V[c] - 1; my >= 0; my--) {
								for (int mx = 0; mx < H[c]; mx++) {
									int dct[][] = next_mcu[off + my * H[c] + mx];
									int new_dct[][] = flipVDct(dct);
									if (transformDct)
										new_mcu[new_off++] = new_dct;
									else
										last_dc[c] = encoder.encode(new_dct,
												last_dc[c], dct[0].length);
								}
							}
							break;
						case ROT_180:
							for (int my = V[c] - 1; my >= 0; my--) {
								for (int mx = H[c] - 1; mx >= 0; mx--) {
									int dct[][] = next_mcu[off + my * H[c] + mx];
									int new_dct[][] = rotate180Dct(dct);
									if (transformDct)
										new_mcu[new_off++] = new_dct;
									else
										last_dc[c] = encoder.encode(new_dct,
												last_dc[c], dct[0].length);
								}
							}
							break;
						case NONE:
						default:
							for (int b = 0; b < V[c] * H[c]; b++) {
								int dct[][] = next_mcu[off + b];
								if (transformDct)
									new_mcu[new_off++] = dct;
								else
									last_dc[c] = encoder.encode(dct,
											last_dc[c], dct[0].length);
							}
							break;
						}
						off += V[c] * H[c];
					}

					if (transformDct)
						new_mcu = next_mcu;
					else {
						if (restart_interval != 0 && --restarts_to_go == 0) {
							restarts_to_go = restart_interval;
							if (_Ss == 0) {
								for (int k = 0; k < last_dc.length; k++)
									last_dc[k] = 0;
							}
							encoder.restart();
						}

						currentProgress += progressPerMcu;
						if (writeProgressCallback != null
								&& currentProgress - callbackProgress > writeProgressCallback
										.getCallbackInterval()) {
							callbackProgress = currentProgress;
							writeProgressCallback.progressHandler(
									callbackProgress,
									(int) Math.round(callbackProgress * 100));
						}
					}
				} catch (RestartException re) {
					// re.printStackTrace();
					restarts_to_go = 0;
				}
			}
			new_ix = 0;
			new_dct_row = dct_coefs[iy];
			if (pullDownMode)
				dct_coefs[iy] = null;
		}

		iWriteVars.new_ix = new_ix;
		iWriteVars.new_iy = new_iy;
		iWriteVars.restarts_to_go = restarts_to_go;
		iWriteVars.currentProgress = currentProgress;
		iWriteVars.callbackProgress = callbackProgress;

		if (new_iy >= heightMCU) {
			retVal = false;
			if (transformDct)
				dct_coefs = new_dct_coefs;
			else
				encoder.flush();
			// Cleanup
			encoder = null;
		}

		return retVal;
	}

	// class variables partialXMCU and partialYMCU should be set to indicate
	// partial X & Y blocks without transpose
	private void writeDCT(OutputStream os, int op, int options,
			int restart_interval, boolean transformDct) throws IOException {
		initWriteDCT(os, op, options, restart_interval, transformDct);
		do
			; while (writeNextDCT(10000000));
	}

	// Utility method to copy dct coeffictients for transforming methods
	// to avoid changing the main array while gatheringStats or if not
	// changing the original image
	private static void copyDct(int srcDct[][], int destDct[][]) {
		int i;
		for (i = 0; i < srcDct.length; ++i)
			System.arraycopy(srcDct[i], 0, destDct[i], 0, srcDct[i].length);
	}

	private static void compactDct(int tmpCoef[], int destDct[][]) {
		int i, k;
		for (i = k = 1; i < tmpCoef.length; i++) {
			if (tmpCoef[i] != 0) {
				destDct[1][k] = i;
				destDct[0][k] = tmpCoef[i];
				k++;
			}
		}
	}

	// In all the dct coefficient transformation routines if the class variable
	// retainDct is true then the new dct is written to tmp_dct, else the new
	// dct is written to the passed dct array
	//
	/**
	 * Internal method to Transpose a dct array
	 * 
	 * @param dct
	 *            Dct Coefficient array
	 * @see #retainDct
	 */
	protected int[][] transposeDCT(int[][] dct) {
		int i, k;
		// In all dct transform method tmp_dct is used as a temporary coeff
		// array in addition to being used as a return value
		// in case retainDct is true meaning the original dct should not be
		// written to.
		int tmpCoef[] = tmp_dct[0];
		for (i = 0; i < tmpCoef.length; ++i)
			tmpCoef[i] = 0;
		for (i = 0; i < dct[0].length; i++) {
			k = jpegnaturalorder[dct[1][i]];
			k = ((k & 7) << 3) + (k >> 3);
			tmpCoef[jpegzigzagorder[k]] = dct[0][i];
		}
		int retVal[][] = retainDct ? tmp_dct : dct;
		compactDct(tmpCoef, retVal);
		return retVal;
	}

	/**
	 * Internal method to Rotate clockwise 90 degrees a dct array
	 * 
	 * @param dct
	 *            Dct Coefficient array
	 * @see #retainDct
	 */
	protected int[][] rotate90DCT(int[][] dct) {
		int i, k;
		int tmpCoef[] = tmp_dct[0];
		for (i = 0; i < tmpCoef.length; ++i)
			tmpCoef[i] = 0;
		for (i = 0; i < dct[0].length; i++) {
			k = jpegnaturalorder[dct[1][i]];
			k = ((k & 7) << 3) + (k >> 3);
			tmpCoef[jpegzigzagorder[k]] = (k & 1) == 1 ? -dct[0][i] : dct[0][i];
		}
		int retVal[][] = retainDct ? tmp_dct : dct;
		compactDct(tmpCoef, retVal);
		return retVal;
	}

	/**
	 * Internal method to Rotate clockwise 270 degrees a dct array
	 * 
	 * @param dct
	 *            Dct Coefficient array
	 * @see #retainDct
	 */
	protected int[][] rotate270DCT(int[][] dct) {
		int i, k;
		int tmpCoef[] = tmp_dct[0];
		for (i = 0; i < tmpCoef.length; ++i)
			tmpCoef[i] = 0;
		for (i = 0; i < dct[0].length; i++) {
			k = jpegnaturalorder[dct[1][i]];
			k = ((k & 7) << 3) + (k >> 3);
			tmpCoef[jpegzigzagorder[k]] = (k & 8) == 8 ? -dct[0][i] : dct[0][i];
		}
		int retVal[][] = retainDct ? tmp_dct : dct;
		compactDct(tmpCoef, retVal);
		return retVal;
	}

	/**
	 * Internal method to Transverse a dct array
	 * 
	 * @param dct
	 *            Dct Coefficient array
	 * @see #retainDct
	 */
	protected int[][] transverseDCT(int[][] dct) {
		int i, k;
		int tmpCoef[] = tmp_dct[0];
		for (i = 0; i < tmpCoef.length; ++i)
			tmpCoef[i] = 0;
		boolean neg;
		for (i = 0; i < dct[0].length; i++) {
			k = jpegnaturalorder[dct[1][i]];
			neg = (k & 1) != 0;
			k = ((k & 7) << 3) + (k >> 3);
			neg ^= (k & 1) != 0;
			tmpCoef[jpegzigzagorder[k]] = neg ? -dct[0][i] : dct[0][i];
		}
		int retVal[][] = retainDct ? tmp_dct : dct;
		compactDct(tmpCoef, retVal);
		return retVal;
	}

	/**
	 * Internal method to Horizontally Flip a dct array
	 * 
	 * @param dct
	 *            Dct Coefficient array
	 * @see #retainDct
	 */
	protected int[][] flipHDct(int[][] dct) {
		int retVal[][] = dct;
		int len = dct[0].length;
		if (retainDct) {
			copyDct(dct, tmp_dct);
			retVal = tmp_dct;
		}
		for (int k = 0; k < len; k++) {
			if ((jpegnaturalorder[retVal[1][k]] & 1) != 0)
				retVal[0][k] = -retVal[0][k];
		}
		return retVal;
	}

	/**
	 * Internal method to Vertically Flip a dct array
	 * 
	 * @param dct
	 *            Dct Coefficient array
	 * @see #retainDct
	 */
	protected int[][] flipVDct(int[][] dct) {
		int retVal[][] = dct;
		int len = dct[0].length;
		if (retainDct) {
			copyDct(dct, tmp_dct);
			retVal = tmp_dct;
		}
		for (int k = 0; k < len; k++) {
			if ((jpegnaturalorder[retVal[1][k]] & 8) == 8)
				retVal[0][k] = -retVal[0][k];
		}
		return retVal;
	}

	/**
	 * Internal method to Rotate 180 degrees a dct array
	 * 
	 * @param dct
	 *            Dct Coefficient array
	 * @see #retainDct
	 */
	protected int[][] rotate180Dct(int[][] dct) {
		int retVal[][] = dct;
		int len = dct[0].length;
		if (retainDct) {
			copyDct(dct, tmp_dct);
			retVal = tmp_dct;
		}
		for (int k = 0; k < len; k++) {
			// For even row, negate every odd column.
			// For odd row, negate every even column.
			if (((jpegnaturalorder[retVal[1][k]] & 9) == 1)
					|| ((jpegnaturalorder[retVal[1][k]] & 9) == 8))
				retVal[0][k] = -retVal[0][k];
		}
		return retVal;
	}

	/**
	 * Method used for debugging Iterative Read/Writes.
	 * 
	 * @param which
	 *            Identifies what RequestSize to return. 0: minReadRequest, 1:
	 *            maxReadRequest, 2: minWriteRequest, 3: maxWriteRequest
	 * @return Corresponding numBytes requestSize to nextRead/nextWrite
	 * @see IterativeReader#nextRead(int)
	 * @see IterativeWriter#nextWrite(int)
	 */
	public int getRequestSize(int which) {
		int retVal = -1;

		switch (which) {
		case 0:
			retVal = iReadVars.minReadRequest;
			break;
		case 1:
			retVal = iReadVars.maxReadRequest;
			break;
		case 2:
			retVal = iWriteVars.minWriteRequest;
			break;
		case 3:
			retVal = iWriteVars.maxWriteRequest;
			break;
		default:
			break;
		}

		return retVal;
	}

	// trimRightEdge
	// trimBottomEdge

	private class HuffDecoder {
		private InputStream is;
		int bit_buff;
		int bit_buff_len;

		int[] cur_maxcode, cur_huffval, cur_valoffset;

		HuffDecoder(InputStream is) {
			this.is = is;
		}

		void setTables(boolean ac, int index) {
			if (ac) {
				// find index of table
				for (int i = 0; i < ac_ix.length; i++) {
					if (ac_ix[i] == index) { // found
						cur_maxcode = ac_maxcode[i];
						cur_huffval = ac_huffval[i];
						cur_valoffset = ac_valoffset[i];
						break;
					}
				}
			} else {
				// find index of table
				for (int i = 0; i < dc_ix.length; i++) {
					if (dc_ix[i] == index) { // found
						cur_maxcode = dc_maxcode[i];
						cur_huffval = dc_huffval[i];
						cur_valoffset = dc_valoffset[i];
						break;
					}
				}
			}
		}

		void checkBitBuffer(int len) throws IOException, RestartException {
			if (bit_buff_len < len) {
				int nextbyte;
				if (len > 16) // !!!
					throw new IOException(
							"An attempt to read more than 16 bit (inbuff="
									+ bit_buff_len + ", len=" + len + ") ("
									+ getLocationName() + ")");
				do {
					nextbyte = read();
					// Forget it if we have hit an unprocessed_marker which
					// should be a restart marker
					if (unprocessed_marker != 0)
						break;

					// Fill bit_buff till we have atleast len bits
					bit_buff <<= BYTE_SIZE;
					bit_buff |= nextbyte;
					bit_buff_len += BYTE_SIZE;
				} while (bit_buff_len < len);
			}
		}

		int read() throws IOException, RestartException {
			int result = -1;

			// Read a byte only if we have not hit a marker while decoding
			if (unprocessed_marker == 0) {
				result = is.read();
				readcounter++;

				// Special Cases
				if (result == -1)
					throw new IOException("End of file reached at "
							+ readcounter + " (" + getLocationName() + ")");
				if (result == 0xff) {
					// Skip 0xff filling
					do {
						result = is.read();
						readcounter++;
					} while (result == 0xff);

					if (result == 0)
						result = 0xff; // 0xff followed by 0 means 0xff
					else
						// marker found. Further calls to read if any will do
						// nothing and checkBitBuffer will not fill bit_buff
						unprocessed_marker = result;
				}
			}
			return result;
		}

		int getBits(int len) throws IOException, RestartException {
			int retVal = 0;
			checkBitBuffer(len);

			// Go ahead if any bits are available for reading. Otherwise all 0's
			// will be returned which is the case for calls after a marker is
			// encountered while decoding till the next MCU block
			if (bit_buff_len > 0) {
				if (bit_buff_len >= len) {
					// Under Normal case of len bits being available take 1st
					// len bits.
					bit_buff_len -= len;
					retVal = (bit_buff >> bit_buff_len)
							& (0xffff >> (16 - len));
				} else {
					// If Enough bits are not available (Due to marker
					// encounter) then take what is available.
					int defecit = len - bit_buff_len;
					bit_buff_len = 0;
					// Fill the deficit bits with zeroes
					retVal = (bit_buff << defecit) & (0xffff >> (16 - len));
				}
			}

			return retVal;
		}

		int extend(int n_bits) throws IOException, RestartException {
			if (n_bits == 0)
				return 0;
			int result = getBits(n_bits);
			return ((result) < (1 << ((n_bits) - 1)) ? (result)
					+ (((-1) << (n_bits)) + 1) : (result));
		}

		int decode(int min_bits) throws IOException, RestartException {
			int l = min_bits;
			// decode has determined that the code is at least min_bits
			// bits long, so fetch that many bits in one swoop.
			int code = getBits(l);

			// Collect the rest of the Huffman code one bit at a time.
			// This is per Figure F.16 in the JPEG spec.
			while (code > cur_maxcode[l]) {
				if (code < 0)
					if (Log.debugLevel >= Log.LEVEL_ERROR)
						android.util.Log.e(
								TAG,
								"Negative code 0x" + Integer.toHexString(code)
										+ " max 0x"
										+ Integer.toHexString(cur_maxcode[l]));
				code <<= 1;
				code |= getBits(1);
				// code &= 0xFFFF;
				if (++l > 16)
					throw new IOException(
							"Corrupted JPEG data: bad Huffman code, 0x"
									+ Integer.toHexString(code) + " max 0x"
									+ Integer.toHexString(cur_maxcode[l])
									+ " at 0x"
									+ Integer.toHexString(readcounter) + " ("
									+ getLocationName() + ")");
			}
			return cur_huffval[code + cur_valoffset[l]];
		}

		void restart() {
			bit_buff_len = 0;
			bit_buff = 0;
			// System.err.println("Restart at offset 0x"+Long.toHexString(readcounter));
		}
	}

	private class HuffEncoder {
		private int bufferputbits;
		private int bufferputbuffer;
		private OutputStream outputstream;
		private int[][] dc_ecodetable, ac_ecodetable;
		int next_restart_num;
		int acTblIndex, dcTblIndex;

		public HuffEncoder(OutputStream os) {
			outputstream = os;
		}

		void setTables(int iac, int idc) {
			boolean dt_found = false, at_found = false;
			for (int i = 0; i < ac_ix.length; i++) {
				if (ac_ix[i] == iac) { // found
					if (!gatheringStats)
						ac_ecodetable = enc_ac_matrix[i];
					this.acTblIndex = i;
					at_found = true;
					break;
				}
			}
			for (int i = 0; i < dc_ix.length; i++) {
				if (dc_ix[i] == idc) { // found
					if (!gatheringStats)
						dc_ecodetable = enc_dc_matrix[i];
					this.dcTblIndex = i;
					dt_found = true;
					break;
				}
			}
			if (at_found == false || dt_found == false)
				if (Log.debugLevel >= Log.LEVEL_ERROR)
					android.util.Log.e(TAG, "One of tables not found for a "
							+ iac + " " + at_found + " d " + idc + " "
							+ dt_found);
		}

		// The length is required to be passed since it may not be
		// coeff[0].length if the dct is tmp_dct.
		int encode(int coef[][], int last_dc, int len) throws IOException,
				RestartException {
			if (coef == null || coef[0] == null)
				throw new RestartException(0);
			int temp, temp2, nbits, k, r, i;
			// The DC portion
			temp = temp2 = coef[0][0] - last_dc;
			if (temp < 0) {
				temp = -temp;
				temp2--;
			}
			nbits = 0;
			while (temp != 0) {
				nbits++;
				temp >>= 1;
			}
			if (gatheringStats)
				huffGen.updateDCCount(dcTblIndex, nbits);
			else
				writeCode(dc_ecodetable[nbits][0], dc_ecodetable[nbits][1]);
			// The arguments in bufferIt are code and size.
			if (nbits != 0)
				writeCode(temp2, nbits);

			// The AC portion
			for (k = 1; k < len; k++) {
				r = coef[1][k] - coef[1][k - 1] - 1;
				while (r > 15) { // write for 0 ig gap > 15
					if (gatheringStats)
						huffGen.updateACCount(acTblIndex, 0xF0);
					else
						writeCode(ac_ecodetable[0xF0][0],
								ac_ecodetable[0xF0][1]);
					r -= 16;
				}
				temp = temp2 = coef[0][k];
				if (temp < 0) {
					temp = -temp;
					temp2--;
				}
				nbits = 1; // temp is never 0
				while ((temp >>= 1) != 0)
					nbits++;
				i = (r << 4) + nbits;
				if (gatheringStats)
					huffGen.updateACCount(acTblIndex, i);
				else
					writeCode(ac_ecodetable[i][0], ac_ecodetable[i][1]);
				writeCode(temp2, nbits);
			}

			if ((63 - coef[1][len - 1]) > 0) { // mark that the end of data
				if (gatheringStats)
					huffGen.updateACCount(acTblIndex, 0);
				else
					writeCode(ac_ecodetable[0][0], ac_ecodetable[0][1]);
			}

			return coef[0][0];
		}

		void restart() throws IOException {
			if (gatheringStats)
				return;
			flush();
			outputstream.write(M_PRX);
			outputstream.write((M_RST0 & 255) + next_restart_num);
			writecounter += 2;
			next_restart_num = (next_restart_num + 1) & 7;
			bufferputbits = bufferputbuffer = 0;
		}

		// Uses an integer long (32 bits) buffer to store the Huffman encoded
		// bits
		// and sends them to out stream by the byte.

		void writeCode(int code, int size) throws IOException {
			if (gatheringStats)
				return;
			if (size == 0)
				throw new RuntimeException(
						"Runtime Error: Missing Huffman Table Entry");
			int putbuffer = code;
			int putbits = bufferputbits;
			putbuffer &= (1 << size) - 1;
			putbits += size;
			putbuffer <<= 24 - putbits;
			putbuffer |= bufferputbuffer;
			int c;
			while (putbits >= 8) {
				c = ((putbuffer >> 16) & 0xff);
				outputstream.write(c);
				writecounter++;
				if (c == 0xff) {
					outputstream.write(0);
					writecounter++;
				}
				putbuffer <<= 8;
				putbits -= 8;
			}
			bufferputbuffer = putbuffer;
			bufferputbits = putbits;
		}

		void flush() throws IOException {
			if (gatheringStats)
				return;
			int putbuffer = bufferputbuffer;
			int putbits = bufferputbits;
			int c;
			while (putbits >= 8) {
				c = (putbuffer >> 16) & 0xff;
				outputstream.write(c);
				writecounter++;
				if (c == 0xFF) {
					outputstream.write(0);
					writecounter++;
				}
				putbuffer <<= 8;
				putbits -= 8;
			}
			if (putbits > 0) {
				c = (putbuffer >> 16) & (0xff00 >> putbits) & 0xff;
				outputstream.write(c);
				writecounter++;
			}
			bufferputbuffer = putbuffer;
			bufferputbits = putbits;
		}
	}

	private class HuffGenerator {
		/*
		 * Symbol(byte) frequencies for each of the [da]HuffTblCount Huff tables
		 */
		private int dc_count[][];
		private int ac_count[][];

		/* Equivalent to constructor. Allocates frequency arrays */
		public void init() {
			/* Allocate space for frequency table */
			dc_count = new int[dc_ix.length][257];
			ac_count = new int[ac_ix.length][257];
		}

		public void freeMemory() {
			dc_count = null;
			ac_count = null;
		}

		public HuffGenerator() {
		}

		public void updateDCCount(int tableIndex, int symbol) {
			++dc_count[tableIndex][symbol];
		}

		public void updateACCount(int tableIndex, int symbol) {
			++ac_count[tableIndex][symbol];
		}

		/*
		 * Code is modified from jpeg_gen_optimal_table function in jchuff.c
		 * file of IJG code. It writes to bs the Number of symbols for each of
		 * 1..16 code lengths followed by the actual codes for lenghts 1..16 as
		 * expected in the JPEG format. If bs is a ByteArrayOutputStream then
		 * the bytes can be recovered from it.
		 */
		private void genOptimalTable(OutputStream os, int freq[])
				throws IOException {
			int bits[] = new int[MAX_CLEN + 1]; /*
												 * bits[k] = # of symbols with
												 * code length k
												 */
			int codesize[] = new int[257]; /*
											 * codesize[k] = code length of
											 * symbol k
											 */
			int others[] = new int[257]; /* next symbol in current branch of tree */
			int c1, c2;
			int i, j;
			int v;
			/* This algorithm is explained in section K.2 of the JPEG standard */

			for (i = 0; i < 257; i++)
				others[i] = -1; /* init links to empty */

			freq[256] = 1; /* make sure 256 has a nonzero count */
			/*
			 * Including the pseudo-symbol 256 in the Huffman procedure
			 * guarantees that no real symbol is given code-value of all ones,
			 * because 256 will be placed last in the largest codeword category.
			 */

			/*
			 * Huffman's basic algorithm to assign optimal code lengths to
			 * symbols
			 */

			for (;;) {
				/* Find the smallest nonzero frequency, set c1 = its symbol */
				/* In case of ties, take the larger symbol number */
				c1 = -1;
				v = 1000000000;
				for (i = 0; i <= 256; i++) {
					if (freq[i] > 0 && freq[i] <= v) {
						v = freq[i];
						c1 = i;
					}
				}

				/* Find the next smallest nonzero frequency, set c2 = its symbol */
				/* In case of ties, take the larger symbol number */
				c2 = -1;
				v = 1000000000;
				for (i = 0; i <= 256; i++) {
					if (freq[i] > 0 && freq[i] <= v && i != c1) {
						v = freq[i];
						c2 = i;
					}
				}

				/* Done if we've merged everything into one frequency */
				if (c2 < 0)
					break;

				/* Else merge the two counts/trees */
				freq[c1] += freq[c2];
				freq[c2] = 0;

				/* Increment the codesize of everything in c1's tree branch */
				codesize[c1]++;
				while (others[c1] >= 0) {
					c1 = others[c1];
					codesize[c1]++;
				}

				others[c1] = c2; /* chain c2 onto c1's tree branch */

				/* Increment the codesize of everything in c2's tree branch */
				codesize[c2]++;
				while (others[c2] >= 0) {
					c2 = others[c2];
					codesize[c2]++;
				}
			}

			/* Now count the number of symbols of each code length */
			for (i = 0; i <= 256; i++) {
				if (codesize[i] > 0) {
					/* The JPEG standard seems to think that this can't happen, */
					/* but I'm paranoid... */
					if (codesize[i] > MAX_CLEN)
						throw new RuntimeException(
								"Internal Error regenerating Huff Tables: Code Length "
										+ codesize[i] + " for symbol " + i
										+ " > 32");

					bits[codesize[i]]++;
				}
			}

			/*
			 * JPEG doesn't allow symbols with code lengths over 16 bits, so if
			 * the pure Huffman procedure assigned any such lengths, we must
			 * adjust the coding. Here is what the JPEG spec says about how this
			 * next bit works: Since symbols are paired for the longest Huffman
			 * code, the symbols are removed from this length category two at a
			 * time. The prefix for the pair (which is one bit shorter) is
			 * allocated to one of the pair; then, skipping the BITS entry for
			 * that prefix length, a code word from the next shortest nonzero
			 * BITS entry is converted into a prefix for two code words one bit
			 * longer.
			 */

			for (i = MAX_CLEN; i > 16; i--) {
				while (bits[i] > 0) {
					j = i - 2; /* find length of new prefix to be used */
					while (bits[j] == 0)
						j--;

					bits[i] -= 2; /* remove two symbols */
					bits[i - 1]++; /* one goes in this length */
					bits[j + 1] += 2; /* two new symbols in this length */
					bits[j]--; /* symbol of this length is now a prefix */
				}
			}

			/*
			 * Remove the count for the pseudo-symbol 256 from the largest
			 * codelength
			 */
			while (bits[i] == 0)
				/* find largest codelength still in use */
				i--;
			bits[i]--;

			/* Output final symbol counts (only for lengths 1..16) */
			for (i = 1; i <= 16; ++i)
				os.write(bits[i]);

			/* Output a list of the symbols sorted by code length */
			/*
			 * It's not real clear to me why we don't need to consider the
			 * codelength changes made above, but the JPEG spec seems to think
			 * this works.
			 */
			for (i = 1; i <= MAX_CLEN; i++) {
				for (j = 0; j <= 255; j++) {
					if (codesize[j] == i) {
						os.write(j);
					}
				}
			}
		}

		/*
		 * Writes out the Huffman Tables to os as per JPEG spec excluding marker
		 * and length
		 */
		private void writeHuffTables(OutputStream os) throws IOException {
			int tableIndex, htInfo;

			/* Write out dc huff tables */
			for (tableIndex = 0; tableIndex < dc_ix.length; tableIndex++) {
				htInfo = dc_ix[tableIndex];
				os.write(htInfo);
				genOptimalTable(os, dc_count[tableIndex]);
			}

			/* Write out ac huff tables */
			for (tableIndex = 0; tableIndex < ac_ix.length; tableIndex++) {
				htInfo = 16 + ac_ix[tableIndex];
				os.write(htInfo);
				genOptimalTable(os, ac_count[tableIndex]);
			}
		}
	}

	private class RestartException extends Exception {
		/**
		 * 
		 */
		private static final long serialVersionUID = -4260953232744779223L;

		RestartException(int scan) {
			super(String.valueOf(scan));
		}
	}

	/**
	 * Internal variable indicating number of bytes written
	 */
	protected int writecounter;
	/**
	 * Internal variable indicating number of bytes read
	 */
	protected int readcounter;
	private boolean gatheringStats;

	// image parameters
	private int components_in_scan;
	private int components_in_frame;
	private int frm_precision;
	private int[] comp_ids;
	private int[] dc_table;
	private int[] ac_table;
	private int _Ss, _Se, _Ah, _Al;
	// frame parameters
	private int frm_x;
	private int frm_y;
	private int[] V, H, QT, ID;
	private int maxHi, maxVi, widthMCU, heightMCU;
	private int mcusize;
	/**
	 * Internal variable containing restart interval
	 */
	protected int restart_interval;

	private int[][] dc_valoffset;
	private int[][] dc_maxcode;
	private int[][] dc_huffval;
	private int[] dc_ix;

	private int[][] ac_valoffset;
	private int[][] ac_maxcode;
	private int[][] ac_huffval;
	private int[][] dc_huffbits, ac_huffbits;
	private int[] ac_ix;
	private int[][] q_table;
	private int[] q_ix;
	private int[] q_prec;
	private int[][][][][] dct_coefs;
	/**
	 * In all dct transform method tmp_dct is used as a temporary coeff array in
	 * addition to being used as a return value in case retainDct is true
	 * meaning the original dct should not be written to.
	 */
	protected int[][] tmp_dct = new int[2][DCTSIZE2];

	private Rect cropBounds = new Rect();

	private int[][][] enc_ac_matrix;
	private int[][][] enc_dc_matrix;

	private HuffGenerator huffGen;
	private ProgressCallback readProgressCallback, writeProgressCallback;

	/**
	 * Internal variable containing unprocessed_marker. 0 if none
	 */
	protected int unprocessed_marker;
	private String unprocessedError;
	private boolean xferDone = false;
	/**
	 * Internal variable indicating if image is valid.
	 */
	protected boolean valid;
	/**
	 * Internal variable indicating if the current image can be processed by
	 * LLJTran.
	 */
	protected boolean canBeProcessed; // 22
	/**
	 * Internal variable indicating How much of the image has been read so far.
	 * 
	 * @see #READ_INFO
	 */
	protected int readUpto;
	/**
	 * Internal variable indicating input file. null if reading from InputStream
	 */
	protected File file;
	/**
	 * Internal variable indicating input stream. null if reading from file
	 */
	protected InputStream inStream;
	/**
	 * Internal variable containing Internal InputStream
	 */
	protected InputStream currentStream;
	/**
	 * Internal variable containing marker to be processed
	 */
	protected byte[] markerid;
	/**
	 * Internal variable containing imageinfo
	 */
	protected AbstractImageInfo<?> imageinfo;

	/**
	 * Internal variable containing appx Marker Data
	 */
	protected byte[] appxs[];
	// protected byte[] comment_data;

	private int appHdrIndex = -1;

	/**
	 * Internal Variable containing comment data
	 */
	protected String out_comment;
	/**
	 * Internal variable indicating if Appx markers where retained during read
	 */
	protected boolean appxs_read;

	/**
	 * In all the dct coefficient transformation routines if the class variable
	 * retainDct is true then the new dct is written to tmp_dct, else the new
	 * dct is written to the passed dct array
	 */
	protected boolean retainDct;
	private byte prevHuffOption;
	private IterativeReadVars iReadVars;
	private IterativeWriteVars iWriteVars;

	private Exception lljtError;
	private String errorMsg;

	private static final String uptoName[] = { "None", "Info", "Header", "Body" };

	/**
	 * This array contains the data for a Dummy Exif Header. This can be used to
	 * create an Exif header for a jpeg image without one.
	 */
	public static final byte dummyExifHeader[] = { (byte) 0xff, (byte) 0xe1,
			0x3, 0x37, 0x45, 0x78, 0x69, 0x66, 0x0, 0x0, 0x49, 0x49, 0x2a, 0x0,
			0x8, 0x0, 0x0, 0x0, 0x9, 0x0, 0x28, 0x1, 0x3, 0x0, 0x1, 0x0, 0x0,
			0x0, 0x2, 0x0, 0x0, 0x0, 0x12, 0x1, 0x3, 0x0, 0x1, 0x0, 0x0, 0x0,
			0x1, 0x0, 0x0, 0x0, 0x32, 0x1, 0x2, 0x0, 0x14, 0x0, 0x0, 0x0, 0x7a,
			0x0, 0x0, 0x0, 0x1b, 0x1, 0x5, 0x0, 0x1, 0x0, 0x0, 0x0,
			(byte) 0x8e, 0x0, 0x0, 0x0, 0x10, 0x1, 0x2, 0x0, 0x15, 0x0, 0x0,
			0x0, (byte) 0x96, 0x0, 0x0, 0x0, 0x1a, 0x1, 0x5, 0x0, 0x1, 0x0,
			0x0, 0x0, (byte) 0xab, 0x0, 0x0, 0x0, 0xf, 0x1, 0x2, 0x0, 0x8, 0x0,
			0x0, 0x0, (byte) 0xb3, 0x0, 0x0, 0x0, 0x13, 0x2, 0x3, 0x0, 0x1,
			0x0, 0x0, 0x0, 0x1, 0x0, 0x0, 0x0, 0x69, (byte) 0x87, 0x4, 0x0,
			0x1, 0x0, 0x0, 0x0, (byte) 0xbb, 0x0, 0x0, 0x0, (byte) 0xe9, 0x2,
			0x0, 0x0, 0x32, 0x30, 0x30, 0x32, 0x3a, 0x30, 0x33, 0x3a, 0x31,
			0x38, 0x20, 0x31, 0x34, 0x3a, 0x30, 0x38, 0x3a, 0x34, 0x34, 0x0,
			(byte) 0xb4, 0x0, 0x0, 0x0, 0x1, 0x0, 0x0, 0x0, 0x55, 0x6e, 0x6b,
			0x6e, 0x6f, 0x77, 0x6e, 0x20, 0x66, 0x72, 0x6f, 0x6d, 0x20, 0x4c,
			0x4c, 0x4a, 0x54, 0x72, 0x61, 0x6e, 0x0, (byte) 0xb4, 0x0, 0x0,
			0x0, 0x1, 0x0, 0x0, 0x0, 0x55, 0x6e, 0x6b, 0x6e, 0x6f, 0x77, 0x6e,
			0x0, 0x1e, 0x0, 0x17, (byte) 0xa2, 0x3, 0x0, 0x1, 0x0, 0x0, 0x0,
			0x2, 0x0, 0x0, 0x0, 0x0, (byte) 0xa3, 0x7, 0x0, 0x1, 0x0, 0x0, 0x0,
			0x3, 0x0, 0x0, 0x0, 0x2, (byte) 0x91, 0x5, 0x0, 0x1, 0x0, 0x0, 0x0,
			0x29, 0x2, 0x0, 0x0, 0x1, (byte) 0x91, 0x7, 0x0, 0x4, 0x0, 0x0,
			0x0, 0x1, 0x2, 0x3, 0x0, 0x10, (byte) 0xa2, 0x3, 0x0, 0x1, 0x0,
			0x0, 0x0, 0x2, 0x0, 0x0, 0x0, 0xf, (byte) 0xa2, 0x5, 0x0, 0x1, 0x0,
			0x0, 0x0, 0x31, 0x2, 0x0, 0x0, 0xe, (byte) 0xa2, 0x5, 0x0, 0x1,
			0x0, 0x0, 0x0, 0x39, 0x2, 0x0, 0x0, 0x3, (byte) 0xa0, 0x3, 0x0,
			0x1, 0x0, 0x0, 0x0, (byte) 0xb0, 0x4, 0x0, 0x0, 0x2, (byte) 0xa0,
			0x3, 0x0, 0x1, 0x0, 0x0, 0x0, 0x40, 0x6, 0x0, 0x0, 0x1,
			(byte) 0xa0, 0x3, 0x0, 0x1, 0x0, 0x0, 0x0, 0x1, 0x0, 0x0, 0x0, 0x0,
			(byte) 0xa0, 0x7, 0x0, 0x4, 0x0, 0x0, 0x0, 0x30, 0x31, 0x30, 0x30,
			0xa, (byte) 0x92, 0x5, 0x0, 0x1, 0x0, 0x0, 0x0, 0x41, 0x2, 0x0,
			0x0, 0x9, (byte) 0x92, 0x3, 0x0, 0x1, 0x0, 0x0, 0x0, 0x10, 0x0,
			0x0, 0x0, (byte) 0x9d, (byte) 0x82, 0x5, 0x0, 0x1, 0x0, 0x0, 0x0,
			0x49, 0x2, 0x0, 0x0, 0x4, (byte) 0x90, 0x2, 0x0, 0x14, 0x0, 0x0,
			0x0, 0x51, 0x2, 0x0, 0x0, 0x6, (byte) 0xa4, 0x3, 0x0, 0x1, 0x0,
			0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x3, (byte) 0x90, 0x2, 0x0, 0x14,
			0x0, 0x0, 0x0, 0x65, 0x2, 0x0, 0x0, 0x7, (byte) 0x92, 0x3, 0x0,
			0x1, 0x0, 0x0, 0x0, 0x5, 0x0, 0x0, 0x0, 0x4, (byte) 0xa4, 0x5, 0x0,
			0x1, 0x0, 0x0, 0x0, 0x79, 0x2, 0x0, 0x0, (byte) 0x9a, (byte) 82,
			0x5, 0x0, 0x1, 0x0, 0x0, 0x0, (byte) 0x81, 0x2, 0x0, 0x0, 0x3,
			(byte) 0xa4, 0x3, 0x0, 0x1, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x5,
			(byte) 0x92, 0x5, 0x0, 0x1, 0x0, 0x0, 0x0, (byte) 0x89, 0x2, 0x0,
			0x0, 0x0, (byte) 0x90, 0x7, 0x0, 0x4, 0x0, 0x0, 0x0, 0x30, 0x32,
			0x32, 0x30, 0x2, (byte) 0xa4, 0x3, 0x0, 0x1, 0x0, 0x0, 0x0, 0x0,
			0x0, 0x0, 0x0, 0x4, (byte) 0x92, 0xa, 0x0, 0x1, 0x0, 0x0, 0x0,
			(byte) 0x91, 0x2, 0x0, 0x0, 0x1, (byte) 0xa4, 0x3, 0x0, 0x1, 0x0,
			0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x2, (byte) 0x92, 0x5, 0x0, 0x1, 0x0,
			0x0, 0x0, (byte) 0x99, 0x2, 0x0, 0x0, 0x1, (byte) 0x92, 0xa, 0x0,
			0x1, 0x0, 0x0, 0x0, (byte) 0xa1, 0x2, 0x0, 0x0, (byte) 0x86,
			(byte) 0x92, 0x7, 0x0, 0xa, 0x0, 0x0, 0x0, (byte) 0xa9, 0x2, 0x0,
			0x0, 0x5, (byte) 0xa0, 0x4, 0x0, 0x1, 0x0, 0x0, 0x0, (byte) 0xb3,
			0x2, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x3, 0x0, 0x0, 0x0, 0x1, 0x0,
			0x0, 0x0, (byte) 0x80, 0x4f, 0x12, 0x0, (byte) 0x9b, 0x0, 0x0, 0x0,
			0x0, 0x6a, 0x18, 0x0, (byte) 0xce, 0x0, 0x0, 0x0, 0x6, 0x2, 0x0,
			0x0, 0x20, 0x0, 0x0, 0x0, (byte) 0x8c, 0x0, 0x0, 0x0, 0xa, 0x0,
			0x0, 0x0, 0x32, 0x30, 0x30, 0x32, 0x3a, 0x30, 0x33, 0x3a, 0x31,
			0x38, 0x20, 0x31, 0x34, 0x3a, 0x30, 0x38, 0x3a, 0x34, 0x34, 0x0,
			0x32, 0x30, 0x30, 0x32, 0x3a, 0x30, 0x33, 0x3a, 0x31, 0x38, 0x20,
			0x31, 0x34, 0x3a, 0x30, 0x38, 0x3a, 0x34, 0x34, 0x0, 0x40, 0x6,
			0x0, 0x0, 0x40, 0x6, 0x0, 0x0, 0x1, 0x0, 0x0, 0x0, 0x64, 0x0, 0x0,
			0x0, (byte) 0x8a, (byte) 0xf8, 0x2, 0x0, 0x0, 0x0, 0x1, 0x0, 0x0,
			0x0, 0x0, 0x0, 0x3, 0x0, 0x0, 0x0, 0x5e, (byte) 0x9d, 0x7, 0x0,
			0x0, 0x0, 0x1, 0x0, (byte) 0xd5, 0x0, 0x0, 0x0, 0x20, 0x0, 0x0,
			0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x4, 0x0,
			0x2, 0x10, 0x3, 0x0, 0x1, 0x0, 0x0, 0x0, (byte) 0xb0, 0x4, 0x0,
			0x0, 0x1, 0x10, 0x3, 0x0, 0x1, 0x0, 0x0, 0x0, 0x40, 0x6, 0x0, 0x0,
			0x2, 0x0, 0x7, 0x0, 0x4, 0x0, 0x0, 0x0, 0x30, 0x31, 0x30, 0x30,
			0x1, 0x0, 0x2, 0x0, 0x4, 0x0, 0x0, 0x0, 0x52, 0x39, 0x38, 0x0, 0x0,
			0x0, 0x0, 0x0, 0x4, 0x0, 0x28, 0x1, 0x3, 0x0, 0x1, 0x0, 0x0, 0x0,
			0x2, 0x0, 0x0, 0x0, 0x1b, 0x1, 0x5, 0x0, 0x1, 0x0, 0x0, 0x0, 0x1f,
			0x3, 0x0, 0x0, 0x1a, 0x1, 0x5, 0x0, 0x1, 0x0, 0x0, 0x0, 0x27, 0x3,
			0x0, 0x0, 0x3, 0x1, 0x3, 0x0, 0x1, 0x0, 0x0, 0x0, 0x6, 0x0, 0x0,
			0x0, 0x0, 0x0, 0x0, 0x0, (byte) 0xb4, 0x0, 0x0, 0x0, 0x1, 0x0, 0x0,
			0x0, (byte) 0xb4, 0x0, 0x0, 0x0, 0x1, 0x0, 0x0, 0x0 };

	/**
	 * Program Name
	 */
	public static final String PROGRAMNAME = "LLJTran";
}
