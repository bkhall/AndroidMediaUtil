/* MediaUtil LLJTran - $RCSfile: Record.java,v $
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
 *	$Id: Record.java,v 1.4 2007/12/15 01:44:24 drogatkin Exp $
 *
 * Some ideas and algorithms were borrowed from:
 * Thomas G. Lane, and James R. Weeks
 */
package android.mediautil.image.jpeg;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.util.Locale;
import java.io.Serializable;

public class Record implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 9194837378357559812L;

	private static final DateFormat dateformat = new SimpleDateFormat(
			"yyyy:MM:dd HH:mm:ss", Locale.getDefault());

//	private int colorbitdepth, colorbw, reserved1, reserved2; // review why we
//																// need to
//																// expose
//
	public Record(int type) {
		this.type = type;
	}

	public Record(int type, String value) {
		this(type);
		stringvalue = value;
	}

	public Record(int type, int value) {
		this(type);
		mainvalue = value;
	}

	public Record(int type, int value, float value2) {
		this(type, value);
		mainfloatvalue = value2;
	}

	public Record(int type, int value, int value1, int value2) {
		this(type, value);
//		reserved1 = value1;
//		reserved2 = value2;
	}

	public Record(int type, float value) {
		this(type);
		mainfloatvalue = value;
	}

	public Record(int type, float value, float value1) {
		this(type, value);
		floatvalue1 = value1;
	}

	public Record(int type, float value, float value1, float value2) {
		this(type, value, value1);
		floatvalue2 = value2;
	}

	public Record(int type, int w, int h, float par, int ra, int compbits,
			int colorbits, int colorbw) {
		this(type);
		imagewidth = w;
		imageheight = h;
		rotationangle = ra;
		mainfloatvalue = par;
		componentbitdepth = compbits;
//		colorbitdepth = colorbits;
//		this.colorbw = colorbw;
	}

	public int getWidth() {
		return imagewidth;
	}

	public int getHeight() {
		return imageheight;
	}

	public Integer getType() {
		return type;
	}

	public String getTypeName() {
		return Naming.getPropName(type);
	}

	public float getFloatValue() {
		return mainfloatvalue;
	}

	public float getFloatValue(int i) {
		if (i > 1)
			return floatvalue2;
		return floatvalue1;
	}

	public int getIntValue() {
		return mainvalue;
	}

	public String toString() {
		if (stringvalue != null)
			return stringvalue;
		StringBuffer result = new StringBuffer();
		switch (type.intValue()) {
		case CIFF.K_TC_IMAGESPEC:
			result.append(imagewidth);
			result.append("x");
			result.append(imageheight);
			result.append(" Ratio: ");
			result.append(mainfloatvalue);
			result.append(" Rotation: ");
			result.append(rotationangle);
			result.append(" Bit depth: ");
			result.append(componentbitdepth);
			return result.toString();
		case CIFF.K_TC_SR_EXPOSURE:
			result.append("Compensation: ");
			result.append(mainfloatvalue);
			result.append(" TV: ");
			result.append(floatvalue1);
			result.append(" AV: ");
			result.append(floatvalue2);
			return result.toString();
		case CIFF.K_TC_SR_TARGETDISTANCESETTING:
			result.append(mainfloatvalue);
			return result.toString();
		case CIFF.K_TC_RECORDID:
			result.append(mainvalue);
			return result.toString();
		case CIFF.K_TC_IMAGEFORMAT:
			result.append("JPEG:");
			switch (mainvalue & 0xFFFF) {
			case 0:
				result.append("lossy");
				break;
			case 1:
				result.append("none");
				break;
			case 2:
				result.append("DCT");
				break;
			case 3:
				result.append("PS600");
				break;
			}
			return result.toString();
		case CIFF.K_TC_SR_RELEASETIMING:
			result.append((mainvalue == 0) ? "Shutter" : "Focus");
			return result.toString();
		case CIFF.K_TC_SR_RELEASEMETHOD:
			result.append((mainvalue == 0) ? "Single" : "Continuous");
			return result.toString();
		case CIFF.K_TC_TARGETIMAGETYPE:
			result.append((mainvalue == 0) ? "Real-world" : "Written");
			return result.toString();
		case CIFF.K_TC_SERIALNUMBER:
			result.append(mainvalue);
			return result.toString();
		case CIFF.K_TC_SR_EF:
			result.append(mainfloatvalue);
			return result.toString();
		case CIFF.K_TC_MI_EV:
			result.append(mainfloatvalue);
			return result.toString();
		case CIFF.K_TC_BODYID:
			result.append(mainvalue);
			return result.toString();
		case CIFF.K_TC_BODYSENSITIVITY:
			result.append(mainvalue);
			return result.toString();
		case CIFF.K_TC_CAPTURETIME:
			Calendar cal = Calendar.getInstance();
			cal.setTime(new Date(mainvalue * 1000l));
			synchronized (dateformat) {
				result.append(dateformat.format(new Date(mainvalue
						* 1000l
						+ -(cal.get(Calendar.ZONE_OFFSET) + cal
								.get(Calendar.DST_OFFSET)))));
			}
			// (long)(new
			// Date(mainvalue*1000l).getTimezoneOffset()*60*1000l))));
			return result.toString();
		}
		return Naming.getPropName(type);
	}

	private Integer type;

	private float mainfloatvalue, floatvalue1, floatvalue2;

	private int mainvalue;

	private int imagewidth, imageheight, rotationangle;

	private int componentbitdepth;

	private String stringvalue;
}
