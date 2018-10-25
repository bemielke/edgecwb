/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 1999 Anthony Lomax <lomax@faille.unice.fr>
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

/**
 * A class to represent complex numbers
 */
public final class Cmplx {

    /**
     * The real part of this complex value.
     */
    public double r;
    /**
     * The imaginary part of this complex value.
     */
    public double i;
    private static final double PI = Math.PI;
    private static final double TWOPI = 2.0 * Math.PI;

    public Cmplx() {
        ;
    }

    public Cmplx(Cmplx c) {
        this.r = c.r;
        this.i = c.i;
    }

    public Cmplx(double re, double im) {
        this.r = re;
        this.i = im;
    }

    public Cmplx(double re) {
        this.r = re;
        this.i = 0.0;
    }

    // class methods and basic instance methods
    /**
     * Returns the sum of two complex values.
     *
     * @param a a complex value.
     * @param b a complex value.
     * @return the complex sum a+b.
     */
    public static final Cmplx add(Cmplx a, Cmplx b) {
        Cmplx c = new Cmplx();
        c.r = a.r + b.r;
        c.i = a.i + b.i;
        return c;
    }

    /**
     * Add another complex to this complex.
     *
     * @param b a complex value.
     * @return this complex after summation
     */
    public final Cmplx add(Cmplx b) {
        this.r += b.r;
        this.i += b.i;
        return this;
    }

    /**
     * Returns the difference of two complex values.
     *
     * @param a a complex value.
     * @param b a complex value.
     * @return the complex difference a-b.
     */
    public static final Cmplx sub(Cmplx a, Cmplx b) {
        Cmplx c = new Cmplx();
        c.r = a.r - b.r;
        c.i = a.i - b.i;
        return c;
    }

    /**
     * Subtract another complex from this complex.
     *
     * @param b a complex value.
     * @return this complex after summation
     */
    public final Cmplx sub(Cmplx b) {
        this.r -= b.r;
        this.i -= b.i;
        return this;
    }

    /**
     * Returns the product of two complex values.
     *
     * @param a a complex value.
     * @param b a complex value.
     * @return the complex product a*b.
     */
    public static final Cmplx mul(Cmplx a, Cmplx b) {
        Cmplx c = new Cmplx();
        c.r = a.r * b.r - a.i * b.i;
        c.i = a.i * b.r + a.r * b.i;
        return c;
    }

    /**
     * Multiply this complex by another complex.
     *
     * @param b a complex value.
     * @return this complex after multiplication
     */
    public final Cmplx mul(Cmplx b) {
        double rtmp = this.r;
        double itmp = this.i;
        this.r = rtmp * b.r - itmp * b.i;
        this.i = itmp * b.r + rtmp * b.i;
        return this;
    }

    /**
     * Returns the product of a complex value and a double value.
     *
     * @param a a complex value.
     * @param b a double value.
     * @return the complex product a*b.
     */
    public static final Cmplx mul(Cmplx a, double b) {
        Cmplx c = new Cmplx();
        c.r = a.r * b;
        c.i = a.i * b;
        return c;
    }

    /**
     * Returns the product of this value and a double value.
     *
     * @param b a double value.
     * @return this complex after multiplication.
     */
    public final Cmplx mul(double b) {
        this.r *= b;
        this.i *= b;
        return this;
    }

    /**
     * Returns the quotient of a complex and a double value.
     *
     * @param a a complex value.
     * @param b a double value.
     * @return the complex division a/b.
     */
    public static final Cmplx div(Cmplx a, double b) {

        Cmplx c = new Cmplx(a);
        c.r /= b;
        c.i /= b;

        return c;
    }

    /**
     * Divide this complex by a double value.
     *
     * @param b a double value.
     * @return the complex division a/b.
     */
    public final Cmplx div(double b) {

        this.r /= b;
        this.i /= b;

        return this;
    }

    /**
     * Divide this complex by another complex.
     *
     * @param a a complex value.
     * @param b a complex value.
     * @return the complex division a/b.
     */
    public final Cmplx div(Cmplx b) {

        double rtmp = this.r;
        double itmp = this.i;

        double r, den;
        if (Math.abs(b.r) >= Math.abs(b.i)) {
            r = b.i / b.r;
            den = b.r + r * b.i;
            this.r = (rtmp + r * itmp) / den;
            this.i = (itmp - r * rtmp) / den;
        } else {
            r = b.r / b.i;
            den = b.i + r * b.r;
            this.r = (rtmp * r + itmp) / den;
            this.i = (itmp * r - rtmp) / den;
        }
        return this;

    }

