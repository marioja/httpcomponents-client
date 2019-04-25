package org.apache.http.impl.conn;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.UnmappableCharacterException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This class is thread safe.
 * @author MXJ037
 *
 */
public class CharsetStreamSupport {

	public final Log logger;
	private final String charsetName;
	private ThreadLocal<CharsetDecoder> csDecoder=new ThreadLocal<CharsetDecoder>() {
		@Override protected CharsetDecoder initialValue() {
			return charset.newDecoder();
		}
	};
	private final Charset charset;
	public Charset getCharset() {
		return charset;
	}
	private final ThreadLocal<Boolean> initialized=new ThreadLocal<Boolean>() {
		@Override protected Boolean initialValue() {
			return false;
		}
	};
	
	public CharsetStreamSupport(String aCharsetName) {
		this(aCharsetName, LogFactory.getLog(CharsetStreamSupport.class));
	}
	public CharsetStreamSupport(String aCharsetName, Log aLogger) {
		this.charsetName=aCharsetName;
		this.logger=aLogger;
		charset=Charset.forName(charsetName);
	}
	public CoderResult decode(CharBuffer cb, ByteBuffer bb, boolean eoi) {
		if (!initialized.get()) {
			csDecoder.get().reset();
			initialized.set(true);
		}
		CoderResult res = csDecoder.get().decode(bb, cb, eoi);
		if (eoi) {
			csDecoder.get().flush(cb);
			cb.flip();
			initialized.set(false);
		}
		return res;
	}
	/**
	 * main3
	 * @param args
	 * @throws UnsupportedEncodingException
	 * @throws CharacterCodingException
	 */
	public static void main3(String[] args) throws UnsupportedEncodingException, CharacterCodingException {
		byte[] a=new byte[] {(byte) 0x80,(byte) 0x31,(byte) 0x80,(byte) 0x32,(byte) 0x80,(byte) 0x33};
		String ts="€1€2€3";
		ts="élè€ve";
		final Charset cs=Charset.forName("ISO-8859-1");
		CharsetEncoder encoder = cs.newEncoder();
		encoder.reset();
		CharBuffer ochars=CharBuffer.wrap(ts);
		System.out.println(displayBuffer(ochars, "ochars"));
		ochars.rewind();
		ByteBuffer ret = null;
		try {
			ret = encoder.encode(ochars);
		} catch (UnmappableCharacterException e) {
			throw new IllegalArgumentException(MessageFormat.format("The character ''{0}'' at position {1} could not be mapped", ochars.get(ochars.position()), ochars.position()), e);
		}
		byte[] encoded=ret.array();
		System.out.println("encoded="+Arrays.toString((encoded)));
		System.out.println("encoded="+toString(toHex(encoded)));
		System.out.println("encoded="+toBinary(encoded));
		System.out.println("a="+Arrays.toString((a)));
		System.out.println("a="+toBinary(a));
		System.out.println(new String(encoded,"ISO-8859-1"));
	}
	private static final String FORMAT="{0},\"{1}\",{2},{3},\"{4}\"";
	private static final String FORMAT2="{},\"{}\",{},{}";

