/**
 * NAME: PREETI LEKHA
 * COURSE_NUMBER: CS-5323-001
 * ASSIGNMENT_TITLE: SYSTEM (OS-II PROJECT)
 * DATE: APRIL 26 2016
 */
/**
 * SYSTEM MODULE :
 * 		1.SYSTEM will invokes DISK_MANAGER to load the input jobs to DISK
 * 		2.Invokes LOADER to load the disk jobs to memory
 * 		3.Invokes CPU to schedule and execute the jobs on memory for various 
 * 		  combinations of CPU turns and Quantum size
 *
 * GLOBAL VARIABLES:
 * 		progressFileString: string builder to keep tracks of the progress of the SYSTEM
 *  		MLFBQFileString: string builder to keep tracks of contents of the subQueues
 * 		MatrixFileString: string builder for 3*4 matrix of the traffic among sub-queues
 * 		systemCLOCK:  Keeps track of the current clock
 * 		noOfInputJobsRead:  Keeps track of the number of input jobs processed
 * 		PCBList: Keeps track of all the jobs PCB's
 * 		migrationMatrixArray : stores migration status for various combinations 
 * 		of CPU turns and Quantum size 
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SYSTEM {
    public static StringBuilder progressFileString = new StringBuilder();
    public static StringBuilder MLFBQFileString = new StringBuilder();
    public static StringBuilder MatrixFileString = new StringBuilder();
    public static Integer systemCLOCK = 0;
    public static Integer noOfInputJobsRead = 0;
    public static final int PAGE_SIZE = 16;
    public static int[] n = { 3, 4, 5 };
    public static int[] q = { 35, 40, 45, 50 };
    public static Map<Integer, PCB> PCBList = new HashMap<Integer, PCB>();
    private static BufferedReader bufferedReader;
    public static ArrayList<ArrayList<Integer>> migrationMatrixArray = new ArrayList<ArrayList<Integer>>();

    public static void main(String[] args) throws Exception {

	File file = new File("progress.txt");
	if (file.exists()) {
	    file.delete();
	}
	file = new File("MLFBQ.txt");
	if (file.exists()) {
	    file.delete();
	}
	file = new File("MigrationMatrix.txt");
	if (file.exists()) {
	    file.delete();
	}

	// READ INPUT FILE
	String userProgramFile = "";
	if (args.length > 0) {
	    userProgramFile = args[0];
	}
	allJobs = getJobsFromInputFile(userProgramFile);

	for (int row = 0; row < n.length; row++) {
	    ArrayList<Integer> migrations = new ArrayList<Integer>();

	    for (int col = 0; col < q.length; col++) {

		// LOAD JOBS TO DISK
		DISK_MANAGER.loadJobsToDisk();
		DISK_MANAGER.shouldComoputeDiskUtilization = true;
		DISK_MANAGER.computeDiskUtilization();
		CPU.n = n[row];
		CPU.q = q[col];
		CPU.createReadyQueues();

		// LOAD JOBS TO MEMORY
		LOADER.loader();

		// EXECUTE JOBS
		CPU.MLFBQ();
		migrations.add(CPU.numberOfMigration);
		// migrationMatrixArray.add(CPU.numberOfMigration);

		generateBatchReport();
		resetSYSTEM();
	    }
	    migrationMatrixArray.add(row, migrations);
	}
	writeMatrixFile();
    }

    /**
     * This method is responsible for reseting the global variables for the next
     * combinations of CPU turns and Quantum size
     */
    public static void resetSYSTEM() {
	systemCLOCK = 0;
	SYSTEM.noOfInputJobsRead = 0;
	SYSTEM.progressFileString.delete(0, SYSTEM.progressFileString.length());
	PCBList.clear();
	DISK_MANAGER.resetDisk();
	LOADER.resetLoader();
	CPU.resetCPU();
    }

    /**
     * This method is responsible for generating the batch report for tb.txt and
     * tb+err.txt
     */
    public static void generateBatchReport() {

	int totalRuntime = 0;
	int totalIOTime = 0;
	int totalExecutionTime = 0;
	int totalTurnAroundTime = 0;
	int totalPageFaultHandlingTime = 0;
	int cpuIdleTime = 0;
	int totalNumberOfJobs = SYSTEM.PCBList.size();
	int normalJobs = 0;
	int abNormalJobs = 0;
	int timeLostDueToAbNormalJobs = 0;
	int timeLostDueToInfiniteJobs = 0;
	ArrayList<Integer> suspectedInfiniteJobs = new ArrayList<>();
	int totalNoOfPagefaults = 0;

	for (Map.Entry<Integer, PCB> entry : PCBList.entrySet()) {
	    PCB pcb = entry.getValue();
	    totalRuntime += pcb.getRunTime();
	    totalIOTime += pcb.getIOTime();
	    totalExecutionTime += pcb.getExecutionTime();
	    totalTurnAroundTime += pcb.getTurnAroundTime();
	    totalPageFaultHandlingTime += pcb.getPageFaultTime();
	    totalNoOfPagefaults += pcb.getNumberOFPageFaults();

	    if (pcb.getHasError() || pcb.getErrorCode() != null) {
		if (pcb.getErrorCode() == ERROR_HANDLER.Error.eSuspectedInfiniteJob) {
		    timeLostDueToInfiniteJobs += pcb.getTurnAroundTime();
		    suspectedInfiniteJobs.add(pcb.getJobID());
		}
		abNormalJobs++;
		timeLostDueToAbNormalJobs += pcb.getTurnAroundTime();
	    } else {
		normalJobs++;
	    }
	}
	cpuIdleTime = totalIOTime;

	Double averageDiskUtilization = 0.0;
	for (int i = 0; i < DISK_MANAGER.percentageOccupiedOnDisk.size(); i++) {
	    averageDiskUtilization += DISK_MANAGER.percentageOccupiedOnDisk.get(i);
	}
	averageDiskUtilization = (averageDiskUtilization
		/ DISK_MANAGER.percentageOccupiedOnDisk.size());
	DecimalFormat dformat = new DecimalFormat();
	dformat.setMaximumFractionDigits(2);
	String utilization = (dformat.format(averageDiskUtilization));

	int averageNumberOfHoles = (DISK_MANAGER.DISK_SIZE / 16);
	SYSTEM.progressFileString.append("\nCURRENT_CLOCK_TIME(DECIMAL): " + SYSTEM.systemCLOCK);
	SYSTEM.progressFileString
		.append("\nMEAN_JOB_RUN_TIME(DECIMAL): " + (totalRuntime / totalNumberOfJobs));
	SYSTEM.progressFileString
		.append("\nMEAN_JOB_IO_TIME(DECIMAL): " + (totalIOTime / totalNumberOfJobs));
	SYSTEM.progressFileString.append(
		"\nMEAN_JOB_EXECUTION_TIME(DECIMAL): " + (totalExecutionTime / totalNumberOfJobs));
	SYSTEM.progressFileString.append("\nMEAN_JOB_TURNAROUND_TIME(DECIMAL): "
		+ (totalTurnAroundTime / totalNumberOfJobs));
	SYSTEM.progressFileString.append("\nMEAN_JOB_PAGEFAULT_HANDLING_TIME(DECIMAL): "
		+ (totalPageFaultHandlingTime / totalNumberOfJobs));
	SYSTEM.progressFileString.append("\nTOTAL_CPU_IDLE_TIME(DECIMAL): " + cpuIdleTime);
	SYSTEM.progressFileString.append("\nTIME_LOST_DUE_TO_ABNORMALLY_TERMINATED_JOB(DECIMAL): "
		+ timeLostDueToAbNormalJobs);
	SYSTEM.progressFileString
		.append("\nNUMBER_OF_JOBS_TERMINATED_NORMALLY(DECIMAL): " + normalJobs);
	SYSTEM.progressFileString
		.append("\nNUMBER_OF_JOBS_TERMINATED_ABNORMALLY(DECIMAL): " + abNormalJobs);
	SYSTEM.progressFileString
		.append("\nTOTAL_TIME_LOST_DUE_TO_SUSPECTED_INFINITE_JOBS(DECIMAL): "
			+ timeLostDueToInfiniteJobs);
	SYSTEM.progressFileString.append(
		"\nSUSPECTED_INFINITE_JOBS(JOB_ID)(DECIMAL): " + suspectedInfiniteJobs.toString());
	SYSTEM.progressFileString
		.append("\nTOTAL_NUMBER_OF_PAGEFAULT(DECIMAL): " + totalNoOfPagefaults);
	SYSTEM.progressFileString.append("\nAVERAGE_DISK_UTILIZATION(%)(DECIMAL): " + utilization);
	SYSTEM.progressFileString
		.append("\nNUMBER_OF_HOLES_ON_DISK(DECIMAL): " + averageNumberOfHoles);
	SYSTEM.progressFileString.append("\nSIZE_OF_HOLES_ON_DISK(DECIMAL): " + SYSTEM.PAGE_SIZE);

	int n = 1;
	while (n <= CPU.ready_queues.size()) {
	    Queue queue = CPU.ready_queues.get(n);

	    Integer maxQueueSize = 0;
	    for (int i = 0; i < queue.getQueueSizes().size(); i++) {
		if (queue.getQueueSizes().get(i) > maxQueueSize) {
		    maxQueueSize = queue.getQueueSizes().get(i);
		}
	    }

	    SYSTEM.progressFileString
		    .append("\nMAX_NUMBER_OF_JOBS_IN_SUBQUEUE" + n + "(DECIMAL): " + maxQueueSize);

	    Double queueSize = 0.0;
	    for (int i = 0; i < queue.getQueueSizes().size(); i++) {
		queueSize += queue.getQueueSizes().get(i);
	    }

	    Double averageSubQueueSize = 0.00;
	    Double totalSize = (double) queue.getQueueSizes().size();
	    if (totalSize > 0) {
		averageSubQueueSize = (queueSize / totalSize);
	    }

	    String averageSubQueueSizeStr = (dformat.format(averageSubQueueSize));
	    SYSTEM.progressFileString.append(
		    "\nAVERAGE_SIZE_OF_SUBQUEUE" + n + "(DECIMAL): " + averageSubQueueSizeStr);
	    n++;
	}

	SYSTEM.writeProgressFile();
	SYSTEM.progressFileString.delete(0, SYSTEM.progressFileString.length());
    }

    /**
     * This method is write the system progress to progress.txt file
     */
    public static void writeProgressFile() {
	try {
	    FileWriter fw = new FileWriter("progress.txt", true);
	    BufferedWriter bw = new BufferedWriter(fw);
	    bw.write(SYSTEM.progressFileString.toString());
	    int length = SYSTEM.progressFileString.length();
	    SYSTEM.progressFileString.delete(0, length);
	    bw.flush();
	    bw.close();

	} catch (IOException e) {
	}
    }

    /**
     * This method is write the content of the four sub-queues to MLFBQ.txt file
     */
    public static void writeMLFBQFile() {
	try {
	    FileWriter fw = new FileWriter("MLFBQ.txt", true);
	    BufferedWriter bw = new BufferedWriter(fw);
	    bw.write(SYSTEM.MLFBQFileString.toString());
	    int length = SYSTEM.MLFBQFileString.length();
	    SYSTEM.MLFBQFileString.delete(0, length);
	    bw.flush();
	    bw.close();

	} catch (IOException e) {
	}
    }

    /**
     * This method is write the traffic among four sub-queues to
     * MigrationMatrix.txt file
     */
    public static void writeMatrixFile() {
	MatrixFileString.append(String.format("\n %-5s %-7s %-7s %-7s %-7s", "", "q(35)", "q(40)",
		"q(45)", "q(50)"));
	MatrixFileString.append(String.format("\n %-5s %-7s %-7s %-7s %-7s", "", "-----", "-----",
		"-----", "-----"));
	for (int row = 0; row < n.length; row++) {
	    MatrixFileString.append(String.format("\n n(%-1s) | ", n[row]));
	    for (int col = 0; col < q.length; col++) {
		MatrixFileString
			.append(String.format("%-7s ", migrationMatrixArray.get(row).get(col)));
	    }
	}

	try {
	    FileWriter fw = new FileWriter("MigrationMatrix.txt");
	    BufferedWriter bw = new BufferedWriter(fw);
	    bw.write(SYSTEM.MatrixFileString.toString());
	    int length = SYSTEM.MatrixFileString.length();
	    SYSTEM.MatrixFileString.delete(0, length);
	    bw.flush();
	    bw.close();

	} catch (IOException e) {
	}
    }

    /**
     * This method is writes the Trace generated for the jobs to TRACE.txt file
     */
    public static void writeTraceFileForJob(PCB job) {
	try {
	    String filename = String.valueOf(job.getJobID()) + "TRACE.txt";
	    File file = new File(filename);
	    if (file.exists()) {
		file.delete();
	    }
	    FileWriter fileWriter = new FileWriter(file);
	    BufferedWriter bw = new BufferedWriter(fileWriter);
	    bw.write(job.getTraceFileString().toString());
	    bw.close();
	} catch (IOException e) {
	}
    }

    public static ArrayList<JOB> allJobs = new ArrayList<>();

    public static ArrayList<JOB> getJobsFromInputFile(String filename)
	    throws FileNotFoundException, IOException {
	ArrayList<JOB> arrayOfJobs = new ArrayList<>();

	try {
	    bufferedReader = new BufferedReader(new FileReader(filename));
	    int jobCount = 0;
	    boolean status = true;

	    // start reading jobs from file
	    boolean isExpectingNewJob = false;
	    String strLine = bufferedReader.readLine();
	    while (strLine != null && strLine.length() != 0) {

		if (isExpectingNewJob) {
		    strLine = bufferedReader.readLine();
		    if (strLine == null) {
			break;
		    }
		}

		String[] line = strLine.split(" ");
		if (isExpectingNewJob && !(line[0].equals("**JOB"))) {
		    status = false;
		    while ((strLine = bufferedReader.readLine()) != null) {
			line = strLine.split(" ");
			if (line[0].equals("**JOB")) {
			    status = true;
			    break;
			}
		    }
		}

		if (status == false) {
		    break;
		}

		JOB job = new JOB();
		try {
		    jobCount++;
		    job.jobID = jobCount;

		    if (line[0].equals("**JOB") && line[1].length() == 2 && line[2].length() == 2) {
			// proceed
			job.xx = Integer.parseInt(line[1], 16);
			job.yy = Integer.parseInt(line[2], 16);

			// <loader format> read first record
			strLine = bufferedReader.readLine();
			line = strLine.split(" ");
			if (line[0].equals("**JOB") && line[1].length() == 2
				&& line[2].length() == 2) {

			    job.hasLoadTimeError = true;
			    job.errorCode = ERROR_HANDLER.Error.eNullJob;
			    arrayOfJobs.add(job);
			    // proceed
			    job = new JOB();
			    jobCount++;
			    job.jobID = jobCount;
			    job.xx = Integer.parseInt(line[1], 16);
			    job.yy = Integer.parseInt(line[2], 16);
			    // <loader format> read first record
			    strLine = bufferedReader.readLine();
			    line = strLine.split(" ");
			}

			if (line[0].length() <= 0 || line[1].length() > 2 || line[1].length() <= 0
				|| line[1].length() > 2) {
			    job.hasLoadTimeError = true;
			    job.errorCode = ERROR_HANDLER.Error.eInvalidLoaderFormatCharacter;
			    arrayOfJobs.add(job);
			    isExpectingNewJob = true;
			    continue;
			}
			job.loadAddress = Integer.parseInt(line[0], 16);
			job.userJobLength = Integer.parseInt(line[1], 16);

			if (job.userJobLength > MEMORY.MEMORY_SIZE) {
			    job.hasLoadTimeError = true;
			    job.errorCode = ERROR_HANDLER.Error.eProgramSizeTooLarge;
			    arrayOfJobs.add(job);
			    isExpectingNewJob = true;
			    continue;
			}

			// <loader format> read program record
			boolean readProgramStatus = true;
			boolean hasDATATag = false;
			boolean expectingDATATag = false;
			boolean hasFINTag = false;
			boolean expectingFINTag = false;
			int numberOfWordsLoaded = 0;

			while (readProgramStatus) {
			    strLine = bufferedReader.readLine();
			    if (strLine.length() == 0) {
				if (expectingDATATag && !hasDATATag) {
				    job.hasLoadTimeError = true;
				    job.errorCode = ERROR_HANDLER.Error.eMissingDATATag;
				    readProgramStatus = false;
				} else if (expectingFINTag && !hasFINTag) {
				    job.hasLoadTimeWarning = true;
				    job.warningCode = ERROR_HANDLER.Warning.eMissingFINTag;
				    readProgramStatus = false;
				}
				break;
			    }
			    line = strLine.split(" ");

			    if (expectingDATATag) {
				if (!(line[0].equals("**DATA"))) {
				    job.hasLoadTimeError = true;
				    job.errorCode = ERROR_HANDLER.Error.eMissingDATATag;
				    readProgramStatus = false;
				}
				hasDATATag = true;
				expectingDATATag = false;
				expectingFINTag = true;
				continue;
			    } else if (hasDATATag && line[0].equals("**DATA")) {
				job.hasLoadTimeWarning = true;
				job.warningCode = ERROR_HANDLER.Warning.eDoubleDATATag;
				readProgramStatus = false;
				continue;
			    } else if (hasDATATag && strLine.length() % 8 == 0
				    && strLine.length() <= 32) {
				int n = 0;
				while (n < strLine.length()) {
				    job.data.add(strLine.substring(n, n + 8));
				    n = n + 8;
				}
				continue;
			    } else if (hasDATATag && strLine.length() % 8 != 0 && expectingFINTag) {
				if (!(strLine.equals("**FIN"))) {
				    job.hasLoadTimeWarning = true;
				    job.warningCode = ERROR_HANDLER.Warning.eMissingFINTag;
				    arrayOfJobs.add(job);
				    if (strLine.contains("**JOB")) {
					line = strLine.split(" ");
					if (line[0].equals("**JOB") && line[1].length() == 2
						&& line[2].length() == 2) {

					    // proceed
					    job = new JOB();
					    jobCount++;
					    job.jobID = jobCount;
					    job.xx = Integer.parseInt(line[1], 16);
					    job.yy = Integer.parseInt(line[2], 16);

					    // <loader format> read first record
					    strLine = bufferedReader.readLine();
					    line = strLine.split(" ");
					    if (line[0].length() <= 0 || line[1].length() > 2
						    || line[1].length() <= 0
						    || line[1].length() > 2) {
						job.hasLoadTimeError = true;
						job.errorCode = ERROR_HANDLER.Error.eInvalidLoaderFormatCharacter;
						arrayOfJobs.add(job);
						readProgramStatus = false;
						isExpectingNewJob = true;
						continue;
					    }
					    job.loadAddress = Integer.parseInt(line[0], 16);
					    job.userJobLength = Integer.parseInt(line[1], 16);

					    if (job.userJobLength > MEMORY.MEMORY_SIZE) {
						job.hasLoadTimeError = true;
						job.errorCode = ERROR_HANDLER.Error.eProgramSizeTooLarge;
						arrayOfJobs.add(job);
						readProgramStatus = false;
						isExpectingNewJob = true;
						continue;
					    }

					    readProgramStatus = true;
					    hasDATATag = false;
					    expectingDATATag = false;
					    hasFINTag = false;
					    expectingFINTag = false;
					    numberOfWordsLoaded = 0;
					    continue;
					}
				    }

				} else {
				    expectingFINTag = false;
				    hasFINTag = true;
				    break;
				}
			    } else if (hasDATATag == false && strLine.length() % 8 == 0
				    && strLine.length() <= 32) {
				int n = 0;
				while (n < strLine.length()) {
				    job.instructions.add(strLine.substring(n, n + 8));
				    n = n + 8;
				}
				numberOfWordsLoaded += n;
			    } else if (numberOfWordsLoaded == 0 && strLine.length() % 8 != 0) {
				job.hasLoadTimeError = true;
				job.errorCode = ERROR_HANDLER.Error.eMissingProgram;
				readProgramStatus = false;
			    } else if (numberOfWordsLoaded > 0
				    && (strLine.length() > 0 && strLine.length() <= 4)) {
				// <loader format> read last record
				line = strLine.split(" ");
				if (line[0].length() != 2) {
				    job.hasLoadTimeError = true;
				    job.errorCode = ERROR_HANDLER.Error.eInvalidLoaderFormatCharacter;
				    readProgramStatus = false;
				}
				if (!(line[1].equals("1") || line[1].equals("0"))
					|| (line[1].length() != 1)) {
				    job.hasLoadTimeWarning = true;
				    job.warningCode = ERROR_HANDLER.Warning.eInvalidTraceSwitch;
				    job.traceSwitch = false;
				} else {
				    Integer switchFlag = Integer.parseInt(line[1], 16);
				    job.traceSwitch = (switchFlag == 1) ? true : false;
				}
				job.startAddress = Integer.parseInt(line[0], 16);
				expectingDATATag = true;
			    }
			}

		    } else {
			job.hasLoadTimeError = true;
			if (!(line[0].equals("**JOB"))) {
			    job.errorCode = ERROR_HANDLER.Error.eMissingJOBTag;
			} else if (line[1].length() != 2 || line[2].length() != 2) {
			    job.errorCode = ERROR_HANDLER.Error.eInvalidLoaderFormatCharacter;
			} else {
			    job.errorCode = ERROR_HANDLER.Error.eBadCharacterEncounteredByLoader;
			}
		    }
		} catch (IllegalArgumentException c) {
		    job.hasLoadTimeError = true;
		    job.errorCode = ERROR_HANDLER.Error.eInvalidLoaderFormatCharacter;
		} catch (Exception c) {
		}
		arrayOfJobs.add(job);
		isExpectingNewJob = true;
	    }
	    bufferedReader.close();
	} catch (Exception e) {
	}
	return arrayOfJobs;
    }
}

