/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package subspacepreprocess;

import gov.usgs.subspaceprocess.SubspacePreprocess;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Calendar;

/**
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class CWBSubspacePreprocess extends SubspacePreprocess {

  private final StringBuilder out = new StringBuilder(100);
  private String templatePath;
  private final EdgeThread par;

  public CWBSubspacePreprocess(String inputfile, boolean operational, EdgeThread parent) throws IOException {
    super(inputfile, operational);
    par = parent;
    super.setLoggering(par);
  }

  public StringBuilder writeKeyValueFile(String keyValueFile) throws IOException {
    Util.clear(out);
    templatePath = keyValueFile.substring(0, keyValueFile.lastIndexOf("/")) + "Template/";
    Util.chkFilePath(templatePath);
    out.append("filtertype#").append(filter.getFilterType()).append("\n");
    out.append("lowpassFreq#").append(filter.getLPcorner()).append("\n");
    out.append("hipassFreq#").append(filter.getHPcorner()).append("\n");
    out.append("npoles#").append(filter.getNpoles()).append("\n");
    out.append("detectthreshold#").append(detectionthreshold).append("\n");
    out.append("noiseWindowLengthInSec#").append(noisewindowlengthinsec).append("\n");
    out.append("detectionThresholdScaleFactor#").append(detectionthresholdscalefactor).append("\n");
    out.append("detectionThresholdType#").append(detectionthresholdtype).append("\n");
    out.append("stationLatitude#").append(stationinfo.getLatitude()).append("\n");
    out.append("stationLongitude#").append(stationinfo.getLongitude()).append("\n");
    out.append("stationElevation#").append(stationinfo.getElevation()).append("\n");
    out.append("centroidLatitude#").append(centroidlat).append("\n");
    out.append("centroidLongitude#").append(centroidlon).append("\n");
    out.append("centroidDepth#").append(centroiddep).append("\n");
    out.append("sourceReceiverDistance#").append(srcrcvdist).append("\n");
    out.append("radialDistance#").append(maxdist).append("\n");
    // getChannels
    out.append("channels#");
    String loc = stationinfo.getLocation();
    if (loc == null) {
      loc = "..";
    }
    if (loc.trim().length() == 0) {
      loc = "--";
    }
    for (int i = 0; i < stationinfo.getCompleteChannelList().length; i++) {
      out.append(stationinfo.getChannels()[i]).append(loc).append(",");
    }
    out.deleteCharAt(out.length() - 1);
    out.append("\n");

    // Template files
    writeTemplateFiles();
    out.append("templateNames#");
    for (String templateFilename : templateFilenames) {
      out.append(templateFilename).append(",");
    }
    out.deleteCharAt(out.length() - 1);
    out.append("\n");
    Util.writeFileFromSB(keyValueFile, out);

    return out;
  }
  private byte[] buf;
  private ByteBuffer bb;
  private static final int HEADER_SIZE = 0;
  private final ArrayList<String> templateFilenames = new ArrayList<>(3);

  private void writeTemplateFiles() throws IOException {
    templateFilenames.clear();
    GregorianCalendar ot = templates.getReferenceOT();
    String stub = Util.df22(templates.getReferenceLatitude()) + "_" + Util.df22(templates.getReferenceLongitude()) + "_"
            + Util.df4(ot.get(Calendar.YEAR)) + Util.df3(ot.get(Calendar.DAY_OF_YEAR))
            + Util.df2(ot.get(Calendar.HOUR_OF_DAY)) + Util.df2(ot.get(Calendar.MINUTE)) + Util.df2(ot.get(Calendar.SECOND));
    int nchannels = stationinfo.getChannels().length;
    String[] channels = stationinfo.getChannels();
    for (int ndet = 0; ndet < templates.getNumberOfTemplates(); ndet++) {
      float[] data = templates.DetectorTemplates().get(ndet);      // This has nchannels of concatenated floats
      int nsamples = data.length / nchannels;
      if (buf == null) {
        buf = new byte[nsamples * 4 + HEADER_SIZE];
        bb = ByteBuffer.wrap(buf);
      }
      if (buf.length < nsamples * 4 + HEADER_SIZE) {
        buf = new byte[nsamples * 4 + HEADER_SIZE];
      }
      for (int ich = 0; ich < nchannels; ich++) {
        bb.position(0);
        for (int i = ich * nsamples; i < (ich + 1) * nsamples; i++) {
          bb.putFloat(data[i]);
        }
        String filename = templatePath + stub + "_" + channels[ich] + ".template";
        templateFilenames.add(filename);
        Util.chkFilePath(filename);
        try (RandomAccessFile rf = new RandomAccessFile(filename, "rw")) {
          rf.seek(0L);
          rf.write(buf, 0, bb.position());
          rf.setLength(bb.position());
        }
      }
    }
  }
}
