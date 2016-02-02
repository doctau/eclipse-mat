package org.eclipse.mat.core.corefile;

import java.math.BigInteger;
import java.nio.charset.Charset;

import org.eclipse.cdt.core.IAddress;

public interface CoreReader
{
    int readShort();
    int readUnsignedShort();
    int readInt();
    long readUnsignedInt();
    long readLong();
    BigInteger readUnsignedLong();
    IAddress readPointer();
    BigInteger readSize();
    
    byte[] readBytes(int length);
    void readBytes(byte[] bytes, int offset, int length);
    byte[] readToNullByte();
    String readCString(Charset cs);

    void skipData(int i);

    IAddress currentAddress();
    int pointerSize();
    int sizetSize();
}
