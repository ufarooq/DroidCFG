package edu.ucr.droidCFG.flowdroid;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;
import soot.*;
import org.apache.commons.cli.*;
import soot.baf.BafASMBackend;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.InfoflowConfiguration.*;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration.CallbackAnalyzer;
import soot.jimple.infoflow.InfoflowConfiguration.CallbackSourceMode;
import soot.jimple.infoflow.InfoflowConfiguration.LayoutMatchingMode;
import soot.jimple.infoflow.android.config.XMLConfigurationParser;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.jimple.toolkits.ide.icfg.BackwardsInterproceduralCFG;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.toolkits.graph.pdg.HashMutablePDG;
import soot.toolkits.graph.pdg.PDGNode;

import java.io.*;
import java.util.*;


/**
 * Main class for running FlowDroid from the command-line
 *
 * @author Steven Arzt
 */
public class MainClass {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final static Options options = new Options();
    //private EntryPointsCreator analyzer = null;

    private Set<String> filesToSkip = new HashSet<>();

    private static final String OPTION_RETURN_DEPTH = "rd";
    private static final String OPTION_ACCESS_PATH_SIZE = "ap";

    // Files
    private static final String OPTION_CONFIG_FILE = "c";
    private static final String OPTION_APK_FILE = "a";
    private static final String OPTION_PLATFORMS_DIR = "p";
    private static final String OPTION_SOURCES_SINKS_FILE = "s";
    private static final String OPTION_OUTPUT_FILE = "o";
    private static final String OPTION_ADDITIONAL_CLASSPATH = "ac";
    private static final String OPTION_SKIP_APK_FILE = "si";

    // Timeouts
    private static final String OPTION_TIMEOUT = "dt";
    private static final String OPTION_CALLBACK_TIMEOUT = "ct";
    private static final String OPTION_RESULT_TIMEOUT = "rt";

    // Optional features
    private static final String OPTION_NO_STATIC_FLOWS = "ns";
    private static final String OPTION_NO_CALLBACK_ANALYSIS = "nc";
    private static final String OPTION_NO_EXCEPTIONAL_FLOWS = "ne";
    private static final String OPTION_NO_TYPE_CHECKING = "nt";
    private static final String OPTION_REFLECTION = "r";
    private static final String OPTION_MISSING_SUMMARIES_FILE = "ms";

    // Taint wrapper
    private static final String OPTION_TAINT_WRAPPER = "tw";
    private static final String OPTION_TAINT_WRAPPER_FILE = "t";

    // Individual settings
    private static final String OPTION_ACCESS_PATH_LENGTH = "al";
    private static final String OPTION_NO_THIS_CHAIN_REDUCTION = "nr";
    private static final String OPTION_FLOW_INSENSITIVE_ALIASING = "af";
    private static final String OPTION_COMPUTE_PATHS = "cp";
    private static final String OPTION_ONE_SOURCE = "os";
    private static final String OPTION_ONE_COMPONENT = "ot";
    private static final String OPTION_SEQUENTIAL_PATHS = "sp";
    private static final String OPTION_LOG_SOURCES_SINKS = "ls";
    private static final String OPTION_MERGE_DEX_FILES = "d";
    private static final String OPTION_SINGLE_JOIN_POINT = "sa";
    private static final String OPTION_MAX_CALLBACKS_COMPONENT = "mc";
    private static final String OPTION_MAX_CALLBACKS_DEPTH = "md";
    private static final String OPTION_PATH_SPECIFIC_RESULTS = "ps";

    // Inter-component communication
    private static final String OPTION_ICC_MODEL = "im";
    private static final String OPTION_ICC_NO_PURIFY = "np";

    // Modes and algorithms
    private static final String OPTION_CALLGRAPH_ALGO = "cg";
    private static final String OPTION_LAYOUT_MODE = "l";
    private static final String OPTION_PATH_RECONSTRUCTION_ALGO = "pa";
    private static final String OPTION_CALLBACK_ANALYZER = "ca";
    private static final String OPTION_DATA_FLOW_SOLVER = "ds";
    private static final String OPTION_ALIAS_ALGO = "aa";
    private static final String OPTION_CODE_ELIMINATION_MODE = "ce";
    private static final String OPTION_CALLBACK_SOURCE_MODE = "cs";
    private static final String OPTION_PATH_RECONSTRUCTION_MODE = "pr";
    private static final String OPTION_IMPLICIT_FLOW_MODE = "i";
    private static final String OPTION_STATIC_FLOW_TRACKING_MODE = "sf";

    // Evaluation-specific options
    private static final String OPTION_ANALYZE_FRAMEWORKS = "ff";

    private MainClass() {
        initializeCommandLineOptions();
    }

