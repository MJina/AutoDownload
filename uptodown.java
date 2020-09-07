import groovy.json.StringEscapeUtils;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.tika.io.FilenameUtils;
import org.jsoup.Jsoup;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
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
import com.mysql.jdbc.DatabaseMetaData;


public class uptodown  {
	static String DB_table = "uptodown";
	static String exception = "";
	static String target_href = "";
	static String file_ID = "";
	static Boolean success = false;
	static int urls_count = 0;
	static int urls_success = 0;
	static int url_success_file_dl_fail = 0;
	static int urls_fail = 0;
	static int windows_apps = 0;
	static String downloadFilepath = "/home/user/Downloads/uptodown";

	//Insert into MYSQL "app" table
	public static void insert_into_app_tb(Connection conn, String tbl_name, String ID, String url, boolean dl_success, boolean signed, int err_code_rsp, String exception, Float float1, String file_size_unit){
		String sql = "INSERT INTO " + tbl_name + " (ID, DL_Portal, DL_Success, Signed, ERR_Code_RSP, Exception, File_Size, File_Size_Unit)" + 
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
	
	public static void create_table(Connection conn, String tb_name)//if it does not exist already
	{
		String sql = "create table " + tb_name + " like App;";
		java.sql.DatabaseMetaData dbm = null;
		try {
			dbm = conn.getMetaData();
			ResultSet rs = null;
			rs = dbm.getTables(null, "Applications", tb_name, null);
			if (!rs.next()) {
			    PreparedStatement create = conn.prepareStatement(sql);
			    create.executeUpdate();
			}else{
			    System.out.println("Database table already exists!");
			}
		} catch (SQLException e) {
			e.printStackTrace();
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
	
	public static  MutablePair<Float, String> parseFileSizeWithoutSpace(String fileSizeStr)
	{
		MutablePair<Float, String> file_size = new MutablePair<Float, String>((float) 0, "");
		if(!fileSizeStr.isEmpty())
		{
			int endIndex = fileSizeStr.length()-2;
			String float_str = fileSizeStr.substring(0, endIndex);
			file_size.setLeft(Float.parseFloat(float_str));
			file_size.setRight(fileSizeStr.substring(float_str.length(), fileSizeStr.length()));
			System.out.println(float_str + "    " + fileSizeStr.substring(float_str.length(), fileSizeStr.length()));
		}
		return file_size;
	}

	public static long calculateThreshold(MutablePair<Float, String> file_size)
	{
		long default_threshold = 9000; //default threshold
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
		int code = -1;
		URL urll = new URL(url);
		HttpURLConnection connection = (HttpURLConnection)urll.openConnection();
		connection.setRequestMethod("GET");
		code = connection.getResponseCode();
		return code;

	}

	public static void collect_apps_from_highlited_links(Connection conn, WebDriver driver) throws IOException
	{
		int statusCode = 0;
		String url = "";
		String size_str = "";
		String size = "";
		MutablePair<Float, String> file_size = new MutablePair<Float, String>((float)0, "");
		sccrollDown(driver);
		List<WebElement> highlightedLinks = driver.findElements(By.xpath("//*[starts-with(@title,'download ')]")); 
		
		System.out.println("size=" + highlightedLinks.size());                                                       
		for(WebElement el: highlightedLinks) // take the Url of each link     
		{
			urls_count++;
			//In case of exception empty values will be set for file_size and OS_type
			size_str = "";
			statusCode = 0;
			file_ID = "";
			exception="";
			success = false;
			url = el.getAttribute("href");
			System.out.println(url);
			if(url.endsWith("/windows"))
			{
				WebDriver driver1 = getWebDriverInstance();
				WebElement dl_button = null;
			try{
					target_href = url + "/download";
					driver1.get(target_href);
					dl_button = driver1.findElement(By.xpath(".//h3[@class='download-text']"));
					WebElement size_elem = driver1.findElement(By.xpath(".//p[@class='size']"));
					if (size_elem != null)
					{
						size_str = size_elem.getText();
						System.out.println("size_string: " + size_str);
						if(!size_str.isEmpty())
							file_size = parseFileSizeWithoutSpace(size_str);
					}

				}
				catch(Exception e)
				{
					
					System.out.println("Here1: " + e.getMessage());					
					if(driver1 != null)
						driver1.quit();
					continue;
				}
				try{
					windows_apps++;
					if(dl_button != null)
					{
						//WebDriverWait wait = new WebDriverWait(driver1, 120);
						//wait.until(ExpectedConditions.visibilityOfElementLocated(By.partialLinkText("http://")));//if link is not appeared, Malicious URL probably detected!
						//List<WebElement> els = driver1.findElements(By.partialLinkText("http://"));
						//for(WebElement e: els)
						//{
							long oldCount = new File(downloadFilepath).list().length;
							long waitTime = 90000; //default 1.5 min
							if(file_size.getLeft() != (float)0)
								waitTime = calculateThreshold(file_size);
							System.out.println("wait: " + waitTime + "\n");
							//statusCode = httpResponseCodeViaGet(target_href);
							//System.out.println(statusCode);
							//driver1.get(target_href);
							dl_button.click();
							Thread.sleep(waitTime);
							waitForInProgressDownload(oldCount);
							if(newFileIsDownloaded(oldCount).equals("success")) //here file's name is not verified, we just verify if a new download is completed
							{
							file_ID = renameDownloadedFile();
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
						//exception = "";
						System.out.println( urls_count + "  URLs have been visited. Success: " + urls_success + "  Fail: " + urls_fail + " Failed downloads:  " + url_success_file_dl_fail+ "   Windows apps: " + windows_apps +"\n"  );
						insert_into_app_tb(conn, DB_table, file_ID, url, success, false, statusCode, exception, file_size.getLeft(), file_size.getRight());
						}
					}
					catch(java.util.NoSuchElementException e)
					{
						System.out.println(e);
						urls_fail++;
						System.out.println( urls_count + "  URLs have been visited. Success: " + urls_success + "  Fail: " + urls_fail + " Failed downloads:  " + url_success_file_dl_fail+ "   Windows apps: " + windows_apps +"\n"  );
						insert_into_app_tb(conn, DB_table, file_ID, url, success, false, statusCode, "File is not downloaded!", file_size.getLeft(), file_size.getRight());
					}
					catch(Exception e){
						System.out.println(e);
						urls_fail++;
						System.out.println( urls_count + "  URLs have been visited. Success: " + urls_success + "  Fail: " + urls_fail + " Failed downloads:  " + url_success_file_dl_fail+ "   Windows apps: " + windows_apps +"\n"  );
						insert_into_app_tb(conn, DB_table, file_ID, url, success, false, statusCode, e.getMessage(), file_size.getLeft(), file_size.getRight());
					} 

					finally{
						if(driver1 != null)
							driver1.quit();
					}

				}	
			/*	else
				{
					if(driver1 != null)
						driver1.quit();
				}*/
				}
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
	public static void hideElements(WebDriver driver)
	{

		String jscode = "var elements = document.getElementsByClassName('className')\"; for (var i = 0; i < elements.length; i++){ elements[i].style.display = 'none';};";

		// escaping single / double quotes / tabs / line breaks / so on
		jscode =  escapeJS(jscode);   

		((JavascriptExecutor)driver).executeScript(jscode);
	}

	/**
	 * Escapes JS.
	 */
	public static String escapeJS(String value) {
		return StringEscapeUtils.escapeJavaScript(value);
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
				return "success";
			else
				return "Incomplete";
		}
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
			Thread.sleep(12000);
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
	
	public static void sccrollDown(WebDriver webDriver)
	{
		try {
			long lastHeight = (long) ((JavascriptExecutor) webDriver).executeScript("return document.body.scrollHeight");

			while (true) {
				((JavascriptExecutor) webDriver).executeScript("window.scrollTo(0, document.body.scrollHeight);");
				Thread.sleep(2000);

				long newHeight = (long) ((JavascriptExecutor) webDriver).executeScript("return document.body.scrollHeight");
				if (newHeight == lastHeight) {
					break;
				}
				lastHeight = newHeight;
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
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
			create_table(con, DB_table);
			System.setProperty("webdriver.chrome.driver","/usr/bin/chromedriver"); 
			WebDriver driver = new org.openqa.selenium.chrome.ChromeDriver();

			// And now use this to visit soft112 sitemap
			String hlink = "";
			List<String> pages = Arrays.asList("https://en.uptodown.com/windows/dj-mixer",
					"https://en.uptodown.com/windows/audio-players",
					"https://en.uptodown.com/windows/general-audio",
					"https://en.uptodown.com/windows/audio-utilities",
					"https://en.uptodown.com/windows/audio-editors",
					"https://en.uptodown.com/windows/audio-streaming",
					"https://en.uptodown.com/windows/audio-conversion",
					"https://en.uptodown.com/windows/karaoke");
				for (int i = 0; i < pages.size() ; i++) {
					driver.get(pages.get(i));
					collect_apps_from_highlited_links(con,driver);				      	
				}
			con.close(); 
			driver.quit();
		}
		catch(Exception e){
			System.out.println(e);
		}  
	}
}
