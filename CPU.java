
/**
 * CPU MODULE :
 * 		CPU module schedule the jobs on memory by maintaining multi-level-feedback-subqueue, 
 * 		blocked queue and active job status.
 * 		CPU executes the job which involves fetch, decode and execute the instructions of the job.
 * 		The CPU subsystem loops indefinitely until all the input jobs are processed and terminated
 */
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class CPU {

    public static final int NUMBER_OF_REGISTERS = 16;
    public static final int REGISTER_SIZE_IN_BITS = 32;
    public static final int ONE_CLOCK_UNIT = 1;
    public static final int TWO_CLOCK_UNIT = 2;
    public static final int CLOCK_ON_IO = 8;
    public static final int CLOCK_ON_PF = 5;
    public static final int INTERVAL_FOR_SYSTEM_STATUS = 500;
    public static final String READ = "READ";
    public static final String WRIT = "WRIT";
    public static final String DUMP = "DUMP";
    static int previousStatusOutPutTime = 0;
    static int previousQueueSampleTime = 0;
    private static String[] REG = new String[NUMBER_OF_REGISTERS];
    static String IR = "";// Instruction register
    static Boolean isInDirectAddressing = false;
    static opCode opcode = opCode.INVALID_opCode;
    static Integer accumulator = 0;
    static Integer indexReg = 0;
    static Integer DADDR = 0;
    static Integer EA = 0;
    static Integer realEA = 0;
    static Integer programCounter = 0;
    static Integer realPC = 0;
    public static boolean HLT = false;
    public static boolean FATAL_ERROR = false;
    public static boolean IS_RD_WR = false;
    static int quantum = 0;
    static int turn = 0;
    public static PCB activeJOB = null;
    public static Queue activeQueue = null;
    public static int n;
    public static int q;
    public static Integer numberOfMigration = 0;
    public static Map<Integer, Queue> ready_queues;
    public static ArrayList<Integer> blockedQueue = new ArrayList<Integer>();

    static {
	for (int i = 0; i < REG.length; i++) {
	    REG[i] = "0";
	}
    }

    public static void createReadyQueues() {
	Map<Integer, Queue> readyQueue = new HashMap<Integer, Queue>();
	Queue q1 = new Queue(1, n, (n * q));
	readyQueue.put(q1.getQueueID(), q1);

	Queue q2 = new Queue(2, (n + 2), ((n + 2) * q));
	readyQueue.put(q2.getQueueID(), q2);

	Queue q3 = new Queue(3, (n + 4), ((n + 4) * q));
	readyQueue.put(q3.getQueueID(), q3);

	Queue q4 = new Queue(4, 0, ((n + 6) * q));
	readyQueue.put(q4.getQueueID(), q4);

	ready_queues = readyQueue;
    }

    public static void MLFBQ() {
	while (LOADER.jobsOnMemory.size() > 0 && activeJOB == null) {

	    resetVariables();
	    boolean foundJob = getNextJobFromReadyQueue();
	    if (foundJob == false) {
		SYSTEM.systemCLOCK++;
		checkSystemOnClockIncrement();
		continue;
	    }

	    if (activeJOB.getStartTime() == -1) {
		activeJOB.setStartTime(SYSTEM.systemCLOCK);
		activeJOB.setPC(activeJOB.getStartAddress());
	    }
	    if (activeJOB.getREG() != null) {
		REG = activeJOB.getREG();
	    }
	    while (quantum < activeQueue.getQUANTUM()) {

		HLT = FATAL_ERROR = IS_RD_WR = false;
		checkSystemOnClockIncrement();
		cpu(activeJOB.getPC(), activeJOB.getTraceSwitch());

		activeJOB.setPC(programCounter);
		if (REG != null) {
		    activeJOB.setREG(REG);
		}
		SYSTEM.PCBList.replace(activeJOB.getJobID(), activeJOB);
		if (FATAL_ERROR || HLT || IS_RD_WR) {
		    break;
		}
	    }
	    if (FATAL_ERROR || HLT) {
		activeJOB.setIsTerminated(true);
		activeJOB.setTerminationTime(SYSTEM.systemCLOCK);
		SYSTEM.PCBList.replace(activeJOB.getJobID(), activeJOB);
		SPOOL(activeJOB.getJobID());
		activeJOB = null;
	    } else if (IS_RD_WR) {
		// send to blocked queue/reset the turn for the current queue/

		handleIO();
	    } else {
		// move to next queue
		handleQuantumExpire();
	    }
	}
    }

    public static void handleQuantumExpire() {

	Integer currentQueue = activeJOB.getCurrentQueue();
	Integer newQueueNumber = activeJOB.getCurrentQueue();

	if (activeJOB.getCurrentTurn() >= ready_queues.get(currentQueue).getTURN()) {
	    if (currentQueue == 1) {
		newQueueNumber = 2;
		numberOfMigration++;

	    } else if (currentQueue == 2) {
		newQueueNumber = 3;
		numberOfMigration++;

	    } else if (currentQueue == 3) {
		newQueueNumber = 4;
		numberOfMigration++;

	    } else if (currentQueue == 4) {
		newQueueNumber = 4;
	    }
	    activeJOB.setCurrentTurn(0);
	}
	activeJOB.setCurrentQueue(newQueueNumber);
	SYSTEM.PCBList.replace(activeJOB.getJobID(), activeJOB);

	Queue q = ready_queues.get(newQueueNumber);
	q.getJobs().addLast(activeJOB.getJobID());
	ready_queues.replace(q.getQueueID(), q);
	activeJOB = null;
    }

    public static void handleIO() {
	blockedQueue.add(activeJOB.getJobID());
	activeJOB.setIsBlocked(true);

	Integer currentQueue = activeJOB.getCurrentQueue();
	Integer newQueueNumber = activeJOB.getCurrentQueue();

	if (currentQueue == 4) {
	    newQueueNumber = 1;
	    numberOfMigration++;
	} else {

	    if (quantum >= activeQueue.getQUANTUM()) {
		if (currentQueue == 1) {
		    newQueueNumber = 2;
		    numberOfMigration++;

		} else if (currentQueue == 2) {
		    newQueueNumber = 3;
		    numberOfMigration++;

		} else if (currentQueue == 3) {
		    newQueueNumber = 4;
		    numberOfMigration++;

		} else if (currentQueue == 4) {
		    newQueueNumber = 4;
		}
	    }
	}

	activeJOB.setCurrentQueue(newQueueNumber);
	activeJOB.setCurrentTurn(0);
	SYSTEM.PCBList.replace(activeJOB.getJobID(), activeJOB);
	ready_queues.replace(activeQueue.getQueueID(), activeQueue);
	activeJOB = null;
    }

    public static Boolean getNextJobFromReadyQueue() {
	boolean jobFound = false;
	for (Map.Entry<Integer, Queue> queueEntry : ready_queues.entrySet()) {
	    Queue queue = queueEntry.getValue();

	    if (queue.jobs.isEmpty() == false) {
		Integer activeJobID = queue.getJobs().poll();
		activeJOB = SYSTEM.PCBList.get(activeJobID);
		activeJOB.incrementCurrentTurn();
		activeJOB.incrementCpuShots(1);
		activeQueue = queue;
		SYSTEM.PCBList.replace(activeJOB.getJobID(), activeJOB);
		ready_queues.replace(queue.getQueueID(), queue);
		jobFound = true;
		break;
	    }
	}
	return jobFound;
    }

    public static Integer cpu(Integer PC, Boolean traceSwitch) {
	try {
	    int n = 1;
	    do {
		programCounter = PC;
		if (activeJOB.getTraceSwitch() && activeJOB.getTraceFileString().length() == 0) {
		    activeJOB.getTraceFileString()
			    .append(String.format("\n %-7s  %-10s  %-21s  %-20s  %-21s %n",
				    "PC(HEX)", "IR(HEX)", "C(A)(HEX)BEFORE_EXEC",
				    "C(A)(HEX)AFTER_EXEC", "C(EA)(HEX)AFTER_EXEC"));
		}
		// check: suspected infinite loop
		if (activeJOB.getBurstTime() >= 100000) {
		    activeJOB.setHasError(true);
		    activeJOB.setErrorCode(ERROR_HANDLER.Error.eSuspectedInfiniteJob);
		    FATAL_ERROR = true;
		    break;
		}

		// get PC after translation
		realPC = virtualToRealAddress(programCounter);
		markPageAsReferenced(programCounter);
		if (realPC != -1) {
		    // read the next instruction into memory buffer register
		    if (!MEMORY.memory("READ", realPC, "")) {
			activeJOB.setHasError(true);
			activeJOB.setErrorCode(ERROR_HANDLER.Error.eMemoryAddressFault);
			FATAL_ERROR = true;
			break;
		    }
		} else {
		    activeJOB.setHasError(true);
		    activeJOB.setErrorCode(ERROR_HANDLER.Error.eMemoryAddressFault);
		    FATAL_ERROR = true;
		    break;
		}

		// 1. FETCH INSTRUCTION
		IR = MEMORY.MEM_BUFFER_REG;
		// 2. DECODE INSTRUCTION
		isInDirectAddressing = (IR.substring(0, 1).equals("1")) ? true : false;
		opcode = opCode.getopCode(Utilities.binary2Hex(IR.substring(1, 8), 2));
		accumulator = Utilities.binary2Decimal(IR.substring(8, 12));
		indexReg = Utilities.binary2Decimal(IR.substring(12, 16));
		DADDR = Utilities.binary2Decimal(IR.substring(16, IR.length()));
		// 3. VALIDATE INSTRUCTION
		if (opcode == null) {
		    activeJOB.setHasError(true);
		    activeJOB.setErrorCode(ERROR_HANDLER.Error.eInvalidOpcode);
		    FATAL_ERROR = true;
		    break;
		}
		// check: validate arithmetic and index register address
		if ((indexReg < 0 || indexReg >= NUMBER_OF_REGISTERS)
			|| (accumulator < 0 && accumulator >= NUMBER_OF_REGISTERS)) {
		    activeJOB.setHasError(true);
		    activeJOB.setErrorCode(ERROR_HANDLER.Error.eMemoryAddressFault);
		    FATAL_ERROR = true;
		    break;
		}
		EA = getEffectiveAddress();
		if (EA == -1) {
		    activeJOB.setHasError(true);
		    activeJOB.setErrorCode(ERROR_HANDLER.Error.eMemoryAddressFault);
		    FATAL_ERROR = true;
		    break;
		}
		markPageAsReferenced(EA);
		if (!isValidEffectiveAddress(EA)) {
		    activeJOB.setHasError(true);
		    activeJOB.setErrorCode(ERROR_HANDLER.Error.eMemoryAddressFault);
		    FATAL_ERROR = true;
		    break;
		}

		// 4. EXECUTE INSTRUCTION
		String curContentOfAcc, newContentOfAcc, newContentOfEA = "";

		// appending content of PC, Instruction, accumulator and
		// effective address after execution
		if (activeJOB.getTraceSwitch()) {
		    curContentOfAcc = Utilities.binary2Hex(REG[accumulator], 8);
		    activeJOB.getTraceFileString()
			    .append(String.format(" %-7s  %-10s  %-21s ",
				    Utilities.decimal2hex(programCounter, 2).toUpperCase(),
				    Utilities.binary2Hex(IR, 8), curContentOfAcc));
		}

		programCounter = executeInstruction(programCounter);

		if (activeJOB.getTraceSwitch()) {
		    newContentOfAcc = Utilities.binary2Hex(REG[accumulator], 8);
		    MEMORY.memory("READ", realEA, "");
		    newContentOfEA = MEMORY.MEM_BUFFER_REG;
		    activeJOB.getTraceFileString().append(String.format("%-20s  %-21s %n",
			    newContentOfAcc, Utilities.binary2Hex(newContentOfEA, 8)));
		}
		n++;
	    } while (n == 1);
	} catch (NullPointerException e) {
	    activeJOB.setHasError(true);
	    activeJOB.setErrorCode(ERROR_HANDLER.Error.eNullPointerError);
	    FATAL_ERROR = true;
	} catch (ArithmeticException e) {
	    activeJOB.setHasError(true);
	    activeJOB.setErrorCode(ERROR_HANDLER.Error.eDivideByZero);
	    FATAL_ERROR = true;
	} catch (NumberFormatException e) {
	    activeJOB.setHasError(true);
	    activeJOB.setErrorCode(ERROR_HANDLER.Error.eInvalidLoaderFormatCharacter);
	    FATAL_ERROR = true;
	} catch (Exception e) {
	    activeJOB.setHasError(true);
	    activeJOB.setErrorCode(ERROR_HANDLER.Error.eUnKnownError);
	    FATAL_ERROR = true;
	}
	return programCounter;
    }

    /**
     * This method resets the global variables of CPU for the next combinations
     * of CPU turns and Quantum size
     */
    public static void resetCPU() {
	resetVariables();
	blockedQueue.clear();
	ready_queues.clear();

	previousStatusOutPutTime = 0;
	previousQueueSampleTime = 0;
	turn = 0;
	activeJOB = null;
	activeQueue = null;
	n = 0;
	q = 0;
	numberOfMigration = 0;
    }

    public static void resetVariables() {
	IR = "";
	isInDirectAddressing = false;
	opcode = opCode.INVALID_opCode;
	accumulator = 0;
	indexReg = 0;
	DADDR = 0;
	EA = 0;
	realEA = 0;
	programCounter = 0;
	realPC = 0;
	HLT = false;
	FATAL_ERROR = false;
	IS_RD_WR = false;
	for (int i = 0; i < REG.length; i++) {
	    REG[i] = "0";
	}
	quantum = 0;
    }

    public static void checkSystemOnClockIncrement() {
	checkBlockedQueue();
	performAgingIn4thQueue();
	checkAgedJobsIn4thQueue();
	printSystemStatusIfTimeOut();

    }

    public static void printSystemStatusIfTimeOut() {

	Integer currentStatusOutPutTime = SYSTEM.systemCLOCK - previousStatusOutPutTime;
	if (currentStatusOutPutTime >= 1200) {
	    previousStatusOutPutTime = SYSTEM.systemCLOCK;

	    float diskInUse = DISK_MANAGER.DISK_SIZE
		    - (DISK_MANAGER.availablePagesOnDisk.size() * 16);
	    float diskInUsePercent = (diskInUse * 100) / DISK_MANAGER.DISK_SIZE;
	    DecimalFormat dformat = new DecimalFormat();
	    dformat.setMaximumFractionDigits(2);
	    String diskUtilization = (dformat.format(diskInUsePercent));

	    // UPDATE PROGRESS FILE
	    SYSTEM.progressFileString
		    .append("\n\nCURRENT_CLOCK_TIME(DECIMAL): " + SYSTEM.systemCLOCK);
	    SYSTEM.progressFileString.append(
		    "\nSub_Queue1(JOB_ID)(DECIMAL): " + ready_queues.get(1).getJobs().toString());
	    SYSTEM.progressFileString.append(
		    "\nSub_Queue2(JOB_ID)(DECIMAL): " + ready_queues.get(2).getJobs().toString());
	    SYSTEM.progressFileString.append(
		    "\nSub_Queue3(JOB_ID)(DECIMAL): " + ready_queues.get(3).getJobs().toString());
	    SYSTEM.progressFileString.append(
		    "\nSub_Queue4(JOB_ID)(DECIMAL): " + ready_queues.get(4).getJobs().toString());
	    SYSTEM.progressFileString
		    .append("\nBLOCKED_QUEUE(JOB_ID)(DECIMAL): " + blockedQueue.toString());
	    if (activeJOB != null) {
		SYSTEM.progressFileString.append(
			"\nCURRENTLY_EXECUTING_JOB_ID(DECIMAL):" + activeJOB.getJobID().toString());
	    }
	    SYSTEM.progressFileString
		    .append("\nMEMORY_CONFIGURATION(DECIMAL)(BYTES): " + (MEMORY.MEMORY_SIZE*32));
	    SYSTEM.progressFileString.append(
		    "\nDEGREE_OF_MULTIPROGRAMMING(DECIMAL): " + (LOADER.jobsOnMemory.size()));
	    SYSTEM.progressFileString
		    .append("\n%_OF_DISK_IN_USE(DECIMAL): " + diskUtilization + "%\n\n\n");
	    SYSTEM.writeProgressFile();

	    // UPDATE MLFBQ FILE
	    SYSTEM.MLFBQFileString.append("\n\n");
	    SYSTEM.MLFBQFileString.append(
		    "\nSub_Queue1(JOB_ID)(DECIMAL): " + ready_queues.get(1).getJobs().toString());
	    SYSTEM.MLFBQFileString.append(
		    "\nSub_Queue2(JOB_ID)(DECIMAL): " + ready_queues.get(2).getJobs().toString());
	    SYSTEM.MLFBQFileString.append(
		    "\nSub_Queue3(JOB_ID)(DECIMAL): " + ready_queues.get(3).getJobs().toString());
	    SYSTEM.MLFBQFileString.append(
		    "\nSub_Queue4(JOB_ID)(DECIMAL): " + ready_queues.get(4).getJobs().toString());
	    SYSTEM.writeMLFBQFile();
	}

	Integer currentQueueSampleTime = SYSTEM.systemCLOCK - previousQueueSampleTime;
	if (currentQueueSampleTime >= 500) {
	    previousQueueSampleTime = SYSTEM.systemCLOCK;
	    int n = 1;
	    while (n <= ready_queues.size()) {
		Queue queue = ready_queues.get(n);
		Integer cur_queueSize = queue.getJobs().size();
		queue.getQueueSizes().add(cur_queueSize);
		ready_queues.replace(queue.getQueueID(), queue);
		n++;
	    }
	}
    }

    public static void checkBlockedQueue() {
	for (int i = 0; i < blockedQueue.size(); i++) {
	    Integer jobId = blockedQueue.get(i);
	    PCB pcb = SYSTEM.PCBList.get(jobId);
	    if (pcb.getIsBlocked()) {
		int blockRevivalTime = pcb.getBlockedQueueRevivalTime();
		int currentTime = SYSTEM.systemCLOCK;
		if (currentTime >= blockRevivalTime) {
		    blockedQueue.remove(pcb.getJobID());
		    Queue queue = ready_queues.get(pcb.getCurrentQueue());
		    queue.getJobs().addLast(pcb.getJobID());
		    ready_queues.replace(pcb.getCurrentQueue(), queue);
		}
	    }
	}
    }

    public static void checkAgedJobsIn4thQueue() {
	ArrayList<Integer> jobs = new ArrayList<>();
	jobs.addAll(ready_queues.get(4).getJobs());
	ArrayList<Integer> agedJobs = new ArrayList<>();

	// get aged jobs
	for (Integer jobId : jobs) {
	    PCB pcb = SYSTEM.PCBList.get(jobId);
	    if (pcb.getJobAge() > (9 * n * q)) {
		agedJobs.add(jobId);
	    }
	}
	// add aged jobs to sub_queue1 and remove aged jobs to sub_queue4
	for (Integer jobId : agedJobs) {
	    ready_queues.get(1).getJobs().addLast(jobId);
	    ready_queues.get(4).getJobs().remove(jobId);
	    SYSTEM.PCBList.get(jobId).setCurrentQueue(1);
	    SYSTEM.PCBList.get(jobId).setCurrentTurn(0);
	}
    }

    public static void performAgingIn4thQueue() {
	// perform aging
	for (Integer jobId : ready_queues.get(4).getJobs()) {
	    SYSTEM.PCBList.get(jobId).incrementJobAge(1);
	}
    }

    public static void SPOOL(Integer JobID) {

	PCB pcb = SYSTEM.PCBList.get(JobID);

	try {
	    // update progress.txt with job termination status
	    SYSTEM.progressFileString.append("\n\nJOB IDEBTIFICATION NUMBER(DECIMAL): " + pcb.getJobID());
	    SYSTEM.progressFileString
		    .append("\nCURRENT_CLOCK_TIME(DECIMAL): " + SYSTEM.systemCLOCK);
	    SYSTEM.progressFileString.append(
		    "\nCLOCK_AT_LOAD_TIME(HEX): " + Integer.toHexString(pcb.getArrivalTime()));
	    SYSTEM.progressFileString.append("\nCLOCK_AT_TERMINATION_TIME(HEX): "
		    + Integer.toHexString(pcb.getTerminationTime()));
	    // Warning if any
	    if (pcb.getHasWarning() && pcb.getWarningCode() != null) {
		SYSTEM.progressFileString
			.append("\nJOB_ID :" + pcb.getJobID() + "  " + pcb.getWarningCode());
	    }
	    // print output
	    SYSTEM.progressFileString.append("\nOUTPUT(HEX):\n\t");
	    Integer expectedOutputWords = pcb.getOutputLineLimit() * 4;
	    int n = 0;
	    while (n < expectedOutputWords) {
		int i = 0;
		while (i < 4) {
		    String word = "00000000";
		    if (n < pcb.getOutputBuffer().size()) {
			word = Utilities.binary2Hex(pcb.getOutputBuffer().get(n), 8);
		    }
		    SYSTEM.progressFileString.append(word);
		    i++;
		    n++;
		    if (i == 4 && n < expectedOutputWords) {
			SYSTEM.progressFileString.append("\n\t");
		    }
		}
	    }
	    SYSTEM.writeProgressFile();
	    // Error if any
	    if (pcb.getHasError() || pcb.getErrorCode() != null) {
		SYSTEM.progressFileString.append("\n" + pcb.getErrorCode());
	    }
	    // OUTPUT : Nature of termination - normal or abnormal
	    if (pcb.getHasError() || pcb.getErrorCode() != null) {
		SYSTEM.progressFileString.append("\nABNORMAL TERMINATION");
	    } else {
		SYSTEM.progressFileString.append("\nNORMAL TERMINATION");
	    }

	    // calculate job timings
	    pcb.setTerminationTime(SYSTEM.systemCLOCK);
	    int turnaroundTime = pcb.getTerminationTime() - pcb.getArrivalTime();
	    pcb.setTurnAroundTime(turnaroundTime);

	    int runTime = pcb.getTerminationTime() - pcb.getStartTime();
	    pcb.setRunTime(runTime);

	    int executionTime = pcb.getBurstTime();
	    pcb.setExecutionTime(executionTime);

	    int pageFaultTime = pcb.getNumberOFPageFaults() * CLOCK_ON_PF;
	    pcb.setPageFaultTime(pageFaultTime);

	    int IOTime = pcb.getNumberOfIO() * CLOCK_ON_IO;
	    pcb.setIOTime(IOTime);

	    SYSTEM.PCBList.replace(pcb.getJobID(), pcb);
	    SYSTEM.progressFileString
		    .append("\nTURN_AROUND_TIME(DECIMAL): " + pcb.getTurnAroundTime());
	    SYSTEM.progressFileString.append("\nRUN_TIME(DECIMAL): " + pcb.getRunTime());
	    SYSTEM.progressFileString
		    .append("\nEXECUTION_TIME(DECIMAL): " + pcb.getExecutionTime());
	    SYSTEM.progressFileString
		    .append("\nPAGE_FAULT_HANDLING_TIME(DECIMAL): " + pcb.getPageFaultTime());
	    SYSTEM.progressFileString
		    .append("\nNUMBER_OF_CPU_SHOTS(DECIMAL): " + pcb.getCpuShots() + "\n\n");

	    SYSTEM.writeProgressFile();
	    if (pcb.getTraceSwitch()) {
		SYSTEM.writeTraceFileForJob(pcb);
	    }
	} catch (Exception e) {
	}

	// remove the job from memory
	LOADER.unloadJobOnMemory(JobID);
	// remove the job from disk
	DISK_MANAGER.unloadJobOnDisk(JobID);
	// check and add more input jobs to disk
	DISK_MANAGER.loadJobsToDisk();
	// check and add more disk jobs to memory
	LOADER.loader();

	pcb.clearPCB();
    }

    public static Integer executeInstruction(Integer programCounter) throws Exception {

	switch (opcode) {

	case LD: {
	    programCounter = LD_OPERATION(programCounter);
	    break;
	}
	case ST: {
	    programCounter = ST_OPERATION(programCounter);
	    break;
	}
	case AD: {
	    programCounter = ADD_OPETAION(programCounter);
	    break;
	}
	case SB: {
	    programCounter = SUBTRACT_OPERATION(programCounter);
	    break;
	}
	case MPY: {
	    programCounter = MULTIPLY_OPERATION(programCounter);
	    break;
	}
	case DIV: {
	    programCounter = DIVIDE_OPERATION(programCounter);
	    break;
	}
	case RD: {
	    programCounter = READ_OPERATION(programCounter);
	    break;
	}
	case WR: {
	    programCounter = WRITE_OPERATION(programCounter);
	    break;
	}
	case SHL: {
	    programCounter = SHL_OPERATION(programCounter);
	    break;
	}
	case SHR: {
	    programCounter = SHR_OPERATION(programCounter);
	    break;
	}
	case BRM: {
	    programCounter = BRM_OPERATION(programCounter);
	    break;
	}
	case BRP: {
	    programCounter = BRP_OPERATION(programCounter);
	    break;
	}
	case BRZ: {
	    programCounter = BRZ_OPERATION(programCounter);
	    break;
	}
	case BRL: {
	    programCounter = BRL_OPERATION(programCounter);
	    break;
	}
	case AND: {
	    programCounter = AND_OPERATION(programCounter);
	    break;
	}
	case OR: {
	    programCounter = OR_OPERATION(programCounter);
	    break;
	}
	case DMP: {
	    // Dump physical memory to stdout
	    SYSTEM.systemCLOCK += ONE_CLOCK_UNIT;
	    quantum += ONE_CLOCK_UNIT;
	    activeJOB.incrementBurstTimeBy(ONE_CLOCK_UNIT);

	    checkSystemOnClockIncrement();
	    programCounter++;
	    if (!(MEMORY.memory(DUMP, 0, ""))) {
		activeJOB.setHasError(true);
		activeJOB.setErrorCode(ERROR_HANDLER.Error.eMemoryAddressFault);
		FATAL_ERROR = true;
	    }
	    break;
	}

	case HLT: { // Stop Execution
	    SYSTEM.systemCLOCK += ONE_CLOCK_UNIT;
	    quantum += ONE_CLOCK_UNIT;
	    activeJOB.incrementBurstTimeBy(ONE_CLOCK_UNIT);
	    programCounter++;
	    HLT = true;
	    break;
	}

	default:
	    break;
	}
	return programCounter;
    }

    /**
     * Category :Operations to be simulated
     */
    public static Integer READ_OPERATION(Integer programCounter) throws Exception {
	ArrayList<String> readBuffer = new ArrayList<String>();
	int numberOfWordsLoaded = 0;
	Integer diskAddress = activeJOB.getAddressOfCurrentRDdataRecord() + numberOfWordsLoaded;

	if (activeJOB.getAddressOfCurrentRDdataRecord() == -1) {
	    activeJOB.setHasError(true);
	    activeJOB.setErrorCode(ERROR_HANDLER.Error.eAddressOutOfRange);
	    FATAL_ERROR = true;
	} else {
	    while (numberOfWordsLoaded < 4) {
		readBuffer.add(DISK_MANAGER.DISK[diskAddress]);
		diskAddress++;
		numberOfWordsLoaded++;
	    }

	    int curRDPage = activeJOB.getPageTable()
		    .get(activeJOB.getCurrentRDdataPageNo()).pageNumberOnDisk;
	    int value = (diskAddress / 16);
	    if (value == curRDPage) {
		activeJOB.setddressOfCurrentRDdataRecord(diskAddress);
	    } else {
		Integer newRDPageNo = activeJOB.getCurrentRDdataPageNo();
		newRDPageNo = newRDPageNo + 1;
		if (newRDPageNo < activeJOB.getPageTable().size()) {
		    activeJOB.setCurrentRDdataPageNo(newRDPageNo);
		    Integer newAddress = activeJOB.getPageTable()
			    .get(activeJOB.getCurrentRDdataPageNo()).pageNumberOnDisk;
		    activeJOB.setddressOfCurrentRDdataRecord(newAddress * 16);
		    SYSTEM.PCBList.replace(activeJOB.getJobID(), activeJOB);
		} else {
		    activeJOB.setddressOfCurrentRDdataRecord(-1);
		    activeJOB.setCurrentRDdataPageNo(-1);
		}
	    }

	    numberOfWordsLoaded = 0;
	    for (String word : readBuffer) {
		realEA = virtualToRealAddress(EA + numberOfWordsLoaded);
		if (MEMORY.memory(WRIT, realEA, word)) {
		    markPageAsWritten(EA + numberOfWordsLoaded);
		    numberOfWordsLoaded++;
		} else {
		    activeJOB.setHasError(true);
		    activeJOB.setErrorCode(ERROR_HANDLER.Error.eOverFlow);
		    FATAL_ERROR = true;
		    break;
		}
	    }

	    activeJOB.setBlockedQueueRevivalTime(SYSTEM.systemCLOCK + CLOCK_ON_IO);
	    SYSTEM.systemCLOCK += ONE_CLOCK_UNIT;
	    checkSystemOnClockIncrement();
	    SYSTEM.systemCLOCK += ONE_CLOCK_UNIT;
	    checkSystemOnClockIncrement();
	    quantum += (CLOCK_ON_IO + TWO_CLOCK_UNIT);
	    activeJOB.incrementBurstTimeBy(TWO_CLOCK_UNIT);
	    IS_RD_WR = true;
	    activeJOB.incrementNumberOfIOBy(1);
	    programCounter++;

	}

	return programCounter;
    }

    public static Integer WRITE_OPERATION(Integer programCounter) {

	Integer loadAddress = activeJOB.getAddressOfCurrentWRdataRecord();
	if (loadAddress == -1) {
	    activeJOB.setHasError(true);
	    activeJOB.setErrorCode(ERROR_HANDLER.Error.eInsufficientOutputSpace);
	    FATAL_ERROR = true;
	} else {
	    ArrayList<String> readBuffer = new ArrayList<String>();
	    int numberOfWordsLoaded = 0;
	    while (numberOfWordsLoaded < 4) {
		realEA = virtualToRealAddress(EA + numberOfWordsLoaded);
		if (MEMORY.memory(READ, (realEA), "")) {
		    String word = MEMORY.MEM_BUFFER_REG;
		    readBuffer.add(word);
		    numberOfWordsLoaded++;
		} else {
		    activeJOB.setHasError(true);
		    activeJOB.setErrorCode(ERROR_HANDLER.Error.eMemoryAddressFault);
		    FATAL_ERROR = true;
		    break;
		}
	    }

	    if (!FATAL_ERROR) {
		ArrayList<String> output = activeJOB.getOutputBuffer();
		for (String word : readBuffer) {
		    DISK_MANAGER.DISK[loadAddress] = word;
		    output.add(word);
		    numberOfWordsLoaded++;
		    loadAddress++;
		}
		activeJOB.setOutputBuffer(output);
	    }
	    int curWRPage = activeJOB.getPageTable()
		    .get(activeJOB.getCurrentWRdataPageNo()).pageNumberOnDisk;
	    int value = (loadAddress / 16);
	    if (value == curWRPage) {
		activeJOB.setAddressOfCurrentWRdataRecord(loadAddress);
	    } else {
		Integer newWRPageNo = activeJOB.getCurrentWRdataPageNo();
		newWRPageNo = newWRPageNo + 1;

		if (newWRPageNo < activeJOB.getPageTable().size()) {
		    activeJOB.setCurrentWRdataPageNo(newWRPageNo);
		    Integer newAddress = activeJOB.getPageTable()
			    .get(activeJOB.getCurrentWRdataPageNo()).pageNumberOnDisk;
		    activeJOB.setAddressOfCurrentWRdataRecord(newAddress * 16);
		    SYSTEM.PCBList.replace(activeJOB.getJobID(), activeJOB);
		} else {
		    activeJOB.setAddressOfCurrentWRdataRecord(-1);
		    activeJOB.setCurrentWRdataPageNo(-1);
		}
	    }

	    activeJOB.setBlockedQueueRevivalTime(SYSTEM.systemCLOCK + CLOCK_ON_IO);
	    SYSTEM.systemCLOCK += ONE_CLOCK_UNIT;
	    checkSystemOnClockIncrement();
	    SYSTEM.systemCLOCK += ONE_CLOCK_UNIT;
	    checkSystemOnClockIncrement();
	    quantum += (CLOCK_ON_IO + TWO_CLOCK_UNIT);
	    activeJOB.incrementBurstTimeBy(TWO_CLOCK_UNIT);
	    IS_RD_WR = true;
	    activeJOB.incrementNumberOfIOBy(1);
	    programCounter++;
	}
	return programCounter;
    }

    public static Integer ADD_OPETAION(Integer programCounter) {
	// SEMANTICS: C(REG) + C(EA) --> REG check: handle overflow/underflow
	realEA = virtualToRealAddress(EA);
	if (MEMORY.memory(READ, realEA, "")) {
	    try {
		String result = Utilities.decimal2Binary((Utilities.binary2Decimal(REG[accumulator])
			+ Utilities.binary2Decimal(MEMORY.MEM_BUFFER_REG)), 32);

		if (result.equals("-1")) {
		    activeJOB.setHasError(true);
		    activeJOB.setErrorCode(ERROR_HANDLER.Error.eInputOutOfRange);
		    FATAL_ERROR = true;
		} else {
		    REG[accumulator] = result;
		}
	    } catch (ArithmeticException e) {
		activeJOB.setHasError(true);
		activeJOB.setErrorCode(ERROR_HANDLER.Error.eOverFlow);
		FATAL_ERROR = true;
	    } catch (Exception e) {
	    }
	} else {
	    activeJOB.setHasError(true);
	    activeJOB.setErrorCode(ERROR_HANDLER.Error.eMemoryAddressFault);
	    FATAL_ERROR = true;
	}

	SYSTEM.systemCLOCK += ONE_CLOCK_UNIT;
	quantum += ONE_CLOCK_UNIT;
	activeJOB.incrementBurstTimeBy(ONE_CLOCK_UNIT);
	checkSystemOnClockIncrement();
	programCounter++;
	return programCounter;
    }

    public static Integer SUBTRACT_OPERATION(Integer programCounter) throws Exception {
	// SEMANTICS: C(REG) - C(EA) --> REG check: handle overflow/underflow
	realEA = virtualToRealAddress(EA);
	if (MEMORY.memory(READ, realEA, "")) {
	    try {
		String result = Utilities.decimal2Binary((Utilities.binary2Decimal(REG[accumulator])
			- Utilities.binary2Decimal(MEMORY.MEM_BUFFER_REG)), 32);

		if (result.equals("-1")) {
		    activeJOB.setHasError(true);
		    activeJOB.setErrorCode(ERROR_HANDLER.Error.eInputOutOfRange);
		    FATAL_ERROR = true;
		} else {
		    REG[accumulator] = result;
		}
	    } catch (ArithmeticException e) {
		activeJOB.setHasError(true);
		activeJOB.setErrorCode(ERROR_HANDLER.Error.eOverFlow);
		FATAL_ERROR = true;
	    } catch (Exception e) {
	    }
	} else {
	    activeJOB.setHasError(true);
	    activeJOB.setErrorCode(ERROR_HANDLER.Error.eMemoryAddressFault);
	    FATAL_ERROR = true;
	}

	SYSTEM.systemCLOCK += ONE_CLOCK_UNIT;
	quantum += ONE_CLOCK_UNIT;
	activeJOB.incrementBurstTimeBy(ONE_CLOCK_UNIT);

	checkSystemOnClockIncrement();
	programCounter++;
	return programCounter;

    }

    public static Integer MULTIPLY_OPERATION(Integer programCounter) {
	// SEMANTICS: C(REG) * C(EA) --> REG check: handle overflow/underflow
	realEA = virtualToRealAddress(EA);

	if (MEMORY.memory(READ, realEA, "")) {
	    try {
		String result = Utilities.decimal2Binary((Utilities.binary2Decimal(REG[accumulator])
			* Utilities.binary2Decimal(MEMORY.MEM_BUFFER_REG)), 32);

		if (result.equals("-1")) {
		    activeJOB.setHasError(true);
		    activeJOB.setErrorCode(ERROR_HANDLER.Error.eInputOutOfRange);
		    FATAL_ERROR = true;
		} else {
		    REG[accumulator] = result;
		}
	    } catch (ArithmeticException e) {
		activeJOB.setHasError(true);
		activeJOB.setErrorCode(ERROR_HANDLER.Error.eOverFlow);
		FATAL_ERROR = true;
	    } catch (Exception e) {
	    }
	} else {
	    activeJOB.setHasError(true);
	    activeJOB.setErrorCode(ERROR_HANDLER.Error.eMemoryAddressFault);
	    FATAL_ERROR = true;
	}

	SYSTEM.systemCLOCK += ONE_CLOCK_UNIT;
	checkSystemOnClockIncrement();
	SYSTEM.systemCLOCK += ONE_CLOCK_UNIT;

	checkSystemOnClockIncrement();
	quantum += TWO_CLOCK_UNIT;
	activeJOB.incrementBurstTimeBy(TWO_CLOCK_UNIT);
	programCounter++;
	return programCounter;

    }

    public static Integer DIVIDE_OPERATION(Integer programCounter) {
	// SEMANTICS: C(REG) / C(EA) --> REG check: handle overflow/underflow
	realEA = virtualToRealAddress(EA);
	if (MEMORY.memory(READ, realEA, "")) {
	    try {
		Integer y = Utilities.binary2Decimal(MEMORY.MEM_BUFFER_REG);
		if (y == 0) {
		    activeJOB.setHasError(true);
		    activeJOB.setErrorCode(ERROR_HANDLER.Error.eDivideByZero);
		    FATAL_ERROR = true;
		} else {
		    String result = Utilities
			    .decimal2Binary((Utilities.binary2Decimal(REG[accumulator])
				    / Utilities.binary2Decimal(MEMORY.MEM_BUFFER_REG)), 32);

		    if (result.equals("-1")) {
			activeJOB.setHasError(true);
			activeJOB.setErrorCode(ERROR_HANDLER.Error.eInputOutOfRange);
			FATAL_ERROR = true;
		    } else {
			REG[accumulator] = result;
		    }
		}
	    } catch (ArithmeticException e) {
		activeJOB.setHasError(true);
		activeJOB.setErrorCode(ERROR_HANDLER.Error.eOverFlow);
		FATAL_ERROR = true;
	    } catch (Exception e) {
	    }
	} else {
	    activeJOB.setHasError(true);
	    activeJOB.setErrorCode(ERROR_HANDLER.Error.eMemoryAddressFault);
	    FATAL_ERROR = true;
	}

	SYSTEM.systemCLOCK += ONE_CLOCK_UNIT;
	checkSystemOnClockIncrement();
	SYSTEM.systemCLOCK += ONE_CLOCK_UNIT;
	checkSystemOnClockIncrement();
	quantum += TWO_CLOCK_UNIT;
	activeJOB.incrementBurstTimeBy(TWO_CLOCK_UNIT);
	programCounter++;
	return programCounter;
    }

    public static Integer AND_OPERATION(Integer programCounter) {
	// SEMANTICS: C(REG) & C(EA) --> REG
	realEA = virtualToRealAddress(EA);
	if (MEMORY.memory(READ, realEA, "")) {

	    try {
		String result = Utilities.decimal2Binary((Utilities.binary2Decimal(REG[accumulator])
			& Utilities.binary2Decimal(MEMORY.MEM_BUFFER_REG)), 32);

		if (result.equals("-1")) {
		    activeJOB.setHasError(true);
		    activeJOB.setErrorCode(ERROR_HANDLER.Error.eInputOutOfRange);
		    FATAL_ERROR = true;
		} else {
		    REG[accumulator] = result;
		}
	    } catch (ArithmeticException e) {
		activeJOB.setHasError(true);
		activeJOB.setErrorCode(ERROR_HANDLER.Error.eOverFlow);
		FATAL_ERROR = true;
	    } catch (Exception e) {
	    }
	} else {
	    activeJOB.setHasError(true);
	    activeJOB.setErrorCode(ERROR_HANDLER.Error.eMemoryAddressFault);
	    FATAL_ERROR = true;
	}
	SYSTEM.systemCLOCK += ONE_CLOCK_UNIT;
	quantum += ONE_CLOCK_UNIT;
	activeJOB.incrementBurstTimeBy(ONE_CLOCK_UNIT);
	checkSystemOnClockIncrement();
	programCounter++;
	return programCounter;
    }

    public static Integer OR_OPERATION(Integer programCounter) {
	// SEMANTICS: C(REG) | C(EA) --> REG
	realEA = virtualToRealAddress(EA);
	if (MEMORY.memory(READ, realEA, "")) {
	    try {
		String result = Utilities.decimal2Binary((Utilities.binary2Decimal(REG[accumulator])
			| Utilities.binary2Decimal(MEMORY.MEM_BUFFER_REG)), 32);

		if (result.equals("-1")) {
		    activeJOB.setHasError(true);
		    activeJOB.setErrorCode(ERROR_HANDLER.Error.eInputOutOfRange);
		    FATAL_ERROR = true;
		} else {
		    REG[accumulator] = result;
		}
	    } catch (ArithmeticException e) {
		activeJOB.setHasError(true);
		activeJOB.setErrorCode(ERROR_HANDLER.Error.eOverFlow);
		FATAL_ERROR = true;
	    } catch (Exception e) {
	    }
	} else {
	    activeJOB.setHasError(true);
	    activeJOB.setErrorCode(ERROR_HANDLER.Error.eMemoryAddressFault);
	    FATAL_ERROR = true;
	}
	SYSTEM.systemCLOCK += ONE_CLOCK_UNIT;
	quantum += ONE_CLOCK_UNIT;
	activeJOB.incrementBurstTimeBy(ONE_CLOCK_UNIT);
	checkSystemOnClockIncrement();
	programCounter++;
	return programCounter;
    }

    public static Integer BRL_OPERATION(Integer programCounter) throws Exception {
	// SEMANTICS: if C(PC) --> REG then EA --> PC
	if (MEMORY.memory(READ, realPC, "")) {
	    REG[accumulator] = MEMORY.MEM_BUFFER_REG;
	    programCounter = EA;
	} else {
	    activeJOB.setHasError(true);
	    activeJOB.setErrorCode(ERROR_HANDLER.Error.eMemoryAddressFault);
	    FATAL_ERROR = true;
	    programCounter++;
	}
	SYSTEM.systemCLOCK += TWO_CLOCK_UNIT;
	quantum += TWO_CLOCK_UNIT;
	activeJOB.incrementBurstTimeBy(TWO_CLOCK_UNIT);
	checkSystemOnClockIncrement();
	return programCounter;
    }

    public static Integer BRZ_OPERATION(Integer programCounter) {
	// SEMANTICS: if C(REG) = 0 then EA --> PC
	// Integer result = Utilities.binary2Decimal(REG[accumulator]);
	int result = ((int) Long.parseLong(REG[accumulator], 2));
	if (result == 0) {
	    programCounter = EA;
	} else {
	    programCounter++;
	}
	SYSTEM.systemCLOCK += ONE_CLOCK_UNIT;
	quantum += ONE_CLOCK_UNIT;
	activeJOB.incrementBurstTimeBy(ONE_CLOCK_UNIT);
	checkSystemOnClockIncrement();
	return programCounter;
    }

    public static Integer BRM_OPERATION(Integer programCounter) {
	// SEMANTICS: if C(REG) < 0 then EA --> PC

	int result = ((int) Long.parseLong(REG[accumulator], 2));

	// Integer result = Utilities.binary2Decimal(REG[accumulator]);
	if (result < 0) {
	    programCounter = EA;
	} else {
	    programCounter++;
	}
	SYSTEM.systemCLOCK += ONE_CLOCK_UNIT;
	quantum += ONE_CLOCK_UNIT;
	activeJOB.incrementBurstTimeBy(ONE_CLOCK_UNIT);
	checkSystemOnClockIncrement();
	return programCounter;
    }

    public static Integer BRP_OPERATION(Integer programCounter) throws Exception {
	// SEMANTICS: if C(REG) > 0 then EA --> PC
	// Integer result = Utilities.binary2Decimal(REG[accumulator]);
	int result = ((int) Long.parseLong(REG[accumulator], 2));
	if (result > 0) {
	    programCounter = EA;
	} else {
	    programCounter++;
	}
	SYSTEM.systemCLOCK += ONE_CLOCK_UNIT;
	quantum += ONE_CLOCK_UNIT;
	activeJOB.incrementBurstTimeBy(ONE_CLOCK_UNIT);
	checkSystemOnClockIncrement();
	return programCounter;
    }

    public static Integer LD_OPERATION(Integer programCounter) throws Exception {
	// SEMANTICS: C(EA)-->REG
	realEA = virtualToRealAddress(EA);
	if (MEMORY.memory(READ, realEA, "")) {
	    REG[accumulator] = MEMORY.MEM_BUFFER_REG;
	} else {
	    activeJOB.setHasError(true);
	    activeJOB.setErrorCode(ERROR_HANDLER.Error.eMemoryAddressFault);
	    FATAL_ERROR = true;
	}
	SYSTEM.systemCLOCK += ONE_CLOCK_UNIT;
	quantum += ONE_CLOCK_UNIT;
	activeJOB.incrementBurstTimeBy(ONE_CLOCK_UNIT);
	checkSystemOnClockIncrement();
	programCounter++;
	return programCounter;
    }

    public static Integer ST_OPERATION(Integer programCounter) throws Exception {
	// SEMANTICS: C(REG) --> EA

	realEA = virtualToRealAddress(EA);

	String str = REG[accumulator];
	if (MEMORY.memory(WRIT, realEA, str)) {
	    markPageAsWritten(EA);
	} else {
	    activeJOB.setHasError(true);
	    activeJOB.setErrorCode(ERROR_HANDLER.Error.eMemoryAddressFault);
	    FATAL_ERROR = true;
	}
	SYSTEM.systemCLOCK += ONE_CLOCK_UNIT;
	quantum += ONE_CLOCK_UNIT;
	activeJOB.incrementBurstTimeBy(ONE_CLOCK_UNIT);
	checkSystemOnClockIncrement();
	programCounter++;
	return programCounter;
    }

    public static Integer SHL_OPERATION(Integer programCounter) {
	// SEMANTICS: C(REG) shifted left EA places
	realEA = virtualToRealAddress(EA);
	if (MEMORY.memory(READ, realEA, "")) {

	    try {
		REG[accumulator] = Utilities
			.decimal2Binary((Utilities.binary2Decimal(REG[accumulator]) << realEA), 32);
	    } catch (Exception e) {
		activeJOB.setHasError(true);
		activeJOB.setErrorCode(ERROR_HANDLER.Error.eInputOutOfRange);
		FATAL_ERROR = true;
	    }
	} else {
	    activeJOB.setHasError(true);
	    activeJOB.setErrorCode(ERROR_HANDLER.Error.eMemoryAddressFault);
	    FATAL_ERROR = true;
	}
	SYSTEM.systemCLOCK += ONE_CLOCK_UNIT;
	quantum += ONE_CLOCK_UNIT;
	activeJOB.incrementBurstTimeBy(ONE_CLOCK_UNIT);
	checkSystemOnClockIncrement();
	programCounter++;
	return programCounter;
    }

    public static Integer SHR_OPERATION(Integer programCounter) {
	// SEMANTICS: C(REG) shifted right EA places
	realEA = virtualToRealAddress(EA);
	if (MEMORY.memory(READ, realEA, "")) {
	    try {
		REG[accumulator] = Utilities
			.decimal2Binary((Utilities.binary2Decimal(REG[accumulator]) >> realEA), 32);
	    } catch (Exception e) {
		activeJOB.setHasError(true);
		activeJOB.setErrorCode(ERROR_HANDLER.Error.eInputOutOfRange);
		FATAL_ERROR = true;
	    }
	} else {
	    activeJOB.setHasError(true);
	    activeJOB.setErrorCode(ERROR_HANDLER.Error.eMemoryAddressFault);
	    FATAL_ERROR = true;
	}
	SYSTEM.systemCLOCK += ONE_CLOCK_UNIT;
	quantum += ONE_CLOCK_UNIT;
	activeJOB.incrementBurstTimeBy(ONE_CLOCK_UNIT);
	checkSystemOnClockIncrement();
	programCounter++;
	return programCounter;
    }

    /**
     * Category : Virtual to Physical Address translation and validation
     */

    public static Integer virtualToRealAddress(Integer address) {

	Integer realAddress = -1;
	String virtualAddress = null;
	try {
	    virtualAddress = Utilities.decimal2Binary(address, 8);
	    String leftOfVirtualAddress = virtualAddress.substring(0, 4);
	    Integer pageIndex = Utilities.binary2Decimal(leftOfVirtualAddress);

	    if (pageIndex < CPU.activeJOB.getNoOfPgmPages()) {
		// 2. required page is a valid page, check if the page is
		// available
		// in memory
		PageEntry entry = activeJOB.getPageTable().get(pageIndex);
		if (entry.isInMemory) {
		    activeJOB.getPageTable().get(pageIndex).isReferenced = true;
		    SYSTEM.PCBList.replace(activeJOB.getJobID(), activeJOB);
		    String frame;
		    frame = Utilities.decimal2Binary(entry.pageNumberOnMemory, 4);
		    String rightOfVirtualAddress = virtualAddress.substring(4, 8);

		    char[] realAddressArray = new char[8];
		    int n = 0, i = 0, j = 0;
		    while (n < 8) {
			if (n < 4) {
			    realAddressArray[n] = frame.charAt(i);
			    i++;
			} else {
			    realAddressArray[n] = rightOfVirtualAddress.charAt(j);
			    j++;
			}
			n++;
		    }
		    String RA = new String(realAddressArray);
		    realAddress = Utilities.binary2Decimal(RA);
		} else {

		    activeJOB.incrementNumberOFPageFaultsBy(1);
		    quantum += CLOCK_ON_PF;
		    SYSTEM.systemCLOCK += CLOCK_ON_PF;
		    LOADER.replacePageForJobID(activeJOB.getJobID(), pageIndex);
		    SYSTEM.PCBList.replace(activeJOB.getJobID(), activeJOB);
		    // compute and return the real address
		    String frame = Utilities.decimal2Binary(entry.pageNumberOnMemory, 4);
		    String rightOfVirtualAddress = virtualAddress.substring(4, 8);
		    char[] realAddressArray = new char[8];
		    int n = 0, i = 0, j = 0;
		    while (n < 8) {
			if (n < 4) {
			    realAddressArray[n] = frame.charAt(i);
			    i++;
			} else {
			    realAddressArray[n] = rightOfVirtualAddress.charAt(j);
			    j++;
			}
			n++;
		    }
		    String RA = new String(realAddressArray);
		    realAddress = Utilities.binary2Decimal(RA);
		}
	    } else {
		activeJOB.setHasError(true);
		activeJOB.setErrorCode(ERROR_HANDLER.Error.eAddressOutOfRange);
		FATAL_ERROR = true;
	    }
	} catch (Exception e) {
	}

	return realAddress;
    }

    public static Integer getEffectiveAddress() {
	Integer realAddress = -1;
	if (isInDirectAddressing) {
	    Integer realAddressFromDADDR = virtualToRealAddress(DADDR);
	    if (realAddressFromDADDR != -1) {
		if (indexReg >= 1) {
		    // with both indirect and index addressing
		    if (MEMORY.memory(READ, realAddressFromDADDR, "")) {
			realAddress = Utilities.binary2Decimal(MEMORY.MEM_BUFFER_REG);
			realAddress = realAddress + Utilities.binary2Decimal(REG[indexReg]);
		    } else {
			activeJOB.setHasError(true);
			activeJOB.setErrorCode(ERROR_HANDLER.Error.eMemoryAddressFault);
			FATAL_ERROR = true;
		    }
		} else {
		    // with indirect addressing
		    if (MEMORY.memory(READ, realAddressFromDADDR, "")) {
			realAddress = Utilities.binary2Decimal(MEMORY.MEM_BUFFER_REG);
		    } else {
			activeJOB.setHasError(true);
			activeJOB.setErrorCode(ERROR_HANDLER.Error.eMemoryAddressFault);
			FATAL_ERROR = true;
		    }
		}
	    } else {
		activeJOB.setHasError(true);
		activeJOB.setErrorCode(ERROR_HANDLER.Error.eMemoryAddressFault);
		FATAL_ERROR = true;
	    }
	} else {
	    if (indexReg >= 1) {
		// with index addressing
		realAddress = Utilities.binary2Decimal(REG[indexReg]) + DADDR;
	    } else {
		// with immediate addressing
		realAddress = DADDR;
	    }
	}
	return realAddress;
    }

    public static boolean isValidEffectiveAddress(Integer EA) {
	boolean status = true;
	if (EA < 0 || EA >= MEMORY.MEMORY_SIZE) {
	    activeJOB.setHasError(true);
	    activeJOB.setErrorCode(ERROR_HANDLER.Error.eMemoryAddressFault);
	    FATAL_ERROR = true;
	    status = false;
	} else if (EA < activeJOB.getLoadAddress() || EA >= activeJOB.getUserJobLength()) {
	    activeJOB.setHasError(true);
	    activeJOB.setErrorCode(ERROR_HANDLER.Error.eAddressOutOfRange);
	    FATAL_ERROR = true;
	    status = false;
	}
	return status;
    }

    public static void markPageAsWritten(Integer address) {
	// passed address is EA
	Integer pageIndex = address / SYSTEM.PAGE_SIZE;
	activeJOB.getPageTable().get(pageIndex).isReferenced = true;
	activeJOB.getPageTable().get(pageIndex).isWritten = true;
	activeJOB.getPageTable().get(pageIndex).pageLoadTime = SYSTEM.systemCLOCK;
	SYSTEM.PCBList.replace(activeJOB.getJobID(), activeJOB);
    }

    public static void markPageAsReferenced(Integer address) {
	Integer pageIndex = address / SYSTEM.PAGE_SIZE;
	activeJOB.getPageTable().get(pageIndex).isReferenced = true;
	activeJOB.getPageTable().get(pageIndex).pageLoadTime = SYSTEM.systemCLOCK;
	SYSTEM.PCBList.replace(activeJOB.getJobID(), activeJOB);
    }

}// END_OF_CPU

