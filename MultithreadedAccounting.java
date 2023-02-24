import java.util.Scanner;
import java.io.FileNotFoundException;
import java.io.File;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.PriorityQueue;
import java.util.Stack;
import java.util.HashMap;
import static java.lang.Math.min;
import static java.lang.Math.max;
import java.util.concurrent.*;


/**
 * Multithreaded Accounting application to solve the fetch coding challenge
 *
 * @author Patrick McCarty
 * 	date created: 02/23/2023
 */
public class MultithreadedAccounting{

	public static final String PAYER = "\"payer\"";			// column name for payer
	public static final String POINTS = "\"points\"";		// column name for points
	public static final String TIMESTAMP = "\"timestamp\"";		// column name for timestamp


	/**
	 * Process the data and do all the accounting specified by the problem statement
	 * 
	 * @param args[0] number of points the user will spend
	 * @param args[1] path to a CSV file where the data will be read from
	 */
	public static void main(String[] args) throws FileNotFoundException{
		int pointsToSpend = -1;		
		String filePath = null;
		Stack<Transaction> negativeTransactions;
		PriorityQueue<Transaction> positiveTransactions;
		HashMap<String, Integer> payerMap;

		if(args.length != 2){ // wrong number of command line arguments
			System.out.println("Usage: java Accounting <points to spend(integer)> <filepath>");
			System.exit(1);	
		}
		try{
			pointsToSpend = Integer.parseInt(args[0]);
		} catch(NumberFormatException e){ // number of points was not a valid integer
			System.out.println("\'" + args[0] + "\' is not a valid integer");
			System.exit(1);	
		}
		filePath = args[1];

		boolean success = loadTransactions(filePath);
		if(!success){
			throw new IllegalStateException("Could not load transactions form CSV file");	
		}
		
		positiveTransactions = MyRunnable.getPQ();
		negativeTransactions = MyRunnable.getStack();

		if(positiveTransactions == null || positiveTransactions.size() == 0 || negativeTransactions == null){
			throw new IllegalStateException("Could not load transactions form CSV file");	
		}

		payerMap = new HashMap<String, Integer>();

		// process all negative transactions first so we don't accidentaly leave a payer negative
		while(!negativeTransactions.empty()){
			Transaction t = negativeTransactions.pop();
			if(!payerMap.containsKey(t.payer)){
				payerMap.put(t.payer, t.points);
			} else{
				payerMap.replace(t.payer, payerMap.get(t.payer) + t.points);	
			}
		}

		// process all positive transactions starting with the oldest
		while(positiveTransactions.size() > 0){
			Transaction t = positiveTransactions.poll();
			if(!payerMap.containsKey(t.payer)){ // first time we have seen this payer
				payerMap.put(t.payer, 0);	
			} 
            		if(payerMap.get(t.payer) >= 0 && pointsToSpend == 0){ 
				/* payer does not have a negative balance and we have processed all
				   points the user requested */
				payerMap.replace(t.payer, payerMap.get(t.payer) + t.points);	
			} else if(payerMap.get(t.payer) >= 0){ 
				/* payer does not have a negative balance but we still need to process
				   the users requested points total */
				int temp = pointsToSpend;
				pointsToSpend = max(pointsToSpend - t.points, 0);
				payerMap.replace(t.payer, payerMap.get(t.payer) + max(t.points - temp, 0));
			} else if(payerMap.get(t.payer) < 0){
				/* payer balance is negative. We must first pay for this until it is 0 */

				if(t.points > 0){ // pay negative balance first
					int temp = payerMap.get(t.payer);
					payerMap.replace(t.payer, min(payerMap.get(t.payer) + t.points, 0));
					t.points += temp;
				}
				
				if(t.points > 0){ // if there are points left pay off points requested by user
					int temp = pointsToSpend;
					pointsToSpend = max(pointsToSpend - t.points, 0);
					t.points -= temp;	
				} 
				if(t.points > 0){ // if there are any points left over add it for this payer
					payerMap.replace(t.payer, payerMap.get(t.payer) + t.points);	
				}
			}
		}

		// print out the information in the specified format
		System.out.println("{");
		for(String key : payerMap.keySet()){
			System.out.println("\t\"" + key + "\": " + payerMap.get(key));
		}
		System.out.println("}");
	}


	/**
	* Loads loads the transactions from a CSV file into a PriorityQueue for the positive ones
	* and loads the transactions with negative 'points' values into a Stack
	*
	* @param filepathToCSV path to the CSV file relative to the program 
	* @return true if the files where read successfully and false otherwise 
	* @throws FileNotFoundException if file does not exist or cannot be read
	*/
	public static boolean loadTransactions (String filepathToCSV) throws FileNotFoundException{

		File CSVfile = new File(filepathToCSV);
		Scanner scanner = new Scanner(CSVfile, "UTF-8");


		if(!scanner.hasNextLine()){
			scanner.close(); // file is empty
			return false;
		}
		int indxs[] = getIndexes(scanner.nextLine());
		for(int i = 0; i < indxs.length; i++){
			if(indxs[i] == -1){
				scanner.close(); // file does not contain one of the needed columns
				return false;
			}
		}	

		MyRunnable.setIndeces(indxs[0], indxs[1], indxs[2]);

		// number of cores available for this program
		ExecutorService executor = 
				Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()); 

