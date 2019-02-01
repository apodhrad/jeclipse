package org.apodhrad.jeclipse.manager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apodhrad.jdownload.manager.JDownloadManager;
import org.apodhrad.jdownload.manager.hash.Hash;
import org.apodhrad.jdownload.manager.hash.MD5Hash;
import org.apodhrad.jdownload.manager.hash.NullHash;
import org.apodhrad.jeclipse.manager.matcher.FileNameStartsWith;
import org.apodhrad.jeclipse.manager.util.FileSearch;
import org.apodhrad.jeclipse.manager.util.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class represents an eclipse instance
 * 
 * @author apodhrad
 * 
 */
public class Eclipse {

	public static final String ECLIPSE_MARS_JEE_VERSION = "jee-mars-1";
	public static final String ECLIPSE_LUNA_JEE_VERSION = "jee-luna-SR2";
	@SuppressWarnings("serial")
	public static final Map<String, String> ECLIPSE_MD5 = new HashMap<String, String>() {
		{
			put("eclipse-jee-luna-SR2-linux-gtk.tar.gz", "d8e1b995e95dbec95d69d62ddf6f94f6");
			put("eclipse-jee-luna-SR2-linux-gtk-x86_64.tar.gz", "be9391112776755e898801d3f3f51b74");
			put("eclipse-jee-luna-SR2-macosx-cocoa.tar.gz", "46f741dab6e94f5509dd2ecfe0e1d295");
			put("eclipse-jee-luna-SR2-macosx-cocoa-x86_64.tar.gz", "8e8b8ae2c66838d0cc3bf0b316576212");
			put("eclipse-jee-luna-SR2-win32.zip", "3e36cea1287c8e4b602eb0510c8a1dc1");
			put("eclipse-jee-luna-SR2-win32-x86_64.zip", "f3820cea9fae6a37275999e6a01ddc01");
			put("eclipse-jee-mars-1-linux-gtk.tar.gz", "dce33eba239a4f1e745976e7cb576569");
			put("eclipse-jee-mars-1-linux-gtk-x86_64.tar.gz", "72a722a59a43e8ed6c47ae279fb3d355");
			put("eclipse-jee-mars-1-macosx-cocoa-x86_64.tar.gz", "8d053622066166886d4a7116fd18dc93");
			put("eclipse-jee-mars-1-win32.zip", "fd0e5ceebc2a5b40274cb6146e78a4c3");
			put("eclipse-jee-mars-1-win32-x86_64.zip", "2917778fd8604cd2ba58453c6cb7a6bc");
		}
	};

	public static final String ECLIPSE_DEFAULT_VERSION = ECLIPSE_LUNA_JEE_VERSION;
	public static final String ECLIPSE_DEFAULT_MIRROR = "http://www.eclipse.org/downloads/download.php?r=1&file=/technology/epp/downloads/release";

	public static final String TIMEOUT_PROPERTY = "jeclipse.timeout";

	private static final String LAUNCHER_PREFIX = "org.eclipse.equinox.launcher_";

	private static Logger log = LoggerFactory.getLogger(Eclipse.class);
	private File jarFile;
	private Set<String> updateSites;
	private Set<String> ignoredFeatures;

	public Eclipse(String path) {
		this(new File(path));
	}

	public Eclipse(File file) {
		List<File> launchers = null;
		if (file.isDirectory()) {
			launchers = new FileSearch().find(file, new FileNameStartsWith(LAUNCHER_PREFIX));
			for (File launcher: launchers) {
				if (isEclipseStructure(launcher)) {
					file = launcher;
				}
			}
		}
		if (!isEclipseStructure(file)) {
			throw new EclipseException("Cannot find any eclipse structure in '" + file.getAbsolutePath() + "'");
		}
		this.jarFile = file;
		this.updateSites = new HashSet<String>();
		this.ignoredFeatures = new HashSet<String>();
	}

	public File getLauncher() {
		return jarFile;
	}

	public File getHomeDirectory() {
		return getLauncher().getParentFile().getParentFile();
	}

	private static boolean isEclipseStructure(File launcher) {
		if (launcher.isDirectory() || !launcher.exists()) {
			return false;
		}
		if (!launcher.getName().startsWith(LAUNCHER_PREFIX)) {
			return false;
		}
		File pluginsFolder = launcher.getParentFile();
		if (pluginsFolder == null || !pluginsFolder.getName().equals("plugins")) {
			return false;
		}
		File featuresFolder = new File(pluginsFolder.getParentFile(), "features");
		if (featuresFolder == null || !featuresFolder.exists()) {
			return false;
		}
		return true;
	}

