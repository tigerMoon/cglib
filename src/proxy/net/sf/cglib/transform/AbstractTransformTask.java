package net.sf.cglib.transform;

import java.io.*;
import java.util.*;
import net.sf.cglib.core.*;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.util.FileUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

abstract public class AbstractTransformTask extends Task implements ClassFilter {
    private FileUtils FILE_UTILS = new FileUtils() { };
    private Vector filesets = new Vector();
    private ClassTransformer transformer;
    private boolean verbose;

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void addFileset(FileSet set) {
        filesets.addElement(set);
    }
    
    private Collection getFiles() {
        Map fileMap = new HashMap();
        Project p = getProject();
        for (int i = 0; i < filesets.size(); i++) {
            FileSet fs = (FileSet)filesets.elementAt(i);
            DirectoryScanner ds = fs.getDirectoryScanner(p);
            String[] srcFiles = ds.getIncludedFiles();
            File dir = fs.getDir(p);
            for (int j = 0; j < srcFiles.length; j++) {
                File src = new File(dir, srcFiles[j]);
                fileMap.put(src.getAbsolutePath(), src);
            }
        }
        return fileMap.values();
    }

    public void execute() throws BuildException {
        if (filesets.size() == 0) {
            throw new BuildException("Specify at least one source fileset.");
        }
        transformer = getClassTransformer();
        for (Iterator it = getFiles().iterator(); it.hasNext();) {
            try {
                processFile((File)it.next());
            } catch (Exception e) {
                throw new BuildException(e);
            }
        }
    }

    private static class EarlyExitException extends RuntimeException { }

    private class CaptureNameWriter extends ClassWriter {
        private String name;

        public CaptureNameWriter(boolean computeMaxs) {
            super(computeMaxs);
        }

        public void visit(int access, String name, String superName, String[] interfaces, String sourceFile) {
            this.name = name.replace('/', '.');
            if (!accept(name)) {
                throw new EarlyExitException();
            }
            super.visit(access, name, superName, interfaces, sourceFile);
        }

        public String getName() {
            return name;
        }
    }

    private void processFile(File file) throws Exception {
        InputStream in = new BufferedInputStream(new FileInputStream(file));
        ClassReader r = new ClassReader(in);
        CaptureNameWriter w = new CaptureNameWriter(true);
        ClassTransformer t = (ClassTransformer)transformer.clone();
        try {
            new TransformingGenerator(new ClassReaderGenerator(r, true), t).generateClass(w);
            in.close();
            byte[] b = w.toByteArray();
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(b);
            fos.close();
            if (verbose) {
                System.out.println("Enhancing class " + w.getName());
            }
        } catch (EarlyExitException e) {
            // ignore this file
        }
    }
                
    abstract protected ClassTransformer getClassTransformer();
}
