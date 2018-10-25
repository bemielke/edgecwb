/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 1999 Anthony Lomax <lomax@faille.unice.fr>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package net.alomax.io;

import java.io.*;
import java.net.*;
import net.alomax.net.GeneralURLConnection;

/**
 * GeneralInputStream class
 */
public class GeneralInputStream {

    private static boolean DEBUG = false;

    /**
     * Given a string representing a file name or URL, tries to open a URL or file input stream
     */
    public static InputStream openStreamCheckCompress(URL documentBase, String fileName, boolean useCaches) throws IOException {

        InputStream is = openStream(documentBase, fileName, useCaches);
        is = ZipStream.getInputStream(is, fileName);
        return (is);

    }

    /**
     * Given a string representing a file name or URL, tries to open a URL or file input stream
     */
    public static InputStream openStream(
            URL documentBase, String fileName, boolean useCaches) throws IOException {
        if (DEBUG) {
            System.out.println("\nGeneralInputStream.openStream: enter");
        }
        if (DEBUG) {
            System.out.println("   documentBase: " + documentBase);
        }
        if (DEBUG) {
            System.out.println("   fileName: " + fileName);
        }
        if (DEBUG) {
            System.out.flush();
        }

        InputStream inputStream = null;
        String exceptionString = "";

        //if (false && (documentBase.toString().startsWith("file:") && fileName.indexOf(":") < 0)) {
        if ((documentBase == null || documentBase.toString().startsWith("file:")) && fileName.indexOf(":") < 0) {
            if (documentBase != null && !fileName.startsWith(System.getProperty("file.separator"))
                    && !fileName.startsWith(documentBase.toString().substring("file:".length())))
            {
                // make a single file path from documentBase and fileName
                // 20130304 AJL - bug fix
                fileName = documentBase.toString().substring("file:".length()) + System.getProperty("file.separator") + fileName;
                documentBase = null;
            }
            //if (DEBUG) {
            //    System.out.println("NOTE: Applied BUG fix: GeneralURLConnection.createURL: Java version 1.4.2 (Linux?)");
            //}
            if (DEBUG) {
                System.out.println("      Opening: " + fileName);
            }
            if (DEBUG) {
                System.out.flush();
            }
        } else {
            // not local file
            //try to open input stream as URL
            try {
                if (!useCaches) {
                    // no caches, must use URLConnection
                    if (DEBUG) {
                        System.out.println("GeneralInputStream.openStream: call openURLConnInputStream");
                    }
                    if (DEBUG) {
                        System.out.flush();
                    }
                    inputStream = openURLConnInputStream(documentBase, fileName, useCaches);
                    if (DEBUG) {
                        System.out.println("GeneralInputStream.openStream: return openURLConnInputStream");
                    }
                    //System.out.println("openStream 0a " + inputStream);
                    if (inputStream != null) {
                        return (inputStream);
                    }
                } else {
                    // caches, can use URL directly
                    if (DEBUG) {
                        System.out.println("GeneralInputStream.openStream: call openURLInputStream");
                    }
                    if (DEBUG) {
                        System.out.flush();
                    }
                    inputStream = openURLInputStream(documentBase, fileName);
                    if (DEBUG) {
                        System.out.println("GeneralInputStream.openStream: return openURLInputStream");
                    }
                    //System.out.println("openStream 0b " + inputStream);
                    if (inputStream != null) {
                        return (inputStream);
                    }
                }
            } catch (Exception e) {
                exceptionString += e.toString() + "\n";
            }
        }

        // try to open input stream as local file
        try {
            if (DEBUG) {
                System.out.println("GeneralInputStream.openStream: call openFileInputStream");
            }
            inputStream = openFileInputStream(fileName);
            if (DEBUG) {
                System.out.println("GeneralInputStream.openStream: return openFileInputStream");
            }
            if (inputStream != null) {
                return (inputStream);
            }
        } catch (Exception e) {
            exceptionString += e.toString() + "\n";
        }

        try {
            inputStream.close();
        } catch (Exception e) {
            ;
        }

        throw (new IOException("GeneralInputStream: " + exceptionString));

    }

    /**
     * Given a string representing a file name or URL, tries to open a URL input stream
     */
    public static InputStream openURLConnInputStream(
            URL documentBase, String fileName, boolean useCaches)
            throws IOException, MalformedURLException {


        // try to open input stream through URLConnection

        // set URL
        URLConnection urlConn = null;

        if (DEBUG) {
            System.out.println("openURLConnInputStream openURLConnection() enter");
        }
        urlConn = GeneralURLConnection.openURLConnection(documentBase, fileName);
        if (DEBUG) {
            System.out.println("openURLConnInputStream openURLConnection() return");
        }

        if (DEBUG) {
            System.out.println("openURLConnInputStream url is : " + urlConn.getURL());
        }

        // set caching
        if (urlConn != null && !useCaches) {
            urlConn.setUseCaches(useCaches);
        }
        if (DEBUG) {
            System.out.println("openURLConnInputStream connect() enter");
        }
        urlConn.connect();
        if (DEBUG) {
            System.out.println("openURLConnInputStream connect() return");
        }

        if (urlConn != null) {
            // try to open input stream
            if (DEBUG) {
                System.out.println("openURLConnInputStream getInputStream() enter");
            }
            InputStream inputStream = getInputStream(urlConn);
            if (DEBUG) {
                System.out.println("openURLConnInputStream getInputStream() return");
            }
            urlConn = null;
            if (inputStream != null) {
                return (inputStream);
            }
        }

        throw (new IOException("GeneralInputStream: openURLConnInputStream; unknown error."));

    }

