package edu.ucr.droidCFG.harness.toJar;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;

import java.io.*;
import java.util.Collection;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class Utils {
  public static void main(String... args) {

    String basePath =
        "/Users/umarfarooq/UCR/Research/Research/ProgramAnalysis/Reactive/PluginTest/app/build/intermediates/javac/debug/classes";
    File file = new File(basePath);
    if (file.isDirectory()) {
      File outputDirFile = new File(basePath);
      Collection<File> files =
          FileUtils.listFiles(
              outputDirFile, new RegexFileFilter("^(.*?)"), DirectoryFileFilter.DIRECTORY);
      System.out.println("OutJar:" + outputDirFile.getAbsolutePath());
      // File[] files = outputDirFile.listFiles((File pathname) ->
      // pathname.getName().endsWith(".class"));
      // List<File> classFiles = Arrays.asList(files);
      String jarFileName = "out";
      String jarPath = basePath + File.separator + jarFileName + ".jar";
      File jarFile = new File(jarPath);
      if (jarFile.exists()) jarFile.delete();
      Utils.createJarArchive(new File(jarPath), files);
    }
  }

  public static int BUFFER_SIZE = 10240;

  public static void createJarArchive(File archiveFile, Collection<File> tobeJared) {
    try {
      byte buffer[] = new byte[BUFFER_SIZE];
      // Open archive file
      FileOutputStream stream = new FileOutputStream(archiveFile);
      JarOutputStream out = new JarOutputStream(stream, new Manifest());

      for (File file : tobeJared) {
        if (file == null || !file.exists() || file.isDirectory()) continue; // Just in case...
        System.out.println("Adding " + file.getName());

        // Add archive entry
        JarEntry jarAdd = new JarEntry(file.getName());
        jarAdd.setTime(file.lastModified());
        out.putNextEntry(jarAdd);

        // Write file to archive
        FileInputStream in = new FileInputStream(file);
        while (true) {
          int nRead = in.read(buffer, 0, buffer.length);
          if (nRead <= 0) break;
          out.write(buffer, 0, nRead);
        }
        in.close();
      }

      out.close();
      stream.close();
      System.out.println("Adding completed OK");
    } catch (Exception ex) {
      ex.printStackTrace();
      System.out.println("Error: " + ex.getMessage());
    }
  }
}
