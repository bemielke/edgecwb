/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

/**
 * AnssPorts.java - this class contains public static ints for various ports used in the ANSS
 * system.
 *
 * Created on January 13, 2004, 3:33 PM
 *
 * @author ketchum
 */
public final class AnssPorts {

  /**
   * RTSServer listens here for UDP to forward on
   */
  public static int RTS_COMMAND_UDP_PORT = 7999;
  /**
   * UDPListen get udp status packets from RTS
   */
  public static int RTS_STATUS_UDP_PORT = 7998;
  /**
   * UDPListen server status to this port
   */
  public static int RTS_STATUS_SERVER_PORT = 7997;
  /**
   * TcpHoldings listens here
   */
  public static int HOLDINGS_TCP_PORT = 7996;
  /**
   * RTSServer (cmdlistens) here for sockets from RTS
   */
  public static int RTS_COMMAND_SOCKET_PORT = 7995;
  /**
   * RTSServer (cmdlistens) here for sockets from RTS
   */
  public static int UDP_HOLDINGS_SERVER_PORT = 7994;

  /**
   * UDPChannel listens for UDP packets here
   */
  public static int CHANNEL_UDP_PORT = 7993;
  /**
   * processes needed the current status StatusSocketReader here
   */
  public static int CHANNEL_SERVER_PORT = 7992;
  /**
   * The UdpProcess server listens here for UDP Packets with process information
   */
  public static int PROCESS_UDP_PORT = 7991;
  /**
   * The UdpProcess server up process data on sockets from Display clients here
   */
  public static int PROCESS_SERVER_PORT = 7990;
  /**
   * / RTSServer listens here for user connections
   */
  public static int RTS_UTILITY_COMMAND_PORT = 7989;
  /**
   * The UdpConnect server listens here for UDP Packets with process information
   */
  public static int CONNECT_UDP_PORT = 7988;
  /**
   * The UdpConnect server up process data on sockets from Display clients here
   */
  public static int CONNECT_SERVER_PORT = 7987;
  /**
   * the port for EdgeMom to have status service running -100 for each vdl instance
   */
  public static int EDGEMOM_STATUS_SERVER = 7986;
  /**
   * the port for status commands and control DBMessageServer
   */
  public static int MYSQL_TEXT_SERVER = 7985;
  /**
   * the port for status commands and control TextStatusServer/TextStatusServer
   */
  public static int TEXT_STATUS_SERVER = 7984;
  /**
   * The port used for serving EdgeBlockQueue to outside clients (continuous wave forms
   */
  public static int EDGE_BLOCK_SERVER = 7983;
  /**
   * The port used for Raw Input Server to Edge mom (rcv/station in raw form)
   */
  public static int RAW_INPUT_RCV_SERVER = 7982;
  /**
   * The port used for Raw Input Server to Edge mom (any contributor in raw form
   */
  public static int RAW_INPUT_SERVER = 7981;
  /**
   * The Port used by the UdpTunnelServer to make connections from Q330s using tunneling
   */
  public static int RTS_TUNNEL_PORT = 7980;
  /**
   * the port for EdgeMom to have status serivice running
   */
  public static int EDGEMOM_INDEX_FILE_SERVER = 7979;
  /**
   * the port for EdgeMom RawInputServer for IRIS/IDA via ISI
   */
  public static int EDGEMOM_RAWINPUT_IDA_ISI_SERVER = 7977;
  /**
   * the port for EdgeMom RawInputServer for NSN re request data
   */
  public static int EDGEMOM_RAWINPUT_NSNREQUEST_SERVER = 7976;
  /**
   * the port for EdgeMom RawInputServer for Q330s
   */
  public static int EDGEMOM_RAWINPUT_Q330_SERVER = 7975;
  /**
   * the port for EdgeMom MiniSeedServer for baler data
   */
  public static int EDGEMOM_MINISEED_BALER_SERVER = 7974;
  /**
   * the port for EdgeMom RawInputServer for EW data
   */
  public static int EDGEMOM_RAWINPUT_EW_SERVER = 7973;
  /**
   * the port for EdgeMom RawInputServer for IMS public data
   */
  public static int EDGEMOM_RAWINPUT_IMS_SERVER = 7972;
  /**
   * the port for EdgeMom RawInputServer for EW data
   */
  public static int EDGEMOM_RAWINPUT_DMC_SERVER = 7971;
  /**
   * the port for EdgeMom RawInputServer for DADP data
   */
  public static int EDGEMOM_RAWINPUT_DADP_SERVER = 7970;
  /**
   * the port for EdgeMom RawInputServer for IMS restricted data
   */
  public static int EDGEMOM_RAWINPUT_IMS_RESTRICTED_SERVER = 7970;
  /**
   * the port for EdgeMom MiniSeedsServer for gsn baler data
   */
  public static int EDGEMOM_MINISEED_GSN_SERVER = 7969;
  /**
   * the port for EdgeMom MiniSeedsServer for gsn baler data
   */
  public static int EDGEMOM_Q330_1SEC_SERVER = 7968;
  /**
   * the port for ASL/GSN import of out-of-band miniseed data from re-requests
   */
  public static int EDGEMOM_GSN_REREQUEST_SERVER = 7965;
  /**
   * the port for UDP packets for the Alarm system UDP Server
   */
  public static int ALARM_EVENT_UDP_PORT = 7964;
  /**
   * the port for EdgeMonitorServer for ICINGA mainly each vdl 100 less if multiple
   */
  public static int EDGEMOM_MONITOR_PORT = 7962;
  /**
   * the port for ForceCheckServer for forcing a replication to start on some old file
   */
  public static int EDGEMOM_FORCE_CHECK_PORT = 7960;
  /**
   * the port for alarm access for GUI for active events
   */
  public static int EDGEMOM_ALARM_CHECK_PORT = 7959;

