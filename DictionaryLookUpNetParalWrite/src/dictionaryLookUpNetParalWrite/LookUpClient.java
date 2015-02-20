package dictionaryLookUpNetParalWrite;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Paths;

public class LookUpClient {

	private String ip = "";
	private int port = 4444;
	private Socket clientSocket = null;
	private String userName = "";

	public static enum MSG_TYPE {

		WELCOME(0),
		CONFIRM(1),
		NORMAL(2),
		TERMINATE(3);

		private final int value;
		private MSG_TYPE(int value) {
			this.value = value;
		}

		public int getValue() {

			return this.value;

		}

	}

	public LookUpClient() {
		super();
	}

	public LookUpClient(String ip, int port) {

		super();
		this.ip = ip;
		this.port = port;
		clientSocket = new Socket();
		try {
			askForUserName();
			clientSocket.connect(new InetSocketAddress(this.ip, this.port));
		}
		catch (IOException e) {
			e.printStackTrace();
		}

	}

	public String getWord(BufferedReader br) {

		String word = "";
        do {

		    try {
		        word = br.readLine();
		    }
		    catch (IOException ioe) {
		        System.err.println("getWord() IO error trying to read word, because:" + ioe.getLocalizedMessage());
		        System.exit(1);
		    }
		    if (word == null)
		    	return word;
		    word = word.trim();

        } while (word.equals(""));

	    word = word.toUpperCase();
		return word;

	}

	public void runClient(File f) {

		BufferedReader br = null;
		BufferedReader input = null;
		PrintWriter output = null;
		try {

			br = new BufferedReader(new FileReader(f));
			input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			output = new PrintWriter(clientSocket.getOutputStream());

		}
		catch (IOException e1) {
			e1.printStackTrace();
		}

		String word = "";
		while ((word = getWord(br)) != null) {

			output.print(MSG_TYPE.NORMAL.getValue() + ":" + word + "\r\n");
			output.flush();
			String out = "";
			try {

				out = input.readLine();
				if (out == null || !out.split(":")[0].equals(Integer.toString(MSG_TYPE.NORMAL.getValue())))
					break;
				System.out.println(out.split(":")[1]);
				while(!((out = input.readLine()).trim().equals("||END||")))
					System.out.println(out);
				System.out.println();

			}
			catch (IOException e) {
				e.printStackTrace();
			}

		}
		output.print(MSG_TYPE.TERMINATE.getValue() + ":\r\n");
		output.flush();
		try {
			output.close();
			input.close();
			br.close();
			clientSocket.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}

	}

	public void askForUserName() {

		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        userName = "";

        do {

	        System.out.print("Enter a Username: ");
		    try {
		    	userName = br.readLine();
		        System.out.println();
		    }
		    catch (IOException ioe) {
		        System.err.println("getUserName() IO error trying to read word, because:" + ioe.getLocalizedMessage());
		        System.exit(1);
		    }
		    userName = userName.trim();

        } while (userName.equals(""));

        userName = userName.toUpperCase();

	}

	public boolean setUpConnection() {

		try {

			int count = 0;
			String line = "";
			do {

				BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
	
				// Read welcome message
				line = input.readLine();
				if (line == null) {
	
					return false;
	
				}
	
				line = line.trim();
				if (!line.split(":")[0].equals(Integer.toString(MSG_TYPE.WELCOME.getValue()))) {
	
					return false;
	
				}
	
				PrintWriter output = new PrintWriter(clientSocket.getOutputStream());

				output.print(MSG_TYPE.CONFIRM.getValue() + ":" + userName + "\r\n");
				output.flush();

				line = input.readLine();
				if (line == null) {

					return false;

				}
				line = line.trim();
				if (!line.split(":")[0].equals(Integer.toString(MSG_TYPE.CONFIRM.getValue()))) {
					System.out.println("Username is already in use.");
					if ((count + 1) < 3) {
						try {
							clientSocket.close();
							clientSocket = new Socket();
							askForUserName();
							clientSocket.connect(new InetSocketAddress(this.ip, this.port));
						}
						catch (IOException e) {
							e.printStackTrace();
						}
					}
				}

			} while ((++count) < 3 && !line.split(":")[0].equals(Integer.toString(MSG_TYPE.CONFIRM.getValue())));

			if (count < 3 && line.split(":")[0].equals(Integer.toString(MSG_TYPE.CONFIRM.getValue())))
				return true;

		}
		catch (IOException e) {
			return false;
		}
		return false;

	}

	public static void main(String[] args) {
		
		try {

			String ip = InetAddress.getLocalHost().getHostAddress();
		    int port = 4444;
		    File file = Paths.get(System.getProperty("user.dir"), "words.txt").toFile();
		    String filePath = "";

			if (args.length > 0) {

			    if (args.length > 1) {

			    	if (args[0].contains(".") && args[0].contains(":")) {

			    		ip = args[0].split(":")[0];
			    		port = Integer.parseInt(args[0].split(":")[1]);
			    		filePath = args[1];

			    	}
			    	else if (args[0].contains(".") && !args[0].contains(":")) {

			    		ip = args[0];
			    		filePath = args[1];

			    	}
			    	else if (!args[0].contains(".") && args[0].contains(":")) {

			    		port = Integer.parseInt(args[0].split(":")[1]);
			    		filePath = args[1];

			    	}
			    	else {

			    		ip = args[1].split(":")[0];
			    		port = Integer.parseInt(args[1].split(":")[1]);
			    		filePath = args[0];

			    	}

				}
				else {

					if (args[0].contains(".") && args[0].contains(":")) {

			    		ip = args[0].split(":")[0];
			    		port = Integer.parseInt(args[0].split(":")[1]);

			    	}
			    	else if (args[0].contains(".") && !args[0].contains(":")) {

			    		ip = args[0];

			    	}
			    	else if (!args[0].contains(".") && args[0].contains(":")) {

			    		port = Integer.parseInt(args[0].split(":")[1]);

			    	}
			    	else {

			    		filePath = args[0];

			    	}

				}

			}

			if (!filePath.equals(""))
				file = new File(filePath);

			if (file.exists() && !file.isDirectory() && file.isFile() && file.canRead()) {

				LookUpClient luc = new LookUpClient(ip, port);
				if (luc.setUpConnection())
					luc.runClient(file);

			}
			else {

				System.err.println("Error: " + file.getPath() + " does not exists, it is not a regular file, or it cannont be read.");
				System.exit(1);

			}

		}
		catch (IOException e) {
            e.printStackTrace();
		}

	}

}