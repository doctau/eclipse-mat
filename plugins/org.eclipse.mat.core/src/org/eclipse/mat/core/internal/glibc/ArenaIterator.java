package org.eclipse.mat.core.internal.glibc;

import java.util.Iterator;

import org.eclipse.cdt.core.IAddress;
import org.eclipse.mat.core.corefile.CoreReader;
import org.eclipse.mat.core.corefile.CoreReaderFactory;
import org.eclipse.mat.core.internal.glibc.structures.MallocState;

public class ArenaIterator implements Iterator<MallocState>
{
    private final CoreReaderFactory readerFactory;
    private final MallocInformation info;
    private final IAddress mainArenaAddress;
    private IAddress nextAddress;

    public ArenaIterator(CoreReaderFactory readerFactory, MallocInformation info, IAddress mainArenaAddress) {
        this.readerFactory = readerFactory;
        this.info = info;
        this.mainArenaAddress = mainArenaAddress;
        this.nextAddress = null;
    }

    public boolean hasNext() {
        return nextAddress == null || !nextAddress.equals(mainArenaAddress);
    }

    public MallocState next() {
        IAddress addr = (nextAddress != null) ? nextAddress : mainArenaAddress; 
        MallocState mallocState = GlibcDataReader.readMallocState(readerFactory, info, addr);
        this.nextAddress = mallocState.next;
        return mallocState;
    }
}
