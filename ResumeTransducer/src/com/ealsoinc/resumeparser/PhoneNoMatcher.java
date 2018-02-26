package com.ealsoinc.resumeparser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PhoneNoMatcher {

	final static String regex = "(?:(?:\\+|0{0,2})91(\\s*[\\-]\\s*)?|[0]?)?[789]\\d{9}";
	//final String string = "8285734838";

	public static String findPhoneNo(String data) {
		
		final Pattern pattern = Pattern.compile(regex);
		final Matcher matcher = pattern.matcher(data);
		
		if(matcher.find())
		    return matcher.group(0);
		else 
			return "";
	}
}
