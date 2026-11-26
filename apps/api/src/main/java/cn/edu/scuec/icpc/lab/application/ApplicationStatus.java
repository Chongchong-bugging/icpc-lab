package cn.edu.scuec.icpc.lab.application;

/** 申请状态机 7 态（DOM-07）。 */
public final class ApplicationStatus {

    public static final String DRAFT = "DRAFT";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String PROCESSING = "PROCESSING";
    public static final String RETURNED = "RETURNED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String WITHDRAWN = "WITHDRAWN";

    private ApplicationStatus() {
    }

    public static boolean terminal(String status) {
        return APPROVED.equals(status) || REJECTED.equals(status) || WITHDRAWN.equals(status);
    }
}