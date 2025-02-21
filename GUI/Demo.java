import java.awt.Desktop;
import java.awt.Desktop.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javafx.animation.*;
import javafx.application.Application;
import javafx.event.*;
import javafx.fxml.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.media.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.stage.*;
import javafx.stage.FileChooser.*;
import javafx.util.*;

public class Demo extends Application{
	public static FileChooser fileChooser;
	public static String imageFilePath;
	public static Stage stage;
	public static Scene scene;
	public static boolean flag;
	public static ImageView photoView;
	public static Text keyText;
	public static Circle circle;
	public static Rectangle rect;
	public static Button play, stop;
	public static MediaPlayer mediaPlayer;// prevent GC from killing media
	
	@Override
	public void start(Stage primaryStage) throws Exception {
		// load gui from fxml
		Parent root = FXMLLoader.load(getClass().getResource("Demo.fxml"));
		
		// setup some global vars
		scene = new Scene(root);
		stage = primaryStage;
		flag = true;
		photoView = (ImageView)scene.getRoot().lookup("#photo");
		keyText = (Text)scene.getRoot().lookup("#keytext");
		circle = (Circle)scene.getRoot().lookup("#circle");
		rect = (Rectangle)scene.getRoot().lookup("#rect");
		play = (Button)scene.getRoot().lookup("#play");
		stop = (Button)scene.getRoot().lookup("#stop");
		
		fileChooser = new FileChooser();
		fileChooser.setTitle("Open File");
		fileChooser.getExtensionFilters().addAll(new ExtensionFilter("All Files", "*.*"));
		
		// restore photo if saved
		File imageFile = new File("imageFile.txt");
		if(imageFile.exists()){
			try{
				Scanner scan = new Scanner(imageFile);
				imageFilePath = scan.nextLine();
				scan.close();
				imageFile = new File(imageFilePath);
				if(imageFile.exists()){
					InputStream input = new FileInputStream(imageFile);
					photoView.setImage(new Image(input));
				}
			}catch(Exception e){
				imageFilePath = null;
			}
		}
		
		// actually show the gui
		stage.setTitle("GUI Demo Code");
		stage.setScene(scene);
		stage.getIcons().add(new Image("open-iconic-master/png/aperture-2x.png"));
		stage.show();
		
		// add music
		try{
			String musicFile = "Handel_Water_Music_Air.mp3";
			Media sound = new Media(new File(musicFile).toURI().toString());
			mediaPlayer = new MediaPlayer(sound);
		}catch(Exception e){
			e.printStackTrace();
		}
		
		// setup periodic task
		Timeline timeline = new Timeline(new KeyFrame(Duration.millis(5_000), e ->{
			circle.setCenterX(0);
			circle.setCenterY(0);
			}));
		timeline.setCycleCount(Animation.INDEFINITE);
		timeline.play();
	}
	
	@FXML
	public void exit(Event e){
		System.exit(0);
	}
	
	@FXML
	public void open(Event e){
		try{
			File file = fileChooser.showOpenDialog(stage);
			InputStream input = new FileInputStream(file);
			photoView.setImage(new Image(input));
			imageFilePath = file.getCanonicalPath();
		} catch(IOException ioe){
			ioe.printStackTrace();
		} catch(NullPointerException npe){
			// cancel
		}
	}
	
	@FXML
	public void save(Event e){
		try{
			PrintStream out = new PrintStream(new File("imageFile.txt"));
			if(imageFilePath != null){
				out.print(imageFilePath);
			}
			out.println();
		}catch(Exception ex){
		}
	}
	
	@FXML
	public void keyTyped(Event e){
		keyText.setText(((KeyEvent)e).getCharacter());
	}
	
	@FXML
	public void browser(Event e){
		String url = "http://www.google.com";
		getHostServices().showDocument(url);
	}
	
