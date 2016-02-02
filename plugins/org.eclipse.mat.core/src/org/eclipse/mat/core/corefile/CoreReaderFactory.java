package org.eclipse.mat.core.corefile;

import java.math.BigInteger;

import org.eclipse.cdt.core.IAddress;

public interface CoreReaderFactory {
    CoreReader createReader(IAddress address);
    CoreReader createReader(long address);
    IAddress convertAddress(long address);
    IAddress convertAddress(BigInteger address);
}
