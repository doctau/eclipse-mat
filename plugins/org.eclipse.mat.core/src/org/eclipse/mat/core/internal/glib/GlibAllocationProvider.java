package org.eclipse.mat.core.internal.glib;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.cdt.core.IAddress;
import org.eclipse.cdt.utils.debug.DebugParameterKind;
import org.eclipse.cdt.utils.debug.DebugType;
import org.eclipse.cdt.utils.debug.DebugVariableKind;
import org.eclipse.cdt.utils.debug.IDebugEntryRequestor;
import org.eclipse.mat.core.corefile.AllocationProvider;
import org.eclipse.mat.core.corefile.ClassRegistry;
import org.eclipse.mat.core.corefile.CoreReader;
import org.eclipse.mat.core.corefile.CoreReaderFactory;
import org.eclipse.mat.core.internal.glib.structures.TypeNode;
import org.eclipse.mat.core.internal.glibc.structures.MallocChunk;
import org.eclipse.mat.core.internal.util.NumberUtils;
import org.eclipse.mat.core.plugin.CorePlugin;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.FieldDescriptor;

public class GlibAllocationProvider implements AllocationProvider
{
    private Long static_fundamental_type_nodesAddress;
    private TypeNode[] static_fundamental_type_nodes;

    private Long quark_seq_idAddress;
    private Long quarksAddress;
    Integer numQuarks;
    private byte[][] quarks;

    private Integer G_TYPE_FUNDAMENTAL_SHIFT; // FIXME: load from DWARF
    
    // computed
    private Long G_TYPE_FUNDAMENTAL_MAX;
    private Long TYPE_ID_MASK;
    private BigInteger G_TYPE_FUNDAMENTAL_MAX_BigInteger;
    private BigInteger TYPE_ID_MASK_BigInteger;

    Map<IAddress, TypeNode> typeNodeCache = new ConcurrentHashMap<IAddress, TypeNode>();
    Map<TypeNode, Integer> typeNodeClasses = new ConcurrentHashMap<TypeNode, Integer>();
    
    private Integer typeNodeClassId;
    private Integer glibLoaderId;
    private int sizeOfTypeNode;

    public boolean prepare(CoreReaderFactory readerFactory, ClassRegistry classRegistry)
    {
        boolean found = true;

        if (static_fundamental_type_nodesAddress == null)
        {
            CorePlugin.warning("Missing static_fundamental_type_nodes for glib");
            found = false;
        }

        // load up the gtype data structures
        if (found)
        {
            shiftAddresses();
            loadQuarks(readerFactory);
            
            // set up MAT info
            typeNodeClassId = null;
            glibLoaderId = null;
            sizeOfTypeNode = 80; // FIXME: not correct for 32 bit
            loadGTypes(readerFactory, classRegistry);
        }
        return found;
    }

    private void shiftAddresses()
    {
        /*
         * 
                Section libcDataSection = findLibraryData(coreInfo, libc6.getName());   
                long libcDataStart = libcDataSection.sh_addr.getValue().longValue();
                long main_arenaStart = libcDataStart + locator.info.mainArenaOffset;
         * */
        static_fundamental_type_nodesAddress = 0x7ff84b0e3300L; // FIXME HACK
        quark_seq_idAddress = 0x7ff84ae91b98L;
        quarksAddress = 0x7ff84ae91ba0L;
    }

    private void loadQuarks(CoreReaderFactory readerFactory)
    {
        numQuarks = readerFactory.createReader(quark_seq_idAddress).readInt();
        quarks = new byte[numQuarks][];
        IAddress quarkPtr = readerFactory.createReader(quarksAddress).readPointer();
        CoreReader quarkPtrReader = readerFactory.createReader(quarkPtr);
        for (int i = 0; i < numQuarks; i++) {
            IAddress addr = quarkPtrReader.readPointer();
            if (!addr.isZero()) {
                //TODO: doesn't work right.
                try {
                    quarks[i] = readerFactory.createReader(addr).readToNullByte();
                } catch (IllegalArgumentException e) {
                    CorePlugin.warning("Cannot read quark", e);
                }
            }
        }
    }

