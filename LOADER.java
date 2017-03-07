
/**
 * LOADER MODULE :
 * 		LOADER MODULE reads and loads disk jobs on memory 
 * 		and also takes care of page replacement algorithm
 */

import java.util.ArrayList;
import java.util.Collections;

public class LOADER {

    public static ArrayList<Integer> availablePagesInMemory = new ArrayList<>();
    public static ArrayList<Integer> jobsOnMemory = new ArrayList<>();
    public static Integer noOfDiskJobsRead = 0;

    static {
	int i = 0;
	int j = 0;
	while (i < MEMORY.MEM.length) {
	    availablePagesInMemory.add(j);
	    i = i + 16;
	    j++;
	}
    }

    /**
     * This method reset the loader for nextBatch of jobs processing
     */
    public static void resetLoader() {

	availablePagesInMemory.clear();
	jobsOnMemory.clear();
	noOfDiskJobsRead = 0;
	int i = 0;
	int j = 0;
	while (i < MEMORY.MEM.length) {
	    availablePagesInMemory.add(j);
	    i = i + 16;
	    j++;
	}
    }

    /**
     * This method loads the job from the input file into loaderBuffer and then
     * writes in to memory using memory(X, Y, Z)
     * 
     * @param X
     *            - starting address
     * @param Y
     *            - trace switch
     */
    public static void loader() {

	Collections.sort(availablePagesInMemory);
	boolean memorySpace = (availablePagesInMemory.size() > 0) ? true : false;
	while (DISK_MANAGER.jobsOnDisks.size() > 0 && memorySpace) {

	    Integer nextJobID = -1;
	    // find the job on disk that is not in memory
	    for (Integer jobid : DISK_MANAGER.jobsOnDisks) {
		if (jobsOnMemory.contains(jobid)) {
		    continue;
		} else {
		    nextJobID = jobid;
		    break;
		}
	    }

	    if (nextJobID == -1) {
		break;
	    } else {

		// Add the job to memory
		PCB pcbOfNextJobID = SYSTEM.PCBList.get(nextJobID);
		Integer oneThird = (pcbOfNextJobID.getNoOfPgmPages() / 3);
		if (pcbOfNextJobID.getNoOfPgmPages() % 3 != 0) {
		    oneThird++;
		}
		Integer numberOfMemoryPagesRequired = Math.max(2, oneThird);
		memorySpace = (availablePagesInMemory.size() < numberOfMemoryPagesRequired) ? false : true;
		if (memorySpace) {
		    loadJobOnMemory(pcbOfNextJobID);
		    jobsOnMemory.add(nextJobID);
		    noOfDiskJobsRead++;
		} else {
		    break;
		}
	    }
	}
    }

    public static void loadJobOnMemory(PCB pcb) {
	Integer oneThird = (pcb.getNoOfPgmPages() / 3);
	if (pcb.getNoOfPgmPages() % 3 != 0) {
	    oneThird++;
	}
	pcb.setFramesNeeded(Math.max(2, oneThird));
	Integer noOfPages = 0;
	while (noOfPages < pcb.getFramesNeeded()) {
	    loadPageForJobID(pcb.getJobID(), noOfPages);
	    noOfPages++;
	}

	SYSTEM.systemCLOCK++;
	pcb.setArrivalTime(SYSTEM.systemCLOCK);
	pcb.setCurrentQueue(1);
	CPU.ready_queues.get(1).getJobs().addLast(pcb.getJobID());
	SYSTEM.PCBList.replace(pcb.getJobID(), pcb);

	// REMOVE START
	SYSTEM.progressFileString.append("\n\nJOB IDEBTIFICATION NUMBER : " + pcb.getJobID());
	SYSTEM.progressFileString.append("\nSTATUS: LOADED INTO MEMORY");
	SYSTEM.progressFileString.append("\nCURRENT_CLOCK_TIME(DECIMAL): " + pcb.getArrivalTime());
	SYSTEM.writeProgressFile();
	// REMOVE END

    }

