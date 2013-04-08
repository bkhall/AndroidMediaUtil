/* MediaUtil LLJTran - $RCSfile: Log.java,v $
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
 *  $Id: Log.java,v 1.3 2005/09/30 21:23:18 drogatkin Exp $
 *
 */
package android.mediautil.generic;

/**
 * This class provides a place to set the debug level which ImageUtil classes
 * refer to while printing messages.
 */
public class Log {
	/** Debug Level which specifies that no messages are to be printed */
	public final static int LEVEL_NONE = 0;

	/**
	 * Debug Level which specifies that only error messages are to be printed
	 */
	public final static int LEVEL_ERROR = 1;

	/**
	 * Debug Level which specifies that only error and warning messages are to
	 * be printed
	 */
	public final static int LEVEL_WARNING = 2;

	/**
	 * Debug Level which specifies that error, warning and informational
	 * messages are to be printed. Debug messages are not printed.
	 */
	public final static int LEVEL_INFO = 3;

	/**
	 * Debug Level which specifies that all messages including debug messages
	 * are to be printed.
	 */
	public final static int LEVEL_DEBUG = 4;

	/** Current debug Level Setting */
	public static int debugLevel = LEVEL_ERROR;
}
