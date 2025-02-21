/**
 * Example of printing color to the terminal.
 * 
 * Windows support is disabled by default. See README for instuctions
 * on enabling support. Mac and Linux should support this code as is.
 * 
 * @see <a href="https://gist.github.com/dainkaplan/4651352">ansi</a>
 */
public class Color{
	public static final String RESET = "\033[0m";
	
	public static final String BLACK 	= "\033[30m";
	public static final String RED 		= "\033[31m";
	public static final String GREEN 	= "\033[32m";
	public static final String YELLOW 	= "\033[33m";
	public static final String BLUE 	= "\033[34m";
	public static final String MAGENTA 	= "\033[35m";
	public static final String CYAN 	= "\033[36m";
	public static final String WHITE 	= "\033[37m";
	
	public static final String BG_BLACK 	= "\033[40m";
	public static final String BG_RED 		= "\033[41m";
	public static final String BG_GREEN 	= "\033[42m";
	public static final String BG_YELLOW 	= "\033[43m";
	public static final String BG_BLUE 		= "\033[44m";
	public static final String BG_MAGENTA 	= "\033[45m";
	public static final String BG_CYAN 		= "\033[46m";
	public static final String BG_WHITE 	= "\033[47m";
	
	public static void main(String[]args){
		
		String rainbow = RESET + 'A' + BLACK+BG_WHITE + 'B' + RED+BG_BLACK+ 'C'+
						GREEN + 'D' + YELLOW + 'E' + BLUE + 'F' + MAGENTA +'G'+
						CYAN + 'H' + WHITE + 'I' + RESET;
		System.out.println("If the following is not a rainbow of color, you need to enable ANSI support.");
		System.out.println(rainbow);
	}
}
