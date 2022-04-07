/*
 * Copyright (c) 2014 The APN-PROXY Project
 *
 * The APN-PROXY Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.dataeye.proxy.apn.utils;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;

/**
 * @author xmx
 * @version $Id: com.dataeye.proxy.apn.utils.Base64 14-1-8 16:13 (xmx) Exp $
 */
public class Base64 {

    /**
     * Holds thread context so classes can be thread-safe.
     * <p/>
     * This class is not itself thread-safe; each thread must allocate its own
     * copy.
     *
     * @since 1.7
     */
    static class Context {

        /**
         * Place holder for the bytes we're dealing with for our based logic.
         * Bitwise operations store and extract the encoding or decoding from
         * this variable.
         */
        int ibitWorkArea;

        /**
         * Place holder for the bytes we're dealing with for our based logic.
         * Bitwise operations store and extract the encoding or decoding from
         * this variable.
         */
        long lbitWorkArea;

        /**
         * Buffer for streaming.
         */
        byte[] buffer;

        /**
         * Position where next character should be written in the buffer.
         */
        int pos;

        /**
         * Position where next character should be read from the buffer.
         */
        int readPos;

        /**
         * Boolean flag to indicate the EOF has been reached. Once EOF has been
         * reached, this object becomes useless, and must be thrown away.
         */
        boolean eof;

        /**
         * Variable tracks how many characters have been written to the current
         * line. Only used when encoding. We use it to make sure each encoded
         * line never goes beyond lineLength (if lineLength > 0).
         */
        int currentLinePos;

        /**
         * Writes to the buffer only occur after every 3/5 reads when encoding,
         * and every 4/8 reads when decoding. This variable helps track that.
         */
        int modulus;

        Context() {
        }

        /**
         * Returns a String useful for debugging (especially within a debugger.)
         *
         * @return a String useful for debugging.
         */
        @SuppressWarnings("boxing")
        // OK to ignore boxing here
        @Override
        public String toString() {
            return String.format(
                    "%s[buffer=%s, currentLinePos=%s, eof=%s, ibitWorkArea=%s, lbitWorkArea=%s, "
                            + "modulus=%s, pos=%s, readPos=%s]", this.getClass().getSimpleName(),
                    buffer, currentLinePos, eof, ibitWorkArea, lbitWorkArea, modulus, pos, readPos);
        }
    }

    /**
     * EOF
     *
     * @since 1.7
     */
    static final int EOF = -1;

    /**
     * MIME chunk size per RFC 2045 section 6.8.
     * <p/>
     * <p>
     * The {@value} character limit does not count the trailing CRLF, but counts
     * all other characters, including any equal signs.
     * </p>
     *
     * @see <a href="http://www.ietf.org/rfc/rfc2045.txt">RFC 2045 section
     * 6.8</a>
     */
    public static final int MIME_CHUNK_SIZE = 76;

    /**
     * PEM chunk size per RFC 1421 section 4.3.2.4.
     * <p/>
     * <p>
     * The {@value} character limit does not count the trailing CRLF, but counts
     * all other characters, including any equal signs.
     * </p>
     *
     * @see <a href="http://tools.ietf.org/html/rfc1421">RFC 1421 section
     * 4.3.2.4</a>
     */
    public static final int PEM_CHUNK_SIZE = 64;

    private static final int DEFAULT_BUFFER_RESIZE_FACTOR = 2;

    /**
     * Defines the default buffer size - currently {@value} - must be large
     * enough for at least one encoded block+separator
     */
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    /**
     * Mask used to extract 8 bits, used in decoding bytes
     */
    protected static final int MASK_8BITS = 0xff;

    /**
     * Byte used to pad output.
     */
    protected static final byte PAD_DEFAULT = '=';           // Allow static access to
    // default

    protected final byte PAD = PAD_DEFAULT;   // instance variable just in case it
    // needs to vary later

    /**
     * Number of bytes in each full block of unencoded data, e.g. 4 for Base64
     * and 5 for Base32
     */
    private final int unencodedBlockSize;

    /**
     * Number of bytes in each full block of encoded data, e.g. 3 for Base64 and
     * 8 for Base32
     */
    private final int encodedBlockSize;

    /**
     * Chunksize for encoding. Not used when decoding. A value of zero or less
     * implies no chunking of the encoded data. Rounded down to nearest multiple
     * of encodedBlockSize.
     */
    protected final int lineLength;

    /**
     * Size of chunk separator. Not used unless {@link #lineLength} > 0.
     */
    private final int chunkSeparatorLength;

    /**
     * BASE32 characters are 6 bits in length. They are formed by taking a block
     * of 3 octets to form a 24-bit string, which is converted into 4 BASE64
     * characters.
     */
    private static final int BITS_PER_ENCODED_BYTE = 6;
    private static final int BYTES_PER_UNENCODED_BLOCK = 3;
    private static final int BYTES_PER_ENCODED_BLOCK = 4;

    /**
     * Chunk separator per RFC 2045 section 2.1.
     * <p/>
     * <p>
     * N.B. The next major release may break compatibility and make this field
     * private.
     * </p>
     *
     * @see <a href="http://www.ietf.org/rfc/rfc2045.txt">RFC 2045 section
     * 2.1</a>
     */
    static final byte[] CHUNK_SEPARATOR = {'\r', '\n'};