    /**
     * Returns the quotient of two complex values.
     *
     * @param a a complex value.
     * @param b a complex value.
     * @return the complex division a/b.
     */
    public static final Cmplx div(Cmplx a, Cmplx b) {
        Cmplx c = new Cmplx();
        double r, den;
        if (Math.abs(b.r) >= Math.abs(b.i)) {
            r = b.i / b.r;
            den = b.r + r * b.i;
            c.r = (a.r + r * a.i) / den;
            c.i = (a.i - r * a.r) / den;
        } else {
            r = b.r / b.i;
            den = b.i + r * b.r;
            c.r = (a.r * r + a.i) / den;
            c.i = (a.i * r - a.r) / den;
        }
        return c;
    }

    /**
     * Returns the complex conjugate of a complex value.
     *
     * @return a new complex which is the conjugate of the argument.
     */
    public static final Cmplx conjg(Cmplx a) {
        Cmplx c = new Cmplx();
        c.r = a.r;
        c.i = -a.i;
        return c;
    }

    /**
     * Returns the complex conjugate of this complex value.
     *
     * @return the complex conjugate.
     */
    public final Cmplx conjg() {
        this.i = -this.i;
        return this;
    }

    // instance methods
    /**
     * Returns a string representation of this complex value.
     *
     * @return the String.
     */
    public final String toString() {
        String s;
        return (s = "(" + this.r + "," + this.i + ")");
    }

    /**
     * Returns the real part of this complex value.
     *
     * @return the real part.
     */
    public final double doubleValue() {
        double r;
        return (r = this.r);
    }

    /**
     * Returns the real part of this complex value.
     *
     * @return the real part.
     */
    public final double real() {
        double r;
        return (r = this.r);
    }

    /**
     * Returns the imaginary part of this complex value.
     *
     * @return the imaginary part.
     */
    public final double imag() {
        double r;
        return (r = this.i);
    }

    /**
     * Returns the magnitude of this complex value.
     *
     * @return the magnitude.
     */
    public final double mag() {
        return (Math.sqrt(this.r * this.r + this.i * this.i));
    }

    /**
     * Returns the magnitude of this complex value.
     *
     * @return the magnitude.
     */
    public final double hypot() {

        final double xAbs = Math.abs(this.r);
        final double yAbs = Math.abs(this.i);
        if (xAbs == 0.0 && yAbs == 0.0) {
            return 0.0;
        } else if (xAbs < yAbs) {
            return (yAbs * Math.sqrt(1.0 + (this.r / this.i) * (this.r / this.i)));
        } else {
            return (xAbs * Math.sqrt(1.0 + (this.i / this.r) * (this.i / this.r)));
        }

    }

    /**
     * Returns the square of the magnitude of this complex value.
     *
     * @return the square of the magnitude.
     */
    public final double mag2() {
        return (this.r * this.r + this.i * this.i);
    }

    /**
     * Returns the phase of this complex value.
     *
     * @return the phase in radians.
     */
    public final double phs() {
        if (this.r == 0.0) {
            if (this.i == 0.0) {
                return (0.0);
            } else {
                return ((this.i / Math.abs(this.i)) * 2.0 * Math.atan(1.0));
            }
        } else {
            return (Math.atan2(this.i, this.r));
        }
    }

    /**
     * Returns the complex square root of this complex value.
     *
     * @return the complex square root.
     */
    public final Cmplx sqrt() {
        Cmplx c = new Cmplx();
        double x, y, w, r;
        if ((this.r == 0.0) && (this.i == 0.0)) {
            c.r = (c.i = 0.0);
            return (c);
        } else {
            x = Math.abs(this.r);
            y = Math.abs(this.i);
            if (x >= y) {
                r = y / x;
                w = Math.sqrt(x) * Math.sqrt(0.5 * (1.0
                        + Math.sqrt(1.0 + r * r)));
            } else {
                r = x / y;
                w = Math.sqrt(y) * Math.sqrt(0.5 * (r
                        + Math.sqrt(1.0 + r * r)));
            }
            if (this.r >= 0.0) {
                c.r = w;
                c.i = this.i / (2.0 * w);
            } else {
                c.i = (this.i >= 0.0) ? w : -w;
                c.r = this.i / (2.0 * c.i);
            }
            return (c);
        }
    }

    /**
     * Returns Euler's number e raised to the power of a complex value.
     *
     * @param c a complex value.
     *
     * @return the value e^c, where e is the base of the natural logarithms.
     */
    public static final Cmplx exp(Cmplx c) {

        double rpart = Math.exp(c.r);
        Cmplx ipart = new Cmplx(Math.cos(c.i), Math.sin(c.i));
        return (ipart.mul(rpart));

    }

