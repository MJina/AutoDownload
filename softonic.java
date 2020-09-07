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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

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


public class softonic  {
	static String exception = "";
	static String file_ID = "";
	static Boolean success = false;
	static int urls_count = 0;
	static int urls_success = 0;
	static int url_success_file_dl_fail = 0;
	static int urls_fail = 0;
	static int windows_apps = 0;
	static String downloadFilepath = "/home/user/Downloads/softonicfiles";

	//Insert into MYSQL "app" table
	public static void insert_into_app_tb(Connection conn, String ID, String url, boolean dl_success, boolean signed, int err_code_rsp, String exception, Float float1, String file_size_unit){
		String sql = "INSERT INTO softonic (ID, DL_Portal, DL_Success, Signed, ERR_Code_RSP, Exception, File_Size, File_Size_Unit)" + 
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
		String OS_type = "";
		MutablePair<Float, String> file_size = new MutablePair<Float, String>((float)0, "");
		List<WebElement> highlightedLinks = driver.findElements(By.xpath(".//li[@class='directory__item']/a")); 
		System.out.println("size=" + highlightedLinks.size());                                                       
		for(WebElement el: highlightedLinks) // take the Url of each link     
		{
			urls_count++;
			if(el.getText().contains("for Windows"))
			{
			//In case of exception empty values will be set for file_size and OS_type
			OS_type = "";
			statusCode = 0;
			file_ID = "";
			exception="";
			success = false;
			String old_url = url; 
			url = el.getAttribute("href");
			//try{
			System.out.println(old_url);
			if(el !=  null)
				System.out.println(url);
			if(old_url.compareTo(url) == 0)
			{
				WebDriver driver1 = getWebDriverInstance();
				try{
				driver1.get(url);
				List<WebElement> els = driver1.findElements(By.xpath(".//p[@class='app-specs__value']"));
				if (els.size()>0)
					OS_type = els.get(1).getText();
				System.out.println("OS: " + OS_type);
				}
				catch(Exception e)
				{
					if(driver1 != null)
						driver1.quit();
					System.out.println(e.getMessage());
					continue;
				}
				if(OS_type.contains("windows") || OS_type.contains("Windows"))
				{
					windows_apps++;
					try{
						if(url.endsWith("/"))
							driver1.get(url + "download");
						else
							driver1.get(url + "/download");
						WebDriverWait wait = new WebDriverWait(driver1, 120);
						wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#js-app-download-info > div.app-download-info__container > a > strong")));//if link is not appeared, Malicious URL probably detected!
						long oldCount = new File(downloadFilepath).list().length;
						driver1.findElement(By.cssSelector("#js-app-download-info > div.app-download-info__container > a > strong")).click();
						Thread.sleep(5000);
						driver1.findElement(By.cssSelector("#main-app-footer > a")).click();
						Thread.sleep(12000);//wait for download to get started
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
						insert_into_app_tb(conn, file_ID, url, success, false, statusCode, exception, file_size.getLeft(), file_size.getRight());
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
				else
				{
					if(driver1 != null)
						driver1.quit();
				}
			}
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
		{    System.out.println(newFile.getName());
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
			for (int i = 1; i < 136; i++) {
				hlink = "https://en.softonic.com/download/programs/p/" + i;
				driver.get(hlink);
				collect_apps_from_highlited_links(con,driver);//collect links from "first" page
				/*while(driver.findElements(By.linkText(">")).size() != 0)//collect links from next pages 
				{
					driver.findElement(By.linkText(">")).click();				
					collect_apps_from_highlited_links(con,driver);
				} */       	
			}
			con.close(); 
			driver.quit();
		}
		catch(Exception e){
			System.out.println(e);
		}  
	}
}