    /**
     * This array is a lookup table that translates 6-bit positive integer index
     * values into their "Base64 Alphabet" equivalents as specified in Table 1
     * of RFC 2045.
     * <p/>
     * Thanks to "commons" project in ws.apache.org for this code.
     * http://svn.apache.org/repos/asf/webservices/commons/trunk/modules/util/
     */
    private static final byte[] STANDARD_ENCODE_TABLE = {'A', 'B', 'C', 'D', 'E', 'F', 'G',
            'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X',
            'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o',
            'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5',
            '6', '7', '8', '9', '+', '/'};

    /**
     * This is a copy of the STANDARD_ENCODE_TABLE above, but with + and /
     * changed to - and _ to make the encoded Base64 results more URL-SAFE. This
     * table is only used when the Base64's mode is set to URL-SAFE.
     */
    private static final byte[] URL_SAFE_ENCODE_TABLE = {'A', 'B', 'C', 'D', 'E', 'F', 'G',
            'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X',
            'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o',
            'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5',
            '6', '7', '8', '9', '-', '_'};

    /**
     * This array is a lookup table that translates Unicode characters drawn
     * from the "Base64 Alphabet" (as specified in Table 1 of RFC 2045) into
     * their 6-bit positive integer equivalents. Characters that are not in the
     * Base64 alphabet but fall within the bounds of the array are translated to
     * -1.
     * <p/>
     * Note: '+' and '-' both decode to 62. '/' and '_' both decode to 63. This
     * means decoder seamlessly handles both URL_SAFE and STANDARD base64. (The
     * encoder, on the other hand, needs to know ahead of time what to emit).
     * <p/>
     * Thanks to "commons" project in ws.apache.org for this code.
     * http://svn.apache.org/repos/asf/webservices/commons/trunk/modules/util/
     */
    private static final byte[] DECODE_TABLE = {-1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 62, -1, 62, -1, 63, 52, 53, 54, 55,
            56, 57, 58, 59, 60, 61, -1, -1, -1, -1, -1, -1, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
            11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -1, -1, -1, -1, 63, -1, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48,
            49, 50, 51};

    /**
     * Base64 uses 6-bit fields.
     */
    /**
     * Mask used to extract 6 bits, used when encoding
     */
    private static final int MASK_6BITS = 0x3f;

    // The static final fields above are used for the original static byte[]
    // methods on Base64.
    // The private member fields below are used with the new streaming approach,
    // which requires
    // some state be preserved between calls of encode() and decode().

    /**
     * Encode table to use: either STANDARD or URL_SAFE. Note: the DECODE_TABLE
     * above remains static because it is able to decode both STANDARD and
     * URL_SAFE streams, but the encodeTable must be a member variable so we can
     * switch between the two modes.
     */
    private final byte[] encodeTable;

    // Only one decode table currently; keep for consistency with Base32 code
    private final byte[] decodeTable = DECODE_TABLE;

    /**
     * Line separator for encoding. Not used when decoding. Only used if
     * lineLength > 0.
     */
    private final byte[] lineSeparator;

    /**
     * Convenience variable to help us determine when our buffer is going to run
     * out of room and needs resizing.
     * <code>decodeSize = 3 + lineSeparator.length;</code>
     */
    private final int decodeSize;

    /**
     * Convenience variable to help us determine when our buffer is going to run
     * out of room and needs resizing.
     * <code>encodeSize = 4 + lineSeparator.length;</code>
     */
    private final int encodeSize;

    /**
     * Creates a Base64 codec used for decoding (all modes) and encoding in
     * URL-unsafe mode.
     * <p>
     * When encoding the line length is 0 (no chunking), and the encoding table
     * is STANDARD_ENCODE_TABLE.
     * </p>
     * <p/>
     * <p>
     * When decoding all variants are supported.
     * </p>
     */
    public Base64() {
        this(0);
    }

    /**
     * Creates a Base64 codec used for decoding (all modes) and encoding in the
     * given URL-safe mode.
     * <p>
     * When encoding the line length is 76, the line separator is CRLF, and the
     * encoding table is STANDARD_ENCODE_TABLE.
     * </p>
     * <p/>
     * <p>
     * When decoding all variants are supported.
     * </p>
     *
     * @param urlSafe if {@code true}, URL-safe encoding is used. In most cases this
     *                should be set to {@code false}.
     * @since 1.4
     */
    public Base64(boolean urlSafe) {
        this(MIME_CHUNK_SIZE, CHUNK_SEPARATOR, urlSafe);
    }

    /**
     * Creates a Base64 codec used for decoding (all modes) and encoding in
     * URL-unsafe mode.
     * <p>
     * When encoding the line length is given in the constructor, the line
     * separator is CRLF, and the encoding table is STANDARD_ENCODE_TABLE.
     * </p>
     * <p>
     * Line lengths that aren't multiples of 4 will still essentially end up
     * being multiples of 4 in the encoded data.
     * </p>
     * <p>
     * When decoding all variants are supported.
     * </p>
     *
     * @param lineLength Each line of encoded data will be at most of the given length
     *                   (rounded down to nearest multiple of 4). If lineLength <= 0,
     *                   then the output will not be divided into lines (chunks).
     *                   Ignored when decoding.
     * @since 1.4
     */
    public Base64(int lineLength) {
        this(lineLength, CHUNK_SEPARATOR);
    }

