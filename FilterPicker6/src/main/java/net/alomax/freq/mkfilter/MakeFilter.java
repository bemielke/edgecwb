/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 2008 Anthony Lomax <anthony@alomax.net www.alomax.net>
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

/*  This code modified from C++ code from:
 
 
 Tony Fisher's Home Page
University of York, Computer Science Dept
 
      Analogue (LC) filter designer
      Greek New Testament
      More mkfilter improvements
 
Condition of Use
The University grants you permission to use this web site for non-profit making purposes only.
 
Users of this page will be very sorry to hear that Tony Fisher, from the Computer Science Dept at York University, died on February 29 2000.
A tribute to Tony can be found here.
 
 */


/* mkfilter -- given n, compute recurrence relation
   to implement Butterworth, Bessel or Chebyshev filter of order n
   A.J. Fisher, University of York   <fisher@minster.york.ac.uk>
   September 1992
 */


package net.alomax.freq.mkfilter;

import net.alomax.math.*;

import java.io.*;


/*
#include <stdio.h>
#include <math.h>
#include <string.h>
 
#include "mkfilter.h"
#include "Cmplx.h"
 **/

public class MakeFilter {
    
    public static final String VERSION = "4.6";
    
    public static final int CALLED = 0;
    public static final int CONSOLE = 1;
    public static int mode = CALLED;
    
    public static int MAXORDER = 10;
    
    public static final int OPT_be  = 0x00001;	/* -Be		Bessel characteristic	       */
    public static final int OPT_bu  = 0x00002;	/* -Bu		Butterworth characteristic     */
    public static final int OPT_ch  = 0x00004;	/* -Ch		Chebyshev characteristic       */
    public static final int OPT_re  = 0x00008;	/* -Re		Resonator		       */
    public static final int OPT_pi  = 0x00010;	/* -Pi		proportional-integral	       */
    
    public static final int OPT_lp  = 0x00020;	/* -Lp		lowpass			       */
    public static final int OPT_hp  = 0x00040;	/* -Hp		highpass		       */
    public static final int OPT_bp  = 0x00080;	/* -Bp		bandpass		       */
    public static final int OPT_bs  = 0x00100;	/* -Bs		bandstop		       */
    public static final int OPT_ap  = 0x00200;	/* -Ap		allpass			       */
    
    public static final int OPT_c   = 0x00400;	/* -c		corner freqs		       */
    public static final int OPT_l   = 0x00800;	/* -l		just list filter parameters    */
    public static final int OPT_o   = 0x01000;	/* -o		order of filter		       */
    public static final int OPT_p   = 0x02000;	/* -p		specified poles only	       */
    public static final int OPT_w   = 0x04000;	/* -w		don't pre-warp		       */
    public static final int OPT_z   = 0x08000;	/* -z		use matched z-transform	       */
    public static final int OPT_Z   = 0x10000;	/* -Z		additional zero		       */
    public static final int OPT_s   = 0x20000;	/* -s		sample rate		       */
    
    public static final double EPS = 1e-10;
    public static final double PI = Math.PI;
    public static final double TWOPI = 2.0 * Math.PI;
    
    
/*
struct pzrep
  { complex poles[MAXPZ], zeros[MAXPZ];
    int numpoles, numzeros;
  };
 **/
    
    static PoleZeroRep splane = new PoleZeroRep();
    static PoleZeroRep zplane = new PoleZeroRep();
    static int order;
    static double raw_alpha1, raw_alpha2, raw_alphaz;
    static Cmplx dc_gain, fc_gain, hf_gain;
    static /*uint*/ int options;
    static double warped_alpha1, warped_alpha2, chebrip, qfactor, sample_rate;
    static boolean infq;
    static /*uint*/ int polemask;
    static double[] xcoeffs = new double[PoleZeroRep.MAXPZ + 1];
    static double[] ycoeffs = new double[PoleZeroRep.MAXPZ + 1];
    
    static Cmplx[] bessel_poles = { /* table produced by /usr/fisher/bessel --	N.B. only one member of each C.Conj. pair is listed */
        new Cmplx(-1.00000000000e+00, 0.00000000000e+00),
        new Cmplx( -1.10160133059e+00, 6.36009824757e-01),
        new Cmplx( -1.32267579991e+00, 0.00000000000e+00),
        new Cmplx( -1.04740916101e+00, 9.99264436281e-01),
        new Cmplx( -1.37006783055e+00, 4.10249717494e-01),
        new Cmplx( -9.95208764350e-01, 1.25710573945e+00),
        new Cmplx( -1.50231627145e+00, 0.00000000000e+00),
        new Cmplx( -1.38087732586e+00, 7.17909587627e-01),
        new Cmplx( -9.57676548563e-01, 1.47112432073e+00),
        new Cmplx( -1.57149040362e+00, 3.20896374221e-01),
        new Cmplx( -1.38185809760e+00, 9.71471890712e-01),
        new Cmplx( -9.30656522947e-01, 1.66186326894e+00),
        new Cmplx( -1.68436817927e+00, 0.00000000000e+00),
        new Cmplx( -1.61203876622e+00, 5.89244506931e-01),
        new Cmplx( -1.37890321680e+00, 1.19156677780e+00),
        new Cmplx( -9.09867780623e-01, 1.83645135304e+00),
        new Cmplx( -1.75740840040e+00, 2.72867575103e-01),
        new Cmplx( -1.63693941813e+00, 8.22795625139e-01),
        new Cmplx( -1.37384121764e+00, 1.38835657588e+00),
        new Cmplx( -8.92869718847e-01, 1.99832584364e+00),
        new Cmplx( -1.85660050123e+00, 0.00000000000e+00),
        new Cmplx( -1.80717053496e+00, 5.12383730575e-01),
        new Cmplx( -1.65239648458e+00, 1.03138956698e+00),
        new Cmplx( -1.36758830979e+00, 1.56773371224e+00),
        new Cmplx( -8.78399276161e-01, 2.14980052431e+00),
        new Cmplx( -1.92761969145e+00, 2.41623471082e-01),
        new Cmplx( -1.84219624443e+00, 7.27257597722e-01),
        new Cmplx( -1.66181024140e+00, 1.22110021857e+00),
        new Cmplx( -1.36069227838e+00, 1.73350574267e+00),
        new Cmplx( -8.65756901707e-01, 2.29260483098e+00),
    };
    
