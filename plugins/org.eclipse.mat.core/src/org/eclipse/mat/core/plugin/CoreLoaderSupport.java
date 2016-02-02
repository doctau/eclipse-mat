package org.eclipse.mat.core.plugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.IAddressFactory2;
import org.eclipse.cdt.core.IBinaryParser;
import org.eclipse.cdt.core.IBinaryParser.IBinaryExecutable;
import org.eclipse.cdt.core.IBinaryParser.IBinaryFile;
import org.eclipse.cdt.core.IBinaryParser.IBinaryObject;
import org.eclipse.cdt.core.IBinaryParser.IBinaryShared;
import org.eclipse.cdt.internal.core.ByteUtils;
import org.eclipse.cdt.utils.elf.Elf;
import org.eclipse.cdt.utils.elf.Elf.Section;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.core.corefile.CoreReaderFactory;
import org.eclipse.mat.core.corefile.CoreReaderFactoryImpl;
import org.eclipse.mat.core.corefile.MemorySegmentMapping;
import org.eclipse.mat.core.corefile.NoteEntry;
import org.eclipse.mat.core.corefile.PrStatusNote;
import org.eclipse.mat.core.corefile.PrpsInfoNote;
import org.eclipse.mat.core.internal.core.Timeval;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

public class CoreLoaderSupport
{
    protected CoreInfo loadCoreInformation(IBinaryObject coreBinary) throws CoreException, IOException, SnapshotException {
        String execPath = findExecutable(coreBinary);
        IBinaryObject execBinary = loadBinary(execPath);
        if (execBinary.getType() != IBinaryFile.EXECUTABLE && execBinary.getType() != IBinaryFile.SHARED) {
            throw new SnapshotException("wrong executable type " + execBinary.getType() + ": " + execPath);
        }

        IBinaryExecutable execBinary2 = (IBinaryExecutable) execBinary.getAdapter(IBinaryExecutable.class);
        String[] libs = execBinary2.getNeededSharedLibs();
        Map<String, IBinaryShared> sharedBinaries = new HashMap<String, IBinaryShared>(libs.length);
        for (String sharedLib: libs) {
            sharedBinaries.put(sharedLib, (IBinaryShared)loadLibrary(coreBinary, sharedLib).getAdapter(IBinaryShared.class));
        }

        return new CoreInfo(coreBinary, execBinary2, sharedBinaries);
    }

    protected static IBinaryObject loadBinary(String executable) throws CoreException, IOException {
        IBinaryParser parser = CCorePlugin.getDefault().getDefaultBinaryParser();
        Path path = new Path(executable);
        return (IBinaryObject) parser.getBinary(path).getAdapter(IBinaryObject.class);
    }

    protected static IBinaryShared loadLibrary(IBinaryObject core, String lib) throws CoreException, IOException {
        IBinaryParser parser = CCorePlugin.getDefault().getDefaultBinaryParser();        
        List<MemorySegmentMapping> segments = getCoreMemorySegments(core);

        //FIXME: use the library path
        Path path = new Path("/usr/lib64/" + lib);
        return (IBinaryShared) parser.getBinary(path).getAdapter(IBinaryShared.class);
    }

