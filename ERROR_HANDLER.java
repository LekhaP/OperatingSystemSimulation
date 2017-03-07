
/**
 * ERROR_HANDLER MODULE :
 * 		ERROR_HANDLER will hold code, label and descriptions of errors and warnigs.
 * GLOBAL VARIABLES:
 * 		Error: Error is global enum, storing the code, label and description of errors. 
 * 		Warning: Warning is global enum, storing the code, label and description of warnings. 
 */

import java.util.HashMap;
import java.util.Map;

public class ERROR_HANDLER {

    public static enum Error {

    eInputOutOfRange(110, "eInputOutOfRange","ERROR: GIVEN INPUT IS OUT OF RANGE"),
    eFileNotFound(111,"eFileNotFound","ERROR:FILE NOT FOUND IN THE SPECIFIED PATH"),
    eInvalidLoaderFormatCharacter(112, "eInvalidLoaderFormatCharacter","ERROR:INVALID LOADER FORMAT CHARACTER"),
    eInvalidOpcode(113, "eInvalidOpcode","ERROR: INVALID OPCODE IDENTIFIED"),
    eInvalidInstructionFormat(114,"eInvalidInstructionFormat","ERROR:INVALID INSTRUCTION FORMAT"),
    eDivideByZero(	115, "eDivideByZero","ERROR: ATTEMPT TO DIVIDE BY ZERO"),
    eMemoryAddressFault(116,"eMemoryAddressFault","ERROR: MEMORY_REFERENCE_ERROR:MEMORY ADDRESS OUT OF RANGE"),
    eAddressOutOfRange(117,"eAddressOutOfRange","ERROR: ADDRESS OUT OF JOB"),
    eInvalidUserInput(118,"eInvalidUserInput","ERROR: INVALID USER INPUT"),
    eUnKnownError(119,"eUnKnownError","ERROR: UNKNOWN_ERROR_FOUND"),
    eNullPointerError(120,"eNullPointerError","ERROR: NULL POINTER ERROR"),
    eMissingJOBTag(122,"eMissingJOBTag","ERROR: Missing **JOB Tag"),
    eMissingDATATag(123,"eMissingDATATag","ERROR: Missing **DATA Tag"),
    eMisssingDATA(125,"eMisssingDATA","ERROR: Missing DATA"),
    eNullJob(126,"eNullJob","ERROR : NULL JOB"),
    eMissingProgram(127,"eMissingProgram","ERROR: Missing Program"),
    eInsufficientOutputSpace(128,"eInsufficientOutputSpace","ERROR: Insufficient Output space"),
    eBadCharacterEncounteredByLoader(129,"eBadCharacterEncounteredByLoader","ERROR: Bad Character Encountered By Loader"),
    eSuspectedInfiniteJob(130,"eSuspectedInfiniteJob","ERROR: SUSPECTED INFINITE JOB"),
    eProgramSizeTooLarge(131,"eProgramSizeTooLarge","ERROR: PROGRAM SIZE IS TOO LARGE"),
    eOverFlow(132,"eOverFlow","ERROR: OVERFLOW");


	private int code;
	private String label;
	private String description;

	public int getCode() {
	    return code;
	}

	public String getLabel() {
	    return label;
	}

	public String getDescription() {
	    return description;
	}

	// A mapping between the integer code and its corresponding Status to
	// facilitate lookup by code.
	private static Map<Integer, Error> codeToStatusMapping;

	private Error(int code, String label, String description) {
	    this.code = code;
	    this.label = label;
	    this.description = description;
	}

	public static Error getStatus(int i) {
	    if (codeToStatusMapping == null) {
		initMapping();
	    }
	    return codeToStatusMapping.get(i);
	}

	private static void initMapping() {
	    codeToStatusMapping = new HashMap<Integer, Error>();
	    for (Error s : values()) {
		codeToStatusMapping.put(s.code, s);
	    }
	}

	@Override
	public String toString() {
	    final StringBuilder sb = new StringBuilder();
	    sb.append("Status");
	    sb.append("{[ERROR]: code=").append(code);
	    sb.append(", label='").append(label).append('\'');
	    sb.append(", description='").append(description).append('\'');
	    sb.append('}');
	    return sb.toString();
	}
    }