  /*
static void readcmdline(char*[]);
static uint decodeoptions(char*), optbit(char);
static double getfarg(char*);
static int getiarg(char*);
static void usage(), checkoptions(), opterror(char*, int = 0, int = 0), setdefaults();
static void compute_s(), choosepole(complex), prewarp(), normalize(), compute_z_blt();
static complex blt(complex);
static void compute_z_mzt();
static void compute_notch(), compute_apres();
static complex reflect(complex);
static void compute_bpres(), add_extra_zero();
static void expandpoly(), expand(complex[], int, complex[]), multin(complex, int, complex[]);
static void printresults(char*[]), printcmdline(char*[]), printfilter(), printgain(char*, complex);
static void printcoeffs(char*, int, double[]);
static void printrat_s(), printrat_z(), printpz(complex*, int), printrecurrence(), prcomplex(complex);
   **/
    
    
    public static void main(String[] argv) {
        
        mode = CONSOLE;
        try {
            processCommands(argv);
            printresults(argv);
        } catch (Exception ignored) {;}
        
        System.exit(0);
    }
    
    
    public static void processCommands(String[] argv) throws Exception {
        
        readcmdline(argv);
        checkoptions();
        setdefaults();
        if ((options & OPT_re) != 0) {
            if ((options & OPT_bp) != 0) compute_bpres();	   /* bandpass resonator	 */
            if ((options & OPT_bs) != 0) compute_notch();	   /* bandstop resonator (notch) */
            if ((options & OPT_ap) != 0) compute_apres();	   /* allpass resonator		 */
        }
        else {
            if ((options & OPT_pi) != 0) {
                prewarp();
                splane.poles[0] = new Cmplx(0.0, 0.0);
                splane.zeros[0] = new Cmplx(-TWOPI * warped_alpha1, 0.0);
                splane.numpoles = splane.numzeros = 1;
            }
            else {
                compute_s();
                prewarp();
                normalize();
            }
            if ((options & OPT_z) != 0) compute_z_mzt(); else compute_z_blt();
        }
        if ((options & OPT_Z) != 0)
            add_extra_zero();
        expandpoly();
        
    }
    
    
    static void readcmdline(String[] argv) {
        
        options = order = polemask = 0;
        
        if (argv.length == 0)
            usage();
        
        int ap = 0;
        while (ap < argv.length) {
            /*uint*/ int m = decodeoptions(argv[ap++]);
            if ((m & OPT_ch) != 0) chebrip = getfarg(argv[ap++]);
            if ((m & OPT_s) != 0) sample_rate = getfarg(argv[ap++]);
            if ((m & OPT_c) != 0) {
                raw_alpha1 = getfarg(argv[ap++]);
                raw_alpha2 = (argv[ap] != null && argv[ap].charAt(0) != '-') ? getfarg(argv[ap++]) : raw_alpha1;
            }
            if ((m & OPT_Z) != 0)
                raw_alphaz = getfarg(argv[ap++]);
            if ((m & OPT_o) != 0)
                order = getiarg(argv[ap++]);
            if ((m & OPT_p) != 0) {
                while (argv[ap] != null && argv[ap].charAt(0) >= '0' && argv[ap].charAt(0) <= '9') {
                    int p = 31;
                    try {
                        p = Integer.parseInt(argv[ap++]);
                    } catch (Exception e) {;}
                    if (p < 0 || p > 31)
                        p = 31; /* out-of-range value will be picked up later */
                    polemask |= (1 << p);
                }
            }
            if ((m & OPT_re) != 0) {
                String s = argv[ap++];
                if (s != null && s.startsWith("Inf")) {
                    infq = true;
                }
                else {
                    qfactor = getfarg(s);
                    infq = false;
                }
            }
            options |= m;
        }
    }
    
    
    static /*uint*/ int decodeoptions(String s) {
        
        if (!s.startsWith("-") || s.startsWith("-h"))
            usage();
        /*uint*/ int m = 0;
        if (s.startsWith("-Be"))
            m |= OPT_be;
        else if (s.startsWith("-Bu"))
            m |= OPT_bu;
        else if (s.startsWith( "-Ch"))
            m |= OPT_ch;
        else if (s.startsWith( "-Re"))
            m |= OPT_re;
        else if (s.startsWith( "-Pi"))
            m |= OPT_pi;
        else if (s.startsWith( "-Lp"))
            m |= OPT_lp;
        else if (s.startsWith( "-Hp"))
            m |= OPT_hp;
        else if (s.startsWith( "-Bp"))
            m |= OPT_bp;
        else if (s.startsWith( "-Bs"))
            m |= OPT_bs;
        else if (s.startsWith( "-Ap"))
            m |= OPT_ap;
        else {
            for (int n = 1; n < s.length(); n++) {
                /*uint*/ int bit = optbit(s.charAt(n));
                if (bit == 0)
                    usage();
                m |= bit;
            }
        }
        return m;
    }
    
    
    static /*uint*/ int optbit(char c)
    
