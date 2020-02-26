package edu.ucr.droidCFG.flowdroid;

import java.io.*;
import java.util.Collection;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class Utils {
    public static String executeCommand(String command) {

        StringBuffer output = new StringBuffer();

        Process p;
        try {
            p = Runtime.getRuntime().exec(command);
            p.waitFor();
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line = "";
            while ((line = reader.readLine()) != null) {
                output.append(line + "\n");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return output.toString();

    }

    public static int BUFFER_SIZE = 10240;

    public static void createJarArchive(File archiveFile, Collection<File> tobeJared) {
        try {
            byte buffer[] = new byte[BUFFER_SIZE];
            // Open archive file
            FileOutputStream stream = new FileOutputStream(archiveFile);
            JarOutputStream out = new JarOutputStream(stream, new Manifest());

            for (File file: tobeJared) {
                if (file == null || !file.exists()
                        || file.isDirectory())
                    continue; // Just in case...
                System.out.println("Adding " + file.getName());

                // Add archive entry
                JarEntry jarAdd = new JarEntry(file.getName());
                jarAdd.setTime(file.lastModified());
                out.putNextEntry(jarAdd);

                // Write file to archive
                FileInputStream in = new FileInputStream(file);
                while (true) {
                    int nRead = in.read(buffer, 0, buffer.length);
                    if (nRead <= 0)
                        break;
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
