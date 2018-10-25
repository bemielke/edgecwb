/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 2009 Anthony Lomax <anthony@alomax.net www.alomax.net>
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


package net.alomax.timedom;


/** FeatureFinder */

/**
 * An abstract class representing processes that can find a feature in a time series.
 *
 * @author  Anthony Lomax
 */

public interface FeatureFinder {
    
   /** Method to find indices in time series to a feature in the time series
    *
    * @param featureName the name of the feature to find
    * @param featureOffset offset in seconds to add to feature index positions
    * @return indices to all samples in the time series with specifeid fieature name
    */

    public Feature[] getOffsetsToFeature(String featureName, double featureOffset);

    
}	// End

