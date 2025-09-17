package com.aem.sync;
import com.aem.sync.service.SyncAemContentService;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import java.util.*;

public class SyncAemContentFunction {
    // @FunctionName("SyncAemContentTimer")
    // public void runTimer(
    //     @TimerTrigger(name = "syncAemTimer", schedule = "0 0 * * * *") String timerInfo,
    //     final ExecutionContext context
    // ) {
    //     context.getLogger().info("Timer triggered SyncAemContent");
    //     SyncAemContentService.sync(context);
    // }

    @FunctionName("SyncAemContentHttp")
    public HttpResponseMessage runHttp(
        @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context
    ) {
        context.getLogger().info("HTTP triggered SyncAemContent");
        SyncAemContentService.sync(context);
        return request.createResponseBuilder(HttpStatus.OK).body("Sync triggered").build();
    }
}
