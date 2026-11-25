package com.iacuityfintech.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iacuityfintech.model.GraphDataFile;
import com.iacuityfintech.model.GraphIndex;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Loads the static JSON dataset once at startup and exposes it as an in-memory {@link GraphIndex}.
 * If the file is missing or malformed the application fails fast with a clear message.
 */
@Configuration
public class GraphDataConfig {

    @Bean
    public GraphIndex graphIndex(ObjectMapper objectMapper,
                                 @Value("${graph.data.location:classpath:transactions-graph-nodes.json}") Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            GraphDataFile data = objectMapper.readValue(in, GraphDataFile.class);
            return new GraphIndex(data.nodes());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load graph data from " + resource, e);
        }
    }
}
