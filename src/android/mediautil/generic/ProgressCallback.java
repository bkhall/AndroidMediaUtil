/* MediaUtil ProgressCallback - $RCSfile: ProgressCallback.java,v $
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
 *  $Id: ProgressCallback.java,v 1.4 2013/02/26 08:21:51 drogatkin Exp $
 *
 */
package android.mediautil.generic;

/**
 * Interface for notifying progress for use by Callers which need to know of the
 * progress of a time consuming method possibly to display the progress.
 */
public interface ProgressCallback {
	/**
	 * This is the callback method is called when the required progress is made
	 * as returned by getCallBackInterval().
	 * 
	 * @param fraction
	 *            Fraction of the progress made (0-1)
	 * @param percent
	 *            percent of the progress made (0-100)
	 */
	public void progressHandler(double fraction, int percent);

	/**
	 * This method is called to get the progress after which the callback should
	 * be made. It should be implemented to return the required Callback
	 * Interval which is a fraction between 0-1.
	 * <p>
	 * 
	 * How often this method is called should be specified by the Object
	 * providing the callback. It maybe every time some progress is made or be
	 * called just once or be called once in the beginning and once for every
	 * callback made.
	 * 
	 * @return Callback Interval between 0 and 1. For example 0.1 means that
	 *         <b>progressHandler(..)</b> will be called for every 10% of
	 *         progress.
	 */
	public double getCallbackInterval();
}
