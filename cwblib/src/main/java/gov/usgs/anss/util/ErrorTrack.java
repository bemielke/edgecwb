/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

/**
 * This class helps with creating a "error" variable which is used in "chk***()" routines from
 * FUtil. This class tracks how many errors are found between reset() and errorCount(). The FUtil
 * routines (or the any user chkForm) records an error and appends some text using appendText(). At
 * the end of the error checking routine (usually chkForm in the GUI) the ErrorTrack is used to see
 * if any errors where found with isSet() and the text of all the errors obtained for display by
 * getText(). Typical use would be :
 * <pre>
 * ErrorTrack err = new ErrorTrack();
 * err.reset();
 * d = FUtil.chkDouble(textField1, err);
 * dt = FUtil.chkDate(textField2, err);
 * if(err.isSet()) {  Take action saying the form did not check out.
 * </pre> Note : FUtil is designed to change the color of invalid fields to red when the text fields
 * cannot convert.
 *
 * @author D. Ketchum
 */
public final class ErrorTrack {

  private int err;                  // Count of the errors
  private final StringBuilder text;        // Concatenation of error messages
  private boolean last;             // each call to set revises this so you can see "last error"

  /**
   * default constructor is the only constructor.
   */
  public ErrorTrack() {
    err = 0;
    text = new StringBuilder(256);       // Initial error message length
  }

  /**
   * Increment the error count indicating an error was detected.
   */
  public void set() {
    err++;
    last = true;
  }

  /**
   * if boolean argument is true, increment the error flag and set last to boolean
   *
   * @param a A boolean indicating whether an error occurred
   */
  public void set(boolean a) {
    if (a) {
      err++;
    }
    last = a;
  }

  /**
   * Check if an error has been noted since the last reset().
   *
   * @return true if err > 0, error is a count of # of errors so far.
   */
  public boolean isSet() {
    return err != 0;
  }

  /**
   * get whether the last call caused an error - used to perform actions based on last call to a
   * FUtil.chk***() routine
   *
   * @return the value of the last error call.
   */
  public boolean lastWasError() {
    return last;
  }

  /**
   * Return the error count since last reset.
   *
   * @return the error count
   */
  public int errorCount() {
    return err;
  }

  /**
   * reset the error count to zero and clear the error text buffer
   */
  public void reset() {
    err = 0;
    if (text.length() > 0) {
      text.delete(0, text.length());
    }
  }

  /**
   * add the argument text to the internal error text buffer separated with a hyphen.
   *
   * @param a The text to add to the error buffer
   */
  public void appendText(String a) {
    text.append(a);
    text.append(" - ");
  }

  /**
   * get the text of all errors since the last reset()
   *
   * @return The error text concatenated
   */
  public String getText() {
    return text.toString();
  }
}
