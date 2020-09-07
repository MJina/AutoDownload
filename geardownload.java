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


public class geardownload  {
	static String DB_table = "geardownload";
	static String exception = "";
	static String target_href = "";
	static String file_ID = "";
	static Boolean success = false;
	static int urls_count = 0;
	static int urls_success = 0;
	static int url_success_file_dl_fail = 0;
	static int urls_fail = 0;
	static int windows_apps = 0;
	static String downloadFilepath = "/home/user/Downloads/geardownloadfils";

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
		String os_size_str = "";
		String size = "";
		MutablePair<Float, String> file_size = new MutablePair<Float, String>((float)0, "");
		List<WebElement> highlightedLinks = driver.findElements(By.xpath(".//h2/a")); 
		System.out.println("size=" + highlightedLinks.size());                                                       
		for(WebElement el: highlightedLinks) // take the Url of each link     
		{
			urls_count++;
			//In case of exception empty values will be set for file_size and OS_type
			os_size_str = "";
			statusCode = 0;
			file_ID = "";
			exception="";
			success = false;
			//el.click();
			url = el.getAttribute("href");
			//try{
			if(el !=  null)              //*[@id="info"]/div[2]/div[2]/dl/dd[3]/text()
				System.out.println(url);
				WebDriver driver1 = getWebDriverInstance();
				try{
				driver1.get(url);
				List<WebElement> els = driver1.findElements(By.xpath(".//dd[@class='line']"));
				if (els.size()>0)
				{
					os_size_str = els.get(2).getText();
					System.out.println("OS_size_string: " + os_size_str);
					Pattern pattern1 = Pattern.compile("[0-9]*.[0-9]* [KMG]B");
					if(!os_size_str.isEmpty())
					{
					Matcher matcher1 = pattern1.matcher(os_size_str);
					

					if (matcher1.find() && !matcher1.group().isEmpty())
					{
						size = matcher1.group();
						file_size = parseFileSize(size);
					}
					}
					
				/*	Pattern pattern2 = Pattern.compile("|   [a-zA-Z0-9 ]+");
					Matcher matcher2 = pattern2.matcher(os_size_str);
					
					if (matcher2.find())
					{
						os_type = matcher2.group();
					}*/
				}
				System.out.println("OS: " + os_size_str);
				System.out.println("OS: " + size);


				}
				catch(Exception e)
				{
					
					System.out.println("Here1: " + e.getMessage());
					if(!e.getMessage().contains("empty String"))
					{
						if(driver1 != null)
							driver1.quit();
						continue;
					}
					else
						exception = e.getMessage();
				}
				if(os_size_str.contains("windows") || os_size_str.contains("Windows") || os_size_str.isEmpty())
				{
					System.out.println("Hi");
					windows_apps++;
					try{
						driver1.get(url.replaceFirst("\\.html$", "-download.html"));
						WebDriverWait wait = new WebDriverWait(driver1, 120);
						wait.until(ExpectedConditions.visibilityOfElementLocated(By.partialLinkText("http://")));//if link is not appeared, Malicious URL probably detected!
						List<WebElement> els = driver1.findElements(By.partialLinkText("http://"));
						for(WebElement e: els)
						{
							System.out.println("Hi1");
							long oldCount = new File(downloadFilepath).list().length;
							long waitTime = 90000; //default 1.5 min
							if(file_size.getLeft() != (float)0)
								waitTime = calculateThreshold(file_size);
							System.out.println("wait: " + waitTime + "\n");
							target_href = e.getAttribute("href");
							statusCode = httpResponseCodeViaGet(target_href);
							System.out.println(statusCode);
							driver1.get(target_href);
							Thread.sleep(waitTime);
							//Thread.sleep(5000);
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
				else
				{
					if(driver1 != null)
						driver1.quit();
				}
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
				System.out.println("Cheraaaa: ");
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
			create_table(con, DB_table);
			System.setProperty("webdriver.chrome.driver","/usr/bin/chromedriver"); 
			WebDriver driver = new org.openqa.selenium.chrome.ChromeDriver();

			String hlink = "";
			Hashtable<String, Integer> h = new Hashtable<String, Integer>(); 
			h.put("http://www.geardownload.com/multimedia/audio-encoders-decoders-1.html",); 
			h.put("http://www.geardownload.com/multimedia/audio-encoders-decoders-2.html",); 
			h.put("http://www.geardownload.com/multimedia/audio-encoders-decoders-3.html",); 
			h.put("http://www.geardownload.com/multimedia/audio-encoders-decoders-4.html",); 
			h.put("http://www.geardownload.com/multimedia/audio-encoders-decoders-5.html",); 
			h.put("http://www.geardownload.com/multimedia/audio-encoders-decoders-6.html",); 
			h.put("http://www.geardownload.com/multimedia/audio-encoders-decoders-7.html",); 
			h.put("http://www.geardownload.com/multimedia/audio-encoders-decoders-8.html",); 
			h.put("http://www.geardownload.com/multimedia/audio-encoders-decoders-9.html",); 
			h.put("http://www.geardownload.com/multimedia/audio-encoders-decoders-10.html",); 
			
			 for (@SuppressWarnings("rawtypes") Map.Entry mapElement : h.entrySet()) { 
		            String partial_link = (String)mapElement.getKey(); 
		            int page_number = ((int)mapElement.getValue()); 
		  System.out.println("Last category: " + partial_link);
				for (int i = start.get(partial_link); i <= page_number ; i++) {
					hlink =  partial_link + i + ".html";
					driver.get(hlink);
					collect_apps_from_highlited_links(con,driver);				      	
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
