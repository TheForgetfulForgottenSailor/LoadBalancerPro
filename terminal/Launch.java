/**
 * Launch default program for a given URL or filepath.
 * 
 * @see <a href="https://stackoverflow.com/questions/5226212/how-to-open-the-default-webbrowser-using-java">launch</a>
 */
import java.awt.Desktop;
import java.awt.Desktop.*;
import java.io.*;
import java.net.*;
public class Launch{
	
	public static void main(String[]args){
		browse("https://www.google.com");
		open("../GUI/open-iconic-master/png/bug-8x.png");
		open("../GUI/Handel_Water_Music_Air.mp3");
	}
	
	/**
	 * Opens the system default application for the specified file type.
	 * 
	 * @param filepath path to the file to open
	 * @throws IllegalArgumentException if the filepath is null
	 */
	public static void open(String filepath){
		if(filepath == null){
			throw new IllegalArgumentException("filepath cannot be null");
		}
		if(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Action.OPEN)){
			Desktop desktop = Desktop.getDesktop();
			try{
				desktop.open(new File(filepath));
			}catch(IOException ioe){
				ioe.printStackTrace();
			}
		}
	}
	/**
	 * Opens the system web browser to the specified url.
	 * 
	 * @param url the page to open
	 * @throws IllegalArgumentException if the url is null
	 */
	public static void browse(String url){
		if(url == null){
			throw new IllegalArgumentException("url cannot be null");
		}
		if(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Action.BROWSE)){
			Desktop desktop = Desktop.getDesktop();
			try{
				desktop.browse(new URI(url));
			}catch(IOException ioe){
				ioe.printStackTrace();
			}catch(URISyntaxException urise) {
				urise.printStackTrace();
			}
		}
	}
}
