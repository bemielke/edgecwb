/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.util;

import java.awt.event.ActionEvent;
import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/**
 * A set of utility classes for the USGS codebase.
 *
 * TODO: this is starting to get unruly, decompose a bit.
 * 
 * @author Dan Cervelli
 */
public class Util
{
	public static final char DEGREE_SYMBOL = (char)0xb0;
	
	public static final long ONE_KB = 1024;
	public static final long ONE_MB = 1024 * ONE_KB;
	public static final long ONE_GB = 1024 * ONE_MB;
	public static final long ONE_TB = 1024 * ONE_GB;
	public static final long ONE_PB = 1024 * ONE_TB;
	
	private static final SimpleDateFormat dateOut;
	private static final DecimalFormat diffFormat;
	private static final Calendar cal = Calendar.getInstance();
  private static final StringBuffer sb = new StringBuffer(100);
	
	/**
	 * Static initializers for the dateOut object.
	 */
	static
	{
		dateOut = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		dateOut.setTimeZone(TimeZone.getTimeZone("GMT"));
		diffFormat = new DecimalFormat("#.##");
	}
	
	/** Formats a <CODE>Date</CODE> with a <CODE>SimpleDateFormat</CODE> of
	 * "yyyy-MM-dd HH:mm:ss.SSS".
	 * @param d the date
	 * @return the formatted date String
	 */	
	public static String formatDate(java.util.Date d)
	{
		return dateOut.format(d);
	}

	/**
	 * Splits a string into tokens.
	 * 
	 * @param src the string to split
	 * @param tokens the characters that demarcate tokens
	 * @return the sub-strings
	 * @deprecated
	 */
	public static String[] splitString(String src, String tokens)
	{
		ArrayList<String> list = new ArrayList<>();
		StringTokenizer st = new StringTokenizer(src, tokens);
		while (st.hasMoreTokens())
			list.add(st.nextToken());
			
		String[] result = new String[list.size()];
		for (int i = 0; i < list.size(); i++)
			result[i] = (String)list.get(i);
			
		return result;
	}
	
	/**
	 * Escapes a string for a given character by duplicating that character.
	 * Example: escapeString("Dan's example", '\'') would return "Dan''s 
	 * example".  This is useful for storing strings with quotes in a database.
	 *
	 * @param src the source string
	 * @param c the character to duplicate
	 * @return the escaped string
	 */
	public synchronized static String escapeString(String src, char c)
	{
		if (src == null)
			return "";
	  if(sb.length() > 0) sb.delete(0, sb.length());
    sb.append(src);
		//StringBuffer b = new StringBuffer(src);
		for (int i = 0; i < sb.length(); i++)
		{
			if (sb.charAt(i) == c)
			{
				sb.insert(i, c);
				i++;
			}
		}
		return sb.toString();
	}
	
	/** Converts a j2ksec to a <CODE>Date</CODE> object
	 * @param in the j2ksec
	 * @return the Date
	 */	
	public static java.util.Date j2KToDate(double in)
	{
		return new java.util.Date((long)(1000 * (in + 946728000)));
	}
	
	/** Converts a <CODE>Date</CODE> object to a j2ksec.
	 * @param in the Date
	 * @return the j2ksec
	 */	
	public static double dateToJ2K(java.util.Date in)
	{
		return (((double)in.getTime() / (double)1000) - 946728000);
	}
	
	/** Gets the time now as a j2ksec
	 * @return the j2ksec
	 */	
	public static double nowJ2K()
	{
		return dateToJ2K(new java.util.Date());
	}

	/** Gets the year from a <CODE>Date</CODE> object
	 * @param d the date
	 * @return the year
	 */	
	public static int getYear(java.util.Date d)
	{
		cal.setTime(d);
		return cal.get(Calendar.YEAR);
	}
	
	/** Gets the month from a <CODE>Date</CODE> object
	 * @param d the date
	 * @return the month
	 */	
	public static int getMonth(java.util.Date d)
	{
		cal.setTime(d);
		return cal.get(Calendar.MONTH);
	}
	
