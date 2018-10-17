package org.trinet.waveserver.rt;
/** Defines the TRINET WaveClient/Server TCPMessage types. Each message type requires a unique identifier. */
public interface TrinetTCPMessageTypes {
    public final static int  TN_TCP_GETDATA_REQ   = 3000;
    public final static int  TN_TCP_GETTIMES_REQ  = 3001;
    public final static int  TN_TCP_GETRATE_REQ   = 3002;
    public final static int  TN_TCP_GETCHAN_REQ   = 3003;

    public final static int  TN_TCP_ERROR_RESP    = 3004;
    public final static int  TN_TCP_GETDATA_RESP  = 3005;
    public final static int  TN_TCP_GETTIMES_RESP = 3006;
    public final static int  TN_TCP_GETRATE_RESP  = 3007;
    public final static int  TN_TCP_GETCHAN_RESP  = 3008;

    public final static int  TN_TCP_TIMEOUT_RESP  = 3099;
}