    {
        switch (c) {
            default:    return 0;
            case 'c':   return OPT_c;
            case 'l':   return OPT_l;
            case 'o':   return OPT_o;
            case 'p':   return OPT_p;
            case 'w':   return OPT_w;
            case 'z':   return OPT_z;
            case 'Z':   return OPT_Z;
            case 's':   return OPT_s;
        }
    }
    
    
    static double getfarg(String s) {
        if (s == null)
            usage();
        double val = 0.0;
        try {
            val = Double.parseDouble(s);
        } catch (Exception e) {;}
        return val;
    }
    
    
    static int getiarg(String s) {
        if (s == null) usage();
        int ival = 0;
        try {
            ival = Integer.parseInt(s);
        } catch (Exception e) {;}
        
        return ival;
    }
    
    
    static void usage() {
        if (mode != CONSOLE)
            return;
        System.out.print( "MakeFilter V." + VERSION + " from <fisher@minster.york.ac.uk>, Java version www.alomax.net\n");
        System.out.print( "Interactive web version at: http://www-users.cs.york.ac.uk/~fisher/mkfilter/trad.html\n");
        System.out.print( "Usage: mkfilter [-Be | -Bu | -Ch <r> | -Pi] [-Lp | -Hp | -Bp | -Bs] [-p <n1> <n2> ...] [-{lwz}] "
        + "[-Z <alphaz>] " + "-o <order> -c <corner1> [ <corner2> ] -s <sample_rate>\n");
        System.out.print( "       mkfilter -Re <q> [-Bp | -Bs | -Ap] [-l] -c <corner> -s <sample_rate>\n\n");
        System.out.print( "  -Be, Bu             = Bessel, Butterworth\n");
        System.out.print( "  -Ch <r>             = Chebyshev (r = dB ripple)\n");
        System.out.print( "  -Pi                 = Proportional-Integral\n");
        System.out.print( "  -Re <q>             = 2-pole resonator (q = Q-factor)\n");
        System.out.print( "  -Lp, Hp, Bp, Bs, Ap = lowpass, highpass, bandpass, bandstop, allpass\n");
        System.out.print( "  -p                  = use listed poles only (ni = 0 .. order-1)\n");
        System.out.print( "  -l                  = just list <order> parameters\n");
        System.out.print( "  -w                  = don't pre-warp frequencies\n");
        System.out.print( "  -z                  = use matched z-transform\n");
        System.out.print( "  -Z                  = additional z-plane zero\n");
        System.out.print( "  order = 1.." + MAXORDER + ";  alpha = f(corner)/f(sample)\n\n");
        System.exit(1);
    }
    
    
    static boolean optsok;
    
