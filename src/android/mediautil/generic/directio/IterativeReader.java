/* MediaUtil LLJTran - $RCSfile: IterativeReader.java,v $
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
 *  $Id: IterativeReader.java,v 1.3 2005/09/30 21:23:18 drogatkin Exp $
 *
 */
package android.mediautil.generic.directio;

import java.io.IOException;

/**
 * Interface specifying read from a stream in small chunks. This interface
 * specifies an Object to read from an InputStream in small chunks. An
 * IterativeReader class can support the directio package's features of Sharing
 * an InputStream with another reader and providing an OutputStream to save
 * directly for the IterativeReader to read.
 * 
 * @see android.mediautil.generic.directio
 * @see SplitInputStream
 * @see OutStreamToIterativeReader
 * 
 * @author Suresh Mahalingam (msuresh@cheerful.com)
 */
public interface IterativeReader {
	/**
	 * Constant which specifies that the IterativeReader/IterativeWriter is not
	 * done with nextRead/nextWrite.
	 */
	public static final int CONTINUE = 0;
	/**
	 * Constant which specifies that the IterativeReader/IterativeWriter is done
	 * with nextRead/nextWrite. This means that the reading/writing is complete
	 * and further calls of nextRead/nextWrite are not expected.
	 */
	public static final int STOP = 1;

	/**
	 * Specifies that numBytes bytes are to be read. The InputStream to read
	 * from should be stored in the implementing class.
	 * 
	 * @param numBytes
	 *            Number of bytes to read. This is only indicative. The
	 *            implementor may read more or less. Reading too less impacts
	 *            performance due to repeated nextRead calls. Reading too much
	 *            more than numBytes leads to performance impact due to buffer
	 *            reallocation in case of SplitInputStream and an IOException
	 *            due to Empty Buffer in an OutStreamToIterativeReader.
	 *            <p>
	 * 
	 *            The InputStream returned for use by an IterativeReader by
	 *            directio's classes implement {@link ByteCounter} which can
	 *            help in keeping track of the number of bytes read or remaining
	 *            during a nextRead call.
	 * @return {@link #CONTINUE} to indicate that there is more to be read and
	 *         STOP to indicate that the IterativeReader is done with reading.
	 * @exception IOException
	 *                In case of an error during read. Note that data errors can
	 *                also be handled by returning STOP and storing the error
	 *                instead of throwing an Exception. This may be desirable
	 *                for a SplitInputStream since the main Read can continue
	 *                unhindered by the SubStream's error.
	 */
	public int nextRead(int numBytes) throws IOException;
}