    /**
     * Given a string representing a file name or URL, tries to open a URL input stream
     */
    public static InputStream openURLInputStream(URL documentBase, String fileName)
            throws IOException {

        // try to open input stream directly from URL

        InputStream inputStream = null;

        if (DEBUG) {
            System.out.println("openURLInputStream createURL() enter");
        }
        URL url = GeneralURLConnection.createURL(documentBase, fileName);
        if (DEBUG) {
            System.out.println("openURLInputStream createURL() return : url : " + url);
        }
        if (url != null) {
            try {
                if (DEBUG) {
                    System.out.println("openURLInputStream url.openStream() enter ");
                }
                if (DEBUG) {
                    System.out.flush();
                }
                inputStream = url.openStream();
                if (DEBUG) {
                    System.out.println("openURLInputStream url.openStream() return ");
                }
                return (inputStream);
            } catch (UnknownHostException uhe) {
                throw (new IOException(uhe.getMessage()));
            }
        }

        throw (new IOException("GeneralInputStream: openURLInputStream; unknown error."));
    }

    /**
     * Given a string representing a file name or URL, tries to open a URL or file input stream
     */
    public static InputStream openFileInputStream(String fileName)
            throws FileNotFoundException, IOException {

        // open input stream as local file

        InputStream inputStream = null;

        //try {
        inputStream = new FileInputStream(fileName);
        //} catch (Exception e) {
        //}

        if (inputStream != null) {
            return (inputStream);
        }

        throw (new IOException("GeneralInputStream: openFileInputStream; unknown error."));
    }

    /**
     * get InputStream from URLConnection
     */
    public static InputStream getInputStream(URLConnection urlConn) throws IOException {

        InputStream inputStream = null;

        try {
            if (DEBUG) {
                System.out.println("GeneralInputStream.getInputStream: call getInputStream : " + urlConn);
            }
            inputStream = urlConn.getInputStream();
            if (DEBUG) {
                System.out.println("GeneralInputStream.getInputStream: return getInputStream");
            }
        } catch (UnknownServiceException use) {
            if (DEBUG) {
                System.out.println("GeneralInputStream.getInputStream: UnknownServiceException use getInputStream");
            }
            throw (new IOException(use.getMessage()));
        }

        if (inputStream != null) {
            return (inputStream);
        }

        throw (new IOException("GeneralInputStream: getInputStream; unknown error."));
    }

    /**
     * dump of URLConnection properties
     */
    public static void dumpURLConn(URLConnection urlConn) {


        ////System.out.println("getAllowUserInteraction <" + urlConn.getAllowUserInteraction() + ">");
        try {
            ////System.out.println("getContent <" + urlConn.getContent() + ">");
        } catch (Exception e) {
            ////System.out.printlne);
        }
        ////System.out.println("getContentEncoding <" + urlConn.getContentEncoding() + ">");
        ////System.out.println("getContentLength <" + urlConn.getContentLength() + ">");
        ////System.out.println("getContentType <" + urlConn.getContentType() + ">");
        ////System.out.println("getDate <" + urlConn.getDate() + ">");
        ////System.out.println("getDefaultAllowUserInteraction <" + urlConn.getDefaultAllowUserInteraction() + ">");
        //////System.out.println("getDefaultRequestProperty <" + urlConn.getDefaultRequestProperty(String);
        ////System.out.println("getDefaultUseCaches <" + urlConn.getDefaultUseCaches() + ">");
        ////System.out.println("getDoInput <" + urlConn.getDoInput() + ">");
        ////System.out.println("getDoOutput <" + urlConn.getDoOutput() + ">");
        ////System.out.println("getExpiration <" + urlConn.getExpiration() + ">");
        //////System.out.println("URLConnection.getFileNameMap <" + URLConnection.getFileNameMap() + ">");
        //////System.out.println("getHeaderField <" + urlConn.getHeaderField(int) ;
        //////System.out.println("getHeaderField <" + urlConn.getHeaderField(String);
        //////System.out.println("getHeaderFieldDate <" + urlConn.getHeaderFieldDate(String, long);
        //////System.out.println("getHeaderFieldInt <" + urlConn.getHeaderFieldInt(String, int);
        //////System.out.println("getHeaderFieldKey <" + urlConn.getHeaderFieldKey(int);
        ////System.out.println("getIfModifiedSince <" + urlConn.getIfModifiedSince() + ">");
        try {
            ////System.out.println("getInputStream <" + urlConn.getInputStream() + ">");
        } catch (Exception e) {
            ////System.out.printlne);
        }
        ////System.out.println("getLastModified <" + urlConn.getLastModified() + ">");
        try {
            ////System.out.println("getOutputStream <" + urlConn.getOutputStream() + ">");
        } catch (Exception e) {
            ////System.out.printlne);
        }
        //////System.out.println("getRequestProperty <" + urlConn.getRequestProperty(String);
        ////System.out.println("getURL <" + urlConn.getURL() + ">");
        ////System.out.println("getUseCaches <" + urlConn.getUseCaches() + ">");
    }
}	// end class GeneralInputStream