    static boolean onebit(/*uint*/ int m) {
        return (m != 0) && ((m & m-1) == 0);
    }
    
    
    static void checkoptions() throws Exception {
        optsok = true;
        //unless(onebit(options & (OPT_be | OPT_bu | OPT_ch | OPT_re | OPT_pi)))
        if (!onebit(options & (OPT_be | OPT_bu | OPT_ch | OPT_re | OPT_pi)))
            opterror("must specify exactly one of -Be, -Bu, -Ch, -Re, -Pi");
        if ((options & OPT_re) != 0) {
            if ((options & (OPT_bp | OPT_bs | OPT_ap)) == 0)
                opterror("must specify exactly one of -Bp, -Bs, -Ap with -Re");
            if ((options & (OPT_lp | OPT_hp | OPT_o | OPT_p | OPT_w | OPT_z)) == 0)
                opterror("can't use -Lp, -Hp, -o, -p, -w, -z with -Re");
        }
        else if ((options & OPT_pi) != 0) {
            if ((options & (OPT_lp | OPT_hp | OPT_bp | OPT_bs | OPT_ap)) == 0)
                opterror("-Lp, -Hp, -Bp, -Bs, -Ap illegal in conjunction with -Pi");
            if (((options & OPT_o) != 0) && (order != 1))
                opterror("-Pi implies -o 1");
        }
        else {
            if ((options & (OPT_lp | OPT_hp | OPT_bp | OPT_bs)) == 0)
                opterror("must specify exactly one of -Lp, -Hp, -Bp, -Bs");
            if ((options & OPT_ap) != 0) opterror("-Ap implies -Re");
            if ((options & OPT_o) != 0) {
                if (order < 1 || order > MAXORDER)
                    opterror("order must be in range 1 .. " + MAXORDER);
                if ((options & OPT_p) != 0)
                { /*uint*/ int m = (1 << order) - 1; /* "order" bits set */
                           if ((polemask & ~m) != 0)
                               opterror("order=" + order + ", so args to -p must be in range 0 .. " + (order-1));
                }
            }
            else opterror("must specify -o");
        }
        if ((options & OPT_c) == 0)
            opterror("must specify -c");
        if ((options & OPT_s) == 0)
            opterror("must specify -s");
        
        if (raw_alpha1 >= sample_rate / 2.0 || raw_alpha2 >= sample_rate / 2.0)
            opterror("Corner frequency greater than Nyquist frequency = "
            + (float) (sample_rate / 2.0) + "Hz (half the sample rate).");
        
        if(!optsok && mode == CONSOLE)
            System.exit(1);
    }
    
    
    static void opterror(String msg) throws Exception {
        
        if (mode == CONSOLE) {
            System.out.print("MakeFilter: ");
            System.out.print(msg);
            System.out.println();
            optsok = false;
        } else {
            throw(new Exception("MakeFilter: " + msg));
        }
    }
    
    
    static void setdefaults() {
        if ((options & OPT_p) == 0)
            polemask = ~0; /* use all poles */
        if ((options & (OPT_bp | OPT_bs)) == 0)
            raw_alpha2 = raw_alpha1;
        raw_alpha1 /= sample_rate;
        //System.out.println(">>>>>>>>>>>>>>>>> DEBUG: sample_rate:" + sample_rate + ", raw_alpha1:" + raw_alpha1);
        raw_alpha2 /= sample_rate;
    }
    
    
    public static void compute_s() /* compute S-plane poles for prototype LP filter */ {
        splane.numpoles = 0;
        if ((options & OPT_be) != 0) { /* Bessel filter */
            int p = (order*order)/4; /* ptr into table */
            if ((order & 1) != 0)
                choosepole(bessel_poles[p++]);
            for (int i = 0; i < order/2; i++) {
                choosepole(bessel_poles[p]);
                choosepole(Cmplx.conjg(bessel_poles[p]));
                p++;
            }
        }
        if ((options & (OPT_bu | OPT_ch)) != 0) { /* Butterworth filter */
            for (int i = 0; i < 2*order; i++) {
                double theta = (order & 1) != 0 ? (i*PI) / order : ((i+0.5)*PI) / order;
                choosepole(Cmplx.exp(0.0, theta));
            }
        }
        if ((options & OPT_ch) != 0) { /* modify for Chebyshev (p. 136 DeFatta et al.) */
            if (chebrip >= 0.0) {
                System.out.print( "MakeFilter: Chebyshev ripple is " + chebrip + " dB; must be .lt. 0.0\n");
                if (mode == CONSOLE)
                    System.exit(1);
            }
            // double rip = pow(10.0, -chebrip / 10.0);
            double rip = Math.pow(10.0, -chebrip / 10.0);
            // double eps = sqrt(rip - 1.0);
            double eps = Math.sqrt(rip - 1.0);
            // 	double y = asinh(1.0 / eps) / (double) order;
            double y = Cmplx.asinh((new Cmplx(1.0 / eps, 0.0))).real() / (double) order;
            if (y <= 0.0) {
                System.out.print( "MakeFilter: bug: Chebyshev y=" + y + "; must be .gt. 0.0\n");
                if (mode == CONSOLE)
                    System.exit(1);
            }
            for (int i = 0; i < splane.numpoles; i++) {
                splane.poles[i].r *= XMath.sinh(y);
                splane.poles[i].i *= XMath.cosh(y);
            }
        }
    }
    
    
    static void choosepole(Cmplx z) {
        if (z.r < 0.0) {
            if ((polemask & 1) != 0)
                splane.poles[splane.numpoles++] = new Cmplx(z);
            polemask >>= 1;
        }
    }
    
    
    static void prewarp() { /* for bilinear transform, perform pre-warp on alpha values */
        if ((options & (OPT_w | OPT_z)) != 0) {
            warped_alpha1 = raw_alpha1;
            warped_alpha2 = raw_alpha2;
        }
        else {
            warped_alpha1 = Math.tan(PI * raw_alpha1) / PI;
            warped_alpha2 = Math.tan(PI * raw_alpha2) / PI;
        }
    }
    
    
    static void normalize()		/* called for trad, not for -Re or -Pi */ {
        double w1 = TWOPI * warped_alpha1;
        double w2 = TWOPI * warped_alpha2;
        /* transform prototype into appropriate filter type (lp/hp/bp/bs) */
        switch (options & (OPT_lp | OPT_hp | OPT_bp| OPT_bs)) {
            
            case OPT_lp: {
                for (int i = 0; i < splane.numpoles; i++)
                    splane.poles[i] = splane.poles[i].mul(w1);
                splane.numzeros = 0;
                break;
            }
            
            case OPT_hp: {
                int i;
                for (i=0; i < splane.numpoles; i++)
                    splane.poles[i] = (new Cmplx(w1, 0.0)).div(splane.poles[i]);
                for (i=0; i < splane.numpoles; i++)
                    splane.zeros[i] = new Cmplx(0.0, 0.0);	 /* also N zeros at (0,0) */
                splane.numzeros = splane.numpoles;
                break;
            }
            
            case OPT_bp: {
                /*
        case opt_bp:
          { double w0 = sqrt(w1*w2), bw = w2-w1; int i;
            for (i=0; i < splane.numpoles; i++)
              { complex hba = 0.5 * (splane.poles[i] * bw);
                complex temp = csqrt(1.0 - sqr(w0 / hba));
                splane.poles[i] = hba * (1.0 + temp);
                splane.poles[splane.numpoles+i] = hba * (1.0 - temp);
              }
            for (i=0; i < splane.numpoles; i++) splane.zeros[i] = 0.0;	 // also N zeros at (0,0)
            splane.numzeros = splane.numpoles;
            splane.numpoles *= 2;
            break;
          }
                 */
                //double w0 = sqrt(w1*w2), bw = w2-w1;
                double w0 = Math.sqrt(w1*w2);
                double bw = w2-w1;
                int i;
                for (i=0; i < splane.numpoles; i++) {
                    // complex hba = 0.5 * (splane.poles[i] * bw);
                    Cmplx hba = Cmplx.mul(splane.poles[i], bw).mul(0.5);
                    // complex temp = csqrt(1.0 - sqr(w0 / hba));
                    Cmplx temp = (new Cmplx(w0, 0.0)).div(hba);
                    temp = ((new Cmplx(1.0, 0.0)).sub(Cmplx.mul(temp, temp))).sqrt();
                    // splane.poles[i] = hba * (1.0 + temp);
                    splane.poles[i] = Cmplx.mul(hba, Cmplx.add(new Cmplx(1.0, 0.0), temp));
                    // splane.poles[splane.numpoles+i] = hba * (1.0 - temp);
                    splane.poles[splane.numpoles+i] = Cmplx.mul(hba, Cmplx.sub(new Cmplx(1.0, 0.0), temp));
                }
                for (i=0; i < splane.numpoles; i++)
                    splane.zeros[i] = new Cmplx(0.0, 0.0);	 /* also N zeros at (0,0) */
                splane.numzeros = splane.numpoles;
                splane.numpoles *= 2;
                break;
            }
            
            case OPT_bs: {
                /*
        case opt_bs:
          { double w0 = sqrt(w1*w2), bw = w2-w1; int i;
            for (i=0; i < splane.numpoles; i++)
              { complex hba = 0.5 * (bw / splane.poles[i]);
                complex temp = csqrt(1.0 - sqr(w0 / hba));
                splane.poles[i] = hba * (1.0 + temp);
                splane.poles[splane.numpoles+i] = hba * (1.0 - temp);
              }
            for (i=0; i < splane.numpoles; i++)	   // also 2N zeros at (0, +-w0)
              { splane.zeros[i] = complex(0.0, +w0);
                splane.zeros[splane.numpoles+i] = complex(0.0, -w0);
              }
            splane.numpoles *= 2;
            splane.numzeros = splane.numpoles;
            break;
          }
                 */
                //double w0 = sqrt(w1*w2), bw = w2-w1; int i;
                double w0 = Math.sqrt(w1*w2);
                double bw = w2-w1;
                int i;
                for (i=0; i < splane.numpoles; i++) {
                    // complex hba = 0.5 * (bw / splane.poles[i]);
                    Cmplx hba = Cmplx.div(new Cmplx(bw, 0.0), splane.poles[i]).mul(0.5);
                    // complex temp = csqrt(1.0 - sqr(w0 / hba));
                    Cmplx temp = (new Cmplx(w0, 0.0)).div(hba);
                    temp = ((new Cmplx(1.0, 0.0)).sub(Cmplx.mul(temp, temp))).sqrt();
                    // splane.poles[i] = hba * (1.0 + temp);
                    splane.poles[i] = Cmplx.mul(hba, Cmplx.add(new Cmplx(1.0, 0.0), temp));
                    // splane.poles[splane.numpoles+i] = hba * (1.0 - temp);
                    splane.poles[splane.numpoles+i] = Cmplx.mul(hba, Cmplx.sub(new Cmplx(1.0, 0.0), temp));
                }
                for (i=0; i < splane.numpoles; i++)	   /* also 2N zeros at (0, +-w0) */ {
                    splane.zeros[i] = new Cmplx(0.0, w0);
                    splane.zeros[splane.numpoles+i] = new Cmplx(0.0, -w0);
                }
                splane.numpoles *= 2;
                splane.numzeros = splane.numpoles;
                break;
            }
        }
    }
    
    
    public static void compute_z_blt() /* given S-plane poles & zeros, compute Z-plane poles & zeros, by bilinear transform */
    { int i;
      zplane.numpoles = splane.numpoles;
      zplane.numzeros = splane.numzeros;
      for (i=0; i < zplane.numpoles; i++)
          zplane.poles[i] = blt(splane.poles[i]);
      for (i=0; i < zplane.numzeros; i++)
          zplane.zeros[i] = blt(splane.zeros[i]);
      while (zplane.numzeros < zplane.numpoles)
          zplane.zeros[zplane.numzeros++] = new Cmplx(-1.0, 0.0);
    }
    
    
    static Cmplx blt(Cmplx pz) {
        // return (2.0 + pz) / (2.0 - pz);
        return ((new Cmplx(2.0, 0.0)).add(pz).div((new Cmplx(2.0, 0.0)).sub(pz)));
        // WARNING: above is only valid for 1 sec sample interval!
    }
    
    
    public static void compute_z_mzt() /* given S-plane poles & zeros, compute Z-plane poles & zeros, by matched z-transform */
    { int i;
      zplane.numpoles = splane.numpoles;
      zplane.numzeros = splane.numzeros;
      for (i=0; i < zplane.numpoles; i++)
          zplane.poles[i] = Cmplx.exp(splane.poles[i]);
      for (i=0; i < zplane.numzeros; i++)
          zplane.zeros[i] = Cmplx.exp(splane.zeros[i]);
    }
    
    
    public static void compute_notch() { /* compute Z-plane pole & zero positions for bandstop resonator (notch filter) */
        compute_bpres();		/* iterate to place poles */
        double theta = TWOPI * raw_alpha1;
        Cmplx zz = Cmplx.exp(0.0, theta);	/* place zeros exactly */
        zplane.zeros[0] = new Cmplx(zz);
        zplane.zeros[1] = Cmplx.conjg(zz);
    }
    
    
    public static void compute_apres() { /* compute Z-plane pole & zero positions for allpass resonator */
        compute_bpres();		/* iterate to place poles */
        zplane.zeros[0] = reflect(zplane.poles[0]);
        zplane.zeros[1] = reflect(zplane.poles[1]);
    }
    
    
    static Cmplx reflect(Cmplx z) {
        //double r = XMath.hypot(z);
        //return z / sqr(r);
        double r = z.hypot();
        return Cmplx.div(z, r * r);
        
    }
    
    
    public static void compute_bpres() { /* compute Z-plane pole & zero positions for bandpass resonator */
        zplane.numpoles = zplane.numzeros = 2;
        zplane.zeros[0] = new Cmplx(1.0, 0.0);
        zplane.zeros[1] = new Cmplx(-1.0, 0.0);
        double theta = TWOPI * raw_alpha1; /* where we want the peak to be */
        if (infq) { /* oscillator */
            Cmplx zp = Cmplx.exp(0.0, theta);
            zplane.poles[0] = new Cmplx(zp);
            zplane.poles[1] = Cmplx.conjg(zp);
        }
        else { /* must iterate to find exact pole positions */
            Cmplx[] topcoeffs = new Cmplx[PoleZeroRep.MAXPZ+1];
            expand(zplane.zeros, zplane.numzeros, topcoeffs);
            double r = Math.exp(-theta / (2.0 * qfactor));
            double thm = theta, th1 = 0.0, th2 = PI;
            boolean cvg = false;
            for (int i=0; i < 50 && !cvg; i++) {
                Cmplx zp = Cmplx.exp(0.0, thm).mul(r);
                zplane.poles[0] = new Cmplx(zp);
                zplane.poles[1] = Cmplx.conjg(zp);
                Cmplx[] botcoeffs = new Cmplx[PoleZeroRep.MAXPZ+1];
                expand(zplane.poles, zplane.numpoles, botcoeffs);
                Cmplx g = evaluate(topcoeffs, zplane.numzeros, botcoeffs, zplane.numpoles, Cmplx.exp(0.0, theta));
                double phi = g.i / g.r; /* approx to atan2 */
                if (phi > 0.0) th2 = thm; else th1 = thm;
                if (Math.abs(phi) < EPS)
                    cvg = true;
                thm = 0.5 * (th1+th2);
            }
            if (!cvg)
                System.out.print( "MakeFilter: warning: failed to converge\n");
        }
    }
    
    
    static void add_extra_zero() {
        if (zplane.numzeros+2 > PoleZeroRep.MAXPZ) {
            System.out.print( "MakeFilter: too many zeros; can't do -Z\n");
            if (mode == CONSOLE)
                System.exit(1);
        }
        double theta = TWOPI * raw_alphaz;
        Cmplx zz = Cmplx.exp(0.0, theta);
        zplane.zeros[zplane.numzeros++] = new Cmplx(zz);
        zplane.zeros[zplane.numzeros++] = Cmplx.conjg(zz);
        while (zplane.numpoles < zplane.numzeros)
            zplane.poles[zplane.numpoles++] = new Cmplx(0.0, 0.0);	 /* ensure causality */
    }
    
    
    static void expandpoly() /* given Z-plane poles & zeros, compute top & bot polynomials in Z, and then recurrence relation */ {
        Cmplx[] topcoeffs = new Cmplx[PoleZeroRep.MAXPZ+1];
        Cmplx[] botcoeffs = new Cmplx[PoleZeroRep.MAXPZ+1];
        int i;
        expand(zplane.zeros, zplane.numzeros, topcoeffs);
        expand(zplane.poles, zplane.numpoles, botcoeffs);
        dc_gain = evaluate(topcoeffs, zplane.numzeros, botcoeffs, zplane.numpoles, new Cmplx(1.0, 0.0));
        double theta = TWOPI * 0.5 * (raw_alpha1 + raw_alpha2); /* "jwT" for centre freq. */
        fc_gain = evaluate(topcoeffs, zplane.numzeros, botcoeffs, zplane.numpoles, Cmplx.exp(0.0, theta));
        hf_gain = evaluate(topcoeffs, zplane.numzeros, botcoeffs, zplane.numpoles, new Cmplx(-1.0, 0.0));
        for (i = 0; i <= zplane.numzeros; i++)
            xcoeffs[i] = +(topcoeffs[i].r / botcoeffs[zplane.numpoles].r);
        for (i = 0; i <= zplane.numpoles; i++)
            ycoeffs[i] = -(botcoeffs[i].r / botcoeffs[zplane.numpoles].r);
    }
    
    
    static void expand(Cmplx pz[], int npz, Cmplx coeffs[]) { /* compute product of poles or zeros as a polynomial of z */
        int i;
        coeffs[0] = new Cmplx(1.0, 0.0);
        for (i=0; i < npz; i++)
            coeffs[i+1] = new Cmplx(0.0, 0.0);
        for (i=0; i < npz; i++)
            multin(pz[i], npz, coeffs);
        /* check computed coeffs of z^k are all real */
        for (i=0; i < npz+1; i++) {
            if (Math.abs(coeffs[i].i) > EPS) {
                System.out.print( "MakeFilter: coeff of z^" + i + " is not real; poles/zeros are not complex conjugates\n");
                if (mode == CONSOLE)
                    System.exit(1);
            }
        }
    }
    
    
    static void multin(Cmplx w, int npz, Cmplx coeffs[]) { /* multiply factor (z-w) into coeffs */
        Cmplx nw = Cmplx.mul(w, -1.0);
        for (int i = npz; i >= 1; i--)
            coeffs[i] = Cmplx.mul(nw, coeffs[i]).add(coeffs[i-1]);
        coeffs[0] = Cmplx.mul(nw, coeffs[0]);
    }
    
    
    public static void printresults(String[] argv) {
        if ((options & OPT_l) != 0) { /* just list parameters */
            printcmdline(argv);
            Cmplx gain = ((options & OPT_pi) != 0) ? hf_gain :
                ((options & OPT_lp) != 0) ? dc_gain :
                    ((options & OPT_hp) != 0) ? hf_gain :
                        ((options & (OPT_bp | OPT_ap))) != 0 ? fc_gain :
                            ((options & OPT_bs) != 0) ? Cmplx.mul(dc_gain, hf_gain).sqrt() : new Cmplx(1.0, 0.0);
                            System.out.println("G  = " + gain.hypot());
                            printcoeffs("NZ", zplane.numzeros, xcoeffs);
                            printcoeffs("NP", zplane.numpoles, ycoeffs);
        }
        else {
            System.out.print("Command line: ");
            printcmdline(argv);
            printfilter(argv);
        }
    }
    
    
    static void printcmdline(String[] argv) {
        System.out.print(getcmdline(argv));
        System.out.println();
    }
    
    
    static String getcmdline(String[] argv) {
        String cmdline = "";
        for (int k = 0; k < argv.length; k++)
            cmdline += argv[k] + " ";
        return(cmdline);
    }
    
