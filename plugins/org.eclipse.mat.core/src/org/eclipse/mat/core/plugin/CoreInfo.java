package org.eclipse.mat.core.plugin;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.eclipse.cdt.core.IAddress;
import org.eclipse.cdt.core.IBinaryParser.IBinaryExecutable;
import org.eclipse.cdt.core.IBinaryParser.IBinaryObject;
import org.eclipse.cdt.core.IBinaryParser.IBinaryShared;
import org.eclipse.cdt.utils.elf.Elf;
import org.eclipse.cdt.utils.elf.Elf.Section;
import org.eclipse.mat.core.corefile.MemorySegmentMapping;

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

    public Collection<IBinaryShared> getSharedLibraries()
    {
        return sharedLibraries.values();
    }

    
    public int pointerSize() {
        return 8;
    }
    
    
    public static class DataSection {
        public IAddress start;
        public ByteBuffer data;
        
    }

    public DataSection[] getAllReadableSections() {
        try {   
            List<MemorySegmentMapping> segments = CoreLoaderSupport.getCoreMemorySegments(core);

            List<DataSection> sections = new ArrayList<DataSection>();

            addDataSections(sections);
            addReadOnlyDataSections(segments, sections);

            sections.sort(new Comparator<DataSection>() {
                public int compare(DataSection a, DataSection b) {
                    return a.start.compareTo(b.start);
                }
            });
            return sections.toArray(new DataSection[sections.size()]);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void addDataSections(List<DataSection> sections) throws IOException
    {
        addDataSection(sections, core.getAdapter(Elf.class));
        addDataSection(sections, executable.getAdapter(Elf.class));
        for (IBinaryShared lib: sharedLibraries.values()) {
            addDataSection(sections, lib.getAdapter(Elf.class));
        }
    }

    private void addReadOnlyDataSections(List<MemorySegmentMapping> segments, List<DataSection> sections) throws IOException
    {
        addReadOnlyDataSection(segments, sections, executable.getAdapter(Elf.class));
        for (IBinaryShared lib: sharedLibraries.values()) {
            addReadOnlyDataSection(segments, sections, lib.getAdapter(Elf.class));
        }
    }

    private void addDataSection(List<DataSection> dataSections, Elf object) throws IOException
    {
        for (Section s: object.getSections()) {
            if ((s.sh_flags & Section.SHF_ALLOC) != 0) {
                DataSection ds = new DataSection();
                ds.start = s.sh_addr;
                ds.data = s.mapSectionData();
                dataSections.add(ds);
            }
        }
    }

    private void addReadOnlyDataSection(List<MemorySegmentMapping> segments, List<DataSection> dataSections, Elf elf) throws IOException
    {
        List<MemorySegmentMapping> msms = new ArrayList<MemorySegmentMapping>();
        byte[] fileName = elf.getFilename().getBytes();
        for (MemorySegmentMapping msm: segments) {
            if (arrayEqualsTrailingZeroes(fileName, msm.name)) {
                msms.add(msm);
            }
        }
        
        if (msms.isEmpty()) {
            return;
        }
        
        IAddress rodata_addr = elf.getSectionByName(".rodata").sh_addr;
        IAddress text_addr = elf.getSectionByName(".text").sh_addr;
        IAddress data_addr = elf.getSectionByName(".data").sh_addr;

        // 0x00007ff84aecd1a0 is .rodata in gobject
        // need to add the base that the library is loaded at
        Section s = elf.getSectionByName(".rodata");
        /*if (s != null) {
            DataSection ds = new DataSection();
            ds.start = s.sh_addr;
            ds.data = s.mapSectionData();
            dataSections.add(ds);
        }

        s = elf.getSectionByName(".text");
        if (s != null) {
            DataSection ds = new DataSection();
            ds.start = s.sh_addr;
            ds.data = s.mapSectionData();
            dataSections.add(ds);
        }*/
    }

    private boolean arrayEqualsTrailingZeroes(byte[] fileName, byte[] name)
    {
        int shorter = Math.min(fileName.length, name.length);
        for (int i = 0; i < shorter; i++) {
            if (fileName[i] != name[i])
                return false;
        }
        
        if (fileName.length > name.length) {
            for (int i = shorter; i < fileName.length; i++) {
                if (fileName[i] != 0)
                    return false;
            }
        } else if (fileName.length < name.length) {
            for (int i = shorter; i < name.length; i++) {
                if (name[i] != 0)
                    return false;
            }
        }

        return true;
    }
}
