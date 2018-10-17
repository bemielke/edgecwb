package org.trinet.waveserver.rt;
/** Codes returned by TRINET WaveClient/Server API methods or as TCPMessage response error values. */
public interface TrinetReturnCodes {

    public static final int TN_TRUE       =  1;
    public static final int TN_FALSE      =  0;

    public static final int TN_SUCCESS    =  0;
    public static final int TN_FAILURE    = -1;
    public static final int TN_EOF        = -2;
    public static final int TN_SIGNAL     = -3;
    public static final int TN_NODATA     = -4;
    public static final int TN_NOTVALID   = -5;
    public static final int TN_TIMEOUT    = -6;
    public static final int TN_BEGIN      = -7;
    public static final int TN_END        = -8;
    public static final int TN_PARENT     = -9;
    public static final int TN_CHILD      = -10;

/** Operational failure status codes */
    public static final int TN_FAIL_WRITE = -20;
    public static final int TN_FAIL_READ  = -21;

/** Seismic-specific status codes */
    public static final int TN_BAD_SAMPRATE = -100;
}
