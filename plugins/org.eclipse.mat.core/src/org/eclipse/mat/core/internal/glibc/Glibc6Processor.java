package org.eclipse.mat.core.internal.glibc;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.cdt.core.IAddress;
import org.eclipse.cdt.core.IBinaryParser.IBinaryExecutable;
import org.eclipse.cdt.core.IBinaryParser.IBinaryObject;
import org.eclipse.cdt.utils.debug.DebugParameterKind;
import org.eclipse.cdt.utils.debug.DebugType;
import org.eclipse.cdt.utils.debug.DebugVariableKind;
import org.eclipse.cdt.utils.debug.IDebugEntryRequestor;
import org.eclipse.cdt.utils.elf.Elf;
import org.eclipse.cdt.utils.elf.Elf.Section;
import org.eclipse.mat.collect.HashMapIntLong;
import org.eclipse.mat.core.corefile.AllocationProvider;
import org.eclipse.mat.core.corefile.ClassRegistry;
import org.eclipse.mat.core.corefile.CoreReaderFactory;
import org.eclipse.mat.core.corefile.MemorySegmentMapping;
import org.eclipse.mat.core.internal.glibc.structures.MallocChunk;
import org.eclipse.mat.core.internal.glibc.structures.MallocState;
import org.eclipse.mat.core.internal.util.DebugEntryRequestorMultiplexer;
import org.eclipse.mat.core.plugin.CoreInfo;
import org.eclipse.mat.core.plugin.CoreLoaderSupport;
import org.eclipse.mat.core.plugin.CorePlugin;
import org.eclipse.mat.util.IProgressListener;

public class Glibc6Processor extends DebugEntryRequestorMultiplexer
{
    private static final String MALLOC_CU = "malloc.c";

    GlibHeapLocator locator;

    private HashMapIntLong fakeSizedClasses;
    
    public Glibc6Processor() {
        this(new GlibHeapLocator());
    }

    private Glibc6Processor(GlibHeapLocator locator) {
        super(locator);
        this.locator = locator;
    }

    public synchronized void fillIndex(IProgressListener listener, ClassRegistry classRegistry,
                    CoreReaderFactory readerFactory, IBinaryExecutable libc6, CoreInfo coreInfo, List<AllocationProvider> providers) {
        try {
            this.fakeSizedClasses = new HashMapIntLong();

            if (locator.info.mainArenaOffset  != null) {
                // found the main arena
                Section coreLibcDataSection = findLibraryData(coreInfo.getCore(), libc6.getName());   
                Elf libcElf = libc6.getAdapter(Elf.class);
                Section libLibcDataSection = libcElf.getSectionByName(".data");

                long libcDataStart = coreLibcDataSection.sh_addr.getValue().longValue();
                long elfOffset = libLibcDataSection.sh_addr.getValue().andNot(BigInteger.valueOf(1024-1)).longValueExact();
                long main_arenaStart = libcDataStart + locator.info.mainArenaOffset - elfOffset;

                // for leafpad it is 0x7ff84a448b40
                main_arenaStart = 0x7ff84a448b40L;
                // off by 0x17682000
                // libcDataStart.sh_offset is 1BB0A0
                // core.sh_offset is C36BA04


                // MAT stuff
                int zeroSizedClassId = getSizedClass(0, classRegistry); // needs to be done


                Iterator<MallocState> arenaIterator = new ArenaIterator(readerFactory, locator.info, readerFactory.convertAddress(main_arenaStart));
                boolean isMainArena = true;
                try {
                    while (arenaIterator.hasNext()) {
                        MallocState state = arenaIterator.next();
                        final IAddress arenaStart;
                        if (isMainArena) {
                            if (state.isContiguous()) {
                                MallocChunk topChunk = GlibcDataReader.readChunk(readerFactory, locator.info, state.top);
                                arenaStart = state.top.add(topChunk.chunkSize()).add(-state.system_mem);
                            } else { 
                                throw new RuntimeException("not implemented");
                            }
                            isMainArena = false;
                        } else {
                            arenaStart = state.end;
                        }
    
                        try {
                            Iterator<MallocChunk> chunkIterator = new ChunkIterator(readerFactory, arenaStart, state.top);
                            while (chunkIterator.hasNext()) {
                                MallocChunk chunk = chunkIterator.next();
                                processChunk(chunk, providers, readerFactory, classRegistry);
                            }
                        } catch (RuntimeException e) {
                            CorePlugin.error(e);
                        }
                    }
                } catch (RuntimeException e) {
                    if (isMainArena) {
                        throw e;
                    } else {
                        CorePlugin.error(e);
                    }
                }
            }
        } catch (IOException e) {
            // No Dwarf data in the Elf.
            CorePlugin.info("No DWARF data in glibc");
        }
    }