	/**
	 * main2
	 * This is from this stackoverflow:
	 * https://stackoverflow.com/a/29560129
	 * This shows how to call decode with false as end of input argument
	 * @param args
	 * @throws IOException 
	 */
	public static void main2(String[] args) throws IOException {
		final String pound="€1€2€3";
		final Charset charset=Charset.forName("ISO-8859-1");
		final byte[] tab = pound.getBytes(charset); //char €
	    final int maxi=tab.length-1;
		final CharsetDecoder dec = charset.newDecoder();
		final int charsLen=(int)(dec.maxCharsPerByte() * (double)tab.length);
		final CharBuffer chars = CharBuffer.allocate(charsLen);
		final PrintWriter csv=new PrintWriter(new FileWriter(new File("results.csv")));
//		logger.info("byte[]={}, chars length={} maxCharsPerByte={}, maxi={}", toString(toHex(tab)), charsLen, dec.maxCharsPerByte(), maxi);
//		logger.info("Buffer=position/remaining/limit/capacity");
		Stream.of(new String[]{"i","when","iposition","iremaining","ilimit","icapacity","oposition","oremaining","olimit","ocapacity","result"}).forEach(header -> csv.append('"').append(header).append('"').append(','));;
		csv.append("\n");

		final ByteBuffer buffer = ByteBuffer.allocate(tab.length);

		for (int i = 0; i < tab.length; i++) {
		    csv.println(MessageFormat.format(FORMAT, i, "before put", csvDisplayBuffer(buffer),csvDisplayBuffer(chars),""));
		    // Add the next byte to the buffer
		    buffer.put(tab[i]);

		    // Remember the current position
		    final int pos = buffer.position();
//		    logger.info(FORMAT2, i, "after put", displayBuffer(buffer, "buffer"),displayBuffer(chars, "chars"));
		    csv.println(MessageFormat.format(FORMAT, i, "after put", csvDisplayBuffer(buffer),csvDisplayBuffer(chars),""));

		    // Try to decode
		    buffer.flip();
		    boolean lastLoop=i == maxi;
//		    logger.info(FORMAT2, i, "after flip", displayBuffer(buffer, "buffer"),displayBuffer(chars, "chars"));
		    csv.println(MessageFormat.format(FORMAT, i, "after flip", csvDisplayBuffer(buffer),csvDisplayBuffer(chars),""));

		    final CoderResult result = dec.decode(buffer, chars, lastLoop);
//		    logger.info(FORMAT2, i, "after decode", displayBuffer(buffer, "buffer"),displayBuffer(chars, "chars"));
		    csv.println(MessageFormat.format(FORMAT, i, "after decode", csvDisplayBuffer(buffer),csvDisplayBuffer(chars), result));
//			logger.info("result={} lastLoop={} decoded=\"{}\"", result, lastLoop, displayBufferContent(chars));

		    if (result.isUnderflow()) {
		        // Underflow, prepare the buffer for more writing
		    	if (!lastLoop) chars.rewind();
		        buffer.limit(buffer.capacity());
		        buffer.position(pos);
		    }
		}

		dec.flush(chars);
		chars.flip();
//		logger.info("decoded=\"{}\"", new String(chars.array()));
//		logger.info("decoded=\"{}\"", new String(tab, charset));
		csv.close();
		
	}
	private static String displayBuffer(Buffer aBuffer, String name) {
		return MessageFormat.format("\"{0}\",{1},{2},{3},{4}", name, aBuffer.position(), aBuffer.remaining(), aBuffer.limit(), aBuffer.capacity());
	}
	
	private static String csvDisplayBuffer(Buffer aBuffer) {
		return MessageFormat.format("{0},{1},{2},{3}", aBuffer.position(), aBuffer.remaining(), aBuffer.limit(), aBuffer.capacity());
	}
	
	public float averageCharsPerByte() {
		return csDecoder.get().averageCharsPerByte();
	}
	