	public Bundle[] getFeatures() {
		File featuresDir = new File(jarFile.getParentFile().getParentFile(), "features");
		File[] featureFiles = featuresDir.listFiles();
		Bundle[] featureBundles = new Bundle[featureFiles.length];
		for (int i = 0; i < featureFiles.length; i++) {
			featureBundles[i] = new Bundle(featureFiles[i]);
		}
		return featureBundles;
	}

	public Bundle[] getPlugins() {
		File pluginsDir = new File(jarFile.getParentFile().getParentFile(), "plugins");
		File[] pluginFiles = pluginsDir.listFiles();
		Bundle[] pluginBundles = new Bundle[pluginFiles.length];
		for (int i = 0; i < pluginFiles.length; i++) {
			pluginBundles[i] = new Bundle(pluginFiles[i]);
		}
		return pluginBundles;
	}

	public void addUpdateSite(String updateSite) {
		updateSites.add(updateSite);
	}

	public Set<String> getUpdateSites() {
		return Collections.unmodifiableSet(updateSites);
	}

	public void ignoreFeature(String regex) {
		ignoredFeatures.add(regex);
	}

	public Set<String> getIgnoredFeatures() {
		return ignoredFeatures;
	}

	public boolean isFeatureIgnored(String feature) {
		for (String ignoredFeature : ignoredFeatures) {
			if (feature.matches(ignoredFeature)) {
				return true;
			}
		}
		return false;
	}

	public List<Bundle> listFeatures() {
		return listFeatures(getUpdateSites());
	}

	public List<Bundle> listFeatures(Collection<String> updateSites) {
		return listFeatures(collectionToString(updateSites));
	}

	public List<Bundle> listFeatures(String updateSite) {
		List<String> command = new ArrayList<String>();
		command.add("-application");
		command.add("org.eclipse.equinox.p2.director");
		command.add("-consoleLog");
		command.add("-followReferences");
		command.add("-nosplash");
		command.add("-repository");
		command.add(updateSite);
		command.add("-list");

		List<Bundle> features = new ArrayList<Bundle>();
		EclipseExecutionOutput eclipseExecutionOutput = execute(command);
		for (String line : eclipseExecutionOutput.getLines()) {
			if (line.contains(".feature.group=")) {
				String[] parser = line.split(".feature.group=");
				features.add(new Bundle(parser[0], parser[1]));
			}
		}
		return features;
	}

	public EclipseExecutionOutput installFeatures(String... features) {
		return installFeatures(true, features);
	}

	public EclipseExecutionOutput installFeatures(boolean followReferences, String... features) {
		return installFeature(followReferences, arrayToString(features));
	}

	public EclipseExecutionOutput installFeatures(Collection<String> features) {
		return installFeatures(true, features);
	}

	public EclipseExecutionOutput installFeatures(boolean followReferences, Collection<String> features) {
		return installFeature(followReferences, collectionToString(features));
	}

	public EclipseExecutionOutput installAllFeaturesFromUpdateSite(String updateSite) {
		return installAllFeaturesFromUpdateSite(true, updateSite);
	}

	public EclipseExecutionOutput installAllFeaturesFromUpdateSite(boolean followReferences, String updateSite) {
		return installAllFeaturesFromUpdateSite(followReferences, updateSite, true);
	}

	public EclipseExecutionOutput installAllFeaturesFromUpdateSite(boolean followReferences, String updateSite,
			boolean ignoreFeatures) {
		List<Bundle> features = listFeatures(updateSite);
		List<String> listOfUIs = new ArrayList<String>();
		for (Bundle feature : features) {
			if (ignoreFeatures && isFeatureIgnored(feature.getName())) {
				continue;
			}
			listOfUIs.add(feature.getName() + ".feature.group");
		}
		addUpdateSite(updateSite);
		return installFeatures(followReferences, listOfUIs);
	}

	public EclipseExecutionOutput installFeature(String feature) {
		return installFeature(true, feature);
	}

	public EclipseExecutionOutput installFeature(boolean followReferences, String feature) {
		List<String> command = new ArrayList<String>();
		command.add("-application");
		command.add("org.eclipse.equinox.p2.director");
		command.add("-consoleLog");
		if (followReferences) {
			command.add("-followReferences");
		}
		command.add("-nosplash");
		command.add("-repository");
		command.add(collectionToString(updateSites));
		command.add("-installIUs");
		command.add(feature);

		EclipseExecutionOutput output = execute(command);
		for (String line : output.getLines()) {
			if (line.contains("Operation completed in")) {
				return output;
			}
		}
		throw new EclipseException("Installation failed.");
	}

	public EclipseExecutionOutput execute(List<String> command) {
		return execute(command.toArray(new String[command.size()]));
	}

