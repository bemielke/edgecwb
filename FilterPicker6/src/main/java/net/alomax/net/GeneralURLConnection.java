/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 1999 Anthony Lomax <anthony@alomax.net www.alomax.net>
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


package net.alomax.net;


import java.io.*;
import java.net.*;



/** GeneralIURLConnection class */


public class GeneralURLConnection  {
    
    
    /** open URLConnection */
    
    public static URLConnection openURLConnection(URL documentBase, String fileName)
    throws IOException, MalformedURLException {
        
        URL url = createURL(documentBase, fileName);
        // open URLConnection
        return(openURLConnection(url));
        
    }
    
    
    
    /** open URLConnection */
    
    public static URLConnection openURLConnection(URL url) throws IOException {
        
        // open URLConnection
        URLConnection urlConn = url.openConnection();
        return (urlConn);
        
    }
    
    
    
    /** create URL */
    
    public static URL createURL(URL documentBase, String fileName) throws MalformedURLException {
        
        URL url = null;
        //System.out.println("createURL 0 " + documentBase + " " + fileName);
        
/*		// BUG fix: Linux: Java version 1.4.2 (48.0, Sun Microsystems Inc.)
                // if file beginning with "/" try name only as absolute path to file
                if (documentBase.toString().startsWith("file:") && fileName.indexOf(":") < 0) {
                        try {
                                url = new URL("file:" + fileName);
System.out.println("NOTE: Applied BUG fix: GeneralURLConnection.createURL: Java version 1.4.2 (Linux?)");
System.out.println("      Opening: " + url);
                                return(url);
                        } catch (MalformedURLException e) {}
                }
 */
        // try name as URL
        try {
            url = new URL(fileName);
            //System.out.println("createURL 1 " + url);
            return(url);
        } catch (MalformedURLException e) { }
        // try context and name
        try {
            url = new URL(documentBase, fileName);
            //System.out.println("createURL 2 " + url);
            return(url);
        } catch (MalformedURLException e) { }
        // try name only as file
        try {
            url = new URL("file:" + fileName);
            //System.out.println("createURL 3 " + url);
            return(url);
        } catch (MalformedURLException e) {
            throw(e);
        }
        
    }
    
    
    
}	// end class GeneralIURLConnection