    /**
     * Initializes the set of available command-line options
     */
    private static void initializeCommandLineOptions() {
        options.addOption("?", "help", false, "Print this help message");

        // Files
        options.addOption(OPTION_CONFIG_FILE, "configfile", true, "Use the given configuration file");
        options.addOption(OPTION_APK_FILE, "apkfile", true, "APK file to analyze");
        options.addOption(OPTION_PLATFORMS_DIR, "platformsdir", true,
                "Path to the platforms directory from the Android SDK");
        options.addOption(OPTION_SOURCES_SINKS_FILE, "sourcessinksfile", true, "Definition file for sources and sinks");
        options.addOption(OPTION_OUTPUT_FILE, "outputfile", true, "Output XML file for the discovered data flows");
        options.addOption(OPTION_ADDITIONAL_CLASSPATH, "additionalclasspath", true,
                "Additional JAR file that shal be put on the classpath");
        options.addOption(OPTION_SKIP_APK_FILE, "skipapkfile", true,
                "APK file to skip when processing a directory of input files");

        options.addOption(OPTION_RETURN_DEPTH, "returndepth", true,
                "Return results depth");

        options.addOption(OPTION_ACCESS_PATH_SIZE, "accesspathsize", true,
                "Return results depth");
        // Timeouts
        options.addOption(OPTION_TIMEOUT, "timeout", true, "Timeout for the main data flow analysis");
        options.addOption(OPTION_CALLBACK_TIMEOUT, "callbacktimeout", true,
                "Timeout for the callback collection phase");
        options.addOption(OPTION_RESULT_TIMEOUT, "resulttimeout", true, "Timeout for the result collection phase");

        // Optional features
        options.addOption(OPTION_NO_STATIC_FLOWS, "nostatic", false, "Do not track static data flows");
        options.addOption(OPTION_NO_CALLBACK_ANALYSIS, "nocallbacks", false, "Do not analyze Android callbacks");
        options.addOption(OPTION_NO_EXCEPTIONAL_FLOWS, "noexceptions", false,
                "Do not track taints across exceptional control flow edges");
        options.addOption(OPTION_NO_TYPE_CHECKING, "notypechecking", false,
                "Disable type checking during taint propagation");
        options.addOption(OPTION_REFLECTION, "enablereflection", false, "Enable support for reflective method calls");
        options.addOption(OPTION_MISSING_SUMMARIES_FILE, "missingsummariesoutputfile", true,
                "Outputs a file with information about which summaries are missing");

        // Taint wrapper
        options.addOption(OPTION_TAINT_WRAPPER, "taintwrapper", true,
                "Use the specified taint wrapper algorithm (NONE, EASY, STUBDROID, MULTI)");
        options.addOption(OPTION_TAINT_WRAPPER_FILE, "taintwrapperfile", true, "Definition file for the taint wrapper");

        // Individual settings
        options.addOption(OPTION_ACCESS_PATH_LENGTH, "aplength", true, "Maximum access path length");
        options.addOption(OPTION_NO_THIS_CHAIN_REDUCTION, "nothischainreduction", false,
                "Disable reduction of inner class chains");
        options.addOption(OPTION_FLOW_INSENSITIVE_ALIASING, "aliasflowins", false,
                "Use a flow-insensitive alias analysis");
        options.addOption(OPTION_COMPUTE_PATHS, "paths", false,
                "Compute the taint propagation paths and not just source-to-sink connections. This is a shorthand notation for -pr fast.");
        options.addOption(OPTION_LOG_SOURCES_SINKS, "logsourcesandsinks", false,
                "Write the discovered sources and sinks to the log output");
        options.addOption("mt", "maxthreadnum", true, "Limit the maximum number of threads to the given value");
        options.addOption(OPTION_ONE_COMPONENT, "onecomponentatatime", false,
                "Analyze one Android component at a time");
        options.addOption(OPTION_ONE_SOURCE, "onesourceatatime", false, "Analyze one source at a time");
        options.addOption(OPTION_SEQUENTIAL_PATHS, "sequentialpathprocessing", false,
                "Process the result paths sequentially instead of in parallel");
        options.addOption(OPTION_SINGLE_JOIN_POINT, "singlejoinpointabstraction", false,
                "Only use a single abstraction at join points, i.e., do not support multiple sources for one value");
        options.addOption(OPTION_MAX_CALLBACKS_COMPONENT, "maxcallbackspercomponent", true,
                "Eliminate Android components that have more than the given number of callbacks");
        options.addOption(OPTION_MAX_CALLBACKS_DEPTH, "maxcallbacksdepth", true,
                "Only analyze callback chains up to the given depth");
        options.addOption(OPTION_MERGE_DEX_FILES, "mergedexfiles", false,
                "Merge all dex files in the given APK file into one analysis target");
        options.addOption(OPTION_PATH_SPECIFIC_RESULTS, "pathspecificresults", false,
                "Report different results for same source/sink pairs if they differ in their propagation paths");

        // Inter-component communication
        options.addOption(OPTION_ICC_MODEL, "iccmodel", true,
                "File containing the inter-component data flow model (ICC model)");
        options.addOption(OPTION_ICC_NO_PURIFY, "noiccresultspurify", false,
                "Do not purify the ICC results, i.e., do not remove simple flows that also have a corresponding ICC flow");

        // Modes and algorithms
        options.addOption(OPTION_CALLGRAPH_ALGO, "cgalgo", true,
                "Callgraph algorithm to use (AUTO, CHA, VTA, RTA, SPARK, GEOM)");
        options.addOption(OPTION_LAYOUT_MODE, "layoutmode", true,
                "Mode for considerung layout controls as sources (NONE, PWD, ALL)");
        options.addOption(OPTION_PATH_RECONSTRUCTION_ALGO, "pathalgo", true,
                "Use the specified algorithm for computing result paths (CONTEXTSENSITIVE, CONTEXTINSENSITIVE, SOURCESONLY)");
        options.addOption(OPTION_CALLBACK_ANALYZER, "callbackanalyzer", true,
                "Use the specified callback analyzer (DEFAULT, FAST)");
        options.addOption(OPTION_DATA_FLOW_SOLVER, "dataflowsolver", true,
                "Use the specified data flow solver (CONTEXTFLOWSENSITIVE, FLOWINSENSITIVE)");
        options.addOption(OPTION_ALIAS_ALGO, "aliasalgo", true,
                "Use the specified aliasing algorithm (NONE, FLOWSENSITIVE, PTSBASED, LAZY)");
        options.addOption(OPTION_CODE_ELIMINATION_MODE, "codeelimination", true,
                "Use the specified code elimination algorithm (NONE, PROPAGATECONSTS, REMOVECODE)");
        options.addOption(OPTION_CALLBACK_SOURCE_MODE, "callbacksourcemode", true,
                "Use the specified mode for defining which callbacks introduce which sources (NONE, ALL, SOURCELIST)");
        options.addOption(OPTION_PATH_RECONSTRUCTION_MODE, "pathreconstructionmode", true,
                "Use the specified mode for reconstructing taint propagation paths (NONE, FAST, PRECISE).");
        options.addOption(OPTION_IMPLICIT_FLOW_MODE, "implicit", true,
                "Use the specified mode when processing implicit data flows (NONE, ARRAYONLY, ALL)");
        options.addOption(OPTION_STATIC_FLOW_TRACKING_MODE, "staticmode", true,
                "Use the specified mode when tracking static data flows (CONTEXTFLOWSENSITIVE, CONTEXTFLOWINSENSITIVE, NONE)");

        // Evaluation-specific options
        options.addOption(OPTION_ANALYZE_FRAMEWORKS, "analyzeframeworks", false,
                "Analyze the full frameworks together with the app without any optimizations");
    }

    public static void main(String[] args) throws Exception {
        MainClass main = new MainClass();
        main.run(args);
    }

