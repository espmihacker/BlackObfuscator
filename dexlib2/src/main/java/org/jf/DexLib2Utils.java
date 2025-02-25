package org.jf;

import org.jf.baksmali.Adaptors.ClassDefinition;
import org.jf.baksmali.BaksmaliOptions;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.writer.builder.DexBuilder;
import org.jf.dexlib2.writer.io.MemoryDataStore;
import org.jf.smali.Smali;
import org.jf.smali.SmaliOptions;
import org.jf.util.IndentingWriter;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class DexLib2Utils {

	public static boolean saveDex(File in, File out) {
		try {
			DexBackedDexFile dexBackedDexFile = loadBackedDexFile(in.getAbsolutePath());
			DexBuilder dexBuilder = new DexBuilder(Opcodes.getDefault());
			Set<? extends DexBackedClassDef> defs = dexBackedDexFile.getClasses();
			for (DexBackedClassDef def : defs) {
				Smali.assembleSmaliFile(classToSmali(def), dexBuilder, new SmaliOptions());
			}
			return saveDexFileDexLib2(dexBuilder, out.getAbsolutePath());
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public static long splitDex(File in, File out, List<String> className) {
		try {
			List<String> convertList = new ArrayList<>();
			for (String s : className) {
				if (!s.startsWith("L") && !s.endsWith(";")) {
					convertList.add("L" + s.replace(".", "/"));
				} else {
					convertList.add(s);
				}
			}
			DexBackedDexFile dexBackedDexFile = loadBackedDexFile(in.getAbsolutePath());
			DexBuilder dexBuilder = new DexBuilder(Opcodes.getDefault());
			Set<? extends DexBackedClassDef> defs = dexBackedDexFile.getClasses();

			List<String> mateList = new ArrayList<>();

			for (String sp : convertList) {
				for (DexBackedClassDef def : defs) {
					if (def.getType().contains(sp) && !mateList.contains(def.getType())) {
						Smali.assembleSmaliFile(classToSmali(def), dexBuilder, new SmaliOptions());
						mateList.add(def.getType());
					}
				}
			}

			return saveDexFileDexLib2(dexBuilder, out.getAbsolutePath()) ? mateList.size() : 0;
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}

	public static boolean mergerAndCoverDexFile(File mainDex, File secondDex, File outDexFile) {
		try {
			DexBackedDexFile mainDexFile = loadBackedDexFile(mainDex.getAbsolutePath());
			DexBackedDexFile secondDexFile = loadBackedDexFile(secondDex.getAbsolutePath());

			DexBuilder dexBuilder = new DexBuilder(Opcodes.getDefault());
			List<String> inserted = new ArrayList<>();
			Set<? extends DexBackedClassDef> secondDefs = secondDexFile.getClasses();
			for (DexBackedClassDef def : secondDefs) {
				inserted.add(def.getType());
				dexBuilder.internClassDef(def);
			}

			Set<? extends DexBackedClassDef> mainDefs = mainDexFile.getClasses();
			for (DexBackedClassDef def : mainDefs) {
				if (!inserted.contains(def.getType())) {
					dexBuilder.internClassDef(def);
				}
			}
			return saveDexFileDexLib2(dexBuilder, outDexFile.getAbsolutePath());
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	private static boolean saveDexFileDexLib2(DexBuilder dexBuilder, String dexpath) {
		MemoryDataStore store = null;
		try {
			store = new MemoryDataStore();
			dexBuilder.writeTo(store);
			byte[] dexByte = Arrays.copyOf(store.getBufferData(), store.getSize());
			return createFile(dexpath, dexByte);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		} finally {
			CloseUtils.close(store);
		}
	}

	private static boolean createFile(String path, byte[] content) {
		FileOutputStream fos = null;
		try {
			File file = new File(path);
			if (file.exists()) {
				boolean del = file.delete();
				if (!del) {
					return false;
				}
			}
			if (!file.getParentFile().exists()) {
				file.getParentFile().mkdirs();
			}
			fos = new FileOutputStream(path);
			fos.write(content);
			fos.flush();
			return file.exists();
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		} finally {
			CloseUtils.close(fos);
		}
	}

	/**
	 * classDef转smali
	 *
	 * @param def
	 * @return
	 */
	private static String classToSmali(ClassDef def) {
		ClassDefinition classDefinition = new ClassDefinition(new BaksmaliOptions(), def);
		StringWriter stringWrapper = new StringWriter();
		IndentingWriter writer = new IndentingWriter(stringWrapper);
		try {
			classDefinition.writeTo(writer);
			return stringWrapper.toString();
		} catch (IOException e) {
			return null;
		} finally {
			CloseUtils.close(stringWrapper, writer);
		}
	}

	/**
	 * dexlib2加载dex
	 *
	 * @param path
	 * @return
	 * @throws IOException
	 */
	private static DexBackedDexFile loadBackedDexFile(String path) throws IOException {
		FileInputStream fileInputStream = new FileInputStream(path);
		BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
		DexBackedDexFile dexBackedDexFile = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), bufferedInputStream);
		fileInputStream.close();
		bufferedInputStream.close();
		return dexBackedDexFile;
	}
}