class PCB {
    private Integer jobID;
    private Integer totalNoOfPages;
    private Integer noOfPgmPages;
    private Integer noOfDataPages;
    private Integer noOfOutputLinePages;
    private Integer framesNeeded;
    private Integer outputLineLimit;
    private Integer loadAddress;
    private Integer userJobLength;
    private Integer startAddress;
    private boolean traceSwitch;
    private boolean hasError;
    private boolean hasWarning;
    private ERROR_HANDLER.Error errorCode;
    private ERROR_HANDLER.Warning warningCode;
    private Integer PC;
    private String[] REG;
    private ArrayList<PageEntry> pageTable;
    private boolean isTerminated;

    private boolean isBlocked;
    private Integer blockedQueueRevivalTime;
    private Integer currentQueue;
    private Integer jobAge;
    private Integer cpuShots;
    public Integer currentTurn;

    // time the job was added to ready queue or loaded to memory
    private int arrivalTime;
    // time the job was starts executing for the first time
    private int startTime;
    // time the job was terminated
    private int terminationTime;
    // time from start to termination (terminationTime-arrivalTime)
    private int turnAroundTime;
    // time spent by process being active
    private int burstTime;
    // time spent in ready queue
    private int runTime;
    // time spent in blocked queue
    private int IOTime;
    private int executionTime;
    private int pageFaultTime;

