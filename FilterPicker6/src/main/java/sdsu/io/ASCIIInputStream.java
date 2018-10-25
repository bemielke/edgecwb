/*
 * Copyright (C) 1996 Andrew Scherpbier <andrew@sdsu.edu>
 *
 * This file is part of the San Diego State University Java Library.
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
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package sdsu.io;

import java.io.DataInput;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import net.alomax.io.PushbackInputStream;

/**
 * This class provides a way of getting interpreted ASCII input from a stream. The standard Java library doesn't provide any way of reading things
 * like integers from standard input. This functionality is nice to have for interactive programs.
 *
 * @version 1.1 12 Sept 1997
 * @author Andrew Scherpbier (<a href=mailto:andrew@sdsu.edu>andrew@sdsu.edu</a>)
 */
// AJL 20071210 - modifed private to public to allow extension by net.alomax.io.SmartASCIIInputStream
public class ASCIIInputStream extends FilterInputStream implements DataInput {
    //
    // Status info
    //

    public boolean hadEOF = false;

    /**
     * Creates a new ASCIIInputStream.
     *
     * @param stream	the input stream
     */
    public ASCIIInputStream(InputStream stream) {
        super(stream);
    }

    /**
     * Do not use. This method no longer does anything. Always returns false.
     *
     * @deprecated
     */
    public boolean bad() {
        return false;
    }

    /**
     * Determines if reach end of file in a previous read. This differs from eof() in in that eof() will indicate end of file if only white space
     * remains. This function only indicates if you actually reached end of file an a prevoius read. If only whitespace remains and you use readInt
     * (readDouble, etc.) an exception will be thrown. Use this method to detect end of file only if direct access to the white space between tokens
     * is important to your program and you handle the case of a file ending in white space properly.
     *
     * @see ASCIIInputStream#eof
     * @return <i>true</i> if end of file was reached.
     */
    public boolean hadEof() throws IOException {
        return hadEOF;
    }

    /**
     * Determines if stream is has reach end of file. Consumes white space up to next token. If your program needs that white space between tokens use
     * hadEof() instead. eof() is the prefered way to detect end of file.
     *
     * @see ASCIIInputStream#hadEof
     * @return <i>true</i> if end of file was reached or if only white space remained in the stream.
     */
    public boolean eof() throws IOException {
        if (hadEOF) {
            return true;
        } else {
            int character = nextNonWhiteSpaceCharacter();
            if (character == -1) {
                hadEOF = true;
                return true;
            } else {
                pushback(character);
                return false;
            }
        }
    }

    /**
     * Reads data into an array of bytes. This method blocks until some input is available.
     *
     * @param b	the buffer into which the data is read.
     * @return	the actual number of bytes read. -1 is returned when the end of the stream is reached. (In this case, the eof() member will return
     * <i>true</i>.)
     * @exception IOException If an I/O error has occurred.
     */
    public int read(byte b[]) throws IOException {
        return read(b, 0, b.length);
    }

    /**
     * Reads data into an array of bytes. This method blocks until some input is available.
     *
     * @param b	the buffer into which the data is read.
     * @param off the start offset of the data
     * @param len the maximum number of bytes read
     * @return	the actual number of bytes read. -1 is returned when the end of the stream is reached. (In this case, the eof() member will return
     * <i>true</i>.)
     * @exception IOException If an I/O error has occurred.
     */
    public int read(byte b[], int off, int len) throws IOException {
        int n = in.read(b, off, len);

        if (n < 0) {
            hadEOF = true;
        }
        return n;
    }

    /**
     * Reads bytes, blocking until all bytes are read. If EOF is reached, the eof() member will return <i>true</i>.
     *
     * @param b the buffer into which the data is read
     * @exception IOException If an I/O error has occurred.
     */
    public void readFully(byte b[]) throws IOException {
        readFully(b, 0, b.length);
    }

    /**
     * Reads bytes, blocking until all bytes are read. If EOF is reached, the eof() member will return <i>true</i>.
     *
     * @param b the buffer into which the data is read
     * @param off the start offset of the data
     * @param len the maximum number of bytes read
     * @exception IOException If an I/O error has occurred.
     */
    public void readFully(byte b[], int off, int len) throws IOException {
        InputStream in = this.in;
        int n = 0;

        while (n < len) {
            int count = in.read(b, off + n, len - n);
            if (count < 0) {
                hadEOF = true;
            }
            n += count;
        }
    }