    public static void loadPageForJobID(Integer jobID, Integer pageNo) {

	PCB pcb = SYSTEM.PCBList.get(jobID);

	if (pageNo < pcb.getPageTable().size()) {
	    PageEntry entry = pcb.getPageTable().get(pageNo);

	    Integer availablePageOnMemory = availablePagesInMemory.get(0);
	    availablePagesInMemory.remove(0);

	    Integer loadAddressInMemory = availablePageOnMemory * SYSTEM.PAGE_SIZE;
	    Integer loadAddressFromDisk = entry.pageNumberOnDisk * SYSTEM.PAGE_SIZE;
	    entry.pageNumberOnMemory = availablePageOnMemory;
	    entry.isInMemory = true;
	    entry.pageLoadTime = pageNo;
	    pcb.getPageTable().set(pageNo, entry);

	    int n = 0;
	    while (n < 16) {
		MEMORY.MEM[loadAddressInMemory] = DISK_MANAGER.DISK[loadAddressFromDisk];
		loadAddressInMemory++;
		loadAddressFromDisk++;
		n++;
	    }
	} else {

	    Integer availablePageOnMemory = availablePagesInMemory.get(0);
	    availablePagesInMemory.remove(0);
	    Integer loadAddressInMemory = availablePageOnMemory * SYSTEM.PAGE_SIZE;
	    PageEntry entry = new PageEntry(3, true, false, false, -1, -1);
	    entry.pageNumberOnMemory = availablePageOnMemory;
	    entry.isInMemory = true;
	    entry.pageLoadTime = pageNo;
	    pcb.getPageTable().add(entry);

	    int n = 0;
	    while (n < 16) {
		MEMORY.MEM[loadAddressInMemory] = "0";
		loadAddressInMemory++;
		n++;
	    }
	}
	SYSTEM.PCBList.replace(jobID, pcb);
    }

    public static void replacePageForJobID(Integer jobID, Integer pageNo) {

	Integer pageToBeReplaced = selectPageForReplacementForJobID(jobID);
	PCB pcb = SYSTEM.PCBList.get(jobID);

	PageEntry outPageEntry = pcb.getPageTable().get(pageToBeReplaced);
	if (outPageEntry.isWritten) {
	    // write the page back to disk before replacement
	    Integer memoryAddress = outPageEntry.pageNumberOnMemory * SYSTEM.PAGE_SIZE;
	    Integer diskAddress = outPageEntry.pageNumberOnDisk * SYSTEM.PAGE_SIZE;
	    int n = 0;
	    while (n < 16) {
		DISK_MANAGER.DISK[diskAddress] = MEMORY.MEM[memoryAddress];
		memoryAddress++;
		diskAddress++;
		n++;
	    }
	}

	PageEntry inPageEntry = pcb.getPageTable().get(pageNo);
	inPageEntry.isInMemory = true;
	inPageEntry.isReferenced = true;
	inPageEntry.isWritten = false;
	inPageEntry.pageNumberOnMemory = outPageEntry.pageNumberOnMemory;
	inPageEntry.pageLoadTime = SYSTEM.systemCLOCK;
	pcb.getPageTable().set(pageNo, inPageEntry);

	// reset the page being removed from
	outPageEntry.isInMemory = false;
	outPageEntry.isReferenced = false;
	outPageEntry.isWritten = false;
	outPageEntry.pageNumberOnMemory = -1;
	outPageEntry.pageLoadTime = -1;
	pcb.getPageTable().set(pageToBeReplaced, outPageEntry);

	Integer loadAddressInMemory = inPageEntry.pageNumberOnMemory * SYSTEM.PAGE_SIZE;
	Integer loadAddressFromDisk = inPageEntry.pageNumberOnDisk * SYSTEM.PAGE_SIZE;
	int n = 0;
	while (n < 16) {
	    MEMORY.MEM[loadAddressInMemory] = DISK_MANAGER.DISK[loadAddressFromDisk];
	    loadAddressInMemory++;
	    loadAddressFromDisk++;
	    n++;
	}
	SYSTEM.PCBList.replace(pcb.getJobID(), pcb);
    }

