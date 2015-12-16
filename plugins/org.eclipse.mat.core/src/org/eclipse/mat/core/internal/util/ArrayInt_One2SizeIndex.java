package org.eclipse.mat.core.internal.util;

import java.io.IOException;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.parser.index.IIndexReader.IOne2SizeIndex;

public class ArrayInt_One2SizeIndex implements IOne2SizeIndex
{
    private final ArrayInt ai;

    public ArrayInt_One2SizeIndex(ArrayInt ai)
    {
        this.ai = ai;
    }

    public int get(int index)
    {
        return ai.get(index);
    }

    public int[] getAll(int[] index)
    {
        int[] r = new int[index.length];
        for (int i = 0; i < index.length; i++) {
            r[i] = get(index[i]);
        }
        return r;
    }

    public int[] getNext(int index, int length)
    {
        int[] r = new int[length];
        for (int i = 0; i < length; i++) {
            r[i] = get(index + i);
        }
        return r;
    }

    public int size()
    {
        return ai.size();
    }

    public void unload() throws IOException
    {
        
    }

    public void close() throws IOException
    {
        
    }

    public void delete()
    {
        
    }

    public long getSize(int index)
    {
        return ai.get(index);
    }

}