    static void printcoeffs(String pz, int npz, double coeffs[]) {
        System.out.println(pz + " = " + npz);
        for (int i = 0; i <= npz; i++)
            System.out.println("" + coeffs[i]);
    }
    
    static void printfilter(String[] argv) {
        System.out.println("raw alpha1    = " + raw_alpha1);
        System.out.println("raw alpha2    = " + raw_alpha2);
        if ((options & (OPT_re | OPT_w | OPT_z)) == 0) {
            System.out.println("warped alpha1 = " + warped_alpha1);
            System.out.println("warped alpha2 = " + warped_alpha2);
        }
        printgain("dc    ", dc_gain);
        printgain("centre", fc_gain);
        printgain("hf    ", hf_gain);
        System.out.println();
        if ((options & OPT_re) == 0)
            printrat_s();
        printrat_z();
        printrecurrence();
        printrat_s_paz(System.out, argv);
        try {
            String fileName = getcmdline(argv).replace(" ", "_").replace("-", "_") + ".paz";
            PrintStream ps = new PrintStream(fileName);
            printrat_s_paz(ps, argv);
            ps.close();
        } catch (Exception e) {
            System.out.print("Error: writing S-plane poles and zeros in PAZ format:" + e);
        }
    }
    
    static void printgain(String str, Cmplx gain) {
        double r = gain.hypot();
        System.out.print("gain at " + str + ":   mag = " + r);
        if (r > EPS)
            System.out.print("   phase = " + (gain.phs() / PI) + " pi");
        System.out.println();
    }
    