    private int timeOfCompletionOfCurrentIOoperation = -1;
    private int addressOfCurrentRDdataRecord = -1;
    private int addressOfCurrentWRdataRecord = -1;
    private Integer currentRDdataPageNo = -1;
    private Integer currentWRdataPageNo = -1;
    private int numberOFPageFaults = 0;
    private int numberOfIO = 0;
    private ArrayList<String> outputBuffer;
    private StringBuilder traceFileString;

    public PCB() {
	jobID = 0;
	totalNoOfPages = 0;
	noOfPgmPages = 0;
	noOfDataPages = 0;
	noOfOutputLinePages = 0;
	framesNeeded = 0;
	outputLineLimit = 0;
	loadAddress = 0;
	userJobLength = 0;
	startAddress = 0;
	traceSwitch = false;
	hasError = false;
	hasWarning = false;
	PC = 0;
	currentTurn = 0;
	pageTable = new ArrayList<>();
	isTerminated = false;
	isBlocked = false;
	blockedQueueRevivalTime = -1;
	jobAge = 0;
	cpuShots = 0;
	arrivalTime = -1;
	startTime = -1;
	terminationTime = -1;
	burstTime = -1;
	turnAroundTime = -1;
	runTime = -1;
	executionTime = -1;
	IOTime = -1;
	pageFaultTime = -1;
	timeOfCompletionOfCurrentIOoperation = -1;
	addressOfCurrentRDdataRecord = -1;
	addressOfCurrentWRdataRecord = -1;
	currentRDdataPageNo = -1;
	currentWRdataPageNo = -1;
	numberOFPageFaults = 0;
	numberOfIO = 0;
	outputBuffer = new ArrayList<>();
	traceFileString = new StringBuilder();
	REG = new String[16];
	for (int i = 0; i < REG.length; i++) {
	    REG[i] = "0";
	}
    }

