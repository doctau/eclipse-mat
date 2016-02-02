package org.eclipse.mat.core.internal.util;

import java.util.Arrays;
import java.util.List;

import org.eclipse.cdt.utils.debug.DebugParameterKind;
import org.eclipse.cdt.utils.debug.DebugType;
import org.eclipse.cdt.utils.debug.DebugVariableKind;
import org.eclipse.cdt.utils.debug.IDebugEntryRequestor;

public class DebugEntryRequestorMultiplexer implements IDebugEntryRequestor
{
    private final List<IDebugEntryRequestor> requestors;
    
    public DebugEntryRequestorMultiplexer(List<IDebugEntryRequestor> requestors) {
        this.requestors = requestors;
    }
    
    public DebugEntryRequestorMultiplexer(IDebugEntryRequestor ... requestors) {
        this(Arrays.asList(requestors));
    }

    public void enterCompilationUnit(String name, long address)
    {
        for (IDebugEntryRequestor der: requestors) {
            der.enterCompilationUnit(name, address);
        }
    }

    public void exitCompilationUnit(long address)
    {
        for (IDebugEntryRequestor der: requestors) {
            der.exitCompilationUnit(address);
        }
    }

    public void enterInclude(String name)
    {
        for (IDebugEntryRequestor der: requestors) {
            der.enterInclude(name);
        }
    }

    public void exitInclude()
    {
        for (IDebugEntryRequestor der: requestors) {
            der.exitInclude();
        }
    }

    public void enterFunction(String name, DebugType type, boolean isGlobal, long address)
    {
        for (IDebugEntryRequestor der: requestors) {
            der.enterFunction(name, type, isGlobal, address);
        }
    }

    public void exitFunction(long address)
    {
        for (IDebugEntryRequestor der: requestors) {
            der.exitFunction(address);
        }
    }

    public void enterCodeBlock(long offset)
    {
        for (IDebugEntryRequestor der: requestors) {
            der.enterCodeBlock(offset);
        }
    }

    public void exitCodeBlock(long offset)
    {
        for (IDebugEntryRequestor der: requestors) {
            der.exitCodeBlock(offset);
        }
    }

    public void acceptStatement(int line, long address)
    {
        for (IDebugEntryRequestor der: requestors) {
            der.acceptStatement(line, address);
        }
    }

    public void acceptIntegerConst(String name, int value)
    {
        for (IDebugEntryRequestor der: requestors) {
            der.acceptIntegerConst(name, value);
        }
    }

    public void acceptFloatConst(String name, double value)
    {
        for (IDebugEntryRequestor der: requestors) {
            der.acceptFloatConst(name, value);
        }
    }

    public void acceptTypeConst(String name, DebugType type, int value)
    {
        for (IDebugEntryRequestor der: requestors) {
            der.acceptTypeConst(name, type, value);
        }
    }

    public void acceptCaughtException(String name, DebugType type, long address)
    {
        for (IDebugEntryRequestor der: requestors) {
            der.acceptCaughtException(name, type, address);
        }
    }

    public void acceptParameter(String name, DebugType type, DebugParameterKind kind, long offset)
    {
        for (IDebugEntryRequestor der: requestors) {
            der.acceptParameter(name, type, kind, offset);
        }
    }

    public void acceptVariable(String name, DebugType type, DebugVariableKind kind, long address)
    {
        for (IDebugEntryRequestor der: requestors) {
            der.acceptVariable(name, type, kind, address);
        }
    }

    public void acceptTypeDef(String name, DebugType type)
    {
        for (IDebugEntryRequestor der: requestors) {
            der.acceptTypeDef(name, type);
        }
    }
}
