package org.eclipse.mat.core.corefile;

import org.eclipse.mat.core.internal.glibc.structures.MallocChunk;

public interface AllocationProvider
{
    Integer identifyChunkClass(MallocChunk chunk, CoreReaderFactory readerFactory);
}