    static void printrat_s_paz(PrintStream printStream, String[] argv)	/* print S-plane poles and zeros in PAZ format*/ {
        printStream.println();
        printStream.println("S-plane poles and zeros in PAZ format:");
        printStream.println();
        printStream.println(getcmdline(argv));
        printStream.println("ZEROS  " + splane.numzeros);
        printpz_paz(printStream, splane.zeros, splane.numzeros);
        printStream.println("POLES  " + splane.numpoles);
        printpz_paz(printStream, splane.poles, splane.numpoles);
        printStream.println("CONSTANT  " + fc_gain.hypot());
    }
    
    
    static void printpz_paz(PrintStream printStream, Cmplx[] pzvec, int num) {
        
        for (int n = 0; n < num; n++) {
            printStream.print('\t');
            printStream.print("" + pzvec[n].r + "  " + pzvec[n].i);
            printStream.println();
        }
    }
    
    static void printrat_s()	/* print S-plane poles and zeros */ {
        System.out.print("S-plane zeros:\n");
        printpz(splane.zeros, splane.numzeros);
        System.out.println();
        System.out.print("S-plane poles:\n");
        printpz(splane.poles, splane.numpoles);
        System.out.println();
    }
    
    static void printrat_z()	/* print Z-plane poles and zeros */ {
        System.out.print("Z-plane zeros:\n");
        printpz(zplane.zeros, zplane.numzeros);
        System.out.println();
        System.out.print("Z-plane poles:\n");
        printpz(zplane.poles, zplane.numpoles);
        System.out.println();
    }
    
