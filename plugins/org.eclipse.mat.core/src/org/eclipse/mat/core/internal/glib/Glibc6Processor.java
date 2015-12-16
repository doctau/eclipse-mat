package org.eclipse.mat.core.internal.glib;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.cdt.core.IAddress;
import org.eclipse.cdt.core.IBinaryParser.IBinaryShared;
import org.eclipse.cdt.internal.core.ByteUtils;
import org.eclipse.cdt.utils.debug.DebugParameterKind;
import org.eclipse.cdt.utils.debug.DebugType;
import org.eclipse.cdt.utils.debug.DebugVariableKind;
import org.eclipse.cdt.utils.debug.IDebugEntryRequestor;
import org.eclipse.cdt.utils.elf.Elf;
import org.eclipse.cdt.utils.elf.Elf.Section;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.HashMapIntLong;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.core.corefile.CoreReaderFactory;
import org.eclipse.mat.core.corefile.MemorySegmentMapping;
import org.eclipse.mat.core.corefile.NoteEntry;
import org.eclipse.mat.core.internal.glibstructures.MallocChunk;
import org.eclipse.mat.core.internal.glibstructures.MallocState;
import org.eclipse.mat.core.internal.util.ArrayInt_One2SizeIndex;
import org.eclipse.mat.core.plugin.CoreInfo;
import org.eclipse.mat.core.plugin.CoreLoaderSupport;
import org.eclipse.mat.core.plugin.CorePlugin;
import org.eclipse.mat.parser.IPreliminaryIndex;
import org.eclipse.mat.parser.index.IIndexReader.IOne2OneIndex;
import org.eclipse.mat.parser.index.IIndexReader.IOne2SizeIndex;
import org.eclipse.mat.parser.index.IndexManager.Index;
import org.eclipse.mat.parser.index.IndexWriter;
import org.eclipse.mat.parser.index.IndexWriter.Identifier;
import org.eclipse.mat.parser.index.IndexWriter.IntArray1NWriter;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.parser.model.XGCRootInfo;
import org.eclipse.mat.parser.model.XSnapshotInfo;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.FieldDescriptor;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.util.IProgressListener;

import copied.cdt.dwarf.DwarfReader;

public class Glibc6Processor
{
    private static final String MALLOC_CU = "malloc.c";

    List<String> cus = new ArrayList<String>();
    

    private final long FAKE_SYSTEM_LOADER_ADDRESS = 0; // address 0 MUST always be the system class loader
    private final int FAKE_SYSTEM_LOADER_ID = 0;
    private final int FAKE_CLASS_CLASS_ID = 1;
    private long FAKE_CLASS_CLASS_ADDRESS;
    private long FAKE_GC_ROOT_ADDRESS;
    private final int FAKE_GC_ROOT_ID = 2;
    private final int FIRST_USABLE_OBJECT_ID = 3;

    Set<Long> usedFakeAddresses;
    int nextObjectId;

    HashMapIntObject<ClassImpl> classesById;
    HashMapIntLong fakeSizedClasses;

    Identifier identifiers;
    ArrayInt chunkSizes ;
    ArrayInt objectClasses;
    List<XGCRootInfo> fakeRootInfo;
    ArrayInt allObjectIndices;

