/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 2015 Anthony Lomax <anthony@alomax.net www.alomax.net>
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
package net.alomax.timedom.fp6;

import net.alomax.timedom.TimeDomainException;
import net.alomax.timedom.TimeDomainText;

/**
 *
 * @author anthony
 */
public class FilterPicker6_BandParameters {

    /**
     * band period/freq display types
     */
    public static enum DisplayMode {

        /**
         * Read and display period as period "s"
         */
        DISPLAY_MODE_PERIOD,
        /**
         * Read and display period as Frequency "Hz"
         */
        DISPLAY_MODE_FREQ
    };
    /**
     * band period/freq display type
     */
    public DisplayMode displayMode = DisplayMode.DISPLAY_MODE_PERIOD;

    private static final double PERIOD_MIN = Double.MIN_VALUE;
    private static final double PERIOD_MAX = Double.MAX_VALUE;
    private static final double THRES_SCALE_FACT_MIN = Double.MIN_VALUE;
    private static final double THRES_SCALE_FACT_MAX = Double.MAX_VALUE;
    /**
     * Filter band center period.
     * The ratio of each band period band to previous shorter band period must be approximately constant.
     */
    public double period = -1.0;
    /**
     * band threshold scaling factor:
     * trigger thresholds for band are band thresholdScaleFactor multiplied by picker threshold1 or  threshold2;
     * thresholdScaleFactor for all bands are normalized so maximum thresholdScaleFactor = 1.0
     */
    public double thresholdScaleFactor = 1.0;
    @Override
    public String toString() {return "p="+period+"s "+1./period+" Hz threshScale="+thresholdScaleFactor;}
    public FilterPicker6_BandParameters(double period, double thresholdScaleFactor, DisplayMode displayMode) {

        this.period = period;
        this.thresholdScaleFactor = thresholdScaleFactor;
        this.displayMode = displayMode;

    }

    /**
     * check parameters which are independent of sample data
     *
     * @throws TimeDomainException
     */
    protected void checkParams() throws TimeDomainException {

        String errMessage = "";
        int badSettings = 0;

        if (period < PERIOD_MIN || period > PERIOD_MAX) {
            errMessage += ": " + TimeDomainText.invalid_period_value;
            badSettings++;
        }

        if (thresholdScaleFactor < THRES_SCALE_FACT_MIN || thresholdScaleFactor > THRES_SCALE_FACT_MAX) {
            errMessage += ": " + TimeDomainText.invalid_threshold1_scale_factor_value;
            badSettings++;
        }

        if (badSettings > 0) {
            throw new TimeDomainException(errMessage + ".");
        }
    }

    /**
     * check parameters which depend on sample data
     *
     * @param deltaTime
     * @throws TimeDomainException
     */
    protected void checkParamsSampleDependent(double deltaTime) throws TimeDomainException {

        if ((deltaTime - period) > (deltaTime / 10.0)) {
            throw new TimeDomainException(
                    TimeDomainText.filter_period_less_than_sampling_interval + ": " + period + "<" + (float) deltaTime);
        }

    }

}