	/** Gets the number of months between two <CODE>Date</CODE> objects.
	 * The number of months between two dates in the same month in the same year is 0.
	 * @param ds the starting date
	 * @param de the ending date
	 * @return the number of months
	 */	
	public static int getMonthsBetween(java.util.Date ds, java.util.Date de)
	{
		int months = 0;
		cal.setTime(ds);
		Calendar cal2 = Calendar.getInstance();
		cal2.setTime(de);
		while (cal.get(Calendar.MONTH) != cal2.get(Calendar.MONTH) 
				|| cal.get(Calendar.YEAR) != cal2.get(Calendar.YEAR))
		{
			months++;
			cal.add(Calendar.MONTH, 1);
		}
		return months;
	}
	
	/**
	 * Returns a string of this format: "#d #h #m #s" representing the amount
	 * of time between two dates.
	 * @param diff the difference (seconds)
	 * @return the string representing this difference
	 */
	public static String timeDifferenceToString(double diff)
	{
	    String diffString;
	    if (diff < 60)
	        diffString = diffFormat.format(diff) + "s";
	    else if (diff < 3600)
	        diffString = (int)(diff / 60) + "m " + diffFormat.format(diff % 60) + "s";
	    else if (diff < 86400)
	    {
	        diffString = (int)(diff / 3600) + "h ";
	        diff -= 3600 * (int)(diff / 3600);
	        diffString = diffString + (int)(diff / 60) + "m " + diffFormat.format(diff % 60) + "s";
	    }
	    else
	    {
	        diffString = (int)(diff / 86400) + "d ";
	        diff -= 86400 * (int)(diff / 86400);
	        diffString = diffString + (int)(diff / 3600) + "h ";
	        diff -= 3600 * (int)(diff / 3600);
	        diffString = diffString + (int)(diff / 60) + "m " + diffFormat.format(diff % 60) + "s";
	    }
	    return diffString;
	}
	
	/**
	 * @param bytes bytes quantity
	 * @return String representation of bytes count, in bytes, kilobytes, megabytes etc.
	 */
	public static String numBytesToString(long bytes)
	{
		if (bytes <= 4 * ONE_KB)
			return String.format("%d B", bytes);
		else if (bytes <= 4 * ONE_MB)
			return String.format("%.3f KB", (double)bytes / (double)ONE_KB);
		else if (bytes <= 4 * ONE_GB)
			return String.format("%.3f MB", (double)bytes / (double)ONE_MB);
		else if (bytes <= 4 * ONE_TB)
			return String.format("%.3f GB", (double)bytes / (double)ONE_GB);
		else if (bytes <= 4 * ONE_PB)
			return String.format("%.3f TB", (double)bytes / (double)ONE_TB);
		else 
			return String.format("%.3f PB", (double)bytes / (double)ONE_PB);
	}
	
	/**
	 * Converts a ResultSet to an HTML table 
	 * @param rs the ResultSet
	 * @return an HTML string
	 */
	public synchronized static String resultSetToHTML(ResultSet rs)
	{
		try
		{
			ResultSetMetaData meta = rs.getMetaData();
			int numCols = meta.getColumnCount();
			
			//StringBuffer sbloc = new StringBuffer();
      if(sb.length() > 0) sb.delete(0, sb.length());
			sb.append("<table>\n<tr>");
			for (int i = 1; i <= numCols; i++)
				sb.append("<td>").append(meta.getColumnLabel(i)).append("</td>");
			
			sb.append("</tr>\n");
			while (rs.next())
			{
				sb.append("<tr>");
				for (int i = 1; i <= numCols; i++)
					sb.append("<td>").append(rs.getObject(i)).append("</td>");
				sb.append("</tr>");
			}
			sb.append("</table>");
			return sb.toString();
		}
		catch (Exception e)
		{
			e.printStackTrace();	
		}
		return null;
	}
	
