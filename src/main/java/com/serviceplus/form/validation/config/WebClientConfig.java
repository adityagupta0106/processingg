package com.serviceplus.form.validation.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

	@Bean
    @LoadBalanced
    WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
	
	@Bean(name = "restTemplateBuilder01")
    @LoadBalanced
    @Primary
    RestTemplate restTemplateBuilder() {
    	return new RestTemplate();
    }
}
