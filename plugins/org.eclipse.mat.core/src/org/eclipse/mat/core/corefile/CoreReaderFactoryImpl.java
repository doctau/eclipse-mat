package org.eclipse.mat.core.corefile;

import java.math.BigInteger;
import java.nio.ByteBuffer;

import org.eclipse.cdt.core.IAddress;
import org.eclipse.cdt.core.IAddressFactory2;
import org.eclipse.mat.core.plugin.CoreInfo;
import org.eclipse.mat.core.plugin.CoreInfo.DataSection;

public class CoreReaderFactoryImpl implements CoreReaderFactory
{
    private final CoreInfo info;
    private final DataSection[] dataSections;
    private final IAddressFactory2 addressFactory;

    public CoreReaderFactoryImpl(CoreInfo info, IAddressFactory2 addressFactory) {
        this.info = info;
        this.dataSections = info.getAllReadableSections();
        this.addressFactory = addressFactory;
    }

    public CoreReader createReader(IAddress address) {
        if (address.isZero()) {
            return NullCoreReader.INSTANCE;
        }

        if (address.compareTo(dataSections[0].start) < 0) {
            throw new IllegalArgumentException("Data section not found");
        }
        int i;
        for (i = 1; i < dataSections.length; i++) {
            if (dataSections[i].start.compareTo(address) > 0) {
                break;
            }
        }

        DataSection section = dataSections[i-1];
        BigInteger offset = section.start.distanceTo(address);
        if (offset.longValueExact() >= section.data.limit()) {
            throw new IllegalArgumentException("address out of section bounds");
        }
        ByteBuffer buffer = section.data.slice();
        buffer.position(offset.intValue());
        return new CoreReaderImpl(info, section.start, buffer, addressFactory);
    }


    public IAddress convertAddress(long address) {
        return convertAddress(BigInteger.valueOf(address));
    }

    public IAddress convertAddress(BigInteger address) {
        return addressFactory.createAddress(address);
    }

    public CoreReader createReader(long address) {
        return createReader(convertAddress(address));
    }

}