    /**
     * Returns Euler's number e raised to the power of a complex value.
     *
     * @param r, i the real and imaginary parts of a complex value.
     *
     * @return the value e^c, where e is the base of the natural logarithms.
     */
    public static final Cmplx exp(double r, double i) {

        double rpart = Math.exp(r);
        Cmplx ipart = new Cmplx(Math.cos(i), Math.sin(i));
        return (ipart.mul(rpart));

    }
    // Static processing methods
    public static double[] data = new double[0];

    /**
     * Returns the complex array forward Fast Fourier Transform of a float array. The complex array will have a length that is the smallest power of 2
     * greater than or equal to the length of the float array.
     *
     * @param fdata the float array.
     * @return the complex transform array.
     */
    public static final Cmplx[] fft(float[] fdata) {

        // find power of two >= to num of points in fdata
        int nPointsPow2 = 1;
        while (nPointsPow2 < fdata.length) {
            nPointsPow2 *= 2;
        }

        // create double array if not large enough
        if (data.length < 2 * nPointsPow2) {
            data = new double[2 * nPointsPow2];
        }

        // load float data to double array
        int i, j;
        for (i = 0, j = 0; i < fdata.length; i++) {
            data[j++] = (double) fdata[i];
            data[j++] = 0.0;
        }
        for (; j < 2 * nPointsPow2; j++) {
            data[j] = 0.0;
        }

        // apply forward FFT
        data = four1(data, 1, 2 * nPointsPow2);

        // create complex array
        Cmplx[] cdata = new Cmplx[nPointsPow2];

        // load double data to complex array
        for (i = 0, j = 0; i < nPointsPow2; i++, j += 2) {
            cdata[i] = new Cmplx(data[j], data[j + 1]);
        }

        //data = null;

        return (cdata);

    }

    /**
     * Returns the float array inverse Fast Fourier Transform of a complex array.
     *
     * @param cdata the complex array.
     * @param nPoints the desired length of the float array.
     * @return the first nPoints of the float transform array.
     */
    public static final float[] fftInverse(Cmplx[] cdata, int nPoints) {

        // create double array if not large enough
        if (data.length < 2 * cdata.length) {
            data = new double[2 * cdata.length];
        }

        // load complex data to double array
        for (int i = 0, j = 0; i < cdata.length; i++) {
            data[j++] = cdata[i].doubleValue();
            data[j++] = cdata[i].imag();
        }

        // apply inverse FFT
        data = four1(data, -1, 2 * cdata.length);

        // create float array
        float[] fdata = new float[nPoints];

        // load double data to float array
        for (int i = 0, j = 0; i < nPoints; i++, j += 2) {
            fdata[i] = (float) data[j];
        }

        return (fdata);

    }

