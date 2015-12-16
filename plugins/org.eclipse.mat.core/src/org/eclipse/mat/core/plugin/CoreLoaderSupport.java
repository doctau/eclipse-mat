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

    protected static String findExecutable(IBinaryObject binary) {
        return "/usr/libexec/gvfsd"; //"/bin/bash"; // FIXME!
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
