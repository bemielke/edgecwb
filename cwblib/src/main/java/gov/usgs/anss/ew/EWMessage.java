/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.ew;

import java.nio.ByteBuffer;

/**
 * Mostly static definitions for Earthworm
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class EWMessage {

  /**
   * wildcard value - DO NOT CHANGE!!!
   */
  public static final int TYPE_WILDCARD = 0;
  /**
   * multiplexed waveforms from DOS adsend
   */
  public static final int TYPE_ADBUF = 1;
  /**
   * error message
   */
  public static final int TYPE_ERROR = 2;
  /**
   * heartbeat message
   */
  public static final int TYPE_HEARTBEAT = 3;
  /**
   * compressed waveforms from compress_UA, with SCNL
   */
  public static final int TYPE_TRACE2_COMP_UA = 4; // compressed waveform with SCNL
  /**
   * single-channel waveforms from nanometrics
   */
  public static final int TYPE_NANOBUF = 5;
  /**
   * acknowledgment sent by import to export
   */
  public static final int TYPE_ACK = 6;
  /**
   * P-wave arrival time (with location code)
   */
  public static final int TYPE_PICK_SCNL = 8;
  /**
   * coda info (plus station/loc code) from pick_ew
   */
  public static final int TYPE_CODA_SCNL = 9;
  /**
   * P-wave arrival time (with 4 digit year) from pick_ew
   */
  public static final int TYPE_PICK2K = 10;
  /**
   * coda info (plus station code) from pick_ew
   */
  public static final int TYPE_CODA2K = 11;
  /**
   * P-wave arrival time from picker & pick_ew
   */
  public static final int TYPE_PICK2 = 12;
  /**
   * coda info from picker & pick_ew
   */
  public static final int TYPE_CODA2 = 13;
  /**
   * hyp2000 (Y2K hypoinverse) event archive msg from eqproc/eqprelim
   */
  public static final int TYPE_HYP2000ARC = 14;
  /**
   * hypo71-format hypocenter summary msg (with 4-digit year) from eqproc/eqprelim
   */
  public static final int TYPE_H71SUM2K = 15;
  /**
   * hypoinverse event archive msg from eqproc/eqprelim
   */
  public static final int TYPE_HINVARC = 17;
  /**
   * hypo71-format summary msg from eqproc/eqprelim
   */
  public static final int TYPE_H71SUM = 18;
  /**
   * single-channel waveforms with channels identified with sta,comp,net,loc (SCNL)
   */
  public static final int TYPE_TRACEBUF2 = 19;
  /**
   * single-channel waveforms from NT adsend, getdst2, nano2trace, rcv_ew, import_ida...
   */
  public static final int TYPE_TRACEBUF = 20;
  /**
   * single-channel long-period trigger from lptrig & evanstrig
   */
  public static final int TYPE_LPTRIG = 21;
  /**
   * cubic-format summary msg from cubic_msg
   */
  public static final int TYPE_CUBIC = 22;
  /**
   * single-channel trigger from carlstatrig
   */
  public static final int TYPE_CARLSTATRIG = 23;
  /**
   * trigger-list msg (used by tracesave modules) from arc2trig, trg_assoc, carlsubtrig
   */
  public static final int TYPE_TRIGLIST = 24;
  /**
   * trigger-list msg (with 4-digit year) used by tracesave modules from arc2trig, trg_assoc,
   * carlsubtrig
   */
  public static final int TYPE_TRIGLIST2K = 25;
  /**
   * compressed waveforms from compress_UA
   */
  public static final int TYPE_TRACE_COMP_UA = 26;
  /**
   * single-instrument peak accel, peak velocity, peak displacement, spectral acceleration
   */
  public static final int TYPE_STRONGMOTION = 27;
  /**
   * event magnitude: summary plus station info
   */
  public static final int TYPE_MAGNITUDE = 28;
  /**
   * event strong motion parameters
   */
  public static final int TYPE_STRONGMOTIONII = 29;
  /**
   * Global location message used by NEIC & localmag
   */
  public static final int TYPE_LOC_GLOBAL = 30;
  /**
   * single-channel long-period trigger from lptrig & evanstrig (with location code)
   */
  public static final int TYPE_LPTRIG_SCNL = 31;
  /**
   * single-channel trigger from carlstatrig (with loc)
   */
  public static final int TYPE_CARLSTATRIG_SCNL = 32;
  /**
   * trigger-list msg (with 4-digit year) used by tracesave modules from arc2trig, trg_assoc,
   * carlsubtrig (with location code)
   */
  public static final int TYPE_TRIGLIST_SCNL = 33;
  /**
   * time-domain reduced-rate amplitude summary produced by CISN RAD software & ada2ring
   */
  public static final int TYPE_TD_AMP = 34;
  /**
   * Geomag ObsRio message
   */
  public static final int TYPE_GEOMAG_DATA = -96;   // Unsigned 160
  public static final int TYPE_GEOMAG_DATA2 = -95;   // Unsigned 161 - non magnetic data - ignore
  public static final String[] typeString = {
    "WILDCARD  ", "ADBUF     ", "HEARTBEAT ", "ERROR     ", "TRACE2_UA ", //0-4
    "NANOBUF   ", "ACK       ", "7 UNKN    ", "PICK_SCNL ", "CODA_SCNL ", "PICK2K    ", "CODA2K    ", "PICK2     ", "CODA2     ", // 5-13
    "HYP2000ARC", "H71SUM2K  ", "16 UNKN   ", "HINVARC   ", "H71SUM    ", "TRACE2    ", "TRACE     ", "LPTRIG    ",//14-21
    "CUBIC     ", "CARLSTATRG", "TRIGLIST  ", "TRIGLIST2K", "TRACE_UA  ", "STRONGMOTN", "MAGNITUDE ", "STRONGMOT2",
    "LOC_GLOBAL", "LPTRIGSCNL", //15-31
    "CRLSTASCNL", "TRGLSTSCNL", "TD_AMP    "}; //32-34

  public static String getTypeString(int i) {
    if (i < 0 || i >= typeString.length) {
      return "TYPE OOR";
    }
    return typeString[i];
  }
  /**
   * the general msg format is : type One of the TYPE_ above These three bytes form the "logo"
   * module Module ID instid Institution ID buf Buffer with message, the type of message determines
   * exactly what is in here
   *
   */
  public static final int LOGO_TYPE_OFFSET = 0;
  public static final int LOGO_MODULE_OFFSET = 1;
  public static final int LOGO_INSTITUTION_OFFSET = 2;
  // for an unknown reason the UDP header does not have same logo order and adds the fragmentation stuff
  public final static int OFF_INST_UDP = 0;
  public final static int OFF_TYPE_UDP = 1;
  public final static int OFF_MOD_UDP = 2;
  public final static int OFF_FRAGNO_UDP = 3;
  public final static int OFF_SEQNO_UDP = 4;
  public final static int OFF_LAST_UDP = 5;

  public static final int BODY_OFFSET = 3;
  private final byte[] buf;         // contains the entire message, logo and body
  private final ByteBuffer bb;       // BYte buffer wrapping the message

  public EWMessage(byte[] inbuf, int len) {
    buf = new byte[len];
    bb = ByteBuffer.wrap(buf);
    System.arraycopy(inbuf, 0, buf, 0, len);
  }

  public byte getType() {
    bb.position(LOGO_TYPE_OFFSET);
    return bb.get();
  }

  public byte getModule() {
    bb.position(LOGO_MODULE_OFFSET);
    return bb.get();
  }

  public byte getInstitution() {
    bb.position(LOGO_INSTITUTION_OFFSET);
    return bb.get();
  }

}