    static void printpz(Cmplx[] pzvec, int num) {
        int n1 = 0;
        while (n1 < num) {
            System.out.print('\t');
            prcomplex(pzvec[n1]);
            int n2 = n1+1;
            /*
            while (n2 < num && pzvec[n2] == pzvec[n1])
                n2++;
            if (n2-n1 > 1)
                System.out.print("\t" + (n2-n1) + " times");
             **/
            System.out.println();
            n1 = n2;
        }
    }
    
    static void printrecurrence() /* given (real) Z-plane poles & zeros, compute & print recurrence relation */ {
        System.out.print("Recurrence relation:\n");
        System.out.print("y[n] = ");
        int i;
        for (i = 0; i < zplane.numzeros+1; i++) {
            if (i > 0) System.out.print("     + ");
            double x = xcoeffs[i];
            // double f = fmod(fabs(x), 1.0);
            double f = Math.abs(x) % 1.0;
            //char *fmt = (f < EPS || f > 1.0-EPS) ? "%3g" : "%14.10f";
            System.out.print("(" + x);
            System.out.print(" * x[n-" + (zplane.numzeros-i) + "])\n");
        }
        System.out.println();
        for (i = 0; i < zplane.numpoles; i++) {
            System.out.print("     + (" + ycoeffs[i] + " * y[n-" + (zplane.numpoles-i) + "])\n");
        }
        System.out.println();
    }
    
    static void prcomplex(Cmplx z) {
        System.out.print("" + z.r + " + j " + z.i);
    }
    
    
    
    // from complex.C
    
    public static Cmplx evaluate(Cmplx topco[], int nz, Cmplx botco[], int np, Cmplx z) { /* evaluate response, substituting for z */
        return eval(topco, nz, z).div(eval(botco, np, z));
    }
    
    public static Cmplx eval(Cmplx coeffs[], int npz, Cmplx z) { /* evaluate polynomial in z, substituting for z */
        Cmplx sum = new Cmplx(0.0, 0.0);
        for (int i = npz; i >= 0; i--)
            sum = sum.mul(z).add(coeffs[i]);
        return sum;
    }
    
    
    
    // AJL helper methods
    
    
    /** returns gain */
    
    public static double getGain() {
        
        if ((options & (OPT_lp)) != 0)
            return (dc_gain.hypot());
        
        if ((options & (OPT_hp)) != 0)
            return (hf_gain.hypot());
        
        return (fc_gain.hypot());
        
    }
    
    
    /** returns gain */
    
    public static double getGain(String type) {
        
        if(type.equalsIgnoreCase("dc"))
            return (dc_gain.hypot());
        if(type.equalsIgnoreCase("fc"))
            return (fc_gain.hypot());
        if(type.equalsIgnoreCase("hf"))
            return (hf_gain.hypot());
        
        return(0.0);
        
    }
    
    
    /** returns S poles */
    
    public static Cmplx[] getSPoles() {
        
        Cmplx[] poles = new Cmplx[splane.numpoles];
        System.arraycopy(splane.poles, 0, poles, 0, splane.numpoles);
        
        return(poles);
        
    }
    
    
    /** returns S zeros */
    
