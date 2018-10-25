/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.metadatagui;

import gov.usgs.anss.util.Util;
import java.util.Objects;

/**
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class UtilGUI {

    static final StringBuilder nullSb = new StringBuilder(1000);

    private static boolean nullSbInUse = false;

    private static StringBuilder startNullSb() {
        nullSb.delete(0, nullSb.length());
        nullSbInUse = true;
        return nullSb;
    }

    private static void endNullSb() {
        nullSbInUse = false;
    }

    /**
     * Compare two double and return true if they are within the given tolerance
     *
     * @param one A double
     * @param two The other double
     * @param tolerance the allowed ratio of the two doubles
     *
     */
    public static boolean compareDoubles(double one, double two, double tolerance, String description, StringBuilder sb) {
        double difference;
        if (one == 0.0 && two == 0.0) {
            difference = 0.;
        } else if (two == 0.0) {
            difference = 100.;       // its really infinite!
        } else {
            difference = Math.abs(1 - (one / two));
        }

        boolean match = false;
        if (difference < tolerance) {
            match = true;
        }
        if (!match) {
            synchronized (nullSb) {
                if (sb == null) {
                    sb = startNullSb();
                }
                sb.append(description).append("* OutOfTolerance * ").append(one).append(" != ").append(two).
                        append(" ,Ratio= ").append(difference).append(", Tolerance=").append(tolerance).append("\n");
                if (nullSbInUse) {
                    Util.prt(sb);
                    endNullSb();
                }
            }
        }
        return match;
    }

    public static boolean diffDoubles(double one, double two, double tolerance, String description, StringBuilder sb) {
        boolean match = true;
        double difference = Math.abs(one - two);
        if (difference > tolerance) {
            match = false;
            synchronized (nullSb) {
                if (sb == null) {
                    sb = startNullSb();
                }
                sb.append(description).append("* OutOfTolerance * ").append(one).append(" != ").append(two).
                        append(" ,Diff= ").append(difference).append(", Tolerance=").append(tolerance).append("\n");
                if (nullSbInUse) {
                    Util.prt(sb);
                    endNullSb();
                }
            }
        }
        return match;
    }

    public static boolean diffStrings(String one, String two, boolean strict, String description, StringBuilder sb) {
        boolean match;
        match = Objects.equals(one.toUpperCase().trim(), two.toUpperCase().trim());
        if (!match) {
            synchronized (nullSb) {
                if (sb == null) {
                    sb = startNullSb();
                }
                sb.append(description).append("* OutOfTolerance * ").append(one).append(" != ").append(two).append("\n");
                if (nullSbInUse) {
                    Util.prt(sb);
                    endNullSb();
                }
                if (strict) {
                    match = false;
                }
            }
        }
        return match;
    }

    public static boolean diffInts(int one, int two, int tolerance, String description, StringBuilder sb) {
        boolean match = true;
        double difference = Math.abs(one - two);
        if (difference > tolerance) {
            match = false;
            synchronized (nullSb) {
                if (sb == null) {
                    sb = startNullSb();
                }
                sb.append(description).append("* OutOfTolerance *").append(one).append(" != ").append(two).
                        append(" ,Diff= ").append(difference).append(", Tolerance=").append(tolerance).append("\n");
                if (nullSbInUse) {
                    Util.prt(sb);
                    endNullSb();
                }
            }
        }
        return match;
    }
}