	/**
	 * Adds to JComponent action and keystroke for it
	 * @param comp JComponent
	 * @param ks KeyStroke
	 * @param name 
	 * @param action
	 */
	public static void mapKeyStrokeToAction(final JComponent comp, final String ks, final String name, final AbstractAction action)
	{
		comp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(ks), name);
		comp.getActionMap().put(name, action);
	}
	
	/**
	 * Adds to JComponent button and keystroke for it
	 * @param comp JComponent
	 * @param ks KeyStroke
	 * @param name 
	 * @param button
	 */
	public static void mapKeyStrokeToButton(final JComponent comp, final String ks, final String name, final AbstractButton button)
	{
		comp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(ks), name);
		comp.getActionMap().put(name, new AbstractAction()
				{
					private static final long serialVersionUID = -1;
          @Override
					public void actionPerformed(ActionEvent e)
					{
						button.doClick();
					}	
				});
	}
	
	/**
	 * Converts a j2ksec to an "yyyy-MM-dd HH:mm:ss.SSS" string.
	 * @param j j2ksec
   * @return 
	 */
	public static String j2KToDateString(double j)
	{
		return j2KToDateString(j, "yyyy-MM-dd HH:mm:ss.SSS");
	}
	
	/**
	 * Converts a j2ksec to string date.
	 * @param format format to convert date
	 * @param j j2ksec
   * @return 
	 */
	public static String j2KToDateString(double j, String format)
	{
		DateFormat df1 = new SimpleDateFormat(format);
		df1.setTimeZone(TimeZone.getTimeZone("GMT"));
		return df1.format(j2KToDate(j));
	}
	
	/**
	 * Converts a j2ksec to an earthworm second.
	 * @param j j2ksec
	 * @return earthworm second.
	 */
	public static double j2KToEW(double j)
	{
		return j + 946728000;
	}

	/**
	 * Converts a earthworm second into an j2ksec
	 * @param e earthworm second
	 * @return an j2ksec
	 */
	public static double ewToJ2K(double e)
	{
		return e - 946728000;
	}
	
	/**
	 * Converts integer to byte value
	 * @param i integer to convert
	 * @return converted byte
	 */
	public static byte intToByte(int i)
	{
		return (byte)(i & 0xff);
	}

	/**
	 * Converts byte to integer value
	 * @param b byte to convert
	 * @return converted int
	 */
	public static int byteToInt(byte b)
	{
		return (int)b & 0xff;
	}

	/**
	 * Converts array of bytes into string on per-symbol basis, till first 0 value
	 * @param b byte array to convert
	 * @return converted string
	 */
	public static String bytesToString(byte[] b)
	{
		return bytesToString(b, 0, b.length);
	}
	
	/**
	 * Converts array of bytes into string on per-symbol basis, till first 0 value
	 * @param b byte array to convert
	 * @param o number of first byte to convert
	 * @param l length of converting part
	 * @return converted string
	 */
	public static String bytesToString(byte[] b, int o, int l)
	{
		int trunc = -1;
		for (int i = o; i < o + l; i++)
			if (b[i] == 0)
			{
				trunc = i;
				break;
			}
		if (trunc != -1)
			//return new String(b, o, trunc - o);
			return quickBytesToString(b, o, trunc - o);
		else
//			return new String(b, o, l);
			return quickBytesToString(b, o, l);
	}

	/**
	 * Converts array of bytes into string on per-symbol basis
	 * 
	 * @param b byte array to convert 
	 * @param ofs offset from first array member to start conversion
	 * @param len resulting string length
	 * @return converted string
	 */
	public static String quickBytesToString(byte[] b, int ofs, int len)
	{
		char[] chars = new char[len];
		for (int i = 0; i < chars.length; i++)
			chars[i] = (char)b[i + ofs];
		return new String(chars);
	}
	
	/**
	 * Register
	 * 
	 * @param n 
	 * @param m
	 * @return register
	 */
	public static double register(double n, double m)
	{
		double dif = n % m;
		if (dif >= m / 2)
			return n + (m - dif);
		else
			return n - dif;
	}

	/**
	 * Swaps byte order, integer
	 * @param i int to have bytes swapped
	 * @return int w/ bytes of i swapped
	 */
	public static int swap(int i)
	{
		return (((i & 0xff000000) >> 24) & 0x000000ff) | 
			    ((i & 0x00ff0000) >> 8) |
			    ((i & 0x0000ff00) << 8) | 
			    ((i & 0x000000ff) << 24);
	}

	/**
	 * Swaps byte order, short
	 * @param s short to have bytes swapped
	 * @return short w/ bytes of s swapped
	 */
	public static short swap(short s)
	{
		return (short)( ((s & 0xff00 ) >> 8) | ((s & 0x00ff) << 8) );
	}
	
	/**
	 * Swaps byte order, double
	 * @param d double to have bytes swapped
	 * @return double w/ bytes of d swapped
	 */
	public static double swap(double d)
	{
		long l = Double.doubleToRawLongBits(d);
		long sl =  ((((l & 0xff00000000000000L) >> 56) & 0x00000000000000ff) |
				     ((l & 0x00ff000000000000L) >> 40) |
				     ((l & 0x0000ff0000000000L) >> 24) |
				     ((l & 0x000000ff00000000L) >> 8) |
				     ((l & 0x00000000ff000000L) << 8) |
				     ((l & 0x0000000000ff0000L) << 24) |
				     ((l & 0x000000000000ff00L) << 40) |
				     ((l & 0x00000000000000ffL) << 56));
		return Double.longBitsToDouble(sl);
	}
	
	/** Outputs raw data to a text file.  The first column is expected to be 
	 * a j2ksec.  The header string is written then lines of <br>
	 * j2ksec,converted j2ksec,data,data, ...<br>
	 * Text is comma delimited.
	 * @param fn the output file name
	 * @param hdr the header String
	 * @param data the data to write
	 */	
	public static void outputData(String fn, String hdr, double[][] data)
	{
		try
		{
      try (PrintWriter out = new PrintWriter(new FileWriter(fn))) {
        out.println(hdr);
        for (double[] data1 : data) {
          out.print(data1[0] + ",");
          out.print(dateOut.format(j2KToDate(data1[0])) + ",");
          for (int j = 1; j < data1.length - 1; j++) {
            out.print(data1[j] + ",");
          }
          out.println(data1[data1.length - 1]);
        }
      }
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	/** Outputs raw data to a text file.  The first column is expected to be 
	 * a j2ksec.  The header string is written then lines of <br>
	 * j2ksec,converted j2ksec,data,data, ...<br>
	 * Text is comma delimited.
	 * @param fn the output file name
	 * @param hdr the header String
	 * @param rs the ResultSet with the data
	 */	
	public static void outputData(String fn, String hdr, ResultSet rs)
	{
		try
		{
			ResultSetMetaData meta = rs.getMetaData();
			int nc = meta.getColumnCount();
      try (PrintWriter out = new PrintWriter(new FileWriter(fn, true))) {
        out.println(hdr);
        while (rs.next())
        {
          double j2ksec = rs.getDouble(1);
          out.print(j2ksec + ",");
          out.print(dateOut.format(j2KToDate(j2ksec)) + ",");
          for (int i = 2; i < nc - 1; i++)
            out.print(rs.getString(i) + ",");
          out.println(rs.getString(nc));
        }
      }
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}   
	}
	
	/** 
	 * Generates the MD5 hash for a text string. 
	 * @param plaintext the text string
	 * @return the MD5 hash
	 */
	public static String md5(String plaintext)
	{
		MessageDigest md = getMD5();
		if (md == null)
			return null;
		
		md.update(plaintext.getBytes());
		byte[] raw = md.digest();
		return getHexString(raw);
	}

	/**
	 * Constructs default MessageDigest
   * @return 
	 */
	public static MessageDigest getMD5()
	{
		MessageDigest md = null;
		try { md = MessageDigest.getInstance("MD5"); } catch (Exception e) {}
		return md;
	}
	
	/**
	 * Converts fixed-length 16-byte array to hex string
	 * @param raw array of 16 bytes
	 * @return Converted hex string
	 */
	public synchronized static String getHexString(byte[] raw)
	{
		ByteBuffer bb = ByteBuffer.wrap(raw);
		//StringBuffer sbloc = new StringBuffer(32);
      if(sb.length() > 0) sb.delete(0, sb.length());
		for (int i = 0; i < 4; i++)
		{
			String h = Integer.toHexString(bb.getInt());
			for (int j = h.length(); j < 8; j++)
				sb.append('0');
			sb.append(h);
		}
		return sb.toString();
	}

	/**
	 * Compute MD5 checksum for resource
	 * @param resource
	 * @return checksum
	 */
	public static String md5Resource(String resource)
	{
		
		MessageDigest md = getMD5();
		if (md == null)
			return null;
		
		try
		{
			InputStream in ;
			if (resource.contains("://"))
			{
				URL url = new URL(resource);
				in = url.openStream();
			}
			else
			{
				in = new FileInputStream(new File(resource));
			}
			
			int nr = 0;
			byte[] buf = new byte[64 * 1024];
			while (nr != -1)
			{
				nr = in.read(buf);
				if (nr != -1)
					md.update(buf, 0, nr);
			}
			in.close();
			return getHexString(md.digest());
		}
		catch (IOException e)
		{}
		return null;
	}
	
	/**
	 * Compresses an array of bytes using the JDK <code>Inflator</code>/
	 * <code>Deflator</code> implementation set for maximum speed.
	 * Compression ratios comparable to gzip are attained.
	 * 
	 * @param bytes the array of bytes
	 * @return the compressed array of bytes
	 */
	public static byte[] compress(byte[] bytes)
	{
	    return compress(bytes, Deflater.BEST_SPEED);
	}
	
	/**
	 * Compresses an array of bytes using the JDK <code>Inflator</code>/
	 * <code>Deflator</code> implementation.  Compression ratios comparable to
	 * gzip are attained.
	 *  
	 * @param bytes the array of bytes
	 * @param level the compression level (1[least]-9[most])
	 * @return the compressed array of bytes
	 */
	public static byte[] compress(byte[] bytes, int level)
	{
		return compress(bytes, level, 0, bytes.length);
	}

	/**
	 * Compresses an array of bytes using the JDK <code>Inflator</code>/
	 * <code>Deflator</code> implementation.  Compression ratios comparable to
	 * gzip are attained.
	 * 
	 * @param bytes the array of bytes
	 * @param level the compression level (1[least]-9[most])
	 * @param ofs number of first byte in array to process
	 * @param len length of processed array zone
	 * @return the compressed array of bytes
	 */
	public static byte[] compress(byte[] bytes, int level, int ofs, int len)
	{
		Deflater deflater = new Deflater(level);
		deflater.setInput(bytes, ofs, len);
		deflater.finish();
		ArrayList<byte[]> list = new ArrayList<>(2);
		boolean done = false;
		int compSize = 0;
		// must allow for the compressed size to be larger than the original size
		while (!done)
	    {
		    byte[] compBuf = new byte[bytes.length];
			compSize = deflater.deflate(compBuf);
	        if (deflater.finished())
	            done = true;
	        list.add(compBuf);
	    }
		int total = (list.size() - 1) * bytes.length + compSize;
	    byte[] finalBuf = new byte[total];
	    int j = 0;
	    for (int i = 0; i < list.size() - 1; i++)
	    {
	        System.arraycopy((byte[])list.get(i), 0, finalBuf, j, bytes.length);
	        j += bytes.length;
	    }
	    System.arraycopy((byte[])list.get(list.size() - 1), 0, finalBuf, j, compSize);
	    return finalBuf;
	}
	
	/**
	 * Decompresses an array of bytes compressed via the <code>Util.compress()</code>
	 * methods.  Uses a bufferSize of 64K.
	 * 
	 * @param bytes the compressed bytes
	 * @return the decompressed array of bytes
	 */
	public static byte[] decompress(byte[] bytes)
	{
	    return decompress(bytes, 65536);
	}

	/**
	 * Decompresses an array of bytes compressed via the <code>Util.compress()</code>
	 * methods.  Use a bufferSize slightly larger than the expected size of the
	 * decompressed data for maximum efficiency.
	 * 
	 * @param bytes the compressed bytes
	 * @param bufferSize the decompression buffer size
	 * @return the decompressed array of bytes
	 */
	public static byte[] decompress(byte[] bytes, int bufferSize)
	{
	    try
	    {
		    Inflater inflater = new Inflater();
		    inflater.setInput(bytes);
		    boolean done = false;
		     
		    ArrayList<byte[]> list = new ArrayList<>(10);
		    int numBytes = 0;
		    while (!done)
		    {
		        byte[] buffer = new byte[bufferSize];
		        numBytes = inflater.inflate(buffer);
		        if (inflater.finished())
		            done = true;
		        list.add(buffer);
		    }
		    inflater.end();
		    int total = (list.size() - 1) * bufferSize + numBytes;
		    byte[] finalBuf = new byte[total];
		    int j = 0;
		    for (int i = 0; i < list.size() - 1; i++)
		    {
		        System.arraycopy((byte[])list.get(i), 0, finalBuf, j, bufferSize);
		        j += bufferSize;
		    }
		    System.arraycopy((byte[])list.get(list.size() - 1), 0, finalBuf, j, numBytes);
		    return finalBuf;
	    }
	    catch (Exception e)
	    {
	        e.printStackTrace();
	    }
	    return null;
	}
	
	/**
	 * Resizes a byte array either up or down, truncating or 0-padding.  If the 
	 * new size is the same as the old the original array is returned, otherwise
	 * a new array is returned leaving the original one unmodified.
	 * 
	 * @param b the original byte array
	 * @param i the new size
	 * @return the resized array
	 */
	public static byte[] resize(byte[] b, int i)
	{
	    if (b.length == i)
	        return b;
	    else
	    {
	        byte[] bb = new byte[i];
	        System.arraycopy(b, 0, bb, 0, Math.min(i, b.length));
	        return bb;
	    }
	}
	
	/**
	 * Finds the filename and line number for an object in a stack trace.
	 * 
	 * @param obj the object whose line number is being sought
	 * @param t the exception
	 * @return the filename and line number in a string
	 */
	public static String getLineNumber(Object obj, Throwable t)
	{
	    StackTraceElement[] stack = t.getStackTrace();
    for (StackTraceElement stack1 : stack) {
      if (obj.getClass().getName().equals(stack1.getClassName())) {
        return t.getClass().getName() + "/" + stack1.getFileName() + ":" + stack1.getLineNumber();
      }
    }
	        
	    return "";
	}
	
	/**
	 * Gets the version info from the Version class that is generated when the
	 * projects are built.  Return null if it can't find it.
	 * 
	 * @param pkg the package name
	 * @return the version string and build date string
	 */
	public static String[] getVersion(String pkg)
	{
		try
		{
			String version = (String)Class.forName(pkg + ".Version").getField("VERSION").get(null);
			String build = (String)Class.forName(pkg + ".Version").getField("BUILD_DATE").get(null);
			return new String[] { version, build };
		}
		catch (ClassNotFoundException | NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e)
		{
			return null;
		}
	}
	
	/**
	 * Converts a string to a boolean.  To avoid confusion there is no default
	 * value.  This simply returns true on a val of "1" or "true".
	 * 
	 * @param val the string that represents the boolean
	 * @return the boolean
	 */
	public static boolean stringToBoolean(String val)
	{
		if (val == null)
			return false;
		return (val.toLowerCase().equals("true") || val.equals("1") || val.toLowerCase().equals("t"));
	}
	
	/**
	 * Converts a string to a boolean.  Returns default value on null input.
	 * 
	 * @param val the string that represents the boolean
   * @param def
	 * @return the boolean
	 */
	public static boolean stringToBoolean(String val, boolean def)
	{
		if (val == null)
			return def;
		return (val.toLowerCase().equals("true") || val.equals("1") || val.toLowerCase().equals("t"));
	}
	
	/**
	 * Converts a string to an integer, sets to <code>Interger.MIN_VALUE</code>
	 * if there's an exception.
	 * 
	 * @param val the string that represents the integer
	 * @return the integer
	 */
	public static int stringToInt(String val)
	{
		return stringToInt(val, Integer.MIN_VALUE);
	}
	
	/**
	 * Converts a string to an integer, sets to user-specified default if 
	 * there's an exception.
	 * 
	 * @param val the string that represents the integer
	 * @param def the default value
	 * @return the integer
	 */
	public static int stringToInt(String val, int def)
	{
		int i = def;
		try
		{
			i = Integer.parseInt(val);
		}
		catch (Exception e)
		{}
		return i;
	}
	
	/**
	 * Converts a string to an integer, sets to <code>Interger.MIN_VALUE</code>
	 * if there's an exception.
	 * 
	 * @param val the string that represents the integer
	 * @return the integer
	 */
	public static Integer stringToInteger(String val)
	{
		return stringToInt(val, Integer.MIN_VALUE);
	}
	
	/**
	 * Converts a string to an integer, sets to user-specified default if 
	 * there's an exception.
	 * 
	 * @param val the string that represents the integer
	 * @param def the default value
	 * @return the integer
	 */
	public static Integer stringToInteger(String val, Integer def)
	{
		Integer i = def;
		try
		{
			i = Integer.valueOf(val);
		}
		catch (Exception e)
		{}
		return i;
	}
	
	/**
	 * Converts a string to a double, sets to <code>Double.NaN</code>
	 * if there's an exception.
	 * 
	 * @param val the string that represents the double
	 * @return the double
	 */
	public static double stringToDouble(String val)
	{
		return stringToDouble(val, Double.NaN);
	}
	
	/**
	 * Converts a string to a double, sets to user-specified default if 
	 * there's an exception.
	 * 
	 * @param val the string that represents the double
	 * @param def the default value
	 * @return the double
	 */
	public static double stringToDouble(String val, double def)
	{
		double d = def;
		try
		{
			d = Double.parseDouble(val);
		}
		catch (Exception e)
		{}
		return d;
	}
	
	/**
	 * Checks if a string is null and returns a default string if it is.
	 * 
	 * @param val the original string
	 * @param def the default in case of null
	 * @return the original string if != null, otherwise default string
	 */
	public static String stringToString(String val, String def)
	{
		return (val == null) ? def : val;
	}

	/**
	 * Reads disk file
	 * @param fn file name
	 * @return string with file content
	 */
	public synchronized static String readTextFile(String fn)
	{
		try
		{
			BufferedReader in = new BufferedReader(new FileReader(fn));
            String s;
            //StringBuffer sbloc = new StringBuffer();
      if(sb.length() > 0) sb.delete(0, sb.length());
      while ((s = in.readLine()) != null)
        sb.append(s);

      return sb.toString();
		}
		catch (Exception e)
		{}
		return null;
	}

	/**
	 * Converts array of objects to Set
	 * @param objs to be made into Set
	 * @return Set of elements of objs
	 */
	public static Set toSet(Object[] objs)
	{
		HashSet<Object> set = new HashSet<>();
		for (Object o : objs)
			set.add(o);
		return set;
	}
	
	/**
	 * Converts map to string which consist from pairs key=value, divided by semicolon
	 * @param map to be made into string
	 * @return string representation
	 */
	public static String mapToString(Map<String, String> map)
	{
		String result = "";
		for (Map.Entry item : map.entrySet())
			result += item.getKey() + "=" + item.getValue() + "; ";
		
		return result;
	}

	/**
	 * Converts string which consist from pairs key=value, divided by semicolon to map
	 * @param src string representation
	 * @return map
	 */
	public static Map<String, String> stringToMap(String src) 
	{
		Map<String, String> result = new HashMap<>();
		String[] params = src.split(";");
    for (String param : params) {
      int ei = param.indexOf("=");
      if (ei == -1)
        continue;
      String k = param.substring(0, ei).trim();
      String v = param.substring(ei + 1);
      result.put(k, v);
    }
		return result;
	}
	
	/**
	 * Converts byte to hex string
	 * @param b byte
	 * @return hex string
	 */
	public static String byteToHex(byte b)
	{
		String h = Integer.toHexString((int)b & 0xff);
		if (h.length() == 1)
			h = "0" + h;
		return h;
	}

	/**
	 * Converts array of bytes to hex string
	 * @param buf byte array
	 * @return hex string
	 */
	public static String bytesToHex(byte[] buf)
	{
		StringBuilder sbloc = new StringBuilder(buf.length * 2 + 1);
		for (int i = 0; i < buf.length; i++)
			sbloc.append(byteToHex(buf[i]));
		
		return sbloc.toString();
	}

	/**
	 * Converts hex string to array of bytes
	 * @param s hex string
	 * @return array of bytes
	 */
	public static byte[] hexToBytes(String s)
	{
		int n = s.length() / 2;
		byte[] buf = new byte[n];
		for (int i = 0; i < n; i++)
		{
			String ss = s.substring(i * 2, i * 2 + 2);
			int j = Integer.parseInt(ss, 16);
			buf[i] = (byte)j;
		}
		return buf;
	}

	/**
	 * Converts signed double latitude to string north or south value 
	 * @param lat latitude
	 * @return string rep of latitude
	 */
	public static String latitudeToString(double lat)
	{
		char ns = 'N';
		if (lat < 0)
			ns = 'S';
		
		lat = Math.abs(lat);
		return String.format("%.4f", lat) + DEGREE_SYMBOL + ns;
	}

	/**
	 * Converts signed double longitude to string east or west value 
	 * @param lon longitude
	 * @return string rep of longitude
	 */
	public static String longitudeToString(double lon)
	{
		while (lon < -180)
			lon += 360;
		while (lon > 180)
			lon -= 360;
		
		char ew = 'E';
		if (lon < 0)
			ew = 'W';
		
		lon = Math.abs(lon);
		return String.format("%.4f", lon) + DEGREE_SYMBOL + ew;
	}
	
	/**
	 * Converts signed double longitude and latitude to string 
   * @param pt
   * @return 
	 * @see Util#longitudeToString(double)
	 * @see Util#latitudeToString(double)
	 */
	public static String lonLatToString(Point2D.Double pt)
	{
		return String.format("%s, %s", longitudeToString(pt.x), latitudeToString(pt.y));
	}
	
	/**
	 * Constructs comparator to compare strings with ignore case mean.
	 * @return std comparison result
	 */
	public static Comparator<String> getIgnoreCaseStringComparator()
	{
		return new Comparator<String>()
				{
          @Override
					public int compare(String o1, String o2)
					{
						return o1.compareToIgnoreCase(o2);
					}
				};
	}
	
	/**
	 * Sleep without caring about an {@link InterruptedException}, returns
	 * a boolean indicated if one occurred.
	 * 
	 * @param ms
	 * @return "interruption occurred"
	 */
	public static boolean sleep(long ms)
	{
		try
		{
			Thread.sleep(ms);
			return true;
		}
		catch (InterruptedException ex)
		{
			return false;
		}
	}
	
	/**
	 * <p>Main class method.</p>
	 * <p>Usage:</p>
	 * <p>without arguments - prints usage message</p>
	 * <p>-j2d [j2k]			j2k to date</p>
	 * <p>-d2j [yyyymmddhhmmss]	date to j2k</p>
	 * <p>-e2d [ewtime]         earthworm to date</p>
	 * <p>-md5 [string]			md5 of string</p>
	 * <p>-md5r [resource]      md5 of a resource (filename, url)</p>
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception
	{
		if (args.length == 0)
		{
			System.out.println("-j2d [j2k]				j2k to date");
			System.out.println("-j2e [j2k]              j2k to earthworm");
			System.out.println("-d2j [yyyymmddhhmmss]	date to j2k");
			System.out.println("-e2d [ewtime]           earthworm to date");
			System.out.println("-md5 [string]			md5 of string");
			System.out.println("-md5r [resource]        md5 of a resource (filename, url)");
			System.exit(1);
		}
		DateFormat df1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		DateFormat df2 = new SimpleDateFormat("yyyyMMddHHmmss");
		df1.setTimeZone(TimeZone.getTimeZone("GMT"));
		df2.setTimeZone(TimeZone.getTimeZone("GMT"));
    switch (args[0]) {
      case "-j2d":
        System.out.println(df1.format(j2KToDate(Double.parseDouble(args[1]))));
        break;
      case "-j2e":
        System.out.println(df1.format(j2KToEW(Double.parseDouble(args[1]))));
        break;
      case "-d2j":
        System.out.println(Util.dateToJ2K(df2.parse(args[1])));
        break;
      case "-e2d":
        System.out.println(df1.format(j2KToDate(ewToJ2K(Double.parseDouble(args[1])))));
        break;
      case "-md5":
        System.out.println(Util.md5(args[1]));
        break;
      case "-md5r":
        System.out.println(Util.md5Resource(args[1]));
        break;
    }
	}
}
