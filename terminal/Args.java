/**
 * Example of using command line arguments to seed a random number generator 
 * and redirect keyboard input like it was manually entered but is actually 
 * the content of a file.
 * 
 * java Args seed < filename
 * 
 * seed is an integer
 * filename contains the contents you want to input
 * < denotes redirected input, you can also use > to redirect output to a file
 * 
 * You can omit either/both but then the generated numbers will be unpredictable 
 * or the program will wait for keyboard input.
 * 
 * @author Adrian Veliz
 */

import java.util.*;
public class Args{
	/**
	 * Global random number generator and scanner to be shared among classes after init
	 */
	public static Random rand;
	public static Scanner console;
	
	public static void main(String[]args){
		int seed = 0;
		String output = "";
		
		if(args.length > 0){
			try{
				seed = Integer.parseInt(args[0]);
			}catch(NumberFormatException nfe){
				System.out.println("Seed is not a number");
				System.exit(0);
			}
			rand = new Random(seed);
			output = "Expect to get the following output:\n";
			if(seed == 9_000)
				output += "4675 6287 5968 303 6460 1568 2201 6575 874 1968";
			else if(seed == 42)
				output += "4130 5763 4248 6884 2970 4525 4505 2918 1519 8093";
			else if(seed == 12345)
				output += "5251 5080 8241 828 3055 1084 1375 8802 1501 4389";
			else if(seed == 54321)
				output += "2928 451 8642 6402 4522 3773 5977 7704 5893 8115";
			else
				output = "Unknown seed given. Cannot predict random output.";
		}else{
			rand = new Random();
			output = "No seed value given. Cannot predict random output.";
		}
		
		System.out.println(output);
		for(int i = 0;i < 10; i++){
			System.out.print(rand.nextInt(9000) + " ");
		}
		System.out.println();
		/*
		 * Notice this scanner expects to read input from the keyboard
		 * because it uses System.in. It will not know the difference 
		 * if the input is actually from the keyboard or if it was 
		 * redirected contents of a file.
		 * 
		 * If using redirected input, you cannot have more than one Scanner 
		 * instance using System.in as only the first instance will store 
		 * the redirected input in its buffer. 
		 */
		console = new Scanner(System.in);
		System.out.println("This program will now print all keyboard input by line:");
		while(console.hasNext()){
			System.out.println(console.nextLine());
		}
		console.close();
	}
}
