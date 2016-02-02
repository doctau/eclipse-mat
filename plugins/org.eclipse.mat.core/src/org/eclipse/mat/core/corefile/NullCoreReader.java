package org.eclipse.mat.core.corefile;

import java.math.BigInteger;
import java.nio.charset.Charset;

import org.eclipse.cdt.core.IAddress;

public class NullCoreReader implements CoreReader
{
    public static final CoreReader INSTANCE = new NullCoreReader();
    
    private NullCoreReader() {}

    public int readInt()
    {
        throw new IllegalStateException("Cannot read from null address");
    }

    public long readUnsignedInt()
    {
        throw new IllegalStateException("Cannot read from null address");
    }

    public long readLong()
    {
        throw new IllegalStateException("Cannot read from null address");
    }

    public BigInteger readUnsignedLong()
    {
        throw new IllegalStateException("Cannot read from null address");
    }

    public IAddress readPointer()
    {
        throw new IllegalStateException("Cannot read from null address");
    }

    public BigInteger readSize()
    {
        throw new IllegalStateException("Cannot read from null address");
    }

    public byte[] readBytes(int length)
    {
        throw new IllegalStateException("Cannot read from null address");
    }

    public void readBytes(byte[] bytes, int offset, int length)
    {
        throw new IllegalStateException("Cannot read from null address");
    }

    public void skipData(int i)
    {
        throw new IllegalStateException("Cannot read from null address");
    }

    public IAddress currentAddress()
    {
        throw new IllegalStateException("Cannot read from null address");
    }

    public int pointerSize()
    {
        throw new IllegalStateException("Cannot read from null address");
    }

    public int sizetSize()
    {
        throw new IllegalStateException("Cannot read from null address");
    }

    public byte[] readToNullByte() {
        throw new IllegalStateException("Cannot read from null address");
    }

    public int readShort()
    {
        throw new IllegalStateException("Cannot read from null address");
    }

    public int readUnsignedShort()
    {
        throw new IllegalStateException("Cannot read from null address");
    }

    public String readCString(Charset cs)
    {
        throw new IllegalStateException("Cannot read from null address");
    }
}
