import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;

import java.io.File;
import java.io.IOException; 
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.tika.io.FilenameUtils;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.apache.commons.io.FileUtils;
import org.apache.commons.codec.digest.DigestUtils;

import com.google.common.base.Optional;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;


public class Softpedia  {
	static String exception = "";
	static String file_ID = "";
	static Boolean success = false;
	static int urls_count = 0;
	static int urls_success = 0;
	static int url_success_file_dl_fail = 0;
	static int urls_fail = 0;
	static int windows_apps = 0;
	static String downloadFilepath = "/home/user/Downloads/softpediafiles";

	//Insert into MYSQL "app" table
	public static void insert_into_app_tb(Connection conn, String ID, String url, boolean dl_success, boolean signed, int err_code_rsp, String exception, Float float1, String file_size_unit){
		String sql = "INSERT INTO softpedia (ID, DL_Portal, DL_Success, Signed, ERR_Code_RSP, Exception, File_Size, File_Size_Unit)" + 
				"VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
		try{
			PreparedStatement preparedStatement = conn.prepareStatement(sql);
			preparedStatement.setString(1, ID);
			preparedStatement.setString(2, url);
			preparedStatement.setBoolean(3, dl_success);
			preparedStatement.setBoolean(4, signed);
			preparedStatement.setInt(5, err_code_rsp);
			preparedStatement.setString(6, exception);
			preparedStatement.setFloat(7, float1);
			preparedStatement.setString(8, file_size_unit);
			preparedStatement.executeUpdate(); 
		}
		catch(Exception e){
			System.out.println(e);
		}  
	}

	public static WebDriver getWebDriverInstance()
	{
		ChromeOptions options = new ChromeOptions();
		System.setProperty("webdriver.chrome.driver","/usr/bin/chromedriver");
		HashMap<String, Object> setPath = new HashMap<String, Object>();    
		setPath.put("download.default_directory", downloadFilepath); //to set path 
		setPath.put("safebrowsing.enabled", "false"); // to disable security check eg. Keep or cancel button

		HashMap<String, Object> chromeOptionsMap = new HashMap<String, Object>();
		options.setExperimentalOption("prefs", setPath);
		options.addArguments("--disable-extensions"); //to disable browser extension popup
		DesiredCapabilities cap = DesiredCapabilities.chrome();

		cap = DesiredCapabilities.chrome();
		cap.setCapability(ChromeOptions.CAPABILITY, chromeOptionsMap);
		cap.setCapability(ChromeOptions.CAPABILITY, options);

		@SuppressWarnings("deprecation")
		WebDriver driver = new org.openqa.selenium.chrome.ChromeDriver(cap);// web driver is only created for Windows apps

		return driver;
	}

	public static  MutablePair<Float, String> parseFileSize(String fileSizeStr)
	{
		MutablePair<Float, String> file_size = new MutablePair<Float, String>((float) 0, "");
		if(!fileSizeStr.isEmpty())
		{
			String delims = "[ ]";
			String[] tokens = fileSizeStr.split(delims);
			file_size.setLeft(Float.parseFloat(tokens[0]));
			file_size.setRight(tokens[1]);
		}
		return file_size;
	}

	public static long calculateThreshold(MutablePair<Float, String> file_size, String url)
	{
		long default_threshold = 16000; //default threshold
		long threshold;

		if(file_size.getRight().equalsIgnoreCase("MB"))
		{
			threshold = (long) ((file_size.getLeft()/20) * 4000 + default_threshold);
			System.out.println("Hi  " + threshold);
			return threshold;
		}
		if(file_size.getRight().equalsIgnoreCase("GB"))
		{
			threshold = (long) (file_size.getLeft() * 240000 + default_threshold);
			System.out.println("Hi  " + threshold);
			return threshold;
		}
		return default_threshold;
	}

