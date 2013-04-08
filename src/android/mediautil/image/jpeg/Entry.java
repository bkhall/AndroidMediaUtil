/* MediaUtil LLJTran - $RCSfile: Entry.java,v $
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
 *	$Id: Entry.java,v 1.5 2006/12/22 21:12:06 vicne Exp $
 *
 * Some ideas and algorithms were borrowed from:
 * Thomas G. Lane, and James R. Weeks
 */
package android.mediautil.image.jpeg;

import java.io.Serializable;

public class Entry implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1688494049476253296L;
	public Entry(int type) {
		this.type = type;
	}

	public Entry(String value) {
		this(Exif.ASCII, value);
	}

	public Entry(int type, String value) {
		this.type = type;
		str = value;
	}

	public Entry(int type, Object[] value) {
		this.type = type;
		this.value = value;
	}

	public int getType() {
		return type;
	}

	public String toString() {
		if (str != null)
			return str;
		StringBuffer buff = new StringBuffer();
		for (int i = 0; i < value.length; i++)
			buff.append(
					(type != Exif.UNDEFINED) ? value[i] : Integer
							.toHexString(((Integer) value[i]).intValue()))
					.append(' ');// .append('|');
		return buff.toString();

	}

	public Object[] getValues() {
		return value;
	}

	public Object getValue(int index) {
		if (value != null)
			return value[index];
		else if (str != null)
			return str;
		return null;
	}

	public void setValue(int index, Object newValue) {
		if (newValue instanceof String)
			str = (String) newValue;
		else if (value != null && index < value.length)
			value[index] = newValue;
		else {
			Object[] tempHolder = new Object[index + 1];
			if (value != null)
				System.arraycopy(value, 0, tempHolder, 0, value.length);
			tempHolder[index] = newValue;
			value = tempHolder;
		}
	}

	private int type;
	private String str;
	private Object[] value;
}
