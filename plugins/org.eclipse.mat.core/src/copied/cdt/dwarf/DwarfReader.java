/*******************************************************************************
 * Copyright (c) 2007, 2013 Nokia and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nokia - initial API and implementation
 *     Ling Wang (Nokia) bug 201000
 *     Serge Beauchamp (Freescale Semiconductor) - Bug 421070
 *     Red Hat Inc. - add debuginfo and macro section support
 *******************************************************************************/

package copied.cdt.dwarf;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.utils.coff.PE;
import org.eclipse.cdt.utils.debug.IDebugEntryRequestor;
import org.eclipse.cdt.utils.elf.Elf;
import org.eclipse.cdt.utils.elf.Elf.Section;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

public class DwarfReader extends Dwarf {
		
	public DwarfReader(String file) throws IOException {
		super(file);
	}

	public DwarfReader(Elf exe) throws IOException {
		super(exe);
	}

	/**
	 * @since 5.1
	 */
	public DwarfReader(PE exe) throws IOException  {
		super(exe);
	}

	// Override parent.
	// 
	@Override
	public void init(Elf exe) throws IOException {
		Elf.ELFhdr header = exe.getELFhdr();
		isLE = header.e_ident[Elf.ELFhdr.EI_DATA] == Elf.ELFhdr.ELFDATA2LSB;

		IPath debugInfoPath = new Path(exe.getFilename());
		Elf.Section[] sections = exe.getSections();
		
		boolean have_build_id = false;
		
		// Look for a special GNU build-id note which means the debug data resides in a separate
		// file with a name based on the build-id.
		for (Section section : sections) {
			if (section.sh_type == Elf.Section.SHT_NOTE) {
				ByteBuffer data = section.mapSectionData();
				if (data.remaining() > 12) {
					try {
						// Read .note section, looking to see if it is named "GNU" and is of GNU_BUILD_ID type
						@SuppressWarnings("unused")
						int name_sz = read_4_bytes(data);
						int data_sz = read_4_bytes(data);
						int note_type = read_4_bytes(data);

						String noteName = readString(data);
						String buildId = null;
						if (noteName.equals("GNU") && note_type == Elf.Section.NT_GNU_BUILD_ID) { //$NON-NLS-1$
							// We have the special GNU build-id note section.  Skip over the name to
							// a 4-byte boundary.
							byte[] byteArray = new byte[data_sz];
							while ((data.position() & 0x3) != 0)
								data.get();
							int i = 0;
							// Read in the hex bytes from the note section's data.
							while (data.hasRemaining() && data_sz-- > 0) {
								byteArray[i++] = data.get();
							}
							// The build-id location is taken by converting the binary bytes to hex string.
							// The first byte is used as a directory specifier (e.g. 51/a4578fe2).
							String bName = printHexBinary(byteArray).toLowerCase();
							buildId = bName.substring(0, 2) + "/" + bName.substring(2) + ".debug"; //$NON-NLS-1$ //$NON-NLS-2$
							// The build-id file should be in the special directory /usr/lib/debug/.build-id
							IPath buildIdPath = new Path("/usr/lib/debug/.build-id").append(buildId); //$NON-NLS-1$
							File buildIdFile = buildIdPath.toFile();
							if (buildIdFile.exists()) {
								// if the debug file exists from above, open it and get the section info from it
								Elf debugInfo = new Elf(buildIdFile.getCanonicalPath());
								sections = debugInfo.getSections();
								have_build_id = true;
								debugInfoPath = new Path(buildIdFile.getCanonicalPath()).removeLastSegments(1);
								break;
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
						CCorePlugin.log(e);
					}
				}
			}
		}
		
		if (!have_build_id) {
			// No build-id.  Look for a .gnu_debuglink section which will have the name of the debug info file
			Elf.Section gnuDebugLink = exe.getSectionByName(DWARF_GNU_DEBUGLINK);
			if (gnuDebugLink != null) {
				ByteBuffer data = gnuDebugLink.mapSectionData();
				if (data != null) { // we have non-empty debug info link
					try {
						// name is zero-byte terminated character string
						String debugName = ""; //$NON-NLS-1$
						if (data.hasRemaining()) {
							int c;
							StringBuffer sb = new StringBuffer();
							while ((c = data.get()) != -1) {
								if (c == 0) {
									break;
								}
								sb.append((char) c);
							}
							debugName = sb.toString();
						}
						if (debugName.length() > 0) {
							// try and open the debug info from 3 separate places in order
							File debugFile = null;
							IPath exePath = new Path(exe.getFilename());
							IPath p = exePath.removeLastSegments(1);
							// 1. try and open the file in the same directory as the executable
							debugFile = p.append(debugName).toFile();
							if (!debugFile.exists()) {
								// 2. try and open the file in the .debug directory where the executable is 
								debugFile = p.append(".debug").append(debugName).toFile(); //$NON-NLS-1$
								if (!debugFile.exists())
									// 3. try and open /usr/lib/debug/$(EXE_DIR)/$(DEBUGINFO_NAME)
									debugFile = new Path("/usr/lib/debug").append(p).append(debugName).toFile(); //$NON-NLS-1$
							}
							if (debugFile.exists()) {
								// if the debug file exists from above, open it and get the section info from it
								Elf debugInfo = new Elf(debugFile.getCanonicalPath());
								sections = debugInfo.getSections();
								debugInfoPath = new Path(debugFile.getCanonicalPath()).removeLastSegments(1);
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
						CCorePlugin.log(e);
					}
				}

			}
		}
		
		// Read in sections (and only the sections) we care about.
		//
		for (Section section : sections) {
			String name = section.toString();
			if (name.equals(DWARF_GNU_DEBUGALTLINK)) {
				ByteBuffer data = section.mapSectionData();
				try {
					// name is zero-byte terminated character string
					String altInfoName = readString(data);
					if (altInfoName.length() > 0) {
						IPath altPath = new Path(altInfoName);
						if (!altPath.isAbsolute()) {
							altPath = debugInfoPath.append(altPath);
						}
						File altFile = altPath.toFile();
						if (altFile.exists()) {
							Elf altInfo = new Elf(altFile.getCanonicalPath());
							Elf.Section[] altSections = altInfo.getSections();
							for (Section altSection : altSections) {
								String altName = altSection.toString();
								for (String element : DWARF_ALT_SCNNAMES) {
									if (altName.equals(element)) {
										try {
											dwarfAltSections.put(element, altSection.mapSectionData());
										} catch (Exception e) {
											e.printStackTrace();
											CCorePlugin.log(e);
										}
									}
								}
							}
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
					CCorePlugin.log(e);
				}
			} 
			else {
				for (String element : DWARF_ALT_SCNNAMES) {
					if (name.equals(element)) {
						// catch out of memory exceptions which might happen trying to
						// load large sections (like .debug_info).  not a fix for that
						// problem itself, but will at least continue to load the other
						// sections.
						try {
							dwarfSections.put(element, section.mapSectionData());
						} catch (Exception e) {
							CCorePlugin.log(e);
						}
					}
				}
			}
		}

		this.printEnabled = false;
	}



    private static final char[] hexCode = "0123456789ABCDEF".toCharArray();
    private String printHexBinary(byte[] data) {
        StringBuilder r = new StringBuilder(data.length * 2);
        for (byte b : data) {
            r.append(hexCode[(b >> 4) & 0xF]);
            r.append(hexCode[(b & 0xF)]);
        }
        return r.toString();
    }
}