		/* create as many threads as there are CPU cores available to process rows of data
		   into Transaction objects. This can be done concurently to speed up processing */ 
		try{
			while(scanner.hasNextLine()){
				String line = scanner.nextLine();
				executor.execute(new MyRunnable(line));
			}
		}catch(Exception err){
            		err.printStackTrace();
        	}
        	executor.shutdown(); // done with excecutor 
		scanner.close(); // done with Scanner
		try {
     			// Wait at most 10 minutes for existing tasks to terminate
     			if (!executor.awaitTermination(600, TimeUnit.SECONDS)) {
       				executor.shutdownNow(); // Cancel currently executing tasks
       				// Wait for 1 minute for tasks to respond to being cancelled
       				if (!executor.awaitTermination(60, TimeUnit.SECONDS))
           				System.err.println("Pool did not terminate");
     			}
   		} catch (InterruptedException ie) {
     			// (Re-)Cancel if current thread also interrupted
     			executor.shutdownNow();
     			// Preserve interrupt status
     			Thread.currentThread().interrupt();
   		}
		return true;
	}

	
	/**
	* Returns the column number of the keywords "payer", "points", "timeStamp" 
	* and the number of columns in the dataset
	*
	* @param header the header of a the dataset
	* @return an array containing the column number of "payer", "points" 
	*		and "timestamp" and the number of columns if the dataset. If column
	*		name does not exist return -1 for the given entry of the array
	*/
	private static int[] getIndexes(String header){

		int[] indexes = {-1, -1, -1};
			String[] wordsArr = header.split(",");

			// populate the array with the correct information
			for(int i = 0; i < wordsArr.length; i++){
				if(wordsArr[i].trim().equals(PAYER)){
					indexes[0] = i;
				} else if(wordsArr[i].trim().equals(POINTS)){
					indexes[1] = i;
				} else if(wordsArr[i].trim().equals(TIMESTAMP)){
					indexes[2] = i;
				}
			}
		return indexes;
	}


	/**
	* Returns an array containing the payer, points and timestamp
	*
	* @param row the specific row from the CSV file
	* @param payerIdx the column where "payer" is located
	* @param pointsIdx the column where "points" is located
	* @param timestampIdx the column where "timestamp" is located
	* @return an array of Strings containing the information of "payer", "points" and "timestamp"
	*/
	protected static String[] getInfo(String row, int payerIdx, int pointsIdx, int timestampIdx){
		
		String[] info = new String[3];

		row = row.replaceAll("\"", ""); // remove double quotes since they are used to contain data
		String[] wordArr = row.split(","); // commas a delimiters
		
		info[0] = wordArr[payerIdx];
		info[1] = wordArr[pointsIdx];
		info[2] = wordArr[timestampIdx];
		return info;
	}
}


/**
 * Represents a specific transaction for a user
 */
class Transaction implements Comparable<Transaction>{
	public String payer; 		// payer for this Transaction
	public int points;		// points awarded during this Transaction
	public LocalDateTime dt;	// date and time of this Transaction

	/**
	 * Constructs a Transaction object so it can be processed
	 *
	 * @param payer payer for this Transaction
	 * @param points points awarded during this Transaction
	 * @param timeStamp date and time information for this Transacion
	 * @return The created Transaction
	 */
	public Transaction(String payer, String points, String timeStamp){
		this.payer = payer;
		this.points = Integer.parseInt(points);
		this.dt = LocalDateTime.parse(timeStamp);	
	}


	/**
	 * Compares two different Transaction objects based on their date and time.
	 * Older Transaction will come first.
	 * 
	 * @param t1 Transaction being compared
	 * @return a negative int if this < t1, 0 if this = t1, a positive int if this > t1
	 */
	@Override
	public int compareTo(Transaction t1){
		return this.dt.compareTo(t1.dt);	
	}
}


/**
 * Represents a thread used for processing rows of data concurently
 */
class MyRunnable implements Runnable{

	private String row;  		// row of data this thread will process
			
	private static int paIdx;	// index of the column where "payer" is located
	private static int poIdx;	// index of the column where "points" is located
	private static int tiIdx;	// index of the column where "timestamp" is located
	
	// queue that each thread adds if the the transaction has a positive points value
	private static PriorityQueue<Transaction> pq = new PriorityQueue<>();

	// stack that each thread adds to if the transaction has negative points value	
	private static Stack<Transaction> stack = new Stack<>(); 

	/**
	 * Create a thread that will process the given row
	 *
	 * @param row a specific row of data in the CSV
	 */
	public MyRunnable(String row){
		super();
		this.row = row;
	}	

	/**
	 * Store the indeces of which columns the relevant data is stored
	 *
	 * @param payerIdx index of where the payer of this Transaction is located	
	 * @param pointsIdx index of where the payer of this Transaction is located	
	 * @param timestampIdx index of where the payer of this Transaction is located
	 */
	public static void setIndeces(int payerIdx, int pointsIdx, int timestampIdx){
		paIdx = payerIdx;			
		poIdx = pointsIdx;			
		tiIdx = timestampIdx;			
	}

	/**
	 * Transform a string representation of a row of data into a Transaction then store it in
	 * the chared PriorityQueue
	 */
	@Override
	public void run(){
		String[] info = MultithreadedAccounting.getInfo(row, paIdx, poIdx, tiIdx);
		Transaction T = new Transaction(info[0], info[1], info[2].substring(0, info[2].length()-1));
		if(T.points >= 0){
			synchronized (pq){
				pq.add(T); // only allow 1 thread at a time to make changes to this
			}
		} else {
			synchronized(stack){
				stack.push(T);	
			}	
		}
	}

	/**
	 * Get the PriotityQueue with the Transaction information
	 *
	 * @return the Priority Queue which contains the transactions with positive points
	 */
	public static PriorityQueue<Transaction> getPQ(){
		return pq;	
	}

	/**
	 * Get the Stack with the negative Transaction information
	 * 
	 * @return the Stack which contains the transactions with negative points
	 */
	public static Stack<Transaction> getStack(){
		return stack;	
	}
}
