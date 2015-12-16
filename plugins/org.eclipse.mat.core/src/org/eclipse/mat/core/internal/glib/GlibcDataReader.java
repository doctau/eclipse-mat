package org.eclipse.mat.core.internal.glib;

import org.eclipse.cdt.core.IAddress;
import org.eclipse.mat.core.corefile.CoreReader;
import org.eclipse.mat.core.corefile.CoreReaderFactory;
import org.eclipse.mat.core.internal.glibstructures.MallocChunk;
import org.eclipse.mat.core.internal.glibstructures.MallocState;
import org.eclipse.mat.core.plugin.CorePlugin;

public class GlibcDataReader {

    public static MallocState readMallocState(CoreReaderFactory factory, MallocInformation mallocInfo, IAddress address) {
        CoreReader reader = factory.createReader(address);
        MallocState state = new MallocState(mallocInfo.numFastBins,
                                            mallocInfo.numBins,
                                            mallocInfo.binMapSize);
        state.mutex = reader.readInt();
        state.flags = reader.readInt();
        for (int i = 0; i < mallocInfo.numFastBins; i++) {
            state.fastbinsY[i] = reader.readPointer();
        }
        state.top = reader.readPointer();
        state.last_remainder = reader.readPointer();
        for (int i = 0; i < mallocInfo.numBins; i++) {
            state.bins[i] = reader.readPointer();
        }
        for (int i = 0; i < mallocInfo.binMapSize; i++) {
            state.binmap[i] = reader.readInt();
        }
        state.next = reader.readPointer();
        state.next_free = reader.readPointer();
        if (mallocInfo.attachedThreads_exists)
            state.attached_threads = reader.readLong();
        state.system_mem = reader.readLong();
        state.max_system_mem = reader.readLong();
        
        state.end = reader.currentAddress();
        return state;
    }

    public static MallocChunk readChunk(CoreReaderFactory factory, MallocInformation mallocInfo, IAddress address) {
        return readChunk(factory.createReader(address), false);
    }

    public static MallocChunk readChunk(CoreReader reader) {
        return readChunk(reader, true);
    }
    
    private static MallocChunk readChunk(CoreReader reader, boolean skipUserData) {
        MallocChunk chunk = new MallocChunk();
        IAddress start = reader.currentAddress();

        try {
            chunk.prev_size = reader.readSize().longValueExact();
        } catch (ArithmeticException e) {
            CorePlugin.warning("previous chunk size is not valid", e);
            chunk.prev_size = null;
        }
        chunk.size = reader.readSize().longValueExact();
        assert(chunk.size > 8);

        // only makes sense for allocated blocks
        chunk.userdata = reader.currentAddress();

        // only makes sense for unallocated blocks
        chunk.fd = reader.readPointer();
        chunk.bk = reader.readPointer();
        chunk.fd_nextsize = reader.readPointer();
        chunk.bk_nextsize = reader.readPointer();

        chunk.usersize = chunk.chunkSize() - (2 * reader.sizetSize());
        // read size minus the four pointers we read
        if (skipUserData) {
            reader.skipData((int)chunk.usersize - reader.pointerSize() * 4);
            chunk.end = reader.currentAddress();
            assert(start.distanceTo(chunk.end).longValueExact() == chunk.size);
        }
        return chunk;
    }
}
