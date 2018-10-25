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
import java.util.zip.*;

/**
 * open a buffered input stream use GZIPInputStream or ZipInputStream if extension is .gz or .zip
 */
public class ZipStream {

    /**
     * returns true if extension is .gz
     */
    public static boolean isGzip(String URLName) {

        // determine if gzipped
        if (URLName.toLowerCase().endsWith(".gz")) {
            return (true);
        }

        return (false);
    }

    /**
     * returns true if extension is .zip
     */
    public static boolean isZip(String URLName) {

        // determine if gzipped
        if (URLName.toLowerCase().endsWith(".zip")) {
            return (true);
        }

        return (false);
    }

    /**
     * open an input stream use GZIPInputStream or ZipInputStream if extension is .gz or .zip
     */
    public static InputStream getInputStream(InputStream is, String URLName)
            throws IOException {

        if (is == null) {
            return (null);
        }

        InputStream zipis = is;

        // determine if gzipped
        if (isGzip(URLName)) {
            try {
                zipis = new GZIPInputStream(zipis);
            } catch (IOException ioe) {
//ioe.printStackTrace();
                throw (new IOException(
                        "ERROR: IOException opening GZIP file: " + ioe.getMessage() + ": " + URLName));
            }
        } // determine if zipped
        else if (isZip(URLName)) {
            try {
                zipis = new ZipInputStream(zipis);
                ((ZipInputStream) zipis).getNextEntry();
            } catch (ZipException ze) {
                throw (new IOException(
                        "ERROR: ZipException opening ZIP file: " + URLName));
            } catch (IOException ioe) {
                throw (new IOException(
                        "ERROR: IOException opening ZIP file: " + URLName));
            }
        }

        return (zipis);

    }

    /**
     * open an output stream
     */
    public static OutputStream getOutputStream(OutputStream os, String URLName)
            throws IOException {

        if (os == null) {
            return (null);
        }

        OutputStream zipos = os;

        // determine if gzipped
        if (URLName.toLowerCase().endsWith(".gz")) {
            try {
                zipos = new GZIPOutputStream(zipos);
            } catch (IOException ioe) {
                throw (new IOException(
                        "ERROR: IOException opening GZIP file: " + URLName));
            }
        } // determine if zipped
        else if (URLName.toLowerCase().endsWith(".zip")) {
            try {
                zipos = new ZipOutputStream(zipos);
                ((ZipOutputStream) zipos).putNextEntry(new ZipEntry(URLName));
            } catch (ZipException ze) {
                throw (new IOException(
                        "ERROR: ZipException writing ZIP file: " + URLName));
            } catch (IOException ioe) {
                throw (new IOException(
                        "ERROR: IOException opening ZIP file: " + URLName));
            }
        }


        return (zipos);

    }
}
