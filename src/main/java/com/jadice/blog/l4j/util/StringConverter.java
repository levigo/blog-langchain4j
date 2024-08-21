package com.jadice.blog.l4j.util;

import java.text.DecimalFormat;

public class StringConverter {
	private static DecimalFormat bytesProcessedFormat = new DecimalFormat("#############################0.00");

	/**
	 * Converts an amount of bytes to a nice readable String. E.g. if you pass
	 * "500", this Method will return "500 byte". If you pass "1500000", this Method
	 * will return "1,49 megabyte".
	 * 
	 * @param amountOfBytes
	 * @return a value String, e.g.: 500 byte
	 */
	public static String getBytesString(long amountOfBytes) {
		String s = "";
		int t = 1024;
		String einheit = " byte";

		double x = amountOfBytes;
		// if(x >= t*t*t*t){
		// x = x/(t*t*t*t);
		// einheit = " tb";
		// }else
		if (x >= t * t * t) {
			x = x / (t * t * t);
			einheit = " gb";
		} else if (x >= t * t) {
			x = x / (t * t);
			einheit = " mb";
		} else if (x >= t) {
			x = x / t;
			einheit = " kb";
		} else {
			einheit = " byte";
		}

		s = bytesProcessedFormat.format(x) + einheit;

		return s;
	}
}
