/* MediaUtil LLJTran - $RCSfile: MakerNoteHandlerFactory.java,v $
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
 *  $Id: MakerNoteHandlerFactory.java,v 1.4 2007/12/15 01:44:24 drogatkin Exp $
 *
 * Some ideas and algorithms were borrowed from:
 * Thomas G. Lane, and James R. Weeks
 * Contribution for Maker Notes handling by Vincent Deconinck
 *
 */
package android.mediautil.image.jpeg.maker;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import android.content.res.XmlResourceParser;
import android.mediautil.MediaUtil;
import android.mediautil.R;
import android.mediautil.generic.Log;
import android.mediautil.image.jpeg.AbstractImageInfo;

import java.io.IOException;
import java.lang.reflect.Constructor;

/**
 * This factory instantiates a class suited to handle the MakerNote block used
 * for a given make and model
 */
public class MakerNoteHandlerFactory {
	private static String TAG = "MakerNote";

	/**
	 * Returns a handler according to XML config file.
	 * 
	 * @param make
	 * @param model
	 * @return
	 */
	public static MakerNoteHandler getHandler(String make, String model) {
		MakerNoteHandler handler = null;

		if (!AbstractImageInfo.NA.equals(make)) {
			if (!AbstractImageInfo.NA.equals(model)) {
				// Both make and models are specified. Try to find an exact
				// match
				handler = instantiate(make, model);
				if (handler != null) {
					return handler;
				}
			}
			// Either model was not specified, or no specific handler is defined
			// for this model
			// Try to find a match for the make
			handler = instantiate(make, null);
			if (handler != null) {
				return handler;
			}
		}
		// Either the make was not specified, or no generic handler is defined
		// for the make
		// Get a universal handler
		handler = instantiate(null, null);

		return handler;
	}

	private static MakerNoteHandler instantiate(String make, String model) {
		String className = getHandlerClassName(make, model);
		if (className != null && className.trim().length() > 0) {
			try {
				if (Log.debugLevel >= Log.LEVEL_DEBUG)
					android.util.Log.d(TAG, "Trying " + className + "... ");
				Class<?> handlerClass = Class.forName(className);

				// Get its default constructor
				Constructor<?> ct = handlerClass
						.getConstructor((Class<?>) null);

				// Call the constructor to get a plugin instance
				MakerNoteHandler handler = (MakerNoteHandler) ct
						.newInstance((Class<?>) null);

				if (Log.debugLevel >= Log.LEVEL_DEBUG)
					android.util.Log.d(TAG, "OK.");
				return handler;
			} catch (Exception e) {
				if (Log.debugLevel >= Log.LEVEL_DEBUG)
					android.util.Log.d(TAG, "failed.");
			}
		}
		return null;
	}

	private static String getHandlerClassName(String make, String model) {
		String handler = "android.mediautil.image.jpeg.maker.GenericHandler";

		boolean makeFound = false;
		boolean modelFound = false;
		;
		try {
			XmlResourceParser xpp = MediaUtil.getContext().getResources()
					.getXml(R.xml.makernote);
			xpp.next();
			int eventType = xpp.getEventType();
			while (eventType != XmlPullParser.END_DOCUMENT) {
				if (eventType == XmlPullParser.START_DOCUMENT) {
				} else if (eventType == XmlPullParser.START_TAG) {
					if (xpp.getName().equalsIgnoreCase("make")) {
						if (xpp.nextText().equalsIgnoreCase(make))
							makeFound = true;
						else {
							makeFound = false;
							modelFound = false;
						}
					} else if (xpp.getName().equalsIgnoreCase("model")) {
						if (xpp.nextText().equalsIgnoreCase(model))
							modelFound = true;
						else
							modelFound = false;
					} else if (xpp.getName().equalsIgnoreCase("handler")) {
						if (makeFound || modelFound)
							handler = xpp.nextText();
						if (makeFound && modelFound)
							break;
					}
				}
				eventType = xpp.next();
			}
		} catch (XmlPullParserException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		return handler;
	}
}