    public static Cmplx[] getSZeros() {
        
        Cmplx[] zeros = new Cmplx[splane.numzeros];
        System.arraycopy(splane.zeros, 0, zeros, 0, splane.numzeros);
        
        return(zeros);
        
    }
    
    
    protected static String[] tokensSg2k = new String[] {
        "bandpass", "highpass", "lowpass", "bandstop",
        "bessel", "butterworth", "chebyshev",
        "n", "co"
    };
    
    protected static String[] tokensSg2kShort = new String[] {
        "bp", "hp", "lp", "bs",
        "be", "bu", "ch",
        "n", "co",
    };
    
    protected static String[] tokensMakeFilter = new String[] {
        "-Bp", "-Hp", "-Lp", "-Bs",
        "-Be", "-Bu", "-Ch",
        "-o", "-c"
    };
    
    
    /** parse SG2K command string and convert to MakeFilter command tokens as String array
     *
     * spaces in SG2K command string must be replaced by "_"
     *
     */
    
    public static String[] sg2kToMakeFilterCommandString(String sg2kCmd, double sampleRate) throws Exception {
        
        // examples (spaces must be replaced by "%"m "^" or "_")
        // bp bu n 4 co 0.5 2.0
        
        String tokens[] = null;
        if (sg2kCmd.indexOf('%') >= 0)
            tokens = sg2kCmd.split("%");
        else if (sg2kCmd.indexOf('^') >= 0)
            tokens = sg2kCmd.split("^");
        else
            tokens = sg2kCmd.split("_");
        
        String mftokens[] = new String[tokens.length + 2];
        
        boolean DEBUG = true;
        
        int i = 0;
        while (i < tokens.length) {
            
            int j = 0;
            while (j < tokensSg2k.length) {
                if (tokens[i].equalsIgnoreCase(tokensSg2kShort[j]) || tokens[i].equalsIgnoreCase(tokensSg2k[j])) {
                    mftokens[i] = tokensMakeFilter[j];
                    break;
                }
                j++;
            }
            if (j == tokensSg2k.length) {
                throw(new Exception("MakeFilter: Unrecognized or unsupported SG2K command key: " + tokens[i]));
            }
            
            
            // treat special cases
            if (mftokens[i].equals("-o")) {   // -o <order>
                i++;
                mftokens[i] = tokens[i];
            }
            if (mftokens[i].equals("-c")) {   // -c <corner1> [ <corner2> ]
                i++;
                mftokens[i] = tokens[i];
                i++;
                try {
                    mftokens[i] = "" + Double.parseDouble(tokens[i]);
                } catch (Exception e) { // may be only one corner frequency specified
                    i--;
                }
            }
            else if (mftokens[i].equals("-Ch")) {   // Chebyshev (r = dB ripple)
                i++;
                mftokens[i] = tokens[i];
            }
            
            i++;
            
        }
        
        // set sample rate tokens -s <sample_rate>
        mftokens[i] = "-s";
        i++;
        mftokens[i] = "" + sampleRate;
        
        /* NOTE: do not need following to match SG2K bp filter number of poles
        // For lowpass and highpass, order is the number of poles.
        // For bandpass and bandstop, the number of poles is twice the order.)
        int factor = 1;
        for (int n = 0; n < mftokens.length; n++) {
            if (mftokens[n].equals("-Bp") || mftokens[n].equals("-Bs"))
                factor = 2;
        }
        if (factor > 1) {
            for (int n = 0; n < mftokens.length; n++) {
                if (mftokens[n].equals("-o")) {
                    mftokens[n + 1] = "" + (Integer.parseInt(mftokens[n + 1])) / factor;
                    break;
                }
            }
        }
         */
        
        if (DEBUG) {
            String makeFilterCmd = "";
            for (int n = 0; n < mftokens.length; n++) {
                makeFilterCmd += mftokens[n] + " ";
            }
            System.out.println("sg2kToMakeFilterCommandString: " + makeFilterCmd);
        }
        
        return(mftokens);
        
    }
    
    /*
        static void usage() {
        System.out.print( "MakeFilter V." + VERSION + " from <fisher@minster.york.ac.uk>, Java version www.alomax.net\n");
        System.out.print( "Interactive web version at: http://www-users.cs.york.ac.uk/~fisher/mkfilter/trad.html\n");
        System.out.print( "Usage: mkfilter [-Be | -Bu | -Ch <r> | -Pi] [-Lp | -Hp | -Bp | -Bs] [-p <n1> <n2> ...] [-{lwz}] "
        + "[-Z <alphaz>] " + "-o <order> -c <corner1> [ <corner2> ] -s <sample_rate>\n");
        System.out.print( "       mkfilter -Re <q> [-Bp | -Bs | -Ap] [-l] -c <corner> -s <sample_rate>\n\n");
        System.out.print( "  -Be, Bu             = Bessel, Butterworth\n");
        System.out.print( "  -Ch <r>             = Chebyshev (r = dB ripple)\n");
        System.out.print( "  -Pi                 = Proportional-Integral\n");
        System.out.print( "  -Re <q>             = 2-pole resonator (q = Q-factor)\n");
        System.out.print( "  -Lp, Hp, Bp, Bs, Ap = lowpass, highpass, bandpass, bandstop, allpass\n");
        System.out.print( "  -p                  = use listed poles only (ni = 0 .. order-1)\n");
        System.out.print( "  -l                  = just list <order> parameters\n");
        System.out.print( "  -w                  = don't pre-warp frequencies\n");
        System.out.print( "  -z                  = use matched z-transform\n");
        System.out.print( "  -Z                  = additional z-plane zero\n");
        System.out.print( "  order = 1.." + MAXORDER + ";  alpha = f(corner)/f(sample)\n\n");
        System.exit(1);
    }
     */
    
    
    
}
