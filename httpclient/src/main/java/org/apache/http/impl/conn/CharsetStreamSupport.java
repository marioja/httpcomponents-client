package org.apache.http.impl.conn;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;


/**
 * This class is thread safe.
 * @author MXJ037
 *
 */
public class CharsetStreamSupport {

	private ThreadLocal<CharsetDecoder> csDecoder=new ThreadLocal<CharsetDecoder>();
	private ThreadLocal<ByteBuffer> bbinternal=new ThreadLocal<ByteBuffer>();
	private final ThreadLocal<Charset> charset=new ThreadLocal<Charset>();
	private final ThreadLocal<CoderResult> result=new ThreadLocal<CoderResult>();

	public CoderResult getCoderReult() {
		return result.get();
	}
	public Charset getCharset() {
		return charset.get();
	}
	private final ThreadLocal<Boolean> decoderInitialized=new ThreadLocal<Boolean>() {
		@Override protected Boolean initialValue() {
			return false;
		}
	};
	public void initialize(String aCharsetName, int capacity) {
		initialize(Charset.forName(aCharsetName), capacity);
	}
	public void initialize(Charset aCharset, int capacity) {
		charset.set(aCharset);
		bbinternal.set(ByteBuffer.allocate(capacity));
		csDecoder.set(charset.get().newDecoder());
	}
	
	public CharsetStreamSupport() {
	}
	public CoderResult decode2(ByteBuffer bb, CharBuffer cb, boolean eoi) {
		if (!decoderInitialized.get()) {
			csDecoder.get().reset();
			decoderInitialized.set(true);
		}
		CoderResult res = csDecoder.get().decode(bb, cb, eoi);
		if (eoi) {
			csDecoder.get().flush(cb);
			cb.flip();
			decoderInitialized.set(false);
		}
		return res;
	}
	public CharBuffer decode(byte[] bytes, boolean eoi) {
		return decode(ByteBuffer.wrap(bytes), eoi);
	}
	public CharBuffer decode(ByteBuffer inbb, boolean eoi) {
		if (bbinternal.get()==null) throw new IllegalStateException("You must initialize the CharsetStreamSupport before calling decode2");
		ByteBuffer bb = bbinternal.get();
		bb.put(inbb);
		bb.flip();
		CharBuffer cb=CharBuffer.allocate((int)(bb.capacity()*averageCharsPerByte()));
		
		result.set(decode2(bb, cb, eoi));
		if (!eoi) cb.flip();
		bb.compact();
		return cb;
	}
	public static void main7(String[] args) {
		CharsetStreamSupport css = new CharsetStreamSupport();
		css.initialize("UTF-8", 10);
		byte[] utf8bytes=new String("élèves").getBytes(css.getCharset());
		for (int ix = 0; ix < utf8bytes.length+1; ix++) {
			byte[] part1=Arrays.copyOf(utf8bytes, ix);
			byte[] part2=Arrays.copyOfRange(utf8bytes, ix, utf8bytes.length);
			CharBuffer cb;
			cb = css.decode(part1, false);
			CoderResult res1=css.getCoderReult();
			String decoded1=cb.toString();
			cb = css.decode(part2, true);
			System.out.println(MessageFormat.format("decoded={0}/{1} result={2}/{3}", decoded1,cb.toString(),res1, css.getCoderReult()));
		}
	}

	private static String displayBuffer(Buffer aBuffer, String name) {
		return MessageFormat.format("\"{0} p/r/l/c\",{1},{2},{3},{4}", name, aBuffer.position(), aBuffer.remaining(), aBuffer.limit(), aBuffer.capacity());
	}
	
	private static String csvDisplayBuffer(Buffer aBuffer) {
		return MessageFormat.format("{0},{1},{2},{3}", aBuffer.position(), aBuffer.remaining(), aBuffer.limit(), aBuffer.capacity());
	}
	
	private static String extendedDisplayBuffer(Buffer aBuffer, String name) {
		StringBuffer sb=new StringBuffer(3*aBuffer.capacity());
		sb.append(name).append(": ");
		if (aBuffer instanceof CharBuffer) {
			CharBuffer cb=(CharBuffer)aBuffer;
			sb.append(Arrays.toString(cb.array()));
		} else {
			ByteBuffer bb=(ByteBuffer)aBuffer;
			sb.append(toString(toHex(bb.array())));
		}
		sb.append("\n");
		sb.append("position=").append(aBuffer.position())
			.append(" remaining=").append(aBuffer.remaining())
			.append(" limit=").append(aBuffer.limit())
			.append(" capacity=").append(aBuffer.capacity())
			.append("\n");			
		return sb.toString();
	}
	
	public float averageCharsPerByte() {
		if (csDecoder.get()==null)  throw new IllegalStateException("You must initialize the CharsetStreamSupport before calling averageCharsPerByte");
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

	