    public static Integer selectPageForReplacementForJobID(Integer jobId) {

	PCB activeJOB = SYSTEM.PCBList.get(jobId);

	Integer PageToBeReplaced = 0;
	ArrayList<Integer> candidatePageForReplacement = new ArrayList<>();
	boolean pageFound = false;
	// find(0,0)
	for (int n = 0; n < activeJOB.getPageTable().size(); n++) {
	    PageEntry entry = activeJOB.getPageTable().get(n);
	    if (entry.isInMemory && !entry.isReferenced && !entry.isWritten) {
		candidatePageForReplacement.add(n);
	    }
	}

	// check: if page to be replaced is found
	if (candidatePageForReplacement.size() > 0) {
	    pageFound = true;
	} else {
	    // find(1,0)
	    for (int n = 0; n < activeJOB.getPageTable().size(); n++) {
		PageEntry entry = activeJOB.getPageTable().get(n);
		if (entry.isInMemory && entry.isReferenced && !entry.isWritten) {
		    candidatePageForReplacement.add(n);
		}
	    }
	    if (candidatePageForReplacement.size() > 0) {
		pageFound = true;
	    } else {
		// find(1,1)
		for (int n = 0; n < activeJOB.getPageTable().size(); n++) {
		    PageEntry entry = activeJOB.getPageTable().get(n);
		    if (entry.isInMemory && entry.isReferenced && entry.isWritten) {
			candidatePageForReplacement.add(n);
		    }
		}
	    }
	}

	if (pageFound) {
	    if (candidatePageForReplacement.size() == 1) {
		PageToBeReplaced = candidatePageForReplacement.get(0);
	    } else {
		// select among candidate(tie breaker)
		Integer oldestPage = candidatePageForReplacement.get(0);
		Integer minTime = activeJOB.getPageTable().get(oldestPage).pageLoadTime;

		for (int i = 0; i < candidatePageForReplacement.size(); i++) {
		    PageEntry curEntry = activeJOB.getPageTable().get(candidatePageForReplacement.get(i));
		    if (curEntry.pageLoadTime < minTime) {
			oldestPage = candidatePageForReplacement.get(i);
			minTime = curEntry.pageLoadTime;
		    }
		}
		PageToBeReplaced = oldestPage;
	    }
	} else {

	    ArrayList<PageEntry> pageTable = activeJOB.getPageTable();
	    for (int i = 0; i < pageTable.size(); i++) {
		PageEntry entry = pageTable.get(i);
		if (entry.isInMemory) {
		    Integer memoryAddress = entry.pageNumberOnMemory * SYSTEM.PAGE_SIZE;
		    Integer diskAddress = entry.pageNumberOnDisk * SYSTEM.PAGE_SIZE;
		    int n = 0;
		    while (n < 16) {
			DISK_MANAGER.DISK[diskAddress] = MEMORY.MEM[memoryAddress];
			memoryAddress++;
			diskAddress++;
			n++;
		    }
		    entry.isWritten = false;
		    pageTable.set(i, entry);
		    candidatePageForReplacement.add(i);
		}
	    }
	    activeJOB.setPageTable(pageTable);
	    SYSTEM.PCBList.replace(activeJOB.getJobID(), activeJOB);
	    // select among candidate(tie breaker)
	    Integer oldestPage = candidatePageForReplacement.get(0);
	    Integer minTime = activeJOB.getPageTable().get(oldestPage).pageLoadTime;

	    for (int i = 0; i < candidatePageForReplacement.size(); i++) {
		PageEntry curEntry = activeJOB.getPageTable().get(candidatePageForReplacement.get(i));
		if (curEntry.pageLoadTime < minTime) {
		    oldestPage = candidatePageForReplacement.get(i);
		    minTime = curEntry.pageLoadTime;
		}
	    }
	    PageToBeReplaced = oldestPage;
	}
	return PageToBeReplaced;
    }

    public static void unloadJobOnMemory(Integer jobID) {
	PCB pcb = SYSTEM.PCBList.get(jobID);
	for (int i = 0; i < pcb.getPageTable().size(); i++) {
	    PageEntry entry = pcb.getPageTable().get(i);
	    if (entry.isInMemory) {
		Integer memoryPageToUnload = entry.pageNumberOnMemory;
		Integer unloadAddress = memoryPageToUnload * SYSTEM.PAGE_SIZE;
		int n = 0;
		while (n < 16) {
		    MEMORY.MEM[unloadAddress] = "0";
		    unloadAddress++;
		    n++;
		}
		availablePagesInMemory.add(memoryPageToUnload);
	    }
	}
	jobsOnMemory.remove(jobID);
	Collections.sort(availablePagesInMemory);
    }
}