
/**
 * DISK_MANAGER MODULE :
 * 		1.DISK_MANAGER module load the input jobs to disk.
 * 		2. determines load time error.
 * 		3. DISK is using paging and MFT for disk management
 * 		4. Every time a job is spooled out, new job is loaded to disk if there is any new 
 * 		    job available.
 */
import java.util.ArrayList;
import java.util.Collections;

class DISK_MANAGER {

    public static final int DISK_SIZE = 4096;
    public static String[] DISK = new String[DISK_SIZE];
    public static ArrayList<Integer> availablePagesOnDisk = new ArrayList<>();
    public static ArrayList<Integer> jobsOnDisks = new ArrayList<>();
    public static ArrayList<Double> percentageOccupiedOnDisk = new ArrayList<>();
    public static boolean shouldComoputeDiskUtilization = false;

    static {
	for (int i = 0; i < DISK.length; i++) {
	    DISK[i] = "0";
	}

	int i = 0;
	int j = 0;
	while (i < DISK.length) {
	    availablePagesOnDisk.add(j);
	    i = i + 16;
	    j++;
	}
    }

    /**
     * This method resets the global variables of DISK_MANAGER for the next
     * combinations of CPU turns and Quantum size
     */
    public static void resetDisk() {

	availablePagesOnDisk.clear();
	jobsOnDisks.clear();
	percentageOccupiedOnDisk.clear();
	shouldComoputeDiskUtilization = false;

	for (int i = 0; i < DISK.length; i++) {
	    DISK[i] = "0";
	}

	int i = 0;
	int j = 0;
	while (i < DISK.length) {
	    availablePagesOnDisk.add(j);
	    i = i + 16;
	    j++;
	}
    }

    public static PCB spoolJobOnDisk(JOB job, PCB pcb) {
	Integer pageNo = 0;
	boolean didLoadInst = false;
	boolean didLoadData = false;
	ArrayList<PageEntry> pageTable = pcb.getPageTable();
	try {
	    while (pageNo < pcb.getTotalNoOfPages()) {

		if (didLoadInst && didLoadData) {
		    Integer availablePageOnDisk = availablePagesOnDisk.get(0);
		    availablePagesOnDisk.remove(0);
		    Integer loadAddress = availablePageOnDisk * SYSTEM.PAGE_SIZE;
		    PageEntry entry = new PageEntry(2, false, false, false, availablePageOnDisk, -1);
		    pageTable.add(entry);
		    if (pcb.getAddressOfCurrentWRdataRecord() == -1) {
			pcb.setAddressOfCurrentWRdataRecord(loadAddress);
			pcb.setCurrentWRdataPageNo(pageNo);
		    }

		    int n = 0;
		    while (n < 16) {
			DISK[loadAddress++] = "0";
			n++;
		    }
		    pageNo++;

		} else if (!didLoadInst) {
		    Integer IC = 0;
		    while (IC < job.instructions.size()) {
			Integer availablePageOnDisk = availablePagesOnDisk.get(0);
			availablePagesOnDisk.remove(0);
			Integer loadAddress = availablePageOnDisk * SYSTEM.PAGE_SIZE;
			PageEntry entry = new PageEntry(0, false, false, false, availablePageOnDisk, -1);
			pageTable.add(entry);
			int n = 0;
			while (n < 16) {
			    if (IC < job.instructions.size()) {
				DISK[loadAddress++] = Utilities.hex2Binary(job.instructions.get(IC++));
			    } else {
				DISK[loadAddress++] = "0";
			    }
			    n++;
			}
			pageNo++;
		    }
		    didLoadInst = true;
		} else if (!didLoadData) {
		    if (job.data.size() == 0) {
			didLoadData = true;
			continue;
		    }
		    Integer DC = 0;
		    while (DC < job.data.size()) {
			Integer availablePageOnDisk = availablePagesOnDisk.get(0);
			availablePagesOnDisk.remove(0);
			Integer loadAddress = availablePageOnDisk * SYSTEM.PAGE_SIZE;
			PageEntry entry = new PageEntry(1, false, false, false, availablePageOnDisk, -1);
			pageTable.add(entry);

			if (pcb.getAddressOfCurrentRDdataRecord() == -1) {
			    pcb.setddressOfCurrentRDdataRecord(loadAddress);
			    pcb.setCurrentRDdataPageNo(pageNo);
			}
			int n = 0;
			while (n < 16) {
			    if (DC < job.data.size()) {
				DISK[loadAddress++] = Utilities.hex2Binary(job.data.get(DC++));
			    } else {
				DISK[loadAddress++] = "00000000";
			    }
			    n++;
			}
			pageNo++;
		    }
		    didLoadData = true;
		}
	    }
	} catch (Exception e) {
	    pcb.setHasError(true);
	    pcb.setErrorCode(ERROR_HANDLER.Error.eInvalidLoaderFormatCharacter);
	}
	pcb.setPageTable(pageTable);
	return pcb;
    }

