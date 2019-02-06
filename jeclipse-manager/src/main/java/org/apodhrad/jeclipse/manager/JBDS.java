package org.apodhrad.jeclipse.manager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.StringJoiner;

import org.apache.commons.io.FileUtils;
import org.apodhrad.jdownload.manager.JDownloadManager;
import org.apodhrad.jdownload.manager.hash.Hash;
import org.apodhrad.jdownload.manager.hash.NullHash;
import org.apodhrad.jeclipse.manager.util.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author apodhrad
 *
 */
public class JBDS extends Eclipse {

	private static Logger log = LoggerFactory.getLogger(JBDS.class);
	
	private File installerJarFile;

	public JBDS(String path) {
		super(path);
	}

	public JBDS(File file) {
		super(file);
	}

	public File getInstallerJarFile() {
		return installerJarFile;
	}

	protected void setInstallerJarFile(File installerJarFile) {
		this.installerJarFile = installerJarFile;
	}

	public static JBDS installJBDS(File target, String url) throws IOException {
		return installJBDS(target, url, new NullHash(), null, new String[] {}, new String[] {});
	}

	public static JBDS installJBDS(File target, String url, String jreLocation) throws IOException {
		return installJBDS(target, url, new NullHash(), jreLocation, new String[] {}, new String[] {});
	}

	public static JBDS installJBDS(File target, String url, Hash hash) throws IOException {
		return installJBDS(target, url, hash, null, new String[] {}, new String[] {});
	}

	public static JBDS installJBDS(File target, String url, Hash hash, String jreLocation, String[] runtimes, String[] ius)
			throws IOException {
		JDownloadManager manager = new JDownloadManager();
		File installerJarFile = manager.download(url, target, hash);
		return installJBDS(target, installerJarFile, jreLocation, runtimes, ius);
	}

	public static JBDS installJBDS(File target, File installerJarFile, String jreLocation, String[] runtimes, String[] ius)
			throws IOException {
		// Install JBDS
		String installationFile = null;
		try {
			installationFile = createInstallationFile(target, installerJarFile, jreLocation, runtimes, ius);
		} catch (IOException ioe) {
			ioe.printStackTrace();
			throw new RuntimeException("Exception occured during creating installation file");
		}

		// Switch IzPack mode to privileged on Windows
		if (OS.isWindows()) {
			System.setProperty("izpack.mode", "privileged");
		}

		JarRunner jarRunner = new JarRunner(installerJarFile.getAbsolutePath(), installationFile);
		jarRunner.setOutput(new EclipseExecutionOutput());
		jarRunner.setTimeout(getJEclipseTimeout());
		jarRunner.run();

		JBDS jbds = new JBDS(new File(target, "jbdevstudio"));
		jbds.setInstallerJarFile(installerJarFile);
		return jbds;
	}

	public static String createInstallationFile(File target, File installerJarFile, String jreLocation, String[] runtimes, String[] ius)
			throws IOException {
		JBDSConfig config = new JBDSConfig();
		config.setTarget(target);
		config.setInstallerJarFile(installerJarFile);
		config.setJreLocation(jreLocation);
		for (String iu : ius) {
			config.addInstallableUnit(iu);
		}
		for (String runtime: runtimes) {
			config.addRuntime(runtime);
		}
		return createInstallationFile(config);
	}

	public static String createInstallationFile(JBDSConfig config) throws IOException {
		File jre = OS.getJre(config.getJreLocation());
		log.info("JRE: " + jre);
		if (jre == null) {
			throw new IllegalStateException("Cannot find JRE location!");
		}

		StringJoiner iuList = new StringJoiner(",");
		iuList.add("com.jboss.devstudio.core.package");
		iuList.add("org.testng.eclipse.feature.group");
		for (String feature : config.getInstallabelUnits()) {
			iuList.add(feature);
		}

		StringJoiner runtimeList = new StringJoiner(",");
		runtimeList.setEmptyValue("");
		for (String runtime : config.getRuntimes()) {
			runtimeList.add(runtime);
		}

		String group = "devstudio";

		String jbdsVersion = getJBDSVersion(config.getInstallerJarFile());
		StringJoiner productList = new StringJoiner(",");
		productList.add(startsWithOneOf(jbdsVersion, "10", "11", "12") ? "devstudio" : "jbds");
		if (!config.getInstallabelUnits().isEmpty()) {
			productList.add(startsWithOneOf(jbdsVersion, "10", "11", "12") ? "devstudio-is" : "jbdsis");
		}

		String dest = new File(config.getTarget(), "jbdevstudio").getAbsolutePath();

		String tempFile = new File(config.getTarget(), "/install.xml").getAbsolutePath();
		String targetFile = new File(config.getTarget(), "/installation.xml").getAbsolutePath();

		String sourceFile = "/install.xml";
		if (jbdsVersion != null && jbdsVersion.startsWith("8")) {
			sourceFile = "/install-8.xml";
		}
		if (jbdsVersion != null && jbdsVersion.startsWith("9")) {
			sourceFile = "/install-9.xml";
		}
		if (jbdsVersion != null && jbdsVersion.startsWith("10")) {
			sourceFile = "/install-10.xml";
			if (runtimeList.length() > 0) {
				sourceFile = "/install-10-runtime.xml";
			}
			if (config.getInstallerJarFile().getName().contains("eap")) {
				group = "jbosseap";
				sourceFile = "/install-10-runtime.xml";
			}
		}
		if (jbdsVersion != null && startsWithOneOf(jbdsVersion, "11", "12")) {
			sourceFile = "/install-11.xml";
			if (runtimeList.length() > 0) {
				sourceFile = "/install-11-runtime.xml";
			}
			if (config.getInstallerJarFile().getName().contains("eap")) {
				group = "jbosseap";
				sourceFile = "/install-11-runtime.xml";
			}
		}
		URL url = JBDS.class.getResource(sourceFile);

		FileUtils.copyURLToFile(url, new File(tempFile));
		BufferedReader in = new BufferedReader(new FileReader(tempFile));
		BufferedWriter out = new BufferedWriter(new FileWriter(targetFile));
		String line = null;
		while ((line = in.readLine()) != null) {
			line = line.replace("@DEST@", dest);
			line = line.replace("@GROUP@", group);
			line = line.replace("@JRE@", jre.getAbsolutePath());
			line = line.replace("@IUS@", iuList.toString());
			line = line.replace("@PRODUCTS@", productList.toString());
			line = line.replace("@RUNTIMES@", runtimeList.toString());
			out.write(line);
			out.newLine();
		}
		out.flush();
		out.close();
		in.close();

		new File(tempFile).delete();
		return targetFile;
	}

	private static boolean startsWithOneOf(String text, String... prefixes) {
		for (String prefix: prefixes) {
			if (text.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	public static String getJBDSVersion(File installer) {
		return getJBDSVersion(installer.getName());
	}

	public static String getJBDSVersion(String installer) {
		String[] part = installer.split("-");
		for (int i = 0; i < part.length; i++) {
			if (part[i].contains(".")) {
				return part[i];
			}
		}
		return null;
	}
}
