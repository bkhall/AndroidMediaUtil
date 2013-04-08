/* MediaUtil LLJTran - $RCSfile: MakerNoteHandler.java,v $
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
 *  $Id: MakerNoteHandler.java,v 1.2 2007/12/15 01:44:24 drogatkin Exp $
 *
 * Some ideas and algorithms were borrowed from:
 * Thomas G. Lane, and James R. Weeks
 * Contribution for Maker Notes handling by Vincent Deconinck
 *
 */
package android.mediautil.image.jpeg.maker;

import java.io.IOException;
import java.io.OutputStream;

import android.mediautil.image.jpeg.Exif;
import android.mediautil.image.jpeg.IFD;
import android.mediautil.image.jpeg.IFDParsingException;



/**
 * This is the interface that all MakerNote handlers have to follow : read and
 * write must be supported
 * 
 * Two implementations exist for now : BlockMakerNoteHandler which considers the
 * notes as a block of Raw Data According to http://www.exif.org/samples.html ,
 * this is the format used by Kodak and Ricoh This is also the handler used for
 * processing of unknown MakerNotes
 * 
 * IDFMakerNoteHandler which considers the notes as an IFD structure and is able
 * to read it and write it with accurate offset handling According to
 * http://www.exif.org/samples.html , this is the format used by Canon,
 * Fujifilm, Nikon, Nikon, Olympus and Sanyo
 * 
 */
public interface MakerNoteHandler {
	void load(Exif exif, IFD ifd, int tag, int type, int offset, int count,
			int typelen) throws IFDParsingException;

	void save(OutputStream out, Exif exif, IFD ifd1) throws IOException;
}