    /**
     * Creates a Base64 codec used for decoding (all modes) and encoding in
     * URL-unsafe mode.
     * <p>
     * When encoding the line length and line separator are given in the
     * constructor, and the encoding table is STANDARD_ENCODE_TABLE.
     * </p>
     * <p>
     * Line lengths that aren't multiples of 4 will still essentially end up
     * being multiples of 4 in the encoded data.
     * </p>
     * <p>
     * When decoding all variants are supported.
     * </p>
     *
     * @param lineLength    Each line of encoded data will be at most of the given length
     *                      (rounded down to nearest multiple of 4). If lineLength <= 0,
     *                      then the output will not be divided into lines (chunks).
     *                      Ignored when decoding.
     * @param lineSeparator Each line of encoded data will end with this sequence of
     *                      bytes.
     * @throws IllegalArgumentException Thrown when the provided lineSeparator included some base64
     *                                  characters.
     * @since 1.4
     */
    public Base64(int lineLength, byte[] lineSeparator) {
        this(lineLength, lineSeparator, false);
    }

    /**
     * Creates a Base64 codec used for decoding (all modes) and encoding in
     * URL-unsafe mode.
     * <p>
     * When encoding the line length and line separator are given in the
     * constructor, and the encoding table is STANDARD_ENCODE_TABLE.
     * </p>
     * <p>
     * Line lengths that aren't multiples of 4 will still essentially end up
     * being multiples of 4 in the encoded data.
     * </p>
     * <p>
     * When decoding all variants are supported.
     * </p>
     *
     * @param lineLength    Each line of encoded data will be at most of the given length
     *                      (rounded down to nearest multiple of 4). If lineLength <= 0,
     *                      then the output will not be divided into lines (chunks).
     *                      Ignored when decoding.
     * @param lineSeparator Each line of encoded data will end with this sequence of
     *                      bytes.
     * @param urlSafe       Instead of emitting '+' and '/' we emit '-' and '_'
     *                      respectively. urlSafe is only applied to encode operations.
     *                      Decoding seamlessly handles both modes.
     * @throws IllegalArgumentException The provided lineSeparator included some base64 characters.
     *                                  That's not going to work!
     * @since 1.4
     */
    public Base64(int lineLength, byte[] lineSeparator, boolean urlSafe) {

        this.unencodedBlockSize = BYTES_PER_UNENCODED_BLOCK;
        this.encodedBlockSize = BYTES_PER_ENCODED_BLOCK;

        int chunkSeparatorLength = lineSeparator == null ? 0 : lineSeparator.length;
        final boolean useChunking = lineLength > 0 && chunkSeparatorLength > 0;
        this.lineLength = useChunking ? (lineLength / encodedBlockSize) * encodedBlockSize : 0;
        this.chunkSeparatorLength = chunkSeparatorLength;

        // could be simplified if there is no requirement to reject invalid
        // line sep when length <=0
        // @see test case Base64Test.testConstructors()
        if (lineSeparator != null) {
            if (containsAlphabetOrPad(lineSeparator)) {
                String sep = newStringUtf8(lineSeparator);
                throw new IllegalArgumentException(
                        "lineSeparator must not contain base64 characters: [" + sep + "]");
            }
            if (lineLength > 0) { // null line-sep forces no chunking rather
                // than throwing IAE
                this.encodeSize = BYTES_PER_ENCODED_BLOCK + lineSeparator.length;
                this.lineSeparator = new byte[lineSeparator.length];
                System.arraycopy(lineSeparator, 0, this.lineSeparator, 0, lineSeparator.length);
            } else {
                this.encodeSize = BYTES_PER_ENCODED_BLOCK;
                this.lineSeparator = null;
            }
        } else {
            this.encodeSize = BYTES_PER_ENCODED_BLOCK;
            this.lineSeparator = null;
        }
        this.decodeSize = this.encodeSize - 1;
        this.encodeTable = urlSafe ? URL_SAFE_ENCODE_TABLE : STANDARD_ENCODE_TABLE;
    }

    /**
     * Returns true if this object has buffered data for reading.
     *
     * @param context the context to be used
     * @return true if there is data still available for reading.
     */
    boolean hasData(Context context) { // package protected for access from I/O
        // streams
        return context.buffer != null;
    }

    /**
     * Returns the amount of buffered data available for reading.
     *
     * @param context the context to be used
     * @return The amount of buffered data available for reading.
     */
    int available(Context context) { // package protected for access from I/O
        // streams
        return context.buffer != null ? context.pos - context.readPos : 0;
    }

    /**
     * Get the default buffer size. Can be overridden.
     *
     * @return {@link #DEFAULT_BUFFER_SIZE}
     */
    protected int getDefaultBufferSize() {
        return DEFAULT_BUFFER_SIZE;
    }

    /**
     * Increases our buffer by the {@link #DEFAULT_BUFFER_RESIZE_FACTOR}.
     *
     * @param context the context to be used
     */
    private byte[] resizeBuffer(Context context) {
        if (context.buffer == null) {
            context.buffer = new byte[getDefaultBufferSize()];
            context.pos = 0;
            context.readPos = 0;
        } else {
            byte[] b = new byte[context.buffer.length * DEFAULT_BUFFER_RESIZE_FACTOR];
            System.arraycopy(context.buffer, 0, b, 0, context.buffer.length);
            context.buffer = b;
        }
        return context.buffer;
    }

