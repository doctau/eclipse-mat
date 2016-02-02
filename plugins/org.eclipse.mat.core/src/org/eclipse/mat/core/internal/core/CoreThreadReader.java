package org.eclipse.mat.core.internal.core;

import java.io.IOException;
import java.util.Arrays;

import org.eclipse.cdt.utils.debug.DebugParameterKind;
import org.eclipse.cdt.utils.debug.DebugType;
import org.eclipse.cdt.utils.debug.DebugVariableKind;
import org.eclipse.cdt.utils.debug.IDebugEntryRequestor;
import org.eclipse.cdt.utils.elf.Elf;
import org.eclipse.mat.core.corefile.ClassRegistry;
import org.eclipse.mat.core.corefile.CoreReaderFactory;
import org.eclipse.mat.core.corefile.NoteEntry;
import org.eclipse.mat.core.corefile.PrStatusNote;
import org.eclipse.mat.core.plugin.CoreInfo;
import org.eclipse.mat.core.plugin.CoreLoaderSupport;
import org.eclipse.mat.parser.IPreliminaryIndex;
import org.eclipse.mat.util.IProgressListener;

public class CoreThreadReader extends CoreLoaderSupport implements IDebugEntryRequestor
{

    public void loadStacks(CoreInfo coreInfo,
                    IPreliminaryIndex index, IProgressListener listener,
                    ClassRegistry classRegistry, CoreReaderFactory readerFactory) throws IOException
    {
        Elf coreElf = coreInfo.getCore().getAdapter(Elf.class);
        boolean isle = coreElf.getAttributes().isLittleEndian();
        for (NoteEntry n: getNoteEntries(coreInfo.getCore())) {
            if (n.noteType == NoteEntry.NT_PRSTATUS && Arrays.equals(n.noteName, NoteEntry.NT_NAME_CORE)) {
                PrStatusNote note = parsePrStatusNote(n.noteDesc, isle);
                readThread(coreInfo, index, listener, classRegistry, readerFactory, note, isle);
            }
        }
    }


    private void readThread(CoreInfo coreInfo, IPreliminaryIndex index, IProgressListener listener,
                    ClassRegistry classRegistry, CoreReaderFactory readerFactory, PrStatusNote note, boolean isle)
    {
        System.out.println(note);
    }


    public void enterCompilationUnit(String name, long address)
    {
        // TODO Auto-generated method stub
        
    }

    public void exitCompilationUnit(long address)
    {
        // TODO Auto-generated method stub
        
    }

    public void enterInclude(String name)
    {
        // TODO Auto-generated method stub
        
    }

    public void exitInclude()
    {
        // TODO Auto-generated method stub
        
    }

    public void enterFunction(String name, DebugType type, boolean isGlobal, long address)
    {
        // TODO Auto-generated method stub
        
    }

    public void exitFunction(long address)
    {
        // TODO Auto-generated method stub
        
    }

    public void enterCodeBlock(long offset)
    {
        // TODO Auto-generated method stub
        
    }

    public void exitCodeBlock(long offset)
    {
        // TODO Auto-generated method stub
        
    }

    public void acceptStatement(int line, long address)
    {
        // TODO Auto-generated method stub
        
    }

    public void acceptIntegerConst(String name, int value)
    {
        // TODO Auto-generated method stub
        
    }

    public void acceptFloatConst(String name, double value)
    {
        // TODO Auto-generated method stub
        
    }

    public void acceptTypeConst(String name, DebugType type, int value)
    {
        // TODO Auto-generated method stub
        
    }

    public void acceptCaughtException(String name, DebugType type, long address)
    {
        // TODO Auto-generated method stub
        
    }

    public void acceptParameter(String name, DebugType type, DebugParameterKind kind, long offset)
    {
        // TODO Auto-generated method stub
        
    }

    public void acceptVariable(String name, DebugType type, DebugVariableKind kind, long address)
    {
        // TODO Auto-generated method stub
        
    }

    public void acceptTypeDef(String name, DebugType type)
    {
        // TODO Auto-generated method stub
        
    }

}