    public void setCurrentTurn(Integer x) {
	currentTurn = x;
    }

    public Integer getCurrentTurn() {
	return currentTurn;
    }

    public void incrementCurrentTurn() {
	currentTurn += 1;
    }

    public void incrementCpuShots(Integer x) {
	cpuShots += x;
    }

    public Integer getCpuShots() {
	return cpuShots;
    }

    public void incrementJobAge(Integer x) {
	jobAge += x;
    }

    public Integer getJobAge() {
	return jobAge;
    }

    public void setCurrentQueue(Integer x) {
	currentQueue = x;
    }

    public Integer getCurrentQueue() {
	return currentQueue;
    }

    public void setCurrentRDdataPageNo(Integer x) {
	currentRDdataPageNo = x;
    }

    public Integer getCurrentRDdataPageNo() {
	return currentRDdataPageNo;
    }

    public void setCurrentWRdataPageNo(Integer x) {
	currentWRdataPageNo = x;
    }

    public Integer getCurrentWRdataPageNo() {
	return currentWRdataPageNo;
    }

    public Integer getJobID() {
	return jobID;
    }

    public void setJobID(Integer x) {
	jobID = x;
    }

    public Integer getTotalNoOfPages() {
	return totalNoOfPages;
    }

    public void setTotalNoOfPages(Integer x) {
	totalNoOfPages = x;
    }