    /**
     * Ensure that the buffer has room for <code>size</code> bytes
     *
     * @param size    minimum spare space required
     * @param context the context to be used
     */
    protected byte[] ensureBufferSize(int size, Context context) {
        if ((context.buffer == null) || (context.buffer.length < context.pos + size)) {
            return resizeBuffer(context);
        }
        return context.buffer;
    }

    /**
     * Extracts buffered data into the provided byte[] array, starting at
     * position bPos, up to a maximum of bAvail bytes. Returns how many bytes
     * were actually extracted.
     * <p/>
     * Package protected for access from I/O streams.
     *
     * @param b       byte[] array to extract the buffered data into.
     * @param bPos    position in byte[] array to start extraction at.
     * @param bAvail  amount of bytes we're allowed to extract. We may extract fewer
     *                (if fewer are available).
     * @param context the context to be used
     * @return The number of bytes successfully extracted into the provided
     * byte[] array.
     */
    int readResults(byte[] b, int bPos, int bAvail, Context context) {
        if (context.buffer != null) {
            int len = Math.min(available(context), bAvail);
            System.arraycopy(context.buffer, context.readPos, b, bPos, len);
            context.readPos += len;
            if (context.readPos >= context.pos) {
                context.buffer = null; // so hasData() will return false, and
                // this method can return -1
            }
            return len;
        }
        return context.eof ? EOF : 0;
    }

    /**
     * Checks if a byte value is whitespace or not. Whitespace is taken to mean:
     * space, tab, CR, LF
     *
     * @param byteToCheck the byte to check
     * @return true if byte is whitespace, false otherwise
     */
    protected static boolean isWhiteSpace(byte byteToCheck) {
        switch (byteToCheck) {
            case ' ':
            case '\n':
            case '\r':
            case '\t':
                return true;
            default:
                return false;
        }
    }

    /**
     * Encodes an Object using the Base-N algorithm. This method is provided in
     * order to satisfy the requirements of the Encoder interface, and will
     * throw an EncoderException if the supplied object is not of type byte[].
     *
     * @param obj Object to encode
     * @return An object (of type byte[]) containing the Base-N encoded data
     * which corresponds to the byte[] supplied.
     * @throws Exception if the parameter supplied is not of type byte[]
     */
    public Object encode(Object obj) throws Exception {
        if (!(obj instanceof byte[])) {
            throw new Exception("Parameter supplied to Base-N encode is not a byte[]");
        }
        return encode((byte[]) obj);
    }

    /**
     * Encodes a byte[] containing binary data, into a String containing
     * characters in the Base-N alphabet. Uses UTF8 encoding.
     *
     * @param pArray a byte array containing binary data
     * @return A String containing only Base-N character data
     */
    public String encodeToString(byte[] pArray) {
        return newStringUtf8(encode(pArray));
    }

    /**
     * Encodes a byte[] containing binary data, into a String containing
     * characters in the appropriate alphabet. Uses UTF8 encoding.
     *
     * @param pArray a byte array containing binary data
     * @return String containing only character data in the appropriate
     * alphabet.
     */
    public String encodeAsString(byte[] pArray) {
        return newStringUtf8(encode(pArray));
    }

    /**
     * Decodes an Object using the Base-N algorithm. This method is provided in
     * order to satisfy the requirements of the Decoder interface, and will
     * throw a DecoderException if the supplied object is not of type byte[] or
     * String.
     *
     * @param obj Object to decode
     * @return An object (of type byte[]) containing the binary data which
     * corresponds to the byte[] or String supplied.
     * @throws Exception if the parameter supplied is not of type byte[]
     */
    public Object decode(Object obj) throws Exception {
        if (obj instanceof byte[]) {
            return decode((byte[]) obj);
        } else if (obj instanceof String) {
            return decode((String) obj);
        } else {
            throw new Exception("Parameter supplied to Base-N decode is not a byte[] or a String");
        }
    }

    /**
     * Decodes a String containing characters in the Base-N alphabet.
     *
     * @param pArray A String containing Base-N character data
     * @return a byte array containing binary data
     */
    public byte[] decode(String pArray) {
        return decode(getBytesUtf8(pArray));
    }

    /**
     * Decodes a byte[] containing characters in the Base-N alphabet.
     *
     * @param pArray A byte array containing Base-N character data
     * @return a byte array containing binary data
     */
    public byte[] decode(byte[] pArray) {
        if (pArray == null || pArray.length == 0) {
            return pArray;
        }
        Context context = new Context();
        decode(pArray, 0, pArray.length, context);
        decode(pArray, 0, EOF, context); // Notify decoder of EOF.
        byte[] result = new byte[context.pos];
        readResults(result, 0, result.length, context);
        return result;
    }

    /**
     * Encodes a byte[] containing binary data, into a byte[] containing
     * characters in the alphabet.
     *
     * @param pArray a byte array containing binary data
     * @return A byte array containing only the basen alphabetic character data
     */
    public byte[] encode(byte[] pArray) {
        if (pArray == null || pArray.length == 0) {
            return pArray;
        }
        Context context = new Context();
        encode(pArray, 0, pArray.length, context);
        encode(pArray, 0, EOF, context); // Notify encoder of EOF.
        byte[] buf = new byte[context.pos - context.readPos];
        readResults(buf, 0, buf.length, context);
        return buf;
    }

