package com.aem.sync.service;

import com.microsoft.azure.functions.ExecutionContext;
import java.util.logging.Logger;

public class SyncAemContentTestMain {
    public static void main(String[] args) {
        ExecutionContext context = new ExecutionContext() {
            @Override
            public Logger getLogger() {
                return Logger.getLogger("SyncAemContentTestMain");
            }
            @Override
            public String getInvocationId() { return "test"; }
            @Override
            public String getFunctionName() { return "test"; }
        };
        SyncAemContentService.syncFromFile("src/main/resources/sample-aem-response.json", context);
    }
}
