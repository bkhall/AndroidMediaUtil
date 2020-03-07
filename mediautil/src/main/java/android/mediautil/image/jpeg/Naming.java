/* MediaUtil LLJTran - $RCSfile: Naming.java,v $
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
 *	$Id: Naming.java,v 1.4 2007/07/23 18:53:53 drogatkin Exp $
 *
 * Some ideas and algorithms were borrowed from:
 * Thomas G. Lane, and James R. Weeks
 */
package android.mediautil.image.jpeg;

import java.util.Hashtable;

public final class Naming {

	// TODO: extend with expected result type and conversion func
	// for better vieweing
	static final Object[][] ExifTagNames = {
			{ Integer.valueOf(Exif.NEWSUBFILETYPE), "NewSubFileType" },
			{ Integer.valueOf(Exif.IMAGEWIDTH), "ImageWidth" },
			{ Integer.valueOf(Exif.IMAGELENGTH), "ImageLength" },
			{ Integer.valueOf(Exif.BITSPERSAMPLE), "BitsPerSample" },
			{ Integer.valueOf(Exif.COMPRESSION), "Compression" },
			{ Integer.valueOf(Exif.PHOTOMETRICINTERPRETATION),
					"PhotometricInterpretation" },
			{ Integer.valueOf(Exif.FILLORDER), "FillOrder" },
			{ Integer.valueOf(Exif.DOCUMENTNAME), "DocumentName" },
			{ Integer.valueOf(Exif.IMAGEDESCRIPTION), "ImageDescription" },
			{ Integer.valueOf(Exif.MAKE), "Make" },
			{ Integer.valueOf(Exif.MODEL), "Model" },
			{ Integer.valueOf(Exif.STRIPOFFSETS), "StripOffsets" },
			{ Integer.valueOf(Exif.ORIENTATION), "Orientation" },
			{ Integer.valueOf(Exif.SAMPLESPERPIXEL), "SamplesPerPixel" },
			{ Integer.valueOf(Exif.ROWSPERSTRIP), "RowsPerStrip" },
			{ Integer.valueOf(Exif.STRIPBYTECOUNTS), "StripByteCounts" },
			{ Integer.valueOf(Exif.XRESOLUTION), "XResolution" },
			{ Integer.valueOf(Exif.YRESOLUTION), "YResolution" },
			{ Integer.valueOf(Exif.PLANARCONFIGURATION), "PlanarConfiguration" },
			{ Integer.valueOf(Exif.RESOLUTIONUNIT), "ResolutionUnit" },
			{ Integer.valueOf(Exif.TRANSFERFUNCTION), "TransferFunction" },
			{ Integer.valueOf(Exif.SOFTWARE), "Software" },
			{ Integer.valueOf(Exif.DATETIME), "DateTime" },
			{ Integer.valueOf(Exif.ARTIST), "Artist" },
			{ Integer.valueOf(Exif.WHITEPOINT), "WhitePoint" },
			{ Integer.valueOf(Exif.PRIMARYCHROMATICITIES),
					"PrimaryChromaticities" },
			{ Integer.valueOf(Exif.SUBIFDS), "SubIFDs" },
			{ Integer.valueOf(Exif.JPEGTABLES), "JPEGTables" },
			{ Integer.valueOf(Exif.TRANSFERRANGE), "TransferRange" },
			{ Integer.valueOf(Exif.JPEGPROC), "JPEGProc" },
			{ Integer.valueOf(Exif.JPEGINTERCHANGEFORMAT),
					"JPEGInterchangeFormat" },
			{ Integer.valueOf(Exif.JPEGINTERCHANGEFORMATLENGTH),
					"JPEGInterchangeFormatLength" },
			{ Integer.valueOf(Exif.YCBCRCOEFFICIENTS), "YCbCrCoefficients" },
			{ Integer.valueOf(Exif.YCBCRSUBSAMPLING), "YCbCrSubSampling" },
			{ Integer.valueOf(Exif.YCBCRPOSITIONING), "YCbCrPositioning" },
			{ Integer.valueOf(Exif.REFERENCEBLACKWHITE), "ReferenceBlackWhite" },
			{ Integer.valueOf(Exif.CFAREPEATPATTERNDIM), "CFARepeatPatternDim" },
			{ Integer.valueOf(Exif.CFAPATTERN), "CFAPattern" },
			{ Integer.valueOf(Exif.SUBJECTDDISTANCERANGE),
					"SubjectDistanceRange" },
			{ Integer.valueOf(Exif.BATTERYLEVEL), "BatteryLevel" },
			{ Integer.valueOf(Exif.COPYRIGHT), "Copyright" },
			{ Integer.valueOf(Exif.EXPOSURETIME), "ExposureTime" },
			{ Integer.valueOf(Exif.FNUMBER), "FNumber" },
			{ Integer.valueOf(Exif.IPTC_NAA), "IPTC/NAA" },
			{ Integer.valueOf(Exif.EXIFOFFSET), "ExifOffset" },
			{ Integer.valueOf(Exif.INTERCOLORPROFILE), "InterColorProfile" },
			{ Integer.valueOf(Exif.EXPOSUREPROGRAM), "ExposureProgram" },
			{ Integer.valueOf(Exif.SPECTRALSENSITIVITY), "SpectralSensitivity" },
			{ Integer.valueOf(Exif.GPSINFO), "GPSInfo" },
			{ Integer.valueOf(Exif.ISOSPEEDRATINGS), "ISOSpeedRatings" },
			{ Integer.valueOf(Exif.OECF), "OECF" },
			{ Integer.valueOf(Exif.EXIFVERSION), "ExifVersion" },
			{ Integer.valueOf(Exif.DATETIMEORIGINAL), "DateTimeOriginal" },
			{ Integer.valueOf(Exif.DATETIMEDIGITIZED), "DateTimeDigitized" },
			{ Integer.valueOf(Exif.COMPONENTSCONFIGURATION),
					"ComponentsConfiguration" },
			{ Integer.valueOf(Exif.COMPRESSEDBITSPERPIXEL),
					"CompressedBitsPerPixel" },
			{ Integer.valueOf(Exif.SHUTTERSPEEDVALUE), "ShutterSpeedValue" },
			{ Integer.valueOf(Exif.APERTUREVALUE), "ApertureValue" },
			{ Integer.valueOf(Exif.BRIGHTNESSVALUE), "BrightnessValue" },
			{ Integer.valueOf(Exif.EXPOSUREBIASVALUE), "ExposureBiasValue" },
			{ Integer.valueOf(Exif.MAXAPERTUREVALUE), "MaxApertureValue" },
			{ Integer.valueOf(Exif.SUBJECTDISTANCE), "SubjectDistance" },
			{ Integer.valueOf(Exif.METERINGMODE), "MeteringMode" },
			{ Integer.valueOf(Exif.LIGHTSOURCE), "LightSource" },
			{ Integer.valueOf(Exif.FLASH), "Flash" },
			{ Integer.valueOf(Exif.FOCALLENGTH), "FocalLength" },
			{ Integer.valueOf(Exif.MAKERNOTE), "MakerNote" },
			{ Integer.valueOf(Exif.USERCOMMENT), "UserComment" },
			{ Integer.valueOf(Exif.SUBSECTIME), "SubSecTime" },
			{ Integer.valueOf(Exif.SUBSECTIMEORIGINAL), "SubSecTimeOriginal" },
			{ Integer.valueOf(Exif.SUBSECTIMEDIGITIZED), "SubSecTimeDigitized" },
			{ Integer.valueOf(Exif.FLASHPIXVERSION), "FlashPixVersion" },
			{ Integer.valueOf(Exif.COLORSPACE), "ColorSpace" },
			{ Integer.valueOf(Exif.EXIFIMAGEWIDTH), "ExifImageWidth" },
			{ Integer.valueOf(Exif.EXIFIMAGELENGTH), "ExifImageLength" },
			{ Integer.valueOf(Exif.INTEROPERABILITYOFFSET),
					"InteroperabilityOffset" },
			{ Integer.valueOf(Exif.FLASHENERGY), "FlashEnergy" },
			{ Integer.valueOf(Exif.SPATIALFREQUENCYRESPONSE),
					"SpatialFrequencyResponse" },
			{ Integer.valueOf(Exif.FOCALPLANEXRESOLUTION),
					"FocalPlaneXResolution" },
			{ Integer.valueOf(Exif.FOCALPLANEYRESOLUTION),
					"FocalPlaneYResolution" },
			{ Integer.valueOf(Exif.FOCALPLANERESOLUTIONUNIT),
					"FocalPlaneResolutionUnit" },
			{ Integer.valueOf(Exif.SUBJECTLOCATION), "SubjectLocation" },
			{ Integer.valueOf(Exif.EXPOSUREINDEX), "ExposureIndex" },
			{ Integer.valueOf(Exif.SENSINGMETHOD), "SensingMethod" },
			{ Integer.valueOf(Exif.FILESOURCE), "FileSource" },
			{ Integer.valueOf(Exif.SCENETYPE), "SceneType" },
			{ Integer.valueOf(Exif.FOCALLENGTHIN35MMFILM),
					"FocalLengthIn35mmFilm" },
			{ Integer.valueOf(Exif.SHARPNESS), "Sharpness" },
			{ Integer.valueOf(Exif.CUSTOMRENDERED), "CustomRendered" },
			{ Integer.valueOf(Exif.SATURATION), "Saturation" },
			{ Integer.valueOf(Exif.WHITEBALANCE), "WhiteBalance" },
			{ Integer.valueOf(Exif.DIGITALZOOMRATIO), "DigitalZoomRatio" },
			{ Integer.valueOf(Exif.CONTRAST), "Contrast" },
			{ Integer.valueOf(Exif.GAINCONTROL), "GainControl" },
			{ Integer.valueOf(Exif.EXPOSUREMODE), "ExposureMode" },
			{ Integer.valueOf(Exif.DIGITALZOOMRATIO), "DigitalZoomRatio" },
			{ Integer.valueOf(Exif.PRINTMODE), "PrintMode" },
			{ Integer.valueOf(Exif.SCENECAPTURETYPE), "SceneCaptureType" } };

