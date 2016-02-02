package org.eclipse.mat.core.corefile;

import org.eclipse.mat.core.internal.core.Timeval;

public class PrStatusNote
{

    public long signalSigNo;
    public long signalSigCode;
    public long signalSigErrno;
    public short currentSignal;
    public long pendingSignals;
    public long heldSignals;
    public long pid;
    public long ppid;
    public long pgrp;
    public long sid;
    public Timeval userTime;
    public Timeval systemTime;
    public Timeval cumulativeUserTime;
    public Timeval cumulativeSystemTime;
    public /*unsigned*/ Long[] registerSet;
    public long fpValid;

}