    /**
     * Returns the Fast Fourier Transform of a pseudo-complex double array representing an array of complex pairs. (adapted from Numerical Recipies in
     * C)
     *
     * @param data the input double array.
     * @param isign FFT direction. (isign=1: returns forward FFT of data. isign=-1: returns inverse FFT or data.)
     * @return the first transformed double array.
     */
    public static final double[] four1(double[] data, int isign, int nPoints) {

        int nn = nPoints / 2;
        int n, m, j;
        double temp;

        n = nn << 1;
        j = 0;
        for (int i = 0; i < n; i += 2) {
            if (j > i) {
                //swap(data[j], data[i]);
                temp = data[j];
                data[j] = data[i];
                data[i] = temp;
                //swap(data[j + 1], data[i + 1]);
                temp = data[j + 1];
                data[j + 1] = data[i + 1];
                data[i + 1] = temp;
            }
            m = n >> 1;
            while (m >= 2 && j > m - 1) {
                j -= m;
                m >>= 1;
            }
            j += m;
        }


        int i, istep;
        double wtemp, wr, wpr, wpi, wi, theta;
        double tempr, tempi;

        int mmax = 2;
        while (n > mmax) {
            istep = 2 * mmax;
            theta = TWOPI / (isign * mmax);
            wtemp = Math.sin(0.5 * theta);
            wpr = -2.0 * wtemp * wtemp;
            wpi = Math.sin(theta);
            wr = 1.0;
            wi = 0.0;
            for (m = 0; m < mmax; m += 2) {
                for (i = m; i < n; i += istep) {
                    j = i + mmax;
                    tempr = wr * data[j] - wi * data[j + 1];
                    tempi = wr * data[j + 1] + wi * data[j];
                    data[j] = data[i] - tempr;
                    data[j + 1] = data[i + 1] - tempi;
                    data[i] += tempr;
                    data[i + 1] += tempi;
                }
                wr = (wtemp = wr) * wpr - wi * wpi + wr;
                wi = wi * wpr + wtemp * wpi + wi;
            }
            mmax = istep;
        }

        if (isign == -1) {
            for (m = 0; m < 2 * nn; m += 2) {
                data[m] /= nn;
            }
        }

        return (data);

    }
    // functions from JSci.maths.Complex
    public static final Cmplx ZERO = new Cmplx(0.0, 0.0);
    public static final Cmplx I = new Cmplx(0.0, 1.0);
    public static final Cmplx ONE = new Cmplx(1.0, 0.0);
    public static final Cmplx MINUS_ONE = new Cmplx(-1.0, 0.0);
    public static final Cmplx MINUS_I = new Cmplx(0.0, -1.0);
    public static final Cmplx HALF = new Cmplx(0.5, 0.0);
    public static final Cmplx MINUS_HALF = new Cmplx(-0.5, 0.0);
    public static final Cmplx HALF_I = new Cmplx(0.0, 0.5);
    public static final Cmplx MINUS_HALF_I = new Cmplx(0.0, -0.5);
    public static final Cmplx TWO = new Cmplx(2.0, 0.0);
    public static final Cmplx MINUS_TWO = new Cmplx(-2.0, 0.0);
    public static final Cmplx SQRT_HALF = new Cmplx(Math.sqrt(0.5), 0.0);
    public static final Cmplx SQRT_HALF_I = new Cmplx(0.0, Math.sqrt(0.5));
    public static final Cmplx MINUS_SQRT_HALF_I = new Cmplx(0.0, -Math.sqrt(0.5));
    //public static final Cmplx PI=new Cmplx(Math.PI,0.0);
    public static final Cmplx PI_I = new Cmplx(0.0, Math.PI);
    public static final Cmplx PI_2 = new Cmplx(Math.PI / 2.0, 0.0);
    public static final Cmplx MINUS_PI_2 = new Cmplx(-Math.PI / 2.0, 0.0);
    public static final Cmplx PI_2_I = new Cmplx(0.0, Math.PI / 2.0);
    public static final Cmplx MINUS_PI_2_I = new Cmplx(0.0, -Math.PI / 2.0);

    // LOG
    /**
     * Returns the natural logarithm (base e) of a complex number.
     *
     * @jsci.planetmath NaturalLogarithm2
     * @param z a complex number.
     */
    public static Cmplx log(final Cmplx z) {
        return new Cmplx(Math.log(z.mag()), z.phs());
    }

    private final static Cmplx log(final double real, final double imag) {
        return new Cmplx(Math.log((new Cmplx(real, imag)).mag()), (new Cmplx(real, imag)).phs());
    }

    private final static Cmplx log_2(final double real, final double imag) {
        return new Cmplx(Math.log((new Cmplx(real, imag)).mag()) / 2.0, (new Cmplx(real, imag)).phs() / 2.0);
    }

    private final static Cmplx log_2I(final double real, final double imag) {
        return new Cmplx((new Cmplx(real, imag)).phs() / 2.0, -Math.log((new Cmplx(real, imag)).mag()) / 2.0);
    }

    private final static Cmplx log_2IplusPI_2(final double real, final double imag) {
        return new Cmplx(((new Cmplx(real, imag)).phs() + Math.PI) / 2.0, -Math.log((new Cmplx(real, imag)).mag()) / 2.0);
    }

    // INVERSE SINH
    /**
     * Returns the arc hyperbolic sine of a complex number, in the range of (-<img border=0 alt="infinity" src="doc-files/infinity.gif"> through <img
     * border=0 alt="infinity" src="doc-files/infinity.gif">, -<img border=0 alt="pi" src="doc-files/pi.gif">/2 through <img border=0 alt="pi"
     * src="doc-files/pi.gif">/2).
     *
     * @param z a complex number.
     */
    public static Cmplx asinh(final Cmplx z) {
        if (z.equals(I)) {
            return PI_2_I;
        } else if (z.equals(MINUS_I)) {
            return MINUS_PI_2_I;
        } else {
            // log(z+sqrt(z*z+1))
            final Cmplx root = (new Cmplx(z.r * z.r - z.i * z.i + 1.0, 2.0 * z.r * z.i).sqrt());
            return log(z.r + root.r, z.i + root.i);
        }
    }
}	// End class Cmplx

