package dictionaryLookUpNetParalWrite;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;

public class LookUpServer {

	private static final int MAX_THREADS = (Runtime.getRuntime().availableProcessors() - 1);
	private static final int MAX_CLIENTS = (MAX_THREADS * 10);
	public final Semaphore availableJobs = new Semaphore(0, true);

	private File dictFile = null;
	private volatile String ip = "";
	private volatile int port = 4444;
	private ServerSocket serverSocket = null;
	private volatile boolean isRunning = false;
	private HashMap<String, Socket> userSockets = null;
	private ArrayList<ClientThread> threadPool = null;
	private Queue<String> jobs = null;

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

	private class ManagerThread extends Thread {

		private LookUpServer parrent = null;

		public ManagerThread(LookUpServer lus) {
			super();
			parrent = lus;
		}

		@Override
		public void run() {
			
			while (getParrentIsRunning()) {

				try {
					parrent.availableJobs.acquire();
				}
				catch (InterruptedException e) {
					e.printStackTrace();
				}
				boolean useKey = false;
				for (ClientThread ct : getParrentThreads()) {
					if (ct.getIsRunning() && !ct.isProcessingWord()) {

						String key = getParrentJobs().poll();
						if (key != null) {

							System.out.println("Thread ID: " + getId() + " Assigning thread ID: " + ct.getId() + " to a work for Username: " + key);
							ct.processWord(key);
							useKey = true;
							break;

						}

					}

				}
				if (!useKey)
					parrent.availableJobs.release();

			}
			for (ClientThread ct : getParrentThreads()) {
				ct.setIsRunning(false);
				try {
					ct.join();
				}
				catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
		}

		private boolean getParrentIsRunning() {
			synchronized(parrent) {
				return parrent.getIsRunning();
			}
		}
		private Queue<String> getParrentJobs() {
			synchronized(parrent) {
				return parrent.getJobs();
			}
		}
		private ArrayList<ClientThread> getParrentThreads() {
			synchronized(parrent) {
				return parrent.getThreads();
			}
		}

	}

	private class ClientThread extends Thread {

		private final Semaphore available = new Semaphore(0, true);

		private volatile boolean isRunning = false;
		private LookUpServer parrent = null;
		private volatile String word = "";
		private volatile String clientName = "";

		public ClientThread(LookUpServer lus) {

			super();
			parrent = lus;

		}

		private String getDef(File dictFile, String word) {

			BufferedReader br = null;
			try {
				br = new BufferedReader(new FileReader(dictFile));
			}
			catch (FileNotFoundException e1) {
				e1.printStackTrace();
			}

			String line = "";
			String output = "";

			if (word.equals(""))
				return "word not found. Perhaps you misspelled it.\n";

			try {

				boolean wordFound = false;
				while ((line = br.readLine()) != null) {

					if (line.equals(word)) {

						wordFound = true;
						output += line;
						output += "\n";
						while((line = br.readLine()) != null) {

							if (line.matches("([A-Z])+") && !line.equals(word) && !line.equals(""))
								break;
							output += line;
							output += "\n";

						}
						if (line == null)
							break;

					}

				}
				if (!wordFound) {

					output += word;
				    output += " not found. Perhaps you misspelled it.\n";

				}

			}
			catch (IOException e) {
				e.printStackTrace();
			}

			try {
				br.close();
			}
			catch (IOException e) {
				e.printStackTrace();
			}

			return output;

		}
		
		@Override
		public void run() {

			isRunning = true;
			while (isRunning && getParrentIsRunning()) {

				try {
					available.acquire();
				}
				catch (InterruptedException e) {
					e.printStackTrace();
				}
				if (isRunning && getParrentIsRunning() && !word.equals("") && !clientName.equals("")) {

					PrintWriter output = null;
					BufferedReader input = null;
					synchronized(userSockets) {
						try {
							output = new PrintWriter(userSockets.get(clientName).getOutputStream());
							input = new BufferedReader(new InputStreamReader(userSockets.get(clientName).getInputStream()));
						}
						catch (IOException e) {
							e.printStackTrace();
						}

					}
					boolean lastWord = false;
					do {

						System.out.println("Thread ID: " + this.getId() + "-- Searching for word: " + word);
						String out = getDef(getParrentDictFile(), word);
						output.print(Integer.toString(MSG_TYPE.NORMAL.getValue()) + ":" + out + "||END||" + "\r\n");
						output.flush();

						try {
							word = input.readLine().trim();
						}
						catch (IOException e) {
							e.printStackTrace();
						}
						if (word.split(":")[0].equals(Integer.toString(MSG_TYPE.NORMAL.getValue())))
							word = word.split(":")[1].trim();
						else
							lastWord = true;

					} while(!lastWord);

					synchronized(this) {

						removeParrentUsername();
						this.word = "";
						this.clientName = "";
//						try {
//							output.close();
//							input.close();
//						}
//						catch (IOException e) {
//							e.printStackTrace();
//						}

					}

				}

			}

		}

