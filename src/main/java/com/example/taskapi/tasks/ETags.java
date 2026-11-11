package com.example.taskapi.tasks;

/** Strong ETags derived from the JPA @Version of a task. */
final class ETags {

    private ETags() {}

    static String of(long version) {
        return "\"" + version + "\"";
    }

    /**
     * RFC 9110 If-Match evaluation with strong comparison. A missing header
     * means "unconditional"; weak tags never match.
     */
    static boolean ifMatchSatisfied(String ifMatch, long currentVersion) {
        if (ifMatch == null) {
            return true;
        }
        String current = of(currentVersion);
        for (String candidate : ifMatch.split(",")) {
            String tag = candidate.trim();
            if (tag.equals("*") || tag.equals(current)) {
                return true;
            }
        }
        return false;
    }
}
