/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package gov.usgs.adslserverdb;
import gov.usgs.anss.util.Util;
/**
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */


public class Stats {
  String ip;
  int nquery;
  int nold;
  int nconn;
  int noldcoord;
  int noldtran;
  int noldorient;
  int noldresp;
  int noldalias;
  int noldcomment;
  int kml,resp,alias,delazs,delazc,station,coord,desc,list;
  public Stats(String name) {
    ip=name;
    nquery=0;
  }
  public void incNewCount() {nquery++;}
  public void incOldCount() {nold++;}
  public void incOldCoord() {noldcoord++;}
  public void incOldTran() {noldtran++;}
  public void incOldOrient() {noldorient++;}
  public void incOldResp() {noldresp++;}
  public void incOldAlias() {noldalias++;}
  public void incOldComment() {noldcomment++;}
  public void incKML() {kml++;}
  public void incResp() {resp++;}
  public void incAlias() {alias++;}
  public void incDelazc() {delazc++;}
  public void incDelazs() {delazs++;}
  public void incStation() {station++;}
  public void incCoord() {coord++;}
  public void incDesc() {desc++;}
  public void incList() {list++;}
  public void incConnCount() {nconn++;}
  @Override
  public String toString() {return  ""+Util.rightPad(ip.substring(1), 15)+Util.leftPad(""+nconn, 8)+Util.leftPad(""+nquery, 8)+

          Util.leftPad(""+kml, 8)+Util.leftPad(""+resp, 8)+Util.leftPad(""+alias, 8)+
          Util.leftPad(""+delazs, 8)+Util.leftPad(""+delazc, 8)+Util.leftPad(""+station, 8)+
          Util.leftPad(""+coord, 8)+Util.leftPad(""+desc, 8)+Util.leftPad(""+list, 8)+
          Util.leftPad(""+nold, 8)+Util.leftPad(""+noldcoord, 8)+
          Util.leftPad(""+noldtran, 8)+Util.leftPad(""+noldorient, 8)+Util.leftPad(""+noldresp, 8)+
          Util.leftPad(""+noldalias, 8)+Util.leftPad(""+noldcomment, 8);
  }
}