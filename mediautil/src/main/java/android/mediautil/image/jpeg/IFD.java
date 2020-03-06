/* MediaUtil LLJTran - $RCSfile: IFD.java,v $
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
 *	$Id: IFD.java,v 1.4 2006/12/22 21:29:32 vicne Exp $
 *
 * Some ideas and algorithms were borrowed from:
 * Thomas G. Lane, and James R. Weeks
 * Contribution by Vincent Deconinck
 */
package android.mediautil.image.jpeg;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class IFD extends Entry {
	/**
	 * 
	 */
	private static final long serialVersionUID = -8866775011364812679L;
	public IFD(int tag) {
		this(tag, Exif.UNDEFINED);
	}
	
	public IFD(int tag, int type) {
		super(type);
		this.tag = tag;
		entries = new TreeMap<Integer, Entry>();
	}
	
	public void addEntry(int tag, Entry entry) {
		entries.put(tag, entry);
	}
	
	public void removeEntry(int tag) {
		entries.remove(tag);
	}

	public void addIFD(IFD ifd) {
		IFD[] temp = ifds==null?new IFD[1]:new IFD[ifds.length+1];
		temp[ifds==null?0:ifds.length] = ifd;
		if (ifds != null)
			System.arraycopy(ifds, 0, temp, 0, ifds.length);
		ifds = temp;
	}
	
	public Entry getEntry(int tag, int subTag) {
		Entry result = entries.get(tag);
		if (result != null)
			return result;
		if (subTag > 0) {
			for (int i=0; i<ifds.length; i++)
				if (ifds[i].getTag() == subTag)
					return ifds[i].getEntry(tag, -1);
		} else {
			for (int i=0; ifds != null && i<ifds.length; i++) {
				result = ifds[i].getEntry(tag, -1);
				if (result != null)
					break;
			}
		}
		return result;
	}
	
	public IFD getIFD(int tag) {
		for (int i=0; i<ifds.length; i++)
			if (ifds[i].getTag() == tag)
				return ifds[i];
		return null;
	}
	
	public int getTag() {
		return tag;
	}
	
	public Entry setEntry(int tag, int subTag, Entry value) {
		Entry result = null;
		if (subTag > 0) {
			for (int i=0; i<ifds.length; i++)
				if (ifds[i].getTag() == subTag)
					return ifds[i].setEntry(tag, -1, value);
		} else if (subTag == 0) {
			result = entries.put(tag, value);
		} else {
			for (int i=0; i<ifds.length; i++) {
				result = ifds[i].getEntry(tag, -1);
				if (result != null) {
					ifds[i].setEntry(tag, 0, value);
					break;
				}
			}
		}
		return result;
	}
	
	public Map<Integer, Entry> getEntries() {
		return entries;
	}
	
	public IFD[] getIFDs() {
		return ifds;
	}

	protected SortedMap<Integer, Entry> entries;
	protected IFD[] ifds;
	protected int tag;
}
