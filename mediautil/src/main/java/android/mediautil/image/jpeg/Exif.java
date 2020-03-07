/* MediaUtil LLJTran - $RCSfile: Exif.java,v $
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
 *  $Id: Exif.java,v 1.15 2009/09/28 03:31:51 drogatkin Exp $
 *
 * Some ideas and algorithms were borrowed from:
 * Thomas G. Lane, and James R. Weeks
 * Contribution for Maker Notes handling by Vincent Deconinck
 */
package android.mediautil.image.jpeg;

/**
 * For building this class were used the following sources
 * 1. Thierry Bousch <bousch@topo.math.u-psud.fr>
 * 2. ISO/DIS 12234-2
 *    Photography - Electronic still picture cameras - Removable Memory
 *    Part 2: Image data format - TIFF/EP (http://www.pima.net/it10a.htm)
 * 3. <a href="http://www.pima.net/standards/it10/PIMA15740/exif.htm"> some enhancements were based on </a>
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.Map;

import android.mediautil.generic.FileFormatException;
import android.mediautil.generic.Log;
import android.mediautil.generic.Rational;
import android.mediautil.image.jpeg.maker.MakerNoteHandler;
import android.mediautil.image.jpeg.maker.MakerNoteHandlerFactory;

// TODO: add loading custom/manufac exif specific properties from
// database in form of XML file
// where primary key is combination make+model
/**
 * This class represent the Exif header providing additional information about
 * image. It is organized in a similar IFD directory structure as specified by
 * the Exif spec.
 * <p>
 * 
 * The below shows the most common usage of creating and changing an Exif Entry:
 * <p>
 * 
 * <pre>
 *      // Use appropriate code to read an image into llj
 *      LLJTran llj = new LLJTran(..); llj.read(..);
 *      Exif exif = (Exif) llj.getImageInfo();
 * 
 *      // Create and entry for the EXIFIMAGEWIDTH tag
 *      // LONG is the appropriate Data Type for the EXIFIMAGEWIDTH tag
 *      Entry e = new Entry(LONG);
 *      e.setValue(0, Integer.valueOf(llj.getWidth()));
 *      exif.setTagValue(Exif.EXIFIMAGEWIDTH, 0, e, true);
 * 
 *      // Change the value of the DATETIME Entry
 *      Entry entry = exif.getTagValue(Exif.DATETIME, true);
 *      if(entry != null)
 *          entry.setValue(0, "1998:08:18 11:15:00");
 * </pre>
 * 
 * Also go through the below sources whose help was used to build this class:
 * <p>
 * 
 * <ul>
 * <li>ISO/DIS 12234-2 Photography - Electronic still picture cameras -
 * Removable Memory Part 2: Image data format - TIFF/EP
 * (http://www.pima.net/it10a.htm)
 * <li><a href="http://www.pima.net/standards/it10/PIMA15740/exif.htm"> some
 * enhancements were based on </a>
 * </ul>
 * 
 * @see #setTagValue(int,int,Entry,boolean)
 * @see Entry#setValue(int,Object)
 * 
 * @author dmitriy
 * 
 */
public class Exif extends AbstractImageInfo<LLJTran> {
	public final static String FORMAT = "Exif";

	public static final byte[] EXIF_MARK = { 0x45, 0x78, 0x69, 0x66, 0, 0 };

	static final int FIRST_IFD_OFF = 6;

	static final int MIN_JPEG_SIZE = 100;

	// Exif directory tag definition
	/** Identifies NEWSUBFILETYPE tag */
	public final static int NEWSUBFILETYPE = 0xFE;

	/** Identifies the IMAGEWIDTH tag */
	public final static int IMAGEWIDTH = 0x100;

	/** Identifies the IMAGELENGTH tag */
	public final static int IMAGELENGTH = 0x101;

	/** Identifies the BITSPERSAMPLE tag */
	public final static int BITSPERSAMPLE = 0x102;

	/** Identifies the COMPRESSION tag */
	public final static int COMPRESSION = 0x103;

	/** Identifies the PHOTOMETRICINTERPRETATION tag */
	public final static int PHOTOMETRICINTERPRETATION = 0x106;

	/** Identifies the FILLORDER tag */
	public final static int FILLORDER = 0x10A;

	/** Identifies the DOCUMENTNAME tag */
	public final static int DOCUMENTNAME = 0x10D;

	/** Identifies the IMAGEDESCRIPTION tag */
	public final static int IMAGEDESCRIPTION = 0x10E;

	/** Identifies the MAKE tag */
	public final static int MAKE = 0x10F;

	/** Identifies the MODEL tag */
	public final static int MODEL = 0x110;

	/** Identifies the STRIPOFFSETS tag */
	public final static int STRIPOFFSETS = 0x111;

	/** Identifies the ORIENTATION tag */
	public final static int ORIENTATION = 0x112;

	/** Identifies the SAMPLESPERPIXEL tag */
	public final static int SAMPLESPERPIXEL = 0x115;

	/** Identifies the ROWSPERSTRIP tag */
	public final static int ROWSPERSTRIP = 0x116;

	/** Identifies the STRIPBYTECOUNTS tag */
	public final static int STRIPBYTECOUNTS = 0x117;

	/** Identifies the XRESOLUTION tag */
	public final static int XRESOLUTION = 0x11A;

	/** Identifies the YRESOLUTION tag */
	public final static int YRESOLUTION = 0x11B;

	/** Identifies the PLANARCONFIGURATION tag */
	public final static int PLANARCONFIGURATION = 0x11C;

	/** Identifies the RESOLUTIONUNIT tag */
	public final static int RESOLUTIONUNIT = 0x128;

	/** Identifies the TRANSFERFUNCTION tag */
	public final static int TRANSFERFUNCTION = 0x12D;

	/** Identifies the SOFTWARE tag */
	public final static int SOFTWARE = 0x131;

	/** Identifies the DATETIME tag */
	public final static int DATETIME = 0x132;

	/** Identifies the ARTIST tag */
	public final static int ARTIST = 0x13B;

	/** Identifies the WHITEPOINT tag */
	public final static int WHITEPOINT = 0x13E;

	/** Identifies the PRIMARYCHROMATICITIES tag */
	public final static int PRIMARYCHROMATICITIES = 0x13F;

	/** Identifies the SUBIFDS tag */
	public final static int SUBIFDS = 0x14A;

