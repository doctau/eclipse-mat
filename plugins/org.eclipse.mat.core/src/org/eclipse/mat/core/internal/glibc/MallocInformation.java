package org.eclipse.mat.core.internal.glibc;

public class MallocInformation
{
    public Long mainArenaOffset = null;
    public Long mallocCuAddress = null;

    public boolean attachedThreads_exists = true;
    public int numFastBins = 10; // NFASTBINS
    public int numBins = 254; // NBINS * 2 - 2
    public int binMapSize = 4; // BINMAPSIZE
}

