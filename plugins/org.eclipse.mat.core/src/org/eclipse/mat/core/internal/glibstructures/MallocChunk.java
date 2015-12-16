package org.eclipse.mat.core.internal.glibstructures;

import org.eclipse.cdt.core.IAddress;


public class MallocChunk
{
    public Long prev_size; // only used when free
    public long size; // this includes the flags
    public IAddress fd; // only used when free
    public IAddress bk; // only used when free
    public IAddress fd_nextsize; // only used when free
    public IAddress bk_nextsize; // only used when free
    
    public IAddress userdata; // what gets returned from malloc
    public long usersize;
    public IAddress end; // after us


    private static final long SIZE_BITS = 0xFFFFFFFFFFFFFFF8L;
    private static final long PREV_IN_USE_BITS = 0x01L;
    private static final long MMAPED_BITS = 0x02L;
    private static final long SOME_OTHER_BITS = 0x04L;
    
    public long chunkSize() {
        return size & SIZE_BITS;
    }

    public boolean previousInUse() {
        return (size & PREV_IN_USE_BITS) != 0;
    }
    
    public boolean isMmapped() {
        return (size & MMAPED_BITS) != 0;
    }
    
    public String toString() {
        return "chunk " + chunkSize() + "@" + userdata;
    }

    public long userSize()
    {
        return usersize;
    }
}
