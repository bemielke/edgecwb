/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package subspacedetector;

import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 *
 * @author hbenz
 */
public final class DetectionSummary {
    

    /*
     * seismograms:  Array list of template seismogram, ALL need to be of the same length
     * seismogramdst: Array list of transformed templates
     * nft:  length in powser of 2 of the template transforms
     * tlength:  length of the individual templates, ALL need to be the same length
     */
    
    private GregorianCalendar origintime;
    private GregorianCalendar phasetime;
    private GregorianCalendar detectiontime;
    private GregorianCalendar timeoffirstsample;
    
    private float magnitude;

    private float latitude;       // Latitude of reference earthquake
    private float longitude;      // Longitude of reference earthquake
    private float depth;          // Depth of reference earthquake
    private String phase;         // Observing phase
    private float rate;           // Sample rate 
    private boolean complete = false;  

    private float threshold;
    
    public void prt(String s) {Util.prt(s);}
    public void prta(String s) {Util.prta(s);} 
    
    public float Magnitude() { return magnitude; }
    public boolean Complete() { return complete; }
    public float Rate() { return rate; }
    public float Latitude() { return latitude; }
    public float Longitude() { return longitude; }
    public float Depth() { return depth; }
    public String Phase() { return phase; }
    public GregorianCalendar OriginTime() { return origintime; }        // Estimated origin-time of detection event
    public GregorianCalendar PhaseTime() { return phasetime; }          // Estimated arrivaltime of detection event
    public GregorianCalendar DetectionTime() { return detectiontime; }  // Time of the detection
    public GregorianCalendar TimeofFirstSample() { return timeoffirstsample; }  // Estimated origin-time of detection event
    
    
    public float getThreshold() { return threshold; }
    
    public DetectionSummary() {  
    }
    
    //
    // tt: object containing information about the templates needed to compute the
    //     origin time, phase and magnitude of the detection
    // data:  The segment of data being processed
    // lastsegment:  A portion of the last segment (block) of data needed to find the full waveform
    //               detected
    // currenttime:  The time of the 1st sample of the segment being processed
   
    // peaksample: The sample of the peak.
    // 
    public void summary( Config cfg,
            GregorianCalendar currenttime, int peaksample ) throws IOException {  
        
        StringBuilder cc = Util.ascdatetime2(currenttime);
        
        int nchan = cfg.getNumberofChannels();
        int templateLength = cfg.getTemplateLength();
        float delta = (float) (1.0/cfg.getStatInfo().getRate()); 
        float templateDuration = (templateLength-1)*delta; 
        float lag = templateLength*delta;
        
        latitude = cfg.getReferenceLatitude();
        longitude = cfg.getReferenceLongitude();
        depth = cfg.getReferenceDepth(); 
        phase = cfg.getStatInfo().getPhaseLabel();
        
        rate = cfg.getStatInfo().getRate();
          
        float dt = (float)peaksample / (float)rate;
        detectiontime = new GregorianCalendar();
        detectiontime.setTimeInMillis(currenttime.getTimeInMillis());
        detectiontime.add(Calendar.MILLISECOND,(int)(1000.0*dt));
        StringBuilder det = Util.ascdatetime2(detectiontime);
        
        timeoffirstsample = new GregorianCalendar();
        timeoffirstsample.setTimeInMillis(detectiontime.getTimeInMillis());
        timeoffirstsample.add(Calendar.MILLISECOND, (int) (-1000.0*(templateDuration))); 
        StringBuilder ft = Util.ascdatetime2(timeoffirstsample);
        
        phasetime = new GregorianCalendar();
        phasetime.setTimeInMillis(timeoffirstsample.getTimeInMillis());
        phasetime.add(Calendar.MILLISECOND, (int) ((float)(1000.0*(Math.abs(cfg.getPreEvent())))));
        StringBuilder ph = Util.ascdatetime2(phasetime);
        
        origintime = new GregorianCalendar();
        origintime.setTimeInMillis(phasetime.getTimeInMillis());
        float ttime = (float) ((cfg.getReferenceArrivalTime().getTimeInMillis()-cfg.getReferenceOriginTime().getTimeInMillis())/1000.0);
        origintime.add(Calendar.MILLISECOND, (int) (-1000.*ttime));
        StringBuilder ot = Util.ascdatetime2(origintime);
        
        magnitude = 1;
        complete = true;
    }
}