    public Integer getNoOfPgmPages() {
	return noOfPgmPages;
    }

    public void setNoOfPgmPages(Integer x) {
	noOfPgmPages = x;
    }

    public Integer getNoOfDataPages() {
	return noOfDataPages;
    }

    public void setNoOfDataPages(Integer x) {
	noOfDataPages = x;
    }

    public Integer getNoOfOutputLinePages() {
	return noOfOutputLinePages;
    }

    public void setNoOfOutputLinePages(Integer x) {
	noOfOutputLinePages = x;
    }

    public Integer getFramesNeeded() {
	return framesNeeded;
    }

    public void setFramesNeeded(Integer x) {
	framesNeeded = x;
    }

    public Integer getOutputLineLimit() {
	return outputLineLimit;
    }

    public void setOutputLineLimit(Integer x) {
	outputLineLimit = x;
    }

    public Integer getLoadAddress() {
	return loadAddress;
    }

    public void setLoadAddress(Integer x) {
	loadAddress = x;
    }

    public Integer getUserJobLength() {
	return userJobLength;
    }

    public void setUserJobLength(Integer x) {
	userJobLength = x;
    }

    public Integer getStartAddress() {
	return startAddress;
    }

    public void setStartAddress(Integer x) {
	startAddress = x;
    }