	/** Identifies the JPEGTABLES tag */
	public final static int JPEGTABLES = 0x15B;

	/** Identifies the TRANSFERRANGE tag */
	public final static int TRANSFERRANGE = 0x156;

	/** Identifies the JPEGPROC tag */
	public final static int JPEGPROC = 0x200;

	/** Identifies the JPEGINTERCHANGEFORMAT tag */
	public final static int JPEGINTERCHANGEFORMAT = 0x201;

	/** Identifies the JPEGINTERCHANGEFORMATLENGTH tag */
	public final static int JPEGINTERCHANGEFORMATLENGTH = 0x202;

	/** Identifies the YCBCRCOEFFICIENTS tag */
	public final static int YCBCRCOEFFICIENTS = 0x211;

	/** Identifies the YCBCRSUBSAMPLING tag */
	public final static int YCBCRSUBSAMPLING = 0x212;

	/** Identifies the YCBCRPOSITIONING tag */
	public final static int YCBCRPOSITIONING = 0x213;

	/** Identifies the REFERENCEBLACKWHITE tag */
	public final static int REFERENCEBLACKWHITE = 0x214;

	/** Identifies the CFAREPEATPATTERNDIM tag */
	public final static int CFAREPEATPATTERNDIM = 0x828D;

	/** Identifies the CFAPATTERN tag */
	// public final static int CFAPATTERN = 0x828E;
	/**
	 * Indicates the color filter array (CFA) geometric pattern of the image
	 * sensor when a one-chip color area sensor is used.
	 */
	public final static int CFAPATTERN = 0xA302;

	/** Indicates the distance to the subject. */
	public final static int SUBJECTDDISTANCERANGE = 0xA40C;

	//

	/** Identifies the BATTERYLEVEL tag */
	public final static int BATTERYLEVEL = 0x828F;

	/** Identifies the COPYRIGHT tag */
	public final static int COPYRIGHT = 0x8298;

	/** Identifies the EXPOSURETIME tag */
	public final static int EXPOSURETIME = 0x829A;

	/** Identifies the FNUMBER tag */
	public final static int FNUMBER = 0x829D;

	/** Identifies the IPTC_NAA tag */
	public final static int IPTC_NAA = 0x83BB;

	/** Identifies the EXIFOFFSET tag */
	public final static int EXIFOFFSET = 0x8769;

	/** Identifies the ERCOLORPROFILE tag */
	public final static int INTERCOLORPROFILE = 0x8773;

	/** Identifies the EXPOSUREPROGRAM tag */
	public final static int EXPOSUREPROGRAM = 0x8822;

	/** Identifies the SPECTRALSENSITIVITY tag */
	public final static int SPECTRALSENSITIVITY = 0x8824;

	/** Identifies the GPSINFO tag */
	public final static int GPSINFO = 0x8825;

	/** Identifies the ISOSPEEDRATINGS tag */
	public final static int ISOSPEEDRATINGS = 0x8827;

	/** Identifies the OECF tag */
	public final static int OECF = 0x8828;

	/** Identifies the EXIFVERSION tag */
	public final static int EXIFVERSION = 0x9000;

	/** Identifies the DATETIMEORIGINAL tag */
	public final static int DATETIMEORIGINAL = 0x9003;

	/** Identifies the DATETIMEDIGITIZED tag */
	public final static int DATETIMEDIGITIZED = 0x9004;

	/** Identifies the COMPONENTSCONFIGURATION tag */
	public final static int COMPONENTSCONFIGURATION = 0x9101;

	/** Identifies the COMPRESSEDBITSPERPIXEL tag */
	public final static int COMPRESSEDBITSPERPIXEL = 0x9102;

	/** Identifies the SHUTTERSPEEDVALUE tag */
	public final static int SHUTTERSPEEDVALUE = 0x9201;

	/** Identifies the APERTUREVALUE tag */
	public final static int APERTUREVALUE = 0x9202;

	/** Identifies the BRIGHTNESSVALUE tag */
	public final static int BRIGHTNESSVALUE = 0x9203;

	/** Identifies the EXPOSUREBIASVALUE tag */
	public final static int EXPOSUREBIASVALUE = 0x9204;

	/** Identifies the MAXAPERTUREVALUE tag */
	public final static int MAXAPERTUREVALUE = 0x9205;

	/** Identifies the SUBJECTDISTANCE tag */
	public final static int SUBJECTDISTANCE = 0x9206;

	/** Identifies the METERINGMODE tag */
	public final static int METERINGMODE = 0x9207;

	/** Identifies the LIGHTSOURCE tag */
	public final static int LIGHTSOURCE = 0x9208;

	/** Identifies the FLASH tag */
	public final static int FLASH = 0x9209;

	/** Identifies the FOCALLENGTH tag */
	public final static int FOCALLENGTH = 0x920A;

	/** Identifies the MAKERNOTE tag */
	public final static int MAKERNOTE = 0x927C;

	/** Identifies the USERCOMMENT tag */
	public final static int USERCOMMENT = 0x9286;

	/** Identifies the SUBSECTIME tag */
	public final static int SUBSECTIME = 0x9290;

	/** Identifies the SUBSECTIMEORIGINAL tag */
	public final static int SUBSECTIMEORIGINAL = 0x9291;

	/** Identifies the SUBSECTIMEDIGITIZED tag */
	public final static int SUBSECTIMEDIGITIZED = 0x9292;

	/** Identifies the FLASHPIXVERSION tag */
	public final static int FLASHPIXVERSION = 0xA000;

	/** Identifies the COLORSPACE tag */
	public final static int COLORSPACE = 0xA001;

	/** Identifies the EXIFIMAGEWIDTH tag */
	public final static int EXIFIMAGEWIDTH = 0xA002;

	/** Identifies the EXIFIMAGELENGTH tag */
	public final static int EXIFIMAGELENGTH = 0xA003;

	/** Identifies the EROPERABILITYOFFSET tag */
	public final static int INTEROPERABILITYOFFSET = 0xA005;

	/** Identifies the FLASHENERGY tag */
	public final static int FLASHENERGY = 0xA20B; // = 0x920B in TIFF/EP

	/** Identifies the SPATIALFREQUENCYRESPONSE tag */
	public final static int SPATIALFREQUENCYRESPONSE = 0xA20C; // = 0x920C - -

	/** Identifies the FOCALPLANEXRESOLUTION tag */
	public final static int FOCALPLANEXRESOLUTION = 0xA20E; // = 0x920E - -

