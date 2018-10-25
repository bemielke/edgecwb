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

import java.text.DecimalFormat;

public class NumberFormat {
    
    public static final double INV_LOG10 = 1.0 / Math.log(10.0);
    
    public static final java.text.DecimalFormat DECIMAL_FORMAT = new java.text.DecimalFormat();
    public static final java.text.DecimalFormat EXPONENTIAL_FORMAT = new java.text.DecimalFormat("0.E0");
    public static final char DECIMAL_SEPARATOR = DECIMAL_FORMAT.getDecimalFormatSymbols().getDecimalSeparator();
    /*
    protected static final java.text.DecimalFormat[] EXPONENTIAL_FORMAT = {
        new java.text.DecimalFormat("0.E0"),
        new java.text.DecimalFormat("0.0E0"),
        new java.text.DecimalFormat("0.00E0"),
        new java.text.DecimalFormat("0.000E0"),
        new java.text.DecimalFormat("0.000E0"),
        new java.text.DecimalFormat("0.0000E0"),
        new java.text.DecimalFormat("0.00000E0"),
        new java.text.DecimalFormat("0.000000E0"),
        new java.text.DecimalFormat("0.0000000E0"),
        new java.text.DecimalFormat("0.00000000E0"),
    };*/
    
    
    /** Method to convert double to string */
    
    public static final String decimalString(double dval, int idec) {
        
        return(decimalString(dval, -1, idec, false));
    }
    
    
    /** Method to convert double to string */
    
    public static final String decimalString(double dval, int ilen, int idec, boolean zeropad) {
        
        return(decimalString(dval, ilen, idec, idec, zeropad));
        
    }
    
    /** Method to convert double to string */
    
    public static final String decimalString(double dval, int ilen, int minFractionDigits, int maxFractionDigits, boolean zeropad) {
        
        // check for values that require exponential form
        double test = Math.abs(dval);
        if (test > 1.0e7 || (test < 1.0e-7 && test > Double.MIN_VALUE))
            return(exponentialString(dval, minFractionDigits, maxFractionDigits));
        
        DECIMAL_FORMAT.setMinimumFractionDigits(minFractionDigits);
        DECIMAL_FORMAT.setMaximumFractionDigits(maxFractionDigits);
        DECIMAL_FORMAT.setGroupingUsed(false);
        
        String str = DECIMAL_FORMAT.format(dval);
        
        if (zeropad)
            str = zeroPadString(str, ilen, maxFractionDigits);
        //System.out.println(" -> str " + str);
        
        return (str);
    }
    
    
    
    /** Method to convert double to string */
    
    public static final String exponentialString(double dval, int minFractionDigits, int maxFractionDigits) {
        
        //java.text.DecimalFormat format = EXPONENTIAL_FORMAT[idec];
        EXPONENTIAL_FORMAT.setMinimumFractionDigits(minFractionDigits);
        EXPONENTIAL_FORMAT.setMaximumFractionDigits(maxFractionDigits);
        
        
        String str = EXPONENTIAL_FORMAT.format(dval);
        
        return (str);
    }
    
    
    
    /** Method to convert integer to zero padded string */
    
    public static final String intString(int ival, int ilen) {
        
        String str = String.valueOf(ival);
        
        while (str.length() < ilen)
            str = "0" + str;
        
        return (str);
    }
    
    
    /** Method to prepend blanks numeric String to given length */
    
    public static final String prependBlanks(String str, int ilen) {
        
        // if not long enough, prepend blank
        while (str.length() < ilen) {
            str = " " + str;
        }
        
        return (str);
        
    }
    
    
    /** Method to zero pad numeric String to given length */
    
    public static final String zeroPadString(String str, int ilen, int idec) {
        
        // if not long enough, add decimal point
        int count = 0;
        while (count < 1000 && str.length() < ilen) {
            if (str.indexOf(DECIMAL_SEPARATOR) < 0)
                str += ".";
            count++;	// avoid infinite loop
        }
        
        // if no length limit or not long enough, add decimal precision
        count = 0;
        while (count < 1000 && (ilen <= 0 || str.length() < ilen)) {
            if (str.length() - str.indexOf(DECIMAL_SEPARATOR) - 1 < idec)
                str += "0";
            else
                break;
            count++;	// avoid infinite loop
        }
        
        // if not long enough, add leading 0's
        count = 0;
        while (count < 1000 && str.length() < ilen) {
            str = "0" + str;
            count++;	// avoid infinite loop
        }
        
        
        return (str);
        
    }
    
    
    /** Method to zero pad numeric String to given length */
    
