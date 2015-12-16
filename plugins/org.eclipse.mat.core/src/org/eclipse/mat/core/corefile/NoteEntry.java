package org.eclipse.mat.core.corefile;

public class NoteEntry {
    public static final int NT_PRSTATUS = 1; // Contains copy of prstatus struct
    public static final int NT_FPREGSET = 2; // Contains copy of fpregset struct
    public static final int NT_PRPSINFO = 3; // Contains copy of prpsinfo struct
    public static final int NT_PRXREG = 4; // Contains copy of prxregset struct
    public static final int NT_TASKSTRUCT = 4; // Contains copy of task structure
    public static final int NT_PLATFORM = 5; // String from sysinfo(SI_PLATFORM)
    public static final int NT_AUXV = 6; // Contains copy of auxv array
    public static final int NT_GWINDOWS = 7; // Contains copy of gwindows struct
    public static final int NT_ASRS = 8; // Contains copy of asrset struct
    public static final int NT_PSTATUS = 10; // Contains copy of pstatus struct
    public static final int NT_PSINFO = 13; // Contains copy of psinfo struct
    public static final int NT_PRCRED = 14; // Contains copy of prcred struct
    public static final int NT_UTSNAME = 15; // Contains copy of utsname struct
    public static final int NT_LWPSTATUS = 16; // Contains copy of lwpstatus struct
    public static final int NT_LWPSINFO = 17; // Contains copy of lwpinfo struct
    public static final int NT_PRFPXREG = 20; // Contains copy of fprxregset struct
    public static final int NT_SIGINFO = 0x53494749; // Contains copy of siginfo_t, size might increase
    public static final int NT_FILE = 0x46494c45; // Contains information about mapped files
    
    public static final byte[] NT_NAME_CORE = new byte[] {'C', 'O', 'R', 'E', 0};
    public static final byte[] NT_NAME_LINUX = new byte[] {'L', 'I', 'N', 'U', 'X', 0};
    
    public int noteType;
    public byte[] noteName;
    public byte[] noteDesc;
}