    private void processChunk(MallocChunk chunk, List<AllocationProvider> providers,
                    CoreReaderFactory readerFactory, ClassRegistry classRegistry)
    {
        long userdataAddress = chunk.userdata.getValue().longValueExact();

        int chunkSize = (int)chunk.userSize();
        
        // look at the providers to identify what the chunk is
        Integer classId = null;
        for (AllocationProvider provider: providers) {
            Integer cls = provider.identifyChunkClass(chunk, readerFactory);
            if (cls != null) {
                classId = cls;
                break;
            }
        }
        if (classId == null) {
            classId = getSizedClass(chunkSize, classRegistry);
        }

        int objectId = classRegistry.addObject(userdataAddress, chunkSize, classId);
        classRegistry.addFakeGCRoot(userdataAddress);
    }

    private int getSizedClass(int size, ClassRegistry classRegistry)
    {
        if (fakeSizedClasses.containsKey(size)) {
            return (int) fakeSizedClasses.get(size);
        }

        int classId = classRegistry.registerFakeClass(null, size, "glibc.malloc.size" + size +"[]", null, null);
        fakeSizedClasses.put(size, (long)classId);
        return classId;
    }

    private Section findLibraryData(IBinaryObject binary, String name) throws IOException {
        Elf coreElf = binary.getAdapter(Elf.class);
        List<MemorySegmentMapping> memorySegments = CoreLoaderSupport.getCoreMemorySegments(binary);

        List<Long> libcOffsets = new ArrayList<Long>();
        for (MemorySegmentMapping msm: memorySegments) {
            String msmn = new String(msm.name, Charset.defaultCharset());
            // libc.so.6 versus /usr/lib64/libc-2.22.so. urgh.
            String libcName = name.substring(0, name.indexOf('.'));
            if (msmn.contains('/' + libcName)) {
                libcOffsets.add(msm.start);
            }
        }
        
        for (Section s: coreElf.getSections(Section.SHT_PROGBITS)) {
            if (s.sh_flags == Section.SHF_ALLOC + Section.SHF_WRITE && libcOffsets.contains(s.sh_addr.getValue().longValue())) {
                return s;
            }
        }
        return null;
    }

    static class GlibHeapLocator implements IDebugEntryRequestor {
        MallocInformation info = new MallocInformation();
        
        /**
         * Entering a compilation unit.
         * @param name
         * @param address start of address of the cu.
         */
        public void enterCompilationUnit(String name, long address) {
            if (name.endsWith(MALLOC_CU)) {
                info.mallocCuAddress = address;
            }
        }

        /**
         * Exit the current compilation unit.
         * @param address end of compilation unit.
         */
        public void exitCompilationUnit(long address) {}

        /**
         * Entering new include file in a compilation unit.
         * @param name
         */
        public void enterInclude(String name) {}

        /**
         * Exit the current include file.
         */
        public void exitInclude() {}

        /**
         * Enter a function.
         * @param name of the function/method
         * @param type type of the return value.
         * @param isGlobal return the visiblity of the function.
         * @param address the start address of the function.
         */
        public void enterFunction(String name, DebugType type, boolean isGlobal, long address) {}

        /**
         * Exit the current function.
         * @param address the address where the function ends.
         */
        public void exitFunction(long address) {}

        /**
         * Enter a code block in a function.
         * @param offset address of the block starts relative to the current function.
         */
        public void enterCodeBlock(long offset) {}

        /**
         * Exit of the current code block.
         * @param offset the address of which the blocks ends relative to the current function.
         */
        public void exitCodeBlock(long offset) {}

        /**
         * Statement in the compilation unit with a given address.
         * @param line lineno of the statement relative to the current compilation unit.
         * @param offset addres of the statement relative to the current function.
         */
        public void acceptStatement(int line, long address) {}

        /**
         * Integer constant.
         */
        public void acceptIntegerConst(String name, int value) {}

        /**
         *  floating point constant.
         * @param name
         * @param value
         */
        public void acceptFloatConst(String name, double value) {}

        /**
         * Type constant: "const b = 0", b is a type enum.
         * @param name
         * @param type
         * @param address
         */
        public void acceptTypeConst(String name, DebugType type, int value) {}

        /**
         * Caught Exception.
         * @param name
         * @param value
         */
        public void acceptCaughtException(String name, DebugType type, long address) {}

        /**
         * Accept a parameter for the current function.
         * @param name of the parameter
         * @param type of the parameter
         * @param kind of the parameter
         * @param offset address of the parameter relative to the current function.
         */
        public void acceptParameter(String name, DebugType type, DebugParameterKind kind, long offset) {}

        /**
         * Record a variable.
         * @param name
         * @param type
         * @param kind
         * @param address
         */
        public void acceptVariable(String name, DebugType type, DebugVariableKind kind, long address) {
            if ("main_arena".equals(name)) {
                info.mainArenaOffset = address;
            }
        }

        /**
         * Type definition.
         * IDebugEntryRequestor
         * @param name new name
         * @param type
         */
        public void acceptTypeDef(String name, DebugType type) {}
    }
}
