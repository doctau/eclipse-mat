package org.eclipse.mat.core.plugin;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.eclipse.cdt.core.IAddress;
import org.eclipse.cdt.core.IBinaryParser.IBinaryExecutable;
import org.eclipse.cdt.core.IBinaryParser.IBinaryObject;
import org.eclipse.cdt.core.IBinaryParser.IBinaryShared;
import org.eclipse.cdt.utils.elf.Elf;
import org.eclipse.cdt.utils.elf.Elf.Section;

public class CoreInfo
{
    private final IBinaryObject core;
    private final IBinaryExecutable executable;
    private final Map<String, IBinaryShared> sharedLibraries;

    public CoreInfo(IBinaryObject core, IBinaryExecutable executable, Map<String, IBinaryShared> sharedLibraries)
    {
        this.core = core;
        this.executable = executable;
        this.sharedLibraries = sharedLibraries;
    }

    public IBinaryObject getCore() {
        return core;
    }

    public IBinaryExecutable getExecutable() {
        return executable;
    }

    public IBinaryShared getSharedLibrary(String lib) {
        return sharedLibraries.get(lib);
    }

    
    public int pointerSize() {
        return 8;
    }
    
    
    public static class DataSection {
        public IAddress start;
        public ByteBuffer data;
        
    }
    
    public DataSection[] getDataSections() {
        try {
            List<DataSection> dataSections = new ArrayList<DataSection>();
            for (Section s: core.getAdapter(Elf.class).getSections(Section.SHT_PROGBITS)) {
                if (s.sh_flags == Section.SHF_ALLOC + Section.SHF_WRITE) {
                    DataSection ds = new DataSection();
                    ds.start = s.sh_addr;
                    ds.data = s.mapSectionData();
                    dataSections.add(ds);
                }
            }
            dataSections.sort(new Comparator<DataSection>() {
                public int compare(DataSection a, DataSection b) {
                    return a.start.compareTo(b.start);
                }
            });
            return dataSections.toArray(new DataSection[dataSections.size()]);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
