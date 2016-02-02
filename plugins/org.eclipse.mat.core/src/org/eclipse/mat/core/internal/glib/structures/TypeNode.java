package org.eclipse.mat.core.internal.glib.structures;

import org.eclipse.cdt.core.IAddress;

public class TypeNode
{
    public IAddress address;
    
    public long refCount;
    public int n_children;
    public TypeNode[] children;
    public String qname;
    public IAddress/*GAtomicArray.data*/ offsets_or_ifaceentries;
    public IAddress/*GData*/ global_gdata;
    public int n_prerequisites;
    public TypeNode[] prerequisites;
    public int n_supers; // unsigned byte
    public TypeNode[] supers; // 0 entry is this
    public IAddress/*GTypePlugin*/ plugin;
    public Long instance_count;
    public IAddress/*TypeDaya*/ data;
    public boolean is_classed;
    public boolean is_instantiatable;
    public boolean mutatable_check_cache;

}