	public EclipseExecutionOutput execute(String[] command) {
		EclipseExecutionOutput eclipseExecutionOutput = new EclipseExecutionOutput();
		JarRunner jarRunner = new JarRunner(jarFile.getAbsolutePath(), command);
		jarRunner.setOutput(eclipseExecutionOutput);
		jarRunner.setTimeout(getJEclipseTimeout());
		jarRunner.run();
		return eclipseExecutionOutput;
	}

	protected static int getJEclipseTimeout() {
		String jEclipseTimeout = System.getProperty(TIMEOUT_PROPERTY);
		if (jEclipseTimeout != null) {
			if (jEclipseTimeout.matches("\\d+")) {
				log.info("Eclipse execution timeout set to " + jEclipseTimeout + " s.");
				return Integer.valueOf(jEclipseTimeout);
			} else {
				log.warn("The variable '" + TIMEOUT_PROPERTY + "' must be numeric but was '" + jEclipseTimeout + "'");
			}
		}
		log.info("Eclipse execution timeout set to default value which is " + JarRunner.DEFAULT_TIMEOUT + " s.");
		return JarRunner.DEFAULT_TIMEOUT;
	}

	private static String collectionToString(Collection<String> collection) {
		if (collection.size() == 0) {
			return "";
		}
		StringBuffer sb = new StringBuffer();
		for (String item : collection) {
			sb.append(",").append(item);
		}
		return sb.substring(1);
	}

