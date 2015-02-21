package dictionaryLookUpNetParalWrite;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;

public class LookUpServer {

	private static final int MAX_THREADS = (Runtime.getRuntime().availableProcessors() - 1);
	private static final int MAX_CLIENTS = (MAX_THREADS * 10);

	public final Semaphore availableUsers = new Semaphore(0, true);
	public final Semaphore writeFileLock = new Semaphore(1, true);

	private File dictFile = null;
	private volatile String ip = "";
	private volatile int port = 4444;
	private ServerSocket serverSocket = null;
	private volatile boolean isRunning = false;
	private Queue<UserSocket> userSockets = null;
	private ArrayList<ClientThread> threadPool = null;

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

	private class UserSocket {

		public String username = "";
		public Socket socket = null;

		public UserSocket(String name, Socket s) {

			super();
			username = name;
			socket = s;

		}

	}

	private class ManagerThread extends Thread {

		private LookUpServer parrent = null;
		private Queue<String> words = null;
		private Queue<UserSocket> clients = null;

		public ManagerThread(LookUpServer lus) {

			super();
			parrent = lus;
			words = new LinkedList<String>();
			clients = new LinkedList<UserSocket>();

		}
		
		private void addWordToQueue() {

			try {
				parrent.availableUsers.acquire();
			}
			catch (InterruptedException e) {
			    e.printStackTrace();
			}
			UserSocket userSocket = peekParrentSocket();

			if (userSocket != null) {

				int currentTimeOut = 0;
				BufferedReader input = null;
				String in = null;
				try {

					currentTimeOut = userSocket.socket.getSoTimeout();
					userSocket.socket.setSoTimeout(100);
					input = new BufferedReader(new InputStreamReader(userSocket.socket.getInputStream()));

				}
				catch (IOException e) {
					e.printStackTrace();
				}
				try {
					in = input.readLine();
				}
				catch (IOException e1) {
				}
				try {
					userSocket.socket.setSoTimeout(currentTimeOut);
				}
				catch (SocketException e2) {
				}
				if (in == null) {

					synchronized(parrent.getSockets()) {
						pollParrentSocket();
						addParrentSocket(userSocket);
					}
					parrent.availableUsers.release();

				}
				else {

					in = in.trim();
					if (in.split(":")[0].equals(Integer.toString(MSG_TYPE.NORMAL.getValue()))) {

						words.add(in.split(":")[1].trim());
						clients.add(new UserSocket(userSocket.username, userSocket.socket));
						synchronized(parrent.getSockets()) {
							pollParrentSocket();
							addParrentSocket(userSocket);
						}
						parrent.availableUsers.release();

					}
					else {
						try {

							PrintWriter output = new PrintWriter(userSocket.socket.getOutputStream());
							output.print(Integer.toString(MSG_TYPE.TERMINATE.getValue()) + ":\r\n");
							output.flush();
							pollParrentSocket();
							userSocket.socket.close();

						}
						catch (IOException e) {
						}
					}

				}

			}
			else
				pollParrentSocket();

		}

