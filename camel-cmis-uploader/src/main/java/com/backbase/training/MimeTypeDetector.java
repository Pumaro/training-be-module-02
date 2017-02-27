package com.backbase.training;

import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * Detects files mime type using Apache Tica library.
 */
@Service
public class MimeTypeDetector {

    private static final Logger logger = LoggerFactory.getLogger(MimeTypeDetector.class);

//    You can mark a method in your bean with the @Handler annotation to indicate that this method should be used for
//    Bean Binding.
//    This has an advantage as you need not specify a method name in the Camel route,
//    and therefore do not run into problems after renaming the method in an IDE that can't find all its references.
    @Handler
    public String detect(GenericFile<File> body) throws Exception {
        String mimeType = new Tika().detect(body.getFile());
        logger.debug("Mime Type for file: '" + body.getFileName() + "' resolved to :" + mimeType);
        return mimeType;
    }
}