    private void loadGTypes(CoreReaderFactory readerFactory, ClassRegistry classRegistry)
    {
        G_TYPE_FUNDAMENTAL_SHIFT = 2;
        G_TYPE_FUNDAMENTAL_MAX  = 255L << G_TYPE_FUNDAMENTAL_SHIFT;
        G_TYPE_FUNDAMENTAL_MAX_BigInteger = BigInteger.valueOf(G_TYPE_FUNDAMENTAL_MAX);
        TYPE_ID_MASK = (1L << G_TYPE_FUNDAMENTAL_SHIFT) - 1L;
        TYPE_ID_MASK_BigInteger = BigInteger.valueOf(TYPE_ID_MASK);
        int numFundamentalTypes = (int)(G_TYPE_FUNDAMENTAL_MAX >> G_TYPE_FUNDAMENTAL_SHIFT) + 1;

        CoreReader reader = readerFactory.createReader(static_fundamental_type_nodesAddress);

        // read all the pointers and then load them, so resolution works
        static_fundamental_type_nodes = new TypeNode[numFundamentalTypes];
        IAddress/*TypeNode*/[] typeNodesAddrs = new IAddress[numFundamentalTypes];
        for (int i = 0; i < numFundamentalTypes; i++) {
            typeNodesAddrs[i] = reader.readPointer();
            if (!typeNodesAddrs[i].isZero()) {
                static_fundamental_type_nodes[i] = new TypeNode();
            }
        }
        for (int i = 0; i < numFundamentalTypes; i++) {
            if (static_fundamental_type_nodes[i] != null) {
                readTypeNodeInto(static_fundamental_type_nodes[i], typeNodesAddrs[i], readerFactory, classRegistry);
            }
        }
    }

    private TypeNode findTypeNodePtrByGType(IAddress gtype) {
        // check fundamental types
        BigInteger val = gtype.getValue();
        if (val.compareTo(G_TYPE_FUNDAMENTAL_MAX_BigInteger) <= 0) {
            int index = val.shiftRight(G_TYPE_FUNDAMENTAL_SHIFT).intValueExact();
            return static_fundamental_type_nodes[index];
        }

        // check the cache
        TypeNode node = typeNodeCache.get(gtype);
        return node;
    }

    private TypeNode convertGTypeToTypeNodePtr(IAddress gtype, CoreReaderFactory readerFactory, ClassRegistry classRegistry)
    {
        TypeNode node = findTypeNodePtrByGType(gtype);
        if (node != null)
            return node;

        // add entry to cache, BEFORE loading it, because of child-parent circular references
        node = new TypeNode();
        TypeNode old = typeNodeCache.putIfAbsent(gtype, node);
        if (old != null)
            return old;

        BigInteger val = gtype.getValue();
        IAddress typeNodeAddr = readerFactory.convertAddress(val.andNot(TYPE_ID_MASK_BigInteger));
        readTypeNodeInto(node, typeNodeAddr, readerFactory, classRegistry);
        return node;
    }