    /**
     * Skips bytes, block until all bytes are skipped.
     *
     * @param n the number of bytes to be skipped
     * @return the actual number of bytes skipped.
     * @exception IOException If an I/O error has occurred.
     */
    public int skipBytes(int n) throws IOException {
        InputStream in = this.in;

        for (int i = 0; i < n; i += (int) in.skip(n - i));
        return n;
    }

    /**
     * Reads an ASCII boolean value. It reads a word and determines if it represents <i>true</i> or
     * <i>false</i>. Possible values are: <i>true</i> and <i>false</i>. The comparison is case insensitive. The eof() method will return true after
     * this method has attempted to read beyond the end of file.
     *
     * @return the boolean.
     * @exception IOException If an I/O error has occurred.
     * @exception NumberFormatException	If the next token in the input stream is not a valid boolean.
     */
    public boolean readBoolean() throws IOException {
        String s = readWord();
        if (s == null) {
            throw new NumberFormatException("Error in reading boolean: no string input");
        }

        if (s.equalsIgnoreCase("false")) {
            return false;
        } else if (s.equalsIgnoreCase("true")) {
            return true;
        } else {
            throw new NumberFormatException("Expected true or false, but got " + s);
        }
    }

    /**
     * Reads a single byte. The eof() method will return true after this method has attempted to read beyond the end of file.
     *
     * @return the byte.
     * @exception IOException If an I/O error has occurred.
     */
    public byte readByte() throws IOException {
        int character = in.read();
        if (character < 0) {
            hadEOF = true;
        }
        return (byte) (character & 0xff);
    }

    /**
     * Reads a single unsigned byte. This is virually the same as readChar(), except that the return value is an <i>int</i>.
     *
     * @return an int representing the unsigned byte.
     * @exception IOException If an I/O error has occurred.
     * @see ASCIIInputStream#readChar
     */
    public int readUnsignedByte() throws IOException {
        return (int) (readChar() & 0xff);
    }

    /**
     * Reads an ASCII decimal short. Shorts can be preceded by optional whitespace. whitespace is defined as SPACE, TAB, CR, or NL. The eof() method
     * will return true after this method has attempted to read beyond the end of file.
     *
     * @return the short.
     * @exception IOException If an I/O error has occurred.
     * @exception NumberFormatException	If next token is not a valid short. This is a runtime exception, so the compiler does not force you to catch
     * it.
     * @see ASCIIInputStream@readInt
     */
    public short readShort() throws IOException {
        return (short) readInt();
    }

    /**
     * Reads an ASCII decimal unsigned short. Unsigned shorts can be preceded by optional whitespace. whitespace is defined as SPACE, TAB, CR, or NL.
     *
     * @return an int representing the unsigned short.
     * @exception IOException If an I/O error has occurred.
     * @exception NumberFormatException	If next token is not a valid short. This is a runtime exception, so the compiler does not force you to catch
     * it.
     * @see ASCIIInputStream@readInt
     */
    public int readUnsignedShort() throws IOException {
        return readInt() & 0xffff;
    }

    /**
     * Read an ASCII character and convert it into the internal <b>char</b>
     * format. The eof() method will return true after this method has attempted to read beyond the end of file.
     *
     * @return the character.
     * @exception IOException If an I/O error occurred.
     */
    public char readChar() throws IOException {
        int character = in.read();

        if (character < 0) {
            hadEOF = true;
        }
        return (char) character;
    }

    /**
     * Reads an ASCII decimal integer. Integers can be preceded by optional whitespace. whitespace is defined as SPACE, TAB, CR, or NL. The eof()
     * method will return true after this method has attempted to read beyond the end of file.
     *
     * @return the integer.
     * @exception IOException If an I/O error has occurred.
     * @exception NumberFormatException	If the next token in the input stream is not a valid int. This is a runtime exception, so the compiler does
     * not force you to catch it.
     */
    public int readInt() throws IOException {
        return (int) readLong();
    }

    /**
     * Reads an ASCII decimal long. Longs can be preceded by optional whitespace. whitespace is defined as SPACE, TAB, CR, or NL. The eof() method
     * will return true after this method has attempted to read beyond the end of file.
     *
     * @return the long.
     * @exception IOException If an I/O error has occurred.
     * @exception NumberFormatException	If the next token in the input stream is not a valid long. This is a runtime exception, so the compiler does
     * not force you to catch it.
     */
    public long readLong() throws IOException {
        long value = 0;
        int character = -1;
        long sign = 1;
        int digitsRead = 0;

        if (hadEOF) {
            return 0;
        }

        //
        // Skip whitespace first.
        //
        character = nextNonWhiteSpaceCharacter();


        //
        // See if we need to change the sign of the number
        //
        switch (character) {
            case '-':
                sign = -1;
                character = in.read();
                break;

            case '+':
                character = in.read();
                break;

            case -1:
                hadEOF = true;
                return 0;
        }

        //
        // Read digits
        //
        while (character >= '0' && character <= '9') {
            value = value * 10 + (character - '0');
            character = in.read();
            digitsRead++;
        }

        if (character < 0) {
            hadEOF = true;
        } else {
            pushback(character);
        }

        if (digitsRead == 0) {
            //(new NumberFormatException()).printStackTrace();
            throw new NumberFormatException(
                    "The next token did not start with a digit");
        }
        return value * sign;
    }

