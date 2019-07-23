# kuraMinifiPlugin
## Description:
This plugin is a working example of how Minifi/Nifi can be embedded in the KURA/KAPUA OSGI Eclipse project. The plugin can be used to run Minifi inside a Kura enabled device, connecting it to a server side Nifi instance. 


# License
```
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, 
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR 
PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE 
FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, 
ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

URL: 	    https://github.com/sgrotz/kuraMinifiPlugin
Author: 	Stephan Grotz - stephan.grotz@gmail.com
```


# Things to be aware off
The plugin works by downloading and installing additional binaries from a remote location. The plugin is able to download these minifi binaries from the internet or (preferred) from an internal repository. Please make sure that you only use URL's to binary archives, that you trust - otherwise you may run the risk of installing unwanted software or even malware. 


# Compatibility
The plugin was tested with the following versions: 

| Component | Version |
| --- | --- |
| Eclipse Kura | 4.0.0, 4.1.0 |
| Eclipse Kapua | 1.0 |
| Minifi / Nifi |  0.5, 0.6 (CEM Edition) |


# Installation
The github repository contains a binary version, ready to be deployed in the **resources/dp** folder. 
Simply 
* open the Kapua web-frontend 
* select a KURA device
* browse the installed packages
* Click "Install new package"
* Enter the URL (eg https://github.com/sgrotz/kuraMinifiPlugin/blob/master/resources/dp/KuraMinifiPlugin_v01.dp), a name for the plugin as well as a version number.
* Click install

Kapua will now send an installation command to the device, downloading the plugin and activating it on the device. Make sure to check the log files of both, the KURA as well as the KAPUA device for any errors. 
Within Kapua - click on the Kura device and refresh its installed packages. You should see the plugin installed.


# Configuring the plugin
The configuration of the plugin is rather straight forward - open the "Configuration" Tab in Kapua - or access the service directly from the Kura Web-frontend, by clicking on the "Minifi / Nifi Plugin" service in the left hand menu. 
The plugin offers the following configuration properties: 
* Auto Start Server: Boolean flag, indicating if the server should automatically start upon device start. Default: true
* URL to download binaries: String field, containing the URL from where to download the Minifi binaries. Keep in mind that the devices need to have physical access to download these files - if the devices do not have internet access, you need to set up an internal repository to download from. Defaults to: http://mirror.funkfreundelandshut.de/apache/nifi/minifi/0.5.0/minifi-0.5.0-bin.tar.gz
* Bootstrap configuration: String Textarea, containing the bootstrap configuration of this minifi instance. Once the server binaries are installed, the plugin will overwrite the bootstrap configuration from this property. You can therefore control and maintain the bootstrap configuration from within your command&control center (Kapua in this example). Default: empty
* Config YML configuration: String Textarea, containing the Config YAML file. Once the server binaries are installed, the plugin will overwrite the config.yml configuration from this property. Default: empty
* Logback XML configuration: String Textarea, containing the logback.xml file. Once the server binaries are installed, the plugin will overwrite the logback.xml configuration from this property. Default: empty


# Starting/Stopping the plugin
After installing the plugin, you should be able to browse the configuration of the plugin directly from within Kura (web-frontend should have a new entry in the service list in the left-hand menu) or from within the server side command central Kapua. 
You can start or stop the bundle by accessing the "Bundles" - you should see an entry "org.sg.kura.minifiPlugin" in the list of bundles. Click on the bundle and use the command buttons to stop or start the bundle. This will activate or deactivate the kura components. 