	public static int httpResponseCodeViaGet(String url) throws IOException {
		/*  try{

		  // RestAssured.config=RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig().
			//        setParam("http.connection.timeout",150000).
			   //     setParam("http.socket.timeout",150000).
			    //    setParam("http.connection-manager.timeout",150000));
           return RestAssured.get(url).statusCode();
		   }
		   catch (Exception e) {
			exception = "Restassured : " + e.getMessage();
		    return 0; 	    
		   }*/
		int code = -1;
		URL urll = new URL(url);
		HttpURLConnection connection = (HttpURLConnection)urll.openConnection();
		connection.setRequestMethod("GET");
		//connection.connect();

		code = connection.getResponseCode();
		return code;

	}
	public static void find_win_category_links(Connection conn, WebDriver driver) throws InterruptedException
	{
		int statusCode = 0;
		String url = "";
		String target_href = "";
		String OS_type = "windows";
		MutablePair<Float, String> file_size = new MutablePair<Float, String>((float)0, "");

		List<WebElement> highlightedLinks = driver.findElements(By.xpath(".//h4[@class='ln']/a")); //*[@id="sjmp"]/div[8]/h4/a
		System.out.println(highlightedLinks.size());
		for(WebElement el: highlightedLinks) // take the Url of each link
		{
			windows_apps++;
			url = el.getAttribute("href");
			System.out.println(url);
			WebDriver driver1 = getWebDriverInstance();

			try{
				driver1.get(url);
				WebDriverWait wait = new WebDriverWait(driver1, 120);
				wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#wrapper > div.main > div:nth-child(2) > div:nth-child(3) > div.grid_37.prefix_1.fr > div.tabbar > h3.bg-blue.dltab > a")));//if link is not appeared, Malicious URL probably detected!
				driver1.findElement(By.cssSelector("#wrapper > div.main > div:nth-child(2) > div:nth-child(3) > div.grid_37.prefix_1.fr > div.tabbar > h3.bg-blue.dltab > a")).click(); //click on "free download"
				String url_backup = driver1.getCurrentUrl();
				Thread.sleep(5000);
				String size_ = "";
				file_size.setLeft((float) 0);
				file_size.setRight("");
				//statusCode = 0;
				file_ID = "";
				exception="";
				success = false;
				try{
					org.jsoup.nodes.Document doc = Jsoup.connect(url).get();
					for (int i = 0; i<10 ; i++)
					{
						Elements e = doc.select("#wrapper > div.main > div:nth-child(2) > div:nth-child(3) > div.grid_10 > dl.pspec2015.mgtop_5 > dd:nth-child(" + i +")");

						if (e != null)
							if(e.text().matches("^[0-9]*\\.?[0-9]*\\s[KMG]B$"))
							{
								size_ = e.text(); 
								System.out.println(size_);
								break;
							}
					}
							
					file_size = parseFileSize(size_);//css selector for extracting OS type from details_table
					System.out.println(file_size.getLeft());
				}
				catch(Exception e)
				{
					System.out.println("Couldn't retrive details about size of file! " + e);
				}
				WebElement elem = driver1.findElement(By.xpath("//*[@id=\"dllinks\"]/div[*]/a[contains(@title, 'irror') or contains(@title ,'US') or contains(@title ,'Softpedia') or contains(@title, 'Trial')]"));    
				try
				{
					if(elem != null)
					{
						String title = "";
						title = elem.getAttribute("title");
						System.out.println(title);
						/*Actions actions = new Actions(driver1);
						actions.moveToElement(elem);
						actions.perform();*/
						String dl_url = elem.getAttribute("href");
						driver1.get(dl_url);
						//elem.click(); //here we may have exception
						long waitTime = 300000; //default 5 min
						long oldCount = new File(downloadFilepath).list().length; 
						if(file_size.getLeft() != (float)0)
							waitTime = calculateThreshold(file_size, target_href);
						System.out.println("wait: " + waitTime + "\n");
						Thread.sleep(waitTime);
						waitForInProgressDownload(oldCount);
						if(newFileIsDownloaded(oldCount).equals("success")) //here file's name is not verified, we just verify if a new download is completed
						{
							file_ID = renameDownloadedFile();
							System.out.println("File Name: " + file_ID);
							if(!file_ID.isEmpty())
							{
								success = true;
								urls_success++;
							}
							else
								url_success_file_dl_fail++;
						}
						else if(newFileIsDownloaded(oldCount).equals("fail"))
						{
							urls_fail++;
							exception = "File couldn't be downloaded!";
						}
						else if(newFileIsDownloaded(oldCount).equals("Incomplete"))
						{
							urls_fail++;
							exception = "Unconfirmed[Incomplete] download : cr.download!";		
						}
						System.out.println( urls_count + "  URLs have been visited. Success: " + urls_success + "  Fail: " + urls_fail + " Failed downloads:  " + url_success_file_dl_fail+ "   Windows apps: " + windows_apps +"\n"  );
						insert_into_app_tb(conn, file_ID, url, success, false, statusCode, exception, file_size.getLeft(), file_size.getRight());
					}
					else
					{
						urls_fail++;
						exception = "No downloadable version is provided by portal!(You need to buy the software or register/fill in form)";
						System.out.println( urls_count + "  URLs have been visited. Success: " + urls_success + "  Fail: " + urls_fail + " Failed downloads:  " + url_success_file_dl_fail+ "   Windows apps: " + windows_apps +"\n"  );
						insert_into_app_tb(conn, file_ID, url, success, false, statusCode, exception, file_size.getLeft(), file_size.getRight());
					}

				}
				catch (Exception e){
					System.out.println(e);
					urls_fail++;
					exception = "Something went wrong!";
					System.out.println( urls_count + "  URLs have been visited. Success: " + urls_success + "  Fail: " + urls_fail + " Failed downloads:  " + url_success_file_dl_fail+ "   Windows apps: " + windows_apps +"\n"  );
					insert_into_app_tb(conn, file_ID, url, success, false, statusCode, exception, file_size.getLeft(), file_size.getRight());
					continue;
				}
			}


			catch(java.util.NoSuchElementException e)
			{
				System.out.println(e);
				urls_fail++;
				System.out.println( urls_count + "  URLs have been visited. Success: " + urls_success + "  Fail: " + urls_fail + " Failed downloads:  " + url_success_file_dl_fail+ "   Windows apps: " + windows_apps +"\n"  );
				insert_into_app_tb(conn, file_ID, url, success, false, statusCode, "File is not downloaded!", file_size.getLeft(), file_size.getRight());
			}
			catch(Exception e){
				System.out.println(e);
				urls_fail++;
				System.out.println( urls_count + "  URLs have been visited. Success: " + urls_success + "  Fail: " + urls_fail + " Failed downloads:  " + url_success_file_dl_fail+ "   Windows apps: " + windows_apps +"\n"  );
				insert_into_app_tb(conn, file_ID, url, success, false, statusCode, e.getMessage(), file_size.getLeft(), file_size.getRight());
			} 

			finally{
				if(driver1 != null)
					driver1.quit();
			}
		}

	}


