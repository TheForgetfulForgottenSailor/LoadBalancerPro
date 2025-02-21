# Terminal 

This directory contains small demos of code that may be useful in your Final Project.
- Args.java: Uses command line arguments and demos redirected input
- Clear.java: Shows how to clear the terminal contents
- Color.java: Add color to your terminal program output
- Launch.java: Launch a browser, media player, or photo viewer

## Installation
No additional software is needed to run these programs. Unfortunately, Windows does not have ANSI support enabled by default. Mac and Linux users can skip this step.
Visit this [website](https://superuser.com/questions/413073/windows-console-with-ansi-colors-handling) for information on how to edit the registry. 
If you would rather not edit the registry, do not worry. **The addition of color is completely optional** and will not affect your grade in any way. 
You do **NOT** need to describe how to enable ANSI support in your project.

Consider also adding Unicode to your output. Remeber that not all unicode characters are supported because `char` is a certain size. Recall Windows needs 
additional configuration to print unicode in the console. Instructions available on Canvas under Configuring Your Development Environment.

Please delete this directory by your final commit.

## Compile and Execute
You can compile and execute these programs directly from the CLI or Geany. Args.java shows how to pass a seed value for a random number generator to
your program via the command line. Additionally, you can redirect the contents of a file to be "keyboard" input using the '<' symbol as seen below.

`java Args 42 < README.md`