    /**
     * Reads an ASCII decimal floating point number, returning default value on error.
     */
    public int readInt(int defaultValue) {

        try {
            return (Integer.parseInt(readWord()));
        } catch (Exception ignored) {
            return (defaultValue);
        }

    }

    /**
     * Reads an ASCII decimal floating point number, returning default value on error.
     */
    public float readFloat(float defaultValue) {

        try {
            return (Float.parseFloat(readWord()));
        } catch (Exception ignored) {
            return (defaultValue);
        }

    }

    /**
     * Reads an ASCII decimal floating point number, returning default value on error.
     */
    public double readDouble(double defaultValue) {

        try {
            return (Double.parseDouble(readWord()));
        } catch (Exception ignored) {
            return (defaultValue);
        }

    }

    /**
     * Reads an ASCII decimal floating point number. A floating point number is defined as follows:
     * <ul><li>an optional '-' to make the number negative</li>
     * <li>0 or more digits</li>
     * <li>an optional period follows by more digits</li>
     * <li>an optional 'e' or 'E' to introduce the exponent<ul>
     * <li>an optional '-' to make the exponent negative</li>
     * <li>digits making up the exponent</li>
     * </ul></li></ul>
     * Floats can be preceded by optional whitespace. whitespace is defined as SPACE, TAB, CR, or NL.<br>
     *
     * @return the float.
     * @exception IOException If an I/O error has occurred.
     * @exception NumberFormatException	If the next token in the input stream is not a valid float format. Note NumberFormatException is a runtime
     * exception.
     */
    public float readFloat() throws IOException {
        return (float) readDouble();
    }

    /**
     * Reads an ASCII decimal floating point number. A floating point number is defined as follows:
     * <ul><li>an optional '-' to make the number negative</li>
     * <li>0 or more digits</li>
     * <li>an optional period follows by more digits</li>
     * <li>an optional 'e' or 'E' to introduce the exponent<ul>
     * <li>an optional '-' to make the exponent negative</li>
     * <li>digits making up the exponent</li>
     * </ul></li></ul>
     * Doubles can be preceded by optional whitespace. whitespace is defined as SPACE, TAB, CR, or NL.<br>
     *
     * @return the double.
     * @exception IOException If an I/O error has occurred.
     * @exception NumberFormatException	If the next token in the input stream is not a valid double. This is a runtime exception, so the compiler does
     * not force you to catch it.
     */
    public double readDouble() throws IOException {
        double value = 0.0;
        int character = -1;
        int sign = 1;
        int digitsRead = 0;

        //
        // Skip whitespace first.
        //
        character = nextNonWhiteSpaceCharacter();

        //
        // See if we need to change the sign of the number
        //
        switch (character) {
            case '-':
                sign = -1;
                character = in.read();
                break;

            case '+':
                character = in.read();
                break;
        }

        //
        // Read digits
        //
        while (character >= '0' && character <= '9') {
            value = (double) (value * 10 + (character - '0'));
            character = in.read();
            digitsRead++;
        }

        //
        // After digits there are two possible legal characters:
        // an 'e' or a '.'
        //
        if (character == '.') {
            //
            // Decimal point.  All digits after this are after the
            // decimal point.
            //

            double multiplier = 0.1;
            character = in.read();
            while (character >= '0' && character <= '9') {
                value += (double) (character - '0') * multiplier;
                multiplier /= 10.0;
                character = in.read();
                digitsRead++;
            }
        }

        if (digitsRead == 0) {
            throw new NumberFormatException(
                    "Next token does not have valid decimal value. Token read so far: "
                    + (value * sign));
        }

        //
        // Now the only legal value is an 'e' to start the exponent
        //
        if (character == 'e' || character == 'E') {
            double exponent = 0;
            int esign = 1;

            digitsRead = 0;

            character = in.read();
            switch (character) {
                case '-':
                    esign = -1;
                    character = in.read();
                    break;

                case '+':
                    character = in.read();
                    break;
            }

            while (character >= '0' && character <= '9') {
                exponent = (double) (exponent * 10 + (character - '0'));
                character = in.read();
                digitsRead++;
            }
            if (exponent != 0) {
                try {
                    value *= Math.pow(10.0, exponent * esign);
                } catch (Exception e) {
                    value = 0;
                    sign = 1;
                }
            }

            if (digitsRead == 0) {
                throw new NumberFormatException("Invalid exponent format for: " + value * sign);
            }
        }

        if (character < 0) {
            hadEOF = true;
        } else {
            pushback(character);
        }
        return value * sign;
    }
    public char lineBuffer[];