    private void readTypeNodeInto(TypeNode node, IAddress addr, CoreReaderFactory readerFactory, ClassRegistry classRegistry)
    {
        boolean g_debug_enabled = true; // FIXME
        
        node.address = addr;

        CoreReader reader = readerFactory.createReader(addr);

        node.refCount = reader.readUnsignedInt();
        if (g_debug_enabled) {
          node.instance_count = reader.readUnsignedInt();
        }
        node.plugin = reader.readPointer();
        node.n_children = NumberUtils.unsignedToSignedInt(reader.readUnsignedInt());

        // 20 bits used
        long mixed_data = reader.readUnsignedInt();
        node.n_supers = NumberUtils.unsignedToUnsignedBits(mixed_data & 0xFF, 8); //8 bits
        node.n_prerequisites = NumberUtils.unsignedToUnsignedBits((mixed_data >> 8) & 0x1FF, 9); //9 bits
        node.is_classed = ((mixed_data >> 17) & 0x1) != 0;
        node.is_instantiatable = ((mixed_data >> 18) & 0x1) != 0;
        node.mutatable_check_cache = ((mixed_data >> 19) & 0x1) != 0;

        IAddress/*GType[]*/ childrenAddr = reader.readPointer();
        CoreReader childrenReader = readerFactory.createReader(childrenAddr);
        node.children = new TypeNode[node.n_children];
        for (int i = 0; i < node.n_children; i++) {
            node.children[i] = convertGTypeToTypeNodePtr(childrenReader.readPointer(), readerFactory, classRegistry);
        }

        node.data = reader.readPointer();
        node.qname = resolveQuark(reader.readInt(), readerFactory);
        int unknown_zeroes = reader.readInt();
        node.global_gdata = reader.readPointer();
        node.offsets_or_ifaceentries = reader.readPointer();

        IAddress/*GType[]>*/ prerequisitesAddr = reader.readPointer();
        CoreReader prerequisitesReader = readerFactory.createReader(prerequisitesAddr);
        node.prerequisites = new TypeNode[node.n_prerequisites];
        for (int i = 0; i < node.n_prerequisites; i++) {
            node.prerequisites[i] = convertGTypeToTypeNodePtr(prerequisitesReader.readPointer(), readerFactory, classRegistry);
        }

        // supers
        node.supers = new TypeNode[node.n_supers];
        for (int i = 0; i < node.n_supers; i++) {
            IAddress superAddr = reader.readPointer();
            if (!superAddr.equals(addr)) {
                node.supers[i] = convertGTypeToTypeNodePtr(superAddr, readerFactory, classRegistry);
            } else {
                //first entry is the self-type
                node.supers[i] = node;
            }
        }

        registerTypeClass(node, classRegistry);
    }

    private String resolveQuark(int qid, CoreReaderFactory readerFactory)
    {
        if (qid > numQuarks)
            throw new IllegalArgumentException();
        if (quarks[qid] != null)
            return Charset.forName("UTF-8").decode(ByteBuffer.wrap(quarks[qid])).toString();
        else
            return "Missing_glib_Quark_" + qid;
    }

    public Integer identifyChunkClass(MallocChunk chunk, CoreReaderFactory readerFactory)
    {
        TypeNode node = typeNodeCache.get(chunk.userdata);
        if (node != null) {
            // this is a TypeNode itself
            return typeNodeClassId;
        }

        CoreReader chunkReader = readerFactory.createReader(chunk.userdata);
        BigInteger potentialGType = chunkReader.readSize();

        node = findTypeNodePtrByGType(readerFactory.convertAddress(potentialGType));
        if (node == null) {
            // not a GTyped chunk
            return null;
        }

        return typeNodeClasses.get(node);
    }

    private int registerTypeClass(TypeNode node, ClassRegistry classRegistry)
    {
        long address = node.address.getValue().longValueExact();
        
        final Integer superClassId;
        if (node.n_supers > 1)
            superClassId = typeNodeClasses.get(node.supers[1]);
        else
            superClassId = null;
        int classObjectId = classRegistry.registerFakeClass(address, sizeOfTypeNode, "glib.type." + node.qname, superClassId, glibLoaderId);
        typeNodeClasses.put(node, classObjectId);
        return classObjectId;
    }

    
    // load the information from the DWARF file

    public IDebugEntryRequestor getGlibDER()
    {
        return new GlibDebugEntryRequestor();
    }

    public IDebugEntryRequestor getGObjectDER()
    {
        return new GObjectDebugEntryRequestor();
    }

    class GObjectDebugEntryRequestor implements IDebugEntryRequestor {
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
            // not loaded from DWARF info
            // TODO Auto-generated method stub
            if ("G_TYPE_FUNDAMENTAL_MAX".equals(name)) {
                return;
            }
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
            if ("static_fundamental_type_nodes".equals(name)) {
                static_fundamental_type_nodesAddress = address;
            }
        }
    
        public void acceptTypeDef(String name, DebugType type)
        {
            // TODO Auto-generated method stub
        }
    }

    class GlibDebugEntryRequestor implements IDebugEntryRequestor {
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
            if ("quark_seq_id".equals(name)) {
                quark_seq_idAddress = address;
            }
            if ("quarks".equals(name)) {
                quarksAddress = address;
            }
        }
    
        public void acceptTypeDef(String name, DebugType type)
        {
            // TODO Auto-generated method stub
        }
    }
}
