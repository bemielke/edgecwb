/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 2000 Anthony Lomax <anthony@alomax.net>
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
package net.alomax.text;

import java.util.Formatter;
import java.util.Locale;

/**
 * An abstract base class for multi-language text
 *
 * All of the methods of this class are static.
 */
public abstract class LocalizedText {

    protected static final String ENDLINE = System.getProperty("line.separator");
    private static String LOCALE_NAME_DEFAULT = "en_US";
    private static String LANGUAGE_DEFAULT = "en";
    private static String COUNTRY_DEFAULT = "US";
    private static String VARIANT_DEFAULT = "";
    // !! DO NOT make the following final - gives numeric string instead of special chararcters! (Java 1.1.8 only ?)
    public static char A_g = (char) 192;
    public static char A_a = (char) 193;
    public static char A_c = (char) 194;
    public static char A_t = (char) 195;
    public static char C_5 = (char) 199;
    public static char E_g = (char) 200;
    public static char E_a = (char) 201;
    public static char E_c = (char) 202;
    public static char I_g = (char) 204;
    public static char I_a = (char) 205;
    public static char I_c = (char) 206;
    public static char O_g = (char) 210;
    public static char O_a = (char) 211;
    public static char O_c = (char) 212;
    public static char O_t = (char) 213;
    public static char U_g = (char) 217;
    public static char U_a = (char) 218;
    public static char U_c = (char) 219;
    public static char a_g = (char) 224;
    public static char a_a = (char) 225;
    public static char a_c = (char) 226;
    public static char a_t = (char) 227;
    public static char c_5 = (char) 231;
    public static char e_g = (char) 232;
    public static char e_a = (char) 233;
    public static char e_c = (char) 234;
    public static char i_g = (char) 236;
    public static char i_a = (char) 237;
    public static char i_c = (char) 238;
    public static char i_h = (char) 238;
    public static char o_g = (char) 242;
    public static char o_a = (char) 243;
    public static char o_c = (char) 244;
    public static char o_t = (char) 245;
    public static char u_g = (char) 249;
    public static char u_a = (char) 250;
    public static char u_c = (char) 251;
    //
    // 20110304 AJL, Mehmet ERGIN
    public static char c_b = (char) 199;
    //public static char g_s = (char) 487;
    public static char g_s = (char) 287;
    public static char i_b = (char) 304;
    public static char i_s = (char) 305;
    public static char O_b = (char) 214;
    public static char o_s = (char) 246;
    public static char U_b = (char) 220;
    public static char u_s = (char) 252;
    public static char s_b = (char) 350;
    public static char s_s = (char) 351;
    // missing
    //public static char I_b = (char) 000;
    //public static char S_b = (char) 000;
    //
    protected static String localeName = null;
    protected static Locale locale = null;

    /**
     * Sets locale and text
     */
    public static void setLocale(String locName) {

        localeName = locName;

        if (localeName == null) // default en_US
        {
            localeName = LOCALE_NAME_DEFAULT;
        }

        // create Locale object
        locale = toLocale();

        try {
            Locale.setDefault(locale);
        } catch (Exception e) {
            ;	// may be applet - security exception
        }

    }

    /**
     * get Locale String
     */
    public static String getLocaleString() {
        return (localeName);
    }

    /**
     * get Locale object
     */
    public static Locale getLocale() {
        return (locale);
    }

    /**
     * convert locale to Locale object
     */
    public static Locale toLocale() {

        String language = LANGUAGE_DEFAULT;
        String country = COUNTRY_DEFAULT;
        String variant = VARIANT_DEFAULT;

        String locStr = localeName;

        // language
        int ndx = locStr.indexOf("_");
        if (ndx > 0) {
            language = locStr.substring(0, ndx);
            locStr = locStr.substring(ndx + 1);
            //System.out.println("1 - language: <" + language + ">  country: <" + country + ">  variant: <" + variant + ">");
            // country
            ndx = locStr.indexOf("_");
            if (ndx > 0) {
                country = locStr.substring(0, ndx);
                locStr = locStr.substring(ndx + 1);
                // variant
                variant = locStr;
                //System.out.println("2 - language: <" + language + ">  country: <" + country + ">  variant: <" + variant + ">");
            } else {
                country = locStr;
                //System.out.println("3 - language: <" + language + ">  country: <" + country + ">  variant: <" + variant + ">");
            }
        } else {
            language = locStr;
        }
        //System.out.println("4 - language: <" + language + ">  country: <" + country + ">  variant: <" + variant + ">");

        // construct Locale object
        Locale loc = null;
        try {
            loc = new Locale(language, country, variant);
            //loc.setDefault(
            //	new Locale(LANGUAGE_DEFAULT, COUNTRY_DEFAULT));
        } catch (Exception e) {
            e.printStackTrace();
            loc = Locale.getDefault();
        }

        //System.out.println("5 - locale: <" + loc.toString() + ">");


        return (loc);

    }



}	// end class LocalizedText

