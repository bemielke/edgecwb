/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

/**
 * This class implements keys for the indexFile which are basically the Seedname and julian date. It
 * implements Comparable to support sorted lists.
 *
 * @author davidketchum
 */
public final class IndexKey implements Comparable {

  private final String seedName;
  private final int julian;

  /**
   * Creates a new instance of IndexKey
   *
   * @param n The 12 character seedname
   * @param j The julian date
   */
  public IndexKey(String n, int j) {
    seedName = n;
    julian = j;
  }

  /**
   * return the seedname portion of the key
   *
   * @return the seedname portion of the key
   */
  public String getSeedName() {
    return seedName;
  }

  /**
   * return the julian day portion of the key
   *
   * @return the julian day portion of the key
   */
  public int getJulian() {
    return julian;
  }

  /**
   * compares two index block keys for ordering. The are compared seedname first and then julian
   * day. 0 both match exactly.
   *
   * @param o The other object to compare to
   * @return -1 if this is < param, 0 if equal, 1 if this > param
   */
  @Override
  public int compareTo(Object o) {
    IndexKey idx = (IndexKey) o;
    int idiff = seedName.compareTo(idx.getSeedName());
    if (idiff != 0) {
      return idiff;
    }

    //Break the tie with julian dates
    if (julian < idx.getJulian()) {
      return -1;
    } else if (julian > idx.getJulian()) {
      return 1;
    }
    return 0;               // They are equal in every way
  }

}
