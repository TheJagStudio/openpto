package gov.openpto.ingest.transform.lambda;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

/** Minimal Lambda {@link Context} used when the function is invoked locally (service emulation, tests). */
public final class LocalContext implements Context {

    private final String requestId;
    private final String functionName;
    private final long deadline;
    private final LambdaLogger logger;

    public LocalContext(String functionName, int timeoutMillis, Consumer<String> log) {
        this.requestId = UUID.randomUUID().toString();
        this.functionName = functionName;
        this.deadline = System.currentTimeMillis() + timeoutMillis;
        this.logger = new LambdaLogger() {
            @Override
            public void log(String message) {
                log.accept(message);
            }

            @Override
            public void log(byte[] message) {
                log.accept(new String(message, StandardCharsets.UTF_8));
            }
        };
    }

    public static LocalContext silent() {
        return new LocalContext("openpto-transform", 900_000, m -> {
        });
    }

    @Override
    public String getAwsRequestId() {
        return requestId;
    }

    @Override
    public String getLogGroupName() {
        return "/local/" + functionName;
    }

    @Override
    public String getLogStreamName() {
        return "local";
    }

    @Override
    public String getFunctionName() {
        return functionName;
    }

    @Override
    public String getFunctionVersion() {
        return "$LATEST";
    }

    @Override
    public String getInvokedFunctionArn() {
        return "arn:aws:lambda:local:000000000000:function:" + functionName;
    }

    @Override
    public CognitoIdentity getIdentity() {
        return null;
    }

    @Override
    public ClientContext getClientContext() {
        return null;
    }

    @Override
    public int getRemainingTimeInMillis() {
        return (int) Math.max(0, deadline - System.currentTimeMillis());
    }

    @Override
    public int getMemoryLimitInMB() {
        return 1024;
    }

    @Override
    public LambdaLogger getLogger() {
        return logger;
    }
}