    /**
     * This method returns the error message based on the errorCode received
     * 
     * @param errorCode
     * @return errorMsg
     */
    public static void showError(Error errorCode) {

	switch (errorCode) {
	case eFileNotFound:
	    SYSTEM.progressFileString.append(Error.eFileNotFound);
	    break;
	case eInvalidLoaderFormatCharacter:
	    SYSTEM.progressFileString.append(Error.eInvalidLoaderFormatCharacter);
	    break;
	case eInvalidOpcode:
	    SYSTEM.progressFileString.append(Error.eInvalidOpcode);
	    break;
	case eInvalidInstructionFormat:
	    SYSTEM.progressFileString.append(Error.eInvalidInstructionFormat);
	    break;
	case eDivideByZero:
	    SYSTEM.progressFileString.append(Error.eDivideByZero);
	    break;
	case eMemoryAddressFault:
	    SYSTEM.progressFileString.append(Error.eMemoryAddressFault);
	    break;
	case eAddressOutOfRange:
	    SYSTEM.progressFileString.append(Error.eAddressOutOfRange);
	    break;
	case eInvalidUserInput:
	    SYSTEM.progressFileString.append(Error.eInvalidUserInput);
	    break;
	case eMissingJOBTag:
	    SYSTEM.progressFileString.append(Error.eMissingJOBTag);
	    break;
	case eMissingDATATag:
	    SYSTEM.progressFileString.append(Error.eMissingDATATag);
	    break;
	case eMisssingDATA:
	    SYSTEM.progressFileString.append(Error.eMisssingDATA);
	    break;
	case eNullJob:
	    SYSTEM.progressFileString.append(Error.eNullJob);
	    break;
	case eMissingProgram:
	    SYSTEM.progressFileString.append(Error.eMissingProgram);
	    break;
	case eInsufficientOutputSpace:
	    SYSTEM.progressFileString.append(Error.eInsufficientOutputSpace);
	    break;
	case eBadCharacterEncounteredByLoader:
	    SYSTEM.progressFileString.append(Error.eBadCharacterEncounteredByLoader);
	    break;
	case eSuspectedInfiniteJob:
	    SYSTEM.progressFileString.append(Error.eSuspectedInfiniteJob);
	    break;
	case eProgramSizeTooLarge:
	    SYSTEM.progressFileString.append(Error.eProgramSizeTooLarge);
	    break;
	case eOverFlow:
	    SYSTEM.progressFileString.append(Error.eOverFlow);
	    break;
	case eUnKnownError:
	    SYSTEM.progressFileString.append(Error.eUnKnownError);
	    break;
	case eNullPointerError:
	    SYSTEM.progressFileString.append(Error.eNullPointerError);
	    break;

	default:
	    SYSTEM.progressFileString.append(Error.eUnKnownError);
	    break;
	}
	SYSTEM.progressFileString.append("\n");
    }

    public static enum Warning {

	eInvalidTraceSwitch(222, "eInvalidTraceSwitch",
		"WARNING: Invalid trace switch, allowed values are 0 or 1"), eMissingFINTag(121,
			"eMissingFINTag", "WARNING: Missing **FIN Tag"), eDoubleDATATag(124,
				"eDoubleDATATag", "WARNING: Double DATA Tag");

	private int code;
	private String label;
	private String description;

	public int getCode() {
	    return code;
	}

	public String getLabel() {
	    return label;
	}

	public String getDescription() {
	    return description;
	}

	// A mapping between the integer code and its corresponding Status to
	// facilitate lookup by code.
	private static Map<Integer, Warning> codeToStatusMapping;

	private Warning(int code, String label, String description) {
	    this.code = code;
	    this.label = label;
	    this.description = description;
	}

	public static Warning getCode(int i) {
	    if (codeToStatusMapping == null) {
		initMapping();
	    }
	    return codeToStatusMapping.get(i);
	}

	private static void initMapping() {
	    codeToStatusMapping = new HashMap<Integer, Warning>();
	    for (Warning s : values()) {
		codeToStatusMapping.put(s.code, s);
	    }
	}

	@Override
	public String toString() {
	    final StringBuilder sb = new StringBuilder();
	    sb.append("Status");
	    sb.append("{code=").append(code);
	    sb.append(", label='").append(label).append('\'');
	    sb.append(", description='").append(description).append('\'');
	    sb.append('}');
	    return sb.toString();
	}
    }

    /**
     * This method returns the warning message based on the warningCode received
     * 
     * @param warningCode
     * @return warningMessage
     */
    public static void showWarning(Warning warningCode) {
	switch (warningCode) {
	case eInvalidTraceSwitch:
	    SYSTEM.progressFileString.append(Warning.eInvalidTraceSwitch + "\n");
	    break;
	case eMissingFINTag:
	    SYSTEM.progressFileString.append(Warning.eMissingFINTag + "\n");
	    break;
	case eDoubleDATATag:
	    SYSTEM.progressFileString.append(Warning.eDoubleDATATag);
	    break;
	default:
	    break;
	}
    }
}