    /**
     * Reads in a line that has been terminated by a \n, \r, \r\n or EOF. The eof() method will return true if the read attempted to go beyond the end
     * of file. The terminating line characters will not be part of the String that is returned.
     *
     * @return a String copy of the line or null if nothing more could be read.
     * @exception IOException If an I/O error has occurred.
     */
    public String readLine() throws IOException {
        InputStream in = this.in;
        char buf[] = lineBuffer;

        if (hadEOF) {
            return null;
        }

        if (buf == null) {
            buf = lineBuffer = new char[128];
        }

        int room = buf.length;
        int offset = 0;
        int c;

        loop:
        while (true) {
            switch (c = in.read()) {
                case -1:
                case '\n':
                    break loop;

                case '\r':
                    int c2 = in.read();
                    if (c2 != '\n') {
                        pushback(c2);
                    }
                    break loop;

                default:
                    if (--room < 0) {
                        buf = new char[offset + 128];
                        room = buf.length - offset - 1;
                        System.arraycopy(lineBuffer, 0, buf, 0, offset);
                        lineBuffer = buf;
                    }
                    buf[offset++] = (char) c;
                    break;
            }
        }
        if ((c == -1) && (offset == 0)) {
            hadEOF = true;
            return null;
        }
        return String.copyValueOf(buf, 0, offset);
    }

    /**
     * Does nothing. Do not use.
     */
    public String readUTF() throws IOException {
        return "";
    }

    /**
     * Reads a word. A word is a string of characters deliminated by whitespace or EOF. A word can be preceded by optional whitespace characters.
     * Whitespace is defined as SPACE, TAB, CR, or NL.<br>
     *
     * @return the word or null if EOF reached.
     * @exception IOException If an I/O error has occurred.
     */
    public String readWord() throws IOException {
        InputStream in = this.in;
        char word[] = lineBuffer;
        int room = 0;
        int offset = 0;
        int character = -1;
        boolean endOfWord = false;

        if (hadEOF) {
            return null;
        }

        if (word == null) {
            word = lineBuffer = new char[128];
        }

        room = word.length;

        //
        // Skip whitespace first.
        //
        character = nextNonWhiteSpaceCharacter();

        while (character != ' ' && character != '\t'
                && character != '\n' && character != '\r'
                && character >= 0) {
            if (--room < 0) {
                word = new char[offset + 128];
                room = word.length - offset - 1;
                System.arraycopy(lineBuffer, 0, word, 0, offset);
                lineBuffer = word;
            }
            word[offset++] = (char) character;
            character = in.read();
        }

        if ((character == -1) && (offset == 0)) {
            hadEOF = true;
            return null;
        }
        pushback(character);
        return String.copyValueOf(word, 0, offset);
    }

    /**
     * Causes the next I/O operation to start at the beginning of the next input line. Lines are delimited by either <i>cr</i>, <i>cr lf</i>, or
     * <i>lf</i>.
     *
     * @exception IOException If an I/O error has occurred.
     */
    public void flushLine() throws IOException {
        int character;

        while ((character = in.read()) >= 0) {
            if (character == '\r') {
                character = in.read();
                if (character != '\n') {
                    pushback(character);
                }
                return;
            } else if (character == '\n') {
                return;
            }
        }
        if (character < 0) {
            hadEOF = true;
        }
        return;
    }

    public void pushback(int character) throws IOException {
        if (!(in instanceof PushbackInputStream)) {
            in = new PushbackInputStream(in);
        }
        ((PushbackInputStream) in).unread(character);
    }

    /**
     * Returns the next character that is not a \n, \t, \r or a space
     */
    public int nextNonWhiteSpaceCharacter() throws IOException {
        int character;

        do {
            character = in.read();
        } while (character == ' ' || character == '\t'
                || character == '\r' || character == '\n');

        return character;
    }
}