	public static String getFileName(String urlStr)throws Exception { 
		/* Note that this function won't necessarily extract file name.
		 * For example some urls end with index.html or sth.php or some characters.
		 * As such urls would not lead to a successful download, still application of this function for verification of downloaded file
		 * would be valid!
		 */
		String fileName = "";
		if(!urlStr.isEmpty())
		{
			if (urlStr.endsWith("/download"))//this is specifically for "sourceforge.net" urls!
				urlStr = urlStr.substring(0,urlStr.lastIndexOf("/download"));;

				URL url = new URL(urlStr);
				fileName = FilenameUtils.getName(url.getPath()); // -> file.xml
				System.out.println(urlStr);
				System.out.println(fileName);
		}
		return (fileName);	
	}
	
	public static WebElement scrollToElementByOffset(WebElement element, int offset, WebDriver driver) {
	    JavascriptExecutor jse = (JavascriptExecutor) driver;
	    jse.executeScript("window.scrollTo(" + element.getLocation().getX() + "," + (element.getLocation().getY()
	            + offset) + ");");

	    return element;
	}

	public static Boolean downloadedFileExists(String fileName)
	{
		Path downloadPath =  Paths.get(downloadFilepath);
		File downloadFile = downloadPath.resolve(fileName).toFile();
		if(downloadFile.exists())
			return true;

		return false;
	}

