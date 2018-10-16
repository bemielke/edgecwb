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


package gov.usgs.anss.seed;
import java.nio.ByteBuffer;

import gov.usgs.anss.util.Util;
/*
 * Blockette2000.java
 * * The blockette 2000 has :
 * 0  i*2 blockette type  is 2000
 * 2  i*2 Next Blockette byte number (offset to next blockette)
 * 4  i*2 total blockett length in bytes
 * 6  i*2 Offset to Opaque data
 * 8  i*4 record number
 * 12 word order (0=little, 1= big endian)
 * 13 Opaque data flags (bit 0 (0=stream, 1=record orientd)
 *     Bit 1 Packageing bit 0 2000s from multiple seed with different times can be grouped
 *           1=Do not repackage
 *     Bit 2-3 Fragmentation (00 = completed contained, 01 first of many, 10 continuation of many, 11 last of many
 *     Bit 4-5 File blockette information 00 not file oriented, 01 first blockette of file, 10 continuation of file, 11 last of file
 * 14  Number of header fields -
 * 15-??? The header fields, each on is terminated with a ~
 * Starting at offset to opaque there is more data encoding depends on vendor
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class Blockette2000 {
  String [] fields;
  short lengthTotal;
  short offsetOpaque;
  int recordNumber;
  byte wordOrder;
  byte opaqueFlags;
  byte [] opaqueBuf;
  String [] fragChars = {"A","1","C","E"};
  public Blockette2000(ByteBuffer ms) {
    int pos = ms.position();
    short b2000 = ms.getShort();
    short nxt = ms.getShort();
    lengthTotal = ms.getShort();
    offsetOpaque = ms.getShort();
    recordNumber = ms.getInt();
    wordOrder = ms.get();
    opaqueFlags = ms.get();
    byte nfields = ms.get();
    fields = new String[nfields];
    byte byt;
    for(int i=0; i<nfields; i++) {
      fields[i]="";
      while( (byt = ms.get()) != '~')
        fields[i]+=(char) byt;
      //Util.prt("Header["+i+"]="+fields[i]);
    }
    ms.position(pos+offsetOpaque);
    if(Math.min(lengthTotal -(ms.position()-pos), ms.limit()-ms.position()) > 0) {
      opaqueBuf = new byte[Math.min(lengthTotal - (ms.position()-pos), ms.limit()-ms.position())];
      ms.get(opaqueBuf);
    }
    else {
      Util.prt("********** Odd blockette 2000 neg data size");
    }
  }
  @Override
  public String toString() {return "(2k:"+recordNumber+"/"+((opaqueFlags&1) != 0?"S":"R")+((opaqueFlags&2) != 0?"P":"N")+
          " frg="+fragChars[(opaqueFlags<< 2)&3]+" l="+lengthTotal+")";}
  public String [] getFields() {return fields;}
  public int getLength() {return lengthTotal;}
  public byte getWordOrder() {return wordOrder;}
  public byte getFlags() {return opaqueFlags;}
  public int getRecordNumber() {return recordNumber;}
  public byte[] getOpaqueBuf() {return opaqueBuf;}

}
