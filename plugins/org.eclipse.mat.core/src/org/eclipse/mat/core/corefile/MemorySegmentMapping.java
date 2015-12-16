package org.eclipse.mat.core.corefile;

public class MemorySegmentMapping {
    public long start;
    public long end;
    public long length;
    public byte[] name; // null terminated
}