	public static String renameDownloadedFile()
	{
		File file = getLastModifiedFile();
		String hash = "";
		if(file.exists())
		{
			hash = getHash(file);
			if(!hash.isEmpty())
			{
				//rename file
				Path downloadPath =  Paths.get(downloadFilepath);
				File dstFile = downloadPath.resolve(hash).toFile();
				if(file.renameTo(dstFile))
					file.delete();
			}
		}
		return hash;
	}

	@SuppressWarnings("deprecation")
	public static String getHash(File downloadFile)
	{
		String hashStr = "";
		if(downloadFile.exists())
		{
			try {
				HashCode hash = Files.hash(downloadFile,Hashing.sha256());
				hashStr = hash.toString();
			} catch (IOException e) {
				System.out.println("Function: getHash  " + e);
			}
		}
		return hashStr;		
	}

	public static String newFileIsDownloaded(long oldCount)
	{
		long count = new File(downloadFilepath).list().length;
		File newFile = getLastModifiedFile();

		if((count == oldCount + 1) && newFile.exists())
		{
			if (!newFile.getName().contains(".crdownload"))
			{
				System.out.println("New file is downloaded successfully!");
				return "success";
			}
			else
			{
				System.out.println("File download didn't complete successfully!");
				return "Incomplete";
			}
		}
		System.out.println("No new file!");
		return "fail";
	}

	public static void waitForInProgressDownload(long oldCount) throws InterruptedException
	{
		long count = new File(downloadFilepath).list().length;
		File newFile = getLastModifiedFile();
		//long downloadTimeout = 720000;// 12 min
		File oldFile = null;
		System.out.println(newFile.getName());
		if((count == oldCount + 1) && newFile.exists())
		{    System.out.println("GHi: " + newFile.getName());
		//long downloadStartTime = System.currentTimeMillis();
		while (newFile.getName().contains("crdownload")) 

		{
			System.out.println(newFile.getName());
			System.out.println(count);
			System.out.println(oldCount);
			/*  if ((System.currentTimeMillis() - downloadStartTime) > downloadTimeout) //don't wait more than download timeout
			        return;*/
			System.out.println("Wait for in-progress download...\n");
			long old_lastModified = newFile.lastModified();
			Thread.sleep(7000);
			long new_lastModified = newFile.lastModified();
			System.out.println(old_lastModified);
			System.out.println(new_lastModified);
			if(old_lastModified == new_lastModified) // download process is halted
			{
				return;
			}
		}
		}

	}

	public static File getLastModifiedFile()
	{
		File file;
		Path downloadPath =  Paths.get(downloadFilepath);
		java.util.Optional<File> mostRecentFile =
				Arrays
				.stream(downloadPath.toFile().listFiles())
				.filter(f -> f.isFile())
				.max(
						(f1, f2) -> Long.compare(f1.lastModified(),
								f2.lastModified()));
		file = mostRecentFile.get();
		return file;
	}

