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

@Service
public class MimeTypeAggregator implements AggregationStrategy {

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        oldExchange.getIn().setHeader("mimeType", newExchange.getIn().getBody(String.class));
        return oldExchange;
    }
}
