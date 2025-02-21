# GUI

This directory contains GUI demo code that may help you complete your Final Project.
- Demo.fxml: Structure and formatting for GUI written using JavaFXML
- Demo.java: Accessible GUI written using JavaFX that incluces
  - Menu Bar with various features including
    - File Chooser
    - Launch Browser
    - Alert Box
    - Exit
  - Images
  - Text including some Unicode characters and hyperlinks
  - Responding to characters from keyboard
  - Shapes that respond to mouse events
  - Music controlled by buttons
- [open-iconic](https://useiconic.com/open): A collection of icons under the MIT license. I removed all but the PNGs to save repo size. 
- jacocoant.jar: [JaCoCo](https://www.eclemma.org/jacoco/) is an open source code coverage tool. This jar file work with ant requires no dependencies. Originally from the [offical maven repositiry](https://search.maven.org/search?q=g:org.jacoco)
- build.xml: A build file for ant that can compile, run, and create JaCoCo reports. 

Please delete this directory by your final commit.

## Installation
JavaFX is no longer part of the standard Java SDK. Please download the JavaFX SDK zip file for your OS from [Gluon](https://gluonhq.com/products/javafx/).
Unzip the file and place the folder in either the root of your Java SDK or in your home directory.
Examples

Windows: C:\Program Files\Java\javafx-sdk-21

Unix: /home/aveliz/javafx-sdk-21

Note: Some linux distros may also require installing GTK-4, FFMPEG, and libavcodec.

## Compile and Execute
You need to tell Java where to find JavaFX when you compile and execute the GUI demo. Don't forget to substitute your path to JavaFX/lib.


### Geany: Configure Build Commands 

javafxc: javac --module-path="/home/aveliz/javafx-sdk-21/lib" --add-modules="javafx.controls,javafx.media,javafx.fxml" "%f"

javafx: java --module-path="/home/aveliz/javafx-sdk-21/lib" --add-modules="javafx.controls,javafx.media,javafx.fxml" "%e"

### CLI: 

javac --module-path="/home/aveliz/javafx-sdk-21/lib" --add-modules="javafx.controls,javafx.media,javafx.fxml" Demo.java

java --module-path="/home/aveliz/javafx-sdk-21/lib" --add-modules="javafx.controls,javafx.media,javafx.fxml" Demo


### Ant
Be sure to update the location of your JavaFX location in the build.xml. Also, include any additional .java and .class that you create as the reports are only going to include Demo by default.

Targets:
 - **build** to compile
 - **run** executes the GUI (is also default target)
 - **report** executes and generates a JaCoCo report
 - **clean** removes all class files and the JaCoCo report