    public boolean getTraceSwitch() {
	return traceSwitch;
    }

    public void setTraceSwitch(boolean x) {
	traceSwitch = x;
    }

    public boolean getHasError() {
	return hasError;
    }

    public void setHasError(boolean x) {
	hasError = x;
    }

    public boolean getHasWarning() {
	return hasWarning;
    }

    public void setHasWarning(boolean x) {
	hasWarning = x;
    }

    public ERROR_HANDLER.Warning getWarningCode() {
	return warningCode;
    }

    public void setWarningCode(ERROR_HANDLER.Warning x) {
	warningCode = x;
    }

    public ERROR_HANDLER.Error getErrorCode() {
	return errorCode;
    }

    public void setErrorCode(ERROR_HANDLER.Error x) {
	errorCode = x;
    }

    public Integer getPC() {
	return PC;
    }

    public void setPC(Integer x) {
	PC = x;
    }

    public String[] getREG() {
	return REG;
    }

    public void setREG(String[] x) {
	REG = x.clone();
    }

    public ArrayList<PageEntry> getPageTable() {
	return pageTable;
    }

    public void setPageTable(ArrayList<PageEntry> x) {
	pageTable = x;
    }

    public boolean getIsTerminated() {
	return isTerminated;
    }

    public void setIsTerminated(boolean x) {
	isTerminated = x;
    }

    public boolean getIsBlocked() {
	return isBlocked;
    }

    public void setIsBlocked(boolean x) {
	isBlocked = x;
    }

    public Integer getBlockedQueueRevivalTime() {
	return blockedQueueRevivalTime;
    }

    public void setBlockedQueueRevivalTime(Integer x) {
	blockedQueueRevivalTime = x;
    }

    public int getArrivalTime() {
	return arrivalTime;
    }

    public void setArrivalTime(int x) {
	arrivalTime = x;
    }