    public static void loadJobsToDisk() {

	Collections.sort(availablePagesOnDisk);
	boolean hasDiskSpace = (availablePagesOnDisk.size() > 0) ? true : false;
	while (SYSTEM.noOfInputJobsRead < SYSTEM.allJobs.size() && hasDiskSpace) {

	    JOB job = SYSTEM.allJobs.get(SYSTEM.noOfInputJobsRead);

	    PCB pcb = new PCB();
	    pcb.setJobID(job.jobID);
	    pcb.setOutputLineLimit(job.yy);
	    pcb.setLoadAddress(job.loadAddress);
	    pcb.setUserJobLength(job.userJobLength);
	    pcb.setStartAddress(job.startAddress);
	    pcb.setTraceSwitch(job.traceSwitch);
	    pcb.setHasError(job.hasLoadTimeError);
	    pcb.setHasWarning(job.hasLoadTimeWarning);
	    pcb.setErrorCode(job.errorCode);
	    pcb.setWarningCode(job.warningCode);

	    int count = (job.instructions.size() / 16);
	    if ((job.instructions.size() % 16) != 0) {
		count += 1;
	    }
	    pcb.setNoOfPgmPages(count);

	    count = (job.data.size() / 16);
	    if ((job.data.size() % 16) != 0) {
		count += 1;
	    }
	    pcb.setNoOfDataPages(count);

	    count = ((job.yy * 4) / 16);
	    if ((job.yy * 4) % 16 != 0) {
		count += 1;
	    }
	    pcb.setNoOfOutputLinePages(count);

	    count = pcb.getNoOfPgmPages() + pcb.getNoOfDataPages() + pcb.getNoOfOutputLinePages();
	    pcb.setTotalNoOfPages(count);

	    hasDiskSpace = (availablePagesOnDisk.size() < pcb.getTotalNoOfPages()) ? false : true;
	    if (hasDiskSpace) {

		SYSTEM.systemCLOCK++;
		// Update the progress file on job arrival
		SYSTEM.progressFileString.append("\n\nJOB IDEBTIFICATION NUMBER(DECIMAL): " + pcb.getJobID());
		SYSTEM.progressFileString.append("\nSTATUS: LOADED INTO SYSTEM");
		SYSTEM.progressFileString.append("\nCURRENT_CLOCK_TIME(DECIMAL): " + SYSTEM.systemCLOCK);
		if (pcb.getHasWarning() && pcb.getWarningCode() != null) {
		    SYSTEM.progressFileString.append("\nJOB_ID :" + pcb.getJobID() + "  " + pcb.getWarningCode());
		}

		if (pcb.getHasError()) {
		    pcb.setIsTerminated(true);
		    SYSTEM.PCBList.put(pcb.getJobID(), pcb);
		    if (pcb.getHasError() || pcb.getErrorCode() != null) {
			SYSTEM.progressFileString.append("\n" + pcb.getErrorCode());
			SYSTEM.progressFileString.append("\nABNORMAL TERMINATION");
		    }
		    SYSTEM.writeProgressFile();
		    SYSTEM.noOfInputJobsRead++;
		    continue;
		} else {
		    // validate instruction and data
		    boolean hasLoadTimeError = false;
		    int n = 0;
		    while (n < job.instructions.size()) {
			String str = Utilities.hex2Binary(job.instructions.get(n));
			if (str == "-1") {
			    hasLoadTimeError = true;
			    break;
			}
			n++;
		    }

		    if (hasLoadTimeError == false) {
			n = 0;
			while (n < job.data.size()) {
			    String str = Utilities.hex2Binary(job.data.get(n));
			    if (str == "-1") {
				hasLoadTimeError = true;
				break;
			    }
			    n++;
			}
		    }

		    if (hasLoadTimeError == true) {
			pcb.setHasError(true);
			pcb.setErrorCode(ERROR_HANDLER.Error.eInvalidLoaderFormatCharacter);
			pcb.setIsTerminated(true);
			SYSTEM.PCBList.put(pcb.getJobID(), pcb);
			if (pcb.getHasError() || pcb.getErrorCode() != null) {
			    SYSTEM.progressFileString.append("\n" + pcb.getErrorCode());
			    SYSTEM.progressFileString.append("\nABNORMAL TERMINATION");
			}
			SYSTEM.writeProgressFile();
			SYSTEM.noOfInputJobsRead++;
			continue;
		    } else {
			pcb = spoolJobOnDisk(job, pcb);
			SYSTEM.writeProgressFile();
			SYSTEM.PCBList.put(pcb.getJobID(), pcb);
			jobsOnDisks.add(pcb.getJobID());
			SYSTEM.noOfInputJobsRead++;
		    }
		}
	    } else {
		break;
	    }
	    computeDiskUtilization();
	}
    }

    public static void computeDiskUtilization() {
	if (shouldComoputeDiskUtilization) {
	    Double diskInUse = (double) (DISK_MANAGER.DISK_SIZE - (DISK_MANAGER.availablePagesOnDisk.size() * 16));
	    Double diskInUsePercent = (diskInUse * 100) / DISK_MANAGER.DISK_SIZE;
	    DISK_MANAGER.percentageOccupiedOnDisk.add(diskInUsePercent);
	}
    }

    public static void unloadJobOnDisk(Integer jobID) {
	PCB pcb = SYSTEM.PCBList.get(jobID);
	ArrayList<PageEntry> pageTable = pcb.getPageTable();
	for (int i = 0; i < pageTable.size(); i++) {
	    PageEntry entry = pageTable.get(i);
	    if (entry.isProgram != 3) {
		Integer diskPageToUnload = entry.pageNumberOnDisk;
		Integer unloadAddress = diskPageToUnload * SYSTEM.PAGE_SIZE;
		int n = 0;
		while (n < 16) {
		    DISK[unloadAddress] = "0";
		    unloadAddress++;
		    n++;
		}
		availablePagesOnDisk.add(diskPageToUnload);
	    }
	}
	jobsOnDisks.remove(jobID);
	Collections.sort(availablePagesOnDisk);
    }
}