  /**
   * The ANSTCP process listens for socket connections on this port, and Q680 data
   */
  public static int ANS_PORT = 2004;
  /**
   * the VDLTCP process listens for socket connections on this port, and IMPORT server
   */
  public static int VDL_PORT = 2003;
  /**
   * The UNAVTCP process listens for socket connections on this port, and Console server user connections
   */
  public static int UNAVTCP_PORT = 2009;
  /**
   * The EXPORT (VMS) Program listens for socket connections on this port
   */
  public static int EXPORT_PORT = 3000;
  /**
   * TCPOUT listens for connection on this port for systems desiring data
   */
  public static int TCPOUT_PORT = 2005;
  /**
   * The DRMSRV (VMS) program listens on this port
   */
  public static int DRMSRV_PORT = 2001;
  /**
   * The IPSTAG (VMS) program listens on this port
   */
  public static int IPSTAG_PORT = 2000;
  /**
   * The NSNDLLTCP program listens for connections from consoles on this port, RTS console
   * connections
   */
  public static int DLL_PORT = 2007;
  /**
   * The GETFILE (VMS) program listens for file connection on this port
   */
  public static int GETFILE_PORT = 2012;

  // Monitoring ports for NAGIOS - multi instance
  public static int MONITOR_EDGEMOM_PORT = 7800;    // to 7809
  public static int MONITOR_EWEXPORT_PORT = 7810;   // to 7814
  public static int MONITOR_ALARM_PORT = 7819;      // single port
  public static int MONITOR_LISSSERVER_PORT = 7820; // to 7824
  public static int MONITOR_QUERYSERVER_PORT = 7825;// to 7826
  public static int MONITOR_RRPSERVER_PORT = 7830;  // to 7834
  public static int MONITOR_RRPCLIENT_PORT = 7840;  // to 7849
  public static int MONITOR_EDGEMOM_STATUS_PORT = 7890; // status output format

  // Single monitoring ports with a single spare
  public static int MONITOR_MDS_PORT = 7850;
  public static int MONITOR_MONITORMAIL_PORT = 7852;
  public static int MONITOR_SMGETTER_PORT = 7854;
  public static int MONITOR_TCPHOLDING_PORT = 7856;
  public static int MONITOR_UDPCHANNEL_PORT = 7858;
  public static int MONITOR_CWBWAVESERVER_PORT = 7860;

  /**
   * Creates a new instance of AnssPorts
   */
  public AnssPorts() {
  }

}