    private void run(String[] args) throws Exception {
        // We need proper parameters
        final HelpFormatter formatter = new HelpFormatter();
        if (args.length == 0) {
            formatter.printHelp("soot-infoflow-cmd [OPTIONS]", options);
            return;
        }

        // Parse the command-line parameters
        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);
            System.out.println("Parsing commands");
            // Do we need to display the user manual?
            if (cmd.hasOption("?") || cmd.hasOption("help")) {
                formatter.printHelp("soot-infoflow-cmd [OPTIONS]", options);
                return;
            }

            // Do we have a configuration file?
            String configFile = cmd.getOptionValue(OPTION_CONFIG_FILE);
            final InfoflowAndroidConfiguration config = configFile == null || configFile.isEmpty()
                    ? new InfoflowAndroidConfiguration()
                    : loadConfigurationFile(configFile);
            if (config == null)
                return;

            // Parse the other options
            parseCommandLineOptions(cmd, config, filesToSkip);

            // We can analyze whole directories of apps. In that case, we must gather the
            // target APKs.
            File targetFile = new File(config.getAnalysisFileConfig().getTargetAPKFile());
            if (!targetFile.exists()) {
                System.err.println(String.format("Target APK file %s does not exist", targetFile.getCanonicalPath()));
                return;
            }
            List<File> apksToAnalyze;
            if (targetFile.isDirectory()) {
                apksToAnalyze = Arrays.asList(targetFile.listFiles(new FilenameFilter() {

                    @Override
                    public boolean accept(File dir, String name) {
                        return name.toLowerCase().endsWith(".apk");
                    }

                }));
            } else
                apksToAnalyze = Collections.singletonList(targetFile);

            // In case we analyze multiple APKs, we want to have one file per app for the
            // results
            String outputFileStr = config.getAnalysisFileConfig().getOutputFile();
            File outputFile = null;
            if (outputFileStr != null && !outputFileStr.isEmpty()) {
                outputFile = new File(outputFileStr);
                if (outputFile.exists()) {
                    if (apksToAnalyze.size() > 1 && outputFile.isFile()) {
                        System.err.println("The output file must be a directory when analyzing multiple APKs");
                        return;
                    }
                } else if (apksToAnalyze.size() > 1)
                    outputFile.mkdirs();
            }


            int curAppIdx = 1;
            for (File apkFile : apksToAnalyze) {
                try {
                    final long startTime = Calendar.getInstance().getTimeInMillis();
                    if (filesToSkip.contains(apkFile.getName())) {
                        logger.info(String.format("Skipping app %s (%d of %d)...", apkFile.getCanonicalPath(), curAppIdx++,
                                apksToAnalyze.size()));
                        continue;
                    }
                    logger.info(String.format("Analyzing app %s (%d of %d)...", apkFile.getCanonicalPath(), curAppIdx++,
                            apksToAnalyze.size()));

                    System.out.println("Processing APK for " + apkFile.getAbsolutePath());

                    // Configure the analyzer for the current APK file
                    config.getAnalysisFileConfig().setTargetAPKFile(apkFile.getCanonicalPath());
                    if (outputFile != null) {
                        if (apksToAnalyze.size() > 1 || (outputFile.exists() && outputFile.isDirectory())) {
                            String outputFileName = apkFile.getName().replace(".apk", ".xml");
                            File curOutputFile = new File(outputFile, outputFileName);
                            config.getAnalysisFileConfig().setOutputFile(curOutputFile.getCanonicalPath());

                            // If we have already analyzed this APK and we have the results, there is no
                            // need to do it again
                            if (curOutputFile.exists())
                                continue;
                        }
                    }

                    // Create the data flow analyzer
                    EntryPointsCreator analyzer = new EntryPointsCreator(config);

                    soot.options.Options.v().set_output_jar(true);
                    soot.options.Options.v().set_output_format(soot.options.Options.output_format_class);
                    System.out.println("Path:" + apkFile.getParent());
                    soot.options.Options.v().set_output_dir(apkFile.getParent() + File.separator);

                    //analyzer.setCallbackFile(configPath + File.separator + "AndroidCallbacks.txt");
                    //analyzer.setTaintWrapper(
                    //        new EasyTaintWrapper(configPath + File.separator + "EasyTaintWrapperSource.txt"));

                    analyzer.prepareForAndroid(apkFile);

                    //now we have created ICFG for Android
                    PackManager.v().getPack("wjtp").add(new Transform("wjtp.entrypoints", new SceneTransformer() {
                        @Override
                        protected void internalTransform(String s, Map<String, String> map) {
                            JimpleBasedInterproceduralCFG icfg = new JimpleBasedInterproceduralCFG();
                            BackwardsInterproceduralCFG backwardICFG = new BackwardsInterproceduralCFG(icfg);

                            ProcessManifest manifest = null;
                            String version = "NotSet";
                            String packageName = "NotSet";
                            String appName = FilenameUtils.removeExtension(apkFile.getName());
                            try {
                                manifest = new ProcessManifest(apkFile.toPath().toAbsolutePath().toString());
                                version = manifest.getVersionName();
                                packageName = manifest.getPackageName();

                            } catch (IOException e) {
                                e.printStackTrace();
                            } catch (XmlPullParserException e) {
                                e.printStackTrace();
                            }

                            for (SootMethod entry : Scene.v().getEntryPoints()) {
                                SootClass dummyClass = entry.getDeclaringClass();//Scene.v().getSootClassUnsafe("dummyMainClass");
                                if (!dummyClass.declaresMethodByName("main")) {
                                    // Create the method, public static void main(String[])
                                    SootMethod mainMethod = new SootMethod("main",
                                            Arrays.asList(new Type[]{ArrayType.v(RefType.v("java.lang.String"), 1)}),
                                            VoidType.v(), Modifier.PUBLIC | Modifier.STATIC);

                                    //JimpleBody body = Jimple.v().newBody(mainMethod);
                                    mainMethod.setActiveBody(entry.retrieveActiveBody());
                                    dummyClass.addMethod(mainMethod);
                                }
                                dummyClass.setModifiers(Modifier.PUBLIC);
                                System.out.println("Entry:" + entry.toString());
                                for (SootMethod sootMethod : dummyClass.getMethods()) {
                                    System.out.println("DummyClass Method: " + sootMethod.getSubSignature());
                                    if (!sootMethod.getReturnType().equals(VoidType.v())) {
                                        SootClass returnClass = Scene.v().getSootClass(sootMethod.getReturnType().toString());
                                        Set<Unit> calls = icfg.getCallsFromWithin(sootMethod);
                                        for (Unit call : calls) {
                                            Collection<SootMethod> callees = icfg.getCalleesOfCallAt(call);
                                            for (SootMethod callee : callees) {
                                                System.out.println("Callee: " + callee.getSubSignature());
                                            }
                                        }
                                    }
                                }
                            }
                            soot.options.Options.v().set_output_dir("sootOutput");
                            Iterator<SootClass> itCls = Scene.v().getClasses().iterator();
                            while (itCls.hasNext()) {
                                SootClass sootClass = itCls.next();
                                if (sootClass.isApplicationClass() && (!sootClass.getPackageName().startsWith("java.") && !sootClass.getPackageName().startsWith("android."))) {
                                    String fileName = SourceLocator.v().getFileNameFor(sootClass, soot.options.Options.output_format_class);
                                    OutputStream streamOut = null;
                                    try {
                                        System.out.println(fileName);
                                        File file = new File(fileName);
                                        file.getParentFile().mkdirs();
                                        int java_version = soot.options.Options.v().java_version();
                                        streamOut = new FileOutputStream(fileName);
                                        BafASMBackend backend = new BafASMBackend(sootClass, java_version);
                                        backend.generateClassFile(streamOut);
                                        streamOut.close();
                                    } catch (Exception e) {
                                        System.out.println(sootClass.getName());
                                        System.out.println(sootClass.getMethodCount());
                                        e.printStackTrace();
                                    }
                                }
                            }
                            String outputDir = soot.options.Options.v().output_dir();
                            File outputDirFile = new File(outputDir);
                            Collection<File> files = FileUtils.listFiles(
                                    outputDirFile,
                                    new RegexFileFilter("^(.*?)"),
                                    DirectoryFileFilter.DIRECTORY
                            );
                            System.out.println("OutJar:" + outputDirFile.getAbsolutePath());
                            System.out.println("ClassPath:" + analyzer.getClasspath());
                            //File[] files = outputDirFile.listFiles((File pathname) -> pathname.getName().endsWith(".class"));
                            //List<File> classFiles = Arrays.asList(files);
                            Utils.createJarArchive(new File(outputDirFile.getAbsolutePath() + File.separator + "out.jar"), files);
                            files.parallelStream().forEach(f -> f.deleteOnExit());
                            //String generateJar = "jar cf " + outputDirFile.getAbsolutePath() + File.separator + "out.jar " + outputDirFile.getAbsolutePath() + File.separator + "*";
                            //String generateJarCommandOutput = Utils.executeCommand(generateJar);
                            //System.out.println(generateJarCommandOutput);
                        }
                    }));
                    PackManager.v().getPack("wjtp").apply();
                    //soot.options.Options.v().set_output_jar(true);
                    //PackManager.v().runPacks();
                    //PackManager.v().writeOutput();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (
                AbortAnalysisException e) {
            // Silently return
        } catch (
                ParseException e) {
            formatter.printHelp("soot-infoflow-cmd [OPTIONS]", options);
            return;
        } catch (
                Exception e) {
            System.err.println(String.format("The data flow analysis has failed. Error message: %s", e.getMessage()));
            e.printStackTrace();
        }

    }