		@Override
		public void run() {
			
			while (getParrentIsRunning()) {

				assert(words.size() == clients.size());
				addWordToQueue();
				assert(words.size() == clients.size());

				if (words.size() > 0)
					for (ClientThread ct : getParrentThreads()) {
	
						if (ct.getIsRunning() && !ct.isProcessingWord()) {
	
							String word = words.poll();
							UserSocket client = clients.poll();
							if (word != null && client != null) {

								word = word.toUpperCase().replaceAll("([^A-Z0-9-])+", "");
								System.out.println("Thread ID: " + getId() + " Assigning thread ID: " + ct.getId() + " to a work for Username: " + client.username + " on word: " + word);
								ct.processWord(client, word);
								break;
	
							}
	
						}

					}

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

		private ArrayList<ClientThread> getParrentThreads() {
			synchronized(parrent) {
				return parrent.getThreads();
			}
		}

		private void addParrentSocket(UserSocket us) {
			synchronized (parrent) {
				parrent.addSocket(us);
			}
		}
		private UserSocket pollParrentSocket() {
			synchronized (parrent) {
				return parrent.pollUserSocket();
			}
		}

		private UserSocket peekParrentSocket() {
			synchronized (parrent) {
				return parrent.peekUserSocket();
			}
		}

	}

	private class ClientThread extends Thread {

		private final Semaphore available = new Semaphore(0, true);

		private volatile boolean isRunning = false;
		private LookUpServer parrent = null;
		private volatile String word = "";
		private UserSocket client = null;

		public ClientThread(LookUpServer lus) {

			super();
			parrent = lus;

		}

		private void addWord(File dictFile, String word) throws IOException {

			try {
				parrent.writeFileLock.acquire();
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
			PrintWriter pr = null;
			BufferedReader br = null;
			File temp = new File(Paths.get(System.getProperty("user.dir"), "temp.txt").toUri());
			temp.setExecutable(dictFile.canExecute());
			temp.setReadable(dictFile.canRead());
			temp.setWritable(true);
			try {
				temp.createNewFile();
				pr = new PrintWriter(new FileWriter(temp, true));
				br = new BufferedReader(new FileReader(dictFile));
			}
			catch (IOException e1) {
				e1.printStackTrace();
			}

			String line = "";
			boolean wroteWord = false;
			while ((line = br.readLine()) != null) {

				if (line.equals(word.toUpperCase()))
					wroteWord = true;

				if (!wroteWord && line.matches("([A-Z0-9-])+") && line.compareTo(word.toUpperCase()) > 0) {
					pr.println(word.toUpperCase() + "\n");
					wroteWord = true;
				}

				if (!wroteWord && line.equals("End of Project Gutenberg's Webster's Unabridged Dictionary, by Various")) {

					pr.println(word.toUpperCase() + "\n");
					wroteWord = true;

				}
				pr.println(line);

			}
			temp.setWritable(dictFile.canWrite());
			dictFile.delete();
			temp.renameTo(dictFile);
			br.close();
			pr.close();
			parrent.writeFileLock.release();

		}
		
		private String getDef(File dictFile, String word) throws InterruptedException {

			BufferedReader br = null;
			parrent.writeFileLock.acquire();
			try {
				br = new BufferedReader(new FileReader(dictFile));
			}
			catch (FileNotFoundException e1) {
				e1.printStackTrace();
			}

			String line = "";
			String output = "";

			if (word.equals("")) {

				parrent.writeFileLock.release();
				return "No word given\r\n";
			}

			try {

				boolean wordFound = false;
				while ((line = br.readLine()) != null) {

					if (line.equals(word)) {

						wordFound = true;
						output += line;
						output += "\r\n";
						while((line = br.readLine()) != null) {

							if (line.matches("([A-Z0-9-])+") && !line.equals(word) && !line.equals(""))
								break;
							if (line.equals("End of Project Gutenberg's Webster's Unabridged Dictionary, by Various")) {

								line = null;
								break;

							}
							output += line;
							output += "\r\n";

						}
						if (line == null)
							break;

					}
				}
				parrent.writeFileLock.release();
				if (!wordFound) {

					output += word;
					output += " not found. Adding word to dictionary.\r\n";
					System.out.println("Thread ID: " + getId() + "-- did not find " + word + " in the dictionary.");
					System.out.println("Thread ID: " + getId() + "-- adding: " + word + " to the dictionary.");
				    addWord(dictFile, word);

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
					//sleep(10000);
					available.acquire();
				}
				catch (InterruptedException e) {
					e.printStackTrace();
				}
				if (isRunning && getParrentIsRunning() && !word.equals("") && client != null) {

					PrintWriter output = null;
					synchronized(userSockets) {
						try {
							output = new PrintWriter(client.socket.getOutputStream());
						}
						catch (IOException e) {
							e.printStackTrace();
						}

					}

					System.out.println("Thread ID: " + this.getId() + "-- Searching for word: " + word);
					String out = "";
					try {
						out = getDef(getParrentDictFile(), word);
					}
					catch (InterruptedException e) {
						e.printStackTrace();
					}
					output.print(Integer.toString(MSG_TYPE.NORMAL.getValue()) + ":" + out + "\r\n||END||" + "\r\n");
					output.flush();
					synchronized(this) {
						word = "";
						client = null;
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

		public void processWord(UserSocket client, String word) {

			synchronized(this) {
				if (this.client == null && this.word.equals("")) {

					this.client = client;
					this.word = word;
					available.release();

				}
			}

		}
		public boolean isProcessingWord() {
			synchronized(this) {
				return (client != null && !word.equals(""));
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
		userSockets = new LinkedList<UserSocket>();
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

	public ArrayList<ClientThread> getThreads() {
		return threadPool;
	}

	public UserSocket pollUserSocket() {
		return userSockets.poll();
	}

	public UserSocket peekUserSocket() {
		return userSockets.peek();
	}

	public Queue<UserSocket> getSockets() {
		return userSockets;
	}
	
	public void addSocket(UserSocket us) {
		userSockets.add(us);
	}

	public void acceptUser(Socket newClientSocket) throws IOException {

		PrintWriter output = new PrintWriter(newClientSocket.getOutputStream());
		BufferedReader input = new BufferedReader(new InputStreamReader(newClientSocket.getInputStream()));

		output.print(MSG_TYPE.WELCOME.getValue() + ":" + "Welcome to the server, please send your username\r\n");
		output.flush();

		String line = "";
		int count = 0;
		boolean validUsername = true;
		line = input.readLine();
		if (line == null)
			return;

		line = line.trim();
		if (!line.split(":")[0].equals(Integer.toString(MSG_TYPE.CONFIRM.getValue()))) {

			output.print(MSG_TYPE.TERMINATE.getValue() + ":" + "Invalid message\r\n");
			output.flush();
			return;

		}
		line = line.split(":")[1];
		line = line.trim();
		synchronized(userSockets) {

			Iterator<UserSocket> listIterator = userSockets.iterator();
	        while (listIterator.hasNext()) {

	        	UserSocket user = listIterator.next();
				if (user.username.toUpperCase().equals(line.toUpperCase())) {

					output.print(MSG_TYPE.TERMINATE.getValue() + ":" + "Invalid username\r\n");
					output.flush();
					validUsername = false;
					newClientSocket.close();
					break;

				}

	        }

		}

		if (count >= 3 || !validUsername)
			return;

		output.print(MSG_TYPE.CONFIRM.getValue() + ":" + "Username confirmed as " + line + "\r\n");
		output.flush();

		synchronized(userSockets) {

			userSockets.add(new UserSocket(line, newClientSocket));
			availableUsers.release();

		}

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