	public static void main(String[] args) {


		//Connect to Database: apps
		//System.out.println("CLASSPATH IS=" + System.getProperty("java.class.path"));
		try{ 
			Class.forName("com.mysql.jdbc.Driver");  
			Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/Applications","user","pass");    

			System.setProperty("webdriver.chrome.driver","/usr/bin/chromedriver"); 
			WebDriver driver = new org.openqa.selenium.chrome.ChromeDriver();

			String hlink = "";
			List<String> pages = Arrays.asList("https://www.softpedia.com/get/Network-Tools/Bandwidth-Tools/",
					"https://www.softpedia.com/get/Network-Tools/IP-Tools/",
					"https://www.softpedia.com/get/Network-Tools/Misc-Networking-Tools/",
					"https://www.softpedia.com/get/Network-Tools/Network-Information/",
					"https://www.softpedia.com/get/Network-Tools/Network-Monitoring/",
					"https://www.softpedia.com/get/Network-Tools/Network-Testing/",
					"https://www.softpedia.com/get/Network-Tools/Network-Tools-Suites/",
					"https://www.softpedia.com/get/Network-Tools/Network-IP-Scanner/",
					"https://www.softpedia.com/get/Network-Tools/Protocol-Analyzers-Sniffers/",
					"https://www.softpedia.com/get/Network-Tools/Telnet-SSH-Clients/",
					"https://www.softpedia.com/get/Network-Tools/Traceroute-Whois-Tools/",
				        "https://www.softpedia.com/get/CD-DVD-Tools/",
			                "https://www.softpedia.com/get/Antivirus/",
					"https://www.softpedia.com/get/Compression-tools/",
					"https://www.softpedia.com/get/Desktop-Enhancements/",
					"https://www.softpedia.com/get/File-managers/",
					"https://www.softpedia.com/get/Gaming-Related/",
					"https://www.softpedia.com/get/Internet/",
					"https://www.softpedia.com/get/IPOD-TOOLS/", 
					"https://www.softpedia.com/get/Maps-GPS/",
					"https://www.softpedia.com/get/Mobile-Phone-Tools/",
					"https://www.softpedia.com/get/Multimedia/", 
					"https://www.softpedia.com/get/Network-Tools/",
					"https://www.softpedia.com/get/Office-tools/",
					"https://www.softpedia.com/get/Others/", 
					"https://www.softpedia.com/get/PORTABLE-SOFTWARE/",
					"https://www.softpedia.com/get/Programming/",
					"https://www.softpedia.com/get/Science-CAD/", 
					"https://www.softpedia.com/get/Security/",
					"https://www.softpedia.com/get/System/",
					"https://www.softpedia.com/get/Tweak/", 
					"https://www.softpedia.com/get/Windows-Widgets/",
					"https://www.softpedia.com/get/Antivirus/Removal-Tools/",
					"https://www.softpedia.com/get/Authoring-tools/Authoring-Related/", 
					"https://www.softpedia.com/get/Authoring-tools/Business-Cards/",
					"https://www.softpedia.com/get/Authoring-tools/Cover-editors/", 
					"https://www.softpedia.com/get/Authoring-tools/Digital-Album/",
					"https://www.softpedia.com/get/Authoring-tools/Help-e-book-creators/", 
					"https://www.softpedia.com/get/Authoring-tools/Screensaver-Tools/",
					"https://www.softpedia.com/get/Authoring-tools/Setup-creators/");

			for (int i = 0; i < pages.size(); i++) {
				driver.get(pages.get(i));
				//driver.findElements(By.linkText("Browse category:"))
				//driver.findElement(By.className("fa fa-caret-down list-opener")).click();

				find_win_category_links(con,driver);//collect links from "first" page
				System.out.println("after one page==================================");
				while(driver.findElements(By.cssSelector("#sjmp > div:nth-child(39) > div.grid_29.cls_pagination.ta_right.mgtop_20 > div > a.ss.fr")).size() != 0)//collect links from next pages 
				{
					//Actions actions = new Actions(driver);
					WebElement el = driver.findElement(By.cssSelector("#sjmp > div:nth-child(39) > div.grid_29.cls_pagination.ta_right.mgtop_20 > div > a.ss.fr"));
					String URL = el.getAttribute("href");
					driver.get(URL);
					//actions.moveToElement(el);
					//actions.click().build().perform();
					//el.click();
					System.out.println("inside while==================================");				
					find_win_category_links(con,driver);
				}        	
			}
			con.close(); 
			driver.quit();
		}
		catch(Exception e){
			System.out.println(e);
		}  
	}
}
