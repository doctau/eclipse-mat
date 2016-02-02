package org.eclipse.mat.core.internal.glibc;

import java.util.Iterator;

import org.eclipse.cdt.core.IAddress;
import org.eclipse.mat.core.corefile.CoreReader;
import org.eclipse.mat.core.corefile.CoreReaderFactory;
import org.eclipse.mat.core.internal.glibc.structures.MallocChunk;

public class ChunkIterator implements Iterator<MallocChunk> {
    private final CoreReader reader;
    private final IAddress last;
    
    public ChunkIterator(CoreReaderFactory readerFactory, IAddress start, IAddress last) {
        this.reader = readerFactory.createReader(start);
        this.last = last;
    }

    public boolean hasNext() {
        IAddress current = reader.currentAddress();
        switch (current.compareTo(last)) {
            case -1:
                return true;
            case 0:
                return false;
            case 1: // shouldn't happen
                return false;
            default:
                throw new IllegalStateException();    
        }
    }

    public MallocChunk next() {
        if (hasNext())
            return GlibcDataReader.readChunk(reader);
        else
            throw new IllegalStateException();
    }

}