enum opCode {
    HLT("00"), LD("01"), ST("02"), AD("03"), SB("04"), MPY("05"), 
	DIV("06"), SHL("07"),SHR("08"), BRM("09"), BRP("0A"), BRZ("0B"), 
	BRL("0C"), AND("0D"), OR("0E"), RD("0F"), WR("10"), DMP("11"), 
	INVALID_opCode("-1");

    private final String opCode;

    // A mapping between the String code and its corresponding opCode to
    // facilitate lookup by String.
    private static Map<String, opCode> stringToopCodeMapping;

    private opCode(String opCodeStr) {
	this.opCode = opCodeStr;
    }

    private static void initMapping() {
	stringToopCodeMapping = new HashMap<String, opCode>();
	for (opCode op : values()) {
	    stringToopCodeMapping.put(op.opCode, op);
	}
    }

    public static opCode getopCode(String s) {
	if (stringToopCodeMapping == null) {
	    initMapping();
	}
	return stringToopCodeMapping.get(s);
    }

    public String getopCode() {
	return this.opCode;
    }
}

class Queue {
    public Integer queueID;
    public Integer QUANTUM;
    public Integer TURN;
    public ArrayDeque<Integer> jobs;
    public ArrayList<Integer> queueSizes;

    public Queue(Integer id, Integer turn, Integer quantum) {
	queueID = id;
	QUANTUM = quantum;
	TURN = turn;
	jobs = new ArrayDeque<Integer>();
	queueSizes = new ArrayList<>();
    }

    public void setQueueID(Integer x) {
	queueID = x;
    }

    public Integer getQueueID() {
	return queueID;
    }

    public void setQUANTUM(Integer x) {
	QUANTUM = x;
    }

    public Integer getQUANTUM() {
	return QUANTUM;
    }

    public void setTURN(Integer x) {
	TURN = x;
    }

    public Integer getTURN() {
	return TURN;
    }

    public void setJobs(ArrayDeque<Integer> x) {
	jobs = x;
    }

    public ArrayDeque<Integer> getJobs() {
	return jobs;
    }

    public ArrayList<Integer> getQueueSizes() {
	return queueSizes;
    }
}
