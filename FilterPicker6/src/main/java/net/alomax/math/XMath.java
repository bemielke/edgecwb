/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 2007 Anthony Lomax <anthony@alomax.net>
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


package net.alomax.math;


/** A class containing extra math funciton */

public class XMath {
    
    public static double LOG10 = Math.log(10.0);
    public static final double PI = Math.PI;
    public static final double TWOPI = 2.0 * Math.PI;
    
    
    
    private XMath() {
        ;
    }
    
    
    // class methods and basic instance methods
    // mostly from JSci.maths.ExtraMath
    
    /** Returns the the logarithm base 10 of a value.
     *
     * @param     a  a double value.
     * @return    the logarithm base 10 of a.
     */
    public static final double log10(double a) {
        
        return (Math.log(a) / LOG10);
        
    }
    

    /**
     * Returns the hyperbolic sine of a double.
     * @param x a double.
     */
    public static double sinh(double x) {
        return (Math.exp(x)-Math.exp(-x))/2.0;
    }
    
    
    /**
     * Returns the hyperbolic cosine of a double.
     * @param x a double.
     */
    public static double cosh(double x) {
        return (Math.exp(x)+Math.exp(-x))/2.0;
    }
    
    
    /**
     * Returns the hyperbolic tangent of a double.
     * @param x a double.
     */
    public static double tanh(double x) {
        return sinh(x)/cosh(x);
    }

    
    
    
    /**
     * Returns sqrt(x<sup>2</sup>+y<sup>2</sup>).
     */
    public static double hypot(final double x,final double y) {
        final double xAbs=Math.abs(x);
        final double yAbs=Math.abs(y);
        if(xAbs==0.0 && yAbs==0.0)
            return 0.0;
        else if(xAbs<yAbs)
            return yAbs*Math.sqrt(1.0+(x/y)*(x/y));
        else
            return xAbs*Math.sqrt(1.0+(y/x)*(y/x));
    }
    
}	// End class


