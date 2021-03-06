package com.commerzbank.simpleFileReceptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class Server {

	private static final int port = 8096;

	// MUSS GROESSER 4 SEIN !!!
	private static final int bufferSize = 32;// 1024;
	private static final ExecutorService threadPool = Executors.newFixedThreadPool(100);

	private static final String dataFolder = "data\\";

	private static String uploadPageHtml = "_client\\the simpleFileReceptor dropzone.htm";

	private static final String LINE_SEPARATOR = System.getProperty("line.separator");

	private static final String logFolder = "logs\\";
	private static final String logFileDateFormat = "yyyy.MM.dd HH.mm.ss";
	private static final String logEntryDateFormat = "yyyy.MM.dd HH.mm.ss";

	private static final String logFileExtention = ".log";
	private static final Logger log = Logger.getLogger(Server.class.getName());

	private static boolean run = true;
	private static boolean isRunning = false;

	private static LinkedList<Buffer> connections;
	
	public static void shutdownNow() {
		System.exit(0);
	}

	public static void shutdown() {
		run = false;
		int maxPollAttempts = 3;
		int millisBetweenPollAttempts = 1000 * 3;
		for (int shutDownAttempt = 0; shutDownAttempt < 10; shutDownAttempt++) {
			int pollAttemptNumber = 0;
			while (isRunning) {
				if (pollAttemptNumber < maxPollAttempts) {
					try {
						Thread.sleep(millisBetweenPollAttempts); // 1000
																	// milliseconds
																	// is one
																	// second.
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
				}
				pollAttemptNumber++;
			}
			if (isRunning) {
				threadPool.shutdownNow();
			}
		}
		System.exit(0);
	}

	public static void main(String[] args) {
		System.out.println("T E S T   . . .");
		tests();
		System.out.println("E N D   O F   T E S T");

		File logFolderFile = new File(logFolder);
		if (logFolderFile.exists()) {
			if (!logFolderFile.canWrite()) {
				System.err.println("can not write in log folder! Shut Down ...");
				return;
			}
		} else {
			if (!logFolderFile.mkdir()) {
				System.err.println("can not create log folder! Shut Down ...");
				return;
			}
			if (!logFolderFile.canWrite()) {
				System.err.println("can not write in log folder! Shut Down ...");
				return;
			}
		}
		Handler handler = null;
		try {
			handler = new FileHandler(logFolder + "%g.log", 5242880, 30, true);
		} catch (SecurityException e2) {
			System.err.println(e2.getMessage());
			e2.printStackTrace();
			return;
		} catch (IOException e2) {
			System.err.println(e2.getMessage());
			e2.printStackTrace();
			return;
		}
		handler.setFormatter(new LogFormatter());
		log.addHandler(handler);

		System.out.println("L O G G I N G   I N I T I A L I Z E D");
		File dataFolderFile = new File(dataFolder);
		if (dataFolderFile.exists()) {
			if (!dataFolderFile.canWrite()) {
				log.severe("can not write in data folder! Shut Down ...");
				return;
			}
		} else {
			if (!dataFolderFile.mkdir()) {
				log.severe("can not create data folder! Shut Down ...");
				return;
			}
			if (!dataFolderFile.canWrite()) {
				log.severe("can not write in data folder! Shut Down ...");
				return;
			}
		}
		File idxFile = new File(uploadPageHtml);
		if (idxFile.exists()) {
			if (!idxFile.canWrite()) {
				log.severe("can not read uploadPage! Shut Down ...");
				return;
			}
		} else {
			log.severe("can not find " + uploadPageHtml + "! Shut Down ...");
			return;
		}
		try {
			byte[] encoded = Files.readAllBytes(idxFile.toPath());
			uploadPageHtml = new String(encoded, Charset.defaultCharset());
		} catch (IOException e1) {
			log.log(Level.SEVERE, e1.getMessage(), e1);
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return;
		}
		ServerSocket ss = null;
		try {
			// new ServerSocket (8093, 10, InetAddress.getByName("127.0.0.1"));
			ss = new ServerSocket(port);
			log.info("S E R V E R   S T A R T E D");
			
			threadPool.execute(new Runnable() {
				public void run() {
					while(run){
						try {
							Thread.sleep(90000);
						} catch (InterruptedException ex) {
							Thread.currentThread().interrupt();
						}
						Buffer b = connections.removeFirst();
						long ct = System.currentTimeMillis();
						if(  b.lastReadTime + 120000 > ct && !( b.closeSocket() )  ){
							connections.addLast(b);
						}
					}
				}
			});			
			
			while (run) {
				isRunning = true;
				final Socket s = ss.accept();
				Runnable task = new Runnable() {
					@Override
					public void run() {
						handleRequest(s);
					}
				};
				threadPool.execute(task);
			}
			isRunning = false;
		} catch (IOException e) {
			isRunning = false;
			log.log(Level.SEVERE, e.getMessage(), e);
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				isRunning = false;
				ss.close();
			} catch (IOException e) {
				log.log(Level.SEVERE, e.getMessage(), e);
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public static void tests() {
		try {
			String[] lns = { "\n", "\r", "\r\n" };
			String[] dels = { "\n", "\r", "\r\n", "�", "�", "��", "@", "@@", "@@@", "@q", "@q@", "@q@q" };
			// String[] dels = { "\n", "\r", "\r\n", "@", "@@", "@@@","@q",
			// "@q@", "@q@q" };
			for (String ln : lns) {
				for (String del : dels) {
					for (int b = -3; b < 333; b++) {
						test(b, ln, del);
						// System.out.println("end " + b + " " + ln + " " +
						// del);
					}
				}
			}
		} catch (IOException e) {
			log.log(Level.SEVERE, e.getMessage(), e);
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void test(int bufferSize, String ln, String del) throws IOException {
		ByteArrayInputStream is = null;
		ByteArrayOutputStream fos = new ByteArrayOutputStream();
		// Test 1 + 2
		String l1 = "a1234567a";
		String l2 = "b123456b";
		String l3 = "c12345c";
		String l4 = "d1234d";
		// Test 1
		String req = l1 + ln + l2 + ln + l3 + ln + l4 + ln;
		byte[] reqBytes = req.getBytes();
		is = new ByteArrayInputStream(reqBytes);
		Buffer b = new Buffer(null, is, fos, bufferSize);
		String r1 = writeString(b);
		if (!r1.equals(l1)) {
			log.severe("TEST FEHLER");
		}
		String r2 = writeString(b);
		if (!r2.equals(l2)) {
			log.severe("TEST FEHLER");
		}
		String r3 = writeString(b);
		if (!r3.equals(l3)) {
			log.severe("TEST FEHLER");
		}
		String r4 = writeString(b);
		if (!r4.equals(l4)) {
			log.severe("TEST FEHLER");
		}
		String r5 = writeString(b);
		if (r5 != null) {
			log.severe("TEST FEHLER");
		}
		// Test 2
		String req2 = l1 + del + l2 + del + l3 + del + l4 + del;
		KnuthMorrisPratt boundary = KnuthMorrisPratt.getAlgorithm(del.getBytes());
		byte[] reqBytes2 = req2.getBytes();
		is = new ByteArrayInputStream(reqBytes2);
		b = new Buffer(null, is, fos, bufferSize);
		fos.reset();
		writeFile(b, fos, boundary);
		String rb1 = new String(fos.toByteArray());
		if (!rb1.equals(l1)) {
			log.severe("TEST FEHLER");
		}
		fos.reset();
		writeFile(b, fos, boundary);
		String rb2 = new String(fos.toByteArray());
		if (!rb2.equals(l2)) {
			log.severe("TEST FEHLER");
		}
		fos.reset();
		writeFile(b, fos, boundary);
		// while ((!b.write(fos, boundary)) && (!(b.read(is) == -1)))
		// ;
		String rb3 = new String(fos.toByteArray());
		if (!rb3.equals(l3)) {
			log.severe("TEST FEHLER");
		}
		fos.reset();
		writeFile(b, fos, boundary);
		String rb4 = new String(fos.toByteArray());
		if (!rb4.equals(l4)) {
			log.severe("TEST FEHLER");
		}
		fos.reset();
		while ((!b.write(fos, boundary)) && (!(b.read() == -1)))
			;
		String rb5 = new String(fos.toByteArray());
		if (!rb5.equals("")) {
			log.severe("TEST FEHLER");
		}
	}

	private static class Buffer {
		final static int bufferMinLength = 1; // TODO 5
		private byte[] buffer;
		private int offset;
		private int offsetW;
		private DateFormat logFileDateFormatter = new SimpleDateFormat(logFileDateFormat);

		private InputStream is;
		private OutputStream socketsOutputStream;
		private Socket socket;

		private boolean osClosable;
		
		private long lastReadTime;

		Buffer(Socket socket, InputStream instream, OutputStream outstream) {
			this.buffer = new byte[bufferSize];
			this.offset = 0;
			this.offsetW = 0;

			this.osClosable = false;

			this.is = instream;
			this.socketsOutputStream = outstream;
			this.socket = socket;
			
			lastReadTime = System.currentTimeMillis();
		}

		Buffer(Socket socket, InputStream instream, OutputStream outstream, int size) {
			if (size < 3) { // wichtig, weil ln kann "\r\n" sein (2 Byte).
				size = 3;
			}
			this.buffer = new byte[size];
			this.offset = 0;
			this.offsetW = 0;

			this.osClosable = false;

			this.is = instream;
			this.socketsOutputStream = outstream;
			this.socket = socket;
			
			lastReadTime = System.currentTimeMillis();
		}

		public int read() {
			this.lastReadTime = System.currentTimeMillis();
			int read;
			try {
				read = this.is.read(this.buffer, this.offset, this.buffer.length - this.offset);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				// e.printStackTrace();
				closeSocket();
				return -1;
			}
			if (read > -1) {
				this.offset = this.offset + read;
				if (read < this.buffer.length - this.offset) {
					// log.info(" --> read " + read + " but wanted " +
					// (this.buffer.length - this.offset));
					return read;
				}
			} else {
				log.info(" --> EOF");
				closeSocket();
			}
			return read;
		}

		public void writeLast(OutputStream os, KnuthMorrisPratt kpm) throws IOException {
			if (this.offset <= this.offsetW) {
				this.offset = 0;
				this.offsetW = 0;
				return;
			}
			int l = (this.offset - 0) - this.offsetW;
			int o = -1;
			int patternLength = 0;
			if (kpm != null) {
				l = (this.offset - kpm.pattern.length) - this.offsetW;
				o = kpm.indexOf(this.buffer, this.offsetW, this.offset);
				patternLength = kpm.pattern.length;
			}
			if (o == -1) {
				if (l > 0) {
					os.write(this.buffer, this.offsetW, l);
					this.offsetW += l;
				}
				return;
			} else {
				l = o - this.offsetW;
				if (l > 0) {
					os.write(this.buffer, this.offsetW, l);
					this.offsetW += l;
					this.offsetW += patternLength;
				}
				return;
			}
		}

		public boolean write(OutputStream os, KnuthMorrisPratt kmp) throws IOException {
			if (this.offset <= this.offsetW) {
				this.offset = 0;
				this.offsetW = 0;
				return false;
			}
			int l = (this.offset - 0) - this.offsetW;
			int o = -1;
			int patternLength = 0;
			if (kmp != null) {
				l = (this.offset - kmp.pattern.length) - this.offsetW;
				o = kmp.indexOf(this.buffer, this.offsetW, this.offset);
				patternLength = kmp.pattern.length;
			}
			if (o == -1) {
				if (l > 0) {
					os.write(this.buffer, this.offsetW, l);
					this.offsetW += l;
				}
				o = this.offset - patternLength;
				o = this.offsetW > o ? this.offsetW : o;
				if (this.buffer.length < patternLength * 2) {
					byte[] oldbuffer = this.buffer;
					this.buffer = new byte[patternLength * 2 + bufferMinLength];
					System.arraycopy(oldbuffer, o, this.buffer, 0, this.offset - o);
				} else {
					System.arraycopy(this.buffer, o, this.buffer, 0, this.offset - o);
				}
				this.offset = this.offset - o;
				this.offsetW = 0;
				return false; // jetzt sollt neu gelesen werden ...
			} else {
				l = o - this.offsetW;
				if (l > 0) {
					os.write(this.buffer, this.offsetW, l);
					this.offsetW += l;
					this.offsetW += patternLength;
				}
				return true;
			}
		}

		

		public synchronized void osClosable(boolean closable) {
			this.osClosable = closable;
			//log.info(" --> outstream is finished ");
			//this.closeSocket();
		}

		public boolean closeSocket() {
			boolean close = false;
			synchronized (this) {
				if (this.osClosable) {
					close = true;
				}
			}
			if (!close) {
				return close;
			}

			if (socketsOutputStream != null) {
				log.info(" --> os.flush ");
				try {
					socketsOutputStream.flush();
				} catch (IOException e) {
					log.log(Level.SEVERE, e.getMessage(), e);
					e.printStackTrace();
				}

				log.info(" --> os.close ");
				try {
					socketsOutputStream.close();
				} catch (IOException e) {
					log.log(Level.SEVERE, e.getMessage(), e);
					e.printStackTrace();
				}
				socketsOutputStream = null;
			}

			if (is != null) {
				log.info(" --> is.close ");
				try {
					// log.info(" --> is.close ");
					is.close();
				} catch (IOException e) {
					log.log(Level.SEVERE, e.getMessage(), e);
					e.printStackTrace();
				}
				is = null;
			}

			if (socket == null) {
				log.severe("Socket soll mehrfach geschlossen werden!");
			} else {
				try {
					log.info(" --> s.close ");
					socket.close();
				} catch (IOException e) {
					log.log(Level.SEVERE, e.getMessage(), e);
					e.printStackTrace();
				}
				socket = null;
			}

			return close;
		}

		public void writeLastLn(StringBuilder sb) {
			if (this.offset <= this.offsetW) {
				this.offset = 0;
				this.offsetW = 0;
				return;
			}
			for (int i = this.offsetW; i < this.offset; i++) {
				int c = this.buffer[i];
				if (c == '\r') {
					if (i + 1 < this.offset) {
						c = this.buffer[i + 1];
						if (c == '\n') {
							i++;
						}
					}
					this.offsetW = ++i;
					return;
				}
				if (c == '\n') {
					this.offsetW = ++i;
					return;
				}
				this.offsetW++;
				sb.append((char) c);
			}
			return;

		}

		public boolean writeLn(StringBuilder sb) {
			if (this.offset <= this.offsetW) {
				this.offset = 0;
				this.offsetW = 0;
				return false;
			}
			for (int i = this.offsetW; i < this.offset - 1; i++) {
				int c = this.buffer[i];
				if (c == '\r') {
					c = this.buffer[i + 1];
					if (c == '\n') {
						i++;
					}
					this.offsetW = ++i;
					return true;
				}
				if (c == '\n') {
					this.offsetW = ++i;
					return true;
				}
				this.offsetW++; // eigentlich �berfl�ssig !
				sb.append((char) c);
			}
			this.buffer[0] = this.buffer[this.offset - 1];
			this.offset = 1;
			this.offsetW = 0;
			return false;
		}

		public boolean skipLn(StringBuilder sb) {
			if (this.offset <= this.offsetW) {
				this.offset = 0;
				this.offsetW = 0;
				return false;
			}
			if (this.offsetW < this.offset - 1) {
				int c = this.buffer[this.offsetW];
				if (c == '\r') {
					sb.append((char) c);
					this.offsetW++;
					c = this.buffer[this.offsetW];
					if (c == '\n') {
						sb.append((char) c);
						this.offsetW++;
					}
				} else if (c == '\n') {
					sb.append((char) c);
					this.offsetW++;
				}
				return true;
			}
			return false;
		}

		public void skipLnlast(StringBuilder sb) {
			if (this.offset <= this.offsetW) {
				this.offset = 0;
				this.offsetW = 0;
				return;
			}
			if (this.offsetW < this.offset) {
				int c = this.buffer[this.offsetW];
				if (c == '\r') {
					sb.append((char) c);
					this.offsetW++;
					if (this.offsetW < this.offset) {
						c = this.buffer[this.offsetW];
						if (c == '\n') {
							sb.append((char) c);
							this.offsetW++;
						}
					}

				} else if (c == '\n') {
					sb.append((char) c);
					this.offsetW++;
				}
				return;
			}
			return;
		}

	}

	private static void handleRequest(Socket s) {
		String remoteSocketAddress = s.getRemoteSocketAddress().toString();
		log.info(" --> New Connection:" + remoteSocketAddress);
		InputStream is = null;
		OutputStream os = null;

		try {
			is = s.getInputStream();
			os = s.getOutputStream();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		Buffer b = new Buffer(s, is, os);
		handleRequest(b);
		connections.addLast(b);
		
	}

	private static void handleRequest(Buffer b) {

		if ( b.closeSocket() ) {
			return;
		}
		
		b.osClosable(false);
		
		try {
				String line = writeString(b);
				if (line != null) {
					
					
					log.fine(line.toString());
					if (line.startsWith("POST")) {
						// handleRequestImpl(b);
						threadPool.execute(new Runnable() {
							public void run() {
								logRequest(b, line);
								handleRequest(b);
							}
						});
						log.info(" --> Req behandelt");
						sendUploadPage(b);
						// sendResponse(s.getOutputStream());
						log.info(" --> Res gesendet");
					} else if (line.startsWith("GET / ")) {
						threadPool.execute(new Runnable() {
							public void run() {
								logRequest(b, line);
								handleRequest(b);
							}
						});
						sendUploadPage(b);
					} else if (line.startsWith("OPTIONS")) {
						threadPool.execute(new Runnable() {
							public void run() {
								logRequest(b, line);
								handleRequest(b);
							}
						});
						sendOptions(b);
					} else if (line.startsWith("GET")) {
						log.severe(" --> ERROR : " + line);
						threadPool.execute(new Runnable() {
							public void run() {
								logRequest(b, line);
								handleRequest(b);
							}
						});
						sendErrorCode(b, "404", "SORRY! Die angefragte Datei ist nicht vorhanden!");
					} else {
						log.severe(" --> ERROR : " + line);
						threadPool.execute(new Runnable() {
							public void run() {
								logRequest(b, line);
								handleRequest(b);
							}
						});
						sendErrorCode(b, "501", "SORRY! Die angefragte http-Methode ist nicht implementiert!");
					}
				} else {
					log.severe(" --> ERROR can't read request");
					sendErrorCode(b, "500", "SORRY! Request konnte nicht gelesen werden!");
				}

			//}

			//

		} catch (IOException e) {
			log.log(Level.SEVERE, e.getMessage(), e);
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception ae)

		{
			log.log(Level.SEVERE, ae.getMessage(), ae);
			// TODO Auto-generated catch block
			ae.printStackTrace();
		}

		b.osClosable(true);

	}

	private static void sendErrorCode(Buffer b, String code, String msg) throws IOException {
		PrintWriter out = new PrintWriter(b.socketsOutputStream, false);
		out.println("HTTP/1.1 " + code);
		out.println("Content-Type: text/html");
		out.println("Content-Length: " + msg.length());
		out.println("Connection: close");// out.println("Connection: close");
		out.println("");
		out.print(msg);
		out.println("");
		out.flush();
		out.close();

	}

	private static void sendOptions(Buffer b) throws IOException {
		PrintWriter out = new PrintWriter(b.socketsOutputStream, false);
		out.println("HTTP/1.1 200 OK");
		out.println("Allow: OPTIONS, GET, POST");
		out.println("Content-Length: 0");
		out.println("Connection: close");// out.println("Connection: close");
		out.println("");
		out.flush();
		out.close();

	}

	private static void sendUploadPage(Buffer b) throws IOException {
		PrintWriter out = new PrintWriter(b.socketsOutputStream, false);
		out.println("HTTP/1.1 200 OK");
		out.println("Content-type: text/html");
		out.println("Content-length: " + uploadPageHtml.length());
		// out.println("Connection: Keep-Alive");
		out.println("Connection: close");
		out.println("");
		out.print(uploadPageHtml);
		out.println("");
		out.flush();
		out.close();

	}

	private static void _sendResponse(OutputStream os) {
		PrintWriter out = new PrintWriter(os, false);
		out.println("HTTP/1.0 200");
		out.println("Content-type: text/html");
		out.println("Server-name: franke");
		String response = "<html>n" + "<head>n" + "<title>My Web Server</title></head>n"
				+ "<h1>Welcome to my Web Server!</h1>n" + "</html>n";
		out.println("Content-length: " + response.length());
		out.println("");
		out.println(response);
		out.flush();
		out.close();
	}

	private static void logRequest(Buffer b, String line) {
		String timeStamp = b.logFileDateFormatter.format(new Date());
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(getFile(logFolder + timeStamp + logFileExtention), true);
			byte[] data = (line + LINE_SEPARATOR).getBytes();
			fos.write(data, 0, data.length);
			// (new PrintWriter(fos)).write(line);

			String boundaryStr = "\r\n\r\n";
			byte[] boundaryBytes = boundaryStr.getBytes();
			KnuthMorrisPratt boundary = KnuthMorrisPratt.getAlgorithm(boundaryBytes);

			writeFile(b, fos, boundary);

			fos.flush();
			fos.close();
		} catch (FileNotFoundException e1) {
			log.log(Level.SEVERE, e1.getMessage(), e1);
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			log.log(Level.SEVERE, e.getMessage(), e);
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (fos != null) {
				try {
					fos.flush();
					fos.close();
				} catch (IOException e) {
					log.log(Level.SEVERE, e.getMessage(), e);
					e.printStackTrace();
				}
			}
		}
	}

	static class KnuthMorrisPratt {
		private byte[] pattern;
		private int[] failure;

		public static KnuthMorrisPratt getAlgorithm(byte[] pattern) {
			if (pattern == null) {
				return null;
			}
			KnuthMorrisPratt re = new KnuthMorrisPratt();
			re.pattern = pattern;
			re.failure = computeFailure(pattern);
			return re;
		}

		private KnuthMorrisPratt() {
		}

		public int indexOf(byte[] data, int start, int stop) {
			if (data == null)
				return -1;
			int j = 0;
			for (int i = start; i < stop; i++) {
				while (j > 0 && (pattern[j] != data[i])) {
					j = failure[j - 1];
				}
				if (pattern[j] == data[i]) {
					j++;
				}
				if (j == pattern.length) {
					return i - pattern.length + 1;
				}
			}
			return -1;
		}

		/**
		 * 
		 * Computes the failure function using a boot-strapping process, where
		 * 
		 * the pattern is matched against itself.
		 * 
		 */
		private static int[] computeFailure(byte[] pattern) {
			int[] failure = new int[pattern.length];
			int j = 0;
			for (int i = 1; i < pattern.length; i++) {
				while (j > 0 && pattern[j] != pattern[i]) {
					j = failure[j - 1];
				}
				if (pattern[j] == pattern[i]) {
					j++;
				}
				failure[i] = j;
			}
			return failure;
		}
	}

	private static String skipLn(Buffer b) throws IOException {
		StringBuilder sb = new StringBuilder();
		if (!b.skipLn(sb)) {
			if (b.read() == -1) {
				b.skipLnlast(sb);
			}
		}
		return sb.toString();
	}

	private static String writeString(Buffer b) throws IOException {
		StringBuilder sb = new StringBuilder();
		while (!b.writeLn(sb)) {
			if (b.read() == -1) {
				b.writeLastLn(sb);
				if (sb.length() > 0) {
					return sb.toString();
				}
				return null;
			}
		}
		return sb.toString();
	}

	private static void writeFile(Buffer b, OutputStream o, KnuthMorrisPratt boundary) throws IOException {
		while (!b.write(o, boundary)) {
			if (b.read() == -1) {
				b.write(o, boundary);
				break;
			}
		}
	}

	private static File getFile(String fname) {
		File file = new File(fname);
		if (file.exists()) {
			int endIndex = fname.lastIndexOf(".");
			String fnameEnd = ".file";
			if (endIndex != -1) {
				fnameEnd = fname.substring(endIndex, fname.length());
				fname = fname.substring(0, endIndex);
			}
			int i = 0;
			while (file.exists()) {
				// fname.substring(0, endIndex);
				file = new File(fname + Integer.toString(i++) + fnameEnd);
			}
		}
		return file;
	}

	private static void handleRequestImpl(Buffer b) {
		FileOutputStream fos = null;
		try {
			String fname = "unknown" + System.currentTimeMillis() + ".file";
			String line = writeString(b);
			String boundaryStr = null;
			KnuthMorrisPratt boundary = null;
			while (line != null) {
				log.fine(line.toString());
				if (line.indexOf("Content-Type: multipart/form-data") != -1) {
					boundaryStr = "\r\n--" + line.split("boundary=")[1];
					line = writeString(b);
					// boundaryStr = ln + boundaryStr;
					byte[] boundaryBytes = boundaryStr.getBytes();
					boundary = KnuthMorrisPratt.getAlgorithm(boundaryBytes);
					break;
				}
				line = writeString(b);
			}

			while (line != null) {
				log.fine(line);
				if (line.startsWith("Content-Disposition: form-data; name=\"file")) {
					String filename = line.split("filename=")[1].replaceAll("\"", "");
					String[] filelist = filename.split("\\" + System.getProperty("file.separator"));
					filename = filelist[filelist.length - 1];
					line = writeString(b);
					if (line != null) {
						log.fine(line);
					}
					String ln = skipLn(b);
					skipLn(b);

					/*
					 * line = writeString(b, is); if (line != null) {
					 * log.fine(line); }
					 */
					log.info(" --> File to be uploaded = " + filename);
					if (filename.length() > 0) {
						fname = filename;
					}
					fos = new FileOutputStream(getFile(dataFolder + fname), true);
					writeFile(b, fos, boundary);
					fos.flush();
					fos.close();
					log.info(" --> File zu");
					line = writeString(b);
					if (line != null) {
						log.fine(line);
						if (line.equals("--")) {
							break;
						}
					}
				}
				line = writeString(b);
			}
		} catch (IOException e) {
			log.log(Level.SEVERE, "Failed respond to client request: " + e.getMessage(), e);
		} finally {
			if (fos != null) {
				try {
					fos.flush();
					fos.close();
				} catch (IOException e) {
					log.log(Level.SEVERE, e.getMessage(), e);
					// e.printStackTrace();
				}
			}
		}
		return;
	}

	public static final class LogFormatter extends Formatter {

		private DateFormat logEntryDateFormatter = new SimpleDateFormat(logEntryDateFormat);

		@Override
		public String format(LogRecord record) {
			StringBuilder sb = new StringBuilder();

			sb.append(this.logEntryDateFormatter.format(new Date(record.getMillis()))).append(" ")
					.append(Thread.currentThread().getId()).append(" ").append(record.getLevel().getName())
					// .getLocalizedName())
					.append(": ").append(formatMessage(record)).append(LINE_SEPARATOR);

			if (record.getThrown() != null) {
				try {
					StringWriter sw = new StringWriter();
					PrintWriter pw = new PrintWriter(sw);
					record.getThrown().printStackTrace(pw);
					pw.close();
					sb.append(sw.toString());
				} catch (Exception ex) {
					// ignore
				}
			}

			return sb.toString();
		}
	}

}