	private static String displayBufferContent(Buffer aBuffer) {
		int limit=aBuffer.limit();
		int pos=aBuffer.position();
		int remaining=aBuffer.remaining();
		if (pos!=0) aBuffer.flip();
		String ret="";
		if (aBuffer instanceof CharBuffer) {
			CharBuffer cBuffer=(CharBuffer)aBuffer;
			ret=cBuffer.toString();
		} else {
			ret=aBuffer.toString();
		}
		aBuffer.limit(limit);
		aBuffer.position(pos);
		if (remaining!=aBuffer.remaining()) throw new IllegalStateException("Unable to restore buffer after displaying its contents");
		return ret;
	}
	/**
	 * main1
	 * @param args
	 */
	/**
	 * main1
	 * This illustrates the following sequence
	 * <p>
	 * Step 1: decode 4 bytes from byte[].  This should decode the first two characters because byte is required to
	 * decode the third character.  Output in cb. Position in bb is at the fourth (unused) byte.
	 * <p>
	 * Step 2: decode remaining byte 4 onwards to complete the character string.
	 * <p>
	 * Step 1 has eoi false, step 2 has eoi true.
	 * 
	 * @param args
	 */
	public static void main1(String[] args) {
		int limit;
		int charSize;
		int byteSize;
		
		CharsetStreamSupport css = new CharsetStreamSupport("UTF-8");
		byte[] utf8bytes=new String("élèves").getBytes(css.getCharset());
		byteSize=utf8bytes.length;
//		logger.info(toString(toHex(utf8bytes)));
		charSize=(int)(((double)byteSize)*css.averageCharsPerByte());
		
		limit=byteSize;
		limit=4;
		
//		logger.info("Buffer=position/remaining/limit/capacity");
		
		CharBuffer cb=CharBuffer.allocate(charSize);
		ByteBuffer bb=ByteBuffer.wrap(utf8bytes);
		
		bb.limit(limit);
		
		CoderResult res = css.decode(cb, bb, limit==byteSize);
//	    logger.info(FORMAT2, 1, "after decode1", displayBuffer(bb, "bb"),displayBuffer(cb, "cb"));
		if (cb.hasArray()) {
//			logger.info("Decode string={}, eoi={}, result={}", displayBufferContent(cb), limit==byteSize, res);
		}
//		logger.info("CharBuffer size={}, byte[] size/limit={}/{}, ByteBuffer remaining/position={}/{}, result={}", charSize, byteSize, limit, bb.remaining(), 
//				bb.position(), res);
		
		limit=byteSize;
//		bb.rewind();
		bb.limit(limit);
//		cb.rewind();
		
//	    logger.info(FORMAT2, 2, "before decode2", displayBuffer(bb, "bb"),displayBuffer(cb, "cb"));
		res=css.decode(cb, bb, limit==byteSize);
//	    logger.info(FORMAT2, 2, "after decode2", displayBuffer(bb, "bb"),displayBuffer(cb, "cb"));
		if (cb.hasArray()) {
//			logger.info("Decode string={}, eoi={}, result={}", displayBufferContent(cb), limit==byteSize, res);
		}
//		logger.info("CharBuffer size={}, byte[] size/limit={}/{}, ByteBuffer remaining/position={}/{}, result={}", charSize, byteSize, limit, bb.remaining(), 
//				bb.position(), res);

	}
	public static void main5(String[] args) {
		int byteSize;
		
		CharsetStreamSupport css = new CharsetStreamSupport("UTF-8");
		byte[] utf8bytes=new String("élèves").getBytes(css.getCharset());
		byteSize=utf8bytes.length;

		CharBuffer cb=CharBuffer.allocate((int)(((double)byteSize)*css.averageCharsPerByte()));
		ByteBuffer bb=ByteBuffer.wrap(utf8bytes);
		
		// step 1
		bb.limit(4);		
		CoderResult res = css.decode(cb, bb, bb.limit()==byteSize);

		// step 2
		bb.limit(byteSize);
		res=css.decode(cb, bb, bb.limit()==byteSize);

	}
	
	public static void main4(String[] args) throws IOException {
		CharsetStreamSupport css = new CharsetStreamSupport("UTF-8");
		byte[] utf8bytes=new String("élèves").getBytes(css.getCharset());
		int byteSize=utf8bytes.length;
//		logger.info(toString(toHex(utf8bytes)));
		int charSize=(int)(((double)byteSize)*css.averageCharsPerByte());
		ByteArrayInputStream bais=new ByteArrayInputStream(utf8bytes);
		ReadableByteChannel channel = Channels.newChannel(bais);
		ByteBuffer bb=ByteBuffer.wrap(utf8bytes);
		ByteArrayOutputStream baos=new ByteArrayOutputStream();
		channel.read(bb);
		
	}
	public static void main(String[] args) {
		ByteBuffer bb=ByteBuffer.allocate(128);
		System.out.println(displayBuffer(bb, "bb"));
//		main5(args);
	}
	/**​
	 * Convert array of bytes to array of hexadecimal representation of byte.​
	 * For example, byte[-1, 4] will be converted to ​
	 * @param in​
	 * @return​
	 */
	public static String[] toHex(byte[] in) {
		Byte[] ino = new Byte[in.length];
		Arrays.setAll(ino, n -> in[n]);
		return Stream.of(ino).map(bo -> "0x" + new String(new char[] { Character.forDigit((bo >> 4) & 0xF, 16),
				Character.forDigit(bo & 0xF, 16) }).toUpperCase()).collect(Collectors.toList()).toArray(new String[] {});
	}
	
	public static String toBinary(byte[] encoded) {
		return IntStream.range(0, encoded.length).map(i -> encoded[i])
				.mapToObj(e -> Integer.toBinaryString(e ^ 255)).map(e -> String.format("%1$" + Byte.SIZE + "s", e)
				.replace(" ", "0")).collect(Collectors.joining(" "));
	}
	
	public static String toString(String[] strings) {
		return Arrays.toString(strings);
	}
}

	
