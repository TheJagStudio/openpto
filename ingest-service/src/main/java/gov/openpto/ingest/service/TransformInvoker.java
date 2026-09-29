package gov.openpto.ingest.service;

import com.amazonaws.services.lambda.runtime.events.S3Event;
import gov.openpto.ingest.storage.ObjectCreatedEvent;
import gov.openpto.ingest.transform.lambda.ObjectResult;
import gov.openpto.ingest.transform.lambda.TransformResult;

import java.util.function.Function;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Invokes the transform function with an S3-shaped event, exactly as the Lambda service would. */
@Component
public class TransformInvoker {

    private final Function<S3Event, TransformResult> transform;

    public TransformInvoker(@Qualifier("transform") Function<S3Event, TransformResult> transform) {
        this.transform = transform;
    }

    public ObjectResult invoke(ObjectCreatedEvent event) {
        TransformResult result = transform.apply(S3Events.objectCreated(event));
        if (result == null || result.objects().isEmpty()) {
            throw new IllegalStateException("Transform function returned no result for " + event.key());
        }
        return result.objects().get(0);
    }
}