	/** Identifies the FOCALPLANEYRESOLUTION tag */
	public final static int FOCALPLANEYRESOLUTION = 0xA20F; // = 0x920F - -

	/** Identifies the FOCALPLANERESOLUTIONUNIT tag */
	public final static int FOCALPLANERESOLUTIONUNIT = 0xA210; // = 0x9210 - -

	/** Identifies the SUBJECTLOCATION tag */
	public final static int SUBJECTLOCATION = 0xA214; // = 0x9214 - -

	/** Identifies the EXPOSUREINDEX tag */
	public final static int EXPOSUREINDEX = 0xA215; // = 0x9215 - -

	/** Identifies the SENSINGMETHOD tag */
	public final static int SENSINGMETHOD = 0xA217; // = 0x9217 - -

	/** Identifies the FILESOURCE tag */
	public final static int FILESOURCE = 0xA300;

	/** Identifies the SCENETYPE tag */
	public final static int SCENETYPE = 0xA301;

	/** Identifies the FOCALLENGTHIN35MMFILM tag */
	public final static int FOCALLENGTHIN35MMFILM = 0xA405;

	/** Identifies the SHARPNESS tag */
	public final static int SHARPNESS = 0xA40A;

	/** Identifies the CUSTOMRENDERED tag */
	public final static int CUSTOMRENDERED = 0xA401;

	/** Identifies the EXPOSUREMODE tag */
	public final static int EXPOSUREMODE = 0xA402;

	/** Identifies the WHITEBALANCE tag */
	public final static int WHITEBALANCE = 0xA403;

	/** Identifies the DIGITALZOOMRATIO tag */
	public final static int DIGITALZOOMRATIO = 0xA404;

	/** Identifies the SATURATION tag */
	public final static int SATURATION = 0xA409;

	/** Identifies the SCENECAPTURETYPE tag */
	public final static int SCENECAPTURETYPE = 0xA406;

	/** Identifies the GAINCONTROL tag */
	public final static int GAINCONTROL = 0xA407;

	/** Identifies the CONTRAST tag */
	public final static int CONTRAST = 0xA408;

	/** Identifies the PRINTMODE tag */
	public final static int PRINTMODE = 0xC4A5;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSVersionID = 0x0000;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSLatitudeRef = 0x0001;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSLatitude = 0x0002;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSLongitudeRef = 0x0003;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSLongitude = 0x0004;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSAltitudeRef = 0x0005;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSAltitude = 0x0006;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSTimeStamp = 0x0007;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSSatellites = 0x0008;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSStatus = 0x0009;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSMeasureMode = 0x000a;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSDOP = 0x000b;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSSpeedRef = 0x000c;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSSpeed = 0x000d;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSTrackRef = 0x000e;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSTrack = 0x000f;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSImgDirectionRef = 0x0010;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSImgDirection = 0x0011;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSMapDatum = 0x0012;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSDestLatitudeRef = 0x0013;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSDestLatitude = 0x0014;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSDestLongitudeRef = 0x0015;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSDestLongitude = 0x0016;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSDestBearingRef = 0x0017;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSDestBearing = 0x0018;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSDestDistanceRef = 0x0019;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSDestDistance = 0x001a;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSProcessingMethod = 0x001b;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSAreaInformation = 0x001c;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSDateStamp = 0x001d;

	/** GPS tag. Make sure you access this under the IFD for GPSINFO */
	public final static int GPSDifferential = 0x001e;

	// Exif directory type of tag definition
	/** Identifies the Byte Data Type */
	public final static int BYTE = 1;

	/** Identifies the ASCII Data Type */
	public final static int ASCII = 2;

	/** Identifies the SHORT Data Type */
	public final static int SHORT = 3;

	/** Identifies the LONG Data Type */
	public final static int LONG = 4;

	/** Identifies the RATIONAL Data Type */
	public final static int RATIONAL = 5;

	/** Identifies the Signed BYTE Data Type */
	public final static int SBYTE = 6;

	/** Identifies the UNDEFINED Data Type */
	public final static int UNDEFINED = 7;

	/** Identifies the Signed SHORT Data Type */
	public final static int SSHORT = 8;

	/** Identifies the Signed LONG Data Type */
	public final static int SLONG = 9;

	/** Identifies the Signed RATIONAL Data Type */
	public final static int SRATIONAL = 10;

	public final static int ORIENTATION_TOPLEFT = 1;

	public final static int ORIENTATION_TOPRIGHT = 2;

	public final static int ORIENTATION_BOTRIGHT = 3;

	public final static int ORIENTATION_BOTLEFT = 4;

	public final static int ORIENTATION_LEFTTOP = 5;

	public final static int ORIENTATION_RIGHTTOP = 6;

	public final static int ORIENTATION_RIGHTBOT = 7;

	public final static int ORIENTATION_LEFTBOT = 8;

	// TODO: read names from XML database on camera vendor
	public final static String[] EXPOSURE_PROGRAMS = { "P0", "P1", "Normal",
			"P3", "P5" };

	public final static String[] METERING_MODES = { "P0", "P1", "Normal", "P3",
			"PATTERN" };

	public final static int DIR_ENTRY_SIZE = 12;

	public final static int[] TYPELENGTH = { 1, 1, 2, 4, 8, 1, 1, 2, 4, 8 };

	// TODO: consider replacing String name to java.io.File file
	/**
	 * Loads the ImageInfo using information supplied. Uses the
	 * {@link #readInfo()} method through AbstractImageInfo's constructor.
	 * 
	 * @param is
	 *            Image input. This is not used by Exif.
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
	public Exif(InputStream is, byte[] data, int offset, LLJTran format)
			throws FileFormatException {
		super(is, data, offset, format);
		// a unusual problem is here
		// no own variables are initialized here
		// but super's constructor calls our method read, which is using
		// uninitialized local variables, so they are moved to parent
	}

	/**
	 * Basic constructor
	 */
	public Exif() {
		ifds = new IFD[2];
		intel = true;
		version = 2;
	}