    public static InfoflowAndroidConfiguration defaultConfig(String platforms) {
        String[] args = {"-p", platforms};
        initializeCommandLineOptions();
        final HelpFormatter formatter = new HelpFormatter();
        if (args.length == 0) {
            formatter.printHelp("soot-infoflow-cmd [OPTIONS]", options);
            return null;
        }

        // Parse the command-line parameters
        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);
            System.out.println("Parsing commands");
            // Do we need to display the user manual?
            if (cmd.hasOption("?") || cmd.hasOption("help")) {
                formatter.printHelp("soot-infoflow-cmd [OPTIONS]", options);
                return null;
            }
            final InfoflowAndroidConfiguration config = new InfoflowAndroidConfiguration();
            if (config == null)
                return null;

            // Parse the other options
            parseCommandLineOptions(cmd, config, Collections.emptySet());
            return config;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void generateJar(String androidPlatforms, File apk) {


    }

    private static EntryPointsCreator createFlowDroidCFG(String androidPlatforms, File apkFile) {
        try {
            InfoflowAndroidConfiguration config = defaultConfig(androidPlatforms);
            final long startTime = Calendar.getInstance().getTimeInMillis();

            System.out.println("Processing APK for " + apkFile.getAbsolutePath());
            // Configure the analyzer for the current APK file
            config.getAnalysisFileConfig().setTargetAPKFile(apkFile.getCanonicalPath());

            // Create the data flow analyzer
            EntryPointsCreator analyzer = new EntryPointsCreator(config);
            analyzer.prepareForAndroid(apkFile);
            return analyzer;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static FlowDroidJarResult convertToJar(String androidPlatforms, File apkFile, String outputDirectory) {
        EntryPointsCreator entryPointCreator = createFlowDroidCFG(androidPlatforms, apkFile);

        Set<String> entryClasses = new HashSet<>();
        for (SootMethod entry : Scene.v().getEntryPoints()) {
            SootClass dummyClass = entry.getDeclaringClass();//Scene.v().getSootClassUnsafe("dummyMainClass");
            dummyClass.setModifiers(Modifier.PUBLIC);
            entryClasses.add(dummyClass.getName());
            if (!dummyClass.declaresMethodByName("main")) {
                // Create the method, public static void main(String[])
                SootMethod mainMethod = new SootMethod("main",
                        Arrays.asList(new Type[]{ArrayType.v(RefType.v("java.lang.String"), 1)}),
                        VoidType.v(), Modifier.PUBLIC | Modifier.STATIC);
                mainMethod.setActiveBody(entry.retrieveActiveBody());
                dummyClass.addMethod(mainMethod);
            }
        }
        String tmpDir = outputDirectory+File.separator+"tmp";
        new File(tmpDir).mkdirs();
        soot.options.Options.v().set_output_dir(tmpDir);
        Iterator<SootClass> itCls = Scene.v().getClasses().iterator();
        while (itCls.hasNext()) {
            SootClass sootClass = itCls.next();
            if (sootClass.isApplicationClass() && (!sootClass.getPackageName().startsWith("java.") && !sootClass.getPackageName().startsWith("android."))) {
                String fileName = SourceLocator.v().getFileNameFor(sootClass, soot.options.Options.output_format_class);
                OutputStream streamOut = null;
                try {
                    System.out.println(fileName);
                    File file = new File(fileName);
                    file.getParentFile().mkdirs();
                    int java_version = soot.options.Options.v().java_version();
                    streamOut = new FileOutputStream(fileName);
                    BafASMBackend backend = new BafASMBackend(sootClass, java_version);
                    backend.generateClassFile(streamOut);
                    streamOut.close();
                } catch (Exception e) {
                    System.out.println(sootClass.getName());
                    System.out.println(sootClass.getMethodCount());
                    e.printStackTrace();
                }
            }
        }
        String outputDir = soot.options.Options.v().output_dir();
        File outputDirFile = new File(outputDir);
        Collection<File> files = FileUtils.listFiles(
                outputDirFile,
                new RegexFileFilter("^(.*?)"),
                DirectoryFileFilter.DIRECTORY
        );
        System.out.println("OutJar:" + outputDirFile.getAbsolutePath());
        System.out.println("ClassPath:" + entryPointCreator.getClasspath());
        //File[] files = outputDirFile.listFiles((File pathname) -> pathname.getName().endsWith(".class"));
        //List<File> classFiles = Arrays.asList(files);
        String jarFileName = FilenameUtils.getBaseName(apkFile.getAbsolutePath());
        String jarPath = outputDirectory + File.separator + jarFileName + ".jar";
        File jarFile = new File(jarPath);
        if (jarFile.exists())
            jarFile.delete();
        Utils.createJarArchive(new File(jarPath), files);
        files.parallelStream().forEach(f -> f.deleteOnExit());
        return new FlowDroidJarResult(jarPath, entryPointCreator.getClasspath(), entryClasses);

    }

    public static void analyzeAPK(File apkFile, InfoflowAndroidConfiguration config) {
        try {
            Logger logger = LoggerFactory.getLogger(MainClass.class);
            final long startTime = Calendar.getInstance().getTimeInMillis();
            logger.info(String.format("Analyzing app %s (%d of %d)...", apkFile.getCanonicalPath()));

            System.out.println("Processing APK for " + apkFile.getAbsolutePath());
            // Configure the analyzer for the current APK file
            config.getAnalysisFileConfig().setTargetAPKFile(apkFile.getCanonicalPath());

            // Create the data flow analyzer
            EntryPointsCreator analyzer = new EntryPointsCreator(config);
            analyzer.prepareForAndroid(apkFile);

            //now we have created ICFG for Android
            JimpleBasedInterproceduralCFG icfg = new JimpleBasedInterproceduralCFG();

            for (SootMethod entry : Scene.v().getEntryPoints()) {
                SootClass dummyClass = entry.getDeclaringClass();//Scene.v().getSootClassUnsafe("dummyMainClass");
                if (!dummyClass.declaresMethodByName("main")) {
                    // Create the method, public static void main(String[])
                    SootMethod mainMethod = new SootMethod("main",
                            Arrays.asList(new Type[]{ArrayType.v(RefType.v("java.lang.String"), 1)}),
                            VoidType.v(), Modifier.PUBLIC | Modifier.STATIC);

                    //JimpleBody body = Jimple.v().newBody(mainMethod);
                    mainMethod.setActiveBody(entry.retrieveActiveBody());
                    dummyClass.addMethod(mainMethod);
                }
                dummyClass.setModifiers(Modifier.PUBLIC);
                System.out.println("Entry:" + entry.toString());
                for (SootMethod sootMethod : dummyClass.getMethods()) {
                    System.out.println("DummyClass Method: " + sootMethod.getSubSignature());
                    if (!sootMethod.getReturnType().equals(VoidType.v())) {
                        SootClass returnClass = Scene.v().getSootClass(sootMethod.getReturnType().toString());
                        Set<Unit> calls = icfg.getCallsFromWithin(sootMethod);
                        for (Unit call : calls) {
                            Collection<SootMethod> callees = icfg.getCalleesOfCallAt(call);
                            for (SootMethod callee : callees) {
                                System.out.println("Callee: " + callee.getSubSignature());
                            }
                        }
                    }
                }
            }
            soot.options.Options.v().set_output_dir("sootOutput");
            Iterator<SootClass> itCls = Scene.v().getClasses().iterator();
            while (itCls.hasNext()) {
                SootClass sootClass = itCls.next();
                if (sootClass.isApplicationClass() && (!sootClass.getPackageName().startsWith("java.") && !sootClass.getPackageName().startsWith("android."))) {
                    String fileName = SourceLocator.v().getFileNameFor(sootClass, soot.options.Options.output_format_class);
                    OutputStream streamOut = null;
                    try {
                        System.out.println(fileName);
                        File file = new File(fileName);
                        file.getParentFile().mkdirs();
                        int java_version = soot.options.Options.v().java_version();
                        streamOut = new FileOutputStream(fileName);
                        BafASMBackend backend = new BafASMBackend(sootClass, java_version);
                        backend.generateClassFile(streamOut);
                        streamOut.close();
                    } catch (Exception e) {
                        System.out.println(sootClass.getName());
                        System.out.println(sootClass.getMethodCount());
                        e.printStackTrace();
                    }
                }
            }
            String outputDir = soot.options.Options.v().output_dir();
            File outputDirFile = new File(outputDir);
            Collection<File> files = FileUtils.listFiles(
                    outputDirFile,
                    new RegexFileFilter("^(.*?)"),
                    DirectoryFileFilter.DIRECTORY
            );
            System.out.println("OutJar:" + outputDirFile.getAbsolutePath());
            System.out.println("ClassPath:" + analyzer.getClasspath());
            //File[] files = outputDirFile.listFiles((File pathname) -> pathname.getName().endsWith(".class"));
            //List<File> classFiles = Arrays.asList(files);
            Utils.createJarArchive(new File(outputDirFile.getAbsolutePath() + File.separator + "out.jar"), files);
            files.parallelStream().forEach(f -> f.deleteOnExit());
            //String generateJar = "jar cf " + outputDirFile.getAbsolutePath() + File.separator + "out.jar " + outputDirFile.getAbsolutePath() + File.separator + "*";
            //String generateJarCommandOutput = Utils.executeCommand(generateJar);
            //System.out.println(generateJarCommandOutput);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // generate Program Dependency Graph
    public static void printPDG(HashMutablePDG pdg) {

        // generate Program Dependency Graph file content
        //StringBuffer pdgFile = new StringBuffer("");
        for (PDGNode node : pdg) {
            System.out.println("Node = " + node + '\n');
            System.out.println("Preds:" + "\n");
            for (PDGNode pred : pdg.getPredsOf(node)) {
                System.out.println(pred);
            }
            System.out.println("Succs:" + '\n');
            for (PDGNode succ : pdg.getSuccsOf(node)) {
                System.out.println(succ);
            }
        }

    }

    private void printCallsInfo(JimpleBasedInterproceduralCFG icfg, SootMethod mainMethod) {
        Set<Unit> calls = icfg.getCallsFromWithin(mainMethod);
        for (Unit call : calls) {
            if (icfg.isCallStmt(call)) {
                //List<Unit> succsOf = icfg.getSuccsOf(call);
                Collection<SootMethod> methods = icfg.getCalleesOfCallAt(call);
                for (SootMethod method : methods) {
                    System.out.println(mainMethod.toString() + "--CalleesOfCallAt-->" + method.toString());
                    if (!method.getDeclaringClass().getName().endsWith("java.lang.Object"))
                        printCallsInfo(icfg, method);
                }
//                Collection<Unit> returnSites = icfg.getReturnSitesOfCallAt(call);
//                for (Unit returnSite : returnSites) {
//                    System.out.println(call.toString() + "-->" + returnSite.toString());
//                }
            }
        }
    }

    private static CallgraphAlgorithm parseCallgraphAlgorithm(String algo) {
        return CallgraphAlgorithm.SPARK;
//        if (algo.equalsIgnoreCase("AUTO"))
//            return CallgraphAlgorithm.AutomaticSelection;
//        else if (algo.equalsIgnoreCase("CHA"))
//            return CallgraphAlgorithm.CHA;
//        else if (algo.equalsIgnoreCase("VTA"))
//            return CallgraphAlgorithm.VTA;
//        else if (algo.equalsIgnoreCase("RTA"))
//            return CallgraphAlgorithm.RTA;
//        else if (algo.equalsIgnoreCase("SPARK"))
//            return CallgraphAlgorithm.SPARK;
//        else if (algo.equalsIgnoreCase("GEOM"))
//            return CallgraphAlgorithm.GEOM;
//        else {
//            System.err.println(String.format("Invalid callgraph algorithm: %s", algo));
//            throw new AbortAnalysisException();
//        }
    }

    private static LayoutMatchingMode parseLayoutMatchingMode(String layoutMode) {
        if (layoutMode.equalsIgnoreCase("NONE"))
            return LayoutMatchingMode.NoMatch;
        else if (layoutMode.equalsIgnoreCase("PWD"))
            return LayoutMatchingMode.MatchSensitiveOnly;
        else if (layoutMode.equalsIgnoreCase("ALL"))
            return LayoutMatchingMode.MatchAll;
        else {
            System.err.println(String.format("Invalid layout matching mode: %s", layoutMode));
            throw new AbortAnalysisException();
        }
    }

    private static PathBuildingAlgorithm parsePathReconstructionAlgo(String pathAlgo) {
        if (pathAlgo.equalsIgnoreCase("CONTEXTSENSITIVE"))
            return PathBuildingAlgorithm.ContextSensitive;
        else if (pathAlgo.equalsIgnoreCase("CONTEXTINSENSITIVE"))
            return PathBuildingAlgorithm.ContextInsensitive;
        else if (pathAlgo.equalsIgnoreCase("SOURCESONLY"))
            return PathBuildingAlgorithm.ContextInsensitiveSourceFinder;
        else {
            System.err.println(String.format("Invalid path reconstruction algorithm: %s", pathAlgo));
            throw new AbortAnalysisException();
        }
    }

    private static CallbackAnalyzer parseCallbackAnalyzer(String callbackAnalyzer) {
        if (callbackAnalyzer.equalsIgnoreCase("DEFAULT"))
            return CallbackAnalyzer.Default;
        else if (callbackAnalyzer.equalsIgnoreCase("FAST"))
            return CallbackAnalyzer.Fast;
        else {
            System.err.println(String.format("Invalid callback analysis algorithm: %s", callbackAnalyzer));
            throw new AbortAnalysisException();
        }
    }

    private static DataFlowSolver parseDataFlowSolver(String solver) {
        if (solver.equalsIgnoreCase("CONTEXTFLOWSENSITIVE"))
            return DataFlowSolver.ContextFlowSensitive;
        else if (solver.equalsIgnoreCase("FLOWINSENSITIVE"))
            return DataFlowSolver.FlowInsensitive;
        else {
            System.err.println(String.format("Invalid data flow solver: %s", solver));
            throw new AbortAnalysisException();
        }
    }

    private static AliasingAlgorithm parseAliasAlgorithm(String aliasAlgo) {
        if (aliasAlgo.equalsIgnoreCase("NONE"))
            return AliasingAlgorithm.None;
        else if (aliasAlgo.equalsIgnoreCase("FLOWSENSITIVE"))
            return AliasingAlgorithm.FlowSensitive;
        else if (aliasAlgo.equalsIgnoreCase("PTSBASED"))
            return AliasingAlgorithm.PtsBased;
        else if (aliasAlgo.equalsIgnoreCase("LAZY"))
            return AliasingAlgorithm.Lazy;
        else {
            System.err.println(String.format("Invalid aliasing algorithm: %s", aliasAlgo));
            throw new AbortAnalysisException();
        }
    }

    private static CodeEliminationMode parseCodeEliminationMode(String eliminationMode) {
        if (eliminationMode.equalsIgnoreCase("NONE"))
            return CodeEliminationMode.NoCodeElimination;
        else if (eliminationMode.equalsIgnoreCase("PROPAGATECONSTS"))
            return CodeEliminationMode.PropagateConstants;
        else if (eliminationMode.equalsIgnoreCase("REMOVECODE"))
            return CodeEliminationMode.RemoveSideEffectFreeCode;
        else {
            System.err.println(String.format("Invalid code elimination mode: %s", eliminationMode));
            throw new AbortAnalysisException();
        }
    }

    private static CallbackSourceMode parseCallbackSourceMode(String callbackMode) {
        if (callbackMode.equalsIgnoreCase("NONE"))
            return CallbackSourceMode.NoParametersAsSources;
        else if (callbackMode.equalsIgnoreCase("ALL"))
            return CallbackSourceMode.AllParametersAsSources;
        else if (callbackMode.equalsIgnoreCase("SOURCELIST"))
            return CallbackSourceMode.SourceListOnly;
        else {
            System.err.println(String.format("Invalid callback source mode: %s", callbackMode));
            throw new AbortAnalysisException();
        }
    }

    private static PathReconstructionMode parsePathReconstructionMode(String pathReconstructionMode) {
        if (pathReconstructionMode.equalsIgnoreCase("NONE"))
            return PathReconstructionMode.NoPaths;
        else if (pathReconstructionMode.equalsIgnoreCase("FAST"))
            return PathReconstructionMode.Fast;
        else if (pathReconstructionMode.equalsIgnoreCase("PRECISE"))
            return PathReconstructionMode.Precise;
        else {
            System.err.println(String.format("Invalid path reconstruction mode: %s", pathReconstructionMode));
            throw new AbortAnalysisException();
        }
    }

    private static ImplicitFlowMode parseImplicitFlowMode(String implicitFlowMode) {
        if (implicitFlowMode.equalsIgnoreCase("NONE"))
            return ImplicitFlowMode.NoImplicitFlows;
        else if (implicitFlowMode.equalsIgnoreCase("ARRAYONLY"))
            return ImplicitFlowMode.ArrayAccesses;
        else if (implicitFlowMode.equalsIgnoreCase("ALL"))
            return ImplicitFlowMode.AllImplicitFlows;
        else {
            System.err.println(String.format("Invalid implicit flow mode: %s", implicitFlowMode));
            throw new AbortAnalysisException();
        }
    }

    private static StaticFieldTrackingMode parseStaticFlowMode(String staticFlowMode) {
        if (staticFlowMode.equalsIgnoreCase("NONE"))
            return StaticFieldTrackingMode.None;
        else if (staticFlowMode.equalsIgnoreCase("CONTEXTFLOWSENSITIVE"))
            return StaticFieldTrackingMode.ContextFlowSensitive;
        else if (staticFlowMode.equalsIgnoreCase("CONTEXTFLOWINSENSITIVE"))
            return StaticFieldTrackingMode.ContextFlowInsensitive;
        else {
            System.err.println(String.format("Invalid static flow tracking mode: %s", staticFlowMode));
            throw new AbortAnalysisException();
        }
    }

    /**
     * Parses the given command-line options and fills the given configuration
     * object accordingly
     *
     * @param cmd    The command line to parse
     * @param config The configuration object to fill
     */
    private static void parseCommandLineOptions(CommandLine cmd, InfoflowAndroidConfiguration config, Set<String> filesToSkip) {
        // Files
        {
            String apkFile = cmd.getOptionValue(OPTION_APK_FILE);
            if (apkFile != null && !apkFile.isEmpty())
                config.getAnalysisFileConfig().setTargetAPKFile(apkFile);
        }
        {
            String platformsDir = cmd.getOptionValue(OPTION_PLATFORMS_DIR);
            if (platformsDir != null && !platformsDir.isEmpty())
                config.getAnalysisFileConfig().setAndroidPlatformDir(platformsDir);
        }
        {
            String sourcesSinks = cmd.getOptionValue(OPTION_SOURCES_SINKS_FILE);
            if (sourcesSinks != null && !sourcesSinks.isEmpty())
                config.getAnalysisFileConfig().setSourceSinkFile(sourcesSinks);
        }
        {
            String outputFile = cmd.getOptionValue(OPTION_OUTPUT_FILE);
            if (outputFile != null && !outputFile.isEmpty())
                config.getAnalysisFileConfig().setOutputFile(outputFile);
        }
        {
            String additionalClasspath = cmd.getOptionValue(OPTION_ADDITIONAL_CLASSPATH);
            if (additionalClasspath != null && !additionalClasspath.isEmpty())
                config.getAnalysisFileConfig().setAdditionalClasspath(additionalClasspath);
        }

        {
            Integer depth = getIntOption(cmd, OPTION_RETURN_DEPTH);
            if (depth != null)
                Configuration.v().setReturnDepth(depth);
        }

        {
            Integer size = getIntOption(cmd, OPTION_ACCESS_PATH_SIZE);
            if (size != null)
                Configuration.v().setAccessPathSize(size);
        }
        // Timeouts
        {
            Integer timeout = getIntOption(cmd, OPTION_TIMEOUT);
            if (timeout != null)
                config.setDataFlowTimeout(timeout);
        }
        {
            Integer timeout = getIntOption(cmd, OPTION_CALLBACK_TIMEOUT);
            if (timeout != null)
                config.getCallbackConfig().setCallbackAnalysisTimeout(timeout);
        }
        {
            Integer timeout = getIntOption(cmd, OPTION_RESULT_TIMEOUT);
            if (timeout != null)
                config.getPathConfiguration().setPathReconstructionTimeout(timeout);
        }

        // Optional features
        if (cmd.hasOption(OPTION_NO_STATIC_FLOWS))
            config.setStaticFieldTrackingMode(StaticFieldTrackingMode.None);
        if (cmd.hasOption(OPTION_NO_CALLBACK_ANALYSIS))
            config.getCallbackConfig().setEnableCallbacks(false);
        if (cmd.hasOption(OPTION_NO_EXCEPTIONAL_FLOWS))
            config.setEnableExceptionTracking(false);
        if (cmd.hasOption(OPTION_NO_TYPE_CHECKING))
            config.setEnableTypeChecking(false);
        if (cmd.hasOption(OPTION_REFLECTION))
            config.setEnableReflection(true);
        // Individual settings
        {
            Integer aplength = getIntOption(cmd, OPTION_ACCESS_PATH_LENGTH);
            if (aplength != null)
                config.getAccessPathConfiguration().setAccessPathLength(aplength);
        }
        if (cmd.hasOption(OPTION_NO_THIS_CHAIN_REDUCTION))
            config.getAccessPathConfiguration().setUseThisChainReduction(false);
        if (cmd.hasOption(OPTION_FLOW_INSENSITIVE_ALIASING))
            config.setFlowSensitiveAliasing(false);
        if (cmd.hasOption(OPTION_COMPUTE_PATHS))
            config.getPathConfiguration().setPathReconstructionMode(PathReconstructionMode.Fast);
        if (cmd.hasOption(OPTION_ONE_SOURCE))
            config.setOneSourceAtATime(true);
        if (cmd.hasOption(OPTION_ONE_COMPONENT))
            config.setOneComponentAtATime(true);
        if (cmd.hasOption(OPTION_SEQUENTIAL_PATHS))
            config.getPathConfiguration().setSequentialPathProcessing(true);
        if (cmd.hasOption(OPTION_LOG_SOURCES_SINKS))
            config.setLogSourcesAndSinks(true);
        if (cmd.hasOption(OPTION_MERGE_DEX_FILES))
            config.setMergeDexFiles(true);
        if (cmd.hasOption(OPTION_PATH_SPECIFIC_RESULTS))
            //InfoflowConfiguration.setPathAgnosticResults(false); //Doesn't work!
        if (cmd.hasOption(OPTION_SINGLE_JOIN_POINT))
            config.getSolverConfiguration().setSingleJoinPointAbstraction(true);
        {
            Integer maxCallbacks = getIntOption(cmd, OPTION_MAX_CALLBACKS_COMPONENT);
            if (maxCallbacks != null)
                config.getCallbackConfig().setMaxCallbacksPerComponent(maxCallbacks);
        }
        {
            Integer maxDepth = getIntOption(cmd, OPTION_MAX_CALLBACKS_DEPTH);
            if (maxDepth != null)
                config.getCallbackConfig().setMaxAnalysisCallbackDepth(maxDepth);
        }

        // Inter-component communication
        if (cmd.hasOption(OPTION_ICC_NO_PURIFY))
            config.getIccConfig().setIccResultsPurify(false);
        {
            String iccModel = cmd.getOptionValue(OPTION_ICC_MODEL);
            if (iccModel != null && !iccModel.isEmpty())
                config.getIccConfig().setIccModel(iccModel);
        }

        // Modes and algorithms
        {
            String cgalgo = cmd.getOptionValue(OPTION_CALLGRAPH_ALGO);
            if (cgalgo != null && !cgalgo.isEmpty())
                config.setCallgraphAlgorithm(parseCallgraphAlgorithm(cgalgo));
        }
        {
            String layoutMode = cmd.getOptionValue(OPTION_LAYOUT_MODE);
            if (layoutMode != null && !layoutMode.isEmpty())
                config.getSourceSinkConfig().setLayoutMatchingMode(parseLayoutMatchingMode(layoutMode));
        }
        {
            String pathAlgo = cmd.getOptionValue(OPTION_PATH_RECONSTRUCTION_ALGO);
            if (pathAlgo != null && !pathAlgo.isEmpty())
                config.getPathConfiguration().setPathBuildingAlgorithm(parsePathReconstructionAlgo(pathAlgo));
        }
        {
            String callbackAnalyzer = cmd.getOptionValue(OPTION_CALLBACK_ANALYZER);
            if (callbackAnalyzer != null && !callbackAnalyzer.isEmpty())
                config.getCallbackConfig().setCallbackAnalyzer(parseCallbackAnalyzer(callbackAnalyzer));
        }
        {
            String solver = cmd.getOptionValue(OPTION_DATA_FLOW_SOLVER);
            if (solver != null && !solver.isEmpty())
                config.getSolverConfiguration().setDataFlowSolver(parseDataFlowSolver(solver));
        }
        {
            String aliasAlgo = cmd.getOptionValue(OPTION_ALIAS_ALGO);
            if (aliasAlgo != null && !aliasAlgo.isEmpty())
                config.setAliasingAlgorithm(parseAliasAlgorithm(aliasAlgo));
        }
        {
            String eliminationMode = cmd.getOptionValue(OPTION_CODE_ELIMINATION_MODE);
            if (eliminationMode != null && !eliminationMode.isEmpty())
                config.setCodeEliminationMode(parseCodeEliminationMode(eliminationMode));
        }
        {
            String callbackMode = cmd.getOptionValue(OPTION_CALLBACK_SOURCE_MODE);
            if (callbackMode != null && !callbackMode.isEmpty())
                config.getSourceSinkConfig().setCallbackSourceMode(parseCallbackSourceMode(callbackMode));
        }
        {
            String pathMode = cmd.getOptionValue(OPTION_PATH_RECONSTRUCTION_MODE);
            if (pathMode != null && !pathMode.isEmpty())
                config.getPathConfiguration().setPathReconstructionMode(parsePathReconstructionMode(pathMode));
        }
        {
            String implicitMode = cmd.getOptionValue(OPTION_IMPLICIT_FLOW_MODE);
            if (implicitMode != null && !implicitMode.isEmpty())
                config.setImplicitFlowMode(parseImplicitFlowMode(implicitMode));
        }
        {
            String staticFlowMode = cmd.getOptionValue(OPTION_STATIC_FLOW_TRACKING_MODE);
            if (staticFlowMode != null && !staticFlowMode.isEmpty())
                config.setStaticFieldTrackingMode(parseStaticFlowMode(staticFlowMode));
        }

        {
            String[] toSkip = cmd.getOptionValues(OPTION_SKIP_APK_FILE);
            if (toSkip != null && toSkip.length > 0) {
                for (String skipAPK : toSkip)
                    filesToSkip.add(skipAPK);
            }
        }

        // We have some options to quickly configure FlowDroid for a certain mode or use
        // case
        if (cmd.hasOption(OPTION_ANALYZE_FRAMEWORKS)) {
            config.setExcludeSootLibraryClasses(false);
            config.setIgnoreFlowsInSystemPackages(false);
        }
    }

    private static Integer getIntOption(CommandLine cmd, String option) {
        String str = cmd.getOptionValue(option);
        if (str == null || str.isEmpty())
            return null;
        else
            return Integer.parseInt(str);
    }

    /**
     * Loads the data flow configuration from the given file
     *
     * @param configFile The configuration file from which to load the data flow
     *                   configuration
     * @return The loaded data flow configuration
     */
    private InfoflowAndroidConfiguration loadConfigurationFile(String configFile) {
        try {
            InfoflowAndroidConfiguration config = new InfoflowAndroidConfiguration();
            XMLConfigurationParser.fromFile(configFile).parse(config);
            return config;
        } catch (IOException e) {
            System.err.println("Could not parse configuration file: " + e.getMessage());
            return null;
        }
    }


}