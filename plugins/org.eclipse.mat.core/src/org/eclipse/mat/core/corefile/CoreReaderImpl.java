package org.eclipse.mat.core.corefile;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

import org.eclipse.cdt.core.IAddress;
import org.eclipse.cdt.core.IAddressFactory2;
import org.eclipse.mat.core.plugin.CoreInfo;

public class CoreReaderImpl implements CoreReader {
    private final CoreInfo info;
    private final IAddressFactory2 addressFactory;
    private final IAddress initialAddress;
    private ByteBuffer buffer;

    private final int SHORT_ADJUST = 65536;
    private final long INT_ADJUST = 4294967296L;
    private final BigInteger LONG_ADJUST = BigInteger.valueOf(2).pow(64);

    public CoreReaderImpl(CoreInfo info, IAddress initialAddress, ByteBuffer buffer, IAddressFactory2 addressFactory) {
        this.info = info;
        this.initialAddress = initialAddress;
        this.buffer = buffer.order(info.getCore().isLittleEndian() ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        this.addressFactory = addressFactory;
    }

    public int readShort() {
        return buffer.getShort();
    }

    public int readUnsignedShort() {
        short i = buffer.getShort();
        if (i >= 0)
            return i;
        else
            return ((int)i) + SHORT_ADJUST;
    }

    public int readInt() {
        return buffer.getInt();
    }

    public long readUnsignedInt() {
        int i = buffer.getInt();
        if (i >= 0)
            return i;
        else
            return ((long)i) + INT_ADJUST;
    }

    public long readLong() {
        return buffer.getLong();
    }

    public BigInteger readUnsignedLong() {
        BigInteger l = BigInteger.valueOf(buffer.getLong());
        if (l.signum() >= 0)
            return l;
        else
            return LONG_ADJUST.add(l);
    }

    public IAddress readPointer() {
        return addressFactory.createAddress(readSized(pointerSize()), false);
    }

    public IAddress currentAddress() {
        return initialAddress.add(buffer.position());
    }

    public void skipData(int i) {
        buffer.position(buffer.position() + i);
    }

    public int pointerSize() {
        return info.pointerSize();
    }

    public BigInteger readSize() {
        return readSized(sizetSize());
    }

    public int sizetSize() {
        // mostly true
        return info.pointerSize();
    }
    
    private BigInteger readSized(int size) {
        switch (size) {
            case 4:
                return BigInteger.valueOf(readUnsignedInt());
            case 8:
                return readUnsignedLong();
            default:
                throw new IllegalArgumentException("unhandled size " + size);
        }
    }

    public byte[] readBytes(int length) {
        byte[] bytes = new byte[length];
        buffer.get(bytes, 0, length);
        return bytes;
    }

    public void readBytes(byte[] bytes, int offset, int length) {
        buffer.get(bytes, offset, length);
    }

    public byte[] readToNullByte() {
        ByteBuffer buf = readToNullByteAsBuffer();
        if (buf.hasArray()) {
            return buf.array();
        } else {
            byte[] res = new byte[buf.remaining()];
            buf.get(res);
            return res;
        }
    }

    private ByteBuffer readToNullByteAsBuffer() {
        buffer.mark();
        int count = 0;
        while (buffer.get() != 0)
            count++;
        buffer.reset();

        ByteBuffer rbuf = buffer.slice();
        rbuf.limit(count+1);
        return rbuf;
    }

    public String readCString(Charset cs) {
        return cs.decode(readToNullByteAsBuffer()).toString();
    }
}
