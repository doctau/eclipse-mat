package org.eclipse.mat.core.plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.IAddressFactory2;
import org.eclipse.cdt.core.IBinaryParser;
import org.eclipse.cdt.core.IBinaryParser.IBinaryExecutable;
import org.eclipse.cdt.core.IBinaryParser.IBinaryFile;
import org.eclipse.cdt.core.IBinaryParser.IBinaryObject;
import org.eclipse.cdt.core.IBinaryParser.IBinaryShared;
import org.eclipse.cdt.utils.elf.Elf;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.core.corefile.CoreReaderFactory;
import org.eclipse.mat.core.internal.glib.Glibc6Processor;
import org.eclipse.mat.parser.IIndexBuilder;
import org.eclipse.mat.parser.IPreliminaryIndex;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.VoidProgressListener;

public class CoreIndexBuilder extends CoreLoaderSupport implements IIndexBuilder{
    private File file;
    private String prefix;

    public void init(File file, String prefix) throws SnapshotException, IOException {
        this.file = file;
        this.prefix = prefix;
    }

    public void fill(IPreliminaryIndex index, IProgressListener listener) throws SnapshotException, IOException {
        try {
            IBinaryObject coreBinary = loadBinary(file.getAbsolutePath());
            switch (coreBinary.getType()) {
                case IBinaryFile.CORE:
                    fill(index, listener, coreBinary);
                    break;
                default:
                    throw new SnapshotException("unimplemented processing of type " + coreBinary.getType() + ": " + file.getPath());
            }
        } catch (CoreException e) {
            CorePlugin.error(e);
        }
    }

    private void fill(IPreliminaryIndex index, IProgressListener listener, IBinaryObject coreBinary) throws IOException, SnapshotException, CoreException {
        CoreInfo coreInfo = loadCoreInformation(coreBinary);
        CoreReaderFactory readerFactory = setupCoreReaderFactory(coreInfo);
        findAllocations(index, listener, readerFactory, coreInfo);
        identifyAllocations(index, listener, coreInfo);
    }

    private void findAllocations(IPreliminaryIndex index, IProgressListener listener, CoreReaderFactory readerFactory, CoreInfo coreInfo) throws IOException {
        listener.beginTask("libc.so.6 indexing", IProgressListener.UNKNOWN_TOTAL_WORK);
        IBinaryShared libc6 = coreInfo.getSharedLibrary("libc.so.6");
        if (libc6 == null) {
            CorePlugin.info("Did not find libc.so.6");
        } else {
            new Glibc6Processor().fillIndex(index, listener, readerFactory, libc6, coreInfo);
        }
    }

    private void identifyAllocations(IPreliminaryIndex index, IProgressListener listener, CoreInfo coreInfo) {
    }

    public void clean(int[] purgedMapping, IProgressListener listener) throws IOException {
        // TODO Auto-generated method stub

    }

    public void cancel() {
        // TODO Auto-generated method stub
    }
}
