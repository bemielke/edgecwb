/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 2000 Anthony Lomax <anthony@alomax.net www.alomax.net>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package net.alomax.util;

import java.util.*;

/**
 * Extensions to java.lang.String
 */
public class StringExt {

    /**
     * Returns a new string resulting from replacing all occurrences of oldStr in this string with newStr.
     */
    public static String replace(String string, String oldStr, String newStr) {

        String originalString = string;
        String replacedString = "";

        int endNdx = originalString.indexOf(oldStr);
        if (endNdx == -1) {
            return (originalString);
        }
        while (endNdx != -1) {
            replacedString += originalString.substring(0, endNdx) + newStr;
            originalString = originalString.substring(endNdx + oldStr.length());
            endNdx = originalString.indexOf(oldStr);
        }
        replacedString += originalString;

        return (replacedString);
    }

    /**
     * Checks if all characters in a String are printable.
     *
     * Returns the String.
     *
     */
    public static String checkPrintable(
            String str, boolean fix, char cReplace, boolean verbose, String name) {

        for (int n = 0; n < str.length(); n++) {
            char c = str.charAt(n);
            // CalTech 20020521; AJL 20030519
            if (c == 0) {	// C null char
                str = doNotPrintable(n, str, fix, ' ', verbose, name, "\'C-StringNull\'");
            } else if (Character.isWhitespace(c)) {
                continue;
            } else if (!Character.isDefined(c)) {
                str = doNotPrintable(n, str, fix, cReplace, verbose, name, "\'NotDefined\'");
                //			} else if (Character.isIdentifierIgnorable(c)) {
                //				str = doNotPrintable(n, str, fix, cReplace, verbose, name, "\'IdentifierIgnorable\'");
            } else if (Character.isISOControl(c)) {
                str = doNotPrintable(n, str, fix, cReplace, verbose, name, "\'ISOControl\'");
            }
        }

        return (str);

    }

    /**
     * Fixes non printable chars and write a message.
     *
     * Returns the String.
     *
     */
    protected static String doNotPrintable(int n,
            String str, boolean fix, char cReplace,
            boolean verbose, String name, String type) {

        if (verbose) {
            System.out.print(
                    "WARNING: Non-printable " + type + " character \\u"
                    + Character.getNumericValue(str.charAt(n))
                    + " at index " + n + " in String <" + name + "> = \"" + str + "\" of length "
                    + str.length() + ".");
        }
        if (fix) {
            String newstr = str.substring(0, n)
                    + cReplace + str.substring(n + 1, str.length());
            str = newstr;
            if (verbose) {
                System.out.println(" Replaced with \'" + cReplace
                        + "\' to give \"" + str + "\".");
            }
        } else {
            if (verbose) {
                System.out.println("");
            }
        }

        return (str);

    }

    /**
     * parse a String into tokens
     */
    public static String[] parse(String tokenString) {

        return (parse(tokenString, null));

    }

    /**
     * parse a String into tokens
     */
    public static String[] parse(String tokenString, String separator) {

        return (parse(tokenString, separator, true));

    }

    /**
     * parse a String into tokens
     */
    public static String[] parse(String tokenString, String separator, boolean trim) {

        if (tokenString == null) {
            return (null);
        }

        Vector tokenVect = new Vector();

        StringTokenizer strTzr = null;
        if (separator != null) {
            strTzr = new StringTokenizer(tokenString, separator);
        } else {
            strTzr = new StringTokenizer(tokenString);
        }

        try {

            while (strTzr.hasMoreTokens()) {

                String token = strTzr.nextToken();
                if (trim) {
                    token = token.trim();
                }
                tokenVect.addElement(token);

            }

        } catch (NoSuchElementException nse) {
            ;
        }

        if (tokenVect.size() < 1) {
            return (null);
        }

        String[] tokens = new String[tokenVect.size()];
        tokenVect.copyInto(tokens);

        return (tokens);

    }

    /**
     * parse a String into a double array
     */
    public static double[] parseDoubles(String tokenString, String separator) {

        if (tokenString == null) {
            return (null);
        }

        String[] tokens = parse(tokenString, separator);

        double[] values = new double[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            try {
                values[i] = Double.valueOf(tokens[i]).doubleValue();
            } catch (NumberFormatException nfe) {
                values[i] = Double.NaN;
            }
        }

        return (values);

    }

    /**
     * Reads an ASCII decimal floating point number, returning default value on error.
     */
    public static float parseFloat(String str, float defaultValue) {

        try {
            return (Float.parseFloat(str));
        } catch (Exception ignored) {
            return (defaultValue);
        }

    }

    /**
     * Reads an ASCII decimal floating point number, returning default value on error.
     */
    public static double parseDouble(String str, double defaultValue) {

        try {
            return (Double.parseDouble(str));
        } catch (Exception ignored) {
            return (defaultValue);
        }

    }

    /**
     * Reads an ASCII decimal floating point number, returning default value on error.
     */
    public static int parseInt(String str, int defaultValue) {

        try {
            return (Integer.parseInt(str));
        } catch (Exception ignored) {
            return (defaultValue);
        }

    }

    /**
     * pads leading spaces to a String
     */
    public static String padLeading(String str, int len, char padChr) {

        if (str.length() >= len) {
            return (str);
        }

        StringBuffer strBuf = new StringBuffer(128);
        for (int i = 0; i < len - str.length(); i++) {
            strBuf.append(padChr);
        }
        return (strBuf.append(str).toString());

    }

    /**
     * pads leading spaces to a String
     */
    public static String padLeading(String str, int len) {

        return (padLeading(str, len, ' '));

    }

    /**
     * returns count of characters at each index that differ in two Strings
     */
    public static int countCharsDifferent(String str1, String str2) {

        int ndiff = 0;

        int len1 = str1.length();
        int len2 = str2.length();
        int len = len1 < len2 ? len1 : len2;

        for (int n = 0; n < len; n++) {
            if (str1.charAt(n) != str2.charAt(n)) {
                ndiff++;
            }
        }

        ndiff += Math.abs(len1 - len2);

        return (ndiff);

    }

    /**
     * returns count of characters at each index that are the same in two Strings
     */
    public static int countCharsSame(String str1, String str2) {

        int nsame = 0;

        int len1 = str1.length();
        int len2 = str2.length();
        int len = len1 < len2 ? len1 : len2;

        for (int n = 0; n < len; n++) {
            if (str1.charAt(n) == str2.charAt(n)) {
                nsame++;
            }
        }

        return (nsame);

    }
}