    public static final String removeTrailingZeros(String str) {
        
        if (str.indexOf(DECIMAL_SEPARATOR) < 0)   // no decimal, do nothing
            return(str);
        
        StringBuffer tempStr = new StringBuffer(str);
        
        int iEnd = tempStr.length();
        
        int iExp = tempStr.indexOf("E");
        if (iExp < 0)
            iExp = tempStr.indexOf("e");
        if (iExp >= 0)
            iEnd = iExp;
        
        for (int i = iEnd - 1; i >= 0; i--) {
            
            char currChar = tempStr.charAt(i);
            
            if (currChar == '0') {
                tempStr.delete(i, i + 1);
                continue;
            }
            
            if (currChar == DECIMAL_SEPARATOR)
                tempStr.delete(i, i + 1);
            
            break;
            
        }
        
        return (tempStr.toString());
        
    }
    
    
    private static final int MAXIMUM_EXPONENT = 8;
    
    
    /** Method to convert double to string */
    
    public static final String doubleString(double dval) {
        
        return(doubleString(dval, -1, -1, false));
        
    }
    
    
    /** Method to convert double to string */
    
    public static final String doubleString(double dval, int ilen, int idec) {
        
        return(doubleString(dval, ilen, idec, false));
        
    }
    
    
    /** Method to convert double to string */
    
    public static final String doubleString(double dval, int ilen, int idec, boolean zeropad) {
        
        int exponent = 0;
        double absval = Math.abs(dval);
        if (absval > Double.MIN_VALUE)
            exponent = (int) (Math.log(absval) * INV_LOG10);
        
        boolean shouldRemoveTrailingZeros = false;
        
        String str = null;
        if (exponent < -3 || exponent > 4) {
            if (idec < 0) {
                shouldRemoveTrailingZeros = true;
                idec = 4;
            }
            str = exponentialString(dval, idec, idec);
        } else {
            if (idec < 0) {
                shouldRemoveTrailingZeros = true;
                if (exponent <= 0)
                    idec = Math.abs(exponent) + 5;
                else
                    idec = 4;
            }
            str =  decimalString(dval, ilen, idec, idec, zeropad);
        }
        
        if (shouldRemoveTrailingZeros)
            str = removeTrailingZeros(str);
        
        return(str);
    }
    
    
    /** Method to convert float to string */
    
    public static final String floatString(float fval, int ilen, int idec) {
        
        return(floatString(fval, ilen, idec, true));
        
    }
    
    /** Method to convert float to string */
    
    public static final String floatString(float fval, int ilen, int idec, boolean zeropad) {
        
        return(doubleString(fval, ilen, idec, zeropad));
        
    }
    
    
    
    /** remake of String.valueOf() */
    
    public static final String StringValueOf(double dval, int maxExp){
        
        return (String.valueOf(dval));
        
    }
    
    public static final String StringValueOf(float fval, int maxExp){
        //return StringValueOf((double) fval, maxExp);
        return (String.valueOf(fval));
    }
    
    
    
    /** Method to find nearest power of 10 to a specified value */
    
    public static float nearestPower10(double value) {
        
        double niceValue = Math.pow(10.0, (double) ((int) (0.5 + Math.log(value) * INV_LOG10)));
        
        return((float) niceValue);
        
    }
    
    
    /** Method to find tic mark locations */
    
    public static double niceValue(double value) {
        
        double niceValue = Math.pow(10.0, (double) (1 + (int) (Math.log(value) * INV_LOG10)) );
        int icount = 0;	// prevent infinite loop
        do {
            if (niceValue > value)
                niceValue /= 2.0;
            if (niceValue > value)
                niceValue /= 2.5;
            if (niceValue > value)
                niceValue /= 2.0;
            
        } while (niceValue > value && icount++ < 1000);
        
        return(niceValue);
        
    }
    
    
    
    /** main method for testing */
    
    public static void main(String argv[]) {
        
        double dval;
        
        dval = 0.00000001;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 0.0000001;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 0.000001;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 0.00001;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 0.0001;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 0.001;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 0.01;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 0.1;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 1.0;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 10.0;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 100.0;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 1000.0;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 10000.0;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 100000.0;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 1000000.0;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 10000000.0;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        
        
        dval = 0.000000012345;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 0.00000012345;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 0.0000012345;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 0.000012345;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 0.00012345;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 0.0012345;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 0.012345;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 0.12345;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 1.2345;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 12.345;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 123.45;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 1234.5;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 12345.;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 123450.;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 1234500.;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 12345000.;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 123450000.;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        
        
        dval = -123450000.;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 1.2345e56;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = -1.2345e56;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = 1.2345e-65;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        dval = -1.2345e-65;
        System.out.println("" + dval + "\t\t" +  doubleString(dval) + "\t\t" + ((dval - Double.parseDouble(doubleString(dval))) / dval));
        
        
    }
    
    
    
    
}

