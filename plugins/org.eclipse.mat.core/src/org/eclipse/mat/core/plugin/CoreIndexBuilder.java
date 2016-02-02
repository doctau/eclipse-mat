package org.eclipse.mat.core.plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.cdt.core.IBinaryParser.IBinaryExecutable;
import org.eclipse.cdt.core.IBinaryParser.IBinaryFile;
import org.eclipse.cdt.core.IBinaryParser.IBinaryObject;
import org.eclipse.cdt.core.IBinaryParser.IBinaryShared;
import org.eclipse.cdt.utils.debug.IDebugEntryRequestor;
import org.eclipse.cdt.utils.elf.Elf;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.core.corefile.AllocationProvider;
import org.eclipse.mat.core.corefile.ClassRegistry;
import org.eclipse.mat.core.corefile.CoreReaderFactory;
import org.eclipse.mat.core.internal.core.CoreThreadReader;
import org.eclipse.mat.core.internal.glib.GlibAllocationProvider;
import org.eclipse.mat.core.internal.glibc.Glibc6Processor;
import org.eclipse.mat.parser.IIndexBuilder;
import org.eclipse.mat.parser.IPreliminaryIndex;
import org.eclipse.mat.util.IProgressListener;

import copied.cdt.dwarf.DwarfReader;

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
        List<AllocationProvider> providers = new ArrayList<AllocationProvider>();

        ClassRegistry classRegistry = new ClassRegistry();

        // GType from glib
        GlibAllocationProvider glibProvider = new GlibAllocationProvider();
        boolean glibOkay = (processLibraryDebugInfo("libglib-2.0.so.0", listener, coreInfo, glibProvider.getGlibDER()) != null)
                        && (processLibraryDebugInfo("libgobject-2.0.so.0", listener, coreInfo, glibProvider.getGObjectDER()) != null);
        if (glibOkay && glibProvider.prepare(readerFactory, classRegistry)) {
            providers.add(glibProvider);
        }

        findAllocations(index, listener, classRegistry, readerFactory, coreInfo, providers);

        // read threads
        CoreThreadReader tr = new CoreThreadReader();
        processAllDebugInfo(listener, coreInfo, tr);
        tr.loadStacks(coreInfo, index, listener, classRegistry, readerFactory);
        
        // tidy up
        classRegistry.write(index);
    }

    private void findAllocations(IPreliminaryIndex index, IProgressListener listener, ClassRegistry classRegistry,
                    CoreReaderFactory readerFactory, CoreInfo coreInfo,
                    List<AllocationProvider> providers) throws IOException {
        Glibc6Processor processor = new Glibc6Processor();
        IBinaryExecutable libc6 = processLibraryDebugInfo("libc.so.6", listener, coreInfo, processor);
        if (libc6 == null)
            return;
        processor.fillIndex(listener, classRegistry, readerFactory, libc6, coreInfo, providers);
    }

    private IBinaryExecutable processLibraryDebugInfo(String libraryName, IProgressListener listener, CoreInfo coreInfo,
                    IDebugEntryRequestor requestor) throws IOException
    {
        listener.beginTask(libraryName + " indexing", IProgressListener.UNKNOWN_TOTAL_WORK);
        IBinaryExecutable lib = coreInfo.getSharedLibrary(libraryName);
        if (lib == null) {
            CorePlugin.error("Did not find " + libraryName);
            return null;
        }
        readDwarf(lib, requestor);
        return lib;
    }

    private void processAllDebugInfo(IProgressListener listener, CoreInfo coreInfo, IDebugEntryRequestor requestor) throws IOException
    {
        readDwarf(coreInfo.getExecutable(), requestor);
        for (IBinaryShared lib: coreInfo.getSharedLibraries()) {
            readDwarf(lib, requestor);
        }
    }

    private void readDwarf(IBinaryExecutable lib, IDebugEntryRequestor requestor) throws IOException
    {
        Elf elf = (Elf)lib.getAdapter(Elf.class);
        // report error if dwarfSections is missing debug data
        new DwarfReader(elf).parse(requestor);
    }

    public void clean(int[] purgedMapping, IProgressListener listener) throws IOException {
        // TODO Auto-generated method stub

    }

    public void cancel() {
        // TODO Auto-generated method stub
    }
}