	static final Object[][] CIFFPropsNames = {
			{ Integer.valueOf(CIFF.K_TC_DESCRIPTION), "Description" },
			{ Integer.valueOf(CIFF.K_TC_MODELNAME), "ModelName" },
			{ Integer.valueOf(CIFF.K_TC_FIRMWAREVERSION), "FirmwareVersion" },
			{ Integer.valueOf(CIFF.K_TC_COMPONENTVESRION), "ComponentVesrion" },
			{ Integer.valueOf(CIFF.K_TC_ROMOPERATIONMODE), "ROMOperationMode" },
			{ Integer.valueOf(CIFF.K_TC_OWNERNAME), "OwnerName" },
			{ Integer.valueOf(CIFF.K_TC_IMAGEFILENAME), "ImageFilename" },
			{ Integer.valueOf(CIFF.K_TC_THUMBNAILFILENAME), "ThumbnailFilename" },

			{ Integer.valueOf(CIFF.K_TC_TARGETIMAGETYPE), "TargetImageType" },
			{ Integer.valueOf(CIFF.K_TC_SR_RELEASEMETHOD), "ReleaseMethod" },
			{ Integer.valueOf(CIFF.K_TC_SR_RELEASETIMING), "ReleaseTiming" },
			{ Integer.valueOf(CIFF.K_TC_RELEASESETTING), "ReleaseSetting" },
			{ Integer.valueOf(CIFF.K_TC_BODYSENSITIVITY), "BodySensitivity" },

			{ Integer.valueOf(CIFF.K_TC_IMAGEFORMAT), "ImageFormat" },
			{ Integer.valueOf(CIFF.K_TC_RECORDID), "RecordId" },
			{ Integer.valueOf(CIFF.K_TC_SELFTIMERTIME), "SelfTimerTime" },
			{ Integer.valueOf(CIFF.K_TC_SR_TARGETDISTANCESETTING),
					"TargetDistanceSetting" },
			{ Integer.valueOf(CIFF.K_TC_BODYID), "BodyId" },
			{ Integer.valueOf(CIFF.K_TC_CAPTURETIME), "CaptureTime" },
			{ Integer.valueOf(CIFF.K_TC_IMAGESPEC), "ImageSpec" },
			{ Integer.valueOf(CIFF.K_TC_SR_EF), "EF" },
			{ Integer.valueOf(CIFF.K_TC_MI_EV), "EV" },
			{ Integer.valueOf(CIFF.K_TC_SERIALNUMBER), "SerialNumber" },
			{ Integer.valueOf(CIFF.K_TC_SR_EXPOSURE), "Exposure" },

			{ Integer.valueOf(CIFF.K_TC_CAMERAOBJECT), "CameraObject" },
			{ Integer.valueOf(CIFF.K_TC_SHOOTINGRECORD), "ShootingRecord" },
			{ Integer.valueOf(CIFF.K_TC_MEASUREDINFO), "MeasuredInfo" },
			{ Integer.valueOf(CIFF.K_TC_CAMERASPECIFICATION),
					"CameraSpecification" } };

