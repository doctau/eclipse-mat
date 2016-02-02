package org.eclipse.mat.core.internal.util;

public class NumberUtils
{
    public static int unsignedToSignedInt(long ulong)
    {
        if (ulong < 0 || ulong > Integer.MAX_VALUE) {
            throw new RuntimeException("Unsigned long" + ulong + " is out of bounds for a signed int");
        }
        return (int)ulong;
    }

    public static int unsignedToUnsignedBits(long l, int bits)
    {
        if (l > (1 << bits)) {
            throw new RuntimeException("Unsigned value" + l + " is out of bounds for " + bits + " bits");
        }
        return (int) l;
    }
}
