/**
 * MEMORY MODULE : MEMORY MODULE is responsible for managing the MEMORY. Any
 * value updates or read or dump from the memory is through the MEMORY()
 * routine.
 * 
 * GLOBAL VARIABLES: MEM: Is an array of size 256, simulating the memory in the
 * SYSTEM. MEM_BUFFER_REG : Is a memory buffer register, to be written or read
 * into
 */

public class MEMORY {

    public static final int MEMORY_SIZE = 256;
    public static final int MEMORY_BUFFER_REG_SIZE_IN_BITS = 32;
    public static String[] MEM = new String[256];
    public static String MEM_BUFFER_REG = "0";

    static {
	MEM_BUFFER_REG = "0";
	for (int i = 0; i < MEM.length; i++) {
	    MEM[i] = "0";
	}
    }

    /**
     * This method is responsible for performing read or write or dump operation
     * on memory
     * 
     * @param X
     *            - control signal "READ" or "WRIT" or "DUMP"
     * @param Y
     *            - effective address
     * @param Z
     *            - variable to be read or write
     * @return boolean - returns the error status in the function if any
     */
    public static boolean memory(String X, Integer Y, String Z) {
	boolean status = true;

	MEM_BUFFER_REG = Z;
	try {
	    if (X.equalsIgnoreCase("READ")) {
		MEM_BUFFER_REG = MEM[Y];
	    } else if (X.equalsIgnoreCase("WRIT")) {
		MEM[Y] = MEM_BUFFER_REG;
	    } else if (X.equalsIgnoreCase("DUMP")) {
		SYSTEM.progressFileString.append(String.format("\n %-10s  %-10s  %-10s  %-10s  %-10s %-10s  %-10s  %-10s  %-10s %n", "(HEX)", "(HEX)",
			"(HEX)", "(HEX)", "(HEX)", "(HEX)", "(HEX)", "(HEX)", "(HEX)"));

		for (int index = 0; index < MEM.length; index++) {
		    int numberOfWordInOneLine = 0;
		    String str = Utilities.decimal2hex(index, 2);

		    SYSTEM.progressFileString.append(String.format(" %-10s ", str));
		    while (numberOfWordInOneLine < 8) {
			SYSTEM.progressFileString.append(String.format(" %-10s ", Utilities.binary2Hex(MEM[index], 8)));
			numberOfWordInOneLine++;
			if (numberOfWordInOneLine != 8) {
			    index++;
			}
		    }
		    SYSTEM.progressFileString.append(String.format(" %n"));
		}

		SYSTEM.writeProgressFile();
		SYSTEM.progressFileString.delete(0, SYSTEM.progressFileString.length());
	    }
	} catch (ArrayIndexOutOfBoundsException e) {
	    status = false;
	} catch (Exception e) {
	}
	return status;
    }
}
