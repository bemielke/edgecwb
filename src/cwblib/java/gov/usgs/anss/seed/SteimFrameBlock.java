/*
 * Copyright 2010, United States Geological Survey or
 * third-party contributors as indicated by the @author tags.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package gov.usgs.anss.seed;

//import gov.usgs.anss.edge.*;
import java.io.*;

/**
 * This class acts as a container to hold encoded bytes processed
 * by a Steim compression routine, as well as supporting information
 * relating to the data processed.
 * It also facilitates Steim notation and the formation
 * of the data frames.
 * This class stores the Steim encoding, but is ignorant of the encoding
 * process itself...it's just for self-referencing.
 * @author Robert Casey (IRIS DMC)
 * @version 12/10/2001
 */

public class SteimFrameBlock {

	// *** constructors *** 

	/**
	 * Create a new block of Steim frames for a particular version of Steim
	 * copression.
	 * Instantiate object with the number of 64-byte frames
	 * that this block will contain (should connect to data
	 * record header such that a proper power of 2 boundary is
	 * formed for the data record) AND the version of Steim
	 * compression used (1 and 2 currently)
	 * the number of frames remains static...frames that are
	 * not filled with data are simply full of nulls.
	 * @param numFrames the number of frames in this Steim record
	 * @param steimVersion which version of Steim compression is being used
	 * (1,2,3).
	 */
	public SteimFrameBlock (int numFrames, int steimVersion) {
		steimFrame = new SteimFrame[numFrames]; // array of frames
		for (int i = 0; i < steimFrame.length; i++) {
			// initialize the SteimFrame array
			steimFrame[i] = new SteimFrame();
		}
		this.numFrames = numFrames;
		this.steimVersion = steimVersion;

		// initialize the first frame properly
		currentFrame = 0;            // sanity
		addEncodingNibble(0); // first nibble always 00
		this.steimFrame[currentFrame].pos++;  // increment position in frame to W1
	}


	// *** public methods ***

	/**
	 * Return the number of data samples represented by this frame block
	 * @return integer value indicating number of samples
	 */
	public int getNumSamples () {
		return numSamples;
	}

	/**
	 * Return the version of Steim compression used
	 * @return integer value representing the Steim version (1,2,3)
	 */
	public int getSteimVersion () {
		return steimVersion;
	}

	/**
	 * Return the compressed byte representation of the data for inclusion
	 * in a data record.
	 * @return byte array containing the encoded, compressed data
	 * @throws IOException from called method(s)
	 */
	public byte[] getEncodedData () throws IOException {
		// set up a byte array to write int words to
		ByteArrayOutputStream encodedData = 
			new ByteArrayOutputStream(numFrames * 64);
		// set up interface to the array for writing the ints
		DataOutputStream intSerializer = 
			new DataOutputStream(encodedData);
		for (int i = 0; i < numFrames; i++) {  // for each frame
			for (int j = 0; j < 16; j++) {     // for each word
				// write integer to byte stream
				intSerializer.writeInt(steimFrame[i].word[j]);	
			}
		}

		return encodedData.toByteArray(); // return byte stream as array
	}

	/**
	 * Return the number of frames in this frame block
	 * @return integer value indicating number of frames
	 */
	public int getNumFrames () {
		return numFrames;
	}


	// *** private and protected methods ***

	/**
	 * Add a single 32-bit word to current frame.
   * @param word The word to convert
	 * @param samples the number of sample differences in the word
	 * @param nibble a value of 0 to 3 that reflects the W0 encoding
	 * for this word
	 * @return boolean indicating true if the block is full (ie: the
	 * calling app should not add any more to this object)
	 */
	protected boolean addEncodedWord (int word, int samples, int nibble) {
		int pos = steimFrame[currentFrame].pos; // word position
		steimFrame[currentFrame].word[pos] = word; // add word
		addEncodingNibble (nibble);                     // add nibble
		numSamples += samples;
		pos++;     // increment position in frame
		if (pos > 15) {  // need next frame?
			currentFrame++;
			if (currentFrame >= numFrames) {  // exceeded frame limit?
				return true;  // block is full
			}
			addEncodingNibble(0); // first nibble always 00
		}
		steimFrame[currentFrame].pos++;  // increment position in frame
		return false;  // block is not yet full
	}

	/**
	 * Set the reverse integration constant X(N) explicitly to the
	 * provided word value.
	 * This method is typically used to reset X(N) should the compressor
	 * fill the frame block before all samples have been read.
	 * @param word integer value to be placed in X(N)
	 */
	protected void setXsubN (int word) {
		steimFrame[0].word[2] = word;
	}

	/**
	* Add encoding nibble to W0.
	* @param bitFlag a value 0 to 3 representing an encoding nibble
	*/
	private void addEncodingNibble (int bitFlag) {
		int offset = steimFrame[currentFrame].pos; // W0 nibble offset - determines Cn in W0
		int shift = (15 - offset)*2;  // how much to shift bitFlag
		steimFrame[currentFrame].word[0] |= (bitFlag << shift);
	}


	// *** inner classes ***

	/**
	 * This represents a single Steim compression frame.  It stores values
	 * as an int array and keeps track of it's current position in the frame.
	 */
	private class SteimFrame {
		public int[] word = new int[16];  // 16 32-byte words
		public int pos = 0;  // word position in frame (pos: 0 = W0, 1 = W1, etc...)
	}


	// *** instance variables ***

	private int numFrames = 0;        // number of frames this object contains
	private int numSamples = 0;      // number of samples represented
	private int steimVersion = 0;    // Steim version number
	private int currentFrame = 0;     // number of current frame being built
	private SteimFrame[] steimFrame = null;  // array of frames;
}