	public static String[] ExifTagTypes = { "B", // BYTE
			"A", // ASCII
			"S", // SHORT
			"L", // LONG
			"R", // RATIONAL
			"SB", // SBYTE
			"U", // UNDEFINED
			"SS", // SSHORT
			"SL", // SLONG
			"SR", // SRATIONAL
	};

	public static String[] OrientationNames = { "TopLeft", "TopRight",
			"BotRight", "BotLeft", "LeftTop", "RightTop", "RightBot", "LeftBot" };

	public static String getCIFFTypeName(int type) {
		switch (type & CIFF.K_DATATYPEMASK) {
		case CIFF.K_DT_BYTE:
			return "Byte";
		case CIFF.K_DT_ASCII:
			return "ASCII";
		case CIFF.K_DT_WORD:
			return "Word";
		case CIFF.K_DT_DWORD:
			return "Double word";
		case CIFF.K_DT_BYTE2:
			return "Byte2";
		case CIFF.K_DT_HEAPTYPEPROPERTY1:
			return "Heap1";
		case CIFF.K_DT_HEAPTYPEPROPERTY2:
			return "Heap2";
		}
		return "Unknown";
	}

	public static String getTagName(int tag) {
		String result = (String) tagnames.get(tag);
		return (result != null) ? result : ("0x" + Integer.toHexString(tag));
	}

	public static String getPropName(int tag) {
		String result = (String) propnames.get(tag);
		return (result != null) ? result : ("0x" + Integer.toHexString(tag));
	}

	public static String getTypeName(int type) {
		return ExifTagTypes[type - 1];
	}

	static Hashtable<Integer, String> tagnames;
	static Hashtable<Integer, String> propnames;

	static {
		tagnames = new Hashtable<Integer, String>(ExifTagNames.length);
		for (int i = 0; i < ExifTagNames.length; i++)
			tagnames.put((Integer) ExifTagNames[i][0],
					(String) ExifTagNames[i][1]);

		propnames = new Hashtable<Integer, String>(CIFFPropsNames.length);
		for (int i = 0; i < CIFFPropsNames.length; i++)
			propnames.put((Integer) CIFFPropsNames[i][0],
					(String) CIFFPropsNames[i][1]);
	}

}
