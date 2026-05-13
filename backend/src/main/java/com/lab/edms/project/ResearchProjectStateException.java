package com.lab.edms.project;

public class ResearchProjectStateException extends RuntimeException {
    public ResearchProjectStateException(String projectCode,
                                         ResearchProjectStatus current,
                                         String operation) {
        super("Project '" + projectCode + "' cannot be '" + operation
              + "' from status " + current);
    }
}
