/*
 * This file is part of the ORFEUS Java Library.
 *
 * Copyright (C) 2004 Anthony Lomax <anthony@alomax.net www.alomax.net>
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


/*
 * XMLParser.java
 *
 * Created on 24 May 2004, 17:20
 */

package nl.knmi.orfeus.seedlink;

/**
 *
 * @author  user
 */


import org.dom4j.*;
import org.dom4j.io.SAXReader;

import java.io.*;
import java.util.*;


public class XMLParser {
	
	/** Creates a new instance of XMLParser */
	private XMLParser() {
	}
	
	
	/**
	 *
	 * Parses SeedLink XML into a org.dom4j.Document object.
	 *
	 * @param xml XML source returned by a SeedLink INFO request.
	 *
	 * @return a org.dom4j.Document object
	 *
	 * <p>Example usage:
	 *
	 * <p><hr><blockquote><pre>
	 * infoLevel = slconn.getInfoString();
	 * 
	 * // print raw XML
	 * // System.out.println(infoLevel);
	 *
	 * if (infoLevel == null)
	 *     throw (new DatabaseException("ERROR: Invalid INFO response"));
	 *
	 * // parse XML to String
	 * Document document = XMLParser.parse(infoLevel);
	 *
	 * // print doc contents in readable format
	 * System.out.println(XMLParser.documentToString(document));
	 *
	 * // do something with document...
	 * </pre></blockquote><hr>
	 * <p>
	 *
	 * @exception DocumentException on error reading XML into Document
	 *
	 * @see nl.knmi.orfeus.SLClient#packetHandler(int, SLPacket)
	 * @see nl.knmi.orfeus.seedlink.client.SeedLinkConnection#getInfoString()
	 */
	public static Document parse(String xml) throws DocumentException {
		
		SAXReader reader = new SAXReader();
		Document document = reader.read(new StringReader(xml));
		return(document);
		
	}
	
	
	/**
	 *
	 * Parses SeedLink XML into a String.
	 *
	 * @param document a org.dom4j.Document object containing SeedLink XML .
	 *
	 * @return a String representation of this SeedLink XML Document
	 *
	 */
	public static String documentToString(Document document) throws DocumentException {
		
		StringBuffer xmlString = new StringBuffer("");
		
		Element root = document.getRootElement();
		
		// iterate through child elements of root
		Iterator i = root.elementIterator();
		Element element = root;
		do {
			xmlString.append(elementToString(element));
		} while (i.hasNext() && (element = (Element) i.next()) != null);
		
		return(xmlString.toString());
		
	}
	
	
	/**
	 *
	 * Parses SeedLink XML Element into a String.
	 *
	 * @param element a org.dom4j.Element object containing SeedLink XML .
	 *
	 * @return a String representation of this SeedLink XML Element
	 *
	 */
	public static String elementToString(Element element) throws DocumentException {
		
		StringBuffer xmlString = new StringBuffer("");
		
		xmlString.append(element.getQualifiedName());
		xmlString.append("\n");
		for ( Iterator j = element.attributeIterator(); j.hasNext(); ) {
			xmlString.append("    ");
			Attribute attribute = (Attribute) j.next();
			xmlString.append(attribute.getQualifiedName());
			xmlString.append("= ");
			xmlString.append(attribute.getValue());
			xmlString.append("\n");
		}
		
		return(xmlString.toString());
		
	}
	
}
