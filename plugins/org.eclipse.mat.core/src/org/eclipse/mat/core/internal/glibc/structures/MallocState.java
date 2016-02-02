package org.eclipse.mat.core.internal.glibc.structures;

import org.eclipse.cdt.core.IAddress;

public class MallocState
{
    public /*mutex_t*/int mutex;
    public int flags;
    public final IAddress[] fastbinsY;
    public IAddress top;
    public IAddress last_remainder;
    public final IAddress[] bins;
    public final /*unsigned*/ int[] binmap;
    public IAddress next;
    public IAddress next_free;
    public Long attached_threads;
    public /*INTERNAL_SIZE_T*/long system_mem;
    public /*INTERNAL_SIZE_T*/long max_system_mem;
    
    private static final int NON_CONTIGUOUS_BITS = 0x02;

    // the address after the malloc_state
    public IAddress end;

    public MallocState(int numFastBins, int numBins, int binMapSize) {
        this.fastbinsY = new IAddress[numFastBins];
        this.bins = new IAddress[numBins];
        this.binmap = new int[binMapSize];
    }

    public boolean isContiguous() {
        return (flags & NON_CONTIGUOUS_BITS) == 0;
    }
}