    public int getStartTime() {
	return startTime;
    }

    public void setStartTime(int x) {
	startTime = x;
    }

    public int getTerminationTime() {
	return terminationTime;
    }

    public void setTerminationTime(int x) {
	terminationTime = x;
    }

    public int getTurnAroundTime() {
	return turnAroundTime;
    }

    public void setTurnAroundTime(int x) {
	turnAroundTime = x;
    }

    public int getBurstTime() {
	return burstTime;
    }

    public void incrementBurstTimeBy(int x) {
	burstTime += x;
    }

    public int getRunTime() {
	return runTime;
    }

    public void setRunTime(int x) {
	runTime = x;
    }

    public int getExecutionTime() {
	return executionTime;
    }

    public void setExecutionTime(int x) {
	executionTime = x;
    }

    public int getPageFaultTime() {
	return pageFaultTime;
    }

    public void setPageFaultTime(int x) {
	pageFaultTime = x;
    }

    public int getIOTime() {
	return IOTime;
    }

    public void setIOTime(int x) {
	IOTime = x;
    }

    public int getTimeOfCompletionOfCurrentIOoperation() {
	return timeOfCompletionOfCurrentIOoperation;
    }

    public void setTimeOfCompletionOfCurrentIOoperation(int x) {
	timeOfCompletionOfCurrentIOoperation = x;
    }

    public Integer getAddressOfCurrentRDdataRecord() {
	return addressOfCurrentRDdataRecord;
    }

    public void setddressOfCurrentRDdataRecord(Integer x) {
	addressOfCurrentRDdataRecord = x;
    }

    public Integer getAddressOfCurrentWRdataRecord() {
	return addressOfCurrentWRdataRecord;
    }

    public void setAddressOfCurrentWRdataRecord(Integer x) {
	addressOfCurrentWRdataRecord = x;
    }

    public int getNumberOFPageFaults() {
	return numberOFPageFaults;
    }

    public void incrementNumberOFPageFaultsBy(int x) {
	numberOFPageFaults += x;
    }

    public int getNumberOfIO() {
	return numberOfIO;
    }

    public void incrementNumberOfIOBy(int x) {
	numberOfIO += x;
    }

    public ArrayList<String> getOutputBuffer() {
	return outputBuffer;
    }

    public void setOutputBuffer(ArrayList<String> x) {
	outputBuffer = x;
    }

    public StringBuilder getTraceFileString() {
	return traceFileString;
    }

    public void setTraceFileString(StringBuilder x) {
	traceFileString = x;
    }

    public void clearPCB() {
	traceFileString.delete(0, traceFileString.length());
	REG = null;
	pageTable.clear();
    }
}

class PageEntry {
    public Integer isProgram = 0;
    public boolean isInMemory = false;
    public boolean isReferenced = false;
    public boolean isWritten = false;
    public Integer pageNumberOnDisk = -1;
    public Integer pageNumberOnMemory = -1;
    public Integer pageLoadTime = -1;

    public PageEntry(int program, boolean inMemory, boolean referenced, boolean written,
	    Integer availablePageOnDisk, int pageNoOnMemory) {
	isProgram = program;
	isInMemory = inMemory;
	isReferenced = referenced;
	isWritten = written;
	pageNumberOnDisk = availablePageOnDisk;
	pageNumberOnMemory = pageNoOnMemory;
    }
}

class JOB {
    public int xx;
    public int yy;
    public Integer jobID = 0;
    public Integer loadAddress = 0;
    public Integer userJobLength = 0;
    public Integer startAddress = 0;
    public Boolean traceSwitch = false;
    public boolean hasLoadTimeError = false;
    public boolean hasLoadTimeWarning = false;
    public ERROR_HANDLER.Error errorCode;
    public ERROR_HANDLER.Warning warningCode;
    public ArrayList<String> instructions = new ArrayList<>();
    public ArrayList<String> data = new ArrayList<>();

    public void clearJob() {
	xx = 0;
	yy = 0;
	jobID = 0;
	loadAddress = 0;
	userJobLength = 0;
	startAddress = 0;
	traceSwitch = false;
	hasLoadTimeError = false;
	hasLoadTimeWarning = false;
	errorCode = null;
	warningCode = null;
	instructions.clear();
	data.clear();
    }
}

/**
 * UTILITIES MODULE: Utilities contain common methods/functions used in other
 * module
 */

class Utilities {

    /*
     * Integer constant NEGATIVE_VALUE_BINARY_SIZE represents the size negative
     * value returns when converted
     */
    public static final int NEGATIVE_VALUE_BINARY_SIZE = 32;

    public static int hex2decimal(String s) {
	String digits = "0123456789ABCDEF";
	s = s.toUpperCase();
	int val = 0;
	for (int i = 0; i < s.length(); i++) {
	    char c = s.charAt(i);
	    int d = digits.indexOf(c);
	    val = 16 * val + d;
	}
	return val;
    }

