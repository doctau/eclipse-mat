package org.eclipse.mat.core.corefile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.HashMapIntLong;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.core.internal.util.ArrayInt_One2SizeIndex;
import org.eclipse.mat.parser.IPreliminaryIndex;
import org.eclipse.mat.parser.index.IndexWriter;
import org.eclipse.mat.parser.index.IIndexReader.IOne2OneIndex;
import org.eclipse.mat.parser.index.IIndexReader.IOne2SizeIndex;
import org.eclipse.mat.parser.index.IndexManager.Index;
import org.eclipse.mat.parser.index.IndexWriter.Identifier;
import org.eclipse.mat.parser.index.IndexWriter.IntArray1NWriter;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.parser.model.XGCRootInfo;
import org.eclipse.mat.parser.model.XSnapshotInfo;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.FieldDescriptor;
import org.eclipse.mat.snapshot.model.GCRootInfo;

public class ClassRegistry
{
    private final long FAKE_SYSTEM_LOADER_ADDRESS = 0; // address 0 MUST always be the system class loader
    private final int FAKE_SYSTEM_LOADER_ID = 0;
    private final int FAKE_CLASS_CLASS_ID = 1;
    private long FAKE_CLASS_CLASS_ADDRESS;
    private long FAKE_GC_ROOT_ADDRESS;
    private final int FAKE_GC_ROOT_ID = 2;
    private final int FIRST_USABLE_OBJECT_ID = 3;

    private final Set<Long> usedFakeAddresses;
    private int nextObjectId;

    private final HashMapIntObject<ClassImpl> classesById;

    private final Identifier identifiers;
    private final ArrayInt chunkSizes ;
    private final ArrayInt objectClasses;
    private final List<XGCRootInfo> fakeRootInfo;
    private final ArrayInt allObjectIndices;
    private final HashMapIntObject<List<XGCRootInfo>> gcRoots;

    public ClassRegistry()
    {

        this.classesById = new HashMapIntObject<ClassImpl>();
        this.identifiers = new IndexWriter.Identifier();
        this.chunkSizes = new ArrayInt();
        this.objectClasses = new ArrayInt();
        this.fakeRootInfo = new ArrayList<XGCRootInfo>();
        this.allObjectIndices = new ArrayInt();
        this.usedFakeAddresses = new HashSet<Long>();
        this.FAKE_GC_ROOT_ADDRESS = takeFakeAddress();
        this.FAKE_CLASS_CLASS_ADDRESS = takeFakeAddress();
        this.usedFakeAddresses.add(FAKE_SYSTEM_LOADER_ADDRESS);
        

        createSystemLoader(FAKE_SYSTEM_LOADER_ID);
        createFakeClassClass(FAKE_CLASS_CLASS_ID);
        gcRoots = createFakeGcRoot(FAKE_GC_ROOT_ID);
        this.nextObjectId = FIRST_USABLE_OBJECT_ID;
    }

    public void write(IPreliminaryIndex index) throws IOException
    {
        XSnapshotInfo ssInfo = index.getSnapshotInfo();

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

    

    public int registerFakeClass(Long address, int size, String name,
                    Integer superClassId, Integer loaderId) {
        int loaderId_ = (loaderId != null) ? loaderId : FAKE_SYSTEM_LOADER_ID;
        long loaderAddress = identifiers.get(loaderId_);

        int classId = addObject(takeFakeAddress(), 0, FAKE_CLASS_CLASS_ID, true);
        
        long classAddress = (address != null) ? address : classId;

        ClassImpl superClassImpl = (superClassId != null) ? classesById.get(superClassId) : null;
        long superClassAddress = (superClassImpl != null) ? superClassImpl.getObjectAddress() : 0;

        ClassImpl classImpl = new ClassImpl(classAddress, name, superClassAddress, loaderAddress,
                        new Field[0], new FieldDescriptor[0]);
        classImpl.setObjectId(classId);
        classImpl.setSuperClassIndex((superClassId != null) ? superClassId : 0);
        classImpl.setClassLoaderIndex(loaderId_);
        classImpl.setHeapSizePerInstance(size);
        classImpl.setClassInstance(classesById.get(FAKE_CLASS_CLASS_ID));

        classesById.put(classId, classImpl);
        
        return classId;
    }
    
    public void addFakeGCRoot(long address) {
        fakeRootInfo.add(new XGCRootInfo(address, FAKE_GC_ROOT_ADDRESS, GCRootInfo.Type.UNKNOWN));
    }

    public int addFakeObject(int classId) {
        return addObject(takeFakeAddress(), 0, classId);
    }

    public int addObject(long address, int size, int classId) {
        return addObject(address, size, classId, false);
    }

    private int addObject(long address, int size, int classId, boolean fakeOkay) {
        if (!fakeOkay && this.usedFakeAddresses.contains(address)) {
            throw new IllegalArgumentException("already used " + address + " as fake address!");
        }

        int objectId = addNextObject(address, size, classId);
        allObjectIndices.add(objectId);
        return objectId;
    }

    public void registerClass(long address, String name, int size,
                  long superClassAddress, int superClassId,
                  long loaderAddress, int loaderId,
                  Field[] staticFields, FieldDescriptor[] fieldDescriptors) {
        
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
}