	@FXML
	public void info(Event e){
		Alert alert = new Alert(AlertType.INFORMATION, "This is demo code.");
		alert.setTitle("About");
		alert.setHeaderText("Demo code");
		alert.show();
	}
	
	@FXML
	public void hyper(Event e){
		String url = "https://en.wikipedia.org/wiki/File:5-George_Frideric_Handel_-_Water_Music_Suite_in_F_major_(Air)_HWV348.ogg";
		getHostServices().showDocument(url);
	}
	
	@FXML
	public void flip(Event e){
		if(flag){
			rect.setFill(Color.INDIGO);
		} else{
			rect.setFill(Color.DODGERBLUE);
		}
		flag = !flag;
	}
	
	@FXML
	public void move(MouseEvent me){
		circle.setCenterX(me.getX());
		circle.setCenterY(me.getY());
	}
	
	@FXML
	public void play(Event e){
		try{
			mediaPlayer.play();
		}catch(Exception ex){
		}
		play.setDisable(true);
		stop.setDisable(false);
	}
	
	@FXML
	public void stop(Event e){
		try{
			mediaPlayer.stop();
		}catch(Exception ex){
		}
		stop.setDisable(true);
		play.setDisable(false);
	}
	
	@FXML
	public void gameOver(Event e) throws Exception{
		Parent root = FXMLLoader.load(getClass().getResource("GameOver.fxml"));
		scene = new Scene(root);
		scene.getStylesheets().add(getClass().getResource("GameOver.css").toExternalForm());
		stage.setScene(scene);
		stage.show();
	}
}

/* All the links!!!
 * https://www.youtube.com/watch?v=9XJicRt_FaI
 * http://tutorials.jenkov.com/javafx/fxml.html#:~:text=In%20order%20to%20load%20an%20FXML%20file%20and,the%20FXML%20file%20must%20be%20located%20at%20C:datahello-world.fxml.
 * https://docs.oracle.com/javafx/2/api/javafx/fxml/doc-files/introduction_to_fxml.html
 * https://docs.oracle.com/javase/8/javafx/api/javafx/scene/Parent.html#lookup-java.lang.String-
 * http://www.java2s.com/example/java/javafx/get-javafx-node-by-id.html
 * https://useiconic.com/open
 * https://www.tutorialspoint.com/javafx/javafx_quick_guide.htm
 * https://www.tutorialspoint.com/javafx-example-to-set-action-behavior-to-the-menu-item
 * http://tutorials.jenkov.com/javafx/menubar.html
 * https://www.tutorialspoint.com/how-to-create-a-menubar-in-javafx
 * https://www.geeksforgeeks.org/javafx-menubar-and-menu/
 * https://stackoverflow.com/questions/9408244/how-to-set-vbox-size-to-window-size-in-javafx
 * http://www.java2s.com/Code/Java/JDK-6/UsingtheDesktopclasstolaunchaURLwithdefaultbrowser.htm
 * https://www.geeksforgeeks.org/javafx-alert-with-examples/
 * https://examples.javacodegeeks.com/desktop-java/javafx/dialog-javafx/javafx-dialog-example/
 * https://stackoverflow.com/questions/10121991/javafx-application-icon
 * https://examples.javacodegeeks.com/desktop-java/javafx/javafx-input-event-example/
 * https://stackoverflow.com/questions/5226212/how-to-open-the-default-webbrowser-using-java
 * https://stackoverflow.com/questions/23202272/how-to-play-sounds-with-javafx
 * https://stackoverflow.com/questions/29870368/javafx-mediaplayer-music-stops-after-10-seconds
 * https://commons.wikimedia.org/wiki/File:5-George_Frideric_Handel_-_Water_Music_Suite_in_F_major_(Air)_HWV348.ogg
 * https://stackoverflow.com/questions/24448002/when-click-on-hyperlinks-in-javafx-a-relevant-url-should-open-in-browser
 * https://stackoverflow.com/questions/9966136/javafx-periodic-background-task
 */