	private static String arrayToString(String[] array) {
		if (array.length == 0) {
			return "";
		}
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < array.length; i++) {
			sb.append(",").append(array[i]);
		}
		return sb.substring(1);
	}

	public void addProgramArgument(String... args) {
		File iniFile = getIniFile();
		List<String> originalLines = null;
		try {
			originalLines = FileUtils.readLines(iniFile);
		} catch (IOException e) {
			throw new RuntimeException("Cannot read from '" + iniFile.getAbsolutePath() + "'", e);
		}

		List<String> revisedLines = new ArrayList<String>();
		for (String line : originalLines) {
			if (line.startsWith("-vmargs") || line.startsWith("--launcher.appendVmargs")) {
				for (String arg : args) {
					revisedLines.add(arg);
				}
				args = new String[] {};
			}
			revisedLines.add(line);
		}

		try {
			FileUtils.writeLines(iniFile, revisedLines);
		} catch (IOException e) {
			throw new RuntimeException("Cannot write to '" + iniFile.getAbsolutePath() + "'", e);
		}
	}

	public void addVMArgument(String... args) {
		File iniFile = getIniFile();
		List<String> originalLines = null;
		try {
			originalLines = FileUtils.readLines(iniFile);
		} catch (IOException e) {
			throw new RuntimeException("Cannot read from '" + iniFile.getAbsolutePath() + "'", e);
		}

		List<String> revisedLines = new ArrayList<String>(originalLines);
		for (String arg : args) {
			revisedLines.add(arg);
		}

		try {
			FileUtils.writeLines(iniFile, revisedLines);
		} catch (IOException e) {
			throw new RuntimeException("Cannot write to '" + iniFile.getAbsolutePath() + "'", e);
		}
	}

	public File getIniFile() {
		File eclipseDir = jarFile.getParentFile().getParentFile();
		for (File file : eclipseDir.listFiles()) {
			if (file.getName().endsWith(".ini")) {
				return file;
			}
		}
		for (File dir : eclipseDir.listFiles()) {
			if (dir.getName().endsWith(".app") && dir.isDirectory()) {
				for (File file : new File(dir, "Contents/MacOS").listFiles()) {
					if (file.getName().endsWith(".ini")) {
						return file;
					}
				}
			}
		}
		throw new RuntimeException("Cannot find .ini file at '" + eclipseDir.getAbsolutePath() + "'");
	}

	public static Eclipse installEclipse(File target) throws IOException {
		return installEclipse(target, ECLIPSE_DEFAULT_VERSION);
	}

	public static Eclipse installEclipse(File target, String eclipseVersion) throws IOException {
		return installEclipse(target, eclipseVersion, ECLIPSE_DEFAULT_MIRROR);
	}

	public static Eclipse installEclipse(File target, String eclipseVersion, String eclipseMirror) throws IOException {
		return installEclipse(target, eclipseVersion, eclipseMirror, null);
	}

	public static Eclipse installEclipse(File target, String eclipseVersion, Hash hash) throws IOException {
		return installEclipse(target, eclipseVersion, ECLIPSE_DEFAULT_MIRROR, hash);
	}

	public static Eclipse installEclipse(File target, String eclipseVersion, String eclipseMirror, Hash hash)
			throws IOException {
		if (hash == null) {
			String md5sum = ECLIPSE_MD5.get(getEclipseInstaller(eclipseVersion));
			hash = md5sum == null ? new NullHash() : new MD5Hash(md5sum);
		}

		JDownloadManager manager = new JDownloadManager();
		String eclipseFolder = "eclipse";
		if (OS.isMac()) {
			manager.download(getEclipseUrl(eclipseVersion, eclipseMirror), target, hash);
			unpackDMG(new File(target, getEclipseInstaller(eclipseVersion)), target);
			eclipseFolder = "Eclipse.app";
		} else {
			manager.download(getEclipseUrl(eclipseVersion, eclipseMirror), target, true, hash);
		}
		return new Eclipse(new File(target, eclipseFolder));
	}

	private static void unpackDMG(File file, File target) {
		try {
		Process process = Runtime.getRuntime().exec("hdiutil attach " + file);
		process.waitFor();
		process = Runtime.getRuntime().exec("cp -R /Volumes/Eclipse/Eclipse.app " + target);
		process.waitFor();
		process = Runtime.getRuntime().exec("hdiutil detach /Volumes/Eclipse");
		process.waitFor();
		} catch (Exception e) {
			log.error("Installation failed!", e);
		}
		
	}

	private static String getEclipseUrl(String eclipseVersion, String eclipseMirror) {
		String[] version = eclipseVersion.split("-");
		return eclipseMirror + "/" + version[1] + "/" + version[2] + "/" + getEclipseInstaller(eclipseVersion);
	}

	private static String getEclipseInstaller(String eclipseVersion) {
		String os_property = OS.getName();
		String arch_property = OS.getArch();

		String platform = null;
		String archive = "zip";

		if (os_property.contains("linux")) {
			platform = "linux-gtk";
			archive = "tar.gz";
		} else if (os_property.contains("win")) {
			platform = "win32";
			archive = "zip";
		} else if (os_property.contains("mac")) {
			platform = "macosx-cocoa";
			archive = "dmg";
		}

		if (platform == null) {
			throw new RuntimeException("Unknown platform '" + os_property + "'");
		}

		if (arch_property.contains("64")) {
			platform += "-x86_64";
		}

		return "eclipse-" + eclipseVersion + "-" + platform + "." + archive;
	}

	public void mirrorRepository(String source, File destination) throws IOException {
		if (destination.exists()) {
			FileUtils.forceDelete(destination);
		}
		destination.mkdirs();

		List<String> commandMetadata = new ArrayList<String>();
		commandMetadata.add("-application");
		commandMetadata.add("org.eclipse.equinox.p2.metadata.repository.mirrorApplication");
		commandMetadata.add("-source");
		commandMetadata.add(source);
		commandMetadata.add("-destination");
		commandMetadata.add(destination.getAbsolutePath());
		commandMetadata.add("-nosplash");
		execute(commandMetadata);

		List<String> commandArtifact = new ArrayList<String>();
		commandArtifact.add("-application");
		commandArtifact.add("org.eclipse.equinox.p2.artifact.repository.mirrorApplication");
		commandArtifact.add("-source");
		commandArtifact.add(source);
		commandArtifact.add("-destination");
		commandArtifact.add(destination.getAbsolutePath());
		commandArtifact.add("-nosplash");
		execute(commandArtifact);
	}

	public List<EclipseLogMessage> getLogMessages(int severity) throws IOException {
		List<EclipseLogMessage> allMessages = getLogMessages();
		List<EclipseLogMessage> messages = new ArrayList<EclipseLogMessage>();
		for (EclipseLogMessage message : allMessages) {
			if (message.getSeverity() >= severity) {
				messages.add(message);
			}
		}
		return messages;
	}

	public List<EclipseLogMessage> getLogMessages() throws IOException {
		List<EclipseLogMessage> messages = new ArrayList<EclipseLogMessage>();
		File logFile = new File(getHomeDirectory(), "workspace/.metadata/.log");
		if (logFile.exists()) {
			BufferedReader in = new BufferedReader(new FileReader(logFile));
			String line = null;
			while ((line = in.readLine()) != null) {
				int index = 1;
				int severity = 0;
				String source = null;
				String message = null;
				if (line.startsWith("!ENTRY") || line.startsWith("!SUBENTRY")) {
					if (line.startsWith("!SUBENTRY")) {
						index = 2;
					}
					String[] parts = line.split(" ");
					source = parts[index];
					severity = Integer.valueOf(parts[index + 1]);
					line = in.readLine();
					if (line.startsWith("!MESSAGE")) {
						message = line.substring("!MESSAGE".length() + 1);
					}
					messages.add(new EclipseLogMessage(severity, source, message));
				}
			}
			in.close();
		}
		return messages;
	}

}
