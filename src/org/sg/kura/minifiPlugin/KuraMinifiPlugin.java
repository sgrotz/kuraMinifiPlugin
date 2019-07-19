package org.sg.kura.minifiPlugin;

/*
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, 
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR 
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE 
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, 
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * 
 * URL: 	https://github.com/sgrotz/kuraMinifiPlugin
 * Author: 	Stephan Grotz - stephan.grotz@gmail.com
 * 
 */


import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.kura.configuration.ConfigurableComponent;

public class KuraMinifiPlugin implements ConfigurableComponent {

	private static final Logger logger = LoggerFactory.getLogger(KuraMinifiPlugin.class);
	private static final String APP_ID = "org.sg.kura.minifiPlugin.KuraMinifiPlugin";
	private final String WORKING_DIR=System.getProperty("user.dir");
	private final String SEPARATOR=File.separator;
	private final String INSTALL_DIR=WORKING_DIR + SEPARATOR + "minifi_install_dir";
	private final String INSTALL_CONF_DIR=INSTALL_DIR + SEPARATOR + "conf" + SEPARATOR;
	private final String BIN_DIR=INSTALL_DIR + SEPARATOR + "bin" + SEPARATOR;
	private final String LOG_DIR=INSTALL_DIR + SEPARATOR + "logs" + SEPARATOR;
	private final String BOOTSTRAP_CONF=INSTALL_CONF_DIR + "bootstrap.conf";
	private final String CONFIG_YML_CONF=INSTALL_CONF_DIR + "config.yml";

	private final String LOCAL_CONFIG_FILE=WORKING_DIR+ SEPARATOR + "binaryURL.txt";

	private final String INSTALL_FILE_NAME="minifi.tar.gz";
	private Map<String, Object> properties;

	protected void activate(ComponentContext componentContext) {
		logger.info("Bundle " + APP_ID + " has started!");

		properties = (Map<String, Object>) componentContext.getProperties();
		String binaryURL = null;

		// First of all, verify where the binaries are stored - if null do nothing
		if(properties != null && !properties.isEmpty() && properties.containsKey("binaryURL")) {
			binaryURL = properties.get("binaryURL").toString().trim();

			if (binaryURL.equals(this.getLocalConfig())) {
				// if the binary URL matches the locally installed URL, then we assume the server is already installed - we can try to start it
				this.startServer();

			} else if (binaryURL.equals("")) {
				// The binary URL is empty - we do nothing - we need to wait until the setting is provided
				logger.info("No Binary URL set - " + APP_ID + " started, but no minifi instance is running...");
			} else {
				// We assume the server is not yet installed - lets install it
				logger.info("Installing minifi server - " + APP_ID + " ...");
				this.installServerComponents();

				// If the server is not already running - Now we can start it
				this.startServer();
			}
		} else {
			logger.info("No Binary URL property not found - " + APP_ID + " started ...");
		}

	}

	private boolean installServerComponents() {
		boolean success = false;

		if(properties != null && !properties.isEmpty() && properties.containsKey("binaryURL")) {
			// First lets clean up the base directory
			this.cleanupBaseDir(INSTALL_DIR);

			// Where to get the binaries from
			String binaryURL = properties.get("binaryURL").toString();

			// Then lets download the binary files
			//this.downloadBinaryFile("http://192.168.77.32/minifi-0.6.0.1.0.0.0-54-bin.tar.gz", WORKING_DIR + "/minifi.tar.gz");
			this.downloadBinaryFile(binaryURL, WORKING_DIR + SEPARATOR + INSTALL_FILE_NAME);

			// Decompress the binary files
			String COMPRESSED_FILE = WORKING_DIR + SEPARATOR + INSTALL_FILE_NAME;
			String DESTINATION_PATH = INSTALL_DIR;
			unTarFile(COMPRESSED_FILE, DESTINATION_PATH);

			// Make sure to write the binary URL to the local configuration file.	
			setLocalConfig(binaryURL);
		}

		return success;
	}

	protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
		logger.info("Bundle " + APP_ID + " has started with config!");
		updated(properties);
	}

	protected void deactivate(ComponentContext componentContext) {
		logger.info("Bundle " + APP_ID + " has stopped!");

		this.stopServer();
	}

	public void updated(Map<String, Object> properties) {
		this.properties = properties;
		if(properties != null && !properties.isEmpty()) {

			// Verify if the binary URL has changed - do we need to reinstall the server?
			if (!properties.get("binaryURL").toString().trim().equals(this.getLocalConfig())) {
				// The binary URL has changed - lets reinstall the server
				this.installServerComponents();
			}

			// Now lets update the local configuration files ...
			Iterator<Entry<String, Object>> it = properties.entrySet().iterator();
			while (it.hasNext()) {
				Entry<String, Object> entry = it.next();
				logger.debug("Property changed - " + entry.getKey() + " = " +
						entry.getValue() + " of type " + entry.getValue().getClass().toString());

				// Overwrite the bootstrap configuration 
				if (entry.getKey().equals("bootstrapConf")) {
					overwriteConfigurationFile(entry, BOOTSTRAP_CONF); 
				}

				// Overwrite the config yaml file
				if (entry.getKey().equals("configYml")) {
					overwriteConfigurationFile(entry, CONFIG_YML_CONF); 
				}
			}

			// Configuration has changed - we need to restart the server.
			logger.info("Configuration changed - Initiating server restart sequence ...");
			this.stopServer();
			this.startServer();

		}
	}

	private void overwriteConfigurationFile(Entry<String, Object> entry, String path) {
		String content = (String) entry.getValue();

		if (content != null && !content.equals("")) {
			FileWriter fw;
			try {
				fw = new FileWriter(path);
				fw.write(content);    
				fw.close();   
	
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
	}

	private void setLocalConfig(String binaryURL) {
		FileWriter fw;
		String path = LOCAL_CONFIG_FILE;
		try {
			fw = new FileWriter(path);
			fw.write(binaryURL);    
			fw.close();   

		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	private String getLocalConfig() {

		File file = new File(LOCAL_CONFIG_FILE); 

		BufferedReader br;
		String ret = null; 
		try {
			br = new BufferedReader(new FileReader(file));

			ret = br.readLine();

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			logger.info("No local binary file found - is this the first start?");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 



		return ret;

	}

	private boolean downloadBinaryFile(String url, String localPath) {
		boolean success = false;

		// Create a new trust manager that trust all certificates
		TrustManager[] trustAllCerts = new TrustManager[]{
				new X509TrustManager() {
					public java.security.cert.X509Certificate[] getAcceptedIssuers() {
						return null;
					}
					public void checkClientTrusted(
							java.security.cert.X509Certificate[] certs, String authType) {
					}
					public void checkServerTrusted(
							java.security.cert.X509Certificate[] certs, String authType) {
					}
				}
		};

		// Activate the new trust manager
		try {
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} catch (Exception e) {
		}

		// And as before now you can use URL and URLConnection
		URL location;
		try {
			location = new URL(url);

			logger.info("Downloading file from: " + url + " - writing to: " + localPath);
			URLConnection connection = location.openConnection();
			InputStream is = connection.getInputStream();

			File targetFile = new File(localPath);

			FileUtils.copyInputStreamToFile(is, targetFile);

			success = true;
			// .. then download the file
		} catch (MalformedURLException  e) {
			// TODO Auto-generated catch block
			logger.info("Invalid URL - can not connect!");
			e.printStackTrace();
		} catch (ConnectException e) {
			logger.info("Remote host is not available - can not connect!");
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return success;
	}

	private boolean cleanupBaseDir(String baseDir) {
		boolean success = false; 

		try {

			FileUtils.deleteDirectory(new File(baseDir));
			logger.debug("Deleting directory: " + baseDir);

			success = true; 
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return success;
	}


	private boolean unTarFile(String tarFile, String DESTINATION_PATH ) {
		boolean success = false;
		TarArchiveInputStream tis = null;
		try {
			logger.info("Unpacking " + tarFile);
			File destFile = new File(DESTINATION_PATH);
			FileInputStream fis = new FileInputStream(tarFile);
			// .gz
			GZIPInputStream gzipInputStream = new GZIPInputStream(new BufferedInputStream(fis));
			//.tar.gz
			tis = new TarArchiveInputStream(gzipInputStream);
			TarArchiveEntry tarEntry = null;
			while ((tarEntry = tis.getNextTarEntry()) != null) {
				logger.trace(" tar entry- " + tarEntry.getName());
				if(tarEntry.isDirectory()){
					continue;
				}else {
					// In case entry is for file ensure parent directory is in place
					// and write file content to Output Stream
					String base_dir = tarEntry.getName().split("/")[0] + File.separator;
					String targetDirectory = tarEntry.getName().replaceFirst(base_dir, "");
					File outputFile = new File(destFile + File.separator + targetDirectory);

					outputFile.getParentFile().mkdirs();    
					IOUtils.copy(tis, new FileOutputStream(outputFile));

					if (FilenameUtils.getExtension(tarEntry.getName()).equals("sh")) {
						outputFile.setExecutable(true);
					}

				}
			}              
		}catch(IOException ex) {
			logger.error("Error while untarring a file- " + ex.getMessage());
		}finally {
			if(tis != null) {
				try {
					tis.close();
					success = true; 
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} return success;   
	}

	private boolean isServerRunning() {
		boolean ret = false;

		String output = this.runCommand(BIN_DIR + "minifi.sh status");

		if (output != null && !output.equals("") && output.contains("currently running")) {
			ret = true;
		} 

		return ret;
	}

	private void startServer() {
		// Make sure to check if the autostart variable is set.
		boolean autoStart = Boolean.valueOf(properties.get("autostart").toString());
		if (autoStart) {
			// if true, start the server - bit first verify if the server is not already running...
			if (!isServerRunning() && autoStart) {
				logger.info("Initiating minifi server startup sequence - " + APP_ID + " ...");
				logger.info("Nifi/Minifi log files can be found here: " + LOG_DIR);
				runCommand(BIN_DIR + "minifi.sh start");
			} else {
				logger.info("Minifi is already running - will ignore ... ");
			}
		} else {
			logger.debug("Autostart is disabled - will not start server ... ");
		}
	}

	private void stopServer() {
		if (isServerRunning()) {
			logger.info("Initiating minifi server shutdown sequence - " + APP_ID + " ...");
			runCommand(BIN_DIR + "minifi.sh stop");
		}
	}

	private String runCommand(String command) {
		String ret = null;
		try {

			logger.debug("Executing command: " + command);
			Process p = Runtime.getRuntime().exec(command);
			//int result = p.waitFor();


			StringBuilder output = new StringBuilder();

			BufferedReader reader = new BufferedReader(
					new InputStreamReader(p.getInputStream()));

			String line;
			while ((line = reader.readLine()) != null) {
				output.append(line + "\n");
			}

			int exitVal = p.waitFor();
			if (exitVal == 0) {
				logger.trace("Success!");
				logger.trace(output.toString());
				ret = output.toString();
			} else {
				logger.error("Command " + command + " returned a non-0 exit code. Not good :(");
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return ret;
	}

}