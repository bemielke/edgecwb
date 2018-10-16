/*
 * Copyright 2010, United States Geological Survey or
 * third-party contributors as indicated by the @author tags.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package gov.usgs.anss.net;
import java.util.Comparator;
/**
 *
 * @author davidketchum
 */
   class HoldingsBySize implements Comparator {
    @Override
    public int compare(Object o1, Object o2) {
      if(((Holding) o1).getLengthInMillis() > ((Holding)o2).getLengthInMillis()) return -1;
      if(((Holding) o1).getLengthInMillis() == ((Holding) o2).getLengthInMillis()) return 0;
      return 1;
    }
    public boolean equals(Object o1) {return false;}
  }