    public static String decimal2hex(int d, int returnValueLength) {

	if (returnValueLength <= 0) {
	    returnValueLength = 8;
	}

	String digits = "0123456789ABCDEF";
	if (d == 0)
	    return "0";
	String hex = "";
	while (d > 0) {
	    int digit = d % 16; // rightmost digit
	    hex = digits.charAt(digit) + hex; // string concatenation
	    d = d / 16;
	}

	char strArray[] = hex.toCharArray();
	char returnArray[] = new char[returnValueLength];
	if (strArray.length != returnValueLength) {
	    int i;
	    for (i = 0; i < returnValueLength - strArray.length; i++) {
		returnArray[i] = '0';
	    }
	    for (char ch : strArray) {
		returnArray[i] = ch;
		i++;
	    }
	} else {
	    returnArray = strArray;
	}
	return new String(returnArray).toUpperCase();
    }

    /*
     * This method is used to convert a binary string to decimal value
     *
     * @param binary
     * 
     * @return (int) decimal
     */
    public static int binary2Decimal(String binary) {
	double decimal = 0;
	if (binary.equals("-1")) {
	    decimal = -1;
	} else {
	    for (int i = 0; i < binary.length(); i++) {
		if (binary.charAt(i) == '1') {
		    decimal = decimal + Math.pow(2, binary.length() - 1 - i);
		}
	    }
	}

	/*
	 * Check: whether the given binary string value is negative or not. If
	 * negative, converts to negative
	 */
	if (binary.length() == 32 && binary.substring(0, 1).equals("1"))
	    decimal = decimal - 65536;

	return (int) decimal;
    }

    /*
     * This method is used to convert decimal value to binary string
     *
     * @param decimalValue
     * 
     * @return new String(returnArray)
     */
    public static String decimal2Binary(int decimalValue, Integer returnBits) throws Exception {

	String str = Integer.toBinaryString(decimalValue);
	char strArray[] = str.toCharArray();
	Integer returnArraySize = (returnBits == 0) ? CPU.REGISTER_SIZE_IN_BITS : returnBits;
	char returnArray[] = new char[CPU.REGISTER_SIZE_IN_BITS];

	/*
	 * Below if else condition will convert a decimal value to binary string
	 * if condition will check whether @param decimalValue is negative
	 * number or not and return appropriate binary string. else condition
	 * will be appending zeroes to the number returned by
	 * Integer.toBinaryString(int) so that the binary string will be 16 bits
	 */
	try {
	    if (NEGATIVE_VALUE_BINARY_SIZE == strArray.length) {
		String resizeString = str.substring(0, 32);
		returnArray = resizeString.toCharArray();
	    } else {
		if (returnArraySize != strArray.length) {
		    int i;
		    for (i = 0; i < returnArraySize - strArray.length; i++) {
			returnArray[i] = '0';
		    }
		    for (char ch : strArray) {
			returnArray[i] = ch;
			i++;
		    }
		} else {
		    returnArray = strArray;
		}
	    }
	} catch (ArrayIndexOutOfBoundsException e) {
	    return "-1";
	}
	return new String(returnArray).toUpperCase();
    }

    /*
     * This method is used to convert hex value to binary value
     * 
     * @param hexValue
     * 
     * @return new String(returnArray)
     */
    public static String hex2Binary(String hexValue) {

	try {
	    Long j = Long.parseLong(hexValue, 16);
	    String str = Long.toBinaryString(j);
	    char strArray[] = str.toCharArray();
	    char returnArray[] = new char[CPU.REGISTER_SIZE_IN_BITS];

	    /*
	     * Below if condition will check whether the method
	     * Integer.toBinaryString(int) is returning 16 bit or not. If not it
	     * will be append zeroes to match the size of register.
	     */

	    if (strArray.length != CPU.REGISTER_SIZE_IN_BITS) {
		int i;
		for (i = 0; i < CPU.REGISTER_SIZE_IN_BITS - strArray.length; i++) {
		    returnArray[i] = '0';
		}
		for (char ch : strArray) {
		    returnArray[i] = ch;
		    i++;
		}
	    } else {
		returnArray = strArray;
	    }
	    return new String(returnArray);

	} catch (Exception e) {
	    return "-1";
	}
    }

    /*
     * This method is used to convert binary value to hex value
     * 
     * @param binaryValue
     * 
     * @return new String(returnArray)
     */
    public static String binary2Hex(String binaryValue, int returnValueLength) throws Exception {

	if (returnValueLength <= 0) {
	    returnValueLength = 8;
	}

	String str = Long.toHexString(Long.parseLong(binaryValue, 2));
	char strArray[] = str.toCharArray();
	char returnArray[] = new char[returnValueLength];

	/*
	 * append zeroes to hex string to match the size of return value
	 * expected.
	 */
	if (strArray.length != returnValueLength) {
	    int i;
	    for (i = 0; i < returnValueLength - strArray.length; i++) {
		returnArray[i] = '0';
	    }
	    for (char ch : strArray) {
		returnArray[i] = ch;
		i++;
	    }
	} else {
	    returnArray = strArray;
	}

	return new String(returnArray).toUpperCase();
    }
}