    protected static String findExecutable(IBinaryObject core) throws IOException {
        Elf coreElf = core.getAdapter(Elf.class);
        boolean isle = coreElf.getAttributes().isLittleEndian();
        String executableName = null;
        for (NoteEntry n: getNoteEntries(core)) {
            if (n.noteType == NoteEntry.NT_PRPSINFO && Arrays.equals(n.noteName, NoteEntry.NT_NAME_CORE)) {
                PrpsInfoNote prpsNote = parsePrpsInfoNote(n.noteDesc, isle);
                executableName = new String(prpsNote.fname);
                break;
            }
        }

        final String title = executableName != null ? "Please select executable '" + executableName + "' for core file" : "Please select executable for core file";
        class Holder { public String result;};
        final Holder holder = new Holder();
        PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
            public void run() {
                Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
                FileDialog dialog = new FileDialog(shell, SWT.OPEN);
                dialog.setText(title);
                holder.result = dialog.open();
            }
        });
        if (holder.result != null)
            return holder.result;
        else
            throw new IOException("Executable selection cancelled");
    }
    
    private static PrpsInfoNote parsePrpsInfoNote(byte[] noteDesc, boolean isle) throws IOException {
        boolean bit64 = true; // FIXME
        
        PrpsInfoNote info = new PrpsInfoNote();
        info.state = noteDesc[0];
        info.stateName = noteDesc[1];
        info.zombie = noteDesc[2];
        info.nice = noteDesc[3];
        info.flags = ByteUtils.makeLong(noteDesc, 4, isle);
        
        final int next;
        if (bit64) {
            info.uid = (int) ByteUtils.makeInt(noteDesc, 12, isle);
            info.gid = (int) ByteUtils.makeInt(noteDesc, 16, isle);
            next = 20;
        } else {
            info.uid = ByteUtils.makeShort(noteDesc, 12, isle);
            info.gid = ByteUtils.makeShort(noteDesc, 14, isle);
            next = 16;
        }
        
        info.pid = (int) ByteUtils.makeInt(noteDesc, next, isle);
        info.ppid = (int) ByteUtils.makeInt(noteDesc, next + 4, isle);
        info.pgrp = (int) ByteUtils.makeInt(noteDesc, next + 8, isle);
        info.sid = (int) ByteUtils.makeInt(noteDesc, next + 16, isle);
        info.fname = Arrays.copyOfRange(noteDesc, next + 20, next + 36);
        info.args = Arrays.copyOfRange(noteDesc, next + 36, noteDesc.length);
        
        return info;
    }
    
    public static PrStatusNote parsePrStatusNote(byte[] noteDesc, boolean isle) throws IOException {
        PrStatusNote status = new PrStatusNote();
        
        // read elfSigInf
        status.signalSigNo = ByteUtils.makeInt(noteDesc, 0, isle);
        status.signalSigCode = ByteUtils.makeInt(noteDesc, 4, isle);
        status.signalSigErrno = ByteUtils.makeInt(noteDesc, 8, isle);
        status.currentSignal = ByteUtils.makeShort(noteDesc, 12, isle); // 2 bytes padding
        status.pendingSignals = ByteUtils.makeInt(noteDesc, 16, isle);
        status.heldSignals = ByteUtils.makeInt(noteDesc, 20, isle);
        
        status.pid = ByteUtils.makeInt(noteDesc, 24, isle); // maybe 2 bytes on 32 bit?
        status.ppid = ByteUtils.makeInt(noteDesc, 28, isle); // maybe 2 bytes on 32 bit?
        status.pgrp = ByteUtils.makeInt(noteDesc, 32, isle); // maybe 2 bytes on 32 bit?
        status.sid = ByteUtils.makeInt(noteDesc, 36, isle); // maybe 2 bytes on 32 bit?

        int next = 36;
        status.userTime = parseTimeval(Arrays.copyOfRange(noteDesc, next, next + 16), isle);
        next += 16;
        status.systemTime = parseTimeval(Arrays.copyOfRange(noteDesc, next, next + 16), isle);
        next += 16;
        status.cumulativeUserTime = parseTimeval(Arrays.copyOfRange(noteDesc, next, next + 16), isle);
        next += 16;
        status.cumulativeSystemTime = parseTimeval(Arrays.copyOfRange(noteDesc, next, next + 16), isle);
        next += 16;
        
        // read general purpose registers
        // 32/64 bit depending on arch.
        /* 64 bit
   __extension__ unsigned long long int r15;
  __extension__ unsigned long long int r14;
  __extension__ unsigned long long int r13;
  __extension__ unsigned long long int r12;
  __extension__ unsigned long long int rbp;
  __extension__ unsigned long long int rbx;
  __extension__ unsigned long long int r11;
  __extension__ unsigned long long int r10;
  __extension__ unsigned long long int r9;
  __extension__ unsigned long long int r8;
  __extension__ unsigned long long int rax;
  __extension__ unsigned long long int rcx;
  __extension__ unsigned long long int rdx;
  __extension__ unsigned long long int rsi;
  __extension__ unsigned long long int rdi;
  __extension__ unsigned long long int orig_rax;
  __extension__ unsigned long long int rip;
  __extension__ unsigned long long int cs;
  __extension__ unsigned long long int eflags;
  __extension__ unsigned long long int rsp;
  __extension__ unsigned long long int ss;
  __extension__ unsigned long long int fs_base;
  __extension__ unsigned long long int gs_base;
  __extension__ unsigned long long int ds;
  __extension__ unsigned long long int es;
  __extension__ unsigned long long int fs;
  __extension__ unsigned long long int gs;
      */
        status.registerSet = new Long[27];
        for (int i = 0; i < status.registerSet.length; i++) {
            status.registerSet[i] = ByteUtils.makeLong(noteDesc, next, isle); // maybe 2 bytes on 32 bit?
            next += 8;   
        }
        /*
         * 32 bit
         struct user_regs_struct
{
  long int ebx;
  long int ecx;
  long int edx;
  long int esi;
  long int edi;
  long int ebp;
  long int eax;
  long int xds;
  long int xes;
  long int xfs;
  long int xgs;
  long int orig_eax;
  long int eip;
  long int xcs;
  long int eflags;
  long int esp;
  long int xss;
};
*/
        status.fpValid = ByteUtils.makeInt(noteDesc, next, isle);
        
        return status;
    }

    private static Timeval parseTimeval(byte[] bytes, boolean isle)
    {
        if (bytes.length != 16)
            throw new IllegalArgumentException();
        return new Timeval();
    }

    protected static CoreReaderFactory setupCoreReaderFactory(CoreInfo coreInfo) throws IOException {
        Elf elf = (Elf)coreInfo.getCore().getAdapter(Elf.class);
        IAddressFactory2 addressFactory = (IAddressFactory2) elf.getAttributes().getAddressFactory();
        return new CoreReaderFactoryImpl(coreInfo, addressFactory);
    }
    


    public static List<MemorySegmentMapping> getCoreMemorySegments(IBinaryObject core) throws IOException
    {
        Elf coreElf = core.getAdapter(Elf.class);
        boolean isle = coreElf.getAttributes().isLittleEndian();
        List<MemorySegmentMapping> memorySegments = new ArrayList<MemorySegmentMapping>();
        for (NoteEntry n: getNoteEntries(core)) {
            if (n.noteType == NoteEntry.NT_FILE && Arrays.equals(n.noteName, NoteEntry.NT_NAME_CORE)) {
                for (MemorySegmentMapping msm: parseCoreFileNote(n.noteDesc, isle)) {
                    memorySegments.add(msm);
                }
            }
        }
        return memorySegments;
    }

    private static MemorySegmentMapping[] parseCoreFileNote(byte[] noteDesc, boolean isle) throws IOException {
        int idx = 0;
        
        //FIXME: should this be 32 bits on a 32 bit system?
        long count = ByteUtils.makeLong(noteDesc, idx, isle);
        long pageSize = ByteUtils.makeLong(noteDesc, idx+8, isle);
        idx += 16;

        MemorySegmentMapping[] segments = new MemorySegmentMapping[(int) count];
        for (int i = 0; i < count; i++) {
            segments[i] = new MemorySegmentMapping();
            segments[i].start = ByteUtils.makeLong(noteDesc, idx, isle);
            segments[i].end = ByteUtils.makeLong(noteDesc, idx+8, isle);
            segments[i].length = ByteUtils.makeLong(noteDesc, idx+16, isle);
            idx += 24;
        }
        for (int i = 0; i < count; i++) {
            segments[i].name = readNullTerminatedString(noteDesc, idx);
            idx += segments[i].name.length;
        }
        
        return segments;
    }
    
    public static List<NoteEntry> getNoteEntries(IBinaryObject core) throws IOException {
        Elf coreElf = core.getAdapter(Elf.class);
        boolean isle = coreElf.getAttributes().isLittleEndian();
    
        List<NoteEntry> noteEntries = new ArrayList<NoteEntry>();
        for (Section noteSection: coreElf.getSections(Section.SHT_NOTE)) {
            byte[] noteBytes = noteSection.loadSectionData();   
            int idx = 0;
            while (idx < noteBytes.length) {
                NoteEntry noteEntry = new NoteEntry();
                // spec seems to say it's 64 bits on 64 bit elf, but...
                int nameSize = (int) ByteUtils.makeInt(noteBytes, idx, isle);
                int descSize = (int) ByteUtils.makeInt(noteBytes, idx + 4, isle);
                noteEntry.noteType = (int) ByteUtils.makeInt(noteBytes, idx + 8, isle);
                idx += 12;
    
                noteEntry.noteName = new byte[nameSize];
                System.arraycopy(noteBytes, idx, noteEntry.noteName, 0, nameSize);
                idx += roundUpTo(nameSize, 4);
                noteEntry.noteDesc = new byte[descSize];
                System.arraycopy(noteBytes, idx, noteEntry.noteDesc, 0, descSize);
                idx += roundUpTo(descSize, 4);
    
                noteEntries.add(noteEntry);
            }
        }
        return noteEntries;
    }


    private static byte[] readNullTerminatedString(byte[] noteDesc, int idx) {
        for (int i = idx; i < noteDesc.length; i++) {
            if (noteDesc[i] == 0) {
                // drop the null?
                return Arrays.copyOfRange(noteDesc, idx, i + 1);
            }
        }
        return Arrays.copyOfRange(noteDesc, idx, noteDesc.length);
    }

    private static int roundUpTo(int i, int n) {
        return (i % n == 0) ? i : i + n - (i % n);
    }
}