    public synchronized void fillIndex(IPreliminaryIndex index, IProgressListener listener, CoreReaderFactory readerFactory, IBinaryShared libc6, CoreInfo coreInfo) {
        try {
            this.classesById = new HashMapIntObject<ClassImpl>();
            this.fakeSizedClasses = new HashMapIntLong();
            this.identifiers = new IndexWriter.Identifier();
            this.chunkSizes = new ArrayInt();
            this.objectClasses = new ArrayInt();
            this.fakeRootInfo = new ArrayList<XGCRootInfo>();
            this.allObjectIndices = new ArrayInt();
            this.usedFakeAddresses = new HashSet<Long>();
            this.FAKE_GC_ROOT_ADDRESS = takeFakeAddress();
            this.FAKE_CLASS_CLASS_ADDRESS = takeFakeAddress();
            this.usedFakeAddresses.add(FAKE_SYSTEM_LOADER_ADDRESS);


            Elf elf = (Elf)libc6.getAdapter(Elf.class);
            GlibHeapLocator locator = new GlibHeapLocator();
            new DwarfReader(elf).parse(locator);

            if (locator.info.mainArenaAddress  != null) {
                // found the main arena
                Section libcDataSection = findLibraryData(coreInfo, libc6.getName());   
                long libcDataStart = libcDataSection.sh_addr.getValue().longValue();
                long main_arenaStart = libcDataStart + locator.info.mainArenaAddress;

                //FIXME
                main_arenaStart -= 0x3BB000; // fuck knows why it is offset, objdump agrees with what we read, gdb doesn't
                
                
                // MAT stuff
                XSnapshotInfo ssInfo = index.getSnapshotInfo();

                createSystemLoader(FAKE_SYSTEM_LOADER_ID);
                createFakeClassClass(FAKE_CLASS_CLASS_ID);
                HashMapIntObject<List<XGCRootInfo>> gcRoots = createFakeGcRoot(FAKE_GC_ROOT_ID);
                this.nextObjectId = FIRST_USABLE_OBJECT_ID;
                int zeroSizedClassId = getSizedClass(0); // needs to be done


                Iterator<MallocState> arenaIterator = new ArenaIterator(readerFactory, locator.info, readerFactory.convertAddress(main_arenaStart));
                boolean isMainArena = true;
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

                    Iterator<MallocChunk> chunkIterator = new ChunkIterator(readerFactory, arenaStart, state.top);
                    while (chunkIterator.hasNext()) {
                        MallocChunk chunk = chunkIterator.next();
                        processChunk(chunk);
                    }
                }

                IntArray1NWriter outbound = new IndexWriter.IntArray1NWriter(identifiers.size(), Index.OUTBOUND.getFile(ssInfo.getPrefix() + "temp."));//$NON-NLS-1$
                outbound.log(FAKE_GC_ROOT_ID, new int[0]);
                /*outbound.log(FAKE_GC_ROOT_ID, allObjectIndices.toArray());
                for (int i = FIRST_USABLE_OBJECT_ID; i < objectClasses.size(); i++) {
                    int clsId = objectClasses.get(i);
                    outbound.log(i, new int[] {clsId});
                }*/


                // FIXME?
                HashMapIntObject<HashMapIntObject<List<XGCRootInfo>>> thread2objects2roots = new HashMapIntObject<HashMapIntObject<List<XGCRootInfo>>>();

                IOne2OneIndex object2class = new ArrayInt_One2SizeIndex(objectClasses);
                IOne2SizeIndex array2size = new ArrayInt_One2SizeIndex(chunkSizes);

                index.setIdentifiers(identifiers);
                index.setGcRoots(gcRoots);
                index.setOutbound(outbound.flush());
                index.setClassesById(classesById);
                index.setObject2classId(object2class);
                index.setArray2size(array2size );
                index.setThread2objects2roots(thread2objects2roots);

                if (identifiers.size() != object2class.size()) {
                    throw new RuntimeException("mis-matched identifier and object2class sizes");
                }
            }
        } catch (IOException e) {
            // No Dwarf data in the Elf.
            CorePlugin.info("No DWARF data in glibc");
        }
    }

    private void processChunk(MallocChunk chunk)
    {
        long userdataAddress = chunk.userdata.getValue().longValueExact();
        if (this.usedFakeAddresses.contains(userdataAddress)) {
            throw new RuntimeException("already used " + userdataAddress + " as fake address!");
        }

        int chunkSize = (int)chunk.userSize();
        final int classId = getSizedClass(chunkSize);
        int objectId = addNextObject(userdataAddress, chunkSize, classId);
        allObjectIndices.add(objectId);
        fakeRootInfo.add(new XGCRootInfo(userdataAddress, FAKE_GC_ROOT_ADDRESS, GCRootInfo.Type.UNKNOWN));
    }

    protected int addNextObject(long address, int objectSize, int classId)
    {
        int objectId = this.nextObjectId++;
        addObject(objectId, address, objectSize, classId);
        return objectId;
    }

    private void addObject(int objectId, long address, int objectSize, int classId)
    {
        if (identifiers.size() != objectId)
            throw new IllegalArgumentException("next identifier is not object id " + objectId);
        if (chunkSizes.size() != objectId)
            throw new IllegalArgumentException("next chunk size is not object id " + objectId);
        if (objectClasses.size() != objectId)
            throw new IllegalArgumentException("next class is not object id " + objectId);

        this.identifiers.add(address);
        this.chunkSizes.add(objectSize);
        this.objectClasses.add(classId);
        this.classesById.get(classId).addInstance(objectSize);
    }

    private void createSystemLoader(int objId)
    {   
        int superId = -1;
        long superClassAddress = 0;
        ClassImpl classImpl = new ClassImpl(FAKE_SYSTEM_LOADER_ADDRESS, "fake.loader.system", superClassAddress,
                        FAKE_SYSTEM_LOADER_ADDRESS, new Field[0], new FieldDescriptor[0]);
        classImpl.setClassLoaderIndex(objId);
        classImpl.setObjectId(objId);
        classImpl.setSuperClassIndex(superId);
        classImpl.setClassInstance(classesById.get(FAKE_CLASS_CLASS_ID));
        this.classesById.put(objId, classImpl);
        
        addObject(objId, FAKE_SYSTEM_LOADER_ADDRESS, 0, FAKE_SYSTEM_LOADER_ID);
    }

    private HashMapIntObject<List<XGCRootInfo>> createFakeGcRoot(int objId)
    {
        addObject(objId, FAKE_GC_ROOT_ADDRESS, 0, FAKE_CLASS_CLASS_ID); //FIXME: use it's own class?

        HashMapIntObject<List<XGCRootInfo>> gcRoots = new HashMapIntObject<List<XGCRootInfo>>();
        gcRoots.put(objId, fakeRootInfo);
        return gcRoots;
    }

    private void createFakeClassClass(int objId)
    {
        int superId = -1;
        long superClassAddress = 0;
        ClassImpl classImpl = new ClassImpl((long) FAKE_CLASS_CLASS_ID, "fake.class.class", superClassAddress, FAKE_SYSTEM_LOADER_ADDRESS,
                        new Field[0], new FieldDescriptor[0]);
        classImpl.setObjectId(FAKE_CLASS_CLASS_ID);
        classImpl.setClassLoaderIndex(FAKE_SYSTEM_LOADER_ID);
        classImpl.setSuperClassIndex(superId);
        classImpl.setClassInstance(classImpl);
        classesById.put(objId, classImpl);

        addObject(objId, FAKE_CLASS_CLASS_ADDRESS, 0, FAKE_CLASS_CLASS_ID);
    }

    private long takeFakeAddress() {
        long addr = this.usedFakeAddresses.size() + 1;
        this.usedFakeAddresses.add(addr);
        return addr;
    }

    private int getSizedClass(int size)
    {
        if (fakeSizedClasses.containsKey(size)) {
            return (int) fakeSizedClasses.get(size);
        }

        long address = takeFakeAddress(); //FIXME? what if this overlaps
        int classId = addNextObject(address, 0, FAKE_CLASS_CLASS_ID);
        
        int superId = -1;
        long superClassAddress = 0;
        ClassImpl classImpl = new ClassImpl((long) classId, "glibc.malloc.size" + size +"[]", superClassAddress, FAKE_SYSTEM_LOADER_ADDRESS,
                        new Field[0], new FieldDescriptor[0]);
        classImpl.setObjectId(classId);
        classImpl.setSuperClassIndex(superId);
        classImpl.setClassLoaderIndex(FAKE_SYSTEM_LOADER_ID);
        classImpl.setHeapSizePerInstance(size);
        classImpl.setClassInstance(classesById.get(FAKE_CLASS_CLASS_ID));

        classesById.put(classId, classImpl);
        fakeSizedClasses.put(size, (long)classId);
        return classId;
    }

    private Section findLibraryData(CoreInfo coreInfo, String name) throws IOException {
        Elf coreElf = coreInfo.getCore().getAdapter(Elf.class);
        List<MemorySegmentMapping> memorySegments = CoreLoaderSupport.getCoreMemorySegments(coreInfo.getCore());

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

    class GlibHeapLocator implements IDebugEntryRequestor {
        MallocInformation info = new MallocInformation();
        
        /**
         * Entering a compilation unit.
         * @param name
         * @param address start of address of the cu.
         */
        public void enterCompilationUnit(String name, long address) {
            cus.add(name);
            if (name.endsWith(MALLOC_CU)) {
                name.toString();
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
                info.mainArenaAddress = address;
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