	public static byte[] getMarkerData() {
		return new byte[] { (byte) 0xFF, (byte) 0xE1, 0, 40, (byte) 0x45,
				(byte) 0x78, (byte) 0x69, (byte) 0x66, (byte) 0x00,
				(byte) 0x00, (byte) 0x49, (byte) 0x49, (byte) 0x2A,
				(byte) 0x00, (byte) 0x08, (byte) 0x00, (byte) 0x00,
				(byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x0F,
				(byte) 0x01, (byte) 0x02, (byte) 0x00, (byte) 0x05,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 26, 0, 0, 0,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 'F',
				(byte) 'A', (byte) 'K', (byte) 'E', (byte) 0x00, 0 };
	}

	/**
	 * Gets the Entry corresponding to an Exif tag.
	 * 
	 * @param tag
	 *            Exif tag
	 * @param main
	 *            true if it is in the main IFD, false if it is in the Sub IFD.
	 *            Most of the commonly used Exif tags are in the main IFD. The
	 *            Thumbnail related tags are in the Sub IFD.
	 * @return Entry corresponding to the tag
	 */
	public Entry getTagValue(int tag, boolean main) {
		return getTagValue(Integer.valueOf(tag), -1, main);
	}

	/**
	 * Gets the Entry corresponding to an Exif tag.
	 * 
	 * @param tag
	 *            Exif tag
	 * @param subTag
	 *            Sub Tag if any or pass -1
	 * @param main
	 *            true if it is in the main IFD, false if it is in the Sub IFD.
	 *            Most of the commonly used Exif tags are in the main IFD. The
	 *            Thumbnail related tags are in the Sub IFD.
	 * @return Entry corresponding to the tag
	 */
	public Entry getTagValue(Integer tag, int subTag, boolean main) {

		return ifds[main ? 0 : 1] != null ? ifds[main ? 0 : 1].getEntry(tag,
				subTag) : null;
	}

	/**
	 * Sets the Entry corresponding to an Exif tag.
	 * 
	 * @param tag
	 *            Exif tag
	 * @param subTag
	 *            Sub Tag if any or pass -1
	 * @param value
	 *            Entry to set
	 * @param main
	 *            true if it is in the main IFD, false if it is in the Sub IFD.
	 *            Most of the commonly used Exif tags are in the main IFD. The
	 *            Thumbnail related tags are in the Sub IFD.
	 */
	public void setTagValue(int tag, int subTag, Entry value, boolean main) {
		if (ifds[main ? 0 : 1] != null)
			ifds[main ? 0 : 1].setEntry(Integer.valueOf(tag), subTag, value);
	}

	/**
	 * Method to get the length of the Thumbnail.
	 * 
	 * @return Length of the Thumbnail
	 */
	public int getThumbnailLength() {
		int retVal = 0;

		Entry e = getTagValue(JPEGINTERCHANGEFORMATLENGTH, false);
		if (e == null)
			e = getTagValue(STRIPBYTECOUNTS, false);
		if (e != null)
			retVal = ((Integer) e.getValue(0)).intValue();
		return retVal;
	}

	/**
	 * Method to get the offset of the Thumbnail within the imageInfo data.
	 * 
	 * @return Offset of the Thumbnail within the Appx marker data
	 */
	public int getThumbnailOffset() {
		int retVal = 0;

		Entry e = getTagValue(JPEGINTERCHANGEFORMAT, false);
		if (e == null)
			e = getTagValue(STRIPOFFSETS, false);
		if (e != null)
			retVal = ((Integer) e.getValue(0)).intValue() + FIRST_IFD_OFF;
		return retVal;
	}

	/**
	 * Reads the imageInfo from the Input supplied in Constructor.
	 */
	@Override
	public void readInfo() {
		ifds = new IFD[2];
		offset -= data.length;
		intel = data[6] == 'I';
		motorola = data[6] == 'M';
		if (!(intel || motorola))
			return;
		version = s2n(8, 2);
		processAllIFDs();
		String msg = correctThumbnailTags(data, 0);
		if (msg != null)
			if (Log.debugLevel >= Log.LEVEL_WARNING)
				android.util.Log.w(FORMAT, "Warning: Exif Read: " + msg);
		data = null; // for gc
	}

	/**
	 * Returns the new Orientation Tag after applying a transformation.
	 * 
	 * @param tag
	 *            Current Orientation tag
	 * @param op
	 *            Transformation as defined in LLJTran
	 * @return New Orientation tag that should be set after Transforming the
	 *         image
	 */
	public static int transformOrientationTag(int tag, int op) {
		int newTag = 0;
		if (tag >= 1 && tag <= 8) {
			int positions, newPositions;

			// Get 4 2-bit numbers:
			// n0 n1
			// n3 n2
			// on 4 image corners corresponding to orientation tag. The numbers
			// are packed into a single 8-bit number
			positions = posForOrientationTags[tag];
			newPositions = positions;
			// Find out new positions after transformation
			switch (op) {
			case LLJTran.TRANSPOSE:
				// n0,n1,n2,n3 => n0,n3,n2,n1
				newPositions = positions & ((3 << 6) | (3 << 2))
						| ((positions & 3) << 4) | (positions >> 4) & 3;
				break;
			case LLJTran.TRANSVERSE:
				// n0,n1,n2,n3 => n2,n1,n0,n3
				newPositions = positions & ((3 << 4) | 3)
						| ((positions & (3 << 2)) << 4)
						| ((positions & (3 << 6)) >> 4);
				break;
			case LLJTran.ROT_90:
				// n0,n1,n2,n3 => n3,n0,n1,n2
				newPositions = (positions >> 2) | ((positions & 3) << 6);
				break;
			case LLJTran.ROT_270:
				// n0,n1,n2,n3 => n1,n2,n3,n0
				newPositions = ((positions << 2) | (positions >> 6)) & 255;
				break;
			case LLJTran.ROT_180:
				// n0,n1,n2,n3 => n2,n3,n0,n1
				newPositions = ((positions & 15) << 4) | (positions >> 4);
				break;
			case LLJTran.FLIP_H:
				// n0,n1,n2,n3 => n1,n0,n3,n2
				newPositions = ((positions & (3 << 4)) << 2)
						| ((positions & (3 << 6)) >> 2)
						| ((positions & 3) << 2)
						| ((positions & (3 << 2)) >> 2);
				break;
			case LLJTran.FLIP_V:
				// n0,n1,n2,n3 => n3,n2,n1,n0
				newPositions = ((positions & 3) << 6)
						| ((positions & (3 << 2)) << 2)
						| ((positions & (3 << 4)) >> 2) | (positions >> 6);
				break;
			case LLJTran.NONE:
			default:
				break;
			}

			newTag = 0;
			// Reverse lookup newTag from newPositions
			do
				++newTag;
			while (posForOrientationTags[newTag] != newPositions);
		}

		return newTag;
	}

	/**
	 * Writes modified or not Exif to out. APP header and its length are not
	 * included so any wrapper should do that calculation.
	 * <p>
	 * 
	 * This method is mainly for use by LLJTran to regenerate the Appx marker
	 * Data for the imageInfo.
	 * 
	 * @param markerData
	 *            The existing markerData. This is used by Exif to read the
	 *            existing Thumbnail.
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
	 *            Actual Image Height
	 * @param encoding
	 *            Encoding to be used when for writing out Character information
	 *            like comments.
	 */
	@Override
	public void writeInfo(byte markerData[], OutputStream out, int op,
			int options, boolean modifyImageInfo, int imageWidth,
			int imageHeight, String encoding) throws IOException {
		// TODO: this implementation takes twice memory than needed
		// it should be rewritten using byte[] and then copying to stream
		// version returning just byte[] is also very useful
		if (ifds == null)
			throw new IllegalStateException("EXIF data not filled.");

		Entry orgEntries[] = null;
		Object orgVals[] = null;
		int numOrgEntries = 0;
		Entry orientationEntry = null;
		Object orientationVal = null;
		if ((options & LLJTran.OPT_XFORM_ORIENTATION) != 0
				&& (orientationEntry = getTagValue(ORIENTATION, true)) != null) {
			Object val = orientationEntry.getValue(0);
			// Now Transform Orientation tag
			int orientation = ((Integer) val).intValue();
			int newOrientation = transformOrientationTag(orientation, op);
			if (orientation != newOrientation) {
				orientationVal = val;
				orientationEntry.setValue(0, Integer.valueOf(newOrientation));
			}
		}

		boolean dimensionModified = false;
		Entry resX = null, resY;
		Object xVal = null, yVal = null;
		resY = getTagValue(EXIFIMAGELENGTH, true);
		if (resY != null) {
			yVal = resY.getValue(0);
			resX = getTagValue(EXIFIMAGEWIDTH, true);
			if (resX != null) {
				xVal = resX.getValue(0);
				if (imageWidth > 0 && imageHeight > 0) {
					Integer xVal1 = Integer.valueOf(imageWidth);
					Integer yVal1 = Integer.valueOf(imageHeight);
					dimensionModified = true;
					resY.setValue(0, yVal1);
					resX.setValue(0, xVal1);
					if (modifyImageInfo) {
						xVal = xVal1;
						yVal = yVal1;
					}
				}
			}
		}

		if (!modifyImageInfo) {
			orgEntries = new Entry[16];
			orgVals = new Object[orgEntries.length];
		}

		switch (op) {
		case LLJTran.TRANSPOSE:
		case LLJTran.TRANSVERSE:
		case LLJTran.ROT_90:
		case LLJTran.ROT_270:
			if (!dimensionModified && resX != null && resY != null) {
				dimensionModified = true;
				resY.setValue(0, xVal);
				resX.setValue(0, yVal);
			}
			Entry eResX,
			eResY;
			Object eResXVal,
			eResYVal;
			for (int i = 0; i < 2; i++) {
				eResX = getTagValue(XRESOLUTION, i == 0 ? true : false);
				eResY = getTagValue(YRESOLUTION, i == 0 ? true : false);
				if (eResX != null && eResY != null) {
					eResXVal = eResX.getValue(0);
					eResYVal = eResY.getValue(0);
					if (!modifyImageInfo) {
						orgEntries[numOrgEntries] = eResX;
						orgVals[numOrgEntries] = eResXVal;
						numOrgEntries++;
						orgEntries[numOrgEntries] = eResY;
						orgVals[numOrgEntries] = eResYVal;
						numOrgEntries++;
					}
					eResY.setValue(0, eResXVal);
					eResX.setValue(0, eResYVal);
				}
			}
			break;
		case LLJTran.ROT_180:
		case LLJTran.FLIP_H:
		case LLJTran.FLIP_V:
		case LLJTran.NONE:
		default:
		}

		if (!modifyImageInfo) {
			if (orientationVal != null) {
				orgEntries[numOrgEntries] = orientationEntry;
				orgVals[numOrgEntries] = orientationVal;
				numOrgEntries++;
			}

			Entry e = getTagValue(JPEGINTERCHANGEFORMAT, false);
			Object val;
			if (e != null) {
				val = e.getValue(0);
				orgEntries[numOrgEntries] = e;
				orgVals[numOrgEntries] = val;
				numOrgEntries++;
			}
			e = getTagValue(JPEGINTERCHANGEFORMATLENGTH, false);
			if (e != null) {
				val = e.getValue(0);
				orgEntries[numOrgEntries] = e;
				orgVals[numOrgEntries] = val;
				numOrgEntries++;
			}

			if (dimensionModified) {
				orgEntries[numOrgEntries] = resX;
				orgVals[numOrgEntries] = xVal;
				numOrgEntries++;
				orgEntries[numOrgEntries] = resY;
				orgVals[numOrgEntries] = yVal;
				numOrgEntries++;
			}
		}

		// TODO: write to out FIRST_IFD_OFF bytes
		out.write(EXIF_MARK);
		if (intel) {
			out.write('I');
			out.write('I');
		} else {
			out.write('M');
			out.write('M');
		}
		out.write(n2s(version, 2));

		int emptySlot = EXIF_MARK.length + 2;
		// write offset of IFD
		out.write(n2s(emptySlot, 4));
		String msg = correctThumbnailTags(markerData, 4);
		if (msg != null)
			if (Log.debugLevel >= Log.LEVEL_WARNING)
				android.util.Log.w(FORMAT, "Warning: Exif Write: " + msg);
		for (int k = 0; k < 2; k++) {
			// System.err.println("--->IFD "+k+" offeset "+emptySlot);
			boolean isLast = false;
			if (k == 1 || ifds[k + 1] == null)
				isLast = true;
			emptySlot = writeIfd(markerData, out, emptySlot, ifds[k], op,
					options, isLast, encoding);
		}

		for (int i = 0; i < numOrgEntries; ++i) {
			Entry e = orgEntries[i];
			if (e != null)
				e.setValue(0, orgVals[i]);
		}
	}

	/**
	 * Removes the Thumbnail Tags in the imageInfo. Thus the next time the Appx
	 * is written using
	 * {@link #writeInfo(byte[],OutputStream,int,int,boolean,int,int,String)
	 * writeInfo(..)} it will be without a Thumbnail
	 * 
	 * @return True if the Sub IFD containing Thumbnail is present
	 */
	public boolean removeThumbnailTags() {
		IFD ifd = ifds[1];
		if (ifd != null) {
			ifds[1].removeEntry(JPEGINTERCHANGEFORMAT);
			ifds[1].removeEntry(JPEGINTERCHANGEFORMATLENGTH);
			ifds[1].removeEntry(STRIPOFFSETS);
			ifds[1].removeEntry(STRIPBYTECOUNTS);
			ifds[1].removeEntry(PHOTOMETRICINTERPRETATION);
		}

		return true;
	}

	// Removes Thumbnail tags if offset/length are not okay. Truncates
	// length if it goes outside marker. Returns null if okay or warning
	// message otherwise.
	private String correctThumbnailTags(byte markerData[], int leading) {
		String retVal = null;
		boolean thumbnailTagsPresent = false;
		boolean isJpegThumbnail = false;
		int offsetTagVal = 0;
		int offset = 0;
		Entry offsetEnt = getTagValue(JPEGINTERCHANGEFORMAT, false);
		if (offsetEnt == null)
			offsetEnt = getTagValue(STRIPOFFSETS, false);
		else
			isJpegThumbnail = true;
		if (offsetEnt != null) {
			thumbnailTagsPresent = true;
			offsetTagVal = ((Integer) offsetEnt.getValue(0)).intValue();
			offset = offsetTagVal + FIRST_IFD_OFF + leading;
		}

		int length = 0;
		Entry lengthEnt = getTagValue(JPEGINTERCHANGEFORMATLENGTH, false);
		if (lengthEnt == null)
			lengthEnt = getTagValue(STRIPBYTECOUNTS, false);
		else
			isJpegThumbnail = true;
		if (lengthEnt != null) {
			length = ((Integer) lengthEnt.getValue(0)).intValue();
			thumbnailTagsPresent = true;
		}
		int orgLen = length;

		if (thumbnailTagsPresent) {
			int lengthOvershoot = 0, skipCount = 0;
			StringBuffer warnBuf = new StringBuffer();
			if (markerData == null)
				retVal = "Removing Thumbnail: No Marker Supplied";
			else if (offset < 0 || offset > markerData.length)
				retVal = "Removing Thumbnail: Invalid Offset: " + offset;
			else if ((lengthOvershoot = offset + length - markerData.length) > 0) {
				length -= lengthOvershoot;
				warnBuf.append("; Thumbnail length ").append(orgLen)
						.append(" is beyond Exif header. Reducing it to ")
						.append(length);
			}

			if (retVal == null) {
				if (isJpegThumbnail) {
					while (offset < markerData.length - 1
							&& length > 0
							&& !(markerData[offset] == M_PRX && markerData[offset + 1] == M_SOI)) {
						length--;
						offset++;
						skipCount++; // skip garbage in begining including
										// padding FF
					}

					if (skipCount > 0) {
						offsetTagVal += skipCount;
						warnBuf.append("; Skipped ")
								.append(skipCount)
								.append(" Garbage bytes at the beginning of Jpeg Thumbnail");
					}
				}

				if (length <= MIN_JPEG_SIZE) {
					warnBuf.append("; Removing Thumbnail: Invalid length: ")
							.append(length);
					retVal = warnBuf.substring(2);
				}
			}

			if (retVal != null)
				removeThumbnailTags();
			else if (lengthOvershoot > 0 || skipCount > 0) {
				retVal = warnBuf.substring(2);
				lengthEnt.setValue(0, Integer.valueOf(length));
				offsetEnt.setValue(0, Integer.valueOf(offsetTagVal));
			}
			warnBuf = null;
		}

		return retVal;
	}

	/**
	 * writes IFD from map and returns length
	 */
	protected int writeIfd(byte markerData[], OutputStream out, int emptySlot,
			IFD ifd, int op, int options, boolean isLast, String encoding)
			throws IOException {
		if (ifd == null) {
			if (Log.debugLevel >= Log.LEVEL_WARNING)
				android.util.Log
						.w(FORMAT,
								"Warning: Requested to write NULL IFD, nothing written.");
			return emptySlot;
		}
		ByteArrayOutputStream buf = new ByteArrayOutputStream(1 * 1024);
		int ne = (ifd.getEntries() == null ? 0 : ifd.getEntries().size())
				+ (ifd.getIFDs() == null ? 0 : ifd.getIFDs().length);
		// System.err.println("ifd= "+Integer.toHexString(ifd.getTag())+" entries "+ne+" offset 0x"+Integer.toHexString(emptySlot));
		out.write(n2s(ne, 2)); // num entries
		emptySlot += ne * DIR_ENTRY_SIZE + 2 + 4; // num entries + next slot
		Iterator<Map.Entry<Integer, Entry>> it = ifd.getEntries().entrySet()
				.iterator();
		boolean foundJpegThumbnailTag = false;
		boolean foundBmpThumbnailTag = false;
		while (it.hasNext()) {
			Map.Entry<Integer, Entry> me = it.next();
			int tag = me.getKey();

			if (tag == MAKERNOTE) // write it
			{
				if (makerNoteHandler != null) {
					makerNoteHandler.save(out, this, ifd);
					continue;
				}
			}
			// Skip Thumbnail Tags to process at end. Processing at end
			// keeps Thumbnail tags next to Thumbnail data which programs
			// like jhead prefer
			if (tag == JPEGINTERCHANGEFORMAT) // skip it
			{
				foundJpegThumbnailTag = true;
				continue;
			}
			if (tag == JPEGINTERCHANGEFORMATLENGTH) // skip it
				continue;
			if (tag == STRIPOFFSETS) // skip it
			{
				foundBmpThumbnailTag = true;
				continue;
			}
			if (tag == STRIPBYTECOUNTS) // skip it
				continue;

			Entry e = (Entry) me.getValue();
			if (e == null)
				continue;
			// TODO: consider write(e.toByteArray(intel)
			out.write(n2s(tag, 2));
			int type;
			out.write(n2s(type = e.getType(), 2));
			// System.err.println("write type "+Integer.toString(type,16)+" tag "+Integer.toString(tag,
			// 16)+
			// " tag vals "+rogatkin.DataConv.arrayToString(e.getValues(),
			// ':'));
			if (type == ASCII) {
				byte[] str = e.toString().getBytes(encoding);
				out.write(n2s(str.length + 1, 4));
				if (str.length + 1 > 4) {
					out.write(n2s(emptySlot, 4));
					buf.write(str); // buf used
					buf.write(0);
					emptySlot += str.length + 1;
				} else { // write data
					out.write(str);
					if (str.length < 4) // write padding
						for (int i = 0; i < 4 - str.length; i++)
							// shouldn't be a stopper
							out.write(0);
				}
			} else {
				Object[] vs = e.getValues(); // can vs be null ? or have length
												// 0
				out.write(n2s(vs.length, 4));
				int tlen = TYPELENGTH[type - 1];
				if (vs.length * tlen > 4) {
					out.write(n2s(emptySlot, 4));
					// boolean signed = (type == SBYTE || type >= SSHORT);
					for (int i = 0; i < vs.length; i++) {
						if (type == RATIONAL || type == SRATIONAL) {
							buf.write(n2s(((Rational) vs[i]).getNum(), 4));
							buf.write(n2s(((Rational) vs[i]).getDen(), 4));
							emptySlot += 8;
						} else {
							buf.write(n2s(((Integer) vs[i]).intValue(), tlen));
							emptySlot += tlen;
						}
					}
				} else {
					for (int i = 0; i < vs.length; i++)
						out.write(n2s(((Integer) vs[i]).intValue(), tlen));
					if (vs.length * tlen < 4)
						for (int i = 0; i < 4 - vs.length * tlen; i++)
							// shouldn't be a stopper
							out.write(0);
				}
			}
		}

		// Write Thumbmnail offset and length if found
		if (foundJpegThumbnailTag) {
			int length = getThumbnailLength();
			int jpeg_offset = getThumbnailOffset() + 4;
			// Not doing any validity checks. Validation and correction should
			// have been done by calling correctThumbnailTags
			try {
				int l = buf.size();
				boolean copyThumbnail = true;
				if ((options & LLJTran.OPT_XFORM_THUMBNAIL) != 0
						&& op != LLJTran.NONE && op != LLJTran.CROP) {
					LLJTran ljt = null;
					try {
						ByteArrayInputStream tis = new ByteArrayInputStream(
								markerData, jpeg_offset, length);
						ljt = new LLJTran(tis);
						ljt.read(LLJTran.READ_ALL, false);
						ljt.transform(op, 0);
						copyThumbnail = false;
						tis = null;
					} catch (Throwable e) {
						if (Log.debugLevel >= Log.LEVEL_WARNING) {
							android.util.Log.w(FORMAT,
									"Warning: Unable to Transform Thumbnail, will write it unchanged: "
											+ e.getMessage());
							e.printStackTrace(System.err);
						}
					}

					// Hope there won't be an exception just now
					if (!copyThumbnail)
						ljt.save(buf, 0);
				}

				if (copyThumbnail)
					buf.write(markerData, jpeg_offset, length);
				l = buf.size() - l;
				Entry ent = getTagValue(JPEGINTERCHANGEFORMATLENGTH, false);
				if (ent != null)
					ent.setValue(0, Integer.valueOf(l));
				out.write(n2s(JPEGINTERCHANGEFORMATLENGTH, 2));
				out.write(n2s(LONG, 2));
				out.write(n2s(1, 4));
				out.write(n2s(l, 4));
				ent = getTagValue(JPEGINTERCHANGEFORMAT, false);
				ent.setValue(0, Integer.valueOf(emptySlot));
				out.write(n2s(JPEGINTERCHANGEFORMAT, 2));
				out.write(n2s(ent.getType(), 2));
				out.write(n2s(1, 4));
				out.write(n2s(emptySlot, 4));
				emptySlot += l;
			} catch (Throwable t) {
				if (Log.debugLevel >= Log.LEVEL_ERROR)
					t.printStackTrace();
			}
		} else if (foundBmpThumbnailTag) {
			int length = getThumbnailLength();
			int offset = getThumbnailOffset() + 4;
			// Not doing any validity checks. Validation and correction should
			// have been done by calling correctThumbnailTags
			try {
				int l = buf.size();
				buf.write(markerData, offset, length);
				l = buf.size() - l;

				Entry ent = getTagValue(STRIPBYTECOUNTS, false);
				if (ent != null)
					ent.setValue(0, Integer.valueOf(l));
				out.write(n2s(STRIPBYTECOUNTS, 2));
				out.write(n2s(LONG, 2));
				out.write(n2s(1, 4));
				out.write(n2s(l, 4));
				ent = getTagValue(STRIPOFFSETS, false);
				ent.setValue(0, Integer.valueOf(emptySlot));
				out.write(n2s(STRIPOFFSETS, 2));
				out.write(n2s(ent.getType(), 2));
				out.write(n2s(1, 4));
				out.write(n2s(emptySlot, 4));
				emptySlot += l;
			} catch (Throwable t) {
				if (Log.debugLevel >= Log.LEVEL_ERROR)
					t.printStackTrace();
			}
		}

		// write IFDs
		IFD[] ifds = ifd.getIFDs();
		for (int k = 0; ifds != null && k < ifds.length;) {
			IFD ifd1 = ifds[k];
			out.write(n2s(ifd1.getTag(), 2));
			out.write(n2s(ifd1.getType(), 2));
			if (ifd1.getTag() == MAKERNOTE && makerNoteHandler != null) {
				makerNoteHandler.save(out, this, ifd1);
			} else {
				out.write(n2s(1, 4));
			}
			out.write(n2s(emptySlot, 4));
			k++;
			// Passing the isLast parameter for below call assumes that there
			// are no null entries in SubIfds
			emptySlot = writeIfd(markerData, buf, emptySlot, ifd1, op, options,
					(k == ifds.length), encoding);
		}
		// next IFD
		out.write(n2s(isLast ? 0 : emptySlot, 4));
		// write data
		buf.writeTo(out);

		return emptySlot;
	}

	protected int firstIFD() {
		// System.err.println("FIFD "+(s2n(FIRST_IFD_OFF+4, 4)+FIRST_IFD_OFF));
		return s2n(FIRST_IFD_OFF + 4, 4) + FIRST_IFD_OFF;
	}

	protected int nextIFD(int ifd) {
		int entries = s2n(ifd, 2);
		return s2n(ifd + 2 + DIR_ENTRY_SIZE * entries, 4) + FIRST_IFD_OFF;
	}

	protected void processAllIFDs() {
		int iifd = 0;
		for (int i = firstIFD(); i > FIRST_IFD_OFF && iifd < 2; i = nextIFD(i)) {
			ifds[iifd] = new IFD(iifd);
			try {
				storeIFD(i, ifds[iifd]);
			} catch (IFDParsingException e) {
				e.printStackTrace();
			}
			iifd++;
		}
	}

	public void storeIFD(int ifdoffset, IFD ifd) throws IFDParsingException {
		int entries = s2n(ifdoffset, 2);
		checkIFDConsistence(ifd, entries);
		// System.err.println("Store off " + ifdoffset + " tag " +
		// Integer.toHexString(ifd.getTag()) + " entries " + entries);
		for (int i = 0; i < entries; i++) {
			int entry = ifdoffset + 2 + DIR_ENTRY_SIZE * i;
			int tag = s2n(entry, 2);
			int type = s2n(entry + 2, 2);
			if (type < 1 || type > 10)
				continue; // not handled
			int typelen = TYPELENGTH[type - 1];
			int count = s2n(entry + 4, 4);
			int offset = entry + 8;
			if (count * typelen > 4)
				offset = s2n(offset, 4) + FIRST_IFD_OFF;
			// System.err.println("tag " + Integer.toHexString(tag) + " type " +
			// type + " len " + count + " off " + offset);
			if (type == ASCII) {
				// Special case: zero-terminated ASCII string
				try {
					ifd.addEntry(tag, new Entry(type, new String(data, offset,
							count - 1, "US-ASCII")));
				} catch (UnsupportedEncodingException e) {
					if (Log.debugLevel >= Log.LEVEL_ERROR)
						android.util.Log
								.e(FORMAT, "storeIFD: getString() " + e);
				}
			} else {
				if (tag == MAKERNOTE) {
					makerNoteHandler = MakerNoteHandlerFactory.getHandler(
							getMake(), getModel());
					if (makerNoteHandler != null) {
						makerNoteHandler.load(this, ifd, tag, type, offset,
								count, typelen);
						continue;
					}
					// ifd.addIFD(makerNoteIFD);
				}
				storeValue(ifd, tag, type, offset, count, typelen);
			}
		}
	}

	private void checkIFDConsistence(IFD ifd, int entries)
			throws IFDParsingException {
		if (ifd.getTag() == INTEROPERABILITYOFFSET && entries > 5) {
			throw new IFDParsingException("Too many entries (" + entries
					+ ") for tag " + ifd.getTag()
					+ ". Should not be more than 5.");
		}
	}

	public String getMake() {
		Entry e = getTagValue(MAKE, true);
		if (e != null)
			return e.toString();
		return NA;
	}

	public String getModel() {
		Entry e = getTagValue(MODEL, true);
		if (e != null)
			return e.toString();
		return NA;
	}

	public void storeValue(IFD ifd, int tag, int type, int offset, int count,
			int typelen) {
		Object[] values = new Object[count];
		boolean signed = (type == SBYTE || type >= SSHORT);
		for (int j = 0; j < count; j++) {
			if (type == RATIONAL || type == SRATIONAL)
				values[j] = new Rational(s2n(offset, 4, signed), s2n(
						offset + 4, 4, signed));
			else
				// Not a fraction
				values[j] = Integer.valueOf(s2n(offset, typelen, signed));
			offset += typelen;
			// Recent Fujifilm and Toshiba cameras have a little subdirectory
			// here, pointed to by tag 0xA005. Apparently, it's the
			// "Interoperability IFD", defined in Exif 2.1.
			if ((tag == EXIFOFFSET || tag == INTEROPERABILITYOFFSET || tag == GPSINFO)
					&& j == 0 && ((Integer) values[0]).intValue() > 0) {
				IFD iifd;
				try {
					storeIFD(((Integer) values[0]).intValue() + FIRST_IFD_OFF,
							iifd = new IFD(tag, type));
					ifd.addIFD(iifd);
				} catch (IFDParsingException e) {
					if (Log.debugLevel >= Log.LEVEL_WARNING)
						android.util.Log
								.w(FORMAT, "Warning: " + e.getMessage());
				}
			} else if (j == 0) // by Kirill
				ifd.addEntry(tag, new Entry(type, values));
		}
	}

	// Assume 4 corners of original image have a number 0-3 marked
	// clockwise as below:
	// 0 1
	// 3 2
	// The below lookup array gives the numbers on each corner of the oriented
	// image corresponding to each orientation tag represented as a 8-bit
	// number, 2-bits per number on each corner of the oriented image
	private static final int posForOrientationTags[] = { -1,
			(1 << 4) + (2 << 2) + 3, // tag 1 => NONE: 0,1,2,3
			(1 << 6) + (3 << 2) + 2, // tag 2 => FLIP_H: 1,0,3,2
			(2 << 6) + (3 << 4) + 1, // tag 3 => ROT_180: 2,3,0,1
			(3 << 6) + (2 << 4) + (1 << 2), // tag 4 => FLIP_V: 3,2,1,0
			(3 << 4) + (2 << 2) + 1, // tag 5 => TRANSPOSE: 0,3,2,1
			(1 << 6) + (2 << 4) + (3 << 2), // tag 6 => ROT_270: 1,2,3,0
			(2 << 6) + (1 << 4) + 3, // tag 7 => TRANSVERSE: 2,1,0,3
			(3 << 6) + (1 << 2) + 2 // tag 8 => ROT_90: 3,0,1,2
	};

	/**
	 * A lookup array which can be used to get the LLJTran transformation
	 * operation required to correct the orientation for a given Exif
	 * Orientation Tag
	 */
	public static final int opToCorrectOrientation[] = { -1, LLJTran.NONE,
			LLJTran.FLIP_H, LLJTran.ROT_180, LLJTran.FLIP_V, LLJTran.TRANSPOSE,
			LLJTran.ROT_90, LLJTran.TRANSVERSE, LLJTran.ROT_270 };

	protected int version;

	protected IFD[] ifds;

	// Due to the unusual "constructor calls read()" architecture,
	// makerNoteHandler cannot be initialized to null here, because that would
	// overwrite the value set by read()
	private MakerNoteHandler makerNoteHandler;

}
