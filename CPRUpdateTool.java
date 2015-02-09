/**
 * 
 */

/**
 * @author dchdc
 *
 */
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.jsoup.Jsoup;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;



public class CPRUpdateTool {
	
	private static DocumentBuilder builder;
	private static XPath xpath;
	private static ArrayList<String> IDs;
	private static String updateRoot;
	private static String tempFolder;
	private static String cprVersion;
	private static String sevenZipExe;
	private static String cprFile;   // The pattern file
	private static String cprName;   // Name after 7z

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		if ( args.length < 2 )
		{
			System.out.println("Usage: CPRUpdateTool.java <product> <version>");
			System.exit(1);
		}
		
		init(args);
		downloadCPR();
		prepareCPR();
		updateEachId();
		System.out.println("Completed!");		

	}
	
	
	
	private static void init(String[] args) {
		
		// Get Document Builder Factory
		DocumentBuilderFactory factory = 
			            DocumentBuilderFactory.newInstance();
		factory.setValidating(false);
		factory.setNamespaceAware(false);
		
		
		try {
			builder = factory.newDocumentBuilder();
			
			// Get xpath instance
			xpath = XPathFactory.newInstance().newXPath();
			
			
			// Read config
			IDs = new ArrayList<String>();		
	        Document config = builder.parse(new File("config.xml"));
	        sevenZipExe = ((Element) config.getElementsByTagName("Config").item(0)).getAttribute("zippath");
	        	        
	        // Go to product/version node
	        String productItemXPath = "/Config/Products/Product[@name='" + args[0] +"']" + 
	        						  "/Versions/Version[@value='" + args[1] +"']";
	        //System.out.println(productItemXPath);
	        Node update = (Node) xpath.evaluate(productItemXPath, config, XPathConstants.NODE);
	        updateRoot = ((Element) update).getAttribute("updateRoot");
	        NodeList itemList = update.getChildNodes();
	        
	        // Get IDs and updateRoot
	        for (int i=0; i<itemList.getLength(); i++) {
	        	 if (itemList.item(i).getNodeName().equals("id")) {
	        		IDs.add(itemList.item(i).getTextContent());
	        	}
	        }
	        
			tempFolder = updateRoot + "\\cpr_temp";
			
			
			System.out.println("Update " + args[0] + " " + args[1] + " pattern to CPR version " +  cprVersion);
			
			
			File folder = new File(tempFolder);
			if (folder.exists()) {				
				String rdTemp = "cmd.exe /c rd /s /q " + "\""+ tempFolder + "\"" ; 				   
				Process rdTempProcess = Runtime.getRuntime().exec(rdTemp);
				rdTempProcess.waitFor();
			}
			
			System.out.println("creating directory: " + tempFolder);
		    boolean result = false;
		    try{
		        folder.mkdir();
		        result = true;
		     } catch(SecurityException se){
		         //handle it
		    	 System.out.println("Unable to create temp folder");
		    	 System.exit(1);
		     }        
		     if(result) {    
		       System.out.println("Temp folder created");  
		     }
		              
			
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		}
		
		
		

	}
	
	private static void downloadCPR(){
		
		org.jsoup.nodes.Document doc;
		
		try {
			doc = Jsoup.connect("http://downloadcenter.trendmicro.com/index.php?clk=tab_pattern&clkval=5&regs=NABU&lang_loc=1").get();
			org.jsoup.nodes.Element resultsContainer = doc.getElementById("resultsContainer");
			
			// h2 = "Enterprise Pattern - CPR 11.464.03"
			String h2 = resultsContainer.getElementsByTag("h2").get(0).text();
			
			// Extract version number from h2
			String[] temp = h2.split("[\\.\\s]");
			cprVersion = temp[temp.length-3] + temp[temp.length-2] + temp[temp.length-1];
			cprName = cprVersion + ".7z";
			
			// Find download URL
			org.jsoup.select.Elements hrefs = resultsContainer.select("a[href]");
			String url="";
			for (int i=0; i < hrefs.size(); ++i ) {
				if ( hrefs.get(i).text().contains("lpt") ) {
					url = hrefs.get(i).attr("href");
				}
			}
			
			System.out.println( "Pattern URL is " + url);
			System.out.println( "Pattern Version is " + cprVersion);
			
			// Download the file
			cprFile = tempFolder + File.pathSeparator + url.substring(url.lastIndexOf('/') + 1);
			downloadFile(url);

			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	
	private static void prepareCPR() {
		
		try {
			
			// Unzip pattern file to tempFolder/cprversion/
	        System.out.println("Unzipping pattern...");
	        String unzip = "\""+ sevenZipExe + "\" e \""+ cprFile + "\" -o\"" + tempFolder + "\\" + cprVersion + "\" -y";
	        //System.out.println(unzip);
	        Process unzipProcess = Runtime.getRuntime().exec(unzip);
	        unzipProcess.waitFor();
	         
	        // Find the pattern file
	        File dir = new File( tempFolder + "\\" + cprVersion );
	        for (String filename : dir.list()) {
	       	 if ( filename.contains("lpt") )
	        		cprFile= tempFolder + "\\" + cprVersion + "\\" + filename;
	        }
	         
	         
	        System.out.println("Done!");
			
			// Modify package.xml
	        Document pack = builder.parse(new File("package.xml"));
	        Element entity = (Element) pack.getElementsByTagName("entity").item(0);
	        entity.setAttribute("name", "VSAPI_Pattern_ENT_00 " + cprVersion);
	        entity.setAttribute("version", cprVersion);
	        
	        Element file = (Element) pack.getElementsByTagName("file").item(0);
	        file.setAttribute("name", cprFile);
	        file.setAttribute("digest", calculateMD5(cprFile));
	        
	        
	        // Write package.xml to temp folder	        	        
	        Transformer t = TransformerFactory.newInstance().newTransformer();  
            FileOutputStream out =  new FileOutputStream(new File(tempFolder+"\\package.xml"));
            t.transform(new DOMSource(pack), new StreamResult(out));
            out.close();
                       
            
            // 7zip pattern file and package.xml
            System.out.println("Building update package...");
            String sevenZip = "\""+ sevenZipExe + "\" a -t7z \""+ updateRoot + "\\packages\\" + cprName + "\" \"" +
            					tempFolder+"\\package.xml" + "\" \"" +
            					cprFile +
            					"\" -y";				
            
			Process sevenZipProcess = Runtime.getRuntime().exec(sevenZip);
			sevenZipProcess.waitFor();
            
			System.out.println("Done!");
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		} 
		
	}

	
	private static void updateEachId(){
		
		try {
			
			for (String id : IDs) {
				
				
				System.out.println("Updating " + id );
				// Extract id file 
				String unzip = "\""+ sevenZipExe + "\" e \""+ updateRoot + "\\" + id + "\" -o\"" + tempFolder + "\\" + id + "\" -y";
				
				Process childProcess = Runtime.getRuntime().exec(unzip);
				childProcess.waitFor();
				
				//Get the extracted file
				File dir = new File( tempFolder + "\\" + id);
				String xmlFile = tempFolder + "\\" + id + "\\" + dir.list()[0];
				// Find VSAPI Pattern Entity Node				
			    
				Document doc = builder.parse(new File(xmlFile));	            		 		            
				String expression = "//entity[@type='4' and @class='3']";      
	            Element entity = (Element) xpath.evaluate(expression, doc, XPathConstants.NODE);	            
	            if (entity != null ){
	            	
	            System.out.println("Found pattern entry! Change it to CPR");
	            entity.setAttribute("version", cprVersion);
	            
	            
	            // Traverse all its child nodes to insert CPR pattern
	            NodeList linkNodes = entity.getChildNodes();
	            for (int i = 0; i < linkNodes.getLength();i++) {
	            	Node tmpNode = linkNodes.item(i);
	            		            	
	            	// Replace full pattern with CPR
	            	if (tmpNode.getNodeName().equals("full")){
	            		Element e =(Element) tmpNode;
	            		e.setAttribute("dig", calculateMD5(updateRoot + "\\packages\\" + cprName));
	            		e.setAttribute("size", calculateFileSize(updateRoot + "\\packages\\" + cprName));
	            		e.getElementsByTagName("url").item(0).setTextContent("packages/" + cprName);
	            		
	            	}
	            	// Remove incremental patterns
	            	else if (tmpNode.getNodeName().equals("inc")){
	            		entity.removeChild(linkNodes.item(i));
	                }
	            	// Modify maxver
	            	else if (tmpNode.getNodeName().equals("applyto")){
	            		Element e =(Element) tmpNode;
	            		e.setAttribute("maxver", cprVersion);
	            	}
	            }
	            
	            // Backup original id file
	            new File(updateRoot + "\\" + id).renameTo(new File(updateRoot + "\\" + id + ".old"));
	            
	            
	            // Write XML to file and replace the original
	            Transformer t = TransformerFactory.newInstance().newTransformer();  
	            FileOutputStream out =  new FileOutputStream(new File(xmlFile));
	            t.transform(new DOMSource(doc), new StreamResult(out));	            
	            out.close();
	            
	            
	            String zip = "\""+ sevenZipExe + "\" a -tgzip \""+ updateRoot + "\\" + id + "\" \"" +xmlFile + "\" -y";				
	            //System.out.println(zip);
				Process zipProcess = Runtime.getRuntime().exec(zip);
				
				zipProcess.waitFor();
			        }
			}
				
			
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		} 

		
	}
	
	
	
	
	private static String calculateMD5(String filename) {
		
		try {
			InputStream in = new FileInputStream(filename);

			MessageDigest md5 = MessageDigest.getInstance("MD5");
			byte[] buffer = new byte[1024];

			while (true)
			{
			    int c = in.read(buffer);

			    if (c > 0)
			        md5.update(buffer, 0, c);
			    else if (c < 0)
			        break;
			}

			in.close();

			byte[] b = md5.digest();
			String result = "";
		    for (int i=0; i < b.length; i++) {
		       result +=
		          Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring( 1 );
		    }
			return result;
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return "";
	}
	
	private static String calculateFileSize(String filename) {
		
		long s=0;
		 	 
		try {
			File f = new File(filename);
			if (f.exists()) {
				FileInputStream fis = null;
				fis = new FileInputStream(f);
				s = fis.available();
				fis.close();
			} else {				
				System.out.println("File does not exist!");
			}
			
			return Long.toString(s);
			
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
			
			return "";
		}
	}
	
	private static void downloadFile(String fileURL) throws IOException {
        
		System.out.println("Start Downloading...");
		
		URL url = new URL(fileURL);
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
        int responseCode = httpConn.getResponseCode();
 
        // always check HTTP response code first
        if (responseCode == HttpURLConnection.HTTP_OK) {       	
 
            // opens input stream from the HTTP connection
            InputStream inputStream = httpConn.getInputStream();
             
            // opens an output stream to save into file
            FileOutputStream outputStream = new FileOutputStream(cprFile);
 
            int bytesRead = -1;
            byte[] buffer = new byte[4096];
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
 
            outputStream.close();
            inputStream.close();
 
            System.out.println("File downloaded!");
        } else {
            System.out.println("No file to download. Server replied HTTP code: " + responseCode);
        }
        httpConn.disconnect();
    }


}
