package org.eclipse.mat.core.plugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.cdt.core.IBinaryParser.IBinaryFile;
import org.eclipse.cdt.core.IBinaryParser.IBinaryObject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.core.corefile.CoreReader;
import org.eclipse.mat.core.corefile.CoreReaderFactory;
import org.eclipse.mat.parser.IObjectReader;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.parser.model.InstanceImpl;
import org.eclipse.mat.parser.model.ObjectArrayImpl;
import org.eclipse.mat.parser.model.PrimitiveArrayImpl;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IObject;

public class CoreObjectReader extends CoreLoaderSupport implements IObjectReader {
    CoreInfo coreInfo;
    CoreReaderFactory readerFactory;

    public void open(ISnapshot snapshot) throws SnapshotException, IOException {
        try {
            String path = snapshot.getSnapshotInfo().getPath();
            IBinaryObject coreBinary = loadBinary(path);
            switch (coreBinary.getType()) {
                case IBinaryFile.CORE:
                    this.coreInfo = loadCoreInformation(coreBinary);
                    this.readerFactory = setupCoreReaderFactory(coreInfo);
                    break;
                default:
                    throw new SnapshotException("unimplemented processing of type " + coreBinary.getType() + ": " + path);
            }
        } catch (CoreException e) {
            CorePlugin.error(e);
        }
    }

    public IObject read(int objId, ISnapshot snapshot) throws SnapshotException, IOException {
        long address = snapshot.mapIdToAddress(objId);
        ClassImpl klass = (ClassImpl) snapshot.getClassOf(objId);

        if (klass.isArrayType()) {
            return new PrimitiveArrayImpl(objId, address, klass, (int)klass.getHeapSizePerInstance(), IObject.Type.BYTE);
        } else {
            List<Field> fields = new ArrayList<Field>();
            return new InstanceImpl(objId, address, klass, fields );
        }
    }

    public Object readPrimitiveArrayContent(PrimitiveArrayImpl array, int offset, int length) throws IOException, SnapshotException {
        switch (array.getType()) {
            case IObject.Type.BOOLEAN:
                throw new RuntimeException();
            case IObject.Type.BYTE:
                CoreReader reader = readerFactory.createReader(array.getObjectAddress() + offset);
                byte[] bytes = reader.readBytes(length);
                return bytes;
            case IObject.Type.CHAR:
                throw new RuntimeException();
            case IObject.Type.DOUBLE:
                throw new RuntimeException();
            case IObject.Type.FLOAT:
                throw new RuntimeException();
            case IObject.Type.INT:
                throw new RuntimeException();
            case IObject.Type.LONG:
                throw new RuntimeException();
            case IObject.Type.SHORT:
                throw new RuntimeException();
            default:
                throw new RuntimeException();
        }
    }

    public long[] readObjectArrayContent(ObjectArrayImpl array, int offset, int length) throws IOException, SnapshotException {
        throw new RuntimeException();
    }

    public <A> A getAddon(Class<A> addon) throws SnapshotException {
        // TODO Auto-generated method stub
       return null;
    }

    public void close() throws IOException {
        // TODO Auto-generated method stub

    }
}