		public boolean getIsRunning() {
			return isRunning;
		}
		public void setIsRunning(boolean isRun) {
			isRunning = isRun;
		}

		public void processWord(String clientName) {
			synchronized(this) {
				if (this.clientName.equals("") && this.word.equals("")) {

					try {

						synchronized(userSockets) {

							BufferedReader input = new BufferedReader(new InputStreamReader(userSockets.get(clientName).getInputStream()));
							word = input.readLine().trim();

						}

					}
					catch (IOException e) {
						e.printStackTrace();
					}
					if (word.split(":")[0].equals(Integer.toString(MSG_TYPE.NORMAL.getValue()))) {

						this.clientName = clientName;
						word = word.split(":")[1].trim();
						available.release();

					}

				}
			}
		}
		public boolean isProcessingWord() {
			synchronized(this) {
				return (!clientName.equals("") && !word.equals(""));
			}
		}

		private boolean getParrentIsRunning() {
			synchronized(parrent) {
				return parrent.getIsRunning();
			}
		}
		private File getParrentDictFile() {
			synchronized(parrent) {
				return parrent.getDictFile();
			}
		}
		private void removeParrentUsername() {
			synchronized(parrent) {
				parrent.removeUsername(this.clientName);
			}
		}

	}

	public LookUpServer() {
		super();
	}

	public LookUpServer(String ip, int port, File f) {

		super();
		this.ip = ip;
		this.port = port;
		this.dictFile = f;
		try {
			serverSocket = new ServerSocket();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		try {
			serverSocket.bind(new InetSocketAddress(this.ip, this.port));
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		userSockets = new HashMap<String, Socket>(MAX_CLIENTS);
		jobs = new LinkedList<String>();
		threadPool = new ArrayList<ClientThread>(MAX_THREADS);
		isRunning = true;
		for (int i = 0; i < MAX_THREADS; i++) {
			threadPool.add(new ClientThread(this));
			threadPool.get(i).start();
		}

	}

	public boolean getIsRunning() {
		return isRunning;
	}
	public void setIsRunning(boolean isRun) {
		isRunning = isRun;
	}

	public File getDictFile() {
		return dictFile;
	}

	public Queue<String> getJobs() {
		return jobs;
	}
	public ArrayList<ClientThread> getThreads() {
		return threadPool;
	}
	public void removeUsername(String name) {
		userSockets.remove(name);
	}

	public void acceptUser(Socket newClientSocket) throws IOException {

		PrintWriter output = new PrintWriter(newClientSocket.getOutputStream());
		BufferedReader input = new BufferedReader(new InputStreamReader(newClientSocket.getInputStream()));

		output.print(MSG_TYPE.WELCOME.getValue() + ":" + "Welcome to the server, please send your username\r\n");
		output.flush();

		String line = "";
		int count = 0;
		boolean validUsername = true;
		//do {

			validUsername = true;
			line = input.readLine();
			if (line == null) {

//				input.close();
//				output.close();
				return;

			}
			line = line.trim();
			if (!line.split(":")[0].equals(Integer.toString(MSG_TYPE.CONFIRM.getValue()))) {

				output.print(MSG_TYPE.TERMINATE.getValue() + ":" + "Invalid message\r\n");
				output.flush();
//				output.close();
//				input.close();
				return;

			}
			line = line.split(":")[1];
			line = line.trim();
			synchronized(userSockets) {
				for (String user : userSockets.keySet()) {
					if (user.toUpperCase().equals(line.toUpperCase())) {

						output.print(MSG_TYPE.TERMINATE.getValue() + ":" + "Invalid username\r\n");
						output.flush();
						validUsername = false;
						break;

					}
				}
			}

		//} while((++count) < 3 && !validUsername);
		if (count >= 3 || !validUsername) {

//			input.close();
//			output.close();
			return;

		}
		output.print(MSG_TYPE.CONFIRM.getValue() + ":" + "Username confirmed as " + line + "\r\n");
		output.flush();

		synchronized(userSockets) {

			userSockets.put(line, newClientSocket);
			jobs.add(line);
			availableJobs.release();

		}
		//input.close();
		//output.close();

	}

	public void runServer() {

		ManagerThread mt = new ManagerThread(this);
		mt.start();
		while (isRunning) {

			synchronized(userSockets) {
				if (userSockets.size() >= MAX_CLIENTS) {
					try {
						Thread.sleep(100);
					}
					catch (InterruptedException e) {
						continue;
					}
					continue;
				}
			}

			try {
				acceptUser(serverSocket.accept());
			}
			catch (IOException e) {
				continue;
			}

		}

	}

	public static void main(String[] args) {

		try {

			String ip = InetAddress.getLocalHost().getHostAddress();
		    int port = 4444;
		    File file = Paths.get(System.getProperty("user.dir"), "dictionary.txt").toFile();
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

				LookUpServer lus = new LookUpServer(ip, port, file);
				lus.runServer();

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