    /**
     * Tests a given byte array to see if it contains only valid characters
     * within the alphabet. The method optionally treats whitespace and pad as
     * valid.
     *
     * @param arrayOctet byte array to test
     * @param allowWSPad if {@code true}, then whitespace and PAD are also allowed
     * @return {@code true} if all bytes are valid characters in the alphabet or
     * if the byte array is empty; {@code false}, otherwise
     */
    public boolean isInAlphabet(byte[] arrayOctet, boolean allowWSPad) {
        for (int i = 0; i < arrayOctet.length; i++) {
            if (!isInAlphabet(arrayOctet[i])
                    && (!allowWSPad || (arrayOctet[i] != PAD) && !isWhiteSpace(arrayOctet[i]))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tests a given String to see if it contains only valid characters within
     * the alphabet. The method treats whitespace and PAD as valid.
     *
     * @param basen String to test
     * @return {@code true} if all characters in the String are valid characters
     * in the alphabet or if the String is empty; {@code false},
     * otherwise
     * @see #isInAlphabet(byte[], boolean)
     */
    public boolean isInAlphabet(String basen) {
        return isInAlphabet(getBytesUtf8(basen), true);
    }

    /**
     * Tests a given byte array to see if it contains any characters within the
     * alphabet or PAD.
     * <p/>
     * Intended for use in checking line-ending arrays
     *
     * @param arrayOctet byte array to test
     * @return {@code true} if any byte is a valid character in the alphabet or
     * PAD; {@code false} otherwise
     */
    protected boolean containsAlphabetOrPad(byte[] arrayOctet) {
        if (arrayOctet == null) {
            return false;
        }
        for (byte element : arrayOctet) {
            if (PAD == element || isInAlphabet(element)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calculates the amount of space needed to encode the supplied array.
     *
     * @param pArray byte[] array which will later be encoded
     * @return amount of space needed to encoded the supplied array. Returns a
     * long since a max-len array will require > Integer.MAX_VALUE
     */
    public long getEncodedLength(byte[] pArray) {
        // Calculate non-chunked size - rounded up to allow for padding
        // cast to long is needed to avoid possibility of overflow
        long len = ((pArray.length + unencodedBlockSize - 1) / unencodedBlockSize)
                * (long) encodedBlockSize;
        if (lineLength > 0) { // We're using chunking
            // Round up to nearest multiple
            len += ((len + lineLength - 1) / lineLength) * chunkSeparatorLength;
        }
        return len;
    }

    /**
     * Returns our current encode mode. True if we're URL-SAFE, false otherwise.
     *
     * @return true if we're in URL-SAFE mode, false otherwise.
     * @since 1.4
     */
    public boolean isUrlSafe() {
        return this.encodeTable == URL_SAFE_ENCODE_TABLE;
    }

    /**
     * <p>
     * Encodes all of the provided data, starting at inPos, for inAvail bytes.
     * Must be called at least twice: once with the data to encode, and once
     * with inAvail set to "-1" to alert encoder that EOF has been reached, so
     * flush last remaining bytes (if not multiple of 3).
     * </p>
     * <p>
     * Thanks to "commons" project in ws.apache.org for the bitwise operations,
     * and general approach.
     * http://svn.apache.org/repos/asf/webservices/commons/trunk/modules/util/
     * </p>
     *
     * @param in      byte[] array of binary data to base64 encode.
     * @param inPos   Position to start reading data from.
     * @param inAvail Amount of bytes available from input for encoding.
     * @param context the context to be used
     */
    void encode(byte[] in, int inPos, int inAvail, Context context) {
        if (context.eof) {
            return;
        }
        // inAvail < 0 is how we're informed of EOF in the underlying data we're
        // encoding.
        if (inAvail < 0) {
            context.eof = true;
            if (0 == context.modulus && lineLength == 0) {
                return; // no leftovers to process and not using chunking
            }
            final byte[] buffer = ensureBufferSize(encodeSize, context);
            final int savedPos = context.pos;
            switch (context.modulus) { // 0-2
                case 0: // nothing to do here
                    break;
                case 1: // 8 bits = 6 + 2
                    // top 6 bits:
                    buffer[context.pos++] = encodeTable[(context.ibitWorkArea >> 2) & MASK_6BITS];
                    // remaining 2:
                    buffer[context.pos++] = encodeTable[(context.ibitWorkArea << 4) & MASK_6BITS];
                    // URL-SAFE skips the padding to further reduce size.
                    if (encodeTable == STANDARD_ENCODE_TABLE) {
                        buffer[context.pos++] = PAD;
                        buffer[context.pos++] = PAD;
                    }
                    break;

                case 2: // 16 bits = 6 + 6 + 4
                    buffer[context.pos++] = encodeTable[(context.ibitWorkArea >> 10) & MASK_6BITS];
                    buffer[context.pos++] = encodeTable[(context.ibitWorkArea >> 4) & MASK_6BITS];
                    buffer[context.pos++] = encodeTable[(context.ibitWorkArea << 2) & MASK_6BITS];
                    // URL-SAFE skips the padding to further reduce size.
                    if (encodeTable == STANDARD_ENCODE_TABLE) {
                        buffer[context.pos++] = PAD;
                    }
                    break;
                default:
                    throw new IllegalStateException("Impossible modulus " + context.modulus);
            }
            context.currentLinePos += context.pos - savedPos; // keep track of
            // current line
            // position
            // if currentPos == 0 we are at the start of a line, so don't add
            // CRLF
            if (lineLength > 0 && context.currentLinePos > 0) {
                System.arraycopy(lineSeparator, 0, buffer, context.pos, lineSeparator.length);
                context.pos += lineSeparator.length;
            }
        } else {
            for (int i = 0; i < inAvail; i++) {
                final byte[] buffer = ensureBufferSize(encodeSize, context);
                context.modulus = (context.modulus + 1) % BYTES_PER_UNENCODED_BLOCK;
                int b = in[inPos++];
                if (b < 0) {
                    b += 256;
                }
                context.ibitWorkArea = (context.ibitWorkArea << 8) + b; // BITS_PER_BYTE
                if (0 == context.modulus) { // 3 bytes = 24 bits = 4 * 6 bits to
                    // extract
                    buffer[context.pos++] = encodeTable[(context.ibitWorkArea >> 18) & MASK_6BITS];
                    buffer[context.pos++] = encodeTable[(context.ibitWorkArea >> 12) & MASK_6BITS];
                    buffer[context.pos++] = encodeTable[(context.ibitWorkArea >> 6) & MASK_6BITS];
                    buffer[context.pos++] = encodeTable[context.ibitWorkArea & MASK_6BITS];
                    context.currentLinePos += BYTES_PER_ENCODED_BLOCK;
                    if (lineLength > 0 && lineLength <= context.currentLinePos) {
                        System.arraycopy(lineSeparator, 0, buffer, context.pos,
                                lineSeparator.length);
                        context.pos += lineSeparator.length;
                        context.currentLinePos = 0;
                    }
                }
            }
        }
    }

    /**
     * <p>
     * Decodes all of the provided data, starting at inPos, for inAvail bytes.
     * Should be called at least twice: once with the data to decode, and once
     * with inAvail set to "-1" to alert decoder that EOF has been reached. The
     * "-1" call is not necessary when decoding, but it doesn't hurt, either.
     * </p>
     * <p>
     * Ignores all non-base64 characters. This is how chunked (e.g. 76
     * character) data is handled, since CR and LF are silently ignored, but has
     * implications for other bytes, too. This method subscribes to the
     * garbage-in, garbage-out philosophy: it will not check the provided data
     * for validity.
     * </p>
     * <p>
     * Thanks to "commons" project in ws.apache.org for the bitwise operations,
     * and general approach.
     * http://svn.apache.org/repos/asf/webservices/commons/trunk/modules/util/
     * </p>
     *
     * @param in      byte[] array of ascii data to base64 decode.
     * @param inPos   Position to start reading data from.
     * @param inAvail Amount of bytes available from input for encoding.
     * @param context the context to be used
     */
    void decode(byte[] in, int inPos, int inAvail, Context context) {
        if (context.eof) {
            return;
        }
        if (inAvail < 0) {
            context.eof = true;
        }
        for (int i = 0; i < inAvail; i++) {
            final byte[] buffer = ensureBufferSize(decodeSize, context);
            final byte b = in[inPos++];
            if (b == PAD) {
                // We're done.
                context.eof = true;
                break;
            } else {
                if (b >= 0 && b < DECODE_TABLE.length) {
                    final int result = DECODE_TABLE[b];
                    if (result >= 0) {
                        context.modulus = (context.modulus + 1) % BYTES_PER_ENCODED_BLOCK;
                        context.ibitWorkArea = (context.ibitWorkArea << BITS_PER_ENCODED_BYTE)
                                + result;
                        if (context.modulus == 0) {
                            buffer[context.pos++] = (byte) ((context.ibitWorkArea >> 16) & MASK_8BITS);
                            buffer[context.pos++] = (byte) ((context.ibitWorkArea >> 8) & MASK_8BITS);
                            buffer[context.pos++] = (byte) (context.ibitWorkArea & MASK_8BITS);
                        }
                    }
                }
            }
        }

        // Two forms of EOF as far as base64 decoder is concerned: actual
        // EOF (-1) and first time '=' character is encountered in stream.
        // This approach makes the '=' padding characters completely optional.
        if (context.eof && context.modulus != 0) {
            final byte[] buffer = ensureBufferSize(decodeSize, context);

            // We have some spare bits remaining
            // Output all whole multiples of 8 bits and ignore the rest
            switch (context.modulus) {
                // case 0 : // impossible, as excluded above
                case 1: // 6 bits - ignore entirely
                    // not currently tested; perhaps it is impossible?
                    break;
                case 2: // 12 bits = 8 + 4
                    context.ibitWorkArea = context.ibitWorkArea >> 4; // dump the
                    // extra 4
                    // bits
                    buffer[context.pos++] = (byte) ((context.ibitWorkArea) & MASK_8BITS);
                    break;
                case 3: // 18 bits = 8 + 8 + 2
                    context.ibitWorkArea = context.ibitWorkArea >> 2; // dump 2 bits
                    buffer[context.pos++] = (byte) ((context.ibitWorkArea >> 8) & MASK_8BITS);
                    buffer[context.pos++] = (byte) ((context.ibitWorkArea) & MASK_8BITS);
                    break;
                default:
                    throw new IllegalStateException("Impossible modulus " + context.modulus);
            }
        }
    }

    /**
     * Tests a given byte array to see if it contains only valid characters
     * within the Base64 alphabet. Currently the method treats whitespace as
     * valid.
     *
     * @param arrayOctet byte array to test
     * @return {@code true} if all bytes are valid characters in the Base64
     * alphabet or if the byte array is empty; {@code false}, otherwise
     * @deprecated 1.5 Use {@link #isBase64(byte[])}, will be removed in 2.0.
     */
    @Deprecated
    public static boolean isArrayByteBase64(byte[] arrayOctet) {
        return isBase64(arrayOctet);
    }

    /**
     * Returns whether or not the <code>octet</code> is in the base 64 alphabet.
     *
     * @param octet The value to test
     * @return {@code true} if the value is defined in the the base 64 alphabet,
     * {@code false} otherwise.
     * @since 1.4
     */
    public static boolean isBase64(byte octet) {
        return octet == PAD_DEFAULT
                || (octet >= 0 && octet < DECODE_TABLE.length && DECODE_TABLE[octet] != -1);
    }

    /**
     * Tests a given String to see if it contains only valid characters within
     * the Base64 alphabet. Currently the method treats whitespace as valid.
     *
     * @param base64 String to test
     * @return {@code true} if all characters in the String are valid characters
     * in the Base64 alphabet or if the String is empty; {@code false},
     * otherwise
     * @since 1.5
     */
    public static boolean isBase64(String base64) {
        return isBase64(getBytesUtf8(base64));
    }

    /**
     * Tests a given byte array to see if it contains only valid characters
     * within the Base64 alphabet. Currently the method treats whitespace as
     * valid.
     *
     * @param arrayOctet byte array to test
     * @return {@code true} if all bytes are valid characters in the Base64
     * alphabet or if the byte array is empty; {@code false}, otherwise
     * @since 1.5
     */
    public static boolean isBase64(byte[] arrayOctet) {
        for (int i = 0; i < arrayOctet.length; i++) {
            if (!isBase64(arrayOctet[i]) && !isWhiteSpace(arrayOctet[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Encodes binary data using the base64 algorithm but does not chunk the
     * output.
     *
     * @param binaryData binary data to encode
     * @return byte[] containing Base64 characters in their UTF-8
     * representation.
     */
    public static byte[] encodeBase64(byte[] binaryData) {
        return encodeBase64(binaryData, false);
    }

    /**
     * Encodes binary data using the base64 algorithm but does not chunk the
     * output.
     * <p/>
     * NOTE: We changed the behaviour of this method from multi-line chunking
     * (commons-codec-1.4) to single-line non-chunking (commons-codec-1.5).
     *
     * @param binaryData binary data to encode
     * @return String containing Base64 characters.
     * @since 1.4 (NOTE: 1.4 chunked the output, whereas 1.5 does not).
     */
    public static String encodeBase64String(byte[] binaryData) {
        return newStringUtf8(encodeBase64(binaryData, false));
    }

    /**
     * Encodes binary data using a URL-safe variation of the base64 algorithm
     * but does not chunk the output. The url-safe variation emits - and _
     * instead of + and / characters.
     *
     * @param binaryData binary data to encode
     * @return byte[] containing Base64 characters in their UTF-8
     * representation.
     * @since 1.4
     */
    public static byte[] encodeBase64URLSafe(byte[] binaryData) {
        return encodeBase64(binaryData, false, true);
    }

    /**
     * Encodes binary data using a URL-safe variation of the base64 algorithm
     * but does not chunk the output. The url-safe variation emits - and _
     * instead of + and / characters.
     *
     * @param binaryData binary data to encode
     * @return String containing Base64 characters
     * @since 1.4
     */
    public static String encodeBase64URLSafeString(byte[] binaryData) {
        return newStringUtf8(encodeBase64(binaryData, false, true));
    }

    private static String newStringUtf8(byte bytes[]) {
        try {
            return new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return new String(bytes);
        }
    }

    private static byte[] getBytesUtf8(String s) {
        try {
            return s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s.getBytes();
        }
    }

    /**
     * Encodes binary data using the base64 algorithm and chunks the encoded
     * output into 76 character blocks
     *
     * @param binaryData binary data to encode
     * @return Base64 characters chunked in 76 character blocks
     */
    public static byte[] encodeBase64Chunked(byte[] binaryData) {
        return encodeBase64(binaryData, true);
    }

    /**
     * Encodes binary data using the base64 algorithm, optionally chunking the
     * output into 76 character blocks.
     *
     * @param binaryData Array containing binary data to encode.
     * @param isChunked  if {@code true} this encoder will chunk the base64 output into
     *                   76 character blocks
     * @return Base64-encoded data.
     * @throws IllegalArgumentException Thrown when the input array needs an output array bigger than
     *                                  {@link Integer#MAX_VALUE}
     */
    public static byte[] encodeBase64(byte[] binaryData, boolean isChunked) {
        return encodeBase64(binaryData, isChunked, false);
    }

    /**
     * Encodes binary data using the base64 algorithm, optionally chunking the
     * output into 76 character blocks.
     *
     * @param binaryData Array containing binary data to encode.
     * @param isChunked  if {@code true} this encoder will chunk the base64 output into
     *                   76 character blocks
     * @param urlSafe    if {@code true} this encoder will emit - and _ instead of the
     *                   usual + and / characters.
     * @return Base64-encoded data.
     * @throws IllegalArgumentException Thrown when the input array needs an output array bigger than
     *                                  {@link Integer#MAX_VALUE}
     * @since 1.4
     */
    public static byte[] encodeBase64(byte[] binaryData, boolean isChunked, boolean urlSafe) {
        return encodeBase64(binaryData, isChunked, urlSafe, Integer.MAX_VALUE);
    }

    /**
     * Encodes binary data using the base64 algorithm, optionally chunking the
     * output into 76 character blocks.
     *
     * @param binaryData    Array containing binary data to encode.
     * @param isChunked     if {@code true} this encoder will chunk the base64 output into
     *                      76 character blocks
     * @param urlSafe       if {@code true} this encoder will emit - and _ instead of the
     *                      usual + and / characters.
     * @param maxResultSize The maximum result size to accept.
     * @return Base64-encoded data.
     * @throws IllegalArgumentException Thrown when the input array needs an output array bigger than
     *                                  maxResultSize
     * @since 1.4
     */
    public static byte[] encodeBase64(byte[] binaryData, boolean isChunked, boolean urlSafe,
                                      int maxResultSize) {
        if (binaryData == null || binaryData.length == 0) {
            return binaryData;
        }

        // Create this so can use the super-class method
        // Also ensures that the same roundings are performed by the ctor and
        // the code
        Base64 b64 = isChunked ? new Base64(urlSafe) : new Base64(0, CHUNK_SEPARATOR, urlSafe);
        long len = b64.getEncodedLength(binaryData);
        if (len > maxResultSize) {
            throw new IllegalArgumentException(
                    "Input array too big, the output array would be bigger (" + len
                            + ") than the specified maximum size of " + maxResultSize);
        }

        return b64.encode(binaryData);
    }

    /**
     * Decodes a Base64 String into octets
     *
     * @param base64String String containing Base64 data
     * @return Array containing decoded data.
     * @since 1.4
     */
    public static byte[] decodeBase64(String base64String) {
        return new Base64().decode(base64String);
    }

    /**
     * Decodes Base64 data into octets
     *
     * @param base64Data Byte array containing Base64 data
     * @return Array containing decoded data.
     */
    public static byte[] decodeBase64(byte[] base64Data) {
        return new Base64().decode(base64Data);
    }

    // Implementation of the Encoder Interface

    // Implementation of integer encoding used for crypto

    /**
     * Decodes a byte64-encoded integer according to crypto standards such as
     * W3C's XML-Signature
     *
     * @param pArray a byte array containing base64 character data
     * @return A BigInteger
     * @since 1.4
     */
    public static BigInteger decodeInteger(byte[] pArray) {
        return new BigInteger(1, decodeBase64(pArray));
    }

    /**
     * Encodes to a byte64-encoded integer according to crypto standards such as
     * W3C's XML-Signature
     *
     * @param bigInt a BigInteger
     * @return A byte array containing base64 character data
     * @throws NullPointerException if null is passed in
     * @since 1.4
     */
    public static byte[] encodeInteger(BigInteger bigInt) {
        if (bigInt == null) {
            throw new NullPointerException("encodeInteger called with null parameter");
        }
        return encodeBase64(toIntegerBytes(bigInt), false);
    }

    /**
     * Returns a byte-array representation of a <code>BigInteger</code> without
     * sign bit.
     *
     * @param bigInt <code>BigInteger</code> to be converted
     * @return a byte array representation of the BigInteger parameter
     */
    static byte[] toIntegerBytes(BigInteger bigInt) {
        int bitlen = bigInt.bitLength();
        // round bitlen
        bitlen = ((bitlen + 7) >> 3) << 3;
        byte[] bigBytes = bigInt.toByteArray();

        if (((bigInt.bitLength() % 8) != 0) && (((bigInt.bitLength() / 8) + 1) == (bitlen / 8))) {
            return bigBytes;
        }
        // set up params for copying everything but sign bit
        int startSrc = 0;
        int len = bigBytes.length;

        // if bigInt is exactly byte-aligned, just skip signbit in copy
        if ((bigInt.bitLength() % 8) == 0) {
            startSrc = 1;
            len--;
        }
        int startDst = bitlen / 8 - len; // to pad w/ nulls as per spec
        byte[] resizedBytes = new byte[bitlen / 8];
        System.arraycopy(bigBytes, startSrc, resizedBytes, startDst, len);
        return resizedBytes;
    }

    /**
     * Returns whether or not the <code>octet</code> is in the Base32 alphabet.
     *
     * @param octet The value to test
     * @return {@code true} if the value is defined in the the Base32 alphabet
     * {@code false} otherwise.
     */
    protected boolean isInAlphabet(byte octet) {
        return octet >= 0 && octet < decodeTable.length && decodeTable[octet] != -1;
    }
}
