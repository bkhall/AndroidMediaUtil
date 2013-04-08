/* MediaUtil LLJTran - $RCSfile: IterativeWriter.java,v $
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
 *  $Id: IterativeWriter.java,v 1.3 2005/09/30 21:23:18 drogatkin Exp $
 *
 */
package android.mediautil.generic.directio;

import java.io.IOException;

/**
 * Interface specifying write to a stream in small chunks. This interface
 * specifies an Object to write to an OutputStream in small chunks. An
 * IterativeWriter class can support the directio package's feature to provide
 * an InputStream to read directly the data written by an IterativeWriter.
 * 
 * @see android.mediautil.generic.directio
 * @see InStreamFromIterativeWriter
 * 
 * @author Suresh Mahalingam (msuresh@cheerful.com)
 */
public interface IterativeWriter {
	/**
	 * Specifies that numBytes bytes are to be written. The OutputStream to
	 * Write to should be stored in the implementing class.
	 * 
	 * @param numBytes
	 *            Number of bytes to write. This is only indicative. The
	 *            implementor may write more or less. Writing too less impacts
	 *            performance due to repeated nextWrite calls. Writing too much
	 *            more than numBytes leads to performance impact due to buffer
	 *            reallocation.
	 *            <p>
	 * 
	 *            The OutputStream returned for use by an IterativeWriter by
	 *            directio's classes implement {@link ByteCounter} which can
	 *            help in keeping track of the number of bytes written or
	 *            remaining during a nextWrite call.
	 * @return {@link IterativeReader#CONTINUE} to indicate that there is more
	 *         to be written and IterativeReader.STOP to indicate that the
	 *         IterativeWriter is done with writing.
	 * @exception IOException
	 *                In case of an error during write.
	 */
	public int nextWrite(int numBytes) throws IOException;
}
